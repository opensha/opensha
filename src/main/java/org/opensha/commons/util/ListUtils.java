package org.opensha.commons.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.data.Named;

public class ListUtils {
	
	public static Named getObjectByName(Collection<? extends Named> objects, String name) {
		for (Named object : objects) {
			if (object.getName().equals(name))
				return object;
		}
		return null;
	}
	
	public static int getIndexByName(List<? extends Named> list, String name) {
		for (int i=0; i<list.size(); i++) {
			if (list.get(i).getName().equals(name))
				return i;
		}
		return -1;
	}
	
	public static ArrayList<String> getNamesList(Collection<? extends Named> objects) {
		ArrayList<String> names = new ArrayList<String>();
		for (Named object : objects) {
			names.add(object.getName());
		}
		return names;
	}
	
	public static <T> ArrayList<T> wrapInList(T object) {
		ArrayList<T> list = new ArrayList<T>();
		list.add(object);
		return list;
	}
	
	public static int getClosestIndex(List<Double> values, double target) {
		double dist = Double.MAX_VALUE;
		int index = -1;
		for (int i=0; i<values.size(); i++) {
			double val = values.get(i);
			double myDist = Math.abs(target - val);
			if (myDist < dist) {
				dist = myDist;
				index = i;
			}
		}
		return index;
	}

}
