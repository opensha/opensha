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

package org.opensha.commons.data.function;

import java.util.List;

import org.opensha.commons.gui.plot.PlotElement;

import com.google.common.collect.Lists;


/**
 * <p>Title: WeightedFuncListforPlotting</p>
 * <p>Description: This class creates the plotting capabilities for Weighted function
 * as required by our wrapper to Jfreechart.</p>
 * @author : Ned Field, Nitin Gupta
 * @version 1.0
 */

public class WeightedFuncListforPlotting extends WeightedFuncList implements PlotElement {


	private boolean individualCurvesToPlot = true;
	private boolean fractilesToPlot = true;
	private boolean meantoPlot = true;




	/**
	 * Sets boolean based on if application needs to plot individual curves
	 * @param toPlot
	 */
	public void setIndividualCurvesToPlot(boolean toPlot){
		individualCurvesToPlot = toPlot;
	}


	/**
	 * Sets boolean based on if application needs to plot fractiles
	 * @param toPlot
	 */
	public void setFractilesToPlot(boolean toPlot){
		fractilesToPlot = toPlot;
	}

	/**
	 * Sets boolean based on if application needs to plot mean curve
	 * @param toPlot
	 */
	public void setMeanToPlot(boolean toPlot){
		meantoPlot = toPlot;
	}

	/**
	 *
	 * @return true if individual plots need to be plotted , else return false
	 */
	public boolean areIndividualCurvesToPlot(){
		return individualCurvesToPlot;
	}

	/**
	 *
	 * @return true if fractile plots need to be plotted, else return false
	 */
	public boolean areFractilesToPlot(){
		return fractilesToPlot;
	}

	/**
	 *
	 * @return true if mean curve needs to be plotted, else return false.
	 */
	public boolean isMeanToPlot(){
		return meantoPlot;
	}


	@Override
	public XY_DataSetList getDatasetsToPlot() {
		XY_DataSetList plottedFuncs = new XY_DataSetList();
		if (areIndividualCurvesToPlot()) {
			XY_DataSetList list = getWeightedFunctionList();
			//list.get(0).setInfo(weightedList.getInfo()+"\n"+"(a) "+list.getInfo());
			plottedFuncs.addAll(list);
		}
		if (areFractilesToPlot()) {
			XY_DataSetList list = getFractileList();
			// list.get(0).setInfo("(b) "+list.getInfo());
			plottedFuncs.addAll(list);
		}
		if (isMeanToPlot()) {
			AbstractXY_DataSet meanFunc = getMean();
			//String info = meanFunc.getInfo();
			//meanFunc.setInfo("(c) "+info);
			plottedFuncs.add(meanFunc);
		}
		return plottedFuncs;
	}


	@Override
	public List<Integer> getPlotNumColorList() {
		List<Integer> numColorArray = Lists.newArrayList();
		if (areIndividualCurvesToPlot()) {
			numColorArray.add(new Integer(getWeightedFunctionList().size()));
		}
		if (areFractilesToPlot()) {
			numColorArray.add(new Integer(getFractileList().size()));
		}
		if (isMeanToPlot()) {
			numColorArray.add(new Integer(1));
		}
		return numColorArray;
	}



}
