package org.opensha.sha.simulators.srf;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.simulators.RSQSimEvent;

import com.google.common.base.Preconditions;

public class RSQSimEventSlipTimeFunc {
	
	private Map<Integer,  List<RSQSimStateTime>> patchTransitions;
	private Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
	private Map<Integer, DiscretizedFunc> relSlipFuncs = new HashMap<>();
	
	private double minTime;
	private double maxTime;
	
	private double minVel;
	private double maxVel;
	
	/**
	 * @param patchTransitions state transitions for this event
	 * @param slipVels patch slip velocities in m/s (used if variableSlipSpeed == false)
	 * @param variableSlipSpeed flag for variable slip speed (from RSQSimStateTime instances)
	 */
	public RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions) {
		Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
		Map<Integer, DiscretizedFunc> relSlipFuncs = new HashMap<>();
		double minTime = Double.POSITIVE_INFINITY;
		double maxTime = Double.NEGATIVE_INFINITY;
		double minVel = Double.POSITIVE_INFINITY;
		double maxVel = Double.NEGATIVE_INFINITY;
		for (int patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTrans = patchTransitions.get(patchID);
			if (patchTrans.isEmpty())
				continue;
			DiscretizedFunc slipFunc = new ArbitrarilyDiscretizedFunc();
			DiscretizedFunc relSlipFunc = new ArbitrarilyDiscretizedFunc();
			double curSlip = 0d;
			for (RSQSimStateTime trans : patchTrans) {
				if (trans.state == RSQSimState.EARTHQUAKE_SLIP) {
					// slipping
					slipFunc.set(trans.absoluteTime, curSlip);
					Preconditions.checkState(Double.isFinite(trans.relativeTime));
					relSlipFunc.set(trans.relativeTime, curSlip);
					double slipVel = trans.velocity;
					minVel = Math.min(minVel, slipVel);
					maxVel = Math.max(maxVel, slipVel);
					Preconditions.checkState(trans.hasDuration(), "EQ slip and we don't have a duration!");
					double slip = slipVel * trans.getDuration();
					curSlip += slip;
					slipFunc.set(trans.absoluteTime+trans.getDuration(), curSlip);
					relSlipFunc.set(trans.relativeTime+trans.getDuration(), curSlip);
				}
			}
			slipFuncs.put(patchID, slipFunc);
			relSlipFuncs.put(patchID, relSlipFunc);
			minTime = Math.min(minTime, slipFunc.getMinX());
			maxTime = Math.max(maxTime, slipFunc.getMaxX());
		}
		init(patchTransitions, slipFuncs, relSlipFuncs, minTime, maxTime, minVel, maxVel);
	}
	
	private RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions,
			Map<Integer, DiscretizedFunc> slipFuncs, Map<Integer, DiscretizedFunc> relSlipFuncs,
			double minTime, double maxTime, double minVel, double maxVel) {
		init(patchTransitions, slipFuncs, relSlipFuncs, minTime, maxTime, minVel, maxVel);
	}
	
	private void init(Map<Integer, List<RSQSimStateTime>> patchTransitions,
			Map<Integer, DiscretizedFunc> slipFuncs, Map<Integer, DiscretizedFunc> relSlipFuncs,
			double minTime, double maxTime, double minVel, double maxVel) {
		this.patchTransitions = patchTransitions;
		this.slipFuncs = slipFuncs;
		this.relSlipFuncs = relSlipFuncs;
		this.minTime = minTime;
		this.maxTime = maxTime;
		this.minVel = minVel;
		this.maxVel = maxVel;
	}
	
	public RSQSimStateTime getStateTime(int patchID, double time) {
		if (patchTransitions.containsKey(patchID)) {
			for (RSQSimStateTime trans : patchTransitions.get(patchID))
				if (time >= trans.absoluteTime && trans.hasDuration()
					&& time < trans.absoluteTime+trans.getDuration())
					return trans;
		}
		return null;
	}
	
	public RSQSimState getState(int patchID, double time) {
		RSQSimStateTime stateTime = getStateTime(patchID, time);
		if (stateTime != null)
			return stateTime.state;
		return null;
	}
	
	List<RSQSimStateTime> getTransitions(int patchID) {
		return patchTransitions.get(patchID);
	}
	
	DiscretizedFunc getSlipFunc(int patchID) {
		return slipFuncs.get(patchID);
	}
	
	public double getMaxSlipVel() {
		return maxVel;
	}
	
	public double getMinSlipVel() {
		return minVel;
	}
	
	public double getVelocity(RSQSimStateTime stateTime) {
		if (stateTime.state == RSQSimState.EARTHQUAKE_SLIP)
			return stateTime.velocity;
		return 0d;
	}
	
	/**
	 * @param patchID
	 * @param time absolute catalog time in seconds
	 * @return slip velocity if in EARTHQUAKE_SLIP state, 0 if in other state, or NaN if patch not applicable
	 */
	public double getVelocity(int patchID, double time) {
		if (!patchTransitions.containsKey(patchID))
			return Double.NaN;
		RSQSimStateTime stateTime = getStateTime(patchID, time);
		if (stateTime == null)
			return 0d;
		return getVelocity(stateTime);
	}
	
	/**
	 * @param patchID
	 * @return time of first slip (either earthquake or nucleating slip) for the given patch
	 */
	public double getTimeOfFirstSlip(int patchID) {
		for (RSQSimStateTime trans : patchTransitions.get(patchID))
			if (trans.state == RSQSimState.EARTHQUAKE_SLIP)
				return trans.absoluteTime;
		return Double.NaN;
	}
	
	/**
	 * @param patchID
	 * @return the end time of the last slip (either earthquake or nucleating slip) event on the given patch
	 */
	public double getTimeOfLastSlip(int patchID) {
		List<RSQSimStateTime> patchTrans = patchTransitions.get(patchID);
		for (int i=patchTrans.size(); --i>=0;) {
			RSQSimStateTime trans = patchTrans.get(i);
			if (trans.state == RSQSimState.EARTHQUAKE_SLIP)
				return trans.absoluteTime+trans.getDuration();
		}
		return Double.NaN;
	}
	
	/**
	 * 
	 * @param patchID
	 * @param time
	 * @return cumulative slip in this event at the given time, or NaN if patch not applicable
	 */
	public double getCumulativeEventSlip(int patchID, double time) {
		DiscretizedFunc slipFunc = slipFuncs.get(patchID);
		if (slipFunc == null)
			return Double.NaN;
		if (time < slipFunc.getMinX())
			return 0d;
		if (time > slipFunc.getMaxX())
			return slipFunc.getY(slipFunc.size()-1);
		return slipFunc.getInterpolatedY(time);
	}
	
	public double getMaxCumulativeSlip() {
		double max = 0d;
		for (DiscretizedFunc func : slipFuncs.values())
			max = Math.max(max, func.getMaxY());
		return max;
	}
	
	MinMaxAveTracker validateTotalSlip(RSQSimEvent event, double pDiffThreshold) {
		int[] patchIDs = event.getAllElementIDs();
		double[] slips = event.getAllElementSlips();
		
		MinMaxAveTracker pDiffTrack = new MinMaxAveTracker();
		
		for (int i=0; i<patchIDs.length; i++) {
			int patchID = patchIDs[i];
			double expectedSlip = slips[i];
			double calcSlip = getCumulativeEventSlip(patchID, Double.POSITIVE_INFINITY);
			
			if (expectedSlip == 0) {
				Preconditions.checkState(calcSlip == 0 || Double.isNaN(calcSlip),
				"Expected zero slip, calculated %s", calcSlip);
			} else {
				if (expectedSlip < 1e-4)
					continue;
				double pDiff = DataUtils.getPercentDiff(calcSlip, expectedSlip);
				pDiffTrack.addValue(pDiff);
				Preconditions.checkState(pDiff <= pDiffThreshold, "Calculated slip is off for patch %s.\n"
						+ "\tExpected: %s\n\tCalculated: %s\n\tDiff: %s\n\tpDiff: %s",
						patchID, expectedSlip, calcSlip, Math.abs(calcSlip - expectedSlip), pDiff);
			}
		}
		return pDiffTrack;
	}
	
	public double getStartTime() {
		return minTime;
	}
	
	public double getEndTime() {
		return maxTime;
	}
	
	private RSQSimEventSlipTimeFunc relative = null;
	public synchronized RSQSimEventSlipTimeFunc asRelativeTimeFunc() {
		if (minTime == 0)
			return this;
		if (relative == null) {
			Map<Integer, List<RSQSimStateTime>> relPatchTransitions = new HashMap<>();
			for (Integer patchID : patchTransitions.keySet()) {
				List<RSQSimStateTime> relTrans = new ArrayList<>();
				for (RSQSimStateTime trans : patchTransitions.get(patchID)) {
					RSQSimStateTime rTrans = new RSQSimStateTime((double)trans.relativeTime, trans.relativeTime,
							trans.eventID, patchID, trans.state, trans.velocity);
					if (trans.hasDuration())
						rTrans.setDuration(trans.getDuration());
					relTrans.add(rTrans);
				}
				relPatchTransitions.put(patchID, relTrans);
			}
			double maxRelTime = 0d;
			for (DiscretizedFunc relSlipFunc : relSlipFuncs.values())
				maxRelTime = Math.max(maxRelTime, relSlipFunc.getMaxX());
			relative = new RSQSimEventSlipTimeFunc(relPatchTransitions,
					relSlipFuncs, relSlipFuncs, 0, maxRelTime, minVel, maxVel);
		}
		return relative;
	}
	
	public RSQSimEventSlipTimeFunc getTimeScaledFunc(double timeScalar, boolean scaleVelocities) {
		Map<Integer, List<RSQSimStateTime>> scaledPatchTransitions = new HashMap<>();
		for (Integer patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTransList = patchTransitions.get(patchID);
			double patchRelStart = patchTransList.get(0).absoluteTime;
			double newPatchRelStart = patchRelStart/timeScalar;
			double offsetForNoScale = patchRelStart - newPatchRelStart;
//			double newPatchStart = minTime + patchRelStart/timeScalar;
			List<RSQSimStateTime> scaledTrans = new ArrayList<>();
			for (RSQSimStateTime trans : patchTransList) {
				double relStart = trans.relativeTime;
				double duration = trans.hasDuration() ? trans.getDuration() : Double.NaN;
				double newStart, newRelStart;
				double slipVel = trans.velocity;
				if (scaleVelocities) {
					newRelStart = relStart/timeScalar;
					newStart = minTime + newRelStart;
					duration /= timeScalar;
					slipVel *= timeScalar;
				} else {
					newStart = trans.absoluteTime - offsetForNoScale;
					newRelStart = trans.relativeTime - offsetForNoScale;
				}
				RSQSimStateTime newTrans = new RSQSimStateTime(newStart, (float)newRelStart, trans.eventID,
						patchID, trans.state, (float)slipVel);
				if (trans.hasDuration())
					newTrans.setDuration(duration);
				scaledTrans.add(newTrans);
			}
			scaledPatchTransitions.put(patchID, scaledTrans);
		}
		return new RSQSimEventSlipTimeFunc(scaledPatchTransitions);
	}

}
