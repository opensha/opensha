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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.enumTreeBranches.FaultModels;
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
		},
		NSHM23("nshm23", "Selects the NSHM23 inversion configuration factory.") {
			
			private NSHM23_InvConfigFactory factory;
			@Override
			public NSHM23_InvConfigFactory get() {
				if (factory == null)
					factory = new NSHM23_InvConfigFactory();
				return factory;
			}

			@Override
			public LogicTreeBranch<?> getDefaultBranch() {
				return NSHM23_LogicTreeBranch.AVERAGE_COMBINED;
			}

			@Override
			public void addExtraOptions(Options ops) {
				ops.addOption(null, "iters-per-rup", true, "Specify the number of iterations per rupture in order to "
						+ "speed up the inversion or change convergende. Default: "+NSHM23_InvConfigFactory.NUM_ITERS_PER_RUP_DEFAULT);
				ops.addOption(null, "full-sys-inv", false, "Flag to force a single full-system inversion rather than "
						+ "splitting the problem into smaller inversions for each isolated fault system.");
			}

			@Override
			public void processExtraOptions(CommandLine cmd) {
				if (cmd.hasOption("iters-per-rup"))
					get().setNumItersPerRup(Long.parseLong(cmd.getOptionValue("iters-per-rup")));
				if (cmd.hasOption("full-sys-inv"))
					get().setSolveClustersIndividually(false);
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
	}
	
	private static Options createOptions(Factory factory) {
		Options ops = Inversions.createOptionsNoConstraints(false, false);
		
		ops.addOption(FaultSysTools.cacheDirOption());
		
		for (Factory f : Factory.values())
			ops.addOption(null, f.argName, false, f.argDescription);
		
		if (factory != null)
			factory.addExtraOptions(ops);

		ops.addOption(null, "list-branch-choices", false, "Flag to list all logic tree branch choices and exit.");
		ops.addOption(null, "branch-choice", true, "Set a logic tree branch choice to a non-default value. See "
				+ "--list-branch-choices to see available branching levels and choices along with example syntax. "
				+ "Supply this argument multiple times to set multiple branch choices. Usage: "
				+ "--branch-choice name:value");
		
		return ops;
	}
	
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

		LogicTreeBranch<?> rawBranch = factoryChoice.getDefaultBranch();
		
		if (cmd.hasOption("list-branch-choices")) {
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
		
		// write solution
		sol.write(outputFile);
		
		return sol;
	}

}
