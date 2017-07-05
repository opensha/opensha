package scratch.UCERF3.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This class loads and stores Last Event Data for UCERF3 subsections.
 * @author kevin
 *
 */
public class LastEventData {
	
	// sub directory of UCERF3/data
	public static final String SUB_DIR = "paleoRateData";
	// file name
	public static final String FILE_NAME = "UCERF3_OpenIntervals_ver11.xls";
	// sheet in the workbook, zero based
	//"Well resolved historical","Paleo-well resolved", and "Unique to FM3.2"
	private static final int[] SHEET_NUMS = {0,1,2};
	
	public static final int OPEN_INTERVAL_BASIS_YEAR = 2013;
	public static final GregorianCalendar OPEN_INTERVAL_BASIS = new GregorianCalendar(OPEN_INTERVAL_BASIS_YEAR, 0, 0);
	private static final double MATCH_LOCATION_TOLERANCE = 1d;
//	private static final double MILLIS_TO_YEARS = (double)(1000*60*24);
	
	private String sectName;
	private int parentSectID;
	private double lastOffset;
	private double openInterval;
	private GregorianCalendar eventDate;
	private Location startLoc;
	private Location endLoc;
	
	/**
	 * Loads last event data for every section where the open interval is specified, grouped
	 * by parent section ID
	 * 
	 * @return
	 * @throws IOException
	 */
	public static Map<Integer, List<LastEventData>> load() throws IOException {
		return load(UCERF3_DataUtils.locateResourceAsStream(SUB_DIR, FILE_NAME));
	}
	
	/**
	 * Loads last event data for every section where the open interval is specified, grouped
	 * by parent section ID
	 * 
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static Map<Integer, List<LastEventData>> load(InputStream is) throws IOException {
		return load(is, SHEET_NUMS);
	}
	
	public static Map<Integer, List<LastEventData>> load(InputStream is, int[] sheets) throws IOException {
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		
		Map<Integer, List<LastEventData>> datas = Maps.newHashMap();
		for (int sheetNum : sheets) {
			HSSFSheet sheet = wb.getSheetAt(sheetNum);
			
			for (int rowIndex=0; rowIndex<=sheet.getLastRowNum(); rowIndex++) {
				HSSFRow row = sheet.getRow(rowIndex);
				if (row == null)
					continue;
				HSSFCell intervalCell = row.getCell(4);
				// only cells with open intervals
				if (intervalCell == null || intervalCell.getCellType() != HSSFCell.CELL_TYPE_NUMERIC)
					continue;
				double interval = intervalCell.getNumericCellValue();
				String name = row.getCell(0).getStringCellValue();
				int parentID = (int)row.getCell(1).getNumericCellValue();
				Preconditions.checkState(parentID >= 0);
				HSSFCell offsetCell = row.getCell(3);
				double offset;
				if (offsetCell == null || offsetCell.getCellType() != HSSFCell.CELL_TYPE_NUMERIC)
					offset = Double.NaN;
				else
					offset = offsetCell.getNumericCellValue();
				// make sure it has a location
				HSSFCell locStartCell = row.getCell(6);
				if (locStartCell == null || locStartCell.getCellType() != HSSFCell.CELL_TYPE_NUMERIC) {
					System.err.println("WARNING: no location for "+name+"...skipping!");
					continue;
				}
				double startLat = row.getCell(6).getNumericCellValue();
				double startLon = row.getCell(7).getNumericCellValue();
				double endLat = row.getCell(8).getNumericCellValue();
				double endLon = row.getCell(9).getNumericCellValue();
				Location startLoc = new Location(startLat, startLon);
				Location endLoc = new Location(endLat, endLon);
				
				List<LastEventData> parentList = datas.get(parentID);
				if (parentList == null) {
					parentList = Lists.newArrayList();
					datas.put(parentID, parentList);
				}
				
				parentList.add(new LastEventData(name, parentID, offset, interval, startLoc, endLoc));
			}
		}
		return datas;
	}
	
	/**
	 * This populates the last event data in the given subsections list from the
	 * given last event data
	 * @param sects
	 * @param datas
	 */
	public static void populateSubSects(List<FaultSectionPrefData> sects,
			Map<Integer, List<LastEventData>> datas) {
		// clear any old last event data
		for (FaultSectionPrefData sect : sects)
			sect.setDateOfLastEvent(Long.MIN_VALUE);
		
		int populated = 0;
		int duplicates = 0;
		// start/end location tolerance (km)
		HashSet<LastEventData> usedDatas = new HashSet<LastEventData>();
		for (FaultSectionPrefData sect : sects) {
			int parentID = sect.getParentSectionId();
			List<LastEventData> parentDatas = datas.get(parentID);
			if (parentDatas == null)
				// no data for this section
				continue;
			// now find closest
			for (LastEventData data : parentDatas) {
				if (data.matchesLocation(sect, MATCH_LOCATION_TOLERANCE)) {
					// no duplicate check
					if (usedDatas.contains(data))
						duplicates++;
//					Preconditions.checkState(!usedDatas.contains(data), "Duplicate on: "+data.getSectName());
					sect.setDateOfLastEvent(data.getDateOfLastEvent().getTimeInMillis());
					sect.setSlipInLastEvent(data.getLastOffset());
					populated++;
					usedDatas.add(data);
					break;
				}
			}
		}
		int numDatas = 0;
		List<String> unusedData = Lists.newArrayList();
		for (List<LastEventData> dataList : datas.values()) {
			numDatas += dataList.size();
			for (LastEventData data : dataList)
				if (!usedDatas.contains(data))
					unusedData.add(data.getSectName());
		}
//		System.out.println("Populated "+populated+"/"+sects.size()+" sects from "+numDatas
//				+" last event data ("+duplicates+" duplicates)");
//		System.out.println("Unused: "+Joiner.on(",").join(unusedData));
	}
	
	/**
	 * Calculates a date from the open interval and open interval basis to day precision
	 * ignoring leap years
	 * @param openInterval
	 * @return
	 */
	private static GregorianCalendar calcDate(double openInterval) {
		return calcDate(OPEN_INTERVAL_BASIS, openInterval);
	}
	
	public static GregorianCalendar calcDate(GregorianCalendar intervalBasis, double openInterval) {
		GregorianCalendar eventDate = (GregorianCalendar)intervalBasis.clone();
		
		// go back years
		int years = (int)openInterval;
		if (years > 0)
			eventDate.add(Calendar.YEAR, -years);
		
		double fractYears = openInterval - Math.floor(openInterval);
		if ((float)fractYears == 0f)
			return eventDate;
		
		int days = (int)(fractYears*365d);
		if (days > 0)
			eventDate.add(Calendar.DAY_OF_YEAR, -days);
		
		return eventDate;
	}

	public LastEventData(String sectName, int parentSectID, double lastOffset,
			double openInterval, Location startLoc, Location endLoc) {
		this(sectName, parentSectID, lastOffset, openInterval, calcDate(openInterval), startLoc, endLoc);
	}
	
	private LastEventData(String sectName, int parentSectID, double lastOffset,
			double openInterval, GregorianCalendar eventDate, Location startLoc, Location endLoc) {
		super();
		this.sectName = sectName;
		this.parentSectID = parentSectID;
		this.lastOffset = lastOffset;
		this.openInterval = openInterval;
		this.eventDate = eventDate;
		this.startLoc = startLoc;
		this.endLoc = endLoc;
	}

	public String getSectName() {
		return sectName;
	}

	public int getParentSectID() {
		return parentSectID;
	}

	/**
	 * 
	 * @return last offset (m) or NaN if unknown
	 */
	public double getLastOffset() {
		return lastOffset;
	}

	/**
	 * 
	 * @return open interval from the OPEN_INTERVAL_BASIS reference date
	 */
	public double getRefOpenInterval() {
		return openInterval;
	}
	
	/**
	 * 
	 * @return Date of last event
	 */
	public GregorianCalendar getDateOfLastEvent() {
		return eventDate;
	}
	
	public boolean matchesLocation(FaultSectionPrefData sect, double toleranceKM) {
		Location sectStartLoc = sect.getFaultTrace().first();
		Location sectEndLoc = sect.getFaultTrace().last();
		
		if (LocationUtils.horzDistanceFast(sectStartLoc, startLoc) <= toleranceKM
				&& LocationUtils.horzDistanceFast(sectEndLoc, endLoc) <= toleranceKM)
			return true;
		
		return LocationUtils.horzDistanceFast(sectStartLoc, endLoc) <= toleranceKM
				&& LocationUtils.horzDistanceFast(sectEndLoc, startLoc) <= toleranceKM;
	}
	
	public static void writeOpenRecurrRatioTable(File file, FaultSystemSolution sol) throws IOException {
		writeOpenRecurrRatioTable(file, sol, SHEET_NUMS);
	}
	
	public static void writeOpenRecurrRatioTable(File file, FaultSystemSolution sol, int[] sheets) throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		csv.addLine("Parent Section Name", "Parent Section ID", "Sub Section ID",
				"Open Interval (years)", "Paleo Obs. RI", "OI/Paleo RI");
		
		Map<Integer, List<LastEventData>> datas = load(UCERF3_DataUtils.locateResourceAsStream(SUB_DIR, FILE_NAME), sheets);
		List<FaultSectionPrefData> fsd = sol.getRupSet().getFaultSectionDataList();
		Map<Integer, List<FaultSectionPrefData>> fsdByParent = Maps.newHashMap();
		for (FaultSectionPrefData sect : fsd) {
			List<FaultSectionPrefData> parentSects = fsdByParent.get(sect.getParentSectionId());
			if (parentSects == null) {
				parentSects = Lists.newArrayList();
				fsdByParent.put(sect.getParentSectionId(), parentSects);
			}
			parentSects.add(sect);
		}
		
		PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		
		// this is for sorting by name
		Map<String, Integer> parentNamesMap = Maps.newHashMap();
		for (Integer parentID : datas.keySet())
			if (fsdByParent.containsKey(parentID))
				parentNamesMap.put(fsdByParent.get(parentID).get(0).getParentSectionName(), parentID);
		List<String> parentNames = Lists.newArrayList(parentNamesMap.keySet());
		Collections.sort(parentNames);
		
		for (String parentName : parentNames) {
			Integer parentID = parentNamesMap.get(parentName);
			List<LastEventData> eventData = datas.get(parentID);
			List<FaultSectionPrefData> sects = fsdByParent.get(parentID);
			
			for (FaultSectionPrefData sect : sects) {
				for (LastEventData data : eventData) {
					if (data.matchesLocation(sect, MATCH_LOCATION_TOLERANCE)) {
						double paleoObsRate = sol.calcTotPaleoVisibleRateForSect(sect.getSectionId(), paleoProbModel);
						double paleoRI = 1d/paleoObsRate;
						double ratio = data.openInterval/paleoRI;
						csv.addLine(parentName, parentID+"", sect.getSectionId()+"", data.openInterval+"", paleoRI+"", ratio+"");
						break;
					}
				}
			}
		}
		
		csv.writeToFile(file);
	}

	public static void main(String[] args) throws IOException, DocumentException {
		// TODO Auto-generated method stub
//		GregorianCalendar date = calcDate(14000);
//		System.out.println(date);
//		System.out.println(date.get(Calendar.YEAR));
//		System.out.println(date.getTimeInMillis());
//		date.setTimeInMillis(date.getTimeInMillis());
//		System.out.println(date.get(Calendar.YEAR));
//		System.out.println(date.getTimeInMillis());
//		System.out.println(Long.MAX_VALUE);
		
//		Map<Integer, List<LastEventData>> datas = load();
//		List<FaultSectionPrefData> subSects = new DeformationModelFetcher(
//				FaultModels.FM3_1, DeformationModels.GEOLOGIC,
//				UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1d).getSubSectionList();
//		populateSubSects(subSects, datas);
//		
//		subSects = new DeformationModelFetcher(
//				FaultModels.FM3_2, DeformationModels.GEOLOGIC,
//				UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1d).getSubSectionList();
//		populateSubSects(subSects, datas);
		
		File solDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
		FaultSystemSolution sol = FaultSystemIO.loadSol(new File(solDir,
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		File csvFile = new File("/tmp/open_interval_ratios.csv");
		writeOpenRecurrRatioTable(csvFile, sol);
		
//		csvFile = new File("/tmp/open_interval_ratios_unused.csv");
//		int[] sheets_unused = {2,3};
//		writeOpenRecurrRatioTable(csvFile, sol, sheets_unused);
		
		// now try normal methods with each FM
		Map<Integer, List<LastEventData>> datas = load();
		populateSubSects(sol.getRupSet().getFaultSectionDataList(), datas);
		populateSubSects(FaultSystemIO.loadSol(new File(solDir,
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip"))
				.getRupSet().getFaultSectionDataList(), datas);
	}

}
