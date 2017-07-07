package org.opensha.sha.simulators;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Event records are ordered such that the first is where the event nucleated
 * @author field
 *
 */
public class SimulatorEvent implements Comparable<SimulatorEvent>, Iterable<EventRecord>, Serializable {
	
	int event_id;			
	double magnitude;		// (same for all records for the event)
	double time;			// seconds from start of simulation (same for all records for the event)
	double duration;		// seconds (same for all records for the event)
	
	private List<? extends EventRecord> records;

	public SimulatorEvent(EventRecord eventRecord) {
		this(Lists.newArrayList(eventRecord));
	}

	public SimulatorEvent(List<? extends EventRecord> records) {
		this.records = records;
		Preconditions.checkArgument(!records.isEmpty());
		EventRecord firstRecord = records.get(0);
		this.event_id = firstRecord.getID();
		this.magnitude=firstRecord.getMagnitude();
		this.time=firstRecord.getTime();
		this.duration = firstRecord.getDuration();
	}
	
	public String toString() {
		String info="";
		info += "event_id="+event_id+"\n";
		info += "magnitude="+magnitude+"\n";
		info += "time="+time+"\n";
		info += "duration="+duration+"\n";
//		info += "getLength()="+getLength()+"\n";
		info += "getArea()="+getArea()+"\n";
		info += "size()="+size()+"\n";
		for(int i=0;i<this.size();i++) {
			EventRecord evRec = get(i);
			info += "EventRecord "+i+":\n"+evRec.toString();
		}
		
		return info;
	}
	
	/**
	 * The compares the event times, returning
	 *   0 if they are the same
	 *  -1 if that passed in is greater
	 *   1 if that passed in is less
	 */
	public int compareTo(SimulatorEvent event) {
		double thisTime = this.getTime();
		double thatTime = event.getTime();
		int cmp = Double.compare(thisTime, thatTime);
		if (cmp == 0)
			cmp = new Integer(event_id).compareTo(event.event_id);
		return cmp;
	}
	
	public boolean isSameEvent(EventRecord eventRecord) {
		return (eventRecord.getID() == event_id);
	}
	
	/**
	 * @param sectId
	 * @return
	 */
	public boolean doesEventIncludeSection(int sectId) {
		for(EventRecord eventRecord: this)
			if(eventRecord.getSectionID() == sectId)
				return true;
		return false;
	}
	
	/**
	 * This returns true if any one section ID (from the event records) is contained in
	 * the list passed in (e.g., used in SCEC VDO).
	 * @param sectId
	 * @return
	 */
	public boolean doesEventIncludeFault(HashSet<Integer> sectsForFault) {
		for(EventRecord eventRecord: this)
			if(sectsForFault.contains(eventRecord.sectionID))
				return true;
		return false;
	}
	
	public void setID(int event_id) {
		this.event_id = event_id;
	}
	
	public int getID() { return event_id;}
	
	public double getMagnitude() { return magnitude;}
	
	
	/**
	 * This returns the time of the event in seconds
	 * @return
	 */
	public double getTime() { return time;}
	
	/**
	 * This overrides the event time with the value passed in
	 * @param time
	 */
	public void setTime(double time) {
		this.time=time;
		for(EventRecord rec:this) {
			rec.setTime(time);
		}
	}
	
	/**
	 * This returns the time of the event in years
	 * @return
	 */
	public double getTimeInYears() { return time/General_EQSIM_Tools.SECONDS_PER_YEAR;}
	

	
	public double getDuration() { return duration;}

	
	/**
	 * This tells whether the event has element slips on at least one event record
	 * (the Ward event file has some records with no slips; were these moved to other 
	 * records because the mags are OK?) 
	 * @return
	 */
	public boolean hasElementSlipsAndIDs() {
		for (EventRecord evRec : this) {
			if(evRec.hasElementSlipsAndIDs())
				return true;  // true if any event record has slips and IDs
		}
		return false;
	}
	

	/**
	 * This tells whether the event has element slips on all event records
	 * (the Ward event file has some records with no slips; were these moved to other 
	 * records because the mags are OK?) 
	 * @return
	 */
	public boolean hasElementSlipsAndIDsOnAllRecords() {
		boolean hasThem = true;
		for (EventRecord evRec : this) {
			if(!evRec.hasElementSlipsAndIDs()) hasThem = false;  // false is any event record lacks slips and IDs
		}
		return hasThem;
	}
	
	public int getNumElements() {
		int num = 0;
		for (EventRecord evRec : this) {
			num += evRec.getElementIDs().length;
		}
		return num;
	}
	
	/**
	 * This returns a complete list of element IDs for this event 
	 * (it loops over all the event records). The results are in the same
	 * order as returned by getAllElementSlips().
	 * @return
	 */
	public int[] getAllElementIDs() {
		if(hasElementSlipsAndIDs()) {
			ArrayList<int[]> idList = new ArrayList<int[]>();
			int totSize = 0;
			for(int r=0; r<this.size();r++) {
				int[] ids = get(r).getElementIDs();
				totSize += ids.length;
				idList.add(ids);
			}
			int[] ids = new int[totSize];
			int index = 0;
			for (int i=0; i<idList.size(); i++) {
				int[] recIDs = idList.get(i);
				System.arraycopy(recIDs, 0, ids, index, recIDs.length);
				index += recIDs.length;
			}
			return ids;
		} else return null;
	}
	
	/**
	 * This returns a complete list of elements for this event 
	 * (it loops over all the event records). The results are in the same
	 * order as returned by getAllElementSlips().
	 * @return
	 */
	public ArrayList<SimulatorElement> getAllElements() {
		if(hasElementSlipsAndIDs()) {
			ArrayList<SimulatorElement> elementList = new ArrayList<SimulatorElement>();
			for(EventRecord er:this) {
				elementList.addAll(er.getElements());
			}
			return elementList;
		} else return null;
	}

	
	/**
	 * This returns a complete list of element Slips for this event 
	 * (it loops over all the event records).  The results are in the same
	 * order as returned by getAllElementIDs().
	 * @return
	 */
	public double[] getAllElementSlips() {
		if(hasElementSlipsAndIDs()) {
			ArrayList<double[]> slipList = new ArrayList<double[]>();
			int totSize = 0;
			for(int r=0; r<this.size();r++) {
				double[] slips = get(r).getElementSlips();
				totSize += slips.length;
				slipList.add(slips);
			}
			double[] slips = new double[totSize];
			int index = 0;
			for (int i=0; i<slipList.size(); i++) {
				double[] recSlips = slipList.get(i);
				System.arraycopy(recSlips, 0, slips, index, recSlips.length);
				index += recSlips.length;
			}
			return slips;
		} else return null;
	}
	
	
	/**
	 * This utility finds the shortest distance between the ends of two event records
	 * @param er1_1stEnd - the vertex at the first end of event record 1
	 * @param er1_2ndEnd - the vertex at the other end of event record 1
	 * @param er2_1stEnd - the vertex at the first end of event record 2
	 * @param er2_2ndEnd - the vertex at the other end of event record 2
	 * @return
	 */
	static double getMinDistBetweenEventRecordEnds(Vertex er1_1stEnd, Vertex er1_2ndEnd, Vertex er2_1stEnd, Vertex er2_2ndEnd) {
		double min1 = Math.min(er1_1stEnd.getLinearDistance(er2_1stEnd), er1_1stEnd.getLinearDistance(er2_2ndEnd));
		double min2 = Math.min(er1_2ndEnd.getLinearDistance(er2_1stEnd), er1_2ndEnd.getLinearDistance(er2_2ndEnd));
		return Math.min(min1, min2);
	}
	
	/**
	 * This returns the event area in meters squared
	 * @return
	 */
	public double getArea() {
		double area=0;
		for(EventRecord evRec:this) area += evRec.getArea();
		return area;
	}
	
	public SimulatorEvent cloneNewTime(double timeSeconds, int newID) {
		SimulatorEvent o = new SimulatorEvent(records);
//		for (int i=1; i<size(); i++)
//			o.add(get(i));
		o.time = timeSeconds;
		o.event_id = newID;
		o.magnitude = magnitude;
		o.duration = duration;
		return o;
	}

	@Override
	public Iterator<EventRecord> iterator() {
		return (Iterator<EventRecord>) records.iterator();
	}
	
	public int size() {
		return records.size();
	}
	
	public EventRecord get(int index) {
		return records.get(index);
	}
	
	/**
	 * This returns the event length in meters
	 * (computed as the sum of (das_hi-das_lo) from event records
	 * @return
	 */
	public double getLength() {
		double length=0;
		for(EventRecord evRec:records) length += evRec.getLength();
		return length;
	}

}
