package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.util.Random;

import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;

public class NSHM27_CrustalRandomlySampledDeformationModelLevel extends RandomlyGeneratedLevel<NSHM27_CrustalRandomlySampledDeformationModels> {

	public static String NAME = "Crustal Deformation Model Sample";
	public static String SHORT_NAME = "DMSample";

	public NSHM27_CrustalRandomlySampledDeformationModelLevel() {
		super(NAME, SHORT_NAME, "Deformation Model Sample ", "DMSample", "DMSample");
	}
	
	public NSHM27_CrustalRandomlySampledDeformationModelLevel(int numSamples) {
		this();
		build(new Random().nextLong(), numSamples);
	}
	
	public NSHM27_CrustalRandomlySampledDeformationModelLevel(int numSamples, long seed) {
		this();
		build(seed, numSamples);
	}

	@Override
	public Class<? extends NSHM27_CrustalRandomlySampledDeformationModels> getType() {
		return NSHM27_CrustalRandomlySampledDeformationModels.class;
	}

	@Override
	public NSHM27_CrustalRandomlySampledDeformationModels build(Long seed, double weight, String name,
			String shortName, String filePrefix) {
		return new NSHM27_CrustalRandomlySampledDeformationModels(name, shortName, filePrefix, seed, weight);
	}

}
