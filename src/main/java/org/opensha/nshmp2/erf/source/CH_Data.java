package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.NSHMP_Utils.*;


/*
 * Wrapper for characteristic MFD data.
 */
class CH_Data {
	
	double mag;
	double rate;
	double weight;
	
	CH_Data(String src) {
		double[] chDat = readDoubles(src, 3);
		mag = chDat[0];
		rate = chDat[1];
		weight = chDat[2];
	}
	
	CH_Data(double mag, double rate, double weight) {
		this.mag = mag;
		this.rate = rate;
		this.weight = weight;
	}

}
