package org.opensha.sha.calc.hazardMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.hazardMap.components.CalculationInputsXMLFile;
import org.opensha.sha.calc.hazardMap.components.CalculationSettings;
import org.opensha.sha.calc.hazardMap.components.CurveMetadata;
import org.opensha.sha.calc.hazardMap.components.CurveResultsArchiver;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TRTUtils;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This class calculates a set of hazard curves, typically for use in Condor.
 * 
 * @author kevin
 *
 */
public class HazardCurveSetCalculator {
	
	private static final boolean D = false;
	
	private ERF erf;
	private List<Map<TectonicRegionType, ScalarIMR>> imrMaps;
	private List<Parameter<Double>> imts;
	private CurveResultsArchiver archiver;
	private CalculationSettings calcSettings;
	private HazardCurveCalculator calc;
	
	public HazardCurveSetCalculator(ERF erf,
			List<Map<TectonicRegionType, ScalarIMR>> imrMaps,
			CurveResultsArchiver archiver,
			CalculationSettings calcSettings) {
		this(erf, imrMaps, null, archiver, calcSettings);
	}
	
	public HazardCurveSetCalculator(CalculationInputsXMLFile inputs) {
		this(inputs.getERF(), inputs.getIMRMaps(), inputs.getIMTs(), inputs.getArchiver(), inputs.getCalcSettings());
	}
	
	public HazardCurveSetCalculator(ERF erf,
			List<Map<TectonicRegionType, ScalarIMR>> imrMaps,
			List<Parameter<Double>> imts,
			CurveResultsArchiver archiver,
			CalculationSettings calcSettings) {
		this.erf = erf;
		this.imrMaps = imrMaps;
		this.imts = imts;
		this.archiver = archiver;
		this.calcSettings = calcSettings;
		
		if (imts != null && imts.size() != imrMaps.size())
			throw new IllegalArgumentException("If IMTs are specified for each IMR map, there must me exactly one" +
					" for every IMR map.");
		
	
		this.calc = new HazardCurveCalculator();
		
		erf.updateForecast();
	}
	
	public void calculateCurves(List<Site> sites, List<Integer> siteIndexes) throws IOException {
		if (D) System.out.println("Calculating " + sites.size() + " hazard curves");
		if (siteIndexes == null) {
			siteIndexes = Lists.newArrayList();
			for (int i=0; i<sites.size(); i++)
				siteIndexes.add(i);
		}
		Preconditions.checkState(siteIndexes.size() == sites.size());
		if (D) System.out.println("ERF: " + erf.getName());
		if (D) System.out.println("Num IMR Maps: " + imrMaps.size());
		calc.setMaxSourceDistance(calcSettings.getMaxSourceDistance());
		if (D) System.out.println("Max Source Cutoff: " + calcSettings.getMaxSourceDistance());
		// looop over all sites
		int siteCount = 0;
		for (int i=0; i<sites.size(); i++) {
			int index = siteIndexes.get(i);
			Site site = sites.get(i);
			siteCount++;
			if (siteCount % 10 == 0)
				System.gc();
			if (D) System.out.println("Calculating curve(s) for site " + siteCount + "/" + sites.size());
			calculateCurves(site, index);
		}
		if (D) System.out.println("DONE!");
	}
	
	public void calculateCurves(Site site, int index) throws IOException {
		int imrMapCount = 0;
		for (Map<TectonicRegionType, ScalarIMR> imrMap : imrMaps) {
			if (imts != null) {
				// if a different IMT has been specified for each imr map then we must set it
				Parameter<Double> newIMT = imts.get(imrMapCount);
//				System.out.println("Setting IMT to " + newIMT.getName());
				for (TectonicRegionType trt : imrMap.keySet()) {
					ScalarIMR imr = imrMap.get(trt);
					imr.setIntensityMeasure(newIMT.getName());
					Parameter<Double> imt = (Parameter<Double>) imr.getIntensityMeasure();
					for (Parameter<?> depParam : newIMT.getIndependentParameterList())
						imt.getIndependentParameter(depParam.getName()).setValue(depParam.getValue());
				}
			}
			imrMapCount++;
			Parameter<Double> imtParam =
				(Parameter<Double>) TRTUtils.getFirstIMR(imrMap).getIntensityMeasure();
			String imt = imtParam.getName();
			String imtMeta = imtParam.getName();
			if (imtParam instanceof SA_Param) {
				imtMeta += " (Period: "+SA_Param.getPeriodInSA_Param(imtParam)+" sec)";
			}
			if (D) System.out.println("Calculating curve(s) for IMR Map " + imrMapCount
					+ "/" + imrMaps.size() + " IMT: " + imtMeta);
			CurveMetadata meta = new CurveMetadata(site, index, imrMap, "imrs" + imrMapCount);
			if (archiver.isCurveCalculated(meta, calcSettings.getXValues(imt))) {
				if (D) System.out.println("Curve already calculated, skipping...");
				continue;
			}
			DiscretizedFunc calcFunction;
			boolean logSpace = calcSettings.isCalcInLogSpace();
			if (logSpace)
				calcFunction = getLogFunction(calcSettings.getXValues(imt));
			else
				calcFunction = calcSettings.getXValues(imt).deepClone();
			long curveStart = System.currentTimeMillis();
			if (D) System.out.println("Calculating Hazard Curve. timestamp=" + curveStart);
			// actually calculate the curve from the log hazard function, site, IMR, and ERF
			try {
				calc.getHazardCurve(calcFunction,site,imrMap,erf);
			} catch (Exception e) {
				System.err.println("Error calculating hazard curve. Metadata below.");
				System.err.println("Site: "+site);
				System.err.println("ERF: "+erf.getName());
				try {
					System.err.println("IMR: "+IMR_MultiGuiBean.getIMRMetadataHTML(imrMap).replaceAll("<br>", "\n"));
				} catch (Exception e1) {
					System.err.println("IMR: "+imrMap.values().iterator().next().getName());
				}
				System.err.println("Function: "+calcFunction);
				ExceptionUtils.throwAsRuntimeException(e);
			}
			long curveEnd = System.currentTimeMillis();
			float curveSecs = (float)(curveEnd - curveStart) / 1000f;
			if (D) System.out.println("Calculated a curve! timestamp=" + curveEnd + " ("+curveSecs+" secs)");
			DiscretizedFunc hazardCurve;
			if (logSpace)
				hazardCurve = unLogFunction(calcSettings.getXValues(imt), calcFunction);
			else
				hazardCurve = calcFunction;
			// archive the curve;
			archiver.archiveCurve(hazardCurve, meta);
		}
	}
	
	/**
	 * Takes the log of the X-values of the given function
	 * @param arb
	 * @return A function with points (Log(x), 1)
	 */
	public static ArbitrarilyDiscretizedFunc getLogFunction(DiscretizedFunc arb) {
		ArbitrarilyDiscretizedFunc new_func = new ArbitrarilyDiscretizedFunc();
		// TODO: we need to check if the log should be taken for all IMTs GEM will be using!
		// take log only if it is PGA, PGV or SA
//		if (this.xLogFlag) {
			for (int i = 0; i < arb.size(); ++i)
				new_func.set(Math.log(arb.getX(i)), 1);
			return new_func;
//		}
//		else
//			throw new RuntimeException("Unsupported IMT");
	}
	
	public static ArbitrarilyDiscretizedFunc unLogFunction(
			DiscretizedFunc oldHazFunc, DiscretizedFunc logHazFunction) {
		int numPoints = oldHazFunc.size();
		ArbitrarilyDiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
		// take log only if it is PGA, PGV or SA
//		if (this.xLogFlag) {
			for (int i = 0; i < numPoints; ++i) {
				hazFunc.set(oldHazFunc.getX(i), logHazFunction.getY(i));
			}
			return hazFunc;
//		}
//		else
//			throw new RuntimeException("Unsupported IMT");
	}
	
	public void close() {
		archiver.close();
	}

}
