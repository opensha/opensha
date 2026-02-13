package org.opensha.nshmp2.calc;

import static org.opensha.nshmp2.util.Period.GM0P20;
import static org.opensha.nshmp2.util.SourceType.CLUSTER;
import static org.opensha.nshmp2.util.SourceType.GRIDDED;
import static org.opensha.sha.util.NEHRP_TestCity.LOS_ANGELES;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.nshmp2.erf.NSHMP2008;
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
import org.opensha.sha.util.NEHRP_TestCity;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import scratch.UCERF3.utils.ProbOfExceed;
import scratch.peter.nshmp.DeterministicResult;
import scratch.peter.nshmp.HazardCurveCalculatorNSHMP;

/**
 * Standalone calculator class for NSHMP_ListERFs. Assumes Poissonian.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardCalc implements Callable<HazardResult> {

	private EpistemicListERF erfList;
	private SourceIMR imrRef;
	private Site site;
	private Period period;
	private boolean epiUncert;
	private DiscretizedFunc curve;

	private boolean determ = false;
	private DeterministicResult detData = null;
	
	private HazardCurveCalculatorNSHMP calc;
	
	private HazardCalc() {}
	
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
	public static HazardCalc create(EpistemicListERF erfList, Site site,
			Period period, boolean epiUncert) {
		HazardCalc hc = new HazardCalc();
		hc.erfList = erfList;
		hc.site = site;
		hc.period = period;
		hc.epiUncert = epiUncert;
		return hc;
	}
	
	public static HazardCalc create(EpistemicListERF erfList, SourceIMR imrRef, 
			Site site, Period period, boolean epiUncert, boolean determ) {
		HazardCalc hc = new HazardCalc();
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
		
		calc.getAdjustableParams().getParameter(MaxDistanceParam.NAME).setValue(300.0);
		
//		callCalc();
		if (erfList instanceof NSHMP_ListERF) {
			callNSHMP((NSHMP_ListERF) erfList);
		} else {
			callCalc();
		}

		return new HazardResult(period, site.getLocation(), curve, detData);
	}
	
	private void callCalc() {
		ScalarIMR imr = (imrRef != null) ?
			imrRef.instance(period) :
			SourceIMR.WUS_FAULT_14.instance(period);
		imr.getParameter(NSHMP08_WUS.IMR_UNCERT_PARAM_NAME).setValue(
			epiUncert);
		imr.setSite(site);
		DiscretizedFunc f = period.getFunction(); // utility function
		if (determ) detData = new DeterministicResult();

		for (int i=0; i<erfList.getNumERFs(); i++) {
			ERF erf = erfList.getERF(i);
			f = basicCalc(calc, f, site, imr, erf, detData);
			f.scale(erfList.getERF_RelativeWeight(i));
			Utils.addFunc(curve, f);
		}
	}
	
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
				f = basicCalc(calc, f, site, imr, erf, null);
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
				@Override public Double apply(Double mag) {
					return (mag < 6.5) ? depths[0] : depths[1];
				}
			};
			wusIMR.setTable(func);

		} else if (imr instanceof NSHMP08_SUB_SlabGrid) {
			NSHMP08_SUB_SlabGrid slabIMR = (NSHMP08_SUB_SlabGrid) imr;
			slabIMR.getParameter(RupTopDepthParam.NAME).setValue(erf.getDepths()[0]);
			slabIMR.setTable();
		}
	}
	
	private static DiscretizedFunc basicCalc(
			HazardCurveCalculatorNSHMP c,
			DiscretizedFunc f,
			Site s,
			ScalarIMR imr,
			ERF erf,
			DeterministicResult detResult) {
		
		c.getHazardCurve(f, s, imr, erf, detResult, null);
		// convert to annual rate
		for (Point2D p : f) {
			f.set(p.getX(), NSHMP_Utils.probToRate(p.getY(), 1));
		}
		return f;
	}
	
	private static DiscretizedFunc clusterCalc(
			DiscretizedFunc f, 
			Site s,
			ScalarIMR imr, 
			ClusterERF erf) {
		
		double maxDistance = erf.getMaxDistance();
		Utils.zeroFunc(f); //zero for aggregating results
		DiscretizedFunc peFunc = f.deepClone();
		
		for (ClusterSource cs : erf.getSources()) { // geom variants
			
			// apply distance cutoff to source
			double dist = cs.getMinDistance(s);
			if (dist > maxDistance) {
				continue;
			}
			// assemble list of PE curves for each cluster segment
			List<DiscretizedFunc> fltFuncList = Lists.newArrayList();

			for (FaultSource fs : cs.getFaultSources()) { // segments
				DiscretizedFunc fltFunc = peFunc.deepClone();
				Utils.zeroFunc(fltFunc);
				// agregate weighted PE curves for mags on each segment
				for (int i=0; i < fs.getNumRuptures(); i++) { // mag variants
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
			double rateAndWeight = cs.getWeight() / cs.getRate();
			fOut.scale(rateAndWeight);
			Utils.addFunc(f, fOut);
		} // end geom
		return f;
	}
	
	/*
	 * Computes joint probability of exceedence given the occurrence of a
	 * cluster of events: [1 - [(1-PE1) * (1-PE2) * ...]]. The probability of
	 * exceedance of each individual event is given in the supplied curves.
	 * WARNING: Method modifies curves in place and returns result in the first
	 * supplied curve.s
	 */
	private static DiscretizedFunc calcClusterExceedProb(List<DiscretizedFunc> fList) {
		DiscretizedFunc firstFunc = fList.get(0);
		// set all to complement and multiply into first
		for (int i=0; i < fList.size(); i++) {
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
		DepthTo1pt0kmPerSecParam d10p = new DepthTo1pt0kmPerSecParam(null,
			0, 1000, true);
		d10p.setValueAsDefault();
		s.addParameter(d10p);
		// CB
		DepthTo2pt5kmPerSecParam d25p = new DepthTo2pt5kmPerSecParam(null,
			0, 1000, true);
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
	
	public static void main(String[] args) {
		
////		WUS_ERF erf = new WUS_ERF();
////		EpistemicListERF erf = ERF_ID.MEAN_UCERF2.instance();
////		EpistemicListERF erf = NSHMP2008.createCalifornia();
//		EpistemicListERF erf = NSHMP2008.createSingleSource("bFault.ch.in");
//		erf.updateForecast();
//		System.out.println(erf);
//		Period p = Period.GM0P00;
//
//		Site site;
//		HazardCalc hc;
////		Site site = new Site(new Location(34.1, -118.1));
//		
//		// 2008 calc
////		site = new Site(NEHRP_TestCity.LOS_ANGELES.location());
////		hc = HazardCalc.create(erf, SourceIMR.WUS_FAULT, site, p, false);
//		
//		// 2013 calc
//		site = new Site(NEHRP_TestCity.LOS_ANGELES.location());
//		hc = HazardCalc.create(erf, SourceIMR.WUS_FAULT, site, p, false, false);
//		
//		HazardResult result = hc.call();
//		System.out.println(result.curve());
//		System.out.println(result.curve().getFirstInterpolatedX_inLogXLogYDomain(ProbOfExceed.PE2IN50.annualRate()));
		
//		site = new Site(new Location(34.15, -118.15));
//		hc = HazardCalc.create(erf, site, p, true);
//		result = hc.call();
//		System.out.println(result.curve());
//		System.out.println(result.curve().getFirstInterpolatedX_inLogXLogYDomain(ProbOfExceed.PE2IN50.annualRate()));

//		Set<NEHRP_TestCity> cities = EnumSet.of(
//			NEHRP_TestCity.LOS_ANGELES,
//			NEHRP_TestCity.SEATTLE,
//			NEHRP_TestCity.SALT_LAKE_CITY,
//			NEHRP_TestCity.MEMPHIS);
//		
//		for (NEHRP_TestCity city : cities) {
//			sw.reset().start();
//			Site site = new Site(city.shiftedLocation());
//			System.out.println(city.name() + " " + site.getLocation());
//			HazardCalc2 hc = HazardCalc2.create(erf, site, p);
//			HazardCalcResult result = hc.call();
//			System.out.println(result.curve());
//			sw.stop();
//			System.out.println("Time: " + sw.elapsedTime(TimeUnit.SECONDS) + " sec");
//		}
		
//		EpistemicListERF erf = NSHMP2008.createSingleSource("bFault.ch.in");
		EpistemicListERF erf = NSHMP2008.create();
		erf.updateForecast();
		System.out.println(erf);
		List<Period> periods = Lists.newArrayList(GM0P20);//, GM0P20,GM1P00);
		List<NEHRP_TestCity> cities = Lists.newArrayList(LOS_ANGELES);//, SAN_FRANCISCO, OAKLAND);

		for (Period p : periods) {
			System.out.println("Period: " + p.getLabel());
			for (NEHRP_TestCity city : cities) {
				Site site = new Site(city.location());
				HazardCalc hc = HazardCalc.create(erf, SourceIMR.WUS_FAULT, 
					site, p, false, false);
				HazardResult result = hc.call();
				System.out.println(city);
				System.out.println(result.curve());
				
				System.out.println(result.curve().getFirstInterpolatedX_inLogXLogYDomain(ProbOfExceed.PE2IN50.annualRate()));
			}
		}
		
		ArbitrarilyDiscretizedFunc f2 = new ArbitrarilyDiscretizedFunc();
		
//		double[] xs = new double[] {0.005000,0.007000,0.009800,0.013700,0.019200,0.026900,0.037600,0.052700,0.073800,0.103000,0.145000,0.203000,0.284000,0.397000,0.556000,0.778000,1.090000,1.520000,2.200000,3.300000};
//		double[] ys = new double[] {0.47147E-01,0.45436E-01,0.43057E-01,0.39983E-01,0.36170E-01,0.31674E-01,0.26671E-01,0.21415E-01,0.16442E-01,0.12197E-01,0.87027E-02,0.60197E-02,0.39228E-02,0.23271E-02,0.12039E-02,0.52755E-03,0.18905E-03,0.54112E-04,0.93344E-05,0.64881E-06};
//		for (int i=0; i<xs.length; i++) {
//			f2.set(xs[i], ys[i]);
//		}
//		System.out.println(f2.getFirstInterpolatedX_inLogXLogYDomain(ProbOfExceed.PE2IN50.annualRate()));
		System.exit(0);

	}
	
}
