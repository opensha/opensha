package scratch.UCERF3.inversion.coulomb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.util.IDPairing;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class CoulombRates extends HashMap<IDPairing, CoulombRatesRecord> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static HashMap<FaultModels, String> modelDataFilesMap;
	
	static {
		modelDataFilesMap = new HashMap<FaultModels, String>();
//		modelDataFilesMap.put(FaultModels.FM3_1, "Stress_Table_FM3_1_2601_v4.xls");
//		modelDataFilesMap.put(FaultModels.FM3_2, "Stress_Table_FM3_2_2659_v3.xls");
//		modelDataFilesMap.put(FaultModels.FM3_1, "2012_06_04-Stress_Table-FM3.1.xls");
//		modelDataFilesMap.put(FaultModels.FM3_2, "2012_06_04-Stress_Table-FM3.2.xls");
		modelDataFilesMap.put(FaultModels.FM3_1, "2013_04_08-Stress_Table-FM3.1.xls");
		modelDataFilesMap.put(FaultModels.FM3_2, "2013_04_08-Stress_Table-FM3.2.xls");
	}
	
	private static final String DATA_SUB_DIR = "coulomb";

	private FaultModels fm;

	private CoulombRates(FaultModels fm) {
		this.fm = fm;
		// private so that it can only be instantiated with the from data file methods
	}
	
	public CoulombRates(FaultModels fm, Map<IDPairing, CoulombRatesRecord> rates) {
		this.fm = fm;
		this.putAll(rates);
	}
	
	/**
	 * This is a simple test to make sure that coulomb data exists for each
	 * of the given pairings. Checks for data in both directions
	 * 
	 * @param prefData
	 * @return
	 */
	public boolean isApplicableTo(Collection<IDPairing> pairings) {
		for (IDPairing pairing : pairings)
			if (!containsKey(pairing) || !containsKey(pairing.getReversed()))
				return false;
		return true;
	}
	
	public static CoulombRates loadUCERF3CoulombRates(FaultModels faultModel) throws IOException {
		String fileName = modelDataFilesMap.get(faultModel);
		Preconditions.checkNotNull(fileName, "No coulomb file exists for the given fault model: "+faultModel);
		return loadExcelFile(faultModel, UCERF3_DataUtils.locateResourceAsStream(DATA_SUB_DIR, fileName));
	}
	
	public static CoulombRates loadExcelFile(FaultModels faultModel, InputStream is) throws IOException {
		CoulombRates rates = new CoulombRates(faultModel);
		
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		int lastRowIndex = sheet.getLastRowNum();
		int id1, id2;
		double ds, pds, dcff, pdcff;
		for(int r=1; r<=lastRowIndex; ++r) {
//			System.out.println("Coulomb row: "+r);
			HSSFRow row = sheet.getRow(r);
			if(row==null) continue;
			int cellNum = row.getFirstCellNum();
			if (cellNum < 0)
				continue;
			HSSFCell id1_cell = row.getCell(cellNum++);
			if(id1_cell==null || id1_cell.getCellType()!=HSSFCell.CELL_TYPE_NUMERIC)
				continue;
			id1 = (int)id1_cell.getNumericCellValue();
			id2 = (int)row.getCell(cellNum++).getNumericCellValue();
			ds = row.getCell(cellNum++).getNumericCellValue();
			dcff = row.getCell(cellNum++).getNumericCellValue();
			pds = row.getCell(cellNum++).getNumericCellValue();
			pdcff = row.getCell(cellNum++).getNumericCellValue();
			
			IDPairing pairing = new IDPairing(id1, id2);
			rates.put(pairing, new CoulombRatesRecord(pairing, ds, pds, dcff, pdcff));
		}
		
		return rates;
	}
	
	public static void writeExcelFile(CoulombRates rates, Map<IDPairing, Double> distancesMap, File file) throws IOException {
		List<IDPairing> pairings = Lists.newArrayList(rates.keySet());
		// sort by ID1, ID2
		Collections.sort(pairings, new Comparator<IDPairing>() {

			@Override
			public int compare(IDPairing o1, IDPairing o2) {
				Integer id11 = o1.getID1();
				Integer id12 = o1.getID2();
				Integer id21 = o2.getID1();
				Integer id22 = o2.getID2();
				int cmp = id11.compareTo(id21);
				if (cmp != 0)
					return cmp;
				return id12.compareTo(id22);
			}
		});
		
		// load in FM3.1 table as a starting point
		InputStream is = UCERF3_DataUtils.locateResourceAsStream(DATA_SUB_DIR, modelDataFilesMap.get(FaultModels.FM3_1));
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		// make sure there are no missing rows
		if (sheet.getLastRowNum() != sheet.getPhysicalNumberOfRows()-1)
			for (int i=0; i<=sheet.getLastRowNum(); i++)
				if (sheet.getRow(i) == null)
					sheet.createRow(i);
		int lastRowIndex = sheet.getLastRowNum();
		// rectify row differences
		if (lastRowIndex != pairings.size()) {
			// check == because there is a single header row so we can check index against size
			
			if (lastRowIndex < pairings.size()) {
				for (int i=lastRowIndex+1; i<=pairings.size(); i++) {
					sheet.createRow(i);
				}
			} else {
				// lastRowIndex > pairings.size()
//				System.out.println("Target count: "+pairings.size());
//				System.out.println("lastRowIndex before remove: "+lastRowIndex);
				int removeCnt = 0;
				for (int i=lastRowIndex; i>pairings.size(); i--) {
					sheet.removeRow(sheet.getRow(i));
					removeCnt++;
				}
//				System.out.println("lastRowIndex after removed "+removeCnt+": "+sheet.getLastRowNum());
			}
			lastRowIndex = sheet.getLastRowNum();
			Preconditions.checkState(lastRowIndex == pairings.size(),
					"Last row index messed up...expected="+pairings.size()+", actual="+lastRowIndex);
		}
		Preconditions.checkState(sheet.getFirstRowNum() == 0);
		Preconditions.checkState(sheet.getLastRowNum() == sheet.getPhysicalNumberOfRows()-1,
				"Last row num="+sheet.getLastRowNum()+", num rows: "+sheet.getPhysicalNumberOfRows());
		
		for(int r=1; r<=lastRowIndex; ++r) {
			IDPairing pairing = pairings.get(r-1);
			CoulombRatesRecord rec = rates.get(pairing);
			double dist = distancesMap.get(pairing);
			HSSFRow row = sheet.getRow(r);
			// clear/create cells
			for (int i=0; i<7; i++)
				row.createCell(i, HSSFCell.CELL_TYPE_NUMERIC);
//			if (row.getFirstCellNum() != 0 || row.getLastCellNum() < 6) {
//				for (int i=0; i<7; i++)
//					row.createCell(i, HSSFCell.CELL_TYPE_NUMERIC);
//			}
			row.getCell(0).setCellValue(pairing.getID1());
			row.getCell(1).setCellValue(pairing.getID2());
			row.getCell(2).setCellValue(rec.getShearStressChange());
			row.getCell(3).setCellValue(rec.getCoulombStressChange());
			row.getCell(4).setCellValue(rec.getShearStressProbability());
			row.getCell(5).setCellValue(rec.getCoulombStressProbability());
			row.getCell(6).setCellValue(dist);
		}
		
		FileOutputStream fos = new FileOutputStream(file);
		wb.write(fos);
	}
	
	public static void main(String[] args) throws IOException {
		CoulombRates rates = loadUCERF3CoulombRates(FaultModels.FM3_1);
		
		IDPairing pairing = new IDPairing(594, 2402);
		System.out.println(rates.get(pairing));
		System.out.println(rates.get(pairing.getReversed()));
		pairing = new IDPairing(2402, 635);
		System.out.println(rates.get(pairing));
		System.out.println(rates.get(pairing.getReversed()));
		
//		for (int id1=961; id1<=969; id1++) {
			
//			for (int id2=1925; id2<=1940; id2++) {
//			for (int id2=1962; id2<=1962; id2++) {
//			for (int id2=0; id2<=2600; id2++) {
//				pairing = new IDPairing(id1, id2);
//				if (rates.get(pairing) != null) {
//					System.out.println(rates.get(pairing));
//					System.out.println(rates.get(pairing.getReversed()));
//				}
//			}
//		}
		
//		rates = loadUCERF3CoulombRates(FaultModels.FM3_2);
//		
//		pairing = new IDPairing(1381, 1793);
//		System.out.println(rates.get(pairing));
//		System.out.println(rates.get(pairing.getReversed()));
//		pairing = new IDPairing(1368, 1382);
//		System.out.println(rates.get(pairing));
//		System.out.println(rates.get(pairing.getReversed()));
	}
	
	public static class Adapter extends TypeAdapter<CoulombRates> {

		@Override
		public void write(JsonWriter out, CoulombRates value) throws IOException {
			Preconditions.checkNotNull(value.fm);
			out.beginObject();
			
			out.name("faultModel").value(value.fm.name());
			
			out.endObject();
		}

		@Override
		public CoulombRates read(JsonReader in) throws IOException {
			in.beginObject();
			
			Preconditions.checkState(in.hasNext());
			Preconditions.checkState(in.nextName().equals("faultModel"));
			FaultModels fm = FaultModels.valueOf(in.nextString());
			
			in.endObject();
			return loadUCERF3CoulombRates(fm);
		}
		
	}

}
