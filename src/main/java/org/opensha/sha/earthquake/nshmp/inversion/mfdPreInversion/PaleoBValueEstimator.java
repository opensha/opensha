package org.opensha.sha.earthquake.nshmp.inversion.mfdPreInversion;

import java.awt.Color;
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
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ExclusionaryInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.PosteriorSectionBValueDistributions;
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
	
	/* [potentially-]configurable with defaults */
	private DoubleFunction<Double> logLikelihoodFunction;
	private double connectivityWeightB;
	// if true, sections use weighted combinations of distributions
	// if false, sections use weighted combinations of likelihoods
	private boolean sectUseWeightedDistribution = false;
	private DiscretizationType interpType = DiscretizationType.INTERPOLATE;
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
	
	public void setTargetMFDCalc(Function<MFDCalcInputs, InversionTargetMFDs> targetMFDCalc) {
		this.targetMFDCalc = targetMFDCalc;
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
			paleoBValMisfits.add(bVals.deepClone());
			paleoBValEstRates.add(bVals.deepClone());
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
		
		for (int i=0; i<bValTargetMFDs.length; i++) {
			rs.removeModuleInstances(InversionTargetMFDs.class);
			double b = bVals.getX(i);
			if (verbose) {
				System.out.println("====================================");
				System.out.println("Site results for b="+(float)b);
			}
			for (int s=0; s<paleoConstraints.size(); s++) {
				SectMappedUncertainDataConstraint paleoConstr = paleoConstraints.get(s);
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
				double paleoVisibleRate = 0d;
				for (int m=0; m<mfd.size(); m++) {
					double totNuclRate = mfd.getY(m);
					if (totNuclRate > 0d) {
						double particRate = totNuclRate * sumParticScalars[m]/binCounts[m];
						paleoVisibleRate += particRate * sumPaleoScalars[m]/binCounts[m];
					}
				}
//				double misfit = (paleoVisibleRate - paleoConstr.bestEstimate)/paleoConstr.getPreferredStdDev();
				double misfit = paleoConstr.estimateDataZ(paleoVisibleRate);
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
			double[] posteriorWeights = new double[bVals.size()];
			double sumWeights = 0d;
			for (int i=0; i<posteriorWeights.length; i++) {
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
				posteriorWeights[i] = priorDensity * Math.exp(logLikelihood);
				sumWeights += posteriorWeights[i];
			}

			Preconditions.checkState(Double.isFinite(sumWeights) && sumWeights > 0d,
					"Sum of posterior weights=%s must be finite and positive for site=%s", sumWeights,
					paleoConstr.name);

			EvenlyDiscretizedFunc posteriorPDF = new EvenlyDiscretizedFunc(
					bVals.getMinX(), bVals.getMaxX(), bVals.size());

			// Normalize such that sum(pdf[i] * delta) = 1
			double normalization = sumWeights * posteriorPDF.getDelta();

			for (int i=0; i<posteriorWeights.length; i++)
				posteriorPDF.set(i, posteriorWeights[i] / normalization);
			
			siteLogLikelihoods.add(logLikelihoods);
			sitePosteriorDists.add(new EvenlyDiscrFuncContinuousDistribution(posteriorPDF, interpType));
			
			siteRups[s] = new BitSet(numRups);
			for (int r : rs.getRupturesForSection(paleoConstr.sectionIndex))
				siteRups[s].set(r);
		}
		
		System.out.println("Building section weighted b-value posteriors");
		int numSects = rs.getNumSections();
		List<CompletableFuture<SectResult>> sectBDistFutures = new ArrayList<>(numSects);
		
		for (int s=0; s<numSects; s++) {
			int sectIndex = s;
			int parentID = rs.getFaultSectionData(sectIndex).getParentSectionId();
			String faultName = faults == null ? null : faults.getFaultName(parentID);
			
			sectBDistFutures.add(CompletableFuture.supplyAsync(() -> {
				List<Integer> connectedPaleoIndexes;
				if (faultName != null && faultPaleoIndexes.containsKey(faultName)) {
					connectedPaleoIndexes = faultPaleoIndexes.get(faultName);
				} else if (parentPaleoIndexes.containsKey(parentID)) {
					connectedPaleoIndexes = parentPaleoIndexes.get(parentID);
				} else {
					// no connected paleo data, use prior
					return new SectResult(null, null, 1d);
				}
				
				IncrementalMagFreqDist mfd = connectivityWeightTargetMFDs.getOnFaultSupraSeisNucleationMFDs().get(sectIndex);
				
				if (mfd.calcSumOfY_Vals() == 0d)
					return new SectResult(null, null, 1d);
				
				/*
				 * Weighting scheme:
				 * 
				 * figure out estimated fractional rate of each rupture by converting the MFD to estimated participation
				 * rates and dividing the total rate for each magnitude bin evenly among them
				 * 
				 * for each rupture, give that fractional rate as weight to each paleo PDF that it hits (divided evenly
				 * among them, or to the prior if none)
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
							if (siteRups[paleoIndex].get(rupIndex))
								rupPaleoIndexes.set(paleoIndex);
						int numPaleo = rupPaleoIndexes.cardinality();
						if (numPaleo == 0) {
							// rupture hits no paleo sites
							weightNoPaleo += particRateEach;
						} else {
							double rupRatePerPaleo = particRateEach / (double)numPaleo;
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
				if (sectUseWeightedDistribution) {
					WeightedList<ContinuousDistribution> distWeights = new WeightedList<>();
					
					if (weightNoPaleo > 0d) {
						// weight for ruptures that hit no paleo sites
						distWeights.add(priorDist, weightNoPaleo);
					}
					
					for (int paleoIndex : connectedPaleoIndexes) {
						double paleoWeight = weightPerPaleo[paleoIndex];
						if (paleoWeight > 0d)
							distWeights.add(sitePosteriorDists.get(paleoIndex), paleoWeight);
					}
					Preconditions.checkState(!distWeights.isEmpty());
					Preconditions.checkState(distWeights.isNormalized());
					distribution = EvenlyDiscrFuncContinuousDistribution.discretize(
							new WeightedContinuousDistribution(distWeights), bVals, interpType);
				} else {
					EvenlyDiscretizedFunc posterior = bVals.deepClone();

					double maxLogPosterior = Double.NEGATIVE_INFINITY;

					for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
						double b = bVals.getX(bIndex);
						double logPosterior = Math.log(priorDist.density(b));

						for (int paleoIndex : connectedPaleoIndexes) {
							double paleoWeight = weightPerPaleo[paleoIndex];
							if (paleoWeight > 0d) {
								double logLikelihood = siteLogLikelihoods.get(paleoIndex)[bIndex];

								logPosterior += paleoWeight * logLikelihood;
							}
						}

						posterior.set(bIndex, logPosterior);
						maxLogPosterior = Math.max(maxLogPosterior, logPosterior);
					}

					// exponentiate and normalize
					double sum = 0d;
					for (int bIndex=0; bIndex<posterior.size(); bIndex++) {
						double density = Math.exp(posterior.getY(bIndex) - maxLogPosterior);
						posterior.set(bIndex, density);
						sum += density;
					}

					double scalar = 1d / (sum * posterior.getDelta());
					posterior.scale(scalar);
					
					distribution = new EvenlyDiscrFuncContinuousDistribution(posterior, interpType);
				}
				return new SectResult(distribution, weightPerPaleo, weightNoPaleo);
			}));
		}

		List<ContinuousDistribution> sectPosteriors = new ArrayList<>(numSects);
		List<double[]> sectPaleoWeights = new ArrayList<>();
		int numSet = 0;
		for (CompletableFuture<SectResult> future : sectBDistFutures) {
			SectResult result = future.join();
			if (result.distribution != null)
				numSet++;
			sectPosteriors.add(result.distribution);
			sectPaleoWeights.add(result.paleoSiteWeights);
		}
		
		this.bValTargetMFDs = bValTargetMFDs;
		this.branch = branch;
		
		System.out.println("DONE calculating section b-value posteriors for "+numSet+"/"+numSects+" sections");
		
		return new PosteriorSectionBValueDistributions(priorDist, sectPosteriors, sectPaleoWeights, sitePosteriorDists, paleoBValMisfits);
	}
	
	private static record SectResult(ContinuousDistribution distribution, double[] paleoSiteWeights, double noPaleoWeight) {}; 
	
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
	
	private static PlotCurveCharacterstics priorDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Colors.tab_green);
	private static PlotCurveCharacterstics parentDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Colors.tab_blue);
	private static PlotCurveCharacterstics otherDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	private static PlotCurveCharacterstics posteriorDistChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Colors.tab_orange);
	private static PlotCurveCharacterstics posteriorAvgDistChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK);
	private static PlotCurveCharacterstics charBounds = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.DARK_GRAY);
	private static PlotCurveCharacterstics postModeChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Colors.tab_blue);
	private static PlotCurveCharacterstics postAvgChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Colors.tab_orange);
	
	private static LightFixedXFunc thicknessForWeights = new LightFixedXFunc(
			0d, 1d,
			0.5d, 4d,
			1d, 5d);
	
	public static void plotSectDistributions(File outputDir, FaultSystemRupSet rs,
			PosteriorSectionBValueDistributions posteriors) throws IOException {
		NamedFaults faults = rs.getModule(NamedFaults.class);
		
		Map<Integer, List<FaultSection>> parentMappedSects = rs.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S->S.getParentSectionId()));
		ContinuousDistribution priorDist = posteriors.getPriorDist();
		EvenlyDiscretizedFunc bVals = PosteriorSectionBValueDistributions.detectBValues(posteriors);
		

		PaleoseismicConstraintData paleoData = rs.requireModule(PaleoseismicConstraintData.class);
		List<? extends SectMappedUncertainDataConstraint> paleoConstraints = paleoData.getPaleoRateConstraints();
		
		List<EvenlyDiscretizedFunc> paleoBValMisfits = posteriors.getPaleoSiteMisfits();
		List<ContinuousDistribution> sitePosteriorDists = posteriors.getPaleoSitePosteriors();
		
		Map<Integer, String> parentWithinNamedPrefixes = new HashMap<>();
//		namedFaultResults
		for (String faultName : faults.getFaultNames()) {
			List<Integer> parents = faults.getParentIDsForFault(faultName);
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			List<Location> parentMiddles = new ArrayList<>();
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
			}
			
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
				for (int p=0; p<paleoSiteWeights.length; p++) {
					paleoSiteWeights[p] += sectWeights[p];
					sectNoPaleoWeight -= sectWeights[p];
				}
			}
			mySectDists.add(sectDist, 1d);
			if (sectNoPaleoWeight < 0d) {
				// floating point errors can make this barely negative
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
			if (sameParent) {
				if (firstSame) {
					misfits = misfits.deepClone();
					pdf = pdf.deepClone();
					misfits.setName("Paleo site (this section)");
//					pdf.setName(misfits.getName());
					firstSame = false;
				}
				misfitFuncs.add(misfits);
				misfitChars.add(getForThickness(parentDistChar, thickness));
				pdfFuncs.add(pdf);
				pdfChars.add(getForThickness(parentDistChar, thickness));
			} else {
				if (firstOther) {
					misfits = misfits.deepClone();
					pdf = pdf.deepClone();
					misfits.setName("Paleo site (other connected sections)");
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

		boolean anyCustom = false;
		for (int i=0; i<sects.size(); i++) {
			FaultSection sect = sects.get(i);
			int sectIndex = sect.getSectionId();
			
			anyCustom |= posteriors.getSectPosteriors().get(sectIndex) != null;
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
		plots.add(getPosteriorBValueAlongStrikePlot(posteriors, sects, faultName, emptySectFuncs, xLabel));
		
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
		double priorAvg = posteriors.getPriorDist().getMean();
		for (int i=0; i<faultSects.size(); i++) {
			XY_DataSet emptyFunc = emptySectFuncs.get(i);
			
			funcs.add(copyAtY(emptyFunc, priorAvg));
			chars.add(priorDistChar);
			if (i == 0)
				funcs.get(funcs.size()-1).setName("Average prior");
		}
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
			if (i == 0)
				funcs.get(funcs.size()-1).setName("Average posterior");
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

	private static XY_DataSet line(double x1, double y1, double x2, double y2) {
		return new DefaultXY_DataSet(new double[] { x1, x2 }, new double[] { y1, y2 });
	}

}
