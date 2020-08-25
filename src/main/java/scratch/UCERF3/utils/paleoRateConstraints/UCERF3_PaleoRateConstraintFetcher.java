package scratch.UCERF3.utils.paleoRateConstraints;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

public class UCERF3_PaleoRateConstraintFetcher {
	
	private final static String PALEO_DATA_SUB_DIR = "paleoRateData";
//	private final static String PALEO_DATA_FILE_NAME = "UCERF3_PaleoRateData_v08_withMappings.xls";
	private final static String PALEO_DATA_FILE_NAME = "UCERF3_PaleoRateData_BIASI_v02_withMappings.xls";
	
	protected final static boolean D = false;  // for debugging
	
	public static ArrayList<PaleoRateConstraint> getConstraints(
			List<? extends FaultSection> faultSectionData) throws IOException {
		return getConstraints(faultSectionData, -1);
	}
	
	/**
	 * this additional constructor is for writing the subsection mapping to the given column in the file
	 * @param faultSectionData
	 * @param mappingCol
	 * @return
	 * @throws IOException
	 */
	private static ArrayList<PaleoRateConstraint> getConstraints(
			List<? extends FaultSection> faultSectionData, int mappingCol) throws IOException {
		
		ArrayList<PaleoRateConstraint> paleoRateConstraints   = new ArrayList<PaleoRateConstraint>();
		if(D) System.out.println("Reading Paleo Seg Rate Data from "+PALEO_DATA_FILE_NAME);
		POIFSFileSystem fs;
		File outputFile = null;
		if (mappingCol > 0) {
			File dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParentFile(), PALEO_DATA_SUB_DIR);
			outputFile = new File(dir, PALEO_DATA_FILE_NAME);
			InputStream is =
				new FileInputStream(outputFile);
			fs = new POIFSFileSystem(is);
		} else {
			InputStream is =
				UCERF3_DataUtils.locateResourceAsStream(PALEO_DATA_SUB_DIR, PALEO_DATA_FILE_NAME);
			fs = new POIFSFileSystem(is);
		}
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		
		// this determines if it is a new UCERF3.3+ (Biasi) file verses the old UCERF3.2 (Parsons) file
		boolean hasQuantiles = sheet.getRow(0).getCell(4).getStringCellValue().startsWith("2.5%");
		
		int lastRowIndex = sheet.getLastRowNum();
		double lat, lon, meanRate, lower68Conf, upper68Conf, lower95Conf, upper95Conf;
		String siteName;
		for(int r=1; r<=lastRowIndex; ++r) {	
			HSSFRow row = sheet.getRow(r);
			if(row==null) continue;
			HSSFCell cell = row.getCell(1);
			if(cell==null || cell.getCellType()==HSSFCell.CELL_TYPE_STRING) continue;
			HSSFCell nameCell = row.getCell(0);
			if (nameCell == null || nameCell.getCellType()!=HSSFCell.CELL_TYPE_STRING
					|| nameCell.getStringCellValue().trim().isEmpty())
				continue;
			lat = cell.getNumericCellValue();
			siteName = nameCell.getStringCellValue().trim();
			lon = row.getCell(2).getNumericCellValue();
			// skipping MRI cells
			if (hasQuantiles) {
				meanRate = row.getCell(8).getNumericCellValue();
				lower68Conf = row.getCell(10).getNumericCellValue();
				upper68Conf =  row.getCell(11).getNumericCellValue();
				lower95Conf = row.getCell(9).getNumericCellValue();
				upper95Conf =  row.getCell(12).getNumericCellValue();
			} else {
				meanRate = row.getCell(6).getNumericCellValue();
				lower68Conf = row.getCell(8).getNumericCellValue();	// note the labels are swapped in the *_v1 file
				upper68Conf =  row.getCell(7).getNumericCellValue();
				lower95Conf = Double.NaN;
				upper95Conf = Double.NaN;
			}
			
			if (lower68Conf == upper68Conf) {
				// TODO we don't want any of these
				System.out.println("Skipping value at "+siteName+" because upper and lower " +
						"values are equal: meanRate="+(float)meanRate+
					"\tlower68="+(float)lower68Conf+"\tupper68="+(float)upper68Conf);
				continue;
			}
				
			// get Closest section
			double minDist = Double.MAX_VALUE, dist;
			int closestFaultSectionIndex=-1;
			Location loc = new Location(lat,lon);
			
			// these hacks along with SCEC-VDO images are described in an e-mail from
			// Kevin on 3/6/12, subject "New MRI table"
			boolean blindThrustHack = siteName.equals("Compton") || siteName.equals("Puente Hills");
			boolean safOffshoreHack = siteName.equals("N. San Andreas -Offshore Noyo");
			
			for(int sectionIndex=0; sectionIndex<faultSectionData.size(); ++sectionIndex) {
				FaultSection data = faultSectionData.get(sectionIndex);
				// TODO this is a hack for blind thrust faults
				if (blindThrustHack && !data.getSectionName().contains(siteName))
					continue;
				dist  = data.getFaultTrace().minDistToLine(loc);
				if(dist<minDist) {
					minDist = dist;
					closestFaultSectionIndex = sectionIndex;
				}
			}
			if(minDist>2 && !blindThrustHack && !safOffshoreHack || closestFaultSectionIndex < 0) {
				if (D) {
					if (D) System.out.print("No match for: "+siteName+" (lat="+lat+", lon="+lon
							+") closest was "+minDist+" away: "+closestFaultSectionIndex);
					if (closestFaultSectionIndex >= 0)
						System.out.println(". "+faultSectionData.get(closestFaultSectionIndex).getSectionName());
					else
						System.out.println();
				}
				continue; // closest fault section is at a distance of more than 2 km
			}
			if (D) System.out.println("Matching constraint for closest index: "+closestFaultSectionIndex+" site name: "+siteName);
			// add to Seg Rate Constraint list
			String name = faultSectionData.get(closestFaultSectionIndex).getSectionName();
			PaleoRateConstraint paleoRateConstraint;
			if (hasQuantiles) {
				paleoRateConstraint = new PaleoRateConstraint(name, loc, closestFaultSectionIndex, 
						meanRate, lower68Conf, upper68Conf, lower95Conf, upper95Conf);
			} else {
				paleoRateConstraint = new PaleoRateConstraint(name, loc, closestFaultSectionIndex, 
						meanRate, lower68Conf, upper68Conf);
			}
			paleoRateConstraint.setPaleoSiteName(siteName);
			if(D) System.out.println("\t"+siteName+" (lat="+lat+", lon="+lon+") associated with "+name+
					" (section index = "+closestFaultSectionIndex+")\tdist="+(float)minDist+"\tmeanRate="+(float)meanRate+
					"\tlower68="+(float)lower68Conf+"\tupper68="+(float)upper68Conf+
					"\tlower95="+(float)lower95Conf+"\tupper95="+(float)upper95Conf);
			paleoRateConstraints.add(paleoRateConstraint);
			
			if (mappingCol > 0) {
				HSSFCell mappingCell = row.getCell(mappingCol);
				if (mappingCell == null)
					mappingCell = row.createCell(mappingCol, HSSFCell.CELL_TYPE_STRING);
//				System.out.println("Writing mapping at "+r+","+mappingCol+": "+name);
				mappingCell.setCellValue(name);
			}
		}
		
		if (mappingCol > 0)
			wb.write(new FileOutputStream(outputFile));
		return paleoRateConstraints;
	}
	
	
	
	
	
	/**
	 * This returns all PaleoRateConstraint objects without any association to
	 * fault sections.
	 * @TODO code here is redundant with that in private method (replace code in latter with
	 * call to this method?)
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<PaleoRateConstraint> getConstraints() throws IOException {
		ArrayList<PaleoRateConstraint> paleoRateConstraints   = new ArrayList<PaleoRateConstraint>();
		if(D) System.out.println("Reading Paleo Seg Rate Data from "+PALEO_DATA_FILE_NAME);
		POIFSFileSystem fs;
		InputStream is = UCERF3_DataUtils.locateResourceAsStream(PALEO_DATA_SUB_DIR, PALEO_DATA_FILE_NAME);
		fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		
		// this determines if it is a new UCERF3.3+ (Biasi) file verses the old UCERF3.2 (Parsons) file
		boolean hasQuantiles = sheet.getRow(0).getCell(4).getStringCellValue().startsWith("2.5%");
		
		int lastRowIndex = sheet.getLastRowNum();
		double lat, lon, meanRate, lower68Conf, upper68Conf, lower95Conf, upper95Conf;
		String siteName;
		for(int r=1; r<=lastRowIndex; ++r) {	
			HSSFRow row = sheet.getRow(r);
			if(row==null) continue;
			HSSFCell cell = row.getCell(1);
			if(cell==null || cell.getCellType()==HSSFCell.CELL_TYPE_STRING) continue;
			HSSFCell nameCell = row.getCell(0);
			if (nameCell == null || nameCell.getCellType()!=HSSFCell.CELL_TYPE_STRING
					|| nameCell.getStringCellValue().trim().isEmpty())
				continue;
			lat = cell.getNumericCellValue();
			siteName = nameCell.getStringCellValue().trim();
			lon = row.getCell(2).getNumericCellValue();
			// skipping MRI cells
			if (hasQuantiles) {
				meanRate = row.getCell(8).getNumericCellValue();
				lower68Conf = row.getCell(10).getNumericCellValue();
				upper68Conf =  row.getCell(11).getNumericCellValue();
				lower95Conf = row.getCell(9).getNumericCellValue();
				upper95Conf =  row.getCell(12).getNumericCellValue();
			} else {
				meanRate = row.getCell(6).getNumericCellValue();
				lower68Conf = row.getCell(8).getNumericCellValue();	// note the labels are swapped in the *_v1 file
				upper68Conf =  row.getCell(7).getNumericCellValue();
				lower95Conf = Double.NaN;
				upper95Conf = Double.NaN;
			}
			
			if (lower68Conf == upper68Conf) {
				// TODO we don't want any of these
				System.out.println("Skipping value at "+siteName+" because upper and lower " +
						"values are equal: meanRate="+(float)meanRate+
					"\tlower68="+(float)lower68Conf+"\tupper68="+(float)upper68Conf);
				continue;
			}
				
			Location loc = new Location(lat,lon);
			PaleoRateConstraint paleoRateConstraint;
			if (hasQuantiles) {
				paleoRateConstraint = new PaleoRateConstraint(null, loc, -1, 
						meanRate, lower68Conf, upper68Conf, lower95Conf, upper95Conf);
			} else {
				paleoRateConstraint = new PaleoRateConstraint(null, loc, -1, 
						meanRate, lower68Conf, upper68Conf);
			}
			paleoRateConstraint.setPaleoSiteName(siteName);
			paleoRateConstraints.add(paleoRateConstraint);
		}
		
		return paleoRateConstraints;
	}
	
	
	
	
	
	
	
	
	public static void main(String args[]) throws IOException, DocumentException {
//		int mappingCol = 16; // 14 for parsons, 16 for biasi
//		for (FaultModels fm : FaultModels.values()) {
//			List<FaultSectionPrefData> datas = new DeformationModelFetcher(
//					fm, DeformationModels.forFaultModel(fm).get(0),	UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,
//					InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
//			UCERF3_PaleoRateConstraintFetcher.getConstraints(datas, mappingCol++);
//		}
//		System.exit(0);
		
		FaultModels fm = FaultModels.FM3_1;
		List<? extends FaultSection> datas = new DeformationModelFetcher(
				fm, DeformationModels.forFaultModel(fm).get(0),	UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,
				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
		HashSet<Integer> parentIDs = new HashSet<>();
		for (FaultSection data : datas)
			parentIDs.add(data.getParentSectionId());
		ArrayList<PaleoRateConstraint> constrs = UCERF3_PaleoRateConstraintFetcher.getConstraints(datas);
		System.out.println("Loaded "+constrs.size()+" constraints");
		HashSet<Integer> parentIDsConstrained = new HashSet<>();
		for (PaleoRateConstraint constr : constrs)
			parentIDsConstrained.add(datas.get(constr.getSectionIndex()).getParentSectionId());
		System.out.println(parentIDsConstrained.size()+"/"+parentIDs.size()+" sections constrained");
		System.exit(0);
		System.out.println("Site Name\tSubsection Index");
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Site Name", "Subsection Index");
		for (PaleoRateConstraint constr : constrs) {
			System.out.println(constr.getPaleoSiteName()+"\t"+constr.getSectionIndex());
			csv.addLine(constr.getPaleoSiteName(), constr.getSectionIndex()+"");
		}
		csv.writeToFile(new File("/tmp/paleo_subsections.csv"));

//   		FaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory.cachedForBranch(DeformationModels.GEOLOGIC);
//   		UCERF3_PaleoRateConstraintFetcher.getConstraints(faultSysRupSet.getFaultSectionDataList());

//		File rupSetsDir = new File(precomp, "FaultSystemRupSets");
//		ArrayList<FaultSystemSolution> sols = new ArrayList<FaultSystemSolution>();
//		sols.add(SimpleFaultSystemSolution.fromFile(new File(rupSetsDir, "UCERF2.xml")));
//		sols.add(SimpleFaultSystemSolution.fromFile(new File(rupSetsDir, "Model1.xml")));
//		
//		showSegRateComparison(getConstraints(precomp, sols.get(0).getFaultSectionDataList()), sols);
	}
}
