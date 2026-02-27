package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SectionTotalRateConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectBVals;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectNuclMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectParticMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchParentSectParticMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBySectDetailPlots.AlongStrikePlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.AnyWithinDistConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectIDRange;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class SectBySectDetailPlots extends AbstractRupSetPlot {
	
	private double maxNeighborDistance;
	private boolean doGeoJSON = false;

	public SectBySectDetailPlots() {
		this(Double.NaN);
	}
	
	public SectBySectDetailPlots(double maxNeighborDistance) {
		this.maxNeighborDistance = maxNeighborDistance;
	}

	@Override
	public String getName() {
		return "Parent Section Detail Pages";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		if (distAzCalc == null) {
			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
			rupSet.addModule(distAzCalc);
		}
		
		if (Double.isNaN(maxNeighborDistance)) {
			PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
			if (config == null || config.getConnectionStrategy() == null) {
				System.out.println(getName()+": WARNING, no maximum jump distance specified & no connection strategy. "
						+ "Will include everything up to 20 km.");
				maxNeighborDistance = 20d;
			} else {
				maxNeighborDistance = config.getConnectionStrategy().getMaxJumpDist();
			}
		}
		
		if (!rupSet.hasModule(ClusterRuptures.class))
			rupSet.addModule(ClusterRuptures.singleStranged(rupSet));
		if (meta.comparisonHasSameSects && !meta.comparison.rupSet.hasModule(ClusterRuptures.class))
			meta.comparison.rupSet.addModule(ClusterRuptures.singleStranged(meta.comparison.rupSet));
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		File parentsDir = new File(resourcesDir.getParentFile(), "parent_sect_pages");
		Preconditions.checkState(parentsDir.exists() || parentsDir.mkdir());
		
		List<HistScalarValues> scalarVals = new ArrayList<>();
		List<ClusterRupture> cRups = rupSet.requireModule(ClusterRuptures.class).getAll();
		for (HistScalar scalar : plotScalars)
			scalarVals.add(new HistScalarValues(scalar, rupSet, sol, cRups, distAzCalc));
		
		Map<String, Callable<String>> linkCallsMap = new HashMap<>();
		for (int parentID : sectsByParent.keySet()) {
			String parentName = sectsByParent.get(parentID).get(0).getParentSectionName();
			
			linkCallsMap.put(parentName, new SectPageCallable(meta, parentID, parentName, parentsDir, distAzCalc,
					sectsByParent, scalarVals));
		}
		
		Map<String, String> linksMap = new HashMap<>();
		
		int threads = getNumThreads();
		if (threads > 1) {
			ExecutorService exec = Executors.newFixedThreadPool(threads);
			
			Map<String, Future<String>> futures = new HashMap<>();
			
			for (String parentName : linkCallsMap.keySet())
				futures.put(parentName, exec.submit(linkCallsMap.get(parentName)));
			
			for (String parentName : futures.keySet()) {
				String link;
				try {
					String subDirName = futures.get(parentName).get();
					link = relPathToResources+"/../"+parentsDir.getName()+"/"+subDirName;
				} catch (ExecutionException | RuntimeException e) { 
					System.err.println("Error processing SectBySectDetailPlots plot for parent fault: " +parentName);
					e.printStackTrace();
					link = null;
					System.err.flush();
				} catch (InterruptedException e) {
					try {
						exec.shutdown();
					} catch (Exception e2) {}
					throw ExceptionUtils.asRuntimeException(e);
				}
				
				linksMap.put(parentName, link);
			}
			
			exec.shutdown();
		} else {
			for (String parentName : linkCallsMap.keySet()) {
				String link;
				try {
					String subDirName = linkCallsMap.get(parentName).call();
					link = relPathToResources+"/../"+parentsDir.getName()+"/"+subDirName;
				} catch (RuntimeException e) { 
					System.err.println("Error processing SectBySectDetailPlots plot for parent fault: " +parentName);
					e.printStackTrace();
					link = null;
					System.err.flush();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				
				linksMap.put(parentName, link);
			}
		}
		
		List<String> sortedNames = new ArrayList<>(linksMap.keySet());
		Collections.sort(sortedNames);
		
		TableBuilder table = buildSectLinksTable(linksMap, sortedNames, "Fault Section");
		
		List<String> lines = new ArrayList<>();
		
		// make map of parent sections to make clicking easy
		GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region) {

			@Override
			protected Feature surfFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
				return featureLink(super.surfFeature(sect, pChar), sect);
			}

			@Override
			protected Feature traceFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
				return featureLink(super.traceFeature(sect, pChar), sect);
			}
			
			private Feature featureLink(Feature feature, FaultSection sect) {
				feature.properties.set("name", sect.getParentSectionName());
				feature.properties.set("id", sect.getParentSectionId());
				feature.properties.set("subSectID", sect.getSectionId());
				return feature;
			}
			
		};
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setRegion(meta.region);
		mapMaker.setWritePDFs(false);
		
		List<Color> sectColors = new ArrayList<>();
		for (int s=0; s<rupSet.getNumSections(); s++)
			sectColors.add(null);
		// now fill in random colors for each parent section
		Random rand = new Random(rupSet.getNumSections()*sectsByParent.size());
		List<Integer> sortedIDs = new ArrayList<>(sectsByParent.keySet());
		Collections.sort(sortedIDs);
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
		for (int parentID : sortedIDs) {
			List<FaultSection> sects = sectsByParent.get(parentID);
			Color color = cpt.getColor(rand.nextFloat());
			for (FaultSection sect : sects)
				sectColors.set(sect.getSectionId(), color);
		}
		mapMaker.plotSectColors(sectColors);
		mapMaker.plot(resourcesDir, "parent_sections", "Parent Fault Sections");
		
		lines.add("This section includes links to pages with plots for specific parent fault sections.");
		lines.add("");
		lines.add("Clickable GeoJSON map to identify fault section names (each parent section is plotted in a random color): "+
				RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/parent_sections.geojson")
				+" "+"[Download GeoJSON]("+relPathToResources+"/parent_sections.geojson)");
		lines.add("");
		
		lines.addAll(table.build());

		return lines;
	}
	
	public void plotSingleParent(File outputDir, ReportMetadata meta, int parentID) throws IOException {
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		FaultSystemSolution sol = meta.primary.sol;
		
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		if (distAzCalc == null) {
			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
			rupSet.addModule(distAzCalc);
		}
		
		if (Double.isNaN(maxNeighborDistance)) {
			PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
			if (config == null || config.getConnectionStrategy() == null) {
				System.out.println(getName()+": WARNING, no maximum jump distance specified & no connection strategy. "
						+ "Will include everything up to 20 km.");
				maxNeighborDistance = 20d;
			} else {
				maxNeighborDistance = config.getConnectionStrategy().getMaxJumpDist();
			}
		}
		
		if (!rupSet.hasModule(ClusterRuptures.class))
			rupSet.addModule(ClusterRuptures.singleStranged(rupSet));
		if (meta.comparisonHasSameSects && !meta.comparison.rupSet.hasModule(ClusterRuptures.class))
			meta.comparison.rupSet.addModule(ClusterRuptures.singleStranged(meta.comparison.rupSet));
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		Preconditions.checkState(sectsByParent.containsKey(parentID));
		String parentName = sectsByParent.get(parentID).get(0).getParentSectionName();
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		List<HistScalarValues> scalarVals = new ArrayList<>();
		List<ClusterRupture> cRups = rupSet.requireModule(ClusterRuptures.class).getAll();
		for (HistScalar scalar : plotScalars)
			scalarVals.add(new HistScalarValues(scalar, rupSet, sol, cRups, distAzCalc));
		
		buildSectionPage(meta, parentID, parentName, outputDir, distAzCalc, sectsByParent, scalarVals);
	}

	static TableBuilder buildSectLinksTable(Map<String, String> linksMap, List<String> sortedNames, String header) {
		return buildSectLinksTable(linksMap, sortedNames, null, header);
	}

	static TableBuilder buildSectLinksTable(Map<String, String> linksMap, List<String> sortedNames,
			Map<String, Boolean> highlights, String header) {
		int cols;
		if (sortedNames.size() > 30)
			cols = 3;
		else if (sortedNames.size() > 15)
			cols = 2;
		else
			cols = 1;
		int rows = (int)Math.ceil((double)sortedNames.size()/(double)cols);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		for (int c=0; c<cols; c++)
			table.addColumn("Fault Section");
		table.finalizeLine();
		for (int row=0; row<rows; row++) {
			table.initNewLine();
			for (int col=0; col<cols; col++) {
				int index = rows*col+row;
				if (index >= sortedNames.size()) {
					table.addColumn("");
				} else {
					String name = sortedNames.get(index);
					String link = linksMap.get(name);
					if (link == null) {
						// there was en exception with that section, still list it so that we know there was an issue
						// to investigate
						table.addColumn("_"+name+"_");
					} else {
						if (highlights != null && highlights.get(name))
							table.addColumn("[**"+name+"**]("+linksMap.get(name)+")");
						else
							table.addColumn("["+name+"]("+linksMap.get(name)+")");
					}
				}
			}
			table.finalizeLine();
		}
		return table;
	}
	
	private static PlotCurveCharacterstics highlightChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK);
	
	private class SectPageCallable implements Callable<String> {
		
		private ReportMetadata meta;
		private int parentSectIndex;
		private String parentName;
		private File parentsDir;
		private SectionDistanceAzimuthCalculator distAzCalc;
		private Map<Integer, List<FaultSection>> sectsByParent;
		private List<HistScalarValues> scalarVals;

		public SectPageCallable(ReportMetadata meta, int parentSectIndex, String parentName, File parentsDir,
				SectionDistanceAzimuthCalculator distAzCalc, Map<Integer, List<FaultSection>> sectsByParent,
				List<HistScalarValues> scalarVals) {
			this.meta = meta;
			this.parentSectIndex = parentSectIndex;
			this.parentName = parentName;
			this.parentsDir = parentsDir;
			this.distAzCalc = distAzCalc;
			this.sectsByParent = sectsByParent;
			this.scalarVals = scalarVals;
		}

		@Override
		public String call() throws Exception {
			return buildSectionPage(meta, parentSectIndex, parentName, parentsDir,
					distAzCalc, sectsByParent, scalarVals);
		}
		
	}
	
	private String buildSectionPage(ReportMetadata meta, int parentSectIndex, String parentName, File parentsDir,
			SectionDistanceAzimuthCalculator distAzCalc, Map<Integer, List<FaultSection>> sectsByParent,
			List<HistScalarValues> scalarVals) throws IOException {
		System.out.println("Building page for: "+parentName);
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		String dirName = getFileSafe(parentName);
		
		File parentDir = new File(parentsDir, dirName);
		Preconditions.checkState(parentDir.exists() || parentDir.mkdir());
		
		File resourcesDir = new File(parentDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		lines.add("# "+parentName+" Details");
		
		lines.add("");
		
		List<Integer> allRups = rupSet.getRupturesForParentSection(parentSectIndex);
		if (allRups == null || allRups.isEmpty()) {
			lines.add("No Ruptures on "+parentName);
			
			MarkdownUtils.writeReadmeAndHTML(lines, parentDir);
			
			return dirName;
		}
		
		List<FaultSection> parentSects = sectsByParent.get(parentSectIndex);
		
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		double minLen = Double.POSITIVE_INFINITY;
		double maxLen = Double.NEGATIVE_INFINITY;
		boolean hasMultiFault = false;
		double minSingleFaultMag = Double.POSITIVE_INFINITY;
		double maxSingleFaultMag = Double.NEGATIVE_INFINITY;
		double minNonzeroRateMag = Double.POSITIVE_INFINITY;
		double maxNonzeroRateMag = Double.NEGATIVE_INFINITY;
		HashSet<Integer> directConnections = new HashSet<>();
		HashSet<Integer> allConnections = new HashSet<>();
		double totRate = 0d;
		double multiRate = 0d;
		int rupCount = 0;
		ModSectMinMags minMags = meta.primary.rupSet.getModule(ModSectMinMags.class);
		double maxMin = minMags == null ? 0d : StatUtils.max(minMags.getMinMagForSections());
		int rupCountNonZero = 0;
		int rupCountBelowMin = 0;
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int r : allRups) {
			rupCount++;
			double mag = rupSet.getMagForRup(r);
			minMag = Math.min(minMag, mag);
			maxMag = Math.max(maxMag, mag);
			double len = rupSet.getLengthForRup(r)*1e-3;
			minLen = Math.min(minLen, len);
			maxLen = Math.max(maxLen, len);
			ClusterRupture rup = cRups.get(r);
			if (rup.getTotalNumClusters() > 1) {
				hasMultiFault = true;
			} else {
				minSingleFaultMag = Math.min(minSingleFaultMag, mag);
				maxSingleFaultMag = Math.max(maxSingleFaultMag, mag);
			}
			if (meta.primary.sol != null) {
				double rate = meta.primary.sol.getRateForRup(r);
				totRate += rate;
				if (rup.getTotalNumClusters() > 1)
					multiRate += rate;
				if (rate > 0d) {
					rupCountNonZero++;
					minNonzeroRateMag = Math.min(minNonzeroRateMag, mag);
					maxNonzeroRateMag = Math.max(maxNonzeroRateMag, mag);
				}
			}
			if (minMags != null && mag < maxMin) {
				boolean below = false;
				for (int s : rupSet.getSectionsIndicesForRup(r)) {
					if (mag < minMags.getMinMagForSection(s)) {
						below = true;
						break;
					}
				}
				if (below)
					rupCountBelowMin++;
			}
			if (rup.getTotalNumClusters() > 1) {
				RuptureTreeNavigator nav = rup.getTreeNavigator();
				for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
					if (cluster.parentSectionID == parentSectIndex) {
						// find direct connections
						FaultSubsectionCluster predecessor = nav.getPredecessor(cluster);
						if (predecessor != null)
							directConnections.add(predecessor.parentSectionID);
						for (FaultSubsectionCluster descendant : nav.getDescendants(cluster))
							directConnections.add(descendant.parentSectionID);
					} else {
						allConnections.add(cluster.parentSectionID);
					}
				}
			}
		}
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("_Property_", "_Value_");
		table.addLine("**Rupture Count**", countDF.format(rupCount));
		if (minMags != null)
			table.addLine("**Ruptures Above Sect Min Mag**", countDF.format(rupCount-rupCountBelowMin));
		if (meta.primary.sol != null)
			table.addLine("**Ruptures w/ Nonzero Rates**", countDF.format(rupCountNonZero));
		table.addLine("**Magnitude Range**", "["+twoDigits.format(minMag)+", "+twoDigits.format(maxMag)+"]");
		if (hasMultiFault)
			table.addLine("**Single-Fault Magnitude Range**", "["+twoDigits.format(minSingleFaultMag)
					+", "+twoDigits.format(maxSingleFaultMag)+"]");
		if (meta.primary.sol != null)
			table.addLine("**Magnitude Range w/ Nonzero Rates**", "["+twoDigits.format(minNonzeroRateMag)
					+", "+twoDigits.format(maxNonzeroRateMag)+"]");
		table.addLine("**Length Range**", "["+countDF.format(minLen)+", "+countDF.format(maxLen)+"] km");
		if (meta.primary.sol != null) {
			table.addLine("**Total Rate**", (float)totRate+" /yr");
			table.addLine("**Multi-Fault Rate**", (float)multiRate+" /yr ("+percentDF.format(multiRate/totRate)+")");
		}
		table.addLine("**Directly-Connected Faults**", countDF.format(directConnections.size()));
		table.addLine("**All Co-Rupturing Faults**", countDF.format(allConnections.size()));
		lines.addAll(table.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		if (meta.primary.sol != null) {
			// MFDs and such only for solutions
			
			lines.add("");
			lines.addAll(getMFDLines(meta, parentName, parentSects, resourcesDir, topLink));
			
			lines.add("");
			lines.addAll(getAlongStrikeLines(meta, parentName, parentSects, resourcesDir, topLink));
			
			lines.add("");
			lines.addAll(getLengthLines(meta, parentName, parentSects, resourcesDir, topLink));
			
			lines.add("");
			lines.addAll(getRateWeightedLengthExampleLines(meta, parentName, parentSects, resourcesDir, topLink));
		} else {
			// histograms and rupture examples when we don't have a solution
			lines.add("");
			lines.addAll(getScalarLines(meta, parentSectIndex, parentName, parentSects,
					rupSet, resourcesDir, topLink, scalarVals));
		}

		lines.add("");
		lines.addAll(getConnectivityLines(meta, parentSectIndex, parentName, distAzCalc, sectsByParent,
				rupSet, resourcesDir, topLink));
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
		lines.add(tocIndex, "## Table Of Contents");
		
		MarkdownUtils.writeReadmeAndHTML(lines, parentDir);
		
		return dirName;
	}
	
	private static HistScalar[] plotScalars = { HistScalar.MAG, HistScalar.LENGTH, HistScalar.CUM_JUMP_DIST };
	
	private List<String> getScalarLines(ReportMetadata meta, int parentSectIndex, String parentName,
			List<? extends FaultSection> parentSects, FaultSystemRupSet rupSet, File outputDir, String topLink,
			List<HistScalarValues> scalars) throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add("## Scalar Histograms & Example Ruptures");
		lines.add(topLink); lines.add("");
		
		HashSet<Integer> sectRups = new HashSet<>(rupSet.getRupturesForParentSection(parentSectIndex));
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		
		for (HistScalarValues scalarVals : scalars) {
			HistScalar scalar = scalarVals.getScalar();
			lines.add("### "+scalar.getName());
			lines.add(topLink); lines.add("");
			
			String prefix = "hist_"+scalar.name();
			
			RupHistogramPlots.plotRuptureHistogram(outputDir, prefix, scalarVals, sectRups,
					null, null, MAIN_COLOR, false, false);
			lines.add("!["+scalar.getName()+" plot]("+outputDir.getName()+"/"+prefix+".png)");
			lines.add("");
			double[] fractiles = scalar.getExampleRupPlotFractiles();
			if (fractiles != null && fractiles.length > 0) {
				List<Integer> filteredIndexes = new ArrayList<>();
				List<Double> filteredVals = new ArrayList<>();
				for (int r=0; r<rupSet.getNumRuptures(); r++) {
					if (sectRups.contains(r)) {
						filteredIndexes.add(r);
						filteredVals.add(scalarVals.getValue(r));
					}
				}
				List<Integer> sortedIndexes = ComparablePairing.getSortedData(filteredVals, filteredIndexes);
				
				int[] fractileIndexes = new int[fractiles.length];
				for (int j=0; j<fractiles.length; j++) {
					double f = fractiles[j];
					if (f == 1d)
						fractileIndexes[j] = filteredIndexes.size()-1;
					else
						fractileIndexes[j] = (int)(f*filteredIndexes.size());
				}
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				for (int j=0; j<fractiles.length; j++) {
					int index = sortedIndexes.get(fractileIndexes[j]);
					double val = scalarVals.getValue(index);
					double f = fractiles[j];
					String str;
					if (f == 0d)
						str = "Minimum";
					else if (f == 1d)
						str = "Maximum";
					else
						str = "p"+new DecimalFormat("0.#").format(f*100d);
					str += ": ";
					if (val < 0.1)
						str += (float)val;
					else
						str += new DecimalFormat("0.##").format(val);
					table.addColumn("**"+str+"**");
				}
				table.finalizeLine();
				table.initNewLine();
				for (int rawIndex : fractileIndexes) {
					int index = sortedIndexes.get(rawIndex);
					String rupPrefix = "rupture_"+index;
					ClusterRupture rup = cRups.get(index);
					PlotSpec spec = RupCartoonGenerator.buildRupturePlot(rup, "Rupture "+index, false, true,
							new HashSet<>(parentSects), Color.GREEN.darker().darker(), parentName);
					RupCartoonGenerator.plotRupture(outputDir, rupPrefix, spec, true);
					table.addColumn("![Rupture "+index+"]("+outputDir.getName()+"/"+rupPrefix+".png)");
				}
				
				lines.addAll(table.wrap(4, 0).build());
				lines.add("");
			}
		}
		return lines;
	}
	
	static List<String> getLengthLines(ReportMetadata meta, String faultName,
			List<FaultSection> faultSects, File outputDir, String topLink) throws IOException {
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		FaultSystemSolution sol = meta.primary.sol;
		
		double[] rupLengths = rupSet.getLengthForAllRups();
		if (rupLengths == null)
			return new ArrayList<>();
		
		HashSet<Integer> rupIndexes = new HashSet<>();
		for (FaultSection sect : faultSects)
			rupIndexes.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
		
		if (rupIndexes.isEmpty())
			return new ArrayList<>();
		
		boolean anyRate = false;
		for (int rupIndex : rupIndexes) {
			if (sol.getRateForRup(rupIndex) > 0) {
				anyRate = true;
				break;
			}
		}
		if (!anyRate)
			return new ArrayList<>();
		
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int rupIndex : rupIndexes)
			track.addValue(rupLengths[rupIndex]*1e-3);
		
		HistogramFunction countHist = RupHistogramPlots.HistScalar.LENGTH.getHistogram(track);
		HistogramFunction rateHist = RupHistogramPlots.HistScalar.LENGTH.getHistogram(track);
		
		for (int rupIndex : rupIndexes) {
			int index = countHist.getClosestXIndex(rupLengths[rupIndex]*1e-3);
			countHist.add(index, 1d);
			rateHist.add(index, sol.getRateForRup(rupIndex));
		}
		
		List<PlotSpec> specs = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		List<Boolean> yLogs = new ArrayList<>();
		Range xRange = null;
		
		Color color = MAIN_COLOR;
		
		for (int p=0; p<3; p++) {
			HistogramFunction hist;
			boolean logY;
			boolean rateWeighted;
			if (p == 0) {
				hist = countHist;
				logY = false;
				rateWeighted = false;
			} else if (p == 1) {
				hist = rateHist;
				logY = false;
				rateWeighted = true;
			} else {
				hist = rateHist;
				logY = true;
				rateWeighted = true;
			}
			
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
			
			String title = "Rupture Length Histogram";
			String xAxisLabel = "Length (km)";
			String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
			
			if (xRange == null)
				xRange = new Range(hist.getMinX() - 0.5*hist.getDelta(),
						hist.getMaxX() + 0.5*hist.getDelta());
			
			Range yRange;
			if (logY) {
				double minY = Double.POSITIVE_INFINITY;
				double maxY = 0d;
				for (DiscretizedFunc func : funcs) {
					for (Point2D pt : func) {
						double y = pt.getY();
						if (y > 0) {
							minY = Math.min(minY, y);
							maxY = Math.max(maxY, y);
						}
					}
				}
				yRange = new Range(Math.pow(10, Math.floor(Math.log10(minY))),
						Math.pow(10, Math.ceil(Math.log10(maxY))));
			} else {
				double maxY = hist.getMaxY();
				yRange = new Range(0, 1.05*maxY);
			}
			
			specs.add(spec);
			yRanges.add(yRange);
			yLogs.add(logY);
		}
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(specs, List.of(false), yLogs, List.of(xRange), yRanges);
		gp.getChartPanel().setSize(800, 1200);
		File pngFile = new File(outputDir, "length_hist.png");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Length Distributions");
		lines.add(topLink); lines.add("");
		
		lines.add("Fault rupture length distributions. The top panel shows the raw count of ruptures in which _"
				+ faultName+"_ participates in the rupture set as a function of rupture length. The bottom two panels "
				+ "show the rate-weighted distribution (i.e., the distribution of lengths in the solution). The middle "
				+ "panel is plotted with a linear scale, and the bottom paney a logarithmic scale.");
		lines.add("");
		
		lines.add("![Length Hist]("+outputDir.getName()+"/"+pngFile.getName()+")");
		return lines;
	}
	
	static List<String> getRateWeightedLengthExampleLines(ReportMetadata meta, String faultName,
			List<FaultSection> faultSects, File outputDir, String topLink) throws IOException {
		double[] fractiles = { 0.5d, 0.75, 0.9, 0.975, 0.99, 1d };
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		FaultSystemSolution sol = meta.primary.sol;
		
		double[] rupLengths = rupSet.getLengthForAllRups();
		ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
		if (rupLengths == null || cRups == null)
			return new ArrayList<>();
		
		HashSet<Integer> rupIndexes = new HashSet<>();
		for (FaultSection sect : faultSects)
			for (int rupIndex : rupSet.getRupturesForSection(sect.getSectionId()))
				if (sol.getRateForRup(rupIndex) > 0d)
					// only include ruptures that were used here
					rupIndexes.add(rupIndex);
		
		boolean hasMultiFault = false;
		for (int rupIndex : rupIndexes) {
			Integer commonParent = null;
			for (FaultSection sect : rupSet.getFaultSectionDataForRupture(rupIndex)) {
				if (commonParent == null) {
					commonParent = sect.getParentSectionId();
				} else if (commonParent != sect.getParentSectionId()) {
					// multi-fault rupture
					hasMultiFault = true;
					break;
				}
			}
			if (hasMultiFault)
				break;
		}
		
		if (!hasMultiFault)
			return new ArrayList<>();
		
		ArbDiscrEmpiricalDistFunc lengthRateDist = new ArbDiscrEmpiricalDistFunc();
		for (int rupIndex : rupIndexes)
			lengthRateDist.set(rupLengths[rupIndex], sol.getRateForRup(rupIndex));
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.initNewLine();
		for (int f=0; f<fractiles.length; f++)
			table.addColumn(MarkdownUtils.boldCentered("p"+optionalDigitDF.format(fractiles[f]*100d)+" Example"));
		table.finalizeLine();
		
		GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(false);
		mapMaker.setWritePDFs(false);
		
		table.initNewLine();
		
		HashSet<Integer> writtenRups = new HashSet<>();
		for (int f=0; f<fractiles.length; f++) {
			double targetLen = lengthRateDist.getInterpolatedFractile(fractiles[f]);
			
			double closestDiff = Double.POSITIVE_INFINITY;
			int closestIndex = -1;
			
			for (int rupIndex : rupIndexes) {
				double len = rupLengths[rupIndex];
				double diff = Math.abs(len - targetLen);
				if (diff < closestDiff) {
					closestDiff = diff;
					closestIndex = rupIndex;
				}
			}
			
			int rupIndex = closestIndex;
			
			String rupPrefix = "rupture_"+rupIndex;
			
			if (!writtenRups.contains(rupIndex)) {
				// not a duplicate, need to plot
				MinMaxAveTracker latTrack = new MinMaxAveTracker();
				MinMaxAveTracker lonTrack = new MinMaxAveTracker();
				List<FaultSection> rupSects = rupSet.getFaultSectionDataForRupture(rupIndex);
				for (FaultSection sect : rupSects) {
					LocationList locs;
					if (sect.getAveDip() == 90d)
						locs = sect.getFaultTrace();
					else
						locs = sect.getFaultSurface(1d).getEvenlyDiscritizedPerimeter();
					for (Location loc : locs) {
						latTrack.addValue(loc.getLatitude());
						lonTrack.addValue(loc.getLongitude());
					}
				}
				
				double minLon = lonTrack.getMin();
				double maxLon = lonTrack.getMax();
				double minLat = latTrack.getMin();
				double maxLat = latTrack.getMax();
				maxLat += 0.05;
				minLat -= 0.05;
				maxLon += 0.05;
				minLon -= 0.05;
				
				Region region = new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
				mapMaker.setRegion(region);
				mapMaker.setSectHighlights(rupSects, highlightChar);
				
				double length = rupLengths[rupIndex]*1e-3;
				
				String title = "Rupture "+rupIndex+", M"+optionalDigitDF.format(rupSet.getMagForRup(rupIndex))
					+", "+optionalDigitDF.format(length)+" km, rate="+expProbDF.format(sol.getRateForRup(rupIndex));
				mapMaker.plot(outputDir, rupPrefix, title);
				
				writtenRups.add(rupIndex);
			}
			table.addColumn("![Rupture "+rupIndex+"]("+outputDir.getName()+"/"+rupPrefix+".png)");
		}
		table.finalizeLine();
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Rupture Examples");
		lines.add(topLink); lines.add("");
		
		lines.add("The following table includes example ruptures, selected at various percentiles from the rate-weighted "
				+ "rupture length distribution. So, for example, the rupture with length closest to the median length "
				+ "(again, rate-weighted) is plotted in the first column ('p50 Example'). The longest participating "
				+ "rupture is plotted last ('p100 Example').");
		lines.add("");
		lines.add("It is important to note that the rupture examples here may not be representative, and may have "
				+ "negligible rates. They are included for illustration purposes only.");
		
		lines.add("");
		lines.addAll(table.wrap(4, 0).build());
		
		return lines;
	}

	private List<String> getConnectivityLines(ReportMetadata meta, int parentSectIndex, String parentName,
			SectionDistanceAzimuthCalculator distAzCalc, Map<Integer, List<FaultSection>> sectsByParent,
			FaultSystemRupSet rupSet, File outputDir, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add("## Connectivity");
		lines.add(topLink); lines.add("");
		
		List<FaultSection> mySects = sectsByParent.get(parentSectIndex);
		
		// include all nearby parents up to the cutoff distance
		List<Integer> parentsIDsToConsider = new ArrayList<>();
		Map<Integer, Double> matchMinDists = new HashMap<>();
		for (int parentID : sectsByParent.keySet()) {
			if (parentID == parentSectIndex)
				continue;
			double minDist = Double.POSITIVE_INFINITY;
			for (FaultSection s1 : sectsByParent.get(parentID))
				for (FaultSection s2 : mySects)
					minDist = Double.min(minDist, distAzCalc.getDistance(s1, s2));
			if (minDist <= maxNeighborDistance) {
				parentsIDsToConsider.add(parentID);
				matchMinDists.put(parentID, minDist);
			}
		}
//		System.out.println("\t"+parentsIDsToConsider.size()+" parents are within "+(float)maxNeighborDistance+" km");
		
		ClusterRuptures clusterRups = rupSet.requireModule(ClusterRuptures.class);

		RupConnectionsData rupData = new RupConnectionsData(parentSectIndex, clusterRups, rupSet, meta.primary.sol);
//		System.out.println("\t"+rupData.parentCoruptureCounts.size()+" parents ("+rupData.sectCoruptureCounts.size()
//			+" sects) corupture with this parent");
		
		RupConnectionsData compRupData = null;
		if (meta.comparisonHasSameSects)
			compRupData = new RupConnectionsData(parentSectIndex,
					meta.comparison.rupSet.requireModule(ClusterRuptures.class),
					meta.comparison.rupSet, meta.comparison.sol);
		
		HashSet<FaultSection> plotSectsSet = new HashSet<>();
		plotSectsSet.addAll(mySects);
		// add all sections within the cutoff
		for (int parentID : parentsIDsToConsider)
			plotSectsSet.addAll(sectsByParent.get(parentID));
		// add all sections that corupture
		for (int sectID : rupData.sectCoruptureCounts.keySet())
			plotSectsSet.add(rupSet.getFaultSectionData(sectID));
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection plotSect : plotSectsSet) {
			LocationList perim;
			RuptureSurface surf = plotSect.getFaultSurface(5d);
			try {
				perim = surf.getPerimeter();
			} catch (RuntimeException e) {
				perim = surf.getEvenlyDiscritizedPerimeter();
			}
			for (Location loc : perim) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		// also buffer around our trace
		if (maxNeighborDistance > 0d && maxNeighborDistance < 500d) {
			for (FaultSection sect : mySects) {
				LocationList traceBuffer;
				try {
					traceBuffer = new Region(sect.getFaultTrace(), maxNeighborDistance).getBorder();
				} catch (RuntimeException e) {
					// that can fail for some weird-squirrely fault traces
					// fall back to juse using the first/last location
					traceBuffer = new Region(LocationList.of(sect.getFaultTrace().first(), sect.getFaultTrace().last()),
							maxNeighborDistance).getBorder();
				}
				for (Location loc : traceBuffer) {
					latTrack.addValue(loc.lat);
					lonTrack.addValue(loc.lon);
				}
			}
		}
		Region plotRegion = new Region(new Location(latTrack.getMin()-0.1, lonTrack.getMin()-0.1),
				new Location(latTrack.getMax()+0.1, lonTrack.getMax()+0.1));
		
		GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(doGeoJSON);
		mapMaker.setRegion(plotRegion);
		mapMaker.setWritePDFs(false);
		mapMaker.setSkipNaNs(true);
		mapMaker.setSectHighlights(mySects, highlightChar);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		List<String> jsonLinks = doGeoJSON ? new ArrayList<>() : null;
		
		boolean[] isPlotRates;
		if (rupData.sectCoruptureRates == null)
			// no rates, plot counts only
			isPlotRates = new boolean[] {false};
		else
			// have rates, plot rates only
			isPlotRates = new boolean[] {true};
		for (boolean rate : isPlotRates) {
			Map<Integer, ? extends Number> valsMap = rate ? rupData.sectCoruptureRates : rupData.sectCoruptureCounts;
			if (valsMap == null)
				continue;
			double[] scalars = new double[rupSet.getNumSections()];
			MinMaxAveTracker logValTrack = new MinMaxAveTracker();
			for (int s=0; s<scalars.length; s++) {
				if (valsMap.containsKey(s) && valsMap.get(s).doubleValue() > 0d) {
					double logVal = Math.log10(valsMap.get(s).doubleValue());
					logValTrack.addValue(logVal);
					scalars[s] = logVal;
				} else {
					scalars[s] = Double.NaN;
				}
			}
			double min, max;
			String label;
			String prefix;
			if (rate) {
				if (logValTrack.getNum() == 0) {
					min = -6;
					max = -1d;
				} else {
					max = Math.ceil(logValTrack.getMax());
					min = Math.floor(logValTrack.getMin());
					// at most 5 orders of magnitude
					min = Math.max(min, max-5d);
				}
				label = "Log10 Co-rupture Rate";
				prefix = "corupture_rate";
			} else {
				if (logValTrack.getNum() == 0) {
					min = 0;
					max = 1;
				} else {
					min = Math.floor(logValTrack.getMin());
					max = Math.ceil(logValTrack.getMax());
				}
				label = "Log10 Co-rupture Count";
				prefix = "corupture_count";
			}
			if (min == max)
				max++;
			CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max);
			cpt.setNanColor(Color.GRAY);
			
			mapMaker.clearSectScalars();
			mapMaker.plotSectScalars(scalars, cpt, label);
			mapMaker.plot(outputDir, prefix, parentName+" Connectivity");
			table.addColumn("![Map]("+outputDir.getName()+"/"+prefix+".png)");
			
			if (doGeoJSON)
				jsonLinks.add(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", prefix+".geojson")
						+" [Download GeoJSON]("+prefix+".geojson)");
		}
		table.finalizeLine();
		if (doGeoJSON)
			table.addLine(jsonLinks);
		
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("### Nearby Sections");
		lines.add(topLink); lines.add("");
		String nearbyLink = "[_(back to table)_](#"+MarkdownUtils.getAnchorName("Nearby Sections")+")";
		
		PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
		
		Map<String, String> linksMap = new HashMap<>();
		Map<String, List<String>> parentMarkdowns = new HashMap<>(); 
		Map<String, Boolean> parentConnecteds = new HashMap<>(); 
		Map<String, Double> parentDists = new HashMap<>();
		for (int parentID : parentsIDsToConsider) {
			List<FaultSection> parentSects = sectsByParent.get(parentID);
			String name = parentSects.get(0).getParentSectionName();
			double minDist = matchMinDists.get(parentID);
			
			name += ", "+optionalDigitDF.format(minDist)+" km away";
			
			List<String> connLines = new ArrayList<>();
			connLines.add("#### "+name);
			connLines.add(nearbyLink); connLines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("");
			table.addColumn(meta.primary.name);
			if (meta.comparisonHasSameSects)
				table.addColumn(meta.comparison.name);
			table.finalizeLine();
			
			parentDists.put(name, minDist);
			
			boolean connected = rupData.parentCoruptures.containsKey(parentID);
			
			table.initNewLine();
			table.addColumn("**Connected?**");
			table.addColumn(connected);
			if (meta.comparisonHasSameSects)
				table.addColumn(compRupData.parentCoruptures.containsKey(parentID));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Directly Connected?**");
			boolean directly = rupData.directlyConnectedParents.contains(parentID);
			table.addColumn(directly);
			if (meta.comparisonHasSameSects)
				table.addColumn(compRupData.directlyConnectedParents.contains(parentID));
			table.finalizeLine();
			
			if (directly) {
				double minRupDist = Double.POSITIVE_INFINITY;
				double rateWeightDist = 0d;
				double sumRate = 0d;
				for (int rupIndex : rupData.parentCoruptures.get(parentID)) {
					ClusterRupture rup = clusterRups.get(rupIndex);
					for (Jump jump : rup.getJumpsIterable()) {
						if (jump.fromCluster.parentSectionID == parentID && jump.toCluster.parentSectionID == parentSectIndex
								|| jump.fromCluster.parentSectionID == parentSectIndex && jump.toCluster.parentSectionID == parentID) {
							minRupDist = Math.min(minRupDist, jump.distance);
							if (meta.primary.sol != null) {
								double rate = meta.primary.sol.getRateForRup(rupIndex);
								rateWeightDist += rate*jump.distance;
								sumRate += rate;
							}
							break;
						}
					}
				}
				if (sumRate > 0)
					rateWeightDist /= sumRate;
				
				table.initNewLine().addColumn("**Min Co-Rupture Dist**").addColumn(optionalDigitDF.format(minRupDist)+" km");
				if (meta.comparisonHasSameSects) {
					List<Integer> compCorups = compRupData.parentCoruptures.get(parentID);
					if (compCorups ==  null) {
						table.addColumn("_N/A_");
						table.finalizeLine();
						if (sumRate > 0) {
							table.initNewLine().addColumn("**Min Rate-Weighted Dist**").addColumn(optionalDigitDF.format(rateWeightDist)+" km");
							table.addColumn("_N/A_");
							table.finalizeLine();
						}
					} else {
						double cminRupDist = Double.POSITIVE_INFINITY;
						double crateWeightDist = 0d;
						double csumRate = 0d;
						ClusterRuptures compClusterRuprs = meta.comparison.rupSet.requireModule(ClusterRuptures.class);
						for (int rupIndex : compCorups) {
							ClusterRupture rup = compClusterRuprs.get(rupIndex);
							for (Jump jump : rup.getJumpsIterable()) {
								if (jump.fromCluster.parentSectionID == parentID && jump.toCluster.parentSectionID == parentSectIndex
										|| jump.fromCluster.parentSectionID == parentSectIndex && jump.toCluster.parentSectionID == parentID) {
									cminRupDist = Math.min(cminRupDist, jump.distance);
									if (meta.comparison.sol != null) {
										double rate = meta.comparison.sol.getRateForRup(rupIndex);
										crateWeightDist += rate*jump.distance;
										csumRate += rate;
									}
									break;
								}
							}
						}
						if (csumRate > 0)
							crateWeightDist /= csumRate;
						
						table.addColumn(optionalDigitDF.format(cminRupDist)+" km");
						table.finalizeLine();
						if (sumRate > 0) {
							table.initNewLine().addColumn("**Min Rate-Weighted Dist**").addColumn(optionalDigitDF.format(rateWeightDist)+" km");
							if (csumRate > 0)
								table.addColumn(optionalDigitDF.format(crateWeightDist)+" km");
							else
								table.addColumn("_N/A_");
							table.finalizeLine();
						}
					}
				} else {
					table.finalizeLine();
					if (sumRate > 0) {
						table.initNewLine();
						table.addColumn("**Min Rate-Weighted Dist**").addColumn(optionalDigitDF.format(rateWeightDist)+" km");
						table.finalizeLine();
					}
				}
			}
			
			table.initNewLine();
			boolean coruptures = false;
			table.addColumn("**Co-rupture Count**");
			if (rupData.parentCoruptures.containsKey(parentID)) {
				table.addColumn(countDF.format(rupData.parentCoruptures.get(parentID).size()));
				coruptures = true;
			} else {
				table.addColumn("0");
			}
			if (meta.comparisonHasSameSects) {
				if (compRupData.parentCoruptures.containsKey(parentID)) {
					table.addColumn(countDF.format(compRupData.parentCoruptures.get(parentID).size()));
					coruptures = true;
				} else {
					table.addColumn("0");
				}
			}
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Co-rupture Rate**");
			if (rupData.parentCoruptureRates != null && rupData.parentCoruptureRates.containsKey(parentID))
				table.addColumn(rupData.parentCoruptureRates.get(parentID).floatValue());
			else
				table.addColumn("_N/A_");
			if (meta.comparisonHasSameSects) {
				if (compRupData.parentCoruptureRates != null && compRupData.parentCoruptureRates.containsKey(parentID))
					table.addColumn(compRupData.parentCoruptureRates.get(parentID).floatValue());
				else
					table.addColumn("_N/A_");
			}
			table.finalizeLine();
			
			if (coruptures) {
				table.initNewLine();
				table.addColumn("**Co-rupture Mag Range**");
				if (rupData.parentCoruptureMags.containsKey(parentID)) {
					MinMaxAveTracker minMax = rupData.parentCoruptureMags.get(parentID);
					table.addColumn("["+twoDigits.format(minMax.getMin())+", "+twoDigits.format(minMax.getMax())+"]");
				} else {
					table.addColumn("_N/A_");
				}
				if (meta.comparisonHasSameSects) {
					if (compRupData.parentCoruptureMags.containsKey(parentID)) {
						MinMaxAveTracker minMax = compRupData.parentCoruptureMags.get(parentID);
						table.addColumn("["+twoDigits.format(minMax.getMin())+", "+twoDigits.format(minMax.getMax())+"]");
					} else {
						table.addColumn("_N/A_");
					}
				}
				table.finalizeLine();
			}
			
			connLines.addAll(table.build());
			
			// only do the search to figure out why something wasn't connected if:
			// * we have filters & a connection strategy
			// * they're not connected
			// * we only have a rupture set (if we have a solution, we're probably not too worried about this anymore)
			if (!connected && config != null && config.getFilters() != null && !config.getFilters().isEmpty()
					&& config.getConnectionStrategy() != null && meta.primary.sol == null) {
				List<FaultSection> pairSects = new ArrayList<>();
				List<FaultSubsectionCluster> pairClusters = new ArrayList<>();
				pairSects.addAll(mySects);
				pairClusters.add(new FaultSubsectionCluster(mySects));
				pairSects.addAll(parentSects);
				pairClusters.add(new FaultSubsectionCluster(parentSects));
				ClusterConnectionStrategy pairStrat = new AnyWithinDistConnectionStrategy(
						pairSects, pairClusters, distAzCalc, maxNeighborDistance);
				
				PlausibilityConfiguration pairConfig = new PlausibilityConfiguration(
						List.of(new FullConnectionOneWayFilter(pairClusters.get(0), pairClusters.get(1))), 0, pairStrat, distAzCalc);
				ClusterRuptureBuilder builder = new ClusterRuptureBuilder(pairConfig);
//				System.out.println("\tBuilding ruptures from "+parentName+" to "+name);
				List<ClusterRupture> possibleRups = builder.build(new ConnectionPointsRuptureGrowingStrategy());
				
//				System.out.println("\tBuilt "+possibleRups.size()+" possible ruptures including "+name);
				
				List<Boolean> passes = new ArrayList<>();
				boolean hasPassAll = false;
				List<PlausibilityFilter> filters = config.getFilters();
				for (int p=0; p<filters.size(); p++)
					passes.add(false);
				
				for (ClusterRupture rup : possibleRups) {
					boolean passAll = true;
					for (int p=0; p<filters.size(); p++) {
						PlausibilityFilter filter = filters.get(p);
						PlausibilityResult result;
						try {
							result = filter.apply(rup, false);
						} catch (Exception e) {
							result = PlausibilityResult.FAIL_HARD_STOP;
						}
						if (result.isPass())
							passes.set(p, true);
						else
							passAll = false;
					}
					if (passAll)
						hasPassAll = true;
				}
				
				connLines.add("");
				connLines.add("### Plausibility Filters Comparisons");
				connLines.add("");
				connLines.add("Here, we try to figure out which plausibility filter(s) precluded connections "
						+ "between these two fault sections. This is not necessarily done using the exact same connection "
						+ "strategy (i.e., the algorithm that chooses where jumps occur), so results might not exactly "
						+ "match the original rupture building algorithm.");
				connLines.add("");
				connLines.add("Plausibility filters precluding direct connection:");
				connLines.add("");
				boolean hasNever = false;
				for (int p=0; p<filters.size(); p++) {
					PlausibilityFilter filter = filters.get(p);
					if (!passes.get(p)) {
						hasNever = true;
						connLines.add("* "+filter.getName());
					}
				}
				if (!hasNever) {
					if (hasPassAll)
						connLines.add("* _None found, and this test found a rupture that passed all filters. This may be "
								+ "due to the connection strategy: we try many connection points here and may have found "
								+ "one that works and was not included in the original rupture set, or no connections "
								+ "may have been allowed to this fault in the case of an adaptive strategy._");
					else
						connLines.add("* _Each filter passed for at least one possible rupture connecting these two "
								+ "sections, but no individual rupture between the two sections passed all filters "
								+ "simultaneously._");
				}
			}

			parentConnecteds.put(name, connected);
			linksMap.put(name, "#"+MarkdownUtils.getAnchorName(name));
			parentMarkdowns.put(name, connLines);
		}
		
		List<String> sortedNames = ComparablePairing.getSortedData(parentDists);
		
		lines.addAll(buildSectLinksTable(linksMap, sortedNames, parentConnecteds, "Fault Section").build());
		lines.add("");
		for (String name : sortedNames)
			lines.addAll(parentMarkdowns.get(name));
		return lines;
	}
	
	private static class FullConnectionOneWayFilter implements PlausibilityFilter {

		private FaultSubsectionCluster from;
		private FaultSubsectionCluster to;
		
		private HashSet<FaultSection> validStarts;
		private HashSet<FaultSection> validEnds;

		public FullConnectionOneWayFilter(FaultSubsectionCluster from, FaultSubsectionCluster to) {
			this.from = from;
			this.to = to;
			validStarts = new HashSet<>();
			validStarts.add(from.startSect);
			validStarts.add(from.subSects.get(from.subSects.size()-1));
			validEnds = new HashSet<>();
			validEnds.add(to.startSect);
			validEnds.add(to.subSects.get(to.subSects.size()-1));
		}

		@Override
		public String getShortName() {
			return "One Way";
		}

		@Override
		public String getName() {
			return "One Way";
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			if (rupture.clusters.length > 2)
				return PlausibilityResult.FAIL_HARD_STOP;
			FaultSubsectionCluster firstCluster = rupture.clusters[0];
			if (firstCluster.parentSectionID != from.parentSectionID)
				return PlausibilityResult.FAIL_HARD_STOP;
			// make sure it starts with an end
			if (!validStarts.contains(firstCluster.startSect))
				return PlausibilityResult.FAIL_HARD_STOP;
			if (rupture.clusters.length < 2)
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			FaultSubsectionCluster secondCluster = rupture.clusters[1];
			if (secondCluster.parentSectionID != to.parentSectionID)
				return PlausibilityResult.FAIL_HARD_STOP;
			if (!validEnds.contains(secondCluster.subSects.get(secondCluster.subSects.size()-1)))
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			return PlausibilityResult.PASS;
		}
		
	}
	
	private static class RupConnectionsData {
		
		private Map<Integer, List<Integer>> parentCoruptures;
		private HashSet<Integer> directlyConnectedParents;
		private Map<Integer, Double> parentCoruptureRates;
		private Map<Integer, MinMaxAveTracker> parentCoruptureMags;
		private Map<Integer, Integer> sectCoruptureCounts;
		private Map<Integer, Double> sectCoruptureRates;
		
		public RupConnectionsData(int parentSectIndex, ClusterRuptures clusterRups,
				FaultSystemRupSet rupSet, FaultSystemSolution sol) {
			parentCoruptures = new HashMap<>();
			directlyConnectedParents = new HashSet<>();
			parentCoruptureRates = sol == null ? null : new HashMap<>();
			sectCoruptureCounts = new HashMap<>();
			parentCoruptureMags = new HashMap<>();
			sectCoruptureRates = sol == null ? null : new HashMap<>();
			
			for (int rupIndex : rupSet.getRupturesForParentSection(parentSectIndex)) {
				ClusterRupture rup = clusterRups.get(rupIndex);
				double rate = sectCoruptureRates == null ? Double.NaN : sol.getRateForRup(rupIndex);
				double mag = rupSet.getMagForRup(rupIndex);
				RuptureTreeNavigator nav = rup.getTreeNavigator();
				for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
					if (cluster.parentSectionID == parentSectIndex) {
						FaultSubsectionCluster pred = nav.getPredecessor(cluster);
						if (pred != null)
							directlyConnectedParents.add(pred.parentSectionID);
						for (FaultSubsectionCluster desc : nav.getDescendants(cluster))
							directlyConnectedParents.add(desc.parentSectionID);
						continue;
					}
					List<Integer> coruptures = parentCoruptures.get(cluster.parentSectionID);
					if (coruptures == null) {
						coruptures = new ArrayList<>();
						parentCoruptures.put(cluster.parentSectionID, coruptures);
						if (parentCoruptureRates != null)
							parentCoruptureRates.put(cluster.parentSectionID, rate);
						parentCoruptureMags.put(cluster.parentSectionID, new MinMaxAveTracker());
					} else if (parentCoruptureRates != null) {
						parentCoruptureRates.put(cluster.parentSectionID, parentCoruptureRates.get(cluster.parentSectionID)+rate);
					}
					parentCoruptureMags.get(cluster.parentSectionID).addValue(mag);
					coruptures.add(rupIndex);
					for (FaultSection sect : cluster.subSects) {
						Integer id = sect.getSectionId();
						if (sectCoruptureCounts.containsKey(id)) {
							sectCoruptureCounts.put(id, sectCoruptureCounts.get(id)+1);
							if (sectCoruptureRates != null)
								sectCoruptureRates.put(id, sectCoruptureRates.get(id)+rate);
						} else {
							sectCoruptureCounts.put(id, 1);
							if (sectCoruptureRates != null)
								sectCoruptureRates.put(id, rate);
						}
					}
				}
			}
		}
	}

	static List<String> getMFDLines(ReportMetadata meta, String faultName,
			List<FaultSection> faultSects, File outputDir, String topLink) throws IOException {
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		FaultSystemSolution sol = meta.primary.sol;
		
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		HashSet<Integer> rups = new HashSet<>();
		for (FaultSection sect : faultSects) {
			List<Integer> myRups = rupSet.getRupturesForSection(sect.getSectionId());
			rups.addAll(myRups);
			if (myRups.size() > 0) {
				minMag = Math.min(minMag, rupSet.getMinMagForSection(sect.getSectionId()));
				maxMag = Math.max(maxMag, rupSet.getMaxMagForSection(sect.getSectionId()));
			}
		}
		
		if (rups.isEmpty())
			return new ArrayList<>();
		
		if (sol.hasModule(RupMFDsModule.class)) {
			// we have rupture MFDs, expand mag range as necessary
			RupMFDsModule mfds = sol.getModule(RupMFDsModule.class);
			for (int rupIndex : rups) {
				DiscretizedFunc mfd = mfds.getRuptureMFD(rupIndex);
				if (mfd != null) {
					minMag = Math.min(minMag, mfd.getMinX());
					maxMag = Math.max(maxMag, mfd.getMaxX());
				}
			}
		}
		
		GridSourceProvider gridProv = sol.getGridSourceProvider();
		FaultGridAssociations gridAssoc = rupSet.getModule(FaultGridAssociations.class);
		boolean hasGridded = gridProv != null && (gridProv instanceof GridSourceList || gridAssoc != null);
		if (hasGridded)
			minMag = Math.min(minMag, 5d);
		
		IncrementalMagFreqDist defaultMFD = SolMFDPlot.initDefaultMFD(6d, 8d, minMag, maxMag);
		
		SummedMagFreqDist nuclTargetMFD = null;
		if (meta.primary.rupSet.hasModule(InversionTargetMFDs.class))
			// see if we have section nucleation MFDs
			nuclTargetMFD = calcTargetMFD(faultSects, meta.primary.rupSet.requireModule(InversionTargetMFDs.class));
		
		Range xRange = SolMFDPlot.xRange(defaultMFD);
		List<XY_DataSet> incrFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> incrChars = new ArrayList<>();
		
		IncrementalMagFreqDist particMFD = sol.calcParticipationMFD_forRups(
				rups, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
		SummedMagFreqDist nuclMFD = new SummedMagFreqDist(defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
		for (FaultSection sect : faultSects)
			nuclMFD.addIncrementalMagFreqDist(sol.calcNucleationMFD_forSect(
					sect.getSectionId(), defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size()));
		if (hasGridded) {
			IncrementalMagFreqDist griddedMFD = null;
			for (FaultSection sect : faultSects) {
				if (gridProv instanceof GridSourceList) {
					for (GriddedRupture rup : ((GridSourceList)gridProv).getAssociatedRuptures(sect.getSectionId())) {
						double assocFract = rup.getFractAssociated(sect.getSectionId());
						double assocRate = assocFract * rup.rate;
						if (griddedMFD == null) {
							IncrementalMagFreqDist refMFD = ((GridSourceList)gridProv).getRefMFD();
							griddedMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
						}
						griddedMFD.add(griddedMFD.getClosestXIndex(rup.properties.magnitude), assocRate);
					}
				} else {
					Map<Integer, Double> scaledNodeFracts = gridAssoc.getScaledNodeFractions(sect.getSectionId());
					for (int nodeIndex : scaledNodeFracts.keySet()) {
						double fract = scaledNodeFracts.get(nodeIndex);
						IncrementalMagFreqDist nodeMFD = gridProv.getMFD_SubSeisOnFault(nodeIndex);
						
						if (fract > 0 && nodeMFD != null) {
							for (int i=0; i<nodeMFD.size(); i++) {
								double y = nodeMFD.getY(i);
								if (y > 0) {
									if (griddedMFD == null) {
										griddedMFD = new IncrementalMagFreqDist(nodeMFD.getMinX(), nodeMFD.size(), nodeMFD.getDelta());
									} else {
										Preconditions.checkState((float)griddedMFD.getMinX() == (float)nodeMFD.getMinX());
										Preconditions.checkState((float)griddedMFD.getDelta() == (float)nodeMFD.getDelta());
										if (griddedMFD.size() <= i) {
											// need to elarge it
											IncrementalMagFreqDist newMFD = new IncrementalMagFreqDist(
													nodeMFD.getMinX(), nodeMFD.size(), nodeMFD.getDelta());
											for (int j=0; j<griddedMFD.size(); j++)
												newMFD.set(j, griddedMFD.getY(j));
											griddedMFD = newMFD;
										}
									}
									griddedMFD.add(i, y);
								}
							}
						}
					}
				}
			}
			if (griddedMFD != null) {griddedMFD.setName("Sub-Seismogenic");
				incrFuncs.add(griddedMFD);
				incrChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.CYAN));
			}
		}

		incrFuncs.add(particMFD);
		incrChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, MAIN_COLOR));
		
		incrFuncs.add(nuclMFD);
		incrChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, MAIN_COLOR.darker()));
		
		BranchSectNuclMFDs branchNuclMFDs = sol.getModule(BranchSectNuclMFDs.class);
		// decided to just do branch MFDs on cumulative, uncomment if we want them back on incremental
//		if (branchMFDs != null) {
//			double alpha = minMaxColor.getAlpha()/255d;
//			
//			Color nuclColor = minMaxColor.darker();
//			
//			// this is the same as a transparent one over a white background
//			// do it this way so that the lower bound shows up over the histogram
//			Color fakeAlpha = new Color(
//					(float)((1d-alpha) + alpha*nuclColor.getRed()/255d),
//					(float)((1d-alpha) + alpha*nuclColor.getGreen()/255d),
//					(float)((1d-alpha) + alpha*nuclColor.getBlue()/255d));
//			
//			List<Integer> sectIDs = new ArrayList<>(faultSects.size());
//			for (FaultSection sect : faultSects)
//				sectIDs.add(sect.getSectionId());
//			IncrementalMagFreqDist[] minMax = branchMFDs.calcIncrementalSectFractiles(sectIDs, 0d, 1d);
//			minMax[0].setName("Nucleation Min/Max");
//			addFakeHistFromFunc(minMax[0], incrFuncs, incrChars,
//					new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, fakeAlpha));
//			minMax[1].setName(null);
//			addFakeHistFromFunc(minMax[1], incrFuncs, incrChars,
//					new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, fakeAlpha));
//		}

		List<DiscretizedFunc> cmlFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> cmlChars = new ArrayList<>();
		
		if (nuclTargetMFD != null) {
			nuclTargetMFD.setName("Target Nucleation");
			
			Color targetColor = Color.GREEN.darker();
			
			addFakeHistFromFunc(nuclTargetMFD, incrFuncs, incrChars,
					new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
			
			cmlFuncs.add(nuclTargetMFD.getCumRateDistWithOffset());
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
		}
		
		// add G-R fit overlay
		boolean[] binsAvail = new boolean[SectBValuePlot.refFunc.size()];
		boolean[] binsUsed = new boolean[SectBValuePlot.refFunc.size()];
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		RupMFDsModule rupMFDs = sol.getModule(RupMFDsModule.class);
		for (FaultSection sect : faultSects)
			SectBValuePlot.calcSectMags(sect.getSectionId(), meta.primary.sol, modMinMags, rupMFDs, binsAvail, binsUsed);
		// traslate section MFD to b-value plot x values
		IncrementalMagFreqDist transNuclMFD = new IncrementalMagFreqDist(SectBValuePlot.refFunc.getMinX(),
				SectBValuePlot.refFunc.size(), SectBValuePlot.refFunc.getDelta());
		double minNuclMag = Double.POSITIVE_INFINITY;
		double maxNuclMag = 0d;
		for (Point2D pt : nuclMFD) {
			if (pt.getY() > 0) {
				transNuclMFD.add(transNuclMFD.getClosestXIndex(pt.getX()), pt.getY());
				minNuclMag = Math.min(minNuclMag, pt.getX());
				maxNuclMag = Math.max(maxNuclMag, pt.getX());
			}
		}
		if (transNuclMFD.calcSumOfY_Vals() > 0d) {
			double bVal = SectBValuePlot.estBValue(binsAvail, binsUsed, transNuclMFD).b;
			GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(nuclMFD.getMinX(), nuclMFD.size(), nuclMFD.getDelta());
			gr.setAllButTotMoRate(gr.getX(gr.getClosestXIndex(minNuclMag)), gr.getX(gr.getClosestXIndex(maxNuclMag)),
					transNuclMFD.calcSumOfY_Vals(), bVal);
			
			gr.setName("Nucl. G-R fit, b="+optionalDigitDF.format(bVal));
			addFakeHistFromFunc(gr, incrFuncs, incrChars,
					new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, new Color(0, 0, 0, 127)));
		}
		
		SummedMagFreqDist compNuclMFD = null;
		EvenlyDiscretizedFunc compCmlParticMFD = null;
		
		if (meta.comparisonHasSameSects && meta.comparison.sol != null) {
			HashSet<Integer> compRups = new HashSet<>();
			for (FaultSection sect : faultSects)
				compRups.addAll(meta.comparison.rupSet.getRupturesForSection(sect.getSectionId()));
			IncrementalMagFreqDist compParticMFD = meta.comparison.sol.calcParticipationMFD_forRups(
					compRups, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
			compNuclMFD = new SummedMagFreqDist(defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
			for (FaultSection sect : faultSects)
				compNuclMFD.addIncrementalMagFreqDist(meta.comparison.sol.calcNucleationMFD_forSect(
						sect.getSectionId(), defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size()));
			
			particMFD.setName("Primary Participation");
			nuclMFD.setName("Primary Nucleation");
			compParticMFD.setName("Comparison Participation");
			compNuclMFD.setName("Comparison Nucleation");
			
			addFakeHistFromFunc(compParticMFD, incrFuncs, incrChars,
					new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, COMP_COLOR));
			
			addFakeHistFromFunc(compNuclMFD, incrFuncs, incrChars,
					new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, COMP_COLOR));
			
			compCmlParticMFD = compParticMFD.getCumRateDistWithOffset();
			cmlFuncs.add(compCmlParticMFD);
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, COMP_COLOR));
			cmlFuncs.add(compNuclMFD.getCumRateDistWithOffset());
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, COMP_COLOR));
			
			// decided that having the comparison target is too busy, uncomment if you want it back
//			SummedMagFreqDist compNuclTargetMFD = null;
//			if (nuclTargetMFD != null && meta.comparison.rupSet.hasModule(InversionTargetMFDs.class))
//				// see if we have section nucleation MFDs
//				compNuclTargetMFD = calcTargetMFD(faultSects, meta.comparison.rupSet.requireModule(InversionTargetMFDs.class));
//			
//			if (compNuclTargetMFD != null) {
//				// if it's identical, skip it
//				boolean identical = compNuclTargetMFD.size() == nuclTargetMFD.size();
//				for (int i=0; identical && i<nuclTargetMFD.size(); i++)
//					identical = identical && (float)nuclTargetMFD.getY(i) == (float)compNuclTargetMFD.getY(i);
//				
//				if (!identical) {
//					compNuclTargetMFD.setName("Comparison Target Nucleation");
//					
//					Color targetColor = Color.CYAN.darker();
//					
//					addFakeHistFromFunc(compNuclTargetMFD, incrFuncs, incrChars,
//							new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
//					
//					cmlFuncs.add(compNuclTargetMFD.getCumRateDistWithOffset());
//					cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
//				}
//			}
		} else {
			particMFD.setName("Participation");
			nuclMFD.setName("Nucleation");
		}
		
		EvenlyDiscretizedFunc cmlParticMFD = particMFD.getCumRateDistWithOffset();
		cmlFuncs.add(cmlParticMFD);
		cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, MAIN_COLOR));
		EvenlyDiscretizedFunc nuclCmlMFD = nuclMFD.getCumRateDistWithOffset();
		cmlFuncs.add(nuclCmlMFD);
		cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, MAIN_COLOR));
		
		Range yRange = yRange(cmlFuncs, MFD_DEFAULT_Y_RANGE, MFD_MAX_Y_RANGE, MFD_MAX_Y_RANGE_ORDERS_MAG);
		if (yRange == null)
			return new ArrayList<>();
		
		Integer commonParentID = faultSects.get(0).getParentSectionId();
		for (FaultSection sect : faultSects) {
			int parentID = sect.getParentSectionId();
			if (parentID < 0 || parentID != commonParentID) {
				commonParentID = null;
				break;
			}
		}
		
		BranchParentSectParticMFDs parentParticMFDs = commonParentID == null ? null : sol.getModule(BranchParentSectParticMFDs.class);
		UncertainArbDiscFunc cmlParticBounds = null;
		
		if (parentParticMFDs != null) {
			// plot cumulative participation fractiles
			EvenlyDiscretizedFunc[] cmlMinMedMax = parentParticMFDs.calcCumulativeSectFractiles(
					commonParentID, 0d, 0.16, 0.5d, 0.84, 1d);
			cmlParticBounds = new UncertainArbDiscFunc(
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[2], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[0], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[4], nuclCmlMFD.getMinX()));
			UncertainArbDiscFunc cml68 = new UncertainArbDiscFunc(
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[2], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[1], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[3], nuclCmlMFD.getMinX()));
			cmlParticBounds.setName("Participation p[0,16,84,100]");
			cmlFuncs.add(cmlParticBounds);
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_BOUNDS));
			cml68.setName(null);
			cmlFuncs.add(cml68);
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_68_OVERLAY));
		} else if (branchNuclMFDs != null) {
			List<Integer> sectIDs = new ArrayList<>(faultSects.size());
			for (FaultSection sect : faultSects)
				sectIDs.add(sect.getSectionId());
			EvenlyDiscretizedFunc[] cmlMinMedMax = branchNuclMFDs.calcCumulativeSectFractiles(
					sectIDs, 0d, 0.16, 0.5d, 0.84, 1d);
			UncertainArbDiscFunc cmlBounds = new UncertainArbDiscFunc(
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[2], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[0], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[4], nuclCmlMFD.getMinX()));
			UncertainArbDiscFunc cml68 = new UncertainArbDiscFunc(
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[2], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[1], nuclCmlMFD.getMinX()),
					SolMFDPlot.extendCumulativeToLowerBound(cmlMinMedMax[3], nuclCmlMFD.getMinX()));
			cmlBounds.setName("Nucleation p[0,16,84,100]");
			cmlFuncs.add(cmlBounds);
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_BOUNDS));
			cml68.setName(null);
			cmlFuncs.add(cml68);
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_68_OVERLAY));
		}
		
		List<DiscretizedFunc> availableFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> availableChars = new ArrayList<>();
		IncrementalMagFreqDist availableRups = defaultMFD.deepClone();
		IncrementalMagFreqDist usedRups = defaultMFD.deepClone();
		for (int rupIndex : rups) {
			double rate = sol.getRateForRup(rupIndex);
			double mag = rupSet.getMagForRup(rupIndex);
			int magIndex = availableRups.getClosestXIndex(mag);
			availableRups.add(magIndex, 1d);
			if (rate > 0d)
				usedRups.add(magIndex, 1d);
		}
		availableRups.setName("Available Ruptures");
		availableFuncs.add(availableRups);
		availableChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GREEN.brighter()));
		usedRups.setName("Utilized Ruptures");
		availableFuncs.add(usedRups);
		availableChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GREEN.darker()));
		PlotSpec availableSpec = new PlotSpec(availableFuncs, availableChars, null, "Magnitude", "Rupture Count");
		availableSpec.setLegendInset(RectangleAnchor.TOP_LEFT, 0.025, 0.975, 0.3, true);
		List<Range> xRanges = List.of(xRange);
		double countMax = Math.pow(10, Math.ceil(Math.max(1, Math.log10(availableRups.getMaxY()))));
		List<Range> yRanges = List.of(yRange, new Range(0.8, countMax));
		List<Boolean> xLogs = List.of(false);
		List<Boolean> yLogs = List.of(true, true);
		int[] weights = { 7, 3 };
		
		PlotSpec incrSpec = new PlotSpec(incrFuncs, incrChars, faultName, "Magnitude", "Incremental Rate (per yr)");
		PlotSpec cmlSpec = new PlotSpec(cmlFuncs, cmlChars, faultName, "Magnitude", "Cumulative Rate (per yr)");
		incrSpec.setLegendInset(true);
		cmlSpec.setLegendInset(true);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.setTickLabelFontSize(20);
		
		String prefix = "sect_mfd";
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.addLine("Incremental", "Cumulative");
		
		table.initNewLine();
		gp.drawGraphPanel(List.of(incrSpec, availableSpec), xLogs, yLogs, xRanges, yRanges);
		PlotUtils.setSubPlotWeights(gp, weights);
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, 1100, true, true, true);
		table.addColumn("![Incremental Plot]("+outputDir.getName()+"/"+prefix+".png)");
		
		prefix += "_cumulative";
		gp.drawGraphPanel(List.of(cmlSpec, availableSpec), xLogs, yLogs, xRanges, yRanges);
		PlotUtils.setSubPlotWeights(gp, weights);
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, 1100, true, true, true);
		table.addColumn("![Cumulative Plot]("+outputDir.getName()+"/"+prefix+".png)");
		table.finalizeLine();
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Magnitude-Frequency Distribution");
		lines.add(topLink); lines.add("");
		
		lines.add("Fault magnitude-frequency distributions. The left plot shows incremental rates (rates within "
				+ "each magnitude bin), and the right plot shows cumulative rates (rates at or above the given magnitude).");
		lines.add("");
		lines.add("The smaller bottom panel shows the distribution of available ruptures with a green histogram and "
				+ "_is not rate-weighted_. It only shows the raw count of ruptures of various magnitudes available in "
				+ "the rupture set.");
		lines.add("");
		
		lines.addAll(table.build());
		
		if (sol.hasModule(RupMFDsModule.class)) {
			lines.add("");
			lines.add("_NOTE: This solution has a distribution of magnitudes and rates for each ruptures, and is likely "
					+ "a branch-averaged solution. That full distribution for each rupture is used to construct the MFDs "
					+ "above, but only mean magnitudes are used in the lower panel showing available and utilized "
					+ "magnitudes, and thus there may be bins with nonzero rates in the top panel where no mgnitudes "
					+ "are shown in the bottom. The magnitude rage at the top of this page also only considers mean magnitudes._");
		}
		
		// table of cumulative participation
		table = MarkdownUtils.tableBuilder();
		
		table.initNewLine();
		table.addColumn("Magnitude");
		table.addColumn("Participation Rate");
		if (cmlParticBounds != null)
			table.addColumn("Range");
		table.addColumn("Participation RI (yrs)");
		if (cmlParticBounds != null)
			table.addColumn("Range");
		if (compCmlParticMFD != null) {
			table.addColumn("Comparison Rate");
			table.addColumn("Comparison RI (yrs)");
		}
		table.finalizeLine();
		
		int firstIndex = 0;
		double firstVal = cmlParticMFD.getY(0);
		for (int i=1; i<cmlParticMFD.size(); i++) {
			double val = cmlParticMFD.getY(i);
			if ((float)val != (float)firstVal)
				// we're above the first bin
				break;
			else
				firstIndex = i;
		}
		for (int i=firstIndex; i<cmlParticMFD.size(); i++) {
			double val = cmlParticMFD.getY(i);
			double compVal = compCmlParticMFD == null ? 0d : compCmlParticMFD.getY(i);
			if (val == 0d && compVal == 0d)
				// we're above the max mag, stop
				break;
			double mag = cmlParticMFD.getX(i);
			table.initNewLine();
			table.addColumn("__M&ge;"+optionalDigitDF.format(mag)+"__");
			table.addColumn((float)val+"");
			if (cmlParticBounds != null && (float)mag <= (float)cmlParticBounds.getMaxX()) {
				String boundsStr = "[";
				boundsStr += rangeRateStr(cmlParticBounds.getLower(), mag);
				boundsStr += ", ";
				boundsStr += rangeRateStr(cmlParticBounds.getUpper(), mag);
				boundsStr += "]";
				table.addColumn(boundsStr);
			}
			table.addColumn(riRateStr(1d/val)+"");
			if (cmlParticBounds != null && (float)mag <= (float)cmlParticBounds.getMaxX()) {
				String boundsStr = "[";
				boundsStr += riRateStr(cmlParticBounds.getUpper(), mag);
				boundsStr += ", ";
				boundsStr += riRateStr(cmlParticBounds.getLower(), mag);
				boundsStr += "]";
				table.addColumn(boundsStr);
			}
			if (compCmlParticMFD != null) {
				table.addColumn((float)compVal+"");
				table.addColumn(riRateStr(1d/compVal)+"");
			}
			table.finalizeLine();
		}
		
		lines.add("");
		lines.add("### Cumulative Rates and Recurrence Intervals Table");
		lines.add(topLink); lines.add("");
		
		lines.addAll(table.build());
		
		if (branchNuclMFDs != null) {
			// add plots showing each one
			int alpha;
			if (branchNuclMFDs.getNumBranches() > 10000)
				alpha = 20;
			else if (branchNuclMFDs.getNumBranches() > 5000)
				alpha = 40;
			else if (branchNuclMFDs.getNumBranches() > 1000)
				alpha = 60;
			else if (branchNuclMFDs.getNumBranches() > 500)
				alpha = 80;
			else if (branchNuclMFDs.getNumBranches() > 100)
				alpha = 100;
			else
				alpha = 160;
//			Color indvColor = new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(), MAIN_COLOR.getBlue(), alpha);
			Color indvColor = new Color(80, 80, 80, alpha);
			PlotCurveCharacterstics indvCurveChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, indvColor);

			Color minColor = Color.GREEN.darker();
			Color maxColor = Color.MAGENTA.darker();
			
			IncrementalMagFreqDist minMFD = null;
			double minRate = Double.POSITIVE_INFINITY;
			IncrementalMagFreqDist maxMFD = null;
			double maxRate = 0d;
			
			incrFuncs = new ArrayList<>();
			incrChars = new ArrayList<>();
			cmlFuncs = new ArrayList<>();
			cmlChars = new ArrayList<>();
			
			int branches = branchNuclMFDs.getNumBranches();
			Preconditions.checkState(branches > 0);
			
			for (int b=0; b<branches; b++) {
				SummedMagFreqDist mfd = null;
				for (FaultSection sect : faultSects) {
					IncrementalMagFreqDist sectMFD = branchNuclMFDs.getSectionMFD(b, sect.getSectionId());
					if (mfd == null)
						mfd = new SummedMagFreqDist(sectMFD.getMinX(), sectMFD.getMaxX(), sectMFD.size());
					mfd.addIncrementalMagFreqDist(sectMFD);
				}
				double totRate = mfd.calcSumOfY_Vals();
				if (totRate > maxRate) {
					maxRate = totRate;
					maxMFD = mfd;
				}
				if (totRate < minRate) {
					minRate = totRate;
					minMFD = mfd;
				}
				
				mfd.setName(null);
//				addLineGapFuncs(mfd, incrFuncs, incrChars, indvCurveChar);
				incrFuncs.add(mfd);
				incrChars.add(indvCurveChar);
				DiscretizedFunc cmlMFD = mfd.getCumRateDistWithOffset();
				if (cmlMFD.getMinX() > xRange.getLowerBound()) {
					// extend to minX
					DiscretizedFunc extended = new ArbitrarilyDiscretizedFunc();
					extended.set(xRange.getLowerBound(), cmlMFD.getY(0));
					for (Point2D pt : cmlMFD)
						extended.set(pt);
					cmlMFD = extended;
				}
				cmlFuncs.add(cmlMFD);
				cmlChars.add(indvCurveChar);
			}
			
			// make sure we're not all zero
			if (incrFuncs.size() > 0 && maxMFD != null) {
				incrFuncs.get(0).setName("Individual Branches");
				cmlFuncs.get(0).setName("Individual Branches");
				
				// add min/max
				PlotCurveCharacterstics minChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, minColor);
				// so that we can change the name
				minMFD = minMFD.deepClone();
				minMFD.setName("Min Rate MFD");
//				addLineGapFuncs(minMFD, incrFuncs, incrChars, minChar);
				incrFuncs.add(minMFD);
				incrChars.add(minChar);
				
				PlotCurveCharacterstics maxChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, maxColor);
				// so that we can change the name
				maxMFD = maxMFD.deepClone();
				maxMFD.setName("Max Rate MFD");
//				addLineGapFuncs(maxMFD, incrFuncs, incrChars, maxChar);
				incrFuncs.add(maxMFD);
				incrChars.add(maxChar);
				
				DiscretizedFunc cmlMinMFD = minMFD.getCumRateDistWithOffset();
				DiscretizedFunc cmlMaxMFD = maxMFD.getCumRateDistWithOffset();
				if (cmlMinMFD.getMinX() > xRange.getLowerBound()) {
					// extend to minX
					DiscretizedFunc extended = new ArbitrarilyDiscretizedFunc();
					extended.set(xRange.getLowerBound(), cmlMinMFD.getY(0));
					for (Point2D pt : cmlMinMFD)
						extended.set(pt);
					cmlMinMFD = extended;
				}
				if (cmlMaxMFD.getMinX() > xRange.getLowerBound()) {
					// extend to minX
					DiscretizedFunc extended = new ArbitrarilyDiscretizedFunc();
					extended.set(xRange.getLowerBound(), cmlMaxMFD.getY(0));
					for (Point2D pt : cmlMaxMFD)
						extended.set(pt);
					cmlMaxMFD = extended;
				}
				cmlMinMFD.setName(minMFD.getName());
				cmlMaxMFD.setName(maxMFD.getName());
				cmlFuncs.add(cmlMinMFD);
				cmlChars.add(minChar);
				cmlFuncs.add(cmlMaxMFD);
				cmlChars.add(maxChar);
				
				List<Integer> sectIDs = new ArrayList<>(faultSects.size());
				for (FaultSection sect : faultSects)
					sectIDs.add(sect.getSectionId());
				IncrementalMagFreqDist medianMFD = branchNuclMFDs.calcIncrementalSectFractiles(sectIDs, 0.5d)[0];
				DiscretizedFunc medianCmlMFD = branchNuclMFDs.calcCumulativeSectFractiles(sectIDs, 0.5d)[0];
				if (medianCmlMFD.getMinX() > xRange.getLowerBound()) {
					// extend to minX
					DiscretizedFunc extended = new ArbitrarilyDiscretizedFunc();
					extended.set(xRange.getLowerBound(), medianCmlMFD.getY(0));
					for (Point2D pt : medianCmlMFD)
						extended.set(pt);
					medianCmlMFD = extended;
				}
				
				if (compNuclMFD != null) {
					// add comparison
					PlotCurveCharacterstics compChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR);
					compNuclMFD.setName("Comparison Mean");
//					addLineGapFuncs(compNuclMFD, incrFuncs, incrChars, compChar);
					incrFuncs.add(compNuclMFD);
					incrChars.add(compChar);
					
					EvenlyDiscretizedFunc compCmlMFD = compNuclMFD.getCumRateDistWithOffset();
					compCmlMFD.setName("Comparison Mean");
					cmlFuncs.add(compCmlMFD);
					cmlChars.add(compChar);
					
					nuclMFD.setName("Primary Mean");
					medianMFD.setName("Primary Median");
				} else {
					nuclMFD.setName("Mean");
					medianMFD.setName("Median");
				}
				medianCmlMFD.setName(medianMFD.getName());
				
				PlotCurveCharacterstics medianChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, MAIN_COLOR);
				incrFuncs.add(medianMFD);
				incrChars.add(medianChar);
				
				cmlFuncs.add(medianCmlMFD);
				cmlChars.add(medianChar);
				
				// add mean
				PlotCurveCharacterstics meanChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, MAIN_COLOR);
//				addLineGapFuncs(nuclMFD, incrFuncs, incrChars, primaryChar);
				incrFuncs.add(nuclMFD);
				incrChars.add(meanChar);
				
				nuclCmlMFD.setName(nuclMFD.getName());
				cmlFuncs.add(nuclCmlMFD);
				cmlChars.add(meanChar);
				
				incrSpec = new PlotSpec(incrFuncs, incrChars, faultName, "Magnitude", "Incremental Nucleation Rate (per yr)");
				cmlSpec = new PlotSpec(cmlFuncs, cmlChars, faultName, "Magnitude", "Cumulative Nucleation Rate (per yr)");
				incrSpec.setLegendInset(true);
				cmlSpec.setLegendInset(true);
				
				prefix = "sect_mfd_dist";
				
				table = MarkdownUtils.tableBuilder();
				table.addLine("Incremental", "Cumulative");
				
				table.initNewLine();
				gp.drawGraphPanel(incrSpec, false, true, xRange, yRange);
				PlotUtils.writePlots(outputDir, prefix, gp, 1000, 800, true, false, false);
				table.addColumn("![Incremental Plot]("+outputDir.getName()+"/"+prefix+".png)");
				
				prefix += "_cumulative";
				gp.drawGraphPanel(cmlSpec, false, true, xRange, yRange);
				PlotUtils.writePlots(outputDir, prefix, gp, 1000, 800, true, false, false);
				table.addColumn("![Cumulative Plot]("+outputDir.getName()+"/"+prefix+".png)");
				table.finalizeLine();
				
				lines.add("");
				lines.add("### Individual Branch Nucleation MFDs");
				lines.add(topLink); lines.add("");
				
				lines.add("Individual nucleation MFDs across "+branches+" logic tree branches. The individual branches "
						+ "with the highest and lowest total rate are highlighted, as are the mean and median models.");
				lines.add("");
				
				lines.addAll(table.build());
			}
		}
		
		return lines;
	}
	
	private static String rangeRateStr(DiscretizedFunc func, double mag) {
		if ((float)mag == (float)func.getMinX())
			return rangeRateStr(func.getY(0));
		if ((float)mag == (float)func.getMaxX())
			return rangeRateStr(func.getY(func.size()-1));
		if ((float)mag > (float)func.getMaxX() || (float)mag < (float)func.getMinX())
			return "_N/A_";
		return rangeRateStr(func.getInterpolatedY(mag));
	}
	
	private static String rangeRateStr(double rate) {
		if (rate < 1e-1)
			return expProbDF.format(rate);
		return (float)rate+"";
	}
	
	private static String riRateStr(DiscretizedFunc func, double mag) {
		if ((float)mag == (float)func.getMinX())
			return riRateStr(1d/func.getY(0));
		if ((float)mag == (float)func.getMaxX())
			return riRateStr(1d/func.getY(func.size()-1));
		if ((float)mag > (float)func.getMaxX() || (float)mag < (float)func.getMinX())
			return "_N/A_";
		return riRateStr(1d/func.getInterpolatedY(mag));
	}
	
	private static String riRateStr(double ri) {
		if (ri > 1d)
			return riDF.format(ri);
		return (float)ri+"";
	}

	public static SummedMagFreqDist calcTargetMFD(List<FaultSection> faultSects, InversionTargetMFDs targetMFDs) {
		List<? extends IncrementalMagFreqDist> sectNuclMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
		SummedMagFreqDist nuclTargetMFD = null;
		if (sectNuclMFDs != null) {
			for (FaultSection sect : faultSects) {
				IncrementalMagFreqDist sectTarget = sectNuclMFDs.get(sect.getSectionId());
				if (sectTarget == null) {
					nuclTargetMFD = null;
					break;
				}
				if (nuclTargetMFD == null)
					// first one
					nuclTargetMFD = new SummedMagFreqDist(sectTarget.getMinX(), sectTarget.size(), sectTarget.getDelta());
				nuclTargetMFD.addIncrementalMagFreqDist(sectTarget);
			}
		}
		return nuclTargetMFD;
	}
	
	private static void addFakeHistFromFunc(EvenlyDiscretizedFunc mfd, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, PlotCurveCharacterstics pChar) {
		boolean first = true;
		double plusMinus = mfd.getDelta()*0.4;
		for (int i=0; i<mfd.size(); i++) {
			double x = mfd.getX(i);
			double y = mfd.getY(i);
			if (y > 0) {
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				if (first)
					xy.setName(mfd.getName());
				first = false;
				double lowX = x - plusMinus;
				double highX = x + plusMinus;
				xy.set(lowX, y);
				xy.set(highX, y);
				funcs.add(xy);
				chars.add(pChar);
			}
		}
	}
	
	private static void addLineGapFuncs(EvenlyDiscretizedFunc mfd, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, PlotCurveCharacterstics pChar) {
		boolean first = true;
		double plusMinus = mfd.getDelta()*0.25;
		DefaultXY_DataSet continuation = null;
		for (int i=0; i<mfd.size(); i++) {
			double x = mfd.getX(i);
			double y = mfd.getY(i);
			if (y > 0) {
				boolean hasBefore = i > 0 && mfd.getY(i-1) > 0;
				boolean hasAfter = i < mfd.size()-1 && mfd.getY(i+1) > 0;
				boolean neighbors = hasBefore || hasAfter;
				if (neighbors) {
					if (hasBefore) {
						Preconditions.checkState(!first);
						Preconditions.checkNotNull(continuation);
						
						continuation.set(x, y);
					} else {
						// first in a set of multi
						DefaultXY_DataSet xy = new DefaultXY_DataSet();
						if (first)
							xy.setName(mfd.getName());
						first = false;
						xy.set(x, y);
						funcs.add(xy);
						chars.add(pChar);
						
						continuation = xy;
					}
				} else {
					// fully isolated
					DefaultXY_DataSet xy = new DefaultXY_DataSet();
					if (first)
						xy.setName(mfd.getName());
					first = false;
					double lowX = x - plusMinus;
					double highX = x + plusMinus;
					xy.set(lowX, y);
					xy.set(highX, y);
					funcs.add(xy);
					chars.add(pChar);
				}
			}
		}
	}
	
	private static Range MFD_DEFAULT_Y_RANGE = new Range(1e-10, 1e-2);
	private static Range MFD_MAX_Y_RANGE = new Range(1e-10, 1e1);
	private static double MFD_MAX_Y_RANGE_ORDERS_MAG = 8d;
	
	private static Range yRange(List<? extends XY_DataSet> funcs, Range defaultRange, Range maxRange, double maxOrdersMag) {
		double minNonZero = defaultRange.getLowerBound();
		double max = defaultRange.getUpperBound();
		int numNonZero = 0;
		for (XY_DataSet func : funcs) {
			XY_DataSet[] subFuncs;
			if (func instanceof UncertainBoundedDiscretizedFunc) {
				UncertainBoundedDiscretizedFunc bounded = (UncertainBoundedDiscretizedFunc)func;
				subFuncs = new XY_DataSet[] { bounded.getLower(), bounded.getUpper() };
			} else {
				subFuncs = new XY_DataSet[] { func };
			}
			for (XY_DataSet subFunc : subFuncs) {
				for (Point2D pt : subFunc) {
					if (pt.getY() > maxRange.getLowerBound()) {
						minNonZero = Double.min(minNonZero, pt.getY()*0.95);
						numNonZero++;
					}
					max = Double.max(max, pt.getY()*1.05);
				}
			}
		}
		if (numNonZero == 0)
			return null;
		Preconditions.checkState(Double.isFinite(minNonZero));
		minNonZero = Math.pow(10, Math.floor(Math.log10(minNonZero)));
		max = Math.pow(10, Math.ceil(Math.log10(max)));
		if (maxRange != null) {
			minNonZero = Math.max(minNonZero, maxRange.getLowerBound());
			max = Math.min(max, maxRange.getUpperBound());
		}
		double maxLog = Math.log10(max);
		double minLog = Math.log10(minNonZero);
		if (maxLog - minLog > maxOrdersMag) {
			minLog = maxLog - maxOrdersMag;
			minNonZero = Math.pow(10, minLog);
		}
		return new Range(minNonZero, max);
	}

	static List<String> getAlongStrikeLines(ReportMetadata meta, String faultName,
			List<FaultSection> faultSects, File outputDir, String topLink) throws IOException {
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		MinMaxAveTracker latRange = new MinMaxAveTracker();
		MinMaxAveTracker lonRange = new MinMaxAveTracker();
		
		for (FaultSection sect : faultSects) {
			for (Location loc : sect.getFaultTrace()) {
				latRange.addValue(loc.lat);
				lonRange.addValue(loc.lon);
			}
		}
		
		// strongly prefer latitude here
		boolean latX = latRange.getLength() > 0.6*lonRange.getLength() || faultName.contains("San Andreas");
		String xLabel;
		Range xRange;
		if (latX) {
			xLabel = "Latitude (degrees)";
			xRange = new Range(latRange.getMin(), latRange.getMax());
		} else {
			xLabel = "Longitude (degrees)";
			xRange = new Range(lonRange.getMin(), lonRange.getMax());
		}
		
		List<XY_DataSet> emptySectFuncs = new ArrayList<>();
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = 0d;
		for (FaultSection sect : faultSects) {
			XY_DataSet func = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace()) {
				if (latX)
					func.set(loc.getLatitude(), 0d);
				else
					func.set(loc.getLongitude(), 0d);
			}
			emptySectFuncs.add(func);
			minMag = Math.min(minMag, rupSet.getMinMagForSection(sect.getSectionId()));
			maxMag = Math.max(maxMag, rupSet.getMaxMagForSection(sect.getSectionId()));
		}
		
        double legendRelX = 0.025;
		
		Map<Integer, List<FaultSection>> parentsMap = faultSects.stream().collect(Collectors.groupingBy(s->s.getParentSectionId()));

		double[] targetSectRates = null;
		double[] targetSectRateStdDevs = null;
		boolean targetNucleation = false;
		InversionTargetMFDs targetMFDs = meta.primary.rupSet.getModule(InversionTargetMFDs.class);
		if (targetMFDs != null && targetMFDs.getOnFaultSupraSeisNucleationMFDs() != null) {
			targetNucleation = true;
			targetSectRates = new double[rupSet.getNumSections()];
			targetSectRateStdDevs = new double[rupSet.getNumSections()];
			// only need to fill in the ones we'll use
			List<? extends IncrementalMagFreqDist> targets = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
			for (FaultSection sect : faultSects) {
				int sectID = sect.getSectionId();
				IncrementalMagFreqDist target = targets.get(sectID);
				targetSectRates[sectID] = target.calcSumOfY_Vals();
				if (targetSectRateStdDevs != null) {
					if (target instanceof UncertainIncrMagFreqDist) {
						UncertainIncrMagFreqDist uncertTarget = (UncertainIncrMagFreqDist)target;
						UncertainBoundedIncrMagFreqDist oneSigmaBoundedMFD =
								uncertTarget.estimateBounds(UncertaintyBoundType.ONE_SIGMA);
						double upperVal = oneSigmaBoundedMFD.getUpper().calcSumOfY_Vals();
						double lowerVal = oneSigmaBoundedMFD.getLower().calcSumOfY_Vals();
						targetSectRateStdDevs[sectID] = UncertaintyBoundType.ONE_SIGMA.estimateStdDev(
								lowerVal, upperVal);
					} else {
						targetSectRateStdDevs = null;
					}
				}
			}
		} else {
			// we didn't use to store them explicitly, fall back to inversion configuration
			InversionConfiguration invConfig = null;
			if (meta.hasPrimarySol())
				invConfig = meta.primary.sol.getModule(InversionConfiguration.class);
			if (invConfig != null && invConfig.getConstraints() != null) {
				// see if we constrained this with target section rates
				for (InversionConstraint constraint : invConfig.getConstraints()) {
					if (constraint instanceof SectionTotalRateConstraint) {
						SectionTotalRateConstraint sectConstr = (SectionTotalRateConstraint)constraint;
						targetSectRates = sectConstr.getSectRates();
						targetSectRateStdDevs = sectConstr.getSectRateStdDevs();
						targetNucleation = sectConstr.isNucleation();
						break;
					}
				}
			}
		}
		
		List<AlongStrikePlot> plots = new ArrayList<>();
		
		// mag plot
		if (meta.hasPrimarySol() || (!targetNucleation && targetSectRates != null))
			plots.add(buildParticRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX,
					!targetNucleation ? targetSectRates : null, !targetNucleation ? targetSectRateStdDevs : null));
		
		List<SectMappedUncertainDataConstraint> paleoConstraints = null;
		List<SectMappedUncertainDataConstraint> paleoSlipConstraints = null;
		
		PaleoseismicConstraintData paleoData = rupSet.getModule(PaleoseismicConstraintData.class);
		if (paleoData != null) {
			HashSet<Integer> sectIndexes = new HashSet<>();
			for (FaultSection sect : faultSects)
				sectIndexes.add(sect.getSectionId());
			
			if (paleoData.hasPaleoRateConstraints()) {
				paleoConstraints = new ArrayList<>();
				for (SectMappedUncertainDataConstraint constr : paleoData.getPaleoRateConstraints())
					if (sectIndexes.contains(constr.sectionIndex))
						paleoConstraints.add(constr);
				if (paleoConstraints.isEmpty())
					paleoConstraints = null;
			}
			
			if (paleoData.hasPaleoSlipConstraints()) {
				paleoSlipConstraints = new ArrayList<>();
				for (SectMappedUncertainDataConstraint constr : paleoData.inferRatesFromSlipConstraints(true))
					if (sectIndexes.contains(constr.sectionIndex))
						paleoSlipConstraints.add(constr);
				if (paleoSlipConstraints.isEmpty())
					paleoSlipConstraints = null;
			}
		}
		
		if (meta.hasPrimarySol() && (paleoConstraints != null || paleoSlipConstraints != null))
			plots.add(buildPaleoRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX, xRange, latX,
					paleoData, paleoConstraints, paleoSlipConstraints));
		
		// add nucleation rate plot
		if (meta.hasPrimarySol() || (targetNucleation && targetSectRates != null))
			plots.add(buildNuclRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX,
					targetNucleation ? targetSectRates : null, targetNucleation ? targetSectRateStdDevs : null));
		
		if (rupSet.hasModule(SlipAlongRuptureModel.class) && rupSet.hasModule(AveSlipModule.class))
			plots.add(buildSlipRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX, true));
		
		boolean doReductions = false;
		for (FaultSection sect : faultSects) {
			doReductions = doReductions || sect.getCouplingCoeff() != 1d;
			// common defaults
			doReductions = doReductions || (sect.getAseismicSlipFactor() != 0d && sect.getAseismicSlipFactor() != 0.1d);
		}
		if (doReductions)
			plots.add(buildSlipRateReductionPlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX));
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Along-Strike Values");
		lines.add(topLink); lines.add("");

		String prefix = "sect_along_strike";
		writeAlongStrikePlots(outputDir, prefix, plots, parentsMap, latX, xLabel, xRange, faultName);

		lines.add("![Along-strike plot]("+outputDir.getName()+"/"+prefix+".png)");
		
		if (meta.primary.rupSet.hasModule(AveSlipModule.class) && meta.hasPrimarySol()) {
			lines.add("### Moment-Rates and b-Values");
			lines.add(topLink); lines.add("");
			
			plots = new ArrayList<>();

			plots.add(buildMoRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX));
			plots.add(buildBValPlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX));
			
			prefix = "sect_along_strike_mo_b";
			writeAlongStrikePlots(outputDir, prefix, plots, parentsMap, latX, xLabel, xRange, faultName);

			lines.add("![Along-strike plot]("+outputDir.getName()+"/"+prefix+".png)");
		}
		
		return lines;
	}
	
	static void writeAlongStrikePlots(File outputDir, String prefix, List<AlongStrikePlot> plots,
			Map<Integer, List<FaultSection>> parentsMap, boolean latX, String xLabel, Range xRange, String faultName)
					throws IOException {
		List<PlotSpec> specs = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		List<List<XY_DataSet>> funcLists = new ArrayList<>();
		List<List<PlotCurveCharacterstics>> charLists = new ArrayList<>();
		List<Boolean> yLogs = new ArrayList<>();
		
		for (AlongStrikePlot plot : plots) {
			specs.add(plot.spec);
			yRanges.add(plot.yRange);
			funcLists.add(plot.funcs);
			charLists.add(plot.chars);
			yLogs.add(plot.yLog);
		}
		
		List<Integer> specWeights = null;
		int nameInerstIndex = -1;
		if (parentsMap.size() > 1) {
			// add section boundary labels
			HashSet<Float> boundaryLocs = new HashSet<>();
			Map<Double, String> boundaryMiddles = new HashMap<>();
			
			for (List<FaultSection> parentSects : parentsMap.values()) {
				double minVal = Double.POSITIVE_INFINITY;
				double maxVal = Double.NEGATIVE_INFINITY;
				String name = null;
				for (FaultSection sect : parentSects) {
					if (name == null)
						name = sect.getParentSectionName();
					FaultTrace trace = sect.getFaultTrace();
					double x1, x2;
					if (latX) {
						x1 = trace.first().lat;
						x2 = trace.last().lat;
					} else {
						x1 = trace.first().lon;
						x2 = trace.last().lon;
					}
					minVal = Double.min(minVal, x1);
					minVal = Double.min(minVal, x2);
					maxVal = Double.max(maxVal, x1);
					maxVal = Double.max(maxVal, x2);
				}
				boundaryLocs.add((float)minVal);
				boundaryLocs.add((float)maxVal);
				if (name != null)
					boundaryMiddles.put(0.5*(maxVal+minVal), name);
			}
			
			if (!boundaryMiddles.isEmpty()) {
				// add names in the middle
				nameInerstIndex = specs.size()/2;
				
				int mainWeight = 10;
				int namesWeight = 6;
				
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				PlotSpec nameSpec = new PlotSpec(funcs, chars, " ", xLabel, "Section Names");
				
				Range yRange = new Range(0d, 1d);
				double y = 0.5;
				double angle = -0.5*Math.PI;
				TextAnchor rotAnchor = TextAnchor.CENTER;
				TextAnchor textAnchor = TextAnchor.CENTER;
				for (Double middle : boundaryMiddles.keySet()) {
					String label = boundaryMiddles.get(middle);
					
					label = NamedFaults.stripFaultNameFromSect(faultName, label);
					
					XYTextAnnotation ann = new XYTextAnnotation(label+" ", middle, y);
					int fontSize;
					if (label.length() > 30)
						fontSize = 14;
					else if (label.length() > 20)
						fontSize = 18;
					else if (label.length() > 10)
						fontSize = 20;
					else
						fontSize = 22;
					Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
					ann.setFont(annFont);
					ann.setTextAnchor(textAnchor);
					ann.setRotationAnchor(rotAnchor);
					ann.setRotationAngle(angle);
					nameSpec.addPlotAnnotation(ann);
				}
				
				specWeights = new ArrayList<>();
				while (specWeights.size() < specs.size()) {
					if (specWeights.size() == nameInerstIndex) {
						specs.add(nameInerstIndex, nameSpec);
						yRanges.add(nameInerstIndex, yRange);
						funcLists.add(nameInerstIndex, funcs);
						charLists.add(nameInerstIndex, chars);
						yLogs.add(nameInerstIndex, false);
						specWeights.add(namesWeight);
					} else {
						specWeights.add(mainWeight);
					}
				}
			}
			
			PlotCurveCharacterstics boundaryChar = new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY);
			for (int s=0; s<specs.size(); s++) {
				Range yRange = yRanges.get(s);
				if (yRange == null)
					yRange = new Range(0d, 1d);
				List<XY_DataSet> funcs = funcLists.get(s);
				List<PlotCurveCharacterstics> chars = charLists.get(s);
				
				for (Float boundary : boundaryLocs) {
					DefaultXY_DataSet xy = new DefaultXY_DataSet();
					xy.set(boundary.doubleValue(), yRange.getLowerBound());
					xy.set(boundary.doubleValue(), yRange.getUpperBound());
					funcs.add(xy);
					chars.add(boundaryChar);
				}
			}
		}
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.setTickLabelFontSize(20);
		
		List<Boolean> xLogs = List.of(false);
		List<Range> xRanges = List.of(xRange);
		
		gp.setxAxisInverted(latX);
		gp.drawGraphPanel(specs, xLogs, yLogs, xRanges, yRanges);
		
		if (specWeights != null)
			PlotUtils.setSubPlotWeights(gp, Ints.toArray(specWeights));
		
		if (nameInerstIndex > 0) {
			XYPlot subPlot = PlotUtils.getSubPlots(gp).get(nameInerstIndex);
			subPlot.setDomainGridlinesVisible(false);
			subPlot.setRangeGridlinesVisible(false);
			subPlot.getRangeAxis().setTickLabelsVisible(false);
		}

		int height = 300 + 500*specs.size();
		if (specWeights != null)
			height -= 200;
		int width = parentsMap.size() == 1 ? 1000 : 1400;
		PlotUtils.writePlots(outputDir, prefix, gp, width, height, true, true, false);
	}
	
	static class AlongStrikePlot {
		
		private PlotSpec spec;
		private List<XY_DataSet> funcs;
		private List<PlotCurveCharacterstics> chars;
		private Range yRange;
		private boolean yLog;
		
		public AlongStrikePlot(PlotSpec spec, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, Range yRange,
				boolean yLog) {
			super();
			this.spec = spec;
			this.funcs = funcs;
			this.chars = chars;
			this.yRange = yRange;
			this.yLog = yLog;
		}
	}
	
	private static final Color TARGET_COLOR = Color.CYAN.darker();
	private static final int boundsAlpha = 60;
	private static final Color targetBoundsColor = new Color(TARGET_COLOR.getRed(), TARGET_COLOR.getGreen(),
			TARGET_COLOR.getBlue(), boundsAlpha);

	private static final Color PRIMARY_BOUNDS = new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(),
			MAIN_COLOR.getBlue(), 30);
	private static final Color PRIMARY_68_OVERLAY = new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(),
			MAIN_COLOR.getBlue(), 30);
	private static final Color PRIMARY_68_STANDALONE = new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(),
			MAIN_COLOR.getBlue(), 60);

	

	private static final float primaryThickness = 3f;
	private static final float compThickness = 2f;
	
	private static AlongStrikePlot buildParticRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX,
			double[] targetRates, double[] targetRateStdDevs) {
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = 0d;
		for (FaultSection sect : faultSects) {
			minMag = Math.min(minMag, rupSet.getMinMagForSection(sect.getSectionId()));
			maxMag = Math.max(maxMag, rupSet.getMaxMagForSection(sect.getSectionId()));
		}
		// build event rate plot
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		List<PlotLineType> magLines = new ArrayList<>();
		List<Double> minMags = new ArrayList<>();
		minMags.add(0d);
		magLines.add(PlotLineType.SOLID);

		if (maxMag > 9) {
			if (minMag < 7 && maxMag > 7) {
				minMags.add(7d);
				magLines.add(PlotLineType.DASHED);
			}
			if (minMag < 8 && maxMag > 8) {
				minMags.add(8d);
				magLines.add(PlotLineType.DOTTED);
			}
			if (minMag < 9 && maxMag > 9) {
				minMags.add(9d);
				magLines.add(PlotLineType.DOTTED_AND_DASHED);
			}
		} else {
			if (minMag < 6 && maxMag > 6) {
				minMags.add(6d);
				magLines.add(PlotLineType.DASHED);
			}
			if (minMag < 7 && maxMag > 7) {
				minMags.add(7d);
				magLines.add(PlotLineType.DOTTED);
			}
			if (minMag < 8 && maxMag > 8) {
				minMags.add(8d);
				magLines.add(PlotLineType.DOTTED_AND_DASHED);
			}
		}
		List<String> magLabels = new ArrayList<>();
		for (double myMinMag : minMags) {
			if (myMinMag > 0d)
				magLabels.add("M≥"+optionalDigitDF.format(myMinMag));
			else
				magLabels.add("Supra-Seis");
		}
		
		BranchSectParticMFDs dists = meta.hasPrimarySol() ? meta.primary.sol.getModule(BranchSectParticMFDs.class) : null;

		boolean comp = meta.hasComparisonSol() && meta.comparisonHasSameSects;
		boolean first = true;
		for (int s=0; s<faultSects.size(); s++) {
			FaultSection sect = faultSects.get(s);
			XY_DataSet emptyFunc = emptySectFuncs.get(s);

			if (targetRates != null) {
				// we have target participation rates
				XY_DataSet rateFunc = copyAtY(emptyFunc, targetRates[sect.getSectionId()]);

				if (first)
					rateFunc.setName("Target Supra-Seis");

				funcs.add(rateFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, compThickness, TARGET_COLOR));
				
				if (targetRateStdDevs != null && targetRateStdDevs[sect.getSectionId()] > 0) {
					UncertainArbDiscFunc uncertFunc = uncertCopyAtY(emptyFunc, targetRates[sect.getSectionId()],
							targetRateStdDevs[sect.getSectionId()]);

					if (first)
						uncertFunc.setName("± σ");
					
					funcs.add(uncertFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, targetBoundsColor));
				}
			}

			if (comp) {
				for (int m=0; m<minMags.size(); m++) {
					double myMinMag = minMags.get(m);

					double rate = rateAbove(myMinMag, sect.getSectionId(), meta.comparison.sol);
					XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
					if (first) {
						if (comp && m == 0)
							rateFunc.setName("Comparison "+magLabels.get(m));
						else
							rateFunc.setName(magLabels.get(m));
					}

					PlotLineType line = magLines.get(m);

					funcs.add(rateFunc);
					chars.add(new PlotCurveCharacterstics(line, compThickness, COMP_COLOR));
				}
			}

			if (meta.hasPrimarySol()) {
				for (int m=0; m<minMags.size(); m++) {
					double myMinMag = minMags.get(m);

					double rate = rateAbove(myMinMag, sect.getSectionId(), meta.primary.sol);
					XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
					if (first) {
						if (comp && m == 0)
							rateFunc.setName("Primary "+magLabels.get(m));
						else
							rateFunc.setName(magLabels.get(m));
					}

					PlotLineType line = magLines.get(m);

					funcs.add(rateFunc);
					chars.add(new PlotCurveCharacterstics(line, primaryThickness, MAIN_COLOR));
					
					if (m == 0 && dists != null) {
						// we have a solution distribution
						EvenlyDiscretizedFunc[] fractiles = dists.calcCumulativeSectFractiles(
								sect.getSectionId(), 0d, 0.16, 0.5, 0.84, 1d);
						double min = fractiles[0].getY(0);
						double p16 = fractiles[1].getY(0);
						double p50 = fractiles[2].getY(0);
						double p84 = fractiles[3].getY(0);
						double max = fractiles[4].getY(0);
						
						UncertainArbDiscFunc minMaxFunc = uncertCopyAtY(emptyFunc, p50, min, max);

						if (first)
							minMaxFunc.setName("p[0,16,84,100]");
						
						funcs.add(minMaxFunc);
						chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_BOUNDS));
						
						UncertainArbDiscFunc sixyEightFunc = uncertCopyAtY(emptyFunc, p50, p16, p84);
						
						funcs.add(sixyEightFunc);
						chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_68_OVERLAY));
					}
				}
			}

			first = false;
		}
		PlotSpec magRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Participation Rate (per year)");
		// legend at bottom
		
		magRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(magRateSpec, funcs, chars, yRange(funcs, new Range(1e-4, 1e-3), new Range(1e-8, 1e1), 5), true);
	}
	
	private static AlongStrikePlot buildPaleoRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX, Range xRange, boolean latX,
			PaleoseismicConstraintData paleoData, List<SectMappedUncertainDataConstraint> paleoConstraints,
			List<SectMappedUncertainDataConstraint> paleoSlipConstraints) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		PaleoProbabilityModel paleoProb = paleoConstraints != null ? paleoData.getPaleoProbModel() : null;
		PaleoSlipProbabilityModel paleoSlipProb = null;
		if (paleoSlipConstraints != null && rupSet.hasModule(AveSlipModule.class)
				&& rupSet.hasModule(SlipAlongRuptureModel.class))
			paleoSlipProb = paleoData.getPaleoSlipProbModel();
		
		List<XY_DataSet> dataFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> dataChars = new ArrayList<>();
		
		double halfWhisker = 0.005*xRange.getLength();
		for (boolean slip : new boolean[] { true, false}) {
			List<? extends SectMappedUncertainDataConstraint> constraints;
			Color constrColor;
			if (slip) {
				constrColor = Color.GREEN.darker();
				constraints = paleoSlipConstraints;
			} else {
				constrColor = Color.BLACK;
				constraints = paleoConstraints; 
			}
			Color whiskerColor = new Color(constrColor.getRed(), constrColor.getGreen(), constrColor.getBlue(), 127);
			
			if (constraints != null) {
				DefaultXY_DataSet dataXY = new DefaultXY_DataSet();
				for (SectMappedUncertainDataConstraint constraint : constraints) {
					double x = latX ? constraint.dataLocation.getLatitude() : constraint.dataLocation.getLongitude();
					dataXY.set(x, constraint.bestEstimate);
					
					BoundedUncertainty range95 = constraint.estimateUncertaintyBounds(UncertaintyBoundType.CONF_95);
					
					dataFuncs.add(line(x-halfWhisker, range95.upperBound, x+halfWhisker, range95.upperBound));
					dataChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, whiskerColor));
					
					dataFuncs.add(line(x, range95.lowerBound, x, range95.upperBound));
					dataChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, whiskerColor));
					
					dataFuncs.add(line(x-halfWhisker, range95.lowerBound, x+halfWhisker, range95.lowerBound));
					dataChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, whiskerColor));
				}
				if (slip)
					dataXY.setName("Slip Data");
				else
					dataXY.setName("Rate Data");
				dataFuncs.add(dataXY);
				dataChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 5f, constrColor));
			}
		}
		boolean comp = meta.hasComparisonSol() && meta.comparisonHasSameSects;
		double[] slipRates = null, compSlipRates = null;
		if (paleoSlipProb != null) {
			slipRates = calcPaleoRates(faultSects, meta.primary.sol, paleoSlipProb);
			if (comp && meta.comparison.rupSet.hasModule(AveSlipModule.class)
					&& meta.comparison.rupSet.hasModule(SlipAlongRuptureModel.class))
				compSlipRates = calcPaleoRates(faultSects, meta.comparison.sol, paleoSlipProb);
		}
		for (int s=0; s<faultSects.size(); s++) {
			int sectIndex = faultSects.get(s).getSectionId();
			for (boolean isComp : new boolean[] {true, false}) {
				if (isComp && !comp)
					continue;
				
				for (boolean slip : new boolean[] { true, false}) {
					if (isComp && slip && compSlipRates == null)
						continue;
					
					PlotLineType lineType;
					String funcLabel;
					if (slip) {
						if (paleoSlipProb == null)
							continue;
						lineType = PlotLineType.DOTTED;
						funcLabel = "Slip-Rate-Observable";
					} else {
						if (paleoProb == null)
							continue;
						lineType = PlotLineType.SOLID;
						funcLabel = "Rate-Observable";
					}
					XY_DataSet emptyFunc = emptySectFuncs.get(s);
					
					double rate;
					String label = null;
					PlotCurveCharacterstics pChar;
					
					if (isComp) {
						if (slip)
							rate = compSlipRates[s];
						else
							rate = paleoRate(sectIndex, meta.comparison.sol, paleoProb);
						label = "Comparison";
						pChar = new PlotCurveCharacterstics(lineType, compThickness, COMP_COLOR);
					} else {
						if (slip)
							rate = slipRates[s];
						else
							rate = paleoRate(sectIndex, meta.primary.sol, paleoProb);
						if (comp)
							label = "Primary "+funcLabel;
						else
							label = funcLabel;
						pChar = new PlotCurveCharacterstics(lineType, primaryThickness, MAIN_COLOR);
					}
					
					XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
					if (s == 0)
						rateFunc.setName(label);
					
					funcs.add(rateFunc);
					chars.add(pChar);
				}
			}
		}
		
		funcs.addAll(0, dataFuncs);
		chars.addAll(0, dataChars);
		
		PlotSpec paleoSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Paleo-Visible Participation Rate (per year)");
		// legend at bottom
		paleoSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(paleoSpec, funcs, chars, yRange(funcs, new Range(1e-4, 1e-3), new Range(1e-8, 1e1), 5), true);
	}
	
	private static AlongStrikePlot buildNuclRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX,
			double[] targetRates, double[] targetRateStdDevs) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		boolean comp = meta.hasComparisonSol() && meta.comparisonHasSameSects;
		
		BranchSectNuclMFDs dists = meta.hasPrimarySol() ? meta.primary.sol.getModule(BranchSectNuclMFDs.class) : null;
		
		boolean first = true;
		for (int s=0; s<faultSects.size(); s++) {
			FaultSection sect = faultSects.get(s);
			XY_DataSet emptyFunc = emptySectFuncs.get(s);

			if (targetRates != null) {
				// we have target nucleation rates
				XY_DataSet rateFunc = copyAtY(emptyFunc, targetRates[sect.getSectionId()]);

				if (first)
					rateFunc.setName("Target Supra-Seis Nucleation");

				funcs.add(rateFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, compThickness, TARGET_COLOR));
				
				if (targetRateStdDevs != null && targetRateStdDevs[sect.getSectionId()] > 0) {
					UncertainArbDiscFunc uncertFunc = uncertCopyAtY(emptyFunc, targetRates[sect.getSectionId()],
							targetRateStdDevs[sect.getSectionId()]);

					if (first)
						uncertFunc.setName("+/- σ");
					
					funcs.add(uncertFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, targetBoundsColor));
				}
			}

			if (comp) {
				double rate = nuclRateAbove(0d, sect.getSectionId(), meta.comparison.sol);
				XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
				if (first)
					rateFunc.setName("Comparison");

				funcs.add(rateFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, compThickness, COMP_COLOR));
			}

			if (meta.hasPrimarySol()) {
				double rate = nuclRateAbove(0d, sect.getSectionId(), meta.primary.sol);
				XY_DataSet rateFunc = copyAtY(emptyFunc, rate);
				if (first) {
					if (comp)
						rateFunc.setName("Primary Nucleation");
					else
						rateFunc.setName("Solution Nucleation");
				}

				funcs.add(rateFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, primaryThickness, MAIN_COLOR));
				
				if (dists != null) {
					// we have a solution distribution
					EvenlyDiscretizedFunc[] fractiles = dists.calcCumulativeSectFractiles(
							List.of(sect.getSectionId()), 0d, 0.16, 0.5, 0.84, 1d);
					double min = fractiles[0].getY(0);
					double p16 = fractiles[1].getY(0);
					double p50 = fractiles[2].getY(0);
					double p84 = fractiles[3].getY(0);
					double max = fractiles[4].getY(0);
					
					UncertainArbDiscFunc minMaxFunc = uncertCopyAtY(emptyFunc, p50, min, max);

					if (first)
						minMaxFunc.setName("p[0,16,84,100]");
					
					funcs.add(minMaxFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_BOUNDS));
					
					UncertainArbDiscFunc sixyEightFunc = uncertCopyAtY(emptyFunc, p50, p16, p84);
					
					funcs.add(sixyEightFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_68_OVERLAY));
				}
			}

			first = false;
		}
		PlotSpec nuclRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Nucleation Rate (per year)");
		nuclRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(nuclRateSpec, funcs, chars, yRange(funcs, new Range(1e-5, 1e-4), new Range(1e-9, 1e0), 5), true);
	}
	
	static AlongStrikePlot buildSlipRateReductionPlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotCurveCharacterstics creepRedChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED.darker());
		PlotCurveCharacterstics aseisChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GREEN.darker());
		PlotCurveCharacterstics couplingChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE.darker());
		
		for (int s=0; s<faultSects.size(); s++) {
			FaultSection sect = faultSects.get(s);
			boolean first = funcs.isEmpty();
			
			double origSlipRate = sect.getOrigAveSlipRate();
			
			double momentRed;
			if (origSlipRate <= 0d || !Double.isFinite(origSlipRate)) {
				momentRed = 1d;
			} else {
				double origArea = sect.getArea(false);
				double reducedArea = sect.getArea(true);
				double reducedSlipRate = sect.getReducedAveSlipRate();
				double origMoRate = FaultMomentCalc.getMoment(origArea, origSlipRate*1e-3);
				double reducedMoRate = FaultMomentCalc.getMoment(reducedArea, reducedSlipRate*1e-3);
				momentRed = (origMoRate - reducedMoRate)/origMoRate;
			}
			
			double aseis = sect.getAseismicSlipFactor();
			double coupling = sect.getCouplingCoeff();
			
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			funcs.add(SectBySectDetailPlots.copyAtY(emptyFunc, momentRed));
			chars.add(creepRedChar);
			if (first)
				funcs.get(funcs.size()-1).setName("Fractional Moment Reduction");
			
			funcs.add(SectBySectDetailPlots.copyAtY(emptyFunc, aseis));
			chars.add(aseisChar);
			if (first)
				funcs.get(funcs.size()-1).setName("Aseismic Slip Factor");
			
			funcs.add(SectBySectDetailPlots.copyAtY(emptyFunc, coupling));
			chars.add(couplingChar);
			if (first)
				funcs.get(funcs.size()-1).setName("Coupling Coefficient");
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, faultName, xLabel, "Creep Reduction");
		// legend at bottom
		spec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(spec, funcs, chars, new Range(0d, 1d), false);
	}
	
	static AlongStrikePlot buildSlipRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX, boolean logY) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class)
				&& meta.comparison.rupSet.hasModule(SlipAlongRuptureModel.class) && meta.comparisonHasSameSects;
		
		SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
		
		double[] solSlipRates = null;
		if (meta.hasPrimarySol() && meta.primary.rupSet.hasModule(AveSlipModule.class) && meta.primary.rupSet.hasModule(SlipAlongRuptureModel.class))
			solSlipRates = sectSolSlipRates(meta.primary.sol, faultSects);
		double[] compSolSlipRates = comp ? sectSolSlipRates(meta.comparison.sol, faultSects) : null;
		
		double avgTarget = 0d;
		
		PlotCurveCharacterstics origSlipChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK.darker());
		PlotCurveCharacterstics reducedSlipChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY.darker());
		PlotCurveCharacterstics creepRateChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.ORANGE.darker());
		
		boolean firstCreep = true;
		for (int s=0; s<faultSects.size(); s++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			FaultSection sect = faultSects.get(s);
			
			if (sect instanceof GeoJSONFaultSection) {
				GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
				double creepRate = geoSect.getProperties().getDouble("CreepRate", Double.NaN);
				if (Double.isFinite(creepRate)) {
					XY_DataSet creepFunc = copyAtY(emptyFunc, creepRate);
					if (firstCreep)
						creepFunc.setName("Creep Rate");
					funcs.add(0, creepFunc);
					chars.add(0, creepRateChar);
					firstCreep = false;
				}
			}
			
			double origSlip = sect.getOrigAveSlipRate();
			XY_DataSet origFunc = copyAtY(emptyFunc, origSlip);
			if (s == 0)
				origFunc.setName("Original");
			funcs.add(origFunc);
			chars.add(origSlipChar);
			
			double reducedSlip = sect.getReducedAveSlipRate();
			XY_DataSet reducedFunc = copyAtY(emptyFunc, reducedSlip);
			if (s == 0)
				reducedFunc.setName("Creep Reduced");
			funcs.add(reducedFunc);
			chars.add(reducedSlipChar);
			
			if (slipRates != null) {
				double targetSlip = slipRates.getSlipRate(sect.getSectionId())*1e3;
				XY_DataSet targetFunc = copyAtY(emptyFunc, targetSlip);
				if (s == 0)
					targetFunc.setName("Target");
				
				funcs.add(targetFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, TARGET_COLOR));
				
				double stdDev = slipRates.getSlipRateStdDev(sect.getSectionId())*1e3;
				
				if (stdDev > 0d) {
					UncertainArbDiscFunc uncertFunc = uncertCopyAtY(emptyFunc, targetSlip, stdDev);

					if (s == 0)
						uncertFunc.setName("+/- σ");
					
					funcs.add(uncertFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, targetBoundsColor));
				}
				
				avgTarget += targetSlip;
			} else if (solSlipRates != null) {
				avgTarget += solSlipRates[s];
			} else {
				avgTarget += reducedSlip;
			}
			
			if (comp) {
				XY_DataSet compSolFunc = copyAtY(emptyFunc, compSolSlipRates[s]*1e3);
				if (s == 0)
					compSolFunc.setName("Comparison Solution");
				
				funcs.add(compSolFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, COMP_COLOR.darker()));
			}
			
			if (solSlipRates != null) {
				XY_DataSet solFunc = copyAtY(emptyFunc, solSlipRates[s]*1e3);
				if (s == 0)
					solFunc.setName("Solution");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.MAGENTA.darker()));
			}
		}
		avgTarget /= faultSects.size();
		
		PlotSpec slipRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Slip Rate (mm/yr)");
		// legend at bottom
		slipRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		Range yRange;
		if (logY) {
			Range defaultSlipRange;
			if (avgTarget > 1d)
				defaultSlipRange = new Range(1e0, 1e1);
			else if (avgTarget > 0.1)
				defaultSlipRange = new Range(1e-1, 1e0);
			else
				defaultSlipRange = new Range(1e-2, 1e-1);
			yRange = yRange(funcs, defaultSlipRange, new Range(1e-5, 1e2), 3);
		} else {
			double minSlip = Double.POSITIVE_INFINITY;
			double maxSlip = Double.NEGATIVE_INFINITY;
			for (XY_DataSet func : funcs) {
				minSlip = Math.min(minSlip, func.getMinY());
				maxSlip = Math.max(maxSlip, func.getMaxY());
			}
			double delta;
			if (maxSlip > 20d)
				delta = 10d;
			else if (maxSlip > 10d)
				delta = 5d;
			else
				delta = 1d;
			maxSlip = Math.max(1d, Math.ceil(maxSlip/delta)*delta);
			minSlip = Math.floor(minSlip/delta)*delta;
			if (minSlip < 5d)
				minSlip = 0d;
			yRange = new Range(minSlip, maxSlip);
		}
		return new AlongStrikePlot(slipRateSpec, funcs, chars, yRange, logY);
	}
	
	private static AlongStrikePlot buildMoRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class)
				&& meta.comparisonHasSameSects;
		
		SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
		
		double[] rupMoRates = SectBValuePlot.calcRupMoments(meta.primary.rupSet);
		for (int r=0; r<rupMoRates.length; r++)
			rupMoRates[r] *= meta.primary.sol.getRateForRup(r);
		double[] compRupMoRates = null;
		if (comp) {
			compRupMoRates = SectBValuePlot.calcRupMoments(meta.comparison.rupSet);
			for (int r=0; r<compRupMoRates.length; r++)
				compRupMoRates[r] *= meta.comparison.sol.getRateForRup(r);
		}
		
		for (int s=0; s<faultSects.size(); s++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			int sectIndex = faultSects.get(s).getSectionId();
			
			if (slipRates != null) {
				double targetNucl = FaultMomentCalc.getMoment(rupSet.getAreaForSection(sectIndex), slipRates.getSlipRate(sectIndex));
				XY_DataSet targetFunc = copyAtY(emptyFunc, targetNucl);
				if (s == 0)
					targetFunc.setName("Target Nucleation");
				
				funcs.add(targetFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, TARGET_COLOR));
			}
			
			if (comp) {
				double particRate = SectBValuePlot.calcSectMomentRate(meta.comparison.rupSet, compRupMoRates, false, sectIndex);
				
				XY_DataSet solFunc = copyAtY(emptyFunc, particRate);
				if (s == 0)
					solFunc.setName("Comparison Solution Participation");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
				
				double nuclRate = SectBValuePlot.calcSectMomentRate(meta.comparison.rupSet, compRupMoRates, true, sectIndex);
				
				solFunc = copyAtY(emptyFunc, nuclRate);
				if (s == 0)
					solFunc.setName("Nucleation");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, COMP_COLOR));
			}
			
			double particRate = SectBValuePlot.calcSectMomentRate(meta.primary.rupSet, rupMoRates, false, sectIndex);
			
			XY_DataSet solFunc = copyAtY(emptyFunc, particRate);
			if (s == 0) {
				if (comp)
					solFunc.setName("Primary Participation");
				else
					solFunc.setName("Solution Participation");
			}
			
			funcs.add(solFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
			
			double nuclRate = SectBValuePlot.calcSectMomentRate(meta.primary.rupSet, rupMoRates, true, sectIndex);
			
			solFunc = copyAtY(emptyFunc, nuclRate);
			if (s == 0)
				solFunc.setName("Nucleation");
			
			funcs.add(solFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, MAIN_COLOR));
		}
		
		PlotSpec moRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Moment Rate (N-m/yr)");
		// legend at bottom
		moRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(moRateSpec, funcs, chars, yRange(funcs, new Range(1e15, 1e19), new Range(1e10, 1e20), 6), true);
	}
	
	private static AlongStrikePlot buildBValPlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class)
				&& meta.comparisonHasSameSects;
		
		double[] rupMoRates = SectBValuePlot.calcRupMoments(meta.primary.rupSet);
		for (int r=0; r<rupMoRates.length; r++)
			rupMoRates[r] *= meta.primary.sol.getRateForRup(r);
		double[] compRupMoRates = null;
		if (comp) {
			compRupMoRates = SectBValuePlot.calcRupMoments(meta.comparison.rupSet);
			for (int r=0; r<compRupMoRates.length; r++)
				compRupMoRates[r] *= meta.comparison.sol.getRateForRup(r);
		}
		
		ModSectMinMags modMinMags = meta.primary.rupSet.getModule(ModSectMinMags.class);
		RupMFDsModule rupMFDs = meta.primary.sol.getModule(RupMFDsModule.class);
		ModSectMinMags compModMinMags = comp ? meta.comparison.rupSet.getModule(ModSectMinMags.class) : null;
		RupMFDsModule compRupMFDs = comp ? meta.comparison.sol.getModule(RupMFDsModule.class) : null;
		
		BranchSectBVals branchBVals = meta.primary.sol.getModule(BranchSectBVals.class);
		
		List<? extends IncrementalMagFreqDist> sectTargetMFDs = null;
		if (meta.primary.rupSet.hasModule(InversionTargetMFDs.class))
			sectTargetMFDs = meta.primary.rupSet.requireModule(InversionTargetMFDs.class).getOnFaultSupraSeisNucleationMFDs();
		
		EvenlyDiscretizedFunc refFunc = SectBValuePlot.refFunc;
		
		for (int s=0; s<faultSects.size(); s++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			int sectIndex = faultSects.get(s).getSectionId();
			
			if (sectTargetMFDs != null) {
//				if (branchBVals != null && branchBVals.hasTargetBVals()) {
//					targetBVal = branchBVals.getSectTargetMeanBVal(sectIndex);
//				} else {
//					
//				}
				boolean[] bins = new boolean[refFunc.size()];
				IncrementalMagFreqDist sectMFD = new IncrementalMagFreqDist(
						refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
				IncrementalMagFreqDist target = sectTargetMFDs.get(sectIndex);
				
				boolean any = false;
				
				for (Point2D pt : target) {
					if (pt.getY() > 0d) {
						int binIndex = sectMFD.getClosestXIndex(pt.getX());
						sectMFD.add(binIndex, pt.getY());
						bins[binIndex] = true;
						any = true;
					}
				}
				
				if (any) {
					double targetBVal = SectBValuePlot.estBValue(bins, bins, sectMFD).b;
					XY_DataSet solFunc = copyAtY(emptyFunc, targetBVal);
					if (s == 0)
						solFunc.setName("Target");
					
					funcs.add(solFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, TARGET_COLOR));
				}
				
				if (branchBVals != null && branchBVals.hasTargetBVals()) {
					double targetBVal = branchBVals.getSectTargetMeanBVal(sectIndex);
					
					XY_DataSet solFunc = copyAtY(emptyFunc, targetBVal);
					if (s == 0)
						solFunc.setName("Mean Target");
					
					funcs.add(solFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, TARGET_COLOR));
				}
			}
			
			if (comp) {
				double bVal = bVal(meta.comparison.sol, sectIndex, compModMinMags, compRupMFDs, refFunc);
				
				XY_DataSet solFunc = copyAtY(emptyFunc, bVal);
				if (s == 0)
					solFunc.setName("Comparison Solution");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
			}
			
			double bVal = bVal(meta.primary.sol, sectIndex, modMinMags, rupMFDs, refFunc);
			
			XY_DataSet solFunc = copyAtY(emptyFunc, bVal);
			if (s == 0) {
				if (comp)
					solFunc.setName("Primary");
				else
					solFunc.setName("Solution");
			}
			
			funcs.add(solFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
			
			if (branchBVals != null) {
				// add distribution
				ArbDiscrEmpiricalDistFunc bValDist = branchBVals.getSectBValDist(sectIndex);
				
				double min = bValDist.getMinX();
				double p16 = bValDist.getInterpolatedFractile(0.16);
				double p50 = bValDist.getInterpolatedFractile(0.5);
				double p84 = bValDist.getInterpolatedFractile(0.84);
				double max = bValDist.getMaxX();
				
				UncertainArbDiscFunc minMaxFunc = uncertCopyAtY(emptyFunc, p50, min, max);

				if (s == 0)
					minMaxFunc.setName("p[0,16,84,100]");
				
				funcs.add(minMaxFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_BOUNDS));
				
				UncertainArbDiscFunc sixyEightFunc = uncertCopyAtY(emptyFunc, p50, p16, p84);
				
				funcs.add(sixyEightFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, PRIMARY_68_OVERLAY));
				
				solFunc = copyAtY(emptyFunc, branchBVals.getSectMeanBVal(sectIndex));
				if (s == 0)
					solFunc.setName("Mean");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, MAIN_COLOR));
			}
		}
		
		PlotSpec bValSpec = new PlotSpec(funcs, chars, faultName, xLabel, "G-R Estimated b-value");
		// legend at bottom
		bValSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(bValSpec, funcs, chars, new Range(-3, 3), false);
	}
	
	private static double bVal(FaultSystemSolution sol, int sectIndex, ModSectMinMags modMinMags, RupMFDsModule rupMFDs,
			EvenlyDiscretizedFunc refFunc) {
		boolean[] binsAvail = new boolean[refFunc.size()];
		boolean[] binsUsed = new boolean[refFunc.size()];
		SectBValuePlot.calcSectMags(sectIndex, sol, modMinMags, rupMFDs, binsAvail, binsUsed);
		IncrementalMagFreqDist sectMFD = sol.calcNucleationMFD_forSect(
				sectIndex, refFunc.getX(0), refFunc.getX(refFunc.size()-1), refFunc.size());
		return SectBValuePlot.estBValue(binsAvail, binsUsed, sectMFD).b;
	}

	private static XY_DataSet line(double x1, double y1, double x2, double y2) {
		return new DefaultXY_DataSet(new double[] { x1, x2 }, new double[] { y1, y2 });
	}
	
	private static double[] sectSolSlipRates(FaultSystemSolution sol, List<FaultSection> parentSects) {
		double[] solSlipRates = new double[parentSects.size()];
		FaultSystemRupSet rupSet = sol.getRupSet();
		SlipAlongRuptureModel slipAlongs = rupSet.getModule(SlipAlongRuptureModel.class);
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		
		for (int s=0; s<solSlipRates.length; s++)
			solSlipRates[s] = slipAlongs.calcSlipRateForSect(sol, aveSlips, parentSects.get(s).getSectionId());
		
		return solSlipRates;
	}
	
	private static double rateAbove(double minMag, int sectIndex, FaultSystemSolution sol) {
		return sol.calcParticRateForSect(sectIndex, minMag, Double.POSITIVE_INFINITY);
	}
	
	private static double nuclRateAbove(double minMag, int sectIndex, FaultSystemSolution sol) {
		return sol.calcNucleationRateForSect(sectIndex, minMag, Double.POSITIVE_INFINITY);
	}
	
	private static double paleoRate(int sectIndex, FaultSystemSolution sol, PaleoProbabilityModel probModel) {
		double rate = 0d;
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int rupIndex : rupSet.getRupturesForSection(sectIndex)) {
			double rupRate = sol.getRateForRup(rupIndex);
			if (rupRate > 0)
				rate += rupRate * probModel.getProbPaleoVisible(rupSet, rupIndex, sectIndex);
		}
		return rate;
	}
	
	private static double[] calcPaleoRates(List<FaultSection> sects, FaultSystemSolution sol, PaleoSlipProbabilityModel probModel) {
		double[] rates = new double[sects.size()];
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		HashSet<Integer> rups = new HashSet<>();
		Map<Integer, Integer> sectIndexMap = new HashMap<>();
		for (int s=0; s<sects.size(); s++) {
			FaultSection sect = sects.get(s);
			rups.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
			sectIndexMap.put(sect.getSectionId(), s);
		}
		
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlongs = rupSet.requireModule(SlipAlongRuptureModel.class);
		
		for (int rupIndex : rups) {
			double rupRate = sol.getRateForRup(rupIndex);
			if (rupRate > 0) {
				double[] slipAlongRups = slipAlongs.calcSlipOnSectionsForRup(rupSet, aveSlips, rupIndex);
				List<Integer> sectIDs = rupSet.getSectionsIndicesForRup(rupIndex);
				for (int s=0; s<slipAlongRups.length; s++) {
					Integer index = sectIndexMap.get(sectIDs.get(s));
					if (index != null) {
						double slipOnSect = slipAlongRups[s]; 
						double probVisible = probModel.getProbabilityOfObservedSlip(slipOnSect);
						rates[index] += rupRate * probVisible;
					}
				}
			}
		}
		return rates;
	}
	
	static XY_DataSet copyAtY(XY_DataSet func, double y) {
		DefaultXY_DataSet ret = new DefaultXY_DataSet();
		for (Point2D pt : func)
			ret.set(pt.getX(), y);
		return ret;
	}
	
	static UncertainArbDiscFunc uncertCopyAtY(XY_DataSet func, double y, double stdDev) {
		ArbitrarilyDiscretizedFunc middleFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : func)
			middleFunc.set(pt.getX(), y);
		return UncertainArbDiscFunc.forStdDev(middleFunc, stdDev, UncertaintyBoundType.ONE_SIGMA, false);
	}
	
	static UncertainArbDiscFunc uncertCopyAtY(XY_DataSet func, double y, double yLower, double yUpper) {
		ArbitrarilyDiscretizedFunc middleFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : func) {
			middleFunc.set(pt.getX(), y);
			lowerFunc.set(pt.getX(), yLower);
			upperFunc.set(pt.getX(), yUpper);
		}
		return new UncertainArbDiscFunc(middleFunc, lowerFunc, upperFunc);
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class);
	}
	
	public static void main(String[] args) throws IOException {
//		File inputFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip");
//		File inputFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
//				+ "FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
		File inputFile = new File("/data/kevin/markdown/inversions/"
				+ "2022_02_14-U3_ZENG-Shaw09Mod-DsrUni-SupraB0.8-NuclMFD-ShawR0_3-reweight_MAD-conserve-10m//solution.zip");
//		File inputFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_plausibleMulti15km_adaptive6km_direct_"
//				+ "cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01"
//				+ "_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
		String name = "UCERF3";
		ZipFile zip = new ZipFile(inputFile);
		
		File outputDir = new File("/tmp/sect_by_sect");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemRupSet rupSet;
		FaultSystemSolution sol = null;
		if (FaultSystemSolution.isSolution(zip)) {
			sol = FaultSystemSolution.load(zip);
			rupSet = sol.getRupSet();
		} else {
			rupSet = FaultSystemRupSet.load(zip);
		}
		
		SectBySectDetailPlots plot = new SectBySectDetailPlots();
		
		plot.writePlot(rupSet, sol, name, outputDir);
	}

}
