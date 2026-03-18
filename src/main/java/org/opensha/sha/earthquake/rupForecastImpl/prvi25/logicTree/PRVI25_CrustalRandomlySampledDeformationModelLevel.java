package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;

public class PRVI25_CrustalRandomlySampledDeformationModelLevel extends RandomlyGeneratedLevel<PRVI25_CrustalRandomlySampledDeformationModels> {

	public static String NAME = "PRVI Crustal Def Model Sample";
	public static String SHORT_NAME = "DMSample";

	public PRVI25_CrustalRandomlySampledDeformationModelLevel() {
		super();
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this(numSamples, new Random().nextLong());
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples, long seed) {
		super(NAME, SHORT_NAME, "Deformation Model Sample ", "DMSample", "DMSample");
		build(seed, numSamples);
	}

	@Override
	public PRVI25_CrustalRandomlySampledDeformationModels buildNodeInstance(int index, long seed, double weight) {
		return new PRVI25_CrustalRandomlySampledDeformationModels(getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index), seed, weight);
	}

	@Override
	public Class<? extends PRVI25_CrustalRandomlySampledDeformationModels> getType() {
		return PRVI25_CrustalRandomlySampledDeformationModels.class;
		//+index,+index, +index
	}

}
