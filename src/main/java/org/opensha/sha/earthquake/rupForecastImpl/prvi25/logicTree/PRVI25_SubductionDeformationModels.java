package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.earthquake.faultSysSolution.util.minisections.MinisectionMappings;
import org.opensha.sha.earthquake.faultSysSolution.util.minisections.MinisectionSlipRecord;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum PRVI25_SubductionDeformationModels implements RupSetDeformationModel {
	FULL("Full Rate", "Full", 0.5,
			Map.of(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE, "PRVI_sub_v1_large_full_rate_minisections.txt",
					PRVI25_SubductionFaultModels.PRVI_SUB_FM_SMALL, "PRVI_sub_v1_small_full_rate_minisections.txt")),
	PARTIAL("Partial Rate", "Partial", 0.5,
			Map.of(PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE, "PRVI_sub_v1_large_part_rate_minisections.txt",
					PRVI25_SubductionFaultModels.PRVI_SUB_FM_SMALL, "PRVI_sub_v1_small_part_rate_minisections.txt"));

	private static final String PREFIX = "/data/erf/prvi25/def_models/subduction/";
	
	/**
	 * if standard deviation is zero, default to this fraction of the slip rate. if DEFAULT_STD_DEV_USE_GEOLOGIC is
	 * true, then this will only be used if the geologic slip rate standard deviation is also zero 
	 */
	private static final double DEFAULT_FRACT_SLIP_STD_DEV = 0.1;
	/**
	 * minimum allowed slip rate standart deviation, in mm/yr
	 */
	public static final double STD_DEV_FLOOR = 1e-4;
	
	/**
	 * If nonzero, will use this fractional standard deviation everywhere rather than those from the deformation model
	 */
	public static double HARDCODED_FRACTIONAL_STD_DEV = 0d;
	
	/**
	 * If nonzero, will use this fractional standard deviation as an upper bound, retaining the original value if less
	 */
	public static double HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND = 0.1;
	
	/**
	 * For a given creep fractional moment reduction, creepRed:
	 * 
	 * if (creepRed <= ASEIS_CEILING) {
	 * 	aseis = creepRed
	 * 	coupling = 1
	 * } else {
	 * 	aseis = ASEIS_CEILING
	 * 	coupling = 1 - (1/(1-ASEIS_CEILING))*(creepRed - ASEIS_CEILING)
	 * }
	 * 
	 * In UCERF3, this was set to 0.9.
	 */
	public static double ASEIS_CEILING = 0.4;
	
	/**
	 * If no creep value is available, use the given default creep reduction value
	 */
	public static double CREEP_FRACT_DEFAULT = 0.1;
	
	private String name;
	private String shortName;
	private Map<RupSetFaultModel, String> fNameMap;
	private double weight;
	
	private Map<String, Map<Integer, List<MinisectionSlipRecord>>> dmMinisMap;

	private PRVI25_SubductionDeformationModels(String name, String shortName, double weight,
			Map<RupSetFaultModel, String> fNameMap) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.fNameMap = fNameMap;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
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
		return faultModel instanceof PRVI25_SubductionFaultModels;
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
		return build(faultModel, 2, 0.5, 30d);
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel, int minPerFault, double ddwFract,
			double fixedLen) throws IOException {
		String minisectsFileName = fNameMap.get(faultModel);
		System.out.println("Mapping slip rates for "+faultModel.getShortName()+", "+this.getShortName()+": "+minisectsFileName);
		Preconditions.checkNotNull(minisectsFileName, "No minisection file mapping for fm=%s", faultModel);
		List<? extends FaultSection> fullSects = faultModel.getFaultSections();
		return buildDefModel(SubSectionBuilder.buildSubSects(
				faultModel.getFaultSections(), minPerFault, ddwFract, fixedLen), fullSects, minisectsFileName);
	}

	@Override
	public List<? extends FaultSection> buildForSubsects(RupSetFaultModel faultModel,
			List<? extends FaultSection> subSects) throws IOException {
		String minisectsFileName = fNameMap.get(faultModel);
		Preconditions.checkNotNull(minisectsFileName, "No minisection file mapping for fm=%s", faultModel);
		List<? extends FaultSection> fullSects = faultModel.getFaultSections();
		return buildDefModel(subSects, fullSects, minisectsFileName);
	}
	
	private List<? extends FaultSection> buildDefModel(List<? extends FaultSection> subSects,
			List<? extends FaultSection> fullSects, String minisectsFileName) throws IOException {
		applySlipRates(subSects, fullSects, minisectsFileName);
		applyStdDevDefaults(subSects);
		applyCreepDefaults(subSects);
		return subSects;
	}
	
	protected void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects,
			String minisectsFileName) throws IOException {
		Map<Integer, List<MinisectionSlipRecord>> dmMinis;
		synchronized (this) {
			if (dmMinisMap == null) {
				dmMinisMap = new HashMap<>();
			}
			dmMinis = dmMinisMap.get(minisectsFileName);
			if (dmMinis == null) {
				InputStream is = PRVI25_CrustalDeformationModels.class.getResourceAsStream(PREFIX+minisectsFileName);
				dmMinis = MinisectionSlipRecord.readMinisectionsFile(is);
				is.close();
				dmMinisMap.put(minisectsFileName, dmMinis);
			}
		}
		MinisectionMappings mappings = new MinisectionMappings(fullSects, subSects);
		mappings.mapDefModelMinisToSubSects(dmMinis);
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
	
	private void applyCreepDefaults(List<? extends FaultSection> subSects) {
		double creepFract = CREEP_FRACT_DEFAULT;
		double aseis, coupling;
		if (creepFract < ASEIS_CEILING) {
			aseis = creepFract;
			coupling = 1d;
		} else {
			aseis = ASEIS_CEILING;
			coupling = 1 - (1/(1-ASEIS_CEILING))*(creepFract - ASEIS_CEILING);
		}
		for (FaultSection subSect : subSects) {
			subSect.setAseismicSlipFactor(aseis);
			subSect.setCouplingCoeff(coupling);
		}
	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");

}
