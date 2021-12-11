package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Jump probability constraint, for imposing fault segmentation according to a {@link JumpProbabilityCalc} model.
 * <p>
 * There is 1 constraint for each side of each jump in the given rupture set. So for a simple 2 section case, with 1
 * jumping point between them, there would be 2 constraints, one in each direction.
 * <p>
 * First, we use the given model (e.g., {@link Shaw07JumpDistProb}) to compute the conditional probability of taking all
 * possible jumps. These probabilities don't take into account other possible options, they are just the probability
 * of taking the given jump relative to doing nothing.
 * <p>
 * Then, for each jump, we normalize that conditional probability into a relative probability of taking that particular
 * jump relative to all other options. We first sum the conditional probabilities of all jumps from that same departing
 * subsection (including the jump we're evaluating). Then, if the departing subsection is in the middle of a parent
 * section, we add '1' to that sum to account for the probability of continuing down on that section without taking any
 * jump. If the departing subsection is at the end of a fault, then we ensure that the sum of all conditional jump
 * probabilities is at least 1 (where any leftover probability that is added in can be interpreted as the probability
 * of stopping and not taking any jump).
 * <p>
 * Now we have a relative probability of taking each jump, conditioned on rupturing up to the point of the departing
 * subsection. That probability is relative to all other available options, including other jumps, continuing on the
 * same section (if possible), or arresting. That can be applied in 2 ways:
 * <br>
 * 1. As a {@link ProxySlip} rate constraint: proxySlip = targetSlip*relJumpProb.
 * <p>
 * 2. As a {@link RelativeRate} constraint, where the sum of rates that use the jump are constrained to be
 * <code>relJumpProb</code> fraction of the sum of all rates that use the departing section. This has the added
 * complication that misfits will scale with supra-seis participation rates, meaning that we won't fit low rate faults
 * well without extra normalization. We can get around this by normalizing by an estimated departing section total
 * event rate (e.g., from the smooth starting solution, see {@link SectParticipationRateEstimator}}).
 * 
 * @author kevin
 *
 */
public abstract class JumpProbabilityConstraint extends InversionConstraint {

	protected transient FaultSystemRupSet rupSet;
	private transient Map<Jump, List<Integer>> jumpRupsMap;
	
	@JsonAdapter(ProbCalcAdapter.class)
	private JumpProbabilityCalc jumpProbCalc;

	protected JumpProbabilityConstraint(String name, String shortName, double weight, boolean inequality,
			ConstraintWeightingType weightingType, FaultSystemRupSet rupSet, JumpProbabilityCalc jumpProbCalc) {
		super(name, shortName, weight, inequality, weightingType);
		setRuptureSet(rupSet);
		this.jumpProbCalc = jumpProbCalc;
	}
	
	private synchronized void checkInitJumpRups() {
		if (jumpRupsMap == null) {
			jumpRupsMap = new HashMap<>();
			
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			
			for (int r=0; r<cRups.size(); r++) {
				ClusterRupture rup = cRups.get(r);
				for (Jump jump : rup.getJumpsIterable()) {
					List<Integer> jumpRups = jumpRupsMap.get(jump);
					if (jumpRups == null) {
						jumpRups = new ArrayList<>();
						jumpRupsMap.put(jump, jumpRups);
					}
					jumpRups.add(r);
					// now add it reversed
					jump = jump.reverse();
					jumpRups = jumpRupsMap.get(jump);
					if (jumpRups == null) {
						jumpRups = new ArrayList<>();
						jumpRupsMap.put(jump, jumpRups);
					}
					jumpRups.add(r);
				}
			}
		}
	}

	@Override
	public int getNumRows() {
		checkInitJumpRups();
		return jumpRupsMap.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		checkInitJumpRups();
		List<Jump> allJumps = new ArrayList<>(jumpRupsMap.keySet());
		allJumps.sort(Jump.id_comparator); // sort for consistent row ordering
		
		long count = 0l;
		int row = startRow;
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		
//		// this will calculate the relative probability of taking a jump in the presence of other options
//		// relative to sum of alternatives, we use best for plausibility but sum is more applicable here
//		boolean relativeToBest = false;
//		RelativeJumpProb relProbCalc = new RelativeJumpProb(jumpProbCalc,
//				rupSet.requireModule(PlausibilityConfiguration.class).getConnectionStrategy(), relativeToBest);
		
		// first find the prob of taking each jump, not considering alternatives
		Table<Integer, Jump, Double> departingSectJumpProbs = HashBasedTable.create();
		for (Jump jump : allJumps) {
			List<Integer> rupsUsingJump = jumpRupsMap.get(jump);
			Preconditions.checkNotNull(rupsUsingJump != null);
			Preconditions.checkState(!rupsUsingJump.isEmpty());
			
			MinMaxAveTracker probTrack = new MinMaxAveTracker();
			for (int r : rupsUsingJump) {
				ClusterRupture rup = cRups.get(r);
				RuptureTreeNavigator nav = rup.getTreeNavigator();
				Jump myJump = nav.getJump(jump.fromSection, jump.toSection);
				Preconditions.checkState(myJump.fromSection.getSectionId() == jump.fromSection.getSectionId());
				
				// see if either end needs to be reversed
				if (!myJump.fromCluster.endSects.contains(myJump.fromSection))
					myJump = new Jump(myJump.fromSection, myJump.fromCluster.reversed(),
							myJump.toSection, myJump.toCluster, myJump.distance);
				Preconditions.checkState(myJump.fromCluster.endSects.contains(myJump.fromSection));
				if (!myJump.toCluster.startSect.equals(myJump.toSection))
					myJump = new Jump(myJump.fromSection, myJump.fromCluster,
							myJump.toSection, myJump.toCluster.reversed(), myJump.distance);
				Preconditions.checkState(myJump.toCluster.startSect.equals(myJump.toSection));
				
				probTrack.addValue(jumpProbCalc.calcJumpProbability(rup, myJump, false));
			}
			
			double avgRupProbUsing = probTrack.getAverage();
			departingSectJumpProbs.put(jump.fromSection.getSectionId(), jump, avgRupProbUsing);
		}
		
		Map<Integer, List<FaultSection>> parentSectsMap = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		for (Jump jump : allJumps) {
			List<Integer> rupsUsingJump = jumpRupsMap.get(jump);
			Preconditions.checkNotNull(rupsUsingJump != null);
			Preconditions.checkState(!rupsUsingJump.isEmpty());
			
			int fromID = jump.fromSection.getSectionId();
			List<Integer> allJumpsForDepartingSect = rupSet.getRupturesForSection(fromID);
			Preconditions.checkState(allJumpsForDepartingSect.size() >= rupsUsingJump.size());
			
			double myJumpProb = departingSectJumpProbs.get(fromID, jump);
			
			Preconditions.checkState(Double.isFinite(myJumpProb) && myJumpProb >= 0d && myJumpProb <= 1d,
					"Bad jumpProb=%s for jump %s", myJumpProb, jump);
			
			double sumAllProbs = 0d;
			for (double jumpProb : departingSectJumpProbs.row(fromID).values())
				// add all jumps, including the one in question to the denominator
				sumAllProbs += jumpProb;
			
			List<FaultSection> parentSects = parentSectsMap.get(jump.fromSection.getParentSectionId());
			int indexInParent = parentSects.indexOf(jump.fromSection);
			Preconditions.checkState(indexInParent >= 0);
			if (indexInParent > 0 && indexInParent < parentSects.size()-1) {
				// departing section is in the middle of a fault, add alternative of continuing on the same fault,
				// which is here just assumed to be 1 (TODO)
				sumAllProbs += 1d;
			} else {
				// this is at an end, assume that any leftover probability in the denominator is the probability of
				// terminating without taking any jump
				sumAllProbs = Math.max(sumAllProbs, 1d);
			}
			
			Preconditions.checkState(Double.isFinite(sumAllProbs) && sumAllProbs >= 0d,
					"Bad sumAllProb=%s for jump %s", myJumpProb, jump);
			
			double jumpCondProb = myJumpProb / sumAllProbs;
			
			System.out.println("Jump probability for "+jump+"\tfrom "+jump.fromSection.getName()
					+"\tto "+jump.toSection.getName()+"\n\tP = "
					+(float)myJumpProb+" / "+(float)sumAllProbs+" = "+(float)jumpCondProb);
			
			count += encodeRow(A, d, row++, jump, jumpCondProb, rupsUsingJump, allJumpsForDepartingSect);
		}
		
		return count;
	}

	/**
	 * Encodes the A matrix/d vector for the given row
	 * 
	 * @param A A matrix
	 * @param d d vector
	 * @param row row that we are encoding
	 * @param jump jump corresponding to that row
	 * @param jumpCondProb the conditional probability of taking that jump (conditioned on rupturing up to the
	 * departing subsection)
	 * @param rupsUsingJump all ruptures that use that jump (in either direction)
	 * @param allJumpsForDepartingSect all ruptures that use the departing subsection
	 * @return number of A matrix cells encoded
	 */
	protected abstract long encodeRow(DoubleMatrix2D A, double[] d, int row, Jump jump, double jumpCondProb,
			Collection<Integer> rupsUsingJump, Collection<Integer> allJumpsForDepartingSect);
	
	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		rupSet.requireModule(ClusterRuptures.class);
		this.rupSet = rupSet;
	}
	
	private static class ProbCalcAdapter extends TypeAdapter<JumpProbabilityCalc> {
		
		Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, JumpProbabilityCalc value) throws IOException {
			// TODO Auto-generated method stub
			out.beginObject();

			out.name("type").value(value.getClass().getName());
			out.name("data");
			gson.toJson(value, value.getClass(), out);

			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public JumpProbabilityCalc read(JsonReader in) throws IOException {
			Class<? extends JumpProbabilityCalc> type = null;

			in.beginObject();

			Preconditions.checkState(in.nextName().equals("type"), "JSON 'type' object must be first");
			try {
				type = (Class<? extends JumpProbabilityCalc>) Class.forName(in.nextString());
			} catch (ClassNotFoundException | ClassCastException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}

			Preconditions.checkState(in.nextName().equals("data"), "JSON 'data' object must be second");
			JumpProbabilityCalc model = gson.fromJson(in, type);

			in.endObject();
			return model;
		}
		
	}
	
	/**
	 * {@link JumpProbabilityConstraint} implementation that enforces the constraint through proxy sections slip rates
	 * at jumps. This is a phantom slip rate between the departing and landing section of the jump.
	 * 
	 * @author kevin
	 *
	 */
	public static class ProxySlip extends JumpProbabilityConstraint {

		private transient AveSlipModule aveSlips;
		private transient SlipAlongRuptureModel slipAlongModel;
		private transient SectSlipRates slipRates;

		public ProxySlip(double weight, boolean inequality,
				FaultSystemRupSet rupSet, JumpProbabilityCalc jumpProbCalc) {
			super("Proxy Slip Jump Probability Constraint, "+jumpProbCalc.getName(), "SlipJumpProb",
					weight, inequality, ConstraintWeightingType.NORMALIZED, rupSet, jumpProbCalc);
		}

		@Override
		protected long encodeRow(DoubleMatrix2D A, double[] d, int row, Jump jump, double jumpCondProb,
				Collection<Integer> rupsUsingJump, Collection<Integer> allJumpsForDepartingSect) {
			long count = 0l;
			
			double totTargetSlip = slipRates.getSlipRate(jump.fromSection.getSectionId());
			
			double relTargetSlip = jumpCondProb*totTargetSlip;
			
			ConstraintWeightingType weightType = getWeightingType();
			
			double aScalar = this.weight*weightType.getA_Scalar(relTargetSlip, Double.NaN);
			
			// encode this as a proxy relative slip constraint
			for (int rup : rupsUsingJump) {
				double[] slips = slipAlongModel.calcSlipOnSectionsForRup(rupSet, aveSlips, rup);
				List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
				
				double slip = Double.NaN;
				for (int i=0; i<slips.length; i++) {
					int sect = sects.get(i);
					if (sect == jump.fromSection.getSectionId()) {
						slip = slips[i];
						break;
					}
				}
				Preconditions.checkState(Double.isFinite(slip));
				
				setA(A, row, rup, slip*aScalar);
				count++;
			}
			
			d[row] = weight*weightType.getD(relTargetSlip, Double.NaN);
			
			return count;
		}

		@Override
		public void setRuptureSet(FaultSystemRupSet rupSet) {
			aveSlips = rupSet.requireModule(AveSlipModule.class);
			slipAlongModel = rupSet.requireModule(SlipAlongRuptureModel.class);
			slipRates = rupSet.requireModule(SectSlipRates.class);
			super.setRuptureSet(rupSet);
		}
		
	}
	
	@JsonAdapter(RateEstAdapter.class)
	public static interface SectParticipationRateEstimator {
		
		public double[] estimateSectParticRates();
		
		public double estimateSectParticRate(int sectionIndex); 
	}
	
	@JsonAdapter(RateEstAdapter.class)
	public static class InitialModelParticipationRateEstimator implements SectParticipationRateEstimator {
		
		private double[] particRates;
		
		public InitialModelParticipationRateEstimator(FaultSystemRupSet rupSet, double[] initialSol) {
			particRates = new FaultSystemSolution(rupSet, initialSol).calcTotParticRateForAllSects();
		}
		
		public double estimateSectParticRate(int sectionIndex) {
			return particRates[sectionIndex];
		}

		@Override
		public double[] estimateSectParticRates() {
			return particRates;
		}
	}
	
	private static class RateEstAdapter extends TypeAdapter<SectParticipationRateEstimator> {

		@Override
		public void write(JsonWriter out, SectParticipationRateEstimator value) throws IOException {
			out.beginArray();
			for (double val : value.estimateSectParticRates())
				out.value(val);
			out.endArray();
		}

		@Override
		public SectParticipationRateEstimator read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			in.beginArray();
			List<Double> vals = new ArrayList<>();
			while (in.hasNext())
				vals.add(in.nextDouble());
			in.endArray();
			
			return new SectParticipationRateEstimator() {
				
				@Override
				public double[] estimateSectParticRates() {
					return Doubles.toArray(vals);
				}
				
				@Override
				public double estimateSectParticRate(int sectionIndex) {
					return vals.get(sectionIndex);
				}
			};
		}
		
	}
	
	public static class RelativeRate extends JumpProbabilityConstraint {

		@JsonAdapter(RateEstAdapter.class)
		private SectParticipationRateEstimator rateEst;

		public RelativeRate(double weight, boolean inequality,
				FaultSystemRupSet rupSet, JumpProbabilityCalc jumpProbCalc) {
			this(weight, inequality, rupSet, jumpProbCalc, null);
		}

		public RelativeRate(double weight, boolean inequality,
				FaultSystemRupSet rupSet, JumpProbabilityCalc jumpProbCalc, SectParticipationRateEstimator rateEst) {
			super("Relative Rate Jump Probability Constraint, "+jumpProbCalc.getName(), "RelRateJumpProb",
					weight, inequality, ConstraintWeightingType.UNNORMALIZED, rupSet, jumpProbCalc);
			this.rateEst = rateEst;
		}

		@Override
		protected long encodeRow(DoubleMatrix2D A, double[] d, int row, Jump jump, double jumpCondProb,
				Collection<Integer> rupsUsingJump, Collection<Integer> allJumpsForDepartingSect) {
			long count = 0l;
			
			double weight = this.weight;
			if (rateEst != null)
				// scale weight by that estimated total event rate for this section
				weight /= rateEst.estimateSectParticRate(jump.fromSection.getSectionId());
			// weight by the target fractional rate (large misfits of small conditional rates should still be fit)
			weight /= jumpCondProb;
			
			HashSet<Integer> setUsingJump = new HashSet<>(rupsUsingJump);
			
			double scalarIn = weight*(1d-jumpCondProb);
			double scalarOut = -weight*jumpCondProb;
			
			for (int r : allJumpsForDepartingSect) {
				if (setUsingJump.contains(r)) {
					setA(A, row, r, scalarIn);
				} else {
					setA(A, row, r, scalarOut);
				}
			}
			count += allJumpsForDepartingSect.size();
			d[row] = 0d;
			
			return count;
		}
		
	}

}
