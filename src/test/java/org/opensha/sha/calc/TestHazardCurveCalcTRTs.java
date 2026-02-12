package org.opensha.sha.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.params.NonSupportedTRT_OptionsParam;
import org.opensha.sha.calc.params.SetTRTinIMR_FromSourceParam;
import org.opensha.sha.calc.sourceFilters.params.MaxDistanceParam;
import org.opensha.sha.earthquake.ERFSubset;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 * This tests the hazard curve calculator's handling of the TRT parameters.
 * 
 * All of the tests use a fake ERF, which contains a single source. That source
 * has a single rupture, where the tectonic region is set to the given rupture.
 * This testing with a single rupture allows me to verify that the TRT parameter
 * is being set correctly in the IMR (with the help of parameter change listeners).
 * There is also a fake IMR which takes a list of supported TRT's in its constructor.
 * 
 * @author kevin
 *
 */
public class TestHazardCurveCalcTRTs implements ParameterChangeListener {
	
	private static HashMap<TectonicRegionType, ERFSubset> singleERFMaps;
	private static Site site;
	private static DiscretizedFunc func;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		singleERFMaps = new HashMap<TectonicRegionType, ERFSubset>();
		for (TectonicRegionType trt : TectonicRegionType.values()) {
			ERFSubset erf = getSingleSourceERF(trt);
			singleERFMaps.put(trt, erf);
		}
	}
	
	private static ERFSubset getSingleSourceERF(TectonicRegionType trt) {
		ERFSubset erf = new ERFSubset(new Frankel96_AdjustableEqkRupForecast());
		
		erf.updateForecast();
		erf.includeSource(0);
		erf.getSource(0).setTectonicRegionType(trt);
		
		if (site == null) {
			Location surfLoc = erf.getSource(0).getRupture(0).getRuptureSurface().getLocationsIterator().next();
			Location loc = new Location(surfLoc.getLatitude(), surfLoc.getLongitude());
			site = new Site(loc);
			FakeTRTBasedIMR imr = new FakeTRTBasedIMR(TectonicRegionType.ACTIVE_SHALLOW);
			for (Parameter<?> param : imr.getSiteParams())
				site.addParameter(param);
			IMT_Info imtInfo = new IMT_Info();
			func = imtInfo.getDefaultHazardCurve(imr.getIntensityMeasure());
		}
		
		return erf;
	}
	
	private FakeTRTBasedIMR allIMR;
	private HazardCurveCalculator calc;
	private MaxDistanceParam maxDistanceParam;
	private SetTRTinIMR_FromSourceParam setTRTinIMR_FromSourceParam;
	private NonSupportedTRT_OptionsParam nonSupportedTRT_OptionsParam;
	
	private Stack<ParameterChangeEvent> eventStack = new Stack<ParameterChangeEvent>();

	@Before
	public void setUp() throws Exception {
		ArrayList<TectonicRegionType> trts = new ArrayList<TectonicRegionType>();
		for (TectonicRegionType trt : TectonicRegionType.values())
			trts.add(trt);
		allIMR = new FakeTRTBasedIMR(trts, TectonicRegionType.ACTIVE_SHALLOW);
		
		calc = new HazardCurveCalculator();
		calc.setMaxSourceDistance(MaxDistanceParam.MAX);
		setTRTinIMR_FromSourceParam = (SetTRTinIMR_FromSourceParam)
					calc.getAdjustableParams().getParameter(SetTRTinIMR_FromSourceParam.NAME);
		nonSupportedTRT_OptionsParam = (NonSupportedTRT_OptionsParam)
					calc.getAdjustableParams().getParameter(NonSupportedTRT_OptionsParam.NAME);
	}
	
	/**
	 * This simply tests the case where the TRT isn't supposed to be set by the source, but
	 * rather the IMR's parameter is maintained. It sets the setTRTinIMR_FromSourceParam to
	 * false, then does calculations with ERFs of all sorts of TRTs. With each calculation,
	 * it verifies that the TRT parameter is never changed.
	 */
	@Test
	public void testHCSetBySourceFalse() {
		setTRTinIMR_FromSourceParam.setValue(false);
		
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)allIMR.getParameter(TectonicRegionTypeParam.NAME);
		trtParam.addParameterChangeListener(this);
		
		for (TectonicRegionType trt : TectonicRegionType.values()) {
			assertEquals("event stack should be empty", 0, eventStack.size());
			TectonicRegionType prevTRT = trtParam.getValueAsTRT();
			if (prevTRT != trt) {
				trtParam.setValue(trt);
				eventStack.pop(); // pop the event from the manual set
			}
			for (TectonicRegionType erfTRT : TectonicRegionType.values()) {
				calc.getHazardCurve(func, site, allIMR, singleERFMaps.get(erfTRT));
				assertEquals("event stack should be empty", 0, eventStack.size());
				assertTrue("TRT changed but wasn't supposed to!", trtParam.getValueAsTRT() == trt);
			}
		}
	}
	
	/**
	 * This tests the case where the TRT param should be set by the source, and that TRT is supported by the IMR. 
	 * 
	 * It verifies that the TRT param is being set to the source's TRT (via listeners), and that the TRT param is
	 * set back to the original value at the end of the calculation. 
	 */
	@Test
	public void testHCSetBySourceTrueSupported() {
		setTRTinIMR_FromSourceParam.setValue(true);
		
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)allIMR.getParameter(TectonicRegionTypeParam.NAME);
		
		// make sure that even when you set it from source, the original value is restored
		for (TectonicRegionType origTRT : TectonicRegionType.values()) {
			trtParam.setValue(origTRT);
			for (TectonicRegionType erfTRT : TectonicRegionType.values()) {
				calc.getHazardCurve(func, site, allIMR, singleERFMaps.get(erfTRT));
				assertTrue("TRT changed but wasn't changed back!", trtParam.getValueAsTRT() == origTRT);
			}
		}
		
		// now check that it changed during calculation
		trtParam.addParameterChangeListener(this);
		for (TectonicRegionType origTRT : TectonicRegionType.values()) {
			assertEquals("event stack should be empty", 0, eventStack.size());
			TectonicRegionType prevTRT = trtParam.getValueAsTRT();
			if (prevTRT != origTRT) {
				trtParam.setValue(origTRT);
				eventStack.pop(); // pop the event from the manual set
			}
			for (TectonicRegionType erfTRT : TectonicRegionType.values()) {
				assertEquals("event stack should be empty", 0, eventStack.size());
				calc.getHazardCurve(func, site, allIMR, singleERFMaps.get(erfTRT));
				if (origTRT == erfTRT) {
					assertEquals("event stack should be empty", 0, eventStack.size());
				} else {
					assertEquals("should have fired 2 events", 2, eventStack.size());
//					System.out.println("orig=" + origTRT + " erf="+erfTRT);
					ParameterChangeEvent event2 = eventStack.pop(); // the 2nd change, changing back to orig
					ParameterChangeEvent event1 = eventStack.pop(); // the 1st change, changing to ERF
//					System.out.println("event2: new=" + event2.getNewValue() + " old=" + event2.getOldValue());
//					System.out.println("event1: new=" + event1.getNewValue() + " old=" + event1.getOldValue());
					assertNotNull(event2.getNewValue());
					assertTrue("2nd event should have set back to orig", event2.getNewValue().equals(origTRT.toString()));
					assertNotNull(event1.getNewValue());
					assertTrue("1st event should have set to erf's TRT", event1.getNewValue().equals(erfTRT.toString()));
				}
				assertTrue("TRT changed but wasn't changed back!", trtParam.getValueAsTRT() == origTRT);
			}
		}
	}
	
	/**
	 * This tests when the nonSupportedTRT_OptionsParam is set to use the default value 
	 * 
	 * it does calculations with multiple unsupported TRT's, and verifies via listeners that the
	 * value used for calculation is always the default value for the TRT param 
	 */
	@Test
	public void testHCSetBySourceUnsupportedAsDefault() {
		setTRTinIMR_FromSourceParam.setValue(true);
		
		ArrayList<TectonicRegionType> subdTypes = new ArrayList<TectonicRegionType>();
		subdTypes.add(TectonicRegionType.SUBDUCTION_INTERFACE);
		subdTypes.add(TectonicRegionType.SUBDUCTION_SLAB);
		FakeTRTBasedIMR subdIMR = new FakeTRTBasedIMR(subdTypes, TectonicRegionType.SUBDUCTION_INTERFACE);
		
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)subdIMR.getParameter(TectonicRegionTypeParam.NAME);
		trtParam.setValueAsDefault();
		trtParam.addParameterChangeListener(this);
		assertEquals("event stack should be empty", 0, eventStack.size());
		
		nonSupportedTRT_OptionsParam.setValue(NonSupportedTRT_OptionsParam.USE_DEFAULT);
		TectonicRegionType erfTRT = TectonicRegionType.ACTIVE_SHALLOW;
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(erfTRT));
		assertEquals("event stack should be empty", 0, eventStack.size());
		assertTrue("TRT should still be default", trtParam.getValueAsTRT() == TectonicRegionType.SUBDUCTION_INTERFACE);
		
		trtParam.setValue(TectonicRegionType.SUBDUCTION_SLAB);
		eventStack.pop();
		assertEquals("event stack should be empty", 0, eventStack.size());
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(erfTRT));
		assertEquals("event stack should contain 2 events", 2, eventStack.size());
		ParameterChangeEvent event2 = eventStack.pop(); // the 2nd change, changing back to orig
		ParameterChangeEvent event1 = eventStack.pop(); // the 1st change, setting as default
		assertNotNull(event2.getNewValue());
		assertTrue("2nd event should have set back to orig",
				event2.getNewValue().equals(TectonicRegionType.SUBDUCTION_SLAB.toString()));
		assertNotNull(event1.getNewValue());
		assertTrue("1st event should have set to erf's TRT",
				event1.getNewValue().equals(TectonicRegionType.SUBDUCTION_INTERFACE.toString()));
		assertTrue("TRT should still be default", trtParam.getValueAsTRT() == TectonicRegionType.SUBDUCTION_SLAB);
		
		assertEquals("event stack should be empty", 0, eventStack.size());
	}
	
	/**
	 * same as testHCSetBySourceUnsupportedAsDefault, except it tests that the original value of the
	 * IMR's TRT paramis used for calculation when nonSupportedTRT_OptionsParam is set as such. 
	 */
	@Test
	public void testHCSetBySourceUnsupportedUseOrig() {
		setTRTinIMR_FromSourceParam.setValue(true);
		
		ArrayList<TectonicRegionType> subdTypes = new ArrayList<TectonicRegionType>();
		subdTypes.add(TectonicRegionType.SUBDUCTION_INTERFACE);
		subdTypes.add(TectonicRegionType.SUBDUCTION_SLAB);
		FakeTRTBasedIMR subdIMR = new FakeTRTBasedIMR(subdTypes, TectonicRegionType.SUBDUCTION_INTERFACE);
		
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)subdIMR.getParameter(TectonicRegionTypeParam.NAME);
		trtParam.setValueAsDefault();
		trtParam.addParameterChangeListener(this);
		assertEquals("event stack should be empty", 0, eventStack.size());
		
		TectonicRegionType erfTRT = TectonicRegionType.ACTIVE_SHALLOW;
		
		nonSupportedTRT_OptionsParam.setValue(NonSupportedTRT_OptionsParam.USE_ORIG);
		
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(erfTRT));
		assertEquals("event stack should be empty", 0, eventStack.size());
		assertTrue("TRT should still be orig", trtParam.getValueAsTRT() == TectonicRegionType.SUBDUCTION_INTERFACE);
		
		trtParam.setValue(TectonicRegionType.SUBDUCTION_SLAB);
		eventStack.pop();
		assertEquals("event stack should be empty", 0, eventStack.size());
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(erfTRT));
		assertEquals("event stack should be empty", 0, eventStack.size());
		assertTrue("TRT should still be orig", trtParam.getValueAsTRT() == TectonicRegionType.SUBDUCTION_SLAB);
	}
	
	/**
	 * this tests when nonSupportedTRT_OptionsParam is set to throw an exception on unsupported TRT's 
	 * 
	 * it simply checks that an exception is thrown in the unsupported case, and is not thrown when it's supported.
	 */
	@Test
	public void testHCSetBySourceUnsupportedThrow() {
		setTRTinIMR_FromSourceParam.setValue(true);
		
		ArrayList<TectonicRegionType> subdTypes = new ArrayList<TectonicRegionType>();
		subdTypes.add(TectonicRegionType.SUBDUCTION_INTERFACE);
		subdTypes.add(TectonicRegionType.SUBDUCTION_SLAB);
		FakeTRTBasedIMR subdIMR = new FakeTRTBasedIMR(subdTypes, TectonicRegionType.SUBDUCTION_INTERFACE);
		
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)subdIMR.getParameter(TectonicRegionTypeParam.NAME);
		trtParam.setValueAsDefault();
		
		TectonicRegionType erfTRT = TectonicRegionType.ACTIVE_SHALLOW;
		
		nonSupportedTRT_OptionsParam.setValue(NonSupportedTRT_OptionsParam.THROW);
		
		// shouldn't throw exceptoin
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(TectonicRegionType.SUBDUCTION_INTERFACE));
		
		// shouldn't throw exceptoin
		calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(TectonicRegionType.SUBDUCTION_SLAB));
		
		// should throw exceptoin
		try {
			calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(TectonicRegionType.ACTIVE_SHALLOW));
			fail("should have thrown exception!");
		} catch (RuntimeException e) {}
		
		// should throw exceptoin
		try {
			calc.getHazardCurve(func, site, subdIMR, singleERFMaps.get(TectonicRegionType.STABLE_SHALLOW));
			fail("should have thrown exception!");
		} catch (RuntimeException e) {}
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
//		System.out.println("Pushing event: new=" + event.getNewValue() + " old=" + event.getOldValue());
		eventStack.push(event);
	}

}
