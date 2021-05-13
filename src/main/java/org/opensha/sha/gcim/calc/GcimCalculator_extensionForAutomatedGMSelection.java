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

package org.opensha.sha.gcim.calc;


import java.io.IOException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.calc.GaussianDistCalc;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.gcim.Utils;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;


/**
 * <p>Title: GcimCalculator </p>
 * <p>Description: This class constructs the generalised conditional intensity measure (GCIM)
 * distributions for a given IMj=imj value from the hazard curve based on the
 * input parameters imr, site and eqkRupforecast.     
 * 
 * <p>Copyright: Copyright (c) 2010</p>
 * <p>Company: </p>
 * @author Brendon Bradley
 * @date April 1, 2010
 * @version 1.0 - this is a work in progress - see GCIM_thingstodo
 */

public class GcimCalculator_extensionForAutomatedGMSelection {
//public class GcimCalculator extends UnicastRemoteObject
//implements GcimCalculatorAPI{
	//Debugging
	protected final static String C = "GcimCalculator";
	protected final static boolean D = false;
	
	private Map<TectonicRegionType, ScalarIMR> imrjMap;
	private double[][] pRup_IMj, epsilonIMj;
	private int numIMi = 0;
	private int currentIMi = -1;
	private ArrayList<HashMap<TectonicRegionType, ScalarIMR>> imiAttenRels;
	private ArrayList<String> imiTypes;
	private ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels;
	private double[][] imiArray, cdfIMi_IMjArray;
	private double[][][] mulnIMi_RupIMjArray, stdlnIMi_RupIMjArray;
	private double[][] rhoIMiIMk_IMj;
	private double[] zApprox;
	
	//stores the Gcim Results
	private String GcimResultsString = "";
	
	private boolean gcimComplete = false;
	
	private Site site;
	private AbstractERF eqkRupForecast;
	
	//public static final String OPENSHA_SERVLET_URL = ServletPrefs.OPENSHA_SERVLET_URL + "GcimPlotServlet";
	
	/**
	 * This no-arg constructor sets defaults
	 */
	public GcimCalculator_extensionForAutomatedGMSelection() {
//	public GcimCalculator()throws java.rmi.RemoteException {
		//Set defaults
		setApproxCDFvalues();
	};
	
	/**
	 * This method gets the contribution of each rupture in the ERF toward the probability of IML=iml
	 *
	 * @throws java.rmi.RemoteException
	 * @throws IOException
	 */
	public void getRuptureContributions(double iml, Site site,
			HashMap<TectonicRegionType, ScalarIMR> imrjMap, AbstractERF eqkRupForecast,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) throws java.rmi.RemoteException {
		
		//IMj the GCIM is to be conditioned on
		this.imrjMap = imrjMap;
		ScalarIMR firstIMRFromMap = TRTUtils.getFirstIMR(imrjMap);
		
		//Site and earthquake rupture forecast
		this.site = site;
		this.eqkRupForecast = eqkRupForecast;
		
		//Call DisaggregationCalculator twice for IMj = imj and imj + deltaimj
		DisaggregationCalculator disaggCalc = new DisaggregationCalculator();
		disaggCalc.setStoreRupProbEpsilons(true);
		disaggCalc.disaggregate(iml, site, imrjMap, eqkRupForecast, maxDist, magDistFilter);
		double disaggRupDetails1[][][] = disaggCalc.getRupProbEpsilons();
		double trate_imj = disaggCalc.getTotalRate();
		
		disaggCalc.disaggregate(iml * 1.01, site, imrjMap, eqkRupForecast, maxDist, magDistFilter);
		double disaggRupDetails2[][][] = disaggCalc.getRupProbEpsilons();
		double trate_imj2 = disaggCalc.getTotalRate();
		
		
		//get some initial details from the ERF and sanity check
		int numSources = eqkRupForecast.getNumSources();
		if (numSources != disaggRupDetails1.length || numSources != disaggRupDetails2.length)
			throw new IllegalArgumentException("Error: Num Sources != that from disagg calc");
		
		pRup_IMj = new double[numSources][];
		epsilonIMj = new double[numSources][];
		
		//compute the probability given IMj==iml (Equation 7 in Bradley)
		double dtrate_imj = trate_imj - trate_imj2;
		
		for (int i = 0; i < numSources; ++i) {
			int numRup = eqkRupForecast.getSource(i).getNumRuptures();
			pRup_IMj[i] = new double[numRup];
			epsilonIMj[i] = new double[numRup];
			if (disaggRupDetails1[i] == null) {
				for (int j=0; j<numRup; j++) {
					pRup_IMj[i][j] = 0;
					epsilonIMj[i][j] = 0;
				}
				continue;
			}
			for (int j = 0; j < numRup; j++) {
				double pRup_imj = disaggRupDetails1[i][j][0];
				double pRup_imj2 = disaggRupDetails2[i][j][0];
				pRup_IMj[i][j] = (pRup_imj * trate_imj - pRup_imj2 * trate_imj2)/dtrate_imj;
				epsilonIMj[i][j] = disaggRupDetails1[i][j][1];
			}
		}
	}

	/**
	 * this function obtains the GCIM distributions for multiple IMs by successively calling
	 * the getSingleGcim method
	 * Returns true if it was successfully else return false
	 *
	 * @param imri: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imCorrelationRelationship: selected correlation object for IMi and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getMultipleGcims(int numIMi,	
			ArrayList<HashMap<TectonicRegionType, ScalarIMR>> imiAttenRels,
			ArrayList<String> imiTypes, ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) {
		
		this.numIMi = numIMi;
		this.imiAttenRels = imiAttenRels;
		this.imiTypes = imiTypes;
		this.imijCorrRels = imijCorrRels;
		setGcimOutputDimensions();
		
		mulnIMi_RupIMjArray = new double [numIMi][][];
		stdlnIMi_RupIMjArray = new double [numIMi][][];
		
		for (int i=0; i<numIMi; i++) {
			this.currentIMi = i+1;  //For updating the progress bar
			
			//Get the IMi, AttenRel, CorrRel, (and period later if IMi is SA)
			HashMap<TectonicRegionType, ScalarIMR> imriMap = imiAttenRels.get(i);
			HashMap<TectonicRegionType, ImCorrelationRelationship> corrImijMap = imijCorrRels.get(i);
			
			//Calculate the GCIM distribution for the given IMi
			getSingleGcim(i, imriMap, corrImijMap, maxDist, magDistFilter);
			
			//now get the (lower triangular) correlation coefficeint between IMi and IMk given IMj 
			//i.e. rho_{lnIMi|IMj,lnIMk,IMj}
//			getIMiIMkCorrelation();
			
		}
		
		gcimComplete = true;
		return gcimComplete;
	}
	
	/**
	 * this function performs the GCIM computations to obtain the conditional distribution
	 * of a single input intensity measure.
	 * Returns true if it was successfully else return false
	 *
	 *@param imiNumber - the imi counter used for storing results in array
	 * @param imri: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imCorrelationRelationship: selected correlation object for IMi and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getSingleGcim(int imiNumber, HashMap<TectonicRegionType, ScalarIMR> imriMap,
			HashMap<TectonicRegionType, ImCorrelationRelationship> imijCorrRelMap,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) {	
		
		//Set the site in imri
		for (ScalarIMR imri:imriMap.values()) {
			imri.resetParameterEventListeners();
			imri.setUserMaxDistance(maxDist);
			imri.setSite(site);
		}
		
		//Get total number of sources
		int numSources = eqkRupForecast.getNumSources();
		
		double[][] mulnIMi_RupIMj = new double[numSources][];
		double[][] stdlnIMi_RupIMj = new double[numSources][];
		double mulnIMi_Rup, stdlnIMi_Rup, mulnIMi_IMj, varlnIMi_IMj, stdlnIMi_IMj, cdfIMi_RupIMj;
		
		mulnIMi_RupIMjArray[imiNumber] = new double[numSources][];
		stdlnIMi_RupIMjArray[imiNumber] = new double[numSources][];
		
		boolean includeMagDistFilter;
		if(magDistFilter == null ) includeMagDistFilter=false;
		else includeMagDistFilter=true;
		double magThresh=0.0;
			
		int numRupRejected =0;
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			// get source and all its details 
			ProbEqkSource source = eqkRupForecast.getSource(i);

			int numRuptures = eqkRupForecast.getNumRuptures(i);
			mulnIMi_RupIMj[i] = new double[numRuptures];
			stdlnIMi_RupIMj[i] = new double[numRuptures];
			
			mulnIMi_RupIMjArray[imiNumber][i] = new double[numRuptures];
			stdlnIMi_RupIMjArray[imiNumber][i] = new double[numRuptures];
			
			// check the distance of the source
			double distance = source.getMinDistance(site);
			if (distance > maxDist) {
				continue;
			}		

	        // set the IMR according to the tectonic region of the source (if there is more than one)
			TectonicRegionType trt = source.getTectonicRegionType();
			ScalarIMR imri = TRTUtils.getIMRforTRT(imriMap, trt);
			ImCorrelationRelationship imijCorrRel = Utils.getIMCorrRelForTRT(imijCorrRelMap, trt);
			
			//compute the correlation coefficient between lnIMi and lnIMj for the given source  
			double rho_lnIMilnIMj = imijCorrRel.getImCorrelation();
			
			// loop over ruptures
			for (int j = 0; j < numRuptures; j++) {
				
				ProbEqkRupture rupture = source.getRupture(j);
				
			     // apply magThreshold if we're to use the mag-dist cutoff filter
		        if(includeMagDistFilter && rupture.getMag() < magThresh) {
		        	numRupRejected+=1;
		        	continue;
		        }
				
				// set the rupture in the imr
				imri.setEqkRupture(rupture);

				// get the unconditional mean, stdDev of lnIMi for the given rupture
				mulnIMi_Rup = imri.getMean();
				stdlnIMi_Rup = imri.getStdDev();
				
				// get the conditional mean, stdDev of lnIMi for the given rupture
				mulnIMi_RupIMj[i][j] = mulnIMi_Rup + stdlnIMi_Rup * rho_lnIMilnIMj *epsilonIMj[i][j];
				stdlnIMi_RupIMj[i][j] = stdlnIMi_Rup * Math.sqrt(1-Math.pow(rho_lnIMilnIMj,2.0));
				
				//store in array
				mulnIMi_RupIMjArray [imiNumber][i][j] = mulnIMi_RupIMj[i][j];
				stdlnIMi_RupIMjArray [imiNumber][i][j] = stdlnIMi_RupIMj[i][j];
				
			}
		}
		
		
		
		//compute the conditional mean and variance of lnIMi (independent of Rup)
		mulnIMi_IMj=0; 
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			int numRuptures = eqkRupForecast.getNumRuptures(i);
			for (int j = 0; j < numRuptures; j++) {
				mulnIMi_IMj += mulnIMi_RupIMj[i][j] * pRup_IMj[i][j];
			}
		}
		
		varlnIMi_IMj=0;
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			int numRuptures = eqkRupForecast.getNumRuptures(i);
			for (int j = 0; j < numRuptures; j++) {
				varlnIMi_IMj += (Math.pow(stdlnIMi_RupIMj[i][j],2.0) + Math.pow(mulnIMi_RupIMj[i][j]-mulnIMi_IMj, 2.0) ) * pRup_IMj[i][j];
			}
		}
		stdlnIMi_IMj = Math.sqrt(varlnIMi_IMj);
		
		//Initially assuming that that lnIMi|IMj=imj is lognormal determine the range of 
		//IMi values to compute the CDF of lnIMi|IMj=imj for, then compute this cdf
		for (int n = 0; n < zApprox.length; n++) {
			imiArray[n][imiNumber] = Math.exp(mulnIMi_IMj + zApprox[n] * stdlnIMi_IMj);
			
			cdfIMi_IMjArray[n][imiNumber]=0.0;
			//loop over all of the ruptures
			for (int i = 0; i < numSources; i++) {
				int numRuptures = eqkRupForecast.getNumRuptures(i);
				for (int j = 0; j < numRuptures; j++) {
					double z = (Math.log(imiArray[n][imiNumber])-mulnIMi_RupIMj[i][j])/stdlnIMi_RupIMj[i][j];
					cdfIMi_RupIMj = GaussianDistCalc.getCDF(z);
					cdfIMi_IMjArray[n][imiNumber] += cdfIMi_RupIMj * pRup_IMj[i][j];
				}
			}
		}
		
		return true;
	}
	
	/**
	 * this function performs computes the linear correlation between IMi and IMk given IMj
	 * which is needed to form the full covariance/correlation matrix of IM|IMj
	 * Returns true if it was successfully else return false
	 *
	 *@param imiNumber - the imi counter used for storing results in array
	 *@param imkNumber - the imk counter used for storing results in array
	 * @param imriMap: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imijCorrRelMap: selected correlation object for IMi and IMj
	 * @param imrkMap: selected IMRk object (that for which the distribution is desired i.e. IMk)
	 * @param imkjCorrRelMap: selected correlation object for IMk and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getIMiIMk_IMj_Correlation(int imiNumber, int imkNumber, 
//			HashMap<TectonicRegionType, ScalarIntensityMeasureRelationshipAPI> imriMap,
			HashMap<TectonicRegionType, ImCorrelationRelationship> imijCorrRelMap,
//			HashMap<TectonicRegionType, ScalarIntensityMeasureRelationshipAPI> imrkMap,
			HashMap<TectonicRegionType, ImCorrelationRelationship> imkjCorrRelMap,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter
			) {	
		
		//Set the site in imri
//		for (ScalarIntensityMeasureRelationshipAPI imri:imriMap.values()) {
//			imri.resetParameterEventListeners();
//			imri.setUserMaxDistance(maxDist);
//			imri.setSite(site);
//		}
//		
//		//Set the site in imrk
//		for (ScalarIntensityMeasureRelationshipAPI imrk:imrkMap.values()) {
//			imrk.resetParameterEventListeners();
//			imrk.setUserMaxDistance(maxDist);
//			imrk.setSite(site);
//		}
		
		//Get total number of sources
		int numSources = eqkRupForecast.getNumSources();
		
//		double[][] mulnIMi_RupIMj = new double[numSources][];
//		double[][] stdlnIMi_RupIMj = new double[numSources][];
//		double mulnIMi_Rup, stdlnIMi_Rup, mulnIMi_IMj, varlnIMi_IMj, stdlnIMi_IMj, cdfIMi_RupIMj;
		
//		double[][] mulnIMk_RupIMj = new double[numSources][];
//		double[][] stdlnIMk_RupIMj = new double[numSources][];
//		double mulnIMk_Rup, stdlnIMk_Rup, mulnIMk_IMj, varlnIMk_IMj, stdlnIMk_IMj, cdfIMk_RupIMj;
		
		boolean includeMagDistFilter;
		if(magDistFilter == null ) includeMagDistFilter=false;
		else includeMagDistFilter=true;
		double magThresh=0.0;
			
		int numRupRejected =0;
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			// get source and all its details 
			ProbEqkSource source = eqkRupForecast.getSource(i);

			int numRuptures = eqkRupForecast.getNumRuptures(i);
//			mulnIMi_RupIMj[i] = new double[numRuptures];
//			stdlnIMi_RupIMj[i] = new double[numRuptures];
			
//			mulnIMk_RupIMj[i] = new double[numRuptures];
//			stdlnIMk_RupIMj[i] = new double[numRuptures];
			
			// check the distance of the source
			double distance = source.getMinDistance(site);
			if (distance > maxDist) {
				continue;
			}		

	        // set the IMR according to the tectonic region of the source (if there is more than one)
			TectonicRegionType trt = source.getTectonicRegionType();
//			ScalarIntensityMeasureRelationshipAPI imri = TRTUtils.getIMRForTRT(imriMap, trt);
			ImCorrelationRelationship imijCorrRel = Utils.getIMCorrRelForTRT(imijCorrRelMap, trt);
//			
//			ScalarIntensityMeasureRelationshipAPI imrk = TRTUtils.getIMRForTRT(imrkMap, trt);
			ImCorrelationRelationship imkjCorrRel = Utils.getIMCorrRelForTRT(imkjCorrRelMap, trt);
			
			//compute the correlation coefficient between lnIMi and lnIMj for the given source  
			double rho_lnIMilnIMj = imijCorrRel.getImCorrelation();
			
			//compute the correlation coefficient between lnIMk and lnIMj for the given source  
			double rho_lnIMklnIMj = imkjCorrRel.getImCorrelation();
			
			// loop over ruptures
			for (int j = 0; j < numRuptures; j++) {
				
				ProbEqkRupture rupture = source.getRupture(j);
				
			     // apply magThreshold if we're to use the mag-dist cutoff filter
		        if(includeMagDistFilter && rupture.getMag() < magThresh) {
		        	numRupRejected+=1;
		        	continue;
		        }
				
				// set the rupture in the imr
//				imri.setEqkRupture(rupture);
//				imrk.setEqkRupture(rupture);

				// get the unconditional mean, stdDev of lnIMi for the given rupture
//				mulnIMi_Rup = imri.getMean();
//				stdlnIMi_Rup = imri.getStdDev();
				
//				mulnIMk_Rup = imrk.getMean();
//				stdlnIMk_Rup = imrk.getStdDev();
				
				// get the conditional mean, stdDev of lnIMi for the given rupture
//				mulnIMi_RupIMj[i][j] = mulnIMi_Rup + stdlnIMi_Rup * rho_lnIMilnIMj *epsilonIMj[i][j];
//				stdlnIMi_RupIMj[i][j] = stdlnIMi_Rup * Math.sqrt(1-Math.pow(rho_lnIMilnIMj,2.0));
				
				// get the conditional mean, stdDev of lnIMk for the given rupture
//				mulnIMk_RupIMj[i][j] = mulnIMk_Rup + stdlnIMk_Rup * rho_lnIMklnIMj *epsilonIMj[i][j];
//				stdlnIMk_RupIMj[i][j] = stdlnIMk_Rup * Math.sqrt(1-Math.pow(rho_lnIMklnIMj,2.0));
		        
		        //compute the correlation between IMi and IMj given IMk
		        
			}
		}
		
		//compute the conditional mean and variance of lnIMi and lnIMk (independent of Rup)
//		mulnIMi_IMj=0;
//		mulnIMk_IMj=0; 
		
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			int numRuptures = eqkRupForecast.getNumRuptures(i);
			for (int j = 0; j < numRuptures; j++) {
//				mulnIMi_IMj += mulnIMi_RupIMj[i][j] * pRup_IMj[i][j];
			}
		}
		
//		varlnIMi_IMj=0;
		//loop over all of the ruptures
		for (int i = 0; i < numSources; i++) {
			int numRuptures = eqkRupForecast.getNumRuptures(i);
			for (int j = 0; j < numRuptures; j++) {
//				varlnIMi_IMj += (Math.pow(stdlnIMi_RupIMj[i][j],2.0) + Math.pow(mulnIMi_RupIMj[i][j]-mulnIMi_IMj, 2.0) ) * pRup_IMj[i][j];
			}
		}
//		stdlnIMi_IMj = Math.sqrt(varlnIMi_IMj);
		
		rhoIMiIMk_IMj[imiNumber][imkNumber]= 0.0;
		return true;
		
	}
	
	/**
	 * This method sets the approximate CDF values for which the gcim distributions are calculated for
	 * @param zmin - the minimum z (normalised CDF value) to compute gcim for
	 * @param zmax - the maximum z (normalised CDF value) to compute gcim for
	 * @param dz -   the increment of z (normalised CDF value) which determines the number of points
	 */
	public void setApproxCDFvalues(double zmin, double zmax, double dz) {
		int num_z = (int) Math.round((zmax-zmin)/(dz)+1);
		zApprox = new double[num_z];
		for (int i = 0; i < num_z; i++) {
			this.zApprox[i] = zmin + i * dz;
		}
	}
	/**
	 * This method sets the approximate CDF values for which the gcim distributions are calculated for
	 * using defaults zmin=-3, zmax=3, and dz=0.2 which gives 51 points
	 */
	public void setApproxCDFvalues() {
		setApproxCDFvalues(-3.0,3.0,0.2);
	}
	/**
	 * This method gets the Approx CDFvalues
	 */
	public double[] getApproxCDFvalues() {
		return zApprox;
	}
	/**
	 * This method sets the dimensions of the imiArray and cdfIMi_IMjArrays
	 */
	public void setGcimOutputDimensions() {
		imiArray = new double[zApprox.length][numIMi];
		cdfIMi_IMjArray = new double[zApprox.length][numIMi];
	}
	
	/**
	 * This methods makes the GCIM results in a string for output to either console or external window
	 */
	public String getGcimResultsString() {
		
		for (int i=0; i<numIMi; i++) {
			
			HashMap<TectonicRegionType, ScalarIMR> imriMap = imiAttenRels.get(i);
			ScalarIMR firstIMRiFromMap = TRTUtils.getFirstIMR(imriMap);
			
			GcimResultsString += "----------------------" + "\n";
			//Get the IM name for printing
			if (firstIMRiFromMap.getIntensityMeasure().getName()==SA_Param.NAME) {
				GcimResultsString += "Results for SA period: " + 
						((SA_Param) firstIMRiFromMap.getIntensityMeasure()).getPeriodParam().getValue() + "\n";
			}
			else if (firstIMRiFromMap.getIntensityMeasure().getName()==SA_InterpolatedParam.NAME) {
				GcimResultsString += "Results for SA Interpolated period: " + 
						((SA_InterpolatedParam) firstIMRiFromMap.getIntensityMeasure()).getPeriodInterpolatedParam().getValue() + "\n";
			}
			else {
				GcimResultsString += "Results for " + firstIMRiFromMap.getIntensityMeasure().getName() + "\n";
			}
			for (int j=0; j<zApprox.length; j++) {
				GcimResultsString += imiArray[j][i] + "\t" + cdfIMi_IMjArray[j][i] + "\n";
			}
			GcimResultsString += "----------------------" + "\n";
			GcimResultsString += "\n";
		}
		return GcimResultsString;
	}
	
	// temp method to print results to screen
	public void printResultsToConsole() {
		System.out.println(GcimResultsString);
	}
	
	private static void setSAPeriodInIMR(ScalarIMR imr, double period) {
		((Parameter<Double>)imr.getIntensityMeasure())
		.getIndependentParameter(PeriodParam.NAME).setValue(new Double(period));
	}
	
	/**
	 * gets the number of current IMi being processed
	 * @return
	 */
	public int getCurrIMi() throws java.rmi.RemoteException{
		return this.currentIMi;
	}

	/**
	 * gets the total number of IMi's
	 * @return
	 */
	public int getTotIMi() throws java.rmi.RemoteException{
		return this.numIMi;
	}
	
	/**
	 * Checks to see if GCIM calculation for the selected site
	 * have been completed.
	 * @return
	 */
	public boolean done() throws java.rmi.RemoteException{
		return gcimComplete;
	}
	
	
}
