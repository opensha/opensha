package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;







/**
 *
 * <p>Title:Shakemap_2003_test </p>
 * <p>Description: Checks for the proper implementation of the Shakemap_2003_AttenRel
 * class.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Ned Field, Nitin Gupta & Vipin Gupta
 * @version 1.0
 */
public class Shakemap_2003_test implements ParameterChangeWarningListener {


	ShakeMap_2003_AttenRel shakemap_2003 = null;

	private static final String RESULT_SET_PATH = "/org/opensha/sha/imr/attenRelImpl/test/AttenRelResultSetFiles/";
	private static final String ShakeMap_2003_RESULTS = RESULT_SET_PATH +"shakemap2003.txt";

	//Tolerence to check if the results fall within the range.
	private static double tolerence = .01; //default value for the tolerence

	/**String to see if the user wants to output all the parameter setting for the all the test set
	 * or wants to see only the failed test result values, with the default being only the failed tests
	 **/
	private static String showParamsForTests = "fail"; //other option can be "both" to show all results

	//Instance of the class that does the actual comparison for the AttenuationRelationship classes
	AttenRelResultsChecker attenRelChecker;


	public Shakemap_2003_test() {
	}

	@Before
	public void setUp() {
		// create the instance of the ShakeMap_2003
		shakemap_2003 = new ShakeMap_2003_AttenRel(this);
		attenRelChecker = new AttenRelResultsChecker(shakemap_2003,this.ShakeMap_2003_RESULTS,this.tolerence);
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testShakeMap2003_Creation() throws IOException {

		boolean result =attenRelChecker.readResultFile();

		/**
		 * If any test for the ShakeMap-2003 failed
		 */
		if(result == false)
			assertNull(attenRelChecker.getFailedTestParamsSettings(),attenRelChecker.getFailedTestParamsSettings());

		//if the all the succeeds and their is no fail for any test
		else {
			assertTrue("ShakeMap-2003 Test succeeded for all the test cases",result);
		}
	}

	public void parameterChangeWarning(ParameterChangeWarningEvent e){
		return;
	}


	/**
	 * Run the test case
	 * @param args
	 */

	public static void main (String[] args)
	{
		org.junit.runner.JUnitCore.runClasses(Shakemap_2003_test.class);
	}

}
