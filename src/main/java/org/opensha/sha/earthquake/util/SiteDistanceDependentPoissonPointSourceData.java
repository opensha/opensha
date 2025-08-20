package org.opensha.sha.earthquake.util;

import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.PointSource.PoissonPointSourceData;
import org.opensha.sha.earthquake.PointSource.SiteAdaptivePointSourceData;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class SiteDistanceDependentPoissonPointSourceData implements SiteAdaptivePointSourceData<PoissonPointSourceData>, PoissonPointSourceData {

	protected Location centerLoc;
	protected List<PoissonPointSourceData> datas;
	protected List<Double> cutoffDistances;
	protected PoissonPointSourceData fallbackData;
	protected double maxCutoffDist;

	public SiteDistanceDependentPoissonPointSourceData(Location centerLoc, PoissonPointSourceData nearbyData,
			double cutoffDistance, PoissonPointSourceData fallbackData) {
		this(centerLoc, List.of(nearbyData), List.of(cutoffDistance), fallbackData);
	}
	
	public SiteDistanceDependentPoissonPointSourceData(Location centerLoc, List<PoissonPointSourceData> datas,
			List<Double> cutoffDistances, PoissonPointSourceData fallbackData) {
		init(centerLoc, datas, cutoffDistances, fallbackData);
	}
	
	/**
	 * No-arg constructor for use by subclasses; you must call the {@link #init(Location, List, List, PoissonPointSourceData)}
	 * method.
	 */
	protected SiteDistanceDependentPoissonPointSourceData() {
		
	}
	
	protected void init(Location centerLoc, List<PoissonPointSourceData> datas,
			List<Double> cutoffDistances, PoissonPointSourceData fallbackData) {
		this.centerLoc = centerLoc;
		this.datas = datas;
				this.cutoffDistances = cutoffDistances;
				this.fallbackData = fallbackData;
		Preconditions.checkArgument(datas.size() == cutoffDistances.size());
		for (int i=1; i<cutoffDistances.size(); i++) {
			double dist0 = cutoffDistances.get(i-1);
			double dist1 = cutoffDistances.get(i);
			Preconditions.checkState(dist1 > dist0,
					"Distances must monotonically increasing; cutoffDistances[%s]=%s, cutoffDistances[%s]=%s",
					i-1, dist0, i, dist1);
		}
		maxCutoffDist = cutoffDistances.get(cutoffDistances.size()-1);
	}

	@Override
	public int getNumRuptures() {
		return fallbackData.getNumRuptures();
	}

	@Override
	public double getMagnitude(int rupIndex) {
		return fallbackData.getMagnitude(rupIndex);
	}

	@Override
	public double getAveRake(int rupIndex) {
		return fallbackData.getAveRake(rupIndex);
	}

	@Override
	public double getRate(int rupIndex) {
		return fallbackData.getRate(rupIndex);
	}

	@Override
	public RuptureSurface getSurface(int rupIndex) {
		return fallbackData.getSurface(rupIndex);
	}

	@Override
	public boolean isFinite(int rupIndex) {
		return fallbackData.isFinite(rupIndex);
	}

	@Override
	public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
		return fallbackData.getHypocenter(sourceLoc, rupSurface, rupIndex);
	}

	@Override
	public PoissonPointSourceData getForSite(Site site) {
		double dist = LocationUtils.horzDistanceFast(centerLoc, site.getLocation());
		return getForDistance(dist);
	}
	
	public PoissonPointSourceData getForDistance(double dist) {
		if ((float)dist > (float)maxCutoffDist)
			return this;
		for (int i=0; i<datas.size(); i++)
			if ((float)dist <= cutoffDistances.get(i).floatValue())
				return datas.get(i);
		throw new IllegalStateException("Distance ("+(float)dist+") not found and < maxCutoffDistance ("+(float)maxCutoffDist+")?");
	}

	@Override
	public boolean isDiscrete() {
		return true;
	}

	@Override
	public TectonicRegionType getTectonicRegionType() {
		return fallbackData.getTectonicRegionType();
	}

}
