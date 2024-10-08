package org.opensha.sha.faultSurface.utils;

import java.util.function.Supplier;

import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm;
import org.opensha.sha.util.NSHMP_Util;

public enum PointSourceDistanceCorrections implements Supplier<PointSourceDistanceCorrection> {
	
	NONE("None", new PointSourceDistanceCorrection() {

		@Override
		public double getCorrectedDistanceJB(EqkRupture rup, double horzDist) {
			return horzDist;
		}
		
	}),
	FIELD("Field", new PointSourceDistanceCorrection() {
		// TODO is there a reference? more specific name?

		@Override
		public double getCorrectedDistanceJB(EqkRupture rup, double horzDist) {
			// Wells and Coppersmith L(M) for "all" focal mechanisms
			// this correction comes from work by Ned Field and Bruce Worden
			// it assumes a vertically dipping straight fault with random
			// hypocenter and strike
			double rupLen =  Math.pow(10.0,-3.22+0.69*rup.getMag());
			double corrFactor = 0.7071 + (1.0-0.7071)/(1 + Math.pow(rupLen/(horzDist*0.87),1.1));
			return horzDist*corrFactor;
		}
		
	}),
	NSHM_2008("USGS NSHM (2008)", new PointSourceDistanceCorrection() {

		@Override
		public double getCorrectedDistanceJB(EqkRupture rup, double horzDist) {
			double mag = rup.getMag();
			if(mag<=6) {
				return horzDist;
			} else if (horzDist == 0d) {
				return 0d;
			} else { //if (mag<=7.6) {
				// NSHMP getMeanRJB is built on the assumption of 0.05 M
				// centered bins. Non-UCERF erf's often do not make
				// this assumption and are 0.1 based so we push
				// the value down to the next closest compatible M
				
				// this was Peter's original correction, but it explodes if it's given say 6.449999999999999 (which converts to 6.39999999999999)
//				double adjMagAlt = ((int) (mag*100) % 10 != 5) ? mag - 0.05 : mag;
				double adjMag = ((double)Math.round(mag/0.05))*0.05;
				if (adjMag > 8.6) adjMag = 8.55;
//				if(adjMagAlt != adjMag)
//					System.out.println("mag,adj,alt:\t"+mag+"\t"+adjMag+"\t"+adjMagAlt);
				return NSHMP_Util.getMeanRJB(adjMag, horzDist);
			}
		}
		
	}),
	NSHM_2013("USGS NSHM (2013)", new PointSourceDistanceCorrection() {

		@Override
		public double getCorrectedDistanceJB(EqkRupture rup, double horzDist) {
			return PointSourceNshm.correctedRjb(rup.getMag(), horzDist);
		}
		
	});
	
	private String name;
	private PointSourceDistanceCorrection corr;

	private PointSourceDistanceCorrections(String name, PointSourceDistanceCorrection corr) {
		this.name = name;
		this.corr = corr;
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public PointSourceDistanceCorrection get() {
		return corr;
	}

}
