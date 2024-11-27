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

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.util.GridCellSuperSamplingPoissonPointSourceData;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.faultSurface.utils.PointSurfaceBuilder;
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
	
	private static final SurfaceBuilder SURF_BUILDER_DEFAULT = new SurfaceBuilder(
			M_DEPTH_CUT_DEFAULT, DEPTH_BELOW_M_CUT_DEFAULT, DEPTH_ABOVE_M_CUT_DEFAULT);

	/** Minimum magnitude for finite fault representation. */
	// public static final double M_FINITE_CUT = 6.0;

	private static final MagLengthRelationship WC94 =
			new WC1994_MagLengthRelationship();

	/**
	 * Constructs a new point earthquake source.
	 *
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param duration of the parent forecast
	 * @param depths 2 element array of rupture top depths; <code>depths[0]</code>
	 *        used for M&lt;6.5, <code>depths[1]</code> used for M&ge;6.5
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	public PointSourceNshm(Location loc, IncrementalMagFreqDist mfd,
			double duration, Map<FocalMech, Double> mechWtMap, PointSourceDistanceCorrections distCorrType) {
		this(loc, mfd, duration, mechWtMap, distCorrType == null ? null : distCorrType.get());
	}
	
	public PointSourceNshm(Location loc, IncrementalMagFreqDist mfd,
			double duration, Map<FocalMech, Double> mechWtMap, WeightedList<PointSourceDistanceCorrection> distCorrs) {
		this(loc, mfd, duration, mechWtMap, SURF_BUILDER_DEFAULT, distCorrs);
	}
	
	public PointSourceNshm(Location loc, IncrementalMagFreqDist mfd,
			double duration, Map<FocalMech, Double> mechWtMap,
			double magCut, double depthBelowMagCut, double depthAboveMagCut,
			WeightedList<PointSourceDistanceCorrection> distCorrs) {
		this(loc, mfd, duration, mechWtMap, new SurfaceBuilder(magCut, depthBelowMagCut, depthAboveMagCut), distCorrs);
	}
	
	private PointSourceNshm(Location loc, IncrementalMagFreqDist mfd,
			double duration, Map<FocalMech, Double> mechWtMap,
			SurfaceBuilder surfaceBuilder, WeightedList<PointSourceDistanceCorrection> distCorrs) {
		super(loc, TectonicRegionType.ACTIVE_SHALLOW, duration, 
				PointSource.dataForMFDs(loc, mfd, weightsMap(mechWtMap), surfaceBuilder), distCorrs);
		this.name = NAME;
	}
	
	// TODO: Add supersampling support
//	private static PoissonPointSourceData buildData(Location loc, IncrementalMagFreqDist mfd,
//			Map<FocalMech, Double> mechWtMap, SurfaceBuilder surfaceBuilder,
//			GridCellSupersamplingSettings supersamplingSettings) {
//		PoissonPointSourceData data = PointSource.dataForMFDs(loc, mfd, weightsMap(mechWtMap), surfaceBuilder);
//		if (supersamplingSettings != null)
//			data = new GridCellSuperSamplingPoissonPointSourceData(data, loc, cell, supersamplingSettings);
//		return data;
//	}
	
	private static Map<FocalMechanism, Double> weightsMap(Map<FocalMech, Double> map) {
		Map<FocalMechanism, Double> ret = new HashMap<>(map.size());
		for (FocalMech mech : map.keySet())
			ret.put(mech.mechanism, map.get(mech));
		return ret;
	}
	
	private static class SurfaceBuilder implements FocalMechRuptureSurfaceBuilder {

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
			// 1 surface for SS, 2 for dipping (1 for each HW setting)
			return mech == STRIKE_SLIP ? 1 : 2;
		}

		@Override
		public RuptureSurface getSurface(Location loc, double magnitude, FocalMech mech, int surfaceIndex) {
			Preconditions.checkState(surfaceIndex < 2);

			double zTop = depthForMag(magnitude);
			double dipRad = mech.dip() * TO_RAD;
			double widthDD = calcWidth(magnitude, zTop, dipRad);
			
			PointSurfaceBuilder builder = new PointSurfaceBuilder(loc);
			builder.upperDepthWidthAndDip(zTop, widthDD, mech.dip());
			builder.footwall(surfaceIndex == 0); // always true for SS, true for one rup for N & R
			builder.magnitude(magnitude);
			
			return builder.buildFiniteApproxPointSurface();
		}

		@Override
		public double getSurfaceWeight(double magnitude, FocalMech mech, int surfaceIndex) {
			// 1 surface for SS, 2 for dipping (1 for each HW setting)
			return mech == STRIKE_SLIP ? 1d : 0.5;
		}

		@Override
		public boolean isSurfaceFinite(double magnitude, FocalMech mech, int surfaceIndex) {
			// always point source
			return false;
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface) {
			Preconditions.checkState(rupSurface instanceof FiniteApproxPointSurface);
			double depth = 0.5*(rupSurface.getAveRupTopDepth() + ((FiniteApproxPointSurface)rupSurface).getLowerDepth());
			return new Location(sourceLoc.lat, sourceLoc.lon, depth);
		}

		/**
		 * Returns the rupture depth to use for the supplied magnitude.
		 * @param mag of interest
		 * @return the associated depth of rupture
		 */
		private double depthForMag(double mag) {
			return (mag >= magCut) ? depthAboveMagCut : depthBelowMagCut;
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
		private static double calcWidth(double mag, double depth, double dipRad) {
			double length = WC94.getMedianLength(mag);
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
	 * assumption that data from mfds and upstream distance calulations and
	 * dimensioning will have already been checked for odd values.
	 */
	public static class DistanceCorrection2013 implements PointSourceDistanceCorrection {

		@Override
		public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
			if (mag < RJB_M_CUTOFF) {
				return horzDist;
			}
			int mIndex = min((int) round((mag - RJB_M_MIN) / RJB_M_DELTA), RJB_M_MAX_INDEX);
			int rIndex = min(RJB_R_MAX_INDEX, (int) floor(horzDist));
			return RJB_WC94LENGTH[mIndex][rIndex];
		}
		
	}

}
