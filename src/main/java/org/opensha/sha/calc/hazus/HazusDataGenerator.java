package org.opensha.sha.calc.hazus;

import java.util.ArrayList;
import java.util.ListIterator;

import org.opensha.commons.data.region.SitesInGriddedRegion;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.calc.HazusMapCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.SiteTranslator;


/**
 * <p>Title: HazusDataGenerator</p>
 * <p>Description: This class generates the Hazard Curve data to be
 * provided as the input to the Hazus.</p>
 * @author : Edward (Ned) Field and Nitin Gupta
 * @version 1.0
 */

public class HazusDataGenerator implements ParameterChangeWarningListener{

  //grid region for the LA County provide by the Hazus
  private final double MIN_LAT=  32.4;
  private final double MAX_LAT= 42.1;
  private final double MIN_LON = -125.5 ;
  private final double MAX_LON= -114.1;
  private final double GRID_SPACING= 0.1;

  private Frankel02_AdjustableEqkRupForecast forecast;
  private USGS_Combined_2004_AttenRel attenRel;
  private SitesInGriddedRegion sites;

  public HazusDataGenerator() {

    createAttenRel_Instance();
    createERF_Instance();
    createRegion();
    getSiteParamsForRegion();
    HazusMapCalculator calc = new HazusMapCalculator();
    calc.showProgressBar(false);
    String metaData = "Hazus Run 1(b) for the finer Grid spacing of 0.1km without Soil Effects with Background:\n"+
    	                "\n"+
                      "ERF: "+forecast.getName()+"\n"+
                      "IMR Name: "+attenRel.getName()+"\n"+
                      "\t"+"Site Name: "+ Vs30_Param.NAME+"\n"+
                      "Region Info: "+
                      "\t MIN LAT: "+sites.getRegion().getMinLat()+" MAX LAT:"+sites.getRegion().getMaxLat()+
                      " MIN LON: "+sites.getRegion().getMinLon()+" MAX LON: "+sites.getRegion().getMaxLon()+
                      " Grid Spacing: "+sites.getRegion().getSpacing()+"\n";
    //doing ofr PGA
    calc.getHazardMapCurves(sites,attenRel,forecast,metaData);
  }


  public static void main(String[] args) {
    try {
      HazusDataGenerator hazusDataGenerator1 = new HazusDataGenerator();
    }
    catch (Exception ex) {
      System.out.println(ex.getMessage());
      System.exit(0);
    }
  }

  /**
   * Gets the wills site class for the given sites
   */
  private void getSiteParamsForRegion() {
    sites.addSiteParams(attenRel.getSiteParamsIterator());
    //getting Wills Site Class
    //region.setSiteParamsForRegionFromServlet(true);
    //getting the Attenuation Site Parameters Liat
    ListIterator it = attenRel.getSiteParamsIterator();
    //creating the list of default Site Parameters, so that site parameter values can be filled in
    //if Site params file does not provide any value to us for it.
    ArrayList defaultSiteParams = new ArrayList();
    SiteTranslator siteTrans = new SiteTranslator();
    while (it.hasNext()) {
      //adding the clone of the site parameters to the list
      Parameter tempParam = (Parameter) ( (Parameter) it.next()).clone();
      //getting the Site Param Value corresponding to the default Wills site class selected by the user
      // for the seleted IMR  from the SiteTranslator
      siteTrans.setParameterValue(tempParam, WillsMap2000.WILLS_DE, Double.NaN);
      defaultSiteParams.add(tempParam);
    }
    sites.setDefaultSiteParams(defaultSiteParams);
  }

  private void createERF_Instance(){
	   forecast = new Frankel02_AdjustableEqkRupForecast();
	   forecast.getTimeSpan().setDuration(50.0);
	   /*forecast.getAdjustableParameterList().getParameter(
               Frankel02_AdjustableEqkRupForecast.BACK_SEIS_NAME).setValue(Frankel02_AdjustableEqkRupForecast.
                                        BACK_SEIS_EXCLUDE);*/
	   forecast.getAdjustableParameterList().getParameter(
	                Frankel02_AdjustableEqkRupForecast.BACK_SEIS_NAME).setValue(Frankel02_AdjustableEqkRupForecast.
	                                         BACK_SEIS_INCLUDE);
	   forecast.getAdjustableParameterList().getParameter(
	                Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_NAME).setValue(
	                    Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_FINITE);
	   forecast.updateForecast();
  }


  private void createAttenRel_Instance(){
	  attenRel = new USGS_Combined_2004_AttenRel(this);
          attenRel.setParamDefaults();
	  attenRel.getParameter(Vs30_Param.NAME).setValue(new Double(760));
	  attenRel.getParameter(SigmaTruncTypeParam.NAME).setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
	  attenRel.getParameter(SigmaTruncLevelParam.NAME).setValue(new Double(3.0));
	  attenRel.getParameter(ComponentParam.NAME).setValue(Component.AVE_HORZ);
  }


 private void createRegion() {
	 //	make the Gridded Region object
//	  GriddedRegion eggr = 
//		  new GriddedRegion(
//				  MIN_LAT, MAX_LAT, MIN_LON,MAX_LON, GRID_SPACING);
	  GriddedRegion eggr = new GriddedRegion(
	    		new Location(MIN_LAT, MIN_LON),
	    		new Location(MAX_LAT, MAX_LON),
	    		GRID_SPACING, new Location(0,0));
	 sites = new SitesInGriddedRegion(eggr);
 }


  /**
   *  Function that must be implemented by all Listeners for
   *  ParameterChangeWarnEvents.
   *
   * @param  event  The Event which triggered this function call
   */
  public void parameterChangeWarning( ParameterChangeWarningEvent e ){

    String S =  " : parameterChangeWarning(): ";

    WarningParameter param = e.getWarningParameter();

    //System.out.println(b);
    param.setValueIgnoreWarning(e.getNewValue());
  }


}
