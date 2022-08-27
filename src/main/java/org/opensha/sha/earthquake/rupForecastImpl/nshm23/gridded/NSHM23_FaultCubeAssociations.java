package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;

/**
 * This class handles aassociations between cubes and faults, and then aggregates those cube associations to grid nodes.
 * 
 * @author kevin
 *
 */
public class NSHM23_FaultCubeAssociations implements FaultGridAssociations {
	
	private static final boolean D = false;
	
	/*
	 * Inputs
	 */
	private FaultSystemRupSet rupSet;
	private CubedGriddedRegion cgr;
	private GriddedRegion griddedRegion;
	private double maxFaultNuclDist;
	
	private List<NSHM23_FaultCubeAssociations> regionalAssociations;
	
	/*
	 * Cube outputs
	 */
	// for each cube, an array of mapped section indexes (or null if none)
	private int[][] sectsAtCubes;
	// for each cube, an array of mapped section distance-fraction wts (where the wts represent the fraction of
	// seismicity assigned to the fault section below the min seismo mag), in the same order as sectsAtCubes
	private double[][] sectDistWeightsAtCubes;
	// this is the total wt for each section summed from sectDistWeightsAtCubes (divide the wt directly above
	// by this value to get the nucleation fraction for the section in the associated cube) 
	private double[] totDistWtsAtCubesForSectArray;
	
	/*
	 * Grid cell outputs
	 */
	private ImmutableSet<Integer> sectIndices;
	
	private ImmutableMap<Integer, Double> nodeExtents;
	
	// both are Table<SubSectionID, NodeIndex, Value>
	//
	// the percentage of each node spanned by each fault sub-section
	private ImmutableTable<Integer, Integer, Double> nodeInSectPartic;
	// same as above, scaled with percentage scaled to account for
	// multiple overlapping sub-sections
	private ImmutableTable<Integer, Integer, Double> sectInNodePartic;

	public NSHM23_FaultCubeAssociations(FaultSystemRupSet rupSet, CubedGriddedRegion cgr, double maxFaultNuclDist) {
		this.rupSet = rupSet;
		this.cgr = cgr;
		this.maxFaultNuclDist = maxFaultNuclDist;
		this.griddedRegion = cgr.getGriddedRegion();
		
		// this calculates sectsAtCubes, sectDistWeightsAtCubes, and totDistWtsAtCubesForSectArray
		cacheCubeSectMappings();
		
		aggregateToGridNodes();
	}
	
	public NSHM23_FaultCubeAssociations(FaultSystemRupSet rupSet, CubedGriddedRegion cgr,
			List<NSHM23_FaultCubeAssociations> regionalAssociations) {
		this.rupSet = rupSet;
		this.cgr = cgr;
		this.griddedRegion = cgr.getGriddedRegion();
		
		sectsAtCubes = new int[cgr.getNumCubes()][];
		sectDistWeightsAtCubes = new double[sectsAtCubes.length][];
		
		int numMapped = 0;
		int nodeCount = griddedRegion.getNodeCount();
		for (int gridIndex=0; gridIndex<nodeCount; gridIndex++) {
			Location loc = griddedRegion.locationForIndex(gridIndex);
			NSHM23_FaultCubeAssociations match = null;
			int matchIndex = -1;
			for (NSHM23_FaultCubeAssociations prov : regionalAssociations) {
				int myIndex = prov.griddedRegion.indexForLocation(loc);
				if (myIndex >= 0) {
					Preconditions.checkState(match == null,
							"TODO: don't yet support grid locations that map to multiple sub-regions");
					match = prov;
					matchIndex = myIndex;
				}
			}
			if (match != null) {
				// map all of the cubes for this grid node
				numMapped++;
				int[] newCubeIndexes = cgr.getCubeIndicesForGridCell(gridIndex);
				int[] matchCubeIndexes = match.cgr.getCubeIndicesForGridCell(matchIndex);
				Preconditions.checkState(newCubeIndexes.length == matchCubeIndexes.length);
				for (int i=0; i<matchCubeIndexes.length; i++) {
					sectsAtCubes[newCubeIndexes[i]] = match.sectsAtCubes[matchCubeIndexes[i]];
					sectDistWeightsAtCubes[newCubeIndexes[i]] = match.sectDistWeightsAtCubes[matchCubeIndexes[i]];
				}
			} else {
				// do nothing
			}
		}
		System.out.println("Mapped "+numMapped+"/"+nodeCount+" model region fault associations locations to sub-region grid locations");
		totDistWtsAtCubesForSectArray = new double[rupSet.getNumSections()];
		for (NSHM23_FaultCubeAssociations regional : regionalAssociations)
			for (int s=0; s<totDistWtsAtCubesForSectArray.length; s++)
				totDistWtsAtCubesForSectArray[s] += regional.totDistWtsAtCubesForSectArray[s];
		
		this.regionalAssociations = regionalAssociations;
	}
	
	private void aggregateToGridNodes() {
		// now collapse them to grid nodes
		HashSet<Integer> sectIndices = new HashSet<>();
		ImmutableMap.Builder<Integer, Double> nodeExtentsBuilder = ImmutableMap.builder();
		ImmutableTable.Builder<Integer, Integer, Double> nodeInSectParticBuilder = ImmutableTable.builder();
		ImmutableTable.Builder<Integer, Integer, Double> sectInNodeParticBuilder = ImmutableTable.builder();

		for (int nodeIndex=0; nodeIndex<griddedRegion.getNodeCount(); nodeIndex++) {
			int numCubes = 0;
			Map<Integer, Double> sectFracts = null;
			Map<Integer, Double> sectOrigFracts = null;
			for (int cubeIndex : cgr.getCubeIndicesForGridCell(nodeIndex)) {
				numCubes++;
				int[] sects = sectsAtCubes[cubeIndex];
				if (sects != null) {
					if (sectFracts == null) {
						sectFracts = new HashMap<>();
						sectOrigFracts = new HashMap<>();
					}
					double[] sectDistWts = sectDistWeightsAtCubes[cubeIndex];
					for (int s=0; s<sects.length; s++) {
						sectIndices.add(sects[s]);
						Double prevWt = sectFracts.get(sects[s]);
						Double prevOrigWt = sectOrigFracts.get(sects[s]);
						if (prevWt == null) {
							prevWt = 0d;
							prevOrigWt = 0d;
						}
						sectFracts.put(sects[s], prevWt + sectDistWts[s]);
						sectOrigFracts.put(sects[s], prevOrigWt + sectDistWts[s]/totDistWtsAtCubesForSectArray[sects[s]]);
					}
				}
			}
			if (sectFracts != null) {
				double sumNodeWeight = 0d;
				for (Integer sectIndex : sectFracts.keySet()) {
					// this weight is scaled to account for overlap
					double sectWeight = sectFracts.get(sectIndex)/(double)numCubes;
					sumNodeWeight += sectWeight;
					sectInNodeParticBuilder.put(sectIndex, nodeIndex, sectWeight);
					double origWeight = sectOrigFracts.get(sectIndex);
					nodeInSectParticBuilder.put(sectIndex, nodeIndex, origWeight);
				}
				nodeExtentsBuilder.put(nodeIndex, sumNodeWeight);
			}
		}

		this.sectIndices = ImmutableSet.copyOf(sectIndices);
		this.nodeExtents = nodeExtentsBuilder.build();
		this.nodeInSectPartic = nodeInSectParticBuilder.build();
		this.sectInNodePartic = sectInNodeParticBuilder.build();
	}
	
	/*
	 * Cube-related methods
	 */
	
	public CubedGriddedRegion getCubedGriddedRegion() {
		return cgr;
	}
	
	/**
	 * @param cubeIndex
	 * @return sections indexes assocated with this cube, or null if none
	 */
	public int[] getSectsAtCube(int cubeIndex) {
		return sectsAtCubes[cubeIndex];
	}
	
	/**
	 * @param cubeIndex
	 * @return section distance-fraction weights for this cube, returned in the same order as
	 * {@link #getSectsAtCube(int)}, or null if none.
	 */
	public double[] getSectDistWeightsAtCube(int cubeIndex) {
		return sectDistWeightsAtCubes[cubeIndex];
	}
	
	/**
	 * 
	 * @param sectIndex
	 * @return the total weight for each section summed across all cubes, after accounting for any overlapping
	 * sections
	 */
	public double getTotalDistWtAtCubesForSect(int sectIndex) {
		return totDistWtsAtCubesForSectArray[sectIndex];
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
		return ImmutableMap.copyOf(nodeExtents);
	}
	
	@Override
	public double getNodeFraction(int nodeIdx) {
		Double fraction = nodeExtents.get(nodeIdx);
		return (fraction == null) ? 0.0 : fraction;
	}
	
	@Override
	public Map<Integer, Double> getScaledNodeFractions(int sectIdx) {
		return sectInNodePartic.row(sectIdx);
	}
	
	@Override
	public Map<Integer, Double> getNodeFractions(int sectIdx) {
		return nodeInSectPartic.row(sectIdx);
	}
	
	@Override
	public Map<Integer, Double> getSectionFracsOnNode(int nodeIdx) {
		return nodeInSectPartic.column(nodeIdx);
	}

	@Override
	public GriddedRegion getRegion() {
		return griddedRegion;
	}

	@Override
	public Collection<Integer> sectIndices() {
		return sectIndices;
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
		List<Future<SectCubeMapper>> futures = new ArrayList<>(rupSet.getNumSections());
		for (int s=0; s<rupSet.getNumSections(); s++)
			futures.add(exec.submit(new SectCubeMapper(s)));
		
		System.out.println("Waiting on "+futures.size()+" fault section to cube mapping futures");
		
		// store these as arrays, which will be more memory efficient than lists
		// there's a slight cost in rebuilding arrays if a cube is mapped to multiple sections, but that's probably
		// worth the memory overhead (especially as most cubes will be mapped to 0 or 1 section)
		sectsAtCubes = new int[cgr.getNumCubes()][];
		// this is only needed temporarily
		double[][] sectDistsAtCubes = new double[cgr.getNumCubes()][];
		totDistWtsAtCubesForSectArray = new double[rupSet.getNumSections()];
		
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
					totDistWtsAtCubesForSectArray[mapper.sectIndex] = mapper.totWeight;
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
		sectDistWeightsAtCubes = new double[cgr.getNumCubes()][];
		
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
			if(wtSum>1.0) {
				for(int s=0;s<numSect;s++) {
					double prevWeight = weights[s];
					weights[s] = prevWeight/wtSum;
					totDistWtsAtCubesForSectArray[sects[s]] += (weights[s]-prevWeight); // reduce this for the wt reduction here
				}
			}
			
			sectDistWeightsAtCubes[c] = weights;
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
		
		public SectCubeMapper(int sectIndex) {
			this.sectIndex = sectIndex;
		}

		@Override
		public SectCubeMapper call() throws Exception {
			FaultSection fltSection = rupSet.getFaultSectionData(sectIndex);
			
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
				if (skip)
					// can actually skip it
					return null;
			}
			
			mappedCubeDistances = new HashMap<>();
			totWeight = 0d;
			
			boolean aseisReducesArea = true; // TODO
			RuptureSurface sectSurf = fltSection.getFaultSurface(0.25, false, aseisReducesArea);
			
//			Region fltPolygon = getSectionPolygonRegion(fltSection, maxFaultNuclDist, true);
			Region fltPolygon = NSHM23_FaultPolygonBuilder.buildPoly(fltSection, sectSurf, aseisReducesArea, maxFaultNuclDist);
			
//			System.out.println(fltSection.getName()+"\nsectIndex = "+sectIndex+"\ndip = "+fltSection.getAveDip()
//			+"\ndipDir = "+fltSection.getDipDirection()+
//			"\nupSeisDep = "+fltSection.getOrigAveUpperDepth()+
//			"\nddw = "+fltSection.getOrigDownDipWidth()+"\nTrace:\n");
//			for(Location loc:fltSection.getFaultTrace())
//				System.out.println(loc);
//			System.out.println("\nPolygonRegion:\n");
//			for(Location loc:fltPolygon.getBorder())
//				System.out.println(loc);
			
//			System.out.println("\nGriddedSurface:\n");
//			RuptureSurface sectSurf = fltSection.getFaultSurface(1.0, false, false);
//			for(int i=0;i<sectSurf.getEvenlyDiscretizedNumLocs();i++) {
//				System.out.println(sectSurf.getEvenlyDiscretizedLocation(i));
//			}
//			QuadSurface sectSurf = new QuadSurface(fltSection,false);

			GriddedRegion griddedPolygonReg = new GriddedRegion(fltPolygon, cgr.getCubeLatLonSpacing(), cgr.getCubeLocationForIndex(0));
			double testWt=0;
			for(int i=0;i<griddedPolygonReg.getNumLocations();i++) {
				Location loc = griddedPolygonReg.getLocation(i);
				double depthDiscr = cgr.getCubeDepthDiscr();
				for(double depth = depthDiscr/2;depth<cgr.getMaxDepth();depth+=depthDiscr) {
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

			float ratio = (float)testWt/(float)totWeight;
			if(ratio != 1.0) {
//				sectionsThatNucleateOutsideRegionList.add(sectIndex);
				if(D) System.out.println((1f-ratio)+" of "+rupSet.getFaultSectionData(sectIndex).getName()+ " nucleates outside the region");
			}
			
			if (mappedCubeDistances.isEmpty())
				return null;
			return this;
		}
	}

	public double getDistWt(double dist) {
		return (maxFaultNuclDist-dist)/maxFaultNuclDist;
	}
	
	/**
	 * @return List of regional associations if this represents multiple regions stitched together, else null
	 */
	public List<NSHM23_FaultCubeAssociations> getRegionalAssociations() {
		return regionalAssociations;
	}

}
