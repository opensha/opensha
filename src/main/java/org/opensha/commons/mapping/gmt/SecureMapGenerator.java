package org.opensha.commons.mapping.gmt;

import java.util.ArrayList;

import org.opensha.commons.exceptions.GMT_MapException;

public interface SecureMapGenerator {

	/** Validates any additional renderer-specific work on an HTTP request. */
	default void validateRemoteWork(GMT_Map map) throws GMT_MapException {}
	
	public ArrayList<String> getGMT_ScriptLines(GMT_Map map, String newDir) throws GMT_MapException;

}
