package org.opensha.sha.earthquake.faultSysSolution.hazard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.HazardCurveMetadata.TimeDependence;

public class HazardCurveMetadataTest {

	@Test
	public void testTimeDependentRoundTripAndDefensiveCopy() throws Exception {
		HazardCurveMetadata metadata = timeDependent(2026, 50d);
		TimeSpan span = metadata.getTimeSpan();
		span.setDuration(1d);

		assertEquals(TimeDependence.TIME_DEPENDENT, metadata.getTimeDependence());
		assertFalse(metadata.hasTimeIndependentCurves());
		assertTrue(metadata.hasTimeDependentCurves());
		assertEquals(50d, metadata.getDurationYears(), 0d);

		TimeSpan metadataSpan = metadata.getTimeSpan();
		assertNotSame(span, metadataSpan);
		metadataSpan.setDuration(2d);
		assertEquals(50d, metadata.getDurationYears(), 0d);

		File file = File.createTempFile("hazard_curve_metadata", ".json");
		try {
			metadata.write(file);
			HazardCurveMetadata loaded = HazardCurveMetadata.read(file);
			assertEquals(TimeDependence.TIME_DEPENDENT, loaded.getTimeDependence());
			assertEquals(metadata.getForecastStartTimeMillis(), loaded.getForecastStartTimeMillis());
			assertEquals(50d, loaded.getDurationYears(), 0d);
		} finally {
			file.delete();
		}
	}

	@Test
	public void testTimeIndependentMetadata() {
		HazardCurveMetadata metadata = HazardCurveMetadata.timeIndependent(1d);
		assertEquals(TimeDependence.TIME_INDEPENDENT, metadata.getTimeDependence());
		assertTrue(metadata.hasTimeIndependentCurves());
		assertFalse(metadata.hasTimeDependentCurves());
		assertNull(metadata.getForecastStartTimeMillis());
	}

	@Test
	public void testDurationComparisonUsesTemporalMeaning() {
		TimeSpan years = new TimeSpan(DurationUnits.YEARS);
		years.setDuration(1d);
		TimeSpan days = new TimeSpan(DurationUnits.DAYS);
		days.setDuration(365.25d);
		assertTrue(new HazardCurveMetadata(years).isSameDuration(new HazardCurveMetadata(days)));

		days.setDuration(365d);
		assertFalse(new HazardCurveMetadata(years).isSameDuration(new HazardCurveMetadata(days)));
	}

	@Test
	public void testMerge() {
		HazardCurveMetadata td = timeDependent(2026, 50d);
		HazardCurveMetadata mixed = td.merge(HazardCurveMetadata.timeIndependent(50d));
		assertEquals(TimeDependence.MIXED, mixed.getTimeDependence());
		assertTrue(mixed.hasTimeIndependentCurves());
		assertTrue(mixed.hasTimeDependentCurves());
		assertEquals(td.getForecastStartTimeMillis(), mixed.getForecastStartTimeMillis());

		HazardCurveMetadata merged = HazardCurveMetadata.merge(Arrays.asList(
				HazardCurveMetadata.timeIndependent(50d), td, HazardCurveMetadata.timeIndependent(50d)));
		assertEquals(TimeDependence.MIXED, merged.getTimeDependence());
	}

	@Test
	public void testMergeRejectsDifferentDurationOrTDStart() {
		assertMergeFails(timeDependent(2026, 50d), timeDependent(2026, 30d));
		assertMergeFails(timeDependent(2026, 50d), timeDependent(2014, 50d));
		assertMergeFails(HazardCurveMetadata.timeIndependent(1d), HazardCurveMetadata.timeIndependent(50d));
	}

	@Test
	public void testComparisonRules() {
		HazardCurveMetadata td50_2026 = timeDependent(2026, 50d);
		assertTrue(td50_2026.isComparable(HazardCurveMetadata.timeIndependent(1d)));
		assertTrue(td50_2026.isComparable(HazardCurveMetadata.timeIndependent(30d)));
		assertTrue(td50_2026.isComparable(timeDependent(2014, 50d)));
		assertFalse(td50_2026.isComparable(timeDependent(2026, 30d)));
		assertTrue(HazardCurveMetadata.timeIndependent(1d)
				.isComparable(HazardCurveMetadata.timeIndependent(50d)));

		assertTrue(HazardCurveMetadata.areComparable(Arrays.asList(HazardCurveMetadata.timeIndependent(1d),
				td50_2026, timeDependent(2014, 50d), HazardCurveMetadata.timeIndependent(30d))));
		assertFalse(HazardCurveMetadata.areComparable(Arrays.asList(td50_2026, timeDependent(2014, 30d))));
	}

	@Test
	public void testArchiveRoundTrip() throws Exception {
		HazardCurveMetadata metadata = timeDependent(2026, 50d)
				.merge(HazardCurveMetadata.timeIndependent(50d));
		ArchiveOutput.InMemoryZipOutput output = new ArchiveOutput.InMemoryZipOutput(true, 1024);
		metadata.write(output);
		output.close();
		try (ArchiveInput input = output.getCompletedInput()) {
			assertTrue(input.hasEntry(HazardCurveMetadata.FILE_NAME));
			HazardCurveMetadata loaded = HazardCurveMetadata.read(input);
			assertEquals(TimeDependence.MIXED, loaded.getTimeDependence());
			assertEquals(metadata.getForecastStartTimeMillis(), loaded.getForecastStartTimeMillis());
			assertEquals(50d, loaded.getDurationYears(), 0d);
		}
	}

	@Test
	public void testMarkdownTimeSpanSummary() {
		assertTrue(HazardCurveMetadata.buildTimeSpanSummary("Primary",
				HazardCurveMetadata.timeIndependent(1d), "Comparison",
				HazardCurveMetadata.timeIndependent(50d)).isEmpty());

		HazardCurveMetadata mixed = timeDependent(2026, 50d)
				.merge(HazardCurveMetadata.timeIndependent(50d));
		List<String> lines = HazardCurveMetadata.buildTimeSpanSummary("Primary", mixed,
				"Comparison", HazardCurveMetadata.timeIndependent(1d));
		assertTrue(lines.contains("**Hazard Curve Time Span Summary**"));
		assertTrue(lines.contains("| Primary | Mixed (time-independent and time-dependent) | 50 years | 2026 |"));
		assertTrue(lines.contains("| Comparison | Time-independent | 1 year | N/A |"));
	}

	private static HazardCurveMetadata timeDependent(int startYear, double durationYears) {
		TimeSpan span = new TimeSpan(StartTimePrecision.YEARS, DurationUnits.YEARS);
		span.setStartTime(startYear);
		span.setDuration(durationYears);
		return new HazardCurveMetadata(span);
	}

	private static void assertMergeFails(HazardCurveMetadata first, HazardCurveMetadata second) {
		try {
			first.merge(second);
			fail("Expected merge to fail");
		} catch (IllegalArgumentException expected) {}
	}
}
