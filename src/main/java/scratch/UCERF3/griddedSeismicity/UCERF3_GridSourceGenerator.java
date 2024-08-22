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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import scratch.UCERF3.U3FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

import com.google.common.collect.Maps;

/**
 * This class generates UCERF3 background seismicity (gridded) sources.
 * 
 * @author Ned Field
 * @author Peter Powers
 */
public class UCERF3_GridSourceGenerator extends AbstractGridSourceProvider {

	private final CaliforniaRegions.RELM_TESTING_GRIDDED region = RELM_RegionUtils.getGriddedRegionInstance();

	private static double[] fracStrikeSlip,fracNormal,fracReverse;
	private FaultGridAssociations polyMgr;
	
	// spatial pdfs of seismicity, orginal and revised (reduced and
	// renormalized) to avoid double counting with fault polygons
	private double[] srcSpatialPDF;
	private double[] revisedSpatialPDF;
	
//	private double totalMgt5_Rate;

	// total off-fault MFD (sub-seismo + background)
	private IncrementalMagFreqDist realOffFaultMFD;

	// the sub-seismogenic MFDs for those nodes that have them
	private Map<Integer, SummedMagFreqDist> nodeSubSeisMFDs;

	// the sub-seismogenic MFDs for each section
	private Map<Integer, IncrementalMagFreqDist> sectSubSeisMFDs;

	// reference mfd values
	private double mfdMin = 5.05;
	private double mfdMax = 8.45;
	private int mfdNum = 35;

	/**
	 * Options:
	 * 
	 * 1) set a-values in fault-section polygons from moment-rate reduction or
	 * from smoothed seismicity
	 * 
	 * 2) focal mechanism options, and finite vs point
	 * sources (cross hair, random strike, etc)?
	 * 
	 * @param ifss {@code InversionFaultSystemSolution} for which
	 *        grided/background sources should be generated
	 */
	public UCERF3_GridSourceGenerator(InversionFaultSystemSolution ifss) {
		this(ifss, ifss.getLogicTreeBranch().getValue(SpatialSeisPDF.class),
				ifss.getLogicTreeBranch().getValue(MomentRateFixes.class), ifss.getRupSet().getInversionTargetMFDs(),
				ifss.requireModule(SubSeismoOnFaultMFDs.class),
				ifss.getLogicTreeBranch().getValue(MaxMagOffFault.class).getMaxMagOffFault(),
				ifss.getRupSet().requireModule(FaultGridAssociations.class));
	}

	/**
	 * Options:
	 * 
	 * 1) set a-values in fault-section polygons from moment-rate reduction or
	 * from smoothed seismicity
	 * 
	 * 2) focal mechanism options, and finite vs point
	 * sources (cross hair, random strike, etc)?
	 * 
	 * @param ifss {@code InversionFaultSystemSolution} for which
	 *        grided/background sources should be generated
	 */
	public UCERF3_GridSourceGenerator(FaultSystemSolution fss, SpatialSeisPDF srcSpatialPDF, MomentRateFixes rateFixes,
			InversionTargetMFDs targetMFDs, SubSeismoOnFaultMFDs subSeisMFDs, double mMaxOffFault,
			FaultGridAssociations polyMgr) {
		this.srcSpatialPDF = srcSpatialPDF.getPDF();
//		totalMgt5_Rate = branch.getValue(TotalMag5Rate.class).getRateMag5();
		IncrementalMagFreqDist totalNuclMFD = fss.calcNucleationMFD_forRegion(RELM_RegionUtils.getGriddedRegionInstance(),
				U3InversionTargetMFDs.MIN_MAG, U3InversionTargetMFDs.MAX_MAG, U3InversionTargetMFDs.DELTA_MAG, true);
		realOffFaultMFD = InversionFaultSystemSolution.getFinalTrulyOffFaultMFD(targetMFDs, rateFixes, mMaxOffFault,
				subSeisMFDs.getTotal(), totalNuclMFD);

		mfdMin = realOffFaultMFD.getMinX();
		mfdMax = realOffFaultMFD.getMaxX();
		mfdNum = realOffFaultMFD.size();

//		polyMgr = FaultPolyMgr.create(fss.getFaultSectionDataList(), 12d);
		this.polyMgr = polyMgr;
//		polyMgr = ifss.getRupSet().getInversionTargetMFDs().getGridSeisUtils().getPolyMgr();

		System.out.println("   initSectionMFDs() ...");
		initSectionMFDs(fss, subSeisMFDs);
		System.out.println("   initNodeMFDs() ...");
		initNodeMFDs(fss);
		System.out.println("   updateSpatialPDF() ...");
		updateSpatialPDF();
	}


	/*
	 * Initialize the sub-seismogenic MFDs for each fault section
	 * (sectSubSeisMFDs)
	 */
	private void initSectionMFDs(FaultSystemSolution fss, SubSeismoOnFaultMFDs subSeisMFDs) {

		List<? extends IncrementalMagFreqDist> subSeisMFD_list =  subSeisMFDs.getAll();

		sectSubSeisMFDs = Maps.newHashMap();
		List<? extends FaultSection> faults = fss.getRupSet().getFaultSectionDataList();
		for (int i = 0; i < faults.size(); i++) {
			sectSubSeisMFDs.put(
				faults.get(i).getSectionId(),
				subSeisMFD_list.get(i));
		}
	}

	/*
	 * Initialize the sub-seismogenic MFDs for each grid node
	 * (nodeSubSeisMFDs) by partitioning the sectSubSeisMFDs according to
	 * the overlapping fraction of each fault section and grid node.
	 */
	private void initNodeMFDs(FaultSystemSolution fss) {
		nodeSubSeisMFDs = Maps.newHashMap();
		for (FaultSection sect : fss.getRupSet().getFaultSectionDataList()) {
			int id = sect.getSectionId();
			IncrementalMagFreqDist sectSubSeisMFD = sectSubSeisMFDs.get(id);
			Map<Integer, Double> nodeFractions = polyMgr.getNodeFractions(id);
			for (Integer nodeIdx : nodeFractions.keySet()) {
				SummedMagFreqDist nodeMFD = nodeSubSeisMFDs.get(nodeIdx);
				if (nodeMFD == null) {
					nodeMFD = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
					nodeSubSeisMFDs.put(nodeIdx, nodeMFD);
				}
				double scale = nodeFractions.get(nodeIdx);
				IncrementalMagFreqDist scaledMFD = sectSubSeisMFD.deepClone();
				scaledMFD.scale(scale);
				nodeMFD.addIncrementalMagFreqDist(scaledMFD);
			}
		}
	}

	/*
	 * Update (normalize) the spatial PDF to account for those nodes that
	 * are partially of fully occupied by faults to whom all small magnitude
	 * events will have been apportioned.
	 */
	private void updateSpatialPDF() {
		// update pdf
		revisedSpatialPDF = new double[srcSpatialPDF.length];
		for (int i=0; i<region.getNodeCount(); i++) {
			double fraction = 1 - polyMgr.getNodeFraction(i);
			revisedSpatialPDF[i] = srcSpatialPDF[i] * fraction;
		}
		// normalize
		DataUtils.asWeights(revisedSpatialPDF);
	}

	/**
	 * Returns the sub-seismogenic MFD associated with a section.
	 * @param idx sub section index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getSectSubSeisMFD(int idx) {
		return sectSubSeisMFDs.get(idx);
	}

	/**
	 * Returns the sum of the sub-seismogenic MFDs of all fault sub-sections.
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getSectSubSeisMFD() {
		SummedMagFreqDist sum = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
		sum.setName("Sub-seismogenic MFD for all fault sections");
		for (IncrementalMagFreqDist mfd : sectSubSeisMFDs.values()) {
			sum.addIncrementalMagFreqDist(mfd);
		}
		return sum;
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
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(int idx) {
		return nodeSubSeisMFDs.get(idx);
	}

	@Override
	public IncrementalMagFreqDist getMFD_Unassociated(int idx) {
		IncrementalMagFreqDist mfd = realOffFaultMFD.deepClone();
		mfd.scale(revisedSpatialPDF[idx]);
		return mfd;
	}

	@Override
	public GriddedRegion getGriddedRegion() {
		return region;
	}

	/**
	 * Returns the sum of the sub-seismogenic MFD of all nodes.
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getNodeSubSeisMFD() {
		SummedMagFreqDist sum = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
		sum.setName("Sub-seismogenic MFD for all nodes");
		for (IncrementalMagFreqDist mfd : nodeSubSeisMFDs.values()) {
			sum.addIncrementalMagFreqDist(mfd);
		}
		return sum;
	}

//	/**
//	 * Returns the MFD associated with a grid node, implied by the
//	 * {@code spatialPDF} of seismicity and the {@code totalMgt5_Rate} supplied
//	 * at initialization.
//	 * @param inPoly {@code true} for MFD associated with fault polygons,
//	 *        {@code false} if unassociated part requested
//	 * @param idx node index
//	 * @return the MFD
//	 */
//	public IncrementalMagFreqDist getSpatialMFD(boolean inPoly, int idx) {
//		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
//			mfdMin, mfdMax, mfdNum);
//		mfd.setAllButTotMoRate(mfdMin, mfdMax, totalMgt5_Rate, 0.8);
//		double frac = polyMgr.getNodeFraction(idx);
//		if (!inPoly) frac = 1 - frac;
//		mfd.scale(frac);
//		return mfd;
//	}



	public static void main(String[] args) {
		//
		//		GutenbergRichterMagFreqDist grMFD = new GutenbergRichterMagFreqDist(1.0, 1.0, 5.05, 8.05, 31);
		//		System.out.println(grMFD);
		//		scaleMFD(grMFD);
		//		System.out.println(grMFD);

		InversionFaultSystemSolution invFss = null;
		try {
			//			File f = new File("tmp/invSols/reference_ch_sol2.zip");
//			File f = new File("/Users/pmpowers/projects/OpenSHA/tmp/invSols/refCH/FM3_1_NEOK_EllB_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip");
			File f = new File("/Users/pmpowers/projects/OpenSHA/tmp/invSols/refGR/FM3_1_NEOK_EllB_DsrUni_GRUnconst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip");
			System.out.println(f.exists());
			invFss = U3FaultSystemIO.loadInvSol(f);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(invFss);
		int numSrcs = gridGen.getNumLocations();
		int numRups = 0;
		System.out.println("numSrcs: " + numSrcs);
		for (int i=0; i<numSrcs; i++) {
			numRups += gridGen.getSource(i, 1, null, BackgroundRupType.POINT).getNumRuptures();
		}
		System.out.println("numRups: " + numRups);

		// gr nofix error test
// Commented out by ned because method turned private for safety
//				List<GutenbergRichterMagFreqDist> list = invFss.getImpliedSubSeisGR_MFD_List();
//				System.out.println(list.size());
		
		//		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(
		//			invFss, null, SpatialSeisPDF.UCERF3, 8.54, SmallMagScaling.MO_REDUCTION);
		//		System.out.println("init done");


		//		Location loc = new Location(36, -119);
		//		Location loc = new Location(34, -118.5);
		//		int locIdx = region.indexForLocation(loc);
		//		System.out.println(loc+ " " + locIdx);
		//
		//		System.out.println("SubSeis");
		//		System.out.println(gridGen.getNodeSubSeisMFD(locIdx));
		//		System.out.println("Indep");
		//		System.out.println(gridGen.getNodeIndependentMFD(locIdx));
		//		System.out.println("Total");
		//		System.out.println(gridGen.getNodeTotalMFD(locIdx));
		//
		//		Point2Vert_FaultPoisSource peq = (Point2Vert_FaultPoisSource) gridGen.getSource(GridSourceType.CROSSHAIR, locIdx, 1);
		//		System.out.println("EqRup");
		//		System.out.println( peq.getMFD());

	}

	static void plot(ArrayList<IncrementalMagFreqDist> mfds) {
		GraphWindow graph = new GraphWindow(mfds,
				"GridSeis Test");
		graph.setX_AxisLabel("Magnitude");
		graph.setY_AxisLabel("Incremental Rate");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-8, 1e2);

	}

	@Override
	public double getFracStrikeSlip(int idx) {
		checkInitFocalMechGrids();
		return fracStrikeSlip[idx];
	}


	@Override
	public double getFracReverse(int idx) {
		checkInitFocalMechGrids();
		return fracReverse[idx];
	}


	@Override
	public double getFracNormal(int idx) {
		checkInitFocalMechGrids();
		return fracNormal[idx];
	}
	
	private synchronized static void checkInitFocalMechGrids() {
		if (fracStrikeSlip == null)
			fracStrikeSlip = new GridReader("StrikeSlipWts.txt").getValues();
		if (fracReverse == null)
			fracReverse = new GridReader("ReverseWts.txt").getValues();
		if (fracNormal == null)
			fracNormal = new GridReader("NormalWts.txt").getValues();
	}

	@Override
	public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
			double[] fracReverse, TectonicRegionType[] trts) {
		return new AbstractGridSourceProvider.Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
				fracStrikeSlip, fracNormal, fracReverse, trts);
	}

}
