package org.opensha.nshmp2.util;

/**
 * Identifier for different methods of NSHMP grid node source handling. Grid
 * node sources can be handled as point-sources or as finite faults. In all
 * cases where finite faults are employed, they are applied only to events where
 * M&gt;6. There are also magnitude conversion flags nested in this identifier
 * class.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum FaultCode {

	/** Do not use finite sources */
	OFF(0),

	/** Use finite sources for M&ge;6. */
	ON(1),

	/** Use finite sources with fixed strike for M&ge;6. */
	FIXED(2),

	/** Use finite sources for M&ge;6 with Johston mblg to Mw converter. */
	M_CONV_J(3),

	/**
	 * Use finite sources for M&ge;6 with Atkinson and Booore mblg to Mw
	 * converter.
	 */
	M_CONV_AB(4);
	
	private int id;
	private FaultCode(int id) {
		this.id = id;
	}

	public static FaultCode typeForID(int id) {
		for (FaultCode ff : FaultCode.values()) {
			if (ff.id == id) return ff;
		}
		return null;
	}

}
