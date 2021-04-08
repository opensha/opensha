package scratch.kevin.ucerf3;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.U3CoulombJunctionFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;

public class U3CoulombVsDistance {

	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		CoulombRates cff = CoulombRates.loadUCERF3CoulombRates(FaultModels.FM3_1);
		List<? extends FaultSection> subSects = DeformationModels.loadSubSects(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		distAzCalc.loadCacheFile(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_dist_az_cache.csv"));
		
		File outputDir = new File("/tmp");
		
		double minTestDist = 4.5d;
		int numAbove = 0;
		double maxPAbove = 0d;
		double maxDAbove = 0d;
		
		DefaultXY_DataSet dScatter = new DefaultXY_DataSet();
		DefaultXY_DataSet pScatter = new DefaultXY_DataSet();
		HashSet<IDPairing> processed = new HashSet<>();
		for (CoulombRatesRecord rec : cff.values()) {
			IDPairing pair = rec.getPairing();
			if (pair.getID1() > pair.getID2())
				pair = pair.getReversed();
			if (processed.contains(pair) || processed.contains(pair.getReversed()))
				continue;
			processed.add(pair);
			double dist = distAzCalc.getDistance(pair.getID1(), pair.getID2());
			double dcff = rec.getCoulombStressChange();
			double pdcff = rec.getCoulombStressProbability();
			dScatter.set(dist, dcff);
			pScatter.set(dist, pdcff);
			if (dist >= minTestDist) {
				numAbove++;
				maxPAbove = Math.max(maxPAbove, pdcff);
				maxDAbove = Math.max(maxDAbove, dcff);
			}
		}
		
		System.out.println("There are "+numAbove+" values with R>="+(float)minTestDist);
		System.out.println("\tMax DCFF:\t"+(float)maxDAbove);
		System.out.println("\tMax PDCFF:\t"+(float)maxPAbove);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(dScatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.BOLD_X, 2f, Color.BLACK));
		
		PlotSpec dSpec = new PlotSpec(funcs, chars, "DCFF vs Distance", "Distance (km)", "DCFF (bar)");
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(dSpec, false, false, new Range(0d, 10d), new Range(0d, 10d));
		PlotUtils.writePlots(outputDir, "dcff", gp, 800, 600, true, false, false);
		
		funcs = new ArrayList<>();
		funcs.add(pScatter);
		
		PlotSpec pSpec = new PlotSpec(funcs, chars, "PDCFF vs Distance", "Distance (km)", "PDCFF");
		gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(pSpec, false, false, new Range(0d, 10d), new Range(0d, 0.2d));
		PlotUtils.writePlots(outputDir, "pdcff", gp, 800, 600, true, false, false);
		
		// now reproduce, but go up to 10km
		
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
				subSects, 1d, lambda, mu, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 0d);
		
		double testDist = 10d;
		DistCutoffClosestSectClusterConnectionStrategy connectionStrategy =
				new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, testDist);
		
		CoulombRatesTester coulombTester = new CoulombRatesTester(
				TestType.COULOMB_STRESS, 0.04, 0.04, 1.25d, true, true);
		U3CoulombJunctionFilter filter = new U3CoulombJunctionFilter(coulombTester, cff);
		filter.setFallbackCalculator(calc, connectionStrategy);
		
		HashSet<IDPairing> pairings = new HashSet<>(processed);
		// add up to 10km
		for (Jump jump : connectionStrategy.getAllPossibleJumps()) {
			IDPairing pair = new IDPairing(jump.fromSection.getSectionId(), jump.toSection.getSectionId());
			if (pair.getID1() > pair.getID2())
				pair = pair.getReversed();
			pairings.add(pair);
		}
		Map<IDPairing, Future<CoulombRatesRecord>> recordFutures = new HashMap<>();
		ExecutorService exec = Executors.newFixedThreadPool(32);
		for (IDPairing pair : pairings) {
			recordFutures.put(pair, exec.submit(new Callable<CoulombRatesRecord>() {

				@Override
				public CoulombRatesRecord call() throws Exception {
					return filter.calculateFallbackCoulombRates(pair);
				}
			}));
		}
		System.out.println("Waiting on "+recordFutures.size()+" CFF futures...");
		
		minTestDist = 5d;
		numAbove = 0;
		maxPAbove = 0d;
		maxDAbove = 0d;
		
		dScatter = new DefaultXY_DataSet();
		pScatter = new DefaultXY_DataSet();
		for (IDPairing pair : recordFutures.keySet()) {
			CoulombRatesRecord rec = recordFutures.get(pair).get();
			double dist = distAzCalc.getDistance(pair.getID1(), pair.getID2());
			double dcff = rec.getCoulombStressChange();
			double pdcff = rec.getCoulombStressProbability();
			dScatter.set(dist, dcff);
			pScatter.set(dist, pdcff);
			if (dist >= minTestDist) {
				numAbove++;
				maxPAbove = Math.max(maxPAbove, pdcff);
				maxDAbove = Math.max(maxDAbove, dcff);
			}
		}
		exec.shutdown();
		
		System.out.println("There are "+numAbove+" values with R>="+(float)minTestDist);
		System.out.println("\tMax DCFF:\t"+(float)maxDAbove);
		System.out.println("\tMax PDCFF:\t"+(float)maxPAbove);
		
		funcs = new ArrayList<>();
		chars = new ArrayList<>();
		
		funcs.add(dScatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.BOLD_X, 2f, Color.BLUE));
		
		dSpec = new PlotSpec(funcs, chars, "DCFF vs Distance", "Distance (km)", "DCFF (bar)");
		gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(dSpec, false, false, new Range(0d, 10d), new Range(0d, 10d));
		PlotUtils.writePlots(outputDir, "dcff_new", gp, 800, 600, true, false, false);
		
		funcs = new ArrayList<>();
		funcs.add(pScatter);
		
		pSpec = new PlotSpec(funcs, chars, "PDCFF vs Distance", "Distance (km)", "PDCFF");
		gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(pSpec, false, false, new Range(0d, 10d), new Range(0d, 0.2d));
		PlotUtils.writePlots(outputDir, "pdcff_new", gp, 800, 600, true, false, false);
	}
	
}
