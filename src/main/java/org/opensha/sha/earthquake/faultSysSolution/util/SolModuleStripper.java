package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveInput.ApacheZipFileInput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures.SingleStranded;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSectionInstances;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSetSplitMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

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
		ops.addOption(null, "keep-rup-mfds", false,
				"Flag to keep rupture MFDs (if present)");
		ops.addOption(null, "update-build-info", false,
				"Flag to update OpenSHA build info rather than retaining the version in the original file");
		
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
		
		File outputFile = new File(args[1]);
		
		boolean keepRupMFDs = cmd.hasOption("keep-rup-mfds");
		boolean updateBuildInfo = cmd.hasOption("update-build-info");
		
		try {
			ZipFile inputZip = new ZipFile(inputFile);
			if (!FaultSystemSolution.isSolution(inputZip)) {
				// see if it's a solution logic tree
				System.out.println("Input file isn't a FaultSystemSolution, trying SolutionLogicTree");
				ApacheZipFileInput sltInput = new ArchiveInput.ApacheZipFileInput(inputFile);
				SolutionLogicTree slt = SolutionLogicTree.load(sltInput);
				SolutionLogicTree.simplify(slt, outputFile, keepRupMFDs, updateBuildInfo);
				inputZip.close();
				sltInput.close();
				System.exit(0);
			}
			FaultSystemSolution inputSol = FaultSystemSolution.load(inputZip);
			
			FaultSystemSolution strippedSol = stripModules(inputSol, gridMinMag, keepRupMFDs, updateBuildInfo);
			strippedSol.write(outputFile);
			inputZip.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static FaultSystemSolution stripModules(FaultSystemSolution inputSol, double gridMinMag) {
		return stripModules(inputSol, gridMinMag, false, false);
	}
	
	public static FaultSystemSolution stripModules(FaultSystemSolution inputSol, double gridMinMag, boolean keepRupMFDs, boolean updateBuildInfo) {
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
		BuildInfoModule updatedBuildInfo = null;
		try {
			updatedBuildInfo = updateBuildInfo ? BuildInfoModule.detect() : null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (updateBuildInfo) {
			if (updatedBuildInfo != null)
				strippedRupSet.addModule(updatedBuildInfo);
		} else {
			BuildInfoModule buildInfo = inputRupSet.getModule(BuildInfoModule.class);
			if (buildInfo != null)
				strippedRupSet.addModule(buildInfo);
		}
		InfoModule info = inputRupSet.getModule(InfoModule.class);
		if (info != null)
			strippedRupSet.addModule(info);
		ClusterRuptures.SingleStranded ssClusterRups = inputRupSet.getModule(SingleStranded.class);
		if (ssClusterRups != null)
			strippedRupSet.addModule(ssClusterRups);
		
		ProxyFaultSectionInstances proxies = inputRupSet.getModule(ProxyFaultSectionInstances.class);
		RuptureSetSplitMappings splitMappings = null;
		FaultSystemSolution strippedSol;
		if (proxies != null) {
			System.out.println("Splitting out proxy ruptures");
			strippedRupSet = proxies.getSplitRuptureSet(strippedRupSet);
			splitMappings = strippedRupSet.requireModule(RuptureSetSplitMappings.class);
			strippedRupSet.removeModule(splitMappings);
			double[] mappedRates = new double[strippedRupSet.getNumRuptures()];
			for (int r=0; r<mappedRates.length; r++)
				mappedRates[r] = inputSol.getRateForRup(splitMappings.getOrigRupID(r)) * splitMappings.getNewRupWeight(r);
			strippedSol = new FaultSystemSolution(strippedRupSet, mappedRates);
			float newSum = (float)strippedSol.getTotalRateForAllFaultSystemRups();
			float oldSum = (float)inputSol.getTotalRateForAllFaultSystemRups();
			Preconditions.checkState(newSum == oldSum, "Proxy rupture expansion changed the rupture rate from %s to %s", oldSum, newSum);
		} else {
			strippedSol = new FaultSystemSolution(strippedRupSet, inputSol.getRateForAllRups());
		}
		if (updateBuildInfo) {
			if (updatedBuildInfo != null)
				strippedSol.addModule(updatedBuildInfo);
		} else {
			BuildInfoModule buildInfo = inputSol.getModule(BuildInfoModule.class);
			if (buildInfo != null)
				strippedSol.addModule(buildInfo);
		}
		info = inputSol.getModule(InfoModule.class);
		if (info != null)
			strippedSol.addModule(info);
		GridSourceProvider gridProv = inputSol.getGridSourceProvider();
		if (gridProv != null) {
			if (gridMinMag > 0d)
				gridProv = gridProv.getAboveMinMag((float)gridMinMag);
			if (splitMappings != null && gridProv instanceof GridSourceList) {
				// need to update sects
				GridSourceList gridSources = (GridSourceList)gridProv;
				EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRupsMap = new EnumMap<>(TectonicRegionType.class);
				for (TectonicRegionType trt : gridSources.getTectonicRegionTypes()) {
					List<List<GriddedRupture>> modLists = new ArrayList<>(gridSources.getNumLocations());
					for (int gridIndex=0; gridIndex<gridSources.getNumLocations(); gridIndex++) {
						List<GriddedRupture> origRups = gridSources.getRuptures(trt, gridIndex);
						if (origRups.isEmpty()) {
							modLists.add(null);
						} else {
							List<GriddedRupture> modRups = new ArrayList<>(origRups.size());
							for (GriddedRupture rup : origRups) {
								if (rup.associatedSections == null) {
									modRups.add(rup);
								} else {
									// need to update
									List<Integer> modSects = new ArrayList<>();
									List<Double> modFracts = new ArrayList<>();
									for (int s=0; s<rup.associatedSections.length; s++) {
										List<Integer> mappedIDs = splitMappings.getNewSectIDs(rup.associatedSections[s]);
										for (int mappedID : mappedIDs) {
											modSects.add(mappedID);
											modFracts.add(rup.associatedSectionFracts[s] * splitMappings.getNewSectWeight(mappedID));
										}
									}
									modRups.add(new GriddedRupture(rup.gridIndex, rup.location, rup.properties, rup.rate,
											Ints.toArray(modSects), Doubles.toArray(modFracts)));
								}
							}
							modLists.add(modRups);
						}
					}
					trtRupsMap.put(trt, modLists);
				}
				gridProv = new GridSourceList.Precomputed(gridSources, trtRupsMap);
			}
			strippedSol.addModule(gridProv);
		}
		if (keepRupMFDs) {
			RupMFDsModule mfds = inputSol.getModule(RupMFDsModule.class);
			if (mfds != null) {
				if (splitMappings != null) {
					DiscretizedFunc[] modMFDs = new DiscretizedFunc[strippedRupSet.getNumRuptures()];
					for (int r=0; r<modMFDs.length; r++) {
						int origID = splitMappings.getOrigRupID(r);
						DiscretizedFunc origMFD = mfds.getRuptureMFD(origID);
						if (origMFD != null) {
							double weight = splitMappings.getNewRupWeight(r);
							if (weight == 1d) {
								// copy directly
								modMFDs[r] = origMFD;
							} else {
								// scale it
								DiscretizedFunc modMFD = origMFD.deepClone();
								modMFD.scale(weight);
								modMFDs[r] = modMFD;
							}
						}
					}
					mfds = new RupMFDsModule(strippedSol, modMFDs);
				}
				strippedSol.addModule(mfds);
			}
		}
		
		return strippedSol;
	}

}
