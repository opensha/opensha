package org.opensha.commons.data.uncertainty;

import org.opensha.commons.data.WeightedList;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;

/**
 * An {@link Uncertainty} that has bounds, e.g., 95% confidence bounds, along with a standard deviation.
 * 
 * @author kevin
 *
 */
@JsonAdapter(Uncertainty.UncertaintyAdapter.class)
public class BoundedUncertainty extends Uncertainty {
	public final UncertaintyBoundType type;
	public final double lowerBound;
	public final double upperBound;
	
	public static BoundedUncertainty fromMeanAndBounds(UncertaintyBoundType type, double bestEstimate,
			double lowerBound, double upperBound) {
		return new BoundedUncertainty(type, lowerBound, upperBound,
				type.estimateStdDev(lowerBound, upperBound));
	}
	
	public BoundedUncertainty(UncertaintyBoundType type, double lowerBound, double upperBound, double stdDev) {
		super(stdDev);
		this.type = type;
		Preconditions.checkState(Double.isFinite(lowerBound),
				"Lower uncertainty bound is non-finite: %s", (float)lowerBound);
		this.lowerBound = lowerBound;
		Preconditions.checkState(lowerBound <= upperBound,
				"Upper uncertainty bound non-finite or less than lower bound (%s): %s",
				(float)lowerBound, (float)upperBound);
		this.upperBound = upperBound;
	}
	
	@Override
	public String toString() {
		return "type="+type.name()+"\tbounds=["+(float)lowerBound+", "+(float)upperBound+"]\tstdDev="+(float)stdDev;
	}
	
	public boolean contains(double value) {
		return value >= lowerBound && value <= upperBound;
	}

	@Override
	public BoundedUncertainty scaled(double bestEstimate, double scalar) {
		double newSD = stdDev*scalar;
		return type.estimate(bestEstimate, newSD);
	}
	
	/**
	 * Computes a single, symmetric BoundedUncertainty by combining multiple weighted uncertainties using a
	 * mixture‑of‑normals approach.
	 *
	 * <p>Each branch’s interval [lowerBound, upperBound] is treated as 
	 * a Normal(μᵢ, σᵢ²) with  
	 * μᵢ = (lowerBound + upperBound)/2 and σᵢ = stdDev.  
	 * The mixture mean μ_mix and variance σ_mix² are then
	 *   μ_mix = Σ wᵢ·μᵢ,  
	 *   E[X²] = Σ wᵢ·(σᵢ² + μᵢ²),  
	 *   σ_mix² = E[X²] – μ_mix².  
	 * The returned interval is μ_mix ± z·σ_mix, where z = type.z (e.g. 1.96 for 95%).
	 *
	 * @param uncertainties  a weighted list of BoundedUncertainty instances; must be non‑empty
	 *                       and all share the same UncertaintyBoundType
	 * @return               a new BoundedUncertainty whose bounds are
	 *                       [μ_mix – z·σ_mix, μ_mix + z·σ_mix] and whose stdDev = σ_mix
	 * @throws IllegalStateException  
	 *                       if the input list is empty or contains mixed types
	 */
	public static BoundedUncertainty weightedCombination(WeightedList<BoundedUncertainty> uncertainties) {
		Preconditions.checkState(!uncertainties.isEmpty());
		if (uncertainties.size() == 1)
			return uncertainties.getValue(0);
		double weightSum = 0d;
		double muSum  = 0d; // mixture mean sum
		double ex2Sum = 0d; // second moment sum
		UncertaintyBoundType type = null;

		for (int i=0; i<uncertainties.size(); i++) {
			double weight = uncertainties.getWeight(i);
			BoundedUncertainty uncert = uncertainties.getValue(i);

			if (i == 0)
				type = uncert.type;
			else
				Preconditions.checkState(type == uncert.type,
				"All uncertainties must be of the same type (first is %s, also have %s)", type, uncert.type);

			// branch mean μ_i
			double mu_i = 0.5 * (uncert.lowerBound + uncert.upperBound);
			// branch std‑dev σ_i (you already have it)
			double sigma_i = type.estimateStdDev(uncert.lowerBound, uncert.upperBound);

			muSum += weight * mu_i;
			ex2Sum += weight * (sigma_i*sigma_i + mu_i*mu_i);
			weightSum += weight;
		}

		// normalize
		double muMix = muSum / weightSum;
		double ex2Mix = ex2Sum / weightSum;

		// mixture variance and std‑dev
		double varMix = ex2Mix - muMix*muMix;
		double stdDev = Math.sqrt(varMix);

		// symmetric bounds at ±z
		double lowerBound = muMix - type.z * stdDev;
		double upperBound = muMix + type.z * stdDev;

		return new BoundedUncertainty(type, lowerBound, upperBound, stdDev);
	}
}