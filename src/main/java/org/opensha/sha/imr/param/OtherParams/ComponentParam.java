/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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
