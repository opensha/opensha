package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ConnectivityClusters;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class ClusterSplitterAndCombinerTool {
	
	private static Options createOptions() {
		Options ops = new Options();

		ops.addOption(FaultSysTools.helpOption());
		
		ops.addOption(null, "split", false, "Flag to enable splitting mode; --input is assumed to be a single file and "
				+ "--output is assumed to be a directory");
		ops.addOption(null, "combine", false, "Flag to enable combining mode; --input is assumed to be a directory "
				+ "and --output is assumed to be a file");
		
		ops.addRequiredOption(null, "input", true, "Path to the input solution/rupture set file (if --split) or "
				+ "directory containing cluster-specific zip files (if --combine). In combine mode, input zip files "
				+ "must start with `cluster` and end with `.zip` (and no other files should match that pattern).");
		ops.addRequiredOption(null, "output", true, "Path to the putput solution/rupture set file (if --combine) or "
				+ "directory to write cluster-specific zip files (if --split)");
		
		ops.addOption(null, "full-rupture-set", true, "Path the original full rupture set, optionally used with "
				+ "--combine if directory supplied via --input contains solutions. If supplied, the given rupture set "
				+ "will be used and all attached modules kept, rather than building a stripped down rupture set from "
				+ "the sections and ruptures encountered in the input files. Cluster-specific rupture sets must have "
				+ "mappings to their original sections/ruptures attached (which they will if they were split using this tool).");
		ops.addOption(null, "remap", false, "Flag to ignore attached rupture set mappings and assign new indexes in "
				+ "combine mode. This is useful if you want to delete a cluster.");
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		Options options = createOptions();
		
		CommandLine cmd = FaultSysTools.parseOptions(options, args, ClusterSplitterAndCombinerTool.class);
		FaultSysTools.checkPrintHelp(options, cmd, ClusterSplitterAndCombinerTool.class);
		
		boolean split = cmd.hasOption("split");
		boolean combine = cmd.hasOption("combine");
		
		Preconditions.checkArgument(split || combine, "Must supply either --split or --combine");
		Preconditions.checkArgument(!split || !combine, "Can't supply both --split and --combine");
		
		File input = new File(cmd.getOptionValue("input"));
		Preconditions.checkState(input.exists(), "Input path doesn't exist: %s", input.getAbsolutePath());
		
		File output = new File(cmd.getOptionValue("output"));
		
		ModuleArchive.VERBOSE_DEFAULT = false;
		
		if (split) {
			Preconditions.checkState(input.isFile(), "Input must be a file in split mode");
			Preconditions.checkState(output.isDirectory() || output.mkdir(),
					"Could not create directory for split output: %s", output.getAbsolutePath());
			
			ArchiveInput archive = ArchiveInput.getDefaultInput(input);
			
			FaultSystemRupSet rupSet;
			FaultSystemSolution sol;
			RupMFDsModule solMFDs = null;
			if (FaultSystemSolution.isSolution(archive)) {
				sol = FaultSystemSolution.load(archive);
				rupSet = sol.getRupSet();
				solMFDs = sol.getModule(RupMFDsModule.class);
			} else {
				rupSet = FaultSystemRupSet.load(archive);
				sol = null;
			}
			
			ConnectivityClusters clusters = rupSet.getModule(ConnectivityClusters.class);
			
			if (clusters == null) {
				System.out.println("Connectivity clusters not already attached, will build");
				clusters = ConnectivityClusters.build(rupSet);
			}
			System.out.println("Have "+clusters.size()+" clusters");
			Preconditions.checkState(clusters.size() > 1, "Must have at least 2 clusters to split");
			
			for (int c=0; c<clusters.size(); c++) {
				ConnectivityCluster cluster = clusters.get(c);
				System.out.println("Building subset "+c+"/"+clusters.size()
						+" for "+cluster.getNumSections()+" sects and "+cluster.getNumRuptures()+" ruptures");
				FaultSystemRupSet subset = rupSet.getForSectionSubSet(cluster.getSectIDs());
				
				File outputFile = new File(output, "cluster_"+c+"_"+cluster.getNumSections()+"sects_"+cluster.getNumRuptures()+"rups.zip");
				
				if (sol == null) {
					// rupture set mode
					subset.write(outputFile);
				} else {
					RuptureSubSetMappings mappings = subset.requireModule(RuptureSubSetMappings.class);
					// need to split the solution
					double[] splitRates = new double[subset.getNumRuptures()];
					DiscretizedFunc[] splitMFDs = solMFDs == null ? null : new DiscretizedFunc[subset.getNumRuptures()];
					boolean anyMFDs = false;
					for (int i=0; i<splitRates.length; i++) {
						splitRates[i] = sol.getRateForRup(mappings.getOrigRupID(i));
						if (splitMFDs != null) {
							DiscretizedFunc mfd = solMFDs.getRuptureMFD(mappings.getOrigRupID(i));
							if (mfd != null) {
								anyMFDs = true;
								splitMFDs[i] = mfd;
							}
						}
					}
					
					FaultSystemSolution subsetSol = new FaultSystemSolution(subset, splitRates);
					if (anyMFDs)
						subsetSol.addModule(new RupMFDsModule(subsetSol, splitMFDs));
					
					subsetSol.write(outputFile);
				}
			}
			
			System.out.println("DONE; wrote "+clusters.size()+" cluster-specific "+(sol == null ? "rupture sets" : "solutions"));
		} else {
			Preconditions.checkState(input.isDirectory(), "Input must be a directory in combine mode");
			Preconditions.checkState(!output.isDirectory(),
					"Output not be a directory in combine mode: %s", output.getAbsolutePath());
			
			File[] files = input.listFiles();
			Arrays.sort(files, new FileNameComparator());
			
			FaultSystemRupSet fullRupSet = null;
			if (cmd.hasOption("full-rupture-set"))
				fullRupSet = FaultSystemRupSet.load(new File(cmd.getOptionValue("full-rupture-set")));
			
			List<FaultSystemRupSet> clusterRupSets = new ArrayList<>();
			List<FaultSystemSolution> clusterSolutions = new ArrayList<>();
			
			boolean allHaveMappings = !cmd.hasOption("remap");
			int totalNumSects = 0;
			int totalNumRuptures = 0;
			
			for (File file : files) {
				
				if (file.isFile() &&  file.getName().toLowerCase().startsWith("cluster_")
						&& file.getName().toLowerCase().endsWith(".zip")) {
					System.out.println("Loading "+file.getName());
					
					ArchiveInput archive = ArchiveInput.getDefaultInput(file);
					
					FaultSystemRupSet rupSet;
					if (clusterSolutions != null && FaultSystemSolution.isSolution(archive)) {
						FaultSystemSolution sol = FaultSystemSolution.load(archive);
						clusterSolutions.add(sol);
						rupSet = sol.getRupSet(); 
					} else {
						if (clusterSolutions != null) {
							Preconditions.checkState(clusterSolutions.isEmpty(),
									"%s is a rupture set only but we previously had solutions", file.getName());
							clusterSolutions = null;
						}
						rupSet = FaultSystemRupSet.load(archive);
					}
					allHaveMappings &= rupSet.hasModule(RuptureSubSetMappings.class);
					clusterRupSets.add(rupSet);
					totalNumSects += rupSet.getNumSections();
					totalNumRuptures += rupSet.getNumRuptures();
				}
			}
			System.out.println("Loaded "+clusterRupSets.size()+" cluster-specific "+(clusterSolutions == null ? "rupture sets" : "solutions")
					+" with "+totalNumSects+" sections and "+totalNumRuptures+" ruptures");
			Preconditions.checkState(clusterRupSets.size() > 1, "Must have at least 2 inputs to combine");
			Preconditions.checkState(clusterSolutions == null || clusterSolutions.size() == clusterRupSets.size());
			
			if (fullRupSet == null) {
				// need to merge
				System.out.println("Reconstructing the rupture set");
				if (allHaveMappings)
					System.out.println("All inputs have RuptureSubSetMappings attached, will use existing indexes");
				else
					System.out.println("Missing attached RuptureSubSetMappings attached, will create new subsection and rupture indexes");
				List<FaultSection> sects = new ArrayList<>(totalNumSects);
				for (int s=0; s<totalNumSects; s++)
					sects.add(null);
				List<List<Integer>> rups = new ArrayList<>(totalNumRuptures);
				for (int r=0; r<totalNumRuptures; r++)
					rups.add(null);
				
				double[] mags = new double[totalNumRuptures];
				double[] rakes = new double[totalNumRuptures];
				double[] rupAreas = new double[totalNumRuptures];
				double[] rupLengths = new double[totalNumRuptures];
				
				boolean allSingleStranded = true;
				
				int runningSectIndex = allHaveMappings ? -1 : 0;
				int runningRupIndex = allHaveMappings ? -1 : 0;
				for (int i=0; i<clusterRupSets.size(); i++) {
					FaultSystemRupSet rupSet = clusterRupSets.get(i);
					System.out.println("Processing rupture set "+i+" with "+rupSet.getNumSections()
							+" sections and "+rupSet.getNumRuptures()+" ruptures");
					
					allSingleStranded &= rupSet.hasModule(ClusterRuptures.class)
							&& ((rupSet.getModule(ClusterRuptures.class)) instanceof ClusterRuptures.SingleStranded);
					
					RuptureSubSetMappings mappings;
					if (allHaveMappings) {
						mappings = rupSet.requireModule(RuptureSubSetMappings.class);
					} else {
						// build mappings
						BiMap<Integer, Integer> sectIDs_newToOld = HashBiMap.create(rupSet.getNumSections());
						BiMap<Integer, Integer> rupIDs_newToOld = HashBiMap.create(rupSet.getNumRuptures());
						for (int s=0; s<rupSet.getNumSections(); s++)
							sectIDs_newToOld.put(s, runningSectIndex++);
						for (int r=0; r<rupSet.getNumRuptures(); r++)
							rupIDs_newToOld.put(r, runningRupIndex++);
						Preconditions.checkState(runningSectIndex <= totalNumSects);
						Preconditions.checkState(runningRupIndex <= totalNumRuptures);
						mappings = new RuptureSubSetMappings(sectIDs_newToOld, rupIDs_newToOld, null);
						rupSet.addModule(mappings);
					}
					
					for (int s=0; s<rupSet.getNumSections(); s++) {
						int origIndex = mappings.getOrigSectID(s);
						Preconditions.checkState(sects.get(origIndex) == null,
								"Multiple rupture sets have mappings to section %s", origIndex);
						FaultSection sect = rupSet.getFaultSectionData(s);
						sect = sect.clone();
						sect.setSectionId(origIndex);
						sects.set(origIndex, sect);
					}
					
					for (int r=0; r<rupSet.getNumRuptures(); r++) {
						int origIndex = mappings.getOrigRupID(r);
						Preconditions.checkState(rups.get(origIndex) == null,
								"Multiple rupture sets have mappings to rupture %s", origIndex);
						List<Integer> rupSects = rupSet.getSectionsIndicesForRup(r);
						List<Integer> remapped = new ArrayList<>(rupSects.size());
						for (int s : rupSects)
							remapped.add(mappings.getOrigSectID(s));
						rups.set(origIndex, remapped);
						mags[origIndex] = rupSet.getMagForRup(r);
						rakes[origIndex] = rupSet.getAveRakeForRup(r);
						rupAreas[origIndex] = rupSet.getAreaForRup(r);
						rupLengths[origIndex] = rupSet.getLengthForRup(r);
					}
				}
				
				// make sure everything was set
				for (int s=0; s<sects.size(); s++)
					Preconditions.checkState(sects.get(s) != null,
								"Combined section %s not found, were clusters removed? Try with --remap enabled.");
				for (int r=0; r<rups.size(); r++)
					Preconditions.checkState(rups.get(r) != null,
								"Combined rupture %s not found, were clusters removed? Try with --remap enabled.");
				
				fullRupSet = FaultSystemRupSet.builder(sects, rups)
						.rupMags(mags)
						.rupRakes(rakes)
						.rupAreas(rupAreas)
						.rupLengths(rupLengths)
						.build();
				
				if (allSingleStranded)
					fullRupSet.addModule(ClusterRuptures.singleStranged(fullRupSet));
			} else {
				Preconditions.checkState(allHaveMappings, "A full rupture set was passed in, but the input cluster-specific "
						+ "rupture sets don't contain the RuptureSubSetMappings needed to map them back to the original "
						+ "rupture set.");
			}
			
			if (clusterSolutions != null) {
				System.out.println("Stitching together solution rates");
				
				// TODO support gridded seismicity?
				
				double[] rates = new double[totalNumRuptures];
				boolean anyMFDs = false;
				DiscretizedFunc[] mfds = new DiscretizedFunc[totalNumRuptures];
				
				for (int i=0; i<clusterSolutions.size(); i++) {
					// will always have them attached at this stage (either originally, or added when combining above)
					RuptureSubSetMappings mappings = clusterRupSets.get(i).requireModule(RuptureSubSetMappings.class);
					FaultSystemSolution sol = clusterSolutions.get(i);
					double[] subRates = sol.getRateForAllRups();
					RupMFDsModule subMFDs = sol.getModule(RupMFDsModule.class);
					
					for (int r=0; r<subRates.length; r++) {
						int origIndex = mappings.getOrigRupID(r);
						Preconditions.checkState(rates[origIndex] == 0d);
						rates[origIndex] = subRates[r];
						if (subMFDs != null) {
							DiscretizedFunc mfd = subMFDs.getRuptureMFD(r);
							if (mfd != null) {
								anyMFDs = true;
								mfds[origIndex] = mfd;
							}
						}
					}
				}
				
				System.out.println("Writing combined solution to "+output.getAbsolutePath());
				FaultSystemSolution sol = new FaultSystemSolution(fullRupSet, rates);
				
				if (anyMFDs)
					sol.addModule(new RupMFDsModule(sol, mfds));
				
				sol.write(output);
			} else {
				System.out.println("Writing combined rupture set to "+output.getAbsolutePath());
				fullRupSet.write(output);
			}
		}
	}

}
