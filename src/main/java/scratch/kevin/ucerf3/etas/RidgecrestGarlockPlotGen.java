package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.FaultSystemIO;

public class RidgecrestGarlockPlotGen {

	public static void main(String[] args) throws IOException, DocumentException {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		String[] dirNames = {
			"2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces",
			"2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces",
			"2019_08_19-ComCatM7p1_ci38457511_14DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_07_27-ComCatM7p1_ci38457511_21DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_08_03-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_08_19-ComCatM7p1_ci38457511_35DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_08_19-ComCatM7p1_ci38457511_42DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_08_24-ComCatM7p1_ci38457511_49DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14",
			"2019_08_31-ComCatM7p1_ci38457511_56DaysAfter_ShakeMapSurfaces",
			"2019_09_09-ComCatM7p1_ci38457511_63DaysAfter_ShakeMapSurfaces",
			"2019_09_16-ComCatM7p1_ci38457511_70DaysAfter_ShakeMapSurfaces",
			"2019_11_04-ComCatM7p1_ci38457511_122DaysAfter_ShakeMapSurfaces",
			"2020_04_27-ComCatM7p1_ci38457511_296p8DaysAfter_ShakeMapSurfaces",
			"2020_05_28-ComCatM7p1_ci38457511_327p6DaysAfter_ShakeMapSurfaces",
			"2020_06_03-ComCatM7p1_ci38457511_334DaysAfter_ShakeMapSurfaces",
			"2020_06_22-ComCatM7p1_ci38457511_352p9DaysAfter_ShakeMapSurfaces",
		};
		
		HashSet<String> highlightProbNames = new HashSet<>();
		highlightProbNames.add("2020_06_03-ComCatM7p1_ci38457511_334DaysAfter_ShakeMapSurfaces");
		
		int targetSectID = 341; // garlock central
		
		File outputDir = new File("/tmp");
		String prefix = "ridgecrest_garlock_probs";
		
		double[] dataMinMags = {
//				4d,
				5d
		};
		PlotCurveCharacterstics[] dataChars = {
//				new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 4f, Color.RED.darker()),
				new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 10f, Color.RED.darker())
		};
		
		double millsPerDay = ProbabilityModelsCalc.MILLISEC_PER_DAY;
		double probDays = 30d;
		long probMillis = (long)(probDays * millsPerDay);
		
		List<ETAS_Config> configs = new ArrayList<>();
		for (String dirName : dirNames) {
			File dir = new File(baseDir, dirName);
			Preconditions.checkState(dir.exists());
			File configFile = new File(dir, "config.json");
			Preconditions.checkState(configFile.exists());
			configs.add(ETAS_Config.readJSON(configFile));
		}
		
		// sort by start time, ascending
		Collections.sort(configs, new Comparator<ETAS_Config>() {

			@Override
			public int compare(ETAS_Config o1, ETAS_Config o2) {
				return Long.compare(o1.getSimulationStartTimeMillis(), o1.getSimulationStartTimeMillis());
			}
		});
		
		long overallStartMillis = configs.get(0).getSimulationStartTimeMillis();
		double totalMaxDays = 365d;
		double deltaDays = 0.5d;
		
		double minMag = 7d;
		
		FaultSystemSolution fss = null;
		FaultSystemRupSet rupSet = null;
		FaultSystemSolutionERF erf = null;
		List<Integer> garlockSources = null;
		
		List<ArbitrarilyDiscretizedFunc> etasProbFuncs = new ArrayList<>();
		ArbitrarilyDiscretizedFunc tdProbFunc = new ArbitrarilyDiscretizedFunc();
		
		List<Double> simStartDaysList = new ArrayList<>();
		
		for (int i=0; i<configs.size(); i++) {
			ETAS_Config config = configs.get(i);
			long myStartMillis = config.getSimulationStartTimeMillis();
			
			long relStartMillis = myStartMillis - overallStartMillis;
			double relStartDays = (double)relStartMillis / millsPerDay;
			double nextRelStartDays;
			if (i == configs.size() - 1) {
				nextRelStartDays = totalMaxDays;
			} else {
				long nextRelMillis = configs.get(i+1).getSimulationStartTimeMillis() - overallStartMillis;
				nextRelStartDays = (double)nextRelMillis / millsPerDay;
			}
			
			simStartDaysList.add(relStartDays);
			
			System.out.println("Processing "+config.getSimulationName());
			System.out.println("\tStarts at "+(float)relStartDays+" days");
			System.out.println("\tKeeps until "+(float)nextRelStartDays+" days");
			System.out.println("\tDuration: "+(float)(nextRelStartDays-relStartDays)+" days");
			
			File resultsFile = new File(config.getOutputDir(), "results_m5_preserve_chain.bin");
			Preconditions.checkState(resultsFile.exists(), "%s doesn't exist", resultsFile.getAbsoluteFile());
			
			if (fss == null) {
				fss = FaultSystemIO.loadSol(config.getFSS_File());
				rupSet = fss.getRupSet();
				erf = new FaultSystemSolutionERF_ETAS(fss);
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
				erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
			}
			
			if (tdProbFunc.size() == 0 || relStartDays - tdProbFunc.getMaxX() > 30d) {
				System.out.println("\tUpdating TD probs...");
				double startYear = 1970d + (double)myStartMillis/(double)ProbabilityModelsCalc.MILLISEC_PER_YEAR;
				erf.setParameter(HistoricOpenIntervalParam.NAME, startYear-1875d);
				erf.getTimeSpan().setStartTimeInMillis(myStartMillis);
				erf.getTimeSpan().setDuration(probDays/365.25);
				
				erf.updateForecast();
				if (garlockSources == null) {
					garlockSources = new ArrayList<>();
					HashSet<Integer> garlockFSSs = new HashSet<>(rupSet.getRupturesForParentSection(targetSectID));
					for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
						int fssIndex = erf.getFltSysRupIndexForSource(sourceID);
						if (garlockFSSs.contains(fssIndex) && rupSet.getMagForRup(fssIndex) >= minMag)
							garlockSources.add(sourceID);
					}
					System.out.println("Have "+garlockSources.size()+" garlock sources");
				}
				List<Double> erfProbs = new ArrayList<>();
				for (int sourceIndex : garlockSources)
					for (ProbEqkRupture rup : erf.getSource(sourceIndex))
						erfProbs.add(rup.getProbability());
				double tdProb = FaultSysSolutionERF_Calc.calcSummedProbs(erfProbs);
				tdProbFunc.set(relStartDays, tdProb);
				System.out.println("\t\tTD prob: "+tdProb);
			}
			
			List<ETAS_Catalog> catalogs = ETAS_CatalogIO.loadCatalogsBinary(resultsFile, minMag);
			
			List<Double> startDaysList = new ArrayList<>();
			for (double startDays=relStartDays; (float)startDays<(float)nextRelStartDays; startDays+=deltaDays)
				startDaysList.add(startDays);
			startDaysList.add(nextRelStartDays);
			
			ArbitrarilyDiscretizedFunc etasProbFunc = new ArbitrarilyDiscretizedFunc();
			etasProbFuncs.add(etasProbFunc);
			
			for (double startDays : startDaysList) {
				// startDays is relative to the overall start. calc start millis in an absolute sense
				long startMillis = overallStartMillis + (long)(startDays*millsPerDay);
				long endMillis = startMillis + probMillis;
				
				int numWith = 0;
				for (ETAS_Catalog catalog : catalogs) {
					boolean hasMatch = false;
					for (ETAS_EqkRupture rup : catalog) {
						if (hasMatch)
							break;
						long timeMillis = rup.getOriginTime();
						if (timeMillis < startMillis)
							continue;
						if (timeMillis >= endMillis)
							break;
						int fssIndex = rup.getFSSIndex();
						if (fssIndex >= 0) {
							Preconditions.checkState(rup.getMag() >= minMag);
							for (FaultSection sect : rupSet.getFaultSectionDataForRupture(fssIndex)) {
								if (sect.getParentSectionId() == targetSectID) {
									hasMatch = true;
									break;
								}
							}
						}
					}
					if (hasMatch)
						numWith++;
				}
				double prob = (double)numWith/(double)catalogs.size();
				System.out.println("\t\t"+(float)startDays+": "+(float)100d*prob+" %");
				
				etasProbFunc.set(startDays, prob);
			}
		}
		
		erf.updateForecast();
		
		// add TD point at end
		double endMillis = overallStartMillis + totalMaxDays*ProbabilityModelsCalc.MILLISEC_PER_DAY;
		double startYear = 1970d + (double)endMillis/(double)ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		erf.setParameter(HistoricOpenIntervalParam.NAME, startYear-1875d);
		erf.getTimeSpan().setStartTimeInMillis((long)endMillis);
		erf.getTimeSpan().setDuration(probDays/365.25);
		erf.updateForecast();
		List<Double> erfProbs = new ArrayList<>();
		for (int sourceIndex : garlockSources)
			for (ProbEqkRupture rup : erf.getSource(sourceIndex))
				erfProbs.add(rup.getProbability());
		double tdProb = FaultSysSolutionERF_Calc.calcSummedProbs(erfProbs);
		tdProbFunc.set(totalMaxDays, tdProb);
		
		ComcatAccessor accessor = new ComcatAccessor();
		Region region = configs.get(0).getComcatMetadata().region;
		ComcatRegion cReg = region instanceof ComcatRegion ?
				(ComcatRegion)region : new ComcatRegionAdapter(region);
		ObsEqkRupList comcatEvents = accessor.fetchEventList(null, overallStartMillis,
				(long)endMillis, -10d, ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH, cReg,
				false, false, StatUtils.min(dataMinMags));
		
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);
		
		String title = (int)probDays+" Day Garlock M≥"+(int)minMag+" Probabilities";
		Range xRange = new Range(0d, totalMaxDays);
		for (boolean logY : new boolean[] {false, true}) {
			Range yRange;
			String yAxisLabel;
			ArbitrarilyDiscretizedFunc myTDProbFunc;
			if (logY) {
				yRange = new Range(1e-4, 1e-1);
				yAxisLabel = "Probability";
				myTDProbFunc = tdProbFunc;
			} else {
				yRange = new Range(0d, 5d);
				yAxisLabel = "Probability (%)";
				myTDProbFunc = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : tdProbFunc)
					myTDProbFunc.set(pt.getX(), 100d*pt.getY());
			}
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			myTDProbFunc.setName("Long-Term Model");
			funcs.add(myTDProbFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			
			double maxProb = 0d;
			double minProb = 1d;
			
			List<XYTextAnnotation> anns = new ArrayList<>();
			
			for (int i=0; i<etasProbFuncs.size(); i++) {
				ArbitrarilyDiscretizedFunc etasProbFunc = etasProbFuncs.get(i);
				ArbitrarilyDiscretizedFunc combFunc = new ArbitrarilyDiscretizedFunc();
				ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
				ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : etasProbFunc) {
					tdProb = tdProbFunc.getInterpolatedY(pt.getX());
					double combProb = FaultSysSolutionERF_Calc.calcSummedProbs(tdProb, pt.getY());
					maxProb = Math.max(combProb, maxProb);
					minProb = Math.min(combProb, minProb);
					double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(
							pt.getY(), configs.get(0).getNumSimulations());
					conf[0] = FaultSysSolutionERF_Calc.calcSummedProbs(tdProb, conf[0]);
					conf[1] = FaultSysSolutionERF_Calc.calcSummedProbs(tdProb, conf[1]);
					if (!logY) {
						combProb *= 100d;
						conf[0] *= 100d;
						conf[1] *= 100d;
					}
					combFunc.set(pt.getX(), combProb);
					lowerFunc.set(pt.getX(), conf[0]);
					upperFunc.set(pt.getX(), conf[1]);
				}
				
				UncertainArbDiscDataset confFunc = new UncertainArbDiscDataset(combFunc, lowerFunc, upperFunc);
				funcs.add(0, confFunc);
				chars.add(0, new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, new Color(0, 0, 0, 35)));
				
				if (i == 0) {
					combFunc.setName("Short-Term Model");
//					confFunc.setName("Sampling Uncertainty");
				}
				funcs.add(combFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
				
				if (highlightProbNames.contains(configs.get(i).getOutputDir().getName())) {
					double prob = combFunc.getY(0);
					double yLoc;
					if (logY) {
						yLoc = prob;
					} else {
						yLoc = prob;
						prob /= 100d;
					}
					XYTextAnnotation upperAnn = new XYTextAnnotation(
							towSigFigPercent(prob), combFunc.getMinX()+2d, yLoc);
					upperAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
					upperAnn.setFont(annFont);
					anns.add(upperAnn);
					
					DefaultXY_DataSet xy = new DefaultXY_DataSet();
					xy.set(combFunc.getMinX(), yLoc);
					funcs.add(xy);
					chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Color.BLACK));
				}
			}
			
			for (int i=0; i<simStartDaysList.size(); i++) {
				double days = simStartDaysList.get(i);
				XY_DataSet line = new DefaultXY_DataSet();
				line.set(days, yRange.getLowerBound());
				line.set(days, yRange.getUpperBound());
				
				if (i == 0)
					line.setName("Model Updates");
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.LIGHT_GRAY));
			}
			if (logY) {
				// annotate
				
				// round them
//				maxProb = Math.pow(10, Math.ceil(Math.log10(maxProb*10d)))/10d;
//				minProb = Math.pow(10, Math.floor(Math.log10(minProb*10d)))/10d;
				
				DefaultXY_DataSet upper = new DefaultXY_DataSet();
				upper.set(0d, maxProb);
				upper.set(totalMaxDays, maxProb);
				funcs.add(upper);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
				
				DefaultXY_DataSet lower = new DefaultXY_DataSet();
				lower.set(0d, minProb);
				lower.set(totalMaxDays, minProb);
				funcs.add(lower);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
				
				XYTextAnnotation upperAnn = new XYTextAnnotation(towSigFigPercent(maxProb), 2d, maxProb);
				upperAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				upperAnn.setFont(annFont);
				anns.add(upperAnn);
				
				XYTextAnnotation lowerAnn = new XYTextAnnotation(towSigFigPercent(minProb), 2d, minProb);
				lowerAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				lowerAnn.setFont(annFont);
				anns.add(lowerAnn);
				
				tdProb = tdProbFunc.getMaxY();
				XYTextAnnotation tdAnn = new XYTextAnnotation(towSigFigPercent(tdProb), 2d, tdProb);
				tdAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				tdAnn.setFont(annFont);
				tdAnn.setPaint(Color.BLUE);
				anns.add(tdAnn);
			}
			
			// add data funcs
			for (int i=0; i<dataMinMags.length; i++) {
				double dataMinMag = dataMinMags[i];
				DefaultXY_DataSet dataFunc = new DefaultXY_DataSet();
				double dataY;
				if (logY) {
					double logLower = Math.log10(yRange.getLowerBound());
					double logUpper = Math.log10(yRange.getUpperBound());
					double logLen = logUpper - logLower;
					dataY = Math.pow(10, logLower + 0.975*logLen);
				} else {
					dataY = yRange.getLowerBound() + 0.975*yRange.getLength();
				}
				for (ObsEqkRupture rup : comcatEvents) {
					if (rup.getMag() < dataMinMag)
						continue;
					long time = rup.getOriginTime();
					long deltaTime = time - overallStartMillis;
					if (deltaTime < 0l)
						deltaTime = 0l;
					double rupDeltaDays = (double)deltaTime/millsPerDay;
					dataFunc.set(rupDeltaDays, dataY);
				}
				if (i == dataMinMags.length-1)
					dataFunc.setName("M≥"+(int)dataMinMag+" Aftershocks");
				else
					dataFunc.setName("M≥"+(int)dataMinMag);
				funcs.add(dataFunc);
				chars.add(dataChars[i]);
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, "Days Since M7.1", yAxisLabel);
			spec.setLegendVisible(true);
			spec.setPlotAnnotations(anns);
			
			TickUnits tus = new TickUnits();
			TickUnit tu = new NumberTickUnit(probDays);
			tus.add(tu);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setLegendFontSize(22);
			gp.setBackgroundColor(Color.WHITE);
			
			gp.drawGraphPanel(spec, false, logY, xRange, yRange);
			gp.getXAxis().setStandardTickUnits(tus);
			
			File file = new File(outputDir, prefix+(logY ? "_log" : ""));
			gp.getChartPanel().setSize(900, 500);
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		}
	}
	
	private static String towSigFigPercent(double prob) {
		prob *= 100d;
		if (prob > 1d)
			return new DecimalFormat("0.0").format(prob)+"%";
		if (prob > 0.1d)
			return new DecimalFormat("0.00").format(prob)+"%";
		if (prob > 0.01d)
			return new DecimalFormat("0.000").format(prob)+"%";
		return new DecimalFormat("0.0000").format(prob)+"%";
	}

}
