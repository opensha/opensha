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

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;

public class PlausibilityConfigurationReport extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Plausibility Configuration";
	}
	

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		List<String> header = new ArrayList<>();
		header.add(" ");
		header.add(meta.primary.name);
		if (meta.comparison != null)
			header.add(meta.comparison.name);
		
		FaultSystemRupSet compRupSet = meta.comparison == null ? null : meta.comparison.rupSet;

		PlausibilityConfiguration config = rupSet.requireModule(PlausibilityConfiguration.class);
		PlausibilityConfiguration compConfig = meta.comparison == null ?
				null : compRupSet.getModule(PlausibilityConfiguration.class);
		
		lines.add(getSubHeading()+" Connection Strategy");
		lines.add(topLink); lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.addLine(header);
		table.initNewLine();
		table.addColumn("**Name**");
		table.addColumn(config.getConnectionStrategy().getName());
		if (meta.comparison != null) {
			if (compConfig == null)
				table.addColumn("*(N/A)*");
			else
				table.addColumn(compConfig.getConnectionStrategy().getName());
		}
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Max Jump Dist**");
		table.addColumn(optionalDigitDF.format(config.getConnectionStrategy().getMaxJumpDist())+" km");
		if (meta.comparison != null) {
			if (compConfig == null)
				table.addColumn("*(N/A)*");
			else
				table.addColumn(optionalDigitDF.format(compConfig.getConnectionStrategy().getMaxJumpDist())+" km");
		}
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Possible parent-section connections**");
		table.addColumn(getParentConnsStr(config.getConnectionStrategy()));
		if (meta.comparison != null) {
			if (compConfig == null)
				table.addColumn("*(N/A)*");
			else
				table.addColumn(getParentConnsStr(compConfig.getConnectionStrategy()));
		}
		table.finalizeLine();
		
		if (rupSet.hasModule(ClusterRuptures.class) ||
				(meta.comparison != null && compRupSet.hasModule(ClusterRuptures.class))) {
			table.initNewLine();
			table.addColumn("**Actual connections (after applying filters)**");
			table.addColumn(getActualConnsStr(rupSet.getModule(ClusterRuptures.class)));
			if (compRupSet != null)
				table.addColumn(getActualConnsStr(compRupSet.getModule(ClusterRuptures.class)));
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		lines.add("");
		lines.add(getSubHeading()+" Splays");
		lines.add(topLink); lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		table.addLine(header);
		table.initNewLine();
		table.addColumn("**Max Allowed Splays**");
		table.addColumn(config.getMaxNumSplays());
		if (meta.comparison != null) {
			if (compConfig == null)
				table.addColumn("*(N/A)*");
			else
				table.addColumn(compConfig.getMaxNumSplays());
		}
		table.finalizeLine();
		
		lines.addAll(table.build());
		lines.add("");
		lines.add(getSubHeading()+" Plausibility Filters");
		lines.add(topLink); lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		
		if (meta.comparison == null)
			table.addLine(meta.primary.name);
		else
			table.addLine(meta.primary.name, meta.comparison.name);
		
		HashSet<String> primaryFilters = new HashSet<>();
		for (PlausibilityFilter filter : config.getFilters())
			primaryFilters.add(filter.getName());
		
		HashSet<String> compFilters = new HashSet<>();
		if (compConfig != null)
			for (PlausibilityFilter filter : compConfig.getFilters())
				compFilters.add(filter.getName());
		
		HashSet<String> allFilters = new HashSet<>(primaryFilters);
		allFilters.addAll(compFilters);
		
		List<String> allFiltersSorted = new ArrayList<>(allFilters);
		Collections.sort(allFiltersSorted);
		
		for (String filter : allFiltersSorted) {
			table.initNewLine();
			if (primaryFilters.contains(filter))
				table.addColumn(filter);
			else
				table.addColumn("");
			if (compRupSet != null) {
				if (compFilters.contains(filter))
					table.addColumn(filter);
				else
					table.addColumn("");
			}
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		
		return lines;
	}
	
	private static String getParentConnsStr(ClusterConnectionStrategy connStrat) {
		if (connStrat == null)
			return "*(N/A)*";
		MinMaxAveTracker parentConnTrack = new MinMaxAveTracker();
		HashSet<Integer> parentIDs = new HashSet<>();
		for (FaultSection sect : connStrat.getSubSections())
			parentIDs.add(sect.getParentSectionId());
		int total = 0;
		for (int parentID1 : parentIDs) {
			int myConnections = 0;
			for (int parentID2 : parentIDs) {
				if (parentID1 == parentID2)
					continue;
				if (connStrat.areParentSectsConnected(parentID1, parentID2))
					myConnections++;
			}
			parentConnTrack.addValue(myConnections);
			total += myConnections;
		}
		return "Total: "+total+", Avg: "+twoDigits.format(parentConnTrack.getAverage())+", Range: ["
			+(int)parentConnTrack.getMin()+","+(int)parentConnTrack.getMax()+"]";
	}
	
	private static String getActualConnsStr(ClusterRuptures cRups) {
		if (cRups == null)
			return "*(N/A)*";
		HashSet<Jump> jumps = new HashSet<>();
		int numRups = cRups.size();
		for (int r=0; r<numRups; r++) {
			ClusterRupture rupture = cRups.get(r);
			for (Jump jump : rupture.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				jumps.add(jump);
			}
		}
		
		Map<Integer, Integer> actualParentCountsMap = new HashMap<>();
		for (Jump jump : jumps) {
			int parent1 = jump.fromCluster.parentSectionID;
			if (actualParentCountsMap.containsKey(parent1))
				actualParentCountsMap.put(parent1, actualParentCountsMap.get(parent1)+1);
			else
				actualParentCountsMap.put(parent1, 1);
			int parent2 = jump.toCluster.parentSectionID;
			if (actualParentCountsMap.containsKey(parent2))
				actualParentCountsMap.put(parent2, actualParentCountsMap.get(parent2)+1);
			else
				actualParentCountsMap.put(parent2, 1);
		}
		MinMaxAveTracker actualTrack = new MinMaxAveTracker();
		for (Integer parentID : actualParentCountsMap.keySet())
			actualTrack.addValue(actualParentCountsMap.get(parentID));
		return "Total: "+jumps.size()+", Avg: "+twoDigits.format(actualTrack.getAverage())+", Range: ["
			+(int)actualTrack.getMin()+","+(int)actualTrack.getMax()+"]";
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(PlausibilityConfiguration.class);
	}

}
