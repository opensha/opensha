package org.opensha.commons.util.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.CorrTruncatedNormalDistribution;
import org.apache.commons.statistics.distribution.TruncatedNormalDistribution;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ContinuousDistributionTypeAdapterTest {

	private static Gson buildGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(ContinuousDistribution.class, new ContinuousDistributionTypeAdapter());
		return builder.create();
	}

	@Test
	public void testCorrTruncatedRoundTripUsesCanonicalTypeName() {
		Gson gson = buildGson();
		ContinuousDistribution distribution = CorrTruncatedNormalDistribution.of(1d, 0.1d, 0.7d, 1.3d);

		String json = gson.toJson(distribution, ContinuousDistribution.class);
		assertTrue(json.contains("\"type\":\"TruncatedNormalDistribution\""));
		assertFalse(json.contains("CorrTruncatedNormalDistribution"));

		ContinuousDistribution loaded = gson.fromJson(json, ContinuousDistribution.class);
		assertTrue(loaded instanceof CorrTruncatedNormalDistribution);
		CorrTruncatedNormalDistribution corr = (CorrTruncatedNormalDistribution)loaded;
		assertEquals(1d, corr.getParentMean(), 0d);
		assertEquals(0.1d, corr.getParentStandardDeviation(), 0d);
		assertEquals(0.7d, corr.getSupportLowerBound(), 0d);
		assertEquals(1.3d, corr.getSupportUpperBound(), 0d);
	}

	@Test(expected = IllegalStateException.class)
	public void testLegacyTruncatedNormalThrows() {
		Gson gson = buildGson();
		ContinuousDistribution distribution = TruncatedNormalDistribution.of(1d, 0.1d, 0.7d, 1.3d);
		gson.toJson(distribution, ContinuousDistribution.class);
	}
}
