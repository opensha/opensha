package org.opensha.commons.param.editor.impl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;

/**
 * <b>Title:</b> ConstrainedDoubleDiscreteParameterEditor<p>
 *
 * <b>Description:</b> This editor is for editing DoubleDiscreteParameters.
 * The widget is simply a picklist of all possible constrained values you
 * can choose from. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class ConstrainedDoubleDiscreteParameterEditor
extends AbstractParameterEditor<Double>
implements ItemListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Class name for debugging. */
	protected final static String C = "ConstrainedDoubleDiscreteParameterEditor";
	/** If true print out debug statements. */
	protected static final boolean D = false;

	private JComponent widget;

	/** No-Arg constructor calls super(); */
	public ConstrainedDoubleDiscreteParameterEditor() { super(); }

	/**
	 * Sets the model in this constructor. The parameter is checked that it is a
	 * DoubleDiscreteParameter, and the constraint is checked that it is a
	 * DoubleDiscreteConstraint. Then the constraints are checked that
	 * there is at least one. If any of these fails an error is thrown. <P>
	 *
	 * The widget is then added to this editor, based on the number of
	 * constraints. If only one the editor is made into a non-editable label,
	 * else a picklist of values to choose from are presented to the user.
	 * A tooltip is given to the name label if model info is available.
	 */
	public ConstrainedDoubleDiscreteParameterEditor(Parameter<Double> model)
	throws ConstraintException {
		super(model);
	}

	/**
	 * Called whenever a user picks a new value in the picklist, i.e.
	 * synchronizes the model to the new GUI value. This is where the
	 * picklist value is set in the ParameterAPI of this editor.
	 */
	public void itemStateChanged(ItemEvent e) {
		String S = C + ": itemStateChanged(): ";
		if(D) System.out.println(S + "Starting: " + e.toString());

		Double value = (Double) ((JComboBox) widget).getSelectedItem();
		this.setValue(value);

		if(D) System.out.println(S + "Ending");
	}

	@Override
	public boolean isParameterSupported(Parameter<Double> param) {
		if (param == null)
			return false;
		
		if (!(param.getValue() instanceof Double))
			return false;

		ParameterConstraint constraint = param.getConstraint();
		
		if (constraint == null)
			return false;
		
		if (constraint.isNullAllowed())
			return false;

		if (!(constraint instanceof DoubleDiscreteConstraint))
			return false;
		
		DoubleDiscreteConstraint dconst = (DoubleDiscreteConstraint)constraint;

		int numConstriants = dconst.size();
		if(numConstriants < 1)
			return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (widget != null)
			widget.setEnabled(enabled);
	}

	@Override
	protected JComponent buildWidget() {
		DoubleDiscreteConstraint con = ((DoubleDiscreteConstraint)
				getParameter().getConstraint());
		
		List<Double> vals = con.getAllowedDoubles();

		if(vals.size() > 1){
			JComboBox combo = new JComboBox(vals.toArray());
			combo.setMaximumRowCount(32);
			widget = combo;
			widget.setPreferredSize(WIGET_PANEL_DIM);
			widget.setMinimumSize(WIGET_PANEL_DIM);
//			widget.setFont(JCOMBO_FONT);
			//valueEditor.setBackground(this.BACK_COLOR);
			combo.setSelectedIndex(vals.indexOf(getParameter().getValue()));
			combo.addItemListener(this);
		}
		else{
			JLabel label = makeSingleConstraintValueLabel( vals.get(0).toString() );
			widget = new JPanel(new BorderLayout());
			widget.setBackground(Color.LIGHT_GRAY);
			widget.add(label);
//			widget.add(valueEditor, WIDGET_GBC);
		}
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		DoubleDiscreteConstraint con = ((DoubleDiscreteConstraint)
				getParameter().getConstraint());
		
		List<Double> vals = con.getAllowedDoubles();
		
		if (vals.size() > 1) {
			if (widget instanceof JComboBox) {
				JComboBox combo = (JComboBox)widget;
				combo.removeItemListener(this);
				combo.setModel(new DefaultComboBoxModel(vals.toArray()));
				combo.setSelectedIndex(vals.indexOf(getParameter().getValue()));
				combo.addItemListener(this);
				return widget;
			} else {
				return buildWidget();
			}
		} else {
			return buildWidget();
		}
	}
}
