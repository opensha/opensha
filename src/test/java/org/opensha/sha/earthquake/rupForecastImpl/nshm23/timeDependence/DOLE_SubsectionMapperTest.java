package org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.util.TimeUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.HistoricalRupture;

public class DOLE_SubsectionMapperTest {

	@Test
	public void testLoadHistoricalRupturesWithBCEYears() throws IOException {
		List<HistoricalRupture> ruptures = DOLE_SubsectionMapper.loadHistRups();
		assertFalse(ruptures.isEmpty());

		boolean foundBCE = false;
		for (HistoricalRupture rupture : ruptures) {
			if (rupture.year < 1) {
				foundBCE = true;
				assertEquals(TimeUtils.yearToEpochMillis(rupture.year), rupture.epochMillis);
			}
		}
		assertTrue("Expected at least one BCE historical rupture", foundBCE);
	}
}
