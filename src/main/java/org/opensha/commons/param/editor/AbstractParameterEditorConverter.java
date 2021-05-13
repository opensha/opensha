package org.opensha.commons.param.editor;

import javax.swing.JComponent;
import javax.swing.border.Border;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;

/**
 * This class is used to quickly create an editor of one type which uses a parameter editor
 * of another type. For example, a LocationParameter might need to be edited with a
 * StringParameterEditor or a ParameterListEditor. This makes it really easy to wrap
 * parameter editors.
 * 
 * @author Kevin
 *
 * @param <E>
 * @param <F>
 */
public abstract class AbstractParameterEditorConverter<E, F> implements
		ParameterEditor<E>, ParameterChangeListener {
	
	private Parameter<E> nativeParam;
	private Parameter<F> convertedParam;
	private ParameterEditor<F> editor;
	
	public AbstractParameterEditorConverter() {
		this(null);
	}
	
	public AbstractParameterEditorConverter(Parameter<E> nativeParam) {
		setParameter(nativeParam);
	}
	
	/**
	 * This will be called to build a parameter of the editor type from the
	 * parameter type
	 * 
	 * @param myParam
	 * @return
	 */
	protected abstract Parameter<F> buildParameter(Parameter<E> myParam);
	
	/**
	 * Converts a native value to a converted value
	 * 
	 * @param value
	 * @return
	 */
	protected abstract F convertFromNative(E value);
	
	/**
	 * Converts a converted value back to a native value.
	 * 
	 * @param value
	 * @return
	 */
	protected abstract E convertToNative(F value);

	@Override
	public void setValue(E object) {
		nativeParam.setValue(object);
		editor.setValue(convertFromNative(object));
	}

	@Override
	public E getValue() {
		return nativeParam.getValue();
	}

	@Override
	public void unableToSetValue(Object object) {
		editor.unableToSetValue(object);
	}

	@Override
	public void refreshParamEditor() {
		editor.refreshParamEditor();
	}

	@Override
	public Parameter<E> getParameter() {
		return nativeParam;
	}

	@Override
	public void setParameter(Parameter<E> model) {
		if (model == null) {
			nativeParam = null;
			convertedParam = null;
			editor = null;
		} else {
			this.nativeParam = model;
			convertedParam = buildParameter(model);
			convertedParam.addParameterChangeListener(this);
			if (editor == null) {
				editor = convertedParam.getEditor();
			} else {
				editor.setParameter(convertedParam);
			}
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == convertedParam) {
			E nativeVal = convertToNative(convertedParam.getValue());
//			System.out.println("Passing new value on to native: "+nativeVal);
			nativeParam.setValue(nativeVal);
		}
	}

	@Override
	public void setFocusEnabled(boolean newFocusEnabled) {
		editor.setFocusEnabled(newFocusEnabled);
	}

	@Override
	public boolean isFocusEnabled() {
		return editor.isFocusEnabled();
	}

	@Override
	public void setEnabled(boolean isEnabled) {
		editor.setEnabled(isEnabled);
	}

	@Override
	public void setVisible(boolean isVisible) {
		editor.setVisible(isVisible);
	}

	@Override
	public boolean isVisible() {
		return editor.isVisible();
	}

	@Override
	public JComponent getComponent() {
		return editor.getComponent();
	}

	@Override
	public void setEditorBorder(Border b) {
		editor.setEditorBorder(b);
	}
	
	

}
