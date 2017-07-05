package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator.TestScenario;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.kevin.ucerf3.etas.MPJ_ETAS_Simulator;

public class ETAS_SciPaperFigures {

	static double mfdMinMag = 2.55;
	static double mfdDelta = 0.1;
	static int mfdNumMag = 61;
	public static double mfdMinY = 1e-6;
	public static double mfdMaxY = 1e2;
	
	public static void main(String[] args) throws IOException, DocumentException {
		File mainDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		
		File fssFile = new File("dev/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		FaultSystemSolution fss = FaultSystemIO.loadSol(fssFile);
		
		File outputDir = new File(mainDir, "science_paper");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		long otScenario = 1325419200000l;
		long otScenarioOneWeek = otScenario + ProbabilityModelsCalc.MILLISEC_PER_DAY*7;
		long otBombay2016 = 1474990200000l;
		double inputDuration = 10d;
		
		// input files
		// Bombay Beach M4.8
		File bbFullTD_desc = new File(mainDir,	// 400K
				"2016_08_31-bombay_beach_m4pt8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined-plus300kNoSpont/results_descendents.bin");
		File bbNoERT_desc = new File(mainDir,	// 400K
				"2016_10_27-bombay_beach_m4pt8-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-noSpont-combined/results_descendents.bin");
		// Bombay Beach 2016 Swarm
		File bb2016FullTD_desc = new File(mainDir,	// 400K
				"2016_10_25-2016_bombay_swarm-24yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-noSpont-combined/results.bin");
		File bb2016NoERT_desc = new File(mainDir,	// 400K
				"2016_10_27-2016_bombay_swarm-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-noSpont-combined/results.bin");
		// San Jacinto M4.8
		File sjFullTD_desc = new File(mainDir,	// 400K
				"2016_11_02-san_jacinto_0_m4p8-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-noSpont-combined/results_descendents.bin");
		File sjNoERT_desc = new File(mainDir,	// 400K
				"2016_11_02-san_jacinto_0_m4p8-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-noSpont-combined/results_descendents.bin");
		// Mojave M7
		File mojaveFullTD_desc = new File(mainDir,	// 100K
				"2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k/results_descendents_m4_preserve.bin");
		File mojaveFullTD_all = new File(mainDir,	// 100K
				"2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined100k/results_m5_preserve.bin");
		File mojaveNoERT_desc = new File(mainDir,	// 100K
				"2016_02_22-mojave_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined100k/results_descendents_m4_preserve.bin");
		File mojaveNoERT_all = new File(mainDir,	// 100K
				"2016_02_22-mojave_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined100k/results_m5_preserve.bin");
		// Haywired
		File haywiredFullTD_desc = new File(mainDir,	// 100K
				"2016_06_15-haywired_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_descendents_m4_preserve.bin");
		File haywiredFullTD_all = new File(mainDir,	// 100K
				"2016_06_15-haywired_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14-combined/results_m5_preserve.bin");
		File haywiredNoERT_desc = new File(mainDir,	// 100K
				"2016_06_15-haywired_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined/results_descendents_m4_preserve.bin");
		File haywiredNoERT_all = new File(mainDir,	// 100K
				"2016_06_15-haywired_m7-10yr-no_ert-subSeisSupraNucl-gridSeisCorr-combined/results_m5_preserve.bin");
		
		boolean doFig2 = true; // Bombay/SJ MFD
		boolean doFig3 = true; // Mojave/Haywired MFD
		boolean doFig4 = true; // Mojave/Haywired Region MFD
		boolean doFig5 = true; // Scales of Hazard
		
		FaultSystemSolutionERF erf = null;
		if (doFig4 || doFig5) {
			erf = new FaultSystemSolutionERF(fss);
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
			erf.getTimeSpan().setDuration(1d);
			erf.updateForecast();
		}
		
		// Figure 2 - One Week MFDs, 4.8 Bombay and SJ
		double expNumForM2p5 = 0.058;
		if (doFig2) {
			System.out.println("Figure 2");
			File fig2Dir = new File(outputDir, "figure_2");
			Preconditions.checkState(fig2Dir.exists() || fig2Dir.mkdir());
			
			List<List<ETAS_EqkRupture>> bbFullTD = ETAS_CatalogIO.loadCatalogsBinary(bbFullTD_desc);
			List<List<ETAS_EqkRupture>> bbNoERT = ETAS_CatalogIO.loadCatalogsBinary(bbNoERT_desc);
			plotMagNumWithConf(bbFullTD, bbNoERT, fig2Dir, "Bombay Beach M4.8", "bombay_beach_one_week_mfd", TestScenario.BOMBAY_BEACH_M4pt8,
					expNumForM2p5, fss, otScenarioOneWeek);
			
			bbFullTD = null;
			bbNoERT = null;
			System.gc();
			
			List<List<ETAS_EqkRupture>> sjFullTD = ETAS_CatalogIO.loadCatalogsBinary(sjFullTD_desc);
			List<List<ETAS_EqkRupture>> sjNoERT = ETAS_CatalogIO.loadCatalogsBinary(sjNoERT_desc);
			plotMagNumWithConf(sjFullTD, sjNoERT, fig2Dir, "San Jacinto M4.8", "san_jacinto_one_week_mfd", TestScenario.SAN_JACINTO_0_M4p8,
					expNumForM2p5, fss, otScenarioOneWeek);
		}
		
		// Figure 3 - One Week MFDs, Mojave M7 andd Haywired
		if (doFig3) {
			System.out.println("Figure 3");
			File fig3Dir = new File(outputDir, "figure_3");
			Preconditions.checkState(fig3Dir.exists() || fig3Dir.mkdir());
			
			List<List<ETAS_EqkRupture>> mojaveFullTD = ETAS_CatalogIO.loadCatalogsBinary(mojaveFullTD_desc);
			List<List<ETAS_EqkRupture>> mojaveNoERT = ETAS_CatalogIO.loadCatalogsBinary(mojaveNoERT_desc);
			plotMagNumWithConf(mojaveFullTD, mojaveNoERT, fig3Dir, "Mojave M7", "mojave_one_week_mfd", TestScenario.MOJAVE_M7,
					expNumForM2p5, fss, otScenarioOneWeek);
			
			mojaveFullTD = null;
			mojaveNoERT = null;
			System.gc();
			
			List<List<ETAS_EqkRupture>> haywiredFullTD = ETAS_CatalogIO.loadCatalogsBinary(haywiredFullTD_desc);
			List<List<ETAS_EqkRupture>> haywiredNoERT = ETAS_CatalogIO.loadCatalogsBinary(haywiredNoERT_desc);
			plotMagNumWithConf(haywiredFullTD, haywiredNoERT, fig3Dir, "Haywired M7", "haywired_one_week_mfd", TestScenario.HAYWIRED_M7,
					expNumForM2p5, fss, otScenarioOneWeek);
		}
		
		// Figure 4 - One Week Regional MFDs, Mojave M7 and Haywired
		if (doFig4) {
			System.out.println("Figure 4");
			File fig4Dir = new File(outputDir, "figure_4");
			Preconditions.checkState(fig4Dir.exists() || fig4Dir.mkdir());
			
			List<List<ETAS_EqkRupture>> mojaveFullTD = ETAS_CatalogIO.loadCatalogsBinary(mojaveFullTD_all);
			List<List<ETAS_EqkRupture>> mojaveNoERT = ETAS_CatalogIO.loadCatalogsBinary(mojaveNoERT_all);
			ETAS_MultiSimAnalysisTools.plotRegionalMPDs(mojaveFullTD, mojaveNoERT, TestScenario.MOJAVE_M7,
					new CaliforniaRegions.LA_BOX(), otScenario, erf, fig4Dir, "Mojave M7", "mojave_one_week_mfd_la_box", true);
			
			mojaveFullTD = null;
			mojaveNoERT = null;
			System.gc();
			
			List<List<ETAS_EqkRupture>> haywiredFullTD = ETAS_CatalogIO.loadCatalogsBinary(haywiredFullTD_all);
			List<List<ETAS_EqkRupture>> haywiredNoERT = ETAS_CatalogIO.loadCatalogsBinary(haywiredNoERT_all);
			ETAS_MultiSimAnalysisTools.plotRegionalMPDs(haywiredFullTD, haywiredNoERT, TestScenario.HAYWIRED_M7,
					new CaliforniaRegions.SF_BOX(), otScenario, erf, fig4Dir, "Haywired M7", "haywired_one_week_mfd_sf_box", true);
		}
		
		// Figure 5 - Scales of Hazard Change
		if (doFig5) {
			System.out.println("Figure 5");
			File fig5Dir = new File(outputDir, "figure_5");
			Preconditions.checkState(fig5Dir.exists() || fig5Dir.mkdir());
			
			boolean rates = false;
			boolean subSects = false;
			double[] mags = { 6.7 };
			
			HashSet<Integer> sects = new HashSet<Integer>();
			sects.add(295); // coachella
			
			List<List<ETAS_EqkRupture>> bbFullTD = ETAS_CatalogIO.loadCatalogsBinary(bbFullTD_desc);
			List<List<ETAS_EqkRupture>> bbNoERT = ETAS_CatalogIO.loadCatalogsBinary(bbNoERT_desc);
			File subDir = new File(fig5Dir, "bombay_4p8");
			Preconditions.checkState(subDir.exists() || subDir.mkdir());
			ETAS_MultiSimAnalysisTools.plotScalesOfHazardChange(bbFullTD, null, bbNoERT, TestScenario.BOMBAY_BEACH_M4pt8,
					otScenario, erf, subDir, "Bombay Beach M4.8", inputDuration, rates, subSects, sects, mags);
			
			bbFullTD = null;
			bbNoERT = null;
			System.gc();
			
			List<List<ETAS_EqkRupture>> bb2016FullTD = ETAS_CatalogIO.loadCatalogsBinary(bb2016FullTD_desc);
			List<List<ETAS_EqkRupture>> bb2016NoERT = ETAS_CatalogIO.loadCatalogsBinary(bb2016NoERT_desc);
			subDir = new File(fig5Dir, "bombay_2016");
			Preconditions.checkState(subDir.exists() || subDir.mkdir());
			// Scenario below is only used for finding nearby sects, so 4.8 scenario is fine. Mag is ignored
			ETAS_MultiSimAnalysisTools.plotScalesOfHazardChange(bb2016FullTD, null, bb2016NoERT, TestScenario.BOMBAY_BEACH_M4pt8,
					otBombay2016, erf, subDir, "Bombay Beach 2016 Swarm", inputDuration, rates, subSects, sects, mags);
		}
	}
	
	public static void plotMagNumWithConf(List<List<ETAS_EqkRupture>> fullTDCatalogs, List<List<ETAS_EqkRupture>> noERTCatalogs,
			File outputDir, String name, String prefix, TestScenario scenario, double expNumForM2p5, FaultSystemSolution fss,
			long maxOT) 	throws IOException {
		double minMag = mfdMinMag;
		int numMag = mfdNumMag;
		
		// see if we need to adjust
		int numToTrim = ETAS_MultiSimAnalysisTools.calcNumMagToTrim(fullTDCatalogs);
		if (numToTrim > 0)
			System.out.println("Trimming "+numToTrim+" MFD bins");
		for (int i=0; i<numToTrim; i++) {
			minMag += mfdDelta;
			numMag--;
		}
		
		DiscretizedFunc fullTDmean = null;
		DiscretizedFunc noERTmean = null;
		
		for (int n=0; n<2; n++) {
			List<List<ETAS_EqkRupture>> catalogs;
			if (n == 0)
				catalogs = fullTDCatalogs;
			else
				catalogs = noERTCatalogs;
			
			ArbIncrementalMagFreqDist[] subMagNums = new ArbIncrementalMagFreqDist[catalogs.size()];
			EvenlyDiscretizedFunc[] cmlSubMagNums = new EvenlyDiscretizedFunc[catalogs.size()];
			for (int i=0; i<catalogs.size(); i++)
				subMagNums[i] = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);
			ArbIncrementalMagFreqDist primaryMFD = new ArbIncrementalMagFreqDist(minMag, numMag, mfdDelta);
			
			double primaryNumEach = 1d/catalogs.size();
			
			for (int i=0; i<catalogs.size(); i++) {
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
			
			FractileCurveCalculator fractCalc = ETAS_MultiSimAnalysisTools.getFractileCalc(cmlSubMagNums);
			
			if (n == 0)
				fullTDmean = (DiscretizedFunc)fractCalc.getMeanCurve();
			else
				noERTmean = (DiscretizedFunc)fractCalc.getMeanCurve();
		}
		fullTDmean.setName("FullTD");
		noERTmean.setName("NoERT");
		
		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		ArbitrarilyDiscretizedFunc fullTDupperFunc = new ArbitrarilyDiscretizedFunc();
		fullTDupperFunc.setName("FullTD Upper 95%");
		ArbitrarilyDiscretizedFunc fullTDlowerFunc = new ArbitrarilyDiscretizedFunc();
		fullTDlowerFunc.setName("FullTD Lower 95%");
		ArbitrarilyDiscretizedFunc noERTupperFunc = new ArbitrarilyDiscretizedFunc();
		noERTupperFunc.setName("NoERT Upper 95%");
		ArbitrarilyDiscretizedFunc noERTlowerFunc = new ArbitrarilyDiscretizedFunc();
		noERTlowerFunc.setName("NoERT Lower 95%");
		
		double fssMaxMag = fss.getRupSet().getMaxMag();
		
		for (int n=0; n<2; n++) {
			XY_DataSet meanFunc, upperFunc, lowerFunc;
			int num;
			
			if (n == 0) {
				meanFunc = fullTDmean;
				upperFunc = fullTDupperFunc;
				lowerFunc = fullTDlowerFunc;
				num = fullTDCatalogs.size();
			} else {
				meanFunc = noERTmean;
				upperFunc = noERTupperFunc;
				lowerFunc = noERTlowerFunc;
				num = noERTCatalogs.size();
			}
			
			for (int i=0; i<meanFunc.size(); i++) {
				double x = meanFunc.getX(i);
				double y = meanFunc.getY(i);
				
				boolean aboveMax = x - 0.5*mfdDelta > fssMaxMag;
				if (aboveMax)
					Preconditions.checkState(y == 0);
				
				if (y >= 1d || aboveMax) {
					upperFunc.set(x, y);
					lowerFunc.set(x, y);
				} else {
					double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(y, num);
					lowerFunc.set(x, conf[0]);
					upperFunc.set(x, conf[1]);
				}
			}
		}
		
		Preconditions.checkState(fullTDmean.size() == noERTmean.size());
		ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
		meanFunc.setName("Mean");
		ArbitrarilyDiscretizedFunc totUpperFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc totLowerFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<fullTDmean.size(); i++) {
			double x = fullTDmean.getX(i);
			Preconditions.checkState(x == noERTmean.getX(i));
			double y = 0.5*(fullTDmean.getY(i) + noERTmean.getY(i));
			meanFunc.set(x, y);
			double upper = Math.max(fullTDupperFunc.getY(i), noERTupperFunc.getY(i));
			double lower = Math.min(fullTDlowerFunc.getY(i), noERTlowerFunc.getY(i));
			lower = Math.min(lower, y); // for places where mean=0 at high mag
			totUpperFunc.set(x, upper);
			totLowerFunc.set(x, lower);
		}
//		System.out.println("Lower Func");
//		System.out.println(totLowerFunc);
//		System.out.println("Full TD");
//		System.out.println(fullTDmean);
		
		UncertainArbDiscDataset meanConf = new UncertainArbDiscDataset(meanFunc, totLowerFunc, totUpperFunc);
		meanConf.setName("Uncertainties");
		
		UncertainArbDiscDataset meanRange = new UncertainArbDiscDataset(meanFunc, fullTDmean, noERTmean);
//		meanRange.setName("Uncertainties");
		
		funcs.add(meanConf);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, Color.LIGHT_GRAY));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN_TRANS, 1f, Color.GRAY));
		
		funcs.add(meanRange);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, Color.LIGHT_GRAY));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, Color.GRAY));
		
//		funcs.add(lowerFunc);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
//		
//		funcs.add(upperFunc);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
		
//		funcs.add(fullTDmean);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.DARK_GRAY));
//		
//		funcs.add(noERTmean);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.DARK_GRAY));
		
		funcs.add(meanFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(funcs, chars, name, "Magnitude", "Cumulative Number");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(meanFunc.getMinX(), meanFunc.getMaxX(), mfdMinY, mfdMaxY);
		gp.setLegendFontSize(20);
		
		ETAS_MultiSimAnalysisTools.setFontSizes(gp, 10);
		
		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(new File(outputDir, prefix+"_mean_with_conf.png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+"_mean_with_conf.pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix+"_mean_with_conf.txt").getAbsolutePath());
	}

}
