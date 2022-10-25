package org.opensha.sha.gui.beans;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Iterator;

import javax.swing.JEditorPane;
import javax.swing.JPanel;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;

/**
 * Time span gui.
 * 
 * @version $Id: TimeSpanGuiBean.java 8331 2011-11-15 00:52:54Z kmilner $
 */

public class TimeSpanGuiBean extends JPanel {

	public final static String TIMESPAN_EDITOR_TITLE = "Set Time Span";
	private TimeSpan timeSpan;

	private ParameterListEditor editor;
	private ParameterList parameterList;
	private JEditorPane timespanEditor = new JEditorPane();
	private GridBagLayout gridBagLayout1 = new GridBagLayout();

	/**
	 * default constructor
	 */
	public TimeSpanGuiBean() {
		parameterList = new ParameterList();
		try {
			jbInit();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Constructor : It accepts the TimeSpan object. This is timeSpan reference
	 * as exists in the ERF.
	 * 
	 * @param timeSpan
	 */
	public TimeSpanGuiBean(TimeSpan timeSpan) {
		this();
		setTimeSpan(timeSpan);
	}

	/**
	 * It accepts the timespan object and shows it based on adjustable params of
	 * this new object
	 * 
	 * @param timeSpan
	 */
	public void setTimeSpan(TimeSpan timeSpan) {
		this.parameterList.clear();
		this.timeSpan = timeSpan;
		if (editor != null)
			this.remove(editor);
		if (timeSpan != null) {
			// get the adjustable params and add them to the list
			for (Parameter<?> param : timeSpan.getAdjustableParams()) {
				this.parameterList.addParameter(param);
			}
			this.remove(timespanEditor);
			editor = new ParameterListEditor(parameterList);
			editor.setTitle(TIMESPAN_EDITOR_TITLE);
			this.add(editor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
					GridBagConstraints.CENTER, GridBagConstraints.BOTH,
					new Insets(0, 0, 0, 0), 0, 0));
			this.validate();
			this.repaint();
		} else {
			this.add(timespanEditor, new GridBagConstraints(0, 0, 1, 1,
					1.0, 1.0, GridBagConstraints.CENTER,
					GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

		}
	}

	/**
	 * Return the timeSpan that is shown in gui Bean
	 * 
	 * @return
	 */
	public TimeSpan getTimeSpan() {
		return this.timeSpan;
	}

	private void jbInit() throws Exception {
		String text = "This ERF does not have any Timespan\n";
		this.setLayout(gridBagLayout1);

		timespanEditor.setEditable(false);
		timespanEditor.setText(text);
		//this.setMinimumSize(new Dimension(0, 0));
		this.add(timespanEditor, new GridBagConstraints(0, 0, 1, 1, 1.0,
				1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(4, 4, 4, 4), 0, 0));
	}

	/**
	 * 
	 * @return the ParameterList
	 */
	public ParameterList getParameterList() {
		return this.parameterList;
	}

	/**
	 * 
	 * @return the ParameterListEditor
	 */
	public ParameterListEditor getParameterListEditor() {
		return this.editor;
	}

	/**
	 * 
	 * @return the Visible parameters metadata
	 */
	public String getParameterListMetadataString() {
		if (timeSpan != null) {
			return editor.getVisibleParametersCloned().
				getParameterListMetadataString();
		} else {
			return "No Timespan";
		}
	}

}
