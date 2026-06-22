package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.AverageSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.FixedSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.RupSetDeformationModelDistribution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.subduction.InterfaceDeformationProjection;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public class NSHM27_InterfaceDeformationModels extends RupSetDeformationModelDistribution<FixedSampler> {
	
	public static class SamplingLevel extends RupSetDeformationModelDistribution.UniformSamplingLevel<NSHM27_InterfaceDeformationModels> {
		
		public static String NAME = "Crustal Deformation Model Sample";
		public static String SHORT_NAME = "DMSample";

		public SamplingLevel() {
			super(NAME, SHORT_NAME);
		}

		@Override
		public NSHM27_InterfaceDeformationModels build(FixedFractileSampler value, double weight, String name,
				String shortName, String filePrefix) {
			return new NSHM27_InterfaceDeformationModels(name, shortName, filePrefix, weight, value);
		}

		@Override
		public Class<? extends NSHM27_InterfaceDeformationModels> getType() {
			return NSHM27_InterfaceDeformationModels.class;
		}
		
	}
	
	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static enum Aggregated implements RupSetDeformationModel, FixedWeightNode {
		LOW_COUPLING("Low Interface Coupling", "Low", 1d, new FixedFractileSampler(0.025)),
		PREF_COUPLING("Preferred Interface Coupling", "Preferred", 1d, new AverageSampler()),
		HIGH_COUPLING("High Interface Coupling", "High", 1d, new FixedFractileSampler(0.975));
		
		private String name;
		private String shortName;
		private double weight;
		private FixedSampler sampler;
		
		private Aggregated(String name, String shortName, double weight, FixedSampler sampler) {
			this.name = name;
			this.shortName = shortName;
			this.weight = weight;
			this.sampler = sampler;
		}

		@Override
		public double getNodeWeight() {
			return weight;
		}

		@Override
		public String getFilePrefix() {
			return name();
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
		public boolean isApplicableTo(RupSetFaultModel faultModel) {
			return faultModel instanceof NSHM27_InterfaceFaultModels;
		}
		
		public FixedSampler getSampler() {
			return sampler;
		}

		@Override
		public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
				LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
				List<? extends FaultSection> subSects) throws IOException {
			Preconditions.checkState(faultModel instanceof NSHM27_InterfaceFaultModels);
			NSHM27_InterfaceFaultModels fm = (NSHM27_InterfaceFaultModels)faultModel;
			DeformationFront df = getDeformationFront(fm);
			
			NSHM27_InterfaceCouplingDepthModels depthCoupling = branch.getValue(
					NSHM27_InterfaceCouplingDepthModels.class);
			
			return NSHM27_InterfaceDeformationModels.apply(subSects, df, depthCoupling, sampler);
		}
	}

	@SuppressWarnings("unused") // for deserialization
	private NSHM27_InterfaceDeformationModels() {}
	
	NSHM27_InterfaceDeformationModels(String name, String shortName, String filePrefix,
			double weight, FixedSampler sampler) {
		super(name, shortName, filePrefix, weight, sampler);
	}
	
	/**
	 * if standard deviation is zero, default to this fraction of the slip rate. if DEFAULT_STD_DEV_USE_GEOLOGIC is
	 * true, then this will only be used if the geologic slip rate standard deviation is also zero 
	 */
	private static final double DEFAULT_FRACT_SLIP_STD_DEV = 0.1;
	/**
	 * minimum allowed slip rate standart deviation, in mm/yr
	 */
	public static final double STD_DEV_FLOOR = 1;
	
	/**
	 * If nonzero, will use this fractional standard deviation everywhere rather than those from the deformation model
	 */
	public static double HARDCODED_FRACTIONAL_STD_DEV = 0d;
	
	/**
	 * If nonzero, will use this fractional standard deviation as an upper bound, retaining the original value if less
	 */
	public static double HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND = 0.1;

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModel instanceof NSHM27_InterfaceFaultModels;
	}
	
	public record DeformationFront(FaultTrace trace, double[] convergence, ContinuousDistribution[] couplingDists) {}
	
	public static DeformationFront getDeformationFront(NSHM27_InterfaceFaultModels fm) throws IOException {
		String csvPath;
		if (fm == NSHM27_InterfaceFaultModels.AMSAM_V1) {
			csvPath = "/data/erf/nshm27/amsam/deformation_models/subduction/ker_trace_dm.csv";
		} else if (fm == NSHM27_InterfaceFaultModels.GNMI_V1) {
			csvPath = "/data/erf/nshm27/gnmi/deformation_models/subduction/izu_trace_dm.csv";
		} else {
			throw new IllegalStateException("Unexpected FM: "+fm);
		}
//		InputStream is = NSHM27_InterfaceDeformationModels.class.getResourceAsStream(csvPath);
//		Preconditions.checkNotNull(is, "Couldn't load CSV: %s", csvPath);
//		CSVFile<String> csv = CSVFile.readStream(is, true);
//		FaultTrace trace = new FaultTrace(fm.getName(), csv.getNumRows()-1);
//		double[] slips = new double[csv.getNumRows()-1];
//		int column;
//		switch (this) {
//		case LOW_COUPLING:
//			column = 6;
//			break;
//		case PREF_COUPLING:
//			column = 7;
//			break;
//		case HIGH_COUPLING:
//			column = 8;
//			break;
//
//		default:
//			throw new IllegalStateException("Unexpected model: "+this);
//		}
//		
//		List<? extends FaultSection> fullSects = fm.getFaultSections();
//		Preconditions.checkState(fullSects.size() == 1);
//		FaultSection fullSect = fullSects.get(0);
//		
//		for (int i=0; i<slips.length; i++) {
//			int row = i+1;
//			Location loc = new Location(csv.getDouble(row, 1), csv.getDouble(row, 0));
//			if (loc.lon < 0)
//				loc = new Location(loc.lat, loc.lon+360d);
//			trace.add(loc);
//			slips[i] = csv.getDouble(row, column);
//			Preconditions.checkState(Double.isFinite(slips[i]) && slips[i] >= 0, "Bad slip rate: %s", slips[i]);
//		}
//		
//		InterfaceDeformationProjection.checkForTraceDirection(fullSect.getFaultTrace(), trace, slips);
//		
//		if (fm.getSlipSmoothingDistance() > 0)
//			slips = InterfaceDeformationProjection.getSmoothedDeformationFrontSlipRates(trace, slips, fm.getSlipSmoothingDistance());
//		
//		return new DeformationFront(trace, slips);
		// TODO
		return null;
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		Preconditions.checkState(faultModel instanceof NSHM27_InterfaceFaultModels);
		NSHM27_InterfaceFaultModels fm = (NSHM27_InterfaceFaultModels)faultModel;
		DeformationFront df = getDeformationFront(fm);
		
		FixedSampler sampler = getValue();
		
		NSHM27_InterfaceCouplingDepthModels depthCoupling = branch.getValue(
				NSHM27_InterfaceCouplingDepthModels.class);
		
		return apply(subSects, df, depthCoupling, sampler);
	}
	
	public static double[] getCoupledSlipRates(DeformationFront df, FixedSampler sampler) {
		double[] slips = new double[df.trace.size()];
		Preconditions.checkState(df.trace.size() == df.convergence.length);
		Preconditions.checkState(df.trace.size() == df.couplingDists.length);
		for (int i=0; i<slips.length; i++)
			slips[i] = df.convergence[i] * sampler.getValue(df.couplingDists[i]);
		return slips;
	}
	
	public static List<? extends FaultSection> apply(List<? extends FaultSection> subSects, DeformationFront df,
			NSHM27_InterfaceCouplingDepthModels depthCoupling, FixedSampler sampler) {
		double[] slips = getCoupledSlipRates(df, sampler);
		
		InterfaceDeformationProjection.projectSlipRates(subSects, df.trace, slips);
		
		if (depthCoupling != null) {
			System.out.println("Applying depth-coupling model: "+depthCoupling);
			depthCoupling.apply(subSects);
		}
		
		applyStdDevDefaults(subSects);
		
		return subSects;
	}
	
	public static boolean isHardcodedFractionalStdDev() {
		return HARDCODED_FRACTIONAL_STD_DEV > 0d;
	}
	
	public static void applyStdDevDefaults(List<? extends FaultSection> subSects) {
		if (isHardcodedFractionalStdDev()) {
			System.out.println("Overriding deformation model slip rates std devs and using hardcoded fractional value: "
					+HARDCODED_FRACTIONAL_STD_DEV);
			Preconditions.checkState(!(HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND > 0d),
					"Can't supply both hardcoded fractional std dev, and a fractional upper bound");
		}
		
		int numZeroSlips = 0;
		int numFractDefault = 0;
		int numFloor = 0;
		
		for (FaultSection sect : subSects) {
			double slipRate = sect.getOrigAveSlipRate();
			Preconditions.checkState(Double.isFinite(slipRate) && slipRate >= 0d, "Bad slip rate for %s. %s: %s",
					sect.getSectionId(), sect.getSectionName(), slipRate);
			if ((float)slipRate == 0f)
				numZeroSlips++;
			
			if (sect instanceof GeoJSONFaultSection && slipRate > 0d) {
				double origStdDev = sect.getOrigSlipRateStdDev();
				if (Double.isFinite(origStdDev))
					((GeoJSONFaultSection)sect).setProperty(NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, origStdDev/slipRate);
			}
			
			double stdDev;
			if (isHardcodedFractionalStdDev()) {
				stdDev = HARDCODED_FRACTIONAL_STD_DEV * slipRate;
			} else {
				// use value from the deformation model
				stdDev = sect.getOrigSlipRateStdDev();
				if (!Double.isFinite(stdDev))
					stdDev = 0d;
				
				if (HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND > 0d) {
					double floorSD = HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND * slipRate;
					if ((float)stdDev <= 0f || floorSD < stdDev)
						stdDev = floorSD;
				}
				
				if ((float)stdDev <= 0f) {
					// no slip rate std dev specified
					
					if ((float)stdDev <= 0f) {
						// didn't use fallback (DEFAULT_STD_DEV_USE_GEOLOGIC == false, or no geologic value)
						// set it to the given default fraction of the slip rate
						stdDev = slipRate*DEFAULT_FRACT_SLIP_STD_DEV;
						numFractDefault++;
					}
				}
			}
			
			// now apply any floor
			if ((float)stdDev < (float)STD_DEV_FLOOR) {
				stdDev = STD_DEV_FLOOR;
				numFloor++;
			}
			
			sect.setSlipRateStdDev(stdDev);
		}

		if (numZeroSlips > 0)
			System.err.println("WARNING: "+numZeroSlips+"/"+subSects.size()+" ("
					+pDF.format((double)numZeroSlips/(double)subSects.size())+") subsection slip rates are 0");
		if (numFractDefault > 0)
			System.err.println("WARNING: Set "+numFractDefault+"/"+subSects.size()+" ("
					+pDF.format((double)numFractDefault/(double)subSects.size())
					+") subsection slip rate standard deviations to the default: "
					+(float)DEFAULT_FRACT_SLIP_STD_DEV+" x slipRate");
		if (numFloor > 0)
			System.err.println("WARNING: Set "+numFloor+"/"+subSects.size()+" ("
					+pDF.format((double)numFloor/(double)subSects.size())
					+") subsection slip rate standard deviations to the floor value of "+(float)STD_DEV_FLOOR+" (mm/yr)");
	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
}
