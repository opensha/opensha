/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.FocalMech.*;
import static org.opensha.nshmp2.util.NSHMP_Utils.*;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static java.lang.Math.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SingleMagFreqDist;
import org.opensha.sha.util.NSHMP_Util;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

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
 * hanging wall effects as well as to override {@code getMinDistance(Site)} to provide
 * consistency with distances determined during hazard calcs.
 * 
 * <p>The class is currently configured to handle 2 rupture top depths; depth #1 is applied to
 * M&lt;6.5 and depth #2 to M&ge;6.5. Set both values the same for single depth
 * across all magnitudes.
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
 * would be threading issues as the internal rupture reference would be
 * updated asynchronously.
 * 
 * Could probably implement a slightly speedier subclass that would ignore
 * mechWeights as the weighting is actually handled in the lookup tables
 * of the GridIMRs that get used during hazard calcs.
 * 
 * @author P. Powers
 * @version: $Id$
 */
public class PointSource13b extends ProbEqkSource {

	// TODO class will eventually be reconfigured to supply distance metrics
	// at which point M_FINITE_CUT will be used (and set on invocation)

	private static final String NAME = "NSHMP Point Source";
	private static final double M_DEPTH_CUT = 6.5;

	/** Minimum magnitude for finite fault representation. */
//	public static final double M_FINITE_CUT = 6.0;
	
	private static final MagLengthRelationship WC94 = 
			new WC1994_MagLengthRelationship();
	
	private Location loc;
	private IncrementalMagFreqDist mfd;
	private double duration;
	private double lgMagDepth;
	private double smMagDepth;
	public Map<FocalMech, Double> mechWts;

	private int mechCount; // mechs with weight 1-3;
	private int ssIdx, revIdx; // normal not needed
	private int fwIdxLo, fwIdxHi;
	
	// Rupture indexing: no array index out of bounds are checked, it is assumed
	// that users will only request values in the range getNumRuptures()-1
	// Focal mech is determined using the max indices for each type of mech
	// determined using the Math.ceil(wt) [scales to 1] * num_M

	// this field is ignored by subclasses but ostensibly should be able to
	// be a RuptureSurface once migrated to the new representation
	private PointSurface13b surface;
	
	/**
	 * Rupture instance.
	 */
	protected ProbEqkRupture probEqkRupture;

	
	/**
	 * Constructs a new point earthquake source.
	 * 
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param duration of the parent forecast
	 * @param depths 2 element array of rupture top depths;
	 *        <code>depths[0]</code> used for M&lt;6.5, <code>depths[1]</code>
	 *        used for M&ge;6.5
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 */
	public PointSource13b(Location loc, IncrementalMagFreqDist mfd,
		double duration, double[] depths, Map<FocalMech, Double> mechWtMap) {

		name = NAME; // super
		this.loc = loc;
		this.mfd = mfd;
		this.duration = duration;
		smMagDepth = depths[0];
		lgMagDepth = depths[1];
		this.mechWts = mechWtMap;

		// rupture indexing
		mechCount = countMechs(mechWtMap);
		setIndices();

		probEqkRupture = new ProbEqkRupture();
		surface = new PointSurface13b(loc); // mutable, possibly depth varying
	}

	@Override
	public ProbEqkRupture getRupture(int idx) {
		if(idx>getNumRuptures()-1 || idx < 0)
			throw new RuntimeException("index out of bounds");
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
	 * implemented incorrectly. Was building custom NSHMP calculator
	 * using enhanced for-loops and was losing class information when iterating
	 * over sources and ruptures.
	 */
	@Override
	public List<ProbEqkRupture> getRuptureList() {
		throw new UnsupportedOperationException(
			"A PointSource does not allow access to the list "
				+ "of all possible ruptures.");
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
		locList.add(surface.getLocation());
		return locList;
	}

	@Override
	public RuptureSurface getSourceSurface() {
		return surface;
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
	 * Returns the minimum of the aspect ratio width (based on WC94) length
	 * and the allowable down-dip width.
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
	 * potentially short circuit GMPE calcs. Because the index order is
	 * 		SS-FW RV-FW RV-HW NR-FW NR-HW
	 */
	private boolean isOnFootwall(int idx) {
		return (idx < fwIdxLo) ? true : 
			   (idx < revIdx) ? false : 
			   (idx < fwIdxHi) ? true : false;
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
	 * This is misnamed; we're double counting reverse and normal mechs
	 * because they will have hanging wall and footwall representations.
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
	public static class PointSurface13b extends PointSurface {
		
		private double widthH; // horizontal width (surface projection)
		private double widthDD; // down-dip width
		private double zTop;
		private double zBot; // base of rupture; may be less than 14km
		
		private boolean footwall;
		
		public PointSurface13b(Location loc) {
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
		
		// because we're not using table lookup optimizations, we push the
		// minimum rJB out to 0.5 (half the table bin-width)
		@Override
		public double getDistanceJB(Location loc) {
			return max(0.5, super.getDistanceJB(loc));
		}
		
		@Override
		public double getDistanceX(Location loc) {
			double rJB = getDistanceJB(loc);
			return footwall ? -rJB : rJB + widthH;
		}
		
		@Override
		public double getDistanceRup(Location loc) {
			double rJB = getDistanceJB(loc);

			if (footwall) return hypot2(rJB, zTop);
			
			double dipRad = aveDip * TO_RAD;
			double rCut = zBot * tan(dipRad);
			
			if (rJB > rCut) return hypot2(rJB, zBot);
			
			// rRup when rJB is 0 -- we take the minimum the site-to-top-edge
			// and site-to-normal of rupture for  the site being directly over
			// the down-dip edge of the rupture
			double rRup0 = min(hypot2(widthH, zTop) ,zBot * cos(dipRad));
			// rRup at cutoff rJB
			double rRupC = zBot / cos(dipRad);
			// scale linearly with rJB distance
			return (rRupC - rRup0) * rJB / rCut + rRup0;
		}
		
		public boolean isOnFootwall() { return footwall; }
		
		/**
		 * Same as {@code Math.hypot()} without regard to under/over flow.
		 */
		private static final double hypot2(double v1, double v2) {
			return sqrt(v1 * v1 + v2 * v2);
		}
		
	}
	
	public static void main(String[] args) {
		
		System.out.println(NSHMP_Util.getMeanRJB(6.05, 1.0));
//		double dist = 6.5;
//		double xmag = 6.05;
//		
//		double dr_rjb = 1.0; // historic context; could be dropped
//		double dm_rjb = 0.1;
//		double xmmin_rjb = 6.05;
//		
//	    int irjb = (int) (dist/dr_rjb+1);
//	     
//	    int m_ind = 1 + Math.max(0,(int) Math.rint((xmag-xmmin_rjb)/dm_rjb));
//	    m_ind= Math.min(26,m_ind);
//	    System.out.println("m_ind: " + m_ind);
//	    System.out.println("irjb: " + irjb);
//	    
//	    System.out.println("====");
//	    double mCorr = Math.round(xmag/0.05)*0.05;
//		double r = NSHMP_Util.getMeanRJB(mCorr, dist);
//		System.out.println(r);
		
		
		double Mw = 7.45;
		SingleMagFreqDist mfd = new SingleMagFreqDist(Mw, 1, 0.1, Mw, 1);
		Location srcLoc = new Location(31.6, -117.1);
		Location siteLoc = new Location(31.6, -117.105);
		double[] depths = new double[] {5.0, 1.0};
		
		Map<FocalMech, Double> mechMap = Maps.newHashMap();
		mechMap.put(FocalMech.STRIKE_SLIP, 0.0);
		mechMap.put(FocalMech.REVERSE, 0.0);
		mechMap.put(FocalMech.NORMAL, 1.0);

		
		PointSource13b ptSrc = new PointSource13b(srcLoc, mfd, 1.0, depths, mechMap);
		Joiner J = Joiner.on(" ");
		for (ProbEqkRupture rup : ptSrc) {
			PointSurface13b surf = (PointSurface13b) rup.getRuptureSurface();
			List<Double> attr = Lists.newArrayList(
				rup.getMag(),
				rup.getAveRake(),
				surf.getAveDip(),
				surf.zTop,
				surf.zBot,
				surf.widthH,
				surf.widthDD,
				surf.getDistanceJB(siteLoc),
				surf.getDistanceRup(siteLoc),
				surf.getDistanceX(siteLoc));
				
			System.out.println(J.join(attr) + " " + surf.footwall);
		}
		
	}
	
	public IncrementalMagFreqDist getMFD() {
		return mfd;
	}

}
