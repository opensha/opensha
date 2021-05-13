package org.opensha.sha.earthquake.observedEarthquake.magComplete;

import java.util.List;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.collect.Lists;

public class Helmstetter2006_TimeDepMc implements TimeDepMagComplete {
	
	private static final double MILLISEC_PER_DAY = (double)(1000l*60*60*24);
	
	private List<? extends ObsEqkRupture> mainshocks;
	private double minMag;

	public Helmstetter2006_TimeDepMc(ObsEqkRupture mainshock, double minMag) {
		this(Lists.newArrayList(mainshock), minMag);
	}
	
	public Helmstetter2006_TimeDepMc(List<? extends ObsEqkRupture> mainshocks, double minMag) {
		this.mainshocks = mainshocks;
		this.minMag = minMag;
	}
	
	public double getMinMagThreshold() {
		return minMag;
	}
	
	public List<? extends ObsEqkRupture> getMainshocksList() {
		return mainshocks;
	}
	
	@Override
	public double calcTimeDepMc(ObsEqkRupture rup) {
		return calcTimeDepMc(rup.getOriginTime());
	}
	
	@Override
	public double calcTimeDepMc(long time) {
		return calcTimeDepMc(mainshocks, time, minMag);
	}
	
	@Override
	public boolean isAboveTimeDepMc(ObsEqkRupture rup) {
		return rup.getMag() >= calcTimeDepMc(rup);
	}
	
	@Override
	public ObsEqkRupList getFiltered(List<? extends ObsEqkRupture> rups) {
		ObsEqkRupList filtered = new ObsEqkRupList();
		
		for (ObsEqkRupture rup : rups)
			if (isAboveTimeDepMc(rup))
				filtered.add(rup);
		
		return filtered;
	}
	
	public static double calcTimeDepMc(List<? extends ObsEqkRupture> mainshocks, long time, double minMag) {
		double maxMc = minMag;
		for (ObsEqkRupture mainshock : mainshocks) {
			double myMc = calcTimeDepMc(mainshock, time, minMag);
			if (Double.isFinite(myMc))
				maxMc = Math.max(maxMc, myMc);
		}
		return maxMc;
	}
	
	public static double calcTimeDepMc(ObsEqkRupture mainshock, long time, double minMag) {
		if (time <= mainshock.getOriginTime())
			return Double.NaN;
		double daysSinceMainshock = (double)(time - mainshock.getOriginTime())/MILLISEC_PER_DAY;
		return calcTimeDepMc(mainshock.getMag(), daysSinceMainshock, minMag);
	}
	
	public static double calcTimeDepMc(double mainshockMag, double daysSinceMainshock, double minMag) {
		return Math.max(minMag, mainshockMag - 4.5 - 0.75*Math.log10(daysSinceMainshock));
	}

}
