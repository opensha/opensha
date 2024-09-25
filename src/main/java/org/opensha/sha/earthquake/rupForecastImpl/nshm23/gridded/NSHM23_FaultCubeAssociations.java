package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/**
 * This class handles aassociations between cubes and faults, and then aggregates those cube associations to grid nodes.
 * 
 * @author kevin
 *
 */
public class NSHM23_FaultCubeAssociations implements FaultCubeAssociations, ArchivableModule {
	
	private static final boolean D = false;
	
	/*
	 * Inputs
	 */
	private List<? extends FaultSection> subSects;
	private CubedGriddedRegion cgr;
	private GriddedRegion griddedRegion;
	private double maxFaultNuclDist;
	
	/*
	 * Cube outputs
	 */
	// for each cube, an array of mapped section indexes (or null if none)
	private int[][] sectsAtCubes;
	// for each cube, an array of mapped section distance-fraction wts (where the wts represent the fraction of
	// seismicity assigned to the fault section below the min seismo mag), in the same order as sectsAtCubes
	private double[][] sectOrigDistWeightsAtCubes;
	// same as sectOrigDistWeightsAtCubes, but scaled to account for other faults also being mapped to individual cubes
	private double[][] sectScaledDistWeightsAtCubes;
	// this is the total wt for each section summed from sectOrigDistWeightsAtCubes, not scaled for overlap
	private double[] totOrigDistWtsAtCubesForSectArray;
	// this is the total wt for each section summed from sectDistWeightsAtCubes (divide the wt directly above
	// by this value to get the nucleation fraction for the section in the associated cube) 
	private double[] totScaledDistWtsAtCubesForSectArray;
	// fraction of section nucleation (after being spread to corresponding cubes) in the region
	private double[] fractSectsInRegion;
	
	/*
	 * Grid cell outputs
	 */
	private CubeToGridNodeAggregator cubeGridAggregator;

	public NSHM23_FaultCubeAssociations(FaultSystemRupSet rupSet, CubedGriddedRegion cgr, double maxFaultNuclDist) {
		this(rupSet.getFaultSectionDataList(), cgr, maxFaultNuclDist);
	}

	public NSHM23_FaultCubeAssociations(List<? extends FaultSection> subSects, CubedGriddedRegion cgr, double maxFaultNuclDist) {
		validateSubSects(subSects);
		this.subSects = subSects;
		this.cgr = cgr;
		this.maxFaultNuclDist = maxFaultNuclDist;
		this.griddedRegion = cgr.getGriddedRegion();
		
		// this calculates sectsAtCubes, sectDistWeightsAtCubes, and totDistWtsAtCubesForSectArray
		cacheCubeSectMappings();
		
		cubeGridAggregator = new CubeToGridNodeAggregator(this);
	}
	
	private static void validateSubSects(List<? extends FaultSection> subSects) {
		Preconditions.checkState(!subSects.isEmpty(), "No subsections supplied");
		for (int s=0; s<subSects.size(); s++)
			Preconditions.checkState(subSects.get(s).getSectionId() == s, "Subsections not in order or not 0-indexed");
	}
	
	/*
	 * Cube-related methods
	 */
	
	public CubedGriddedRegion getCubedGriddedRegion() {
		return cgr;
	}
	
	@Override
	public int[] getSectsAtCube(int cubeIndex) {
		return sectsAtCubes[cubeIndex];
	}
	
	@Override
	public double[] getScaledSectDistWeightsAtCube(int cubeIndex) {
		return sectScaledDistWeightsAtCubes[cubeIndex];
	}
	
	@Override
	public double getTotalScaledDistWtAtCubesForSect(int sectIndex) {
		return totScaledDistWtsAtCubesForSectArray[sectIndex];
	}

	@Override
	public double[] getOrigSectDistWeightsAtCube(int cubeIndex) {
		return sectOrigDistWeightsAtCubes[cubeIndex];
	}

	@Override
	public double getTotalOrigDistWtAtCubesForSect(int sectIndex) {
		return totOrigDistWtsAtCubesForSectArray[sectIndex];
	}
	
	@Override
	public double getSectionFractInRegion(int sectIndex) {
		return fractSectsInRegion[sectIndex];
	}

	@Override
	public String getName() {
		return "NSHM23 Fault Cube Associations";
	}
	
	/*
	 * Grid-related methods
	 */

	@Override
	public Map<Integer, Double> getNodeExtents() {
		return cubeGridAggregator.getNodeExtents();
	}
	
	@Override
	public double getNodeFraction(int nodeIdx) {
		return cubeGridAggregator.getNodeFraction(nodeIdx);
	}
	
	@Override
	public Map<Integer, Double> getScaledNodeFractions(int sectIdx) {
		return cubeGridAggregator.getScaledNodeFractions(sectIdx);
	}
	
	@Override
	public Map<Integer, Double> getScaledSectFracsOnNode(int nodeIdx) {
		return cubeGridAggregator.getScaledSectFracsOnNode(nodeIdx);
	}
	
	@Override
	public Map<Integer, Double> getNodeFractions(int sectIdx) {
		return cubeGridAggregator.getNodeFractions(sectIdx);
	}
	
	@Override
	public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
		return cubeGridAggregator.getSectionFracsOnNode(nodeIdx);
	}

	@Override
	public GriddedRegion getRegion() {
		return griddedRegion;
	}

	@Override
	public Collection<Integer> sectIndices() {
		return cubeGridAggregator.sectIndices();
	}
	
//	/**
//	 * This assumes the trace represents a surface projection of top edge of surface (and not projected
//	 * up-dip when upper seismogenic depth is nonzero)
//	 * 
//	 * TODO: deal with surface projection traces
//	 * @param faultSection
//	 * @param distance
//	 * @param accountForDip
//	 * @return
//	 */
//	public static Region getSectionPolygonRegion(FaultSection faultSection, double distance, boolean accountForDip) {
//		LocationList trace = faultSection.getFaultTrace();
//		Preconditions.checkArgument(trace.size() > 1);
//		double dipDir = faultSection.getDipDirection();
//		double distPlusDip = distance;
//		if(accountForDip)
//			distPlusDip += faultSection.getOrigDownDipWidth() * Math.cos(faultSection.getAveDip() * Math.PI / 180d);
//		LocationList locList = new LocationList();
//		locList.add(trace.get(0));
//		LocationVector v = new LocationVector(dipDir, distPlusDip, 0);
//
//		for (int i = 0; i < trace.size(); i++) {
//			locList.add(LocationUtils.location(trace.get(i), v));
//		}
//		locList.add(trace.get(trace.size()-1));
//		double reverseDipDir = (dipDir + 180) % 360;
//		v = new LocationVector(reverseDipDir, distance, 0);
//		for (int i = trace.size()-1; i >= 0; i--) {
//			locList.add(LocationUtils.location(trace.get(i), v));
//		}
//		return new Region(locList, BorderType.MERCATOR_LINEAR);
//	}
	
	private void cacheCubeSectMappings() {
		// do the mappings in parallel
		ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
		
		Stopwatch watch = Stopwatch.createStarted();
		List<Future<SectCubeMapper>> futures = new ArrayList<>(subSects.size());
		for (int s=0; s<subSects.size(); s++)
			futures.add(exec.submit(new SectCubeMapper(s)));
		
		System.out.println("Waiting on "+futures.size()+" fault section to cube mapping futures");
		
		// store these as arrays, which will be more memory efficient than lists
		// there's a slight cost in rebuilding arrays if a cube is mapped to multiple sections, but that's probably
		// worth the memory overhead (especially as most cubes will be mapped to 0 or 1 section)
		sectsAtCubes = new int[cgr.getNumCubes()][];
		// this is only needed temporarily
		double[][] sectDistsAtCubes = new double[cgr.getNumCubes()][];
		totOrigDistWtsAtCubesForSectArray = new double[subSects.size()];
		totScaledDistWtsAtCubesForSectArray = new double[subSects.size()];
		fractSectsInRegion = new double[subSects.size()];
		
		for (Future<SectCubeMapper> future : futures) {
			try {
				SectCubeMapper mapper = future.get();
				if (mapper != null) {
					// this section is mapped to cubes in this region
					for (int mappedCube : mapper.mappedCubeDistances.keySet()) {
						float distance = mapper.mappedCubeDistances.get(mappedCube);
						if (sectsAtCubes[mappedCube] == null) {
							// simple, first mapping
							sectsAtCubes[mappedCube] = new int[] {mapper.sectIndex};
							sectDistsAtCubes[mappedCube] = new double[] {distance};
						} else {
							// mapped to multiple sections, need to grow it
							int prevLen = sectsAtCubes[mappedCube].length;
							sectsAtCubes[mappedCube] = Arrays.copyOf(sectsAtCubes[mappedCube], prevLen+1);
							sectDistsAtCubes[mappedCube] = Arrays.copyOf(sectDistsAtCubes[mappedCube], prevLen+1);
							sectsAtCubes[mappedCube][prevLen] = mapper.sectIndex;
							sectDistsAtCubes[mappedCube][prevLen] = distance;
						}
					}
					totScaledDistWtsAtCubesForSectArray[mapper.sectIndex] = mapper.totWeight;
					totOrigDistWtsAtCubesForSectArray[mapper.sectIndex] = mapper.totWeight;
					fractSectsInRegion[mapper.sectIndex] = mapper.fractInRegion;
				}
			} catch (InterruptedException | ExecutionException e) {
				exec.shutdown();
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		exec.shutdown();
		
		watch.stop();
		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+" secs to map sections to cubes");
		
		watch.reset().start();
		// now convert to weights
		sectOrigDistWeightsAtCubes = new double[cgr.getNumCubes()][];
		sectScaledDistWeightsAtCubes = new double[cgr.getNumCubes()][];
		
		// could easily parallelize this, but it's quite fast
		for(int c=0;c<cgr.getNumCubes();c++) {
			int[] sects = sectsAtCubes[c];
			if (sects == null)
				// this cube is not mapped to any sections
				continue;
			double[] dists = sectDistsAtCubes[c];
			
			int numSect = sects.length;
			double[] weights = new double[numSect];
			double wtSum = 0;
			for(int s=0;s<numSect;s++) {
				double dist = dists[s];
				weights[s] = getDistWt(dist);
				wtSum += weights[s];
			}
			double[] scaledWeights;
			if(wtSum>1.0) {
				scaledWeights = new double[numSect];
				for(int s=0;s<numSect;s++) {
					scaledWeights[s] = weights[s]/wtSum;
					// reduce this to still track sum of scaled weights
					totScaledDistWtsAtCubesForSectArray[sects[s]] += (scaledWeights[s]-weights[s]);
				}
			} else {
				scaledWeights = weights;
			}
			
			sectOrigDistWeightsAtCubes[c] = weights;
			sectScaledDistWeightsAtCubes[c] = scaledWeights;
		}
		
		watch.stop();
		System.out.println("Took "+(watch.elapsed(TimeUnit.MILLISECONDS)/1000f)+" secs to build cube/section weights");
	}
	
	/**
	 * Memory efficient 
	 * @author kevin
	 *
	 */
	private class SectCubeMapper implements Callable<SectCubeMapper> {
		/**
		 * Section index
		 */
		private int sectIndex;
		private double totWeight;
		private Map<Integer, Float> mappedCubeDistances;
		private double fractInRegion;
		
		public SectCubeMapper(int sectIndex) {
			this.sectIndex = sectIndex;
		}

		@Override
		public SectCubeMapper call() throws Exception {
			FaultSection fltSection = subSects.get(sectIndex);
			
			// will be non null if this is a proxy section with an associated polygon
			Region proxyReg = null;
			if (fltSection.isProxyFault())
				proxyReg = fltSection.getZonePolygon();
			
			// first see if we can skip this section
			boolean skip = true;
			FaultTrace trace = fltSection.getFaultTrace();
			for (Location traceLoc : trace) {
				if (griddedRegion.contains(traceLoc)) {
					skip = false;
					break;
				}
			}
			if (skip) {
				// might be able to skip, no trace locations inside the region
				
				// see if any trace points are within a conservative distance from the region, calculated by summing:
				// * 2 x the max fault nucleation distance
				// * 2 x the trace length (in case there are long spans with no trace locations
				// * 2 x the horizontal distance from the trace to the bottom of the fault if dipping
				double regCheckDist = 2d*maxFaultNuclDist + 2d*trace.getTraceLength();
				if (fltSection.getAveDip() != 90d)
					// increase the check distance to account for the possibility that this fault dips toward the region
					regCheckDist += 2d*Math.cos(Math.toRadians(fltSection.getAveDip()))*fltSection.getAveLowerDepth();
				
				for (Location traceLoc : trace) {
					if (griddedRegion.distanceToLocation(traceLoc) < regCheckDist) {
						// we're at least close to the region, need to go forward with a complete check
						skip = false;
						break;
					}
				}
				
				if (skip && proxyReg != null) {
					for (Location gridLoc : griddedRegion.getNodeList()) {
						if (proxyReg.distanceToLocation(gridLoc) < regCheckDist) {
							skip = false;
							break;
						}
					}
				}
				if (skip)
					// can actually skip it
					return null;
			}
			
			mappedCubeDistances = new HashMap<>();
			totWeight = 0d;
			
			boolean aseisReducesArea = true; // TODO

			double depthDiscr = cgr.getCubeDepthDiscr();
			List<Double> depths = new ArrayList<>();
			for(double depth = depthDiscr/2;depth<cgr.getMaxDepth();depth+=depthDiscr)
				depths.add(depth);
			
			if (proxyReg != null) {
				// proxy section with an associated polygon
				// fully associate within that polygon, ramp at the edges (including below)
				
				// build buffered polygon
				Location topRight = new Location(proxyReg.getMaxLat(), proxyReg.getMaxLon());
				Location botLeft = new Location(proxyReg.getMinLat(), proxyReg.getMinLon());
				topRight = LocationUtils.location(topRight, Math.PI*0.25, maxFaultNuclDist);
				botLeft = LocationUtils.location(botLeft, Math.PI*1.25, maxFaultNuclDist);
				Region bufferedPoly = new Region(botLeft, topRight);
				GriddedRegion griddedPolygonReg = new GriddedRegion(bufferedPoly, cgr.getCubeLatLonSpacing(), cgr.getCubeLocationForIndex(0));
				
				double upperDepth = aseisReducesArea ? fltSection.getReducedAveUpperDepth() : fltSection.getOrigAveUpperDepth();
				double lowerDepth = fltSection.getAveLowerDepth();
				
				for(int i=0;i<griddedPolygonReg.getNumLocations();i++) {
					Location loc = griddedPolygonReg.getLocation(i);
					double horzDist;
					if (proxyReg.contains(loc))
						horzDist = 0d;
					else
						horzDist = proxyReg.distanceToLocation(loc);
					if (horzDist > maxFaultNuclDist)
						continue;
					for(double depth : depths) {
						double vertDist;
						if (depth <= upperDepth)
							vertDist = upperDepth - depth;
						else if (depth >= lowerDepth)
							vertDist = depth = lowerDepth;
						else
							vertDist = 0d;
						double dist;
						if (vertDist == 0d)
							dist = horzDist;
						else if (horzDist == 0d)
							dist = vertDist;
						else
							dist = Math.hypot(horzDist, vertDist); // sqrt(horzDist^2 + vertDist^2)
						if(dist<=maxFaultNuclDist) { 
							totWeight += getDistWt(dist); // this will includes cubes outside the region where the section could nucleate
							int cubeIndex = cgr.getCubeIndexForLocation(new Location(loc.getLatitude(),loc.getLongitude(),depth));
							if(cubeIndex>=0) {// make sure it's in the region
								mappedCubeDistances.put(cubeIndex, (float)dist);
							}
						}
					}
				}
			} else {
				RuptureSurface sectSurf = fltSection.getFaultSurface(0.25, false, aseisReducesArea);
				
//				Region fltPolygon = getSectionPolygonRegion(fltSection, maxFaultNuclDist, true);
				Region fltPolygon = NSHM23_FaultPolygonBuilder.buildPoly(fltSection, sectSurf, aseisReducesArea, maxFaultNuclDist);
				
//				System.out.println(fltSection.getName()+"\nsectIndex = "+sectIndex+"\ndip = "+fltSection.getAveDip()
//				+"\ndipDir = "+fltSection.getDipDirection()+
//				"\nupSeisDep = "+fltSection.getOrigAveUpperDepth()+
//				"\nddw = "+fltSection.getOrigDownDipWidth()+"\nTrace:\n");
//				for(Location loc:fltSection.getFaultTrace())
//					System.out.println(loc);
//				System.out.println("\nPolygonRegion:\n");
//				for(Location loc:fltPolygon.getBorder())
//					System.out.println(loc);
				
//				System.out.println("\nGriddedSurface:\n");
//				RuptureSurface sectSurf = fltSection.getFaultSurface(1.0, false, false);
//				for(int i=0;i<sectSurf.getEvenlyDiscretizedNumLocs();i++) {
//					System.out.println(sectSurf.getEvenlyDiscretizedLocation(i));
//				}
//				QuadSurface sectSurf = new QuadSurface(fltSection,false);

				GriddedRegion griddedPolygonReg = new GriddedRegion(fltPolygon, cgr.getCubeLatLonSpacing(), cgr.getCubeLocationForIndex(0));
				double testWt=0;
				for(int i=0;i<griddedPolygonReg.getNumLocations();i++) {
					Location loc = griddedPolygonReg.getLocation(i);
					for(double depth : depths) {
						Location loc2 = new Location(loc.getLatitude(),loc.getLongitude(),depth);
						double dist = LocationUtils.distanceToSurfFast(loc2, sectSurf);
						if(dist<=maxFaultNuclDist) { 
							totWeight += getDistWt(dist); // this will includes cubes outside the region where the section could nucleate
							int cubeIndex = cgr.getCubeIndexForLocation(loc2);
							if(cubeIndex>=0) {// make sure it's in the region
								mappedCubeDistances.put(cubeIndex, (float)dist);
								testWt += getDistWt(dist);
							}
						}
					}
				}
				
				fractInRegion = testWt/totWeight;
				if((float)fractInRegion != 1.0) {
//					sectionsThatNucleateOutsideRegionList.add(sectIndex);
					if(D) System.out.println((float)(1d-fractInRegion)+" of "+sectIndex+". "
							+subSects.get(sectIndex).getName()+ " nucleates outside the region");
				}
			}
			
			if (mappedCubeDistances.isEmpty())
				return null;
			return this;
		}
	}

	public double getDistWt(double dist) {
		return (maxFaultNuclDist-dist)/maxFaultNuclDist;
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return FaultCubeAssociations.Precomputed.class;
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		new FaultCubeAssociations.Precomputed(this).writeToArchive(output, entryPrefix);
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		throw new IllegalStateException("Should be serialized back in as Precomputed");
	}

	@Override
	public int getNumCubes() {
		return cgr.getNumCubes();
	}

}
