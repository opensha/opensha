package org.opensha.commons.util.bugReports;

public interface KnownBugDetector {
	
	public boolean isKnownBug(BugReport bug);
	
	public String getKnownBugDescription();
	
	public boolean canIgnore();

}
