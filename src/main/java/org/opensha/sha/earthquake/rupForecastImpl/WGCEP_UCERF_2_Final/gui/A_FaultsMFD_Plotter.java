/**
 * 
 */
package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;

import com.google.common.collect.Lists;

/**
 * This class is used for plotting the MFDs for the EqkRateModel2.2
 * @author vipingupta
 *
 */
public class A_FaultsMFD_Plotter {
	private final static String X_AXIS_LABEL = "Magnitude";
	private final static String RATE_AXIS_LABEL = "Rate (per year)";
	private final static String CUM_RATE_AXIS_LABEL = "Cum Rate (per year)";
	private String yAxisLabel;
	
	private List<DiscretizedFunc> funcs;
	
	private final PlotCurveCharacterstics PLOT_CHAR1 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.BLUE);
	private final PlotCurveCharacterstics PLOT_CHAR2 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.GRAY);
	private final PlotCurveCharacterstics PLOT_CHAR3 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.GREEN);
	private final PlotCurveCharacterstics PLOT_CHAR4 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.MAGENTA);
	private final PlotCurveCharacterstics PLOT_CHAR5 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.PINK);
	private final PlotCurveCharacterstics PLOT_CHAR6 = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, null, 4f,
		      Color.YELLOW);
	private final PlotCurveCharacterstics PLOT_CHAR7 = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, null, 4f,
		      Color.RED);
	private final PlotCurveCharacterstics PLOT_CHAR8 = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, null, 4f,
		      Color.BLACK);
	
	private List<PlotCurveCharacterstics> plottingFeatures;
	
	public A_FaultsMFD_Plotter(List<DiscretizedFunc> funcs, boolean isCumRate) {
		this.funcs = funcs;
		if(isCumRate) yAxisLabel = CUM_RATE_AXIS_LABEL;
		else yAxisLabel = RATE_AXIS_LABEL;
		
		// set the default plotting features
		plottingFeatures  = Lists.newArrayList();
		plottingFeatures.add(PLOT_CHAR8);
		plottingFeatures.add(PLOT_CHAR7);
		 if(funcs.size()>2) { // Size is 2 for B-Faults 
			 plottingFeatures.add(PLOT_CHAR1);
			 plottingFeatures.add(PLOT_CHAR2);
			 plottingFeatures.add(PLOT_CHAR3);
			 plottingFeatures.add(PLOT_CHAR4);
			 plottingFeatures.add(PLOT_CHAR5);
			 plottingFeatures.add(PLOT_CHAR6);
		 }
	}
	
	

	
	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getCurveFunctionList()
	 */
	public List<DiscretizedFunc> getCurveFunctionList() {
		return funcs;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getXLog()
	 */
	public boolean getXLog() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getYLog()
	 */
	public boolean getYLog() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getXAxisLabel()
	 */
	public String getXAxisLabel() {
		return X_AXIS_LABEL;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getYAxisLabel()
	 */
	public String getYAxisLabel() {
		return yAxisLabel;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getPlottingFeatures()
	 */
	public List<PlotCurveCharacterstics> getPlottingFeatures() {
		return this.plottingFeatures;
	}
	
	public PlotSpec getPlotSpec() {
		return new PlotSpec(funcs, plottingFeatures, "", getXAxisLabel(), getYAxisLabel());
	}
	
	/**
	 * Set the plotting features
	 * @param plotFeatures
	 */
	public void setPlottingFeatures(ArrayList plotFeatures) {
		this.plottingFeatures = plotFeatures;
	}
	

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#isCustomAxis()
	 */
	public boolean isCustomAxis() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getMinX()
	 */
	public double getUserMinX() {
		return 6.0;
		//throw new UnsupportedOperationException("Method not implemented yet");
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getMaxX()
	 */
	public double getUserMaxX() {
		return 8.5;
		//throw new UnsupportedOperationException("Method not implemented yet");
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getMinY()
	 */
	public double getUserMinY() {
		return 1e-7;
		//throw new UnsupportedOperationException("Method not implemented yet");
	}

	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getMaxY()
	 */
	public double getUserMaxY() {
		return 0.1;
		//throw new UnsupportedOperationException("Method not implemented yet");
	}
}
