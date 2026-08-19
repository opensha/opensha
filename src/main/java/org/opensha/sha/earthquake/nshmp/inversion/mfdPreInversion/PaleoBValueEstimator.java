package org.opensha.sha.earthquake.nshmp.inversion.mfdPreInversion;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.TDistribution;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.WeightedContinuousDistribution;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution.DiscretizationType;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils.LocationAverager;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ExclusionaryInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.PosteriorSectionBValueDistributions;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBySectDetailPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBySectDetailPlots.AlongStrikePlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import net.mahdilamb.colormap.Colors;

public class PaleoBValueEstimator {

	/* direct inputs */
	private final ContinuousDistribution priorDist;
	private final EvenlyDiscretizedFunc bVals;
	private final InversionConfigurationFactory factory;

	public enum WeightingType {
		/**
		 * Weight by the (rate-estimated) fraction of ruptures at a section that include the given paleo site. This
		 * quantity will be <=1 for each paleo site, but can exceed 1 when summed across all sites.
		 */
		CONNECTED_FRACTION,
		/**
		 * Connectivity weights that split overlapping rupture contributions among sites. Site weights plus the
		 * background weight sum to unity.
		 */
		OVERLAP_ADJUSTED
	}

	public enum BackgroundDistribution {
		/**
		 * Use the initial generic prior as the background distribution.
		 */
		GENERIC_PRIOR,
		/**
		 * Use the named-fault average, or the parent-section average if the section is not on a named fault.
		 */
		FAULT_AVERAGE,
		/**
		 * Use the equal-weight average across all usable paleo sites.
		 */
		PALEO_AVERAGE,
		/**
		 * Use the named-fault average when available, otherwise use the generic prior.
		 */
		NAMED_FAULT_AVERAGE,
		/**
		 * Use the average across paleo sites on faults with the same slip style.
		 */
		FAULT_STYLE_AVERAGE
	}

	public enum PosteriorCombination {
		/**
		 * Calculates the posterior by adding weighted site log likelihoods to the background distribution.
		 */
		WEIGHTED_SITE_LIKELIHOODS,
		/**
		 * Calculates the posterior as a weighted mixture of site and background distributions.
		 */
		WEIGHTED_SITE_DISTRIBUTIONS
	}

	public enum FaultStyleBins {
		STRIKE_SLIP("Strike-slip") {
			@Override
			public boolean matches(double rake) {
				return (rake < -150d) || (rake > -30d && rake < 30d) || (rake > 150d);
			}
		},
		REVERSE("Reverse") {
			@Override
			public boolean matches(double rake) {
				return rake > 60d && rake < 120d;
			}
		},
		NORMAL("Normal") {
			@Override
			public boolean matches(double rake) {
				return rake > -120d && rake < -60d;
			}
		},
		OBLIQUE("Oblique") {
			@Override
			public boolean matches(double rake) {
				for (FaultStyleBins bin : values()) {
					if (bin != OBLIQUE && bin.matches(rake))
						return false;
				}
				return true;
			}
		};

		private String name;

		private FaultStyleBins(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}

		public abstract boolean matches(double rake);

		public static FaultStyleBins get(double rake) {
			for (FaultStyleBins bin : values())
				if (bin.matches(rake))
					return bin;
			throw new IllegalStateException("No fault style found for rake="+rake);
		}
	}

	/* [potentially-]configurable with defaults */
	private DoubleFunction<Double> logLikelihoodFunction;
	private double connectivityWeightB;
	private WeightingType weightingType = WeightingType.CONNECTED_FRACTION;
	private BackgroundDistribution backgroundDist = BackgroundDistribution.GENERIC_PRIOR;
	private PosteriorCombination posteriorComb = PosteriorCombination.WEIGHTED_SITE_LIKELIHOODS;
	private DiscretizationType interpType = DiscretizationType.INTERPOLATE;
	private boolean likelihoodInRecurrence = true;
	private Function<MFDCalcInputs, InversionTargetMFDs> targetMFDCalc =
			(I) -> {
				try {
					return I.factory.updateRuptureSetForBranch(I.rupSet, I.branch).requireModule(InversionTargetMFDs.class);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			};

	/* Intermediate Outputs for most recent calculation (used for some diagnostics) */
	private InversionTargetMFDs[] bValTargetMFDs;
	private LogicTreeBranch<? extends LogicTreeNode> branch;

	public PaleoBValueEstimator(ContinuousDistribution priorDist, EvenlyDiscretizedFunc bVals,
			InversionConfigurationFactory factory) {
		Preconditions.checkNotNull(priorDist);
		this.priorDist = priorDist;
		Preconditions.checkNotNull(bVals);
		this.bVals = bVals;
		Preconditions.checkNotNull(factory);
		this.factory = factory;

		this.logLikelihoodFunction = GAUSSIAN_LIKELIHOOD;
		this.connectivityWeightB = priorDist.getMean();
	}

	public static DoubleFunction<Double> GAUSSIAN_LIKELIHOOD = (misfit) -> -0.5 * misfit * misfit;

	public static DoubleFunction<Double> getStudentsTLogLikelihood(double nu) {
		TDistribution tDist = TDistribution.of(nu);
		// Preserve the meaning of the original 95% bounds: make the t's
		// 95% interval coincide with the Gaussian-derived 95% interval.
		double scaleFactor = tDist.inverseCumulativeProbability(0.975) / 1.96;
		return (misfit) -> tDist.logDensity(misfit * scaleFactor);
	}

	public void setLogLikelihoodFunction(DoubleFunction<Double> logLikelihoodFunction) {
		Preconditions.checkNotNull(logLikelihoodFunction);
		this.logLikelihoodFunction = logLikelihoodFunction;
	}

	public void setGaussianLogLikelihood() {
		this.logLikelihoodFunction = GAUSSIAN_LIKELIHOOD;
	}

	public void setStudentsTLogLikelihood(double nu) {
		this.logLikelihoodFunction = getStudentsTLogLikelihood(nu);
	}

	public void setWeightingType(WeightingType weightingType) {
		this.weightingType = Preconditions.checkNotNull(weightingType);
	}

	public WeightingType getWeightingType() {
		return weightingType;
	}

	public void setBackgroundDistribution(BackgroundDistribution backgroundDist) {
		this.backgroundDist = Preconditions.checkNotNull(backgroundDist);
	}

	public BackgroundDistribution getBackgroundDistribution() {
		return backgroundDist;
	}

	public void setPosteriorCombination(PosteriorCombination posteriorComb) {
		this.posteriorComb = Preconditions.checkNotNull(posteriorComb);
	}

	public PosteriorCombination getPosteriorCombination() {
		return posteriorComb;
	}

	public void setTargetMFDCalc(Function<MFDCalcInputs, InversionTargetMFDs> targetMFDCalc) {
		this.targetMFDCalc = targetMFDCalc;
	}

	public void setLikelihoodInRecurrence(boolean likelihoodInRecurrence) {
		this.likelihoodInRecurrence = likelihoodInRecurrence;
	}

	public boolean isLikelihoodInRecurrence() {
		return likelihoodInRecurrence;
	}

	public PosteriorSectionBValueDistributions calculate(LogicTreeBranch<? extends LogicTreeNode> branch, boolean verbose) throws IOException {
		FaultSystemRupSet rs = factory.buildRuptureSet(branch, FaultSysTools.defaultNumThreads());
		return calculate(rs, branch, verbose);
	}

	public record MFDCalcInputs(InversionConfigurationFactory factory, FaultSystemRupSet rupSet,
			LogicTreeBranch<? extends LogicTreeNode> branch) {}

	public PosteriorSectionBValueDistributions calculate(FaultSystemRupSet rs,
			LogicTreeBranch<? extends LogicTreeNode> branch, boolean verbose) {
		Preconditions.checkNotNull(rs);
		PaleoseismicConstraintData paleoData = rs.requireModule(PaleoseismicConstraintData.class);
		List<? extends SectMappedUncertainDataConstraint> paleoConstraints = paleoData.getPaleoRateConstraints();
		Preconditions.checkState(paleoConstraints != null && !paleoConstraints.isEmpty(),
				"Paleo constraints cannot be null/empty");
		PaleoProbabilityModel paleoProb = paleoData.getPaleoProbModel();

		System.out.println("Calculating section posterior b-value distributions for "+rs.getNumSections()
				+" sections and "+paleoConstraints.size()+" paleo constraints");

		BitSet includedRups;
		if (factory instanceof ExclusionaryInversionConfigurationFactory) {
			ClusterRuptures cRups = rs.requireModule(ClusterRuptures.class);
			BinaryRuptureProbabilityCalc exclusionModel = ((ExclusionaryInversionConfigurationFactory)factory).getExclusionModel(rs, branch, cRups);
			includedRups = exclusionModel == null ? null : new BitSet(rs.getNumRuptures());
			if (exclusionModel != null) {
				for (int r=0; r<rs.getNumRuptures(); r++)
					if (exclusionModel.isRupAllowed(cRups.get(r), false))
						includedRups.set(r);
			}
		} else {
			includedRups = null;
		}

		List<EvenlyDiscretizedFunc> paleoBValMisfits = new ArrayList<>(paleoConstraints.size());
		List<EvenlyDiscretizedFunc> paleoBValEstRates = new ArrayList<>(paleoConstraints.size());
		for (int s=0; s<paleoConstraints.size(); s++) {
			String name = paleoConstraints.get(s).name;
			paleoBValMisfits.add(bVals.deepClone());
			paleoBValMisfits.getLast().setName(name);
			paleoBValEstRates.add(bVals.deepClone());
			paleoBValEstRates.getLast().setName(name);
		}

		// calculate for each b-value
		System.out.println("Calculating target MFDs for "+bVals.size()+" b-values");
		List<CompletableFuture<InversionTargetMFDs>> targetFutures = new ArrayList<>();
		for (int i=0; i<bVals.size(); i++) {
			double b = bVals.getX(i);
			targetFutures.add(CompletableFuture.supplyAsync(()->targetMFDCalc.apply(
					new MFDCalcInputs(factory, rs, getBranchForB(branch, b)))));
		}
		InversionTargetMFDs connectivityWeightTargetMFDs = targetMFDCalc.apply(
				new MFDCalcInputs(factory, rs, getBranchForB(branch, connectivityWeightB)));
		InversionTargetMFDs[] bValTargetMFDs = new InversionTargetMFDs[bVals.size()];
		for (int i=0; i<bValTargetMFDs.length; i++)
			bValTargetMFDs[i] = targetFutures.get(i).join();
		System.out.println("DONE calculating for "+bVals.size()+" b-values");
		System.out.println("Calculating site b-value paleo misfits");

		boolean[] zeroSlipSites = new boolean[paleoConstraints.size()];
		for (int s=0; s<paleoConstraints.size(); s++) {
			SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
			if (rs.getFaultSectionData(paleoConstr.sectionIndex).getReducedAveSlipRate() == 0d) {
				// zero slip rate at a paleo site? keep finite outputs, but don't let it influence b-values
				System.err.println("WARNING: zero slip rate for section "
						+rs.getFaultSectionData(paleoConstr.sectionIndex)+" mapped to paleo site "+paleoConstr.getName());
				zeroSlipSites[s] = true;
				for (int i=0; i<bVals.size(); i++) {
					paleoBValMisfits.get(s).set(i, 0d);
					paleoBValEstRates.get(s).set(i, 0d);
				}
			}
		}

		for (int i=0; i<bValTargetMFDs.length; i++) {
			rs.removeModuleInstances(InversionTargetMFDs.class);
			double b = bVals.getX(i);
			if (verbose) {
				System.out.println("====================================");
				System.out.println("Site results for b="+(float)b);
			}
			for (int s=0; s<paleoConstraints.size(); s++) {
				SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
				if (zeroSlipSites[s])
					continue;
				IncrementalMagFreqDist mfd = bValTargetMFDs[i].
						getOnFaultSupraSeisNucleationMFDs().get(paleoConstr.sectionIndex);
				double[] sumParticScalars = new double[mfd.size()];
				double[] sumPaleoScalars = new double[mfd.size()];
				int[] binCounts = new int[mfd.size()];
				double sectArea = rs.getAreaForSection(paleoConstr.sectionIndex);
				for (int rupIndex : rs.getRupturesForSection(paleoConstr.sectionIndex)) {
					int magIndex = mfd.getClosestXIndex(rs.getMagForRup(rupIndex));
					binCounts[magIndex]++;
					double rupArea = rs.getAreaForRup(rupIndex);
					sumParticScalars[magIndex] += rupArea/sectArea;
					double paleoVisibleProb =  paleoProb.getProbPaleoVisible(rs, rupIndex, paleoConstr.sectionIndex);
					sumPaleoScalars[magIndex] += paleoVisibleProb;
				}
				double totParticRate = 0d;
				double paleoVisibleRate = 0d;
				for (int m=0; m<mfd.size(); m++) {
					double totNuclRate = mfd.getY(m);
					if (totNuclRate > 0d) {
						double particRate = totNuclRate * sumParticScalars[m]/binCounts[m];
						totParticRate += particRate;
						paleoVisibleRate += particRate * sumPaleoScalars[m]/binCounts[m];
					}
				}
				FaultSection sect = rs.getFaultSectionData(paleoConstr.sectionIndex);
				Preconditions.checkState(paleoVisibleRate > 0d && Double.isFinite(paleoVisibleRate),
						"Bad paleoVisibleRate=%s for constraint %s mapped to section %s with reducedSlip=%s, mfdSumY=%s, and particRate=%s",
						paleoVisibleRate, paleoConstr, sect, sect.getReducedAveSlipRate(), mfd.calcSumOfY_Vals(), totParticRate);
//				double misfit = (paleoVisibleRate - paleoConstr.bestEstimate)/paleoConstr.getPreferredStdDev();
				double misfit;
				if (likelihoodInRecurrence) {
					// convert to recurrence
					// preferred uncertainty is always first and should always be bounded here
					Preconditions.checkState(paleoConstr.uncertainties[0] instanceof BoundedUncertainty);
					Preconditions.checkState(paleoConstr.bestEstimate > 0d);
					BoundedUncertainty rateUncert = (BoundedUncertainty)paleoConstr.uncertainties[0];
					BoundedUncertainty recurrenceUncert = new BoundedUncertainty(rateUncert.type,
							// upper is lower, lower is upper, stdDev is unused (NaN is fine)
							1d/rateUncert.upperBound, 1d/rateUncert.lowerBound, Double.NaN);
					misfit = recurrenceUncert.estimateDataZ(1d/paleoVisibleRate, 1d/paleoConstr.bestEstimate);
				} else {
					misfit = paleoConstr.estimateDataZ(paleoVisibleRate);
				}
				if (verbose) {
					System.out.println(paleoConstr.name+" ("+paleoConstr.sectionName+"):");
					System.out.println("\tpaleoRate="+(float)paleoConstr.bestEstimate+"\testSolRate="+(float)paleoVisibleRate+"\tmisfit="+(float)misfit);
					System.out.println("\tpaleoRI="+(float)(1d/paleoConstr.bestEstimate)+"\testSolRI="+(float)(1d/paleoVisibleRate));
				}
				paleoBValMisfits.get(s).set(i, misfit);
				paleoBValEstRates.get(s).set(i, paleoVisibleRate);
			}
			if (verbose) System.out.println("====================================");
		}

		NamedFaults faults = rs.getModule(NamedFaults.class);

		if (verbose && faults != null) {
			DecimalFormat df = new DecimalFormat("0.000");
			Map<String, List<EvenlyDiscretizedFunc>> namedFaultResults = new HashMap<>();
			List<EvenlyDiscretizedFunc> ssafResults = new ArrayList<>();
			List<EvenlyDiscretizedFunc> nsafResults = new ArrayList<>();
			if (faults.getFaultNames().contains("San Andreas")) {
				namedFaultResults.put("San Andreas (Southern)", nsafResults);
				namedFaultResults.put("San Andreas (Northern)", ssafResults);
			}
			for (int s=0; s<paleoConstraints.size(); s++) {
				SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
				System.out.println(paleoConstr.name+" ("+paleoConstr.sectionName+"):");
				if (zeroSlipSites[s]) {
					System.out.println("\tZero slip rate");
					continue;
				}
				EvenlyDiscretizedFunc result = paleoBValMisfits.get(s);
				for (int b=0; b<bVals.size(); b++)
					System.out.print("\t"+df.format(result.getY(b)));
				System.out.println();
				System.out.println();
				int parentID = rs.getFaultSectionData(paleoConstr.sectionIndex).getParentSectionId();
				String faultName = faults.getFaultName(parentID);
				if (faultName != null) {
					List<EvenlyDiscretizedFunc> faultResults =  namedFaultResults.get(faultName);
					if (faultResults == null) {
						faultResults = new ArrayList<>();
						namedFaultResults.put(faultName, faultResults);
					}
					faultResults.add(result);
					if (faultName.toLowerCase().startsWith("san andreas")) {
						if (paleoConstr.dataLocation.lat > 36.5)
							nsafResults.add(result);
						else
							ssafResults.add(result);
					}
				}
			}
			System.out.println("=====================");
			System.out.println("Named Faults:");
			System.out.println("=====================");
			List<String> faultNames = new ArrayList<>(namedFaultResults.keySet());
			Collections.sort(faultNames);
			for (String faultName : faultNames) {
				List<EvenlyDiscretizedFunc> faultResults = namedFaultResults.get(faultName);
				EvenlyDiscretizedFunc avgResult = new EvenlyDiscretizedFunc(bVals.getMinX(), bVals.getMaxX(), bVals.size());
				for (EvenlyDiscretizedFunc result : faultResults)
					for (int i=0; i<result.size(); i++)
						avgResult.add(i, result.getY(i));
				avgResult.scale(1d/faultResults.size());
				System.out.println(faultName+":");
				for (int b=0; b<bVals.size(); b++)
					System.out.print("\t"+df.format(avgResult.getY(b)));
				System.out.println();
				System.out.println();
			}
		}

		// now build posteriori distributions
		System.out.println("Building site b-value posteriors");
		Map<Integer, List<Integer>> parentPaleoIndexes = new HashMap<>();
		Map<String, List<Integer>> faultPaleoIndexes = new HashMap<>();
		List<ContinuousDistribution> sitePosteriorDists = new ArrayList<>(paleoConstraints.size());
		List<double[]> siteLogLikelihoods = new ArrayList<>(paleoConstraints.size());
		List<EvenlyDiscretizedFunc> paleoBValLogLikelihoods = new ArrayList<>(paleoConstraints.size());
		int numRups = rs.getNumRuptures();
		BitSet[] siteRups = new BitSet[paleoConstraints.size()];
		for (int s=0; s<paleoConstraints.size(); s++) {
			SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
			int parentID = rs.getFaultSectionData(paleoConstr.sectionIndex).getParentSectionId();
			if (!parentPaleoIndexes.containsKey(parentID))
				parentPaleoIndexes.put(parentID, new ArrayList<>());
			parentPaleoIndexes.get(parentID).add(s);
			String faultName = faults == null ? null : faults.getFaultName(parentID);
			if (faultName != null) {
				if (!faultPaleoIndexes.containsKey(faultName))
					faultPaleoIndexes.put(faultName, new ArrayList<>());
				faultPaleoIndexes.get(faultName).add(s);
			}

			double[] logLikelihoods = new double[bVals.size()];
			ContinuousDistribution posterior;
			if (zeroSlipSites[s]) {
				for (int i=0; i<logLikelihoods.length; i++)
					logLikelihoods[i] = 0d;
				posterior = priorDist;
			} else {
				EvenlyDiscretizedFunc posteriorPDF = new EvenlyDiscretizedFunc(
						bVals.getMinX(), bVals.getMaxX(), bVals.size());
				double maxLogPosterior = Double.NEGATIVE_INFINITY;
				for (int i=0; i<posteriorPDF.size(); i++) {
					double b = bVals.getX(i);
					double priorDensity = priorDist.density(b);
					Preconditions.checkState(Double.isFinite(priorDensity) && priorDensity > 0d,
							"Prior density=%s must be finite and positive for b=%s, site=%s",
							priorDensity, b, paleoConstr.name);

					// z-score
					double misfit = paleoBValMisfits.get(s).getY(i);
					Preconditions.checkState(Double.isFinite(misfit), "Misfit=%s must be finite for b=%s, site=%s",
							misfit, b, paleoConstr.name);

					// Unnormalized posterior density:
					// prior density * Gaussian likelihood
					double logLikelihood = logLikelihoodFunction.apply(misfit);
					logLikelihoods[i] = logLikelihood;
					double logPosterior = Math.log(priorDensity) + logLikelihood;
					posteriorPDF.set(i, logPosterior);
					maxLogPosterior = Math.max(maxLogPosterior, logPosterior);
				}

				Preconditions.checkState(Double.isFinite(maxLogPosterior),
						"Max log posterior=%s must be finite for site=%s", maxLogPosterior, paleoConstr.name);

				double sumWeights = 0d;
				for (int i=0; i<posteriorPDF.size(); i++) {
					double density = Math.exp(posteriorPDF.getY(i) - maxLogPosterior);
					posteriorPDF.set(i, density);
					sumWeights += density;
				}

				Preconditions.checkState(Double.isFinite(sumWeights) && sumWeights > 0d,
						"Sum of posterior weights=%s must be finite and positive for site=%s", sumWeights,
						paleoConstr.name);

				// Normalize such that sum(pdf[i] * delta) = 1
				posteriorPDF.scale(1d / (sumWeights * posteriorPDF.getDelta()));

				posterior = new EvenlyDiscrFuncContinuousDistribution(posteriorPDF, interpType);
			}

			siteLogLikelihoods.add(logLikelihoods);
			EvenlyDiscretizedFunc logLikelihoodFunc = bVals.deepClone();
			logLikelihoodFunc.setName(paleoConstr.name);
			for (int i=0; i<logLikelihoodFunc.size(); i++)
				logLikelihoodFunc.set(i, logLikelihoods[i]);
			paleoBValLogLikelihoods.add(logLikelihoodFunc);
			sitePosteriorDists.add(posterior);

			siteRups[s] = new BitSet(numRups);
			for (int r : rs.getRupturesForSection(paleoConstr.sectionIndex))
				siteRups[s].set(r);
		}

		Map<String, ContinuousDistribution> faultAverageDists = new HashMap<>();
		Map<Integer, ContinuousDistribution> parentAverageDists = new HashMap<>();
		Map<FaultStyleBins, List<Integer>> stylePaleoIndexes = new HashMap<>();
		Map<FaultStyleBins, ContinuousDistribution> styleAverageDists = new HashMap<>();
		for (int p=0; p<paleoConstraints.size(); p++) {
			FaultStyleBins style = FaultStyleBins.get(rs.getFaultSectionData(paleoConstraints.get(p).sectionIndex).getAveRake());
			stylePaleoIndexes.computeIfAbsent(style, k -> new ArrayList<>()).add(p);
		}
		final ContinuousDistribution paleoAverageDist;
		if (backgroundDist != BackgroundDistribution.GENERIC_PRIOR) {
			System.out.println("Building paleo-average b-value distributions");
			for (Map.Entry<String, List<Integer>> entry : faultPaleoIndexes.entrySet())
				faultAverageDists.put(entry.getKey(), buildAverageDistribution(entry.getValue(), sitePosteriorDists,
						siteLogLikelihoods, zeroSlipSites, "fault="+entry.getKey()));
			for (Map.Entry<Integer, List<Integer>> entry : parentPaleoIndexes.entrySet())
				parentAverageDists.put(entry.getKey(), buildAverageDistribution(entry.getValue(), sitePosteriorDists,
						siteLogLikelihoods, zeroSlipSites, "parent="+entry.getKey()));
			for (Map.Entry<FaultStyleBins, List<Integer>> entry : stylePaleoIndexes.entrySet())
				styleAverageDists.put(entry.getKey(), buildAverageDistribution(entry.getValue(), sitePosteriorDists,
						siteLogLikelihoods, zeroSlipSites, "fault style="+entry.getKey()));
			List<Integer> allPaleoIndexes = new ArrayList<>(paleoConstraints.size());
			for (int p=0; p<paleoConstraints.size(); p++)
				allPaleoIndexes.add(p);
			paleoAverageDist = buildAverageDistribution(allPaleoIndexes, sitePosteriorDists, siteLogLikelihoods,
					zeroSlipSites, "all paleo sites");
		} else {
			paleoAverageDist = priorDist;
		}

		System.out.println("Building section weighted b-value posteriors");
		int numSects = rs.getNumSections();
		List<CompletableFuture<SectResult>> sectBDistFutures = new ArrayList<>(numSects);

		for (int s=0; s<numSects; s++) {
			int sectIndex = s;
			int parentID = rs.getFaultSectionData(sectIndex).getParentSectionId();
			String faultName = faults == null ? null : faults.getFaultName(parentID);
			FaultStyleBins faultStyle = FaultStyleBins.get(rs.getFaultSectionData(sectIndex).getAveRake());

			sectBDistFutures.add(CompletableFuture.supplyAsync(() -> {
				ContinuousDistribution sectionBackground = switch (backgroundDist) {
				case GENERIC_PRIOR -> priorDist;
				case PALEO_AVERAGE -> paleoAverageDist;
				case FAULT_AVERAGE -> faultName == null ? parentAverageDists.getOrDefault(parentID, priorDist)
						: faultAverageDists.getOrDefault(faultName, priorDist);
				case NAMED_FAULT_AVERAGE -> faultName == null ? priorDist
						: faultAverageDists.getOrDefault(faultName, priorDist);
				case FAULT_STYLE_AVERAGE -> styleAverageDists.getOrDefault(faultStyle, priorDist);
				};

				List<Integer> connectedPaleoIndexes;
				if (faultName != null && faultPaleoIndexes.containsKey(faultName)) {
					connectedPaleoIndexes = faultPaleoIndexes.get(faultName);
				} else if (parentPaleoIndexes.containsKey(parentID)) {
					connectedPaleoIndexes = parentPaleoIndexes.get(parentID);
				} else {
					// no connected paleo data; use the selected background distribution
					return new SectResult(backgroundDist == BackgroundDistribution.GENERIC_PRIOR ? null : sectionBackground,
							sectionBackground, null, 1d);
				}

				IncrementalMagFreqDist mfd = connectivityWeightTargetMFDs.getOnFaultSupraSeisNucleationMFDs().get(sectIndex);

				if (mfd.calcSumOfY_Vals() == 0d)
					return new SectResult(backgroundDist == BackgroundDistribution.GENERIC_PRIOR ? null : sectionBackground,
							sectionBackground, null, 1d);

				/*
				 * Weighting scheme:
				 *
				 * figure out estimated fractional rate of each rupture by converting the MFD to estimated participation
				 * rates and dividing the total rate for each magnitude bin evenly among them
				 *
				 * for each rupture, give that fractional rate to each paleo site that it hits. In overlap-adjusted mode,
				 * split that rate evenly among the sites; in connected-fraction mode, give each site the full rate.
				 */

				// bin ruptures by magnitude
				int mMinIndex = mfd.getClosestXIndex(rs.getMinMagForSection(sectIndex));
				int mMaxIndex = mfd.getClosestXIndex(rs.getMaxMagForSection(sectIndex));
				int numMag = 1 + mMaxIndex - mMinIndex;
				List<List<Integer>> magRupIndexes = new ArrayList<>(numMag);
				for (int m=0; m<numMag; m++)
					magRupIndexes.add(new ArrayList<>());
				double[] rupAreaSums = new double[numMag];
				for (int rupIndex : rs.getRupturesForSection(sectIndex)) {
					if (includedRups == null || includedRups.get(rupIndex)) {
						double mag = rs.getMagForRup(rupIndex);
						int magIndex = mfd.getClosestXIndex(mag) - mMinIndex;
						rupAreaSums[magIndex] += rs.getAreaForRup(rupIndex);
						magRupIndexes.get(magIndex).add(rupIndex);
					}
				}

				double sectArea = rs.getAreaForSection(sectIndex);

				double[] weightPerPaleo = new double[paleoConstraints.size()];
				double weightNoPaleo = 0d;
				BitSet rupPaleoIndexes = new BitSet(paleoConstraints.size());
				double sectParticRate = 0d;
				for (int m=0; m<numMag; m++) {
					List<Integer> rups = magRupIndexes.get(m);
					if (rups.isEmpty())
						continue;
					double nuclRate = mfd.getY(m+mMinIndex);
					if (nuclRate == 0d)
						continue;
					double particScalar = rupAreaSums[m] / (rups.size() * sectArea);
					double particRate = nuclRate * particScalar;
					double particRateEach = particRate / rups.size();
					Preconditions.checkState(Double.isFinite(particRateEach) && particRateEach > 0d);
					for (int rupIndex : rups) {
						rupPaleoIndexes.clear();
						for (int paleoIndex : connectedPaleoIndexes)
							if (!zeroSlipSites[paleoIndex] && siteRups[paleoIndex].get(rupIndex))
								rupPaleoIndexes.set(paleoIndex);
						int numPaleo = rupPaleoIndexes.cardinality();
						if (numPaleo == 0) {
							// rupture hits no paleo sites
							weightNoPaleo += particRateEach;
						} else {
							double rupRatePerPaleo = weightingType == WeightingType.OVERLAP_ADJUSTED
									? particRateEach / (double)numPaleo : particRateEach;
							for (int i = rupPaleoIndexes.nextSetBit(0); i >= 0; i = rupPaleoIndexes.nextSetBit(i + 1))
								weightPerPaleo[i] += rupRatePerPaleo;
						}
					}
					sectParticRate += particRate;
				}

				Preconditions.checkState(sectParticRate > 0d);

				// normalize rates to weights
				weightNoPaleo /= sectParticRate;
				for (int p=0; p<weightPerPaleo.length; p++)
					weightPerPaleo[p] /= sectParticRate;

				ContinuousDistribution distribution;
				double[] outputWeights = weightPerPaleo;
				double outputNoPaleoWeight = weightNoPaleo;
				if (posteriorComb == PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS) {
					WeightedList<ContinuousDistribution> distWeights = new WeightedList<>();

					if (weightNoPaleo > 0d) {
						// weight for ruptures that hit no paleo sites
						distWeights.add(sectionBackground, weightNoPaleo);
					}

					for (int paleoIndex : connectedPaleoIndexes) {
						double paleoWeight = weightPerPaleo[paleoIndex];
						if (paleoWeight > 0d)
							distWeights.add(sitePosteriorDists.get(paleoIndex), paleoWeight);
					}
					Preconditions.checkState(!distWeights.isEmpty());
					double sumDistWeights = distWeights.getWeightSum();
					distWeights.normalize();
					outputWeights = weightPerPaleo.clone();
					for (int p=0; p<outputWeights.length; p++)
						outputWeights[p] /= sumDistWeights;
					outputNoPaleoWeight /= sumDistWeights;
					distribution = EvenlyDiscrFuncContinuousDistribution.discretize(
							new WeightedContinuousDistribution(distWeights), bVals, interpType);
				} else {
					distribution = buildLikelihoodDistribution(sectionBackground, connectedPaleoIndexes, weightPerPaleo,
							siteLogLikelihoods, "section="+sectIndex);
				}
				return new SectResult(distribution, sectionBackground, outputWeights, outputNoPaleoWeight);
			}));
		}

		List<ContinuousDistribution> sectPosteriors = new ArrayList<>(numSects);
		List<ContinuousDistribution> sectPriors = backgroundDist == BackgroundDistribution.GENERIC_PRIOR ? null
				: new ArrayList<>(numSects);
		List<double[]> sectPaleoWeights = new ArrayList<>();
		int numSet = 0;
		for (CompletableFuture<SectResult> future : sectBDistFutures) {
			SectResult result = future.join();
			if (result.distribution != null)
				numSet++;
			sectPosteriors.add(result.distribution);
			if (sectPriors != null)
				sectPriors.add(result.prior);
			sectPaleoWeights.add(result.paleoSiteWeights);
		}

		this.bValTargetMFDs = bValTargetMFDs;
		this.branch = branch;

		System.out.println("DONE calculating section b-value posteriors for "+numSet+"/"+numSects+" sections");

		return new PosteriorSectionBValueDistributions(priorDist, sectPosteriors, sectPriors, sectPaleoWeights,
				sitePosteriorDists, paleoBValMisfits, paleoBValLogLikelihoods);
	}

	private ContinuousDistribution buildAverageDistribution(List<Integer> paleoIndexes,
			List<ContinuousDistribution> sitePosteriorDists, List<double[]> siteLogLikelihoods, boolean[] zeroSlipSites,
			String context) {
		List<Integer> usableIndexes = new ArrayList<>();
		for (int paleoIndex : paleoIndexes)
			if (!zeroSlipSites[paleoIndex])
				usableIndexes.add(paleoIndex);
		if (usableIndexes.isEmpty())
			return priorDist;

		if (posteriorComb == PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS) {
			List<ContinuousDistribution> usableDists = new ArrayList<>(usableIndexes.size());
			for (int paleoIndex : usableIndexes)
				usableDists.add(sitePosteriorDists.get(paleoIndex));
			return EvenlyDiscrFuncContinuousDistribution.discretize(
					new WeightedContinuousDistribution(WeightedList.evenlyWeighted(usableDists)), bVals, interpType);
		}

		return buildLikelihoodDistribution(priorDist, usableIndexes, null, siteLogLikelihoods, context);
	}

	private ContinuousDistribution buildLikelihoodDistribution(ContinuousDistribution baseDist, List<Integer> paleoIndexes,
			double[] weights, List<double[]> siteLogLikelihoods, String context) {
		EvenlyDiscretizedFunc posterior = bVals.deepClone();
		double maxLogPosterior = Double.NEGATIVE_INFINITY;
		for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
			double b = bVals.getX(bIndex);
			double baseDensity = baseDist.density(b);
			Preconditions.checkState(Double.isFinite(baseDensity) && baseDensity > 0d,
					"Background density=%s must be finite and positive for b=%s, %s", baseDensity, b, context);
			double logPosterior = Math.log(baseDensity);
			for (int paleoIndex : paleoIndexes) {
				double weight = weights == null ? 1d : weights[paleoIndex];
				if (weight > 0d) {
					double logLikelihood = siteLogLikelihoods.get(paleoIndex)[bIndex];
					Preconditions.checkState(Double.isFinite(logLikelihood),
							"Log likelihood=%s must be finite for b=%s, %s", logLikelihood, b, context);
					logPosterior += weight * logLikelihood;
				}
			}
			posterior.set(bIndex, logPosterior);
			maxLogPosterior = Math.max(maxLogPosterior, logPosterior);
		}
		Preconditions.checkState(Double.isFinite(maxLogPosterior),
				"Max log posterior=%s must be finite, %s", maxLogPosterior, context);

		double sum = 0d;
		for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
			double density = Math.exp(posterior.getY(bIndex) - maxLogPosterior);
			posterior.set(bIndex, density);
			sum += density;
		}
		Preconditions.checkState(Double.isFinite(sum) && sum > 0d,
				"Sum of posterior weights=%s must be finite and positive, %s", sum, context);
		posterior.scale(1d / (sum * posterior.getDelta()));
		return new EvenlyDiscrFuncContinuousDistribution(posterior, interpType);
	}

	private static record SectResult(ContinuousDistribution distribution, ContinuousDistribution prior,
			double[] paleoSiteWeights, double noPaleoWeight) {};

	private static LogicTreeBranch<? extends LogicTreeNode> getBranchForB(LogicTreeBranch<? extends LogicTreeNode> branch, double b) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		List<LogicTreeNode> nodes = new ArrayList<>();
		SectionSupraSeisBValues.FixedValueLevel fixedB = null;
		boolean replaced = false;
		for (int l=0; l<branch.size(); l++) {
			LogicTreeLevel<? extends LogicTreeNode> level = branch.getLevel(l);
			if (SectionSupraSeisBValues.class.isAssignableFrom(level.getType())) {
				Preconditions.checkState(!replaced);
				System.out.println("Replacing level "+l+": level");
				fixedB = new SectionSupraSeisBValues.FixedValueLevel("Fixed b", "FixedB", b);
				levels.add(fixedB);
				nodes.add(fixedB.getNodes().get(0));
				replaced = true;
			} else {
				levels.add(level);
				nodes.add(branch.getValue(l));
			}
		}
		Preconditions.checkState(replaced, "Section b-value branch level not found");
		return new LogicTreeBranch<>(levels, nodes);
	}

	private static PlotCurveCharacterstics priorIQRDistChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Colors.tab_green);
	private static PlotCurveCharacterstics priorDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Colors.tab_green);
	private static PlotCurveCharacterstics parentDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Colors.tab_blue);
	private static PlotCurveCharacterstics otherDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	private static PlotCurveCharacterstics posteriorDistChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Colors.tab_orange);
	private static PlotCurveCharacterstics posteriorLikelihoodDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Colors.tab_orange);
	private static PlotCurveCharacterstics posteriorAvgDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK);
	private static PlotCurveCharacterstics charBounds = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.DARK_GRAY);
	private static PlotCurveCharacterstics postModeChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Colors.tab_blue);
	private static PlotCurveCharacterstics postAvgChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Colors.tab_orange);
	private static PlotCurveCharacterstics postShadedChar = new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
			modAlpha(Colors.tab_lightorange, 100));

	private static Color modAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private static LightFixedXFunc thicknessForWeights = new LightFixedXFunc(
			0d, 0.25d,
			0.1d, 1d,
			0.5d, 5d,
			1d, 5d);

	public static void plotSectDistributions(File outputDir, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors) throws IOException {
		NamedFaults faults = rs.getModule(NamedFaults.class);

		Map<Integer, List<FaultSection>> parentMappedSects = rs.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S->S.getParentSectionId()));
		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);

		PaleoseismicConstraintData paleoData = rs.requireModule(PaleoseismicConstraintData.class);
		List<? extends SectMappedUncertainDataConstraint> paleoConstraints = paleoData.getPaleoRateConstraints();
		BitSet paleoSects = new BitSet(rs.getNumSections());
		for (SectMappedUncertainDataConstraint data : paleoConstraints)
			paleoSects.set(data.sectionIndex);

		HashSet<Integer> mappedParents = new HashSet<>();
		for (Integer parentID : parentMappedSects.keySet()) {
			boolean anyCustom = false;
			for (FaultSection sect : parentMappedSects.get(parentID)) {
				if (posteriors.getSectPosteriors().get(sect.getSectionId()) != null) {
					anyCustom = true;
					break;
				}
			}
			if (!anyCustom)
				continue;

			// now check that we're connected to paleo data
			boolean anyPaleo = false;
			for (int rupIndex : rs.getRupturesForParentSection(parentID)) {
				for (int sectIndex : rs.getSectionsIndicesForRup(rupIndex)) {
					if (paleoSects.get(sectIndex)) {
						anyPaleo = true;
						break;
					}
				}
				if (anyPaleo)
					break;
			}

			if (anyPaleo)
				mappedParents.add(parentID);
		}

		Map<Integer, String> parentWithinNamedPrefixes = new HashMap<>();
//		namedFaultResults
		for (String faultName : faults.getFaultNames()) {
			List<Integer> parents = faults.getParentIDsForFault(faultName);
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			List<Location> parentMiddles = new ArrayList<>();
			boolean any = false;
			for (int parentID : parents) {
				LocationAverager avg = new LocationAverager();
				for (FaultSection sect : parentMappedSects.get(parentID)) {
					FaultTrace trace = sect.getFaultTrace();
					Location loc = trace.first();
					avg.add(loc, 1d);
					latTrack.addValue(loc.lat);
					lonTrack.addValue(loc.lon);
					loc = trace.last();
					avg.add(loc, 1d);
					latTrack.addValue(loc.lat);
					lonTrack.addValue(loc.lon);
				}
				parentMiddles.add(avg.getAverage());
				any |= mappedParents.contains(parentID);
			}
			if (!any)
				continue;

			boolean latX = SectBySectDetailPlots.isLatX(faultName, latTrack, lonTrack);

			List<Double> parentComps = new ArrayList<>(parents.size());
			for (Location loc : parentMiddles)
				parentComps.add(latX ? loc.lat : loc.lon);

			System.out.println(faultName+" latX="+latX);
			System.out.println("\tParents (unsorted):");
			for (int p=0; p<parents.size(); p++) {
				int parentID = parents.get(p);
				String parentName = parentMappedSects.get(parentID).get(0).getParentSectionName();
				System.out.println("\t\t"+p+". "+parentName+"\tsort="+parentComps.get(p).floatValue()+"\tloc="+parentMiddles.get(p));
			}

			// sort
			parents = ComparablePairing.getSortedData(parentComps, parents);

			String faultPrefix = FileNameUtils.simplify(faultName);

			String pattern = "0";
			while (pattern.length() < ((parents.size()-1)+"").length())
				pattern += "0";
			DecimalFormat parentDF = new DecimalFormat(pattern);
			System.out.println("\tParents (sorted):");
			for (int p=0; p<parents.size(); p++) {
				int parentID = parents.get(p);
				String parentName = parentMappedSects.get(parentID).get(0).getParentSectionName();
				System.out.println("\t\t"+p+". "+parentName);
				if (parentName.startsWith(faultName))
					parentName = parentName.substring(faultName.length()).trim();
				String parentPrefix = faultPrefix+"_"+parentDF.format(p)+"_"+FileNameUtils.simplify(parentName);
				parentWithinNamedPrefixes.put(parentID, parentPrefix);
			}
		}

		Map<Integer, List<Integer>> parentPaleoIndexes = new HashMap<>();
		Map<String, List<Integer>> faultPaleoIndexes = new HashMap<>();
		for (int s=0; s<paleoConstraints.size(); s++) {
			SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
			int parentID = rs.getFaultSectionData(paleoConstr.sectionIndex).getParentSectionId();
			if (!parentPaleoIndexes.containsKey(parentID))
				parentPaleoIndexes.put(parentID, new ArrayList<>());
			parentPaleoIndexes.get(parentID).add(s);
			String faultName = faults.getFaultName(parentID);
			if (faultName != null) {
				if (!faultPaleoIndexes.containsKey(faultName))
					faultPaleoIndexes.put(faultName, new ArrayList<>());
				faultPaleoIndexes.get(faultName).add(s);
			}
		}

		for (int parentID : parentMappedSects.keySet()) {
			if (!mappedParents.contains(parentID))
				continue;
			List<FaultSection> sects = parentMappedSects.get(parentID);
			String parentName = sects.get(0).getParentSectionName();
			String prefix;
			if (parentWithinNamedPrefixes.containsKey(parentID))
				prefix = parentWithinNamedPrefixes.get(parentID);
			else
				prefix = FileNameUtils.simplify(parentName);
			plotSectDistribution(outputDir, prefix, sects, posteriors, rs, paleoConstraints, bVals);
		}
	}

	public static void plotSectDistribution(File outputDir, String prefix, List<FaultSection> sects,
			PosteriorSectionBValueDistributions posteriors, FaultSystemRupSet rs) throws IOException {
		plotSectDistribution(outputDir, prefix, sects, posteriors, rs,
				rs.requireModule(PaleoseismicConstraintData.class).getPaleoRateConstraints(),
				PosteriorSectionBValueDistributions.detectBValues(posteriors));
	}

	public static void plotSectDistribution(File outputDir, String prefix, List<FaultSection> sects,
			PosteriorSectionBValueDistributions posteriors, FaultSystemRupSet rs,
			List<? extends SectMappedUncertainDataConstraint> paleoConstraints, EvenlyDiscretizedFunc bVals) throws IOException {
		List<EvenlyDiscretizedFunc> pdfFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> pdfChars = new ArrayList<>();
		List<EvenlyDiscretizedFunc> misfitFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> misfitChars = new ArrayList<>();

		WeightedList<ContinuousDistribution> mySectDists = new WeightedList<>();
		double[] paleoSiteWeights = new double[posteriors.getPaleoSitePosteriors().size()];
		double noPaleoWeight = 0d;
		for (FaultSection sect : sects) {
			ContinuousDistribution sectDist = posteriors.getSectDistribution(sect.getSectionId());
			double[] sectWeights = posteriors.getSectPaleoSiteWeights().get(sect.getSectionId());
			double sectNoPaleoWeight = 1d;
			if (sectWeights != null) {
				double sumSiteWeights = 0d;
				for (double weight : sectWeights)
					sumSiteWeights += weight;
				double weightScale = sumSiteWeights > 1d ? 1d/sumSiteWeights : 1d;
				for (int p=0; p<paleoSiteWeights.length; p++) {
					paleoSiteWeights[p] += weightScale * sectWeights[p];
					sectNoPaleoWeight -= weightScale * sectWeights[p];
				}
			}
			mySectDists.add(sectDist, 1d);
			if (sectNoPaleoWeight < 0d) {
				// floating point errors can make this barely negative after normalization
				Preconditions.checkState(sectNoPaleoWeight > -1e-10);
				sectNoPaleoWeight = 0d;
			}
			noPaleoWeight += sectNoPaleoWeight;
		}
		for (int p=0; p<paleoSiteWeights.length; p++)
			paleoSiteWeights[p] /= (double)sects.size();
		noPaleoWeight /= (double)sects.size();

		EvenlyDiscretizedFunc priorFunc = bVals.deepClone();
		ContinuousDistribution priorDist = posteriors.getPriorDist();
		for (int i=0; i<priorFunc.size(); i++)
			priorFunc.set(i, priorDist.density(bVals.getX(i)));
		priorFunc.setName("Prior");
		pdfFuncs.add(priorFunc);
		pdfChars.add(getForThickness(priorDistChar, (float)thicknessForWeights.getInterpolatedY(noPaleoWeight)));

		int parentID = sects.get(0).getParentSectionId();
		boolean firstSame = true;
		boolean firstOther = true;
		for (int paleoIndex=0; paleoIndex<paleoSiteWeights.length; paleoIndex++) {
			int paleoSectIndex = paleoConstraints.get(paleoIndex).sectionIndex;
			if (paleoSiteWeights[paleoIndex] == 0d)
				continue;
			float thickness = (float)thicknessForWeights.getInterpolatedY(paleoSiteWeights[paleoIndex]);
			boolean sameParent = parentID == rs.getFaultSectionData(paleoSectIndex).getParentSectionId();
			EvenlyDiscretizedFunc misfits = posteriors.getPaleoSiteMisfits().get(paleoIndex);
			ContinuousDistribution dist = posteriors.getPaleoSitePosteriors().get(paleoIndex);
			EvenlyDiscretizedFunc pdf = bVals.deepClone();
			for (int i=0; i<pdf.size(); i++)
				pdf.set(i, dist.density(pdf.getX(i)));
			misfits = misfits.deepClone();
			misfits.setName(null);
			if (sameParent) {
				if (firstSame) {
					misfits.setName("Paleo site (this section)");
//					pdf = pdf.deepClone();
//					pdf.setName(misfits.getName());
					firstSame = false;
				}
				misfitFuncs.add(misfits);
				misfitChars.add(getForThickness(parentDistChar, thickness));
				pdfFuncs.add(pdf);
				pdfChars.add(getForThickness(parentDistChar, thickness));
			} else {
				if (firstOther) {
					misfits.setName("Paleo site (other connected sections)");
					pdf = pdf.deepClone();
//					pdf.setName(misfits.getName());
					firstOther = false;
				}
				misfitFuncs.add(0, misfits);
				misfitChars.add(0, getForThickness(otherDistChar, thickness));
				pdfFuncs.add(0, pdf);
				pdfChars.add(0, getForThickness(otherDistChar, thickness));
			}
		}

		boolean firstSectDist = true;
		for (int s=0; s<mySectDists.size(); s++) {
			ContinuousDistribution sectDist = mySectDists.getValue(s);
			EvenlyDiscretizedFunc sectPDF = bVals.deepClone();
			for (int i=0; i<sectPDF.size(); i++)
				sectPDF.set(i, sectDist.density(bVals.getX(i)));
			if (firstSectDist) {
				sectPDF.setName("Subsection posteriors");
				firstSectDist = false;
			}
			pdfFuncs.add(sectPDF);
			pdfChars.add(posteriorDistChar);
		}
		ContinuousDistribution poisteriorDist = new WeightedContinuousDistribution(mySectDists);
		EvenlyDiscretizedFunc posteriorFunc = bVals.deepClone();
		for (int i=0; i<posteriorFunc.size(); i++)
			posteriorFunc.set(i, poisteriorDist.density(bVals.getX(i)));
		posteriorFunc.setName("Section average posterior");
		pdfFuncs.add(posteriorFunc);
		pdfChars.add(posteriorAvgDistChar);

		String parentName = sects.get(0).getParentSectionName();
		PlotSpec pdfPlot = new PlotSpec(pdfFuncs, pdfChars, parentName, "b-value", "Density");
		pdfPlot.setLegendInset(true);

//		PlotSpec misfitsPlot = new PlotSpec(misfitFuncs, misfitChars, parentName, "b-value", "Misfit (z-score)");
//		misfitsPlot.setLegendInset(true);

		List<EvenlyDiscretizedFunc> absMisfitFuncs = new ArrayList<>();
		for (EvenlyDiscretizedFunc func : misfitFuncs) {
			EvenlyDiscretizedFunc absFunc = func.deepClone();
			for (int i=0; i<func.size(); i++)
				absFunc.set(i, Math.abs(func.getY(i)));
			absMisfitFuncs.add(absFunc);
		}
		PlotSpec misfitsPlot = new PlotSpec(absMisfitFuncs, misfitChars, parentName, "b-value", "|Misfit (z-score)|");
		misfitsPlot.setLegendInset(true);

		HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();

		gp.drawGraphPanel(List.of(pdfPlot, misfitsPlot), false, false, List.of(new Range(bVals.getMinX(), bVals.getMaxX())), null);

		PlotUtils.writePlots(outputDir, prefix, gp, 800, 1200, true, true, false);
	}

	public static void plotFaultSiteAverages(File outputDir, String prefix, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors, NamedFaults faults) throws IOException {
		Preconditions.checkNotNull(rs);
		Preconditions.checkNotNull(posteriors);
		Preconditions.checkNotNull(faults);
		List<ContinuousDistribution> siteDists = posteriors.getPaleoSitePosteriors();
		List<EvenlyDiscretizedFunc> siteMisfits = posteriors.getPaleoSiteMisfits();
		Preconditions.checkState(siteDists != null && siteMisfits != null && !siteDists.isEmpty(),
				"Paleo site distributions and misfits are required for site-average plots");
		Preconditions.checkState(siteDists.size() == siteMisfits.size());
		List<EvenlyDiscretizedFunc> siteLogLikelihoods = posteriors.getPaleoSiteLogLikelihoods();
		Preconditions.checkState(siteLogLikelihoods != null && siteLogLikelihoods.size() == siteDists.size(),
				"Paleo site log likelihoods are required for likelihood-combination plots");

		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);
		Preconditions.checkNotNull(bVals, "Could not determine b-value discretization");
		List<Integer> allIndexes = usablePaleoIndexes(siteLogLikelihoods, siteDists.size());
		ContinuousDistribution allAverage = combineSiteDistributions(posteriors.getPriorDist(), bVals,
				getAll(siteDists, allIndexes), getAll(siteLogLikelihoods, allIndexes), PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS, "all paleo sites");
		ContinuousDistribution allLikelihoodCombined = combineSiteDistributions(posteriors.getPriorDist(), bVals,
				getAll(siteDists, allIndexes), getAll(siteLogLikelihoods, allIndexes), PosteriorCombination.WEIGHTED_SITE_LIKELIHOODS, "all paleo sites");
		plotSitesDistribution(outputDir, prefix+"_all_paleo_sites", "All Paleo Sites", bVals, posteriors.getPriorDist(),
				getAll(siteDists, allIndexes), allAverage, allLikelihoodCombined, getAll(siteMisfits, allIndexes));

		List<? extends SectMappedUncertainDataConstraint> paleoConstraints = rs.requireModule(PaleoseismicConstraintData.class)
				.getPaleoRateConstraints();
		Preconditions.checkState(paleoConstraints.size() == siteDists.size());
		Map<String, List<Integer>> faultPaleoIndexes = new HashMap<>();
		Map<FaultStyleBins, List<Integer>> stylePaleoIndexes = new HashMap<>();
		for (int p=0; p<paleoConstraints.size(); p++) {
			FaultSection paleoSect = rs.getFaultSectionData(paleoConstraints.get(p).sectionIndex);
			int parentID = paleoSect.getParentSectionId();
			String faultName = faults.getFaultName(parentID);
			if (faultName != null)
				faultPaleoIndexes.computeIfAbsent(faultName, k -> new ArrayList<>()).add(p);
			FaultStyleBins style = FaultStyleBins.get(paleoSect.getAveRake());
			stylePaleoIndexes.computeIfAbsent(style, k -> new ArrayList<>()).add(p);
		}
		List<String> faultNames = new ArrayList<>(faultPaleoIndexes.keySet());
		Collections.sort(faultNames);
		for (String faultName : faultNames) {
			List<Integer> paleoIndexes = usablePaleoIndexes(siteLogLikelihoods, faultPaleoIndexes.get(faultName));
			if (paleoIndexes.isEmpty())
				continue;
			ContinuousDistribution average = combineSiteDistributions(posteriors.getPriorDist(), bVals,
					getAll(siteDists, paleoIndexes), getAll(siteLogLikelihoods, paleoIndexes), PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS,
					"fault="+faultName);
			ContinuousDistribution combined = combineSiteDistributions(posteriors.getPriorDist(), bVals,
					getAll(siteDists, paleoIndexes), getAll(siteLogLikelihoods, paleoIndexes), PosteriorCombination.WEIGHTED_SITE_LIKELIHOODS,
					"fault="+faultName);
			plotSitesDistribution(outputDir, prefix+"_"+FileNameUtils.simplify(faultName), faultName, bVals,
					posteriors.getPriorDist(), getAll(siteDists, paleoIndexes), average, combined, getAll(siteMisfits, paleoIndexes));
		}

		for (FaultStyleBins style : FaultStyleBins.values()) {
			List<Integer> paleoIndexes = stylePaleoIndexes.get(style);
			if (paleoIndexes == null)
				continue;
			paleoIndexes = usablePaleoIndexes(siteLogLikelihoods, paleoIndexes);
			if (paleoIndexes.isEmpty())
				continue;
			ContinuousDistribution average = combineSiteDistributions(posteriors.getPriorDist(), bVals,
					getAll(siteDists, paleoIndexes), getAll(siteLogLikelihoods, paleoIndexes),
					PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS, "fault style="+style);
			ContinuousDistribution combined = combineSiteDistributions(posteriors.getPriorDist(), bVals,
					getAll(siteDists, paleoIndexes), getAll(siteLogLikelihoods, paleoIndexes),
					PosteriorCombination.WEIGHTED_SITE_LIKELIHOODS, "fault style="+style);
			plotSitesDistribution(outputDir, prefix+"_"+style.name(), style.toString(), bVals, posteriors.getPriorDist(),
					getAll(siteDists, paleoIndexes), average, combined, getAll(siteMisfits, paleoIndexes));
		}
	}

	private static List<Integer> usablePaleoIndexes(List<EvenlyDiscretizedFunc> siteLogLikelihoods, int numSites) {
		List<Integer> indexes = new ArrayList<>(numSites);
		for (int p=0; p<numSites; p++)
			indexes.add(p);
		return usablePaleoIndexes(siteLogLikelihoods, indexes);
	}

	private static List<Integer> usablePaleoIndexes(List<EvenlyDiscretizedFunc> siteLogLikelihoods,
			List<Integer> candidateIndexes) {
		if (siteLogLikelihoods == null)
			return new ArrayList<>(candidateIndexes);
		List<Integer> usable = new ArrayList<>(candidateIndexes.size());
		for (int paleoIndex : candidateIndexes) {
			EvenlyDiscretizedFunc logLikelihood = siteLogLikelihoods.get(paleoIndex);
			boolean zeroSlip = true;
			for (int i=0; i<logLikelihood.size(); i++) {
				if (logLikelihood.getY(i) != 0d) {
					zeroSlip = false;
					break;
				}
			}
			if (!zeroSlip)
				usable.add(paleoIndex);
		}
		return usable;
	}

	private static <E> List<E> getAll(List<E> values, List<Integer> indexes) {
		List<E> ret = new ArrayList<>(indexes.size());
		for (int index : indexes)
			ret.add(values.get(index));
		return ret;
	}

	/**
	 * Combines equally weighted paleo site inferences using the requested combination method.
	 * <p>
	 * For {@link PosteriorCombination#WEIGHTED_SITE_DISTRIBUTIONS}, this returns an equal-weight mixture of the
	 * supplied site posteriors. For {@link PosteriorCombination#WEIGHTED_SITE_LIKELIHOODS}, it multiplies the
	 * supplied site likelihoods with {@code priorDist}. The latter therefore requires the unnormalized site
	 * log-likelihood functions saved alongside the site posterior distributions.
	 */
	public static ContinuousDistribution combineSiteDistributions(ContinuousDistribution priorDist,
			EvenlyDiscretizedFunc bVals, List<ContinuousDistribution> siteDists,
			List<EvenlyDiscretizedFunc> siteLogLikelihoods, PosteriorCombination posteriorComb, String context) {
		Preconditions.checkNotNull(priorDist);
		Preconditions.checkNotNull(bVals);
		Preconditions.checkNotNull(siteDists);
		Preconditions.checkNotNull(posteriorComb);
		Preconditions.checkState(!siteDists.isEmpty(), "No usable paleo sites for %s", context);
		if (posteriorComb == PosteriorCombination.WEIGHTED_SITE_DISTRIBUTIONS) {
			return EvenlyDiscrFuncContinuousDistribution.discretize(
					new WeightedContinuousDistribution(WeightedList.evenlyWeighted(siteDists)), bVals,
					DiscretizationType.INTERPOLATE);
		}
		return combineSiteLogLikelihoods(priorDist, bVals, siteLogLikelihoods, context);
	}

	/**
	 * Combines equally weighted paleo site log likelihoods with a prior distribution.
	 */
	public static ContinuousDistribution combineSiteLogLikelihoods(ContinuousDistribution priorDist,
			EvenlyDiscretizedFunc bVals, List<EvenlyDiscretizedFunc> siteLogLikelihoods, String context) {
		Preconditions.checkNotNull(priorDist);
		Preconditions.checkNotNull(bVals);
		Preconditions.checkNotNull(siteLogLikelihoods);
		Preconditions.checkState(!siteLogLikelihoods.isEmpty(), "No usable paleo site log likelihoods for %s", context);
		EvenlyDiscretizedFunc posterior = bVals.deepClone();
		double maxLogPosterior = Double.NEGATIVE_INFINITY;
		for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
			double b = posterior.getX(bIndex);
			double priorDensity = priorDist.density(b);
			Preconditions.checkState(Double.isFinite(priorDensity) && priorDensity > 0d,
					"Prior density=%s must be finite and positive for b=%s, %s", priorDensity, b, context);
			double logPosterior = Math.log(priorDensity);
			for (EvenlyDiscretizedFunc siteLogLikelihood : siteLogLikelihoods) {
				Preconditions.checkState(EvenlyDiscretizedFunc.areXValuesIdentical(bVals, siteLogLikelihood),
						"Log likelihood b-values differ for %s", context);
				double logLikelihood = siteLogLikelihood.getY(bIndex);
				Preconditions.checkState(Double.isFinite(logLikelihood),
						"Log likelihood=%s must be finite for b=%s, %s", logLikelihood, b, context);
				logPosterior += logLikelihood;
			}
			posterior.set(bIndex, logPosterior);
			maxLogPosterior = Math.max(maxLogPosterior, logPosterior);
		}
		double sum = 0d;
		for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
			double density = Math.exp(posterior.getY(bIndex) - maxLogPosterior);
			posterior.set(bIndex, density);
			sum += density;
		}
		Preconditions.checkState(Double.isFinite(sum) && sum > 0d,
				"Sum of posterior weights=%s must be finite and positive, %s", sum, context);
		posterior.scale(1d / (sum * posterior.getDelta()));
		return new EvenlyDiscrFuncContinuousDistribution(posterior, DiscretizationType.INTERPOLATE);
	}

	public static void plotSitesDistribution(File outputDir, String prefix, String name, EvenlyDiscretizedFunc bVals,
			ContinuousDistribution priorDist, List<ContinuousDistribution> siteDists,
			ContinuousDistribution averageDist, ContinuousDistribution combLikelihoodDist,
			List<EvenlyDiscretizedFunc> siteMisfits) throws IOException {


		List<EvenlyDiscretizedFunc> pdfFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> pdfChars = new ArrayList<>();
		List<EvenlyDiscretizedFunc> misfitFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> misfitChars = new ArrayList<>();

		Preconditions.checkState(!siteDists.isEmpty() && siteDists.size() == siteMisfits.size());
		EvenlyDiscretizedFunc priorFunc = bVals.deepClone();
		for (int i=0; i<priorFunc.size(); i++)
			priorFunc.set(i, priorDist.density(priorFunc.getX(i)));
		priorFunc.setName("Prior");
		pdfFuncs.add(priorFunc);
		pdfChars.add(getForThickness(priorDistChar, 1f));

		if (siteDists.size() > 1) {
			for (int s=0; s<siteDists.size(); s++) {
				ContinuousDistribution siteDist = siteDists.get(s);
				EvenlyDiscretizedFunc distFunc = bVals.deepClone();
				for (int i=0; i<distFunc.size(); i++)
					distFunc.set(i, siteDist.density(distFunc.getX(i)));
				if (s == 0)
					distFunc.setName("Individual sites");
				pdfFuncs.add(distFunc);
				pdfChars.add(getForThickness(parentDistChar, 2f));
			}
		}

		if (siteDists.size() > 1) {
			// plot average and comb-likelihood
			EvenlyDiscretizedFunc combDistFunc = bVals.deepClone();
			for (int i=0; i<combDistFunc.size(); i++)
				combDistFunc.set(i, combLikelihoodDist.density(combDistFunc.getX(i)));
			combDistFunc.setName("Combined likelihoods");
			pdfFuncs.add(combDistFunc);
			pdfChars.add(posteriorLikelihoodDistChar);

			combDistFunc = bVals.deepClone();
			for (int i=0; i<combDistFunc.size(); i++)
				combDistFunc.set(i, averageDist.density(combDistFunc.getX(i)));
			combDistFunc.setName("Average");
			pdfFuncs.add(combDistFunc);
			pdfChars.add(posteriorAvgDistChar);
		} else {
			// they're the same, plot one
			EvenlyDiscretizedFunc combDistFunc = bVals.deepClone();
			for (int i=0; i<combDistFunc.size(); i++)
				combDistFunc.set(i, averageDist.density(combDistFunc.getX(i)));
			combDistFunc.setName("Site distribution");
			pdfFuncs.add(combDistFunc);
			pdfChars.add(posteriorAvgDistChar);
		}

		for (int s=0; s<siteMisfits.size(); s++) {
			EvenlyDiscretizedFunc misfitFunc = siteMisfits.get(s).deepClone();
			if (s == 0)
				misfitFunc.setName(siteMisfits.size() > 1 ? "Individual sites" : null);
			else
				misfitFunc.setName(null);
			misfitFuncs.add(misfitFunc);
			misfitChars.add(getForThickness(parentDistChar, 2f));
		}

		PlotSpec pdfPlot = new PlotSpec(pdfFuncs, pdfChars, name, "b-value", "Density");
		pdfPlot.setLegendInset(true);

//		PlotSpec misfitsPlot = new PlotSpec(misfitFuncs, misfitChars, parentName, "b-value", "Misfit (z-score)");
//		misfitsPlot.setLegendInset(true);

		List<EvenlyDiscretizedFunc> absMisfitFuncs = new ArrayList<>();
		for (EvenlyDiscretizedFunc func : misfitFuncs) {
			EvenlyDiscretizedFunc absFunc = func.deepClone();
			for (int i=0; i<func.size(); i++)
				absFunc.set(i, Math.abs(func.getY(i)));
			absMisfitFuncs.add(absFunc);
		}
		PlotSpec misfitsPlot = new PlotSpec(absMisfitFuncs, misfitChars, name, "b-value", "|Misfit (z-score)|");
		misfitsPlot.setLegendInset(true);

		HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();

		gp.drawGraphPanel(List.of(pdfPlot, misfitsPlot), false, false, List.of(new Range(priorFunc.getMinX(), priorFunc.getMaxX())), null);

		PlotUtils.writePlots(outputDir, prefix, gp, 800, 1200, true, true, false);
	}

	public void plotAlongStrike(File outputDir, String prefix, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors, NamedFaults faults) throws IOException {

		PaleoseismicConstraintData paleoData = rs.requireModule(PaleoseismicConstraintData.class);

		BitSet includedRups = null;
		if (factory instanceof ExclusionaryInversionConfigurationFactory)
			includedRups = ((ExclusionaryInversionConfigurationFactory)factory).getInncludedRups(rs, branch, rs.requireModule(ClusterRuptures.class));

		for (String faultName : faults.getFaultNames()) {
			List<FaultSection> sects = new ArrayList<>();
			for (FaultSection sect : rs.getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				String sectFaultName = faults.getFaultName(parentID);
				if (sectFaultName != null && sectFaultName.equals(faultName))
					sects.add(sect);
			}

			if (!sects.isEmpty())
				doPlotAlongStrike(outputDir, prefix+"_"+FileNameUtils.simplify(faultName), rs, posteriors,
						faultName, sects, paleoData, includedRups);
		}
	}

	public boolean plotAlongStrike(File outputDir, String prefix, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors, String faultName, List<FaultSection> sects) throws IOException {

		PaleoseismicConstraintData paleoData = rs.requireModule(PaleoseismicConstraintData.class);

		BitSet includedRups = null;
		if (factory instanceof ExclusionaryInversionConfigurationFactory)
			includedRups = ((ExclusionaryInversionConfigurationFactory)factory).getInncludedRups(rs, branch, rs.requireModule(ClusterRuptures.class));
		return doPlotAlongStrike(outputDir, prefix, rs, posteriors, faultName, sects, paleoData, includedRups);
	}

	private static double[] calcModes(List<FaultSection> sects, EvenlyDiscretizedFunc bVals, PosteriorSectionBValueDistributions posteriors) {
		double[] modes = new double[sects.size()];
		for (int i=0; i<sects.size(); i++) {
			FaultSection sect = sects.get(i);
			int sectIndex = sect.getSectionId();
			ContinuousDistribution dist = posteriors.getSectDistribution(sectIndex);
			double maxDensity = 0d;
			double mode = Double.NaN;
			boolean allSame = true;
			for (int b=0; b<bVals.size(); b++) {
				double density = dist.density(bVals.getX(b));
				allSame &= b==0 || density == 0 || density == maxDensity;
				if (density > maxDensity) {
					maxDensity = density;
					mode = bVals.getX(b);
				}
			}
			if (allSame)
				// uniform distribution, just use the mean
				mode = dist.getMean();
			Preconditions.checkState(Double.isFinite(mode));
			modes[i] = mode;
		}
		return modes;
	}

	private boolean doPlotAlongStrike(File outputDir, String prefix, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors, String faultName, List<FaultSection> sects,
			PaleoseismicConstraintData paleoData, BitSet includedRups) throws IOException {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		HashSet<Integer> parents = new HashSet<>();
		Map<Integer, List<FaultSection>> parentsMap = sects.stream().collect(Collectors.groupingBy(S->S.getParentSectionId()));
		for (FaultSection sect : sects) {
			parents.add(sect.getParentSectionId());
			for (Location loc : sect.getFaultTrace()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		Range latRange = new Range(latTrack.getMin(), latTrack.getMax());
		Range lonRange = new Range(lonTrack.getMin(), lonTrack.getMax());

		boolean latX = SectBySectDetailPlots.isLatX(faultName, latTrack, lonTrack);
		String xLabel;
		Range xRange;
		if (latX) {
			xLabel = "Latitude";
			xRange = latRange;
		} else {
			xLabel = "Longitude";
			xRange = lonRange;
		}

		List<XY_DataSet> emptySectFuncs = new ArrayList<>();
		for (FaultSection sect : sects) {
			XY_DataSet func = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace()) {
				if (latX)
					func.set(loc.getLatitude(), 0d);
				else
					func.set(loc.getLongitude(), 0d);
			}
			emptySectFuncs.add(func);
		}

		List<? extends SectMappedUncertainDataConstraint> paleoConstraints = paleoData.getPaleoRateConstraints();
		PaleoProbabilityModel paleoProb = paleoData.getPaleoProbModel();

		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);
		Preconditions.checkNotNull(bVals);

//		System.out.println("Checking to see if we should plot "+faultName);

		boolean anyCustom = false;
		for (int i=0; i<sects.size(); i++) {
			FaultSection sect = sects.get(i);
			int sectIndex = sect.getSectionId();

			if (posteriors.getSectPosteriors().get(sectIndex) != null) {
//				if (!anyCustom)
//					System.out.println("\t"+sect+" has a posterior: "+ClassUtils.getClassNameWithoutPackage(
//							posteriors.getSectPosteriors().get(sectIndex).getClass()));
				anyCustom = true;
			}
		}
		if (!anyCustom)
			return false;

		double[] modes = calcModes(sects, bVals, posteriors);

		// top plot: overall rates
		// 2nd plot: paleo fits

		List<AlongStrikePlot> plots = new ArrayList<>();

//		for (boolean paleoVisible : new boolean[] {false,true}) {
//		for (boolean paleoVisible : new boolean[] {false}) {
		for (boolean paleoVisible : new boolean[] {true}) {
			double minY = Double.POSITIVE_INFINITY;
			double maxY = 0d;

			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();

			if (paleoVisible) {
				DefaultXY_DataSet dataXY = new DefaultXY_DataSet();
				double halfWhisker = 0.005*xRange.getLength();
				for (int c=0; c<paleoConstraints.size(); c++) {
					SectMappedUncertainDataConstraint constraint = paleoConstraints.get(c);
					int parentID = rs.getFaultSectionData(constraint.sectionIndex).getParentSectionId();
					if (parents.contains(parentID)) {
						double x = latX ? constraint.dataLocation.getLatitude() : constraint.dataLocation.getLongitude();
						dataXY.set(x, constraint.bestEstimate);

						BoundedUncertainty range95 = constraint.estimateUncertaintyBounds(UncertaintyBoundType.CONF_95);

						funcs.add(line(x-halfWhisker, range95.upperBound, x+halfWhisker, range95.upperBound));
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));

						funcs.add(line(x, range95.lowerBound, x, range95.upperBound));
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));

						funcs.add(line(x-halfWhisker, range95.lowerBound, x+halfWhisker, range95.lowerBound));
						chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));

						BoundedUncertainty range68 = constraint.estimateUncertaintyBounds(UncertaintyBoundType.CONF_68);
						// check against a smaller range
						// but 68 could even be negative (determined from std dev, whereas 95 might be direct), so guard against that
						double lowerCheck = Math.max(range68.lowerBound, 0.5*(constraint.bestEstimate + range95.lowerBound));
						double upperCheck = Math.min(range68.upperBound, 0.5*(constraint.bestEstimate + range95.upperBound));
						minY = Math.min(minY, lowerCheck);
						maxY = Math.max(maxY, upperCheck);
					}
				}
				if (dataXY.size() > 0) {
					dataXY.setName("Paleo Constraints");
					funcs.add(dataXY);
					chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 5f, Color.BLACK));
				} else {
					break;
				}
			}

			List<CompletableFuture<double[]>> sectPaleoFutures = new ArrayList<>();
			for (int i=0; i<sects.size(); i++) {
				int sectIndex = sects.get(i).getSectionId();
				sectPaleoFutures.add(CompletableFuture.supplyAsync(()->{
					double[] ret = new double[bVals.size()];
					for (int b=0; b<bVals.size(); b++)
						ret[b] = estimateParticRate(rs, sectIndex,
								bValTargetMFDs[b].getOnFaultSupraSeisNucleationMFDs().get(sectIndex),
								paleoVisible ? paleoProb : null, includedRups);
					return ret;
				}));
			}
			List<double[]> sectPaleoRates = new ArrayList<>();
			for (int i=0; i<sects.size(); i++)
				sectPaleoRates.add(sectPaleoFutures.get(i).join());

			DecimalFormat oDF = new DecimalFormat("0.#");
			for (int i=0; i<sects.size(); i++) {
				XY_DataSet emptyFunc = emptySectFuncs.get(i);

				double rate0 = sectPaleoRates.get(i)[0];
				double rate1 = sectPaleoRates.get(i)[bVals.size()-1];

				funcs.add(copyAtY(emptyFunc, rate0));
				chars.add(charBounds);
				if (i == 0)
					funcs.get(funcs.size()-1).setName("b={"+oDF.format(bVals.getMinX())+", "+oDF.format(bVals.getMaxX())+"}");
				funcs.add(copyAtY(emptyFunc, rate1));
				chars.add(charBounds);
				minY = Math.min(minY, rate0);
				minY = Math.min(minY, rate1);
				maxY = Math.max(maxY, rate0);
				maxY = Math.max(maxY, rate1);
			}
			for (int i=0; i<sects.size(); i++) {
				XY_DataSet emptyFunc = emptySectFuncs.get(i);

				double sumWeight = 0d;
				double sumRateWeight = 0d;
				for (int b=0; b<bVals.size(); b++) {
					double weight = priorDist.density(bVals.getX(b));
					sumWeight += weight;
					sumRateWeight += sectPaleoRates.get(i)[b]*weight;
				}

				funcs.add(copyAtY(emptyFunc, sumRateWeight/sumWeight));
				chars.add(priorDistChar);
				if (i == 0)
					funcs.get(funcs.size()-1).setName("Average prior");
			}
			for (int i=0; i<sects.size(); i++) {
				XY_DataSet emptyFunc = emptySectFuncs.get(i);
				int bIndex = bVals.getClosestXIndex(modes[i]);

				funcs.add(copyAtY(emptyFunc, sectPaleoRates.get(i)[bIndex]));
				chars.add(postModeChar);
				if (i == 0)
					funcs.get(funcs.size()-1).setName("Modal posterior");
			}
			for (int i=0; i<sects.size(); i++) {
				XY_DataSet emptyFunc = emptySectFuncs.get(i);
				FaultSection sect = sects.get(i);
				int sectIndex = sect.getSectionId();

				ContinuousDistribution dist = posteriors.getSectDistribution(sectIndex);
				double sumWeight = 0d;
				double sumRateWeight = 0d;
				for (int b=0; b<bVals.size(); b++) {
					double weight = dist.density(bVals.getX(b));
					sumWeight += weight;
					sumRateWeight += sectPaleoRates.get(i)[b]*weight;
				}

				funcs.add(copyAtY(emptyFunc, sumRateWeight/sumWeight));
				chars.add(postAvgChar);
				if (i == 0)
					funcs.get(funcs.size()-1).setName("Average posterior");
			}

			PlotSpec plot = new PlotSpec(funcs, chars, " ", xLabel,
					paleoVisible ? "Paleo-visible rate estimate" : "Participation rate estimate");
			plot.setLegendInset(RectangleAnchor.BOTTOM_LEFT, 0.025, 0.025, 0.95, false);

			Range yRange = new Range(Math.pow(10, Math.floor(Math.log10(minY))), Math.pow(10, Math.ceil(Math.log10(maxY))));

			plots.add(new AlongStrikePlot(plot, funcs, chars, yRange, true));
		}

		// b-value plot
		plots.add(getPosteriorBValueAlongStrikePlot(posteriors, sects, faultName, emptySectFuncs, xLabel, bVals, modes));

		SectBySectDetailPlots.writeAlongStrikePlots(outputDir, prefix, plots, parentsMap, latX, xLabel, xRange, faultName);
		return true;
	}

	public static void plotSolAlongStrike(File outputDir, String prefix, FaultSystemSolution sol,
			PosteriorSectionBValueDistributions posteriors) throws IOException {
		plotSolAlongStrike(outputDir, prefix, sol, "Solution", null, null, posteriors);
	}

	public static void plotSolAlongStrike(File outputDir, String prefix, FaultSystemSolution sol, String solName,
			FaultSystemSolution compSol, String compSolName, PosteriorSectionBValueDistributions posteriors) throws IOException {
		FaultSystemRupSet rs = sol.getRupSet();
		NamedFaults faults = rs.requireModule(NamedFaults.class);

		for (String faultName : faults.getFaultNames()) {
			List<FaultSection> sects = new ArrayList<>();
			for (FaultSection sect : rs.getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				String sectFaultName = faults.getFaultName(parentID);
				if (sectFaultName != null && sectFaultName.equals(faultName))
					sects.add(sect);
			}

			if (!sects.isEmpty())
				plotSolAlongStrike(outputDir, prefix+"_"+FileNameUtils.simplify(faultName), sol, solName,
						compSol, compSolName, posteriors, faultName, sects);
		}
	}

	private static boolean plotSolAlongStrike(File outputDir, String prefix, FaultSystemSolution sol, String solName,
			FaultSystemSolution compSol, String compName, PosteriorSectionBValueDistributions posteriors,
			String faultName, List<FaultSection> sects) throws IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		HashSet<Integer> parents = new HashSet<>();
		Map<Integer, List<FaultSection>> parentsMap = sects.stream().collect(Collectors.groupingBy(S->S.getParentSectionId()));
		for (FaultSection sect : sects) {
			parents.add(sect.getParentSectionId());
			for (Location loc : sect.getFaultTrace()) {
				latTrack.addValue(loc.lat);
				lonTrack.addValue(loc.lon);
			}
		}
		Range latRange = new Range(latTrack.getMin(), latTrack.getMax());
		Range lonRange = new Range(lonTrack.getMin(), lonTrack.getMax());

		boolean latX = SectBySectDetailPlots.isLatX(faultName, latTrack, lonTrack);
		String xLabel;
		Range xRange;
		if (latX) {
			xLabel = "Latitude";
			xRange = latRange;
		} else {
			xLabel = "Longitude";
			xRange = lonRange;
		}

		List<XY_DataSet> emptySectFuncs = new ArrayList<>();
		for (FaultSection sect : sects) {
			XY_DataSet func = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace()) {
				if (latX)
					func.set(loc.getLatitude(), 0d);
				else
					func.set(loc.getLongitude(), 0d);
			}
			emptySectFuncs.add(func);
		}

		PaleoseismicConstraintData paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);

		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);
		Preconditions.checkNotNull(bVals);

//		System.out.println("Checking to see if we should plot "+faultName);

		boolean anyCustom = false;
		HashSet<Integer> sectIndexes = new HashSet<>();
		for (int i=0; i<sects.size(); i++) {
			FaultSection sect = sects.get(i);

			int sectIndex = sect.getSectionId();
			sectIndexes.add(sectIndex);

			if (posteriors.getSectPosteriors().get(sectIndex) != null) {
//				if (!anyCustom)
//					System.out.println("\t"+sect+" has a posterior: "+ClassUtils.getClassNameWithoutPackage(
//							posteriors.getSectPosteriors().get(sectIndex).getClass()));
				anyCustom = true;
			}
		}
		if (!anyCustom)
			return false;

		List<SectMappedUncertainDataConstraint> paleoConstraints = new ArrayList<>();
		for (SectMappedUncertainDataConstraint paleo : paleoData.getPaleoRateConstraints())
			if (sectIndexes.contains(paleo.sectionIndex))
				paleoConstraints.add(paleo);

		double[] modes = calcModes(sects, bVals, posteriors);
		List<AlongStrikePlot> plots = new ArrayList<>();

		ReportMetadata meta;
		if (compSol == null)
			meta = new ReportMetadata(new RupSetMetadata(solName, sol));
		else
			meta = new ReportMetadata(new RupSetMetadata(solName, sol), new RupSetMetadata(compName, compSol));
		plots.add(SectBySectDetailPlots.buildPaleoRatePlot(meta, sects, faultName, emptySectFuncs, xLabel,
				0.025, xRange, latX, paleoData, paleoConstraints, null));

		// b-value plot
		plots.add(getPosteriorBValueAlongStrikePlot(posteriors, sects, faultName, emptySectFuncs, xLabel, bVals, modes));

		SectBySectDetailPlots.writeAlongStrikePlots(outputDir, prefix, plots, parentsMap, latX, xLabel, xRange, faultName);
		return true;
	}

	public static AlongStrikePlot getPosteriorBValueAlongStrikePlot(PosteriorSectionBValueDistributions posteriors,
			List<FaultSection> faultSects, String faultName, List<XY_DataSet> emptySectFuncs, String xLabel) {
		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);
		Preconditions.checkNotNull(bVals);
		double[] modes = calcModes(faultSects, bVals, posteriors);
		return getPosteriorBValueAlongStrikePlot(posteriors, faultSects, faultName, emptySectFuncs, xLabel, bVals, modes);
	}
	public static AlongStrikePlot getPosteriorBValueAlongStrikePlot(PosteriorSectionBValueDistributions posteriors,
			List<FaultSection> faultSects, String faultName, List<XY_DataSet> emptySectFuncs, String xLabel,
			EvenlyDiscretizedFunc bVals, double[] modes) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		// add posterior fractiles
		for (int i=0; i<faultSects.size(); i++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(i);
			FaultSection sect = faultSects.get(i);
			int sectIndex = sect.getSectionId();

			ContinuousDistribution dist = posteriors.getSectDistribution(sectIndex);

//			funcs.add(uncertCopyAtY(emptyFunc, dist.inverseCumulativeProbability(0.5),
//					dist.inverseCumulativeProbability(0.025), dist.inverseCumulativeProbability(0.975)));
//			chars.add(postShadedChar);
//			funcs.add(uncertCopyAtY(emptyFunc, dist.inverseCumulativeProbability(0.5),
//					dist.inverseCumulativeProbability(0.14), dist.inverseCumulativeProbability(0.86)));
//			chars.add(postShadedChar);
			funcs.add(uncertCopyAtY(emptyFunc, dist.inverseCumulativeProbability(0.5),
					dist.inverseCumulativeProbability(0.25), dist.inverseCumulativeProbability(0.75)));
			chars.add(postShadedChar);
		}

		double priorAvg = posteriors.getPriorDist().getMean();
		double prior25 = posteriors.getPriorDist().inverseCumulativeProbability(0.25);
		double prior75 = posteriors.getPriorDist().inverseCumulativeProbability(0.75);
		double minX = emptySectFuncs.stream().mapToDouble(F->F.getMinX()).min().getAsDouble();
		double maxX = emptySectFuncs.stream().mapToDouble(F->F.getMaxX()).max().getAsDouble();
		funcs.add(new DefaultXY_DataSet(minX, priorAvg, maxX, priorAvg));
		chars.add(priorDistChar);
		funcs.get(funcs.size()-1).setName("Average prior");
		funcs.add(new DefaultXY_DataSet(minX, prior25, maxX, prior25));
		chars.add(priorIQRDistChar);
		funcs.get(funcs.size()-1).setName("Prior IQR");
		funcs.add(new DefaultXY_DataSet(minX, prior75, maxX, prior75));
		chars.add(priorIQRDistChar);
//		for (int i=0; i<faultSects.size(); i++) {
//			XY_DataSet emptyFunc = emptySectFuncs.get(i);
//
//			funcs.add(copyAtY(emptyFunc, priorAvg));
//			chars.add(priorDistChar);
//			if (i == 0)
//				funcs.get(funcs.size()-1).setName("Average prior");
//
//			funcs.add(copyAtY(emptyFunc, prior25));
//			chars.add(priorIQRDistChar);
//			funcs.add(copyAtY(emptyFunc, prior75));
//			chars.add(priorIQRDistChar);
//			if (i == 0)
//				funcs.get(funcs.size()-1).setName("Prior IQR");
//		}
		for (int i=0; i<faultSects.size(); i++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(i);

			funcs.add(copyAtY(emptyFunc, modes[i]));
			chars.add(postModeChar);
			if (i == 0)
				funcs.get(funcs.size()-1).setName("Modal posterior");
		}
		for (int i=0; i<faultSects.size(); i++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(i);
			FaultSection sect = faultSects.get(i);
			int sectIndex = sect.getSectionId();

			double avgB = posteriors.getSectDistribution(sectIndex).getMean();

			funcs.add(copyAtY(emptyFunc, avgB));
			chars.add(postAvgChar);
			if (i == 0) {
				funcs.get(funcs.size()-1).setName("Average posterior");
				funcs.add(uncertCopyAtY(emptyFunc, -10, -11, -9));
//				chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, modAlpha(Colors.tab_lightorange, 140)));
//				funcs.get(funcs.size()-1).setName("95% and 68% bounds");
				chars.add(postShadedChar);
				funcs.get(funcs.size()-1).setName("Posterior IQR");
			}
		}

		PlotSpec plot = new PlotSpec(funcs, chars, " ", xLabel, "b-value");
		plot.setLegendInset(RectangleAnchor.BOTTOM_LEFT, 0.025, 0.025, 0.95, false);

		Range yRange = new Range(bVals.getMinX()-0.02, bVals.getMaxX()+0.02);

		return new AlongStrikePlot(plot, funcs, chars, yRange, false);
	}

	private static PlotCurveCharacterstics getForThickness(PlotCurveCharacterstics pChar, float thickness) {
		return new PlotCurveCharacterstics(pChar.getLineType(), thickness, pChar.getSymbol(), pChar.getSymbolWidth(), pChar.getColor());
	}

	private static double estimateParticRate(FaultSystemRupSet rs, int sectIndex,
			IncrementalMagFreqDist nuclMFD, PaleoProbabilityModel paleoProb, BitSet includedRups) {
		int mMinIndex = nuclMFD.getClosestXIndex(rs.getMinMagForSection(sectIndex));
		int mMaxIndex = nuclMFD.getClosestXIndex(rs.getMaxMagForSection(sectIndex));
		int numMag = 1 + mMaxIndex - mMinIndex;
		List<List<Integer>> magRupIndexes = new ArrayList<>(numMag);
		for (int m=0; m<numMag; m++)
			magRupIndexes.add(new ArrayList<>());
		double[] rupAreaSums = new double[numMag];
		for (int rupIndex : rs.getRupturesForSection(sectIndex)) {
			if (includedRups == null || includedRups.get(rupIndex)) {
				double mag = rs.getMagForRup(rupIndex);
				int magIndex = nuclMFD.getClosestXIndex(mag) - mMinIndex;
				rupAreaSums[magIndex] += rs.getAreaForRup(rupIndex);
				magRupIndexes.get(magIndex).add(rupIndex);
			}
		}

		double sectArea = rs.getAreaForSection(sectIndex);

		double ret = 0d;

		for (int m=0; m<numMag; m++) {
			List<Integer> rups = magRupIndexes.get(m);
			if (rups.isEmpty())
				continue;
			double nuclRate = nuclMFD.getY(m+mMinIndex);
			if (nuclRate == 0d)
				continue;
			double particScalar = rupAreaSums[m] / (rups.size() * sectArea);
			double particRate = nuclRate * particScalar;
			if (paleoProb == null) {
				ret += particRate;
			} else {
				double particRateEach = particRate / rups.size();
				Preconditions.checkState(Double.isFinite(particRateEach) && particRateEach > 0d);
				for (int rupIndex : rups)
					ret += particRateEach * paleoProb.getProbPaleoVisible(rs, rupIndex, sectIndex);
			}
		}
		return ret;
	}

	static XY_DataSet copyAtY(XY_DataSet func, double y) {
		double[] xVals = new double[func.size()];
		double[] yVals = new double[xVals.length];
		for (int i=0; i<xVals.length; i++) {
			xVals[i] = func.getX(i);
			yVals[i] = y;
		}
		return new DefaultXY_DataSet(xVals, yVals);
	}

	static UncertainArbDiscFunc uncertCopyAtY(XY_DataSet func, double y, double yLower, double yUpper) {
		ArbitrarilyDiscretizedFunc middleFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lowerFunc = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upperFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : func) {
			middleFunc.set(pt.getX(), y);
			lowerFunc.set(pt.getX(), yLower);
			upperFunc.set(pt.getX(), yUpper);
		}
		return new UncertainArbDiscFunc(middleFunc, lowerFunc, upperFunc);
	}

	private static XY_DataSet line(double x1, double y1, double x2, double y2) {
		return new DefaultXY_DataSet(new double[] { x1, x2 }, new double[] { y1, y2 });
	}

}
