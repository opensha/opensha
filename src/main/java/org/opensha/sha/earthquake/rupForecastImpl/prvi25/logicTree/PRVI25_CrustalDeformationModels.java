package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
//@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing enabled
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing disabled
public enum PRVI25_CrustalDeformationModels implements RupSetDeformationModel {
	GEOLOGIC("Geologic Preferred", "Preferred", 0.9d) {
		@Override
		protected void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
			applyGeologicSlipRates(subSects, fullSects, RateType.PREF);
		}
	},
	GEOLOGIC_HIGH("Geologic High", "High", 0.05) {
		@Override
		protected void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
			applyGeologicSlipRates(subSects, fullSects, RateType.HIGH);
		}
	},
	GEOLOGIC_LOW("Geologic Low", "Low", 0.05) {
		@Override
		protected void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
			applyGeologicSlipRates(subSects, fullSects, RateType.LOW);
		}
	},
	GEOLOGIC_DIST_AVG("Geologic Distribution Average", "GeologicDistAvg", 0.0d) {
		@Override
		protected void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
			try {
				PRVI25_CrustalRandomlySampledDeformationModels.applyDistAvgSlipRates(subSects, fullSects);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
	};
	
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
	
	private static Table<String, RateType, Double> csvRateTable = null;
	
	private enum RateType {
		PREF(GeoJSONFaultSection.SLIP_RATE, 1),
		LOW(PRVI25_CrustalFaultModels.LOW_RATE_PROP_NAME, 2),
		HIGH(PRVI25_CrustalFaultModels.HIGH_RATE_PROP_NAME, 3);
		
		private String propName;
		private int csvColumn;

		private RateType(String propName, int csvColumn) {
			this.propName = propName;
			this.csvColumn = csvColumn;
			
		}
	};
	
	public static boolean USE_DM_CSV_FILE = true;
	private static final String CSV_FILE_PATH = "/data/erf/prvi25/def_models/crustal/BinRanges_RakeDipProjected_Out_PrefRate.csv";
	
	private static synchronized Table<String, RateType, Double> checkLoadCSVRates() throws IOException {
		if (csvRateTable == null) {
			InputStream is = PRVI25_CrustalDeformationModels.class.getResourceAsStream(CSV_FILE_PATH);
			Preconditions.checkNotNull(is, "Couldn't locate CSV: %s", CSV_FILE_PATH);
			CSVFile<String> csv = CSVFile.readStream(is, true);
			Table<String, RateType, Double> table = HashBasedTable.create();
			for (int row=1; row<csv.getNumRows(); row++) {
				String name = csv.get(row, 0);
				for (RateType type : RateType.values()) {
					double val = csv.getDouble(row, type.csvColumn);
					Preconditions.checkState(Double.isFinite(val) && val >= 0d);
					table.put(name, type, val);
				}
			}
			csvRateTable = table;
		}
		return csvRateTable;
	}
	
	private String name;
	private String shortName;
	private double weight;

	private PRVI25_CrustalDeformationModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
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
		return faultModel instanceof PRVI25_CrustalFaultModels;
	}
	
	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		return buildDefModel(subSects, fullSects);
	}

	private List<? extends FaultSection> buildDefModel(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
		applySlipRates(subSects, fullSects);
		applyStdDevDefaults(subSects);
		applyCreepDefaults(subSects);
		return subSects;
	}
	
	protected abstract void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException;
	
	protected static void applyGeologicSlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects, RateType type)
			throws IOException {
		Map<Integer, FaultSection> idMapped = new HashMap<>();
		for (FaultSection sect : fullSects)
			idMapped.put(sect.getSectionId(), sect);
		
		for (FaultSection subSect : subSects) {
			FaultSection origSect = idMapped.get(subSect.getParentSectionId());
			Preconditions.checkNotNull(origSect);
			Preconditions.checkState(origSect.getSectionName().equals(subSect.getParentSectionName()));
			
			double slip = getSlipRate(origSect, type);
			subSect.setAveSlipRate(slip);
		}
	}
	
	protected static double getSlipRate(FaultSection origSect, RateType type) throws IOException {
		double slip;
		if (USE_DM_CSV_FILE) {
			checkLoadCSVRates();
			slip = csvRateTable.get(origSect.getSectionName(), type);
		} else {
			Preconditions.checkState(origSect instanceof GeoJSONFaultSection);
			slip = ((GeoJSONFaultSection)origSect).getProperties().getDouble(type.propName, Double.NaN);
		}
		Preconditions.checkState(Double.isFinite(slip));
		return slip;
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
	
	static void applyCreepDefaults(List<? extends FaultSection> subSects) {
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
	
	public static void main(String[] args) throws IOException {
		PRVI25_CrustalFaultModels fm = PRVI25_CrustalFaultModels.PRVI_CRUSTAL_FM_V1p1;
//		for (PRVI25_CrustalDeformationModels dm : values()) {
//			List<? extends FaultSection> subSects = dm.build(fm);
//			GeoJSONFaultReader.writeFaultSections(new File("/tmp/"+fm.getFilePrefix()+"_"+dm.getFilePrefix()+"_subSects.geojson"), subSects);
//		}
		
		for (FaultSection sect : fm.getFaultSections()) {
			System.out.println(sect.getSectionName());
			System.out.println("\tPref:\t"+(float)getSlipRate(sect, RateType.PREF));
			System.out.println("\tBounds:\t["+(float)getSlipRate(sect, RateType.LOW)+", "+(float)getSlipRate(sect, RateType.HIGH)+"]");
		}
	}

}
