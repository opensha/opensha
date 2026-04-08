package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

public class SolLogicTreeSampler {
	
	public static Options createOptions() {
		Options ops = new Options();

		ops.addRequiredOption(null, "input-file", true, "Path to input SolutionLogicTree");
		ops.addRequiredOption(null, "output-file", true, "Path to output (sampled) SolutionLogicTree");
		ops.addOption(null, "samples", true, "Number of random samples. Must supply this or --logic-tree");
		ops.addOption(null, "rand-seed", true, "Random seed to use when downsampling; default is repeatable and based "
				+ "on the number of samples and the number of original branches");
		ops.addOption(null, "logic-tree", true, "Already sampled logic tree. Must supply this or --samples");
		ops.addOption(null, "simplify", false, "Flag to simplify the written SolutionLogicTree");
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SolModuleStripper.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		ArchiveInput input = new ArchiveInput.ApacheZipFileInput(inputFile);
		SolutionLogicTree slt = SolutionLogicTree.load(input);
		
		LogicTree<?> origTree = slt.getLogicTree();
		
		LogicTree<?> outputTree;
		if (cmd.hasOption("samples")) {
			int numSamples = Integer.parseInt(cmd.getOptionValue("samples"));
			long randSeed;
			if (cmd.hasOption("rand-seed"))
				randSeed = Long.parseLong(cmd.getOptionValue("rand-seed"));
			else
				randSeed = (long)origTree.size() * (long)numSamples;
			outputTree = origTree.sample(numSamples, true, new Random(randSeed), true);
		} else if (cmd.hasOption("logic-tree")) {
			outputTree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		} else {
			throw new IllegalArgumentException("Must supply --logic-tree or --samples");
		}
		
		SolutionLogicTree sampledSLT = new SolutionLogicTree.SubsetSolutionLogicTree(slt, outputTree);
		
		boolean simplify = cmd.hasOption("simplify");
		if (simplify) {
			SolutionLogicTree.simplify(sampledSLT, outputFile);
		} else {
			SolutionLogicTree.reprocess(sampledSLT, outputFile, null, true, true);
		}
		input.close();
		System.exit(0);
	}

}
