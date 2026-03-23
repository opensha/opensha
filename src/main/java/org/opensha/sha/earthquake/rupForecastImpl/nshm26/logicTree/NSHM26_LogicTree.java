package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.CorrTruncatedNormalDistribution;
import org.apache.commons.statistics.distribution.UniformContinuousDistribution;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.RandomSeedUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxRuptureLengthBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import gov.usgs.earthquake.nshmp.erf.logicTree.TectonicRegionBranchTreeNode;

public class NSHM26_LogicTree {
	
	/*
	 * Subduction interface branch levels (FSS and gridded)
	 * missing/TODO:
	 * 
	 * * option to use observed seismicity b-value?
	 * * and maybe obs seis rate as well?
	 */
	public static final LogicTreeLevel<NSHM26_InterfaceFaultModels> INTERFACE_FM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceFaultModels.class, "Interface Fault Model", "InterfaceFM");
	public static final LogicTreeLevel<NSHM26_InterfaceCouplingDepthModels> INTERFACE_DEPTH_COUPLING =
			LogicTreeLevel.forEnum(NSHM26_InterfaceCouplingDepthModels.class, "Interface Depth Coupling", "InterfaceDepthCoupling");
	public static final LogicTreeLevel<NSHM26_InterfaceDeformationModels> INTERFACE_DM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceDeformationModels.class, "Interface Deformation Model", "InterfaceDM");
	public static final LogicTreeLevel<PRVI25_SubductionScalingRelationships> INTERFACE_SCALE = 
			LogicTreeLevel.forEnum(PRVI25_SubductionScalingRelationships.class, "Interface Scaling Relationship", "InterfaceScale");
	public static final LogicTreeLevel<NSHM26_InterfaceObsSeisDMAdjustment> INTERFACE_OBS_SEIS_DM_ADJ =
			LogicTreeLevel.forEnum(NSHM26_InterfaceObsSeisDMAdjustment.class, "Interface Observed Seismicity Adjustment", "InterfaceObsSeisDMAdj");
	public static final LogicTreeLevel<NSHM26_InterfaceMinSubSects> INTERFACE_MIN_SUB_SECTS =
			LogicTreeLevel.forEnum(NSHM26_InterfaceMinSubSects.class, "Interface Minimum Subsection Count", "InterfaceMinSubSects");
	public static final NSHM26_SeisRateModelSamples GNMI_INTERFACE_RATE_SAMPLES =
			new NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions.GNMI, TectonicRegionType.SUBDUCTION_INTERFACE);
	public static final NSHM26_SeisRateModelSamples AMSAM_INTERFACE_RATE_SAMPLES =
			new NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions.AMSAM, TectonicRegionType.SUBDUCTION_INTERFACE);
	public static final double INTERFACE_B_SINGLE_DEFAULT = 1d;
	public static final ContinuousDistribution INTERFACE_B_DIST = UniformContinuousDistribution.of(0.5d, 1d); // TODO
	public static final double INTERFACE_MAX_LEN_SINGLE_DEFAULT = 1300;
	public static final ContinuousDistribution INTERFACE_MAX_LEN_DIST = UniformContinuousDistribution.of(1000d, 1500d); // TODO
	
	/*
	 * Subduction intraslab branch levels (gridded)
	 * missing/TODO:
	 */
	public static final double INTRASLAB_MMAX_OFF_SINGLE_DEFAULT = 8;
	public static final ContinuousDistribution INTRASLAB_MMAX_OFF_DIST = CorrTruncatedNormalDistribution.of(8, 0.2, 7.45, 8.55);
	
	/*
	 * Crustal branch levels (FSS & gridded)
	 * missing/TODO:
	 */
	public static final LogicTreeLevel<NSHM26_CrustalFaultModels> CRUSTAL_FM =
			LogicTreeLevel.forEnum(NSHM26_CrustalFaultModels.class, "Crustal Fault Model", "CrustalFM");
	public static final LogicTreeLevel<NSHM26_CrustalAggregatedDeformationModels> CRUSTAL_AGG_DM =
			LogicTreeLevel.forEnum(NSHM26_CrustalAggregatedDeformationModels.class, "Crustal Aggregated Deformation Model", "CrustalAggDM");
	public static final LogicTreeLevel<NSHM23_ScalingRelationships> CRUSTAL_SCALE = NSHM23_LogicTreeBranch.SCALE;
	public static final LogicTreeLevel<NSHM23_SegmentationModels> SEG = NSHM23_LogicTreeBranch.SEG;
	public static final double CRUSTAL_B_SINGLE_DEFAULT = 0.5d;
	public static final ContinuousDistribution CRUSTAL_B_DIST = UniformContinuousDistribution.of(0d, 1d);
	public static final double CRUSTAL_MMAX_OFF_SINGLE_DEFAULT = 7.6;
	public static final ContinuousDistribution CRUSTAL_MMAX_OFF_DIST = CorrTruncatedNormalDistribution.of(7.6, 0.134, 7.05, 8.05);
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> buildLevels(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean sampled) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		
		levels.add(new NSHM26_ModelRegimeNode.Level(seisReg, trt));
		
		String trtName = NSHM26_RegionLoader.getNameForTRT(trt);
		
		// inversion
		String supraBname = sampled ? trtName+" b-value Samples" : trtName+" Fixed b-value";
		String supraBshortName = sampled ? trtName+"-bSamples" : trtName+"-FixedB";
		if (trt == TectonicRegionType.SUBDUCTION_INTERFACE) {
			levels.add(INTERFACE_FM);
			levels.add(INTERFACE_DEPTH_COUPLING);
			levels.add(INTERFACE_DM);
			levels.add(INTERFACE_SCALE);
			if (sampled)
				levels.add(new SectionSupraSeisBValues.DistributionSamplingLevel(supraBname, supraBshortName, INTERFACE_B_DIST));
			else
				levels.add(new SectionSupraSeisBValues.FixedValueLevel(supraBname, supraBshortName, INTERFACE_B_SINGLE_DEFAULT));
			levels.add(INTERFACE_OBS_SEIS_DM_ADJ);
			levels.add(INTERFACE_MIN_SUB_SECTS);
			if (sampled)
				levels.add(new MaxRuptureLengthBranchNode.DistributionSamplingLevel(
						"Interface Maximum Rupture Length", "Interface Max. Len.", INTERFACE_MAX_LEN_DIST));
			else
				levels.add(new MaxRuptureLengthBranchNode.FixedValueLevel(
						"Interface Maximum Rupture Length", "Interface Max. Len.", INTERFACE_MAX_LEN_SINGLE_DEFAULT));
		} else if (trt == TectonicRegionType.ACTIVE_SHALLOW && seisReg == NSHM26_SeismicityRegions.GNMI) {
			// have crustal on-fault
			levels.add(CRUSTAL_FM);
			if (sampled)
				levels.add(new NSHM26_CrustalRandomlySampledDeformationModelLevel());
			else
				levels.add(CRUSTAL_AGG_DM);
			levels.add(CRUSTAL_SCALE);
			if (sampled)
				levels.add(new SectionSupraSeisBValues.DistributionSamplingLevel(supraBname, supraBshortName, CRUSTAL_B_DIST));
			else
				levels.add(new SectionSupraSeisBValues.FixedValueLevel(supraBname, supraBshortName, CRUSTAL_B_SINGLE_DEFAULT));
			levels.add(SEG);
		}
		
		// gridded
		if (sampled)
			levels.add(new NSHM26_SeisRateModelSamples(seisReg, trt));
		else
			levels.add(LogicTreeLevel.forEnum(NSHM26_SeisRateModelBranch.class,
					seisReg.getShortName()+" Rate Model Branch ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
					seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"Branch"));
		levels.add(LogicTreeLevel.forEnum(NSHM26_DeclusteringAlgorithms.class,
				seisReg.getShortName()+" Declustering Algorithm ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
				seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"-Decluster"));
		levels.add(LogicTreeLevel.forEnum(NSHM26_SeisSmoothingAlgorithms.class,
				seisReg.getShortName()+" Smoothing Kernel ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
				seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"-Smooth"));

		String mMaxName = sampled ? trtName+" Off Fault Mmax Samples" : trtName+" Fixed Off Fault Mmax";
		String mMaxShortName = sampled ? trtName+"-MmaxOffSamples" : trtName+"-FixedMmaxOff";
		if (trt == TectonicRegionType.ACTIVE_SHALLOW) {
			if (sampled)
				levels.add(new MaxMagOffFaultBranchNode.DistributionSamplingLevel(mMaxName, mMaxShortName, trt, CRUSTAL_MMAX_OFF_DIST));
			else
				levels.add(new MaxMagOffFaultBranchNode.FixedValueLevel(mMaxName, mMaxShortName, trt, CRUSTAL_MMAX_OFF_SINGLE_DEFAULT));
		} else if (trt == TectonicRegionType.SUBDUCTION_SLAB) {
			if (sampled)
				levels.add(new MaxMagOffFaultBranchNode.DistributionSamplingLevel(mMaxName, mMaxShortName, trt, INTRASLAB_MMAX_OFF_DIST));
			else
				levels.add(new MaxMagOffFaultBranchNode.FixedValueLevel(mMaxName, mMaxShortName, trt, INTRASLAB_MMAX_OFF_SINGLE_DEFAULT));
		}
		return levels;
	}
	
	public static LogicTreeBranch<LogicTreeNode> buildDefault(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean sampled) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = buildLevels(seisReg, trt, sampled);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		Preconditions.checkState(levels.get(0) instanceof NSHM26_ModelRegimeNode.Level);
		branch.setValue(0, levels.get(0).getNodes().get(0));
		
		// inversion
		if (trt == TectonicRegionType.SUBDUCTION_INTERFACE) {
			branch.setValue(NSHM26_InterfaceFaultModels.regionDefault(seisReg));
			branch.setValue(NSHM26_InterfaceCouplingDepthModels.DEEP_TAPER);
			branch.setValue(NSHM26_InterfaceDeformationModels.PREF_COUPLING);
			branch.setValue(PRVI25_SubductionScalingRelationships.LOGA_C4p0);
			branch.setValue(NSHM26_InterfaceObsSeisDMAdjustment.AVERAGE); // TODO
			branch.setValue(NSHM26_InterfaceMinSubSects.FOUR); // TODO
		} else if (trt == TectonicRegionType.ACTIVE_SHALLOW && seisReg == NSHM26_SeismicityRegions.GNMI) {
			branch.setValue(NSHM26_CrustalFaultModels.GNMI_V1);
			if (!sampled)
				branch.setValue(NSHM26_CrustalAggregatedDeformationModels.AVERAGE);
			branch.setValue(NSHM23_ScalingRelationships.LOGA_C4p2);
			branch.setValue(NSHM23_SegmentationModels.MID);
		}
		
		// gridded seismicity
		if (!sampled)
			branch.setValue(NSHM26_SeisRateModelBranch.PREFFERRED);
		branch.setValue(NSHM26_DeclusteringAlgorithms.AVERAGE);
		branch.setValue(NSHM26_SeisSmoothingAlgorithms.AVERAGE);
		
		// set any fixed values, e.g., b and mmax when not sampled
		for (int i=0; i<levels.size(); i++) {
			LogicTreeNode value = branch.getValue(i);
			if (value == null) {
				LogicTreeLevel<?> level = levels.get(i);
				if (!(level instanceof RandomLevel<?,?>))
					branch.setValue(level.getNodes().get(0));
			}
		}
		
		return branch;
	}
	
	public static LogicTree<LogicTreeNode> buildLogicTree(NSHM26_SeismicityRegions seisReg, TectonicRegionType trt,
			int numSamples, boolean deterministicSeed) {
		
		long seed;
		if (deterministicSeed) {
			List<Integer> seedComponents = new ArrayList<>();
			seedComponents.add(seisReg.name().hashCode());
			seedComponents.add(trt.name().hashCode());
			seedComponents.add(numSamples);
			seed = RandomSeedUtils.uniqueSeedCombination(RandomSeedUtils.getMixed64(seedComponents));
		} else {
			seed = new Random().nextLong();
		}
		return buildLogicTree(seisReg, trt, numSamples, seed);
	}
	
	private static List<LogicTreeNode> buildFixed(LogicTreeNode node, int numSamples) {
		List<LogicTreeNode> samples = new ArrayList<>();
		for (int i=0; i<numSamples; i++)
			samples.add(node);
		return samples;
	}
	
	public static LogicTree<LogicTreeNode> buildLogicTree(NSHM26_SeismicityRegions seisReg, TectonicRegionType trt,
			int numSamples, long seed) {
		Preconditions.checkState(numSamples > 0);
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = buildLevels(seisReg, trt, true);
		
		Random r = new Random(seed);
		List<List<? extends LogicTreeNode>> levelSamples = new ArrayList<>(levels.size());
		for (LogicTreeLevel<?> level : levels) {
			List<? extends LogicTreeNode> samples;
			if (level == INTERFACE_FM) {
				samples = buildFixed(NSHM26_InterfaceFaultModels.regionDefault(seisReg), numSamples);
			} else if (level instanceof RandomLevel<?,?>) {
				((RandomLevel<?,?>)level).build(r.nextLong(), numSamples);
				samples = ((RandomLevel<?,?>)level).getNodes();
				Preconditions.checkState(samples.size() == numSamples);
			} else {
				List<? extends LogicTreeNode> nodes = level.getNodes();
				List<LogicTreeNode> nonzeroWeightNodes = new ArrayList<>(nodes.size());
				List<Double> nonzeroWeights = new ArrayList<>(nodes.size());
				for (LogicTreeNode node : nodes) {
					// TODO: no support yet for branch-dependent-weighting
					double weight = node.getNodeWeight(null);
					if (weight > 0d) {
						nonzeroWeightNodes.add(node);
						nonzeroWeights.add(weight);
					}
				}
				Preconditions.checkState(!nonzeroWeightNodes.isEmpty());
				List<LogicTreeNode> mySamples = new ArrayList<>(numSamples);
				if (nonzeroWeightNodes.size() == 1) {
					for (int i=0; i<numSamples; i++)
						mySamples.add(nonzeroWeightNodes.get(0));
				} else {
					IntegerPDF_FunctionSampler sampler = new IntegerPDF_FunctionSampler(Doubles.toArray(nonzeroWeights));
					for (int i=0; i<numSamples; i++)
						mySamples.add(nonzeroWeightNodes.get(sampler.getRandomInt(r)));
				}
				samples = mySamples;
			}
			levelSamples.add(samples);
		}
//		String branchPrefix = seisReg.name()+"_"+NSHM26_RegionLoader.getNameForTRT(trt)+"Sample";
		double weightEach = 1d/(double)numSamples;
		List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(numSamples);
		for (int i=0; i<numSamples; i++) {
			List<LogicTreeNode> values = new ArrayList<>(levels.size());
			for (int l=0; l<levels.size(); l++)
				values.add(levelSamples.get(l).get(i));
			LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);
			branch.setOrigBranchWeight(weightEach);
//			branch.setCustomFileName(branchPrefix+i);
			branches.add(branch);
		}
		
		return LogicTree.fromExisting(levels, branches);
	}
	
	public static LogicTree<LogicTreeNode> buildMultiRegimeTree(NSHM26_SeismicityRegions seisReg,
			int numSamples, boolean deterministicSeed) {
		long seed;
		if (deterministicSeed) {
			List<Integer> seedComponents = new ArrayList<>();
			seedComponents.add(seisReg.name().hashCode());
			seedComponents.add(numSamples);
			seed = RandomSeedUtils.uniqueSeedCombination(RandomSeedUtils.getMixed64(seedComponents));
		} else {
			seed = new Random().nextLong();
		}
		return buildMultiRegimeTree(seisReg, numSamples, seed);
	}
	
	public static LogicTree<LogicTreeNode> buildMultiRegimeTree(NSHM26_SeismicityRegions seisReg,
			int numSamples, long seed) {
		Random rand = new Random(seed);
		
		LogicTree<LogicTreeNode> interfaceTree = buildLogicTree(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, numSamples, rand.nextLong());
		LogicTree<LogicTreeNode> intraslabTree = buildLogicTree(seisReg, TectonicRegionType.SUBDUCTION_SLAB, numSamples, rand.nextLong());
		LogicTree<LogicTreeNode> crustalTree = buildLogicTree(seisReg, TectonicRegionType.ACTIVE_SHALLOW, numSamples, rand.nextLong());
		
		return buildMultiRegimeTree(seisReg, interfaceTree, intraslabTree, crustalTree);
	}
	
	public static LogicTree<LogicTreeNode> buildMultiRegimeTree(NSHM26_SeismicityRegions seisReg,
			LogicTree<LogicTreeNode> interfaceTree, LogicTree<LogicTreeNode> intraslabTree, LogicTree<LogicTreeNode> crustalTree) {
		Preconditions.checkState(interfaceTree.size() == intraslabTree.size());
		Preconditions.checkState(interfaceTree.size() == crustalTree.size());
		int numEach = interfaceTree.size();
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>(3);
		levels.add(new TectonicRegionBranchTreeNode.Level(TectonicRegionType.SUBDUCTION_INTERFACE, interfaceTree,
				seisReg.getName()+" Interface Branches", seisReg.name()+"-Interface",
				seisReg.name()+"-Interface Branch", "InterfaceBranch", "InterfaceBranch"));
		levels.add(new TectonicRegionBranchTreeNode.Level(TectonicRegionType.SUBDUCTION_SLAB, intraslabTree,
				seisReg.getName()+" Intraslab Branches", seisReg.name()+"-Intraslab",
				seisReg.name()+"-Intraslab Branch", "IntraslabBranch", "IntraslabBranch"));
		levels.add(new TectonicRegionBranchTreeNode.Level(TectonicRegionType.ACTIVE_SHALLOW, crustalTree,
				seisReg.getName()+" Crustal Branches", seisReg.name()+"-Crustal",
				seisReg.name()+"-Crustal Branch", "CrustalBranch", "CrustalBranch"));
		List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(numEach);
		for (int i=0; i<numEach; i++) {
			List<LogicTreeNode> values = new ArrayList<>(3);
			double weight0 = -1d;
			for (LogicTreeLevel<? extends LogicTreeNode> level : levels) {
				LogicTreeNode value = level.getNodes().get(i);
				if (weight0 == -1)
					weight0 = value.getNodeWeight(null);
				else
					Preconditions.checkState(weight0 == value.getNodeWeight(null));
				values.add(value);
			}
			LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);
			branches.add(branch);
		}
		return LogicTree.fromExisting(levels, branches);
	}
	
	public static void main(String[] args) throws IOException {
		TectonicRegionType[] trts = {TectonicRegionType.SUBDUCTION_INTERFACE, TectonicRegionType.SUBDUCTION_SLAB, TectonicRegionType.ACTIVE_SHALLOW};
		for (NSHM26_SeismicityRegions seisReg : NSHM26_SeismicityRegions.values()) {
			List<LogicTree<LogicTreeNode>> trees = new ArrayList<>(3);
			for (TectonicRegionType trt : trts) {
				System.out.println("Building for "+seisReg+", "+trt);
				System.out.println("\tRegular:\t"+buildDefault(seisReg, trt, false));
				System.out.println("\tSampled:\t"+buildDefault(seisReg, trt, true));
				System.out.println("\tBuilding sampled tree");
				LogicTree<LogicTreeNode> tree = buildLogicTree(seisReg, trt, 100, true);
				trees.add(tree);
				File treeFile = new File("/tmp/nshm26_tree_test_"+seisReg.name()+"_"+trt.name()+".json");
				tree.write(treeFile);
				LogicTree<LogicTreeNode> tree2 = LogicTree.read(treeFile);
				tree2.write(new File(treeFile.getParentFile(), treeFile.getName()+".rerpo"));
				LogicTreeBranch<LogicTreeNode> branch = tree2.getBranch(0);
				System.out.println("\tOrig branch origWeight="+branch.getOrigBranchWeight());
				File branchFile = new File("/tmp/nshm26_tree_test_"+seisReg.name()+"_"+trt.name()+"_branch0.json");
				branch.writeToFile(branchFile);
				LogicTreeBranch<LogicTreeNode> branch2 = LogicTreeBranch.read(branchFile);
				System.out.println("\tLoaded branch origWeight="+branch2.getOrigBranchWeight());
				branch2.writeToFile(new File(branchFile.getParentFile(), branchFile.getName()+".rerpo"));
			}
			System.out.println("\tBuilding multi-regime");
			LogicTree<LogicTreeNode> multiTree = buildMultiRegimeTree(seisReg, trees.get(0), trees.get(1), trees.get(2));
			for (LogicTreeLevel<? extends LogicTreeNode> level : multiTree.getLevels()) {
				System.out.println("\tMulti-regime level "+level.getName());
				System.out.println("\t\tAffected: "+level.getAffected());
				System.out.println("\t\tNotAffected: "+level.getNotAffected());
			}
			File treeFile = new File("/tmp/nshm26_tree_test_"+seisReg.name()+"_multi.json");
			multiTree.write(treeFile);
			LogicTree<LogicTreeNode> tree2 = LogicTree.read(treeFile);
			tree2.write(new File(treeFile.getParentFile(), treeFile.getName()+".rerpo"));
		}
	}

}
