package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
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

import org.dom4j.DocumentException;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.RupSetPlausibilityResult;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeSlipRateProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.DirectPathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MultiDirectionalPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class RupSetFilterComparePageGen {

	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		String inputName = "RSQSim 4983, SectArea=0.5";
		File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5.zip");
		File distAzCache = new File(rupSetsDir, "fm3_1_dist_az_cache.csv");
		File altFiltersFile = new File(rupSetsDir, "u3_az_cff_cmls.json");
		String altName = "UCERF3";
		
//		String inputName = "Too big!";
//		File inputFile = new File(rupSetsDir, "fm3_1_plausible10km_slipP0.1incr_cff0.67IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
//		File distAzCache = new File(rupSetsDir, "fm3_1_dist_az_cache.csv");
		
		File markdownDir = new File("/home/kevin/markdown/rupture-sets");
		File myMDdir = new File(markdownDir, inputFile.getName().replace(".zip", ""));
		Preconditions.checkState(myMDdir.exists() || myMDdir.mkdir());
		File outputDir = new File(myMDdir, "plausibility_filter_debug");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		String[] skipFaultNames = {
				"Great Valley"
		};
		
		double maxDist = 10d;
		double rupDebugMinMag = 8d;
		int maxRupDebugs = 20;
		
		List<ParameterizedFilterWrapper> wrappers = new ArrayList<>();
		List<Float> minVals = new ArrayList<>();
		List<Float> maxVals = new ArrayList<>();
		List<Float> prefVals = new ArrayList<>();
		List<String> names = new ArrayList<>();
		
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(inputFile);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		if (distAzCache.exists()) {
			System.out.println("Loading dist/az cache from "+distAzCache.getAbsolutePath());
			distAzCalc.loadCacheFile(distAzCache);
		}
		
		List<ClusterRupture> rups = rupSet.getClusterRuptures();
		RuptureConnectionSearch connSearch;
		if (rupSet.getPlausibilityConfiguration() == null)
			connSearch = new RuptureConnectionSearch(rupSet, distAzCalc, 100d, false);
		else
			connSearch = new RuptureConnectionSearch(rupSet, distAzCalc,
					rupSet.getPlausibilityConfiguration().getConnectionStrategy().getMaxJumpDist(), false);
		if (rups == null) {
			rupSet.buildClusterRups(connSearch);
			rups = rupSet.getClusterRuptures();
		}
		HashSet<Jump> allJumps = new HashSet<>();
		double minRupMag = rupSet.getMinMag();
		for (ClusterRupture rup : rups) {
			for (Jump jump : rup.getJumpsIterable())
				allJumps.add(jump);
		}
		
//		ClusterRupture testRup = rups.get(43472);
//		System.out.println("testRup has "+testRup.getTotalNumSects()+" sects: "+testRup);
//		List<ClusterRupture> testRemovals = getWithNEndSectsRemoved(testRup, 2);
//		System.out.println("Found "+testRemovals.size()+" removals:");
//		for (ClusterRupture r : testRemovals)
//			System.out.println("\t"+r);
//		System.exit(0);
		
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
		if (stiffnessCacheFile.exists())
			stiffnessCache.loadCacheFile(stiffnessCacheFile);
		// common aggregators
		AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		AggregatedStiffnessCalculator fractRpatchPosAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.SUM, AggregationMethod.PASSTHROUGH, AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE);
		AggregatedStiffnessCalculator fractIntsPositive = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		
		double[] minMags;
		if ((float)minRupMag >= 7d)
			minMags = new double[] { 7d, 7.5d, 8d };
		if ((float)minRupMag >= 6.5d)
			minMags = new double[] { 6.5d, 7d, 7.5d, 8d };
		else
			minMags = new double[] { 0d, 6.5d, 7d, 7.5d, 8d };
		
		CPT magIndexCPT = new CPT(0, minMags.length-1, Color.BLUE, Color.RED, Color.BLACK);
		
		ClusterConnectionStrategy connStrat;
		if (rupSet.getPlausibilityConfiguration() == null)
			connStrat = RupSetDiagnosticsPageGen.buildDefaultConnStrat(null, distAzCalc, allJumps, maxDist);
		else
			connStrat = rupSet.getPlausibilityConfiguration().getConnectionStrategy();
		
		List<PlausibilityFilter> altFilters = PlausibilityConfiguration.readFiltersJSON(altFiltersFile, connStrat, distAzCalc);

		// slip path
		wrappers.add(new ParameterizedFilterWrapper() {
			
			@Override
			public PlausibilityFilter get(Float value) {
				return new CumulativeProbabilityFilter(value, new RelativeSlipRateProb(connStrat, true));
			}
		});
		minVals.add(0.01f);
		maxVals.add(0.2f);
		prefVals.add(0.05f);
		names.add("Slip Probability");
		
		// fraction of interactions
		wrappers.add(new ParameterizedFilterWrapper() {
			
			@Override
			public PlausibilityFilter get(Float value) {
				return new NetRuptureCoulombFilter(fractIntsPositive, value);
			}
		});
		minVals.add(0.5f);
		maxVals.add(0.95f);
		prefVals.add(0.75f);
		names.add("Fraction of Interactions");
		
//		// fraction of interactions
//		wrappers.add(new ParameterizedFilterWrapper() {
//			
//			@Override
//			public PlausibilityFilter get(Float value) {
//				return new DirectPathPlausibilityFilter(connStrat);
//			}
//		});
//		minVals.add(null);
//		maxVals.add(null);
//		prefVals.add(null);
//		names.add("No Indirect Paths");
		
		/*
		 * Path filters
		 */
		// sect ratio
		wrappers.add(new ParameterizedFilterWrapper() {
			
			@Override
			public PlausibilityFilter get(Float value) {
				return new PathPlausibilityFilter(new CumulativeProbPathEvaluator(value, PlausibilityResult.FAIL_HARD_STOP,
						new CoulombSectRatioProb(sumAgg, 2, true, (float)maxDist, distAzCalc)));
			}
		});
		minVals.add(0.1f);
		maxVals.add(1.0f);
		prefVals.add(0.5f);
		names.add("CFF Ratio");
		
		// cff relative prob
		wrappers.add(new ParameterizedFilterWrapper() {
			
			@Override
			public PlausibilityFilter get(Float value) {
				return new PathPlausibilityFilter(new CumulativeProbPathEvaluator(value, PlausibilityResult.FAIL_HARD_STOP,
						new RelativeCoulombProb(sumAgg, connStrat, false, true, true, (float)maxDist, distAzCalc)));
			}
		});
		minVals.add(0.01f);
		maxVals.add(0.1f);
		prefVals.add(0.02f);
		names.add("CFF Probability");
		
		boolean hasSplays = false;
		for (ClusterRupture rup : rups) {
			if (!rup.splays.isEmpty()) {
				hasSplays = true;
				break;
			}
		}
		
		List<DiscretizedFunc> xValsList = new ArrayList<>();
		
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
		
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		List<List<Future<PlausibilityResult[]>>> futures = new ArrayList<>();
		for (int i=0; i<wrappers.size(); i++) {
			Float min = minVals.get(i);
			Float max = maxVals.get(i);
			Float pref = prefVals.get(i);
			ParameterizedFilterWrapper wrapper = wrappers.get(i);
			
			ArbitrarilyDiscretizedFunc xVals;
			List<Future<PlausibilityResult[]>> myFutures = new ArrayList<>();;
			if (pref == null) {
				// no value
				xVals = null;
				PlausibilityFilter filter = wrapper.get(null);
				if (filter.isDirectional(false) || (hasSplays && filter.isDirectional(true)))
					filter = new MultiDirectionalPlausibilityFilter(filter, connSearch, !filter.isDirectional(false));
				
				myFutures.add(exec.submit(new CalcCallable(filter, rups)));
			} else {
				xVals = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : new EvenlyDiscretizedFunc(min, max, 20))
					xVals.set(pt);
				xVals.set(pref, 0d);
				
				
				for (int x=0; x<xVals.size(); x++) {
					double thresh = xVals.getX(x);
					
					PlausibilityFilter filter = wrapper.get((float)thresh);
					if (filter.isDirectional(false) || (hasSplays && filter.isDirectional(true)))
						filter = new MultiDirectionalPlausibilityFilter(filter, connSearch, !filter.isDirectional(false));
					
					myFutures.add(exec.submit(new CalcCallable(filter, rups)));
				}
			}
			
			xValsList.add(xVals);
			futures.add(myFutures);
		}
		
		List<RupSetPlausibilityResult> combResults = new ArrayList<>();
		List<String> combNames = new ArrayList<>();
		
		Boolean[] maxs = { null, false, true };
		
		List<PlausibilityFilter> combinedPrefFilters = null;
		
		for (int i=0; i<wrappers.size(); i++) {
			for (Boolean isMax : maxs) {
				if (isMax == null && i > 0)
					continue;
				Float value;
				if (isMax == null)
					value = prefVals.get(i);
				else if (isMax)
					value = maxVals.get(i);
				else
					value = minVals.get(i);
				List<PlausibilityFilter> filters = new ArrayList<>();
				// add all others
				for (int k=0; k<wrappers.size(); k++) {
					if (k == i)
						filters.add(wrappers.get(k).get(value));
					else
						filters.add(wrappers.get(k).get(prefVals.get(k)));
				}
				List<PlausibilityFilter> finalFilters = new ArrayList<>();
				List<NucleationClusterEvaluator> pathEvals = new ArrayList<>();
				for (PlausibilityFilter filter : filters) {
					if (filter instanceof PathPlausibilityFilter)
						for (NucleationClusterEvaluator eval : ((PathPlausibilityFilter)filter).getEvaluators())
							pathEvals.add(eval);
					else
						finalFilters.add(filter);
				}
				if (!pathEvals.isEmpty())
					finalFilters.add(new PathPlausibilityFilter(pathEvals.toArray(new NucleationClusterEvaluator[0])));
				combResults.add(RupSetDiagnosticsPageGen.testRupSetPlausibility(
						rups, finalFilters, rupSet.getPlausibilityConfiguration(), connSearch, exec));
				if (isMax == null) {
					combNames.add("Preferred Values");
					combinedPrefFilters = new ArrayList<>(filters);
					if (!pathEvals.isEmpty())
						combinedPrefFilters.add(new PathPlausibilityFilter(pathEvals.toArray(new NucleationClusterEvaluator[0])));
				} else if (isMax) {
					combNames.add(names.get(i)+" Max");
				} else {
					combNames.add(names.get(i)+" Min");
				}
			}
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("# "+inputName+" Plausibility Comparisons");
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		
		DecimalFormat pDF = new DecimalFormat("0.00%");
		
		for (int i=0; i<wrappers.size(); i++) {
			String name = names.get(i);
			
			lines.add("## "+name);
			lines.add(topLink); lines.add("");
			
			DiscretizedFunc xVals = xValsList.get(i);
			
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			for (int m=0; m<minMags.length; m++) {
				double minMag = minMags[m];
				ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
				if (minMag > 0d)
					func.setName("M≥"+(float)minMag);
				else
					func.setName("Supra-Seismogenic");
				funcs.add(func);
				Color color = magIndexCPT.getColor((float)m);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
			}
			
			List<Future<PlausibilityResult[]>> myFutures = futures.get(i);
			
			Range xRange;
			if (xVals == null) {
				xRange = new Range(0d, 1d);
				PlausibilityResult[] results;
				try {
					results = myFutures.get(0).get();
				} catch (InterruptedException | ExecutionException e) {
					exec.shutdown();
					throw ExceptionUtils.asRuntimeException(e);
				}
				
				int passes = 0;
				for (PlausibilityResult result : results)
					if (result.isPass())
						passes++;
				System.out.println("Testing "+name);
				System.out.println(passes+"/"+rups.size()+" = "+pDF.format((double)passes/(double)rups.size())+" passed");
				
				for (int m=0; m<minMags.length; m++) {
					double minMag = minMags[m];
					int magPasses = 0;
					int magRups = 0;
					for (int r=0; r<rups.size(); r++) {
						if (rupSet.getMagForRup(r) >= minMag) {
							magRups++;
							if (results[r].isPass())
								magPasses++;
						}
					}
					double val = 100d*(magRups - magPasses)/(double)magRups;
					funcs.get(m).set(0d, val);
					funcs.get(m).set(1d, val);
				}
			} else {
				Float min = minVals.get(i);
				Float max = maxVals.get(i);
				
				for (int x=0; x<xVals.size(); x++) {
					PlausibilityResult[] results;
					try {
						results = myFutures.get(x).get();
					} catch (InterruptedException | ExecutionException e) {
						exec.shutdown();
						throw ExceptionUtils.asRuntimeException(e);
					}
					double thresh = xVals.getX(x);
					
					int passes = 0;
					for (PlausibilityResult result : results)
						if (result.isPass())
							passes++;
					System.out.println("Testing "+name+": "+thresh);
					System.out.println(passes+"/"+rups.size()+" = "+pDF.format((double)passes/(double)rups.size())+" passed");
					
					for (int m=0; m<minMags.length; m++) {
						double minMag = minMags[m];
						int magPasses = 0;
						int magRups = 0;
						for (int r=0; r<rups.size(); r++) {
							if (rupSet.getMagForRup(r) >= minMag) {
								magRups++;
								if (results[r].isPass())
									magPasses++;
							}
						}
						funcs.get(m).set(thresh, 100d*(magRups - magPasses)/(double)magRups);
					}
				}
				xRange = new Range(min, max);
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, name, "Threshold", "% Failed");
			spec.setLegendVisible(true);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setBackgroundColor(Color.WHITE);
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(20);
			gp.setPlotLabelFontSize(21);
			gp.setLegendFontSize(22);
			
			double maxY = 20d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 50d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 100d;
			
			gp.drawGraphPanel(spec, false, false, xRange, new Range(0d, maxY));
			gp.getChartPanel().setSize(1000, 800);
			
			String prefix = name.replaceAll("\\W+", "");
			File pngFile = new File(resourcesDir, prefix+".png");
			File pdfFile = new File(resourcesDir, prefix+".pdf");
			gp.saveAsPNG(pngFile.getAbsolutePath());
			gp.saveAsPDF(pdfFile.getAbsolutePath());
			
			lines.add("![plot](resources/"+pngFile.getName()+")");
			lines.add("");
		}
		
		RupSetPlausibilityResult altResult = null;
		if (altFilters != null) {
			System.out.println("Calculating alt plausibility...");
			altResult = RupSetDiagnosticsPageGen.testRupSetPlausibility(
					rups, altFilters, rupSet.getPlausibilityConfiguration(), connSearch, exec);
			System.out.println("done");
		}
		
		EvenlyDiscretizedFunc prefMagFunc = null;
		
		// now overall values
		lines.add("## Combined Plausibility");
		lines.add(topLink); lines.add("");
		for (int r=0; r<combResults.size(); r++) {
			RupSetPlausibilityResult result = combResults.get(r);
			lines.add("### Combined Plausibility: "+combNames.get(r));
			lines.add(topLink); lines.add("");
			lines.add("*Filters:*");
			lines.add("");
			for (PlausibilityFilter filter : result.filters)
				lines.add("* "+filter.getName());
			lines.add("");
			String prefix = "plausibility_"+r;
			File plot = RupSetDiagnosticsPageGen.plotRupSetPlausibility(result, resourcesDir, prefix, "Plausibility");
			
			lines.add("![plot](resources/"+plot.getName()+")");
			lines.add("");
			TableBuilder table = MarkdownUtils.tableBuilder();
			for (double minMag : minMags) {
				RupSetPlausibilityResult magResult = result.filterByMag(rupSet, minMag);
				if (magResult == null)
					continue;
				String magPrefix = prefix+"_m"+(float)minMag;
				String title = "M≥"+(float)minMag+" Comparison";
				File file = RupSetDiagnosticsPageGen.plotRupSetPlausibility(magResult, resourcesDir, magPrefix, title);
				table.addColumn("![M>="+(float)minMag+"]("+resourcesDir.getName()+"/"+file.getName()+")");
			}
			lines.addAll(table.wrap(2, 0).build());
			lines.add("");
			// by mag table
			double minMag;
			if (minRupMag < 6d)
				minMag = 6d;
			else
				minMag = minMags[0];
			
			EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(minMag, minMags[minMags.length-1], 100);
			magFunc.setName(combNames.get(r));
			for (int m=0; m<magFunc.size(); m++) {
				double mag = magFunc.getX(m);
				int fails = 0;
				int magRups = 0;
				for (int i=0; i<rups.size(); i++) {
					if (rupSet.getMagForRup(i) >= mag) {
						magRups++;
						for (int f=0; f<result.filterResults.size(); f++) {
							PlausibilityResult rupResult = result.filterResults.get(f).get(i);
							if (rupResult != null && !rupResult.isPass()) {
								fails++;
								break;
							}
						}
					}
				}
				magFunc.set(m, 100d*fails/(double)magRups);
			}
			EvenlyDiscretizedFunc altMagFunc = null;
			if (altResult != null) {
				altMagFunc = new EvenlyDiscretizedFunc(magFunc.getMinX(), magFunc.getMaxX(), magFunc.size());
				altMagFunc.setName(altName);
				for (int m=0; m<altMagFunc.size(); m++) {
					double mag = altMagFunc.getX(m);
					int fails = 0;
					int magRups = 0;
					for (int i=0; i<rups.size(); i++) {
						if (rupSet.getMagForRup(i) >= mag) {
							magRups++;
							for (int f=0; f<result.filterResults.size(); f++) {
								PlausibilityResult rupResult = altResult.filterResults.get(f).get(i);
								if (rupResult != null && !rupResult.isPass()) {
									fails++;
									break;
								}
							}
						}
					}
					altMagFunc.set(m, 100d*fails/(double)magRups);
				}
			}
			
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars =  new ArrayList<>();
			
			if (altMagFunc != null) {
				funcs.add(altMagFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			}
			
			if (prefMagFunc == null) {
				prefMagFunc = magFunc;
			} else {
				funcs.add(prefMagFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
			}
			
			funcs.add(magFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			
			PlotSpec spec = new PlotSpec(funcs, chars, "Combined Fails vs Mag", "Min Magnitude", "% Failed");
			spec.setLegendVisible(true);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setBackgroundColor(Color.WHITE);
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(20);
			gp.setPlotLabelFontSize(21);
			gp.setLegendFontSize(22);
			
			double maxY = 20d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 50d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 100d;
			
			gp.drawGraphPanel(spec, false, false, new Range(magFunc.getMinX(), magFunc.getMaxX()), new Range(0d, maxY));
			gp.getChartPanel().setSize(1000, 800);
			
			prefix += "_vs_mag";
			File pngFile = new File(resourcesDir, prefix+".png");
			File pdfFile = new File(resourcesDir, prefix+".pdf");
			gp.saveAsPNG(pngFile.getAbsolutePath());
			gp.saveAsPDF(pdfFile.getAbsolutePath());
			
			lines.add("![plot](resources/"+pngFile.getName()+")");
			lines.add("");
		}
		
		if (rupDebugMinMag > 0d && rupDebugMinMag < 10d) {
			lines.add("## Plausibility debug for M"+(float)rupDebugMinMag+" failing ruptures");
			lines.add(topLink); lines.add("");
			
			for (int f=0; f<combinedPrefFilters.size(); f++) {
				PlausibilityFilter filter = combinedPrefFilters.get(f);
				if (filter.isDirectional(false) || filter.isDirectional(true))
					combinedPrefFilters.set(f, new MultiDirectionalPlausibilityFilter(filter, connSearch, !filter.isDirectional(false)));
			}
			
			RupSetPlausibilityResult result = combResults.get(0); // primary
			
			int numDebugRups = 0;
			for (int r=0; r<rups.size(); r++) {
				double mag = rupSet.getMagForRup(r);
				if (mag < rupDebugMinMag)
					continue;
				
				// see if it failed
				PlausibilityResult rupResult = PlausibilityResult.PASS;
				for (int f=0; f<result.filterResults.size(); f++) {
					PlausibilityResult fResult = result.filterResults.get(f).get(r);
//					if (r == 175226)
//						System.out.println(result.filters.get(f).getName()+": "+fResult);
					rupResult = rupResult.logicalAnd(fResult);
				}
//				if (r == 175226)
//					System.exit(0);
				
				if (rupResult.isPass())
					continue;
				
				lines.add("## Plausibility debug for "+r+", an M"+(float)mag);
				lines.add(topLink); lines.add("");
				
				ClusterRupture rup = rups.get(r);
				lines.add("Text representation:");
				lines.add("");
				lines.add("```");
				lines.add(rup.toString());
				lines.add("```");
				
				System.out.println("Debugging rupture "+r+" w/ M="+mag);
				
				// see how many sections we would have to remove to get this to pass
				int numSects = rup.getTotalNumSects();
				System.out.println("Rupture: "+rup);
				ClusterRupture passingSubset = new PassingSubRuptureSearch(rup, combinedPrefFilters).getLargestPassinSubset();
//				ClusterRupture passingSubset = null;
//				int maxRemoval = numSects/2;
//				int triedRemovals = 0;
//				for (int removeSects=1; removeSects<=maxRemoval; removeSects++) {
////					System.out.println("\tfinding removals of "+removeSects+" sects");
//					List<ClusterRupture> removals = getWithNEndSectsRemoved(rup, removeSects);
////					System.out.println("\tfound "+removals.size()+" removals");
//					for (ClusterRupture removal : removals) {
////						System.out.println("\t\t"+removal);
//						Preconditions.checkState(removal.getTotalNumSects() == numSects-removeSects);
//						// see if we can get a pass
//						triedRemovals++;
//						PlausibilityResult modResult = PlausibilityResult.PASS;
//						for (PlausibilityFilter filter : combinedPrefFilters) {
//							modResult = modResult.logicalAnd(filter.apply(removal, false));
//							if (!modResult.isPass())
//								break;
//						}
//						if (modResult.isPass()) {
//							System.out.println("Found a removal that passes!: "+removal);
//							passingSubset = removal;
//							break;
//						}
//					}
////					System.exit(0);
//					if (passingSubset != null)
//						break;
//				}
				
				HashSet<FaultSection> highlightSects = new HashSet<>();
				lines.add("");
				if (passingSubset == null) {
//					lines.add("We were't able to find any subset ruptures that passed. We tried "+triedRemovals
//							+" sub-ruptures, removing up to "+maxRemoval+"/"+numSects+" sections at a time.");
					lines.add("We were't able to find any subset ruptures that passed.");
				} else {
					for (FaultSubsectionCluster cluster : passingSubset.getClustersIterable())
						highlightSects.addAll(cluster.subSects);
					lines.add("We found a passing rupture subset by removing "+(rup.getTotalNumSects() - passingSubset.getTotalNumSects())
							+" sections. That subset rupture is listed below and highlighted in green in the rupture picture.");
					lines.add("");
					lines.add("```");
					lines.add(passingSubset.toString());
					lines.add("```");
				}
				lines.add("");
				
				PlotSpec rupPlot = RupCartoonGenerator.buildRupturePlot(rup, "Rupture "+r, false, true,
						highlightSects, Color.GREEN.darker(), "Passing subset");
				
				String prefix = "rup_"+r;
				RupCartoonGenerator.plotRupture(resourcesDir, prefix, rupPlot, true);
				
				lines.add("![plot](resources/"+prefix+".png)");
				lines.add("");
				
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine("Filter", "Pref. Threshold", "Result w/ Pref.", "Max Passing Threshold");
				
				List<NucleationClusterEvaluator> pathEvals = new ArrayList<>();
				for (int i=0; i<wrappers.size(); i++) {
					Float pref = prefVals.get(i);
					Float min = minVals.get(i);
					Float max = maxVals.get(i);
					ParameterizedFilterWrapper wrapper = wrappers.get(i);
					
					boolean passesPref = testWrapper(wrapper, connSearch, pref, rup);
					
					PlausibilityFilter prefFilter = wrapper.get(pref);
					if (prefFilter instanceof PathPlausibilityFilter)
						for (NucleationClusterEvaluator eval : ((PathPlausibilityFilter)prefFilter).getEvaluators())
							pathEvals.add(eval);
					
//					if (r == 175226 && prefFilter instanceof PathPlausibilityFilter)
//						prefFilter.apply(rup, true);
					
					if (pref == null) {
						table.addLine(names.get(i), "*N/A*", passesPref ? "PASS" : "FAIL", "*N/A*");
					} else {
						EvenlyDiscretizedFunc discr = new EvenlyDiscretizedFunc(min, max, 100);
						Float minPass = null;
						for (int j=discr.size(); --j>=0;) {
							float threshold = (float)discr.getX(j);
							if (testWrapper(wrapper, connSearch, threshold, rup)) {
								minPass = threshold;
								break;
							}
						}
						table.addLine(names.get(i), pref, passesPref ? "PASS" : "FAIL", minPass == null ? "*N/A*" : minPass);
					}
				}
				if (pathEvals.size() > 1) {
					PathPlausibilityFilter combFilter = new PathPlausibilityFilter(pathEvals.toArray(new NucleationClusterEvaluator[0]));
					boolean passes = combFilter.apply(rup, false).isPass();
					table.addLine(pathEvals.size()+" Paths Combined", "*N/A*", passes ? "PASS" : "FAIL", "*N/A*");
				}
				lines.addAll(table.build());
				lines.add("");
				
				numDebugRups++;
				if (numDebugRups == maxRupDebugs)
					break;
			}
		}
		
		if (skipFaultNames != null && skipFaultNames.length > 0) {
			// without specific faults
			Map<Integer, String> skipParents = new HashMap<>();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				String parentName = sect.getParentSectionName();
				for (String name : skipFaultNames)
					if (parentName.contains(name))
						skipParents.put(sect.getParentSectionId(), sect.getParentSectionName());
			}
			
			Preconditions.checkState(!skipParents.isEmpty());
			
			HashSet<Integer> retainedRups = new HashSet<>();
			for (int r=0; r<rups.size(); r++) {
				boolean exclude = false;
				for (FaultSubsectionCluster cluster : rups.get(r).getClustersIterable()) {
					if (skipParents.containsKey(cluster.parentSectionID)) {
						exclude = true;
						break;
					}
				}
				if (!exclude)
					retainedRups.add(r);
			}
			
			String combSkipNames = Joiner.on(", ").join(skipFaultNames);
			lines.add("## Plausibility without "+combSkipNames);
			
			RupSetPlausibilityResult result = combResults.get(0);
			lines.add(topLink); lines.add("");
			
			lines.add("This gives plausibility results with preferred filter values and "+(rups.size()-retainedRups.size())
					+" ruptures involving the following faults exluded:");
			lines.add("");
			List<String> parentNames = new ArrayList<>(skipParents.values());
			Collections.sort(parentNames);
			for (String name : parentNames)
				lines.add("* "+name);
			lines.add("");
			
			lines.add("*Filters:*");
			lines.add("");
			for (PlausibilityFilter filter : result.filters)
				lines.add("* "+filter.getName());
			lines.add("");
			String prefix = "plausibility_sans_faults";
			File plot = RupSetDiagnosticsPageGen.plotRupSetPlausibility(result.filterByRups(retainedRups), resourcesDir, prefix, "Plausibility");
			
			lines.add("![plot](resources/"+plot.getName()+")");
			lines.add("");
			
			// by mag table
			double minMag;
			if (minRupMag < 6d)
				minMag = 6d;
			else
				minMag = minMags[0];
			
			EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(minMag, minMags[minMags.length-1], 100);
			magFunc.setName("Excluding "+combSkipNames);
			for (int m=0; m<magFunc.size(); m++) {
				double mag = magFunc.getX(m);
				int fails = 0;
				int magRups = 0;
				for (int i=0; i<rups.size(); i++) {
					if (retainedRups.contains(i) && rupSet.getMagForRup(i) >= mag) {
						magRups++;
						for (int f=0; f<result.filterResults.size(); f++) {
							PlausibilityResult rupResult = result.filterResults.get(f).get(i);
							if (rupResult != null && !rupResult.isPass()) {
								fails++;
								break;
							}
						}
					}
				}
				magFunc.set(m, 100d*fails/(double)magRups);
			}
			EvenlyDiscretizedFunc altMagFunc = null;
			if (altResult != null) {
				altMagFunc = new EvenlyDiscretizedFunc(magFunc.getMinX(), magFunc.getMaxX(), magFunc.size());
				altMagFunc.setName(altName+" excluding "+combSkipNames);
				for (int m=0; m<altMagFunc.size(); m++) {
					double mag = altMagFunc.getX(m);
					int fails = 0;
					int magRups = 0;
					for (int i=0; i<rups.size(); i++) {
						if (retainedRups.contains(i) && rupSet.getMagForRup(i) >= mag) {
							magRups++;
							for (int f=0; f<result.filterResults.size(); f++) {
								PlausibilityResult rupResult = altResult.filterResults.get(f).get(i);
								if (rupResult != null && !rupResult.isPass()) {
									fails++;
									break;
								}
							}
						}
					}
					altMagFunc.set(m, 100d*fails/(double)magRups);
				}
			}
			
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars =  new ArrayList<>();
			
			if (altMagFunc != null) {
				funcs.add(altMagFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			}
			
			if (prefMagFunc != null) {
				prefMagFunc.setName("All Faults");
				funcs.add(prefMagFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
			}
			
			funcs.add(magFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			
			PlotSpec spec = new PlotSpec(funcs, chars, "Combined Fails vs Mag", "Min Magnitude", "% Failed");
			spec.setLegendVisible(true);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setBackgroundColor(Color.WHITE);
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(20);
			gp.setPlotLabelFontSize(21);
			gp.setLegendFontSize(22);
			
			double maxY = 20d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 50d;
			if (funcs.get(0).getMaxY() > maxY)
				maxY = 100d;
			
			gp.drawGraphPanel(spec, false, false, new Range(magFunc.getMinX(), magFunc.getMaxX()), new Range(0d, maxY));
			gp.getChartPanel().setSize(1000, 800);
			
			prefix += "_vs_mag";
			File pngFile = new File(resourcesDir, prefix+".png");
			File pdfFile = new File(resourcesDir, prefix+".pdf");
			gp.saveAsPNG(pngFile.getAbsolutePath());
			gp.saveAsPDF(pdfFile.getAbsolutePath());
			
			lines.add("![plot](resources/"+pngFile.getName()+")");
			lines.add("");
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		exec.shutdown();
	}
	
	private static boolean testWrapper(ParameterizedFilterWrapper wrapper, RuptureConnectionSearch connSearch,
			Float threshold, ClusterRupture rup) {
		PlausibilityFilter filter = wrapper.get(threshold);
		if (filter.isDirectional(!rup.splays.isEmpty()))
			filter = new MultiDirectionalPlausibilityFilter(filter, connSearch, !filter.isDirectional(false));
		return filter.apply(rup, false).isPass();
	}
	
	private static List<FaultSection> locateEnds(ClusterRupture rup) {
		List<FaultSection> ends = new ArrayList<>();
		ends.add(rup.clusters[0].startSect);
		for (ClusterRupture strand : rup.getStrandsIterable())
			ends.addAll(strand.clusters[strand.clusters.length-1].endSects);
		return ends;
	}
	
	private static List<ClusterRupture> getWithNEndSectsRemoved(ClusterRupture primary, int numToRemove) {
		// first find ends
		List<FaultSection> ends = locateEnds(primary);
		
		// now see how far back we can go from each end section
		
		RuptureTreeNavigator nav = primary.getTreeNavigator();
		
		List<List<FaultSection>> maxStrands = new ArrayList<>();
		
		for (FaultSection end : ends) {
			List<FaultSection> maxRemovalStrand = new ArrayList<>();
//			System.out.println("building out end: "+end.getSectionId());
			if (end.equals(primary.clusters[0].startSect)) {
				// forward, go until we reach a branch point
				FaultSection cur = end;
				while (maxRemovalStrand.size() < numToRemove) {
					Collection<FaultSection> descendants = nav.getDescendants(cur);
//					System.out.println(cur.getSectionId()+" has "+descendants.size()+" descendants");
					if (descendants.size() != 1)
						break;
					maxRemovalStrand.add(cur);
					cur = descendants.iterator().next();
				}
			} else {
				maxRemovalStrand.add(end);
				// backward, go until we reach a parent that has multiple children
				while (maxRemovalStrand.size() < numToRemove) {
					FaultSection parent = nav.getPredecessor(end);
					if (parent == null)
						break;
					Collection<FaultSection> descendants = nav.getDescendants(parent);
					if (descendants.size() != 1)
						break;
					maxRemovalStrand.add(parent);
				}
			}
			maxStrands.add(maxRemovalStrand);
//			System.out.println("\tstrand:"+maxStrands.stream().map(S -> S.getSectionId()).map(S -> S.toString()).collect(Collectors.joining(",")));
		}
//		System.exit(0);
		
		// now find all unique combinations of those strands that remove the correct number and don't overlap
		List<ClusterRupture> ret = new ArrayList<>();
		findRemovalsRecursive(primary, ret, maxStrands, new ArrayList<>(), 0, 0, numToRemove);
		return ret;
	}
	
	private static void findRemovalsRecursive(ClusterRupture origRup, List<ClusterRupture> ret,
			List<List<FaultSection>> maxStrands, List<List<FaultSection>> myRemovals, int startIndex, int myCount, int targetCount) {
		if (myCount == targetCount) {
			// build them!
//			System.out.println("building with "+myCount+" to remove!");
//			for (List<FaultSection> rem : myRemovals)
//				System.out.println("\t"+rem.stream().map(S -> S.getSectionId()).map(S -> S.toString()).collect(Collectors.joining(",")));
			ClusterRupture removed = removeEndSects(origRup, myRemovals);
//			System.out.println("removed has "+removed.getTotalNumSects()+" sects: "+removed);
			if (origRup.getTotalNumSects() - removed.getTotalNumSects() == targetCount)
				ret.add(removed);
			return;
		}
		// need more, keep on down the line
		for (int i=startIndex; i<maxStrands.size(); i++) {
			List<FaultSection> maxStrand = maxStrands.get(i);
			for (int j=0; j<maxStrand.size(); j++) {
				int newCount = myCount + j + 1;
				if (newCount <= targetCount) {
					List<List<FaultSection>> newRemovals = new ArrayList<>(myRemovals);
					newRemovals.add(maxStrand.subList(0, j+1));
					findRemovalsRecursive(origRup, ret, maxStrands, newRemovals, i+1, newCount, targetCount);
				} else {
					break;
				}
			}
		}
	}
	
	private static ClusterRupture removeEndSects(ClusterRupture rup, List<List<FaultSection>> removals) {
		for (List<FaultSection> removal : removals) {
			if (rup.clusters[0].startSect.equals(removal.get(0))) {
				// forward
				rup = removeForward(rup, removal);
				if (rup == null)
					return null;
			} else {
				// backward from an end
				rup = removeBackward(rup, removal);
				if (rup == null)
					return null;
			}
		}
		return rup;
	}
	
	private static ClusterRupture removeForward(ClusterRupture rup, List<FaultSection> sects) {
		FaultSubsectionCluster origStart = rup.clusters[0];
		int c0 = 0;
		while (sects.size() >= origStart.subSects.size()) {
			// skip over the full cluster
			sects = sects.subList(origStart.subSects.size(), sects.size());
			c0++;
			origStart = rup.clusters[c0];
		}
		
		for (FaultSection sect : sects)
			if (!rup.contains(sect))
				return null;
		FaultSubsectionCluster newStart;
		if (sects.isEmpty())
			newStart = origStart;
		else
			newStart = new FaultSubsectionCluster(
					origStart.subSects.subList(sects.size(), origStart.subSects.size()));

//		System.out.println("newStart="+newStart);
		ClusterRupture ret = new ClusterRupture(newStart);
		for (int c=c0+1; c<rup.clusters.length; c++) {
			Jump jump = rup.getTreeNavigator().getJump(rup.clusters[c-1], rup.clusters[c]);
			if (jump.fromCluster == origStart && origStart != newStart)
				jump = new Jump(jump.fromSection, newStart, jump.toSection, jump.toCluster, jump.distance);
			ret = ret.take(jump);
		}
		for (Jump jump : rup.splays.keySet()) {
			ClusterRupture splay = rup.splays.get(jump);
			if (jump.fromCluster.equals(origStart))
				jump = new Jump(jump.fromSection, newStart, jump.toSection, jump.toCluster, jump.distance);
			ret = buildOutSplay(ret, jump, splay, rup.getTreeNavigator());
		}
		return ret;
	}
	
	private static ClusterRupture buildOutSplay(ClusterRupture rup, Jump jump, ClusterRupture splay, RuptureTreeNavigator nav) {
		rup = rup.take(jump);
		for (int c=1; c<splay.clusters.length; c++)
			rup = rup.take(nav.getJump(splay.clusters[c-1], splay.clusters[c]));
		for (Jump splayJump : splay.splays.keySet())
			rup = buildOutSplay(rup, splayJump, splay.splays.get(splayJump), nav);
		return rup;
	}
	
	private static ClusterRupture removeBackward(ClusterRupture rup, List<FaultSection> sects) {
		FaultSubsectionCluster curCluster = rup.clusters[0];
		
		HashSet<FaultSection> sectsSet = new HashSet<>(sects);
		
		for (FaultSection sect : sects) {
			if (curCluster.contains(sect)) {
				// they extend to the first cluster, simple case
				List<FaultSection> newSects = new ArrayList<>();
				for (FaultSection s : curCluster.subSects) {
					if (sectsSet.contains(s))
						break;
					newSects.add(s);
				}
				if (newSects.isEmpty())
					return null;
				return new ClusterRupture(new FaultSubsectionCluster(newSects));
			}
		}
		RuptureTreeNavigator nav = rup.getTreeNavigator();
		
		return buildOutTo(new ClusterRupture(curCluster), curCluster, sectsSet, nav);
	}

	private static ClusterRupture buildOutTo(ClusterRupture rup, FaultSubsectionCluster curCluster, HashSet<FaultSection> sectsSet,
			RuptureTreeNavigator nav) {
		for (FaultSubsectionCluster child : nav.getDescendants(curCluster)) {
			int keepSects;
			if (sectsSet == null) {
				keepSects = child.subSects.size();
			} else {
				keepSects = 0;
				for (int i=0; i<child.subSects.size(); i++) {
					if (sectsSet.contains(child.subSects.get(i)))
						break;
					keepSects++;
				}
				if (keepSects == 0)
					// can stop here
					continue;
			}
			Jump jump = nav.getJump(curCluster, child);
			if (keepSects < child.subSects.size()) {
				FaultSubsectionCluster newChild = new FaultSubsectionCluster(child.subSects.subList(0, keepSects));
				rup = rup.take(new Jump(jump.fromSection, curCluster, jump.toSection, newChild, jump.distance));
				// check for splays from this section
				for (FaultSubsectionCluster splayCluster : nav.getDescendants(child)) {
					if (sectsSet.contains(splayCluster.startSect))
						continue;
					Jump splayJump = nav.getJump(child, splayCluster);
					rup = rup.take(new Jump(splayJump.fromSection, newChild, splayJump.toSection, splayJump.toCluster, splayJump.distance));
					rup = buildOutTo(rup, splayCluster, null, nav);
				}
				// now done
				continue;
			}
			// full cluster
			rup = rup.take(jump);
			rup = buildOutTo(rup, child, sectsSet, nav);
		}
		return rup;
	}
	
	private static interface ParameterizedFilterWrapper {
		
		public PlausibilityFilter get(Float value);
		
	}
	
	private static class CalcCallable implements Callable<PlausibilityResult[]> {
		
		private final PlausibilityFilter filter;
		private final List<ClusterRupture> rups;

		public CalcCallable(PlausibilityFilter filter, List<ClusterRupture> rups) {
			super();
			this.filter = filter;
			this.rups = rups;
		}

		@Override
		public PlausibilityResult[] call() throws Exception {
			PlausibilityResult[] results = new PlausibilityResult[rups.size()];
			
			for (int r=0; r<results.length; r++)
				results[r] = filter.apply(rups.get(r), false);
			
			return results;
		}
		
	}

}
