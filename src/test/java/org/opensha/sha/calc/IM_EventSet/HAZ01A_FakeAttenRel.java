package org.opensha.sha.calc.IM_EventSet;

import java.io.IOException;
import java.util.HashMap;

import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01ASegment;
import org.opensha.sha.imr.AttenuationRelationship;

public class HAZ01A_FakeAttenRel extends AttenuationRelationship {
	
	HAZ01ASegment segment;
	HashMap<String, Integer> sourceRupIDMap;

    public HAZ01A_FakeAttenRel(String fileName) throws IOException {
		System.out.println("Loading HAZ01A file");
		segment = HAZ01ASegment.loadHAZ01A(fileName).get(0);
		sourceRupIDMap = new HashMap<String, Integer>();
		
		for (int i=0; i<segment.size(); i++) {
			String key = getSourceRupKey(segment.getSourceID(i), segment.getRupID(i));
			sourceRupIDMap.put(key, (Integer)i);
		}
		super.initOtherParams();
	}
	
	public static String getSourceRupKey(int sourceID, int rupID) {
		return sourceID + "_" + rupID;
	}
	
	private int getIndex(int sourceID, int rupID) {
		String key = getSourceRupKey(sourceID, rupID);
		Integer index = sourceRupIDMap.get(key);
		if (index == null) {
			throw new RuntimeException("No entry found in HAZ01A data for sourceID=" + 
				sourceID + ", rupID=" + rupID + " (key: " + key + ")");
		}
		return index;
	}

	@Override
	protected void initEqkRuptureParams() {}

	@Override
	protected void initPropagationEffectParams() {}

	@Override
	protected void initSiteParams() {}

	@Override
	protected void initSupportedIntensityMeasureParams() {}

	@Override
	protected void setPropagationEffectParams() {}

	public double getMean() {
		HAZ01A_FakeRupture rup = (HAZ01A_FakeRupture)eqkRupture;
		return segment.getMeanVal(getIndex(rup.getSourceID(), rup.getRupID()));
	}

	public double getStdDev() {
		HAZ01A_FakeRupture rup = (HAZ01A_FakeRupture)eqkRupture;
		return segment.getStdDevVal(getIndex(rup.getSourceID(), rup.getRupID()));
	}

	public String getShortName() {
		return "HAZ01A_IMR";
	}

	public void setParamDefaults() {}

}
