package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.modules.IndividualSolutionRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;

import com.google.common.base.Preconditions;

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
		
		// now build average modules
		for (OpenSHA_Module module : inputs[0].getModulesAssignableTo(AverageableModule.class, true)) {
			Preconditions.checkState(module instanceof AverageableModule<?>);
			System.out.println("Building average instance of "+module.getName());
			try {
				AveragingAccumulator<?> accumulator = ((AverageableModule<?>)module).averagingAccumulator();
				
				for (FaultSystemSolution sol : inputs)
					accumulator.processContainer(sol, scale);
				
				OpenSHA_Module avgModule = accumulator.getAverage();
				
				if (avgModule == null)
					System.err.println("Averaging returned null for "+module.getName()+", skipping");
				else
					avgSol.addModule(avgModule);
			} catch (Exception e) {
				System.err.println("Error averaging module: "+module.getName());
				e.printStackTrace();
				System.err.flush();
			}
		}
		
		// TODO average grid source provider
		
		// special case, see if inversion config is the same for each, and if so attach to final
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
