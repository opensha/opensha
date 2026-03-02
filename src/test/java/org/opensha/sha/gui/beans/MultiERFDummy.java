package org.opensha.sha.gui.beans;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.util.TectonicRegionType;

public class MultiERFDummy extends AbstractERF {
	
	Set<TectonicRegionType> regions;
	
	public MultiERFDummy() {
		regions = EnumSet.of(TectonicRegionType.ACTIVE_SHALLOW,
				TectonicRegionType.STABLE_SHALLOW, TectonicRegionType.SUBDUCTION_SLAB);
	}

	@Override
	public int getNumSources() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ProbEqkSource getSource(int iSource) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArrayList getSourceList() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateForecast() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Set<TectonicRegionType> getIncludedTectonicRegionTypes() {
		return regions;
	}

}

