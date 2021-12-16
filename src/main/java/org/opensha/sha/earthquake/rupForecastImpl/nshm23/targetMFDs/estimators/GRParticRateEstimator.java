package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.data.Range;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.SectParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * A priori estimation of section participation rates and rupture rates consistent with
 * {@link SupraSeisBValInversionTargetMFDs} implementation. Can be used to weight rate-based
 * {@link JumpProbabilityConstraint} implementations, and as a variable perturbation basis in the inversion.
 * 
 * @author kevin
 *
 */
public class GRParticRateEstimator implements SectParticipationRateEstimator {
	
	private EvenlyDiscretizedFunc refFunc;
	
	private FaultSystemRupSet rupSet;
	private double supraSeisB;
	
	private double[] estParticRates;
	private double[] estRupRates;

	public GRParticRateEstimator(FaultSystemRupSet rupSet, double supraSeisB) {
		this.rupSet = rupSet;
		this.supraSeisB = supraSeisB;
		
		refFunc = SupraSeisBValInversionTargetMFDs.buildRefXValues(rupSet);
		
		estParticRates = new double[rupSet.getNumSections()];
		estRupRates = new double[rupSet.getNumRuptures()];
		
		SectSlipRates slipRates = rupSet.requireModule(SectSlipRates.class);
		
		for (int s=0; s<estParticRates.length; s++) {
			double slipRate = slipRates.getSlipRate(s);
			double sectArea = rupSet.getAreaForSection(s);
			double moRate = FaultMomentCalc.getMoment(sectArea, slipRate);
			ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
			List<Integer> rups = new ArrayList<>();
			List<Double> rupMags = new ArrayList<>();
			int[] rupsPerBin = new int[refFunc.size()];
			for (int r : rupSet.getRupturesForSection(s)) {
				double mag = rupSet.getMagForRup(r);
				if (modMinMags != null && modMinMags.isBelowSectMinMag(s, mag))
					continue;
				rups.add(r);
				rupMags.add(mag);
				rupsPerBin[refFunc.getClosestXIndex(mag)]++;
			}
			// nulceation GR
			IncrementalMagFreqDist nuclGR = DataSectNucleationRateEstimator.buildGRFromBVal(
					refFunc, rupMags, supraSeisB, moRate, true);
			
			// spread to all ruptures evenly to get partic rate
			double calcRate = 0d;
			for (int r=0; r<rups.size(); r++) {
				int bin = nuclGR.getClosestXIndex(rupMags.get(r));
				/// this is a nucleation rate
				double nuclRate = nuclGR.getY(bin)/(double)rupsPerBin[bin];
				// turn back into participation rate
				double particRate = nuclRate*rupSet.getAreaForRup(rups.get(r))/sectArea;
				// adjust for visibility
				calcRate += particRate;
				
				// estimated rup rates should sum nucleation rates
				estRupRates[rups.get(r)] += nuclRate;
			}
			estParticRates[s] = calcRate;
		}
	}

	@Override
	public double[] estimateSectParticRates() {
		return estParticRates;
	}

	@Override
	public double estimateSectParticRate(int sectionIndex) {
		return estParticRates[sectionIndex];
	}
	
	public double[] estimateRuptureRates() {
		return estRupRates;
	}
	
	public static void main(String[] args) throws IOException {
//		double supraB = 0.8;
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
//				+ "2021_12_08-coulomb-fm31-ref_branch-uniform-nshm23_draft_default-supra_b_0.8-2h/run_0/solution.zip"));
		double supraB = 0.0;
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_19-reproduce-ucerf3-ref_branch-uniform-nshm23_draft-supra_b_sweep-u3_supra_reduction"
				+ "-no_paleo-no_parkfield-mfd_wt_10-sect_wt_0.5-skipBelow-2h/"
				+ "2021_11_19-reproduce-ucerf3-ref_branch-uniform-nshm23_draft-supra_b_"+(float)supraB
				+ "-u3_supra_reduction-no_paleo-no_parkfield-mfd_wt_10-sect_wt_0.5-skipBelow-2h/mean_solution.zip"));
		
		File outputDir = new File("/tmp");
		
		double[] solPartics = sol.calcParticRateForAllSects(0d, Double.POSITIVE_INFINITY);
		
		List<SectParticipationRateEstimator> estimators = new ArrayList<>();
		List<String> estimatorNames = new ArrayList<>();
		List<String> estimatorPrefixes = new ArrayList<>();
		
		GRParticRateEstimator grEst = new GRParticRateEstimator(sol.getRupSet(), supraB);
		
		estimators.add(grEst);
		estimatorNames.add("G-R estimate");
		estimatorPrefixes.add("gr");
		
		estimators.add(new JumpProbabilityConstraint.InitialModelParticipationRateEstimator(sol.getRupSet(),
				grEst.estimateRuptureRates()));
		estimatorNames.add("G-R rate est");
		estimatorPrefixes.add("gr_rate");
		
		double[] prevRateEst = Inversions.getDefaultVariablePerturbationBasis(sol.getRupSet());
		estimators.add(new JumpProbabilityConstraint.InitialModelParticipationRateEstimator(sol.getRupSet(),
				prevRateEst));
		estimatorNames.add("Smooth starting model estimate");
		estimatorPrefixes.add("smooth_start");
		
		System.out.println("Prev rate est sum: "+StatUtils.sum(prevRateEst));
		System.out.println("New rate est sum: "+StatUtils.sum(grEst.estRupRates));
		
		for (int i=0; i<estimators.size(); i++) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			SectParticipationRateEstimator estimator = estimators.get(i);
			
			for (int s=0; s<solPartics.length; s++)
				xy.set(solPartics[s], estimator.estimateSectParticRate(s));
			
			List<XY_DataSet> funcs = List.of(xy);
			List<PlotCurveCharacterstics> chars = List.of(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
			
			PlotSpec plot = new PlotSpec(funcs, chars, "Participation Rate Comparison", "Acutal Solution", estimatorNames.get(i));
			
			Range range = new Range(Math.min(xy.getMinX(), xy.getMinY()), Math.max(xy.getMaxX(), xy.getMaxY()));
			range = new Range(Math.pow(10, Math.floor(Math.log10(range.getLowerBound()))),
					Math.pow(10, Math.ceil(Math.log(range.getUpperBound()))));
			
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(plot, true, true, range, range);
			
			String prefix = "partic_rate_vs_"+estimatorPrefixes.get(i);
			
			PlotUtils.writePlots(outputDir, prefix, gp, 800, false, true, false, false);
		}
	}

}
