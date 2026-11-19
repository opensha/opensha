package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;
import static org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.ReceiverDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;
import org.opensha.sha.simulators.stiffness.patches.TriangularPatch;

public class AreaWeightedStiffnessTest {
	@Test
	public void weightedMedianAndSignsRespectArea() {
		double[] values = { -10, 2, 8 }, weights = { .8, .1, .1 };
		assertEquals(-10, MEDIAN.calculate(values, weights), 0);
		assertEquals(.2, FRACT_POSITIVE.calculate(values, weights), 1e-15);
		assertEquals(3, COUNT.calculate(values, weights), 0);
		assertEquals(5, MEDIAN.calculate(new double[] { 2, 8 }, new double[] { .5, .5 }), 0);
	}

	@Test
	public void sourceSumIsPhysicalStressAndReceiverStatisticsUseArea() {
		// Areas in reference-patch units: sources .25/.75, receiver 2.
		var raw = new ReceiverDistribution(0, 2, new double[] { 2, -1 }, new double[] { .5, 1.5 }, 2, true, 2);
		assertEquals(-.25, raw.calculate(SUM), 0);
		assertEquals(.25, raw.calculate(NUM_POSITIVE), 0);
		assertEquals(.25, raw.calculate(FRACT_POSITIVE), 0);
		assertEquals(2, raw.calculate(COUNT), 0);
		var reduced = SUM.aggregate(0, new ReceiverDistribution[] { raw });
		assertEquals(-.5, SUM.get(reduced), 0); // physical stress times receiver area
		assertEquals(-.25, MEAN.get(reduced), 0);
	}

	@Test
	public void refinementDoesNotBiasAnySampleStatistic() {
		var coarse = new ReceiverDistribution(0, 2, new double[] { 2, -1 }, new double[] { .25, .75 }, 1, true, 1);
		var refined = new ReceiverDistribution(0, 4, new double[] { 2, 2, 2, -1 },
				new double[] { .05, .1, .1, .75 }, 1, true, 1);
		for (AggregationMethod method : new AggregationMethod[] { SUM, MEAN, MEDIAN, FRACT_POSITIVE,
				NUM_POSITIVE, NUM_NEGATIVE, GREATER_SUM_MEDIAN, GREATER_MEAN_MEDIAN, MIN, MAX })
			assertEquals(method.name(), coarse.calculate(method), refined.calculate(method), 1e-14);
	}

	private static FaultSection section(int id) {
		FaultTrace trace = new FaultTrace("section " + id);
		trace.add(new Location(34, -118));
		trace.add(new Location(34.1, -118));
		return (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] { FaultSection.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "getSectionId":
						return id;
					case "getSectionName":
					case "getName":
						return "section " + id;
					case "getFaultTrace":
						return trace;
					case "getArea":
						return 4e6;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					case "toString":
						return "section " + id;
					default:
						throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	private static PatchLocation patch(double weight) {
		double length = Math.sqrt(8e6 * weight);
		var triangle = new TriangularPatch(
				new double[][] { { 0, 0, -5000 }, { length, 0, -5000 }, { 0, length, -5000 } }, 0, 0);
		return new PatchLocation(triangle, new Location(34, -118, 5), new Location[3]);
	}

	/** Controlled constant stress densities isolate aggregation from quadrature convergence. */
	private static final class ControlledCalculator extends SubSectStiffnessCalculator {
		final List<PatchLocation> source, receiver;
		final int positiveSourcePieces;

		ControlledCalculator(List<FaultSection> sections, int pieces, boolean refineReceiver) {
			super(sections, 2, 30000, 30000, .5, PatchAlignment.FILL_OVERLAP, 0, PatchGenerationStrategy.TRIANGULAR);
			positiveSourcePieces = pieces;
			source = new ArrayList<>();
			for (int i = 0; i < pieces; i++)
				source.add(patch(.25 / pieces));
			source.add(patch(.75));
			receiver = refineReceiver ? List.of(patch(.2), patch(.4), patch(.4)) : List.of(patch(.2), patch(.8));
		}

		@Override
		public List<PatchLocation> buildPatches(FaultSection section) {
			return section.getSectionId() == 1 ? receiver : source;
		}

		@Override
		public StiffnessDistribution calcStiffnessDistribution(FaultSection s, FaultSection r) {
			double[][][] values = new double[3][receiver.size()][source.size()];
			for (int j = 0; j < receiver.size(); j++)
				for (int i = 0; i < source.size(); i++) {
					double density = j == 0 ? (i < positiveSourcePieces ? 2 : -1) : 1;
					for (int type = 0; type < 3; type++)
						values[type][j][i] = density * source.get(i).patch.area / getReferencePatchArea();
				}
			return new StiffnessDistribution(source, receiver, values, getReferencePatchArea());
		}
	}

	@Test
	public void fullPipelineAndCachePreserveWeightedResultsUnderRefinement() throws Exception {
		var sections = List.of(section(0), section(1));
		for (int pieces : new int[] { 1, 3, 8 })
			for (boolean refineReceiver : new boolean[] { false, true }) {
				var calc = new ControlledCalculator(sections, pieces, refineReceiver);
				var raw = calc.calcStiffnessDistribution(sections.get(0), sections.get(1));
				assertEquals(-.25, raw.receiverAggregate(SUM).get(StiffnessType.CFF)[0][0], 1e-14);
				assertEquals(-.25, raw.receiverAggregate(MEAN).get(StiffnessType.CFF)[0][0], 1e-14);
				assertEquals(.25, raw.receiverAggregate(FRACT_POSITIVE).get(StiffnessType.CFF)[0][0], 1e-14);
				assertEquals(.75, aggregate(calc, SUM, SUM).calc(sections.get(0), sections.get(1)), 1e-14);
				assertEquals(.75, aggregate(calc, SUM, MEAN).calc(sections.get(0), sections.get(1)), 1e-14);
				assertEquals(.8, aggregate(calc, SUM, FRACT_POSITIVE).calc(sections.get(0), sections.get(1)), 1e-14);
				assertEquals(.85, aggregate(calc, FLATTEN, FRACT_POSITIVE).calc(sections.get(0), sections.get(1)),
						1e-14);
				assertEquals(.75, aggregate(calc, FLATTEN, MEAN).calc(sections.get(0), sections.get(1)), 1e-14);
				assertEquals(1, aggregate(calc, FLATTEN, MEDIAN).calc(sections.get(0), sections.get(1)), 1e-14);
				File file = File.createTempFile("triangle-stiffness-", ".csv");
				try {
					calc.getAggregationCache(StiffnessType.CFF).writeCacheFile(file);
					calc.getAggregationCache(StiffnessType.CFF).clear();
					calc.getAggregationCache(StiffnessType.CFF).loadCacheFile(file);
					assertEquals(.85, aggregate(calc, FLATTEN, FRACT_POSITIVE).calc(sections.get(0), sections.get(1)),
							1e-14);
					assertTrue(Files.readString(file.toPath()).contains("Interaction Weight"));
				} finally {
					Files.deleteIfExists(file.toPath());
				}
			}
	}

	@Test
	public void multipleSourcesSumBeforeReceiverSignAndNormalizeByWeightedInteractions() {
		var sections = List.of(section(0), section(1), section(2));
		var calc = new ControlledCalculator(sections, 3, true);
		var net = AggregatedStiffnessCalculator.builder(StiffnessType.CFF, calc).process(SUM).passthrough()
				.receiverSum().process(FRACT_POSITIVE).get();
		assertEquals(.8, net.calc(List.of(sections.get(0), sections.get(2)), List.of(sections.get(1))), 1e-14);
		var positive = AggregatedStiffnessCalculator.builder(StiffnessType.CFF, calc).process(NUM_POSITIVE).process(SUM)
				.process(SUM).process(NORM_BY_COUNT).get();
		assertEquals(.85, positive.calc(List.of(sections.get(0), sections.get(2)), List.of(sections.get(1))), 1e-14);
	}

	private static AggregatedStiffnessCalculator aggregate(SubSectStiffnessCalculator calc,
			AggregationMethod first, AggregationMethod second) {
		return AggregatedStiffnessCalculator.builder(StiffnessType.CFF, calc).process(first).process(second).get();
	}
}
