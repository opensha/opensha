package org.opensha.commons.param.impl;

import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.LocationConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ConstrainedDiscreteLocationParameterEditor;
import org.opensha.commons.param.editor.impl.LocationParameterEditor;

public class LocationParameter extends AbstractParameter<Location> {
	
	private transient ParameterEditor<Location> paramEdit = null;
	private boolean showDepthInEditor = true;
	
	public LocationParameter(String name) {
		this(name, null);
	}
	
	public LocationParameter(String name, Location location) {
		super(name, null, null, location);
	}
	
	public LocationParameter(String name, List<Location> allowed, Location location) {
		this(name, new LocationConstraint(allowed), location);
	}
	
	public LocationParameter(String name, ParameterConstraint<Location> constraint, Location location) {
		super(name, constraint, null, location);
	}

	@Override
	public ParameterEditor getEditor() {
		if (paramEdit == null) {
			ParameterConstraint<Location> constraint = getConstraint();
			if (constraint == null) {
				paramEdit = new LocationParameterEditor(this, showDepthInEditor);
			} else {
				paramEdit = new ConstrainedDiscreteLocationParameterEditor(this);
			}
		}
		return paramEdit;
	}

	@Override
	public Object clone() {
		return new LocationParameter(getName(), getConstraint(), getValue());
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}
	
	/**
	 * If false, this will hide the depth parameter in the editor;
	 * @param showDepthInEditor
	 */
	public void setShowDepthInEditor(boolean showDepthInEditor) {
		this.showDepthInEditor = showDepthInEditor;
		if (paramEdit != null && paramEdit instanceof LocationParameterEditor)
			((LocationParameterEditor)paramEdit).setShowDepth(showDepthInEditor);
	}

}
