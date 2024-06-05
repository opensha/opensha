package org.opensha.sha.earthquake.faultSysSolution.erf;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

public class ProxyRupsFaultRuptureSource extends ProbEqkSource {
	
	private List<Region> polys = null;
	private List<RuptureSurface> origSectSurfaces;
	
	private List<ProbEqkRupture> rups;
	
	public ProxyRupsFaultRuptureSource(List<? extends FaultSection> origSects, List<List<FaultSection>> proxySects,
			double mag, double rate, double rake, double duration, boolean isPoisson, double gridSpacing, boolean aseisReducesArea) {
		this(origSects, proxySects, new DefaultXY_DataSet(new double[] {mag}, new double[] {rate}),
				rake, duration, isPoisson, gridSpacing, aseisReducesArea);
	}
	
	public ProxyRupsFaultRuptureSource(List<? extends FaultSection> origSects, List<List<FaultSection>> proxySects,
			XY_DataSet rupMFD, double rake, double duration, boolean isPoisson, double gridSpacing, boolean aseisReducesArea) {
		polys = new ArrayList<>(origSects.size());
		for (FaultSection sect : origSects) {
			Region poly = sect.getZonePolygon();
			if (poly == null) {
				polys = null;
				break;
			}
			polys.add(poly);
		}
		if (polys == null ) {
			// build sect surfaces
			origSectSurfaces = new ArrayList<>(origSects.size());
			for (FaultSection sect : origSects)
				origSectSurfaces.add(sect.getFaultSurface(gridSpacing));
		}
		
		Preconditions.checkState(rupMFD.size() >= 1);
		rups = new ArrayList<>(proxySects.size() * rupMFD.size());
		
		for (List<? extends FaultSection> proxyInstance : proxySects) {
			List<RuptureSurface> rupSurfs = new ArrayList<>(proxyInstance.size());
			for (FaultSection fltData : proxyInstance)
				rupSurfs.add(fltData.getFaultSurface(gridSpacing, false, aseisReducesArea));
			RuptureSurface surf;
			if (rupSurfs.size() == 1)
				surf = rupSurfs.get(0);
			else
				surf = new CompoundSurface(rupSurfs);
			for (Point2D mfdPt : rupMFD) {
				double mag = mfdPt.getX();
				double origRate = mfdPt.getY();
				double scaledRate = origRate / proxySects.size();
				
				double prob;
				if (isPoisson)
					prob = 1d-Math.exp(-scaledRate*duration);
				else
					// cannot exceed 1
					prob = Math.min(1d, scaledRate*duration);
				
				ProbEqkRupture probEqkRupture = new ProbEqkRupture();
				probEqkRupture.setAveRake(rake);
				probEqkRupture.setRuptureSurface(surf);
				probEqkRupture.setMag(mag);
				probEqkRupture.setProbability(prob);
				rups.add(probEqkRupture);
			}
		}
	}
	
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public LocationList getAllSourceLocs() {
		throw new UnsupportedOperationException("Not Supported");
	}

	@Override
	public RuptureSurface getSourceSurface() {
		throw new UnsupportedOperationException("Not Supported");
	}

	@Override
	public double getMinDistance(Site site) {
		double minDist = Double.POSITIVE_INFINITY;
		if (polys != null) {
			for (Region poly : polys) {
				minDist = Math.min(minDist, poly.distanceToLocation(site.getLocation()));
				if (minDist == 0d)
					return 0d;
			}
		} else
			for (RuptureSurface surf : origSectSurfaces)
				minDist = Math.min(minDist, surf.getQuickDistance(site.getLocation()));
		return minDist;
	}

	@Override
	public int getNumRuptures() {
		return rups.size();
	}

	@Override
	public ProbEqkRupture getRupture(int nRupture) {
		return rups.get(nRupture);
	}

}
