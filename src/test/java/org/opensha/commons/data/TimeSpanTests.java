package org.opensha.commons.data;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.TimeSpan;


/**
 * <b>Title:</b> TestLocation<p>
 *
 * <b>Description:>/b> JUnit tester for the Location object. Tests every
 * piece of functionality, included expected fail conditions. If any
 * part of the test fails, the error code is indicated. Useful to ensure
 * the accuracy and weither the class is functioning as expect. Any
 * time in the future if the internal code is changed, this class will
 * verify that the class still works as prescribed. This is called
 * unit testing in software engineering. <p>
 *
 * Note: Requires the JUnit classes to run<p>
 * Note: This class is not needed in production, only for testing.<p>
 *
 * JUnit has gained many supporters, specifically used in ANT which is a java
 * based tool that performs the same function as the make command in unix. ANT
 * is developed under Apache.<p>
 *
 * Any function that begins with test will be executed by JUnit<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class TimeSpanTests
{

	public TimeSpanTests() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testTimeSpan()
	{
		TimeSpan tSpan = new TimeSpan(TimeSpan.YEARS,TimeSpan.YEARS);
		tSpan.setStartTime(1964);
		assertEquals("Year doesn't Match",1964,tSpan.getStartTimeYear());
		tSpan.setStartTimeConstraint("Start Year", 1980,2003);
		tSpan.setStartTime(1984);
		assertEquals("Start Time Doesn't Match",1984,tSpan.getStartTimeYear());

	}

	public void testConstraintCheck()
	{
		TimeSpan tSpan = new TimeSpan(TimeSpan.YEARS,TimeSpan.YEARS);
		try
		{
			tSpan.setStartTimeConstraint("Start Year", -10,2003);
			fail("Should have thrown a constraint exception");
		}
		catch (Exception e)
		{
			assertTrue("Constraint Exception caught as expected",true);
		}
	}
}
