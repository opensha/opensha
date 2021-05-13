package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.jfree.data.Range;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.constraint.impl.RangeConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.RangeParameterEditor;

public class RangeParameter extends AbstractParameter<Range> {
	
	private RangeParameterEditor editor;
	
	public RangeParameter(String name, Range value) {
		this(name, null, value);
	}
	
	public RangeParameter(String name, Range constraintRange, Range value) {
		super(name, buildConstraint(constraintRange), null, value);
	}
	
	private static RangeConstraint buildConstraint(Range constraintRange) {
		if (constraintRange == null)
			return null;
		return new RangeConstraint(constraintRange);
	}

	@Override
	public synchronized ParameterEditor getEditor() {
		if (editor == null)
			editor = new RangeParameterEditor(this);
		return editor;
	}

	@Override
	public Object clone() {
		RangeParameter param = new RangeParameter(getName(), getValue());
		param.setUnits(getUnits());
		if (constraint != null)
			param.setConstraint((RangeConstraint)constraint.clone());
		return param;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		try {
			String str = el.attributeValue("value").trim();
			// expected: "Range[lower,upper]
			if (!str.startsWith("Range[") || !str.endsWith("]"))
				return false;
			str = str.substring(str.indexOf("[")+1);
			str = str.substring(0, str.length()-1);
			if (!str.contains(","))
				return false;
			String[] split = str.split(",");
			if (split.length != 2)
				return false;
			double lower = Double.parseDouble(split[0]);
			double upper = Double.parseDouble(split[1]);
			this.setValue(new Range(lower, upper));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	public static void main(String[] args) {
		System.out.println(new Range(0.5, 10d));
	}

}
