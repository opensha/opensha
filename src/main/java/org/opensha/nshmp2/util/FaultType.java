package org.opensha.nshmp2.util;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum FaultType {
	
	CH(1, "Characteristic"),
	GR(2, "Gutenberg-Richter"),
	GRB0(-2, "Gutenberg-Richter (w/ B=0)");
	
	private int id;
	private String label;
	private FaultType(int id, String label) {
		this.id = id;
		this.label = label;
	}
	
	public static FaultType typeForID(int id) {
		for (FaultType ft : FaultType.values()) {
			if (ft.id == id) return ft;
		}
		return null;
	}
	
	@Override
	public String toString() {
		return label;
	}
}
