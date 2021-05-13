package org.opensha.sha.gcim.calc;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gcim.ui.infoTools.IMT_Info;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
//import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.depricated.Baker07_SaPga_ImCorrRel;
//import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.depricated.Bradley11_AsiSa_ImCorrRel;
//import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.depricated.Bradley11_PgaSa_ImCorrRel;
//import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.depricated.Bradley11_SiSa_ImCorrRel;
import org.opensha.sha.gcim.imr.attenRelImpl.ASI_WrapperAttenRel.BA_2008_ASI_AttenRel;
import org.opensha.sha.gcim.imr.attenRelImpl.SI_WrapperAttenRel.BA_2008_SI_AttenRel;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.ASI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.SI_Param;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

public class HardCodedGCIMTest {
	
	private static void setSAPeriodInIMR(ScalarIMR imr, double period) {
		((Parameter<Double>)imr.getIntensityMeasure())
		.getIndependentParameter(PeriodParam.NAME).setValue(new Double(period));
	}
	
	/**
	 * This sets the site and site params of imri to match those already defined in the 'main' imrj
	//and makes them non-editable
	 * @param imri
	 * @param site
	 */
	private static void overrideSiteParams(ScalarIMR imri,
			Site site) {
			//loop over all of the site parameters of imri
			ListIterator<Parameter<?>> imriParamIt = imri.getSiteParamsIterator();
			ListIterator<Parameter<?>> siteParamIt = site.getParametersIterator();
			while (imriParamIt.hasNext()) {
				Parameter<?> siteParamImri = imriParamIt.next();
//				site.addParameter((ParameterAPI)siteParam.clone());
				//for each imri site parameter, see if it is imrj, and if it is then set it to that and
				//make it non-ediatable
				while (siteParamIt.hasNext()) {
					Parameter<?> siteParam = siteParamIt.next();
					if (siteParamImri == siteParam) {
//						imri.set
//						.addParameter((ParameterAPI)siteParam.clone());
					}
				}
			}
				
	}
	
	

	/**
	 * @param args
	 * @throws RemoteException 
	 */
	public static void main(String[] args) throws RemoteException {
		AbstractERF erf = new FakeFrankel96();
		
		//------------------------------------------------------------------------------
		//1) things that would be defined in the main hazardCurveApplication
//		EqkRupForecast erf = new Frankel96_AdjustableEqkRupForecast();
		erf.updateForecast();
		ArbitrarilyDiscretizedFunc magDistFilter = null;
		ScalarIMR imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		HashMap<TectonicRegionType, ScalarIMR> imrMap
				= new HashMap<TectonicRegionType, ScalarIMR>();
		imrMap.put(TectonicRegionType.ACTIVE_SHALLOW, imr);
		double period_IMj = 1.0;
		double maxDist = 200.0;
		// set to SA 1.0s
		imr.setIntensityMeasure(SA_Param.NAME);
		setSAPeriodInIMR(imr, period_IMj);
		
		Site site = new Site(new Location(34, -118));
		ListIterator<Parameter<?>> paramIt = imr.getSiteParamsIterator();
		while (paramIt.hasNext()) {
			Parameter<?> siteParam = paramIt.next();
			site.addParameter((Parameter)siteParam.clone());
		}
		IMT_Info imtInfo = new IMT_Info();
		ArbitrarilyDiscretizedFunc hazFunction = imtInfo.getDefaultHazardCurve(SA_Param.NAME);

		
		//------------------------------------------------------------------------------
		//2) Things that would be defined in the GCIM control panel (in the HazardCurve GUI)
		
		//The IM level for which the GCIM distributions would be calculated for
		//(or alternatively, similar to Disaggregation, specify probability)
		double iml = Math.log(0.2);
		
		//The number of different IMi's to compute conditional distributions for
		int numIMi = 6;

		//Create arrays to store the IMi, AttenRel, CorrRel, and perio (is IMi is SA) for each IMi of interest
		ArrayList<HashMap<TectonicRegionType, ScalarIMR>> imiMapAttenRels = 
					new ArrayList<HashMap<TectonicRegionType, ScalarIMR>>();
		ArrayList<String> imiTypes = new ArrayList<String>();
//		ArrayList<ImCorrelationRelationship> imijCorrRels = new ArrayList<ImCorrelationRelationship>();
		ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> imijMapCorrRels = 
					new ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>>();
		ArrayList<Double> imiPeriods = new ArrayList<Double>();
		
		String imiType;
		double imiPeriod;
		ScalarIMR imri;
		ImCorrelationRelationship imijCorrRel;
		
//		The lines below would be parsed from the Hazard curve GUI: GCIM control panel
		//TODO make imr's into an imrMap i.e. different IMRs for different tectonic types
		//i==1
		imiType=SA_Param.NAME;
		imiPeriod = 0.1;
		imri = new CB_2008_AttenRel(null);
		HashMap<TectonicRegionType, ScalarIMR> imriMap
						= new HashMap<TectonicRegionType, ScalarIMR>();
		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
		imijCorrRel = new BakerJayaram08_ImCorrRel();
		HashMap<TectonicRegionType, ImCorrelationRelationship> imijCorrRelMap
						= new HashMap<TectonicRegionType, ImCorrelationRelationship>();
		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
		imri.setParamDefaults();
		imri.setIntensityMeasure(imiType);
		setSAPeriodInIMR(imri, imiPeriod);
		imiTypes.add(imiType);
		imiMapAttenRels.add(imriMap); 
		imijMapCorrRels.add(imijCorrRelMap);
		//i==2
		imiType=SA_Param.NAME;
		imiPeriod = 0.2;
		imri = new CB_2008_AttenRel(null);
		imriMap = new HashMap<TectonicRegionType, ScalarIMR>();
		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
		imijCorrRel = new BakerJayaram08_ImCorrRel();
		imijCorrRelMap = new HashMap<TectonicRegionType, ImCorrelationRelationship>();
		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
		imri.setParamDefaults();
		imri.setIntensityMeasure(imiType);
		setSAPeriodInIMR(imri, imiPeriod);
		imiTypes.add(imiType);
		imiMapAttenRels.add(imriMap); 
		imijMapCorrRels.add(imijCorrRelMap);
//		//i==3
		imiType=SA_Param.NAME;
		imiPeriod = 1.0;
		imri = new CB_2008_AttenRel(null);
		imriMap = new HashMap<TectonicRegionType, ScalarIMR>();
		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
		imijCorrRel = new BakerJayaram08_ImCorrRel();
		imijCorrRelMap = new HashMap<TectonicRegionType, ImCorrelationRelationship>();
		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
		imri.setParamDefaults();
		imri.setIntensityMeasure(imiType);
		setSAPeriodInIMR(imri, imiPeriod);
		imiTypes.add(imiType);
		imiMapAttenRels.add(imriMap); 
		imijMapCorrRels.add(imijCorrRelMap);
//		//i==4
//		imiType=PGA_Param.NAME;
//		imri = new CB_2008_AttenRel(null);
//		imriMap = new HashMap<TectonicRegionType, ScalarIntensityMeasureRelationshipAPI>();
//		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
//		imijCorrRel = new Bradley11_PgaSa_ImCorrRel();
//		imijCorrRelMap = new HashMap<TectonicRegionType, ImCorrelationRelationship>();
//		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
//		imri.setParamDefaults();
//		imri.setIntensityMeasure(imiType);
//		imiTypes.add(imiType);
//		imiMapAttenRels.add(imriMap); 
//		imijMapCorrRels.add(imijCorrRelMap);
//		//i==5
//		imiType=ASI_Param.NAME;
//		imri = new BA_2008_ASI_AttenRel(null);
//		imriMap = new HashMap<TectonicRegionType, ScalarIntensityMeasureRelationshipAPI>();
//		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
//		imijCorrRel = new Bradley11_AsiSa_ImCorrRel();
//		imijCorrRelMap = new HashMap<TectonicRegionType, ImCorrelationRelationship>();
//		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
//		imri.setParamDefaults();
//		imri.setIntensityMeasure(imiType);
//		imiTypes.add(imiType);
//		imiMapAttenRels.add(imriMap); 
//		imijMapCorrRels.add(imijCorrRelMap);
//		//i==6
//		imiType=SI_Param.NAME;
//		imri = new BA_2008_SI_AttenRel(null);
//		imriMap = new HashMap<TectonicRegionType, ScalarIntensityMeasureRelationshipAPI>();
//		imriMap.put(TectonicRegionType.ACTIVE_SHALLOW, imri);
//		imijCorrRel = new Bradley11_SiSa_ImCorrRel();
//		imijCorrRelMap = new HashMap<TectonicRegionType, ImCorrelationRelationship>();
//		imijCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imijCorrRel);
//		imri.setParamDefaults();
//		imri.setIntensityMeasure(imiType);
//		imiTypes.add(imiType);
//		imiMapAttenRels.add(imriMap); 
//		imijMapCorrRels.add(imijCorrRelMap);
		
		

		
		//------------------------------------------------------------------------------
		//3) Hazard Curve calculation
		HazardCurveCalculator curveCalc = new HazardCurveCalculator();
		curveCalc.getHazardCurve(hazFunction, site, imrMap, erf);

		//------------------------------------------------------------------------------
		//4) GCIM calculations
		// initiate the GcimCalculator
		//TODO currently in getMultipleGcims() results are printed to screen - modify 
//		GcimCalculator gcimCalc = new GcimCalculator();
		GcimCalculator gcimCalc = new GcimCalculator();
		gcimCalc.getRuptureContributions(iml, site, imrMap, erf, maxDist, magDistFilter);
		
//		gcimCalc.getMultipleGcims(numIMi, imiMapAttenRels, imiTypes, imijCorrRels, 
//				maxDist, magDistFilter);
		gcimCalc.getMultipleGcims(numIMi, imiMapAttenRels, imiTypes, imijMapCorrRels, 
				maxDist, magDistFilter);
		gcimCalc.printResultsToConsole();
		
		System.exit(0);
	}
}
