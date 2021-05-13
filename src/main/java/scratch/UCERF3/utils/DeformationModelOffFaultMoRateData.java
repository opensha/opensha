package scratch.UCERF3.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSetMath;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.analysis.DeformationModelsCalc;
import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;

/**
 * This reads and provides the off-fault gridded moment rates provided by Kaj Johnson 
 * for each deformation model at each RELM region grid point.  He sent the latest
 * files via email to Ned Field on Oct 11, 2012.  
 * 
 * Kaj assumed a seismogenic thickness of 11 km.
 * 
 * Note that the FM3.1 vs 3.2 results from Zeng are identical (that's what he told us his were)
 * 
 * Note that the NeoKinema model has zero values in SE California (276 of the 7637 points are zero; 3.6%).
 *
 * @author field
 *
 */
public class DeformationModelOffFaultMoRateData {
	
	private static DeformationModelOffFaultMoRateData data;
	/**
	 * Gets an instance of the DeformationModelOffFaultMoRateData (cached so that we don't wast time instantiating it multiple times)
	 * @return
	 */
	public static DeformationModelOffFaultMoRateData getInstance() {
		if (data == null)
			data = new DeformationModelOffFaultMoRateData();
		return data;
	}
	
	public static final String SUBDIR = "DeformationModels";
	public static final String FILENAME_FM3_1 = "gridded_moment_fm_3_1_jan14_2013_combined.txt";
	public static final String FILENAME_FM3_2 = "gridded_moment_fm_3_2_jan14_2013_combined.txt";
	public static final double KAJ_SEISMO_THICKNESS = 11d;
	public static final double REVISED_SEISMO_THICKNESS = 11d;
	public static final double NEOK_ZERO_VALS = Math.pow(10, -1000)*REVISED_SEISMO_THICKNESS/KAJ_SEISMO_THICKNESS;
	
	final static CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion  = new CaliforniaRegions.RELM_TESTING_GRIDDED();
	
	GriddedGeoDataSet neok_Fm3pt1_xyzData, zeng_b_bound_Fm3pt1_xyzData, zeng_orig_Fm3pt1_xyzData, abm_Fm3pt1_xyzData,geol_Fm3pt1_xyzData, abmPlusGeol_Fm3pt1_xyzData;
	GriddedGeoDataSet neok_Fm3pt2_xyzData, zeng_b_bound_Fm3pt2_xyzData, zeng_orig_Fm3pt2_xyzData, abm_Fm3pt2_xyzData, geol_Fm3pt2_xyzData, abmPlusGeol_Fm3pt2_xyzData;
	
	/**
	 * This is private so that we always use the cached version. use getInstance() instead.
	 */
	private DeformationModelOffFaultMoRateData() {
		readDefModelGridData();
	}
	
	
	/**
	 * This makes the off-fault moment rate distributions assuming the total for the ABM model is correct.
	 * This is spread uniformly for the geologic model, and an average of this and the spatial ABM-model dist for
	 * the average Geol+ABM model.
	 */
	private void makeGeolData() {
		// do for FM 3.1
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = DeformationModels.ABM;
		double assumedTotalMoRate = DeformationModelsCalc.calcFaultMoRateForDefModel(fm, dm, true)+getTotalOffFaultMomentRate(fm, dm);
		double geolMoRate = assumedTotalMoRate - DeformationModelsCalc.calcFaultMoRateForDefModel(fm, DeformationModels.GEOLOGIC, true);
		geol_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);
		int numPts = geol_Fm3pt1_xyzData.size();
		for(int i=0;i<numPts;i++)
			geol_Fm3pt1_xyzData.set(i,geolMoRate/numPts);
		
		abmPlusGeol_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);
		for(int i=0;i<numPts;i++)
			abmPlusGeol_Fm3pt1_xyzData.set(i,(geol_Fm3pt1_xyzData.get(i)+abm_Fm3pt1_xyzData.get(i))/2.0);
		
		// now do for FM 3.2
		fm = FaultModels.FM3_2;
		dm = DeformationModels.ABM;
		assumedTotalMoRate = DeformationModelsCalc.calcFaultMoRateForDefModel(fm, dm, true)+getTotalOffFaultMomentRate(fm, dm);
		geolMoRate = assumedTotalMoRate - DeformationModelsCalc.calcFaultMoRateForDefModel(fm, DeformationModels.GEOLOGIC, true);
		geol_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);
		numPts = geol_Fm3pt2_xyzData.size();
		for(int i=0;i<numPts;i++)
			geol_Fm3pt2_xyzData.set(i,geolMoRate/numPts);
		
		abmPlusGeol_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);
		for(int i=0;i<numPts;i++)
			abmPlusGeol_Fm3pt2_xyzData.set(i,(geol_Fm3pt2_xyzData.get(i)+abm_Fm3pt2_xyzData.get(i))/2.0);

	}
	
	/**
	 * This returns the total off-fault moment rate
	 * @param fm
	 * @param dm
	 * @return
	 */
	public double getTotalOffFaultMomentRate(FaultModels fm, DeformationModels dm) {
		if (dm == DeformationModels.UCERF2_ALL) 
			return 0.54e19;	// this is the value for non CA B faults, background, and C Zones (see Table in report)
		double total=0;
		GriddedGeoDataSet data = getDefModSpatialOffFaultMoRates(fm, dm);
		for(int i=0;i<data.size();i++)
			total+=data.get(i);
		return total;
	}

	
	/**
	 * 
	 */
	private void readDefModelGridData() {
		neok_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		zeng_orig_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		zeng_b_bound_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		abm_Fm3pt1_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		neok_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		zeng_orig_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		zeng_b_bound_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		abm_Fm3pt2_xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		
		// to convert seismo thickness
		double CONVERSION = (REVISED_SEISMO_THICKNESS/KAJ_SEISMO_THICKNESS);

		// read for FM 3.1
		try {
			BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUBDIR, FILENAME_FM3_1));
			int l=-1;
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
				if (l == 0)
					continue;	// skip header
				String[] st = StringUtils.split(line,",");
				Location loc = new Location(Double.valueOf(st[0]),Double.valueOf(st[1]));
				int index = griddedRegion.indexForLocation(loc);
				neok_Fm3pt1_xyzData.set(index, Math.pow(10, Double.valueOf(st[2]))*CONVERSION);
				zeng_b_bound_Fm3pt1_xyzData.set(index, Math.pow(10, Double.valueOf(st[3]))*CONVERSION);
				zeng_orig_Fm3pt1_xyzData.set(index, Math.pow(10, Double.valueOf(st[4]))*CONVERSION);
				abm_Fm3pt1_xyzData.set(index, Math.pow(10, Double.valueOf(st[5]))*CONVERSION);
			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		// read for FM 3.2
		try {
			BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUBDIR, FILENAME_FM3_2));
			int l=-1;
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
				if (l == 0)
					continue;
				String[] st = StringUtils.split(line,",");
				Location loc = new Location(Double.valueOf(st[0]),Double.valueOf(st[1]));
				int index = griddedRegion.indexForLocation(loc);
				neok_Fm3pt2_xyzData.set(index, Math.pow(10, Double.valueOf(st[2]))*CONVERSION);
				zeng_b_bound_Fm3pt2_xyzData.set(index, Math.pow(10, Double.valueOf(st[3]))*CONVERSION);
				zeng_orig_Fm3pt2_xyzData.set(index, Math.pow(10, Double.valueOf(st[4]))*CONVERSION);
				abm_Fm3pt2_xyzData.set(index, Math.pow(10, Double.valueOf(st[5]))*CONVERSION);
			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	
	/**
	 * This generates the average of off-fault moment rates for the deformation models.  
	 * 
	 * @param fm - the specified fault model
	 * @param includeGeologic - tells whether to include the geologic model (which has a uniform distribution)
	 * 
	 * @return
	 */
	public GriddedGeoDataSet getAveDefModelSpatialOffFaultMoRates(FaultModels fm, boolean includeGeologic) {
		GriddedGeoDataSet aveData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		GriddedGeoDataSet neok_xyzData, zeng_b_bound_xyzData, zeng_orig_xyzData, abm_xyzData, geol_xyzData=null;
		
		if(fm == FaultModels.FM3_1) {
			neok_xyzData = neok_Fm3pt1_xyzData;
//			zeng_orig_xyzData = zeng_orig_Fm3pt1_xyzData;
			zeng_b_bound_xyzData = zeng_b_bound_Fm3pt1_xyzData;
			abm_xyzData = abm_Fm3pt1_xyzData;
			if(includeGeologic) {
				if(geol_Fm3pt1_xyzData == null) {makeGeolData();}
				geol_xyzData = geol_Fm3pt1_xyzData;
			}
		}
		else if(fm == FaultModels.FM3_2) {
			neok_xyzData = neok_Fm3pt2_xyzData;
//			zeng_orig_xyzData = zeng_orig_Fm3pt2_xyzData;
			zeng_b_bound_xyzData = zeng_b_bound_Fm3pt2_xyzData;
			abm_xyzData = abm_Fm3pt2_xyzData;
			if(includeGeologic) {
				if(geol_Fm3pt2_xyzData == null) {makeGeolData();}
				geol_xyzData = geol_Fm3pt2_xyzData;
			}
		}
		else
			throw new RuntimeException("Error - Unsupported fault model: "+fm);

		double sum=0;
		for(int i=0;i<aveData.size();i++) {
			double val=0;
			int num =0;
			if(neok_xyzData.get(i) > 2*NEOK_ZERO_VALS) {
				val += neok_xyzData.get(i);
				num+=1;
			}
			if(zeng_b_bound_xyzData.get(i) > 2*NEOK_ZERO_VALS) {
				val += zeng_b_bound_xyzData.get(i);
				num+=1;
			}
			if(abm_xyzData.get(i) > 2*NEOK_ZERO_VALS) {
				val += abm_xyzData.get(i);
				num+=1;
			}
			if(includeGeologic) {
				if(geol_xyzData.get(i) > 2*NEOK_ZERO_VALS) {
					val += geol_xyzData.get(i);
					num+=1;
				}
			}
			
			if(num !=0) {
				aveData.set(i,val/num);
				sum += aveData.get(i);
			}
			else {
				aveData.set(i,0.0);
			}
		}
		
		return aveData;
	}
	
	
	
	/**
	 * This generates an average PDF of the off-fault deformation models.  
	 * 
	 * @param fm - the specified fault model
	 * @param includeGeologic - tells whether to include the geologic model (which has a uniform distribution)
	 * @return
	 */
	public GriddedGeoDataSet getAveDefModelPDF(FaultModels fm, boolean includeGeologic) {
		return getNormalizdeData(getAveDefModelSpatialOffFaultMoRates(fm, includeGeologic));
	}
	
	
	
	/**
	 * This average over both fault models (FM3.1 and FM 3.2)
	 * 	 
	 * @param fm - the specified fault model
	 * @param includeGeologic - tells whether to include the geologic model (which has a uniform distribution)
	 * @return
	 */
	public GriddedGeoDataSet getAveDefModelPDF(boolean includeGeologic) {
		GriddedGeoDataSet aveData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		GriddedGeoDataSet data1 = this.getAveDefModelPDF(FaultModels.FM3_1, includeGeologic);
		GriddedGeoDataSet data2 = this.getAveDefModelPDF(FaultModels.FM3_2, includeGeologic);
		for(int i=0; i< data1.size(); i++)
			aveData.set(i, data1.get(i)+data2.get(i));
		return getNormalizdeData(aveData);
	}
	
	/**
	 * This writes the total moment rates to system.out
	 */
	public void writeAllTotalMomentRates() {
		System.out.println("For FM3.1:");
		FaultModels fm = FaultModels.FM3_1;
		System.out.println("\t"+DeformationModels.ABM.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.ABM));
		System.out.println("\t"+DeformationModels.NEOKINEMA.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.NEOKINEMA));
		System.out.println("\t"+DeformationModels.ZENG.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.ZENG));
		System.out.println("\t"+DeformationModels.GEOLOGIC.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.GEOLOGIC));
		System.out.println("\t"+DeformationModels.GEOLOGIC_PLUS_ABM.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.GEOLOGIC_PLUS_ABM));
		System.out.println("For FM3.2:");
		fm = FaultModels.FM3_2;
		System.out.println("\t"+DeformationModels.ABM.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.ABM));
		System.out.println("\t"+DeformationModels.NEOKINEMA.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.NEOKINEMA));
		System.out.println("\t"+DeformationModels.ZENG.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.ZENG));
		System.out.println("\t"+DeformationModels.GEOLOGIC.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.GEOLOGIC));
		System.out.println("\t"+DeformationModels.GEOLOGIC_PLUS_ABM.getShortName()+"\t"+(float) getTotalOffFaultMomentRate(fm, DeformationModels.GEOLOGIC_PLUS_ABM));
	}

	
	/**
	 * this returns a GriddedGeoDataSet with the deformation model moment rate (Nm/yr).
	 * Note that the NeoKinema model has zero values in SE California (276 of the 7637 points are zero; 3.6%).
	 * at each RELM region grid point 
	 * @param fm - Fault Model
	 * @param dm - Deformation Model
	 * @return
	 */
	public GriddedGeoDataSet getDefModSpatialOffFaultMoRates(FaultModels fm, DeformationModels dm) {
		
		GriddedGeoDataSet data=null;

		if(fm == FaultModels.FM3_1) {
			switch(dm) {
			case ABM:
				data = abm_Fm3pt1_xyzData;
				break;
			case NEOKINEMA:
				data = neok_Fm3pt1_xyzData;
				break;
			case ZENG:
				data = zeng_orig_Fm3pt1_xyzData;
				break;
			case ZENGBB:
				data = zeng_b_bound_Fm3pt1_xyzData;
				break;
			case GEOLOGIC:
				if(geol_Fm3pt1_xyzData == null) makeGeolData();
				data = geol_Fm3pt1_xyzData;
				break;
			case GEOLOGIC_PLUS_ABM:
				if (abmPlusGeol_Fm3pt1_xyzData == null) makeGeolData();
				data = abmPlusGeol_Fm3pt1_xyzData;
				break;
			case GEOL_P_ABM_OLD_MAPPED:
				if (abmPlusGeol_Fm3pt1_xyzData == null) makeGeolData();
				data = abmPlusGeol_Fm3pt1_xyzData;
				break;
			}
		}

		else if(fm == FaultModels.FM3_2) {
			switch(dm) {
			case ABM:
				data = abm_Fm3pt2_xyzData;
				break;
			case NEOKINEMA:
				data = neok_Fm3pt2_xyzData;
				break;
			case ZENG:
				data = zeng_orig_Fm3pt2_xyzData;
				break;
			case ZENGBB:
				data = zeng_b_bound_Fm3pt2_xyzData;
				break;
			case GEOLOGIC:
				if(geol_Fm3pt2_xyzData == null) makeGeolData();
				data = geol_Fm3pt2_xyzData;
				break;
			case GEOLOGIC_PLUS_ABM:
				if (abmPlusGeol_Fm3pt2_xyzData == null) makeGeolData();
				data = abmPlusGeol_Fm3pt2_xyzData;
				break;
			}
		}
		else {
			throw new RuntimeException("Error - unrecognized fault model: "+fm);
		}
		
		if (dm == DeformationModels.MEAN_UCERF3) {
			List<Double> weights = Lists.newArrayList();
			List<DeformationModels> dms = Lists.newArrayList();
			double sum = 0;
			for (DeformationModels d : DeformationModels.values()) {
				double weight = d.getRelativeWeight(null);
				if (weight > 0) {
					weights.add(weight);
					sum += weight;
					dms.add(d);
				}
			}
			if (sum != 0)
				for (int i=0; i<weights.size(); i++)
					weights.set(i, weights.get(i)/sum);
			data = null;
			for (int i=0; i<dms.size(); i++) {
				DeformationModels d = dms.get(i);
				double weight = weights.get(i);
				GriddedGeoDataSet subData = getDefModSpatialOffFaultMoRates(fm, d);
				if (data == null)
					data = new GriddedGeoDataSet(subData.getRegion(), subData.isLatitudeX());
				Preconditions.checkState(subData.size() == data.size());
				for (int j=0; j<data.size(); j++)
					data.set(j, data.get(j)+weight*subData.get(j));
			}
		}
		
		if(data == null)
			throw new RuntimeException("Error - unrecognized deformation model: "+dm);
		
		return data;
	}

	
	
	/**
	 * This returns the spatial PDF of off-fault moment rate (values sum to 1.0)
	 * Note that the NeoKinema model has ~zero values in SE California (276 of the 7637 points are zero; 3.6%).
	 * at each RELM region grid point 
	 * @param fm - Fault Model
	 * @param dm - Deformation Model
	 * @return
	 */
	public GriddedGeoDataSet getDefModSpatialOffFaultPDF(FaultModels fm, DeformationModels dm) {
		return getNormalizdeData(getDefModSpatialOffFaultMoRates(fm, dm));
	}
	
	
	/**
	 * This counts and returns the number of zero values
	 * @param data
	 */
	private static int countZeroValues(GriddedGeoDataSet data) {
		int numZeros = 0;
		double sum=0;
		for(int i=0; i<data.size();i++) {
			if(data.get(i) <= 2*NEOK_ZERO_VALS) {	// factor of two to avoid rounding errors
				numZeros += 1;
			}
			else{
				sum += data.get(i); 
			}
		}
		return numZeros;
	}

	
	
	/**
	 * this normalizes the data so they sum to 1.0 (original data is unchanged)
	 * @param data
	 */
	public static GriddedGeoDataSet getNormalizdeData(GriddedGeoDataSet data) {
		GriddedGeoDataSet normData = new GriddedGeoDataSet(griddedRegion, true);;
		double sum=0;
		for(int i=0;i<data.size();i++) 
			sum += data.get(i);
		for(int i=0;i<data.size();i++) 
			normData.set(i, data.get(i)/sum);
		return normData;
	}
	
	private void testPlotMaps() {
		try {
			FaultModels fm = FaultModels.FM3_1;
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.NEOKINEMA), "NeoKinema FM3.1 PDF", "test meta data", "NeoKinema3pt1_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.ZENG), "Zeng FM3.1 PDF", "test meta data", "Zeng3pt1_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.ABM), "ABM FM3.1 PDF", "test meta data", "ABM_3pt1_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.GEOLOGIC), "Geologic FM3.1 PDF", "test meta data", "Geol_3pt1_PDF_Map");
			
			fm = FaultModels.FM3_2;
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.NEOKINEMA), "NeoKinema FM3.2 PDF", "test meta data", "NeoKinema3pt2_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.ZENG), "Zeng FM3.2 PDF", "test meta data", "Zeng3pt2_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.ABM), "ABM FM3.2 PDF", "test meta data", "ABM_3pt2_PDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getDefModSpatialOffFaultPDF(fm, DeformationModels.GEOLOGIC), "Geologic FM3.2 PDF", "test meta data", "Geol_3pt2_PDF_Map");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void plotAveDefModPDF_Map(boolean includeGeologic) {
		
		try {
			String info;
			if(includeGeologic)
				info = "average of 4 deformation models (including Geologic)";
			else
				info = "average of 3 deformation models (excluding Geologic)";
			GMT_CA_Maps.plotSpatialPDF_Map(getAveDefModelPDF(FaultModels.FM3_1, includeGeologic), "Ave Def Mod PDF for  FM3.1", info, "AveDefModPDF_FM3_1_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getAveDefModelPDF(FaultModels.FM3_2, includeGeologic), "Ave Def Mod PDF for  FM3.2", info, "AveDefModPDF_FM3_2_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getAveDefModelPDF(includeGeologic), "Ave Def Mod PDF for both FM 3.1 and 3.2", info, "AveDefModPDF_Map");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// get the UCERF PDF with C zones for comparison
		ModMeanUCERF2 erf= new ModMeanUCERF2();
		erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		erf.updateForecast();
		GriddedGeoDataSet ucerf2_OffFaultMoRate = ERF_Calculator.getMomentRatesInRegion(erf, RELM_RegionUtils.getGriddedRegionInstance());
		GriddedGeoDataSet ucerf2_OffFaultSeis = ERF_Calculator.getNucleationRatesInRegion(erf, RELM_RegionUtils.getGriddedRegionInstance(),0d,10d);

		try {
			GMT_CA_Maps.plotSpatialPDF_Map(getNormalizdeData(ucerf2_OffFaultMoRate), "UCERF2 Off-Fault MoRate", "this includes the C zones", "UCERF2_OffFaultMoRatePDF_Map");
			GMT_CA_Maps.plotSpatialPDF_Map(getNormalizdeData(ucerf2_OffFaultSeis), "UCERF2 Off-Fault Seis", "this includes the C zones", "UCERF2_OffFaultSeisPDF_Map");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * This lists the number of zero-value points in each model
	 */
	public void listNumZeroValues() {
		System.out.println("neok_Fm3pt1_xyzData numZeros = "+countZeroValues(neok_Fm3pt1_xyzData));
		System.out.println("zeng_orig_Fm3pt1_xyzData numZeros = "+countZeroValues(zeng_orig_Fm3pt1_xyzData));
		System.out.println("zeng_b_bound_Fm3pt1_xyzData numZeros = "+countZeroValues(zeng_b_bound_Fm3pt1_xyzData));
		System.out.println("abm_Fm3pt1_xyzData numZeros = "+countZeroValues(abm_Fm3pt1_xyzData));
		System.out.println("neok_Fm3pt2_xyzData numZeros = "+countZeroValues(neok_Fm3pt2_xyzData));
		System.out.println("zeng_orig_Fm3pt2_xyzData numZeros = "+countZeroValues(zeng_orig_Fm3pt2_xyzData));
		System.out.println("zeng_b_bound_Fm3pt2_xyzData numZeros = "+countZeroValues(zeng_b_bound_Fm3pt2_xyzData));
		System.out.println("abm_Fm3pt2_xyzData numZeros = "+countZeroValues(abm_Fm3pt2_xyzData));
		System.out.println("(out of "+neok_Fm3pt2_xyzData.size()+" grid points)");
	}

	
	public void testAveMapDiffs() {
		GriddedGeoDataSet data1 = getAveDefModelPDF(FaultModels.FM3_1, false);
		GriddedGeoDataSet data2 = getAveDefModelPDF(FaultModels.FM3_2, false);
		double ave=0, min=Double.MAX_VALUE, max=0;
		for(int i=0; i< data1.size();i++) {
			double ratio = data1.get(i)/data2.get(i);
			ave += ratio;
			if(min>ratio) min=ratio;
			if(max<ratio) max=ratio;
		}
		ave /= data1.size();
		System.out.println("ave="+(float)ave+"\tmin="+(float)min+"\tmax="+(float)max);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		DeformationModelOffFaultMoRateData test = DeformationModelOffFaultMoRateData.getInstance();
//		test.testAveMapDiffs();
		test.plotAveDefModPDF_Map(false);
//		test.listNumZeroValues();
//		test.writeAllTotalMomentRates();
//		test.testPlotMaps();
	}

}
