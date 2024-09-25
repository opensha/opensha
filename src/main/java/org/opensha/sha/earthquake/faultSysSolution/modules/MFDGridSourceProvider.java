package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;

public interface MFDGridSourceProvider extends GridSourceProvider {
	
	/**
	 * @return the {@link TectonicRegionType} at the given grid location
	 */
	public TectonicRegionType getTectonicRegionType(int gridIndex);
	
	public default TectonicRegionType[] getTectonicRegionTypeArray() {
		TectonicRegionType[] ret = new TectonicRegionType[getNumLocations()];
		for (int i=0; i<ret.length; i++)
			ret[i] = getTectonicRegionType(i);
		return ret;
	}
	
	/**
	 * Return the source at {@code gridIndex}.
	 * 
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSource(int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);
	

	/**
	 * Return the source at {@code gridIndex}, where only the on-fault sub-seismogenic component is included
	 * (no seismicity that is unassociated with modeled faults).  This returns null if there is no on-fault
	 * sub-seismogenic component for the grid location
	 * 
	 * @param index of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceSubSeisOnFault(int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);

	/**
	 * Return the source at {@code gridIndex}, where only the component that is unassociated with modeled faults
	 * included (no on-fault sub-seismogenic component). This returns null if there is no unassociated component
	 * for the grid location
	 * 
	 * @param gridIndex of source to retrieve
	 * @param duration of forecast
	 * @param aftershockFilter if non-null, function that will be used to scale rupture rates for aftershocks in the
	 * form scaledRate = aftershockFilter(magnitude, rate)
	 * @param bgRupType type of source to build
	 * @return the source at {@code index}
	 */
	public ProbEqkSource getSourceUnassociated(int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType);

	@Override
	default ProbEqkSource getSource(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getSource(gridIndex, duration, aftershockFilter, bgRupType);
		return null;
	}

	@Override
	default ProbEqkSource getSourceSubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getSourceSubSeisOnFault(gridIndex, duration, aftershockFilter, bgRupType);
		return null;
	}

	@Override
	default ProbEqkSource getSourceUnassociated(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getSourceUnassociated(gridIndex, duration, aftershockFilter, bgRupType);
		return null;
	}

	@Override
	default Set<TectonicRegionType> getTectonicRegionTypes() {
		TectonicRegionType firstTRT = getTectonicRegionType(0);
		EnumSet<TectonicRegionType> trts = EnumSet.of(firstTRT);
		for (int i=1; i<getNumLocations(); i++) {
			TectonicRegionType trt = getTectonicRegionType(i);
			if (trt != firstTRT)
				trts.add(trt);
		}
		return trts;
	}

	@Override
	default IncrementalMagFreqDist getMFD_Unassociated(TectonicRegionType tectonicRegionType, int gridIndex) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getMFD_Unassociated( gridIndex);
		return null;
	}

	@Override
	default IncrementalMagFreqDist getMFD_SubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getMFD_SubSeisOnFault(gridIndex);
		return null;
	}

	@Override
	default IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex, double minMag) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getMFD(gridIndex, minMag);
		return null;
	}

	@Override
	default IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex) {
		if (tectonicRegionType == null || tectonicRegionType == getTectonicRegionType(gridIndex))
			return getMFD(gridIndex);
		return null;
	}

	@Override
	default void scaleAll(TectonicRegionType tectonicRegionType, double[] valuesArray) {
		if (tectonicRegionType != null && getTectonicRegionTypes().size() > 1) {
			// make sure it's unity or NaN anywhere not of this trt
			
			// start by making a copy
			valuesArray = Arrays.copyOf(valuesArray, valuesArray.length);
			for (int i=0; i<valuesArray.length; i++) {
				TectonicRegionType trt = getTectonicRegionType(i);
				if (trt != tectonicRegionType) {
					Preconditions.checkState(Double.isNaN(valuesArray[i]) || valuesArray[i] == 1d || valuesArray[i] == 0d,
							"Gave a non-placeholder scalar for node %s, but it has a TRT type of %s rather than the specified %s",
							i, trt, tectonicRegionType);
					if (valuesArray[i] != 1d)
						valuesArray[i] = 1d;
				}
			}
		}
		scaleAll(valuesArray);
	}

	@Override
	public default AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		return new MFDGridSourceProvider.Averager();
	}
	
	@Override
	public default Location getLocation(int index) {
		return getGriddedRegion().getLocation(index);
	}
	
	@Override
	default Location getLocationForSource(int sourceIndex) {
		return getLocation(sourceIndex);
	}
	
	@Override
	default int getLocationIndexForSource(int sourceIndex) {
		return sourceIndex;
	}

	@Override
	default int getLocationIndex(Location location) {
		return getGriddedRegion().indexForLocation(location);
	}

	public default int getNumSources() {
		return getNumLocations();
	}

	@Override
	default GridSourceProvider getAboveMinMag(float minMag) {
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = new HashMap<>();
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = new HashMap<>(getNumLocations());
		double[] fracStrikeSlip = new double[getNumLocations()];
		double[] fracNormal = new double[fracStrikeSlip.length];
		double[] fracReverse = new double[fracStrikeSlip.length];
		TectonicRegionType[] trts = new TectonicRegionType[fracStrikeSlip.length];
		
		boolean alreadyAbove = true;
		double snappedMinMag = Double.NaN;
		for (int i=0; i<fracStrikeSlip.length; i++) {
			fracStrikeSlip[i] = getFracStrikeSlip(i);
			fracNormal[i] = getFracNormal(i);
			fracReverse[i] = getFracReverse(i);
			trts[i] = getTectonicRegionType(i);
			IncrementalMagFreqDist subSeisMFD = getMFD_SubSeisOnFault(i);
			IncrementalMagFreqDist unassocMFD = getMFD_Unassociated(i);
			if (subSeisMFD != null || unassocMFD != null) {
				double myMin = Double.POSITIVE_INFINITY;
				if (subSeisMFD != null)
					myMin = subSeisMFD.getMinX();
				if (unassocMFD != null)
					myMin = Math.min(myMin, unassocMFD.getMinX());
				if ((float)myMin < minMag) {
					// need to filter
					if (alreadyAbove) {
						// first time we're below
						alreadyAbove = false;
						// snap the minimum magnitude to our MFD gridding
						IncrementalMagFreqDist mfd = unassocMFD == null ? subSeisMFD : unassocMFD;
						int index = mfd.getClosestXIndex((double)minMag);
						snappedMinMag = mfd.getX(index);
						if ((float)snappedMinMag < minMag) {
							snappedMinMag = mfd.getX(index+1);
							Preconditions.checkState((float)snappedMinMag >= minMag);
						}
					}
					if (subSeisMFD != null)
						nodeSubSeisMFDs.put(i, trimMFD(subSeisMFD, snappedMinMag));
					if (unassocMFD != null)
						nodeUnassociatedMFDs.put(i, trimMFD(unassocMFD, snappedMinMag));
				} else {
					if (subSeisMFD != null)
						nodeSubSeisMFDs.put(i, subSeisMFD);
					if (unassocMFD != null)
						nodeUnassociatedMFDs.put(i, unassocMFD);
				}
			}
		}
		if (alreadyAbove)
			return this;
		
		return newInstance(nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, trts);
	}

	/**
	 * Creates a new instance of this same type, but with the given data. Used primarily to create new instances when
	 * branch averaging by {@link MFDGridSourceProvider.Averager}.
	 * 
	 * @param nodeSubSeisMFDs
	 * @param nodeUnassociatedMFDs
	 * @param fracStrikeSlip
	 * @param fracNormal
	 * @param fracReverse
	 * @return
	 */
	public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip,
				double[] fracNormal, double[] fracReverse, TectonicRegionType[] trts);
	
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
	public abstract class Abstract implements MFDGridSourceProvider {
		
		private double minMagCutoff;
	
		public Abstract(double minMagCutoff) {
			this.minMagCutoff = minMagCutoff;
		}
		
		/**
		 * Sets the minimum magnitude of ruptures to include when building sources for hazard calculation
		 * 
		 * @param minMagCutoff
		 */
		public void setSourceMinMagCutoff(double minMagCutoff) {
			this.minMagCutoff = minMagCutoff;
		}
		
		/**
		 * @return the minimum magnitude of ruptures to include when building sources for hazard calculation
		 */
		public double getSourceMinMagCutoff() {
			return minMagCutoff;
		}
		
		/**
		 * Builds a source for hazard calculation for the given MFD, which will already be trimmed such that it starts
		 * at/above {@link #getSourceMinMagCutoff()}.
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
		public int getNumLocations() {
			return getGriddedRegion().getNodeCount();
		}
		
		@Override
		public IncrementalMagFreqDist getMFD(int idx, double minMag) {
			return trimMFD(getMFD(idx), minMag);
			
			// NOTE trimMFD clones the MFD returned by getMFD so its safe for
			// subsequent modification; if this changes, then we need to review if
			// MFD is safe from alteration.
		}
		
		private void applyAftershockFilter(IncrementalMagFreqDist mfd, DoubleBinaryOperator aftershockFilter) {
			for (int i=0; i<mfd.size(); i++) {
				double rate = mfd.getY(i);
				if (rate > 0d) {
					double mag = mfd.getX(i);
					mfd.set(i, aftershockFilter.applyAsDouble(mag, rate));
				}
			}
		}
	
		@Override
		public ProbEqkSource getSource(int gridIndex, double duration, DoubleBinaryOperator aftershockFilter,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD(gridIndex, minMagCutoff);
			if (mfd == null)
				return null;
			if (aftershockFilter != null)
				applyAftershockFilter(mfd, aftershockFilter);
			return buildSource(gridIndex, mfd, duration, bgRupType);
		}
	
		@Override
		public ProbEqkSource getSourceSubSeisOnFault(int gridIndex, double duration, DoubleBinaryOperator aftershockFilter,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD_SubSeisOnFault(gridIndex);
			if(mfd == null)
				return null;
			// trim it
			mfd = trimMFD(mfd, minMagCutoff);
			if (aftershockFilter != null)
				applyAftershockFilter(mfd, aftershockFilter);
			return buildSource(gridIndex, mfd, duration, bgRupType);
		}
	
		@Override
		public ProbEqkSource getSourceUnassociated(int gridIndex, double duration, DoubleBinaryOperator aftershockFilter,
				BackgroundRupType bgRupType) {
			IncrementalMagFreqDist mfd = getMFD_Unassociated(gridIndex);
			if(mfd == null)
				return null;
			// trim it
			mfd = trimMFD(mfd, minMagCutoff);
			if (aftershockFilter != null)
				applyAftershockFilter(mfd, aftershockFilter);
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
			runningMFD.set(i, Math.fma(newMFD.getY(i), weight, runningMFD.getY(i)));
		return runningMFD;
	}
	
	public class Averager implements AveragingAccumulator<GridSourceProvider> {

		private MFDGridSourceProvider refGridProv = null;
		private GriddedRegion gridReg = null;
		private Map<Integer, IncrementalMagFreqDist> subSeisMFDs = null;
		private Map<Integer, IncrementalMagFreqDist> unassociatedMFDs = null;

		private double totWeight = 0;

		private double[] fractSS, fractR, fractN;
		
		private TectonicRegionType[] trts;

		@Override
		public void process(GridSourceProvider module, double relWeight) {
			Preconditions.checkState(module instanceof MFDGridSourceProvider,
					"Only support averaging MFDGridSoruceProvider instances");
			MFDGridSourceProvider gridProv = (MFDGridSourceProvider)module;
			if (gridReg == null) {
				Preconditions.checkState(totWeight == 0d, "Can't reuse an averager after getAverage called");
				refGridProv = gridProv;
				gridReg = gridProv.getGriddedRegion();
				Preconditions.checkNotNull(gridReg, "GriddedRegion cannot be null when doing MFD-based averaging");
				subSeisMFDs = new HashMap<>();
				unassociatedMFDs = new HashMap<>();

				fractSS = new double[gridProv.getNumLocations()];
				fractR = new double[fractSS.length];
				fractN = new double[fractSS.length];
				trts = new TectonicRegionType[fractSS.length];
				for (int i=0; i<trts.length; i++)
					trts[i] = gridProv.getTectonicRegionType(i);
			} else {
				Preconditions.checkState(gridReg.equalsRegion(gridProv.getGriddedRegion()));
			}
			totWeight += relWeight;
			for (int i=0; i<gridReg.getNodeCount(); i++) {
				addWeighted(subSeisMFDs, i, gridProv.getMFD_SubSeisOnFault(i), relWeight);
				addWeighted(unassociatedMFDs, i, gridProv.getMFD_Unassociated(i), relWeight);
				fractSS[i] += gridProv.getFracStrikeSlip(i)*relWeight;
				fractR[i] += gridProv.getFracReverse(i)*relWeight;
				fractN[i] += gridProv.getFracNormal(i)*relWeight;
				Preconditions.checkState(trts[i] == gridProv.getTectonicRegionType(i));
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

			MFDGridSourceProvider ret = refGridProv.newInstance(subSeisMFDs, unassociatedMFDs, fractSS, fractN, fractR, trts);
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
		private TectonicRegionType[] trts;
		
		private boolean round = true;
		
		protected AbstractPrecomputed(double minMagCutoff) {
			// used for serialization
			super(minMagCutoff);
		}
	
		public AbstractPrecomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip,
				double[] fracNormal, double[] fracReverse, TectonicRegionType[] trts, double minMagCutoff) {
			super(minMagCutoff);
			this.region = region;
			this.nodeSubSeisMFDs = ImmutableMap.copyOf(nodeSubSeisMFDs);
			this.nodeUnassociatedMFDs = ImmutableMap.copyOf(nodeUnassociatedMFDs);
			this.fracStrikeSlip = fracStrikeSlip;
			this.fracNormal = fracNormal;
			this.fracReverse = fracReverse;
			this.trts = trts;
		}
	
		public AbstractPrecomputed(GriddedRegion region, CSVFile<String> subSeisCSV,
				CSVFile<String> unassociatedCSV, CSVFile<String> mechCSV, double minMagCutoff) {
			super(minMagCutoff);
			init(region, subSeisCSV, unassociatedCSV, mechCSV);
		}
	
		public AbstractPrecomputed(Abstract prov) {
			this(prov, prov.getSourceMinMagCutoff());
		}
		
		public AbstractPrecomputed(MFDGridSourceProvider prov, double minMagCutoff) {
			super(minMagCutoff);
			this.region = prov.getGriddedRegion();
			int nodeCount = region.getNodeCount();
			Builder<Integer, IncrementalMagFreqDist> subSeisBuilder = ImmutableMap.builder();
			Builder<Integer, IncrementalMagFreqDist> unassociatedBuilder = ImmutableMap.builder();
			fracStrikeSlip = new double[nodeCount];
			fracNormal = new double[nodeCount];
			fracReverse = new double[nodeCount];
			trts = new TectonicRegionType[nodeCount];
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
				trts[i] = prov.getTectonicRegionType(i);
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
		
		private CSVFile<String> buildTRTCSV() {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Node Index", "Tectonic Regime");
			for (int i=0; i<getNumLocations(); i++)
				csv.addLine(i+"", getTectonicRegionType(i).name()+"");
			return csv;
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
			header.add("Tectonic Regime");
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
				line.add(getTectonicRegionType(i).name());
				csv.addLine(line);
			}
			return csv;
		}
	
		@Override
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			CSVFile<String> subSeisCSV = buildCSV(nodeSubSeisMFDs);
			CSVFile<String> unassociatedCSV = buildCSV(nodeUnassociatedMFDs);
			
			Feature regFeature = region.toFeature();
			OutputStreamWriter writer = new OutputStreamWriter(FileBackedModule.initOutputStream(
					output, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME));
			Feature.write(regFeature, writer);
			writer.flush();
			output.closeEntry();
			
			if (subSeisCSV != null)
				CSV_BackedModule.writeToArchive(subSeisCSV, output, entryPrefix, MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME);
			if (unassociatedCSV != null)
				CSV_BackedModule.writeToArchive(unassociatedCSV, output, entryPrefix, MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME);
			CSV_BackedModule.writeToArchive(buildWeightsCSV(), output, entryPrefix, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
		}
	
		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			// load MFDs
			CSVFile<String> subSeisCSV = loadCSV(input, entryPrefix, MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME);
			CSVFile<String> nodeUnassociatedCSV = loadCSV(input, entryPrefix, MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME);
			
			// load mechanisms
			CSVFile<String> mechCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
			
			GriddedRegion region;
			if (FileBackedModule.hasEntry(input, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)) {
				// load gridded region
				BufferedInputStream regionIS = FileBackedModule.getInputStream(input, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
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
			trts = new TectonicRegionType[region.getNodeCount()];
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
				List<String> line = mechCSV.getLine(row);
				if (line.size() < 7)
					trts[i] = TectonicRegionType.ACTIVE_SHALLOW;
				else
					trts[i] = TectonicRegionType.valueOf(line.get(6));
			}
		}
		
		public static CSVFile<String> loadCSV(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
			String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
			Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
			if (!input.hasEntry(entryName))
				return null;
			
			return CSVFile.readStream(new BufferedInputStream(input.getInputStream(entryName)), true);
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
		public void scaleAll(double[] valuesArray) {
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

		@Override
		public TectonicRegionType getTectonicRegionType(int gridIndex) {
			return trts[gridIndex];
		}
		
	}

	/**
	 * Default MFDGridSourceProvider instance that will be loaded if no implementation is specifies. Currently defaults
	 * to UCERF3 grid source treatment, but that is subject to change (will likely when NSHM23 is released).
	 * 
	 * @author kevin
	 */
	public static class Default extends AbstractPrecomputed {
		
		private Default() {
			super(AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}
		
		public Default(MFDGridSourceProvider prov) {
			super(prov, AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}
	
		public Default(GriddedRegion region, CSVFile<String> subSeisCSV, CSVFile<String> unassociatedCSV,
				CSVFile<String> mechCSV) {
			super(region, subSeisCSV, unassociatedCSV, mechCSV, AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}
	
		public Default(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			super(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, trts,
					AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF);
		}
	
		@Override
		public String getName() {
			return "Precomputed Default Grid Source Provider";
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
		public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			return new Default(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, trts);
		}

		@Override
		public TectonicRegionType getTectonicRegionType(int gridIndex) {
			return TectonicRegionType.ACTIVE_SHALLOW;
		}
		
	}

}
