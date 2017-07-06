package org.opensha.commons.param.editor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.LocationConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditorConverter;
import org.opensha.commons.param.impl.StringParameter;

import com.google.common.base.Preconditions;

public class ConstrainedDiscreteLocationParameterEditor extends
		AbstractParameterEditorConverter<Location, String> {
	
	private HashMap<String, Location> map;
	
	public ConstrainedDiscreteLocationParameterEditor() {
		super();
	}
	
	public ConstrainedDiscreteLocationParameterEditor(Parameter<Location> param) {
		super(param);
	}

	@Override
	protected Parameter<String> buildParameter(Parameter<Location> myParam) {
		ParameterConstraint<Location> constraint = myParam.getConstraint();
		Preconditions.checkArgument(constraint instanceof LocationConstraint,
				"constraint must be of type LocationConstraint");
		LocationConstraint locConst = (LocationConstraint)constraint;
		List<Location> allowed = locConst.getAllowedValues();
		ArrayList<String> strings = new ArrayList<String>();
		map = new HashMap<String, Location>();
		for (Location loc : allowed) {
			String val = convertFromNative(loc);
			strings.add(val);
			map.put(val, loc);
		}
		
		StringParameter param = new StringParameter(myParam.getName(), strings, myParam.getValue().toString());
		return param;
	}

	@Override
	protected String convertFromNative(Location value) {
		return value.toString();
	}

	@Override
	protected Location convertToNative(String value) {
		// we use a hashmap here to avoid rounding errors in string conversion
		return map.get(value);
	}

}
