package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;

public class PRVI25_CrustalRandomlySampledDeformationModelLevel extends RandomlyGeneratedLevel<PRVI25_CrustalRandomlySampledDeformationModels> {

	public static String NAME = "PRVI Crustal Def Model Sample";
	public static String SHORT_NAME = "DMSample";

	public PRVI25_CrustalRandomlySampledDeformationModelLevel() {
		super(NAME, SHORT_NAME);
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this(numSamples, new Random().nextLong());
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples, long seed) {
		super(NAME, SHORT_NAME, "Deformation Model Sample ", "DMSample", "DMSample");
		build(seed, numSamples);
	}

	@Override
	public Class<? extends PRVI25_CrustalRandomlySampledDeformationModels> getType() {
		return PRVI25_CrustalRandomlySampledDeformationModels.class;
	}

	@Override
	public PRVI25_CrustalRandomlySampledDeformationModels build(Long value, double weight, String name,
			String shortName, String filePrefix) {
		return new PRVI25_CrustalRandomlySampledDeformationModels(name, shortName, filePrefix, value, weight);
	}

}
