package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.IndividualSolutionRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class AverageSolutionCreator {

	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println("USAGE: <output-solution> <input-solution-1> <input-solution-2> [... <input-solution-N>]");
			System.exit(1);
		}
		
		File outputFile = new File(args[0]);
		
		List<File> inputFiles = new ArrayList<>();
		for (int i=1; i<args.length; i++) {
			File file = new File(args[i]);
			Preconditions.checkArgument(file.exists(), "Input solution doesn't exist: %s", file.getAbsolutePath());
			inputFiles.add(file);
		}
		
		average(outputFile, inputFiles);
	}
	
	public static void average(File outputFile, List<File> inputFiles) throws IOException {
		FaultSystemSolution[] inputs = new FaultSystemSolution[inputFiles.size()];
		
		FaultSystemRupSet refRupSet = null;
		for (int i=0; i<inputs.length; i++) {
			File file = inputFiles.get(i);
			Preconditions.checkArgument(file.exists(), "Input solution doesn't exist: %s", file.getAbsolutePath());
			
			if (i >= 10) {
				// don't bother reloading the rupture set, assume equivalence
				if (i == 10) {
					// first load all available modules
					refRupSet.loadAllAvailableModules();
				}
				// clear the parent archive of the reference rup set so that it's not duplicated each time
				refRupSet.setParent(null);
				inputs[i] = FaultSystemSolution.load(file, refRupSet);
			} else {
				inputs[i] = FaultSystemSolution.load(file);
			}
			if (i == 0) {
				refRupSet = inputs[i].getRupSet();
			} else {
				Preconditions.checkState(refRupSet.isEquivalentTo(inputs[i].getRupSet()),
						"Solutions don't all use the same ruptures, cannot average");
			}
		}
		
		FaultSystemSolution avgSol = buildAverage(inputs);
		
		avgSol.write(outputFile);
	}
	
	public static FaultSystemSolution buildAverage(FaultSystemSolution... inputs) {
		Preconditions.checkState(inputs.length > 1);
		FaultSystemRupSet refRupSet = null;
		for (int i=0; i<inputs.length; i++) {
			if (i == 0) {
				refRupSet = inputs[i].getRupSet();
			} else {
				Preconditions.checkState(refRupSet.isEquivalentTo(inputs[i].getRupSet()),
						"Solutions don't all use the same ruptures, cannot average");
			}
		}
		
		ArrayList<double[]> ratesList = new ArrayList<>();
		for (FaultSystemSolution sol : inputs)
			ratesList.add(sol.getRateForAllRups());
		
		double scale = 1d/inputs.length;
		int numRups = refRupSet.getNumRuptures();
		double[] rates = new double[numRups];
		for (int r=0; r<numRups; r++) {
			for (int i=0; i<inputs.length; i++)
				rates[r] += inputs[i].getRateForRup(r);
			rates[r] *= scale;
		}
		
		FaultSystemSolution avgSol = new FaultSystemSolution(refRupSet, rates);
		
		// TODO average grid source provider
		
		if (inputs[0].hasModule(InversionMisfits.class)) {
			List<InversionMisfits> misfits = new ArrayList<>();
			
			System.out.println("Trying to average misfits...");
			for (FaultSystemSolution sol : inputs) {
				InversionMisfits misfit = sol.getModule(InversionMisfits.class);
				if (misfit == null || misfits.size() > 0 && !misfit.getConstraintRanges().equals(misfits.get(0).getConstraintRanges())) {
					misfits = null;
					System.out.println("Can't average misfits!");
					break;
				}
				misfits.add(misfit);
			}
			
			if (misfits != null)
				avgSol.addModule(InversionMisfits.average(misfits));
		}
		
		if (inputs[0].hasModule(AnnealingProgress.class)) {
			List<AnnealingProgress> progresses = new ArrayList<>();

			System.out.println("Trying to average misfits...");
			for (FaultSystemSolution sol : inputs) {
				AnnealingProgress progress = sol.getModule(AnnealingProgress.class);
				if (progress == null || progresses.size() > 0 && !progress.getEnergyTypes().equals(progresses.get(0).getEnergyTypes())) {
					progresses = null;
					System.out.println("Can't average progress!");
					break;
				}
				progresses.add(progress);
			}
			
			if (progresses != null)
				avgSol.addModule(AnnealingProgress.average(progresses));
		}
		
		if (inputs[0].hasModule(WaterLevelRates.class)) {
			WaterLevelRates wl = inputs[0].getModule(WaterLevelRates.class);
			for (int i=1; i<inputs.length; i++) {
				if (!inputs[i].hasModule(WaterLevelRates.class)) {
					wl = null;
					break;
				}
				WaterLevelRates oWL = inputs[i].getModule(WaterLevelRates.class);
				if (!equivalent(wl.get(), oWL.get())) {
					wl = null;
					break;
				}
			}
			if (wl != null)
				avgSol.addModule(wl);
		}
		
		if (inputs[0].hasModule(InitialSolution.class)) {
			InitialSolution initial = inputs[0].getModule(InitialSolution.class);
			for (int i=1; i<inputs.length; i++) {
				if (!inputs[i].hasModule(InitialSolution.class)) {
					initial = null;
					break;
				}
				InitialSolution oInitial = inputs[i].getModule(InitialSolution.class);
				if (!equivalent(initial.get(), oInitial.get())) {
					initial = null;
					break;
				}
			}
			if (initial != null)
				avgSol.addModule(initial);
		}
		
		if (inputs[0].hasModule(InversionConfiguration.class)) {
			InversionConfiguration config = inputs[0].getModule(InversionConfiguration.class);
			for (int i=1; i<inputs.length; i++) {
				if (!inputs[i].hasModule(InversionConfiguration.class)) {
					config = null;
					break;
				}
				InversionConfiguration oConfig = inputs[i].getModule(InversionConfiguration.class);
				if (!oConfig.equals(config)) {
					config = null;
					break;
				}
			}
			if (config != null)
				avgSol.addModule(config);
		}
		
		avgSol.addModule(new IndividualSolutionRates(avgSol, ratesList));
		
		String info = "Average of "+inputs.length+" solutions, generated with 'fst_solution_averager.sh'";
		if (inputs[0].hasModule(InfoModule.class))
			info += "\n\nInfo from first input solution:\n\n"+inputs[0].getModule(InfoModule.class).getText();
		avgSol.addModule(new InfoModule(info));
		
		return avgSol;
	}
	
	private static boolean equivalent(double[] vals1, double[] vals2) {
		if (vals1.length != vals2.length)
			return false;
		for (int i=0; i<vals1.length; i++)
			if ((float)vals1[i] != (float)vals2[i])
				return false;
		return true;
	}

}
