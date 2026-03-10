package org.opensha.sha.earthquake.faultSysSolution.subduction;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Function;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.faultSurface.DownDipSubsectionBuilder;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import net.mahdilamb.colormap.Colors;

/**
 * Utilities for transforming Slab2 contours (and potentially an upper trace of the deformation front) into subsections
 * with constant DDW and length. Various processing methods exist for cleaning up discontinuous contours and stitching
 * the upper trace in.
 */
public class InterfaceSubSectionBuilder {
	
	// inputs
	private int faultID;
	private String faultName;
	private FaultTrace upperTrace;
	private List<FaultTrace> rawContours;
	private double scaleLength;
	
	// intermediates
	private List<FaultTrace> depthContours = null;
	private List<FaultTrace> interpContours = null;
	
	// for plotting
	private GeographicMapMaker mapMaker;
	private List<List<FaultTrace>> stitchedIndividualDepthContours; // if plot is requested after stitching
	
	// params
	// if true, try to use scale length as a maximum, otherwise an average
	private boolean scaleIsMax = false;

	/**
	 * Reads depth contours in the GMT format used by Slab2 with elevations given in kilometers relative to sea level
	 * (nagetive-down):
	 * 
	 * <pre>
	 * > -590 contour -Z-590
	 * 138.5	27.802857402	-590
	 * 138.503675043	27.8	-590
	 * 138.55	27.7640522242	-590
	 * 138.566637765	27.75	-590
	 * > -580 contour -Z-580
	 * 138.35	27.9607361525	-580
	 * 138.367122051	27.95	-580
	 * 138.4	27.929769212	-580
	 * 138.444328374	27.9	-580
	 * ...
	 * </pre>
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public static List<FaultTrace> loadDepthContours(BufferedReader in) throws IOException {
		List<FaultTrace> ret = new ArrayList<>();
		FaultTrace curTrace = null;
		String line;
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.startsWith(">")) {
				// new contour
				curTrace = new FaultTrace();
				ret.add(curTrace);
				continue;
			}
			StringTokenizer tok = new StringTokenizer(line);
			Preconditions.checkState(tok.countTokens() == 3);
			double lon = Double.parseDouble(tok.nextToken());
			double lat = Double.parseDouble(tok.nextToken());
			double depth = -Double.parseDouble(tok.nextToken());
			curTrace.add(new Location(lat, lon, depth));;
		}
		return ret;
	}
	
	/**
	 * Loads USGS trench depth CSV file with columns: lon,lat,az,bound,slab,depth
	 * @param traceCSV
	 * @return
	 * @throws IOException
	 */
	public static FaultTrace loadTrenchDepthCSV(CSVFile<String> traceCSV) throws IOException {
		FaultTrace upper = new FaultTrace();
		AngleAverager upperAzAvg = new AngleAverager();
		for (int row=1; row<traceCSV.getNumRows(); row++) {
			double lon = traceCSV.getDouble(row, 0);
			double lat = traceCSV.getDouble(row, 1);
			double depth = traceCSV.getDouble(row, 5);
			upperAzAvg.add(traceCSV.getDouble(row, 2), 1d);
			upper.add(new Location(lat, lon, depth));
		}
		double upperAz = upperAzAvg.getAverage();
		double upperStrike = upper.getStrikeDirection();
		if (shouldReverse(upperAz, upperStrike)) {
			System.out.println("Upper is reversed: data gives az="+(float)upperAz+", calculated is "+(float)upperStrike);
			upper.reverse();
		}
		return upper;
	}
	
	public InterfaceSubSectionBuilder(int faultID, String faultName, FaultTrace upperTrace,
			List<FaultTrace> rawContours, double scaleLength) {
		this.faultID = faultID;
		this.faultName = faultName;
		this.upperTrace = upperTrace;
		this.rawContours = rawContours;
		this.scaleLength = scaleLength;
		Preconditions.checkState(rawContours != null && !rawContours.isEmpty(), "Must supply depth contours");
		Preconditions.checkState(upperTrace != null || rawContours.size() > 1, "Must supply at least 2 contours (or 1 and a trace)");
	}
	
	public void stitchContours() {
		stitchedIndividualDepthContours = new ArrayList<>();
		depthContours = processContours(rawContours, upperTrace, stitchedIndividualDepthContours);
	}
	
	public void setScaleIsMax(boolean scaleIsMax) {
		this.scaleIsMax = scaleIsMax;
	}
	
	public void buildInterpolatedDepthContours(Range<Double> depthRange) {
		buildInterpolatedDepthContours(depthRange, upperTrace != null, 200d);
	}
	
	public void buildInterpolatedDepthContours(Range<Double> depthRange, boolean smoothTraceDepthsForDDW, double traceSmoothDist) {
		if (depthContours == null) {
			// stitching was not requested, make sure they didn't need it
			List<FaultTrace> depthOrdered = new ArrayList<>();
			for (FaultTrace trace : rawContours) {
				double depth = trace.first().depth;
				for (Location loc : trace)
					Preconditions.checkState(Precision.equals(depth, loc.depth, DEPTH_PRECISION),
							"Depths vary within contours: %s != %s", (float)depth, (float)loc.depth);
				depthOrdered.add(trace);
			}
			
			// sort by depth increasing
			Collections.sort(depthOrdered, (o1, o2) -> Double.compare(o1.first().depth, o2.first().depth));
			
			// make sure no duplicates
			for (int i=1; i<rawContours.size(); i++) {
				double prevDepth = rawContours.get(i-1).first().depth;
				double depth = rawContours.get(i).first().depth;
				Preconditions.checkState(!Precision.equals(prevDepth, depth, DEPTH_PRECISION),
						"Duplicate contours exist for depth %s; stitch them first via stitchContours()",
						depth);
			}
			depthContours = depthOrdered;
		}
		List<FaultTrace> interpTraces;
		if (smoothTraceDepthsForDDW && upperTrace != null) {
			// smooth any jaggedness from the upper trace down-dip
			int numResample = 1000;
			FaultTrace upperResampled = FaultUtils.resampleTrace(upperTrace, numResample);
			double avgDepth = upperResampled.stream().mapToDouble(l -> l.depth).average().getAsDouble();
			double minDepth = upperTrace.stream().mapToDouble(l -> l.depth).min().getAsDouble();
			double maxDepth = upperTrace.stream().mapToDouble(l -> l.depth).max().getAsDouble();
			System.out.println("Averaging out depth of upper trace for DDW calculation; avg="
					+(float)avgDepth+" km, range=["+(float)minDepth+", "+(float)maxDepth+"]");
			
			boolean replaceInterpolatedWithUpper = depthRange == null || depthRange.contains(avgDepth);
			
			FaultTrace localLimit = FaultUtils.resampleTrace(depthContours.get(1), numResample);
			
			FaultTrace upperForDDW;
			if (Double.isFinite(traceSmoothDist) && traceSmoothDist > 0d) {
				double[] smoothed = new double[upperResampled.size()];
				double length = upperTrace.getTraceLength();
				
				System.out.println("\tUsing smoothing distance="+(float)traceSmoothDist+" (in either direction) for length="+(float)length);
				
				double lengthEach = length/(upperResampled.size()-1d);
				int numAway = (int)Math.round(traceSmoothDist/lengthEach);
				
				for (int i=0; i<upperResampled.size(); i++) {
					int count = 0;
					for (int j=Integer.max(0, i-numAway); j<=i+numAway && j<upperResampled.size(); j++) {
						smoothed[i] += upperResampled.get(j).depth;
						count++;
					}
					smoothed[i] /= (double)count;
					if (smoothed[i] >= localLimit.get(i).depth)
						// we smoothed past the next layer down, use that depth as a limit
						smoothed[i] = localLimit.get(i).depth;
				}
				for (int i=0; i<smoothed.length; i++) {
					Location loc = upperResampled.get(i);
					upperResampled.set(i, new Location(loc.lat, loc.lon, smoothed[i]));
				}
				upperForDDW = upperResampled;
				
				avgDepth = upperResampled.stream().mapToDouble(l -> l.depth).average().getAsDouble();
				minDepth = upperResampled.stream().mapToDouble(l -> l.depth).min().getAsDouble();
				maxDepth = upperResampled.stream().mapToDouble(l -> l.depth).max().getAsDouble();
				System.out.println("Smoothed upper trace for DDW calculation: avg="
						+(float)avgDepth+" km, range=["+(float)minDepth+", "+(float)maxDepth+"]");
			} else {
				upperForDDW = new FaultTrace(null, upperTrace.size());
				for (int i=0; i<upperTrace.size(); i++) {
					Location loc = upperTrace.get(i);
					upperForDDW.add(new Location(loc.lat, loc.lon, avgDepth));
				}
			}
			
			List<FaultTrace> depthContoursForDDW = new ArrayList<>(depthContours);
			depthContoursForDDW.set(0, upperForDDW);
			interpTraces = DownDipSubsectionBuilder.interpolateDepthTraces(
					depthContoursForDDW, 2, scaleLength, scaleIsMax, depthRange);
			if (replaceInterpolatedWithUpper) {
				System.out.println("Replacing interpolated upper with original");
				interpTraces.set(0, upperTrace);
			}
		} else {
			interpTraces = DownDipSubsectionBuilder.interpolateDepthTraces(
					depthContours, 2, scaleLength, scaleIsMax, depthRange);
		}
		
		System.out.println("Built "+interpTraces.size()+" interpolated traces");
		for (FaultTrace trace : interpTraces)
			System.out.println("\t"+traceStr(trace));
		this.interpContours = interpTraces;
	}
	
	public List<FaultTrace> getInterpolatedDepthContours() {
		Preconditions.checkNotNull(interpContours, "Interpolated depth contours have not yet been built.");
		return interpContours;
	}
	
	public GeoJSONFaultSection[][] buildSubSects() {
		return buildSubSects(false);
	}
	
	public GeoJSONFaultSection[][] buildSubSects(boolean constantCount) {
		Preconditions.checkNotNull(interpContours, "Interpolated depth contours have not yet been built.");
		Preconditions.checkState(interpContours.size() > 1, "Must have at least 2 interpolated depth contours");
		
		Geometry parentGeom = new Geometry.MultiLineString(List.of(interpContours.get(0),
				interpContours.get(interpContours.size()-1)));
		GeoJSONFaultSection refSect = new GeoJSONFaultSection.Builder(faultID, faultName, parentGeom)
				.aseismicity(0d).build();
		
		if (constantCount)
			return DownDipSubsectionBuilder.buildForConstantCountAlong(
					refSect, interpContours, 2, scaleLength, 0);
		else
			return DownDipSubsectionBuilder.buildForConstantLength(
					refSect, interpContours, 2, scaleLength, scaleIsMax, 0);
	}
	
	private static List<FaultTrace> processContours(List<FaultTrace> contours, FaultTrace upper) {
		return processContours(contours, upper, null);
	}
	
	private static double DEPTH_PRECISION = 0.1;
	
	private static List<FaultTrace> processContours(List<FaultTrace> contours, FaultTrace upper, List<List<FaultTrace>> stitchedIndividual) {
		// group by depth
		List<List<FaultTrace>> depthGrouped = new ArrayList<>();
		double curDepth = Double.NaN;
		List<FaultTrace> curTraces = null;
		for (FaultTrace trace : contours) {
			double depth = trace.first().depth;
			for (Location loc : trace)
				Preconditions.checkState(Precision.equals(depth, loc.depth, DEPTH_PRECISION),
						"Depths vary within contours: %s != %s", (float)depth, (float)loc.depth);
			if (curTraces == null || !Precision.equals(depth, curDepth, 0.1)) {
				curTraces = new ArrayList<>();
				curDepth = depth;
				depthGrouped.add(curTraces);
			}
			curTraces.add(trace);
		}
		
		// sort by depth increasing
		Collections.sort(depthGrouped, (o1, o2) -> Double.compare(o1.get(0).first().depth, o2.get(0).first().depth));
		
		// figure out overall strike direction using the middle trace
		List<FaultTrace> middleGroup = depthGrouped.get(depthGrouped.size()/2);
		Location[] furthestPair = null;
		double furthestDist = 0d;
		if (middleGroup.size() == 1) {
			FaultTrace trace = middleGroup.get(0);
			furthestDist = LocationUtils.horzDistanceFast(trace.first(), trace.last());
			furthestPair = new Location[] { trace.first(), trace.last() };
		} else {
			for (int i=0; i<middleGroup.size()-1; i++) {
				FaultTrace trace1 = middleGroup.get(i);
				Location[] locs1 = {trace1.first(), trace1.last()};
				for (int j=i+1; j<middleGroup.size(); j++) {
					FaultTrace trace2 = middleGroup.get(j);
					Location[] locs2 = {trace2.first(), trace2.last()};
					for (Location l1 : locs1) {
						for (Location l2 : locs2) {
							double dist = LocationUtils.horzDistanceFast(l1, l2);
							if (dist > furthestDist) {
								furthestDist = dist;
								furthestPair = new Location[] { l1, l2 };
							}
						}
					}
				}
			}
		}
		// that gives us an overall strike direction, but could be reversed relative to the RHR
		double strike = LocationUtils.azimuth(furthestPair[0], furthestPair[1]);
		double[] downAzimuths = new double[depthGrouped.size()-1];
		Location upperMiddle = avgLocation(depthGrouped.get(0));
		for (int i=1; i<depthGrouped.size(); i++) {
			Location lowerMiddle = avgLocation(depthGrouped.get(i));
			
			double dipAz = LocationUtils.azimuth(upperMiddle, lowerMiddle);
			downAzimuths[i-1] = dipAz;
			upperMiddle = lowerMiddle;
		}
		double dipDir = DataUtils.median(downAzimuths);
		// this handles wrapping and returns a value in [0, 360]
		System.out.println("Detected middle azimuth="+oDF.format(strike)+" and dip-dir azimuth="+oDF.format(dipDir));
		if (shouldReverse(strike+90d, dipDir))
			strike = LocationUtils.azimuth(furthestPair[1], furthestPair[0]);
		System.out.println("Detected middle strike="+oDF.format(strike)+" with dip direction of "
				+oDF.format(dipDir)+" (delta="+FaultUtils.getAbsAngleDiff(strike, dipDir)+")");

		// now stitch
		List<FaultTrace> stitched = new ArrayList<>(depthGrouped.size()+1);
		
		double upperMaxDepth = 0d;
		FaultTrace resampledUpper = null;
		if (upper != null) {
			for (Location loc : upper)
				upperMaxDepth = Math.max(upperMaxDepth, loc.depth);
			
			// add the upper trace first
			stitched.add(upper);
		}
		
		for (List<FaultTrace> depthTraces : depthGrouped) {
			double depth = depthTraces.get(0).first().depth;
			System.out.println(oDF.format(depth)+" km: "+depthTraces.size()+" contours");
			
			if (depthTraces.size() == 1) {
				// simple case
				FaultTrace trace = depthTraces.get(0);
				double az = trace.getStrikeDirection();
				boolean reverse = shouldReverse(strike, az, 70d);
				System.out.println("\taz="+oDF.format(az)+", reverse="+reverse);
				if (reverse)
					trace = getReversed(trace);
				stitched.add(trace);
				continue;
			}
			
			// find the most central trace and work out from there
			Location centerLoc = avgLocation(depthTraces);
			
			int centralIndex = -1;
			double centerDist = Double.POSITIVE_INFINITY;
			
			for (int i=0; i<depthTraces.size(); i++) {
				for (Location loc : depthTraces.get(i)) {
					double dist = LocationUtils.horzDistanceFast(loc, centerLoc);
					if (dist < centerDist) {
						centerDist = dist;
						centralIndex = i;
					}
				}
			}
			
			// now build it out
			List<FaultTrace> candidates = new ArrayList<>(depthTraces);
			FaultTrace central = candidates.remove(centralIndex);
			List<FaultTrace> stitchedList = new ArrayList<>(depthTraces.size());
			stitchedList.add(central);
			Location curFirst = central.first();
			Location curLast = central.last();
			while (!candidates.isEmpty()) {
				// find the closest location to either end
				int closestIndex = -1;
				boolean closestReversed = false;
				boolean closestToFirst = false;
				double minDist = Double.POSITIVE_INFINITY;
				for (int i=0; i<candidates.size(); i++) {
					FaultTrace candidate = candidates.get(i);
					Location first = candidate.first();
					Location last = candidate.last();
					double distFF = LocationUtils.horzDistanceFast(curFirst, first);
					double distFL = LocationUtils.horzDistanceFast(curFirst, last);
					double distLF = LocationUtils.horzDistanceFast(curLast, first);
					double distLL = LocationUtils.horzDistanceFast(curLast, last);
					double dist = Math.min(Math.min(distFF, distFL), Math.min(distLF, distLL));
					if (dist < minDist) {
						minDist = dist;
						closestIndex = i;
						if (distFF == dist || distFL == dist)
							closestToFirst = true;
						if (distFF == dist || distLL == dist)
							closestReversed = true;
					}
				}
				FaultTrace closest = candidates.remove(closestIndex);
				if (closestReversed)
					closest = getReversed(closest);
				if (closestToFirst) {
					stitchedList.add(0, closest);
					curFirst = closest.first();
				} else {
					stitchedList.add(closest);
					curLast = closest.last();
				}
			}
			
			double stitchedAz = LocationUtils.azimuth(curFirst, curLast);
			if (shouldReverse(strike, stitchedAz, 70d)) {
				// flip everything
				Collections.reverse(stitchedList);
				for (int i=0; i<stitchedList.size(); i++)
					stitchedList.set(i, getReversed(stitchedList.get(i)));
			}
			
			if (upper != null && (depth < upperMaxDepth || Precision.equals(depth, upperMaxDepth, 2d))) {
				// see if we can stitch anything in from the upper trace
				System.out.println("Will try to stitch in from the upper trace that dips below this one (down to "
						+(float)upperMaxDepth+" km); upper strike: "+upper.getStrikeDirection());
				if (resampledUpper == null) {
					resampledUpper = FaultUtils.resampleTrace(upper, (int)Math.max(1000, upper.getTraceLength()/0.1));
					Preconditions.checkState(!shouldReverse(strike, resampledUpper.getStrikeDirection()));
				}
				List<FaultTrace> modStitchedList = new ArrayList<>();
				modStitchedList.add(stitchedList.get(0));
				for (int s=1; s<stitchedList.size(); s++) {
					FaultTrace before = stitchedList.get(s-1);
					FaultTrace after = stitchedList.get(s);
					
					if (before.size() > 1 && after.size() > 1) {
						// draw lines extending the trace before and after this gap toward the gap
						Location before1 = before.get(before.size()-2);
						Location before2 = before.last();
						Location after1 = after.get(1);
						Location after2 = after.first();
						
						// and draw lines perpendicular to those lines that will be used to find trace points between them
						// we'll make sure the trace points are to the right (positive in LocationUtils methods) of
						// these lines
						Location beforePerp1 = before2;
						// azimuth is the direction - 90 degrees
						Location beforePerp2 = LocationUtils.location(beforePerp1,
								LocationUtils.azimuthRad(before1, before2) - Math.PI*0.5, 10d);
						
						Location afterPerp1 = after2;
						// azimuth is the direction - 90 degrees
						Location afterPerp2 = LocationUtils.location(afterPerp1,
								LocationUtils.azimuthRad(after1, after2) - Math.PI*0.5, 10d);
						
						// within this distance from either end, we'll try to stop at the projection from the adjacent
						// segment to get a smooth interpolation
//						double gapDistance = LocationUtils.horzDistanceFast(before2, after1);
//						double lineCheckDist = Math.max(10d, gapDistance * 0.2);
						// on second thought, it seems to do better always forcing this check
						double lineCheckDist = Double.POSITIVE_INFINITY;
						
						FaultTrace addition = null;
						
						for (int i=1; i<resampledUpper.size()-1; i++) {
							Location loc = resampledUpper.get(i);
							// these methods ignore depth, which is good here
							boolean inside = LocationUtils.distanceToLineFast(beforePerp1, beforePerp2, loc) >= 0d
									&& LocationUtils.distanceToLineFast(afterPerp1, afterPerp2, loc) >= 0d;
							if (inside) {
								// we're within the envelope of this missing piece
								
								boolean keep = false;
								if (addition == null) {
									// first one that's inside, see if we have crossed the projected line
									double beforeDist = LocationUtils.horzDistanceFast(before2, loc);
									if (beforeDist < lineCheckDist) {
										// check against the projected line and wait until we've crossed it to the right
										if (LocationUtils.distanceToLineFast(before1, before2, loc) >= 0) {
											// we're on the right of the projected line, start tracking
											addition = new FaultTrace(ADDITION_FROM_UPPER_NAME);
											keep = true;
										}
									} else {
										// far enough away, just start tracking
										addition = new FaultTrace();
										keep = true;
									}
								} else {
									if (LocationUtils.horzDistanceFast(loc, after1) < lineCheckDist) {
										// we're near the end, start checking against that line
										if (LocationUtils.distanceToLineFast(after1, after2, loc) >= 0) {
											// we're back to the left of the contour (to the right of the back-line)
											break;
										}
									}
									keep = true;
								}
								if (keep) {
//									addition.add(loc);
									addition.add(new Location(loc.lat, loc.lon, depth));
								}
							} else if (addition != null) {
								// already found it, break
								break;
							}
						}
						if (addition != null) {
							modStitchedList.add(addition);
							System.out.println("Stitched in an addition from the upper trace:\t"+traceStr(addition));
						}
					}
					
					modStitchedList.add(after);
				}
				Preconditions.checkState(modStitchedList.size() >= stitchedList.size());
				stitchedList = modStitchedList;
			}
			
			if (stitchedIndividual != null)
				stitchedIndividual.add(stitchedList);
			
			// build final trace and print distances
			FaultTrace stitchedTrace = new FaultTrace();
			for (int i=0; i<stitchedList.size(); i++) {
				FaultTrace trace = stitchedList.get(i);
				if (i == 0)
					System.out.println("\t0. "+traceStr(trace));
				else
					System.out.println("\t"+i+". "+oDF.format(LocationUtils.horzDistanceFast(stitchedTrace.last(), trace.first()))
							+" km away;\t"+traceStr(trace));
				stitchedTrace.addAll(trace);
			}
			stitched.add(stitchedTrace);
		}
		
		return stitched;
	}
	
	private static final String ADDITION_FROM_UPPER_NAME = "Stitched From Upper Trace";
	
	private static List<FaultTrace> filterContours(List<FaultTrace> contours, Range<Double> latRange, Range<Double> lonRange) {
		List<FaultTrace> ret = new ArrayList<>();
		
		for (FaultTrace trace : contours) {
			FaultTrace cutTrace = filterContour(trace, latRange, lonRange);
			if (cutTrace != null)
				ret.add(cutTrace);
		}
		return ret;
	}
	
	private static FaultTrace filterContour(FaultTrace contour, Range<Double> latRange, Range<Double> lonRange) {
		return filterContour(contour, L->{
			return (latRange == null || latRange.contains(L.lat))
					&& (lonRange == null || lonRange.contains(L.lon));
		});
	}
	
	private static FaultTrace filterContour(FaultTrace contour, Function<Location, Boolean> insideTest) {
		int firstInside = -1;
		int lastInside = -1;
		boolean allInside = true;
		boolean noneInside = true;
		for (int i=0; i<contour.size(); i++) {
			Location loc = contour.get(i);
			if (insideTest.apply(loc)) {
				if (firstInside < 0)
					firstInside = i;
				lastInside = i;
				noneInside = false;
			} else {
				allInside = false;
			}
		}
		if (noneInside)
			return null;
		if (allInside)
			return contour;
		
		// need to cut it
		FaultTrace cutTrace = new FaultTrace(null, 3+lastInside-firstInside);
		if (firstInside > 1) {
			// try to interpolate
			Location first = findFurthestInside(contour.get(firstInside), contour.get(firstInside-1), insideTest);
			if (first != null)
				cutTrace.add(first);
		}
		for (int i=firstInside; i<=lastInside; i++)
			cutTrace.add(contour.get(i));
		if (lastInside < contour.size()-1) {
			// try to interpolate
			Location last = findFurthestInside(contour.get(lastInside), contour.get(lastInside+1), insideTest);
			if (last != null)
				cutTrace.add(last);
		}
		
		return cutTrace;
	}
	
	private static List<FaultTrace> filterContoursTracePerpenducular(List<FaultTrace> contours, FaultTrace upperTrace,
			boolean isStart, double backTraceDist) {
		if (backTraceDist > 0d)
			upperTrace = FaultUtils.resampleTrace(upperTrace, Integer.max(100, 10*(int)(upperTrace.getTraceLength()/backTraceDist)));
		Location traceLoc, prevTraceLoc;
		if (isStart) {
			traceLoc = upperTrace.get(0);
			if (backTraceDist > 0d) {
				double dist = 0;
				prevTraceLoc = traceLoc;
				for (int i=1; i<upperTrace.size(); i++) {
					Location loc = upperTrace.get(i);
					dist += LocationUtils.horzDistanceFast(prevTraceLoc, loc);
					if (dist > backTraceDist)
						break;
					prevTraceLoc = loc;
				}
			} else {
				prevTraceLoc = upperTrace.get(1);
			}
		} else {
			traceLoc = upperTrace.get(upperTrace.size()-1);
			if (backTraceDist > 0d) {
				double dist = 0;
				prevTraceLoc = traceLoc;
				for (int i=upperTrace.size()-1; --i>=0;) {
					Location loc = upperTrace.get(i);
					dist += LocationUtils.horzDistanceFast(prevTraceLoc, loc);
					if (dist > backTraceDist)
						break;
					prevTraceLoc = loc;
				}
			} else {
				prevTraceLoc = upperTrace.get(upperTrace.size()-2);
			}
		}
		double traceAz = LocationUtils.azimuthRad(prevTraceLoc, traceLoc);
		Location rightLoc = LocationUtils.location(traceLoc, traceAz + Math.PI/2d, 50d);
		List<FaultTrace> ret = new ArrayList<>();
		
		Function<Location, Boolean> insideTest = L->{
			return LocationUtils.distanceToLineFast(traceLoc, rightLoc, L) >= 0d;
		};
		
		for (FaultTrace trace : contours) {
			FaultTrace cutTrace = filterContour(trace, insideTest);
			if (cutTrace != null)
				ret.add(cutTrace);
		}
		return ret;
	}
	
	private static Location findFurthestInside(Location from, Location to, Range<Double> latRange, Range<Double> lonRange) {
		return findFurthestInside(from, to, L->{
			return (latRange == null || latRange.contains(L.lat))
					&& (lonRange == null || lonRange.contains(L.lon));
		});
	}
	
	private static Location findFurthestInside(Location from, Location to, Region region) {
		return findFurthestInside(from, to, L->region.contains(L));
	}
	
 	private static Location findFurthestInside(Location from, Location to, Function<Location, Boolean> insideTest) {
		FaultTrace resampled = new FaultTrace(null, 2);
		resampled.add(from);
		resampled.add(to);
		resampled = FaultUtils.resampleTrace(resampled, 100);
		Location lastInside = null;
		Location firstOutside = null;
		for (int i=1; i<resampled.size(); i++) {
			Location loc = resampled.get(i);
			if (insideTest.apply(loc)) {
				lastInside = loc;
				firstOutside = null;
			} else if (firstOutside == null) {
				firstOutside = loc;
			}
		}
		if (firstOutside == null) {
			// re-interpolate zoomed in
			resampled = new FaultTrace(null, 2);
			resampled.add(lastInside);
			resampled.add(firstOutside);
			resampled = FaultUtils.resampleTrace(resampled, 20);
			lastInside = null;
			firstOutside = null;
			for (int i=0; i<resampled.size(); i++) {
				Location loc = resampled.get(i);
				if (insideTest.apply(loc)) {
					lastInside = loc;
					firstOutside = null;
				} else if (firstOutside == null) {
					firstOutside = loc;
				}
			}
		}
		return lastInside;
	}
	
	private static final DecimalFormat twoDF = new DecimalFormat("0.00");
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	private static String traceStr(FaultTrace trace) {
		boolean resampled = false;
		double avgDepth = trace.stream().mapToDouble(l->l.depth).average().getAsDouble();
		if (trace.size() > 5) {
			trace = FaultUtils.resampleTrace(trace, 4);
			resampled = true;
		}
		StringBuilder str = null;
		for (Location loc : trace) {
			if (str == null)
				str = new StringBuilder();
			else
				str.append(" ");
			str.append("[").append(twoDF.format(loc.lat)).append(", ").append(twoDF.format(loc.lon))
					.append(", ").append(oDF.format(loc.depth)).append("]");
		}
		str.append("; strike=").append(oDF.format(LocationUtils.azimuth(trace.first(), trace.last())));
		str.append(", avg depth=").append(oDF.format(avgDepth)).append(" km");
		if (resampled)
			str.append(" (resampled)");
		return str.toString();
	}
	
	private static Location avgLocation(LocationList locs) {
		return avgLocation(List.of(locs));
	}
	
	private static Location avgLocation(List<? extends LocationList> locLists) {
		double sumLat = 0d;
		double sumLon = 0d;
		double sumDepth = 0d;
		int count = 0;
		for (LocationList list : locLists) {
			for (Location loc : list) {
				count++;
				sumLat += loc.lat;
				sumLon += loc.lon;
				sumDepth += loc.depth;
			}
		}
		return new Location(sumLat/(double)count, sumLon/(double)count, sumDepth/(double)count);
	}
	
	private static final double AZ_TOL_DEFAULT = 50d;
	
	private static FaultTrace getOriented(FaultTrace trace, double overallStrike) {
		double traceStrike = trace.getStrikeDirection();
		if (shouldReverse(overallStrike, traceStrike, AZ_TOL_DEFAULT))
			return getReversed(trace);
		return trace;
	}
	
	private static boolean shouldReverse(double refStrike, double testStrike) {
		return shouldReverse(refStrike, testStrike, AZ_TOL_DEFAULT);
	}
	
	private static boolean shouldReverse(double refStrike, double testStrike, double tolerance) {
		double diff = FaultUtils.getAbsAngleDiff(refStrike, testStrike);
		Preconditions.checkState(diff >= 0d && diff <= 180d, "bad diff=%s for (%s - %s)", diff, refStrike, testStrike);
		if (diff <= tolerance)
			return false;
		if (diff > 180d-tolerance)
			return true;
		throw new IllegalStateException("Couldn't determine if the trace should be reversed: ref="
				+oDF.format(refStrike)+", dest="+oDF.format(testStrike)+", diff="+oDF.format(diff));
	}
	
	private static FaultTrace getReversed(FaultTrace trace) {
		FaultTrace reversed = new FaultTrace(trace.getName(), trace.size());
		for (int i=trace.size(); --i>=0;)
			reversed.add(trace.get(i));
		return reversed;
	}
	
	private void checkInitMapMaker() {
		if (mapMaker == null) {
			MinMaxAveTracker latRange = new MinMaxAveTracker();
			MinMaxAveTracker lonRange = new MinMaxAveTracker();
			for (FaultTrace trace : rawContours) {
				for (Location loc : trace) {
					latRange.addValue(loc.lat);
					lonRange.addValue(loc.lon);
				}
			}
			if (upperTrace != null) {
				for (Location loc : upperTrace) {
					latRange.addValue(loc.lat);
					lonRange.addValue(loc.lon);
				}
			}
			
			Region mapReg = new Region(new Location(latRange.getMin()-1d, lonRange.getMin()-1d),
					new Location(latRange.getMax()+1d, lonRange.getMax()+1d));
			mapMaker = new GeographicMapMaker(mapReg);
		}
	}
	
	public GeographicMapMaker getMapMaker() {
		checkInitMapMaker();
		return mapMaker;
	}
	
	private static final PlotCurveCharacterstics rawChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
	private static final PlotCurveCharacterstics rawCharLight = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
	private static final PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Colors.tab_orange);
	
	public void writeRawContourPlot(File outputDir, String prefix) throws IOException {
		checkInitMapMaker();
		List<LocationList> lines = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		for (FaultTrace raw : rawContours) {
			lines.add(raw);
			chars.add(rawChar);
		}
		
		if (upperTrace != null) {
			lines.add(upperTrace);
			chars.add(traceChar);
		}
		mapMaker.plotLines(lines, chars);
		mapMaker.plot(outputDir, prefix+"_raw", " ");
		mapMaker.clearLines();
	}
	
	
	public void writeContourStitchPlot(File outputDir, String prefix) throws IOException {
		checkInitMapMaker();
		Preconditions.checkState(depthContours != null);
		
		List<LocationList> lines = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotCurveCharacterstics processedChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		for (FaultTrace processed : depthContours) {
			lines.add(processed);
			chars.add(processedChar);
		}
		
		if (stitchedIndividualDepthContours != null) {
			PlotCurveCharacterstics connectorChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Colors.tab_red);
			for (List<FaultTrace> stitched : stitchedIndividualDepthContours) {
				for (int i=1; i<stitched.size(); i++) {
					FaultTrace prev = stitched.get(i-1);
					FaultTrace cur = stitched.get(i);
					LocationList connector = LocationList.of(prev.last(), cur.first());
					lines.add(connector);
					chars.add(connectorChar);
					if (cur.getName() != null && cur.getName().equals(ADDITION_FROM_UPPER_NAME)) {
						lines.add(cur);
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, traceChar.getColor()));
					}
				}
			}
		}
		mapMaker.plotLines(lines, chars);
		LocationList startEnds = new LocationList();
		List<PlotCurveCharacterstics> startEndChars = new ArrayList<>();
		PlotCurveCharacterstics startChar = new PlotCurveCharacterstics(PlotSymbol.FILLED_SQUARE, 2f, Colors.tab_blue);
		PlotCurveCharacterstics endChar = new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 2f, Colors.tab_green);
		for (FaultTrace trace : depthContours) {
			startEnds.add(trace.first());
			startEndChars.add(startChar);
		}
		for (FaultTrace trace : depthContours) {
			startEnds.add(trace.last());
			startEndChars.add(endChar);
		}
		mapMaker.plotScatters(startEnds, startEndChars);
		
		mapMaker.plot(outputDir, prefix, " ");
		mapMaker.clearScatters();
		mapMaker.clearLines();
	}
	
	public void writeInterpolatedDepthContoursPlot(File outputDir, String prefix) throws IOException {
		Preconditions.checkState(interpContours != null);
		checkInitMapMaker();
		
		List<LocationList> lines = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		for (FaultTrace raw : rawContours) {
			lines.add(raw);
			chars.add(rawCharLight);
		}
		
		PlotCurveCharacterstics interpChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.2f, Color.BLACK);
		for (FaultTrace interp : interpContours) {
			lines.add(interp);
			chars.add(interpChar);
		}

		mapMaker.plotLines(lines, chars);
		mapMaker.plot(outputDir, prefix, " ");
		mapMaker.clearLines();
	}
	
	public void writeSubSectPlot(File outputDir, String prefix, GeoJSONFaultSection[][] subSects) throws IOException {
		checkInitMapMaker();
		int sectMod = 20;
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, sectMod-1d);
		
		List<FaultSection> allSects = new ArrayList<>();
		for (GeoJSONFaultSection[] row : subSects)
			for (GeoJSONFaultSection sect : row)
				allSects.add(sect);
		mapMaker.setFaultSections(allSects);
		mapMaker.plotSectScalars(s -> {return (double)(s.getSubSectionIndex() % sectMod);},
				cpt, null);
//		mapMaker.plotSectScalarsByIndex(I -> {return (double)(I % sectMod);},
//				cpt, "Subsection Index");
		mapMaker.setPlotSectsOnTop(true);

		List<LocationList> lines = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		for (FaultTrace raw : rawContours) {
			lines.add(raw);
			chars.add(rawCharLight);
		}
		
		PlotCurveCharacterstics interpChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.2f, Color.BLACK);
		for (FaultTrace interp : interpContours) {
			lines.add(interp);
			chars.add(interpChar);
		}

		mapMaker.plotLines(lines, chars);
		mapMaker.plot(outputDir, prefix, " ");
		mapMaker.clearLines();
		mapMaker.clearFaultSections();
	}

	public static void main(String[] args) throws IOException {
		File baseOutputDir = new File("/home/kevin/OpenSHA/nshm26/down-dip-subsectioning");
		Preconditions.checkState(baseOutputDir.exists() || baseOutputDir.mkdir());
		
		double backTraceDist = 100d;
		
		String faultName = "Tonga Trench";
		int faultID = 6700;
		String resourcePrefix = "/data/erf/nshm26/amsam/fault_models/subduction/";
		BufferedReader countoursIn = new BufferedReader(new InputStreamReader(
				InterfaceSubSectionBuilder.class.getResourceAsStream(resourcePrefix+"ker_slab2_dep_10km_contours.xyz")));
		CSVFile<String> traceCSV = CSVFile.readStream(
				InterfaceSubSectionBuilder.class.getResourceAsStream(resourcePrefix+"trenches_usgs_2017_depths_ker.csv"), true);
		boolean smoothTraceForDDW = true;
		String prefix = "ker_slab2";
		Range<Double> depthRange = Range.closed(0d, 60d);
		// these happen before cleanup
		Range<Double> preLonFilter = null;
		Range<Double> preLatFilter = Range.atLeast(-30d);
		// these happen after cleanup
		Range<Double> postLonFilter = null;
		Range<Double> postLatFilter = Range.atLeast(-23.7335);
		boolean filterStartPerpendicular = false;
		boolean filterEndPerpendicular = true;
		
//		String faultName = "Mariana Trench";
//		int faultID = 6200;
//		String resourcePrefix = "/data/erf/nshm26/gnmi/fault_models/subduction/";
//		BufferedReader countoursIn = new BufferedReader(new InputStreamReader(
//				InterfaceSubSectionBuilder.class.getResourceAsStream(resourcePrefix+"izu_slab2_dep_10km_contours.xyz")));
//		CSVFile<String> traceCSV = CSVFile.readStream(
//				InterfaceSubSectionBuilder.class.getResourceAsStream(resourcePrefix+"trenches_usgs_2017_depths_izu.csv"), true);
//		boolean smoothTraceForDDW = true;
//		String prefix = "izu_slab2";
//		Range<Double> depthRange = Range.closed(0d, 60d);
//		Range<Double> preLonFilter = null;
//		Range<Double> preLatFilter = Range.atMost(23.123577);
//		Range<Double> postLonFilter = null;
//		Range<Double> postLatFilter = null;
//		boolean filterStartPerpendicular = true;
//		boolean filterEndPerpendicular = false;
		
		double scaleLength = 20d;
		double traceSmoothDist = 200d;
		
		File outputDir = new File(baseOutputDir, prefix);
		Preconditions.checkArgument(outputDir.exists() || outputDir.mkdir());
		
//		File sectOutputDir = outputDir;
		File sectOutputDir = new File("/home/kevin/workspace/opensha/src/main/resources"+resourcePrefix);
		
		List<FaultTrace> rawContours = loadDepthContours(countoursIn);
		FaultTrace upperTrace = loadTrenchDepthCSV(traceCSV);
		
		if (preLatFilter != null || preLonFilter != null) {
			System.out.println("Pre-filtering contours");
			int origCount = rawContours.size();
			upperTrace = filterContour(upperTrace, preLatFilter, preLonFilter);
			if (postLatFilter == null && postLonFilter == null && (filterStartPerpendicular || filterEndPerpendicular)) {
				if (filterStartPerpendicular)
					rawContours = filterContoursTracePerpenducular(rawContours, upperTrace, true, backTraceDist);
				if (filterEndPerpendicular)
					rawContours = filterContoursTracePerpenducular(rawContours, upperTrace, false, backTraceDist);
			} else {
				rawContours = filterContours(rawContours, preLatFilter, preLonFilter);
			}
			rawContours = filterContours(rawContours, preLatFilter, preLonFilter);
			System.out.println("Retained "+rawContours.size()+"/"+origCount+" contours");
		}
		
		InterfaceSubSectionBuilder builder = new InterfaceSubSectionBuilder(faultID, faultName, upperTrace, rawContours, scaleLength);
		
		builder.writeRawContourPlot(outputDir, prefix+"_raw");
		
		builder.stitchContours();
		
		builder.writeContourStitchPlot(outputDir, prefix+"_contour_stitch");
		
		if (postLatFilter != null || postLonFilter != null) {
			System.out.println("Post-filtering contours");
			rawContours = new ArrayList<>(builder.depthContours);
			rawContours.remove(0); // remove upper trace
			int origCount = rawContours.size();
			upperTrace = filterContour(upperTrace, postLatFilter, postLonFilter);
			if (filterStartPerpendicular || filterEndPerpendicular) {
				if (filterStartPerpendicular)
					rawContours = filterContoursTracePerpenducular(rawContours, upperTrace, true, backTraceDist);
				if (filterEndPerpendicular)
					rawContours = filterContoursTracePerpenducular(rawContours, upperTrace, false, backTraceDist);
			} else {
				rawContours = filterContours(rawContours, postLatFilter, postLonFilter);
			}
			System.out.println("Retained "+rawContours.size()+"/"+origCount+" contours");
			
			builder = new InterfaceSubSectionBuilder(faultID, faultName, upperTrace, rawContours, scaleLength);
		}
		
		builder.buildInterpolatedDepthContours(depthRange, smoothTraceForDDW, traceSmoothDist);
		
		builder.writeInterpolatedDepthContoursPlot(outputDir, prefix+"_interp");
		
		GeoJSONFaultSection[][] subSects = builder.buildSubSects();
		
		RupSetScalingRelationship scale = PRVI25_SubductionScalingRelationships.LOGA_C4p0;
		
		MinMaxAveTracker lenRange = new MinMaxAveTracker();
		MinMaxAveTracker ddwRange = new MinMaxAveTracker();
		MinMaxAveTracker areaRange = new MinMaxAveTracker();
		MinMaxAveTracker magRange = new MinMaxAveTracker();
		MinMaxAveTracker slipAtMminRange = new MinMaxAveTracker();
		MinMaxAveTracker dipRange = new MinMaxAveTracker();
		MinMaxAveTracker perRowRange = new MinMaxAveTracker();
		double sumArea = 0d;
		for (GeoJSONFaultSection[] row : subSects) {
			perRowRange.addValue(row.length);
			for (GeoJSONFaultSection sect : row) {
				double length = sect.getTraceLength();
				double ddw = sect.getOrigDownDipWidth();
				double area = sect.getArea(false)*1e-6;
				sumArea += area;
				lenRange.addValue(length);
				ddwRange.addValue(ddw);
				areaRange.addValue(area);
				magRange.addValue(scale.getMag(area*1e6, length*1e3, ddw*1e3, ddw*1e3, 90d));
				slipAtMminRange.addValue(scale.getAveSlip(area*1e6, length*1e3, ddw*1e3, ddw*1e3, 90d));
				dipRange.addValue(sect.getAveDip());
			}
		}
		
		System.out.println("Sub-Sect Lenghts:\t"+lenRange);
		System.out.println("Sub-Sect DDWs:\t"+ddwRange);
		System.out.println("Sub-Sect Areas:\t"+areaRange);
		System.out.println("Sub-Sect Mmin:\t"+magRange);
		System.out.println("Sub-Sect slip at Mmin:\t"+slipAtMminRange);
		System.out.println("Sub-Sect Dip:\t"+dipRange);
		System.out.println("Sub-Sect Row counts:\t"+perRowRange);
		
		System.out.println();
		System.out.println("Row average stats:");
		List<FaultSection> allSects = new ArrayList<>();
		for (int row=0; row<subSects.length; row++) {
			MinMaxAveTracker upperDepthTrack = new MinMaxAveTracker();
			MinMaxAveTracker lowerDepthTrack = new MinMaxAveTracker();
			dipRange = new MinMaxAveTracker();
			magRange = new MinMaxAveTracker();
			for (FaultSection sect : subSects[row]) {
				upperDepthTrack.addValue(sect.getOrigAveUpperDepth());
				lowerDepthTrack.addValue(sect.getAveLowerDepth());
				dipRange.addValue(sect.getAveDip());
				double area = sect.getArea(false)*1e-6;
				magRange.addValue(scale.getMag(
						area*1e6, Double.NaN, Double.NaN, Double.NaN, 90d));
				allSects.add(sect);
			}
			System.out.println("\tRow "+row+" ("+subSects[row].length+" sects):\t"
					+ "upper="+twoDF.format(upperDepthTrack.getAverage())
					+ ", lower="+twoDF.format(lowerDepthTrack.getAverage())
					+ ", dip="+twoDF.format(dipRange.getAverage())
					+ ", Mmin="+twoDF.format(magRange.getAverage()));
		}
		System.out.println("Built "+allSects.size()+" total subsections");
		
		System.out.println("Mmax="+twoDF.format(scale.getMag(sumArea*1e6, Double.NaN, Double.NaN, Double.NaN, Double.NaN)));
		System.out.println("Slip at Mmax="+twoDF.format(scale.getAveSlip(sumArea*1e6, Double.NaN, Double.NaN, Double.NaN, Double.NaN)));
		
		builder.writeSubSectPlot(outputDir, prefix+"_sub_sects_map", subSects);
		
		List<FaultTrace> interpTraces = builder.getInterpolatedDepthContours();
		
		File geoJSONFile = new File(sectOutputDir, "sub_sections.geojson");
		GeoJSONFaultSection fullSect = new GeoJSONFaultSection.Builder(faultID, faultName,
				new Geometry.MultiLineString(List.of(interpTraces.get(0), interpTraces.get(interpTraces.size()-1))))
				.rake(90d)
				.aseismicity(0d)
				.build();
		FeatureCollection.write(new FeatureCollection(fullSect.toFeature()), new File(sectOutputDir, "full_section.geojson"));
		
		GeoJSONFaultReader.writeFaultSections(geoJSONFile, allSects);
	}

}
