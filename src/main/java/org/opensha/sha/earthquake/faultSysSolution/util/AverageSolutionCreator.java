package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.IndividualSolutionRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
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
		Preconditions.checkState(!outputFile.exists(), "Output file already exists, delete first: %s", outputFile.getAbsolutePath());
		
		FaultSystemSolution[] inputs = new FaultSystemSolution[args.length-1];
		
		FaultSystemRupSet refRupSet = null;
		for (int i=0; i<inputs.length; i++) {
			File file = new File(args[i+1]);
			Preconditions.checkArgument(file.exists(), "Input solution doesn't exist: %s", file.getAbsolutePath());
			
			inputs[i] = FaultSystemSolution.load(file);
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
		
		// TODO average misfits?
		
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
				ImmutableList<InversionConstraint> constrs1 = config.getConstraints();
				ImmutableList<InversionConstraint> constrs2 = oConfig.getConstraints();
				if (constrs1.size() != constrs2.size()) {
					config = null;
					break;
				}
				for (int j=0; j<constrs1.size(); j++) {
					InversionConstraint c1 = constrs1.get(j);
					InversionConstraint c2 = constrs2.get(j);
					if (!c1.getClass().equals(c2.getClass())) {
						config = null;
						break;
					}
					if (!c1.getName().equals(c2.getName())) {
						config = null;
						break;
					}
					if ((float)c1.getWeight() != (float)c2.getWeight()) {
						config = null;
						break;
					}
				}
			}
			if (config != null)
				avgSol.addModule(config);
		}
		
		avgSol.addModule(new IndividualSolutionRates(avgSol, ratesList));
		
		avgSol.write(outputFile);
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
