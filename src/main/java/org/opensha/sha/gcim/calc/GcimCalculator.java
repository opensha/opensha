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

import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.NormalizedRandomGenerator;
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
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

//import Jama.CholeskyDecomposition; //This JAMA one doesnt work!  
import Jama.Matrix;

import java.util.Random;


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

public class GcimCalculator {
//public class GcimCalculator extends UnicastRemoteObject
//implements GcimCalculatorAPI{
	//Debugging
	protected final static String C = "GcimCalculator";
	protected final static boolean D = true;
	
	private Map<TectonicRegionType, ScalarIMR> imrjMap;
	private double[][] pRup_IMj, rupCdf, epsilonIMj;
	private double[] sourceCdf;
	private double[][] randIMiRealizations, randIMiRealizationStdevs;
	private int randSourceId, randRupId;
	private int numIMi = 0, num_z, numGcimRealizations;
	private int currentIMi = -1;
	private ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> imiAttenRels;
	private ArrayList<String> imiTypes;
	private ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels;
	private double[][] imiArray, cdfIMi_IMjArray;
	private double[] zApprox;
	//Frobenius Norm if modification of the correlation matrix was required
	private boolean corrMatrixPD = true;
	private double normFrob;
	private String corrMatrixNotPDString, corrMatrixPDString;
	
	//stores the Gcim Results
	private String GcimResultsString = "";
	
	private boolean gcimComplete = false;
	private boolean gcimRealizationsComplete = false;
	
	private Site site;
	private AbstractERF eqkRupForecast;
	
	//public static final String OPENSHA_SERVLET_URL = ServletPrefs.OPENSHA_SERVLET_URL + "GcimPlotServlet";
	
	/**
	 * This no-arg constructor sets defaults
	 */
	public GcimCalculator() {
//	public GcimCalculator()throws java.rmi.RemoteException {
		//Set defaults
		setApproxCDFvalues();
	};
	
	/**
	 * This method gets the contribution of each rupture in the ERF toward the probability of IML=iml.  
	 * It also creates a CDF for the sources and ruptures which is later used in simulating source/rups
	 * from the deaggregation results
	 *
	 * @throws java.rmi.RemoteException
	 * @throws IOException
	 */
	public void getRuptureContributions(double iml, Site site,
			Map<TectonicRegionType, ScalarIMR> imrjMap, AbstractERF eqkRupForecast,
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
		
		//Setup the CDF arrays
		rupCdf = new double[numSources][];
		sourceCdf = new double[numSources];
		
		//compute the probability given IMj==iml (Equation 7 in Bradley)
		double dtrate_imj = trate_imj - trate_imj2;
		
		double cumProb = 0.0;
		
		for (int i = 0; i < numSources; ++i) {
			int numRup = eqkRupForecast.getSource(i).getNumRuptures();
			pRup_IMj[i] = new double[numRup];
			epsilonIMj[i] = new double[numRup];
			rupCdf[i] = new double[numRup];
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
				
				cumProb = cumProb + pRup_IMj[i][j];
				rupCdf[i][j] = cumProb;
			}
			//Now assign the cumulative prob to the source CDF
			sourceCdf[i] = cumProb;
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
			ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> imiAttenRels,
			ArrayList<String> imiTypes, ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) {
		
		this.numIMi = numIMi;
		this.imiAttenRels = imiAttenRels;
		this.imiTypes = imiTypes;
		this.imijCorrRels = imijCorrRels;
		setGcimOutputDimensions();
		
		for (int i=0; i<numIMi; i++) {
			this.currentIMi = i+1;  //For updating the progress bar
			
			//Get the IMi, AttenRel, CorrRel, (and period later if IMi is SA)
			Map<TectonicRegionType, ScalarIMR> imriMap = imiAttenRels.get(i);
			Map<TectonicRegionType, ImCorrelationRelationship> corrImijMap = imijCorrRels.get(i);
			
			//Calculate the GCIM distribution for the given IMi
			getSingleGcim(i, imriMap, corrImijMap, maxDist, magDistFilter);
			
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
	public boolean getSingleGcim(int imiNumber, Map<TectonicRegionType, ScalarIMR> imriMap,
			Map<TectonicRegionType, ImCorrelationRelationship> imijCorrRelMap,
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
		
		boolean includeMagDistFilter;
		if(magDistFilter == null ) includeMagDistFilter=false;
		else includeMagDistFilter=true;
		double magThresh=0.0;
			
		int numRupRejected =0;
		//loop over all of the sources
		for (int i = 0; i < numSources; i++) {
			// get source and all its details 
			ProbEqkSource source = eqkRupForecast.getSource(i);

			int numRuptures = eqkRupForecast.getNumRuptures(i);
			mulnIMi_RupIMj[i] = new double[numRuptures];
			stdlnIMi_RupIMj[i] = new double[numRuptures];
			
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
	 * this function obtains realizations of the IMi values from the GCIM distributions.  
	 * The primary purpose of this method is to output random realizations of the IMi values 
	 * which can then be used to select ground motion records for seismic response analysis
	 * Returns true if it was successfully else return false
	 *
	 *@param numGcimRealizations: the number of random realizations of the IMi vector to compute
	 * @param imri: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imCorrelationRelationship: selected correlation object for IMi and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getGcimRealizations(int numGcimRealizations, int numIMi,	
			ArrayList<? extends Map<TectonicRegionType, ScalarIMR>> imiAttenRels,
			ArrayList<String> imiTypes, ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels,
			ArrayList<? extends Map<TectonicRegionType, ImCorrelationRelationship>> imikCorrRels, 
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) {
		
		this.numGcimRealizations = numGcimRealizations;
		randIMiRealizations = new double[numIMi][numGcimRealizations];
		randIMiRealizationStdevs = new double[numIMi][numGcimRealizations];
		
		//Now loop over the number of different realizations desired
		for (int m=0; m<numGcimRealizations; m++) {
			
			//Get a random earthquake source and rupture
			boolean randSourceRupUpdated = getRandomSourceRupture(); 
			ProbEqkSource source = eqkRupForecast.getSource(randSourceId);
			ProbEqkRupture rupture = source.getRupture(randRupId);
			
			//For the given rupture obtain the conditional mean and variance of all of the IMis			
			double[] mulnIMi_RandRupIMj = new double[numIMi];
			double[] stdlnIMi_RandRupIMj = new double[numIMi];
			double[] rho_lnIMilnIMj = new double[numIMi];
			double[][] rho_lnIMilnIMk_lnIMj = new double[numIMi][numIMi]; 
			
			double[] mulnIMi_RandRup = new double [numIMi];
			double[] stdlnIMi_RandRup = new double [numIMi];
			
			for (int i=0; i<numIMi; i++) {
				
				//Get the IMi, AttenRel, CorrRel, (and period later if IMi is SA)
				Map<TectonicRegionType, ScalarIMR> imriMap = imiAttenRels.get(i);
				Map<TectonicRegionType, ImCorrelationRelationship> corrImijMap = imijCorrRels.get(i);
				
				//Set the site in imri
				for (ScalarIMR imri:imriMap.values()) {
					imri.resetParameterEventListeners();
					imri.setUserMaxDistance(maxDist);
					imri.setSite(site);
				}		

			    // set the IMR according to the tectonic region of the source (if there is more than one)
				TectonicRegionType trt = source.getTectonicRegionType();
				ScalarIMR imri = TRTUtils.getIMRforTRT(imriMap, trt);
				ImCorrelationRelationship imijCorrRel = Utils.getIMCorrRelForTRT(corrImijMap, trt);
					
				//compute the correlation coefficient between lnIMi and lnIMj for the given source  
				rho_lnIMilnIMj[i] = imijCorrRel.getImCorrelation();
							
				// set the rupture in the imr
				imri.setEqkRupture(rupture);

				// get the unconditional mean, stdDev of lnIMi for the given rupture
				mulnIMi_RandRup[i] = imri.getMean();
				stdlnIMi_RandRup[i] = imri.getStdDev();
						
				// get the conditional mean, stdDev of lnIMi for the given rupture
				mulnIMi_RandRupIMj[i] = mulnIMi_RandRup[i] + stdlnIMi_RandRup[i] * rho_lnIMilnIMj[i] *epsilonIMj[randSourceId][randRupId];
				stdlnIMi_RandRupIMj[i] = stdlnIMi_RandRup[i] * Math.sqrt(1-Math.pow(rho_lnIMilnIMj[i],2.0));
				
				//Now setup the covariance matrix between the different IMi's
				rho_lnIMilnIMk_lnIMj[i][i]=1.0;
				for (int k=0; k<i; k++) {
					 //Determine the (unconditional) correlation between lnIMi and lnIMk 
					int corrImikIndex = (i)*(i-1)/2+k;
					Map<TectonicRegionType, ImCorrelationRelationship> corrImikMap = imikCorrRels.get(corrImikIndex);
					//Get the correlation relation associated with the TRT
					ImCorrelationRelationship imikCorrRel = Utils.getIMCorrRelForTRT(corrImikMap, trt);
					//compute the correlation coefficient between lnIMi and lnIMk for the given source  
					double rho_lnIMilnIMk = imikCorrRel.getImCorrelation();
					//Now get the conditional correlation
					rho_lnIMilnIMk_lnIMj[i][k]=(rho_lnIMilnIMk-rho_lnIMilnIMj[i]*rho_lnIMilnIMj[k])/Math.sqrt((1.0-Math.pow(rho_lnIMilnIMj[i],2))*(1.0-Math.pow(rho_lnIMilnIMj[k],2)));
					//Then as conditional correlation matrix is symmetric
					rho_lnIMilnIMk_lnIMj[k][i]=rho_lnIMilnIMk_lnIMj[i][k];
				}
			}
			//determine the cholesky decomposition of the correlation matrix			
			Matrix rho_lnIMilnIMk_lnIMj_matrix = new Matrix(rho_lnIMilnIMk_lnIMj);
			CholeskyDecomposition cholDecomp = new CholeskyDecomposition(rho_lnIMilnIMk_lnIMj_matrix);
//			CholeskyDecomposition cholDecomp = rho_lnIMilnIMk_lnIMj_matrix.chol(); //This JAMA .chol doesnt work well
			
			//Check if the matrix is PD then get L
			Matrix L_matrix;
			if (cholDecomp.isSPD()) {
				L_matrix = cholDecomp.getL();
			} else {
				corrMatrixPD = false;
				corrMatrixNotPDString = getMatrixAsString(rho_lnIMilnIMk_lnIMj_matrix);
				if (D) {
					System.out.println("The corr matrix below is not PD");
					rho_lnIMilnIMk_lnIMj_matrix.print(10,4);
				}
				//First compute the nearest PD matrix
				NearPD nearPd = new NearPD();
				nearPd.setKeepDiag(true);
//				nearPd.setEigTol(1.e-6);
				boolean success = nearPd.calcNearPD(rho_lnIMilnIMk_lnIMj_matrix);
				normFrob = nearPd.getFrobNorm();
				if (!success) {
					throw new RuntimeException("Error: nearPD failed to converge, the correlation matrix maybe" +
							" significantly different from a PD matrix, check that the correlation equations" +
							"used are reasonable");
				}
				
				Matrix rho_lnIMilnIMk_lnIMj_PDmatrix = nearPd.getX();
				corrMatrixPDString = getMatrixAsString(rho_lnIMilnIMk_lnIMj_PDmatrix);
				if (D) {
					System.out.println("This is the nearest PD matrix to corr matrix");
					rho_lnIMilnIMk_lnIMj_PDmatrix.print(10,6);
				}
				//Now get the CholDecomp of this nearest matrix
				CholeskyDecomposition cholDecompPD = new CholeskyDecomposition(rho_lnIMilnIMk_lnIMj_PDmatrix);
//				CholeskyDecomposition cholDecompPD = rho_lnIMilnIMk_lnIMj_PDmatrix.chol(); //THis JAMA one doesnt work
				if (cholDecompPD.isSPD()) {
					L_matrix = cholDecompPD.getL();
				} else {
					throw new RuntimeException("Error: Even after NearPD the matrix is not PD");
				}
			}
				
//			L = cholesky(rho_lnIMilnIMk_lnIMj);

			//compute an uncorrelated array of standard normal random variables
//			Random generator = new Random();
//			double[] randArray = new double[numIMi];
//			for (int i=0; i<numIMi; i++) {
//				randArray[i] = generator.nextGaussian();
//			}
			Random generator = new Random();
			double[][] randArray = new double[numIMi][1];
			for (int i=0; i<numIMi; i++) {
				randArray[i][0] = generator.nextGaussian();
			}
			Matrix randArray_matrix = new Matrix(randArray);
			
			//multiply by the cholesky decomposition to get correlation random variables
			Matrix corrRandArray_matrix = L_matrix.times(randArray_matrix);
//			double[] corrRandArray = matrixVectorMultiplication(L, randArray);
			
			//Compute the random realization of IMi
			for (int i=0;i<numIMi; i++) {
				randIMiRealizations[i][m]=Math.exp(mulnIMi_RandRupIMj[i]+stdlnIMi_RandRupIMj[i]*corrRandArray_matrix.get(i,0));
//				randIMiRealizations[i][m]=Math.exp(mulnIMi_RandRupIMj[i]+stdlnIMi_RandRupIMj[i]*corrRandArray[i]);
				randIMiRealizationStdevs[i][m]=stdlnIMi_RandRupIMj[i];
			}
		}
		
		
		gcimRealizationsComplete = true;
		return gcimRealizationsComplete;
	}
	
	/**
	 * This method obtains a random earthquake rupture (from a correspondingly random source), based 
	 * on the empirical CDF of the deaggregation results
	 */
	public boolean getRandomSourceRupture() {
		//success flag
		boolean randomSourceRupUpdated = false;
		//get random number
		double randVal = Math.random();
		//Determine the corresponding random source
		int numSources = eqkRupForecast.getNumSources();
		for (int i = 0; i < numSources; ++i) {
			if (sourceCdf[i] > randVal) {
				this.randSourceId = i;
				break;
			}
		}
		//Determine the corresponding random rupture
		int randRupId = 0;
		int numRup = eqkRupForecast.getSource(randSourceId).getNumRuptures();
		for (int j = 0; j < numRup; ++j) {
			if (rupCdf[randSourceId][j] > randVal) {
				this.randRupId = j;
				randomSourceRupUpdated = true;
				break;
			}
		}
		return randomSourceRupUpdated;
	}
	
	/**
	 * This method sets the approximate CDF values for which the gcim distributions are calculated for
	 * @param zmin - the minimum z (normalised CDF value) to compute gcim for
	 * @param zmax - the maximum z (normalised CDF value) to compute gcim for
	 * @param dz -   the increment of z (normalised CDF value) which determines the number of points
	 */
	public void setApproxCDFvalues(double zmin, double zmax, double dz) {
		num_z = (int) Math.round((zmax-zmin)/(dz)+1);
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
		
		//First put the number of Imis and z values
		GcimResultsString += "Number of IMi's considered: " + numIMi + "\n";
		GcimResultsString += "Number of z values for each IMi: " + num_z + "\n";
		GcimResultsString += "Number of GCIM realizations: " + numGcimRealizations + "\n";
		GcimResultsString += "--------------------------------------------------" + "\n";
		GcimResultsString += " IMi value     Cumulative Probability, F(IMi|IMj) \n";
		
		String[] IMiNames = new String[numIMi];
		for (int i=0; i<numIMi; i++) {
			
			Map<TectonicRegionType, ScalarIMR> imriMap = imiAttenRels.get(i);
			ScalarIMR firstIMRiFromMap = TRTUtils.getFirstIMR(imriMap);
			
			GcimResultsString += "--------------------------------------------------" + "\n";
			//Get the IM name for printing
			if (firstIMRiFromMap.getIntensityMeasure().getName()==SA_Param.NAME) {
				IMiNames[i]= "SA (" + ((SA_Param) firstIMRiFromMap.getIntensityMeasure()).getPeriodParam().getValue() + "s)";
			}
			else if (firstIMRiFromMap.getIntensityMeasure().getName()==SA_InterpolatedParam.NAME) {
				IMiNames[i]= "SA (" + ((SA_InterpolatedParam) firstIMRiFromMap.getIntensityMeasure()).getPeriodInterpolatedParam().getValue() + "s)";
			}
			else {
				IMiNames[i] = firstIMRiFromMap.getIntensityMeasure().getName();
			}
			GcimResultsString += "Results for " + IMiNames[i] + "\n";
			
			for (int j=0; j<zApprox.length; j++) {
				GcimResultsString += imiArray[j][i] + "\t" + cdfIMi_IMjArray[j][i] + "\n";
			}
			GcimResultsString += "--------------------------------------------------" + "\n";
			GcimResultsString += "\n";
		}
		
		//Now write out the IMi realizations
		//First create the header which contains the IMi names and the standard deviations
		if (numGcimRealizations>0) {
			GcimResultsString += "Realization";
			for (int i=0;i<numIMi;i++) {
				GcimResultsString += "  " + IMiNames[i] + "           ";
			}
			GcimResultsString += "\n   ";
			for (int i=0;i<numIMi;i++) {
				GcimResultsString += "|     IMi Val     |    Cond. Stddev     ";
			}
			GcimResultsString += "\n";
			//Now for each of the realizations add the results to the string
			for (int m=0;m<numGcimRealizations; m++) {
				GcimResultsString += " " + (m+1) + " "; //The realization number
				for (int i=0; i<numIMi; i++) {
					GcimResultsString += " " + randIMiRealizations[i][m] + " " + randIMiRealizationStdevs[i][m] + " ";
				}
				GcimResultsString += "\n";
			}
		}
		GcimResultsString += "\n";
		
		//Finally write any notes
		GcimResultsString += "Additional Notes/Comments: \n";
		GcimResultsString += "--------------------------------------------------" + "\n";
		if (!corrMatrixPD) {
			GcimResultsString += "The conditional correlation matrix was not positive definite" + "\n" +
								 "The original correlation matrix was: Rho = " + "\n" +
								 corrMatrixNotPDString + "\n" +
								 "The nearest PD correlation matrix used was: Rho_PD = " + "\n" +
								 corrMatrixPDString + "\n" +
								 "The Frobenius norm of the difference of these two matricies is: " + "\n" +
								 "normFrob = " + normFrob + "\n";
		}
		
		
		return GcimResultsString;
	}
	
//	/**
//	 * The method below computes the cholesky decomposition of a symmetric positive definate matrix
//	 * @param A: the positive definate matrix
//	 */
//	public double[][] cholesky(double[][] A) throws RuntimeException {
//		
//		//Get the dimensions of A
//		int numCol = A[0].length;
//		
//		double[][] L = new double[numCol][numCol];
//		for (int i=0; i<numCol; i++) {
//			for (int j=0; j<numCol; j++) {
//				L[i][j]=0.0;
//			}
//		}
//	    //Main Cholesky decomp code
//		for (int i=0; i<numCol; i++) {
//			for (int j=0; j<i; j++) {
//				L[i][j]=0.0;
//				double sumLL = 0.0;
//				for (int k=0; k<j; k++) {
//					sumLL = sumLL + L[i][k]*L[j][k];
//				}
//				L[i][j]=(A[i][j]-sumLL)/L[j][j];
//			}
//			double sumL2=0.0;
//			for (int k=0; k<i; k++) {
//				sumL2 = sumL2 + Math.pow(L[i][k],2);
//			}
//			
//			L[i][i]=Math.sqrt(A[i][i]-sumL2);
//			if (A[i][i]<sumL2) {
//			
//				String MatrixString = "Sigma=";
//				for (int p=0; p<numCol; p++) {
//					for (int q=0; q<numCol; q++) {
//						MatrixString = MatrixString + " " + Math.round((A[p][q])*1000.)/1000.; 
//					}
//					MatrixString = MatrixString + "\n";
//				}
//				throw new RuntimeException("Error: Covariance matrix (Sigma) is not SPD \n " + MatrixString);
//			}
//		}
//		return L;
//	}
//	
//	/**
//	 * This method performs matrix multiplication of a matrix and vector (Ax = b).  No checking is performed
//	 * that the dimension of the matricies are such that they commute
//	 * @param: A an n x m matrix
//	 * @param: x and m x 1 vector
//	 * @return: a vector b
//	 */
//	public double[] matrixVectorMultiplication(double[][] A,double[] x) {
//		
//		//Get the dimensions of the two matricies
//		int numRowsA = A.length;
//		int numColsA = A[0].length;
//		int numRowsx = x.length;
//		
//		if (numColsA!=numRowsx) 
//			throw new RuntimeException("Error: Matricies A and x do not commute. A = " + A + "; x = " + x);
//		
//		double sum;
//		double[] b = new double[numRowsA];
//	    
//	    for (int i=0; i<numRowsA; i++) {
//	    		sum=0.0;
//	    		for (int k=0; k<numColsA; k++) {
//	    			sum = sum + A[i][k]*x[k];
//	    		}
//	    		b[i]=sum;
//	    }
//		return b;
//	}
	
	/**
	 * This method returns matrix A in the form of a String which can be used in writing output
	 */
	private String getMatrixAsString(Matrix A) {
		String aAsString = "";
		//Get the dimensions of A
		int m = A.getRowDimension();
		int n = A.getColumnDimension();
	      for (int i = 0; i < m; i++) {
	         for (int j = 0; j < n; j++) {
	        	 aAsString = aAsString + " " + Math.round(A.get(i,j)*1.e5)/(1.e5); //5 dp
	         }
	         aAsString = aAsString + "\n";
	      }
	      return aAsString;
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
