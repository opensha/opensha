package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NamedFaultPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Named Fault Detail Pages";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		File faultsDir = new File(resourcesDir.getParentFile(), "named_fault_pages");
		Preconditions.checkState(faultsDir.exists() || faultsDir.mkdir());
		
		NamedFaults faults = sol.getRupSet().requireModule(NamedFaults.class);
		
		Map<String, String> linksMap = new HashMap<>();
		for (String faultName : faults.getFaultNames()) {
			String subDirName = writeFaultPage(meta, faults, faultName, faultsDir);
			
			linksMap.put(faultName, relPathToResources+"/../"+faultsDir.getName()+"/"+subDirName);
		}
		
		List<String> sortedNames = new ArrayList<>(linksMap.keySet());
		Collections.sort(sortedNames);
		
		TableBuilder table = SectBySectDetailPlots.buildSectLinksTable(linksMap, sortedNames, "Fault Name");

		return table.build();
	}
	
	private static String writeFaultPage(ReportMetadata meta, NamedFaults faults, String faultName, File faultsDir) throws IOException {
		System.out.println("Building page for: "+faultName);
		String dirName = getFileSafe(faultName);
		
		File faultDir = new File(faultsDir, dirName);
		Preconditions.checkState(faultDir.exists() || faultDir.mkdir());
		
		File resourcesDir = new File(faultDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		lines.add("# "+faultName+" Details");
		
		lines.add("");
		
		List<FaultSection> faultSects = faults.getSectsForFault(faultName);
		
		HashSet<Integer> allRups = new HashSet<>();
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		double totRate = 0d;
		for (FaultSection sect : faultSects)
			allRups.addAll(meta.primary.rupSet.getRupturesForSection(sect.getSectionId()));
		for (int rupIndex : allRups) {
			double mag = meta.primary.rupSet.getMagForRup(rupIndex);
			minMag = Math.min(minMag, mag);
			maxMag = Math.max(maxMag, mag);
			totRate += meta.primary.sol.getRateForRup(rupIndex);
		}
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("_Property_", "_Value_");
		table.addLine("**Rupture Count**", countDF.format(allRups.size()));
		table.addLine("**Magnitude Range**", "["+twoDigits.format(minMag)+", "+twoDigits.format(maxMag)+"]");
		table.addLine("**Total Rate**", (float)totRate+" /yr");
		lines.addAll(table.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		lines.addAll(SectBySectDetailPlots.getMFDLines(meta, faultName, faultSects, resourcesDir, topLink));
		lines.add("");
		
		lines.addAll(SectBySectDetailPlots.getAlongStrikeLines(meta, faultName, faultSects, resourcesDir, topLink));
		lines.add("");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
		lines.add(tocIndex, "## Table Of Contents");

		MarkdownUtils.writeReadmeAndHTML(lines, faultDir);

		return dirName;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(NamedFaults.class);
	}

}
