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

package org.opensha.sha.earthquake.calc.recurInterval.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotElement;

import com.google.common.collect.Lists;


/**
 * It represents a tab in each tabbed pane of the Probability Dist GUI
 * 
 * @author vipingupta
 *
 */
public class PlottingPanel extends JPanel {
	
	private GraphWidget graphWidget;
	private List<PlotElement> funcList;
	
	public PlottingPanel() {
		this.setLayout(new GridBagLayout());
		funcList = new ArrayList<PlotElement>();
		graphWidget = new GraphWidget();
		this.add(graphWidget, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				  , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
	}

	  /**
	   * Add Graph Panel
	   *
	   */
	  public void addGraphPanel() {
		  graphWidget.getPlotSpec().setPlotElems(funcList);
		  this.graphWidget.drawGraph();
		  graphWidget.validate();
		  graphWidget.repaint();
	  }

	  public void plotGraphUsingPlotPreferences() {
		  this.clearPlot();
		  this.addGraphPanel();
	  }
	  
	  public void save() {
		  try {
			this.graphWidget.save();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  }
	  
	  public void print() {
		  this.graphWidget.print();
	  }

	  public void peelOff() {
		  GraphWindow graphWindow = new GraphWindow(graphWidget);
		  this.remove(graphWidget);
		  graphWidget = new GraphWidget();
		  funcList = Lists.newArrayList();
		  this.add(graphWidget, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				  , GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets( 0, 0, 0, 0 ), 0, 0 ));
		  graphWindow.setVisible(true);
	  }
	  
	  public void clearPlot() {
		  this.graphWidget.removeChartAndMetadata();
		  this.funcList.clear();
		  graphWidget.setAutoRange();
		  graphWidget.validate();
		  graphWidget.repaint();
	  }
	  
	  /**
	   * Add a function to the list of functions to be plotted
	   * 
	   * @param func
	   */
	  public void addFunc(DiscretizedFunc func) {
		  funcList.add(func);
		  this.addGraphPanel();
	  }
}
