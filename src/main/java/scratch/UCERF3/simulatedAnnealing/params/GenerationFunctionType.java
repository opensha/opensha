package scratch.UCERF3.simulatedAnnealing.params;

public enum GenerationFunctionType { // how rates are perturbed each SA algorithm iteration
	/**
	 * recommended (box-car distribution of perturbations, no dependence on SA temperature)
	 */
	UNIFORM_NO_TEMP_DEPENDENCE,
	VARIABLE_NO_TEMP_DEPENDENCE,
	GAUSSIAN,  
	TANGENT,
	POWER_LAW,
	EXPONENTIAL;
}