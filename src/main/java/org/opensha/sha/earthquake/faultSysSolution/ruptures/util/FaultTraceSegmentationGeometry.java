package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.SubsectionTransition;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;

/**
 * Subsection-scale, map-view geometry for exploratory segmentation models.
 * Distances are in km and angles in degrees. Probability rules are separate.
 *
 * <p>Subsection strike is {@link FaultTrace#getStrikeDirection()}, the initial
 * bearing between trace endpoints. The local along-strike axis bisects the two
 * undirected strike axes. Bend angles instead use directed strikes, so input
 * traces must be consistently oriented. Reversing traces can slightly change
 * these initial bearings because of spherical geometry.
 *
 * <p>Trace vertices are projected into a local azimuthal-equidistant plane
 * centered on the four trace endpoints. Segment calculations are exact in that
 * plane, which is a local approximation to spherical geometry. Depths are ignored.
 * Strike axes 90 degrees apart have no unique bisector; overlap, gap, and stepover
 * measurements are withheld in that case.
 */
public class FaultTraceSegmentationGeometry {

	// Numerical tolerances, not feature-size thresholds.
	private static final double DISTANCE_TOLERANCE_KM = 1e-9;
	private static final double STRIKE_AXIS_TOLERANCE_DEGREES = 1e-8;
	private static final double RELATIVE_PARALLEL_TOLERANCE = 1e-14;
	private static final double GEOGRAPHIC_CENTER_TOLERANCE = 1e-8;

	private final double minBendAngleDegrees;
	private final double maxPerpendicularDeviationDegrees;

	/** Overlap-clipped shortest distance, with no angular restriction. */
	public FaultTraceSegmentationGeometry(double minBendAngleDegrees) {
		this(minBendAngleDegrees, Double.POSITIVE_INFINITY);
	}

	/**
	 * @param minBendAngleDegrees minimum bend angle, inclusive; zero-angle
	 *        continuation never creates a bend transition
	 * @param maxPerpendicularDeviationDegrees optional angular limit in [0, 90),
	 *        or positive infinity for none. Zero requires perpendicular connectors.
	 *        Overlap clipping applies for every setting.
	 */
	public FaultTraceSegmentationGeometry(double minBendAngleDegrees, double maxPerpendicularDeviationDegrees) {
		Preconditions.checkArgument(Double.isFinite(minBendAngleDegrees)
				&& minBendAngleDegrees >= 0d && minBendAngleDegrees <= 180d, "Invalid bend threshold");
		Preconditions.checkArgument(maxPerpendicularDeviationDegrees == Double.POSITIVE_INFINITY
				|| (Double.isFinite(maxPerpendicularDeviationDegrees)
				&& maxPerpendicularDeviationDegrees >= 0d && maxPerpendicularDeviationDegrees < 90d),
				"Invalid perpendicular tolerance");
		this.minBendAngleDegrees = minBendAngleDegrees;
		this.maxPerpendicularDeviationDegrees = maxPerpendicularDeviationDegrees;
	}

	public double getMinBendAngleDegrees() {
		return minBendAngleDegrees;
	}

	public double getMaxPerpendicularDeviationDegrees() {
		return maxPerpendicularDeviationDegrees;
	}

	/** Absolute directed endpoint-strike change, in [0, 180]. */
	public static double calcBendAngle(FaultSection first, FaultSection second) {
		return angleDifference(validTrace(first).getStrikeDirection(), validTrace(second).getStrikeDirection());
	}

	public boolean isBend(FaultSection first, FaultSection second) {
		return qualifiesAsBend(calcBendAngle(first, second));
	}

	private boolean qualifiesAsBend(double angle) {
		return angle > 0d && angle >= minBendAngleDegrees;
	}

	/** Returns only traversed within-parent boundaries meeting the threshold, including splays. */
	public List<SubsectionTransition> getBendTransitions(ClusterRupture rupture) {
		List<SubsectionTransition> transitions = new ArrayList<>();
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
			for (FaultSection from : cluster.subSects) {
				for (FaultSection to : navigator.getDescendants(from)) {
					if (from.getParentSectionId() == to.getParentSectionId() && isBend(from, to))
						transitions.add(new SubsectionTransition(from, to));
				}
			}
		}
		return transitions;
	}

	/**
	 * Relation between the traces' projected intervals along their average strike
	 * axis. This describes along-strike overlap, not whether the traces intersect
	 * in map view, and does not by itself select a probability rule.
	 */
	public enum TraceRelation {
		/**
		 * The projected intervals share a positive-length interval, beyond the
		 * numerical distance tolerance. Both traces are clipped to that interval
		 * for stepover measurements. The traces can remain laterally separated;
		 * "overlapping" does not imply that they physically cross or coincide.
		 * Facing-end gap measurements are not defined for this relation.
		 */
		OVERLAPPING,
		/**
		 * The projected intervals meet at a single along-strike position, within
		 * the numerical distance tolerance. Any reported projected gap or overlap
		 * length is at most that tolerance. The traces need not physically
		 * touch: their ends can have a nonzero across-strike offset. Facing-end
		 * measurements are available, but no positive overlap region exists for
		 * a stepover measurement.
		 */
		ALONG_STRIKE_TOUCHING,
		/**
		 * The projected intervals are separated by a positive along-strike gap,
		 * beyond the numerical distance tolerance. Facing-end gap and offset
		 * measurements are available; overlap-based stepover measurements are
		 * not. For a folded trace, an interior projection extremum can make the
		 * full-trace projected gap smaller than the facing-end gap; see
		 * {@link Measurements#facingEndsBoundProjectedGap}.
		 */
		SEPARATED,
		/**
		 * The two undirected subsection strike axes are perpendicular, within
		 * the numerical angle tolerance. For example, north-south and east-west
		 * axes have equally valid northeast-southwest and northwest-southeast
		 * bisectors. Neither is preferred, so their average strike axis is
		 * undefined. This is not triggered by opposite trace orientations:
		 * strikes of 0 and 180 degrees describe the same undirected axis.
		 *
		 * <p>Without an average axis, along-strike overlap and gap classification
		 * would be arbitrary. The axis, overlap length, and projected gap are
		 * therefore NaN, and perpendicular, stepover, and facing-end connectors
		 * are null. The bend angle and unconstrained closest-trace/closest-end
		 * distances remain available. This is a very narrow numerical check
		 * around 90 degrees, not a general exclusion of large bends.
		 */
		AMBIGUOUS_STRIKE_AXIS
	}

	/** Measures this subsection pair, not the nearest connection anywhere on its parents. */
	public Measurements measure(FaultSection first, FaultSection second) {
		return measure(first, second, false);
	}

	/**
	 * Measures the subsection pair and optionally includes the trace segments used in the overlap distance search.
	 * The diagnostic segments are the clipped pieces from the same projection used to measure the distance.
	 */
	public Measurements measure(FaultSection first, FaultSection second, boolean includeOverlapSegments) {
		Preconditions.checkArgument(first.getSectionId() != second.getSectionId(), "Expected distinct subsections");
		return new Measurements(new SectionGeometry(first), new SectionGeometry(second), this, includeOverlapSegments);
	}

	/** Snapshot of the geometry and identity of one subsection. */
	public static class SectionGeometry {
		public final int sectionID;
		public final int parentID;
		public final String name;
		public final double lengthKm;
		public final double strikeDegrees;
		private final FaultTrace trace;

		private SectionGeometry(FaultSection section) {
			FaultTrace source = validTrace(section);
			sectionID = section.getSectionId();
			parentID = section.getParentSectionId();
			name = section.getSectionName();
			trace = new FaultTrace(source.getName());
			for (Location location : source)
				trace.add(new Location(location.lat, location.lon));
			lengthKm = trace.getTraceLength();
			strikeDegrees = trace.getStrikeDirection();
		}

		public FaultTrace getTrace() {
			return trace.clone();
		}
	}

	public static class Measurements {
		public final SectionGeometry first;
		public final SectionGeometry second;
		public final double bendAngleDegrees;
		public final double axialStrikeDifferenceDegrees;
		public final double averageStrikeAxisDegrees;
		public final TraceRelation relation;
		public final double overlapLengthKm;
		/** Trace pieces within the shared along-strike interval, empty unless requested and overlap exists. */
		public final List<LocationList> firstOverlapSegments;
		/** Trace pieces within the shared along-strike interval, empty unless requested and overlap exists. */
		public final List<LocationList> secondOverlapSegments;
		public final double projectedGapKm;
		public final Connector closestTraceConnector;
		public final Connector closestEndConnector;
		/** Null without positive overlap. */
		public final Connector exactPerpendicularConnector;
		/** Overlap-clipped shortest connector, with the optional angular limit applied. */
		public final Connector stepoverConnector;
		/** Actual facing trace ends; null for overlap or an ambiguous strike axis. */
		public final Connector facingEndConnector;
		/** Components of the facing-end connector; NaN when absent. Offset is not an overlap-based stepover. */
		public final double facingEndGapKm;
		public final double facingEndOffsetKm;
		/** False when absent, or when an interior projection extremum extends beyond a facing end. */
		public final boolean facingEndsBoundProjectedGap;
		public final double minBendAngleDegrees;
		public final double maxPerpendicularDeviationDegrees;
		public final boolean bendTransitionDefined;

		private Measurements(SectionGeometry first, SectionGeometry second, FaultTraceSegmentationGeometry settings,
				boolean includeOverlapSegments) {
			this.first = first;
			this.second = second;
			minBendAngleDegrees = settings.minBendAngleDegrees;
			maxPerpendicularDeviationDegrees = settings.maxPerpendicularDeviationDegrees;
			bendAngleDegrees = angleDifference(first.strikeDegrees, second.strikeDegrees);
			bendTransitionDefined = settings.qualifiesAsBend(bendAngleDegrees);
			axialStrikeDifferenceDegrees = Math.min(bendAngleDegrees, 180d-bendAngleDegrees);
			averageStrikeAxisDegrees = averageStrikeAxis(first.strikeDegrees, second.strikeDegrees);
			boolean hasStrikeAxis = Double.isFinite(averageStrikeAxisDegrees);

			// The fallback frame is used only for unconstrained distances when the axis is ambiguous.
			double frameStrike = hasStrikeAxis ? averageStrikeAxisDegrees : 0d;
			LocalStrikeFrame frame = new LocalStrikeFrame(first.trace, second.trace, frameStrike);
			List<Point> firstPoints = project(first.trace, frame);
			List<Point> secondPoints = project(second.trace, frame);
			Interval overlap = alongInterval(firstPoints).intersection(alongInterval(secondPoints));
			relation = classifyRelation(overlap, hasStrikeAxis);
			overlapLengthKm = hasStrikeAxis ? Math.max(0d, overlap.length()) : Double.NaN;
			projectedGapKm = hasStrikeAxis ? Math.max(0d, -overlap.length()) : Double.NaN;
			List<Segment> firstSegments = segments(firstPoints);
			List<Segment> secondSegments = segments(secondPoints);
			closestTraceConnector = connector(findClosestConnector(firstSegments, secondSegments), frame, hasStrikeAxis);
			closestEndConnector = connector(findClosestEnds(firstPoints, secondPoints), frame, hasStrikeAxis);

			if (relation == TraceRelation.OVERLAPPING) {
				List<Segment> firstOverlap = clipToOverlap(firstSegments, overlap);
				List<Segment> secondOverlap = clipToOverlap(secondSegments, overlap);
				firstOverlapSegments = includeOverlapSegments ? toLocations(firstOverlap, frame) : List.of();
				secondOverlapSegments = includeOverlapSegments ? toLocations(secondOverlap, frame) : List.of();
				Candidate perpendicular = findPerpendicularConnector(firstOverlap, secondOverlap);
				exactPerpendicularConnector = connector(perpendicular, frame, true);
				Candidate stepover;
				if (Double.isInfinite(maxPerpendicularDeviationDegrees)) {
					stepover = findOverlapConnector(firstOverlap, secondOverlap);
				} else if (maxPerpendicularDeviationDegrees == 0d) {
					stepover = perpendicular;
				} else {
					stepover = AngularConnectorSearch.find(firstOverlap, secondOverlap, maxPerpendicularDeviationDegrees);
				}
				stepoverConnector = connector(stepover, frame, true);
				facingEndConnector = null;
			} else {
				firstOverlapSegments = List.of();
				secondOverlapSegments = List.of();
				exactPerpendicularConnector = null;
				stepoverConnector = null;
				if (hasStrikeAxis)
					facingEndConnector = connector(findFacingEnds(firstPoints, secondPoints), frame, true);
				else
					facingEndConnector = null;
			}
			if (facingEndConnector == null) {
				facingEndGapKm = Double.NaN;
				facingEndOffsetKm = Double.NaN;
				facingEndsBoundProjectedGap = false;
			} else {
				facingEndGapKm = facingEndConnector.alongStrikeKm;
				facingEndOffsetKm = facingEndConnector.acrossStrikeKm;
				facingEndsBoundProjectedGap = Math.abs(facingEndGapKm-projectedGapKm) <= DISTANCE_TOLERANCE_KM;
			}
		}
	}

	private static List<LocationList> toLocations(List<Segment> segments, LocalStrikeFrame frame) {
		List<LocationList> locations = new ArrayList<>(segments.size());
		for (Segment segment : segments)
			locations.add(LocationList.of(frame.toLocation(segment.first), frame.toLocation(segment.second))
					.unmodifiableList());
		return List.copyOf(locations);
	}

	/** Connector endpoints and components always describe the same point pair. */
	public static class Connector {
		public final Location firstLocation;
		public final Location secondLocation;
		/** Local planar length used by the calculations. */
		public final double lengthKm;
		/** Spherical horizontal distance, for checking the local approximation. */
		public final double horizontalDistanceKm;
		/** Absolute local components; NaN without a strike axis. Deviation is also NaN for zero length. */
		public final double alongStrikeKm;
		public final double acrossStrikeKm;
		public final double perpendicularDeviationDegrees;

		private Connector(Candidate candidate, LocalStrikeFrame frame, boolean hasStrikeAxis) {
			firstLocation = frame.toLocation(candidate.first);
			double length = candidate.length();
			// Coalesce numerical roundoff at shared endpoints/intersections.
			lengthKm = length <= DISTANCE_TOLERANCE_KM ? 0d : length;
			secondLocation = lengthKm == 0d ? firstLocation : frame.toLocation(candidate.second);
			horizontalDistanceKm = LocationUtils.horzDistance(firstLocation, secondLocation);
			if (!hasStrikeAxis) {
				alongStrikeKm = Double.NaN;
				acrossStrikeKm = Double.NaN;
			} else if (lengthKm == 0d) {
				alongStrikeKm = 0d;
				acrossStrikeKm = 0d;
			} else {
				alongStrikeKm = Math.abs(candidate.second.along-candidate.first.along);
				acrossStrikeKm = Math.abs(candidate.second.across-candidate.first.across);
			}
			if (hasStrikeAxis && lengthKm > 0d)
				perpendicularDeviationDegrees = Math.toDegrees(Math.atan2(alongStrikeKm, acrossStrikeKm));
			else
				perpendicularDeviationDegrees = Double.NaN;
		}
	}

	private static Connector connector(Candidate candidate, LocalStrikeFrame frame, boolean hasStrikeAxis) {
		return new Connector(Preconditions.checkNotNull(candidate, "No feasible connector"), frame, hasStrikeAxis);
	}

	private static FaultTrace validTrace(FaultSection section) {
		FaultTrace trace = Preconditions.checkNotNull(section.getFaultTrace(), "Missing trace");
		Preconditions.checkArgument(trace.size() >= 2,
				"Trace must have at least two points: %s", section.getSectionId());
		double length = trace.getTraceLength();
		Preconditions.checkArgument(Double.isFinite(length) && length > 0d,
				"Trace must have positive horizontal length: %s", section.getSectionId());
		Preconditions.checkArgument(LocationUtils.horzDistance(trace.first(), trace.last()) > 0d,
				"Trace endpoints must be distinct to define strike: %s", section.getSectionId());
		return trace;
	}

	private static double angleDifference(double first, double second) {
		return Math.abs(Math.IEEEremainder(first-second, 360d));
	}

	private static double averageStrikeAxis(double first, double second) {
		double difference = angleDifference(first, second);
		double axialDifference = Math.min(difference, 180d-difference);
		if (Math.abs(axialDifference-90d) < STRIKE_AXIS_TOLERANCE_DEGREES)
			return Double.NaN;
		// Double angles before averaging undirected strike axes.
		double east = Math.sin(Math.toRadians(2d*first))+Math.sin(Math.toRadians(2d*second));
		double north = Math.cos(Math.toRadians(2d*first))+Math.cos(Math.toRadians(2d*second));
		return (0.5*Math.toDegrees(Math.atan2(east, north))+180d)%180d;
	}

	private static TraceRelation classifyRelation(Interval overlap, boolean hasStrikeAxis) {
		if (!hasStrikeAxis)
			return TraceRelation.AMBIGUOUS_STRIKE_AXIS;
		if (overlap.length() > DISTANCE_TOLERANCE_KM)
			return TraceRelation.OVERLAPPING;
		if (overlap.length() < -DISTANCE_TOLERANCE_KM)
			return TraceRelation.SEPARATED;
		return TraceRelation.ALONG_STRIKE_TOUCHING;
	}

	private static Candidate findClosestEnds(List<Point> first, List<Point> second) {
		Candidate closest = null;
		for (Point firstEnd : Arrays.asList(first.get(0), first.get(first.size()-1))) {
			for (Point secondEnd : Arrays.asList(second.get(0), second.get(second.size()-1)))
				closest = best(closest, new Candidate(firstEnd, secondEnd));
		}
		return closest;
	}

	/** Physical ends facing the projected gap, not necessarily the nearest ends in map view. */
	private static Candidate findFacingEnds(List<Point> first, List<Point> second) {
		boolean firstBeforeSecond = alongInterval(first).max <= alongInterval(second).min+DISTANCE_TOLERANCE_KM;
		List<Point> firstEnds = Arrays.asList(first.get(0), first.get(first.size()-1));
		List<Point> secondEnds = Arrays.asList(second.get(0), second.get(second.size()-1));
		Interval firstRange = alongInterval(firstEnds);
		Interval secondRange = alongInterval(secondEnds);
		double firstFacing = firstBeforeSecond ? firstRange.max : firstRange.min;
		double secondFacing = firstBeforeSecond ? secondRange.min : secondRange.max;
		Candidate closest = null;
		for (Point firstEnd : firstEnds) {
			if (Math.abs(firstEnd.along-firstFacing) > DISTANCE_TOLERANCE_KM)
				continue;
			for (Point secondEnd : secondEnds) {
				if (Math.abs(secondEnd.along-secondFacing) <= DISTANCE_TOLERANCE_KM)
					closest = best(closest, new Candidate(firstEnd, secondEnd));
			}
		}
		return closest;
	}

	/** Shortest segment-to-segment connector after both traces have been clipped to the overlap. */
	private static Candidate findOverlapConnector(List<Segment> firstOverlap, List<Segment> secondOverlap) {
		return findClosestConnector(firstOverlap, secondOverlap);
	}

	private static Candidate findClosestConnector(List<Segment> first, List<Segment> second) {
		Candidate closest = null;
		for (Segment firstSegment : first) {
			for (Segment secondSegment : second) {
				Candidate intersection = intersection(firstSegment, secondSegment);
				if (intersection != null)
					return intersection;
				closest = best(closest, new Candidate(firstSegment.first, secondSegment.project(firstSegment.first)));
				closest = best(closest, new Candidate(firstSegment.second, secondSegment.project(firstSegment.second)));
				closest = best(closest, new Candidate(firstSegment.project(secondSegment.first), secondSegment.first));
				closest = best(closest, new Candidate(firstSegment.project(secondSegment.second), secondSegment.second));
			}
		}
		return Preconditions.checkNotNull(closest, "No segments to connect");
	}

	/** Clips individual segments, preserving disconnected pieces and isolated boundary points. */
	private static List<Segment> clipToOverlap(List<Segment> trace, Interval overlap) {
		List<Segment> clipped = new ArrayList<>();
		for (Segment segment : trace) {
			double alongChange = segment.second.along-segment.first.along;
			if (Math.abs(alongChange) <= DISTANCE_TOLERANCE_KM) {
				if (overlap.contains(segment.first.along))
					clipped.add(segment);
				continue;
			}
			double atMin = (overlap.min-segment.first.along)/alongChange;
			double atMax = (overlap.max-segment.first.along)/alongChange;
			double start = Math.max(0d, Math.min(atMin, atMax));
			double end = Math.min(1d, Math.max(atMin, atMax));
			if (start <= end)
				clipped.add(new Segment(segment.at(start), segment.at(end)));
		}
		return clipped;
	}

	/**
	 * At a shared along-strike station, across-strike separation is linear on a
	 * segment pair. Its minimum occurs at an interval end or where the traces cross.
	 * Constant-along segments contribute an across-strike interval at their station.
	 */
	private static Candidate findPerpendicularConnector(List<Segment> first, List<Segment> second) {
		Candidate closest = null;
		for (Segment firstSegment : first) {
			for (Segment secondSegment : second) {
				Interval shared = firstSegment.alongInterval().intersection(secondSegment.alongInterval());
				if (shared.length() < -DISTANCE_TOLERANCE_KM)
					continue;
				double low = shared.min;
				double high = Math.max(low, shared.max);
				Candidate lowConnector = perpendicularAt(firstSegment, secondSegment, low);
				Candidate highConnector = perpendicularAt(firstSegment, secondSegment, high);
				closest = best(closest, best(lowConnector, highConnector));
				double lowDifference = lowConnector.second.across-lowConnector.first.across;
				double highDifference = highConnector.second.across-highConnector.first.across;
				if (lowDifference*highDifference < 0d) {
					double crossing = low+(high-low)*lowDifference/(lowDifference-highDifference);
					closest = best(closest, perpendicularAt(firstSegment, secondSegment, crossing));
				}
			}
		}
		return Preconditions.checkNotNull(closest, "No perpendicular connector in overlap");
	}

	private static Candidate perpendicularAt(Segment first, Segment second, double station) {
		Interval firstAcross = first.acrossAt(station);
		Interval secondAcross = second.acrossAt(station);
		double firstValue;
		double secondValue;
		if (firstAcross.max < secondAcross.min) {
			firstValue = firstAcross.max;
			secondValue = secondAcross.min;
		} else if (secondAcross.max < firstAcross.min) {
			firstValue = firstAcross.min;
			secondValue = secondAcross.max;
		} else {
			firstValue = Math.max(firstAcross.min, secondAcross.min);
			secondValue = firstValue;
		}
		return new Candidate(new Point(station, firstValue), new Point(station, secondValue));
	}

	private static Candidate intersection(Segment first, Segment second) {
		Point firstDirection = first.direction();
		Point secondDirection = second.direction();
		double determinant = firstDirection.cross(secondDirection);
		double scale = Math.sqrt(firstDirection.dot(firstDirection)*secondDirection.dot(secondDirection));
		if (Math.abs(determinant) <= RELATIVE_PARALLEL_TOLERANCE*scale)
			return null;
		Point offset = second.first.minus(first.first);
		double firstFraction = offset.cross(secondDirection)/determinant;
		double secondFraction = offset.cross(firstDirection)/determinant;
		if (firstFraction < 0d || firstFraction > 1d || secondFraction < 0d || secondFraction > 1d)
			return null;
		Point crossing = first.at(firstFraction);
		return new Candidate(crossing, crossing);
	}

	private static double clampFraction(double fraction) {
		return Math.max(0d, Math.min(1d, fraction));
	}

	private static List<Point> project(LocationList trace, LocalStrikeFrame frame) {
		List<Point> projected = new ArrayList<>();
		for (Location location : trace)
			projected.add(frame.toPoint(location));
		return projected;
	}

	private static List<Segment> segments(List<Point> points) {
		List<Segment> segments = new ArrayList<>();
		for (int i=1; i<points.size(); i++)
			segments.add(new Segment(points.get(i-1), points.get(i)));
		return segments;
	}

	private static Interval alongInterval(List<Point> points) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Point point : points) {
			min = Math.min(min, point.along);
			max = Math.max(max, point.along);
		}
		return new Interval(min, max);
	}

	private static Candidate best(Candidate first, Candidate second) {
		if (first == null)
			return second;
		if (second == null)
			return first;
		return second.length() < first.length() ? second : first;
	}

	/** Intersection bounds can be reversed to represent a projected gap. */
	private static class Interval {
		final double min;
		final double max;

		Interval(double min, double max) {
			this.min = min;
			this.max = max;
		}

		double length() {
			return max-min;
		}

		boolean contains(double value) {
			return value >= min-DISTANCE_TOLERANCE_KM && value <= max+DISTANCE_TOLERANCE_KM;
		}

		Interval intersection(Interval other) {
			return new Interval(Math.max(min, other.min), Math.min(max, other.max));
		}
	}

	private static class Point {
		final double along;
		final double across;

		Point(double along, double across) {
			this.along = along;
			this.across = across;
		}

		Point plus(Point other) {
			return new Point(along+other.along, across+other.across);
		}

		Point minus(Point other) {
			return new Point(along-other.along, across-other.across);
		}

		Point times(double scalar) {
			return new Point(along*scalar, across*scalar);
		}

		double dot(Point other) {
			return along*other.along+across*other.across;
		}

		double cross(Point other) {
			return along*other.across-across*other.along;
		}
	}

	private static class Segment {
		final Point first;
		final Point second;

		Segment(Point first, Point second) {
			this.first = first;
			this.second = second;
		}

		Point direction() {
			return second.minus(first);
		}

		Point at(double fraction) {
			return first.plus(direction().times(fraction));
		}

		Point project(Point point) {
			Point direction = direction();
			double squaredLength = direction.dot(direction);
			if (squaredLength == 0d)
				return first;
			return at(clampFraction(point.minus(first).dot(direction)/squaredLength));
		}

		Interval alongInterval() {
			return new Interval(Math.min(first.along, second.along), Math.max(first.along, second.along));
		}

		Interval acrossAt(double station) {
			if (Math.abs(second.along-first.along) <= DISTANCE_TOLERANCE_KM)
				return new Interval(Math.min(first.across, second.across), Math.max(first.across, second.across));
			double fraction = clampFraction((station-first.along)/(second.along-first.along));
			double across = at(fraction).across;
			return new Interval(across, across);
		}
	}

	private static class Candidate {
		final Point first;
		final Point second;

		Candidate(Point first, Point second) {
			this.first = first;
			this.second = second;
		}

		double length() {
			Point difference = second.minus(first);
			return Math.hypot(difference.along, difference.across);
		}
	}

	private static class LocalStrikeFrame {
		final Location origin;
		final double strikeRadians;

		LocalStrikeFrame(LocationList first, LocationList second, double strike) {
			double x = 0d;
			double y = 0d;
			double z = 0d;
			for (Location location : Arrays.asList(first.first(), first.last(), second.first(), second.last())) {
				double lat = Math.toRadians(location.lat);
				double lon = Math.toRadians(location.lon);
				x += Math.cos(lat)*Math.cos(lon);
				y += Math.cos(lat)*Math.sin(lon);
				z += Math.sin(lat);
			}
			Preconditions.checkArgument(Math.sqrt(x*x+y*y+z*z) > GEOGRAPHIC_CENTER_TOLERANCE,
					"Trace endpoints have no local geographic center");
			origin = new Location(Math.toDegrees(Math.atan2(z, Math.hypot(x, y))), Math.toDegrees(Math.atan2(y, x)));
			strikeRadians = Math.toRadians(strike);
		}

		Point toPoint(Location location) {
			double distance = LocationUtils.horzDistance(origin, location);
			double angle = LocationUtils.azimuthRad(origin, location)-strikeRadians;
			return new Point(distance*Math.cos(angle), distance*Math.sin(angle));
		}

		Location toLocation(Point point) {
			return LocationUtils.location(origin, strikeRadians+Math.atan2(point.across, point.along),
					Math.hypot(point.along, point.across));
		}
	}

	/**
	 * Optional finite-angle solver. Constraints are half-planes in the two segment
	 * fractions. The default and exact-perpendicular calculations do not use it.
	 */
	private static class AngularConnectorSearch {
		static Candidate find(List<Segment> first, List<Segment> second, double maxDeviationDegrees) {
			double tangentTolerance = Math.tan(Math.toRadians(maxDeviationDegrees));
			Candidate closest = null;
			for (Segment firstSegment : first) {
				for (Segment secondSegment : second) {
					Point firstDirection = firstSegment.direction();
					Point secondDirection = secondSegment.direction();
					Point offset = secondSegment.first.minus(firstSegment.first);
					for (int sign : new int[] {-1, 1}) {
						List<SegmentParameters> polygon = new ArrayList<>(Arrays.asList(new SegmentParameters(0, 0),
								new SegmentParameters(1, 0), new SegmentParameters(1, 1), new SegmentParameters(0, 1)));
						polygon = clip(polygon, sign*firstDirection.across, -sign*secondDirection.across, sign*offset.across);
						for (int direction : new int[] {-1, 1}) {
							double constant = direction*offset.along-tangentTolerance*sign*offset.across;
							double firstCoefficient = -direction*firstDirection.along+tangentTolerance*sign*firstDirection.across;
							double secondCoefficient = direction*secondDirection.along-tangentTolerance*sign*secondDirection.across;
							polygon = clip(polygon, firstCoefficient, secondCoefficient, -constant);
						}
						for (int i=0; i<polygon.size(); i++) {
							SegmentParameters start = polygon.get(i);
							SegmentParameters end = polygon.get((i+1)%polygon.size());
							Point startConnector = offset.plus(secondDirection.times(start.secondFraction))
									.minus(firstDirection.times(start.firstFraction));
							Point change = secondDirection.times(end.secondFraction-start.secondFraction)
									.minus(firstDirection.times(end.firstFraction-start.firstFraction));
							double fraction = 0d;
							if (change.dot(change) > 0d)
								fraction = clampFraction(-startConnector.dot(change)/change.dot(change));
							SegmentParameters parameters = start.interpolate(end, fraction);
							closest = best(closest, new Candidate(firstSegment.at(parameters.firstFraction),
									secondSegment.at(parameters.secondFraction)));
						}
					}
					closest = best(closest, intersection(firstSegment, secondSegment));
				}
			}
			return Preconditions.checkNotNull(closest, "No connector within angular limit");
		}

		/** Clips a convex parameter polygon to firstCoefficient*t + secondCoefficient*u <= bound. */
		private static List<SegmentParameters> clip(List<SegmentParameters> polygon,
				double firstCoefficient, double secondCoefficient, double bound) {
			List<SegmentParameters> clipped = new ArrayList<>();
			for (int i=0; i<polygon.size(); i++) {
				SegmentParameters start = polygon.get(i);
				SegmentParameters end = polygon.get((i+1)%polygon.size());
				double startValue = firstCoefficient*start.firstFraction+secondCoefficient*start.secondFraction-bound;
				double endValue = firstCoefficient*end.firstFraction+secondCoefficient*end.secondFraction-bound;
				boolean startInside = startValue <= DISTANCE_TOLERANCE_KM;
				boolean endInside = endValue <= DISTANCE_TOLERANCE_KM;
				if (startInside)
					clipped.add(start);
				if (startInside != endInside)
					clipped.add(start.interpolate(end, clampFraction(startValue/(startValue-endValue))));
			}
			return clipped;
		}

		/** Fractions along the first and second segments, each in [0, 1]. */
		private static class SegmentParameters {
			final double firstFraction;
			final double secondFraction;

			SegmentParameters(double firstFraction, double secondFraction) {
				this.firstFraction = firstFraction;
				this.secondFraction = secondFraction;
			}

			SegmentParameters interpolate(SegmentParameters other, double fraction) {
				return new SegmentParameters(firstFraction+(other.firstFraction-firstFraction)*fraction,
						secondFraction+(other.secondFraction-secondFraction)*fraction);
			}
		}
	}
}
