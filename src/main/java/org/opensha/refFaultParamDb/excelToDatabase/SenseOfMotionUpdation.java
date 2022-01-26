package org.opensha.refFaultParamDb.excelToDatabase;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opensha.commons.util.FileUtils;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;

/**
 * Update sense of motion in database from Peter Bird's excel sheets
 * 
 * @author vipingupta
 *
 */
public class SenseOfMotionUpdation {
//	 update the sense of motion
	private final static String FILE_NAME = "org/opensha/refFaultParamDb/excelToDatabase/SenseOfMotion.csv";
	// database connection
	private  static final DB_AccessAPI dbConnection = DB_ConnectionPool.getDB2ReadOnlyConn();
	 
	public static void main(String[] args) {
		SessionInfo.setUserName("vgupta");
		SessionInfo.setPassword("vgupta");
		try {
			ArrayList<String> lines = FileUtils.loadFile(FILE_NAME);
			int updatedLines = 0;
			for(int i=1; i<lines.size(); ++i) {
				String line = lines.get(i);
				StringTokenizer tokenizer = new StringTokenizer(line,",");
				String entryDate = tokenizer.nextToken();
				int siteId= Integer.parseInt(tokenizer.nextToken());
				int referenceId =  Integer.parseInt(tokenizer.nextToken());
				String senseOfMotion= tokenizer.nextToken(); 
				ResultSet rsInfoId = dbConnection.queryData("select info_id from " +
						"combined_events_info  where site_id="+siteId+
						"   and entry_date='"+entryDate+"'");
				while(rsInfoId.next()) {
					int infoId = rsInfoId.getInt("info_id");
					ResultSet senseOfMotionQual= dbConnection.queryData("select SENSE_OF_MOTION_QUAL from COMBINED_DISPLACEMENT_INFO where INFO_ID="+infoId);
					if(senseOfMotionQual.next()) {
						dbConnection.insertUpdateOrDeleteData("update COMBINED_DISPLACEMENT_INFO set SENSE_OF_MOTION_QUAL='"+senseOfMotion+"' where INFO_ID="+infoId);
						++updatedLines;
					}
					senseOfMotionQual= dbConnection.queryData("select SENSE_OF_MOTION_QUAL from COMBINED_SLIP_RATE_INFO where INFO_ID="+infoId);
					if(senseOfMotionQual.next()) {
						dbConnection.insertUpdateOrDeleteData("update COMBINED_SLIP_RATE_INFO set SENSE_OF_MOTION_QUAL='"+senseOfMotion+"' where INFO_ID="+infoId);
						++updatedLines;
					}
				
				}
				//System.out.println("Updated Lines="+updatedLines);
			}
			System.out.println("Lines in Excel sheet="+(lines.size()-1));
			System.out.println("Updated Lines="+updatedLines);
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
}
