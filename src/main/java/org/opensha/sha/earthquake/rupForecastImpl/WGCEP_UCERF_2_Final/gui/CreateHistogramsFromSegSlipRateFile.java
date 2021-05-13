/**
 * 
 */
package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.gui;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;

/**
 * @author vipingupta
 *
 */
public class CreateHistogramsFromSegSlipRateFile {


	private final PlotCurveCharacterstics PLOT_HISTOGRAM =
		new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.BLACK); // black
	private ArrayList funcs;
	private String xAxisLabel, yAxisLabel;
	
	public CreateHistogramsFromSegSlipRateFile(ArrayList funcList, String xAxisLabel, String yAxisLabel) {
		funcs = funcList;
		this.xAxisLabel = xAxisLabel;
		this.yAxisLabel = yAxisLabel;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getPlottingFeatures()
	 */
	public ArrayList<PlotCurveCharacterstics> getPlottingFeatures() {
		 ArrayList<PlotCurveCharacterstics> list = new ArrayList<PlotCurveCharacterstics>();
		 list.add(PLOT_HISTOGRAM);
		 return list;
	}
	
	/**
	 * Create the histograms from segment recurrence interval file. 
	 * It creates a plot for each slip Model and Mag Area Relationship combination.
	 * Plot can be created for 1 segment or for all segments.
	 * It creates plots and saves PDFs in A_FaultRupRatesPlots_2_1 subdirectory in masterDirectory
	 * @param masterDirName MasterDirectoty where A_FaultRupRatesPlots_2_1 will be created
	 * @param excelSheetName Absolute pathname to excel file
	 */
	public static void createHistogramPlots(String masterDirName, String excelSheetName) {
		String xAxisLabel = "Ratio of Original and Calculated Slip Rate";
		String yAxisLabel = "Count";
		String plotLabel = "Slip Rate Ratio";
		
		String[] names = {"Original Slip Rate",
			"Characteristic", 
			"Ellsworth-A_UniformBoxcar", "Ellsworth-A_WGCEP-2002", "Ellsworth-A_Tapered",
			"Ellsworth-B_UniformBoxcar", "Ellsworth-B_WGCEP-2002", "Ellsworth-B_Tapered",
			"Hanks & Bakun (2002)_UniformBoxcar", "Hanks & Bakun (2002)_WGCEP-2002", "Hanks & Bakun (2002)_Tapered",
			"Somerville (2006)_UniformBoxcar", "Somerville (2006)_WGCEP-2002", "Somerville (2006)_Tapered"};
		try {
			// directory to save the PDF files. Directory will be created if it does not exist already
			String dirName = masterDirName+"/A_FaultSegSlipRateHistograms_2_1/";
			File file = new File(dirName);
			if(!file.isDirectory()) { // create directory if it does not exist already
				file.mkdir();
			}
			//System.out.println(excelSheetName);
			ArrayList funcList = new ArrayList();
			for(int k=1; k<names.length; ++k) {
				EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0.2, 2.5, 24);
				func.setTolerance(func.getDelta());
				func.setName(names[k]);
				funcList.add(func);
			}
			
			// read the recurrence interval file
			POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(excelSheetName));
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			double ratio;
			int xIndex;
			for(int i=0; i<wb.getNumberOfSheets(); ++i) {
				HSSFSheet sheet = wb.getSheetAt(i);
				// do for selected fault only
				String sheetName = wb.getSheetName(i);
				int lastIndex = sheet.getLastRowNum();
				int r = 3;
				// read data for each row
				for(; r<=lastIndex; ++r) {
					HSSFRow row = sheet.getRow(r);
					HSSFCell cell = null;
					String rupName ="";
					if(row!=null)  cell = row.getCell( (short) 0);
					// segment name. Do for selected segment
					if(cell!=null) rupName = cell.getStringCellValue().trim();
					if(row==null || cell==null || 
							cell.getCellType()==HSSFCell.CELL_TYPE_BLANK || rupName.equalsIgnoreCase("")) {
						r=r+4;
						continue;
					}
					double mean = Double.NaN;
					cell = row.getCell((short)1);
					if(cell == null) continue;
					else mean = cell.getNumericCellValue();
					for(int col=2; col<=14; ++col) {
						ratio = row.getCell( (short) col).getNumericCellValue()/mean;	
						//System.out.println(rupName+","+mean+","+ratio);
						EvenlyDiscretizedFunc func = (EvenlyDiscretizedFunc)funcList.get(col-2);
						xIndex = func.getXIndex(ratio);
						//System.out.println(ratio);
						func.add(xIndex, 1.0);
					}
				}
			}
			for(int k=1, i=0; k<names.length; ++k, ++i) {
				ArrayList list = new ArrayList();
				list.add(funcList.get(i));
				CreateHistogramsFromSegSlipRateFile plot = new CreateHistogramsFromSegSlipRateFile(list, xAxisLabel, yAxisLabel);
				GraphWindow graphWindow= new GraphWindow(funcList, plotLabel, plot.getPlottingFeatures());
				graphWindow.setX_AxisLabel(xAxisLabel);
				graphWindow.setY_AxisLabel(yAxisLabel);
				graphWindow.setTitle(names[k]);
				graphWindow.setVisible(true);
				//graphWindow.setAxisRange(-0.5,graphWindow.getMaxX() , graphWindow.getMinY(), graphWindow.getMaxY());
				graphWindow.getGraphWidget().saveAsPDF(dirName+"/"+names[k]+".pdf");
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Create the histograms from segment recurrence interval file. 
	 * It creates a plot for each slip Model and Mag Area Relationship combination.
	 * Plot can be created for 1 segment or for all segments.
	 * It creates plots and saves PDFs in A_FaultRupRatesPlots_2_1 subdirectory in masterDirectory
	 * @param masterDirName MasterDirectoty where A_FaultRupRatesPlots_2_1 will be created
	 * @param excelSheetName Absolute pathname to excel file
	 */
	public static void createHistogramPlots(String masterDirName, String excelSheetName, String faultName, String segName) {
		String xAxisLabel = "Ratio of Original and Calculated Slip Rate";
		String yAxisLabel = "Count";
		String plotLabel = "Slip Rate Ratio";
		
		String[] names = {"Original Slip Rate",
			"Characteristic", 
			"Ellsworth-A_UniformBoxcar", "Ellsworth-A_WGCEP-2002", "Ellsworth-A_Tapered",
			"Ellsworth-B_UniformBoxcar", "Ellsworth-B_WGCEP-2002", "Ellsworth-B_Tapered",
			"Hanks & Bakun (2002)_UniformBoxcar", "Hanks & Bakun (2002)_WGCEP-2002", "Hanks & Bakun (2002)_Tapered",
			"Somerville (2006)_UniformBoxcar", "Somerville (2006)_WGCEP-2002", "Somerville (2006)_Tapered"};
		try {
			// directory to save the PDF files. Directory will be created if it does not exist already
			String dirName = masterDirName+"/"+faultName+"_"+segName+"/";
			File file = new File(dirName);
			if(!file.isDirectory()) { // create directory if it does not exist already
				file.mkdir();
			}
			
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0.2, 2.5, 24);
			func.setTolerance(func.getDelta());
			func.setName(faultName+"_"+segName);
			
			// read the recurrence interval file
			POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(excelSheetName));
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			double ratio;
			int xIndex;
			for(int i=0; i<wb.getNumberOfSheets(); ++i) {
				HSSFSheet sheet = wb.getSheetAt(i);
				// do for selected fault only
				String sheetName = wb.getSheetName(i);
				if(faultName!=null && !faultName.equalsIgnoreCase(sheetName)) continue;
				int lastIndex = sheet.getLastRowNum();
				int r = 3;
				// read data for each row
				for(; r<=lastIndex; ++r) {
					HSSFRow row = sheet.getRow(r);
					HSSFCell cell = null;
					String rupName ="";
					if(row!=null)  cell = row.getCell( (short) 0);
					// segment name. Do for selected segment
					if(cell!=null) rupName = cell.getStringCellValue().trim();
					if(segName!=null && !rupName.equalsIgnoreCase(segName)) continue;
					if(row==null || cell==null || 
							cell.getCellType()==HSSFCell.CELL_TYPE_BLANK || rupName.equalsIgnoreCase("")) {
						r=r+4;
						continue;
					}
					double mean = Double.NaN;
					cell = row.getCell((short)1);
					if(cell == null) continue;
					else mean = cell.getNumericCellValue();
					for(int col=2; col<=14; ++col) {
						ratio = row.getCell( (short) col).getNumericCellValue()/mean;	
						xIndex = func.getXIndex(ratio);
						func.add(xIndex, 1.0);
					}
				}
			}
			
			ArrayList<EvenlyDiscretizedFunc> list = new ArrayList<EvenlyDiscretizedFunc>();
			list.add(func);
			CreateHistogramsFromSegSlipRateFile plot = new CreateHistogramsFromSegSlipRateFile(list, xAxisLabel, yAxisLabel);
			GraphWindow graphWindow= new GraphWindow(list, plotLabel, plot.getPlottingFeatures());
			graphWindow.setX_AxisLabel(xAxisLabel);
			graphWindow.setY_AxisLabel(yAxisLabel);
			graphWindow.setTitle(segName);
			graphWindow.setVisible(true);
			//graphWindow.setAxisRange(-0.5,graphWindow.getMaxX() , graphWindow.getMinY(), graphWindow.getMaxY());
			graphWindow.getGraphWidget().saveAsPDF(dirName+"/"+segName+".pdf");
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
}
