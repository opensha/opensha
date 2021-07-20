package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

class UCERF3FileConverter {

	public static void main(String[] args) throws ZipException, IOException {
		File inputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/orig");
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular");
		File comoundFile = new File(inputDir, "full_ucerf3_compound_sol.zip");
		
		CompoundFaultSystemSolution cfss = CompoundFaultSystemSolution.fromZipFile(comoundFile);
		
		FaultModels[] fms = {FaultModels.FM3_1, FaultModels.FM3_2};
		
		for (FaultModels fm : fms) {
			U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(true, fm);
			System.out.println("Ref branch: "+branch);
			InversionFaultSystemSolution ivfss = cfss.getSolution(branch); 
			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
		}
	}
	
	private static GridSourceProvider exactGridProv(GridSourceProvider prov) {
		Preconditions.checkNotNull(prov);
		AbstractGridSourceProvider.Precomputed precomputed = new AbstractGridSourceProvider.Precomputed(prov);
		precomputed.setRound(false);
		return precomputed;
	}

}
