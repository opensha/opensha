package scratch.UCERF3.griddedSeismicity;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Interface implemented by providers of gridded (sometimes referred to as
 * 'other') seismicity sources.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface GridSourceProvider {

	/**
	 * Returns the number of sources in the provider.
	 * @return the number of sources
	 */
	public int size();

	/**
	 * Return the source at {@code index}.
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param crosshair sources if true,
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSource(int index, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);
	

	/**
	 * Return the source at {@code index}, where only the subseismo component is included
	 * (no truly off fault component).  This returns null if there is no subseismo component
	 * for the grid node
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param crosshair sources if true,
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceSubseismoOnly(int index, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);

	/**
	 * Return the source at {@code index}, where only the truly off fault component is included
	 * (no subseismo component).  This returns null if there is no truly off fault component
	 * for the grid node
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param crosshair sources if true,
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceTrulyOffOnly(int index, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);

	
	
//	/**
//	 * Set whether all sources should just be treated as point sources, not just
//	 * those with M&leq;6.0
//	 * 
//	 * @param usePoints
//	 */
//	public void setAsPointSources(boolean usePoints);

	/**
	 * Returns the unassociated MFD of a grid node.
	 * @param idx node index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getNodeUnassociatedMFD(int idx);
	
	/**
	 * Returns the sub-seismogenic MFD associated with a grid node, if any
	 * exists.
	 * @param idx node index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getNodeSubSeisMFD(int idx);
	
	/**
	 * Returns the MFD associated with a grid node trimmed to the supplied 
	 * minimum magnitude and the maximum non-zero magnitude.
	 * 
	 * @param idx node index
	 * @param minMag minimum magniitude to trim MFD to
	 * @return the trimmed MFD
	 */
	public IncrementalMagFreqDist getNodeMFD(int idx, double minMag);
	
	/**
	 * Returns the MFD associated with a grid node. This is the sum of any
	 * unassociated and sub-seismogenic MFDs for the node.
	 * @param idx node index
	 * @return the MFD
	 * @see UCERF3_GridSourceGenerator#getNodeUnassociatedMFD(int)
	 * @see UCERF3_GridSourceGenerator#getNodeSubSeisMFD(int)
	 */
	public IncrementalMagFreqDist getNodeMFD(int idx);
	
	/**
	 * Returns the gridded region associated with these grid sources.
	 * @return the gridded region
	 */
	public GriddedRegion getGriddedRegion();
	
	/**
	 * Returns the fraction of focal mechanisms at this node that are strike slip
	 * @param idx
	 * @return
	 */
	public abstract double getFracStrikeSlip(int idx);

	/**
	 * Returns the fraction of focal mechanisms at this node that are reverse
	 * @param idx
	 * @return
	 */
	public abstract double getFracReverse(int idx);

	/**
	 * Returns the fraction of focal mechanisms at this node that are normal
	 * @param idx
	 * @return
	 */
	public abstract double getFracNormal(int idx);

}
