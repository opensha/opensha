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
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class NSHM26_LogicTree {
	
	/*
	 * Subduction interface branch levels (FSS and gridded)
	 * missing/TODO:
	 * 
	 * * sampled b values?
	 * * option to use observed seismicity b-value?
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
	
	/*
	 * Subduction intraslab branch levels (gridded)
	 * missing/TODO:
	 */
	public static final double INTRASLAB_MMAX_OFF_SINGLE_DEFAULT = 8;
	public static final ContinuousDistribution INTRASLAB_MMAX_OFF_DIST = CorrTruncatedNormalDistribution.of(8, 0.2, 7.4, 8.6);
	
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
	public static final ContinuousDistribution CRUSTAL_MMAX_OFF_DIST = CorrTruncatedNormalDistribution.of(7.6, 0.134, 7, 8);
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> buildLevels(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean sampled) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		
		// inversion
		if (trt == TectonicRegionType.SUBDUCTION_INTERFACE) {
			levels.add(INTERFACE_FM);
			levels.add(INTERFACE_DEPTH_COUPLING);
			levels.add(INTERFACE_DM);
			levels.add(INTERFACE_SCALE);
			if (sampled)
				levels.add(new NSHM26_SupraSeisBValues.DistributionSamplingLevel(trt, INTERFACE_B_DIST));
			else
				levels.add(new NSHM26_SupraSeisBValues.FixedValueLevel(trt, INTERFACE_B_SINGLE_DEFAULT));
			levels.add(INTERFACE_OBS_SEIS_DM_ADJ);
			levels.add(INTERFACE_MIN_SUB_SECTS);
		} else if (trt == TectonicRegionType.ACTIVE_SHALLOW && seisReg == NSHM26_SeismicityRegions.GNMI) {
			// have crustal on-fault
			levels.add(CRUSTAL_FM);
			if (sampled)
				levels.add(new NSHM26_CrustalRandomlySampledDeformationModelLevel());
			else
				levels.add(CRUSTAL_AGG_DM);
			levels.add(CRUSTAL_SCALE);
			if (sampled)
				levels.add(new NSHM26_SupraSeisBValues.DistributionSamplingLevel(trt, CRUSTAL_B_DIST));
			else
				levels.add(new NSHM26_SupraSeisBValues.FixedValueLevel(trt, CRUSTAL_B_SINGLE_DEFAULT));
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
		
		if (trt == TectonicRegionType.ACTIVE_SHALLOW) {
			if (sampled)
				levels.add(new NSHM26_OffFaultMmax.DistributionSamplingLevel(trt, CRUSTAL_MMAX_OFF_DIST));
			else
				levels.add(new NSHM26_OffFaultMmax.FixedValueLevel(trt, CRUSTAL_MMAX_OFF_SINGLE_DEFAULT));
		} else if (trt == TectonicRegionType.SUBDUCTION_SLAB) {
			if (sampled)
				levels.add(new NSHM26_OffFaultMmax.DistributionSamplingLevel(trt, INTRASLAB_MMAX_OFF_DIST));
			else
				levels.add(new NSHM26_OffFaultMmax.FixedValueLevel(trt, INTRASLAB_MMAX_OFF_SINGLE_DEFAULT));
		}
		return levels;
	}
	
	public static LogicTreeBranch<LogicTreeNode> buildDefault(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean sampled) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = buildLevels(seisReg, trt, sampled);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		// inversion
		if (trt == TectonicRegionType.SUBDUCTION_INTERFACE) {
			branch.setValue(NSHM26_InterfaceFaultModels.regionDefault(seisReg));
			branch.setValue(NSHM26_InterfaceCouplingDepthModels.DOUBLE_TAPER);
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
			branch.setValue(NSHM26_SeisRateModelBranch.AVERAGE);
		branch.setValue(NSHM26_DeclusteringAlgorithms.AVERAGE);
		branch.setValue(NSHM26_SeisSmoothingAlgorithms.AVERAGE);
		
		// set any fixed values, e.g., b and mmax when not sampled
		for (int i=0; i<levels.size(); i++) {
			LogicTreeNode value = branch.getValue(i);
			if (value == null) {
				LogicTreeLevel<?> level = levels.get(i);
				if (!(level instanceof RandomLevel<?>))
					branch.setValue(level.getNodes().get(0));
			}
		}
		
		return branch;
	}
	
	public static LogicTree<LogicTreeNode> buildLogicTree(NSHM26_SeismicityRegions seisReg, TectonicRegionType trt,
			int numSamples, boolean deterministicSeed, LogicTreeNode... fixed) {
		
		long seed;
		if (deterministicSeed) {
			List<Integer> seedComponents = new ArrayList<>();
			seedComponents.add(seisReg.name().hashCode());
			seedComponents.add(trt.name().hashCode());
			seedComponents.add(numSamples);
			if (fixed != null)
				for (LogicTreeNode node : fixed)
					seedComponents.add(node.getName().hashCode());
			seed = RandomSeedUtils.uniqueSeedCombination(RandomSeedUtils.getMixed64(seedComponents));
		} else {
			seed = new Random().nextLong();
		}
		return buildLogicTree(seisReg, trt, numSamples, seed, fixed);
	}
	
	public static LogicTree<LogicTreeNode> buildLogicTree(NSHM26_SeismicityRegions seisReg, TectonicRegionType trt,
			int numSamples, long seed, LogicTreeNode... fixed) {
		Preconditions.checkState(numSamples > 0);
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = buildLevels(seisReg, trt, true);
		
		Random r = new Random(seed);
		List<List<? extends LogicTreeNode>> levelSamples = new ArrayList<>(levels.size());
		for (LogicTreeLevel<?> level : levels) {
			List<? extends LogicTreeNode> samples;
			if (level instanceof RandomLevel<?>) {
				((RandomLevel<?>)level).build(r.nextLong(), numSamples);
				samples = ((RandomLevel<?>)level).getNodes();
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
		String branchPrefix = seisReg.name()+"_"+NSHM26_RegionLoader.getNameForTRT(trt)+"Sample";
		double weightEach = 1d/(double)numSamples;
		List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(numSamples);
		for (int i=0; i<numSamples; i++) {
			List<LogicTreeNode> values = new ArrayList<>(levels.size());
			for (int l=0; l<levels.size(); l++)
				values.add(levelSamples.get(l).get(i));
			LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);
			branch.setOrigBranchWeight(weightEach);
			branch.setCustomFileName(branchPrefix+i);
			branches.add(branch);
		}
		
		return LogicTree.fromExisting(levels, branches);
	}
	
	public static void main(String[] args) throws IOException {
		TectonicRegionType[] trts = {TectonicRegionType.SUBDUCTION_INTERFACE, TectonicRegionType.SUBDUCTION_SLAB, TectonicRegionType.ACTIVE_SHALLOW};
		for (NSHM26_SeismicityRegions seisReg : NSHM26_SeismicityRegions.values()) {
			for (TectonicRegionType trt : trts) {
				System.out.println("Building for "+seisReg+", "+trt);
				System.out.println("\tRegular:\t"+buildDefault(seisReg, trt, false));
				System.out.println("\tSampled:\t"+buildDefault(seisReg, trt, true));
				System.out.println("\tBuilding sampled tree");
				LogicTree<LogicTreeNode> tree = buildLogicTree(seisReg, trt, 100, true);
				tree.write(new File("/tmp/nshm26_tree_test_"+seisReg.name()+"_"+trt.name()+".json"));
			}
		}
	}

}
