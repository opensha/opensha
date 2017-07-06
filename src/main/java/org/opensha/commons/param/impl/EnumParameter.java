package org.opensha.commons.param.impl;

import java.util.EnumSet;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.EnumConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.EnumParameterEditor;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

/**
 * Parameter class for <code>Enum</code> valued types. The sole constructor
 * accepts a name, the choices that should be made available to the user, a
 * default value, and a <code>String</code> that will be displayed to represent
 * a <code>null</code> value. Quite often this argument can be left
 * <code>null</code>. However a scenario in which one might want a null value
 * would be in using an <code>Enum</code> as a filter. In this case one might
 * want an option to not apply the filter and a value of "All" or "None" might
 * be more appropriate. Internally, the supplied choices are used to create
 * a constraint object.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class EnumParameter<E extends Enum<E>> extends AbstractParameter<E> {

	private EnumParameterEditor<E> editor;
	private String nullOption;
	private Class<E> clazz;
	private EnumSet<E> choices;

	// private; for internal cloning use only
	private EnumParameter() {};
	
	/**
	 * Constructs an <code>Enum</code> valued <code>Parameter</code> from the
	 * supplied name, constraint <code>Set</code>, and default value.
	 * @param name for this <code>Parameter</code>
	 * @param choices to be made available
	 * @param defaultValue
	 * @param nullOption <code>String</code> used to represent <code>null</code>
	 *        option in editor; may be <code>null</code>
	 * @throws NullPointerException if <code>choices</code> is null
	 * @throws IllegalArgumentException if <code>choices</code> is empty
	 */
	@SuppressWarnings("unchecked")
	public EnumParameter(String name, EnumSet<E> choices, E defaultValue,
		String nullOption) {
		super(name, new EnumConstraint<E>(choices, nullOption != null), null,
			defaultValue);
		clazz = (Class<E>) Iterables.getFirst(choices, null).getClass();
		this.choices = choices;
		if (choices.size() > 1) {
			if (!clazz.isEnum()) {
				// this checks to see if the enum class is actually a concrete implementation
				// of an abstract enum (this means that the enum has an abstract method that is
				// Implemented by each instance - see AveSlipForRupModels for an example). in
				// this case we need the parent class, which is the actual enum class itself.
				clazz = (Class<E>) clazz.getEnclosingClass();
			}
		}
		this.nullOption = nullOption;
		setDefaultValue(defaultValue);
	}

	@Override
	public ParameterEditor<E> getEditor() {
		if (editor == null) {
			editor = new EnumParameterEditor<E>(this, clazz);
		}
		return editor;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object clone() {
		EnumParameter<E> ep = new EnumParameter<E>();
		ep.nullOption = nullOption;
		ep.constraint = (ParameterConstraint<E>) constraint.clone();
		ep.defaultValue = defaultValue;
		ep.value = value;
		ep.name = name;
		ep.choices = choices;
		return ep;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		String val = el.attributeValue("value");
		for (E choice : choices) {
			if (choice.toString().equals(val)) {
				setValue(choice);
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the word or phrase to be used in an editor that corresponds to a
	 * <code>null</code> value.
	 * @return the <code>String</code> representation of a <code>null</code>
	 *         value; may return <code>null</code> if <code>null</code> values
	 *         are not permitted by this <code>Parameter</code>'s constraint
	 */
	public String getNullOption() {
		return nullOption;
	}

	@Override
	public EnumConstraint<E> getConstraint() {
		return (EnumConstraint<E>) super.getConstraint();
	}

}
