/**
 * 
 */
package org.opensha.refFaultParamDb.gui.view;

import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;

/**
 * @author vipingupta
 *
 */
public class SectionInfoTextFileWriter extends AbstractSectionInfoFileWriter {
	
	public SectionInfoTextFileWriter(DB_AccessAPI dbConnection) {
		super(dbConnection);
	}
	
	/**
	 * Get String for faultSectionPrefData ( excluding slip rate and aseismic slip factor)
	 * @param faultSectionPrefData
	 * @return
	 */
	@Override
	public String getFaultAsString(FaultSectionPrefData faultSectionPrefData) {
		FaultTrace faultTrace = faultSectionPrefData.getFaultTrace(); 
		String str =  "#"+faultSectionPrefData.getSectionName()+"\n"+
			getValue(faultSectionPrefData.getShortName())+"\n"+
			getValue(faultSectionPrefData.getOrigAveUpperDepth())+"\n"+
			getValue(faultSectionPrefData.getAveLowerDepth())+"\n"+
			getValue(faultSectionPrefData.getAveDip()) +"\n"+
			getValue(faultSectionPrefData.getDipDirection())+"\n"+
			getValue(faultSectionPrefData.getAveRake())+"\n"+
			getValue(faultTrace.getTraceLength())+"\n"+
			faultTrace.getNumLocations()+"\n";
		// write all the point on the fault section trace
		for(int i=0; i<faultTrace.getNumLocations(); ++i)
			str+=(float)faultTrace.get(i).getLatitude()+"\t"+(float)faultTrace.get(i).getLongitude()+"\n";
		return str;
	}
	
	/**
	 * File format for writing fault sections in a fault model file.
	 * Fault sections within a fault model do not have slip rate and aseismic slip factor
	 * 
	 * @return
	 */
	@Override
	public  String getFileHeader() {
		return "********************************\n"+ 
			"#Section Name\n"+
			"#Short Name\n"+
			"#Ave Upper Seis Depth (km)\n"+
			"#Ave Lower Seis Depth (km)\n"+
			"#Ave Dip (degrees)\n"+
			"#Ave Dip Direction\n"+
			"#Ave Rake\n"+
			"#Trace Length (derivative value) (km)\n"+
			"#Num Trace Points\n"+
			"#lat1 lon1\n"+
			"#lat2 lon2\n"+
			"********************************\n";
	}
}
