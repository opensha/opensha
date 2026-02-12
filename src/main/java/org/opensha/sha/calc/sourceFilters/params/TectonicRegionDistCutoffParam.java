package org.opensha.sha.calc.sourceFilters.params;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter.TectonicRegionDistanceCutoffs;
import org.opensha.sha.util.TectonicRegionType;

public class TectonicRegionDistCutoffParam extends AbstractParameter<TectonicRegionDistanceCutoffs> {
	
	public static final String NAME = "Tectonic Region Distance Cutoffs";
	
	private TectonicRegionDistCutoffParamEditor editor = null;
	
	public TectonicRegionDistCutoffParam() {
		this(NAME);
	}
	
	public TectonicRegionDistCutoffParam(String name) {
		this(name, new TectonicRegionDistanceCutoffs());
	}
	
	public TectonicRegionDistCutoffParam(String name, TectonicRegionDistanceCutoffs cutoffs) {
		super(name, null, "km", cutoffs);
	}

	@Override
	public synchronized ParameterEditor getEditor() {
		if (editor == null)
			editor = new TectonicRegionDistCutoffParamEditor(this);
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}

}
