package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.CombinedSamplingNode;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_GridSourceBuilder;
import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public class NSHM27_InterfaceHingedBValue implements SectionSupraSeisBValues.FixedWeight {
	
	private static final NSHM27_InterfaceHingedBValue HINGED_SINGLE_NODE = new NSHM27_InterfaceHingedBValue(1d);
	
	public static final String NAME = "Hinged b-value";
	public static final String SHORT_NAME = "HingedB";
	
	public static class FixedLevel extends DataBackedLevel<NSHM27_InterfaceHingedBValue> {
		
		@SuppressWarnings("unused") // deserialization
		private FixedLevel() {}
		
		public FixedLevel(String name, String shortName) {
			super(name, shortName);
		}

		@Override
		public Class<? extends NSHM27_InterfaceHingedBValue> getType() {
			return NSHM27_InterfaceHingedBValue.class;
		}

		@Override
		public List<? extends NSHM27_InterfaceHingedBValue> getNodes() {
			return List.of(HINGED_SINGLE_NODE);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return HINGED_SINGLE_NODE == node;
		}

		@Override
		public JsonObject toJsonObject() {
			return new JsonObject();
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {}
		
	}
	
	public static class CombinedSampledType extends CombinedSamplingNode<SectionSupraSeisBValues.FixedWeight>
	implements SectionSupraSeisBValues {

		private FixedWeight node;

		@SuppressWarnings("unused") // deserialization
		private CombinedSampledType() {
			super();
		}

		public CombinedSampledType(int[] indexes, FixedWeight value, double weight,
				String name, String shortName, String filePrefix) {
			super(indexes, value, int[].class, weight, name, shortName, filePrefix);
		}

		@Override
		protected void setNodeValue(SectionSupraSeisBValues.FixedWeight node) {
			super.setNodeValue(node);
			this.node = node;
		}

		@Override
		public double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return node.getSectBValues(rupSet, branch);
		}

		@Override
		public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return node.getB(rupSet, branch);
		}
		
		public boolean isHinged() {
			return node instanceof NSHM27_InterfaceHingedBValue;
		}
		
	}
	
	public static class CombinedSamplingLevel extends
	LogicTreeLevel.AbstractCombinedSamplingLevel<SectionSupraSeisBValues.FixedWeight, CombinedSampledType> {
		
		private CombinedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public CombinedSamplingLevel(String levelName, String levelShortName, double hingedWeight,
				ContinuousDistribution bDistribution, double distWeight) {
			super(levelName, levelShortName, "bCombSample");
			
			SectionSupraSeisBValues.DistributionSamplingLevel distLevel = new DistributionSamplingLevel("b-value Distribution", "b-dist", bDistribution);
			
			WeightedList<LogicTreeLevel<? extends SectionSupraSeisBValues.FixedWeight>> levels = new WeightedList<>(2);
			levels.add(new FixedLevel(NAME, SHORT_NAME), hingedWeight);
			levels.add(distLevel, distWeight);
			
			init(levels);
		}

		@Override
		public Class<? extends int[]> getValueType() {
			return int[].class;
		}

		@Override
		public Class<? extends CombinedSampledType> getType() {
			return CombinedSampledType.class;
		}

		@Override
		protected CombinedSampledType buildCombinedNode(int[] indexes, SectionSupraSeisBValues.FixedWeight value,
				double weight, String name, String shortName,
				String filePrefix) {
			return new CombinedSampledType(indexes, value, weight, name, shortName, filePrefix);
		}
		
	}

	private double weight;

	private NSHM27_InterfaceHingedBValue() {
		super();
	}

	private NSHM27_InterfaceHingedBValue(double weight) {
		this.weight = weight;
	}

	@Override
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
		return null;
	}

	@Override
	public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
		return calcInterfaceHingedBValue(rupSet, branch);
	}
	
	/**
	 * Calculates an interface on-fault moment-balanced GR b-value such that the incremental rate in the first
	 * on-fault MFD bin matches the extrapolation from the observed seismicity rate model. Using this b-value to
	 * construct the interface on-fault MFD will result in a continuous but hinged MFD, rather than a shelved one when
	 * hitting the higher on-fault rates.
	 * 
	 * If the corresponding hinge b-value is negative, 0 is returned instead.
	 * 
	 * @param rupSet
	 * @param branch
	 * @return the greater of the hinge b-value and 0
	 */
	public static double calcInterfaceHingedBValue(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		// figure out included rupture mMin and mMax
		int numSects = rupSet.getNumSections();
		int numRups = rupSet.getNumRuptures();
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		BinaryRuptureProbabilityCalc rupExclusionModel = NSHM27_InvConfigFactory.getExclusionModel(rupSet, branch, cRups);
		double[] sectMmins = new double[numSects];
		for (int s=0; s<numSects; s++)
			sectMmins[s] = Double.POSITIVE_INFINITY;
		double mMax = 0d;
		for (int rupIndex=0; rupIndex<numRups; rupIndex++) {
			if (rupExclusionModel != null && !rupExclusionModel.isRupAllowed(cRups.get(rupIndex), false))
				continue;
			double mag = rupSet.getMagForRup(rupIndex);
			mMax = Math.max(mMax, mag);
			for (int s : rupSet.getSectionsIndicesForRup(rupIndex))
				sectMmins[s] = Math.min(sectMmins[s], mag);
		}
		Preconditions.checkState(mMax > 0d);
		
		// calculate associated moment, and moment-weighted section mMin
		FaultGridAssociations assoc = rupSet.requireModule(FaultGridAssociations.class);
		double momentSum = 0d;
		double momentWeightedMminSum = 0d;
		SectSlipRates slips = rupSet.requireModule(SectSlipRates.class);
		for (int s=0; s<numSects; s++) {
			double moment = slips.calcMomentRate(s) * assoc.getSectionFractInRegion(s);
			Preconditions.checkState(Double.isFinite(moment));
			if (moment == 0)
				continue;
			Preconditions.checkState(Double.isFinite(sectMmins[s]));
			momentSum += moment;
			momentWeightedMminSum += sectMmins[s]*moment;
		}
		Preconditions.checkState(momentSum > 0d);
		double mMin = momentWeightedMminSum/momentSum;
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(NSHM27_GridSourceBuilder.OVERALL_MMIN, mMax+0.1);
		int mMinIndex = refMFD.getClosestXIndex(mMin);
		Preconditions.checkState(mMinIndex > 0, "should be far from the first bin");
		int mMaxIndex = refMFD.getClosestXIndex(mMax);
		Preconditions.checkState(mMaxIndex > mMinIndex);
		mMin = refMFD.getX(mMinIndex);
		mMax = refMFD.getX(mMaxIndex);
		
		// obs seis MFD
		NSHM27_SeismicityRegions seisRegion = branch.requireValue(NSHM27_InterfaceFaultModels.class).getSeismicityRegion();
		// go all the way to mMin (not the bin before) because that's what we're pinning to
		IncrementalMagFreqDist obsMFD = NSHM27_GridSourceBuilder.buildInterfaceGriddedMFD(
				seisRegion, branch, refMFD,
				mMin, // this is gridded mMax when constructing obs MFD; the returned MFD will go from 2.55->this
				mMax); // this is fault mMax, which is used only to carve out the obs MFD
		double rateAtMmin = obsMFD.getY(mMinIndex);
		Preconditions.checkState(rateAtMmin > 0d);
		// do it again to mMin-1 in case that shifts the MFD (the real gridded MFD won't include that bin, which can affect rates)
		IncrementalMagFreqDist obsMFD2 = NSHM27_GridSourceBuilder.buildInterfaceGriddedMFD(
				seisRegion, branch, refMFD, mMin-refMFD.getDelta(), mMax);
		if (obsMFD2.getY(mMinIndex-1) > obsMFD.getY(mMinIndex-1))
			rateAtMmin *= obsMFD2.getY(mMinIndex-1) / obsMFD.getY(mMinIndex-1);
		
		System.out.println("Calculating hinged b-value for incrRate["+(float)mMin+"]="+(float)rateAtMmin);
		
		GutenbergRichterMagFreqDist faultGR = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
		faultGR.setAllButBvalueForIncrRate(mMin, mMax, momentSum, rateAtMmin);
		
		System.out.println("Calculated hinged b="+(float)faultGR.get_bValue()+"; faultIncr["+(float)mMin+"]="
				+faultGR.getY(mMinIndex)+", faultTotal="+faultGR.calcSumOfY_Vals());
		
		return Math.max(0d, faultGR.get_bValue());
	}

}
