package scratch.UCERF3.griddedSeismicity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.DataUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

import com.google.common.collect.Maps;

/**
 * This class generates UCERF3 background seismicity assuming no fault-based sources.
 * 
 * @author Ned Field
 */
public class UCERF3_NoFaultsGridSourceGenerator extends AbstractGridSourceProvider {

	private final CaliforniaRegions.RELM_TESTING_GRIDDED region = RELM_RegionUtils.getGriddedRegionInstance();

	private double[] fracStrikeSlip,fracNormal,fracReverse;
	
	// spatial pdf of seismicity
	private double[] srcSpatialPDF;
	private double totalMgt5_Rate;
	private double maxMag;

	// total off-fault MFD (sub-seismo + background)
	private IncrementalMagFreqDist realOffFaultMFD;

	// the sub-seismogenic MFDs for those nodes that have them
	private Map<Integer, SummedMagFreqDist> nodeSubSeisMFDs;


	/**
	 * This applies a uniform spatial distribution if srcSpatialPDF=null
	 * @param srcSpatialPDF - spatial distribution of rates
	 * @param totalMgt5_Rate - total regional rate of M≥5 events
	 * @param maxMag - maximum magnitude (same for all grid nodes)
	 */
	public UCERF3_NoFaultsGridSourceGenerator(double[] srcSpatialPDF, double totalMgt5_Rate, double maxMag) {
		initFocalMechGrids();

		this.srcSpatialPDF = srcSpatialPDF;
		this.totalMgt5_Rate = totalMgt5_Rate;
		this.maxMag=maxMag;
		
		double mfdMin = 2.55;
		double mfdMax = Math.round(maxMag*10.0)/10.0-0.05;
		int mfdNum = (int)Math.round((maxMag-mfdMin)/0.1);
		
		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(1.0, 1.0,mfdMin, mfdMax, mfdNum);
		mfd.scaleToCumRate(5.05, totalMgt5_Rate);

		this.realOffFaultMFD = mfd;
		
//		System.out.println(realOffFaultMFD);
//		System.out.println(realOffFaultMFD.getCumRateDistWithOffset());
	}



	/**
	 * Returns the sum of the unassociated MFD of all nodes.
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getNodeUnassociatedMFD() {
		realOffFaultMFD.setInfo("Same as "+realOffFaultMFD.getName());
		realOffFaultMFD.setName("Unassociated MFD for all nodes");
		return realOffFaultMFD;
	}

	@Override
	public IncrementalMagFreqDist getNodeSubSeisMFD(int idx) {
		return null;
	}

	@Override
	public IncrementalMagFreqDist getNodeUnassociatedMFD(int idx) {
		IncrementalMagFreqDist mfd = realOffFaultMFD.deepClone();
		if(srcSpatialPDF !=null)
			mfd.scale(srcSpatialPDF[idx]);
		else	// apply uniform distribution
			mfd.scale(1.0/(double)region.getNodeCount());

		return mfd;
	}

	@Override
	public GriddedRegion getGriddedRegion() {
		return region;
	}

	/**
	 * Returns the sum of the sub-seismogenic MFD of all nodes,
	 * which is null here because there are no faults.
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getNodeSubSeisMFD() {
		return null;
	}


	public static void main(String[] args) {
	}

	@Override
	public double getFracStrikeSlip(int idx) {
		return fracStrikeSlip[idx];
	}


	@Override
	public double getFracReverse(int idx) {
		return fracReverse[idx];
	}


	@Override
	public double getFracNormal(int idx) {
		return fracNormal[idx];
	}
	
	private void initFocalMechGrids() {
		GridReader gRead;
		gRead = new GridReader("StrikeSlipWts.txt");
		fracStrikeSlip = gRead.getValues();
		gRead = new GridReader("ReverseWts.txt");
		fracReverse = gRead.getValues();
		gRead = new GridReader("NormalWts.txt");
		fracNormal = gRead.getValues();
	}

}
