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
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
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
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
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
		if (meta.comparison != null && !meta.comparison.rupSet.hasModule(ClusterRuptures.class))
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

		return table.build();
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
			if (meta.primary.sol != null) {
				double rate = meta.primary.sol.getRateForRup(r);
				totRate += rate;
				if (rup.getTotalNumClusters() > 1)
					multiRate += rate;
				if (rate > 0d)
					rupCountNonZero++;
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
		if (meta.comparison != null)
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
			for (Location loc : plotSect.getFaultTrace()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		// also buffer around our trace
		for (FaultSection sect : mySects) {
			for (Location loc : new Region(sect.getFaultTrace(), maxNeighborDistance).getBorder()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		Region plotRegion = new Region(new Location(latTrack.getMin()-0.1, lonTrack.getMin()-0.1),
				new Location(latTrack.getMax()+0.1, lonTrack.getMax()+0.1));
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(doGeoJSON);
		mapMaker.setRegion(plotRegion);
		mapMaker.setWritePDFs(false);
		mapMaker.setSkipNaNs(true);
		mapMaker.highLightSections(mySects, highlightChar);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		List<String> jsonLinks = doGeoJSON ? new ArrayList<>() : null;
		for (boolean rate : new boolean[] {false, true}) {
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
					min = Math.floor(logValTrack.getMin());
					max = Math.ceil(logValTrack.getMax());
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
			if (meta.comparison != null)
				table.addColumn(meta.comparison.name);
			table.finalizeLine();
			
			parentDists.put(name, minDist);
			
			boolean connected = rupData.parentCoruptures.containsKey(parentID);
			
			table.initNewLine();
			table.addColumn("**Connected?**");
			table.addColumn(connected);
			if (meta.comparison != null)
				table.addColumn(compRupData.parentCoruptures.containsKey(parentID));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Directly Connected?**");
			boolean directly = rupData.directlyConnectedParents.contains(parentID);
			table.addColumn(directly);
			if (meta.comparison != null)
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
				if (meta.comparison != null) {
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
						for (int rupIndex : compCorups) {
							ClusterRupture rup = clusterRups.get(rupIndex);
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
			table.addColumn("**Co-rupture Count**");
			if (rupData.parentCoruptures.containsKey(parentID))
				table.addColumn(countDF.format(rupData.parentCoruptures.get(parentID).size()));
			else
				table.addColumn("0");
			if (meta.comparison != null) {
				if (compRupData.parentCoruptures.containsKey(parentID))
					table.addColumn(countDF.format(compRupData.parentCoruptures.get(parentID).size()));
				else
					table.addColumn("0");
			}
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Co-rupture Rate**");
			if (rupData.parentCoruptureRates != null && rupData.parentCoruptureRates.containsKey(parentID))
				table.addColumn(rupData.parentCoruptureRates.get(parentID).floatValue());
			else
				table.addColumn("_N/A_");
			if (meta.comparison != null) {
				if (compRupData.parentCoruptureRates != null && compRupData.parentCoruptureRates.containsKey(parentID))
					table.addColumn(compRupData.parentCoruptureRates.get(parentID).floatValue());
				else
					table.addColumn("_N/A_");
			}
			table.finalizeLine();
			
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
		private Map<Integer, Integer> sectCoruptureCounts;
		private Map<Integer, Double> sectCoruptureRates;
		
		public RupConnectionsData(int parentSectIndex, ClusterRuptures clusterRups,
				FaultSystemRupSet rupSet, FaultSystemSolution sol) {
			parentCoruptures = new HashMap<>();
			directlyConnectedParents = new HashSet<>();
			parentCoruptureRates = sol == null ? null : new HashMap<>();
			sectCoruptureCounts = new HashMap<>();
			sectCoruptureRates = sol == null ? null : new HashMap<>();
			
			for (int rupIndex : rupSet.getRupturesForParentSection(parentSectIndex)) {
				ClusterRupture rup = clusterRups.get(rupIndex);
				double rate = sectCoruptureRates == null ? Double.NaN : sol.getRateForRup(rupIndex);
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
					} else if (parentCoruptureRates != null) {
						parentCoruptureRates.put(cluster.parentSectionID, parentCoruptureRates.get(cluster.parentSectionID)+rate);
					}
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
		
		IncrementalMagFreqDist defaultMFD = SolMFDPlot.initDefaultMFD(minMag, maxMag);
		
		SummedMagFreqDist nuclTargetMFD = null;
		if (meta.primary.rupSet.hasModule(InversionTargetMFDs.class)) {
			// see if we have section nucleation MFDs
			InversionTargetMFDs targetMFDs = meta.primary.rupSet.requireModule(InversionTargetMFDs.class);
			List<? extends IncrementalMagFreqDist> sectNuclMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
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
		}
		
		Range xRange = SolMFDPlot.xRange(defaultMFD);
		List<XY_DataSet> incrFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> incrChars = new ArrayList<>();
		
		IncrementalMagFreqDist particMFD = sol.calcParticipationMFD_forRups(
				rups, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
		SummedMagFreqDist nuclMFD = new SummedMagFreqDist(defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
		for (FaultSection sect : faultSects)
			nuclMFD.addIncrementalMagFreqDist(sol.calcNucleationMFD_forSect(
					sect.getSectionId(), defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size()));

		incrFuncs.add(particMFD);
		incrChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, MAIN_COLOR));
		
		incrFuncs.add(nuclMFD);
		incrChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, MAIN_COLOR.darker()));

		List<EvenlyDiscretizedFunc> cmlFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> cmlChars = new ArrayList<>();
		
		if (nuclTargetMFD != null) {
			nuclTargetMFD.setName("Target Nucleation");
			
			Color targetColor = Color.GREEN.darker();
			
			addFakeHistFromFunc(nuclTargetMFD, incrFuncs, incrChars,
					new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
			
			cmlFuncs.add(nuclTargetMFD.getCumRateDistWithOffset());
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, targetColor));
		}
		
		if (meta.comparison != null && meta.comparison.sol != null) {
			HashSet<Integer> compRups = new HashSet<>();
			for (FaultSection sect : faultSects)
				compRups.addAll(meta.comparison.rupSet.getRupturesForSection(sect.getSectionId()));
			IncrementalMagFreqDist compParticMFD = meta.comparison.sol.calcParticipationMFD_forRups(
					compRups, defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
			SummedMagFreqDist compNuclMFD = new SummedMagFreqDist(defaultMFD.getMinX(), defaultMFD.getMaxX(), defaultMFD.size());
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
			
			cmlFuncs.add(compParticMFD.getCumRateDistWithOffset());
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, COMP_COLOR));
			cmlFuncs.add(compNuclMFD.getCumRateDistWithOffset());
			cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, COMP_COLOR));
		} else {
			particMFD.setName("Participation");
			nuclMFD.setName("Nucleation");
		}
		
		cmlFuncs.add(particMFD.getCumRateDistWithOffset());
		cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, MAIN_COLOR));
		cmlFuncs.add(nuclMFD.getCumRateDistWithOffset());
		cmlChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, MAIN_COLOR));
		
		Range yRange = yRange(cmlFuncs, MFD_DEFAULT_Y_RANGE, MFD_MAX_Y_RANGE, MFD_MAX_Y_RANGE_ORDERS_MAG);
		if (yRange == null)
			return new ArrayList<>();
		
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
		
		lines.addAll(table.build());
		
		return lines;
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
		
		double legendRelX = latX ? 0.975 : 0.025;
		
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
								targetSectRates[sectID], lowerVal, upperVal);
					} else {
						targetSectRateStdDevs = null;
					}
				}
			}
		} else {
			// we didn't use to store them explicitly, fall back to inversion configuration
			InversionConfiguration invConfig = meta.primary.sol.getModule(InversionConfiguration.class);
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
		
		if (paleoConstraints != null || paleoSlipConstraints != null)
			plots.add(buildPaleoRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX, xRange, latX,
					paleoData, paleoConstraints, paleoSlipConstraints));
		
		// add nucleation rate plot
		plots.add(buildNuclRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX,
				targetNucleation ? targetSectRates : null, targetNucleation ? targetSectRateStdDevs : null));
		
		if (rupSet.hasModule(SlipAlongRuptureModel.class) && rupSet.hasModule(AveSlipModule.class))
			plots.add(buildSlipRatePlot(meta, faultSects, faultName, emptySectFuncs, xLabel, legendRelX));
		
		List<String> lines = new ArrayList<>();
		
		lines.add("## Along-Strike Values");
		lines.add(topLink); lines.add("");

		String prefix = "sect_along_strike";
		writeAlongStrikePlots(outputDir, prefix, plots, parentsMap, latX, xLabel, xRange, faultName);

		lines.add("![Along-strike plot]("+outputDir.getName()+"/"+prefix+".png)");
		
		if (meta.primary.rupSet.hasModule(AveSlipModule.class)) {
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
	
	private static void writeAlongStrikePlots(File outputDir, String prefix, List<AlongStrikePlot> plots,
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
	
	private static class AlongStrikePlot {
		
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

		boolean comp = meta.hasComparisonSol();
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
					UncertainArbDiscFunc uncertFunc = ucertCopyAtY(emptyFunc, targetRates[sect.getSectionId()],
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
		boolean comp = meta.hasComparisonSol();
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
		boolean comp = meta.hasComparisonSol();
		
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
					UncertainArbDiscFunc uncertFunc = ucertCopyAtY(emptyFunc, targetRates[sect.getSectionId()],
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

			first = false;
		}
		PlotSpec nuclRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Nucleation Rate (per year)");
		nuclRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(nuclRateSpec, funcs, chars, yRange(funcs, new Range(1e-5, 1e-4), new Range(1e-9, 1e0), 5), true);
	}
	
	private static AlongStrikePlot buildSlipRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class)
				&& meta.comparison.rupSet.hasModule(SlipAlongRuptureModel.class);
		
		SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
		
		double[] solSlipRates = sectSolSlipRates(meta.primary.sol, faultSects);
		double[] compSolSlipRates = comp ? sectSolSlipRates(meta.comparison.sol, faultSects) : null;
		
		double avgTarget = 0d;
		
		for (int s=0; s<faultSects.size(); s++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			if (slipRates != null) {
				double targetSlip = slipRates.getSlipRate(faultSects.get(s).getSectionId())*1e3;
				XY_DataSet targetFunc = copyAtY(emptyFunc, targetSlip);
				if (s == 0)
					targetFunc.setName("Target");
				
				funcs.add(targetFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, TARGET_COLOR));
				
				double stdDev = slipRates.getSlipRateStdDev(faultSects.get(s).getSectionId())*1e3;
				
				if (stdDev > 0d) {
					UncertainArbDiscFunc uncertFunc = ucertCopyAtY(emptyFunc, targetSlip, stdDev);

					if (s == 0)
						uncertFunc.setName("+/- σ");
					
					funcs.add(uncertFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, targetBoundsColor));
				}
				
				avgTarget += targetSlip;
			} else {
				avgTarget += solSlipRates[s];
			}
			
			if (comp) {
				XY_DataSet compSolFunc = copyAtY(emptyFunc, compSolSlipRates[s]*1e3);
				if (s == 0)
					compSolFunc.setName("Comparison Solution");
				
				funcs.add(compSolFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, COMP_COLOR.darker()));
			}
			
			XY_DataSet solFunc = copyAtY(emptyFunc, solSlipRates[s]*1e3);
			if (s == 0)
				solFunc.setName("Solution");
			
			funcs.add(solFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.MAGENTA.darker()));
		}
		avgTarget /= faultSects.size();
		
		PlotSpec slipRateSpec = new PlotSpec(funcs, chars, faultName, xLabel, "Slip Rate (mm/yr)");
		// legend at bottom
		if (slipRates != null)
			slipRateSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		Range defaultSlipRange;
		if (avgTarget > 1d)
			defaultSlipRange = new Range(1e0, 1e1);
		else if (avgTarget > 0.1)
			defaultSlipRange = new Range(1e-1, 1e0);
		else
			defaultSlipRange = new Range(1e-2, 1e-1);
		return new AlongStrikePlot(slipRateSpec, funcs, chars, yRange(funcs, defaultSlipRange, new Range(1e-5, 1e2), 3), true);
	}
	
	private static AlongStrikePlot buildMoRatePlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FaultSystemRupSet rupSet = meta.primary.rupSet;
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class);
		
		SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
		
		double[] rupMoRates = SectBValuePlot.calcRupMomentRates(meta.primary.sol);
		double[] compRupMoRates = comp ? SectBValuePlot.calcRupMomentRates(meta.comparison.sol) : null;
		
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
	
	private static double sectMinMag(FaultSystemRupSet rupSet, int sectIndex) {
		ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
		if (minMags == null)
			return rupSet.getMinMagForSection(sectIndex);
		return minMags.getMinMagForSection(sectIndex);
	}
	
	private static AlongStrikePlot buildBValPlot(ReportMetadata meta, List<FaultSection> faultSects,
			String faultName, List<XY_DataSet> emptySectFuncs, String xLabel, double legendRelX) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		boolean comp = meta.hasComparisonSol() && meta.comparison.rupSet.hasModule(AveSlipModule.class);
		
		double[] rupMoRates = SectBValuePlot.calcRupMomentRates(meta.primary.sol);
		double[] compRupMoRates = comp ? SectBValuePlot.calcRupMomentRates(meta.comparison.sol) : null;
		
		for (int s=0; s<faultSects.size(); s++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(s);
			
			int sectIndex = faultSects.get(s).getSectionId();
			
			if (comp) {
				double minMag = sectMinMag(meta.comparison.rupSet, sectIndex);
				double maxMag = meta.comparison.rupSet.getMaxMagForSection(sectIndex);
				double bVal = SectBValuePlot.estBValue(minMag, maxMag, meta.comparison.sol,
						meta.comparison.rupSet.getRupturesForSection(sectIndex), compRupMoRates,
						SectIDRange.build(sectIndex, sectIndex)).b;
				
				XY_DataSet solFunc = copyAtY(emptyFunc, bVal);
				if (s == 0)
					solFunc.setName("Comparison Solution");
				
				funcs.add(solFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
			}
			
			double minMag = sectMinMag(meta.primary.rupSet, sectIndex);
			double maxMag = meta.primary.rupSet.getMaxMagForSection(sectIndex);
			double bVal = SectBValuePlot.estBValue(minMag, maxMag, meta.primary.sol,
					meta.primary.rupSet.getRupturesForSection(sectIndex), rupMoRates,
					SectIDRange.build(sectIndex, sectIndex)).b;
			
			XY_DataSet solFunc = copyAtY(emptyFunc, bVal);
			if (s == 0) {
				if (comp)
					solFunc.setName("Primary");
				else
					solFunc.setName("Solution");
			}
			
			funcs.add(solFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
		}
		
		PlotSpec bValSpec = new PlotSpec(funcs, chars, faultName, xLabel, "G-R Estimated b-value");
		// legend at bottom
		bValSpec.setLegendInset(RectangleAnchor.BOTTOM_LEFT, legendRelX, 0.025, 0.95, false);
		return new AlongStrikePlot(bValSpec, funcs, chars, new Range(-3, 3), false);
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
		double rate = 0d;
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int rupIndex : rupSet.getRupturesForSection(sectIndex))
			if (rupSet.getMagForRup(rupIndex) >= minMag)
				rate += sol.getRateForRup(rupIndex);
		return rate;
	}
	
	private static double nuclRateAbove(double minMag, int sectIndex, FaultSystemSolution sol) {
		double rate = 0d;
		FaultSystemRupSet rupSet = sol.getRupSet();
		double sectArea = rupSet.getAreaForSection(sectIndex);
		for (int rupIndex : rupSet.getRupturesForSection(sectIndex))
			if (rupSet.getMagForRup(rupIndex) >= minMag)
				rate += sol.getRateForRup(rupIndex)*sectArea/rupSet.getAreaForRup(rupIndex);
		return rate;
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
	
	private static XY_DataSet copyAtY(XY_DataSet func, double y) {
		DefaultXY_DataSet ret = new DefaultXY_DataSet();
		for (Point2D pt : func)
			ret.set(pt.getX(), y);
		return ret;
	}
	
	private static UncertainArbDiscFunc ucertCopyAtY(XY_DataSet func, double y, double stdDev) {
		ArbitrarilyDiscretizedFunc middleFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : func)
			middleFunc.set(pt.getX(), y);
		return UncertainArbDiscFunc.forStdDev(middleFunc, stdDev, UncertaintyBoundType.ONE_SIGMA, false);
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
