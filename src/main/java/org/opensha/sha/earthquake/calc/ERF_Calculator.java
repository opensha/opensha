/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.earthquake.calc;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.RupInRegionCache;
import org.opensha.sha.faultSurface.RupNodesCache;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.primitives.Ints;


/**
 * <p>Title: ERF_Calculator</p>
 * <p>Description: This for various calculations related to ERFs.  
 * This is to replace ERF2GriddedSeisRatesCalc (which is overly complex)
 * </p>
 * @author Ned Field
 * @version 1.0
 */
public class ERF_Calculator {


	/**
	 * default class Constructor.
	 */
	public ERF_Calculator() {}

	/**
	 * This computes the annualized total magnitude frequency distribution for the ERF.
	 * Magnitudes that are out of the range specified are ignored.
	 * @param eqkRupForecast - assumed to be updated before being passed in
	 * @param min - for MagFreqDist x axis
	 * @param max - for MagFreqDist x axis
	 * @param num - for MagFreqDist x axis
	 * @param preserveRates - if true rates are assigned to nearest discrete magnitude 
	 * without modification,if false rates are adjusted to preserve moment rate.
	 * @return
	 */
	public static SummedMagFreqDist getTotalMFD_ForERF(ERF eqkRupForecast, double min,double max,int num, boolean preserveRates) {
		return getTotalMFD_ForSourceRange(eqkRupForecast, min, max, num, preserveRates, 
				0, eqkRupForecast.getNumSources()-1);
	}


	/**
	 * This computes the annualized total magnitude frequency distribution for the ERF for
	 * the specified range of sources. Magnitudes that are out of the range specified are 
	 * ignored.
	 * @param eqkRupForecast - assumed to be updated before being passed in
	 * @param min - for MagFreqDist x axis
	 * @param max - for MagFreqDist x axis
	 * @param num - for MagFreqDist x axis
	 * @param preserveRates - if true rates are assigned to nearest discrete magnitude
	 *        without modification,if false rates are adjusted to preserve moment rate.
	 * @param firstSourceIndex - first index of sources to be included 
	 * @param lastSourceIndex - last index of sources to be included 
	 * @return
	 */
	public static SummedMagFreqDist getTotalMFD_ForSourceRange(ERF eqkRupForecast, double min,double max,int num, 
			boolean preserveRates, int firstSourceIndex, int lastSourceIndex) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(min,max,num);
		double duration = eqkRupForecast.getTimeSpan().getDuration();
		for(int s=firstSourceIndex;s<=lastSourceIndex;s++) {
			ProbEqkSource src = eqkRupForecast.getSource(s);
			for(int r=0;r<src.getNumRuptures();r++) {
				ProbEqkRupture rup = src.getRupture(r);
				mfd.addResampledMagRate(rup.getMag(), rup.getMeanAnnualRate(duration), preserveRates);
			}
		}
		return mfd;
	}



	/**
	 * This computes the annualized total magnitude frequency distribution for the ERF.
	 * Magnitudes that are out of the range specified are ignored.
	 * @param src - an earthquake source
	 * @param duration - the forecast duration (e.g., from a time span)
	 * @param min - for MagFreqDist x axis
	 * @param max - for MagFreqDist x axis
	 * @param num - for MagFreqDist x axis
	 * @param preserveRates - if true rates are assigned to nearest discrete magnitude 
	 * without modification,if false rates are adjusted to preserve moment rate.
	 * @return
	 */
	public static SummedMagFreqDist getTotalMFD_ForSource(ProbEqkSource src, double duration, double min,double max,int num, boolean preserveRates) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(min,max,num);
		for(int r=0;r<src.getNumRuptures();r++) {
			ProbEqkRupture rup = src.getRupture(r);
			mfd.addResampledMagRate(rup.getMag(), rup.getMeanAnnualRate(duration), preserveRates);
		}
		return mfd;
	}
	
	
	
	/**
	 * This returns the total equivalent annual rate for the source for ruptures with magnitude
	 * greater than or equal to the given magThres
	 * @param src
	 * @param duration
	 * @param magThresh
	 * @return
	 */
	public static double getTotalRateAboveMagForSource(ProbEqkSource src, double duration, double magThresh) {
		double totRate = 0;
		for(int r=0;r<src.getNumRuptures();r++) {
			ProbEqkRupture rup = src.getRupture(r);
			if(rup.getMag() >= magThresh)
				totRate += rup.getMeanAnnualRate(duration);
		}
		return totRate;
	}


	/**
	 * This returns the total equivalent annual moment rate for the source
	 * @param src
	 * @param duration
	 * @return
	 */
	public static double getTotalMomentRateForSource(ProbEqkSource src, double duration) {
		double totMoRate = 0;
		for(int r=0;r<src.getNumRuptures();r++) {
			ProbEqkRupture rup = src.getRupture(r);
			totMoRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(duration);
		}
		return totMoRate;
	}


	/**
	 * This computes the total nucleation magnitude frequency distribution (equivalent poisson rates) for the
	 * ERF inside the region (only the fraction of each rupture inside the region is included)
	 * @param erf
	 * @param region
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param preserveRates - this tells whether to preserve rates or preserve moment rates
	 * @return
	 */
	public static SummedMagFreqDist getMagFreqDistInRegion(ERF erf, Region region,
			double minMag,int numMag,double deltaMag, boolean preserveRates) {

		SummedMagFreqDist magFreqDist = new SummedMagFreqDist(minMag, numMag, deltaMag);
		double duration = erf.getTimeSpan().getDuration();
		for (int s = 0; s < erf.getNumSources(); ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				double mag = rupture.getMag();
				double equivRate = rupture.getMeanAnnualRate(duration);
				LocationList surfLocs = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				double ptRate = equivRate/surfLocs.size();
				ListIterator<Location> it = surfLocs.listIterator();
				while (it.hasNext()) {
					//discard the pt if outside the region 
					if (!region.contains(it.next()))
						continue;
					magFreqDist.addResampledMagRate(mag, ptRate, preserveRates);
				}
			}
		}
		return magFreqDist;
	}
	
	/**
	 * This computes the total nucleation magnitude frequency distribution (equivalent poisson rates) for the
	 * ERF inside the region (only the fraction of each rupture inside the region is included, where here
	 * the surface is approximated by the trace)
	 * @param erf
	 * @param region
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param preserveRates - this tells whether to preserve rates or preserve moment rates
	 * @return
	 */
	public static SummedMagFreqDist getMagFreqDistInRegionFaster(ERF erf, Region region,
			double minMag,int numMag,double deltaMag, boolean preserveRates) {

		SummedMagFreqDist magFreqDist = new SummedMagFreqDist(minMag, numMag, deltaMag);
		double duration = erf.getTimeSpan().getDuration();
		for (int s = 0; s < erf.getNumSources(); ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				double mag = rupture.getMag();
				double equivRate = rupture.getMeanAnnualRate(duration);
				LocationList surfLocs = rupture.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
				double ptRate = equivRate/surfLocs.size();
				ListIterator<Location> it = surfLocs.listIterator();
				while (it.hasNext()) {
					//discard the pt if outside the region 
					if (!region.contains(it.next()))
						continue;
					magFreqDist.addResampledMagRate(mag, ptRate, preserveRates);
				}
			}
		}
		return magFreqDist;
	}




	/**
	 * This computes the total participation magnitude frequency distribution (equivalent poisson rates) for the
	 * ERF inside the region (rupture mag and rate is included if any part of the rupture is inside)
	 * @param erf
	 * @param region
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param preserveRates - this tells whether to preserve rates or preserve moment rates
	 * @return
	 */
	public static SummedMagFreqDist getParticipationMagFreqDistInRegion(ERF erf, Region region,
			double minMag,int numMag,double deltaMag, boolean preserveRates) {
		return getParticipationMagFreqDistInRegion(erf, region, minMag, numMag, deltaMag, preserveRates, null);
	}
	
	public static SummedMagFreqDist getParticipationMagFreqDistInRegion(ERF erf, Region region,
			double minMag,int numMag,double deltaMag, boolean preserveRates, RupInRegionCache rupInRegionCache) {

		SummedMagFreqDist magFreqDist = new SummedMagFreqDist(minMag, numMag, deltaMag);
		double duration = erf.getTimeSpan().getDuration();
		int srcIndex = 0;
		for (ProbEqkSource source : erf) {
			int rupIndex = 0;
			for (ProbEqkRupture rupture : source) {
				// see if any point is inside
				boolean isInside = false;
				if (rupInRegionCache == null)
					for(Location loc : rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
						if (region.contains(loc)) {
							isInside = true;
							break;
						}
					}
				else
					isInside = rupInRegionCache.isRupInRegion(erf, source, rupture, srcIndex, rupIndex, region);
				
				if(isInside) {
					double equivRate = rupture.getMeanAnnualRate(duration);
					magFreqDist.addResampledMagRate(rupture.getMag(), equivRate, preserveRates);
				}
				
				rupIndex++;
			}
			srcIndex++;
		}
		return magFreqDist;
	}




	/**
	 * This computes the rate of event (equivalent poisson rates) for the
	 * ERF inside the region (only the fraction of each rupture inside the region is included)
	 * @param erf
	 * @param region
	 * @param minMag - ruptures with magnitudes less than this are not included in the total
	 * @return
	 */
	public static double getTotalRateInRegion(ERF erf, Region region, double minMag) {
		double duration = erf.getTimeSpan().getDuration();
		double totRate=0;
		for (int s = 0; s < erf.getNumSources(); ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				if(rupture.getMag()>= minMag) {
					double fractionInside = RegionUtils.getFractionInside(region, rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface());
					totRate += fractionInside*rupture.getMeanAnnualRate(duration);
				}
			}
		}
		return totRate;
	}


	/**
	 * This computes the total moment rate (Nm/yr), from equivalent poisson rates, for the
	 * ERF inside the region (only the fraction of each rupture inside the region is included)
	 * @param erf
	 * @param region - set as null to get ERF total moment rate
	 * @return
	 */
	public static double getTotalMomentRateInRegion(ERF erf, Region region) {
		double duration = erf.getTimeSpan().getDuration();
		double totMoRate=0;
		double totMoRateOutside=0;
		for (int s = 0; s < erf.getNumSources(); ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				double rupMoment = MagUtils.magToMoment(rupture.getMag());
				if(region != null) {
					double fractionInside = RegionUtils.getFractionInside(region, rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface());
					totMoRate += fractionInside*rupMoment*rupture.getMeanAnnualRate(duration);
					totMoRateOutside += (1.0-fractionInside)*rupMoment*rupture.getMeanAnnualRate(duration);
				}
				else {
					totMoRate += rupMoment*rupture.getMeanAnnualRate(duration);
				}
			}
		}
		if(region != null) System.out.println("totMoRateOutside="+totMoRateOutside);
		return totMoRate;
	}



	/**
	 * This computes the  magnitude frequency distribution (equivalent poisson rates) for each
	 * location in the supplied GriddedRegion and ERF.  This assumes a uniform distribution of 
	 * nucleations on each rupture.  Ruptures that fall outside the region are ignored.  The
	 * indices for the returned array list are the same as for the GriddedRegion.
	 * @param erf
	 * @param griddedRegion
	 * @param minMag
	 * @param numMag
	 * @param deltaMag
	 * @param preserveRates - this tells whether to preserve rates or preserve moment rates
	 * @return
	 */
	public static ArrayList<SummedMagFreqDist> getMagFreqDistsAtLocsInRegion(ERF erf, GriddedRegion griddedRegion,
			double minMag,int numMag,double deltaMag, boolean preserveRates) {

		ArrayList<SummedMagFreqDist> magFreqDists = new ArrayList<SummedMagFreqDist>();
		for(Location loc:griddedRegion)
			magFreqDists.add(new SummedMagFreqDist(minMag, numMag, deltaMag));

		SummedMagFreqDist unassignedMFD = new SummedMagFreqDist(minMag, numMag, deltaMag);

		double duration = erf.getTimeSpan().getDuration();
		for (int s = 0; s < erf.getNumSources(); ++s) {
			ProbEqkSource source = erf.getSource(s);
			for (int r = 0; r < source.getNumRuptures(); ++r) {
				ProbEqkRupture rupture = source.getRupture(r);
				double mag = rupture.getMag();
				double equivRate = rupture.getMeanAnnualRate(duration);
				LocationList surfLocs = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				double ptRate = equivRate/surfLocs.size();
				ListIterator<Location> it = surfLocs.listIterator();
				while (it.hasNext()) {
					Location loc = it.next();
					int index = griddedRegion.indexForLocation(loc);
					if(index >=0)
						magFreqDists.get(index).addResampledMagRate(mag, ptRate, preserveRates);
					else
						unassignedMFD.addResampledMagRate(mag, ptRate, preserveRates);
				}
			}
		}
		//	  System.out.println(unassignedMFD.getCumRateDistWithOffset());
		return magFreqDists;
	}


	/**
	 * This returns the b-value between min_bValMag and max_bValMag at each point in the region for the given ERF.
	 * Set min_bValMag (or max_bValMag) as Double.NaN if you want this set as the smallest (or largest) mag with a
	 * non-zero rate.
	 * @param erf
	 * @param griddedRegion
	 * @param min_bValMag
	 * @param max_bValMag
	 * @return
	 */
	public static GriddedGeoDataSet get_bValueAtPointsInRegion(ERF erf, GriddedRegion griddedRegion, double min_bValMag, double max_bValMag) {
		ArrayList<SummedMagFreqDist> mfdList = getMagFreqDistsAtLocsInRegion(erf, griddedRegion,0.05,100,0.1, true);
		GriddedGeoDataSet xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude

		System.out.println(mfdList.get(1536));

		//	  GutenbergRichterMagFreqDist grTest = new GutenbergRichterMagFreqDist(1d, 1d,0.0,10d,100);
		for(int i=0;i<xyzData.size();i++)
			//		  xyzData.set(i, grTest.compute_bValue(min_bValMag, max_bValMag));
			xyzData.set(i, mfdList.get(i).compute_bValue(min_bValMag, max_bValMag));
		return xyzData;
	}



	/**
	 * The gives the effective nucleation rates for events greater than or equal to minMag
	 * and less than maxMag for each point in the supplied GriddedRegion.
	 * @param erf - it's assumed that erf.updateForecast() has already been called
	 * @param griddedRegion
	 * @param minMag
	 * @param maxMag
	 * @return GriddedGeoDataSet - X-axis is set as Latitude, and Y-axis is Longitude
	 */
	public static GriddedGeoDataSet getNucleationRatesInRegion(ERF erf, GriddedRegion griddedRegion,
			double minMag, double maxMag) {
		return getNucleationRatesInRegion(erf, griddedRegion, minMag, maxMag, null);
	}
	
	/**
	 * The gives the effective nucleation rates for events greater than or equal to minMag
	 * and less than maxMag for each point in the supplied GriddedRegion.
	 * @param erf - it's assumed that erf.updateForecast() has already been called
	 * @param griddedRegion
	 * @param minMag
	 * @param maxMag
	 * @return GriddedGeoDataSet - X-axis is set as Latitude, and Y-axis is Longitude
	 */
	public static GriddedGeoDataSet getNucleationRatesInRegion(ERF erf, GriddedRegion griddedRegion,
			double minMag, double maxMag, RupNodesCache rupNodesCache) {

		GriddedGeoDataSet xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		double[] zVals = new double[griddedRegion.getNodeCount()];

		double duration = erf.getTimeSpan().getDuration();
		for (int srcIndex=0; srcIndex<erf.getNumSources(); srcIndex++) {
			ProbEqkSource source = erf.getSource(srcIndex);
			for (int rupIndex=0; rupIndex<source.getNumRuptures(); rupIndex++) {
				ProbEqkRupture rupture = source.getRupture(rupIndex);
				double mag = rupture.getMag();
				if(mag>=minMag && mag<maxMag) {
					int[] nodes = null;
					if (rupNodesCache != null)
						nodes = rupNodesCache.getNodesForRup(
								source, rupture, srcIndex, rupIndex, griddedRegion);
					
					if (nodes == null) {
						LocationList surfLocs = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
						double ptRate = rupture.getMeanAnnualRate(duration)/surfLocs.size();
						for(Location loc: surfLocs) {
							int index = griddedRegion.indexForLocation(loc);
							//					  int index = xyzData.indexOf(loc);	// this is slow and should be changed; revise this later when it has been
							if(index >= 0) {
								//						  xyzData.set(index, xyzData.get(index)+ptRate);
								zVals[index] += ptRate;
							}			  
						}
					} else {
						double[] fracts = rupNodesCache.getFractsInNodesForRup(
								source, rupture, srcIndex, rupIndex, griddedRegion);
						double rate = rupture.getMeanAnnualRate(duration);
						for (int i=0; i<nodes.length; i++)
							zVals[nodes[i]] += rate*fracts[i];
					}
				}
			}
		}

		for(int i=0;i<griddedRegion.getNodeCount();i++)
			xyzData.set(i, zVals[i]);

		return xyzData;
	}


	/**
	 * The computes the nucleation moment rate in each grid point in the supplied GriddedRegion.
	 * @param erf - it's assumed that erf.updateForecast() has already been called
	 * @param griddedRegion
	 * @return GriddedGeoDataSet - X-axis is set as Latitude, and Y-axis is Longitude
	 */
	public static GriddedGeoDataSet getMomentRatesInRegion(ERF erf, GriddedRegion griddedRegion) {

		GriddedGeoDataSet xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		double[] zVals = new double[griddedRegion.getNodeCount()];
		double duration = erf.getTimeSpan().getDuration();
		double moRateOutsideRegion = 0;
		for (ProbEqkSource source : erf) {
			for (ProbEqkRupture rupture : source) {
				LocationList surfLocs = rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
				double ptMoRate = MagUtils.magToMoment(rupture.getMag())*rupture.getMeanAnnualRate(duration)/surfLocs.size();
				for(Location loc: surfLocs) {
					int index = griddedRegion.indexForLocation(loc);
					if(index >= 0) {
						zVals[index] += ptMoRate;
					}		
					else{
						moRateOutsideRegion+=ptMoRate;
					}
				}
			}
		}

		System.out.println("moRateOutsideRegion="+moRateOutsideRegion);

		for(int i=0;i<griddedRegion.getNodeCount();i++)
			xyzData.set(i, zVals[i]);

		return xyzData;
	}




	/**
	 * The gives the effective participation rates for events greater than or equal to minMag
	 * and less than maxMag for each point in the supplied GriddedRegion.
	 * @param erf - it's assumed that erf.updateForecast() has already been called
	 * @param griddedRegion
	 * @param minMag
	 * @param maxMag
	 * @return GriddedGeoDataSet - X-axis is set as Latitude, and Y-axis is Longitude
	 */
	public static GriddedGeoDataSet getParticipationRatesInRegion(ERF erf, GriddedRegion griddedRegion,
			double minMag, double maxMag) {
		return getParticipationRatesInRegion(erf, griddedRegion, minMag, maxMag, null);
	}

	public static GriddedGeoDataSet getParticipationRatesInRegion(ERF erf, GriddedRegion griddedRegion,
			double minMag, double maxMag, RupNodesCache rupNodesCache) {

		GriddedGeoDataSet xyzData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
		double[] zVals = new double[griddedRegion.getNodeCount()];

		double duration = erf.getTimeSpan().getDuration();
		for (int srcIndex=0; srcIndex<erf.getNumSources(); srcIndex++) {
			ProbEqkSource source = erf.getSource(srcIndex);
			for (int rupIndex=0; rupIndex<source.getNumRuptures(); rupIndex++) {
				ProbEqkRupture rupture = source.getRupture(rupIndex);
				double mag = rupture.getMag();
				if(mag>=minMag && mag<maxMag) {
					int[] rupNodes = null;
					if (rupNodesCache != null)
						rupNodes = rupNodesCache.getNodesForRup(
								source, rupture, srcIndex, rupIndex, griddedRegion);
					if (rupNodes == null)
						rupNodes = getRupNodesInRegion(rupture, griddedRegion);
					double qkRate = rupture.getMeanAnnualRate(duration);
					for(int locIndex : rupNodes) {
						zVals[locIndex] += qkRate;
					}
				}
			}
		}

		for(int i=0;i<griddedRegion.getNodeCount();i++)
			xyzData.set(i, zVals[i]);

		return xyzData;
	}

	public static int[] getRupNodesInRegion(
			ProbEqkRupture rupture, GriddedRegion griddedRegion) {
		HashSet<Integer> locIndices = new HashSet<Integer>();	// this will prevent duplicate entries
		for(Location loc: rupture.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
			int index = griddedRegion.indexForLocation(loc);
			if(index >= 0)
				locIndices.add(index);
		}
		return Ints.toArray(locIndices);
	}



	/**
	 * This writes a STEP format file, which gives the lat, lon, and rates for the associated mag-freq
	 * dist on each line.
	 * @param mfds
	 * @param griddedRegion
	 * @param filePathAndName
	 */
	public static void writeSTEP_FormatFile(ArrayList<SummedMagFreqDist> mfds, GriddedRegion griddedRegion,
			String filePathAndName) {
		// The following matches the total MFD in the UCERF2 report within 1% below M 8.3
		SummedMagFreqDist totalMFD = new SummedMagFreqDist(5.05,36,0.1);
		for(IncrementalMagFreqDist mfd:mfds)
			totalMFD.addResampledMagFreqDist(mfd, true);
		System.out.println(totalMFD.getCumRateDistWithOffset());

		FileWriter fw;
		try {
			fw = new FileWriter(filePathAndName);
			String header = "lat\tlon";
			for(int m=0; m<totalMFD.size();m++)
				header += "\t"+(float)totalMFD.getX(m);
			fw.write(header+"\n");
			for(int i=0;i<griddedRegion.getNodeCount();i++) {
				Location loc = griddedRegion.locationForIndex(i);
				String line = (float)loc.getLatitude()+"\t"+(float)loc.getLongitude();
				IncrementalMagFreqDist mfd = mfds.get(i);
				for(int m=0; m<mfd.size();m++)
					line += "\t"+(float)mfd.getY(m);
				fw.write(line+"\n");
			}
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	public static void main(String[] args) {


		// OLD STUFF BELOW
		/*
		MeanUCERF2 meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
//		meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_EMPIRICAL);
		meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		meanUCERF2.updateForecast();
		IncrementalMagFreqDist mfd = meanUCERF2.getTotalMFD();
//		System.out.println(mfd.toString());

//		SummedMagFreqDist calcMFD = ERF_Calculator.getTotalMFD_ForERF(meanUCERF2, mfd.getMinX(), mfd.getMaxX(), mfd.getNum(), true); 

		// get the observed rates for comparison
		ArrayList<EvenlyDiscretizedFunc> obsCumDists = UCERF2.getObsCumMFD(false);

		// PLOT Cum MFDs
		ArrayList mfd_funcs = new ArrayList();
		mfd_funcs.add(mfd.getCumRateDistWithOffset());
//		mfd_funcs.add(calcMFD.getCumRateDistWithOffset());
		mfd_funcs.addAll(obsCumDists);

		GraphWindow mfd_graph = new GraphWindow(mfd_funcs, "Magnitude Frequency Distributions");   
		mfd_graph.setYLog(true);
		mfd_graph.setY_AxisRange(1e-5, 10);
		mfd_graph.setX_AxisRange(5.0, 9.0);
		mfd_graph.setX_AxisLabel("Magnitude");
		mfd_graph.setY_AxisLabel("Rate (per yr)");

		ArrayList<PlotCurveCharacterstics> plotMFD_Chars = new ArrayList<PlotCurveCharacterstics>();
		plotMFD_Chars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLUE, 2));
//		plotMFD_Chars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.BLACK, 2));
		plotMFD_Chars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.SOLID_LINE, Color.RED, 2));
		plotMFD_Chars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.CROSS_SYMBOLS, Color.RED, 4));
		plotMFD_Chars.add(new PlotCurveCharacterstics(PlotColorAndLineTypeSelectorControlPanel.CROSS_SYMBOLS, Color.RED, 4));
		mfd_graph.setPlottingFeatures(plotMFD_Chars);
		mfd_graph.setTickLabelFontSize(12);
		mfd_graph.setAxisAndTickLabelFontSize(14);

		 */
	}

}
