package org.opensha.sha.calc.params.filters;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;

public class SourceFiltersParam extends AbstractParameter<SourceFilterManager> {
	
	public static final String NAME = "Source Filters";
	
	private SourceFiltersParamEditor editor = null;
	
	public SourceFiltersParam() {
		super(NAME, null, null, new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF));
	}

	@Override
	public SourceFiltersParamEditor getEditor() {
		if (editor == null)
			editor = new SourceFiltersParamEditor(this);
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		SourceFiltersParam other = new SourceFiltersParam();
		other.setValue(getValue());
		return other;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}

}
