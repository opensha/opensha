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
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
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
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

class UCERF3FileConverter {

	public static void main(String[] args) throws ZipException, IOException {
		File inputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/orig");
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular");
		File comoundFile = new File(inputDir, "full_ucerf3_compound_sol.zip");
		
		FaultSystemSolutionFetcher cfss = CompoundFaultSystemSolution.fromZipFile(comoundFile);
		
		FaultModels[] fms = {FaultModels.FM3_1, FaultModels.FM3_2};
		
		System.out.println("Writing reference branches...");
		for (FaultModels fm : fms) {
			U3LogicTreeBranch branch = U3LogicTreeBranch.fromValues(true, fm);
			System.out.println("Ref branch: "+branch);
			InversionFaultSystemSolution ivfss = cfss.getSolution(branch); 
			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
			branch.setValue(SlipAlongRuptureModels.UNIFORM);
			System.out.println("Ref (UNIFORM) branch: "+branch);
			ivfss = cfss.getSolution(branch); 
			ivfss.setGridSourceProvider(exactGridProv(ivfss.getGridSourceProvider()));
			ivfss.write(new File(outputDir, branch.buildFileName()+".zip"));
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
		ModuleArchive<OpenSHA_Module> compoundArchive = new ModuleArchive<>();
		compoundArchive.addModule(solTreeModule);
		solTreeModule.setSerializeGridded(false);
		compoundArchive.write(new File(outputDir, "full_logic_tree.zip"));
		solTreeModule.setSerializeGridded(true);
		compoundArchive.write(new File(outputDir, "full_logic_tree_with_gridded.zip"));
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
			FaultSystemSolutionFetcher fmFetcher = FaultSystemSolutionFetcher.getSubset(cfss, fm);
			
			FaultSystemSolution sol = calcBranchAveraged(fmFetcher);
			
			String prefix = fm.encodeChoiceString()+"_branch_averaged";
			sol.write(new File(outputDir, prefix+".zip"));
			
			SolutionLogicTree subTreeModule = new SolutionLogicTree.UCERF3(fmFetcher);
			subTreeModule.setSerializeGridded(false);
			sol.addModule(subTreeModule);
			sol.write(new File(outputDir, prefix+"_with_logic_tree.zip"));
			
			for (SpatialSeisPDF spatSeis : new SpatialSeisPDF[] {SpatialSeisPDF.UCERF2, SpatialSeisPDF.UCERF3}) {
				FaultSystemSolutionFetcher ssFetcher = FaultSystemSolutionFetcher.getSubset(fmFetcher, spatSeis);
				
				sol = calcBranchAveraged(ssFetcher);
				
				prefix = fm.encodeChoiceString()+"_"+spatSeis.encodeChoiceString()+"_branch_averaged";
				sol.write(new File(outputDir, prefix+".zip"));
			}
		}
	}

	public static FaultSystemSolution calcBranchAveraged(FaultSystemSolutionFetcher fetcher) {
		double totWeight = 0d; 
		double[] avgRates = null;
		double[] avgMags = null;
		GridSourceProvider refGridProv = null;
		GriddedRegion gridReg = null;
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = null;
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = null;
		List<IncrementalMagFreqDist> sectSubSeisMFDs = null;
		List<List<Integer>> sectIndices = null;
		List<DiscretizedFunc> rupMFDs = null;
		
		List<U3LogicTreeBranch> branches = new ArrayList<>(fetcher.getBranches());
		
		U3LogicTreeBranch combBranch = null;
		
		for (U3LogicTreeBranch branch : branches) {
			double weight = branch.getBranchWeight();
			totWeight += weight;
			
			FaultSystemSolution sol = fetcher.getSolution(branch);
			FaultSystemRupSet rupSet = sol.getRupSet();
			GridSourceProvider gridProv = sol.getGridSourceProvider();
			SubSeismoOnFaultMFDs ssMFDs = sol.requireModule(SubSeismoOnFaultMFDs.class);
			
			if (avgRates == null) {
				// first time
				avgRates = new double[rupSet.getNumRuptures()];
				avgMags = new double[avgRates.length];
				nodeSubSeisMFDs = new HashMap<>();
				nodeUnassociatedMFDs = new HashMap<>();
				sectSubSeisMFDs = new ArrayList<>();
				for (int s=0; s<rupSet.getNumSections(); s++)
					sectSubSeisMFDs.add(null);
				refGridProv = gridProv;
				gridReg = gridProv.getGriddedRegion();
				combBranch = branch.copy();
				sectIndices = rupSet.getSectionIndicesForAllRups();
				rupMFDs = new ArrayList<>();
				for (int r=0; r<avgRates.length; r++)
					rupMFDs.add(new ArbitrarilyDiscretizedFunc());
			}
			
			for (int i=0; i<combBranch.size(); i++)
				if (!combBranch.hasValue(branch.getValue(i)))
					combBranch.clearValue(i);
			
			addWeighted(avgRates, sol.getRateForAllRups(), weight);
			for (int r=0; r<avgRates.length; r++) {
				double rate = sol.getRateForRup(r);
				double mag = rupSet.getMagForRup(r);
				DiscretizedFunc rupMFD = rupMFDs.get(r);
				double y = rate*weight;
				if (rupMFD.hasX(mag))
					y += rupMFD.getY(mag);
				rupMFD.set(mag, y);
			}
			addWeighted(avgMags, rupSet.getMagForAllRups(), weight);
			for (int i=0; i<gridReg.getNodeCount(); i++) {
				addWeighted(nodeSubSeisMFDs, i, gridProv.getNodeSubSeisMFD(i), weight);
				addWeighted(nodeUnassociatedMFDs, i, gridProv.getNodeUnassociatedMFD(i), weight);
			}
			
			for (int s=0; s<rupSet.getNumSections(); s++) {
				IncrementalMagFreqDist subSeisMFD = ssMFDs.get(s);
				Preconditions.checkNotNull(subSeisMFD);
				IncrementalMagFreqDist avgMFD = sectSubSeisMFDs.get(s);
				if (avgMFD == null) {
					avgMFD = new IncrementalMagFreqDist(subSeisMFD.getMinX(), subSeisMFD.getMaxX(), subSeisMFD.size());
					sectSubSeisMFDs.set(s, avgMFD);
				}
				addWeighted(avgMFD, subSeisMFD, weight);
			}
		}
		
		System.out.println("Common branches: "+combBranch);
		if (!combBranch.hasValue(DeformationModels.class))
			combBranch.setValue(DeformationModels.MEAN_UCERF3);
		if (!combBranch.hasValue(ScalingRelationships.class))
			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
		if (!combBranch.hasValue(SlipAlongRuptureModels.class))
			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
		
		// now scale by total weight
		System.out.println("Normalizing by total weight");
		for (int r=0; r<avgRates.length; r++) {
			avgRates[r] /= totWeight;
			avgMags[r] /= totWeight;
			DiscretizedFunc rupMFD = rupMFDs.get(r);
			rupMFD.scale(1d/totWeight);
			Preconditions.checkState((float)rupMFD.calcSumOfY_Vals() == (float)avgRates[r]);
		}
		double[] fractSS = new double[refGridProv.size()];
		double[] fractR = new double[fractSS.length];
		double[] fractN = new double[fractSS.length];
		for (int i=0; i<fractSS.length; i++) {
			IncrementalMagFreqDist subSeisMFD = nodeSubSeisMFDs.get(i);
			if (subSeisMFD != null)
				subSeisMFD.scale(1d/totWeight);
			IncrementalMagFreqDist nodeUnassociatedMFD = nodeUnassociatedMFDs.get(i);
			if (nodeUnassociatedMFD != null)
				nodeUnassociatedMFD.scale(1d/totWeight);
			fractSS[i] = refGridProv.getFracStrikeSlip(i);
			fractR[i] = refGridProv.getFracReverse(i);
			fractN[i] = refGridProv.getFracNormal(i);
		}
		for (int s=0; s<sectSubSeisMFDs.size(); s++)
			sectSubSeisMFDs.get(s).scale(1d/totWeight);
		
		GridSourceProvider combGridProv = new Precomputed(refGridProv.getGriddedRegion(),
				nodeSubSeisMFDs, nodeUnassociatedMFDs, fractSS, fractN, fractR);
		
		List<? extends FaultSection> subSects = new DeformationModelFetcher(combBranch.getValue(FaultModels.class),
				combBranch.getValue(DeformationModels.class), UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,
				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
		
		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).forU3Branch(combBranch).rupMags(avgMags).build();
		// remove these as they're not correct for branch-averaged
		avgRupSet.removeModuleInstances(InversionTargetMFDs.class);
		avgRupSet.removeModuleInstances(SectSlipRates.class);
		
		FaultSystemSolution sol = new FaultSystemSolution(avgRupSet, avgRates);
		sol.addModule(combBranch);
		sol.setGridSourceProvider(combGridProv);
		sol.addModule(new SubSeismoOnFaultMFDs(sectSubSeisMFDs));
		sol.addModule(new RupMFDsModule(sol, rupMFDs.toArray(new DiscretizedFunc[0])));
		return sol;
	}
	
	public static void addWeighted(Map<Integer, IncrementalMagFreqDist> mfdMap, int index,
			IncrementalMagFreqDist newMFD, double weight) {
		if (newMFD == null)
			// simple case
			return;
		IncrementalMagFreqDist runningMFD = mfdMap.get(index);
		if (runningMFD == null) {
			runningMFD = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
			mfdMap.put(index, runningMFD);
		}
		addWeighted(runningMFD, newMFD, weight);
	}
	
	public static void addWeighted(IncrementalMagFreqDist runningMFD,
			IncrementalMagFreqDist newMFD, double weight) {
		Preconditions.checkState(runningMFD.size() == newMFD.size(), "MFD sizes inconsistent");
		Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
		Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
		for (int i=0; i<runningMFD.size(); i++)
			runningMFD.add(i, newMFD.getY(i)*weight);
	}
	
	private static void addWeighted(double[] running, double[] vals, double weight) {
		Preconditions.checkState(running.length == vals.length);
		for (int i=0; i<running.length; i++)
			running[i] += vals[i]*weight;
	}
	
	private static GridSourceProvider exactGridProv(GridSourceProvider prov) {
		Preconditions.checkNotNull(prov);
		AbstractGridSourceProvider.Precomputed precomputed = new AbstractGridSourceProvider.Precomputed(prov);
		precomputed.setRound(false);
		return precomputed;
	}

}
