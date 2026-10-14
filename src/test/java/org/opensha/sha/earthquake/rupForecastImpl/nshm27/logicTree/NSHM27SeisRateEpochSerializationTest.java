package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.EpochDependentGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SeisRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModelEpochCatalog.EpochWeighting;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

public class NSHM27SeisRateEpochSerializationTest {

	@Test
	public void testCatalogParsingUsesSourceOrderAndIgnoresDuplicateBlock() throws Exception {
		NSHM27_SeisRateModelEpochCatalog catalog = buildCatalog();
		assertEquals(2, catalog.getEpochs().size());
		assertEquals(1916d, catalog.getEpoch(0).getStartYear(), 0d);
		assertEquals(1964d, catalog.getEpoch(1).getStartYear(), 0d);
		assertEquals(0.25d, catalog.getEpoch(0).getNodeWeight(), 0d);
		assertEquals(0.75d, catalog.getEpoch(1).getNodeWeight(), 0d);
	}

	@Test
	public void testSampleLevelRoundTrip() throws Exception {
		NSHM27_SeisRateModelEpochCatalog catalog = buildCatalog();
		NSHM27_SeisRateModelSamples level = new NSHM27_SeisRateModelSamples(catalog);
		PureGR first = new PureGR(RateType.M1, 5d, Double.POSITIVE_INFINITY, 0.01d, 0.8d, Double.NaN, true);
		PureGR second = new PureGR(RateType.M1, 5d, Double.POSITIVE_INFINITY, 0.02d, 0.9d, Double.NaN, true);
		level.setValues(List.of(new EpochDependentGR(List.of(first, second), 0.25d)), 1d);

		LogicTreeLevel.Adapter<LogicTreeNode> adapter = new LogicTreeLevel.Adapter<>();
		String json = adapter.toJson(level);
		assertTrue(json.contains("epochCatalog"));
		assertFalse(json.contains("rateModels"));

		LogicTreeLevel<?> loadedRaw = adapter.fromJson(json);
		assertTrue(loadedRaw instanceof NSHM27_SeisRateModelSamples);
		NSHM27_SeisRateModelSamples loaded = (NSHM27_SeisRateModelSamples)loadedRaw;
		assertEquals(catalog, loaded.getEpochCatalog());
		assertEquals(1, loaded.getNodes().size());
		NSHM27_SeisRateModelSample sample = loaded.getNodes().get(0);
		assertEquals(0.25d, sample.getValue().getSampleFractile(), 0d);
		assertEquals(0.01d, sample.getValue().getValue(catalog.getEpoch(0)).rateAboveM1, 0d);
		assertEquals(0.02d, sample.getValue().getValue(catalog.getEpoch(1)).rateAboveM1, 0d);
	}

	private static NSHM27_SeisRateModelEpochCatalog buildCatalog() throws Exception {
		File file = File.createTempFile("nshm27-epoch-weights", ".csv");
		file.deleteOnExit();
		String block = "Region name,GNMI-Interface\n"
				+"n epochs,2\n"
				+"t start,t end,Mc\n"
				+"1916,1964,6.5\n"
				+"1964,2026,5.8\n"
				+"Duration weights,0.4,0.6\n"
				+"N weights,0.3,0.7\n"
				+"SQRTN weights,0.25,0.75\n";
		Files.writeString(file.toPath(), block+block, StandardCharsets.UTF_8);
		return NSHM27_SeisRateModelEpochCatalog.load(file, NSHM27_SeismicityRegions.GNMI,
				TectonicRegionType.SUBDUCTION_INTERFACE, "test_dataset", EpochWeighting.SQRTN);
	}
}
