package org.opensha.sha.imr.attenRelImpl.nshmp;

import java.util.EnumMap;
import java.util.EnumSet;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;

/**
 * This manages translations between nshmp-lib {@link Field} values and their OpenSHA counterparts.
 * 
 * The static {@link #nshmpToOpenSHA(Field, double)} and {@link #openshaToNSHMP(Field, Double)} methods translate
 * values directly, accounting for differences in units (e.g., for Z1.0) or using null vs NaN to express no value.
 * 
 * The class instance handles value changes and generation of {@link GmmInput} instances. {@link Field} values are set
 * using OpenSHA units, and will be translated to nshmp-lib units. If parameters are used, the corresponding parameter
 * for each field will also be updated when each {@link Field} value changes. See method notes for details on how
 * parameter change events are handled.
 * 
 */
class FieldParameterValueManager {
	
	/**
	 * Set of fields supported by this wrapper.
	 * 
	 * Currently all of them, but may be useful if/when new fields are added
	 */
	static final EnumSet<Field> SUPPORTED_FIELDS = EnumSet.of(
			Field.DIP, Field.MW, Field.RAKE, Field.RJB, Field.RRUP, Field.RX, Field.VS30, Field.WIDTH, Field.Z1P0,
			Field.Z2P5, Field.ZHYP, Field.ZSED, Field.ZTOR);
	
	static void ensureSupported(Field field) {
		Preconditions.checkState(SUPPORTED_FIELDS.contains(field),
				"Field is not supported by the OpenSHA<->nshmp-lib wrapper: %s", field);
	}
	
	/**
	 * Converts an nshmp-lib {@link Field} value to it's OpenSHA counterpart. For most fields this does nothing,
	 * but units are translated for Z1.0 and NaN values for Z1.0/Z2.5/ZSED are converted to null.
	 *  
	 * @param field
	 * @param nshmpVal
	 * @return
	 */
	static Double nshmpToOpenSHA(Field field, double nshmpVal) {
		ensureSupported(field);
		switch (field) {
		case Z1P0:
			if (Double.isNaN(nshmpVal))
				return null;
			// km -> m
			return nshmpVal * 1e3;
		case Z2P5:
			if (Double.isNaN(nshmpVal))
				return null;
			return nshmpVal;
		case ZSED:
			if (Double.isNaN(nshmpVal))
				return null;
			return nshmpVal;

		default:
			return nshmpVal;
		}
	}
	
	/**
	 * Inverse of {@link #nshmpToOpenSHA(Field, double)}
	 *  
	 * @param field
	 * @param nshmpVal
	 * @return
	 */
	static Double openshaToNSHMP(Field field, Double openshaVal) {
		ensureSupported(field);
		switch (field) {
		case Z1P0:
			if (openshaVal == null)
				return Double.NaN;
			// m -> km
			return openshaVal * 1e-3;
		case Z2P5:
			if (openshaVal == null)
				return Double.NaN;
			return openshaVal;
		case ZSED:
			if (openshaVal == null)
				return Double.NaN;
			return openshaVal;
		case WIDTH:
			if (openshaVal == 0d)
				// must be >0
				return 0.1;
			return openshaVal;

		default:
			return openshaVal;
		}
	}
	
	private ParameterChangeListener listener;
	private EnumMap<Field, Parameter<Double>> fieldParamMap;
	private EnumMap<Field, FieldParameterChangeListener> fieldParamListenerMap;
	private MutableGmmInputBuilder gmmInputBuilder;
	
	/**
	 * Instantiates a {@link FieldParameterValueManager}.
	 * 
	 * If parameter mappings are used, they can be added via the {@link #addParameterMapping(Field, Parameter)} method.
	 * If a {@link ParameterChangeListener} is also supplied, change events will be fired whenever values are updated
	 * externally (by setting the parameter value in an outside class). 
	 * 
	 * @param listener
	 */
	FieldParameterValueManager(ParameterChangeListener listener) {
		this.listener = listener;
		this.fieldParamMap = new EnumMap<>(Field.class);
		this.fieldParamListenerMap = new EnumMap<>(Field.class);
		this.gmmInputBuilder = new MutableGmmInputBuilder();
	}
	
	/**
	 * Adds a mapping between the given {@link Field} and a parameter. If the parameter is updated externally, that
	 * change will be registered and the corresponding {@link Field} value will be updated.
	 * 
	 * The wrapper should not add change listeners to individual parameters; change events will be sent by this value
	 * manager whenever a value is changed externally.
	 * 
	 * @param field
	 * @param param
	 */
	void addParameterMapping(Field field, Parameter<Double> param) {
		Preconditions.checkState(!fieldParamMap.containsKey(field));
		Preconditions.checkNotNull(param);
		fieldParamMap.put(field, param);
		fieldParamListenerMap.put(field, new FieldParameterChangeListener(field, param));
		gmmInputBuilder.setValue(field, openshaToNSHMP(field, param.getValue()));
	}
	
	/**
	 * This sets the value for the given {@link Field}, also updating any mapped parameters. No parameter change events
	 * will be fired for these internal updates.
	 * 
	 * @param field
	 * @param value
	 */
	void setParameterValue(Field field, Double value) {
		// set the value in the gmm input builder
		gmmInputBuilder.setValue(field, openshaToNSHMP(field, value));
		
		// see if we need to update any parameter
		Parameter<Double> param = fieldParamMap.get(field);
		if (param == null)
			return;
		FieldParameterChangeListener listener = fieldParamListenerMap.get(field);
		listener.valueChangeInternal = value;
		if (param instanceof WarningParameter<?>)
			((WarningParameter<Double>)param).setValueIgnoreWarning(value);
		else
			param.setValue(value);
	}
	
	/**
	 * This sets all values to those from the given GmmInput, updating any parameters as needed
	 * 
	 * @param externalInput
	 */
	void setGmmInput(GmmInput externalInput) {
		gmmInputBuilder.setAll(externalInput);
		
		// see if we need to update any parameters
		for (Field field : fieldParamMap.keySet()) {
			Double value = nshmpToOpenSHA(field, MutableGmmInputBuilder.valueForField(externalInput, field));
			Parameter<Double> param = fieldParamMap.get(field);
			FieldParameterChangeListener listener = fieldParamListenerMap.get(field);
			listener.valueChangeInternal = value;
			if (param instanceof WarningParameter<?>)
				((WarningParameter<Double>)param).setValueIgnoreWarning(value);
			else
				param.setValue(value);
		}
	}
	
	/**
	 * This builds a {@link GmmInput} for the current set of {@link Field} values. All Fields that have never been set,
	 * e.g. those that are unused by a model, will be set to NaN.
	 * 
	 * @return
	 */
	GmmInput getGmmInput() {
		return gmmInputBuilder.build();
	}
	
	private class FieldParameterChangeListener implements ParameterChangeListener {
		private Field field;
		private Parameter<Double> param;
		
		private Double valueChangeInternal;

		FieldParameterChangeListener(Field field, Parameter<Double> param) {
			this.field = field;
			this.param = param;
		}

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			Object newVal = event.getNewValue();
			
			if (valueChangeInternal != null && valueChangeInternal.equals(newVal)) {
				// shortcut: this is the value that we already set above, don't need to fire an external event
				// nor update the gmm input builder
				valueChangeInternal = null;
				
			} else {
				// this is an external parameter change
				// clear any internal value
				valueChangeInternal = null;
				// update the gmm input builder
				gmmInputBuilder.setValue(field, openshaToNSHMP(field, param.getValue()));
				// fire external event
				if (listener != null)
					listener.parameterChange(event);
			}
		}
	}

}
