package scratch.UCERF3.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import com.google.common.collect.Lists;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class GeologicSlipRateLoader {
	
	private static String getCellAsString(HSSFCell cell) {
		
		if (cell.getCellType() == HSSFCell.CELL_TYPE_STRING)
			return cell.getStringCellValue();
		else if (cell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC)
			return ""+cell.getNumericCellValue();
		else if (cell.getCellType() == HSSFCell.CELL_TYPE_BLANK)
			return null;
		else
			throw new IllegalStateException("cell is neither a string, blank, nor numeric");
	}
	
	public static ArrayList<GeologicSlipRate> loadExcelFile(URL url) throws FileNotFoundException, IOException {
		return loadExcelFile(new BufferedInputStream(url.openStream()));
	}
	
	public static ArrayList<GeologicSlipRate> loadExcelFile(File excelFile) throws FileNotFoundException, IOException {
		return loadExcelFile(new FileInputStream(excelFile));
	}
	public static ArrayList<GeologicSlipRate> loadExcelFile(InputStream is) throws FileNotFoundException, IOException {
		ArrayList<GeologicSlipRate> rates = new ArrayList<GeologicSlipRate>();
		
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		wb.setMissingCellPolicy(Row.CREATE_NULL_AS_BLANK);
		HSSFSheet sheet = wb.getSheetAt(0);
		
		boolean newTable = sheet.getRow(0).getCell(8).getStringCellValue().startsWith("ID");
		boolean newerTable = sheet.getRow(0).getCell(10).getStringCellValue().startsWith("Site-specific Data");
		
		int latCol, lonCol, valCol, idCol;
		
		if (newTable) {
			latCol = 21;
			lonCol = 20;
			valCol = 17;
			idCol = 8;
		} else if (newerTable) {
			latCol = 12;
			lonCol = 11;
			valCol = 9;
			idCol = 1;
		} else {
			latCol = 10;
			lonCol = 9;
			valCol = 16;
			idCol = -1;
		}
		
		for (int rowInd=2; rowInd<=sheet.getLastRowNum(); rowInd++) {
			HSSFRow row = sheet.getRow(rowInd);
			double lat, lon;
			try {
				String lonStr = GeologicSlipRate.numbersSpacesOnly(getCellAsString(row.getCell(lonCol)), true);
				if (lonStr == null || lonStr.length() == 0)
					continue;
				String latStr = GeologicSlipRate.numbersSpacesOnly(getCellAsString(row.getCell(latCol)), true);
				if (latStr == null || lonStr.length() == 0)
					continue;
				lat = Double.parseDouble(latStr);
				lon = Double.parseDouble(lonStr);
			} catch (Exception e) {
				System.out.println("Error parsint location: "+e.getMessage());
				continue;
			}
			Location loc = new Location(lat, lon);
			String valStr = getCellAsString(row.getCell(valCol));
			if (valStr == null || valStr.length() == 0) {
				System.out.println("Skipping empty value at loc: "+loc);
				continue;
			}
			
			GeologicSlipRate geo;
			try {
				geo = GeologicSlipRate.fromString(loc, valStr);
			} catch (Exception e) {
				System.out.println("Couldn't parse slip rate: "+valStr+" ("+e.getMessage()+")");
				continue;
			}
			
			if (idCol >= 0) {
				String idStr = GeologicSlipRate.numbersSpacesOnly(getCellAsString(row.getCell(idCol)), true);
				geo.setSectID((int)Double.parseDouble(idStr));
			}
				
			rates.add(geo);
		}
		
		return rates;
	}
	
	private static void writeMinisectionAssociations(FaultModels fm, File file) throws FileNotFoundException, IOException {
		ArrayList<GeologicSlipRate> rates = loadExcelFile(UCERF3_DataUtils.locateResourceAsStream("DeformationModels",
				"geologic_slip_rate_sites_2012_07_11.xls"));
		
		Map<Integer, FaultSectionPrefData> sects = fm.fetchFaultSectionsMap();
		
		CSVFile<String> csv = new CSVFile<String>(true);
		
		csv.addLine(Lists.newArrayList("Minisection", "Name", "Site Lon", "Site Lat",
				"Minisection Distance (KM)", "UCERF3 Preferred Rate"));
		
		for (GeologicSlipRate r : rates) {
			Location loc = r.getLocation();
			
			FaultSectionPrefData sect = sects.get(r.getSectID());
			
			if (sect == null) {
				System.out.println("No section ID found with ID: "+r.getSectID());
				List<String> line = Lists.newArrayList(r.getSectID()+" ??", "",
						(float)r.getLocation().getLongitude()+"", (float)r.getLocation().getLatitude()+"",
						"", (float)r.getValue()+"");
				csv.addLine(line);
				continue;
			}
			
			double minDist = Double.MAX_VALUE;
			int closestMini = -1;
			
			FaultTrace trace = sect.getFaultTrace();
			for (int mini=0; mini<trace.size()-1; mini++) {
				FaultTrace subTrace = new FaultTrace(mini+"");
				subTrace.add(trace.get(mini));
				subTrace.add(trace.get(mini+1));
				
				StirlingGriddedSurface surf = new StirlingGriddedSurface(subTrace, sect.getAveDip(),
						sect.getOrigAveUpperDepth(),sect.getAveLowerDepth(), 1d, sect.getDipDirection());
				
				for (Location surfLoc : surf) {
					double d = LocationUtils.linearDistanceFast(loc, surfLoc);
					if (d < minDist) {
						minDist = d;
						closestMini = mini;
					}
				}
			}
			int[] miniSection = { sect.getSectionId(), closestMini+1 };
			String miniStr = DeformationModelFileParser.getMinisectionString(miniSection);
			List<String> line = Lists.newArrayList(miniStr, sect.getName(),
					(float)r.getLocation().getLongitude()+"", (float)r.getLocation().getLatitude()+"",
					(float)minDist+"", (float)r.getValue()+"");
			
			if (minDist > 5d) {
				System.out.println("WARNING - assignment for "+sect.getSectionName()+" ("+sect.getSectionId()
						+") is far: "+minDist+" km");
			}
			
			csv.addLine(line);
		}
		
		csv.writeToFile(file);
	}
	
	public static void main(String args[]) throws FileNotFoundException, IOException {
		ArrayList<GeologicSlipRate> rates =
			loadExcelFile(new File("/tmp/UCERF3_Geologic_Slip Rates_version 3_2011_08_03.xls"));
		System.out.println("Loading new slips!");
		rates = loadExcelFile(UCERF3_DataUtils.locateResourceAsStream("DeformationModels",
						"geologic_slip_rate_sites_2012_07_11.xls"));
		for (GeologicSlipRate r : rates)
			System.out.println(r.getSectID()+"\t"+r.getValue());
		
		
		writeMinisectionAssociations(FaultModels.FM3_1, new File("/tmp/fm_3_1_geologic_mini_assignment.csv"));
		writeMinisectionAssociations(FaultModels.FM3_2, new File("/tmp/fm_3_2_geologic_mini_assignment.csv"));
	}

}
