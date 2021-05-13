package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import java.util.List;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.util.cpt.CPT;

/**
 * Analogous to PlotSpec, but for XYZ plots. Contains data, labels, CPT for XYZ plot to be
 * passed to XYZGraphPanel. It is recommended that you set the tickness (width of the colored
 * square to be shown at each XY location) for non-gridded surfaces.
 * 
 * @author kevin
 *
 */
public class XYZPlotSpec {
	
	private XYZ_DataSet xyzData;
	private CPT cpt;
	private String title, xAxisLabel, yAxisLabel, zAxisLabel;
	private Double thickness = null;
	private List<? extends XYAnnotation> annotations;
	private RectangleEdge legendPosition = RectangleEdge.TOP;
	private boolean cptVisible = true;
	private List<? extends XY_DataSet> xyElems;
	private List<PlotCurveCharacterstics> xyChars;
	private double cptTickUnit = -1;
	
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
		super();
		this.xyzData = xyzData;
		this.cpt = cpt;
		this.title = title;
		this.xAxisLabel = xAxisLabel;
		this.yAxisLabel = yAxisLabel;
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getXAxisLabel() {
		return xAxisLabel;
	}

	public void setXAxisLabel(String xAxisLabel) {
		this.xAxisLabel = xAxisLabel;
	}

	public String getYAxisLabel() {
		return yAxisLabel;
	}

	public void setYAxisLabel(String yAxisLabel) {
		this.yAxisLabel = yAxisLabel;
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
	
	/**
	 * Set the list of plot annotations (or null for no annotations). Note that any line annotations
	 * will use default rendering (black 1pt line).
	 * 
	 * @param annotations
	 */
	public void setPlotAnnotations(List<? extends XYAnnotation> annotations) {
		this.annotations = annotations;
	}
	
	public List<? extends XYAnnotation> getPlotAnnotations() {
		return annotations;
	}

	public RectangleEdge getCPTPosition() {
		return legendPosition;
	}

	/**
	 * This sets the position of the color scale legend.
	 * @param legendPosition
	 */
	public void setCPTPosition(RectangleEdge legendPosition) {
		this.legendPosition = legendPosition;
	}
	
	public void setCPTVisible(boolean cptVisible) {
		this.cptVisible = cptVisible;
	}
	
	public boolean isCPTVisible() {
		return cptVisible;
	}

	public List<? extends XY_DataSet> getXYElems() {
		return xyElems;
	}

	public void setXYElems(List<? extends XY_DataSet> xyElems) {
		this.xyElems = xyElems;
	}

	public List<PlotCurveCharacterstics> getXYChars() {
		return xyChars;
	}

	public void setXYChars(List<PlotCurveCharacterstics> xyChars) {
		this.xyChars = xyChars;
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
