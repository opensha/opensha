package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.SourceRegion.CEUS;
import static org.opensha.nshmp2.util.SourceType.GRIDDED;

import java.awt.Color;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.nshmp.NEHRP_TestCity;
import org.opensha.nshmp2.util.FaultCode;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;

/**
 * The ERF class used to represent gridded sources defined in the 2008 NSHMP.
 * Instances of this class can only be generated via a parser. Currently this
 * ERF does not require <code>updateForecast()</code> to be called as it has
 * no adjustable parameters.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GridERF extends NSHMP_ERF {

	private String name;
	private String info; // summary of data used to build erf
	private LocationList locs;
	private Region border; // region connecting outer edge of valid nodes
	private Region bounds;
	private List<IncrementalMagFreqDist> mfds;
	private int[] srcIndices;
	private Map<FocalMech, Double> mechWtMap;
	private double[] depths;
	private FaultCode faultCode; // used as mag conversion flag
	private double strike; // only referenced as needed in source init
	private SourceRegion srcRegion;
	private SourceIMR srcIMR;
	private double weight;
	private double maxR, dR;

	private final static MagLengthRelationship MLR = new WC1994_MagLengthRelationship();

	GridERF(String name, String info, Region border, LocationList locs,
		List<IncrementalMagFreqDist> mfds, double[] depths,
		Map<FocalMech, Double> mechWtMap, FaultCode faultCode, double strike,
		SourceRegion srcRegion, SourceIMR srcIMR, double weight, double maxR, double dR) {
		this.name = name;
		this.info = info;
		this.border = border;
		this.locs = locs;
		this.mfds = mfds;
		this.depths = depths;
		this.mechWtMap = mechWtMap;
		this.faultCode = faultCode;
		this.strike = strike;
		this.srcRegion = srcRegion;
		this.srcIMR = srcIMR;
		this.weight = weight;
		this.maxR = maxR;
		this.dR = dR;
		
		initIndices();
		// nshmp defaults
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(1);
		timeSpan.addParameterChangeListener(this);
	}

	private void initIndices() {
		// srcIndices.length == # of non-null mfds; the stored value points
		// to the mfd and location index
		List<Integer> list = Lists.newArrayList();
		for (int i = 0; i < mfds.size(); i++) {
			if (mfds.get(i) != null) list.add(i);
		}
		srcIndices = Ints.toArray(list);
	}
	
	private void initBounds() {
		bounds = NSHMP_Utils.createBounds(border.getBorder(), maxR + 10);
	}

	/**
	 * Returns the list of <code>Location</code>s for each point source in the
	 * source.
	 * @return the list of source <code>Location</code>s
	 */
	public LocationList getNodes() {
		return locs;
	}

	/**
	 * Returns the border of this source. This is a <code>Region</code> defined
	 * by connecting the outermost point sources associated with this ERF.
	 * @return the border
	 */
	public Region getBorder() {
		return border;
	}

	@Override
	public int getNumSources() {
		return srcIndices.length;
	}
	
	@Override
	public int getRuptureCount() {
		// ths number of ruptures in a grid source can be gleaned by tallying
		// the magnitude count in all mfds; however this doesn't account for
		// focal mech variations
		int count = 0;
		for (IncrementalMagFreqDist mfd : mfds) {
			count += mfd.size();
		}
		return count;
	}
	
	@Override
	public SourceRegion getSourceRegion() {
		return srcRegion;
	}

	@Override
	public SourceType getSourceType() {
		return SourceType.GRIDDED;
	}

	@Override
	public SourceIMR getSourceIMR() {
		return srcIMR;
	}
	
	@Override
	public double getSourceWeight() {
		return weight;
	}

	@Override
	public double getMaxDistance() {
		return maxR;
	}
	
	@Override
	public Region getBounds() {
		return bounds;
	}
	
	/**
	 * Returns the {@link FaultCode} for this ERF
	 * @return the fault code
	 */
	public FaultCode getFaultCode() {
		return faultCode;
	}
	
	/**
	 * Returns the distance interval to use when building exceedance
	 * lookup tables. Value is used in conjuction with {@code getMaxDistance()}. 
	 * @return the distance increment (dR)
	 */
	public double getDistanceInterval() {
		return dR;
	}
	
	/**
	 * Returns the rupture depths for events M&lt;6.5&leq;M.
	 * @return a two element depth array
	 */
	public double[] getDepths() {
		return depths;
	}
	
	/**
	 * Returns the map pf focal mechanism and associated weights.
	 * @return the focal mech weight map
	 */
	public Map<FocalMech, Double> getFocalMechs() {
		return mechWtMap;
	}

	/**
	 * Method not supported to conserve memory; <code>iterator() </code>
	 * overridden to provide efficient loop access.
	 * @throws UnsupportedOperationException
	 */
	@Override
	public List<ProbEqkSource> getSourceList() {
		throw new UnsupportedOperationException(
			"A GridSource does not allow access to the list "
				+ "of all possible sources.");
	}
	
	@Override
	public Iterator<ProbEqkSource> iterator() {
		// @formatter:off
		return new Iterator<ProbEqkSource>() {
			int size = getNumSources();
			int caret = 0;
			@Override public boolean hasNext() {
				return caret < size;
			}
			@Override public ProbEqkSource next() {
				return getSource(caret++);
			}
			@Override public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		// @formatter:on
	}

	@Override
	public ProbEqkSource getSource(int idx) {
		// @formatter:off
		idx = srcIndices[idx];
		return (faultCode == FIXED)
				? new FixedStrikeSource(locs.get(idx), mfds.get(idx), MLR,
					timeSpan.getDuration(), depths, mechWtMap, strike)
				: new PointSource13b(locs.get(idx), mfds.get(idx),
					timeSpan.getDuration(), depths, mechWtMap);
		// @formatter:on
	}

	/**
	 * Returns the magnitude-frequency distribution at the specified location.
	 * 
	 * NOTE: this isn't good as the original MFD's are returned, which can be
	 * manipulated
	 * 
	 * @param loc
	 * @return the MFD at the supplied location or <code>null</code> if none
	 *         exists
	 */
	public IncrementalMagFreqDist getMFD(Location loc) {
		for (int i=0; i < locs.size(); i++) {
			if (LocationUtils.areSimilar(locs.get(i), loc)) {
//			if (locs.get(i).equals(loc)) {
				return mfds.get(i);
			}
		}
		return null;
	}

	@Override
	public void updateForecast() {
		initBounds();
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return info;
	}
	
	/**
	 * Scales the internal mfd source rates by the {@code NSHMP_ERF} weight.
	 * Calling this method is only appropriate when not using {@code GridERF}s
	 * with {@link HazardCalc}.
	 */
	public void scaleRatesToWeight() {
		double wt = getSourceWeight();
		for (int idx : srcIndices) {
			mfds.get(idx).scale(wt);
		}
	}

//	private String printIthSourceInputs(int idx) {
//		StringBuilder sb = new StringBuilder(getName());
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append("Src idx: ").append(idx);
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(locs.get(idx));
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(mfds.size()).append(mfds.get(idx));
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(MLR);
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(0.0);
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(timeSpan.getDuration());
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(6.0);
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(mechWtMap.get(FocalMech.STRIKE_SLIP));
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(mechWtMap.get(FocalMech.REVERSE));
//		sb.append(IOUtils.LINE_SEPARATOR).append("\t");
//		sb.append(mechWtMap.get(FocalMech.NORMAL));
//		return sb.toString();
//	}

	public static void main(String[] args) {
		GridERF tmpERF = Sources.getGrid("CEUS.2007all8.AB.in");
		RegionUtils.regionToKML(tmpERF.getBorder(), "CEUSborder", Color.RED);
		RegionUtils.regionToKML(tmpERF.getBounds(), "CEUSbounds", Color.ORANGE);
		
		tmpERF = Sources.getGrid("pnwdeep.in");
		RegionUtils.regionToKML(tmpERF.getBorder(), "PNWdeepBorder", Color.RED);
		RegionUtils.regionToKML(tmpERF.getBounds(), "PNWdeepBounds", Color.ORANGE);
		
//		getTestGrid(new Location(35.6,-90.4), tmpERF);
	}
	
	/**
	 * Returns a custom GridERF with a single point source. Requires user
	 * customization.
	 * @return the ERF
	 */
	public static GridERF getTestGrid(Location loc, GridERF erf) {
		
		Logger log = NSHMP_Utils.logger();
		Level level = Level.FINE;
		log.setLevel(level);
		for (Handler h : NSHMP_Utils.logger().getHandlers()) {
			h.setLevel(level);
		}

		IncrementalMagFreqDist mfd = erf.getMFD(loc);
		erf.mfds.clear();
		erf.mfds.add(mfd);
		erf.locs.clear();
		erf.locs.add(loc);
		erf.name = "Small Test GridERF";
		erf.initIndices();
		
		return erf;
	}

}
