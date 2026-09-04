package org.opensha.sha.earthquake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.CustomCacheWrappedSurface;

/**
 * Tests that {@link DistCachedERFWrapper} hands out surfaces that can still be decomposed.
 * <p>
 * The wrapper rebuilds every {@link CompoundSurface} so that each calculation thread gets its own distance caches.
 * That rebuild has to preserve two things: the {@link FaultSection} list, because IMRs such as
 * {@code JointRuptureExperimentalIMR} split a rupture by the tectonic region types of its sections, and the
 * {@link CompoundSurface.DownDip} vs {@link CompoundSurface.Simple} choice, because the two compute DistanceX
 * differently.
 *
 * @author voj
 */
public class DistCachedERFWrapperTest {

	private static final double SPACING = 1d;

	private static FaultSystemRupSet downDipRupSet;
	private static FaultSystemRupSet singleRowRupSet;

	/** Sites spread around the sections, close in and far out, on both sides of the dip direction. */
	private static final Location[] SITES = {
			new Location(-41.4, 174.85),
			new Location(-41.2, 174.6),
			new Location(-41.6, 175.2),
			new Location(-40.5, 174.0),
			new Location(-42.0, 176.0),
	};

	@BeforeClass
	public static void setUpBeforeClass() {
		downDipRupSet = buildRupSet(2);
		singleRowRupSet = buildRupSet(1);
	}

	/**
	 * A rupture set with a single rupture over a grid of subsections: {@code numRows} rows down dip by two columns
	 * along strike, all under one parent. With more than one row the rupture surface is a
	 * {@link CompoundSurface.DownDip}, with one row it is a {@link CompoundSurface.Simple}.
	 */
	protected static FaultSystemRupSet buildRupSet(int numRows) {
		List<FaultSection> sections = new ArrayList<>();
		List<Integer> sectionsForRup = new ArrayList<>();
		int id = 0;
		for (int row = 0; row < numRows; row++) {
			for (int col = 0; col < 2; col++) {
				sections.add(makeSection(id, row, col));
				sectionsForRup.add(id);
				id++;
			}
		}
		return FaultSystemRupSet.builder(sections, List.of(sectionsForRup))
				.rupMags(new double[] { 8d })
				.build();
	}

	/** One subsection of the grid: {@code col} places it along strike, {@code row} places it down dip. */
	protected static FaultSection makeSection(int id, int row, int col) {
		FaultTrace trace = new FaultTrace("trace " + id);
		// each column steps east along strike, each row steps north as the surface dips away
		double lat = -41.5 + 0.15 * row;
		double lon = 174.7 + 0.2 * col;
		trace.add(new Location(lat, lon));
		trace.add(new Location(lat, lon + 0.2));

		FaultSectionPrefData pref = new FaultSectionPrefData();
		pref.setSectionId(id);
		pref.setSectionName("Section " + id);
		pref.setParentSectionId(0);
		pref.setParentSectionName("Parent");
		pref.setFaultTrace(trace);
		pref.setAveSlipRate(10);
		pref.setAveRake(90);
		pref.setAveDip(20);
		pref.setAveUpperDepth(row * 10d);
		pref.setAveLowerDepth(row * 10d + 10d);
		pref.setDipDirection((float) trace.getDipDirection());

		GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
		section.setSubSectionIndex(id);
		section.setSubSectionIndexAlong(col);
		section.setSubSectionIndexDownDip(row);
		return section;
	}

	/** Runs a rupture surface through the wrapper the way {@link DistCachedERFWrapper} does internally. */
	protected static RuptureSurface wrap(RuptureSurface surf) {
		Map<RuptureSurface, RuptureSurface> wrappedMap = new HashMap<>();
		return DistCachedERFWrapper.getWrappedSurface(wrappedMap, surf);
	}

	/** The fixture has to actually exercise the down dip path, otherwise the DistanceX test proves nothing. */
	@Test
	public void testFixtureCoversBothImplementations() {
		assertTrue("expected a DownDip surface",
				downDipRupSet.getSurfaceForRupture(0, SPACING) instanceof CompoundSurface.DownDip);
		assertTrue("expected a Simple surface",
				singleRowRupSet.getSurfaceForRupture(0, SPACING) instanceof CompoundSurface.Simple);
	}

	/** A wrapped compound surface is still a compound surface, so IMRs can still decompose it. */
	@Test
	public void testWrappedSurfaceIsStillCompound() {
		for (FaultSystemRupSet rupSet : new FaultSystemRupSet[] { downDipRupSet, singleRowRupSet }) {
			RuptureSurface wrapped = wrap(rupSet.getSurfaceForRupture(0, SPACING));
			assertTrue("wrapped surface is no longer a CompoundSurface", wrapped instanceof CompoundSurface);
		}
	}

	/** The section list survives the rebuild, in the original order, for both implementations. */
	@Test
	public void testWrappedSurfaceKeepsItsSections() {
		for (FaultSystemRupSet rupSet : new FaultSystemRupSet[] { downDipRupSet, singleRowRupSet }) {
			CompoundSurface orig = (CompoundSurface) rupSet.getSurfaceForRupture(0, SPACING);
			CompoundSurface wrapped = (CompoundSurface) wrap(orig);

			List<? extends FaultSection> sections = wrapped.getSectionsList();
			assertNotNull("wrapped " + orig.getClass().getSimpleName() + " lost its section list", sections);
			assertEquals(orig.getSectionsList().size(), sections.size());
			for (int i = 0; i < sections.size(); i++) {
				assertSame("section " + i + " changed", orig.getSectionsList().get(i), sections.get(i));
			}
		}
	}

	/**
	 * {@link CompoundSurface#get(List, List)} has to keep the sections it is handed on both branches. It used to
	 * drop them whenever no section was down dip, which left a Simple surface that IMRs could not decompose.
	 */
	@Test
	public void testCompoundSurfaceGetKeepsSections() {
		for (FaultSystemRupSet rupSet : new FaultSystemRupSet[] { downDipRupSet, singleRowRupSet }) {
			CompoundSurface orig = (CompoundSurface) rupSet.getSurfaceForRupture(0, SPACING);
			CompoundSurface rebuilt = CompoundSurface.get(orig.getSurfaceList(), orig.getSectionsList());
			assertNotNull("CompoundSurface.get dropped the sections for a " + rebuilt.getClass().getSimpleName(),
					rebuilt.getSectionsList());
			assertEquals(orig.getSectionsList(), rebuilt.getSectionsList());
		}
	}

	/** DownDip and Simple compute DistanceX differently, so the rebuild has to keep the same implementation. */
	@Test
	public void testWrappedSurfaceKeepsItsImplementation() {
		assertTrue("down dip surface was rebuilt as something else",
				wrap(downDipRupSet.getSurfaceForRupture(0, SPACING)) instanceof CompoundSurface.DownDip);
		assertTrue("simple surface was rebuilt as something else",
				wrap(singleRowRupSet.getSurfaceForRupture(0, SPACING)) instanceof CompoundSurface.Simple);
	}

	/** Caching must not change any distance metric, at any site. */
	@Test
	public void testWrappedSurfaceGivesTheSameDistances() {
		for (FaultSystemRupSet rupSet : new FaultSystemRupSet[] { downDipRupSet, singleRowRupSet }) {
			RuptureSurface orig = rupSet.getSurfaceForRupture(0, SPACING);
			RuptureSurface wrapped = wrap(orig);
			for (Location site : SITES) {
				assertEquals("DistanceRup at " + site, orig.getDistanceRup(site), wrapped.getDistanceRup(site), 1e-10);
				assertEquals("DistanceJB at " + site, orig.getDistanceJB(site), wrapped.getDistanceJB(site), 1e-10);
				assertEquals("DistanceX at " + site, orig.getDistanceX(site), wrapped.getDistanceX(site), 1e-10);
			}
		}
	}

	/** The point of the wrapper: every sub-surface gets its own single valued cache. */
	@Test
	public void testSubSurfacesAreCacheWrapped() {
		CompoundSurface wrapped = (CompoundSurface) wrap(downDipRupSet.getSurfaceForRupture(0, SPACING));
		for (RuptureSurface subSurf : wrapped.getSurfaceList()) {
			assertTrue("sub-surface is not cache wrapped: " + subSurf.getClass(),
					subSurf instanceof CustomCacheWrappedSurface);
		}
	}

	/** A surface that is not compound is still wrapped, and the same surface is only wrapped once. */
	@Test
	public void testNonCompoundSurfacesAreWrappedOnce() {
		RuptureSurface orig = downDipRupSet.getFaultSectionData(0).getFaultSurface(SPACING, false, true);
		assertTrue("fixture surface should be cache enabled", orig instanceof CacheEnabledSurface);

		Map<RuptureSurface, RuptureSurface> wrappedMap = new HashMap<>();
		RuptureSurface first = DistCachedERFWrapper.getWrappedSurface(wrappedMap, orig);
		RuptureSurface second = DistCachedERFWrapper.getWrappedSurface(wrappedMap, orig);
		assertTrue(first instanceof CustomCacheWrappedSurface);
		assertSame("the same surface was wrapped twice", first, second);
	}

	/** End to end: the ruptures an ERF hands out through the wrapper still decompose into their sections. */
	@Test
	public void testWrappedErfRupturesStillDecompose() {
		FaultSystemSolution solution = new FaultSystemSolution(downDipRupSet, new double[] { 1e-2 });
		BaseFaultSystemSolutionERF erf = new BaseFaultSystemSolutionERF();
		erf.setSolution(solution);
		erf.updateForecast();

		DistCachedERFWrapper wrapper = new DistCachedERFWrapper(erf);
		assertSame(erf, wrapper.getOriginalERF());

		int checked = 0;
		for (int s = 0; s < wrapper.getNumSources(); s++) {
			ProbEqkSource source = wrapper.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); r++) {
				RuptureSurface surf = source.getRupture(r).getRuptureSurface();
				assertTrue("rupture surface reached the IMR as an opaque " + surf.getClass().getSimpleName(),
						surf instanceof CompoundSurface);
				assertNotNull("rupture surface reached the IMR without its sections",
						((CompoundSurface) surf).getSectionsList());
				checked++;
			}
		}
		assertTrue("expected at least one rupture", checked > 0);
	}

	/** A compound surface built without sections stays without sections; the wrapper invents nothing. */
	@Test
	public void testSectionlessCompoundSurfaceStaysSectionless() {
		CompoundSurface orig = (CompoundSurface) downDipRupSet.getSurfaceForRupture(0, SPACING);
		CompoundSurface sectionless = CompoundSurface.get(orig.getSurfaceList());
		assertNull(sectionless.getSectionsList());

		CompoundSurface wrapped = (CompoundSurface) wrap(sectionless);
		assertNull(wrapped.getSectionsList());
	}
}
