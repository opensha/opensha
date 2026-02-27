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
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

import com.google.common.base.Preconditions;

public class GriddedSeismicitySettingsParam extends AbstractParameter<GriddedSeismicitySettings> implements ParameterChangeListener {
	
	public static final String NAME = "Gridded Seismicity Settings";
	private BackgroundRupParam surfTypeParam;
	
	private Editor editor;
	
	public GriddedSeismicitySettingsParam(GriddedSeismicitySettings value) {
		this(value, null);
	}
	
	/**
	 * Constructor for the case where you are controlling the surface type separately and shouldn't display it separately.
	 * 
	 * @param value
	 * @param surfTypeParam
	 * @throws IllegalStateException if the passed in surface type param is of different type than the initial value
	 */
	public GriddedSeismicitySettingsParam(GriddedSeismicitySettings value, BackgroundRupParam surfTypeParam) {
		super(NAME, null, null, value);
		Preconditions.checkNotNull(value, "Passed in settings value cannot be null");
		this.surfTypeParam = surfTypeParam;
		if (surfTypeParam != null && value != null) {
			Preconditions.checkState(surfTypeParam.getValue() == value.surfaceType,
					"Passed in surface type param (%s) does not match the settings value (%s)",
					surfTypeParam.getValue(), value.surfaceType);
		}
		if (surfTypeParam != null) {
			surfTypeParam.addParameterChangeListener(this);
			this.addParameterChangeListener(this); // for updates back to the surf type
		}
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
		return new GriddedSeismicitySettingsParam(value, surfTypeParam);
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		return false;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == surfTypeParam) {
			BackgroundRupType newSurfType = surfTypeParam.getValue();
			if (value.surfaceType != newSurfType)
				setValue(value.forSurfaceType(newSurfType));
		} else if (event.getParameter() == this) {
			GriddedSeismicitySettings value = getValue();
			if (value.surfaceType != surfTypeParam.getValue())
				surfTypeParam.setValue(value.surfaceType);
		}
	}
	
	private static class Editor extends AbstractParameterEditor<GriddedSeismicitySettings> implements ParameterChangeListener {
		
		private ParameterList paramList;
		private ParameterListEditor paramEdit;
		
		private BackgroundRupParam surfTypeParam;
		private DoubleParameter minMagParam;
		private DoubleParameter pointSourceCutoffMagParam;
		private PointSourceDistanceCorrectionParam distCorrParam;
		private GridCellSupersamplingParam supersamplingParam;
		
		public Editor(GriddedSeismicitySettingsParam param) {
			super(param);
		}

		@Override
		public boolean isParameterSupported(Parameter<GriddedSeismicitySettings> param) {
			return param != null && param.getValue() != null;
		}

		@Override
		public void setEnabled(boolean enabled) {
			if (paramEdit != null)
				paramEdit.setEnabled(enabled);
		}

		@Override
		protected JComponent buildWidget() {
			paramList = new ParameterList();
			
			Parameter<GriddedSeismicitySettings> param = getParameter();
			GriddedSeismicitySettings settings = param.getValue();
			
			if (param instanceof GriddedSeismicitySettingsParam && ((GriddedSeismicitySettingsParam)param).surfTypeParam != null) {
				// use the passed in surface type parameter, and don't add it to the displayed list
				surfTypeParam = ((GriddedSeismicitySettingsParam)param).surfTypeParam;
				Preconditions.checkState(surfTypeParam.getValue() == settings.surfaceType);
			} else {
				surfTypeParam = new BackgroundRupParam();
				paramList.addParameter(surfTypeParam);
			}
			surfTypeParam.addParameterChangeListener(this);
			
			minMagParam = new DoubleParameter("Minimum Gridded Magnitude", 0d, 10d);
			minMagParam.getConstraint().setNullAllowed(false);
			minMagParam.setInfo("Minimum magnitude for gridded seismicity; all ruptures below this magnitude will be "
					+ "skipped entirely.");
			minMagParam.addParameterChangeListener(this);
			paramList.addParameter(minMagParam);
			
			pointSourceCutoffMagParam = new DoubleParameter("Minimum Finite Magnitude", 0d, 10d);
			minMagParam.getConstraint().setNullAllowed(false);
			pointSourceCutoffMagParam.setInfo("Minimum magnitude for finite ruptures; all ruptures below this magnitude "
					+ "will be treated as point sources regardless of the surface type setting.");
			pointSourceCutoffMagParam.addParameterChangeListener(this);
			paramList.addParameter(pointSourceCutoffMagParam);
			
			distCorrParam = new PointSourceDistanceCorrectionParam();
			distCorrParam.addParameterChangeListener(this);
			paramList.addParameter(distCorrParam);
			
			supersamplingParam = new GridCellSupersamplingParam();
			supersamplingParam.addParameterChangeListener(this);
			paramList.addParameter(supersamplingParam);
			
			paramEdit = new ParameterListEditor(paramList);
			
			return updateWidget();
		}
		
		@Override
		protected synchronized JComponent updateWidget() {
			GriddedSeismicitySettings value = getValue();
			
			updating = true;
			surfTypeParam.setValue(value.surfaceType);
			minMagParam.setValue(value.minimumMagnitude);
			pointSourceCutoffMagParam.setValue(value.pointSourceMagnitudeCutoff);
			PointSourceDistanceCorrections corrType = PointSourceDistanceCorrections.forCorrections(value.distanceCorrections);
			Preconditions.checkNotNull(corrType, "Passed in corrections are not of a standard type; editor not supported.");
			distCorrParam.setValue(corrType);
			supersamplingParam.setValue(value.supersamplingSettings);
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
			Parameter<GriddedSeismicitySettings> param = getParameter();
//			System.out.println("GriddedSeismicitySettingsParam: param change, updating parent: "+source.getName()+" to "+source.getValue());
			GriddedSeismicitySettings value = getValue();
			if (source == surfTypeParam)
				param.setValue(value.forSurfaceType(surfTypeParam.getValue()));
			else if (source == minMagParam)
				param.setValue(value.forMinimumMagnitude(minMagParam.getValue()));
			else if (source == pointSourceCutoffMagParam)
				param.setValue(value.forPointSourceMagCutoff(pointSourceCutoffMagParam.getValue()));
			else if (source == distCorrParam)
				param.setValue(value.forDistanceCorrections(distCorrParam.getValue()));
			else if (source == supersamplingParam)
				param.setValue(value.forSupersamplingSettings(supersamplingParam.getValue()));
		}
		
	}

}
