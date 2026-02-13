package org.opensha.sha.calc.sourceFilters.params;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;

public class SourceFiltersParam extends AbstractParameter<SourceFilterManager> {
	
	public static final String NAME = "Source Filters";
	
	private SourceFiltersParamEditor editor = null;
	
	public static SourceFilterManager getDefault() {
		return new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
	}
	
	public SourceFiltersParam() {
		this(getDefault());
	}
	
	public SourceFiltersParam(SourceFilterManager value) {
		super(NAME, null, null, value);
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
