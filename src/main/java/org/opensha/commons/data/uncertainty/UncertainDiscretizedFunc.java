package org.opensha.commons.data.uncertainty;

import org.opensha.commons.data.function.DiscretizedFunc;

public interface UncertainDiscretizedFunc extends DiscretizedFunc {
	
	DiscretizedFunc getStdDevs();
	
	default double getMaxStdDev() {
		return getStdDevs().getMaxY();
	}
	
	default double getMinStdDev() {
		return getStdDevs().getMinY();
	}
	
	default double getStdDev(int index) {
		return getStdDevs().getY(index);
	}
	
	default double getStdDev(double x) {
		return getStdDevs().getY(x);
	}
	
	UncertainBoundedDiscretizedFunc estimateBounds(UncertaintyBoundType boundType);

}
