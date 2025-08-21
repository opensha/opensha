package org.opensha.sha.imr.attenRelImpl;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.DevStatus;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RectangularSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceX_Parameter;
import org.opensha.sha.imr.param.PropagationEffectParams.PropagationEffectParameter;

@RunWith(Parameterized.class)
public class ProductionIMRsBasicConsitencyTest {
	
	private AttenRelRef impl;
	private boolean doMeanTests;
	
	private static Location pointLoc;
	private static Location colocatedLoc;
	private static Location farLoc;
	private static EqkRupture finiteRup;
	
	public ProductionIMRsBasicConsitencyTest(AttenRelRef impl) {
		this.impl = impl;
		
		// this one is weird
		doMeanTests = impl != AttenRelRef.NON_ERGODIC_2016 && impl != AttenRelRef.NSHMP_2008;
	}
	
	private ScalarIMR instance() {
		ScalarIMR imr = impl.instance(null);
		assertNotNull("IMR instance returned is NULL!", imr);
		imr.setParamDefaults();
		imr.setIntensityMeasure(imr.getSupportedIntensityMeasures().getByIndex(0));
		return imr;
	}
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		pointLoc = new Location(0d, 0d);
		colocatedLoc = new Location(0.01, 0.01); // not perfectly colocated, some require rRup>0
		farLoc = new Location(1d, 1d);
//		PointSurface pointSurf = new PointSurface(pointLoc);
//		pointSurf.setAveDip(90d);
//		pointSurf.setAveLength(0d);
//		pointSurf.setDepths(5d, 10d);
		Location hypo = new Location(0d, 0d, 7.5d);
//		pointRup = new EqkRupture(7.123, 0.1234, pointSurf, hypo); 
		RectangularSurface rectSurf = new RectangularSurface(new Location(-0.5, 0, 5), new Location(0.5, 0, 5), 90d, 10d);
		finiteRup = new EqkRupture(8.123, 1.1234, rectSurf, hypo);
	}
	
	@Parameters
	public static Collection<AttenRelRef[]> data() {
//		Set<AttenRelRef> set = AttenRelRef.get(DevStatus.PRODUCTION);
		Set<AttenRelRef> set = AttenRelRef.get(DevStatus.PRODUCTION, DevStatus.DEVELOPMENT);
		ArrayList<AttenRelRef[]> ret = new ArrayList<AttenRelRef[]>();
		for (AttenRelRef imr : set) {
			AttenRelRef[] array = { imr };
			ret.add(array);
		}
		return ret;
	}
	
	@Test
	public void testInstantiation() {
		ScalarIMR imr = instance();
		assertNotNull("IMR instance returned is NULL!", imr);
	}
	
	@Test
	public void testSetSite() {
		ScalarIMR imr = instance();
		Site site = new Site(new Location(0d, 0d));
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		assertEquals(site, imr.getSite());
	}
	
	@Test
	public void testSetSiteWithMean() {
		if (!doMeanTests)
			return;
		ScalarIMR imr = instance();
		Site site = new Site(colocatedLoc); // not perfectly colocated (some models can't compute for exactly colocated)
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		imr.setEqkRupture(finiteRup);
//		System.out.println("MEAN test for "+imr.getName()+"\t"+imr.getShortName());
		double colocatedMean = imr.getMean();
//		System.out.println("\t"+colocatedMean);
		assertTrue(impl.getShortName()+": mean is non-finite after setting site and eqk rup: "+colocatedMean, Double.isFinite(colocatedMean));
		
		// move further away, mean should go down
		site = new Site(farLoc);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		double farMean = imr.getMean();
		assertTrue(impl.getShortName()+": moving site further away didn't decrease mean; "
				+ "colocated="+colocatedMean+", far="+farMean, farMean < colocatedMean);
		
		// now try again with setSiteLocation
		// first reset to colocated
		site = new Site(colocatedLoc); // not perfectly colocated (some models can't compute for exactly colocated)
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		double colocatedMean2 = imr.getMean();
		assertEquals(colocatedMean, colocatedMean2, 1e-10);
		imr.setSiteLocation(farLoc);
		double farMean2 = imr.getMean();
		assertEquals(farMean, farMean2, 1e-10);
	}
	
	private static void debugPrintParams(ScalarIMR imr) {
		System.out.println("DEBUG for "+imr.getName());
		System.out.println("IMT:\t"+imr.getIntensityMeasure().getName());
		System.out.println("IMT params:");
		for (Parameter<?> param : imr.getIntensityMeasure().getIndependentParameterList())
			System.out.println("\t"+param.getName()+":\t"+param.getValue());
		System.out.println("Other params:");
		for (Parameter<?> param : imr.getOtherParams())
			System.out.println("\t"+param.getName()+":\t"+param.getValue());
		System.out.println("EqkRup params:");
		for (Parameter<?> param : imr.getEqkRuptureParams())
			System.out.println("\t"+param.getName()+":\t"+param.getValue());
		System.out.println("Site params:");
		for (Parameter<?> param : imr.getSiteParams())
			System.out.println("\t"+param.getName()+":\t"+param.getValue());
		System.out.println("PropEffect params:");
		for (Parameter<?> param : imr.getPropagationEffectParams())
			System.out.println("\t"+param.getName()+":\t"+param.getValue());
	}
	
	@Test
	public void testSetSiteLocation() {
		ScalarIMR imr = instance();
		Site site = new Site(pointLoc);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		Location newLoc = new Location(1d, 1d);
		imr.setSiteLocation(newLoc);
		assertEquals(newLoc, imr.getSite().getLocation());
	}
	
	@Test
	public void testSetAndClearRup() {
		ScalarIMR imr = instance();
		imr.setEqkRupture(finiteRup);
		assertEquals(finiteRup, imr.getEqkRupture());
		imr.setEqkRupture(null); // shouldn't fail
		assertNull(imr.getEqkRupture());
		Site site = new Site(pointLoc);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		imr.setEqkRupture(finiteRup);
		assertEquals(finiteRup, imr.getEqkRupture());
		imr.setEqkRupture(null); // still shouldn't fail
		assertNull(imr.getEqkRupture());
	}
	
	@Test
	public void testDistanceRup() {
		ScalarIMR imr = instance();
		testPropEffectParam(imr, DistanceRupParameter.NAME, false);
		
		ParameterList propParams = imr.getPropagationEffectParams();
		int numTested = 0;
		for (Parameter<?> param : propParams) {
			String name = param.getName();
			if (name.equals(DistanceRupParameter.NAME)
					|| name.equals(DistanceJBParameter.NAME)
					|| name.equals(DistanceX_Parameter.NAME)) {
				numTested++;
			}
		}
		if (numTested == 0)
			System.err.println("WARNING: "+imr.getName()+" ("+imr.getClass().getName()
					+") doesn't have any supported/testable prop effect params. Has "+propParams.size()+" in list.");
	}
	
	@Test
	public void testDistanceJB() {
		ScalarIMR imr = instance();
		testPropEffectParam(imr, DistanceJBParameter.NAME, true);
	}
	
	@Test
	public void testDistanceX() {
		ScalarIMR imr = instance();
		testPropEffectParam(imr, DistanceX_Parameter.NAME, false);
	}
	
	private void testPropEffectParam(ScalarIMR imr, String name, boolean shouldMatchHorzDist) {
		ParameterList propParams = imr.getPropagationEffectParams();
		if (!propParams.containsParameter(name))
			return;
		Site site = new Site(farLoc);
		site.addParameterList(imr.getSiteParams());
		
		String prefix = impl.getShortName()+"["+name+"]";
		
		
		imr.setSite(site);
		imr.setEqkRupture(null);
		Parameter<Double> param = (Parameter<Double>)propParams.getParameter(name);
		Double valueOrig = param.getValue();
		DetectableChangeListener listener = new DetectableChangeListener();
		param.addParameterChangeListener(listener);
		imr.setEqkRupture(finiteRup); // should update prop effects
//		System.out.println("Listener changed for '"+name+"'? "+listener.changed);
		double value1 = param.getValue();
		assertTrue(prefix+" listener wasn't updated after setting eqk rup (w/ site already); value="+value1+", orig="+valueOrig, listener.changed);
		listener.changed = false;
		imr.setSiteLocation(pointLoc); // should also update prop effects
		assertTrue(prefix+" listener wasn't updated after calling setSiteLocation", listener.changed);
		double value2 = param.getValue();
		assertNotEquals(prefix+" value should have changed after calling setSiteLocation", value1, value2, 1e-6);
		if (shouldMatchHorzDist) {
			assertEquals(prefix+" value should be 0 after setting site to pt source loc", 0d, value2, 1e-6);
		}
		site = new Site(farLoc);
		site.addParameterList(imr.getSiteParams());
		listener.changed = false;
		imr.setSite(site); // should also update prop effects
		assertTrue(prefix+" listener wasn't updated after calling setSite", listener.changed);
		double value3 = param.getValue();
		assertNotEquals(prefix+" value should have changed after calling setSiteLocation", value2, value3, 1e-6);
		if (shouldMatchHorzDist) {
			assertNotEquals(prefix+" value shouldn't be 0 after setting site away from pt source loc: "+imr.getSite().getLocation(), 0d, value3, 1e-6);
		}
		
		site = new Site(colocatedLoc);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		double colocatedValue = param.getValue();
		// now test using surface distances directly
		SurfaceDistances colocatedDists = finiteRup.getRuptureSurface().getDistances(colocatedLoc);
		imr.setPropagationEffectParams(colocatedDists);
		double colocatedTestValue = param.getValue();
		assertEquals(prefix+" value should match colocated value after setPropagationEffectParams(colocatedDists)", colocatedValue, colocatedTestValue, 1e-6);
	}
	
	
	private static class DetectableChangeListener implements ParameterChangeListener {
		
		private boolean changed = false;

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			changed = true;
		}
		
	}

}
