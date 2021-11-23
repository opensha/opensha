/**
 * 
 */
package scratch.UCERF3.inversion;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.analysis.DeformationModelsCalc;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.laughTest.OldPlausibilityConfiguration;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSectionDataWriter;
import scratch.UCERF3.utils.U3SectionMFD_constraint;

/**
 * This class represents a FaultSystemRupSet for the Grand Inversion.
 * 
 * Important Notes:
 * 
 * 1) If the sections are actually subsections of larger sections, then the method 
 * computeCloseSubSectionsListList() only allows one connection between parent sections
 * (to avoid ruptures jumping back and forth for closely spaced and parallel sections).
 * Is this potentially problematic?
 * 
 * 2) Aseismicity reduces area here
 *
 * 
 * TO DO:
 * 
 * a) Make the moment-rate reduction better (section specific)?
 * 
 * b) Add the following methods from the old version (../oldStuff/RupsInFaultSystemInversion) ????:
 * 
 * 		writeCloseSubSections() 
 * 
 * 
 * @author Field, Milner, Page, & Powers
 *
 */
public class InversionFaultSystemRupSet extends SlipAlongRuptureModelRupSet {

	protected final static boolean D = false;  // for debugging
	//	static boolean applySubSeismoMomentReduction = true; // set to false to turn off reductions to slip rate from subseismogenic-rup moment

	// following are defined in constructor
	private DeformationModels defModName;
	private FaultModels faultModel;
	private String deformationModelString;
	private SlipAlongRuptureModels slipModelType;
	private ScalingRelationships scalingRelationship;
	private InversionModels inversionModel;
	private double totalRegionRateMgt5;
	private double mMaxOffFault;
	private boolean applyImpliedCouplingCoeff;
	private SpatialSeisPDF spatialSeisPDF;
	
	private List<? extends FaultSection> faultSectionData;

	private U3LogicTreeBranch logicTreeBranch;

	private OldPlausibilityConfiguration filter;

	// rupture attributes (all in SI units)
	final static double MIN_MO_RATE_REDUCTION = 0.1;
	private double[] rupMeanSlip;

	// cluster information
	private List<List<Integer>> clusterRups;
	private List<List<Integer>> clusterSects;

	private List<List<Integer>> sectionConnectionsListList;

	private Map<IDPairing, Double> subSectionDistances;

	public final static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 6.0;
	private boolean[] isRupBelowMinMagsForSects;

 	/**
 	 * Create a new InversionFaultSystemRupSet for the given rupture set & branch.
 	 * 
 	 * @param rupSet
 	 * @param branch
 	 */
 	@SuppressWarnings("unused")
	public InversionFaultSystemRupSet(FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
		super(branch.getValue(SlipAlongRuptureModels.class));
		init(branch);		
		init(rupSet);
		
		/*
		 * TODO: 
		 *  - perhaps we have a rupture set with no cluster_ruptures.json?
		 *  - Or maybe we just want to recompute? 
		 * 
		 * Beware, there's a lot going on in this class and the other constructors
		 * are quite different.
		 * 
		 * As I did this first and got it working, I decided to leave this here for now - CBC.
		 * 
		 */
		//		if (rupSet.getClusterRuptures() == null) {
		if (!rupSet.hasModule(ClusterRuptures.class)) {
			PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class);
			if (plausibility != null) {
				SectionDistanceAzimuthCalculator distCalc = plausibility.getDistAzCalc(); 
				double maxDist = plausibility.getConnectionStrategy().getMaxJumpDist();
				boolean cumulativeJumps = true;
				RuptureConnectionSearch search = new RuptureConnectionSearch(rupSet,
						distCalc, maxDist, cumulativeJumps); 
				rupSet.addModule(ClusterRuptures.instance(this, search));
			}
		} else {
			// When we have a rupset loaded from disk with cluster_ruptures.json, this should be enough...
			addModule(rupSet.getModule(ClusterRuptures.class)); 			
		}
 	}
	
	/**
	 * This creates a new InversionFaultSystemRupSet for the given cluster list, which may or may have been
	 * generated with this deformation model (but needs to be generated with this fault model!).
	 * 
	 * @param branch
	 * @param sectionClusterList
	 * @param faultSectionData
	 */
	public InversionFaultSystemRupSet(U3LogicTreeBranch branch, SectionClusterList sectionClusterList,
			List<? extends FaultSection> faultSectionData) {
		super(branch.getValue(SlipAlongRuptureModels.class));

		Preconditions.checkNotNull(branch, "LogicTreeBranch cannot be null!");
		Preconditions.checkArgument(branch.isFullySpecified(), "LogicTreeBranch must be fully specified.");

		if (faultSectionData == null)
			// default to using the fault section data from the clusters
			faultSectionData = sectionClusterList.getFaultSectionData();
		
		this.faultSectionData = faultSectionData;

		init(branch);
		this.filter = sectionClusterList.getPlausibilityConfiguration();
		this.subSectionDistances = sectionClusterList.getSubSectionDistances();
		this.sectionConnectionsListList = sectionClusterList.getSectionConnectionsListList();

		// check that indices are same as sectionIDs (this is assumed here)
		for(int i=0; i<faultSectionData.size();i++)
			Preconditions.checkState(faultSectionData.get(i).getSectionId() == i,
					"RupsInFaultSystemInversion: Error - indices of faultSectionData don't match IDs");

		// calculate rupture magnitude and other attributes
		calcRuptureAttributes(faultSectionData, sectionClusterList);
	}
	
	/**
	 * Constructor with everything already computed, mostly to be used for rup sets loaded from files
	 * 
	 * @param rupSet
	 * @param branch
	 * @param filter
	 * @param rupAveSlips
	 * @param sectionConnectionsListList
	 * @param clusterRups
	 * @param clusterSects
	 */
	public InversionFaultSystemRupSet(
			FaultSystemRupSet rupSet,
			U3LogicTreeBranch branch,
			OldPlausibilityConfiguration filter,
			double[] rupAveSlips,
			List<List<Integer>> sectionConnectionsListList,
			List<List<Integer>> clusterRups,
			List<List<Integer>> clusterSects) {
		super(branch.getValue(SlipAlongRuptureModels.class));
		setLogicTreeBranch(branch); // needed by init(rupSet)
		init(rupSet);
		init(branch);
		
		int numSects = rupSet.getNumSections();
		int numRups = rupSet.getNumRuptures();
		
		Preconditions.checkArgument(rupAveSlips == null
				|| rupAveSlips.length == getNumRuptures(), "rupAveSlips sizes inconsistent!");
		this.rupMeanSlip = rupAveSlips;
		
		// can partially empty but we at least need FM/DM/Scale
		Preconditions.checkNotNull(branch, "LogicTreeBranch cannot be null");
		if (!branch.isFullySpecified())
			System.err.println("WARNING: LogicTreeBranch not fully specified");
		
		// can be null
		this.filter = filter;
		
		Preconditions.checkArgument(sectionConnectionsListList == null || sectionConnectionsListList.size() == numSects,
				"close sub section size doesn't match number of sections!");
		this.sectionConnectionsListList = sectionConnectionsListList;
		
		// can be null
		this.clusterRups = clusterRups;
		
		// can be null
		this.clusterSects = clusterSects;
	}

	private void init(U3LogicTreeBranch branch) {
		if (branch.hasValue(FaultModels.class))
			this.faultModel = branch.getValue(FaultModels.class);
		if (branch.hasValue(DeformationModels.class))
			this.defModName = branch.getValue(DeformationModels.class);
		if (branch.hasValue(ScalingRelationships.class)) {
			this.scalingRelationship = branch.getValue(ScalingRelationships.class);
			addModule(AveSlipModule.forModel(this, scalingRelationship));
		}
		if (branch.hasValue(SlipAlongRuptureModels.class))
			this.slipModelType = branch.getValue(SlipAlongRuptureModels.class);
		if (branch.hasValue(InversionModels.class))
			this.inversionModel = branch.getValue(InversionModels.class);
		if (branch.hasValue(TotalMag5Rate.class))
			this.totalRegionRateMgt5 = branch.getValue(TotalMag5Rate.class).getRateMag5();
		if (branch.hasValue(MaxMagOffFault.class))
			this.mMaxOffFault = branch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		if (branch.hasValue(MomentRateFixes.class))
			this.applyImpliedCouplingCoeff = branch.getValue(MomentRateFixes.class).isApplyCC();
		if (branch.hasValue(SpatialSeisPDF.class))
			this.spatialSeisPDF = branch.getValue(SpatialSeisPDF.class);
		setLogicTreeBranch(branch);

		offerAvailableModule(new Callable<PolygonFaultGridAssociations>() {

			@Override
			public PolygonFaultGridAssociations call() throws Exception {
				if (faultModel == FaultModels.FM3_1 || faultModel == FaultModels.FM3_2)
					return FaultPolyMgr.loadSerializedUCERF3(faultModel);
				System.err.println("WARNING: rupture set doesn't have polygons attached, building with default buffer");
				return FaultPolyMgr.create(getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER);
			}
		}, PolygonFaultGridAssociations.class);
		// make sure that we don't end up with a default implementation of this
		if (hasAvailableModule(ModSectMinMags.class))
			removeModuleInstances(ModSectMinMags.class);
		addAvailableModule(new Callable<ModSectMinMags>() {

			@Override
			public ModSectMinMags call() throws Exception {
				return ModSectMinMags.instance(InversionFaultSystemRupSet.this, calcFinalMinMagForSections());
			}
		}, ModSectMinMags.class);
		offerAvailableModule(new Callable<U3InversionTargetMFDs>() {

			@Override
			public U3InversionTargetMFDs call() throws Exception {
				return new U3InversionTargetMFDs(InversionFaultSystemRupSet.this);
			}
		}, U3InversionTargetMFDs.class);
		// make sure that we don't end up with a default implementation of this
		if (hasAvailableModule(SectSlipRates.class))
			removeModuleInstances(SectSlipRates.class);
		addAvailableModule(new Callable<SectSlipRates>() {

			@Override
			public SectSlipRates call() throws Exception {
				return computeTargetSlipRates(InversionFaultSystemRupSet.this,
						inversionModel.isCharacteristic(), applyImpliedCouplingCoeff, getInversionTargetMFDs());
			}
		}, SectSlipRates.class);
	}

	// TODO [re]move (put in FaultSectionPrefData class?)
	public static Vector3D getSlipVector(FaultSectionPrefData section) {
		double[] strikeDipRake = { section.getFaultTrace().getAveStrike(), section.getAveDip(), section.getAveRake() };
		double[] vect = FaultUtils.getSlipVector(strikeDipRake);

		return new Vector3D(vect[0], vect[1], vect[2]);
	}


	/**
	 * Plot magnitude histogram for the inversion ruptures (how many rups at each mag)
	 * TODO move to parent or analysis class?
	 */
	public void plotMagHistogram() {
		//IncrementalMagFreqDist magHist = new IncrementalMagFreqDist(5.05,35,0.1);  // This doesn't go high enough if creeping section is left in for All-California
		IncrementalMagFreqDist magHist = new IncrementalMagFreqDist(5.05,40,0.1);
		magHist.setTolerance(0.2);	// this makes it a histogram
		for(int r=0; r<getNumRuptures();r++)
			magHist.add(getMagForRup(r), 1.0);
		ArrayList funcs = new ArrayList();
		funcs.add(magHist);
		magHist.setName("Histogram of Inversion ruptures");
		magHist.setInfo("(number in each mag bin)");
		GraphWindow graph = new GraphWindow(funcs, "Magnitude Histogram"); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Num");
	}

	/**
	 * This computes mag and various other attributes of the ruptures
	 */
	private void calcRuptureAttributes(List<? extends FaultSection> faultSectionData, SectionClusterList sectionClusterList) {

		String infoString = "FaultSystemRupSet Parameter Settings:\n\n";
		infoString += "\tfaultModel = " +faultModel+ "\n";
		infoString += "\tdefModName = " +defModName+ "\n";
		infoString += "\tdefMod filter basis = " +faultModel.getFilterBasis()+ "\n";
		infoString += "\t" +sectionClusterList.getPlausibilityConfiguration()+ "\n";
		infoString += "\tscalingRelationship = " +scalingRelationship+ "\n";
		infoString += "\tinversionModel = " +inversionModel+ "\n";
		infoString += "\tslipModelType = " +slipModelType+ "\n";

		if(D) System.out.println(infoString);

		int numSections = faultSectionData.size();
		double[] sectAreasReduced = new double[numSections];
		double[] sectAreasOrig = new double[numSections];
		for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
			FaultSection sectData = faultSectionData.get(sectIndex);
			// modified these 6/11/21 to use the standard method,
			// which can differ slightly due to floating point order of operations
			// aseismicity reduces area; km --> m on length & DDW
//			sectAreasReduced[sectIndex] = sectData.getTraceLength()*1e3*sectData.getReducedDownDipWidth()*1e3;
			sectAreasReduced[sectIndex] = sectData.getArea(true);
			// km --> m on length & DDW
//			sectAreasOrig[sectIndex] = sectData.getTraceLength()*1e3*sectData.getOrigDownDipWidth()*1e3;
			sectAreasOrig[sectIndex] = sectData.getArea(false);
		}

		int numRuptures = 0;
		for(int c=0; c<sectionClusterList.size();c++)
			numRuptures += sectionClusterList.get(c).getNumRuptures();
		List<List<Integer>> sectionsForRups = Lists.newArrayList();
		double[] rupMeanMag = new double[numRuptures];
		double[] rupMeanMoment = new double[numRuptures];
		double[] rupArea = new double[numRuptures];
		double[] rupLength = new double[numRuptures];
//		double[] rupOrigDDW = new double[numRuptures];	// down-dip width before aseismicity reduction
		double[] rupRake = new double[numRuptures];

		// cluster stuff
		clusterRups = Lists.newArrayList();
		clusterSects = Lists.newArrayList();
		//		int[] clusterIndexForRup = new int[numRuptures];
		//		int[] rupIndexInClusterForRup = new int[numRuptures];

		int rupIndex=-1;
		for(int c=0;c<sectionClusterList.size();c++) {
			SectionCluster cluster = sectionClusterList.get(c);
			List<List<Integer>> clusterRupSects = cluster.getSectionIndicesForRuptures();
			List<Integer> clusterRupIndexes = new ArrayList<Integer>(clusterRups.size());
			this.clusterRups.add(clusterRupIndexes);
			this.clusterSects.add(cluster.getAllSectionsIdList());
			for(int r=0;r<clusterRupSects.size();r++) {
				rupIndex+=1;
				//				clusterIndexForRup[rupIndex] = c;
				//				rupIndexInClusterForRup[rupIndex] = r;
				clusterRupIndexes.add(rupIndex);
				double totArea=0;
				double totOrigArea=0;
				double totLength=0;
				List<Integer> sectsInRup = clusterRupSects.get(r);
				sectionsForRups.add(sectsInRup);
				List<Double> areas = new ArrayList<Double>();
				List<Double> rakes = new ArrayList<Double>();
				for(Integer sectID:sectsInRup) {
					double length = faultSectionData.get(sectID).getTraceLength()*1e3;	// km --> m
					totLength += length;
					double area = sectAreasReduced[sectID];	// sq-m
					totArea += area;
					totOrigArea += sectAreasOrig[sectID];	// sq-m
					areas.add(area);
					rakes.add(faultSectionData.get(sectID).getAveRake());
				}
				rupArea[rupIndex] = totArea;
				rupLength[rupIndex] = totLength;
				rupRake[rupIndex] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(areas, rakes));
				double origDDW = totOrigArea/totLength;
				double mag = scalingRelationship.getMag(totArea, origDDW, rupRake[rupIndex]);
				rupMeanMag[rupIndex] = mag;
				rupMeanMoment[rupIndex] = MagUtils.magToMoment(rupMeanMag[rupIndex]);
				// the above is meanMoment in case we add aleatory uncertainty later (aveMoment needed elsewhere); 
				// the above will have to be corrected accordingly as in SoSAF_SubSectionInversion
				// (mean moment != moment of mean mag if aleatory uncertainty included)
				// rupMeanMoment[rupIndex] = MomentMagCalc.getMoment(rupMeanMag[rupIndex])* gaussMFD_slipCorr; // increased if magSigma >0
				//				rupMeanSlip[rupIndex] = rupMeanMoment[rupIndex]/(rupArea[rupIndex]*FaultMomentCalc.SHEAR_MODULUS);
//				if (rupIndex == 0)
//					System.out.println("Orig:\tarea="+totArea+"\torigArea="+totOrigArea
//							+"\tlength="+totLength+"\torigDDW="+origDDW+"\tslip="+rupMeanSlip[rupIndex]);
			}
		}
		
		// set with what we have now before inversionMFDs instantiation (we'll set again with slips later)
		init(faultSectionData, null, null, sectAreasReduced, sectionsForRups, rupMeanMag, rupRake, rupArea, rupLength, infoString);
		
		if (D) System.out.println("DONE creating "+getNumRuptures()+" ruptures!");
	}
	
	/**
	 * Computes UCERF3 target slip rates for the given fault section data, inversion target MFDs, inversion model,
	 * and moment rate fixes branch.
	 * <p>
	 * A 2D array is returned where the first value is the array of target slip rates, and the second is the array of
	 * target slip rate standard deviations. Extract as:
	 * <p>
	 * <code>
	 * double[][] slipTargets = computeTargetSlipRates(...);
	 * double[] targetSlipRate = slipTargets[0];
	 * double[] targetSlipRateStdDev = slipTargets[1];
	 * </code>
	 * 
	 * @param rupSet
	 * @param inversionModel
	 * @param momentRateFixes
	 * @param inversionMFDs
	 * @return target slip rates
	 */
	public static SectSlipRates computeTargetSlipRates(FaultSystemRupSet rupSet,
			InversionModels inversionModel, MomentRateFixes momentRateFixes,
			org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs inversionMFDs) {
		return computeTargetSlipRates(rupSet, inversionModel.isCharacteristic(), momentRateFixes.isApplyCC(), inversionMFDs);
	}
	
	/**
	 * Computes UCERF3 target slip rates for the given fault section data, inversion target MFDs and inversion settings.
	 * <p>
	 * A 2D array is returned where the first value is the array of target slip rates, and the second is the array of
	 * target slip rate standard deviations. Extract as:
	 * <p>
	 * <code>
	 * double[][] slipTargets = computeTargetSlipRates(...);
	 * double[] targetSlipRate = slipTargets[0];
	 * double[] targetSlipRateStdDev = slipTargets[1];
	 * </code>
	 * 
	 * @param rupSet
	 * @param characteristic characteristic branch if true, G-R branch if false
	 * @param applyImpliedCouplingCoeff if true, apply the implied coupling-coefficient
	 * @param inversionMFDs
	 * @return target slip rates
	 */
	public static SectSlipRates computeTargetSlipRates(FaultSystemRupSet rupSet,
			boolean characteristic, boolean applyImpliedCouplingCoeff,
			org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs inversionMFDs) {
		int numSections = rupSet.getNumSections();
		// compute target slip rate and stdDev (reduced for subseismo ruptures)
		double[] targetSlipRate = new double[numSections];
		double[] targetSlipRateStdDev = new double[numSections];
		Preconditions.checkState(targetSlipRate.length == targetSlipRateStdDev.length);
		SubSeismoOnFaultMFDs subSeismoOnFaultMFDs = inversionMFDs.getOnFaultSubSeisMFDs();

		// first get the implied coupling coeff reduction factor
		double impliedCC_reduction = 1.0;
		if (applyImpliedCouplingCoeff) {
			Preconditions.checkState(inversionMFDs instanceof U3InversionTargetMFDs,
					"Can only use applyImpliedCouplingCoeff option on U3InversionTargetMFDs instance. "
					+ "This option was only used in early UCERF3 tests, and was not carried forward");
			double impliedOnFaultCouplingCoeff = ((U3InversionTargetMFDs)inversionMFDs).getImpliedOnFaultCouplingCoeff();
			if(impliedOnFaultCouplingCoeff < 1)
				impliedCC_reduction = impliedOnFaultCouplingCoeff;
		}
		
		double totalOrigOnFaultMoRate = DeformationModelsCalc.calculateTotalMomentRate(rupSet.getFaultSectionDataList(), true);
		double totalFinalOnFaultMoRate = totalOrigOnFaultMoRate*impliedCC_reduction;
		double aveCharSubSeismoMoRateFraction = inversionMFDs.getTotalOnFaultSubSeisMFD().getTotalMomentRate()/totalFinalOnFaultMoRate;	// denomintor reduces by any implied CC

		// now compute reduced slip rates and their std
		for(int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			// get original (creep reduced) section moment rate
			double origSectMoRate = sect.calcMomentRate(true);	// this is creep reduced
			if (Double.isNaN(origSectMoRate))
				origSectMoRate = 0;
			
			double impliedCC_reducedSectMoRate = origSectMoRate*impliedCC_reduction;
			
			double fractionalSlipRateReduction=1.0;	// default if next is false
			if(origSectMoRate > 0) { // avoid division by zero
				if (characteristic) {
					fractionalSlipRateReduction = impliedCC_reducedSectMoRate*(1.0-aveCharSubSeismoMoRateFraction)/origSectMoRate;	// reduced by subseismo and any implied CC
				} else {
					double subSeismoMoRate = subSeismoOnFaultMFDs.get(s).getTotalMomentRate(); 	// implied CC already applied if applicable
					fractionalSlipRateReduction = (impliedCC_reducedSectMoRate-subSeismoMoRate)/origSectMoRate;	// reduced by subseismo and any implied CC
				}				
			}
			targetSlipRate[s] = sect.getReducedAveSlipRate()*1e-3*fractionalSlipRateReduction; // mm/yr --> m/yr; includes moRateReduction
			targetSlipRateStdDev[s] = sect.getReducedSlipRateStdDev()*1e-3*fractionalSlipRateReduction; // mm/yr --> m/yr; includes moRateReduction
		}
		return SectSlipRates.precomputed(rupSet, targetSlipRate, targetSlipRateStdDev);
	}

	/**
	 * This writes the rupture sections to an ASCII file
	 * 
	 * TODO [re]move
	 * @param filePathAndName
	 */
	public void writeRupsToFiles(String filePathAndName) {
		FileWriter fw;
		try {
			fw = new FileWriter(filePathAndName);
			fw.write("rupID\tclusterID\trupInClustID\tmag\tnumSectIDs\tsect1_ID\tsect2_ID\t...\n");	// header
			int rupIndex = 0;

			for(int c=0;c<getNumClusters();c++) {
				List<Integer> clusterRupIndexes = clusterRups.get(c);
				for(int r=0; r<clusterRupIndexes.size();r++) {
					List<Integer> rup = getSectionsIndicesForRup(clusterRupIndexes.get(r));
					String line = Integer.toString(rupIndex)+"\t"+Integer.toString(c)+"\t"+Integer.toString(r)+"\t"+
							(float)getMagForRup(rupIndex)+"\t"+rup.size();
					for(Integer sectID: rup) {
						line += "\t"+sectID;
					}
					line += "\n";
					fw.write(line);
					rupIndex+=1;
				}				  
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * This writes the section data to an ASCII file
	 * 
	 * TODO: [re]move?
	 * @throws IOException 
	 */
	public void writeSectionsToFile(String filePathAndName) throws IOException {
		ArrayList<String> metaData = new ArrayList<String>();
		metaData.add("defModName = "+defModName);
		FaultSectionDataWriter.writeSectionsToFile(getFaultSectionDataList(), metaData, filePathAndName);
	}

	// Ave Slip and Slip On Sections Methods

	@Override
	public void copyCacheFrom(FaultSystemRupSet rupSet) {
		super.copyCacheFrom(rupSet);
		if (rupSet instanceof InversionFaultSystemRupSet) {
			FaultModels myFM = getFaultModel();
			DeformationModels myDM = getDeformationModel();
			U3LogicTreeBranch branch = getLogicTreeBranch();
			ScalingRelationships myScale = branch.getValue(ScalingRelationships.class);
			SlipAlongRuptureModels mySlipAlong = branch.getValue(SlipAlongRuptureModels.class);
			
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			U3LogicTreeBranch oBranch = invRupSet.getLogicTreeBranch();
			FaultModels oFM = invRupSet.getFaultModel();
			DeformationModels oDM = invRupSet.getDeformationModel();
			ScalingRelationships oScale = oBranch.getValue(ScalingRelationships.class);
			SlipAlongRuptureModels oSlipAlong = oBranch.getValue(SlipAlongRuptureModels.class);
			if (myFM == oFM && myDM == oDM && myScale == oScale) {
				surfCache = invRupSet.surfCache;
//				System.out.println("Copying surface cache!");
				if (mySlipAlong == oSlipAlong)
					rupSectionSlipsCache = invRupSet.rupSectionSlipsCache;
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see scratch.UCERF3.inversion.SlipEnabledRupSet#getAveSlipForRup(int)
	 */
	@Override
	public double getAveSlipForRup(int rupIndex) {
		return getAveSlipForAllRups()[rupIndex];
	}


	/* (non-Javadoc)
	 * @see scratch.UCERF3.inversion.SlipEnabledRupSet#getAveSlipForAllRups()
	 */
	@Override
	public synchronized double[] getAveSlipForAllRups() {
		if (rupMeanSlip == null) {
			double[] slips = new double[getNumRuptures()];
			AveSlipModule aveSlips = getModule(AveSlipModule.class);
			for (int r=0; r<slips.length; r++) {
				slips[r] = aveSlips.getAveSlip(r);
			}
			rupMeanSlip = slips;
		}
		return rupMeanSlip;
	}

	/**
	 * 
	 * @return the number of clusters
	 */
	public int getNumClusters() {
		if (clusterRups == null)
			return 0;
		return clusterRups.size();
	}

	/**
	 * 
	 * @param index index of the cluster to get
	 * @return number of ruptures in the given cluster
	 */
	public int getNumRupturesForCluster(int index) {
		return clusterRups.get(index).size();
	}

	/**
	 * 
	 * @param index index of the cluster to get
	 * @return list of rupture indexes for the cluster at the given index
	 * @throws IndexOutOfBoundsException if the index is invalid
	 */
	public List<Integer> getRupturesForCluster(int index)
			throws IndexOutOfBoundsException {
		return clusterRups.get(index);
	}
	
	/**
	 * 
	 * @return list of rupture indexes for each cluster
	 */
	public List<List<Integer>> getRupturesForClusters() {
		return clusterRups;
	}

	/**
	 * 
	 * @param index index of the cluster to get
	 * @return list of section IDs in the cluster at the given index
	 */
	public List<Integer> getSectionsForCluster(int index) {
		return clusterSects.get(index);
	}
	
	/**
	 * 
	 * @return list of section indexes for each cluster
	 */
	public List<List<Integer>> getSectionsForClusters() {
		return clusterSects;
	}

	/**
	 * This fetches a list of all of the close sections to this section, as defined by the rupture set.
	 * @param sectIndex index of the section to retrieve
	 * @return close sections
	 */
	public List<Integer> getCloseSectionsList(int sectIndex) {
		return sectionConnectionsListList.get(sectIndex);
	}

	/**
	 * This fetches a list of all of the close sections to each section, as defined by the rupture set.
	 * @return close sections
	 */
	public List<List<Integer>> getCloseSectionsListList() {
		return sectionConnectionsListList;
	}
	
	/**
	 * Returns the laugh test filter, or null if not available
	 * 
	 * @return
	 */
	public OldPlausibilityConfiguration getOldPlausibilityConfiguration() {
		return filter;
	}

	public U3LogicTreeBranch getLogicTreeBranch() { return logicTreeBranch; }
	
	public void setLogicTreeBranch(U3LogicTreeBranch logicTreeBranch) {
		addModule(logicTreeBranch);
		this.logicTreeBranch = logicTreeBranch;
	}

	// convenience methods for FM and DM, Dsr

	public DeformationModels getDeformationModel() {
		return defModName;
	}

	public FaultModels getFaultModel() {
		return faultModel;
	}

	public SlipAlongRuptureModels getSlipAlongRuptureModel() {
		return slipModelType;
	}

	public U3InversionTargetMFDs getInversionTargetMFDs() {
		return getModule(U3InversionTargetMFDs.class);
	}

	/**
	 * Returns distances between each sub section, either cached or calculated on demand.
	 * @return
	 */
	public synchronized Map<IDPairing, Double> getSubSectionDistances() {
		if (subSectionDistances == null) {
			subSectionDistances = DeformationModelFetcher.calculateDistances(filter.getMaxJumpDist(), getFaultSectionDataList());
			// now add the reverse distances
			// wrap keySet in list to avoid concurrent modification exception
			for (IDPairing pair : Lists.newArrayList(subSectionDistances.keySet())) {
				subSectionDistances.put(pair.getReversed(), subSectionDistances.get(pair));
			}
		}
		return subSectionDistances;
	}

	// TODO: move?
	public String getPreInversionAnalysisData(boolean includeHeader) {


		String str = "";

		if(includeHeader)
			str += logicTreeBranch.getTabSepValStringHeader()+"\t"+getInversionTargetMFDs().getPreInversionAnalysisDataHeader()+
			"\t"+"targetOnFaultMoRate\tMMaxOffFaultIfDefModMoRateSatisfied\n";

		str += logicTreeBranch.getTabSepValString()+"\t"+getInversionTargetMFDs().getPreInversionAnalysisData()+
				"\t"+(float)getTotalReducedMomentRate()+"\t"+(float)getInversionTargetMFDs().getOffFaultMmaxIfOrigMoRateSatisfied();
		return str;
	}
	
	/**
	 * This computes the final minimum mag for eachfault section.
	 * See doc for FaultSystemRupSetCalc.computeMinSeismoMagForSections() for details.
	 * @param sectIndex
	 * @return
	 */
	protected double[] calcFinalMinMagForSections() {
		return FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,MIN_MAG_FOR_SEISMOGENIC_RUPS);
	}

	/**
	 * This returns the final minimum mag for a given fault section.
	 * See doc for computeMinSeismoMagForSections() for details.
	 * @param sectIndex
	 * @return
	 */
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		return getModule(ModSectMinMags.class).getMinMagForSection(sectIndex);
	}


	/**
	 * This tells whether the given rup is below any of the final minimum magnitudes 
	 * of the sections utilized by the rup.  Actually, the test is really whether the
	 * mag falls below the lower bin edge implied by the section min mags; see doc for
	 * computeWhichRupsFallBelowSectionMinMags()).
	 * @param rupIndex
	 * @return
	 */
	public synchronized boolean isRuptureBelowSectMinMag(int rupIndex) {

		// see if it needs to be computed
		if(isRupBelowMinMagsForSects == null) {
			ModSectMinMags minMagsModule = getModule(ModSectMinMags.class);
			isRupBelowMinMagsForSects = FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(this, minMagsModule);
		}

		return isRupBelowMinMagsForSects[rupIndex];

	}
	
	public boolean[] getRuptureBelowSectMinMagArray() {
		// make sure it's initialized;
		isRuptureBelowSectMinMag(0);
		return isRupBelowMinMagsForSects;
	}


	/**
	 * This returns the upper magnitude of sub-seismogenic ruptures
	 * (at the bin center).  This is the lower bin edge of the minimum
	 * seismogenic rupture minus half the MFD discretization.
	 * @param sectIndex
	 * @return
	 */
	public double getUpperMagForSubseismoRuptures(int sectIndex) {
		return getUpperMagForSubseismoRuptures(getFinalMinMagForSection(sectIndex));
	}
	
	public static double getUpperMagForSubseismoRuptures(double finalMinMag) {
		return U3SectionMFD_constraint.getLowerEdgeOfFirstBin(finalMinMag) - U3InversionTargetMFDs.DELTA_MAG/2;
	}



}
