package scratch.UCERF3.erf.ETAS.ETAS_Params;

/**
 * Probability model options.
 * @author Ned Field
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum U3ETAS_ProbabilityModelOptions {
	FULL_TD("FullTD"),
	POISSON("Poisson"),
	NO_ERT("NoERT");
	
	private String label;
	private U3ETAS_ProbabilityModelOptions(String label) {
		this.label = label;
	}
	
	@Override public String toString() {
		return label;
	}
	

}
