package org.opensha.refFaultParamDb.gui.view;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.PrefFaultSectionDataDB_DAO;
import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

/**
 * @author vipingupta
 *
 */
public abstract class AbstractSectionInfoFileWriter implements Runnable {
	private  PrefFaultSectionDataDB_DAO faultSectionPrefDAO; 
	private CalcProgressBar progressBar;
	private int totSections;
	private int currSection;
	
	public AbstractSectionInfoFileWriter(DB_AccessAPI dbConnection) {
		faultSectionPrefDAO = new PrefFaultSectionDataDB_DAO(dbConnection);
	}
	
	/**
	 * Write FaultSectionPrefData to file.
	 * @param faultSectionIds  array of faultsection Ids
	 * @param file
	 */
	public  void writeForFaultModel(int[] faultSectionIds, File file) {
		try {
			currSection=0;
			totSections = faultSectionIds.length;
			// make JProgressBar
			progressBar = new CalcProgressBar("Writing to file", "Writing Fault sections");
			progressBar.displayProgressBar();
			Thread t = new Thread(this);
			t.start();
			// write to file
			FileWriter fw = new FileWriter(file);
			String header = getFileHeader();
			if (!header.endsWith("\n"))
				header += "\n";
			fw.write(header);
			
			for(currSection=0; currSection<totSections; ++currSection) {
				System.out.println(currSection);
				writeForFaultModel(faultSectionIds[currSection], fw);
			}
			fw.close();
			
			// dispose the progressbar
			progressBar.showProgress(false);
		}catch(Exception e) {
			e.printStackTrace();
			String message = "Error: "+e.getMessage()+"\nSee console output for more details.";
			JOptionPane.showMessageDialog(null, message, "Error Saving File!", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public void run() {
		try {
			while(currSection<totSections) {
				//System.out.println("Updating "+currSection+ " of "+totSections);
				progressBar.updateProgress(this.currSection, this.totSections);
				Thread.currentThread().sleep(500);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Write FaultSectionPrefData to the file. It does not contain slip rate and aseismic slip factor
	 * @param faultSectionId Fault section Id for which data needs to be written to file
	 * @param fw
	 * @throws IOException 
	 */
	public  void writeForFaultModel(int faultSectionId, FileWriter fw) throws IOException {
		writeForFaultModel(faultSectionPrefDAO.getFaultSectionPrefData(faultSectionId), fw);
	}
	
	
	/**
	 * Write FaultSectionPrefData to the file. It does not contain slip rate and aseismic slip factor
	 * @param faultSectionPrefData
	 * @param fw
	 * @throws IOException 
	 */
	public void writeForFaultModel(FaultSectionPrefData faultSectionPrefData, FileWriter fw) throws IOException {
		fw.write(getFaultAsString(faultSectionPrefData));
	}
	
	protected String getValue(double val) {
		if(Double.isNaN(val)) return "Not Available";
		else return GUI_Utils.decimalFormat.format(val);
	}
	
	protected String getValue(String val) {
		if(val==null || val.equalsIgnoreCase("")) return "Not Available";
		else return val;
	}
	
	/**
	 * Get String for representation for faultSectionPrefData
	 * 
	 * @param faultSectionPrefData
	 * @return
	 */
	public abstract String getFaultAsString(FaultSectionPrefData faultSectionPrefData);
	
	/**
	 * File format for writing fault sections in a fault model file.
	 * Fault sections within a fault model do not have slip rate and aseismic slip factor
	 * 
	 * @return
	 */
	public abstract String getFileHeader();
}
