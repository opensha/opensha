package org.opensha.sha.gui.beans.event;

import java.util.EventObject;

import org.opensha.commons.param.Parameter;

public class IMTChangeEvent extends EventObject {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Parameter<Double> newIMT;
	
	public IMTChangeEvent(Object source,
			Parameter<Double> newIMT) {
		super(source);
		this.newIMT = newIMT;
	}

	public Parameter<Double> getNewIMT() {
		return newIMT;
	}

}
