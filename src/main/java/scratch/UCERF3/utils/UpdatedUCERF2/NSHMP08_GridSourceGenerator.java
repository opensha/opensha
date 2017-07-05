package scratch.UCERF3.utils.UpdatedUCERF2;

import static scratch.UCERF3.utils.UpdatedUCERF2.GridSources.*;

import java.util.Arrays;
import java.util.List;

import org.opensha.nshmp2.erf.NSHMP2008;
import org.opensha.nshmp2.erf.source.GridERF;
import org.opensha.nshmp2.erf.source.NSHMP_ERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_GridSourceGenerator {
	
	private NSHMP2008 gridListERF;
	
	// array of summed erf source counts to facilitate
	// indexing of nested erfs
	private int[] erfIndices;
	
	public NSHMP08_GridSourceGenerator(GridSources id) {
		gridListERF = (id == ALL)
			? NSHMP2008.createCaliforniaGridded()
			: (id == FIX_STRK)
				? NSHMP2008.createCaliforniaFixedStrk()
				: NSHMP2008.createCaliforniaPointSrc();
//		System.out.println(gridListERF);
		List<Integer> indexList = Lists.newArrayList();
		int total = 0;
		for (ERF erf : gridListERF) {
			// need to include an initial 0 value
			// and can skip last value
			indexList.add(total);
			total += erf.getNumSources();
		}
		erfIndices = Ints.toArray(indexList);
		
		// NOTE:
		// adjust mfd rates of sources; this is possible becuase the mfds can
		// be accessed publicly without cloning (and therefore adjusted),
		// this isn't good
		
		for (NSHMP_ERF erf : gridListERF) {
			GridERF gerf = (GridERF) erf;
			gerf.scaleRatesToWeight();
		}
	}
	
	public ProbEqkSource getSource(int srcIdx) {
		int erfIdx = Arrays.binarySearch(erfIndices, srcIdx);
		// for index matches, select the next highest erf
		erfIdx = (erfIdx < 0) ? -(erfIdx + 2) : erfIdx;
		srcIdx = srcIdx - erfIndices[erfIdx];
		return gridListERF.getERF(erfIdx).getSource(srcIdx);
		
//		int erfIdx2 = -1, srcIdx2 = -1;
//		int erfIdx1 = Arrays.binarySearch(erfIndices, srcIdx);
//		try {
//			erfIdx2 = erfIdx1;
//			if (erfIdx1 < 0) erfIdx2 = -(erfIdx1 + 1);
//			srcIdx2 = srcIdx - erfIndices[erfIdx2];
//			return gridListERF.getERF(erfIdx2).getSource(srcIdx);
//		} catch (ArrayIndexOutOfBoundsException e) {
//			System.out.println(srcIdx);
//			System.out.println(erfIdx1);
//			System.out.println(erfIdx2);
//			System.out.println(srcIdx2);
//			System.out.println(Arrays.toString(erfIndices));
//			e.printStackTrace();
//		}
//		return null;
	}
	
	public int getNumSources() {
		return gridListERF.getSourceCount();
	}
	
	public void setForecastDuration(double duration) {
		gridListERF.getTimeSpan().setDuration(duration);
	}

}
