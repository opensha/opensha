package org.opensha.sha.imr.attenRelImpl.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Tests;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_WrapperTest;
import org.opensha.sha.imr.mod.ModAttenuationRelationshipTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	Spudich_1999_test.class,
	Shakemap_2003_test.class,
	Abrahamson_2000_test.class,
	Abrahamson_Silva_1997_test.class,
	BJF_1997_test.class,
	CB_2003_test.class,
	SCEMY_1997_test.class,
	Campbell_1997_test.class,
	Field_2000_test.class,
	AS_2008_test.class,
	BA_2008_test.class,
	CB_2008_test.class,
	CY_2008_test.class,
	NGA08_Site_EqkRup_Tests.class,
	ZhaoEtAl_2006_test.class,
	GeneralIMR_ParameterTests.class,
	MultiIMR_CalcTest.class,
	MultiIMR_ParamTest.class,
	
	NGAW2_WrapperTest.class,
	NGAW2_Tests.class,
	ModAttenuationRelationshipTest.class
})


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class AttenRelTestsSuite {

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(AttenRelTestsSuite.class);
	}
}
