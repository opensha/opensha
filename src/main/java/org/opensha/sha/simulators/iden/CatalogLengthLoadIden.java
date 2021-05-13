package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.base.Preconditions;

public class CatalogLengthLoadIden extends AbstractRuptureIdentifier {

	private double firstEventYears = Double.NaN;
	private final double lengthYears;
	private boolean encounteredEnd = false;
	
	public CatalogLengthLoadIden(double lengthYears) {
		Preconditions.checkArgument(lengthYears > 0);
		this.lengthYears = lengthYears;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double years = event.getTimeInYears();
		Preconditions.checkState(Double.isFinite(years));
		if (Double.isNaN(firstEventYears)) {
			// this is the first one
			firstEventYears = years;
		}
		Preconditions.checkState(years >= firstEventYears);
		double diff = years - firstEventYears;
		
		boolean match = diff <= lengthYears;
		if (!match)
			encounteredEnd = true;
		return match;
	}

	@Override
	public String getName() {
		return "Total Length Iden";
	}

	@Override
	public boolean furtherMatchesPossible() {
		return !encounteredEnd;
	}

}
