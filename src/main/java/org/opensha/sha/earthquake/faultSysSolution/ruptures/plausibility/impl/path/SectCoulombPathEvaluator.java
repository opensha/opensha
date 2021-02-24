package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * A section-by-section Coulomb path evaluator. It can optionally choose the most favorable path up to a given distance,
 * rather than taking the prescribed jump.
 * 
 * @author kevin
 *
 */
public class SectCoulombPathEvaluator extends ScalarCoulombPathEvaluator {
	
	public static FaultSection findMostFavorableJumpSect(Collection<? extends FaultSection> sources, Jump jump, float maxSearchDist,
			Range<Float> acceptableRange, AggregatedStiffnessCalculator aggCalc, SectionDistanceAzimuthCalculator distAzCalc,
			boolean verbose) {
		if (verbose)
			System.out.println("Finding most favorable jump to "+jump.toCluster+", origJump="+jump);
		List<FaultSection> allowedJumps = new ArrayList<>();
		for (FaultSection sect : jump.toCluster.subSects) {
			for (FaultSection source : jump.fromCluster.subSects) {
				if (sect == jump.toSection || (float)distAzCalc.getDistance(sect, source) <= maxSearchDist) {
					allowedJumps.add(sect);
					break;
				}
			}
		}
		Preconditions.checkState(!allowedJumps.isEmpty(), "No jumps within %s km found between %s and %s",
				maxSearchDist, jump.fromCluster, jump.toCluster);
		if (allowedJumps.size() == 1) {
			if (verbose)
				System.out.println("Only 1 possible jump: "+allowedJumps.get(0));
			return allowedJumps.get(0);
		}
		// find the most favorable one
		float bestVal = Float.NaN;
		FaultSection bestSect = null;
		for (FaultSection sect : allowedJumps) {
			float myVal = (float)aggCalc.calc(sources, sect);
			if (verbose)
				System.out.println("CFF to "+sect.getSectionId()+": "+myVal);
			if (Double.isNaN(bestVal) || ScalarValuePlausibiltyFilter.isValueBetter(myVal, bestVal, acceptableRange)) {
				bestVal = myVal;
				bestSect = sect;
			}
		}
		Preconditions.checkNotNull(bestSect);
		return bestSect;
	}
	
	public static class CoulombFavorableSectionPathNavigator extends SectionPathNavigator {

		private final AggregatedStiffnessCalculator aggCalc;
		private final SectionDistanceAzimuthCalculator distAzCalc;
		private final float maxSearchDist;
		private Range<Float> acceptableRange;

		public CoulombFavorableSectionPathNavigator(Collection<FaultSection> startSects, RuptureTreeNavigator nav,
				AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
				SectionDistanceAzimuthCalculator distAzCalc, float maxSearchDist) {
			super(startSects, nav);
			this.aggCalc = aggCalc;
			this.acceptableRange = acceptableRange;
			this.distAzCalc = distAzCalc;
			this.maxSearchDist = maxSearchDist;
		}

		@Override
		protected List<FaultSection> getNeighbors(FaultSection fromSect) {
			List<FaultSection> neighbors = new ArrayList<>();
			for (FaultSection neighbor : super.getNeighbors(fromSect)) {
				if (currentSects.contains(neighbor))
					continue;
				if (neighbor.getParentSectionId() == fromSect.getParentSectionId()) {
					// not a jump
					if (verbose)
						System.out.println("\tneighbor of "+fromSect.getSectionId()+" is on same parent: "+neighbor.getSectionId());
					neighbors.add(neighbor);
				} else {
					// it's a jump, find most favorable
					Jump jump = rupNav.getJump(fromSect, neighbor);
					neighbors.add(findMostFavorableJumpSect(currentSects, jump, maxSearchDist, acceptableRange,
							aggCalc, distAzCalc, verbose));
				}
			}
			return neighbors;
		}
		
	}

	private boolean jumpToMostFavorable;
	private float maxJumpDist;
	private transient SectionDistanceAzimuthCalculator distAzCalc;
	
	public SectCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			PlausibilityResult failureType) {
		this(aggCalc, acceptableRange, failureType, false, 0f, null);
	}

	public SectCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
			PlausibilityResult failureType, boolean jumpToMostFavorable, float maxJumpDist,
			SectionDistanceAzimuthCalculator distAzCalc) {
		super(aggCalc, acceptableRange, failureType);
		this.jumpToMostFavorable = jumpToMostFavorable;
		if (jumpToMostFavorable) {
			Preconditions.checkState(maxJumpDist > 0d);
			Preconditions.checkNotNull(distAzCalc);
			this.maxJumpDist = maxJumpDist;
			this.distAzCalc = distAzCalc;
		} else {
			this.maxJumpDist = 0f;
			this.distAzCalc = null;
		}
	}
	
	public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		this.distAzCalc = distAzCalc;
	}
	
	@Override
	protected SectionPathNavigator getPathNav(ClusterRupture rupture,
			FaultSubsectionCluster nucleationCluster) {
		if (jumpToMostFavorable)
			return new CoulombFavorableSectionPathNavigator(nucleationCluster.subSects,
					rupture.getTreeNavigator(), aggCalc, acceptableRange, distAzCalc, maxJumpDist);
		return new SectionPathNavigator(nucleationCluster.subSects, rupture.getTreeNavigator());
	}

	@Override
	public Float getAdditionValue(Collection<FaultSection> curSects, PathAddition addition, boolean verbose) {
		Preconditions.checkState(addition.toSects.size() == 1);
		FaultSection destSect = addition.toSects.iterator().next();
		double val = aggCalc.calc(curSects, destSect);
		if (verbose)
			System.out.println("\t"+curSects.size()+" sources to "+destSect.getSectionId()+": "+val);
		return (float)val;
	}

	@Override
	public String getShortName() {
		String sectStr = jumpToMostFavorable ? "SectFav"+new DecimalFormat("0.#").format(maxJumpDist) : "Sect";
		return sectStr+"["+aggCalc.getScalarShortName()+"]"+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
	}

	@Override
	public String getName() {
		String sectStr = jumpToMostFavorable ? "Sect Favorable ("+new DecimalFormat("0.#").format(maxJumpDist)+"km)" : "Sect";
		return sectStr+" ["+aggCalc.getScalarName()+"] "+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
	}
	
}