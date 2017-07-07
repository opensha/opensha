package org.opensha.sha.imr.attenRelImpl;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.util.DevStatus;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

@RunWith(Parameterized.class)
public class ProductionIMRsInstantiationTest {
	
	private AttenRelRef impl;
	
	public ProductionIMRsInstantiationTest(AttenRelRef impl) {
		this.impl = impl;
	}
	
	@Parameters
	public static Collection<AttenRelRef[]> data() {
		Set<AttenRelRef> set = AttenRelRef.get(DevStatus.PRODUCTION);
		ArrayList<AttenRelRef[]> ret = new ArrayList<AttenRelRef[]>();
		for (AttenRelRef imr : set) {
			AttenRelRef[] array = { imr };
			ret.add(array);
		}
		return ret;
	}
	
	@Test
	public void testInstantiation() {
		ScalarIMR imr = impl.instance(null);
		assertNotNull("IMR instance returned is NULL!", imr);
		imr.setParamDefaults();
	}

}
