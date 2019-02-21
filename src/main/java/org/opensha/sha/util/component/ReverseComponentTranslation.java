package org.opensha.sha.util.component;

import org.opensha.sha.imr.param.OtherParams.Component;

public class ReverseComponentTranslation extends ComponentTranslation {
	
	private ComponentTranslation trans;
	
	public ReverseComponentTranslation(ComponentTranslation trans) {
		this.trans = trans;
	}

	@Override
	public String getName() {
		return trans.getName();
	}

	@Override
	public Component getFromComponent() {
		return trans.getToComponent();
	}

	@Override
	public Component getToComponent() {
		return trans.getFromComponent();
	}

	@Override
	public double getScalingFactor(double period) throws IllegalArgumentException {
		return 1d/trans.getScalingFactor(period);
	}

	@Override
	public double getMinPeriod() {
		return trans.getMinPeriod();
	}

	@Override
	public double getMaxPeriod() {
		return trans.getMaxPeriod();
	}

}
