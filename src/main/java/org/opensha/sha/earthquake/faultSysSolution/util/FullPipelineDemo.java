package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

class FullPipelineDemo {

	public static void main(String[] args) throws IOException {
		File outputDir = new File("/tmp/full_pipeline_demo");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		int threads = 32;
		
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		
		FaultModels fm = branch.getValue(FaultModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		
		String newName = "Coulomb Rupture Set";
		RupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm, scale);
//		String newName = "U3 Reproduction";
//		RupSetConfig rsConfig = new RuptureSets.U3RupSetConfig(fm , scale);
		FaultSystemSolution compSol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip"));
		FaultSystemRupSet rupSet = rsConfig.build(threads);
		
		// configure as UCERF3
		rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(branch).build();
		
		// write it out
		rupSet.write(new File(outputDir, "rupture_set.zip"));
		
		// write a full rupture set report in the background
		ReportMetadata rupMeta = new ReportMetadata(new RupSetMetadata(newName, rupSet), new RupSetMetadata("UCERF3", compSol));
		ReportPageGen rupSetReport = new ReportPageGen(rupMeta, new File(outputDir, "rup_set_report"),
				ReportPageGen.getDefaultRupSetPlots(PlotLevel.FULL));
		
		Thread reportThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					rupSetReport.generatePage();
					System.out.println("Done with rupture set report");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		reportThread.start();
		
		// now build inputs
		InversionTargetMFDs targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
		UCERF3InversionConfiguration config = UCERF3InversionConfiguration.forModel(
				branch.getValue(InversionModels.class), rupSet, fm, targetMFDs);
		
		// get the paleo rate constraints
		List<PaleoRateConstraint> paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
				fm, rupSet);

		// get the improbability constraints
		double[] improbabilityConstraint = null; // null for now

		// paleo probability model
		PaleoProbabilityModel paleoProbabilityModel = UCERF3InversionInputGenerator.loadDefaultPaleoProbabilityModel();

		List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(rupSet.getFaultSectionDataList());

		UCERF3InversionInputGenerator inputGen = new UCERF3InversionInputGenerator(
				rupSet, config, paleoRateConstraints, aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);
		
		System.out.println("Generating inputs");
		inputGen.generateInputs();
		
		ProgressTrackingCompletionCriteria completion = new ProgressTrackingCompletionCriteria(
				TimeCompletionCriteria.getInMinutes(10));
		TimeCompletionCriteria subCompletion = TimeCompletionCriteria.getInSeconds(5);
		
		ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
				inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq(), threads, subCompletion);
		completion.setConstraintRanges(inputGen.getConstraintRowRanges());
		tsa.setConstraintRanges(inputGen.getConstraintRowRanges());
		tsa.iterate(completion);
		
		double[] rawSol = tsa.getBestSolution();
		double[] rates = inputGen.adjustSolutionForWaterLevel(rawSol);
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		// add sub-seismo MFDs
		sol.addModule(targetMFDs.getOnFaultSubSeisMFDs());
		// add grid source provider
		sol.setGridSourceProvider(new UCERF3_GridSourceGenerator(sol, branch.getValue(SpatialSeisPDF.class),
				branch.getValue(MomentRateFixes.class), targetMFDs,
				sol.requireModule(SubSeismoOnFaultMFDs.class),
				branch.getValue(MaxMagOffFault.class).getMaxMagOffFault(),
				rupSet.requireModule(FaultGridAssociations.class)));
		// add inversion progress
		sol.addModule(completion.getProgress());
		
		// write solution
		sol.write(new File(outputDir, "solution.zip"));
		
		try {
			reportThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// write solution report
		ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(newName, sol), new RupSetMetadata("UCERF3", compSol));
		ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputDir, "sol_report"),
				ReportPageGen.getDefaultSolutionPlots(PlotLevel.FULL));
		solReport.generatePage();
		System.out.println("DONE");
		System.exit(0);
	}
	
	
}