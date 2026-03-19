package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;

public class NSHM26_CrustalRandomlySampledDeformationModelLevel extends RandomlyGeneratedLevel<NSHM26_CrustalRandomlySampledDeformationModels> {

	public static String NAME = "NSHM26 Crustal Def Model Sample";
	public static String SHORT_NAME = "DMSample";

	public NSHM26_CrustalRandomlySampledDeformationModelLevel() {
		super(NAME, SHORT_NAME, "Deformation Model Sample ", "DMSample", "DMSample");
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this();
		build(new Random().nextLong(), numSamples);
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples, long seed) {
		this();
		build(seed, numSamples);
	}

	@Override
	public Class<? extends NSHM26_CrustalRandomlySampledDeformationModels> getType() {
		return NSHM26_CrustalRandomlySampledDeformationModels.class;
	}

	@Override
	public NSHM26_CrustalRandomlySampledDeformationModels build(Long seed, double weight, String name,
			String shortName, String filePrefix) {
		return new NSHM26_CrustalRandomlySampledDeformationModels(name, shortName, filePrefix, seed, weight);
	}

}
