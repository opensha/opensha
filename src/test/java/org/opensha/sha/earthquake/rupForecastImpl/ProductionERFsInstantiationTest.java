package org.opensha.sha.earthquake.rupForecastImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DevStatus;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_AreaForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_MultiSourceForecast;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests the following criteria for each {@link DevStatus}.PRODUCTION ERF.
 * 
 * <ul>
 * <li> ERF instantiation is not null
 * <li> updateForecast() returns successfully
 * <li> does not reuse the same source objects for multiple sources (for thread safety)
 * <li> does not reuse the same rupture objects/surfaces for multiple ruptures in a source (for thread safety)
 * </ul>
 * 
 * If the erf is a regular {@link ERF} the following criteria is checked:
 * <ul>
 * <li> has at least 1 source
 * <li> each source is not null
 * <li> each source has a non null source surface with at least 1 point
 * (exceptions are allowed on getSourceSurface if it is a point source)
 * <li> each source has a non null name
 * <li> each source has a non null {@link TectonicRegionType}
 * <li> each source has at least 1 rupture
 * <li> each rupture is not null
 * <li> each rupture's surface is not null and has at least 1 point
 * <li> each rupture's magnitude and probability are not NaN
 * <li> each rupture's magnitude is within 0<mag<=12
 * <li> each rupture's probability is within 0<=prob<=1
 * </ul>
 * 
 * If the erf is an {@link EpistemicListERF} the following criteria is checked:
 * <ul>
 * <li> has at least one ERF
 * <li> each ERF conforms to the critera above for regular ERFs.
 * </ul>
 * 
 * @author Kevin
 *
 */
@RunWith(Parameterized.class)
public class ProductionERFsInstantiationTest {

	@Parameters
	public static Collection<ERF_Ref[]> data() {
		Set<ERF_Ref> set = ERF_Ref.get(true, DevStatus.PRODUCTION);
		ArrayList<ERF_Ref[]> ret = new ArrayList<ERF_Ref[]>();
		for (ERF_Ref erf : set) {
			ERF_Ref[] array = { erf };
			ret.add(array);
		}
		return ret;
	}

	private ERF_Ref erfRef;

	public ProductionERFsInstantiationTest(ERF_Ref erfRef) {
		this.erfRef = erfRef;
	}

	private void validateRupture(int sourceID, int rupID, ProbEqkRupture rupture) {
		String rupStr = erfRef+": source "+sourceID+", rup "+rupID;
		assertNotNull(rupStr+" is null!", rupture);
		RuptureSurface surface = rupture.getRuptureSurface();
		assertNotNull(rupStr+" surface is null!", surface);
		assertTrue(rupStr+" surface has zero points!", surface.getEvenlyDiscritizedListOfLocsOnSurface().size()>0l);
		double prob = rupture.getProbability();
		assertFalse(rupStr+" probability is NaN", Double.isNaN(prob));
		assertTrue(rupStr+" probability is <0", prob>=0d);
		assertTrue(rupStr+" probability is >1", prob<=1d);
		double mag = rupture.getMag();
		assertFalse(rupStr+" magnitude is NaN", Double.isNaN(mag));
		assertTrue(rupStr+" magnitude is <=0", mag>0d);
		assertTrue(rupStr+" magnitude is >12", mag<=12d);
	}

	private void validateSourceSurface(String srcStr, ProbEqkSource source) {
		try {
			RuptureSurface sourceSurface = source.getSourceSurface();
			assertNotNull(srcStr+" surface is null!", sourceSurface);
			assertTrue(srcStr+" surface has zero points!", sourceSurface.getEvenlyDiscritizedListOfLocsOnSurface().size()>0l);
		} catch (RuntimeException e) {
			// if there was an error, only throw it if not a point source
			// dirty, I know
			if (ClassUtils.getClassNameWithoutPackage(source.getClass()).toLowerCase().contains("point"))
				return;
			ProbEqkRupture rup1 = source.getRupture(0);
			if (rup1.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface().size() != 1l)
				throw e;
		}
	}

	private void validateSource(int sourceID, ProbEqkSource source) {
		String srcStr = erfRef+": source "+sourceID;
		assertNotNull(srcStr+" is null!", source);
		assertNotNull(srcStr+" name is null!", source.getName());
		srcStr += " ("+source.getName()+")";
		assertTrue(srcStr+" has no ruptures!", source.getNumRuptures() > 0);
		validateSourceSurface(srcStr, source);
		assertNotNull(srcStr+" tectonic region type is null!", source.getTectonicRegionType());
		for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
			ProbEqkRupture rupture = source.getRupture(rupID);
			validateRupture(sourceID, rupID, rupture);
		}
	}

	private void validateERF(ERF erf) {
		assertTrue("ERF "+erf.getName()+" has no sources!", erf.getNumSources() > 0);
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			validateSource(sourceID, source);
		}
	}

	@Test
	public void testInstantiation() {
		BaseERF baseERF = erfRef.instance();
		assertNotNull("ERF "+baseERF.getName()+" is null!", baseERF);

		// several PEER and simple ERFs require user input without which
		// update forecast will throw an exception.
		if (ERFsToSkip.contains(erfRef.toString())) return;

		baseERF.updateForecast();
		if (baseERF instanceof ERF) {
			ERF erf = (ERF)baseERF;
			validateERF(erf);
		} else if (baseERF instanceof EpistemicListERF) {
			EpistemicListERF listERF = (EpistemicListERF)baseERF;
			assertTrue(erfRef+" is epistemic, but contains zero ERFs!", listERF.getNumERFs()>0);
			int numERFs = listERF.getNumERFs();
			if (numERFs < 10) {
				for (int erfID=0; erfID<numERFs; erfID++) {
					ERF erf = listERF.getERF(erfID);
					validateERF(erf);
				}
			} else {
				// if there are more than 5 ERFs it will take too long to do the test, so just use 5 random ones
				Random r = new Random(System.currentTimeMillis());
				for (int i=0; i<5; i++) {
					int erfID = r.nextInt(numERFs);
					validateERF(listERF.getERF(erfID));
				}
			}
		}
	}

	private final static String TREAT_BACK_SEIS_AS = "Treat Background Seismicity As";

	public static List<String> getBackSeisTypes(ERF erf) {
		ParameterList params = erf.getAdjustableParameterList();
		if (!params.containsParameter(TREAT_BACK_SEIS_AS)) {
			ArrayList<String> strings = new ArrayList<String>();
			strings.add(null);
			return strings;
		}
		Parameter<String> param = params.getParameter(String.class, TREAT_BACK_SEIS_AS);
		StringConstraint sconst = (StringConstraint) param.getConstraint();
		return sconst.getAllowedStrings();
	}

	@Test
	public void testThreadSourceSafety() {
		// several PEER and simple ERFs require user input without which
		// update forecast will throw an exception.
		if (ERFsToSkip.contains(erfRef.toString())) return;

		BaseERF baseERF = erfRef.instance();
		if (baseERF instanceof ERF) {
			doThreadSourceSafetyTest((ERF)baseERF);
		} else if (baseERF instanceof EpistemicListERF) {
			EpistemicListERF listERF = (EpistemicListERF)baseERF;
			int numERFs = listERF.getNumERFs();
			if (numERFs < 10) {
				for (int erfID=0; erfID<numERFs; erfID++) {
					ERF erf = listERF.getERF(erfID);
					doThreadSourceSafetyTest(erf);
				}
			} else {
				// if there are more than 5 ERFs it will take too long to do the test, so just use 5 random ones
				Random r = new Random(System.currentTimeMillis());
				for (int i=0; i<2; i++) {
					int erfID = r.nextInt(numERFs);
					doThreadSourceSafetyTest(listERF.getERF(erfID));
				}
			}
		}
	}

	@Test
	public void testThreadRupSafety() {
		// several PEER and simple ERFs require user input without which
		// update forecast will throw an exception.
		if (ERFsToSkip.contains(erfRef.toString())) return;

		try {
			BaseERF baseERF = erfRef.instance();
			if (baseERF instanceof ERF) {
				doThreadRupSafetyTest((ERF)baseERF);
			} else if (baseERF instanceof EpistemicListERF) {
				EpistemicListERF listERF = (EpistemicListERF)baseERF;
				int numERFs = listERF.getNumERFs();
				if (numERFs < 10) {
					for (int erfID=0; erfID<numERFs; erfID++) {
						ERF erf = listERF.getERF(erfID);
						doThreadRupSafetyTest(erf);
					}
				} else {
					// if there are more than 5 ERFs it will take too long to do the test, so just use 5 random ones
					Random r = new Random(System.currentTimeMillis());
					for (int i=0; i<2; i++) {
						int erfID = r.nextInt(numERFs);
						doThreadRupSafetyTest(listERF.getERF(erfID));
					}
				}
			}
		} catch (AssertionError e) {
			String sourceClass = e.getMessage();
			sourceClass = sourceClass.substring(sourceClass.indexOf("=[")+2);
			sourceClass = sourceClass.substring(0, sourceClass.indexOf("]"));
			System.out.println("* "+erfRef.toString()+" ("+ClassUtils.getClassNameWithoutPackage(
					erfRef.getERFClass())+")");
			System.out.println(" * Source class: "+sourceClass);
			
			if (!offendingSources.contains(sourceClass))
				offendingSources.add(sourceClass);
			if (erfRef == ERF_Ref.YUCCA_MOUNTAIN_LIST) {
				System.out.println("Offending sources:");
				for (String source : offendingSources)
					System.out.println("* "+source);
			}
			throw e;
		}
	}
	
	private static ArrayList<String> offendingSources = new ArrayList<String>();

	private void doThreadSourceSafetyTest(ERF erf) {
		String name = erf.getName()+" ("+erf.getClass().getName()+")";

		// try to enable background seismicity first
		if (erf.getAdjustableParameterList().containsParameter("Background Seismicity")) {
			try {
				erf.setParameter("Background Seismicity", "Include");
			} catch (Exception e) {
				System.out.println("Tried setting background seismicity but failed for: "+name);
			}
		}
		for (String backSeisType : getBackSeisTypes(erf)) {
			if (backSeisType != null)
				erf.setParameter(TREAT_BACK_SEIS_AS, backSeisType);
			System.out.println("Testing Sources "+name+": "+backSeisType);
			erf.updateForecast();
			// first check that it's not reusing sources
			for (int sourceID=1; sourceID<erf.getNumSources(); sourceID++) {
				ProbEqkSource src0 = erf.getSource(sourceID-1);
				String name0 = src0.getName();
				RuptureSurface surf0 = null;
				boolean doSurf0EqualsTest = false;
				try {
					surf0 = src0.getSourceSurface();
					doSurf0EqualsTest = surf0 != null && surf0.equals(src0.getSourceSurface());
				} catch (Exception e) {}
				int num0 = src0.getNumRuptures();
				ProbEqkSource src1 = erf.getSource(sourceID);
				String name1 = src1.getName();
				RuptureSurface surf1 = null;
				boolean doSurf1EqualsTest = false;
				try {
					surf1 = src1.getSourceSurface();
					doSurf1EqualsTest = surf1 != null && surf1.equals(src1.getSourceSurface());
				} catch (Exception e) {}
				int num1 = src1.getNumRuptures();
				assertNotSame(name+": reusing sources ("+(sourceID-1)+"=="+sourceID+")", src0, src1);
				assertEquals(name+": get changed source rupture count", num0, src0.getNumRuptures());
				assertEquals(name+": get changed source name", name0, src0.getName());
				if (doSurf0EqualsTest)
					assertEquals(name+": get changed source surface", surf0, src0.getSourceSurface());
				erf.getSource(sourceID-1);
				assertNotSame(name+": reusing sources ("+(sourceID-1)+"=="+sourceID+")", src0, src1);
				assertEquals(name+": get changed source rupture count", num1, src1.getNumRuptures());
				assertEquals(name+": get changed source name", name1, src1.getName());
				if (doSurf1EqualsTest)
					assertEquals(name+": get changed source surface", surf1, src1.getSourceSurface());
			}
		}
	}

	private void doThreadRupSafetyTest(ERF erf) {
		String name = erf.getName();

		// try to enable background seismicity first
		if (erf.getAdjustableParameterList().containsParameter("Background Seismicity")) {
			try {
				erf.setParameter("Background Seismicity", "Include");
			} catch (Exception e) {
				System.out.println("Tried setting background seismicity but failed for: "+name);
			}
		}
		for (String backSeisType : getBackSeisTypes(erf)) {
			if (backSeisType != null)
				erf.setParameter(TREAT_BACK_SEIS_AS, backSeisType);
			System.out.println("Testing Ruptures "+name+": "+backSeisType);
			erf.updateForecast();

			// now check that it's not reusing ruptures
			for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
				ProbEqkSource src = erf.getSource(sourceID);
				String srcStr = sourceID+" ("+src.getName()+"=["+src.getClass().getName()+"])";
				for (int rupID=1; rupID<src.getNumRuptures(); rupID++) {
					ProbEqkRupture rup0 = src.getRupture(rupID-1);
					double mag0 = rup0.getMag();
					double prob0 = rup0.getProbability();
					Location hypo0 = rup0.getHypocenterLocation();
					String info0 = rup0.getInfo();
					RuptureSurface surf0 = rup0.getRuptureSurface();
					// rup surface may be generated on the fly
					boolean doSurf0Test = surf0 != null && surf0.equals(rup0.getRuptureSurface());

					ProbEqkRupture rup1 = src.getRupture(rupID);
					assertNotSame(name+": reusing ruptures for source "+srcStr, rup0, rup1);
					assertEquals(name+": get changed mag for source "+srcStr, mag0, rup0.getMag(), 1e-10);
					assertEquals(name+": get changed prob for source "+srcStr, prob0, rup0.getProbability(), 1e-10);
					if (hypo0 != null)
						assertEquals(name+": get changed hypo for source "+srcStr, hypo0, rup0.getHypocenterLocation());
					if (info0 != null)
						assertEquals(name+": get changed info for source "+srcStr, info0, rup0.getInfo());
					if (doSurf0Test)
						assertEquals(name+": get changed info for source "+srcStr, surf0, rup0.getRuptureSurface());

					double mag1 = rup1.getMag();
					double prob1 = rup1.getProbability();
					Location hypo1 = rup1.getHypocenterLocation();
					RuptureSurface surf1 = rup1.getRuptureSurface();
					String info1 = rup1.getInfo();
					boolean doSurf1Test = surf1 != null && surf1.equals(rup1.getRuptureSurface());

					src.getRupture(rupID-1);
					assertNotSame(name+": reusing ruptures for source "+srcStr, rup0, rup1);
					assertEquals(name+": get changed mag for source "+srcStr, mag1, rup1.getMag(), 1e-10);
					assertEquals(name+": get changed prob for source "+srcStr, prob1, rup1.getProbability(), 1e-10);
					if (hypo1 != null)
						assertEquals(name+": get changed hypo for source "+srcStr, hypo1, rup1.getHypocenterLocation());
					if (info1 != null)
						assertEquals(name+": get changed info for source "+srcStr, info1, rup1.getInfo());
					if (doSurf1Test)
						assertEquals(name+": get changed info for source "+srcStr, surf1, rup1.getRuptureSurface());
				}
			}
		}
	}

	private static List<String> ERFsToSkip;
	static {
		ERFsToSkip = new ArrayList<String>();
		ERFsToSkip.add(PEER_AreaForecast.NAME);
		ERFsToSkip.add(PEER_MultiSourceForecast.NAME);
		ERFsToSkip.add(FloatingPoissonFaultERF.NAME);
		ERFsToSkip.add(PoissonFaultERF.NAME);
	}

}
