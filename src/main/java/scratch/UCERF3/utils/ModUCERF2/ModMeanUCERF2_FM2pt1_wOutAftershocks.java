/**
 * 
 */
package scratch.UCERF3.utils.ModUCERF2;



import java.util.ArrayList;
import java.util.Collections;

import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.FaultSegmentData;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import scratch.UCERF3.utils.ModUCERF2.UnsegmentedSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.B_FaultsFetcherForMeanUCERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data.A_FaultsFetcher;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.NSHMP_GridSourceGenerator;
import org.opensha.sha.earthquake.util.EqkSourceNameComparator;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;



/**
 * This has the same attributes of its parent (ModMeanUCERF2), except the fault model 2.2 
 * sources are removed and the fault model 2.1 sources are given double weight.  This constitutes
 * an average over all fault model 2.1 logic-tree branches.  This has been tested in the main
 * method here.
 */
public class ModMeanUCERF2_FM2pt1_wOutAftershocks extends ModMeanUCERF2_FM2pt1 {
	//for Debug purposes
	protected static String  C = new String("MeanUCERF2 Modified, FM 2.1 wOut aft");
	// name of this ERF
	public final static String NAME = new String("WGCEP (2007) UCERF2 - FM 2.1 wOut aftershocks");


 	
	public ModMeanUCERF2_FM2pt1_wOutAftershocks() {
		super();
	 	nshmp_gridSrcGen = new NSHMP_GridSourceGenerator();

	}

	
	
	/**
	 * Return the name for this class
	 *
	 * @return : return the name for this class
	 */
	public String getName(){
		return NAME;
	}


	

	// this is temporary for testing purposes
	public static void main(String[] args) {
		
	}
}