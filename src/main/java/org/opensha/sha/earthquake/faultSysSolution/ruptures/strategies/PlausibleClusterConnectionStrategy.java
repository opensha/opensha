package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.PathPlausibilityFilter.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This connection strategy uses one or more plausibility filters to determine the best jumping point between each pair
 * of fault subsection clusters.
 * 
 * If there are multiple viable connection points (i.e., less than the specified maximum distance and passing all filters),
 * then the one that allows the most subsections between the two clusters to participate in a single rupture is taken. Extra
 * weight can be assigned to cluster endpoints via the endPointBonus field, with a default value of 1 meaning that section end
 * points count as double. This will encourage clusters to rupture to a an endpoint if possible (leaving fewer hanging chads).
 * 
 * Ties are generally broken by taking the shorter jump. If one of the supplied filters is a scalar value filter with an
 * associated range, then scalar values will be used to break ties if the tied jumps are within equivalentDistThreshold
 * of each other (which defaults to 2km).
 * 
 * @author kevin
 *
 */
public class PlausibleClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;
	private List<PlausibilityFilter> filters;
	private ScalarValuePlausibiltyFilter<Double> scalarFilter;
	private Range<Double> scalarRange;
	
	public static final double EQUIV_DIST_THRESH_DEFAULT = 2d;
	public static final int JUMP_AT_END_POINT_BONUS_DEFAULT = 1;
	
	// distances within this threshold are considered equivalent and a jump with a better scalar value will be chosen
	private double equivalentDistThreshold = EQUIV_DIST_THRESH_DEFAULT;
	private int endPointBonus = JUMP_AT_END_POINT_BONUS_DEFAULT;

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, PlausibilityFilter... filters) {
		this(subSects, distCalc, maxJumpDist, Lists.newArrayList(filters));
	}

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, List<PlausibilityFilter> filters) {
		super(subSects);
		init(distCalc, maxJumpDist, filters);
	}

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double maxJumpDist, List<PlausibilityFilter> filters) {
		super(subSects, clusters);
		init(distCalc, maxJumpDist, filters);
	}
	
	private void init(SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, List<PlausibilityFilter> filters) {
		Preconditions.checkState(!filters.isEmpty());
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
		this.filters = filters;
		for (PlausibilityFilter filter : filters) {
			if (filter instanceof ScalarValuePlausibiltyFilter<?>) {
				Range<?> range = ((ScalarValuePlausibiltyFilter<?>)filter).getAcceptableRange();
				if (range != null) {
					scalarFilter = new ScalarValuePlausibiltyFilter.DoubleWrapper((ScalarValuePlausibiltyFilter<?>)filter);
					scalarRange = scalarFilter.getAcceptableRange();
					break;
				}
			}
		}
	}

	/**
	 * @return the threshold between which two distances are thought to be equivalent. If candidate jumps are otherwise
	 * equivalent and have scalar values associated, the jump with the better scalar value will be taken
	 */
	public double getEquivalentDistThreshold() {
		return equivalentDistThreshold;
	}

	/**
	 * Sets the threshold between which two distances are thought to be equivalent. If candidate jumps are otherwise
	 * equivalent and have scalar values associated, the jump with the better scalar value will be taken
	 * @param equivalentDistThreshold
	 */
	public void setEquivalentDistThreshold(double equivalentDistThreshold) {
		this.equivalentDistThreshold = equivalentDistThreshold;
	}

	/**
	 * @return the bonus associated with taking a jump at a section end point
	 */
	public int getEndPointBonus() {
		return endPointBonus;
	}

	/**
	 * Sets the end point bonus, used to lend extra weight to jumps that occur at an endpoint of one or more sections.
	 * @param endPointBonus
	 */
	public void setEndPointBonus(int endPointBonus) {
		this.endPointBonus = endPointBonus;
	}

	private static final int debug_parent_1 = -1;
	private static final int debug_parent_2 = -1;
//	private static final int debug_parent_1 = 84;
//	private static final int debug_parent_2 = 85;
//	private static final int debug_parent_1 = 170;
//	private static final int debug_parent_2 = 97;
//	private static final int debug_parent_1 = 295;
//	private static final int debug_parent_2 = 170;

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return buildPossibleConnections(from, to,
				from.parentSectionID == debug_parent_1 && to.parentSectionID == debug_parent_2
				|| to.parentSectionID == debug_parent_1 && from.parentSectionID == debug_parent_2);
	}
	
	private class JumpRecord implements Comparable<JumpRecord> {
		private final Jump jump;
		private final int allowedSects;
		private final int endSects;
		private final Double scalar;
		
		private final int sectScore;
		
		public JumpRecord(Jump jump, int allowedSects, int endSects, Double scalar) {
			this.jump = jump;
			this.allowedSects = allowedSects;
			this.endSects = endSects;
			this.scalar = scalar;
			sectScore = allowedSects + endSects*endPointBonus;
		}

		@Override
		public int compareTo(JumpRecord o) {
			int cmp = Integer.compare(o.sectScore, sectScore);
			if (cmp == 0)
				cmp = Double.compare(jump.distance, o.jump.distance);
			return cmp;
		}
		
		@Override
		public String toString() {
			return "sectScore="+sectScore+" ("+allowedSects+" sects, "+endSects+" ends)\tscalar="+scalar+"\t"+jump;
		}
	}

	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to, final boolean debug) {
		List<JumpRecord> candidates = new ArrayList<>();
		int bestScore = -1;
		for (int i=0; i<from.subSects.size(); i++) {
			FaultSection s1 = from.subSects.get(i);
			List<List<? extends FaultSection>> fromStrands = getStrandsTo(from.subSects, i);
			for (int j=0; j<to.subSects.size(); j++) {
				FaultSection s2 = to.subSects.get(j);
				double dist = distCalc.getDistance(s1, s2);
				if ((float)dist <= (float)maxJumpDist) {
					if (debug)
						System.out.println(s1.getSectionId()+" => "+s2.getSectionId()+": "+dist+" km");
					int myBestNumSects = 0;
					Double scalar = null;
					int myEndSects = 0;
					if (i == 0 || i == from.subSects.size()-1)
						myEndSects++;
					if (j == 0 || j == to.subSects.size()-1)
						myEndSects++;
					List<List<? extends FaultSection>> toStrands = getStrandsTo(to.subSects, j);
					for (List<? extends FaultSection> fromStrand : fromStrands) {
						FaultSubsectionCluster fromCluster = new FaultSubsectionCluster(fromStrand);
						if (!fromCluster.subSects.get(fromCluster.subSects.size()-1).equals(s1))
							// reverse the 'from' such that it leads up to the jump point
							fromCluster = fromCluster.reversed();
						for (List<? extends FaultSection> toStrand : toStrands) {
							FaultSubsectionCluster toCluster = new FaultSubsectionCluster(toStrand);
							if (!toCluster.subSects.get(0).equals(s2))
								// reverse the 'to' such that it starts from the jump point
								toCluster = toCluster.reversed();
							ClusterRupture rupture = new ClusterRupture(fromCluster).take(new Jump(s1, fromCluster, s2, toCluster, dist));
							int rupNumSects = rupture.getTotalNumSects();
							int testScore = rupture.getTotalNumSects() + endPointBonus*myEndSects;
							if (rupNumSects < myBestNumSects || testScore < bestScore) {
								if (debug)
									System.out.println("\tSkipping rupture w/ low sect-score="+testScore+": "+rupture);
								continue;
							}
							if (debug)
								System.out.println("\tTrying rupture w/ sect-score="+testScore+": "+rupture);
							PlausibilityResult result = PlausibilityResult.PASS;
							boolean directional = false;
							for (PlausibilityFilter filter : filters) {
								result = result.logicalAnd(filter.apply(rupture, false));
								directional = directional || filter.isDirectional(false);
							}
							if (debug)
								System.out.println("\tResult: "+result);
							Double myScalar = null;
							if (result.isPass() && scalarFilter != null) {
								// get scalar value
								myScalar = scalarFilter.getValue(rupture); 
								if (debug)
									System.out.println("\tScalar val: "+myScalar);
							}
							if (directional && (!result.isPass() || scalarFilter != null)) {
								// try the other direction
								rupture = rupture.reversed();
								if (debug)
									System.out.println("\tTrying reversed: "+rupture);
								PlausibilityResult reverseResult = PlausibilityResult.PASS;
								for (PlausibilityFilter filter : filters)
									reverseResult = result.logicalAnd(filter.apply(rupture, false));
								if (debug)
									System.out.println("\tResult: "+reverseResult);
								if (reverseResult.isPass() && scalarFilter != null) {
									// get scalar value
									Double myScalar2 = scalarFilter.getValue(rupture); 
									if (debug)
										System.out.println("\tScalar val: "+myScalar2);
									if (myScalar == null || (myScalar2 != null &&
											ScalarValuePlausibiltyFilter.isValueBetter(myScalar2, myScalar, scalarRange)))
										myScalar = myScalar2;
								}
								result = result.logicalOr(reverseResult);
							}
							if (result.isPass() && rupNumSects >= myBestNumSects) {
								if (scalarFilter != null) {
									if (scalar == null || rupNumSects > myBestNumSects)
										scalar = myScalar;
									else if (myScalar != null && ScalarValuePlausibiltyFilter.isValueBetter(myScalar, scalar, scalarRange))
										scalar = myScalar;
								}
								myBestNumSects = rupNumSects;
							}
						}
					}
					JumpRecord candidate = new JumpRecord(new Jump(s1, from, s2, to, dist), myBestNumSects, myEndSects, scalar);
					if (candidate.sectScore >= bestScore) {
						// new candidate!
						if (candidate.sectScore > bestScore) {
							candidates.clear();
							bestScore = candidate.sectScore;
						}
						candidates.add(candidate);
						if (debug)
							System.out.println("New candidate w/ "+candidate+" options: "+candidate);
					}
				}
			}
		}
		if (candidates.isEmpty())
			return null;
		Jump jump;
		if (candidates.size() == 1) {
			jump = candidates.get(0).jump;
		} else {
			// pick the best one, first sort
			Collections.sort(candidates);
			JumpRecord preferred = candidates.get(0);
			if (debug)
				System.out.println("Have "+candidates.size()+" options, shortest: "+preferred);
			if (scalarFilter != null) {
				// check scalar values up to dist+threshold
				double maxAllowedDist = preferred.jump.distance + equivalentDistThreshold;
				for (int i=1; i<candidates.size(); i++) {
					JumpRecord test = candidates.get(0);
					if ((float)test.jump.distance > (float)maxAllowedDist || test.allowedSects < preferred.allowedSects)
						break;
					if (test.scalar != null) {
						if (preferred.scalar == null) {
							// keep the one with the scalar
							if (debug)
								System.out.println("Replacing with a scalar value: "+test);
							preferred = test;
						} else if (ScalarValuePlausibiltyFilter.isValueBetter(test.scalar, preferred.scalar, scalarRange)) {
							// keep the one with the better scalar
							if (debug)
								System.out.println("Replacing with a better scalar value: "+test);
							preferred = test;
						}
					}
				}
			}
			jump = preferred.jump;
		}
		if (debug)
			System.out.println("Final candidate: "+jump);
		return Lists.newArrayList(jump);
	}
	
	private List<List<? extends FaultSection>> getStrandsTo(List<? extends FaultSection> subSects, int index) {
		Preconditions.checkState(!subSects.isEmpty());
		List<List<? extends FaultSection>> ret = new ArrayList<>();
		if (subSects.size() == 1) {
			ret.add(subSects.subList(index, index+1));
		} else {
			if (index > 0)
				ret.add(subSects.subList(0, index+1));
			if (index < subSects.size()-1)
				ret.add(subSects.subList(index, subSects.size()));
		}
		return ret;
	}

	@Override
	public String getName() {
		if (filters.size() == 1)
			return filters.get(0)+" Plausibile: maxDist="+(float)maxJumpDist+" km";
		return filters.size()+" Filters Plausible: maxDist="+(float)maxJumpDist+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}
	
	public static void main(String[] args) throws IOException {
		List<? extends FaultSection> subSects = DeformationModels.loadSubSects(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, 2d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/");
		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
		int stiffnessCacheSize = 0;
		if (stiffnessCacheFile.exists())
			stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
		// common aggregators
		AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File distAzCacheFile = new File(rupSetsDir, "fm3_1_dist_az_cache.csv");
		if (distAzCacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+distAzCacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(distAzCacheFile);
		}
		
		double maxJumpDist = 10d;
		
		float cffProb = 0.05f;
		RelativeCoulombProb cffProbCalc = new RelativeCoulombProb(
				sumAgg, new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d), false, true);
		float cffRatio = 0.5f;
		CoulombSectRatioProb sectRatioCalc = new CoulombSectRatioProb(sumAgg, 2);
		NetRuptureCoulombFilter threeQuarters = new NetRuptureCoulombFilter(new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS),
				Range.greaterThan(0f));
		PathPlausibilityFilter combinedPathFilter = new PathPlausibilityFilter(
				new CumulativeProbPathEvaluator(cffRatio, PlausibilityResult.FAIL_HARD_STOP, sectRatioCalc),
				new CumulativeProbPathEvaluator(cffProb, PlausibilityResult.FAIL_HARD_STOP, cffProbCalc));
		
		
//		ClusterConnectionStrategy orig = new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist);
//		ClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist, sumAgg, false);
//		PlausibleClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), threeQuarters);
//		String origName = "No CFF Prob";
		PlausibleClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, threeQuarters);
		String origName = "10km";
//		String origName = "No CFF Prob";
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), threeQuarters);
		
//		PlausibilityFilter filter = new CumulativeProbabilityFilter(0.5f, new CoulombSectRatioProb(sumAgg, 2));
		ClusterConnectionStrategy cff = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, 15d,
				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, threeQuarters);
//		String newName = "With CFF Prob";
		String newName = "15km";
		
		HashSet<Jump> origJumps = new HashSet<>();
		for (FaultSubsectionCluster cluster : orig.getClusters())
			for (Jump jump : cluster.getConnections())
				if (jump.fromSection.getSectionId() < jump.toSection.getSectionId())
					origJumps.add(jump);
		HashSet<Jump> cffJumps = new HashSet<>();
		for (FaultSubsectionCluster cluster : cff.getClusters())
			for (Jump jump : cluster.getConnections())
				if (jump.fromSection.getSectionId() < jump.toSection.getSectionId())
					cffJumps.add(jump);
		HashSet<Jump> origUniqueJumps = new HashSet<>();
		HashSet<Jump> cffUniqueJumps = new HashSet<>();
		HashSet<Jump> commonJumps = new HashSet<>();
		for (Jump jump : origJumps) {
			if (cffJumps.contains(jump))
				commonJumps.add(jump);
			else
				origUniqueJumps.add(jump);
		}
		for (Jump jump : cffJumps) {
			if (!commonJumps.contains(jump))
				cffUniqueJumps.add(jump);
		}
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(subSects, RupSetMapMaker.buildBufferedRegion(subSects));
		mapMaker.plotJumps(commonJumps, Color.GREEN, "Common Jumps");
		mapMaker.plotJumps(origUniqueJumps, Color.BLUE, origName);
		mapMaker.plotJumps(cffUniqueJumps, Color.RED, newName);
		mapMaker.plot(new File("/tmp"), "cff_jumps_compare", "CFF Jump Comparison", 3000);
	}

}
