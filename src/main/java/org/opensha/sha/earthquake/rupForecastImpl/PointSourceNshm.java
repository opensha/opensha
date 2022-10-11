package org.opensha.sha.earthquake.rupForecastImpl;

import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static org.opensha.nshmp2.util.NSHMP_Utils.rateToProb;
import static org.opensha.sha.util.FocalMech.NORMAL;
import static org.opensha.sha.util.FocalMech.REVERSE;
import static org.opensha.sha.util.FocalMech.STRIKE_SLIP;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PtSrcDistCorr;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SingleMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.NSHMP_Util;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * Updated from PointSource13b for 2014/2018 NSHM.
 *
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

  private static final String NAME = "NSHMP Point Source";

  private static final MagLengthRelationship WC94 =
      new WC1994_MagLengthRelationship();

  private Location loc;
  private IncrementalMagFreqDist mfd;
  private double duration;
  private Map<FocalMech, Double> mechWtMap;

  private int rupCount;
  private int magDepthSize;

  // private int mechCount; // mechs with weight 1-3;
  private int ssIdx, revIdx; // normal not needed
  private int fwIdxLo, fwIdxHi;

  private DepthModel depthModel;

  private ProbEqkRupture rupture;
  private PointSurfaceNshm surface;

  private static final double MAX_DEPTH = 14.0;
  private static final NavigableMap<Double, Map<Double, Double>> MAG_DEPTH_MAP;

  static {
    MAG_DEPTH_MAP = new TreeMap<>();
    MAG_DEPTH_MAP.put(10.0, Map.of(1.0, 1.0));
    MAG_DEPTH_MAP.put(6.5, Map.of(5.0, 1.0));
  }

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
    this.mechWtMap = mechWtMap;
    init();
  }

  private void init() {

    rupture = new ProbEqkRupture();
    surface = new PointSurfaceNshm(loc);
    rupture.setPointSurface(surface);

    depthModel = DepthModel.create(
        MAG_DEPTH_MAP,
        mfd.xValues(),
        MAX_DEPTH);

    /*
     * Get the number of mag-depth iterations required to get to mMax. See
     * explanation in GridRuptureSet for how magDepthIndices is set up
     */
    magDepthSize = depthModel.magDepthIndices.lastIndexOf(mfd.size() - 1) + 1;

    /*
     * Init rupture indexing: SS-FW RV-FW RV-HW NR-FW NR-HW. Each category will
     * have ruptures for every mag in 'mfd' and depth in parent 'magDepthMap'.
     */
    int ssCount = (int) ceil(mechWtMap.get(STRIKE_SLIP)) * magDepthSize;
    int revCount = (int) ceil(mechWtMap.get(REVERSE)) * magDepthSize * 2;
    int norCount = (int) ceil(mechWtMap.get(NORMAL)) * magDepthSize * 2;
    ssIdx = ssCount;
    revIdx = ssCount + revCount;
    fwIdxLo = ssCount + revCount / 2;
    fwIdxHi = ssCount + revCount + norCount / 2;

    rupCount = ssCount + revCount + norCount;
  }

  @Override
  public ProbEqkRupture getRupture(int idx) {
    if (idx > getNumRuptures() - 1 || idx < 0)
      throw new RuntimeException("index out of bounds");
    updateRupture(idx);
    return rupture;
  }

  private void updateRupture(int index) {

    int magDepthIndex = index % magDepthSize;
    int magIndex = depthModel.magDepthIndices.get(magDepthIndex);
    double mag = mfd.getX(magIndex);
    double rate = mfd.getY(magIndex);

    double zTor = depthModel.magDepthDepths.get(magDepthIndex);
    double zTorWt = depthModel.magDepthWeights.get(magDepthIndex);

    FocalMech mech = mechForIndex(index);
    double mechWt = mechWtMap.get(mech);
    if (mech != STRIKE_SLIP) {
      mechWt *= 0.5;
    }
    double dipRad = mech.dip() * TO_RAD;

    double maxWidthDD = (depthModel.maxDepth - zTor) / sin(dipRad);

    double widthDD = min(maxWidthDD, lengthWc94(mag) / 1.5);

    rupture.setMag(mag);
    rupture.setAveRake(mech.rake());
    double wtRate = rate * zTorWt * mechWt;
    rupture.setProbability(rateToProb(wtRate, duration));

    PointSurfaceNshm fpSurf = (PointSurfaceNshm) rupture.getRuptureSurface();
    fpSurf.mag = mag; // KLUDGY needed for distance correction
    fpSurf.dipRad = dipRad;
    fpSurf.widthDD = widthDD;
    fpSurf.widthH = widthDD * cos(dipRad);
    fpSurf.zTor = zTor;
    fpSurf.zBot = zTor + widthDD * sin(dipRad);
    fpSurf.footwall = isOnFootwall(index);
  }

  private static double lengthWc94(double mag) {
    return pow(10.0, -3.22 + 0.69 * mag);
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
    return new PointSurfaceNshm(loc);
  }

  @Override
  public int getNumRuptures() {
    return rupCount; // mfd.size() * mechCount;
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
   * Overrides using point location for depth information
   */
  public static class PointSurfaceNshm extends PointSurface {

    private double widthH; // horizontal width (surface projection)
    private double widthDD; // down-dip width
    private double zTor;
    private double zBot; // base of rupture; may be less than 14km

    private boolean footwall;
    private double mag;
    private double dipRad;

    // distance metrics for reference site; this should
    // work for single threaded calculations
    private Location siteLoc;
    private Distance distance;

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
      return zTor;
    }

    @Override
    public void setDepth(double depth) {
      // overridden to not cause creation of new Location in parent
      zTor = depth;
    }

    @Override
    public double getAveWidth() {
      return widthDD;
    }

    @Override
    public synchronized double getDistanceRup(Location location) {
      if (location != this.siteLoc) {
        setDistances(location);
      }
      return distance.rRup;
    }

    @Override
    public synchronized double getDistanceJB(Location location) {
      if (location != this.siteLoc) {
        setDistances(location);
      }
      return distance.rJB;
    }

    @Override
    public synchronized double getDistanceX(Location location) {
      if (location != this.siteLoc) {
        setDistances(location);
      }
      return distance.rX;
    }

    private void setDistances(Location location) {
      this.distance = distanceTo(location);
      this.siteLoc = location;
    }

    private Distance distanceTo(Location loc) {
      double rJB = LocationUtils.horzDistanceFast(getLocation(), loc);
      double corr = PtSrcDistCorr.getCorrection(rJB, mag, PtSrcDistCorr.Type.NSHMP08);
      rJB *= corr;

      // because we're not using table lookup optimizations, we push the
      // minimum rJB out to 0.5 (half the table bin-width)
      // rJB = max(0.5, rJB);

      double rX = footwall ? -rJB : rJB + widthH;

      if (footwall) {
        return new Distance(rJB, hypot2(rJB, zTor), rX);
      }

      double rCut = zBot * tan(dipRad);

      if (rJB > rCut) {
        return new Distance(rJB, hypot2(rJB, zBot), rX);
      }

      // rRup when rJB is 0 -- we take the minimum the site-to-top-edge
      // and site-to-normal of rupture for the site being directly over
      // the down-dip edge of the rupture
      double rRup0 = min(hypot2(widthH, zTor), zBot * cos(dipRad));
      // rRup at cutoff rJB
      double rRupC = zBot / cos(dipRad);
      // scale linearly with rJB distance
      double rRup = (rRupC - rRup0) * rJB / rCut + rRup0;

      return new Distance(rJB, rRup, rX);
    }

    /* Same as Math.hypot() without regard to under/over flow. */
    private static final double hypot2(double v1, double v2) {
      return sqrt(v1 * v1 + v2 * v2);
    }

  }

  private static class Distance {
    public final double rJB, rRup, rX;

    private Distance(double rJB, double rRup, double rX) {
      this.rJB = rJB;
      this.rRup = rRup;
      this.rX = rX;
    }
  }

  public static void main(String[] args) {

    System.out.println(NSHMP_Util.getMeanRJB(6.05, 1.0));
    // double dist = 6.5;
    // double xmag = 6.05;
    //
    // double dr_rjb = 1.0; // historic context; could be dropped
    // double dm_rjb = 0.1;
    // double xmmin_rjb = 6.05;
    //
    // int irjb = (int) (dist/dr_rjb+1);
    //
    // int m_ind = 1 + Math.max(0,(int) Math.rint((xmag-xmmin_rjb)/dm_rjb));
    // m_ind= Math.min(26,m_ind);
    // System.out.println("m_ind: " + m_ind);
    // System.out.println("irjb: " + irjb);
    //
    // System.out.println("====");
    // double mCorr = Math.round(xmag/0.05)*0.05;
    // double r = NSHMP_Util.getMeanRJB(mCorr, dist);
    // System.out.println(r);

    double Mw = 7.45;
    SingleMagFreqDist mfd = new SingleMagFreqDist(Mw, 1, 0.1, Mw, 1);
    Location srcLoc = new Location(31.6, -117.1);
    Location siteLoc = new Location(31.6, -117.105);
    double[] depths = new double[] { 5.0, 1.0 };

    Map<FocalMech, Double> mechMap = Maps.newHashMap();
    mechMap.put(FocalMech.STRIKE_SLIP, 0.0);
    mechMap.put(FocalMech.REVERSE, 0.0);
    mechMap.put(FocalMech.NORMAL, 1.0);

    PointSourceNshm ptSrc = new PointSourceNshm(srcLoc, mfd, 1.0, mechMap);
    Joiner J = Joiner.on(" ");
    for (ProbEqkRupture rup : ptSrc) {
      PointSurfaceNshm surf = (PointSurfaceNshm) rup.getRuptureSurface();
      List<Double> attr = Lists.newArrayList(
          rup.getMag(),
          rup.getAveRake(),
          surf.getAveDip(),
          surf.zTor,
          surf.zBot,
          surf.widthH,
          surf.widthDD,
          surf.getDistanceJB(siteLoc),
          surf.getDistanceRup(siteLoc),
          surf.getDistanceX(siteLoc));

      System.out.println(J.join(attr) + " " + surf.footwall);
    }

  }

  /*
   * A depth model stores lookup arrays for mfd magnitude indexing, depths, and
   * depth weights. These arrays remove the need to do expensive lookups in a
   * magDepthMap when iterating grid sources and ruptures. A model may be longer
   * (have more magnitudes) than required by grid or area point source
   * implementations as it usually spans the [mMin mMax] of some master MFD.
   * Implementations will only ever reference those indices up to their
   * individual mMax so there should only be one per GridRuptureSet or
   * AreaSource.
   *
   * Given magDepthMap:
   *
   * [6.5 :: [1.0:0.4, 3.0:0.5, 5.0:0.1]; 10.0 :: [1.0:0.1, 5.0:0.9]]
   *
   * and an MFD with mags:
   *
   * [5.0, 5.5, 6.0, 6.5, 7.0]
   *
   * The number of mag-depth combinations a point source would iterate over is:
   * sum(m = MFD.mag(i) * nDepths(m)) = 3 * 3 + 2 * 2 = 13
   *
   * (note: mag cutoffs in magDepthMap are always used as m < cutoff)
   *
   * magDepthIndices[] : magnitude index in original MFD
   *
   * [ 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4]
   *
   * magDepthDepths[] : depth for index
   *
   * [1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 3.0, 5.0, 1.0, 5.0, 1.0, 5.0]
   *
   * magDepthWeights[] : depth weight for index
   *
   * [0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.4, 0.5, 0.1, 0.1, 0.9, 0.1, 0.9]
   *
   * A depth model also encapsulates a maximum depth value that is usually
   * source type dependent and may be used when computing the maximum width of a
   * point source.
   *
   * All DepthModel validation is currently performed in GridRuptureSet.Builder.
   */
  static final class DepthModel {

    /*
     * Initialized with a MagDepthMap; examples:
     *
     * single depth:
     *
     * [10.0 :: [depth : 1.0 ]]
     *
     * NSHMP depths:
     *
     * [6.5 :: [1.0 : 0.0, 5.0 : 1.0], 10.0 :: [1.0 : 1.0, 5.0 : 0.0]]
     */

    /*
     * maxDepth constrains the width of finite point sources. In many cases
     * (e.g. CEUS) this is not used as sources are simply modeled as lines; the
     * gmm's do not require a full finite-source parameterization.
     */
    final double maxDepth;

    final List<Double> magMaster;

    final List<Integer> magDepthIndices;
    final List<Double> magDepthDepths;
    final List<Double> magDepthWeights;

    static DepthModel create(
        NavigableMap<Double, Map<Double, Double>> magDepthMap,
        List<Double> magMaster,
        double maxDepth) {

      return new DepthModel(magDepthMap, magMaster, maxDepth);
    }

    private DepthModel(
        NavigableMap<Double, Map<Double, Double>> magDepthMap,
        List<Double> magMaster,
        double maxDepth) {

      this.magMaster = magMaster;
      this.maxDepth = maxDepth;

      List<Integer> indices = Lists.newArrayList();
      List<Double> depths = Lists.newArrayList();
      List<Double> weights = Lists.newArrayList();

      for (int i = 0; i < magMaster.size(); i++) {
        Map.Entry<Double, Map<Double, Double>> magEntry =
            magDepthMap.higherEntry(magMaster.get(i));
        for (Map.Entry<Double, Double> entry : magEntry.getValue().entrySet()) {
          indices.add(i);
          depths.add(entry.getKey());
          weights.add(entry.getValue());
        }
      }

      magDepthIndices = Ints.asList(Ints.toArray(indices));
      magDepthDepths = Doubles.asList(Doubles.toArray(depths));
      magDepthWeights = Doubles.asList(Doubles.toArray(weights));
    }
  }

}
