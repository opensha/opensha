package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * This filter tests the Coulomb compatibility of each possible path through the given rupture. It tries each
 * cluster as the nucleation point, and builds outwards to each rupture endpoint. Ruptures pass if at least
 * one nucleation point is viable (or if at least the supplied fraction of paths pass).
 * 
 * This differs from ClusterPathCoulombCompatibilityFilter in that each section of each destination cluster is
 * added and evaluated one at a time (or two at a time for bilateral rupture on that cluster).
 * 
 * Jumps need not occur at the closest subsection pair between two clusters (or wherever the given rupture defined the
 * jump to occur, which depends on the connection strategy). If jumpToMostFavorable is true, then jumps will instead
 * be treated as occurring at the most favorable subsection up to the supplied maximum jump distance.
 * 
 * @author kevin
 *
 */
public class SectPathCoulombCompatibilityFilter implements ScalarCoulombPlausibilityFilter {
	
	private AggregatedStiffnessCalculator aggCalc;
	private Range<Float> acceptableRange;
	private float fractPassThreshold = 0; // default pass to if 1 or more paths pass
	private boolean jumpToMostFavorable;
	private float maxJumpDist;
	private transient SectionDistanceAzimuthCalculator distAzCalc;
	private boolean failFuturePossible;

	public SectPathCoulombCompatibilityFilter(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			float fractPassThreshold, boolean failFuturePossible) {
		this(aggCalc, acceptableRange, fractPassThreshold, false, Float.NaN, null, failFuturePossible);
	}

	public SectPathCoulombCompatibilityFilter(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			float fractPassThreshold, boolean jumpToMostFavorable, float maxJumpDist,
			SectionDistanceAzimuthCalculator distAzCalc, boolean failFuturePossible) {
		this.aggCalc = aggCalc;
		this.acceptableRange = acceptableRange;
		Preconditions.checkState(fractPassThreshold <= 1f);
		this.fractPassThreshold = fractPassThreshold;
		this.jumpToMostFavorable = jumpToMostFavorable;
		if (jumpToMostFavorable) {
			Preconditions.checkState(maxJumpDist > 0d);
			Preconditions.checkNotNull(distAzCalc);
			this.maxJumpDist = maxJumpDist;
			this.distAzCalc = distAzCalc;
		} else {
			this.maxJumpDist = Float.NaN;
			this.distAzCalc = null;
		}
		this.failFuturePossible = failFuturePossible;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
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
			if (skipClusters != null && skipClusters.contains(nucleationCluster))
				// we can skip this one because it already failed in a subset of this rupture so it will
				// never pass here
				continue;
			float val = testNucleationPoint(navigator, nucleationCluster, !verbose, verbose);
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster
						+", val="+val+", result: "+(acceptableRange.contains(val)));
			if (acceptableRange.contains(val))
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
		if (failFuturePossible)
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumJumps()  == 0)
			return null;
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		List<Float> vals = new ArrayList<>();
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			float val = testNucleationPoint(navigator, nucleationCluster, false, false);
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
		Float bestVal = getWorseValue(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
		for (Float val : vals)
			bestVal = getBestValue(val, bestVal);
		return bestVal;
	}
	
	private FaultSection getFavorableJumpingPoint(List<FaultSection> currentSects, FaultSubsectionCluster fromCluster,
			FaultSubsectionCluster toCluster, float maxJumpDist) {
		Preconditions.checkState(jumpToMostFavorable);
		List<FaultSection> allowedJumps = new ArrayList<>();
		for (FaultSection sect : toCluster.subSects) {
			for (FaultSection source : fromCluster.subSects) {
				if ((float)distAzCalc.getDistance(sect, source) <= maxJumpDist) {
					allowedJumps.add(sect);
					break;
				}
			}
		}
		Preconditions.checkState(!allowedJumps.isEmpty(), "No jumpst within %s km found between %s and %s",
				maxJumpDist, fromCluster, toCluster);
		if (allowedJumps.size() == 1)
			return allowedJumps.get(0);
		// find the most favorable one
		float bestVal = Float.NaN;
		FaultSection bestSect = null;
		for (FaultSection sect : allowedJumps) {
			float myVal = (float)aggCalc.calc(currentSects, sect);
			if (Double.isNaN(bestVal) || isValueBetter(myVal, bestVal)) {
				bestVal = myVal;
				bestSect = sect;
			}
		}
		Preconditions.checkNotNull(bestSect);
		return bestSect;
	}
	
	private float testNucleationPoint(RuptureTreeNavigator navigator,
			FaultSubsectionCluster nucleationCluster, boolean shortCircuit, boolean verbose) {
		HashSet<FaultSubsectionCluster> curClusters = new HashSet<>();
		curClusters.add(nucleationCluster);
		FaultSubsectionCluster predecessor = navigator.getPredecessor(nucleationCluster);
		Float worstVal = getBestValue(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
		if (verbose)
			System.out.println("Testing strand(s) with start="+nucleationCluster);
		
		List<FaultSubsectionCluster> destClusters = new ArrayList<>();
		if (predecessor != null)
			destClusters.add(predecessor);
		destClusters.addAll(navigator.getDescendants(nucleationCluster));
		for (FaultSubsectionCluster destCluster : destClusters) {
			if (verbose)
				System.out.println("\tTesting to cluster="+destCluster);
			List<FaultSection> curSects = new ArrayList<>(nucleationCluster.subSects);
			
			FaultSection jumpSect;
			Jump origJump = navigator.getJump(nucleationCluster, destCluster);
			if (jumpToMostFavorable)
				jumpSect = getFavorableJumpingPoint(curSects, nucleationCluster, destCluster,
						Float.max(maxJumpDist, (float)origJump.distance));
			else
				jumpSect = origJump.toSection;
			
			float strandVal = testStrand(navigator, curClusters, curSects, destCluster, jumpSect, shortCircuit, verbose);
			if (verbose)
				System.out.println("\tPredecessor strand val="+strandVal);
			worstVal = getWorseValue(worstVal, strandVal);
			if (shortCircuit && !acceptableRange.contains(worstVal))
				return worstVal;
		}
		
		return worstVal;
	}
	
	private float testStrand(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
			List<FaultSection> strandSects, FaultSubsectionCluster destCluster, FaultSection destSect,
			boolean shortCircuit, boolean verbose) {
		// first build out this cluster, one section at a time;
		int jumpIndex = destCluster.subSects.indexOf(destSect);
		Preconditions.checkState(jumpIndex >= 0);
		int maxIndex = destCluster.subSects.size()-1;
		int curOffset = 0;
		Float worstVal = getBestValue(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
		while (true) {
			FaultSection dest1 = null, dest2 = null;
			if (curOffset == 0) {
				dest1 = destSect;
			} else {
				int ind1 = jumpIndex + curOffset;
				int ind2 = jumpIndex - curOffset;
				if (ind1 <= maxIndex)
					dest1 = destCluster.subSects.get(ind1);
				if (ind2 >= 0)
					dest2 = destCluster.subSects.get(ind2);
			}
			if (dest1 == null && dest2 == null)
				break;
			if (dest1 != null) {
				float val = (float)aggCalc.calc(strandSects, dest1);
//				if (verbose)
//					System.out.println("\t\tval="+val+" for "+strandClusters.size()+" clusters ("
//							+strandSects.size()+" sects) to sect "+dest1.getSectionId());
				worstVal = getWorseValue(worstVal, (float)val);
				if (shortCircuit && !acceptableRange.contains(worstVal))
					return worstVal;
			}
			if (dest2 != null) {
				float val = (float)aggCalc.calc(strandSects, dest2);
//				if (verbose)
//					System.out.println("\t\tval="+val+" for "+strandClusters.size()+" clusters ("
//							+strandSects.size()+" sects) to sect "+dest2.getSectionId());
				worstVal = getWorseValue(worstVal, (float)val);
				if (shortCircuit && !acceptableRange.contains(worstVal))
					return worstVal;
			}
			if (dest1 != null)
				strandSects.add(dest1);
			if (dest2 != null)
				strandSects.add(dest2);
			curOffset++;
		}
		if (verbose)
			System.out.println("\t\tworst value after cluster: "+worstVal);
		
		// now continue down this strand, taking any jumps
		HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
		newStrandClusters.add(destCluster);
		
		List<FaultSubsectionCluster> nextClusters = new ArrayList<>();
		// add predecessor if new
		FaultSubsectionCluster predecessor = navigator.getPredecessor(destCluster);
		if (predecessor != null && !strandClusters.contains(predecessor))
			nextClusters.add(predecessor);
		// add descendants if new
		for (FaultSubsectionCluster descendant : navigator.getDescendants(destCluster))
			if (!strandClusters.contains(descendant))
				nextClusters.add(descendant);
		
		// go down all paths
		for (FaultSubsectionCluster newDestCluster : nextClusters) {
			if (verbose)
				System.out.println("\tTesting to cluster="+newDestCluster);
			List<FaultSection> curSects = new ArrayList<>(strandSects);
			
			FaultSection jumpSect;
			Jump origJump = navigator.getJump(destCluster, newDestCluster);
			if (jumpToMostFavorable)
				jumpSect = getFavorableJumpingPoint(curSects, destCluster, newDestCluster,
						Float.max(maxJumpDist, (float)origJump.distance));
			else
				jumpSect = origJump.toSection;
			
			float strandVal = testStrand(navigator, newStrandClusters, curSects, newDestCluster, jumpSect,
					shortCircuit, verbose);
			worstVal = getWorseValue(worstVal, strandVal);
			if (shortCircuit && !acceptableRange.contains(worstVal))
				return worstVal;
		}
		
		// if we made it here, this either the end of the line or all downstream strand extensions pass
		return worstVal;
	}

	@Override
	public String getShortName() {
		String type = "["+aggCalc.getScalarShortName()+"]";
		String threshStr = getRangeStr();
		String pathStr = jumpToMostFavorable ? "SFav"+new DecimalFormat("0.#").format(maxJumpDist)+"Path" : "SPath";
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return "Half"+pathStr+"s"+type+threshStr;
			if (fractPassThreshold == 1f/3f)
				return "1/3"+pathStr+"s"+type+threshStr;
			if (fractPassThreshold == 2f/3f)
				return "2/3"+pathStr+"s"+type+threshStr;
			if (fractPassThreshold == 0.25f)
				return "1/4"+pathStr+"s"+type+threshStr;
			if (fractPassThreshold == 0.75f)
				return "3/4"+pathStr+"s"+type+threshStr;
			return fractPassThreshold+pathStr+"s"+type+threshStr;
		}
		return pathStr+type+threshStr;
	}

	@Override
	public String getName() {
		String type = "["+aggCalc.getScalarName()+"]";
		String sectStr = jumpToMostFavorable ? "Sect Favorable ("+new DecimalFormat("0.#").format(maxJumpDist)+"km)" : "Sect";
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return sectStr+" Half Paths "+type+" "+getRangeStr();
			if (fractPassThreshold == 1f/3f)
				return sectStr+" 1/3 Paths "+type+" "+getRangeStr();
			if (fractPassThreshold == 2f/3f)
				return sectStr+" 2/3 Paths "+type+" "+getRangeStr();
			if (fractPassThreshold == 0.25f)
				return sectStr+" 1/4 Paths "+type+" "+getRangeStr();
			if (fractPassThreshold == 0.75f)
				return sectStr+" 3/4 Paths "+type+" "+getRangeStr();
			return sectStr+" "+fractPassThreshold+"x Paths "+type+" "+getRangeStr();
		}
		return sectStr+" Path "+type+" "+getRangeStr();
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return acceptableRange;
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return splayed;
	}

	@Override
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}
	
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private SectionDistanceAzimuthCalculator distAzCalc;
		private Gson gson;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.distAzCalc = distAzCalc;
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof SectPathCoulombCompatibilityFilter);
			SectPathCoulombCompatibilityFilter filter = (SectPathCoulombCompatibilityFilter)value;
			out.beginObject();
			out.name("aggCalc");
			gson.toJson(filter.aggCalc, filter.aggCalc.getClass(), out);
			out.name("acceptableRange");
			gson.toJson(filter.acceptableRange, TypeToken.getParameterized(Range.class, Float.class).getType(), out);
			out.name("fractPassThreshold").value(filter.fractPassThreshold);
			out.name("jumpToMostFavorable").value(filter.jumpToMostFavorable);
			out.name("maxJumpDist").value(filter.maxJumpDist);
			out.name("failFuturePossible").value(filter.failFuturePossible);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			
			AggregatedStiffnessCalculator aggCalc = null;
			Range<Float> acceptableRange = null;
			Float fractPassThreshold = null;
			Boolean jumpToMostFavorable = null;
			float maxJumpDist = Float.NaN;
			Boolean failFuturePossible = null;
			
			while (in.hasNext()) {
				String nextName = in.nextName();
				switch (nextName) {
				case "aggCalc":
					aggCalc = gson.fromJson(in, AggregatedStiffnessCalculator.class);
					break;
				case "acceptableRange":
					acceptableRange = gson.fromJson(in, TypeToken.getParameterized(Range.class, Float.class).getType());
					break;
				case "fractPassThreshold":
					fractPassThreshold = (float)in.nextDouble();
					break;
				case "jumpToMostFavorable":
					jumpToMostFavorable = in.nextBoolean();
					break;
				case "maxJumpDist":
					maxJumpDist = (float)in.nextDouble();
					break;
				case "failFuturePossible":
					failFuturePossible = in.nextBoolean();
					break;
				case "distAzCalc":
					in.skipValue();
					break;

				default:
					throw new IllegalStateException("unexpected token: "+nextName);
				}
			}
			
			in.endObject();
			
			return new SectPathCoulombCompatibilityFilter(aggCalc, acceptableRange, fractPassThreshold,
					jumpToMostFavorable, maxJumpDist, distAzCalc, failFuturePossible);
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
//		SectPathCoulombCompatibilityFilter filter = new SectPathCoulombCompatibilityFilter(aggCalc, 0f);
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
