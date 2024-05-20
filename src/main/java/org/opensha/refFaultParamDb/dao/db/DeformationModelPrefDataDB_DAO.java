/**
 * 
 */
package org.opensha.refFaultParamDb.dao.db;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.opensha.commons.data.estimate.NormalEstimate;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.PrintAndExitUncaughtExceptionHandler;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;
import org.opensha.refFaultParamDb.vo.DeformationModelSummary;
import org.opensha.refFaultParamDb.vo.EstimateInstances;
import org.opensha.refFaultParamDb.vo.FaultSectionData;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;

/**
 * 
 * Get the preferred slip and aseismic slip factor from the deformation model
 * 
 * @author vipingupta
 *
 */
public class DeformationModelPrefDataDB_DAO {
	private final static String TABLE_NAME = "Pref_Deformation_Model_Data";
	private final static String DEFORMATION_MODEL_ID = "Deformation_Model_Id";
	private final static String SECTION_ID = "Section_Id";
	private final static String PREF_LONG_TERM_SLIP_RATE = "Pref_Long_Term_Slip_Rate";
	private final static String SLIP_STD_DEV = "Slip_Std_Dev";
	private final static String PREF_ASEISMIC_SLIP = "Pref_Aseismic_Slip";
	private HashMap<Integer, Double> slipRateMap;
	private HashMap<Integer, Double> aseismicSlipMap;
	private HashMap<Integer, Double> stdDevMap;
	private int selectedDefModelId = -1;
	private DB_AccessAPI dbAccess;
	private PrefFaultSectionDataDB_DAO prefFaultSectionDAO;
	private DeformationModelDB_DAO deformationModelDB_DAO;
	private static ArrayList<Integer> faultSectionIdList;

	public DeformationModelPrefDataDB_DAO(DB_AccessAPI dbAccess) {
		setDB_Connection(dbAccess);
	}

	/**
	 * Set the database connection
	 * @param dbAccess
	 */
	public void setDB_Connection(DB_AccessAPI dbAccess) {
		this.dbAccess = dbAccess;
		prefFaultSectionDAO = new PrefFaultSectionDataDB_DAO(dbAccess); 
		deformationModelDB_DAO = new DeformationModelDB_DAO(dbAccess);
	}

	/**
	 * Remove the exisiting data from preferred data table and re-populate it.
	 *
	 */
	public void rePopulatePrefDataTable() {
		removeAll(); // remove all the pref data

		// iterate over all deformation Models
		DeformationModelSummaryDB_DAO defModelSumDAO = new DeformationModelSummaryDB_DAO(this.dbAccess);
		ArrayList<DeformationModelSummary> deformationModelList = defModelSumDAO.getAllDeformationModels();


		int faultSectionId, deformationModelId;
		double aseismicSlipFactor, slipRate;
		for(int i=0; i<deformationModelList.size(); ++i) {
			DeformationModelSummary defModelSummary = (DeformationModelSummary)deformationModelList.get(i);
			deformationModelId = defModelSummary.getDeformationModelId();
			// get the fault sections in each deformation model
			ArrayList<Integer> faultSectionIdList = deformationModelDB_DAO.getFaultSectionIdsForDeformationModel(deformationModelId);
			for(int j=0; j<faultSectionIdList.size(); ++j) {
				faultSectionId = ((Integer)faultSectionIdList.get(j)).intValue();
				aseismicSlipFactor = FaultSectionData.getPrefForEstimate(deformationModelDB_DAO.getAseismicSlipEstimate(deformationModelId, faultSectionId));
				/*if(aseismicSlipFactor==1) {
					System.out.println(deformationModelId+","+faultSectionId+","+deformationModelDB_DAO.getAseismicSlipEstimate(deformationModelId, faultSectionId));
					System.exit(0);
				}*/
				EstimateInstances estimateInstance = deformationModelDB_DAO.getSlipRateEstimate(deformationModelId, faultSectionId);
				slipRate = FaultSectionData.getPrefForEstimate(estimateInstance);
				double slipRateStdDev = Double.NaN;
				//System.out.println(faultSectionId);
				if(!Double.isNaN(slipRate)) {
					if(estimateInstance.getEstimate() instanceof NormalEstimate)
						slipRateStdDev = ((NormalEstimate)estimateInstance.getEstimate()).getStdDev();
				}
				addToTable(deformationModelId, faultSectionId, aseismicSlipFactor, slipRate, slipRateStdDev);
			}
		}
	}


	/**
	 * Add data to table
	 * @param deformationModelId
	 * @param faultSectionId
	 * @param aseismicSlipFactor
	 * @param slipRate
	 */
	private void addToTable(int deformationModelId, int faultSectionId, 
			double aseismicSlipFactor, double slipRate, double slipRateStdDev) {
		String columnNames = "";
		String colVals = "";
		if(!Double.isNaN(slipRate)) {
			columnNames +=PREF_LONG_TERM_SLIP_RATE+",";
			colVals +=slipRate+",";
		}
		if(!Double.isNaN(slipRateStdDev)) {
			columnNames +=SLIP_STD_DEV+",";
			colVals +=slipRateStdDev+",";
		}
		String sql = "insert into "+TABLE_NAME+" ("+DEFORMATION_MODEL_ID+","+
		SECTION_ID+","+columnNames+PREF_ASEISMIC_SLIP+") values ("+
		deformationModelId+","+faultSectionId+","+colVals+aseismicSlipFactor+")";
		try {
			dbAccess.insertUpdateOrDeleteData(sql);
		}
		catch(SQLException e) {
			throw new InsertException(e.getMessage());
		}
	}


	/**
	 * Get a list of all fault sections within this deformation model
	 * @param deformationModelId
	 * @return
	 */
	public ArrayList<Integer> getFaultSectionIdsForDeformationModel(int deformationModelId) {
		if(selectedDefModelId!=deformationModelId) this.cache(deformationModelId);
		return faultSectionIdList;
	}

	/**
	 * Get Fault Section Pref data for a deformation model ID and Fault section Id
	 * @param deformationModelId
	 * @param faultSectionId
	 * @return
	 */
	public FaultSectionPrefData getFaultSectionPrefData(int deformationModelId,
			int faultSectionId) {
		FaultSectionPrefData faultSectionPrefData = prefFaultSectionDAO.getFaultSectionPrefData(faultSectionId).clone();
		// get slip rate and aseimic slip factor from deformation model
		faultSectionPrefData.setAseismicSlipFactor(this.getAseismicSlipFactor(deformationModelId, faultSectionId));
		faultSectionPrefData.setAveSlipRate(this.getSlipRate(deformationModelId, faultSectionId));
		faultSectionPrefData.setSlipRateStdDev(this.getSlipStdDev(deformationModelId, faultSectionId));
		return faultSectionPrefData;
	}

	/**
	 * Get the preferred Slip Rate value for selected Deformation Model and Fault Section
	 * Returns NaN if Slip rate is not available
	 * 
	 * @param deformationModelId
	 * @param faultSectionId
	 * @return
	 */
	public double getSlipRate(int deformationModelId, int faultSectionId) {
		if(selectedDefModelId!=deformationModelId) this.cache(deformationModelId);
		Double slipRate =  (Double)slipRateMap.get(Integer.valueOf(faultSectionId));
		if(slipRate==null) return Double.NaN;
		else return slipRate.doubleValue();
	}	

	/**
	 * Get the Std Dev for Slip Rate value for selected Deformation Model and Fault Section
	 * Returns NaN if Std Dev is not  available
	 * 
	 * @param deformationModelId
	 * @param faultSectionId
	 * @return
	 */
	public double getSlipStdDev(int deformationModelId, int faultSectionId) {
		if(selectedDefModelId!=deformationModelId) this.cache(deformationModelId);
		Double stdDev =  (Double)stdDevMap.get(Integer.valueOf(faultSectionId));
		if(stdDev==null) return Double.NaN;
		else return stdDev.doubleValue();
	}	

	/**
	 * Get the preferred Aseismic Slip Factor for selected Deformation Model and Fault Section
	 * 
	 * 
	 * @param deformationModelId
	 * @param faultSectionId
	 * @return
	 */
	public double getAseismicSlipFactor(int deformationModelId, int faultSectionId) {
		if(selectedDefModelId!=deformationModelId) this.cache(deformationModelId);
		Double aseismicSlip = (Double)aseismicSlipMap.get(Integer.valueOf(faultSectionId));
		if(aseismicSlip == null) return Double.NaN;
		else return aseismicSlip.doubleValue();
	}

	public void cacheEverything(int defModelId) {
		// update the cache for fault sections
		prefFaultSectionDAO.getAllFaultSectionPrefData();
	}

	private void cache(int defModelId) {
		slipRateMap = new HashMap<Integer, Double>();
		aseismicSlipMap = new HashMap<Integer, Double>();
		stdDevMap = new HashMap<Integer, Double>();
		faultSectionIdList = new ArrayList<Integer>();
		String sql= "select "+SECTION_ID+"," +
		" ("+PREF_ASEISMIC_SLIP+"+0) "+PREF_ASEISMIC_SLIP+","+
		" ("+SLIP_STD_DEV+"+0) "+SLIP_STD_DEV+","+
		" ("+PREF_LONG_TERM_SLIP_RATE+"+0) "+PREF_LONG_TERM_SLIP_RATE+
		" from "+TABLE_NAME+" where " + DEFORMATION_MODEL_ID+"="+defModelId;
		double aseismicSlipFactor=Double.NaN,slip=Double.NaN, stdDev=Double.NaN;
		Integer sectionId;
		try {
			ResultSet rs  = this.dbAccess.queryData(sql);
			while(rs.next()) {
				aseismicSlipFactor = rs.getFloat(PREF_ASEISMIC_SLIP);
				if(rs.wasNull()) aseismicSlipFactor = Double.NaN;
				slip = rs.getFloat(PREF_LONG_TERM_SLIP_RATE);
				if(rs.wasNull()) slip = Double.NaN;
				stdDev = rs.getFloat(SLIP_STD_DEV);
				if(rs.wasNull()) stdDev = Double.NaN;
				sectionId = Integer.valueOf(rs.getInt(SECTION_ID));
				faultSectionIdList.add(sectionId);
				slipRateMap.put(sectionId, Double.valueOf(slip)) ;
				aseismicSlipMap.put(sectionId, Double.valueOf(aseismicSlipFactor));
				stdDevMap.put(sectionId, Double.valueOf(stdDev));
			}
		} catch (SQLException e) {
			throw new QueryException(e.getMessage());
		}
		selectedDefModelId = defModelId;

	}


	/**
	 * Remove all preferred data
	 */
	private void removeAll() {
		String sql = "delete from "+TABLE_NAME;
		try {
			dbAccess.insertUpdateOrDeleteData(sql);
		} catch(SQLException e) { throw new UpdateException(e.getMessage()); }
	}

	public static void main(String[] args) {
		try {
			Thread.setDefaultUncaughtExceptionHandler(new PrintAndExitUncaughtExceptionHandler());
			DB_AccessAPI dbAccessAPI = new ServerDB_Access(ServerDB_Access.SERVLET_URL_DB3);
			
			Preconditions.checkArgument(args.length == 2, "Must have 2 arguments!");
			
			String user;
			String pass;
			if (args[0].equals("--file")) {
				if (!new File(args[1]).exists()) {
					throw new FileNotFoundException("Password file not found: "+args[1]);
				}
				String[] up = FileUtils.loadFile(args[1]).get(0).trim().split(":");
				Preconditions.checkState(up.length == 2, "user/pass file has incorrect format" +
						" (should be 'user:pass'");
				user = up[0];
				pass = up[1];
			} else {
				user = args[0];
				pass = args[1];
			}
			
			SessionInfo.setUserName(user);
			SessionInfo.setPassword(pass);
			SessionInfo.setContributorInfo();
			DeformationModelPrefDataDB_DAO defModelPrefDataDB_DAO = new DeformationModelPrefDataDB_DAO(dbAccessAPI);
			defModelPrefDataDB_DAO.rePopulatePrefDataTable();
			System.exit(0);
		} catch (Throwable t) {
			// TODO Auto-generated catch block
			t.printStackTrace();
			System.exit(1);
		}
	}
}
