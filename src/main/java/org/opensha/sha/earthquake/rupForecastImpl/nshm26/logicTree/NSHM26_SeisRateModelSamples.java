package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.NSHM26_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_SeisRateModel.NSHM26_SiesRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree.NSHM26_SeisRateModel.NSHM26_SiesRateModelSampleData;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;

public class NSHM26_SeisRateModelSamples extends AbstractRandomlySampledLevel<NSHM26_SiesRateModelSampleData, NSHM26_SiesRateModelSample> {
	
	private NSHM26_SeismicityRegions region;
	private TectonicRegionType trt;
	
	private NSHM26_SeisRateModelSamples() {}
	
	public NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
		super(region.getShortName()+" Rate Model Samples ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
				region.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"Samples",
				"Rate sample ", "Sample", "Sample");
		this.region = region;
		this.trt = trt;
	}
	
	@Override
	public Class<? extends NSHM26_SiesRateModelSampleData> getValueType() {
		return NSHM26_SiesRateModelSampleData.class;
	}
	
	protected CSVFile<String> loadCSV() throws IOException {
		File data = new File(NSHM26_InvConfigFactory.locateDataDirectory(), "seis_rate_samples");
		Preconditions.checkState(data.exists(), "Data directory doesn't exist: %s", data.getAbsolutePath());
		Preconditions.checkNotNull(region, "Region not set; can only be built upon initial construction");
		Preconditions.checkNotNull(trt, "TRT not set; can only be built upon initial construction");
		data = new File(data, region.name().toLowerCase());
		Preconditions.checkState(data.exists(), "Region directory doesn't exist: %s", data.getAbsolutePath());
		data = new File(data, NSHM26_SeisRateModelBranch.getRateModelDate(region));
		Preconditions.checkState(data.exists(), "Date directory doesn't exist: %s", data.getAbsolutePath());
		File csvFile = new File(data, NSHM26_SeisRateModelBranch.getRateModelCSVName(trt));
		Preconditions.checkState(csvFile.exists(), "CSV doesn't exist: %s", data.getAbsolutePath());
		return CSVFile.readFile(csvFile, false);
	}

	@Override
	protected void doBuild(long seed, int numNodes, double weightEach) {
		CSVFile<String> csv;
		try {
			csv = loadCSV();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		List<PureGR> samplesList = SeismicityRateFileLoader.loadSamplesCSV(csv);
		Random rand = new Random(seed);
		ArrayDeque<PureGR> samples = new ArrayDeque<>(numNodes);
		for (int i=0; i<numNodes; i++) {
			int sampleIndex = i % samplesList.size();
			if (sampleIndex == 0)
				// shuffle the list (and reshuffle on any subsequent passes through it)
				Collections.shuffle(samplesList, rand);
			samples.addLast(samplesList.get(sampleIndex));
		}
		build(()->{
			PureGR gr = samples.pop();
			return new NSHM26_SiesRateModelSampleData(region, trt, gr);
		}, numNodes, weightEach);
	}

	@Override
	protected NSHM26_SiesRateModelSample build(int index, NSHM26_SiesRateModelSampleData value, double weightEach) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class<? extends NSHM26_SiesRateModelSample> getType() {
		// TODO Auto-generated method stub
		return null;
	}

}
