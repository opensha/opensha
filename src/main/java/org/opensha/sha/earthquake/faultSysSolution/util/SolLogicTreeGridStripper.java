package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

public class SolLogicTreeGridStripper {
	
	public static Options createOptions() {
		Options ops = new Options();

		ops.addRequiredOption(null, "input-file", true, "Path to input SolutionLogicTree");
		ops.addRequiredOption(null, "output-file", true, "Path to output SolutionLogicTree");
		ops.addOption(null, "min-grid-mag", true, "If supplied, gridded seismicity will be kept above this magnitude "
				+ "rather than removed completely");
		ops.addOption(null, "process-modules", false, "If supplied, gridded seismicity will be kept above this magnitude "
				+ "rather than removed completely");
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SolModuleStripper.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		Double minMag = null;
		if (cmd.hasOption("min-grid-mag"))
			minMag = Double.parseDouble(cmd.getOptionValue("min-grid-mag"));
		
		ArchiveInput input = new ArchiveInput.ApacheZipFileInput(inputFile);
		SolutionLogicTree slt = SolutionLogicTree.load(input);
		
		LogicTree<?> tree = slt.getLogicTree();
		
		SolutionLogicTree outputSLT = new SolutionLogicTree.GridRemovalSolutionLogicTree(slt, minMag);
		
		boolean process = cmd.hasOption("process-modules");
		SolutionLogicTree.reprocess(outputSLT, outputFile, null, false, process, false);
		input.close();
		System.exit(0);
	}

}
