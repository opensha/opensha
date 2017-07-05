package scratch.UCERF3.simulatedAnnealing;

import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;

public interface SimulatedAnnealing {

	public void setCalculationParams(CoolingScheduleType coolingFunc,
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm,
			GenerationFunctionType perturbationFunc);

	public CoolingScheduleType getCoolingFunc();

	public void setCoolingFunc(CoolingScheduleType coolingFunc);

	public NonnegativityConstraintType getNonnegativeityConstraintAlgorithm();

	public void setNonnegativeityConstraintAlgorithm(
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm);

	public GenerationFunctionType getPerturbationFunc();

	public void setPerturbationFunc(GenerationFunctionType perturbationFunc);
	
	public void setVariablePerturbationBasis(double[] variablePerturbBasis);

	public double[] getBestSolution();

	/**
	 * 
	 * @return an array of energies containing: [ total, equality, entropy, inequality ]
	 */
	public double[] getBestEnergy();
	
	public double[] getBestMisfit();
	
	public double[] getBestInequalityMisfit();

	public void setResults(double[] Ebest, double[] xbest);
	
	public void setResults(double[] Ebest, double[] xbest, double[] misfit, double[] misfit_ineq);

	public long iterate(long numIterations);

	public long iterate(CompletionCriteria completion);

	public long[] iterate(long startIter, long startPerturbs, CompletionCriteria criteria);

}