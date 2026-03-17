package org.opensha.sha.earthquake.param;

import javax.swing.JComponent;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.sha.earthquake.util.GriddedFiniteRuptureSettings;

import com.google.common.base.Preconditions;

public class GriddedFiniteRuptureSettingsParam extends AbstractParameter<GriddedFiniteRuptureSettings> {
	
	public static final String NAME = "Gridded Finite Rupture Settings";
	
	private Editor editor;
	
	public GriddedFiniteRuptureSettingsParam(GriddedFiniteRuptureSettings value) {
		super(NAME, null, null, value);
	}

	@Override
	public ParameterEditor getEditor() {
		if (editor == null)
			editor = new Editor(this);
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		return new GriddedFiniteRuptureSettingsParam(value);
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		return false;
	}
	
	private static class Editor extends AbstractParameterEditor<GriddedFiniteRuptureSettings> implements ParameterChangeListener {
		
		private ParameterList paramList;
		private ParameterListEditor paramEdit;
		
		private IntegerParameter numSurfacesParam;
		private DoubleParameter fixedStrikeParam;
		private BooleanParameter sampleAlongStrikeParam;
		private BooleanParameter sampleDownDipParam;
		
		public Editor(GriddedFiniteRuptureSettingsParam param) {
			super(param);
		}

		@Override
		public boolean isParameterSupported(Parameter<GriddedFiniteRuptureSettings> param) {
			return param != null && param instanceof GriddedFiniteRuptureSettingsParam;
		}

		@Override
		public void setEnabled(boolean enabled) {
			if (paramEdit != null)
				paramEdit.setEnabled(enabled);
		}

		@Override
		protected JComponent buildWidget() {
			paramList = new ParameterList();
			
			numSurfacesParam = new IntegerParameter("Num Finite Realizations", 1, 100, Integer.valueOf(1));
			numSurfacesParam.getConstraint().setNullAllowed(false);
			numSurfacesParam.setInfo("The number of finite rupture realizations per ruptpure. If >1, ruptures will be "
					+ "evenly distributed in a circle (e.g., 2 is a crosshair).");
			numSurfacesParam.addParameterChangeListener(this);
			paramList.addParameter(numSurfacesParam);
			
			fixedStrikeParam = new DoubleParameter("Fixed Rupture Strike", -180d, 360d);
			fixedStrikeParam.getConstraint().setNullAllowed(true);
			fixedStrikeParam.setInfo("Fixed strike for all finite rupture realizations; leave blank for random strikes.");
			fixedStrikeParam.addParameterChangeListener(this);
			paramList.addParameter(fixedStrikeParam);
			
			sampleAlongStrikeParam = new BooleanParameter("Sample Along-Strike");
			sampleAlongStrikeParam.setInfo("If false, the rupture will always be centered along-strike about the grid node. "
					+ "If true, the grid node will be randomly positioned along-strike of the rupture.");
			sampleAlongStrikeParam.addParameterChangeListener(this);
			paramList.addParameter(sampleAlongStrikeParam);
			
			sampleDownDipParam = new BooleanParameter("Sample Down-Dip");
			sampleDownDipParam.setInfo("If false, the rupture will always be centered down-dip about the grid node. "
					+ "If true, for dipping ruptures the grid node will be randomly positioned down-dip of the rupture.");
			sampleDownDipParam.addParameterChangeListener(this);
			paramList.addParameter(sampleDownDipParam);
			
			paramEdit = new ParameterListEditor(paramList);
			
			return updateWidget();
		}
		
		@Override
		protected synchronized JComponent updateWidget() {
			GriddedFiniteRuptureSettings value = getValue();
			
			boolean isSet = value != null;
			
//			System.out.println("GriddedFiniteRuptureSettingsParam.Editor.updateWidget(): value="+value);
			
			if (!isSet)
				// set to default values for display if null
				value = GriddedFiniteRuptureSettings.DEFAULT;
			
			updating = true;
			numSurfacesParam.setValue(value.numSurfaces);
			fixedStrikeParam.setValue(value.strike);
			sampleAlongStrikeParam.setValue(value.sampleAlongStrike);
			sampleDownDipParam.setValue(value.sampleDownDip);
			
			numSurfacesParam.getEditor().setEnabled(isSet);
			fixedStrikeParam.getEditor().setEnabled(isSet);
			sampleAlongStrikeParam.getEditor().setEnabled(isSet);
			sampleDownDipParam.getEditor().setEnabled(isSet);
			updating = false;
			
			paramEdit.refreshParamEditor();
			
			return paramEdit.getContents();
		}
		
		private boolean updating = false;

		@Override
		public synchronized void parameterChange(ParameterChangeEvent event) {
			if (updating)
				// in the process of updating everything, don't set the value a bunch of times
				return;
			Parameter<?> source = event.getParameter();
			Parameter<GriddedFiniteRuptureSettings> param = getParameter();
			GriddedFiniteRuptureSettings value = getValue();
			if (source == numSurfacesParam)
				param.setValue(value.forNumSurfaces(numSurfacesParam.getValue()));
			else if (source == fixedStrikeParam)
				param.setValue(value.forStrike(fixedStrikeParam.getValue()));
			else if (source == sampleAlongStrikeParam)
				param.setValue(value.forSampleAlongStrike(sampleAlongStrikeParam.getValue()));
			else if (source == sampleDownDipParam)
				param.setValue(value.forSampleDownDip(sampleDownDipParam.getValue()));
		}
		
	}

}
