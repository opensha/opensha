package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.util.Collection;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathAddition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator.CoulombFavorableSectionPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

/**
 * Probability calculator that compares the Coulomb favorability of each jump taken to all other possible paths not taken.
 * 
 * @author kevin
 *
 */
public class RelativeCoulombProb extends AbstractRelativeProb {
	
	private AggregatedStiffnessCalculator aggCalc;
	private boolean sectBySect;
	private boolean jumpToMostFavorable;
	private float maxJumpDist;
	private transient SectionDistanceAzimuthCalculator distAzCalc;

	public RelativeCoulombProb(AggregatedStiffnessCalculator aggCalc, ClusterConnectionStrategy connStrat,
			boolean allowNegative, boolean sectBySect) {
		this(aggCalc, connStrat, allowNegative, sectBySect, false, 0f, null);
	}

	public RelativeCoulombProb(AggregatedStiffnessCalculator aggCalc, ClusterConnectionStrategy connStrat,
			boolean allowNegative, boolean sectBySect, boolean jumpToMostFavorable,
			float maxJumpDist, SectionDistanceAzimuthCalculator distAzCalc) {
		super(connStrat, allowNegative, true); // always relative to best
		this.aggCalc = aggCalc;
		this.sectBySect = sectBySect;
		this.jumpToMostFavorable = jumpToMostFavorable;
		if (jumpToMostFavorable) {
			Preconditions.checkState(sectBySect);
			Preconditions.checkState(maxJumpDist > 0f);
			this.maxJumpDist = maxJumpDist;
			Preconditions.checkNotNull(distAzCalc);
			this.distAzCalc = distAzCalc;
		}
	}
	
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

	@Override
	public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		super.init(connStrat, distAzCalc);
		this.distAzCalc = distAzCalc;
	}

	@Override
	public String getName() {
		String name = "Rel CFF";
		if (sectBySect) {
			name += " Sect";
			if (jumpToMostFavorable)
				name += ", Fav"+CumulativeProbabilityFilter.optionalDigitDF.format(maxJumpDist)+"km";
		} else {
			name += " Cluster";
		}
		if (allowNegative)
			name += ", Allow Neg";
		if (!relativeToBest)
			name += ", Rel Total";
		return name;
	}
	
	public boolean isDirectional(boolean splayed) {
		return true;
	}

	@Override
	public double calcAdditionValue(ClusterRupture fullRupture, Collection<? extends FaultSection> currentSects,
			PathAddition addition) {
		return aggCalc.calc(currentSects, addition.toSects);
	}

	@Override
	public boolean isAddFullClusters() {
		return !sectBySect;
	}

	@Override
	public PathNavigator getPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster) {
		if (jumpToMostFavorable)
			return new CoulombFavorableSectionPathNavigator(nucleationCluster.subSects, rupture.getTreeNavigator(),
					aggCalc, Range.atLeast(0f), distAzCalc, maxJumpDist);
		return super.getPathNav(rupture, nucleationCluster);
	}

	@Override
	public PathAddition targetJumpToAddition(Collection<? extends FaultSection> curSects,
			PathAddition testAddition, Jump alternateJump) {
		if (jumpToMostFavorable) {
			Preconditions.checkState(sectBySect);
			FaultSection destSect = SectCoulombPathEvaluator.findMostFavorableJumpSect(curSects, alternateJump,
					maxJumpDist, Range.atLeast(0f), aggCalc, distAzCalc, false);
			return new PathAddition(testAddition.fromSect, testAddition.fromCluster, destSect, alternateJump.toCluster);
		}
		return super.targetJumpToAddition(curSects, testAddition, alternateJump);
	}
	
}