package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.LocationList;
import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.FloatingPoissonFaultSource;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Lists;

/**
 * This class is used to represent all fault sources in the 2008 NSHMP. Each
 * {@code FaultSource} wraps one or more {@code FloatingPoissonFaultSource}s.
 * There is a 1 to 1 mapping of mfds to wrapped sources.
 * 
 * <p>A fault source can not be created directly; it may only be created by
 * a private parser.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class FaultSource extends ProbEqkSource {

	private static final MagScalingRelationship WCLmsr; // W&C mag-length
	private static final MagScalingRelationship CAFmsr; // CA floater

	// fields directly populated by parser
	FaultTrace trace;
	List<IncrementalMagFreqDist> mfds;
	SourceFile file;
	String name;
	FaultType type;
	FocalMech mech;
	int nMag;
	boolean floats;
	double dip;
	double width;
	double top;

	int size = 0;
	// TODO this should not be using abstract impl
	StirlingGriddedSurface surface;

	List<FloatingPoissonFaultSource> sources;
	List<Integer> rupCount; // cumulative index list for iterating ruptures

	FaultSource() {}

	/**
	 * Initialize intrnal fault sources.
	 */
	public void init() {
		// init fault surface
		double lowerSeis = top + width * Math.sin(dip * GeoTools.TO_RAD);
		SimpleFaultData sfd = new SimpleFaultData(dip, lowerSeis, top, trace);
		surface = new StirlingGriddedSurface(sfd, 1.0, 1.0);
//		surface = new StirlingGriddedSurface(trace, dip, top, lowerSeis, 1);
		// create a floating poisson source for each mfd
		if (mfds.size() == 0) return;
		sources = Lists.newArrayList();
		rupCount = Lists.newArrayList();
		rupCount.add(0);
		size = 0;
		MagScalingRelationship msr = ((file.getType() == FAULT) &&
			(file.getRegion() == CA) && floats) ? CAFmsr : WCLmsr;
		FloatingPoissonFaultSource source;
		for (IncrementalMagFreqDist mfd : mfds) {
			source = new FloatingPoissonFaultSource(
				mfd, // IncrementalMagFreqDist
				surface, // EvenlyGriddedSurface
				msr, // MagScalingRelationship
				0d, // sigma of the mag-scaling relationship
				1d, // floating rupture aspect ratio (length/width)
				1d, // floating rupture offset
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
	
	/*
	 * Overriden due to uncertainty on how getRuptureList() is constructed in
	 * parent. Looks clucky and uses cloning which can be error prone if
	 * implemented incorrectly. Was building custom NSHMP calculator
	 * using enhanced for-loops and was losing class information when iterating
	 * over sources and ruptures.
	 */
	@Override
	public List<ProbEqkRupture> getRuptureList() {
		throw new UnsupportedOperationException(
			"A FaultSource does not allow access to the list "
				+ "of all possible sources.");
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
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new StringBuilder()
		.append("==========  Fault Source  ==========")
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
		WCLmsr = new WC1994_MagLengthRelationship();
		CAFmsr = new CA_MagAreaRelationship();
	}
	
	/**
	 * Returns the list of magnitude frequency distributions that this source
	 * represents
	 * @return the source MFD's
	 */
	public List<IncrementalMagFreqDist> getMFDs() {
		return mfds;
	}

}
