package org.opensha.commons.util;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;

/**
 * <b>Title:</b> FaultUtils<p>
 *
 * <b>Description:</b> Collection of static utilities used in conjunction with
 * strike, dip and rake angles of faults. These functions are assertion functions,
 * in that they validate the angles as valid strike, dip, and rake angles, and
 * return true or false if valid. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public final class FaultUtils {

	/** Class name used for debug strings */
	protected final static String C = "FaultUtils";
	/** boolean that indicates if print out debug statements */
	protected final static boolean D = false;

	/** debugging string */
	private static final String S1 = C + ": assertValidStrike(): ";
	/** debugging string */
	private static final String S2 = C + ": assertValidDip(): ";
	/** debugging string */
	private static final String S3 = C + ": assertValidRake(): ";



	/**
	 * Checks that the strike angle fits within the definition<p>
	 * <code>0 <= strike <= 360</code><p>
	 * @param strike                    Angle to validate
	 * @throws InvalidRangeException    Thrown if not valid angle
	 */
	public static void assertValidStrike( double strike)
			throws InvalidRangeException
			{

		if( strike < 0 ) throw new InvalidRangeException( S1 +
				"Strike angle cannot be less than zero (value = "+ strike+")");
		if( strike > 360 ) throw new InvalidRangeException( S1 +
				"Strike angle cannot be greater than 360 (value = "+ strike+")");
			}


	/**
	 * Checks that the dip angle fits within the definition<p>
	 * <code>0 <= dip <= 90</code><p>
	 * @param dip                       Angle to validate
	 * @throws InvalidRangeException    Thrown if not valid angle
	 */
	public static void assertValidDip( double dip)
			throws InvalidRangeException
			{

		if( dip < 0 ) throw new InvalidRangeException( S2 +
				"Dip angle cannot be less than zero; the value is "+dip);
		if( dip > 90 ) throw new InvalidRangeException( S2 +
				"Dip angle cannot be greater than 90; the value is "+dip);
			}


	/**
	 * This makes sure that a depth on the fault is a positive number<p>
	 * @param depth
	 * @throws InvalidRangeException
	 */
	public static void assertValidDepth( double depth)
			throws InvalidRangeException
			{
		if( !(depth >= 0) ) throw new InvalidRangeException( S2 +
				"Depth on fault must be positive" );
			}


	/**
	 * This makes sure that a depth on the fault is a positive number<p>
	 * @param depth
	 * @throws InvalidRangeException
	 */
	public static void assertValidSeisUpperAndLower(double upperSeis, double lowerSeis)
			throws InvalidRangeException {

		assertValidDepth(upperSeis);
		assertValidDepth(lowerSeis);
		if( upperSeis > lowerSeis ) throw new InvalidRangeException( S2 +
				"upperSeisDepth must be < lowerSeisDepth" );
	}
	/**
	 * Checks that the rake angle fits within the definition<p>
	 * <code>-180 <= rake <= 180</code><p>
	 * @param rake                      Angle to validate
	 * @throws InvalidRangeException    Thrown if not valid angle
	 */
	public static void assertValidRake( double rake)
			throws InvalidRangeException
			{

		if( rake < -180 ) throw new InvalidRangeException( S3 +
				"Rake angle cannot be less than -180"
				);
		if( rake > 180 ) throw new InvalidRangeException( S3 +
				"Rake angle cannot be greater than 180"
				);
			}
	
	/**
	 * Returns the given angle in the range <code>-180 <= rake <= 180</code>
	 * 
	 * @param angle
	 */
	public static double getInRakeRange(double angle) {
		while (angle > 180)
			angle -= 360;
		while (angle < -180)
			angle += 180;
		return angle;
	}

	/**
	 * This subdivides the given fault trace into sub-traces that have the length as given (or less).
	 * 
	 * @param faultTrace 
	 * @param maxSubSectionLen Maximum length of each subsection
	 */
	public static List<FaultTrace> getEqualLengthSubsectionTraces(FaultTrace faultTrace, double maxSubSectionLen) {
		return getEqualLengthSubsectionTraces(faultTrace, maxSubSectionLen, 1);
	}

	/**
	 * This subdivides the given fault trace into sub-traces that have the length as given (or less).
	 * 
	 * @param faultTrace 
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @param minSubSections minimum number of sub section traces to generate
	 */
	public static List<FaultTrace> getEqualLengthSubsectionTraces(
			FaultTrace faultTrace, double maxSubSectionLen, int minSubSections) {
		int numSubSections = getNumSubSects(faultTrace.getTraceLength(), maxSubSectionLen, minSubSections);
		
		return getEqualLengthSubsectionTraces(faultTrace, numSubSections);
	}
	
	private static int getNumSubSects(double traceLength, double maxSubSectionLen, int minSubSections) {
		// find the number of sub sections
		double numSubSec= traceLength/maxSubSectionLen;
		int numSubSections;
		if(Math.floor(numSubSec)!=numSubSec)
			numSubSections = (int)Math.floor(numSubSec)+1;
		else
			numSubSections = (int)numSubSec;
		if (numSubSections < minSubSections)
			numSubSections = minSubSections;
		return numSubSections;
	}
	
	/**
	 * This subdivides the given fault trace into the specified number of equal-length sub-traces.
	 * 
	 * @param faultTrace 
	 * @param numSubSections number of sub section traces to generate
	 */
	public static List<FaultTrace> getEqualLengthSubsectionTraces(
			FaultTrace faultTrace, int numSubSections) {
		// find the length of each sub section
		double subSecLength = faultTrace.getTraceLength()/numSubSections;
		double distance = 0, distLocs=0;
		int numLocs = faultTrace.getNumLocations();
		int index=0;
		ArrayList<FaultTrace> subSectionTraceList = new ArrayList<FaultTrace>();
		Location prevLoc = faultTrace.get(index);
		while(index<numLocs && subSectionTraceList.size()<numSubSections) {
			FaultTrace subSectionTrace = new FaultTrace(faultTrace.getName()+" "+(subSectionTraceList.size()+1));
			subSectionTraceList.add(subSectionTrace);
			subSectionTrace.add(prevLoc); // the first location
			++index;
			distance = 0;
			while(true && index<faultTrace.getNumLocations()) {
				Location nextLoc = faultTrace.get(index);
				distLocs = LocationUtils.horzDistance(prevLoc, nextLoc);
				distance+= distLocs;
				if(distance<subSecLength) { // if sub section length is greater than distance, then get next point on trace
					prevLoc = nextLoc;
					subSectionTrace.add(prevLoc);
					++index;
				} else {
					LocationVector direction = LocationUtils.vector(prevLoc, nextLoc);
					double origHorzDist = direction.getHorzDistance();
					double modHorzDist = subSecLength-(distance-distLocs);
					direction.setHorzDistance(modHorzDist);
					if (direction.getVertDistance() != 0d)
						// scale vert distance to account for truncated horizontal distance
						direction.setVertDistance(direction.getVertDistance()*modHorzDist/origHorzDist);
					prevLoc = LocationUtils.location(prevLoc, direction);
					subSectionTrace.add(prevLoc);
					--index;
					break;
				}
			}
		}
		return subSectionTraceList;
	}

	/**
	 * This subdivides the given fault trace into sub-traces that have the length as given (or less).
	 * 
	 * @param upperTrace upper fault trace
	 * @param lowerTrace lower fault trace
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @param minSubSections minimum number of sub section traces to generate
	 * @return list of upper-lower trace pairs for each subsection
	 */
	public static List<FaultTrace[]> getEqualLengthSubsectionTraces(
			FaultTrace upperTrace, FaultTrace lowerTrace, double maxSubSectionLen, int minSubSections) {
		int numSubSections = getNumSubSects(0.5*(upperTrace.getTraceLength() + lowerTrace.getTraceLength()),
				maxSubSectionLen, minSubSections);

		return getEqualLengthSubsectionTraces(upperTrace, lowerTrace, numSubSections);
	}
	
	/**
	 * This subdivides the given fault trace into the specified number of equal-length sub-traces.
	 * 
	 * @param upperTrace upper fault trace
	 * @param lowerTrace lower fault trace
	 * @param numSubSects number of sub section traces to generate
	 * @return list of upper-lower trace pairs for each subsection
	 */
	public static List<FaultTrace[]> getEqualLengthSubsectionTraces(
			FaultTrace upperTrace, FaultTrace lowerTrace, int numSubSects) {
		final boolean D = false;
		// we have a lower trace, which is more complex.
		// we could just split the upper and lower traces into equal length pieces and connect them, but those can
		// be skewed if one trace has more (and uneven) curvature than the other

		// instead, we'll try to build less skewed sections by subsectioning a trace down the middle of the fault
		// and then projecting up/down to the top/bottom

		// build a trace at the middle
		int numResample = Integer.max(100, (int)Math.max(upperTrace.getTraceLength(), lowerTrace.getTraceLength()));
		FaultTrace upperResampled = resampleTrace(upperTrace, numResample);
		FaultTrace lowerResampled = resampleTrace(lowerTrace, numResample);
		Preconditions.checkState(upperResampled.size() == lowerResampled.size());
		// this won't necessarily be evenly spaced, but that's fine (we'll build equal length traces next)
		FaultTrace middleTrace = new FaultTrace(null);
		double maxHorzDist = 0d;
		for (int i=0; i<upperResampled.size(); i++) {
			Location upperLoc = upperResampled.get(i);
			Location lowerLoc = lowerResampled.get(i);
			// vector from upper to lower
			LocationVector vector = LocationUtils.vector(upperLoc, lowerLoc);
			maxHorzDist = Math.max(maxHorzDist, vector.getHorzDistance());
			// scale by 0.5 to get a middle loc
			vector.setHorzDistance(0.5*vector.getHorzDistance());
			vector.setVertDistance(0.5*vector.getVertDistance());
			middleTrace.add(LocationUtils.location(upperLoc, vector));
		}

		// resample the middle trace to get subsections
		List<FaultTrace> equalLengthMiddleTraces = getEqualLengthSubsectionTraces(
				middleTrace, numSubSects);
		// project the middle trace to the upper and lower traces; do that by finding the index on the resampled
		// traces that is closest to a right angle from middle trace strike direction
		int[][] closestUpperIndexes = new int[numSubSects][2];
		int[][] closestLowerIndexes = new int[numSubSects][2];
		for (int i=0; i<numSubSects; i++) {
			FaultTrace middle = equalLengthMiddleTraces.get(i);
			double strike = middle.getAveStrike();
			double leftOfStrikeRad = Math.toRadians(strike-90d);
			double rightOfStrikeRad = Math.toRadians(strike+90d);
			Location[] firstLine = {
					LocationUtils.location(middle.first(), leftOfStrikeRad, maxHorzDist),
					LocationUtils.location(middle.first(), rightOfStrikeRad, maxHorzDist)
			};
			Location[] lastLine = {
					LocationUtils.location(middle.last(), leftOfStrikeRad, maxHorzDist),
					LocationUtils.location(middle.last(), rightOfStrikeRad, maxHorzDist)
			};
			double upperFirstDist = Double.POSITIVE_INFINITY;
			double upperLastDist = Double.POSITIVE_INFINITY;
			double lowerFirstDist = Double.POSITIVE_INFINITY;
			double lowerLastDist = Double.POSITIVE_INFINITY;
			// this could be sped up, we shouldn't need to search the whole trace every time
			for (int j=0; j<upperResampled.size(); j++) {
				double distUpFirst = LocationUtils.distanceToLineSegmentFast(firstLine[0], firstLine[1], upperResampled.get(j));
				if (distUpFirst < upperFirstDist) {
					upperFirstDist = distUpFirst;
					closestUpperIndexes[i][0] = j;
				}
				double distUpLast = LocationUtils.distanceToLineSegmentFast(lastLine[0], lastLine[1], upperResampled.get(j));
				if (distUpLast < upperLastDist) {
					upperLastDist = distUpLast;
					closestUpperIndexes[i][1] = j;
				}
				double distLowFirst = LocationUtils.distanceToLineSegmentFast(firstLine[0], firstLine[1], lowerResampled.get(j));
				if (distLowFirst < lowerFirstDist) {
					lowerFirstDist = distLowFirst;
					closestLowerIndexes[i][0] = j;
				}
				double distLowLast = LocationUtils.distanceToLineSegmentFast(lastLine[0], lastLine[1], lowerResampled.get(j));
				if (distLowLast < lowerLastDist) {
					lowerLastDist = distLowLast;
					closestLowerIndexes[i][1] = j;
				}
			}
			if (D) {
				System.out.println("Raw mappings for subsection "+i);
				System.out.println("\t"+closestUpperIndexes[i][0]+" "+closestUpperIndexes[i][1]);
				System.out.println("\t"+(float)upperFirstDist+" "+(float)upperLastDist);
				System.out.println("\t"+closestLowerIndexes[i][0]+" "+closestLowerIndexes[i][1]);
				System.out.println("\t"+(float)lowerFirstDist+" "+(float)lowerLastDist);
			}
		}
		// now process to fix two cases:
		// * any overlaps with the neighbors
		// * ensure that we include the overall first or last point on the traces
		for (int i=0; i<numSubSects; i++) {
			int[] myUpper = closestUpperIndexes[i];
			int[] myLower = closestLowerIndexes[i];
			if (i == 0) {
				// force it to start at the first point
				myUpper[0] = 0;
				myLower[0] = 0;
			} else {
				// average with the previous
				int[] prevUpper = closestUpperIndexes[i-1];
				if (myUpper[0] != prevUpper[1]) {
					double tieBreaker = myUpper[1]-myUpper[0] > prevUpper[1]-prevUpper[0] ? 0.1 : -0.1;
					int avg = (int)(0.5*(myUpper[0] + prevUpper[1])+tieBreaker);
					if (D) {
						double raw = (myUpper[0] + prevUpper[1])+tieBreaker;
						System.out.println("Averaging start of upper["+i+"]="+myUpper[0]
								+" with end of previous upper["+(i-1)+"]="+prevUpper[1]
										+": ("+myUpper[0]+" + "+prevUpper[1]+")+"+tieBreaker+" = "+raw+" = "+avg);
					}
					myUpper[0] = avg;
					prevUpper[1] = avg;
				}
				int[] prevLower = closestLowerIndexes[i-1];
				if (myLower[0] != prevLower[1]) {
					double tieBreaker = myLower[1]-myLower[0] > prevLower[1]-prevLower[0] ? 0.1 : -0.1;
					int avg = (int)(0.5*(myLower[0] + prevLower[1])+tieBreaker);
					if (D) {
						double raw = (myLower[0] + prevLower[1])+tieBreaker;
						System.out.println("Averaging start of lower["+i+"]="+myLower[0]
								+" with end of previous lower["+(i-1)+"]="+prevLower[1]
										+": ("+myLower[0]+" + "+prevLower[1]+")+"+tieBreaker+" = "+raw+" = "+avg);
					}
					myLower[0] = avg;
					prevLower[1] = avg;
				}
			}

			if (i == numSubSects-1) {
				// force it to end at the last point
				myUpper[1] = upperResampled.size()-1;
				myLower[1] = upperResampled.size()-1;
			}
		}
		// now check to make sure that none are weird (last same as or before first)
		boolean fail = false;
		for (int i=0; i<numSubSects; i++) {
			int[] myUpper = closestUpperIndexes[i];
			int[] myLower = closestLowerIndexes[i];
			if (myUpper[0] >= myUpper[1] || myLower[0] >= myLower[1]) {
				System.out.println("Fail for subsection "+i+"/"+numSubSects+" with middle-trace strike="+(float)equalLengthMiddleTraces.get(i).getAveStrike());
				System.out.println("\tupper: "+myUpper[0]+"->"+myUpper[1]);
				System.out.println("\tlower: "+myLower[0]+"->"+myLower[1]);
				fail = true;
				break;
			}
		}
		
		List<FaultTrace> upperTraces;
		List<FaultTrace> lowerTraces;
		if (fail) {
			// fallback to the possibly skewed subsections just using the resampled upper and lower trace
			System.err.println("WARNING: failed to build unskewed subsections for  "+upperTrace.getName()
					+", reverting to splitting upper and lower trace evenly");
			upperTraces = getEqualLengthSubsectionTraces(upperTrace, numSubSects);
			lowerTraces = getEqualLengthSubsectionTraces(lowerTrace, numSubSects);
			Preconditions.checkState(upperTraces.size() == lowerTraces.size());
		} else {
			// build our nicer subsections
			upperTraces = new ArrayList<>(numSubSects);
			lowerTraces = new ArrayList<>(numSubSects);

			int upperSearchStartIndex = 0;
			int lowerSearchStartIndex = 0;
			for (int i=0; i<numSubSects; i++) {
				FaultTrace upperSubTrace = new FaultTrace(null);
				FaultTrace lowerSubTrace = new FaultTrace(null);
				int[] myUpper = closestUpperIndexes[i];
				int[] myLower = closestLowerIndexes[i];
				Location upperFirst = upperResampled.get(myUpper[0]);
				Location upperLast = upperResampled.get(myUpper[1]);
				Location lowerFirst = lowerResampled.get(myLower[0]);
				Location lowerLast = lowerResampled.get(myLower[1]);
				upperSubTrace.add(upperFirst);
				lowerSubTrace.add(lowerFirst);

				// add any intermediate locations
				upperSearchStartIndex = addIntermediateTracePoints(upperTrace, upperSubTrace, upperFirst, upperLast, upperSearchStartIndex);
				lowerSearchStartIndex = addIntermediateTracePoints(lowerTrace, lowerSubTrace, lowerFirst, lowerLast, lowerSearchStartIndex);

				upperSubTrace.add(upperLast);
				lowerSubTrace.add(lowerLast);
				//							int upperBeforeStartIndex = -1;
				//							int upperAfterEndIndex = -1;
				//							int lowerBeforeStartIndex = -1;
				//							int lowerAfterEndIndex = -1;
				//							for (int j=0; j<2; j++) {
				//								int targetUpperSampledIndex = closestUpperIndexes[i][j];
				//								int targetLowerSampledIndex = closestUpperIndexes[i][j];
				//								
				//								if (i == 0 && j == 0) {
				//									// simple, just start at the beginning
				//									upperSubTrace.add(trace.first());
				//									lowerSubTrace.add(lowerTrace.first());
				//								} else if (i == numSubSects-1 && j == 2) {
				//									// need to search for the index before
				//								}
				//								
				//								
				//							}
				//							
				//							int upperBeforeIndex = -1;
				//							for (int j=upperSearchStartIndex; j<trace.size(); j++)
				//							int lowerBeforeIndex = -1;

				upperTraces.add(upperSubTrace);
				lowerTraces.add(lowerSubTrace);
			}
		}
		
		List<FaultTrace[]> ret = new ArrayList<>(numSubSects);
		for (int i=0; i<numSubSects; i++)
			ret.add(new FaultTrace[] {upperTraces.get(i), lowerTraces.get(i)});
		return ret;
	}
	
	private static int addIntermediateTracePoints(FaultTrace rawTrace, FaultTrace subSectTrace,
			Location subsectionStart, Location subsectionEnd, int searchStartIndex) {
//		System.out.println("Adding intermediate points with start:\t"+subsectionStart);
		// find the segment for the start index
		double minDist = Double.POSITIVE_INFINITY;
		int closestSegToStart = -1;
		for (int i=searchStartIndex; i<rawTrace.size()-1; i++) {
			Location loc1 = rawTrace.get(i);
			Location loc2 = rawTrace.get(i+1);
			double distToSeg = LocationUtils.distanceToLineSegmentFast(loc1, loc2, subsectionStart);
			if (distToSeg < minDist) {
				closestSegToStart = i;
				minDist = distToSeg;
			} else if (minDist < 1d && distToSeg > 10d) {
				// we've already found it and gone past, stop searching
				break;
			}
		}
//		System.out.println("\tClosest segment to start: "+closestSegToStart+" (minDist="+(float)minDist+")");
		
		// find the segment for the start index
		minDist = Double.POSITIVE_INFINITY;
		int closestSegToEnd = -1;
		for (int i=closestSegToStart; i<rawTrace.size()-1; i++) {
			Location loc1 = rawTrace.get(i);
			Location loc2 = rawTrace.get(i+1);
			double distToSeg = LocationUtils.distanceToLineSegmentFast(loc1, loc2, subsectionEnd);
			if (distToSeg < minDist) {
				closestSegToEnd = i;
				minDist = distToSeg;
			} else if (minDist < 1d && distToSeg > 10d) {
				// we've already found it and gone past, stop searching
				break;
			}
		}
//		System.out.println("\tClosest segment to end: "+closestSegToEnd+" (minDist="+(float)minDist+")");
		
		// we've now identified the segments on which the start and end section lie
		if (closestSegToStart < closestSegToEnd) {
			// there's at least one point between the two
			for (int i=closestSegToStart+1; i<=closestSegToEnd; i++) {
//				System.out.println("\tAdding intermediate: "+i+". "+rawTrace.get(i));
				subSectTrace.add(rawTrace.get(i));
			}
		}
//		System.out.println("Done adding intermediate points with end:\t"+subsectionEnd);
		
		return closestSegToEnd;
	}


	/**
	 * This resamples the trace into num subsections of equal length 
	 * (final number of points in trace is num+1).  However, note that
	 * these subsections of are equal length on the original trace, and
	 * that the final subsections will be less than that if there is curvature
	 * in the original between the points (e.g., corners getting cut).
	 * @param trace
	 * @param num - number of subsections
	 * @return
	 */
	public static FaultTrace resampleTrace(FaultTrace trace, int num) {
		double resampInt = trace.getTraceLength()/num;
		FaultTrace resampTrace = new FaultTrace("resampled "+trace.getName());
		resampTrace.add(trace.get(0));  // add the first location
		double remainingLength = resampInt;
		Location lastLoc = trace.get(0);
		int NextLocIndex = 1;
		while (NextLocIndex < trace.size()) {
			Location nextLoc = trace.get(NextLocIndex);
			double length = LocationUtils.horzDistance(lastLoc, nextLoc);
			if (length > remainingLength) {
				// set the point
				LocationVector dir = LocationUtils.vector(lastLoc, nextLoc);
				dir.setHorzDistance(dir.getHorzDistance()*remainingLength/length);
				dir.setVertDistance(dir.getVertDistance()*remainingLength/length);
				Location loc = LocationUtils.location(lastLoc, dir);
				resampTrace.add(loc);
				lastLoc = loc;
				remainingLength = resampInt;
				// Next location stays the same
			} else {
				lastLoc = nextLoc;
				NextLocIndex += 1;
				remainingLength -= length;
			}
		}

		// the last one usually (always?) gets missed in the above, add it if needed
		double dist = LocationUtils.linearDistanceFast(trace.get(trace.size()-1), resampTrace.get(resampTrace.size()-1));
		if (dist> resampInt/2)
			resampTrace.add(trace.get(trace.size()-1));
		
		Preconditions.checkState(resampTrace.size() == num+1,
				"Resampled trace should have %s locations, but has %s", num+1, resampTrace.size());

		/* Debugging Stuff *****************/
		/*
		  // write out each to check
		  System.out.println("RESAMPLED");
		  for(int i=0; i<resampTrace.size(); i++) {
			  Location l = resampTrace.getLocationAt(i);
			  System.out.println(l.getLatitude()+"\t"+l.getLongitude()+"\t"+l.getDepth());
		  }

		  System.out.println("ORIGINAL");
		  for(int i=0; i<trace.size(); i++) {
			  Location l = trace.getLocationAt(i);
			  System.out.println(l.getLatitude()+"\t"+l.getLongitude()+"\t"+l.getDepth());
		  }

		  // write out each to check
		  System.out.println("target resampInt="+resampInt+"\tnum sect="+num);
		  System.out.println("RESAMPLED");
		  double ave=0, min=Double.MAX_VALUE, max=Double.MIN_VALUE;
		  for(int i=1; i<resampTrace.size(); i++) {
			  double d = LocationUtils.getTotalDistance(resampTrace.getLocationAt(i-1), resampTrace.getLocationAt(i));
			  ave +=d;
			  if(d<min) min=d;
			  if(d>max) max=d;
		  }
		  ave /= resampTrace.size()-1;
		  System.out.println("ave="+ave+"\tmin="+min+"\tmax="+max+"\tnum pts="+resampTrace.size());


		  System.out.println("ORIGINAL");
		  ave=0; min=Double.MAX_VALUE; max=Double.MIN_VALUE;
		  for(int i=1; i<trace.size(); i++) {
			  double d = LocationUtils.getTotalDistance(trace.getLocationAt(i-1), trace.getLocationAt(i));
			  ave +=d;
			  if(d<min) min=d;
			  if(d>max) max=d;
		  }
		  ave /= trace.size()-1;
		  System.out.println("ave="+ave+"\tmin="+min+"\tmax="+max+"\tnum pts="+trace.size());

		  /* End of debugging stuff ********************/

		return resampTrace;
	}


	/**
	 * This is a quick plot of the traces
	 * @param traces
	 */
	public static void plotTraces(ArrayList<FaultTrace> traces) {
		throw new RuntimeException("This doesn't work because our functions will reorder x-axis values" +
				"to monotonically increase (and remove duplicates - someone should fix this)");
		/*ArrayList funcs = new ArrayList();
				for(int t=0; t<traces.size();t++) {
					FaultTrace trace = traces.get(t);
					ArbitrarilyDiscretizedFunc traceFunc = new ArbitrarilyDiscretizedFunc();
					for(int i=0;i<trace.size();i++) {
						Location loc= trace.getLocationAt(i);
						traceFunc.set(loc.getLongitude(), loc.getLatitude());
					}
					traceFunc.setName(trace.getName());
					funcs.add(traceFunc);
				}
				GraphWindow graph = new GraphWindow(funcs, "");  
				ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
	/*			plotChars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.FILLED_CIRCLES, Color.BLACK, 4));
				plotChars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLUE, 2));
				plotChars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLUE, 1));
				plotChars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLUE, 1));
				graph.setPlottingFeatures(plotChars);
				graph.setX_AxisLabel("Longitude");
				graph.setY_AxisLabel("Latitude");
				graph.setTickLabelFontSize(12);
				graph.setAxisAndTickLabelFontSize(14);
				/*
				// to save files
				if(dirName != null) {
					String filename = ROOT_PATH+dirName+"/slipRates";
					try {
						graph.saveAsPDF(filename+".pdf");
						graph.saveAsPNG(filename+".png");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
		 */

	}


	/**
	 * Returns an average of the given angles scaled by the distances between the corresponding
	 * locations. Note that this expects angles in degrees, and will return angles from 0 to 360 degrees.
	 * 
	 * @param locs locations for distance scaling
	 * @param angles angles in degrees corresponding to each pair of locations
	 * @return
	 */
	public static double getLengthBasedAngleAverage(List<Location> locs, List<Double> angles) {
		Preconditions.checkArgument(locs.size() >= 2, "must have at least 2 locations!");
		Preconditions.checkArgument(angles.size() == locs.size()-1, "must have exactly one fewer angles than location");

		ArrayList<Double> lengths = new ArrayList<Double>();

		for (int i=1; i<locs.size(); i++)
			lengths.add(LocationUtils.linearDistanceFast(locs.get(i), locs.get(i-1)));

		return getScaledAngleAverage(lengths, angles);
	}
	
	/**
	 * Class for calculating the average of angles weighted by scalar values without storing them all in memory at once.
	 * Note that this expects angles in degrees, and will return angles from 0 to 360 degrees.
	 * 
	 * @author kevin
	 *
	 */
	public static class AngleAverager {
		double xdir=0; double ydir=0;
		
		/**
		 * 
		 * @param angle in degrees
		 * @param weight
		 */
		public synchronized void add(double angle, double weight) {
			xdir+=weight*Math.cos(Math.toRadians(angle));
			ydir+=weight*Math.sin(Math.toRadians(angle));
		}
		
		public double getAverage() {
			double avg;

			if (xdir>0 & ydir>=0)
				avg = Math.toDegrees(Math.atan(ydir/xdir));
			else if (xdir>0 & ydir<0)
				avg =  Math.toDegrees(Math.atan(ydir/xdir))+360;
			else if (xdir<0)
				avg =  Math.toDegrees(Math.atan(ydir/xdir))+180;
			else if (xdir==0 & ydir>0)
				avg = 90;
			else if (xdir==0 & ydir<0)
				avg = 270;
			else
				avg = 0; // if both xdir==0 & ydir=0

			while (avg > 360)
				avg -= 360;
			while (avg < 0)
				avg += 360;

			return avg;
		}
	}

	/**
	 * Returns an average of the given angles scaled by the given scalars. Note that this
	 * expects angles in degrees, and will return angles from 0 to 360 degrees.
	 * 
	 * @param scalars scalar weights for each angle (does not need to be normalized)
	 * @param angles angles in degrees corresponding to each pair of locations
	 * @return
	 */
	public static double getScaledAngleAverage(List<Double> scalars, List<Double> angles) {
		Preconditions.checkArgument(scalars.size() >= 1, "must have at least 1 lengths!");
		Preconditions.checkArgument(angles.size() == scalars.size(), "must have exactly the same amount of lengths as angles");

		// see if we have an easy case, or a NaN
		if (angles.size() == 1)
			return angles.get(0);
		if (Double.isNaN(angles.get(0)))
			return Double.NaN;
		boolean equal = true;
		for (int i=1; i<angles.size(); i++) {
			if (Double.isNaN(angles.get(i)))
				return Double.NaN;
			if (angles.get(i) != angles.get(0)) {
				equal = false;
			}
		}
		if (equal)
			return angles.get(0);
		
		AngleAverager avg = new AngleAverager();
		
		for (int i=0; i<scalars.size(); i++) {
			double scalar = scalars.get(i);
			double angle = angles.get(i);
			avg.add(angle, scalar);
		}

		return avg.getAverage();
	}
	
	/**
	 * Averages angles dealing with any -180/180 or 0/360 cut issues. Note that this
	 * expects angles in degrees, and will return angles from 0 to 360 degrees.
	 * 
	 * @param angles
	 * @return
	 */
	public static double getAngleAverage(List<Double> angles) {
		ArrayList<Double> scalars = new ArrayList<Double>();
		for (int i=0; i<angles.size(); i++)
			scalars.add(1d);
		return getScaledAngleAverage(scalars, angles);
	}
	
	/**
	 * Returns the smallest absolute difference between two angles in degrees.
	 * Angles may be any real values (not restricted to 0-360), wraparound is handled internally.
	 * 
	 * @return absolute difference between the 2 given angles in decimal degrees in the range [0, 180]
	 */
	public static double getAbsAngleDiff(double angle1, double angle2) {
		double diff = Math.abs(angle1 - angle2) % 360.0;
		if (diff > 180.0)
			diff = 360.0 - diff;
		return diff;
	}

	/* <b>x</b>-axis unit normal vector [1,0,0]*/ 
	private static final double[] VX_UNIT_NORMAL = { 1.0, 0.0, 0.0 };
	/* <b>y</b>-axis unit normal vector [0,1,0]*/ 
	private static final double[] VY_UNIT_NORMAL = { 0.0, 1.0, 0.0 };
	/* <b>z</b>-axis unit normal vector [0,0,1]*/ 
	private static final double[] VZ_UNIT_NORMAL = { 0.0, 0.0, 1.0 };


	/**
	 * Calculates a slip vector from strike, dip, and rake information provided.
	 * @param strike
	 * @param dip
	 * @param rake
	 * @return double[x,y,z] array for slip vector.
	 */
	public static double[] getSlipVector(double[] strikeDipRake) {
		// start with y-axis unit normal on a horizontal plane
		double[] startVector = VY_UNIT_NORMAL;
		// rotate rake amount about z-axis (negative axial rotation)
		double[] rakeRotVector = vectorMatrixMultiply(zAxisRotMatrix(-strikeDipRake[2]),startVector);
		// rotate dip amount about y-axis (negative axial rotation)
		double[] dipRotVector = vectorMatrixMultiply(yAxisRotMatrix(-strikeDipRake[1]),rakeRotVector);
		// rotate strike amount about z-axis (positive axial rotation)
		double[] strikeRotVector = vectorMatrixMultiply(zAxisRotMatrix(strikeDipRake[0]),dipRotVector);
		return strikeRotVector;
	}

	/*
	 * Multiplies the vector provided with a matrix. Useful for rotations.
	 * @param matrix double[][] matrix (likely one of the rotation matrices from
	 * this class).
	 * @param vector double[x,y,z] to be modified.
	 */
	private static double[] vectorMatrixMultiply(double[][] matrix, double[] vector) {
		double[] rotatedVector = new double[3];
		for (int i = 0; i < 3; i++) {
			rotatedVector[i] = vector[0] * matrix[i][0] + vector[1] *
					matrix[i][1] + vector[2] * matrix[i][2];
		}
		return rotatedVector;
	}


	/*
	 * Returns a rotation matrix about the x axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * @param theta axial rotation in degrees.
	 * @return double[][] rotation matrix.
	 */
	private static double[][] xAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{ 1.0 ,                 0.0 ,                0.0 },
				{ 0.0 ,  Math.cos(thetaRad) , Math.sin(thetaRad) },
				{ 0.0 , -Math.sin(thetaRad) , Math.cos(thetaRad) }};
		return rotMatrix;
		// @formatter:on
	}

	/*
	 * Returns a rotation matrix about the y axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * @param theta axial rotation in degrees.
	 * @return double[][] rotation matrix.
	 */
	private static double[][] yAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{ Math.cos(thetaRad) , 0.0 , -Math.sin(thetaRad) },
				{                0.0 , 1.0 ,                 0.0 },
				{ Math.sin(thetaRad) , 0.0 ,  Math.cos(thetaRad) }};
		return rotMatrix;
		// @formatter:on
	}

	/*
	 * Returns a rotation matrix about the z axis in a right-handed coordinate
	 * system for a given theta. Note that these are coordinate transformations
	 * and that a positive (anticlockwise) rotation of a vector is the same as a
	 * negative rotation of the reference frame.
	 * @param theta axial rotation in degrees.
	 * @return double[][] rotation matrix.
	 */
	private static double[][] zAxisRotMatrix(double theta) {
		// @formatter:off
		double thetaRad = Math.toRadians(theta);
		double[][] rotMatrix= {{  Math.cos(thetaRad) , Math.sin(thetaRad) , 0.0 },
				{ -Math.sin(thetaRad) , Math.cos(thetaRad) , 0.0 },
				{                 0.0 ,                0.0 , 1.0 }};
		return rotMatrix;
		// @formatter:on
	}


}
