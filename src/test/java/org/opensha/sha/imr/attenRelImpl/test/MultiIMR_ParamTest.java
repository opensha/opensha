package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.MultiIMR_Averaged_AttenRel;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.StewartAfshariGoulet2017NonergodicGMPE;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

public class MultiIMR_ParamTest {
	
	private static List<? extends ScalarIMR> imrs;
	private static ArrayList<ArrayList<ScalarIMR>> bundles;
	
	private static int imrs_per_bundle = 3;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
//		AttenuationRelationshipsInstance inst = new AttenuationRelationshipsInstance();
//		imrs = inst.createIMRClassInstance(null);
		imrs = AttenRelRef.instanceList(null, true);
		for (int i=imrs.size()-1; i>=0; i--) {
			ScalarIMR imr = imrs.get(i);
			if (imr instanceof MultiIMR_Averaged_AttenRel)
				imrs.remove(i);
			else if (imr instanceof StewartAfshariGoulet2017NonergodicGMPE)
				imrs.remove(i);
			
			imr.setParamDefaults();
		}
		
//		Collections.shuffle(imrs);
		
		bundles = new ArrayList<ArrayList<ScalarIMR>>();
		
		ArrayList<ScalarIMR> mimrs = null;
		for (int i=0; i<imrs.size(); i++) {
			if (i % imrs_per_bundle == 0) {
				if (mimrs != null) {
					bundles.add(mimrs);
				}
				mimrs = new ArrayList<ScalarIMR>();
			}
			mimrs.add(imrs.get(i));
		}
		if (mimrs.size() > 0)
			bundles.add(mimrs);
		System.out.println("created " + bundles.size() + " bundles!");
	}
	
	@Test
	public void testSetIMTs() {
		for (ArrayList<ScalarIMR> bundle : bundles) {
			MultiIMR_Averaged_AttenRel multi = new MultiIMR_Averaged_AttenRel(bundle);
			
			for (Parameter<?> imt : multi.getSupportedIntensityMeasures()) {
				for (ScalarIMR imr : bundle) {
					assertTrue("IMT '"+imt.getName()+"' is included but not supported by imr '"+imr.getName()+"'",
							imr.isIntensityMeasureSupported(imt));
				}
				multi.setIntensityMeasure(imt);
				for (ScalarIMR imr : bundle) {
					assertEquals("IMT not set correctly!", imt.getName(), imr.getIntensityMeasure().getName());
				}
			}
		}
	}
	
	@Test
	public void testInitialConsist() {
		for (ArrayList<ScalarIMR> bundle : bundles) {
			MultiIMR_Averaged_AttenRel multi = new MultiIMR_Averaged_AttenRel(bundle);
			testParamConsistancy(multi, bundle);
		}
	}
	
	@Test
	public void testChangeProp() {
		for (ArrayList<ScalarIMR> bundle : bundles) {
			MultiIMR_Averaged_AttenRel multi = new MultiIMR_Averaged_AttenRel(bundle);
			trySet(multi, StdDevTypeParam.NAME, StdDevTypeParam.STD_DEV_TYPE_INTRA);
			testParamConsistancy(multi, bundle);
			trySet(multi, StdDevTypeParam.NAME, StdDevTypeParam.STD_DEV_TYPE_INTER);
			testParamConsistancy(multi, bundle);
			trySet(multi, SigmaTruncTypeParam.NAME, SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
			testParamConsistancy(multi, bundle);
			trySet(multi, SigmaTruncLevelParam.NAME, new Double(3.0));
			testParamConsistancy(multi, bundle);
			trySet(multi, SigmaTruncLevelParam.NAME, new Double(2.0));
			testParamConsistancy(multi, bundle);
			trySet(multi, Vs30_Param.NAME, new Double(200.0));
			testParamConsistancy(multi, bundle);
			trySet(multi, DepthTo2pt5kmPerSecParam.NAME, new Double(1.33));
			testParamConsistancy(multi, bundle);
		}
	}
	
	private void trySet(MultiIMR_Averaged_AttenRel multi, String paramName, Object value) {
		try {
			Parameter param = multi.getParameter(paramName);
			if (param.isAllowed(value))
				param.setValue(value);
			else
				System.err.println("Multi imr can't set param '" + paramName + "' to '"+value+"'");
		} catch (ParameterException e) {
			System.err.println("Multi imr doesn't have param '" + paramName + "'");
		}
	}
	
	private void testParamConsistancy(MultiIMR_Averaged_AttenRel multi,
			ArrayList<ScalarIMR> bundle) {
		for (ScalarIMR imr : bundle) {
			testParamConsistancy(multi.getSiteParamsIterator(), imr);
			testParamConsistancy(multi.getOtherParamsIterator(), imr);
			testParamConsistancy(multi.getEqkRuptureParamsIterator(), imr);
		}
	}
	
	private void testParamConsistancy(ListIterator<Parameter<?>> it, ScalarIMR imr2) {
		while (it.hasNext()) {
			Parameter param1 = it.next();
			try {
				Parameter param2 = imr2.getParameter(param1.getName());
				// this imr has it also
				assertEquals("Param '"+param1.getName() +"' not propogated correctly!", param1.getValue(), param2.getValue());
			} catch (ParameterException e) {}
		}
	}

}
