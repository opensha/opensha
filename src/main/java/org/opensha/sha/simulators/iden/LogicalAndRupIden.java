package org.opensha.sha.simulators.iden;

import java.util.Collection;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.collect.Lists;

public class LogicalAndRupIden extends AbstractRuptureIdentifier {
	
	public static List<RuptureIdentifier> buildAsList(Collection<RuptureIdentifier> rupIdens) {
		List<RuptureIdentifier> idens = Lists.newArrayList();
		
		idens.add(new LogicalAndRupIden(rupIdens));
		
		return idens;
	}
	
	private Collection<RuptureIdentifier> rupIdens;

	public LogicalAndRupIden(RuptureIdentifier... rupIdens) {
		this(Lists.newArrayList(rupIdens));
	}
	
	public LogicalAndRupIden(Collection<RuptureIdentifier> rupIdens) {
		this.rupIdens = rupIdens;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		for (RuptureIdentifier rupIden : rupIdens)
			if (!rupIden.isMatch(event))
				return false;
		return true;
	}

	@Override
	public String getName() {
		return "Logical and rupture identfier with "+rupIdens.size()+" idens.";
	}
	
	public Collection<RuptureIdentifier> getSubIdens() {
		return rupIdens;
	}

}
