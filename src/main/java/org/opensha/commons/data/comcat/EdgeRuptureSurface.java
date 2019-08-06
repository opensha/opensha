package org.opensha.commons.data.comcat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class EdgeRuptureSurface extends AbstractEvenlyGriddedSurface {
	
	private static final boolean D = false;
	
	private FaultTrace upperTrace;
	private FaultTrace lowerTrace;
	private double maxSpacing;
	
	private FaultTrace discrUpperTrace;
	private FaultTrace discrLowerTrace;
	
	private double aveDip;
	private double aveStrike;
	private double upperDepth;
	private double lowerDepth;

	public EdgeRuptureSurface(FaultTrace upperTrace, LocationList lowerTrace, double maxSpacing) {
		this.maxSpacing = maxSpacing;
		Preconditions.checkState(upperTrace.size() == lowerTrace.size(),
				"Upper and lower traces must be of the same size");
		// both traces should be in the average strke direction
		double traceStrike = upperTrace.getStrikeDirection();
		double lowerStrike = LocationUtils.azimuth(lowerTrace.first(), lowerTrace.last());
		double strikeDiff = angleDiff(traceStrike, lowerStrike);
		if (D) System.out.println("*** Building ShakeMap Finite Surface ***");
		if (D) System.out.println("SM Surface strikes: "+(float)traceStrike+", "+(float)lowerStrike
				+". diff: "+(float)strikeDiff);
		if (strikeDiff > 90) {
			// flip it
			if (D) System.out.println("\tflipping it");
			LocationList newLower = new LocationList();
			newLower.addAll(lowerTrace);
			newLower.reverse();
			lowerTrace = newLower;
		}
//		Preconditions.checkState(strikeDiff <= 90d,
//				"Lower trace is not in strike direction. Upper trace strike is %s, lower is %s. Diff=%s",
//				traceStrike, lowerStrike, strikeDiff);
		this.upperTrace = upperTrace;
		this.lowerTrace = new FaultTrace(null);
		this.lowerTrace.addAll(lowerTrace);
		buildSurface();
		if (D) System.out.println("*** DONE Building ShakeMap Finite Surface ***");
	}
	
	private static double angleDiff(double angle1, double angle2) {
		double angleDiff = Math.abs(angle1 - angle2);
		while (angleDiff > 270)
			angleDiff -= 360;
		return Math.abs(angleDiff);
	}
	
	private void buildSurface() {
		double upperLength = upperTrace.getTraceLength();
		double lowerLength = lowerTrace.getTraceLength();
		double minLen = Math.min(upperLength, lowerLength);
		if (D) System.out.println("SM Surface trace lengths: "+(float)upperLength+", "+(float)lowerLength);
		int cols = Integer.max((int)Math.ceil(minLen/maxSpacing), upperTrace.getNumLocations()*2);
		if (D) System.out.println("\tcols: "+cols);
		discrUpperTrace = FaultUtils.resampleTrace(upperTrace, cols-1);
		discrLowerTrace = FaultUtils.resampleTrace(lowerTrace, cols-1);
		if (D) System.out.println("Upper Trace\n"+discrUpperTrace);
		if (D) System.out.println("Lower Trace\n"+discrLowerTrace);
		Preconditions.checkState(discrUpperTrace.size() == discrLowerTrace.size());
		Preconditions.checkState(discrUpperTrace.size() == cols);
		gridSpacingAlong = Math.min(discrUpperTrace.getTraceLength(), discrLowerTrace.getTraceLength())/(cols-1d);
		double maxUpperDepth = Double.NEGATIVE_INFINITY;
		double minLowerDepth = Double.POSITIVE_INFINITY;
		for (Location loc : upperTrace)
			maxUpperDepth = Math.max(maxUpperDepth, loc.getDepth());
		for (Location loc : lowerTrace)
			minLowerDepth = Math.min(minLowerDepth, loc.getDepth());
		Preconditions.checkState(maxUpperDepth < minLowerDepth,
				"Deepest point on upper trace must be above shallowest point on lower trace: %s >= %s",
				maxUpperDepth, minLowerDepth);
		double minDepthSpan = minLowerDepth - maxUpperDepth;
		if (D) System.out.println("SM Surface min depth range: "+(float)maxUpperDepth+", "+(float)minLowerDepth);
		int rows = Integer.max((int)Math.ceil(minDepthSpan/maxSpacing), 2);
		if (D) System.out.println("\trows: "+rows);
		setNumRowsAndNumCols(rows, cols);
		List<Double> dips = new ArrayList<>();
		List<Double> upperDepths = new ArrayList<>();
		List<Double> lowerDepths = new ArrayList<>();
		gridSpacingDown = Double.POSITIVE_INFINITY;
		for (int col=0; col<cols; col++) {
			Location upperLoc = discrUpperTrace.get(col);
			Location lowerLoc = discrLowerTrace.get(col);
			upperDepths.add(upperLoc.getDepth());
			lowerDepths.add(lowerLoc.getDepth());
			double horzDist = LocationUtils.horzDistanceFast(upperLoc, lowerLoc);
			double vertDist = LocationUtils.vertDistance(upperLoc, lowerLoc);
			double dip;
			if (horzDist > 0)
				dip = Math.toDegrees(Math.atan(horzDist/vertDist));
			else
				dip = 90;
			Preconditions.checkState(dip > 0 && dip <= 90,
					"Bad dip=%s at col=%s with horzDist=%s, vertDist=%s.\n\tUpperLoc: %s\n\tLowerLoc: %s",
					(float)dip, col, (float)horzDist, (float)vertDist, upperLoc, lowerLoc);
			dips.add(dip);
			LocationVector vector = LocationUtils.vector(upperLoc, lowerLoc);
			vector.setHorzDistance(vector.getHorzDistance()/(rows-1d));
			vector.setVertDistance(vector.getVertDistance()/(rows-1d));
			gridSpacingDown = Math.min(gridSpacingDown,
					Math.sqrt(vector.getHorzDistance()*vector.getHorzDistance()
							+vector.getVertDistance()*vector.getVertDistance()));
			set(0, col, upperLoc);
			for (int row=1; row<rows; row++)
				set(row, col, LocationUtils.location(get(row-1, col), vector));
		}
		sameGridSpacing = (float)gridSpacingAlong == (float)gridSpacingDown;
		aveDip = StatUtils.mean(Doubles.toArray(dips));
		aveStrike = discrUpperTrace.getAveStrike();
		upperDepth = StatUtils.mean(Doubles.toArray(upperDepths));
		lowerDepth = StatUtils.mean(Doubles.toArray(lowerDepths));
		if (D) {
			System.out.println("SM Surface computed quantities:");
			System.out.println("\tminSpacingAlong: "+(float)gridSpacingAlong);
			System.out.println("\tminSpacingDown: "+(float)gridSpacingDown);
			System.out.println("\taveDip: "+(float)aveDip);
			System.out.println("\taveStike: "+(float)aveStrike);
			System.out.println("\taveDepths: "+(float)upperDepth+", "+(float)lowerDepth);
		}
	}

	@Override
	public double getAveDip() {
		return aveDip;
	}

	@Override
	public double getAveStrike() {
		return aveStrike;
	}

	@Override
	public double getAveRupTopDepth() {
		return upperDepth;
	}

	@Override
	public double getAveDipDirection() {
		return lowerDepth;
	}

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		return new EdgeRuptureSurface(discrUpperTrace, discrLowerTrace, maxSpacing);
	}
	
	public static EdgeRuptureSurface build(List<Location> locs, double maxGridSpacing) {
		Preconditions.checkState(locs.size() % 2 == 1, "Must have odd number of surface outline locations");
		Preconditions.checkState(LocationUtils.areSimilar(locs.get(0), locs.get(locs.size()-1)),
				"Surface outline must be a closed polygon");
		int numEach = locs.size()/2;
		FaultTrace upper = new FaultTrace(null);
		for (int i=0; i<numEach; i++)
			upper.add(locs.get(i));
		LocationList lower = new LocationList();
		for (int i=numEach; i<numEach*2; i++)
			lower.add(locs.get(i));
		return new EdgeRuptureSurface(upper, lower, maxGridSpacing);
	}

}
