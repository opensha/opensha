package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.NSHMP_Utils.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.opensha.nshmp2.util.SourceType;

/*
 * Wrapper for Gutenberg-Richter MFD data; handles all validation and NSHMP
 * corrections to fault and subduction GR mfds.
 */
class GR_Data {

	double aVal;
	double bVal;
	double mMin;
	double mMax;
	double dMag;
	double weight;
	int nMag;

	/* Usd for parsing WUS fault sources */
	GR_Data(String src, FaultSource fs, Logger log) {
		readSource(src);
		// check for too small a dMag
		validateDeltaMag(log, fs);
		// check if mags are not the same, center on closest dMag values
		recenterMagBins(log, fs);
		// check mag count
		validateMagCount(log, fs);
	}

	/* Used for parsing grid and subduction source inputs */
	GR_Data(String src, SourceType type) {
		if (type == SourceType.GRIDDED) {
		double[] grDat = readDoubles(src, 4);
		bVal = grDat[0];
		mMin = grDat[1];
		mMax = grDat[2];
		dMag = grDat[3];
		} else if (type == SourceType.SUBDUCTION) {
			readSource(src);
			updateMagCount();
		}
	}

	/* Used for building grid MFDs */
	GR_Data(double aVal, double bVal, double mMin, double mMax, double dMag) {
		this.aVal = aVal;
		this.bVal = bVal;
		this.mMin = mMin;
		this.mMax = mMax;
		this.dMag = dMag;
		recenterMagBins();
		updateMagCount();
	}

	private void readSource(String src) {
		String[] grDat = StringUtils.split(src);
		aVal = readDouble(grDat, 0);
		bVal = readDouble(grDat, 1);
		mMin = readDouble(grDat, 2);
		mMax = readDouble(grDat, 3);
		dMag = readDouble(grDat, 4);
		try {
			weight = readDouble(grDat, 5);
		} catch (ArrayIndexOutOfBoundsException oobe) {
			weight = 1;
		}
		nMag = 0;
	}
	
	private void validateDeltaMag(Logger log, FaultSource fs) {
		if (dMag <= 0.004) {
			StringBuilder sb = new StringBuilder().append("GR dMag [")
				.append(dMag).append("] is being increased to 0.1");
			FaultParser.appendFaultDat(sb, fs);
			log.warning(sb.toString());
			dMag = 0.1;
		}
	}

	// for fault sources
	private void recenterMagBins(Logger log, FaultSource fs) {
		if (mMin == mMax) {
			StringBuilder sb = new StringBuilder()
				.append("GR mMin and mMax are the same [").append(mMin)
				.append("]; switching to CH");
			FaultParser.appendFaultDat(sb, fs);
			log.warning(sb.toString());
		} else {
			mMin = mMin + dMag / 2.0;
			mMax = mMax - dMag / 2.0 + 0.0001; // 0.0001 necessary??
		}
	}

	// for grid sources
	private void recenterMagBins() {
		if (mMin == mMax) return;
		mMin = mMin + dMag / 2.0;
		mMax = mMax - dMag / 2.0;
	}

	private void validateMagCount(Logger log, FaultSource fs) {
		updateMagCount();
		if (nMag < 1) {
			RuntimeException rex = new RuntimeException(
				"Number of mags must be \u2265 1");
			StringBuilder sb = new StringBuilder()
				.append("GR nMag is less than 1");
			FaultParser.appendFaultDat(sb, fs);
			log.log(Level.WARNING, sb.toString(), rex);
			throw rex;
		}
	}

	/*
	 * Returns true if (1) multi-mag and mMax-epi < 6.5 or (2) single-mag and
	 * mMax-epi-2s < 6.5
	 */
	boolean hasMagExceptions(Logger log, FaultSource fs, MagData md) {
		if (nMag > 1) {
			// for multi mag consider only epistemic uncertainty
			double mMaxAdj = mMax + md.epiDeltas[0];
			if (mMaxAdj < 6.5) {
				StringBuilder sb = new StringBuilder()
					.append("Multi mag GR mMax [").append(mMax)
					.append("] with epistemic unc. [").append(mMaxAdj)
					.append("] is \u003C 6.5");
				FaultParser.appendFaultDat(sb, fs);
				log.warning(sb.toString());
				return true;
			}
		} else if (nMag == 1) {
			// for single mag consider epistemic and aleatory uncertainty
			double mMaxAdj = md.aleaMinMag(mMax + md.epiDeltas[0]);
			if (md.hasAleatory &&  (mMaxAdj < 6.5)) {
				System.out.println("PP " + md.hasAleatory + " " + (mMaxAdj < 6.5) + " " + fs.name);
				System.exit(0);
			}
			
			if (mMaxAdj < 6.5) {
				StringBuilder sb = new StringBuilder()
					.append("Single mag GR mMax [").append(mMax)
					.append("] with epistemic and aleatory unc. [")
					.append(mMaxAdj).append("] is \u003C 6.5");
				FaultParser.appendFaultDat(sb, fs);
				log.warning(sb.toString());
				return true;
			}
		} else {
			// log empty mfd
			StringBuilder sb = new StringBuilder()
				.append("GR MFD with no mags");
			FaultParser.appendFaultDat(sb, fs);
			log.warning(sb.toString());
		}
		return false;
	}

	void updateMagCount() {
		nMag = (int) ((mMax - mMin) / dMag + 1.4);
	}

}
