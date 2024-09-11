package org.opensha.sha.calc.disaggregation;

import java.util.Collection;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.sha.earthquake.ProbEqkSource;

import com.google.common.base.Preconditions;

/**
 * <p>Title: DisaggregationSourceInfo</p>
 *
 * <p>Description: Stores the Source info. required for Disaggregation.</p>
 *
 * @author
 * @version 1.0
 */
public class DisaggregationSourceRuptureInfo {

	private String name;
	private double rate;
	private int id;
	private ProbEqkSource source;
	
	private DiscretizedFunc exceedProbs;

	public DisaggregationSourceRuptureInfo(String name, double rate, int id, ProbEqkSource source) {
		this(name, rate, id, source, null);
	}

	public DisaggregationSourceRuptureInfo(String name, double rate, int id, ProbEqkSource source, DiscretizedFunc exceedProbs) {
		this.name = name;
		this.rate = rate;
		this.id = id;
		this.source = source;
		this.exceedProbs = exceedProbs;
	}

	public int getId(){
		return id;
	}

	public double getRate(){
		return rate;
	}

	public String getName(){
		return name;
	}
	
	public ProbEqkSource getSource() {
		return source;
	}
	
	public DiscretizedFunc getExceedProbs() {
		return exceedProbs;
	}
	
	/**
	 * Returns a copy where the rate (and exceedance probabilities, if applicable) are scaled by the given scalar.
	 * For exceedance probabilities, they are converted to poisson-equivalent rates (using the supplied duration),
	 * scaled, and then converted back to probabilities.
	 * 
	 * This is useful when converting from participation to nucleation as part of the consolidation process
	 * 
	 * @param scalar
	 * @param duration
	 * @return scaled view of this {@link DisaggregationSourceRuptureInfo}
	 */
	public DisaggregationSourceRuptureInfo getScaled(double scalar, double duration) {
		DiscretizedFunc exceedProbs = this.exceedProbs;
		if (exceedProbs != null) {
			exceedProbs = exceedProbs.deepClone();
			for (int i=0; i<exceedProbs.size(); i++) {
				double origProb = exceedProbs.getY(i);
				double origRate = -Math.log(1.0 - origProb)/duration;
				double modRate = origRate*scalar;
				double modProb = 1d - Math.exp(-modRate*duration);
				exceedProbs.set(i, modProb);
			}
		}
		return new DisaggregationSourceRuptureInfo(name, rate*scalar, id, source, exceedProbs);
	}
	
	/**
	 * Utility method to consolidate source-rupture info objects from multiple sources into a single contribution
	 * @param contribs
	 * @param consolidatedIndex
	 * @param consolidatedName
	 * @return
	 */
	public static DisaggregationSourceRuptureInfo consolidate(Collection<DisaggregationSourceRuptureInfo> contribs,
			int consolidatedIndex, String consolidatedName) {
		DiscretizedFunc nonExceeds = null;
		double rate = 0d;
		for (DisaggregationSourceRuptureInfo contrib : contribs) {
			DiscretizedFunc exceeds = contrib.getExceedProbs();
			if (exceeds != null) {
				if (nonExceeds == null) {
					// first time (hopefully)
					Preconditions.checkState(rate == 0d, "Some but not all source/rup infos have exceedance probs");
					// initialize to 1 (we'll sum non-exceedances)
					double[] xVals = new double[exceeds.size()];
					double[] yVals = new double[exceeds.size()];
					for (int i=0; i<xVals.length; i++) {
						xVals[i] = exceeds.getX(i);
						yVals[i] = 1;
					}
					nonExceeds = new LightFixedXFunc(xVals, yVals);
				} else {
					// make sure they're identical
					Preconditions.checkState(nonExceeds.size() == exceeds.size(),
							"Some source/rup infos have different exceedenace function sizes");
				}
				for (int i=0; i<exceeds.size(); i++) {
					Preconditions.checkState((float)exceeds.getX(i) == (float)nonExceeds.getX(i),
							"Some source/rup infos have different x-values");
					nonExceeds.set(i, nonExceeds.getY(i) * (1d-exceeds.getY(i)));
				}
			}
			rate += contrib.getRate();
		}
		DiscretizedFunc exceedProps = null;
		if (nonExceeds != null) {
			// convert to exeedance probs
			double[] xVals = new double[nonExceeds.size()];
			double[] yVals = new double[nonExceeds.size()];
			for (int i=0; i<xVals.length; i++) {
				xVals[i] = nonExceeds.getX(i);
				yVals[i] = 1d - nonExceeds.getY(i);
			}
			exceedProps = new LightFixedXFunc(xVals, yVals);
		}
		return new DisaggregationSourceRuptureInfo(consolidatedName, rate, consolidatedIndex, null, exceedProps);
	}
}
