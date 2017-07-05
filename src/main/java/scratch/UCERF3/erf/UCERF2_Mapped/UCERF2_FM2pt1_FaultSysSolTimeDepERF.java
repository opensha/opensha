package scratch.UCERF3.erf.UCERF2_Mapped;

import java.util.ArrayList;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.param.BPT_AperiodicityParam;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.NSHMP_GridSourceGenerator;

import java.awt.Toolkit;
import java.io.File;


import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.ModUCERF2.NSHMP_GridSourceGeneratorMod2;


/**
 * 
 * NOTE I changed this to extend FaultSystemSolutionERF rather than FaultSystemSolutionTimeDepERF on
 * July 26, 2014, and as a result, I had to comment out a couple errors in the main method here
 * (these will need to be fixed before that method can be run again)
 * 
 * Note that this uses NSHMP_GridSourceGeneratorMod2 which goes down to M 2.5,
 *  adds aftershocks back in, and excludes C zones (fixed strike sources)
 * @author field
 *
 */
public class UCERF2_FM2pt1_FaultSysSolTimeDepERF extends FaultSystemSolutionERF {

//	NSHMP_GridSourceGenerator nshmp_gridSrcGen;
	NSHMP_GridSourceGeneratorMod2 nshmp_gridSrcGen;
	
	public UCERF2_FM2pt1_FaultSysSolTimeDepERF() {
		super(UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1));
		nshmp_gridSrcGen = new NSHMP_GridSourceGeneratorMod2();
		// treat as point sources
		nshmp_gridSrcGen.setAsPointSources(true);
		numOtherSources = nshmp_gridSrcGen.getNumSources();
//		numOtherSources=0;
//		System.out.println("numOtherSources="+numOtherSources);

	}
	
	
	@Override
	protected ProbEqkSource getOtherSource(int iSource) {
		return nshmp_gridSrcGen.getRandomStrikeGriddedSource(iSource, timeSpan.getDuration());
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		UCERF2_FM2pt1_FaultSysSolTimeDepERF erf = new UCERF2_FM2pt1_FaultSysSolTimeDepERF();
		erf.aleatoryMagAreaStdDevParam.setValue(0.0);
		erf.getParameter(BPT_AperiodicityParam.NAME).setValue(0.2);
//		erf.bpt_AperiodicityParam.setValue(0.2);
		erf.getTimeSpan().setStartTimeInMillis(0);
		erf.getTimeSpan().setDuration(1);
		long runtime = System.currentTimeMillis();
		// make the gridded region
//		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		CaliforniaRegions.RELM_GRIDDED griddedRegion = new CaliforniaRegions.RELM_GRIDDED();
		
//		System.out.println("Location(37.00000, -119.30000, 0.00000):\t"+griddedRegion.indexForLocation(new Location(37.00000, -119.30000, 0.00000)));
//		System.out.println("Location(37.10000, -119.30000, 0.00000):\t"+griddedRegion.indexForLocation(new Location(37.10000, -119.30000, 0.00000)));
//		System.out.println("Location(37.00000, -119.40000, 0.00000):\t"+griddedRegion.indexForLocation(new Location(37.00000, -119.4000, 0.00000)));
//		System.out.println("Location(37.10000, -119.40000, 0.00000):\t"+griddedRegion.indexForLocation(new Location(37.10000, -119.4000, 0.00000)));
//		System.out.println("Location(37.05000, -119.35000, 0.00000):\t"+griddedRegion.indexForLocation(new Location(37.10000, -119.4000, 0.00000)));
//		System.exit(0);
		
		// update forecast to we can get a main shock
		erf.updateForecast();
		
		// get the rupture index of a Landers rupture
		int nthRup = erf.getIndexN_ForSrcAndRupIndices(4755, 0);
		ProbEqkRupture landers = erf.getSource(4755).getRupture(0);
		ETAS_EqkRupture landersObs = new ETAS_EqkRupture();
		landersObs.setAveRake(landers.getAveRake());
		landersObs.setMag(landers.getMag());
		
//		Location ptSurf = new Location(41.0,-121.45,2.0);	// this is Not good beyond logDist = 0.6.
		
		// treat as point source
//		Location ptSurf = new Location(34.30,-116.44,0.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,1.0);	//
//		Location ptSurf = new Location(34.30,-116.44,2.0);	// 
//		Location ptSurf = new Location(34.31,-116.45,2.0);	//
//		Location ptSurf = new Location(34.30,-116.44,3.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,4.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,5.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,6.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,7.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,8.0);	//
//		Location ptSurf = new Location(34.30,-116.44,9.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,10.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,11.0);	//
//		Location ptSurf = new Location(34.30,-116.44,12.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,13.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,14.0);	//
//		Location ptSurf = new Location(34.30,-116.44,15.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,16.0);	//
//		Location ptSurf = new Location(34.30,-116.44,17.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,18.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,19.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,20.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,21.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,22.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,23.0);	// 
//		Location ptSurf = new Location(34.30,-116.44,24.0);	// 
//		Location ptSurf = new Location(34.31,-116.45,0.0);	// 
		
//		System.out.println("Min dist to edge of RELM region: "+griddedRegion.getBorder().minDistToLocation(ptSurf));
//		System.out.println("Landers pt src loc: "+ptSurf);
//		landersObs.setPointSurface(ptSurf);
		
		
//		landersObs.setRuptureSurface(landers.getRuptureSurface());
//		landersObs.setOriginTime(0);	// occurs at 1970
		
		// Test edge of RELM region triggering
//		Location ptSurf = new Location(34.30,-116.44,13.0);	//
		Location ptSurf = new Location(34.40,-113.19,13.0);	//
		landersObs.setMag(7);
		landersObs.setPointSurface(ptSurf);

		
//		landersObs.setMag(8.5);	// set higher to spawn more aftershocks for testing
		System.out.println("main shock: s=4755, r=0, nthRup="+nthRup+"mag="+landersObs.getMag()+
				"; src name: " +erf.getSource(4755).getName());
		
		ArrayList<ETAS_EqkRupture> obsEqkRuptureList = new ArrayList<ETAS_EqkRupture>();
		obsEqkRuptureList.add(landersObs);
		
//		erf.setRuptureOccurrenceTimePred(nthRup, 0);
		
		boolean includeSpontEvents=false;
		boolean includeIndirectTriggering=false;
		boolean includeEqkRates = false;
//		erf.testETAS_Simulation(griddedRegion, obsEqkRuptureList, includeSpontEvents, includeIndirectTriggering,
//				includeEqkRates, 0.1);
//		erf.testETAS_SimulationOld(griddedRegion, obsEqkRuptureList);

//		erf.testER_Simulation();
		runtime -= System.currentTimeMillis();
		System.out.println("simulation took "+(double)runtime/(1000.0*60.0)+" minutes");


	}

}
