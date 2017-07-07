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

package org.opensha.sha.calc.disaggregation;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.params.IncludeMagDistFilterParam;
import org.opensha.sha.calc.params.MagDistCutoffParam;
import org.opensha.sha.calc.params.MaxDistanceParam;
import org.opensha.sha.calc.params.NonSupportedTRT_OptionsParam;
import org.opensha.sha.calc.params.NumStochasticEventSetsParam;
import org.opensha.sha.calc.params.PtSrcDistanceCorrectionParam;
import org.opensha.sha.calc.params.SetTRTinIMR_FromSourceParam;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PtSrcDistCorr;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;


/**
 * <p>Title: DisaggregationCalculator </p>
 * <p>Description: This class disaggregates a hazard curve based on the
 * input parameters imr, site and eqkRupforecast.  See Bazzurro and Cornell
 * (1999, Bull. Seism. Soc. Am., 89, pp. 501-520) for a complete discussion
 * of disaggregation.  The Dbar computed here is for rupture distance.  This
 * assumes all sources in the ERF are Poissonian.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Ned Field
 * @date Oct 28, 2002
 * @version 1.0
 */

public class DisaggregationCalculator
implements DisaggregationCalculatorAPI{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	protected final static String C = "DisaggregationCalculator";
	protected final static boolean D = false;


	public static final String OPENSHA_SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "DisaggregationPlotServlet";

	// disaggregation stuff

	private double[] mag_center, mag_binEdges;
	private double[] dist_center, dist_binEdges;

	private int NUM_E = 8;
	private double[][][] pdf3D;
	private double maxContrEpsilonForDisaggrPlot;
	private double maxContrEpsilonForGMT_Plot;


	private int iMag, iDist, iEpsilon;
	private double mag, dist, epsilon;
	private boolean withinBounds;

	private double Mbar, Dbar, Ebar;
	private int M_index_mode3D, D_index_mode3D; //E_mode3D;

	//gets the Epsilon Range
	private String epsilonRangeString;

	private double totalRate, outOfBoundsRate;

	private int currRuptures = -1;
	private int totRuptures=0;

	//gets the number of sources to be shown in the disaggregation window
	private int numSourcesToShow = 0;
	
	private boolean showDistances = true;

	//stores the source Disagg info
	private String sourceDisaggInfo;

	//Disaggregation Plot Img Name
	public static final String DISAGGREGATION_PLOT_NAME = "DisaggregationPlot";
	public static final String DISAGGREGATION_PLOT_JPG_NAME = DISAGGREGATION_PLOT_NAME +".jpg";
	public static final String DISAGGREGATION_PLOT_PNG_NAME = DISAGGREGATION_PLOT_NAME +".png";
	public static final String DISAGGREGATION_PLOT_PDF_NAME = DISAGGREGATION_PLOT_NAME +".pdf";

	//Address to the disaggregation plot img
	private String disaggregationPlotImgWebAddr;

	static String[] epsilonColors = {
			"-G215/38/3",
			"-G252/94/62",
			"-G252/180/158",
			"-G254/220/210",
			"-G217/217/255",
			"-G151/151/255",
			"-G0/0/255",
	"-G0/0/170"};


	/**
	 * creates the DisaggregationCalculator object
	 *
	 * @throws IOException
	 */
	public DisaggregationCalculator() {

		//set defaults
		setMagRange(5, 9, 0.5);
		setDistanceRange(5, 11, 10);
		
		//Create adjustable parameters
	}

	/**
	 * this function performs the disaggregation.
	 * Returns true if it was succesfully able to disaggregate above
	 * a given IML else return false
	 *
	 * @param iml: the intensity measure level to disaggregate
	 * @param site: site parameter
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @param calcParams: calculation parameters from the <code>HazardCurveCalculator</code>
	 * @return boolean
	 */
	public boolean disaggregate(double iml, Site site,
			ScalarIMR imr,
			AbstractERF eqkRupForecast,
			ParameterList calcParams) {
		return disaggregate(iml, site, TRTUtils.wrapInHashMap(imr), eqkRupForecast, calcParams);
	}
	
	@Override
	public boolean disaggregate(
			double iml,
			Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap,
			AbstractERF eqkRupForecast, ParameterList calcParams) {
		
		if (Double.isInfinite(iml) || Double.isNaN(iml)) {
			currRuptures = 0;
			totRuptures = 0;
			return false;
		}
		
		MaxDistanceParam maxDistanceParam = (MaxDistanceParam)calcParams.getParameter(MaxDistanceParam.NAME);
		PtSrcDistanceCorrectionParam ptSrcDistCorrParam = (PtSrcDistanceCorrectionParam)calcParams.getParameter(PtSrcDistanceCorrectionParam.NAME);
		PtSrcDistCorr.Type distCorrType = ptSrcDistCorrParam.getValueAsTypePtSrcDistCorr();

//		NumStochasticEventSetsParam numStochEventSetRealizationsParam =
//			(NumStochasticEventSetsParam)calcParams.getParameter(NumStochasticEventSetsParam.NAME);
		IncludeMagDistFilterParam includeMagDistFilterParam =
			(IncludeMagDistFilterParam)calcParams.getParameter(IncludeMagDistFilterParam.NAME);
		MagDistCutoffParam magDistCutoffParam = (MagDistCutoffParam)calcParams.getParameter(MagDistCutoffParam.NAME);
		SetTRTinIMR_FromSourceParam setTRTinIMR_FromSourceParam =
			(SetTRTinIMR_FromSourceParam)calcParams.getParameter(SetTRTinIMR_FromSourceParam.NAME);
		NonSupportedTRT_OptionsParam nonSupportedTRT_OptionsParam =
			(NonSupportedTRT_OptionsParam)calcParams.getParameter(NonSupportedTRT_OptionsParam.NAME);

		double rate, condProb;

		DecimalFormat f1 = new DecimalFormat("000000");
		DecimalFormat f2 = new DecimalFormat("00.00");

		pdf3D = new double[dist_center.length][mag_center.length][NUM_E];

		DistanceRupParameter distRup = new DistanceRupParameter();

		String S = C + ": disaggregate(): ";

		if (D) System.out.println(S + "STARTING DISAGGREGATION");

		if (D) System.out.println(S + "iml = " + iml);

		//    if( D )System.out.println(S + "deltaMag = " + deltaMag + "; deltaDist = " + deltaDist + "; deltaE = " + deltaE);
		ArrayList<DisaggregationSourceRuptureInfo> disaggSourceList = null;
		DisaggregationSourceRuptureComparator srcRupComparator = null;
		if (this.numSourcesToShow > 0) {
			disaggSourceList = new ArrayList<DisaggregationSourceRuptureInfo>();
			srcRupComparator = new DisaggregationSourceRuptureComparator();
		}
		//resetting the Parameter change Listeners on the AttenuationRelationship
		//parameters. This allows the Server version of our application to listen to the
		//parameter changes.
//		for (ScalarIMR imr : imrMap.values())
//			( (AttenuationRelationship) imr).resetParameterEventListeners();


		boolean includeMagDistFilter = includeMagDistFilterParam.getValue();
		ArbitrarilyDiscretizedFunc magDistFilter = null;
		if (includeMagDistFilter)
			magDistFilter = magDistCutoffParam.getValue();
		double magThresh=0.0;
		double maxDist = maxDistanceParam.getValue();
		
		// set the maximum distance in the attenuation relationship
		// (Note- other types of IMRs may not have this method so we should really check type here)
		for (ScalarIMR imr : imrMap.values())
			imr.setUserMaxDistance(maxDist);

		// set iml in imrs
		for (ScalarIMR imr : imrMap.values()) {
			Parameter<Double> im = imr.getIntensityMeasure();
			if (im instanceof WarningParameter<?>) {
				WarningParameter<Double> warnIM = (WarningParameter<Double>)im;
				warnIM.setValueIgnoreWarning(new Double(iml));
			} else {
				im.setValue(new Double(iml));
			}
		}

		// get total number of sources
		int numSources = eqkRupForecast.getNumSources();

//		HashMap<String, ArrayList<?>> sourceDissaggMap = new HashMap<String, ArrayList<?>>();

		// compute the total number of ruptures for updating the progress bar
		totRuptures = 0;
		for (int i = 0; i < numSources; ++i)
			totRuptures += eqkRupForecast.getSource(i).getNumRuptures();

		// init the current rupture number (also for progress bar)
		currRuptures = 0;

		for (ScalarIMR imr : imrMap.values()) {
			try {
				// set the site in IMR
				imr.setSite(site);
			}
			catch (Exception ex) {
				if (D) System.out.println(C + ":Param warning caught" + ex);
				ex.printStackTrace();
			}
		}

		// initialize
		Ebar = 0;
		Mbar = 0;
		Dbar = 0;
		totalRate = 0;
		outOfBoundsRate = 0;

		// initialize the PDF
		for (int i = 0; i < dist_center.length; i++)
			for (int j = 0; j < mag_center.length; j++)
				for (int k = 0; k < NUM_E; k++)
					pdf3D[i][j][k] = 0;
		
	    int numRupRejected =0;

	    boolean setTRTinIMR_FromSource = setTRTinIMR_FromSourceParam.getValue();
		HashMap<ScalarIMR, TectonicRegionType> trtDefaults = null;
		if (setTRTinIMR_FromSource)
			trtDefaults = TRTUtils.getTRTsSetInIMRs(imrMap);
		
		for (int i = 0; i < numSources; i++) {

			double sourceRate = 0;
			// get source and get its distance from the site
			ProbEqkSource source = eqkRupForecast.getSource(i);

			String sourceName = source.getName();
			int numRuptures = eqkRupForecast.getNumRuptures(i);

			// check the distance of the source
			double distance = source.getMinDistance(site);
			if (distance > maxDist) {
				currRuptures += numRuptures;
				continue;
			}

			// get magThreshold if we're to use the mag-dist cutoff filter
			if(includeMagDistFilter) {
				magThresh = magDistFilter.getInterpolatedY(distance);
			}
			
			// set the IMR according to the tectonic region of the source (if there is more than one)
			TectonicRegionType trt = source.getTectonicRegionType();
			ScalarIMR imr = TRTUtils.getIMRforTRT(imrMap, trt);
			
			// Set Tectonic Region Type in IMR
			if(setTRTinIMR_FromSource) { // (otherwise leave as originally set)
				TRTUtils.setTRTinIMR(imr, trt, nonSupportedTRT_OptionsParam, trtDefaults.get(imr));
			}

//			if (numSourcesToShow > 0)
//				sourceDissaggMap.put(sourceName, new ArrayList());

			// loop over ruptures
			for (int n = 0; n < numRuptures; n++, ++currRuptures) {

				// get the rupture
				ProbEqkRupture rupture = source.getRupture(n);
				
				// set point-source distance correction type & mag if it's a pointSurface
				if(rupture.getRuptureSurface() instanceof PointSurface)
					((PointSurface)rupture.getRuptureSurface()).setDistCorrMagAndType(rupture.getMag(), distCorrType);

				double qkProb = rupture.getProbability();
				
			     // apply magThreshold if we're to use the mag-dist cutoff filter
		        if(includeMagDistFilter && rupture.getMag() < magThresh) {
		        	numRupRejected+=1;
		        	continue;
		        }

				// set the rupture in the imr
				imr.setEqkRupture(rupture);

				// get the cond prob
				condProb = imr.getExceedProbability(iml);
				// should the following throw an exception?
				if (condProb == 0 && D)
					System.out.println(S +
							"Exceedance probability is zero! (thus the NaNs below)");

				// get the mean, stdDev, epsilon, dist, and mag
				epsilon = imr.getEpsilon();
				distRup.setValue(rupture, site);
				dist = ( (Double) distRup.getValue()).doubleValue();
				mag = rupture.getMag();

				// get the equiv. Poisson rate over the time interval (not annualized)
				rate = -condProb * Math.log(1 - qkProb);


				/*
                   if( Double.isNaN(epsilon) && testNum < 1) {
          System.out.println("srcName = " + sourceName +
                             " src#=" + i +
                             " rup#=" + n +
                             " qkProb=" + (float)qkProb +
                             " condProb=" + (float)condProb +
                             " mean=" + (float)mean +
                             " stdDev=" + (float)stdDev +
                             " epsilon=" + (float)epsilon +
                             " dist=" + (float)dist +
                             " rate=" + (float)rate);
          System.out.println(rupture.getMag()+"  "+rupture.getRuptureSurface().get(0,0).toString());
          System.out.println(rupture.getRuptureSurface().getNumCols()+"  "+rupture.getRuptureSurface().getNumRows());
          System.out.println(site.getLocation().toString());
          Iterator it = site.getParametersIterator();
          while(it.hasNext())
            System.out.println( ((Parameter)it.next()).getMetadataString() );
          PropagationEffect pe = new PropagationEffect();
          pe.setAll(rupture,site);
          System.out.println(pe.getParamValue(DistanceSeisParameter.NAME));

          testNum += 1;
                   }
				 */

				// proceed only if rate is greater than zero (avoids NaN epsilons & is faster)
				if( rate > 0.0) {
					// set the 3D array indices & check that all are in bounds
					setIndices();
					if (withinBounds)
						pdf3D[iDist][iMag][iEpsilon] += rate;
					else {
						if (D) System.out.println(
								"disaggregation(): Some bin is out of range");
						outOfBoundsRate += rate;
					}

					//          if( D ) System.out.println("disaggregation(): bins: " + iMag + "; " + iDist + "; " + iEpsilon);

					totalRate += rate;

					Mbar += rate * mag;
					Dbar += rate * dist;
					Ebar += rate * epsilon;
					sourceRate += rate;

				}
				// create and add rupture info to source list
				/*if (numSourcesToShow > 0) {
            double eventRate = -Math.log(1 - qkProb); // this event rate is not annualized!
            DisaggregationSourceRuptureInfo rupInfo = new
                DisaggregationSourceRuptureInfo(null, eventRate, (float) rate, n,
                mag,dist);
            ( (ArrayList) sourceDissaggMap.get(sourceName)).add(rupInfo);
          }*/

			}
			if (numSourcesToShow > 0) {
				// sort the ruptures in this source according to contribution
				//ArrayList sourceRupList = (ArrayList) sourceDissaggMap.get(sourceName);
				//Collections.sort(sourceRupList,srcRupComparator);
				// create the total rate info for this source
				DisaggregationSourceRuptureInfo disaggInfo = new
				DisaggregationSourceRuptureInfo(sourceName, (float) sourceRate, i, source);
				disaggSourceList.add(disaggInfo);
			}
		}
		
		// reset TRT parameter in IMRs
		if (trtDefaults != null)
			TRTUtils.resetTRTsInIMRs(trtDefaults);

		//if no rate of exceedance above a given IML then return false.
		if (! (totalRate > 0)) {
			System.out.println("Disagg filed: totalRate: "+totalRate);
			return false;
		}

		// sort the disaggSourceList according to contribution
		if (numSourcesToShow > 0) {
			Collections.sort(disaggSourceList, srcRupComparator);
			// make a string of the sorted list info
			sourceDisaggInfo =
				"Source#\t% Contribution\tTotExceedRate\tSourceName";
			if (showDistances)
				sourceDisaggInfo += "\tDistRup\tDistX\tDistSeis\tDistJB";
			sourceDisaggInfo += "\n";
			int size = disaggSourceList.size();
			if (size > numSourcesToShow)
				size = numSourcesToShow;
			// overide to only give the top 100 sources (otherwise can be to big and cause crash)
			for (int i = 0; i < size; ++i) {
				DisaggregationSourceRuptureInfo disaggInfo = (
						DisaggregationSourceRuptureInfo)
						disaggSourceList.get(i);
				sourceDisaggInfo += f1.format(disaggInfo.getId()) + "\t" +
				f2.format(100*disaggInfo.getRate()/totalRate) +
				"\t" + (float) disaggInfo.getRate() +
				"\t" + disaggInfo.getName();
				
				if (showDistances) {
					ProbEqkSource source = disaggInfo.getSource();
					double mag = 0;
					for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
						double myMag = source.getRupture(rupID).getMag();
						if (myMag > mag)
							mag = myMag;
					}
					
					try {
						RuptureSurface surf = source.getSourceSurface();
						sourceDisaggInfo += "\t" + f2.format(surf.getDistanceRup(site.getLocation()))
								+ "\t" + f2.format(surf.getDistanceX(site.getLocation()))
								+ "\t" + f2.format(surf.getDistanceSeis(site.getLocation()))
								+ "\t" + f2.format(surf.getDistanceJB(site.getLocation()));
					} catch (Exception e) {
						sourceDisaggInfo += "\t(no source surface information available, likely a background source)";
					}

				}
				
				sourceDisaggInfo += "\n";
				//System.out.println(f2.format(100*disaggInfo.getRate()/totalRate));
			}
		}
		/*try {
      FileWriter fw = new FileWriter("Source_Rupture_OpenSHA.txt");
      String sourceRupDisaggregationInfo =
          "#Source-Id  Source-Rate   Rupture-Id   Mag   Distance   Rupture-Exceed-Rate Rupture-Rate  Source-Name\n";
      fw.write(sourceRupDisaggregationInfo);
      int size = disaggSourceList.size();
      for (int i = 0; i < size; ++i) {
        DisaggregationSourceRuptureInfo disaggInfo = (
            DisaggregationSourceRuptureInfo)
            disaggSourceList.get(i);
        String sourceName = disaggInfo.getName();
        String sourceInfo = disaggInfo.getId() + "\t" +
            (float) disaggInfo.getRate();
        ArrayList rupList = (ArrayList) sourceDissaggMap.get(sourceName);
        int rupListSize = rupList.size();
        for (int j = 0; j < rupListSize; ++j) {
          DisaggregationSourceRuptureInfo disaggRupInfo =
              (DisaggregationSourceRuptureInfo) rupList.get(j);
          sourceRupDisaggregationInfo = sourceInfo + "\t" + disaggRupInfo.getId() +
              "\t" + disaggRupInfo.getMag() + "\t" + disaggRupInfo.getDistance() +
              "\t"+ (float) disaggRupInfo.getRate() + "\t" +
              (float) disaggRupInfo.getEventRate()
              + "\t" + sourceName + "\n";
          fw.write(sourceRupDisaggregationInfo);
        }
      }

      fw.close();
    }
    catch (IOException ex1) {
      ex1.printStackTrace();
    }*/

		Mbar /= totalRate;
		Dbar /= totalRate;
		Ebar /= totalRate;
		if (D) System.out.println(S + "Mbar = " + Mbar);
		if (D) System.out.println(S + "Dbar = " + Dbar);
		if (D) System.out.println(S + "Ebar = " + Ebar);

		maxContrEpsilonForDisaggrPlot = -1;
		int modeMagBin = -1, modeDistBin = -1, modeEpsilonBin = -1;
		double maxContrBinRate = -1;
		for (int i = 0; i < dist_center.length; i++) {
			for (int j = 0; j < mag_center.length; j++) {
				double contrEpsilonSum = 0;
				for (int k = 0; k < NUM_E; k++) {
					pdf3D[i][j][k] = pdf3D[i][j][k] / totalRate * 100; // convert to
					//summing over all the contributing Epsilon for a given dist and Mag.
					contrEpsilonSum += pdf3D[i][j][k];
					if (pdf3D[i][j][k] > maxContrBinRate) {
						maxContrBinRate = pdf3D[i][j][k];
						modeDistBin = i;
						modeMagBin = j;
						modeEpsilonBin = k;
					}
				}
				if (contrEpsilonSum > maxContrEpsilonForDisaggrPlot)
					maxContrEpsilonForDisaggrPlot = contrEpsilonSum;
			}
		}
		M_index_mode3D = modeMagBin;
		D_index_mode3D = modeDistBin;
		epsilonRangeString = this.getEpsilonRange(modeEpsilonBin);
		//E_mode3D = eps(modeEpsilonBin);

		if (D) System.out.println(S + "MagModeIndex = " + M_index_mode3D + "; binNum = " +
				modeMagBin);
		if (D) System.out.println(S + "DistModeIndex = " + D_index_mode3D + "; binNum = " +
				modeDistBin);
		if (D) System.out.println(S + "EpsMode = " + epsilonRangeString +
				"; binNum = " + modeEpsilonBin);
		//if( D ) System.out.println(S + "EpsMode = "  + E_mode3D + "; binNum = " + modeEpsilonBin);

System.out.println("numRupRejected="+numRupRejected);

		return true;
	}


	/**
	 *
	 * Returns the disaggregated source list with following info ( in each line)
	 * 1)Source Id as given by OpenSHA
	 * 2)Name of the Source
	 * 3)Rate Contributed by that source
	 * 4)Percentage Contribution of the source in Hazard at the site.
	 *
	 * @return String
	 */
	public String getDisaggregationSourceInfo() {
		if(numSourcesToShow >0)
			return sourceDisaggInfo;
		return "";
	}

	/**
	 * Setting up the Mag Range
	 * @param minMag double - this is the center of the first bin
	 * @param numMags int
	 * @param deltaMag double
	 */
	public void setMagRange(double minMag, int numMags, double deltaMag) {
		mag_center = new double[numMags];
		mag_binEdges = new double[numMags+1];
		mag_binEdges[0] = minMag-deltaMag/2;
		for(int i=0;i<numMags;i++) {
			mag_center[i] = minMag+i*deltaMag;
			mag_binEdges[i+1] = mag_center[i] + deltaMag/2;
		}
	}

	/**
	 * Setting up the Mag Range
	 * @param dmagBinEdges - a double array of the distance-bin edges (in correct order, from low to high)
	 */
	public void setMagRange(double[] magBinEdges) {
		this.mag_binEdges = magBinEdges;
		mag_center = new double[mag_binEdges.length-1];
		for(int i=0;i<mag_center.length;i++)
			mag_center[i] = (mag_binEdges[i]+mag_binEdges[i+1])/2;
	}



	/**
	 * Setting up the Distance Range
	 * @param minDist double - this is the center of the first bin
	 * @param numDist int
	 * @param deltaDist double
	 */
	public void setDistanceRange(double minDist, int numDist, double deltaDist) {
		dist_center = new double[numDist];
		dist_binEdges = new double[numDist+1];
		dist_binEdges[0] = minDist-deltaDist/2;
		for(int i=0;i<numDist;i++) {
			dist_center[i] = minDist+i*deltaDist;
			dist_binEdges[i+1] = dist_center[i] + deltaDist/2;
		}
		// hack test:
		//	  double[] temp = {0,1,2,5,10,20,50,100,200};
		//	  setDistanceRange(temp);
	}


	/**
	 * Setting up the Distance Range
	 * @param distBinEdges - a double array of the distance-bin edges (in correct order, from low to high)
	 */
	public void setDistanceRange(double[] distBinEdges) {
		this.dist_binEdges = distBinEdges;
		dist_center = new double[distBinEdges.length-1];
		for(int i=0;i<dist_center.length;i++)
			dist_center[i] = (distBinEdges[i]+distBinEdges[i+1])/2;
	}



	/**
	 * Sets the Max Z Axis Range value for plotting purposes
	 * @param zMax
	 */
	public void setMaxZAxisForPlot(double zMax) {
		if(!Double.isNaN(zMax))
			maxContrEpsilonForGMT_Plot = zMax;
		else
			maxContrEpsilonForGMT_Plot = this.maxContrEpsilonForDisaggrPlot;
	}

	/**
	 * gets the number of current rupture being processed
	 * @return
	 */
	public int getCurrRuptures() {
		return this.currRuptures;
	}

	/**
	 * gets the total number of ruptures
	 * @return
	 */
	public int getTotRuptures() {
		return this.totRuptures;
	}

	/**
	 * Checks to see if disaggregation calculation for the selected site
	 * have been completed.
	 * @return
	 */
	public boolean done() {
		return (currRuptures==totRuptures);
	}

	/**
	 *
	 * @return resultant disaggregation in a String format.
	 */
	public String getMeanAndModeInfo() {


		float mm_l = (float) mag_binEdges[M_index_mode3D]; 
		float mm_u = (float) mag_binEdges[M_index_mode3D+1]; 
		float dm_l = (float) dist_binEdges[D_index_mode3D]; 
		float dm_u = (float) dist_binEdges[D_index_mode3D+1]; 
		//float em_l = (float) (E_mode3D-deltaE/2.0);
		//float em_u = (float) (E_mode3D+deltaE/2.0);
		String results;

		results = "\n" +
		"\n  Mbar = " + (float) Mbar +
		"\n  Dbar = " + (float) Dbar +
		"\n  Ebar = " + (float) Ebar + "\n" +
		"\n  " + mm_l+" ≤ Mmode < " + mm_u +
		"\n  " + dm_l+" ≤ Dmode < " + dm_u;
		/*if( E_mode3D == Double.NEGATIVE_INFINITY || E_mode3D == Double.POSITIVE_INFINITY)
      results += "\n  Emode = " + E_mode3D;
    else
      results += "\n  " + em_l+" ≤ Emode < " + em_u;*/
		results += "\n"+epsilonRangeString;

		if(totalRate == 0.0)
			results += "\n\nNote:\n" +
			"The above NaN values result from the chosen IML\n" +
			"(or that interpolated from the chosen probability)\n" +
			"never being exceeded.";

		/*
        results = "Disaggregation Result:\n\n\tMbar = " + Mbar + "\n\tDbar = " +
              Dbar + "\n\tEbar = " + Ebar + "\n\n\tMmode = " + M_mode3D +
              "\n\tDmode = " + D_mode3D + "\n\tEmode = " + E_mode3D;
		 */

		return results;

	}


	/**
	 * Returns the Bin Data in the String format
	 * @return String
	 */
	public String getBinData() {

		DecimalFormat f1 = new DecimalFormat("0.00");
		DecimalFormat f2 = new DecimalFormat("00.00");
		DecimalFormat f3 = new DecimalFormat("000.00");
		double totPercent, percent;

		String binInfo = "Dist\tMag\tE≤-2\t-2<E≤-1\t-1<E≤-0.5\t "+
		"-0.5>E≤0\t 0<E≤0.5\t 0.5<E≤1\t 1<E≤2\t 2>E \n";
		binInfo += "-----\t----\t------\t------\t-------\t" +
		"-------\t-------\t-------\t-------\t------\n";
		for (int i = 0; i < dist_center.length; ++i) {
			for (int j = 0; j < mag_center.length; ++j) {
				binInfo +=f3.format(dist_center[i])+" \t "+f1.format(mag_center[j])+" \t ";
				String E_String ="";
				totPercent = 0;
				for (int k = 0; k < NUM_E; ++k) {
					percent = pdf3D[i][j][k];
					E_String += f2.format(percent)+" \t ";
					totPercent += percent;
				}
				binInfo +=E_String+f2.format(totPercent)+"\n";
			}
		}
		return binInfo;
	}


	private void setIndices() {
		withinBounds= true;
		iMag=-1;
		iDist=-1;

		// Get mag bin
		for(int i=0;i<mag_center.length;i++) 
			if(mag>=mag_binEdges[i] && mag<mag_binEdges[i+1]) {
				iMag = i;
				break;
			}

		// Get the dist bin
		for(int i=0;i<dist_center.length;i++) 
			if(dist>=dist_binEdges[i] && dist<dist_binEdges[i+1]) {
				iDist = i;
				break;
			}

		if (epsilon <= -2)
			iEpsilon = 0;
		else if (epsilon > -2 && epsilon <= -1)
			iEpsilon = 1;
		else if (epsilon > -1 && epsilon <= -0.5)
			iEpsilon = 2;
		else if (epsilon > -0.5 && epsilon <= 0)
			iEpsilon = 3;
		else if (epsilon > 0 && epsilon <= 0.5)
			iEpsilon = 4;
		else if (epsilon > 0.5 && epsilon <= 1.0)
			iEpsilon = 5;
		else if (epsilon > 1.0 && epsilon <= 2.0)
			iEpsilon = 6;
		else if (epsilon > 2.0)
			iEpsilon = 7;

		if( iMag == -1) withinBounds= false;
		if( iDist == -1) withinBounds = false;

	}


	/**
	 * Gets the Epsilon range String based on the index of the epsilon
	 * @param iEpsilon int
	 * @return String
	 */
	private String getEpsilonRange(int iEpsilon){

		switch (iEpsilon){
		case 0:
			return "Emode <= -2";
		case 1:
			return "-2 < Emode <= -1";
		case 2:
			return "-1 < Emode <= -0.5";
		case 3:
			return "-0.5 < Emode <= 0.0";
		case 4:
			return "0.0 < Emode <= 0.5";
		case 5:
			return "0.5 < Emode <= 1.0";
		case 6:
			return "1.0 < Emode <= 2.0";
		case 7:
			return "2.0 < Emode ";
		default:
			return "Incorrect Index";
		}
	}

	/**
	 * Gets the plot image for the Disaggregation
	 * @param metadata String
	 * @return String
	 */
	public String getDisaggregationPlotUsingServlet(String metadata) {
		DisaggregationPlotData data = getDisaggPlotData();
		disaggregationPlotImgWebAddr = openServletConnection(data, metadata);
		return disaggregationPlotImgWebAddr;
	}
	
	public DisaggregationPlotData getDisaggPlotData() {
		return new DisaggregationPlotData(mag_center, mag_binEdges, dist_center, dist_binEdges,
				maxContrEpsilonForGMT_Plot, NUM_E, pdf3D);
	}

	/**
	 * Creates the GMT_Script lines
	 */
	public static ArrayList<String> createGMTScriptForDisaggregationPlot(DisaggregationPlotData data, String dir){
		if (D) System.out.println(1);

		double x_axis_length = 4.5; // in inches
		double y_axis_length = 4.0; // in inches
		double z_axis_length = 2.5; // in inches

		int numTicksToDrawForZAxis = 5;
		// compute z-axis tick spacing & max z value
		double z_tick = Math.ceil(data.getMaxContrEpsilonForGMT_Plot()/numTicksToDrawForZAxis);
		double maxZVal = z_tick * numTicksToDrawForZAxis;
		Preconditions.checkState(maxZVal > 0, "disagg max z val must be greater than 0!");
		ArrayList<String> gmtScriptLines = new ArrayList<String>();
		// System.out.println(maxContrEpsilonForDisaggrPlot+"\t"+z_grid+"\t"+maxZVal);
		
		if (D) System.out.println(2);
		
		double dist_binEdges[] = data.getDist_binEdges();
		double mag_binEdges[] = data.getMag_binEdges();
		double dist_center[] = data.getDist_center();
		double mag_center[] = data.getMag_center();
		
		int numE = data.getNUM_E();
		
		double pdf3D[][][] = data.getPdf3D();
		
//		data.get

		float min_dist = (float) dist_binEdges[0];
		float max_dist = (float) dist_binEdges[dist_binEdges.length-1];
		float min_mag = (float) mag_binEdges[0];
		float max_mag = (float) mag_binEdges[mag_binEdges.length-1];
		
		if (D) System.out.println(3);

		double totDist = dist_binEdges[dist_binEdges.length-1]-dist_binEdges[0];
		double x_tick;
		if(totDist<115) x_tick = 10;
		else if (totDist<225) x_tick = 20;
		else if (totDist<335) x_tick = 30;
		else if (totDist<445) x_tick = 40;
		else x_tick = 50;

		double distBinWidthToInches = x_axis_length/totDist;


		double totMag = mag_binEdges[mag_binEdges.length-1]-mag_binEdges[0];
		double y_tick;
		if(totMag<5) y_tick = 0.5;
		else y_tick = 1.0;

		double magBinWidthToInches = y_axis_length/totMag;
		
		if (D) System.out.println(4);

		gmtScriptLines.add("#!/bin/bash");
		gmtScriptLines.add("");
		gmtScriptLines.add("cd " + dir);
		gmtScriptLines.add("");
		gmtScriptLines.addAll(GMT_MapGenerator.getGMTPathEnvLines());
		gmtScriptLines.add("## Plot Script ##");
		gmtScriptLines.add("");
		
		try{
			if (D) System.out.println(5);
			String region = "-R"+min_dist+"/"+max_dist+"/"+min_mag+"/"+max_mag+"/"+0+"/"+maxZVal;
			String projection = "-JX"+x_axis_length+"i/"+y_axis_length+"i";
			String viewAngle = "-p150/30";
			String boxPenWidth = "-W0.5p";  // pen width for drawing boxes
			String verticalScaling = "-JZ"+z_axis_length+"i";
			// temporarily switching background color to 0/0/0 from 180/180/180 because anything
			// that's not pure white comes out as pure black
			gmtScriptLines.add("${GMT_PATH}gmtset PS_PAGE_COLOR=255/255/255");
			gmtScriptLines.add("${GMT_PATH}gmtset MAP_ORIGIN_X=1.0i");
			gmtScriptLines.add("${GMT_PATH}gmtset MAP_ORIGIN_Y=2.0i");
			gmtScriptLines.add("");
			String img_ps_file = "DisaggregationPlot.ps";

			String axisBoundaryTicksBounds = "-B"+x_tick+":\"Rupture Distance (km)\":"+"/"+y_tick+":Magnitude:"+
			"/"+z_tick+":%Contribution:"+"wSnEZ";
			gmtScriptLines.add("${COMMAND_PATH}echo \"plotting axis\"");
			gmtScriptLines.add("${COMMAND_PATH}cat << END > temp_segments");
			//creating the grid lines on Z axis.
			//System.out.println(z_tick+"   "+maxZVal+"   "+maxContrEpsilonForDisaggrPlot);
			if (D) System.out.println(6);
			for (double k = z_tick; k <= maxZVal; k += z_tick) {
				gmtScriptLines.add(">");
				gmtScriptLines.add(min_dist+"  "+ min_mag+" "+k);
				gmtScriptLines.add(min_dist+"  "+max_mag+"  "+k);
				gmtScriptLines.add(">");
				gmtScriptLines.add(min_dist+"  "+ max_mag+"  "+k);
				gmtScriptLines.add(+max_dist+"   "+max_mag+"  "+k);
			}
			if (D) System.out.println(7);
			gmtScriptLines.add(">");
			gmtScriptLines.add(min_dist +"   "+ max_mag+"  " + 0);
			gmtScriptLines.add( min_dist + "  "+max_mag + "  " + maxZVal);
			gmtScriptLines.add(">");
			gmtScriptLines.add(max_dist + "  "+ max_mag + " "  + 0);
			gmtScriptLines.add(  + max_dist + "  " +max_mag+ " "+ maxZVal);
			gmtScriptLines.add("END");
			gmtScriptLines.add("");

			//creating the GMT_Script for the plot
			gmtScriptLines.add("${GMT_PATH}psxyz temp_segments -P "+
					region+"  " +projection +"  "+verticalScaling+" -K -G0/0/0 "+
					viewAngle + "  "+boxPenWidth+"  "+axisBoundaryTicksBounds +" >  "+img_ps_file);

			float contribution, base, top;
			gmtScriptLines.add("${COMMAND_PATH}echo \"plotting disagg\"");
			if (D) System.out.println(8);
			for (int i = 0; i < dist_center.length; ++i) {
				if (D) System.out.println(9);
				gmtScriptLines.add("${COMMAND_PATH}echo \"plotting dist bin " + i + "\"");
				for (int j = mag_center.length - 1; j >= 0; --j) {   // ordering here is important
//					System.out.println(10);
					double box_x_width = (dist_binEdges[i+1]- dist_binEdges[i])*distBinWidthToInches - 0.05; // lst term leaves some space
					double box_y_width = (mag_binEdges[j+1]- mag_binEdges[j])*magBinWidthToInches - 0.05;
					String symbol = " -So"+box_x_width+"i/"+box_y_width+"ib";

					base = 0;
					top = 0;
					for (int k = 0; k < numE; ++k) {
						contribution = (float) pdf3D[i][j][k];
						top = base + contribution;
						if (contribution > 0.0) {
							gmtScriptLines.add("${COMMAND_PATH}echo " + "\"" + dist_center[i] + " " + mag_center[j] + " " + top +
									"\"" +
									" | ${GMT_PATH}psxyz "
									+ "-P " + region + " " + projection + " " +
									verticalScaling + symbol + base +
									" -K -O " + epsilonColors[k] + "  " +
									viewAngle +
									"  " + boxPenWidth + " >> " + img_ps_file);
							base = top;
						}

					}
				}
			}
			if (D) System.out.println(11);
			
			gmtScriptLines.add("");
			gmtScriptLines.add("${COMMAND_PATH}echo \"plotting legend\"");
			// add the legend boxes
			// 1st legend box has origin offset in Y by -2 inches (and X by minus some too)
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"" + dist_binEdges[dist_binEdges.length-1] + " " + mag_binEdges[0] + " " + (0.8*z_tick) +
					"\"" + " | ${GMT_PATH}psxyz " + "-P -Y-1.25i -X-4.2i " +
					region + " " +
					projection + " " + verticalScaling + " -So0.3ib0 " +
					" -K -O " +
					epsilonColors[0] + "  " + viewAngle + "  " + boxPenWidth +
					" >> " + img_ps_file);

			// each now has origin offset in the X direction
			if (D) System.out.println(12);
			for (int k = 1; k < numE; ++k) {
				gmtScriptLines.add("${COMMAND_PATH}echo " + "\"" + dist_binEdges[dist_binEdges.length-1] + " " + mag_binEdges[0] + " " + (0.8*z_tick) +
						"\"" + " | ${GMT_PATH}psxyz " + "-P -X0.9i " +
						region + " " +
						projection + " " + verticalScaling + " -So0.3ib0 " +
						" -K -O " +
						epsilonColors[k] + "  " + viewAngle + "  " + boxPenWidth +
						" >> " + img_ps_file);
			}
			if (D) System.out.println(13);


			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"0.0 0.75 13,12 0.0 CB e<-2\" > temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"0.9 0.75 13,12 0.0 CB -2<e<-1\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"1.8 0.75 13,12 0.0 CB -1<e<-0.5\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"2.7 0.75 13,12 0.0 CB -0.5<e<0\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"3.6 0.75 13,12 0.0 CB 0<e<0.5\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"4.5 0.75 13,12 0.0 CB 0.5<e<1\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"5.4 0.75 13,12 0.0 CB 1<e<2\" >> temp_label");
			gmtScriptLines.add("${COMMAND_PATH}echo " + "\"6.3 0.75 13,12 0.0 CB 2<e\" >> temp_label");
			// on gravity we used -X-2.45, but for some reason that puts stuff to the right
			// on opensha.usc.edu
			gmtScriptLines.add("${GMT_PATH}pstext temp_label -F+f+a+j -R0/8.5/0/11 -N -Jx1i -X-6.1 -P -O >> " + img_ps_file);
			gmtScriptLines.add("");
			gmtScriptLines.add("${COMMAND_PATH}echo \"converting postscript\"");
//			gmtScriptLines.add("${COMMAND_PATH}cat "+img_ps_file+ " |"+ "gs -sDEVICE=jpeg -sOutputFile=temp.jpg"+" -");
			gmtScriptLines.add("${PS2PDF_PATH} "+img_ps_file+"  "+DISAGGREGATION_PLOT_PDF_NAME);
			gmtScriptLines.add("${CONVERT_PATH} -chop 0x300 "+img_ps_file+" "+DISAGGREGATION_PLOT_JPG_NAME);
			gmtScriptLines.add("${CONVERT_PATH} -chop 0x300 "+img_ps_file+" "+DISAGGREGATION_PLOT_PNG_NAME);
			gmtScriptLines.add("${COMMAND_PATH}rm temp_segments");
			if (D) System.out.println(14);
		}catch(Exception e){
			e.printStackTrace();
		}

		return gmtScriptLines;
	}


	/**
	 * sets up the connection with the servlet on the server (gravity.usc.edu)
	 */
	private String openServletConnection(DisaggregationPlotData data,
			String metadata) throws RuntimeException{

		String webaddr=null;
		try{

			if(D) System.out.println("starting to make connection with servlet");
			URL gmtPlotServlet = new URL(OPENSHA_SERVLET_URL);


			URLConnection servletConnection = gmtPlotServlet.openConnection();
			if(D) System.out.println("connection established");

			// inform the connection that we will send output and accept input
			servletConnection.setDoInput(true);
			servletConnection.setDoOutput(true);

			// Don't use a cached version of URL connection.
			servletConnection.setUseCaches (false);
			servletConnection.setDefaultUseCaches (false);
			// Specify the content type that we will send binary data
			servletConnection.setRequestProperty ("Content-Type","application/octet-stream");

			ObjectOutputStream outputToServlet = new
			ObjectOutputStream(servletConnection.getOutputStream());


			//sending the disagg data
			outputToServlet.writeObject(data);
			//sending the contents of the Metadata file to the server.
			outputToServlet.writeObject(metadata);


			outputToServlet.flush();
			outputToServlet.close();

			// Receive the "actual webaddress of all the gmt related files"
			// from the servlet after it has received all the data
			ObjectInputStream inputToServlet = new
			ObjectInputStream(servletConnection.getInputStream());

			Object messageFromServlet = inputToServlet.readObject();
			inputToServlet.close();
			if(messageFromServlet instanceof String){
				webaddr = (String) messageFromServlet;
				if (D) System.out.println("Receiving the Input from the Servlet:" +
						webaddr);
			}
			else
				throw (RuntimeException)messageFromServlet;
		}catch(RuntimeException e){
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Server is down , please try again later");
		}
		return webaddr;
	}


	/**
	 * Sets the number of sources to be shown in the Disaggregation.
	 * @param numSources int
	 */
	public void setNumSourcestoShow(int numSources) {
		numSourcesToShow = numSources;
	}
	
	public void setShowDistances(boolean showDistances) {
		this.showDistances = showDistances;
	}

}
