package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Preconditions;

public class UniqueRuptureTest {
	
	private static Random r = new Random();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testForwardsBackwards() {
		checkEquals(UniqueRupture.forIDs(cluster(0, 10, false)), UniqueRupture.forIDs(cluster(0, 10, true)));
		
		checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, false), cluster(20, 30, false))),
				UniqueRupture.forIDs(union(cluster(0, 10, true), cluster(20, 30, true))));
		checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, false), cluster(20, 30, true))),
				UniqueRupture.forIDs(union(cluster(0, 10, true), cluster(20, 30, false))));
		checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, true), cluster(20, 30, false))),
				UniqueRupture.forIDs(union(cluster(0, 10, false), cluster(20, 30, true))));
	}

	@Test
	public void testAddOrder() {
		for (int i=0; i<10; i++)
			checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, r.nextBoolean()), cluster(20, 30, r.nextBoolean()))),
					UniqueRupture.forIDs(union(cluster(20, 30, r.nextBoolean()), cluster(0, 10, r.nextBoolean()))));
		
		testContains(UniqueRupture.forIDs(union(cluster(20, 30, r.nextBoolean()),
				cluster(0, 10, r.nextBoolean()))));
	}

	@Test
	public void testMerge() {
		for (int i=0; i<10; i++)
			checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, r.nextBoolean()), cluster(11, 20, r.nextBoolean()))),
					UniqueRupture.forIDs(union(cluster(0, 20, r.nextBoolean()))));
		for (int i=0; i<10; i++)
			checkEquals(UniqueRupture.forIDs(union(cluster(0, 10, r.nextBoolean()),
					cluster(15, 20, r.nextBoolean()), cluster(21, 25, r.nextBoolean()))),
					UniqueRupture.forIDs(union(cluster(0, 10, r.nextBoolean()), cluster(15, 25, r.nextBoolean()))));
		for (int i=0; i<10; i++)
			checkEquals(UniqueRupture.forIDs(union(cluster(0, 15, r.nextBoolean()),
					cluster(16, 20, r.nextBoolean()), cluster(21, 25, r.nextBoolean()))),
					UniqueRupture.forIDs(union(cluster(0, 25, r.nextBoolean()))));
	}
	
	@SafeVarargs
	private static List<Integer> union(List<Integer>... clusters) {
		List<Integer> ret = new ArrayList<>();
		
		// add them in random order
		List<Integer> indexes = new ArrayList<>();
		for (int i=0; i<clusters.length; i++)
			indexes.add(i);
		Collections.shuffle(indexes);
		
		for (int i : indexes)
			ret.addAll(clusters[i]);
		
		return ret;
	}
	
	private static List<Integer> cluster(int startIndex, int endIndex, boolean reverse) {
		List<Integer> ids = new ArrayList<>();
		Preconditions.checkState(endIndex >= startIndex);
		for (int i=startIndex; i<=endIndex; i++)
			ids.add(i);
		if (reverse)
			Collections.reverse(ids);
		return ids;
	}
	
	private void checkEquals(UniqueRupture rup1, UniqueRupture rup2) {
		assertEquals(rup1, rup2);
		assertEquals(rup1.hashCode(), rup2.hashCode());
	}
	
	private void testContains(UniqueRupture rup) {
		int minID = Integer.MAX_VALUE;
		int maxID = Integer.MIN_VALUE;
		
		HashSet<Integer> set = new HashSet<>();
		
		for (SectIDRange range : rup.getRanges()) {
			minID = Integer.min(minID, range.getStartID());
			maxID = Integer.max(maxID, range.getEndID());
			for (int i=range.getStartID(); i<=range.getEndID(); i++)
				set.add(i);
		}
		
		int actualSize = 0;
		
		for (int i=Integer.max(0, minID-10); i<=maxID+10; i++) {
			boolean refContains = set.contains(i);
			boolean contains = rup.contains(i);
			if (refContains) {
				assertTrue(contains);
				actualSize++;
			} else {
				assertFalse(contains);
			}
		}
		assertEquals(actualSize, rup.size());
	}

}
