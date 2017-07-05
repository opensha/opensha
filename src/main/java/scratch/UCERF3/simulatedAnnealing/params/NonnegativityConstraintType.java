package scratch.UCERF3.simulatedAnnealing.params;

public enum NonnegativityConstraintType {
	
	TRY_ZERO_RATES_OFTEN, // sets rate to zero if they are perturbed to negative values (anneals much faster!)
	LIMIT_ZERO_RATES, // re-perturb rates if they are perturbed to negative values (still might not be accepted)
	PREVENT_ZERO_RATES; // Any perturbed zero rate MUST accept new value.  100% of rates will be nonnegative if numIterations >> number of Rates. 
}