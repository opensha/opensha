package org.opensha.commons.param.impl;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.ParameterizedModel;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.EnumParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;

/**
 * Parameter and nested editor for a special class of models where the model choice is specified by an Enum that
 * provides model instances, and those model instances may have adjustable parameters.
 * <p>
 * This was initially constructed for time-dependence where, e.g., you might want an enum of fixed aperiodicity models,
 * but also want the flexibility of a custom choice with adjustable parameters.
 * 
 * @param <E> Enum type for selecting models
 * @param <T> Underlying model type
 */
public class EnumParameterizedModelarameter<E extends Enum<E>, T extends ParameterizedModel> extends AbstractParameter<T> {
	
	private static final boolean D = true;
	private static final boolean EDITOR_D = false;

	private EnumParameter<E> enumParam;
	private Function<E, T> instanceBuilder;
	private List<T> instances;
	private Editor editor;
	
	// listeners
	private EnumParamListener enumParamListener;
	private InstanceParamListener instanceParamListener;
	
	private boolean propagateInstanceParamChangeEvents = true;
	private boolean allowCustomValues;
	
	private static final String CUSTOM_VALUE_LABEL_DEFAULT = "External Custom Value";
	
	public EnumParameterizedModelarameter(String name, EnumSet<E> choices, E defaultValue, boolean allowCustomValues,
			Function<E, T> instanceBuilder) {
		this(name, choices, defaultValue, allowCustomValues, CUSTOM_VALUE_LABEL_DEFAULT, instanceBuilder);
	}

	/**
	 * Constructor specifying the enum and it's choices and a function to instantiate models for enum choices.
	 * <p>
	 * If allowCustomValues is true, arbitrary external {@link #setValue(Object)} calls are allowed and will cause the
	 * enum parameter to disappear from the editor (until {@link #setEnumValue(Enum)} is called).
	 * 
	 * @param name parameter name
	 * @param choices allowed enum choices
	 * @param defaultValue default (initial) enum value
	 * @param nullOption string for null enum selection
	 * @param allowCustomValues if custom externally-set values should be allowed
	 * @param instanceBuilder function to instantiate a model for a given enum value
	 */
	public EnumParameterizedModelarameter(String name, EnumSet<E> choices, E defaultValue,
			boolean allowCustomValues, String customValueLabel, Function<E, T> instanceBuilder) {
		super(name, null, null, null);
		this.setConstraint(new CustomValueCheckConstraint());
		this.allowCustomValues = allowCustomValues;
		this.instanceBuilder = instanceBuilder;
		enumParam = new EnumParameter<E>(name, choices, defaultValue, allowCustomValues ? customValueLabel : null);
		this.instances = new ArrayList<>(enumParam.getEnumClass().getEnumConstants().length);
		// propagate parameter change events
		instanceParamListener = new InstanceParamListener();
		enumParamListener = new EnumParamListener();
		enumParam.addParameterChangeListener(enumParamListener);
		enumParam.addParameterChangeFailListener(enumParamListener);
		
		setValueInternal(getCurrentInstance());
	}
	
	private boolean isCustomValue() {
		if (value == null)
			return false;
		for (T existingValue : instances)
			if (value == existingValue)
				return false;
		return true;
	}
	
	private class CustomValueCheckConstraint extends AbstractParameterConstraint<T> {
		
		public CustomValueCheckConstraint() {
			setNullAllowed(true);
		}

		@Override
		public boolean isAllowed(T value) {
			if (value == null || allowCustomValues)
				return true;
			return !isCustomValue();
		}

		@Override
		public Object clone() {
			return new CustomValueCheckConstraint();
		}
		
	}
	
	/**
	 * @param propagateInstanceParamChangeEvents if true, any change to the value of any of the parameters that belong
	 * to individual instances will also trigger {@link ParameterChangeEvent} or {@link ParameterChangeFailEvent}s.
	 */
	public void setPropagateInstanceParamChangeEvents(boolean propagateInstanceParamChangeEvents) {
		this.propagateInstanceParamChangeEvents = propagateInstanceParamChangeEvents;
	}
	
	private T getCurrentInstance() {
		E enumValue = enumParam.getValue();
		if (enumValue == null)
			return null;
		int ordinal = enumValue.ordinal();
		while (instances.size() <= ordinal)
			instances.add(null);
		if (instances.get(ordinal) == null) {
			synchronized (instances) {
				if (instances.get(ordinal) == null) {
					T instance  = instanceBuilder.apply(enumValue);
					if (instance != null) {
						ParameterList instanceParams = instance.getAdjustableParameters();
						if (instanceParams != null) {
							instanceParams.addChangeListener(instanceParamListener); // this will detect and skip duplicate adds
							for (Parameter<?> param : instanceParams)
								param.addParameterChangeListener(instanceParamListener);
						}
						instances.set(ordinal, instance);
					}
				}
			}
		}
		T ret = instances.get(ordinal);
		if (D) System.out.println("getCurrentInstance["+enumValue+"]: "+ret);
		return ret;
	}

	@Override
	public void setValue(T value) throws ConstraintException, ParameterException {
		super.setValue(value);
		if (isCustomValue()) {
			enumParam.setValue(null);
		}
	}
	
	/**
	 * Internally set a value that we know isn't a custom value, skip that check
	 * 
	 * @param value
	 * @throws ConstraintException
	 * @throws ParameterException
	 */
	private void setValueInternal(T value) throws ConstraintException, ParameterException {
		super.setValue(value);
	}

	/**
	 * This can be used to retrieve a view of the instance list, which, for each enum value indexed by
	 * {@link Enum#ordinal()}, will contain the corresponding concrete instance, or null if not yet chosen.
	 * 
	 * @return unmodifiable view of the instance list
	 */
	public List<T> getInstanceList() {
		return Collections.unmodifiableList(instances);
	}
	
	public E getEnumValue() {
		return enumParam.getValue();
	}
	
	public void setEnumValue(E enumValue) {
		// this will trigger a setValue(getCurrentInstance()) via EnumParamListener 
		enumParam.setValue(enumValue);
	}
	
	/**
	 * Clears all instances and resets the parameter value from the currently selected enum
	 */
	public void clearInstances() {
		instances.clear();
		setValueInternal(getCurrentInstance());
		// refresh the editor (if built)
		refreshEditor();
	}
	
	private class EnumParamListener implements ParameterChangeListener, ParameterChangeFailListener {

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			if (D) System.out.println(getName()+": underlying enum changed to: "+event.getNewValue());
			if (enumParam.getValue() == null && isCustomValue()) {
				// this was just clearing the enum param, no need to fire a value update
				if (D) System.out.println(getName()+": custom value already set, won't replace: "+getValue());
			} else {
				// this means the enum changed, update our instance
				setValueInternal(getCurrentInstance());
				if (D) System.out.println(getName()+": value now contains: "+getValue());
			}
			// refresh the editor (if built)
			refreshEditor();
		}

		@Override
		public void parameterChangeFailed(ParameterChangeFailEvent event) {
			// can happen if the setEnumValue is called with a non-allowed choice
			firePropertyChangeFailed(new ParameterChangeFailEvent.Propagated(event, EnumParameterizedModelarameter.this, name, getValue(), getValue()));
		}
		
	}
	
	private class InstanceParamListener implements ParameterChangeListener, ParameterChangeFailListener, ChangeListener {

		@Override
		public void stateChanged(ChangeEvent e) {
			// called when the contents of an instance parameter list change (not the selected instance itself)
			if (D) System.out.println(getName()+": underlying instance parameter list changed, current value: "+getValue());
			
			// refresh the editor (if built)
			refreshEditor();
			
			if (propagateInstanceParamChangeEvents)
				// also propagate that event
				firePropertyChange(new ParameterChangeEvent(EnumParameterizedModelarameter.this, name, getValue(), getValue()));
		}

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			// called when an instance parameter changed (one of the values in the list
			if (D) System.out.println(getName()+": underlying instance parameter changed: "+event.getParameterName()+": "+event.getNewValue());
			if (propagateInstanceParamChangeEvents)
				firePropertyChange(new ParameterChangeEvent.Propagated(event, EnumParameterizedModelarameter.this, name, getValue(), getValue()));
		}

		@Override
		public void parameterChangeFailed(ParameterChangeFailEvent event) {
			if (D) System.out.println(getName()+": underlying instance parameter change failed: "+event.getParameterName()+": "+event.getBadValue());
			if (propagateInstanceParamChangeEvents)
				firePropertyChangeFailed(new ParameterChangeFailEvent.Propagated(event, EnumParameterizedModelarameter.this, name, getValue(), getValue()));
		}
		
	}
	
	@Override
	public ParameterEditor getEditor() {
		if (editor == null) {
			if (EDITOR_D) System.out.println(getName()+": getEditor(): instantiating new");
			editor = new Editor();
		}
		if (EDITOR_D) System.out.println(getName()+": getEditor()");
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		boolean built = editor != null;
		if (EDITOR_D) System.out.println(getName()+": isEditorBuilt() ? "+built);
		return built;
	}

	@Override
	public Object clone() {
		EnumParameterizedModelarameter<E, T> copy = new EnumParameterizedModelarameter<>(name,
				EnumSet.copyOf(enumParam.getConstraint().getAllowedValues()),
				enumParam.getValue(), allowCustomValues, enumParam.getNullOption(), instanceBuilder);
		// copy parameter values
		T origInstance = getCurrentInstance();
		if (origInstance != null) {
			ParameterList origParams = origInstance.getAdjustableParameters();
			if (origParams != null && !origParams.isEmpty()) {
				T newInstance = copy.getCurrentInstance();
				ParameterList newParams = newInstance.getAdjustableParameters();
				for (Parameter<?> param : origParams) {
					newParams.getParameter(param.getName()).setValue(param.getValue());
				}
			}
		}
		
		return copy;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		return false;
	}
	
	public class Editor extends AbstractParameterEditor<T> {
		
		private JPanel widget;
		
		private EnumParameterEditor<E> enumEditor;
		private ParameterList params;
		private ParameterListEditor paramsEdit;
		private JComponent customValueComponent;
		
		private Editor() {
			super(EnumParameterizedModelarameter.this);
			setDebug(EnumParameterizedModelarameter.EDITOR_D);
		}

		@Override
		public boolean isParameterSupported(Parameter<T> param) {
			return param == EnumParameterizedModelarameter.this;
		}

		@Override
		public void setEnabled(boolean enabled) {
			enumEditor.setEnabled(enabled);
			if (paramsEdit != null)
				paramsEdit.setEnabled(enabled);
		}

		@Override
		protected JComponent buildWidget() {
			widget = new JPanel(new BorderLayout());
			enumEditor = new EnumParameterEditor<>(enumParam);
			enumEditor.setIncludeBorder(false);
			enumEditor.setShowNullOptionIfNonNull(false);
			
			return updateWidget();
		}

		@Override
		protected JComponent updateWidget() {
			if (debug) System.out.println(EnumParameterizedModelarameter.this.getName()+": updateWidget()");
			widget.removeAll();

			ParameterizedModel value = getValue();
			if (allowCustomValues && enumEditor.getValue() == null) {
				if (value == null)
					enumEditor.setNullOption(enumParam.getNullOption());
				else
					enumEditor.setNullOption(enumParam.getNullOption()+": "+value.getName());
			}
			enumEditor.refreshParamEditor();
			widget.add(enumEditor.getComponent(), BorderLayout.NORTH);
			
			if (value == null)
				return widget;
			
			params = value.getAdjustableParameters();
			if (params != null && !params.isEmpty()) {
				paramsEdit = new ParameterListEditor(params);
				paramsEdit.setTitle(null);
				widget.add(paramsEdit, BorderLayout.CENTER);
				if (debug) System.out.println(EnumParameterizedModelarameter.this.getName()
						+": updateWidget() with "+params.size()+" parameters");
			} else if (debug) {
				System.out.println(EnumParameterizedModelarameter.this.getName()
						+": updateWidget() with no params (null ? "+params == null+")");
			}
			
			return widget;
		}
		
	}

}
