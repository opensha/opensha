package org.opensha.sha.faultSurface.utils;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.util.TectonicRegionType;

public interface PointSourceDistanceCorrection {
	
	/**
	 * This returns a {@link WeightedList} of corrected {@link SurfaceDistances} for the a point surface at the given
	 * location, which is known to be the given horizontal site-source (rEpicenter) distance away.
	 * 
	 * @param siteLoc the site location that we're calculating distances to
	 * @param surf the point surface, specifying location and depth at a minimum, but potentially also dip, DDW, and length
	 * @param trt point source tectonic region type
	 * @param mag the magnitude of the point source rupture for which we're computing distances 
	 * @param horzDist the horizontal distance in km between the site and the rupture epicenter
	 * @return {@link WeightedList} of corrected {@link SurfaceDistances}
	 */
	public WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc,
			PointSurface surf, TectonicRegionType trt, double mag, double horzDist);
	
	/**
	 * This returns a {@link WeightedList} of {@link PointSurface.DistanceCorrected} for the a point surface at the given
	 * location, which is known to be the given horizontal site-source (rEpicenter) distance away. The returned surfaces
	 * are unique to the passed in location and have all distances precomputed.
	 * 
	 * @param siteLoc the site location that we're calculating distances to
	 * @param surf the point surface, specifying location and depth at a minimum, but potentially also dip, DDW, and length
	 * @param trt point source tectonic region type
	 * @param mag the magnitude of the point source rupture for which we're computing distances 
	 * @param horzDist the horizontal distance in km between the site and the rupture epicenter
	 * @return {@link WeightedList} of {@link PointSurface.DistanceCorrected}
	 */
	public default WeightedList<PointSurface.DistanceCorrected> getCorrectedSurfaces(Location siteLoc,
			PointSurface surf, TectonicRegionType trt, double mag, double horzDist) {
		WeightedList<SurfaceDistances> dists = getCorrectedDistances(siteLoc, surf, trt, mag, horzDist);
		List<WeightedValue<PointSurface.DistanceCorrected>> surfs = new ArrayList<>(dists.size());
		for (int i=0; i<dists.size(); i++)
			surfs.add(new WeightedValue<>(new PointSurface.DistanceCorrected(surf, siteLoc, dists.getValue(i)), dists.getWeight(i)));
		return WeightedList.of(surfs);
	}
	
	public interface Single extends PointSourceDistanceCorrection {
		
		/**
		 * This returns a single corrected {@link SurfaceDistances} for the a point surface at the given
		 * location, which is known to be the given horizontal site-source (rEpicenter) distance away.
		 * 
		 * @param siteLoc the site location that we're calculating distances to
		 * @param surf the point surface, specifying location and depth at a minimum, but potentially also dip, DDW, and length
		 * @param trt point source tectonic region type
		 * @param mag the magnitude of the point source rupture for which we're computing distances 
		 * @param horzDist the horizontal distance in km between the site and the rupture epicenter
		 * @return corrected {@link SurfaceDistances}
		 */
		public SurfaceDistances getCorrectedDistance(Location siteLoc, PointSurface surf, TectonicRegionType trt,
				double mag, double horzDist);
		
		/**
		 * This returns a single {@link PointSurface.DistanceCorrected} for the a point surface at the given
		 * location, which is known to be the given horizontal site-source (rEpicenter) distance away. The returned
		 * surface is unique to the passed in location and has all distances precomputed.
		 * 
		 * @param siteLoc the site location that we're calculating distances to
		 * @param surf the point surface, specifying location and depth at a minimum, but potentially also dip, DDW, and length
		 * @param trt point source tectonic region type
		 * @param mag the magnitude of the point source rupture for which we're computing distances 
		 * @param horzDist the horizontal distance in km between the site and the rupture epicenter
		 * @return {@link PointSurface.DistanceCorrected}
		 */
		public default PointSurface.DistanceCorrected getCorrectedSurface(Location siteLoc,
				PointSurface surf, TectonicRegionType trt, double mag, double horzDist) {
			SurfaceDistances dist = getCorrectedDistance(siteLoc, surf, trt, mag, horzDist);
			return new PointSurface.DistanceCorrected(surf, siteLoc, dist);
		}
		
		/**
		 * This returns a single on-the-fly {@link PointSurface.DistanceCorrecting} for the a point surface and this
		 * correction. The returned surface will compute corrected distances on the fly.
		 * 
		 * @param surf the point surface, specifying location and depth at a minimum, but potentially also dip, DDW, and length
		 * @param trt point source tectonic region type
		 * @param mag the magnitude of the point source rupture for which we're computing distances 
		 * @return {@link PointSurface.DistanceCorrecting}
		 */
		public default PointSurface.DistanceCorrecting getCorrectingSurface(
				PointSurface surf, TectonicRegionType trt, double mag) {
			return new PointSurface.DistanceCorrecting(surf, this, trt, mag);
		}

		@Override
		default WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc, PointSurface surf,
				TectonicRegionType trt, double mag, double horzDist) {
			return WeightedList.evenlyWeighted(getCorrectedDistance(siteLoc, surf, trt, mag, horzDist));
		}
		
	}

}
