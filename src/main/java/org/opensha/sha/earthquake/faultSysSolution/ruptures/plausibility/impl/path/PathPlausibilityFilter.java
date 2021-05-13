package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This is a filter that tests various plausibility rules as paths through a rupture. Each cluster in the rupture is
 * considered as a potential nucleation point, and then the rupture is tested as is grows outward from that nucleation
 * cluster (bi/unilaterally for ruptures with more than 2 clusters). If fractPassThreshold == 0, then a rupture passes
 * if any nucleation clusters pass. Otherwise, at least fractPassThreshold fraction of the nucleation clusters must pass.
 * 
 * @author kevin
 *
 */
public class PathPlausibilityFilter implements PlausibilityFilter {
	
	public static class Scalar<E extends Number & Comparable<E>> extends PathPlausibilityFilter
	implements ScalarValuePlausibiltyFilter<E> {
		
		private NucleationClusterEvaluator.Scalar<E> evaluator;
		
		public Scalar(NucleationClusterEvaluator.Scalar<E> evaluator) {
			this(0f, evaluator);
		}

		public Scalar(float fractPassThreshold, NucleationClusterEvaluator.Scalar<E> evaluator) {
			super(fractPassThreshold, false, evaluator);
			this.evaluator = evaluator;
		}

		@Override
		public E getValue(ClusterRupture rupture) {
			if (rupture.getTotalNumJumps()  == 0)
				return null;
			List<E> vals = new ArrayList<>();
			for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
//				float val = testNucleationPoint(navigator, nucleationCluster, false, false);
				E val = evaluator.getNucleationClusterValue(rupture, nucleationCluster, false);
				vals.add(val);
			}
			if (fractPassThreshold > 0f) {
				// if we need N paths to pass, return the Nth largest value outside
				// (such that if and only if that value passes, the rupture passes)
				int numPaths = vals.size();
				int numNeeded = Integer.max(1, (int)Math.ceil(fractPassThreshold*numPaths));
				Collections.sort(vals, worstToBestComparator());
				return vals.get(vals.size()-numNeeded);
			}
//			Float bestVal = getWorseValue(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
			E bestVal = null;
			for (E val : vals) {
				if (bestVal == null || isValueBetter(val, bestVal))
					bestVal = val;
			}
			return bestVal;
		}

		@Override
		public Range<E> getAcceptableRange() {
			return evaluator.getAcceptableRange();
		}

		@Override
		public String getScalarName() {
			return evaluator.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			return evaluator.getScalarUnits();
		}
		
	}
	
	protected final float fractPassThreshold;
	private final NucleationClusterEvaluator[] evaluators;
	private final boolean logicalOr;
	
	private transient final PlausibilityResult failureType;
	
	public PathPlausibilityFilter(NucleationClusterEvaluator... evaluators) {
		this(0f, evaluators);
	}

	public PathPlausibilityFilter(float fractPassThreshold, NucleationClusterEvaluator... evaluators) {
		this(fractPassThreshold, false, evaluators);
	}

	public PathPlausibilityFilter(float fractPassThreshold, boolean logicalOr, NucleationClusterEvaluator... evaluators) {
		Preconditions.checkState(fractPassThreshold <= 1f);
		this.fractPassThreshold = fractPassThreshold;
		this.logicalOr = logicalOr;
		Preconditions.checkArgument(evaluators.length > 0, "must supply at least one path evaluator");
		this.evaluators = evaluators;
		PlausibilityResult failureType = null;
		for (NucleationClusterEvaluator eval : evaluators) {
			if (failureType == null)
				failureType = eval.getFailureType();
			else
				failureType = failureType.logicalAnd(eval.getFailureType());
		}
		this.failureType = failureType;
		Preconditions.checkState(!failureType.isPass());
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		List<FaultSubsectionCluster> clusters = Lists.newArrayList(rupture.getClustersIterable());
		int numPaths = clusters.size();
		int numPasses = 0;
		int numNeeded = 1;
		if (fractPassThreshold > 0f)
			numNeeded = Integer.max(1, (int)Math.ceil(fractPassThreshold*numPaths));
		HashSet<FaultSubsectionCluster> skipClusters = null;
		if (rupture instanceof FilterDataClusterRupture) {
			FilterDataClusterRupture fdRupture = (FilterDataClusterRupture)rupture;
			Object filterData = fdRupture.getFilterData(this);
			if (filterData != null && filterData instanceof HashSet<?>)
				skipClusters = new HashSet<>((HashSet<FaultSubsectionCluster>)filterData); 
			else
				skipClusters = new HashSet<>();
			fdRupture.addFilterData(this, skipClusters);
		}
		for (FaultSubsectionCluster nucleationCluster : clusters) {
			if (skipClusters != null && skipClusters.contains(nucleationCluster)) {
				// we can skip this one because it already failed in a subset of this rupture so it will
				// never pass here
				if (verbose)
					System.out.println("Skipping known cluster that won't work: "+nucleationCluster);
				continue;
			}
			
			PlausibilityResult result = PlausibilityResult.PASS;
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster);
			for (NucleationClusterEvaluator eval : evaluators) {
				if (verbose)
					System.out.println("Testing "+eval.getName()+"...");
				PlausibilityResult subResult = eval.testNucleationCluster(rupture, nucleationCluster, verbose);
				if (verbose)
					System.out.println("\t"+eval.getName()+": "+subResult);
				if (logicalOr)
					result = result.logicalOr(subResult);
				else
					result = result.logicalAnd(subResult);
			}
			if (result.isPass())
				numPasses++;
			else if (skipClusters != null)
				skipClusters.add(nucleationCluster);
			if (!verbose && numPasses >= numNeeded)
				return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": "+numPasses+"/"+numPaths+" pass, "+numNeeded+" needed");
		if (numPasses >= numNeeded)
			return PlausibilityResult.PASS;
		return failureType;
	}
	
	private String getPathString() {
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return "Half Paths";
			if (fractPassThreshold == 1f/3f)
				return "1/3 Paths";
			if (fractPassThreshold == 2f/3f)
				return "2/3 Paths";
			if (fractPassThreshold == 0.25f)
				return "1/4 Paths";
			if (fractPassThreshold == 0.75f)
				return "3/4 Paths";
			return fractPassThreshold+"x Paths ";
		}
		return "Path";
	}

	@Override
	public String getShortName() {
		String paths = getPathString().replaceAll(" ", "");
		if (evaluators.length > 1)
			return paths+"["+evaluators.length+" criteria]";
		return paths+evaluators[0].getShortName();
	}

	@Override
	public String getName() {
		if (evaluators.length == 1)
			return getPathString()+" "+evaluators[0].getName();
		return getPathString()+" ["+Arrays.stream(evaluators).map(E -> E.getName()).collect(Collectors.joining(", "))+"]";
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return splayed;
	}
	
	public NucleationClusterEvaluator[] getEvaluators() {
		return evaluators;
	}

	public float getFractPassThreshold() {
		return fractPassThreshold;
	}

	public boolean isLogicalOr() {
		return logicalOr;
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private Gson gson;
		private ClusterConnectionStrategy connStrategy;
		private SectionDistanceAzimuthCalculator distAzCalc;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.connStrategy = connStrategy;
			this.distAzCalc = distAzCalc;
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof PathPlausibilityFilter);
			PathPlausibilityFilter filter = (PathPlausibilityFilter)value;
			out.beginObject();

			out.name("fractPassThreshold").value(filter.fractPassThreshold);
			out.name("logicalOr").value(filter.logicalOr);
			out.name("evaluators").beginArray();
			for (NucleationClusterEvaluator eval : filter.evaluators) {
				out.beginObject();
				out.name("class").value(eval.getClass().getName());
				out.name("value");
				if (eval instanceof CumulativeProbPathEvaluator) {
					out.beginObject();
					CumulativeProbPathEvaluator pathEval = (CumulativeProbPathEvaluator)eval;
					out.name("minProbability").value(pathEval.minProbability);
					out.name("failureType").value(pathEval.failureType.name());
					out.name("calcs").beginArray();
					for (RuptureProbabilityCalc calc : pathEval.calcs) {
						out.beginObject();
						out.name("class").value(calc.getClass().getName());
						out.name("value");
						gson.toJson(calc, calc.getClass(), out);
						out.endObject();
					}
					out.endArray();
					out.endObject();
				} else {
					gson.toJson(eval, eval.getClass(), out);
				}
				out.endObject();
			}
			out.endArray();
			
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			
			Float fractPassThreshold = null;
			Boolean logicalOr = null;
			NucleationClusterEvaluator[] evaluators = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "fractPassThreshold":
					fractPassThreshold = (float)in.nextDouble();
					break;
				case "logicalOr":
					logicalOr = in.nextBoolean();
					break;
				case "evaluators":
					ArrayList<NucleationClusterEvaluator> list = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						in.beginObject();
						
						Class<NucleationClusterEvaluator> type = null;
						NucleationClusterEvaluator eval = null;
						
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "class":
								try {
									type = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
								} catch (ClassNotFoundException e) {
									throw ExceptionUtils.asRuntimeException(e);
								}
								break;
							case "value":
								Preconditions.checkNotNull(type, "Class must preceed value in PathPlausibility JSON");
								if (type.equals(CumulativeProbPathEvaluator.class)) {
									in.beginObject();
									Float minProbability = null;
									PlausibilityResult failureType = null;
									RuptureProbabilityCalc[] calcs = null;
									while (in.hasNext()) {
										switch (in.nextName()) {
										case "minProbability":
											minProbability = (float)in.nextDouble();
											break;
										case "failureType":
											failureType = PlausibilityResult.valueOf(in.nextString());
											break;
										case "calcs":
											in.beginArray();
											List<RuptureProbabilityCalc> calcList = new ArrayList<>();
											while (in.hasNext()) {
												in.beginObject();
												Class<RuptureProbabilityCalc> calcType = null;
												RuptureProbabilityCalc calc = null;
												while (in.hasNext()) {
													switch (in.nextName()) {
													case "class":
														try {
															calcType = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
														} catch (ClassNotFoundException e) {
															throw ExceptionUtils.asRuntimeException(e);
														}
														break;
													case "value":
														Preconditions.checkNotNull(calcType, "Class must preceed value in PathPlausibility JSON");
														calc = gson.fromJson(in, calcType);
														break;

													default:
														throw new IllegalStateException("Unexpected JSON field");
													}
												}
												Preconditions.checkNotNull(calc, "Calculator is null?");
												calcList.add(calc);
												in.endObject();
											}
											in.endArray();
											calcs = calcList.toArray(new RuptureProbabilityCalc[0]);
											break;

										default:
											throw new IllegalStateException("Unexpected JSON field");
										}
									}
									in.endObject();
									eval = new CumulativeProbPathEvaluator(minProbability, failureType, calcs);
								} else {
									eval = gson.fromJson(in, type);
								}
								break;

							default:
								throw new IllegalStateException("Unexpected JSON field");
							}
						}
						Preconditions.checkNotNull(eval, "Evaluator is null?");
						eval.init(connStrategy, distAzCalc);
						list.add(eval);
						
						in.endObject();
					}
					in.endArray();
					Preconditions.checkState(!list.isEmpty(), "No prob calcs?");
					evaluators = list.toArray(new NucleationClusterEvaluator[0]);
					break;

				default:
					throw new IllegalStateException("Unexpected JSON field");
				}
			}
			in.endObject();

			Preconditions.checkNotNull(fractPassThreshold, "fractPassThreshold not supplied");
			Preconditions.checkNotNull(logicalOr, "logicalOr not supplied");
			Preconditions.checkNotNull(evaluators, "evaluators not supplied");
			if (evaluators.length == 1 && evaluators[0] instanceof NucleationClusterEvaluator.Scalar<?>)
				return new PathPlausibilityFilter.Scalar<>(fractPassThreshold, (NucleationClusterEvaluator.Scalar<?>)evaluators[0]);
			return new PathPlausibilityFilter(fractPassThreshold, logicalOr, evaluators);
		}
		
	}
	
//	public static void main(String[] args) throws ZipException, IOException, DocumentException {
//		// for profiling
//		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
//		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(
//				new File(rupSetsDir, "fm3_1_cmlAz_cffClusterPathPositive.zip"));
//		
//		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
//				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5);
//		stiffnessCalc.setPatchAlignment(PatchAlignment.FILL_OVERLAP);
//		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
//		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
//		if (stiffnessCacheFile.exists())
//			stiffnessCache.loadCacheFile(stiffnessCacheFile);
//		
//		AggregatedStiffnessCalculator aggCalc =
////				AggregatedStiffnessCalculator.buildMedianPatchSumSects(StiffnessType.CFF, stiffnessCalc);
//				AggregatedStiffnessCalculator.builder(StiffnessType.CFF, stiffnessCalc)
//				.flatten()
//				.process(AggregationMethod.MEDIAN)
//				.process(AggregationMethod.SUM)
////				.passthrough()
//				.process(AggregationMethod.SUM).get();
//		System.out.println("Aggregator: "+aggCalc);
//		PathPlausibilityFilter filter = new PathPlausibilityFilter(aggCalc, 0f);
//		
//		ClusterRupture largest = null;
//		for (ClusterRupture rup : rupSet.getClusterRuptures())
//			if (largest == null || rup.getTotalNumSects() > largest.getTotalNumSects())
//				largest = rup;
//		System.out.println("Benchmarking with a largest rupture ("+largest.getTotalNumSects()+" sects):\n\t"+largest);
////		int num = 1000000;
//		int num = 1;
//		boolean verbose = true;
//		Stopwatch watch = Stopwatch.createStarted();
//		for (int i=0; i<num; i++) {
//			if (i % 1000 == 0) {
//				double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
//				double rate = i/secs;
//				System.out.println("processed "+i+" in "+(float)secs+" s:\t"+(float)rate+" per second");
//			}
//			filter.apply(largest, verbose);
//		}
//		watch.stop();
//		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
//		double rate = num/secs;
//		System.out.println("processed "+num+" in "+(float)secs+" s: "+(float)rate+" per second");
//	}

}
