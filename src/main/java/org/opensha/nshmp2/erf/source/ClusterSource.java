package org.opensha.nshmp2.erf.source;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Lists;

/**
 * This class is used for the cluster sources in the CEUS 2008 NSHMP.
 * Specifically those sources related to the New Madrid Siesmic Zone (NMSZ).
 * Cluster sources are calculated in a way that is incompatible with the
 * standard hazard curve calculation pipeline. This class is only for use with
 * the NSHMP hazard calculator at this time. Many methods required for a
 * 'standard' hazard calculation are overridden here to throw
 * <code>UnsupportedOperationException</code>s.
 * 
 * <p>For most NSHMP sources, each input file produces a single output file that is
 * weighted during the 'combine' phase. In the case of NMSZ cluster sources,
 * there are 5 output files each with a corresponding weight. The 5 outputs are
 * for different fault models and their weights vary depending on the recurrence
 * time of the cluster. These wieghts are stored internally in a
 * <code>ClusterSource</code> and used by the NSHMP hazard calculator during
 * processing.</p>
 * 
 * <p>Currently fault location branches are being treated independently wrt to 
 * inclusion distance <code>getMinDistance(Site)</code>. That is, one or more
 * fault location branches may be included in a calculation for a site at
 * ~1000km but not others.</p>
 * 
 * <p>A cluster source can not be created directly; it may only be created by
 * a private parser.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ClusterSource extends ProbEqkSource {

	// fields directly populated by parser
	List<FaultSource> sources;
	SourceFile file;
	String name;
	double weight;
	double rate; // return period in years

	ClusterSource() {
		sources = Lists.newArrayList();
	}

	/**
	 * Initialize intrnal fault sources.
	 */
	public void init() {
		for (FaultSource fs : sources) {
			fs.init();
		}
//		// init fault surface
//		double lowerSeis = top + width * Math.sin(dip * GeoTools.TO_RAD);
//		surface = new StirlingGriddedSurface(trace, dip, top, lowerSeis, 1);
//		// create a floating poisson source for each mfd
//		if (mfds.size() == 0) return;
//		sources = Lists.newArrayList();
//		rupCount = Lists.newArrayList();
//		MagScalingRelationship msr = ((file.getType() == FAULT) &&
//			(file.getRegion() == CA) && floats) ? CAF : WCL;
//		FloatingPoissonFaultSource source;
//		for (IncrementalMagFreqDist mfd : mfds) {
//			source = new FloatingPoissonFaultSource(mfd, // IncrementalMagFreqDist
//				surface, // EvenlyGriddedSurface
//				msr, // MagScalingRelationship
//				0d, // sigma of the mag-scaling relationship
//				1d, // floating rupture aspect ratio (length/width)
//				5d, // floating rupture offset
//				mech.rake(), // average rake of the ruptures
//				1d, // duration of forecast
//				0d, // minimum mag considered
//				0, // type of floater (0 = full DDW, 1 = both, 2= centered)
//				floats ? 10d : 0d); // mag above which full rup
//			sources.add(source);
//			int rups = source.getNumRuptures();
//			size += rups;
//			rupCount.add(size);
//		}
	}
	
	/**
	 * Returns the return period of this source in years.
	 * @return the cluster return period
	 */
	public double getRate() {
		return rate;
	}
	
	/**
	 * Returns the weight that should be applied to this source.
	 * @return the source weight
	 */
	public double getWeight() {
		return weight;
	}

	/**
	 * Return all sources as {@code FaultSource}s.
	 * @return a list of all {@code FaultSource}s
	 */
	public List<FaultSource> getFaultSources() {
		return sources;
	}

	@Override
	public LocationList getAllSourceLocs() {
		throw new UnsupportedOperationException();
	}

	@Override
	public EvenlyGriddedSurface getSourceSurface() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getMinDistance(Site site) {
		// assumes ClusterSource.init() has been called
		// lopps sources finding closest rupture
		double d = Double.MAX_VALUE;
		for (FaultSource fs : sources) {
			d = Math.min(d, fs.getMinDistance(site));
		}
		return d;
	}

	@Override
	public int getNumRuptures() {
		int count = 0;
		for (FaultSource fs : sources) {
			count += fs.getNumRuptures();
		}
		return count;
	}

	@Override
	public ProbEqkRupture getRupture(int idx) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<ProbEqkRupture> iterator() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		// @formatter:off
		StringBuilder sb = new StringBuilder();
		sb.append("=========  Cluster Source  =========");
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append(" Cluster name: ").append(name);
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("  Ret. period: ").append(rate).append(" yrs");
		sb.append(IOUtils.LINE_SEPARATOR);
		sb.append("       Weight: ").append(weight);
		sb.append(IOUtils.LINE_SEPARATOR);
		for (FaultSource fs : sources) {
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("        Fault: ").append(fs.getName());
			sb.append(IOUtils.LINE_SEPARATOR);
			List<Double> mags = Lists.newArrayList();
			List<Double> wts = Lists.newArrayList();
			for (IncrementalMagFreqDist mfd : fs.mfds) {
				mags.add(mfd.getX(0));
				wts.add(mfd.getY(0) * rate);
			}
			sb.append("         Mags: ").append(mags);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("          Wts: ").append(wts);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("         type: ").append(fs.type);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("         mech: ").append(fs.mech);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("       floats: ").append(fs.floats);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("          dip: ").append(fs.dip);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("        width: ").append(fs.width);
			sb.append(IOUtils.LINE_SEPARATOR);
			sb.append("          top: ").append(fs.top);
			sb.append(IOUtils.LINE_SEPARATOR);
		}
		// @formatter:on
		return sb.toString();
	}

}
