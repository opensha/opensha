package org.opensha.sha.param.editor;

import java.util.ListIterator;
import java.util.Vector;

import javax.swing.JComboBox;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.ConstrainedStringParameterEditor;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.param.MagDistStringParameter;

/**
 * <b>Title:</b> MagDistStringParameterEditor<p>
 *
 * <b>Description:</b> This editor is for editing
 * MagDistStringParameters. Recall a MagDistStringParameter
 * contains a list of the only allowed values. Therefore this editor
 * presents a picklist of those allowed values, instead of a
 * JTextField or subclass. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class MagDistStringParameterEditor extends
ConstrainedStringParameterEditor {

	public MagDistStringParameterEditor(Parameter param) {
		super(param);
	}

	@Override
	public boolean isParameterSupported(Parameter<String> param) {
		String S = C + ": Constructor(model): ";
		if(D) System.out.println(S + "Starting");

		if (param == null) {
			return false;
		}

		if (!(param instanceof MagDistStringParameter))
			return false;

		ParameterConstraint constraint = param.getConstraint();

		if (!(constraint instanceof StringConstraint))
			return false;

		int numConstriants = ((StringConstraint)constraint).size();
		if(numConstriants < 1)
			return false;

		if(D) System.out.println(S + "Ending");
		return true;
	}

}
