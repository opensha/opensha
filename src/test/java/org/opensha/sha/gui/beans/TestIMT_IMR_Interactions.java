package org.opensha.sha.gui.beans;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ListUtils;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean.ChooserComboBox;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ZhaoEtAl_2006_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.MMI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

public class TestIMT_IMR_Interactions {

	static List<? extends ScalarIMR> imrs;
	static ArrayList<TectonicRegionType> demoTRTs;

	IMR_MultiGuiBean imrGui;
	IMT_NewGuiBean imtGui;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		//AttenuationRelationshipsInstance inst = new AttenuationRelationshipsInstance();
		imrs =  AttenRelRef.instanceList(null, true);
		for (ScalarIMR imr : imrs)
			imr.setParamDefaults();
		demoTRTs = new ArrayList<TectonicRegionType>();
		demoTRTs.add(TectonicRegionType.ACTIVE_SHALLOW);
		demoTRTs.add(TectonicRegionType.STABLE_SHALLOW);
		demoTRTs.add(TectonicRegionType.SUBDUCTION_INTERFACE);
		demoTRTs.add(TectonicRegionType.SUBDUCTION_SLAB);
	}

	@Before
	public void setUp() throws Exception {
		imrGui = new IMR_MultiGuiBean(imrs);
		imtGui = new IMT_NewGuiBean(imrGui);
	}

	@Test
	public void testIMTList() {
		ArrayList<String> supportedIMTs = imtGui.getSupportedIMTs();
		for (ScalarIMR imr : imrGui.getIMRs()) {
			for (Parameter<?> imtParam : imr.getSupportedIntensityMeasures()) {
				String imtName = imtParam.getName();
				assertTrue("IMT '" + imtName + "' should be in list!",
						supportedIMTs.contains(imtName));
			}
		}
	}

	@Test
	public void testSingleIMRPeriods() {
		imtGui.setSelectedIMT(SA_Param.NAME);

		for (ScalarIMR imr : imrs) {
			if (imr.isIntensityMeasureSupported(SA_Param.NAME)) {
				imrGui.setSelectedSingleIMR(imr.getName());

				SA_Param saParam = (SA_Param) imtGui.getSelectedIM();
				PeriodParam periodParam = saParam.getPeriodParam();

				if (!imr.isIntensityMeasureSupported(SA_Param.NAME))
					continue;

				imr.setIntensityMeasure(SA_Param.NAME);
				SA_Param mySAParam = (SA_Param) imr.getIntensityMeasure();
				PeriodParam myPeriodParam = mySAParam.getPeriodParam();

				for (Double period : periodParam.getAllowedDoubles()) {

					assertTrue("Period (" + period + ") in IMT gui not supported by selected IMR!"
							+ " (" + imr.getName() + ")",
							myPeriodParam.isAllowed(period));
				}

				for (Double period : myPeriodParam.getAllowedDoubles()) {

					assertTrue("Period in selected IMR listed in IMT gui!",
							periodParam.isAllowed(period));
				}
			} else {
				try {
					imrGui.setSelectedSingleIMR(imr.getName());
					fail("Exception not thrown setting IMR to one that doesn't support SA while SA selected");
				} catch (Exception e) {
					// should throw exception
				}
			}
			
		}
	}

	@Test
	public void testMultipleIMRPeriods() {
		imrGui.setTectonicRegions(demoTRTs);

		imrGui.setMultipleIMRs(true);

		imrGui.setIMR(CB_2008_AttenRel.NAME, TectonicRegionType.ACTIVE_SHALLOW);
		imrGui.setIMR(CY_2008_AttenRel.NAME, TectonicRegionType.STABLE_SHALLOW);
		imrGui.setIMR(ZhaoEtAl_2006_AttenRel.NAME, TectonicRegionType.SUBDUCTION_INTERFACE);
		imrGui.setIMR(ZhaoEtAl_2006_AttenRel.NAME, TectonicRegionType.SUBDUCTION_SLAB);

		imtGui.setSelectedIMT(SA_Param.NAME);

		SA_Param saParam = (SA_Param) imtGui.getSelectedIM();
		PeriodParam periodParam = saParam.getPeriodParam();

		for (ScalarIMR imr : imrGui.getIMRMap().values()) {
			imr.setIntensityMeasure(SA_Param.NAME);
			SA_Param mySAParam = (SA_Param) imr.getIntensityMeasure();
			PeriodParam myPeriodParam = mySAParam.getPeriodParam();

			for (Double period : periodParam.getAllowedDoubles()) {
				assertTrue("One of the IMT gui periods isn't supproted by a selected IMR!",
						myPeriodParam.isAllowed(period));
			}
		}
	}
	
	@Test
	public void testSetMMIWithNonMMI_IMRSelected() {
		// this tests settting IMT to SA with an IMR selected that doesn't support SA
		
		// first make sure the current IMR doesn't allow MMI
		if (imrGui.getSelectedIMR().isIntensityMeasureSupported(MMI_Param.NAME)) {
			imrGui.setSelectedSingleIMR(CB_2008_AttenRel.NAME);
		}
		
		assertFalse("CB 2008 shouldn't support MMI", imrGui.getSelectedIMR().isIntensityMeasureSupported(MMI_Param.NAME));
		
		imtGui.setSelectedIMT(MMI_Param.NAME);
		assertTrue("IMT changed to MMI, but selected IMR doesn't support it and was not changed" +
				" (but should have been).",
				imrGui.getSelectedIMR().isIntensityMeasureSupported(MMI_Param.NAME));
		
		imtGui.setSelectedIMT(SA_Param.NAME);
	}
	
	@Test
	public void testSetSAWithNonSAIMRSelected() {
		// this tests settting IMT to SA with an IMR selected that doesn't support SA
		
		if (!imtGui.getSupportedIMTs().contains(SA_InterpolatedParam.NAME))
			// if interpolated SA isn't in the list, skip this test.
			return;
		imtGui.setSelectedIMT(SA_InterpolatedParam.NAME);
		assertTrue("IMT changed to interpolated, but selected IMR doesn't support it and was not changed.",
				imrGui.getSelectedIMR().isIntensityMeasureSupported(SA_InterpolatedParam.NAME));
		
		imtGui.setSelectedIMT(SA_Param.NAME);
	}

	
	@Test
	public void testChooserSetToDisabled() {
		imtGui.setSelectedIMT(MMI_Param.NAME);
		ChooserComboBox chooser = imrGui.getChooser(null);
		int bjf97Index = ListUtils.getIndexByName(imrs, BJF_1997_AttenRel.NAME);
		String mmiIMRName = imrGui.getSelectedIMR().getName();
		chooser.setSelectedIndex(bjf97Index);
		assertFalse("IMR should not be BJF when it's disabled...should revert to previously selected!"
				, imrGui.getSelectedIMR().getName().equals(BJF_1997_AttenRel.NAME));
		assertTrue("IMR should be reset to previous if disabled IMR selected.",
				imrGui.getSelectedIMR().getName().equals(mmiIMRName));
		System.out.println(imrGui.getSelectedIMR().getName());
	}
	
	@Test
	public void testIMRDisabling() {
		for (String imtName : imtGui.getSupportedIMTs()) {
//			System.out.println("setting to " + imtName + " currently: " + imtGui.getSelectedIMT());
			imtGui.setSelectedIMT(imtName);
			
			for (ScalarIMR imr : imrGui.getIMRs()) {
				boolean shouldBeEnabled = imr.isIntensityMeasureSupported(imtName);
				assertTrue("IMR enabled is inconsistent!",
						shouldBeEnabled == imrGui.isIMREnabled(imr.getName()));
				if (!shouldBeEnabled) {
					try {
						imrGui.setSelectedSingleIMR(imr.getName());
						System.out.println(imr.getName() + " " + imtName);
						fail("Shouldn't be able to set IMR if it doesn't support the IMT in the GUI!");
					} catch (Exception e) {}
				}
			}
		}
	}

}
