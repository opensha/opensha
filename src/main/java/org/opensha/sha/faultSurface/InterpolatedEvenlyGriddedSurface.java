package org.opensha.sha.faultSurface;

import java.util.concurrent.ExecutionException;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ExceptionUtils;

import scratch.UCERF3.utils.IDPairing;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * This is an EvenlyGriddedSurface that is interpolated to become a higher resolution
 * version a given surface.
 * @author kevin
 *
 */
public class InterpolatedEvenlyGriddedSurface extends
		AbstractEvenlyGriddedSurface {
	
	private EvenlyGriddedSurface loResSurf;
	private int discrPnts;
	
	private static final int maxCacheSize = 5000;
	private LoadingCache<IDPairing, Location> locCache;
	
	public InterpolatedEvenlyGriddedSurface(EvenlyGriddedSurface loResSurf, double hiResSpacing) {
		Preconditions.checkState(loResSurf.isGridSpacingSame());
		Preconditions.checkArgument(loResSurf.getGridSpacingAlongStrike() > hiResSpacing);
		
		this.loResSurf = loResSurf;
		double discrPntsDouble = loResSurf.getGridSpacingAlongStrike()/hiResSpacing;
		Preconditions.checkState((float)discrPntsDouble == (float)Math.floor(discrPntsDouble),
				"can't evenly divide los res spacing by high res spacing");
		discrPnts = (int)discrPntsDouble;
		
		int origRows = loResSurf.getNumRows();
		int origCols = loResSurf.getNumCols();
		this.numRows = (origRows-1)*discrPnts+1;
		this.numCols = (origCols-1)*discrPnts+1;
		
		size = ( long ) numRows * ( long ) numCols;
		data = null;
		gridSpacingAlong = hiResSpacing;
		gridSpacingDown = hiResSpacing;
		sameGridSpacing = true;
		
		locCache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build(new LocCacheLoader());
	}
	
	public EvenlyGriddedSurface getLowResSurface() {
		return loResSurf;
	}

	@Override
	public double getAveStrike() {
		return loResSurf.getAveStrike();
	}
	
	@Override
	public double getAveRupTopDepth() {
		return loResSurf.getAveRupTopDepth();
	}
	
	@Override
	public double getAveDipDirection() {
		return loResSurf.getAveDipDirection();
	}
	
	@Override
	public double getAveDip() {
		return loResSurf.getAveDip();
	}
	
	@Override
	public Location get(int row, int column) {
		try {
			return locCache.get(new IDPairing(row, column));
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	private class LocCacheLoader extends CacheLoader<IDPairing, Location> {

		@Override
		public Location load(IDPairing pair) throws Exception {
			int row = pair.getID1();
			int column = pair.getID2();
			
			int origRow = row/discrPnts;
			int origCol = column/discrPnts;
			int rowI = row % discrPnts;
			int colI = column % discrPnts;
			
//			System.out.println("Interp get: row="+row+", origRow="+origRow+", rowI="+rowI);
//			System.out.println("\tcol="+column+", origCol="+origCol+", colI="+colI);
//			System.out.println("\torigRows="+loResSurf.getNumRows()+", origCols="+loResSurf.getNumCols());
			
			Location topLeftLoc = loResSurf.get(origRow, origCol);
//			Location botRightLoc = loResSurf.get(origRow+1, origCol+1);
			double horzDist, horzAz;
			if (origCol+1 == loResSurf.getNumCols()) {
				horzDist = 0;
				horzAz = 0;
			} else {
				Location topRightLoc = loResSurf.get(origRow, origCol+1);
				horzDist = LocationUtils.horzDistance(topLeftLoc, topRightLoc);
				horzAz = LocationUtils.azimuthRad(topLeftLoc, topRightLoc);
			}
			
			double vertDist, vertAz, depthDelta;
			if (origRow+1 == loResSurf.getNumRows()) {
				vertDist = 0;
				vertAz = 0;
				depthDelta = 0;
			} else {
				Location botLeftLoc = loResSurf.get(origRow+1, origCol);
				vertDist = LocationUtils.horzDistance(topLeftLoc, botLeftLoc);
				vertAz = LocationUtils.azimuthRad(topLeftLoc, botLeftLoc);
				depthDelta = botLeftLoc.getDepth()-topLeftLoc.getDepth();
			}
			
			double relativeVertPos = (double)rowI/(double)discrPnts;
			double relativeHorzPos = (double)colI/(double)discrPnts;
			
			// start top left
			Location loc = topLeftLoc;
			// move to the right
			loc = LocationUtils.location(loc, horzAz, horzDist*relativeHorzPos);
			// move down dip
			if ((float)vertDist > 0f)
				loc = LocationUtils.location(loc, vertAz, vertDist*relativeVertPos);
			// now actually move down
			return new Location(loc.getLatitude(), loc.getLongitude(), loc.getDepth()+depthDelta*relativeVertPos);
		}
		
	}

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		throw new UnsupportedOperationException("Not supported for Interpolated Surface");
	}

}
