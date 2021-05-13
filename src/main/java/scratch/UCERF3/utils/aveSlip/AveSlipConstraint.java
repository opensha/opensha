package scratch.UCERF3.utils.aveSlip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class AveSlipConstraint implements Serializable {
	
	public static final String DIR_NAME = "aveSlip";
	public static final String TABLE_5_FILE_NAME = "Table R5v4_withMappings.xls";
	public static final String TABLE_6_FILE_NAME = "Table R6v5_withMappings.xls";
	
	private static ArbitrarilyDiscretizedFunc probObsSlipModel;
	static {
		probObsSlipModel = new ArbitrarilyDiscretizedFunc();
		// meters, probability
		// this is different from Ramon's original proposed values, maxes out at 90%. Approved by Ramon
		probObsSlipModel.set(0d, 0.0d);
		probObsSlipModel.set(0.25d, 0.1d);
		probObsSlipModel.set(2d, 0.90d);
	}
	
	private int subSectionIndex;
	private String subSectionName;
	private double weightedMean;
	private double upperUncertaintyBound;
	private double lowerUncertaintyBound;
	private Location loc;
	
	public AveSlipConstraint(int subSectionIndex, String subSectionName, double weightedMean,
			double upperUncertaintyBound, double lowerUncertaintyBound,
			Location loc) {
		super();
		this.subSectionIndex = subSectionIndex;
		this.subSectionName = subSectionName;
		this.weightedMean = weightedMean;
		this.upperUncertaintyBound = upperUncertaintyBound;
		this.lowerUncertaintyBound = lowerUncertaintyBound;
		this.loc = loc;
	}

	public int getSubSectionIndex() {
		return subSectionIndex;
	}
	
	public String getSubSectionName() {
		return subSectionName;
	}

	/**
	 * Weighted mean slip, in meters
	 * 
	 * @return
	 */
	public double getWeightedMean() {
		return weightedMean;
	}

	/**
	 * "Uncertainties is half-width of COPD peak. For asymetrical uncertainties, we conservatively
	 * use larger uncertainty in weighted mean calculation."
	 * 
	 * @return
	 */
	public double getUpperUncertaintyBound() {
		return upperUncertaintyBound;
	}

	/**
	 * "Uncertainties is half-width of COPD peak. For asymetrical uncertainties, we conservatively
	 * use larger uncertainty in weighted mean calculation."
	 * 
	 * @return
	 */
	public double getLowerUncertaintyBound() {
		return lowerUncertaintyBound;
	}
	
	public Location getSiteLocation() {
		return loc;
	}
	
	public static double getProbabilityOfObservedSlip(double meters) {
		if (meters > probObsSlipModel.getMaxX())
			return probObsSlipModel.getY(probObsSlipModel.size()-1);
		return probObsSlipModel.getInterpolatedY(meters);
	}
	
	@Override
	public String toString() {
		return "AveSlipConstraint [subSectionIndex=" + subSectionIndex
				+ ", weightedMean=" + weightedMean + ", upperUncertaintyBound="
				+ upperUncertaintyBound + ", lowerUncertaintyBound="
				+ lowerUncertaintyBound + "]";
	}

	public static List<AveSlipConstraint> load(List<? extends FaultSection> subSectData) throws IOException {
		List<AveSlipConstraint> aveSlipData =
			load(UCERF3_DataUtils.locateResourceAsStream(DIR_NAME, TABLE_5_FILE_NAME), subSectData);
		aveSlipData.addAll(
				load(UCERF3_DataUtils.locateResourceAsStream(DIR_NAME, TABLE_6_FILE_NAME), subSectData));
		return aveSlipData;
	}
	
	public static List<AveSlipConstraint> load(
			InputStream is, List<? extends FaultSection> subSectData) throws IOException {
		return load(is, subSectData, -1, null);
	}
	
	private static List<AveSlipConstraint> load(
			InputStream is, List<? extends FaultSection> subSectData,
			int mappingCol, File mappingFile) throws IOException {
		Map<Integer, List<FaultSection>> parentSectsMap = Maps.newHashMap();
		for (FaultSection data : subSectData) {
			Integer parentID = data.getParentSectionId();
			List<FaultSection> subSectsForParent = parentSectsMap.get(parentID);
			if (subSectsForParent == null) {
				subSectsForParent = Lists.newArrayList();
				parentSectsMap.put(parentID, subSectsForParent);
			}
			subSectsForParent.add(data);
		}
		
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		
		List<AveSlipConstraint> constraints = Lists.newArrayList();
		
		int lastRowIndex = sheet.getLastRowNum();
		
		int startRowIndex = 0;
		for (int rowIndex=0; rowIndex<=lastRowIndex; rowIndex++) {
			if (sheet.getRow(rowIndex).getCell(0).getStringCellValue().trim().equals("Fault")) {
				startRowIndex = rowIndex+1;
				break;
			}
		}
		Preconditions.checkState(startRowIndex > 0, "Couldn't find start row, data file changed?");
		
		for (int rowIndex=startRowIndex; rowIndex<=lastRowIndex; rowIndex++) {
			HSSFRow row = sheet.getRow(rowIndex);
			if (row == null)
				break;
			String faultName = row.getCell(0).getStringCellValue().trim();
			if (faultName.isEmpty())
				continue;
			if (faultName.equals("EXPLANATION") || faultName.isEmpty())
				// we're done
				break;
//			System.out.println(rowIndex+": "+row.getCell(0));
			HSSFCell parentCell = row.getCell(1);
			List<Integer> parentIDs = loadParentIDs(parentCell);
			
			double lat = row.getCell(2).getNumericCellValue();
			double lon = row.getCell(3).getNumericCellValue();
			Location loc = new Location(lat, lon);
			
			boolean blindThrustHack = faultName.startsWith("Compton") || faultName.startsWith("Puente Hills");
			
			FaultSection matchSect = null;
			double minDist = Double.POSITIVE_INFINITY;
			for (Integer parentID : parentIDs) {
				List<FaultSection> subSects = parentSectsMap.get(parentID);
				if (subSects == null)
					continue;
				for (FaultSection subSect : subSects) {
					FaultTrace trace = FaultUtils.resampleTrace(subSect.getFaultTrace(), 11);
					for (Location traceLoc : trace) {
						double dist = LocationUtils.horzDistanceFast(loc, traceLoc);
						if (dist < minDist) {
							minDist = dist;
							matchSect = subSect;
						}
					}
				}
			}
			if (matchSect == null) {
				System.out.println("Skipping ave slip site '"+faultName+"' as parent(s) don't" +
						" exist in given fault model: "+Joiner.on(",").join(parentIDs));
				continue;
			}
			Preconditions.checkState(blindThrustHack || minDist < 5d,
					"no sub sect found within 5km for site on "+faultName+" at: "+loc+" (mindist="+minDist+")");
			double mean = row.getCell(22).getNumericCellValue();
			double uncertaintyPlus = row.getCell(23).getNumericCellValue();
			double uncertaintyMinus = row.getCell(24).getNumericCellValue();
			
			constraints.add(new AveSlipConstraint(matchSect.getSectionId(), matchSect.getSectionName(), mean,
					mean+uncertaintyPlus, mean-uncertaintyMinus, loc));
			
			if (mappingCol > 0) {
				HSSFCell mappingCell = row.getCell(mappingCol);
				mappingCell.setCellValue(matchSect.getName());
			}
		}
		
		if (mappingCol > 0)
			wb.write(new FileOutputStream(mappingFile));
		
		return constraints;
	}
	
	public static List<Integer> loadParentIDs(HSSFCell parentCell) {
		List<Integer> parentIDs = Lists.newArrayList();
		if (parentCell.getCellType() == HSSFCell.CELL_TYPE_STRING) {
			String[] parentSplits = parentCell.getStringCellValue().trim().split(",");
			for (String parentStr : parentSplits)
				parentIDs.add(Integer.parseInt(parentStr.trim()));
		} else {
//			System.out.println("Numeric!");
			int val = (int)parentCell.getNumericCellValue();
			if (val > 0)
				parentIDs.add(val);
		}
		return parentIDs;
	}
	
	public static void main(String[] args) throws IOException {
		int mappingCol = 25;
		File dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParentFile(), DIR_NAME);
		File tableR5File = new File(dir, TABLE_5_FILE_NAME);
		File tableR6File = new File(dir, TABLE_6_FILE_NAME);
		for (FaultModels fm : FaultModels.values()) {
			DeformationModels dm = DeformationModels.forFaultModel(fm).get(0);
			List<? extends FaultSection> subSects = new DeformationModelFetcher(
					fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1d).getSubSectionList();
			load(new FileInputStream(tableR5File), subSects, mappingCol, tableR5File);
			load(new FileInputStream(tableR6File), subSects, mappingCol, tableR6File);
			mappingCol++;
		}
//		FaultModels fm = FaultModels.FM3_1;
//		DeformationModels dm = DeformationModels.forFaultModel(fm).get(0);
//		List<FaultSectionPrefData> subSects = new DeformationModelFetcher(
//						fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1d).getSubSectionList();
//		for (AveSlipConstraint constr : load(subSects)) {
//			System.out.println(constr);
//		}
	}

}
