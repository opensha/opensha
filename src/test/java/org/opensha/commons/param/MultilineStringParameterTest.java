package org.opensha.commons.param;

import static org.junit.Assert.*;

import java.util.ArrayList;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Test;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.MultilineStringParameterEditor;
import org.opensha.commons.param.impl.MultilineStringParameter;
import org.opensha.commons.util.XMLUtils;

public class MultilineStringParameterTest {

	@Test
	public void testEditorType() {
		MultilineStringParameter param = new MultilineStringParameter("Test");
		ParameterEditor<String> editor = param.getEditor();
		assertTrue(editor instanceof MultilineStringParameterEditor);
		assertTrue(param.isEditorBuilt());
	}

	@Test
	public void testClonePreservesSettings() {
		MultilineStringParameter param = new MultilineStringParameter("Test", "units", "line 1\nline 2");
		param.setInfo("info");
		param.setTextEditable(false);
		param.setScrollPaneEnabled(false);
		param.setLineWrap(false);
		param.setWrapStyleWord(false);
		param.setRows(8);

		MultilineStringParameter clone = (MultilineStringParameter)param.clone();
		assertEquals(param.getName(), clone.getName());
		assertEquals(param.getUnits(), clone.getUnits());
		assertEquals(param.getInfo(), clone.getInfo());
		assertEquals(param.getValue(), clone.getValue());
		assertEquals(param.isTextEditable(), clone.isTextEditable());
		assertEquals(param.isScrollPaneEnabled(), clone.isScrollPaneEnabled());
		assertEquals(param.isLineWrap(), clone.isLineWrap());
		assertEquals(param.isWrapStyleWord(), clone.isWrapStyleWord());
		assertEquals(param.isIncludedInMetadata(), clone.isIncludedInMetadata());
		assertEquals(param.getRows(), clone.getRows());
	}

	@Test
	public void testClonePreservesConstraint() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("allowed");
		strings.add("also allowed");
		MultilineStringParameter param =
				new MultilineStringParameter("Test", new StringConstraint(strings), "allowed");

		MultilineStringParameter clone = (MultilineStringParameter)param.clone();
		assertNotSame(param.getConstraint(), clone.getConstraint());
		assertTrue(clone.isAllowed("also allowed"));
		assertFalse(clone.isAllowed("not allowed"));
	}

	@Test
	public void testXMLPreservesMultilineValue() {
		String value = "line 1\nline 2\nline 3";
		MultilineStringParameter param1 = new MultilineStringParameter("param1", value);
		MultilineStringParameter param2 = new MultilineStringParameter("param2");

		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();

		param1.toXMLMetadata(root);
		assertTrue(param2.setValueFromXMLMetadata(root.element(AbstractParameter.XML_METADATA_NAME)));
		assertEquals(value, param2.getValue());
	}

	@Test
	public void testIncludedInMetadata() {
		MultilineStringParameter param = new MultilineStringParameter("Test", "metadata");
		assertTrue(param.isIncludedInMetadata());
		assertEquals("Test = metadata", param.getMetadataString());

		param.setIncludedInMetadata(false);
		assertFalse(param.isIncludedInMetadata());
		assertNull(param.getMetadataString());
	}

	@Test
	public void testExcludedMetadataSkippedInParameterList() {
		MultilineStringParameter skipped = new MultilineStringParameter("Skipped", "metadata");
		skipped.setIncludedInMetadata(false);
		MultilineStringParameter included = new MultilineStringParameter("Included", "value");

		ParameterList params = new ParameterList();
		params.addParameter(skipped);
		params.addParameter(included);

		assertEquals("Included = value", params.getParameterListMetadataString());
	}

	@Test
	public void testScrollPaneWidget() {
		MultilineStringParameter param = new MultilineStringParameter("Test");
		param.setScrollPaneEnabled(true);

		MultilineStringParameterEditor editor = (MultilineStringParameterEditor)param.getEditor();
		assertTrue(editor.getWidget() instanceof JScrollPane);
		assertNotNull(editor.getTextArea());
	}

	@Test
	public void testScrollPaneRefreshScrollsToTop() throws Exception {
		MultilineStringParameter param = new MultilineStringParameter("Test", "first\nsecond\nthird\nfourth\nfifth");
		param.setScrollPaneEnabled(true);

		MultilineStringParameterEditor editor = (MultilineStringParameterEditor)param.getEditor();
		JTextArea textArea = editor.getTextArea();
		textArea.setCaretPosition(textArea.getText().length());

		param.refreshEditor();
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {}
		});

		assertEquals(0, textArea.getCaretPosition());
	}

	@Test
	public void testDirectTextAreaWidget() {
		MultilineStringParameter param = new MultilineStringParameter("Test");
		param.setScrollPaneEnabled(false);

		MultilineStringParameterEditor editor = (MultilineStringParameterEditor)param.getEditor();
		assertTrue(editor.getWidget() instanceof JTextArea);
		assertSame(editor.getWidget(), editor.getTextArea());
	}

	@Test
	public void testScrollPaneSettingRebuildsWidget() {
		MultilineStringParameter param = new MultilineStringParameter("Test");
		param.setScrollPaneEnabled(false);

		MultilineStringParameterEditor editor = (MultilineStringParameterEditor)param.getEditor();
		assertTrue(editor.getWidget() instanceof JTextArea);

		param.setScrollPaneEnabled(true);
		assertTrue(editor.getWidget() instanceof JScrollPane);
		assertNotNull(editor.getTextArea());
	}

	@Test
	public void testReadOnlyTextRemainsEnabled() {
		MultilineStringParameter param = new MultilineStringParameter("Test", "metadata");
		param.setTextEditable(false);

		MultilineStringParameterEditor editor = (MultilineStringParameterEditor)param.getEditor();
		JTextArea textArea = editor.getTextArea();
		assertFalse(textArea.isEditable());
		assertTrue(textArea.isEnabled());
	}

	@Test
	public void testRowsMustBePositive() {
		MultilineStringParameter param = new MultilineStringParameter("Test");
		try {
			param.setRows(0);
			fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {}
	}
}
