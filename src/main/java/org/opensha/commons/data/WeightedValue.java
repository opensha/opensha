package org.opensha.commons.data;

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
		if (value instanceof ShortNamed) {
			return ((ShortNamed) value).getShortName();
		}
		if (value instanceof Named) {
			return ((Named) value).getName();
		}
		return value.toString();
	}
}
