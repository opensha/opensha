package org.opensha.refFaultParamDb.vo;

import java.util.ArrayList;

/**
 * <p>Title: EventSequence.java </p>
 * <p>Description: This class saves the information about a event sequence</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class EventSequence {

	private String sequenceName;
	private double sequenceProb;
	private String comments;
	private ArrayList<PaleoEvent> eventsParam;
	private double[] missedEventsProbs;

	public EventSequence() {
	}

	public String toString() {
		String paleoEventStr="", missedEventsProbStr="";
		// ids of events in this sequence
		for(int i=0; eventsParam!=null && i<eventsParam.size(); ++i) {
			PaleoEvent paleoEvent = (PaleoEvent)eventsParam.get(i);
			paleoEventStr+=paleoEvent.getEventId()+",";
		}
		// probabilites of missed events
		for(int i=0; i<missedEventsProbs.length; ++i) {
			missedEventsProbStr+=missedEventsProbs[i]+",";
		}
		return "Sequence Name="+sequenceName+"\n"+
		"Sequence Prob="+sequenceProb+"\n"+
		"Events In sequence="+paleoEventStr+"\n"+
		"Prob of missed events="+missedEventsProbStr+"\n"+
		"Comments="+comments;
	}

	public String getComments() {
		return comments;
	}
	public ArrayList<PaleoEvent> getEventsParam() {
		return eventsParam;
	}
	public double[] getMissedEventsProbs() {
		return missedEventsProbs;
	}
	public String getSequenceName() {
		return sequenceName;
	}
	public double getSequenceProb() {
		return sequenceProb;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}
	public void setEventsParam(ArrayList<PaleoEvent> eventsParam) {
		this.eventsParam = eventsParam;
	}
	public void setMissedEventsProbList(double[] missedEventsProbs) {
		this.missedEventsProbs = missedEventsProbs;
	}
	public void setSequenceName(String sequenceName) {
		this.sequenceName = sequenceName;
	}
	public void setSequenceProb(double sequenceProb) {
		this.sequenceProb = sequenceProb;
	}
}
