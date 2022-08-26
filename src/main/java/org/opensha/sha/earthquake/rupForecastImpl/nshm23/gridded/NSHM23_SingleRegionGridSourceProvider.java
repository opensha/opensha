package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.NucleationRatePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;
import scratch.UCERF3.griddedSeismicity.GridReader;

/**
 * This class represents a grid source provider where, rather than in UCERF3 where a fault represented 
 * all supraseismogenic ruptures inside it's polygon, there is now a linear (ramp) transition between 
 * fault-based ruptures and gridded seismicity events. Thus, fault-based ruptures are most likely to
 * nucleate on the fault surface, but can also nucleate in the vicinity assuming a linear ramp with distance
 * out to the specified maxFaultNuclDist, and in 3D.  Thus, a fault is 50% less likely to nucleate at a distance
 * of maxFaultNuclDist/2 (from the nearest point on the fault surface), and has 0% likelihood of nucleating 
 * beyond maxFaultNuclDist.  Likewise, large gridded-seismicity events taper with the opposite trend near faults,
 * such that their rates are half at maxFaultNuclDist/2 and zero right on the fault surface.  Smaller events (those
 * less than the smallest supraseismogenic magnitudes on faults) have grid cell rates that match the given 
 * spatialPDF exactly (but internally they are apportioned between faults and off-fault ("unassociated") 
 * seismicity according to the same linear ramp. 
 * 
 * The class is backed by a CubedGriddedRegion for purposes of computing distances and associated nucleation rates.  
 * Sources could be given for each cube if higher resolution hazard calculations are desired.
 * 
 * The scaleAllMFDs(double[]) method only scales the gridded-region MFDs and sources (not the cube MFDs)
 * 
 * 
 * TODO:
 * 
 * 1) Finalize how cached files are handled
 * 		Kevin: no longer needed, it's fast now
 *    
 * 2) Move getSectionPolygonRegion(*) method to faultSection? & confirm that trace is not offset
 *    when UpperSeisDepth != 0.
 *    	Kevin: still needed TODO
 *    
 * 3) Make sure computeLongTermSupraSeisMFD_OnSectArray() from fss has the final Mmin applied.
 * 		Kevin: probably not needed, as we skip ruptures below that in the inversion so if we have ModSectMinMags applied,
 * then they should have zero rates TODO
 * 
 * 4) QuadSurface - do we have analytical dist to a loc that is not on the earth surface?
 * 		Kevin: no
 * 
 * 5) Move ETAS_SimAnalysisTools.writeMemoryUse() to a more general class/location
 * 		Kevin: no longer using it in my more efficient version
 * 
 * 6) improve input of fracStrikeSlip,fracNormal, and fracReverse (e.g., bundle and
 *    include fixed rake option?
 *    Kevin: could definitely include a fixed option TODO
 * 
 * @author Ned, Kevin
 *
 */
public class NSHM23_SingleRegionGridSourceProvider extends NSHM23_AbstractGridSourceProvider {
	
	final static boolean D = true;
	
	final static double DEFAULT_MAX_FAULT_NUCL_DIST = 12d;
	
	private double maxFaultNuclDist;
	
	private CubedGriddedRegion cgr;
	
	private double[] spatialPDF;
	private FaultSystemSolution fss;
	private FaultSystemRupSet rupSet;
	private EvenlyDiscretizedFunc depthNuclProbHist;
	private GriddedRegion griddedRegion;
	private ModSectMinMags modMinMags;
	
	private IncrementalMagFreqDist totGriddedSeisMFD; // supplied as input

	private SummedMagFreqDist totalSubSeisOnFaultMFD, totalTrulyOffFaultMFD, totalSupraSeisOnFaultMFD; // all computed
	
	private SummedMagFreqDist[] subSeisOnFaultMFD_ForGridArray, unassociatedMFD_ForGridArray;
	
	// for each cube, an array of mapped section indexes (or null if none)
	private int[][] sectsAtCubes;
	// for each cube, an array of mapped section distance-fraction wts (where the wts represent the fraction of
	// seismicity assinged to the fault section below the min seismo mag), in the same order as sectsAtCubes
	private double[][] sectDistWeightsAtCubes;
	
	// this is the total wt for each section summed from sectDistFractWtMapList (divide the wt directly above
	// by this value to get the nucleation fraction for the section in the associated cube) 
	private double[] totDistWtsAtCubesForSectArray;
	
	private IncrementalMagFreqDist[] longTermSupraSeisMFD_OnSectArray;

//	private List<Integer> sectionsThatNucleateOutsideRegionList;
	
	private double[] fracStrikeSlip,fracNormal,fracReverse;
	
	public NSHM23_SingleRegionGridSourceProvider(FaultSystemSolution fss, CubedGriddedRegion cgr, 
			double[] spatialPDF, IncrementalMagFreqDist totGriddedSeisMFD, EvenlyDiscretizedFunc depthNuclProbHist,
			double[] fracStrikeSlip, double[] fracNormal, double[] fracReverse) {

		this(fss, cgr, spatialPDF, totGriddedSeisMFD, depthNuclProbHist, fracStrikeSlip, fracNormal, fracReverse,
				DEFAULT_MAX_FAULT_NUCL_DIST);
	}
	
	public NSHM23_SingleRegionGridSourceProvider(FaultSystemSolution fss, CubedGriddedRegion cgr, 
			double[] spatialPDF, IncrementalMagFreqDist totGriddedSeisMFD, EvenlyDiscretizedFunc depthNuclProbHist, 
			double[] fracStrikeSlip, double[] fracNormal, double[] fracReverse, double maxFaultNuclDist) {
		this.fss = fss;
		this.cgr = cgr;
		this.rupSet = fss.getRupSet();
		this.modMinMags = rupSet.getModule(ModSectMinMags.class);
		this.spatialPDF = spatialPDF;
		this.totGriddedSeisMFD = totGriddedSeisMFD;
		this.depthNuclProbHist = depthNuclProbHist;
		this.maxFaultNuclDist = maxFaultNuclDist;
		this.griddedRegion = cgr.getGriddedRegion();
		this.fracStrikeSlip = fracStrikeSlip;
		this.fracNormal = fracNormal;
		this.fracReverse = fracReverse;
		
		Preconditions.checkState(griddedRegion.getNodeCount() == spatialPDF.length,
				"griddedRegion and spatialPDF have differe sizes: %s vs %s", griddedRegion.getNodeCount(), spatialPDF.length);
		
		// test some things
		double testSum=0;
		for(double val:spatialPDF) testSum += val;
		Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "spatialPDF values must sum to 1.0; sum=%s", testSum);
		
		testSum = depthNuclProbHist.calcSumOfY_Vals();
		Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "depthNuclProbHist y-axis values must sum to 1.0; sum=%s", testSum);
		// could also check the exact x-axis discretization of depthNuclProbHist
		
		// generate or read the sections and their distances for cubes 
		long time = System.currentTimeMillis();
		cacheCubeSectMappings();
		long runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("cacheCubeSectMappings() took "+(runtime/1000)+" seconds");
		
		// compute total MFDs
		time = System.currentTimeMillis();
		computeTotalOnAndOffFaultGriddedSeisMFDs();
		runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("computeTotalOnAndOffFaultGriddedSeisMFDs() took "+(runtime/1000)+" seconds");
		
		// compute total MFDs
		time = System.currentTimeMillis();
		computeLongTermSupraSeisMFD_OnSectArray();
		runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("computeLongTermSupraSeisMFD_OnSectArray() took "+(runtime/1000)+" seconds");
		
		// compute grid cell MFDs
		time = System.currentTimeMillis();
		subSeisOnFaultMFD_ForGridArray = new SummedMagFreqDist[spatialPDF.length];
		unassociatedMFD_ForGridArray = new SummedMagFreqDist[spatialPDF.length];
		for(int i=0;i<spatialPDF.length;i++) {
			subSeisOnFaultMFD_ForGridArray[i] = computeMFD_SubSeisOnFault(i);
			unassociatedMFD_ForGridArray[i] = computeMFD_Unassociated(i);
		}
		runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("computing grid MFDs took "+(runtime/1000)+" seconds");
	
		if(D) System.out.println("Done with constructor");
		
	}
	
	/**
	 * This assumes the trace represents a surface projection of top edge of surface (and not projected
	 * up-dip when upper seismogenic depth is nonzero)
	 * 
	 * TODO: deal with surface projection traces
	 * @param faultSection
	 * @param distance
	 * @param accountForDip
	 * @return
	 */
	public static Region getSectionPolygonRegion(FaultSection faultSection, double distance, boolean accountForDip) {
		LocationList trace = faultSection.getFaultTrace();
		Preconditions.checkArgument(trace.size() > 1);
		double dipDir = faultSection.getDipDirection();
		double distPlusDip = distance;
		if(accountForDip)
			distPlusDip += faultSection.getOrigDownDipWidth() * Math.cos(faultSection.getAveDip() * Math.PI / 180d);
		LocationList locList = new LocationList();
		locList.add(trace.get(0));
		LocationVector v = new LocationVector(dipDir, distPlusDip, 0);

		for (int i = 0; i < trace.size(); i++) {
			locList.add(LocationUtils.location(trace.get(i), v));
		}
		locList.add(trace.get(trace.size()-1));
		double reverseDipDir = (dipDir + 180) % 360;
		v = new LocationVector(reverseDipDir, distance, 0);
		for (int i = trace.size()-1; i >= 0; i--) {
			locList.add(LocationUtils.location(trace.get(i), v));
		}
		return new Region(locList, BorderType.MERCATOR_LINEAR);
	}
	
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
			
			Region fltPolygon = getSectionPolygonRegion(fltSection, maxFaultNuclDist, true);
			
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
			
			RuptureSurface sectSurf = fltSection.getFaultSurface(0.25);
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
	 * this creates a blank (zero y-axis values) MFD with the same discretization as the constructor supplied totGriddedSeisMFD.
	 * @return
	 */
	public SummedMagFreqDist initSummedMFD() {
		return new SummedMagFreqDist(totGriddedSeisMFD.getMinX(), totGriddedSeisMFD.size(),totGriddedSeisMFD.getDelta());
	}
	
	private void computeTotalOnAndOffFaultGriddedSeisMFDs() {
		
		totalSubSeisOnFaultMFD = initSummedMFD();
		totalSubSeisOnFaultMFD.setName("totalSubSeisMFD");
		totalTrulyOffFaultMFD = initSummedMFD();
		totalTrulyOffFaultMFD.setName("totalTrulyOffFaultMFD");
		
		for(int c=0;c<cgr.getNumCubes();c++) {
			SummedMagFreqDist mfd = getSubSeismoMFD_ForCube(c);
			if(mfd != null)
				totalSubSeisOnFaultMFD.addIncrementalMagFreqDist(mfd);
		}
		
		for(int i=0;i<totGriddedSeisMFD.size();i++) {
			totalTrulyOffFaultMFD.add(i, totGriddedSeisMFD.getY(i) - totalSubSeisOnFaultMFD.getY(i));
		}
//		if(D) {
//			System.out.println("totGriddedSeisMFD:\n"+totGriddedSeisMFD);
//			System.out.println("totGriddedSeisMFD Cumulative::\n"+totGriddedSeisMFD.getCumRateDistWithOffset());
//			System.out.println("totSubSeisMFD:\n"+totalSubSeisOnFaultMFD);
//			System.out.println("totalTrulyOffFaultMFD:\n"+totalTrulyOffFaultMFD);
//		}
	}
	
	/**
	 * Need to figure out how to compute this when fss has the module: ModSectMinMags.
	 * 
	 * Note that this includes any fault sections that are outside the region
	 */
	private void computeLongTermSupraSeisMFD_OnSectArray() {
		
		SummedMagFreqDist mfd = initSummedMFD();

		// this didn't work so use ERF to get section mdfs
//		ModSectMinMags mod = fss.getModule(ModSectMinMags.class);
		
		longTermSupraSeisMFD_OnSectArray = new IncrementalMagFreqDist[rupSet.getNumSections()];
		for(int s=0;s<rupSet.getNumSections();s++) {
			IncrementalMagFreqDist nuclMFD = fss.calcNucleationMFD_forSect(s, mfd.getMinX(), mfd.getMaxX(), mfd.size());
			longTermSupraSeisMFD_OnSectArray[s] = nuclMFD;
			mfd.addIncrementalMagFreqDist(nuclMFD);
		}
		mfd.setName("totalSupraSeisOnFaultMFD");
		totalSupraSeisOnFaultMFD=mfd;
	}
	
	private double minMagForSect(int sectIndex) {
		if (modMinMags == null)
			return rupSet.getMinMagForSection(sectIndex);
		return modMinMags.getMinMagForSection(sectIndex);
	}
	
	public SummedMagFreqDist getSubSeismoMFD_ForCube(int cubeIndex) {
		double[] sectDistWeights = sectDistWeightsAtCubes[cubeIndex];
		if (sectDistWeights == null) // no sections nucleate here
			return null;
		SummedMagFreqDist subSeisMFD = initSummedMFD();
		int gridIndex = cgr.getRegionIndexForCubeIndex(cubeIndex);
		int depIndex = cgr.getDepthIndexForCubeIndex(cubeIndex);
		int[] sects = sectsAtCubes[cubeIndex];
		for(int s=0; s<sects.length; s++) {
			double wt = sectDistWeights[s]*spatialPDF[gridIndex]*depthNuclProbHist.getY(depIndex)/(cgr.getNumCubesPerGridEdge()*cgr.getNumCubesPerGridEdge());
			double minMag = minMagForSect(sects[s]);
			double minMagIndex = totGriddedSeisMFD.getClosestXIndex(minMag);
			for(int i=0; i<minMagIndex;i++)
				subSeisMFD.add(i, wt*totGriddedSeisMFD.getY(i));
		}
		return subSeisMFD;
	}
	
	
	
	public SummedMagFreqDist getUnassociatedMFD_ForCube(int cubeIndex) {
		double scaleFactor = totGriddedSeisMFD.getY(0)/totalTrulyOffFaultMFD.getY(0);
		
		double[] sectDistWeights = sectDistWeightsAtCubes[cubeIndex];
		double wtSum =0;
		if (sectDistWeights != null)
			for(double weight : sectDistWeights)
				wtSum += weight;
		SummedMagFreqDist trulyOffMFD = initSummedMFD();
		int gridIndex = cgr.getRegionIndexForCubeIndex(cubeIndex);
		int depIndex = cgr.getDepthIndexForCubeIndex(cubeIndex);
		double wt = (1d-wtSum)*scaleFactor*spatialPDF[gridIndex]*depthNuclProbHist.getY(depIndex)/(cgr.getNumCubesPerGridEdge()*cgr.getNumCubesPerGridEdge());
		
		for(int i=0; i<totalTrulyOffFaultMFD.size();i++)
			trulyOffMFD.add(i, wt*totalTrulyOffFaultMFD.getY(i));

		return trulyOffMFD;
	}
	
	public SummedMagFreqDist getGriddedSeisMFD_ForCube(int cubeIndex) {
		SummedMagFreqDist cubeMFD = initSummedMFD();
		SummedMagFreqDist mfd = getSubSeismoMFD_ForCube(cubeIndex);
		if(mfd != null)
			cubeMFD.addIncrementalMagFreqDist(mfd);
		mfd = getUnassociatedMFD_ForCube(cubeIndex);
		if(mfd != null)
			cubeMFD.addIncrementalMagFreqDist(mfd);
		return cubeMFD;
	}
	
	
	
	public SummedMagFreqDist getSupraSeisMFD_ForCube(int cubeIndex) {
		double[] sectDistWeights = sectDistWeightsAtCubes[cubeIndex];
		if(sectDistWeights == null) // no sections nucleate here
			return null;
		SummedMagFreqDist supraMFD = initSummedMFD();
		int[] sects = sectsAtCubes[cubeIndex];
		for(int s=0; s<sects.length; s++) {
			double wt = sectDistWeights[s]/totDistWtsAtCubesForSectArray[sects[s]];
			IncrementalMagFreqDist mfd = longTermSupraSeisMFD_OnSectArray[sects[s]].deepClone();
			mfd.scale(wt);
			supraMFD.addIncrementalMagFreqDist(mfd);
		}
		return supraMFD;
	}
	
	public SummedMagFreqDist getTotalMFD_ForCube(int cubeIndex) {
		SummedMagFreqDist cubeMFD = initSummedMFD();
		SummedMagFreqDist mfd = getGriddedSeisMFD_ForCube(cubeIndex);
		if(mfd != null)
			cubeMFD.addIncrementalMagFreqDist(mfd);
		mfd = getSupraSeisMFD_ForCube(cubeIndex);
		if(mfd != null)
			cubeMFD.addIncrementalMagFreqDist(mfd);
		return cubeMFD;
	}
	
	@Override
	public SummedMagFreqDist getMFD_SubSeisOnFault(int gridIndex) {
		return subSeisOnFaultMFD_ForGridArray[gridIndex];
	}
	
	@Override
	public SummedMagFreqDist getMFD_Unassociated(int gridIndex) {
		return unassociatedMFD_ForGridArray[gridIndex];
	}
	
	
	private SummedMagFreqDist computeMFD_SubSeisOnFault(int gridIndex) {
		SummedMagFreqDist summedMFD = initSummedMFD();
		for(int c:cgr.getCubeIndicesForGridCell(gridIndex)) {
			SummedMagFreqDist mfd = getSubSeismoMFD_ForCube(c);
			if(mfd != null)
				summedMFD.addIncrementalMagFreqDist(mfd);
		}
		if(summedMFD.getTotalIncrRate() < 1e-10)
			summedMFD=null;
		return summedMFD;
	}
	
	
	private SummedMagFreqDist computeMFD_Unassociated(int gridIndex) {
		SummedMagFreqDist summedMFD = initSummedMFD();
		for(int c:cgr.getCubeIndicesForGridCell(gridIndex)) {
			summedMFD.addIncrementalMagFreqDist(getUnassociatedMFD_ForCube(c));
		}
		if(summedMFD.getTotalIncrRate() < 1e-10)
			summedMFD=null;
		return summedMFD;
	}
	
	/**
	 * this returns the total sub-seismogenic on-fault MFD
	 * @return
	 */
	public SummedMagFreqDist getTotalSubSeisOnFaultMFD() {
		return totalSubSeisOnFaultMFD;
	}
	
	/**
	 * This returns the total unassociated (truly off-fault) MFD
	 * @return
	 */
	public SummedMagFreqDist getTotalUnassociatedMFD() {
		return totalTrulyOffFaultMFD;
	}

	/**
	 * This returns the total supraseismogenic on-fault MFD
	 * @return
	 */
	public SummedMagFreqDist getTotalSupraSeisOnFaultMFD() {
		return totalSupraSeisOnFaultMFD;
	}
	
	/**
	 * This returns the total gridded seismicity MFD (the total sub-seismogenic 
	 * on-fault MFD plus the total unassociated MFD)
	 * @return
	 */
	public IncrementalMagFreqDist getTotalGriddedSeisMFD() {
		return totGriddedSeisMFD;
	}
	
	@Override
	public double getFracStrikeSlip(int gridIndex) {
		return fracStrikeSlip[gridIndex];
	}

	@Override
	public double getFracReverse(int gridIndex) {
		return fracReverse[gridIndex];
	}

	@Override
	public double getFracNormal(int gridIndex) {
		return fracNormal[gridIndex];
	}
	
	@Override
	public GriddedRegion getGriddedRegion() {
		return griddedRegion;
	}

	@Override
	public void scaleAllMFDs(double[] valuesArray) {
		// TODO
		throw new UnsupportedOperationException("not yet implemented");
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged.zip"));
		
		GriddedRegion gridReg = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution();
		double delta=2;
		HistogramFunction binnedDepthDistFunc = new HistogramFunction(1d, 12,delta);
		for(int i=0;i<binnedDepthDistFunc.size();i++) {
			double prob = seisDepthDistribution.getProbBetweenDepths(binnedDepthDistFunc.getX(i)-delta/2d,binnedDepthDistFunc.getX(i)+delta/2d);
			binnedDepthDistFunc.set(i,prob);
		}
		System.out.println("Total Depth Prob Sum: "+binnedDepthDistFunc.calcSumOfY_Vals());

		double[] spatialPDF = SpatialSeisPDF.UCERF3.getPDF();  // this sums to 0.9994463999998295; correct it to 1.0
		double sum=0;
		for(double val:spatialPDF) sum+=val;
		for(int i=0;i<spatialPDF.length;i++)
			spatialPDF[i] = spatialPDF[i]/sum;
		
		double mMaxOff = 7.65;
		double bVal = 1d;
		double rateM5 = TotalMag5Rate.RATE_7p9.getRateMag5();
		
		CubedGriddedRegion cgr = new CubedGriddedRegion(gridReg);
		
		double[] fracStrikeSlip, fracReverse, fracNormal;
		fracStrikeSlip = new GridReader("StrikeSlipWts.txt").getValues();
		fracReverse = new GridReader("ReverseWts.txt").getValues();
		fracNormal = new GridReader("NormalWts.txt").getValues();
		
		EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(sol.getRupSet());
		GutenbergRichterMagFreqDist totalGR = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		totalGR.setAllButTotCumRate(refMFD.getMinX(), refMFD.getX(refMFD.getClosestXIndex(mMaxOff)), 1e16, bVal);
		totalGR.scaleToCumRate(refMFD.getClosestXIndex(5.01d), rateM5);
		
		IncrementalMagFreqDist solTotMFD = sol.calcNucleationMFD_forRegion(
				gridReg, refMFD.getMinX(), refMFD.getMaxX(), refMFD.getDelta(), false);
		
		IncrementalMagFreqDist totGridSeisMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		for (int i=0; i<refMFD.size(); i++) {
			double totGR = totalGR.getY(i);
			if (totGR == 0)
				continue;
			double solSupra = solTotMFD.getY(i);
			double leftover = totGR - solSupra;
			if (leftover < 0)
				System.out.println("WARNING: bulge at M="+(float)refMFD.getX(i)+": "+(float)totGR+" - "+(float)solSupra+" = "+(float)leftover);
			else
				totGridSeisMFD.set(i, leftover);
		}
		
		NSHM23_SingleRegionGridSourceProvider prov = new NSHM23_SingleRegionGridSourceProvider(
				sol, cgr, spatialPDF, totGridSeisMFD, binnedDepthDistFunc, fracStrikeSlip, fracNormal, fracReverse);
		
		SummedMagFreqDist unassociatedMFD = prov.getTotalUnassociatedMFD();
		SummedMagFreqDist subSeisMFD = prov.getTotalSubSeisOnFaultMFD();
		SummedMagFreqDist supraSeisMFD = prov.getTotalSupraSeisOnFaultMFD();
		
		for (int m=0; m<refMFD.size(); m++)
			System.out.println("M="+(float)refMFD.getX(m)
				+"\tgridGR="+(float)totalGR.getY(m)
				+"\tgridTarget="+(float)totGridSeisMFD.getY(m)
				+"\tunassociated="+(float)unassociatedMFD.getY(m)
				+"\tsubSeis="+(float)subSeisMFD.getY(m)
				+"\tsolSupraSeis="+(float)solTotMFD.getY(m)
				+"\tprovSupraSeis="+(float)supraSeisMFD.getY(m));
		
		sol.addModule(prov);
		ReportPageGen pageGen = new ReportPageGen(sol.getRupSet(), sol, "Solution", new File("/tmp/report"),
				List.of(new SolMFDPlot(), new NucleationRatePlot()));
		pageGen.setReplot(true);
		pageGen.generatePage();
	}

}
