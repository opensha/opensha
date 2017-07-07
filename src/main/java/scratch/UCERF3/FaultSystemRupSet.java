/**
 * 
 */
package scratch.UCERF3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.analysis.DeformationModelsCalc;


/**
 * This class represents the attributes of ruptures in a fault system, 
 * where the latter is composed of some number of fault sections.
 * 
 * @author Field, Milner, Page, & Powers
 *
 */
public class FaultSystemRupSet implements Serializable {
	
	// data arrays/lists
	private List<FaultSectionPrefData> faultSectionData;
	private double[] mags;
	private double[] sectSlipRates;
	private double[] sectSlipRateStdDevs;
	private double[] rakes;
	private double[] rupAreas;
	private double[] rupLengths;
	private double[] sectAreas;
	private List<List<Integer>> sectionForRups;
	private String info;
	
	// for caching
	protected boolean showProgress = false;
	
	// NOTE: copy param documentation to init() method if you make any changes below
	/**
	 * Constructor for precomputed data where everything is passed in.
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	public FaultSystemRupSet(
			List<FaultSectionPrefData> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths,
			String info) {
		init(faultSectionData, sectSlipRates, sectSlipRateStdDevs, sectAreas,
				sectionForRups, mags, rakes, rupAreas, rupLengths, info);
	}
	
	/**
	 * Default constructor for subclasses which will call init on their own.
	 * 
	 * Protected so it can only be invoked by subclasses.
	 */
	protected FaultSystemRupSet() {
		// do nothing, it's up to subclass to call init.
	}
	
	/**
	 * Initialize from another rupSet
	 * @param rupSet
	 */
	protected void init(FaultSystemRupSet rupSet) {
		init(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				rupSet.getSectionIndicesForAllRups(), rupSet.getMagForAllRups(), rupSet.getAveRakeForAllRups(),
				rupSet.getAreaForAllRups(), rupSet.getLengthForAllRups(), rupSet.getInfoString());
	}
	
	/**
	 * Sets all parameters
	 * 
	 * @param faultSectionData fault section data list (CANNOT be null)
	 * @param sectSlipRates slip rates for each fault section with any reductions applied (CAN be null)
	 * @param sectSlipRateStdDevs slip rate std deviations for each fault section (CAN be null)
	 * @param sectAreas areas for each fault section (CAN be null)
	 * @param sectionForRups list of fault section indexes for each rupture (CANNOT be null)
	 * @param mags magnitudes for each rupture (CANNOT be null)
	 * @param rakes rakes for each rupture (CANNOT be null)
	 * @param rupAreas areas for each rupture (CANNOT be null)
	 * @param rupLengths lengths for each rupture (CAN be null)
	 * @param info metadata string
	 */
	protected void init(
			List<FaultSectionPrefData> faultSectionData,
			double[] sectSlipRates,
			double[] sectSlipRateStdDevs,
			double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			double[] rupLengths,
			String info) {
		Preconditions.checkNotNull(faultSectionData, "Fault Section Data cannot be null");
		this.faultSectionData = faultSectionData;
		Preconditions.checkNotNull(faultSectionData, "Magnitudes cannot be null");
		this.mags = mags;
		
		int numRups = mags.length;
		int numSects = faultSectionData.size();
		
		Preconditions.checkArgument(sectSlipRates == null
				|| sectSlipRates.length == numSects, "array sizes inconsistent!");
		this.sectSlipRates = sectSlipRates;
		
		Preconditions.checkArgument(sectSlipRateStdDevs == null
				|| sectSlipRateStdDevs.length == numSects, "array sizes inconsistent!");
		this.sectSlipRateStdDevs = sectSlipRateStdDevs;
		
		Preconditions.checkArgument(rakes.length == numRups, "array sizes inconsistent!");
		this.rakes = rakes;
		
		Preconditions.checkArgument(rupAreas == null ||
				rupAreas.length == numRups, "array sizes inconsistent!");
		this.rupAreas = rupAreas;
		
		Preconditions.checkArgument(rupLengths == null ||
				rupLengths.length == numRups, "array sizes inconsistent!");
		this.rupLengths = rupLengths;
		
		Preconditions.checkArgument(sectAreas == null ||
				sectAreas.length == numSects, "array sizes inconsistent!");
		this.sectAreas = sectAreas;
		
		Preconditions.checkArgument(sectionForRups.size() == numRups, "array sizes inconsistent!");
		this.sectionForRups = sectionForRups;
		
		this.info = info;
	}
	
	/**
	 * This enables/disables visible progress bars for long calculations
	 * 
	 * @param showProgress
	 */
	public void setShowProgress(boolean showProgress) {
		this.showProgress = showProgress;
	}
	
	public boolean isShowProgress() {
		return showProgress;
	}
	
	public void clearCache() {
		rupturesForSectionCache.clear();
		rupturesForParentSectionCache.clear();
		fractRupsInsideRegions.clear();
	}
	
	public void copyCacheFrom(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() != getNumRuptures() || rupSet.getNumSections() != getNumSections())
			return;
		rupturesForSectionCache = rupSet.rupturesForSectionCache;
		rupturesForParentSectionCache = rupSet.rupturesForParentSectionCache;
		fractRupsInsideRegions = rupSet.fractRupsInsideRegions;
	}
	
	/**
	 * The total number of ruptures in the fault system
	 * @return
	 */
	public int getNumRuptures() {
		return mags.length;
	}
	
	/**
	 * The total number of ruptures in the fault system
	 * @return
	 */
	public int getNumSections() {
		return faultSectionData.size();
	}
	
	/**
	 * This returns which sections are used by the each rupture
	 * @param rupIndex
	 * @return
	 */
	public List<List<Integer>> getSectionIndicesForAllRups() {
		return sectionForRups;
	}
	
	/**
	 * This returns which sections are used by the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public List<Integer> getSectionsIndicesForRup(int rupIndex) {
		return sectionForRups.get(rupIndex);
	}
	
	/**
	 * This returns the magnitude of the smallest rupture involving this section or NaN
	 * if no ruptures involve this section.  This is called "Orig" because subclasses
	 * may filter the minimum magnitudes further (e.g., so they don't fall below some
	 * threshold).
	 * @param sectIndex
	 * @return
	 */
	public double getOrigMinMagForSection(int sectIndex) {
		List<Integer> rups = getRupturesForSection(sectIndex);
		if (rups.isEmpty())
			return Double.NaN;
		double minMag = Double.POSITIVE_INFINITY;
		for (int rupIndex : getRupturesForSection(sectIndex)) {
			double mag = getMagForRup(rupIndex);
			if (mag < minMag)
				minMag = mag;
		}
		return minMag;
	}
	
	
	/**
	 * This returns the magnitude of the largest rupture involving this section or NaN
	 * if no ruptures involve this section.
	 * @param sectIndex
	 * @return
	 */
	public double getMaxMagForSection(int sectIndex) {
		List<Integer> rups = getRupturesForSection(sectIndex);
		if (rups.isEmpty())
			return Double.NaN;
		double maxMag = 0;
		for (int rupIndex : getRupturesForSection(sectIndex)) {
			double mag = getMagForRup(rupIndex);
			if (mag > maxMag)
				maxMag = mag;
		}
		return maxMag;
	}
	
	
	/**
	 * This computes the fractional slip rate (or moment rate) taken away for sub-seismogenic ruptures
	 * (relative to creep reduced moment rate).  Actually, this will also include any additional coupling
	 * coefficient reductions applied by subclasses (e.g., InversionFaultSystemRuptureSet has the option
	 * of also applying an implied coupling coefficient that will be reflected in getSlipRateForSection(sectIndex))
	 * @param sectIndex
	 * @return
	 */
	public double getMomentRateReductionFraction(int sectIndex) {
		double origSlipRate = getFaultSectionData(sectIndex).getReducedAveSlipRate() * 1e-3; // convert to meters
		double reducedSlipRate = getSlipRateForSection(sectIndex);
		return 1d - reducedSlipRate/origSlipRate;
	}
	
	/**
	 * This returns the total reduction in moment rate for subseimogenic ruptures
	 * and any coupling coefficient applied (the amount removed).  Actually, this 
	 * reduction also includes any additional coupling coefficient reductions applied 
	 * by subclasses (e.g., InversionFaultSystemRuptureSet has the option of also 
	 * applying an implied coupling coefficient that will be reflected in 
	 * getSlipRateForSection(sectIndex))
	 * 
	 * @return
	 */
	public double getTotalMomentRateReduction() {
		return getTotalOrigMomentRate() - getTotalReducedMomentRate();
	}
	
	/**
	 * This returns the total fraction of moment that is reduced by subseismogenic ruptures.
	 * Actually, this reduction also includes any additional coupling coefficient reductions  
	 * applied by subclasses (e.g., InversionFaultSystemRuptureSet has the option of also 
	 * applying an implied coupling coefficient that will be reflected in 
	 * getSlipRateForSection(sectIndex))
	 * 
	 */
	public double getTotalMomentRateReductionFraction() {
		return getTotalMomentRateReduction() / getTotalOrigMomentRate();
	}

	/**
	 * This returns the original moment rate (with creep reductions but without subseismogenic
	 * rupture reductions) for a fault subsection
	 */
	public double getOrigMomentRate(int sectIndex) {
		FaultSectionPrefData sectData = getFaultSectionData(sectIndex);
		double moRate = sectData.calcMomentRate(true);
		if (Double.isNaN(moRate))
			return 0;
		return moRate;
	}
	
	/**
	 * This returns the total moment rate for the given rupSet without taking into account any
	 * moment rate reductions for subseismogenic ruptures (but does include all default creep reductions).<br>
	 * <br>
	 * This simply calls <code>DeformationModelsCalc.calculateTotalMomentRate(sectData, true)</code> 
	 * 
	 * @param rupSet
	 * @return
	 */
	public double getTotalOrigMomentRate() {
		return DeformationModelsCalc.calculateTotalMomentRate(getFaultSectionDataList(), true);
	}
	
	/**
	 * This returns the moment rate after removing that for subseimogenic ruptures 
	 * (and default creep effects). This also include any additional
	 * coupling coefficients applied by subclasses (e.g., InversionFaultSystemRuptureSet 
	 * has the option of also applying an implied coupling coefficient that will be 
	 * reflected in getSlipRateForSection(sectIndex)).
	 * 
	 * @param sectIndex
	 * @return
	 */
	public double getReducedMomentRate(int sectIndex) {
		return getOrigMomentRate(sectIndex) * (1 - getMomentRateReductionFraction(sectIndex));
	}
	
	/**
	 * This returns the total moment rate after removing that for subseismogenic  
	 * ruptures (and default creep influences).  This also include any additional
	 * coupling coefficients applied by subclasses; e.g., InversionFaultSystemRuptureSet 
	 * has the option of also applying an implied coupling coefficient that will be 
	 * reflected in getSlipRateForSection(sectIndex)).
	 * @return
	 */
	public double getTotalReducedMomentRate() {
		double totMoRate = 0d;
		for (int sectIndex=0; sectIndex<getNumSections(); sectIndex++) {
			double sectMoment = getReducedMomentRate(sectIndex);
			if (!Double.isNaN(sectMoment))
				totMoRate += sectMoment;
		}
		return totMoRate;
	}
	
	/**
	 * This sets the magnitudes for each rupture. This is needed for special cases where magnitudes are
	 * overridden, for example UCERF2 comparison solutions.
	 * @param mags
	 */
	public void setMagForallRups(double[] mags) {
		Preconditions.checkArgument(mags.length == getNumRuptures(),
				"Called setMag for "+mags.length+" rups but rup set has "+getNumRuptures()+" rups!");
		this.mags = mags;
	}
	
	/**
	 * This gives the magnitude for each rth rupture
	 * @return
	 */
	public double[] getMagForAllRups() {
		return mags;
	}

	/**
	 * This gives the magnitude for the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getMagForRup(int rupIndex) {
		return mags[rupIndex];
	}
	
	/**
	 * This represents the total moment rate available to the rupture (with creep and 
	 * subseis ruptures removed), assuming it is the only event to occur along the sections it uses.
	 * @param rupIndex
	 * @return
	 */
	protected double calcTotalAvailableMomentRate(int rupIndex) {
		List<Integer> sectsInRup = getSectionsIndicesForRup(rupIndex);
		double totMoRate = 0;
		for(Integer sectID:sectsInRup) {
			double area = getAreaForSection(sectID);
			totMoRate += FaultMomentCalc.getMoment(area, getSlipRateForSection(sectID));
		}
		return totMoRate;
	}
	
	/**
	 * This gives the average rake for all ruptures
	 * @return
	 */
	public double[] getAveRakeForAllRups() {
		return rakes;
	}
	
	/**
	 * This gives the average rake for the rth rupture
	 * @param rupIndex
	 * @return
	 */
	public double getAveRakeForRup(int rupIndex) {
		return rakes[rupIndex];
	}
	
	/**
	 * @return Area (SI units: sq-m)
	 */
	public double[] getAreaForAllRups() {
		return rupAreas;
	}
	
	/**
	 * @param rupIndex
	 * @return Area (SI units: sq-m)
	 */
	public double getAreaForRup(int rupIndex) {
		return rupAreas[rupIndex];
	}
	
	/**
	 * @return Area (SI units: sq-m)
	 */
	public double[] getAreaForAllSections() {
		return sectAreas;
	}
	
	/**
	 * @param sectIndex
	 * @return Area (SI units: sq-m)
	 */
	public double getAreaForSection(int sectIndex) {
		return sectAreas[sectIndex];
	}
	
	/**
	 * This returns a list of all fault-section data
	 * @return
	 */
	public List<FaultSectionPrefData> getFaultSectionDataList() {
		return faultSectionData;
	}
	
	/**
	 * The returns the fault-section data for the sth section
	 * @param sectIndex
	 * @return
	 */
	public FaultSectionPrefData getFaultSectionData(int sectIndex) {
		return faultSectionData.get(sectIndex);
	}
	
	/**
	 * This gets a list of fault-section data for the specified rupture
	 * @param rupIndex
	 * @return
	 */
	public List<FaultSectionPrefData> getFaultSectionDataForRupture(int rupIndex) {
		List<Integer> inds = getSectionsIndicesForRup(rupIndex);
		ArrayList<FaultSectionPrefData> datas = new ArrayList<FaultSectionPrefData>();
		for (int ind : inds)
			datas.add(getFaultSectionData(ind));
		return datas;
	}
	
	private transient double prevGridSpacing = Double.NaN;
	private transient boolean prevQuadSurface = false;
	private transient Map<Integer, RuptureSurface> rupSurfaceCache = Maps.newHashMap();
	
	/**
	 * This creates a CompoundGriddedSurface for the specified rupture.  This applies aseismicity as
	 * a reduction of area and sets preserveGridSpacingExactly=false (for evenly gridded) so there are
	 * no cut-off ends (but variable grid spacing)
	 * @param rupIndex
	 * @param gridSpacing
	 * @param quadRupSurface use quad surfaces (otherwise evenly gridded)
	 * @return
	 */
	public synchronized RuptureSurface getSurfaceForRupupture(int rupIndex, double gridSpacing, boolean quadRupSurface) {
		if (prevGridSpacing != gridSpacing || prevQuadSurface != quadRupSurface) {
			rupSurfaceCache.clear();
			prevGridSpacing = gridSpacing;
			prevQuadSurface = quadRupSurface;
		}
		RuptureSurface surf = rupSurfaceCache.get(rupIndex);
		if (surf != null)
			return surf;
		List<RuptureSurface> rupSurfs = Lists.newArrayList();
		if (quadRupSurface) {
			for(FaultSectionPrefData fltData: getFaultSectionDataForRupture(rupIndex))
				rupSurfs.add(fltData.getQuadSurface(true, gridSpacing));
		} else {
			for(FaultSectionPrefData fltData: getFaultSectionDataForRupture(rupIndex))
				rupSurfs.add(fltData.getStirlingGriddedSurface(gridSpacing, false, true));
		}
		if (rupSurfs.size() == 1)
			surf = rupSurfs.get(0);
		else
			surf = new CompoundSurface(rupSurfs);
		rupSurfaceCache.put(rupIndex, surf);
		return surf;
	}
	
	/**
	 * This returns the length (SI units: m) of each rupture.
	 * @return
	 */
	public double[] getLengthForAllRups() {
		return rupLengths;
	}
	
	/**
	 * This returns the length (SI units: m) of the specified rupture.
	 * @param rupIndex
	 * @return
	 */
	public double getLengthForRup(int rupIndex) {
		return rupLengths[rupIndex];
	}
	
	/**
	 * This returns the width (SI units: m) of the specified rupture 
	 * (calculated as getAreaForRup(rupIndex)/getLengthForRup(rupIndex))
	 * @param rupIndex
	 * @return
	 */
	public double getAveWidthForRup(int rupIndex) {
		return getAreaForRup(rupIndex)/getLengthForRup(rupIndex);
	}

	
	
	/**
	 * This returns the section slip rate after reductions for subseismogenic ruptures
	 * (it differs from what is returned by getFaultSectionData(int).getReducedAveSlipRate())
	 * @return
	 */
	public double getSlipRateForSection(int sectIndex) {
		return sectSlipRates[sectIndex];
	}
	
	/**
	 * This differs from what is returned by getFaultSectionData(int).getAveLongTermSlipRate()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return
	 */
	public double[] getSlipRateForAllSections() {
		return sectSlipRates;
	}
	
	/**
	 * This differs from what is returned by getFaultSectionData(int).getSlipRateStdDev()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return
	 */
	public double getSlipRateStdDevForSection(int sectIndex) {
		return sectSlipRateStdDevs[sectIndex];
	}
	
	/**
	 * This differs from what is returned by getFaultSectionData(int).getSlipRateStdDev()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return
	 */
	public double[] getSlipRateStdDevForAllSections() {
		return sectSlipRateStdDevs;
	}

	/**
	 * This is a general info String
	 * @return
	 */
	public String getInfoString() {
		return info;
	}
	
	public void setInfoString(String info) {
		this.info = info;
	}
	
	private Table<Region, Boolean, double[]> fractRupsInsideRegions = HashBasedTable.create();
	
	/**
	 * 
	 * @param region
	 * @param traceOnly
	 * @return
	 */
	public double[] getFractRupsInsideRegion(Region region, boolean traceOnly) {
		if (!fractRupsInsideRegions.contains(region, traceOnly)) {
			if (fractRupsInsideRegions.size() > 10) { // max cache size
				Set<Cell<Region, Boolean, double[]>> cells = fractRupsInsideRegions.cellSet();
				cells.remove(cells.iterator().next());
			}
			double[] fractSectsInside = new double[getNumSections()];
			double gridSpacing=1;
			int[] numPtsInSection = new int[getNumSections()];
			int numRuptures = getNumRuptures();
			
			for(int s=0;s<getNumSections(); s++) {
				StirlingGriddedSurface surf = getFaultSectionData(s).getStirlingGriddedSurface(gridSpacing, false, true);
				if (traceOnly) {
					FaultTrace trace = surf.getRowAsTrace(0);
					numPtsInSection[s] = trace.size();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, trace);
				} else {
					numPtsInSection[s] = surf.getNumCols()*surf.getNumRows();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, surf.getEvenlyDiscritizedListOfLocsOnSurface());
				}
			}
			
			double[] fractRupsInside = new double[numRuptures];
			
			for(int rup=0; rup<numRuptures; rup++) {
				List<Integer> sectionsIndicesForRup = getSectionsIndicesForRup(rup);
				int totNumPts = 0;
				for(Integer s:sectionsIndicesForRup) {
					fractRupsInside[rup] += fractSectsInside[s]*numPtsInSection[s];
					totNumPts += numPtsInSection[s];
				}
				fractRupsInside[rup] /= totNumPts;
			}
			fractRupsInsideRegions.put(region, traceOnly, fractRupsInside);
		}
		return fractRupsInsideRegions.get(region, traceOnly);
	}
	
	/**
	 * this caches the ruptures involving each section
	 */
	private List<List<Integer>> rupturesForSectionCache = null;
	
	/**
	 * This returns the a list of all ruptures that occur on each section
	 * @param secIndex
	 * @return
	 */
	public final List<Integer> getRupturesForSection(int secIndex) {
		if (rupturesForSectionCache == null) {
			synchronized (this) {
				if (rupturesForSectionCache != null)
					return rupturesForSectionCache.get(secIndex);
				CalcProgressBar p = null;
				if (showProgress) {
					p = new CalcProgressBar("Calculating Ruptures for each Section", "Calculating Ruptures for each Section");
				}
				ArrayList<List<Integer>> rupturesForSectionCache = new ArrayList<List<Integer>>();
				for (int secID=0; secID<getNumSections(); secID++)
					rupturesForSectionCache.add(new ArrayList<Integer>());

				int numRups = getNumRuptures();
				for (int rupID=0; rupID<numRups; rupID++) {
					if (p != null) p.updateProgress(rupID, numRups);
					for (int secID : getSectionsIndicesForRup(rupID)) {
						rupturesForSectionCache.get(secID).add(rupID);
					}
				}
				// now make the immutable
				for (int i=0; i<rupturesForSectionCache.size(); i++)
					rupturesForSectionCache.set(i, Collections.unmodifiableList(rupturesForSectionCache.get(i)));
				this.rupturesForSectionCache = rupturesForSectionCache;
				if (p != null) p.dispose();
			}
		}
		
		return rupturesForSectionCache.get(secIndex);
	}
	
	/**
	 * this caches the ruptures involving each section
	 */
	private Map<Integer, List<Integer>> rupturesForParentSectionCache = null;
	
	/**
	 * This returns the a list of all ruptures that occur on each parent section
	 * @param secIndex
	 * @return
	 */
	public final List<Integer> getRupturesForParentSection(int parentSectID) {
		if (rupturesForParentSectionCache == null) {
			synchronized (this) {
				if (rupturesForParentSectionCache != null)
					return rupturesForParentSectionCache.get(parentSectID);
				CalcProgressBar p = null;
				if (showProgress) {
					p = new CalcProgressBar("Calculating Ruptures for each Parent Section", "Calculating Ruptures for each Parent Section");
				}
				// note this assumes that sections are in order
				rupturesForParentSectionCache = Maps.newConcurrentMap();

				int numRups = getNumRuptures();
				for (int rupID=0; rupID<numRups; rupID++) {
					if (p != null) p.updateProgress(rupID, numRups);
					HashSet<Integer> parents = new HashSet<Integer>();
					for (int secID : getSectionsIndicesForRup(rupID)) {
						int parent = getFaultSectionData(secID).getParentSectionId();
						if (parent < 0)
							continue;
						if (!parents.contains(parent))
							parents.add(parent);
					}
					for (int parent : parents) {
						List<Integer> rupsForParent = rupturesForParentSectionCache.get(parent);
						if (rupsForParent == null) {
							rupsForParent = new ArrayList<Integer>();
							rupturesForParentSectionCache.put(parent, rupsForParent);
						}
						rupsForParent.add(rupID);
					}
				}
				
				// now make the immutable
				for (Integer key : rupturesForParentSectionCache.keySet())
					rupturesForParentSectionCache.put(key, Collections.unmodifiableList(rupturesForParentSectionCache.get(key)));
				if (p != null) p.dispose();
			}
		}
		
		return rupturesForParentSectionCache.get(parentSectID);
	}
	
	public final List<Integer> getParentSectionsForRup(int rupIndex) {
		List<Integer> parents = Lists.newArrayList();
		for (int sectIndex : getSectionsIndicesForRup(rupIndex)) {
			int parent = getFaultSectionData(sectIndex).getParentSectionId();
			if (!parents.contains(parent))
				parents.add(parent);
		}
		return parents;
	}
	
	/**
	 * This returns the maximum magnitude of this rupture set
	 * @return
	 */
	public double getMaxMag() {
		return StatUtils.max(getMagForAllRups());
	}
	
	/**
	 * This returns the maximum magnitude of this rupture set
	 * @return
	 */
	public double getMinMag() {
		return StatUtils.min(getMagForAllRups());
	}
	
	
	
}
