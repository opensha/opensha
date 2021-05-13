/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.calc;

import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.region.SitesInGriddedRegion;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.params.PtSrcDistanceCorrectionParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.gui.servlets.ScenarioShakeMapCalcServlet;
import org.opensha.sha.imr.AttenuationRelationship;


/**
 * <p>Title: ScenarioShakeMapCalculator</p>
 * <p>Description: This class calculates the Scenario Shake Map Data using the
 * based on the PropagationEffectParam input parameters imr, site and eqkRupforecast</p>
 * @author : Nitin Gupta
 * @created May 19,2004
 * @version 1.0
 */

public class ScenarioShakeMapCalculator {

	protected final static String C = "ScenarioShakeMapCalculator";
	protected final static boolean D = false;

	//stores the number of sites
	private int numSites;
	//gets the current site being processed
	private int currentSiteBeingProcessed;

	//the propagation effect object
	private FileWriter fw ;
	private DecimalFormat locFormat = new DecimalFormat("0.000000");
	
	// This tell what type of point-source distance correction to apply
	private PtSrcDistanceCorrectionParam ptSrcDistCorrParam;
	
	// adjustable parameters for this calculator
	private ParameterList adjustableParams;

	//class default constructor
	public ScenarioShakeMapCalculator() {
		ptSrcDistCorrParam = new PtSrcDistanceCorrectionParam();
		adjustableParams = new ParameterList();
		adjustableParams.addParameter(ptSrcDistCorrParam);
	}


	/**
	 * Does the ScenarioShakeMap data calculation on the user's local system.
	 * This function computes a Scenario ShakeMap Data for the given Region, IMR, and ERF.
	 * The computed  data in the form of X, Y and Z is place XYZ_DataSetAPI object.
	 * @param selectedAttenRels : ArrayList for the selected AttenuationRelationships
	 * @param attenRelWts : Relative Wts for all the selected AttenuationRelationship models.
	 * @param griddedRegionSites : Gridded Region Object
	 * @param rupture : selected EarthquakeRupture Object.
	 * @param isProbAtIML : if true the prob at the specified IML value (next param) will
	 * be computed; if false the IML at the specified Prob value (next param) will be computed.
	 * @param value : the IML or Prob to compute the map for.
	 * @return the XYZ_DataSetAPI  : ArbDiscretized XYZ dataset
	 */
	public GeoDataSet getScenarioShakeMapData(List<AttenuationRelationship> selectedAttenRels,
			List<Double> attenRelWts,
			Iterable<Site> sites,
			EqkRupture rupture,
			boolean isProbAtIML,double value) throws ParameterException {

		//numSites = sites.getRegion().getNodeCount();
		
		// set point-source distance correction type & mag if it's a pointSurface
		if(rupture.getRuptureSurface() instanceof PointSurface)
			((PointSurface)rupture.getRuptureSurface()).setDistCorrMagAndType(rupture.getMag(), ptSrcDistCorrParam.getValueAsTypePtSrcDistCorr());

		//instance of the XYZ dataSet.
		GeoDataSet xyzDataSet =null;

		// get the selected attenuationRelation array size.
		int size = selectedAttenRels.size();

		if(D){
			try {
				fw = new FileWriter("Test_PGV_Params_Settings.txt");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		/**
		 * Based on the selected IML@prob or Prob@IML the corresponding value is
		 * set in the selected IMR's
		 */
		for(int i=0;i<size;++i){ //iterate over all the selected AttenuationRelationships
			AttenuationRelationship attenRel = selectedAttenRels.get(i);
			if(D){
				try {
					fw.write("Lat"+"\t"+"Lon"+"\t");
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				ListIterator it = attenRel.getIML_AtExceedProbIndependentParamsIterator();
				while(it.hasNext()){
					Parameter param = (Parameter)it.next();
					try {
						fw.write(param.getName()+"\t");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				for (Parameter<?> param : attenRel.getIntensityMeasure().getIndependentParameterList()) {
					try {
						fw.write(param.getName()+"\t");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					fw.write("Mean(ln)"+"\t"+"Std Dev.\n");
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			//resetting the Parameter change Listeners on the AttenuationRelationship
			//parameters. This allows the Server version of our application to listen to the
			//parameter changes.
			attenRel.resetParameterEventListeners();

			if(isProbAtIML) //if Prob@IML set the Intensity Measure Level
				attenRel.setIntensityMeasureLevel(new Double(value));
			else{
				try{ //if IML@Prob set the Exceed Prob param for the Attenuation.
					attenRel.setExceedProb(value);
				}catch(ParameterException e){
					throw new ParameterException(e.getMessage());
				}
			}
		}

		//store the sum of the averaged value of all the selected AttenRel
		double attenRelsAvgValForSite = 0.0;
		//iterating over all the sites and averaging the values for all AttenRels
		xyzDataSet = new ArbDiscrGeoDataSet(true);
		currentSiteBeingProcessed = 0;
		for (Site site : sites) {
			//saves the number of the current site being processed
			currentSiteBeingProcessed++;
			//for each site initializing it to 0.0
			attenRelsAvgValForSite = 0.0;
			//getting one site at a time
			//Site site = sites.getSite(k);

			/*
      // write out useful info
      System.out.print((float)site.getLocation().getLatitude()+"\t"+
                       (float)site.getLocation().getLongitude()+"\t"+
                       ((Double)site.getParameter(Vs30_Param.NAME).getValue()).floatValue()+"\t"+
                       site.getParameter(AS_1997_AttenRel.SITE_TYPE_NAME).getValue()+"\t"+
                       site.getParameter(SCEMY_1997_AttenRel.SITE_TYPE_NAME).getValue()+"\t"+
                       site.getParameter(CB_2003_AttenRel.SITE_TYPE_NAME).getValue()+"\t"+
                       site.getParameter(ShakeMap_2003_AttenRel.WILLS_SITE_NAME).getValue()+"\t"+
                       ((Double)site.getParameter(Field_2000_AttenRel.BASIN_DEPTH_NAME).getValue()).floatValue()+"\t"+
                       rupture.getRuptureSurface().getDistanceRup(site.getLocation())+"\t"+
                       rupture.getRuptureSurface().getDistanceSeis(site.getLocation())+"\t"+
                       rupture.getRuptureSurface().getDistanceJB(site.getLocation())+"\n");
			 */
			//iterating overe all the selected attenautionRelationShips and getting the XYZ data for them
			for(int i=0;i<size;++i){
				AttenuationRelationship attenRel = selectedAttenRels.get(i);
				//getting the calculated value for the scenarioshakemap for the i-th attenRel and for this site.
				double val= scenarioShakeMapDataCalc(rupture,site,attenRel,isProbAtIML);

				//multiplying the value for the attenuation with the relative normalised wt for it
				val *= (attenRelWts.get(i)).doubleValue();
				//adding up all the values obtained from different AttenRel for the site.
				attenRelsAvgValForSite +=val;
			}
			xyzDataSet.set(site.getLocation(), attenRelsAvgValForSite);
		}
		if(D){
			try {
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//updating the Z Values for the XYZ data after averaging the values for all selected attenuations.
		return xyzDataSet;
	}



	/**
	 * Does the ScenarioShakeMap data calculation on the server.
	 * This function computes a Scenario ShakeMap Data for the given Region, IMR, and ERF.
	 * The computed  data in the form of X, Y and Z is place XYZ_DataSetAPI object.
	 * The computation is performed by the server to save the processor memory.
	 * It opens the connection with the servlet hosted on gravity.usc.edu , which does the
	 * calculation  for the it and return back the result to it.
	 * @param selectedAttenRels : ArrayList for the selected AttenuationRelationships
	 * @param attenRelWts : Relative Wts for all the selected AttenuationRelationship models.
	 * @param griddedRegionSites : Gridded Region Object
	 * @param rupture : selected EarthquakeRupture Object.
	 * @param isProbAtIML : if true the prob at the specified IML value (next param) will
	 * be computed; if false the IML at the specified Prob value (next param) will be computed.
	 * @param value : the IML or Prob to compute the map for.
	 * @return the String  : Absolute path to ArbDiscretized XYZ dataset file on the server
	 */
	public String getScenarioShakeMapDataUsingServer(ArrayList selectedAttenRels, ArrayList attenRelWts,
			String griddedRegionSitesFile,EqkRupture rupture,
			boolean isProbAtIML,double value, String selectedIMT) throws ParameterException {
		
		// set point-source distance correction type & mag if it's a pointSurface
		if(rupture.getRuptureSurface() instanceof PointSurface)
			((PointSurface)rupture.getRuptureSurface()).setDistCorrMagAndType(rupture.getMag(), ptSrcDistCorrParam.getValueAsTypePtSrcDistCorr());
		
		ObjectOutputStream outputToServlet = null;
		ObjectInputStream inputToServlet = null;
		try{

			if(D) System.out.println("starting to make connection with servlet");
			URL scenarioshakeMapCalcServlet = new URL(ScenarioShakeMapCalcServlet.SERVLET_URL);


			URLConnection servletConnection = scenarioshakeMapCalcServlet.openConnection();
			if(D) System.out.println("connection established");

			// inform the connection that we will send output and accept input
			servletConnection.setDoInput(true);
			servletConnection.setDoOutput(true);

			// Don't use a cached version of URL connection.
			servletConnection.setUseCaches (false);
			servletConnection.setDefaultUseCaches (false);
			// Specify the content type that we will send binary data
			servletConnection.setRequestProperty ("Content-Type","application/octet-stream");

			outputToServlet = new
			ObjectOutputStream(servletConnection.getOutputStream());


			//sending the ArrayList of the selected AttenuationRelationships
			outputToServlet.writeObject(selectedAttenRels);


			//sending the Absolute weights of the selected AttenRel to the servlet
			outputToServlet.writeObject(attenRelWts);

			//sending the full path to the griddedregion file to the server
			outputToServlet.writeObject(griddedRegionSitesFile);

			//sending the EqkRupture object ( rupture info).
			outputToServlet.writeObject(rupture);

			//sending the Map type ot the servlet, is it Prob@IML or IML@Prob
			outputToServlet.writeObject(new Boolean(isProbAtIML));


			//sending the value of the iml or prob whichever other needs to be computed
			//based on the selection of the IML@prob or Prob@IML
			outputToServlet.writeObject(new Double(value));

			//sending the selected IMT to the server
			outputToServlet.writeObject(selectedIMT);

			// Receive the "actual webaddress of all the gmt related files"
			// from the servlet after it has received all the data
			inputToServlet = new
			ObjectInputStream(servletConnection.getInputStream());

			//Absolute path to the XYZ data for the scenarioshake as computed using the servlet
			String xyzDataFile =(String)inputToServlet.readObject();
			if(xyzDataFile.startsWith("Error"))
				throw new RuntimeException(xyzDataFile.substring(6));
			//if(D) System.out.println("Receiving the Input from the Servlet:"+webaddr);
			inputToServlet.close();
			return xyzDataFile;
		}catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Server is down , please try again later");
		}finally{
			try{
				if(outputToServlet !=null){
					outputToServlet.flush();
					outputToServlet.close();
				}
				if(inputToServlet !=null){
					inputToServlet.close();
				}
			}catch(IOException e){
				e.printStackTrace();
				throw new RuntimeException("Server is down , please try again later");
			}
		}
	}

	/**
	 *
	 * @param propagationEffect : Propagation Effect comtaining the site and rupture information.
	 * @param imr selected IMR object.
	 * @param isProbAtIML : if true the prob at the specified IML value (next param) will
	 * be computed; if false the IML at the specified Prob value will be computed.
	 * @return computed value for the exceed Prob or IML based on above argument.
	 * @throws ParameterException
	 */
	private double scenarioShakeMapDataCalc(EqkRupture rupture, Site site,
			AttenuationRelationship imr,boolean isProbAtIML) throws ParameterException {

		imr.setEqkRupture(rupture);
		imr.setSite(site);
		if(D) {
			try {
				Location loc = imr.getSite().getLocation();
				fw.write(locFormat.format(loc.getLatitude())+"\t"+locFormat.format(loc.getLongitude())+"\t");
				ListIterator it = imr.getIML_AtExceedProbIndependentParamsIterator();
				while(it.hasNext()){
					Parameter param = (Parameter)it.next();
					try {
						fw.write(param.getValue()+"\t");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				for (Parameter<?> param : imr.getIntensityMeasure().getIndependentParameterList()) {
					try {
						fw.write(param.getValue()+"\t");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					fw.write(imr.getMean()+"\t"+imr.getStdDev()+"\n");
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(isProbAtIML)
			return imr.getExceedProbability();
		else{
			try{
				return imr.getIML_AtExceedProb();
			}catch(ParameterException e){
				throw new ParameterException(e.getMessage());
			}
		}
	}




	/**
	 *
	 * @return the total number of sites in the region
	 */
	public int getNumSites(){
		return numSites;
	}


	/**
	 *
	 * @return the current site being processed
	 */
	public int getCurrentSite(){
		return currentSiteBeingProcessed ;
	}

	/**
	 *
	 * @return true if calculations for all the sites are done
	 * else return false.
	 */
	public boolean done(){
		if(currentSiteBeingProcessed == numSites)
			return true;
		return false;
	}

	/**
	 *
	 * @return the list of adjustable parameters.
	 */
	public ParameterList getAdjustableParams(){
		return adjustableParams;
	}

}
