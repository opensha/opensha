/**
 * 
 */
package scratch.UCERF3.inversion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelDepthDep;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Shaw_2009_ModifiedMagAreaRel;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.SlipEnabledRupSet;
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
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.DeformationModelOffFaultMoRateData;
import scratch.UCERF3.utils.FaultSectionDataWriter;
import scratch.UCERF3.utils.IDPairing;
import scratch.UCERF3.utils.SectionMFD_constraint;

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
public class InversionFaultSystemRupSet extends SlipEnabledRupSet {

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

	private LogicTreeBranch logicTreeBranch;

	private LaughTestFilter filter;

	// rupture attributes (all in SI units)
	final static double MIN_MO_RATE_REDUCTION = 0.1;
	private double[] rupMeanSlip;

	// cluster information
	private List<List<Integer>> clusterRups;
	private List<List<Integer>> clusterSects;

	// this holds the various MFDs implied by the inversion fault system rupture set 
	// (e.g., we need to know the sub-seismo on-fault moment rates to reduce slip rates accordingly)
	private InversionTargetMFDs inversionMFDs;

	private List<List<Integer>> sectionConnectionsListList;

	private Map<IDPairing, Double> subSectionDistances;

	public final static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 6.0;
	private double[] minMagForSectArray;
	private boolean[] isRupBelowMinMagsForSects;

	/**
	 * This generates a new InversionFaultSystemRupSet for the given fault/deformation mode and all other branch
	 * parameters.
	 * 
	 * @param branch
	 * @param precomputedDataDir
	 * @param filter
	 */
	public InversionFaultSystemRupSet(LogicTreeBranch branch, File precomputedDataDir, LaughTestFilter filter) {
		this(branch, new SectionClusterList(branch.getValue(FaultModels.class),
				branch.getValue(DeformationModels.class), precomputedDataDir, filter), null);
	}

	/**
	 * This creates a new InversionFaultSystemRupSet for the given cluster list, which may or may have been
	 * generated with this deformation model (but needs to be generated with this fault model!).
	 * 
	 * @param branch
	 * @param sectionClusterList
	 * @param faultSectionData
	 */
	public InversionFaultSystemRupSet(LogicTreeBranch branch, SectionClusterList sectionClusterList,
			List<FaultSectionPrefData> faultSectionData) {

		Preconditions.checkNotNull(branch, "LogicTreeBranch cannot be null!");
		Preconditions.checkArgument(branch.isFullySpecified(), "LogicTreeBranch must be fully specified.");

		if (faultSectionData == null)
			// default to using the fault section data from the clusters
			faultSectionData = sectionClusterList.getFaultSectionData();

		this.logicTreeBranch = branch;
		setParamsFromBranch(branch);
		this.filter = sectionClusterList.getFilter();
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
			LogicTreeBranch branch,
			LaughTestFilter filter,
			double[] rupAveSlips,
			List<List<Integer>> sectionConnectionsListList,
			List<List<Integer>> clusterRups,
			List<List<Integer>> clusterSects) {
		setParamsFromBranch(branch);
		init(rupSet);
		
		int numSects = rupSet.getNumSections();
		int numRups = rupSet.getNumRuptures();
		
		Preconditions.checkArgument(rupAveSlips == null
				|| rupAveSlips.length == getNumRuptures(), "rupAveSlips sizes inconsistent!");
		this.rupMeanSlip = rupAveSlips;
		
		// can partially empty but we at least need FM/DM/Scale
		Preconditions.checkNotNull(branch, "LogicTreeBranch cannot be null");
		if (!branch.isFullySpecified())
			System.err.println("WARNING: LogicTreeBranch not fully specified");
		this.logicTreeBranch = branch;
		
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
	
	private void setParamsFromBranch(LogicTreeBranch branch) {
		if (branch.hasNonNullValue(FaultModels.class))
			this.faultModel = branch.getValue(FaultModels.class);
		if (branch.hasNonNullValue(DeformationModels.class))
			this.defModName = branch.getValue(DeformationModels.class);
		if (branch.hasNonNullValue(ScalingRelationships.class))
			this.scalingRelationship = branch.getValue(ScalingRelationships.class);
		if (branch.hasNonNullValue(SlipAlongRuptureModels.class))
			this.slipModelType = branch.getValue(SlipAlongRuptureModels.class);
		if (branch.hasNonNullValue(InversionModels.class))
			this.inversionModel = branch.getValue(InversionModels.class);
		if (branch.hasNonNullValue(TotalMag5Rate.class))
			this.totalRegionRateMgt5 = branch.getValue(TotalMag5Rate.class).getRateMag5();
		if (branch.hasNonNullValue(MaxMagOffFault.class))
			this.mMaxOffFault = branch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		if (branch.hasNonNullValue(MomentRateFixes.class))
			this.applyImpliedCouplingCoeff = branch.getValue(MomentRateFixes.class).isApplyCC();
		if (branch.hasNonNullValue(SpatialSeisPDF.class))
			this.spatialSeisPDF = branch.getValue(SpatialSeisPDF.class);
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
	private void calcRuptureAttributes(List<FaultSectionPrefData> faultSectionData, SectionClusterList sectionClusterList) {

		String infoString = "FaultSystemRupSet Parameter Settings:\n\n";
		infoString += "\tfaultModel = " +faultModel+ "\n";
		infoString += "\tdefModName = " +defModName+ "\n";
		infoString += "\tdefMod filter basis = " +sectionClusterList.getDefModel()+ "\n";
		infoString += "\t" +sectionClusterList.getFilter()+ "\n";
		infoString += "\tscalingRelationship = " +scalingRelationship+ "\n";
		infoString += "\tinversionModel = " +inversionModel+ "\n";
		infoString += "\tslipModelType = " +slipModelType+ "\n";

		if(D) System.out.println(infoString);

		int numSections = faultSectionData.size();
		double[] sectAreasReduced = new double[numSections];
		double[] sectAreasOrig = new double[numSections];
		for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
			FaultSectionPrefData sectData = faultSectionData.get(sectIndex);
			// aseismicity reduces area; km --> m on length & DDW
			sectAreasReduced[sectIndex] = sectData.getTraceLength()*1e3*sectData.getReducedDownDipWidth()*1e3;
			// km --> m on length & DDW
			sectAreasOrig[sectIndex] = sectData.getTraceLength()*1e3*sectData.getOrigDownDipWidth()*1e3;
		}

		int numRuptures = 0;
		for(int c=0; c<sectionClusterList.size();c++)
			numRuptures += sectionClusterList.get(c).getNumRuptures();
		List<List<Integer>> sectionsForRups = Lists.newArrayList();
		double[] rupMeanMag = new double[numRuptures];
		double[] rupMeanMoment = new double[numRuptures];
		rupMeanSlip = new double[numRuptures];
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
			ArrayList<ArrayList<Integer>> clusterRupSects = cluster.getSectionIndicesForRuptures();
			ArrayList<Integer> clusterRupIndexes = new ArrayList<Integer>(clusterRups.size());
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
				ArrayList<Integer> sectsInRup = clusterRupSects.get(r);
				sectionsForRups.add(sectsInRup);
				ArrayList<Double> areas = new ArrayList<Double>();
				ArrayList<Double> rakes = new ArrayList<Double>();
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
				double mag = scalingRelationship.getMag(totArea, origDDW);
				rupMeanMag[rupIndex] = mag;
				rupMeanMoment[rupIndex] = MagUtils.magToMoment(rupMeanMag[rupIndex]);
				// the above is meanMoment in case we add aleatory uncertainty later (aveMoment needed elsewhere); 
				// the above will have to be corrected accordingly as in SoSAF_SubSectionInversion
				// (mean moment != moment of mean mag if aleatory uncertainty included)
				// rupMeanMoment[rupIndex] = MomentMagCalc.getMoment(rupMeanMag[rupIndex])* gaussMFD_slipCorr; // increased if magSigma >0
				//				rupMeanSlip[rupIndex] = rupMeanMoment[rupIndex]/(rupArea[rupIndex]*FaultMomentCalc.SHEAR_MODULUS);
				rupMeanSlip[rupIndex] = scalingRelationship.getAveSlip(totArea, totLength, origDDW);
			}
		}
		
		// set with what he have now before inversionMFDs instantiation (we'll set again with slips later)
		init(faultSectionData, null, null, sectAreasReduced, sectionsForRups, rupMeanMag, rupRake, rupArea, rupLength, infoString);
		
		inversionMFDs = new InversionTargetMFDs(this);

		ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = inversionMFDs.getSubSeismoOnFaultMFD_List();
		double impliedOnFaultCouplingCoeff = inversionMFDs.getImpliedOnFaultCouplingCoeff();

		// compute target slip rate and stdDev (reduced for subseismo ruptures)
		double[] targetSlipRate = new double[numSections];
		double[] targetSlipRateStdDev = new double[numSections];

		// first get the implied coupling coeff reduction factor
		double impliedCC_reduction = 1.0;
		if(applyImpliedCouplingCoeff && impliedOnFaultCouplingCoeff<1)
			impliedCC_reduction = impliedOnFaultCouplingCoeff;
	
		
		double totalOrigOnFaultMoRate = inversionMFDs.getOrigOnFltDefModMoRate();
		double totalFinalOnFaultMoRate = totalOrigOnFaultMoRate*impliedCC_reduction;
		double aveCharSubSeismoMoRateFraction = inversionMFDs.getTotalSubSeismoOnFaultMFD().getTotalMomentRate()/totalFinalOnFaultMoRate;	// denomintor reduces by any implied CC

		// now compute reduced slip rates and their std
		for(int s=0; s<numSections; s++) {
			
			// get original (creep reduced) section moment rate
			double origSectMoRate = faultSectionData.get(s).calcMomentRate(true);	// this is creep reduced
			if (Double.isNaN(origSectMoRate))
				origSectMoRate = 0;
			
			double impliedCC_reducedSectMoRate = origSectMoRate*impliedCC_reduction;

			double fractionalSlipRateReduction=1.0;	// default if next is false
			if(origSectMoRate > 0) { // avoid division by zero
				if(inversionModel.isCharacteristic()) {
					fractionalSlipRateReduction = impliedCC_reducedSectMoRate*(1.0-aveCharSubSeismoMoRateFraction)/origSectMoRate;	// reduced by subseismo and any implied CC
				}
				else {
					double subSeismoMoRate = subSeismoOnFaultMFD_List.get(s).getTotalMomentRate(); 	// implied CC already applied if applicable
					fractionalSlipRateReduction = (impliedCC_reducedSectMoRate-subSeismoMoRate)/origSectMoRate;	// reduced by subseismo and any implied CC
				}				
			}
			targetSlipRate[s] = faultSectionData.get(s).getReducedAveSlipRate()*1e-3*fractionalSlipRateReduction; // mm/yr --> m/yr; includes moRateReduction
			targetSlipRateStdDev[s] = faultSectionData.get(s).getReducedSlipRateStdDev()*1e-3*fractionalSlipRateReduction; // mm/yr --> m/yr; includes moRateReduction
		}
		
		if (D) System.out.println("DONE creating "+getNumRuptures()+" ruptures!");

		init(faultSectionData, targetSlipRate, targetSlipRateStdDev, sectAreasReduced, sectionsForRups, rupMeanMag, rupRake, rupArea, rupLength, infoString);
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
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			if (invRupSet.getSlipAlongRuptureModel() == getSlipAlongRuptureModel()
					&& invRupSet.getDeformationModel() == getDeformationModel()
					&& invRupSet.getLogicTreeBranch().getValue(ScalingRelationships.class)
						== getLogicTreeBranch().getValue(ScalingRelationships.class))
				rupSectionSlipsCache = invRupSet.rupSectionSlipsCache;
		}
	}
	
	/* (non-Javadoc)
	 * @see scratch.UCERF3.inversion.SlipEnabledRupSet#getAveSlipForRup(int)
	 */
	@Override
	public double getAveSlipForRup(int rupIndex) {
		return rupMeanSlip[rupIndex];
	}

	/* (non-Javadoc)
	 * @see scratch.UCERF3.inversion.SlipEnabledRupSet#getAveSlipForAllRups()
	 */
	@Override
	public double[] getAveSlipForAllRups() {
		return rupMeanSlip;
	}

	private static EvenlyDiscretizedFunc taperedSlipPDF, taperedSlipCDF;

	/**
	 * This gets the slip on each section based on the value of slipModelType.
	 * The slips are in meters.  Note that taper slipped model wts slips by area
	 * to maintain moment balance (so it doesn't plot perfectly); do something about this?
	 * 
	 * Note that for two parallel faults that have some overlap, the slip won't be reduced
	 * along the overlap the way things are implemented here.
	 * 
	 * This has been spot checked, but needs a formal test.
	 *
	 */
	@Override
	protected double[] calcSlipOnSectionsForRup(int rthRup) {

		SlipAlongRuptureModels slipModelType = getSlipAlongRuptureModel();
		Preconditions.checkNotNull(slipModelType);

		List<Integer> sectionIndices = getSectionsIndicesForRup(rthRup);
		int numSects = sectionIndices.size();

		// compute rupture area
		double[] sectArea = new double[numSects];
		double[] sectMoRate = new double[numSects];
		int index=0;
		for(Integer sectID: sectionIndices) {	
			//				FaultSectionPrefData sectData = getFaultSectionData(sectID);
			//				sectArea[index] = sectData.getTraceLength()*sectData.getReducedDownDipWidth()*1e6;	// aseismicity reduces area; 1e6 for sq-km --> sq-m
			sectArea[index] = getAreaForSection(sectID);
			sectMoRate[index] = FaultMomentCalc.getMoment(sectArea[index], getSlipRateForSection(sectID));
			index += 1;
		}

		double aveSlip = getAveSlipForRup(rthRup);  // in meters

		if (slipModelType == SlipAlongRuptureModels.MEAN_UCERF3) {
			// get mean weights
			List<Double> meanWeights = Lists.newArrayList();
			List<SlipAlongRuptureModels> meanSALs = Lists.newArrayList();

			double sum = 0;
			for (SlipAlongRuptureModels sal : SlipAlongRuptureModels.values()) {
				double weight = sal.getRelativeWeight(null);
				if (weight > 0) {
					meanWeights.add(weight);
					meanSALs.add(sal);
					sum += weight;
				}
			}
			if (sum != 0)
				for (int i=0; i<meanWeights.size(); i++)
					meanWeights.set(i, meanWeights.get(i)/sum);

			// calculate mean
			double[] slipsForRup = new double[numSects];

			for (int i=0; i<meanSALs.size(); i++) {
				double weight = meanWeights.get(i);
				double[] subSlips = calcSlipOnSectionsForRup(rthRup, meanSALs.get(i), sectArea,
						sectMoRate, aveSlip);

				for (int j=0; j<numSects; j++)
					slipsForRup[j] += weight*subSlips[j];
			}

			return slipsForRup;
		}

		return calcSlipOnSectionsForRup(rthRup, slipModelType, sectArea,
				sectMoRate, aveSlip);
	}

	private double[] calcSlipOnSectionsForRup(int rthRup,
			SlipAlongRuptureModels slipModelType,
			double[] sectArea, double[] sectMoRate, double aveSlip) {
		double[] slipsForRup = new double[sectArea.length];

		// for case segment slip is independent of rupture (constant), and equal to slip-rate * MRI
		if(slipModelType == SlipAlongRuptureModels.CHAR) {
			throw new RuntimeException("SlipModelType.CHAR_SLIP_MODEL not yet supported");
		}
		// for case where ave slip computed from mag & area, and is same on all segments 
		else if (slipModelType == SlipAlongRuptureModels.UNIFORM) {
			for(int s=0; s<slipsForRup.length; s++)
				slipsForRup[s] = aveSlip;
		}
		// this is the model where section slip is proportional to section slip rate 
		// (bumped up or down based on ratio of seg slip rate over wt-ave slip rate (where wts are seg areas)
		else if (slipModelType == SlipAlongRuptureModels.WG02) {
			// TODO if we revive this, we need to change the cache copying due to moment changes
			double totMoRateForRup = calcTotalAvailableMomentRate(rthRup);
			for(int s=0; s<slipsForRup.length; s++) {
				slipsForRup[s] = aveSlip*sectMoRate[s]*getAreaForRup(rthRup)/(totMoRateForRup*sectArea[s]);
			}
		}
		else if (slipModelType == SlipAlongRuptureModels.TAPERED) {
			// note that the ave slip is partitioned by area, not length; this is so the final model is moment balanced.

			// make the taper function if hasn't been done yet
			if(taperedSlipCDF == null) {
				synchronized (FaultSystemRupSet.class) {
					if (taperedSlipCDF == null) {
						EvenlyDiscretizedFunc taperedSlipCDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						EvenlyDiscretizedFunc taperedSlipPDF = new EvenlyDiscretizedFunc(0, 5001, 0.0002);
						double x,y, sum=0;
						int num = taperedSlipPDF.size();
						for(int i=0; i<num;i++) {
							x = taperedSlipPDF.getX(i);
							y = Math.pow(Math.sin(x*Math.PI), 0.5);
							taperedSlipPDF.set(i,y);
							sum += y;
						}
						// now make final PDF & CDF
						y=0;
						for(int i=0; i<num;i++) {
							y += taperedSlipPDF.getY(i);
							taperedSlipCDF.set(i,y/sum);
							taperedSlipPDF.set(i,taperedSlipPDF.getY(i)/sum);
							//									System.out.println(taperedSlipCDF.getX(i)+"\t"+taperedSlipPDF.getY(i)+"\t"+taperedSlipCDF.getY(i));
						}
						InversionFaultSystemRupSet.taperedSlipCDF = taperedSlipCDF;
						InversionFaultSystemRupSet.taperedSlipPDF = taperedSlipPDF;
					}
				}
			}
			double normBegin=0, normEnd, scaleFactor;
			for(int s=0; s<slipsForRup.length; s++) {
				normEnd = normBegin + sectArea[s]/getAreaForRup(rthRup);
				// fix normEnd values that are just past 1.0
				//					if(normEnd > 1 && normEnd < 1.00001) normEnd = 1.0;
				if(normEnd > 1 && normEnd < 1.01) normEnd = 1.0;
				scaleFactor = taperedSlipCDF.getInterpolatedY(normEnd)-taperedSlipCDF.getInterpolatedY(normBegin);
				scaleFactor /= (normEnd-normBegin);
				Preconditions.checkState(normEnd>=normBegin, "End is before beginning!");
				Preconditions.checkState(aveSlip >= 0, "Negative ave slip: "+aveSlip);
				slipsForRup[s] = aveSlip*scaleFactor;
				normBegin = normEnd;
			}
		}

		return slipsForRup;
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
	public LaughTestFilter getLaughTestFilter() {
		return filter;
	}

	public LogicTreeBranch getLogicTreeBranch() { return logicTreeBranch; }
	
	public void setLogicTreeBranch(LogicTreeBranch logicTreeBranch) {
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

	public InversionTargetMFDs getInversionTargetMFDs() {
		if (inversionMFDs == null)
			inversionMFDs = new InversionTargetMFDs(this);
		return inversionMFDs;
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
	 * This returns the final minimum mag for a given fault section.
	 * See doc for computeMinSeismoMagForSections() for details.
	 * @param sectIndex
	 * @return
	 */
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		if(minMagForSectArray == null) {
			minMagForSectArray = FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,MIN_MAG_FOR_SEISMOGENIC_RUPS);
		}
		return minMagForSectArray[sectIndex];
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
			if(minMagForSectArray == null) {
				minMagForSectArray = FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,MIN_MAG_FOR_SEISMOGENIC_RUPS);
			}
			isRupBelowMinMagsForSects = FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(this, minMagForSectArray);
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
		return SectionMFD_constraint.getLowerEdgeOfFirstBin(getFinalMinMagForSection(sectIndex)) - InversionTargetMFDs.DELTA_MAG/2;
	}



}
