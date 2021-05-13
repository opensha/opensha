package org.opensha.commons.mapping.gmt;

import java.util.ArrayList;

import org.opensha.commons.exceptions.GMT_MapException;

public interface SecureMapGenerator {
	
	public ArrayList<String> getGMT_ScriptLines(GMT_Map map, String newDir) throws GMT_MapException;

}
