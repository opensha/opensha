package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueByIndexLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.WeightedListSampledLevel;
import org.opensha.commons.logicTree.LogicTreeNode.FixedWeightNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_GridSourceBuilder;
import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

public class NSHM27_InterfaceHingedBValue implements SectionSupraSeisBValues, FixedWeightNode {
	
	private static final NSHM27_InterfaceHingedBValue HINGED_SINGLE_NODE = new NSHM27_InterfaceHingedBValue(1d);
	
	public static final String NAME = "Hinged b-value";
	public static final String SHORT_NAME = "HingedB";
	
	public static class FixedLevel extends DataBackedLevel<NSHM27_InterfaceHingedBValue> {
		
		private NSHM27_InterfaceHingedBValue node;
		
		public FixedLevel(String name, String shortName) {
			super(name, shortName);
			node = new NSHM27_InterfaceHingedBValue(1d);
		}

		@Override
		public Class<? extends NSHM27_InterfaceHingedBValue> getType() {
			return NSHM27_InterfaceHingedBValue.class;
		}

		@Override
		public List<? extends NSHM27_InterfaceHingedBValue> getNodes() {
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return this.node == node;
		}

		@Override
		public JsonObject toJsonObject() {
			return null;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {}
		
	}
	
	public static class CombinedSamplingLevel extends
	LogicTreeLevel.AbstractCombinedSamplingLevel<SectionSupraSeisBValues, CombinedValue> {
		
		private HingeWrappedSamplingLevel hingedLevel;
		private DistributionWrappedSamplingLevel wrappedDistLevel;

		private CombinedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public CombinedSamplingLevel(String levelName, String levelShortName, double hingedWeight,
				ContinuousDistribution bDistribution, double distWeight) {
			super(levelName, levelShortName, "b Sample ", "bSample", "bSample");
			
			hingedLevel = new HingeWrappedSamplingLevel();
			
			SectionSupraSeisBValues.DistributionSamplingLevel distLevel = new DistributionSamplingLevel("b-value Distribution", "b-dist", bDistribution);
			
			wrappedDistLevel = new DistributionWrappedSamplingLevel(distLevel);
			
			WeightedList<AbstractRandomlySampledLevel<SectionSupraSeisBValues, ? extends CombinedValue>> ret = new WeightedList<>(2);
			ret.add(hingedLevel, hingedWeight);
			ret.add(wrappedDistLevel, distWeight);
			
			init(ret);
		}

		@Override
		protected AbstractRandomlySampledLevel<SectionSupraSeisBValues, ? extends CombinedValue> getLevelForValue(
				SectionSupraSeisBValues value) {
			if (value instanceof NSHM27_InterfaceHingedBValue)
				return hingedLevel;
			else if (value instanceof SectionSupraSeisBValues.Default)
				return wrappedDistLevel;
			throw new IllegalStateException("Unexpected value type: "+value);
		}

		@Override
		public Class<? extends SectionSupraSeisBValues> getValueType() {
			return SectionSupraSeisBValues.class;
		}

		@Override
		public Class<? extends CombinedValue> getType() {
			return CombinedValue.class;
		}
		
	}
	
//	private static final CombinedTypeAdapter COMB_TA = new CombinedTypeAdapter();
//	
//	private static class CombinedTypeAdapter extends TypeAdapter<SectionSupraSeisBValues> {
//
//		@Override
//		public void write(JsonWriter out, SectionSupraSeisBValues value) throws IOException {
//			out.beginArray();
//			if (value instanceof NSHM27_InterfaceHingedBValue)
//				out.value(SHORT_NAME);
//			else if (value instanceof SectionSupraSeisBValues.Default)
//				out.value(((SectionSupraSeisBValues.Default)value).getB());
//			throw new IllegalStateException("Unexpected value type: "+value);
//		}
//
//		@Override
//		public SectionSupraSeisBValues read(JsonReader in) throws IOException {
//			if (in.peek() == JsonToken.STRING) {
//				Preconditions.checkState(in.nextString().equals(SHORT_NAME));
//				return HINGED_SINGLE_NODE;
//			} else if (in.peek() == JsonToken.NUMBER) {
//				return new Section
//			}
//			return null;
//		}
//		
//	}
	
	private static class CombinedValue implements ValuedLogicTreeNode<SectionSupraSeisBValues>, SectionSupraSeisBValues {
		
		private SectionSupraSeisBValues value;
		private double weight;
		private String name;
		private String shortName;
		private String filePrefix;

		public CombinedValue(SectionSupraSeisBValues value, double weight, String name, String shortName, String filePrefix) {
			init(value, value.getClass(), weight, name, shortName, filePrefix);
		}

		@Override
		public double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return getValue().getSectBValues(rupSet, branch);
		}

		@Override
		public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return getValue().getB(rupSet, branch);
		}

		@Override
		public double getNodeWeight() {
			return weight;
		}

		@Override
		public String getFilePrefix() {
			return filePrefix;
		}

		@Override
		public String getShortName() {
			return shortName;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public SectionSupraSeisBValues getValue() {
			return value;
		}

		@Override
		public Class<? extends SectionSupraSeisBValues> getValueType() {
			return SectionSupraSeisBValues.class;
		}

		@Override
		public void init(SectionSupraSeisBValues value, Class<? extends SectionSupraSeisBValues> valueClass,
				double weight, String name, String shortName, String filePrefix) {
			this.value = value;
			this.weight = weight;
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
		}
		
	}
	
	private static class HingeWrappedSamplingLevel extends LogicTreeLevel.AbstractRandomlySampledLevel<SectionSupraSeisBValues, CombinedValue>
	implements ValueByIndexLevel<SectionSupraSeisBValues, CombinedValue> {
		
		private double weightEach;

		private HingeWrappedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public HingeWrappedSamplingLevel() {
			super(NAME, SHORT_NAME, NAME+" ", SHORT_NAME, SHORT_NAME);
		}

		@Override
		public Class<? extends SectionSupraSeisBValues> getValueType() {
			return SectionSupraSeisBValues.Default.class;
		}

		@Override
		protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			this.weightEach = weightEach;
			List<CombinedValue> values = new ArrayList<>(numNodes);
			for (int i=0; i<numNodes; i++)
				values.add(build(i, HINGED_SINGLE_NODE, weightEach));
			setValues(values, weightEach);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject jsonObj = super.toJsonObject();

			jsonObj.add("numNodes", new JsonPrimitive(getNodes().size()));
			jsonObj.add("weightEach", new JsonPrimitive(weightEach));
			
			return jsonObj;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);

			int numNodes = jsonObj.get("numNodes").getAsInt();
			double weightEach = jsonObj.get("weightEach").getAsDouble();
			doBuild(0l, numNodes, null, weightEach);
		}

		@Override
		public CombinedValue build(SectionSupraSeisBValues value, double weight, String name, String shortName,
				String filePrefix) {
			return new CombinedValue(value, weight, name, shortName, filePrefix);
		}

		@Override
		public Class<? extends CombinedValue> getType() {
			return CombinedValue.class;
		}

		@Override
		public SectionSupraSeisBValues valueForIndex(int index) {
			return nodes.get(index).getValue();
		}

		@Override
		public void init(List<Double> weights) {
			Preconditions.checkState(nodes.size() == weights.size());
		}
		
	}
	
	private static class DistributionWrappedSamplingLevel extends LogicTreeLevel.AbstractRandomlySampledLevel<SectionSupraSeisBValues, CombinedValue>
	implements ValueByIndexLevel<SectionSupraSeisBValues, CombinedValue> {
		
		private DistributionSamplingLevel distLevel;

		private DistributionWrappedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public DistributionWrappedSamplingLevel(DistributionSamplingLevel distLevel) {
			super(distLevel.getName(), distLevel.getShortName(), getRawNamePrefix(distLevel),
					getRawShortNamePrefix(distLevel), getRawFilePrefix(distLevel));
			System.out.println("Name prefix: "+getNodeNamePrefix()+"/"+getNodeShortNamePrefix()+"/"+getNodeFilePrefix());
			this.distLevel = distLevel;
		}
		
		private static String getRawNamePrefix(DistributionSamplingLevel level) {
			String prefix = level.getNodeName(0);
			return prefix.substring(0, prefix.length()-1);
		}
		private static String getRawShortNamePrefix(DistributionSamplingLevel level) {
			String prefix = level.getNodeShortName(0);
			return prefix.substring(0, prefix.length()-1);
		}
		private static String getRawFilePrefix(DistributionSamplingLevel level) {
			String prefix = level.getNodeFilePrefix(0);
			return prefix.substring(0, prefix.length()-1);
		}

		@Override
		public Class<? extends SectionSupraSeisBValues> getValueType() {
			return SectionSupraSeisBValues.Default.class;
		}

		@Override
		protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			distLevel.build(seed, numNodes, samplingMethod, weightEach);
			
			List<? extends Default> values = distLevel.getNodes();
//			System.out.println("b-value built! "+values.size()+" nodes");
			setValues(values, weightEach);
		}

		@Override
		public JsonObject toJsonObject() {
			LogicTreeLevel.Adapter<SectionSupraSeisBValues> adapter = new LogicTreeLevel.Adapter<>();
			JsonObject wrappedData = adapter.toJsonTree(distLevel).getAsJsonObject();
//			wrappedData
//			double weightEach = jsonObj.get("weightEach").getAsDouble();
			return wrappedData;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			// sets prefixes and original seed
			super.initFromJsonObject(jsonObj.getAsJsonObject("data"));
//			System.out.println("Loaded name prefix: "+getNodeNamePrefix()+"/"+getNodeShortNamePrefix()+"/"+getNodeFilePrefix());
//			System.out.println("Loaded Json:\n"+jsonObj.toString());
			distLevel = DistributionSamplingLevel.fromJson(jsonObj);
			List<? extends Default> values = distLevel.getNodes();
			setValues(values, values.get(0).getNodeWeight());
		}

		@Override
		public CombinedValue build(SectionSupraSeisBValues value, double weight, String name, String shortName,
				String filePrefix) {
			return new CombinedValue(value, weight, name, shortName, filePrefix);
		}

		@Override
		public Class<? extends CombinedValue> getType() {
			return CombinedValue.class;
		}

		@Override
		public SectionSupraSeisBValues valueForIndex(int index) {
			return nodes.get(index).getValue();
		}

		@Override
		public void init(List<Double> weights) {
			Preconditions.checkState(nodes.size() == weights.size());
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
