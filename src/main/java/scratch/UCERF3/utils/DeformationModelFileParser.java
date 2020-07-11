package scratch.UCERF3.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.FaultModelDB_DAO;
import org.opensha.refFaultParamDb.dao.db.PrefFaultSectionDataDB_DAO;
import org.opensha.refFaultParamDb.vo.FaultModelSummary;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.finalReferenceFaultParamDb.PrefFaultSectionDataFinal;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DeformationModelFileParser {
	
	public static Map<Integer, DeformationSection> load(File file) throws IOException {
		return load(file.toURI().toURL());
	}
	
	public static Map<Integer, DeformationSection> load(URL url) throws IOException {
		CSVFile<String> csv;
		try {
			csv = CSVFile.readURL(url, true);
		} catch (RuntimeException e1) {
			System.out.println("Couldn't load: "+url);
			throw e1;
		}
		
		HashMap<Integer, DeformationSection>  defs = new HashMap<Integer, DeformationModelFileParser.DeformationSection>();
		
		for (List<String> row : csv) {
//			System.out.println("ID: "+row.get(0));
			int id[] = parseMinisectionNumber(row.get(0));
			DeformationSection def = defs.get(id[0]);
			if (def == null) {
				def = new DeformationSection(id[0]);
				defs.put(id[0], def);
			}
			double lon1 = Double.parseDouble(row.get(1));
			double lat1 = Double.parseDouble(row.get(2));
			double lon2 = Double.parseDouble(row.get(3));
			double lat2 = Double.parseDouble(row.get(4));
			Location loc1 = new Location(lat1, lon1);
			Location loc2 = new Location(lat2, lon2);
			
			double slip;
			try {
				slip = Double.parseDouble(row.get(5));
			} catch (NumberFormatException e) {
				slip = Double.NaN;
			}
			
			double rake;
			try {
				rake = Double.parseDouble(row.get(6));
			} catch (NumberFormatException e) {
				rake = Double.NaN;
			}
			
			// make sure that the mini section number is correct (starts at 1)
			Preconditions.checkState(def.slips.size() == id[1]-1, "bad row with minisectin: "
						+row.get(0)+" (parsed as: "+id[0]+", "+id[1]+")");
			
			def.add(loc1, loc2, slip, rake);
		}
		
		return defs;
	}
	
	public static int[] parseMinisectionNumber(String miniSection) {
		String[] split = miniSection.trim().split("\\.");
		int id = Integer.parseInt(split[0]);
		Preconditions.checkState(split.length > 1 && !split[1].isEmpty(),
				"Mini section was left blank for "+id+": "+miniSection);
		String str = split[1];
		// must be at least two digits (some files give it at xx.1 meaning xx.10)
		if (str.length() == 1)
			str = str+"0";
		int section = Integer.parseInt(str);
		
		int[] ret = {id, section};
		return ret;
	}
	
	public static String getMinisectionString(int[] miniSection) {
		return getMinisectionString(miniSection[0], miniSection[1]);
	}
	
	public static String getMinisectionString(int parentID, int mini) {
		String str = parentID+".";
		if (mini < 10)
			str += "0";
		str += mini;
		return str;
	}
	
	public static boolean compareAgainst(Map<Integer, DeformationSection> defs,
			List<FaultSectionPrefData> datas, List<Integer> fm) throws IOException {
		boolean ret = true;
		HashSet<DeformationSection> dones = new HashSet<DeformationModelFileParser.DeformationSection>();
		int noneCnt = 0;
		for (int id : fm) {
			DeformationSection def = defs.get(id);
			FaultSectionPrefData data = null;
			for (FaultSectionPrefData myData : datas) {
				if (myData.getSectionId() == id) {
					data = myData;
					break;
				}
			}
			Preconditions.checkNotNull(data);
			if (def == null) {
				System.out.println("No def model data for: "+data.getSectionId()+". "+data.getSectionName());
				ret = false;
				noneCnt++;
				continue;
			}
			ret = ret && def.validateAgainst(data);
			dones.add(def);
		}
		
		if (noneCnt > 0)
			System.out.println("No def model data for: "+noneCnt+" sections");
		
		for (DeformationSection def : defs.values()) {
			if (!dones.contains(def)) {
				System.out.println("No fault exists for def model section of id: "+def.id);
				ret = false;
			}
		}
		return ret;
	}
	
	public static void write(Map<Integer, DeformationSection> model, File file) throws IOException {
		write(model.values(), file);
	}
	
	public static void write(Collection<DeformationSection> model, File file) throws IOException {
		write(model, file, null);
	}
	
	public static void write(Collection<DeformationSection> model, File file, Map<Integer, String> namesMap) throws IOException {
		CSVFile<String> csv = new CSVFile<String>(true);
		for (DeformationSection def : model) {
			List<Location> locs1 = def.getLocs1();
			List<Location> locs2 = def.getLocs2();
			List<Double> slips = def.getSlips();
			List<Double> rakes = def.getRakes();
			for (int i=0; i<slips.size(); i++) {
				int[] mini = { def.getId(), (i+1) };
				String id = getMinisectionString(mini);
				ArrayList<String> line = new ArrayList<String>();
				line.add(id);
				line.add(""+(float)locs1.get(i).getLongitude());
				line.add(""+(float)locs1.get(i).getLatitude());
				line.add(""+(float)locs2.get(i).getLongitude());
				line.add(""+(float)locs2.get(i).getLatitude());
				line.add(""+slips.get(i).floatValue());
				line.add(""+rakes.get(i).floatValue());
				if (namesMap != null)
					line.add(namesMap.get(def.id));
				csv.addLine(line);
			}
		}
		csv.writeToFile(file);
	}
	
	public static class DeformationSection {
		public int getId() {
			return id;
		}
		
		protected void setID(int id) {
			this.id = id;
		}

		public List<Location> getLocs1() {
			return locs1;
		}

		public List<Location> getLocs2() {
			return locs2;
		}

		public List<Double> getSlips() {
			return slips;
		}

		public List<Double> getRakes() {
			return rakes;
		}

		private int id;
		private List<Location> locs1;
		private List<Location> locs2;
		private List<Double> slips;
		private List<Double> rakes;
		private List<Double> momentReductions; // defaults to null
		
		public DeformationSection(int id) {
			this.id = id;
			this.locs1 = new ArrayList<Location>();
			this.locs2 = new ArrayList<Location>();
			this.slips = new ArrayList<Double>();
			this.rakes = new ArrayList<Double>();
		}
		
		public void add(Location loc1, Location loc2, double slip, double rake) {
			locs1.add(loc1);
			locs2.add(loc2);
			slips.add(slip);
			rakes.add(rake);
		}
		
		public void add(int index, Location loc1, Location loc2, double slip, double rake) {
			locs1.add(index, loc1);
			locs2.add(index, loc2);
			slips.add(index, slip);
			rakes.add(index, rake);
		}
		
		public void remove(int index) {
			locs1.remove(index);
			locs2.remove(index);
			slips.remove(index);
			rakes.remove(index);
		}
		
		public void setMomentReductions(List<Double> momentReductions) {
			Preconditions.checkState(momentReductions.size() == slips.size(),
					"Size of moment reductions must match that of the slips");
		}
		
		public List<Double> getMomentReductions() {
			return momentReductions;
		}
		
		public LocationList getLocsAsTrace() {
			LocationList trace = new LocationList();
			trace.addAll(locs1);
			trace.add(locs2.get(locs2.size()-1));
			return trace;
		}
		
		public boolean validateAgainst(FaultSectionPrefData data) {
			String nameID = id+". "+data.getName();
			
			boolean mismatch = false;
			FaultTrace trace = data.getFaultTrace();
			if (trace.size()-1 != locs1.size()) {
				System.out.println(nameID+": trace size mismatch ("+trace.getNumLocations()+" trace pts, "+locs1.size()+" slip vals)");
				mismatch = true;
			}
			
			ArrayList<Location> fileLocs = getLocsAsTrace();
			
			for (int i=0; i<fileLocs.size()&&i<trace.size(); i++) {
				double dist = LocationUtils.horzDistance(fileLocs.get(i), trace.get(i));
				if (dist > 0.5) {
					System.out.println(nameID+": trace location mismatch at index "+i+"/"+(fileLocs.size()-1));
					mismatch = true;
				}
			}
			
			if (mismatch) {
				for (int i=0; i<fileLocs.size(); i++) {
					Location loc = fileLocs.get(i);
					System.out.print("["+(float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\t0]");
					
					if (trace.size() > i) {
						Location traceLoc = trace.get(i);
						System.out.print("\t["+(float)traceLoc.getLatitude()+"\t"+(float)traceLoc.getLongitude()+"\t0]");
						System.out.print("\tdist: "+(float)LocationUtils.linearDistanceFast(loc, traceLoc));
					}
					System.out.println();
				}
			}
			
			if (mismatch)
				return false;
			
			double dist1 = LocationUtils.horzDistance(locs1.get(0), trace.get(0));
			if (dist1 > 1) {
				System.out.println(nameID+": start trace location mismatch ("+dist1+" km)");
				return false;
			}
			
			double dist2 = LocationUtils.horzDistance(locs2.get(locs2.size()-1), trace.get(trace.size()-1));
			if (dist2 > 1) {
				System.out.println(nameID+": end trace location mismatch ("+dist2+" km)");
				return false;
			}
			
			for (int i=1; i<locs2.size(); i++) {
				if (!LocationUtils.areSimilar(locs1.get(i), locs2.get(i-1))) {
					System.out.println(nameID+": trace locations inconsistant in def model!");
					for (int j=0; j<locs1.size(); j++) {
						System.out.print("["+(float)locs1.get(j).getLatitude()+"\t"+(float)locs1.get(j).getLongitude()+"\t0]");
						System.out.println("\t["+(float)locs2.get(j).getLatitude()+"\t"+(float)locs2.get(j).getLongitude()+"\t0]");
					}
					return false;
				}
			}
			
			return true;
		}
	}
	
	private static <E> E getFromEnd(List<E> list, int numFromEnd) {
		return list.get(list.size()-1-numFromEnd);
	}
	
	private static void fixForRevisedFM(Map<Integer, DeformationSection> sects, FaultModels fm) {
		ArrayList<? extends FaultSection> datas = fm.fetchFaultSections(true);
		HashMap<Integer, FaultSection> fmMap = Maps.newHashMap();
		for (FaultSection data : datas)
			fmMap.put(data.getSectionId(), data);
		
		// fix 667. Almanor 2011 CFM:
		FaultSection data = fmMap.get(667);
		FaultTrace trace = data.getFaultTrace();
		DeformationSection sect = sects.get(667);
		sect.add(getFromEnd(trace, 1), getFromEnd(trace, 0), getFromEnd(sect.getSlips(), 0), getFromEnd(sect.getRakes(), 0));
		
		// fix 74. Los Osos 2011
		sect = sects.get(74);
		// remove the last 3 points, but save them for oceanic
		ArrayList<Double> slipsForOceanic = Lists.newArrayList();
		ArrayList<Double> rakesForOceanic = Lists.newArrayList();
		for (int i=sect.getSlips().size()-4; i<sect.getSlips().size(); i++) {
			slipsForOceanic.add(sect.getSlips().get(i));
			rakesForOceanic.add(sect.getRakes().get(i));
		}
		sect.remove(sect.getSlips().size()-1);
		sect.remove(sect.getSlips().size()-1);
		sect.remove(sect.getSlips().size()-1);
		
		// fix 82. North Frontal  (West)
		// remove the first point, then average the next two for the first span in the new fault model
		data = fmMap.get(82);
		sect = sects.get(82);
		trace = data.getFaultTrace();
		sect.remove(0);
		double slip = FaultUtils.getLengthBasedAngleAverage(sect.getLocsAsTrace().subList(0, 3), sect.getSlips().subList(0, 2));
		double rake = FaultUtils.getLengthBasedAngleAverage(sect.getLocsAsTrace().subList(0, 3), sect.getRakes().subList(0, 2));
		sect.remove(0);
		sect.remove(0);
		sect.remove(0);
		sect.add(0, trace.get(0), trace.get(1), slip, rake);
		sect.add(1, trace.get(1), trace.get(2), slip, rake);
		sect.add(2, trace.get(2), trace.get(3), slip, rake);
		sect.add(3, trace.get(3), trace.get(4), slip, rake);
		
		// 687. North Tahoe 2011 CFM
		data = fmMap.get(687);
		sect = sects.get(687);
		trace = data.getFaultTrace();
		slip = getFromEnd(sect.getSlips(), 0);
		rake = getFromEnd(sect.getRakes(), 0);
		sect.remove(sect.getSlips().size()-1);
		sect.add(getFromEnd(trace, 2), getFromEnd(trace, 1), slip, rake);
		sect.add(getFromEnd(trace, 1), getFromEnd(trace, 0), slip, rake);
		sect.getLocs1().set(0, trace.get(0));
		sect.getLocs2().set(0, trace.get(1));
		
		// fix 709. Oceanic - West Huasna
		data = fmMap.get(709);
		sect = sects.get(709);
		trace = data.getFaultTrace();
		sect.add(0, trace.get(0), trace.get(1), getFromEnd(slipsForOceanic, 0), getFromEnd(rakesForOceanic, 0));
		sect.add(1, trace.get(1), trace.get(2), getFromEnd(slipsForOceanic, 1), getFromEnd(rakesForOceanic, 1));
		sect.add(2, trace.get(2), trace.get(3), getFromEnd(slipsForOceanic, 2), getFromEnd(rakesForOceanic, 2));
		
		// fix 123. Rose Canyon
		data = fmMap.get(123);
		sect = sects.get(123);
		trace = data.getFaultTrace();
		for (int i=1; i<trace.size(); i++) {
			sect.getLocs1().set(i-1, trace.get(i-1));
			sect.getLocs2().set(i-1, trace.get(i));
		}
		
		// fix 113. Sierra Madre (San Fernando)
		data = fmMap.get(113);
		sect = sects.get(113);
		trace = data.getFaultTrace();
		for (int i=1; i<trace.size(); i++) {
			sect.getLocs1().set(i-1, trace.get(i-1));
			sect.getLocs2().set(i-1, trace.get(i));
		}
		
		// remove 182. Shelf (projection)
		sects.remove(182);
	}
	
	private static Map<String, Double> creepData = null;
	private static ArbitrarilyDiscretizedFunc momentReductionConversionFunc = null;
	
	private synchronized static void loadMomentReductionsData() throws IOException {
		if (creepData == null) {
			// first load the creep -> moment reductions table
			momentReductionConversionFunc = new ArbitrarilyDiscretizedFunc();
			BufferedReader convReader = new BufferedReader(UCERF3_DataUtils.getReader("creep",
					"moment-reductions-conversion-table-2012_06_08.txt"));
			convReader.readLine(); // header
			String line = convReader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				
				ArrayList<String> vals = Lists.newArrayList(Splitter.on("\t").split(line));
				double x = Double.parseDouble(vals.get(0));
				double y = Double.parseDouble(vals.get(1));
				
				momentReductionConversionFunc.set(x, y);
				
				line = convReader.readLine();
			}
			
			// now load the creep file
			
			String creepFileName = "creep-by-minisection-2012_12_27.xls";
//			String creepFileName = "creep-by-minisection-2012_11_28.xls";
			InputStream is = UCERF3_DataUtils.locateResourceAsStream("creep",
					creepFileName);
			POIFSFileSystem fs = new POIFSFileSystem(is);
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			HSSFSheet sheet = wb.getSheetAt(0);
			int lastRowIndex = sheet.getLastRowNum();
			creepData = new HashMap<String, Double>();
			for(int r=1; r<=lastRowIndex; ++r) {
//				System.out.println("Coulomb row: "+r);
				HSSFRow row = sheet.getRow(r);
				if(row==null) continue;
				HSSFCell miniCell = row.getCell(1);
				if (miniCell == null)
					continue;
				String miniSection;
				if (miniCell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC)
					miniSection = ""+(float)miniCell.getNumericCellValue();
				else
					miniSection = miniCell.getStringCellValue();
				
				HSSFCell dataCell = row.getCell(2);
				Preconditions.checkState(dataCell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC,
						"non numeric data cell!");
				double creep = dataCell.getNumericCellValue();
				creepData.put(miniSection, creep);
			}
		}
	}
	
//	private synchronized static void fixMomentReductionsData() throws IOException {
//		InputStream is = UCERF3_DataUtils.locateResourceAsStream("DeformationModels", "Moment_Reductions_2012_03_01.xls");
//		POIFSFileSystem fs = new POIFSFileSystem(is);
//		HSSFWorkbook wb = new HSSFWorkbook(fs);
//		HSSFSheet sheet = wb.getSheetAt(0);
//		int lastRowIndex = sheet.getLastRowNum();
//		int numModels = 6;
//		for(int r=1; r<=lastRowIndex; ++r) {
//			//				System.out.println("Coulomb row: "+r);
//			HSSFRow row = sheet.getRow(r);
//			if(row==null) continue;
//			HSSFCell miniCell = row.getCell(1);
//			if (miniCell == null)
//				continue;
//			String miniSection;
//			if (miniCell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC)
//				miniSection = ""+(float)miniCell.getNumericCellValue();
//			else
//				miniSection = miniCell.getStringCellValue();
//			int[] mini = parseMinisectionNumber(miniSection);
//			mini[1] = mini[1] + 1;
//			miniCell.setCellValue(getMinisectionString(mini));
//			double[] reductions = new double[numModels];
//			for (int i=0; i<numModels; i++) {
//				HSSFCell dataCell = row.getCell(2+i);
//				Preconditions.checkState(dataCell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC,
//						"non numeric data cell!");
//				reductions[i] = dataCell.getNumericCellValue();
//			}
//		}
//		wb.write(new FileOutputStream(new File("/tmp/fixed.xls")));
//	}
	
	/**
	 * This populates the momentReductions field of the DeformationSection objects passed in. It first loads
	 * the creep data and then sets the moment reductions for each minisection via lookup table using
	 * the creep rate divided by the slip rate, up to the specified maximum reduction.
	 * 
	 * @param model
	 * @param maxMomentReduction
	 */
	public static void applyMomentReductions(Map<Integer, DeformationSection> model, double maxMomentReduction) {
		// first load the data if needed
		try {
			loadMomentReductionsData();
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		for (String miniSectionStr : creepData.keySet()) {
			double creep = creepData.get(miniSectionStr);
			int[] miniSection = parseMinisectionNumber(miniSectionStr);
			int id = miniSection[0];
			int sect = miniSection[1];
			
			DeformationSection def = model.get(id);
			Preconditions.checkNotNull(def, "The given deformation model doesn't have a mapping for section "+id);
			int numMinisForSection = def.getSlips().size();
			if (def.momentReductions == null) {
				def.momentReductions = new ArrayList<Double>(numMinisForSection);
				for (int i=0; i<numMinisForSection; i++)
					def.momentReductions.add(0d);
			}
			Preconditions.checkState(sect <= numMinisForSection, "Mini sections inconsistant for section: "+id);
			double slip = def.getSlips().get(sect-1);
			
			double creepOverSlip = creep / slip;
			
			double momentRecution;
			if (creepOverSlip > momentReductionConversionFunc.getMaxX())
				momentRecution = 1;
			else
				momentRecution = momentReductionConversionFunc.getClosestYtoX(creepOverSlip);
			
			if (momentRecution <= maxMomentReduction)
				def.momentReductions.set(sect-1, momentRecution);
			else
				def.momentReductions.set(sect-1, maxMomentReduction);
		}
	}
	
	static void writeFromDatabase(FaultModels fm, File file, boolean names) throws IOException {
		int fmID = fm.getID();
		DB_AccessAPI db = fm.getDBAccess();
		
		PrefFaultSectionDataDB_DAO pref2db = new PrefFaultSectionDataDB_DAO(db);
		ArrayList<FaultSectionPrefData> datas = pref2db.getAllFaultSectionPrefData();
		FaultModelDB_DAO fm2db = new FaultModelDB_DAO(db);
		ArrayList<Integer> faultSectionIds = fm2db.getFaultSectionIdList(fmID);

		ArrayList<FaultSectionPrefData> faultModel = new ArrayList<FaultSectionPrefData>();
		for (FaultSectionPrefData data : datas) {
			if (!faultSectionIds.contains(data.getSectionId()))
				continue;
			faultModel.add(data);
		}
		
		if (names) {
			Collections.sort(faultModel, new Comparator<FaultSectionPrefData>() {
				
				private Collator c = Collator.getInstance();

				@Override
				public int compare(FaultSectionPrefData o1, FaultSectionPrefData o2) {
					return c.compare(o1.getSectionName(), o2.getSectionName());
				}
			});
		} else {
			Collections.sort(faultModel, new Comparator<FaultSectionPrefData>() {

				@Override
				public int compare(FaultSectionPrefData o1, FaultSectionPrefData o2) {
					return Double.compare(o1.getSectionId(), o2.getSectionId());
				}
			});
		}
		
		ArrayList<DeformationSection> sects = new ArrayList<DeformationModelFileParser.DeformationSection>();
		Map<Integer, String> namesMap;
		if (names)
			namesMap = Maps.newHashMap();
		else
			namesMap = null;
		
		for (FaultSectionPrefData data : faultModel) {
			DeformationSection sect = new DeformationSection(data.getSectionId());
			
			double slip = data.getOrigAveSlipRate();
			double rake = data.getAveRake();
			
			FaultTrace trace = data.getFaultTrace();
			
			for (int i=1; i<trace.size(); i++) {
				sect.add(trace.get(i-1), trace.get(i), slip, rake);
			}
			
			if (namesMap != null)
				namesMap.put(data.getSectionId(), data.getSectionName());
			sects.add(sect);
		}
		
		write(sects, file, namesMap);
		
		File asciiFile = new File(file.getParentFile(), file.getName().replaceAll(".csv", ".txt"));
		FaultSectionDataWriter.writeSectionsToFile(faultModel, null, asciiFile.getAbsolutePath());
		
		try {
			db.destroy();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void writeSlipCreepTable(File file, FaultModels fm) throws IOException {
		loadMomentReductionsData();
		
		List<DeformationModels> dms = DeformationModels.forFaultModel(fm);
		
		CSVFile<String> csv = new CSVFile<String>(true);
		
		List<String> header = Lists.newArrayList("Minisection ID", "Name", "Creep Rate");
		
		List<Map<Integer, DeformationSection>> sectsList = Lists.newArrayList();
		
		final Map<Integer, String> namesMap = Maps.newHashMap();
		for (FaultSection sect : fm.fetchFaultSections())
			namesMap.put(sect.getSectionId(), sect.getSectionName());
		
		for (int i=0; i<dms.size(); i++) {
			DeformationModels dm = dms.get(i);
			
			if (dm.getRelativeWeight(null) == 0 && !(dm == DeformationModels.GEOLOGIC_LOWER || dm == DeformationModels.GEOLOGIC_UPPER))
				continue;
//			if (dm == DeformationModels.GEOLOGIC_PLUS_ABM || dm == DeformationModels.GEOL_P_ABM_OLD_MAPPED
//					|| dm == DeformationModels.GEOBOUND)
//				continue;
			
			header.add(dm.getName());
			
			Map<Integer, DeformationSection> sects = load(dm.getDataFileURL(fm));
			
			applyMomentReductions(sects, 10d);
			
			sectsList.add(sects);
		}
		csv.addLine(header);
		
		ArrayList<Integer> keys = Lists.newArrayList();
		for (Integer key : sectsList.get(0).keySet())
			keys.add(key);
		Collections.sort(keys);
		
		Map<Integer, List<Double>> creepMap = Maps.newHashMap();
		
		for (String key : creepData.keySet()) {
			int[] miniSection = parseMinisectionNumber(key);
			int id = miniSection[0];
			int sect = miniSection[1];
			
			List<Double> creepVals = creepMap.get(id);
			if (creepVals == null) {
				creepVals = Lists.newArrayList();
				for (int i=0; i<sectsList.get(0).get(id).getSlips().size(); i++)
					creepVals.add(0d);
				creepMap.put(id, creepVals);
			}
			
//			while (creepVals.size() <= sect)
//				creepVals.add(0d);
			
			creepVals.set(sect-1, creepData.get(key));
		}
		
		for (Integer id : keys) {
			
			List<Double> creep = creepMap.get(id);
			
			List<DeformationSection> dmSects = Lists.newArrayList();
			for (Map<Integer, DeformationSection> sects : sectsList)
				dmSects.add(sects.get(id));
			
			for (int i=0; i<sectsList.get(0).get(id).getSlips().size(); i++) {
				int[] miniSection = { id.intValue(), i+1 };
				List<String> line = Lists.newArrayList(getMinisectionString(miniSection), namesMap.get(id));
				
				if (creep == null)
					line.add("");
				else
					line.add(creep.get(i)+"");
				
				for (DeformationSection sect : dmSects) {
					line.add(sect.getSlips().get(i)+"");
				}
				
				csv.addLine(line);
			}
		}
		
		csv.writeToFile(file);
		csv.writeToTabSeparatedFile(new File(file.getParentFile(), file.getName().replaceAll(".csv", ".txt")), 1);
	}
	
	private static void writeCreepReductionsTable(File file, FaultModels fm) throws IOException {
		List<DeformationModels> dms = DeformationModels.forFaultModel(fm);
		
		CSVFile<String> csv = new CSVFile<String>(true);
		
		List<String> header = Lists.newArrayList("Minisection ID", "Name", "Creep Rate");
		
		List<Map<Integer, DeformationSection>> sectsList = Lists.newArrayList();
		
		final Map<Integer, String> namesMap = Maps.newHashMap();
		for (FaultSection sect : fm.fetchFaultSections())
			namesMap.put(sect.getSectionId(), sect.getSectionName());
		
		for (int i=0; i<dms.size(); i++) {
			DeformationModels dm = dms.get(i);
			
			if (dm == DeformationModels.GEOLOGIC_LOWER || dm == DeformationModels.GEOLOGIC_UPPER || dm == DeformationModels.MEAN_UCERF3)
				continue;
			if (dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) == 0)
				continue;
			System.out.println("DM: "+dm);
			
			header.add(dm.getShortName()+" Slip");
			header.add(dm.getShortName()+" Moment Reduction");
			
			Map<Integer, DeformationSection> sects = load(dm.getDataFileURL(fm));
			
			applyMomentReductions(sects, 10d);
			
			sectsList.add(sects);
		}
		csv.addLine(header);
		
		ArrayList<String> keys = Lists.newArrayList();
		for (String key : creepData.keySet())
			keys.add(key);
		Collections.sort(keys, new Comparator<String>() {
			private Collator c = Collator.getInstance();

			@Override
			public int compare(String o1, String o2) {
				int[] miniSection1 = parseMinisectionNumber(o1);
				int[] miniSection2 = parseMinisectionNumber(o2);
				String name1 = namesMap.get(miniSection1[0]);
				String name2 = namesMap.get(miniSection2[0]);
				int ret = c.compare(name1, name2);
				if (ret == 0) {
					ret = Double.compare(miniSection1[1], miniSection2[1]);
				}
				return ret;
			}
		});
		
		for (String miniSectionStr : keys) {
			double creep = creepData.get(miniSectionStr);
			int[] miniSection = parseMinisectionNumber(miniSectionStr);
			int id = miniSection[0];
			int sect = miniSection[1];
			List<String> line = Lists.newArrayList(getMinisectionString(miniSection), namesMap.get(id), creep+"");
			
			for (int i=0; i<sectsList.size(); i++) {
				DeformationSection def = sectsList.get(i).get(id);
				double momRed = def.getMomentReductions().get(sect-1);
				if (momRed > DeformationModelFetcher.MOMENT_REDUCTION_MAX)
					momRed = DeformationModelFetcher.MOMENT_REDUCTION_MAX;
				double slip = def.getSlips().get(sect-1);
				
				line.add(slip+"");
				line.add(momRed+"");
			}
			
			csv.addLine(line);
		}
		
		csv.writeToFile(file);
		csv.writeToTabSeparatedFile(new File(file.getParentFile(), file.getName().replaceAll(".csv", ".txt")), 1);
	}
	
	/**
	 * This creates a branch averaged deformation model. Slip rates are averaged by each deformation model's weight,
	 * and rakes are taken from the Geologic model.
	 * @param fm
	 * @return
	 * @throws IOException
	 */
	public static Map<Integer, DeformationSection> loadMeanUCERF3_DM(FaultModels fm) throws IOException {
		List<Double> weights = Lists.newArrayList();
		List<Map<Integer, DeformationSection>> models = Lists.newArrayList();
		// use GEOLOGIC rakes
		Map<Integer, DeformationSection> rakeBasis = null;
		Set<Integer> parentIDs = null;
		double weightSum = 0d;
		for (DeformationModels dm : DeformationModels.values()) {
			double weight = dm.getRelativeWeight(null);
			if (weight > 0) {
				weights.add(weight);
				Map<Integer, DeformationSection> model = load(dm.getDataFileURL(fm));
				models.add(model);
				if (parentIDs == null)
					parentIDs = model.keySet();
				else
					Preconditions.checkState(parentIDs.size() == model.size(), "DM's have different numbers of parents");
				if (dm == DeformationModels.GEOLOGIC)
					rakeBasis = model;
				weightSum += weight;
			}
		}
		
		// normalize the weights
		if (weightSum != 1d) {
			for (int i=0; i<weights.size(); i++)
				weights.set(i, weights.get(i)/weightSum);
		}
		
		Preconditions.checkNotNull(rakeBasis, "Is geologic zero weight? We need it for rakes...");
		
		Map<Integer, DeformationSection> mean = Maps.newHashMap();
		
		for (Integer parentID : parentIDs) {
			DeformationSection sect = new DeformationSection(parentID);
			
			DeformationSection sectRakeBasis = rakeBasis.get(parentID);
			int numMinis = sectRakeBasis.getSlips().size();
			for (int i=0; i<numMinis; i++)
				sect.add(sectRakeBasis.getLocs1().get(i), sectRakeBasis.getLocs2().get(i),
						0, sectRakeBasis.getRakes().get(i));
			
			for (int i=0; i<weights.size(); i++) {
				DeformationSection oSect = models.get(i).get(parentID);
				double weight = weights.get(i);
				for (int j=0; j<numMinis; j++) {
					double slip = oSect.slips.get(j);
					if (Double.isNaN(slip))
						slip = 0;
					sect.slips.set(j, sect.slips.get(j)+weight*slip);
				}
			}
			
			mean.put(parentID, sect);
		}
		
		return mean;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
//		FaultSectionDataWriter.writeSectionsToFile(FaultModels.FM3_1.fetchFaultSections(), Lists.newArrayList("Fault Model 3.1"), "D:\\Documents\\temp\\fm_3_1_sects.txt");
//		System.exit(0);
//		writeFromDatabase(FaultModels.FM3_2, new File("/tmp/fm_3_2_revised_minisections_with_names.csv"), true);
//		System.exit(0);
		
		writeSlipCreepTable(new File("/tmp/slips_creep.csv"), FaultModels.FM3_1);
		writeCreepReductionsTable(new File("/tmp/new_creep_data.csv"), FaultModels.FM3_1);
		System.exit(0);
		
		FaultModels[] fms = { FaultModels.FM3_1, FaultModels.FM3_2 };
		
		DB_AccessAPI db = DB_ConnectionPool.getDB3ReadOnlyConn();
		PrefFaultSectionDataDB_DAO pref2db = new PrefFaultSectionDataDB_DAO(db);
		FaultModelDB_DAO fm2db = new FaultModelDB_DAO(db);
		ArrayList<FaultSectionPrefData> datas = pref2db.getAllFaultSectionPrefData();
//		System.out.println("Fetching fault model");
		
		for (FaultModels fm : fms) {
			ArrayList<Integer> fmSects = fm2db.getFaultSectionIdList(fm.getID());
			for (DeformationModels dm : DeformationModels.forFaultModel(fm)) {
				if (dm.getRelativeWeight(null) == 0)
					continue;
				Map<Integer, DeformationSection> sects = load(dm.getDataFileURL(fm));
//				for (DeformationSection sect : sects.values()) {
//					if (sect.slips.size() == 1) {
//						System.out.println("Sect "+sect.id+" only has one mini!");
//						for (FaultSectionPrefData data : datas) {
//							if (data.getSectionId() == sect.id) {
//								System.out.println("Sect Name: "+data.getSectionName());
//								break;
//							}
//						}
//					}
//				}
//				System.exit(0);
				System.out.println("TESTING "+fm+" : "+dm);
				boolean success = compareAgainst(sects, datas, fmSects);
				System.out.println("VALIDATED??? "+success);
				int nanSlips = 0;
				int nanRakes = 0;
				int zeroSlips = 0;
				for (DeformationSection sect : sects.values()) {
					for (double slip : sect.getSlips())
						if (Double.isNaN(slip))
							nanSlips++;
					for (double slip : sect.getSlips())
						if (slip == 0)
							zeroSlips++;
					for (double rake : sect.getRakes())
						if (Double.isNaN(rake))
							nanRakes++;
				}
				System.out.println("NaNs: "+nanSlips+" slips, "+nanRakes+" rakes");
				System.out.println("Zeros Slips: "+zeroSlips);
				try {
					new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1);
				} catch (Exception e) {
					System.out.println(dm+" failed!");
					e.printStackTrace();
				}
			}
		}
		System.exit(0);
		
		
		File defFile;
		
//		File dir = new File("D:\\Documents\\SCEC\\def_models");
//		File dir = new File("/home/kevin/OpenSHA/UCERF3/def_models/2012_02_07-initial");
		
		File precomputedDataDir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "FaultSystemRupSets");
		
		try {
//			load(DeformationModels.GEOLOGIC.getDataFileURL(FaultModels.FM3_2));
//			fixMomentReductionsData();
			
			// this will fix the mini sction numbering problem
//			String subDirName = "DeformationModels";
//			File dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParent(), subDirName);
//			FaultModels[] fms = { FaultModels.FM3_1, FaultModels.FM3_2 };
//			for (FaultModels fm : fms) {
//				for (DeformationModels dm : DeformationModels.forFaultModel(fm)) {
//					String fileName = dm.getDataFileName(fm);
//					File origFile = new File(dir, fileName);
//					File origBakFile = new File(dir, fileName+".bak");
//					System.out.println("Fixing: "+origFile.getName());
//					CSVFile<String> csv = CSVFile.readFile(origFile, true);
//					for (int i=0; i<csv.getNumRows(); i++) {
//						String miniStr = csv.get(i, 0);
//						int[] mini = parseMinisectionNumber(miniStr);
//						mini[1] = mini[1] + 1;
//						miniStr = getMinisectionString(mini);
//						csv.set(i, 0, miniStr);
//					}
//					FileUtils.moveFile(origFile, origBakFile);
//					csv.writeToFile(origFile);
//				}
//			}
			
			
//			DeformationModels.GEOLOGIC;
//			CSVFile<String> csv = CSVFile.readURL(url, true);
			
//			System.out.println("Fetching fault data");
//			ArrayList<FaultSectionPrefData> datas = pref2db.getAllFaultSectionPrefData();
//			System.out.println("Fetching fault model");
			FaultModels fm = FaultModels.FM3_2;
			ArrayList<Integer> fmSects = fm2db.getFaultSectionIdList(fm.getID());
			File dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParentFile(),
					"DeformationModels");
			for (DeformationModels dm : DeformationModels.forFaultModel(fm)) {
				if (dm == DeformationModels.GEOLOGIC)
					continue;
				String name = dm.getDataFileName(fm);
				defFile = new File(dir, name);
				System.out.println("******** FIXING "+dm);
				Map<Integer, DeformationSection>  defs = load(defFile);
				boolean success = compareAgainst(defs, datas, fmSects);
				if (!success) {
					System.out.println("ok lets fix it...");
					fixForRevisedFM(defs, fm);
					success = compareAgainst(defs, datas, fmSects);
					Preconditions.checkState(success, "...still couldn't fix it :-(");
					if (name.contains("2012_"))	{
						name = name.substring(0, name.indexOf("2012"));
						name += "_MAPPED_2012_06_05.csv";
					}
					write(defs, new File(dir, name));
				}
			}
			System.exit(0);
			
			defFile = new File(dir, "geologic_slip_rake_2012_03_02.csv");
//			System.out.println("Doing comparison: "+defFile.getName());
			Map<Integer, DeformationSection>  defs = load(defFile);
			compareAgainst(defs, datas, fmSects);
			System.out.println("ok lets fix it...");
			fixForRevisedFM(defs, fm);
			compareAgainst(defs, datas, fmSects);
//			System.out.println("");
//			defFile = new File(dir, "ABM_slip_rake_feb06.txt");
//			System.out.println("Doing comparison: "+defFile.getName());
//			defs = load(defFile);
//			compareAgainst(defs, datas, fm);
//			System.out.println("");
//			defFile = new File(dir, "geobound_slip_rake_feb06.txt");
//			System.out.println("Doing comparison: "+defFile.getName());
//			defs = load(defFile);
//			compareAgainst(defs, datas, fm);
//			System.out.println("");
//			defFile = new File(dir, "zeng_slip_rake_feb06.txt");
//			System.out.println("Doing comparison: "+defFile.getName());
//			defs = load(defFile);
//			compareAgainst(defs, datas, fm);
//			System.out.println("");
//			System.out.println("DONE");
			
			
//			ArrayList<FaultSectionPrefData> datas = DeformationModelFetcher.loadUCERF3FaultModel(faultModelId);
//			
//			for (DeformationModels dm : DeformationModels.values()) {
//				if (dm.getDataFileURL() == null)
//					continue;
//				HashMap<Integer, DeformationSection> model = load(dm.getDataFileURL());
//				HashMap<Integer, DeformationSection> fixed = DeformationModelFetcher.getFixedModel(datas, model, dm);
//				File outFile = new File(dm.getDataFileURL().toURI());
//				outFile = new File(outFile.getParentFile(), outFile.getName()+".fixed");
//				System.out.println("Writing: "+outFile.getAbsolutePath());
//				write(fixed, outFile);
//			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				db.destroy();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		System.exit(0);
	}

}
