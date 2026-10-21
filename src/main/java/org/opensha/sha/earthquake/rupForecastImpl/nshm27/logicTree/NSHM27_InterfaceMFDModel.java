package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.IOException;
import java.text.DecimalFormat;
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
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Defines how the interface inversion MFD is related to the observed-seismicity MFD. */
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceMFDModel implements SectionSupraSeisBValues.FixedWeight {
	EXTRAPOLATED("Extrapolated interface MFD", "Extrapolated", 0.1d),
	HINGED("Hinged interface MFD", "Hinged", 0.4d),
	INDEPENDENT("Independent interface MFD", "Independent", 0.5d);

	public static final double DEFAULT_INDEPENDENT_B = 1d;
	public static final String GEOJSON_INPUT_SLIP_RATE_PROP_NAME = "InputSlipRate";
	public static final String GEOJSON_INPUT_SLIP_SD_PROP_NAME = "InputSlipRateStdDev";

	private final String name;
	private final String shortName;
	private final double weight;

	private NSHM27_InterfaceMFDModel(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override public String getName() { return name; }
	@Override public String getShortName() { return shortName; }
	@Override public String getFilePrefix() { return name(); }
	@Override public double getNodeWeight() { return weight; }

	@Override
	public double[] getSectBValues(FaultSystemRupSet rupSet,
			LogicTreeBranch<? extends LogicTreeNode> branch) {
		return null;
	}

	@Override
	public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
		return switch (this) {
		case INDEPENDENT -> DEFAULT_INDEPENDENT_B;
		case EXTRAPOLATED -> getObservedSeismicityB(branch);
		case HINGED -> calcInterfaceHingedBValue(rupSet, branch);
		};
	}

	private static double getObservedSeismicityB(LogicTreeBranch<? extends LogicTreeNode> branch) {
		NSHM27_SeismicityRegions region = branch.requireValue(NSHM27_InterfaceFaultModels.class)
				.getSeismicityRegion();
		RateRecord rateModel = branch.requireValue(NSHM27_SeisRateModel.class).getRateRecord(
				region, TectonicRegionType.SUBDUCTION_INTERFACE, branch);
		Preconditions.checkState(rateModel instanceof PureGR,
				"Extrapolated interface MFD requires a pure-GR observed-seismicity rate model");
		return ((PureGR)rateModel).b;
	}

	public static NSHM27_InterfaceMFDModel getModel(LogicTreeBranch<?> branch) {
		NSHM27_InterfaceMFDModel model = branch.getValue(NSHM27_InterfaceMFDModel.class);
		if (model != null)
			return model;
		SampledNode sampled = branch.getValue(SampledNode.class);
		Preconditions.checkNotNull(sampled, "Missing interface MFD model");
		return sampled.getModel();
	}

	public static boolean isExtrapolated(LogicTreeBranch<?> branch) {
		NSHM27_InterfaceMFDModel model = branch.getValue(NSHM27_InterfaceMFDModel.class);
		if (model != null)
			return model == EXTRAPOLATED;
		SampledNode sampled = branch.getValue(SampledNode.class);
		return sampled != null && sampled.getModel() == EXTRAPOLATED;
	}

	/**
	 * Scales interface slip rates so that their associated moment equals the moment in the
	 * extrapolated observed-seismicity MFD that is available to the inversion.
	 */
	public static SectSlipRates applyExtrapolatedSlipRateScaling(FaultSystemRupSet rupSet,
			LogicTreeBranch<?> branch) throws IOException {
		Preconditions.checkState(getModel(branch) == EXTRAPOLATED,
				"Logic-tree branch does not select the extrapolated interface MFD model");

		final int numSections = rupSet.getNumSections();
		double[] inputFullSlipRates = new double[numSections];
		double[] inputCoupledSlipRates = new double[numSections];
		double[] inputFullSlipSDs = new double[numSections];
		for (int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			// mm -> m
			inputFullSlipRates[s] = sect.getOrigAveSlipRate()*1e-3;
			inputCoupledSlipRates[s] = sect.getReducedAveSlipRate()*1e-3;
			inputFullSlipSDs[s] = sect.getOrigSlipRateStdDev()*1e-3;
		}

		NSHM27_InterfaceFaultModels fm = branch.requireValue(NSHM27_InterfaceFaultModels.class);
		NSHM27_SeismicityRegions seisReg = fm.getSeismicityRegion();
		NSHM27_GridSourceBuilder.doPreGridBuildHook(rupSet, branch);
		FaultGridAssociations assoc = rupSet.requireModule(FaultGridAssociations.class);
		double[] sectMmins = NSHM27_GridSourceBuilder.getInterfaceSectMinMag(rupSet, branch);

		double[] moments = new double[numSections];
		double assocFaultMoment = 0d;
		for (int s=0; s<numSections; s++) {
			moments[s] = rupSet.getFaultSectionData(s).calcMomentRate(true);
			assocFaultMoment += moments[s]*assoc.getSectionFractInRegion(s);
		}
		Preconditions.checkState(assocFaultMoment > 0d,
				"No positive associated interface moment for branch %s", branch);

		double mMax = NSHM27_InvConfigFactory.getIncludeRuptureMmax(rupSet, branch);
		Preconditions.checkState(mMax > 0d);
		IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(NSHM27_GridSourceBuilder.OVERALL_MMIN, mMax);
		NSHM27_SeisRateModel rateModel = branch.requireValue(NSHM27_SeisRateModel.class);
		int mMaxIndex = refMFD.getClosestXIndex(mMax);
		IncrementalMagFreqDist totalGR = rateModel.build(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE,
				branch, refMFD, refMFD.getX(mMaxIndex));

		System.out.println("Applying extrapolated interface MFD slip-rate scaling with mMax="+(float)mMax);

		// moment in the total GR at or above each section's inversion minimum magnitude
		double sumAvailMoment = 0d;
		for (int s=0; s<numSections; s++) {
			double fractAssoc = assoc.getSectionFractInRegion(s);
			if (fractAssoc > 0d) {
				// fraction of total associated fault moment represented by this section
				double sectFract = fractAssoc*moments[s]/assocFaultMoment;
				int sectMminIndex = refMFD.getClosestXIndex(sectMmins[s]);
				for (int m=sectMminIndex; m<=mMaxIndex; m++)
					sumAvailMoment += sectFract*totalGR.getMomentRate(m);
			}
		}

		System.out.println("Total GR has "+totalGR.getTotalMomentRate());
		System.out.println("Inversion-available moment is "+sumAvailMoment+" ("
				+new DecimalFormat("0.00%").format(sumAvailMoment/totalGR.getTotalMomentRate())+" of total GR)");
		double dmScale = sumAvailMoment/assocFaultMoment;
		System.out.println("DM scale = "+(float)sumAvailMoment+" / "+(float)assocFaultMoment+" = "+(float)dmScale);

		double[] scaledSlipRates = new double[numSections];
		double[] scaledSlipSDs = new double[numSections];
		for (int s=0; s<numSections; s++) {
			double inputFullSlipRate = inputFullSlipRates[s];
			double inputCoupledSlipRate = inputCoupledSlipRates[s];
			double inputSlipRateSD = inputFullSlipSDs[s];

			FaultSection sect = rupSet.getFaultSectionData(s);
			Preconditions.checkState(sect instanceof GeoJSONFaultSection);
			GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
			Preconditions.checkState(!geoSect.getProperties().containsKey(GEOJSON_INPUT_SLIP_RATE_PROP_NAME),
					"The extrapolated MFD slip-rate scaling was already applied!");
			double coupling = sect.getCouplingCoeff();

			if (inputCoupledSlipRate == 0d) {
				scaledSlipRates[s] = 0d;
				scaledSlipSDs[s] = inputSlipRateSD;
				sect.setAveSlipRate(0d);
				sect.setSlipRateStdDev(inputSlipRateSD*1e3);
			} else {
				double scaledCoupledSlip = inputCoupledSlipRate*dmScale;
				scaledSlipRates[s] = scaledCoupledSlip;
				double withoutCouplingApplied = scaledCoupledSlip/coupling;

				double slipSD;
				if (NSHM27_InterfaceDeformationModels.isHardcodedFractionalStdDev())
					slipSD = NSHM27_InterfaceDeformationModels.HARDCODED_FRACTIONAL_STD_DEV*withoutCouplingApplied;
				else
					slipSD = NSHM27_InterfaceDeformationModels.DEFAULT_FRACT_SLIP_STD_DEV*withoutCouplingApplied;
				slipSD = Math.max(slipSD, NSHM27_InterfaceDeformationModels.STD_DEV_FLOOR*1e-3);
				scaledSlipSDs[s] = slipSD;

				// Store the uncoupled value used by the deformation model; coupling is reapplied downstream.
				sect.setAveSlipRate(withoutCouplingApplied*1e3);
				sect.setSlipRateStdDev(slipSD*1e3);
			}
			geoSect.setProperty(GEOJSON_INPUT_SLIP_RATE_PROP_NAME, inputFullSlipRate*1e3);
			geoSect.setProperty(GEOJSON_INPUT_SLIP_SD_PROP_NAME, inputSlipRateSD*1e3);
		}
		return SectSlipRates.precomputed(rupSet, scaledSlipRates, scaledSlipSDs);
	}

	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	private static final class ModelNode implements SectionSupraSeisBValues.FixedWeight {
		private final NSHM27_InterfaceMFDModel model;

		private ModelNode(NSHM27_InterfaceMFDModel model) { this.model = model; }

		@Override public String getName() { return model.getName(); }
		@Override public String getShortName() { return model.getShortName(); }
		@Override public String getFilePrefix() { return model.getFilePrefix(); }
		@Override public double getNodeWeight() { return 1d; }
		@Override public double[] getSectBValues(FaultSystemRupSet rupSet,
				LogicTreeBranch<? extends LogicTreeNode> branch) {
			return model.getSectBValues(rupSet, branch);
		}
		@Override public double getB(FaultSystemRupSet rupSet,
				LogicTreeBranch<? extends LogicTreeNode> branch) {
			return model.getB(rupSet, branch);
		}
	}

	private static final class SingleModelLevel extends DataBackedLevel<ModelNode> {
		private NSHM27_InterfaceMFDModel model;
		private ModelNode node;

		@SuppressWarnings("unused") // deserialization
		private SingleModelLevel() {}

		private SingleModelLevel(NSHM27_InterfaceMFDModel model) {
			super(model.getName(), model.getShortName());
			this.model = model;
		}

		@Override public Class<? extends ModelNode> getType() {
			return ModelNode.class;
		}

		@Override public List<? extends ModelNode> getNodes() {
			if (node == null)
				node = new ModelNode(model);
			return List.of(node);
		}

		@Override public boolean isMember(LogicTreeNode node) { return node == this.node; }

		@Override public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("model", new JsonPrimitive(model.name()));
			return json;
		}

		@Override public void initFromJsonObject(JsonObject jsonObj) {
			model = NSHM27_InterfaceMFDModel.valueOf(jsonObj.get("model").getAsString());
			node = null;
		}
	}

	private static final class IndependentBLevel
	extends SectionSupraSeisBValues.DistributionSamplingLevel {
		@SuppressWarnings("unused") // deserialization
		private IndependentBLevel(String name, String shortName) {
			super(name, shortName);
		}

		private IndependentBLevel(ContinuousDistribution distribution) {
			super("Independent Interface Inversion b-value", "Independent-b", distribution);
		}

		@Override
		public String getDistributionString() {
			return "b∈"+super.getDistributionString();
		}
	}

	public static final class SampledNode
	extends CombinedSamplingNode<SectionSupraSeisBValues.FixedWeight>
	implements SectionSupraSeisBValues {
		private FixedWeight bValueNode;
		private NSHM27_InterfaceMFDModel model;

		@SuppressWarnings("unused") // deserialization
		private SampledNode() {}

		private SampledNode(int[] indexes, FixedWeight value, double weight,
				String name, String shortName, String filePrefix) {
			super(indexes, value, int[].class, weight, name, shortName, filePrefix);
		}

		@Override
		protected void setNodeValue(FixedWeight node) {
			super.setNodeValue(node);
			bValueNode = node;
			model = node instanceof ModelNode ? ((ModelNode)node).model : INDEPENDENT;
		}

		public NSHM27_InterfaceMFDModel getModel() { return model; }

		@Override
		public double[] getSectBValues(FaultSystemRupSet rupSet,
				LogicTreeBranch<? extends LogicTreeNode> branch) {
			return bValueNode.getSectBValues(rupSet, branch);
		}

		@Override
		public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return bValueNode.getB(rupSet, branch);
		}
	}

	public static final class SamplingLevel extends
	LogicTreeLevel.AbstractCombinedSamplingLevel<SectionSupraSeisBValues.FixedWeight, SampledNode> {
		@SuppressWarnings("unused") // deserialization
		private SamplingLevel(String name, String shortName) {
			super(name, shortName);
		}

		public SamplingLevel(ContinuousDistribution independentBDist) {
			super("Interface MFD model", "Interface-MFD", "InterfaceMFDSample");
			IndependentBLevel independent = new IndependentBLevel(independentBDist);
			WeightedList<LogicTreeLevel<? extends SectionSupraSeisBValues.FixedWeight>> levels =
					new WeightedList<>(3);
			levels.add(new SingleModelLevel(EXTRAPOLATED), EXTRAPOLATED.weight);
			levels.add(new SingleModelLevel(HINGED), HINGED.weight);
			levels.add(independent, INDEPENDENT.weight);
			init(levels);
		}

		@Override public Class<? extends int[]> getValueType() { return int[].class; }
		@Override public Class<? extends SampledNode> getType() { return SampledNode.class; }

		@Override
		protected SampledNode buildCombinedNode(int[] indexes, FixedWeight value,
				double weight, String name, String shortName, String filePrefix) {
			NSHM27_InterfaceMFDModel model = value instanceof ModelNode ?
					((ModelNode)value).model : INDEPENDENT;
			if (model == INDEPENDENT) {
				name = model.getShortName()+", "+name;
				shortName = model.getShortName()+" "+shortName;
			}
			return new SampledNode(indexes, value, weight, name, shortName, filePrefix);
		}
	}

	/** Returns the nonnegative b-value that hinges the moment-balanced inversion MFD to observed seismicity. */
	public static double calcInterfaceHingedBValue(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		return Math.max(0d, calcRawInterfaceHingedBValue(rupSet, branch));
	}

	/** Returns the unclipped b-value that hinges the moment-balanced inversion MFD to observed seismicity. */
	public static double calcRawInterfaceHingedBValue(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		// figure out included rupture mMin and mMax
		int numSects = rupSet.getNumSections();
		int numRups = rupSet.getNumRuptures();
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		BinaryRuptureProbabilityCalc rupExclusionModel =
				NSHM27_InvConfigFactory.buildExclusionModel(rupSet, branch, cRups);
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
		double totalMomentSum = 0d;
		int positiveMomentSects = 0;
		int positiveAssocMomentSects = 0;
		SectSlipRates slips = rupSet.requireModule(SectSlipRates.class);
		for (int s=0; s<numSects; s++) {
			double sectMoment = slips.calcMomentRate(s);
			Preconditions.checkState(Double.isFinite(sectMoment));
			if (sectMoment > 0d) {
				totalMomentSum += sectMoment;
				positiveMomentSects++;
			}
			double assocFract = assoc.getSectionFractInRegion(s);
			Preconditions.checkState(Double.isFinite(assocFract),
					"Bad association fraction for section %s: %s", Integer.valueOf(s), Double.valueOf(assocFract));
			double moment = sectMoment*assocFract;
			Preconditions.checkState(Double.isFinite(moment));
			if (moment == 0d)
				continue;
			positiveAssocMomentSects++;
			Preconditions.checkState(Double.isFinite(sectMmins[s]));
			momentSum += moment;
			momentWeightedMminSum += sectMmins[s]*moment;
		}
		Preconditions.checkState(momentSum > 0d, "No positive associated interface moment for branch %s: "
				+"totalMoment=%s, positiveMomentSects=%s/%s, positiveAssocMomentSects=%s/%s",
				branch, totalMomentSum, positiveMomentSects, numSects,
				positiveAssocMomentSects, numSects);
		double mMin = momentWeightedMminSum/momentSum;

		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(NSHM27_GridSourceBuilder.OVERALL_MMIN, mMax+0.1);
		int mMinIndex = refMFD.getClosestXIndex(mMin);
		Preconditions.checkState(mMinIndex > 0, "should be far from the first bin");
		int mMaxIndex = refMFD.getClosestXIndex(mMax);
		Preconditions.checkState(mMaxIndex > mMinIndex);
		mMin = refMFD.getX(mMinIndex);
		mMax = refMFD.getX(mMaxIndex);

		// obs seis MFD
		NSHM27_SeismicityRegions seisRegion = branch.requireValue(NSHM27_InterfaceFaultModels.class)
				.getSeismicityRegion();
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
			rateAtMmin *= obsMFD2.getY(mMinIndex-1)/obsMFD.getY(mMinIndex-1);

		System.out.println("Calculating hinged b-value for incrRate["+(float)mMin+"]="+(float)rateAtMmin);
		GutenbergRichterMagFreqDist faultGR = new GutenbergRichterMagFreqDist(
				refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
		faultGR.setAllButBvalueForIncrRate(mMin, mMax, momentSum, rateAtMmin);
		System.out.println("Calculated hinged b="+(float)faultGR.get_bValue()+"; faultIncr["+(float)mMin+"]="
				+faultGR.getY(mMinIndex)+", faultTotal="+faultGR.calcSumOfY_Vals());
		return faultGR.get_bValue();
	}
}
