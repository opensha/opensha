package org.opensha.sha.imr.attenRelImpl.nshmp;

import static gov.usgs.earthquake.nshmp.gmm.Imt.*;

import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.gmm.Imt;

/**
 * This copies (ugh) constants and other things that NSHMP-lib uses but doesn't expose
 */
public class NSHMP_Config {
	
	/**
	 * Array of intensity measure levels (IMLs) for the given spectral period. 0 can be used for PGA and -1 can be used for PGV. 
	 * @param period
	 * @return
	 */
	public static double[] imlsFor(double period) {
		if (period == 0d) {
			return imlsFor(PGA);
		} else if (period == -1d) {
			return imlsFor(PGV);
		} else {
			Preconditions.checkState(period > 0d);
			return imlsFor(fromPeriod(period));
		}
	}
	
	/*
	 * Begin IMLs for IMTs
	 * 
	 * The following were copied from nshmp-haz 1.7.15 on 12/1/2025
	 */
	
	public static double[] imlsFor(Imt imt) {
		if (imt.isSA() || imt == PGA) {
			return imlMap.ceilingEntry(imt).getValue();
		} else if (imt == PGV) {
			return IMLS_PGV;
		} else {
			/* IMLs not yet assigned for IMT. */
			throw new UnsupportedOperationException();
		}
	}

	/*
	 * IML defaults. These were assigned in nshm-conus-2018 and set as custom
	 * overrides. We now include them as built-in default values. For any
	 * periods in between those explicitely specified, values for the next
	 * highest supported period is used (see navigable imlMap, below). Users
	 * need only specify overriding IMLs in the "imls" hazaard config member.
	 */
	static final double[] IMLS_SA0P01 = {
			0.00233, 0.00350, 0.00524, 0.00786, 0.0118, 0.0177, 0.0265, 0.0398, 0.0597, 0.0896, 0.134,
			0.202, 0.302, 0.454, 0.680, 1.02, 1.53, 2.30, 3.44, 5.17 };
	static final double[] IMLS_SA0P02 = {
			0.00283, 0.00424, 0.00637, 0.00955, 0.0143, 0.0215, 0.0322, 0.0483, 0.0725, 0.109, 0.163,
			0.245, 0.367, 0.551, 0.826, 1.24, 1.86, 2.79, 4.18, 6.27 };
	static final double[] IMLS_SA0P5 = {
			0.00333, 0.00499, 0.00749, 0.0112, 0.0169, 0.0253, 0.0379, 0.0569, 0.0853, 0.128, 0.192,
			0.288, 0.432, 0.648, 0.972, 1.46, 2.19, 3.28, 4.92, 7.38 };
	static final double[] IMLS_SA2P0 = {
			0.00250, 0.00375, 0.00562, 0.00843, 0.0126, 0.0190, 0.0284, 0.0427, 0.0640, 0.0960, 0.144,
			0.216, 0.324, 0.486, 0.729, 1.09, 1.64, 2.46, 3.69, 5.54 };
	static final double[] IMLS_SA3P0 = {
			0.00200, 0.00300, 0.00449, 0.00674, 0.0101, 0.0152, 0.0228, 0.0341, 0.0512, 0.0768, 0.115,
			0.173, 0.259, 0.389, 0.583, 0.875, 1.31, 1.97, 2.95, 4.43 };
	static final double[] IMLS_SA4P0 = {
			0.00133, 0.00200, 0.00300, 0.00449, 0.00674, 0.0101, 0.0152, 0.0228, 0.0341, 0.0512, 0.0768,
			0.115, 0.173, 0.259, 0.389, 0.583, 0.875, 1.31, 1.97, 2.95 };
	static final double[] IMLS_SA5P0 = {
			0.000999, 0.00150, 0.00225, 0.00337, 0.00506, 0.00758, 0.0114, 0.0171, 0.0256, 0.0384,
			0.0576, 0.0864, 0.130, 0.194, 0.292, 0.437, 0.656, 0.984, 1.48, 2.21 };
	static final double[] IMLS_SA7P5 = {
			0.000499, 0.000749, 0.00112, 0.00169, 0.00253, 0.00379, 0.00569, 0.00853, 0.0128, 0.0192,
			0.0288, 0.0432, 0.0648, 0.0972, 0.146, 0.219, 0.328, 0.492, 0.738, 1.11 };
	static final double[] IMLS_SA10P0 = {
			0.000333, 0.000499, 0.000749, 0.00112, 0.00169, 0.00253, 0.00379, 0.00569, 0.00853, 0.0128,
			0.0192, 0.0288, 0.0432, 0.0648, 0.0972, 0.146, 0.219, 0.328, 0.492, 0.738 };

	static final double[] IMLS_PGV = {
			0.237, 0.355, 0.532, 0.798, 1.19, 1.80, 2.69, 4.04, 6.06, 9.09, 13.6, 20.5, 30.7,
			46.0, 69.0, 103.0, 155.0, 233.0, 349.0, 525.0 };

	/* NavigableMap facilitates assigning IMLs to ranges of spectral periods. */
	static final NavigableMap<Imt, double[]> imlMap = new TreeMap<>(Map.of(
			SA0P01, IMLS_SA0P01,
			SA0P02, IMLS_SA0P02,
			SA0P5, IMLS_SA0P5,
			SA2P0, IMLS_SA2P0,
			SA3P0, IMLS_SA3P0,
			SA4P0, IMLS_SA4P0,
			SA5P0, IMLS_SA5P0,
			SA7P5, IMLS_SA7P5,
			SA10P0, IMLS_SA10P0));
	
	/*
	 * END IMLs for IMTs
	 */

	public static void main(String[] args) {
		System.out.println(Arrays.toString(imlsFor(PGA)));
	}

}
