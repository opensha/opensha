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
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.Parameterized;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.EnumParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;

public class ParameterizedEnumParameter<E extends Enum<E>, T extends Parameterized> extends AbstractParameter<T> {
	
	private static final boolean D = true;

	private EnumParameter<E> enumParam;
	private Function<E, T> instanceBuilder;
	private List<T> instances;
	private Editor editor;
	
	// listeners
	private EnumParamListener enumParamListener;
	private InstanceParamListener instanceParamListener;
	
	private boolean propagateInstanceParamChangeEvents = true;

	public ParameterizedEnumParameter(String name, EnumSet<E> choices, E defaultValue, String nullOption,
			Function<E, T> instanceBuilder) {
		super(name, null, null, null);
		this.instanceBuilder = instanceBuilder;
		enumParam = new EnumParameter<E>(name, choices, defaultValue, nullOption);
		this.instances = new ArrayList<>(enumParam.getEnumClass().getEnumConstants().length);
		// propagate parameter change events
		instanceParamListener = new InstanceParamListener();
		enumParamListener = new EnumParamListener();
		enumParam.addParameterChangeListener(enumParamListener);
		enumParam.addParameterChangeFailListener(enumParamListener);
		
		setValue(getCurrentInstance());
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
	public T getValue() {
		return super.getValue();
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
		enumParam.setValue(enumValue);
	}
	
	/**
	 * Clears all instances and sets the parameter value to null
	 */
	public void clearInstances() {
		instances.clear();
		setValue(null);
	}
	
	private class EnumParamListener implements ParameterChangeListener, ParameterChangeFailListener {

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			// this means the enum changed, update our instance
			setValue(getCurrentInstance());
			// refresh the editor (if built)
			refreshEditor();
		}

		@Override
		public void parameterChangeFailed(ParameterChangeFailEvent event) {
			// should never happen
			throw new IllegalStateException("Underlying EnumParameter change failed event? Bad value: "+event.getBadValue());
		}
		
	}
	
	private class InstanceParamListener implements ParameterChangeListener, ParameterChangeFailListener, ChangeListener {

		@Override
		public void stateChanged(ChangeEvent e) {
			// called when the contents of an instance parameter list change (not the selected instance itself)
			
			// refresh the editor (if built)
			refreshEditor();
			
			if (propagateInstanceParamChangeEvents)
				// also propagate that event
				firePropertyChange(new ParameterChangeEvent(ParameterizedEnumParameter.this, name, getValue(), getValue()));
		}

		@Override
		public void parameterChange(ParameterChangeEvent event) {
			if (propagateInstanceParamChangeEvents)
				firePropertyChange(new ParameterChangeEvent.Propagated(event, ParameterizedEnumParameter.this, name, getValue(), getValue()));
				firePropertyChange(event);
		}

		@Override
		public void parameterChangeFailed(ParameterChangeFailEvent event) {
			if (propagateInstanceParamChangeEvents)
				firePropertyChangeFailed(new ParameterChangeFailEvent.Propagated(event, ParameterizedEnumParameter.this, name, getValue(), getValue()));
		}
		
	}
	
	@Override
	public ParameterEditor getEditor() {
		if (editor == null)
			editor = new Editor();
		return editor;
	}

	@Override
	public boolean isEditorBuilt() {
		return editor != null;
	}

	@Override
	public Object clone() {
		ParameterizedEnumParameter<E, T> copy = new ParameterizedEnumParameter<>(name,
				EnumSet.copyOf(enumParam.getConstraint().getAllowedValues()),
				enumParam.getValue(), enumParam.getNullOption(), instanceBuilder);
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
		
		private Editor() {
			super(ParameterizedEnumParameter.this);
		}

		@Override
		public boolean isParameterSupported(Parameter<T> param) {
			return param == ParameterizedEnumParameter.this;
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
			
			return updateWidget();
		}

		@Override
		protected JComponent updateWidget() {
			if (D) System.out.println(ParameterizedEnumParameter.this.getName()+": updateWidget()");
			widget.removeAll();

			widget.add(enumEditor.getComponent(), BorderLayout.NORTH);
			
			Parameterized value = getValue();
			if (value == null)
				return widget;
			
			params = value.getAdjustableParameters();
			if (params != null && !params.isEmpty()) {
				paramsEdit = new ParameterListEditor(params);
				paramsEdit.setTitle(null);
				widget.add(paramsEdit, BorderLayout.CENTER);
				if (D) System.out.println(ParameterizedEnumParameter.this.getName()
						+": updateWidget() with "+params.size()+" parameters");
			}
			
			return widget;
		}
		
	}

}
