package org.opensha.commons.gui.plot;

import java.util.List;

import org.opensha.commons.data.function.XY_DataSetList;

/**
 * Interface for an element that can be plotted in a GraphPanel
 * @author kevin
 *
 */
public interface PlotElement {
	
	/**
	 * 
	 * @return
	 */
	public String getInfo();
	
	/**
	 * List of plots to be displayed in a GraphPanel (used by plotting code)
	 * @return
	 */
	public XY_DataSetList getDatasetsToPlot();
	
	/**
	 * List containing counts of functions for each unique color from getDatasetsToPlot() (used by plotting code)
	 * @return
	 */
	public List<Integer> getPlotNumColorList();

}
