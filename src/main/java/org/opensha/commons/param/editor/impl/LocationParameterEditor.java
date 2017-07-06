package org.opensha.commons.param.editor.impl;

import javax.swing.JComponent;

import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.AbstractParameterEditorConverter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.ParameterListParameter;

public class LocationParameterEditor extends AbstractParameterEditorConverter<Location, ParameterList> {
	
	private static String DEFAULT_LATITUDE_LABEL = "Latitude";
	private static String DEFAULT_LONGITUDE_LABEL = "Longitude";
	private static String DEFAULT_DEPTH_LABEL = "Depth";
	
	private final static String DECIMAL_DEGREES = "Decimal Degrees";
	private final static String KMS = "kms";
	
	private DoubleParameter latParam;
	private DoubleParameter lonParam;
	private DoubleParameter depthParam;
	
	private ParameterList list;
	
	ParameterListParameter plp;
	
	public LocationParameterEditor(Parameter<Location> param) {
		this(param, true);
	}
	
	public LocationParameterEditor(Parameter<Location> param, boolean showDepth) {
		super();
		
		Location loc = param.getValue();
		Double lat, lon, depth;
		if (loc == null) {
			lat = null;
			lon = null;
			depth = null;
		} else {
			lat = loc.getLatitude();
			lon = loc.getLongitude();
			depth = loc.getDepth();
		}
		
		latParam = new DoubleParameter(DEFAULT_LATITUDE_LABEL,
				new DoubleConstraint(GeoTools.LAT_MIN,GeoTools.LAT_MAX),
				DECIMAL_DEGREES, lat);
		latParam.getConstraint().setNullAllowed(true);
		lonParam = new DoubleParameter(DEFAULT_LONGITUDE_LABEL,
				new DoubleConstraint(GeoTools.LON_MIN,GeoTools.LON_MAX),
				DECIMAL_DEGREES, lon);
		lonParam.getConstraint().setNullAllowed(true);
		if (showDepth) {
			depthParam = new DoubleParameter(DEFAULT_DEPTH_LABEL,
					new DoubleConstraint(GeoTools.DEPTH_MIN,1000),
					KMS, depth);
			depthParam.getConstraint().setNullAllowed(true);
		}
		
		list = new ParameterList();
		
		list.addParameter(latParam);
		list.addParameter(lonParam);
		if (depthParam != null)
			list.addParameter(depthParam);
		
		plp = new ParameterListParameter(param.getName(), list);
		
		setParameter(param);
	}
	
	public void setShowDepth(boolean showDepth) {
		if (showDepth) {
			if (depthParam == null) {
				Double depth;
				if (getParameter().getValue() == null)
					depth = null;
				else
					depth = getParameter().getValue().getDepth();
				depthParam = new DoubleParameter(DEFAULT_DEPTH_LABEL,
						new DoubleConstraint(GeoTools.DEPTH_MIN,1000),
						KMS, depth);
				depthParam.getConstraint().setNullAllowed(true);
			}
			if (!list.containsParameter(depthParam)) {
				list.addParameter(depthParam);
				plp.getEditor().refreshParamEditor();
			}
		} else if (depthParam != null) {
			list.removeParameter(depthParam);
			plp.getEditor().refreshParamEditor();
		}
	}

	@Override
	protected Parameter<ParameterList> buildParameter(
			Parameter<Location> myParam) {
		updateLocParams(myParam.getValue());
		return plp;
	}
	
	private void updateLocParams(Location loc) {
		if (loc == null) {
			latParam.setValue(null);
			lonParam.setValue(null);
			if (depthParam != null)
				depthParam.setValue(null);
		} else {
			latParam.setValue(loc.getLatitude());
			lonParam.setValue(loc.getLongitude());
			if (depthParam != null)
				depthParam.setValue(loc.getDepth());
		}
		plp.getEditor().refreshParamEditor();
	}

	@Override
	protected ParameterList convertFromNative(Location value) {
		updateLocParams(value);
		return list;
	}

	@Override
	protected Location convertToNative(ParameterList value) {
		Double lat = latParam.getValue();
		Double lon = lonParam.getValue();
		Double depth = depthParam == null ? new Double(0d) : depthParam.getValue();
		if (lat == null || lon == null)
			return null;
		if (depth == null)
			depth = 0d;
		return new Location(lat, lon, depth);
	}

}
