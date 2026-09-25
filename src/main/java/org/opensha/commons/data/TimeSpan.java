package org.opensha.commons.data;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EventObject;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.IntegerConstraint;
import org.opensha.commons.param.constraint.impl.LongConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.TimeSpanChangeListener;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.LongParameter;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * A forecast duration and, for time-dependent forecasts, a UTC start time.
 * <p>
 * Start times are represented as either a calendar year or an exact epoch-millisecond value. A time-independent span
 * has no start time. Duration values retain their declared units and use 365.25 days per year when converted.
 */
@JsonAdapter(TimeSpan.Adapter.class)
public class TimeSpan implements ParameterChangeListener, Serializable {

	private static final long serialVersionUID = -1567318877618681307L;
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	protected static final String C = "TimeSpan";
	protected static final boolean D = false;

	public static final String XML_METADATA_NAME = "TimeSpan";

	public static final String YEARS = "Years";
	public static final String DAYS = "Days";
	public static final String HOURS = "Hours";
	public static final String MINUTES = "Minutes";
	public static final String SECONDS = "Seconds";
	public static final String MILLISECONDS = "Milliseconds";
	public static final String NONE = "None";

	public static final String START_YEAR = "Start Year";
	public static final String START_TIME_MILLIS = "Start Time Millis";
	public static final String START_TIME_PRECISION = "Start-Time Precision";
	public static final String DURATION = "Duration";

	private static final int START_YEAR_DEFAULT = 2003;
	private static final double DURATION_DEFAULT = 50d;
	private static final String XML_START_TIMES = "startTimes";
	private static final String XML_START_TIME_MILLIS = "StartTimeMillis";

	/** Precision of the forecast start time, or {@link #NONE} when a start time is not applicable. */
	public enum StartTimePrecision {
		NONE(TimeSpan.NONE),
		YEARS(TimeSpan.YEARS),
		MILLISECONDS(TimeSpan.MILLISECONDS);

		private final String legacyName;

		private StartTimePrecision(String legacyName) {
			this.legacyName = legacyName;
		}

		public String getLegacyName() {
			return legacyName;
		}

		public static StartTimePrecision fromString(String value) {
			for (StartTimePrecision precision : values())
				if (precision.name().equalsIgnoreCase(value) || precision.legacyName.equalsIgnoreCase(value))
					return precision;
			throw new IllegalArgumentException("Unsupported start-time precision: "+value
					+"; supported values are None, Years, and Milliseconds");
		}
	}

	/** Units used to store the forecast duration. */
	public enum DurationUnits {
		YEARS(TimeSpan.YEARS, 365.25d * 24d * 60d * 60d * 1000d),
		DAYS(TimeSpan.DAYS, 24d * 60d * 60d * 1000d),
		HOURS(TimeSpan.HOURS, 60d * 60d * 1000d),
		MINUTES(TimeSpan.MINUTES, 60d * 1000d),
		SECONDS(TimeSpan.SECONDS, 1000d),
		MILLISECONDS(TimeSpan.MILLISECONDS, 1d);

		private final String legacyName;
		private final double millisPerUnit;

		private DurationUnits(String legacyName, double millisPerUnit) {
			this.legacyName = legacyName;
			this.millisPerUnit = millisPerUnit;
		}

		public String getLegacyName() {
			return legacyName;
		}

		public double toMillis(double duration) {
			return duration * millisPerUnit;
		}

		public double fromMillis(double millis) {
			return millis / millisPerUnit;
		}

		public double convert(double duration, DurationUnits sourceUnits) {
			return fromMillis(sourceUnits.toMillis(duration));
		}

		public static DurationUnits fromString(String value) {
			for (DurationUnits units : values())
				if (units.name().equalsIgnoreCase(value) || units.legacyName.equalsIgnoreCase(value))
					return units;
			throw new IllegalArgumentException("Unsupported duration units: "+value);
		}
	}

	private final StartTimePrecision startTimePrecision;
	private final DurationUnits durationUnits;

	private final IntegerConstraint startYearConstraint = new IntegerConstraint(0, Integer.MAX_VALUE);
	private final IntegerParameter startYearParam;
	private final LongParameter startTimeMillisParam;

	private final DoubleConstraint durationConstraint = new DoubleConstraint(0d, Double.MAX_VALUE);
	private final DoubleParameter durationParam;
	private final DoubleDiscreteParameter discreteDurationParam;
	private boolean isDurationDiscrete;

	private transient List<TimeSpanChangeListener> changeListeners;

	/** Creates a time-independent span. */
	public TimeSpan(DurationUnits durationUnits) {
		this(StartTimePrecision.NONE, durationUnits);
	}

	/** Legacy string constructor. Start-time precision is restricted to None, Years, or Milliseconds. */
	public TimeSpan(String startTimePrecision, String durationUnits) {
		this(StartTimePrecision.fromString(startTimePrecision), DurationUnits.fromString(durationUnits));
	}

	public TimeSpan(StartTimePrecision startTimePrecision, DurationUnits durationUnits) {
		if (startTimePrecision == null)
			throw new NullPointerException("Start-time precision cannot be null");
		if (durationUnits == null)
			throw new NullPointerException("Duration units cannot be null");
		this.startTimePrecision = startTimePrecision;
		this.durationUnits = durationUnits;

		startYearParam = new IntegerParameter(START_YEAR, startYearConstraint, START_YEAR_DEFAULT);
		startTimeMillisParam = new LongParameter(START_TIME_MILLIS, Long.MIN_VALUE, Long.MAX_VALUE,
				startOfYearInMillis(START_YEAR_DEFAULT));

		durationParam = new DoubleParameter(DURATION, durationConstraint, durationUnits.getLegacyName(), DURATION_DEFAULT);
		discreteDurationParam = new DoubleDiscreteParameter(DURATION, durationUnits.getLegacyName(), DURATION_DEFAULT);

		startYearParam.addParameterChangeListener(this);
		startTimeMillisParam.addParameterChangeListener(this);
		durationParam.addParameterChangeListener(this);
		discreteDurationParam.addParameterChangeListener(this);
	}

	/**
	 * Sets the allowed start-year range. For millisecond spans, this constrains the timestamp to the inclusive UTC
	 * calendar-year range. Only {@link #START_YEAR} is accepted.
	 */
	public void setStartTimeConstraint(String name, int min, int max) {
		if (!START_YEAR.equals(name))
			throw new IllegalArgumentException("Only the Start Year constraint is supported");
		if (!startYearConstraint.isAllowed(min) || !startYearConstraint.isAllowed(max) || min > max)
			throw new IllegalArgumentException("Start-year constraint is outside absolute bounds");
		if (startTimePrecision == StartTimePrecision.YEARS) {
			startYearParam.setConstraint(new IntegerConstraint(min, max));
		} else if (startTimePrecision == StartTimePrecision.MILLISECONDS) {
			long minMillis = startOfYearInMillis(min);
			long maxMillis = utcCalendar(max, 12, 31, 23, 59, 59, 999).getTimeInMillis();
			startTimeMillisParam.setConstraint(new LongConstraint(minMillis, maxMillis));
		} else {
			throw new IllegalStateException("A time-independent TimeSpan has no start-time constraint");
		}
	}

	/** Sets the allowed epoch-millisecond range for an exact start time. */
	public void setStartTimeMillisConstraint(long min, long max) {
		if (startTimePrecision != StartTimePrecision.MILLISECONDS)
			throw precisionException("setStartTimeMillisConstraint(long, long)");
		if (min > max)
			throw new IllegalArgumentException("Minimum start time cannot exceed maximum start time");
		startTimeMillisParam.setConstraint(new LongConstraint(min, max));
	}

	public String getStartTimePrecision() {
		return startTimePrecision.getLegacyName();
	}

	public StartTimePrecision getStartTimePrecisionEnum() {
		return startTimePrecision;
	}

	public String getDurationUnits() {
		return durationUnits.getLegacyName();
	}

	public DurationUnits getDurationUnitsEnum() {
		return durationUnits;
	}

	public int getStartTimeYear() {
		if (startTimePrecision == StartTimePrecision.YEARS)
			return startYearParam.getValue();
		if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			return getStartTimeCalendar().get(Calendar.YEAR);
		throw precisionException("getStartTimeYear()");
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeMonth() {
		return exactStartField(Calendar.MONTH) + 1;
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeDay() {
		return exactStartField(Calendar.DATE);
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeHour() {
		return exactStartField(Calendar.HOUR_OF_DAY);
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeMinute() {
		return exactStartField(Calendar.MINUTE);
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeSecond() {
		return exactStartField(Calendar.SECOND);
	}

	/** Compatibility accessor derived from an exact start time. */
	public int getStartTimeMillisecond() {
		return exactStartField(Calendar.MILLISECOND);
	}

	private int exactStartField(int field) {
		if (startTimePrecision != StartTimePrecision.MILLISECONDS)
			throw precisionException("get exact start-time component");
		return getStartTimeCalendar().get(field);
	}

	public void setDuration(double duration) {
		if (isDurationDiscrete)
			discreteDurationParam.setValue(duration);
		else
			durationParam.setValue(duration);
	}

	public void setDuration(double duration, String units) {
		setDuration(convertDurationUnits(duration, DurationUnits.fromString(units), durationUnits));
	}

	public void setDuration(double duration, DurationUnits units) {
		setDuration(convertDurationUnits(duration, units, durationUnits));
	}

	public void setDurationConstraint(double min, double max) {
		if (!durationConstraint.isAllowed(min) || !durationConstraint.isAllowed(max) || min > max)
			throw new IllegalArgumentException("Duration constraint must be nonnegative and ordered");
		durationParam.setConstraint(new DoubleConstraint(min, max));
		isDurationDiscrete = false;
	}

	public void setDurationConstraint(List<Double> values) {
		for (double value : values)
			if (value < 0d)
				throw new IllegalArgumentException("Duration constraint values must be nonnegative");
		discreteDurationParam.setConstraint(new DoubleDiscreteConstraint(values));
		isDurationDiscrete = true;
	}

	public double getDuration() {
		return isDurationDiscrete ? discreteDurationParam.getValue() : durationParam.getValue();
	}

	public double getDuration(String units) {
		return getDuration(DurationUnits.fromString(units));
	}

	public double getDuration(DurationUnits units) {
		return convertDurationUnits(getDuration(), durationUnits, units);
	}

	private static double convertDurationUnits(double duration, DurationUnits present, DurationUnits desired) {
		return desired.convert(duration, present);
	}

	/** Returns a value copy; parameter constraints and listeners are intentionally not copied. */
	public TimeSpan copy() {
		TimeSpan copy = new TimeSpan(startTimePrecision, durationUnits);
		copy.setDuration(getDuration());
		if (startTimePrecision == StartTimePrecision.YEARS)
			copy.setStartTime(getStartTimeYear());
		else if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			copy.setStartTimeInMillis(getStartTimeInMillis());
		return copy;
	}

	public void setStartTime(int year) {
		if (startTimePrecision != StartTimePrecision.YEARS)
			throw precisionException("setStartTime(int)");
		startYearParam.setValue(year);
	}

	/** Convenience setter for an exact UTC time. */
	public void setStartTime(int year, int month, int day, int hour, int minute, int second, int millisecond) {
		if (startTimePrecision != StartTimePrecision.MILLISECONDS)
			throw precisionException("setStartTime(year, month, day, hour, minute, second, millisecond)");
		setStartTimeInMillis(utcCalendar(year, month, day, hour, minute, second, millisecond).getTimeInMillis());
	}

	public void setStartTime(GregorianCalendar calendar) {
		if (!calendar.getTimeZone().equals(UTC))
			throw new IllegalArgumentException("Calendar must use the UTC time zone");
		if (startTimePrecision == StartTimePrecision.YEARS)
			setStartTime(calendar.get(Calendar.YEAR));
		else if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			setStartTimeInMillis(calendar.getTimeInMillis());
	}

	public void setStartTimeInMillis(long millis) {
		if (startTimePrecision != StartTimePrecision.MILLISECONDS)
			throw precisionException("setStartTimeInMillis(long)");
		startTimeMillisParam.setValue(millis);
	}

	public long getStartTimeInMillis() {
		if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			return startTimeMillisParam.getValue();
		if (startTimePrecision == StartTimePrecision.YEARS)
			return startOfYearInMillis(startYearParam.getValue());
		throw precisionException("getStartTimeInMillis()");
	}

	/** Returns midnight UTC on January 1 of the supplied calendar year. */
	public static long startOfYearInMillis(int year) {
		return utcCalendar(year, 1, 1, 0, 0, 0, 0).getTimeInMillis();
	}

	public GregorianCalendar getStartTimeCalendar() {
		GregorianCalendar calendar = new GregorianCalendar(UTC);
		calendar.setTimeInMillis(getStartTimeInMillis());
		return calendar;
	}

	public GregorianCalendar getEndTimeCalendar() {
		long endTimeMillis = getStartTimeInMillis() + (long)getDuration(DurationUnits.MILLISECONDS);
		GregorianCalendar calendar = new GregorianCalendar(UTC);
		calendar.setTime(new Date(endTimeMillis));
		return calendar;
	}

	public ParameterList getAdjustableParams() {
		ParameterList list = new ParameterList();
		list.addParameter(isDurationDiscrete ? discreteDurationParam : durationParam);
		if (startTimePrecision == StartTimePrecision.YEARS)
			list.addParameter(startYearParam);
		else if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			list.addParameter(startTimeMillisParam);
		return list;
	}

	public void addParameterChangeListener(TimeSpanChangeListener listener) {
		if (changeListeners == null)
			changeListeners = new ArrayList<>();
		if (!changeListeners.contains(listener))
			changeListeners.add(listener);
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (changeListeners == null)
			return;
		EventObject timeSpanEvent = new EventObject(this);
		for (TimeSpanChangeListener listener : changeListeners)
			listener.timeSpanChange(timeSpanEvent);
	}

	private RuntimeException precisionException(String method) {
		return new IllegalStateException(method+" is unavailable for start-time precision "+startTimePrecision);
	}

	private static GregorianCalendar utcCalendar(int year, int month, int day, int hour, int minute, int second,
			int millisecond) {
		GregorianCalendar calendar = new GregorianCalendar(UTC);
		calendar.clear();
		calendar.setLenient(false);
		calendar.set(Calendar.ERA, GregorianCalendar.AD);
		calendar.set(year, month - 1, day, hour, minute, second);
		calendar.set(Calendar.MILLISECOND, millisecond);
		calendar.getTimeInMillis();
		return calendar;
	}

	/** JSON representation of the logical value, independent of parameter implementation details. */
	public static class Adapter extends TypeAdapter<TimeSpan> {

		private static final String START_YEAR_FIELD = "startYear";
		private static final String START_MILLIS_FIELD = "startTimeMillis";
		private static final String DURATION_FIELD = "duration";
		private static final String DURATION_UNITS_FIELD = "durationUnits";

		@Override
		public void write(JsonWriter out, TimeSpan value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			if (value.startTimePrecision == StartTimePrecision.YEARS)
				out.name(START_YEAR_FIELD).value(value.getStartTimeYear());
			else if (value.startTimePrecision == StartTimePrecision.MILLISECONDS)
				out.name(START_MILLIS_FIELD).value(value.getStartTimeInMillis());
			out.name(DURATION_FIELD).value(value.getDuration());
			out.name(DURATION_UNITS_FIELD).value(value.durationUnits.name());
			out.endObject();
		}

		@Override
		public TimeSpan read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}

			Integer startYear = null;
			Long startTimeMillis = null;
			Double duration = null;
			DurationUnits durationUnits = null;
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case START_YEAR_FIELD:
					startYear = in.nextInt();
					break;
				case START_MILLIS_FIELD:
					startTimeMillis = in.nextLong();
					break;
				case DURATION_FIELD:
					duration = in.nextDouble();
					break;
				case DURATION_UNITS_FIELD:
					durationUnits = DurationUnits.fromString(in.nextString());
					break;
				default:
					in.skipValue();
				}
			}
			in.endObject();

			if (duration == null)
				throw new IOException("Missing required TimeSpan field: "+DURATION_FIELD);
			if (durationUnits == null)
				throw new IOException("Missing required TimeSpan field: "+DURATION_UNITS_FIELD);
			if (startYear != null && startTimeMillis != null)
				throw new IOException("TimeSpan cannot specify both "+START_YEAR_FIELD+" and "+START_MILLIS_FIELD);

			StartTimePrecision precision = startYear != null ? StartTimePrecision.YEARS
					: startTimeMillis != null ? StartTimePrecision.MILLISECONDS : StartTimePrecision.NONE;
			TimeSpan value = new TimeSpan(precision, durationUnits);
			value.setDuration(duration);
			if (startYear != null)
				value.setStartTime(startYear);
			else if (startTimeMillis != null)
				value.setStartTimeInMillis(startTimeMillis);
			return value;
		}
	}

	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(XML_METADATA_NAME);
		xml.addAttribute("startTimePrecision", getStartTimePrecision());
		Element startTimes = xml.addElement(XML_START_TIMES);
		if (startTimePrecision == StartTimePrecision.YEARS)
			startTimes.addAttribute(START_YEAR.replace(" ", ""), Integer.toString(getStartTimeYear()));
		else if (startTimePrecision == StartTimePrecision.MILLISECONDS)
			startTimes.addAttribute(XML_START_TIME_MILLIS, Long.toString(getStartTimeInMillis()));
		xml.addAttribute("duration", Double.toString(getDuration()));
		xml.addAttribute("durationUnits", getDurationUnits());
		return root;
	}

	/** Reads current XML and collapses legacy component-based start times into an exact millisecond value. */
	public static TimeSpan fromXMLMetadata(Element xml) {
		String precisionName = xml.attributeValue("startTimePrecision");
		DurationUnits durationUnits = DurationUnits.fromString(xml.attributeValue("durationUnits"));
		double duration = Double.parseDouble(xml.attributeValue("duration"));
		Element startTimes = xml.element(XML_START_TIMES);

		StartTimePrecision precision;
		try {
			precision = StartTimePrecision.fromString(precisionName);
		} catch (IllegalArgumentException unsupportedLegacyPrecision) {
			precision = StartTimePrecision.MILLISECONDS;
		}
		TimeSpan span = new TimeSpan(precision, durationUnits);
		span.setDuration(duration);
		if (precision == StartTimePrecision.NONE)
			return span;

		if (startTimes == null)
			throw new IllegalArgumentException("Missing TimeSpan startTimes XML element");
		String millisValue = startTimes.attributeValue(XML_START_TIME_MILLIS);
		if (millisValue != null) {
			if (precision != StartTimePrecision.MILLISECONDS)
				throw new IllegalArgumentException("StartTimeMillis requires millisecond precision");
			span.setStartTimeInMillis(Long.parseLong(millisValue));
			return span;
		}

		int year = xmlInt(startTimes, "StartYear", START_YEAR_DEFAULT);
		if (precision == StartTimePrecision.YEARS) {
			span.setStartTime(year);
			return span;
		}
		int month = xmlInt(startTimes, "StartMonth", 1);
		int day = xmlInt(startTimes, "StartDay", 1);
		int hour = xmlInt(startTimes, "StartHour", 0);
		int minute = xmlInt(startTimes, "StartMinute", 0);
		int second = xmlInt(startTimes, "StartSecond", 0);
		int millisecond = xmlInt(startTimes, "StartMillisecond", 0);
		span.setStartTime(year, month, day, hour, minute, second, millisecond);
		return span;
	}

	private static int xmlInt(Element element, String name, int defaultValue) {
		Attribute attribute = element.attribute(name);
		return attribute == null ? defaultValue : Integer.parseInt(attribute.getValue());
	}
}
