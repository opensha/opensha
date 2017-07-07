package org.opensha.sha.faultSurface;

import static org.opensha.commons.geo.GeoTools.*;
import static org.apache.commons.math3.geometry.euclidean.threed.RotationOrder.*;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceSeisParameter;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

/**
 * Quadrilateral surface implementation - treats calculating the shortest distance
 * as 2D problem. The parallelograms representing each fault segment are rotated
 * into the xy plane of a local cartesian coordinate system. Precalculating and
 * storing the 2D parallelograms and the required rotation matrices drastically
 * reduces the time required to calculate the minimum distance to a large fault
 * surface, although performance is similar/worse than the standard gridded
 * implementation for short (~10 km) faults.<br />
 * <br />
 * Internally, this class uses a right-handed cartesian coordinate system where
 * x is longitude, y is latitude, and z is depth (positive down per seismological
 * convention). This convention preserves strike values (degrees clockwise from
 * north) as clockwise rotation about the z-axis per cartesian convention.<br />
 * <br />
 * Distance X is calculated similarly to the gridded case, but in cartesian coordinates
 * so the extension of the average strike does not follow the great circle. This only
 * affects sites directly off the end of faults.
 * 
 * @author Peter Powers, Kevin Milner
 * @version $Id:$
 */
public class QuadSurface implements RuptureSurface, CacheEnabledSurface {
	
	final static boolean D = false;
	
	private double dipDeg;
	private double dipRad;
	private double width;
	private double avgUpperDepth;
	private double avgDipDirRad;
	private double avgDipDirDeg;
	
	/* true if the entire trace is below 3km */
	private boolean traceBelowSeis;

	/* actual 3d values */
	private FaultTrace trace;
	private List<Rotation> rots;
	private List<Path2D> surfs;

	/* surface projection (for dist jb) */
	private FaultTrace proj_trace;
	private List<Path2D> proj_surfs;

	/* portion of fault below seismogenic depth of 3km (for dist seis) */
	private FaultTrace seis_trace;
	private List<Rotation> seis_rots;
	private List<Path2D> seis_surfs;

	/* for distance X calcs */
	private FaultTrace x_trace;
	private List<Rotation> x_rots;
	private List<Path2D> x_surfs;
	private List<Vector3D> x_trace_vects;
	
	/*
	 * discretization to use for evenly discretized methods
	 */
	private double discr_km = 1d;
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	/**
	 * If true, distance X will use the average strike to extend the trace infinitely, as opposed
	 * to extending the last trace segment itself indfinitely.
	 */
	private boolean distX_useAvgStrike = true;
	
	private static double calcWidth(FaultSectionPrefData prefData, boolean aseisReducesArea) {
		double upperDepth;
		if (aseisReducesArea)
			upperDepth = prefData.getReducedAveUpperDepth();
		else
			upperDepth = prefData.getOrigAveUpperDepth();
		double lowerDepth = prefData.getAveLowerDepth();
//		System.out.println("Wdith calc: ("+lowerDepth+"-"+upperDepth+") * "
//				+Math.sin(Math.toRadians(prefData.getAveDip())));
//		System.out.println("Dip: "+prefData.getAveDip());
		return (lowerDepth-upperDepth) / Math.sin(Math.toRadians(prefData.getAveDip()));
	}
	
	/**
	 * This moves the trace down to the top of the seismogenic zone
	 * @param prefData
	 * @param aseisReducesArea
	 * @return
	 */
	private static FaultTrace getTraceBelowSeismogenic(FaultSectionPrefData prefData, boolean aseisReducesArea) {
		double upperSeismogenicDepth;
		if (aseisReducesArea)
			upperSeismogenicDepth = prefData.getReducedAveUpperDepth();
		else
			upperSeismogenicDepth = prefData.getOrigAveUpperDepth();

		double aveDipRadians = Math.toRadians(prefData.getAveDip());
		double aveDipDirection = prefData.getDipDirection();
		return getTraceBelowDepth(prefData.getFaultTrace(), upperSeismogenicDepth, aveDipRadians, aveDipDirection);
	}
	
	private static FaultTrace getTraceBelowDepth(FaultTrace trace, double depth, double avgDipRad, double dipDirDeg) {
		FaultTrace belowTrace = new FaultTrace("");
		for (Location loc : trace)
			belowTrace.add(StirlingGriddedSurface.getTopLocation(
					loc, depth, avgDipRad, dipDirDeg));
		return belowTrace;
	}
	
	public QuadSurface(FaultSectionPrefData prefData, boolean aseisReducesArea) {
		this(getTraceBelowSeismogenic(prefData, aseisReducesArea),
				prefData.getAveDip(), calcWidth(prefData, aseisReducesArea));
//		this(prefData.getFaultTrace(),
//				prefData.getAveDip(), calcWidth(prefData, aseisReducesArea));
	}

	/**
	 * 
	 * @param trace
	 * @param dip in degrees
	 * @param width down dip width in km
	 */
	public QuadSurface(FaultTrace trace, double dip, double width) {
		this.trace = trace;
		this.dipDeg = dip;
		this.dipRad = dip * TO_RAD;
		this.width = width;
		rots = new ArrayList<Rotation>();
		surfs = new ArrayList<Path2D>();
		
		// TODO USE DIP DIR FROM FSD
		avgDipDirRad = (trace.getStrikeDirection() * TO_RAD) + PI_BY_2;
		avgDipDirDeg = avgDipDirRad * TO_DEG;
		
		initSegments(dipRad, avgDipDirRad, width, trace, rots, surfs);
		
		traceBelowSeis = true;
		avgUpperDepth = 0d;
		// TODO weight average
		for (Location loc : trace) {
			if (loc.getDepth() <= DistanceSeisParameter.SEIS_DEPTH)
				traceBelowSeis = false;
			avgUpperDepth += loc.getDepth();
		}
		avgUpperDepth /= (double)trace.size();
	}

	private static void initSegments(double dipRad, double avgDipDirRad, double width,
			FaultTrace trace, List<Rotation> rots, List<Path2D> surfs) {
		Preconditions.checkState(!Double.isNaN(dipRad), "dip cannot be NaN!");
		Preconditions.checkState(dipRad > 0 && dipRad <= PI_BY_2, "dip must be > 0 and <= 90");
		Preconditions.checkState(!Double.isNaN(avgDipDirRad), "dip direction cannot be NaN!");
		Preconditions.checkState(!Double.isNaN(width), "width cannot be NaN!");
		for (int i = 0; i < trace.size() - 1; i++) {

			Location p1 = trace.get(i);
			Location p2 = trace.get(i + 1);
			LocationVector vec = LocationUtils.vector(p1, p2);

			double surfStrk = vec.getAzimuthRad();
			double surfDip; // true dip of parallelogram
			double p1p2Dist = vec.getHorzDistance();

			// top trace #1 is at [0,0]
			Vector3D vt1 = Vector3D.ZERO;

			// top trace #2
			Vector3D vt2 = new Vector3D(p1p2Dist, new Vector3D(surfStrk, 0));

			// bottom trace #1
			Vector3D vb1 = new Vector3D(width, new Vector3D(avgDipDirRad, dipRad));

			// bottom trace #2
			Vector3D vb2 = new Vector3D(1, vt2, 1, vb1);
			
			if (D) {
				System.out.println("Pre-rotation (width="+width+"):");
				System.out.println("\tvt1="+vt1);
				System.out.println("\tvt2="+vt2);
				System.out.println("\tvb1="+vb1);
				System.out.println("\tvb2="+vb2);
			}

			// set rotation // true dip of surface - rotate vb1 the strike angle about
			// the z-axis, and flatten onto xy plane [0,y,z]
			Rotation dRot = new Rotation(Vector3D.PLUS_K, -surfStrk);
			Vector3D dVec = dRot.applyTo(vb1);
			dVec = new Vector3D(0, dVec.getY(), dVec.getZ());
			surfDip = dVec.getDelta();

			Rotation rot = new Rotation(XYZ, -surfDip, 0, -surfStrk);
			rots.add(rot);

			// rotate parallelogram
			vt2 = rot.applyTo(vt2);
			vb1 = rot.applyTo(vb1);
			vb2 = rot.applyTo(vb2);
			
			if (D) {
				// make sure rotation worked and z=0 for all trace poitns
				Preconditions.checkState(Math.abs(vt1.getZ()) < 1e-10, "vt1 z non zero: "+vt1);
				Preconditions.checkState(Math.abs(vt2.getZ()) < 1e-10, "vt2 z non zero: "+vt2);
				Preconditions.checkState(Math.abs(vb1.getZ()) < 1e-10, "vb1 z non zero: "+vb1);
				Preconditions.checkState(Math.abs(vb2.getZ()) < 1e-10, "vb2 z non zero: "+vb2);
				
				double debugWidth = Math.sqrt(Math.pow(vb1.getX(), 2)+Math.pow(vb1.getY(), 2));
				Preconditions.checkState((float)debugWidth == (float)width,
						"Width not preserved in projection: "+debugWidth+" != "+width);
				System.out.println("debug width: "+debugWidth);
				System.out.println("width: "+width);
				System.out.println(vb1.getX()+", "+vb1.getY());
			}

			// set up for 2D ops in yz plane
			Path2D surface = new Path2D.Double();
			surface.moveTo(vt1.getX(), vt1.getY());
			surface.lineTo(vt2.getX(), vt2.getY());
			surface.lineTo(vb2.getX(), vb2.getY());
			surface.lineTo(vb1.getX(), vb1.getY());
			surface.lineTo(vt1.getX(), vt1.getY());
			surfs.add(surface);

//			System.out.println("vt1: "+vt1);
//			System.out.println("vt2: "+vt2);
//			System.out.println("vb1: "+vb1);
//			System.out.println("vb2: "+vb2);
		}
	}

	private static void initSegmentsJB(double dipRad, double avgDipDirRad, double width,
			FaultTrace trace, List<Path2D> surfs) {
		// this is for distance JB
		Preconditions.checkState(!Double.isNaN(dipRad), "dip cannot be NaN!");
		Preconditions.checkState(dipRad > 0 && dipRad <= PI_BY_2, "dip must be > 0 and <= 90");
		Preconditions.checkState(!Double.isNaN(avgDipDirRad), "dip direction cannot be NaN!");
		Preconditions.checkState(!Double.isNaN(width), "width cannot be NaN!");
		// now project width to the surface;
		width = width*Math.cos(dipRad);
		dipRad = 0;
		for (int i = 0; i < trace.size() - 1; i++) {

			Location p1 = trace.get(i);
			Location p2 = trace.get(i + 1);
			LocationVector vec = LocationUtils.vector(p1, p2);

			double surfStrk = vec.getAzimuthRad();
			double surfDip; // true dip of parallelogram
			double p1p2Dist = vec.getHorzDistance();

			// top trace #1 is at [0,0]
			Vector3D vt1 = Vector3D.ZERO;

			// top trace #2
			Vector3D vt2 = new Vector3D(p1p2Dist, new Vector3D(surfStrk, 0));

			// bottom trace #1
			Vector3D vb1 = new Vector3D(width, new Vector3D(avgDipDirRad, dipRad));

			// bottom trace #2
			Vector3D vb2 = new Vector3D(1, vt2, 1, vb1);
			
			// now make sure everything is indeed still at the surface
			Preconditions.checkState(vt2.getZ() == 0 && vb1.getZ() == 0 && vb2.getZ() == 0);

			// set up for 2D ops in yz plane
			Path2D surface = new Path2D.Double();
			surface.moveTo(vt1.getX(), vt1.getY());
			surface.lineTo(vt2.getX(), vt2.getY());
			if (dipRad < PI_BY_2) {
				// only need line at the top for vertical
				surface.lineTo(vb2.getX(), vb2.getY());
				surface.lineTo(vb1.getX(), vb1.getY());
				surface.lineTo(vt1.getX(), vt1.getY());
			}
			surfs.add(surface);

//			System.out.println("vt1: "+vt1);
//			System.out.println("vt2: "+vt2);
//			System.out.println("vb1: "+vb1);
//			System.out.println("vb2: "+vb2);
		}
	}
	
	@Override
	public SurfaceDistances calcDistances(Location loc) {
		double distRup = calcDistanceRup(loc);
		double distJB = calcDistanceJB(loc);
		double distSeis;
		if (traceBelowSeis)
			distSeis = distRup;
		else
			distSeis = calcDistanceSeis(loc);
		return new SurfaceDistances(distRup, distJB, distSeis);
	}
	
	private double calcDistanceRup(Location loc) {
		return distance3D(trace, rots, surfs, loc);
	}
	
	private double calcDistanceJB(Location loc) {
		if (proj_trace == null) {
			synchronized(this) {
				if (proj_trace == null) {
					// surface projection for calculating distance JB
					FaultTrace proj_trace = new FaultTrace("surface projection");
					for (Location traceLoc : trace)
						proj_trace.add(new Location(traceLoc.getLatitude(), traceLoc.getLongitude()));
					proj_surfs = new ArrayList<Path2D>();
					initSegmentsJB(dipRad, avgDipDirRad, width, trace, proj_surfs);
					this.proj_trace = proj_trace;
				}
			}
		}
		
		return distance3D(proj_trace, null, proj_surfs, new Location(loc.getLatitude(), loc.getLongitude()));
	}
	
	private double calcDistanceSeis(Location loc) {
		if (seis_trace == null) {
			synchronized(this) {
				if (seis_trace == null) {
					FaultTrace seis_trace;
					if (traceBelowSeis) {
						// it's already below the seismogenic depth, use normal trace/rots/surfs
						seis_trace = trace;
						seis_rots = rots;
						seis_surfs = surfs;
					} else {
						seis_trace = getTraceBelowDepth(trace, DistanceSeisParameter.SEIS_DEPTH, dipRad, avgDipDirDeg);
						seis_rots = new ArrayList<Rotation>();
						seis_surfs = new ArrayList<Path2D>();
						
						// new width below seis
						double widthBelowSeis;
						if (avgUpperDepth < DistanceSeisParameter.SEIS_DEPTH)
							widthBelowSeis = width - (DistanceSeisParameter.SEIS_DEPTH - avgUpperDepth);
						else
							widthBelowSeis = width;
						initSegments(dipRad, avgDipDirRad, widthBelowSeis, seis_trace, seis_rots, seis_surfs);
					}
					this.seis_trace = seis_trace;
				}
			}
		}
		return distance3D(seis_trace, seis_rots, seis_surfs, new Location(loc.getLatitude(), loc.getLongitude()));
	}

	public double getDistanceRup(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceRup();
	}

	public double getDistanceJB(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceJB();
	}

	public double getDistanceSeis(Location loc) {
		return cache.getSurfaceDistances(loc).getDistanceSeis();
	}
	
	/**
	 * Returns the given point projected into the plane of the given trace index used for
	 * distance rup calculations. Useful for debugging/tests
	 * @param traceIndex
	 * @param loc
	 * @return
	 */
	Vector3D getRupProjectedPoint(int traceIndex, Location loc) {
		return getProjectedPoint(trace, rots, traceIndex, loc);
	}
	
	private static Vector3D getProjectedPoint(
			FaultTrace trace, List<Rotation> rots, int traceIndex, Location loc) {
		// compute geographic vector to point
		LocationVector vec = LocationUtils.vector(trace.get(traceIndex), loc);
		// convert to cartesian
		Vector3D vp = new Vector3D(vec.getHorzDistance(), new Vector3D(
			vec.getAzimuthRad(), 0), vec.getVertDistance(), Vector3D.PLUS_K);
		if (rots != null)
			// rotate
			vp = rots.get(traceIndex).applyTo(vp);
		return vp;
	}
	
	private static double distance3D(FaultTrace trace, List<Rotation> rots, List<Path2D> surfs, Location loc) {
		double distance = Double.MAX_VALUE;
		for (int i = 0; i < trace.size() - 1; i++) {
			if (D) System.out.println("Calc dist for trace pt "+i);
			// convert to cartesian projected pt
			Vector3D vp = getProjectedPoint(trace, rots, i, loc);
			// compute distance
			Path2D surf = surfs.get(i);
			if (surf.contains(vp.getX(), vp.getY())) {
				if (D) System.out.println("Contained! Z dist: "+vp.getZ());
				distance = Math.min(distance, Math.abs(vp.getZ()));
			} else {
				if (D) System.out.println("Outside! dist: "+distanceToSurface(vp, surf));
				distance = Math.min(distance, distanceToSurface(vp, surf));
			}
			if (D) {
				System.out.flush();
				showDebugGraphIgnoreError(surf, vp, true);
			}
			if (distance == 0)
				return 0;
		}
		if (D && Double.isNaN(distance)) {
			for (int i = 0; i < trace.size() - 1; i++) {
				if (rots != null)
					System.out.println(rots.get(i).getAngle());
				System.out.println(surfs.get(i));
			}
		}
		Preconditions.checkState(!Double.isNaN(distance));
		return distance;
	}
	
	/*
	 * this will prevent headless exceptions if debug is enabled in a headless env
	 */
	private static void showDebugGraphIgnoreError(Path2D surf, Vector3D vp, boolean waitForClose) {
		try {
			showDebugGraph(surf, vp, waitForClose, null);
		} catch (Exception e) {}
	}
	
	private static void showDebugGraph(Path2D surf, Vector3D vp, boolean waitForClose,
			List<Vector3D> otherVects) {
		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		PathIterator pit = surf.getPathIterator(null);
		double[] c = new double[6]; // coordinate array
		double[] prev_pt = new double[2]; // previous coordinate array
		while (!pit.isDone()) {
			int type = pit.currentSegment(c);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				// do nothing, this is just resetting the current location. not a line
				break;
			case PathIterator.SEG_LINETO:
				// this defines a line, check the distance
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(prev_pt[0], prev_pt[1]);
				xy.set(c[0], c[1]);
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
				break;

			default:
				throw new IllegalStateException("unkown path operation: "+type);
			}
			// this will set the previous location
			prev_pt[0] = c[0];
			prev_pt[1] = c[1];
			pit.next();
		}
		if (vp != null) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			xy.set(vp.getX(), vp.getY());
			funcs.add(xy);
			Color col;
			if (surf.contains(vp.getX(), vp.getY()))
				col = Color.GREEN;
			else
				col = Color.RED;
			chars.add(new PlotCurveCharacterstics(PlotSymbol.X, 6f, col));
		}
		if (otherVects != null) {
			MinMaxAveTracker zTrack = new MinMaxAveTracker();
			for (Vector3D v : otherVects)
				zTrack.addValue(Math.abs(v.getZ()));
			CPT cpt;
			try {
				cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0d, zTrack.getMax());
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			System.out.println("Plot Z range: "+zTrack);
			for (Vector3D v : otherVects) {
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(v.getX(), v.getY());
				funcs.add(xy);
//				Color col = Color.BLUE;
				Color col = cpt.getColor((float)Math.abs(v.getZ()));
				PlotSymbol sym;
				if (surf.contains(v.getX(), v.getY()))
					sym = PlotSymbol.FILLED_CIRCLE;
				else
					sym = PlotSymbol.CIRCLE;
				chars.add(new PlotCurveCharacterstics(sym, 3f, col));
			}
		}
		GraphWindow gw = new GraphWindow(funcs, "Surface Debug", chars);
		// now wait until closed
		while (waitForClose && gw.isVisible()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}

	/*
	 * Iterates over surface outline path calculating distance to line segments
	 * and returning the minimum.
	 */
	private static double distanceToSurface(Vector3D p, Path2D border) {
		PathIterator pit = border.getPathIterator(null);
		double[] c = new double[6]; // coordinate array
		double[] prev_pt = new double[2];
		double minDistSq = Double.MAX_VALUE;
		while (!pit.isDone()) {
			// this puts the current location in the first two elements of c
			int type = pit.currentSegment(c);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				// do nothing, this is just resetting the current location. not a line
				break;
			case PathIterator.SEG_LINETO:
				// this defines a line, check the distance
				double distSq = Line2D.ptSegDistSq(prev_pt[0], prev_pt[1], c[0], c[1],
						p.getX(), p.getY());
				minDistSq = Math.min(minDistSq, distSq);
				break;

			default:
				throw new IllegalStateException("unkown path operation: "+type);
			}
			// this will set the previous location
			prev_pt[0] = c[0];
			prev_pt[1] = c[1];
			pit.next();
		}
		return Math.sqrt(p.getZ() * p.getZ() + minDistSq);
	}
	
	@Override
	public synchronized double calcDistanceX(Location siteLoc) {
		// this is Peter's implementation, but it doesn't perform as well in tests
//		if (1d < 2d) {
//			if (trace.size() == 1) return 0.0;
//			int minIdx = trace.minDistIndex(siteLoc);
//			double rSeg = LocationUtils.distanceToLineSegmentFast(trace.get(minIdx),
//			trace.get(minIdx + 1), siteLoc);
//			double rFirst = LocationUtils.horzDistanceFast(trace.get(0), siteLoc);
//			double rLast = LocationUtils.horzDistanceFast(trace.last(), siteLoc);
//
//			return (rSeg < Math.min(rFirst, rLast)) ? LocationUtils.distanceToLineFast(
//			trace.get(minIdx), trace.get(minIdx + 1), siteLoc)
//				: LocationUtils.distanceToLineFast(trace.first(), trace.last(), siteLoc);
//		}
		if (x_trace_vects == null) {
			// we recalculate the rotations because don't want to consider dip
			x_rots = Lists.newArrayList();
			x_surfs = Lists.newArrayList();
			if (distX_useAvgStrike) {
				// add tiny traces spans to the ends in the direction of getAvgStrike
				x_trace = new FaultTrace("dist x");
				Location startPt = trace.first();
				Location endPt = trace.last();
				double strikeDirRad = LocationUtils.azimuthRad(startPt, endPt);
				double reverseStrikeDirRad = LocationUtils.azimuthRad(endPt, startPt);
				double dist_x_pad_dist = 1e-6;
//				double dist_x_pad_dist = 1000;
				x_trace.add(LocationUtils.location(startPt, reverseStrikeDirRad, dist_x_pad_dist));
				x_trace.addAll(trace);
				x_trace.add(LocationUtils.location(endPt, strikeDirRad, dist_x_pad_dist));
			} else {
				x_trace = trace;
			}
			initSegments(PI_BY_2, avgDipDirRad, width, x_trace, x_rots, x_surfs);
			// this is a list of vectors from the origin in the trace pt local coordinate system
			x_trace_vects = Lists.newArrayList();
			for (int i = 0; i < x_trace.size() - 1; i++) {
				Path2D surf = x_surfs.get(i);
				PathIterator pit = surf.getPathIterator(null);
				double[] c = new double[6]; // coordinate array
				// load in origin, ensuring that it's indeed the origin
				Preconditions.checkState(pit.currentSegment(c) == PathIterator.SEG_MOVETO);
				pit.next();
				Preconditions.checkState((float)c[0] == (float)0);
				Preconditions.checkState((float)c[1] == (float)0);
				// load in second trace point, ensuring that it's along the x axis
				Preconditions.checkState(pit.currentSegment(c) == PathIterator.SEG_LINETO);
				Preconditions.checkState(Math.abs(c[1]) < 1e-10);
				x_trace_vects.add(new Vector3D(c[0], c[1], 0));
			}
		}
		// TODO do it right
//		distanceX =  GriddedSurfaceUtils.getDistanceX(getEvenlyDiscritizedUpperEdge(), siteLoc);
//		return distanceX;
//		return distanceX + distanceX*(0.5 - Math.random());
		double distanceSq = Double.MAX_VALUE;
		double distance = Double.MAX_VALUE;
		for (int i = 0; i < x_trace.size() - 1; i++) {
			// compute geographic vector to point
			LocationVector vec = LocationUtils.vector(x_trace.get(i), siteLoc);
			// convert to cartesian
			Vector3D vp = new Vector3D(vec.getHorzDistance(), new Vector3D(
				vec.getAzimuthRad(), 0), vec.getVertDistance(), Vector3D.PLUS_K);
			// rotate
			vp = x_rots.get(i).applyTo(vp);
			double siteX = vp.getX();
			double siteY = vp.getY();
			double siteZ = vp.getZ();
			// now get the trace vector
			Vector3D traceVect = x_trace_vects.get(i);
			double traceX = traceVect.getX();
			double traceY = traceVect.getY();
			// since traceVect is along the X axis, the distance to the segment can be calculated easily
			boolean trueDist; // if true, we do an actual 3d distance to segment. otherwise just y/z dist
			if (siteX < 0) {
				// it's to the left in our projected trace
				// do true distance if this isn't the leftmost trace point
				trueDist = i > 0;
			} else if (siteX > traceX) {
				// it's to the right in our projected trace
				// do true distance if this isn't the leftmost trace point
				trueDist = i < x_trace.size()-2;
			} else {
				// this is directly above/below the trace
				trueDist = false;
			}
			double myDistSq;
			if (trueDist)
				myDistSq = Line2D.ptSegDistSq(0d, 0d, traceX, 0,
						siteX, siteZ);
			else
				myDistSq = siteZ * siteZ;
			
			if (myDistSq < distanceSq) {
				distanceSq = myDistSq;
				distance = Math.sqrt(myDistSq);
				// faults dip in the positive y direction, so neg y is on foot wall
				if (siteZ > 0)
					distance = -distance;
			}
		}
		return distance;
	}
	
	public double getDistanceX(Location siteLoc) {
		return cache.getDistanceX(siteLoc);
	}
	
//	private EvenlyGriddedSurface getGridded() {
//		if (gridSurf == null) {
//			double lower = avgUpperDepth + width;
//			gridSurf = new StirlingGriddedSurface(trace, dip, avgUpperDepth, lower, discr_km);
//		}
//		return gridSurf;
//	}

	@Override
	public double getAveDip() {
		return dipDeg;
	}

	@Override
	public double getAveStrike() {
		return trace.getAveStrike();
	}

	@Override
	public double getAveLength() {
		return trace.getTraceLength();
	}

	@Override
	public double getAveWidth() {
		return width;
	}

	@Override
	public double getArea() {
		return getAveLength()*getAveWidth();
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		LocationList locs = new LocationList();
		locs.addAll(getEvenlyDiscritizedUpperEdge());
		int numSpans = (int)Math.ceil(trace.getTraceLength()/discr_km);
		int numDDW = (int)Math.ceil(width/discr_km);
		double ddw_increment = width/(double)numDDW;
		for (int i=0; i<numDDW; i++) {
			FaultTrace subTrace = new FaultTrace("subTrace");
			subTrace.addAll(getHorizontalPoints(ddw_increment*(double)(i+1)));
			locs.addAll(FaultUtils.resampleTrace(subTrace, numSpans));
		}
		return locs;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getEvenlyDiscritizedListOfLocsOnSurface().listIterator();
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		// build permineter
		LocationList perim = new LocationList();
		LocationList upper = getEvenlyDiscritizedUpperEdge();
		LocationList lower = getEvenlyDiscritizedLowerEdge();
		LocationList right = GriddedSurfaceUtils.getEvenlyDiscretizedLine(upper.last(), lower.last(), discr_km);
		LocationList left = GriddedSurfaceUtils.getEvenlyDiscretizedLine(lower.first(), upper.first(), discr_km);
		// top, forwards
		perim.addAll(upper);
		// "right", except the first point
		perim.addAll(right.subList(1, right.size()));
		// bottom, backwards
		perim.addAll(getReversed(lower).subList(1, lower.size()));
		// "left"
		perim.addAll(left.subList(1, left.size()));
		return perim;
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		// TODO cache these?
		int numSpans = (int)Math.ceil(trace.getTraceLength()/discr_km);
		return FaultUtils.resampleTrace(trace, numSpans);
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		FaultTrace lower = new FaultTrace("lower");
		lower.addAll(getHorizontalPoints(width));
		int numSpans = (int)Math.ceil(lower.getTraceLength()/discr_km);
		return FaultUtils.resampleTrace(lower, numSpans);
	}

	@Override
	public double getAveGridSpacing() {
		return discr_km;
	}
	
	/**
	 * Sets grid spacing used for all evenly discretized methods
	 * @param gridSpacing
	 */
	public void setAveGridSpacing(double gridSpacing) {
		this.discr_km = gridSpacing;
	}

	@Override
	public double getAveRupTopDepth() {
		return avgUpperDepth;
	}

	@Override
	public double getAveDipDirection() {
		return Math.toDegrees(avgDipDirRad);
	}

	@Override
	public FaultTrace getUpperEdge() {
		return trace;
	}

	@Override
	public LocationList getPerimeter() {
		// build permineter
		LocationList perim = new LocationList();
		// top, forwards
		for (Location loc : trace)
			perim.add(loc);
		// bottom, backwards
		perim.addAll(getReversed(getHorizontalPoints(width)));
		// close it
		perim.add(perim.get(0));
		return perim;
	}
	
	private static LocationList getReversed(LocationList locs) {
		LocationList reversed = new LocationList();
		for (int i=locs.size(); --i>=0;)
			reversed.add(locs.get(i));
		return reversed;
	}
	
	/**
	 * This returns basically a fault trace, but at the given depth down dip
	 * of the fault. If width is passed in, the bottom trace is given.
	 * 
	 * Points given in same order as top fault trace.
	 * @param widthDownDip
	 * @return
	 */
	private LocationList getHorizontalPoints(double widthDownDip) {
		LocationList locs = new LocationList();
		double hDistance = widthDownDip * Math.cos( dipRad );
		double vDistance = widthDownDip * Math.sin(dipRad);
		LocationVector dir = new LocationVector(avgDipDirDeg, hDistance, vDistance);
		for (Location traceLoc : trace) {
			locs.add(LocationUtils.location(traceLoc, dir));
		}
		return locs;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		return trace.get(0);
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		return trace.last();
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not yet implemented");
	}

	@Override
	public String getInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not yet implemented");
	}
	
	private static Location getTestLoc(boolean randomize) {
		if (randomize)
			return new Location(34d + Math.random(), -120d + Math.random());
		return new Location(34d, -120d);
	}

	public static void main(String[] args) throws IOException, DocumentException {
//		double depth = 0;
//		Location l1 = new Location(34.0, -118.0, depth);
//		Location l2 = new Location(34.1, -117.9, depth);
//		Location l3 = new Location(34.3, -117.8, depth);
//		Location l4 = new Location(34.4, -117.7, depth);
//		Location l5 = new Location(34.5, -117.5, depth);
//
//		FaultTrace ft = new FaultTrace("Test");
//		ft.add(l1);
//		ft.add(l2);
//		ft.add(l3);
//		ft.add(l4);
//		ft.add(l5);
//
//		// double stk = 35;
//		double dip = 5;
//		double wid = 15;
//		QuadSurface dt = new QuadSurface(ft, dip, wid);
//
//		Location p = new Location(34.0, -117.9);
//		System.out.println(dt.getDistanceRup(p));
//		p = new Location(34.2, -117.8);
//		System.out.println(dt.getDistanceRup(p));
//
//		p = new Location(34.3, -117.7);
//		System.out.println(dt.getDistanceRup(p));
//
//		p = new Location(34.4, -117.6);
//		System.out.println(dt.getDistanceRup(p));
		
		double topDepth = 0d;
		double width = 10d;
		double dip = 45;
//		Location l1 = new Location(34.0, -118.0, topDepth);
//		Location l2 = new Location(34.1, -117.9, topDepth);
		Location l1 = new Location(34.0, -118.0, topDepth);
		Location l2 = new Location(36.1, -118.0, topDepth);
//		Location l1 = new Location(0.00, 0.00, topDepth);
//		Location l2 = new Location(0.01, 0.01, topDepth);
		
		Location distXDebug = new Location(35d, -119);

		FaultTrace ft = new FaultTrace("Test");
		ft.add(l1);
		ft.add(l2);
		
		FaultSectionPrefData prefData = new FaultSectionPrefData();
		prefData.setFaultTrace(ft);
		prefData.setAveDip(dip);
		prefData.setDipDirection((float)ft.getDipDirection());
		prefData.setAveUpperDepth(topDepth);
		double lowerDepth = topDepth + width*Math.sin(Math.toRadians(dip));
		prefData.setAveLowerDepth(lowerDepth);
		
//		QuadSurface q = new QuadSurface(ft, dip, width);
		QuadSurface q = prefData.getQuadSurface(false);
		q.getDistanceX(distXDebug);
		showDebugGraph(q.x_surfs.get(0), getProjectedPoint(q.trace, q.x_rots, 0, distXDebug), true, null);
		EvenlyGriddedSurface gridded = prefData.getStirlingGriddedSurface(1d, false, false);
		
		// now plot outline
		List<Vector3D> pts = Lists.newArrayList();
//		for (Location loc : q.getPerimeter())
		for (Location loc : q.getEvenlyDiscritizedListOfLocsOnSurface())
//		for (Location loc : q.getEvenlyDiscritizedPerimeter())
//		for (Location loc : gridded)
			pts.add(getProjectedPoint(q.trace, q.rots, 0, loc));
		MinMaxAveTracker zTrack = new MinMaxAveTracker();
		for (Vector3D pt : pts)
			zTrack.addValue(pt.getZ());
		System.out.println("Ztrack: "+zTrack);
		showDebugGraph(q.surfs.get(0), null, true, pts);
		
//		PathIterator pit = q.surfs.get(0).getPathIterator(null);
//		double[] c = new double[6]; // coordinate array
//		double minDistSq = Double.MAX_VALUE;
//		double[] prev_c = null;
//		while (!pit.isDone()) {
//			int ret = pit.currentSegment(c);
//			System.out.println("PIT iter. ret="+ret);
//			System.out.println("C: ["+Joiner.on(",").join(Doubles.asList(c))+"]");
//			Preconditions.checkState(c[2] == 0 && c[3] == 0, "this should fail but isn't yet for unknown reasons");
//			pit.next();
//		}
		
//		showDebugGraph(q.surfs.get(0), null, false);
		double d12 = LocationUtils.horzDistanceFast(l1, l2);
		LocationVector v12 = LocationUtils.vector(l1, l2);
		Location middle12 = LocationUtils.location(l1, v12.getAzimuthRad(), 0.5*d12);
		double dipDirRad = ft.getDipDirection()*TO_RAD;
		Location onDipOffMiddle = LocationUtils.location(middle12, dipDirRad, 0.1*d12);
		System.out.println("Dip dir: "+ft.getDipDirection());
//		q.getDistanceRup(l2);
		q.getDistanceJB(onDipOffMiddle);
		
		
//		testPlanar();
	}
	
	private static void testPlanar() throws IOException {
		Location startLoc = new Location (34, -120);
		
		final int num_calcs = 100000;
		
		double[] test_lengths = { 10d, 50d, 100d, 200d, 500d, 1000d };
		long[] point_counts = new long[test_lengths.length];
		
		ArbitrarilyDiscretizedFunc[] quadFuncs = new ArbitrarilyDiscretizedFunc[5];
		ArbitrarilyDiscretizedFunc[] griddedFuncs = new ArbitrarilyDiscretizedFunc[5];
		
		for (int i=0; i<quadFuncs.length; i++) {
			quadFuncs[i] = new ArbitrarilyDiscretizedFunc();
			griddedFuncs[i] = new ArbitrarilyDiscretizedFunc();
		}
		
		String[] dist_labels = { "Rup", "JB", "Seis", "X", "Combined" };
		
		for (int l=0; l<test_lengths.length; l++) {
			double length = test_lengths[l];
			Location endLoc = LocationUtils.location(startLoc, Math.PI*0.5, length);
			FaultTrace trace = new FaultTrace("trace");
			trace.add(startLoc);
			trace.add(endLoc);
			FaultSectionPrefData prefData = new FaultSectionPrefData();
			prefData.setAveDip(80);
			prefData.setAveLowerDepth(10d);
			prefData.setAveUpperDepth(0d);
			prefData.setAseismicSlipFactor(0d);
			prefData.setFaultTrace(trace);
			RuptureSurface gridded = prefData.getStirlingGriddedSurface(1d, false, false);
			point_counts[l] = ((EvenlyGriddedSurface)gridded).size();
			RuptureSurface quad = prefData.getQuadSurface(false);
			
			// initialize without the timer
			runTest(1, quad);
			runTest(1, gridded);
			System.out.println("Calculating for length "+length +" ("+point_counts[l]+" pts)");
			long[] quad_times = runTest(num_calcs, quad);
			long[] gridded_times = runTest(num_calcs, gridded);
			
			for (int i=0; i<quad_times.length; i++)
				quadFuncs[i].set(length, (double)quad_times[i]/1000d);
			for (int i=0; i<gridded_times.length; i++)
				griddedFuncs[i].set(length, (double)gridded_times[i]/1000d);
		}
		
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		List<PlotSpec> specs = Lists.newArrayList();
		for (int i=0; i<dist_labels.length; i++) {
			List<ArbitrarilyDiscretizedFunc> funcs = Lists.newArrayList();
			funcs.add(quadFuncs[i]);
			funcs.add(griddedFuncs[i]);
			PlotSpec spec = new PlotSpec(funcs, chars, "Distance Calculation Speed", "Fault Length (km)",
					"Time for "+num_calcs+" Distance "+dist_labels[i]+" calcs");
			specs.add(spec);
		}
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.drawGraphPanel(specs, false, false, null, null);
		gp.getChartPanel().setSize(1000, 1500);
		gp.setBackground(Color.WHITE);
		gp.saveAsPNG("/tmp/dist_benchmarks_by_length.png");
		
		specs = Lists.newArrayList();
		for (int i=0; i<dist_labels.length; i++) {
			ArbitrarilyDiscretizedFunc quadFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc griddedFunc = new ArbitrarilyDiscretizedFunc();
			for (int j=0; j<point_counts.length; j++) {
				quadFunc.set((double)point_counts[j], quadFuncs[i].getY(j));
				griddedFunc.set((double)point_counts[j], griddedFuncs[i].getY(j));
			}
			List<ArbitrarilyDiscretizedFunc> funcs = Lists.newArrayList();
			funcs.add(quadFunc);
			funcs.add(griddedFunc);
			PlotSpec spec = new PlotSpec(funcs, chars, "Distance Calculation Speed", "# Gridded Points",
					"Time for "+num_calcs+" Distance "+dist_labels[i]+" calcs");
			specs.add(spec);
		}
		gp = new HeadlessGraphPanel();
		gp.drawGraphPanel(specs, false, false, null, null);
		gp.getChartPanel().setSize(1000, 1500);
		gp.setBackground(Color.WHITE);
		gp.saveAsPNG("/tmp/dist_benchmarks_by_pts.png");
	}
	
	private static long[] runTest(int num_calcs, RuptureSurface surf) {
		long[] ret = new long[5];
		
		// distance rup
		Stopwatch watch = Stopwatch.createStarted();
		for (int i=0; i<num_calcs; i++) {
			Location loc = getTestLoc(true);
			surf.getDistanceRup(loc);
		}
		watch.stop();
		ret[0] = watch.elapsed(TimeUnit.MILLISECONDS);
		
		// distance JB
		watch = Stopwatch.createStarted();
		for (int i=0; i<num_calcs; i++) {
			Location loc = getTestLoc(true);
			surf.getDistanceJB(loc);
		}
		watch.stop();
		ret[1] = watch.elapsed(TimeUnit.MILLISECONDS);
		
		// distance Seis
		watch = Stopwatch.createStarted();
		for (int i=0; i<num_calcs; i++) {
			Location loc = getTestLoc(true);
			surf.getDistanceSeis(loc);
		}
		watch.stop();
		ret[2] = watch.elapsed(TimeUnit.MILLISECONDS);
		
		// distance X
		watch = Stopwatch.createStarted();
		for (int i=0; i<num_calcs; i++) {
			Location loc = getTestLoc(true);
			surf.getDistanceX(loc);
		}
		watch.stop();
		ret[3] = watch.elapsed(TimeUnit.MILLISECONDS);
		
		// combined
		watch = Stopwatch.createStarted();
		for (int i=0; i<num_calcs; i++) {
			Location loc = getTestLoc(true);
			surf.getDistanceRup(loc);
			surf.getDistanceJB(loc);
			surf.getDistanceSeis(loc);
			surf.getDistanceX(loc);
		}
		watch.stop();
		ret[4] = watch.elapsed(TimeUnit.MILLISECONDS);
		
		return ret;
	}
	
	private static void testUCERF3calcs() throws IOException, DocumentException {
		// ok now the real test, FSS
		FaultSystemSolution sol = FaultSystemIO.loadSol(new File(
				new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions"),
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		
		boolean useQuad = false;
		boolean randomizeLoc = true;
		System.gc();
		System.out.println("Loaded, waiting");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {}
		System.out.println("Building surfaces");
		Stopwatch watch = Stopwatch.createStarted();
		List<RuptureSurface> surfs = Lists.newArrayList();
		for (int r=0; r<sol.getRupSet().getNumRuptures(); r++) {
			surfs.add(sol.getRupSet().getSurfaceForRupupture(r, 1d, useQuad));
		}
		watch.stop();
		System.out.println("Done building surfaces: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		System.gc();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}
		watch = Stopwatch.createStarted();
		for (int i=0; i<surfs.size(); i++) {
			surfs.get(i).getDistanceRup(getTestLoc(randomizeLoc));
		}
		watch.stop();
		System.out.println("Distance Rup: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		watch = Stopwatch.createStarted();
		for (int i=0; i<surfs.size(); i++) {
			surfs.get(i).getDistanceJB(getTestLoc(randomizeLoc));
		}
		watch.stop();
		System.out.println("Distance JB: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		watch = Stopwatch.createStarted();
		for (int i=0; i<surfs.size(); i++) {
			surfs.get(i).getDistanceSeis(getTestLoc(randomizeLoc));
		}
		watch.stop();
		System.out.println("Distance Seis: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		watch = Stopwatch.createStarted();
		for (int i=0; i<surfs.size(); i++) {
			surfs.get(i).getDistanceX(getTestLoc(randomizeLoc));
		}
		watch.stop();
		System.out.println("Distance X: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		
		// now do it with the same location, calculating each
		watch = Stopwatch.createStarted();
		for (int i=0; i<surfs.size(); i++) {
			Location testLoc = getTestLoc(randomizeLoc);
			surfs.get(i).getDistanceRup(testLoc);
			surfs.get(i).getDistanceJB(testLoc);
			surfs.get(i).getDistanceSeis(testLoc);
			surfs.get(i).getDistanceX(testLoc);
		}
		watch.stop();
		System.out.println("Distance combined for each rup: "+(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
		
		
//		System.out.println("done, waiting on profiling");
//		System.gc();
//		try {
//			Thread.sleep(50000);
//		} catch (InterruptedException e) {}
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		FaultTrace traceMoved = new FaultTrace(trace.getName());
		for (Location loc : trace)
			traceMoved.add(LocationUtils.location(loc, v));
		return new QuadSurface(traceMoved, dipDeg, width);
	}

	@Override
	public QuadSurface copyShallow() {
		return new QuadSurface(trace, dipDeg, width);
	}

}
