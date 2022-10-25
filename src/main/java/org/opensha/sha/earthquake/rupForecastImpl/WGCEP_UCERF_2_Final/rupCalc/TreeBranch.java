package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.rupCalc;

import java.util.ArrayList;

/**
 * <p>Title: TreeBranch.java </p>
 * <p>Description: This refers to a branch of a tree </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TreeBranch {
  private int subSectionId;
  private ArrayList<Integer> adjacentSubSections = new ArrayList<Integer>();

  public TreeBranch(int subSectionId) {
	  this.subSectionId = subSectionId;
  }

  /**
   * Get sub-section id
   * @return
   */
  public int getSubSectionId() {
    return subSectionId;
  }

  /**
   * Get number of adjacent subsections to this section
   * @return
   */
  public int getNumAdjacentSubsections() {
	  return this.adjacentSubSections.size();
  }
  
  
  /**
   * Get the adjancet subsection at the specified index
   * @param index
   * @return
   */
  public int getAdjacentSubSection(int index) {
	  return adjacentSubSections.get(index);
  }
  
  /**
   * Get a list of all adjacent subsections
   * @return
   */
  public ArrayList<Integer> getAdjacentSubSectionsList() {
	  return this.adjacentSubSections;
  }
  
  /**
   * Add adjacent sub section (if it does not exist already)
   * @param subSectionName
   */
  public void addAdjacentSubSection(int subSectionId) {
	  if(!adjacentSubSections.contains(subSectionId))
		  adjacentSubSections.add(subSectionId);
  }
  
  /**
   * Is the specified sub section  adjacent to this sub section?
   * 
   * @param subSectionName
   * @return
   */
  public boolean isAdjacentSubSection(int subSectionId) {
	  return adjacentSubSections.contains(subSectionId);
  }

}
