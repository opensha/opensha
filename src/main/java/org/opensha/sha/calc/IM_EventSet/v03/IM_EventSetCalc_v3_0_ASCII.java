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

package org.opensha.sha.calc.IM_EventSet.v03;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.OriginalModWriter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.GEM1.GEM1_CEUS_ERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF1.WGCEP_UCERF1_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.AS_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BC_2004_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BS_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CS_2005_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.GouletEtAl_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SEA_1999_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SadighEtAl_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;
import org.opensha.sha.util.SiteTranslator;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.erf.mean.MeanUCERF3.Presets;



/**
 * <p>Title: IM_EventSetCalc</p>
 *
 * <p>Description: This class computes the Mean and Sigma for any Attenuation
 * supported and any IMT supported by these AttenuationRelationships.
 * Sites information is read from a input file.
 * </p>
 *
 * @author Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */
public class IM_EventSetCalc_v3_0_ASCII extends IM_EventSetCalc_v3_0
implements ParameterChangeWarningListener {

	protected LocationList locList;

	protected ERF forecast;

	//supported Attenuations
	protected ArrayList<ScalarIMR> chosenAttenuationsList;

	//some static IMT names
	protected ArrayList<String> supportedIMTs;

	protected String inputFileName = "MeanSigmaCalc_InputFile.txt";
	protected String dirName = "MeanSigma";
	
	private File outputDir;
	
	private OrderedSiteDataProviderList providers;
	
	private ArrayList<ArrayList<SiteDataValue<?>>> userDataVals;

	/**
	 *  ArrayList that maps picklist attenRel string names to the real fully qualified
	 *  class names
	 */
	private static ArrayList<String> attenRelClasses = new ArrayList<String>();
	private static ArrayList<String> imNames = new ArrayList<String>();

	static {
//		imNames.add(CY_2006_AttenRel.NAME);
//		attenRelClasses.add(CY_2006_AttenRel.class.getName());
//		imNames.add(CY_2008_AttenRel.NAME);
//		attenRelClasses.add(CY_2008_AttenRel.class.getName());
//		imNames.add(CB_2006_AttenRel.NAME);
//		attenRelClasses.add(CB_2006_AttenRel.class.getName());
//		imNames.add(CB_2008_AttenRel.NAME);
//		attenRelClasses.add(CB_2008_AttenRel.class.getName());
//		imNames.add(BA_2006_AttenRel.NAME);
//		attenRelClasses.add(BA_2006_AttenRel.class.getName());
//		imNames.add(BA_2008_AttenRel.NAME);
//		attenRelClasses.add(BA_2008_AttenRel.class.getName());
//		imNames.add(CS_2005_AttenRel.NAME);
//		attenRelClasses.add(CS_2005_AttenRel.class.getName());
//		imNames.add(BJF_1997_AttenRel.NAME);
//		attenRelClasses.add(BJF_1997_AttenRel.class.getName());
//		imNames.add(AS_1997_AttenRel.NAME);
//		attenRelClasses.add(AS_1997_AttenRel.class.getName());
//		imNames.add(AS_2008_AttenRel.NAME);
//		attenRelClasses.add(AS_2008_AttenRel.class.getName());
//		imNames.add(Campbell_1997_AttenRel.NAME);
//		attenRelClasses.add(Campbell_1997_AttenRel.class.getName());
//		imNames.add(SadighEtAl_1997_AttenRel.NAME);
//		attenRelClasses.add(SadighEtAl_1997_AttenRel.class.getName());
//		imNames.add(Field_2000_AttenRel.NAME);
//		attenRelClasses.add(Field_2000_AttenRel.class.getName());
//		imNames.add(Abrahamson_2000_AttenRel.NAME);
//		attenRelClasses.add(Abrahamson_2000_AttenRel.class.getName());
//		imNames.add(CB_2003_AttenRel.NAME);
//		attenRelClasses.add(CB_2003_AttenRel.class.getName());
//		imNames.add(BS_2003_AttenRel.NAME);
//		attenRelClasses.add(BS_2003_AttenRel.class.getName());
//		imNames.add(BC_2004_AttenRel.NAME);
//		attenRelClasses.add(BC_2004_AttenRel.class.getName());
//		imNames.add(GouletEtAl_2006_AttenRel.NAME);
//		attenRelClasses.add(GouletEtAl_2006_AttenRel.class.getName());
//		imNames.add(ShakeMap_2003_AttenRel.NAME);
//		attenRelClasses.add(ShakeMap_2003_AttenRel.class.getName());
//		imNames.add(SEA_1999_AttenRel.NAME);
//		attenRelClasses.add(SEA_1999_AttenRel.class.getName());
		for (AttenRelRef ref : AttenRelRef.get(ServerPrefUtils.SERVER_PREFS)) {
			imNames.add(ref.getName());
			attenRelClasses.add(ref.getAttenRelClass().getName());
		}
	}

	public IM_EventSetCalc_v3_0_ASCII(String inpFile,String outDir) {
		inputFileName = inpFile;
		dirName = outDir ;
		outputDir = new File(dirName);
		
		providers = OrderedSiteDataProviderList.createCompatibilityProviders(false);
		// disable non-Vs30 providers
		for (int i=0; i<providers.size(); i++) {
			if (!providers.getProvider(i).getDataType().equals(SiteData.TYPE_VS30))
				providers.setEnabled(i, false);
		}
	}

	public void parseFile() throws FileNotFoundException,IOException{

		ArrayList<String> fileLines = null;
		
		logger.log(Level.INFO, "Parsing input file: " + inputFileName);

		fileLines = FileUtils.loadFile(inputFileName);
		
		if (fileLines.size() == 0) {
			throw new RuntimeException("Input file empty or doesn't exist! " + inputFileName);
		}

		int j = 0;
		int numIMRdone=0;
		int numIMRs=0;
		int numIMTdone=0;
		int numIMTs=0;
		int numSitesDone= 0;
		int numSites =0;
		for(int i=0; i<fileLines.size(); ++i) {
			String line = ((String)fileLines.get(i)).trim();
			// if it is comment skip to next line
			if(line.startsWith("#") || line.equals("")) continue;
			if(j==0)getERF(line);
			if(j==1){
				toApplyBackGroud(line.trim());
			}
			if(j==2){
				double rupOffset = Double.parseDouble(line.trim());
				setRupOffset(rupOffset);
			}
			if(j==3)
				numIMRs = Integer.parseInt(line.trim());
			if(j==4){
				setIMR(line.trim());
				++numIMRdone;
				if(numIMRdone == numIMRs)
					++j;
				continue;
			}
			if(j==5)
				numIMTs = Integer.parseInt(line.trim());
			if(j==6){
				setIMT(line.trim());
				++numIMTdone;
				if (numIMTdone == numIMTs)
					++j;
				continue;
			}
			if(j==7)
				numSites = Integer.parseInt(line.trim());
			if(j==8){
				setSite(line.trim());
				++numSitesDone;
				if (numSitesDone == numSites)
					++j;
				continue;
			}
			++j;
		}
	}

	/**
	 * Gets the list of locations with their Wills Site Class values
	 * @param line String
	 */
	private void setSite(String line){
		if(locList == null)
			locList = new LocationList();
		if (userDataVals == null)
			userDataVals = new ArrayList<ArrayList<SiteDataValue<?>>>();
		StringTokenizer st = new StringTokenizer(line);
		int tokens = st.countTokens();
		if(tokens > 3 || tokens < 2){
			throw new RuntimeException("Must Enter valid Lat Lon in each line in the file");
		}
		double lat = Double.parseDouble(st.nextToken().trim());
		double lon = Double.parseDouble(st.nextToken().trim());
		Location loc = new Location(lat,lon);
		locList.add(loc);
		ArrayList<SiteDataValue<?>> dataVals = new ArrayList<SiteDataValue<?>>();
		String dataVal = null;
		if (tokens == 3) {
			dataVal = st.nextToken().trim();
		}
		if (SiteTranslator.wills_vs30_map.keySet().contains(dataVal)) {
			// this is a wills class
			dataVals.add(new SiteDataValue<String>(SiteData.TYPE_WILLS_CLASS,
					SiteData.TYPE_FLAG_MEASURED, dataVal));
		} else if (dataVal != null) {
			// Vs30 value
			try {
				double vs30 = Double.parseDouble(dataVal);
				dataVals.add(new SiteDataValue<Double>(SiteData.TYPE_VS30,
						SiteData.TYPE_FLAG_MEASURED, vs30));
			} catch (NumberFormatException e) {
//				e.printStackTrace();
				System.err.println("*** WARNING: Site Wills/Vs30 value unknown: " + dataVal);
			}
		}
		userDataVals.add(dataVals);
	}
	
//	/**
//	 * Sets the IMT from the string specification
//	 * 
//	 * @param imtLine
//	 * @param attenRel
//	 */
//	public static String getIMTForLine(String imtLine) {
//		StringTokenizer st = new StringTokenizer(imtLine);
//		int numTokens = st.countTokens();
//		String imt = st.nextToken().trim();
//		if (numTokens == 2) {
//			// this is SA
//			double period = Double.parseDouble(st.nextToken().trim());
//			int per10int = (int)(period * 10d + 0.5);
//			String per10str = per10int + "";
//			if (per10str.length() < 2)
//				per10str = "0" + per10str;
////			ParameterAPI imtParam = (ParameterAPI)attenRel.getIntensityMeasure();
////			imtParam.getIndependentParameter(PeriodParam.NAME).setValue(period);
//			imt += per10str;
//		}
//		System.out.println(imtLine + " => " + imt);
//		return imt;
//	}

	/**
	 * Gets the suported IMTs as String
	 * @param line String
	 */
	private void setIMT(String line){
		if(supportedIMTs == null)
			supportedIMTs = new ArrayList<String>();
		this.supportedIMTs.add(line.trim());
	}


	/**
	 * Creates the IMR instances and adds to the list of supported IMRs
	 * @param str String
	 */
	private void setIMR(String str) {
		if(chosenAttenuationsList == null)
			chosenAttenuationsList = new ArrayList<ScalarIMR>();
		String imrName = str.trim();
		//System.out.println(imrName);
		//System.out.println(imNames.get(1));
		int index = imNames.indexOf(imrName);
		createIMRClassInstance(attenRelClasses.get(index));
	}


	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand. For example, if you wanted to create a BJF_1997_AttenRel you can do
	 * it the normal way:<P>
	 *
	 * <code>BJF_1997_AttenRel imr = new BJF_1997_AttenRel()</code><p>
	 *
	 * If your not sure the user wants this one or AS_1997_AttenRel you can use this function
	 * instead to create the same class by:<P>
	 *
	 * <code>BJF_1997_AttenRel imr =
	 * (BJF_1997_AttenRel)ClassUtils.createNoArgConstructorClassInstance("org.opensha.sha.imt.attenRelImpl.BJF_1997_AttenRel");
	 * </code><p>
	 *
	 */
	protected void createIMRClassInstance(String AttenRelClassName) {
		try {
			Class listenerClass = ParameterChangeWarningListener.class;
			Object[] paramObjects = new Object[] {
					this};
			Class[] params = new Class[] {
					listenerClass};
			Class imrClass = Class.forName(AttenRelClassName);
			Constructor con = imrClass.getConstructor(params);
			AttenuationRelationship attenRel = (AttenuationRelationship) con.newInstance(paramObjects);
			if(attenRel.getName().equals(USGS_Combined_2004_AttenRel.NAME))
				throw new RuntimeException("Cannot use "+USGS_Combined_2004_AttenRel.NAME+" in calculation of Mean and Sigma");
			//setting the Attenuation with the default parameters
			attenRel.setParamDefaults();
			chosenAttenuationsList.add(attenRel);
		}
		catch (ClassCastException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		}
	}

	private void getERF(String line){
		String erfName = line.trim();
		logger.log(Level.CONFIG, "Attempting to identify ERF from name: " + erfName);
		if(erfName.equals(Frankel02_AdjustableEqkRupForecast.NAME))
			createFrankel02Forecast();
		else if (erfName.equals(WGCEP_UCERF1_EqkRupForecast.NAME))
			createUCERF1_Forecast();
		else if (erfName.equals(MeanUCERF2.NAME))
			createMeanUCERF2_Forecast();
		else if (erfName.equals(GEM1_CEUS_ERF.NAME))
			createGEM1_CEUS_Forecast();
		else if (erfName.startsWith("Mean UCERF3"))
			createMeanUCERF3_Forecast(erfName);
		else throw new RuntimeException ("Unsupported ERF");
		if (!(forecast instanceof MeanUCERF3))
			forecast.getTimeSpan().setDuration(1.0);
	}

	/**
	 * Creating the instance of the Frankel02 forecast
	 */
	private void createFrankel02Forecast(){
		logger.log(Level.CONFIG, "Creating Frankel02 ERF");
		forecast = new Frankel02_AdjustableEqkRupForecast();
	}

	/**
	 * Creating the instance of the UCERF1 Forecast
	 */
	private void createUCERF1_Forecast(){
		logger.log(Level.CONFIG, "Creating UCERF1 ERF");
		forecast = new WGCEP_UCERF1_EqkRupForecast();
		forecast.getAdjustableParameterList().getParameter(
				WGCEP_UCERF1_EqkRupForecast.TIME_DEPENDENT_PARAM_NAME).setValue(new Boolean(false));
	}

	/**
	 * Creating the instance of the UCERF2 - Single Branch Forecast
	 */
	private void createMeanUCERF2_Forecast(){
		logger.log(Level.CONFIG, "Creating UCERF2 ERF");
		forecast = new MeanUCERF2();
		forecast.getAdjustableParameterList().getParameter(
				UCERF2.PROB_MODEL_PARAM_NAME).setValue(UCERF2.PROB_MODEL_POISSON);
	}
	
	private void createGEM1_CEUS_Forecast(){
		logger.log(Level.CONFIG, "Creating GEM1 CEUS ERF");
		forecast = new GEM1_CEUS_ERF();
	}
	
	private void createMeanUCERF3_Forecast(String name) {
		name = name.trim();
		logger.log(Level.CONFIG, "Creating MeanUCERF3 ERF");
		MeanUCERF3.show_progress = false;
		MeanUCERF3 forecast = new MeanUCERF3();
		Presets preset;
		String args;
		if (name.startsWith("Mean UCERF3 FM3.1")) {
			preset = MeanUCERF3.Presets.FM3_1_BRANCH_AVG;
			args = name.substring("Mean UCERF3 FM3.1".length());
		} else if (name.startsWith("Mean UCERF3 FM3.2")) {
			preset = MeanUCERF3.Presets.FM3_2_BRANCH_AVG;
			args = name.substring("Mean UCERF3 FM3.2".length());
		} else {
			preset = MeanUCERF3.Presets.BOTH_FM_BRANCH_AVG;
			Preconditions.checkState(name.length() == "Mean UCERF3".length(),
					"Can't specify UCERF3-TD params for full model, must use individual Fault Model");
			args = "";
		}
		
		logger.log(Level.CONFIG, "MeanUCERF3 Preset: "+preset.name());
		
		forecast.setPreset(preset);
		
		if (!args.isEmpty()) {
			logger.log(Level.CONFIG, "Time dependent args: "+args);
			// time dependent
			args = args.trim().replaceAll("\t", " ");
			while (args.contains("  "))
				args = args.replaceAll("  ", " ");
			String[] split = args.split(" ");
			Preconditions.checkState(split.length == 1 || split.length == 2,
					"UCERF3-TD arguments: <start-year> [<duration>]");
			int startYear = Integer.parseInt(split[0]);
			double duration = 1d;
			if (split.length == 2)
				duration = Double.parseDouble(split[1]);
			
			logger.log(Level.CONFIG, "Start Year: "+startYear);
			logger.log(Level.CONFIG, "Duration: "+duration);
			
//			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
//			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
			forecast.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
			forecast.setParameter(AleatoryMagAreaStdDevParam.NAME, 0.0);
			forecast.setParameter(HistoricOpenIntervalParam.NAME, startYear-1875d);
			forecast.getTimeSpan().setStartTime(startYear);
			forecast.getTimeSpan().setDuration(duration);
		} else {
			forecast.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			forecast.getTimeSpan().setDuration(1d);
		}
		
		this.forecast = forecast;
	}

	private void toApplyBackGroud(String toApply){
		try {
			Parameter param = forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.BACK_SEIS_NAME);
			logger.log(Level.FINE, "Setting ERF background seismicity value: " + toApply);
			if (param instanceof StringParameter) {
				param.setValue(toApply);
			} else if (param instanceof IncludeBackgroundParam) {
				IncludeBackgroundOption val = IncludeBackgroundOption.valueOf(toApply.trim().toUpperCase());
				param.setValue(val);
			}
		} catch (ParameterException e) {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_NAME+"', ignoring setting.");
		}
		if(!(forecast instanceof MeanUCERF2)) {
			if (forecast.getAdjustableParameterList().containsParameter(
					Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_RUP_NAME)) { 	
				forecast.getAdjustableParameterList().getParameter(
						Frankel02_AdjustableEqkRupForecast.
						BACK_SEIS_RUP_NAME).setValue(Frankel02_AdjustableEqkRupForecast.
								BACK_SEIS_RUP_FINITE);
			}
		}

	}

	private void setRupOffset(double rupOffset){
		if (forecast.getAdjustableParameterList().containsParameter(Frankel02_AdjustableEqkRupForecast.
				RUP_OFFSET_PARAM_NAME)) {
			logger.log(Level.FINE, "Setting ERF rupture offset: " + rupOffset);
			forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME).setValue(new Double(rupOffset));
		} else {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME+"', ignoring setting.");
		}
		forecast.updateForecast();
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma() throws IOException {
		getMeanSigma(false);
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma(boolean haz01) throws IOException {

		int numIMRs = chosenAttenuationsList.size();
		File file = new File(dirName);
		file.mkdirs();
		IM_EventSetOutputWriter writer = null;
		if (haz01) {
			writer = new HAZ01Writer(this);
		} else {
			writer = new OriginalModWriter(this);
		}
		writer.writeFiles(forecast, chosenAttenuationsList, supportedIMTs);
	}

	/**
	 *  Function that must be implemented by all Listeners for
	 *  ParameterChangeWarnEvents.
	 *
	 * @param  event  The Event which triggered this function call
	 */
	public void parameterChangeWarning(ParameterChangeWarningEvent e) {

		String S = " : parameterChangeWarning(): ";

		WarningParameter param = e.getWarningParameter();

		param.setValueIgnoreWarning(e.getNewValue());

	}
	
	private static void printUsage() {
		System.out.println("Usage :\n\t"+"java -jar <jarfileName> [--HAZ01] [--d] <inputFileName> <output directory name>\n\n");
		System.out.println("jarfileName : Name of the executable jar file, by default it is IM_EventSetCalc.jar");
		System.out.println("--HAZ01 : Optional parameter to specify using the HAZ01 output file format instead of the default");
		System.out.println("inputFileName :Name of the input file"+
		" For eg: see \"IM_EventSetCalc_InputFile.txt\". ");
		System.out.println("output directory name : Name of the output directory where all the output files will be generated");
		System.exit(2);
	}

	public static void main(String[] args) {
		boolean haz01 = false;
		
		ArrayList<String> parsedArgs = new ArrayList<String>();
		
		Level level = Level.WARNING;
		
		for (String arg : args) {
			if (arg.trim().toLowerCase().equals("--haz01"))
				haz01 = true;
			else if (arg.trim().toLowerCase().equals("--d"))
				level = Level.CONFIG;
			else if (arg.trim().toLowerCase().equals("--dd"))
				level = Level.FINE;
			else if (arg.trim().toLowerCase().equals("--ddd"))
				level = Level.ALL;
			else if (arg.trim().toLowerCase().equals("--q"))
				level = Level.OFF;
			else
				parsedArgs.add(arg);
		}
		
		initLogger(level);

		IM_EventSetCalc_v3_0_ASCII calc = null;
		if (parsedArgs.size() == 2) {
			calc = new IM_EventSetCalc_v3_0_ASCII(parsedArgs.get(0),parsedArgs.get(1));
//		} else if (args.length == 3) {
//			if (args[0].trim().toLowerCase().equals("--haz01"))
//				haz01 = true;
//			else {
//				System.out.println("Unknown option: " + args[0]);
//				printUsage();
//			}
//			calc = new IM_EventSetCalc_v3_0_ASCII(args[1],args[2]);
		} else {
			printUsage();
		}
		//IM_EventSetCalc calc = new IM_EventSetCalc("org/opensha/sha/calc/IM_EventSetCalc_v02/ExampleInputFile.txt","org/opensha/sha/calc/IM_EventSetCalc_v02/test");
		try {
			calc.parseFile();
		}
		catch (Exception ex) {
			logger.log(Level.INFO, "Error parsing input file!", ex);
//			ex.printStackTrace();
			System.exit(1);
		}

		try {
			calc.getMeanSigma(haz01);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	public int getNumSites() {
		return locList.size();
	}

	public File getOutputDir() {
		return outputDir;
	}

	public OrderedSiteDataProviderList getSiteDataProviders() {
		return providers;
	}

	public Location getSiteLocation(int i) {
		return locList.get(i);
	}

	public ArrayList<SiteDataValue<?>> getUserSiteDataValues(int i) {
		return userDataVals.get(i);
	}
}
