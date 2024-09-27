package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.Collection;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Interface implemented by providers of gridded (sometimes referred to as 'other') seismicity sources. Each
 * {@link GridSourceProvider} supplies a {@link GriddedRegion}, accessible via {@link #getGriddedRegion()}. Then, at
 * each location in the {@link GriddedRegion}, a magnitude-frequency distribution (MFD) is supplied via
 * {@link #getMFD(int)}. That MFD may be comprised of multiple components that are also available individually:
 * sub-seismogenic ruptures associated with a modeled faults (see {@link #getMFD_SubSeisOnFault(int)}), and/or ruptures
 * that are unassociated with any modeled fault (see {@link #getMFD_Unassociated(int)}).
 * <p>
 * Focal mechanisms at each grid location are available via the {@link #getFracStrikeSlip(int)},
 * {@link #getFracReverse(int)}, and {@link #getFracNormal(int)} methods. {@link ProbEqkSource} implementations for are
 * available via the {@link #getSource(int, double, boolean, BackgroundRupType)} method, and also via related methods
 * for sub-seismogenic and/or unassociated sources only.
 * 
 * @author Peter Powers
 */
public interface GridSourceProvider extends OpenSHA_Module, BranchAverageableModule<GridSourceProvider> {

	/**
	 * Returns the number of grid locations in the provider.
	 * @return the number of grid locations
	 */
	public int getNumLocations();
	
	/**
	 * Returns the number of sources in the provider.
	 * @return the number of sources
	 */
	public int getNumSources();
	
	/**
	 * Returns the location for the given grid index.
	 * @param gridIndex
	 * @return
	 */
	public Location getLocation(int gridIndex);
	
	/**
	 * Returns the location for the given source index
	 * @param sourceIndex
	 * @return
	 */
	public Location getLocationForSource(int sourceIndex);
	
	/**
	 * Returns the location index for the given source index
	 * @param sourceIndex
	 * @return
	 */
	public int getLocationIndexForSource(int sourceIndex);
	
	/**
	 * Returns the location index for the given location, or -1 if the location is not mapped to a grid node.
	 * Usually defers to {@link GriddedRegion#indexForLocation(Location)} when {@link #getGriddedRegion()} is non-null,
	 * otherwise lookup may be slower.
	 * @see {@link GriddedRegion#indexForLocation(Location)}
	 * @param location
	 * @return
	 */
	public int getLocationIndex(Location location);
	
	/**
	 * Sets the minimum magnitude of ruptures to include when building sources for hazard calculation
	 * 
	 * @param minMagCutoff
	 */
	public void setSourceMinMagCutoff(double minMagCutoff);
	
	/**
	 * @return the minimum magnitude of ruptures to include when building sources for hazard calculation
	 */
	public double getSourceMinMagCutoff();
	
	/**
	 * Return the source at {@code gridIndex}.
	 * 
	 * @param sourceIndex index of source to retrieve
	 * @param duration duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSource(int sourceIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);

	/**
	 * Return the source at {@code gridIndex}.
	 * 
	 * @param tectonicRegionType source tectonic region type
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSource(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);
	

	/**
	 * Return the source at {@code gridIndex}, where only the on-fault sub-seismogenic component is included
	 * (no seismicity that is unassociated with modeled faults).  This returns null if there is no on-fault
	 * sub-seismogenic component for the grid location
	 * 
	 * @param tectonicRegionType source tectonic region type
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceSubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);

	/**
	 * Return the source at {@code gridIndex}, where only the component that is unassociated with modeled faults
	 * included (no on-fault sub-seismogenic component). This returns null if there is no unassociated component
	 * for the grid location
	 * 
	 * @param tectonicRegionType source tectonic region type
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceUnassociated(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);
	
	/**
	 * @return set of each {@link TectonicRegionType} supplied by this {@link GridSourceProvider}
	 */
	public Set<TectonicRegionType> getTectonicRegionTypes();

	/**
	 * Returns the unassociated MFD of a grid location, if any exists, null otherwise.
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_Unassociated(int gridIndex);

	/**
	 * Returns the unassociated MFD of a grid location and tectonic region type, if any exists, null otherwise.
	 * @param tectonicRegionType tectonic region type
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_Unassociated(TectonicRegionType tectonicRegionType, int gridIndex);
	
	/**
	 * Returns the on-fault sub-seismogenic MFD associated with a grid location, if any
	 * exists, null otherwise
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(int gridIndex);
	
	/**
	 * Returns the on-fault sub-seismogenic MFD associated with a grid location and tectonic region type, if any
	 * exists, null otherwise
	 * @param tectonicRegionType tectonic region type
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex);
	
	/**
	 * Returns the MFD associated with a grid location, trimmed to the supplied 
	 * minimum magnitude and the maximum non-zero magnitude.
	 * 
	 * @param gridIndex grid index
	 * @param minMag minimum magnitude to trim MFD to
	 * @return the trimmed MFD
	 */
	public IncrementalMagFreqDist getMFD(int gridIndex, double minMag);
	
	/**
	 * Returns the MFD associated with a grid location and tectonic region type, trimmed to the supplied 
	 * minimum magnitude and the maximum non-zero magnitude.
	 * 
	 * @param tectonicRegionType tectonic region type
	 * @param gridIndex grid index
	 * @param minMag minimum magnitude to trim MFD to
	 * @return the trimmed MFD
	 */
	public IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex, double minMag);
	
	/**
	 * Returns the MFD associated with a grid location. This is the sum of any
	 * unassociated and sub-seismogenic MFDs for the location.
	 * 
	 * @param gridIndex grid index
	 * @return the MFD
	 * @see #getMFD_Unassociated(int)
	 * @see #getMFD_SubSeisOnFault(int)
	 */
	public IncrementalMagFreqDist getMFD(int gridIndex);
	
	/**
	 * Returns the MFD associated with a grid location and tectonic region type. This is the sum of any
	 * unassociated and sub-seismogenic MFDs for the location.
	 * 
	 * @param tectonicRegionType tectonic region type
	 * @param gridIndex grid index
	 * @return the MFD
	 * @see #getMFD_Unassociated(int)
	 * @see #getMFD_SubSeisOnFault(int)
	 */
	public IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex);
	
	/**
	 * Returns the gridded region associated with these grid sources, or null if no single contiguous region exists.
	 * 
	 * @return the gridded region
	 */
	public GriddedRegion getGriddedRegion();
	
	/**
	 * Returns the fraction of focal mechanisms at this grid index that are strike slip
	 * @param gridIndex
	 * @return
	 */
	public abstract double getFracStrikeSlip(int gridIndex);

	/**
	 * Returns the fraction of focal mechanisms at this grid index that are reverse
	 * @param gridIndex
	 * @return
	 */
	public abstract double getFracReverse(int gridIndex);

	/**
	 * Returns the fraction of focal mechanisms at this grid index that are normal
	 * @param gridIndex
	 * @return
	 */
	public abstract double getFracNormal(int gridIndex);
	
	/**
	 * Get a filtered version of this GridSourceProvider with ruptures filtered out that
	 * are below the specified minimum magnitude. Useful for serializing when smaller
	 * magnitudes are not needed.
	 * @param gridIndex
	 * @return
	 */
	public abstract GridSourceProvider getAboveMinMag(float minMag);
	
	/**
	 * Scales all rates by the given values, and throws an exception if the array size is not equal to the
	 * number of locations in the grid source provider.
	 * 
	 * @param valuesArray
	 */
	public void scaleAll(double[] valuesArray);
	
	/**
	 * Scales all rates with the given {@link TectonicRegionType} by the given values, and throws an exception if the
	 * array size is not equal to the number of locations in the grid source provider.
	 * 
	 * @param tectonicRegionType
	 * @param valuesArray
	 */
	public void scaleAll(TectonicRegionType tectonicRegionType, double[] valuesArray);
	
	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	
	public static boolean affectedByLevel(LogicTreeLevel<?> level) {
		// common to both
		boolean gridRegAffectedByDefault = false;
		
		// for MFDs
		boolean mechsAffectedByDefault = true;
		boolean unassocAffectedByDefault = true;
		boolean subSeisAffectedByDefault = true;
		
		// for lists
		boolean sourcesLocsAffectedByDefault = false;
		boolean sourcesAffectedByDefault = true;
		Collection<String> notAffected = level.getNotAffected();
		if (!notAffected.isEmpty()) {
			// this level explicitly states that it doesn't affect some things
			// if we explicitly state no for any category, change the affected by default flag
			// this makes it so that we don't have to explicitly say we don't affect MFD files if we only provide lists, and vice versa
			
			if (notAffected.contains(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)) {
				// we are explicit about grid source list, don't need to check MFD prov files
				mechsAffectedByDefault = false;
				unassocAffectedByDefault = false;
				subSeisAffectedByDefault = false;
			}
			
			if (notAffected.contains(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
					|| notAffected.contains(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
					|| notAffected.contains(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)) {
				// we are explicit about MFD prov files, don't need to check grid source lists
				sourcesAffectedByDefault = false;
				sourcesLocsAffectedByDefault = false;
			}
		}
		return level.affects(ARCHIVE_GRID_REGION_FILE_NAME, gridRegAffectedByDefault)
				|| level.affects(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, sourcesLocsAffectedByDefault)
				|| level.affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, sourcesAffectedByDefault)
				|| level.affects(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, mechsAffectedByDefault)
				|| level.affects(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, unassocAffectedByDefault)
				|| level.affects(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, subSeisAffectedByDefault);
	}

}
