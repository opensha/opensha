/**
 * 
 */
package org.opensha.sha.faultSurface;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.util.FaultUtils;

import com.google.common.base.Preconditions;

/**
 * An approximately evenly gridded surface, where gridSpacing represents an average value.
 * Column order follows the supplied upper trace, including locally left-dipping
 * subsections. Physical strike/dip are calculated independently from cell geometry.
 * Rx follows the stored upper-trace direction, preserving the parent's sign
 * convention even where a subsection's physical dip direction changes sides.
 * 
 * We could add methods here like: getMinGridSpacing(), getMaxGridSpacing(), etc.
 * @author field
 */
public class ApproxEvenlyGriddedSurface extends AbstractEvenlyGriddedSurfaceWithSubsets {

	// for debugging
	private final static boolean D = false;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private FaultTrace upperFaultTrace = null;
	private FaultTrace lowerFaultTrace = null;
	
	// lazily initialized on the first orientation request
	private double avgDip = Double.NaN;
	// lazily initialized on the first orientation request 
	private double avgStrike = Double.NaN;
	// lazily initialized on first call to getAvgRupTopDepth()
	private double aveUpperDepth = Double.NaN;
	// lazily initialized on first call to getAvgRupBottomDepth()
	private double aveLowerDepth = Double.NaN;
	// Ten micrometers in km: tolerate interpolation roundoff, not inverted boundaries.
	static final double TRACE_DEPTH_TOLERANCE = 1e-8;


	/**
	 * This constructor creates a blank surface enabling the user to populate the surface any way they want
	 * @param numRows
	 * @param numCols
	 * @param aveGridSpacing
	 */
	public ApproxEvenlyGriddedSurface(int numRows,int numCols, double aveGridSpacing) {
		Preconditions.checkArgument(Double.isFinite(aveGridSpacing) && aveGridSpacing > 0d,
				"Grid spacing must be positive and finite");
		setNumRowsAndNumCols(numRows, numCols);
		gridSpacingAlong = aveGridSpacing;
		gridSpacingDown = aveGridSpacing;
		sameGridSpacing = true;
	}


	/**
	 * This constructor takes an upper and lower fault trace, re-samples these according the the given aveGridSpacing
	 * to represent the first and last rows of the surface, and then fills in the intermediate rows by evenly sampling
	 * a straight line between the top and bottom point of each column.  The number of columns is the average length
	 * the top and bottom trace divided by the aveGridSpacing (plus 1), and the number of rows is the average distance
	 * between the top and bottom points in each column divided by the aveGridSpacing (plus 1).
	 * @param upperFaultTrace authoritative along-strike ordering
	 * @param lowerFaultTrace lower boundary; aligned to the upper trace if needed
	 * @param aveGridSpacing
	 */
	public ApproxEvenlyGriddedSurface(FaultTrace upperFaultTrace,FaultTrace lowerFaultTrace, double aveGridSpacing) {
		this(upperFaultTrace, lowerFaultTrace, aveGridSpacing, false, 0d);
	}


	/**
	 * This constructor takes an upper and lower fault trace, re-samples these according the the given aveGridSpacing
	 * to represent the first and last rows of the surface, and then fills in the intermediate rows by evenly sampling
	 * a straight line between the top and bottom point of each column.  The number of columns is the average length
	 * the top and bottom trace divided by the aveGridSpacing (plus 1), and the number of rows is the average distance
	 * between the top and bottom points in each column divided by the aveGridSpacing (plus 1).
	 * <p>
	 * If aseisReducesArea is true and aseismicSlipFactor>0, the upper depth will be reduced so that the final area
	 * is approximately aseismicSlipFactor fraction of the original area.
	 * @param upperFaultTrace authoritative along-strike ordering
	 * @param lowerFaultTrace lower boundary; aligned to the upper trace if needed
	 * @param aveGridSpacing
	 * @param aseisReducesArea if true and aseismicSlipFactor>0, reduce the upper depth by aseismicSlipFactor
	 * @param aseismicSlipFactor if >0 and aseisReducesArea is true, reduce the upper depth by this factor
	 */
	public ApproxEvenlyGriddedSurface(FaultTrace upperFaultTrace,FaultTrace lowerFaultTrace, double aveGridSpacing,
			boolean aseisReducesArea, double aseismicSlipFactor) {
		Preconditions.checkArgument(Double.isFinite(aveGridSpacing) && aveGridSpacing > 0d,
				"Grid spacing must be positive and finite");
		gridSpacingAlong = aveGridSpacing;
		gridSpacingDown = aveGridSpacing;
		sameGridSpacing = true;
		validateTrace(upperFaultTrace, "Upper");
		validateTrace(lowerFaultTrace, "Lower");
		// Own the traces; only the lower trace may be aligned to the upper.
		FaultTrace tmpUpperFaultTrace = new FaultTrace(upperFaultTrace.getName());
		tmpUpperFaultTrace.addAll(upperFaultTrace);
		upperFaultTrace = tmpUpperFaultTrace;
		FaultTrace tmpLowerFaultTrace = new FaultTrace(lowerFaultTrace.getName());
		tmpLowerFaultTrace.addAll(alignLowerTrace(upperFaultTrace, lowerFaultTrace));
		lowerFaultTrace = tmpLowerFaultTrace;
		this.upperFaultTrace = upperFaultTrace;
		this.lowerFaultTrace = lowerFaultTrace;
		resampleAndValidateTraces(upperFaultTrace, lowerFaultTrace);
		
		double traceDistFactor;
		if (aseisReducesArea) {
			Preconditions.checkState(aseismicSlipFactor >= 0d && aseismicSlipFactor < 1,
					"Bad aseismicSlipFactor=%s", aseismicSlipFactor);
			if (aseismicSlipFactor == 0d) {
				traceDistFactor = 1d;
				aseisReducesArea = false;
			} else {
				traceDistFactor = 1d - aseismicSlipFactor;
			}
		} else {
			aseismicSlipFactor = 0d;
			traceDistFactor = 1d;
		}

		// Column order follows the supplied upper trace, including locally left-dipping
		// children. Independently reversing a child would break its parent's indexing.

		// Retain both boundaries even if either dimension is smaller than the
		// requested spacing; orientation requires at least one actual grid cell.
		// now compute num subsection of trace
		double aveTraceLength = (upperFaultTrace.getTraceLength()+lowerFaultTrace.getTraceLength())/2;
		int num = Math.max(1, (int) Math.round(aveTraceLength/aveGridSpacing));
		
		if(D) System.out.println("gridSpacing="+gridSpacingAlong+", aveTraceLength="+aveTraceLength+", numCol="+num);

		// get resampled traces (note that number of locs in trace will be num+1)
		FaultTrace resampUpperTrace = FaultUtils.resampleTrace(upperFaultTrace, num);
		FaultTrace resampLowerTrace = FaultUtils.resampleTrace(lowerFaultTrace, num);
		Preconditions.checkState(resampUpperTrace.size() == num+1, "Resampled upper has %s but expected %s", resampUpperTrace.size(), num+1);
		Preconditions.checkState(resampLowerTrace.size() == num+1, "Resampled lower has %s but expected %s", resampLowerTrace.size(), num+1);
		validateTraceDepths(resampUpperTrace, resampLowerTrace);

		if(D) System.out.println("resample trace lengths: "+resampUpperTrace.size()+" & "+resampLowerTrace.size());
		// compute ave num columns
		double aveDist=0;
		for(int i=0; i<resampUpperTrace.size(); i++) {
			Location topLoc = resampUpperTrace.get(i);
			Location botLoc = resampLowerTrace.get(i);
			aveDist += LocationUtils.linearDistanceFast(topLoc, botLoc);
		}
		aveDist /= resampUpperTrace.size();
		// reduce for aseismicity if applicable
		aveDist *= traceDistFactor;
		int nRows = Math.max(2, (int) Math.round(aveDist/aveGridSpacing)+1);
		
		if(D) System.out.println("aveDist="+aveDist+", nRows="+nRows);

		this.setNumRowsAndNumCols(nRows, num+1);		// note the plus 1!

		// now set the surface locations
		for(int c=0; c<resampUpperTrace.size(); c++) {
			Location topLoc = resampUpperTrace.get(c);
			Location botLoc = resampLowerTrace.get(c);
			if (botLoc.getDepth() < topLoc.getDepth())
				botLoc = new Location(botLoc.getLatitude(), botLoc.getLongitude(), topLoc.getDepth());
			LocationVector dir = LocationUtils.vector(topLoc, botLoc);
			if (aseisReducesArea) {
				// move the top location toward the bottom loc aseismicSlipFactor fraction
				double horzDist = dir.getHorzDistance();
				Preconditions.checkState(horzDist >= 0d);
				double vertDist = dir.getVertDistance();
				Preconditions.checkState(vertDist >= 0d);
				topLoc = LocationUtils.location(topLoc, dir.getAzimuthRad(), horzDist*aseismicSlipFactor);
				topLoc = new Location(topLoc.getLatitude(), topLoc.getLongitude(), topLoc.getDepth()+vertDist*aseismicSlipFactor);
				dir = LocationUtils.vector(topLoc, botLoc);
			}
			double horzIncr = dir.getHorzDistance()/(nRows-1);
			double vertIncr = dir.getVertDistance()/(nRows-1);  // minus sign because vertDist is pos up and depth is pos down
			dir.setHorzDistance(horzIncr);
			dir.setVertDistance(vertIncr);
			this.setLocation(0, c, topLoc);
			Location prevLoc = topLoc;
			for(int r=1;r<nRows;r++) {
				Location nextLoc = LocationUtils.location(prevLoc, dir);
				this.setLocation(r,c, nextLoc);
				prevLoc = nextLoc;
			}
//			double accuracyCheck = LocationUtils.linearDistanceFast(botLoc, this.getLocation(nRows-1,c));
//			System.out.println("Distance between actual and computed bottom point = "+(float)accuracyCheck);
			
			// Shared subsection boundaries should retain the supplied locations exactly.
			this.setLocation(nRows-1, c, botLoc);
		}
		
		Location loc1 = resampLowerTrace.get(resampLowerTrace.size()-1);
		Location loc2 = this.getLocation(numRows-1, numCols-1);
		if(D) System.out.println("DeltaLat = "+ (float)((loc1.getLatitude()-loc2.getLatitude())*111));
		if(D) System.out.println("DeltaLon = "+ (float)((loc1.getLongitude()-loc2.getLongitude())*111));
		if(D) System.out.println("DeltaDepth = "+ (float)(loc1.getDepth()-loc2.getDepth()));

//		if(D) System.out.println(resampLowerTrace.get(resampLowerTrace.size()-1).toString());
//		if(D) System.out.println(resampUpperTrace.get(resampLowerTrace.size()-1).toString());

	}
	
	/**
	 * This explores an accuracy issue (final depth is right be lats and lons are more off)
	 */
	public static void test1(Location l1, Location l2) {
		System.out.println("TEST-1");
//		Location topLoc = new Location(-34.36338,-71.32979,50.0);
//		Location botLoc = new Location(-33.94507,-72.7105,10.0);
		double numSubdivisions=100;
		LocationVector dir = LocationUtils.vector(l1, l2);
		System.out.println("Azimuth p1 to p2: " + dir.getAzimuth());
		double horzIncr = dir.getHorzDistance()/numSubdivisions;
		double vertIncr = dir.getVertDistance()/numSubdivisions;  // minus sign because vertDist is pos up and depth is pos down
		dir.setHorzDistance(horzIncr);
		dir.setVertDistance(vertIncr);
		Location prevLoc = l1;
		for(int r=0;r<numSubdivisions;r++) {
			Location nextLoc = LocationUtils.location(prevLoc, dir);
			prevLoc = nextLoc;
		}
		double accuracyCheck = LocationUtils.linearDistanceFast(l2, prevLoc);
		System.out.println("Distance between actual and computed bottom point = "+(float)accuracyCheck);
		System.out.println("DeltaLat = "+ (float)((l2.getLatitude()-prevLoc.getLatitude())*111));
		System.out.println("DeltaLon = "+ (float)((l2.getLongitude()-prevLoc.getLongitude())*111));
		System.out.println("DeltaDepth = "+ (float)(l2.getDepth()-prevLoc.getDepth()));
		System.out.println("");
	}

	public static void test2(Location l1, Location l2) {
		System.out.println("TEST-2");
		double numSubdivisions=100;
		LocationVector dir = LocationUtils.vector(l1, l2);
		double horzIncr = dir.getHorzDistance()/numSubdivisions;
		double vertIncr = dir.getVertDistance()/numSubdivisions;  // minus sign because vertDist is pos up and depth is pos down
		dir.setHorzDistance(horzIncr);
		dir.setVertDistance(vertIncr);
		Location prevLoc = l1;
		for(int r=0;r<numSubdivisions;r++) {
			Location nextLoc = LocationUtils.location(prevLoc, dir);
			prevLoc = nextLoc;
			dir.setAzimuth(LocationUtils.azimuth(prevLoc, l2));
		}
		double accuracyCheck = LocationUtils.linearDistanceFast(l2, prevLoc);
		System.out.println("Distance between actual and computed bottom point = "+(float)accuracyCheck);
		System.out.println("DeltaLat = "+ (float)((l2.getLatitude()-prevLoc.getLatitude())*111));
		System.out.println("DeltaLon = "+ (float)((l2.getLongitude()-prevLoc.getLongitude())*111));
		System.out.println("DeltaDepth = "+ (float)(l2.getDepth()-prevLoc.getDepth()));
		System.out.println("");
	}


	/**
	 *  Add a Location to the grid - does the same thing as set except that it
	 *  ensures the object is a Location object.
	 *
	 * @param  row                                 The row to set this Location at.
	 * @param  column                              The column to set this Location at.
	 * @param  location                            The new location value.
	 * @exception  ArrayIndexOutOfBoundsException  Thrown if the row or column lies beyond the grid space indexes.
	 */
	public void setLocation( int row, int column, Location location ) {
		set(row, column, location);
	}


	@Override
	public void set(int row, int column, Location location) {
		super.set(row, column, location);
		avgDip = Double.NaN;
		avgStrike = Double.NaN;
		aveUpperDepth = Double.NaN;
		aveLowerDepth = Double.NaN;
		clearCache();
	}

	/** Shared by explicit-trace construction and GeoJSON completion. */
	static void validateTrace(FaultTrace trace, String label) {
		Preconditions.checkState(trace != null && trace.size() >= 2, "%s trace needs at least two locations", label);
		for (Location loc : trace)
			Preconditions.checkState(loc != null && Double.isFinite(loc.getLatitude())
					&& Double.isFinite(loc.getLongitude()) && Double.isFinite(loc.getDepth()) && loc.getDepth() >= 0d,
					"%s trace has an invalid location: %s", label, loc);
	}

	/** Match lower ends to upper ends without changing the authoritative upper direction. */
	static FaultTrace alignLowerTrace(FaultTrace upper, FaultTrace lower) {
		if (FaultUtils.getAbsAngleDiff(upper.getAveStrike(), lower.getAveStrike()) <= 90d
				|| upper.getTraceLength() == 0d || lower.getTraceLength() == 0d)
			return lower;
		FaultTrace aligned = new FaultTrace(lower.getName());
		aligned.addAll(lower);
		aligned.reverse();
		return aligned;
	}

	/** Use a spacing-independent check so loading and dynamically built sections agree. */
	static FaultTrace[] resampleAndValidateTraces(FaultTrace upper, FaultTrace lower) {
		validateTrace(upper, "Upper");
		validateTrace(lower, "Lower");
		int num = Math.max(Math.max(10, Math.max(upper.size(), lower.size())-1),
				(int)Math.ceil(Math.max(upper.getTraceLength(), lower.getTraceLength())));
		FaultTrace top = FaultUtils.resampleTrace(upper, num);
		FaultTrace bottom = FaultUtils.resampleTrace(lower, num);
		validateTraceDepths(top, bottom);
		return new FaultTrace[] {top, bottom};
	}

	private static void validateTraceDepths(FaultTrace upper, FaultTrace lower) {
		for (int i=0; i<upper.size(); i++)
			Preconditions.checkState(lower.get(i).getDepth()+TRACE_DEPTH_TOLERANCE >= upper.get(i).getDepth(),
					"Lower trace is above upper trace at station %s: %s < %s km", i,
					lower.get(i).getDepth(), upper.get(i).getDepth());
	}

	public void writeXYZ_toFile(String fileName) {
		try{
			FileWriter fw = new FileWriter(fileName);
			fw.write("lat\tlon\tdepth\n");
			Iterator<Location> it = this.getLocationsIterator();
			while(it.hasNext()) {
				Location loc = (Location) it.next();
				fw.write(loc.getLatitude()+"\t"+loc.getLongitude()+"\t"+loc.getDepth()+"\n");
			}	
			fw.close();
		}catch(Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * This computes the average grid spacing for adjacent locations along strike (averaged over all rows as well)
	 * @return
	 */
	public double computeAveGridSpacingAlongStrike() {
		double aveDist=0;
		int num = 0;
		for(int r=0; r<numRows;r++)
			for(int c=0; c<numCols-1; c++) {
				aveDist += LocationUtils.linearDistanceFast(getLocation(r, c), getLocation(r, c+1));
				num +=1;
			}
		return aveDist/num;
	}
	
	
	/**
	 * This computes the average grid spacing for adjacent locations down dip (averaged over all cols as well)
	 * @return
	 */
	public double computeAveGridSpacingDownDip() {
		double aveDist=0;
		int num = 0;
		for(int c=0; c<numCols; c++)
			for(int r=0; r<numRows-1;r++) {
				aveDist += LocationUtils.linearDistanceFast(getLocation(r, c), getLocation(r+1, c));
				num +=1;
			}
		return aveDist/num;
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
//		//these traces come from the file "sub-ch9-z4.in" from the NSHMP 2008 model (South America subduction zone)
//		FaultTrace topTrace = new FaultTrace(null);
//		
//		topTrace.addLocation(-33.9450824793, -72.710497654, 10.);
//		topTrace.addLocation(-33.7588818362, -72.6388800048, 10.);
//		topTrace.addLocation(-33.5399999996, -72.5818237309, 10.);
//		topTrace.addLocation(-32.9000000001, -72.4949902347, 10.);
//		topTrace.addLocation(-31.9400000003, -72.4908227539, 10.);
//		topTrace.addLocation(-30.2999999998, -72.3911303709, 10.);
//		topTrace.addLocation(-29.9600000004, -72.3579357913, 10.);
//		topTrace.addLocation(-29.2320361332, -72.2279632571, 10.);
//		topTrace.addLocation(-28.0045263675, -71.9045263674, 10.);
//		topTrace.addLocation(-26.8599999996, -71.6779882813, 10.);
//		topTrace.addLocation(-25.9555615232, -71.584437256, 10.);
//		topTrace.addLocation(-25.1200000003, -71.4298706053, 10.);
//		topTrace.addLocation(-24.7841723632, -71.3841735843, 10.);
//		topTrace.addLocation(-23.0199999996, -71.2632910159, 10.);
//		topTrace.addLocation(-22.22, -71.1589331058, 10.);
//		topTrace.addLocation(-21.8942095629, -71.1322380371, 10.);
//
//		FaultTrace botTrace = new FaultTrace(null);
//
//		botTrace.addLocation(-34.3633763178, -71.3297920084, 50.);
//		botTrace.addLocation(-34.02, -71.1744982909, 50.);
//		botTrace.addLocation(-33.7199999999, -71.0928930662, 50.);
//		botTrace.addLocation(-33.4399999999, -71.0548120114, 50.);
//		botTrace.addLocation(-32.6400000002, -70.993572998, 50.);
//		botTrace.addLocation(-32.2813330081, -70.9813330075, 50.);
//		botTrace.addLocation(-31.9799999997, -70.9924999998, 50.);
//		botTrace.addLocation(-31.3460058597, -71.1060070798, 50.);
//		botTrace.addLocation(-31.093046875, -71.1330468748, 50.);
//		botTrace.addLocation(-30.8200000004, -71.1281042477, 50.);
//		botTrace.addLocation(-30.5200000003, -71.0938342283, 50.);
//		botTrace.addLocation(-29.6231591798, -70.9168408208, 50.);
//		botTrace.addLocation(-28.1739575199, -70.5260412594, 50.);
//		botTrace.addLocation(-26.872775879, -70.2672241207, 50.);
//		botTrace.addLocation(-26.2200000001, -70.1064562992, 50.);
//		botTrace.addLocation(-25.9844946293, -70.0644934082, 50.);
//		botTrace.addLocation(-25.7599999999, -70.0563464355, 50.);
//		botTrace.addLocation(-25.1999999999, -70.0945446773, 50.);
//		botTrace.addLocation(-24.9400000001, -70.0942907718, 50.);
//		botTrace.addLocation(-22.5600000003, -69.8766857908, 50.);
//		botTrace.addLocation(-22.0599999998, -69.855518799, 50.);
//		botTrace.addLocation(-21.5010148695, -69.8649200561, 50.);
//		
//		ApproxEvenlyGriddedSurface surf = new ApproxEvenlyGriddedSurface(topTrace,botTrace,5);
////		surf.writeXYZ_toFile("dev/scratch/ned/hereItIs.txt");
//		
//		System.out.println ("AveGridSpacingAlongStrike="+surf.computeAveGridSpacingAlongStrike());
//		System.out.println ("AveGridSpacingDownDip="+surf.computeAveGridSpacingDownDip());
//		
//		surf.test();

		// lat aligned
		Location top1 = new Location(20.0,0.0,10.0);
		Location bot1 = new Location(40.0,0.0,50.0);
		
		// ln aligned
		Location top2 = new Location(40.0,0.0,10.0);
		Location bot2 = new Location(40.0,20.0,50.0);

		test1(top1, bot1);
		test1(top2, bot2);
		
		test2(top1, bot1);
		test2(top2, bot2);
		
	
	}


	/**
	 * Area-weighted mean of local cell-center dips, in degrees. Each cell uses
	 * the tangent at its bilinear center; its Jacobian magnitude is the exact
	 * area for a planar quadrilateral and a midpoint area estimate for a warped
	 * cell. This is a mean local dip, not the dip of a fitted plane.
	 */
	@Override
	public synchronized double getAveDip() {
		if (Double.isNaN(avgDip)) calculateAverageOrientation();
		return avgDip;
	}

	/**
	 * Azimuth of the area-weighted horizontal cell normals (degrees clockwise
	 * from north). Nonvertical normals point upward; grid winding sets the side of a vertical
	 * fault, avoiding sign flips from roundoff in a nearly zero Z component.
	 * Horizontal cells carry no direction weight. If directions cancel, use
	 * upper-edge strike + 90 degrees as a convention, not a measured direction.
	 */
	@Override
	public double getAveDipDirection() {
		return (getAveStrike()+90d)%360d;
	}

	// Relative to summed cell area, so tiny surfaces and nearly horizontal
	// surfaces use the same criterion for an undefined representative direction.
	// Also absorb small differences between the cells' local geographic frames.
	private static final double DIRECTION_RESULTANT_TOLERANCE = 1e-8;
	// Within about 0.0000006 degrees of vertical, retain the grid winding
	// rather than deriving a normal sign from roundoff in its Z component.
	private static final double VERTICAL_NORMAL_TOLERANCE = 1e-8;

	/** Compute both summaries together from actual nodes, including any intermediate rows. */
	private void calculateAverageOrientation() {
		Preconditions.checkState(numRows >= 2 && numCols >= 2,
				"Average orientation requires at least two rows and columns");
		double totalArea = 0d, weightedDip = 0d, east = 0d, north = 0d;
		for (int r=0; r<numRows-1; r++) {
			for (int c=0; c<numCols-1; c++) {
				// a,b,c,d run clockwise around the cell, starting at its upper left.
				// Express all edges in one local east/north/up frame, in km.
				Location a = getLocation(r, c);
				Vector3D b = localVector(a, getLocation(r, c+1));
				Vector3D cornerC = localVector(a, getLocation(r+1, c+1));
				Vector3D d = localVector(a, getLocation(r+1, c));
				Vector3D along = b.add(cornerC.subtract(d)).scalarMultiply(0.5);
				Vector3D down = d.add(cornerC.subtract(b)).scalarMultiply(0.5);
				Vector3D normal = down.crossProduct(along);
				double area = normal.getNorm();
				Preconditions.checkState(Double.isFinite(area), "Nonfinite cell geometry at (%s,%s)", r, c);
				if (area == 0d) continue; // fully collapsed cells contribute no surface
				if (normal.getZ() < -VERTICAL_NORMAL_TOLERANCE*area)
					normal = normal.negate();
				double dip = Math.toDegrees(Math.atan2(Math.hypot(normal.getX(), normal.getY()),
						Math.abs(normal.getZ())));
				totalArea += area;
				weightedDip += area*dip;
				// area times unit normal is just the unnormalized cross product.
				east += normal.getX();
				north += normal.getY();
			}
		}
		Preconditions.checkState(totalArea > 0d, "Cannot average orientation of a zero-area surface");
		double strike;
		if (Math.hypot(east, north) > DIRECTION_RESULTANT_TOLERANCE*totalArea) {
			// Strike is 90 degrees counterclockwise from the horizontal normal.
			strike = Math.toDegrees(Math.atan2(-north, east));
		} else {
			FaultTrace upper = getRowAsTrace(0);
			// A triangular tip can collapse the entire upper edge. In that case
			// use the lower edge for the otherwise undefined direction convention.
			FaultTrace edge = upper.getTraceLength() > 0d ? upper : getRowAsTrace(numRows-1);
			strike = edge.getAveStrike();
			Preconditions.checkState(Double.isFinite(strike), "No edge strike for orientation fallback");
		}
		// The weighted mean is mathematically in [0,90]; summation roundoff
		// can put an exactly vertical surface a few ulps above 90.
		avgDip = Math.min(90d, Math.max(0d, weightedDip/totalArea));
		avgStrike = (strike%360d+360d)%360d;
	}

	/** Local tangent-plane vector, with depth positive down converted to Z positive up. */
	private static Vector3D localVector(Location origin, Location point) {
		Preconditions.checkState(origin != null && point != null, "Surface has missing grid locations");
		LocationVector vector = LocationUtils.vector(origin, point);
		double azimuth = vector.getAzimuthRad();
		return new Vector3D(vector.getHorzDistance()*Math.sin(azimuth),
				vector.getHorzDistance()*Math.cos(azimuth), origin.getDepth()-point.getDepth());
	}

	@Override
	public synchronized double getAveRupTopDepth() {
		if (!Double.isNaN(aveUpperDepth))
			return aveUpperDepth;
		double aveDepth = 0;
		FaultTrace topTrace = getRowAsTrace(0);
		for(Location loc:topTrace)
			aveDepth += loc.getDepth();
		aveUpperDepth = aveDepth/topTrace.size();
		return aveUpperDepth;
	}
	
	public synchronized double getAveRupBottomDepth() {
		if (!Double.isNaN(aveLowerDepth))
			return aveLowerDepth;
		double aveDepth = 0;
		FaultTrace botTrace = getRowAsTrace(numRows-1);
		for(Location loc:botTrace)
			aveDepth += loc.getDepth();
		aveLowerDepth = aveDepth/botTrace.size();
		return aveLowerDepth;
	}


	/**
	 * Area-weighted representative right-hand-rule strike. Dip direction is
	 * derived from this cached value. If the horizontal normals cancel, use
	 * the edge-strike convention documented by {@link #getAveDipDirection()}.
	 */
	@Override
	public synchronized double getAveStrike() {
		if (Double.isNaN(avgStrike)) calculateAverageOrientation();
		return avgStrike;
	}


	@Override
	public FaultTrace getUpperEdge() {
		return upperFaultTrace == null ? getRowAsTrace(0) : upperFaultTrace;
	}


	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		return new ApproxEvenlyGriddedSurface(getNumRows(), getNumCols(), gridSpacingAlong);
	}

/*
 16 Zone 4 top 4
-33.9450824793 -72.710497654 10.
-33.7588818362 -72.6388800048 10.
-33.5399999996 -72.5818237309 10.
-32.9000000001 -72.4949902347 10.
-31.9400000003 -72.4908227539 10.
-30.2999999998 -72.3911303709 10.
-29.9600000004 -72.3579357913 10.
-29.2320361332 -72.2279632571 10.
-28.0045263675 -71.9045263674 10.
-26.8599999996 -71.6779882813 10.
-25.9555615232 -71.584437256 10.
-25.1200000003 -71.4298706053 10.
-24.7841723632 -71.3841735843 10.
-23.0199999996 -71.2632910159 10.
-22.22 -71.1589331058 10.
-21.8942095629 -71.1322380371 10.
22 bottom 4
-34.3633763178 -71.3297920084 50.
-34.02 -71.1744982909 50.
-33.7199999999 -71.0928930662 50.
-33.4399999999 -71.0548120114 50.
-32.6400000002 -70.993572998 50.
-32.2813330081 -70.9813330075 50.
-31.9799999997 -70.9924999998 50.
-31.3460058597 -71.1060070798 50.
-31.093046875 -71.1330468748 50.
-30.8200000004 -71.1281042477 50.
-30.5200000003 -71.0938342283 50.
-29.6231591798 -70.9168408208 50.
-28.1739575199 -70.5260412594 50.
-26.872775879 -70.2672241207 50.
-26.2200000001 -70.1064562992 50.
-25.9844946293 -70.0644934082 50.
-25.7599999999 -70.0563464355 50.
-25.1999999999 -70.0945446773 50.
-24.9400000001 -70.0942907718 50.
-22.5600000003 -69.8766857908 50.
-22.0599999998 -69.855518799 50.
-21.5010148695 -69.8649200561 50.
 
 */

}
