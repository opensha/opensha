package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.FiniteRuptureConverter;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupturePropertiesCache;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.NucleationRatePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

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

	final static boolean D = false;

	public final static double DEFAULT_MAX_FAULT_NUCL_DIST = 12d;

	private CubedGriddedRegion cgr;

	private FaultCubeAssociations faultCubeassociations;

	private double[] spatialPDF;
	private FaultSystemSolution fss;
	private FaultSystemRupSet rupSet;
	private EvenlyDiscretizedFunc depthNuclProbHist;
	private GriddedRegion griddedRegion;
	private ModSectMinMags modMinMags;

	private IncrementalMagFreqDist totGriddedSeisMFD; // supplied as input

	private SummedMagFreqDist totalSubSeisOnFaultMFD, totalTrulyOffFaultMFD, totalSupraSeisOnFaultMFD; // all computed

	private SummedMagFreqDist[] subSeisOnFaultMFD_ForGridArray, unassociatedMFD_ForGridArray;

	private IncrementalMagFreqDist[] longTermSupraSeisMFD_OnSectArray;
	
	// for each grid node in the region
	// map of associated sections, to the associated 
	private List<Map<Integer, double[]>> gridSectMappedAssocatedMFDs;

	//	private List<Integer> sectionsThatNucleateOutsideRegionList;

	private double[] fracStrikeSlip,fracNormal,fracReverse;

	private TectonicRegionType[] trts;

	public NSHM23_SingleRegionGridSourceProvider(FaultSystemSolution fss, CubedGriddedRegion cgr, 
			double[] spatialPDF, IncrementalMagFreqDist totGriddedSeisMFD, EvenlyDiscretizedFunc depthNuclProbHist,
			double[] fracStrikeSlip, double[] fracNormal, double[] fracReverse, Map<TectonicRegionType, Region> trtRegions) {
		this(fss, cgr, spatialPDF, totGriddedSeisMFD, depthNuclProbHist, fracStrikeSlip, fracNormal, fracReverse,
				DEFAULT_MAX_FAULT_NUCL_DIST, trtRegions);
	}

	public NSHM23_SingleRegionGridSourceProvider(FaultSystemSolution fss, CubedGriddedRegion cgr, 
			double[] spatialPDF, IncrementalMagFreqDist totGriddedSeisMFD, EvenlyDiscretizedFunc depthNuclProbHist, 
			double[] fracStrikeSlip, double[] fracNormal, double[] fracReverse, double maxFaultNuclDist, Map<TectonicRegionType, Region> trtRegions) {
		this(fss, new NSHM23_FaultCubeAssociations(fss.getRupSet(), cgr, maxFaultNuclDist),
				spatialPDF, totGriddedSeisMFD, depthNuclProbHist, fracStrikeSlip, fracNormal, fracReverse, trtRegions);
	}

	public NSHM23_SingleRegionGridSourceProvider(FaultSystemSolution fss, FaultCubeAssociations faultCubeassociations,
			double[] spatialPDF, IncrementalMagFreqDist totGriddedSeisMFD, EvenlyDiscretizedFunc depthNuclProbHist, 
			double[] fracStrikeSlip, double[] fracNormal, double[] fracReverse, Map<TectonicRegionType, Region> trtRegions) {
		this.fss = fss;
		this.faultCubeassociations = faultCubeassociations;
		this.cgr = faultCubeassociations.getCubedGriddedRegion();
		this.rupSet = fss.getRupSet();
		this.modMinMags = rupSet.getModule(ModSectMinMags.class);
		this.spatialPDF = spatialPDF;
		this.totGriddedSeisMFD = totGriddedSeisMFD;
		this.griddedRegion = cgr.getGriddedRegion();
		this.fracStrikeSlip = fracStrikeSlip;
		this.fracNormal = fracNormal;
		this.fracReverse = fracReverse;
		
		trts = new TectonicRegionType[griddedRegion.getNodeCount()];
		for (int i=0; i<trts.length; i++)
			trts[i] = TectonicRegionType.ACTIVE_SHALLOW;
		if (trtRegions != null) {
			for (int i=0; i<trts.length; i++) {
				Location loc = griddedRegion.getLocation(i);
				for (TectonicRegionType trt : trtRegions.keySet()) {
					if (trtRegions.get(trt).contains(loc)) {
						trts[i] = trt;
						break;
					}
				}
			}
		}

		Preconditions.checkState(griddedRegion.getNodeCount() == spatialPDF.length,
				"griddedRegion and spatialPDF have differe sizes: %s vs %s", griddedRegion.getNodeCount(), spatialPDF.length);

		// test some things
		double testSum=0;
		for(double val:spatialPDF) testSum += val;
		Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "spatialPDF values must sum to 1.0; sum=%s", testSum);
		
		// check normalization and binning of depthNuclProbHist
		this.depthNuclProbHist = validateOrUpdateDepthDistr(depthNuclProbHist, cgr);

		// compute on and off fault total and grid node MFDs
		long time = System.currentTimeMillis();
		computeOnAndOffFaultGriddedSeisMFDs();
		long runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("computeOnAndOffFaultGriddedSeisMFDs() took "+(runtime/1000)+" seconds");

		// compute total MFDs
		time = System.currentTimeMillis();
		computeLongTermSupraSeisMFD_OnSectArray();
		runtime = System.currentTimeMillis()-time;
		if(D) System.out.println("computeLongTermSupraSeisMFD_OnSectArray() took "+(runtime/1000)+" seconds");

		if(D) System.out.println("Done with constructor");

	}
	
	private EvenlyDiscretizedFunc validateOrUpdateDepthDistr(EvenlyDiscretizedFunc depthNuclProbHist, CubedGriddedRegion cgr) {
		double testSum = depthNuclProbHist.calcSumOfY_Vals();
		Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "depthNuclProbHist y-axis values must sum to 1.0; sum=%s", testSum);
		// now make sure it matches depths used in CubedGriddedRegion
		double cubeDiscr = cgr.getCubeDepthDiscr();
		int numCubes = cgr.getNumCubeDepths();
		double firstDepth = cgr.getCubeDepth(0);
		if ((float)depthNuclProbHist.getDelta() == (float)cubeDiscr && (float)firstDepth == (float)depthNuclProbHist.getMinX()) {
			// same gridding
			if (numCubes == depthNuclProbHist.size())
				// identical
				return depthNuclProbHist;
			// not identical, resize to the same number of bins, padding with zeros if needed
			EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(firstDepth, numCubes, cubeDiscr);
			for (int i=0; i<ret.size(); i++) {
				if (i >= depthNuclProbHist.size())
					System.err.println("WARNING: depth-nucleation probability histogram doesn't have a value at "
							+(float)ret.getX(i)+", setting to 0");
				else
					ret.set(i, depthNuclProbHist.getY(i));
			}
			double weightBelow = 0d;
			for (int i=ret.size(); i<depthNuclProbHist.size(); i++)
				weightBelow += depthNuclProbHist.getY(i);
			if (weightBelow > 0d) {
				System.err.println("WARNING: depth-nucleation probability histogram has values below CubedGriddedRegion "
						+ "with total weight="+(float)weightBelow+", ignoring those values and re-normalizing.");
				ret.scale(1d/ret.calcSumOfY_Vals());
			} else {
				testSum = ret.calcSumOfY_Vals();
				Preconditions.checkState(testSum < 1.001 && testSum > 0.999,
						"re-gridded depth-nucleation hist doesn't sum to 1? %s", testSum);
			}
			return ret;
		}
		// TODO: could add interpolation support
		throw new IllegalStateException("Supplied depth-nucleation probability histogram does not match "
				+ "CubedGriddedRegion gridding.");
	}
	
	@Override
	public FaultCubeAssociations getFaultCubeassociations() {
		return faultCubeassociations;
	}

	/**
	 * this creates a blank (zero y-axis values) MFD with the same discretization as the constructor supplied totGriddedSeisMFD.
	 * @return
	 */
	public SummedMagFreqDist initSummedMFD() {
		return new SummedMagFreqDist(totGriddedSeisMFD.getMinX(), totGriddedSeisMFD.size(),totGriddedSeisMFD.getDelta());
	}

	private void computeOnAndOffFaultGriddedSeisMFDs() {

		totalSubSeisOnFaultMFD = initSummedMFD();
		totalSubSeisOnFaultMFD.setName("totalSubSeisMFD");
		totalTrulyOffFaultMFD = initSummedMFD();
		totalTrulyOffFaultMFD.setName("totalTrulyOffFaultMFD");

		gridSectMappedAssocatedMFDs = new ArrayList<>(griddedRegion.getNodeCount());
		for (int i=0; i<griddedRegion.getNodeCount(); i++)
			gridSectMappedAssocatedMFDs.add(null);
		subSeisOnFaultMFD_ForGridArray = new SummedMagFreqDist[spatialPDF.length];
		for(int c=0;c<cgr.getNumCubes();c++) {
			SummedMagFreqDist mfd = getSubSeismoMFD_ForCube(c, true);
			if(mfd != null) {
				// add to the total
				totalSubSeisOnFaultMFD.addIncrementalMagFreqDist(mfd);
				
				// add for the grid node
				int gridIndex = cgr.getRegionIndexForCubeIndex(c);
				if (subSeisOnFaultMFD_ForGridArray[gridIndex] == null)
					subSeisOnFaultMFD_ForGridArray[gridIndex] = initSummedMFD();
				subSeisOnFaultMFD_ForGridArray[gridIndex].addIncrementalMagFreqDist(mfd);
			}
		}

		for(int i=0;i<totGriddedSeisMFD.size();i++) {
			totalTrulyOffFaultMFD.add(i, totGriddedSeisMFD.getY(i) - totalSubSeisOnFaultMFD.getY(i));
		}
		
		// now build the unassociated grid node MFDs
		unassociatedMFD_ForGridArray = new SummedMagFreqDist[spatialPDF.length];
		for (int gridIndex=0; gridIndex<spatialPDF.length; gridIndex++) {
			SummedMagFreqDist summedMFD = initSummedMFD();
			for(int c:cgr.getCubeIndicesForGridCell(gridIndex)) {
				summedMFD.addIncrementalMagFreqDist(getUnassociatedMFD_ForCube(c));
			}
			if(summedMFD.getTotalIncrRate() >= 1e-10)
				unassociatedMFD_ForGridArray[gridIndex] = summedMFD;
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
		return getSubSeismoMFD_ForCube(cubeIndex, false);
		
	}

	private SummedMagFreqDist getSubSeismoMFD_ForCube(int cubeIndex, boolean trackAssociations) {
		double[] sectDistWeights = faultCubeassociations.getScaledSectDistWeightsAtCube(cubeIndex);
		if (sectDistWeights == null) // no sections nucleate here
			return null;
		Preconditions.checkState((float)StatUtils.sum(sectDistWeights) <= 1f);
		SummedMagFreqDist subSeisMFD = initSummedMFD();
		int gridIndex = cgr.getRegionIndexForCubeIndex(cubeIndex);
		Preconditions.checkState(gridIndex < spatialPDF.length);
		int depIndex = cgr.getDepthIndexForCubeIndex(cubeIndex);
		int[] sects = faultCubeassociations.getSectsAtCube(cubeIndex);
		Map<Integer, double[]> gridAssocatedMFDs = trackAssociations ? gridSectMappedAssocatedMFDs.get(gridIndex) : null;
		if (trackAssociations && gridAssocatedMFDs == null) {
			gridAssocatedMFDs = new HashMap<>(sects.length);
			gridSectMappedAssocatedMFDs.set(gridIndex, gridAssocatedMFDs);
		}
		for(int s=0; s<sects.length; s++) {
			Preconditions.checkState(s < sectDistWeights.length);
			double wt = sectDistWeights[s]*spatialPDF[gridIndex]*depthNuclProbHist.getY(depIndex)/(cgr.getNumCubesPerGridEdge()*cgr.getNumCubesPerGridEdge());
			double minMag = minMagForSect(sects[s]);
			double minMagIndex = totGriddedSeisMFD.getClosestXIndex(minMag);
			double[] gridAssoc = trackAssociations ? gridAssocatedMFDs.get(sects[s]) : null;
			if (trackAssociations && gridAssoc == null) {
				gridAssoc = new double[subSeisMFD.size()];
				gridAssocatedMFDs.put(sects[s], gridAssoc);
			}
			for(int i=0; i<minMagIndex;i++) {
				double sectCubeRate = wt*totGriddedSeisMFD.getY(i);
				subSeisMFD.add(i, sectCubeRate);
				if (trackAssociations)
					gridAssoc[i] += sectCubeRate;
			}
		}
		return subSeisMFD;
	}

	public SummedMagFreqDist getUnassociatedMFD_ForCube(int cubeIndex) {
		double scaleFactor = totGriddedSeisMFD.getY(0)/totalTrulyOffFaultMFD.getY(0);

		double[] sectDistWeights = faultCubeassociations.getScaledSectDistWeightsAtCube(cubeIndex);
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
		double[] sectDistWeights = faultCubeassociations.getScaledSectDistWeightsAtCube(cubeIndex);
		if(sectDistWeights == null) // no sections nucleate here
			return null;
		SummedMagFreqDist supraMFD = initSummedMFD();
		int[] sects = faultCubeassociations.getSectsAtCube(cubeIndex);
		for(int s=0; s<sects.length; s++) {
			double wt = sectDistWeights[s]/faultCubeassociations.getTotalScaledDistWtAtCubesForSect(sects[s]);
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
	
	public static class NSHM23_WUS_FiniteRuptureConverter implements FiniteRuptureConverter {
		
		private WC1994_MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();

		@Override
		public GriddedRupture buildFiniteRupture(int gridIndex, Location loc, double magnitude, double rate,
				FocalMech focalMech, TectonicRegionType trt, int[] associatedSections, double[] associatedSectionFracts,
				GriddedRupturePropertiesCache cache) {
			double dipRad = Math.toRadians(focalMech.dip());
			
			double depth = (float)magnitude < 6.5f ? 5d : 1d;
			double length = WC94.getMedianLength(magnitude);
			double aspectWidth = length / 1.5;
			double ddWidth = (14.0 - depth) / Math.sin(dipRad);
			ddWidth = Math.min(aspectWidth, ddWidth);
			double lower = depth + ddWidth * Math.sin(dipRad);
			
			GriddedRuptureProperties props = cache.getCached(new GriddedRuptureProperties(magnitude,
					focalMech.rake(), focalMech.dip(), Double.NaN, null, depth, lower, length, Double.NaN, Double.NaN,
					trt));
			
			return new GriddedRupture(gridIndex, loc, props, rate, associatedSections, associatedSectionFracts);
		}
		
	}
	
	public GridSourceList convertToGridSourceList() {
		return convertToGridSourceList(Double.NEGATIVE_INFINITY);
	}
	
	public GridSourceList convertToGridSourceList(double minMag) {
		return convertToGridSourceList(minMag, new NSHM23_WUS_FiniteRuptureConverter());
	}
	
	public GridSourceList convertToGridSourceList(FiniteRuptureConverter converter) {
		return convertToGridSourceList(Double.NEGATIVE_INFINITY, converter);
	}
	
	public GridSourceList convertToGridSourceList(double minMag, FiniteRuptureConverter converter) {
		return new Converter(this, gridSectMappedAssocatedMFDs, minMag, converter, getRefMFD());
	}
	
	private IncrementalMagFreqDist getRefMFD() {
		return new IncrementalMagFreqDist(totGriddedSeisMFD.getMinX(), totGriddedSeisMFD.size(), totGriddedSeisMFD.getDelta());
	}
	
	private static class Converter extends GridSourceList.DynamicallyBuilt {

		private MFDGridSourceProvider gridProv;
		private List<Map<Integer, double[]>> gridSectMappedAssocatedMFDs;
		private double minMag;
		private FiniteRuptureConverter converter;

		public Converter(MFDGridSourceProvider gridProv, List<Map<Integer, double[]>> gridSectMappedAssocatedMFDs,
				double minMag, FiniteRuptureConverter converter, IncrementalMagFreqDist refMFD) {
			super(gridProv.getTectonicRegionTypes(), gridProv.getGriddedRegion(), refMFD);
			this.gridProv = gridProv;
			this.gridSectMappedAssocatedMFDs = gridSectMappedAssocatedMFDs;
			this.minMag = minMag;
			this.converter = converter;
		}

		@Override
		public int getLocationIndexForSource(int sourceIndex) {
			return sourceIndex;
		}

		@Override
		public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
			return gridProv.getTectonicRegionType(sourceIndex);
		}

		@Override
		protected List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			double fractSS = gridProv.getFracStrikeSlip(gridIndex);
			double fractN = gridProv.getFracNormal(gridIndex);
			double fractR = gridProv.getFracReverse(gridIndex);
			
			IncrementalMagFreqDist mfd = gridProv.getMFD(tectonicRegionType, gridIndex);
			if (mfd == null) {
				return null;
			}
			
			GriddedRupturePropertiesCache cache = new GriddedRupturePropertiesCache();
			
			Location loc = gridProv.getLocation(gridIndex);
			Map<Integer, double[]> sectAssocs = gridSectMappedAssocatedMFDs.get(gridIndex);
			List<GriddedRupture> ruptureList = new ArrayList<>();
			for (int m=0; m<mfd.size(); m++) {
				double mag = mfd.getX(m);
				double totRate = mfd.getY(m);
				if (totRate == 0d || (float)mag < (float)minMag)
					continue;
				
				double associatedRate = 0d;
				int[] associatedSections = null;
				double[] associatedSectionFracts = null;
				if (sectAssocs != null) {
					List<Integer> associatedSectionsList = new ArrayList<>(sectAssocs.size());
					List<Double> associatedSectionFractsList = new ArrayList<>(sectAssocs.size());
					for (int sectID : sectAssocs.keySet()) {
						double[] sectAssocRates = sectAssocs.get(sectID);
						Preconditions.checkState(sectAssocRates.length == mfd.size());
						double sectAssocRate = sectAssocRates[m];
						if (sectAssocRate > 0d) {
							associatedRate += sectAssocRate;
							associatedSectionsList.add(sectID);
							associatedSectionFractsList.add(sectAssocRate/totRate);
						}
					}
					if (!associatedSectionFractsList.isEmpty()) {
						Preconditions.checkState((float)associatedRate <= (float)totRate,
								"Associated rate (%s) exceeds the total rate (%s) for gridIndex=%s, M=%s",
								associatedRate, totRate, gridIndex, mag);
						associatedSections = Ints.toArray(associatedSectionsList);
						associatedSectionFracts = Doubles.toArray(associatedSectionFractsList);
					}
				}
				for (FocalMech mech : FocalMech.values()) {
					double mechRate;
					switch (mech) {
					case STRIKE_SLIP:
						mechRate = totRate*fractSS;
						break;
					case NORMAL:
						mechRate = totRate*fractN;
						break;
					case REVERSE:
						mechRate = totRate*fractR;
						break;

					default:
						throw new IllegalStateException();
					}
					if (mechRate == 0d)
						continue;
					
					ruptureList.add(converter.buildFiniteRupture(gridIndex, loc, mag, mechRate, mech,
							tectonicRegionType, associatedSections, associatedSectionFracts, cache));
				}
			}
			return ruptureList;
		}

		@Override
		public double getFracStrikeSlip(int gridIndex) {
			return gridProv.getFracStrikeSlip(gridIndex);
		}

		@Override
		public double getFracReverse(int gridIndex) {
			return gridProv.getFracReverse(gridIndex);
		}

		@Override
		public double getFracNormal(int gridIndex) {
			return gridProv.getFracNormal(gridIndex);
		}

		@Override
		public int getNumSources() {
			return getNumLocations();
		}

		@Override
		public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
			Preconditions.checkState(gridProv instanceof NSHM23_SingleRegionGridSourceProvider,
					"Method only supported when we have an NSHM23_SingleRegionGridSourceProvider instance");
			Map<Integer, Double> assoc = ((NSHM23_SingleRegionGridSourceProvider)gridProv).faultCubeassociations.getNodeFractions(sectionIndex);
			if (assoc == null || assoc.isEmpty())
				return Set.of();
			return assoc.keySet();
		}

		@Override
		public AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
			return new ConverterAverager();
		}
		
	}
	
	private static class ConverterAverager implements AveragingAccumulator<GridSourceProvider> {

		private AveragingAccumulator<GridSourceProvider> mfdAverager = null;
		private List<Map<Integer, double[]>> gridSectMappedAssocatedMFDs;
		private IncrementalMagFreqDist refMFD = null;
		private double minMag;
		private FiniteRuptureConverter converter;
		
		private double sumWeight = 0d;
		
		@Override
		public Class<GridSourceProvider> getType() {
			return GridSourceProvider.class;
		}

		@Override
		public void process(GridSourceProvider module, double relWeight) {
			Preconditions.checkState(module instanceof Converter);
			
			Converter converter = (Converter)module;
			if (mfdAverager == null) {
				// first time
				mfdAverager = converter.gridProv.averagingAccumulator();
				gridSectMappedAssocatedMFDs = new ArrayList<>(converter.getNumLocations());
				for (int i=0; i<converter.getNumLocations(); i++)
					gridSectMappedAssocatedMFDs.add(null);
				this.converter = converter.converter;
				minMag = converter.minMag;
			}
			
			IncrementalMagFreqDist refMFD = converter.getRefMFD();
			if (this.refMFD == null || refMFD.size() > this.refMFD.size())
				this.refMFD = refMFD;
			
			mfdAverager.process(converter.gridProv, relWeight);
			
			for (int i=0; i<converter.gridSectMappedAssocatedMFDs.size(); i++) {
				Map<Integer, double[]> sectAssoc = converter.gridSectMappedAssocatedMFDs.get(i);
				if (sectAssoc == null)
					continue;
				Map<Integer, double[]> runningSectAssoc = gridSectMappedAssocatedMFDs.get(i);
				if (runningSectAssoc == null) {
					runningSectAssoc = new HashMap<>(sectAssoc.size());
					gridSectMappedAssocatedMFDs.set(i, runningSectAssoc);
				}
				
				for (int sectIndex : sectAssoc.keySet()) {
					double[] assoc = sectAssoc.get(sectIndex);
					double[] running = runningSectAssoc.get(sectIndex);
					if (running == null) {
						running = new double[assoc.length];
						runningSectAssoc.put(sectIndex, running);
					} else if (running.length < assoc.length) {
						running = Arrays.copyOf(running, assoc.length);
						runningSectAssoc.put(sectIndex, running);
					}
					for (int j=0; j<assoc.length; j++)
						if (assoc[j] > 0d)
							running[j] = Math.fma(assoc[j], relWeight, running[j]);
				}
			}
			
			sumWeight += relWeight;
		}

		@Override
		public GridSourceProvider getAverage() {
			MFDGridSourceProvider mfdAvg = (MFDGridSourceProvider)mfdAverager.getAverage();
			for (int i=0; i<gridSectMappedAssocatedMFDs.size(); i++) {
				Map<Integer, double[]> sectAssoc = gridSectMappedAssocatedMFDs.get(i);
				if (sectAssoc == null)
					continue;
				for (int sectIndex : sectAssoc.keySet()) {
					double[] assoc = sectAssoc.get(sectIndex);
					if (assoc.length != refMFD.size()) {
						assoc = Arrays.copyOf(assoc, refMFD.size());
						sectAssoc.put(sectIndex, assoc);
					}
					for (int j=0; j<assoc.length; j++)
						if (assoc[j] > 0)
							assoc[j] /= sumWeight;
				}
			}
			return new Converter(mfdAvg, gridSectMappedAssocatedMFDs, minMag, converter, refMFD);
		}
		
	}

	@Override
	public SummedMagFreqDist getMFD_SubSeisOnFault(int gridIndex) {
		return subSeisOnFaultMFD_ForGridArray[gridIndex];
	}

	@Override
	public SummedMagFreqDist getMFD_Unassociated(int gridIndex) {
		return unassociatedMFD_ForGridArray[gridIndex];
	}


//	private SummedMagFreqDist computeMFD_SubSeisOnFault(int gridIndex) {
//		SummedMagFreqDist summedMFD = initSummedMFD();
//		for(int c:cgr.getCubeIndicesForGridCell(gridIndex)) {
//			SummedMagFreqDist mfd = getSubSeismoMFD_ForCube(c);
//			if(mfd != null)
//				summedMFD.addIncrementalMagFreqDist(mfd);
//		}
//		if(summedMFD.getTotalIncrRate() < 1e-10)
//			summedMFD=null;
//		return summedMFD;
//	}
//
//
//	private SummedMagFreqDist computeMFD_Unassociated(int gridIndex) {
//		SummedMagFreqDist summedMFD = initSummedMFD();
//		for(int c:cgr.getCubeIndicesForGridCell(gridIndex)) {
//			summedMFD.addIncrementalMagFreqDist(getUnassociatedMFD_ForCube(c));
//		}
//		if(summedMFD.getTotalIncrRate() < 1e-10)
//			summedMFD=null;
//		return summedMFD;
//	}

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
	public void scaleAll(double[] valuesArray) {
		// TODO
		throw new UnsupportedOperationException("not yet implemented");
	}

	public static void main(String[] args) throws IOException {
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
//				+ "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
//				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged.zip"));
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_02_02-nshm23_branches-WUS_FM_v3/results_WUS_FM_v3_branch_averaged.zip"));
		
		sol.getRupSet().removeModuleInstances(FaultCubeAssociations.class);
		NSHM23_SingleRegionGridSourceProvider prov = (NSHM23_SingleRegionGridSourceProvider)NSHM23_InvConfigFactory.buildGridSourceProv(
				sol, NSHM23_LogicTreeBranch.AVERAGE_OFF_FAULT);
		sol.addModule(prov);
		ReportPageGen pageGen = new ReportPageGen(sol.getRupSet(), sol, "Solution", new File("/tmp/report"),
				List.of(new SolMFDPlot(), new NucleationRatePlot()));
		pageGen.setReplot(true);
		pageGen.generatePage();
		
		// now convert to a GridSoruceList
		GridSourceList list1 = prov.convertToGridSourceList();
		sol.addModule(list1);
		pageGen = new ReportPageGen(sol.getRupSet(), sol, "Solution", new File("/tmp/report_list1"),
				List.of(new SolMFDPlot(), new NucleationRatePlot()));
		pageGen.setReplot(true);
		pageGen.generatePage();
		GridSourceList list2 = GridSourceList.convert(prov, sol.getRupSet().requireModule(FaultCubeAssociations.class), new NSHM23_WUS_FiniteRuptureConverter());
		sol.addModule(list2);
		pageGen = new ReportPageGen(sol.getRupSet(), sol, "Solution", new File("/tmp/report_list2"),
				List.of(new SolMFDPlot(), new NucleationRatePlot()));
		pageGen.setReplot(true);
		pageGen.generatePage();
		
		for (int gridIndex=0; gridIndex<list1.getNumLocations(); gridIndex++) {
			List<GriddedRupture> rups1 = new ArrayList<>(list1.getRuptures(null, gridIndex));
			List<GriddedRupture> rups2 = new ArrayList<>(list2.getRuptures(null, gridIndex));
			Collections.sort(rups1);
			Collections.sort(rups2);
			Preconditions.checkState(rups1.size() == rups2.size());
			for (int i=0; i<rups1.size(); i++) {
				GriddedRupture rup1 = rups1.get(i);
				GriddedRupture rup2 = rups2.get(i);
				Preconditions.checkState(rup1.properties.magnitude == rup2.properties.magnitude);
				Preconditions.checkState((float)rup1.rate == (float)rup2.rate);
				Preconditions.checkState((float)rup1.properties.rake == (float)rup2.properties.rake);
				Preconditions.checkState((rup1.associatedSections == null) == (rup2.associatedSections == null));
				if (rup1.associatedSections != null) {
					Preconditions.checkState(rup2.associatedSections.length >= rup1.associatedSections.length);
					double fract1 = StatUtils.sum(rup1.associatedSectionFracts);
					double fract2 = StatUtils.sum(rup2.associatedSectionFracts);
					Preconditions.checkState(fract1 == fract2);
				}
			}
		}
	}

	@Override
	public TectonicRegionType getTectonicRegionType(int gridIndex) {
		return trts[gridIndex];
	}

}
