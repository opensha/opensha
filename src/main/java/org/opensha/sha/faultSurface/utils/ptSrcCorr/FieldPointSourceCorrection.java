package org.opensha.sha.faultSurface.utils.ptSrcCorr;

import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection.Single;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.util.TectonicRegionType;

public class FieldPointSourceCorrection implements Single {

	@Override
	public SurfaceDistances getCorrectedDistance(Location siteLoc, PointSurface surf,
			TectonicRegionType trt, double mag, double horzDist) {
		// Wells and Coppersmith L(M) for "all" focal mechanisms
		// this correction comes from work by Ned Field and Bruce Worden
		// it assumes a vertically dipping straight fault with random
		// hypocenter and strike
		double rupLen =  Math.pow(10.0,-3.22+0.69*mag);
		double corrFactor = 0.7071 + (1.0-0.7071)/(1 + Math.pow(rupLen/(horzDist*0.87),1.1));
		double rJB = horzDist*corrFactor;
		
		double rJBsq = rJB*rJB;
		
		double depth = surf.getAveRupTopDepth();
		double rRup = Math.sqrt(depth * depth + rJBsq);
		
		double rSeis;
		if (depth < GriddedSurfaceUtils.SEIS_DEPTH)
			rSeis = Math.sqrt(GriddedSurfaceUtils.SEIS_DEPTH * GriddedSurfaceUtils.SEIS_DEPTH + rJBsq);
		else
			rSeis = rRup;
		
		double rX = 0d;
		
		return new SurfaceDistances.Precomputed(siteLoc, rRup, rJB, rSeis, rX);
	}
	
	@Override
	public String toString() {
		return PointSourceDistanceCorrections.FIELD.getName();
	}

}
