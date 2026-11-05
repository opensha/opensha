package org.opensha.sha.calc.sourceFilters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;

import org.junit.Test;
import org.opensha.sha.calc.sourceFilters.params.SourceFiltersParam;
import org.opensha.sha.calc.sourceFilters.params.TectonicRegionTypesParam;
import org.opensha.sha.earthquake.EqkSource;
import org.opensha.sha.util.TectonicRegionType;

public class TectonicRegionSourceFilterTest {

	@Test
	public void testIncludesOnlyConfiguredTectonicRegionTypes() {
		TectonicRegionSourceFilter filter = new TectonicRegionSourceFilter();
		EqkSource crustal = source(TectonicRegionType.ACTIVE_SHALLOW);
		EqkSource interfaceSource = source(TectonicRegionType.SUBDUCTION_INTERFACE);
		EqkSource slab = source(TectonicRegionType.SUBDUCTION_SLAB);

		assertFalse(filter.canSkipSource(crustal, null, 0d));
		assertFalse(filter.canSkipSource(interfaceSource, null, 0d));
		assertFalse(filter.canSkipSource(slab, null, 0d));

		filter.setIncludedTectonicRegionTypes(List.of(TectonicRegionType.SUBDUCTION_INTERFACE));
		assertTrue(filter.canSkipSource(crustal, null, 0d));
		assertFalse(filter.canSkipSource(interfaceSource, null, 0d));
		assertTrue(filter.canSkipSource(slab, null, 0d));
	}

	@Test
	public void testAdjustableParameterUpdatesFilter() {
		TectonicRegionSourceFilter filter = new TectonicRegionSourceFilter();
		assertTrue(filter.getAdjustableParams().size() == 1);
		TectonicRegionTypesParam param = (TectonicRegionTypesParam)filter.getAdjustableParams()
				.getParameter(TectonicRegionTypesParam.NAME);
		param.setValue(EnumSet.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.SUBDUCTION_SLAB));

		assertFalse(filter.canSkipSource(source(TectonicRegionType.ACTIVE_SHALLOW), null, 0d));
		assertTrue(filter.canSkipSource(source(TectonicRegionType.SUBDUCTION_INTERFACE), null, 0d));
		assertFalse(filter.canSkipSource(source(TectonicRegionType.SUBDUCTION_SLAB), null, 0d));
	}

	@Test
	public void testSourceFiltersControlPanelBuilds() {
		SourceFilterManager manager = new SourceFilterManager(SourceFilters.TECTONIC_REGION_TYPES);
		SourceFiltersParam param = new SourceFiltersParam(manager);
		assertNotNull(param.getEditor().getComponent());
	}

	private static EqkSource source(TectonicRegionType trt) {
		EqkSource source = mock(EqkSource.class);
		when(source.getTectonicRegionType()).thenReturn(trt);
		return source;
	}
}
