package scratch.UCERF3.griddedSeismicity;

import java.util.List;
import java.util.Map;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;

/**
 * Utility class for working with fault polygons and spatial PDFs of seismicity.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GriddedSeisUtils {

	private PolygonFaultGridAssociations polyMgr;
	private double[] pdf;
	
	/**
	 * Create an instance of this utility class with a region
	 *
	 * @param fltSectList
	 * @param pdf pass in result of PDF allows us to use alternate SpatiolSeisPDf provider
	 * @param polyMgr fault polygon manager
	 */
	public GriddedSeisUtils(List<? extends FaultSection> fltSectList, 
			double[] pdf, PolygonFaultGridAssociations polyMgr) {
		this.polyMgr = polyMgr;
		this.pdf = pdf.clone();
	}
	
	/**
	 * Create an instance of this utility class.
	 * @param fltSectList
	 * @param pdf pass in result of PDF allows us to use alternate SpatiolSeisPDf provider
	 * @param polyMgr fault polygon manager
	 */
	public GriddedSeisUtils(List<? extends FaultSection> fltSectList, 
			SpatialSeisPDF pdf, PolygonFaultGridAssociations polyMgr) {
		this.polyMgr = polyMgr;
		this.pdf = pdf.getPDF();
	}
	
	/**
	 * Returns a reference to the internal polygon manager.
	 * @return
	 */
	public PolygonFaultGridAssociations getPolyMgr() {
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
		for (int idx=0; idx<pdf.length; idx++)
			if (nodeMap.containsKey(idx))
				fraction += nodeMap.get(idx) * pdf[idx];
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
		for (int iNode=0; iNode<pdf.length; iNode++)
			if (nodeMap.containsKey(iNode))
				sum += pdf[iNode] * nodeMap.get(iNode);
		return sum;
	}
	
	public static void main(String[] args) {
	}
}
