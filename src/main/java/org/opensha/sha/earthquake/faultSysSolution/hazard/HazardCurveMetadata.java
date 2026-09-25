package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Temporal metadata needed to interpret probabilities stored in hazard curves.
 * <p>
 * The duration applies to every curve in the collection. For time-dependent or mixed collections, the start time
 * applies to every time-dependent curve. This class stores a value copy of the supplied {@link TimeSpan}; subsequent
 * changes to the ERF time span are not reflected here. Likewise, {@link #getTimeSpan()} returns a defensive copy.
 */
@JsonAdapter(HazardCurveMetadata.Adapter.class)
public final class HazardCurveMetadata {

	public static final String FILE_NAME = "hazard_curve_metadata.json";

	public enum TimeDependence {
		TIME_INDEPENDENT(true, false),
		TIME_DEPENDENT(false, true),
		MIXED(true, true);

		private final boolean hasTimeIndependentCurves;
		private final boolean hasTimeDependentCurves;

		private TimeDependence(boolean hasTimeIndependentCurves, boolean hasTimeDependentCurves) {
			this.hasTimeIndependentCurves = hasTimeIndependentCurves;
			this.hasTimeDependentCurves = hasTimeDependentCurves;
		}

		private static TimeDependence forContents(boolean hasTimeIndependentCurves,
				boolean hasTimeDependentCurves) {
			Preconditions.checkArgument(hasTimeIndependentCurves || hasTimeDependentCurves,
					"Metadata must describe at least one curve");
			return hasTimeIndependentCurves
					? (hasTimeDependentCurves ? MIXED : TIME_INDEPENDENT)
					: TIME_DEPENDENT;
		}
	}

	private final TimeDependence timeDependence;
	private final TimeSpan timeSpan;

	public static HazardCurveMetadata timeIndependent(double durationYears) {
		TimeSpan timeSpan = new TimeSpan(DurationUnits.YEARS);
		timeSpan.setDuration(durationYears);
		return new HazardCurveMetadata(timeSpan);
	}

	public HazardCurveMetadata(TimeSpan timeSpan) {
		this(timeSpan, inferTimeDependence(timeSpan));
	}

	private static TimeDependence inferTimeDependence(TimeSpan timeSpan) {
		Preconditions.checkNotNull(timeSpan, "TimeSpan cannot be null");
		return timeSpan.getStartTimePrecision() == StartTimePrecision.NONE
				? TimeDependence.TIME_INDEPENDENT : TimeDependence.TIME_DEPENDENT;
	}

	private HazardCurveMetadata(TimeSpan timeSpan, TimeDependence timeDependence) {
		this.timeSpan = Preconditions.checkNotNull(timeSpan, "TimeSpan cannot be null").copy();
		this.timeDependence = Preconditions.checkNotNull(timeDependence, "Time dependence cannot be null");
		Preconditions.checkArgument(this.timeSpan.getDuration() > 0d, "TimeSpan duration must be positive");
		boolean hasStartTime = this.timeSpan.getStartTimePrecision() != StartTimePrecision.NONE;
		Preconditions.checkArgument(hasStartTime == timeDependence.hasTimeDependentCurves,
				"TimeSpan start time presence is inconsistent with time dependence %s", timeDependence);
	}

	public TimeDependence getTimeDependence() {
		return timeDependence;
	}

	/** @return a defensive value copy of the curve time span */
	public TimeSpan getTimeSpan() {
		return timeSpan.copy();
	}

	/** @return curve duration in years */
	public double getDurationYears() {
		return timeSpan.getDuration(DurationUnits.YEARS);
	}

	public boolean hasTimeIndependentCurves() {
		return timeDependence.hasTimeIndependentCurves;
	}

	public boolean hasTimeDependentCurves() {
		return timeDependence.hasTimeDependentCurves;
	}

	/**
	 * @return forecast start time in epoch milliseconds, or {@code null} for a time-independent curve
	 */
	public Long getForecastStartTimeMillis() {
		return hasTimeDependentCurves() ? timeSpan.getStartTimeInMillis() : null;
	}

	/** Returns whether the stored curve durations are equal, allowing for unit conversion. */
	public boolean isSameDuration(HazardCurveMetadata other) {
		if (other == null)
			return false;
		double durationYears = getDurationYears();
		double otherDurationYears = other.getDurationYears();
		return Math.abs(durationYears-otherDurationYears) <= 1e-12
				*Math.max(1d, Math.max(Math.abs(durationYears), Math.abs(otherDurationYears)));
	}

	/**
	 * Returns whether curves described by these objects can be compared. Time-independent curves can be converted to
	 * any comparison duration. If both objects contain time-dependent curves, their durations must match; forecast
	 * start times may differ.
	 */
	public boolean isComparable(HazardCurveMetadata other) {
		return other != null && (!hasTimeDependentCurves() || !other.hasTimeDependentCurves()
				|| isSameDuration(other));
	}

	/**
	 * Merges metadata for curves that will be stored or processed together. Durations must match. If both inputs
	 * contain time-dependent curves, their forecast start times must also match.
	 */
	public HazardCurveMetadata merge(HazardCurveMetadata other) {
		Preconditions.checkNotNull(other, "Other metadata cannot be null");
		Preconditions.checkArgument(isSameDuration(other),
				"Cannot merge hazard curves with different durations: %s and %s years",
				getDurationYears(), other.getDurationYears());
		if (hasTimeDependentCurves() && other.hasTimeDependentCurves())
			Preconditions.checkArgument(timeSpan.getStartTimeInMillis() == other.timeSpan.getStartTimeInMillis(),
					"Cannot merge time-dependent hazard curves with different forecast start times");

		boolean hasTI = hasTimeIndependentCurves() || other.hasTimeIndependentCurves();
		boolean hasTD = hasTimeDependentCurves() || other.hasTimeDependentCurves();
		TimeSpan mergedTimeSpan = hasTimeDependentCurves() ? timeSpan : other.timeSpan;
		return new HazardCurveMetadata(mergedTimeSpan, TimeDependence.forContents(hasTI, hasTD));
	}

	public static HazardCurveMetadata merge(Iterable<? extends HazardCurveMetadata> metadata) {
		Iterator<? extends HazardCurveMetadata> iterator = Preconditions.checkNotNull(metadata,
				"Metadata collection cannot be null").iterator();
		Preconditions.checkArgument(iterator.hasNext(), "Metadata collection cannot be empty");
		HazardCurveMetadata merged = Preconditions.checkNotNull(iterator.next(), "Metadata cannot be null");
		while (iterator.hasNext())
			merged = merged.merge(iterator.next());
		return merged;
	}

	public static boolean areComparable(Iterable<? extends HazardCurveMetadata> metadata) {
		Iterator<? extends HazardCurveMetadata> iterator = Preconditions.checkNotNull(metadata,
				"Metadata collection cannot be null").iterator();
		Preconditions.checkArgument(iterator.hasNext(), "Metadata collection cannot be empty");
		HazardCurveMetadata timeDependent = null;
		while (iterator.hasNext()) {
			HazardCurveMetadata next = Preconditions.checkNotNull(iterator.next(), "Metadata cannot be null");
			if (next.hasTimeDependentCurves()) {
				if (timeDependent != null && !timeDependent.isSameDuration(next))
					return false;
				timeDependent = next;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof HazardCurveMetadata))
			return false;
		HazardCurveMetadata other = (HazardCurveMetadata)obj;
		return timeDependence == other.timeDependence && timeSpan.equals(other.timeSpan);
	}

	@Override
	public int hashCode() {
		return 31 * timeDependence.hashCode() + timeSpan.hashCode();
	}

	public void write(File file) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			write(writer);
		}
	}

	public void write(Writer writer) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(this, writer);
		writer.write(System.lineSeparator());
		writer.flush();
	}

	/** Writes this metadata to its standard entry in an archive. */
	public void write(ArchiveOutput output) throws IOException {
		output.putNextEntry(FILE_NAME);
		write(new OutputStreamWriter(output.getOutputStream(), StandardCharsets.UTF_8));
		output.closeEntry();
	}

	public static HazardCurveMetadata read(File file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			return read(reader);
		}
	}

	public static HazardCurveMetadata read(Reader reader) throws IOException {
		HazardCurveMetadata metadata = new GsonBuilder().create().fromJson(reader, HazardCurveMetadata.class);
		if (metadata == null)
			throw new IOException("Hazard curve metadata is empty");
		return metadata;
	}

	/** Reads the standard metadata entry from an archive. */
	public static HazardCurveMetadata read(ArchiveInput input) throws IOException {
		if (!input.hasEntry(FILE_NAME))
			throw new IOException("Archive does not contain "+FILE_NAME+": "+input.getName());
		try (InputStreamReader reader = new InputStreamReader(input.getInputStream(FILE_NAME), StandardCharsets.UTF_8)) {
			return read(reader);
		}
	}

	public static class Adapter extends TypeAdapter<HazardCurveMetadata> {

		private static final String TIME_DEPENDENCE_FIELD = "timeDependence";
		private static final String TIME_SPAN_FIELD = "timeSpan";
		private final TimeSpan.Adapter timeSpanAdapter = new TimeSpan.Adapter();

		@Override
		public void write(JsonWriter out, HazardCurveMetadata value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			out.name(TIME_DEPENDENCE_FIELD).value(value.timeDependence.name());
			out.name(TIME_SPAN_FIELD);
			timeSpanAdapter.write(out, value.timeSpan);
			out.endObject();
		}

		@Override
		public HazardCurveMetadata read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			TimeDependence timeDependence = null;
			TimeSpan timeSpan = null;
			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				if (TIME_DEPENDENCE_FIELD.equals(name))
					timeDependence = TimeDependence.valueOf(in.nextString());
				else if (TIME_SPAN_FIELD.equals(name))
					timeSpan = timeSpanAdapter.read(in);
				else
					in.skipValue();
			}
			in.endObject();
			if (timeDependence == null)
				throw new IOException("Missing required HazardCurveMetadata field: "+TIME_DEPENDENCE_FIELD);
			if (timeSpan == null)
				throw new IOException("Missing required HazardCurveMetadata field: "+TIME_SPAN_FIELD);
			return new HazardCurveMetadata(timeSpan, timeDependence);
		}
	}
	
	static List<String> buildTimeSpanSummary(String primaryName, HazardCurveMetadata primary,
			String comparisonName, HazardCurveMetadata comparison) {
		if (!primary.hasTimeDependentCurves()
				&& (comparison == null || !comparison.hasTimeDependentCurves()))
			return Collections.emptyList();

		List<String> lines = new ArrayList<>();
		lines.add("**Hazard Curve Time Span Summary**");
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Calculation", "Time Dependence", "Duration", "Forecast Start");
		table.addLine(primaryName == null ? "Primary" : primaryName,
				dependenceLabel(primary), durationLabel(primary), startTimeLabel(primary));
		if (comparison != null)
			table.addLine(comparisonName == null ? "Comparison" : comparisonName,
					dependenceLabel(comparison), durationLabel(comparison), startTimeLabel(comparison));
		lines.addAll(table.build());
		lines.add("");
		return lines;
	}

	private static String dependenceLabel(HazardCurveMetadata metadata) {
		switch (metadata.getTimeDependence()) {
		case TIME_INDEPENDENT:
			return "Time-independent";
		case TIME_DEPENDENT:
			return "Time-dependent";
		case MIXED:
			return "Mixed (time-independent and time-dependent)";
		default:
			throw new IllegalStateException("Unhandled time dependence: "+metadata.getTimeDependence());
		}
	}

	private static String durationLabel(HazardCurveMetadata metadata) {
		double years = metadata.getDurationYears();
		String value = BigDecimal.valueOf(years).stripTrailingZeros().toPlainString();
		return value+" year"+(years == 1d ? "" : "s");
	}

	private static String startTimeLabel(HazardCurveMetadata metadata) {
		if (!metadata.hasTimeDependentCurves())
			return "N/A";
		TimeSpan timeSpan = metadata.getTimeSpan();
		if (timeSpan.getStartTimePrecision() == StartTimePrecision.YEARS)
			return Integer.toString(timeSpan.getStartTimeYear());
		return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timeSpan.getStartTimeInMillis()));
	}
}
