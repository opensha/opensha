package org.opensha.sha.earthquake.faultSysSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModuleManager;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.impl.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.impl.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.impl.PlausibilityConfigurationModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/**
 * This class represents the attributes of ruptures in a fault system, 
 * where the latter is composed of some number of fault sections.
 * <p>
 * Only the core fields common to rupture sets from all models are included. Extra attributes can be attached to this
 * rupture set as RupSetModule instances. Examples of modules include: logic tree branches, gridded seismicity,
 * plausibility configurations, cluster rupture representations, RSQSim event mappings
 * 
 * @author Field, Milner, Page, & Powers
 *
 */
public final class FaultSystemRupSet extends ModuleManager<RupSetModule> {
// TODO: should this be final?
	
	// data arrays/lists
	private final List<? extends FaultSection> faultSectionData;
	private final double[] mags;
	private final double[] sectSlipRates;
	private final double[] sectSlipRateStdDevs; // TODO: make a module?
	private final double[] rakes;
	private final double[] rupAreas;
	private final double[] rupLengths;
	private final double[] sectAreas;
	private final List<List<Integer>> sectionForRups;
	private String info;
	
	// if true, caching operations will show a graphical progress bar
	protected boolean showProgress = false;
	
	/**
	 * Initialized a FaultSystemRupSet object with all core data.
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
			List<? extends FaultSection> faultSectionData,
			@Nullable double[] sectSlipRates,
			@Nullable double[] sectSlipRateStdDevs,
			@Nullable double[] sectAreas,
			List<List<Integer>> sectionForRups,
			double[] mags,
			double[] rakes,
			double[] rupAreas,
			@Nullable double[] rupLengths,
			@Nullable String info) {
		super(RupSetModule.class);
		Preconditions.checkNotNull(faultSectionData, "Fault Section Data cannot be null");
		this.faultSectionData = ImmutableList.copyOf(faultSectionData);
		Preconditions.checkNotNull(faultSectionData, "Magnitudes cannot be null");
		this.mags = mags;
		
		int numRups = mags.length;
		int numSects = this.faultSectionData.size();
		
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
		this.sectionForRups = ImmutableList.copyOf(sectionForRups);
		
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
	public List<? extends FaultSection> getFaultSectionDataList() {
		return faultSectionData;
	}
	
	/**
	 * The returns the fault-section data for the sth section
	 * @param sectIndex
	 * @return
	 */
	public FaultSection getFaultSectionData(int sectIndex) {
		return faultSectionData.get(sectIndex);
	}
	
	/**
	 * This gets a list of fault-section data for the specified rupture
	 * @param rupIndex
	 * @return
	 */
	public List<FaultSection> getFaultSectionDataForRupture(int rupIndex) {
		List<Integer> inds = getSectionsIndicesForRup(rupIndex);
		ArrayList<FaultSection> datas = new ArrayList<FaultSection>();
		for (int ind : inds)
			datas.add(getFaultSectionData(ind));
		return datas;
	}
	
	private class RupSurfaceCache {
		private double prevGridSpacing = Double.NaN;
		private Map<Integer, RuptureSurface> rupSurfaceCache;
		
		private RupSurfaceCache() {
			rupSurfaceCache = new HashMap<>();
		}
		
		private synchronized RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing) {
			if (prevGridSpacing != gridSpacing) {
				rupSurfaceCache.clear();
				prevGridSpacing = gridSpacing;
			}
			RuptureSurface surf = rupSurfaceCache.get(rupIndex);
			if (surf != null)
				return surf;
			List<RuptureSurface> rupSurfs = Lists.newArrayList();
			for (FaultSection fltData : getFaultSectionDataForRupture(rupIndex))
				rupSurfs.add(fltData.getFaultSurface(gridSpacing, false, true));
			if (rupSurfs.size() == 1)
				surf = rupSurfs.get(0);
			else
				surf = new CompoundSurface(rupSurfs);
			rupSurfaceCache.put(rupIndex, surf);
			return surf;
		}
	}
	
	protected transient RupSurfaceCache surfCache = new RupSurfaceCache();
	
	/**
	 * This creates a CompoundGriddedSurface for the specified rupture.  This applies aseismicity as
	 * a reduction of area and sets preserveGridSpacingExactly=false so there are no cut-off ends
	 * (but variable grid spacing)
	 * @param rupIndex
	 * @param gridSpacing
	 * @return
	 */
	public RuptureSurface getSurfaceForRupture(int rupIndex, double gridSpacing) {
		return surfCache.getSurfaceForRupture(rupIndex, gridSpacing);
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
	 * This returns the section slip rate of the given section. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double getSlipRateForSection(int sectIndex) {
		return sectSlipRates[sectIndex];
	}
	
	/**
	 * This returns the section slip rate of all sections. It can differ from what is returned by
	 * getFaultSectionData(index).get*AveSlipRate() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate (SI units: m)
	 */
	public double[] getSlipRateForAllSections() {
		return sectSlipRates;
	}
	
	/**
	 * This returns the standard deviation of the the slip rate for the given section. It can differ from what is returned by
	 * getFaultSectionData(index).getSlipRateStdDev() if there are any reductions for creep or subseismogenic ruptures.
	 * @return slip rate standard deviation (SI units: m)
	 */
	public double getSlipRateStdDevForSection(int sectIndex) {
		return sectSlipRateStdDevs[sectIndex];
	}
	
	/**
	 * This differs from what is returned by getFaultSectionData(int).getSlipRateStdDev()
	 * where there has been a modification (i.e., moment rate reductions for smaller events).
	 * @return slip rate standard deviation (SI units: m)
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
		if (region == null) {
			double[] ret = new double[getNumRuptures()];
			for (int r=0; r<ret.length; r++)
				ret[r] = 1d;
			return ret;
		}
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
				RuptureSurface surf = getFaultSectionData(s).getFaultSurface(gridSpacing, false, true);
				if (traceOnly) {
					FaultTrace trace = surf.getEvenlyDiscritizedUpperEdge();
					numPtsInSection[s] = trace.size();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, trace);
				} else {
					LocationList surfLocs = surf.getEvenlyDiscritizedListOfLocsOnSurface();
					numPtsInSection[s] = surfLocs.size();
					fractSectsInside[s] = RegionUtils.getFractionInside(region, surfLocs);
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
	
	/*
	 * Modules
	 */
	
	/**
	 * Adds the given module to this rupture set
	 * 
	 * @param module
	 */
	@Override
	public void addModule(RupSetModule module) {
		Preconditions.checkNotNull(module.getRupSet());
		Preconditions.checkState(module.getRupSet() == this || this.areRupturesEquivalent(module.getRupSet()),
				"This module was created with a different rupture set, and that rupture set is not equivalent.");
		super.addModule(module);
	}
	
	/**
	 * Returns true if the given rupture set is equivalent to this one. Equivalence is determined by the following
	 * criteria:
	 * 
	 * * Same number of ruptures
	 * * Each rupture uses the same fault sections, listed in the same order
	 * * Same number of sections
	 * * Each section has the same name & parent section ID
	 * 
	 * @param other
	 * @return true if the given rupture set is equivalent to this one (same ruptures and sections in same order,
	 *  maybe different properties)
	 */
	public boolean areRupturesEquivalent(FaultSystemRupSet other) {
		// check sections
		if (getNumSections() != other.getNumSections())
			return false;
		for (int s=0; s<getNumSections(); s++) {
			FaultSection mySect = getFaultSectionData(s);
			FaultSection oSect = other.getFaultSectionData(s);
			if (mySect.getParentSectionId() != oSect.getParentSectionId())
				return false;
			if (!mySect.getParentSectionName().equals(oSect.getSectionName()))
				return false;
		}
		
		// check ruptures
		if (getNumRuptures() != other.getNumRuptures())
			return false;
		for (int r=0; r<getNumRuptures(); r++) {
			List<Integer> mySects = getSectionsIndicesForRup(r);
			List<Integer> oSects = other.getSectionsIndicesForRup(r);
			if (!mySects.equals(oSects))
				return false;
		}
		return true;
	}
	
	/**
	 * Temporary method to transform this to the old version, to reduce compile errors during initial refactoring
	 * 
	 * @return
	 */
	@Deprecated
	public scratch.UCERF3.FaultSystemRupSet toOldRupSet() {
		scratch.UCERF3.FaultSystemRupSet old = new scratch.UCERF3.FaultSystemRupSet(
				faultSectionData, sectSlipRates, sectSlipRateStdDevs, sectAreas, sectionForRups,
				mags, rakes, rupAreas, rupLengths, info);
		if (hasModule(PlausibilityConfigurationModule.class))
			old.setPlausibilityConfiguration(getModule(PlausibilityConfigurationModule.class).get());
		if (hasModule(ClusterRuptures.class))
			old.setClusterRuptures(getModule(ClusterRuptures.class).get());
		return old;
	}
	
	public static Builder builderFromExisting(FaultSystemRupSet rupSet) {
		return new Builder(rupSet.faultSectionData, rupSet.sectSlipRates, rupSet.sectSlipRateStdDevs, rupSet.sectAreas,
				rupSet.sectionForRups, rupSet.mags, rupSet.rakes, rupSet.rupAreas, rupSet.rupLengths, rupSet.info);
	}
	
	public static Builder builder(List<? extends FaultSection> faultSectionData, List<List<Integer>> sectionForRups) {
		return new Builder(faultSectionData, null, null, null, sectionForRups, null, null, null, null, null);
	}
	
	public static Builder builderForClusterRups(List<? extends FaultSection> faultSectionData, List<ClusterRupture> rups) {
		List<List<Integer>> sectionForRups = new ArrayList<>();
		for (ClusterRupture rup : rups) {
			List<Integer> ids = new ArrayList<>();
			for (FaultSection sect : rup.buildOrderedSectionList())
				ids.add(sect.getSectionId());
			sectionForRups.add(ids);
		}
		Builder builder = new Builder(faultSectionData, null, null, null, sectionForRups, null, null, null, null, null);
		builder.addModule(new ModuleBuilder() {
			
			@Override
			public RupSetModule build(FaultSystemRupSet rupSet) {
				return ClusterRuptures.instance(rupSet, rups);
			}
		});
		return builder;
	}
	
	public static interface ModuleBuilder {
		public RupSetModule build(FaultSystemRupSet rupSet);
	}
	
	public static class Builder {
		
		// core data objects
		private List<? extends FaultSection> faultSectionData;
		private double[] mags;
		private double[] sectSlipRates;
		private double[] sectSlipRateStdDevs;
		private double[] rakes;
		private double[] rupAreas;
		private double[] rupLengths;
		private double[] sectAreas;
		private List<List<Integer>> sectionForRups;
		private String info;
		
		private List<ModuleBuilder> modules;
		
		private Builder (
				List<? extends FaultSection> faultSectionData,
				@Nullable double[] sectSlipRates,
				@Nullable double[] sectSlipRateStdDevs,
				@Nullable double[] sectAreas,
				List<List<Integer>> sectionForRups,
				@Nullable double[] mags,
				@Nullable double[] rakes,
				@Nullable double[] rupAreas,
				@Nullable double[] rupLengths,
				@Nullable String info) {
			Preconditions.checkState(faultSectionData != null && !faultSectionData.isEmpty(),
					"Must supply fault sections");
			this.faultSectionData = faultSectionData;
			this.mags = mags;
			this.sectSlipRates = sectSlipRates;
			this.sectSlipRateStdDevs = sectSlipRateStdDevs;
			this.rakes = rakes;
			this.rupAreas = rupAreas;
			this.rupLengths = rupLengths;
			this.sectAreas = sectAreas;
			Preconditions.checkState(sectionForRups != null && !sectionForRups.isEmpty(),
					"Must supply ruptures");
			this.sectionForRups = sectionForRups;
			this.info = info;
			
			modules = new ArrayList<>();
		}
		
		/**
		 * Sets magnitudes from the given UCERF3 scaling relationships enum
		 * @param scale
		 * @return
		 */
		public Builder forScalingRelationship(ScalingRelationships scale) {
			this.mags = new double[sectionForRups.size()];
			for (int r=0; r<mags.length; r++) {
				double totArea = 0d;
				double aveWidth = 0d;
				for (int s : sectionForRups.get(r)) {
					FaultSection sect = faultSectionData.get(s);
					double area = faultSectionData.get(s).getArea(true);	// sq-m
					totArea += area;
					aveWidth += sect.getOrigDownDipWidth()*1e3*area;
				}
				aveWidth /= totArea;
				mags[r] = scale.getMag(totArea, aveWidth);
			}
			modules.add(new ModuleBuilder() {
				
				@Override
				public RupSetModule build(FaultSystemRupSet rupSet) {
					return AveSlipModule.forModel(rupSet, scale);
				}
			});
			return this;
		}
		
		public Builder rupMags(double[] mags) {
			Preconditions.checkArgument(mags.length == this.sectionForRups.size());
			this.mags = mags;
			return this;
		}
		
		public Builder rupRakes(double[] rakes) {
			Preconditions.checkArgument(rakes.length == this.sectionForRups.size());
			this.rakes = rakes;
			return this;
		}
		
		public Builder rupAreas(double[] rupAreas) {
			Preconditions.checkArgument(rupAreas.length == this.sectionForRups.size());
			this.rupAreas = rupAreas;
			return this;
		}
		
		public Builder rupLengths(double[] rupLengths) {
			Preconditions.checkArgument(rupLengths == null
					|| rupLengths.length == this.sectionForRups.size());
			this.rupLengths = rupLengths;
			return this;
		}
		
		public Builder sectSlipRates(double[] sectSlipRates) {
			Preconditions.checkArgument(sectSlipRates == null
					|| sectSlipRates.length == this.faultSectionData.size());
			this.sectSlipRates = sectSlipRates;
			return this;
		}
		
		public Builder sectSlipRateStdDevs(double[] sectSlipRateStdDevs) {
			Preconditions.checkArgument(sectSlipRateStdDevs == null
					|| sectSlipRateStdDevs.length == this.faultSectionData.size());
			this.sectSlipRateStdDevs = sectSlipRateStdDevs;
			return this;
		}
		
		public Builder sectAreas(double[] sectAreas) {
			Preconditions.checkArgument(sectAreas == null
					|| sectAreas.length == this.faultSectionData.size());
			this.sectAreas = sectAreas;
			return this;
		}
		
		public Builder addModule(ModuleBuilder module) {
			this.modules.add(module);
			return this;
		}
		
		public FaultSystemRupSet build() {
			Preconditions.checkNotNull(mags, "Must set magnitudes");
			int numSects = faultSectionData.size();
			
			double[] sectSlipRates= this.sectSlipRates;
			if (sectSlipRates == null) {
				sectSlipRates = new double[numSects];
				for (int s=0; s<numSects; s++)
					sectSlipRates[s] = faultSectionData.get(s).getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
			}
			double[] sectSlipRateStdDevs = this.sectSlipRateStdDevs;
			if (sectSlipRateStdDevs == null) {
				sectSlipRateStdDevs = new double[numSects];
				for (int s=0; s<numSects; s++)
					sectSlipRateStdDevs[s] = faultSectionData.get(s).getReducedSlipRateStdDev()*1e-3; // mm/yr => m/yr
			}
			double[] sectAreas = this.sectAreas;
			if (sectAreas == null) {
				sectAreas = new double[numSects];
				for (int s=0; s<numSects; s++)
					sectSlipRateStdDevs[s] = faultSectionData.get(s).getArea(true);
			}
			
			int numRups = sectionForRups.size();
			double[] rakes = this.rakes;
			double[] rupAreas = this.rupAreas;
			if (rakes == null || rupAreas == null) {
				if (rakes == null)
					rakes = new double[numRups];
				if (rupAreas == null)
					rupAreas =new double[numRups];
				for (int r=0; r<numRups; r++) {
					List<Double> mySectAreas = new ArrayList<>();
					List<Double> mySectRakes = new ArrayList<>();
					double totArea = 0d;
					for (int s : sectionForRups.get(r)) {
						FaultSection sect = faultSectionData.get(s);
						double area = sectAreas[s];	// sq-m
						totArea += area;
						mySectAreas.add(area);
						mySectRakes.add(sect.getAveRake());
					}
					if (rupAreas[r] == 0d)
						rupAreas[r] = totArea;
					if (rakes[r] == 0d)
						rakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(mySectAreas, mySectRakes));
				}
			}
			
			double[] rupLengths = this.rupLengths;
			if (rupLengths == null) {
				rupLengths = new double[numRups];
				for (int r=0; r<numRups; r++) {
					for (int s : sectionForRups.get(r)) {
						FaultSection sect = faultSectionData.get(s);
						double length = sect.getTraceLength()*1e3;	// km --> m
						rupLengths[r] += length;
					}
				}
			}
			
			FaultSystemRupSet rupSet = new FaultSystemRupSet(faultSectionData, sectSlipRates, sectSlipRateStdDevs,
					sectAreas, sectionForRups, mags, rakes, rupAreas, rupLengths, info);
			for (ModuleBuilder module : modules)
				rupSet.addModule(module.build(rupSet));
			return rupSet;
		}
		
	}
	
}
