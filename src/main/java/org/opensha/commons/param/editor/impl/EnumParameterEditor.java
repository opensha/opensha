package org.opensha.commons.param.editor.impl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.EnumSet;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.EnumConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.EnumParameter;
//import org.opensha.sha.earthquake.rupForecastImpl.nshmp.util.FaultType;

/**
 * The editor used to render <code>EnumParameter</code>s.
 * 
 * @author Peter Powers
 * @version $Id$
 */
public class EnumParameterEditor<E extends Enum<E>> extends
		AbstractParameterEditor<E> implements ItemListener {

	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "EnumParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	private JComboBox widget;
	private Class<E> clazz;

	/**
	 * Constructs a new editor for the supplied <code>Parameter</code>.
	 * @param model for editor
	 * @param clazz the class of the supplied <code>Parameter</code>
	 */
	public EnumParameterEditor(EnumParameter<E> model, Class<E> clazz) {
		super(model);
		this.clazz = clazz;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object obj = widget.getSelectedItem();
		setValue(obj instanceof String ? null : clazz.cast(obj));
	}

	@Override
	public boolean isParameterSupported(Parameter<E> param) {
		if (param == null) return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		widget.setEnabled(enabled);
//		widget.repaint();
	}

	@Override
	protected JComponent buildWidget() {
		widget = new JComboBox(new DefaultComboBoxModel(buildModel()));
		widget.setModel(new DefaultComboBoxModel(buildModel()));
		widget.setMaximumRowCount(40);
		String nullOption = ((EnumParameter<E>) getParameter()).getNullOption();
		E value = getParameter().getValue();
		widget.setSelectedItem(value == null ? nullOption : value);
		widget.addItemListener(this);
		widget.setPreferredSize(WIGET_PANEL_DIM);
		widget.setMinimumSize(WIGET_PANEL_DIM);
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		widget.removeItemListener(this);
		widget.setModel(new DefaultComboBoxModel(buildModel()));
		String nullOption = ((EnumParameter<E>) getParameter()).getNullOption();
		E value = getParameter().getValue();
		widget.setSelectedItem(value == null ? nullOption : value);
		widget.addItemListener(this);
		return widget;
	}

	@SuppressWarnings("unchecked")
	private Vector<?> buildModel() {
		EnumConstraint<E> con = (EnumConstraint<E>) getParameter()
			.getConstraint();
		Vector<Object> v = new Vector<Object>();
		String nullOption = ((EnumParameter<E>) getParameter()).getNullOption();
		if (nullOption != null) v.add(nullOption);
		v.addAll(con.getAllowedValues());
		return v;
	}

	
	
	
	
	
	
//	public static void main(String[] args) {
//		SwingUtilities.invokeLater(new ParamTester());
//	}
//
//	private static class ParamTester extends JFrame implements Runnable {
//
//		public ParamTester() {
//			JPanel p = new JPanel();
//			p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//			EnumParameter<FaultType> ep = new EnumParameter<FaultType>(
//				"FaultType", EnumSet.allOf(FaultType.class), null,
//				"All");
//			JComponent editor = (JComponent) ep.getEditor();
//			p.add(editor, BorderLayout.CENTER);
//
//			getContentPane().add(p);
//
//			setDefaultCloseOperation(EXIT_ON_CLOSE);
//		}
//
//		public void run() {
//			setVisible(true);
//		}
//
//	}
}
