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
 * Interface implemented by providers of gridded (sometimes referred to as
 * 'other') seismicity sources.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface GridSourceProvider extends BranchAverageableModule<GridSourceProvider> {

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
	
	/**
	 * Scales all MFDs by the given values, and throws an exception if the array size is not equal to the
	 * number of nodes in the gridded region
	 * 
	 * @param valuesArray
	 */
	public void scaleAllNodeMFDs(double[] valuesArray);

	@Override
	default AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		return new AveragingAccumulator<GridSourceProvider>() {
			
			private GridSourceProvider refGridProv = null;
			private GriddedRegion gridReg = null;
			private Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = null;
			private Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = null;
			
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
					nodeSubSeisMFDs = new HashMap<>();
					nodeUnassociatedMFDs = new HashMap<>();
					
					fractSS = new double[refGridProv.size()];
					fractR = new double[fractSS.length];
					fractN = new double[fractSS.length];
				} else {
					Preconditions.checkState(gridReg.equalsRegion(module.getGriddedRegion()));
				}
				totWeight += relWeight;
				for (int i=0; i<gridReg.getNodeCount(); i++) {
					addWeighted(nodeSubSeisMFDs, i, module.getNodeSubSeisMFD(i), relWeight);
					addWeighted(nodeUnassociatedMFDs, i, module.getNodeUnassociatedMFD(i), relWeight);
					fractSS[i] += module.getFracStrikeSlip(i)*relWeight;
					fractR[i] += module.getFracReverse(i)*relWeight;
					fractN[i] += module.getFracNormal(i)*relWeight;
				}
			}

			@Override
			public GridSourceProvider getAverage() {
				double scale = 1d/totWeight;
				for (int i=0; i<fractSS.length; i++) {
					IncrementalMagFreqDist subSeisMFD = nodeSubSeisMFDs.get(i);
					if (subSeisMFD != null)
						subSeisMFD.scale(scale);
					IncrementalMagFreqDist nodeUnassociatedMFD = nodeUnassociatedMFDs.get(i);
					if (nodeUnassociatedMFD != null)
						nodeUnassociatedMFD.scale(scale);
					fractSS[i] *= scale;
					fractR[i] *= scale;
					fractN[i] *= scale;
				}
				
				return new AbstractGridSourceProvider.Precomputed(refGridProv.getGriddedRegion(),
						nodeSubSeisMFDs, nodeUnassociatedMFDs, fractSS, fractN, fractR);
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
