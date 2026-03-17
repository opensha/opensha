package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ReweightEvenFitSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;

public interface InversionSolver {
	
	public default FaultSystemSolution run(InversionConfigurationFactory factory, LogicTreeBranch<?> branch, int threads)
			throws IOException {
		return run(factory, branch, threads, null);
	}
	
	public default FaultSystemSolution run(InversionConfigurationFactory factory, LogicTreeBranch<?> branch, int threads,
			CommandLine cmd) throws IOException {
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch, threads);
		
		return run(rupSet, factory, branch, threads, cmd);
	}
	
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfigurationFactory factory,
			LogicTreeBranch<?> branch, int threads, CommandLine cmd) throws IOException;
	
	public default FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config) {
		return run(rupSet, config, null);
	}
	
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info);
	
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, InversionInputGenerator inputs, String info);
	
	public static class Default implements InversionSolver {

		@Override
		public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfigurationFactory factory,
				LogicTreeBranch<?> branch, int threads, CommandLine cmd) throws IOException {
			InversionConfiguration config = factory.buildInversionConfig(rupSet, branch, threads);
			if (branch != rupSet.getModule(LogicTreeBranch.class))
				rupSet.addModule(branch);
			if (cmd != null)
				// apply any command line overrides
				config = InversionConfiguration.builder(config).forCommandLine(cmd).build();
			
			FaultSystemSolution sol = run(rupSet, config, null);
			
			// attach any relevant modules before writing out
			SolutionProcessor processor = factory.getSolutionLogicTreeProcessor();
			
			if (processor != null)
				processor.processSolution(sol, branch);
			
			return sol;
		}

		@Override
		public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info) {
			InversionInputGenerator inputs = new InversionInputGenerator(rupSet, config);
			inputs.generateInputs(true);
			
			return run(rupSet, config, inputs, info);
		}
		
		public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, InversionInputGenerator inputs, String info) {
			CompletionCriteria completion = config.getCompletionCriteria();
			if (completion == null)
				throw new IllegalArgumentException("Must supply total inversion time or iteration count");

			inputs.columnCompress();
			
			SimulatedAnnealing sa = config.buildSA(inputs);
			
			ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
			if (sa instanceof SerialSimulatedAnnealing) {
				// don't track every iteration
				if (completion instanceof IterationCompletionCriteria) {
					long targetNum = 10000l;
					long iters = ((IterationCompletionCriteria)completion).getMinIterations();
					if (iters > targetNum*2l) {
						long mod = iters/targetNum;
						progress.setIterationModulus(mod);
					}
				} else {
					// just guess, set to 100
					progress.setIterationModulus(100l);
				}
			}
			
			System.out.println("SA Parameters:");
			System.out.println("\tImplementation: "+sa.getClass().getName());
			System.out.println("\tCompletion Criteria: "+completion);
			System.out.println("\tPerturbation Function: "+sa.getPerturbationFunc());
			System.out.println("\tNon-Negativity Constraint: "+sa.getNonnegativeityConstraintAlgorithm());
			System.out.println("\tCooling Schedule: "+sa.getCoolingFunc());
			if (sa instanceof ThreadedSimulatedAnnealing) {
				ThreadedSimulatedAnnealing tsa = (ThreadedSimulatedAnnealing)sa;
				System.out.println("\tTop-Level Threads: "+tsa.getNumThreads());
				System.out.println("\tSub-Completion Criteria: "+tsa.getSubCompetionCriteria());
				System.out.println("\tAveraging? "+tsa.isAverage());
			}
			
			System.out.println("Annealing!");
			sa.iterate(progress);
			
			if (sa instanceof ThreadedSimulatedAnnealing)
				((ThreadedSimulatedAnnealing)sa).shutdown();
			
			System.out.println("DONE. Building solution...");
			double[] rawSol = sa.getBestSolution();
			double[] rates = inputs.adjustSolutionForWaterLevel(rawSol);
			
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
			// add inversion progress
			sol.addModule(progress.getProgress());
			// add water level rates
			if (inputs.getWaterLevelRates() != null)
				sol.addModule(new WaterLevelRates(inputs.getWaterLevelRates()));
			if (inputs.hasInitialSolution())
				sol.addModule(new InitialSolution(inputs.getInitialSolution()));
			sol.addModule(config);
			InversionMisfits misfits = new InversionMisfits(sa);
			sol.addModule(misfits);
			sol.addModule(misfits.getMisfitStats());
			if (info != null)
				sol.setInfoString(info);
			if (sa instanceof ReweightEvenFitSimulatedAnnealing)
				sol.addModule(((ReweightEvenFitSimulatedAnnealing)sa).getMisfitProgress());
			
			return sol;
		}
		
	}

}
