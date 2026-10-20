package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.Direct;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SeisRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModelEpochCatalog.EpochWeighting;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_SeismicityDataFiles;
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
	public void testEpochWeightsUseBValueLogicTreeWeightedAverage() {
		double[][] weightsByMethod = {
				{ 0.2d, 0.8d },
				{ 0.9d, 0.1d },
				{ 0.4d, 0.6d }
		};
		double[] averaged = NSHM27_SeisRateModelEpochCatalog
				.calcLogicTreeWeightedEpochWeights(weightsByMethod);
		// Current b-method weights are 0.5, 0, and 0.5.
		assertEquals(0.3d, averaged[0], 1e-15);
		assertEquals(0.7d, averaged[1], 1e-15);
	}

	@Test
	public void testUnionCatalogAndSamples() throws Exception {
		for (NSHM27_SeismicityRegions region : NSHM27_SeismicityRegions.values()) {
			NSHM27_SeisRateModelEpochCatalog catalog = NSHM27_SeisRateModelEpochCatalog.loadUnion(region);
			assertTrue(catalog.isUnion());
			assertNull(catalog.getTectonicRegion());
			assertFalse(catalog.getEpochs().isEmpty());
			assertEquals(catalog, NSHM27_SeisRateModelEpochCatalog.fromJSON(catalog.toJSON()));

			NSHM27_SeisRateModelEpoch epoch = catalog.getEpoch(0);
			assertTrue(epoch.isUnion());
			for (NSHM27_SeisBValueMethod bMethod : NSHM27_SeisBValueMethod.values()) {
				List<PureGR> samples = NSHM27_SeisRateModelSamples.loadUnionSamples(region, epoch, bMethod);
				assertFalse(samples.isEmpty());
				PureGR sample = NSHM27_SeisRateModelSamples.loadUnionSample(region, epoch, bMethod, 0.5d);
				assertTrue(Double.isFinite(sample.rateAboveM1));
				assertTrue(Double.isFinite(sample.b));
			}
		}
	}

	@Test
	public void testDirectRateLoading() throws Exception {
		for (NSHM27_SeismicityRegions region : NSHM27_SeismicityRegions.values()) {
			validateDirectRates(NSHM27_SeismicityDataFiles.loadUnionDirectRates(region));
			for (TectonicRegionType trt : List.of(TectonicRegionType.ACTIVE_SHALLOW,
					TectonicRegionType.SUBDUCTION_INTERFACE, TectonicRegionType.SUBDUCTION_SLAB))
				validateDirectRates(NSHM27_SeismicityDataFiles.loadDirectRates(region, trt));
		}
	}

	@Test
	public void testLegacyDirectRateLayoutStillLoads() throws Exception {
		String resource = "/data/erf/prvi25/seismicity/rates/2025_07_17/direct/"
				+"directrates-PRVI Union-Full-1973-2024-v10.csv";
		try (InputStream stream = NSHM27SeisRateEpochSerializationTest.class.getResourceAsStream(resource)) {
			assertNotNull(stream);
			validateDirectRates(SeismicityRateFileLoader.loadDirectBranches(CSVFile.readStream(stream, false)));
		}
	}

	private static void validateDirectRates(List<Direct> records) {
		assertEquals(5, records.size());
		Direct mean = SeismicityRateFileLoader.locateMean(records);
		assertTrue(mean.mean);
		assertTrue(Double.isFinite(mean.rateAboveM1));
		assertTrue(mean.rateAboveM1 > 0d);
		assertEquals(mean.M1, mean.cumulativeDist.getMinX(), 0d);
		assertEquals(mean.M1+0.5d*mean.incrementalDist.getDelta(), mean.incrementalDist.getMinX(), 1e-12);
	}

	@Test
	public void testSampleLevelRoundTrip() throws Exception {
		NSHM27_SeisRateModelEpochCatalog catalog = buildCatalog();
		NSHM27_SeisRateModelSamples level = new NSHM27_SeisRateModelSamples(catalog);
		level.setValues(List.of(0.25d), 1d);

		LogicTreeLevel.Adapter<LogicTreeNode> adapter = new LogicTreeLevel.Adapter<>();
		String json = adapter.toJson(level);
		assertTrue(json.contains("epochCatalog"));
		assertFalse(json.contains("rateAboveM1"));

		LogicTreeLevel<?> loadedRaw = adapter.fromJson(json);
		assertTrue(loadedRaw instanceof NSHM27_SeisRateModelSamples);
		NSHM27_SeisRateModelSamples loaded = (NSHM27_SeisRateModelSamples)loadedRaw;
		assertEquals(catalog, loaded.getEpochCatalog());
		assertEquals(1, loaded.getNodes().size());
		NSHM27_SeisRateModelSample sample = loaded.getNodes().get(0);
		assertEquals(0.25d, sample.getValue(), 0d);
	}

	@Test
	public void testEpochBranchRoundTripUsesCatalogIndex() throws Exception {
		NSHM27_SeisRateModelEpochCatalog catalog = buildCatalog();
		NSHM27_SeisRateModelEpoch.Level level = new NSHM27_SeisRateModelEpoch.Level(catalog);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(List.of(level), List.of(catalog.getEpoch(1)));

		File file = File.createTempFile("nshm27-epoch-branch", ".json");
		file.deleteOnExit();
		branch.writeToFile(file);
		String json = Files.readString(file.toPath());
		assertTrue(json.contains("\"value\": 1"));

		LogicTreeBranch<LogicTreeNode> loaded = LogicTreeBranch.read(file);
		assertTrue(loaded.getLevel(0) instanceof NSHM27_SeisRateModelEpoch.Level);
		assertTrue(loaded.getValue(0) instanceof NSHM27_SeisRateModelEpoch);
		assertEquals(catalog.getEpoch(1), loaded.getValue(0));
		NSHM27_SeisRateModelEpoch.Level loadedLevel = (NSHM27_SeisRateModelEpoch.Level)loaded.getLevel(0);
		assertSame(loadedLevel.getCatalog().getEpoch(1), loaded.getValue(0));
	}

	@Test
	public void testAllMixtureBranchResourcesLoad() {
		for (NSHM27_SeismicityRegions region : NSHM27_SeismicityRegions.values())
			for (TectonicRegionType trt : List.of(TectonicRegionType.ACTIVE_SHALLOW,
					TectonicRegionType.SUBDUCTION_INTERFACE, TectonicRegionType.SUBDUCTION_SLAB))
				for (NSHM27_SeisBValueMethod bMethod : NSHM27_SeisBValueMethod.values())
					assertTrue(NSHM27_SeisRateModelBranch.loadMixtureRateModel(region, trt, bMethod)
							.getMeanRecord() != null);
		for (NSHM27_SeismicityRegions region : NSHM27_SeismicityRegions.values())
			for (NSHM27_SeisBValueMethod bMethod : NSHM27_SeisBValueMethod.values())
				assertTrue(NSHM27_SeisRateModelBranch.loadUnionMixtureRateModel(region, bMethod)
						.getMeanRecord() != null);
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
