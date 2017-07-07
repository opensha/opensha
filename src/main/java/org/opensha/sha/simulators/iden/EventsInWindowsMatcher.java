package org.opensha.sha.simulators.iden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class EventsInWindowsMatcher {
	
	// inputs
	private List<? extends SimulatorEvent> events;
	private RuptureIdentifier rupIden;
	private double minWindowDurationYears;
	private double windowDurationYears;
	private boolean randomizeEventTimes;
	
	// outputs
	private List<SimulatorEvent> eventsInWindows;
	private List<Double> timeFromStarts;
	private List<TimeWindow> timeWindows;
	private HashSet<Integer> matchIDs;
	private double totalWindowDurationYears;
	
	public EventsInWindowsMatcher(List<? extends SimulatorEvent> events,
			RuptureIdentifier rupIden,
			double minWindowDurationYears,
			double windowDurationYears,
			boolean randomizeEventTimes) {
		this.events = events;
		this.rupIden = rupIden;
		this.minWindowDurationYears = minWindowDurationYears;
		this.windowDurationYears = windowDurationYears;
		this.randomizeEventTimes = randomizeEventTimes;
		
		update();
	}
	
	private List<? extends SimulatorEvent> update() {
		eventsInWindows = Lists.newArrayList();
		timeFromStarts = Lists.newArrayList();
		
		if (events == null || events.isEmpty())
			return null;
		
		if (randomizeEventTimes) {
			double startTime = events.get(0).getTime();
			double simDuration = General_EQSIM_Tools.getSimulationDuration(events);
			
			ArrayList<SimulatorEvent> randomizedEvents = new ArrayList<SimulatorEvent>();
			
			for (SimulatorEvent e : events) {
				double time = startTime+Math.random()*simDuration;
				SimulatorEvent r = e.cloneNewTime(time, e.getID());
				randomizedEvents.add(r);
			}
			
			Collections.sort(randomizedEvents);
			
			events = randomizedEvents;
		}
		
		List<? extends SimulatorEvent> matches = rupIden.getMatches(events);
		
		if (matches.isEmpty()) {
			System.out.println("No matches found!");
			return Lists.newArrayList();
		}
		
		double duration = windowDurationYears * General_EQSIM_Tools.SECONDS_PER_YEAR;
		
		double minDuration = minWindowDurationYears * General_EQSIM_Tools.SECONDS_PER_YEAR;
		
		// [start, end] in seconds
		timeWindows = Lists.newArrayList();
		
		double simEndTime = events.get(events.size()-1).getTime();
		
		double windowDurationSum = 0;
		int overlaps = 0;
		
		// find the time windows and total time covered (accounting for overlap)
		TimeWindow prev = null;
		matchIDs = new HashSet<Integer>();
		for (SimulatorEvent e : matches) {
			double start = e.getTime();
			double end = start + duration;
			start += minDuration; // do this afterward so that the end time doesn't get bumped back
			
			if (end > simEndTime)
				end = simEndTime;
			
			double noOverlapStart = start;
			if (prev != null && noOverlapStart < prev.getEnd()) {
				noOverlapStart = prev.getEnd();
				overlaps++;
			}
			double noOverlapDuration = end - noOverlapStart;
			Preconditions.checkState(noOverlapDuration >= 0);
			windowDurationSum += noOverlapDuration;
			
			matchIDs.add(e.getID());
			
			TimeWindow window = new TimeWindow(start, end, e.getID());
			timeWindows.add(window);
			prev = window;
		}
		
		totalWindowDurationYears = windowDurationSum / General_EQSIM_Tools.SECONDS_PER_YEAR;
		double rate = 1d / totalWindowDurationYears;
		
		System.out.println("Got "+matches.size()+" matches in "+timeWindows.size()+" windows ("
				+overlaps+" overlaps).");
		System.out.println("Total window duration: "+totalWindowDurationYears+" years");
		System.out.println("In-window event rate: "+rate);
		
		int windowIndex = 0;
		int numEventsInWindows = 0;
		mainloop:
		for (SimulatorEvent e : events) {
			double time = e.getTime();
			while (time > timeWindows.get(windowIndex).getEnd()) {
				// while this event happened after the current window ends
				// get the next window
				windowIndex++;
				if (windowIndex >= timeWindows.size())
					break mainloop;
			}
			TimeWindow matchingWindow = null;
			for (int i=windowIndex; i<timeWindows.size(); i++) {
				TimeWindow window = timeWindows.get(i);
				if (window.isBefore(time))
					// this means that the time is before the start of the first window, therefore not in a window
					break;
				if (!window.isAfter(time) && !window.isInitiator(e.getID())) {
					matchingWindow = window;
//					break; // don't break here, we want the last window that it fits into for time from start calcs
				}
			}
			if (matchingWindow == null)
				continue;
			
//			if (matchIDs.contains(e.getID())) {
//				System.out.print("Matching event "+e.getID()
//						+" made it in due to "+matchingWindow.getInitiatorID()+"'s window! ");
//				double diff = e.getTime() - matchingWindow.getStart();
//				double diffMins = diff / 60d;
//				double diffHours = diffMins / 60d;
//				double diffDays = diffHours / 24;
//				double diffYears = diffDays / 365;
//				if (Math.abs(diffYears) > 1)
//					System.out.println("Diff time: "+(float)diffYears+" years");
//				else if (Math.abs(diffDays) > 1)
//					System.out.println("Diff time: "+(float)diffDays+" days");
//				else if (Math.abs(diffHours) > 1)
//					System.out.println("Diff time: "+(float)diffHours+" hours");
//				else if (Math.abs(diffMins) > 1)
//					System.out.println("Diff time: "+(float)diffMins+" mins");
//				else
//					System.out.println("Diff time: "+(float)diff+" secs");
//			}
			
			numEventsInWindows++;
			eventsInWindows.add(e);
			double matchTime = e.getTime() - matchingWindow.getStart();
			Preconditions.checkState(matchTime > 0);
			Preconditions.checkState(matchTime < duration);
			timeFromStarts.add(e.getTime() - matchingWindow.getStart());
		}
		System.out.println("Found "+numEventsInWindows+" events in the given windows/mag range");
		
		return eventsInWindows;
	}

	public List<? extends SimulatorEvent> getInputEvents() {
		return events;
	}

	public RuptureIdentifier getRupIden() {
		return rupIden;
	}

	public double getMinWindowDurationYears() {
		return minWindowDurationYears;
	}

	public double getWindowDurationYears() {
		return windowDurationYears;
	}

	public boolean isRandomizeEventTimes() {
		return randomizeEventTimes;
	}

	public List<SimulatorEvent> getEventsInWindows() {
		return eventsInWindows;
	}
	
	public List<Double> getEventTimesFromWindowStarts() {
		return timeFromStarts;
	}

	public List<TimeWindow> getTimeWindows() {
		return timeWindows;
	}

	public HashSet<Integer> getMatchIDs() {
		return matchIDs;
	}

	public double getTotalWindowDurationYears() {
		return totalWindowDurationYears;
	}

}
