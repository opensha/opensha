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
	public static ArrayList<FaultTrace> getEqualLengthSubsectionTraces(FaultTrace faultTrace, double maxSubSectionLen) {
		return getEqualLengthSubsectionTraces(faultTrace, maxSubSectionLen, 1);
	}

	/**
	 * This subdivides the given fault trace into sub-traces that have the length as given (or less).
	 * 
	 * @param faultTrace 
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @param minSubSections minimum number of sub sections to generate
	 */
	public static ArrayList<FaultTrace> getEqualLengthSubsectionTraces(
			FaultTrace faultTrace, double maxSubSectionLen, int minSubSections) {
		int numSubSections;

		// find the number of sub sections
		double numSubSec= faultTrace.getTraceLength()/maxSubSectionLen;
		if(Math.floor(numSubSec)!=numSubSec) numSubSections=(int)Math.floor(numSubSec)+1;
		else numSubSections=(int)numSubSec;
		if (numSubSections < minSubSections)
			numSubSections = minSubSections;
		return getEqualLengthSubsectionTraces(faultTrace, numSubSections);
	}
	
	/**
	 * This subdivides the given fault trace into the specified number of equal-length sub-traces.
	 * 
	 * @param faultTrace 
	 * @param maxSubSectionLen Maximum length of each subsection
	 * @param minSubSections minimum number of sub sections to generate
	 */
	public static ArrayList<FaultTrace> getEqualLengthSubsectionTraces(
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
	 * Absolute difference between two angles dealing with any -180/180 or 0/360 cut issues. Note that this
	 * expects angles in degrees, and will return angles from 0 to 360 degrees.
	 * @param angle1
	 * @param angle2
	 * @return
	 */
	public static double getAbsAngleDiff(double angle1, double angle2) {
		double angleDiff = Math.abs(angle1 - angle2);
		while (angleDiff > 270)
			angleDiff -= 360;
		return Math.abs(angleDiff);
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
