package org.opensha.sha.earthquake.faultSysSolution.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.sourceFilters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.util.TectonicRegionType;

public class SolHazardMapCalcTest {

	@Test
	public void testNumFaultSourcesForGriddedOnlySiteSkip() {
		BaseFaultSystemSolutionERF erf = mock(BaseFaultSystemSolutionERF.class);
		when(erf.getNumFaultSystemSources()).thenReturn(12345);

		assertEquals(0, SolHazardMapCalc.getNumFaultSystemSourcesForSiteSkip(
				erf, IncludeBackgroundOption.ONLY));
		assertEquals(12345, SolHazardMapCalc.getNumFaultSystemSourcesForSiteSkip(
				erf, IncludeBackgroundOption.EXCLUDE));
		assertEquals(12345, SolHazardMapCalc.getNumFaultSystemSourcesForSiteSkip(
				erf, IncludeBackgroundOption.INCLUDE));
	}

	@Test
	public void testGriddedOnlySiteSkipDoesNotQueryERFSources() {
		AbstractERF erf = mock(AbstractERF.class);
		when(erf.getNumSources()).thenReturn(1);

		GridSourceProvider gridProv = mock(GridSourceProvider.class);
		when(gridProv.getTectonicRegionTypes()).thenReturn(Set.of(TectonicRegionType.STABLE_SHALLOW));
		when(gridProv.getNumLocations()).thenReturn(1);
		when(gridProv.getNumSources()).thenReturn(1);
		when(gridProv.getLocation(0)).thenReturn(new Location(0d, 0d));

		SourceFilterManager filters = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
		filters.getFilterInstance(FixedDistanceCutoffFilter.class).setMaxDistance(100d);
		Site distantSite = new Site(new Location(10d, 10d));

		assertTrue(SolHazardMapCalc.shouldSkipSite(distantSite, filters, erf, 0, gridProv));
		verify(erf, never()).getSource(anyInt());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSiteSkipRejectsInvalidSourceCount() {
		AbstractERF erf = mock(AbstractERF.class);
		when(erf.getNumSources()).thenReturn(1);
		SourceFilterManager filters = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
		SolHazardMapCalc.shouldSkipSite(new Site(new Location(0d, 0d)), filters, erf, 2, null);
	}
}
