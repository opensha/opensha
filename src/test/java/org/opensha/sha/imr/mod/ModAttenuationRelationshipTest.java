package org.opensha.sha.imr.mod;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.ModAttenuationRelationship;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

public class ModAttenuationRelationshipTest {
	
	private static Site dummySite;
	private static EqkRupture dummyRupture;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		dummySite = new Site(new Location(34, -118));
		
		// add site parameters
		ModAttenuationRelationship modIMR = new ModAttenuationRelationship(null);
		modIMR.setParamDefaults();
		dummySite.addParameterList(modIMR.getSiteParams());
		
		Location rupLoc = new Location(35, -119);
		PointSurface surf = new PointSurface(rupLoc);
		surf.setAveDip(90d);
		dummyRupture = new EqkRupture(7d, 0d, surf, rupLoc);
	}

	@Test
	public void testIMT() {
		ModAttenuationRelationship modIMR = new ModAttenuationRelationship(null);
		
		modIMR.setParamDefaults();
		updateIMR(modIMR, AttenRelRef.CB_2008);
		modIMR.setSite(dummySite);
		modIMR.setEqkRupture(dummyRupture);
		
		ScalarIMR imr = modIMR.getCurrentIMR();
		assertNotNull("IMR is null", imr);
		
		modIMR.setIntensityMeasure(PGA_Param.NAME);
		
		// must call getMean at least once to make it update
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", PGA_Param.NAME, imr.getIntensityMeasure().getName());
		
		// change IMR, make sure still PGA
		updateIMR(modIMR, AttenRelRef.BA_2008);
		imr = modIMR.getCurrentIMR();
		
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", PGA_Param.NAME, imr.getIntensityMeasure().getName());
		
		// now change IMT to SA
		modIMR.setIntensityMeasure(SA_Param.NAME);
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", SA_Param.NAME, imr.getIntensityMeasure().getName());
		
		// now change SA Period
		SA_Param.setPeriodInSA_Param(modIMR.getIntensityMeasure(), 0.01);
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", SA_Param.NAME, imr.getIntensityMeasure().getName());
		assertEquals("Incorrect SA Period", 0.01, SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure()), 1e-14);
		
		// change back to CB 2008, check imt and period set correctly
		updateIMR(modIMR, AttenRelRef.CB_2008);
		imr = modIMR.getCurrentIMR();
		
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", SA_Param.NAME, imr.getIntensityMeasure().getName());
		assertEquals("Incorrect SA Period", 0.01, SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure()), 1e-14);
		
		// change to new IMR, make sure imt and period set correctly
		updateIMR(modIMR, AttenRelRef.CY_2008);
		imr = modIMR.getCurrentIMR();
		
		modIMR.getMean();
		assertNotNull("IMT in sub IMR is null", imr.getIntensityMeasure());
		assertEquals("Incorrect IMT", SA_Param.NAME, imr.getIntensityMeasure().getName());
		assertEquals("Incorrect SA Period", 0.01, SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure()), 1e-14);
	}
	
	@Test
	public void testSetSite() {
		ModAttenuationRelationship modIMR = new ModAttenuationRelationship(null);
		
		modIMR.setParamDefaults();
		modIMR.getParameter(ModAttenuationRelationship.MODS_PARAM_NAME).setValue(ModAttenRelRef.SIMPLE_SCALE);
		updateIMR(modIMR, AttenRelRef.CB_2008);
		modIMR.setSite(dummySite);
		
		ScalarIMR imr = modIMR.getCurrentIMR();
		
		assertEquals("Site update failed in IMR", dummySite, imr.getSite());
		
		// now change IMR, make sure site gets set
		updateIMR(modIMR, AttenRelRef.BA_2008);
		imr = modIMR.getCurrentIMR();
		assertEquals("Site update failed in IMR", dummySite, imr.getSite());
		
		// now set new site and make sure previous IMR gets updated
		Site dummySite2 = new Site(dummySite.getLocation());
		dummySite2.addParameterList(dummySite);
		modIMR.setSite(dummySite2);
		updateIMR(modIMR, AttenRelRef.CB_2008);
		imr = modIMR.getCurrentIMR();
		assertEquals("Site update failed in IMR", dummySite2, imr.getSite());
	}
	
	@Test
	public void testSetRupture() {
		ModAttenuationRelationship modIMR = new ModAttenuationRelationship(null);
		
		modIMR.setParamDefaults();
		modIMR.getParameter(ModAttenuationRelationship.MODS_PARAM_NAME).setValue(ModAttenRelRef.SIMPLE_SCALE);
		updateIMR(modIMR, AttenRelRef.CB_2008);
		modIMR.setEqkRupture(dummyRupture);
		
		ScalarIMR imr = modIMR.getCurrentIMR();
		
		assertEquals("Rupture update failed in IMR", dummyRupture, imr.getEqkRupture());
		
		// now change IMR, make sure site gets set
		updateIMR(modIMR, AttenRelRef.BA_2008);
		imr = modIMR.getCurrentIMR();
		assertEquals("Rupture update failed in IMR", dummyRupture, imr.getEqkRupture());
		
		// now set new site and make sure previous IMR gets updated
		EqkRupture dummyRupture2 = new EqkRupture(7.01d, 0d, dummyRupture.getRuptureSurface(),
				dummyRupture.getHypocenterLocation());
		modIMR.setEqkRupture(dummyRupture2);
		updateIMR(modIMR, AttenRelRef.CB_2008);
		imr = modIMR.getCurrentIMR();
		assertEquals("Rupture update failed in IMR", dummyRupture2, imr.getEqkRupture());
	}
	
	@SuppressWarnings("unchecked")
	private static void updateIMR(ModAttenuationRelationship modIMR, AttenRelRef imr) {
		modIMR.getParameter(ModAttenuationRelationship.IMRS_PARAM_NAME).setValue(imr);
		assertEquals("IMR update failed", imr.getShortName(), modIMR.getCurrentIMR().getShortName());
	}

}
