package org.opensha.sha.simulators.stiffness.patches;

import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;

/** Finite Okada rectangle; vertices run lower-away, lower-along, upper-along, upper-away. */
public class RectangularPatch extends StiffnessPatch {
	/** Along-strike length and down-dip width in meters. */
	public final double L, W;

	public RectangularPatch(Location center, int zone, char letter,
			double length, double width, FocalMechanism mechanism) {
		this(cartesian(center,zone,letter),length,width,mechanism);
	}

	public RectangularPatch(double[] center, double length, double width, FocalMechanism mechanism) {
		super(center,length*width,new double[4][3],mechanism);
		L=length;
		W=width;
		// Preserve the original Okada patch geometry and arithmetic order.
		double[] along={Math.sin(strike),Math.cos(strike),0};
		double[] down={Math.cos(dip)*Math.cos(strike),-Math.cos(dip)*Math.sin(strike),-Math.sin(dip)};
		double halfL=.5*L, halfW=.5*W;
		for (int i=0; i<3; i++) {
			corner[0][i]=center[i]-halfL*along[i]+halfW*down[i];
			corner[1][i]=center[i]+halfL*along[i]+halfW*down[i];
			corner[2][i]=center[i]+halfL*along[i]-halfW*down[i];
			corner[3][i]=center[i]-halfL*along[i]-halfW*down[i];
		}
	}
}
