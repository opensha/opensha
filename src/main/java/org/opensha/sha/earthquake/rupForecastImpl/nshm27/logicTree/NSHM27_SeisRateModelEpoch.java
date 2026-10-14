package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.text.DecimalFormat;

import org.opensha.commons.logicTree.LogicTreeNode.FixedWeightNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.util.TectonicRegionType;

public class NSHM27_SeisRateModelEpoch implements FixedWeightNode {
	
	private double startYear, endYear, mc;
	
	private int index;
	
	private double weight;
	
	private TectonicRegionType trt;
	
	private NSHM27_SeisRateModelEpoch(double startYear, double endYear, double mc, int index, double weight,
			TectonicRegionType trt) {
		this.startYear = startYear;
		this.endYear = endYear;
		this.mc = mc;
		this.index = index;
		this.weight = weight;
		this.trt = trt;
	}
	
	private static DecimalFormat oDF = new DecimalFormat("0.#");

	public double getStartYear() {
		return startYear;
	}
	
	public double getEndYear() {
		return endYear;
	}
	
	public double getMagComplete() {
		return mc;
	}

	/**
	 * @return 0-based index of the epoch
	 */
	public int getEpochIndex() {
		return index;
	}

	@Override
	public String getFilePrefix() {
		return NSHM27_RegionLoader.getNameForTRT(trt)+"Epoch"+(index+1);
	}

	@Override
	public String getShortName() {
		return oDF.format(startYear)+"-"+oDF.format(endYear);
	}

	@Override
	public String getName() {
		return "Epoch "+(index+1)+": "+oDF.format(startYear)+"-"+oDF.format(endYear);
	}

	@Override
	public double getNodeWeight() {
		return weight;
	}
}
