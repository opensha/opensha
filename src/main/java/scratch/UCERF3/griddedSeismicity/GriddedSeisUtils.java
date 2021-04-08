package scratch.UCERF3.griddedSeismicity;

import java.util.List;
import java.util.Map;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;

/**
 * Utility class for working with fault polygons and spatial PDFs of seismicity.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GriddedSeisUtils {

	private FaultPolyMgr polyMgr;
	private double[] pdf;
	
	/**
	 * Create an instance of this utility class with a region
	 *
	 * @param fltSectList
	 * @param pdf pass in result of PDF allows us to use alternate SpatiolSeisPDf provider
	 * @param buf distance in km to buffer fault polygons
	 * @param region the user defined region
	 */
	public GriddedSeisUtils(List<? extends FaultSection> fltSectList, 
			double[] pdf, double buf, GriddedRegion region) {
		polyMgr = FaultPolyMgr.create(fltSectList, buf, region);
		this.pdf = pdf.clone();
	}
	
	/**
	 * Create an instance of this utility class.
	 * @param fltSectList
	 * @param pdf pass in result of PDF allows us to use alternate SpatiolSeisPDf provider
	 * @param buf distance in km to buffer fault polygons
	 */
	public GriddedSeisUtils(List<? extends FaultSection> fltSectList, 
			SpatialSeisPDF pdf, double buf) {
		polyMgr = FaultPolyMgr.create(fltSectList, buf);
		this.pdf = pdf.getPDF();
	}	
	
	/**
	 * Create an instance of this utility class.
	 * @param fsrs
	 * @param pdf - double array here
	 */
	public GriddedSeisUtils(List<? extends FaultSection> fltSectList, 
			double[] pdf, double buf) {
		polyMgr = FaultPolyMgr.create(fltSectList, buf);
		this.pdf = pdf;
	}
	

	
	/**
	 * Returns a reference to the internal polygon manager.
	 * @return
	 */
	public FaultPolyMgr getPolyMgr() {
		return polyMgr;
	}
		
	/**
	 * Returns the fraction of the spatial PDF contained inthe fault section
	 * polygons.
	 * @return
	 */
	public double pdfInPolys() {
		double fraction = 0;
		Map<Integer, Double> nodeMap = polyMgr.getNodeExtents();
		for (int idx : nodeMap.keySet()) {
			fraction += nodeMap.get(idx) * pdf[idx];
		}
		return fraction;
	}

	/**
	 * Returns the fraction of the originally supplied spatial PDF captured
	 * by the fault section at {@code idx}. This is a weighted value in as much
	 * as multiple overlapping fault in the originally supplied rupture set
	 * never 'consume' more than what's available at a node that all faults
	 * intersect.
	 * @param idx of section to retreive value of
	 * @return the pdf value
	 */
	public double pdfValForSection(int idx) {
		Map<Integer, Double> nodeMap = polyMgr.getScaledNodeFractions(idx);
		double sum = 0.0;
		for (int iNode : nodeMap.keySet()) {
			sum += pdf[iNode] * nodeMap.get(iNode);
		}
		return sum;
	}
	
	public static void main(String[] args) {
	}
}
