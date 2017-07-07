package org.opensha.sha.simulators.iden;

import java.util.Collection;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.collect.Lists;

public class LogicalOrRupIden extends AbstractRuptureIdentifier {
	
	private Collection<? extends RuptureIdentifier> rupIdens;
	private String name;

	public LogicalOrRupIden(RuptureIdentifier... rupIdens) {
		this(Lists.newArrayList(rupIdens));
	}
	
	public LogicalOrRupIden(Collection<? extends RuptureIdentifier> rupIdens) {
		this.rupIdens = rupIdens;
		this.name = "Logical and rupture identfier with "+rupIdens.size()+" idens.";
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		for (RuptureIdentifier rupIden : rupIdens)
			if (rupIden.isMatch(event))
				return true;
		return false;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public Collection<? extends RuptureIdentifier> getSubIdens() {
		return rupIdens;
	}

}
