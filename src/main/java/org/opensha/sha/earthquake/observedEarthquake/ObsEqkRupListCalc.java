package org.opensha.sha.earthquake.observedEarthquake;

import java.util.Collections;

import org.opensha.sha.earthquake.util.EqkRuptureMagComparator;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

/**
 * <p>Title: ObsEqkRupListCalc</p>
 *
 * <p>Description: This class provides users with capability to operate on
 * Observed Eqk Rupture list. all the functions defined in this class are static,
 * so user does not have to create the object of the class to call the method.
 * </p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */
public class ObsEqkRupListCalc {

	/**
	 * Returns the Mean Mag for the given list of Events.
	 * @param obsEqkEvents ObsEqkRupList list of Observed Events
	 * @return double meean Magnitude
	 */
	public static double getMeanMag(ObsEqkRupList obsEqkEvents){
		int size = obsEqkEvents.size();
		double mag =0;
		for(int i=0;i<size;++i)
			mag += obsEqkEvents.get(i).getMag();
		return (mag/size);
	}

	/**
	 * Returns the minimum magnitude for the observed Eqk Rupture events.
	 * @param obsEqkEvents ObsEqkRupList list of observed eqk events
	 * @return double min-mag
	 */
	public static double getMinMag(ObsEqkRupList obsEqkEvents) {
		Double minMag = Collections.min(obsEqkEvents,new EqkRuptureMagComparator()).getMag();
		return minMag;
	}


	/**
	 * Returns the maximum magnitude for the observed Eqk Rupture events.
	 * @param obsEqkEvents ObsEqkRupList list of observed eqk events
	 * @return double max-mag
	 */
	public static double getMaxMag(ObsEqkRupList obsEqkEvents) {
		Double maxMag = Collections.max(obsEqkEvents,new EqkRuptureMagComparator()).getMag();
		return maxMag;
	}

	/**
	 * Returns the difference in origin time of Observed Eqk events in chronological
	 * order. Difference is in the millisec.
	 * @param obsEqkEvents ObsEqkRupList Observed Eqk Event List
	 * @return long[] returns the long array of difference in time between all
	 * the observed events after ordering them based on their origin time.
	 *
	 * Note : Returns array of long  which is the differnce in the origin time
	 * of 2 events after they are converted to milliseconds.
	 */
	public static long[] getInterEventTimes(ObsEqkRupList obsEqkEvents) {

		obsEqkEvents.sortByOriginTime();
		int size = obsEqkEvents.size();
		long[] interEventTimes = new long[size-1];

		for(int i=0;i<size -1;++i){
			long time = obsEqkEvents.get(i+1).getOriginTime() -
					obsEqkEvents.get(i).getOriginTime();
			interEventTimes[i] = time;
		}

		return interEventTimes;
	}

	public static IncrementalMagFreqDist getMagFreqDist(ObsEqkRupList obsEqkEvents) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public static IncrementalMagFreqDist getMagNumDist(ObsEqkRupList obsEqkEvents,
			double minMag, int numMag, double deltaMag) {
		IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(minMag, numMag, deltaMag);
		double min = mfd.getMinX()-0.5*mfd.getDelta();
		double max = mfd.getMaxX()+0.5*mfd.getDelta();
		
		int numBelow = 0;
		int numAbove = 0;
		
		for (ObsEqkRupture rup : obsEqkEvents) {
			double mag = rup.getMag();
			if (mag < min)
				numBelow++;
			else if (mag > max)
				numAbove++;
			else
				mfd.add(mfd.getClosestXIndex(mag), 1d);
		}
		
		if (numBelow > 0)
			System.out.println("Skipped "+numBelow+" events in MFD below M="+(float)min);
		Preconditions.checkState(numAbove == 0, "MFD max mag too small!");
		
		return mfd;
	}











}
