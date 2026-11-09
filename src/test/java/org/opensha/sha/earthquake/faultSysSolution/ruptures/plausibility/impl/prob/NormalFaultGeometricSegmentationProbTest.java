package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.NormalFaultGeometricSegmentationProb.CoefficientSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.NormalFaultGeometricSegmentationProb.Probabilities;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Measurements;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class NormalFaultGeometricSegmentationProbTest {

	@Test
	public void testPublishedEquationsAndUnits() {
		double[][] coefficients = {{10.446978, 3.405079, 21.715169, 5.611446, 1.876476, 0.0082811},
				{12.859280, 3.978794, 5.886363, 1.845861, 2.856840, 0.034269}};
		for (CoefficientSet set : CoefficientSet.values()) {
			NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(set);
			double[] c = coefficients[set.ordinal()];
			for (double km : new double[] {0.001, 0.1, 1d, 5d, 15d}) {
				assertEquals(1d/(1d+Math.exp(-(c[0]-c[1]*Math.log10(km*1000d)))), model.calcStepoverProbability(km), 1e-14);
				assertEquals(1d/(1d+Math.exp(-(c[2]-c[3]*Math.log10(km*1000d)))), model.calcGapProbability(km), 1e-14);
			}
			assertEquals(1d/(1d+Math.exp(-(c[4]-c[5]*30d))), model.calcBendProbability(30d), 1e-14);
			assertEquals(0.5, model.calcStepoverProbability(Math.pow(10d, c[0]/c[1])/1000d), 1e-14);
		}
	}

	@Test
	public void testZeroLimitsAndExtremeDistances() {
		for (CoefficientSet set : CoefficientSet.values()) {
			NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(set);
			assertEquals(1d, model.calcGapProbability(0d), 0d);
			assertEquals(1d, model.calcStepoverProbability(0d), 0d);
			assertTrue(model.calcBendProbability(0d) < 1d);
			for (double distance : new double[] {Double.MIN_VALUE, Double.MAX_VALUE}) {
				assertTrue(Double.isFinite(model.calcGapProbability(distance)));
				assertTrue(Double.isFinite(model.calcStepoverProbability(distance)));
			}
		}
	}

	@Test
	public void testNonOverlapMultipliesGapOffsetAndBend() {
		NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM);
		Measurements m = new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, 0, 0, 5), section(1, 1, 3, 7, 6, 12));
		Probabilities p = model.calcProbabilities(m);
		assertTrue(p.gapApplied);
		assertTrue(p.stepoverApplied);
		assertTrue(p.bendApplied);
		assertEquals(m.facingEndGapKm, p.gapLengthKm, 0d);
		assertEquals(m.facingEndOffsetKm, p.stepoverDistanceKm, 0d);
		assertEquals(model.calcGapProbability(m.facingEndGapKm)*model.calcStepoverProbability(m.facingEndOffsetKm)
				*model.calcBendProbability(m.bendAngleDegrees), p.combinedProbability, 1e-14);
	}

	@Test
	public void testOverlapHasNoGapPenalty() {
		NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM);
		Measurements m = new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, 0, 0, 10), section(1, 1, 3, 2, 3, 8));
		Probabilities p = model.calcProbabilities(m);
		assertFalse(p.gapApplied);
		assertEquals(1d, p.gapProbability, 0d);
		assertEquals(model.calcStepoverProbability(m.stepoverConnector.lengthKm), p.combinedProbability, 1e-14);
	}

	@Test
	public void testInternalBoundaryOnlyAppliesQualifyingBend() {
		NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(CoefficientSet.MANUAL_60K);
		Measurements m = new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, 0, 0, 5), section(1, 0, 0, 5, 3, 10));
		Probabilities p = model.calcProbabilities(m);
		assertTrue(p.bendApplied);
		assertFalse(p.gapApplied);
		assertFalse(p.stepoverApplied);
		assertEquals(model.calcBendProbability(m.bendAngleDegrees), p.combinedProbability, 0d);
		m = new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, 0, 0, 5), section(1, 0, 0, 5, 0, 10));
		assertEquals(1d, model.calcProbabilities(m).combinedProbability, 0d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testAmbiguousBetweenParentGeometryIsRejected() {
		new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM).calcProbabilities(
				new FaultTraceSegmentationGeometry(10).measure(section(0, 0, 0, -5, 0, 5), section(1, 1, -5, 0, 5, 0)));
	}

	@Test
	public void testAmbiguousAxisDoesNotSuppressInternalBend() {
		NormalFaultGeometricSegmentationProb model = new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM);
		Probabilities p = model.calcProbabilities(new FaultTraceSegmentationGeometry(10).measure(
				section(0, 0, 0, -5, 0, 5), section(1, 0, -5, 0, 5, 0)));
		assertEquals(model.calcBendProbability(90d), p.combinedProbability, 1e-14);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNegativeDistanceRejected() {
		new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM).calcGapProbability(-1d);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testInvalidAngleRejected() {
		new NormalFaultGeometricSegmentationProb(CoefficientSet.AUTOMATED_5KM).calcBendProbability(Double.NaN);
	}

	private static FaultSection section(int id, int parent, double... xy) {
		FaultSectionPrefData section = new FaultSectionPrefData();
		section.setSectionId(id);
		section.setParentSectionId(parent);
		FaultTrace trace = new FaultTrace("test");
		Location origin = new Location(0, 0);
		for (int i=0; i<xy.length; i+=2)
			trace.add(LocationUtils.location(origin, Math.atan2(xy[i], xy[i+1]), Math.hypot(xy[i], xy[i+1])));
		section.setFaultTrace(trace);
		return section;
	}
}
