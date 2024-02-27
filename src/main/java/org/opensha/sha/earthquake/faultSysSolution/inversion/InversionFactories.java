package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SingleStates;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.U3InversionConfigFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class InversionFactories {
	
	private enum Factory implements Supplier<InversionConfigurationFactory> {
		UCERF3("ucerf3", "Selects the UCERF3 inversion configuration factory.") {
			@Override
			public InversionConfigurationFactory get() {
				return new U3InversionConfigFactory();
			}

			@Override
			public LogicTreeBranch<?> getDefaultBranch() {
				return U3LogicTreeBranch.getMEAN_UCERF3(FaultModels.FM3_1);
			}

			@Override
			public LogicTreeBranch<?> getSolutionOnlyDefaultBranch() {
				return branchCopyWithoutTypes(getDefaultBranch(),
						List.of(FaultModels.class, DeformationModels.class, ScalingRelationships.class,
								SlipAlongRuptureModels.class));
			}
		},
		NSHM23("nshm23", "Selects the NSHM23 inversion configuration factory.") {
			
			private NSHM23_InvConfigFactory factory;
			private boolean noGridded;
			private boolean applyStdDevDefaults;
			private NSHM23_SingleStates singleState;
			
			@Override
			public NSHM23_InvConfigFactory get() {
				if (factory == null)
					factory = new NSHM23_InvConfigFactory();
				return factory;
			}

			@Override
			public LogicTreeBranch<?> getDefaultBranch() {
				LogicTreeBranch<?> branch = noGridded ?
						NSHM23_LogicTreeBranch.AVERAGE_ON_FAULT : NSHM23_LogicTreeBranch.AVERAGE_COMBINED;
				
				if (singleState != null) {
					List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
					List<LogicTreeNode> nodes = new ArrayList<>();
					
					levels.add(NSHM23_LogicTreeBranch.SINGLE_STATES);
					nodes.add(singleState);
					
					levels.addAll(branch.getLevels());
					for (LogicTreeNode node : branch)
						nodes.add(node);
					
					branch = new LogicTreeBranch<>(levels, nodes);
				}
				
				return branch;
			}

			@Override
			public LogicTreeBranch<?> getSolutionOnlyDefaultBranch() {
				return branchCopyWithoutLevels(getDefaultBranch(),
						List.of(NSHM23_LogicTreeBranch.FM, NSHM23_LogicTreeBranch.DM,
								NSHM23_LogicTreeBranch.PLAUSIBILITY, NSHM23_LogicTreeBranch.SCALE,
								NSHM23_LogicTreeBranch.SINGLE_STATES));
			}

			@Override
			public void addExtraOptions(Options ops) {
				ops.addOption(null, "iters-per-rup", true, "Specify the number of iterations per rupture in order to "
						+ "speed up the inversion or change convergence. Default: "+NSHM23_InvConfigFactory.NUM_ITERS_PER_RUP_DEFAULT);
				ops.addOption(null, "full-sys-inv", false, "Flag to force a single full-system inversion rather than "
						+ "splitting the problem into smaller inversions for each isolated fault system.");;
				ops.addOption(null, "no-gridded", false, "Flag to disable the gridded seismicity model; useful if you "
						+ "are applying NSHM23 methodology to a new region (or just don't need gridded seismicity).");
				ops.addOption(null, "single-state", true, "Limit the model to a single state (specified by two letter "
						+ "abbreviation). This does not apply when an external rupture set it supplied.");
				ops.addOption(null, "apply-std-dev-defaults", false, "Flag to apply NSHM23 slip rate standard deviation "
						+ "defaults to a passed in rupture set.");
			}

			@Override
			public void processExtraOptions(CommandLine cmd) {
				if (cmd.hasOption("iters-per-rup"))
					get().setNumItersPerRup(Long.parseLong(cmd.getOptionValue("iters-per-rup")));
				if (cmd.hasOption("full-sys-inv"))
					get().setSolveClustersIndividually(false);
				noGridded = cmd.hasOption("no-gridded");
				if (cmd.hasOption("single-state"))
					singleState = NSHM23_SingleStates.valueOf(cmd.getOptionValue("single-state"));
				applyStdDevDefaults = cmd.hasOption("apply-std-dev-defaults");
			}

			@Override
			public FaultSystemRupSet processExternalRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
				if (applyStdDevDefaults) {
					System.out.println("Applying NSHM23 standard deviation defaults to fault sections in passed in "
							+ "rupture set.");
					NSHM23_DeformationModels.applyStdDevDefaults(rupSet.getFaultSectionDataList());
					// replace any previously created SectSlipRates instance
					rupSet.addModule(SectSlipRates.fromFaultSectData(rupSet));
				}
				return super.processExternalRupSet(rupSet, branch);
			}
		};
		
		private String argName;
		private String argDescription;
		private Factory(String argName, String argDescription) {
			this.argName = argName;
			this.argDescription = argDescription;
		}
		
		public void addExtraOptions(Options ops) {
			// do nothing (can be overridden)
		}
		
		public void processExtraOptions(CommandLine cmd) {
			// do nothing (can be overridden)
		}
		
		public abstract InversionConfigurationFactory get();
		
		/**
		 * @return a full {@link LogicTreeBranch} that can be used to build a rupture set and inversion configuration
		 */
		public abstract LogicTreeBranch<?> getDefaultBranch();
		
		/**
		 * @return a {@link LogicTreeBranch} that can be used to build a an inversion configuration for an existing
		 * rupture set
		 */
		public abstract LogicTreeBranch<?> getSolutionOnlyDefaultBranch();
		
		public FaultSystemRupSet processExternalRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			SolutionProcessor processor = get().getSolutionLogicTreeProcessor();
			if (processor != null)
				rupSet = processor.processRupSet(rupSet, branch);
			return rupSet;
		}
		
		private static LogicTreeBranch<LogicTreeNode> branchCopyWithoutLevels(LogicTreeBranch<?> branch,
				List<LogicTreeLevel<?>> excludes) {
			 List<LogicTreeLevel<? extends LogicTreeNode>> newLevels = new ArrayList<>();
			 List<LogicTreeNode> newNodes = new ArrayList<>();
			 
			 for (int l=0; l<branch.size(); l++) {
				 LogicTreeLevel<?> level = branch.getLevel(l);
				 boolean keep = true;
				 for (LogicTreeLevel<?> exclude : excludes) {
					 if (exclude.equals(level) || exclude.getName().equals(level.getName())) {
						 keep = false;
						 break;
					 }
				 }
				 if (keep) {
					 newLevels.add(level);
					 newNodes.add(branch.getValue(l));
				 }
			 }
			 
			 return new LogicTreeBranch<>(newLevels, newNodes);
		}
		
		private static LogicTreeBranch<LogicTreeNode> branchCopyWithoutTypes(LogicTreeBranch<?> branch,
				List<Class<? extends LogicTreeNode>> excludes) {
			 List<LogicTreeLevel<? extends LogicTreeNode>> newLevels = new ArrayList<>();
			 List<LogicTreeNode> newNodes = new ArrayList<>();
			 
			 for (int l=0; l<branch.size(); l++) {
				 LogicTreeLevel<?> level = branch.getLevel(l);
				 boolean keep = true;
				 for (Class<? extends LogicTreeNode> exclude : excludes) {
					 if (level.getType().equals(exclude) || level.getType().isAssignableFrom(exclude)) {
						 keep = false;
						 break;
					 }
				 }
				 if (keep) {
					 newLevels.add(level);
					 newNodes.add(branch.getValue(l));
				 }
			 }
			 
			 return new LogicTreeBranch<>(newLevels, newNodes);
		}
	}
	
	private static Options createOptions(Factory factory) {
		Options ops = Inversions.createOptionsNoConstraints(false, false);
		
		ops.addOption(FaultSysTools.cacheDirOption());
		
		for (Factory f : Factory.values())
			ops.addOption(null, f.argName, false, f.argDescription);
		
		if (factory != null)
			factory.addExtraOptions(ops);

		ops.addOption(null, "list-branch-choices", false, "Flag to list all logic tree branch choices and exit. Make "
				+ "sure to also supply a model flag.");
		ops.addOption(null, "branch-choice", true, "Set a logic tree branch choice to a non-default value. See "
				+ "--list-branch-choices to see available branching levels and choices along with example syntax. "
				+ "Supply this argument multiple times to set multiple branch choices. Usage: "
				+ "--branch-choice name:value");
		
		return ops;
	}
	
	private static boolean LIST_BRANCH_MARKDOWN = false;
	
	public static void main(String[] args) {
//		args = new String[0]; // to force it to print help
		try {
			run(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static FaultSystemSolution run(String[] args) throws IOException {
		Factory factoryChoice = null;
		// see if a factory is supplied, and if so create factory-specific options
		for (String arg : args) {
			arg = arg.trim();
			while (arg.startsWith("-"))
				arg = arg.substring(1);
			for (Factory f : Factory.values()) {
				if (arg.equals(f.argName)) {
					Preconditions.checkArgument(factoryChoice == null,
							"Can't specify multiple factories (both %s and %s specified)", factoryChoice, f);
					factoryChoice = f;
					break;
				}
			}
		}
		Options options = createOptions(factoryChoice);
		
		CommandLine cmd = FaultSysTools.parseOptions(options, args, Inversions.class);
		FaultSysTools.checkPrintHelp(options, cmd, Inversions.class);
		
		if (factoryChoice == null) {
			System.err.println("Must supply a factory flag, e.g. --ucerf3 or --nshm23");
			FaultSysTools.printHelpAndExit(options, Inversions.class);
		}
		
		factoryChoice.processExtraOptions(cmd);

		LogicTreeBranch<?> rawBranch = cmd.hasOption("rupture-set") ?
				factoryChoice.getSolutionOnlyDefaultBranch() : factoryChoice.getDefaultBranch();
		
		if (cmd.hasOption("list-branch-choices")) {
			if (LIST_BRANCH_MARKDOWN) {
				// generate markdown table for generating documentation
				TableBuilder table = MarkdownUtils.tableBuilder();
				
				table.addLine("Level", "Type", "Default Choice", "Options");
				
				LogicTreeBranch<?> branch = factoryChoice.getDefaultBranch();
				LogicTreeBranch<?> solBranch = factoryChoice.getSolutionOnlyDefaultBranch();
				
				for (int l=0; l<branch.size(); l++) {
					LogicTreeLevel<?> level = branch.getLevel(l);
					LogicTreeNode value = branch.getValue(l);
					
					table.initNewLine();
					table.addColumn(level.getName());
					boolean isRupSetOnly = true;
					for (int i=0; i<solBranch.size(); i++) {
						LogicTreeLevel<?> solLevel = solBranch.getLevel(i);
						if (solLevel.equals(level)) {
							isRupSetOnly = false;
							break;
						}
					}
					if (isRupSetOnly)
						table.addColumn("Rupture Set");
					else
						table.addColumn("Solution");
					
					if (value == null)
						table.addColumn("_(null)_");
					else
						table.addColumn("`"+value.getFilePrefix()+"`");
					
					String opsStr = null;
					for (LogicTreeNode option : level.getNodes()) {
						if (opsStr == null)
							opsStr = "";
						else
							opsStr += ", ";
						opsStr += option.getFilePrefix();
					}
					table.addColumn("`"+opsStr+"`");
					table.finalizeLine();
				}
				
				List<String> lines = table.build();
				for (String line : lines)
					System.out.println(line);
			} else {
				System.out.println("Logic Tree Branch options. Each branching level will be listed separately with the "
						+ "level name, and then each choice following (indented). Each choice lists its full descriptive "
						+ "name and then an example showing how to set that branch choice from the command line. "
						+ "Options selected by default will be annotated with '(DEFAULT)'");
				for (int l=0; l<rawBranch.size(); l++) {
					LogicTreeLevel<?> level = rawBranch.getLevel(l);
					System.out.println(level.getName());
					for (LogicTreeNode choice : level.getNodes()) {
						String arg = level.getShortName()+":"+choice.getFilePrefix();
						if (arg.contains(" "))
							arg = "\""+arg+"\"";
						arg = "--branch-choice "+arg;
						System.out.println("\t"+choice.getName()+(rawBranch.hasValue(choice) ? " (DEFAULT)" : "")+";\t"+arg);
					}
				}
			}
			System.out.flush();
			System.exit(0);
		}
		
		List<LogicTreeLevel<?>> levels = new ArrayList<>();
		for (LogicTreeLevel<?> level : rawBranch.getLevels())
			levels.add(level);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<LogicTreeNode>(levels);
		for (int l=0; l<branch.size(); l++)
			if (rawBranch.getValue(l) != null)
				branch.setValue(l, rawBranch.getValue(l));
		
		Preconditions.checkArgument(cmd.hasOption("output-file"), "Must supply --output-file option");
		
		if (cmd.hasOption("branch-choice")) {
			for (String op : cmd.getOptionValues("branch-choice")) {
				Preconditions.checkState(op.contains(":"),
						"Malform branch choice option. Expected <level>:<choice>, got: %s", op);
				int index = op.indexOf(":");
				String levelStr = op.substring(0, index);
				String nodeStr = op.substring(index+1);
				System.out.println("Setting custom logic tree value: level='"+levelStr+"', choice='"+nodeStr+"'");
				boolean found = false;
				for (LogicTreeLevel<?> level : levels) {
					if (level.getShortName().trim().equals(levelStr)) {
						for (LogicTreeNode node : level.getNodes()) {
							if (node.getFilePrefix().equals(nodeStr)) {
								// match
								System.out.println("\tMatched with value: "+node.getName());
								branch.setValue(node);
								found = true;
								break;
							}
						}
						Preconditions.checkState(found,
								"Identified the specified level ('%s') but not the choice ('%s')", levelStr, nodeStr);
					}
				}
				Preconditions.checkState(found, "Did not find the specified level ('%s')", levelStr);
			}
		}
		
		InversionConfigurationFactory factory = factoryChoice.get();
		
		File cacheDir = FaultSysTools.getCacheDir(cmd);
		factory.setCacheDir(cacheDir);
		factory.setAutoCache(cacheDir != null && cacheDir.exists());
		int threads = FaultSysTools.getNumThreads(cmd);
		
		FaultSystemRupSet rupSet;
		
		if (cmd.hasOption("rupture-set")) {
			// use that passed in
			File rupSetFile = new File(cmd.getOptionValue("rupture-set"));
			rupSet = FaultSystemRupSet.load(rupSetFile);
			
			LogicTreeBranch<?> rsBranch = rupSet.getModule(LogicTreeBranch.class);
			if (rsBranch != null) {
				// set values from the branch
				for (int l=0; l<rsBranch.size(); l++) {
					LogicTreeNode choice = rsBranch.getValue(l);
					if (choice != null && !branch.hasValue(choice)) {
						// need to set it
						try {
							branch.setValue(choice);
						} catch (Exception e) {
							System.err.println("WARNING: Rupture set has a logic tree branch already, but couldn't "
									+ "copy over value to new branch: "+e.getMessage());
						}
					}
				}
			}
			
			rupSet = factoryChoice.processExternalRupSet(rupSet, branch);
		} else {
			// build it with the factory
			System.out.println("Building rupture set for logic tree branch: "+branch);
			rupSet = factory.buildRuptureSet(branch, threads);
		}
		
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		System.out.println("Running inversion for branch: "+branch);
		
		FaultSystemSolution sol = Inversions.run(rupSet, factory, branch, threads, cmd);
		
		if (cmd.hasOption("write-config-json")) {
			File configFile = new File(cmd.getOptionValue("write-config-json"));
			
			InversionConfiguration config = sol.getModule(InversionConfiguration.class);
			if (config == null) {
				// might have been cluster-specific, or some other non-default solver
				config = factory.buildInversionConfig(rupSet, branch, threads);
				System.err.println("WARNING: writing configuration JSON file, but configuration not attached to solution. "
						+ "This usually means that the factory uses a non-default solver, so we will rebuild the "
						+ "configuration. It may not exactly match the configuration that was used to build the solution.");
			}
			
			BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(config, InversionConfiguration.class, writer);
			writer.flush();
			writer.close();
		}
		
		String info = "Fault System Solution generated with OpenSHA Fault System Tools ("
				+ "https://github.com/opensha/opensha-fault-sys-tools), using the following command:"
				+ "\n\nfst_inversion_factory_runner.sh "+Joiner.on(" ").join(args);
		info += "\n\nThe selected inversion factory is: "+factoryChoice+" ("+factory.getClass().getName()+")";
		
		sol.addModule(new InfoModule(info));
		
		// write solution
		sol.write(outputFile);
		
		return sol;
	}

}
