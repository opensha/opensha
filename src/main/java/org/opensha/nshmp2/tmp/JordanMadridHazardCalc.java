package org.opensha.nshmp2.tmp;

import static org.opensha.nshmp2.util.SourceType.CLUSTER;
import static org.opensha.nshmp2.util.SourceType.GRIDDED;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.nshmp2.calc.HazardResult;
import org.opensha.nshmp2.erf.NSHMP_ListERF;
import org.opensha.nshmp2.erf.source.ClusterERF;
import org.opensha.nshmp2.erf.source.ClusterSource;
import org.opensha.nshmp2.erf.source.FaultSource;
import org.opensha.nshmp2.erf.source.GridERF;
import org.opensha.nshmp2.erf.source.NSHMP_ERF;
import org.opensha.nshmp2.imr.NSHMP08_CEUS_Grid;
import org.opensha.nshmp2.imr.NSHMP08_SUB_SlabGrid;
import org.opensha.nshmp2.imr.NSHMP08_WUS;
import org.opensha.nshmp2.imr.NSHMP08_WUS_Grid;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.SiteTypeParam;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.calc.sourceFilters.params.MaxDistanceParam;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.FocalMech;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.io.Flushables;

import scratch.peter.nshmp.DeterministicResult;
import scratch.peter.nshmp.HazardCurveCalculatorNSHMP;

/**
 * Standalone calculator class for NSHMP_ListERFs. Assumes Poissonian.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class JordanMadridHazardCalc implements Callable<HazardResult> {

	private EpistemicListERF erfList;
	private SourceIMR imrRef;
	private Site site;
	private Period period;
	private boolean epiUncert;
	private DiscretizedFunc curve;

	private boolean determ = false;
	private DeterministicResult detData = null;

	private HazardCurveCalculatorNSHMP calc;

	private ResWriter resWriter;

	private JordanMadridHazardCalc() {
		resWriter = new ResWriter();
	}

	/**
	 * Creates a new calculation instance. Currently, NSHMP calculations use a
	 * single shared reference to a threadsafe ERF. Note, however, that every
	 * calc instance should be created with a new imrMap, as IMRs are not
	 * threadsafe.
	 * 
	 * @param erfList list to use
	 * @param site
	 * @param period
	 * @param epiUncert
	 * @return a calculation instance
	 */
	public static JordanMadridHazardCalc create(EpistemicListERF erfList,
			Site site, Period period, boolean epiUncert) {
		JordanMadridHazardCalc hc = new JordanMadridHazardCalc();
		hc.erfList = erfList;
		hc.site = site;
		hc.period = period;
		hc.epiUncert = epiUncert;
		hc.resWriter.writeHeader(period);
		return hc;
	}

	public static JordanMadridHazardCalc create(EpistemicListERF erfList,
			SourceIMR imrRef, Site site, Period period, boolean epiUncert,
			boolean determ) {
		JordanMadridHazardCalc hc = new JordanMadridHazardCalc();
		hc.erfList = erfList;
		hc.site = site;
		hc.period = period;
		hc.epiUncert = epiUncert;
		hc.imrRef = imrRef;
		hc.determ = determ;
		return hc;
	}

	@Override
	public HazardResult call() {
		initSite(site); // ensure required site parameters are set
		curve = period.getFunction(); // init output function
		Utils.zeroFunc(curve);
		calc = new HazardCurveCalculatorNSHMP(); // init calculator
		// callCalc();
		if (erfList instanceof NSHMP_ListERF) {
			callNSHMP((NSHMP_ListERF) erfList);
		} else {
			callCalc();
		}
		try {
			resWriter.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return new HazardResult(period, site.getLocation(), curve, detData);
	}

	private void callCalc() {
		ScalarIMR imr = (imrRef != null) ? imrRef.instance(period)
			: SourceIMR.WUS_FAULT_14.instance(period);
		imr.getParameter(NSHMP08_WUS.IMR_UNCERT_PARAM_NAME).setValue(epiUncert);
		imr.setSite(site);
		DiscretizedFunc f = period.getFunction(); // utility function
		if (determ) detData = new DeterministicResult();

		for (int i = 0; i < erfList.getNumERFs(); i++) {
			ERF erf = erfList.getERF(i);
			f = basicCalc(calc, f, site, imr, erf, detData, null);
			f.scale(erfList.getERF_RelativeWeight(i));
			Utils.addFunc(curve, f);
		}
	}

	public static DiscretizedFunc dfAB = null;
	public static DiscretizedFunc dfJ = null;
	
	// erf recast to NSHMP flavor
	private void callNSHMP(NSHMP_ListERF erfList) {
		// IMR map inited to period of interest and site
		Map<SourceIMR, ScalarIMR> imrMap = SourceIMR.map(period);
		for (ScalarIMR imr : imrMap.values()) {
			imr.setSite(site);
			if (imr instanceof NSHMP08_WUS) {
				imr.getParameter(NSHMP08_WUS_Grid.IMR_UNCERT_PARAM_NAME)
					.setValue(epiUncert);
			}
		}
		DiscretizedFunc f = period.getFunction(); // utility function
		for (NSHMP_ERF erf : erfList.asFilteredIterable(site.getLocation())) {
			ScalarIMR imr = imrMap.get(erf.getSourceIMR());
			SourceType st = erf.getSourceType();
			if (st == GRIDDED) initGridIMR(imr, (GridERF) erf); // set tables
			if (st == CLUSTER) {
				f = clusterCalc(f, site, imr, (ClusterERF) erf);
			} else {
				// set max distance on calculator first
				calc.getAdjustableParams().getParameter(MaxDistanceParam.NAME)
					.setValue(erf.getMaxDistance());
				f = basicCalc(calc, f, site, imr, erf, null, resWriter);
				
				if (erf.getName().contains("all8.AB")) {
					dfAB = f.deepClone();
//					resWriter.writeCurve("bg-AB", 0.5, dfAB);
				}
				if (erf.getName().contains("all8.J")) {
					dfJ = f.deepClone();
//					resWriter.writeCurve("bg-J", 0.5, dfJ);
				}

			}
			f.scale(erf.getSourceWeight());
			Utils.addFunc(curve, f);
		}
	}

	private static void initGridIMR(ScalarIMR imr, final GridERF erf) {

		if (imr instanceof NSHMP08_CEUS_Grid) {
			NSHMP08_CEUS_Grid ceusIMR = (NSHMP08_CEUS_Grid) imr;
			ceusIMR.setTable(erf.getFaultCode());

		} else if (imr instanceof NSHMP08_WUS_Grid) {
			NSHMP08_WUS_Grid wusIMR = (NSHMP08_WUS_Grid) imr;

			// set fault type ; kinda KLUDGY
			Map<FocalMech, Double> mechMap = erf.getFocalMechs();
			double sWt = mechMap.get(FocalMech.STRIKE_SLIP);
			double rWt = mechMap.get(FocalMech.REVERSE);
			double nWt = mechMap.get(FocalMech.NORMAL);
			String fltType = (rWt > 0) ? "Reverse" : (nWt > 0) ? "Normal"
				: "Strike-Slip";
			wusIMR.getParameter(FaultTypeParam.NAME).setValue(fltType);
			wusIMR.setGridHybrid(sWt > 0 && sWt < 1);

			// set mag dependent depth function
			Function<Double, Double> func = new Function<Double, Double>() {
				double[] depths = erf.getDepths();

				@Override
				public Double apply(Double mag) {
					return (mag < 6.5) ? depths[0] : depths[1];
				}
			};
			wusIMR.setTable(func);

		} else if (imr instanceof NSHMP08_SUB_SlabGrid) {
			NSHMP08_SUB_SlabGrid slabIMR = (NSHMP08_SUB_SlabGrid) imr;
			slabIMR.getParameter(RupTopDepthParam.NAME).setValue(
				erf.getDepths()[0]);
			slabIMR.setTable();
		}
	}

	private DiscretizedFunc basicCalc(HazardCurveCalculatorNSHMP c,
			DiscretizedFunc f, Site s, ScalarIMR imr, ERF erf,
			DeterministicResult detResult, ResWriter resWriter) {

		c.getHazardCurve(f, s, imr, erf, detResult, resWriter);
		// convert to annual rate
		for (Point2D p : f) {
			f.set(p.getX(), NSHMP_Utils.probToRate(p.getY(), 1));
		}
		return f;
	}

	private Map<String, DiscretizedFunc> map750 = Maps.newHashMap();
	
	private DiscretizedFunc clusterCalc(DiscretizedFunc f, Site s,
			ScalarIMR imr, ClusterERF erf) {

		double maxDistance = erf.getMaxDistance();
		Utils.zeroFunc(f); // zero for aggregating results
		DiscretizedFunc peFunc = f.deepClone();

		for (ClusterSource cs : erf.getSources()) { // geom variants

			// apply distance cutoff to source
			double dist = cs.getMinDistance(s);
			if (dist > maxDistance) {
				continue;
			}
//			System.out.println("Cluster Source: " + cs.getName());
			// assemble list of PE curves for each cluster segment
			List<DiscretizedFunc> fltFuncList = Lists.newArrayList();

			for (FaultSource fs : cs.getFaultSources()) { // segments
//				System.out.println("   FaultSource: " + fs.getName());
				DiscretizedFunc fltFunc = peFunc.deepClone();
				Utils.zeroFunc(fltFunc);
				// agregate weighted PE curves for mags on each segment
				for (int i = 0; i < fs.getNumRuptures(); i++) { // mag variants
					imr.setEqkRupture(fs.getRupture(i));
					imr.getExceedProbabilities(peFunc);
					double weight = fs.getMFDs().get(i).getY(0) * cs.getRate();
					peFunc.scale(weight);
					Utils.addFunc(fltFunc, peFunc);
				} // end mag
				fltFuncList.add(fltFunc);
			} // end segments

			// compute joint PE, scale by geom weight, scale by rate (1/RP),
			// and add to final result
			DiscretizedFunc fOut = calcClusterExceedProb(fltFuncList);
			// double rateAndWeight = cs.getWeight() / cs.getRate();
			double csWeight = cs.getWeight();
			double csRate = 1.0 / cs.getRate();
			fOut.scale(csRate);
			
			// cache 750yr curves and add to 1500yr curves
			if (cs.getRate() == 750.0) {
				map750.put(cs.getName(), fOut.deepClone());
			} else {
				// bg
				String id1 = cs.getName() + " " + cs.getRate() + " bgAB";
				double outWt = csWeight * erf.getSourceWeight() * 0.5;
				DiscretizedFunc f1 = fOut.deepClone();
				if (cs.getRate() == 1500.0) addFuncs(f1, map750.get(cs.getName()));
				addFuncs(f1, dfAB);
				resWriter.writeCurve(id1, outWt, f1);

				String id2 = cs.getName() + " " + cs.getRate() + " bgJ";
				DiscretizedFunc f2 = fOut.deepClone();
				if (cs.getRate() == 1500.0) addFuncs(f2, map750.get(cs.getName()));
				addFuncs(f2, dfJ);
				resWriter.writeCurve(id2, outWt, f2);

				// // no bg
				// String id1 = cs.getName() + " " + cs.getRate();
				// double outWt = csWeight * erf.getSourceWeight();
				// DiscretizedFunc f1 = fOut.deepClone();
				// resWriter.writeCurve(id1, outWt, f1);

			}
			fOut.scale(csWeight);

			Utils.addFunc(f, fOut);
		} // end geom
		return f;
	}

	// adds f2 to f1 in place
	public static void addFuncs(DiscretizedFunc f1, DiscretizedFunc f2) {
		for (int i=0; i<f1.size(); i++) {
			f1.set(i, f1.getY(i) + f2.getY(i));
		}
	}
	
	/*
	 * Computes joint probability of exceedence given the occurrence of a
	 * cluster of events: [1 - [(1-PE1) * (1-PE2) * ...]]. The probability of
	 * exceedance of each individual event is given in the supplied curves.
	 * WARNING: Method modifies curves in place and returns result in the first
	 * supplied curve.s
	 */
	private static DiscretizedFunc calcClusterExceedProb(
			List<DiscretizedFunc> fList) {
		DiscretizedFunc firstFunc = fList.get(0);
		// set all to complement and multiply into first
		for (int i = 0; i < fList.size(); i++) {
			DiscretizedFunc f = fList.get(i);
			Utils.complementFunc(f);
			if (i == 0) continue;
			Utils.multiplyFunc(firstFunc, f);
		}
		Utils.complementFunc(firstFunc);
		return firstFunc;
	}

	public static void initSite(Site s) {

		// CY AS
		DepthTo1pt0kmPerSecParam d10p = new DepthTo1pt0kmPerSecParam(null, 0,
			1000, true);
		d10p.setValueAsDefault();
		s.addParameter(d10p);
		// CB
		DepthTo2pt5kmPerSecParam d25p = new DepthTo2pt5kmPerSecParam(null, 0,
			1000, true);
		d25p.setValueAsDefault();
		s.addParameter(d25p);
		// all
		Vs30_Param vs30p = new Vs30_Param(760);
		vs30p.setValueAsDefault();
		s.addParameter(vs30p);
		// AS CY
		Vs30_TypeParam vs30tp = new Vs30_TypeParam();
		vs30tp.setValueAsDefault();
		s.addParameter(vs30tp);

		// CEUS only (TODO imrs need to be changed to accept vs value)
		SiteTypeParam siteTypeParam = new SiteTypeParam();
		s.addParameter(siteTypeParam);
	}

	private static void throwCalcException(Exception e, int srcIdx, int rupIdx,
			ERF erf, ScalarIMR imr, Site s, DiscretizedFunc f) {
		// TODO this should be logged
		StringBuffer sb = new StringBuffer();
		sb.append("Calculator error in: ");
		sb.append("Source [").append(srcIdx).append("] ");
		sb.append("Rupture [").append(rupIdx).append("]");
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("  ERF: " + erf.getName());
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("  IMR: " + imr.getName());
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("  Site: " + s);
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("  Curve: " + f);
		sb.append(IOUtils.LINE_SEPARATOR);
		System.err.println(sb.toString());
		Throwables.propagate(e);
	}

	public static final String PATH = "tmp/forJordan/Memphis/Memphis_tmp.csv";
	private static final Joiner J = Joiner.on(',').useForNull(" ");
	
	public static class ResWriter {

		
		private BufferedWriter writer;

		ResWriter() {
			try {
				File out = new File(PATH);
				Files.createParentDirs(out);
				writer = Files.newWriter(out, Charsets.US_ASCII);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		private void writeHeader(Period p) {
			try {
				List<String> headerVals = Lists.newArrayList();
				headerVals.add("id");
				headerVals.add("weight");
				for (Double d : p.getIMLs()) {
					headerVals.add(d.toString());
				}
				writer.write(J.join(headerVals));
				writer.newLine();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		public void writeCurve(String id, double weight, DiscretizedFunc curve) {
			try {
				String resultStr = formatCurve(id, weight, curve);
				writer.write(resultStr);
				writer.newLine();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		private static String formatCurve(String id, double weight,
				DiscretizedFunc curve) {
			List<String> dat = Lists.newArrayList();
			dat.add(id);
			dat.add(String.format("%.8f", weight));
			for (Point2D p : curve) {
				dat.add(String.format("%.8g", p.getY()));
			}
			return J.join(dat);
		}

		public void close() throws IOException {
			Flushables.flushQuietly(writer);
			Closeables.close(writer, true);
		}

	}
}
