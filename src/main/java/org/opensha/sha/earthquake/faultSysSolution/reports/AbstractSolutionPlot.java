package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public abstract class AbstractSolutionPlot {
	
	/**
	 * Called to generate plots for the given solution in the given output directory. Returns Markdown that will be
	 * included in the report.
	 * 
	 * @param sol fault system solution to plot
	 * @param sol another fault system solution to compare against, or null if no comparison
	 * @param outputDir output directory where plots should be stored
	 * @param relPathToOutput relative path to that output directory from the Markdown page,
	 * to to embed images/link to files
	 * @param topLink add this anywhere in the Markdown where you want a link back to the top table of contents
	 * @return markdown lines
	 * @throws IOException
	 */
	public abstract List<String> plot(FaultSystemSolution sol, FaultSystemSolution compSol, File outputDir,
			String relPathToOutput, String topLink) throws IOException;
	
	/**
	 * This is used to specify required modules for this plot (either rupture set or solution modules). If any of these
	 * modules are missing from the given rupture set/solution, then this plot will be skipped.
	 * 
	 * @return modules that are required for this plot, or null for no required modules
	 */
	public abstract Collection<Class<? extends OpenSHA_Module>> getRequiredModules();
	
	protected static DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	protected static DecimalFormat normProbDF = new DecimalFormat("0.000");
	protected static DecimalFormat expProbDF = new DecimalFormat("0.00E0");
	protected static DecimalFormat percentProbDF = new DecimalFormat("0.00%");
	
	protected static String getProbStr(double prob) {
		return getProbStr(prob, false);
	}
	
	protected static String getProbStr(double prob, boolean includePercent) {
		String ret;
		if (prob < 0.01 && prob > 0)
			ret = expProbDF.format(prob);
		else
			ret = normProbDF.format(prob);
		if (includePercent)
			ret += " ("+percentProbDF.format(prob)+")";
		return ret;
	}

}
