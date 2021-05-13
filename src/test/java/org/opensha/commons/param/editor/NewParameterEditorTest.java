package org.opensha.commons.param.editor;

import static org.junit.Assert.*;

import javax.swing.JComponent;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.param.impl.StringParameter;

public class NewParameterEditorTest {

	@Before
	public void setUp() throws Exception {
		
	}
	
	@Test
	public void testNullConstructor() {
		MockStringParameterEditor editor = new MockStringParameterEditor();
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void testParamSupportedIsChecked() {
		MockStringParameterEditor editor = new MockStringParameterEditor();
		assertTrue("string params should be supported at first", editor.isParameterSupported(new StringParameter("")));
		editor.setParamSupportedReturn(false);
		assertFalse("string params should not supported in mock at this point",
				editor.isParameterSupported(new StringParameter("")));
		
		editor.setParameter(new StringParameter(""));
	}
	
	@Test
	public void testRebuildLogic() {
		MockStringParameterEditor editor = new MockStringParameterEditor();
		
		assertEquals("buildWiget should have been called once on create", 1, editor.buildWidgetStack.size());
		editor.buildWidgetStack.pop();
		
		assertEquals("updateWidget should not have been called on create", 0, editor.updateWidgetStack.size());
		
		editor.setRebuildOnUpdate(false);
		
		JComponent prevWidget = editor.getWidget();
		editor.refreshParamEditor();
		
		assertEquals("updateWidget should have been called once on refreshParamEditor", 1, editor.updateWidgetStack.size());
		editor.updateWidgetStack.pop();
		assertEquals("buildWiget should not have been called on refreshParamEditor", 0, editor.buildWidgetStack.size());
		assertTrue("widget should have stayed the same on update", prevWidget == editor.getWidget());
		
		editor.setReturnNullOnUpdate(true);
		
		prevWidget = editor.getWidget();
		editor.refreshParamEditor();
		assertEquals("updateWidget should have been called once on refreshParamEditor", 1, editor.updateWidgetStack.size());
		editor.updateWidgetStack.pop();
		assertEquals("buildWiget should have been called once on refreshParamEditor" +
				"when updateWidget returns null", 1, editor.buildWidgetStack.size());
		editor.buildWidgetStack.pop();
		assertFalse("widget should be different now", prevWidget == editor.getWidget());
		
		editor.setReturnNullOnUpdate(false);
		editor.setRebuildOnUpdate(true);
		
		prevWidget = editor.getWidget();
		editor.refreshParamEditor();
		assertEquals("updateWidget should have been called once on refreshParamEditor", 1, editor.updateWidgetStack.size());
		editor.updateWidgetStack.pop();
		assertEquals("buildWiget should not have been called on refreshParamEditor" +
				"when updateWidget returns new widget", 0, editor.buildWidgetStack.size());
		assertFalse("widget should be different now", prevWidget == editor.getWidget());
	}
	
	@Test
	public void testSetValue() {
		StringParameter sParam = new StringParameter("Asdf");
		sParam.setValue("1");
		MockStringParameterEditor editor = new MockStringParameterEditor(sParam);
		
		assertEquals("get value doens't match", "1", editor.getValue());
		
		editor.setReturnNullOnUpdate(false);
		editor.setRebuildOnUpdate(false);
		
		editor.setValue("2");
		assertEquals("set value didn't work match", "2", editor.getValue());
		assertEquals("updateWidget should have been called once on setValue", 1, editor.updateWidgetStack.size());
		editor.updateWidgetStack.pop();
	}

}
