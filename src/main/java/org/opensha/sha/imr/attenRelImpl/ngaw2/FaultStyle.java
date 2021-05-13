package org.opensha.sha.imr.attenRelImpl.ngaw2;

/**
 * Style-of-faulting identifier.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum FaultStyle {

	/** Strike-slip fault identifier. */
	STRIKE_SLIP("Strike-Slip"),
	
	/** Normal fault identifier. */
	NORMAL("Normal"),
	
	/** Reverse fault identifier. */
	REVERSE("Reverse"),
	
	/** Unknown fault style identifier. */
	UNKNOWN("Unknown");
	
	
	private final String name;
	
	private FaultStyle(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}

}
