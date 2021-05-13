package org.opensha.nshmp2.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/**
 * Identifier for different earthquake source types.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum SourceType {

	/** Fault source type. */
	FAULT, 
	
	/** Gridded (background) seismicity source type. */
	GRIDDED,
	
	/** Subduction source type. */
	SUBDUCTION,
	
	/** Cluster source type. */
	CLUSTER,
	
	/** Area source type. */
	AREA;
	
	@Override
	public String toString() {
		return WordUtils.capitalizeFully(name());
	}
	
	public String paramLabel() {
		StringBuilder sb = new StringBuilder();
		sb.append("Enable ");
		sb.append(StringUtils.capitalize(toString().toLowerCase()));
		sb.append(" Sources");
		return sb.toString();
	}
	
}
