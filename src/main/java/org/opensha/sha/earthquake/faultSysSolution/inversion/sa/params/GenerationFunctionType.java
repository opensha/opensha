package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params;

import java.util.Random;

public enum GenerationFunctionType { // how rates are perturbed each SA algorithm iteration
	/**
	 * rand()*0.001, which was used in UCERF3 but makes it difficult to get really low rupture rates and anneals slowly 
	 */
	UNIFORM_0p001 {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			return (r.nextDouble()-0.5)* 0.001;
		}
	},
	/**
	 * rand()*0.0001 
	 */
	UNIFORM_0p0001 {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			return (r.nextDouble()-0.5)* 0.0001;
		}
	},
	VARIABLE_NO_TEMP_DEPENDENCE {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			double basis = variablePerturbationBasis[index];
			if (basis == 0)
				basis = 0.00000001;
			return (r.nextDouble()-0.5) * basis * 1000d;
		}
	},
	GAUSSIAN {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			return (1/Math.sqrt(temperature)) * r.nextGaussian() * 0.0001 * Math.exp(1/(2*temperature));
		}
	},  
	TANGENT {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			return temperature * 0.001 * Math.tan(Math.PI * r.nextDouble() - Math.PI/2);
		}
	},
	POWER_LAW {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			double r2 = r.nextDouble();  
			return Math.signum(r2-0.5) * temperature * 0.001 * (Math.pow(1+1/temperature,Math.abs(2*r2-1))-1);
		}
	},
	EXPONENTIAL {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			double r2 = r.nextDouble();  
			return Math.pow(10, r2) * temperature * 0.001;
		}
	},
	EXPONENTIAL_SCALE {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			double r2 = max_exp - r.nextDouble()*exp_orders_of_mag;
			double scale = Math.pow(10, r2);
			return (r.nextDouble()-0.5)*scale;
		}
	},
	VARIABLE_EXPONENTIAL_SCALE {
		@Override
		public double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis) {
			double basis = Math.log10(Math.max(1e-10, variablePerturbationBasis[index]));
			double r2 = basis + 2d - 4d*r.nextDouble(); // +/- 2 orders of magnitude
			double scale = Math.pow(10, r2);
			return (r.nextDouble()-0.5)*scale;
		}
	};
	
	public boolean isVariable() {
		return this == VARIABLE_NO_TEMP_DEPENDENCE || this == VARIABLE_EXPONENTIAL_SCALE;
	}
	
	public abstract double getPerturbation(Random r, double temperature, int index, double[] variablePerturbationBasis);
	
	/**
	 * used by the EXPONENTIAL_SCALE method to determine the exponential range
	 */
	public static double exp_orders_of_mag = 8;
	/**
	 * used by the EXPONENTIAL_SCALE method to determine the exponential range
	 */
	public static double max_exp = -2;
}