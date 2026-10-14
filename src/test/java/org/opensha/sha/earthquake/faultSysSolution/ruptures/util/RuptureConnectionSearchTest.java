package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

public class RuptureConnectionSearchTest {

	@Test
	public void testIndexedSearchGraphAndBacktracking() {
		for (boolean cumulativeJumps : new boolean[] { false, true }) {
			CountingCluster a = cluster(0);
			CountingCluster b = cluster(1);
			CountingCluster c = cluster(2);
			CountingCluster d = cluster(3);

			// A cheap cycle exercises recursive backtracking and produces tied optimal routes.
			connect(a, b, 1d);
			connect(b, c, 1d);
			connect(c, d, 1d);
			connect(d, a, 1d);
			// These chords must be rejected in favor of paths around the cheap cycle.
			connect(a, c, 10d);
			connect(b, d, 10d);

			List<FaultSubsectionCluster> clusters = Arrays.asList(a, b, c, d);
			RuptureConnectionSearch search = new RuptureConnectionSearch(null, null, 100d, cumulativeJumps);
			List<Jump> jumps = search.calcRuptureJumps(clusters, false);

			Set<String> actualPairs = new HashSet<>();
			for (Jump jump : jumps)
				actualPairs.add(pair(jump.fromSection, jump.toSection));
			assertEquals(new HashSet<>(Arrays.asList("0-1", "1-2", "2-3", "0-3")), actualPairs);

			for (CountingCluster cluster : Arrays.asList(a, b, c, d)) {
				// Adjacency ordering and jump lookup are built once, not repeated at recursive nodes.
				assertEquals(1, cluster.sortedConnectionsCalls);
				assertEquals(3, cluster.connectionsToCalls);
			}
		}
	}

	@Test
	public void testShorterJumpFromNewEndDoesNotStrandOtherBranch() {
		CountingCluster a = cluster(0);
		CountingCluster b = cluster(1);
		CountingCluster c = cluster(2);
		CountingCluster d = cluster(3);
		CountingCluster e = cluster(4);

		// A->B is taken first. The stale A jump list then encounters A->C, but B->C
		// is shorter. B->C must be deferred until B's complete jump list is processed,
		// otherwise B->E is stranded when the main strand advances to C.
		List<Jump> jumps = Arrays.asList(
				jump(a, b, 0.05d),
				jump(a, c, 0.1d),
				jump(b, c, 0.01d),
				jump(c, d, 0.2d),
				jump(b, e, 5.9d));
		SectionDistanceAzimuthCalculator distCalc = mock(SectionDistanceAzimuthCalculator.class);
		when(distCalc.getDistance(any(FaultSection.class), any(FaultSection.class))).thenReturn(1d);
		RuptureConnectionSearch search = new RuptureConnectionSearch(null, distCalc, 100d, false);

		ClusterRupture rupture = search.buildClusterRupture(
				Arrays.asList(a, b, c, d, e), jumps, false, a);

		assertSame(a, rupture.clusters[0]);
		assertEquals(5, rupture.getTotalNumClusters());
	}

	private static CountingCluster cluster(int id) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setSectionName("Section "+id);
		section.setParentSectionId(id);
		section.setParentSectionName("Parent "+id);
		return new CountingCluster(Arrays.asList(section));
	}

	private static void connect(FaultSubsectionCluster from, FaultSubsectionCluster to, double distance) {
		Jump jump = jump(from, to, distance);
		from.addConnection(jump);
		to.addConnection(jump.reverse());
	}

	private static Jump jump(FaultSubsectionCluster from, FaultSubsectionCluster to, double distance) {
		return new Jump(from.startSect, from, to.startSect, to, distance);
	}

	private static String pair(FaultSection section1, FaultSection section2) {
		int id1 = section1.getSectionId();
		int id2 = section2.getSectionId();
		return id1 < id2 ? id1+"-"+id2 : id2+"-"+id1;
	}

	private static class CountingCluster extends FaultSubsectionCluster {
		private int sortedConnectionsCalls;
		private int connectionsToCalls;

		private CountingCluster(List<? extends FaultSection> subSects) {
			super(subSects);
		}

		@Override
		public List<FaultSubsectionCluster> getDistSortedConnectedClusters() {
			sortedConnectionsCalls++;
			return super.getDistSortedConnectedClusters();
		}

		@Override
		public Collection<Jump> getConnectionsTo(FaultSubsectionCluster cluster) {
			connectionsToCalls++;
			return super.getConnectionsTo(cluster);
		}
	}
}
