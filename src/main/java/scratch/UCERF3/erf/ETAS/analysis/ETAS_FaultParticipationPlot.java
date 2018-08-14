package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_FaultParticipationPlot extends ETAS_AbstractPlot {

	private String prefix;
	private boolean annualize;
	private boolean skipMaps;
	
	private boolean hasSpont;
	private boolean hasTriggered;
	
	private double[] minMagBins = { 0d, 6.5d, 7d, 7.5d, 8d };
	
	private FaultStats[] subSectStats;
	private Map<Integer, FaultStats> parentSectStats;
	private Map<Integer, HashSet<FaultStats>> fssIndexToStatsMap;
	
	private boolean hasAny = false;
	private boolean[] hasMags;
	private int catalogCount = 0;
	
	private Map<Double, CSVFile<String>> sectionCSVs;
	private Map<Double, CSVFile<String>> parentCSVs;

	protected ETAS_FaultParticipationPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, boolean annualize, boolean skipMaps) {
		super(config, launcher);
		this.prefix = prefix;
		this.annualize = annualize;
		this.skipMaps = skipMaps;
		
		this.hasTriggered = config.hasTriggers();
		this.hasSpont = config.isIncludeSpontaneous()
				|| (config.getTriggerCatalogFile() != null && config.isTreatTriggerCatalogAsSpontaneous());
	}

	@Override
	public boolean isFilterSpontaneous() {
		return hasTriggered;
	}

	@Override
	protected synchronized void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog,
			List<ETAS_EqkRupture> triggeredOnlyCatalog, FaultSystemSolution fss) {
		if (subSectStats == null) {
			System.out.println("Initializing section stats/mappings");
			// initialize
			FaultSystemRupSet rupSet = fss.getRupSet();
			
			subSectStats = new FaultStats[rupSet.getNumSections()];
			parentSectStats = new HashMap<>();
			
			fssIndexToStatsMap = new HashMap<>();
			
			for (int s=0; s<rupSet.getNumSections(); s++) {
				FaultSectionPrefData sect = rupSet.getFaultSectionData(s);
				subSectStats[s] = new FaultStats(s, sect.getName(), new HashSet<>(rupSet.getRupturesForSection(s)));
				mapRupturesToStat(subSectStats[s]);
				Integer parentID = sect.getParentSectionId();
				if (!parentSectStats.containsKey(parentID)) {
					FaultStats parentStats = new FaultStats(parentID, sect.getParentSectionName(),
							new HashSet<>(rupSet.getRupturesForParentSection(parentID)));
					mapRupturesToStat(parentStats);
					parentSectStats.put(parentID, parentStats);
				}
			}
			
			hasMags = new boolean[minMagBins.length];
			System.out.println("DONE Initializing section stats/mappings");
		}
		
		HashSet<FaultStats> statsToProcess = new HashSet<>();
		for (ETAS_EqkRupture rup : completeCatalog)
			if (rup.getFSSIndex() >= 0)
				statsToProcess.addAll(fssIndexToStatsMap.get(rup.getFSSIndex()));
		hasAny = hasAny || !statsToProcess.isEmpty();
		
		for (FaultStats stats : statsToProcess)
			stats.processCatalog(completeCatalog, triggeredOnlyCatalog);
		
		catalogCount++;
	}
	
	private void mapRupturesToStat(FaultStats stat) {
		for (Integer fssIndex : stat.allRupIDs) {
			HashSet<FaultStats> statsForIndex = fssIndexToStatsMap.get(fssIndex);
			if (statsForIndex == null) {
				statsForIndex = new HashSet<>();
				fssIndexToStatsMap.put(fssIndex, statsForIndex);
			}
			statsForIndex.add(stat);
		}
	}
	
	private class FaultStats {
		private int id;
		private String name;
		
		private HashSet<Integer> allRupIDs;
		private List<int[]> spontCounts;
		private List<int[]> triggeredCounts;
		private List<int[]> triggeredPrimaryCounts;
		
		private double[] spontMeans;
		private double[] spontProbs;
		
		private double[] triggeredMeans;
		private double[] triggeredProbs;
		private double[] triggeredPrimaryMeans;
		
		private FaultStats(int id, String name, HashSet<Integer> allRupIDs) {
			this.id = id;
			this.name = name;
			this.allRupIDs = allRupIDs;
			if (hasSpont)
				this.spontCounts = new ArrayList<>();
			if (hasTriggered) {
				this.triggeredCounts = new ArrayList<>();
				this.triggeredPrimaryCounts = new ArrayList<>();
			}
		}
		
		public void processCatalog(List<ETAS_EqkRupture> catalog, List<ETAS_EqkRupture> triggeredOnlyCatalog) {
			spontMeans = null;
			triggeredProbs = null;
			triggeredPrimaryMeans = null;
			if (hasSpont) {
				int[] mySpontCounts = null;
				for (ETAS_EqkRupture rup : catalog) {
					int fssIndex = rup.getFSSIndex();
					if (fssIndex < 0 || !allRupIDs.contains(fssIndex))
						continue;
					double mag = rup.getMag();
					// it's on this fault
					if (mySpontCounts == null)
						mySpontCounts = new int[minMagBins.length];
					for (int i=0; i<minMagBins.length; i++) {
						if (mag >= minMagBins[i]) {
							hasMags[i] = true;
							mySpontCounts[i]++;
						}
					}
				}
				spontCounts.add(mySpontCounts);
			}
			if (hasTriggered) {
				int[] myTriggeredCounts = null;
				int[] myTriggeredPrimaryCounts = null;
				for (ETAS_EqkRupture rup : triggeredOnlyCatalog) {
					int fssIndex = rup.getFSSIndex();
					if (fssIndex < 0 || !allRupIDs.contains(fssIndex))
						continue;
					double mag = rup.getMag();
					// it's on this fault
					if (myTriggeredCounts == null) {
						myTriggeredCounts = new int[minMagBins.length];
						myTriggeredPrimaryCounts = new int[minMagBins.length];
					}
					boolean primary = rup.getGeneration() == 1;
					for (int i=0; i<minMagBins.length; i++) {
						if (mag >= minMagBins[i]) {
							myTriggeredCounts[i]++;
							hasMags[i] = true;
							if (primary)
								myTriggeredPrimaryCounts[i]++;
						}
					}
				}
				triggeredCounts.add(myTriggeredCounts);
				triggeredPrimaryCounts.add(myTriggeredPrimaryCounts);
			}
		}
		
		public void calcStats(int numCatalogs) {
			if (spontMeans != null || triggeredMeans != null)
				return;
			
			if (hasSpont) {
				spontMeans = new double[minMagBins.length];
				spontProbs = new double[minMagBins.length];
				doCalcStats(spontCounts, numCatalogs, spontMeans, spontProbs);
			}
			
			if (hasTriggered) {
				triggeredMeans = new double[minMagBins.length];
				triggeredProbs = new double[minMagBins.length];
				doCalcStats(triggeredCounts, numCatalogs, triggeredMeans, triggeredProbs);
				
				triggeredPrimaryMeans = new double[minMagBins.length];
				doCalcStats(triggeredPrimaryCounts, numCatalogs, triggeredPrimaryMeans, null);
			}
		}
		
		private void doCalcStats(List<int[]> counts, int numCatalogs, double means[], double[] fractWiths) {
			for (int i=0; i<minMagBins.length; i++) {
				int numWith = 0;
				double sum = 0;
				for (int[] myCounts : counts) {
					if (myCounts != null && myCounts[i] > 0) {
						numWith++;
						sum += myCounts[i];
					}
				}
				means[i] = sum/(double)numCatalogs;
				if (annualize)
					means[i] /= getConfig().getDuration();
				if (fractWiths != null)
					fractWiths[i] = (double)numWith/(double)numCatalogs;
			}
		}
	}
	
	private static Comparator<FaultStats> statsNameComparator = new Comparator<ETAS_FaultParticipationPlot.FaultStats>() {

		@Override
		public int compare(FaultStats o1, FaultStats o2) {
			return o1.name.compareTo(o2.name);
		}
	};
	
	private CSVFile<String> buildCSV(FaultStats[] stats, boolean parents, int magIndex) {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		if (parents) {
			Arrays.sort(stats, statsNameComparator);
			header.add("Parent ID");
			header.add("Parent Name");
		} else {
			header.add("Subsection Index");
			header.add("Subsection Name");
		}
		
		double duration = getConfig().getDuration();
		if (annualize) {
			if (hasSpont) {
				header.add("Total Mean Annual Rate");
				header.add("Total "+getTimeLabel(duration, false)+" Prob");
			}
			if (hasTriggered) {
				header.add("Triggered Mean Annual Rate");
				header.add("Triggered "+getTimeLabel(duration, false)+" Prob");
				header.add("Triggered Primary Mean Annual Rate");
			}
		} else {
			if (hasSpont) {
				header.add("Total Mean Count");
				header.add("Total "+getTimeLabel(duration, false)+" Prob");
			}
			if (hasTriggered) {
				header.add("Triggered Mean Count");
				header.add("Triggered "+getTimeLabel(duration, false)+" Prob");
				header.add("Triggered Primary Mean Count");
			}
		}
		csv.addLine(header);
		
		for (FaultStats stat : stats) {
			List<String> line = new ArrayList<>();
			line.add(stat.id+"");
			line.add(stat.name);
			if (hasSpont) {
				line.add((float)stat.spontMeans[magIndex]+"");
				line.add((float)stat.spontProbs[magIndex]+"");
			}
			if (hasTriggered) {
				line.add((float)stat.triggeredMeans[magIndex]+"");
				line.add((float)stat.triggeredProbs[magIndex]+"");
				line.add((float)stat.triggeredPrimaryMeans[magIndex]+"");
			}
			csv.addLine(line);
		}
		
		return csv;
	}
	
	private Table<Double, Boolean, String> plotPrefixes;

	@Override
	public void finalize(File outputDir, FaultSystemSolution fss) throws IOException {
		if (!hasAny)
			return;
		// calculate all stats
		for (FaultStats stats : subSectStats)
			stats.calcStats(catalogCount);
		for (FaultStats stats : parentSectStats.values())
			stats.calcStats(catalogCount);
		
		// build CSVs
		sectionCSVs = new HashMap<>();
		parentCSVs = new HashMap<>();
		FaultStats[] parentStatsArray = new ArrayList<>(parentSectStats.values()).toArray(new FaultStats[0]);
		for (int m=0; m<minMagBins.length; m++) {
			double mag = minMagBins[m];
			String magStr;
			if (mag == 0)
				magStr = "supra_seis";
			else
				magStr = "m"+optionalDigitDF.format(mag);
			
			boolean hasMag = false;
			for (FaultStats stats : parentStatsArray)
				hasMag = hasMag || (hasSpont && stats.spontMeans[m] > 0) || (hasTriggered && stats.triggeredMeans[m] > 0);
			if (!hasMag)
				continue;
			
			CSVFile<String> sectionCSV = buildCSV(subSectStats, false, m);
			sectionCSVs.put(mag, sectionCSV);
			sectionCSV.writeToFile(new File(outputDir, prefix+"_"+magStr+"_sub_sects.csv"));
			
			CSVFile<String> parentCSV = buildCSV(parentStatsArray, true, m);
			parentCSVs.put(mag, parentCSV);
			parentCSV.writeToFile(new File(outputDir, prefix+"_"+magStr+"_parent_sects.csv"));
		}
		
		// plot
		if (!skipMaps) {
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			double maxRate = 0;
			for (FaultStats stats : subSectStats) {
				if (hasSpont)
					maxRate = Math.max(maxRate, StatUtils.max(stats.spontMeans));
				else
					maxRate = Math.max(maxRate, StatUtils.max(stats.triggeredMeans));
			}
			double fractionalRate;
			if (annualize)
				fractionalRate = 1d / Math.max(1d, Math.round(catalogCount * getConfig().getDuration()));
			else
				fractionalRate = 1d / catalogCount;
			double cptMin = Math.min(-3, Math.log10(fractionalRate));
			double cptMax = Math.max(-1, Math.ceil(Math.log10(maxRate)));
			System.out.println("CPT Range: "+cptMin+"\t"+cptMax);
			if (!Doubles.isFinite(cptMin) || !Doubles.isFinite(cptMax))
				return;
			while (cptMax <= cptMin)
				cptMax++;
			cpt = cpt.rescale(cptMin, cptMax);
			cpt.setBelowMinColor(Color.LIGHT_GRAY);
			
			FaultSystemRupSet rupSet = fss.getRupSet();
			
			List<LocationList> faults = Lists.newArrayList();
			for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++)
				faults.add(rupSet.getFaultSectionData(sectIndex).getFaultTrace());
			Preconditions.checkState(faults.size() == subSectStats.length);
			
			List<Boolean> sponts = new ArrayList<>();
			if (hasSpont)
				sponts.add(true);
			if (hasTriggered)
				sponts.add(false);
			
			Region region = new CaliforniaRegions.RELM_TESTING();
			
			plotPrefixes = HashBasedTable.create();
			
			for (boolean spont : sponts) {
				for (int i = 0; i < minMagBins.length; i++) {
					if (!hasMags[i])
						continue;
					double[] particRates = new double[subSectStats.length];
					double[] primaryRates = null;
					if (!spont)
						primaryRates = new double[subSectStats.length];
					
					for (int s=0; s<subSectStats.length; s++) {
						if (spont) {
							particRates[s] = subSectStats[s].spontMeans[i];
						} else {
							particRates[s] = subSectStats[s].triggeredMeans[i];
							primaryRates[s] = subSectStats[s].triggeredPrimaryMeans[i];
						}
					}

					String magStr;
					String prefixAdd;
					if (minMagBins[i] > 1) {
						magStr = " M>="+(float) minMagBins[i];
						prefixAdd = "_m"+(float) minMagBins[i];
					} else {
						magStr = "";
						prefixAdd = "";
					}
					String particTitle;
					if (annualize)
						particTitle = "Log10" + magStr + " Participation Rate";
					else
						particTitle = "Log10" + magStr + " Participation Exp. Num";
					
					if (!spont) {
						particTitle += ", Triggered Only";
						prefixAdd += "_triggered";
					}
					
					plotPrefixes.put(minMagBins[i], spont, prefix+"_partic"+prefixAdd);

					try {
						FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(particRates), region, outputDir,
								prefix+"_partic"+prefixAdd, false, false, particTitle);
						
						if (!spont)
							FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(primaryRates), region, outputDir,
									prefix+"_partic"+prefixAdd+"_primary", false, false, particTitle+", Primary");
					} catch (GMT_MapException | RuntimeException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Section Participation");
		lines.add(topLink); lines.add("");
		
		if (!hasAny) {
			lines.add("No supra-seismogenic ruptures in any catalog");
			return lines;
		}
		
		if (!skipMaps) {
			lines.add(topLevelHeading+"# Section Participation Plots");
			lines.add(topLink); lines.add("");
			
			TableBuilder builder = MarkdownUtils.tableBuilder();
			
			builder.initNewLine();
			builder.addColumn("Min Mag");
			if (hasSpont)
				builder.addColumn("Complete Catalog (including spontaneous)");
			if (hasTriggered) {
				builder.addColumn("Triggered Ruptures (no spontaneous)");
				builder.addColumn("Triggered Ruptures (primary aftershocks only)");
			}
			builder.finalizeLine();
			for (int i=0; i<minMagBins.length; i++) {
				double minMag = minMagBins[i];
				if (!hasMags[i])
					continue;
				builder.initNewLine();
				if (minMag == 0)
					builder.addColumn("**All Supra. Seis.**");
				else
					builder.addColumn("**M≥"+optionalDigitDF.format(minMag)+"**");
				if (hasSpont) {
					String prefix = plotPrefixes.get(minMag, true);
					builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
				}
				if (hasTriggered) {
					String prefix = plotPrefixes.get(minMag, false);
					builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
					builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+prefix+"_primary.png)");
				}
				builder.finalizeLine();
			}
			lines.addAll(builder.build());
		}
		
		for (int m = 0; m < minMagBins.length; m++) {
			double minMag = minMagBins[m];
			if (!hasMags[m])
				continue;
			CSVFile<String> parentCSV = parentCSVs.get(minMag);
			
			parentCSV.sort(2, 1, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					Double v1 = Double.parseDouble(o1);
					Double v2 = Double.parseDouble(o2);
					return v2.compareTo(v1);
				}
			});
			int numParentsWith = 0;
			for (int row=1; row<parentCSV.getNumRows(); row++)
				if (Double.parseDouble(parentCSV.get(row, 2)) > 0)
					numParentsWith++;
			
			Preconditions.checkState(numParentsWith > 0);
			
			int numParentsToPlot = Integer.min(10, numParentsWith);
			
			TableBuilder builder = MarkdownUtils.tableBuilder();
			builder.initNewLine();
			for (int col=1; col<parentCSV.getNumCols(); col++)
				builder.addColumn(parentCSV.get(0, col));
			builder.finalizeLine();

			for (int i=0; i<numParentsToPlot; i++) {
				int row = i+1;
				builder.initNewLine();
				for (int col=1; col<parentCSV.getNumCols(); col++)
					builder.addColumn(parentCSV.get(row, col));
				builder.finalizeLine();
			}
			
			String magStr;
			if (minMag == 0)
				magStr = "Supra-Seismogenic";
			else
				magStr = "M≥"+optionalDigitDF.format(minMag);
			
			lines.add("");
			lines.add(topLevelHeading+"# "+magStr+" Parent Sections Table");
			lines.add(topLink); lines.add("");
			if (numParentsWith > numParentsToPlot) {
				lines.add("*First "+numParentsToPlot+" of "+numParentsWith+" with matching ruptures shown*");
				lines.add("");
			}
			lines.addAll(builder.build());
		}
		
		return lines;
	}

}
