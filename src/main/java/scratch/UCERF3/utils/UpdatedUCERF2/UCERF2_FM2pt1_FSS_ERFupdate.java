package scratch.UCERF3.utils.UpdatedUCERF2;

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
public class UCERF2_FM2pt1_FSS_ERFupdate extends FaultSystemSolutionERF {
	
	NSHMP08_GridSourceGenerator gridSrcGen;
	
	public UCERF2_FM2pt1_FSS_ERFupdate() {
		super(UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(FaultModels.FM2_1));
		gridSrcGen = new NSHMP08_GridSourceGenerator(GridSources.ALL);
		setParameter(AleatoryMagAreaStdDevParam.NAME, 0.12);
		setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
	}
	
	@Override
	protected ProbEqkSource getOtherSource(int srcIdx) {
		return gridSrcGen.getSource(srcIdx);
	}
	
	// this is called by updateForecast() in parent
	@Override
	protected boolean initOtherSources() {
		// always point sources - fixed strike included
		numOtherSources = gridSrcGen.getNumSources();
		gridSrcGen.setForecastDuration(timeSpan.getDuration());
		return true;
	}

}
