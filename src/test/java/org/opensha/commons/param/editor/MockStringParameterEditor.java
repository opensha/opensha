package org.opensha.commons.param.editor;

import java.util.Stack;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.opensha.commons.param.Parameter;

public class MockStringParameterEditor extends AbstractParameterEditor<String> {
	
	private JPanel widget;
	
	private boolean rebuildOnUpdate = false;
	private boolean returnNullOnUpdate = false;
	
	private Boolean paramSupportedReturn;

	public MockStringParameterEditor() {
		this(null);
	}

	public MockStringParameterEditor(Parameter<String> param) {
		super(param);
		
		updateWidgetStack = new Stack<Long>();
	}
	
	protected void setParamSupportedReturn(boolean paramSupportedReturn) {
		this.paramSupportedReturn = paramSupportedReturn;
	}

	@Override
	public boolean isParameterSupported(Parameter<String> param) {
		if (paramSupportedReturn == null)
			paramSupportedReturn = true;
		return paramSupportedReturn;
	}

	@Override
	public void setEnabled(boolean enabled) {
	}
	
	protected Stack<Long> buildWidgetStack;

	@Override
	protected JComponent buildWidget() {
		widget = new JPanel();
		if (buildWidgetStack == null)
			buildWidgetStack = new Stack<Long>();
		buildWidgetStack.push(System.currentTimeMillis());
		return widget;
	}
	
	protected Stack<Long> updateWidgetStack;

	@Override
	protected JComponent updateWidget() {
		if (updateWidgetStack == null)
			updateWidgetStack = new Stack<Long>();
		updateWidgetStack.push(System.currentTimeMillis());
		
		if (returnNullOnUpdate)
			return null;
		if (rebuildOnUpdate)
			widget = new JPanel();
		return widget;
	}
	
	protected void setRebuildOnUpdate(boolean rebuildOnUpdate) {
		this.rebuildOnUpdate = rebuildOnUpdate;
	}
	
	protected void setReturnNullOnUpdate(boolean returnNullOnUpdate) {
		this.returnNullOnUpdate = returnNullOnUpdate;
	}

}
