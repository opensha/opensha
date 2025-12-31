package org.opensha.sha.earthquake.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.PointSource.DataSubsamplingIndexTracker;
import org.opensha.sha.earthquake.PointSource.PoissonPointSourceData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class GridCellSuperSamplingPoissonPointSourceData extends SiteDistanceDependentPoissonPointSourceData {
	
	/**
	 * This enables distance-dependent supersampling of point sources. The cell represented by this point source will
	 * be divided up into a supersampled grid cell with at least <code>samplesPerKM</code> samples per kilometer.
	 * 
	 * <p>Three sampling levels are supported, each with decreasing computational demands:
	 * 
	 * <p>Full supersampling, up to the supplied <code>fullDist</code>, uses the full set of supersampled grid nodes
	 * and is most expensive but most accurate, especially nearby.
	 * 
	 * <p>Border sampling, up to the supplied <code>borderDist</code>, uses just the exterior grid nodes from
	 * the supersampled region, and is best when the site is a little further away and just sensitive to the size
	 * of the grid cell without integrating over the entire cell.
	 * 
	 * <p>Corder sampling, up to the supplied <code>cornerDist</code>, uses the corners of the grid cell as a crude
	 * approximation. This is fast (only 4 locations) and most appropriate at larger distances.
	 * 
	 * @param data original {@link PoissonPointSourceData}
	 * @param centerLoc location of original grid node
	 * @param gridCell cell that this grid node represents
	 * @param supersampleSettings supersampling settings
	 */
	public GridCellSuperSamplingPoissonPointSourceData(PoissonPointSourceData data, Location centerLoc, Region gridCell,
			GridCellSupersamplingSettings supersampleSettings) {
		this(data, centerLoc, gridCell, supersampleSettings.targetSpacingKM, supersampleSettings.fullDist,
				supersampleSettings.borderDist, supersampleSettings.cornerDist, supersampleSettings.applyToFinite);
	}
	
	/**
	 * This enables distance-dependent supersampling of point sources. The cell represented by this point source will
	 * be divided up into a supersampled grid cell with at least <code>samplesPerKM</code> samples per kilometer.
	 * 
	 * <p>Three sampling levels are supported, each with decreasing computational demands:
	 * 
	 * <p>Full supersampling, up to the supplied <code>fullDist</code>, uses the full set of supersampled grid nodes
	 * and is most expensive but most accurate, especially nearby.
	 * 
	 * <p>Border sampling, up to the supplied <code>borderDist</code>, uses just the exterior grid nodes from
	 * the supersampled region, and is best when the site is a little further away and just sensitive to the size
	 * of the grid cell without integrating over the entire cell.
	 * 
	 * <p>Corder sampling, up to the supplied <code>cornerDist</code>, uses the corners of the grid cell as a crude
	 * approximation. This is fast (only 4 locations) and most appropriate at larger distances.
	 * 
	 * @param data original {@link PoissonPointSourceData}
	 * @param centerLoc location of original grid node
	 * @param gridCell cell that this grid node represents
	 * @param targetSpacingKM target sample spacing (km)
	 * @param fullDist site-to-center distance (km) below which we should use the full resampled grid node
	 * @param borderDist site-to-center distance (km) below which we should use just the exterior of the resampled grid node
	 * @param cornerDist site-to-center distance (km) below which we should use all 4 corners of the grid cell
	 */
	public GridCellSuperSamplingPoissonPointSourceData(PoissonPointSourceData data, Location centerLoc, Region gridCell,
			double targetSpacingKM, double fullDist, double borderDist, double cornerDist, boolean applyToFinite) {
		if (!applyToFinite) {
			// see if all are finite and we can skip everything
			boolean allFinite = true;
			for (int r=0; allFinite && r<data.getNumRuptures(); r++)
				allFinite = data.isFinite(r);
			if (allFinite) {
				// skipping finite and all are finite
				init(centerLoc, List.of(data), List.of(Double.POSITIVE_INFINITY), data);
				return;
			}
		}
		Preconditions.checkState(gridCell.contains(centerLoc), "CenterLoc=%s not contined in cell region", centerLoc);
		double cellWidth = LocationUtils.horzDistanceFast(
				new Location(centerLoc.getLatitude(), gridCell.getMinLon()),
				new Location(centerLoc.getLatitude(), gridCell.getMaxLon()));
		double cellHeight = LocationUtils.horzDistanceFast(
				new Location(gridCell.getMinLat(), centerLoc.getLongitude()),
				new Location(gridCell.getMaxLat(), centerLoc.getLongitude()));
		double samplesPerWidth = cellWidth / targetSpacingKM;
		double samplesPerHeight = cellHeight / targetSpacingKM;
		
		int latSamples = (int)Math.round(samplesPerHeight);
		int lonSamples = (int)Math.round(samplesPerWidth);
		// even numbers are better because they don't co-locate
		if (latSamples % 2 == 1)
			latSamples++;
		if (lonSamples % 2 == 1)
			lonSamples++;
		int maxSamples = Integer.max(latSamples, lonSamples);
		Preconditions.checkState(maxSamples > 1,
				"maxSamples must be >1; was calculated as %s=max(%s,%s) for %s km sample spacing and cell dimensions of %s x %s km",
				maxSamples, latSamples, lonSamples, targetSpacingKM, cellWidth, cellHeight);
		
		GriddedRegion sampled = buildSampled(gridCell, latSamples, lonSamples);
		
		int count = 0;
		if (fullDist > 0d)
			count++;
		if (borderDist > 0d) {
			if (fullDist > 0d)
				Preconditions.checkState(borderDist > fullDist,
						"Border distance (%s) must be greater than full distance (%s)", borderDist, fullDist);
			count++;
		}
		if (cornerDist > 0d) {
			if (borderDist > 0d)
				Preconditions.checkState(cornerDist > borderDist,
						"Corner distance (%s) must be greater than border distance (%s)", cornerDist, borderDist);
			else if (fullDist > 0d)
					Preconditions.checkState(cornerDist > fullDist,
							"Corner distance (%s) must be greater than full distance (%s)", cornerDist, fullDist);
			count++;
		}
		
		List<PoissonPointSourceData> datas = new ArrayList<>(count);
		List<Double> cutoffDists = new ArrayList<>(count);
		
		if (fullDist > 0d) {
			datas.add(new LocSamplingWrapper(data, centerLoc, sampled.getNodeList(), applyToFinite));
			cutoffDists.add(fullDist);
		}
		
		if (borderDist > 0d) {
			datas.add(new LocSamplingWrapper(data, centerLoc, getExteriorNodes(sampled), applyToFinite));
			cutoffDists.add(borderDist);
		}
		
		if (cornerDist > 0d) {
			LocationList locs;
			if (gridCell.isRectangular()) {
				locs = getCorners(sampled);
			} else {
				// sample the border
				// bump up the resolution a bit from 4 corners to midpoints as well (if it were rectangular)
				locs = getResampledExterior(gridCell, 8);
			}
			datas.add(new LocSamplingWrapper(data, centerLoc, locs, applyToFinite));
			cutoffDists.add(cornerDist);
		}
		Preconditions.checkState(!datas.isEmpty(), "No supersampling distances encountered");
		super.init(centerLoc, datas, cutoffDists, data);
	}
	
	private static GriddedRegion buildSampled(Region gridCell, int latSamples, int lonSamples) {
		double latSpan = gridCell.getMaxLat() - gridCell.getMinLat();
		double latSpacing = latSpan/(double)latSamples;
		double lonSpan = gridCell.getMaxLon() - gridCell.getMinLon();
		double lonSpacing = lonSpan/(double)lonSamples;
		return new GriddedRegion(gridCell, latSpacing, lonSpacing,
				new Location(0.5*latSpacing, 0.5*lonSpacing));
	}
	
	private static LocationList getExteriorNodes(GriddedRegion sampled) {
		int numLats = sampled.getNumLatNodes();
		int[] minLonIndexes = new int[numLats];
		int[] maxLonIndexes = new int[numLats];
		for (int i=0; i<numLats; i++)
			minLonIndexes[i] = Integer.MAX_VALUE;
		int minLatIndex = Integer.MAX_VALUE;
		int maxLatIndex = -1;
		LocationList nodes = sampled.getNodeList();
		for (Location loc : nodes) {
			int latIndex = sampled.getLatIndex(loc);
			int lonIndex = sampled.getLonIndex(loc);
			Preconditions.checkState(latIndex >= 0 && lonIndex >= 0);
			// overall min
			minLatIndex = Integer.min(latIndex, minLatIndex);
			maxLatIndex = Integer.max(latIndex, maxLatIndex);
			// min/max lon for this lat
			minLonIndexes[latIndex] = Integer.min(minLonIndexes[latIndex], lonIndex);
			maxLonIndexes[latIndex] = Integer.max(maxLonIndexes[latIndex], lonIndex);
		}
//		System.out.println("LatIndex range: ["+minLatIndex+", "+maxLatIndex+"] for numLat="+numLats);
		LocationList locs = new LocationList();
		for (Location loc : nodes) {
			int latIndex = sampled.getLatIndex(loc);
			int lonIndex = sampled.getLonIndex(loc);
			if (latIndex == minLatIndex || latIndex == maxLatIndex
					|| lonIndex == minLonIndexes[latIndex] || lonIndex == maxLonIndexes[latIndex])
				locs.add(loc);
		}
		return locs;
	}
	
	private static LocationList getCorners(GriddedRegion sampled) {
		Preconditions.checkState(sampled.isRectangular());
		LocationList locs = new LocationList(4);
		locs.add(new Location(sampled.getMinGridLat(), sampled.getMinGridLon()));
		locs.add(new Location(sampled.getMinGridLat(), sampled.getMaxGridLon()));
		locs.add(new Location(sampled.getMaxGridLat(), sampled.getMinGridLon()));
		locs.add(new Location(sampled.getMaxGridLat(), sampled.getMaxGridLon()));
		return locs;
	}
	
	private static LocationList getResampledExterior(Region cell, int samples) {
		LocationList border = cell.getBorder();
		if (LocationUtils.areSimilar(border.first(), border.last())) {
			// remove the closing point (don't want any duplicates)
			border = new LocationList(border);
			border.remove(border.size()-1);
		}
		FaultTrace borderAsTrace = new FaultTrace(null);
		borderAsTrace.addAll(border);
		borderAsTrace.add(border.first()); // need to close it, will remove at the end
		LocationList locs = FaultUtils.resampleTrace(borderAsTrace, samples);
		locs.remove(locs.size()-1); // last is a duplicate
		return locs;
	}
	
	private static class LocSamplingWrapper implements PoissonPointSourceData {
		
		private PoissonPointSourceData data;
		private Location centerLoc;
		private LocationList samples;
		
		private int numNodes;
		private double nodeRateScalar;
		
		// this usually tracks data indexes and correction indexes, but here correction index is node index
		private DataSubsamplingIndexTracker indexes;
		private int numRuptures;

		public LocSamplingWrapper(PoissonPointSourceData data, Location centerLoc, LocationList samples, boolean applyToFinite) {
			this.data = data;
			this.centerLoc = centerLoc;
			this.samples = samples;
			
			this.numNodes = samples.size();
			Preconditions.checkState(numNodes < Short.MAX_VALUE);
			Preconditions.checkState(data.getNumRuptures() < Short.MAX_VALUE);
			this.nodeRateScalar = 1d/(double)numNodes;
			
			if (applyToFinite) {
				long numRuptures = data.getNumRuptures() * numNodes;
				indexes = PointSource.getDataCorrIndexTracker(numRuptures, data.getNumRuptures(), numNodes);
				for (int r=0; r<numRuptures; r++)
					indexes.set(r, r / numNodes, r % numNodes);
				this.numRuptures = (int)numRuptures;
			} else {
				// only apply to point surface ruptures
				long sampledNumRuptures = 0;
				int dataNumRuptures = data.getNumRuptures();
				for (int r=0; r<dataNumRuptures; r++) {
					if (data.isFinite(r))
						sampledNumRuptures++;
					else
						sampledNumRuptures += numNodes;
				}
				
				indexes = PointSource.getDataCorrIndexTracker(sampledNumRuptures, dataNumRuptures, numNodes);
				int rupIndex = 0;
				for (int dataIndex=0; dataIndex<dataNumRuptures; dataIndex++) {
					if (data.isFinite(dataIndex)) {
						indexes.set(rupIndex++, dataIndex, -1); // not sampled
					} else {
						for (int n=0; n<numNodes; n++)
							indexes.set(rupIndex++, dataIndex, n);
					}
				}
				Preconditions.checkState(rupIndex == sampledNumRuptures);
				this.numRuptures = (int)sampledNumRuptures;
			}
		}

		@Override
		public int getNumRuptures() {
			return numRuptures;
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return data.getMagnitude(indexes.getOriginalDataIndex(rupIndex));
		}

		@Override
		public double getAveRake(int rupIndex) {
			return data.getAveRake(indexes.getOriginalDataIndex(rupIndex));
		}

		@Override
		public double getRate(int rupIndex) {
			if (indexes.getSubsampleIndex(rupIndex) < 0)
				// not sampled (e.g., finite)
				return data.getRate(indexes.getOriginalDataIndex(rupIndex));
			return data.getRate(indexes.getOriginalDataIndex(rupIndex)) * nodeRateScalar;
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			RuptureSurface surf = data.getSurface(indexes.getOriginalDataIndex(rupIndex));
			if (indexes.getSubsampleIndex(rupIndex) < 0)
				// not sampled
				return surf;
			Location destLoc = samples.get(indexes.getSubsampleIndex(rupIndex));
			if (surf instanceof PointSurface) {
				// shortcut to avoid the more costly LocationVector creation
				return ((PointSurface)surf).getMoved(destLoc.lat, destLoc.lon);
			}
			LocationVector vector = LocationUtils.vector(centerLoc, destLoc);
			vector.setVertDistance(0d);
			return surf.getMoved(vector);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			return data.isFinite(indexes.getOriginalDataIndex(rupIndex));
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			Location hypo = data.getHypocenter(sourceLoc, rupSurface, indexes.getOriginalDataIndex(rupIndex));
			if (hypo == null || indexes.getSubsampleIndex(rupIndex) < 0)
				return hypo;
			
			// move it but keep the original depth
			Location nodeLoc = samples.get(indexes.getSubsampleIndex(rupIndex));
			if (hypo.depth != nodeLoc.depth)
				nodeLoc = new Location(nodeLoc.lat, nodeLoc.lon, hypo.depth);
			return nodeLoc;
		}

		@Override
		public TectonicRegionType getTectonicRegionType() {
			return data.getTectonicRegionType();
		}
		
	}
	
	public static void main(String[] args) {
//		Location centerLoc = new Location(0d, 0d);
		Location centerLoc = new Location(37d, -110d);
		double gridSpacing = 0.1d;
		Region gridCell = new Region(new Location(centerLoc.getLatitude() - 0.5*gridSpacing, centerLoc.getLongitude() - 0.5*gridSpacing),
				new Location(centerLoc.getLatitude() + 0.5*gridSpacing, centerLoc.getLongitude() + 0.5*gridSpacing));
		double spacing = 1d;
		double fullDist = 30d;
		double borderDist = 80d;
		double cornerDist = 160d;
		
		PoissonPointSourceData fakeData = new PoissonPointSourceData() {
			
			@Override
			public boolean isFinite(int rupIndex) {
				return false;
			}
			
			@Override
			public RuptureSurface getSurface(int rupIndex) {
				return null;
			}
			
			@Override
			public double getRate(int rupIndex) {
				return 1d;
			}
			
			@Override
			public int getNumRuptures() {
				return 1;
			}
			
			@Override
			public double getMagnitude(int rupIndex) {
				return 6d;
			}
			
			@Override
			public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
				return null;
			}
			
			@Override
			public double getAveRake(int rupIndex) {
				return 0;
			}

			@Override
			public TectonicRegionType getTectonicRegionType() {
				return TectonicRegionType.ACTIVE_SHALLOW;
			}
		};
		GridCellSuperSamplingPoissonPointSourceData sampler = new GridCellSuperSamplingPoissonPointSourceData(
				fakeData, centerLoc, gridCell, spacing, fullDist, borderDist, cornerDist, false);
		
		DecimalFormat distDF = new DecimalFormat("0.0");
		double prevDist = 0d;
		for (int i=0; i<sampler.datas.size(); i++) {
			double dist = sampler.cutoffDistances.get(i);
			System.out.println("["+distDF.format(prevDist)+", "+distDF.format(dist)+"] km");
			LocSamplingWrapper data = (LocSamplingWrapper) sampler.datas.get(i);
			System.out.println("\t"+data.numNodes+" nodes");
			prevDist = dist;
		}
	}
	
}