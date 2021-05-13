package org.opensha.sha.simulators.srf;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;

public enum RSQSimState {
	
	/*
	 * Descriptions from Keith 10/19/18
	 * 
	 * #define LOCKED         0
	 * #define NUCLEATE       1
	 * #define RUPTURE        2
	 * #define CREEP          3
	 * #define LOW_SIGMA      4
	 * #define LOW_TAU        5
	 * #define HIGH_THETA     6
	 * #define SLOWSLIP_2A    7
	 * #define SLOWSLIP_2B    8
	 * #define SLOWSLIP_2C    9
	 * #define HIGH_TAU      10
	 * #define MAX_TIME_STEP 11
	 * #define STRESS_RATE_STEP 12
	 * #define HIGH_SIGMA    13
	 */

	LOCKED(0),
	NUCLEATING_SLIP(1),
	EARTHQUAKE_SLIP(2),
	CREEP(3),
	LOW_SIGMA(4),
	LOW_TAU(5),
	HIGH_THETA(6),
	SLOW_SLIP_2A(7),
	SLOW_SLIP_2B(8),
	SLOW_SLIP_2C(9),
	HIGH_TAU(10),
	MAX_TIME_STEP(11),
	STRESS_RATE_STEP(12),
	HIGH_SIGMA(13);
	
	private int stateInt;
	
	private RSQSimState(int stateInt) {
		this.stateInt = stateInt;
	}
	
	public int getStateInt() {
		return stateInt;
	}
	
	private static final Map<Integer, RSQSimState> statesMap;
	static {
		statesMap = new HashMap<>();
		for (RSQSimState state : values())
			statesMap.put(state.stateInt, state);
	}
	
	public static RSQSimState forInt(int stateInt) {
		RSQSimState state = statesMap.get(stateInt);
		Preconditions.checkState(state != null, "Unknown state: "+stateInt);
		return state;
	}
	
}
