package org.opensha.commons.param.constraint.impl;

import static com.google.common.base.Preconditions.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.param.constraint.AbstractParameterConstraint;
import org.opensha.commons.param.constraint.DiscreteParameterConstraint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Constraint object for <code>Enum</code> valued <code>Parameter</code>s. Note
 * that internally the set of possible values is stored as an immutable list
 * that can not be modified via iteration or <code>getAllowedValues()</code>.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class EnumConstraint<E extends Enum<E>> extends
		AbstractParameterConstraint<E> implements
		DiscreteParameterConstraint<E> {

	private List<E> values;

	/**
	 * Constructs a new constraint from the supplied set of values.
	 * @param values to use as constraints
	 * @param allowsNull <code>true</code> if constraint permits a
	 *        <code>null</code> value, <code>false</code> otherwise
	 * @throws NullPointerException if <code>values</code> is null
	 * @throws IllegalArgumentException if <code>values</code> is empty
	 */
	public EnumConstraint(EnumSet<E> values, boolean allowsNull) {
		checkNotNull(values, "Supplied value set is null");
		checkArgument(!values.isEmpty(), "Supplied value set is empty");
		setNullAllowed(allowsNull);
		// switched from ImmutableList, see ticket #451
		this.values = Collections.unmodifiableList(Lists.newArrayList(values));
	}

	@Override
	public boolean isAllowed(E value) {
		return (value == null && isNullAllowed()) ? true : values.contains(value);
	}

	@Override
	public List<E> getAllowedValues() {
		return values;
	}

	@Override
	public ListIterator<E> listIterator() {
		return values.listIterator();
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public Object clone() {
		return new EnumConstraint<E>(EnumSet.copyOf(values), isNullAllowed());
	}

}
