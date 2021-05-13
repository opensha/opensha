package org.opensha.sha.gui.beans;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ListUtils;
import org.opensha.sha.gui.beans.event.IMTChangeEvent;
import org.opensha.sha.gui.beans.event.IMTChangeListener;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ZhaoEtAl_2006_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.MMI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

public class TestIMT_NewGuiBean implements IMTChangeListener {

	static List<? extends ScalarIMR> imrs;

	Stack<IMTChangeEvent> eventStack = new Stack<IMTChangeEvent>();

	IMT_NewGuiBean gui;

	@BeforeClass
	public static void setUpBeforeClass() {
		//AttenuationRelationshipsInstance inst = new AttenuationRelationshipsInstance();
		imrs = AttenRelRef.instanceList(null, true);
		for (ScalarIMR imr : imrs) {
			imr.setParamDefaults();
		}
	}

	@Before
	public void setUp() throws Exception {
		gui = new IMT_NewGuiBean(imrs);
	}

	@Test
	public void testIMTList() {
		ArrayList<String> supportedIMTs = gui.getSupportedIMTs();
		for (ScalarIMR imr : imrs) {
			for (Parameter<?> imtParam : imr.getSupportedIntensityMeasures()) {
				String imtName = imtParam.getName();
				assertTrue("IMT '" + imtName + "' should be in list!",
						supportedIMTs.contains(imtName));
			}
		}
	}

	@Test
	public void testShowsAllPeriods() {
		gui.setSelectedIMT(SA_Param.NAME);
		assertTrue("SA im should be instance of SA_Param", gui.getSelectedIM() instanceof SA_Param);
		SA_Param saParam = (SA_Param) gui.getSelectedIM();
		PeriodParam periodParam = saParam.getPeriodParam();

		for (ScalarIMR imr : imrs) {
			if (!imr.isIntensityMeasureSupported(SA_Param.NAME))
				continue;
			imr.setIntensityMeasure(SA_Param.NAME);
			SA_Param mySAParam = (SA_Param) imr.getIntensityMeasure();
			PeriodParam myPeriodParam = mySAParam.getPeriodParam();
			for (Double period : myPeriodParam.getAllowedDoubles()) {
				assertTrue("Period '" + period + "' should be supported!", periodParam.isAllowed(period));
			}
		}
	}

	@Test
	public void testShowsSupportedPeriods() {
		gui.setSelectedIMT(SA_Param.NAME);
		assertTrue("SA im should be instance of SA_Param", gui.getSelectedIM() instanceof SA_Param);
		SA_Param saParam = (SA_Param) gui.getSelectedIM();
		PeriodParam periodParam = saParam.getPeriodParam();

		for (ScalarIMR imr : imrs) {
			if (!imr.isIntensityMeasureSupported(SA_Param.NAME))
				continue;
			imr.setIntensityMeasure(SA_Param.NAME);
			SA_Param mySAParam = (SA_Param) imr.getIntensityMeasure();
			PeriodParam myPeriodParam = mySAParam.getPeriodParam();

			gui.setSupportedPeriods(myPeriodParam.getSupportedPeriods());

			for (Double period : periodParam.getAllowedDoubles()) {
				assertTrue("Period '" + period + "' should be supported!", myPeriodParam.isAllowed(period));
			}
		}
	}

	@Test
	public void testIMTChangeEvents() {
		gui.setSelectedIMT(SA_Param.NAME);
		SA_Param saParam = (SA_Param) gui.getSelectedIM();
		PeriodParam periodParam = saParam.getPeriodParam();
		gui.addIMTChangeListener(this);

		assertEquals("Event stack should be empty to start", 0, eventStack.size());

		gui.setSelectedIMT(gui.getSelectedIMT());

		assertEquals("Should not fire event when IMT set to itself", 0, eventStack.size());

		gui.setSelectedIMT(MMI_Param.NAME);

		IMTChangeEvent event;
		assertEquals("Should fire 1 event when IMT changed", 1, eventStack.size());
		event = eventStack.pop();
		assertEquals("IMT change event new val is wrong", MMI_Param.NAME , event.getNewIMT().getName());

		periodParam.setValue(0.1);
		assertEquals("Should not fire event when SA period param change, but IMT is MMI", 0, eventStack.size());

		gui.setSelectedIMT(SA_Param.NAME);
		assertEquals("Should fire 1 event when IMT changed", 1, eventStack.size());
		event = eventStack.pop();
		assertEquals("IMT change event new val is wrong", SA_Param.NAME , event.getNewIMT().getName());

		periodParam.setValue(1.0);
		assertEquals("Should fire 1 event when IMT is SA and period changed", 1, eventStack.size());
		event = eventStack.pop();

		periodParam.setValue(1.0);
		assertEquals("Should not fire event when IMT is SA and period set to itself", 0, eventStack.size());
	}

	@Test
	public void testIMTSetting() {
		HashMap<TectonicRegionType, ScalarIMR> imrMap = 
			new HashMap<TectonicRegionType, ScalarIMR>();

		imrMap.put(TectonicRegionType.ACTIVE_SHALLOW,
				imrs.get(ListUtils.getIndexByName(imrs, CB_2008_AttenRel.NAME)));
		imrMap.put(TectonicRegionType.STABLE_SHALLOW,
				imrs.get(ListUtils.getIndexByName(imrs, CY_2008_AttenRel.NAME)));

		try {
			testSetIMT(PGV_Param.NAME, -1, imrMap);
		} catch (Exception e) {
			fail("Could not set IMT of '"+PGV_Param.NAME+"' in IMRs");
		}

		imrMap.put(TectonicRegionType.SUBDUCTION_INTERFACE,
				imrs.get(ListUtils.getIndexByName(imrs, ZhaoEtAl_2006_AttenRel.NAME)));
		imrMap.put(TectonicRegionType.SUBDUCTION_SLAB,
				imrs.get(ListUtils.getIndexByName(imrs, ZhaoEtAl_2006_AttenRel.NAME)));

		try {
			testSetIMT(SA_Param.NAME, 0.1, imrMap);
		} catch (Exception e) {
			fail("Could not set IMT of '"+SA_Param.NAME+"' in IMRs");
		}
		try {
			testSetIMT(PGA_Param.NAME, -1, imrMap);
		} catch (Exception e) {
			fail("Could not set IMT of '"+PGA_Param.NAME+"' in IMRs");
		}
		
		try {
			testSetIMT(MMI_Param.NAME, -1, imrMap);
			fail("Setting IMT of '"+MMI_Param.NAME+"' in IMRs should fail if it's not supported");
		} catch (Exception e) {}
	}
	
	private void setIMTinGUI(String imtName, double period) {
		gui.setSelectedIMT(imtName);
		if (imtName.equals(SA_Param.NAME))
			gui.getSelectedIM().getIndependentParameter(PeriodParam.NAME).setValue(period);
	}

	private void testSetIMT(String imtName, double period,
			HashMap<TectonicRegionType, ScalarIMR> imrMap) {
		setIMTinGUI(imtName, period);
		gui.setIMTinIMRs(imrMap);

		for (ScalarIMR imr : imrMap.values()) {
			testIMTSetCorrectly(imtName, period, imr);
		}
	}
	
	private void testIMTSetCorrectly(String imtName, double period, ScalarIMR imr) {
		assertEquals("IMT not set properly!", imtName, imr.getIntensityMeasure().getName());
		if (period >= 0) {
			double myPeriod = (Double)((Parameter<Double>)imr.getIntensityMeasure())
			.getIndependentParameter(PeriodParam.NAME).getValue();
			assertEquals("Period not set properly!", period, myPeriod, 0.0);
		}
	}
	
	@Test
	public void testSingleIMR() {
		ScalarIMR cb2008 =
			imrs.get(ListUtils.getIndexByName(imrs, CB_2008_AttenRel.NAME));
		
		gui = new IMT_NewGuiBean(cb2008);
		
		ArrayList<String> supportedIMTs = gui.getSupportedIMTs();
		
		for (Parameter<?> imtParam : cb2008.getSupportedIntensityMeasures()) {
			String imtName = imtParam.getName();
			assertTrue("IMT '" + imtName + "' should be in list!",
					supportedIMTs.contains(imtName));
		}
		
		gui.setIMR(cb2008);
		
		String imtName;
		double period;
		
		imtName = PGA_Param.NAME;
		period = -1;
		setIMTinGUI(imtName, period);
		gui.setIMTinIMR(cb2008);
		testIMTSetCorrectly(imtName, period, cb2008);
		
		imtName = SA_Param.NAME;
		period = 0.1;
		setIMTinGUI(imtName, period);
		gui.setIMTinIMR(cb2008);
		testIMTSetCorrectly(imtName, period, cb2008);
		
		imtName = SA_Param.NAME;
		period = 1.0;
		setIMTinGUI(imtName, period);
		gui.setIMTinIMR(cb2008);
		testIMTSetCorrectly(imtName, period, cb2008);
	}

	@Override
	public void imtChange(IMTChangeEvent e) {
		eventStack.push(e);
	}

}
