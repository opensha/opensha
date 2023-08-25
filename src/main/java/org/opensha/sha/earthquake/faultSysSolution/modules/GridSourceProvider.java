package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

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
public interface GridSourceProvider extends OpenSHA_Module, BranchAverageableModule<GridSourceProvider> {

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
	public default AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		return new Averager();
	}
	
	/**
	 * Creates a new instance of this same type, but with the given data. Used primarily to create new instances when
	 * branch averaging by {@link Averager}.
	 * 
	 * @param nodeSubSeisMFDs
	 * @param nodeUnassociatedMFDs
	 * @param fracStrikeSlip
	 * @param fracNormal
	 * @param fracReverse
	 * @return
	 */
	public GridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip,
				double[] fracNormal, double[] fracReverse);
	
	/**
	 * Abstract implementation of a {@link GridSourceProvider} that handles trimming MFDs to a minimum magnitude,
	 * combining sub-seismogenic and unassociated MFDs for a given grid node, and averaging across multiple instances.
	 * 
	 * Storing/building raw MFDs, aftershock filtering, and building a source from an MFD is left to the implementing
	 * class as they may be model-specific.
	 * 
	 * @author kevin
	 *
	 */
	public abstract class Abstract implements GridSourceProvider {
		
		private double minMagCutoff;

		public Abstract(double minMagCutoff) {
			this.minMagCutoff = minMagCutoff;
		}
		
		/**
		 * Sets the minimum magnitude of ruptures to include when building sources for hazard calculation
		 * 
		 * @param minMagCutoff
		 */
		public void setMinMagCutoff(double minMagCutoff) {
			this.minMagCutoff = minMagCutoff;
		}
		
		/**
		 * @return the minimum magnitude of ruptures to include when building sources for hazard calculation
		 */
		public double getMinMagCutoff() {
			return minMagCutoff;
		}
		
		/**
		 * Will be called if a source is requested with aftershocks filterd. This MFD can and should be modified in place
		 * @param mfd
		 */
		public abstract void applyAftershockFilter(IncrementalMagFreqDist mfd);
		
		/**
		 * Builds a source for hazard calculation for the given MFD, which will already be trimmed such that it starts
		 * at/above {@link #getMinMagCutoff()}.
		 * 
		 * @param gridIndex
		 * @param mfd
		 * @param duration
		 * @param bgRupType
		 * @return source
		 */
		protected abstract ProbEqkSource buildSource(int gridIndex, IncrementalMagFreqDist mfd,
				double duration, BackgroundRupType bgRupType);

		@Override
		public int size() {
			return getGriddedRegion().getNodeCount();
		}
		
		@Override
		public IncrementalMagFreqDist getMFD(int idx, double minMag) {
			return trimMFD(getMFD(idx), minMag);
			
			// NOTE trimMFD clones the MFD returned by getMFD so its safe for
			// subsequent modification; if this changes, then we need to review if
			// MFD is safe from alteration.
		}

		@Override
		public ProbEqkSource getSource(int gridIndex, double duration, boolean filterAftershocks,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD(gridIndex, minMagCutoff);
			if (mfd == null)
				return null;
			if (filterAftershocks)
				applyAftershockFilter(mfd);
			return buildSource(gridIndex, mfd, duration, bgRupType);
		}

		@Override
		public ProbEqkSource getSourceSubSeisOnFault(int gridIndex, double duration, boolean filterAftershocks,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD_SubSeisOnFault(gridIndex);
			if(mfd == null)
				return null;
			// trim it
			mfd = trimMFD(mfd, minMagCutoff);
			if (filterAftershocks)
				applyAftershockFilter(mfd);
			return buildSource(gridIndex, mfd, duration, bgRupType);
		}

		@Override
		public ProbEqkSource getSourceUnassociated(int gridIndex, double duration, boolean filterAftershocks,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD_Unassociated(gridIndex);
			if(mfd == null)
				return null;
			// trim it
			mfd = trimMFD(mfd, minMagCutoff);
			if (filterAftershocks)
				applyAftershockFilter(mfd);
			return buildSource(gridIndex, mfd, duration, bgRupType);
		}
		
		@Override
		public IncrementalMagFreqDist getMFD(int idx) {
			IncrementalMagFreqDist nodeIndMFD = getMFD_Unassociated(idx);
			IncrementalMagFreqDist nodeSubMFD = getMFD_SubSeisOnFault(idx);
			if (nodeIndMFD == null) return nodeSubMFD;
			if (nodeSubMFD == null) return nodeIndMFD;
			
			Preconditions.checkState((float)nodeIndMFD.getMinX() == (float)nodeSubMFD.getMinX(),
					"Sub-seismo and unassociated MFDs have different minX for grid node %x: %x != %x",
					idx, nodeIndMFD.getMinX(), nodeSubMFD.getMinX());
			Preconditions.checkState((float)nodeIndMFD.getDelta() == (float)nodeSubMFD.getDelta(),
					"Sub-seismo and unassociated MFDs have different spacings for grid node %x: %x != %x",
					idx, nodeIndMFD.getDelta(), nodeSubMFD.getDelta());
			
			int retSize = Integer.max(nodeIndMFD.size(), nodeSubMFD.size());
			SummedMagFreqDist sumMFD = new SummedMagFreqDist(nodeIndMFD.getMinX(), retSize, nodeIndMFD.getDelta());
			sumMFD.addIncrementalMagFreqDist(nodeSubMFD);
			sumMFD.addIncrementalMagFreqDist(nodeIndMFD);
			return sumMFD;
		}
		
	}

	/*
	 * Utility to trim the supplied MFD to the supplied min mag and the maximum
	 * non-zero mag. This method makes the assumtions that the min mag of the 
	 * supplied mfd is lower then the mMin, and that mag bins are centered on
	 * 0.05.
	 */
	private static IncrementalMagFreqDist trimMFD(IncrementalMagFreqDist mfdIn, double mMin) {
		if (mfdIn == null)
			return new IncrementalMagFreqDist(mMin,mMin,1);
		// in GR nofix branches there are mfds with all zero rates
		double mMax = mfdIn.getMaxMagWithNonZeroRate();
		if (Double.isNaN(mMax)) {
			IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin,mMin,1);
			return mfdOut;
		}
		double delta = mfdIn.getDelta();
		// if delta get's slightly off, the inner part of this cast can be something like 0.99999999, which
		// will mess up the num calculation. pad by 0.1 to be safe before casting
		int num = (int) ((mMax - mMin) / delta + 0.1) + 1;
//		IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, mMax, num);
		IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, num, delta);
		for (int i=0; i<mfdOut.size(); i++) {
			double mag = mfdOut.getX(i);
			double rate = mfdIn.getY(mag);
			mfdOut.set(mag, rate);
		}
		Preconditions.checkState((float)mfdOut.getMaxX() == (float)mMax,
				"Bad trim! mMin=%s, mMax=%s, delta=%s, num=%s, outputMMax=%s",
				mMin, mMax, delta, num, mfdOut.getMaxX());
		return mfdOut;
	}
	
	public class Averager implements AveragingAccumulator<GridSourceProvider> {
		
		private GridSourceProvider refGridProv = null;
		private GriddedRegion gridReg = null;
		private Map<Integer, IncrementalMagFreqDist> subSeisMFDs = null;
		private Map<Integer, IncrementalMagFreqDist> unassociatedMFDs = null;
		
		private double totWeight = 0;
		
		private double[] fractSS, fractR, fractN;

		@Override
		public void process(GridSourceProvider module, double relWeight) {
			if (refGridProv == null) {
				Preconditions.checkState(totWeight == 0d, "Can't reuse an averager after getAverage called");
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
			
			GridSourceProvider ret = refGridProv.newInstance(subSeisMFDs, unassociatedMFDs, fractSS, fractN, fractR);
			// can't reuse this
			subSeisMFDs = null;
			unassociatedMFDs = null;
			fractSS = null;
			fractN = null;
			fractR = null;
			refGridProv = null;
			return ret;
		}

		@Override
		public Class<GridSourceProvider> getType() {
			return GridSourceProvider.class;
		}
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
		IncrementalMagFreqDist ret = addWeighted(runningMFD, newMFD, weight);
		if (ret != runningMFD)
			// we grew it
			mfdMap.put(index, ret);
	}
	
	public static IncrementalMagFreqDist addWeighted(IncrementalMagFreqDist runningMFD,
			IncrementalMagFreqDist newMFD, double weight) {
		Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
		Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
		if (runningMFD.size() < newMFD.size()) {
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
			for (int i=0; i<runningMFD.size(); i++)
				ret.set(i, runningMFD.getY(i));
			runningMFD = ret;
		}
		for (int i=0; i<newMFD.size(); i++)
			runningMFD.add(i, newMFD.getY(i)*weight);
		return runningMFD;
	}
	
	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	public static final String ARCHIVE_MECH_WEIGHT_FILE_NAME = "grid_mech_weights.csv";
	public static final String ARCHIVE_SUB_SEIS_FILE_NAME = "grid_sub_seis_mfds.csv";
	public static final String ARCHIVE_UNASSOCIATED_FILE_NAME = "grid_unassociated_mfds.csv";
	
	/**
	 * Abstract {@link GridSourceProvider} implementation where all MFDs have been precomputed.
	 * 
	 * Aftershock filtering, and building a source from an MFD is left to the implementing class as they may be
	 * model-specific.
	 * 
	 * @author kevin
	 *
	 */
	public abstract class AbstractPrecomputed extends Abstract implements ArchivableModule {
		
		private GriddedRegion region;
		private ImmutableMap<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs;
		private ImmutableMap<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs;
		private double[] fracStrikeSlip;
		private double[] fracNormal;
		private double[] fracReverse;
		
		private boolean round = true;
		
		protected AbstractPrecomputed(double minMagCutoff) {
			// used for serialization
			super(minMagCutoff);
		}

		public AbstractPrecomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip,
				double[] fracNormal, double[] fracReverse, double minMagCutoff) {
			super(minMagCutoff);
			this.region = region;
			this.nodeSubSeisMFDs = ImmutableMap.copyOf(nodeSubSeisMFDs);
			this.nodeUnassociatedMFDs = ImmutableMap.copyOf(nodeUnassociatedMFDs);
			this.fracStrikeSlip = fracStrikeSlip;
			this.fracNormal = fracNormal;
			this.fracReverse = fracReverse;
		}

		public AbstractPrecomputed(GriddedRegion region, CSVFile<String> subSeisCSV,
				CSVFile<String> unassociatedCSV, CSVFile<String> mechCSV, double minMagCutoff) {
			super(minMagCutoff);
			init(region, subSeisCSV, unassociatedCSV, mechCSV);
		}

		public AbstractPrecomputed(Abstract prov) {
			this(prov, prov.getMinMagCutoff());
		}
		
		public AbstractPrecomputed (GridSourceProvider prov, double minMagCutoff) {
			super(minMagCutoff);
			this.region = prov.getGriddedRegion();
			int nodeCount = region.getNodeCount();
			Builder<Integer, IncrementalMagFreqDist> subSeisBuilder = ImmutableMap.builder();
			Builder<Integer, IncrementalMagFreqDist> unassociatedBuilder = ImmutableMap.builder();
			fracStrikeSlip = new double[nodeCount];
			fracNormal = new double[nodeCount];
			fracReverse = new double[nodeCount];
			for (int i=0; i<nodeCount; i++) {
				IncrementalMagFreqDist subSeis = prov.getMFD_SubSeisOnFault(i);
				if (subSeis != null)
					subSeisBuilder.put(i, subSeis);
				IncrementalMagFreqDist unassociated = prov.getMFD_Unassociated(i);
				if (unassociated != null)
					unassociatedBuilder.put(i, unassociated);
				fracStrikeSlip[i] = prov.getFracStrikeSlip(i);
				fracNormal[i] = prov.getFracNormal(i);
				fracReverse[i] = prov.getFracReverse(i);
			}
			this.nodeSubSeisMFDs = subSeisBuilder.build();
			this.nodeUnassociatedMFDs = unassociatedBuilder.build();
		}
		
		public ImmutableMap<Integer, IncrementalMagFreqDist> getNodeSubSeisMFDs() {
			return nodeSubSeisMFDs;
		}
		
		public ImmutableMap<Integer, IncrementalMagFreqDist> getNodeUnassociatedMFDs() {
			return nodeUnassociatedMFDs;
		}
		
		public void setRound(boolean round) {
			this.round = round;
		}

		@Override
		public final IncrementalMagFreqDist getMFD_Unassociated(int idx) {
			return nodeUnassociatedMFDs.get(idx);
		}

		@Override
		public final IncrementalMagFreqDist getMFD_SubSeisOnFault(int idx) {
			return nodeSubSeisMFDs.get(idx);
		}

		@Override
		public final GriddedRegion getGriddedRegion() {
			return region;
		}

		@Override
		public final double getFracStrikeSlip(int idx) {
			return fracStrikeSlip[idx];
		}

		@Override
		public final double getFracReverse(int idx) {
			return fracReverse[idx];
		}

		@Override
		public final double getFracNormal(int idx) {
			return fracNormal[idx];
		}
		
		private static final int locRoundScale = 3;
		private static final int magRoundScale = 3;
		private static final int mfdRoundSigFigs = 6;
		
		public CSVFile<String> buildSubSeisCSV() {
			if (nodeSubSeisMFDs == null)
				return null;
			return buildCSV(nodeSubSeisMFDs);
		}
		
		public CSVFile<String> buildUnassociatedCSV() {
			if (nodeUnassociatedMFDs == null)
				return null;
			return buildCSV(nodeUnassociatedMFDs);
		}
		
		private CSVFile<String> buildCSV(Map<Integer, IncrementalMagFreqDist> mfds) {
			IncrementalMagFreqDist xVals = null;
			
			for (IncrementalMagFreqDist mfd : mfds.values()) {
				if (mfd != null) {
					if (xVals == null) {
						xVals = mfd;
					} else {
						Preconditions.checkState(xVals.getMinX() == mfd.getMinX(),
								"MFDs have different minX: %s != %s",
								xVals.getMinX(), mfd.getMinX());
						Preconditions.checkState(xVals.getDelta() == mfd.getDelta(),
								"MFDs have different deltas: %s != %s",
								xVals.getDelta(), mfd.getDelta());
						// keep the largest
						if (mfd.size() > xVals.size())
							xVals = mfd;
					}
					break;
				}
			}
			
			if (xVals == null)
				// no actual MFDs
				return null;
			CSVFile<String> csv = new CSVFile<>(true);
			List<String> header = new ArrayList<>();
			header.add("Node Index");
			header.add("Latitude");
			header.add("Longitude");
			for (int i=0; i<xVals.size(); i++)
				header.add(DataUtils.roundFixed(xVals.getX(i), magRoundScale)+"");
			csv.addLine(header);
			
			int nodeCount = region.getNodeCount();
			for (int i=0; i<nodeCount; i++) {
				IncrementalMagFreqDist mfd = mfds.get(i);
				if (mfd == null)
					continue;
				Location loc = region.getLocation(i);
				List<String> line = new ArrayList<>(header.size());
				line.add(i+"");
				line.add(DataUtils.roundFixed(loc.getLatitude(), locRoundScale)+"");
				line.add(DataUtils.roundFixed(loc.getLongitude(), locRoundScale)+"");
				Preconditions.checkState(mfd.size() <= xVals.size(),
						"MFD sizes inconsistent. Expected %s values, have %s", xVals.size(), mfd.size());
				for (int j=0; j<xVals.size(); j++) {
					if (j >= mfd.size()) {
						line.add(0d+"");
					} else {
						Preconditions.checkState((float)mfd.getX(j) == (float)xVals.getX(j),
								"MFD x value mismatch for node %s value %s", i, j);
						if (round)
							line.add(DataUtils.roundSigFigs(mfd.getY(j), mfdRoundSigFigs)+"");
						else
							line.add(mfd.getY(j)+"");
					}
				}
				csv.addLine(line);
			}
			return csv;
		}
		
		public CSVFile<String> buildWeightsCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			List<String> header = new ArrayList<>();
			header.add("Node Index");
			header.add("Latitude");
			header.add("Longitude");
			header.add("Fraction Strike-Slip");
			header.add("Fraction Reverse");
			header.add("Fraction Normal");
			csv.addLine(header);
			
			GriddedRegion region = getGriddedRegion();
			
			for (int i=0; i<region.getNodeCount(); i++) {
				Location loc = region.getLocation(i);
				List<String> line = new ArrayList<>(header.size());
				line.add(i+"");
				line.add(loc.getLatitude()+"");
				line.add(loc.getLongitude()+"");
				if (round) {
					line.add(DataUtils.roundFixed(getFracStrikeSlip(i), 6)+"");
					line.add(DataUtils.roundFixed(getFracReverse(i), 6)+"");
					line.add(DataUtils.roundFixed(getFracNormal(i), 6)+"");
				} else {
					line.add(getFracStrikeSlip(i)+"");
					line.add(getFracReverse(i)+"");
					line.add(getFracNormal(i)+"");
				}
				csv.addLine(line);
			}
			return csv;
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			CSVFile<String> subSeisCSV = buildCSV(nodeSubSeisMFDs);
			CSVFile<String> unassociatedCSV = buildCSV(nodeUnassociatedMFDs);
			
			FileBackedModule.initEntry(zout, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
			Feature regFeature = region.toFeature();
			OutputStreamWriter writer = new OutputStreamWriter(zout);
			Feature.write(regFeature, writer);
			writer.flush();
			zout.flush();
			zout.closeEntry();
			
			if (subSeisCSV != null)
				CSV_BackedModule.writeToArchive(subSeisCSV, zout, entryPrefix, ARCHIVE_SUB_SEIS_FILE_NAME);
			if (unassociatedCSV != null)
				CSV_BackedModule.writeToArchive(unassociatedCSV, zout, entryPrefix, ARCHIVE_UNASSOCIATED_FILE_NAME);
			CSV_BackedModule.writeToArchive(buildWeightsCSV(), zout, entryPrefix, ARCHIVE_MECH_WEIGHT_FILE_NAME);
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// load MFDs
			CSVFile<String> subSeisCSV = loadCSV(zip, entryPrefix, ARCHIVE_SUB_SEIS_FILE_NAME);
			CSVFile<String> nodeUnassociatedCSV = loadCSV(zip, entryPrefix, ARCHIVE_UNASSOCIATED_FILE_NAME);
			
			// load mechanisms
			CSVFile<String> mechCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ARCHIVE_MECH_WEIGHT_FILE_NAME);
			
			GriddedRegion region;
			if (FileBackedModule.hasEntry(zip, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME)) {
				// load gridded region
				BufferedInputStream regionIS = FileBackedModule.getInputStream(zip, entryPrefix, ARCHIVE_GRID_REGION_FILE_NAME);
				InputStreamReader regionReader = new InputStreamReader(regionIS);
				Feature regFeature = Feature.read(regionReader);
				region = GriddedRegion.fromFeature(regFeature);
			} else {
				// infer region from grid nodes
				System.out.println("Gridded region GeoJSON not supplied, inferring region from grid nodes in focal mechanism CSV file");
				LocationList gridNodes = new LocationList();
				for (int row=1; row<mechCSV.getNumRows(); row++) {
					int index = mechCSV.getInt(row, 0);
					Preconditions.checkState(index == row-1, "Mechanism row indexes must be in order and 0-based");
					double lat = mechCSV.getDouble(row, 1);
					double lon = mechCSV.getDouble(row, 2);
					gridNodes.add(new Location(lat, lon));
				}
				region = GriddedRegion.inferRegion(gridNodes);
			}
			
			init(region, subSeisCSV, nodeUnassociatedCSV, mechCSV);
		}
		
		public void init(GriddedRegion region, CSVFile<String> subSeisCSV,
				CSVFile<String> unassociatedCSV, CSVFile<String> mechCSV) {
			this.region = region;
			Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = csvToMFDs(region, subSeisCSV);
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = csvToMFDs(region, unassociatedCSV);
			if (nodeSubSeisMFDs == null)
				this.nodeSubSeisMFDs = ImmutableMap.of();
			else
				this.nodeSubSeisMFDs = ImmutableMap.copyOf(nodeSubSeisMFDs);
			if (nodeUnassociatedMFDs == null)
				this.nodeUnassociatedMFDs = ImmutableMap.of();
			else
				this.nodeUnassociatedMFDs = ImmutableMap.copyOf(nodeUnassociatedMFDs);
			
			Preconditions.checkState(mechCSV.getNumRows() == region.getNodeCount()+1,
					"Mechanism node count mismatch, expected %s, have %s", region.getNodeCount(), mechCSV.getNumRows()-1);
			fracStrikeSlip = new double[region.getNodeCount()];
			fracReverse = new double[region.getNodeCount()];
			fracNormal = new double[region.getNodeCount()];
			for (int i=0; i<region.getNodeCount(); i++) {
				int row = i+1;
				int index = mechCSV.getInt(row, 0);
				Preconditions.checkState(index == i, "Mechanism row indexes must be in order and 0-based");
				double lat = mechCSV.getDouble(row, 1);
				double lon = mechCSV.getDouble(row, 2);
				Location loc = region.getLocation(index);
				Preconditions.checkState((float)lat == (float)loc.getLatitude(), "Latitude mismatch at index %s: %s != %s",
						index, lat, loc.getLatitude());
				Preconditions.checkState((float)lon == (float)loc.getLongitude(), "Longitude mismatch at index %s: %s != %s",
						index, lon, loc.getLongitude());
				fracStrikeSlip[i] = mechCSV.getDouble(row, 3);
				fracReverse[i] = mechCSV.getDouble(row, 4);
				fracNormal[i] = mechCSV.getDouble(row, 5);
			}
		}
		
		public static CSVFile<String> loadCSV(ZipFile zip, String entryPrefix, String fileName) throws IOException {
			String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
			Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
			ZipEntry entry = zip.getEntry(entryName);
			if (entry == null)
				return null;
			
			return CSVFile.readStream(new BufferedInputStream(zip.getInputStream(entry)), true);
		}
		
		private static Map<Integer, IncrementalMagFreqDist> csvToMFDs(GriddedRegion region, CSVFile<String> csv) {
			if (csv == null)
				return null;
			Map<Integer, IncrementalMagFreqDist> mfds = new HashMap<>();
			double minX = csv.getDouble(0, 3);
			double maxX = csv.getDouble(0, csv.getNumCols()-1);
			int numX = csv.getNumCols()-3;
			for (int row=1; row<csv.getNumRows(); row++) {
				int index = csv.getInt(row, 0);
				Preconditions.checkState(index >= 0 && index <= region.getNodeCount(),
						"Bad grid node index: %s (max=%s)", index, region.getNodeCount());
				double lat = csv.getDouble(row, 1);
				double lon = csv.getDouble(row, 2);
				Location loc = region.getLocation(index);
				Preconditions.checkState((float)lat == (float)loc.getLatitude(), "Latitude mismatch at index %s: %s != %s",
						index, lat, loc.getLatitude());
				Preconditions.checkState((float)lon == (float)loc.getLongitude(), "Longitude mismatch at index %s: %s != %s",
						index, lon, loc.getLongitude());
				if (csv.getLine(row).size() < 4 || csv.get(row, 3).isBlank())
					continue;
				IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(minX, maxX, numX);
				for (int i=0; i<numX; i++)
					mfd.set(i, csv.getDouble(row, 3+i));
				mfds.put(index, mfd);
			}
			return mfds;
		}

		@Override
		public void scaleAllMFDs(double[] valuesArray) {
			if(valuesArray.length != getGriddedRegion().getNodeCount())
				throw new RuntimeException("Error: valuesArray must have same length as getGriddedRegion().getNodeCount()");
			for(int i=0;i<valuesArray.length;i++) {
				if(valuesArray[i] != 1.0) {
					IncrementalMagFreqDist mfd = getMFD_Unassociated(i);
					if(mfd != null)
						mfd.scale(valuesArray[i]);;
					mfd = getMFD_SubSeisOnFault(i);				
					if(mfd != null)
						mfd.scale(valuesArray[i]);;
				}
			}
		}
		
	}
	
	/**
	 * Default GridSourceProvider instance that will be loaded if no implementation is specifies. Currently defaults
	 * to UCERF3 grid source treatment, but that is subject to change (will likely when NSHM23 is released).
	 * 
	 * @author kevin
	 *
	 */
	public static class Default extends AbstractPrecomputed {
		
		private Default() {
			super(AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}
		
		public Default(GridSourceProvider prov) {
			super(prov, AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}

		public Default(GriddedRegion region, CSVFile<String> subSeisCSV, CSVFile<String> unassociatedCSV,
				CSVFile<String> mechCSV) {
			super(region, subSeisCSV, unassociatedCSV, mechCSV, AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}

		public Default(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse) {
			super(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse,
					AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}

		@Override
		public String getName() {
			return "Precomputed Default Grid Source Provider";
		}

		@Override
		public void applyAftershockFilter(IncrementalMagFreqDist mfd) {
			AbstractGridSourceProvider.applyGK_AftershockFilter(mfd);
		}

		@Override
		protected ProbEqkSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
				BackgroundRupType bgRupType) {
			Location loc = getGriddedRegion().locationForIndex(gridIndex);
			
			double fracStrikeSlip = getFracStrikeSlip(gridIndex);
			double fracNormal = getFracNormal(gridIndex);
			double fracReverse = getFracReverse(gridIndex);

			return AbstractGridSourceProvider.buildSource(
					mfd, duration, bgRupType, loc, fracStrikeSlip, fracNormal, fracReverse);
		}

		@Override
		public GridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse) {
			return new Default(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse);
		}
		
	}

}
