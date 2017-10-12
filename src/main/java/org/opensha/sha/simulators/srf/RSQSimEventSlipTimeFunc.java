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
	
	private double slipVel;
	private Map<Integer,  List<RSQSimStateTime>> patchTransitions;
	private Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
	
	private double minTime;
	private double maxTime;
	
	/**
	 * @param patchTransitions state transitions for this event
	 * @param slipVel slip velocity in m/s
	 */
	public RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions, double slipVel) {
		Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
		double minTime = Double.POSITIVE_INFINITY;
		double maxTime = Double.NEGATIVE_INFINITY;
		for (int patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTrans = patchTransitions.get(patchID);
			if (patchTrans.isEmpty())
				continue;
			DiscretizedFunc slipFunc = new ArbitrarilyDiscretizedFunc();
			double curSlip = 0d;
			for (RSQSimStateTime stateTime : patchTrans) {
				if (stateTime.getState() == RSQSimState.EARTHQUAKE_SLIP) {
					// slipping
					slipFunc.set(stateTime.getStartTime(), curSlip);
					double slip = slipVel * stateTime.getDuration();
					curSlip += slip;
					slipFunc.set(stateTime.getEndTime(), curSlip);
				}
			}
			slipFuncs.put(patchID, slipFunc);
			minTime = Math.min(minTime, patchTrans.get(0).getStartTime());
			maxTime = Math.max(maxTime, patchTrans.get(patchTrans.size()-1).getEndTime());
		}
		init(patchTransitions, slipVel, slipFuncs, minTime, maxTime);
	}
	
	private RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions, double slipVel,
			Map<Integer, DiscretizedFunc> slipFuncs, double minTime, double maxTime) {
		init(patchTransitions, slipVel, slipFuncs, minTime, maxTime);
	}
	
	private void init(Map<Integer, List<RSQSimStateTime>> patchTransitions, double slipVel,
			Map<Integer, DiscretizedFunc> slipFuncs, double minTime, double maxTime) {
		this.slipVel = slipVel;
		this.patchTransitions = patchTransitions;
		this.slipFuncs = slipFuncs;
		this.minTime = minTime;
		this.maxTime = maxTime;
	}
	
	public RSQSimState getState(int patchID, double time) {
		if (patchTransitions.containsKey(patchID)) {
			for (RSQSimStateTime stateTimes : patchTransitions.get(patchID))
				if (stateTimes.containsTime(time))
					return stateTimes.getState();
		}
		return null;
	}
	
	List<RSQSimStateTime> getTransitions(int patchID) {
		return patchTransitions.get(patchID);
	}
	
	DiscretizedFunc getSlipFunc(int patchID) {
		return slipFuncs.get(patchID);
	}
	
	public double getSlipVelocity() {
		return slipVel;
	}
	
	/**
	 * @param patchID
	 * @param time absolute catalog time in seconds
	 * @return slip velocity if in EARTHQUAKE_SLIP state, 0 if in other state, or NaN if patch not applicable
	 */
	public double getVelocity(int patchID, double time) {
		if (!patchTransitions.containsKey(patchID))
			return Double.NaN;
		if (getState(patchID, time) == RSQSimState.EARTHQUAKE_SLIP)
			return slipVel;
		return 0d;
	}
	
	/**
	 * @param patchID
	 * @return time of first slip (either earthquake or nucleating slip) for the given patch
	 */
	public double getTimeOfFirstSlip(int patchID) {
		for (RSQSimStateTime trans : patchTransitions.get(patchID))
			if (trans.getState() == RSQSimState.EARTHQUAKE_SLIP || trans.getState() == RSQSimState.NUCLEATING_SLIP)
				// TODO nucleating slip?
				return trans.getStartTime();
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
			if (trans.getState() == RSQSimState.EARTHQUAKE_SLIP || trans.getState() == RSQSimState.NUCLEATING_SLIP)
				// TODO nucleating slip?
				return trans.getEndTime();
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
				for (RSQSimStateTime trans : patchTransitions.get(patchID))
					relTrans.add(new RSQSimStateTime(trans.getStartTime()-minTime, trans.getEndTime()-minTime, trans.getState()));
				relPatchTransitions.put(patchID, relTrans);
			}
			Map<Integer, DiscretizedFunc> relSlipFuncs = new HashMap<>();
			for (Integer patchID : slipFuncs.keySet()) {
				DiscretizedFunc slipFunc = slipFuncs.get(patchID);
				DiscretizedFunc relSlipFunc = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : slipFunc)
					relSlipFunc.set(pt.getX()-minTime, pt.getY());
				relSlipFuncs.put(patchID, relSlipFunc);
			}
			relative = new RSQSimEventSlipTimeFunc(relPatchTransitions, slipVel, relSlipFuncs, 0, maxTime-minTime);
		}
		return relative;
	}

}
