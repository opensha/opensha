package org.opensha.nshmp2.erf.source;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;

/*
 * This is an implementation of the bizarre NSHMP EllB/WC94 Mag-Area relation.
 * See hazFXnga7c.f line ~1773
 */
class CA_MagAreaRelationship extends MagAreaRelationship {

	private final static String NAME = "NSHMP CA Mag-Area Relation";

	// mag cutoff based on EllB
	private final static double mag_cut = Math.log10(500) + 4.2;

	CA_MagAreaRelationship() {
		rake = Double.NaN;
	}

	@Override
	public double getMedianMag(double area) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMagStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMedianArea(double mag) {
		return (mag >= mag_cut) ? Math.pow(10.0, mag - 4.2) : // EllB
			Math.pow(10.0, (mag - 4.07) / 0.98); // WC94
	}

	@Override
	public double getAreaStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRake(double rake) {
		// do nothing; rake not considered in NSHMP
	}

	@Override
	public String getName() {
		return NAME;
	}

}
