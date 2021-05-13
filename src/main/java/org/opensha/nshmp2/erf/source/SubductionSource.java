package org.opensha.nshmp2.erf.source;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.FloatingPoissonFaultSource;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Lists;

/**
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class SubductionSource extends FaultSource {

	private static final MagScalingRelationship SUBmsr;

	// fields directly populated by parser
	FaultTrace lowerTrace;

	int size = 0;
	ApproxEvenlyGriddedSurface surface;

	// these are in parent and can probably be deleted
	List<FloatingPoissonFaultSource> sources;
	List<Integer> rupCount;

	// package private constructor
	SubductionSource() {}

	@Override
	public void init() {
		// init fault surface
		surface = new ApproxEvenlyGriddedSurface(trace, lowerTrace, 5.0);
		// create a floating poisson source for each mfd
		if (mfds.size() == 0) return;
		sources = Lists.newArrayList();
		rupCount = Lists.newArrayList();
		rupCount.add(0);
		size = 0;
		FloatingPoissonFaultSource source;
		for (IncrementalMagFreqDist mfd : mfds) {
			source = new FloatingPoissonFaultSource(mfd, // IncrementalMagFreqDist
				surface, // EvenlyGriddedSurface
				SUBmsr, // MagScalingRelationship
				0d, // sigma of the mag-scaling relationship
				1d, // floating rupture aspect ratio (length/width)
				5d, // floating rupture offset
				mech.rake(), // average rake of the ruptures
				1d, // duration of forecast
				0d, // minimum mag considered
				0, // type of floater (0 = full DDW, 1 = both, 2= centered)
				floats ? 10d : 0d); // mag above which full rup
			sources.add(source);
			int rups = source.getNumRuptures();
			size += rups;
			rupCount.add(size);
		}
	}

	@Override
	public LocationList getAllSourceLocs() {
		return surface.getEvenlyDiscritizedListOfLocsOnSurface();
	}

	@Override
	public EvenlyGriddedSurface getSourceSurface() {
		return surface;
	}

	@Override
	public double getMinDistance(Site site) {
		if (sources == null || sources.size() == 0) return Double.NaN;
		return sources.get(0).getMinDistance(site);
	}

	@Override
	public int getNumRuptures() {
		return size;
	}

	// for now, ruptures are nested in sources which we iterate over
	@Override
	public ProbEqkRupture getRupture(int idx) {
		if (getNumRuptures() == 0) return null;
		// zero is built in to rupCount array; unless a negative idx is
		// supplied, if statement below should never be entered on first i
		for (int i = 0; i < rupCount.size(); i++) {
			if (idx < rupCount.get(i)) {
				return sources.get(i-1).getRupture(idx - rupCount.get(i-1));
			}
		}
		return null; // shouldn't get here

	}

	@Override
	public Iterator<ProbEqkRupture> iterator() {
		// @formatter:off
		return new Iterator<ProbEqkRupture>() {
			int caret = 0;
			@Override public boolean hasNext() {
				return (caret < size) ? true : false;
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
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new StringBuilder()
		.append("=============  Source  =============")
		.append(IOUtils.LINE_SEPARATOR)
		.append("   Fault name: ").append(name)
		.append(IOUtils.LINE_SEPARATOR)
		.append("         type: ").append(type)
		.append(IOUtils.LINE_SEPARATOR)
		.append("         mech: ").append(mech)
		.append(IOUtils.LINE_SEPARATOR)
		.append("         mags: ").append(nMag)
		.append(IOUtils.LINE_SEPARATOR)
		.append("         mfds: ").append(mfds.size())
		.append(IOUtils.LINE_SEPARATOR)
		.append("       floats: ").append(floats)
		.append(IOUtils.LINE_SEPARATOR)
		.append("          dip: ").append(dip)
		.append(IOUtils.LINE_SEPARATOR)
		.append("        width: ").append(width)
		.append(IOUtils.LINE_SEPARATOR)
		.append("          top: ").append(top)
		.append(IOUtils.LINE_SEPARATOR).toString();
		// @formatter:on
	}

	static {
		SUBmsr = new GeoMat_MagLenthRelationship();
	}

}
