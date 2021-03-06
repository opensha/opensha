package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.util.List;
import java.util.Set;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathAddition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.SectionPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator.CoulombFavorableSectionPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

/**
 * Coulomb probability calculator that computes a ratio as each section is added onto a rupture. That probability is the
 * ratio of the total CFF value on the receiving subsection (with the rupture so far as source) divided by the absolute
 * value of the top N subsections, bounded in the range [0,1]. This penalizes additions dominated by a few large interactions
 * but where it is negatively aligned with the rest of the rupture. 
 * 
 * @author kevin
 *
 */
public class CoulombSectRatioProb implements RuptureProbabilityCalc {
	
	private AggregatedStiffnessCalculator aggCalc;
	private int numDenominatorSubsects;
	private boolean jumpToMostFavorable;
	private float maxJumpDist;
	private transient SectionDistanceAzimuthCalculator distAzCalc;

	public CoulombSectRatioProb(AggregatedStiffnessCalculator aggCalc, int numDenominatorSubsects) {
		this(aggCalc, numDenominatorSubsects, false, 0f, null);
	}

	public CoulombSectRatioProb(AggregatedStiffnessCalculator aggCalc, int numDenominatorSubsects,
			boolean jumpToMostFavorable, float maxJumpDist, SectionDistanceAzimuthCalculator distAzCalc) {
		this.aggCalc = aggCalc;
		Preconditions.checkState(numDenominatorSubsects >= 1);
		this.numDenominatorSubsects = numDenominatorSubsects;
		this.jumpToMostFavorable = jumpToMostFavorable;
		this.maxJumpDist = maxJumpDist;
		this.distAzCalc = distAzCalc;
	}
	
	public AggregatedStiffnessCalculator getAggregator() {
		return aggCalc;
	}

	@Override
	public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		this.distAzCalc = distAzCalc;
	}

	@Override
	public String getName() {
		String str = "CFF Sect Ratio, N="+numDenominatorSubsects;
		if (jumpToMostFavorable)
			str += ", Fav"+CumulativeProbabilityFilter.optionalDigitDF.format(maxJumpDist)+"km";
		return str;
	}

	@Override
	public double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
		SectionPathNavigator nav;
		if (jumpToMostFavorable)
			nav = new CoulombFavorableSectionPathNavigator(rupture.clusters[0].subSects, rupture.getTreeNavigator(),
					aggCalc, Range.atLeast(0f), distAzCalc, maxJumpDist);
		else
			nav = new SectionPathNavigator(rupture.clusters[0].subSects, rupture.getTreeNavigator());
		nav.setVerbose(verbose);
		
		double prob = 1d;
		List<FaultSection> currentSects = nav.getCurrentSects();
		Set<PathAddition> nextAdds = nav.getNextAdditions();
		if (verbose)
			System.out.println("Have "+nextAdds.size()+" nextAdds");
		while (!nextAdds.isEmpty()) {
			for (PathAddition add : nextAdds) {
				Preconditions.checkState(add.toSects.size() == 1);
				FaultSection receiver = add.toSects.iterator().next();
				HighestNTracker track = new HighestNTracker(numDenominatorSubsects);
				for (FaultSection source : currentSects)
					track.addValue(aggCalc.calc(source, receiver));
				double myProb = track.sum/Math.abs(track.getSumHighest());
				if (myProb < 0)
					myProb = 0;
				else if (myProb > 1)
					myProb = 1;
				if (verbose)
					System.out.println("Probability of adding "+receiver.getSectionId()+" with "
							+currentSects.size()+" sources: "+track.sum+"/|"+track.getSumHighest()+"| = "+myProb);
				prob *= myProb;
			}
			
			currentSects = nav.getCurrentSects();
			nextAdds = nav.getNextAdditions();
			if (verbose)
				System.out.println("Have "+nextAdds.size()+" nextAdds");
		}
		Preconditions.checkState(currentSects.size() == rupture.getTotalNumSects());
		return prob;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return true;
	}

	public int getNumDenominatorSubsects() {
		return numDenominatorSubsects;
	}

	public boolean isJumpToMostFavorable() {
		return jumpToMostFavorable;
	}

	public float getMaxJumpDist() {
		return maxJumpDist;
	}
	
	/**
	 * This class keeps track of the N highest values of a series, without keeping/sorting the whole list in memory
	 * 
	 * @author kevin
	 *
	 */
	public static class HighestNTracker {
		private int numProcessed = 0;
		double sum = 0d;
		private double smallestHigh = Double.POSITIVE_INFINITY;
		private int smallestIndex = -1;
		private double[] highVals;
		
		public HighestNTracker(int n) {
			highVals = new double[n];
		}
		
		public void addValue(double val) {
			if (numProcessed < highVals.length) {
				highVals[numProcessed] = val;
				if (val < smallestHigh) {
					smallestHigh = val;
					smallestIndex = numProcessed;
				}
			} else if (val > smallestHigh) {
				// replace the old smallest value with this one
				highVals[smallestIndex] = val;
				smallestHigh = Double.POSITIVE_INFINITY;
				for (int i=0; i<highVals.length; i++) {
					if (highVals[i] < smallestHigh) {
						smallestHigh = highVals[i];
						smallestIndex = i;
					}
				}
			}
			
			numProcessed++;
			sum += val;
		}
		
		public double getSum() {
			return sum;
		}
		
		public double getSumHighest() {
			return StatUtils.sum(highVals);
		}
	}
}