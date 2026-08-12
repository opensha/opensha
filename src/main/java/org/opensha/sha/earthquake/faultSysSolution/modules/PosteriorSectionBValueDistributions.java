package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution.DiscretizationType;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampleLevels;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class PosteriorSectionBValueDistributions implements JSON_BackedModule,
BranchAverageableModule<PosteriorSectionBValueDistributions>, SplittableRuptureModule<PosteriorSectionBValueDistributions> {
	
	/* required */
	private ContinuousDistribution priorDist;
	private List<ContinuousDistribution> sectPosteriors;
	
	/* optional */
	private List<double[]> sectPaleoSiteWeights;
	private List<ContinuousDistribution> paleoSitePosteriors;
	private List<EvenlyDiscretizedFunc> paleoSiteMisfits;
	
	@SuppressWarnings("unused") // deserialization
	private PosteriorSectionBValueDistributions() {};
	
	public PosteriorSectionBValueDistributions(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors) {
		this(priorDist, sectPosteriors, null, null, null);
	}
	
	public PosteriorSectionBValueDistributions(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors,
			List<double[]> sectPaleoSiteWeights, List<ContinuousDistribution> paleoSitePosteriors,
			List<EvenlyDiscretizedFunc> paleoSiteMisfits) {
		init(priorDist, sectPosteriors, sectPaleoSiteWeights, paleoSitePosteriors, paleoSiteMisfits);
	}

	private void init(ContinuousDistribution priorDist, List<ContinuousDistribution> sectPosteriors,
			List<double[]> sectPaleoSiteWeights, List<ContinuousDistribution> paleoSitePosteriors,
			List<EvenlyDiscretizedFunc> paleoSiteMisfits) {
		Preconditions.checkNotNull(priorDist, "Prior cannot be null");
		this.priorDist = priorDist;
		Preconditions.checkNotNull(sectPosteriors, "Sect posteriors cannot be null");
		this.sectPosteriors = Collections.unmodifiableList(sectPosteriors);
		if (paleoSitePosteriors != null && paleoSiteMisfits != null)
			Preconditions.checkState(paleoSiteMisfits.size() == paleoSitePosteriors.size());
		this.paleoSitePosteriors = paleoSitePosteriors == null ? null : Collections.unmodifiableList(paleoSitePosteriors);
		this.paleoSiteMisfits = paleoSiteMisfits == null ? null : Collections.unmodifiableList(paleoSiteMisfits);
		if (sectPaleoSiteWeights != null) {
			this.sectPaleoSiteWeights = Collections.unmodifiableList(sectPaleoSiteWeights);
			int expectedNum = paleoSitePosteriors == null ? -1 : paleoSitePosteriors.size(); 
			for (double[] weights : sectPaleoSiteWeights) {
				if (weights != null) {
					if (expectedNum < 0)
						expectedNum = weights.length;
					Preconditions.checkState(weights.length == expectedNum);
				}
			}
		}
	}

	public ContinuousDistribution getPriorDist() {
		return priorDist;
	}

	public List<ContinuousDistribution> getSectPosteriors() {
		// already unmodifiable
		return sectPosteriors;
	}
	
	public ContinuousDistribution getSectDistribution(int sectIndex) {
		ContinuousDistribution posterior = sectPosteriors.get(sectIndex);
		return posterior == null ? priorDist : posterior;
	}
	
	public List<double[]> getSectPaleoSiteWeights() {
		// already unmodifiable or null
		return sectPaleoSiteWeights;
	}

	public List<ContinuousDistribution> getPaleoSitePosteriors() {
		// already unmodifiable or null
		return paleoSitePosteriors;
	}

	public List<EvenlyDiscretizedFunc> getPaleoSiteMisfits() {
		// already unmodifiable or null
		return paleoSiteMisfits;
	}

	@Override
	public String getFileName() {
		return "posterior_b_values.json";
	}
	
	@Override
	public String getName() {
		return "Posterior b-values";
	}
	
	private static ContinuousDistributionTypeAdapter distAdapter = ContinuousDistributionTypeAdapter.get();
	private static EvenlyDiscretizedFunc.Adapter funcAdapter = new EvenlyDiscretizedFunc.Adapter();
	
	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginObject();
		
		out.name("prior");
		distAdapter.write(out, priorDist);
		
		out.name("sectPosteriors").beginArray();
		for (ContinuousDistribution dist : sectPosteriors) {
			if (dist == null)
				out.nullValue();
			else
				distAdapter.write(out, dist);
		}
		out.endArray();
		
		if (sectPaleoSiteWeights != null) {
			out.name("sectPaleoSiteWeights").beginArray();
			
			for (double[] weights : sectPaleoSiteWeights) {
				if (weights == null) {
					out.nullValue();
				} else {
					out.beginArray();
					for (double value : weights)
						out.value(value);
					out.endArray();
				}
			}
			
			out.endArray();
		}
		
		if (paleoSitePosteriors != null) {
			out.name("paleoSitePosteriors").beginArray();
			for (ContinuousDistribution dist : paleoSitePosteriors) {
				if (dist == null)
					out.nullValue();
				else
					distAdapter.write(out, dist);
			}
			out.endArray();
		}
		
		if (paleoSiteMisfits != null) {
			out.name("paleoSiteMisfits").beginArray();
			for (EvenlyDiscretizedFunc dist : paleoSiteMisfits) {
				if (dist == null)
					out.nullValue();
				else
					funcAdapter.write(out, dist);
			}
			out.endArray();
		}
		
		out.endObject();
	}
	
	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		in.beginObject();
		
		ContinuousDistribution priorDist = null;
		List<ContinuousDistribution> sectPosteriors = null;
		
		List<double[]> sectPaleoSiteWeights = null;
		List<ContinuousDistribution> paleoSitePosteriors = null;
		List<EvenlyDiscretizedFunc> paleoSiteMisfits = null; 
		
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
			case "prior": {
				priorDist = distAdapter.read(in);
				break;
			}
			case "sectPosteriors": {
				sectPosteriors = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						sectPosteriors.add(null);
					}else {
						sectPosteriors.add(distAdapter.read(in));
					}
				}
				in.endArray();
				break;
			}
			case "sectPaleoSiteWeights": {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					break;
				}
				List<Double> weights = new ArrayList<>();
				sectPaleoSiteWeights = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						sectPaleoSiteWeights.add(null);
					} else {
						weights.clear();
						in.beginArray();
						while (in.hasNext())
							weights.add(in.nextDouble());
						in.endArray();
						sectPaleoSiteWeights.add(Doubles.toArray(weights));
					}
				}
				in.endArray();
				break;
			}
			case "paleoSitePosteriors": {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					break;
				}
				paleoSitePosteriors = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						paleoSitePosteriors.add(null);
					} else {
						paleoSitePosteriors.add(distAdapter.read(in));
					}
				}
				in.endArray();
				break;
			}
			case "paleoSiteMisfits": {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					break;
				}
				paleoSiteMisfits = new ArrayList<>();
				in.beginArray();
				while (in.hasNext()) {
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						paleoSiteMisfits.add(null);
					} else {
						paleoSiteMisfits.add(funcAdapter.read(in));
					}
				}
				in.endArray();
				break;
			}
			default:
				System.err.println("Unexpected JSON value: "+name);
				in.skipValue();
			}
		}
		
		in.endObject();
		
		init(priorDist, sectPosteriors, sectPaleoSiteWeights, paleoSitePosteriors, paleoSiteMisfits);
	}

	@Override
	public AveragingAccumulator<PosteriorSectionBValueDistributions> averagingAccumulator() {
		return new Accumulator();
	}
	
	public static EvenlyDiscretizedFunc detectBValues(PosteriorSectionBValueDistributions module) {
		for (ContinuousDistribution dist : module.sectPosteriors) {
			if (dist != null)
				if (dist instanceof EvenlyDiscrFuncContinuousDistribution funcDist) 
					return funcDist.getFunc();
//					return funcDist.getDiscretizationType();
		}
		if (module.paleoSitePosteriors != null) {
			for (ContinuousDistribution dist : module.paleoSitePosteriors) {
				if (dist != null)
					if (dist instanceof EvenlyDiscrFuncContinuousDistribution funcDist)
						return funcDist.getFunc();
//						return funcDist.getDiscretizationType();
			}
		}
		
		if (module.paleoSiteMisfits != null) {
			for (EvenlyDiscretizedFunc misfit : module.paleoSiteMisfits)
				if (misfit != null)
					return misfit.deepClone();
		}
		return null;
	}
	
	private static DiscretizationType detectDistributionType(PosteriorSectionBValueDistributions module) {
		for (ContinuousDistribution dist : module.sectPosteriors) {
			if (dist != null)
				if (dist instanceof EvenlyDiscrFuncContinuousDistribution funcDist) 
					return funcDist.getDiscretizationType();
		}
		if (module.paleoSitePosteriors != null) {
			for (ContinuousDistribution dist : module.paleoSitePosteriors) {
				if (dist != null)
					if (dist instanceof EvenlyDiscrFuncContinuousDistribution funcDist)
						return funcDist.getDiscretizationType();
			}
		}
		return null;
	}
	
	private static class Accumulator implements AveragingAccumulator<PosteriorSectionBValueDistributions> {
		
		private double sumWeight;
		private EvenlyDiscretizedFunc bValues = null;
		private DiscretizationType discrType = null;
		
		// for distribution equality
		private ContinuousDistribution firstPrior;
		private boolean priorsIdentical = true;
		private FuncAverager priorAvg;
		
		private List<FuncAverager> sectPosteriorAvg;
		private List<double[]> sectPaleoWeightAvg;
		
		private List<FuncAverager> paleoSitePosteriorAvg;
		private List<FuncAverager> paleoSiteMisfitAvg;

		@Override
		public Class<PosteriorSectionBValueDistributions> getType() {
			return PosteriorSectionBValueDistributions.class;
		}

		@Override
		public synchronized void process(PosteriorSectionBValueDistributions module, double relWeight) {
			if (firstPrior == null) {
				// first
				
				// find b-value discretization
				bValues = detectBValues(module);
				if (bValues == null) {
					// not specified, discretize ourselves
					Preconditions.checkState(Double.isFinite(module.priorDist.getSupportLowerBound()),
							"Prior lower support support must be finite if discretization not detected: %s",
							module.priorDist.getSupportLowerBound());
					Preconditions.checkState(Double.isFinite(module.priorDist.getSupportUpperBound()),
							"Prior upper support support must be finite if discretization not detected: %s",
							module.priorDist.getSupportUpperBound());
					bValues = new EvenlyDiscretizedFunc(module.priorDist.getSupportLowerBound(),
							module.priorDist.getSupportUpperBound(), 21);
				}
				
				// find function discretization
				discrType = detectDistributionType(module);
				if (discrType == null)
					discrType = DiscretizationType.INTERPOLATE;
				
				firstPrior = module.priorDist;
				priorAvg = new FuncAverager();
				
				sectPosteriorAvg = new ArrayList<>(module.sectPosteriors.size());
				for (int i=0; i<module.sectPosteriors.size(); i++)
					sectPosteriorAvg.add(null);
				
				if (module.sectPaleoSiteWeights != null) {
					sectPaleoWeightAvg = new ArrayList<>(module.sectPaleoSiteWeights.size());
					for (int i=0; i<module.getSectPaleoSiteWeights().size(); i++)
						sectPaleoWeightAvg.add(null);
				}
				
				if (module.paleoSitePosteriors != null) {
					paleoSitePosteriorAvg = new ArrayList<>(module.paleoSitePosteriors.size());
					for (int i=0; i<module.paleoSitePosteriors.size(); i++)
						paleoSitePosteriorAvg.add(null);
				}
				
				if (module.paleoSiteMisfits != null) {
					paleoSiteMisfitAvg = new ArrayList<>(module.paleoSiteMisfits.size());
					for (int i=0; i<module.paleoSiteMisfits.size(); i++)
						paleoSiteMisfitAvg.add(null);
				}
			}
			
			priorsIdentical &= ContinuousDistributionTypeAdapter.distSerializationEquals(firstPrior, module.priorDist);
			
			Preconditions.checkState(module.sectPosteriors.size() == sectPosteriorAvg.size());
			for (int s=0; s<sectPosteriorAvg.size(); s++) {
				FuncAverager avg = sectPosteriorAvg.get(s);
				ContinuousDistribution dist = module.sectPosteriors.get(s);
				if (avg == null && dist == null)
					// we're null and it has always been null
					continue;
				if (avg == null) {
					// first non-null
					avg = new FuncAverager();
					sectPosteriorAvg.set(s, avg);
					if (sumWeight > 0d) {
						// use prior for all prev nulls
						avg.runningWeightSum = priorAvg.runningWeightSum.deepClone();
						avg.sumWeight = sumWeight;
					}
				} else if (dist == null) {
					// previously non-null, but we're null; use prior
					avg.add(module.priorDist, relWeight, bValues, discrType);
					continue;
				}
				// average it in
				avg.add(dist, relWeight, bValues, discrType);
			}

			// this must be after the above because we use the running-prior-avg before this module to fill in nulls above
			priorAvg.add(module.priorDist, relWeight, bValues, discrType);
			
			if (sectPaleoWeightAvg != null) {
				if (module.sectPaleoSiteWeights == null) {
					sectPaleoWeightAvg = null;
				} else {
					Preconditions.checkState(module.sectPaleoSiteWeights.size() == sectPaleoWeightAvg.size());
					for (int s=0; s<sectPaleoWeightAvg.size(); s++) {
						double[] avg = sectPaleoWeightAvg.get(s);
						double[] weights = module.sectPaleoSiteWeights.get(s);
						if (avg == null && weights == null)
							// we're null and it has always been null
							continue;
						if (avg == null) {
							// first non-null
							avg = new double[weights.length];
							sectPaleoWeightAvg.set(s, avg);
							// don't need to add in any prev non-null (null means weight is zero)
						} else if (weights == null) {
							// previously non-null, but we're null; weights are zero can skip
							continue;
						}
						Preconditions.checkState(avg.length == weights.length);
						for (int i=0; i<avg.length; i++)
							avg[i] = Math.fma(weights[i], relWeight, avg[i]);
					}
				}
			}
			
			if (paleoSitePosteriorAvg != null) {
				if (module.paleoSitePosteriors == null) {
					paleoSitePosteriorAvg = null;
				} else {
					Preconditions.checkState(module.paleoSitePosteriors.size() == paleoSitePosteriorAvg.size());
					for (int p=0; p<paleoSitePosteriorAvg.size(); p++) {
						FuncAverager avg = paleoSitePosteriorAvg.get(p);
						ContinuousDistribution dist = module.paleoSitePosteriors.get(p);
						if (avg == null && dist == null)
							continue;
						if (avg == null) {
							avg = new FuncAverager();
							paleoSitePosteriorAvg.set(p, avg);
						} else if (dist == null) {
							continue;
						}
						avg.add(dist, relWeight, bValues, discrType);
					}
				}
			}
			
			if (paleoSiteMisfitAvg != null) {
				if (module.paleoSiteMisfits == null) {
					paleoSiteMisfitAvg = null;
				} else {
					Preconditions.checkState(module.paleoSiteMisfits.size() == paleoSiteMisfitAvg.size());
					for (int p=0; p<paleoSiteMisfitAvg.size(); p++) {
						FuncAverager avg = paleoSiteMisfitAvg.get(p);
						EvenlyDiscretizedFunc misfit = module.paleoSiteMisfits.get(p);
						if (avg == null && misfit == null)
							continue;
						if (avg == null) {
							avg = new FuncAverager();
							paleoSiteMisfitAvg.set(p, avg);
						} else if (misfit == null) {
							continue;
						}
						avg.add(misfit, relWeight);
					}
				}
			}
			
			sumWeight += relWeight;
		}

		@Override
		public PosteriorSectionBValueDistributions getAverage() {
			ContinuousDistribution prior = priorsIdentical ?
					firstPrior : new EvenlyDiscrFuncContinuousDistribution(priorAvg.getAverage(), discrType);
			
			List<ContinuousDistribution> sectPosteriors = new ArrayList<>(sectPosteriorAvg.size());
			for (FuncAverager avg : sectPosteriorAvg)
				sectPosteriors.add(avg == null ?
						null : new EvenlyDiscrFuncContinuousDistribution(avg.getAverage(), discrType));
			
			
			List<double[]> sectPaleoWeights = null;
			if (sectPaleoWeightAvg != null) {
				sectPaleoWeights = new ArrayList<>(sectPaleoWeightAvg.size());
				double weightScale = 1d/sumWeight;
				for (double[] avg : sectPaleoWeightAvg) {
					if (avg != null && (float)sumWeight != 1f)
						for (int i=0; i<avg.length; i++)
							avg[i] *= weightScale;
					sectPaleoWeights.add(avg);
				}
			}
			
			List<ContinuousDistribution> paleoSitePosteriors = null;
			if (paleoSitePosteriorAvg != null) {
				paleoSitePosteriors = new ArrayList<>(paleoSitePosteriorAvg.size());
				for (FuncAverager avg : paleoSitePosteriorAvg)
					paleoSitePosteriors.add(avg == null ?
							null : new EvenlyDiscrFuncContinuousDistribution(avg.getAverage(), discrType));
			}
			
			List<EvenlyDiscretizedFunc> paleoSiteMisfits = null;
			if (paleoSiteMisfitAvg != null) {
				paleoSiteMisfits = new ArrayList<>(paleoSiteMisfitAvg.size());
				for (FuncAverager avg : paleoSiteMisfitAvg)
					paleoSiteMisfits.add(avg == null ? null : avg.getAverage());
			}
			// prevent reuse
			sumWeight = Double.NaN;
			sectPosteriorAvg = null;
			return new PosteriorSectionBValueDistributions(prior, sectPosteriors, sectPaleoWeights, paleoSitePosteriors, paleoSiteMisfits);
		}
		
	}
	
	private static class FuncAverager {
		private EvenlyDiscretizedFunc runningWeightSum;
		private double sumWeight;
		
		public void add(ContinuousDistribution dist, double weight, EvenlyDiscretizedFunc xValues, DiscretizationType discrType) {
			if (dist instanceof EvenlyDiscrFuncContinuousDistribution funcDist)
				add(funcDist.getFunc(), weight);
			else
				add(EvenlyDiscrFuncContinuousDistribution.discretize(dist, xValues, discrType).getFunc(), weight);
		}
		
		public void add(EvenlyDiscretizedFunc func, double weight) {
			if (this.runningWeightSum == null)
				this.runningWeightSum = new EvenlyDiscretizedFunc(func.getMinX(), func.getMaxX(), func.size());
			else
				Preconditions.checkState(EvenlyDiscretizedFunc.areXValuesIdentical(func, runningWeightSum));
			if (weight == 0d)
				return;
			for (int i=0; i<func.size(); i++)
				runningWeightSum.set(i, Math.fma(func.getY(i), weight, runningWeightSum.getY(i)));
			sumWeight += weight;
		}
		
		public EvenlyDiscretizedFunc getAverage() {
			if ((float)sumWeight != 1f)
				runningWeightSum.scale(1d/sumWeight);
			
			EvenlyDiscretizedFunc ret = runningWeightSum;
			// make sure not reused
			runningWeightSum = null;
			sumWeight = Double.NaN;
			return ret;
		}
	}
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class SamplingNode<S extends SectDistributionSampler> extends SectionSupraSeisBValues.SectSpecificDistributionSample<S> {
		
		private PosteriorSectionBValueDistributions dists;

		public SamplingNode(String name, String shortName, String prefix, double weight, S sampler) {
			super(name, shortName, prefix, weight, sampler);
		}

		@Override
		public void initDistributions(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			dists = rupSet.requireModule(PosteriorSectionBValueDistributions.class);
		}

		@Override
		public ContinuousDistribution getSectDistribution(FaultSection subSect) {
			return dists.getSectDistribution(subSect.getSectionId());
		}
		
	}
	
	public static class UniformSamplingLevel
	extends SectDistributionSampleLevels.UniformSamplingLevel<SamplingNode<FixedFractileSampler>> {

		public UniformSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName, 0d, "b Distribution Sample ", "bDistSample", "bDistSample");
		}

		@Override
		public SamplingNode<FixedFractileSampler> build(FixedFractileSampler value,
				double weight, String name, String shortName, String filePrefix) {
			return new SamplingNode<>(name, shortName, filePrefix, weight, value);
		}

		@Override
		public Class<? extends SamplingNode<FixedFractileSampler>> getType() {
			return (Class<? extends SamplingNode<FixedFractileSampler>>) (Class<?>) SamplingNode.class;
		}
		
	}

	@Override
	public PosteriorSectionBValueDistributions getForRuptureSubSet(FaultSystemRupSet rupSubSet,
			RuptureSubSetMappings mappings) {
		return new PosteriorSectionBValueDistributions(priorDist,
				getForSectionSubSet(sectPosteriors, mappings),
				getWeightsForSectionSubSet(sectPaleoSiteWeights, mappings),
				copyList(paleoSitePosteriors), copyList(paleoSiteMisfits));
	}

	@Override
	public PosteriorSectionBValueDistributions getForSplitRuptureSet(FaultSystemRupSet splitRupSet,
			RuptureSetSplitMappings mappings) {
		return new PosteriorSectionBValueDistributions(priorDist,
				getForSplitSections(sectPosteriors, mappings),
				getWeightsForSplitSections(sectPaleoSiteWeights, mappings),
				copyList(paleoSitePosteriors), copyList(paleoSiteMisfits));
	}
	
	private static <E> List<E> copyList(List<E> list) {
		return list == null ? null : new ArrayList<>(list);
	}
	
	private static <E> List<E> getForSectionSubSet(List<E> origList, RuptureSubSetMappings mappings) {
		List<E> ret = new ArrayList<>(mappings.getNumRetainedSects());
		for (int s=0; s<mappings.getNumRetainedSects(); s++)
			ret.add(origList.get(mappings.getOrigSectID(s)));
		return ret;
	}
	
	private static List<double[]> getWeightsForSectionSubSet(List<double[]> origWeights, RuptureSubSetMappings mappings) {
		if (origWeights == null)
			return null;
		List<double[]> ret = new ArrayList<>(mappings.getNumRetainedSects());
		for (int s=0; s<mappings.getNumRetainedSects(); s++)
			ret.add(copyArray(origWeights.get(mappings.getOrigSectID(s))));
		return ret;
	}
	
	private static <E> List<E> getForSplitSections(List<E> origList, RuptureSetSplitMappings mappings) {
		List<E> ret = new ArrayList<>(mappings.getNewNumSections());
		for (int s=0; s<mappings.getNewNumSections(); s++)
			ret.add(origList.get(mappings.getOrigSectID(s)));
		return ret;
	}
	
	private static List<double[]> getWeightsForSplitSections(List<double[]> origWeights, RuptureSetSplitMappings mappings) {
		if (origWeights == null)
			return null;
		List<double[]> ret = new ArrayList<>(mappings.getNewNumSections());
		for (int s=0; s<mappings.getNewNumSections(); s++)
			ret.add(copyArray(origWeights.get(mappings.getOrigSectID(s))));
		return ret;
	}
	
	private static double[] copyArray(double[] array) {
		return array == null ? null : array.clone();
	}

}
