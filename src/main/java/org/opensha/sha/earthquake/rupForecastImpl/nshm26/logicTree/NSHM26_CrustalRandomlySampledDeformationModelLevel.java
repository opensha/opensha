package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;

public class NSHM26_CrustalRandomlySampledDeformationModelLevel extends RandomlyGeneratedLevel<NSHM26_CrustalRandomlySampledDeformationModels> {

	public static String NAME = "NSHM26 Crustal Def Model Sample";
	public static String SHORT_NAME = "DMSample";

	public NSHM26_CrustalRandomlySampledDeformationModelLevel() {
		super();
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this(numSamples, new Random().nextLong());
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples, long seed) {
		super(NAME, SHORT_NAME, "Deformation Model Sample ", "DMSample", "DMSample");
		build(seed, numSamples);
	}

	@Override
	public NSHM26_CrustalRandomlySampledDeformationModels buildNodeInstance(int index, long seed, double weight) {
		return new NSHM26_CrustalRandomlySampledDeformationModels(
				getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index), seed, weight);
	}

	@Override
	public Class<? extends NSHM26_CrustalRandomlySampledDeformationModels> getType() {
		return NSHM26_CrustalRandomlySampledDeformationModels.class;
	}

}
