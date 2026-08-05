package org.opensha.commons.param.impl;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.MultilineStringParameterEditor;

/**
 * String parameter displayed with a multiline text area editor.
 */
public class MultilineStringParameter extends StringParameter {

	private static final long serialVersionUID = 1L;

	protected final static String C = "MultilineStringParameter";

	private transient ParameterEditor<String> paramEdit = null;

	private boolean textEditable = true;
	private boolean scrollPaneEnabled = true;
	private boolean lineWrap = true;
	private boolean wrapStyleWord = true;
	private boolean includedInMetadata = true;
	private int rows = 5;

	public MultilineStringParameter(String name) {
		super(name);
	}

	public MultilineStringParameter(String name, String value) {
		super(name, value);
	}

	public MultilineStringParameter(String name, StringConstraint constraint) throws ConstraintException {
		super(name, constraint);
	}

	public MultilineStringParameter(String name, StringConstraint constraint, String value) throws ConstraintException {
		super(name, constraint, value);
	}

	public MultilineStringParameter(String name, String units, String value) throws ConstraintException {
		super(name, units, value);
	}

	public MultilineStringParameter(String name, StringConstraint constraint, String units, String value)
			throws ConstraintException {
		super(name, constraint, units, value);
	}

	/**
	 * Returns whether the text area accepts user text edits. This is separate
	 * from {@link #isEditable()}, which controls parameter metadata mutability.
	 */
	public boolean isTextEditable() {
		return textEditable;
	}

	public void setTextEditable(boolean textEditable) {
		this.textEditable = textEditable;
		refreshEditor();
	}

	public boolean isScrollPaneEnabled() {
		return scrollPaneEnabled;
	}

	public void setScrollPaneEnabled(boolean scrollPaneEnabled) {
		this.scrollPaneEnabled = scrollPaneEnabled;
		refreshEditor();
	}

	public boolean isLineWrap() {
		return lineWrap;
	}

	public void setLineWrap(boolean lineWrap) {
		this.lineWrap = lineWrap;
		refreshEditor();
	}

	public boolean isWrapStyleWord() {
		return wrapStyleWord;
	}

	public void setWrapStyleWord(boolean wrapStyleWord) {
		this.wrapStyleWord = wrapStyleWord;
		refreshEditor();
	}

	/**
	 * Returns whether this parameter should contribute to metadata strings.
	 */
	public boolean isIncludedInMetadata() {
		return includedInMetadata;
	}

	public void setIncludedInMetadata(boolean includedInMetadata) {
		this.includedInMetadata = includedInMetadata;
	}

	public int getRows() {
		return rows;
	}

	public void setRows(int rows) {
		if (rows < 1)
			throw new IllegalArgumentException("Rows must be positive");
		this.rows = rows;
		refreshEditor();
	}

	@Override
	public String getType() {
		return C;
	}

	@Override
	public Object clone() {
		MultilineStringParameter param;
		if (value == null) {
			if (constraint == null) {
				param = new MultilineStringParameter(name);
			} else {
				param = new MultilineStringParameter(name, (StringConstraint)constraint.clone());
			}
			param.setUnits(units);
		} else {
			StringConstraint clonedConstraint = constraint == null ? null : (StringConstraint)constraint.clone();
			param = new MultilineStringParameter(name, clonedConstraint, units, value.toString());
		}
		param.editable = true;
		param.info = info;
		param.textEditable = textEditable;
		param.scrollPaneEnabled = scrollPaneEnabled;
		param.lineWrap = lineWrap;
		param.wrapStyleWord = wrapStyleWord;
		param.includedInMetadata = includedInMetadata;
		param.rows = rows;
		return param;
	}

	@Override
	public String getMetadataString() {
		if (!includedInMetadata)
			return null;
		return super.getMetadataString();
	}

	@Override
	public ParameterEditor<String> getEditor() {
		if (paramEdit == null) {
			try {
				paramEdit = new MultilineStringParameterEditor(this);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return paramEdit;
	}

	@Override
	public boolean isEditorBuilt() {
		return paramEdit != null;
	}
}
