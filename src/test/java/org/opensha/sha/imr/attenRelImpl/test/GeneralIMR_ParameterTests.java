package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DevStatus;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RakeParam;

import com.google.common.collect.Lists;

/**
 * This class does general parameter setting tests for each IMR included in our applications.
 * For example, it tests that distance metrics are calculated correctly when site/ruptures are
 * set. It also tests that all parameters are initialized when setParamDefaults is called. 
 * 
 * @author kevin
 *
 */
@RunWith(Parameterized.class)
public class GeneralIMR_ParameterTests {
	
	private static EqkRupture rup1;
	private static EqkRupture rup2;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		
		StirlingGriddedSurface surface1 = mkRupSurface(new Location(32.75, -119.4));
		StirlingGriddedSurface surface2 = mkRupSurface(new Location(35.34, -117.1));
		
//		System.out.println(surface.toString());
		rup1 = new EqkRupture();
		rup1.setRuptureSurface(surface1);
		rup1.setMag(7.0);
		rup1.setAveRake(180.0);
		rup1.setHypocenterLocation(surface1.get(0, 0));
		
		rup2 = new EqkRupture();
		rup2.setRuptureSurface(surface2);
		rup2.setMag(7.3);
		rup2.setAveRake(180.0);
		rup2.setHypocenterLocation(surface2.get(0, 0));
	}
	
	private static StirlingGriddedSurface mkRupSurface(Location startPt) {
		double dip = 60;
		double upperSeisDepth = 5;
		double faultDDW = 10;
		double lowerSeisDepth = upperSeisDepth + faultDDW*Math.sin(Math.toRadians(dip)); // Down-dip width = 10km
		
		FaultTrace trace = new FaultTrace("test trace");
		trace.add(startPt);
		trace.add(new Location(startPt.getLatitude() + 0.35, startPt.getLongitude() - 0.1));
		trace.add(new Location(startPt.getLatitude() + 0.85, startPt.getLongitude() + 0.2));
		return new StirlingGriddedSurface(trace, dip, upperSeisDepth, lowerSeisDepth, 0.1);
	}

	@Parameters
	public static Collection<AttenRelRef[]> data() {
		List<AttenRelRef> refs = Lists.newArrayList(AttenRelRef.get(DevStatus.PRODUCTION, DevStatus.DEVELOPMENT));
		Collections.sort(refs, new NamedComparator());

		ArrayList<AttenRelRef[]> ret = new ArrayList<AttenRelRef[]>();

		for (AttenRelRef ref : refs) {
			AttenRelRef[] theRef = { ref };
			ret.add(theRef);
		}

		return ret;
	}

	private String name;
	private String shortName;
	private AttenRelRef ref;

	public GeneralIMR_ParameterTests(AttenRelRef ref) {
		this.ref = ref;
		this.name = ref.getName();
		this.shortName = ref.getShortName();
	}

	private static void addAllForIt(ParameterList list, Iterator<Parameter<?>> it) {
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			if (!list.containsParameter(param))
				list.addParameter(param);
		}
	}

	private static ParameterList getAllIMRParams(ScalarIMR imr) {
		ParameterList list = new ParameterList();

		try {
			list.addParameter(imr.getParameter(AbstractIMR.EXCEED_PROB_NAME));
		} catch (Exception e) {}

		try {
			addAllForIt(list, imr.getSiteParamsIterator());
		} catch (Exception e) {}
		try {
			addAllForIt(list, imr.getEqkRuptureParamsIterator());
		} catch (Exception e) {}
		try {
			addAllForIt(list, imr.getPropagationEffectParamsIterator());
		} catch (Exception e) {}
		//		addAllForIt(list, imr.getSupportedIntensityMeasuresIterator());
		try {
			addAllForIt(list, imr.getOtherParamsIterator());
		} catch (Exception e) {}

		return list;
	}

	@Test
	public void testParamsInit() {
		ScalarIMR imr = ref.instance(null);
		imr.setParamDefaults();

		ParameterList params = getAllIMRParams(imr);

		for (Parameter<?> param : params) {
			if (param.getValue() == null) {
				assertTrue(shortName+": param '"+param.getName()
						+"' value is null, but null isn't allowed!", param.isNullAllowed());
			} else if (param.getDefaultValue() != null) {
				assertEquals(shortName+": param '"+param.getName()+"' value is not set to default " +
						"when setParamDefaults() called." +
						"\ndefault: "+param.getDefaultValue()+
						"\nvalue: "+param.getValue(),param.getDefaultValue(), param.getValue());
			}
		}
	}
	
	@Test
	public void testSupportedIMs() {
		ScalarIMR imr = ref.instance(null);
		imr.setParamDefaults();
		for (Parameter<?> im : imr.getSupportedIntensityMeasures()) {
			try {
				imr.setIntensityMeasure(im);
			} catch (ParameterException e) {
				fail(shortName+": IM '"+im.getName()+"' is in supproted list but can't be set!");
			}
		}
	}
	
	private Site createSite(ScalarIMR imr, Location loc) {
		Site site = new Site(loc);
		
		for (Parameter<?> param : imr.getSiteParams()) {
			param = (Parameter<?>) param.clone();
			if (param.getValue() != null && param instanceof DoubleParameter) {
				DoubleParameter dparam = (DoubleParameter)param;
				double rVal = Math.random();
				if (dparam.isAllowed(dparam.getValue() + rVal))
					try {
						dparam.setValue(dparam.getValue() + rVal);
					} catch (WarningException e) {}
				else if (dparam.isAllowed(dparam.getValue() - rVal))
					try {
						dparam.setValue(dparam.getValue() - rVal);
					} catch (WarningException e) {}
			}
			site.addParameter(param);
		}
		
		return site;
	}
	

	
	@Test
	public void testSetSiteRup() {
		ScalarIMR imr = ref.instance(null);
		imr.setParamDefaults();
		
		Parameter<?> im = imr.getSupportedIntensityMeasuresIterator().next();
		
		Site site = createSite(imr, new Location(34, -118));
		
		imr.setSite(site);
		assertEquals(shortName+": setSite didn't change the site object", site, imr.getSite());
		
		Site site2 = createSite(imr, new Location(35.1, -118.5));
		
		imr.setSite(site2);
		assertEquals(shortName+": setSite didn't change the site object", site2, imr.getSite());
		
		imr.setEqkRupture(rup2);
		assertEquals(shortName+": setSite didn't change the site object", rup2, imr.getEqkRupture());
		
		imr.setEqkRupture(rup1);
		
		imr.setSite(site);
		imr.setEqkRupture(rup2);
		
		imr.setAll(rup1, site2, im);
		assertEquals(shortName+": setAll didn't change the site object", site2, imr.getSite());
		
	}
	
	private void verifyRupParams(ScalarIMR imr, EqkRupture rup) {
		RuptureSurface surf = rup.getRuptureSurface();
		try {
			DipParam dipParam = (DipParam)imr.getParameter(DipParam.NAME);
			assertEquals(shortName+": dip not set correctly",
					surf.getAveDip(), dipParam.getValue().doubleValue(), 0.00001);
		} catch (ParameterException e) {}
		
		try {
			MagParam magParam = (MagParam)imr.getParameter(MagParam.NAME);
			assertEquals(shortName+": magnitude not set correctly",
					rup.getMag(), magParam.getValue().doubleValue(), 0.00001);
		} catch (ParameterException e) {}
		
		try {
			RakeParam rakeParam = (RakeParam)imr.getParameter(RakeParam.NAME);
			assertEquals(shortName+": rake not set correctly",
					rup.getAveRake(), rakeParam.getValue().doubleValue(), 0.00001);
		} catch (ParameterException e) {}
	}
	
	/**
	 * this tests that rupture parameters are correctly set when setEqkRupture is called
	 */
	@Test
	public void testSetRupParams() {
		ScalarIMR imr = ref.instance(null);
		imr.setParamDefaults();
		
		imr.setEqkRupture(rup1);
		verifyRupParams(imr, rup1);
		
		imr.setEqkRupture(rup2);
		verifyRupParams(imr, rup2);
		
		imr.setEqkRupture(rup1);
		verifyRupParams(imr, rup1);
	}
	
	private void verifySiteParams(ScalarIMR imr, Site site) {
		Iterator<Parameter<?>> it = site.getParametersIterator();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			
			assertEquals(shortName+": param '"+param.getName()+"' not set with setSite",
					param.getValue(), imr.getParameter(param.getName()).getValue());
		}
	}
	
	@Test
	public void testSetSiteParams() {
		ScalarIMR imr = ref.instance(null);
		imr.setParamDefaults();
		
		Site site1 = createSite(imr, new Location(34, -118));
		Site site2 = createSite(imr, new Location(34.5, -118.3));
		
		imr.setSite(site1);
		verifySiteParams(imr, site1);
		
		imr.setSite(site2);
		verifySiteParams(imr, site2);
		
		imr.setSite(site1);
		verifySiteParams(imr, site1);
	}

}
