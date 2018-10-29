package org.opensha.sha.simulators.iden;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

public class FocalMechIden extends AbstractRuptureIdentifier {
	
	private Collection<Range<Double>> rakeRanges;
	private boolean rakeMatchAverage = false;
	
	private Collection<Range<Double>> dipRanges;
	private boolean dipMatchAverage = false;
	
	private Collection<Range<Double>> strikeRanges;
	private boolean strikeMatchAverage = false;
	
	private HashSet<SimulatorElement> failElems;
	
	private FocalMechIden() {
		failElems = new HashSet<>();
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private FocalMechIden iden;
		
		private Builder() {
			iden = new FocalMechIden();
		}
		
		public Builder forRake(double rake) {
			return forRake(rake, rake);
		}
		
		public Builder forRake(double minRake, double maxRake) {
			return forRake(Range.closed(minRake, maxRake));
		}
		
		public Builder forRake(Range<Double> range) {
			if (iden.rakeRanges == null)
				iden.rakeRanges = new ArrayList<>();
			iden.rakeRanges.add(range);
			return this;
		}
		
		public Builder strikeSlip(double tolerance) {
			if (tolerance == 0d)
				return forRake(0d).forRake(180).forRake(-180);
			Preconditions.checkState(tolerance > 0);
			return forRake(-180d, -180d+tolerance).forRake(-tolerance, tolerance).forRake(180d-tolerance, 180d);
		}
		
		public Builder averageRake() {
			iden.rakeMatchAverage = true;
			return this;
		}
		
		public Builder forDip(double dip) {
			return forDip(dip, dip);
		}
		
		public Builder forDip(double minDip, double maxDip) {
			return forDip(Range.closed(minDip, maxDip));
		}
		
		public Builder forDip(Range<Double> range) {
			if (iden.dipRanges == null)
				iden.dipRanges = new ArrayList<>();
			iden.dipRanges.add(range);
			return this;
		}
		
		public Builder averageDip() {
			iden.dipMatchAverage = true;
			return this;
		}
		
		public Builder forStrike(double dip) {
			return forStrike(dip, dip);
		}
		
		public Builder forStrike(double minDip, double maxDip) {
			return forStrike(Range.closed(minDip, maxDip));
		}
		
		public Builder forStrike(Range<Double> range) {
			if (iden.strikeRanges == null)
				iden.strikeRanges = new ArrayList<>();
			iden.strikeRanges.add(range);
			return this;
		}
		
		public Builder averageStrike() {
			iden.strikeMatchAverage = true;
			return this;
		}
		
		public FocalMechIden build() {
			Preconditions.checkState(iden.dipRanges != null || iden.rakeRanges != null || iden.strikeRanges != null,
					"No criteria provided!");
			return iden;
		}
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		ArrayList<SimulatorElement> elems = event.getAllElements();
		List<Double> moments = null;
		List<Double> rakes = null, dips = null, strikes = null;
		if (dipRanges != null && dipMatchAverage) {
			moments = new ArrayList<>();
			dips = new ArrayList<>();
		}
		if (rakeRanges != null && rakeMatchAverage) {
			if (moments == null)
				moments = new ArrayList<>();
			rakes = new ArrayList<>();
		}
		if (strikeRanges != null && strikeMatchAverage) {
			if (moments == null)
				moments = new ArrayList<>();
			strikes = new ArrayList<>();
		}
		double[] slips = null;
		if (moments != null)
			slips = event.getAllElementSlips();
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem = elems.get(i);
			if (failElems.contains(elem))
				return false;
			FocalMechanism mech = elem.getFocalMechanism();
			if (moments != null) {
				// units don't matter so ignore shear modulus
				moments.add(slips[i]*elem.getArea());
			}
			
			if (strikes != null) {
				strikes.add(mech.getStrike());
			} else if (!matches(mech.getStrike(), strikeRanges)) {
				failElems.add(elem);
				return false;
			}
			
			if (dips != null) {
				dips.add(mech.getDip());
			} else if (!matches(mech.getDip(), dipRanges)) {
				failElems.add(elem);
				return false;
			}
			
			if (rakes != null) {
				rakes.add(mech.getRake());
			} else if (!matches(mech.getRake(), rakeRanges)) {
				failElems.add(elem);
				return false;
			}
		}
		
		if (rakes != null) {
			// check average rake
			double avgRake = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(moments, rakes));
			if (!matches(avgRake, rakeRanges))
				return false;
		}
		
		if (dips != null) {
			// check average dip
			double avgDip = FaultUtils.getScaledAngleAverage(moments, dips);
			if (!matches(avgDip, dipRanges))
				return false;
		}
		
		if (strikes != null) {
			// check average strike
			double avgStrike = FaultUtils.getScaledAngleAverage(moments, strikes);
			if (!matches(avgStrike, strikeRanges))
				return false;
		}
		return true;
	}
	
	private static boolean matches(double value, Collection<Range<Double>> ranges) {
		if (ranges == null)
			return true;
		for (Range<Double> range : ranges)
			if (range.contains(value))
				return true;
		return false;
	}

	@Override
	public String getName() {
		return "Focal Mechanism Identifier";
	}

}
