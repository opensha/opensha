package scratch.UCERF3.erf;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class FSS_ERF_PrefBlendTest {
	
	private static FaultSystemSolutionERF erf;
	private static List<Location> testLocs = Lists.newArrayList();
	
	private static int numSites = 5;
	
	private static ScalarIMR imr;
	private static Site site;
	
	private static HazardCurveCalculator calc;
	
	private static Map<MagDependentAperiodicityOptions, Double> weightsMap;
	
	private static DiscretizedFunc xVals;
	
	private static double tol = 1e-5;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// use single branch ERF
		erf = new UCERF3_CompoundSol_ERF();
		
		GriddedRegion reg = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		Random r = new Random();
		for (int i=0; i<numSites; i++)
			testLocs.add(reg.getNodeList().get(r.nextInt(reg.getNodeCount())));
		
		imr = new CB_2008_AttenRel(null);
		imr.setIntensityMeasure(PGA_Param.NAME);
		imr.setParamDefaults();
		xVals = new IMT_Info().getDefaultHazardCurve(imr.getIntensityMeasure());
		site = new Site();
		site.addParameterList(imr.getSiteParams());
		
		calc = new HazardCurveCalculator();
		
		weightsMap = Maps.newHashMap();
		weightsMap.put(MagDependentAperiodicityOptions.LOW_VALUES, FaultSystemSolutionERF.PREF_BLEND_COV_LOW_WEIGHT);
		weightsMap.put(MagDependentAperiodicityOptions.MID_VALUES, FaultSystemSolutionERF.PREF_BLEND_COV_MID_WEIGHT);
		weightsMap.put(MagDependentAperiodicityOptions.HIGH_VALUES, FaultSystemSolutionERF.PREF_BLEND_COV_HIGH_WEIGHT);
		weightsMap.put(null, FaultSystemSolutionERF.PREF_BLEND_POISSON_WEIGHT); // poisson
	}

	@Test
	public void test() {
		// first do preferred blend
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
		erf.updateForecast();
		
		List<DiscretizedFunc> blendFuncs = calcForSites();
		
		Map<MagDependentAperiodicityOptions, List<DiscretizedFunc>> indFuncsMap = Maps.newHashMap();
		for (MagDependentAperiodicityOptions cov : weightsMap.keySet()) {
			if (cov == null) {
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			} else {
				erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
				erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
			}
			erf.updateForecast();
			
			indFuncsMap.put(cov, calcForSites());
		}
		
		List<DiscretizedFunc> averagedFuncs = Lists.newArrayList();
		// initialize
		for (int i=0; i<numSites; i++) {
			DiscretizedFunc func = xVals.deepClone();
			func.scale(0d); // make sure it's empty
			averagedFuncs.add(func);
		}
		
		for (MagDependentAperiodicityOptions cov : weightsMap.keySet()) {
			List<DiscretizedFunc> funcs = indFuncsMap.get(cov);
			
			double weight = weightsMap.get(cov);
			
			for (int i=0; i<funcs.size(); i++) {
				DiscretizedFunc avgFunc = averagedFuncs.get(i);
				DiscretizedFunc indFunc = funcs.get(i);
				
				for (int j=0; j<avgFunc.size(); j++)
					avgFunc.set(j, avgFunc.getY(j) + weight*indFunc.getY(j));
			}
		}
		
		// now compare
		for (int i=0; i<averagedFuncs.size(); i++) {
			DiscretizedFunc avgFunc = averagedFuncs.get(i);
			DiscretizedFunc blendFunc = blendFuncs.get(i);
			
			for (int j=0; j<avgFunc.size(); j++) {
				double expected = avgFunc.getY(j);
				double actual = blendFunc.getY(j);
				assertEquals("Failed for site "+i+" pt "+j+" at x="+avgFunc.getX(j), expected, actual, tol);
			}
		}
	}
	
	private static List<DiscretizedFunc> calcForSites() {
		List<DiscretizedFunc> funcs = Lists.newArrayList();
		
		for (Location loc : testLocs) {
			DiscretizedFunc hazFunction = xVals.deepClone();
			site.setLocation(loc);
			calc.getHazardCurve(hazFunction, site, imr, erf);
			funcs.add(hazFunction);
		}
		
		return funcs;
	}

}
