package org.opensha.sha.simulators;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

/**
 * This gives information about an event on a specific section 
 * (separate event records are used when an event involves multiple sections)
 * @author field
 *
 */
public abstract class EventRecord {
	
	int event_id;			
	
	double time;			// seconds from start of simulation (same for all records for the event)
    int sectionID;			// section ID (for just this record)
    
    int numElements = 0;
    private static final int element_array_padding = 10;
    double[] elementSlips = new double[0];
    int[] elementIDs = new int[0];
    private double[] elementTimeFirstSlips = null;
    
    List<SimulatorElement> rectElementsList;	// this is all the elements, not just those used here
    
    public EventRecord(List<SimulatorElement> rectElementsList) {
    	this.rectElementsList=rectElementsList; 
    }
    
    public void addSlip(int id, double slip) {
    	addSlip(id, slip, Double.NaN);
    }
	
	public void addSlip(int id, double slip, double time) {
		int ind = numElements;
		numElements++;
		elementSlips = Doubles.ensureCapacity(elementSlips, numElements, element_array_padding);
		elementSlips[ind] = slip;
		elementIDs = Ints.ensureCapacity(elementIDs, numElements, element_array_padding);
		elementIDs[ind] = id;
		if (Double.isNaN(time)) {
			Preconditions.checkState(elementTimeFirstSlips == null,
					"Some elementes have NaN time of first slip while others have real ones");
		} else {
			if (elementTimeFirstSlips == null) {
				Preconditions.checkState(ind == 0);
				elementTimeFirstSlips = new double[element_array_padding];
			}
			elementTimeFirstSlips = Doubles.ensureCapacity(elementTimeFirstSlips, numElements, element_array_padding);
			elementTimeFirstSlips[ind] = time;
		}
	}
	
	public int getID() { return event_id;}
	
	public void setID(int id) {
		this.event_id = id;
	}
	
	public int getSectionID() {return sectionID;}
	
	public void setSectionID(int sectionID) {
		this.sectionID = sectionID;
	}
	
	public abstract double getMagnitude();
	
	public double getTime() { return time;}
	
	public void setTime(double time) { this.time=time;}
	
	public abstract double getDuration();
	
	public synchronized int[] getElementIDs() {
		if (elementIDs.length > numElements) {
			// trim down the array;
			elementIDs = Arrays.copyOf(elementIDs, numElements);
		}
		return elementIDs;
	}
	
	public void setElementIDsAndSlips(int[] elementIDs, double[] elementSlips) {
		if (elementIDs != null && elementSlips != null)
			Preconditions.checkState(elementIDs.length == elementSlips.length);
		if (elementIDs != null) {
			this.elementIDs = elementIDs;
			numElements = elementIDs.length;
		}
		if (elementSlips != null) {
			this.elementIDs = elementIDs;
			numElements = elementIDs.length;
		}
	}
	
	/**
	 * 
	 * @return length in meters
	 */
	public abstract double getLength();
	
	/**
	 * 
	 * @return area in meters squared
	 */
	public abstract double getArea();
	
	/**
	 * This gives an array of element slips (meters)
	 * @return
	 */
	public double[] getElementSlips() {
		if (elementSlips.length > numElements) {
			// trim down the array;
			elementSlips = Arrays.copyOf(elementSlips, numElements);
		}
		return elementSlips;
	}
	
	/**
	 * @return array of element time of first slips (if supported), or null if not supported by this simulator
	 */
	public double[] getElementTimeFirstSlips() {
		if (elementTimeFirstSlips == null)
			return null;
		if (elementTimeFirstSlips.length > numElements) {
			// trim down the array;
			elementTimeFirstSlips = Arrays.copyOf(elementTimeFirstSlips, numElements);
		}
		return elementTimeFirstSlips;
	}
	
	public boolean hasElementSlipsAndIDs() {
		return numElements > 0;
	}
	
	public abstract double getMoment();
	
	public List<SimulatorElement> getElements() {
		List<SimulatorElement> re_list = Lists.newArrayList();
		for(int elemID:getElementIDs()) {
			SimulatorElement elem = rectElementsList.get(elemID-1); // index is ID-1
			Preconditions.checkState(elem.getID() == elemID);
			re_list.add(elem);
		}
		return re_list;
	}

	public String toString() {
		String info = "";
		info += "event_id="+event_id+"\n";
		info += "magnitude="+getMagnitude()+"\n";
		info += "time="+time+"\n";
		info += "duration="+getDuration()+"\n";
		info += "sectionID="+sectionID+"\n";
		info += "area="+getArea()+"\n";
		info += "moment="+getMoment();
	    return info;
	}
}
