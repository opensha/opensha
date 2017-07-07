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

package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.analysis;

import java.awt.Color;
import java.util.ArrayList;

import org.jfree.data.Range;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.HanksBakun2002_MagAreaRel;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UnsegmentedSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.A_Faults.A_FaultSegmentedSourceGenerator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.A_FaultsFetcher;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.UCERF1MfdReader;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.gui.A_FaultsMFD_Plotter;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

/**
 * It plots the MFDs from UCERF2 and compares them with MFDs from UCERF1
 * @author vipingupta
 *
 */
public class UCERF1ComparisonPlotter {
	private UCERF2 ucerf2;
	
	public UCERF1ComparisonPlotter(UCERF2 ucerf2) {
		this.ucerf2 = ucerf2;
	}
	
	public UCERF1ComparisonPlotter() {
		ucerf2 = new UCERF2();
	}
	
	/**
	 * Plot the differences in San Jacinto and S. san Andreas in Different deformation models
	 *
	 */
	public void plot_SJ_SSAF_FaultsDefModels() {
		// It holds incr rates for each A-Fault
		ArrayList<XY_DataSetList> aFaultIncrRateFuncList = new ArrayList<XY_DataSetList>();
		// It holds Cum Rates for each A-Fault
		ArrayList<XY_DataSetList> aFaultCumRateFuncList = new ArrayList<XY_DataSetList>();
	
		int sanJacintoIndex = 2;
		int soSAF_Index = 4;
		
		XY_DataSetList sjIncrRateFuncList = new XY_DataSetList();
		XY_DataSetList ssafIncrRateFuncList = new XY_DataSetList();
		XY_DataSetList sjCumRateFuncList = new XY_DataSetList();
		XY_DataSetList ssafCumRateFuncList = new XY_DataSetList();
		
		System.out.println("Doing Deformation model 2.1");
		fillCumAndIncrFuncListForDefModel("D2.1", aFaultIncrRateFuncList, aFaultCumRateFuncList);
		
		// add to the list of functions to be plotted
		int numCurvesAdded = aFaultIncrRateFuncList.get(sanJacintoIndex).size();
		sjIncrRateFuncList.add(aFaultIncrRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafIncrRateFuncList.add(aFaultIncrRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		sjCumRateFuncList.add(aFaultCumRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafCumRateFuncList.add(aFaultCumRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		
		System.out.println("Doing Deformation model 2.2");
		aFaultIncrRateFuncList = new ArrayList<XY_DataSetList>();
		// It holds Cum Rates for each A-Fault
		aFaultCumRateFuncList = new ArrayList<XY_DataSetList>();
		fillCumAndIncrFuncListForDefModel("D2.2", aFaultIncrRateFuncList, aFaultCumRateFuncList);
		// add to the list of functions to be plotted
		numCurvesAdded = aFaultIncrRateFuncList.get(sanJacintoIndex).size();
		sjIncrRateFuncList.add(aFaultIncrRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafIncrRateFuncList.add(aFaultIncrRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		sjCumRateFuncList.add(aFaultCumRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafCumRateFuncList.add(aFaultCumRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		
		System.out.println("Doing Deformation model 2.3");
		aFaultIncrRateFuncList = new ArrayList<XY_DataSetList>();
		// It holds Cum Rates for each A-Fault
		aFaultCumRateFuncList = new ArrayList<XY_DataSetList>();
		fillCumAndIncrFuncListForDefModel("D2.3", aFaultIncrRateFuncList, aFaultCumRateFuncList);
		// add to the list of functions to be plotted
		numCurvesAdded = aFaultIncrRateFuncList.get(sanJacintoIndex).size();
		sjIncrRateFuncList.add(aFaultIncrRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafIncrRateFuncList.add(aFaultIncrRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		sjCumRateFuncList.add(aFaultCumRateFuncList.get(sanJacintoIndex).get(numCurvesAdded-1));
		ssafCumRateFuncList.add(aFaultCumRateFuncList.get(soSAF_Index).get(numCurvesAdded-1));
		
	
		
		/* wt-ave MFD WT PROPOSED BY OTHER EXCOM MEMBERS FOLLOWING CONFERENCE CALL
		 aPriori_EllB 	0.225
		 aPriori_HB 		0.225
		 MoBal_EllB 		0.225
		 MoBal_HB 		0.225
		 Unseg_EllB 	0.05
		 Unseg_HB	0.05
		 */
		
		String name  = "Wt Avg MFD";
		
		// SJF and SSAF
		IncrementalMagFreqDist sjWtAveMFD = (IncrementalMagFreqDist) ((IncrementalMagFreqDist)sjIncrRateFuncList.get(0)).deepClone();
		IncrementalMagFreqDist ssafWtAveMFD = (IncrementalMagFreqDist) ((IncrementalMagFreqDist)ssafIncrRateFuncList.get(0)).deepClone();
		XY_DataSet func = sjIncrRateFuncList.get(0);
		for(int imag=0; imag<func.size(); ++imag) {
			double val1 = 0.5*sjIncrRateFuncList.get(0).getY(imag) + 0.2*sjIncrRateFuncList.get(1).getY(imag) + 0.3*sjIncrRateFuncList.get(2).getY(imag);
			sjWtAveMFD.set(func.getX(imag), val1);
			val1 = 0.5*ssafIncrRateFuncList.get(0).getY(imag) + 0.2*ssafIncrRateFuncList.get(1).getY(imag) + 0.3*ssafIncrRateFuncList.get(2).getY(imag);
			ssafWtAveMFD.set(func.getX(imag), val1);
		}			
		sjWtAveMFD.setName(name);
		sjIncrRateFuncList.add(sjWtAveMFD);
		EvenlyDiscretizedFunc cumMFD = sjWtAveMFD.getCumRateDistWithOffset();
		cumMFD.setName(name);
		sjCumRateFuncList.add(cumMFD);
		
		ssafWtAveMFD.setName(name);
		ssafIncrRateFuncList.add(ssafWtAveMFD);
		cumMFD = ssafWtAveMFD.getCumRateDistWithOffset();
		cumMFD.setName(name);
		ssafCumRateFuncList.add(cumMFD);
		
		PlotCurveCharacterstics PLOT_CHAR1 = new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, null, 4f,
			      Color.BLACK);
		PlotCurveCharacterstics PLOT_CHAR2 = new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, null, 4f,
			      Color.BLACK);
		PlotCurveCharacterstics PLOT_CHAR3 = new PlotCurveCharacterstics(PlotLineType.DOTTED_AND_DASHED, 2f, null, 4f,
			      Color.BLACK);
		PlotCurveCharacterstics PLOT_CHAR4 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
			      Color.BLACK);
		ArrayList plotChars = new ArrayList();
		plotChars.add(PLOT_CHAR1);
		plotChars.add(PLOT_CHAR2);
		plotChars.add(PLOT_CHAR3);
		plotChars.add(PLOT_CHAR4);
		makePlot(sjIncrRateFuncList, plotChars, "San Jacinto");
		makePlot(ssafIncrRateFuncList, plotChars, "S San Andreas");
		makePlot(sjCumRateFuncList, plotChars, "San Jacinto");
		makePlot(ssafCumRateFuncList, plotChars, "S San Andreas");

	}

	private void makePlot(XY_DataSetList sjIncrRateFuncList, ArrayList plotChars, String label) {
		ArrayList aList = new ArrayList();
		for(int i=0; i<sjIncrRateFuncList.size(); ++i)
			aList.add(sjIncrRateFuncList.get(i));
		A_FaultsMFD_Plotter aFaultsPlotter = new A_FaultsMFD_Plotter(aList, false);
		aFaultsPlotter.setPlottingFeatures(plotChars);
		PlotSpec spec = aFaultsPlotter.getPlotSpec();
		spec.setTitle(label);
		GraphWindow graphWindow= new GraphWindow(spec, PlotPreferences.getDefault(),
				aFaultsPlotter.getXLog(), aFaultsPlotter.getYLog(),
				new Range(aFaultsPlotter.getUserMinX(), aFaultsPlotter.getUserMaxX()),
				new Range(aFaultsPlotter.getUserMinY(), aFaultsPlotter.getUserMaxY()));
		graphWindow.setVisible(true);
	}
	
	/**
	 * plot all MFDs in one chart, but diff chart for diff faults
	 */
	public void plotA_FaultMFDs_forReport(String defModelName) {
		
		// It holds incr rates for each A-Fault
		ArrayList<XY_DataSetList> aFaultIncrRateFuncList = new ArrayList<XY_DataSetList>();
		// It holds Cum Rates for each A-Fault
		ArrayList<XY_DataSetList> aFaultCumRateFuncList = new ArrayList<XY_DataSetList>();
		
		ArrayList<String> faultNames=fillCumAndIncrFuncListForDefModel(defModelName, aFaultIncrRateFuncList, aFaultCumRateFuncList);
		
	
		//UCERF1 MFD
		String name = "UCERF1 MFD";
		for(int i=0; i<aFaultIncrRateFuncList.size(); ++i) {
			String faultName = faultNames.get(i);
			ArbitrarilyDiscretizedFunc ucerf1Rate = UCERF1MfdReader.getUCERF1IncrementalMFD(faultName);
			if(ucerf1Rate.size()==0) ucerf1Rate.set(0.0, 0.0);
			aFaultIncrRateFuncList.get(i).add(ucerf1Rate);
			ucerf1Rate.setName(name);
			ArbitrarilyDiscretizedFunc cumMFD = UCERF1MfdReader.getUCERF1CumMFD(faultName);
			if(cumMFD.size()==0) cumMFD.set(0.0, 0.0);
			cumMFD.setName(name);
			aFaultCumRateFuncList.get(i).add(cumMFD);
		}

		// PLOT INCR RATES
		for(int i=0; i<aFaultIncrRateFuncList.size(); ++i) {
			XY_DataSetList funcList = aFaultIncrRateFuncList.get(i);
			String faultName = faultNames.get(i);
			ArrayList funcArrayList = new ArrayList();
			funcArrayList.add(funcList.get(funcList.size()-1));
			funcArrayList.add(funcList.get(funcList.size()-2));
			for(int j=0; j<funcList.size()-2; ++j) funcArrayList.add(funcList.get(j));
			
			A_FaultsMFD_Plotter aFaultsPlotter = new A_FaultsMFD_Plotter(funcArrayList, false);
			PlotSpec spec = aFaultsPlotter.getPlotSpec();
			spec.setTitle(faultName);
			GraphWindow graphWindow= new GraphWindow(spec, PlotPreferences.getDefault(),
					aFaultsPlotter.getXLog(), aFaultsPlotter.getYLog(),
					new Range(aFaultsPlotter.getUserMinX(), aFaultsPlotter.getUserMaxX()),
					new Range(aFaultsPlotter.getUserMinY(), aFaultsPlotter.getUserMaxY()));
			graphWindow.setVisible(true);
		}

		// PLOT CUM RATES
		for(int i=0; i<aFaultCumRateFuncList.size(); ++i) {
			XY_DataSetList funcList = aFaultCumRateFuncList.get(i);
			String faultName = faultNames.get(i);
			ArrayList funcArrayList = new ArrayList();
			funcArrayList.add(funcList.get(funcList.size()-1));
			funcArrayList.add(funcList.get(funcList.size()-2));
			for(int j=0; j<funcList.size()-2; ++j) funcArrayList.add(funcList.get(j));
			
			A_FaultsMFD_Plotter aFaultsPlotter = new A_FaultsMFD_Plotter(funcArrayList, false);
			PlotSpec spec = aFaultsPlotter.getPlotSpec();
			spec.setTitle(faultName);
			GraphWindow graphWindow= new GraphWindow(spec, PlotPreferences.getDefault(),
					aFaultsPlotter.getXLog(), aFaultsPlotter.getYLog(),
					new Range(aFaultsPlotter.getUserMinX(), aFaultsPlotter.getUserMaxX()),
					new Range(aFaultsPlotter.getUserMinY(), aFaultsPlotter.getUserMaxY()));
			graphWindow.setVisible(true);
		}
	}

	private ArrayList<String> fillCumAndIncrFuncListForDefModel(String defModelName, ArrayList<XY_DataSetList> aFaultIncrRateFuncList, ArrayList<XY_DataSetList> aFaultCumRateFuncList) {
		
		ucerf2.setParamDefaults();
		ucerf2.updateForecast();
		ArrayList aFaultSourceGenerators = ucerf2.get_A_FaultSourceGenerators();
		A_FaultsFetcher aFaultsFetcher = ucerf2.getA_FaultsFetcher();
		int numA_Faults = aFaultSourceGenerators.size();
		ArrayList<String> faultNames = aFaultsFetcher.getAllFaultNames();
		for(int i=0; i<numA_Faults; ++i) {
			//System.out.println(faultNames.get(i));
			aFaultIncrRateFuncList.add(new XY_DataSetList());
			aFaultCumRateFuncList.add(new XY_DataSetList());
		}
		
		// Default parameters
		ucerf2.setParamDefaults();
		ucerf2.getParameter(UCERF2.DEFORMATION_MODEL_PARAM_NAME).setValue(defModelName);
		ucerf2.updateForecast();
		String name = "Default Parameters with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);
		
		// Def Params w/ change Mag Area to Hanks Bakun
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def Params w/ change Mag Area to Hanks Bakun with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);
		
		// Def. params with High apriori model weight
		ucerf2.setParamDefaults();
		ucerf2.getParameter(UCERF2.DEFORMATION_MODEL_PARAM_NAME).setValue(defModelName);
		ucerf2.setParameter(UCERF2.REL_A_PRIORI_WT_PARAM_NAME, new Double(1e10));
		ucerf2.setParameter(UCERF2.MIN_A_FAULT_RATE_1_PARAM_NAME, new Double(0));
		ucerf2.setParameter(UCERF2.MIN_A_FAULT_RATE_2_PARAM_NAME, new Double(0));
		ucerf2.updateForecast();
		name = "Def. params with High apriori model weight with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);
		
		// Def. params with High apriori model weight & change Mag Area to Hanks Bakun
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def. params with High apriori model weight & change Mag Area to Hanks Bakun with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);


		// Def. Params with unsegmented
		ucerf2.setParamDefaults();
		ucerf2.getParameter(UCERF2.DEFORMATION_MODEL_PARAM_NAME).setValue(defModelName);
		ucerf2.setParameter(UCERF2.RUP_MODEL_TYPE_NAME, UCERF2.UNSEGMENTED_A_FAULT_MODEL);
		ucerf2.updateForecast();
		name = "Def. Params with unegmented with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);

			// Def. Params with unegmented & change Mag Area to Hans Bakun
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def. Params with unegmented & change Mag Area to Hans Bakun with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		addToFuncListForReportPlots(aFaultIncrRateFuncList, aFaultCumRateFuncList, name);
		
		/* wt-ave MFD WT PROPOSED BY OTHER EXCOM MEMBERS FOLLOWING CONFERENCE CALL
		 aPriori_EllB 	0.225
		 aPriori_HB 		0.225
		 MoBal_EllB 		0.225
		 MoBal_HB 		0.225
		 Unseg_EllB 	0.05
		 Unseg_HB	0.05
		 */
		name  = "Wt Avg MFD with "+UCERF2.DEFORMATION_MODEL_PARAM_NAME+"="+defModelName;
		for(int i=0; i<aFaultIncrRateFuncList.size(); ++i) {
			XY_DataSetList funcList = aFaultIncrRateFuncList.get(i);
			//System.out.println("i="+i+", Funclist size="+funcList.size());
			IncrementalMagFreqDist wtAveMFD = (IncrementalMagFreqDist) ((IncrementalMagFreqDist)funcList.get(0)).deepClone();
			XY_DataSet func = funcList.get(0);
			for(int imag=0; imag<func.size(); ++imag) {
				// D2.1
				double val1 = 0.225*funcList.get(0).getY(imag) + 0.225*funcList.get(1).getY(imag) + 
						0.225*funcList.get(2).getY(imag) + 0.225*funcList.get(3).getY(imag) + 
						0.050*funcList.get(4).getY(imag) + 0.050*funcList.get(5).getY(imag);

				wtAveMFD.set(func.getX(imag), val1);
			}
			wtAveMFD.setName(name);
			aFaultIncrRateFuncList.get(i).add(wtAveMFD);
			EvenlyDiscretizedFunc cumMFD = wtAveMFD.getCumRateDistWithOffset();
			cumMFD.setName(name);
			aFaultCumRateFuncList.get(i).add(cumMFD);
		}
		
		return faultNames;
	}
	
	
	/**
	 * Plot MFDs for San Gregorio, Greenville, Concord-Green Valley and Mt. Diablo
	 */
	public void plotB_FaultMFDs_forReport() {

		// Default parameters
		ucerf2.setParamDefaults();
		ucerf2.updateForecast();
		String []bFaultNames = { "San Gregorio Connected", "Greenville Connected", "Green Valley Connected", "Mount Diablo Thrust"};  
		int[] b_FaultIndices = new int[bFaultNames.length];
		ArrayList<UnsegmentedSource> bFaultSources = ucerf2.get_B_FaultSources();
		//find indices of B-Faults in the B-Fault sources list
		for(int i=0; i<bFaultNames.length; ++i) {
			String faultName = bFaultNames[i];
			for(int j=0; j<bFaultSources.size(); ++j) {
				if(bFaultSources.get(j).getFaultSegmentData().getFaultName().equalsIgnoreCase(faultName)) {
					b_FaultIndices[i] = j;
					break;
				}
			}
		}
		
		int numB_Faults = bFaultNames.length;
		
		// It holds incr rates for each B-Faults
		ArrayList<XY_DataSetList> bFaultIncrRateFuncList = new ArrayList<XY_DataSetList>();
		// It holds Cum Rates for each A-Fault
		ArrayList<XY_DataSetList> bFaultCumRateFuncList = new ArrayList<XY_DataSetList>();

		for(int i=0; i<numB_Faults; ++i) {
			bFaultIncrRateFuncList.add(new XY_DataSetList());
			bFaultCumRateFuncList.add(new XY_DataSetList());
		}
		// Default Parameters
		String name = "Default Parameters";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);
		
		// Def Params w/ change Mag Area to Hanks Bakun
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def Params w/ change Mag Area to Hanks Bakun";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);

		// Def Params with Mean Mag correction=-0.1
		ucerf2.setParamDefaults();
		ucerf2.setParameter(UCERF2.MEAN_MAG_CORRECTION, new Double(-0.1));
		ucerf2.updateForecast();
		name = "Def Params w/ change Mean Mag Correction to -0.1";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);
		
		// HB Mag Area Rel and Mean Mag correction=-0.1
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def Params w/ change Mean Mag Correction to -0.1 and Mag Area to Hanks Bakun";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);
		
		// Def Params with Mean Mag correction=0.1
		ucerf2.setParamDefaults();
		ucerf2.setParameter(UCERF2.MEAN_MAG_CORRECTION, new Double(0.1));
		ucerf2.updateForecast();
		name = "Def Params w/ change Mean Mag Correction to 0.1";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);
		
		// HB Mag Area Rel and Mean Mag correction=0.1
		ucerf2.setParameter(UCERF2.MAG_AREA_RELS_PARAM_NAME, HanksBakun2002_MagAreaRel.NAME);
		ucerf2.updateForecast();
		name = "Def Params w/ change Mean Mag Correction to 0.1 and Mag Area to Hanks Bakun";
		addToB_FaultsPlottingList(b_FaultIndices, numB_Faults, bFaultIncrRateFuncList, bFaultCumRateFuncList, name);
		
		/* wt-ave MFD WT 
		 EllB, 0.6 	0.3
		 HB, 0.6	0.3
		 EllB,-0.1	0.1
		 HB,-0.1	0.1
		 EllB,0.1	0.1
		 HB,0.1		0.1
		 */
		name  = "Wt Avg MFD";
		for(int i=0; i<bFaultIncrRateFuncList.size(); ++i) {
			XY_DataSetList funcList = bFaultIncrRateFuncList.get(i);
			IncrementalMagFreqDist wtAveMFD = (IncrementalMagFreqDist) ((IncrementalMagFreqDist)funcList.get(0)).deepClone();
			XY_DataSet func = funcList.get(0);
			
			for(int imag=0; imag<func.size(); ++imag) 
				wtAveMFD.set(func.getX(imag), 
						0.3*funcList.get(0).getY(imag) + 0.3*funcList.get(1).getY(imag)+
						0.1*funcList.get(2).getY(imag) + 0.1*funcList.get(3).getY(imag)+
						0.1*funcList.get(4).getY(imag) + 0.1*funcList.get(4).getY(imag));


			wtAveMFD.setName(name);
			bFaultIncrRateFuncList.get(i).add(wtAveMFD);
			EvenlyDiscretizedFunc cumMFD = wtAveMFD.getCumRateDistWithOffset();
			cumMFD.setName(name);
			bFaultCumRateFuncList.get(i).add(cumMFD);
		}

		//UCERF1 MFD
		name = "UCERF1 MFD";
		for(int i=0; i<bFaultIncrRateFuncList.size(); ++i) {
			String faultName = bFaultNames[i];
			ArbitrarilyDiscretizedFunc ucerf1Rate = UCERF1MfdReader.getUCERF1IncrementalMFD(faultName);
			if(ucerf1Rate.size()==0) ucerf1Rate.set(0.0, 0.0);
			bFaultIncrRateFuncList.get(i).add(ucerf1Rate);
			ucerf1Rate.setName(name);
			ArbitrarilyDiscretizedFunc cumMFD = UCERF1MfdReader.getUCERF1CumMFD(faultName);
			if(cumMFD.size()==0) cumMFD.set(0.0, 0.0);
			cumMFD.setName(name);
			bFaultCumRateFuncList.get(i).add(cumMFD);
		}

		// PLOT INCR RATES
		for(int i=0; i<bFaultIncrRateFuncList.size(); ++i) {
			XY_DataSetList funcList = bFaultIncrRateFuncList.get(i);
			String faultName = bFaultNames[i];
			ArrayList funcArrayList = new ArrayList();
			funcArrayList.add(funcList.get(funcList.size()-1));
			funcArrayList.add(funcList.get(funcList.size()-2));
			//for(int j=0; j<funcList.size()-2; ++j) funcArrayList.add(funcList.get(j));
			A_FaultsMFD_Plotter aFaultsPlotter = new A_FaultsMFD_Plotter(funcArrayList, false);
			PlotSpec spec = aFaultsPlotter.getPlotSpec();
			spec.setTitle(faultName);
			GraphWindow graphWindow= new GraphWindow(spec, PlotPreferences.getDefault(),
					aFaultsPlotter.getXLog(), aFaultsPlotter.getYLog(),
					new Range(aFaultsPlotter.getUserMinX(), aFaultsPlotter.getUserMaxX()),
					new Range(aFaultsPlotter.getUserMinY(), aFaultsPlotter.getUserMaxY()));
			graphWindow.setVisible(true);
		}

		// PLOT CUM RATES
		for(int i=0; i<bFaultCumRateFuncList.size(); ++i) {
			XY_DataSetList funcList = bFaultCumRateFuncList.get(i);
			String faultName = bFaultNames[i];
			ArrayList funcArrayList = new ArrayList();
			funcArrayList.add(funcList.get(funcList.size()-1));
			funcArrayList.add(funcList.get(funcList.size()-2));
			//for(int j=0; j<funcList.size()-2; ++j) funcArrayList.add(funcList.get(j));
			A_FaultsMFD_Plotter aFaultsPlotter = new A_FaultsMFD_Plotter(funcArrayList, false);
			PlotSpec spec = aFaultsPlotter.getPlotSpec();
			spec.setTitle(faultName);
			GraphWindow graphWindow= new GraphWindow(spec, PlotPreferences.getDefault(),
					aFaultsPlotter.getXLog(), aFaultsPlotter.getYLog(),
					new Range(aFaultsPlotter.getUserMinX(), aFaultsPlotter.getUserMaxX()),
					new Range(aFaultsPlotter.getUserMinY(), aFaultsPlotter.getUserMaxY()));
			graphWindow.setVisible(true);
		}
	}

	private void addToB_FaultsPlottingList(int[] b_FaultIndices, int numB_Faults, ArrayList<XY_DataSetList> bFaultIncrRateFuncList, ArrayList<XY_DataSetList> bFaultCumRateFuncList, String name) {
		ArrayList<UnsegmentedSource> bFaultSources = ucerf2.get_B_FaultSources();
		for(int i=0; i<numB_Faults; ++i) {
			IncrementalMagFreqDist incrMFD = bFaultSources.get(b_FaultIndices[i]).getMagFreqDist();
			incrMFD.setName(name);
			incrMFD.setInfo("");
			EvenlyDiscretizedFunc cumMFD = incrMFD.getCumRateDistWithOffset();
			cumMFD.setName(name);
			cumMFD.setInfo("");
			bFaultIncrRateFuncList.get(i).add(incrMFD);
			bFaultCumRateFuncList.get(i).add(cumMFD);
		}
	}

	/**
	 * Add the MFD and Cum MFD to list for creating figures for report
	 * 
	 * @param aFaultIncrRateFuncList
	 * @param aFaultCumRateFuncList
	 * @param name
	 */
	private void addToFuncListForReportPlots(ArrayList<XY_DataSetList> aFaultIncrRateFuncList, 
			ArrayList<XY_DataSetList> aFaultCumRateFuncList, String name) {
		IncrementalMagFreqDist incrMFD;
		EvenlyDiscretizedFunc cumMFD;
		String modelType = (String)ucerf2.getParameter(UCERF2.RUP_MODEL_TYPE_NAME).getValue();
		boolean isUnsegmented = false;
		if(modelType.equalsIgnoreCase(UCERF2.UNSEGMENTED_A_FAULT_MODEL)) isUnsegmented = true;
		boolean isSanJacinto;
		ArrayList aFaultSourceGenerators = ucerf2.get_A_FaultSourceGenerators();
		for(int i=0; i<aFaultSourceGenerators.size(); ++i) {
			isSanJacinto = false;
			Object obj = aFaultSourceGenerators.get(i);
			if(obj instanceof A_FaultSegmentedSourceGenerator) {
				// segmented source
				incrMFD =( (A_FaultSegmentedSourceGenerator)obj).getTotalRupMFD();

			} else {
				// unsegmented source
				incrMFD =( (UnsegmentedSource)obj).getMagFreqDist();

				if(i==2) { // combined the 2 faults for San Jacinto
					String faultName1 = ( (UnsegmentedSource)obj).getFaultSegmentData().getFaultName();
					String faultName2 = ( (UnsegmentedSource)aFaultSourceGenerators.get(i+1)).getFaultSegmentData().getFaultName();
					if(!faultName1.equalsIgnoreCase("San Jacinto (SB to C)") || !faultName2.equalsIgnoreCase("San Jacinto (CC to SM)"))
						throw new RuntimeException("Invalid combination of San Jacinto faults");
					isSanJacinto = true;

					IncrementalMagFreqDist incrMFD2 = ( (UnsegmentedSource)aFaultSourceGenerators.get(i+1)).getMagFreqDist();
					((SummedMagFreqDist)incrMFD).addIncrementalMagFreqDist(incrMFD2);
				}

			}
			incrMFD.setName(name);
			cumMFD = incrMFD.getCumRateDistWithOffset();
			cumMFD.setName(name);
			incrMFD.setInfo("");
			cumMFD.setInfo("");
			if(isUnsegmented && i>2) {
				aFaultIncrRateFuncList.get(i-1).add(incrMFD);
				aFaultCumRateFuncList.get(i-1).add(cumMFD);
			} else {
				aFaultIncrRateFuncList.get(i).add(incrMFD);
				aFaultCumRateFuncList.get(i).add(cumMFD);
			}
			if(isSanJacinto) ++i; // skip next section for San Jacinto as it as already been combined
		}
	}
	
	public static void main(String[] args) {
		UCERF1ComparisonPlotter ucerf1ComparisonPlotter = new UCERF1ComparisonPlotter();
		//ucerf1ComparisonPlotter.plotA_FaultMFDs_forReport();
		ucerf1ComparisonPlotter.plotB_FaultMFDs_forReport();
	}

}
