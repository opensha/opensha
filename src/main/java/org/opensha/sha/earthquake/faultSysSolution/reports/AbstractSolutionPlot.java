package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public abstract class AbstractSolutionPlot extends AbstractRupSetPlot {
	
	public void writePlot(FaultSystemSolution sol, String name, File outputDir) throws IOException {
		super.writePlot(sol.getRupSet(), sol, name, outputDir);
	}
	
	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		Preconditions.checkNotNull(sol, "Solution must be supplied for a solution plot");
		return plot(sol, meta, resourcesDir, relPathToResources, topLink);
	}
	
	/**
	 * Called to generate plots for the given solution in the given output directory. Returns Markdown that will be
	 * included in the report, not including the plot title (that will be added externally).
	 * 
	 * @param sol solution to plot
	 * @param name name of this solution
	 * @param resourcesDir output directory where plots should be stored
	 * @param relPathToResources relative path to that output directory from the Markdown page,
	 * to to embed images/link to files
	 * @param topLink add this anywhere in the Markdown where you want a link back to the top table of contents
	 * @return markdown lines, not including the plot title
	 * @throws IOException
	 */
	public List<String> plot(FaultSystemSolution sol, String name,
			File resourcesDir, String relPathToResources, String topLink) throws IOException {
		return plot(sol, new ReportMetadata(new RupSetMetadata(name, sol)), resourcesDir, relPathToResources, topLink);
	}
	
	/**
	 * Called to generate plots for the given solution in the given output directory. Returns Markdown that will be
	 * included in the report, not including the plot title (that will be added externally).
	 * 
	 * @param rupSet rupture set to plot
	 * @param sol solution that goes with this rupture set, if available
	 * @param meta metadata for this report, possibly including an availabe comparison solution
	 * @param resourcesDir output directory where plots should be stored
	 * @param relPathToResources relative path to that output directory from the Markdown page,
	 * to to embed images/link to files
	 * @param topLink add this anywhere in the Markdown where you want a link back to the top table of contents
	 * @return markdown lines, not including the plot title
	 * @throws IOException
	 */
	public abstract List<String> plot(FaultSystemSolution sol, ReportMetadata meta,
			File resourcesDir, String relPathToResources, String topLink) throws IOException;

}
