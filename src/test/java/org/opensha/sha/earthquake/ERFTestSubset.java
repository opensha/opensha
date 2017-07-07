package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.util.TectonicRegionType;

public class ERFTestSubset implements ERF {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	HashMap<Integer, Integer> sourceIDMap = new HashMap<Integer, Integer>();
	
	private ERF baseERF;
	
	/** fields for nth rupture info */
	protected int totNumRups=-1;
	protected ArrayList<int[]> nthRupIndicesForSource;	// this gives the nth indices for a given source
	protected int[] srcIndexForNthRup;
	protected int[] rupIndexForNthRup;
	
	public ERFTestSubset(ERF baseERF) {
		this.baseERF = baseERF;
	}

	@Override
	public ParameterList getAdjustableParameterList() {
		return baseERF.getAdjustableParameterList();
	}

	@Override
	public Region getApplicableRegion() {
		return baseERF.getApplicableRegion();
	}

	@Override
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {
		return baseERF.getIncludedTectonicRegionTypes();
	}

	@Override
	public String getName() {
		return baseERF.getName() + "_TEST";
	}

	@Override
	public TimeSpan getTimeSpan() {
		return baseERF.getTimeSpan();
	}

	@Override
	public void setParameter(String name, Object value) {
		baseERF.setParameter(name, value);
	}

	@Override
	public void setTimeSpan(TimeSpan time) {
		baseERF.setTimeSpan(time);
	}

	@Override
	public void updateForecast() {
		baseERF.updateForecast();
	}

	@Override
	public ArrayList<EqkRupture> drawRandomEventSet() {
		throw new RuntimeException("WARNING: drawRandomEventSet not implemented for test ERF!");
	}

	@Override
	public int getNumRuptures(int iSource) {
		// TODO Auto-generated method stub
		return baseERF.getNumRuptures(getBaseSourceID(iSource));
	}

	@Override
	public int getNumSources() {
		return sourceIDMap.size();
	}

	@Override
	public ProbEqkRupture getRupture(int iSource, int nRupture) {
		return baseERF.getRupture(getBaseSourceID(iSource), nRupture);
	}

	@Override
	public ProbEqkSource getSource(int iSource) {
		return baseERF.getSource(getBaseSourceID(iSource));
	}
	
	private int getBaseSourceID(int sourceID) {
		return sourceIDMap.get(new Integer(sourceID));
	}

	@Override
	public ArrayList<ProbEqkSource> getSourceList() {
		ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
		for (int i=0; i<getNumSources(); i++) {
			sources.add(getSource(i));
		}
		return sources;
	}
	
	public void includeSource(int sourceID) {
		if (sourceIDMap.containsValue(new Integer(sourceID))) {
			System.out.println("source "+sourceID+" already included!");
			return; // it's already included
		}
		if (sourceID < 0 || sourceID >= baseERF.getNumSources())
			throw new IndexOutOfBoundsException("source ID to include is out of bounds!");
		int newID = this.getNumSources();
		sourceIDMap.put(new Integer(newID), new Integer(sourceID));
	}
	
	public void includeAllExcept(int sourceID) {
		for (int i=0; i<baseERF.getNumSources(); i++)
			if (i != sourceID)
				includeSource(i);
	}
	
	public ProbEqkSource getOrigSource(int sourceID) {
		return baseERF.getSource(sourceID);
	}
	
	@Override
	public int compareTo(BaseERF o) {
		return baseERF.compareTo(o);
	}

	@Override
	public Iterator<ProbEqkSource> iterator() {
		return getSourceList().iterator();
	}
	
}
