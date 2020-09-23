package scratch.UCERF3.inversion;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;

import com.google.common.base.Preconditions;

/**
 * This class serves as a factory for loading/building FaultSystemRupSet's for each branch of the UCERF3 logic tree.<br>
 * <br>
 * It's worth noting that this class uses each Fault Model's filter basis to determine which deformation model to filter by.
 * This means that when, for example, a FM 3.1 ABM rupture set is generated, it is filtered as if it were FM 3.1 Geologic.
 * 
 * @author Kevin
 *
 */
public class InversionFaultSystemRupSetFactory {
	
	public static final double DEFAULT_ASEIS_VALUE = 0.1;
	
	private static File rup_set_store_dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "FaultSystemRupSets");
	
	/**
	 * This loads a rupture set for the specified deformation model (and it's first applicable fault model) using all
	 * other default branch choices and the default laugh test filter.<br>
	 * <br>
	 * It will first attempt to see if a file exists in the precomputed data directory with the same name as
	 * the deformation model. If so, that file will be simply loaded. Otherwise it will be created and the file
	 * will be written to disk for future caching.
	 * 
	 * @param deformationModel
	 * @return
	 * @throws IOException 
	 */
	public static InversionFaultSystemRupSet cachedForBranch(LogicTreeBranchNode<?>... branchNodes) throws IOException {
		return cachedForBranch(false, branchNodes);
	}
	
	/**
	 * This loads a rupture set for the specified deformation model (and it's first applicable fault model) using all
	 * other default branch choices and the default laugh test filter.<br>
	 * <br>
	 * It will first attempt to see if a file exists in the precomputed data directory with the same name as
	 * the deformation model. If so, that file will be simply loaded. Otherwise it will be created and the file
	 * will be written to disk for future caching.
	 * 
	 * @param deformationModel
	 * @return
	 * @throws IOException 
	 */
	public static InversionFaultSystemRupSet cachedForBranch(boolean forceRebuild, LogicTreeBranchNode<?>... branchNodes) throws IOException {
		return cachedForBranch(rup_set_store_dir, forceRebuild, branchNodes);
	}
	
	/**
	 * This loads a rupture set for the specified fault/deformation model using all other default branch
	 * choices and the default laugh test filter.<br>
	 * <br>
	 * It will first attempt to see if a file exists in the precomputed data directory with the same name as
	 * the deformation model. If so, that file will be simply loaded. Otherwise it will be created and the file
	 * will be written to disk for future caching.
	 * 
	 * @param deformationModel
	 * @return
	 * @throws IOException 
	 */
	public static InversionFaultSystemRupSet cachedForBranch(
			File directory, boolean forceRebuild, LogicTreeBranchNode<?>... branchNodes)
			throws IOException {
		LogicTreeBranch branch = LogicTreeBranch.fromValues(branchNodes);
		FaultModels faultModel = branch.getValue(FaultModels.class);
		DeformationModels deformationModel = branch.getValue(DeformationModels.class);
		InversionModels invModel = branch.getValue(InversionModels.class);
		String fileName = deformationModel.name()+"_"+faultModel.name()+".zip";
		File file = new File(directory, fileName);
		if (!forceRebuild && file.exists()) {
			System.out.println("Loading cached rup set from file: "+file.getAbsolutePath());
			
			try {
				InversionFaultSystemRupSet rupSet = FaultSystemIO.loadInvRupSet(file);
				
				return rupSet;
			} catch (Exception e) {
				System.err.println("Error loading rupset from file: "+file.getAbsolutePath());
				e.printStackTrace();
			}
		}
		// this means the file didn't exist, we had an error loading it, or we're forcing a rebuild
		InversionFaultSystemRupSet rupSet = forBranch(UCERF3PlausibilityConfig.getDefault(), DEFAULT_ASEIS_VALUE, branch);
		System.out.println("Caching rup set to file: "+file.getAbsolutePath());
		if (!directory.exists())
			directory.mkdir();
		FaultSystemIO.writeRupSet(rupSet, file);
		return rupSet;
	}
	
	/**
	 * Creates a rupture set for the specified branch on the logic tree and the given laugh test filter.
	 * Any logic tree branch values not chosen will be set to default.
	 * 
	 * @param branchesChoices Logic tree branch values. any values that are omitted will be set to default as
	 * specified by <code>LogicTreeBranch.DEFAULT</code>
	 * @return
	 */
	public static InversionFaultSystemRupSet forBranch(LogicTreeBranchNode<?>... branchesChoices) {
		return forBranch(UCERF3PlausibilityConfig.getDefault(), DEFAULT_ASEIS_VALUE, branchesChoices);
	}
	
	/**
	 * Creates a rupture set for the specified branch on the logic tree and the given laugh test filter.
	 * Any logic tree branch values not chosen will be set to default.
	 * 
	 * @param branchesChoices Logic tree branch values. any values that are omitted will be set to default as
	 * specified by <code>LogicTreeBranch.DEFAULT</code>
	 * @return
	 */
	public static InversionFaultSystemRupSet forBranch(LogicTreeBranch branch) {
		return forBranch(UCERF3PlausibilityConfig.getDefault(), DEFAULT_ASEIS_VALUE, branch);
	}
	
	/**
	 * Creates a rupture set for the specified branch on the logic tree and the given laugh test filter
	 * 
	 * @param laughTest
	 * @param defaultAseismicityValue
	 * @param branchesChoices Logic tree branch values. any values that are omitted will be set to default as
	 * specified by <code>LogicTreeBranch.DEFAULT</code>
	 * @return
	 */
	public static InversionFaultSystemRupSet forBranch(
			UCERF3PlausibilityConfig laughTest,
			double defaultAseismicityValue,
			LogicTreeBranchNode<?>... branchesChoices) {
		LogicTreeBranch branch = LogicTreeBranch.fromValues(branchesChoices);
		return forBranch(laughTest, defaultAseismicityValue, branch);
	}
	
	/**
	 * Creates a rupture set for the specified branch on the logic tree and the given laugh test filter
	 * 
	 * @param laughTest
	 * @param defaultAseismicityValue
	 * @param branch Logic tree branch for which to build a model. Must be fully specified (no null values)</code>
	 * @return
	 */
	public static InversionFaultSystemRupSet forBranch(
			UCERF3PlausibilityConfig laughTest,
			double defaultAseismicityValue,
			LogicTreeBranch branch) {
		return forBranch(laughTest, defaultAseismicityValue, branch, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR);
	}
	
	/**
	 * Creates a rupture set for the specified branch on the logic tree and the given laugh test filter
	 * 
	 * @param laughTest
	 * @param defaultAseismicityValue
	 * @param branch Logic tree branch for which to build a model. Must be fully specified (no null values)</code>
	 * @param scratchDir this is the scratch directory where temporary distance calculation files should be cached.
	 * @return
	 */
	public static InversionFaultSystemRupSet forBranch(
			UCERF3PlausibilityConfig laughTest,
			double defaultAseismicityValue,
			LogicTreeBranch branch,
			File scratchDir) {
		Preconditions.checkArgument(branch.isFullySpecified(), "Logic tree must be fully specified (no null values) in order " +
				"to create an InversionFaultSystemRupSet.");
		
		FaultModels faultModel = branch.getValue(FaultModels.class);
		DeformationModels deformationModel = branch.getValue(DeformationModels.class);
		System.out.println("Building a rupture set for: "+deformationModel+" ("+faultModel+")");
		
		if (faultModel == FaultModels.FM2_1 && laughTest.getCoulombFilter() != null) {
			System.out.println("WARNING: removing coulomb filter since this is FM 2.1");
			laughTest.setCoulombFilter(null);
		}
		
		DeformationModels filterBasis = faultModel.getFilterBasis();
		if (filterBasis == null) {
//			System.out.println("No filter basis specified!");
			filterBasis = deformationModel;
		}
		DeformationModelFetcher filterBasisFetcher = new DeformationModelFetcher(faultModel, filterBasis,
				scratchDir, defaultAseismicityValue);
		CoulombRates coulombRates = null;
		if (laughTest.getCoulombFilter() != null) {
			try {
				coulombRates = CoulombRates.loadUCERF3CoulombRates(faultModel);
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		SectionConnectionStrategy connectionStrategy = new UCERF3SectionConnectionStrategy(
				laughTest.getMaxAzimuthChange(), coulombRates);
		laughTest.setCoulombRates(coulombRates);
//		System.out.println("Creating clusters with filter basis: "+filterBasis+", Fault Model: "+faultModel);
//		SectionClusterList clusters = new SectionClusterList(filterBasisFetcher, laughTest);
		SectionClusterList clusters = new SectionClusterList(filterBasisFetcher, connectionStrategy, laughTest);
		
		List<? extends FaultSection> faultSectionData;
		if (filterBasis == deformationModel) {
			faultSectionData = clusters.getFaultSectionData();
		} else {
			// we need to get it ourselves
			faultSectionData = new DeformationModelFetcher(faultModel, deformationModel,
					scratchDir, defaultAseismicityValue).getSubSectionList();
		}
		
		InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(branch,
				clusters, faultSectionData);
		System.out.println("New rup set has "+rupSet.getNumRuptures()+" ruptures.");
		String info = rupSet.getInfoString();
		if (info == null)
			info = "";
		else
			info += "\n\n";
		
		info += "\n****** Logic Tree Branch ******";
		for (LogicTreeBranchNode<?> node : branch)
			info += "\n"+ClassUtils.getClassNameWithoutPackage(LogicTreeBranch.getEnumEnclosingClass(node.getClass()))
							+": "+node.name();
		info += "\n*******************************";
		rupSet.setInfoString(info);
		return rupSet;
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		try {
//			NCAL_SMALL.getRupSet();
//			NCAL_SMALL_UNIFORM.getRupSet();
//			NCAL.getRupSet(true);
//			ALLCAL_SMALL.getRupSet(true);
//			ALLCAL.getRupSet(true);
//			UCERF3_ALLCAL_3_1_KLUDGE.getRupSet(true);
//			UCERF3_GEOLOGIC.getRupSet(true);
//			cachedForBranch(DeformationModels.GEOLOGIC, true);
//			forBranch(DeformationModels.ABM);
//			FaultSystemRupSet rupSet = forBranch(FaultModels.FM3_1);
			
			forBranch(FaultModels.FM3_1, DeformationModels.ABM,
			ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.GR_CONSTRAINED, TotalMag5Rate.RATE_9p6,
			MaxMagOffFault.MAG_7p3, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
			
			UCERF3PlausibilityConfig filter = UCERF3PlausibilityConfig.getDefault();
//			LaughTestFilter filter = LaughTestFilter.getUCERF3p2Filter();
//			filter.setCoulombFilter(new CoulombRatesTester(TestType.COULOMB_STRESS, 0.05, 0.05, 1.25, true));
			FaultSystemRupSet rupSet = forBranch(filter, DEFAULT_ASEIS_VALUE, LogicTreeBranch.getMEAN_UCERF3(FaultModels.FM3_1));
			System.out.println("FM3.1: "+rupSet.getNumRuptures()+" rups, "+rupSet.getNumSections()+" sects");
			rupSet = forBranch(filter, DEFAULT_ASEIS_VALUE, LogicTreeBranch.getMEAN_UCERF3(FaultModels.FM3_2));
			System.out.println("FM3.2: "+rupSet.getNumRuptures()+" rups, "+rupSet.getNumSections()+" sects");
			FaultSystemIO.writeRupSet(rupSet, new File("/tmp/mean_rupSet.zip"));
			// test loading
			InversionFaultSystemRupSet invRupSet = FaultSystemIO.loadInvRupSet(new File("/tmp/mean_rupSet.zip"));
			System.out.println(invRupSet.getLogicTreeBranch());
			System.out.println(invRupSet.getOldPlausibilityConfiguration());
			System.exit(0);
//			new SimpleFaultSystemRupSet(rupSet).toZipFile(new File("/tmp/rup_set_0.05_1.25.zip"));
//			filter.setAllowSingleSectDuringJumps(false);
//			List<Integer> counts = Lists.newArrayList();
//			List<Double> ratios = Lists.newArrayList();
//			for (double ratio=0; ratio<=0.1; ratio+=0.005) {
//				filter.getCoulombFilter().setMinAverageProb(ratio);
//				filter.getCoulombFilter().setMinIndividualProb(ratio);
//				counts.add(forBranch(filter, DEFAULT_ASEIS_VALUE, FaultModels.FM3_1).getNumRuptures());
//				ratios.add(ratio);
//			}
//			System.out.println("<coulomb ratio>: <rupture count>");
//			for (int i=0; i<counts.size(); i++)
//				System.out.println(ratios.get(i).floatValue()+": "+counts.get(i));
			
//			FaultSystemRupSet rupSet = forBranch(FaultModels.FM3_2, DeformationModels.GEOLOGIC_UPPER, InversionModels.CHAR);
//			cachedForBranch(true, DeformationModels.UCERF2_ALL);
//			InversionFaultSystemRupSet rupSet = forBranch(LogicTreeBranch.DEFAULT);
//			FaultSystemRupSet rupSet = cachedForBranch(FaultModels.FM2_1, DeformationModels.UCERF2_ALL, true);
//			FaultSystemRupSet rupSet = forBranch(FaultModels.FM3_1, DeformationModels.GEOLOGIC, MagAreaRelationships.ELL_B, AveSlipForRupModels.ELLSWORTH_B,
//					SlipAlongRuptureModels.TAPERED, InversionModels.GR, LaughTestFilter.getDefault(), MomentReductions.INCREASE_ASEIS);
			System.out.println("Num sub sects: "+rupSet.getFaultSectionDataList().size());
			
//			FaultSystemRupSet rupSet = forBranch(FaultModels.FM2_1, DeformationModels.UCERF2_ALL, InversionModels.CHAR);
			UCERF3_PaleoRateConstraintFetcher.getConstraints(rupSet.getFaultSectionDataList());
			
			System.out.println("Total Orig Mo Rate (including creep reductions): "+rupSet.getTotalOrigMomentRate());
			System.out.println("Total Reduced Mo Rate (subseis and creep): "+rupSet.getTotalReducedMomentRate());
			System.out.println("Total Mo Rate Reduction (for subseis only): "+rupSet.getTotalMomentRateReduction());
			System.out.println("Total Mo Rate Reduction Fraction (for subseis, relative to creep reduced): "+rupSet.getTotalMomentRateReductionFraction());
			
			System.out.println("\n"+rupSet.getInfoString());
			
//			String info1 = rupSet.getPreInversionAnalysisData(true);
//			LogicTreeBranch br = (LogicTreeBranch) LogicTreeBranch.UCERF2.clone();
//			br.setValue(InversionModels.GR_CONSTRAINED);
//			rupSet = cachedForBranch(true, FaultModels.FM3_2);
//			String info2 = rupSet.getPreInversionAnalysisData(false);
//			System.out.println(info1);
//			System.out.println(info2);
			
			// slip for an 8.4
//			int id = 132520;
//			double area = rupSet.getAreaForRup(id);
//			double aveSlip = rupSet.getAveSlipForRup(id);
//			double[] slips = rupSet.getSlipOnSectionsForRup(id);
//			int middle = slips.length / 2;
//			System.out.println("Mag "+rupSet.getMagForRup(id)+": area: "+area+" aveSlip: "+aveSlip+" middle slip: "+slips[middle]);
			
//			FaultSystemRupSet rupSet = cachedForBranch(FaultModels.FM3_1, DeformationModels.GEOLOGIC_PLUS_ABM, true);
			
//			for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
//				List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
//				if (rups.isEmpty())
//					System.out.println("No ruptures for section: "+sectIndex+". "+rupSet.getFaultSectionData(sectIndex).getSectionName());
//			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

}
