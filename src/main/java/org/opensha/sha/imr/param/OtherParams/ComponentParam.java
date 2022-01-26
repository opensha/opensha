package org.opensha.sha.imr.param.OtherParams;

import java.util.EnumSet;
import java.util.List;

import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;

import com.google.common.collect.Lists;

/**
 * Component Parameter, reserved for representing the component of shaking
 * (in 3D space). See {@link Component} for options.
 */

public class ComponentParam extends EnumParameter<Component> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Component";
	public final static String INFO = "Component of shaking";
	
	public ComponentParam(Component defaultValue, Component... options) {
		this(defaultValue, Lists.newArrayList(options));
	}
	
	public ComponentParam(Component defaultValue, List<Component> options) {
		super(NAME, EnumSet.copyOf(options), defaultValue, null);
		setInfo(INFO);
	}
}
