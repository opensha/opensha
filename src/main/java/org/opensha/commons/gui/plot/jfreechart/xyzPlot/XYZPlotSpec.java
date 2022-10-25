package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.cpt.CPT;

/**
 * Analogous to PlotSpec, but for XYZ plots. Contains data, labels, CPT for XYZ plot to be
 * passed to XYZGraphPanel. It is recommended that you set the tickness (width of the colored
 * square to be shown at each XY location) for non-gridded surfaces.
 * 
 * @author kevin
 *
 */
public class XYZPlotSpec extends PlotSpec {
	
	private XYZ_DataSet xyzData;
	private CPT cpt;
	private String zAxisLabel;
	private Double thickness = null; // this is width of each rectangle
	private boolean cptVisible = true;
	private double cptTickUnit = -1;
	private RectangleEdge cptPosition = RectangleEdge.TOP;
	
	/**
	 * 
	 * @param xyzData XYZ data
	 * @param cpt color palette
	 * @param title
	 * @param xAxisLabel
	 * @param yAxisLabel
	 * @param zAxisLabel shown under the color scale
	 */
	public XYZPlotSpec(XYZ_DataSet xyzData, CPT cpt, String title,
			String xAxisLabel, String yAxisLabel, String zAxisLabel) {
		this(xyzData, new ArrayList<>(), new ArrayList<>(), cpt, title, xAxisLabel, yAxisLabel, zAxisLabel);
	}
	
	/**
	 * 
	 * @param xyzData XYZ data
	 * @param elems XY plot elements
	 * @param chars XY plot characteristics
	 * @param cpt color palette
	 * @param title
	 * @param xAxisLabel
	 * @param yAxisLabel
	 * @param zAxisLabel shown under the color scale
	 */
	public XYZPlotSpec(XYZ_DataSet xyzData, List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> chars, CPT cpt, String title,
			String xAxisLabel, String yAxisLabel, String zAxisLabel) {
		super(elems, chars, title, xAxisLabel, yAxisLabel);
		this.xyzData = xyzData;
		this.cpt = cpt;
		this.zAxisLabel = zAxisLabel;
	}

	public XYZ_DataSet getXYZ_Data() {
		return xyzData;
	}

	public void setXYZ_Data(XYZ_DataSet xyzData) {
		this.xyzData = xyzData;
	}

	public CPT getCPT() {
		return cpt;
	}

	public void setCPT(CPT cpt) {
		this.cpt = cpt;
	}

	public String getZAxisLabel() {
		return zAxisLabel;
	}

	public void setZAxisLabel(String zAxisLabel) {
		this.zAxisLabel = zAxisLabel;
	}

	public Double getThickness() {
		return thickness;
	}

	public void setThickness(Double thickness) {
		this.thickness = thickness;
	}

	public RectangleEdge getCPTPosition() {
		return cptPosition;
	}

	/**
	 * This sets the position of the color scale legend.
	 * @param legendPosition
	 */
	public void setCPTPosition(RectangleEdge legendPosition) {
		this.cptPosition = legendPosition;
	}
	
	public void setCPTVisible(boolean cptVisible) {
		this.cptVisible = cptVisible;
	}
	
	public boolean isCPTVisible() {
		return cptVisible;
	}

	public void setXYElems(List<? extends XY_DataSet> xyElems) {
		this.elems = xyElems;
	}

	public void setXYChars(List<PlotCurveCharacterstics> xyChars) {
		this.chars = xyChars;
	}
	
	/**
	 * Sets tick unit for the CPT or -1 for auto tick units.
	 * @param cptTickUnit
	 */
	public void setCPTTickUnit(double cptTickUnit) {
		this.cptTickUnit = cptTickUnit;
	}
	
	public double getCPTTickUnit() {
		return cptTickUnit;
	}

}
