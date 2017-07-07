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
 * The ERF for NHSMP fault sources.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class FaultERF extends NSHMP_ERF {

	private String name;
	private List<FaultSource> sources;
	private List<ProbEqkSource> sourcesAsEqs;
	private int rupCount = -1;
	private SourceRegion srcRegion;
	private SourceIMR srcIMR;
	private double weight;
	private double maxR;
	private Region bounds;

	FaultERF(String name, List<FaultSource> sources, SourceRegion srcRegion,
		SourceIMR srcIMR, double weight, double maxR) {
		this.name = name;
		this.sources = sources;
		this.srcRegion = srcRegion;
		this.srcIMR = srcIMR;
		this.weight = weight;
		this.maxR = maxR;
		
		// nshmp defaults
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(1);
		timeSpan.addParameterChangeListener(this);
	}

	@Override
	public int getNumSources() {
		return sources.size();
	}
	
	// can't be called until after updateForecast()
	@Override
	public int getRuptureCount() {
		return rupCount;
	}
	
	@Override
	public SourceRegion getSourceRegion() {
		return srcRegion;
	}

	@Override
	public SourceType getSourceType() {
		return SourceType.FAULT;
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
	
	// TODO should be able to retreive a bounds with a variable sized buffer
	private void initBounds() {
		double minLat = Double.POSITIVE_INFINITY;
		double maxLat = Double.NEGATIVE_INFINITY;
		double minLon = Double.POSITIVE_INFINITY;
		double maxLon = Double.NEGATIVE_INFINITY;
		for (FaultSource source : sources) {
			LocationList locs = source.getAllSourceLocs();
			minLat = Math.min(minLat, LocationUtils.calcMinLat(locs));
			maxLat = Math.max(maxLat, LocationUtils.calcMaxLat(locs));
			minLon = Math.min(minLon, LocationUtils.calcMinLon(locs));
			maxLon = Math.max(maxLon, LocationUtils.calcMaxLon(locs));
		}
		bounds = NSHMP_Utils.creatBounds(minLat, maxLat, minLon, maxLon, maxR);
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
	
	public List<FaultSource> getSources() { return sources; } 

	@Override
	public ProbEqkSource getSource(int idx) {
		return sources.get(idx);
	}

	@Override
	public void updateForecast() {
		int count = 0;
		for (FaultSource source : sources) {
			source.init();
			count += source.getNumRuptures();
		}
		rupCount = count;
		initBounds();
	}

	@Override
	public String getName() {
		return name;
	}

}
