package scratch.UCERF3.utils.UpdatedUCERF2;

import java.util.ArrayList;

import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;

import com.google.common.collect.Lists;

/**
 * Due to discrepanices between the current NSHMP background seismsicity model
 * and that implemented in UCERF2, this updated version of {@code MeanUCERF2}
 * supplies the current NSHMP model to facilitate comparisons between the NSHMP,
 * UCERF2, and UCERF3.
 * 
 * This implementation also ignores the non-California b-Fault sources.
 * 
 * the current NSHMP background
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class MeanUCERF2update extends MeanUCERF2 {

	private NSHMP08_GridSourceGenerator gridSrcGen;

	public MeanUCERF2update(GridSources id) {
		gridSrcGen = new NSHMP08_GridSourceGenerator(id);
	}
	
	@Override
	public ProbEqkSource getSource(int idx) {
		return (idx < allSources.size()) ? 
			allSources.get(idx) : 
			gridSrcGen.getSource(idx - allSources.size());
	}

	@Override
	public int getNumSources() {
		if(backSeisParam.getValue().equals(UCERF2.BACK_SEIS_INCLUDE) ||
				backSeisParam.getValue().equals(UCERF2.BACK_SEIS_ONLY))
			return allSources.size() + gridSrcGen.getNumSources();
		return allSources.size();
	}
	
	@Override
	protected void mkNonCA_B_FaultSources() {
		nonCA_bFaultSources = Lists.newArrayList();
	}
	
	@Override
	protected void updateGridSources() {
		// skip adding C-zones to allSurces as they will be handled by
		// gridSrcGen, but they will also be turned off when background
		// siesmicity is turned off
		gridSrcGen.setForecastDuration(timeSpan.getDuration());
	}
	
	@Override
	public ArrayList<ProbEqkSource>  getSourceList(){
		throw new UnsupportedOperationException();
	}

}
