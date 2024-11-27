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
import org.opensha.commons.param.impl.ParameterListParameter;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;

public class GridCellSupersamplingParam extends AbstractParameter<GridCellSupersamplingSettings> {
	
	public static String NAME = "Grid Cell Supersampling";
	
	private Editor editor;
	
	public GridCellSupersamplingParam() {
		this(null);
		this.setInfo("These settings enable and control supersampling of gridded seismicity sources across the original "
				+ "cell represented by each grid node.");
	}
	
	public GridCellSupersamplingParam(GridCellSupersamplingSettings params) {
		super(NAME, null, null, params);
	}

	@Override
	public ParameterEditor getEditor() {
		if (editor == null)
			editor = new Editor(this);
		editor.setValue(getValue());
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		return new GridCellSupersamplingParam(getValue());
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		throw new UnsupportedOperationException("Not supported");
	}
	
	private static class Editor extends AbstractParameterEditor<GridCellSupersamplingSettings> implements ParameterChangeListener {
		
		private ParameterList paramList;
		private ParameterListEditor paramEdit;
		
		private BooleanParameter enabledParam;
		private ParameterList settingsList;
		private DoubleParameter targetSpacingParam;
		private DoubleParameter fullDistParam;
		private DoubleParameter borderDistParam;
		private DoubleParameter cornerDistParam;
		
		private ParameterListParameter settingsListParam;
		
		public Editor(GridCellSupersamplingParam param) {
			super(param);
		}
		
		private Double getCutoffParamValue(double cutoff) {
			if (cutoff > 0d)
				return cutoff;
			return null;
		}
		
		private double paramValueToCutoff(DoubleParameter param) {
			Double paramValue = param.getValue();
			if (paramValue == null)
				return 0d;
			return paramValue.doubleValue();
		}

		@Override
		public boolean isParameterSupported(Parameter<GridCellSupersamplingSettings> param) {
			return param != null;
		}

		@Override
		public void setEnabled(boolean enabled) {
			if (paramEdit != null)
				paramEdit.setEnabled(enabled);
		}

		@Override
		protected JComponent buildWidget() {
			paramList = new ParameterList();
			
			enabledParam = new BooleanParameter("Enable", false);
			enabledParam.setInfo("This enables supersampling of gridded seismicity across the original cell represented"
					+ " by each grid node. This is done in a distance-dependent manner for computational efficiency. "
					+ "Sites near the source will be supersampled, but those further away will not be. Sampling parameters "
					+ "are set to default values initially but can be edited once enabled.");
			enabledParam.addParameterChangeListener(this);
			paramList.addParameter(enabledParam);
			
			settingsList = new ParameterList();
			
			targetSpacingParam = new DoubleParameter("Target Grid Spacing", 0.1d, 10,
					(Double)GridCellSupersamplingSettings.TARGET_SPACING_DEFAULT);
			targetSpacingParam.getConstraint().setNullAllowed(false);
			targetSpacingParam.setInfo("Target supersampling spacing in kilometers. This is used to determine the number "
					+ "of samples in each direction (latitude and longitude), but adapts to non-rectangular grid cells "
					+ "further from the equator.");
			targetSpacingParam.setUnits("km");
			settingsList.addParameter(targetSpacingParam);
			
			fullDistParam = new DoubleParameter("Full Supersampling Distance", 0d, 1000,
					(Double)GridCellSupersamplingSettings.FULL_DISTANCE_DEFAULT);
			targetSpacingParam.getConstraint().setNullAllowed(true);
			fullDistParam.setInfo("The site-to-grid-center distance below which grid cells will be fully supersampled. "
					+ "Set to 0 or black to disable full supersampling at all distances.");
			fullDistParam.setUnits("km");
			settingsList.addParameter(fullDistParam);
			
			borderDistParam = new DoubleParameter("Border Supersampling Distance", 0d, 1000,
					(Double)GridCellSupersamplingSettings.BORDER_DISTANCE_DEFAULT);
			borderDistParam.getConstraint().setNullAllowed(true);
			borderDistParam.setInfo("The site-to-grid-center distance below which grid cell borders will be supersampled "
					+ "(but interiors will not be). Set to 0 or black to disable border supersampling at all distances.");
			borderDistParam.setUnits("km");
			settingsList.addParameter(borderDistParam);
			
			cornerDistParam = new DoubleParameter("Corner Supersampling Distance", 0d, 1000,
					(Double)GridCellSupersamplingSettings.CORNER_DISTANCE_DEFAULT);
			cornerDistParam.getConstraint().setNullAllowed(true);
			cornerDistParam.setInfo("The site-to-grid-center distance below which a grid cell will represented by its "
					+ "4 corners. Set to 0 or black to disable corner supersampling at all distances.");
			cornerDistParam.setUnits("km");
			settingsList.addParameter(cornerDistParam);
			
			settingsListParam = new ParameterListParameter("Sampling Parameters", settingsList);
			settingsListParam.addParameterChangeListener(this);
			paramList.addParameter(settingsListParam);
			
			paramEdit = new ParameterListEditor(paramList);
			paramEdit.setName(getParameter().getName());
			
			return updateWidget();
		}

		@Override
		protected synchronized JComponent updateWidget() {
			GridCellSupersamplingSettings value = getValue();
			
			updating = true;
			enabledParam.setValue(value != null);
			
			if (value != null) {
				targetSpacingParam.setValue(value.targetSpacingKM);
				fullDistParam.setValue(getCutoffParamValue(value.fullDist));
				borderDistParam.setValue(getCutoffParamValue(value.borderDist));
				cornerDistParam.setValue(getCutoffParamValue(value.cornerDist));
				paramEdit.setParameterVisible(settingsListParam.getName(), true);
			} else {
				paramEdit.setParameterVisible(settingsListParam.getName(), false);
			}
			
			paramEdit.refreshParamEditor();
			updating = false;
			
			JComponent contents = paramEdit.getContents();
			contents.invalidate();
			contents.repaint();
			return contents;
		}
		
		private boolean updating = false;

		@Override
		public synchronized void parameterChange(ParameterChangeEvent event) {
			if (updating)
				// in the process of updating everything, don't set the value a bunch of times
				return;
			if (event.getParameter() == enabledParam)
				paramEdit.setParameterVisible(settingsListParam.getName(), enabledParam.getValue());
			GridCellSupersamplingSettings settings = buildCurrentValue();
			System.out.println("GridCellSupersamplingParam.Editor: param change, setting in parent: "+settings);
			getParameter().setValue(settings);
		}

		private GridCellSupersamplingSettings buildCurrentValue() {
			if (enabledParam.getValue()) {
				return new GridCellSupersamplingSettings(paramValueToCutoff(targetSpacingParam), paramValueToCutoff(fullDistParam),
						paramValueToCutoff(borderDistParam), paramValueToCutoff(cornerDistParam));
			}
			return null;
		}
		
	}
}
