package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params;

public enum CoolingScheduleType {
	/**
	 * classical SA cooling schedule (Geman and Geman, 1984) (slow but ensures convergence)
	 */
	CLASSICAL_SA,
	/**
	 * fast SA cooling schedule (Szu and Hartley, 1987) (recommended)
	 */
	FAST_SA,
	/**
	 * very fast SA cooling schedule (Ingber, 1989)
	 */
	VERYFAST_SA,
	LINEAR; // Drops temperature uniformly from 1 to 0.  Only use with a completion criteria of a fixed number of iterations.
}