package org.opensha.nshmp2.imr;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.nshmp2.util.FaultCode;

import com.google.common.base.Function;

/**
 * Interface to indicate alternate access to exceedence probabilities, typically
 * through a parent class.
 * @author Peter Powers
 * @version $Id:$
 */
public interface GridIMR {

	/**
	 * Returns an exceedence probability function as calculated by parent
	 * instead of via table lookup. This method is used when constructing
	 * Western US NGA lookup tables.
	 * @param imls
	 * @return the exceedence probability function
	 */
	public DiscretizedFunc getExceedProbFromParent(DiscretizedFunc imls);
	
//	/**
//	 * Call to trigger build of exceedance lookup table for current IMR state
//	 * @param depthForMag function that supplies mag dependent depth values
//	 * @param code FaultCode, ignored by all but CEUS Grids and may be null
//	 */
//	public void setTable(Function<Double, Double> depthForMag, FaultCode code);
}
