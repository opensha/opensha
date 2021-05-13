package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_HazardChangePlot extends ETAS_AbstractPlot {
	
	private String prefix;
	private double radius;
	
	static double[] times = { 1d / (365.25 * 24), 1d / 365.25, 7d / 365.25, 30 / 365.25, 1d, 10d, 30d, 100d };
	private static double[] minMags = { 5d, 6d, 7d, 8d };
	private static double overallMinMag = StatUtils.min(minMags);
	
	private ArbitrarilyDiscretizedFunc etasTimesFunc;
	private ArbitrarilyDiscretizedFunc u3TimesFunc;
	
	private List<Region> triggerRegions;
	private Region unionRegion;
	
	private HashSet<Integer> fssIndexesInside;
	private long simOT;
	
	private List<int[][]> catalogCounts;
	private boolean[] hasMags;
	
	private ArbitrarilyDiscretizedFunc[] tiFuncs;
	private ArbitrarilyDiscretizedFunc[] tdFuncs;
	private ArbitrarilyDiscretizedFunc[] simFuncs;
	private ArbitrarilyDiscretizedFunc[] simLowerFuncs;
	private ArbitrarilyDiscretizedFunc[] simUpperFuncs;
	private ArbitrarilyDiscretizedFunc[] simOnlyFuncs;

	public ETAS_HazardChangePlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, double radius) {
		super(config, launcher);
		this.prefix = prefix;
		this.radius = radius;
		this.simOT = config.getSimulationStartTimeMillis();
		
		Preconditions.checkState(config.hasTriggers(), "Hazard change plot requires trigger ruptures");
		
		int etasNumX = 1000;
		int u3NumX = 10;
		
		EvenlyDiscretizedFunc evenlyDiscrTimes = new EvenlyDiscretizedFunc(Math.log(times[0]),
				Math.log(times[times.length - 1]), etasNumX);
		etasTimesFunc = new ArbitrarilyDiscretizedFunc();
		for (double x : times)
			etasTimesFunc.set(x, 0);
		for (Point2D pt : evenlyDiscrTimes) {
			double time = Math.exp(pt.getX());
			if (!isHardcodedTime(time))
				etasTimesFunc.set(time, 0);
		}
		
		evenlyDiscrTimes = new EvenlyDiscretizedFunc(Math.log(30 / 365.25), Math.log(times[times.length - 1]), u3NumX);
		u3TimesFunc = new ArbitrarilyDiscretizedFunc();
		for (double x : times)
			u3TimesFunc.set(x, 0);
		for (Point2D pt : evenlyDiscrTimes) {
			double time = Math.exp(pt.getX());
			if (!isHardcodedTime(time))
				u3TimesFunc.set(time, 0);
		}
		
		catalogCounts = new ArrayList<>();
		hasMags = new boolean[minMags.length];
		for (int m=0; m<minMags.length; m++)
			hasMags[m] = false;
	}
	
	private synchronized void checkInitTriggerRegions() {
		if (triggerRegions != null)
			return;
		ETAS_Launcher launcher = getLauncher();
		ETAS_Config config = getConfig();
		List<ETAS_EqkRupture> triggerRups = launcher.getCombinedTriggers();
		Preconditions.checkState(!triggerRups.isEmpty(), "No trigger ruptures?");
		
		triggerRegions = new ArrayList<>();
		ComcatMetadata meta = config.getComcatMetadata();
		if (meta != null && meta.region != null) {
			System.out.println("Will compute hazard change in ComCat region");
			triggerRegions.add(meta.region);
		} else {
			for (ETAS_EqkRupture rup : triggerRups) {
				RuptureSurface surf = rup.getRuptureSurface();
				if (surf == null || surf instanceof PointSurface) {
					triggerRegions.add(new Region(rup.getHypocenterLocation(), radius));
				} else if (surf instanceof CompoundSurface) {		
					for (RuptureSurface subSurf : ((CompoundSurface)surf).getSurfaceList()) {
						LocationList upper;
						try {
							upper = subSurf.getUpperEdge();
						} catch (Exception e) {
							upper = subSurf.getEvenlyDiscritizedUpperEdge();
						}
						triggerRegions.add(new Region(upper, radius));
					}
				} else {
					triggerRegions.add(new Region(surf.getUpperEdge(), radius));
				}
			}
		}
		
		unionRegion = triggerRegions.get(0);
		for (int i=1; i<triggerRegions.size(); i++) {
			try {
				unionRegion = Region.union(unionRegion, triggerRegions.get(i));
			} catch (Exception e) {
				unionRegion = null;
				System.err.println("Exception unioning trigger regions, will be slower:");
				e.printStackTrace();
				break;
			}
			if (unionRegion == null) {
				System.out.println("Warning, can't union trigger rupture buffered regions, will be slower");
				break;
			}
		}
	}

	@Override
	public int getVersion() {
		return 1;
	}
	
	private static boolean isHardcodedTime(double time) {
		for (double test : times)
			if ((float)time == (float)test)
				return true;
		return false;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return true;
	}
	
	private boolean insideTriggerRegion(Location loc) {
		checkInitTriggerRegions();
		if (unionRegion == null) {
			for (Region triggerRegion : triggerRegions)
				if (triggerRegion.contains(loc))
					return true;
			return false;
		}
		return unionRegion.contains(loc);
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		if (fssIndexesInside == null) {
			FaultSystemRupSet rupSet = fss.getRupSet();
			fssIndexesInside = new HashSet<>();
			for (int s=0; s<rupSet.getNumSections(); s++)
				for (Location loc : rupSet.getFaultSectionData(s).getFaultTrace())
					if (insideTriggerRegion(loc))
						fssIndexesInside.addAll(rupSet.getRupturesForSection(s));
		}
		
		int[][] counts = new int[minMags.length][etasTimesFunc.size()];
		for (ETAS_EqkRupture rup : triggeredOnlyCatalog) {
			if (rup.getMag() < overallMinMag)
				continue;
			int fssIndex = rup.getFSSIndex();
			if (fssIndex >= 0 && fssIndexesInside.contains(fssIndex) || insideTriggerRegion(rup.getHypocenterLocation())) {
				// it's a match!
				long rupOT = rup.getOriginTime();
				Preconditions.checkState(rupOT >= simOT);
				double timeSince = (rupOT - simOT)/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
				double mag = rup.getMag();
				for (int m=0; m<minMags.length; m++) {
					if (mag < minMags[m])
						continue;
					hasMags[m] = true;
					for (int t=0; t<etasTimesFunc.size(); t++) {
						if (timeSince > etasTimesFunc.getX(t))
							continue;
						counts[m][t]++;
					}
				}
			}
		}
		catalogCounts.add(counts);
		tiFuncs = null;
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		if (tiFuncs != null)
			return null;
		System.out.println("Calculating hazard change for U3-TI");
		tiFuncs = calcUCERF3(fss, true, exec);
		ArbitrarilyDiscretizedFunc[] addToSimFuncs;
		if (getConfig().isTimeIndependentERF()) {
			tdFuncs = null;
			addToSimFuncs = tiFuncs;
		} else {
			System.out.println("Calculating hazard change for U3-TD");
			tdFuncs = calcUCERF3(fss, false, exec);
			addToSimFuncs = tdFuncs;
		}
		
		System.out.println("Calculating hazard change for ETAS simulations");
		simFuncs = new ArbitrarilyDiscretizedFunc[minMags.length];
		simLowerFuncs = new ArbitrarilyDiscretizedFunc[minMags.length];
		simUpperFuncs = new ArbitrarilyDiscretizedFunc[minMags.length];
		simOnlyFuncs = new ArbitrarilyDiscretizedFunc[minMags.length];
		for (int m=0; m<minMags.length; m++) {
			if (!hasMags[m])
				continue;
			simFuncs[m] = new ArbitrarilyDiscretizedFunc();
			simLowerFuncs[m] = new ArbitrarilyDiscretizedFunc();
			simUpperFuncs[m] = new ArbitrarilyDiscretizedFunc();
			simOnlyFuncs[m] = new ArbitrarilyDiscretizedFunc();
			for (int t=0; t<etasTimesFunc.size(); t++) {
				double duration = etasTimesFunc.getX(t);
				
				int numWith = 0;
				int totalNum = 0;
				
				for (int[][] counts : catalogCounts) {
					Preconditions.checkState(counts[m].length == etasTimesFunc.size());
					if (counts[m][t] > 0)
						numWith++;
					totalNum++;
				}
				double u3Prob;
				try {
					u3Prob = addToSimFuncs[m].getInterpolatedY_inLogXLogYDomain(duration);
					Preconditions.checkState(Double.isFinite(u3Prob));
				} catch (Exception e) {
					u3Prob = addToSimFuncs[m].getInterpolatedY(duration);
				}
				Preconditions.checkState(Double.isFinite(u3Prob) && u3Prob >= 0 && u3Prob <= 1d,
						"Bad U3 prob: %s\n\n$s", u3Prob, addToSimFuncs[m]);
				double simProb = (double)numWith/(double)totalNum;
				
				double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(simProb, totalNum);
				
				double prob = 1d - (1d - simProb )*(1d - u3Prob);
				simFuncs[m].set(duration, prob);
				double lowerProb = 1d - (1d - conf[0])*(1d - u3Prob);
				simLowerFuncs[m].set(duration, lowerProb);
				double upperProb = 1d - (1d - conf[1])*(1d - u3Prob);
				simUpperFuncs[m].set(duration, upperProb);
				if ((float)duration <= (float)getConfig().getDuration())
					simOnlyFuncs[m].set(duration, simProb);
			}
		}
		
		// now plot
		boolean xAxisInverted = false;
		for (int m=0; m<minMags.length; m++) {
			if (!hasMags[m])
				continue;
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			tiFuncs[m].setName("UCERF3-TI");
			funcs.add(tiFuncs[m]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));

			if (tdFuncs != null) {
				tdFuncs[m].setName("UCERF3-TD");
				funcs.add(tdFuncs[m]);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			}
			
			simFuncs[m].setName("UCERF3-ETAS");
			funcs.add(simFuncs[m]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
			
			UncertainArbDiscDataset simConfFunc = new UncertainArbDiscDataset(simFuncs[m], simLowerFuncs[m], simUpperFuncs[m]);
			simConfFunc.setName("95% Conf");
			funcs.add(simConfFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, new Color(255, 0, 0, 30)));
			
			double minProb = 1d;
			for (XY_DataSet func : funcs)
				for (Point2D pt : func)
					if (pt.getY() > 0)
						minProb = Math.min(minProb, pt.getY());
			minProb *= 0.5;

			simOnlyFuncs[m].setName("UCERF3-ETAS Triggered Only");
			funcs.add(simOnlyFuncs[m]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			
			double annY1 = Math.pow(10, Math.log10(minProb)+0.985*(Math.log10(1d) - Math.log10(minProb)));
			double annY2 = Math.pow(10, Math.log10(minProb)+0.94*(Math.log10(1d) - Math.log10(minProb)));
//			System.out.println("Ann Y's: "+annY1+" "+annY2);
			
			List<XYTextAnnotation> annotations = new ArrayList<>();

			Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
			for (int i = 0; i < times.length; i++) {
				double time = times[i];
				String label = getTimeShortLabel(time);
				
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(time, minProb);
				xy.set(time, 1d);
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));

				XYTextAnnotation ann = new XYTextAnnotation(label, time, annY1);
				if (i == 0 && xAxisInverted || i == (times.length - 1) && !xAxisInverted) {
					ann.setTextAnchor(TextAnchor.TOP_RIGHT);
					if (!xAxisInverted)
						ann.setY(annY2); // put it below
				} else {
					ann.setTextAnchor(TextAnchor.TOP_LEFT);
				}
				ann.setFont(annFont);
				annotations.add(ann);
			}

			for (XY_DataSet func : funcs)
				Preconditions.checkState(func.size() > 0, "Empty func with name: %s", func.getName());

			String yAxisLabel = "M≥"+(float)minMags[m]+" Participation Probability";
			PlotSpec spec = new PlotSpec(funcs, chars, "M≥"+(float)minMags[m]+" Simulation Hazard Change",
					"Forecast Timespan (years)", yAxisLabel);
			spec.setPlotAnnotations(annotations);
			spec.setLegendVisible(true);

			HeadlessGraphPanel gp = buildGraphPanel();
			gp.setxAxisInverted(xAxisInverted);
			gp.setUserBounds(times[0], times[times.length - 1], minProb, 1d);

			gp.drawGraphPanel(spec, true, true);
			gp.getChartPanel().setSize(1000, 800);
			
			String myPrefix = prefix+"_m"+(float)minMags[m];
			gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
			gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
			gp.saveAsTXT(new File(outputDir, myPrefix+".txt").getAbsolutePath());
		}
		return null;
	}
	
	private ArbitrarilyDiscretizedFunc[] calcUCERF3(FaultSystemSolution fss, boolean timeIndep, ExecutorService exec) {
		FaultSystemSolutionERF_ETAS erf = ETAS_Launcher.buildERF_millis(fss, timeIndep, timeIndep ? 1d : u3TimesFunc.getMinX(), simOT);
		erf.updateForecast();
		
		int numFSSsources = erf.getNumFaultSystemSources();
		
		List<Integer> sourceIDs = new ArrayList<>();
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			int fssIndex = -1;
			if (sourceID < numFSSsources)
				fssIndex = erf.getFltSysRupIndexForSource(sourceID);
			
			if (fssIndex >= 0) {
				if (fssIndexesInside.contains(fssIndex))
					sourceIDs.add(sourceID);
			} else {
				ProbEqkSource source = erf.getSource(sourceID);
				RuptureSurface sourceSurf = source.getSourceSurface();
				Preconditions.checkState(sourceSurf instanceof PointSurface);
				if (insideTriggerRegion(((PointSurface)sourceSurf).getLocation()))
					sourceIDs.add(sourceID);
			}
		}
		Preconditions.checkState(!sourceIDs.isEmpty(), "No UCERF3 source IDs found inside of trigger region!");
		
		ArbitrarilyDiscretizedFunc[] funcs = new ArbitrarilyDiscretizedFunc[minMags.length];
		for (int m=0; m<funcs.length; m++)
			funcs[m] = new ArbitrarilyDiscretizedFunc();
		
		ArrayDeque<FaultSystemSolutionERF_ETAS> erfDeque = new ArrayDeque<>();
		erfDeque.push(erf);
		
		int threads = Integer.min(Runtime.getRuntime().availableProcessors(), 10);
		if (timeIndep || threads < 1)
			threads = 1;
		
		List<Future<?>> futures = new ArrayList<>();
		
		for (int t=0; t<u3TimesFunc.size(); t++)
			futures.add(exec.submit(new U3CalcRunnable(fss, timeIndep, sourceIDs, erfDeque, u3TimesFunc.getX(t), funcs)));
		
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		// now smooth the coarse UCERF3 functions assuming an exponential form
		HashSet<Double> smoothedXVals = new HashSet<>();
		for (int i=0; i<etasTimesFunc.size(); i++)
			smoothedXVals.add(etasTimesFunc.getX(i));
		for (int i=0; i<u3TimesFunc.getX(i); i++)
			smoothedXVals.add(u3TimesFunc.getX(i));
		
		ArbitrarilyDiscretizedFunc[] smoothed = new ArbitrarilyDiscretizedFunc[funcs.length];
		for (int m=0; m<funcs.length; m++) {
			ArbitrarilyDiscretizedFunc rateFunc = new ArbitrarilyDiscretizedFunc();
			for (int i=0; i<funcs[m].size(); i++) {
				double time = funcs[m].getX(i);
				double prob = funcs[m].getY(i);
				if (prob == 1d)
					// edge case where the implied rate is high enough that the probability is 1
					prob -= 1e-10;
				double rate = -Math.log(1 - prob)/time;
				rateFunc.set(time, rate);
			}
//			System.out.println(rateFunc);
			smoothed[m] = new ArbitrarilyDiscretizedFunc();
			for (double x : smoothedXVals) {
				double smoothedRate = rateFunc.getInterpolatedY(x);
				double smoothedProb;
				if (Double.isInfinite(smoothedRate))
					smoothedProb = 1d;
				else
					smoothedProb = 1d - Math.exp(-smoothedRate*x);
				Preconditions.checkState(Double.isFinite(smoothedProb),
						"Bad smoothed prob! 1-e^(-%s*%s)=%s", smoothedRate, x, smoothedProb);
				smoothed[m].set(x, smoothedProb);
			}
		}
		
		return smoothed;
	}
	
	private class U3CalcRunnable implements Runnable {
		
		private FaultSystemSolution fss;
		private boolean timeIndep;
		private List<Integer> sourceIDs;
		private ArrayDeque<FaultSystemSolutionERF_ETAS> erfDeque;
		private double duration;
		private ArbitrarilyDiscretizedFunc[] ret;

		public U3CalcRunnable(FaultSystemSolution fss, boolean timeIndep, List<Integer> sourceIDs,
				ArrayDeque<FaultSystemSolutionERF_ETAS> erfDeque, double duration, ArbitrarilyDiscretizedFunc[] ret) {
			this.fss = fss;
			this.timeIndep = timeIndep;
			this.sourceIDs = sourceIDs;
			this.erfDeque = erfDeque;
			this.duration = duration;
			this.ret = ret;
		}

		@Override
		public void run() {
			if (!timeIndep)
				System.out.println("\tduration: "+getTimeLabel(duration, true));
			
			FaultSystemSolutionERF_ETAS erf = null;
			synchronized (erfDeque) {
				if (!erfDeque.isEmpty())
					erf = erfDeque.pop();
			}
			if (erf == null) {
				erf = ETAS_Launcher.buildERF_millis(fss, timeIndep, timeIndep ? 1d : duration, simOT);
				erf.updateForecast();
			}
			
			double rateScale;
			if (timeIndep) {
				// do for 1 year and the scale for other durations
				rateScale = duration;
			} else {
				rateScale = 1d;
				TimeSpan timeSpan = erf.getTimeSpan();
				if ((float)duration != (float)timeSpan.getDuration()) {
					timeSpan.setDuration(duration);
					erf.updateForecast();
				}
			}
			
			List<List<Double>> probs = new ArrayList<>();
			for (int m=0; m<minMags.length; m++)
				probs.add(new ArrayList<>());
			for (int sourceID : sourceIDs) {
				ProbEqkSource source = erf.getSource(sourceID);
				for (ProbEqkRupture rup : source) {
					double mag = rup.getMag();
					double prob = rup.getProbability();
					if (rateScale != 1d) {
						double annualRate = -Math.log(1 - prob);
						prob = 1d - Math.exp(-annualRate*duration);
					}
					for (int m=0; m<minMags.length; m++)
						if (mag >= minMags[m])
							probs.get(m).add(prob);
				}
			}
			for (int m=0; m<minMags.length; m++) {
				List<Double> magProbs = probs.get(m);
				ret[m].set(duration, FaultSysSolutionERF_Calc.calcSummedProbs(magProbs));
			}
			
			synchronized (erfDeque) {
				erfDeque.push(erf);
			}
		}
		
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		String title = "Hazard Change Over Time";
		
		lines.add(topLevelHeading+" "+title);
		lines.add(topLink); lines.add("");
		ComcatMetadata meta = getConfig().getComcatMetadata();
		if (meta != null && meta.region != null) {
			String str = "These plots show how the probability of ruptures of various magnitudes ";
			if ((float)meta.region.getExtent() == (float)new CaliforniaRegions.RELM_TESTING().getExtent())
				str += "statewide";
			else
				str += "within the region used to fetch ComCat trigger ruptures";
			str += " changes over time";
			lines.add(str);
		} else {
			lines.add("These plots show how the probability of ruptures of various magnitudes within "
					+optionalDigitDF.format(radius)+"km of any scenario rupture changes over time");
		}
		lines.add("");
		
		for (int m=0; m<minMags.length; m++) {
			if (!hasMags[m])
				continue;
			lines.add(topLevelHeading+"# M&ge;"+(float)minMags[m]+" "+title);
			lines.add(topLink); lines.add("");
			lines.add("![Hazard Change]("+relativePathToOutputDir+"/"+prefix+"_m"+(float)minMags[m]+".png)");
			lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("Forecast Duration");
			table.addColumn("UCERF3-ETAS [95% Conf]");
			table.addColumn("UCERF3-ETAS Triggered Only");
			if (tdFuncs != null) {
				table.addColumn("UCERF3-TD");
				table.addColumn("UCERF3-ETAS/TD Gain");
				table.addColumn("UCERF3-TI");
			} else {
				table.addColumn("UCERF3-TI");
				table.addColumn("UCERF3-ETAS/TI Gain");
			}
			table.finalizeLine();
			
			boolean hasAsterisk = false;
			for (double time : times) {
				table.initNewLine();
				
				table.addColumn(getTimeLabel(time, true));
				String asterisk = "";
				if ((float)time > (float)getConfig().getDuration()) {
					asterisk = " \\*";
					hasAsterisk = true;
				}
				double etasProb = simFuncs[m].getY(time);
				table.addColumn(getProbStr(etasProb)+" ["+getProbStr(simLowerFuncs[m].getY(time))
						+" - "+getProbStr(simUpperFuncs[m].getY(time))+"]"+asterisk);
				if ((float)time > (float)simOnlyFuncs[m].getMaxX())
					table.addColumn("\\*");
				else
					table.addColumn(getProbStr(simOnlyFuncs[m].getY(time)));
				if (tdFuncs != null) {
					double tdProb = tdFuncs[m].getY(time);
					table.addColumn(getProbStr(tdProb));
					table.addColumn(optionalDigitDF.format(etasProb/tdProb)+asterisk);
					table.addColumn(getProbStr(tiFuncs[m].getY(time)));
				} else {
					double tiProb = tiFuncs[m].getY(time);
					table.addColumn(getProbStr(tiProb));
					table.addColumn(optionalDigitDF.format(etasProb/tiProb)+asterisk);
				}
				
				table.finalizeLine();
			}
			
			lines.addAll(table.build());
			lines.add("");
			if (hasAsterisk) {
				lines.add("\\* *forecast duration is longer than simulation length, only ETAS ruptures from the first "
						+getTimeLabel(getConfig().getDuration(), true).toLowerCase()+" are included*");
			}
		}
		
		return lines;
	}

}
