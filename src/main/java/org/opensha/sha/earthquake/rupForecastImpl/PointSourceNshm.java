package org.opensha.sha.earthquake.rupForecastImpl;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.nshmp2.util.NSHMP_Utils.rateToProb;
import static org.opensha.sha.util.FocalMech.NORMAL;
import static org.opensha.sha.util.FocalMech.REVERSE;
import static org.opensha.sha.util.FocalMech.STRIKE_SLIP;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.PointSource13b.PointSurface13b;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;

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
public class PointSourceNshm extends ProbEqkSource {

  // TODO class will eventually be reconfigured to supply distance metrics
  // at which point M_FINITE_CUT will be used (and set on invocation)

  private static final String NAME = "NSHMP Point Source";
  private static final double M_DEPTH_CUT = 6.5;
  private static final double[] DEPTHS = new double[] { 5.0, 1.0 };

  /** Minimum magnitude for finite fault representation. */
  // public static final double M_FINITE_CUT = 6.0;

  private static final MagLengthRelationship WC94 =
      new WC1994_MagLengthRelationship();

  private Location loc;
  private IncrementalMagFreqDist mfd;
  private double duration;
  private double lgMagDepth;
  private double smMagDepth;
  private Map<FocalMech, Double> mechWts;

  private int mechCount; // mechs with weight 1-3;
  private int ssIdx, revIdx; // normal not needed
  private int fwIdxLo, fwIdxHi;

  // Rupture indexing: no array index out of bounds are checked, it is assumed
  // that users will only request values in the range getNumRuptures()-1
  // Focal mech is determined using the max indices for each type of mech
  // determined using the Math.ceil(wt) [scales to 1] * num_M

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
      double duration, Map<FocalMech, Double> mechWtMap) {

    name = NAME; // super
    this.loc = loc;
    this.mfd = mfd;
    this.duration = duration;
    smMagDepth = DEPTHS[0];
    lgMagDepth = DEPTHS[1];
    this.mechWts = mechWtMap;

    // rupture indexing
    mechCount = countMechs(mechWtMap);
    setIndices();

  }

  @Override
  public ProbEqkRupture getRupture(int idx) {
    if (idx > getNumRuptures() - 1 || idx < 0)
      throw new RuntimeException("index out of bounds");
    ProbEqkRupture probEqkRupture = new ProbEqkRupture();
    PointSurfaceNshm surface = new PointSurfaceNshm(loc); // mutable, possibly
    // depth varying

    FocalMech mech = mechForIndex(idx);
    double wt = mechWts.get(mech);
    if (mech != STRIKE_SLIP) wt *= 0.5;
    int magIdx = idx % mfd.size();
    double mag = mfd.getX(magIdx);
    double zTop = depthForMag(mag);
    double dipRad = mech.dip() * TO_RAD;
    double widthDD = calcWidth(mag, zTop, dipRad);
    double zHyp = zTop + sin(dipRad) * widthDD / 2.0;

    surface.setAveDip(mech.dip()); // technically not needed
    surface.widthDD = widthDD;
    surface.widthH = widthDD * cos(dipRad);
    surface.zTop = zTop;
    surface.zBot = zTop + widthDD * sin(dipRad);
    surface.footwall = isOnFootwall(idx);
    surface.mag = mag; // KLUDGY needed for distance correction

    probEqkRupture.setPointSurface(surface);
    probEqkRupture.setMag(mag);
    probEqkRupture.setAveRake(mech.rake());
    double rate = wt * mfd.getY(magIdx);
    probEqkRupture.setProbability(rateToProb(rate, duration));
    probEqkRupture.setHypocenterLocation(new Location(loc.getLatitude(),
        loc.getLongitude(), zHyp));

    return probEqkRupture;
  }

  /*
   * Overriden due to uncertainty on how getRuptureList() is constructed in
   * parent. Looks clunky and uses cloning which can be error prone if
   * implemented incorrectly. Was building custom NSHMP calculator using
   * enhanced for-loops and was losing class information when iterating over
   * sources and ruptures.
   */
  @Override
  public List<ProbEqkRupture> getRuptureList() {
    throw new UnsupportedOperationException(
        "A PointSource does not allow access to the list " + "of all possible ruptures.");
  }

  @Override
  public Iterator<ProbEqkRupture> iterator() {
    // @formatter:off
		return new Iterator<ProbEqkRupture>() {
			int size = getNumRuptures();
			int caret = 0;
			@Override public boolean hasNext() {
				return caret < size;
			}
			@Override public ProbEqkRupture next() {
				return getRupture(caret++);
			}
			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		// @formatter:on
  }

  @Override
  public LocationList getAllSourceLocs() {
    LocationList locList = new LocationList();
    locList.add(loc);
    return locList;
  }

  @Override
  public RuptureSurface getSourceSurface() {
    return new PointSurface13b(loc);
  }

  @Override
  public int getNumRuptures() {
    return mfd.size() * mechCount;
  }

  @Override
  public double getMinDistance(Site site) {
    return LocationUtils.horzDistanceFast(site.getLocation(), loc);
  }

  /**
   * Returns ths <code>Location</code> of this source.
   * @return the source <code>Location</code>
   */
  public Location getLocation() {
    return loc;
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
  private double calcWidth(double mag, double depth, double dipRad) {
    double length = WC94.getMedianLength(mag);
    double aspectWidth = length / 1.5;
    double ddWidth = (14.0 - depth) / sin(dipRad);
    return min(aspectWidth, ddWidth);
  }

  /**
   * Returns the focal mechanism of the rupture at the supplied index.
   * @param idx of the rupture of interest
   * @return the associated focal mechanism
   */
  private FocalMech mechForIndex(int idx) {
    // iteration order is always SS -> REV -> NOR
    return (idx < ssIdx) ? STRIKE_SLIP : (idx < revIdx) ? REVERSE : NORMAL;
  }

  /**
   * Returns whether the rupture at index should be on the footwall (i.e. have
   * its rX value set negative). Strike-slip mechs are marked as footwall to
   * potentially short circuit GMPE calcs. Because the index order is SS-FW
   * RV-FW RV-HW NR-FW NR-HW
   */
  private boolean isOnFootwall(int idx) {
    return (idx < fwIdxLo) ? true : (idx < revIdx) ? false : (idx < fwIdxHi) ? true : false;
  }

  /**
   * Returns the rupture depth to use for the supplied magnitude.
   * @param mag of interest
   * @return the associated depth of rupture
   */
  private double depthForMag(double mag) {
    return (mag >= M_DEPTH_CUT) ? lgMagDepth : smMagDepth;
  }

  /**
   * This is misnamed; we're double counting reverse and normal mechs because
   * they will have hanging wall and footwall representations.
   */
  private static int countMechs(Map<FocalMech, Double> map) {
    int count = 0;
    for (FocalMech mech : map.keySet()) {
      double wt = map.get(mech);
      if (wt == 0.0) continue;
      count += (mech == STRIKE_SLIP) ? 1 : 2;
    }
    return count;
  }

  private void setIndices() {
    int nMag = mfd.size();
    int ssCount = (int) ceil(mechWts.get(STRIKE_SLIP)) * nMag;
    int revCount = (int) ceil(mechWts.get(REVERSE)) * nMag * 2;
    int norCount = (int) ceil(mechWts.get(NORMAL)) * nMag * 2;
    ssIdx = ssCount;
    revIdx = ssCount + revCount;
    fwIdxLo = ssCount + revCount / 2;
    fwIdxHi = ssCount + revCount + norCount / 2;
  }

  /*
   * Overrides using point location for depth information
   */
  public static class PointSurfaceNshm extends PointSurface {

    private double widthH; // horizontal width (surface projection)
    private double widthDD; // down-dip width
    private double zTop;
    private double zBot; // base of rupture; may be less than 14km

    private double mag;

    private boolean footwall;

    public PointSurfaceNshm(Location loc) {
      super(loc);
    }

    @Override
    public double getAveRupTopDepth() {
      return getDepth();
    }

    @Override
    public double getDepth() {
      // overridden to not key depth to point location
      return zTop;
    }

    @Override
    public void setDepth(double depth) {
      // overridden to not cause creation of new Location in parent
      zTop = depth;
    }

    @Override
    public double getAveWidth() {
      return widthDD;
    }

    @Override
    public double getDistanceJB(Location loc) {
      /*
       * In debugging point sources, found that updated PointSourceNshm had
       * synchronization issues with respect to setting rupture properties on a
       * single shared instance of the corrsponding surface. Reverted to
       * PointSource13b-like implementation that creates a new surface for each
       * rupture when iterating.
       *
       * Also found that an update to the point source distance correction
       * algorithm (by Steve Harmsen) never made it's way into OpenSHA. This
       * class now loads and uses the updated rjb correction file instead of
       * delegating to the parent class as before. This could be refactored as a
       * new PtSrcDistanceCorr type.
       */
      double djb = LocationUtils.horzDistanceFast(getLocation(), loc);
      return correctedRjb(mag, djb);
    }

    @Override
    public double getDistanceX(Location loc) {
      double rJB = getDistanceJB(loc);
      return footwall ? -rJB : rJB + widthH;
    }

    @Override
    public double getDistanceRup(Location loc) {
      double rJB = getDistanceJB(loc);

      return getDistanceRup(rJB);
    }
    
    public double getDistanceRup(double rJB) {
        if (footwall) return hypot2(rJB, zTop);

        double dipRad = aveDip * TO_RAD;
        double rCut = zBot * tan(dipRad);

        if (rJB > rCut) return hypot2(rJB, zBot);

        // rRup when rJB is 0 -- we take the minimum the site-to-top-edge
        // and site-to-normal of rupture for the site being directly over
        // the down-dip edge of the rupture
        double rRup0 = min(hypot2(widthH, zTop), zBot * cos(dipRad));
        // rRup at cutoff rJB
        double rRupC = zBot / cos(dipRad);
        // scale linearly with rJB distance
        return (rRupC - rRup0) * rJB / rCut + rRup0;
    }

    public boolean isOnFootwall() {
      return footwall;
    }

    /**
     * Same as {@code Math.hypot()} without regard to under/over flow.
     */
    private static final double hypot2(double v1, double v2) {
      return sqrt(v1 * v1 + v2 * v2);
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

  /*
   * The rjb lookup tables span the magnitude range [6.05..8.55] and distance
   * range [0..1000] km. For M<6 and distances > 1000, lookups return the
   * supplied distance. For M>8.6, lookups return the corrected distance for
   * M=8.55. NOTE that no NaN or ±INFINITY checking is done in this class. This
   * would have to be added for a public api, but we are operating on the
   * assumption that data from mfds and upstream distance calulations and
   * dimensioning will have already been checked for odd values.
   */

  public static double correctedRjb(double m, double r) {
    if (m < RJB_M_CUTOFF) {
      return r;
    }
    int mIndex = min((int) round((m - RJB_M_MIN) / RJB_M_DELTA), RJB_M_MAX_INDEX);
    int rIndex = min(RJB_R_MAX_INDEX, (int) floor(r));
    return RJB_WC94LENGTH[mIndex][rIndex];
  }

}
