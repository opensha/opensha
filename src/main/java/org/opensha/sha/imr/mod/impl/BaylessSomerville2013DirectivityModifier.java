package org.opensha.sha.imr.mod.impl;

import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

/**
 * Bayless and Somerville 2013 Directivity Model
 * 
 * Implemented as described in http://peer.berkeley.edu/publications/peer_reports/reports_2013/webPEER-2013-09-Spudich.pdf
 * 
 * TODO/Issues:
 * * formal JUnit test cases
 * * deal with multi segment ruptures correctly
 * @author kevin
 *
 */
public class BaylessSomerville2013DirectivityModifier extends
		AbstractAttenRelMod {
	
	private static final boolean D = false;
	
	public static final String NAME = "Bayless & Somerville 2013 Directivity";
	public static final String SHORT_NAME = "BS13_Directivity";
	
	// if true, will always use the southernmost hypocenter when not set. for testing.
	private BooleanParameter forceFakeHypoParam;
	private ParameterList params;
	
	public BaylessSomerville2013DirectivityModifier() {
		forceFakeHypoParam = new BooleanParameter("Force Fake Southernmost Hypos", false);
		params = new ParameterList();
		params.addParameter(forceFakeHypoParam);
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void setIMRParams(ScalarIMR imr) {
		// do nothing
	}

	@Override
	public double getModMean(ScalarIMR imr) {
		EqkRupture rup = imr.getEqkRupture();
		Site site = imr.getSite();
		RuptureSurface surf = rup.getRuptureSurface();
		if (forceFakeHypoParam.getValue() && rup.getHypocenterLocation() == null && surf != null) {
			Location first = surf.getFirstLocOnUpperEdge();
			Location last = surf.getFirstLocOnUpperEdge();
			if (first.getLatitude() < last.getLatitude())
				rup.setHypocenterLocation(first);
			else
				rup.setHypocenterLocation(last);
		}
		if (rup == null || rup.getHypocenterLocation() == null
				|| site == null || site.getLocation() == null
				|| surf == null || surf.isPointSurface())
			return imr.getMean();
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(SA_Param.NAME), "Must be S(a)");
		double period = SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure());
		Location siteLoc = site.getLocation();
		
		double fD = getFd(rup, siteLoc, period);
		
		return imr.getMean() + fD;
	}
	
	public double getFd(EqkRupture rup, Location siteLoc, double period) {
		Location hypo = rup.getHypocenterLocation();
		RuptureSurface surf = rup.getRuptureSurface();
		
		double rake = Math.abs(rup.getAveRake());
		Preconditions.checkState(rake <= 180d);
		
		boolean strikeSlip = (rake >= 0 && rake <= 30) || (rake >= 150 && rake <= 180);
		boolean dipSlip = rake >= 60 && rake <= 120;
		
		Location closestTraceLoc = calcClosestLoc(rup, siteLoc, false);
		Location closestSurfLoc;
		if (strikeSlip)
			closestSurfLoc = closestTraceLoc;
		else
			closestSurfLoc = calcClosestLoc(rup, siteLoc, true);
		// compute the distance between the closest point on the trace and the hypocenter
		Location hypoMappedToTraceLoc = calcClosestLoc(rup, hypo, false);
		double s = LocationUtils.horzDistance(closestTraceLoc, hypo);
//		double s = LocationUtils.horzDistance(closestTraceLoc, hypoMappedToTraceLoc);
		s = Math.max(s, Math.exp(1));
		// now for dipping
//		double d = LocationUtils.linearDistanceFast(closestLoc, hypo);
		double depthDiff = Math.abs(hypo.getDepth() - closestSurfLoc.getDepth());
		double d = depthDiff/Math.sin(Math.toRadians(surf.getAveDip()));
		d = Math.max(d, Math.exp(0));
		// degrees, according to Aki & Richards convention
		double azimuth = calcAzimuth(surf, siteLoc, closestTraceLoc);
//		Preconditions.checkState(azimuth >= 0 && azimuth <= 360, "bad azimuth: %s", azimuth);
		double azimuthRad = Math.toRadians(azimuth);
		// degrees, ignores Aki & Richards convention, must be 0 <= theta <= 90
//		double theta = calcTheta(siteLoc, hypo, closestTraceLoc);
		double theta = calcTheta(siteLoc, hypoMappedToTraceLoc, closestTraceLoc);
		if (theta > 90d) {
			if (theta < 180d)
				theta = 180d - theta;
			else if (theta < 270d)
				theta = theta - 180d;
			else
				theta = 360d - theta;
		}
		Preconditions.checkState(theta >= 0 && theta <= 90, "bad theta: %s", theta);
		double thetaRad = Math.toRadians(theta);
		
		// get total length of rupture
		// TODO deal with curvature? would have to above for s as well
		double L = LocationUtils.horzDistance(surf.getFirstLocOnUpperEdge(), surf.getLastLocOnUpperEdge());
//		double x = s / L;
//		// make sure that x isn't slightly larger (due to numerical impecision)
//		if (x > 1.0 & x < 1.001) {
//			x = 1.0;
//		}
		
		double rRup = surf.getDistanceRup(siteLoc);
		double mag = rup.getMag();
		
		double fD;
		if (strikeSlip) {
			// strike-slip
			
			Coeffs c = new Coeffs(period, true);
			fD = calcStrikeSlip(s, thetaRad, L, rRup, mag).calcFd(c.c0, c.c1);
		} else if (dipSlip) {
			// dip-slip
			
			Coeffs c = new Coeffs(period, false);
			fD = calcDipSlip(surf, siteLoc, d, azimuthRad, rRup, mag).calcFd(c.c0, c.c1);
		} else {
			// oblique-slip: 30 < rake < 60 || 120 < rake < 150
			double q1Rake = rake;
			if (q1Rake > 90)
				q1Rake = 180 - q1Rake;
			
			double dipWeight = q1Rake/90;
			double strikeWeight = 1 - dipWeight;
			
			Coeffs cDip = new Coeffs(period, false);
			Coeffs cStrike = new Coeffs(period, true);
			double fD_dip = calcDipSlip(surf, siteLoc, d, azimuthRad, rRup, mag).calcFd(cDip.c0, cDip.c1);
			double fD_strike = calcStrikeSlip(s, thetaRad, L, rRup, mag).calcFd(cStrike.c0, cStrike.c1);
			
			fD = strikeWeight*fD_strike+dipWeight*fD_dip;
		}
		Preconditions.checkState(Doubles.isFinite(fD));
		
		return fD;
	}
	
	private static final double HALF_PI = 0.5*Math.PI;

	private DirectivityParams calcStrikeSlip(double s, double thetaRad, double L,
			double rRup, double mag) {
		// eqn 2.3, geometric directivity predictor
//		if (thetaRad == 0d)
//			thetaRad = Math.toRadians(1e-2);
		double fGeom = Math.log(s)*(0.5*Math.cos(2d*thetaRad)+0.5);
		
		// eqn 2.4, distance taper
		double rRupOverL = rRup/L;
		
		double tCD;
		if (rRupOverL < 0.5)
			tCD = 1;
		else if (rRupOverL < 1d)
			tCD = 1d - (rRupOverL - 0.5)/0.5;
		else
			tCD = 0d;
		
		// eqn 2.5, magnitude taper
		double tMw;
		if (mag < 5d)
			tMw = 0;
		else if (mag < 6.5)
			tMw = 1 - (6.5 - mag)/1.5;
		else
			tMw = 1;
		
		// eqn 2.6, azimuth taper
		double tAz = 1;
		
		if (D) System.out.println("theta="+Math.toDegrees(thetaRad)+"\ts="+s+"\tfGeom="+fGeom);
		
		return new DirectivityParams(fGeom, tCD, tMw, tAz);
	}

	private DirectivityParams calcDipSlip(RuptureSurface surf, Location siteLoc, double d,
			double azimuth, double rRup, double mag) {
		double rx = surf.getDistanceX(siteLoc);
		double width = surf.getAveWidth();
		
		// eqn 2.7, geometric directivity predictor
		double fGeom = Math.log(d)*Math.cos(rx/width);
		
		double rRupOverW = rRup/width;
		
		// eqn 2.8, distance taper
		double tCD;
		if (rRupOverW < 1.5)
			tCD = 1;
		else if (rRupOverW < 2)
			tCD = 1 - (rRupOverW - 1.5)/0.5;
		else
			tCD = 0;
		
		// eqn 2.9, magnitude taper
		double tMw;
		if (mag < 5)
			tMw = 0;
		else if (mag < 6.5)
			tMw = 1 - (6.5 - mag)/1.5;
		else
			tMw = 1;
		
		// eqn 2.10, azimuth taper
		double tAz = Math.pow(Math.sin(Math.abs(azimuth)), 2);
//		double tAz = 1;
		
		return new DirectivityParams(fGeom, tCD, tMw, tAz);
//		return new DirectivityParams(1d, 1d, 1d, 1d);
	}
	
	private class DirectivityParams {
		private double fGeom, tCD, tMw, tAz;

		public DirectivityParams(double fGeom, double tCD, double tMw,
				double tAz) {
			this.fGeom = fGeom;
			this.tCD = tCD;
			this.tMw = tMw;
			this.tAz = tAz;
		}
		
		public double calcFd(double c0, double c1) {
			return (c0 + c1*fGeom)*tCD*tMw*tAz;
		}
	}
	
	private static double[] periods = { 0.5, 0.75, 1, 1.5, 2, 3, 4, 5, 7.5, 10 };
	
	private static ArbitrarilyDiscretizedFunc buildCoeffsFunc(double... vals) {
		Preconditions.checkState(periods.length == vals.length);
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.set(0d, 0d); // add point at start for interpolation
		for (int i=0; i<periods.length; i++)
			func.set(periods[i], vals[i]);
		return func;
	}
	
	// RotD50
	private static final ArbitrarilyDiscretizedFunc c0_ss_func =
			buildCoeffsFunc(0.0, 0.0, -0.120, -0.175, -0.210, -0.235, -0.255, -0.275, -0.290, -0.300);
	private static final ArbitrarilyDiscretizedFunc c1_ss_func =
			buildCoeffsFunc(0.0, 0.0, 0.075, 0.090, 0.095, 0.099, 0.103, 0.108, 0.112, 0.115);
	private static final ArbitrarilyDiscretizedFunc c0_dip_func =
			buildCoeffsFunc(0.0, 0.0, 0.0, 0.0, 0.0, -0.033, -0.089, -0.133, -0.160, -0.176);
	private static final ArbitrarilyDiscretizedFunc c1_dip_func =
			buildCoeffsFunc(0.0, 0.0, 0.0, 0.0, 0.034, 0.093, 0.128, 0.150, 0.165, 0.179);
	
	private class Coeffs {
		
		private double c0, c1;
		
		public Coeffs(double period, boolean strikeSlip) {
			if (strikeSlip) {
				c0 = c0_ss_func.getInterpolatedY(period);
				c1 = c1_ss_func.getInterpolatedY(period);
			} else {
				c0 = c0_dip_func.getInterpolatedY(period);
				c1 = c1_dip_func.getInterpolatedY(period);
			}
		}
	}
	
	private static Location calcClosestLoc(EqkRupture rup, Location siteLoc, boolean fullSurf) {
		RuptureSurface surf = rup.getRuptureSurface();
		if (fullSurf && surf instanceof EvenlyGriddedSurface) {
			// find closest trace point and do search around there
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
			int closestCol = -1;
			double minDist = Double.POSITIVE_INFINITY;
			for (int c=0; c<gridSurf.getNumCols(); c++) {
				Location loc = gridSurf.get(0, c);
				double dist = LocationUtils.horzDistanceFast(siteLoc, loc);
				if (dist < minDist) {
					minDist = dist;
					closestCol = c;
				}
			}
			
			int colStart = closestCol - 10;
			int colEnd = closestCol + 10;
			if (colStart < 0)
				colStart = 0;
			if (colEnd >= gridSurf.getNumCols())
				colEnd = gridSurf.getNumCols()-1;
			int numCols = colEnd - colStart + 1;
//			Preconditions.checkState(colStart + numCols < gridSurf.getNumCols(), "tot=%s, start=%s, end=%s, calcNum=%s",
//					gridSurf.getNumCols(), colStart, colEnd, numCols);
			
			surf = new GriddedSubsetSurface(gridSurf.getNumRows(), numCols, 0, colStart, gridSurf);
		}
		List<Location> locs;
		if (fullSurf)
			locs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
		else
			locs = surf.getEvenlyDiscritizedUpperEdge();

		Preconditions.checkState(locs.size() > 1, "shouldn't be a point source...we already checked?");

		// find the closest point on rupture trace
		double dist, closestDist = Double.MAX_VALUE;
		Location closestLoc = null;
		for (Location loc : locs) {
			dist = LocationUtils.horzDistance(siteLoc, loc);
			if (dist < closestDist) {
				closestDist = dist;
				closestLoc = loc;
			}
		}
		
		return closestLoc;
	}
	
	private static double calcAzimuth(RuptureSurface surf, Location siteLoc, Location closestLoc) {
		LocationVector traceVector = LocationUtils.vector(surf.getFirstLocOnUpperEdge(), surf.getLastLocOnUpperEdge());
		LocationVector siteVector = LocationUtils.vector(closestLoc, siteLoc);
		
		double traceAz = traceVector.getAzimuth();
		double siteAz = siteVector.getAzimuth();
		
		return siteAz - traceAz;
	}
	
	private static double calcTheta(Location siteLoc, Location hypo, Location closestLoc) {
		double angleDiff;
		LocationVector dir;
		dir = LocationUtils.vector(hypo, siteLoc);
		double angle1 = dir.getAzimuth();
		if (angle1 < 0) {
			angle1 += 360; // make it positive to avoid confusion
		}
		dir = LocationUtils.vector(hypo, closestLoc);
		double angle2;
		if (dir.getHorzDistance() < 1e-2)
			// fix for undefined angle where closes loc is the hypocenter loc
			angle2 = 180d;
		else
			angle2 = dir.getAzimuth();
		if (angle2 < 0) {
			angle2 += 360; // make it positive to avoid confusion
		}
		angleDiff = angle2 - angle1;
		// fix if 0 or 360 is in between the two directions
		if (angleDiff < -90) {
			angleDiff += 360;
		} else if (angleDiff > 90) {
			angleDiff -= 360;
		}
		if (D) {
			System.out.println("hyp=" + (float) hypo.getLatitude() + ", " +
					(float) hypo.getLongitude() +
					"; clLoc=" + (float) closestLoc.getLatitude() + ", " +
					(float) closestLoc.getLongitude() +
					"; siteLoc=" + (float) siteLoc.getLatitude() + ", " +
					(float) siteLoc.getLongitude() +
					"; angle1 = " + (float) angle1 + "; angle2 = " +
					(float) angle2 + "; theta = " + (float) angleDiff
					+"; hypCloseDist = "+(float)dir.getHorzDistance());
		}
		while (angleDiff < 0)
			angleDiff += 360;
		return angleDiff;
	}

	@Override
	public double getModStdDev(ScalarIMR imr) {
		return imr.getStdDev();
	}

	@Override
	public ParameterList getModParams() {
		return params;
	}
	
	public static void main(String[] args) {
//		BaylessSomerville2013DirectivityModifier mod = new BaylessSomerville2013DirectivityModifier();
		
		//								len		width	rake	dip		mag		hypAl	hypD	bend	dist	period	name
//		mod.calcTestCase(buildTestRup(	25d,	13d,	180,	90,		6.5,	0.1,	2,		0),		15d,	5d,		"ss2");
//		mod.calcTestCase(buildTestRup(	80d,	15d,	180,	90,		7.2,	0.1,	5,		0),		150d,	5d,		"ss3");
//		mod.calcTestCase(buildTestRup(	235d,	15d,	180,	90,		7.8,	0.1,	5,		0),		500d,	5d,		"ss4");
//		mod.calcTestCase(buildTestRup(	400d,	15d,	180,	90,		8.1,	0.1,	5,		0),		800d,	5d,		"ss7");
//		mod.calcTestCase(buildTestRup(	32d,	28d,	90,		30,		7,		0.1,	8,		0),		100d,	5d,		"rv4");
//		mod.calcTestCase(buildTestRup(	80d,	30d,	90,		30,		7.5,	0.1,	8,		45),	100d,	5d,		"rv7");
//		mod.calcTestCase(buildTestRup(	80d,	15d,	135,	70,		7.2,	0.1,	5,		0),		150d,	5d,		"so6");
	}

}
