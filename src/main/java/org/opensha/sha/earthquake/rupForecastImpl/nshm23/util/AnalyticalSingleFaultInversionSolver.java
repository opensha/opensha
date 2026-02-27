package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

/**
 * This solves for the rate of each rupture analytically, according to the prescribed section nucleation MFDs. It does
 * so by first fetching section supra-seismogenic nucleation MFDs from the {@link InversionTargetMFDs} attached to the
 * rupture set. Then, for each each section, that nucleation MFD is spread across all participating ruptures.
 * <p>
 * If a b-value is available (either passed in via the constructor or retrieved from a
 * {@link SupraSeisBValInversionTargetMFDs} instance), then rupture rates for different magnitudes within a single MFD
 * bin will be proportioned according to their relative G-R rate.
 * 
 * @author kevin
 *
 */
public class AnalyticalSingleFaultInversionSolver extends InversionSolver.Default {
	
	private double forcedBVal;
	private BinaryRuptureProbabilityCalc rupExclusionModel;

	public AnalyticalSingleFaultInversionSolver() {
		this(Double.NaN, null);
	}

	/**
	 * 
	 * @param bVal used to proportion for ruptures of different magnitudes that share an MFD bin
	 * @param rupExclusionModel optional rupture exclusion model
	 */
	public AnalyticalSingleFaultInversionSolver(double bVal) {
		this(bVal, null);
	}

	/**
	 * 
	 * @param rupExclusionModel optional rupture exclusion model
	 */
	public AnalyticalSingleFaultInversionSolver(BinaryRuptureProbabilityCalc rupExclusionModel) {
		this(Double.NaN, rupExclusionModel);
	}

	/**
	 * 
	 * @param bVal used to proportion for ruptures of different magnitudes that share an MFD bin (0 to force spreading evenly)
	 * @param rupExclusionModel optional rupture exclusion model
	 */
	public AnalyticalSingleFaultInversionSolver(double bVal, BinaryRuptureProbabilityCalc rupExclusionModel) {
		this.forcedBVal = bVal;
		this.rupExclusionModel = rupExclusionModel;
	}

	@Override
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info) {
		ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
		
		// get target MFDs from the rupture set
		InversionTargetMFDs targets = rupSet.requireModule(InversionTargetMFDs.class);
		
		List<? extends IncrementalMagFreqDist> supraNuclMFDs = targets.getOnFaultSupraSeisNucleationMFDs();
		Preconditions.checkNotNull(supraNuclMFDs,
				"Must have section supra-seis nucleation MFDs to solve classic model analytically");
		
		BitSet excludeRuptures = null;
		if (rupExclusionModel != null) {
			excludeRuptures = new BitSet(rupSet.getNumRuptures());
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++)
				if (!rupExclusionModel.isRupAllowed(cRups.get(rupIndex), false))
					excludeRuptures.set(rupIndex);
		}
		
		// if non-null, will subtract from target MFDs
		double[] waterLevel = config.getWaterLevel();
		
		double[] bVals = null;
		if (Double.isFinite(forcedBVal)) {
			// use the passed in b-value no matter what
			bVals = new double[rupSet.getNumSections()];
			for (int s=0; s<bVals.length; s++)
				bVals[s] = forcedBVal;
		} else if (targets instanceof SupraSeisBValInversionTargetMFDs) {
			SupraSeisBValInversionTargetMFDs bTargets = (SupraSeisBValInversionTargetMFDs)targets;
			bVals = bTargets.getSectSpecificBValues();
			if (bVals == null) {
				// use global from targets
				double b = bTargets.getSupraSeisBValue();
				bVals = new double[rupSet.getNumSections()];
				for (int s=0; s<bVals.length; s++)
					bVals[s] = b;
			} else {
				// we have section-specific
				Preconditions.checkState(bVals.length == rupSet.getNumSections());
			}
		}
		
		double[] rates = new double[rupSet.getNumRuptures()];
		for (int s=0; s<rupSet.getNumSections(); s++) {
			// solve it in the world of each subsection
			IncrementalMagFreqDist sectMFD = supraNuclMFDs.get(s);
			Preconditions.checkNotNull(sectMFD,
					"Must have section supra-seis nucleation MFDs to solve classic model analytically");
			if (sectMFD.calcSumOfY_Vals() == 0d)
				continue;
			
			List<List<Integer>> rupsInBins = new ArrayList<>(sectMFD.size());
			for (int i=0; i<sectMFD.size(); i++)
				rupsInBins.add(null);
			
			if (waterLevel != null)
				// we will subtract the waterlevel from the target
				sectMFD = sectMFD.deepClone();
			
			// identify ruptures above section minimum magnitude, and figure out how many occupy each input MFD bin
			for (int rupIndex : rupSet.getRupturesForSection(s)) {
				if (excludeRuptures != null && excludeRuptures.get(rupIndex))
					continue;
				double mag = rupSet.getMagForRup(rupIndex);
				int magIndex = sectMFD.getClosestXIndex(mag);
				if (waterLevel != null && waterLevel[rupIndex] > 0d) {
					// remove waterlevel rate from the target (convert to nucleation rate first)
					double waterLevelNuclRate = waterLevel[rupIndex]*rupSet.getAreaForSection(s)/rupSet.getAreaForRup(rupIndex);
					sectMFD.set(magIndex, Math.max(0d, sectMFD.getY(magIndex)-waterLevelNuclRate));
				}
				if (minMags != null && minMags.isBelowSectMinMag(s, mag, sectMFD))
					continue;
				List<Integer> binRups = rupsInBins.get(magIndex);
				if (binRups == null) {
					binRups = new ArrayList<>();
					rupsInBins.set(magIndex, binRups);
				}
				binRups.add(rupIndex);
			}
			
			// now figure out rupture rates to satisfy this nucleation MFD
			for (int i=0; i<sectMFD.size(); i++) {
				List<Integer> rups = rupsInBins.get(i);
				double binRate = sectMFD.getY(i);
				if (rups == null || binRate == 0d)
					continue;
				
				if (bVals == null || bVals[s] == 0d || allMagsSame(rupSet, rups)) {
					// simple case
					double rateEach = binRate / rups.size();
					
					for (int rupIndex : rups)
						rates[rupIndex] += rateEach;
				} else {
					// complicated, need to spread it across ruptures of different magnitudes
					// do so according to the target b-value
					
					double binCenter = sectMFD.getX(i);
					double centerGR = calcGR(bVals[s], binCenter);
					double[] weights = new double[rups.size()];
					double sumWeight = 0d;
					for (int r=0; r<weights.length; r++) {
						double mag = rupSet.getMagForRup(rups.get(r));
						double myGR = calcGR(bVals[s], mag);
						double weight = myGR/centerGR;
						sumWeight += weight;
						weights[r] = weight;
					}
					for (int r=0; r<weights.length; r++)
						rates[rups.get(r)] += binRate*weights[r]/sumWeight;
				}
			}
		}
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		
		// mark that this is a prescriptive solution by setting it as the initial model as well
		sol.addModule(new InitialSolution(rates));
		
		if (waterLevel != null)
			sol.addModule(new WaterLevelRates(waterLevel));
		
		if (config != null) {
			// calculate inversion misfits
			sol.addModule(config);
			List<InversionConstraint> constraints = config.getConstraints();
			if (constraints != null && !constraints.isEmpty()) {
				InversionInputGenerator inputGen = new InversionInputGenerator(rupSet, config);
				inputGen.generateInputs(false);
				double[] data_eq = inputGen.getD();
				double[] misfits_eq = new double[data_eq.length];
				SerialSimulatedAnnealing.calculateMisfit(inputGen.getA(), data_eq, rates, misfits_eq);
				
				double[] data_ineq = null;
				double[] misfits_ineq = null;
				if (inputGen.getA_ineq() != null) {
					data_ineq = inputGen.getD_ineq();
					misfits_ineq = new double[data_ineq.length];
					SerialSimulatedAnnealing.calculateMisfit(inputGen.getA_ineq(), data_ineq, rates, misfits_ineq);
				}
				
				InversionMisfits misfits = new InversionMisfits(inputGen.getConstraintRowRanges(), misfits_eq, data_eq,
						misfits_ineq, data_ineq);
				sol.addModule(misfits);
				sol.addModule(misfits.getMisfitStats());
			}
		}
		
		if (info != null)
			sol.addModule(new InfoModule(info));
		
		return sol;
	}
	
	private static boolean allMagsSame(FaultSystemRupSet rupSet, List<Integer> binRups) {
		if (binRups.size() == 1)
			return true;
		Double mag = null;
		for (int rupIndex : binRups) {
			double myMag = rupSet.getMagForRup(rupIndex);
			if (mag == null)
				mag = myMag;
			else if (mag != myMag)
				return false;
		}
		return true;
	}
	
	private static double calcGR(double bVal, double mag) {
		return Math.pow(10, -bVal*mag);
	}

}
