package scratch.UCERF3.erf.ETAS.ETAS_Params;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

import scratch.UCERF3.utils.U3_EqkCatalogStatewideCompleteness;

public class U3ETAS_StatewideCatalogCompletenessParam extends EnumParameter<U3_EqkCatalogStatewideCompleteness> {
	
	public final static String NAME = "Statewide Completeness Model";
	public final static String INFO = "Statewide completeness model which will be used to filter a historical catalog, and control the rate "
			+ "of spontaneous ruptures (children from missing events in that catalog)";
	public final static U3_EqkCatalogStatewideCompleteness DEFAULT_VALUE = U3_EqkCatalogStatewideCompleteness.RELAXED;

	public U3ETAS_StatewideCatalogCompletenessParam() {
		super(NAME, EnumSet.allOf(U3_EqkCatalogStatewideCompleteness.class), DEFAULT_VALUE, null);
		setInfo(INFO);
	}

}
