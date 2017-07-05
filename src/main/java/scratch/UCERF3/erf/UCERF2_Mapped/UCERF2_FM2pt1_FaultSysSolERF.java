package scratch.UCERF3.erf.UCERF2_Mapped;

import java.util.ArrayList;

import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.NSHMP_GridSourceGenerator;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt1_wOutAftershocks;


/**
 * Deal with this: applyAftershockFilter
 * 
 * @author field
 *
 */
public class UCERF2_FM2pt1_FaultSysSolERF extends FaultSystemSolutionERF {
	
	private static final boolean D = false;

	NSHMP_GridSourceGenerator nshmp_gridSrcGen;
	
	protected ArrayList<ProbEqkSource> fixedStrikeSources;	// type C zone sources
	
	protected int numGridSources;	// not including fixedStrikeSources

	
	public UCERF2_FM2pt1_FaultSysSolERF() {
		super(UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1));
		nshmp_gridSrcGen = new NSHMP_GridSourceGenerator();
//		initOtherSources(); // NOTE called by parent in updateForecast()
		setParameter(AleatoryMagAreaStdDevParam.NAME, 0.12);
		setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
	}
	
	
	@Override
	protected ProbEqkSource getOtherSource(int iSource) {
		
		if(iSource < numGridSources) {
			if(bgRupType.equals(BackgroundRupType.CROSSHAIR))
				return nshmp_gridSrcGen.getCrosshairGriddedSource(iSource, timeSpan.getDuration());	
			else
				return nshmp_gridSrcGen.getRandomStrikeGriddedSource(iSource, timeSpan.getDuration());			
		}
		else {
			return fixedStrikeSources.get(iSource - numGridSources);
		}
	}
	
	
	@Override
	protected boolean initOtherSources() {
			if (bgRupType.equals(BackgroundRupType.POINT))
				nshmp_gridSrcGen.setAsPointSources(true);
			else
				nshmp_gridSrcGen.setAsPointSources(false);
			
			fixedStrikeSources = new ArrayList<ProbEqkSource>();
			fixedStrikeSources.addAll(nshmp_gridSrcGen.getAllFixedStrikeSources(timeSpan.getDuration()));
			
			// update source count
			numGridSources = nshmp_gridSrcGen.getNumSources();
			numOtherSources = numGridSources+fixedStrikeSources.size();
			
			if(D) {
				System.out.println("numFaultSystemSources="+numNonZeroFaultSystemSources);
				System.out.println("numOtherSources="+numOtherSources);
				System.out.println("numGridSources="+numGridSources);
				System.out.println("numFixedStrikeSources="+fixedStrikeSources.size());
			}
			return true;
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		UCERF2_FM2pt1_FaultSysSolERF erf = new UCERF2_FM2pt1_FaultSysSolERF();
//		erf.getTimeSpan().setDuration(50.0);
//		long runtime = System.currentTimeMillis();
//		
//		// update forecast to we can get a main shock
//		erf.updateForecast();
//		
//		runtime -= System.currentTimeMillis();
//		System.out.println("Instantiation took "+(double)runtime/(1000.0)+" seconds");
		
		ModMeanUCERF2_FM2pt1_wOutAftershocks testERF = new ModMeanUCERF2_FM2pt1_wOutAftershocks();
		// CA fault source indices:	0 to 274
		// Non CA fault source indices: 274 to 393
		// fixed strike grid source indices: 394 to 1904 
		// background seis source indices: 1905 to 9647
		testERF.updateForecast();
		for(int s=0; s<testERF.getNumSources();s++) {
			System.out.println(s+"\t"+testERF.getSource(s).getName());
		}
	}
}
