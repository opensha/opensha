package org.opensha.sha.earthquake.param;

/**
 * Probability model options.
 * @author Ned Field
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum ProbabilityModelOptions {
	POISSON("Poisson"),
	U3_BPT("UCERF3 BPT"),
	U3_PREF_BLEND("UCERF3 Preferred Blend"),
	WG02_BPT("WG02 BPT");
	
	private String label;
	private ProbabilityModelOptions(String label) {
		this.label = label;
	}
	
	@Override public String toString() {
		return label;
	}
	

}
