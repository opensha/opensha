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

import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NamedFaultPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Special Fault Detail Pages";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		File faultsDir = new File(resourcesDir.getParentFile(), "special_fault_pages");
		Preconditions.checkState(faultsDir.exists() || faultsDir.mkdir());
		
		NamedFaults faults = rupSet.requireModule(NamedFaults.class);
		
		Map<String, String> linksMap = new HashMap<>();
		for (String faultName : faults.getFaultNames()) {
			try {
				String subDirName = writeFaultPage(meta, faults, faultName, faultsDir);
				
				if (subDirName != null)
					linksMap.put(faultName, relPathToResources+"/../"+faultsDir.getName()+"/"+subDirName);
			} catch (RuntimeException e) {
				System.err.println("Error processing SectBySectDetailPlots plot for fault: " +faultName);
				e.printStackTrace();
				linksMap.put(faultName, null);
				System.err.flush();
			}
		}
		
		List<String> sortedNames = new ArrayList<>(linksMap.keySet());
		Collections.sort(sortedNames);
		

		
		String plotPrefix = "named_faults_map";
		writeFaultsPlot(resourcesDir, plotPrefix, rupSet, meta.region, sortedNames, faults);
		
		List<String> lines = new ArrayList<>();
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("![Map plot]("+relPathToResources+"/"+plotPrefix+".png)");
		table.addLine(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+plotPrefix+".geojson")
				+" "+"[Download GeoJSON]("+relPathToResources+"/"+plotPrefix+".geojson)");
		lines.addAll(table.build());
		lines.add("");
		
		table = SectBySectDetailPlots.buildSectLinksTable(linksMap, sortedNames, "Fault Name");
		lines.addAll(table.build());
		
		return lines;
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
		
		if (faultSects.isEmpty())
			return null;
		
		HashSet<Integer> allRups = new HashSet<>();
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		double totRate = 0d;
		for (FaultSection sect : faultSects)
			allRups.addAll(meta.primary.rupSet.getRupturesForSection(sect.getSectionId()));
		int rupCountNonZero = 0;
		for (int rupIndex : allRups) {
			double mag = meta.primary.rupSet.getMagForRup(rupIndex);
			minMag = Math.min(minMag, mag);
			maxMag = Math.max(maxMag, mag);
			if (meta.primary.sol != null) {
				double rate = meta.primary.sol.getRateForRup(rupIndex);
				totRate += rate;
				if (rate > 0)
					rupCountNonZero++;
			}
		}
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("_Property_", "_Value_");
		table.addLine("**Rupture Count**", countDF.format(allRups.size()));
		if (meta.primary.sol != null)
			table.addLine("**Ruptures w/ Nonzero Rates**", countDF.format(rupCountNonZero));
		table.addLine("**Magnitude Range**", "["+twoDigits.format(minMag)+", "+twoDigits.format(maxMag)+"]");
		table.addLine("**Total Rate**", (float)totRate+" /yr");
		lines.addAll(table.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		if (meta.hasPrimarySol()) {
			lines.addAll(SectBySectDetailPlots.getMFDLines(meta, faultName, faultSects, resourcesDir, topLink));
			lines.add("");
		}
		
		lines.addAll(SectBySectDetailPlots.getAlongStrikeLines(meta, faultName, faultSects, resourcesDir, topLink));
		lines.add("");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
		lines.add(tocIndex, "## Table Of Contents");

		MarkdownUtils.writeReadmeAndHTML(lines, faultDir);

		return dirName;
	}
	
	private void writeFaultsPlot(File resourcesDir, String prefix, FaultSystemRupSet rupSet, Region region,
			List<String> sortedNames, NamedFaults faults) throws IOException {
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, region);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setWritePDFs(false);
		mapMaker.setSkipNaNs(true);
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, Math.max(1d, sortedNames.size()));
		
		double[] scalars = new double[rupSet.getNumSections()];
		for (int i=0; i<scalars.length; i++)
			scalars[i] = Double.NaN;
		for (int i=0; i<sortedNames.size(); i++)
			for (FaultSection sect : faults.getSectsForFault(sortedNames.get(i)))
				scalars[sect.getSectionId()] = (double)i;
		
		mapMaker.plotSectScalars(scalars, cpt, null);
		
		mapMaker.plot(resourcesDir, prefix, "Special Faults");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(NamedFaults.class);
	}

}
