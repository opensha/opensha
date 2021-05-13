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

package org.opensha.sha.earthquake.rupForecastImpl.NewZealand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.HanksBakun2002_MagAreaRel;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.estimate.DiscreteValueEstimate;
import org.opensha.commons.data.estimate.NormalEstimate;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.earthquake.rupForecastImpl.GriddedRegionPoissonEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.analysis.ParamOptions;
import org.opensha.sha.earthquake.rupForecastImpl.YuccaMountain.YuccaMountainERF_List;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.magdist.GaussianMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <p>Title: New Zealand 2010 Eqk Rup Forecast</p>
 * <p>Description: .
 * Earthquake Rupture Forecast for New Zealand using the 2010
 * NSHM (Stirling et al, submitted).  As well as epistemic
 * uncertainties examined as part of EQC project (Bradley et al, in prep)
 * Background sources yet to be included (31 Jan 2011)
 * </p>
 * <p>Copyright: Copyright (c) 2011</p>
 * <p>Company: </p>
 * @author : Brendon Bradley
 * @Date : Jan, 2011
 * @version 1.0
 */

public class NewZealandERF2010 extends AbstractERF{

	//for Debug purposes
	private static String  C = new String("NewZealand_ERF_2010");
	private boolean D = false;
	// name of this ERF
	public final static String NAME = new String("NewZealand_ERF_2010");


	private final static String FAULT_SOURCE_FILENAME = "org/opensha/sha/earthquake/rupForecastImpl/NewZealand/NZ_FLTmodel_2010.txt";
	private final static String BG_FILE_NAME = "org/opensha/sha/earthquake/rupForecastImpl/NewZealand/NZBCK211_OpenSHA.txt";
//	private final static String BG_FILE_NAME = "org/opensha/sha/earthquake/rupForecastImpl/NewZealand/NZ_BKmodeldraft.txt";
	
	// Min/Max/Num Mags for Mag Freq Dist for making fault sources
	private final static double MIN_MAG = 5.0;
	private final static double MAX_MAG = 9.0;
	private final static int NUM_MAGS = 41;
	private final static double magIncrement = 0.1;

	// Default Grid Spacing for making Evenly Gridded Surface
	private final static double DEFAULT_GRID_SPACING = 1.0;

	public final static String FAULT_AND_BACK_SEIS_NAME = new String ("Background and Fault Seismicity");
	public final static String FAULT_AND_BACK_SEIS = new String ("Fault and Background Sources");
	public final static String FAULT_SEIS_ONLY = new String ("Fault Sources Only");
	public final static String BACK_SEIS_ONLY = new String ("Background Sources Only");
	private StringParameter backSeisParam;
	
	// Epistemic uncertainty consideration
	//Overall consideration
	public final static String EPISTEMIC_PARAM_NAME = "Consider Epistemic Uncertainties";
	private final static String EPISTEMIC_PARAM_INFO = "Consideration of Epistemic Uncertainties";
	private final static Boolean EPISTEMIC_PARAM_DEFAULT = new Boolean(false);
	private BooleanParameter epistemicParam;
	//in length, top and bottom
	public final static String EPISTEMIC_GEOMETRY_PARAM_NAME = "Epistemic Uncertainties in fault length, depth";
	private final static String EPISTEMIC_GEOMETRY_PARAM_INFO = "Epistemic Uncertainties in fault length, depth";
	private final static Boolean EPISTEMIC_GEOMETRY_PARAM_DEFAULT = new Boolean(false);
	private BooleanParameter epistemicGeometryParam;
	//in dip
	public final static String EPISTEMIC_DIP_PARAM_NAME = "Epistemic Uncertainties in fault dip";
	private final static String EPISTEMIC_DIP_PARAM_INFO = "Epistemic Uncertainties in fault dip";
	private final static Boolean EPISTEMIC_DIP_PARAM_DEFAULT = new Boolean(false);
	private BooleanParameter epistemicDipParam;
	//in slip rate and coupling coefficient
	public final static String EPISTEMIC_SLIP_PARAM_NAME = "Epistemic Uncertainties in fault slip rate and coupling";
	private final static String EPISTEMIC_SLIP_PARAM_INFO = "Epistemic Uncertainties in fault slip rate and coupling";
	private final static Boolean EPISTEMIC_SLIP_PARAM_DEFAULT = new Boolean(false);
	private BooleanParameter epistemicSlipParam;
	//in magnitude scaling
	public final static String EPISTEMIC_MAGSCALING_PARAM_NAME = "Epistemic Uncertainties in Magnitude Scaling Relations";
	private final static String EPISTEMIC_MAGSCALING_PARAM_INFO = "Epistemic Uncertainties in Magnitude Scaling Relations";
	private final static Boolean EPISTEMIC_MAGSCALING_PARAM_DEFAULT = new Boolean(false);
	private BooleanParameter epistemicMagScalingParam;
	//in correlation between mag scaling on different faults
	public final static String EPISTEMIC_MAGSCALINGCORRELATION_PARAM_NAME = "Correlation of Epistemic Uncertainties in Mw Scaling on faults";
	private final static String EPISTEMIC_MAGSCALINGCORRELATION_PARAM_INFO = "Correlation of Epistemic Uncertainties in Mw Scaling on faults";
	private final static double EPISTEMIC_MAGSCALINGCORRELATION_PARAM_DEFAULT = new Double(0.5);
	private DoubleParameter epistemicMagScalingCorrelationParam;
	//proportion of mag scaling uncertainty considered as aleatory 
	public final static String EPISTEMIC_MAGSCALINGPROPORTION_PARAM_NAME = "Proportion of Mw Scaling Unc considered as epistemic (remaining aleatory)";
	private final static String EPISTEMIC_MAGSCALINGPROPORTION_PARAM_INFO = "Proportion of Mw Scaling Unc considered as epistemic (remaining aleatory)";
	private final static double EPISTEMIC_MAGSCALINGPROPORTION_PARAM_DEFAULT = new Double(0.5);
	private DoubleParameter epistemicMagScalingUncertaintyProportionParam;

	private int numBkSources = 0;
	private ArrayList<String> bkSourceNames = new ArrayList<String>();
	private ArrayList<String> bkSourceTectonicTypes = new ArrayList<String>();
	private ArrayList<Location> bkSourceLocation = new ArrayList<Location>();
	private ArrayList<GriddedRegion> bkSourceRegion = new ArrayList<GriddedRegion>();
	private ArrayList<Double> bkBValMeans = new ArrayList<Double>();
	private ArrayList<Double> bkBValSigmas = new ArrayList<Double>();
	private ArrayList<Double> bkMoRateMeans = new ArrayList<Double>();
	private ArrayList<Double> bkMoRateSigmas = new ArrayList<Double>();
	private ArrayList<Double> bkMMaxMeans = new ArrayList<Double>();
	private ArrayList<Double> bkMMaxSigmas = new ArrayList<Double>();
	private ArrayList<Double> bkMMins = new ArrayList<Double>();
	private ArrayList<IncrementalMagFreqDist> bkMagFD = new ArrayList<IncrementalMagFreqDist>();
	private ArrayList<Double> bkRake = new ArrayList<Double>(); 
	private ArrayList<Double> bkDip = new ArrayList<Double>(); 
	private ArrayList<ProbEqkSource> allSources = new ArrayList<ProbEqkSource>();
	private GutenbergRichterMagFreqDist backgroundMagDist;
	private GriddedRegion backgroundRegion;
		
	private ArrayList<String> sourceNames = new ArrayList<String>();
	private ArrayList<String> sourceTectonicTypes = new ArrayList<String>();
	private ArrayList<String> sourceFaultTypes = new ArrayList<String>();
	private ArrayList<Double> sourceLengthMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceLengthSigmas = new ArrayList<Double>();
	private ArrayList<Double> sourceDipMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceDipSigmas = new ArrayList<Double>();
	private ArrayList<Double> sourceDipDirs = new ArrayList<Double>();
	private ArrayList<Double> sourceRakes = new ArrayList<Double>();
	private ArrayList<Double> sourceTopMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceTopMins = new ArrayList<Double>();
	private ArrayList<Double> sourceTopMaxs = new ArrayList<Double>();
	private ArrayList<Double> sourceBottomMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceBottomSigmas = new ArrayList<Double>();
	private ArrayList<Double> sourceSlipRateMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceSlipRateSigmas = new ArrayList<Double>();
	private ArrayList<Double> sourceCouplingCoeffMeans = new ArrayList<Double>();
	private ArrayList<Double> sourceCouplingCoeffSigmas = new ArrayList<Double>();
	private ArrayList<Double> sourceMedianMags = new ArrayList<Double>();
	private ArrayList<Double> sourceMedianAnnualRates = new ArrayList<Double>();
	
	//Tectonic types
	private ArrayList<TectonicRegionType> tectonicRegionTypes;
	private final static String ACTIVE_SHALLOW = "ACTIVE_SHALLOW";
	private final static String SUBDUCTION_INTERFACE = "SUBDUCTION_INTERFACE";
	private final static String SUBDUCTION_SLAB = "SUBDUCTION_SLAB";
	private final static String VOLCANIC = "VOLCANIC";
	private int numActiveShallow=0;
	private int numSubSlab=0;
	private int numSubInterface=0;
	private int numVolcanic=0;
	//Fault types (for active shallow mag scaling relations)
	private final static String OTHER_CRUSTAL_FAULTING = "OTHER_CRUSTAL_FAULTING";
	private final static String PLATE_BOUNDARY = "PLATE_BOUNDARY";
	private final static String NORMAL_FAULTING = "NORMAL_FAULTING";
	//mag scaling relationships
	private static final HanksBakun2002_MagAreaRel hanksBakunMwAreaScaling = new HanksBakun2002_MagAreaRel();

	private ArrayList<EvenlyGriddedSurface> sourceGriddedSurface = new ArrayList<EvenlyGriddedSurface>();
	//mu - shear stiffness in N/m^2
	private static final double mu = 3.0*Math.pow(10.,10.);

	public NewZealandERF2010(){	

		//create the timespan object with start time and duration in years
		timeSpan = new TimeSpan(TimeSpan.NONE,TimeSpan.YEARS);
		timeSpan.addParameterChangeListener(this);
		timeSpan.setDuration(1);
		
		createFaultSurfaces();
		createBackRegion();
		initAdjParams();
		// Create adjustable parameter list
		createParamList();
		
		makeTectonicRegionList();
	}


	/*
	 * Initialize the adjustable parameters
	 */
	private void initAdjParams(){	
		//Background seismicity
		ArrayList<String> backSeisOptionsStrings = new ArrayList<String>();
		backSeisOptionsStrings.add(FAULT_AND_BACK_SEIS);
		backSeisOptionsStrings.add(FAULT_SEIS_ONLY);
		backSeisOptionsStrings.add(BACK_SEIS_ONLY);
		backSeisParam = new StringParameter(FAULT_AND_BACK_SEIS_NAME,backSeisOptionsStrings,FAULT_AND_BACK_SEIS);
		backSeisParam.addParameterChangeListener(this);
		//Consideration of epistemic unceratinties - overall
		epistemicParam = new BooleanParameter(EPISTEMIC_PARAM_NAME, EPISTEMIC_PARAM_DEFAULT);
		epistemicParam.setInfo(EPISTEMIC_PARAM_INFO);
		epistemicParam.addParameterChangeListener(this);
		//uncertainties in length, top, bottom
		epistemicGeometryParam = new BooleanParameter(EPISTEMIC_GEOMETRY_PARAM_NAME, EPISTEMIC_GEOMETRY_PARAM_DEFAULT);
		epistemicGeometryParam.setInfo(EPISTEMIC_GEOMETRY_PARAM_INFO);
		epistemicGeometryParam.addParameterChangeListener(this);
		//uncertainty in dip
		epistemicDipParam = new BooleanParameter(EPISTEMIC_DIP_PARAM_NAME, EPISTEMIC_DIP_PARAM_DEFAULT);
		epistemicDipParam.setInfo(EPISTEMIC_DIP_PARAM_INFO);
		epistemicDipParam.addParameterChangeListener(this);
		//uncertainty in slip rate and coupling
		epistemicSlipParam = new BooleanParameter(EPISTEMIC_SLIP_PARAM_NAME, EPISTEMIC_SLIP_PARAM_DEFAULT);
		epistemicSlipParam.setInfo(EPISTEMIC_SLIP_PARAM_INFO);
		epistemicSlipParam.addParameterChangeListener(this);
		//uncertainty in magnitude scaling
		epistemicMagScalingParam = new BooleanParameter(EPISTEMIC_MAGSCALING_PARAM_NAME, EPISTEMIC_MAGSCALING_PARAM_DEFAULT);
		epistemicMagScalingParam.setInfo(EPISTEMIC_MAGSCALING_PARAM_INFO);
		epistemicMagScalingParam.addParameterChangeListener(this);
		//proportion of magnitude scaling uncertainty considered as correlated between faults
		epistemicMagScalingCorrelationParam = new DoubleParameter(EPISTEMIC_MAGSCALINGCORRELATION_PARAM_NAME, EPISTEMIC_MAGSCALINGCORRELATION_PARAM_DEFAULT);
		epistemicMagScalingCorrelationParam.setInfo(EPISTEMIC_MAGSCALINGCORRELATION_PARAM_INFO);
		epistemicMagScalingCorrelationParam.addParameterChangeListener(this);
		//proportion of magnitude scaling uncertainty considered epistemic
		epistemicMagScalingUncertaintyProportionParam = new DoubleParameter(EPISTEMIC_MAGSCALINGPROPORTION_PARAM_NAME, EPISTEMIC_MAGSCALINGPROPORTION_PARAM_DEFAULT);
		epistemicMagScalingUncertaintyProportionParam.setInfo(EPISTEMIC_MAGSCALINGPROPORTION_PARAM_INFO);
		epistemicMagScalingUncertaintyProportionParam.addParameterChangeListener(this);
		
	}
	
	/*
	 * Set the adjustable parameters - if passed in from Epistemic version
	 */
	public void setAdjParams(ParameterList adjParamVals){	
		//Set iterator 
		Iterator<String> it = adjParamVals.getParameterNamesIterator();
		while(it.hasNext()) {
			String paramName = it.next();
			if(paramName.equalsIgnoreCase(FAULT_AND_BACK_SEIS_NAME)) {
				String paramValueToSet = (String)adjParamVals.getValue(paramName);
				StringParameter Param = (StringParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_GEOMETRY_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_DIP_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_SLIP_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_MAGSCALING_PARAM_NAME)) {
				Boolean paramValueToSet = (Boolean)adjParamVals.getValue(paramName);
				BooleanParameter Param = (BooleanParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_MAGSCALINGCORRELATION_PARAM_NAME)) {
				Double paramValueToSet = (Double)adjParamVals.getValue(paramName);
				DoubleParameter Param = (DoubleParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			else if(paramName.equalsIgnoreCase(EPISTEMIC_MAGSCALINGPROPORTION_PARAM_NAME)) {
				Double paramValueToSet = (Double)adjParamVals.getValue(paramName);
				DoubleParameter Param = (DoubleParameter)adjustableParams.getParameter(paramName);
				Param.setValue(paramValueToSet);
			}
			
		}
		
	}


	/**
	 * Make Background sources
	 */

	private void createBackRegion(){
		try {
			
			//TODO : have yet to implement epistemic uncertainty random generation here yet
			ArrayList<String> fileLines = FileUtils.loadJarFile(BG_FILE_NAME);
			int size = fileLines.size();
			int j=4;
			String sourceName = fileLines.get(j);
			StringTokenizer st = new StringTokenizer(sourceName);
			String srcCode = st.nextToken();
			int srcCodeLength = srcCode.length();
			String sourceNameString = sourceName.substring(srcCodeLength);

			for(int i=5;i<size;++i){ 
				++numBkSources;
				String magDistInfo  = fileLines.get(i);
				st = new StringTokenizer(magDistInfo);
				double aVal = Double.parseDouble(st.nextToken().trim());
				double bVal = Double.parseDouble(st.nextToken().trim());
				double minMag = Double.parseDouble(st.nextToken().trim());
				double maxMag = Double.parseDouble(st.nextToken().trim());
				int numMag = Integer.parseInt(st.nextToken().trim());
				double totCumRate = Double.parseDouble(st.nextToken().trim());
				double lat = Double.parseDouble(st.nextToken().trim());
				double lon = Double.parseDouble(st.nextToken().trim());
				if (lon>180.0) lon=lon-360.0; //Ensure that in range [-180,180]
				double depth = Double.parseDouble(st.nextToken().trim());
				double rake  = Double.parseDouble(st.nextToken().trim());
				double dip   = Double.parseDouble(st.nextToken().trim());
				String tectonicType = st.nextToken();
				
				
				IncrementalMagFreqDist backgroundMagDist = new GutenbergRichterMagFreqDist(bVal,totCumRate,minMag,maxMag,numMag);
				Location bckLocation = new Location(lat,lon,depth);
				
				this.bkSourceNames.add("backgroundSource"+numBkSources);
				this.bkSourceTectonicTypes.add(tectonicType);
				incrementTectonicTypeCounters(tectonicType);
				this.bkSourceLocation.add(bckLocation);
				this.bkMagFD.add(backgroundMagDist);
				this.bkRake.add(rake);
				this.bkDip.add(dip);
				this.bkMMins.add(minMag);
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private void mkBackRegion(){
		for(int srcIndex=0; srcIndex<bkSourceNames.size(); ++srcIndex) {
				
			PointEqkSource rupSource = new PointEqkSource(this.bkSourceLocation.get(srcIndex),this.bkMagFD.get(srcIndex),
					timeSpan.getDuration(),this.bkRake.get(srcIndex),this.bkDip.get(srcIndex),this.bkMMins.get(srcIndex));
			//Tectonic type of source
			String tectType = this.bkSourceTectonicTypes.get(srcIndex);
			setTectonicTypeOfSource(rupSource,tectType);
			
			
			allSources.add(rupSource);
		}
	}

	/**
	 * 
	 * Read the file and create fault surfaces
	 *
	 */
	private void createFaultSurfaces(){
		try {
			ArrayList<String> fileLines = FileUtils.loadJarFile(FAULT_SOURCE_FILENAME);
			int size = fileLines.size();
			for(int i=15;i<size;++i){ 
				String sourceName = fileLines.get(i);
				if(sourceName.trim().equals(""))
					continue;
				StringTokenizer st = new StringTokenizer(sourceName);
				String srcCode = st.nextToken();
				++i;
				
				String sourceTectFltType = fileLines.get(i);
				st = new StringTokenizer(sourceTectFltType);
				String tectonicType = st.nextToken();
				String faultType = st.nextToken();	
				++i;
				
				String sourceLengthInfo = fileLines.get(i);
				st = new StringTokenizer(sourceLengthInfo);
				double lengthMean = Double.parseDouble(st.nextToken().trim());
				double lengthSigma = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceDipInfo = fileLines.get(i);
				st = new StringTokenizer(sourceDipInfo);
				double dipMean = Double.parseDouble(st.nextToken().trim());
				double dipSigma = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceDipDirInfo = fileLines.get(i);
				st = new StringTokenizer(sourceDipDirInfo);
				double dipDir = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceRakeInfo = fileLines.get(i);
				st = new StringTokenizer(sourceRakeInfo);
				double rake = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceBottomInfo = fileLines.get(i);
				st = new StringTokenizer(sourceBottomInfo);
				double bottomMean = Double.parseDouble(st.nextToken().trim());
				double bottomSigma = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceTopInfo = fileLines.get(i);
				st = new StringTokenizer(sourceTopInfo);
				double topMean = Double.parseDouble(st.nextToken().trim());
				double topMin = Double.parseDouble(st.nextToken().trim());
				double topMax = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String sourceSlipRateInfo = fileLines.get(i);
				st = new StringTokenizer(sourceSlipRateInfo);
				double slipRateMean = Double.parseDouble(st.nextToken().trim());
				double slipRateSigma = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String couplingCoeffInfo = fileLines.get(i);
				st = new StringTokenizer(couplingCoeffInfo);
				double couplingCoeffMean = Double.parseDouble(st.nextToken().trim());
				double couplingCoeffSigma = Double.parseDouble(st.nextToken().trim());
				++i;
				
				String medianMwRivalues = fileLines.get(i);
				st = new StringTokenizer(medianMwRivalues);
				double medianMag = Double.parseDouble(st.nextToken().trim());
				double medianRI = Double.parseDouble(st.nextToken().trim());
				double medianAnnualRate = 1./medianRI;
				++i;
				
				String numSourceLoc = fileLines.get(i);
				st = new StringTokenizer(numSourceLoc);
				int numSourceLocations = Integer.parseInt(st.nextToken().trim());			
				FaultTrace fltTrace = new FaultTrace(srcCode);
				int numLinesDone = i;
				for(i=i+1;i<=(numLinesDone+numSourceLocations);++i){
					String location = fileLines.get(i);
					st = new StringTokenizer(location);
					double lon = Double.parseDouble(st.nextToken().trim());
					double lat = Double.parseDouble(st.nextToken().trim());
					fltTrace.add(new Location(lat,lon));
				}
				
				EvenlyGriddedSurface surface = new StirlingGriddedSurface(fltTrace,dipMean,topMean,bottomMean,DEFAULT_GRID_SPACING,dipDir);
				sourceNames.add(srcCode);
				this.sourceTectonicTypes.add(tectonicType);
				incrementTectonicTypeCounters(tectonicType);
				this.sourceFaultTypes.add(faultType);
				this.sourceLengthMeans.add(lengthMean);
				this.sourceLengthSigmas.add(lengthSigma);
				this.sourceDipMeans.add(dipMean);
				this.sourceDipSigmas.add(dipSigma);
				this.sourceDipDirs.add(dipDir);
				this.sourceRakes.add(rake);
				this.sourceDipMeans.add(dipMean);
				this.sourceDipSigmas.add(dipSigma);
				this.sourceTopMeans.add(topMean);
				this.sourceTopMins.add(topMin);
				this.sourceTopMaxs.add(topMax);
				this.sourceBottomMeans.add(bottomMean);
				this.sourceBottomSigmas.add(bottomSigma);
				this.sourceSlipRateMeans.add(slipRateMean);
				this.sourceSlipRateSigmas.add(slipRateSigma);
				this.sourceCouplingCoeffMeans.add(couplingCoeffMean);
				this.sourceCouplingCoeffSigmas.add(couplingCoeffSigma);
				this.sourceMedianMags.add(medianMag);
				this.sourceMedianAnnualRates.add(medianAnnualRate);
				this.sourceGriddedSurface.add(surface);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Make Fault Sources
	 *
	 */
	private void mkFaultSources() {
		//Epistemic parameters that are considered perfectly correlated between all faults
		//Magnitude scaling relation uncertainty
		NormalEstimate zrand = new NormalEstimate(0, 1, -2, 2); //normally distributed random number between [-2,2]
		double z_plateBoundary = zrand.getRandomValue();
		double z_otherCrustal = zrand.getRandomValue();
		double z_subductionInterface = zrand.getRandomValue();
		double z_volcanic = zrand.getRandomValue();

		//create random fault properties
		for(int srcIndex=0; srcIndex<sourceNames.size(); ++srcIndex) {
			EvenlyGriddedSurface surface = this.sourceGriddedSurface.get(srcIndex);
			
			//Parameters that need to be init irrespective of whether epistemic uncertainties
			//are considered or not
			double meanMag_epistemic, sigmaMag_aleatory, momentRate;
			String tectType = this.sourceTectonicTypes.get(srcIndex);
			String fltType = this.sourceFaultTypes.get(srcIndex);
			
			//Are epistemic uncertainties considered for various params?
			Boolean epiConsider = (Boolean)epistemicParam.getValue();
			Boolean epiGeometry = (Boolean)epistemicGeometryParam.getValue();
			Boolean epiDip = (Boolean)epistemicDipParam.getValue();
			Boolean epiSlip = (Boolean)epistemicSlipParam.getValue();
			Boolean epiMagScaling = (Boolean)epistemicMagScalingParam.getValue();
			Double epiMagScalingCorrelation = (Double)epistemicMagScalingCorrelationParam.getValue();
			Double epiMagScalingUncertaintyProportion = (Double)epistemicMagScalingUncertaintyProportionParam.getValue();
			
			if (epiConsider) {
				//get random length - normal distribution
				double meanFltLength = this.sourceLengthMeans.get(srcIndex);
				double sigmaFltLength = this.sourceLengthSigmas.get(srcIndex);
				double z_fltLength = zrand.getRandomValue();
				double length;
				if (epiGeometry) {
					length = meanFltLength + sigmaFltLength*z_fltLength;
				} else {
					length = meanFltLength;
				}
				
				//get random top for all faults - uniform distribution
				double meanFltTop = this.sourceTopMeans.get(srcIndex);
				double minFltTop = this.sourceTopMins.get(srcIndex);
				double maxFltTop = this.sourceTopMins.get(srcIndex);
				double top;
				if (epiGeometry) {
					top =  minFltTop + (maxFltTop - minFltTop)*Math.random();
				} else {
					top = meanFltTop;
				}
				
				//get random bottom for all faults - normal distribution
				double meanFltBottom = this.sourceBottomMeans.get(srcIndex);
				double sigmaFltBottom = this.sourceBottomSigmas.get(srcIndex);
				double z_fltBottom = zrand.getRandomValue();
				double bottom;
				if (epiGeometry) {
					bottom = meanFltBottom + sigmaFltBottom*z_fltBottom;
				} else {
					bottom = meanFltBottom;
				}
					
				//get random dip - normal distribution
				double meanFltDip = this.sourceDipMeans.get(srcIndex);
				double sigmaFltDip = this.sourceDipSigmas.get(srcIndex);
				double z_fltDip = zrand.getRandomValue();
				double dip;
				if (epiDip) {
					dip = meanFltDip + sigmaFltDip*z_fltDip;
				} else {
					dip = meanFltDip;
				}
				
				//get random slip rate - normal distribution
				double meanFltSlipRate = this.sourceSlipRateMeans.get(srcIndex);
				double sigmaFltSlipRate = this.sourceSlipRateSigmas.get(srcIndex);
				double z_slipRate = zrand.getRandomValue();
				double sliprate;
				if (epiSlip) {
					sliprate = meanFltSlipRate + sigmaFltSlipRate*z_slipRate;
				} else {
					sliprate = meanFltSlipRate;
				}
				
				//get random coupling coefficient - normal distribution
				double meanCouplingCoeff = this.sourceCouplingCoeffMeans.get(srcIndex);
				double sigmaCouplingCoeff = this.sourceCouplingCoeffSigmas.get(srcIndex);
				double z_couplingCoeff = zrand.getRandomValue();
				double coupCoeff;
				if (epiSlip) {
					coupCoeff = meanCouplingCoeff + sigmaCouplingCoeff*z_couplingCoeff;
				} else {
					coupCoeff = meanCouplingCoeff;
				}
					
				//compute fault area
				double faultWidth = (bottom-top)/Math.sin(dip*Math.PI/180);
				double faultArea = faultWidth*length;
				
				//get mean and sigma Mw from Mag-length scaling relationship
				double meanMag, sigmaMag, z_faultType;
				if (tectType.equals(ACTIVE_SHALLOW)) {
					if (fltType.equals(PLATE_BOUNDARY)) {
						//Use Hanks and Bakun MwScaling relation
						meanMag = hanksBakunMwAreaScaling.getMedianMag(faultArea);
						sigmaMag = 0.22;
						z_faultType=z_plateBoundary;
					} 
					else if (fltType.equals(OTHER_CRUSTAL_FAULTING)) { 
						//Other active shallow crustal - Stirling et al (2008) Mw scaling
						meanMag = 4.18 + (2./3.)*Math.log10(faultWidth) + (4./3.)*Math.log10(length);
						sigmaMag = 0.18;
						z_faultType=z_otherCrustal;
					} 
					else if (fltType.equals(NORMAL_FAULTING)) {
						//Use Villamor et al (2007)
						meanMag = 3.39 + (4./3.)*Math.log10(faultArea);
						sigmaMag = 0.195;
						z_faultType=z_volcanic;
					}
					else throw new RuntimeException("The fault type is not supported");
					
					
				} else if (tectType.equals(VOLCANIC)) {
					//Use Villamor et al (2007)
					meanMag = 3.39 + (4./3.)*Math.log10(faultArea);
					sigmaMag = 0.195;
					z_faultType=z_volcanic;
					
				} else if(tectType.equals(SUBDUCTION_INTERFACE)) {
					//Use Strasser et al (2010)
					meanMag = 4.441 + 0.846*Math.log10(faultArea);
					sigmaMag = 0.286;
					z_faultType=z_subductionInterface;
				} 
				else throw new RuntimeException("The tectonic region type is not supported");
				
				double sigmaMag_epistemic;
				if (epiMagScaling) {
					//Now obtain the proportion of magnitude uncertainty as aleatory and epistemic
					sigmaMag_epistemic = sigmaMag*Math.sqrt(epiMagScalingUncertaintyProportion);
					sigmaMag_aleatory = sigmaMag*Math.sqrt(1-epiMagScalingUncertaintyProportion);
					if (sigmaMag_aleatory<0.01)  //ensure that not zero
						sigmaMag_aleatory=0.01;
				} else { //No magnitude scaling uncertainty considered
					sigmaMag_epistemic=0.0;
					sigmaMag_aleatory=0.01;
				}
				
				//Now compute a random z_fault number for magnitude (epistemic) uncertainty	
				double z_fault = zrand.getRandomValue();
				//compute the random mean magnitude for this fault due to epistemic uncertainty
				//Proportion of the uncertainty is assumed correlated with different faults of the same type
				double rho=epiMagScalingCorrelation;
				meanMag_epistemic = meanMag + sigmaMag_epistemic*(Math.sqrt(rho)*z_faultType+Math.sqrt(1.-rho)*z_fault);
				//compute moment rate
				momentRate = mu*faultArea*(sliprate*1000.)*coupCoeff;  //convert slip rate to m/yr from mm/yr
				
			} else {  //Epistemic uncertainties arent considered 
				//Just use median values of Mw and AnnualRate
				meanMag_epistemic = this.sourceMedianMags.get(srcIndex);
				//Now round the meanMag to the increment used for magnitude discretization
				meanMag_epistemic = Math.round(meanMag_epistemic/magIncrement)*magIncrement;
				sigmaMag_aleatory = 0.01; //Cannot be zero so make negligible value
				double meanAnnualRate = this.sourceMedianAnnualRates.get(srcIndex);
				double meanMomentMag = Math.pow(10.,9.05+1.5*meanMag_epistemic);
				momentRate = meanAnnualRate*meanMomentMag;
			}
			
			//assign fault rupture properties		
			IncrementalMagFreqDist magDist = new GaussianMagFreqDist(MIN_MAG, MAX_MAG, NUM_MAGS,
					meanMag_epistemic, sigmaMag_aleatory, momentRate);
				
			FaultRuptureSource rupSource = new FaultRuptureSource(magDist,surface,sourceRakes.get(srcIndex),timeSpan.getDuration());
			rupSource.setName(sourceNames.get(srcIndex));
			setTectonicTypeOfSource(rupSource,tectType);
			//Set the hypocenter - for hazard calcs with directivity (where hypCol and hypRow are defined earlier)
			//int hypRow, hypCol;  //These should have been defined earlier so not needed here
			//Location hypLoc = surface.getLocation(hypRow, hypCol);  //BB added code for varun example
			//get the number of rupture
			//int numRup = rupSource.getNumRuptures();
			//for (int rupIndex=0; rupIndex<numRup; rupIndex++) {
			//	rupSource.getRupture(rupIndex).setHypocenterLocation(hypLoc);  //BB added code for varun example
			//}
			
			allSources.add(rupSource);
		}
	}
	
	/**
	 * This method takes the tectonic type and adds it to a source counter 
	 * tectType - the string defining the tectonic type of the source
	 */
	public void incrementTectonicTypeCounters(String tectType) {
		if (tectType.equals(ACTIVE_SHALLOW)) 
			numActiveShallow += 1;
		else if (tectType.equals(VOLCANIC)) 
			numVolcanic += 1;
		else if(tectType.equals(SUBDUCTION_INTERFACE)) 
			numSubInterface += 1;
		else if(tectType.equals(SUBDUCTION_SLAB))
			numSubSlab += 1;
		else throw new RuntimeException("The tectonic region type is not supported");
	}
	
	/**
	 * This method assigns the tectonic type to a FaultRuptureSource
	 * @param rupSource - the source to assign a tect type to
	 * @param tectType - the tectonic type as a string
	 */
	public void setTectonicTypeOfSource(FaultRuptureSource rupSource,String tectType) {
		if (tectType.equals(ACTIVE_SHALLOW)) 
			rupSource.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
		else if (tectType.equals(VOLCANIC)) 
			rupSource.setTectonicRegionType(TectonicRegionType.VOLCANIC);
		else if (tectType.equals(SUBDUCTION_INTERFACE)) 
			rupSource.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);
		else if (tectType.equals(SUBDUCTION_SLAB)) 
			rupSource.setTectonicRegionType(TectonicRegionType.SUBDUCTION_SLAB);
		else throw new RuntimeException("The tectonic region type is not supported");
	}
	
	/**
	 * This method assigns the tectonic type to a PointEqkSource
	 * @param rupSource - the source to assign a tect type to
	 * @param tectType - the tectonic type as a string
	 */
	public void setTectonicTypeOfSource(PointEqkSource rupSource,String tectType) {
		if (tectType.equals(ACTIVE_SHALLOW)) 
			rupSource.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
		else if (tectType.equals(VOLCANIC)) 
			rupSource.setTectonicRegionType(TectonicRegionType.VOLCANIC);
		else if (tectType.equals(SUBDUCTION_INTERFACE)) 
			rupSource.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);
		else if (tectType.equals(SUBDUCTION_SLAB)) 
			rupSource.setTectonicRegionType(TectonicRegionType.SUBDUCTION_SLAB);
		else throw new RuntimeException("The tectonic region type is not supported");
	}
	
	/**
	 * This method makes the tectonic regions list
	 */
	public void makeTectonicRegionList() {
		tectonicRegionTypes = new ArrayList<TectonicRegionType>();
		if(numActiveShallow>0) tectonicRegionTypes.add(TectonicRegionType.ACTIVE_SHALLOW);
		if(numVolcanic>0) tectonicRegionTypes.add(TectonicRegionType.VOLCANIC);
		if(numSubSlab>0) tectonicRegionTypes.add(TectonicRegionType.SUBDUCTION_SLAB);
		if(numSubInterface>0) tectonicRegionTypes.add(TectonicRegionType.SUBDUCTION_INTERFACE);
	}
	
	/**
	 * This method copies the tectonicRegionTypes for using in the epistemic uncertainty version
	 */
	public ArrayList<TectonicRegionType> getTectonicRegionTypes() {
		return tectonicRegionTypes;
	}
	
	/**
	 * This put parameters in the ParameterList (depending on settings).
	 * This could be smarter in terms of not showing parameters if certain settings
	 */
	protected void createParamList() {
		
		adjustableParams = new ParameterList();
		
		//Parameters always displayed
		adjustableParams.addParameter(backSeisParam);
		adjustableParams.addParameter(epistemicParam);
		//If Epistemic uncertainties considered display other values
		if (epistemicParam.getValue()) {
			adjustableParams.addParameter(epistemicGeometryParam);
			adjustableParams.addParameter(epistemicDipParam);
			adjustableParams.addParameter(epistemicSlipParam);
			adjustableParams.addParameter(epistemicMagScalingParam);
			if (epistemicMagScalingParam.getValue()) {
				adjustableParams.addParameter(epistemicMagScalingCorrelationParam);
				adjustableParams.addParameter(epistemicMagScalingUncertaintyProportionParam);
			}
		}
		
	}

	/**
	 *  This is the main function of this interface. Any time a control
	 *  paramater or independent paramater is changed by the user in a GUI this
	 *  function is called, and a paramater change event is passed in.
	 *
	 *  This sets the flag to indicate that the sources need to be updated
	 *
	 * @param  event
	 */
	public void parameterChange(ParameterChangeEvent event) {
		this.parameterChangeFlag = true;
		// Create adjustable parameter list
		createParamList();
	}

	@Override
	public int getNumSources() {
		return allSources.size();
	}

	@Override
	public ProbEqkSource getSource(int source) {
		// TODO Auto-generated method stub
		return (ProbEqkSource)allSources.get(source);
	}

	@Override
	public ArrayList getSourceList() {
		// TODO Auto-generated method stub
		return allSources;
	}

	public String getName() {
		// TODO Auto-generated method stub
		return NAME;
	}
	
	/**
	 * Update the fault Sources with the change in duration.
	 */
	public void updateForecast() {
		// make sure something has changed
			allSources = new ArrayList<ProbEqkSource>();
			String faltBkgSeisVal = (String)backSeisParam.getValue();
			if (faltBkgSeisVal.equals(FAULT_AND_BACK_SEIS)) {
				mkFaultSources();
				mkBackRegion();
			} else if (faltBkgSeisVal.equals(FAULT_SEIS_ONLY))
				mkFaultSources();
			else if (faltBkgSeisVal.equals(BACK_SEIS_ONLY))
				mkBackRegion();

			makeTectonicRegionList();
	}
	
	@Override
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {
		return tectonicRegionTypes;
	}
	
	
	public static void main(String[] args) {
		
		//Declare and define variables
		
		
		//Instantiate NewZealand_ERF object
		NewZealandERF2010 nzerf = new NewZealandERF2010();
		nzerf.parameterChangeFlag = true;
		nzerf.updateForecast();
		}
	
}
