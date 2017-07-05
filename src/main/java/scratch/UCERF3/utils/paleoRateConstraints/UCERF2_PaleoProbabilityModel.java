package scratch.UCERF3.utils.paleoRateConstraints;

import scratch.UCERF3.FaultSystemRupSet;

public class UCERF2_PaleoProbabilityModel extends PaleoProbabilityModel {

	@Override
	public double getProbPaleoVisible(FaultSystemRupSet rupSet, int rupIndex,
			int sectIndex) {
		return getProbPaleoVisible(rupSet.getMagForRup(rupIndex), Double.NaN);
	}

	/**
	 * This returns the probability that the given magnitude event will be
	 * observed at the ground surface. This is based on equation 4 of Youngs et
	 * al. [2003, A Methodology for Probabilistic Fault Displacement Hazard
	 * Analysis (PFDHA), Earthquake Spectra 19, 191-219] using the coefficients
	 * they list in their appendix for "Data from Wells and Coppersmith (1993)
	 * 276 worldwide earthquakes". Their function has the following
	 * probabilities:
	 * 
	 * mag prob 5 0.10 6 0.45 7 0.87 8 0.98 9 1.00
	 * 
	 * @return
	 */
	@Override
	public double getProbPaleoVisible(double mag, double distAlongRup) {
		return Math.exp(-12.51 + mag * 2.053)
				/ (1.0 + Math.exp(-12.51 + mag * 2.053));
	}

}
