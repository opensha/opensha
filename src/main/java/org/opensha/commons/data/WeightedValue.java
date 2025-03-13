package org.opensha.commons.data;

import org.opensha.sha.imr.IntensityMeasureRelationship;

public class WeightedValue<E> {
	public final E value;
	public final double weight;
	
	public WeightedValue(E value, double weight) {
		super();
		this.value = value;
		this.weight = weight;
	}
	
	@Override
	public String toString() {
		if (value instanceof IntensityMeasureRelationship) {
			return ((IntensityMeasureRelationship) value).getName();
		}
		return value.getClass().getName();
	}
}
