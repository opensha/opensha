package org.opensha.nshmp2.erf.source;

import java.util.List;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.SourceIMR;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;
import org.opensha.sha.earthquake.ProbEqkSource;

import com.google.common.collect.Lists;

/**
 * The ERF for NHSMP cluster (New Madrid seismic zone) sources.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ClusterERF extends NSHMP_ERF {

	private String name;
	private List<ClusterSource> sources;
	private List<ProbEqkSource> sourcesAsEqs;
	private SourceRegion srcRegion;
	private SourceIMR srcIMR;
	private double weight;
	private double maxR;
	private Region bounds;

	ClusterERF(String name, List<ClusterSource> sources, SourceRegion srcRegion,
		SourceIMR srcIMR, double weight, double maxR) {
		this.name = name;
		this.sources = sources;
		this.srcRegion = srcRegion;
		this.srcIMR = srcIMR;
		this.weight = weight;
		this.maxR = maxR;
		
		// nshmp defaults TODO move to NSHMP_ERF
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(1);
		timeSpan.addParameterChangeListener(this);
	}


	@Override
	public int getNumSources() {
		return sources.size();
	}

	@Override
	public List<ProbEqkSource> getSourceList() {
		if (sourcesAsEqs == null) {
			sourcesAsEqs = Lists.newArrayList();
			for (ProbEqkSource pes : sources) {
				sourcesAsEqs.add(pes);
			}
		}
		return sourcesAsEqs;
	}
	
	/**
	 * Convenience method to return a source list characterized as
	 * <code>ClusterSource</code>s rather than <code>ProbEqkSource</code>s.
	 * @return the list of <code>ClusterSource</code>s.
	 * @see #getSourceList()
	 */
	public List<ClusterSource> getSources() { return sources; } 

	@Override
	public ProbEqkSource getSource(int idx) {
		return sources.get(idx);
	}

	@Override
	public void updateForecast() {
		for (ClusterSource source : sources) {
			source.init();
		}
		initBounds();
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public int getRuptureCount() {
		int count = 0;
		for (ClusterSource cs : sources) {
			count += cs.getNumRuptures();
		}
		return count;
	}

	@Override
	public SourceRegion getSourceRegion() {
		return srcRegion;
	}

	@Override
	public SourceType getSourceType() {
		return SourceType.CLUSTER;
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
	
	private void initBounds() {
		double minLat = Double.POSITIVE_INFINITY;
		double maxLat = Double.NEGATIVE_INFINITY;
		double minLon = Double.POSITIVE_INFINITY;
		double maxLon = Double.NEGATIVE_INFINITY;
		for (ClusterSource cSrc : sources) {
			for (FaultSource fSrc : cSrc.sources) {
				LocationList locs = fSrc.getAllSourceLocs();
				minLat = Math.min(minLat, LocationUtils.calcMinLat(locs));
				maxLat = Math.max(maxLat, LocationUtils.calcMaxLat(locs));
				minLon = Math.min(minLon, LocationUtils.calcMinLon(locs));
				maxLon = Math.max(maxLon, LocationUtils.calcMaxLon(locs));
			}
		}
		bounds = NSHMP_Utils.creatBounds(minLat, maxLat, minLon, maxLon, maxR);
	}
	
	// in calculator
	// each cluster ERF is composed of 5 cluster sources
	//		- weighted fault models
	// get each source (cluster)
	//		for each source
	//			calc PE looping and weighting
	//				gmpe	weighted in grouped gmpe
	//				M		rate already weighted when mfd created but not used
	//						until after cluster algorithm; rate used to extract
	//						magnitude weight from mfd 
	//				
	

}
