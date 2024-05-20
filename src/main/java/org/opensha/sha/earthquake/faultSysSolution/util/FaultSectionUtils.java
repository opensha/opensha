package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
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
		FaultSection sect = findSection(subSects, true, nameParts);
		if (sect == null)
			return -1;
		return sect.getParentSectionId();
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
		FaultSection sect = findSection(sects, false, nameParts);
		if (sect == null)
			return -1;
		return sect.getSectionId();
	}
	
	public static FaultSection findSection(List<? extends FaultSection> subSects, String... nameParts) {
		return findSection(subSects, false, nameParts);
	}
	
	private static FaultSection findSection(List<? extends FaultSection> subSects, boolean parent, String... nameParts) {
		Preconditions.checkState(nameParts.length > 0);
		FaultSection matchSect = null;
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
			if (parent && matchSect != null && matchSect.getParentSectionId() == sect.getParentSectionId())
				// multiple sects of the same parent, which is fine, continue
				continue;
			boolean match = true;
			for (String part : nameParts) {
				if (!myName.contains(part)) {
					match = false;
					break;
				}
			}
			if (match) {
				if (matchSect != null) {
					String prevName = parent ? matchSect.getParentSectionName() : matchSect.getSectionName();
					int prevID = parent ? matchSect.getParentSectionId() : matchSect.getSectionId();
					throw new IllegalStateException("Multiple matches for "+partDebugStr+": "
							+prevID+"='"+prevName+"' and "+myID+"='"+myName+"'");
				}
				matchSect = sect;
			}
		}
		return matchSect;
	}
	
	/**
	 * @param region
	 * @param sects
	 * @param traceOnly
	 * @return true if any part of any of the given sections is contained by the given region
	 */
	public static boolean anySectInRegion(Region region, List<? extends FaultSection> sects, boolean traceOnly) {
		for (FaultSection sect : sects)
			if (sectInRegion(region, sect, traceOnly))
				return true;
		return false;
	}
	
	/**
	 * @param region
	 * @param sect
	 * @param traceOnly
	 * @return true if any part of the given section is contained by the given region
	 */
	public static boolean sectInRegion(Region region, FaultSection sect, boolean traceOnly) {
		for (Location loc : sect.getFaultTrace())
			if (region.contains(loc))
				return true;
		if (!traceOnly) {
			// check full surface
			for (Location loc : sect.getFaultSurface(1d).getEvenlyDiscritizedListOfLocsOnSurface())
				if (region.contains(loc))
					return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param sects
	 * @return parent section ID if identical for all of the supplied sections, otherwise null
	 */
	public static Integer getCommonParentID(List<? extends FaultSection> sects) {
		Integer parent = null;
		for (FaultSection sect : sects) {
			int parentID = sect.getParentSectionId();
			Preconditions.checkState(parentID >= 0, "Sect doesn't have parent info: %s. %s",
					sect.getSectionId(), sect.getSectionName());
			if (parent == null)
				parent = parentID;
			else if (parentID != parent.intValue())
				return null;
		}
		return parent;
	}

}
