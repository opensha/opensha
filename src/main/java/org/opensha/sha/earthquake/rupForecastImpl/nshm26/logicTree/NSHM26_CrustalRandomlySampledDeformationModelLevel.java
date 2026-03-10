package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;

public class NSHM26_CrustalRandomlySampledDeformationModelLevel extends RandomlySampledLevel<NSHM26_CrustalRandomlySampledDeformationModels> {
	
	public static String NAME = "NSHM26 Crustal Def Model Sample";

	public NSHM26_CrustalRandomlySampledDeformationModelLevel() {
		
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this(numSamples, new Random());
	}
	
	public NSHM26_CrustalRandomlySampledDeformationModelLevel(int numSamples, Random r) {
		buildNodes(r, numSamples);
	}
	
	@Override
	public String getShortName() {
		return "DMSample";
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public NSHM26_CrustalRandomlySampledDeformationModels buildNodeInstance(int index, long seed, double weight) {
		return new NSHM26_CrustalRandomlySampledDeformationModels(index, seed, weight);
	}

	@Override
	public Class<? extends NSHM26_CrustalRandomlySampledDeformationModels> getType() {
		return NSHM26_CrustalRandomlySampledDeformationModels.class;
	}

}
