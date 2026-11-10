package org.opensha.sha.simulators.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

public class RSQSimUtilsTest {

	@Test
	public void testConnectedComponentIncludesTransitiveConnectionsAndExcludesRemoteBundles() {
		List<List<FaultSection>> bundles = Arrays.asList(
				Arrays.asList(section(0), section(4)),
				Arrays.asList(section(1)),
				Arrays.asList(section(2)),
				Arrays.asList(section(3)));
		SectionDistanceAzimuthCalculator distCalc = mock(SectionDistanceAzimuthCalculator.class);
		when(distCalc.getDistance(any(FaultSection.class), any(FaultSection.class))).thenAnswer(invocation -> {
			int id1 = invocation.getArgument(0, FaultSection.class).getSectionId();
			int id2 = invocation.getArgument(1, FaultSection.class).getSectionId();
			if ((id1 == 0 && id2 == 1) || (id1 == 1 && id2 == 0)
					|| (id1 == 1 && id2 == 2) || (id1 == 2 && id2 == 1))
				return 10d;
			return 300d;
		});

		boolean[] retained = RSQSimUtils.connectedComponent(bundles, 0, distCalc, 200d);

		assertArrayEquals(new boolean[] { true, true, true, false }, retained);
	}

	@Test
	public void testLargestComponentUsesTotalWeight() {
		List<List<FaultSection>> bundles = Arrays.asList(
				Arrays.asList(section(0)),
				Arrays.asList(section(1)),
				Arrays.asList(section(2)));
		SectionDistanceAzimuthCalculator distCalc = mock(SectionDistanceAzimuthCalculator.class);
		when(distCalc.getDistance(any(FaultSection.class), any(FaultSection.class))).thenAnswer(invocation -> {
			int id1 = invocation.getArgument(0, FaultSection.class).getSectionId();
			int id2 = invocation.getArgument(1, FaultSection.class).getSectionId();
			return Math.abs(id1-id2) == 1 && Math.max(id1, id2) == 1 ? 10d : 300d;
		});

		// The disconnected bundle has the largest individual weight (10), but the connected
		// pair has the largest component total (6+5).
		boolean[] retained = RSQSimUtils.largestWeightConnectedComponent(bundles,
				new double[] { 6d, 5d, 10d }, new double[] { 1d, 1d, 1d }, distCalc, 200d);

		assertArrayEquals(new boolean[] { true, true, false }, retained);
	}

	private static FaultSection section(int id) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setSectionName("Section "+id);
		return section;
	}
}
