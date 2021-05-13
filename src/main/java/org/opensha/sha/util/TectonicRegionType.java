/**
 * 
 */
package org.opensha.sha.util;

import java.io.IOException;
import java.io.Serializable;

import org.opensha.commons.util.FileUtils;

/**
 * @author field
 *
 */
public enum TectonicRegionType implements Serializable {
	
	
	/** Active shallow crust tectonic region. */
	ACTIVE_SHALLOW("Active Shallow Crust", 200),
	
	/** Stable shallow crust tectonic region. */
	STABLE_SHALLOW("Stable Shallow Crust", 1000),
	
	/** Subduction Interface tectonic region. */
	SUBDUCTION_INTERFACE("Subduction Interface", 1000),
	
	/** Subduction IntraSlab tectonic region. */
	SUBDUCTION_SLAB("Subduction IntraSlab", 1000),
	
	/** Volcanic tectonic region. */
	VOLCANIC("Volcanic", 200);
	
	private String name;
	private double cutoff;
	
	private TectonicRegionType(String name, double cutoff) {
		this.name = name;
		this.cutoff = cutoff;
	}
	
	/**
	 * This gets the TectonicRegionType associated with the given string
	 * @param name
	 * @return
	 */
	public static TectonicRegionType getTypeForName(String name) {
		if (name == null) throw new NullPointerException();
		for (TectonicRegionType trt:TectonicRegionType.values()) {
			if (trt.name.equals(name)) return trt;
		}
		throw new IllegalArgumentException("TectonicRegionType name does not exist");
	}
	
	/**
	 * This check whether given string is a valid tectonic region
	 * @param name
	 * @return
	 */
	public static boolean isValidType(String name) {
		boolean answer = false;
		for (TectonicRegionType trt:TectonicRegionType.values()) {
			if (trt.name.equals(name)) answer = true;
		}
		return answer;
	}

	
	@Override
	public String toString() {
		return name;
	}
	
	/**
	 * Returns the default calculation cutoff distance for this type.
	 * @return the default calcualtion cutoff distance
	 */
	public double defaultCutoffDist() {
		return cutoff;
	}
	
	//public 
	public static void main(String[] args) throws IOException {
		System.out.println(isValidType("Active Shallow Crust"));
		String fname = "/tmp/trt.obj";
		TectonicRegionType before = TectonicRegionType.ACTIVE_SHALLOW;
		FileUtils.saveObjectInFile(fname, before);
		TectonicRegionType after = (TectonicRegionType)FileUtils.loadObject(fname);
		System.out.println("before: " + before);
		System.out.println("after: " + after);
	}


}
