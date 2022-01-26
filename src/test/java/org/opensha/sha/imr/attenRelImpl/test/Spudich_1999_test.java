package org.opensha.sha.imr.attenRelImpl.test;


import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.attenRelImpl.SEA_1999_AttenRel;







/**
 *
 * <p>Title:Spudich_1999_test </p>
 * <p>Description: Checks for the proper implementation of the SEA_1999_AttenRel
 * class.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Ned Field, Nitin Gupta & Vipin Gupta
 * @version 1.0
 */
public class Spudich_1999_test implements ParameterChangeWarningListener {


	SEA_1999_AttenRel spudich_1999 = null;

	private static final String RESULT_SET_PATH = "/org/opensha/sha/imr/attenRelImpl/test/AttenRelResultSetFiles/";
	private static final String Spudich_1999_RESULTS = RESULT_SET_PATH +"Spudich1999.txt";

	//Tolerence to check if the results fall within the range.
	private static double tolerence = .01; //default value for the tolerence

	/**String to see if the user wants to output all the parameter setting for the all the test set
	 * or wants to see only the failed test result values, with the default being only the failed tests
	 **/
	private static String showParamsForTests = "fail"; //other option can be "both" to show all results

	//Instance of the class that does the actual comparison for the AttenuationRelationship classes
	AttenRelResultsChecker attenRelChecker;


	public Spudich_1999_test() {
		this.setUp();
	}

	@Before
	public void setUp() {
		// create the instance of the ShakeMap_2003
		spudich_1999 = new SEA_1999_AttenRel(this);
		attenRelChecker = new AttenRelResultsChecker(spudich_1999,Spudich_1999_RESULTS,tolerence);
	}
	
	@After
	public void tearDown() {
	}

	@Test
	public void testShakeMap2003_Creation() throws IOException {

		boolean result =attenRelChecker.readResultFile();

		/**
		 * If any test for the Spudich-1999 failed
		 */
		if(result == false)
			assertNull(attenRelChecker.getFailedTestParamsSettings(),attenRelChecker.getFailedTestParamsSettings());

		//if the all the succeeds and their is no fail for any test
		else {
			assertTrue("Spudich-1999 Test succeeded for all the test cases",result);
		}
	}

	public void parameterChangeWarning(ParameterChangeWarningEvent e){
		return;
	}

}
