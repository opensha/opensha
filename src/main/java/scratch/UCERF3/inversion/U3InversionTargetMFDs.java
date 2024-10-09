package scratch.UCERF3.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.magdist.TaperedGR_MagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.DeformationModelsCalc;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.RELM_RegionUtils;


/**
 * This class constructs and stores the various pre-inversion MFD Targets.  
 * 
 * Details on what's returned are:
 * 
 * getTotalTargetGR() returns: 
 * 
 * 		The total regional target GR (Same for both GR and Char branches)
 * 
 * getTotalGriddedSeisMFD() returns:
 * 
 * 		getTrulyOffFaultMFD()+getTotalSubSeismoOnFaultMFD()
 * 
 * getTotalOnFaultMFD() returns:
 * 
 * 		getTotalSubSeismoOnFaultMFD() + getOnFaultSupraSeisMFD();
 * 
 * The rest are branch specific:
 * 
 * if(inversionModel.isCharacteristic())
 * 
 * 		getTrulyOffFaultMFD() returns:  
 * 
 * 			MFD implied by tri-linear total-on-fault model
 * 
 * 		getSubSeismoOnFaultMFD_List() returns:
 * 
 * 			GR up to max subseismo mag on each subsection, with rate from smoothed seismicity inside fault polygon
 * 
 * 		getTotalSubSeismoOnFaultMFD() returns:
 * 
 *  		The sum of getSubSeismoOnFaultMFD_List() 
 *  
 *  	getOnFaultSupraSeisMFD() returns:
 * 
 * 			getTotalTargetGR() - getTrulyOffFaultMFD() - getTotalSubSeismoOnFaultMFD()
 * 
 * 
 * if(inversionModel.isGR())
 * 
 * 		getTrulyOffFaultMFD() returns:  
 * 
 * 			GR from obs off-fault rate and off-fault Mmax (optionally using a tapered GR with same total moment rate)
 * 
 * 		getSubSeismoOnFaultMFD_List() returns:
 * 
 * 			GR implied by slip rate on each section (reduced if applyImpliedCouplingCoeff = true), with rates set to zero at supra-seis mags
 * 
 * 			Note that on the NoFix/GR branch the sum of these does not equal what's returned by getTotalSubSeismoOnFaultMFD() because
 *			the latter gets reduced by implied on-fault coupling coefficient to match total MFD target (so inversion will reduce final 
 *			slip rates to match the target).
 * 
 * 		getTotalSubSeismoOnFaultMFD() returns:
 * 
 *  		Sum of getSubSeismoOnFaultMFD_List(), but on NoFix/GR also reduced by implied on-fault 
 *  		coupling coefficient (so inversion will reduce final slip rates to match the target)
 *  
 *  	getOnFaultSupraSeisMFD() returns:
 * 
 * 			Sum of section GR distributions implied by slip rates (reduced if applyImpliedCouplingCoeff = true), 
 * 			and with rates set to zero at subseismo mags; on NoFix/GR this is also reduced by implied on-fault 
 *  		coupling coefficient (so inversion will reduce final slip rates to match the target)
 * 
 *
 * @author field
 *
 */
public class U3InversionTargetMFDs extends org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs implements ArchivableModule, SubModule<FaultSystemRupSet> {
	
	// debugging flag
	protected final static boolean D = false;
	final static boolean GR_OFF_FAULT_IS_TAPERED = true;
	protected String debugString;
	
	protected double totalRegionRateMgt5;
	protected double onFaultRegionRateMgt5;
	protected double offFaultRegionRateMgt5;
	protected double mMaxOffFault;
	protected boolean applyImpliedCouplingCoeff;
	protected SpatialSeisPDF spatialSeisPDF;
	protected SpatialSeisPDF spatialSeisPDFforOnFaultRates;
	protected InversionModels inversionModel;
	protected GriddedSeisUtils gridSeisUtils;
	
	protected double origOnFltDefModMoRate, offFltDefModMoRate, aveMinSeismoMag, roundedMmaxOnFault;
	double fractSeisInSoCal;
	protected double fractionSeisOnFault;
	protected double impliedOnFaultCouplingCoeff;
	protected double impliedTotalCouplingCoeff;
	protected double finalOffFaultCouplingCoeff;
	protected GutenbergRichterMagFreqDist totalTargetGR;
	protected SummedMagFreqDist targetOnFaultSupraSeisMFD;
	protected IncrementalMagFreqDist trulyOffFaultMFD;
	protected SubSeismoOnFaultMFDs subSeismoOnFaultMFDs;
	protected SummedMagFreqDist totalSubSeismoOnFaultMFD;		// this is a sum of the MFDs in subSeismoOnFaultMFD_List

	GutenbergRichterMagFreqDist totalTargetGR_NoCal, totalTargetGR_SoCal;
	IncrementalMagFreqDist noCalTargetSupraMFD, soCalTargetSupraMFD;

	
	List<IncrementalMagFreqDist> mfdConstraintsForNoAndSoCal;

	// discretization parameters for MFDs
	public final static double MIN_MAG = 0.05;
	public final static double MAX_MAG = 8.95;
	public final static int NUM_MAG = 90;
	public final static double DELTA_MAG = 0.1;
	
	public final static double FAULT_BUFFER = 12d;	// buffer for fault polygons
	private LogicTreeBranch<?> logicTreeBranch;
	private ModSectMinMags finalMinMags;
	private PolygonFaultGridAssociations polygons;

	/**
	 * Implicit constructor required for subclassing
	 */
	protected U3InversionTargetMFDs() {
		// do nothing, this is here so subclasses can do their own setup
		super(null);
	}
	
	/**
	 * 
	 * @param invRupSet
	 */
	public U3InversionTargetMFDs(InversionFaultSystemRupSet invRupSet) {
		this(invRupSet, invRupSet.requireModule(LogicTreeBranch.class), invRupSet.requireModule(ModSectMinMags.class),
				invRupSet.requireModule(PolygonFaultGridAssociations.class));
	}
	
	/**
	 * 
	 * @param rupSet
	 * @param logicTreeBranch
	 * @param finalMinMags
	 */
	public U3InversionTargetMFDs(FaultSystemRupSet rupSet, LogicTreeBranch<?> logicTreeBranch, ModSectMinMags finalMinMags,
			PolygonFaultGridAssociations polygons) {
		super(rupSet);
		init(rupSet, logicTreeBranch, finalMinMags, polygons);
	}
	
	private void init(FaultSystemRupSet rupSet, LogicTreeBranch<?> logicTreeBranch, ModSectMinMags finalMinMags,
			PolygonFaultGridAssociations polygons) {
		this.polygons = polygons;
		this.logicTreeBranch = logicTreeBranch;
		this.finalMinMags = finalMinMags;
		this.inversionModel = logicTreeBranch.getValue(InversionModels.class);
		this.totalRegionRateMgt5 = logicTreeBranch.getValue(TotalMag5Rate.class).getRateMag5();
		this.mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		this.applyImpliedCouplingCoeff = logicTreeBranch.getValue(MomentRateFixes.class).isApplyCC();	// true if MomentRateFixes = APPLY_IMPLIED_CC or APPLY_CC_AND_RELAX_MFD
		this.spatialSeisPDF = logicTreeBranch.getValue(SpatialSeisPDF.class);
		
		// convert mMaxOffFault to bin center
		mMaxOffFault -= DELTA_MAG/2;
		
		boolean noMoRateFix = (logicTreeBranch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE);
		
		// this prevents using any non smoothed seismicity PDF for computing rates on fault (def mod PDF doesn't make sense)
		if (!(spatialSeisPDF == SpatialSeisPDF.UCERF2 || spatialSeisPDF == SpatialSeisPDF.UCERF3))
			System.out.println("WARNING: Was previously hardcoded (for unknown reasons) to force U3 or U2 spatial seismicity on faults. "
					+ "This has been disabled.");
			
		spatialSeisPDFforOnFaultRates = spatialSeisPDF;
//		else
//			spatialSeisPDFforOnFaultRates = SpatialSeisPDF.UCERF3;

		
		// test to make sure it's a statewide deformation model
		DeformationModels dm = logicTreeBranch.getValue(DeformationModels.class);
		if(dm == DeformationModels.UCERF2_BAYAREA || dm == DeformationModels.UCERF2_NCAL)
			throw new RuntimeException("Error - "+dm+" not yet supported by InversionMFD");
		
		List<? extends FaultSection> faultSectionData =  rupSet.getFaultSectionDataList();
		
		gridSeisUtils = new GriddedSeisUtils(faultSectionData, spatialSeisPDFforOnFaultRates, polygons);
		
		GriddedRegion noCalGrid = RELM_RegionUtils.getNoCalGriddedRegionInstance();
		GriddedRegion soCalGrid = RELM_RegionUtils.getSoCalGriddedRegionInstance();
		
		fractSeisInSoCal = spatialSeisPDFforOnFaultRates.getFractionInRegion(soCalGrid);
//		fractionSeisOnFault = DeformationModelsCalc.getFractSpatialPDF_InsideSectionPolygons(faultSectionData, spatialSeisPDFforOnFaultRates);
		fractionSeisOnFault = gridSeisUtils.pdfInPolys();

		onFaultRegionRateMgt5 = totalRegionRateMgt5*fractionSeisOnFault;
		offFaultRegionRateMgt5 = totalRegionRateMgt5-onFaultRegionRateMgt5;
		origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData,true);
		FaultModels fm = logicTreeBranch.getValue(FaultModels.class);
		if (fm == null || dm == null) {
			System.err.println("ERR: non-UCERF3 deformation model encountered, can't get DM off fault moment rate. Setting to zero.");
			offFltDefModMoRate = 0d;
		} else {
			offFltDefModMoRate = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(
					logicTreeBranch.getValue(FaultModels.class), logicTreeBranch.getValue(DeformationModels.class));
		}

		// make the total target GR for region
		totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(rupSet.getMaxMag()));
		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*1e5, 1.0);
		
		totalTargetGR_NoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
		totalTargetGR_NoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*(1-fractSeisInSoCal)*1e5, 1.0);
		
		
		totalTargetGR_SoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
		totalTargetGR_SoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*fractSeisInSoCal*1e5, 1.0);
		
		// get ave min seismo mag for region
		double tempMag = FaultSystemRupSetCalc.getMeanMinMag(rupSet, finalMinMags, true);
		
		// This is a test of applying the minimum rather than average among section min mags in the tri-linear target
		// (increases on fault target by up to 11% (at mean mag) by doing this for case tested; not a big diff, and will make implied off-fault CC worse)
//		double tempMag = 100;
//		for(int s=0;s<invRupSet.getNumSections();s++) {
//			double minMag = invRupSet.getFinalMinMagForSection(s);
//			if(minMag<tempMag) tempMag = minMag;
//			if(minMag<6.301)
//				System.out.println("\t"+(float)minMag+"\t"+invRupSet.getFaultSectionData(s).getParentSectionName());
//		}
//		System.out.println("\ntempMag="+tempMag+"\n");
		
		aveMinSeismoMag = totalTargetGR.getX(totalTargetGR.getClosestXIndex(tempMag));	// round to nearest MFD value

		if(D) {
			debugString = "\ttotalRegionRateMgt5 =\t"+totalRegionRateMgt5+"\n"+
					"\tmMaxOffFault =\t"+mMaxOffFault+"\n"+
					"\tapplyImpliedCouplingCoeff =\t"+applyImpliedCouplingCoeff+"\n"+
					"\tspatialSeisPDF =\t"+spatialSeisPDF+"\n"+
					"\tspatialSeisPDFforOnFaultRates =\t"+spatialSeisPDFforOnFaultRates+"\n"+
					"\tinversionModel =\t"+inversionModel+"\n"+
					"\tfractSeisInSoCal =\t"+(float)fractSeisInSoCal+"\n"+
					"\tfractionSeisOnFault =\t"+(float)fractionSeisOnFault+"\n"+
					"\tonFaultRegionRateMgt5 =\t"+(float)onFaultRegionRateMgt5+"\n"+
					"\toffFaultRegionRateMgt5 =\t"+(float)offFaultRegionRateMgt5+"\n"+
					"\torigOnFltDefModMoRate =\t"+(float)origOnFltDefModMoRate+"\n"+
					"\toffFltDefModMoRate =\t"+(float)offFltDefModMoRate+"\n"+
					"\troundedMmaxOnFault =\t"+(float)roundedMmaxOnFault+"\n"+
					"\ttotalTargetGR(5.05) =\t"+(float)totalTargetGR.getCumRate(5.05)+"\n"+
					"\taveMinSeismoMag =\t"+(float)aveMinSeismoMag+"\n";
		}

		
		
		if (inversionModel.isCharacteristic()) {

			trulyOffFaultMFD = FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(totalTargetGR, onFaultRegionRateMgt5, aveMinSeismoMag, mMaxOffFault);

//			subSeismoOnFaultMFD_List = FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, spatialSeisPDF, totalTargetGR);
			subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
					rupSet, finalMinMags, gridSeisUtils, totalTargetGR));

			totalSubSeismoOnFaultMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			for(int m=0; m<subSeismoOnFaultMFDs.size(); m++) {
				IncrementalMagFreqDist mfd = subSeismoOnFaultMFDs.get(m);
//				if(mfd.getMagUpper() <= 5.05 & D) {
//					debugString += "\tWARNING: "+faultSectionData.get(m).getName()+" has a max subSeism mag of "+mfd.getMagUpper()+" so no contribution above M5!\n";
//				}
//				if(Double.isNaN(mfd.getTotalIncrRate()))
//					throw new RuntimeException("Bad MFD for section:\t"+m+"\t"+faultSectionData.get(m).getName()+"\tslipRate="+faultSectionData.get(m).getReducedAveSlipRate());
				totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
			}

			targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
			targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(trulyOffFaultMFD);
			targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
			
			// split the above between N & S cal
			noCalTargetSupraMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			soCalTargetSupraMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			for(int i=0; i<NUM_MAG; i++) {
				noCalTargetSupraMFD.set(i,targetOnFaultSupraSeisMFD.getY(i)*(1.0-fractSeisInSoCal));
				soCalTargetSupraMFD.set(i,targetOnFaultSupraSeisMFD.getY(i)*fractSeisInSoCal);
			}

			// compute coupling coefficients
			impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()+totalSubSeismoOnFaultMFD.getTotalMomentRate())/origOnFltDefModMoRate;
			finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate()/offFltDefModMoRate;
			impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate()/(origOnFltDefModMoRate+offFltDefModMoRate);

		} else {
			// GR
			
			// get the total GR nucleation MFD for all fault sections based on their orig (creep reduced) slip rates and max mags
			SummedMagFreqDist impliedOnFault_GR_NuclMFD = FaultSystemRupSetCalc.calcImpliedGR_NucleationMFD(rupSet, MIN_MAG, NUM_MAG, DELTA_MAG);

			// compute coupling coefficient
			impliedOnFaultCouplingCoeff = onFaultRegionRateMgt5/impliedOnFault_GR_NuclMFD.getCumRate(5.05);
			double tempCoupCoeff = 1;	// defaults to 1.0; this is used below
			if(applyImpliedCouplingCoeff && impliedOnFaultCouplingCoeff < 1.0) 	// only apply if it's < 1
				tempCoupCoeff = impliedOnFaultCouplingCoeff;	
			
			if(D) {
				debugString += "\timpliedOnFault_GR_NuclMFD(5.05) =\t"+impliedOnFault_GR_NuclMFD.getCumRate(5.05);
				debugString += "\tempCoupCoeff =\t"+tempCoupCoeff+"\n";
			}

			// split the on-fault MFDs into supra- vs sub-seismo MFDs, and apply tempCoupCoeff
			ArrayList<GutenbergRichterMagFreqDist> grNuclMFD_List = FaultSystemRupSetCalc.calcImpliedGR_NuclMFD_ForEachSection(rupSet, MIN_MAG, NUM_MAG, DELTA_MAG);
			ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = new ArrayList<GutenbergRichterMagFreqDist>();
			totalSubSeismoOnFaultMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			noCalTargetSupraMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			soCalTargetSupraMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			// loop over sections
			for(int s=0;s<grNuclMFD_List.size();s++) {
				GutenbergRichterMagFreqDist grNuclMFD = grNuclMFD_List.get(s);
//				int minSupraMagIndex = grNuclMFD.getClosestXIndex(invRupSet.getMinMagForSection(s));
//				double maxMagSubSeismo = grNuclMFD.getX(minSupraMagIndex-1);
//				double maxMagSubSeismo = invRupSet.getUpperMagForSubseismoRuptures(s);
				double maxMagSubSeismo = InversionFaultSystemRupSet.getUpperMagForSubseismoRuptures(finalMinMags.getMinMagForSection(s));
				int minSupraMagIndex = grNuclMFD.getXIndex(maxMagSubSeismo)+1;
				GutenbergRichterMagFreqDist subSeisGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG, MIN_MAG, maxMagSubSeismo, 1.0, 1.0);
				double rateAtZeroMagBin = grNuclMFD.getY(0)*tempCoupCoeff;
				subSeisGR.scaleToIncrRate(0, rateAtZeroMagBin);
				subSeismoOnFaultMFD_List.add(subSeisGR);
				totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(subSeisGR);
				FaultTrace sectTrace = faultSectionData.get(s).getFaultSurface(1.0).getEvenlyDiscritizedUpperEdge();
				double fractSectInSoCal = RegionUtils.getFractionInside(soCalGrid, sectTrace);
				for(int i=minSupraMagIndex;i<grNuclMFD.size();i++) {
					targetOnFaultSupraSeisMFD.add(i, grNuclMFD.getY(i)*tempCoupCoeff);
					noCalTargetSupraMFD.add(i, grNuclMFD.getY(i)*tempCoupCoeff*(1.0-fractSectInSoCal));
					soCalTargetSupraMFD.add(i, grNuclMFD.getY(i)*tempCoupCoeff*fractSectInSoCal);
				}
			}
			subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(subSeismoOnFaultMFD_List);
			
			// If on the NoFix branch, we need to reduce totalSubSeismoOnFaultMFD and targetOnFaultSupraSeisMFD so
			// that they sum with trulyOffFaultMFD to match the regional target (because we want the inversion to reduce the slip
			// rates to match targets); Note that we are not reducing subSeismoOnFaultMFD_List because these are used elsewhere in reducing final
			// target slip rates; Therefore subSeismoOnFaultMFD_List needs to be recomputed on this GR NoFix branch
			// since final slip rates will vary (and we need the subseismo GR to be consistent with the supra-seismo GR)
			if(noMoRateFix && impliedOnFaultCouplingCoeff < 1.0) {
				totalSubSeismoOnFaultMFD.scale(impliedOnFaultCouplingCoeff);
				targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
				targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
				targetOnFaultSupraSeisMFD.scaleToIncrRate(5.05, impliedOnFault_GR_NuclMFD.getY(5.05)*impliedOnFaultCouplingCoeff);
//				targetOnFaultSupraSeisMFD.scale(fractionSeisOnFault); this has numerical precisions problems?
				targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
				noCalTargetSupraMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
				soCalTargetSupraMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
				for(int i=0;i<targetOnFaultSupraSeisMFD.size();i++) {
					noCalTargetSupraMFD.add(i, targetOnFaultSupraSeisMFD.getY(i)*(1.0-fractSeisInSoCal));	// this is approximate ?????????
					soCalTargetSupraMFD.add(i, targetOnFaultSupraSeisMFD.getY(i)*fractSeisInSoCal);
				}
			}
// System.out.println(MIN_MAG+"\t"+NUM_MAG+"\t"+DELTA_MAG+"\t"+MIN_MAG+"\t"+mMaxOffFault);
			trulyOffFaultMFD = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG, MIN_MAG, mMaxOffFault, 1.0, 1.0);
			trulyOffFaultMFD.scaleToCumRate(0, offFaultRegionRateMgt5*1e5);
			if(GR_OFF_FAULT_IS_TAPERED) {
				double moRate = trulyOffFaultMFD.getTotalMomentRate();
				trulyOffFaultMFD = new TaperedGR_MagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
				((TaperedGR_MagFreqDist)trulyOffFaultMFD).setAllButCornerMag(MIN_MAG, moRate, offFaultRegionRateMgt5*1e5, 1.0);
			}

			// compute coupling coefficients
			finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate()/offFltDefModMoRate;
			impliedTotalCouplingCoeff = (impliedOnFaultCouplingCoeff*origOnFltDefModMoRate+finalOffFaultCouplingCoeff*offFltDefModMoRate)/(origOnFltDefModMoRate+offFltDefModMoRate);

		}
		
		if(D) {
			debugString += "\timpliedOnFaultCouplingCoeff =\t"+(float)impliedOnFaultCouplingCoeff+"\n"+
					"\tfinalOffFaultCouplingCoeff =\t"+(float)finalOffFaultCouplingCoeff+"\n"+
					"\timpliedTotalCouplingCoeff =\t"+(float)impliedTotalCouplingCoeff+"\n"+
					"\ttrulyOffFaultMFD(5.05) =\t"+(float)trulyOffFaultMFD.getCumRate(5.05)+"\n"+
					"\ttotalSubSeismoOnFaultMFD(5.05) =\t"+(float)totalSubSeismoOnFaultMFD.getCumRate(5.05)+"\n"+
					"\ttargetOnFaultSupraSeisMFD(5.05) =\t"+(float)targetOnFaultSupraSeisMFD.getCumRate(5.05)+"\n"+
					"\tsum of above three =\t"+(float)(trulyOffFaultMFD.getCumRate(5.05)+totalSubSeismoOnFaultMFD.getCumRate(5.05)+targetOnFaultSupraSeisMFD.getCumRate(5.05))+"\n"+
					"\tnoCalTargetMFD(5.05) =\t"+(float)noCalTargetSupraMFD.getCumRate(5.05)+"\n"+
					"\tsoCalTargetMFD(5.05) =\t"+(float)soCalTargetSupraMFD.getCumRate(5.05)+"\n"+
					"\tsum of above two =\t"+(float)(noCalTargetSupraMFD.getCumRate(5.05)+soCalTargetSupraMFD.getCumRate(5.05))+"\n";
			System.out.println(debugString);
		}
		
		// set the names
		totalTargetGR.setName("InversionTargetMFDs.totalTargetGR");
		totalTargetGR_NoCal.setName("InversionTargetMFDs.totalTargetGR_NoCal");
		totalTargetGR_SoCal.setName("InversionTargetMFDs.totalTargetGR_SoCal");
		targetOnFaultSupraSeisMFD.setName("InversionTargetMFDs.targetOnFaultSupraSeisMFD");
		trulyOffFaultMFD.setName("InversionTargetMFDs.trulyOffFaultMFD");
		totalSubSeismoOnFaultMFD.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD");
		noCalTargetSupraMFD.setName("InversionTargetMFDs.noCalTargetSupraMFD");
		soCalTargetSupraMFD.setName("InversionTargetMFDs.soCalTargetSupraMFD");

		
		mfdConstraintsForNoAndSoCal = new ArrayList<IncrementalMagFreqDist>();
		noCalTargetSupraMFD.setRegion(new Region(noCalGrid));
		mfdConstraintsForNoAndSoCal.add(noCalTargetSupraMFD);
		soCalTargetSupraMFD.setRegion(new Region(soCalGrid));
		mfdConstraintsForNoAndSoCal.add(soCalTargetSupraMFD);

	}

	// never used
	public double getTotalRegionRateMgt5() {return totalRegionRateMgt5;}
	
	// used in 1 plotting routine
	public double getMmaxOffFault() {return mMaxOffFault;}
	
	// used in 1 plotting routine
	public double getFractionSeisOnFault() {return fractionSeisOnFault;}
	
	// used in InversionFaultSystemRupSet.calcRuptureAttributes if <1
	public double getImpliedOnFaultCouplingCoeff() {return impliedOnFaultCouplingCoeff;}
	
	// widely used for plotting
	// used for smooth starting solution in UCERF3InversionInputGenerator
	@Override
	public IncrementalMagFreqDist getTotalOnFaultSupraSeisMFD() {return targetOnFaultSupraSeisMFD;}
	
	// used by grid source generators
	@Override
	public IncrementalMagFreqDist getTrulyOffFaultMFD() {return trulyOffFaultMFD;}
	
	// used by InversionFaultSystemRupSet.calcRuptureAttributes, but only for G-R branches (so unused in final U3 model)
	// supplies the sub-seismo on fault MFDs to InversionFaultSystemSolution, which is then used widely in analysis
	// and by UCERF3-ETAS
	/**
	 * See class description above for details on what this returns in different situations
	 * @return
	 */
	public SubSeismoOnFaultMFDs getOnFaultSubSeisMFDs() {return subSeismoOnFaultMFDs;}
	
	// used by InversionFaultSystemRupSet.calcRuptureAttributes for characteristic branches (thus used in final U3 model)
	/**
	 * See class description above for details on what this returns in different situations
	 * @return
	 */
	@Override
	public IncrementalMagFreqDist getTotalOnFaultSubSeisMFD() {return totalSubSeismoOnFaultMFD;}

	// widely used in plots
	// used by IVFSS.getFinalTrulyOffFaultMFD(), which is integral to U3 grid source provider
	@Override
	public IncrementalMagFreqDist getTotalRegionalMFD() {return totalTargetGR;}
	
	// only used for plots
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {return totalTargetGR_NoCal;}
	
	// only used for plots
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {return totalTargetGR_SoCal;}
	
	// used by InversionFaultSystemRupSet.calcRuptureAttributes for characteristic branches (thus used in final U3 model)
	/**
	 * This has been reduced by creep (aseismicity and coupling coefficient in FaultSectionPrefData),
	 * but not by subseismo rupture or any implied coupling coefficients.
	 * @return
	 */
	public double getOrigOnFltDefModMoRate() {return origOnFltDefModMoRate; }
	
	
	// used directly by U3 inversion configuration
	/**
	 * This returns the northern and southern RELM region MFD_InversionConstraint 
	 * (as the 0th and 1st List elements, respectively).  The associated MFDs have been reduced
	 * by both off-fault and subseismogenic ruptures.
	 * @return
	 */
	@Override
	public List<IncrementalMagFreqDist> getMFD_Constraints() { return mfdConstraintsForNoAndSoCal; }
	
	public String getPreInversionAnalysisData() {
		String str = (float)fractionSeisOnFault+"\t" +
			(float)fractSeisInSoCal+"\t"+
			(float)roundedMmaxOnFault+"\t" +
			(float)aveMinSeismoMag+"\t" +
			(float)origOnFltDefModMoRate+"\t" +
			(float)offFltDefModMoRate+"\t" +
			(float)impliedOnFaultCouplingCoeff+"\t"+
			(float)finalOffFaultCouplingCoeff+"\t"+
			(float)impliedTotalCouplingCoeff+"\t"+
			(float)trulyOffFaultMFD.getCumRate(5.05)+"\t"+
			(float)totalSubSeismoOnFaultMFD.getCumRate(5.05)+"\t"+
			(float)targetOnFaultSupraSeisMFD.getCumRate(5.05)+"\t"+
			(float)noCalTargetSupraMFD.getCumRate(5.05)+"\t"+
			(float)soCalTargetSupraMFD.getCumRate(5.05)+"\t"+
			(float)trulyOffFaultMFD.getTotalMomentRate()+"\t"+
			(float)totalSubSeismoOnFaultMFD.getTotalMomentRate()+"\t"+
			(float)targetOnFaultSupraSeisMFD.getTotalMomentRate()+"\t"+
			(float)noCalTargetSupraMFD.getTotalMomentRate()+"\t"+
			(float)soCalTargetSupraMFD.getTotalMomentRate();

		return str;
	}
	
	public String getPreInversionAnalysisDataHeader() {
		String str = "frSeisOnFlt"+"\t" +
			"frSeisInSoCal"+"\t"+
			"MmaxOnFlt"+"\t" +
			"aveSupraSeisMmin"+"\t" +
			"onFltDefModMoRate"+"\t" +
			"offFltDefModMoRate"+"\t" +
			"implOnFaultCC"+"\t"+
			"finalOffFaultCC"+"\t"+
			"implTotalCC"+"\t"+
			"trulyOffFltMFD_RateM5"+"\t"+
			"subSeisOnFltMFD_RateM5"+"\t"+
			"targetOnFtSupraSeisMFD_RateM5"+"\t"+
			"noCalTargetSuprSeisMFD_RateM5"+"\t"+
			"soCalTargetSuprSeisMFD_RateM5"+"\t"+
			"trulyOffFltMFD_MoRate"+"\t"+
			"subSeisOnFltMFD_MoRate"+"\t"+
			"targetOnFtSupraSeisMFD_MoRate"+"\t"+
			"noCalTargetSuprSeisMFD_MoRate"+"\t"+
			"soCalTargetSuprSeisMFD_MoRate";
		return str;
	}

	// used only for U3 pre-inversion analysis
	/**
	 * This returns the maximum magnitude off fault if the total original off-fault 
	 * moment rate is satisfied.  If (inversionModel.isCharacteristic() == true), Double.NaN 
	 * is returned if it's impossible to satisfy the moment rate.
	 * @return
	 */
	public double getOffFaultMmaxIfOrigMoRateSatisfied() {
		double maxOffMagWithFullMoment;
		if(inversionModel.isCharacteristic()) {
			IncrementalMagFreqDist charOffMFD = FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(offFltDefModMoRate, 
					totalTargetGR, onFaultRegionRateMgt5, aveMinSeismoMag);
			if(charOffMFD != null)
				maxOffMagWithFullMoment = charOffMFD.getMaxMagWithNonZeroRate();
			else
				maxOffMagWithFullMoment = Double.NaN;
		}
		else {
			GutenbergRichterMagFreqDist tempOffFaultGR = new GutenbergRichterMagFreqDist(0.005, 2000, 0.01);
			tempOffFaultGR.setAllButMagUpper(0.005, offFltDefModMoRate, offFaultRegionRateMgt5*1e5, 1.0, true);
			maxOffMagWithFullMoment = tempOffFaultGR.getMagUpper();
		}
			
		return maxOffMagWithFullMoment;

	}
	
	// used only for 1 plot
	public double getOffFaultRegionRateMgt5() {return offFaultRegionRateMgt5; }
	
	// used by UCERF3_GridSourceGenerator to get the FaultPolyMgr instance
	/**
	 * Returns the utility GriddedSeisUtils instance for reuse elsewhere.
	 * @return
	 */
	public GriddedSeisUtils getGridSeisUtils() {
		return gridSeisUtils;
	}

	// used only for 1 plot
	/**
	 * This returns an incremental GR with b=1 up to Mag 9 for the given rate above M 5
	 * (e.g., useful for guiding eyes in plots).
	 * @param totalRegionRateMgt5
	 * @return
	 */
	public static GutenbergRichterMagFreqDist getTotalTargetGR_upToM9(double totalRegionRateMgt5) {
		GutenbergRichterMagFreqDist gr =
				new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG, 1.0, 1.0);
		gr.scaleToCumRate(0, totalRegionRateMgt5*1e5);
		return gr;
	}

	@Override
	public String getName() {
		return "UCERF3 Inversion Target MFDs";
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		new Precomputed(this).writeToArchive(output, entryPrefix);
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		// only used if this was serialized before the Precomputed class was implemented
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkNotNull(rupSet, "Rupture set not initialized");
		init(rupSet, rupSet.requireModule(U3LogicTreeBranch.class), rupSet.requireModule(ModSectMinMags.class), 
				rupSet.requireModule(PolygonFaultGridAssociations.class));
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs.Precomputed.class;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		FaultSystemRupSet rupSet = getParent();
		Preconditions.checkState(rupSet == null || rupSet.isEquivalentTo(newParent));
		if (!newParent.hasModule(U3LogicTreeBranch.class))
			newParent.addModule(logicTreeBranch);
		if (!newParent.hasModule(ModSectMinMags.class))
			newParent.addModule(finalMinMags);
		if (!newParent.hasModule(PolygonFaultGridAssociations.class))
			newParent.addModule(polygons);
		return new U3InversionTargetMFDs(newParent, logicTreeBranch, finalMinMags, polygons);
	}

	@Override
	public AveragingAccumulator<InversionTargetMFDs> averagingAccumulator() {
		return new Precomputed(this).averagingAccumulator();
	}

}
