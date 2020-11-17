package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Some plausibility filters are directional, e.g., a rupture will pass if built in one direction (from A->B)
 * but fail in another (from B->A). The rupture building algorithm generally gets around this by trying every
 * possible starting point, but if you are doing filter tests against an already build rupture set you can
 * wrap a plausibility filter with this in order to test all directions. 
 * @author kevin
 *
 */
public class MultiDirectionalPlausibilityFilter implements PlausibilityFilter {
	
	private PlausibilityFilter filter;
	private boolean onlyWhenSplayed;

	// must supply one or the other
	private RuptureConnectionSearch connSearch;
	private PlausibilityConfiguration plausibilityConfig;

	public MultiDirectionalPlausibilityFilter(PlausibilityFilter filter,
			PlausibilityConfiguration plausibilityConfig, boolean onlyWhenSplayed) {
		this.filter = filter;
		this.plausibilityConfig = plausibilityConfig;
		this.onlyWhenSplayed = onlyWhenSplayed;
	}

	public MultiDirectionalPlausibilityFilter(PlausibilityFilter filter,
			RuptureConnectionSearch connSearch, boolean onlyWhenSplayed) {
		this.filter = filter;
		this.connSearch = connSearch;
		this.onlyWhenSplayed = onlyWhenSplayed;
	}

	@Override
	public String getShortName() {
		return filter.getShortName();
	}

	@Override
	public String getName() {
		return filter.getName();
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result;
		RuntimeException error = null;
		try {
			result = filter.apply(rupture, verbose);
			if (result.isPass() || onlyWhenSplayed && rupture.splays.isEmpty()) {
				return result;
			}
		} catch (RuntimeException e) {
			if (onlyWhenSplayed && rupture.splays.isEmpty())
				throw e;
			result = null;
			error = e;
		}
		// try other paths through the rupture
		List<ClusterRupture> inversions = getInversions(rupture);
		if (verbose)
			System.out.println("MultiDirectional "+getShortName()
				+": trying "+inversions.size()+" inversions with original result="+result);
		for (ClusterRupture altRupture : inversions) {
			try {
				if (verbose)
					System.out.println("MultiDirectional "+getShortName()
						+": inversion="+altRupture);
				result = filter.apply(altRupture, verbose);
				if (verbose)
					System.out.println("MultiDirectional "+getShortName()
						+": inversion result="+result);
				if (result.isPass())
					return result;
			} catch (RuntimeException e) {
				error = e;
			}
		}
		if (result == null && error != null)
			throw error;
		return result;
	}
	
	private List<ClusterRupture> getInversions(ClusterRupture rupture) {
		if (plausibilityConfig != null)
			// this will test all possible inversions as defined by the connection strategy
			return rupture.getAllAltRepresentations(plausibilityConfig.getConnectionStrategy(),
					plausibilityConfig.getMaxNumSplays());
		Preconditions.checkNotNull(connSearch);
		// this will test all starting points for the rupture, but chooses the best
		// path through the rupture from each starting point (not exhaustive)
		return rupture.getPreferredAltRepresentations(connSearch);
	}
	
	public static class Scalar<E extends Number & Comparable<E>> extends MultiDirectionalPlausibilityFilter
	implements ScalarValuePlausibiltyFilter<E> {
		
		private ScalarValuePlausibiltyFilter<E> filter;
		private Range<E> range;
		private Double lower;
		private Double upper;

		public Scalar(ScalarValuePlausibiltyFilter<E> filter, RuptureConnectionSearch connSearch,
				boolean onlyWhenSplayed) {
			super(filter, connSearch, onlyWhenSplayed);
			this.filter = filter;
			this.range = filter.getAcceptableRange();
			if (range != null) {
				if (range.hasLowerBound())
					lower = ((Number)range.lowerEndpoint()).doubleValue();
				if (range.hasUpperBound())
					upper = ((Number)range.upperEndpoint()).doubleValue();
			}
		}

		public Scalar(ScalarValuePlausibiltyFilter<E> filter, PlausibilityConfiguration plausibilityConfig,
				boolean onlyWhenSplayed) {
			super(filter, plausibilityConfig, onlyWhenSplayed);
			this.filter = filter;
			this.range = filter.getAcceptableRange();
			if (range != null) {
				if (range.hasLowerBound())
					lower = ((Number)range.lowerEndpoint()).doubleValue();
				if (range.hasUpperBound())
					upper = ((Number)range.upperEndpoint()).doubleValue();
			}
		}

		@Override
		public E getValue(ClusterRupture rupture) {
			E scalar = filter.getValue(rupture);
			if (super.onlyWhenSplayed && rupture.splays.isEmpty() || range == null)
				return scalar;
			for (ClusterRupture inversion : super.getInversions(rupture)) {
				// see if there's a better version available
				E altScalar = filter.getValue(inversion);
				boolean better = isScalarBetter(altScalar, scalar);
				if (altScalar != null && better)
					// this one is better
					scalar = altScalar;
			}
			return scalar;
		}
		
		private boolean isScalarBetter(E curNumber, E prevNumber) {
			Preconditions.checkState(upper != null || lower != null);
			if (curNumber == null)
				return false;
			if (prevNumber == null)
				return true;
			double curVal = curNumber.doubleValue();
			double prevVal = prevNumber.doubleValue();
			if (upper == null) {
				// we only have a lower bound, so higher values are better
				return curVal > prevVal;
			}
			if (lower == null) {
				// we only have an upper bound, so lower values are better
				return curVal < prevVal;
			}
			// we have both, use distance to center (lower being better)
			double center = 0.5*(lower + upper);
			return Math.abs(curVal - center) < Math.abs(prevVal - center);
		}

		@Override
		public Range<E> getAcceptableRange() {
			return filter.getAcceptableRange();
		}

		@Override
		public String getScalarName() {
			return filter.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			return filter.getScalarUnits();
		}
		
	}

}
