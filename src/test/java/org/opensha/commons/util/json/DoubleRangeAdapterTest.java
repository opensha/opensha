package org.opensha.commons.util.json;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DoubleRangeAdapterTest {

	private final Gson gson = new GsonBuilder()
			.registerTypeAdapter((Class<Range<Double>>) (Class<?>) Range.class, new DoubleRangeAdapter())
			.create();

	@Test
	public void testClosedOpenRoundTrip() {
		Range<Double> range = Range.closedOpen(-1.25d, 3.5d);
		String json = gson.toJson(range, (Class<Range<Double>>) (Class<?>) Range.class);
		assertTrue(json.contains("\"lower\":-1.25"));
		assertTrue(json.contains("\"lowerType\":\"CLOSED\""));
		assertTrue(json.contains("\"upper\":3.5"));
		assertTrue(json.contains("\"upperType\":\"OPEN\""));

		Range<Double> read = gson.fromJson(json, (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals(range, read);
	}

	@Test
	public void testOmittedEndpointsAreUnbounded() {
		Range<Double> all = gson.fromJson("{}", (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals(Range.all(), all);

		Range<Double> lowerOnly = gson.fromJson(
				"{\"lower\":1.5,\"lowerType\":\"OPEN\"}", (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals(Range.greaterThan(1.5d), lowerOnly);

		Range<Double> upperOnly = gson.fromJson(
				"{\"upper\":8.0,\"upperType\":\"CLOSED\"}", (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals(Range.atMost(8d), upperOnly);
	}

	@Test
	public void testInfiniteBoundsAreOmitted() {
		Range<Double> range = Range.lessThan(Double.POSITIVE_INFINITY);
		String json = gson.toJson(range, (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals("{}", json);

		Range<Double> read = gson.fromJson(json, (Class<Range<Double>>) (Class<?>) Range.class);
		assertEquals(Range.all(), read);
	}
}
