package org.opensha.sha.imr.param.OtherParams;

import org.opensha.commons.data.Named;

/**
 * The component of shaking for a given intensity measure. Used in conjunction with the
 * ComponentParam.
 * @author kevin
 *
 */
public enum Component implements Named {
	
	AVE_HORZ("Average Horizontal"),
	GMRotI50("Average Horizontal (GMRotI50)"),
	RANDOM_HORZ("Random Horizontal"),
	UNKNOWN_HORZ("Unknown Horizontal"), // used by DahleEtAl 1995
	GREATER_OF_TWO_HORZ("Greater of Two Horz."),
	VERT("Vertical"),
	RotD50("RotD50"), // TODO improve labeling, add to glossary
	RotD100("RotD100"); // TODO improve labeling, add to glossary
	
	private String name;
	private Component(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return name;
	}

}
