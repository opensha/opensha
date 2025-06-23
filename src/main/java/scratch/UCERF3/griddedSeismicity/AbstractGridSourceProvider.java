package scratch.UCERF3.griddedSeismicity;

import java.io.IOException;
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
import org.opensha.sha.earthquake.aftershocks.MagnitudeDependentAftershockFilter;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.collect.Maps;

import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;

/**
 * Grid source provider used in UCERF3, with UCERF3-specific declustering and point source representations.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public abstract class AbstractGridSourceProvider extends MFDGridSourceProvider.Abstract implements ArchivableModule {

	private static final WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();
//	private static final double ptSrcCutoff = 6.0;
//	
//	public static double SOURCE_MIN_MAG_CUTOFF = 5.05;

	@Override
	public String getName() {
		return "UCERF3 Grid Source Provider";
	}

	@Override
	protected PointSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
			GriddedSeismicitySettings gridSourceSettings) {
		Location loc = getGriddedRegion().locationForIndex(gridIndex);
		
		double fracStrikeSlip = getFracStrikeSlip(gridIndex);
		double fracNormal = getFracNormal(gridIndex);
		double fracReverse = getFracReverse(gridIndex);

		return buildSource(mfd, duration, gridSourceSettings, loc, fracStrikeSlip, fracNormal, fracReverse);
	}

	public static PointSource buildSource(IncrementalMagFreqDist mfd, double duration,
			GriddedSeismicitySettings gridSourceSettings, Location loc, double fracStrikeSlip, double fracNormal, double fracReverse) {
		switch (gridSourceSettings.surfaceType) {
		case FINITE:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					gridSourceSettings.pointSourceMagnitudeCutoff, fracStrikeSlip, fracNormal,
					fracReverse, gridSourceSettings.finiteRuptureSettings.numSurfaces > 1);
		case POINT:
			Map<FocalMech, Double> mechMap = Maps.newHashMap();
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
	
	public static MagnitudeDependentAftershockFilter GK_AFTERSHOCK_FILTER =
			MagnitudeDependentAftershockFilter.forFunction((M,R) -> R*GardnerKnopoffAftershockFilter.scaleForMagnitude(M));

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
		return TectonicRegionType.ACTIVE_SHALLOW;
	}

	public static final String ARCHIVE_GRID_REGION_FILE_NAME = "grid_region.geojson";
	public static final String ARCHIVE_MECH_WEIGHT_FILE_NAME = "grid_mech_weights.csv";
	public static final String ARCHIVE_SUB_SEIS_FILE_NAME = "grid_sub_seis_mfds.csv";
	public static final String ARCHIVE_UNASSOCIATED_FILE_NAME = "grid_unassociated_mfds.csv";
	
	static TectonicRegionType[] getActiveShallowArray(int size) {
		TectonicRegionType[] trts = new TectonicRegionType[size];
		for (int i=0; i<trts.length; i++)
			trts[i] = TectonicRegionType.ACTIVE_SHALLOW;
		return trts;
	}

	public static class Precomputed extends MFDGridSourceProvider.AbstractPrecomputed {
		
		@SuppressWarnings("unused") // for deserialization
		private Precomputed() {
			super();
		}
		
		public Precomputed(MFDGridSourceProvider prov) {
			super(prov);
		}

		public Precomputed(GriddedRegion region, CSVFile<String> subSeisCSV, CSVFile<String> unassociatedCSV,
				CSVFile<String> mechCSV) {
			super(region, subSeisCSV, unassociatedCSV, mechCSV);
		}

		public Precomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse) {
			this(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse,
					getActiveShallowArray(region.getNodeCount()));
		}

		public Precomputed(GriddedRegion region, Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			super(region, nodeSubSeisMFDs, nodeUnassociatedMFDs, fracStrikeSlip, fracNormal, fracReverse, trts);
		}

		@Override
		public String getName() {
			return "Precomputed UCERF3 Grid Source Provider";
		}

		@Override
		protected PointSource buildSource(int gridIndex, IncrementalMagFreqDist mfd, double duration,
				GriddedSeismicitySettings gridSourceSettings) {
			Location loc = getGriddedRegion().locationForIndex(gridIndex);
			
			double fracStrikeSlip = getFracStrikeSlip(gridIndex);
			double fracNormal = getFracNormal(gridIndex);
			double fracReverse = getFracReverse(gridIndex);

			return AbstractGridSourceProvider.buildSource(
					mfd, duration, gridSourceSettings, loc, fracStrikeSlip, fracNormal, fracReverse);
		}

		@Override
		public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
				Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
				double[] fracReverse, TectonicRegionType[] trts) {
			return new Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
					fracStrikeSlip, fracNormal, fracReverse, trts);
		}

		@Override
		public TectonicRegionType getTectonicRegionType(int gridIndex) {
			return TectonicRegionType.ACTIVE_SHALLOW;
		}
		
	}

}
