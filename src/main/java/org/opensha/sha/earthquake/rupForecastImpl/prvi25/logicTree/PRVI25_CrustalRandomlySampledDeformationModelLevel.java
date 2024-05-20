package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;

public class PRVI25_CrustalRandomlySampledDeformationModelLevel extends RandomlySampledLevel<PRVI25_CrustalRandomlySampledDeformationModels> {

	public PRVI25_CrustalRandomlySampledDeformationModelLevel() {
		
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this(numSamples, new Random());
	}
	
	public PRVI25_CrustalRandomlySampledDeformationModelLevel(int numSamples, Random r) {
		buildNodes(r, numSamples);
	}
	
	@Override
	public String getShortName() {
		return "DMSample";
	}

	@Override
	public String getName() {
		return "PRVI Crustal Def Model Sample";
	}

	@Override
	public PRVI25_CrustalRandomlySampledDeformationModels buildNodeInstance(int index, long seed, double weight) {
		return new PRVI25_CrustalRandomlySampledDeformationModels(index, seed, weight);
	}

	@Override
	public Class<? extends PRVI25_CrustalRandomlySampledDeformationModels> getType() {
		return PRVI25_CrustalRandomlySampledDeformationModels.class;
	}

}
