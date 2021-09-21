package scratch.kevin.ucerf3.inversion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class ManualPreInversionAnalysisRun {

	public static void main(String[] args) throws IOException {
		List<U3LogicTreeBranch> branches = new ArrayList<>();
		
		branches.add(U3LogicTreeBranch.DEFAULT);
		branches.add(U3LogicTreeBranch.fromValues(SpatialSeisPDF.UNSMOOTHED_GRIDDED));
		
		branches.add(U3LogicTreeBranch.fromValues(ScalingRelationships.HANKS_BAKUN_08));
		branches.add(U3LogicTreeBranch.fromValues(ScalingRelationships.HANKS_BAKUN_08, SpatialSeisPDF.UNSMOOTHED_GRIDDED));
		
		branches.add(U3LogicTreeBranch.fromValues(ScalingRelationships.HANKS_BAKUN_08, TotalMag5Rate.RATE_9p6));
		branches.add(U3LogicTreeBranch.fromValues(ScalingRelationships.HANKS_BAKUN_08, TotalMag5Rate.RATE_9p6, SpatialSeisPDF.UNSMOOTHED_GRIDDED));
		
		int origBranches = branches.size();
		for (int i=0; i<origBranches; i++) {
			U3LogicTreeBranch branch = (U3LogicTreeBranch) branches.get(i).clone();
			branch.setValue(InversionModels.GR_CONSTRAINED);
			branches.add(branch);
		}
		
		StringBuilder str = new StringBuilder();
		
		for (U3LogicTreeBranch branch : branches) {
			System.out.println("Processing "+branch.buildFileName());
			InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(branch);
			
			str.append(rupSet.getPreInversionAnalysisData(str.length() == 0)).append("\n");
		}
		
		FileWriter fw = new FileWriter(new File("/tmp/morgan_pre_inversion_analysis.csv"));
		
		fw.write(str.toString().replaceAll("\t", ","));
		
		fw.close();
	}

}
