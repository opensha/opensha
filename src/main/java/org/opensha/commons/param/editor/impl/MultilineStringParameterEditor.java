package org.opensha.commons.param.editor.impl;

import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.MultilineStringParameter;

/**
 * Parameter editor that displays a {@link MultilineStringParameter} in a
 * {@link JTextArea}.
 */
public class MultilineStringParameterEditor extends AbstractParameterEditor<String>
implements FocusListener {

	private static final long serialVersionUID = 1L;

	private JTextArea widget;
	private JScrollPane scrollPane;
	private boolean currentScrollPaneEnabled;

	public MultilineStringParameterEditor(Parameter<String> model) throws Exception {
		super(model);
	}

	@Override
	public boolean isParameterSupported(Parameter<String> param) {
		return param instanceof MultilineStringParameter;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (widget != null)
			widget.setEnabled(enabled);
		if (scrollPane != null)
			scrollPane.setEnabled(enabled);
	}

	@Override
	public boolean isEnabled() {
		return widget != null && widget.isEnabled();
	}

	@Override
	protected JComponent buildWidget() {
		MultilineStringParameter param = getMultilineParameter();
		currentScrollPaneEnabled = param.isScrollPaneEnabled();

		widget = new JTextArea();
		widget.setBorder(ETCHED);
		widget.addFocusListener(this);
		applyParameterSettings();
		updateText();

		if (currentScrollPaneEnabled) {
			scrollPane = new JScrollPane(widget);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setPreferredSize(new Dimension(LABEL_DIM.width, widget.getPreferredSize().height + 6));
			scrollPane.setMinimumSize(new Dimension(LABEL_DIM.width, 0));
			return scrollPane;
		}

		scrollPane = null;
		widget.setPreferredSize(new Dimension(LABEL_DIM.width, widget.getPreferredSize().height));
		widget.setMinimumSize(new Dimension(LABEL_DIM.width, 0));
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		MultilineStringParameter param = getMultilineParameter();
		if (param.isScrollPaneEnabled() != currentScrollPaneEnabled)
			return null;
		applyParameterSettings();
		updateText();
		return currentScrollPaneEnabled ? scrollPane : widget;
	}

	private void applyParameterSettings() {
		MultilineStringParameter param = getMultilineParameter();
		widget.setRows(param.getRows());
		widget.setLineWrap(param.isLineWrap());
		widget.setWrapStyleWord(param.isWrapStyleWord());
		widget.setEditable(param.isTextEditable());
	}

	private void updateText() {
		String val = getValue();
		widget.setText(val == null || val.length() == 0 ? "" : val);
		if (currentScrollPaneEnabled)
			scrollToTop();
	}

	private void scrollToTop() {
		widget.setCaretPosition(0);
		if (scrollPane != null) {
			scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMinimum());
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					widget.setCaretPosition(0);
					scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMinimum());
				}
			});
		}
	}

	private MultilineStringParameter getMultilineParameter() {
		return (MultilineStringParameter)getParameter();
	}

	private void commitWidgetValue() {
		String value = widget.getText();
		try {
			setValue(value == null ? "" : value);
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		} catch (ConstraintException ee) {
			updateText();
			this.unableToSetValue(value);
		} catch (WarningException ee) {
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		}
	}

	@Override
	public void focusLost(FocusEvent e) {
		if (widget != null && widget.isEditable() && widget.isEnabled())
			commitWidgetValue();
	}

	@Override
	public void focusGained(FocusEvent e) {}

	/**
	 * @return the text area used by this editor, if already built
	 */
	public JTextArea getTextArea() {
		return widget;
	}
}
