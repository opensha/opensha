package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;

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
 * @see AbstractGridSourceProvider
 */
public interface GridSourceProvider extends BranchAverageableModule<GridSourceProvider> {

	/**
	 * Returns the number of sources in the provider.
	 * @return the number of sources
	 */
	public int size();

	/**
	 * Return the source at {@code gridIndex}.
	 * 
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSource(int gridIndex, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);
	

	/**
	 * Return the source at {@code gridIndex}, where only the on-fault sub-seismogenic component is included
	 * (no seismicity that is unassociated with modeled faults).  This returns null if there is no on-fault
	 * sub-seismogenic component for the grid location
	 * 
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceSubSeisOnFault(int gridIndex, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);

	/**
	 * Return the source at {@code gridIndex}, where only the component that is unassociated with modeled faults
	 * included (no on-fault sub-seismogenic component). This returns null if there is no unassociated component
	 * for the grid location
	 * 
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param filterAftershocks
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceUnassociated(int gridIndex, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType);

	/**
	 * Returns the unassociated MFD of a grid location, if any exists, null otherwise.
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_Unassociated(int gridIndex);
	
	/**
	 * Returns the on-fault sub-seismogenic MFD associated with a grid location, if any
	 * exists, null otherwise
	 * @param gridIndex grid index
	 * @return the MFD
	 */
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(int gridIndex);
	
	/**
	 * Returns the MFD associated with a grid location trimmed to the supplied 
	 * minimum magnitude and the maximum non-zero magnitude.
	 * 
	 * @param gridIndex grid index
	 * @param minMag minimum magnitude to trim MFD to
	 * @return the trimmed MFD
	 */
	public IncrementalMagFreqDist getMFD(int gridIndex, double minMag);
	
	/**
	 * Returns the MFD associated with a grid location. This is the sum of any
	 * unassociated and sub-seismogenic MFDs for the location.
	 * 
	 * @param gridIndex grid index
	 * @return the MFD
	 * @see UCERF3_GridSourceGenerator#getMFD_Unassociated(int)
	 * @see UCERF3_GridSourceGenerator#getMFD_SubSeisOnFault(int)
	 */
	public IncrementalMagFreqDist getMFD(int gridIndex);
	
	/**
	 * Returns the gridded region associated with these grid sources.
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
	 * Scales all MFDs by the given values, and throws an exception if the array size is not equal to the
	 * number of locations in the gridded region
	 * 
	 * @param valuesArray
	 */
	public void scaleAllMFDs(double[] valuesArray);

	@Override
	default AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		return new AveragingAccumulator<GridSourceProvider>() {
			
			private GridSourceProvider refGridProv = null;
			private GriddedRegion gridReg = null;
			private Map<Integer, IncrementalMagFreqDist> subSeisMFDs = null;
			private Map<Integer, IncrementalMagFreqDist> unassociatedMFDs = null;
			
			private double totWeight = 0;
			
			private double[] fractSS, fractR, fractN;

			@Override
			public Class<GridSourceProvider> getType() {
				return GridSourceProvider.class;
			}

			@Override
			public void process(GridSourceProvider module, double relWeight) {
				if (refGridProv == null) {
					refGridProv = module;
					gridReg = module.getGriddedRegion();
					subSeisMFDs = new HashMap<>();
					unassociatedMFDs = new HashMap<>();
					
					fractSS = new double[refGridProv.size()];
					fractR = new double[fractSS.length];
					fractN = new double[fractSS.length];
				} else {
					Preconditions.checkState(gridReg.equalsRegion(module.getGriddedRegion()));
				}
				totWeight += relWeight;
				for (int i=0; i<gridReg.getNodeCount(); i++) {
					addWeighted(subSeisMFDs, i, module.getMFD_SubSeisOnFault(i), relWeight);
					addWeighted(unassociatedMFDs, i, module.getMFD_Unassociated(i), relWeight);
					fractSS[i] += module.getFracStrikeSlip(i)*relWeight;
					fractR[i] += module.getFracReverse(i)*relWeight;
					fractN[i] += module.getFracNormal(i)*relWeight;
				}
			}

			@Override
			public GridSourceProvider getAverage() {
				double scale = 1d/totWeight;
				for (int i=0; i<fractSS.length; i++) {
					IncrementalMagFreqDist subSeisMFD = subSeisMFDs.get(i);
					if (subSeisMFD != null)
						subSeisMFD.scale(scale);
					IncrementalMagFreqDist unassociatedMFD = unassociatedMFDs.get(i);
					if (unassociatedMFD != null)
						unassociatedMFD.scale(scale);
					fractSS[i] *= scale;
					fractR[i] *= scale;
					fractN[i] *= scale;
				}
				
				return new AbstractGridSourceProvider.Precomputed(refGridProv.getGriddedRegion(),
						subSeisMFDs, unassociatedMFDs, fractSS, fractN, fractR);
			}
		};
	}
	
	public static void addWeighted(Map<Integer, IncrementalMagFreqDist> mfdMap, int index,
			IncrementalMagFreqDist newMFD, double weight) {
		if (newMFD == null)
			// simple case
			return;
		IncrementalMagFreqDist runningMFD = mfdMap.get(index);
		if (runningMFD == null) {
			runningMFD = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
			mfdMap.put(index, runningMFD);
		}
		addWeighted(runningMFD, newMFD, weight);
	}
	
	public static void addWeighted(IncrementalMagFreqDist runningMFD,
			IncrementalMagFreqDist newMFD, double weight) {
		Preconditions.checkState(runningMFD.size() == newMFD.size(), "MFD sizes inconsistent");
		Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
		Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
		for (int i=0; i<runningMFD.size(); i++)
			runningMFD.add(i, newMFD.getY(i)*weight);
	}

}
