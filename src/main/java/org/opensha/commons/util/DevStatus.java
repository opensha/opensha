package org.opensha.commons.util;

import org.opensha.commons.data.Named;

/**
 * Class that may be used to represent different states of development. For
 * example, a particular interface or abstract class may have some
 * implementations that have been vetted and tested and are ready for use in
 * production environments while others are under development, experimental, or
 * deprecated.
 * 
 * @author Peter Powers
 * @version $Id: DevStatus.java 11351 2016-05-27 18:02:11Z kmilner $
 */
public enum DevStatus implements Named, Comparable<DevStatus> {

	/** Status indicating something is production ready. */
	PRODUCTION("Production", "dist", 0),

	/** Status indicating something is under development. */
	DEVELOPMENT("Development", "nightly", 1),
	
	/** Status indicating something is merely experimental. */
	EXPERIMENTAL("Experimental", null, 2),

	/** Status indicating something is deprecated. */
	DEPRECATED("Deprecated", null, 3),
	
	/** Status indicating something has a critical error and is disabled until fixed */
	ERROR("Error", null, 4);
	
	private final String name;
	private final String buildDirName;
	private final int priority;
	
	private DevStatus(String name, String buildDirName, int priority) {
		this.name = name;
		this.buildDirName = buildDirName;
		this.priority = priority;
	}

	/**
	 * This retuns the name of the directory associated with this DevStatus instance. This is used in the directory
	 * structure for builds on our server.
	 * 
	 * @return build dir name
	 * @throws UnsupportedOperationException if not applicable for this DevStatus
	 */
	public String getBuildDirName() {
		if (buildDirName == null)
			throw new UnsupportedOperationException("Build dir name is not applicable for DevStatus: "+getName());
		return buildDirName;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public int priority() {
		return priority;
	}
}
