package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

class UCERF3FileConverter {

	public static void main(String[] args) throws ZipException, IOException {
		File inputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/orig");
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular");
		File comoundFile = new File(inputDir, "full_ucerf3_compound_sol.zip");
		
		FaultSystemSolutionFetcher cfss = CompoundFaultSystemSolution.fromZipFile(comoundFile);
		
		FaultModels[] fms = {FaultModels.FM3_1, FaultModels.FM3_2};
		
//		for (FaultModels fm : fms) {
//			U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(true, fm);
//			System.out.println("Ref branch: "+branch);
//			InversionFaultSystemSolution ivfss = cfss.getSolution(branch); 
//			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
//			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
//			branch.setValue(SlipAlongRuptureModels.UNIFORM);
//			System.out.println("Ref (UNIFORM) branch: "+branch);
//			ivfss = cfss.getSolution(branch); 
//			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
//			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
//		}
		
		// write full branch averaged
//		cfss = FaultSystemSolutionFetcher.getSubset(cfss,
//				ScalingRelationships.SHAW_2009_MOD,
//				SlipAlongRuptureModels.TAPERED,
//				InversionModels.CHAR_CONSTRAINED,
//				TotalMag5Rate.RATE_7p9,
//				MaxMagOffFault.MAG_7p6,
//				MomentRateFixes.NONE,
//				SpatialSeisPDF.UCERF3
//				);
		System.out.println("Writing "+cfss.getBranches().size()+" branches");
		SolutionLogicTree rupSetTreeModule = new SolutionLogicTree.UCERF3(cfss);
		ModuleArchive<OpenSHA_Module> compoundArchive = new ModuleArchive<>();
		compoundArchive.addModule(rupSetTreeModule);
		compoundArchive.write(new File(outputDir, "full_modular_compound_sol.zip"));
	}
	
	private static GridSourceProvider exactGridProv(GridSourceProvider prov) {
		Preconditions.checkNotNull(prov);
		AbstractGridSourceProvider.Precomputed precomputed = new AbstractGridSourceProvider.Precomputed(prov);
		precomputed.setRound(false);
		return precomputed;
	}

}
