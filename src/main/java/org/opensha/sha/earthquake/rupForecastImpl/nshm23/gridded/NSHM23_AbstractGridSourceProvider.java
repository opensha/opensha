package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Abstract base class for an NSHM23 {@link GridSourceProvider}. This class handles serialization, averaging, and building
 * a source from an input MFD.
 * 
 * @author kevin
 * @see NSHM23_SingleRegionGridSourceProvider
 * @see Precomputed
 *
 */
public abstract class NSHM23_AbstractGridSourceProvider extends MFDGridSourceProvider.Abstract implements ArchivableModule {
	
	private static final WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();
	
	public NSHM23_AbstractGridSourceProvider() {
		super();
	}

	@Override
	public String getName() {
		return "NSHM23 Grid Source Provider";
	}
	
	public abstract FaultCubeAssociations getFaultCubeassociations();

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		new Precomputed(this).writeToArchive(output, entryPrefix);
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		throw new IllegalStateException("This should not be called (loaded as Precomputed instance)");
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return Precomputed.class;
	}

	@Override
	protected PointSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
			GriddedSeismicitySettings gridSourceSettings) {
		Location loc = getLocation(gridIndex);
		
		double fracStrikeSlip = getFracStrikeSlip(gridIndex);
		double fracNormal = getFracNormal(gridIndex);
		double fracReverse = getFracReverse(gridIndex);

		return buildSource(mfd, duration, gridSourceSettings, loc, fracStrikeSlip, fracNormal, fracReverse);
	}

	/**
	 * Builds NSHM23 source
	 * 
	 * @param mfd
	 * @param duration
	 * @param bgRupType
	 * @param loc
	 * @param fracStrikeSlip
	 * @param fracNormal
	 * @param fracReverse
	 * @return
	 */
	public static PointSource buildSource(IncrementalMagFreqDist mfd, double duration,
			GriddedSeismicitySettings gridSourceSettings, Location loc,
			double fracStrikeSlip, double fracNormal, double fracReverse) {
		switch (gridSourceSettings.surfaceType) {
		case FINITE:
			Preconditions.checkState(gridSourceSettings.finiteRuptureSettings.numSurfaces <= 2, "Only support 1 or 2 finite surfaces here");
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					gridSourceSettings.pointSourceMagnitudeCutoff, fracStrikeSlip, fracNormal,
					fracReverse, gridSourceSettings.finiteRuptureSettings.numSurfaces > 1);
		case POINT:
			Map<FocalMech, Double> mechMap = new EnumMap<>(FocalMech.class);
			mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip);
			mechMap.put(FocalMech.REVERSE, fracReverse);
			mechMap.put(FocalMech.NORMAL, fracNormal);
			return new PointSourceNshm(loc, mfd, duration, mechMap,
					gridSourceSettings.distanceCorrection, gridSourceSettings.pointSourceMagnitudeCutoff,
					gridSourceSettings.supersamplingSettings);

		default:
			throw new IllegalStateException("Unknown Background Rup Type: "+gridSourceSettings.surfaceType);
		}
	}

	@Override
	public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
			double[] fracReverse, TectonicRegionType[] trts) {
		return new Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
				fracStrikeSlip, fracNormal, fracReverse, trts);
	}
	
	public static class Precomputed extends MFDGridSourceProvider.AbstractPrecomputed {
		
		@SuppressWarnings("unused") // for deserialization
		private Precomputed() {
			super();
		}

		public Precomputed(GriddedRegion region, CSVFile<String> subSeisCSV, CSVFile<String> unassociatedCSV,
				CSVFile<String> mechCSV) {
			super(region, subSeisCSV, unassociatedCSV, mechCSV);
		}

		public Precomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			super(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, trts);
		}

		public Precomputed(MFDGridSourceProvider prov) {
			super(prov);
		}

		@Override
		public String getName() {
			return "Precomputed NSHM23 Grid Source Provider";
		}

		@Override
		protected PointSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
				GriddedSeismicitySettings gridSourceSettings) {
			Location loc = getGriddedRegion().locationForIndex(gridIndex);
			
			double fracStrikeSlip = getFracStrikeSlip(gridIndex);
			double fracNormal = getFracNormal(gridIndex);
			double fracReverse = getFracReverse(gridIndex);

			return NSHM23_AbstractGridSourceProvider.buildSource(
					mfd, duration, gridSourceSettings, loc, fracStrikeSlip, fracNormal, fracReverse);
		}

		@Override
		public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			return new Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
					fracStrikeSlip, fracNormal, fracReverse, trts);
		}
		
	}

}
