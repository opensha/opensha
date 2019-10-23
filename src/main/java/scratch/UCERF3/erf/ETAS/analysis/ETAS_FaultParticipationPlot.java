package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_FaultParticipationPlot extends ETAS_AbstractPlot {

	private String prefix;
	private boolean annualize;
	private boolean skipMaps;
	
	private boolean hasSpont;
	private boolean hasTriggered;
	
	private static final double[] default_calc_durations = { 1d / 365.25, 7d / 365.25, 30 / 365.25, 1d };
	// these must be a subset of above
	private static final double[] default_mpd_durations = { 7d / 365.25, 30 / 365.25, 1d };
	private static final double[] default_map_durations = { 1d };
	
	private double[] durations;
	private double[] mapDurations;
	private double[] mpdDurations;
	private long[] maxOTs;
	private double[] plotMagBins = { 0d, 6.5d, 7d, 7.5d, 8d };
	
	private FaultStats[] subSectStats;
	private Map<Integer, FaultStats> parentSectStats;
	private Map<Integer, HashSet<FaultStats>> fssIndexToStatsMap;
	
	private boolean hasAny = false;
	private boolean[] hasMags;
	private int catalogCount = 0;
	
	private Map<Double, CSVFile<String>> sectionCSVs;
	private Map<Double, CSVFile<String>> parentCSVs;
	
	private static final double mfdMinMag = 6.05;
	private static final double mfdDeltaMag = 0.1;
	private static final int mfdNumMag = 31;
	
	private EvenlyDiscretizedFunc magXIndexes;
	
	private TD_CalcThread tdCalcThread;

	protected ETAS_FaultParticipationPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, boolean annualize, boolean skipMaps) {
		super(config, launcher);
		this.prefix = prefix;
		this.annualize = annualize;
		this.skipMaps = skipMaps;
		
		this.hasTriggered = config.hasTriggers();
		this.hasSpont = config.isIncludeSpontaneous()
				|| (config.getTriggerCatalogFile() != null && config.isTreatTriggerCatalogAsSpontaneous());

		double calcDuration = config.getDuration();
		if (annualize) {
			durations = new double[] {calcDuration};
			mapDurations = durations;
			mpdDurations = durations;
		} else {
			List<Double> durList = new ArrayList<>();
			for (double dur : default_calc_durations)
				if (dur <= calcDuration)
					durList.add(dur);
			if (!durList.contains(calcDuration))
				durList.add(calcDuration);
			durations = Doubles.toArray(durList);
			durList = new ArrayList<>();
			for (double dur : default_map_durations)
				if (dur <= calcDuration)
					durList.add(dur);
			if (!durList.contains(calcDuration))
				durList.add(calcDuration);
			mapDurations = Doubles.toArray(durList);
			durList = new ArrayList<>();
			for (double dur : default_mpd_durations)
				if (dur <= calcDuration)
					durList.add(dur);
			if (!durList.contains(calcDuration))
				durList.add(calcDuration);
			mpdDurations = Doubles.toArray(durList);
		}
		maxOTs = new long[durations.length];
		for (int i=0; i<maxOTs.length; i++)
			maxOTs[i] = config.getSimulationStartTimeMillis() +
				(long)(durations[i] * ProbabilityModelsCalc.MILLISEC_PER_YEAR + 0.5);
		
		magXIndexes = new EvenlyDiscretizedFunc(mfdMinMag, mfdNumMag, mfdDeltaMag);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return hasTriggered;
	}

	@Override
	protected synchronized void doProcessCatalog(ETAS_Catalog completeCatalog,
			ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
		if (subSectStats == null) {
			System.out.println("Initializing section stats/mappings");
			// initialize
			FaultSystemRupSet rupSet = fss.getRupSet();
			
			subSectStats = new FaultStats[rupSet.getNumSections()];
			parentSectStats = new HashMap<>();
			
			fssIndexToStatsMap = new HashMap<>();
			
			for (int s=0; s<rupSet.getNumSections(); s++) {
				FaultSectionPrefData sect = rupSet.getFaultSectionData(s);
				HashSet<Integer> rupIDs = new HashSet<>(rupSet.getRupturesForSection(s));
				subSectStats[s] = new FaultStats(s, sect.getName(), rupIDs);
				mapRupturesToStat(subSectStats[s]);
				Integer parentID = sect.getParentSectionId();
				if (!parentSectStats.containsKey(parentID)) {
					HashSet<Integer> parentRups = new HashSet<>(rupSet.getRupturesForParentSection(parentID));
					FaultStats parentStats = new FaultStats(parentID, sect.getParentSectionName(), parentRups);
					mapRupturesToStat(parentStats);
					parentSectStats.put(parentID, parentStats);
				}
			}
			
			hasMags = new boolean[plotMagBins.length];
			System.out.println("DONE Initializing section stats/mappings");
			tdCalcThread = new TD_CalcThread();
			tdCalcThread.start();
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
		
		// magnitude number distributions for each duration
		// will be a total sum during processing, then will be divided by the number of
		// catalogs to turn into a mean
		private IncrementalMagFreqDist[] spontMNDs;
		private IncrementalMagFreqDist[] triggeredMNDs;
		private IncrementalMagFreqDist[] triggeredPrimaryMNDs;
		
		private EvenlyDiscretizedFunc[] spontCumulativeMNDs;
		private EvenlyDiscretizedFunc[] triggeredCumulativeMNDs;
		private EvenlyDiscretizedFunc[] triggeredPrimaryCumulativeMNDs;
		
		// these keep track of if each catalog had at least 1 rupture above the magnitude threshold
		// binned as [durationIndex][magIndex]
		private List<boolean[][]> spontAnyList;
		private List<boolean[][]> triggeredAnyList;
		
		private IncrementalMagFreqDist[] spontIncrProbs;
		private EvenlyDiscretizedFunc[] spontCumulativeProbs;
		private IncrementalMagFreqDist[] triggeredIncrProbs;
		private EvenlyDiscretizedFunc[] triggeredCumulativeProbs;
		
		private int totTriggerCount = 0;
		
		private FaultStats(int id, String name, HashSet<Integer> allRupIDs) {
			this.id = id;
			this.name = name;
			this.allRupIDs = allRupIDs;
			if (hasSpont) {
				this.spontMNDs = new IncrementalMagFreqDist[durations.length];
				for (int i=0; i<durations.length; i++)
					spontMNDs[i] = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDeltaMag);
				this.spontAnyList = new ArrayList<>();
			}
			if (hasTriggered) {
				this.triggeredMNDs = new IncrementalMagFreqDist[durations.length];
				this.triggeredPrimaryMNDs = new IncrementalMagFreqDist[durations.length];
				for (int i=0; i<durations.length; i++) {
					triggeredMNDs[i] = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDeltaMag);
					triggeredPrimaryMNDs[i] = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDeltaMag);
				}
				this.triggeredAnyList = new ArrayList<>();
			}
		}
		
		public void processCatalog(List<ETAS_EqkRupture> catalog, List<ETAS_EqkRupture> triggeredOnlyCatalog) {
			Preconditions.checkState(spontIncrProbs == null && triggeredIncrProbs == null);
			if (hasSpont)
				doProcessCatalog(catalog, spontMNDs, null, spontAnyList);
			if (hasTriggered)
				totTriggerCount += doProcessCatalog(triggeredOnlyCatalog, triggeredMNDs, triggeredPrimaryMNDs, triggeredAnyList);
		}
		
		private int doProcessCatalog(List<ETAS_EqkRupture> catalog, IncrementalMagFreqDist[] mnds,
				IncrementalMagFreqDist[] primaryMNDs, List<boolean[][]> anyList) {
			boolean[][] myAny = null;
			int myCount = 0;
			for (ETAS_EqkRupture rup : catalog) {
				int fssIndex = rup.getFSSIndex();
				if (fssIndex < 0 || !allRupIDs.contains(fssIndex))
					continue;
				// it's on this fault
				double mag = rup.getMag();
				int magIndex = mnds[0].getClosestXIndex(mag);
				if (myAny == null)
					myAny = new boolean[durations.length][mfdNumMag];
				boolean primary = rup.getGeneration() == 1;
				for (int d=0; d<durations.length; d++) {
					if (rup.getOriginTime() > maxOTs[d])
						continue;
					mnds[d].add(magIndex, 1d);
					if (primary && primaryMNDs != null)
						primaryMNDs[d].add(magIndex, 1d);
					myAny[d][magIndex] = true;
				}
				myCount++;
			}
			anyList.add(myAny);
			return myCount;
		}
		
		public void calcStats(int numCatalogs) {
			Preconditions.checkState(spontIncrProbs == null  && triggeredIncrProbs == null);
			
			double rateScalar = 1d/numCatalogs;
			
			if (hasSpont) {
				Preconditions.checkState(spontCumulativeMNDs == null);
				spontCumulativeMNDs = new EvenlyDiscretizedFunc[durations.length];
				spontIncrProbs = new IncrementalMagFreqDist[durations.length];
				spontCumulativeProbs = new EvenlyDiscretizedFunc[durations.length];
			}
			if (hasTriggered) {
				Preconditions.checkState(triggeredCumulativeMNDs == null && triggeredPrimaryCumulativeMNDs == null);
				triggeredCumulativeMNDs = new EvenlyDiscretizedFunc[durations.length];
				triggeredPrimaryCumulativeMNDs = new EvenlyDiscretizedFunc[durations.length];
				triggeredIncrProbs = new IncrementalMagFreqDist[durations.length];
				triggeredCumulativeProbs = new EvenlyDiscretizedFunc[durations.length];
			}

			for (int d=0; d<durations.length; d++) {
				double durScalar = annualize ? rateScalar/durations[d] : rateScalar;
				if (hasSpont) {
					spontMNDs[d].scale(durScalar);
					spontCumulativeMNDs[d] = spontMNDs[d].getCumRateDistWithOffset();
					calcProbs(spontAnyList, spontIncrProbs, spontCumulativeProbs, numCatalogs);
				}
				if (hasTriggered) {
					triggeredMNDs[d].scale(durScalar);
					triggeredCumulativeMNDs[d] = triggeredMNDs[d].getCumRateDistWithOffset();
					triggeredPrimaryMNDs[d].scale(durScalar);
					triggeredPrimaryCumulativeMNDs[d] = triggeredPrimaryMNDs[d].getCumRateDistWithOffset();
					calcProbs(triggeredAnyList, triggeredIncrProbs, triggeredCumulativeProbs, numCatalogs);
				}
			}
		}
		
		private void calcProbs(List<boolean[][]> anyList, IncrementalMagFreqDist[] incrProbs,
				EvenlyDiscretizedFunc[] cumulativeProbs, int numCatalogs) {
			for (int d=0; d<durations.length; d++) {
				incrProbs[d] = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDeltaMag);
				cumulativeProbs[d] = new EvenlyDiscretizedFunc(mfdMinMag-0.5*mfdDeltaMag, mfdNumMag, mfdDeltaMag);
				
				int[] incrCounts = new int[mfdNumMag];
				int[] cumulativeCounts = new int[mfdNumMag];
				
				for (boolean[][] any : anyList) {
					if (any == null)
						continue;
					int maxMagIndex = -1;
					for (int m=0; m<mfdNumMag; m++) {
						if (any[d][m]) {
							incrCounts[m]++;
							maxMagIndex = Integer.max(maxMagIndex, m);
						}
					}
					if (maxMagIndex >= 0) {
						for (int m=0; m<=maxMagIndex; m++)
							cumulativeCounts[m]++;
						double plotMag = incrProbs[d].getX(maxMagIndex);
						for (int m=0; m<plotMagBins.length; m++)
							if (plotMag >= plotMagBins[m])
								hasMags[m] = true;
					}
				}
				
				for (int m=0; m<mfdNumMag; m++) {
					incrProbs[d].set(m, (double)incrCounts[m]/(double)numCatalogs);
					cumulativeProbs[d].set(m, (double)cumulativeCounts[m]/(double)numCatalogs);
					Preconditions.checkState(cumulativeCounts[m] >= incrCounts[m]);
				}
			}
		}
	}
	
	private CSVFile<String> buildCSV(FaultStats[] stats, boolean parents, double minMag) {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		if (parents) {
			Map<String, FaultStats> namesMap = new HashMap<>();
			for (FaultStats stat : stats)
				namesMap.put(stat.name, stat);
			List<String> names = new ArrayList<>(namesMap.keySet());
			Collections.sort(names);
			stats = new FaultStats[names.size()];
			for (int i=0; i<names.size(); i++)
				stats[i] = namesMap.get(names.get(i));
			header.add("Parent ID");
			header.add("Parent Name");
		} else {
			header.add("Subsection Index");
			header.add("Subsection Name");
		}
		
		double calcDuration = getConfig().getDuration();
		
		if (annualize) {
			if (hasSpont) {
				header.add("Total Mean Annual Rate");
				for (double duration : durations)
					header.add("Total "+getTimeLabel(duration, false)+" Prob");
			}
			if (hasTriggered) {
				header.add("Triggered Mean Annual Rate");
				for (double duration : durations)
					header.add("Triggered "+getTimeLabel(duration, false)+" Prob");
				header.add("Triggered Primary Mean Annual Rate");
			}
		} else {
			if (hasSpont) {
				header.add("Total "+getTimeLabel(calcDuration, false)+" Mean Count");
				for (double duration : durations)
					header.add("Total "+getTimeLabel(duration, false)+" Prob");
			}
			if (hasTriggered) {
				header.add("Triggered "+getTimeLabel(calcDuration, false)+" Mean Count");
				for (double duration : durations)
					header.add("Triggered "+getTimeLabel(duration, false)+" Prob");
				header.add("Triggered "+getTimeLabel(calcDuration, false)+" Primary Mean Count");
			}
		}
		csv.addLine(header);
		
		int magIndex = getMFD_magIndex(minMag);
		
		for (FaultStats stat : stats) {
			List<String> line = new ArrayList<>();
			line.add(stat.id+"");
			line.add(stat.name);
			if (hasSpont) {
				line.add((float)stat.spontCumulativeMNDs[durations.length-1].getY(magIndex)+"");
				for (int d=0; d<durations.length; d++)
					line.add((float)stat.spontCumulativeProbs[d].getY(magIndex)+"");
			}
			if (hasTriggered) {
				line.add((float)stat.triggeredCumulativeMNDs[durations.length-1].getY(magIndex)+"");
				for (int d=0; d<durations.length; d++)
					line.add((float)stat.triggeredCumulativeProbs[d].getY(magIndex)+"");
				line.add((float)stat.triggeredPrimaryCumulativeMNDs[durations.length-1].getY(magIndex)+"");
			}
			csv.addLine(line);
		}
		
		return csv;
	}
	
	private int getMFD_magIndex(double minMag) {
		int minIndex = mfdNumMag;
		for (int i=mfdNumMag; --i>=0;) {
			if ((float)magXIndexes.getX(i) >= (float)minMag)
				minIndex = i;
			else
				break;
		}
		Preconditions.checkState(minIndex < mfdNumMag);
		return minIndex;
	}
	
	// duration, spontaneous, mags
	private Table<Double, Boolean, String[]> mapPlotPrefixes;
	// name, duration, prefix
	private Table<String, Double, String> faultMFDPrefixes;

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		if (!hasAny)
			return null;
		// calculate all stats
		for (FaultStats stats : subSectStats)
			stats.calcStats(catalogCount);
		for (FaultStats stats : parentSectStats.values())
			stats.calcStats(catalogCount);
		
		// build CSVs
		writeCSVs(outputDir);
		
		// plot
		if (!skipMaps)
			writeMaps(outputDir, fss);
		
		writeFaultMFDs(outputDir, fss);
		return null;
	}

	private void writeMaps(File outputDir, FaultSystemSolution fss) throws IOException {
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
		double maxRate = 0;
		int minPlotMagIndex = getMFD_magIndex(StatUtils.min(plotMagBins));
		int maxDurationIndex = 0;
		for (int d=1; d<mapDurations.length; d++)
			if (mapDurations[d] > mapDurations[maxDurationIndex])
				maxDurationIndex = d;
		for (FaultStats stats : subSectStats) {
			if (hasSpont)
				maxRate = Math.max(maxRate, stats.spontCumulativeMNDs[maxDurationIndex].getY(minPlotMagIndex));
			else
				maxRate = Math.max(maxRate, stats.triggeredCumulativeMNDs[maxDurationIndex].getY(minPlotMagIndex));
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
		
		mapPlotPrefixes = HashBasedTable.create();
		
		for (int d=0; d<mapDurations.length; d++) {
			for (boolean spont : sponts) {
				mapPlotPrefixes.put(mapDurations[d], spont, new String[plotMagBins.length]);
				for (int p=0; p<plotMagBins.length; p++) {
					if (!hasMags[p])
						continue;
					int magIndex = getMFD_magIndex(plotMagBins[p]);
					double[] particRates = new double[subSectStats.length];
					double[] primaryRates = null;
					if (!spont && d == maxDurationIndex)
						primaryRates = new double[subSectStats.length];
					
					for (int s=0; s<subSectStats.length; s++) {
						if (spont) {
							particRates[s] = subSectStats[s].spontCumulativeMNDs[d].getY(magIndex);
						} else {
							particRates[s] = subSectStats[s].triggeredCumulativeMNDs[d].getY(magIndex);
							if (primaryRates != null)
								primaryRates[s] = subSectStats[s].triggeredPrimaryCumulativeMNDs[d].getY(magIndex);
						}
					}

					String magStr;
					String prefixAdd;
					if (plotMagBins[p] > 1) {
						magStr = " M>="+(float)plotMagBins[p];
						prefixAdd = "_m"+(float)plotMagBins[p];
					} else {
						magStr = "";
						prefixAdd = "";
					}
					String particTitle;
					if (annualize) {
						particTitle = "Log10" + magStr + " Participation Rate";
					} else {
						if (mapDurations.length > 1) {
							prefixAdd = "_"+getTimeShortLabel(mapDurations[d]).replaceAll(" ", "")+prefixAdd;
							particTitle = "Log10 "+getTimeShortLabel(mapDurations[d])+magStr+" Participation Exp. Num";
						} else {
							particTitle = "Log10"+magStr+" Participation Exp. Num";
						}
					}
					
					if (!spont) {
						particTitle += ", Triggered Only";
						prefixAdd += "_triggered";
					}
					
					mapPlotPrefixes.get(mapDurations[d], spont)[p] = prefix+"_partic"+prefixAdd;

					try {
						FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(particRates), region, outputDir,
								prefix+"_partic"+prefixAdd, false, false, particTitle);
						
						if (!spont && primaryRates != null)
							FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(primaryRates), region, outputDir,
									prefix+"_partic"+prefixAdd+"_primary", false, false, particTitle+", Primary");
					} catch (GMT_MapException | RuntimeException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
	}

	private void writeCSVs(File outputDir) throws IOException {
		sectionCSVs = new HashMap<>();
		parentCSVs = new HashMap<>();
		FaultStats[] parentStatsArray = new ArrayList<>(parentSectStats.values()).toArray(new FaultStats[0]);
		for (int p=0; p<plotMagBins.length; p++) {
			double plotMinMag = plotMagBins[p];
			if (!hasMags[p])
				continue;
			String magStr;
			if (plotMinMag == 0)
				magStr = "supra_seis";
			else
				magStr = "m"+optionalDigitDF.format(plotMinMag);
			
//			boolean hasMag = false;
//			for (FaultStats stats : parentStatsArray)
//				hasMag = hasMag || (hasSpont && stats.spontMeans[m] > 0) || (hasTriggered && stats.triggeredMeans[m] > 0);
//			if (!hasMag)
//				continue;
			
			CSVFile<String> sectionCSV = buildCSV(subSectStats, false, plotMinMag);
			sectionCSVs.put(plotMinMag, sectionCSV);
			sectionCSV.writeToFile(new File(outputDir, prefix+"_"+magStr+"_sub_sects.csv"));
			
			CSVFile<String> parentCSV = buildCSV(parentStatsArray, true, plotMinMag);
			parentCSVs.put(plotMinMag, parentCSV);
			parentCSV.writeToFile(new File(outputDir, prefix+"_"+magStr+"_parent_sects.csv"));
		}
	}
	
	private class TD_CalcThread extends Thread {
		
		Table<Integer, Double, EvenlyDiscretizedFunc> tdFuncs;

		@Override
		public void run() {
			tdFuncs = HashBasedTable.create();
			System.out.print("Calculating U3-TD parent section MFDs in background...");
			
			FaultSystemSolutionERF erf = (FaultSystemSolutionERF)getLauncher().checkOutERF();
			for (double duration : mpdDurations) {
				erf.getTimeSpan().setDuration(duration);
				erf.updateForecast();
				Map<Integer, EvenlyDiscretizedFunc> mfds = FaultSysSolutionERF_Calc.calcParentSectSupraSeisMagProbDists(
						erf, mfdMinMag-0.5*mfdDeltaMag, mfdNumMag, mfdDeltaMag);
				for (Integer parentID : mfds.keySet())
					tdFuncs.put(parentID, duration, mfds.get(parentID));
			}
			
			getLauncher().checkInERF(erf);
			System.out.println("Finished background thread calculating U3-TD parent section MFDs");
		}
		
	}
	
	private void writeFaultMFDs(File outputDir, FaultSystemSolution fss) throws IOException {
		faultMFDPrefixes = HashBasedTable.create();
		File subDir = new File(outputDir, "parent_sect_mpds");
		Preconditions.checkState(subDir.exists() || subDir.mkdir());
		
		try {
			tdCalcThread.join();
		} catch (InterruptedException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		Table<Integer, Double, EvenlyDiscretizedFunc> tdFuncs = tdCalcThread.tdFuncs;
		
		List<String> lines = new ArrayList<>();
		lines.add("# Parent Section Magnitude-Probability Distributions");
		lines.add("");
		Map<String, FaultStats> nameStatsMap = new HashMap<>();
		for (FaultStats stats : parentSectStats.values())
			nameStatsMap.put(stats.name, stats);
		List<String> names = new ArrayList<>();
		if (annualize || !hasTriggered) {
			// everything, sorted by name
			names = new ArrayList<>(nameStatsMap.keySet());
			Collections.sort(names);
		} else {
			// only triggered, sorted by trigger rate
			Map<FaultStats, Integer> triggerMap = new HashMap<>();
			for (FaultStats stats : parentSectStats.values())
				if (stats.totTriggerCount > 0)
					triggerMap.put(stats, stats.totTriggerCount);
			names = new ArrayList<>();
			for (FaultStats stats : ComparablePairing.getSortedData(triggerMap))
				names.add(stats.name);
			Collections.reverse(names);
			
			lines.add("Only fault sections with at least one triggered aftershock are plotted. Sections are sorted by total "
					+ "supraseismogenic trigger rate (decreasing)");
			lines.add("");
		}
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		System.out.print("Writing U3-TD parent section MFDs...");
		for (String name : names) {
			lines.add("## "+name);
			lines.add(topLink); lines.add("");
			FaultStats stats = nameStatsMap.get(name);
			String prefix = stats.name.replaceAll("\\W+", "_");
			while (prefix.contains("__"))
				prefix = prefix.replaceAll("__", "_");
			if (prefix.startsWith("_"))
				prefix = prefix.substring(1);
			if (prefix.endsWith("_"))
				prefix = prefix.substring(0, prefix.length()-1);
			writeFaultMFD(stats, subDir, prefix, fss, tdFuncs, lines);
			lines.add("");
		}
		System.out.println("DONE");
		
		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));
		lines.addAll(tocIndex, tocLines);
		
		MarkdownUtils.writeReadmeAndHTML(lines, subDir);
	}
	
	private void writeFaultMFD(FaultStats stats, File outputDir, String prefix, FaultSystemSolution fss,
			Table<Integer, Double, EvenlyDiscretizedFunc> tdFuncs, List<String> lines) throws IOException {
		int parentSectID = stats.id;
		
		// calculate UCERF3-TI
		IncrementalMagFreqDist tiRateFunc = fss.calcParticipationMFD_forParentSect(parentSectID,
				mfdMinMag, magXIndexes.getMaxX(), mfdNumMag);
		EvenlyDiscretizedFunc tdCumulativeRateFunc = tiRateFunc.getCumRateDistWithOffset();
		
		TableBuilder plotTable = MarkdownUtils.tableBuilder();
		
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.add("Magnitude");
		if (annualize) {
			Preconditions.checkState(mpdDurations.length == 1);
			Preconditions.checkState(!hasTriggered);
			header.add("UCERF3-TI Prob");
			header.add("UCERF3-TD Prob");
			header.add("UCERF3-ETAS Prob");
		} else {
			plotTable.initNewLine();
			for (int i=0; i<mpdDurations.length; i++) {
				double duration = mpdDurations[i];
				plotTable.addColumn(getTimeLabel(duration, false));
				String time = getTimeShortLabel(duration);
				header.add(time+" TI Prob");
				header.add(time+" TD Prob");
				header.add(time+" ETAS Prob");
				header.add(time+" ETAS/TD Gain");
				if (hasTriggered) {
					if (hasSpont)
						header.add(time+" ETAS Triggered+TD");
					header.add(time+" ETAS Triggered Only");
				}
			}
			plotTable.finalizeLine();
		}
		csv.addLine(header);
		List<DiscretizedFunc> csvFuncs = new ArrayList<>();
		
		plotTable.initNewLine();
		for (double duration : mpdDurations) {
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			// TI: poisson probabilities
			EvenlyDiscretizedFunc myTIFunc = new EvenlyDiscretizedFunc(tdCumulativeRateFunc.getMinX(),
					tdCumulativeRateFunc.getMaxX(), tdCumulativeRateFunc.size());
			for (int i=0; i<myTIFunc.size(); i++)
				myTIFunc.set(i, 1-Math.exp(-tdCumulativeRateFunc.getY(i)*duration));
			myTIFunc.setName("UCERF3-TI");
			funcs.add(myTIFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			csvFuncs.add(myTIFunc);
			
			// TD
			EvenlyDiscretizedFunc myTDFunc = tdFuncs.get(parentSectID, duration);
			myTDFunc.setName("UCERF3-TD");
			funcs.add(myTDFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			csvFuncs.add(myTDFunc);
			
			int durationIndex = -1;
			for (int i=0; i<durations.length; i++) {
				double calcDuration = durations[i];
				if (duration == calcDuration)
					durationIndex = i;
			}
			Preconditions.checkState(durationIndex >= 0);
			
			// ETAS
			if (hasSpont) {
				EvenlyDiscretizedFunc totFunc = stats.spontCumulativeProbs[durationIndex];
				totFunc.setName("UCERF3-ETAS");
				funcs.add(totFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
				csvFuncs.add(totFunc);
				
				if (!annualize) {
					EvenlyDiscretizedFunc gainFunc = new EvenlyDiscretizedFunc(
							totFunc.getMinX(), totFunc.getMaxX(), totFunc.size());
					for (int i=0; i<gainFunc.size(); i++)
						gainFunc.set(i, totFunc.getY(i)/myTDFunc.getY(i));
					csvFuncs.add(gainFunc);
				}
			}
			
			if (hasTriggered) {
				EvenlyDiscretizedFunc triggeredOnlyFunc = stats.triggeredCumulativeProbs[durationIndex];
				
				EvenlyDiscretizedFunc triggeredPlusTDFunc = new EvenlyDiscretizedFunc(triggeredOnlyFunc.getMinX(),
						triggeredOnlyFunc.getMaxX(), triggeredOnlyFunc.size());
				for (int i=0; i<triggeredOnlyFunc.size(); i++) {
					Preconditions.checkState(myTDFunc.getX(i) == triggeredOnlyFunc.getX(i));
					double tdProb = myTDFunc.getY(i);
					double triggeredProb = triggeredOnlyFunc.getY(i);
					double combProb = FaultSysSolutionERF_Calc.calcSummedProbs(tdProb, triggeredProb);
					triggeredPlusTDFunc.set(i, combProb);
				}
				
				if (hasSpont) {
					triggeredPlusTDFunc.setName("ETAS Triggered+TD");
					funcs.add(triggeredPlusTDFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED.darker()));
					csvFuncs.add(triggeredPlusTDFunc);
				} else {
					triggeredPlusTDFunc.setName("UCERF3-ETAS");
					funcs.add(triggeredPlusTDFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
					csvFuncs.add(triggeredPlusTDFunc);
					
					EvenlyDiscretizedFunc gainFunc = new EvenlyDiscretizedFunc(
							triggeredPlusTDFunc.getMinX(), triggeredPlusTDFunc.getMaxX(), triggeredPlusTDFunc.size());
					for (int i=0; i<gainFunc.size(); i++)
						gainFunc.set(i, triggeredPlusTDFunc.getY(i)/myTDFunc.getY(i));
					csvFuncs.add(gainFunc);
				}
				triggeredOnlyFunc.setName("ETAS Triggered Only");
				funcs.add(triggeredOnlyFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
				csvFuncs.add(triggeredOnlyFunc);
			}
			
			String yAxisLabel = annualize ? "Annual Probability" : getTimeLabel(duration, false)+" Probability";
			PlotSpec spec = new PlotSpec(funcs, chars, stats.name, "Magnitude", yAxisLabel);
			spec.setLegendVisible(true);
			
			Range xRange = new Range(myTDFunc.getMinX(), myTDFunc.getMaxX());
			Range yRange = new Range(1e-8, 1);
			
			HeadlessGraphPanel gp = buildGraphPanel();

			gp.drawGraphPanel(spec, false, true, xRange, yRange);
			gp.getChartPanel().setSize(800, 600);
			
			String myPrefix = annualize ? prefix : prefix+"_"+getTimeShortLabel(duration).replaceAll(" ", "");
			gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
//			gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
			plotTable.addColumn("![MPD]("+myPrefix+".png)");
			faultMFDPrefixes.put(stats.name, duration, myPrefix);
		}
		plotTable.finalizeLine();
		
		Preconditions.checkState(csvFuncs.size() == header.size()-1, "Header len=%s, funcs=%s", header.size(), csvFuncs.size());
		DiscretizedFunc xVals = csvFuncs.get(0);
		for (int i=0; i<xVals.size(); i++) {
			List<String> line = new ArrayList<>();
			if (xVals.getY(i) == 0d)
				break;
			line.add((float)xVals.getX(i)+"");
			for (DiscretizedFunc func : csvFuncs)
				line.add((float)func.getY(i)+"");
			csv.addLine(line);
		}
		csv.writeToFile(new File(outputDir, prefix+".csv"));
		
		lines.addAll(plotTable.build());
		lines.add("");
		lines.addAll(MarkdownUtils.tableFromCSV(csv, false).build());
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
			for (int d=0; d<mapDurations.length; d++) {
				String label = annualize ? "" : getTimeShortLabel(mapDurations[d])+" ";
				if (hasSpont)
					builder.addColumn(label+"Complete Catalog (including spontaneous)");
				if (hasTriggered) {
					builder.addColumn(label+"Triggered Ruptures (no spontaneous)");
				}
			}
			if (hasTriggered) {
				String titlePprefix = annualize ? "" : getTimeShortLabel(StatUtils.max(mapDurations))+" ";
				builder.addColumn(titlePprefix+"Triggered Ruptures (primary aftershocks only)");
			}
			
			builder.finalizeLine();
			for (int i=0; i<plotMagBins.length; i++) {
				double minMag = plotMagBins[i];
				if (!hasMags[i])
					continue;
				builder.initNewLine();
				if (minMag == 0)
					builder.addColumn("**All Supra. Seis.**");
				else
					builder.addColumn("**M&ge;"+optionalDigitDF.format(minMag)+"**");
				String maxTriggeredPrefix = null;
				for (int d=0; d<mapDurations.length; d++) {
					if (hasSpont) {
						String prefix = mapPlotPrefixes.get(mapDurations[d], true)[i];
						builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
					}
					if (hasTriggered) {
						String prefix = mapPlotPrefixes.get(mapDurations[d], false)[i];
						if (mapDurations[d] == StatUtils.max(mapDurations))
							maxTriggeredPrefix = prefix;
						builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
					}
				}
				if (hasTriggered)
					builder.addColumn("![Participation Plot]("+relativePathToOutputDir+"/"+maxTriggeredPrefix+"_primary.png)");
				builder.finalizeLine();
			}
			lines.addAll(builder.build());
		}
		
		for (int m=0; m<plotMagBins.length; m++) {
			double minMag = plotMagBins[m];
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
		
		if (!annualize && hasTriggered) {
			lines.add("");
			lines.add(topLevelHeading+"# Fault Magnitude-Probability Distributions");
			lines.add(topLink); lines.add("");
			
			Map<FaultStats, Integer> triggerParentStats = new HashMap<>();
			for (FaultStats stats : parentSectStats.values()) {
				if (stats.totTriggerCount > 0)
					triggerParentStats.put(stats, stats.totTriggerCount);
			}
			List<FaultStats> sortedParents = ComparablePairing.getSortedData(triggerParentStats);
			Collections.reverse(sortedParents);
			
			int numParentsToPlot = Integer.min(5, sortedParents.size());
			
			lines.add("The first "+numParentsToPlot+" sections (sorted by trigger rate) are plotted below. All fault MPDs are "
					+ "available [here]("+relativePathToOutputDir+"/parent_sect_mpds/README.md)");
			
			if (numParentsToPlot > 0) {
				lines.add("");
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				for (double duration : mpdDurations)
					table.addColumn(getTimeLabel(duration, false));
				table.finalizeLine();
				for (int i=0; i<numParentsToPlot; i++) {
					FaultStats stats = sortedParents.get(i);
					table.initNewLine();
					for (double duration : mpdDurations)
						table.addColumn("![MPD]("+relativePathToOutputDir+"/parent_sect_mpds/"
								+faultMFDPrefixes.get(stats.name, duration)+".png)");
					table.finalizeLine();
				}
				lines.addAll(table.build());
			}
		}
		
		return lines;
	}
	
	public static void main(String[] args) {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-full_td-1000yr");
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-no_ert-1000yr");
//				+ "2019_07_04-SearlesValleyM64-includeSpont-full_td-10yr");
//				+ "2019-06-05_M7.1_SearlesValley_Sequence_UpdatedMw_and_depth");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-start-noon");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
				+ "2019_07_11-ComCatM7p1_ci38457511_FiniteSurface-noSpont-full_td-scale1.14");
		File configFile = new File(simDir, "config.json");
		
		try {
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			int maxNumCatalogs = 1000;
			
			ETAS_FaultParticipationPlot plot = new ETAS_FaultParticipationPlot(config, launcher, "fault_participation",
					false, true);
			File outputDir = new File(simDir, "plots");
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			FaultSystemSolution fss = launcher.checkOutFSS();
			
			File inputFile = SimulationMarkdownGenerator.locateInputFile(config);
			int processed = 0;
			for (ETAS_Catalog catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(inputFile, 0d)) {
				if (processed % 1000 == 0)
					System.out.println("Catalog "+processed);
				plot.processCatalog(catalog, fss);
				processed++;
				if (maxNumCatalogs > 0 && processed == maxNumCatalogs)
					break;
			}
			
			plot.finalize(outputDir, launcher.checkOutFSS());
			
			System.exit(0);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
