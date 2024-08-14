package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSectionInstances;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Utility class to strip out modules that are not essential to calculate hazard or interpret results
 * 
 * @author Kevin Milner
 *
 */
public class SolModuleStripper {
	
	private static final double GRID_MIN_MAG_DEFAULT = 5d;
	
	public static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption(null, "grid-min-mag", true,
				"Filter grid source provider to only include ruptures above this magnitude. Default is M"+(float)GRID_MIN_MAG_DEFAULT);
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SolModuleStripper.class);
		args = cmd.getArgs();
		if (args.length != 2) {
			System.err.println("USAGE: SolModuleStripper <input-file> <output-file>");
			System.exit(1);
		}
		
		double gridMinMag = cmd.hasOption("grid-min-mag") ? Double.parseDouble(cmd.getOptionValue("grid-min-mag")) : GRID_MIN_MAG_DEFAULT;
		
		File inputFile = new File(args[0]);
		Preconditions.checkState(inputFile.exists(), "Input file doesn't exist: %s",
				inputFile.getAbsolutePath());
		FaultSystemSolution inputSol = FaultSystemSolution.load(inputFile);
		
		File outputFile = new File(args[1]);
		FaultSystemSolution strippedSol = stripModules(inputSol, gridMinMag);
		strippedSol.write(outputFile);
	}
	
	public static FaultSystemSolution stripModules(FaultSystemSolution inputSol, double gridMinMag) {
		FaultSystemRupSet inputRupSet = inputSol.getRupSet();
		
		FaultSystemRupSet strippedRupSet = FaultSystemRupSet.buildFromExisting(inputRupSet, false).build();
		RupSetTectonicRegimes tectonics = inputRupSet.getModule(RupSetTectonicRegimes.class);
		if (tectonics != null)
			strippedRupSet.addModule(tectonics);
		SlipAlongRuptureModel slipAlong = inputRupSet.getModule(SlipAlongRuptureModel.class);
		if (slipAlong != null)
			strippedRupSet.addModule(slipAlong);
		AveSlipModule aveSlip = inputRupSet.getModule(AveSlipModule.class);
		if (aveSlip != null)
			strippedRupSet.addModule(aveSlip);
		ProxyFaultSectionInstances proxies = inputRupSet.getModule(ProxyFaultSectionInstances.class);
		if (proxies != null)
			strippedRupSet.addModule(proxies);
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
		if (gridProv != null) {
			if (gridMinMag > 0d)
				gridProv = gridProv.getAboveMinMag((float)gridMinMag);
			strippedSol.addModule(gridProv);
		}
		RupMFDsModule mfds = inputSol.getModule(RupMFDsModule.class);
		if (mfds != null)
			strippedSol.addModule(mfds);
		
		return strippedSol;
	}

}
