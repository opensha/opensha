package org.opensha.sha.earthquake.observedEarthquake.Declustering;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;


/**
 * This implements  Gardner-Knopoff Declustering  (Gardner and Knopoff, 1974, Bulletin of the 
 * Seismological Society of America, vol. 64, pages 1363-1367) using the algorithm in the USGS 
 * NSHM Fortran code "cat3.f" provided by Charles Mueller to Ned Field in Nov. 2020.
 * 
 * The code was verified using western US catalog results also provided by Mueller.  Results are not exactly
 * the same, and differences depend mostly on the distance calculation used (~500 events out of 123,000 have 
 * different classifications), with other numerical differences (e.g., interpolation) also playing a very minor 
 * role (~20 out of 123,000).  The implied declustered-catalog MFDs are indistinguishable and negligible with 
 * respect to overall uncertainties.
 * 
 * The USGS distance calculation is provided in the getDistanceKM(*) method implemented here (used for verification).
 * 
 * @author field
 *
 */
public class GardnerKnopoffDeclustering {
	
	static boolean D = false; // debugging flag
	
	ObsEqkRupList fullCatalog, mainshockList, aftershockList, forshockList, bothAftershockAndForeshockList;
	EvenlyDiscretizedFunc timeWindowDaysFunc, distanceWindowKmFunc;
	int[] numAftershocksForRupArray;
	boolean[] rupIsAfteshockArray, rupIsForeshockArray;

	public GardnerKnopoffDeclustering(ObsEqkRupList catalog) {
		
		// remove M<2.5 events
		fullCatalog = new ObsEqkRupList();
		for(ObsEqkRupture rup : catalog)
			if(rup.getMag()>=2.5)
				fullCatalog.add(rup);
		
		if(D) System.out.println("Num Events (M≥2.5): " + fullCatalog.size());
		
		numAftershocksForRupArray = new int[fullCatalog.size()];
		rupIsAfteshockArray = new boolean[fullCatalog.size()]; // these are initialized to false
		rupIsForeshockArray = new boolean[fullCatalog.size()];
		
		distanceWindowKmFunc = new EvenlyDiscretizedFunc(2.5, 12, 0.5);
		distanceWindowKmFunc.set(2.5,19.5);
		distanceWindowKmFunc.set(3.0,22.5);
		distanceWindowKmFunc.set(3.5,26);
		distanceWindowKmFunc.set(4.0,30);
		distanceWindowKmFunc.set(4.5,35);
		distanceWindowKmFunc.set(5.0,40);
		distanceWindowKmFunc.set(5.5,47);
		distanceWindowKmFunc.set(6.0,54);
		distanceWindowKmFunc.set(6.5,61);
		distanceWindowKmFunc.set(7.0,70);
		distanceWindowKmFunc.set(7.5,81);
		distanceWindowKmFunc.set(8.0,94);
		
		timeWindowDaysFunc = new EvenlyDiscretizedFunc(2.5, 12, 0.5);
		timeWindowDaysFunc.set(2.5,6);
		timeWindowDaysFunc.set(3.0,11.5);
		timeWindowDaysFunc.set(3.5,22);
		timeWindowDaysFunc.set(4.0,42);
		timeWindowDaysFunc.set(4.5,83);
		timeWindowDaysFunc.set(5.0,155);
		timeWindowDaysFunc.set(5.5,290);
		timeWindowDaysFunc.set(6.0,510);
		timeWindowDaysFunc.set(6.5,790);
		timeWindowDaysFunc.set(7.0,915);
		timeWindowDaysFunc.set(7.5,960);
		timeWindowDaysFunc.set(8.0,985);
		
		decluster();
	}
	
	private void decluster() {
		
		// search for aftershocks
		for(int i=0;i<fullCatalog.size();i++) {
			ObsEqkRupture rup = fullCatalog.get(i);
			double tempMag = rup.getMag();
			if(tempMag>8.0) tempMag = 8.0;
			double distanceCutoffKm = distanceWindowKmFunc.getInterpolatedY(tempMag);
			long timeCutoffMillis = (long)(timeWindowDaysFunc.getInterpolatedY(tempMag)*24*60*60*1000); // convert to milliseconds
			for(int j=i+1;j<fullCatalog.size();j++) {
				ObsEqkRupture candidateAftershock = fullCatalog.get(j);
				if(rup.getOriginTime() > candidateAftershock.getOriginTime())
					throw new RuntimeException("Error: catalog is not in chronological order");
				
				// check if outside time window
				if((candidateAftershock.getOriginTime()-rup.getOriginTime()) > timeCutoffMillis) {
					break; // break out of this loop and move onto next rup because no subsequent events can be an aftershock either
				}
				
				// check if magnitude is larger
				if(candidateAftershock.getMag() > rup.getMag()) {
					continue; // move onto next candidateAftershock
				}
				
//				double distKm = LocationUtils.linearDistance(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());
				double distKm = LocationUtils.linearDistanceFast(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());
//				double distKm = getDistanceKM(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());

				if(distKm > distanceCutoffKm) {
					continue; // move onto next candidateAftershock
				}
				
				// this is an aftershock
				numAftershocksForRupArray[i] += 1;
				rupIsAfteshockArray[j] = true;
			}
		}
		
//		boolean testShouldBeSkipped = false;
//		int testCounter = 0;
		
		// search for foreshocks
		for(int i=0;i<fullCatalog.size();i++) {
			ObsEqkRupture candidateForeshock = fullCatalog.get(i);
			double tempMag = candidateForeshock.getMag();
			if(tempMag>8.0) tempMag = 8.0;
			double distanceCutoffKm = distanceWindowKmFunc.getInterpolatedY(tempMag);
			long timeCutoffMillis = (long)(timeWindowDaysFunc.getInterpolatedY(tempMag)*24*60*60*1000); // convert to milliseconds
			for(int j=i+1;j<fullCatalog.size();j++) {
				if(rupIsAfteshockArray[j] == true)
					continue; // skip because aftershocks cannot have foreshocks
				
//				testShouldBeSkipped=false;
//				if(rupIsAfteshockArray[j] == true)
//					testShouldBeSkipped=true; 
				
				
				ObsEqkRupture rup = fullCatalog.get(j);
				if(candidateForeshock.getOriginTime() > rup.getOriginTime())
					throw new RuntimeException("Error: catalog is not in chronological order");
				
				// check if outside time window
				if((rup.getOriginTime()-candidateForeshock.getOriginTime()) > timeCutoffMillis) {
					break; // break out of this loop and move onto next candidateForeshock
				}
				
				// foreshock mag must be smaller
				if(candidateForeshock.getMag() > rup.getMag()) {
					continue; // move onto next rup
				}
				
//				double distKm = LocationUtils.linearDistance(candidateForeshock.getHypocenterLocation(), rup.getHypocenterLocation());
				double distKm = LocationUtils.linearDistanceFast(candidateForeshock.getHypocenterLocation(), rup.getHypocenterLocation());
//				double distKm = getDistanceKM(candidateForeshock.getHypocenterLocation(), rup.getHypocenterLocation());
				if(distKm > distanceCutoffKm) {
					continue; // move onto next rup
				}
				

//				if(testShouldBeSkipped && rupIsAfteshockArray[i]==false && rupIsForeshockArray[i] == false) { // latter test avoids double counting
//					testCounter+=1; // this would have been classified as main shock
//					System.out.println(i);
//				}

				// it's a foreshock
				rupIsForeshockArray[i] = true;				
			}
		}
		
		mainshockList = new ObsEqkRupList();
		aftershockList = new ObsEqkRupList();
		forshockList = new ObsEqkRupList();
		bothAftershockAndForeshockList = new ObsEqkRupList();

		for(int i=0;i<fullCatalog.size();i++) {
			if(rupIsAfteshockArray[i] == true && rupIsForeshockArray[i] == true)
				bothAftershockAndForeshockList.add(fullCatalog.get(i));
			else if (rupIsAfteshockArray[i] == true)
				aftershockList.add(fullCatalog.get(i));
			else if (rupIsForeshockArray[i] == true)
				forshockList.add(fullCatalog.get(i));
			else
				mainshockList.add(fullCatalog.get(i));
						
		}
		if(D) {
			System.out.println("Num Main Shocks: " + mainshockList.size());
			System.out.println("Num Aftershocks: " + aftershockList.size());
			System.out.println("Num Foreshocks: " + forshockList.size());
			System.out.println("Num Both Aft. & Fore.: " + bothAftershockAndForeshockList.size());	
			
//			System.out.println("testCounter="+testCounter);
		}
		
	}
	
	
	/**
	 * This is an alternative articulated by Andrea Llenos.  It's more concise, but allows 
	 * an aftershock to have a foreshock, which leads to an almost equal number of aftershocks
	 * and foreshocks (most are both), which dosn't seem correct.
	 */
	private void decluster_alt() {
		
		// search for aftershocks
		for(int i=0;i<fullCatalog.size();i++) {
			ObsEqkRupture rup = fullCatalog.get(i);
			double tempMag = rup.getMag();
			if(tempMag>8.0) tempMag = 8.0;
			double distanceCutoffKm = distanceWindowKmFunc.getInterpolatedY(tempMag);
			long timeCutoffMillis = (long)(timeWindowDaysFunc.getInterpolatedY(tempMag)*24*60*60*1000); // convert to milliseconds
			for(int j=i+1;j<fullCatalog.size();j++) {
				ObsEqkRupture candidateAftershock = fullCatalog.get(j);
				if(rup.getOriginTime() > candidateAftershock.getOriginTime())
					throw new RuntimeException("Error: catalog is not in chronological order");
				
				// check if outside time window
				if((candidateAftershock.getOriginTime()-rup.getOriginTime()) > timeCutoffMillis) {
					break; // break out of this loop and move onto next rup because no subsequent events can be an aftershock either
				}
				
//				double distKm = LocationUtils.linearDistance(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());
				double distKm = LocationUtils.linearDistanceFast(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());
//				double distKm = getDistanceKM(rup.getHypocenterLocation(), candidateAftershock.getHypocenterLocation());

				if(distKm > distanceCutoffKm) {
					continue; // move onto next candidateAftershock
				}

				
				// check if 1st magnitude is larger
				if(rup.getMag() >= candidateAftershock.getMag()) {
					rupIsAfteshockArray[j] = true;
				}
				else {
					rupIsForeshockArray[i] = true;
				}
//				// THIS IS DIFFERENT - A TEST **************************
//				if(rup.getMag() <= candidateAftershock.getMag()) {
//					rupIsForeshockArray[i] = true;
//				}
				
			}
		}
				
		mainshockList = new ObsEqkRupList();
		aftershockList = new ObsEqkRupList();
		forshockList = new ObsEqkRupList();
		bothAftershockAndForeshockList = new ObsEqkRupList();

		for(int i=0;i<fullCatalog.size();i++) {
			if(rupIsAfteshockArray[i] == true && rupIsForeshockArray[i] == true)
				bothAftershockAndForeshockList.add(fullCatalog.get(i));
			else if (rupIsAfteshockArray[i] == true)
				aftershockList.add(fullCatalog.get(i));
			else if (rupIsForeshockArray[i] == true)
				forshockList.add(fullCatalog.get(i));
			else
				mainshockList.add(fullCatalog.get(i));
						
		}
		if(D) {
			System.out.println("Num Main Shocks: " + mainshockList.size());
			System.out.println("Num Aftershocks: " + aftershockList.size());
			System.out.println("Num Foreshocks: " + forshockList.size());
			System.out.println("Num Both Aft. & Fore.: " + bothAftershockAndForeshockList.size());			
		}
		
	}

	
	
	
	/**
	 * This returns the declustered catalog
	 * @return
	 */
	public ObsEqkRupList getDeclusteredCatalog() {
		return mainshockList;
	}
	
	/**
	 * This is a convenience method to avoid having to instantiate this class if 
	 * only the declusted catalog is desired.
	 * @param rupList - must be in chronological order
	 * @return
	 */
	public static ObsEqkRupList getDeclusteredCatalog(ObsEqkRupList rupList) {
		GardnerKnopoffDeclustering gk_decluster = new GardnerKnopoffDeclustering(rupList);
		return gk_decluster.getDeclusteredCatalog();
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	
	/**
	 * This is exactly how the USGS Fortran code computes distance
	 * @param loc1
	 * @param loc2
	 * @return
	 */
	public static double getDistanceKM(Location loc1, Location loc2) {
//	      subroutine delaz(sorlat,sorlon,stnlat,stnlon,delta,az,baz)
		double sorlat = loc1.getLatitude();
		double sorlon = loc1.getLongitude();
		double stnlat = loc2.getLatitude();
		double stnlon = loc2.getLongitude();
	    double coef= Math.atan(1.)/45.; 
	    double xlat= sorlat*coef;
	    double xlon= sorlon*coef;
	    double st0= Math.cos(xlat);
	    double ct0= Math.sin(xlat);
	    double phi0= xlon;
	    xlat= stnlat*coef;
	    xlon= stnlon*coef;
	    double st1= Math.cos(xlat);
	    double ct1= Math.sin(xlat);
	    double sdlon= Math.sin(xlon-phi0);
	    double cdlon= Math.cos(xlon-phi0);
	    double cdelt= st0*st1*cdlon+ct0*ct1;
	    double x= st0*ct1-st1*ct0*cdlon;
	    double y= st1*sdlon;
	    double sdelt= Math.sqrt(x*x+y*y);
	    double delta= Math.atan2(sdelt,cdelt);
	    delta= delta/coef;
	    double az= Math.atan2(y,x);
	    az= az/coef;
	    x= st1*ct0-st0*ct1*cdlon;
	    y= -sdlon*st0;
	    double baz= Math.atan2(y,x);
	    baz= baz/coef;
	    delta= delta*111.1;
	    return delta;

	}


}
