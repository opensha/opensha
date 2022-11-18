package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import com.google.common.base.Preconditions;

/**
 * Utility class to strip out modules that are not essential to calculate hazard or interpret results
 * 
 * @author Kevin Milner
 *
 */
public class SolModuleStripper {

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("USAGE: SolModuleStripper <input-file> <output-file>");
			System.exit(1);
		}
		
		File inputFile = new File(args[0]);
		Preconditions.checkState(inputFile.exists(), "Input file doesn't exist: %s",
				inputFile.getAbsolutePath());
		
		FaultSystemSolution inputSol = FaultSystemSolution.load(inputFile);
		FaultSystemRupSet inputRupSet = inputSol.getRupSet();
		
		FaultSystemRupSet strippedRupSet = FaultSystemRupSet.buildFromExisting(inputRupSet, false).build();
		SlipAlongRuptureModel slipAlong = inputRupSet.getModule(SlipAlongRuptureModel.class);
		if (slipAlong != null)
			strippedRupSet.addModule(slipAlong);
		AveSlipModule aveSlip = inputRupSet.getModule(AveSlipModule.class);
		if (aveSlip != null)
			strippedRupSet.addModule(aveSlip);
		BuildInfoModule buildInfo = inputRupSet.getModule(BuildInfoModule.class);
		if (buildInfo != null)
			strippedRupSet.addModule(buildInfo);
		InfoModule info = inputRupSet.getModule(InfoModule.class);
		if (info != null)
			strippedRupSet.addModule(info);
		
		FaultSystemSolution strippedSol = new FaultSystemSolution(
				strippedRupSet, inputSol.getRateForAllRups());
		buildInfo = inputSol.getModule(BuildInfoModule.class);
		if (buildInfo != null)
			strippedSol.addModule(buildInfo);
		info = inputSol.getModule(InfoModule.class);
		if (info != null)
			strippedSol.addModule(info);
		GridSourceProvider gridProv = inputSol.getGridSourceProvider();
		if (gridProv != null)
			strippedSol.addModule(gridProv);
		RupMFDsModule mfds = inputSol.getModule(RupMFDsModule.class);
		if (mfds != null)
			strippedSol.addModule(mfds);
		
		strippedSol.write(new File(args[1]));
	}

}
