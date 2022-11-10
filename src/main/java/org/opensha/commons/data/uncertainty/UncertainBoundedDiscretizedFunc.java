package org.opensha.commons.data.uncertainty;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;

public interface UncertainBoundedDiscretizedFunc extends UncertainDiscretizedFunc {
	
	UncertaintyBoundType getBoundType();

	DiscretizedFunc getLower();

	DiscretizedFunc getUpper();
	
	void setBoundName(String boundName);
	
	String getBoundName();
	
	default String getDefaultBoundName() {
		UncertaintyBoundType type = getBoundType();
		if (type == null)
			return "Bounds";
		return type.toString();
	}

	default Range getYRange(int index) {
		return new Range(getLowerY(index), getUpperY(index));
	}
	
	default Range getYRange(double x) {
		return getYRange(getXIndex(x));
	}
	
	default double getUpperY(int index) {
		return getUpper().getY(index);
	}
	
	default double getUpperY(double x) {
		return getUpperY(getXIndex(x));
	}
	
	default double getLowerY(int index) {
		return getLower().getY(index);
	}

	default double getLowerY(double x) {
		return getLowerY(getXIndex(x));
	}
	
	default double getUpperMaxY() {
		return getUpper().getMaxY();
	}
	
	default double getUpperMinY() {
		return getUpper().getMinY();
	}
	
	default double getLowerMaxY() {
		return getLower().getMaxY();
	}
	
	default double getLowerMinY() {
		return getLower().getMinY();
	}

}