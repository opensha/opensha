package org.opensha.sha.earthquake.rupForecastImpl;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static java.lang.Math.floor;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.sha.util.FocalMech.STRIKE_SLIP;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.SiteAdaptiveSource;
import org.opensha.sha.earthquake.util.GridCellSuperSamplingPoissonPointSourceData;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.faultSurface.utils.PointSurfaceBuilder;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrections;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * SINGLE MAG-DEPTH CUTOFF
 *
 * Updated for use with 2013 maps; NGAW2 reuire more details on width dip etc..
 * (hanging wall effect approximations are not possible at present); we still
 * want o use meanrjb distances but reverse and normal sources should be modeled
 * as both HW and FW; just using +-rJB for rX for now.
 *
 * This is a custom point earthquake source representation used for the NSHMP.
 * It was initially created to provide built in approximations of distance and
 * hanging wall effects as well as to override {@code getMinDistance(Site)} to
 * provide consistency with distances determined during hazard calcs.
 *
 * <p>The class is currently configured to handle 2 rupture top depths; depth #1
 * is applied to M&lt;6.5 and depth #2 to M&ge;6.5. Set both values the same for
 * single depth across all magnitudes.
 *
 * M&ge;6 uses finite source; M&lt;6 uses points NOT USED -- NSHMP IMRs should
 * override
 *
 * NGA notes: rake is used to set fault type dip determines whether hanging wall
 * approximation is used and possibly the weight of the effect
 *
 * Efficiently manages all indexing for subclasses. Subclasses need only
 * implement updateRupture()
 *
 * Point sources should not be reused (e.g. at different locations) and there
 * would be threading issues as the internal rupture reference would be updated
 * asynchronously.
 *
 * Could probably implement a slightly speedier subclass that would ignore
 * mechWeights as the weighting is actually handled in the lookup tables of the
 * GridIMRs that get used during hazard calcs.
 *
 * @author P. Powers
 * @version: $Id$
 */
public class PointSourceNshm extends PoissonPointSource {

	// TODO class will eventually be reconfigured to supply distance metrics
	// at which point M_FINITE_CUT will be used (and set on invocation)

	private static final String NAME = "NSHMP Point Source";
	public static final double M_DEPTH_CUT_DEFAULT = 6.5;
	public static final double DEPTH_BELOW_M_CUT_DEFAULT = 5d;
	public static final double DEPTH_ABOVE_M_CUT_DEFAULT = 1d;
	
	public static final SurfaceBuilder SURF_BUILDER_DEFAULT = new SurfaceBuilder(
			M_DEPTH_CUT_DEFAULT, DEPTH_BELOW_M_CUT_DEFAULT, DEPTH_ABOVE_M_CUT_DEFAULT);

	/** Minimum magnitude for finite fault representation. */
	// public static final double M_FINITE_CUT = 6.0;

	private static final MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();
	
	public PointSourceNshm(Location loc,
			IncrementalMagFreqDist mfd,
			double duration,
			Map<FocalMech, Double> mechWtMap,
			PointSourceDistanceCorrection distCorr,
			double minMagForDistCorr) {
		this(loc, mfd, duration, mechWtMap, SURF_BUILDER_DEFAULT, distCorr, minMagForDistCorr, null);
	}
	
	public PointSourceNshm(Location loc,
			IncrementalMagFreqDist mfd,
			double duration,
			Map<FocalMech, Double> mechWtMap,
			PointSourceDistanceCorrection distCorr,
			double minMagForDistCorr,
			GridCellSupersamplingSettings supersamplingSettings) {
		this(loc, mfd, duration, mechWtMap, SURF_BUILDER_DEFAULT, distCorr, minMagForDistCorr, supersamplingSettings);
	}
	
	public PointSourceNshm(Location loc,
			IncrementalMagFreqDist mfd,
			double duration,
			Map<FocalMech, Double> mechWtMap,
			double magCut,
			double depthBelowMagCut,
			double depthAboveMagCut,
			PointSourceDistanceCorrection distCorr,
			double minMagForDistCorr) {
		this(loc, mfd, duration, mechWtMap, new SurfaceBuilder(magCut, depthBelowMagCut, depthAboveMagCut), distCorr, minMagForDistCorr, null);
	}
	
	private PointSourceNshm(Location loc,
			IncrementalMagFreqDist mfd,
			double duration, 
			Map<FocalMech, Double> mechWtMap,
			SurfaceBuilder surfaceBuilder,
			PointSourceDistanceCorrection distCorr,
			double minMagForDistCorr,
			GridCellSupersamplingSettings supersamplingSettings) {
		super(loc, TectonicRegionType.ACTIVE_SHALLOW, duration,
				buildData(loc, mfd, mechWtMap, surfaceBuilder, supersamplingSettings), distCorr, minMagForDistCorr);
		this.name = NAME;
	}
	
	private static PoissonPointSourceData buildData(Location loc, IncrementalMagFreqDist mfd,
			Map<FocalMech, Double> mechWtMap, SurfaceBuilder surfaceBuilder,
			GridCellSupersamplingSettings supersamplingSettings) {
		PoissonPointSourceData data = PointSource.dataForMFDs(loc, mfd, weightsMap(mechWtMap), surfaceBuilder);
		if (supersamplingSettings != null) {
			// assume 0.1 degree gridding (but verify)
			Preconditions.checkState(multipleOf0p1(loc.lat) && multipleOf0p1(loc.lon),
					"Assuming gridding of 0.1 degrees for supersampling, but not all locations are on a 0.1 degree grid: %s", loc);
			Region cell = new Region(new Location(loc.lat-0.05, loc.lon-0.05), new Location(loc.lat+0.05, loc.lon+0.05));
			data = new GridCellSuperSamplingPoissonPointSourceData(data, loc, cell, supersamplingSettings);
		}
		return data;
	}
	
	private static boolean multipleOf0p1(double number) {
		double factor = number / 0.1;
		double roundedFactor = Math.round(factor);
		double reconstructed = roundedFactor * 0.1;
		return Math.abs(number - reconstructed) < 0.001;
	}
	
	private static Map<FocalMechanism, Double> weightsMap(Map<FocalMech, Double> map) {
		Map<FocalMechanism, Double> ret = new HashMap<>(map.size());
		for (FocalMech mech : map.keySet())
			ret.put(mech.mechanism, map.get(mech));
		return ret;
	}
	
	public static class SurfaceBuilder implements FocalMechRuptureSurfaceBuilder {

		private double depthBelowMagCut;
		private double depthAboveMagCut;
		private double magCut;

		private SurfaceBuilder(double magCut, double depthBelowMagCut, double depthAboveMagCut) {
			super();
			this.magCut = magCut;
			this.depthBelowMagCut = depthBelowMagCut;
			this.depthAboveMagCut = depthAboveMagCut;
		}

		@Override
		public int getNumSurfaces(double magnitude, FocalMech mech) {
			// this used to return 2, but splitting out FW/HW is now done in the distance correction
			return 1;
		}

		@Override
		public RuptureSurface getSurface(Location loc, double magnitude, FocalMech mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex < 2);

			double zTop = depthForMag(magnitude);
			double dipRad = mech.dip() * TO_RAD;
			double widthDD = calcWidth(magnitude, zTop, dipRad);
			
			PointSurfaceBuilder builder = new PointSurfaceBuilder(loc);
			builder.upperDepthWidthAndDip(zTop, widthDD, mech.dip());
			builder.magnitude(magnitude);
			builder.length(calcLength(magnitude));
			
			return builder.buildPointSurface();
		}

		@Override
		public double getSurfaceWeight(double magnitude, FocalMech mech, int surfaceIndex) {
			// splitting out FW/HW is now done in the distance correction
			return 1d;
		}

		@Override
		public boolean isSurfaceFinite(double magnitude, FocalMech mech, int surfaceIndex) {
			// always point source
			return false;
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
			double depth = 0.5*(rupSurface.getAveRupTopDepth() + rupSurface.getAveRupBottomDepth());
			return new Location(sourceLoc.lat, sourceLoc.lon, depth);
		}

		/**
		 * Returns the rupture depth to use for the supplied magnitude.
		 * @param mag of interest
		 * @return the associated depth of rupture
		 */
		public double depthForMag(double mag) {
			return (mag >= magCut) ? depthAboveMagCut : depthBelowMagCut;
		}
		
		public double calcLength(double mag) {
			return WC94.getMedianLength(mag);
		}

		/**
		 * Returns the minimum of the aspect ratio width (based on WC94) length and
		 * the allowable down-dip width.
		 *
		 * @param mag
		 * @param depth
		 * @param dipRad (in radians)
		 * @return
		 */
		public double calcWidth(double mag, double depth, double dipRad) {
			double length = calcLength(mag);
			double aspectWidth = length / 1.5;
			double ddWidth = (14.0 - depth) / sin(dipRad);
			return min(aspectWidth, ddWidth);
		}
		
	}

	private static final Splitter SPLITTER = Splitter.on(" ").omitEmptyStrings().trimResults();

	private static final String MAG_ID = "#Mag";
	private static final String COMMENT_ID = "#";
	private static final double RJB_M_MIN = 6.05;
	private static final double RJB_M_CUTOFF = 6.0;
	private static final double RJB_M_DELTA = 0.1;
	private static final int RJB_M_SIZE = 26;
	private static final int RJB_M_MAX_INDEX = RJB_M_SIZE - 1;
	private static final int RJB_R_SIZE = 1001;
	private static final int RJB_R_MAX_INDEX = RJB_R_SIZE - 1;
	private static final String RJB_DAT_DIR = "data/nshmp/";
	private static final double[][] RJB_WC94LENGTH = readRjb("rjb_wc94length.dat");

	/* package visibility for testing */
	static double[][] readRjb(String resource) {
		double[][] rjbs = new double[RJB_M_SIZE][RJB_R_SIZE];
		URL url = getResource(RJB_DAT_DIR + resource);
		List<String> lines = null;
		try {
			lines = readLines(url, UTF_8);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
		int magIndex = -1;
		int rIndex = 0;
		for (String line : lines) {
			if (line.trim().isEmpty()) {
				continue;
			}
			if (line.startsWith(MAG_ID)) {
				magIndex++;
				rIndex = 0;
				continue;
			}
			if (line.startsWith(COMMENT_ID)) {
				continue;
			}
			rjbs[magIndex][rIndex++] = readDouble(line, 1);
		}
		return rjbs;
	}

	private static double readDouble(String s, int position) {
		return Double.valueOf(Iterables.get(SPLITTER.split(s), position));
	}

	/**
	 * The rjb lookup tables span the magnitude range [6.05..8.55] and distance
	 * range [0..1000] km. For M<6 and distances > 1000, lookups return the
	 * supplied distance. For M>8.6, lookups return the corrected distance for
	 * M=8.55. NOTE that no NaN or ±INFINITY checking is done in this class. This
	 * would have to be added for a public api, but we are operating on the
	 * assumption that data from mfds and upstream distance calculations and
	 * dimensioning will have already been checked for odd values.
	 */
	public static class DistanceCorrection2013 implements PointSourceDistanceCorrection {

		public double getCorrectedDistanceJB(Location siteLoc, double mag, PointSurface surf, double horzDist) {
			if (mag < RJB_M_CUTOFF) {
				return horzDist;
			}
			int mIndex = min((int) round((mag - RJB_M_MIN) / RJB_M_DELTA), RJB_M_MAX_INDEX);
			int rIndex = min(RJB_R_MAX_INDEX, (int) floor(horzDist));
			return RJB_WC94LENGTH[mIndex][rIndex];
		}
		
		public double getCorrectedDistanceRup(double rJB, double zTop, double zBot, double dipRad, double horzWidth, boolean footwall) {
			// this is the (buggy) distance correction used by the USGS NSHM in 2013 and at least through 2023; it is labeled
			// 2013 because it was implemented in OpenSHA for the 2013 update.
			// It can return unphysical values, e.g., where rRup < zTop. See https://github.com/opensha/opensha/issues/124
			if (footwall) return hypot2(rJB, zTop);

			double rCut = zBot * Math.tan(dipRad);

			if (rJB > rCut) return hypot2(rJB, zBot);

			// rRup when rJB is 0 -- we take the minimum of the site-to-top-edge
			// and site-to-normal of rupture for the site being directly over
			// the down-dip edge of the rupture
			double rRup0 = Math.min(hypot2(horzWidth, zTop), zBot * Math.cos(dipRad));
			// rRup at cutoff rJB
			double rRupC = zBot / Math.cos(dipRad);
			// scale linearly with rJB distance
			return (rRupC - rRup0) * rJB / rCut + rRup0;
		}
		
		@Override
		public String toString() {
			return PointSourceDistanceCorrections.NSHM_2013.getName();
		}

		@Override
		public WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc, PointSurface surf,
				TectonicRegionType trt, double mag, double horzDist) {
			if (trt == TectonicRegionType.SUBDUCTION_SLAB) {
				// NSHM13 always treats slab ruptures as true point sources
				double corrJB = horzDist;
				double zTop = surf.getAveRupTopDepth();
				double rRup = hypot2(corrJB, zTop);
				double rSeis = zTop > GriddedSurfaceUtils.SEIS_DEPTH ? rRup : hypot2(corrJB, GriddedSurfaceUtils.SEIS_DEPTH);
				double rX = -corrJB;
				return WeightedList.evenlyWeighted(
						new SurfaceDistances.Precomputed(siteLoc, rRup, corrJB, rSeis, rX));
			}
			// note: nshmp-haz still uses all of this logic for M<6, just with corrJB == horzDist, which will happen
			// in getCorrectedDistanceJB
			double corrJB = getCorrectedDistanceJB(siteLoc, mag, surf, horzDist);
			
			double dip = surf.getAveDip();
			
			double zTop = surf.getAveRupTopDepth();
			
			if (Precision.equals(90d, dip, 0.1)) {
				// vertical, simple case
				
				double rRup = getCorrectedDistanceRup(corrJB, zTop, Double.NaN, PI_HALF, Double.NaN, true);
				
				double rSeis;
				if (zTop > GriddedSurfaceUtils.SEIS_DEPTH)
					rSeis = rRup;
				else
					rSeis = getCorrectedDistanceRup(corrJB, GriddedSurfaceUtils.SEIS_DEPTH, Double.NaN, PI_HALF, Double.NaN, true);
				
				double rX = -corrJB; // footwall is set to 'true' for SS ruptures in order to short-circuit GMPE calcs
				
//				System.out.println("CORR13 vertical for rEpi="+horzDist+"; rJB="+corrJB
//						+"; rRup="+rRup+", rX="+rX);
				
				return WeightedList.evenlyWeighted(
						new SurfaceDistances.Precomputed(siteLoc, rRup, corrJB, rSeis, rX));
			} else {
				// dipping, return one on and one off the footwall
				double zBot = surf.getAveRupBottomDepth();
				double horzWidth = surf.getAveHorizontalWidth();
				
				double dipRad = Math.toRadians(dip);
				
				double rRupFW = getCorrectedDistanceRup(corrJB, zTop, zBot, dipRad, horzWidth, true);
				double rRupHW = getCorrectedDistanceRup(corrJB, zTop, zBot, dipRad, horzWidth, false);
				
				double rSeisFW, rSeisHW;
				if (zTop > GriddedSurfaceUtils.SEIS_DEPTH) {
					rSeisFW = rRupFW;
					rSeisHW = rRupHW;
				} else {
					rSeisFW = getCorrectedDistanceRup(corrJB, GriddedSurfaceUtils.SEIS_DEPTH, zBot, dipRad, horzWidth, true);
					rSeisHW = getCorrectedDistanceRup(corrJB, GriddedSurfaceUtils.SEIS_DEPTH, zBot, dipRad, horzWidth, false);
				}
				
				double rX_FW = -corrJB;
				double rX_HW = corrJB + horzWidth;
				
//				System.out.println("CORR13 dipping for rEpi="+horzDist+"; rJB="+corrJB
//						+"; footwall rRup="+rRupFW+", rX="+rX_FW+"; hanging wall rRup="+rRupHW+", rX="+rX_HW);
				
				// evenly-weight them. this is valid if rJB is large but a bad approximation close in, but it's what is
				// (hopefully to become 'was') done in the NSHM
				return WeightedList.evenlyWeighted(
						new SurfaceDistances.Precomputed(siteLoc, rRupFW, corrJB, rSeisFW, rX_FW),
						new SurfaceDistances.Precomputed(siteLoc, rRupHW, corrJB, rSeisHW, rX_HW));
			}
		}
		
	}
	
	private static final double PI_HALF = Math.PI/2d; // 90 degrees
	
	/**
	 * Same as {@code Math.hypot()} without regard to under/over flow.
	 */
	private static final double hypot2(double v1, double v2) {
		return Math.sqrt(v1 * v1 + v2 * v2);
	}

}
