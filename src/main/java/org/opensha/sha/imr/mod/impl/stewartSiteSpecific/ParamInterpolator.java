package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import org.opensha.commons.data.Site;

public interface ParamInterpolator<E extends Enum<E>> {
	
	public double[] getInterpolated(PeriodDependentParamSet<E> periodParams, double period, double refPeriod,
			double tSite, double tSiteN, Site site);

}
