package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy.CandidateJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy.JumpSelector;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen;
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
import com.google.common.collect.Comparators;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * This connection strategy uses one or more plausibility filters to determine the best jumping point between each pair
 * of fault subsection clusters.
 * 
 * If there are multiple viable connection points (i.e., less than the specified maximum distance and passing all filters),
 * then the passed in JumpSelector will be used to select the best version. The current preffered jump selector chooses
 * the jump that has at least one passing branch direction and the fewest failing branch directions. This encourages end-to-end
 * connections: if a simple branch point (end-to-end) works, it will use that. Ties are then broken according to distance,
 * unless a scalar filter is passed in and the distance difference is < 2km, in which case the scalar value is used.
 * 
 * @author kevin
 *
 */
public class PlausibleClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;
	private JumpSelector selector;
	private List<PlausibilityFilter> filters;
	
	private ScalarValuePlausibiltyFilter<Double> scalarFilter;
	private Range<Double> scalarRange;
	
	public static interface JumpSelector {
		
		public default List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			if (candidates == null || candidates.isEmpty())
				return null;
			if (candidates.size() == 1)
				return candidates;
			Comparator<CandidateJump> comp = comparator(candidates, acceptableScalarRange, verbose);
			Collections.sort(candidates, comp);
			List<CandidateJump> ret = new ArrayList<>();
			CandidateJump best = candidates.get(0);
			if (verbose)
				System.out.println("First candidate: "+best);
			ret.add(best);
			for (int c=1; c<candidates.size(); c++) {
				// see if equivalent
				CandidateJump candidate = candidates.get(c);
				if (comp.compare(best, candidate) == 0) {
					if (verbose)
						System.out.println("\t"+candidate+" is equivalent, adding");
					ret.add(candidate);
				}
			}
			return ret;
		}
		
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose);
	}
	
	public static class OnlyEndsJumpSelector implements JumpSelector {
		
		private boolean requireBoth;

		public OnlyEndsJumpSelector(boolean requireBoth) {
			this.requireBoth = requireBoth;
		}

		@Override
		public List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			List<CandidateJump> ret = new ArrayList<>();
			for (CandidateJump jump : candidates) {
				if (jump.fromEnd || jump.toEnd) {
					if (!requireBoth || jump.fromEnd && jump.toEnd)
						ret.add(jump);
				}
			}
			return ret;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			return null;
		}
		
	}
	
	public static class FallbackJumpSelector implements JumpSelector {
		
		private JumpSelector[] selectors;
		private boolean pickFirst;

		public FallbackJumpSelector(JumpSelector... selectors) {
			this(false, selectors);
		}

		/**
		 * 
		 * @param pickFirst if true, only a single candidate will be selected, otherwise multiple are allowed
		 * @param selectors
		 */
		public FallbackJumpSelector(boolean pickFirst, JumpSelector... selectors) {
			this.pickFirst = pickFirst;
			Preconditions.checkArgument(selectors.length > 0);
			this.selectors = selectors;
		}
		
		@Override
		public List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			if (candidates == null || candidates.isEmpty())
				return null;
			for (JumpSelector selector : selectors) {
				candidates = selector.select(candidates, acceptableScalarRange, verbose);
				if (candidates == null || candidates.isEmpty())
					return null;
				if (candidates.size() == 1)
					return candidates;
			}
			if (pickFirst && candidates.size() > 1) {
				Collections.sort(candidates, reproducible_tie_breaker);
				return candidates.subList(0, 1);
			}
			return candidates;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			List<Comparator<CandidateJump>> comps = new ArrayList<>();
			for (JumpSelector selector : selectors)
				comps.add(selector.comparator(candidates, acceptableScalarRange, verbose));
			return new Comparator<CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					for (Comparator<CandidateJump> comp : comps) {
						int cmp = comp.compare(o1, o2);
						if (cmp != 0)
							return cmp;
					}
					return 0;
				}
			};
		}
		
	}
	
	public static class MinDistanceSelector implements JumpSelector {

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			return distCompare;
		}
		
	}
	
	public static class WithinDistanceSelector implements JumpSelector {
		
		private float maxDist;

		public WithinDistanceSelector(float maxDist) {
			this.maxDist = maxDist;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			return new Comparator<PlausibleClusterConnectionStrategy.CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					boolean within1 = (float)o1.connDistance <= maxDist;
					boolean within2 = (float)o2.connDistance <= maxDist;
					if (within1 != within2) {
						if (within1)
							return -1;
						return 1;
					}
					return 0;
				}
			};
		}
		
	}
	
	/*
	 * This will be used to break ties in a reproducible fashion
	 */
	private static Comparator<CandidateJump> reproducible_tie_breaker = new Comparator<CandidateJump>() {
		
		@Override
		public int compare(CandidateJump o1, CandidateJump o2) {
			int cmp = Integer.compare(o1.fromSection.getSectionId(), o2.fromSection.getSectionId());
			if (cmp == 0)
				cmp = Integer.compare(o1.toSection.getSectionId(), o2.toSection.getSectionId());
			return cmp;
		}
	};
	
	private static double getMinPassingDist(Collection<CandidateJump> candidates) {
		double minDist = Double.POSITIVE_INFINITY;
		
		for (CandidateJump candidate : candidates)
			if (!candidate.allowedJumps.isEmpty() && (float)candidate.connDistance < minDist)
				minDist = candidate.connDistance;
		
		return minDist;
	}
	
	public static class BestScalarSelector implements JumpSelector {
		
		private final double equivDistance;

		public BestScalarSelector(double equivDistance) {
			this.equivDistance = equivDistance;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			float maxScalarDist;
			if (equivDistance > 0d)
				maxScalarDist = (float)(getMinPassingDist(candidates) + equivDistance);
			else
				maxScalarDist = Float.POSITIVE_INFINITY;
			
			if (verbose)
				System.out.println("MaxScalarDist="+maxScalarDist);
			
			return new Comparator<CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					if (verbose)
						System.out.println("Comparing scalars (dist fallback) for "+o1+" and "+o2);
					if ((float)o1.connDistance <= maxScalarDist && (float)o2.connDistance <= maxScalarDist
							|| (float)o1.connDistance == (float)o2.connDistance) {
						// compare scalars if we have any
						if (o1.bestScalar != null || o2.bestScalar != null) {
							if (o2.bestScalar == null || ScalarValuePlausibiltyFilter.isValueBetter(
									o1.bestScalar, o2.bestScalar, acceptableScalarRange)) {
								if (verbose)
									System.out.println("\tfirst ("+o1.bestScalar+") is better than second ("+o2.bestScalar+")");
								return -1;
							} else if (o1.bestScalar == null ||ScalarValuePlausibiltyFilter.isValueBetter(
									o2.bestScalar, o1.bestScalar, acceptableScalarRange)) {
								if (verbose)
									System.out.println("\tsecond ("+o2.bestScalar+") is better than first ("+o1.bestScalar+")");
								return 1;
							}
						}
						// no scalars or equal, defer to distance
					}
					int cmp = distCompare.compare(o1, o2);
					if (verbose)
						System.out.println("\tFellback to distance: "+cmp);
					return cmp;
					
				}
			};
//			if (equivDistance > 0d) {
//				double maxDist = candidates.get(0).distance + equivDistance;
//				List<CandidateJump> withinEquiv = new ArrayList<>();
//				for (int i=0; i<candidates.size(); i++) {
//					CandidateJump candidate = candidates.get(i);
//					if ((float)candidate.distance <= (float)maxDist)
//						withinEquiv.add(candidate);
//					else
//						break;
//				}
//				candidates = withinEquiv;
//			}
//			Double bestValue = null;
//			List<CandidateJump> best = null;
//			for (CandidateJump candidate : candidates) {
//				if (candidate.bestScalar == null || candidate.allowedJumps.isEmpty())
//					continue;
//				if (verbose)
//					System.out.println("Testing "+candidate);
//				if (bestValue == null) {
//					// nothing before, so better
//					bestValue = candidate.bestScalar;
//					best = Lists.newArrayList(candidate);
//					if (verbose)
//						System.out.println("\tkeeping as first");
//				} else if (bestValue.floatValue() == candidate.bestScalar.floatValue()) {
//					// equivalent
//					best.add(candidate);
//					if (verbose)
//						System.out.println("\tadding (tie)");
//				} else if (ScalarValuePlausibiltyFilter.isValueBetter(candidate.bestScalar, bestValue, acceptableScalarRange)) {
//					// better
//					bestValue = candidate.bestScalar;
//					best = Lists.newArrayList(candidate);
//					if (verbose)
//						System.out.println("\treplacing as new best");
//				} else if (verbose) {
//					System.out.println("\t"+candidate.bestScalar+" is worse than "+bestValue);
//					System.out.println("\t\tRange: "+acceptableScalarRange);
//					System.out.println("\t\tcompare: "+candidate.bestScalar.compareTo(bestValue));
//					System.out.println("\t\tcandidate dist: "+ScalarValuePlausibiltyFilter.distFromRange(candidate.bestScalar, acceptableScalarRange));
//					System.out.println("\t\tprev dist: "+ScalarValuePlausibiltyFilter.distFromRange(candidate.bestScalar, acceptableScalarRange));
//				}
//			}
//			if (best == null) {
//				// fallback to shortest
//				if (verbose)
//					System.out.println("No scalars, falling back to minDist");
//				return candidates.subList(0, 1);
//			}
//			return Lists.newArrayList(getMinDist(best));
		}
		
	}
	
	public static class AnyPassSelector implements JumpSelector {
		
		private boolean allowFailuresIfNonePass;

		public AnyPassSelector(boolean allowFailuresIfNonePass) {
			this.allowFailuresIfNonePass = allowFailuresIfNonePass;
		}

		@Override
		public List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			List<CandidateJump> selections = JumpSelector.super.select(candidates, acceptableScalarRange, verbose);
			
			if (!allowFailuresIfNonePass)
				return onlyPassing(selections);
			
			return selections;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			return new Comparator<CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					if (o1.allowedJumps.isEmpty() != o2.allowedJumps.isEmpty()) {
						// one of these passes, the other doesn't
						if (o1.allowedJumps.isEmpty())
							return 1;
						return -1;
					}
					return 0;
				}
			};
		}
		
	}
	
	/**
	 * 
	 * @param selections
	 * @return sublist of the given selections that have at least one pass
	 */
	private static List<CandidateJump> onlyPassing(List<CandidateJump> selections) {
		if (selections == null || selections.isEmpty())
			return selections;
		List<CandidateJump> ret = new ArrayList<>();
		for (CandidateJump jump : selections)
			if (!jump.allowedJumps.isEmpty())
				ret.add(jump);
		return ret;
	}
	
	public static class PassesMinimizeFailedSelector implements JumpSelector {
		
		private boolean allowFailuresIfNonePass;

		public PassesMinimizeFailedSelector(boolean allowFailuresIfNonePass) {
			this.allowFailuresIfNonePass = allowFailuresIfNonePass;
		}

		@Override
		public List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			List<CandidateJump> selections = JumpSelector.super.select(candidates, acceptableScalarRange, verbose);
			
			if (!allowFailuresIfNonePass)
				return onlyPassing(selections);
			
			return selections;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange, boolean verbose) {
			return new Comparator<CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					if (verbose)
						System.out.println("Comparing "+o1+" to "+o2);
					if (o1.allowedJumps.isEmpty() != o2.allowedJumps.isEmpty()) {
						// one of these passes, the other doesn't
						if (o1.allowedJumps.isEmpty()) {
							if (verbose)
								System.out.println("\tsecond passes first doesn't, returning 1");
							return 1;
						}
						if (verbose)
							System.out.println("\tfirst passes second doesn't, returning 1");
						return -1;
					}
					// if we're here, both pass or both don't pass
					if (o1.allowedJumps.isEmpty() && o2.allowedJumps.isEmpty()) {
						// both fail, fallback to minDist
						int cmp = distCompare.compare(o1, o2);
						if (verbose)
							System.out.println("\tneither pass, going with closest: "+cmp);
						return cmp;
					}
					// 0 if same number of failures, -1 if o1 has fewer, +1 if o1 has more failures
					int cmp = Integer.compare(o1.failedJumps.size(), o2.failedJumps.size());
					if (verbose)
						System.out.println("\tfailCMP="+cmp);
					if (cmp == 0) {
						// same number of failures, new it's better if fewer jumps in total (e.g., at end(s))
						// 0 if same number of total jumps, +1 if prev had more jumps, -1 if prev had fewer jumps
						cmp = Integer.compare(o1.totalJumps, o2.totalJumps);
						if (verbose)
							System.out.println("\tfellback to totalJumps: jumpCMP="+cmp+"\to1: "+o1.totalJumps+"\to2="+o2.totalJumps);
					}
					return cmp;
				}
			};
		}
		
	}
	
	public static class AllowMultiEndsSelector implements JumpSelector {
		
		private JumpSelector fallback;
		private float maxAdditionalJumpDist;

		public AllowMultiEndsSelector(float maxAdditionalJumpDist, JumpSelector fallback) {
			this.maxAdditionalJumpDist = maxAdditionalJumpDist;
			this.fallback = fallback;
		}

		@Override
		public List<CandidateJump> select(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			if (candidates == null || candidates.isEmpty())
				return null;
			if (candidates.size() == 1)
				return candidates;
			if (fallback != null)
				Collections.sort(candidates, fallback.comparator(candidates, acceptableScalarRange, verbose));
			CandidateJump first = candidates.get(0);
			int firstEnds = first.numEnds();
			if (!first.allowedJumps.isEmpty() && firstEnds > 0) {
				// we have an end. allow everyone else with ends
				List<CandidateJump> ret = new ArrayList<>();
				ret.add(first);
				HashSet<FaultSection> prevJumpPoints = new HashSet<>();
				prevJumpPoints.add(first.fromSection);
				prevJumpPoints.add(first.toSection);
				for (CandidateJump candidate : candidates) {
					// we get to keep this as well if...
					// it's within the (potentially stricter) distance threshold
					if ((float)candidate.connDistance <= maxAdditionalJumpDist
							// it also passes, and involves the same number of ends
							&& !candidate.allowedJumps.isEmpty() && candidate.numEnds() >= firstEnds
							// it doesn't involve a previously allowed jump point
							&& !prevJumpPoints.contains(candidate.fromSection)
							&& !prevJumpPoints.contains(candidate.toSection)) {
						// allow this as well if it has the same number of ends, but involves different from and end sections
						ret.add(candidate);
						prevJumpPoints.add(candidate.fromSection);
						prevJumpPoints.add(candidate.toSection);
					}
				}
				return ret;
			}
			// revert to fallback
			if (fallback != null)
				candidates = fallback.select(candidates, acceptableScalarRange, verbose);
			return candidates;
		}

		@Override
		public Comparator<CandidateJump> comparator(List<CandidateJump> candidates, Range<Double> acceptableScalarRange,
				boolean verbose) {
			return new Comparator<CandidateJump>() {

				@Override
				public int compare(CandidateJump o1, CandidateJump o2) {
					return -Integer.compare(o1.numEnds(), o2.numEnds());
				}
			};
		}
		
	}
	
	private static final boolean allow_failures_if_none_pass = false;
	
//	public static final JumpSelector JUMP_SELECTOR_DEFAULT = new PassesMinimizeFailedSelector(new BestScalarSelector(2d));
//	public static final JumpSelector JUMP_SELECTOR_DEFAULT = new FallbackJumpSelector(true, new PassesMinimizeFailedSelector(), new BestScalarSelector(2d))
	public static final JumpSelector JUMP_SELECTOR_DEFAULT_MULTI = new FallbackJumpSelector(false,
			new PassesMinimizeFailedSelector(allow_failures_if_none_pass), new AllowMultiEndsSelector(5f, new FallbackJumpSelector(true, new BestScalarSelector(2d))));
	public static final JumpSelector JUMP_SELECTOR_DEFAULT_SINGLE = new FallbackJumpSelector(false,
			new PassesMinimizeFailedSelector(allow_failures_if_none_pass), new BestScalarSelector(2d));
	public static final JumpSelector JUMP_SELECTOR_DEFAULT = JUMP_SELECTOR_DEFAULT_MULTI;

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, PlausibilityFilter... filters) {
		this(subSects, distCalc, maxJumpDist, JUMP_SELECTOR_DEFAULT, filters);
	}

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, JumpSelector selector,
			PlausibilityFilter... filters) {
		this(subSects, distCalc, maxJumpDist, selector, Lists.newArrayList(filters));
	}

	public PlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, JumpSelector selector,
			List<PlausibilityFilter> filters) {
		super(subSects, distCalc);
		Preconditions.checkState(!filters.isEmpty());
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
		this.filters = filters;
		this.selector = selector;
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

//	/**
//	 * @return the threshold between which two distances are thought to be equivalent. If candidate jumps are otherwise
//	 * equivalent and have scalar values associated, the jump with the better scalar value will be taken
//	 */
//	public double getEquivalentDistThreshold() {
//		return equivalentDistThreshold;
//	}
//
//	/**
//	 * Sets the threshold between which two distances are thought to be equivalent. If candidate jumps are otherwise
//	 * equivalent and have scalar values associated, the jump with the better scalar value will be taken
//	 * @param equivalentDistThreshold
//	 */
//	public void setEquivalentDistThreshold(double equivalentDistThreshold) {
//		this.equivalentDistThreshold = equivalentDistThreshold;
//	}
//
//	/**
//	 * @return the bonus associated with taking a jump at a section end point
//	 */
//	public int getEndPointBonus() {
//		return endPointBonus;
//	}
//
//	/**
//	 * Sets the end point bonus, used to lend extra weight to jumps that occur at an endpoint of one or more sections.
//	 * @param endPointBonus
//	 */
//	public void setEndPointBonus(int endPointBonus) {
//		this.endPointBonus = endPointBonus;
//	}

	private static final int debug_parent_1 = -1;
	private static final int debug_parent_2 = -1;
//	private static final int debug_parent_1 = 1001;
//	private static final int debug_parent_2 = 1002;
//	private static final int debug_parent_1 = 84;
//	private static final int debug_parent_2 = 85;
//	private static final int debug_parent_1 = 170;
//	private static final int debug_parent_2 = 97;
//	private static final int debug_parent_1 = 295;
//	private static final int debug_parent_2 = 170;
//	private static final int debug_parent_1 = 170;
//	private static final int debug_parent_2 = 96;
//	private static final int debug_parent_1 = 254;
//	private static final int debug_parent_2 = 209;
//	private static final int debug_parent_1 = 82;
//	private static final int debug_parent_2 = 84;
//	private static final int debug_parent_1 = 724; // camp rock
//	private static final int debug_parent_2 = 90; // emerson-copper mtn
//	private static final int debug_parent_1 = 894; // davis creek
//	private static final int debug_parent_2 = 708; // surprise valley
//	private static final int debug_parent_1 = 884; // cleghorn lake
//	private static final int debug_parent_2 = 883; // cleghorn pass
//	private static final int debug_parent_1 = 174; // Thirty Mile Bank
//	private static final int debug_parent_2 = 776; // Coronado Bank
//	private static final int debug_parent_1 = 294; // SAF NBMC
//	private static final int debug_parent_2 = 283; // SAV SB S
//	private static final int debug_parent_1 = 37; // Hat Creek
//	private static final int debug_parent_2 = 689; // Rocky Ledge
//	private static final int debug_parent_1 = 667;
//	private static final int debug_parent_2 = 696;

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return buildPossibleConnections(from, to,
				from.parentSectionID == debug_parent_1 && to.parentSectionID == debug_parent_2
				|| to.parentSectionID == debug_parent_1 && from.parentSectionID == debug_parent_2);
	}
	
	public static class CandidateJump {
		public final FaultSubsectionCluster fromCluster;
		public final FaultSection fromSection;
		public final boolean fromEnd;
		public final FaultSubsectionCluster toCluster;
		public final FaultSection toSection;
		public final boolean toEnd;
		public final double connDistance;
		public final double minDistance;
		
		public final List<Jump> allowedJumps;
		public final List<Jump> failedJumps;
		public final Map<Jump, Double> jumpScalars;
		public final Double bestScalar;
		public final int totalJumps;
		
		public CandidateJump(FaultSubsectionCluster fromCluster, FaultSection fromSection, boolean fromEnd,
				FaultSubsectionCluster toCluster, FaultSection toSection, boolean toEnd, double connDistance, double minDistance,
				List<Jump> allowedJumps, List<Jump> failedJumps, Map<Jump, Double> jumpScalars, Double bestScalar) {
			super();
			this.fromCluster = fromCluster;
			this.fromSection = fromSection;
			this.fromEnd = fromEnd;
			this.toCluster = toCluster;
			this.toSection = toSection;
			this.toEnd = toEnd;
			this.connDistance = connDistance;
			this.minDistance = minDistance;
			this.allowedJumps = allowedJumps;
			this.failedJumps = failedJumps;
			this.jumpScalars = jumpScalars;
			this.bestScalar = bestScalar;
			this.totalJumps = allowedJumps.size() + failedJumps.size();
		}
		
		@Override
		public String toString() {
			String ret = fromSection.getSectionId()+"";
			if (fromEnd)
				ret += "[end]";
			ret += "->"+toSection.getSectionId();
			if (toEnd)
				ret += "[end]";
			ret += ": dist="+(float)connDistance+"\t"+allowedJumps.size()+"/"+(allowedJumps.size()+failedJumps.size())+" pass";
			if (bestScalar != null)
				ret += "\tbestScalar: "+bestScalar.floatValue();
			return ret;
		}
		
		public int numEnds() {
			int ret = 0;
			if (fromEnd)
				ret++;
			if (toEnd)
				ret++;
			return ret;
		}
	}

	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to, final boolean debug) {
		List<CandidateJump> candidates = new ArrayList<>();
		double minDist = Double.POSITIVE_INFINITY;
		for (int i=0; i<from.subSects.size(); i++) {
			FaultSection s1 = from.subSects.get(i);
			List<List<? extends FaultSection>> fromStrands = getStrandsTo(from.subSects, i);
			boolean fromEnd = i == 0 || i == from.subSects.size()-1;
			for (int j=0; j<to.subSects.size(); j++) {
				FaultSection s2 = to.subSects.get(j);
				double dist = distCalc.getDistance(s1, s2);
				minDist = Double.min(minDist, dist);
				boolean toEnd = j == 0 || j == to.subSects.size()-1;
				if ((float)dist <= (float)maxJumpDist) {
					if (debug)
						System.out.println(s1.getSectionId()+" => "+s2.getSectionId()+": "+dist+" km");
					List<Jump> allowedJumps = new ArrayList<>();
					List<Jump> failedJumps = new ArrayList<>();
					Map<Jump, Double> jumpScalars = scalarFilter == null ? null : new HashMap<>();
					Double bestScalar = null;
					List<List<? extends FaultSection>> toStrands = getStrandsTo(to.subSects, j);
					double minPassingDist = Double.POSITIVE_INFINITY;
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
							Jump testJump = new Jump(s1, fromCluster, s2, toCluster, dist);
							ClusterRupture rupture = new ClusterRupture(fromCluster).take(testJump);
							if (debug)
								System.out.println("\tTrying rupture: "+rupture);
							PlausibilityResult result = PlausibilityResult.PASS;
							boolean directional = false;
							for (PlausibilityFilter filter : filters) {
								result = result.logicalAnd(filter.apply(rupture, false));
								directional = directional || filter.isDirectional(false);
							}
							if (debug)
								System.out.println("\tResult: "+result);
							Double myScalar = null;
							if (scalarFilter != null && result.isPass()) {
								// get scalar value
								myScalar = scalarFilter.getValue(rupture); 
								if (debug)
									System.out.println("\tScalar val: "+myScalar);
							}
							if (directional && (scalarFilter != null || !result.isPass())) {
								// try the other direction
								rupture = rupture.reversed();
								if (debug)
									System.out.println("\tTrying reversed: "+rupture);
								PlausibilityResult reverseResult = PlausibilityResult.PASS;
								for (PlausibilityFilter filter : filters)
									reverseResult = reverseResult.logicalAnd(filter.apply(rupture, false));
								if (debug)
									System.out.println("\tResult: "+reverseResult);
								if (scalarFilter != null && reverseResult.isPass()) {
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
							if (myScalar != null) {
								jumpScalars.put(testJump, myScalar);
								if (bestScalar == null || ScalarValuePlausibiltyFilter.isValueBetter(myScalar, bestScalar, scalarRange))
									bestScalar = myScalar;
							}
							
							if (result.isPass()) {
								allowedJumps.add(testJump);
								// figure out the minimum distance between any sections that pass
								for (FaultSection ss1 : testJump.fromCluster.subSects)
									for (FaultSection ss2 : testJump.toCluster.subSects)
										minPassingDist = Double.min(minPassingDist, distCalc.getDistance(ss1, ss2));
							} else {
								failedJumps.add(testJump);
							}
						}
					}
					CandidateJump candidate = new CandidateJump(from, s1, fromEnd, to, s2, toEnd, dist, minPassingDist,
							allowedJumps, failedJumps, jumpScalars, bestScalar);
					if (debug)
						System.out.println("New candidate: "+candidate);
					candidates.add(candidate);
				}
			}
		}
		if (candidates.isEmpty()) {
//			if ((float)minDist <= (float)maxJumpDist)
//				System.err.println("WARNING: no connection returned between "+from.parentSectionID+" and "
//						+to.parentSectionID+", despite a distance of "+(float)minDist);
			return null;
		}
		if (debug) {
			System.out.println("All candidates (distance sorted)");
			Collections.sort(candidates, distCompare);
			for (CandidateJump candidate : candidates)
				System.out.println("\t"+candidate);
		}
		List<CandidateJump> selected = selector.select(candidates, scalarRange, debug);
		if (debug && selected != null)
			System.out.println("Selected candidate(s):\n\t"+selected.stream().map(S -> S.toString()).collect(Collectors.joining("\n\t")));
		if (selected == null)
			return null;
		
		List<Jump> ret = new ArrayList<>();
		for (CandidateJump candidate : selected)
			// use the minimum distance, not the distance at the chosen connection point
			ret.add(new Jump(candidate.fromSection, from, candidate.toSection, to, candidate.minDistance));
//		System.out.println("returning "+ret.size()+" jumps");
//		if (ret.isEmpty() && (float)minDist <= (float)maxJumpDist)
//			System.err.println("WARNING: no connection returned between "+from.parentSectionID+" and "
//					+to.parentSectionID+", despite a distance of "+(float)minDist);
		return ret;
	}
	
	private static final Comparator<CandidateJump> distCompare = new Comparator<CandidateJump>() {

		@Override
		public int compare(CandidateJump o1, CandidateJump o2) {
			return Float.compare((float)o1.connDistance, (float)o2.connDistance);
		}
	};
	
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
		String summary = "maxDist="+new DecimalFormat("0.#").format(maxJumpDist)+" km";
		if (selector instanceof FallbackJumpSelector) {
			for (JumpSelector sub : ((FallbackJumpSelector)selector).selectors)
				if (sub instanceof AllowMultiEndsSelector)
					summary += ", MultiEnds";
		}
		if (filters.size() == 1)
			return filters.get(0)+" Plausibile: "+summary;
		return "Plausible ("+filters.size()+" filters): "+summary;
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
		AggregatedStiffnessCalculator fractIntsAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File distAzCacheFile = new File(rupSetsDir, "fm3_1_dist_az_cache.csv");
		if (distAzCacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+distAzCacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(distAzCacheFile);
		}
		
		double maxJumpDist = 15d;
		boolean favJump = true;
		
		float cffProb = 0.02f;
		RelativeCoulombProb cffProbCalc = new RelativeCoulombProb(
				sumAgg, new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d), false, true, favJump, (float)maxJumpDist, distAzCalc);
		float cffRatio = 0.5f;
		CoulombSectRatioProb sectRatioCalc = new CoulombSectRatioProb(sumAgg, 2, favJump, (float)maxJumpDist, distAzCalc);
		NetRuptureCoulombFilter fractInts = new NetRuptureCoulombFilter(fractIntsAgg, Range.greaterThan(0.75f));
		PathPlausibilityFilter combinedPathFilter = new PathPlausibilityFilter(
				new CumulativeProbPathEvaluator(cffRatio, PlausibilityResult.FAIL_HARD_STOP, sectRatioCalc),
				new CumulativeProbPathEvaluator(cffProb, PlausibilityResult.FAIL_HARD_STOP, cffProbCalc));
		
		
//		ClusterConnectionStrategy orig = new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist);
//		String origName = "Min Distance";
//		ClusterConnectionStrategy orig;
//		try {
//			orig = FaultSystemIO.loadRupSet(new File(rupSetsDir,
////					"fm3_1_plausible10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip"))
//					"fm3_1_plausibleMulti10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip"))
//					.getPlausibilityConfiguration().getConnectionStrategy();
//		} catch (Exception e) {
//			throw ExceptionUtils.asRuntimeException(e);
//		}
//		String origName = "Previous Version";
//		ClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist, sumAgg, false);
//		PlausibleClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), threeQuarters);
		PlausibleClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
				JUMP_SELECTOR_DEFAULT,
				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, fractInts);
		String origName = "15km";
//		String origName = "No CFF Prob";
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), threeQuarters);
//		ClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
//				new PassesMinimizeFailedSelector(),
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, threeQuarters);
//		String origName = "Min Dist Fallback";
//		ClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
//				new PassesMinimizeFailedSelector(new BestScalarSelector(2d)),
//				new CumulativeProbabilityFilter(cffRatio, new CoulombSectRatioProb(sumAgg, 2)), new PathPlausibilityFilter(
//						new CumulativeProbPathEvaluator(cffRatio, PlausibilityResult.FAIL_HARD_STOP, new CoulombSectRatioProb(sumAgg, 2)),
//						new CumulativeProbPathEvaluator(cffProb, PlausibilityResult.FAIL_HARD_STOP, new RelativeCoulombProb(
//								sumAgg, new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d), false, true))), threeQuarters);
//		String origName = "No Fav Nump";
//		JumpSelector singleSelect = new FallbackJumpSelector(true,
//				new PassesMinimizeFailedSelector(allow_failures_if_none_pass), new BestScalarSelector(2d));
//		ClusterConnectionStrategy orig = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
//				singleSelect,
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, fractInts);
//		String origName = "Single Connection";
		
////		PlausibilityFilter filter = new CumulativeProbabilityFilter(0.5f, new CoulombSectRatioProb(sumAgg, 2));
//		ClusterConnectionStrategy newStrat = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, 15f,
//				JUMP_SELECTOR_DEFAULT,
//				new CumulativeProbabilityFilter(cffRatio, sectRatioCalc), combinedPathFilter, fractInts);
////		String newName = "New Plausible";
//		String newName = "15km";
		
//		PlausibilityFilter filter = new CumulativeProbabilityFilter(0.5f, new CoulombSectRatioProb(sumAgg, 2));
		ClusterConnectionStrategy newStrat = new AdaptiveClusterConnectionStrategy(orig, 6d, 1);
		String newName = "Adaptive 6-15km";
		
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
		orig.checkBuildThreaded(threads);
		newStrat.checkBuildThreaded(threads);
		
		HashSet<Jump> origJumps = new HashSet<>();
		for (FaultSubsectionCluster cluster : orig.getClusters())
			for (Jump jump : cluster.getConnections())
				if (jump.fromSection.getSectionId() < jump.toSection.getSectionId())
					origJumps.add(jump);
		HashSet<Jump> cffJumps = new HashSet<>();
		for (FaultSubsectionCluster cluster : newStrat.getClusters())
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
		
		System.out.println(commonJumps.size()+" common");
		System.out.println(origUniqueJumps.size()+" unique to "+origName);
		System.out.println(cffUniqueJumps.size()+" unique to "+newName);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(subSects, RupSetMapMaker.buildBufferedRegion(subSects));
		mapMaker.plotJumps(commonJumps, RupSetDiagnosticsPageGen.darkerTrans(Color.GREEN), "Common Jumps");
		mapMaker.plotJumps(origUniqueJumps, RupSetDiagnosticsPageGen.darkerTrans(Color.BLUE), origName);
		mapMaker.plotJumps(cffUniqueJumps, RupSetDiagnosticsPageGen.darkerTrans(Color.RED), newName);
		mapMaker.plot(new File("/tmp"), "cff_jumps_compare", "CFF Jump Comparison", 5000);
	}
	
	

}
