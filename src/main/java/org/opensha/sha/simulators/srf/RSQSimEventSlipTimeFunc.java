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
	
	private Map<Integer, Double> slipVels;
	private Map<Integer,  List<RSQSimStateTime>> patchTransitions;
	private Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
	
	private double minTime;
	private double maxTime;
	
	private double minVel;
	private double maxVel;
	
	private boolean variableSlipSpeed;
	
	/**
	 * @param patchTransitions state transitions for this event
	 * @param slipVels patch slip velocities in m/s (used if variableSlipSpeed == false)
	 * @param variableSlipSpeed flag for variable slip speed (from RSQSimStateTime instances)
	 */
	public RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions, Map<Integer, Double> slipVels,
			boolean variableSlipSpeed) {
		if (variableSlipSpeed)
			// make sure we don't accidentally use this
			slipVels = null;
		Map<Integer, DiscretizedFunc> slipFuncs = new HashMap<>();
		double minTime = Double.POSITIVE_INFINITY;
		double maxTime = Double.NEGATIVE_INFINITY;
		double minVel = Double.POSITIVE_INFINITY;
		double maxVel = Double.NEGATIVE_INFINITY;
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
					double slipVel;
					if (variableSlipSpeed) {
						slipVel = stateTime.getVelocity();
						Preconditions.checkState(Double.isFinite(slipVel),"Bad slip velocity with variableSlipSpeed=true. "
								+ "Did we read in a regular transitions file instead? SlipVel=%s", slipVel);
					} else {
						slipVel = slipVels.get(patchID);
					}
					minVel = Math.min(minVel, slipVel);
					maxVel = Math.max(maxVel, slipVel);
					double slip = slipVel * stateTime.getDuration();
					curSlip += slip;
					slipFunc.set(stateTime.getEndTime(), curSlip);
				}
			}
			slipFuncs.put(patchID, slipFunc);
			minTime = Math.min(minTime, patchTrans.get(0).getStartTime());
			maxTime = Math.max(maxTime, patchTrans.get(patchTrans.size()-1).getEndTime());
		}
		init(patchTransitions, slipVels, slipFuncs, minTime, maxTime, minVel, maxVel, variableSlipSpeed);
	}
	
	private RSQSimEventSlipTimeFunc(Map<Integer, List<RSQSimStateTime>> patchTransitions, Map<Integer, Double> slipVels,
			Map<Integer, DiscretizedFunc> slipFuncs, double minTime, double maxTime, double minVel, double maxVel,
			boolean variableSlipSpeed) {
		init(patchTransitions, slipVels, slipFuncs, minTime, maxTime, minVel, maxVel, variableSlipSpeed);
	}
	
	private void init(Map<Integer, List<RSQSimStateTime>> patchTransitions, Map<Integer, Double> slipVels,
			Map<Integer, DiscretizedFunc> slipFuncs, double minTime, double maxTime,
			double minVel, double maxVel, boolean variableSlipSpeed) {
		this.slipVels = slipVels;
		this.patchTransitions = patchTransitions;
		this.slipFuncs = slipFuncs;
		this.minTime = minTime;
		this.maxTime = maxTime;
		this.minVel = minVel;
		this.maxVel = maxVel;
		this.variableSlipSpeed = variableSlipSpeed;
	}
	
	public RSQSimStateTime getStateTime(int patchID, double time) {
		if (patchTransitions.containsKey(patchID)) {
			for (RSQSimStateTime stateTimes : patchTransitions.get(patchID))
				if (stateTimes.containsTime(time))
					return stateTimes;
		}
		return null;
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
	
	public double getMaxSlipVel() {
		return maxVel;
	}
	
	public double getMinSlipVel() {
		return minVel;
	}
	
	public double getVelocity(RSQSimStateTime stateTime) {
		if (stateTime.getState() == RSQSimState.EARTHQUAKE_SLIP) {
			double slipVel;
			if (variableSlipSpeed) {
				slipVel = stateTime.getVelocity();
				Preconditions.checkState(Double.isFinite(slipVel),"Bad slip velocity with variableSlipSpeed=true. "
						+ "Did we read in a regular transitions file instead? SlipVel=%s", slipVel);
			} else {
				slipVel = slipVels.get(stateTime.getPatchID());
			}
			return slipVel;
		}
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
					relTrans.add(new RSQSimStateTime(patchID, trans.getStartTime()-minTime, trans.getEndTime()-minTime,
							trans.getState(), trans.getVelocity()));
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
			relative = new RSQSimEventSlipTimeFunc(relPatchTransitions, slipVels, relSlipFuncs, 0, maxTime-minTime,
					minVel, maxVel, variableSlipSpeed);
		}
		return relative;
	}
	
	public RSQSimEventSlipTimeFunc getTimeScaledFunc(double timeScalar, boolean scaleVelocities) {
		Map<Integer, Double> slipVels;
		if (scaleVelocities && !variableSlipSpeed) {
			slipVels = new HashMap<>(this.slipVels);
			for (Integer patchID : slipVels.keySet())
				slipVels.put(patchID, slipVels.get(patchID)*timeScalar);
		} else {
			slipVels = this.slipVels;
		}
		Map<Integer, List<RSQSimStateTime>> scaledPatchTransitions = new HashMap<>();
		for (Integer patchID : patchTransitions.keySet()) {
			double patchRelStart = getTimeOfFirstSlip(patchID) - minTime;
			double newPatchRelStart = patchRelStart/timeScalar;
			double offsetForNoScale = patchRelStart - newPatchRelStart;
//			double newPatchStart = minTime + patchRelStart/timeScalar;
			List<RSQSimStateTime> scaledTrans = new ArrayList<>();
			for (RSQSimStateTime trans : patchTransitions.get(patchID)) {
				double relStart = trans.getStartTime() - minTime;
				double relEnd = trans.getEndTime() - minTime;
				double newStart, newEnd;
				double slipVel = trans.getVelocity();
				if (scaleVelocities) {
					newStart = minTime + relStart/timeScalar;
					newEnd = minTime + relEnd/timeScalar;
					if (variableSlipSpeed)
						slipVel *= timeScalar;
				} else {
					newStart = trans.getStartTime() - offsetForNoScale;
					newEnd = newStart + (relEnd - relStart);
				}
				scaledTrans.add(new RSQSimStateTime(patchID, newStart, newEnd, trans.getState(), slipVel));
			}
			scaledPatchTransitions.put(patchID, scaledTrans);
		}
		return new RSQSimEventSlipTimeFunc(scaledPatchTransitions, slipVels, variableSlipSpeed);
	}

}
