package scratch.UCERF3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.BranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.FaultSystemIO;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

@Deprecated
public class BranchAveragedFSSBuilder {
	
	@Deprecated
	public static InversionFaultSystemSolution build(FaultSystemSolutionFetcher fetch,
			BranchWeightProvider weightProvider, FaultModels fm, List<String> branchNames) {
		return build(fetch, LaughTestFilter.getDefault(), weightProvider, fm, branchNames);
	}
	
	@Deprecated
	public static InversionFaultSystemSolution build(FaultSystemSolutionFetcher fetch, LaughTestFilter laughTest,
			BranchWeightProvider weightProvider, FaultModels fm, List<String> branchNames) {
		
		Preconditions.checkState(weightProvider instanceof APrioriBranchWeightProvider, "Currenlty only a priori branch weights" +
				" supported as branch averaged DM, Dr, Dsr in rup set will not be consistent otherwise.");
		
		// rupIndex, solIndex
		List<List<Double>> ratesList = Lists.newArrayList();
		List<List<Double>> magsList = Lists.newArrayList();
		
		
		double weightSum = 0d;
		List<Double> weightsList = Lists.newArrayList();
		List<LogicTreeBranch> branches = Lists.newArrayList();
		
		branchLoop:
		for (LogicTreeBranch branch : fetch.getBranches()) {
			if (branch.getValue(FaultModels.class) != fm)
				continue;
			
			if (branchNames != null) {
				for (String branchName : branchNames) {
					boolean found = false;
					for (LogicTreeBranchNode<?> node : branch) {
						if (node.name().equals(branchName)) {
							found = true;
							break;
						}
					}
					if (!found)
						continue branchLoop;
				}
			}
			
			double weight = weightProvider.getWeight(branch);
			if (weight == 0)
				continue;
			weightsList.add(weight);
			branches.add(branch);
			weightSum += weight;
		}
		
		double[] rates = null;
		double[] mags = null;
		
		System.out.println(branches.size()+" match criteria");
		
		for (int i=0; i<branches.size(); i++) {
			LogicTreeBranch branch = branches.get(i);
			if ((i+1) % 10 == 0) {
				System.out.println("Loading solution "+(i+1));
				System.gc();
			}
			double[] subRates = fetch.getRates(branch);
			double[] subMags = fetch.getMags(branch);
			
			if (rates == null) {
				rates = new double[subRates.length];
				mags = new double[subRates.length];
			} else {
				Preconditions.checkState(rates.length == subRates.length,
						"Rupture count discrepancy between branches!");
			}
			
			double scaledWeight = weightsList.get(i)/weightSum;
			
			for (int r=0; r<rates.length; r++) {
				rates[r] += subRates[r]*scaledWeight;
				mags[r] += subMags[r]*scaledWeight;
			}
		}
		
		System.out.println("Creating Branch Averaged FSS for "+weightsList.size()+" solutions!");
		
//		FaultSystemRupSet reference = InversionFaultSystemRupSetFactory.forBranch(
//				fm, DeformationModels.GEOLOGIC);
		
		InversionFaultSystemRupSet reference = InversionFaultSystemRupSetFactory.forBranch(laughTest,
				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE, LogicTreeBranch.getMEAN_UCERF3(fm));
		
		Preconditions.checkState(reference.getNumRuptures() == rates.length,
				"Rupture count for Branch Averaged reference different than from FSS fetcher! ("
						+reference.getNumRuptures()+" != "+rates.length);
		
//		double[] refMags = reference.getMagForAllRups();
//		for (int r=0; r<mags.length; r++) {
//			double pdiff = DataUtils.getPercentDiff(refMags[r], mags[r]);
//			Preconditions.checkState(pdiff < 1, mags[r]+"\t!=\t"+refMags[r]);
//		}
		
		String info = reference.getInfoString();
		
		info = "****** BRANCH AVERAGED SOLUTION! ONLY MAGS/RATES VALID! ******\n\n"+info;
		
		List<List<Integer>> clusterRups = Lists.newArrayList();
		List<List<Integer>> clusterSects = Lists.newArrayList();
		for (int i=0; i<reference.getNumClusters(); i++) {
			clusterRups.add(reference.getRupturesForCluster(i));
			clusterSects.add(reference.getSectionsForCluster(i));
		}
		
		// first build the rup set
		InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(
				reference, reference.getLogicTreeBranch(), laughTest,
				reference.getAveRakeForAllRups(), reference.getCloseSectionsListList(),
				reference.getRupturesForClusters(), reference.getSectionsForClusters());
		rupSet.setMagForallRups(mags);
		
		InversionFaultSystemSolution sol = new InversionFaultSystemSolution(rupSet, rates);
		
		return sol;
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static void main(String[] args) throws ZipException, IOException {
		File file, outputFile;
		List<String> branchNames = null;
		if (args.length >= 2) {
			file = new File(args[0]);
			outputFile = new File(args[1]);
			if (args.length > 2) {
				branchNames = Lists.newArrayList();
				for (int i=2; i<args.length; i++)
					branchNames.add(args[i]);
			}
		} else {
			file = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/" +
					"2013_01_14-stampede_3p2_production_runs_combined_COMPOUND_SOL.zip");
			outputFile = new File("/tmp/2013_01_14-stampede_3p2_production_runs_combined_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
		}
		System.out.println("Loading: "+file.getAbsolutePath());
		System.out.println("Will save to: "+outputFile.getAbsolutePath());
		FaultSystemSolutionFetcher fetcher = CompoundFaultSystemSolution.fromZipFile(file);
//		FaultSystemSolutionFetcher fetcher = new Compou(new ZipFile(file));
		BranchWeightProvider weightProvider = new APrioriBranchWeightProvider();
		LaughTestFilter laughTest = LaughTestFilter.getUCERF3p2Filter(); // TODO will need to switch eventually
		FaultModels fm = null;
		if (branchNames != null) {
			for (String branchName : branchNames) {
				for (FaultModels testFM : FaultModels.values()) {
					if (testFM.name().equals(branchName) || testFM.getShortName().equals(branchName))
						fm = testFM;
				}
			}
		}
		if (fm == null)
			fm = FaultModels.FM3_1;
		
		InversionFaultSystemSolution sol = build(fetcher, laughTest, weightProvider, fm, branchNames);
		
		FaultSystemIO.writeSol(sol, outputFile);
	}

}
