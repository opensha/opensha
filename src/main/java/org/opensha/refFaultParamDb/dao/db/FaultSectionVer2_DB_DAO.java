package org.opensha.refFaultParamDb.dao.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import oracle.spatial.geometry.JGeometry;

import org.opensha.commons.data.estimate.MinMaxPrefEstimate;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;
import org.opensha.refFaultParamDb.vo.EstimateInstances;
import org.opensha.refFaultParamDb.vo.FaultSectionData;
import org.opensha.refFaultParamDb.vo.FaultSectionSummary;
import org.opensha.sha.faultSurface.FaultTrace;

/**
 * <p>Title: FaultSectionVer2_DB_DAO.java </p>
 * <p>Description: This class interacts with Fault Section table in CA Ref Fault Param
 * Database.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class FaultSectionVer2_DB_DAO {
	public final static String TABLE_NAME = "Fault_Section";
	public final static String SEQUENCE_NAME = "Fault_Section_Sequence";
	public final static String SECTION_ID = "Section_Id";
	public final static String AVE_LONG_TERM_SLIP_RATE_EST = "Ave_Long_Term_Slip_Rate_Est";
	public final static String AVE_DIP_EST = "Ave_Dip_Est";
	public final static String AVE_RAKE_EST = "Ave_Rake_Est";
	public final static String AVE_UPPER_DEPTH_EST = "Ave_Upper_Depth_Est";
	public final static String AVE_LOWER_DEPTH_EST = "Ave_Lower_Depth_Est";
	public final static String CONTRIBUTOR_ID =  "Contributor_Id";
	public final static String SECTION_NAME = "Name";
	public final static String SHORT_NAME = "Short_Name";
	public final static String ENTRY_DATE = "Entry_Date";
	public final static String COMMENTS   = "Comments";
	public final static String FAULT_TRACE = "Fault_Section_Trace";
	public final static String ASEISMIC_SLIP_FACTOR_EST = "Average_Aseismic_Slip_Est";
	public final static String DIP_DIRECTION = "Dip_Direction";
	public final static String SECTION_SOURCE_ID = "Section_Source_Id";
	public final static String QFAULT_ID = "QFault_Id";
	public final static String CONNECTOR_FLAG = "CONNECTOR_FLAG";
	public final static String CONNECTOR_FLAG_YES = "Y";
	public final static String CONNECTOR_FLAG_NO = "N";
	public final static String CONNECTOR_FLAG_DEFAULT = CONNECTOR_FLAG_NO;
	public final static String FAULT_ZONE_POLYGON = "FAULT_ZONE_POLYGON";
	
	// TODO Mercator Linear OK?
	public static final BorderType POLYGON_BORDER_TYPE = BorderType.MERCATOR_LINEAR;
	
	private DB_AccessAPI dbAccess;
	// estimate instance DAO
	private EstimateInstancesDB_DAO estimateInstancesDAO;
	//section source DAO
	private SectionSourceDB_DAO sectionSourceDAO;
	

	public FaultSectionVer2_DB_DAO(DB_AccessAPI dbAccess) {
		setDB_Connection(dbAccess);
	}

	/**
	 * Set the database connection
	 * @param dbAccess
	 */
	public void setDB_Connection(DB_AccessAPI dbAccess) {
		this.dbAccess = dbAccess;
		estimateInstancesDAO = new EstimateInstancesDB_DAO(dbAccess);
		sectionSourceDAO = new SectionSourceDB_DAO(dbAccess);
	}

	/**
	 * Add a new fault section to the database
	 * @param faultSection
	 * @return
	 */
	public int addFaultSection(FaultSectionData faultSection) {

		int faultSectionId = faultSection.getSectionId();
		String systemDate;
		try {
			// generate fault section Id
			if(faultSectionId<=0) faultSectionId = dbAccess.getNextSequenceNumber(SEQUENCE_NAME);
			systemDate = dbAccess.getSystemDate();
		}catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}

		// get JGeomtery object from fault trace
		JGeometry faultSectionTraceGeom =  SpatialUtils.getMultiPointGeomtery(faultSection.getFaultTrace());

		// various estimate ids

		int aveDipEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveDipEst());
		int aveUpperDepthEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveUpperDepthEst());
		int aveLowerDepthEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveLowerDepthEst());
		int aseismicSlipFactorEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAseismicSlipFactorEst());
		int sectionSourceId = this.sectionSourceDAO.getSectionSource(faultSection.getSource()).getSourceId();

		String columnNames="";
		String columnVals = "";

		// check if slip rate is present for this fault section
		EstimateInstances slipRateEst = faultSection.getAveLongTermSlipRateEst();
		if(slipRateEst!=null) { // if slip rate estimate is present
			int aveLongTermSlipRateEstId = this.estimateInstancesDAO.addEstimateInstance(slipRateEst);
			columnNames+=AVE_LONG_TERM_SLIP_RATE_EST+",";
			columnVals+=aveLongTermSlipRateEstId+",";
		}

		// check if rake is present for this section
		EstimateInstances aveRakeEst = faultSection.getAveRakeEst();
		if(aveRakeEst!=null) {
			int aveRakeEstId =  this.estimateInstancesDAO.addEstimateInstance(aveRakeEst);
			columnNames+=AVE_RAKE_EST+",";
			columnVals+=aveRakeEstId+",";
		}

		// check if dip direction is present for this section. Dip direction is not available wherever dip=90 degrees
		float dipDirection  = faultSection.getDipDirection();
		if(!Float.isNaN(dipDirection)) {
			columnNames+=DIP_DIRECTION+",";
			columnVals+=dipDirection+",";
		}
		// check if qfault Id is available
		String qfaultId = faultSection.getQFaultId();
		if(qfaultId!=null) {
			columnNames+=QFAULT_ID+",";
			columnVals+="'"+qfaultId+"',";
		}
		// check if short name is available
		String shortName = faultSection.getShortName();
		if(shortName!=null) {
			columnNames+=SHORT_NAME+",";
			columnVals+="'"+shortName+"',";
		}
		
		ArrayList<JGeometry> geomteryObjectList = new ArrayList<JGeometry>();
		
		if (faultSection.getZonePolygon() != null) {
			columnNames+=FAULT_ZONE_POLYGON+",";
			columnVals+="?,";
			geomteryObjectList.add(SpatialUtils.getMultiPointGeomtery(faultSection.getZonePolygon().getBorder()));
		}
		
		String connectorStr = faultSection.isConnector() ? CONNECTOR_FLAG_YES : CONNECTOR_FLAG_NO;

		// insert the fault section into the database
		geomteryObjectList.add(faultSectionTraceGeom);
		String sql = "insert into "+TABLE_NAME+"("+ SECTION_ID+","+
		columnNames+AVE_DIP_EST+","+AVE_UPPER_DEPTH_EST+","+AVE_LOWER_DEPTH_EST+","+
		CONTRIBUTOR_ID+","+SECTION_NAME+","+ENTRY_DATE+","+COMMENTS+","+
		FAULT_TRACE+","+ASEISMIC_SLIP_FACTOR_EST+","+
		SECTION_SOURCE_ID+","+CONNECTOR_FLAG+") values ("+
		faultSectionId+","+columnVals+
		aveDipEst+","+aveUpperDepthEst+","+aveLowerDepthEst+","+
		SessionInfo.getContributor().getId()+",'"+faultSection.getSectionName()+"','"+
		systemDate+"','"+faultSection.getComments()+"',?,"+
		aseismicSlipFactorEst+","+ sectionSourceId+",'"+connectorStr+"')";
		try {
			//System.out.println(sql);
			//System.exit(0);
			dbAccess.insertUpdateOrDeleteData(sql, geomteryObjectList);
			return faultSectionId;
		}
		catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}
	}

	/**
	 * Update the fault section in the database
	 * 
	 * @param faultSection
	 */
	public void update(FaultSectionData faultSection) {

		String systemDate;
		try {
			systemDate = dbAccess.getSystemDate();
		} catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}
		// get JGeomtery object from fault trace
		JGeometry faultSectionTraceGeom =  SpatialUtils.getMultiPointGeomtery(faultSection.getFaultTrace());

		// various estimate ids

		int aveDipEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveDipEst());
		int aveUpperDepthEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveUpperDepthEst());
		int aveLowerDepthEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAveLowerDepthEst());
		int aseismicSlipFactorEst = this.estimateInstancesDAO.addEstimateInstance(faultSection.getAseismicSlipFactorEst());
		int sectionSourceId = this.sectionSourceDAO.getSectionSource(faultSection.getSource()).getSourceId();

		String columnNames="";

		// check if slip rate is present for this fault section
		EstimateInstances slipRateEst = faultSection.getAveLongTermSlipRateEst();
		if(slipRateEst!=null) { // if slip rate estimate is present
			int aveLongTermSlipRateEstId = this.estimateInstancesDAO.addEstimateInstance(slipRateEst);
			columnNames+=AVE_LONG_TERM_SLIP_RATE_EST+"="+aveLongTermSlipRateEstId+",";
		} else columnNames+=AVE_LONG_TERM_SLIP_RATE_EST+"=NULL,";

		// check if rake is present for this section
		EstimateInstances aveRakeEst = faultSection.getAveRakeEst();
		if(aveRakeEst!=null) {
			int aveRakeEstId =  this.estimateInstancesDAO.addEstimateInstance(aveRakeEst);
			columnNames+=AVE_RAKE_EST+"="+aveRakeEstId+",";
		}else columnNames+=AVE_RAKE_EST+"=NULL,";

		// check if dip direction is present for this section. Dip direction is not available wherever dip=90 degrees
		float dipDirection  = faultSection.getDipDirection();
		if(!Float.isNaN(dipDirection)) {
			columnNames+=DIP_DIRECTION+"="+dipDirection+",";
		} else columnNames+=DIP_DIRECTION+"=NULL,";

		// check if qfault Id is available
		String qfaultId = faultSection.getQFaultId();
		if(qfaultId!=null) {
			columnNames+=QFAULT_ID+"="+"'"+qfaultId+"',";
		} else columnNames+=QFAULT_ID+"=NULL,";
		
		ArrayList<JGeometry> geomteryObjectList = new ArrayList<JGeometry>();

		// check if short name is available
		String shortName = faultSection.getShortName();
		if(shortName!=null) {
			columnNames+=SHORT_NAME+"="+"'"+shortName+"',";
		} else columnNames+=SHORT_NAME+"=NULL,";
		
		if (faultSection.getZonePolygon() != null) {
			columnNames+=FAULT_ZONE_POLYGON+"=?,";
			geomteryObjectList.add(SpatialUtils.getMultiPointGeomtery(faultSection.getZonePolygon().getBorder()));
		} else columnNames+=FAULT_ZONE_POLYGON+"=NULL,";
		
		String connectorStr = faultSection.isConnector() ? CONNECTOR_FLAG_YES : CONNECTOR_FLAG_NO;

		// insert the fault section into the database
		geomteryObjectList.add(faultSectionTraceGeom);
		String sql = "update "+TABLE_NAME+" set "+ 
		columnNames+AVE_DIP_EST+"="+aveDipEst+","+AVE_UPPER_DEPTH_EST+"="+aveUpperDepthEst+","+
		AVE_LOWER_DEPTH_EST+"="+aveLowerDepthEst+","+CONTRIBUTOR_ID+"="+SessionInfo.getContributor().getId()+","+
		SECTION_NAME+"='"+faultSection.getSectionName()+"',"+ENTRY_DATE+"='"+systemDate+"',"+
		COMMENTS+"='"+faultSection.getComments()+"',"+FAULT_TRACE+"=?,"+
		ASEISMIC_SLIP_FACTOR_EST+"="+aseismicSlipFactorEst+","+SECTION_SOURCE_ID+"="+sectionSourceId+","+
		CONNECTOR_FLAG+"='"+connectorStr+"'"+" where "+SECTION_ID+"="+faultSection.getSectionId();
		try {
			//System.out.println(sql);
			//System.exit(0);
			dbAccess.insertUpdateOrDeleteData(sql, geomteryObjectList);
		}
		catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}

	}

	/**
	 * Update Dip direction in database
	 * 
	 * @param faultSectionId
	 * @param dipDirection
	 */
	public void updateDipDirection(int faultSectionId, float dipDirection) {
		String sql = "update "+TABLE_NAME+" set "+ 
		DIP_DIRECTION+"="+dipDirection+
		" where "+SECTION_ID+"="+faultSectionId;
		try {
			//System.out.println(sql);
			//System.exit(0);
			dbAccess.insertUpdateOrDeleteData(sql);
		}
		catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}

	}
	
	public void updateZonePolygon(int faultSectionId, Region polygon) {
		ArrayList<JGeometry> geomteryObjectList = new ArrayList<JGeometry>();
		
		String sql = "UPDATE "+TABLE_NAME+" SET "+FAULT_ZONE_POLYGON+"=";
		if (polygon == null) {
			sql += "NULL";
		} else {
			sql += "?";
			geomteryObjectList.add(SpatialUtils.getMultiPointGeomtery(polygon.getBorder()));
		}
		
		sql += " WHERE "+SECTION_ID+"="+faultSectionId;
		
		try {
			dbAccess.insertUpdateOrDeleteData(sql, geomteryObjectList);
		} catch (SQLException e) {
			throw new InsertException(e.getMessage());
		}
	}

	/**
	 * Get the fault section based on fault section Id
	 * @param faultSectionId
	 * @return
	 */
	public FaultSectionData getFaultSection(int faultSectionId) {	
		String condition = " where "+SECTION_ID+"="+faultSectionId;
		ArrayList<FaultSectionData> faultSectionsList = query(condition);	
		FaultSectionData faultSection = null;		
		if(faultSectionsList.size()>0) faultSection = (FaultSectionData)faultSectionsList.get(0);
		return faultSection;  
	}

	/**
	 * Get all the fault sections from the database
	 * @return
	 */
	public ArrayList<FaultSectionData> getAllFaultSections() {
		return query(" ");
	}

	public ArrayList<FaultSectionSummary> getAllFaultSectionsSummary() {
		return getSummary("");
	}

	public FaultSectionSummary getFaultSectionSummary(int faultSectionId) {
		ArrayList<FaultSectionSummary> faultSectionSummaryList = getSummary(" where "+SECTION_ID+"="+faultSectionId);
		FaultSectionSummary faultSectionSummary = null;
		if (faultSectionSummaryList.size()>0) faultSectionSummary = faultSectionSummaryList.get(0);
		return faultSectionSummary;
	}

	public FaultSectionSummary getFaultSectionSummary(String faultSectionName) {
		ArrayList<FaultSectionSummary> faultSectionSummaryList = getSummary(" where "+SECTION_NAME+"='"+faultSectionName+"'");
		FaultSectionSummary faultSectionSummary = null;
		if (faultSectionSummaryList.size()>0) faultSectionSummary = faultSectionSummaryList.get(0);
		return faultSectionSummary;
	}

	private ArrayList<FaultSectionSummary> getSummary(String condition) {
		ArrayList<FaultSectionSummary> faultSectionsSummaryList = new ArrayList<FaultSectionSummary>();
		String sql =  "select "+SECTION_ID+","+SECTION_NAME+" from "+TABLE_NAME+" "+condition+" order by ("+SECTION_NAME+")";

		try {
			ResultSet rs  = dbAccess.queryData(sql);
			while(rs.next())  {
				FaultSectionSummary faultSectionSummary = new FaultSectionSummary();
				faultSectionSummary.setSectionId(rs.getInt(SECTION_ID));
				faultSectionSummary.setSectionName(rs.getString(SECTION_NAME));
				faultSectionsSummaryList.add(faultSectionSummary);
			}
			rs.close();
		} catch(SQLException e) { throw new QueryException(e.getMessage()); }
		return faultSectionsSummaryList;
	}


	/**
	 * Return the HashMap containing section Id and the corresponding  Slip rate estimate
	 * @return
	 */
	public HashMap<Integer, EstimateInstances> getSlipRateEstimates() {
		HashMap<Integer, EstimateInstances> sectionSlipRate = new HashMap<Integer, EstimateInstances>();
		try {
			String sql = "select "+SECTION_ID+","+AVE_LONG_TERM_SLIP_RATE_EST+" from "+TABLE_NAME;
			ResultSet rs = dbAccess.queryData(sql);
			while(rs.next()) {
				EstimateInstances slipRateEstInstance= null;
				int slipRateEstId= rs.getInt(FaultSectionVer2_DB_DAO.AVE_LONG_TERM_SLIP_RATE_EST);
				if(!rs.wasNull()) slipRateEstInstance = this.estimateInstancesDAO.getEstimateInstance(slipRateEstId);
				sectionSlipRate.put(new Integer(rs.getInt(SECTION_ID)), slipRateEstInstance);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return sectionSlipRate;
	}

	/**
	 * Get the fault sections based on some filter parameter
	 * @param condition
	 * @return
	 */
	private ArrayList<FaultSectionData> query(String condition) {
		ArrayList<FaultSectionData> faultSectionsList = new ArrayList<FaultSectionData>();
		// this awkward sql is needed else we get "Invalid scale exception"
		String sqlWithSpatialColumnNames =  "select "+SECTION_ID+",to_char("+ENTRY_DATE+") as "+ENTRY_DATE+
		","+AVE_LONG_TERM_SLIP_RATE_EST+","+AVE_DIP_EST+","+AVE_RAKE_EST+","+AVE_UPPER_DEPTH_EST+","+
		AVE_LOWER_DEPTH_EST+","+SECTION_NAME+","+COMMENTS+","+FAULT_TRACE+","+ASEISMIC_SLIP_FACTOR_EST+
		",("+DIP_DIRECTION+"+0) "+DIP_DIRECTION+","+SECTION_SOURCE_ID +","+QFAULT_ID+","+SHORT_NAME+
		","+CONNECTOR_FLAG+","+FAULT_ZONE_POLYGON+" from "+TABLE_NAME+condition;

		String sqlWithNoSpatialColumnNames =  "select "+SECTION_ID+",to_char("+ENTRY_DATE+") as "+ENTRY_DATE+
		","+AVE_LONG_TERM_SLIP_RATE_EST+","+AVE_DIP_EST+","+AVE_RAKE_EST+","+AVE_UPPER_DEPTH_EST+","+
		AVE_LOWER_DEPTH_EST+","+SECTION_NAME+","+COMMENTS+","+ASEISMIC_SLIP_FACTOR_EST+
		",("+DIP_DIRECTION+"+0) "+DIP_DIRECTION+","+SECTION_SOURCE_ID +","+QFAULT_ID+","+SHORT_NAME+
		","+CONNECTOR_FLAG+" from "+TABLE_NAME+condition;

		ArrayList<String> spatialColumnNames = new ArrayList<String>();
		spatialColumnNames.add(FAULT_TRACE);
		spatialColumnNames.add(FAULT_ZONE_POLYGON);
		try {
			SpatialQueryResult spatialQueryResult  = dbAccess.queryData(sqlWithSpatialColumnNames, sqlWithNoSpatialColumnNames, spatialColumnNames);
			ResultSet rs = spatialQueryResult.getCachedRowSet();
			int i=0;
			while(rs.next())  {
				FaultSectionData faultSection = new FaultSectionData();
				faultSection.setSectionId(rs.getInt(SECTION_ID));
				faultSection.setComments(rs.getString(COMMENTS));

				faultSection.setEntryDate(rs.getString(ENTRY_DATE));
				faultSection.setSectionName(rs.getString(SECTION_NAME));
//				System.out.println("Getting fault section: "+faultSection.getSectionName()
//							+" ("+faultSection.getSectionId()+")");
				faultSection.setSource(this.sectionSourceDAO.getSectionSource(rs.getInt(SECTION_SOURCE_ID)).getSectionSourceName());
				faultSection.setAseismicSlipFactorEst(this.estimateInstancesDAO.getEstimateInstance(rs.getInt(FaultSectionVer2_DB_DAO.ASEISMIC_SLIP_FACTOR_EST)));
				faultSection.setAveDipEst(this.estimateInstancesDAO.getEstimateInstance(rs.getInt(FaultSectionVer2_DB_DAO.AVE_DIP_EST)));

				// get slip rate estimate if slip rate is provided for this fault section
				int slipRateEstId= rs.getInt(FaultSectionVer2_DB_DAO.AVE_LONG_TERM_SLIP_RATE_EST);
				if(!rs.wasNull()) faultSection.setAveLongTermSlipRateEst(this.estimateInstancesDAO.getEstimateInstance(slipRateEstId));

				// get rake estimate if rake is provided for this fault section
				int rakeEstId= rs.getInt(FaultSectionVer2_DB_DAO.AVE_RAKE_EST);
				if(!rs.wasNull()) faultSection.setAveRakeEst(this.estimateInstancesDAO.getEstimateInstance(rakeEstId));

				float dipDirection = rs.getFloat(FaultSectionVer2_DB_DAO.DIP_DIRECTION);
				if(rs.wasNull()) dipDirection = Float.NaN;
				faultSection.setDipDirection(dipDirection);

				faultSection.setAveLowerDepthEst(this.estimateInstancesDAO.getEstimateInstance(rs.getInt(FaultSectionVer2_DB_DAO.AVE_LOWER_DEPTH_EST)));
				faultSection.setAveUpperDepthEst(this.estimateInstancesDAO.getEstimateInstance(rs.getInt(FaultSectionVer2_DB_DAO.AVE_UPPER_DEPTH_EST)));

				// fault trace
				String sectionName = faultSection.getSectionName();
				double upperDepth = ((MinMaxPrefEstimate)faultSection.getAveUpperDepthEst().getEstimate()).getPreferred();
//				MinMaxPrefEstimate est = ((MinMaxPrefEstimate)faultSection.getAveUpperDepthEst().getEstimate());
//				System.out.println(est.toString());
//				System.out.println("Upper Depth: "+upperDepth);
				ArrayList<JGeometry> geometries = spatialQueryResult.getGeometryObjectsList(i++);
				FaultTrace faultTrace = getFaultTrace(sectionName, upperDepth, geometries);	
				faultSection.setFaultTrace(faultTrace);

				// qfault Id
				String qFaultId = rs.getString(QFAULT_ID);
				if(!rs.wasNull()) faultSection.setQFaultId(qFaultId);

				// short name
				String shortName = rs.getString(SHORT_NAME);
				if(!rs.wasNull()) faultSection.setShortName(shortName);
				
				// connector
				String connStr = rs.getString(CONNECTOR_FLAG);
				boolean connector = connStr.equals(CONNECTOR_FLAG_YES);
				faultSection.setConnector(connector);
				
				// zone polygon
				Region zone;
				if (geometries.size() < 2 || geometries.get(1) == null) {
					zone = null;
				} else {
					JGeometry geom = geometries.get(1);
					
					LocationList zoneLocs = SpatialUtils.loadMultiPointGeometries(geom, 0d);
					
					zone = new Region(zoneLocs, POLYGON_BORDER_TYPE);
				}
				faultSection.setZonePolygon(zone);

				faultSectionsList.add(faultSection);
			}
			rs.close();
		} catch(SQLException e) { throw new QueryException(e.getMessage()); }
		return faultSectionsList;
	}

	public static FaultTrace getFaultTrace(String sectionName, double upperDepth, ArrayList<JGeometry> geometries) {
		JGeometry faultSectionGeom =(JGeometry) geometries.get(0);
		FaultTrace faultTrace = new FaultTrace(sectionName);
		faultTrace.addAll(SpatialUtils.loadMultiPointGeometries(faultSectionGeom, upperDepth));
//		System.out.println("Number of trace pts: "+faultTrace.size());
//		System.out.println("Number of geoms: "+geometries.size());
//		if (geometries.size() == 2)
//			System.out.println("Num in 2nd geom: "+geometries.get(1).getNumPoints());
		return faultTrace;
	}
	
	/**
	 * Remove the fault section from th database
	 * 
	 * @param faultSectionId
	 */
	public void removeFaultSection(int faultSectionId) {
		String sql = "delete from "+TABLE_NAME+" where "+SECTION_ID+"="+faultSectionId;
		try {
			dbAccess.insertUpdateOrDeleteData(sql);
			FaultModelDB_DAO fm2db = new FaultModelDB_DAO(dbAccess);
			fm2db.removeSectionFromAllModels(faultSectionId);
			DeformationModelDB_DAO dm2db = new DeformationModelDB_DAO(dbAccess);
			dm2db.removeSectionFromAllModels(faultSectionId);
		} catch(SQLException e) { throw new UpdateException(e.getMessage()); }
	}

	public static void main(String args[]) {
		DB_AccessAPI db = DB_ConnectionPool.getLatestReadOnlyConn();
		FaultSectionVer2_DB_DAO fs2db = new FaultSectionVer2_DB_DAO(db);
		for (FaultSectionData fs : fs2db.getAllFaultSections()) {
			System.out.println("Connector? "+fs.isConnector());
			System.out.println("Zone? "+fs.getZonePolygon());
		}
		try {
			db.destroy();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.exit(0);
	}

}
