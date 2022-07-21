package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class FaultSectionUtils {
	
	/**
	 * Finds the parent section ID matching all of the given name parts for the given list of subsections. Matches
	 * are compared ignoring case.
	 * 
	 * @param subSects subsection list
	 * @param nameParts elements of the parent section name that must exist for it to be considered a match
	 * @return matching parent section ID, or -1 if none found
	 * @throws IllegalStateException if multiple matches are found
	 */
	public static int findParentSectionID(List<? extends FaultSection> subSects, String... nameParts) {
		return doFindSectionID(subSects, true, nameParts);
	}
	
	/**
	 * Finds the section ID matching all of the given name parts for the given list of sections. Matches
	 * are compared ignoring case.
	 * 
	 * @param sects section list
	 * @param nameParts elements of the section name that must exist for it to be considered a match
	 * @return matching section ID, or -1 if none found
	 * @throws IllegalStateException if multiple matches are found
	 */
	public static int findSectionID(List<? extends FaultSection> sects, String... nameParts) {
		return doFindSectionID(sects, false, nameParts);
	}
	
	private static int doFindSectionID(List<? extends FaultSection> subSects, boolean parent, String... nameParts) {
		Preconditions.checkState(nameParts.length > 0);
		String prevMatch = null;
		int matchingID = -1;
		String partDebugStr = "[";
		for (int i=0; i<nameParts.length; i++) {
			String part = nameParts[i];
			if (i > 0)
				partDebugStr += ", ";
			partDebugStr += "'"+part+"'";
			nameParts[i] = part.toLowerCase();
		}
		partDebugStr += "]";
		for (FaultSection sect : subSects) {
			String myName;
			int myID;
			if (parent) {
				myName = sect.getParentSectionName();
				Preconditions.checkNotNull(myName, "Parent section names not set");
				myID = sect.getParentSectionId();
			} else {
				myName = sect.getSectionName();
				Preconditions.checkNotNull(myName, "Section names not set");
				myID = sect.getSectionId();
			}
			Preconditions.checkState(myID >= 0, "IDs not set");
			myName = myName.toLowerCase();
			if (sect.getParentSectionId() == matchingID)
				continue;
			boolean match = true;
			for (String part : nameParts) {
				if (!myName.contains(part)) {
					match = false;
					break;
				}
			}
			if (match) {
				Preconditions.checkState(prevMatch == null, "Multiple matches for %s: %s='%s' and %s='%s'",
						partDebugStr, matchingID, prevMatch, myID, myName);
				matchingID = myID;
				prevMatch = myName;
			}
		}
		return matchingID;
	}

}
