package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.NSHMP_Utils.*;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/*
 * Wrapper class for magnitude and associated uncertainty data. Uncertainty
 * flags are initialized based on input data, however, due to quircky nshmp
 * rules, they may be overriden at some point and should always be checked prior
 * to calculation regardless of any uncertainty values present.
 */
class MagData implements Cloneable {

	int numEpiBranches;
	double[] epiDeltas;
	double[] epiWeights;

	double aleaSigma;
	int aleaMagCt;

	boolean hasEpistemic;
	boolean hasAleatory;
	boolean momentBalance;

	// dummy: flags are all false by default
	MagData() {
		epiDeltas = new double[] {0};
		epiWeights = new double[] {0};
	}
	
	MagData(List<String> src) {
		// epistemic
		numEpiBranches = readInt(src.get(0), 0);
		epiDeltas = readDoubles(src.get(1), numEpiBranches);
		epiWeights = readDoubles(src.get(2), numEpiBranches);
		hasEpistemic = numEpiBranches > 1;
		// aleatory
		String[] aleatoryMagDat = StringUtils.split(src.get(3));
		double aleatorySigmaTmp = readDouble(aleatoryMagDat, 0);
		momentBalance = aleatorySigmaTmp > 0.0;
		aleaSigma = Math.abs(aleatorySigmaTmp);
		aleaMagCt = readInt(aleatoryMagDat, 1) * 2 + 1;
		// two ways to kill aleatory
		hasAleatory = aleaMagCt > 1 && aleaSigma != 0.0;
	}

	double aleaMinMag(double mag) {
		return mag - 2 * aleaSigma;
	}

	double aleaMaxMag(double mag) {
		return mag + 2 * aleaSigma;
	}

	void suppressUncertainty() {
		hasEpistemic = false;
		hasAleatory = false;
	}

	@Override
	public MagData clone() {
		try {
			MagData clone = (MagData) super.clone();
			clone.epiDeltas = ArrayUtils.clone(epiDeltas);
			clone.epiWeights = ArrayUtils.clone(epiWeights);
			return clone;
		} catch (CloneNotSupportedException cnse) {
			return null;
		}
	}

	void toLog(Logger log) {
		if (log.isLoggable(Level.INFO)) {
			log.info(IOUtils.LINE_SEPARATOR + this.toString());
		}
	}
	
	@Override
	public String toString() {
		// @formatter:off
		return new StringBuilder()
		.append("============= MFD Data =============")
		.append(IOUtils.LINE_SEPARATOR)
		.append("Epistemic unc: ").append(hasEpistemic)
		.append(IOUtils.LINE_SEPARATOR)
		.append("     M deltas: ").append(Arrays.toString(epiDeltas))
		.append(IOUtils.LINE_SEPARATOR)
		.append("    M weights: ").append(Arrays.toString(epiWeights))
		.append(IOUtils.LINE_SEPARATOR)
		.append(" Aleatory unc: ").append(hasAleatory)
		.append(IOUtils.LINE_SEPARATOR)
		.append("      M sigma: ").append(aleaSigma)
		.append(IOUtils.LINE_SEPARATOR)
		.append("      M count: ").append(aleaMagCt)
		.append(IOUtils.LINE_SEPARATOR)
		.append("   Mo balance: ").append(momentBalance)
		.append(IOUtils.LINE_SEPARATOR).toString();
		// @formatter:off
	}
	
}
