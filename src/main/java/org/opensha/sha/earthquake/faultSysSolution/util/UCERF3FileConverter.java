package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.U3CompoundFaultSystemSolution;
import scratch.UCERF3.U3FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider.Precomputed;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.UCERF3_DataUtils;

class UCERF3FileConverter {

	public static void main(String[] args) throws ZipException, IOException {
		File inputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/orig");
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular");
		File comoundFile = new File(inputDir, "full_ucerf3_compound_sol.zip");
		
		U3FaultSystemSolutionFetcher cfss = U3CompoundFaultSystemSolution.fromZipFile(comoundFile);
		
		Map<Integer, List<LastEventData>> lastEventData = LastEventData.load();
		
		FaultModels[] fms = {FaultModels.FM3_1, FaultModels.FM3_2};
		
		System.out.println("Writing reference branches...");
		for (FaultModels fm : fms) {
			U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(true, fm);
			System.out.println("Ref branch: "+branch);
			InversionFaultSystemSolution ivfss = cfss.getSolution(branch);
			LastEventData.populateSubSects(ivfss.getRupSet().getFaultSectionDataList(), lastEventData);
			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
			attachExtraIVFSRSModules(ivfss.getRupSet());
			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
			branch.setValue(SlipAlongRuptureModels.UNIFORM);
			System.out.println("Ref (UNIFORM) branch: "+branch);
			ivfss = cfss.getSolution(branch);
			LastEventData.populateSubSects(ivfss.getRupSet().getFaultSectionDataList(), lastEventData);
			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
			attachExtraIVFSRSModules(ivfss.getRupSet());
			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
			DeformationModels refDM = branch.getValue(DeformationModels.class);
			for (DeformationModels dm : DeformationModels.values()) {
				if (dm == refDM || dm.getNodeWeight(branch) == 0d)
					continue;
				branch.setValue(dm);
				System.out.println("Alt DM (UNIFORM) branch: "+branch);
				ivfss = cfss.getSolution(branch); 
				LastEventData.populateSubSects(ivfss.getRupSet().getFaultSectionDataList(), lastEventData);
				ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
				attachExtraIVFSRSModules(ivfss.getRupSet());
				ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
			}
		}
		
//		cfss = FaultSystemSolutionFetcher.getSubset(cfss,
//				ScalingRelationships.SHAW_2009_MOD,
//				SlipAlongRuptureModels.TAPERED,
//				InversionModels.CHAR_CONSTRAINED,
//				TotalMag5Rate.RATE_7p9,
//				MaxMagOffFault.MAG_7p6,
//				MomentRateFixes.NONE
////				SpatialSeisPDF.UCERF3
//				);

		// write full branch model
		System.out.println("Writing full model files with "+cfss.getBranches().size()+" branches");
		SolutionLogicTree solTreeModule = new SolutionLogicTree.UCERF3(cfss);
		solTreeModule.setSerializeGridded(false);
		solTreeModule.write(new File(outputDir, "full_logic_tree.zip"));
		solTreeModule.setSerializeGridded(true);
		solTreeModule.write(new File(outputDir, "full_logic_tree_with_gridded.zip"));
//		ModuleArchive<OpenSHA_Module> compoundArchive = new ModuleArchive<>(
//				new File(outputDir, "full_modular_compound_sol.zip"), SolutionLogicTree.class);
//		SolutionLogicTree solTreeModule = compoundArchive.getModule(SolutionLogicTree.class);
//		for (LogicTreeBranch<?> branch : solTreeModule.getLogicTree()) {
//			System.out.println("Loading for "+branch);
//			System.out.println("Branch is type: "+branch.getClass());
//			solTreeModule.forBranch(branch);
//		}
		
		System.out.println("Writing branch averaged files");
		for (FaultModels fm : fms) {
			U3FaultSystemSolutionFetcher fmFetcher = U3FaultSystemSolutionFetcher.getSubset(cfss, fm);
			SolutionLogicTree subTreeModule = new SolutionLogicTree.UCERF3(fmFetcher);
			
			FaultSystemSolution sol = subTreeModule.calcBranchAveraged();
			
			String prefix = fm.encodeChoiceString()+"_branch_averaged";
			writeSimplifiedAndFull(sol, new File(outputDir, prefix+"_full_modules.zip"), new File(outputDir, prefix+".zip"));
			
			subTreeModule.setSerializeGridded(false);
			sol.addModule(subTreeModule);
			sol.write(new File(outputDir, prefix+"_with_logic_tree.zip"));
			
			for (SpatialSeisPDF spatSeis : new SpatialSeisPDF[] {SpatialSeisPDF.UCERF2, SpatialSeisPDF.UCERF3}) {
				U3FaultSystemSolutionFetcher ssFetcher = U3FaultSystemSolutionFetcher.getSubset(fmFetcher, spatSeis);
				
				SolutionLogicTree ssSLT = new SolutionLogicTree.UCERF3(ssFetcher);
				sol = ssSLT.calcBranchAveraged();
				
				prefix = fm.encodeChoiceString()+"_"+spatSeis.encodeChoiceString()+"_branch_averaged";
				writeSimplifiedAndFull(sol, new File(outputDir, prefix+"_full_modules.zip"), new File(outputDir, prefix+".zip"));
			}
		}
	}
	
	private static void writeSimplifiedAndFull(FaultSystemSolution sol, File fullFile, File simplifiedFile) throws IOException {
		sol.write(fullFile);
		sol = SolModuleStripper.stripModules(sol, 0d);
		sol.write(simplifiedFile);
	}
	
	private static GridSourceProvider exactGridProv(GridSourceProvider prov) {
		Preconditions.checkNotNull(prov);
		AbstractGridSourceProvider.Precomputed precomputed = new AbstractGridSourceProvider.Precomputed((MFDGridSourceProvider)prov);
		precomputed.setRound(false);
		return precomputed;
	}
	
	private static void attachExtraIVFSRSModules(InversionFaultSystemRupSet ivfsrs) throws IOException {
		U3LogicTreeBranch branch = ivfsrs.getLogicTreeBranch();
		FaultModels fm = branch.getValue(FaultModels.class);
		if (fm != null) {
			ivfsrs.addModule(new NamedFaults(ivfsrs, fm.getNamedFaultsMapAlt()));
			ivfsrs.addModule(PaleoseismicConstraintData.loadUCERF3(ivfsrs));
		}
	}

}
