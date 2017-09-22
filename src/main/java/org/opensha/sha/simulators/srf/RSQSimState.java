package org.opensha.sha.simulators.srf;

public enum RSQSimState {

	LOCKED,
	NUCLEATING_SLIP,
	EARTHQUAKE_SLIP;
	
	public static RSQSimState forInt(int stateInt) {
		switch (stateInt) {
		case 0:
			return LOCKED;
		case 1:
			return NUCLEATING_SLIP;
		case 2:
			return EARTHQUAKE_SLIP;

		default:
			throw new IllegalStateException("Unkown state: "+stateInt);
		}
	}
	
}
