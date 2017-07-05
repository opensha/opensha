package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FSSRupsInRegionCache;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator.TestScenario;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.kevin.ucerf3.etas.MPJ_ETAS_Simulator;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

public class ETAS_MultiSimAnalysisTools {

	public static int calcNumWithMagAbove(List<List<ETAS_EqkRupture>> catalogs, double targetMinMag) {
		long ot = Math.round((2014.0 - 1970.0) * ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs
																							// at
																							// 2014
		return calcNumWithMagAbove(catalogs, ot, targetMinMag, -1, -1);
	}

	public static int calcNumWithMagAbove(List<List<ETAS_EqkRupture>> catalogs, long ot, double targetMinMag,
			int triggerParentID, int maxDaysAfter) {
		HashSet<Integer> triggerParentIDs = null;
		if (triggerParentID >= 0) {
			triggerParentIDs = new HashSet<Integer>();
			triggerParentIDs.add(triggerParentID);
		}
		int num = 0;
		long maxEventTime;
		if (maxDaysAfter > 0)
			maxEventTime = ot + maxDaysAfter * ProbabilityModelsCalc.MILLISEC_PER_DAY;
		else
			maxEventTime = -1;
		catalogLoop: for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (maxEventTime > 0 && rup.getOriginTime() > maxEventTime)
					break;
				boolean child = true;
				if (triggerParentID >= 0) {
					if (triggerParentIDs.contains(rup.getParentID()))
						// add this as a child
						triggerParentIDs.add(rup.getID());
					else
						// this is spontaneous or part of another chain
						child = false;
				}
				if (rup.getMag() > targetMinMag && child) {
					num++;
					continue catalogLoop;
				}
			}
		}
		String childAdd;
		if (triggerParentID >= 0)
			childAdd = " child";
		else
			childAdd = "";
		String dateAdd;
		if (maxDaysAfter > 0)
			dateAdd = " within " + maxDaysAfter + " days of start of catalog";
		else
			dateAdd = "";
		double percent = 100d * ((double) num / (double) catalogs.size());
		System.out.println(num + "/" + catalogs.size() + " (" + (float) percent + " %) of catalogs had" + childAdd
				+ " rup with M>" + (float) targetMinMag + dateAdd);
		return num;
	}

	public static List<Double> calcTotalMoments(List<List<ETAS_EqkRupture>> catalogs) {
		List<Double> ret = Lists.newArrayList();
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			ret.add(calcTotalMoment(catalog));
		}
		return ret;
	}

	public static double calcTotalMoment(List<ETAS_EqkRupture> catalog) {
		double moment = 0;
		for (ETAS_EqkRupture rup : catalog)
			moment += MagUtils.magToMoment(rup.getMag());
		return moment;
	}

	static FractileCurveCalculator getFractileCalc(EvenlyDiscretizedFunc[] mfds) {
		XY_DataSetList funcsList = new XY_DataSetList();
		List<Double> relativeWeights = Lists.newArrayList();
		for (int i = 0; i < mfds.length; i++) {
			Preconditions.checkNotNull(mfds[i]);
			funcsList.add(mfds[i]);
			relativeWeights.add(1d);
		}
		return new FractileCurveCalculator(funcsList, relativeWeights);
	}

	static double mfdMinMag = 2.55;
	static double mfdDelta = 0.1;
	static int mfdNumMag = 66;
	public static double mfdMinY = 1e-4;
	public static double mfdMaxY = 1e4;

	static int calcNumMagToTrim(List<List<ETAS_EqkRupture>> catalogs) {
		double minMag = mfdMinMag;
		// double catMinMag = Double.POSITIVE_INFINITY;
		HistogramFunction hist = new HistogramFunction(mfdMinMag, mfdNumMag, mfdDelta);
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				hist.add(hist.getClosestXIndex(rup.getMag()), 1d);
				// catMinMag = Math.min(catMinMag, rup.getMag());
			}
			// if (catMinMag < minMag + 0.5*mfdDelta)
			// // it's a full catalog
			// return 0;
		}
		double catModalMag = hist.getX(hist.getXindexForMaxY()) - 0.49 * mfdDelta;
		if (Double.isInfinite(catModalMag))
			throw new IllegalStateException("Empty catalogs!");
		int numToTrim = 0;
		while (catModalMag > (minMag + 0.5 * mfdDelta)) {
			minMag += mfdDelta;
			numToTrim++;
		}
		return numToTrim;
	}

	/**
	 * Plots an MFD to compare with the expected MFD:
	 * 
	 * for each magnitude bin find fract sims with NO supra seis PRIMARY in that
	 * bin plot 1 - fract
	 * 
	 * @param catalogs
	 * @param outputDir
	 * @param name
	 * @param prefix
	 * @throws IOException
	 */
	private static void plotExpectedSupraComparisonMFD(List<List<ETAS_EqkRupture>> catalogs, File outputDir,
			String name, String prefix) throws IOException {
		double minMag = mfdMinMag;
		int numMag = mfdNumMag;

		// see if we need to adjust
		int numToTrim = calcNumMagToTrim(catalogs);
		for (int i = 0; i < numToTrim; i++) {
			minMag += mfdDelta;
			numMag--;
		}

		ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);

		double rate = 1d / catalogs.size();

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);
			ArbIncrementalMagFreqDist subMFD = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() >= 0)
					subMFD.addResampledMagRate(rup.getMag(), rate, true);
			}
			for (int n = 0; n < subMFD.size(); n++)
				if (subMFD.getY(n) == 0d)
					mfd.add(n, rate);
		}
		// now take 1 minus
		for (int n = 0; n < mfd.size(); n++)
			mfd.set(n, 1d - mfd.getY(n));

		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		mfd.setName("Mean");
		funcs.add(mfd);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

		PlotSpec spec = new PlotSpec(funcs, chars, name + " Supra MFD Compare To Expected", "Magnitude",
				"Incremental Rate (1/yr)");
		// spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(mfdMinMag, mfd.getMaxX(), Math.pow(10d, Math.log10(mfdMinY) - 2),
				Math.pow(10d, Math.log10(mfdMaxY) - 2));

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void setFontSizes(HeadlessGraphPanel gp) {
		setFontSizes(gp, 0);
	}

	public static void setFontSizes(HeadlessGraphPanel gp, int addition) {
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(22 + addition);
		gp.setAxisLabelFontSize(24 + addition);
		gp.setPlotLabelFontSize(24 + addition);
	}

	private static ArbIncrementalMagFreqDist[] plotMFD(List<List<ETAS_EqkRupture>> catalogs, double duration,
			FaultSystemSolutionERF erfForComparison, File outputDir, String name, String prefix) throws IOException {
		// double minMag = 5.05;
		// int numMag = 41;
		// double delta = 0.1;
		// double minY = 1e-4;
		// double maxY = 1e2;

		double minMag = mfdMinMag;
		int numMag = mfdNumMag;

		// see if we need to adjust
		int numToTrim = calcNumMagToTrim(catalogs);
		if (numToTrim > 0)
			System.out.println("Trimming " + numToTrim + " MFD bins");
		for (int i = 0; i < numToTrim; i++) {
			minMag += mfdDelta;
			numMag--;
		}

		// ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag,
		// numMag, mfdDelta);
		// mfd.setName("Total");
		ArbIncrementalMagFreqDist[] subMFDs = new ArbIncrementalMagFreqDist[catalogs.size()];
		EvenlyDiscretizedFunc[] cmlSubMFDs = new EvenlyDiscretizedFunc[catalogs.size()];
		for (int i = 0; i < catalogs.size(); i++)
			subMFDs[i] = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);
			double myDuration;
			if (duration < 0)
				myDuration = calcDurationYears(catalog);
			else
				myDuration = duration;
			if (myDuration > 0) {
				double rateEach = 1d / myDuration;

				for (ETAS_EqkRupture rup : catalog) {
					subMFDs[i].addResampledMagRate(rup.getMag(), rateEach, true);
				}
				// for (int n=0; n<mfd.size(); n++)
				// mfd.add(n, subMFDs[i].getY(n)*rate);
			}
			cmlSubMFDs[i] = subMFDs[i].getCumRateDistWithOffset();
		}

		if (outputDir == null)
			return subMFDs;

		IncrementalMagFreqDist comparisonMFD = null;
		if (erfForComparison != null)
			comparisonMFD = ERF_Calculator.getTotalMFD_ForERF(erfForComparison, subMFDs[0].getMinX(),
					subMFDs[0].getMaxX(), subMFDs[0].size(), true);

		boolean[] cumulatives = { false, true };

		for (boolean cumulative : cumulatives) {
			// EvenlyDiscretizedFunc myMFD;
			EvenlyDiscretizedFunc[] mySubMFDs;
			String yAxisLabel;
			String myPrefix = prefix;
			if (myPrefix == null || myPrefix.isEmpty())
				myPrefix = "";
			else
				myPrefix += "_";
			myPrefix += "mfd_";
			if (cumulative) {
				// myMFD = mfd.getCumRateDistWithOffset();
				mySubMFDs = cmlSubMFDs;
				yAxisLabel = "Cumulative Rate (1/yr)";
				myPrefix += "cumulative";
			} else {
				// myMFD = mfd;
				mySubMFDs = subMFDs;
				yAxisLabel = "Incremental Rate (1/yr)";
				myPrefix += "incremental";
			}

			List<XY_DataSet> funcs = Lists.newArrayList();
			List<PlotCurveCharacterstics> chars = Lists.newArrayList();

			File csvFile = new File(outputDir, myPrefix + ".csv");

			double[] fractiles = { 0.025, 0.25, 0.75, 0.975 };

			if (comparisonMFD != null) {
				EvenlyDiscretizedFunc comp;
				if (cumulative)
					comp = comparisonMFD.getCumRateDistWithOffset();
				else
					comp = comparisonMFD;
				comp.setName("Long Term ERF");
				funcs.add(comp);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));
			}

			getFractilePlotFuncs(mySubMFDs, fractiles, true, funcs, chars, csvFile);

			PlotSpec spec = new PlotSpec(funcs, chars, name + " MFD", "Magnitude", yAxisLabel);
			spec.setLegendVisible(true);

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setUserBounds(mfdMinMag, subMFDs[0].getMaxX(), mfdMinY, mfdMaxY);

			setFontSizes(gp);

			gp.drawGraphPanel(spec, false, true);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPNG(new File(outputDir, myPrefix + ".png").getAbsolutePath());
			gp.saveAsPDF(new File(outputDir, myPrefix + ".pdf").getAbsolutePath());
			gp.saveAsTXT(new File(outputDir, myPrefix + ".txt").getAbsolutePath());
		}

		return subMFDs;
	}

	private static EvenlyDiscretizedFunc[] calcFractAboveZero(ArbIncrementalMagFreqDist[] subMFDs) {
		EvenlyDiscretizedFunc atFunc = new EvenlyDiscretizedFunc(subMFDs[0].getMinX(), subMFDs[0].getMaxX(),
				subMFDs[0].size());
		EvenlyDiscretizedFunc atOrAboveFunc = new EvenlyDiscretizedFunc(atFunc.getMinX() - atFunc.getDelta() * 0.5,
				atFunc.size(), atFunc.getDelta());

		double fractEach = 1d / subMFDs.length;

		for (int i = 0; i < subMFDs.length; i++) {
			ArbIncrementalMagFreqDist subMFD = subMFDs[i];
			int maxMagIndex = -1;
			for (int m = 0; m < subMFD.size(); m++) {
				if (subMFD.getY(m) > 0) {
					atFunc.add(m, fractEach);
					maxMagIndex = m;
				}
			}
			for (int m = 0; m <= maxMagIndex; m++)
				atOrAboveFunc.add(m, fractEach);
		}

		atFunc.setName("Fract With Mag");
		atOrAboveFunc.setName("Fract With ≥ Mag");

		return new EvenlyDiscretizedFunc[] { atFunc, atOrAboveFunc };
	}

	private static void plotFractWithMagAbove(List<List<ETAS_EqkRupture>> catalogs, ArbIncrementalMagFreqDist[] subMFDs,
			TestScenario scenario, File outputDir, String name, String prefix) throws IOException {
		if (subMFDs == null)
			subMFDs = plotMFD(catalogs, -1d, null, null, null, null);

		Preconditions.checkArgument(subMFDs.length > 0);
		Preconditions.checkArgument(subMFDs.length == catalogs.size());

		// double minMag = subMFDs[0].getMinX();
		// int numMag = subMFDs[0].size();
		double delta = subMFDs[0].getDelta();

		// EvenlyDiscretizedFunc atOrAboveFunc = new
		// EvenlyDiscretizedFunc(minMag-delta*0.5, numMag, delta);
		// EvenlyDiscretizedFunc atFunc = new EvenlyDiscretizedFunc(minMag,
		// numMag, delta);
		EvenlyDiscretizedFunc[] myFuncs = calcFractAboveZero(subMFDs);
		EvenlyDiscretizedFunc atFunc = myFuncs[0];
		EvenlyDiscretizedFunc atOrAboveFunc = myFuncs[1];

		double fractEach = 1d / subMFDs.length;
		double minY = Math.min(fractEach, 1d / 10000d);

		// for (ArbIncrementalMagFreqDist subMFD : subMFDs) {
		// int maxIndex = -1;
		// for (int i=0; i<numMag; i++) {
		// if (subMFD.getY(i) > 0d) {
		// atFunc.add(i, fractEach);
		// maxIndex = i;
		// }
		// }
		// for (int i=0; i<=maxIndex; i++)
		// atOrAboveFunc.add(i, fractEach);
		// }

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(atFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));

		funcs.add(atOrAboveFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));

		List<XYTextAnnotation> anns = null;

		if (scenario != null) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			double mag = scenario.getMagnitude();
			xy.setName("Scenario M=" + (float) mag);
			xy.set(mag, 0d);
			xy.set(mag, minY);
			xy.set(mag, fractEach);
			xy.set(mag, 1d);

			funcs.add(xy);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, new Color(0, 180, 0)));

			double fractAboveMag = (double) calcNumWithMagAbove(catalogs, mag) / catalogs.size();

			DecimalFormat df = new DecimalFormat("0.#");
			XYTextAnnotation ann = new XYTextAnnotation(
					" " + df.format(fractAboveMag * 100d) + "% > M" + df.format(mag), mag, fractAboveMag);
			ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
			ann.setTextAnchor(TextAnchor.BOTTOM_LEFT);
			Color red = new Color(180, 0, 0);
			ann.setPaint(red);
			anns = Lists.newArrayList(ann);

			xy = new DefaultXY_DataSet();
			xy.setName(null);
			xy.set(mag, fractAboveMag);

			funcs.add(xy);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 5f, red));
		}

		PlotSpec spec = new PlotSpec(funcs, chars, name + " Fract With Mag", "Magnitude", "Fraction Of Simulations");
		spec.setLegendVisible(true);
		spec.setPlotAnnotations(anns);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(atFunc.getMinX() - 0.5 * delta, atFunc.getMaxX(), minY, 1d);

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	/**
	 * Calculate the mode for a set of catalog MFDs. Works because y values are
	 * all integer values.
	 * 
	 * @param allFuncs
	 * @param fractCalc
	 * @return
	 */
	private static EvenlyDiscretizedFunc getCatalogMode(EvenlyDiscretizedFunc[] allFuncs,
			FractileCurveCalculator fractCalc) {
		EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(allFuncs[0].getMinX(), allFuncs[0].size(),
				allFuncs[0].getDelta());

		for (int i = 0; i < ret.size(); i++) {
			ArbDiscrEmpiricalDistFunc dist = fractCalc.getEmpiricalDist(i);
			double mode;
			if (dist.size() == 1)
				mode = dist.getX(0);
			else
				mode = dist.getMostCentralMode();

			ret.set(i, mode);

			// double x = ret.getX(i);
			// if (x == 6d || x == 5d || x == 4d) {
			// double median = fractCalc.getFractile(0.5).getY(i);
			// new GraphWindow(dist, "M"+(float)x+" Empirical Dist.
			// Mode="+(float)mode
			// +", Mdedian="+(float)median);
			// }
		}

		return ret;
	}

	public static void plotMagNum(List<List<ETAS_EqkRupture>> catalogs, File outputDir, String name, String prefix,
			TestScenario scenario, double expNumForM2p5, FaultSystemSolution fss) throws IOException {
		plotMagNum(catalogs, outputDir, name, prefix, scenario, expNumForM2p5, fss, Long.MAX_VALUE);
	}

	public static void plotMagNum(List<List<ETAS_EqkRupture>> catalogs, File outputDir, String name, String prefix,
			TestScenario scenario, double expNumForM2p5, FaultSystemSolution fss, long maxOT) throws IOException {
		double minMag = mfdMinMag;
		int numMag = mfdNumMag;

		// see if we need to adjust
		int numToTrim = calcNumMagToTrim(catalogs);
		if (numToTrim > 0)
			System.out.println("Trimming " + numToTrim + " MFD bins");
		for (int i = 0; i < numToTrim; i++) {
			minMag += mfdDelta;
			numMag--;
		}

		ArbIncrementalMagFreqDist[] subMagNums = new ArbIncrementalMagFreqDist[catalogs.size()];
		EvenlyDiscretizedFunc[] cmlSubMagNums = new EvenlyDiscretizedFunc[catalogs.size()];
		for (int i = 0; i < catalogs.size(); i++)
			subMagNums[i] = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);
		ArbIncrementalMagFreqDist primaryMFD = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);

		double primaryNumEach = 1d / catalogs.size();

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getOriginTime() > maxOT)
					break;
				subMagNums[i].addResampledMagRate(rup.getMag(), 1d, true);
				int gen = rup.getGeneration();
				Preconditions.checkState(gen != 0, "This catalog has spontaneous events!");
				if (gen == 1)
					primaryMFD.addResampledMagRate(rup.getMag(), primaryNumEach, true);
			}
			cmlSubMagNums[i] = subMagNums[i].getCumRateDistWithOffset();
		}

		boolean[] cumulatives = { false, true };

		EvenlyDiscretizedFunc[] myFuncs = calcFractAboveZero(subMagNums);
		EvenlyDiscretizedFunc atFunc = myFuncs[0];
		EvenlyDiscretizedFunc atOrAboveFunc = myFuncs[1];

		IncrementalMagFreqDist regionalGR = null;
		if (expNumForM2p5 > 0)
			regionalGR = ETAS_SimAnalysisTools.getTotalAftershockMFD_ForU3_RegionalGR(scenario.getMagnitude(),
					expNumForM2p5, fss);

		for (boolean cumulative : cumulatives) {
			EvenlyDiscretizedFunc[] mySubMagNums;
			String yAxisLabel;
			String myPrefix = prefix;
			if (myPrefix == null || myPrefix.isEmpty())
				myPrefix = "";
			else
				myPrefix += "_";
			myPrefix += "mag_num_";
			EvenlyDiscretizedFunc myAtFunc;
			EvenlyDiscretizedFunc myPrimaryFunc;
			EvenlyDiscretizedFunc myRegionalGR = null;
			if (cumulative) {
				// myMFD = mfd.getCumRateDistWithOffset();
				myPrimaryFunc = primaryMFD.getCumRateDistWithOffset();
				myPrimaryFunc.setName("Primary");
				mySubMagNums = cmlSubMagNums;
				yAxisLabel = "Cumulative Number";
				myPrefix += "cumulative";
				myAtFunc = atOrAboveFunc;
				if (regionalGR != null)
					myRegionalGR = regionalGR.getCumRateDistWithOffset();
			} else {
				// myMFD = mfd;
				myPrimaryFunc = primaryMFD;
				myPrimaryFunc.setName("Primary");
				mySubMagNums = subMagNums;
				yAxisLabel = "Incremental Number";
				myPrefix += "incremental";
				myAtFunc = atFunc;
				if (regionalGR != null)
					myRegionalGR = regionalGR;
				;
			}
			if (myRegionalGR != null)
				myRegionalGR.setName("GR");

			List<XY_DataSet> funcs = Lists.newArrayList();
			List<PlotCurveCharacterstics> chars = Lists.newArrayList();

			File csvFile = new File(outputDir, myPrefix + ".csv");

			double[] fractiles = { 0.025, 0.975 };

			// getFractilePlotFuncs(mySubMagNums, fractiles, true, funcs, chars,
			// csvFile);
			funcs.add(myPrimaryFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN.darker()));

			XY_DataSet meanFunc = getFractilePlotFuncs(mySubMagNums, fractiles, funcs, chars, csvFile, Color.BLACK,
					Color.BLUE, Color.CYAN, null, myAtFunc, myPrimaryFunc);

			if (myRegionalGR != null) {
				funcs.add(myRegionalGR);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.BLACK));
			}

			funcs.add(myAtFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));

			for (PlotCurveCharacterstics theChar : chars)
				theChar.setLineWidth(theChar.getLineWidth() * 2f);

			PlotSpec spec = new PlotSpec(funcs, chars, name, "Magnitude", yAxisLabel);
			spec.setLegendVisible(true);

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setUserBounds(myPrimaryFunc.getMinX(), subMagNums[0].getMaxX(), mfdMinY, mfdMaxY);
			gp.setLegendFontSize(20);

			setFontSizes(gp, 10);

			gp.drawGraphPanel(spec, false, true);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPNG(new File(outputDir, myPrefix + ".png").getAbsolutePath());
			gp.saveAsPDF(new File(outputDir, myPrefix + ".pdf").getAbsolutePath());
			gp.saveAsTXT(new File(outputDir, myPrefix + ".txt").getAbsolutePath());

			if (cumulative) {
				// do mean conf plot

				funcs = Lists.newArrayList();
				chars = Lists.newArrayList();

				ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
				upperFunc.setName("Upper 95%");
				ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
				lowerFunc.setName("Lower 95%");

				for (int i = 0; i < meanFunc.size(); i++) {
					double x = meanFunc.getX(i);
					double y = meanFunc.getY(i);

					if (y >= 1d) {
						upperFunc.set(x, y);
						lowerFunc.set(x, y);
					} else {
						double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(y, catalogs.size());
						lowerFunc.set(x, conf[0]);
						upperFunc.set(x, conf[1]);
					}
				}

				funcs.add(lowerFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));

				funcs.add(upperFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));

				funcs.add(meanFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

				spec = new PlotSpec(funcs, chars, name, "Magnitude", yAxisLabel);
				spec.setLegendVisible(true);

				gp = new HeadlessGraphPanel();
				gp.setUserBounds(myPrimaryFunc.getMinX(), subMagNums[0].getMaxX(), mfdMinY, mfdMaxY);
				gp.setLegendFontSize(20);

				setFontSizes(gp, 10);

				gp.drawGraphPanel(spec, false, true);
				gp.getChartPanel().setSize(1000, 800);
				gp.saveAsPNG(new File(outputDir, myPrefix + "_mean_with_conf.png").getAbsolutePath());
				gp.saveAsPDF(new File(outputDir, myPrefix + "_mean_with_conf.pdf").getAbsolutePath());
				gp.saveAsTXT(new File(outputDir, myPrefix + "_mean_with_conf.txt").getAbsolutePath());

			}
		}
	}

	private static void getFractilePlotFuncs(EvenlyDiscretizedFunc[] allFuncs, double[] fractiles, boolean mode,
			List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, File csvFile) throws IOException {
		Color fractileColor = Color.GREEN.darker();
		Color medianColor = Color.BLUE;
		Color modeColor = mode ? Color.CYAN : null;
		Color sdomColor = Color.RED.darker();
		getFractilePlotFuncs(allFuncs, fractiles, funcs, chars, csvFile, fractileColor, medianColor, modeColor,
				sdomColor);
	}

	private static XY_DataSet getFractilePlotFuncs(EvenlyDiscretizedFunc[] allFuncs, double[] fractiles,
			List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, File csvFile, Color fractileColor,
			Color medianColor, Color modeColor, Color sdomColor, EvenlyDiscretizedFunc... otherCSVFuncs)
			throws IOException {
		FractileCurveCalculator fractCalc = getFractileCalc(allFuncs);
		List<AbstractXY_DataSet> fractileFuncs = Lists.newArrayList();
		List<String> fractileNames = Lists.newArrayList();
		for (int i = 0; i < fractiles.length; i++)
			fractileNames.add((float) (fractiles[i] * 100d) + "%");

		for (int i = 0; i < fractiles.length; i++) {
			double fractile = fractiles[i];
			AbstractXY_DataSet fractFunc = fractCalc.getFractile(fractile);
			fractileFuncs.add(fractFunc);
			if (fractFunc instanceof IncrementalMagFreqDist) {
				// nasty hack to fix default naming of MFD functions when you
				// set name to null
				IncrementalMagFreqDist mfd = (IncrementalMagFreqDist) fractFunc;
				EvenlyDiscretizedFunc newFractFunc = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(),
						mfd.size());
				for (int j = 0; j < mfd.size(); j++)
					newFractFunc.set(j, mfd.getY(j));
				fractFunc = newFractFunc;
			}

			// String nameAdd;
			// if (i == 0 || i == fractiles.length-1)
			// nameAdd = " Fractile";
			// else
			// nameAdd = "";

			if (i == 0)
				fractFunc.setName(Joiner.on(",").join(fractileNames) + " Fractiles");
			else
				fractFunc.setName(null);
			// fractFunc.setName((float)(fractile*100d)+"%"+nameAdd);
			funcs.add(fractFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, fractileColor));
		}

		AbstractXY_DataSet median = fractCalc.getFractile(0.5);
		median.setName("Median");
		if (medianColor != null) {
			funcs.add(median);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, medianColor));
		}

		int numX = allFuncs[0].size();

		// will be added later
		AbstractXY_DataSet meanFunc = fractCalc.getMeanCurve();

		AbstractXY_DataSet modeFunc = null;
		if (modeColor != null) {
			modeFunc = getCatalogMode(allFuncs, fractCalc);
			modeFunc.setName("Mode");
			funcs.add(modeFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, modeColor));
		}

		double[] stdDevs = new double[numX];
		double[] sdoms = new double[numX];
		EvenlyDiscretizedFunc lower95_mean = new EvenlyDiscretizedFunc(allFuncs[0].getMinX(), numX,
				allFuncs[0].getDelta());
		lower95_mean.setName("Lower/Upper 95% of Mean");
		EvenlyDiscretizedFunc upper95_mean = new EvenlyDiscretizedFunc(allFuncs[0].getMinX(), numX,
				allFuncs[0].getDelta());
		// upper95_mean.setName("Upper 95% of Mean");
		upper95_mean.setName(null);

		for (int n = 0; n < numX; n++) {
			double[] vals = new double[allFuncs.length];
			for (int i = 0; i < allFuncs.length; i++)
				vals[i] = allFuncs[i].getY(n);
			stdDevs[n] = Math.sqrt(StatUtils.variance(vals));
			sdoms[n] = stdDevs[n] / Math.sqrt(allFuncs.length);

			double mean = meanFunc.getY(n);
			lower95_mean.set(n, mean - 1.98 * sdoms[n]);
			upper95_mean.set(n, mean + 1.98 * sdoms[n]);
		}
		if (sdomColor != null) {
			funcs.add(lower95_mean);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, sdomColor));
			funcs.add(upper95_mean);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, sdomColor));
		}

		meanFunc.setName("Mean");
		funcs.add(meanFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

		if (csvFile != null) {
			CSVFile<String> csv = new CSVFile<String>(true);
			List<String> header = Lists.newArrayList("Mag", "Mean", "Std Dev", "SDOM");
			for (double fract : fractiles)
				header.add("p" + (float) (fract * 100d) + "%");
			header.add("Median");
			if (modeFunc != null)
				header.add("Mode");
			header.add("Lower 95% of Mean");
			header.add("Upper 95% of Mean");
			for (EvenlyDiscretizedFunc otherFunc : otherCSVFuncs) {
				Preconditions.checkState(otherFunc.size() == meanFunc.size(), "Other func name mismatch");
				Preconditions.checkNotNull(otherFunc.getName(), "Other func must be named for CSV header");
				header.add(otherFunc.getName());
			}
			csv.addLine(header);

			// now mean and std dev
			for (int n = 0; n < meanFunc.size(); n++) {
				List<String> line = Lists.newArrayList(meanFunc.getX(n) + "", meanFunc.getY(n) + "", stdDevs[n] + "",
						sdoms[n] + "");
				for (AbstractXY_DataSet fractFunc : fractileFuncs)
					line.add(fractFunc.getY(n) + "");
				line.add(median.getY(n) + "");
				if (modeFunc != null)
					line.add(modeFunc.getY(n) + "");
				line.add(lower95_mean.getY(n) + "");
				line.add(upper95_mean.getY(n) + "");
				for (EvenlyDiscretizedFunc otherFunc : otherCSVFuncs)
					line.add(otherFunc.getY(n) + "");

				csv.addLine(line);
			}

			csv.writeToFile(csvFile);
		}

		return meanFunc;
	}

	public static void plotAftershockRateVsLogTimeHistForRup(List<List<ETAS_EqkRupture>> catalogs,
			TestScenario scenario, ETAS_ParameterList params, long rupOT_millis, File outputDir, String name,
			String prefix) throws IOException {
		EvenlyDiscretizedFunc[] funcsArray = new EvenlyDiscretizedFunc[catalogs.size()];

		double firstLogDay = -5;
		double lastLogDay = 5;
		double deltaLogDay = 0.2;

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);

			funcsArray[i] = ETAS_SimAnalysisTools.getAftershockRateVsLogTimeHistForRup(catalog, 0, rupOT_millis,
					firstLogDay, lastLogDay, deltaLogDay);
		}

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		double[] fractiles = { 0.025, 0.25, 0.75, 0.975 };

		getFractilePlotFuncs(funcsArray, fractiles, false, funcs, chars, null);

		if (params != null && scenario != null) {
			HistogramFunction targetFunc = ETAS_Utils.getRateWithLogTimeFunc(params.get_k(), params.get_p(),
					scenario.getMagnitude(), ETAS_Utils.magMin_DEFAULT, params.get_c(), firstLogDay, lastLogDay,
					deltaLogDay);
			targetFunc.setName("Expected");

			funcs.add(targetFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		}

		double maxY = 0;
		for (XY_DataSet xy : funcs)
			maxY = Math.max(maxY, xy.getMaxY());

		PlotSpec spec = new PlotSpec(funcs, chars, name + " Temporal Decay", "Log10 Time (Days)", "Rate (per day)");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(-4d, 3d, 1e-3, maxY * 1.2);

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void plotDistDecay(List<List<ETAS_EqkRupture>> catalogs, ETAS_ParameterList params,
			RuptureSurface surf, File outputDir, String name, String prefix) throws IOException {
		EvenlyDiscretizedFunc[] funcsArray = new EvenlyDiscretizedFunc[catalogs.size()];

		double histLogMin = -1.5;
		double histLogMax = 4.0;
		double histLogDelta = 0.2;

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);

			if (surf == null)
				funcsArray[i] = ETAS_SimAnalysisTools.getLogTriggerDistDecayDensityHist(catalog, histLogMin, histLogMax,
						histLogDelta);
			else
				funcsArray[i] = ETAS_SimAnalysisTools.getLogDistDecayDensityFromRupSurfaceHist(catalog, surf,
						histLogMin, histLogMax, histLogDelta);
		}

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		double[] fractiles = { 0.025, 0.25, 0.75, 0.975 };

		getFractilePlotFuncs(funcsArray, fractiles, false, funcs, chars, null);

		if (params != null) {
			double distDecay = params.get_q();
			double minDist = params.get_d();

			EvenlyDiscretizedFunc expectedLogDistDecay = ETAS_Utils.getTargetDistDecayDensityFunc(
					funcsArray[0].getMinX(), funcsArray[0].getMaxX(), funcsArray[0].size(), distDecay, minDist);
			expectedLogDistDecay.setName("Expected");
			expectedLogDistDecay.setInfo("(distDecay=" + distDecay + " and minDist=" + minDist + ")");

			funcs.add(expectedLogDistDecay);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		}

		double maxY = 0;
		double minY = Double.POSITIVE_INFINITY;
		for (XY_DataSet xy : funcs) {
			maxY = Math.max(maxY, xy.getMaxY());
			double minNonZero = Double.POSITIVE_INFINITY;
			for (Point2D pt : xy)
				if (pt.getY() > 0 && pt.getY() < minNonZero)
					minNonZero = pt.getY();
			if (!Double.isInfinite(minNonZero))
				minY = Math.min(minY, minNonZero);
		}

		String title;
		if (surf == null)
			title = name + " Trigger Loc Dist Decay";
		else
			title = name + " Rupture Surface Dist Decay";

		PlotSpec spec = new PlotSpec(funcs, chars, title, "Log10 Distance (km)", "Aftershock Density (per km)");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		gp.setUserBounds(histLogMin + 0.5 * histLogDelta, 3, minY, maxY * 1.2);

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	private static void plotNumEventsHistogram(List<List<ETAS_EqkRupture>> catalogs, File outputDir, String prefix)
			throws IOException {
		MinMaxAveTracker track = new MinMaxAveTracker();

		for (List<ETAS_EqkRupture> catalog : catalogs)
			track.addValue(catalog.size());

		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(track.getMin(), track.getMax(), 5000d);

		for (List<ETAS_EqkRupture> catalog : catalogs)
			hist.add((double) catalog.size(), 1d);

		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));

		PlotSpec spec = new PlotSpec(funcs, chars, "# Events Distribution", "# Events", "# Catalogs");

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	private static void plotTotalMomentHistogram(List<List<ETAS_EqkRupture>> catalogs, File outputDir, String prefix)
			throws IOException {

		double[] moments = new double[catalogs.size()];

		for (int i = 0; i < catalogs.size(); i++)
			for (ETAS_EqkRupture rup : catalogs.get(i))
				moments[i] += MagUtils.magToMoment(rup.getMag());

		double[] log10Moments = new double[moments.length];
		for (int i = 0; i < moments.length; i++)
			log10Moments[i] = Math.log10(moments[i]);

		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(StatUtils.min(log10Moments),
				StatUtils.max(log10Moments), 0.05);

		for (double val : log10Moments)
			hist.add(val, 1d);

		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));

		PlotSpec spec = new PlotSpec(funcs, chars, "Moment Distribution", "Log10(Total Moment) (N-m)", "# Catalogs");

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	/**
	 * Plot section participation and trigger (nucleation) rates for each fault
	 * subsection. All supraseismic events will be included, so if only children
	 * or primary events are wanted then it should be filtered externally.
	 * 
	 * @param catalogs
	 *            ETAS catalogs
	 * @param rupSet
	 *            rupSet
	 * @param minMags
	 *            array of minimum magnitudes
	 * @param outputDir
	 *            directory in which to write plots
	 * @param titleAdd
	 *            if non null, appended to the end of the title for the map
	 * @param prefix
	 *            file name prefix
	 * @throws IOException
	 * @throws RuntimeException
	 * @throws GMT_MapException
	 */
	public static void plotSectRates(List<List<ETAS_EqkRupture>> catalogs, double duration, FaultSystemRupSet rupSet,
			double[] minMags, File outputDir, String titleAdd, String prefix)
			throws IOException, GMT_MapException, RuntimeException {
		plotSectRates(catalogs, duration, rupSet, minMags, outputDir, titleAdd, prefix, Long.MIN_VALUE);

	}

	public static void plotSectRates(List<List<ETAS_EqkRupture>> catalogs, double duration, FaultSystemRupSet rupSet,
			double[] minMags, File outputDir, String titleAdd, String prefix, long maxOT)
			throws IOException, GMT_MapException, RuntimeException {
		List<double[]> particRatesList = Lists.newArrayList();
		for (int i = 0; i < minMags.length; i++)
			particRatesList.add(new double[rupSet.getNumSections()]);
		List<double[]> triggerRatesList = Lists.newArrayList();
		for (int i = 0; i < minMags.length; i++)
			triggerRatesList.add(new double[rupSet.getNumSections()]);

		Map<Integer, List<Location>> locsForSectsMap = Maps.newHashMap();

		HashSet<Integer> debugSections = null;
		// debugSections = new
		// HashSet<Integer>(rupSet.getSectionsIndicesForRup(193821));

		double maxDuration = 0;
		FaultPolyMgr faultPolyMgr = FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				InversionTargetMFDs.FAULT_BUFFER);

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			double fractionalRate;
			if (maxOT > 0) {
				// no scaling, expected num
				fractionalRate = 1d / (catalogs.size());
			} else {
				double myDuration;
				if (duration < 0)
					myDuration = calcDurationYears(catalog);
				else
					myDuration = duration;
				if (myDuration == 0)
					continue;
				maxDuration = Math.max(maxDuration, myDuration);
				fractionalRate = 1d / (catalogs.size() * myDuration);
			}

			for (ETAS_EqkRupture rup : catalog) {
				int rupIndex = rup.getFSSIndex();
				if (rupIndex < 0)
					// not supra-seismogenic
					continue;
				if (maxOT > 0 && rup.getOriginTime() > maxOT)
					break;
				int closestSectIndex = -1;
				boolean notYetFound = true;

				Location hypocenter = rup.getHypocenterLocation();
				Preconditions.checkNotNull(hypocenter);

				for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
					for (int i = 0; i < minMags.length; i++)
						if (rup.getMag() >= minMags[i])
							particRatesList.get(i)[sectIndex] += fractionalRate;

					// TODO This isn't quite right because more than one section
					// polygon might contain the hypocenter;
					// this will end up taking the first one. I believe the only
					// way to do this right is to save
					// the info in the first place
					if (notYetFound && faultPolyMgr.getPoly(sectIndex).contains(hypocenter)) {
						closestSectIndex = sectIndex;
						notYetFound = false;
					}

					// // now calculate distance
					// List<Location> surfLocs = locsForSectsMap.get(sectIndex);
					// if (surfLocs == null) {
					// // first time we have encountered this section
					// FaultSectionPrefData sect =
					// rupSet.getFaultSectionData(sectIndex);
					// surfLocs = sect.getStirlingGriddedSurface(1d, false,
					// true).getEvenlyDiscritizedPerimeter();
					// locsForSectsMap.put(sectIndex, surfLocs);
					// }
					//
					// for (Location loc : surfLocs) {
					// double dist =
					// LocationUtils.linearDistanceFast(hypocenter, loc);
					// if (dist < closestDist) {
					// closestDist = dist;
					// closestSectIndex = sectIndex;
					// }
					// }
				}

				if (closestSectIndex < 0) {
					// fall back to distance calculation - polygon precision
					// issues
					double closestDist = Double.POSITIVE_INFINITY;
					for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
						// now calculate distance
						List<Location> surfLocs = locsForSectsMap.get(sectIndex);
						if (surfLocs == null) {
							// first time we have encountered this section
							FaultSectionPrefData sect = rupSet.getFaultSectionData(sectIndex);
							surfLocs = sect.getStirlingGriddedSurface(1d, false, true).getEvenlyDiscritizedPerimeter();
							locsForSectsMap.put(sectIndex, surfLocs);
						}

						for (Location loc : surfLocs) {
							double dist = LocationUtils.linearDistanceFast(hypocenter, loc);
							if (dist < closestDist) {
								closestDist = dist;
								closestSectIndex = sectIndex;
							}
						}
					}
					Preconditions.checkState(closestDist < 0.1d,
							"reverted to distance due to polygon issue but too far from perimeter: %s km", closestDist);
				}

				Preconditions.checkState(closestSectIndex >= 0, "fssIndex=%s, hypo=%s", rup.getFSSIndex(), hypocenter);
				for (int i = 0; i < minMags.length; i++)
					if (rup.getMag() >= minMags[i])
						triggerRatesList.get(i)[closestSectIndex] += fractionalRate;
				if (debugSections != null && debugSections.contains(closestSectIndex))
					System.out.println("Ruptured " + closestSectIndex + ":\t" + ETAS_CatalogIO.getEventFileLine(rup));
			}
		}

		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
		double maxRate = 0;
		for (double[] particRates : particRatesList)
			maxRate = Math.max(maxRate, StatUtils.max(particRates));
		double fractionalRate;
		if (maxOT > 0) {
			fractionalRate = 1d / catalogs.size();
		} else {
			if (maxDuration == 0d)
				return;
			fractionalRate = 1d / Math.max(1d, Math.round(catalogs.size() * maxDuration));
		}
		double cptMin = Math.log10(fractionalRate);
		double cptMax = Math.ceil(Math.log10(maxRate));
		if (!Doubles.isFinite(cptMin) || !Doubles.isFinite(cptMax))
			return;
		while (cptMax <= cptMin)
			cptMax++;
		cpt = cpt.rescale(cptMin, cptMax);
		cpt.setBelowMinColor(Color.LIGHT_GRAY);

		List<LocationList> faults = Lists.newArrayList();
		for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++)
			faults.add(rupSet.getFaultSectionData(sectIndex).getFaultTrace());

		Region region = new CaliforniaRegions.RELM_TESTING();

		CSVFile<String> particCSV = new CSVFile<String>(true);
		CSVFile<String> triggerCSV = new CSVFile<String>(true);

		List<String> header = Lists.newArrayList("Section Name", "Section ID");
		for (int i = 0; i < minMags.length; i++) {
			if (minMags[i] > 1)
				header.add("M≥" + (float) minMags[i]);
			else
				header.add("Total");
		}

		particCSV.addLine(header);
		triggerCSV.addLine(header);

		if (titleAdd == null)
			titleAdd = "";

		if (!titleAdd.isEmpty() && !titleAdd.startsWith(" "))
			titleAdd = " " + titleAdd;

		for (int i = 0; i < minMags.length; i++) {
			double[] particRates = particRatesList.get(i);
			double[] triggerRates = triggerRatesList.get(i);

			String magStr;
			String prefixAdd;
			if (minMags[i] > 1) {
				magStr = " M>=" + (float) minMags[i];
				prefixAdd = "_m" + (float) minMags[i];
			} else {
				magStr = "";
				prefixAdd = "";
			}
			String particTitle;
			String triggerTitle;
			if (maxOT > 0) {
				particTitle = "Log10" + magStr + " Participation Exp. Num" + titleAdd;
				triggerTitle = "Log10" + magStr + " Trigger Exp. Num" + titleAdd;
			} else {
				particTitle = "Log10" + magStr + " Participation Rate" + titleAdd;
				triggerTitle = "Log10" + magStr + " Trigger Rate" + titleAdd;
			}

			FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(particRates), region, outputDir,
					prefix + "_partic" + prefixAdd, false, false, particTitle);

			FaultBasedMapGen.makeFaultPlot(cpt, faults, FaultBasedMapGen.log10(triggerRates), region, outputDir,
					prefix + "_trigger" + prefixAdd, false, false, triggerTitle);

			for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
				int row = sectIndex + 1;
				if (i == 0) {
					List<String> line = Lists.newArrayList(rupSet.getFaultSectionData(sectIndex).getSectionName(),
							sectIndex + "");
					for (int m = 0; m < minMags.length; m++)
						line.add("");
					particCSV.addLine(line);
					triggerCSV.addLine(Lists.newArrayList(line)); // need to
																	// clone so
																	// we don't
																	// overwrite
																	// values
				}
				int col = i + 2;
				particCSV.set(row, col, particRates[sectIndex] + "");
				triggerCSV.set(row, col, triggerRates[sectIndex] + "");
			}
		}
		particCSV.writeToFile(new File(outputDir, prefix + "_partic.csv"));
		triggerCSV.writeToFile(new File(outputDir, prefix + "_trigger.csv"));
	}

	private static void plotMaxTriggeredMagHist(List<List<ETAS_EqkRupture>> catalogs,
			List<List<ETAS_EqkRupture>> primaryCatalogs, TestScenario scenario, File outputDir, String name,
			String prefix) throws IOException {
		HistogramFunction fullHist = HistogramFunction.getEncompassingHistogram(2.5, 9d, 0.1);
		HistogramFunction primaryHist = null;
		if (primaryCatalogs != null)
			primaryHist = new HistogramFunction(fullHist.getMinX(), fullHist.size(), fullHist.getDelta());

		int numEmpty = 0;

		List<Double> maxMags = Lists.newArrayList();
		for (int i = 0; i < catalogs.size(); i++) {
			if (catalogs.get(i).isEmpty())
				numEmpty++;
			else
				maxMags.add(ETAS_SimAnalysisTools.getMaxMag(catalogs.get(i)));
		}
		populateHistWithInfo(fullHist, Doubles.toArray(maxMags));

		int numPrimaryEmpty = 0;
		if (primaryCatalogs != null) {
			maxMags = Lists.newArrayList();
			for (int i = 0; i < primaryCatalogs.size(); i++) {
				if (primaryCatalogs.get(i).isEmpty())
					numPrimaryEmpty++;
				else
					maxMags.add(ETAS_SimAnalysisTools.getMaxMag(primaryCatalogs.get(i)));
			}
			populateHistWithInfo(primaryHist, Doubles.toArray(maxMags));
		}

		double maxY = fullHist.getMaxY() * 1.1;

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(fullHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		fullHist.setName("All Children");

		if (primaryHist != null) {
			funcs.add(primaryHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.CYAN));
			primaryHist.setName("Primary Aftershocks");
		}

		if (scenario != null) {
			XY_DataSet scenarioMag = new DefaultXY_DataSet();
			scenarioMag.set(scenario.getMagnitude(), 0d);
			scenarioMag.set(scenario.getMagnitude(), fullHist.getMaxY());
			scenarioMag.set(scenario.getMagnitude(), maxY);
			scenarioMag.setName("Scenario M=" + (float) scenario.getMagnitude());

			funcs.add(scenarioMag);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
		}

		PlotSpec spec = new PlotSpec(funcs, chars, name + " Max Mag Hist", "Magnitude", "Num Simulations");
		spec.setLegendVisible(true);

		double histDelta = fullHist.getDelta();
		double minX = fullHist.getMinX() - 0.5 * histDelta;
		double maxX = fullHist.getMaxX() - 0.5 * histDelta;

		if (numEmpty > 0 || numPrimaryEmpty > 0) {
			String primaryStr = "";
			if (numPrimaryEmpty > 0) {
				primaryStr = numPrimaryEmpty + "/" + primaryCatalogs.size() + " primary";
				if (numEmpty > 0)
					primaryStr = " (" + primaryStr + ")";
			}
			String text;
			if (numEmpty > 0)
				text = numEmpty + "/" + catalogs.size() + primaryStr;
			else
				text = primaryStr;
			text += " catalogs empty and excluded";
			XYTextAnnotation ann = new XYTextAnnotation(text, minX + (maxX - minX) * 0.05, maxY * 0.95);
			ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			List<XYTextAnnotation> anns = Lists.newArrayList(ann);
			spec.setPlotAnnotations(anns);
		}

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false);
		gp.drawGraphPanel(spec, false, false, new Range(minX, maxX), new Range(0, maxY));
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	private static void plotNumEventsPerGeneration(List<List<ETAS_EqkRupture>> catalogs, File outputDir, String name,
			String prefix) throws IOException {
		int maxGeneration = 20;

		EvenlyDiscretizedFunc[] allFuncs = new EvenlyDiscretizedFunc[catalogs.size()];
		boolean hasZero = false;
		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);
			int[] counts = ETAS_SimAnalysisTools.getNumAftershocksForEachGeneration(catalog, maxGeneration);
			allFuncs[i] = new EvenlyDiscretizedFunc(0, (double) maxGeneration, counts.length);
			for (int j = 0; j < counts.length; j++)
				allFuncs[i].set(j, counts[j]);
			hasZero = hasZero || counts[0] > 0;
		}

		double[] fractiles = { 0.025, 0.975 };

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		getFractilePlotFuncs(allFuncs, fractiles, true, funcs, chars, null);

		double maxY = 0;
		for (XY_DataSet func : funcs)
			maxY = Math.max(maxY, func.getMaxY());

		PlotSpec spec = new PlotSpec(funcs, chars, name + " Generations", "Generation", "Count");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		double minX;
		if (hasZero)
			minX = 0;
		else
			minX = 1;
		gp.setUserBounds(minX, maxGeneration, 0, maxY * 1.1);

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	/**
	 * Fills the histogram with the given data and puts statistics in the info
	 * string
	 * 
	 * @param hist
	 * @param data
	 */
	private static void populateHistWithInfo(HistogramFunction hist, double[] data) {
		if (data.length == 0)
			return;
		for (double val : data)
			hist.add(val, 1d);

		double mean = StatUtils.mean(data);
		double median = DataUtils.median(data);
		double mode = hist.getX(hist.getXindexForMaxY());

		String infoStr = "Mean: " + (float) mean + "\nMedian: " + (float) median + "\nMode: " + (float) mode;

		hist.setInfo(infoStr);
	}

	private static final double MILLIS_PER_YEAR = 365.25 * 24 * 60 * 60 * 1000;

	public static void plotCubeNucleationRates(List<List<ETAS_EqkRupture>> catalogs, double duration, File outputDir,
			String name, String prefix, double[] mags) throws IOException, GMT_MapException {
		plotCubeNucleationRates(catalogs, catalogs.size(), duration, Long.MAX_VALUE, outputDir, name, prefix, mags,
				false);
	}

	public static void plotCubeNucleationRates(Iterable<List<ETAS_EqkRupture>> catalogs, int numCatalogs,
			double duration, long maxOT, File outputDir, String name, String prefix, double[] mags, boolean downloadZip)
			throws IOException, GMT_MapException {
		double discr = 0.02;
		GriddedRegion reg = new GriddedRegion(new CaliforniaRegions.RELM_TESTING(), discr, GriddedRegion.ANCHOR_0_0);

		GriddedGeoDataSet[] xyzs = new GriddedGeoDataSet[mags.length];
		for (int i = 0; i < xyzs.length; i++)
			xyzs[i] = new GriddedGeoDataSet(reg, false);

		int count = 0;
		int numSkipped = 0;

		double maxDuration = 0d;
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			if (numCatalogs > 20000 && count % 5000 == 0)
				System.out.println("Gridded nucl, processing catalog " + count + "/" + numCatalogs);

			count++;
			double myDuration;
			if (duration < 0)
				myDuration = calcDurationYears(catalog);
			else
				myDuration = duration;
			if (myDuration == 0)
				continue;
			maxDuration = Math.max(myDuration, maxDuration);
			double rateEach = 1d / (numCatalogs * myDuration);

			for (ETAS_EqkRupture rup : catalog) {
				if (maxOT > 0 && rup.getOriginTime() > maxOT)
					break;
				double mag = rup.getMag();
				Location loc = rup.getHypocenterLocation();
				int index = reg.indexForLocation(loc);
				// Preconditions.checkState(index > 0);
				if (index < 0) {
					numSkipped++;
					continue;
				}
				for (int i = 0; i < mags.length; i++)
					if (mag >= mags[i])
						xyzs[i].set(index, xyzs[i].get(index) + rateEach);
			}
		}
		Preconditions.checkState(count == numCatalogs);

		System.out.println("Skipped " + numSkipped + " events outside of region");

		double scalar = 1d / (numCatalogs * Math.max(1d, Math.round(maxDuration)));
		// for (GriddedGeoDataSet xyz : xyzs) // now done earlier
		// xyz.scale(scalar);

		// now log10
		for (GriddedGeoDataSet xyz : xyzs)
			xyz.log10();

		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();

		Region plotReg = new Region(new Location(reg.getMinGridLat(), reg.getMinGridLon()),
				new Location(reg.getMaxGridLat(), reg.getMaxGridLon()));

		for (int i = 0; i < mags.length; i++) {
			GriddedGeoDataSet xyz = xyzs[i];

			double minZ = Math.floor(Math.log10(scalar));
			double maxZ = Math.ceil(xyz.getMaxZ());
			if (xyz.getMaxZ() == Double.NEGATIVE_INFINITY)
				maxZ = minZ + 4;
			if (maxZ == minZ)
				maxZ++;

			Preconditions.checkState(minZ < maxZ, "minZ=%s >= maxZ=%s", minZ, maxZ);

			double mag = mags[i];
			String label = "Log10 M>=" + (float) mag;
			if (duration == 1d)
				label += " Expected Num";
			else
				label += " Nucleation Rate";
			String myPrefix = prefix + "_m" + (float) mag;
			String baseURL = FaultBasedMapGen.plotMap(outputDir, myPrefix, false, FaultBasedMapGen
					.buildMap(cpt.rescale(minZ, maxZ), null, null, xyzs[i], discr, plotReg, false, label));
			if (downloadZip)
				FileUtils.downloadURL(baseURL + "/allFiles.zip", new File(outputDir, myPrefix + ".zip"));
		}
	}

	private static List<List<ETAS_EqkRupture>> getOnlyAftershocksFromHistorical(List<List<ETAS_EqkRupture>> catalogs) {
		List<List<ETAS_EqkRupture>> ret = Lists.newArrayList();

		long countHist = 0l;
		long countAll = 0l;

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			// HashSet<Integer> eventIDs = new HashSet<Integer>();
			// detect if an event is an aftershock from a historical event by
			// the fact that
			// it will have a parent whose ID is less than the minimum ID in the
			// catalog
			int minIndex = Integer.MAX_VALUE;
			for (ETAS_EqkRupture rup : catalog)
				if (rup.getID() < minIndex)
					minIndex = rup.getID();

			List<ETAS_EqkRupture> hist = Lists.newArrayList();

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getParentID() >= 0 && rup.getParentID() < minIndex)
					hist.add(rup);
			}

			ret.add(hist);
			countHist += hist.size();
			countAll += catalog.size();
		}

		double percent = 100d * ((double) countHist / (double) countAll);

		System.out.println(countHist + "/" + countAll + " (" + (float) percent + " %) are historical aftershocks");

		return ret;
	}

	/**
	 * This gets a list of historical earthquake descendants for each catalog
	 * (event is included if it's most distant relative is an historic event,
	 * where the latter have event IDs that are less than the least value in the
	 * catalog)
	 * 
	 * @param catalogs
	 * @return
	 */
	private static List<List<ETAS_EqkRupture>> getAllDescendentsFromHistorical(List<List<ETAS_EqkRupture>> catalogs) {
		List<List<ETAS_EqkRupture>> ret = Lists.newArrayList();

		long countHist = 0l;
		long countAll = 0l;

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			HashMap<Integer, ETAS_EqkRupture> map = new HashMap<Integer, ETAS_EqkRupture>();
			// detect if an event is an aftershock from a historical event by
			// the fact that
			// it will have a parent whose ID is less than the minimum ID in the
			// catalog
			int minIndex = Integer.MAX_VALUE;
			for (ETAS_EqkRupture rup : catalog) {
				map.put(rup.getID(), rup);
				if (rup.getID() < minIndex)
					minIndex = rup.getID();
			}

			List<ETAS_EqkRupture> hist = Lists.newArrayList();

			for (ETAS_EqkRupture rup : catalog) {
				int parID = rup.getParentID();
				int currentID = rup.getID();
				while (parID != -1) { // find the oldest descendant
					currentID = parID;
					if (currentID < minIndex) // break because currentID won't
												// be in the catalog
						break;
					parID = map.get(currentID).getParentID();
				}
				if (currentID < minIndex)
					hist.add(rup);
			}

			ret.add(hist);
			countHist += hist.size();
			countAll += catalog.size();
		}

		double percent = 100d * ((double) countHist / (double) countAll);

		System.out.println(countHist + "/" + countAll + " (" + (float) percent + " %) are historical aftershocks");

		return ret;
	}

	private static void writeTimeFromPrevSupraHist(List<List<ETAS_EqkRupture>> catalogs, File outputDir)
			throws IOException {
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(0d, 20d, 1d);

		List<Double> allVals = Lists.newArrayList();

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			if (catalog.size() < 2)
				continue;
			// long durationMillis =
			// catalog.get(catalog.size()-1).getOriginTime() -
			// catalog.get(0).getOriginTime();
			// double myDuration = (double)durationMillis/MILLIS_PER_YEAR;
			long startTime = catalog.get(0).getOriginTime();
			long endTime = catalog.get(catalog.size() - 1).getOriginTime();
			long durationMillis = endTime - startTime;
			Preconditions.checkState(durationMillis > 0l);

			int num = 10000;
			long delta = durationMillis / num;
			Preconditions.checkState(delta > 0);

			int catIndexBeforeTime = 0;
			long prevSupra = Long.MIN_VALUE;
			for (long time = startTime; time < endTime; time += delta) {
				for (int i = catIndexBeforeTime; i < catalog.size(); i++) {
					ETAS_EqkRupture e = catalog.get(i);
					if (e.getOriginTime() > time)
						break;
					catIndexBeforeTime = i;
					if (e.getFSSIndex() >= 0)
						prevSupra = e.getOriginTime();
				}
				if (prevSupra > Long.MIN_VALUE) {
					long curDelta = time - prevSupra;
					Preconditions.checkState(curDelta >= 0);
					double curDeltaYears = curDelta / MILLIS_PER_YEAR;
					if (curDeltaYears > hist.getMaxX())
						hist.add(hist.size() - 1, 1d);
					else
						hist.add(curDeltaYears, 1d);
					allVals.add(curDeltaYears);
				}
			}
		}
		hist.normalizeBySumOfY_Vals();

		HistogramFunction cmlHist = new HistogramFunction(hist.getMinX() - hist.getDelta() * 0.5, hist.size(),
				hist.getDelta());
		double cmlVal = 0d;
		for (int i = hist.size(); --i >= 0;) {
			double val = hist.getY(i);
			cmlVal += val;
			cmlHist.set(i, cmlVal);
		}

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		hist.setName("Histogram");

		funcs.add(cmlHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY));
		cmlHist.setName("Cumulative (≥) Histogram");

		double[] allValsArray = Doubles.toArray(allVals);
		double mean = StatUtils.mean(allValsArray);
		double median = DataUtils.median(allValsArray);

		XY_DataSet meanLine = new DefaultXY_DataSet();
		meanLine.set(mean, 0d);
		meanLine.set(mean, 1d);
		meanLine.setName("Mean=" + (float) mean);

		funcs.add(meanLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.BLUE));

		XY_DataSet medianLine = new DefaultXY_DataSet();
		medianLine.set(median, 0d);
		medianLine.set(median, 1d);
		medianLine.setName("Median=" + (float) median);

		funcs.add(medianLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GREEN.darker()));

		PlotSpec spec = new PlotSpec(funcs, chars, "Time Since Last Supra-Seosmogenic Hist", "Time (years)", "Density");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false, new Range(0, hist.getMaxX() + 0.5 * hist.getDelta()), new Range(0d, 1d));
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, "time_since_last_supra.png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, "time_since_last_supra.pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, "time_since_last_supra.txt").getAbsolutePath());
	}

	/**
	 * Writes out catalogs with minimum, median, and maximum total moment
	 * release to the given directory
	 * 
	 * @param catalogs
	 * @param outputDir
	 * @throws IOException
	 */
	private static void writeCatalogsForViz(List<List<ETAS_EqkRupture>> catalogs, TestScenario scenario, File outputDir,
			int numEach) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		double[] fractiles = { 0, 0.5, 1 };

		Preconditions.checkState(catalogs.size() > numEach);
		// List<Double> sortables = calcTotalMoments(catalogs);
		List<Double> sortables = Lists.newArrayList();
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			double num = 0;
			for (ETAS_EqkRupture rup : catalog)
				if (rup.getMag() > 6.5)
					num++;
			sortables.add(num);
		}

		String infoStr;
		if (scenario != null) {
			if (scenario.getFSS_Index() >= 0) {
				infoStr = "FSS simulation. M=" + scenario.getMagnitude() + ", fss ID=" + scenario.getFSS_Index();
			} else {
				Location loc = scenario.getLocation();
				infoStr = "Pt Source. M=" + scenario.getMagnitude() + ", " + loc.getLatitude() + ", "
						+ loc.getLongitude() + ", " + loc.getDepth();
			}
		} else {
			infoStr = "Spontaneous events";
		}

		List<ComparablePairing<Double, List<ETAS_EqkRupture>>> pairings = ComparablePairing.build(sortables, catalogs);
		// increasing in moment
		Collections.sort(pairings);

		for (double fractile : fractiles) {
			int index = (int) ((catalogs.size() - 1) * fractile);
			while (index + numEach >= catalogs.size())
				index--;
			for (int i = 0; i < numEach; i++) {
				int myIndex = index + i;
				ComparablePairing<Double, List<ETAS_EqkRupture>> pairing = pairings.get(myIndex);

				File subDir = new File(outputDir, "fract_" + (float) fractile + "_cat" + i);
				Preconditions.checkState(subDir.exists() || subDir.mkdir());

				List<ETAS_EqkRupture> catalog = pairing.getData();

				File infoFile = new File(subDir, "infoString.txt");

				FileWriter fw = new FileWriter(infoFile);
				fw.write(infoStr + "\n");
				fw.write("\n");
				fw.write("Total num ruptures: " + catalog.size() + "\n");
				fw.write("Total moment: " + pairing.getComparable() + "\n");
				fw.write("Max triggered mag: " + ETAS_SimAnalysisTools.getMaxMag(catalog) + "\n");
				fw.close();

				File catalogFile = new File(subDir, "simulatedEvents.txt");
				ETAS_CatalogIO.writeEventDataToFile(catalogFile, catalog);
			}
		}
	}

	public static void plotSectParticScatter(Iterable<List<ETAS_EqkRupture>> catalogs, double duration,
			FaultSystemSolutionERF erf, File outputDir) throws IOException, GMT_MapException, RuntimeException {
		double[] minMags = { 0d };

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		// this is for map plotting
		CPT logRatioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-1, 1);
		CPT diffCPT = FaultBasedMapGen.getLogRatioCPT(); // will be rescaled
		Region region = new CaliforniaRegions.RELM_TESTING();
		boolean regionFilter = false;

		// filter out results outside of RELM region
		List<Integer> sectsToInclude = Lists.newArrayList();
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
			for (Location loc : sect.getFaultTrace()) {
				if (!regionFilter || region.contains(loc)) {
					sectsToInclude.add(sect.getSectionId());
					break;
				}
			}
		}

		List<LocationList> faults = Lists.newArrayList();
		for (int sectIndex : sectsToInclude)
			faults.add(rupSet.getFaultSectionData(sectIndex).getFaultTrace());

		// if we ever find a case where the parent of a rupture doesn't exist,
		// then this catalog has been filtered somehow and this will be set to
		// false
		boolean completeCatalogs = true;

		for (double minMag : minMags) {
			// each "MFD" will only have one value, for this minimum mag
			double[] subSectVals = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, minMag);

			double[] catalogVals = new double[rupSet.getNumSections()];

			int[] totalNumForSection = new int[rupSet.getNumSections()];
			double[] fractTriggeredForSection = new double[rupSet.getNumSections()];
			// triggered by a supra anywhere in the parent chain
			double[] fractTriggeredBySupraForSection = new double[rupSet.getNumSections()];
			double[] fractTriggeredBySubForSection = new double[rupSet.getNumSections()];
			double[] fractTriggeredByHistForSection = new double[rupSet.getNumSections()];

			int catalogCount = 0;

			for (List<ETAS_EqkRupture> catalog : catalogs) {
				if (duration < 0)
					// detect duration from catalog
					duration = calcDurationYears(catalog);
				// double rateEach = 1d/(catalogs.size()*duration);
				double rateEach = 1d / duration; // normalize by catalog count
													// later
				catalogCount++;

				HashMap<Integer, ETAS_EqkRupture> idToRupMap = new HashMap<Integer, ETAS_EqkRupture>();
				int minIndex = Integer.MAX_VALUE;
				if (completeCatalogs) {
					for (ETAS_EqkRupture rup : catalog) {
						idToRupMap.put(rup.getID(), rup);
						if (rup.getID() < minIndex)
							minIndex = rup.getID();
					}
				}

				for (ETAS_EqkRupture rup : catalog) {
					int rupIndex = rup.getFSSIndex();

					if (rupIndex < 0 || rup.getMag() < minMag)
						continue;

					for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
						catalogVals[sectIndex] += rateEach;
						totalNumForSection[sectIndex] += 1;
						if (rup.getGeneration() > 0) {
							// it's triggered
							fractTriggeredForSection[sectIndex] += 1;
							if (completeCatalogs) {
								// only can do this if catalogs are complete
								// (not filtered)
								boolean supra = false;
								boolean hist = false;
								ETAS_EqkRupture myRup = rup;
								while (myRup.getParentID() >= 0) {
									if (myRup.getParentID() < minIndex) {
										// historical earthquake.
										hist = true;
										break;
									}
									myRup = idToRupMap.get(myRup.getParentID());
									if (myRup == null) {
										// not a complete catalog, bail
										completeCatalogs = false;
										break;
									}
									if (myRup.getFSSIndex() >= 0) {
										// it has a supra parent!
										supra = true;
										break;
									}
								}
								if (supra)
									fractTriggeredBySupraForSection[sectIndex] += 1;
								else if (hist)
									fractTriggeredByHistForSection[sectIndex] += 1;
								else
									fractTriggeredBySubForSection[sectIndex] += 1;
							}
						}
					}
				}
			}

			// now normalize by number of catalogs
			for (int i = 0; i < catalogVals.length; i++)
				catalogVals[i] /= (double) catalogCount;

			if (!completeCatalogs)
				System.out.println("Cannot compute fract triggered by supra as catalog has been magnitude filtered");

			for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
				fractTriggeredForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
				if (completeCatalogs) {
					fractTriggeredBySupraForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
					fractTriggeredBySubForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
					fractTriggeredByHistForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
				}
			}

			// now filter out sections outside the region
			double[] filteredCatalogVals = new double[sectsToInclude.size()];
			double[] filteredSubSectVals = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredBySupraForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredBySubForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredByHistForSection = new double[sectsToInclude.size()];
			for (int i = 0; i < filteredCatalogVals.length; i++) {
				int s = sectsToInclude.get(i);
				filteredCatalogVals[i] = catalogVals[s];
				filteredSubSectVals[i] = subSectVals[s];
				filteredFractTriggeredForSection[i] = fractTriggeredForSection[s];
				if (completeCatalogs) {
					filteredFractTriggeredBySupraForSection[i] = fractTriggeredBySupraForSection[s];
					filteredFractTriggeredBySubForSection[i] = fractTriggeredBySubForSection[s];
					filteredFractTriggeredByHistForSection[i] = fractTriggeredByHistForSection[s];
				}
			}
			if (minMag == minMags[0])
				System.out.println("Filtered out " + (catalogVals.length - filteredCatalogVals.length)
						+ " sects outside of region");
			catalogVals = filteredCatalogVals;
			subSectVals = filteredSubSectVals;
			fractTriggeredForSection = filteredFractTriggeredForSection;
			fractTriggeredBySupraForSection = filteredFractTriggeredBySupraForSection;
			fractTriggeredBySubForSection = filteredFractTriggeredBySubForSection;
			fractTriggeredByHistForSection = filteredFractTriggeredByHistForSection;

			String title = "Sub Section Participation";
			String prefix = "all_eqs_sect_partic";
			if (minMag > 0) {
				title += ", M≥" + (float) minMag;
				prefix += "_m" + (float) minMag;
			}

			CSVFile<String> csv = new CSVFile<String>(true);
			List<String> header = Lists.newArrayList("Sect Index", "Sect Name", "Simulation Rate", "Long Term Rate",
					"Ratio", "Difference", "Fraction Triggered");
			if (completeCatalogs) {
				header.add("Fraction Triggered By Supra-Seismo");
				header.add("Fraction Triggered By Sub-Seismo");
				header.add("Fraction Triggered By Historical");
			}
			csv.addLine(header);

			double[] ratio = ratio(catalogVals, subSectVals);
			double[] diff = diff(catalogVals, subSectVals);

			for (int i = 0; i < catalogVals.length; i++) {
				// if(i>=1268 && i<=1282) // filter out Mendocino off shore
				// subsect
				// continue;
				FaultSectionPrefData sect = rupSet.getFaultSectionData(sectsToInclude.get(i));
				String sectName = sect.getSectionName().replace(",", "_");
				List<String> line = Lists.newArrayList(i + "", sectName, catalogVals[i] + "", subSectVals[i] + "",
						ratio[i] + "", diff[i] + "", fractTriggeredForSection[i] + "");
				if (completeCatalogs) {
					line.add(fractTriggeredBySupraForSection[i] + "");
					line.add(fractTriggeredBySubForSection[i] + "");
					line.add(fractTriggeredByHistForSection[i] + "");
				}
				csv.addLine(line);
			}
			csv.writeToFile(new File(outputDir, prefix + ".csv"));

			plotScatter(catalogVals, subSectVals, title + " Scatter", "Participation Rate", prefix + "_scatter",
					outputDir);

			title = title.replaceAll("≥", ">=");
			FaultBasedMapGen.makeFaultPlot(logRatioCPT, faults, FaultBasedMapGen.log10(ratio), region, outputDir,
					prefix + "_ratio", false, false, title + " Ratio");
			double maxDiff = Math.max(Math.abs(StatUtils.min(diff)), Math.abs(StatUtils.max(diff)));
			FaultBasedMapGen.makeFaultPlot(diffCPT.rescale(-maxDiff, maxDiff), faults, diff, region, outputDir,
					prefix + "_diff", false, false, title + " Diff");
		}
	}

	/**
	 * 
	 * Note that durationForProb is not the duration of the original simulation
	 * 
	 * @param catalogs
	 * @param durationForProb
	 * @param erf
	 *            - this should be the original probability model (not Poisson)
	 * @param outputDir
	 * @throws IOException
	 * @throws GMT_MapException
	 * @throws RuntimeException
	 */
	public static void plotAndWriteSectProbOneOrMoreData(Iterable<List<ETAS_EqkRupture>> catalogs,
			double durationForProb, FaultSystemSolutionERF erf, File outputDir)
			throws IOException, GMT_MapException, RuntimeException {
		double[] minMags = { 0d };

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		// this is for map plotting
		CPT logRatioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-1, 1);
		CPT diffCPT = FaultBasedMapGen.getLogRatioCPT(); // will be rescaled
		Region region = new CaliforniaRegions.RELM_TESTING();
		boolean regionFilter = false;

		// filter out results outside of RELM region
		List<Integer> sectsToInclude = Lists.newArrayList();
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
			for (Location loc : sect.getFaultTrace()) {
				if (!regionFilter || region.contains(loc)) {
					sectsToInclude.add(sect.getSectionId());
					break;
				}
			}
		}

		List<LocationList> faults = Lists.newArrayList();
		for (int sectIndex : sectsToInclude)
			faults.add(rupSet.getFaultSectionData(sectIndex).getFaultTrace());

		// if we ever find a case where the parent of a rupture doesn't exist,
		// then this catalog has been filtered somehow and this will be set to
		// false
		boolean completeCatalogs = true;

		erf.getTimeSpan().setDuration(durationForProb);
		erf.updateForecast();

		for (double minMag : minMags) {
			// each "MFD" will only have one value, for this minimum mag
			double[] subSectEquivRateVals = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, minMag);
			double[] subSectExpVals = new double[rupSet.getNumSections()];
			for (int s = 0; s < subSectExpVals.length; s++)
				subSectExpVals[s] = 1.0 - Math.exp(-subSectEquivRateVals[s] * durationForProb);

			double[] catalogVals = new double[rupSet.getNumSections()];

			int[] totalNumForSection = new int[rupSet.getNumSections()];
			double[] fractTriggeredForSection = new double[rupSet.getNumSections()];
			// triggered by a supra anywhere in the parent chain
			double[] fractTriggeredBySupraForSection = new double[rupSet.getNumSections()];
			double[] fractTriggeredBySubForSection = new double[rupSet.getNumSections()];
			double[] fractTriggeredByHistForSection = new double[rupSet.getNumSections()];

			int catalogCount = 0;

			for (List<ETAS_EqkRupture> catalog : catalogs) {
				// reset time of last event on section
				double[] timeOfLastEventOnSect = new double[rupSet.getNumSections()];
				for (int i = 0; i < timeOfLastEventOnSect.length; i++)
					timeOfLastEventOnSect[i] = -1;

				catalogCount++;

				HashMap<Integer, ETAS_EqkRupture> idToRupMap = new HashMap<Integer, ETAS_EqkRupture>();
				int minIndex = Integer.MAX_VALUE;
				if (completeCatalogs) {
					for (ETAS_EqkRupture rup : catalog) {
						idToRupMap.put(rup.getID(), rup);
						if (rup.getID() < minIndex)
							minIndex = rup.getID();
					}
				}

				for (ETAS_EqkRupture rup : catalog) {
					int rupIndex = rup.getFSSIndex();

					if (rupIndex < 0 || rup.getMag() < minMag)
						continue;

					double eventTime = calcEventTimeYears(catalog, rup);

					for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
						if (timeOfLastEventOnSect[sectIndex] == -1) { // the
																		// first
																		// occurrence
																		// on
																		// section
							if (eventTime < durationForProb) { // if happened
																// within
																// durationForProb
								catalogVals[sectIndex] += 1;
								totalNumForSection[sectIndex] += 1;
								if (rup.getGeneration() > 0) {
									// it's triggered
									fractTriggeredForSection[sectIndex] += 1;
									if (completeCatalogs) {
										// only can do this if catalogs are
										// complete (not filtered)
										boolean supra = false;
										boolean hist = false;
										ETAS_EqkRupture myRup = rup;
										while (myRup.getParentID() >= 0) {
											if (myRup.getParentID() < minIndex) {
												// historical earthquake.
												hist = true;
												break;
											}
											myRup = idToRupMap.get(myRup.getParentID());
											if (myRup == null) {
												// not a complete catalog, bail
												completeCatalogs = false;
												break;
											}
											if (myRup.getFSSIndex() >= 0) {
												// it has a supra parent!
												supra = true;
												break;
											}
										}
										if (supra)
											fractTriggeredBySupraForSection[sectIndex] += 1;
										else if (hist)
											fractTriggeredByHistForSection[sectIndex] += 1;
										else
											fractTriggeredBySubForSection[sectIndex] += 1;
									}
								}
							}
						}
						timeOfLastEventOnSect[sectIndex] = eventTime;
					}
				}
			}

			// now normalize by number of catalogs
			for (int i = 0; i < catalogVals.length; i++)
				catalogVals[i] /= (double) catalogCount;

			// Compute the standard deviation of the mean
			double[] catalogValsStdom = new double[rupSet.getNumSections()];
			for (List<ETAS_EqkRupture> catalog : catalogs) {
				// reset time of last event on section
				double[] gotOneOnSectForCat = new double[rupSet.getNumSections()];
				for (ETAS_EqkRupture rup : catalog) {
					int rupIndex = rup.getFSSIndex();
					if (rupIndex < 0 || rup.getMag() < minMag)
						continue;
					double eventTime = calcEventTimeYears(catalog, rup);
					for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
						if (eventTime < durationForProb) { // if happened within
															// durationForProb
							gotOneOnSectForCat[sectIndex] = 1;
						}
					}
				}
				for (int s = 0; s < gotOneOnSectForCat.length; s++) {
					catalogValsStdom[s] += Math.pow(gotOneOnSectForCat[s] - catalogVals[s], 2d); // square
																									// the
																									// diff
																									// from
																									// mean
				}
			}
			// convert to stdom by dividing by N (assumes N is large); stdev is
			// divided by sqrt(N-1), and divide by sqrt(N) again fro stdom
			for (int s = 0; s < catalogValsStdom.length; s++) {
				catalogValsStdom[s] /= (double) catalogCount;
			}

			if (!completeCatalogs)
				System.out.println("Cannot compute fract triggered by supra as catalog has been magnitude filtered");

			for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
				fractTriggeredForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
				if (completeCatalogs) {
					fractTriggeredBySupraForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
					fractTriggeredBySubForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
					fractTriggeredByHistForSection[sectIndex] /= (double) totalNumForSection[sectIndex];
				}
			}

			// now filter out sections outside the region
			double[] filteredCatalogVals = new double[sectsToInclude.size()];
			double[] filteredSubSectVals = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredBySupraForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredBySubForSection = new double[sectsToInclude.size()];
			double[] filteredFractTriggeredByHistForSection = new double[sectsToInclude.size()];
			for (int i = 0; i < filteredCatalogVals.length; i++) {
				int s = sectsToInclude.get(i);
				filteredCatalogVals[i] = catalogVals[s];
				filteredSubSectVals[i] = subSectExpVals[s];
				filteredFractTriggeredForSection[i] = fractTriggeredForSection[s];
				if (completeCatalogs) {
					filteredFractTriggeredBySupraForSection[i] = fractTriggeredBySupraForSection[s];
					filteredFractTriggeredBySubForSection[i] = fractTriggeredBySubForSection[s];
					filteredFractTriggeredByHistForSection[i] = fractTriggeredByHistForSection[s];
				}
			}
			if (minMag == minMags[0])
				System.out.println("Filtered out " + (catalogVals.length - filteredCatalogVals.length)
						+ " sects outside of region");
			catalogVals = filteredCatalogVals;
			subSectExpVals = filteredSubSectVals;
			fractTriggeredForSection = filteredFractTriggeredForSection;
			fractTriggeredBySupraForSection = filteredFractTriggeredBySupraForSection;
			fractTriggeredBySubForSection = filteredFractTriggeredBySubForSection;
			fractTriggeredByHistForSection = filteredFractTriggeredByHistForSection;

			String title = "Sub Section Prob One Or More";
			String prefix = "all_eqs_sect_prob1orMore";
			if (minMag > 0) {
				title += ", M≥" + (float) minMag;
				prefix += "_m" + (float) minMag;
			}

			CSVFile<String> csv = new CSVFile<String>(true);
			List<String> header = Lists.newArrayList("SectIndex", "SectName", "Simulation Prob1orMore",
					"ExpectedProb1orMore", "Ratio", "Difference", "FractTriggered", "SimProb1orMoreStdom",
					"StdomNormDiff");
			if (completeCatalogs) {
				header.add("FractTrigBySupraSeis");
				header.add("FractTrigBySubSeism");
				header.add("FractTrigByHistQk");
			}
			csv.addLine(header);

			double[] ratio = ratio(catalogVals, subSectExpVals);
			double[] diff = diff(catalogVals, subSectExpVals);

			// this is now just mean corrected difference (normalizing by stdom
			// wasn't helpful)
			double[] stdomNormDiff = new double[ratio.length];
			double meanRatio = 0d;
			for (int i = 0; i < ratio.length; i++)
				meanRatio += ratio[i] / ratio.length;
			System.out.println("meanRatio=" + meanRatio);
			for (int i = 0; i < ratio.length; i++)
				stdomNormDiff[i] = (catalogVals[i] - meanRatio * subSectExpVals[i]);/// catalogValsStdom[i];

			for (int i = 0; i < catalogVals.length; i++) {
				// if(i>=1268 && i<=1282) // filter out Mendocino off shore
				// subsect
				// continue;
				FaultSectionPrefData sect = rupSet.getFaultSectionData(sectsToInclude.get(i));
				String sectName = sect.getSectionName().replace(",", "_");
				List<String> line = Lists.newArrayList(i + "", sectName, catalogVals[i] + "", subSectExpVals[i] + "",
						ratio[i] + "", diff[i] + "", fractTriggeredForSection[i] + "", catalogValsStdom[i] + "",
						stdomNormDiff[i] + "");
				if (completeCatalogs) {
					line.add(fractTriggeredBySupraForSection[i] + "");
					line.add(fractTriggeredBySubForSection[i] + "");
					line.add(fractTriggeredByHistForSection[i] + "");
				}
				csv.addLine(line);
			}
			csv.writeToFile(new File(outputDir, prefix + ".csv"));

			plotScatter(catalogVals, subSectExpVals, title + " Scatter", "Prob One Or More", prefix + "_scatter",
					outputDir);

			title = title.replaceAll("≥", ">=");
			FaultBasedMapGen.makeFaultPlot(logRatioCPT, faults, FaultBasedMapGen.log10(ratio), region, outputDir,
					prefix + "_ratio", false, false, title + " Ratio");
			double maxDiff = Math.max(Math.abs(StatUtils.min(diff)), Math.abs(StatUtils.max(diff)));
			FaultBasedMapGen.makeFaultPlot(diffCPT.rescale(-maxDiff, maxDiff), faults, diff, region, outputDir,
					prefix + "_diff", false, false, title + " Diff");
			FaultBasedMapGen.makeFaultPlot(diffCPT.rescale(-maxDiff / 2, maxDiff / 2), faults, stdomNormDiff, region,
					outputDir, prefix + "_meanCorrectedDiff", false, false, title + " MeanCorrectedDiff");
			for (int i = 0; i < ratio.length; i++) {
				if (subSectExpVals[i] < 3e-3)
					ratio[i] = 1d;
			}
			FaultBasedMapGen.makeFaultPlot(logRatioCPT, faults, FaultBasedMapGen.log10(ratio), region, outputDir,
					prefix + "_ratioFilteredProbGt0pt003", false, false, title + " Ratio");

		}
	}

	public static void plotBinnedSectParticRateVsExpRate(List<List<ETAS_EqkRupture>> catalogs, double duration,
			FaultSystemSolutionERF erf, File outputDir, String prefix)
			throws IOException, GMT_MapException, RuntimeException {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		// this is used to fliter Mendocino sections
		List<? extends IncrementalMagFreqDist> longTermSubSeisMFD_OnSectList = erf.getSolution()
				.getSubSeismoOnFaultMFD_List();

		HistogramFunction aveValsFunc = new HistogramFunction(-4.9, 19, 0.2);
		HistogramFunction numValsFunc = new HistogramFunction(-4.9, 19, 0.2);
		// System.out.println(aveValsFunc.toString());

		double[] subSectExpVals = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 0.0);

		int[] totalNumForSection = new int[rupSet.getNumSections()];

		int catalogCount = 0;

		double[] catalogVals = new double[rupSet.getNumSections()];

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			if (duration < 0)
				duration = calcDurationYears(catalog);
			double rateEach = 1d / duration; // normalize by catalog count later
			catalogCount++;

			for (ETAS_EqkRupture rup : catalog) {
				int rupIndex = rup.getFSSIndex();

				if (rupIndex < 0)
					continue;

				for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
					catalogVals[sectIndex] += rateEach;
					totalNumForSection[sectIndex] += 1;
				}
			}

		}

		for (int s = 0; s < subSectExpVals.length; s++) {
			if (longTermSubSeisMFD_OnSectList.get(s).getTotalIncrRate() == 0)
				System.out.println(erf.getSolution().getRupSet().getFaultSectionData(s).getName());
			if (catalogVals[s] > 0 && longTermSubSeisMFD_OnSectList.get(s).getTotalIncrRate() > 0) {
				catalogVals[s] /= catalogCount; // ave rate over all catalogs
				double logExpVal = Math.log10(subSectExpVals[s]);
				double logSimVal = Math.log10(catalogVals[s]);
				if (logExpVal > aveValsFunc.getMinX() - aveValsFunc.getDelta() / 2) {
					aveValsFunc.add(logExpVal, logSimVal);
					numValsFunc.add(logExpVal, 1.0);
				}
			}
		}

		DefaultXY_DataSet resultsFunc = new DefaultXY_DataSet();
		for (int i = 0; i < numValsFunc.size(); i++) {
			double num = numValsFunc.getY(i);
			if (num > 0)
				resultsFunc.set(Math.pow(10, aveValsFunc.getX(i)), Math.pow(10, aveValsFunc.getY(i) / num));
			// aveValsFunc.set(i,aveValsFunc.getY(i)/num);
		}

		String title = "Binned Sub Section Participation";

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(1e-6, 1e-6);
		line.set(0.1, 0.1);
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));

		funcs.add(resultsFunc);
		resultsFunc.setName("Binned observed sections rates vs expected rates");
		resultsFunc.setInfo(resultsFunc.toString());
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));

		PlotSpec spec = new PlotSpec(funcs, chars, title, "Long Term Rate", "Binned Simulation Rate");
		// spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		Range range = new Range(1e-6, 0.1);

		gp.drawGraphPanel(spec, true, true, range, range);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void calcSupraAncestorStats(Iterable<List<ETAS_EqkRupture>> catalogs, File outputDir)
			throws IOException {
		// total
		long numSupra = 0;

		// sub counts
		long numSupraSpontaneous = 0; // spontaneous
		long numSupraTriggeredSupra = 0; // have at least one supra ancestor
		long numSupraTriggeredHist = 0; // have a historical ancestor and no
										// supra's in-between
		long numSupraTriggeredOther = 0; // should be numSupra minus the total
											// of the above, as a check

		long numSupraTriggeredSupraDirect = 0; // special one for directly
												// triggered by a supra

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			HashMap<Integer, ETAS_EqkRupture> idToRupMap = new HashMap<Integer, ETAS_EqkRupture>();
			int minIndex = Integer.MAX_VALUE;
			for (ETAS_EqkRupture rup : catalog) {
				idToRupMap.put(rup.getID(), rup);
				if (rup.getID() < minIndex)
					minIndex = rup.getID();
			}

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0)
					// only consider supra
					continue;

				numSupra++;

				if (rup.getParentID() < 0) {
					// spontaneous
					numSupraSpontaneous++;
				} else {
					// only can do this if catalogs are complete (not filtered)
					boolean supra = false;
					boolean hist = false;
					ETAS_EqkRupture myRup = rup;
					while (myRup.getParentID() >= 0) {
						if (myRup.getParentID() < minIndex) {
							// historical earthquake.
							hist = true;
							break;
						}
						myRup = idToRupMap.get(myRup.getParentID());
						Preconditions.checkState(myRup != null,
								"This isn't a complete catalog (was filtered), cannot track ancestors");
						if (myRup.getFSSIndex() >= 0) {
							// it has a supra parent!
							supra = true;
							// check if this is the first parent
							if (rup.getParentID() == myRup.getID())
								numSupraTriggeredSupraDirect++;
							break;
						}
					}
					if (supra)
						numSupraTriggeredSupra++;
					else if (hist)
						numSupraTriggeredHist++;
					else
						numSupraTriggeredOther++;
				}
			}
		}

		Preconditions.checkState(numSupraTriggeredOther == (numSupra - numSupraSpontaneous - numSupraTriggeredSupra
				- numSupraTriggeredHist));

		String text = "Supra-seismogenic rupture ancestors:\n";
		text += "\tTotal num supra: " + numSupra + "\n";
		text += "\tNum spontaneous supra: " + numSupraSpontaneous + "\n";
		text += "\tNum with supra ancestor: " + numSupraTriggeredSupra + " (" + numSupraTriggeredSupraDirect
				+ " of which were direct supra triggers)\n";
		text += "\tNum with hist (and no supra) ancestor: " + numSupraTriggeredHist + "\n";
		text += "\tNum triggered other: " + numSupraTriggeredOther + "\n";
		text += "\n";
		double fractSupraSpontaneous = (double) numSupraSpontaneous / (double) numSupra;
		double fractSupraTriggeredSupra = (double) numSupraTriggeredSupra / (double) numSupra;
		double fractSupraTriggeredSupraDirect = (double) numSupraTriggeredSupraDirect / (double) numSupraTriggeredSupra;
		double fractSupraTriggeredHist = (double) numSupraTriggeredHist / (double) numSupra;
		double fractSupraTriggeredOther = (double) numSupraTriggeredOther / (double) numSupra;
		text += "\tFractions: " + numSupra + "\n";
		text += "\tFract spontaneous supra: " + fractSupraSpontaneous + "\n";
		text += "\tFract with supra ancestor: " + fractSupraTriggeredSupra + " (" + fractSupraTriggeredSupraDirect
				+ " of which were direct supra triggers)\n";
		text += "\tFract with hist (and no supra) ancestor: " + fractSupraTriggeredHist + "\n";
		text += "\tFract triggered other: " + fractSupraTriggeredOther + "\n";

		System.out.println(text);
		if (outputDir != null) {
			File outputFile = new File(outputDir, "supra_ancestor_stats.txt");
			FileWriter fw = new FileWriter(outputFile);
			fw.write(text);
			fw.close();
		}
	}

	private static double[] diff(double[] data1, double[] data2) {
		double[] diff = new double[data1.length];
		for (int i = 0; i < data1.length; i++)
			diff[i] = data1[i] - data2[i];
		return diff;
	}

	private static double[] ratio(double[] data1, double[] data2) {
		double[] ratio = new double[data1.length];
		for (int i = 0; i < data1.length; i++)
			ratio[i] = data1[i] / data2[i];
		return ratio;
	}

	private static void plotGriddedNucleationScatter(List<List<ETAS_EqkRupture>> catalogs, double duration,
			FaultSystemSolutionERF erf, File outputDir) throws IOException, GMT_MapException {
		double[] minMags = { 5d };

		GriddedRegion reg = RELM_RegionUtils.getGriddedRegionInstance();

		// this is for map plotting
		CPT logRatioCPT = FaultBasedMapGen.getLogRatioCPT().rescale(-2, 2);
		CPT diffCPT = FaultBasedMapGen.getLogRatioCPT(); // will be rescaled

		for (double minMag : minMags) {
			// each "MFD" will only have one value, for this minimum mag
			// double[] subSectVals =
			// FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf,
			// minMag);
			GriddedGeoDataSet longTermRates = ERF_Calculator.getNucleationRatesInRegion(erf, reg, minMag, 10d);
			GriddedGeoDataSet catalogRates = new GriddedGeoDataSet(reg, longTermRates.isLatitudeX());

			double[] longTermVals = new double[reg.getNodeCount()];
			double[] catalogVals = new double[reg.getNodeCount()];

			for (int i = 0; i < reg.getNodeCount(); i++)
				longTermVals[i] = longTermRates.get(i);

			for (List<ETAS_EqkRupture> catalog : catalogs) {
				if (duration < 0)
					// detect duration from catalog
					duration = calcDurationYears(catalog);
				double rateEach = 1d / (catalogs.size() * duration);

				for (ETAS_EqkRupture rup : catalog) {
					if (rup.getMag() < minMag)
						continue;
					Location hypo = rup.getHypocenterLocation();
					int index = reg.indexForLocation(hypo);
					if (index < 0)
						// outside of region
						continue;

					catalogVals[index] += rateEach;
				}
			}

			for (int i = 0; i < catalogVals.length; i++)
				catalogRates.set(i, catalogVals[i]);

			String title = "Gridded Nucleation";
			String prefix = "all_eqs_gridded_nucl";
			if (minMag > 0) {
				title += ", M≥" + (float) minMag;
				prefix += "_m" + (float) minMag;
			}

			CSVFile<String> csv = new CSVFile<String>(true);
			csv.addLine("Node Index", "Latitude", "Longitude", "Simulation Rate", "Long Term Rate", "Ratio",
					"Difference");

			double[] ratio = ratio(catalogVals, longTermVals);
			double[] diff = diff(catalogVals, longTermVals);

			LocationList nodeList = reg.getNodeList();
			for (int i = 0; i < reg.getNodeCount(); i++) {
				Location loc = nodeList.get(i);

				csv.addLine(i + "", loc.getLatitude() + "", loc.getLongitude() + "", catalogVals[i] + "",
						longTermVals[i] + "", ratio[i] + "", diff[i] + "");
			}
			csv.writeToFile(new File(outputDir, prefix + ".csv"));

			plotScatter(catalogVals, longTermVals, title + " Scatter", "Nucleation Rate", prefix + "_scatter",
					outputDir);

			GeoDataSet ratioData = GeoDataSetMath.divide(catalogRates, longTermRates);
			ratioData.log10();
			GeoDataSet diffData = GeoDataSetMath.subtract(catalogRates, longTermRates);
			title = title.replaceAll("≥", ">=");
			FaultBasedMapGen.plotMap(outputDir, prefix + "_ratio", false, FaultBasedMapGen.buildMap(logRatioCPT, null,
					null, ratioData, reg.getLatSpacing(), reg, false, title + " Ratio"));
			double maxDiff = Math.max(Math.abs(StatUtils.min(diff)), Math.abs(StatUtils.max(diff)));
			FaultBasedMapGen.plotMap(outputDir, prefix + "_diff", false,
					FaultBasedMapGen.buildMap(diffCPT.rescale(-maxDiff, maxDiff), null, null, diffData,
							reg.getLatSpacing(), reg, false, title + " Diff"));
		}
	}

	private static void plotScatter(double[] simulationData, double[] longTermData, String title, String quantity,
			String prefix, File outputDir) throws IOException {
		Preconditions.checkArgument(simulationData.length == longTermData.length);
		DefaultXY_DataSet scatter = new DefaultXY_DataSet();
		DefaultXY_DataSet simZeroScatter = new DefaultXY_DataSet();

		int bothZeroCount = 0;

		for (int i = 0; i < simulationData.length; i++) {
			if (simulationData[i] > 0)
				scatter.set(longTermData[i], simulationData[i]);
			else if (longTermData[i] > 0)
				simZeroScatter.set(longTermData[i], simulationData[i]);
			else
				bothZeroCount++;
		}

		System.out.println("Raw scatter range: " + scatter.getMinX() + ", " + scatter.getMaxX() + ", "
				+ scatter.getMinY() + ", " + scatter.getMaxY());
		System.out.println("Raw scatter range: " + simZeroScatter.getMinX() + ", " + simZeroScatter.getMaxX() + ", "
				+ simZeroScatter.getMinY() + ", " + simZeroScatter.getMaxY());

		double minRate = Math.min(scatter.getMinX(), scatter.getMinY());
		double maxRate = Math.max(scatter.getMaxX(), scatter.getMaxY());
		if (simZeroScatter.size() > 0) {
			minRate = Math.min(minRate, simZeroScatter.getMinX());
			maxRate = Math.max(maxRate, simZeroScatter.getMaxX());
		}
		if (maxRate == minRate)
			maxRate *= 10d;

		System.out.println("minRate=" + minRate + ", maxRate=" + maxRate);

		Range range = new Range(Math.pow(10d, Math.floor(Math.log10(minRate))),
				Math.pow(10d, Math.ceil(Math.log10(maxRate))));

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));

		funcs.add(scatter);
		scatter.setName(scatter.size() + "/" + simulationData.length + " Both Nonzero");
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));

		if (simZeroScatter.size() > 0) {
			// plot subsections with zero rate in simulations as a different
			// color at the bottom of the plot
			// (otherwise they would be hidden below the plot since we're in log
			// space)

			// move it over to the bottom of the plot
			DefaultXY_DataSet modZeroScatter = new DefaultXY_DataSet();
			for (int i = 0; i < simZeroScatter.size(); i++)
				modZeroScatter.set(simZeroScatter.getX(i), range.getLowerBound());
			funcs.add(modZeroScatter);
			modZeroScatter.setName(modZeroScatter.size() + "/" + simulationData.length + " Zero In Simulations");
			chars.add(new PlotCurveCharacterstics(PlotSymbol.X, 3f, Color.RED));

			System.out.println(simZeroScatter.size() + "/" + simulationData.length
					+ " values are zero in simulations and nonzero long term");
		}
		if (bothZeroCount > 0)
			System.out.println(
					bothZeroCount + "/" + simulationData.length + " values are zero in both simulations and long term");

		PlotSpec spec = new PlotSpec(funcs, chars, title, "Long Term " + quantity, "Simulation " + quantity);
		if (simZeroScatter.size() > 0)
			spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, true, true, range, range);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	private static void plotStationarity(List<List<ETAS_EqkRupture>> catalogs, double duration, File outputDir)
			throws IOException {
		if (duration < 0) {
			for (List<ETAS_EqkRupture> catalog : catalogs)
				duration = Math.max(duration, calcDurationYears(catalog));
		}

		double delta;
		if (duration <= 200d)
			delta = 10d;
		else if (duration > 5000d)
			delta = 200d;
		else if (duration > 1000d)
			delta = 100d;
		else
			delta = 50d;

		double histDuration;
		if (duration > 5000)
			histDuration = duration * 0.995;
		else
			histDuration = duration * 0.98;
		HistogramFunction xVals = HistogramFunction.getEncompassingHistogram(0d, histDuration, delta);
		Preconditions.checkState(xVals.size() > 1);

		// double binRateEach = 1d/(catalogs.size()*delta);
		double annualRateEachBin = 1d / delta;

		double[][] momRatesEach = new double[xVals.size()][catalogs.size()];
		double[][] m5RatesEach = new double[xVals.size()][catalogs.size()];

		for (int i = 0; i < catalogs.size(); i++) {
			List<ETAS_EqkRupture> catalog = catalogs.get(i);
			for (ETAS_EqkRupture rup : catalog) {
				double rupTimeYears = calcEventTimeYears(catalog, rup);

				int xIndex = xVals.getXIndex(rupTimeYears);
				if (xIndex < 0) {
					System.out.println("What? bad x index: " + xIndex);
					System.out.println("Rup time: " + rupTimeYears);
					System.out.println("Catalog Start Time: " + calcEventTimeYears(catalog, catalog.get(0)));
					System.out.println("Hist first bin: " + xVals.getMinX() + " (delta=" + delta + ")");
				}
				Preconditions.checkState(xIndex >= 0);

				if (rup.getMag() >= 5d)
					m5RatesEach[xIndex][i] += annualRateEachBin;
				double moment = MagUtils.magToMoment(rup.getMag());
				momRatesEach[xIndex][i] += moment * annualRateEachBin;
			}
		}

		EvenlyDiscretizedFunc momRateMean = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		EvenlyDiscretizedFunc momRateLower = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		EvenlyDiscretizedFunc momRateUpper = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		for (int i = 0; i < xVals.size(); i++) {
			double mean = StatUtils.mean(momRatesEach[i]);
			double lower = StatUtils.percentile(momRatesEach[i], 2.5);
			double upper = StatUtils.percentile(momRatesEach[i], 97.5);

			momRateMean.set(i, mean);
			momRateLower.set(i, lower);
			momRateUpper.set(i, upper);
		}
		UncertainArbDiscDataset momRateDataset = new UncertainArbDiscDataset(momRateMean, momRateLower, momRateUpper);

		EvenlyDiscretizedFunc m5RateMean = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		EvenlyDiscretizedFunc m5RateLower = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		EvenlyDiscretizedFunc m5RateUpper = new EvenlyDiscretizedFunc(xVals.getMinX(), xVals.getMaxX(), xVals.size());
		for (int i = 0; i < xVals.size(); i++) {
			double mean = StatUtils.mean(m5RatesEach[i]);
			double lower = StatUtils.percentile(m5RatesEach[i], 2.5);
			double upper = StatUtils.percentile(m5RatesEach[i], 97.5);

			m5RateMean.set(i, mean);
			m5RateLower.set(i, lower);
			m5RateUpper.set(i, upper);
		}
		UncertainArbDiscDataset m5RateDataset = new UncertainArbDiscDataset(m5RateMean, m5RateLower, m5RateUpper);

		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(momRateDataset);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, Color.BLUE));
		funcs.add(momRateDataset);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, PlotSymbol.FILLED_CIRCLE, 4f, Color.BLACK));
		String prefix = "stationariy_mom_rate";

		PlotSpec spec = new PlotSpec(funcs, chars, "Moment Rate Over Time (" + (int) delta + "yr bins)", "Years",
				"Annual Moment Rate (N-m)");

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true, null,
				new Range(momRateDataset.getLowerMinY(), momRateDataset.getUpperMaxY()));
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());

		funcs = Lists.newArrayList();
		funcs.add(m5RateDataset);
		funcs.add(m5RateDataset);
		prefix = "stationariy_m5_rate";

		spec = new PlotSpec(funcs, chars, "M≥5 Rate Over Time (" + (int) delta + "yr bins)", "Years",
				"Annual M≥5 Rate");

		gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false, null,
				new Range(m5RateDataset.getLowerMinY(), m5RateDataset.getUpperMaxY()));
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void plotSubSectRecurrenceHist(List<List<ETAS_EqkRupture>> catalogs, FaultSystemRupSet rupSet,
			int sectIndex, File outputDir, double targetRI) throws IOException {
		HashSet<Integer> ruptures = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex));

		double numYrsForProb = 10;
		double aveNumInNumYrs = 0;
		int numFirst = 0;
		int numFirstSpont = 0;

		List<Double> intervals = Lists.newArrayList();
		double maxValue = 0d;
		double sum = 0d;

		double meanFirstHalf = 0;
		double meanSecondHalf = 0;
		double meanFirstInterval = 0;
		double meanSecondInterval = 0;
		int numFirstHalf = 0;
		int numSecondHalf = 0;
		int numFirstInterval = 0;
		int numSecondInterval = 0;
		double probOccurInNumyrs = 0;

		int numSpontaneous = 0;
		int totNumEvents = 0;

		List<ETAS_EqkRupture> firstCatalog = catalogs.get(0);
		double simulationDuration = calcEventTimeYears(firstCatalog, firstCatalog.get(firstCatalog.size() - 1));

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			double prevTime = -1;
			boolean firstInterval = true;
			boolean secondInterval = false;

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0 || !ruptures.contains(rup.getFSSIndex()))
					continue;
				totNumEvents += 1.0;
				double myTime = calcEventTimeYears(catalog, rup);

				if (rup.getGeneration() == 0)
					numSpontaneous += 1;

				if (prevTime == -1) { // the first occurrance
					if (myTime <= numYrsForProb) {
						probOccurInNumyrs += 1.0;
						aveNumInNumYrs += 1.0;
					}
					numFirst += 1;
					if (rup.getGeneration() == 0)
						numFirstSpont += 1;
				}

				if (prevTime >= 0) {
					if (myTime <= numYrsForProb)
						aveNumInNumYrs += 1.0;
					double interval = myTime - prevTime;
					intervals.add(interval);
					maxValue = Math.max(maxValue, interval);
					sum += interval;
					if (secondInterval) {
						meanSecondInterval += interval;
						numSecondInterval += 1;
						secondInterval = false;
					}
					if (firstInterval) {
						meanFirstInterval += interval;
						numFirstInterval += 1;
						firstInterval = false;
						secondInterval = true;
					}
					if (myTime <= simulationDuration / 2.0) {
						meanFirstHalf += interval;
						numFirstHalf += 1;
					} else {
						meanSecondHalf += interval;
						numSecondHalf += 1;
					}
				}

				prevTime = myTime;
			}
		}
		double mean = sum / intervals.size();
		meanFirstHalf /= (double) numFirstHalf;
		meanSecondHalf /= (double) numSecondHalf;
		meanFirstInterval /= (double) numFirstInterval;
		meanSecondInterval /= (double) numSecondInterval;

		String info = "Num Catalogs = " + catalogs.size() + "\n";

		if (!Double.isNaN(targetRI))
			info += "targetRI = " + (float) targetRI + "\n";
		info += "meanRI = " + (float) mean + "\n";
		info += "intervals.size() = " + intervals.size() + "\n";
		double fracSpontaneous = (double) numSpontaneous / (double) (totNumEvents); // there
																					// are
																					// one
																					// more
																					// events
																					// than
																					// intervals
		info += "numSpontaneous = " + numSpontaneous + "\t(" + (float) fracSpontaneous + ")" + "\n";

		double testPartRate = totNumEvents / (catalogs.size() * simulationDuration); // assumes
																						// thousand-year
																						// catalogs;
																						// num
																						// events
																						// is
																						// one
																						// minus
																						// num
																						// intervals
		info += "testPartRate = " + (float) testPartRate + "\t1/0/testPartRate=" + (float) (1.0 / testPartRate) + "\n";

		// Compute mean that does not include quick re-ruptures (within first
		// 10% of ave RI)
		double meanFiltered = 0;
		int numFiltered = 0;
		for (double ri : intervals) {
			if (ri / mean > 0.1) {
				meanFiltered += ri;
				numFiltered += 1;
			}
		}
		meanFiltered /= (double) numFiltered;
		info += "meanFiltered = " + (float) meanFiltered + "\t(RIs within first 10% of ave RI excluded)\n";
		info += "numFiltered = " + numFiltered + "\n";
		info += "meanFirstHalf = " + (float) meanFirstHalf + "\t(" + numFirstHalf + ")" + "\n";
		info += "meanSecondHalf = " + (float) meanSecondHalf + "\t(" + numSecondHalf + ")" + "\n";
		info += "meanFirstInterval = " + (float) meanFirstInterval + "\t(" + numFirstInterval + ")" + "\n";
		info += "meanSecondInterval = " + (float) meanSecondInterval + "\t(" + numSecondInterval + ")" + "\n";
		info += "numFirstSpontaneous=" + numFirstSpont + "\n";
		info += "fractFirstSpontaneous=" + (float) numFirstSpont / (float) numFirst + "\n";

		probOccurInNumyrs /= (double) catalogs.size(); // fraction that had
														// nothing in 10 years
		aveNumInNumYrs /= (double) catalogs.size();
		info += "Prob one or more in " + numYrsForProb + " years =" + (float) (probOccurInNumyrs) + "\n";
		info += "Ave num in " + numYrsForProb + " years =" + (float) (aveNumInNumYrs) + "\n";

		System.out.println(info);

		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(0d, maxValue, 0.1 * mean);
		hist.setName("Histogram");
		hist.setInfo(info);

		for (double interval : intervals)
			hist.add(interval, 1d);

		hist.normalizeBySumOfY_Vals();
		hist.scale(1.0 / hist.getDelta()); // make into a density
		// Range yRange = new Range(0d, hist.getMaxY()*1.1);
		Range yRange = new Range(0d, 0.016);

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		DecimalFormat df = new DecimalFormat("0.0");

		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		String prefix = "sub_sect_recurrence_" + sectIndex;

		DefaultXY_DataSet meanLine = new DefaultXY_DataSet();
		meanLine.set(mean, yRange.getLowerBound());
		meanLine.set(mean, yRange.getUpperBound());
		meanLine.setName("Mean=" + df.format(mean));

		funcs.add(meanLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.CYAN));

		double median = DataUtils.median(Doubles.toArray(intervals));

		DefaultXY_DataSet medianLine = new DefaultXY_DataSet();
		medianLine.set(median, yRange.getLowerBound());
		medianLine.set(median, yRange.getUpperBound());
		medianLine.setName("Median=" + df.format(median));

		funcs.add(medianLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));

		double mode = hist.getX(hist.getXindexForMaxY());

		DefaultXY_DataSet modeLine = new DefaultXY_DataSet();
		modeLine.set(mode, hist.getMaxY());
		modeLine.setName("Mode=" + df.format(mode));

		funcs.add(modeLine);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 2f, Color.RED));

		if (!Double.isNaN(targetRI)) {
			DefaultXY_DataSet targetLine = new DefaultXY_DataSet();
			targetLine.set(targetRI, yRange.getLowerBound());
			targetLine.set(targetRI, yRange.getUpperBound());
			targetLine.setName("Target=" + df.format(targetRI));

			funcs.add(targetLine);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GREEN));
		}

		PlotSpec spec = new PlotSpec(funcs, chars,
				rupSet.getFaultSectionData(sectIndex).getName() + " Recurrence Intervals", "Years", "Density");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false, null, yRange);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void plotSubSectRecurrenceIntervalVsTime(List<List<ETAS_EqkRupture>> catalogs,
			FaultSystemRupSet rupSet, int sectIndex, File outputDir, double targetRI) throws IOException {
		HashSet<Integer> ruptures = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex));

		DefaultXY_DataSet riVsTimeScatterFunc = new DefaultXY_DataSet();
		double maxValue = 0d;
		double sum = 0d;

		int totNumEvents = 0;

		List<ETAS_EqkRupture> firstCatalog = catalogs.get(0);
		double simulationDuration = calcEventTimeYears(firstCatalog, firstCatalog.get(firstCatalog.size() - 1));

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			double prevTime = -1;
			boolean firstInterval = true;
			boolean secondInterval = false;

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0 || !ruptures.contains(rup.getFSSIndex()))
					continue;
				totNumEvents += 1.0;
				double myTime = calcEventTimeYears(catalog, rup);

				if (prevTime >= 0) {
					double interval = myTime - prevTime;
					riVsTimeScatterFunc.set(myTime, interval);
					maxValue = Math.max(maxValue, interval);
					sum += interval;
				}

				prevTime = myTime;
			}
		}
		double mean = sum / riVsTimeScatterFunc.size();

		String info = "Num Catalogs = " + catalogs.size() + "\n";

		if (!Double.isNaN(targetRI))
			info += "targetRI = " + (float) targetRI + "\n";
		info += "meanRI = " + (float) mean + "\n";
		info += "num intervals = " + riVsTimeScatterFunc.size() + "\n";

		riVsTimeScatterFunc.setName("RI vs Time");
		riVsTimeScatterFunc.setInfo(info);

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		DecimalFormat df = new DecimalFormat("0.0");

		funcs.add(riVsTimeScatterFunc);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.X, 2f, Color.BLACK));
		String prefix = "sub_sect_RIvsTime_" + sectIndex;

		DefaultXY_DataSet meanLine = new DefaultXY_DataSet();
		meanLine.set(0.0, mean);
		meanLine.set(simulationDuration, mean);
		meanLine.setName("Mean=" + df.format(mean));

		funcs.add(meanLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));

		if (!Double.isNaN(targetRI)) {
			DefaultXY_DataSet targetLine = new DefaultXY_DataSet();
			targetLine.set(0.0, targetRI);
			targetLine.set(simulationDuration, targetRI);
			targetLine.setName("Target=" + df.format(targetRI));

			funcs.add(targetLine);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		}

		PlotSpec spec = new PlotSpec(funcs, chars,
				rupSet.getFaultSectionData(sectIndex).getName() + " Recurrence Intervals vs Time", "Years",
				"RI (years)");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		Range yRange = new Range(0d, riVsTimeScatterFunc.getMaxY());

		gp.drawGraphPanel(spec, false, false, null, yRange);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void plotNormRecurrenceIntForAllSubSectHist(List<List<ETAS_EqkRupture>> catalogs,
			FaultSystemSolutionERF_ETAS erf, File outputDir) throws IOException {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		// compute the long-term rate of each section
		double[] longTermRateOfFltSysRup = erf.getLongTermRateOfFltSysRupInERF();
		double[] longTermPartRateForSectArray = new double[rupSet.getNumSections()];
		for (int r = 0; r < rupSet.getNumRuptures(); r++) {
			List<Integer> sectIndices = rupSet.getSectionsIndicesForRup(r);
			for (int s = 0; s < sectIndices.size(); s++) {
				int sectID = sectIndices.get(s);
				longTermPartRateForSectArray[sectID] += longTermRateOfFltSysRup[r];
			}
		}

		List<Double> intervals = Lists.newArrayList();
		double maxValue = 0d;
		double sum = 0d;

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			double[] prevTime = new double[rupSet.getNumSections()];
			for (int i = 0; i < rupSet.getNumSections(); i++)
				prevTime[i] = -1;

			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0)
					continue;
				double myTime = calcEventTimeYears(catalog, rup);
				for (int sectID : rupSet.getSectionsIndicesForRup(rup.getFSSIndex())) {
					if (prevTime[sectID] >= 0) {
						double interval = (myTime - prevTime[sectID]) * longTermPartRateForSectArray[sectID];
						intervals.add(interval);
						maxValue = Math.max(maxValue, interval);
						sum += interval;
					}
					prevTime[sectID] = myTime;
				}
			}
		}
		double mean = sum / intervals.size();

		String info = "Num Catalogs = " + catalogs.size() + "\n";
		info += "meanNormRI = " + (float) mean + "\n";
		info += "intervals.size() = " + intervals.size() + "\n";

		// Compute mean that does not include quick re-ruptures (within first
		// 10% of ave RI)
		double meanFiltered = 0;
		int numFiltered = 0;
		for (double ri : intervals) {
			if (ri > 0.1) {
				meanFiltered += ri;
				numFiltered += 1;
			}
		}
		meanFiltered /= (double) numFiltered;

		info += "meanFiltered = " + (float) meanFiltered + "\t(RIs within first 10% of ave RI excluded)\n";
		info += "numFiltered = " + numFiltered + "\n";

		System.out.println(info);

		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(0d, maxValue, 0.1);
		hist.setName("Histogram");
		hist.setInfo(info);

		for (double interval : intervals)
			hist.add(interval, 1d);

		hist.normalizeBySumOfY_Vals();
		hist.scale(1.0 / hist.getDelta()); // make into a density
		Range yRange = new Range(0d, 2.6);
		Range xRange = new Range(0d, 5.0);

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		String prefix = "all_sub_sect__norm_recurrence_int_hist";

		PlotSpec spec = new PlotSpec(funcs, chars, "Norm Recurrence Intervals for All Sections", "Normalized RI",
				"Density");
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix + ".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
	}

	public static void plotSubSectNuclMagFreqDist(List<List<ETAS_EqkRupture>> catalogs, FaultSystemSolutionERF_ETAS erf,
			int sectIndex, File outputDir) throws IOException {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		HashSet<Integer> ruptures = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex));
		FaultPolyMgr faultPolyMgr = FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				InversionTargetMFDs.FAULT_BUFFER); // this works for U3, but not
													// generalized
		Region subSectPoly = faultPolyMgr.getPoly(sectIndex);

		SummedMagFreqDist mfdSupra = new SummedMagFreqDist(2.55, 8.45, 60);
		SummedMagFreqDist mfdSupra2 = new SummedMagFreqDist(2.55, 8.45, 60);
		SummedMagFreqDist mfdSub = new SummedMagFreqDist(2.55, 8.45, 60);

		// get catalog duration from the first catalog
		List<ETAS_EqkRupture> firstCatalog = catalogs.get(0);
		double catalogDuration = calcEventTimeYears(firstCatalog, firstCatalog.get(firstCatalog.size() - 1));
		double normFactor = catalogDuration * catalogs.size();

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				// check whether it nucleates inside polygon
				if (subSectPoly.contains(rup.getHypocenterLocation())) {
					if (rup.getFSSIndex() < 0) {
						int index = mfdSupra.getClosestXIndex(rup.getMag());
						mfdSub.add(index, 1.0 / normFactor);
					} else {
						int index = mfdSupra.getClosestXIndex(rup.getMag());
						mfdSupra.add(index, 1.0 / normFactor);
					}
				}

				// set supraMFD based on nucleation spread over rup surface
				if (rup.getFSSIndex() >= 0 && ruptures.contains(rup.getFSSIndex())) {
					List<Integer> sectionList = rupSet.getSectionsIndicesForRup(rup.getFSSIndex());
					FaultSectionPrefData sectData = rupSet.getFaultSectionData(sectIndex);
					double sectArea = sectData.getTraceLength() * sectData.getReducedDownDipWidth();
					double rupArea = 0;
					for (int sectID : sectionList) {
						sectData = rupSet.getFaultSectionData(sectID);
						rupArea += sectData.getTraceLength() * sectData.getReducedDownDipWidth();
					}
					int index = mfdSupra.getClosestXIndex(rup.getMag());
					mfdSupra2.add(index, sectArea / (normFactor * rupArea));

				}
			}
		}

		mfdSupra.setName("Simulated Supra MFD for " + rupSet.getFaultSectionData(sectIndex).getName());
		mfdSupra.setInfo("actually nucleated in section");
		mfdSupra2.setName("Simulated Supra Alt MFD for " + rupSet.getFaultSectionData(sectIndex).getName());
		mfdSupra2.setInfo("nucleation probability from section  and rupture area");
		mfdSub.setName("Simulated SubSeis MFD for " + rupSet.getFaultSectionData(sectIndex).getName());

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.add(mfdSupra);
		funcs.add(mfdSupra2);
		funcs.add(mfdSub);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

		String prefix = "sub_sect_nucl_mfd_" + sectIndex;

		GraphWindow plotGraph = new GraphWindow(funcs, rupSet.getFaultSectionData(sectIndex).getName() + " Nucl. MFDs",
				chars);
		plotGraph.setX_AxisLabel("Magnitude (M)");
		plotGraph.setY_AxisLabel("Rate (per yr)");
		plotGraph.setY_AxisRange(1e-7, 1e-1);
		// plotGraph.setX_AxisRange(2.5d, 8.5d);
		plotGraph.setYLog(true);
		plotGraph.setPlotLabelFontSize(18);
		plotGraph.setAxisLabelFontSize(22);
		plotGraph.setTickLabelFontSize(20);

		try {
			plotGraph.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
			plotGraph.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void plotSubSectPartMagFreqDist(List<List<ETAS_EqkRupture>> catalogs, FaultSystemSolutionERF_ETAS erf,
			int sectIndex, File outputDir) throws IOException {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		HashSet<Integer> ruptures = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex));

		SummedMagFreqDist mfd = new SummedMagFreqDist(5.05, 8.45, 35);

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0 || !ruptures.contains(rup.getFSSIndex()))
					continue;
				mfd.addResampledMagRate(rup.getMag(), 1.0, true);
			}
		}

		// get catalog duration from the first catalog
		List<ETAS_EqkRupture> firstCatalog = catalogs.get(0);
		double catalogDuration = calcEventTimeYears(firstCatalog, firstCatalog.get(firstCatalog.size() - 1));
		mfd.scale(1.0 / (catalogDuration * catalogs.size()));
		mfd.setName("Simulated MFD for " + rupSet.getFaultSectionData(sectIndex).getName());

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.add(mfd);
		funcs.add(mfd.getCumRateDistWithOffset());
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLUE));

		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		SummedMagFreqDist targetMFD = FaultSysSolutionERF_Calc.calcParticipationMFDForAllSects(erf, 5.05, 8.45,
				35)[sectIndex];
		targetMFD.setName("Target MFD for " + rupSet.getFaultSectionData(sectIndex).getName());
		funcs.add(targetMFD);
		funcs.add(targetMFD.getCumRateDistWithOffset());
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));

		String prefix = "sub_sect_part_mfd_" + sectIndex;

		GraphWindow plotGraph = new GraphWindow(funcs, rupSet.getFaultSectionData(sectIndex).getName() + " Part. MFDs",
				chars);
		plotGraph.setX_AxisLabel("Magnitude (M)");
		plotGraph.setY_AxisLabel("Rate (per yr)");
		plotGraph.setY_AxisRange(1e-7, 1e-1);
		// plotGraph.setX_AxisRange(2.5d, 8.5d);
		plotGraph.setYLog(true);
		plotGraph.setPlotLabelFontSize(18);
		plotGraph.setAxisLabelFontSize(22);
		plotGraph.setTickLabelFontSize(20);

		try {
			plotGraph.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
			plotGraph.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// PlotSpec spec = new PlotSpec(funcs, chars,
		// rupSet.getFaultSectionData(sectIndex).getName()+" MFDs",
		// "Magnitude", "Rate (per yr)");
		// HeadlessGraphPanel gp = new HeadlessGraphPanel();
		//
		// setFontSizes(gp);
		//
		// gp.drawGraphPanel(spec, false, false, null, null);
		// gp.setYLog(true);
		// gp.getCartPanel().setSize(1000, 800);
		// gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		// gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		// gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}

	/**
	 * This plots the cumulative number of occurrences with time for the
	 * specified section to see if there is any increase in rate.
	 * 
	 * @param catalogs
	 * @param erf
	 * @param sectIndex
	 * @param outputDir
	 * @throws IOException
	 */
	public static void plotCumNumWithTimeForSection(List<List<ETAS_EqkRupture>> catalogs,
			FaultSystemSolutionERF_ETAS erf, int sectIndex, File outputDir) throws IOException {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		HashSet<Integer> ruptures = new HashSet<Integer>(rupSet.getRupturesForSection(sectIndex));

		ArbitrarilyDiscretizedFunc cumNumWithTimeFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc comparisonLineFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc tempFunc = new ArbitrarilyDiscretizedFunc();

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0 || !ruptures.contains(rup.getFSSIndex()))
					continue;
				tempFunc.set(calcEventTimeYears(catalog, rup), 1.0);
			}
		}

		double cumVal = 0;
		double numSimulations = catalogs.size();
		for (int i = 0; i < tempFunc.size(); i++) {
			cumVal += 1.0 / numSimulations;
			cumNumWithTimeFunc.set(tempFunc.getX(i), cumVal);
		}
		cumNumWithTimeFunc.setName("cumNumWithTimeFunc");

		comparisonLineFunc.set(cumNumWithTimeFunc.get(0));
		comparisonLineFunc.set(cumNumWithTimeFunc.get(cumNumWithTimeFunc.size() - 1));
		comparisonLineFunc.setName("comparisonLineFunc");

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.add(cumNumWithTimeFunc);
		funcs.add(comparisonLineFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));

		String prefix = "sub_sect_cumNumWithTime_" + sectIndex;

		GraphWindow plotGraph = new GraphWindow(funcs, rupSet.getFaultSectionData(sectIndex).getName(), chars);
		plotGraph.setX_AxisLabel("Time (years)");
		plotGraph.setY_AxisLabel("Cum Num");
		// plotGraph.setY_AxisRange(1e-7, 1e-1);
		// plotGraph.setX_AxisRange(2.5d, 8.5d);
		// plotGraph.setYLog(true);
		plotGraph.setPlotLabelFontSize(18);
		plotGraph.setAxisLabelFontSize(22);
		plotGraph.setTickLabelFontSize(20);

		try {
			plotGraph.saveAsPDF(new File(outputDir, prefix + ".pdf").getAbsolutePath());
			plotGraph.saveAsTXT(new File(outputDir, prefix + ".txt").getAbsolutePath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// PlotSpec spec = new PlotSpec(funcs, chars,
		// rupSet.getFaultSectionData(sectIndex).getName()+" MFDs",
		// "Magnitude", "Rate (per yr)");
		// HeadlessGraphPanel gp = new HeadlessGraphPanel();
		//
		// setFontSizes(gp);
		//
		// gp.drawGraphPanel(spec, false, false, null, null);
		// gp.setYLog(true);
		// gp.getCartPanel().setSize(1000, 800);
		// gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		// gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		// gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}

	public static void plotConditionalHypocenterDist(List<List<ETAS_EqkRupture>> catalogs, File outputDir,
			FaultSystemRupSet rupSet) throws IOException {
		HistogramFunction hist = new HistogramFunction(0.025, 0.475, 10);

		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getFSSIndex() < 0)
					continue;
				// super crude for now, just use distance to start/end points
				// List<FaultSectionPrefData> sectData =
				// rupSet.getFaultSectionDataForRupture(rup.getFSSIndex());
				// FaultSectionPrefData first = sectData.get(0);
				// FaultSectionPrefData last = sectData.get(sectData.size()-1);
				// Location start = first.getFaultTrace().first();
				// Location end = last.getFaultTrace().last();
				RuptureSurface surf = rupSet.getSurfaceForRupupture(rup.getFSSIndex(), 1d, false);
				Location hypo = rup.getHypocenterLocation();
				// Location start = surf.getFirstLocOnUpperEdge();
				// Location end = surf.getLastLocOnUpperEdge();
				// double length = LocationUtils.horzDistanceFast(start, end);
				// double d1 = LocationUtils.horzDistanceFast(start, hypo);
				// double d2 = LocationUtils.horzDistanceFast(end, hypo);
				// // scale distances to match length
				// double s = length/(d1+d2);
				// d1 *= s;
				// d2 *= s;
				// double sum = d1+d2;
				// Preconditions.checkState((float)sum == (float)length, "%s !=
				// %s", sum, length);
				// double das = d1/sum;
				// Preconditions.checkState(das >= 0 && das <= 1,
				// "bad DAS: %s (d1=%s, d2=%s,len=%s, s=%s)",
				// das, d1, d2, length, s);

				FaultTrace upperEdge = surf.getEvenlyDiscritizedUpperEdge();

				int closest = -1;
				double closestDist = Double.POSITIVE_INFINITY;
				for (int i = 0; i < upperEdge.size(); i++) {
					double dist = LocationUtils.horzDistanceFast(hypo, upperEdge.get(i));
					if (dist < closestDist) {
						closest = i;
						closestDist = dist;
					}
				}

				double das = 0;
				double totLen = 0;

				for (int i = 1; i < upperEdge.size(); i++) {
					double d = LocationUtils.horzDistanceFast(upperEdge.get(i - 1), upperEdge.get(i));
					if (i <= closest)
						das += d;
					totLen += d;
				}

				das /= totLen;

				if (das > 0.5)
					das = (1d - das);
				hist.add(das, 1d);
			}
		}
		hist.normalizeBySumOfY_Vals();

		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		PlotSpec spec = new PlotSpec(funcs, chars, "Conditional Hypocenter Distribution",
				"Normalized Distance Along Strike", "Density");
		HeadlessGraphPanel gp = new HeadlessGraphPanel();

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, false, null, null);
		gp.setYLog(true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, "cond_hypo_dist.png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, "cond_hypo_dist.pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, "cond_hypo_dist.txt").getAbsolutePath());
	}

	public static void plotScalesOfHazardChange(List<List<ETAS_EqkRupture>> childrenCatalogs,
			List<List<ETAS_EqkRupture>> catalogs, TestScenario scenario, long ot, FaultSystemSolutionERF erf,
			File outputDir, String name, double inputDuration, boolean rates, boolean subSects) throws IOException {
		// double[] mags = {0d, 6.7, 7d, 7.5d};
		double[] mags = { 0d, 6.7 };
		// double[] mags = {0d};
		plotScalesOfHazardChange(childrenCatalogs, catalogs, null, scenario, ot, erf, outputDir, name, inputDuration,
				rates, subSects, null, mags);
	}

	public static void plotScalesOfHazardChange(List<List<ETAS_EqkRupture>> childrenCatalogs,
			List<List<ETAS_EqkRupture>> catalogs, List<List<ETAS_EqkRupture>> childrenCatalogs2, TestScenario scenario,
			long ot, FaultSystemSolutionERF erf, File outputDir, String name, double inputDuration, boolean rates,
			boolean subSects, HashSet<Integer> sects, double[] mags) throws IOException {
		// boolean containsSpontaneous = false;
		// double maxDuration = 0d;
		// for (List<ETAS_EqkRupture> catalog : catalogs) {
		// if (!catalog.isEmpty())
		// maxDuration = Double.max(maxDuration, calcDurationYears(catalog));
		// for (ETAS_EqkRupture rup : catalog) {
		// if (rup.getGeneration() == 0) {
		// containsSpontaneous = true;
		// break;
		// }
		// }
		// }
		// // round duration
		// maxDuration = (double)(int)(maxDuration + 0.5);
		boolean containsSpontaneous = false;
		if (catalogs != null && childrenCatalogs2 == null) {
			for (List<ETAS_EqkRupture> catalog : catalogs) {
				if (containsSpontaneous)
					break;
				for (ETAS_EqkRupture rup : catalog) {
					if (rup.getGeneration() == 0) {
						containsSpontaneous = true;
						break;
					}
				}
			}
		}
		List<List<ETAS_EqkRupture>> spontOnlyCatalogs = null;
		if (containsSpontaneous) {
			spontOnlyCatalogs = Lists.newArrayList();
			for (int i = 0; i < catalogs.size(); i++) {
				HashSet<Integer> children = new HashSet<Integer>();
				for (ETAS_EqkRupture rup : childrenCatalogs.get(i))
					children.add(rup.getID());
				List<ETAS_EqkRupture> catalog = Lists.newArrayList();
				for (ETAS_EqkRupture rup : catalogs.get(i))
					if (!children.contains(rup.getID()))
						catalog.add(rup);
				spontOnlyCatalogs.add(catalog);
			}
		}

		boolean xAxisInverted = false;
		// int numX = 20;
		// int numX = 40;
		int etasNumX = 80;
		int u3NumX = 20;

		double[] times = { 1d / (365.25 * 24), 1d / 365.25, 7d / 365.25, 30 / 365.25, 1d, 10d, 30d, 100d };
		// evenly discretized in log space from min to max
		EvenlyDiscretizedFunc evenlyDiscrTimes = new EvenlyDiscretizedFunc(Math.log(times[0]),
				Math.log(times[times.length - 1]), etasNumX);
		ArbitrarilyDiscretizedFunc etasTimesFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : evenlyDiscrTimes)
			etasTimesFunc.set(Math.exp(pt.getX()), 0);
		for (int i = 1; i < times.length - 1; i++) {
			double x = times[i];
			etasTimesFunc.set(x, 0);
		}
		// for (double x : times)
		// etasTimesFunc.set(x, 0);
		evenlyDiscrTimes = new EvenlyDiscretizedFunc(Math.log(times[0]), Math.log(times[times.length - 1]), u3NumX);
		ArbitrarilyDiscretizedFunc u3TimesFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : evenlyDiscrTimes)
			u3TimesFunc.set(Math.exp(pt.getX()), 0);
		for (double x : times)
			u3TimesFunc.set(x, 0);

		double minDist = 30d;
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		Map<Integer, String> sectNamesMap = Maps.newHashMap();
		if (sects == null) {
			sects = new HashSet<Integer>();
			List<Location> scenarioLocs = Lists.newArrayList();
			if (scenario.getFSS_Index() >= 0)
				scenarioLocs.addAll(rupSet.getSurfaceForRupupture(scenario.getFSS_Index(), 1d, false).getUpperEdge());
			else
				scenarioLocs.add(scenario.getLocation());
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				for (Location loc : sect.getFaultTrace()) {
					for (Location scenarioLoc : scenarioLocs) {
						if (LocationUtils.horzDistanceFast(scenarioLoc, loc) < minDist) {
							if (subSects) {
								Integer id = sect.getSectionId();
								sects.add(id);
								sectNamesMap.put(id, sect.getName());
							} else {
								Integer parentID = sect.getParentSectionId();
								sects.add(parentID);
								sectNamesMap.put(parentID, sect.getParentSectionName());
							}
							break;
						}
					}
				}
			}
		} else {
			// just populate names
			if (subSects) {
				for (int sect : sects)
					sectNamesMap.put(sect, rupSet.getFaultSectionData(sect).getName());
			} else {
				for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList())
					if (sects.contains(sect.getParentSectionId()))
						sectNamesMap.put(sect.getParentSectionId(), sect.getParentSectionName());
			}
		}

		int startYear = calcYearForOT(ot);
		System.out.println("Detected start year: " + startYear);

		for (double mag : mags) {
			System.out.println("Calculating scales of change for M>=" + mag);
			Map<Integer, HashSet<Integer>> rupsForSect = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsTI = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsTD = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETAS = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETAS2 = null;
			if (childrenCatalogs2 != null)
				funcsETAS2 = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETASWithSpont = null;
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETASSpontOnly = null;
			if (containsSpontaneous) {
				funcsETASWithSpont = Maps.newHashMap();
				funcsETASSpontOnly = Maps.newHashMap();
			}
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETASOnly = Maps.newHashMap();
			Map<Integer, ArbitrarilyDiscretizedFunc> funcsETASOnlyAfter = Maps.newHashMap();
			for (int sectID : sects) {
				HashSet<Integer> rups = new HashSet<Integer>();
				List<Integer> allRups;
				if (subSects)
					allRups = rupSet.getRupturesForSection(sectID);
				else
					allRups = rupSet.getRupturesForParentSection(sectID);
				for (int rup : allRups) {
					if (rupSet.getMagForRup(rup) >= mag)
						rups.add(rup);
				}
				rupsForSect.put(sectID, rups);
				ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
				func.setName("UCERF3-TI");
				funcsTI.put(sectID, func);
				func = new ArbitrarilyDiscretizedFunc();
				func.setName("UCERF3-TD");
				funcsTD.put(sectID, func);
				func = new ArbitrarilyDiscretizedFunc();
				func.setName("UCERF3-ETAS");
				funcsETAS.put(sectID, func);
				if (funcsETAS2 != null) {
					func = new ArbitrarilyDiscretizedFunc();
					func.setName("UCERF3-ETAS2");
					funcsETAS2.put(sectID, func);
				}
				func = new ArbitrarilyDiscretizedFunc();
				func.setName("UCERF3-ETAS Only");
				funcsETASOnly.put(sectID, func);
				func = new ArbitrarilyDiscretizedFunc();
				func.setName("UCERF3-ETAS Prob After");
				funcsETASOnlyAfter.put(sectID, func);
				if (containsSpontaneous) {
					func = new ArbitrarilyDiscretizedFunc();
					func.setName("UCERF3-ETAS Total");
					funcsETASWithSpont.put(sectID, func);
					func = new ArbitrarilyDiscretizedFunc();
					func.setName("UCERF3-ETAS Non-Scenario");
					funcsETASSpontOnly.put(sectID, func);
				}
			}

			// calc UCERF3-TI
			System.out.println("Calculating UCERF3-TI for all durations");
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			erf.getTimeSpan().setDuration(1d);
			erf.updateForecast();
			Preconditions.checkState(erf.getTimeSpan().getDuration() == 1d);
			for (Integer sectID : sects) {
				double annualRate = calcParticipationRate(erf, rupsForSect.get(sectID), 1d);
				for (int t = 0; t < etasTimesFunc.size(); t++) {
					double duration = etasTimesFunc.getX(t);
					double rateForDuration = annualRate * duration;
					double val;
					if (rates)
						val = rateForDuration;
					else {
						val = 1d - Math.exp(-rateForDuration);
					}
					funcsTI.get(sectID).set(duration, val);
				}
			}
			// now set up for UCERF3-TD
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
			erf.setParameter(HistoricOpenIntervalParam.NAME, (double) (startYear - 1875));
			erf.getTimeSpan().setStartTime(startYear);
			if (scenario.getFSS_Index() >= 0) {
				// use the start time of the ERF, not the scenario time. our
				// haphazard use of fractional
				// days (365.25 days/yr to account for leap years) can lead to
				// the scenario happening slightly
				// after the ERF start time, which makes the ERF ignore elastic
				// rebound. Fix this by using
				// the ERF start time
				long myOT = erf.getTimeSpan().getStartTimeCalendar().getTime().getTime();
				System.out.println("Setting date of occurance for rup " + scenario.getFSS_Index() + " to " + myOT);
				erf.setFltSystemSourceOccurranceTimeForFSSIndex(scenario.getFSS_Index(), myOT);
			}

			System.out.println("Calculating UCERF3-TD (" + u3TimesFunc.size() + " points)");
			for (int t = 0; t < u3TimesFunc.size(); t++) {
				double duration = u3TimesFunc.getX(t);
				System.out.println("Calculating duration: " + duration + " yrs");
				erf.getTimeSpan().setDuration(duration);
				erf.getTimeSpan().setStartTime(startYear);
				erf.updateForecast();
				Preconditions.checkState(erf.getTimeSpan().getDuration() == duration);
				for (Integer parentID : sects) {
					double val;
					if (rates)
						val = calcParticipationRate(erf, rupsForSect.get(parentID), duration);
					else {
						val = calcParticipationProb(erf, rupsForSect.get(parentID));
					}
					funcsTD.get(parentID).set(duration, val);
				}
			}

			System.out.println("Calculating UCERF3-ETAS (" + etasTimesFunc.size() + " points)");
			for (int t = 0; t < etasTimesFunc.size(); t++) {
				double duration = etasTimesFunc.getX(t);
				long maxOT = ot + (long) (duration * ProbabilityModelsCalc.MILLISEC_PER_YEAR);
				for (Integer parentID : sects) {
					HashSet<Integer> rups = rupsForSect.get(parentID);
					double etasProb = calcETASPartic(childrenCatalogs, ot, maxOT, rups, rates);
					// double tdProb = funcsTD.get(parentID).getY(duration);
					double tdProb = funcsTD.get(parentID).getInterpolatedY_inLogXLogYDomain(duration);
					double sum;
					if (rates)
						sum = etasProb + tdProb;
					else
						sum = FaultSysSolutionERF_Calc.calcSummedProbs(Lists.newArrayList(etasProb, tdProb));
					funcsETAS.get(parentID).set(duration, sum); // include
																// background
																// rate
					if (funcsETAS2 != null) {
						double etasProb2 = calcETASPartic(childrenCatalogs2, ot, maxOT, rups, rates);
						// double tdProb = funcsTD.get(parentID).getY(duration);
						double sum2;
						if (rates)
							sum2 = etasProb2 + tdProb;
						else
							sum2 = FaultSysSolutionERF_Calc.calcSummedProbs(Lists.newArrayList(etasProb2, tdProb));
						funcsETAS2.get(parentID).set(duration, sum2); // include
																		// background
																		// rate
						etasProb = 0.5 * (etasProb + etasProb2); // used for
																	// fract
																	// after
					}
					if ((float) duration <= (float) inputDuration) {
						funcsETASOnly.get(parentID).set(duration, etasProb); // exclude
																				// background
																				// rate
						if (containsSpontaneous) {
							double etasProbWithSpont = calcETASPartic(catalogs, ot, maxOT, rups, rates);
							funcsETASWithSpont.get(parentID).set(duration, etasProbWithSpont);
							double etasProbSpontOnly = calcETASPartic(spontOnlyCatalogs, ot, maxOT, rups, rates);
							funcsETASSpontOnly.get(parentID).set(duration, etasProbSpontOnly);
						}
					}
				}
			}
			// etas prob after
			for (Integer parentID : sects) {
				ArbitrarilyDiscretizedFunc etasOnly = funcsETASOnly.get(parentID);
				ArbitrarilyDiscretizedFunc etasOnlyAfter = funcsETASOnlyAfter.get(parentID);
				double maxProb = etasOnly.getMaxY();
				for (int t = 0; t < etasOnly.size(); t++) {
					double time = etasOnly.getX(t);
					double etasProb = etasOnly.getY(t);
					double probAfter = maxProb - etasProb;
					if (probAfter > 0)
						etasOnlyAfter.set(time, probAfter);
				}
			}

			for (Integer sectID : sects) {
				if (rupsForSect.get(sectID).isEmpty() || funcsETASOnly.get(sectID).size() == 0)
					continue;
				String sectName = sectNamesMap.get(sectID);
				String prefix = sectName.replaceAll("\\W+", "_");
				String yAxisLabel;
				String yVal;
				if (rates)
					yVal = "Rate";
				else
					yVal = "Prob";
				if (mag == 0) {
					prefix += "_supra_seis";
					yAxisLabel = "Supra Seis Participation " + yVal;
				} else {
					prefix += "_m" + (float) mag;
					yAxisLabel = "M≥" + (float) mag + " Participation " + yVal;
				}
				if (rates)
					prefix += "_rates";
				File file = new File(outputDir, prefix);

				List<XY_DataSet> funcs = Lists.newArrayList();
				List<PlotCurveCharacterstics> chars = Lists.newArrayList();

				funcs.add(funcsTI.get(sectID));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

				funcs.add(funcsTD.get(sectID));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));

				if (containsSpontaneous) {
					funcs.add(funcsETASWithSpont.get(sectID));
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN.darker()));

					funcs.add(funcsETASSpontOnly.get(sectID));
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN.darker()));
				}

				if (funcsETAS2 != null) {
					ArbitrarilyDiscretizedFunc etas1 = funcsETAS.get(sectID);
					ArbitrarilyDiscretizedFunc etas2 = funcsETAS2.get(sectID);

					ArbitrarilyDiscretizedFunc etasMean = new ArbitrarilyDiscretizedFunc();
					ArbitrarilyDiscretizedFunc etasLower = new ArbitrarilyDiscretizedFunc();
					ArbitrarilyDiscretizedFunc etasUpper = new ArbitrarilyDiscretizedFunc();
					ArbitrarilyDiscretizedFunc etasMeanLower = new ArbitrarilyDiscretizedFunc();
					ArbitrarilyDiscretizedFunc etasMeanUpper = new ArbitrarilyDiscretizedFunc();
					for (int i = 0; i < etas1.size(); i++) {
						double x = etas1.getX(i);
						Preconditions.checkState(etas2.getX(i) == x);
						double val1 = etas1.getY(i);
						double val2 = etas2.getY(i);
						double mean = 0.5 * (val1 + val2);
						Preconditions.checkState(!Double.isNaN(mean));
						etasMean.set(x, mean);

						double[] conf1;
						if (val1 > 1)
							conf1 = new double[] { val1, val1 };
						else
							conf1 = ETAS_Utils.getBinomialProportion95confidenceInterval(val1, childrenCatalogs.size());
						double[] conf2;
						if (val2 > 1)
							conf2 = new double[] { val2, val2 };
						else
							conf2 = ETAS_Utils.getBinomialProportion95confidenceInterval(val2,
									childrenCatalogs2.size());

						etasMeanLower.set(x, Math.min(val1, val2));
						etasMeanUpper.set(x, Math.max(val1, val2));

						etasLower.set(x, Math.min(Math.min(conf1[0], conf2[0]), mean));
						etasUpper.set(x, Math.max(conf1[1], conf2[1]));

						// System.out.println(etasMFD.getX(i)+"\t"+mean+"\t"+etasLower.getY(i)+"\t"+etasUpper.getY(i));
					}
					UncertainArbDiscDataset confBounds = new UncertainArbDiscDataset(etasMean, etasLower, etasUpper);
					UncertainArbDiscDataset meanRange = new UncertainArbDiscDataset(etasMean, etasMeanLower,
							etasMeanUpper);

					Color confColor = new Color(255, 120, 120);
					funcs.add(0, confBounds);
					chars.add(0, new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, confColor));

					funcs.add(1, meanRange);
					chars.add(1, new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, confColor));

					funcs.add(etasMean);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
				} else {
					funcs.add(funcsETAS.get(sectID));
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
				}

				double minProb = 1d;
				for (XY_DataSet func : funcs)
					for (Point2D pt : func)
						if (pt.getY() > 0)
							minProb = Math.min(minProb, pt.getY());
				minProb *= 0.5;

				funcs.add(funcsETASOnly.get(sectID));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));

				// can be empty if no ETAS rups in mag range
				if (funcsETASOnlyAfter.get(sectID).size() > 0) {
					funcs.add(funcsETASOnlyAfter.get(sectID));
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.MAGENTA));
				}

				List<XYTextAnnotation> annotations = Lists.newArrayList();

				for (int i = 0; i < times.length; i++) {
					double time = times[i];
					String label;
					if (time < 1d) {
						int days = (int) (time * 365.25 + 0.5);
						if (days == 30)
							label = "1 mo";
						else if (days == 7)
							label = "1 wk";
						else if (time < 1d / 365.25) {
							int hours = (int) (time * 365.25 * 24 + 0.5);
							label = hours + " hr";
						} else
							label = days + " d";
					} else {
						label = (int) time + " yr";
					}
					DefaultXY_DataSet xy = new DefaultXY_DataSet();
					xy.set(time, minProb);
					xy.set(time, 1d);
					funcs.add(xy);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));

					XYTextAnnotation ann = new XYTextAnnotation(label, time, 0.9);
					if (i == 0 && xAxisInverted || i == (times.length - 1) && !xAxisInverted) {
						ann.setTextAnchor(TextAnchor.TOP_RIGHT);
						if (!xAxisInverted)
							ann.setY(0.4); // put it below
					} else {
						ann.setTextAnchor(TextAnchor.TOP_LEFT);
					}
					ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
					annotations.add(ann);
				}

				for (XY_DataSet func : funcs) {
					Preconditions.checkState(func.size() > 0, "Empty func with name: %s", func.getName());
				}

				PlotSpec spec = new PlotSpec(funcs, chars, sectName, "Forecast Timespan (years)", yAxisLabel);
				spec.setPlotAnnotations(annotations);
				spec.setLegendVisible(true);

				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				gp.setxAxisInverted(xAxisInverted);
				gp.setUserBounds(times[0], times[times.length - 1], minProb, 1d);

				setFontSizes(gp);

				gp.drawGraphPanel(spec, true, true);
				gp.getChartPanel().setSize(1000, 800);
				gp.saveAsPNG(file.getAbsolutePath() + ".png");
				gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
				gp.saveAsTXT(file.getAbsolutePath() + ".txt");
			}
		}
	}

	private static double calcETASPartic(List<List<ETAS_EqkRupture>> catalogs, long ot, long maxOT,
			HashSet<Integer> rups, boolean rates) {
		return calcETASPartic(catalogs, ot, maxOT, rups, rates, null, 0d);
	}

	private static double calcETASPartic(List<List<ETAS_EqkRupture>> catalogs, long ot, long maxOT,
			HashSet<Integer> rups, boolean rates, Region region, double minMag) {
		if (rates)
			return calcETASParticRate(catalogs, ot, maxOT, rups, region, minMag);
		return calcETASParticProb(catalogs, ot, maxOT, rups, region, minMag);
	}

	public static double calcETASParticProb(Iterable<List<ETAS_EqkRupture>> catalogs, long ot, long maxOT,
			HashSet<Integer> rups, Region region, double minMag) {
		int numWith = 0;
		int total = 0;
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			boolean found = false;
			for (ETAS_EqkRupture rup : catalog) {
				Preconditions.checkState(rup.getOriginTime() >= ot, "Bad event time! ot=%s, rupTime=%s", ot,
						rup.getOriginTime());
				if (rup.getMag() < minMag)
					continue;
				if (rup.getOriginTime() > maxOT)
					break;
				if (rup.getFSSIndex() >= 0) {
					// fss
					if (rups.contains(rup.getFSSIndex())) {
						found = true;
						break;
					}
				} else if (region != null) {
					// gridded
					if (region.contains(rup.getHypocenterLocation())) {
						found = true;
						break;
					}
				}
			}
			if (found)
				numWith++;
			total++;
		}
		double etasProb = (double) numWith / (double) total;
		return etasProb;
	}

	public static double calcETASParticRate(List<List<ETAS_EqkRupture>> catalogs, long ot, long maxOT,
			HashSet<Integer> rups, Region region, double minMag) {
		double rate = 0d;
		double rateEach = 1d / catalogs.size();
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				Preconditions.checkState(rup.getOriginTime() >= ot, "Bad event time! ot=%s, rupTime=%s", ot,
						rup.getOriginTime());
				if (rup.getMag() < minMag)
					continue;
				if (rup.getOriginTime() > maxOT)
					break;
				if (rup.getFSSIndex() >= 0) {
					// fss
					if (rups.contains(rup.getFSSIndex())) {
						rate += rateEach;
					}
				} else if (region != null) {
					// gridded
					if (region.contains(rup.getHypocenterLocation())) {
						rate += rateEach;
					}
				}
			}
		}
		return rate;
	}

	private static double calcParticipationProb(FaultSystemSolutionERF erf, HashSet<Integer> rups) {
		List<Double> probs = Lists.newArrayList();

		for (int sourceID = 0; sourceID < erf.getNumFaultSystemSources(); sourceID++) {
			if (rups.contains(erf.getFltSysRupIndexForSource(sourceID))) {
				probs.add(erf.getSource(sourceID).computeTotalProb());
			}
		}

		Preconditions.checkState(rups.size() == probs.size());

		return FaultSysSolutionERF_Calc.calcSummedProbs(probs);
	}

	private static double calcParticipationRate(FaultSystemSolutionERF erf, HashSet<Integer> rups, double duration) {
		double rate = 0d;

		for (int sourceID = 0; sourceID < erf.getNumFaultSystemSources(); sourceID++)
			if (rups.contains(erf.getFltSysRupIndexForSource(sourceID)))
				// multiply by duration to de-annualize it
				rate += erf.getSource(sourceID).computeTotalEquivMeanAnnualRate(duration) * duration;

		return rate;
	}

	public static int calcYearForOT(long ot) {
		int closest = 0;
		long diff = Long.MAX_VALUE;
		for (int year = 2000; year < 2100; year++) {
			long myOT = Math.round((year - 1970.0) * ProbabilityModelsCalc.MILLISEC_PER_YEAR);
			long myDiff = myOT - ot;
			if (myDiff < 0l)
				myDiff = -myDiff;
			if (myDiff < diff) {
				diff = myDiff;
				closest = year;
			}
		}
		return closest;
	}

	static void plotRegionalMPDs(List<List<ETAS_EqkRupture>> catalogs, TestScenario scenario, long ot,
			FaultSystemSolutionERF erf, File outputDir, String name, boolean rates) throws IOException {
		Region region;
		if (scenario == TestScenario.HAYWIRED_M7)
			region = new CaliforniaRegions.SF_BOX();
		else
			region = new CaliforniaRegions.LA_BOX();

		String regName = region.getName();
		regName = regName.replaceAll("Region", "");
		regName = regName.replaceAll("_", " ");
		regName = regName.trim();

		String prefix = "one_week_mfd_" + regName.replaceAll("\\W+", "_");
		if (rates)
			prefix += "_rates";
		plotRegionalMPDs(catalogs, null, scenario, region, ot, erf, outputDir, name, prefix, rates);
	}

	static void plotRegionalMPDs(List<List<ETAS_EqkRupture>> catalogs1, List<List<ETAS_EqkRupture>> catalogs2,
			TestScenario scenario, Region region, long ot, FaultSystemSolutionERF erf, File outputDir, String name,
			String prefix, boolean rates) throws IOException {
		double fssMaxMag = erf.getSolution().getRupSet().getMaxMag();
		double duration = 7d / 365.25;

		double minMag = 5;
		int numMag = 36;
		double deltaMag = 0.1;

		String regName = region.getName();
		regName = regName.replaceAll("Region", "");
		regName = regName.replaceAll("_", " ");
		regName = regName.trim();

		String yVal;
		if (rates)
			yVal = "Expected Num";
		else
			yVal = "Prob";
		String yAxisLabel = regName + " Cum. One Week Participation " + yVal;

		long maxOT = ot + (long) (duration * ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		int startYear = calcYearForOT(ot);

		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);

		// calc UCERF3-TI
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(duration);
		erf.updateForecast();
		FSSRupsInRegionCache cache = new FSSRupsInRegionCache();
		EvenlyDiscretizedFunc tiMFD;
		if (rates) {
			SummedMagFreqDist incrMFD = ERF_Calculator.getParticipationMagFreqDistInRegion(erf, region,
					minMag + 0.5 * deltaMag, numMag, deltaMag, true, cache);
			tiMFD = incrMFD.getCumRateDistWithOffset();
			tiMFD.scale(duration);
		} else {
			tiMFD = FaultSysSolutionERF_Calc.calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
		}
		tiMFD.setName("UCERF3-TI");

		// now set up for UCERF3-TD
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
		erf.setParameter(HistoricOpenIntervalParam.NAME, (double) (startYear - 1875));
		erf.getTimeSpan().setStartTime(startYear);
		if (scenario.getFSS_Index() >= 0) {
			// use the start time of the ERF, not the scenario time. our
			// haphazard use of fractional
			// days (365.25 days/yr to account for leap years) can lead to the
			// scenario happening slightly
			// after the ERF start time, which makes the ERF ignore elastic
			// rebound. Fix this by using
			// the ERF start time
			long myOT = erf.getTimeSpan().getStartTimeCalendar().getTime().getTime();
			System.out.println("Setting date of occurance for rup " + scenario.getFSS_Index() + " to " + myOT);
			erf.setFltSystemSourceOccurranceTimeForFSSIndex(scenario.getFSS_Index(), myOT);
		}
		erf.getTimeSpan().setDuration(duration);
		erf.getTimeSpan().setStartTime(startYear);
		erf.updateForecast();
		EvenlyDiscretizedFunc tdMFD;
		if (rates) {
			SummedMagFreqDist incrMFD = ERF_Calculator.getParticipationMagFreqDistInRegion(erf, region,
					minMag + 0.5 * deltaMag, numMag, deltaMag, true, cache);
			tdMFD = incrMFD.getCumRateDistWithOffset();
			tdMFD.scale(duration);
		} else {
			tdMFD = FaultSysSolutionERF_Calc.calcMagProbDist(erf, region, minMag, numMag, deltaMag, true, cache);
		}
		tdMFD.setName("UCERF3-TD");

		// now etas
		HashSet<Integer> rupsToInclude = new HashSet<Integer>();
		FaultSystemSolution sol = erf.getSolution();
		for (int fssIndex = 0; fssIndex < sol.getRupSet().getNumRuptures(); fssIndex++)
			if (cache.isRupInRegion(sol, fssIndex, region, 1d))
				rupsToInclude.add(fssIndex);

		File file = new File(outputDir, prefix);

		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();

		funcs.add(tiMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

		funcs.add(tdMFD);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));

		EvenlyDiscretizedFunc etasMFD = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(), tiMFD.size());
		for (int i = 0; i < etasMFD.size(); i++) {
			double mag = etasMFD.getX(i);
			etasMFD.set(i, calcETASPartic(catalogs1, ot, maxOT, rupsToInclude, rates, region, mag));
		}
		if (catalogs2 == null) {
			// one catalog
			etasMFD.setName("UCERF3-ETAS");
			funcs.add(etasMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		} else {
			EvenlyDiscretizedFunc etasMFD2 = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(), tiMFD.size());
			for (int i = 0; i < etasMFD2.size(); i++) {
				double mag = etasMFD2.getX(i);
				etasMFD2.set(i, calcETASPartic(catalogs2, ot, maxOT, rupsToInclude, rates, region, mag));
			}

			EvenlyDiscretizedFunc etasMean = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(), tiMFD.size());
			EvenlyDiscretizedFunc etasLower = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(), tiMFD.size());
			EvenlyDiscretizedFunc etasUpper = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(), tiMFD.size());
			EvenlyDiscretizedFunc etasMeanLower = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(),
					tiMFD.size());
			EvenlyDiscretizedFunc etasMeanUpper = new EvenlyDiscretizedFunc(tiMFD.getMinX(), tiMFD.getMaxX(),
					tiMFD.size());
			for (int i = 0; i < etasMean.size(); i++) {
				double val1 = etasMFD.getY(i);
				double val2 = etasMFD2.getY(i);
				double mean = 0.5 * (val1 + val2);
				Preconditions.checkState(!Double.isNaN(mean));
				etasMean.set(i, mean);

				double x = etasMean.getX(i);
				boolean aboveMax = x - 0.5 * deltaMag > fssMaxMag;
				if (aboveMax) {
					Preconditions.checkState(val1 == 0);
					Preconditions.checkState(val2 == 0);
				}

				double[] conf1;
				if (val1 > 1 || aboveMax)
					conf1 = new double[] { val1, val1 };
				else
					conf1 = ETAS_Utils.getBinomialProportion95confidenceInterval(val1, catalogs1.size());
				double[] conf2;
				if (val2 > 1 || aboveMax)
					conf2 = new double[] { val2, val2 };
				else
					conf2 = ETAS_Utils.getBinomialProportion95confidenceInterval(val2, catalogs2.size());

				etasMeanLower.set(i, Math.min(val1, val2));
				etasMeanUpper.set(i, Math.max(val1, val2));

				etasLower.set(i, Math.min(Math.min(conf1[0], conf2[0]), mean));
				etasUpper.set(i, Math.max(conf1[1], conf2[1]));

				// System.out.println(etasMFD.getX(i)+"\t"+mean+"\t"+etasLower.getY(i)+"\t"+etasUpper.getY(i));
			}
			UncertainArbDiscDataset confBounds = new UncertainArbDiscDataset(etasMean, etasLower, etasUpper);
			UncertainArbDiscDataset meanRange = new UncertainArbDiscDataset(etasMean, etasMeanLower, etasMeanUpper);

			Color confColor = new Color(255, 120, 120);
			funcs.add(0, confBounds);
			chars.add(0, new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, confColor));

			funcs.add(1, meanRange);
			chars.add(1, new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, confColor));

			funcs.add(etasMean);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		}

		double minY = 1d;
		double maxY = 0d;
		for (XY_DataSet func : funcs) {
			for (Point2D pt : func) {
				if (pt.getY() > 0) {
					minY = Math.min(minY, pt.getY());
					maxY = Math.max(maxY, pt.getY());
				}
			}
		}
		minY *= 0.5;

		for (XY_DataSet func : funcs) {
			Preconditions.checkState(func.size() > 0, "Empty func with name: %s", func.getName());
		}

		PlotSpec spec = new PlotSpec(funcs, chars, name, "Magnitude", yAxisLabel);
		spec.setLegendVisible(true);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		minY = 1e-6;
		if (!rates)
			maxY = 1d;
		else
			maxY = 1e1;
		gp.setUserBounds(minMag, tiMFD.getMaxX(), minY, maxY);

		setFontSizes(gp);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(file.getAbsolutePath() + ".png");
		gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
		gp.saveAsTXT(file.getAbsolutePath() + ".txt");
	}

	private static ETAS_ParameterList loadEtasParamsFromMetadata(Element root)
			throws DocumentException, MalformedURLException {
		Element paramsEl = root.element(ETAS_ParameterList.XML_METADATA_NAME);

		return ETAS_ParameterList.fromXMLMetadata(paramsEl);
	}

	public static void nedsAnalysis2() {

		System.out.println("Making ERF");
		double duration = 10;
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2012d, duration);
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		String dir = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/ScenarioSimulations";

		System.out.println("Reading catalogs");
		List<List<ETAS_EqkRupture>> catalogs = null;
		try {
			catalogs = ETAS_CatalogIO.loadCatalogsBinary(new File(dir + "/results_m4.bin"));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		int triggerParentID = 9893;
		List<List<ETAS_EqkRupture>> primaryCatalogs = Lists.newArrayList();
		if (triggerParentID >= 0) {
			for (List<ETAS_EqkRupture> catalog : catalogs)
				primaryCatalogs.add(ETAS_SimAnalysisTools.getPrimaryAftershocks(catalog, triggerParentID));
		} else {
			for (List<ETAS_EqkRupture> catalog : catalogs)
				primaryCatalogs.add(ETAS_SimAnalysisTools.getByGeneration(catalog, 0));
		}

		System.out.println("catalogs.size()=" + catalogs.size());
		// System.exit(-1);

		File outputDir = new File(dir);
		if (!outputDir.exists())
			outputDir.mkdir();

		double[] minMags = { 0d };
		try {
			plotSectRates(primaryCatalogs, -1d, rupSet, minMags, outputDir, null, "M7");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void nedsAnalysis() {

		System.out.println("Making ERF");
		double duration = 10;
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2012d, duration);
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		//// // THIS WAS TO TEST WHETHER TIME DEPENDENCE CAN EXPLAIN
		//// SYSTEMATICLY LOW SIMULATED SECTION RATES; IT CAN'T
		// // THIS ALSO SHOWS THAT TD RATES ARE BOGUS WHEN MORE THAN ONE EVENTS
		//// IS EXPECTED OVER THE DURATION
		// erf.getTimeSpan().setDuration(10000.0);
		// erf.updateForecast();
		// double[] subSectExpValsTD =
		//// FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf,
		//// 0.0);
		//// for(int s=0;s<subSectExpValsTD.length;s++)
		//// System.out.println(subSectExpValsTD[s]+"\t"+rupSet.getFaultSectionData(s).getName());
		//// System.exit(-1);
		//
		// erf.getTimeSpan().setDuration(1.0);
		// erf.setParameter(ProbabilityModelParam.NAME,
		//// ProbabilityModelOptions.POISSON);
		// erf.updateForecast();
		// double[] subSectExpValsTI =
		//// FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf,
		//// 0.0);
		//
		// File tempOutDir = new
		//// File("/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/NoScenarioSimulations/");
		// try {
		// plotScatter(subSectExpValsTD, subSectExpValsTI,"TD vs TI for 10000
		//// yrs", "","TD10000vsTI_sectRatesScatter", tempOutDir);
		// } catch (IOException e1) {
		// // TODO Auto-generated catch block
		// e1.printStackTrace();
		// }
		// System.exit(-1);

		try {

			String dir = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/NoScenarioSimulations/";

			// String simName =
			// "2015_12_08-spontaneous-1000yr-full_td-noApplyLTR";
			// String simName =
			// "2016_01_28-spontaneous-1000yr-full_td-gridSeisCorr";
			// String simName =
			// "2016_01_27-spontaneous-1000yr-newNuclWt-full_td-gridSeisCorr";
			// String simName =
			// "2016_02_04-spontaneous-10000yr-full_td-subSeisSupraNucl-gridSeisCorr";
			// String simName =
			// "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr";

			// String simName =
			// "2015_12_09-spontaneous-30yr-full_td-noApplyLTR";
			// String simName =
			// "2016_01_31-spontaneous-30yr-full_td-gridSeisCorr";
			// String simName =
			// "2016_01_31-spontaneous-30yr-newNuclWt-full_td-gridSeisCorr";
			String simName = "2016_02_18-spontaneous-30yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr";

			// String simName =
			// "2015_12_15-spontaneous-1000yr-mc10-applyGrGridded-full_td-noApplyLTR";
			// String simName =
			// "2016_01_05-spontaneous-10000yr-mc10-applyGrGridded-full_td-noApplyLTR";

			System.out.println("Reading catalogs");
			List<List<ETAS_EqkRupture>> catalogs = ETAS_CatalogIO
					.loadCatalogsBinary(new File(dir + simName + "/results_m4.bin"));
			System.out.println("catalogs.size()=" + catalogs.size());
			// System.exit(-1);

			File outputDir = new File(dir + simName);
			if (!outputDir.exists())
				outputDir.mkdir();

			// DO THIS ONE FOR 30-YEAR SIMULATIONS
			// System.out.println("ETAS_MultiSimAnalysisTools.writeSubSectRecurrenceIntervalStats(*)");
			try {
				plotAndWriteSectProbOneOrMoreData(catalogs, 10d, erf, outputDir);
			} catch (GMT_MapException e1) {
				e1.printStackTrace();
			} catch (RuntimeException e1) {
				e1.printStackTrace();
			}
			System.exit(-1);

			// DO THIS ONE FOR 1000-YEAR SIMULATIONS
			plotNormRecurrenceIntForAllSubSectHist(catalogs, erf, outputDir);
			// this not really necessary?:
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			erf.updateForecast();
			try {
				// plotBinnedSectParticRateVsExpRate(catalogs, -1.0, erf,
				// outputDir, "binned_sect_partic");
				plotSectParticScatter(catalogs, -1.0, erf, outputDir);

				// int numSubCat = 5;
				// int numInCat = catalogs.size()/numSubCat;
				// for(int i=0;i<numSubCat;i++) {
				// plotBinnedSectParticRateVsExpRate(catalogs.subList(i*numInCat,
				// (i+1)*numInCat-1), -1.0, erf, outputDir,
				// "binned_sect_partic"+i);
				// }
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.exit(-1);

			outputDir = new File(dir + simName + "/subSectPlots");
			if (!outputDir.exists())
				outputDir.mkdir();
			int[] sectIndexArray = { 1906, 1850, 1922, 1946 };
			double[] sectPartRate = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 5.0);
			for (int sectIndex : sectIndexArray) {
				plotSubSectRecurrenceHist(catalogs, rupSet, sectIndex, outputDir, 1.0 / sectPartRate[sectIndex]);
				plotSubSectRecurrenceIntervalVsTime(catalogs, rupSet, sectIndex, outputDir,
						1.0 / sectPartRate[sectIndex]);
				// double probOneOrMore = 1-Math.exp(-sectPartRate*duration);
				// System.out.println("Model Prob one or more in "+duration+"
				// years ="+(float)probOneOrMore);
				// System.out.println("Model exp num in "+duration+" years
				// ="+(float)(sectPartRate*duration));
				plotSubSectNuclMagFreqDist(catalogs, erf, sectIndex, outputDir);
				plotSubSectPartMagFreqDist(catalogs, erf, sectIndex, outputDir);
				plotCumNumWithTimeForSection(catalogs, erf, sectIndex, outputDir);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static TestScenario detectScenario(File directory) {
		String dirName = directory.getName().toLowerCase();
		if (dirName.contains("spontaneous")) {
			System.out.println("Detected spontaneous");
		} else {
			for (TestScenario test : TestScenario.values()) {
				if (dirName.contains(test.name().toLowerCase())) {
					return test;
				}
			}
			if (dirName.contains("swarm"))
				System.out.println("Detected swarm, treating as spontaneous");
			else
				throw new IllegalStateException("Couldn't detect scenario from dir name: " + dirName);
		}
		return null;
	}

	private static final String plotDirName = "plots";
	private static final String catsDirName = "selected_catalogs";

	/**
	 * 
	 * set full_TD true to make FULL_TD case; otherwise it's NO_ERT (filenames
	 * differe accordingly)
	 */
	private static void makeImagesForSciencePaperFig1(int model) {

		System.out.println("Loading file");
		File resultsFile = null;
		if (model == 0)
			resultsFile = new File(
					"/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/ScenarioSimulations/KevinsMultiSimRuns/2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k/results_descendents_m5_preserve.bin");
		else if (model == 1)
			resultsFile = new File(
					"/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/ScenarioSimulations/KevinsMultiSimRuns/2016_02_22-mojave_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined100k/results_descendents_m5_preserve.bin");
		else if (model == 2)
			resultsFile = new File(
					"/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/ScenarioSimulations/KevinsMultiSimRuns/2016_02_22-mojave_m7-10yr-BothModels/results_descendents_m5_preserve_merged_with_100k_full_td.bin");

		List<List<ETAS_EqkRupture>> catalogs = null;
		try {
			catalogs = ETAS_CatalogIO.loadCatalogs(resultsFile, 6.7, true);
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// for 30-year result - hard coded overide
		// double duration = 7/365.25;
		double duration = 30;
		Long ot = Math.round((2012.0 - 1970.0) * ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs
																							// at
																							// 2012
		long maxOT = ot + (long) (duration * ProbabilityModelsCalc.MILLISEC_PER_YEAR);

		int fssIndex = 193821; // the M7 Mojave scenario
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2012, duration);
		int srcID = erf.getSrcIndexForFltSysRup(fssIndex);
		erf.setFltSystemSourceOccurranceTime(srcID, ot);
		erf.updateForecast();
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		GriddedRegion griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.1);
		FaultPolyMgr polyManager = FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				InversionTargetMFDs.FAULT_BUFFER); // this works for U3, but not
													// generalized
		double[] zCount = new double[griddedRegion.getNodeCount()];

		System.out.println("Looping over catalog");
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getOriginTime() > maxOT)
					break;
				if (rup.getFSSIndex() >= 0) {
					// fault system rupture
					for (int s : rupSet.getSectionsIndicesForRup(rup.getFSSIndex())) {
						Map<Integer, Double> nodeFracts = polyManager.getNodeFractions(s);
						for (int n : nodeFracts.keySet()) {
							zCount[n] += nodeFracts.get(n);
						}
					}
				} else {
					// gridded rupture
					// if(rup.getGeneration()==1)
					zCount[griddedRegion.indexForLocation(rup.getHypocenterLocation())] += 1;

					// if(rup.getMag()<6.7) {
					// zCount[griddedRegion.indexForLocation(rup.getHypocenterLocation())]
					// += 1;
					// }
					// else { // spread over neighboring cells
					// Location loc = rup.getHypocenterLocation();
					// // smoothe over neighboring cells
					// double totWt=0;
					// for(int i=-1;i<=1;i++) {
					// for(int j=-1;j<=1;j++) {
					// Location newLoc = new Location(loc.getLatitude()+i*0.1,
					// loc.getLongitude()+j*0.1, 0.0);
					// double wt = 0.5/8;
					// if(i==0 & j==0)
					// wt = 0.5;
					// zCount[griddedRegion.indexForLocation(newLoc)] += wt;
					// totWt += wt;
					// }
					// }
					// if(totWt<0.9999 || totWt>1.0001)
					// throw new RuntimeException("");
					// }
				}
			}
		}

		GriddedGeoDataSet triggerData = new GriddedGeoDataSet(griddedRegion, true); // true
																					// makes
																					// X
																					// latitude
		GriddedGeoDataSet ratioData = new GriddedGeoDataSet(griddedRegion, true); // true
																					// makes
																					// X
																					// latitude

		System.out.println("Making Long Term Data");
		GriddedGeoDataSet longTermTD_data = FaultSysSolutionERF_Calc.calcParticipationProbInGriddedRegionFltMapped(erf,
				griddedRegion, 6.7, 10.0);

		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		System.out.println("Making Time Ind. Data");
		GriddedGeoDataSet timeInd_data = FaultSysSolutionERF_Calc.calcParticipationProbInGriddedRegionFltMapped(erf,
				griddedRegion, 6.7, 10.0);

		for (int n = 0; n < zCount.length; n++) {
			triggerData.set(n, Math.log10(zCount[n] / 100000d)); // 100k
																	// simulations
			double value = Math.log10((zCount[n] / 100000d) / longTermTD_data.get(n) + 1);
			if (Double.isNaN(value))
				value = 0.0;
			ratioData.set(n, value); // 100k simulations
			longTermTD_data.set(n, Math.log10(longTermTD_data.get(n)));
			timeInd_data.set(n, Math.log10(timeInd_data.get(n)));
		}

		boolean includeTopo = false;

		System.out.println("Making Plots");
		try {
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			double minValue = -8;
			double maxValue = -2;
			File dir = null;
			if (model == 0)
				dir = new File("SciFig1_FULL_TD_BackgroundImages");
			else if (model == 1)
				dir = new File("SciFig1_NO_ERT_BackgroundImages");
			else
				dir = new File("SciFig1_BOTH_BackgroundImages");
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerData, griddedRegion, dir, "triggerData",
					true, cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(longTermTD_data, griddedRegion, dir,
					"longTermTD_data", true, cpt, minValue, maxValue, includeTopo);
			maxValue = -3;

			// for 30-year result - hard coded overide
			minValue = -5;
			maxValue = 0;

			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(timeInd_data, griddedRegion, dir, "timeInd_data",
					true, cpt, minValue, maxValue, includeTopo);
			CPT cpt_ratio = GMT_CPT_Files.UCERF3_ETAS_GAIN.instance();
			minValue = -3;
			maxValue = 3;
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(ratioData, griddedRegion, dir, "ratioData", true,
					cpt_ratio, minValue, maxValue, includeTopo);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * set full_TD true to make FULL_TD case; otherwise it's NO_ERT (filenames
	 * differe accordingly)
	 */
	private static void makeImagesForEqkSpectraPaperFig1() {

		System.out.println("running makeImagesForEqkSpectraPaperFig1");

		System.out.println("Loading file");
		File resultsFile = new File(
				"/Users/field/Field_Other/CEA_WGCEP/UCERF3/UCERF3-ETAS/ResultsAndAnalysis/ScenarioSimulations/KevinsMultiSimRuns/2016_06_15-haywired_m7-10yr-BothModels/2016_06_15-haywired_m7-10yr-full_td-no_ert-combined-results_descendents_m5.bin");

		List<List<ETAS_EqkRupture>> catalogs = null;
		try {
			catalogs = ETAS_CatalogIO.loadCatalogs(resultsFile, 6.7, true);
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// for 1-year result
		double duration = 1;
		Long ot = Math.round((2012.0 - 1970.0) * ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs
																							// at
																							// 2012
		long maxOT = ot + (long) (duration * ProbabilityModelsCalc.MILLISEC_PER_YEAR);

		// SourceIndex=101485 Inversion Src #101499 for Haywired
		int fssIndex = 101499;
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2012, duration);
		int srcID = erf.getSrcIndexForFltSysRup(fssIndex);
		// erf.setFltSystemSourceOccurranceTime(srcID, ot); // don't apply
		// because we want relative to pre-event probabilities
		erf.updateForecast();
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		GriddedRegion griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.1);
		FaultPolyMgr polyManager = FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				InversionTargetMFDs.FAULT_BUFFER); // this works for U3, but not
													// generalized
		double[] zCount = new double[griddedRegion.getNodeCount()];

		System.out.println("Looping over catalog");
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			for (ETAS_EqkRupture rup : catalog) {
				if (rup.getOriginTime() > maxOT)
					break;
				if (rup.getFSSIndex() >= 0) {
					// fault system rupture
					for (int s : rupSet.getSectionsIndicesForRup(rup.getFSSIndex())) {
						Map<Integer, Double> nodeFracts = polyManager.getNodeFractions(s);
						for (int n : nodeFracts.keySet()) {
							zCount[n] += nodeFracts.get(n);
						}
					}
				} else {
					zCount[griddedRegion.indexForLocation(rup.getHypocenterLocation())] += 1;
				}
			}
		}

		GriddedGeoDataSet triggerOnlyData = new GriddedGeoDataSet(griddedRegion, true); // true
																						// makes
																						// X
																						// latitude
		GriddedGeoDataSet triggerPlusTD_Data = new GriddedGeoDataSet(griddedRegion, true); // true
																							// makes
																							// X
																							// latitude
		GriddedGeoDataSet ratioData = new GriddedGeoDataSet(griddedRegion, true); // true
																					// makes
																					// X
																					// latitude

		System.out.println("Making Long Term Data");
		GriddedGeoDataSet longTermTD_data = FaultSysSolutionERF_Calc.calcParticipationProbInGriddedRegionFltMapped(erf,
				griddedRegion, 6.7, 10.0);

		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		System.out.println("Making Time Ind. Data");
		GriddedGeoDataSet timeInd_data = FaultSysSolutionERF_Calc.calcParticipationProbInGriddedRegionFltMapped(erf,
				griddedRegion, 6.7, 10.0);

		double numSimulations = 200000d; // 100k simulations OR 200k?
		for (int n = 0; n < zCount.length; n++) {
			double numTrig = zCount[n] / numSimulations;
			triggerOnlyData.set(n, Math.log10(numTrig));
			triggerPlusTD_Data.set(n, Math.log10(numTrig + longTermTD_data.get(n)));
			double value = Math.log10((numTrig) / longTermTD_data.get(n) + 1);
			if (Double.isNaN(value))
				value = 0.0;
			ratioData.set(n, value);
			longTermTD_data.set(n, Math.log10(longTermTD_data.get(n)));
			timeInd_data.set(n, Math.log10(timeInd_data.get(n)));
		}

		String fileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/U3_OperationalLossModelingPaper/Figures/Figure1and7_U3maps/hawired-full_td-no_ert-combined-gridded_nucl_m2.5/map_data.txt";
		double discr = 0.02;
		GriddedRegion reg = new GriddedRegion(new CaliforniaRegions.RELM_TESTING(), discr, GriddedRegion.ANCHOR_0_0);
		GriddedGeoDataSet triggerData = new GriddedGeoDataSet(reg, true); // true
																			// makes
																			// X
																			// latitude
		try {

			for (String line : Files.readLines(new File(fileName), Charset.defaultCharset())) {
				line = line.trim();
				String[] split = line.split("\t");
				double lat = Double.parseDouble(split[0]);
				double lon = Double.parseDouble(split[1]);
				double val = Double.parseDouble(split[2]);
				// if (split[2].equals("-Infinity"))
				// System.out.println(val);
				int index = reg.indexForLocation(new Location(lat, lon));
				triggerData.set(index, val);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		fileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/U3_OperationalLossModelingPaper/Figures/Figure1and7_U3maps/haywired-gridded-only_m2.5/map_data.txt";
		GriddedGeoDataSet triggerDataNoFaults = new GriddedGeoDataSet(reg, true); // true
																					// makes
																					// X
																					// latitude
		try {

			for (String line : Files.readLines(new File(fileName), Charset.defaultCharset())) {
				line = line.trim();
				String[] split = line.split("\t");
				double lat = Double.parseDouble(split[0]);
				double lon = Double.parseDouble(split[1]);
				double val = Double.parseDouble(split[2]);
				// if (split[2].equals("-Infinity"))
				// System.out.println(val);
				int index = reg.indexForLocation(new Location(lat, lon));
				triggerDataNoFaults.set(index, val);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		boolean includeTopo = false;

		System.out.println("Making Plots");
		try {
			CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
			double minValue = -6;
			double maxValue = -1;
			File dir = new File("EqkSpectraFig1_BackgroundImages");
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerOnlyData, griddedRegion, dir,
					"triggerOnlyData", true, cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerPlusTD_Data, griddedRegion, dir,
					"triggerPlusTD_Data", true, cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(longTermTD_data, griddedRegion, dir,
					"longTermTD_data", true, cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(timeInd_data, griddedRegion, dir, "timeInd_data",
					true, cpt, minValue, maxValue, includeTopo);

			CPT cpt_ratio = GMT_CPT_Files.UCERF3_ETAS_GAIN.instance();
			minValue = -1.2;
			maxValue = 1.2;
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(ratioData, griddedRegion, dir, "triggerRatioData",
					true, cpt_ratio, minValue, maxValue, includeTopo);
			for (int i = 0; i < ratioData.size(); i++)
				ratioData.set(i, 0.0);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(ratioData, griddedRegion, dir, "grayBackgroundData",
					true, cpt_ratio, minValue, maxValue, includeTopo);

			cpt = GMT_CPT_Files.UCERF3_ETAS_TRIGGER.instance();
			minValue = -5;
			maxValue = 1;
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerData, reg, dir, "triggerOnlyMag2p5", true,
					cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerDataNoFaults, reg, dir,
					"triggerOnlyNoFaultsMag2p5", true, cpt, minValue, maxValue, includeTopo);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * set full_TD true to make FULL_TD case; otherwise it's NO_ERT (filenames
	 * differe accordingly)
	 */
	private static void makeSRL_CoverImage() {

		System.out.println("running makeSRL_CoverImage");

		String fileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/SRL_U3_ShortPaper/Figures/Figure2/parkfield-full_td-no_ert-combined-1wk_m2.5/map_data.txt";
		double discr = 0.02;
		GriddedRegion reg = new GriddedRegion(new CaliforniaRegions.RELM_TESTING(), discr, GriddedRegion.ANCHOR_0_0);
		GriddedGeoDataSet triggerData = new GriddedGeoDataSet(reg, true); // true
																			// makes
																			// X
																			// latitude
		try {

			for (String line : Files.readLines(new File(fileName), Charset.defaultCharset())) {
				line = line.trim();
				String[] split = line.split("\t");
				double lat = Double.parseDouble(split[0]);
				double lon = Double.parseDouble(split[1]);
				double val = Double.parseDouble(split[2]);
				// if (split[2].equals("-Infinity"))
				// System.out.println(val);
				int index = reg.indexForLocation(new Location(lat, lon));
				triggerData.set(index, val);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		fileName = "/Users/field/Field_Other/CEA_WGCEP/UCERF3/SRL_U3_ShortPaper/Figures/Figure2/parkfield-gridded-only-1wk_m2.5/map_data.txt";

		GriddedGeoDataSet triggerDataNoFaults = new GriddedGeoDataSet(reg, true); // true
																					// makes
																					// X
																					// latitude
		try {

			for (String line : Files.readLines(new File(fileName), Charset.defaultCharset())) {
				line = line.trim();
				String[] split = line.split("\t");
				double lat = Double.parseDouble(split[0]);
				double lon = Double.parseDouble(split[1]);
				double val = Double.parseDouble(split[2]);
				// if (split[2].equals("-Infinity"))
				// System.out.println(val);
				int index = reg.indexForLocation(new Location(lat, lon));
				triggerDataNoFaults.set(index, val);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		boolean includeTopo = false;

		System.out.println("Making Plots");
		try {
			CPT cpt = GMT_CPT_Files.UCERF3_ETAS_TRIGGER.instance();
			double minValue = -6;
			double maxValue = 0;
			File dir = new File("SRL_CoverImage");

			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerData, reg, dir, "triggerOnlyMag2p5", true,
					cpt, minValue, maxValue, includeTopo);
			FaultSysSolutionERF_Calc.makeBackgroundImageForSCEC_VDO(triggerDataNoFaults, reg, dir,
					"triggerOnlyNoFaultsMag2p5", true, cpt, minValue, maxValue, includeTopo);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException, GMT_MapException, RuntimeException, DocumentException {

		if (args.length == 0 && new File("/Users/field/").exists()) {
			// now will run by default on your machine Ned
			// nedsAnalysis();
			// makeImagesForSciencePaperFig1(0);
			// makeImagesForSciencePaperFig1(1);
			// makeImagesForSciencePaperFig1(2);
			// makeImagesForEqkSpectraPaperFig1();
			makeSRL_CoverImage();
			System.exit(-1);
		}

		File mainDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		double minLoadMag = -1;

		boolean isCLI = args.length > 0;
		boolean forcePlot = isCLI && !Boolean.parseBoolean(System.getProperty("hardcoded", "false"));
		// boolean forcePlot = isCLI;
		System.out.println("Force plot: " + forcePlot);

		boolean plotMFDs = true || forcePlot;
		boolean plotExpectedComparison = false || forcePlot;
		boolean plotSectRates = true || forcePlot;
		boolean plotSectRatesOneWeek = true || forcePlot;
		boolean plotTemporalDecay = false || forcePlot;
		boolean plotDistanceDecay = false || forcePlot;
		boolean plotMaxMagHist = false || forcePlot;
		boolean plotGenerations = false || forcePlot;
		boolean plotGriddedNucleation = false || forcePlot;
		boolean writeTimeFromPrevSupra = false || forcePlot;
		boolean plotSectScatter = false || forcePlot;
		boolean plotGridScatter = false || forcePlot;
		boolean plotStationarity = false || forcePlot;
		boolean plotSubSectRecurrence = false || forcePlot;
		boolean plotCondDist = false || forcePlot;
		boolean writeCatsForViz = false || forcePlot;
		boolean plotScalesHazard = false && !forcePlot;
		boolean plotRegionOneWeek = false && !forcePlot;
		boolean plotMFDOneWeek = true && !forcePlot;

		// boolean plotMFDs = true;
		// boolean plotExpectedComparison = false;
		// boolean plotSectRates = true;
		// boolean plotTemporalDecay = true;
		// boolean plotDistanceDecay = true;
		// boolean plotMaxMagHist = true;
		// boolean plotGenerations = true;
		// boolean plotGriddedNucleation = true;
		// boolean writeTimeFromPrevSupra = true;
		// boolean plotSectScatter = true;
		// boolean plotGridScatter = true;
		// boolean plotStationarity = true;
		// boolean plotSubSectRecurrence = true;
		// boolean plotCondDist = false;
		// boolean writeCatsForViz = false;
		// boolean plotScalesHazard = false;
		// boolean plotRegionOneWeek = false;

		boolean useDefaultETASParamsIfMissing = true;
		boolean useActualDurations = true; // only applies to spontaneous runs

		int id_for_scenario = 0;

		// File resultDir = new File(mainDir, "2015_08_20-spontaneous-full_td");
		// File myOutput = new File(resultDir, "output_stats");
		// Preconditions.checkState(myOutput.exists() || myOutput.mkdir());
		// List<List<ETAS_EqkRupture>> myCatalogs =
		// ETAS_CatalogIO.loadCatalogs(new File(resultDir, "results.zip"));
		// for (int i=0; i<myCatalogs.size(); i++) {
		// long prevTime = Long.MIN_VALUE;
		// for (ETAS_EqkRupture rup : myCatalogs.get(i)) {
		// Preconditions.checkState(prevTime <= rup.getOriginTime());
		// prevTime = rup.getOriginTime();
		// }
		// }
		// plotNumEventsHistogram(myCatalogs, myOutput, "num_events_hist");
		// plotTotalMomentHistogram(myCatalogs, myOutput, "moment_hist");
		// System.exit(0);

		File fssFile = new File("dev/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		FaultSystemSolution fss = FaultSystemIO.loadSol(fssFile);

		// only for spontaneous
		boolean skipEmpty = true;
		double minDurationForInclusion = 0d;
		// double minDurationForInclusion = 0.5d;
		// double minDurationForInclusion = 990;

		List<File> resultsZipFiles = Lists.newArrayList();

		if (args.length == 0) {
			// manual run on the laptop

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/results_m4.bin"));
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_11-spontaneous-1000yr-no_ert-subSeisSupraNucl-gridSeisCorr/results_m4.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m5-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;
			//
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m5p5-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;
			//
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m6pt3_fss-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;
			//
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m6pt3_ptsrc-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14/results_descendents.bin"));
			// id_for_scenario = 9893;

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k/results_descendents_m4_preserve.bin"));
			//// resultsZipFiles.add(new File(mainDir,
			// "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k/results_m5_preserve.bin"));
			// id_for_scenario = 9893;

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_22-mojave_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined100k/results_descendents_m4_preserve.bin"));
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_22-mojave_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined100k/results_m5_preserve.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_24-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-noSpont-combined/results_descendents.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_06_15-haywired_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_descendents.bin"));
			// resultsZipFiles.add(new File(mainDir,
			// "2016_06_15-haywired_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_m5_preserve.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_06_15-haywired_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined/results_m5_preserve.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_30-san_jacinto_0_m4p8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_m5_preserve.bin"));
			resultsZipFiles.add(new File(mainDir,
					"2016_08_30-san_jacinto_0_m4p8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_descendents.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_31-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_m5_preserve.bin"));
			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_31-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_descendents.bin"));

			resultsZipFiles.add(new File(mainDir,
					"2016_08_31-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined-plus100kNoSpont/results_descendents_m4_preserve.bin"));
			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_31-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined-plus300kNoSpont/results_descendents_m4_preserve.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_24-bombay_beach_m4pt8-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-noSpont-combined/results_descendents.bin"));

			// names.add("30yr Full TD");
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_18-spontaneous-30yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/results_m4.bin"));
			// scenarios.add(null);

			// names.add("1000yr Full TD");
			// resultsZipFiles.add(new File(mainDir,
			// "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/results_m4.bin"));
			// scenarios.add(null);

			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_24-spontaneous-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_m5.bin"));

			// resultsZipFiles.add(new File(mainDir,
			// "2016_08_26-san_jacinto_0_m4p8-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined/results_descendents_union_m5.bin"));
		} else {
			// command line arguments

			for (String arg : args) {
				File resultFile = new File(arg);
				Preconditions.checkState(resultFile.exists()
						&& (resultFile.getName().endsWith(".bin") || resultFile.getName().endsWith(".zip")));

				if (resultFile.getParentFile().getName().startsWith("2016_02_19-mojave")) {
					System.out.println("Changing scenario ID");
					id_for_scenario = 9893;
				}

				resultsZipFiles.add(resultFile);
			}
		}

		for (int n = 0; n < resultsZipFiles.size(); n++) {
			File resultsFile = resultsZipFiles.get(n);
			File directory = resultsFile.getParentFile();
			System.out.println("Processing " + directory.getAbsolutePath());
			TestScenario scenario = detectScenario(directory);
			if (scenario != null)
				System.out.println("Detected scenario " + scenario.name());

			if (scenario != null && scenario.getFSS_Index() >= 0)
				scenario.updateMag(fss.getRupSet().getMagForRup(scenario.getFSS_Index()));

			boolean swarm = resultsFile.getParentFile().getName().contains("swarm");

			// parent ID for the trigger rupture
			int triggerParentID;
			if (swarm && resultsFile.getName().contains("descend"))
				// use input file as is
				triggerParentID = Integer.MAX_VALUE;
			else if (scenario == null)
				triggerParentID = -1;
			else
				triggerParentID = id_for_scenario;

			System.gc();

			RuptureSurface surf;
			if (scenario == null)
				surf = null;
			else if (scenario.getLocation() != null)
				surf = new PointSurface(scenario.getLocation());
			else
				surf = fss.getRupSet().getSurfaceForRupupture(scenario.getFSS_Index(), 1d, false);

			File parentDir = resultsFile.getParentFile();

			File outputDir = new File(resultsFile.getParentFile(), plotDirName);

			File metadataFile = new File(resultsFile.getParentFile(), "metadata.xml");
			System.out.println("Metadatafile: " + metadataFile.getAbsolutePath());
			Element metadataRootEl = null;
			ETAS_ParameterList params;
			if (metadataFile.exists()) {
				System.out.println("Loading ETAS params from metadata file: " + metadataFile.getAbsolutePath());
				Document doc = XMLUtils.loadDocument(metadataFile);
				metadataRootEl = doc.getRootElement();
				params = loadEtasParamsFromMetadata(metadataRootEl);
			} else if (useDefaultETASParamsIfMissing) {
				System.out.println("Using default ETAS params");
				params = new ETAS_ParameterList();
			} else {
				params = null;
			}

			Long ot;
			double inputDuration;
			if (metadataRootEl != null) {
				Element paramsEl = metadataRootEl.element(MPJ_ETAS_Simulator.OTHER_PARAMS_EL_NAME);
				ot = Long.parseLong(paramsEl.attributeValue("ot"));
				inputDuration = Double.parseDouble(paramsEl.attributeValue("duration"));
			} else {
				throw new IllegalStateException("No metadata found and don't want to assume the ot/duration");
				// System.out.println("WARNING: Assuming 1 year 2014");
				// ot =
				// Math.round((2014.0-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
				// // occurs at 2014
				// inputDuration = 1d;
			}
			double duration = inputDuration;
			if (useActualDurations && scenario == null && !swarm)
				duration = -1;

			String name;
			if (scenario == null)
				name = "";
			else
				name = scenario + " ";
			name += (int) inputDuration + "yr";
			if (params != null) {
				String parentName = resultsFile.getParentFile().getName().toLowerCase();
				boolean both = parentName.contains("full_td") && parentName.contains("no_ert");
				if (!both)
					name += " " + params.getU3ETAS_ProbModel();
			}

			if (scenario != null) {
				System.out.println("Scenario: " + scenario);
				System.out.println("\tMag: " + scenario.getMagnitude());
				System.out.println("\tFSS Index: " + scenario.getFSS_Index());
				System.out.println("\tHypo: " + scenario.getLocation());
			}

			if (!outputDir.exists()) {
				// see if old dirs exist;
				File oldDir = new File(parentDir, "output_stats");
				if (oldDir.exists()) {
					Preconditions.checkState(oldDir.renameTo(outputDir));
				} else {
					oldDir = new File(parentDir, "outputs_stats");
					if (oldDir.exists())
						Preconditions.checkState(oldDir.renameTo(outputDir));
				}
			}
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

			// load the catalogs
			System.out.println("Loading " + name + " from " + resultsFile.getAbsolutePath());
			Stopwatch timer = Stopwatch.createStarted();
			List<List<ETAS_EqkRupture>> catalogs = ETAS_CatalogIO.loadCatalogs(resultsFile, minLoadMag, true);
			timer.stop();
			long secs = timer.elapsed(TimeUnit.SECONDS);
			if (secs > 60)
				System.out.println("Catalog loading took " + (float) ((double) secs / 60d) + " minutes");
			else
				System.out.println("Catalog loading took " + secs + " seconds");

			// ETAS_CatalogIO.writeEventDataToFile(new
			// File("/tmp/catalog_0.txt"), catalogs.get(0));
			// System.exit(0);

			if (skipEmpty && scenario == null) {
				int skipped = 0;
				for (int i = catalogs.size(); --i >= 0;) {
					if (catalogs.get(i).isEmpty()) {
						catalogs.remove(i);
						skipped++;
					}
				}
				if (skipped > 0)
					System.out.println("Removed " + skipped + " empty catalogs.");
			}

			// now check actual duration
			MinMaxAveTracker durationTrack = new MinMaxAveTracker();
			int skippedDuration = 0;
			for (int i = catalogs.size(); --i >= 0;) {
				List<ETAS_EqkRupture> catalog = catalogs.get(i);
				double myDuration = 0;
				if (!catalog.isEmpty())
					myDuration = calcDurationYears(catalog);
				if (myDuration < minDurationForInclusion && scenario == null) {
					catalogs.remove(i);
					skippedDuration++;
				} else {
					durationTrack.addValue(myDuration);
				}
			}
			if (skippedDuration > 0)
				System.out.println("Removed " + skippedDuration + " catalgos that were too short");
			System.out.println("Actual duration: " + durationTrack);
			double meanDuration = durationTrack.getAverage();
			if (duration > 0 && DataUtils.getPercentDiff(duration, durationTrack.getMin()) > 2d)
				System.out.println("WARNING: at least 1 simulation doesn't match expected duration");

			List<List<ETAS_EqkRupture>> childrenCatalogs = Lists.newArrayList();
			if (triggerParentID >= 0 && triggerParentID < Integer.MAX_VALUE) {
				long numOrig = 0;
				long numChildren = 0;
				for (List<ETAS_EqkRupture> catalog : catalogs) {
					List<ETAS_EqkRupture> children = ETAS_SimAnalysisTools.getChildrenFromCatalog(catalog,
							triggerParentID);
					numOrig += catalog.size();
					numChildren += children.size();
					childrenCatalogs.add(children);
				}
				long removed = numOrig - numChildren;
				System.out.println("Removed " + removed + " spontaneous events (" + numOrig + " orig, " + numChildren
						+ " children)");
			} else {
				childrenCatalogs.addAll(catalogs);
			}

			MinMaxAveTracker childrenTrack = new MinMaxAveTracker();
			for (List<ETAS_EqkRupture> catalog : childrenCatalogs)
				childrenTrack.addValue(catalog.size());
			System.out.println("Children counts: " + childrenTrack);

			// // print out catalogs with most triggered moment
			// List<Integer> indexes = Lists.newArrayList();
			// for (int i=0; i<childrenCatalogs.size(); i++)
			// indexes.add(i);
			// List<Double> moments = calcTotalMoments(childrenCatalogs);
			// List<ComparablePairing<Double, Integer>> pairings =
			// ComparablePairing.build(moments, indexes);
			// Collections.sort(pairings);
			// Collections.reverse(pairings);
			// System.out.println("Index\tMoment\tMax M\t# Trig\t# Supra");
			// for (int i=0; i<20; i++) {
			// ComparablePairing<Double, Integer> pairing = pairings.get(i);
			// int index = pairing.getData();
			// List<ETAS_EqkRupture> catalog = childrenCatalogs.get(index);
			// double moment = pairing.getComparable();
			// double maxMag = 0d;
			// int numSupra = 0;
			// for (ETAS_EqkRupture rup : catalog) {
			// maxMag = Math.max(maxMag, rup.getMag());
			// if (rup.getFSSIndex() >= 0)
			// numSupra++;
			// }
			//
			// System.out.println(index+"\t"+(float)moment+"\t"+(float)maxMag+"\t"+catalog.size()+"\t"+numSupra);
			// }

			List<List<ETAS_EqkRupture>> primaryCatalogs = Lists.newArrayList();
			if (triggerParentID == Integer.MAX_VALUE) {
				// already filtered to descends
				for (List<ETAS_EqkRupture> catalog : catalogs)
					primaryCatalogs.add(ETAS_SimAnalysisTools.getByGeneration(catalog, 1));
			} else if (triggerParentID >= 0) {
				for (List<ETAS_EqkRupture> catalog : catalogs)
					primaryCatalogs.add(ETAS_SimAnalysisTools.getPrimaryAftershocks(catalog, triggerParentID));
			} else {
				for (List<ETAS_EqkRupture> catalog : catalogs)
					primaryCatalogs.add(ETAS_SimAnalysisTools.getByGeneration(catalog, 0));
			}

			String fullName;
			String fullFileName;
			String subsetName;
			String subsetFileName;
			if (triggerParentID >= 0) {
				fullName = "Children";
				fullFileName = "full_children";
				subsetName = "Primary";
				subsetFileName = "primary";
			} else {
				fullName = "All EQs";
				fullFileName = "all_eqs";
				subsetName = "Spontaneous";
				subsetFileName = "spontaneous";
			}

			FaultSystemSolutionERF erf = null;
			if (triggerParentID < 0 && (meanDuration >= 100d || catalogs.size() >= 1000)) {
				System.out.println("Creating ERF for comparisons");
				erf = new FaultSystemSolutionERF(fss);
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
				erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
				erf.getTimeSpan().setDuration(1d);
				erf.updateForecast();
			}

			boolean gridSeisCorr = resultsFile.getParentFile().getName().contains("gridSeisCorr")
					|| (params != null && params.getApplyGridSeisCorr());

			if (gridSeisCorr) {
				System.out.println("applying gridded seis comparison");
				double[] gridSeisCorrValsArray;
				try {
					gridSeisCorrValsArray = MatrixIO
							.doubleArrayFromFile(new File(ETAS_PrimaryEventSampler.defaultGriddedCorrFilename));
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				ETAS_Simulator.D = false;
				ETAS_Simulator.correctGriddedSeismicityRatesInERF(fss, false, gridSeisCorrValsArray);
			}

			if (plotMFDs) {
				System.out.println("Plotting MFDs");

				ArbIncrementalMagFreqDist[] subMFDs = plotMFD(childrenCatalogs, duration, erf, outputDir, name,
						fullFileName);
				if (triggerParentID >= 0)
					plotMFD(primaryCatalogs, duration, null, outputDir, subsetName + " " + name,
							subsetFileName + "_aftershocks");
				else
					plotMFD(primaryCatalogs, duration, erf, outputDir, subsetName + " " + name,
							subsetFileName + "_events");

				plotFractWithMagAbove(childrenCatalogs, subMFDs, scenario, outputDir, name,
						fullFileName + "_fract_above_mag");

				if (scenario != null) {
					double expNumForM2p5 = 0;
					if (inputDuration == 10)
						expNumForM2p5 = 0.1653;
					plotMagNum(childrenCatalogs, outputDir, name, "consolidated_aftershocks", scenario, expNumForM2p5,
							fss);
				} else if (swarm && triggerParentID == Integer.MAX_VALUE) {
					plotMagNum(childrenCatalogs, outputDir, name, "consolidated_aftershocks", scenario, -1, fss);
				}
			}

			if (plotExpectedComparison && triggerParentID >= 0) {
				System.out.println("Plotting Expected Comparison MFDs");
				plotExpectedSupraComparisonMFD(primaryCatalogs, outputDir, subsetName + " " + name,
						subsetFileName + "_supra_compare_to_expected");

				// now do first/last half
				int numCatalogs = primaryCatalogs.size();
				List<List<ETAS_EqkRupture>> firstHalfPrimary = primaryCatalogs.subList(0, numCatalogs / 2);
				List<List<ETAS_EqkRupture>> secondHalfPrimary = primaryCatalogs.subList(firstHalfPrimary.size(),
						primaryCatalogs.size());
				plotExpectedSupraComparisonMFD(firstHalfPrimary, outputDir, subsetName + " " + name,
						subsetFileName + "_supra_compare_to_expected_first" + firstHalfPrimary.size());
				plotExpectedSupraComparisonMFD(secondHalfPrimary, outputDir, subsetName + " " + name,
						subsetFileName + "_supra_compare_to_expected_last" + secondHalfPrimary.size());
			}

			if (plotSectRates) {
				// sub section partic/trigger rates
				System.out.println("Plotting Sub Sect Rates");
				double[] minMags = { 0, 6.7, 7.8 };
				plotSectRates(childrenCatalogs, duration, fss.getRupSet(), minMags, outputDir, "for All Aftershocks",
						fullFileName + "_sect");
				plotSectRates(primaryCatalogs, duration, fss.getRupSet(), minMags, outputDir, "for Primary Aftershocks",
						subsetFileName + "_sect");
			}

			if (plotSectRatesOneWeek) {
				// sub section partic/trigger rates, 1 week
				long maxOT = ot + ProbabilityModelsCalc.MILLISEC_PER_DAY * 7;
				System.out.println("Plotting Sub Sect Rates 1 Week");
				double[] minMags = { 0, 6.7, 7.8 };
				plotSectRates(childrenCatalogs, 0d, fss.getRupSet(), minMags, outputDir, "for All 1 Week",
						"one_week_" + fullFileName, maxOT);
				plotSectRates(primaryCatalogs, 0d, fss.getRupSet(), minMags, outputDir, "for Primary 1 Wekk",
						"one_week_" + subsetFileName, maxOT);
			}

			if (plotTemporalDecay && triggerParentID >= 0) {
				// temporal decay
				System.out.println("Plotting Temporal Decay");
				plotAftershockRateVsLogTimeHistForRup(primaryCatalogs, scenario, params, ot, outputDir,
						name + " " + subsetName, subsetFileName + "_temporal_decay");
				plotAftershockRateVsLogTimeHistForRup(childrenCatalogs, scenario, params, ot, outputDir, name,
						fullFileName + "_temporal_decay");
			}

			if (plotDistanceDecay && triggerParentID >= 0) {
				// dist decay trigger loc
				System.out.println("Plotting Trigger Loc Dist Decay");
				plotDistDecay(primaryCatalogs, params, null, outputDir, name + " Primary",
						subsetFileName + "_dist_decay_trigger");
				plotDistDecay(childrenCatalogs, params, null, outputDir, name, fullFileName + "_dist_decay_trigger");

				// dist decay rup surf
				if (scenario != null && scenario.getFSS_Index() >= 0) {
					System.out.println("Plotting Surface Dist Decay");
					Stopwatch watch = Stopwatch.createStarted();
					plotDistDecay(primaryCatalogs, params, surf, outputDir, name + " Primary",
							subsetFileName + "_dist_decay_surf");
					double mins = (watch.elapsed(TimeUnit.SECONDS)) / 60d;
					System.out.println("Primary surf dist decay took " + (float) mins + " mins");
					watch.reset();
					watch.start();
					plotDistDecay(childrenCatalogs, params, surf, outputDir, name, fullFileName + "_dist_decay_surf");
					watch.stop();
					mins = (watch.elapsed(TimeUnit.SECONDS)) / 60d;
					System.out.println("Full surf dist decay took " + (float) mins + " mins");
				}
			}

			if (plotMaxMagHist) {
				System.out.println("Plotting max mag hist");
				plotMaxTriggeredMagHist(childrenCatalogs, primaryCatalogs, scenario, outputDir, name, "max_mag_hist");
			}

			if (plotGenerations) {
				System.out.println("Plotting generations");
				plotNumEventsPerGeneration(childrenCatalogs, outputDir, name, fullFileName + "_generations");
			}

			if (plotGriddedNucleation) {
				System.out.println("Plotting gridded nucleation");
				double[] mags = { 2.5, 6.7, 7.8 };
				plotCubeNucleationRates(childrenCatalogs, duration, outputDir, name, fullFileName + "_gridded_nucl",
						mags);
				plotCubeNucleationRates(primaryCatalogs, duration, outputDir, name, subsetFileName + "_gridded_nucl",
						mags);
				if (scenario == null && (duration > 1d || duration < 0)) {
					List<List<ETAS_EqkRupture>> histCats = getOnlyAftershocksFromHistorical(childrenCatalogs);
					plotCubeNucleationRates(histCats, duration, outputDir, name,
							fullFileName + "_gridded_nucl_historical", mags);
				}
			}

			if (plotSectScatter && erf != null) {
				System.out.println("Plotting section participation scatter");
				File fullResultsFile = new File(resultsFile.getParentFile(), "results.bin");
				if (fullResultsFile.exists() && resultsFile.getName().endsWith(".bin")
						&& resultsFile.getName().startsWith("results_m")) {
					System.out.println("Iterating over full results from " + fullResultsFile.getAbsolutePath());
					plotSectParticScatter(ETAS_CatalogIO.getBinaryCatalogsIterable(fullResultsFile, 0d), duration, erf,
							outputDir);
				} else {
					plotSectParticScatter(catalogs, duration, erf, outputDir);
				}
			}

			if (plotGridScatter && erf != null) {
				System.out.println("Plotting gridded nucleation scatter");
				plotGriddedNucleationScatter(catalogs, duration, erf, outputDir);
			}

			if (plotStationarity && (duration > 1d || duration < 0) && triggerParentID < 0 && catalogs.size() >= 500
					&& !swarm) {
				System.out.println("Plotting stationarity");
				plotStationarity(catalogs, duration, outputDir);
			}

			if (plotSubSectRecurrence && (duration > 1d || duration < 0) && triggerParentID < 0 && !swarm) {
				System.out.println("Plotting sub section recurrence");
				int[] sectIndexes = { 1922, // parkfield 2
						1850 // Mojave S 13
				};
				for (int sectIndex : sectIndexes)
					plotSubSectRecurrenceHist(catalogs, fss.getRupSet(), sectIndex, outputDir, Double.NaN);
			}

			if (plotCondDist && (duration > 1d || duration < 0) && triggerParentID < 0 && !swarm) {
				System.out.println("Plotting conditional hypocenter distribution");
				plotConditionalHypocenterDist(catalogs, outputDir, fss.getRupSet());
			}

			if (writeCatsForViz) {
				System.out.println("Writing catalogs for vizualisation in SCEC-VDO");
				writeCatalogsForViz(childrenCatalogs, scenario, new File(parentDir, catsDirName), 5);
			}

			if (scenario == null && writeTimeFromPrevSupra && !swarm) {
				System.out.println("Plotting time since last supra");
				writeTimeFromPrevSupraHist(catalogs, outputDir);
			}

			if (scenario != null && plotScalesHazard || plotRegionOneWeek) {
				erf = new FaultSystemSolutionERF(fss);
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
				erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
				erf.getTimeSpan().setDuration(1d);
				erf.updateForecast();
				boolean rates = false;
				boolean subSects = false;

				if (plotScalesHazard) {
					File scalesDir = new File(outputDir, "scales_of_hazard_change");
					Preconditions.checkState(scalesDir.exists() || scalesDir.mkdir());
					if (subSects) {
						scalesDir = new File(scalesDir, "sub_sects");
						Preconditions.checkState(scalesDir.exists() || scalesDir.mkdir());
					}
					plotScalesOfHazardChange(childrenCatalogs, catalogs, scenario, ot, erf, scalesDir, name,
							inputDuration, rates, subSects);
				}
				if (scenario == TestScenario.HAYWIRED_M7 || scenario == TestScenario.MOJAVE_M7 && plotRegionOneWeek) {
					plotRegionalMPDs(catalogs, scenario, ot, erf, outputDir, name, true);
					plotRegionalMPDs(catalogs, scenario, ot, erf, outputDir, name, false);
				}
			}

			if (plotMFDOneWeek) {
				long maxOT = ot + ProbabilityModelsCalc.MILLISEC_PER_DAY * 7;
				if (scenario != null) {
					if (scenario.getMagnitude() < 6) {
						mfdMinY = 1e-6;
						mfdMaxY = 1e2;
					}
					plotMagNum(childrenCatalogs, outputDir, "1 Week", "one_week_consolidated_aftershocks", scenario,
							0.058, fss, maxOT);
				} else if (swarm && triggerParentID == Integer.MAX_VALUE) {
					mfdMinY = 1e-6;
					mfdMaxY = 1e2;
					plotMagNum(childrenCatalogs, outputDir, "1 Week", "one_week_consolidated_aftershocks", null, -1,
							fss, maxOT);
				}
			}

			writeHTML(parentDir, scenario, name, params, catalogs, inputDuration, durationTrack);
		}
	}

	public static double calcDurationYears(List<ETAS_EqkRupture> catalog) {
		if (catalog.isEmpty())
			return 0d;
		return calcEventTimeYears(catalog, catalog.get(catalog.size() - 1));
	}

	private static double calcEventTimeYears(List<ETAS_EqkRupture> catalog, ETAS_EqkRupture rup) {
		long durationMillis = rup.getOriginTime() - catalog.get(0).getOriginTime();
		double myDuration = (double) durationMillis / MILLIS_PER_YEAR;
		return myDuration;
	}

	private static final int html_w_px = 800;

	private static void writeHTML(File outputDir, TestScenario scenario, String scenName, ETAS_ParameterList params,
			List<List<ETAS_EqkRupture>> catalogs, double inputDuration, MinMaxAveTracker durationTrack)
			throws IOException {
		System.out.println("Writing HTML");

		FileWriter fw = new FileWriter(new File(outputDir, "HEADER.html"));

		fw.write("<h1 style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal;\">" + scenName
				+ "</h1>\n");
		fw.write("<br>\n");
		writeMetadataHTML(fw, scenario, params, catalogs, inputDuration, durationTrack);

		File plotDir = new File(outputDir, plotDirName);
		if (plotDir.exists()) {
			fw.write("<b>Various plots can be found in the " + plotDirName + " directory below</b><br>\n");

			// find minMag
			double minMag = Double.POSITIVE_INFINITY;
			for (List<ETAS_EqkRupture> catalog : catalogs)
				for (ETAS_EqkRupture rup : catalog)
					minMag = Math.min(minMag, rup.getMag());
			// round minMag
			minMag = 0.1 * Math.round((minMag - 0.01) * 10d);

			writePlotHTML(plotDir, scenName, minMag);
		}

		fw.write("</p>\n");

		fw.close();
	}

	private static void writePlotHTML(File plotDir, String scenarioName, double minMag) throws IOException {
		FileWriter fw = new FileWriter(new File(plotDir, "HEADER.html"));

		fw.write("<h1 style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal;\">" + scenarioName
				+ " Plots</h1>\n");
		fw.write("<br>\n");
		fw.write("<p style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal; width:" + html_w_px
				+ ";\">\n");

		fw.write("<b>Plots are divided into 3 categories:</b><br>\n");
		fw.write("<b>full_children_*:</b> Plots that include all generations of child events<br>\n");
		fw.write("<b>primary_*:</b> Plots that only consider primary aftershocks<br>\n");
		fw.write("<b>(other):</b> Plots where both are included or separation is not applicable<br>\n");

		fw.write("<br>\n");
		fw.write("<b>MFD Plots:</b><br>\n");
		fw.write("<b>*_mfd_cumulative.*:</b> Cumulative magnitude frequency distributious across all catalogs<br>\n");
		fw.write("<b>*_mfd_incremental.*:</b> Incremental magnitude frequency distributious across all catalogs<br>\n");
		fw.write("<br>\n");
		fw.write("<b>Decay Plots:</b><br>\n");
		fw.write("<b>*_dist_decay_trigger.*:</b> Distance decay of each child rupture from the trigger location on "
				+ "the parent rupture<br>\n");
		fw.write("<b>*_temporal_decay.*:</b> Temporal decay of each child rupture relative to its parent<br>\n");
		fw.write("<br>\n");
		fw.write("<b>Map Based Plots:</b><br>\n");
		fw.write("<b>*_sect_partic.*:</b> Map view of supra-seismogenic fault section participation rates<br>\n");
		fw.write("<b>*_sect_trigger.*:</b> Map view of supra-seismogenic fault section trigger rates<br>\n");
		fw.write("<b>*_gridded_nucl_m*.*:</b> Map view of gridded nucleation rates<br>\n");
		fw.write("<br>\n");
		fw.write("<b>Other Misc Plots:</b><br>\n");
		fw.write("<b>max_mag_hist.*:</b> Histogram of maximum magnitude triggered rupture across all catalogs<br>\n");
		fw.write("<b>full_children_generations.*:</b> Number of aftershocks of each generation<br>\n");

		if (minMag >= 3d) {
			fw.write("<br>\n");
			fw.write("<b>NOTE: due to the number or aftershocks, only M&ge;" + (float) minMag
					+ " ruptures are considered in these plots</b><br>\n");
		}

		fw.write("</p>\n");

		fw.close();
	}

	private static void writeMetadataHTML(FileWriter fw, TestScenario scenario, ETAS_ParameterList params,
			List<List<ETAS_EqkRupture>> catalogs, double inputDuration, MinMaxAveTracker durationTrack)
			throws IOException {
		fw.write("<p style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal; width:" + html_w_px
				+ ";\">\n");
		if (scenario != null) {
			fw.write("<h2>Scenario Information</h2>\n");
			fw.write("<b>Name:</b> " + scenario.name() + "<br>\n");
			fw.write("<b>Magnitude:</b> " + scenario.getMagnitude() + "<br>\n");
			fw.write("<b>Supra-seismogenic? </b> " + (scenario.getFSS_Index() >= 0) + "<br>\n");
			fw.write("<br>\n");
		}

		fw.write("<h2>Simulation Information</h2>\n");
		fw.write("<b>Num Catalogs:</b> " + catalogs.size() + "<br>\n");
		fw.write("<b>Simulation Input Duration:</b> " + (float) inputDuration + " years<br>\n");
		fw.write("<b>Actual Duration:</b> min=" + (float) durationTrack.getMin() + ", max="
				+ (float) durationTrack.getMax() + ", avg=" + (float) durationTrack.getAverage() + " years<br>\n");
		fw.write("<br>\n");
		if (params != null) {
			fw.write("<h3>ETAS Parameters</h3>\n");
			for (Parameter<?> param : params)
				fw.write("<b>" + param.getName() + ":</b> " + param.getValue() + "<br>\n");
			fw.write("<br>\n");
		}
		fw.write("</p>\n");
	}

	public static void writePaperHTML(File scenariosFile, File inputDir, File outputDir)
			throws IOException, DocumentException {
		String resultFileName = "results_descendents.bin";

		String urlBase = "http://opensha.usc.edu/ftp/UCERF3-ETAS/scenarios";
		Table<TestScenario, U3ETAS_ProbabilityModelOptions, String> dirNameMap = HashBasedTable.create();

		for (String dirName : Files.readLines(scenariosFile, Charset.defaultCharset())) {
			dirName = dirName.trim();
			if (dirName.startsWith("#") || dirName.isEmpty())
				continue;
			System.out.println("Processing " + dirName);
			File dir = new File(inputDir, dirName);
			Preconditions.checkState(dir.exists(), "Directory not found: " + dir.getAbsolutePath());
			File resultsFile = new File(dir, resultFileName);
			Preconditions.checkState(resultsFile.exists(), "Results file not found: " + resultsFile.getAbsolutePath());
			File origPlotDir = new File(dir, "plots");
			Preconditions.checkArgument(origPlotDir.exists(), "Plot dir not found: " + origPlotDir.getAbsolutePath());

			File myOutput = new File(outputDir, dirName);
			Preconditions.checkArgument(myOutput.exists() || myOutput.mkdir());

			TestScenario scenario = detectScenario(dir);

			File metadataFile = new File(resultsFile.getParentFile(), "metadata.xml");
			System.out.println("Metadatafile: " + metadataFile.getAbsolutePath());
			Element metadataRootEl = null;
			ETAS_ParameterList params;
			if (metadataFile.exists()) {
				System.out.println("Loading ETAS params from metadata file: " + metadataFile.getAbsolutePath());
				Document doc = XMLUtils.loadDocument(metadataFile);
				metadataRootEl = doc.getRootElement();
				params = loadEtasParamsFromMetadata(metadataRootEl);
			} else {
				// params = null;
				throw new IllegalStateException("couldn't load params");
			}
			dirNameMap.put(scenario, params.getU3ETAS_ProbModel(), dirName);

			Long ot;
			double inputDuration;
			if (metadataRootEl != null) {
				Element paramsEl = metadataRootEl.element(MPJ_ETAS_Simulator.OTHER_PARAMS_EL_NAME);
				ot = Long.parseLong(paramsEl.attributeValue("ot"));
				inputDuration = Double.parseDouble(paramsEl.attributeValue("duration"));
			} else {
				System.out.println("WARNING: Assuming 1 year 2014");
				ot = Math.round((2014.0 - 1970.0) * ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs
																								// at
																								// 2014
				inputDuration = 1d;
			}

			List<List<ETAS_EqkRupture>> catalogs = ETAS_CatalogIO.loadCatalogsBinary(resultsFile, 4d);
			MinMaxAveTracker durationTrack = new MinMaxAveTracker();
			for (List<ETAS_EqkRupture> catalog : catalogs) {
				if (catalog.isEmpty())
					durationTrack.addValue(0d);
				else
					durationTrack.addValue(calcDurationYears(catalog));
			}

			String name;
			if (scenario == null)
				name = "";
			else
				name = scenario + " ";
			name += (int) inputDuration + "yr";
			if (params != null)
				name += " " + params.getU3ETAS_ProbModel();

			writePaperHTML(origPlotDir, myOutput, scenario, name, params, catalogs, inputDuration, durationTrack);
		}

		// now write table
		List<TestScenario> scenarios = Lists.newArrayList(dirNameMap.rowKeySet());
		Collections.sort(scenarios, new Comparator<TestScenario>() {

			@Override
			public int compare(TestScenario o1, TestScenario o2) {
				return o1.toString().compareTo(o2.toString());
			}
		});

		FileWriter fw = new FileWriter(new File(outputDir, "index.html"));

		for (TestScenario scenario : scenarios) {
			String line = scenario.toString() + ":";
			if (dirNameMap.contains(scenario, U3ETAS_ProbabilityModelOptions.FULL_TD)) {
				String link = urlBase + "/" + dirNameMap.get(scenario, U3ETAS_ProbabilityModelOptions.FULL_TD);
				line += " <a href=\"" + link + "\">Full TD</a>";
			}
			if (dirNameMap.contains(scenario, U3ETAS_ProbabilityModelOptions.NO_ERT)) {
				String link = urlBase + "/" + dirNameMap.get(scenario, U3ETAS_ProbabilityModelOptions.NO_ERT);
				line += " <a href=\"" + link + "\">No ERT</a>";
			}
			fw.write(line + "<br>\n");
		}

		fw.close();
	}

	private static void copyFiles(File fromDir, File toDir, String pattern) throws IOException {
		for (File file : fromDir.listFiles()) {
			if (file.getName().contains(pattern))
				Files.copy(file, new File(toDir, file.getName()));
		}
	}

	private static void writePaperHTML(File origPlotDir, File outputDir, TestScenario scenario, String scenName,
			ETAS_ParameterList params, List<List<ETAS_EqkRupture>> catalogs, double inputDuration,
			MinMaxAveTracker durationTrack) throws IOException {
		System.out.println("Writing HTML");

		FileWriter headerFW = new FileWriter(new File(outputDir, "HEADER.html"));
		FileWriter footerFW = new FileWriter(new File(outputDir, "README.html"));

		headerFW.write("<h1 style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal;\">" + scenName
				+ "</h1>\n");
		headerFW.write("<br>\n");

		writeMetadataHTML(footerFW, scenario, params, catalogs, inputDuration, durationTrack);
		footerFW.close();

		headerFW.write("<p style=\"font-family:'HelveticaNeue-Light', sans-serif; font-weight:normal; width:"
				+ html_w_px + ";\">\n");

		headerFW.write("<b>Plots are divided into 3 categories:</b><br>\n");
		headerFW.write("<b>full_children_*:</b> Plots that include all generations of child events<br>\n");
		headerFW.write("<b>primary_*:</b> Plots that only consider primary aftershocks<br>\n");
		headerFW.write("<b>(other):</b> Plots where both are included or separation is not applicable<br>\n");

		headerFW.write("<br>\n");
		headerFW.write("<b>MFD Plots:</b><br>\n");
		// headerFW.write("<b>*_mfd_cumulative.*:</b> Cumulative magnitude
		// frequency distributious across all catalogs<br>\n");
		// headerFW.write("<b>*_mfd_incremental.*:</b> Incremental magnitude
		// frequency distributious across all catalogs<br>\n");
		copyFiles(origPlotDir, outputDir, "consolidated_aftershocks_mag_num_cumulative");
		headerFW.write(
				"<b>consolidated_aftershocks_mag_num_cumulative.*:</b> Cumulative magnitude frequency distributious across all catalogs<br>\n");
		headerFW.write(
				"<b>consolidated_aftershocks_mag_num_incremental.*:</b> Incremental magnitude frequency distributious across all catalogs<br>\n");
		headerFW.write("<br>\n");
		headerFW.write("<b>Decay Plots:</b><br>\n");
		copyFiles(origPlotDir, outputDir, "_dist_decay_trigger");
		headerFW.write(
				"<b>*_dist_decay_trigger.*:</b> Distance decay of each child rupture from the trigger location on "
						+ "the parent rupture<br>\n");
		copyFiles(origPlotDir, outputDir, "_temporal_decay");
		headerFW.write("<b>*_temporal_decay.*:</b> Temporal decay of each child rupture relative to its parent<br>\n");
		headerFW.write("<br>\n");
		headerFW.write("<b>Map Based Plots:</b><br>\n");
		copyFiles(origPlotDir, outputDir, "_sect_partic");
		headerFW.write("<b>*_sect_partic.*:</b> Map view of supra-seismogenic fault section participation rates<br>\n");
		copyFiles(origPlotDir, outputDir, "_sect_trigger");
		headerFW.write("<b>*_sect_trigger.*:</b> Map view of supra-seismogenic fault section trigger rates<br>\n");
		copyFiles(origPlotDir, outputDir, "_gridded_nucl_");
		headerFW.write("<b>*_gridded_nucl_m*.*:</b> Map view of gridded nucleation rates<br>\n");
		// headerFW.write("<br>\n");
		// headerFW.write("<b>Other Misc Plots:</b><br>\n");
		// headerFW.write("<b>max_mag_hist.*:</b> Histogram of maximum magnitude
		// triggered rupture across all catalogs<br>\n");
		// headerFW.write("<b>full_children_generations.*:</b> Number of
		// aftershocks of each generation<br>\n");

		// if (minMag >= 3d) {
		// headerFW.write("<br>\n");
		// headerFW.write("<b>NOTE: due to the number or aftershocks, only
		// M&ge;"
		// +(float)minMag+" ruptures are considered in these plots</b><br>\n");
		// }

		headerFW.write("</p>\n");

		headerFW.close();
	}

}
