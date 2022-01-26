package org.opensha.commons.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * Class representing an N to N mapping. Similar to the java Map interface, but returns Collection's of values
 * for each key instead of individual values.
 * 
 * @author Kevin Milner
 *
 * @param <Left>
 * @param <Right>
 */
public class NtoNMap<Left, Right> {
	
	private HashMap<Left, Collection<Right>> leftToRightMap = new HashMap<Left, Collection<Right>>();
	
	private HashMap<Right, Collection<Left>> rightToLeftMap = new HashMap<Right, Collection<Left>>();
	
	private int size = 0;
	
	public NtoNMap() {
		
	}
	
	public void clear() {
		leftToRightMap.clear();
		rightToLeftMap.clear();
		size = 0;
	}
	
	/**
	 * Add the mapping to the map
	 * 
	 * @param leftElem
	 * @param rightElem
	 */
	public void put(Left leftElem, Right rightElem) {
		Collection<Right> rights = leftToRightMap.get(leftElem);
		Collection<Left> lefts = rightToLeftMap.get(rightElem);
		
		if (lefts == null) {
			lefts = new ArrayList<Left>();
			rightToLeftMap.put(rightElem, lefts);
		}
		if (rights == null) {
			rights = new ArrayList<Right>();
			leftToRightMap.put(leftElem, rights);
		}
		
		if (!lefts.contains(leftElem) && !rights.contains(rightElem)) {
			lefts.add(leftElem);
			rights.add(rightElem);
			size++;
		}
	}
	
	/**
	 * Add all mappings from the given map
	 * 
	 * @param map
	 */
	public void putAll(NtoNMap<Left, Right> map) {
		for (Left one : map.getLefts()) {
			for (Right two : map.getRights(one)) {
				this.put(one, two);
			}
		}
	}
	
	/**
	 * Get all of the Lefts
	 * 
	 * @return Set<Element1>
	 */
	public Set<Left> getLefts() {
		return leftToRightMap.keySet();
	}
	
	/**
	 * Get all of the Lefts for the given Right
	 * 
	 * @param two
	 * @return
	 */
	public Collection<Left> getLefts(Right two) {
		return rightToLeftMap.get(two);
	}
	
	/**
	 * Get all of the Rights
	 * 
	 * @return
	 */
	public Set<Right> getRights() {
		return rightToLeftMap.keySet();
	}
	
	/**
	 * Get all of the Rights for the given Left
	 * 
	 * @param one
	 * @return
	 */
	public Collection<Right> getRights(Left one) {
		return leftToRightMap.get(one);
	}
	
	/**
	 * Returns true if the specified mapping exists
	 * 
	 * @param left
	 * @param right
	 * @return
	 */
	public boolean containsMapping(Left left, Right right) {
		Collection<Left> lefts = this.getLefts(right);
		if (lefts == null)
			return false;
		
		for (Left leftTest : lefts) {
			if (leftTest.equals(left))
				return true;
		}
		
		return false;
	}
	
	/**
	 * Get the number of unique mappings
	 * 
	 * @return
	 */
	public int size() {
		return size;
	}
	
	/**
	 * Returns true if the size is 0
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		return this.size() == 0;
	}
	
	/**
	 * Remove a mapping
	 * 
	 * @param left
	 * @param right
	 * @return success
	 */
	public boolean remove(Left left, Right right) {
		Collection<Left> lefts = getLefts(right);
		Collection<Right> rights = getRights(left);
		
		if (lefts != null && rights != null) {
			if (lefts.contains(left) && rights.contains(right)) {
				boolean success = lefts.remove(left) && rights.remove(right);
				if (success) {
					size--;
					return true;
				}
			}
		}
		return false;
	}

}
