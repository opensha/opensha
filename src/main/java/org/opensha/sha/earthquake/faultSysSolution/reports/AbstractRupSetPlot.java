package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;

public abstract class AbstractRupSetPlot implements Named {
	
	// default is level 3: top level for report name, 2nd level for plot name
	private String subHeading = "###";
	
	public void writePlot(FaultSystemRupSet rupSet, FaultSystemSolution sol, String name, File outputDir) throws IOException {
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		lines.add("# "+getName());
		String topLink = MarkdownUtils.getAnchorName(getName());
		lines.add("");
		
		lines.addAll(plot(rupSet, sol, name, resourcesDir, resourcesDir.getName(), topLink));
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	/**
	 * Called to generate plots for the given rupture set in the given output directory. Returns Markdown that will be
	 * included in the report, not including the plot title (that will be added externally).
	 * 
	 * @param rupSet rupture set to plot
	 * @param sol solution that goes with this rupture set, if available
	 * @param name name of this rupture set
	 * @param resourcesDir output directory where plots should be stored
	 * @param relPathToResources relative path to that output directory from the Markdown page,
	 * to to embed images/link to files
	 * @param topLink add this anywhere in the Markdown where you want a link back to the top table of contents
	 * @return markdown lines, not including the plot title
	 * @throws IOException
	 */
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, String name,
			File resourcesDir, String relPathToResources, String topLink) throws IOException {
		RupSetMetadata meta = new RupSetMetadata(name, rupSet, sol);
		ReportPageGen.attachDefaultModules(meta, FaultSysTools.getCacheDir(), ReportPageGen.DEFAULT_MAX_DIST);
		return plot(rupSet, sol, new ReportMetadata(meta), resourcesDir, relPathToResources, topLink);
	}
	
	/**
	 * @return the heading (e.g., '###') to use for markdown headings within a plot
	 */
	protected String getSubHeading() {
		return subHeading;
	}
	
	protected void setSubHeading(String subHeading) {
		this.subHeading = subHeading;
	}
	
	/**
	 * Called to generate plots for the given rupture set in the given output directory. Returns Markdown that will be
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
	public abstract List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta,
			File resourcesDir, String relPathToResources, String topLink) throws IOException;
	
	/**
	 * Can be overridden to provide summary markdown, not including the plot title (that will be added externally).
	 * This will be used when generating an index of many reports, such as for many different comparisons, and should be brief.
	 * 
	 * @param meta
	 * @param resourcesDir
	 * @param relPathToResources
	 * @param topLink
	 * @return summary markdown, or null
	 */
	public List<String> getSummary(ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) {
		return null;
	}
	
	/**
	 * This is used to specify required modules for this plot (either rupture set or solution modules). If any of these
	 * modules are missing from the given rupture set/solution, then this plot will be skipped.
	 * 
	 * @return modules that are required for this plot, or null for no required modules
	 */
	public abstract Collection<Class<? extends OpenSHA_Module>> getRequiredModules();
	
	protected static DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	protected static DecimalFormat twoDigits = new DecimalFormat("0.00");
	protected static DecimalFormat normProbDF = new DecimalFormat("0.000");
	protected static DecimalFormat expProbDF = new DecimalFormat("0.00E0");
	protected static DecimalFormat percentDF = new DecimalFormat("0.00%");
	protected static DecimalFormat countDF = new DecimalFormat("#");
	static {
		countDF.setGroupingUsed(true);
		countDF.setGroupingSize(3);
	}
	
	protected static final Color MAIN_COLOR = Color.RED;
	protected static final Color COMP_COLOR = Color.BLUE;
	protected static final Color COMMON_COLOR = Color.GREEN;
	
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
			ret += " ("+percentDF.format(prob)+")";
		return ret;
	}
	
	protected static String getTruncatedTitle(String title) {
		if (title != null && title.length() > 30)
			return title.substring(0, 29).trim()+"…";
		return title;
	}
	
	protected static String getFileSafe(String name) {
		String safe = name.replaceAll("\\W+", "_");
		while (safe.contains("__"))
			safe.replaceAll("__", "_");
		if (safe.startsWith("_"))
			safe = safe.substring(1);
		if (safe.endsWith("_"))
			safe = safe.substring(0, safe.length()-1);
		return safe;
	}

}
