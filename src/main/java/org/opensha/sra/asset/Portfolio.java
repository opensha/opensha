package org.opensha.sra.asset;

import java.util.ArrayList;

import org.opensha.commons.data.Named;

/**
 * A <code>Portfolio</code> represents a collecion of <code>Asset</code>s.
 * 
 * @author Peter Powers
 * @version $Id: Portfolio.java 7863 2011-05-23 20:41:47Z kmilner $
 */
public class Portfolio extends ArrayList<Asset> implements Named {

	private String name;
	
	/**
	 * Creates a new <code>Portfolio</code> with the given name.
	 * @param name the name of the <code>Portfolio</code>
	 */
	public Portfolio(String name) {
		this.name = name;
	}
	
	
	@Override
	public String getName() {
		return name;
	}

}
