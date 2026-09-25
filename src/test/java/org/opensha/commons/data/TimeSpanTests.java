package org.opensha.commons.data;

import static org.junit.Assert.*;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.commons.param.impl.LongParameter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


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

	@Test
	public void testTypedAPIAndCopy() {
		TimeSpan span = new TimeSpan(StartTimePrecision.MILLISECONDS, DurationUnits.DAYS);
		span.setStartTime(2026, 2, 3, 4, 5, 6, 789);
		span.setDuration(12.5);

		assertEquals(StartTimePrecision.MILLISECONDS, span.getStartTimePrecisionEnum());
		assertEquals(DurationUnits.DAYS, span.getDurationUnitsEnum());

		TimeSpan copy = span.copy();
		assertNotSame(span, copy);
		assertEquals(span.getStartTimePrecisionEnum(), copy.getStartTimePrecisionEnum());
		assertEquals(span.getStartTimeInMillis(), copy.getStartTimeInMillis());
		assertEquals(span.getDuration(), copy.getDuration(), 0d);
		assertEquals(span.getDurationUnitsEnum(), copy.getDurationUnitsEnum());

		copy.setDuration(1d);
		assertEquals(12.5, span.getDuration(), 0d);
	}

	@Test
	public void testJSONRoundTrip() {
		TimeSpan span = new TimeSpan(StartTimePrecision.MILLISECONDS, DurationUnits.YEARS);
		span.setStartTime(2026, 2, 3, 4, 5, 6, 789);
		span.setDuration(50d);

		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(span);
		assertFalse(json.contains("startTimePrecision"));
		assertTrue(json.contains("\"startTimeMillis\""));

		TimeSpan loaded = gson.fromJson(json, TimeSpan.class);
		assertEquals(span.getStartTimePrecisionEnum(), loaded.getStartTimePrecisionEnum());
		assertEquals(span.getStartTimeInMillis(), loaded.getStartTimeInMillis());
		assertEquals(span.getDuration(), loaded.getDuration(), 0d);
		assertEquals(span.getDurationUnitsEnum(), loaded.getDurationUnitsEnum());
	}

	@Test
	public void testYearJSONRoundTrip() {
		TimeSpan span = new TimeSpan(StartTimePrecision.YEARS, DurationUnits.YEARS);
		span.setStartTime(2026);
		span.setDuration(50d);

		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(span);
		assertTrue(json.contains("\"startYear\":2026"));
		assertFalse(json.contains("startTimeMillis"));

		TimeSpan loaded = gson.fromJson(json, TimeSpan.class);
		assertEquals(StartTimePrecision.YEARS, loaded.getStartTimePrecisionEnum());
		assertEquals(2026, loaded.getStartTimeYear());
		assertEquals(50d, loaded.getDuration(), 0d);
	}

	@Test
	public void testTimeIndependentJSONRoundTrip() {
		TimeSpan span = new TimeSpan(StartTimePrecision.NONE, DurationUnits.DAYS);
		span.setDuration(30d);

		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(span);
		assertFalse(json.contains("startTimeMillis"));

		TimeSpan loaded = gson.fromJson(json, TimeSpan.class);
		assertEquals(StartTimePrecision.NONE, loaded.getStartTimePrecisionEnum());
		assertEquals(30d, loaded.getDuration(), 0d);
		assertEquals(DurationUnits.DAYS, loaded.getDurationUnitsEnum());
	}

	@Test
	public void testMillisecondXMLRoundTrip() {
		TimeSpan span = new TimeSpan(StartTimePrecision.MILLISECONDS, DurationUnits.YEARS);
		span.setStartTime(2026, 2, 3, 4, 5, 6, 789);
		span.setDuration(50d);

		Element root = DocumentHelper.createElement("root");
		span.toXMLMetadata(root);
		TimeSpan loaded = TimeSpan.fromXMLMetadata(root.element(TimeSpan.XML_METADATA_NAME));
		assertEquals(StartTimePrecision.MILLISECONDS, loaded.getStartTimePrecisionEnum());
		assertEquals(span.getStartTimeInMillis(), loaded.getStartTimeInMillis());
		assertEquals(span.getDuration(), loaded.getDuration(), 0d);
	}

	@Test
	public void testMillisecondAdjustableParameter() {
		TimeSpan span = new TimeSpan(StartTimePrecision.MILLISECONDS, DurationUnits.DAYS);
		assertTrue(span.getAdjustableParams().containsParameter(TimeSpan.START_TIME_MILLIS));
		assertFalse(span.getAdjustableParams().containsParameter(TimeSpan.START_YEAR));
		long millis = 123456789L;
		((LongParameter)span.getAdjustableParams().getParameter(TimeSpan.START_TIME_MILLIS)).setValue(millis);
		assertEquals(millis, span.getStartTimeInMillis());
	}

	@Test
	public void testLegacyComponentXMLRead() {
		Element xml = DocumentHelper.createElement(TimeSpan.XML_METADATA_NAME);
		xml.addAttribute("startTimePrecision", "Seconds");
		xml.addAttribute("duration", "1.0");
		xml.addAttribute("durationUnits", "Days");
		Element starts = xml.addElement("startTimes");
		starts.addAttribute("StartYear", "2026");
		starts.addAttribute("StartMonth", "2");
		starts.addAttribute("StartDay", "3");
		starts.addAttribute("StartHour", "4");
		starts.addAttribute("StartMinute", "5");
		starts.addAttribute("StartSecond", "6");

		TimeSpan loaded = TimeSpan.fromXMLMetadata(xml);
		assertEquals(StartTimePrecision.MILLISECONDS, loaded.getStartTimePrecisionEnum());
		assertEquals(2026, loaded.getStartTimeYear());
		assertEquals(2, loaded.getStartTimeMonth());
		assertEquals(3, loaded.getStartTimeDay());
		assertEquals(4, loaded.getStartTimeHour());
		assertEquals(5, loaded.getStartTimeMinute());
		assertEquals(6, loaded.getStartTimeSecond());
	}
}
