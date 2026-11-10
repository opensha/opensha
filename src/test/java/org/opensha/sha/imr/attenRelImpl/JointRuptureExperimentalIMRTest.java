package org.opensha.sha.imr.attenRelImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR.JointSplit;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests the crustal/interface split that {@link JointRuptureExperimentalIMR} applies to a joint rupture, and the
 * cache that keeps it from being rebuilt at every site.
 *
 * @author voj
 */
public class JointRuptureExperimentalIMRTest {

	private static final double SPACING = 1d;

	/** Sections 0 and 1 are crustal, 2 and 3 are subduction interface. */
	private static final int NUM_CRUSTAL = 2;

	private static FaultSystemRupSet rupSet;

	@BeforeClass
	public static void setUpBeforeClass() {
		List<FaultSection> sections = new ArrayList<>();
		for (int id = 0; id < 4; id++) {
			sections.add(makeSection(id, id < NUM_CRUSTAL
					? TectonicRegionType.ACTIVE_SHALLOW : TectonicRegionType.SUBDUCTION_INTERFACE));
		}
		// rupture 0 is joint, rupture 1 is crustal only
		rupSet = FaultSystemRupSet.builder(sections, List.of(List.of(0, 1, 2, 3), List.of(0, 1)))
				.rupMags(new double[] { 8d, 7d })
				.build();
	}

	protected static FaultSection makeSection(int id, TectonicRegionType trt) {
		boolean crustal = trt == TectonicRegionType.ACTIVE_SHALLOW;
		FaultTrace trace = new FaultTrace("trace " + id);
		double lat = -41.5 + 0.15 * id;
		trace.add(new Location(lat, 174.7));
		trace.add(new Location(lat, 174.9));

		FaultSectionPrefData pref = new FaultSectionPrefData();
		pref.setSectionId(id);
		pref.setSectionName("Section " + id);
		pref.setParentSectionId(crustal ? 0 : 1);
		pref.setParentSectionName(crustal ? "Crustal" : "Interface");
		pref.setFaultTrace(trace);
		pref.setAveSlipRate(10);
		// distinct rakes per region so that the split rake is recognisably that region's
		pref.setAveRake(crustal ? 180 : 90);
		pref.setAveDip(crustal ? 90 : 20);
		pref.setAveUpperDepth(0);
		pref.setAveLowerDepth(crustal ? 15 : 20);
		pref.setDipDirection((float) trace.getDipDirection());

		GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
		section.setTectonicRegionType(trt);
		return section;
	}

	protected static CompoundSurface surfaceForRup(int rupIndex) {
		return (CompoundSurface) rupSet.getSurfaceForRupture(rupIndex, SPACING);
	}

	/** Each half of the split gets exactly the sub-surfaces of its own tectonic region, in order. */
	@Test
	public void testSplitPartitionsByTectonicRegion() {
		CompoundSurface surf = surfaceForRup(0);
		JointSplit split = JointRuptureExperimentalIMR.buildJointSplit(surf, surf.getSectionsList());

		List<? extends RuptureSurface> subSurfs = surf.getSurfaceList();
		CompoundSurface crustal = (CompoundSurface) split.crustalRup.getRuptureSurface();
		CompoundSurface interfce = (CompoundSurface) split.interfaceRup.getRuptureSurface();

		assertEquals(NUM_CRUSTAL, crustal.getSurfaceList().size());
		assertEquals(subSurfs.size() - NUM_CRUSTAL, interfce.getSurfaceList().size());
		for (int i = 0; i < subSurfs.size(); i++) {
			if (i < NUM_CRUSTAL) {
				assertSame("crustal sub-surface " + i, subSurfs.get(i), crustal.getSurfaceList().get(i));
			} else {
				assertSame("interface sub-surface " + i, subSurfs.get(i),
						interfce.getSurfaceList().get(i - NUM_CRUSTAL));
			}
		}
	}

	/** The areas are the summed sub-surface areas, and each half gets its own region's magnitude scaling. */
	@Test
	public void testSplitAreasAndMagnitudes() {
		CompoundSurface surf = surfaceForRup(0);
		JointSplit split = JointRuptureExperimentalIMR.buildJointSplit(surf, surf.getSectionsList());

		double crustalArea = 0d;
		double interfaceArea = 0d;
		for (int i = 0; i < surf.getSurfaceList().size(); i++) {
			double area = surf.getSurfaceList().get(i).getArea();
			if (i < NUM_CRUSTAL) {
				crustalArea += area;
			} else {
				interfaceArea += area;
			}
		}
		assertEquals(crustalArea, split.crustalArea, 1e-9);
		assertEquals(interfaceArea, split.interfaceArea, 1e-9);

		assertEquals(JointRuptureExperimentalIMR.getCrustalMag(crustalArea), split.crustalRup.getMag(), 1e-9);
		assertEquals(JointRuptureExperimentalIMR.getInterfaceMag(interfaceArea), split.interfaceRup.getMag(), 1e-9);

		// each half keeps the rake of its own region rather than a blend of the two
		assertEquals(180d, split.crustalRup.getAveRake(), 1e-9);
		assertEquals(90d, split.interfaceRup.getAveRake(), 1e-9);
	}

	/**
	 * The split is site independent, so it is built once per rupture surface and reused. Without the cache it would
	 * rebuild two {@link CompoundSurface}s for every site.
	 */
	@Test
	public void testSplitIsCachedPerSurface() {
		JointRuptureExperimentalIMR gmm = new JointRuptureExperimentalIMR();
		CompoundSurface surf = surfaceForRup(0);

		JointSplit first = gmm.getJointSplit(surf, surf.getSectionsList());
		JointSplit second = gmm.getJointSplit(surf, surf.getSectionsList());
		assertSame("split was rebuilt for the same surface", first, second);
		assertSame(first.crustalRup, second.crustalRup);
		assertSame(first.interfaceRup, second.interfaceRup);
	}

	/** The cache is keyed per surface, so a different rupture gets its own split. */
	@Test
	public void testDifferentSurfacesGetTheirOwnSplit() {
		JointRuptureExperimentalIMR gmm = new JointRuptureExperimentalIMR();

		// two separately built surfaces over the same sections are different objects, and each gets its own entry
		FaultSystemRupSet other = FaultSystemRupSet.builder(
				rupSet.getFaultSectionDataList(), List.of(List.of(0, 1, 2, 3)))
				.rupMags(new double[] { 8d })
				.build();
		CompoundSurface surf = surfaceForRup(0);
		CompoundSurface otherSurf = (CompoundSurface) other.getSurfaceForRupture(0, SPACING);
		assertNotSame(surf, otherSurf);

		assertNotSame(gmm.getJointSplit(surf, surf.getSectionsList()),
				gmm.getJointSplit(otherSurf, otherSurf.getSectionsList()));
	}

	/** Splitting is only meaningful for a rupture that spans both regions; a single-region one is a bug. */
	@Test
	public void testSplitRejectsNonJointRupture() {
		CompoundSurface crustalOnly = surfaceForRup(1);
		try {
			JointRuptureExperimentalIMR.buildJointSplit(crustalOnly, crustalOnly.getSectionsList());
			fail("expected a non-joint rupture to be rejected");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not joint"));
		}
	}
}
