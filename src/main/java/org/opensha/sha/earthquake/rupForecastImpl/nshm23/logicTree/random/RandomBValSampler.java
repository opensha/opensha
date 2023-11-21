package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.calc.WeightedSampler;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.faultSurface.FaultSection;

public class RandomBValSampler implements BranchDependentSampler<RandomBValSampler> {
	
	private double[] bValues;

	public RandomBValSampler(FaultSystemRupSet rupSet, WeightedSampler<SupraSeisBValues> enumSampler) {
		bValues = new double[rupSet.getNumSections()];
		NamedFaults named = rupSet.getModule(NamedFaults.class);
		Map<String, Double> namedFaultBs = named == null ? null : new HashMap<>(named.get().size());
		Map<Integer, Double> parentBs = new HashMap<>();
		for (int s=0; s<bValues.length; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			int parentID = sect.getParentSectionId();
			String name = named == null ? null : named.getFaultName(parentID);
			if (name != null) {
				if (namedFaultBs.containsKey(name)) {
					bValues[s] = namedFaultBs.get(name);
				} else {
					bValues[s] = enumSampler.nextItem().bValue;
					namedFaultBs.put(name, bValues[s]);
				}
			} else if (parentBs.containsKey(parentID)) {
				bValues[s] = parentBs.get(parentID);
			} else {
				bValues[s] = enumSampler.nextItem().bValue;
				parentBs.put(parentID, bValues[s]);
			}
		}
	}
	
	private RandomBValSampler(double[] bValues) {
		this.bValues = bValues;
	}

	@Override
	public RandomBValSampler getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		double[] subsetBValues = new double[rupSubSet.getNumSections()];
		for (int s=0; s<subsetBValues.length; s++)
			subsetBValues[s] = bValues[mappings.getOrigSectID(s)];
		return new RandomBValSampler(subsetBValues);
	}
	
	public double[] getBValues() {
		return bValues;
	}
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	public static class Node extends AbstractSamplingNode<RandomBValSampler> {
		
		@SuppressWarnings("unused") // deserialization
		private Node() {}
		
		public Node(int index, long seed, double weight) {
			super("Section b-value Sample "+index, "bSample"+index, "bSample"+index, weight, seed);
		}

		@Override
		public RandomBValSampler buildSampler(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch, long branchNodeSamplingSeed) {
			System.out.println("Building b-value sampler for "+getShortName()+" with seed: "+branchNodeSamplingSeed);
			WeightedSampler<SupraSeisBValues> enumSampler = weightedNodeValueSampler(
					new Random(branchNodeSamplingSeed), SupraSeisBValues.class);
			return new RandomBValSampler(rupSet, enumSampler);
		}

	}
	
	public static class Level extends RandomlySampledLevel<Node> {
		
		public Level() {
			
		}
		
		public Level(int numSamples) {
			this(numSamples, new Random());
		}
		
		public Level(int numSamples, Random rand) {
			buildNodes(rand, numSamples);
		}

		@Override
		public String getShortName() {
			return "b Samples";
		}

		@Override
		public String getName() {
			return "Section b-value Samples";
		}

		@Override
		public Node buildNodeInstance(int index, long seed, double weight) {
			return new Node(index, seed, weight);
		}

		@Override
		public Class<? extends Node> getType() {
			return Node.class;
		}
		
	}

}
