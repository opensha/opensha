package org.opensha.sha.calc.disaggregation;

import java.io.Serializable;

/**
 * Renderer-independent data needed to draw a disaggregation plot: magnitude and distance bin centers and edges, the
 * percent-contribution grid by distance, magnitude and epsilon bin, and the requested Z-axis maximum. Created by
 * {@link DisaggregationCalculator#getDisaggPlotData()} and consumed by
 * {@link org.opensha.sha.calc.disaggregation.chart3d.PureJavaDisaggPlotter}. It is {@link Serializable} because it is
 * sent to the GMT plot servlet when that option is used.
 * 
 * @author kevin
 *
 */
public class DisaggregationPlotData implements Serializable {
	
	private double maxContrEpsilonForGMT_Plot;

	private double[] mag_center, mag_binEdges;
	private double[] dist_center, dist_binEdges;
	
	private int NUM_E = 8;
	private double[][][] pdf3D;

	/**
	 * @param mag_center magnitude bin centers
	 * @param mag_binEdges magnitude bin edges (one more than centers)
	 * @param dist_center rupture distance bin centers (km)
	 * @param dist_binEdges rupture distance bin edges (km)
	 * @param maxContrEpsilonForGMT_Plot Z-axis (percent contribution) maximum for the plot
	 * @param numE number of epsilon bins
	 * @param pdf3D percent contribution indexed {@code [distance bin][magnitude bin][epsilon bin]}
	 */
	public DisaggregationPlotData(double[] mag_center, double[] mag_binEdges, double[] dist_center,
			double[] dist_binEdges, double maxContrEpsilonForGMT_Plot, int numE, double[][][] pdf3D) {
		this.mag_center = mag_center;
		this.mag_binEdges = mag_binEdges;
		this.dist_center = dist_center;
		this.dist_binEdges = dist_binEdges;
		this.maxContrEpsilonForGMT_Plot = maxContrEpsilonForGMT_Plot;
		this.NUM_E = numE;
		this.pdf3D = pdf3D;
	}

	/** @return magnitude bin centers */
	public double[] getMag_center() {
		return mag_center;
	}

	/** @param mag_center magnitude bin centers */
	public void setMag_center(double[] mag_center) {
		this.mag_center = mag_center;
	}

	/** @return magnitude bin edges */
	public double[] getMag_binEdges() {
		return mag_binEdges;
	}

	/** @param mag_binEdges magnitude bin edges */
	public void setMag_binEdges(double[] mag_binEdges) {
		this.mag_binEdges = mag_binEdges;
	}

	/** @return rupture distance bin centers (km) */
	public double[] getDist_center() {
		return dist_center;
	}

	/** @param dist_center rupture distance bin centers (km) */
	public void setDist_center(double[] dist_center) {
		this.dist_center = dist_center;
	}

	/** @return rupture distance bin edges (km) */
	public double[] getDist_binEdges() {
		return dist_binEdges;
	}

	/** @param dist_binEdges rupture distance bin edges (km) */
	public void setDist_binEdges(double[] dist_binEdges) {
		this.dist_binEdges = dist_binEdges;
	}

	/** @return the number of epsilon bins */
	public int getNUM_E() {
		return NUM_E;
	}

	/** @param num_e number of epsilon bins */
	public void setNUM_E(int num_e) {
		NUM_E = num_e;
	}

	/** @return percent contribution indexed {@code [distance bin][magnitude bin][epsilon bin]} */
	public double[][][] getPdf3D() {
		return pdf3D;
	}

	/** @param pdf3D percent contribution indexed {@code [distance bin][magnitude bin][epsilon bin]} */
	public void setPdf3D(double[][][] pdf3D) {
		this.pdf3D = pdf3D;
	}

	/** @return the Z-axis (percent contribution) maximum requested for the plot. The name is historical (GMT);
	 * renderers treat it as a minimum Z-axis extent, see
	 * {@link org.opensha.sha.calc.disaggregation.chart3d.PureJavaDisaggPlotter#buildChart(DisaggregationPlotData)} */
	public double getMaxContrEpsilonForGMT_Plot() {
		return maxContrEpsilonForGMT_Plot;
	}

	/** @param maxContrEpsilonForGMT_Plot Z-axis (percent contribution) maximum for the plot */
	public void setMaxContrEpsilonForGMT_Plot(
			double maxContrEpsilonForGMT_Plot) {
		this.maxContrEpsilonForGMT_Plot = maxContrEpsilonForGMT_Plot;
	}

}
