package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVReader.Row;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.commons.util.modules.helpers.LargeCSV_BackedModule;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.PointSource.PoissonPointSourceData;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.aftershocks.MagnitudeDependentAftershockFilter;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSurfaceBuilder;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

public abstract class GridSourceList implements GridSourceProvider, ArchivableModule {
	
	private LocationList locs;
	private GriddedRegion gridReg; // can be null
	private double latGridSpacing = Double.NaN;
	private double lonGridSpacing = Double.NaN;
	
	// these are used for getLocationIndex(Location) if gridReg == null
	private transient GriddedRegion encompassingRegion = null;
	private int[] encompassingIndexesToLocIndexes = null;
	
	private GridSourceList() {}
	
	protected GridSourceList(GriddedRegion gridReg) {
		this(gridReg, gridReg.getNodeList());
	}
	
	protected GridSourceList(LocationList locs) {
		this(null, locs);
	}
	
	protected GridSourceList(GriddedRegion gridReg, LocationList locs) {
		setLocations(gridReg, locs);
	}
	
	protected void setLocations(GriddedRegion gridReg, LocationList locs) {
		Preconditions.checkNotNull(locs);
		if (gridReg != null) {
			Preconditions.checkState(locs.size() == gridReg.getNodeCount(),
					"Location list has %s locations, gridded region has %s", locs.size(), gridReg.getNodeCount());
			this.latGridSpacing = gridReg.getLatSpacing();
			this.lonGridSpacing = gridReg.getLonSpacing();
		} else {
			// detect spacing as the minimum nonzero difference encountered from one node to the next
			double minLatSpacing = Double.POSITIVE_INFINITY;
			double minLonSpacing = Double.POSITIVE_INFINITY;
			Location prevLoc = null;
			for (Location loc : locs) {
				if (prevLoc != null) {
					double latDiff = Math.abs(loc.lat - prevLoc.lat);
					double lonDiff = Math.abs(loc.lon - prevLoc.lon);
					if ((float)latDiff > 0f)
						minLatSpacing = Math.min(minLatSpacing, latDiff);
					if ((float)lonDiff > 0f)
						minLonSpacing = Math.min(minLonSpacing, lonDiff);
				}
				prevLoc = loc;
			}
			this.latGridSpacing = minLatSpacing;
			this.lonGridSpacing = minLonSpacing;
		}
		this.gridReg = gridReg;
		this.locs = locs;
	}

	@Override
	public String getName() {
		return "Grid Source List";
	}

	@Override
	public AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		return new Averager();
	}

	@Override
	public int getNumLocations() {
		return locs.size();
	}

	@Override
	public Location getLocation(int index) {
		return locs.get(index);
	}

	@Override
	public Location getLocationForSource(int sourceIndex) {
		return getLocation(getLocationIndexForSource(sourceIndex));
	}
	
	@Override
	public int getLocationIndex(Location location) {
		if (gridReg != null)
			return gridReg.indexForLocation(location);
		// need to do it the hard way
		if (encompassingRegion == null) {
			synchronized (this) {
				if (encompassingRegion == null)
					encompassingRegion = GriddedRegion.inferEncompassingRegion(locs);
			}
		}
		int encompassingIndex = encompassingRegion.indexForLocation(location);
		if (encompassingIndex >= 0)
			return encompassingIndexesToLocIndexes[encompassingIndex];
		return -1;
	}

	public abstract TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex);

	/**
	 * 
	 * @return reference (empty) MFD for use determining magnitude range and discreization of getMFD methods
	 */
	public abstract IncrementalMagFreqDist getRefMFD();
	
	/**
	 * @param sectionIndex
	 * @return set of associated grid indexes, or empty set if none
	 */
	public abstract Set<Integer> getAssociatedGridIndexes(int sectionIndex);
	
	public abstract ImmutableList<GriddedRupture> getRuptures(TectonicRegionType tectonicRegionType, int gridIndex);
	
	public ImmutableList<GriddedRupture> getAssociatedRuptures(int sectionIndex) {
		Set<Integer> gridIndexes = getAssociatedGridIndexes(sectionIndex);
		if (gridIndexes == null || gridIndexes.isEmpty())
			return ImmutableList.of();
		ImmutableList.Builder<GriddedRupture> ret = ImmutableList.builder();
		
		for (int gridIndex : gridIndexes)
			for (TectonicRegionType trt : getTectonicRegionTypes())
				for (GriddedRupture rup : getRuptures(trt, gridIndex))
					if (rup.associatedSections != null && Ints.contains(rup.associatedSections, sectionIndex))
						ret.add(rup);
		
		return ret.build();
	}
	
	public ImmutableList<GriddedRupture> getRupturesSubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex) {
		ImmutableList.Builder<GriddedRupture> subSeisRups = ImmutableList.builder();
		for (GriddedRupture rup : getRuptures(tectonicRegionType, gridIndex))
			if (rup.associatedSections != null && rup.associatedSections.length > 0)
				subSeisRups.add(rup);
		return subSeisRups.build();
	}
	
	public ImmutableList<GriddedRupture> getRupturesUnassociated(TectonicRegionType tectonicRegionType, int gridIndex) {
		ImmutableList.Builder<GriddedRupture> unassocRups = ImmutableList.builder();
		for (GriddedRupture rup : getRuptures(tectonicRegionType, gridIndex))
			if (rup.associatedSections == null || rup.associatedSections.length == 0)
				unassocRups.add(rup);
		return unassocRups.build();
	}

	@Override
	public ProbEqkSource getSource(int sourceIndex, double duration, MagnitudeDependentAftershockFilter aftershockFilter,
			GriddedSeismicitySettings gridSourceSettings) {
		return getSource(tectonicRegionTypeForSourceIndex(sourceIndex), getLocationIndexForSource(sourceIndex),
				duration, aftershockFilter, gridSourceSettings);
	}

	@Override
	public ProbEqkSource getSource(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			MagnitudeDependentAftershockFilter aftershockFilter, GriddedSeismicitySettings gridSourceSettings) {
		return buildSource(getLocation(gridIndex), getRuptures(tectonicRegionType, gridIndex),
				duration, aftershockFilter, gridSourceSettings, tectonicRegionType);
	}

	@Override
	public ProbEqkSource getSourceSubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			MagnitudeDependentAftershockFilter aftershockFilter, GriddedSeismicitySettings gridSourceSettings) {
		return buildSource(getLocation(gridIndex), getRupturesSubSeisOnFault(tectonicRegionType, gridIndex),
				duration, aftershockFilter, gridSourceSettings, tectonicRegionType);
	}

	@Override
	public ProbEqkSource getSourceUnassociated(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			MagnitudeDependentAftershockFilter aftershockFilter, GriddedSeismicitySettings gridSourceSettings) {
		return buildSource(getLocation(gridIndex), getRupturesUnassociated(tectonicRegionType, gridIndex),
				duration, aftershockFilter, gridSourceSettings, tectonicRegionType);
	}

	@Override
	public IncrementalMagFreqDist getMFD_Unassociated(TectonicRegionType tectonicRegionType, int gridIndex) {
		return getMFD(tectonicRegionType, gridIndex, Double.NEGATIVE_INFINITY, true, false);
	}

	@Override
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex) {
		return getMFD(tectonicRegionType, gridIndex, Double.NEGATIVE_INFINITY, false, true);
	}

	@Override
	public IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex, double minMag) {
		return getMFD(tectonicRegionType, gridIndex, minMag, true, true);
	}

	@Override
	public IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex) {
		return getMFD(tectonicRegionType, gridIndex, Double.NEGATIVE_INFINITY, true, true); 
	}
	
	@Override
	public double getCumulativeNucleationRate(int gridIndex, double minMag) {
		return getCumulativeNucleationRate(null, gridIndex, minMag);
	}

	@Override
	public double getCumulativeNucleationRate(TectonicRegionType tectonicRegionType, int gridIndex, double minMag) {
		double sum = 0d;
		for (GriddedRupture rup : getRuptures(tectonicRegionType, gridIndex))
			if ((float)rup.properties.magnitude >= (float)minMag)
				sum += rup.rate;
		return sum;
	}

	@Override
	public IncrementalMagFreqDist getMFD_Unassociated(int gridIndex) {
		return getMFD(null, gridIndex, Double.NEGATIVE_INFINITY, true, false);
	}

	@Override
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(int gridIndex) {
		return getMFD(null, gridIndex, Double.NEGATIVE_INFINITY, false, true);
	}

	@Override
	public IncrementalMagFreqDist getMFD(int gridIndex, double minMag) {
		return getMFD(null, gridIndex, minMag, true, true);
	}

	@Override
	public IncrementalMagFreqDist getMFD(int gridIndex) {
		return getMFD(null, gridIndex, Double.NEGATIVE_INFINITY, true, true); 
	}
	
	private IncrementalMagFreqDist getMFD(TectonicRegionType tectonicRegionType, int gridIndex, double minMag,
			boolean includeUnassociated, boolean includeAssociated) {
		IncrementalMagFreqDist refMFD = getRefMFD();
		IncrementalMagFreqDist mfd;
		if (Double.isFinite(minMag)) {
			int minIndex = refMFD.getClosestXIndex(minMag);
			mfd = new IncrementalMagFreqDist(refMFD.getX(minIndex), refMFD.size()-minIndex, refMFD.getDelta());
			// reset minMag to be the bin edge
			minMag = mfd.getMinX() - 0.5*mfd.getDelta();
		} else {
			mfd = refMFD.deepClone();
			Preconditions.checkState(mfd.calcSumOfY_Vals() == 0d);
		}
		Preconditions.checkState(includeUnassociated || includeAssociated);
		int maxIndexNonZero = 0;
		for (GriddedRupture rup : getRuptures(tectonicRegionType, gridIndex)) {
			if (rup.properties.magnitude >= minMag && rup.rate >= 0d) {
				double rate;
				if (!includeAssociated)
					rate = rup.getRateUnassociated();
				else if (!includeUnassociated)
					rate = rup.getRateAssociated();
				else
					rate = rup.rate;
				int index = mfd.getClosestXIndex(rup.properties.magnitude);
				mfd.add(index, rate);
				maxIndexNonZero = Integer.max(maxIndexNonZero, index);
			}
		}
		if (maxIndexNonZero < mfd.size()-1) {
			// trim it
			IncrementalMagFreqDist trimmed = new IncrementalMagFreqDist(mfd.getMinX(), maxIndexNonZero+1, mfd.getDelta());
			for (int i=0; i<trimmed.size(); i++)
				trimmed.set(i, mfd.getY(i));
			mfd = trimmed;
		}
		return mfd;
	}

	@Override
	public GriddedRegion getGriddedRegion() {
		return gridReg;
	}
	
	public GridSourceList getAboveMinMag(float minMag) {
		return new MinMagFiltered(this, minMag);
	}
	
	private static List<Range<Double>> SS_RANGES = List.of(
			Range.closedOpen(-180d, -135d),
			Range.open(-45d, 45d),
			Range.openClosed(135d, 180d));
	private static Range<Double> REV_RANGE = Range.closed(45d, 135d);
	private static Range<Double> NORM_RANGE = Range.closed(-135d, -45d);
	
	public static FocalMech getMechForRake(double rake) {
		if (REV_RANGE.contains(rake))
			return FocalMech.REVERSE;
		if (NORM_RANGE.contains(rake))
			return FocalMech.NORMAL;
		return FocalMech.STRIKE_SLIP;
	}

	@Override
	public double getFracStrikeSlip(int gridIndex) {
		return getFractWithRake(SS_RANGES, gridIndex);
	}

	@Override
	public double getFracReverse(int gridIndex) {
		return getFractWithRake(REV_RANGE, gridIndex);
	}

	@Override
	public double getFracNormal(int gridIndex) {
		return getFractWithRake(NORM_RANGE, gridIndex);
	}
	
	private double getFractWithRake(Range<Double> rakeRange, int gridIndex) {
		return getFractWithRake(List.of(rakeRange), gridIndex);
	}
	
	private double getFractWithRake(List<Range<Double>> rakeRanges, int gridIndex) {
		double totRate = 0d;
		double rateMatching = 0d;
		for (TectonicRegionType trt : getTectonicRegionTypes()) {
			ImmutableList<GriddedRupture> rups = getRuptures(trt, gridIndex);
			for (GriddedRupture rup : rups) {
				totRate += rup.rate;
				for (Range<Double> range : rakeRanges) {
					if (range.contains(rup.properties.rake)) {
						rateMatching += rup.rate;
					}
				}
			}
		}
		if (totRate == 0d)
			return 0d;
		return rateMatching/totRate;
	}

	@Override
	public void scaleAll(double[] valuesArray) {
		for (TectonicRegionType trt : getTectonicRegionTypes())
			scaleAll(trt, valuesArray);
	}
	
	public static final String ARCHIVE_GRID_LOCS_FILE_NAME = "grid_source_locations.csv";
	public static final String ARCHIVE_GRID_SOURCES_FILE_NAME = "grid_sources.csv";
	
	private static final int locRoundScale = 3;
	private static final int magRoundScale = 3;
	private static final int mechRoundSigFigs = 3;
	private static final int depthRoundSigFigs = 3;
	private static final int lenRoundSigFigs = 3;
	private static final int rateRoundSigFigs = 6;
	
	private boolean round = true;
	
	public void setArchiveRounding(boolean round) {
		this.round = true;
	}
	
	public CSVFile<String> buildGridLocsCSV() {
		CSVFile<String> gridCSV = new CSVFile<>(true);
		gridCSV.addLine("Grid Index", "Latitude", "Longitude");
		for (int i=0; i<getNumLocations(); i++) {
			Location loc = getLocation(i);
			gridCSV.addLine(i+"", getFixedPrecision(loc.lat, locRoundScale), getFixedPrecision(loc.lon, locRoundScale));
		}
		return gridCSV;
	}
	
	public void writeGridSourcesCSV(ArchiveOutput output, String entryName) throws IOException {
		// use CSVWriter for efficiency
		output.putNextEntry(entryName);
		CSVWriter rupCSV = new CSVWriter(output.getOutputStream(), false);
		List<String> header = new ArrayList<>();
		header.add("Grid Index");
		header.add("Magnitude");
		header.add("Annual Rate");
		header.add("Rake");
		header.add("Dip");
		header.add("Strike");
		header.add("Upper Depth (km)");
		header.add("Lower Depth (km)");
		header.add("Length (km)");
		header.add("Hypocentral Depth (km)");
		header.add("Hypocentral DAS (km)");
		header.add("Tectonic Regime");
		int maxNumAssoc = 0;
		for (TectonicRegionType trt : getTectonicRegionTypes())
			for (int i=0; i<getNumLocations(); i++)
				for (GriddedRupture rup : getRuptures(trt, i))
					if (rup.associatedSections != null)
						maxNumAssoc = Integer.max(maxNumAssoc, rup.associatedSections.length);
		if (maxNumAssoc > 0) {
			if (maxNumAssoc == 1) {
				header.add("Associated Section Index");
				header.add("Fraction Associated");
			} else {
				header.add("Associated Section Index 1");
				header.add("Fraction Associated 1");
				header.add("Associated Section Index N");
				header.add("Fraction Associated N");
			}
		}
		rupCSV.write(header);
		for (int i=0; i<getNumLocations(); i++) {
			for (TectonicRegionType trt : getTectonicRegionTypes()) {
				for (GriddedRupture rup : getRuptures(trt, i)) {
					List<String> line = new ArrayList<>();
					line.add(i+"");
					line.add(getFixedPrecision(rup.properties.magnitude, magRoundScale));
					line.add(getSigFigs(rup.rate, rateRoundSigFigs));
					line.add(getSigFigs(rup.properties.rake, mechRoundSigFigs));
					line.add(getSigFigs(rup.properties.dip, mechRoundSigFigs));
					if (rup.properties.strikeRange != null)
						line.add(rangeToString(rup.properties.strikeRange));
					else
						line.add(getSigFigs(rup.properties.strike, mechRoundSigFigs));
					line.add(getSigFigs(rup.properties.upperDepth, depthRoundSigFigs));
					line.add(getSigFigs(rup.properties.lowerDepth, depthRoundSigFigs));
					line.add(getSigFigs(rup.properties.length, lenRoundSigFigs));
					line.add(getSigFigs(rup.properties.hypocentralDepth, depthRoundSigFigs));
					line.add(getSigFigs(rup.properties.hypocentralDAS, lenRoundSigFigs));
					line.add(rup.properties.tectonicRegionType.name());
					if (rup.associatedSections != null) {
						for (int s=0; s<rup.associatedSections.length; s++) {
							line.add(rup.associatedSections[s]+"");
							line.add(getSigFigs(rup.associatedSectionFracts[s], rateRoundSigFigs)+"");
						}
					}
					rupCSV.write(line);
				}
			}
		}
		rupCSV.flush();
		output.closeEntry();
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		if (gridReg != null) {
			// write the gridded region
			FileBackedModule.initEntry(output, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
			Feature regFeature = gridReg.toFeature();
			OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
			Feature.write(regFeature, writer);
			writer.flush();
			output.closeEntry();
		}
		
		// write grid locations
		CSV_BackedModule.writeToArchive(buildGridLocsCSV(), output, entryPrefix, ARCHIVE_GRID_LOCS_FILE_NAME);
		
		// write gridded rupture list
		writeGridSourcesCSV(output, ArchivableModule.getEntryName(entryPrefix, ARCHIVE_GRID_SOURCES_FILE_NAME));
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		// will always load back in as Precomputed unless explicitly overridden in a downstream class
		return Precomputed.class;
	}
	
	private static boolean isRangeString(String str) {
		str = str.trim();
		return str.startsWith("[") && str.endsWith("]");
	}
	
	private static Range<Double> parseRangeString(String str) {
		str = str.trim();
		Preconditions.checkState(isRangeString(str));
		str = str.substring(1, str.length()-1);
		String firstStr, lastStr;
		if (str.contains(",")) {
			int commaIndex = str.indexOf(",");
			firstStr = str.substring(0, commaIndex);
			lastStr = str.substring(commaIndex+1);
		} else {
			Preconditions.checkState(str.contains(".."));
			int dotsIndex = str.indexOf("..");
			firstStr = str.substring(0, dotsIndex);
			lastStr = str.substring(dotsIndex+2);
		}
		double lower = Double.parseDouble(firstStr.trim());
		double upper = Double.parseDouble(lastStr.trim());
		return Range.closed(lower, upper);
	}
	
	private String rangeToString(Range<Double> range) {
		Preconditions.checkState(range.hasLowerBound() && range.hasUpperBound(), "Must have fixed founds");
		return "["+getSigFigs(range.lowerEndpoint(), mechRoundSigFigs)+".."+getSigFigs(range.upperEndpoint(), mechRoundSigFigs)+"]";
	}
	
	private String getFixedPrecision(double val, int scale) {
		if (Double.isNaN(val))
			return "";
		if (!Double.isFinite(val))
			return val+"";
		if (val == Math.floor(val))
			return (int)val+"";
		if (!round)
			return val+"";
		return DataUtils.roundFixed(val, scale)+"";
	}
	
	private String getSigFigs(double val, int sigFigs) {
		if (Double.isNaN(val))
			return "";
		if (!Double.isFinite(val))
			return val+"";
		if (val == Math.floor(val))
			return (int)val+"";
		if (!round)
			return val+"";
		return DataUtils.roundSigFigs(val, sigFigs)+"";
	}
	
	public static LocationList loadGridLocsCSV(CSVFile<String> gridCSV, GriddedRegion gridReg) {
		LocationList fileLocs = new LocationList();
		for (int row=1; row<gridCSV.getNumRows(); row++) {
			int index = row-1;
			int csvIndex = gridCSV.getInt(row, 0);
			Preconditions.checkState(csvIndex == index,
					"Grid locations must be in order, expected index=%s for row=%s, encountered index=%s",
					index, row, csvIndex);
			double lat = gridCSV.getDouble(row, 1);
			double lon = gridCSV.getDouble(row, 2);
			fileLocs.add(new Location(lat, lon));
		}
		if (gridReg == null)
			// use the CSV file directly
			return fileLocs;
		// use the gridded region, but validate
		Preconditions.checkState(gridReg.getNodeCount() == fileLocs.size(),
				"Gridded region has %s nodes, but %s has %s", gridReg.getNodeCount(), ARCHIVE_GRID_LOCS_FILE_NAME, fileLocs.size());
		for (int i=0; i<fileLocs.size(); i++) {
			Location fileLoc = fileLocs.get(i);
			Location gridLoc = gridReg.getLocation(i);
			if (!LocationUtils.areSimilar(fileLoc, gridLoc)) {
				// check rounding
				Location roundedLoc = new Location(DataUtils.roundFixed(gridLoc.lat, locRoundScale),
						DataUtils.roundFixed(gridLoc.lon, locRoundScale));
				Preconditions.checkState(LocationUtils.areSimilar(fileLoc, roundedLoc),
						"Location mismatch at index=%s between gridded region and %s: %s != %s;"
								+ "also tried test with rounded form: %s != %s",
								i, ARCHIVE_GRID_LOCS_FILE_NAME, gridLoc, fileLoc, roundedLoc, fileLoc);
			}
		}
		return gridReg.getNodeList();
	}
	
	public static EnumMap<TectonicRegionType, List<List<GriddedRupture>>> loadGridSourcesCSV(CSVReader rupSectsCSV, LocationList locs) {
		rupSectsCSV.read(); // skip header row
		EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
		GriddedRupturePropertiesCache cache = new GriddedRupturePropertiesCache();
		while (true) {
			Row row = rupSectsCSV.read();
			if (row == null)
				break;
			
			int col = 0;
			int gridIndex = row.getInt(col++);
			Preconditions.checkState(gridIndex >= 0 && gridIndex < locs.size(),
					"Bad gridIndex=%s with %s locations", gridIndex, locs.size());
			double mag = row.getDouble(col++);
			Preconditions.checkState(Double.isFinite(mag), "Bad magnitude=%s", mag);
			double rate = row.getDouble(col++);
			Preconditions.checkState(Double.isFinite(rate) && rate >= 0d, "Bad rate=%s", rate);
			double rake = row.getDouble(col++);
			FaultUtils.assertValidRake(rake);
			double dip = row.getDouble(col++);
			FaultUtils.assertValidDip(dip);
			String strikeStr = row.get(col++);
			Range<Double> strikeRange = null;
			double strike = Double.NaN;
			if (isRangeString(strikeStr))
				strikeRange = parseRangeString(strikeStr);
			else if (!strikeStr.isBlank())
				strike = Double.parseDouble(strikeStr);
			double upperDepth = row.getDouble(col++);
			FaultUtils.assertValidDepth(upperDepth);
			double lowerDepth = row.getDouble(col++);
			FaultUtils.assertValidDepth(lowerDepth);
			double length = row.getDouble(col++);
			Preconditions.checkState(Double.isFinite(length) && length >= 0d, "Bad length=%s", length);
			String hypocentralDepthStr = row.get(col++);
			double hypocentralDepth = hypocentralDepthStr.isBlank() ? Double.NaN : Double.parseDouble(hypocentralDepthStr);
			if (!Double.isNaN(hypocentralDepth)) {
				FaultUtils.assertValidDepth(hypocentralDepth);
				Preconditions.checkState((float)hypocentralDepth >= (float)upperDepth,
						"Hypocentral depth (%s) must be at or below upper depth (%s)", (float)hypocentralDepth, (float)upperDepth);
				Preconditions.checkState((float)hypocentralDepth <= (float)lowerDepth,
						"Hypocentral depth (%s) must be at or above lower depth (%s)", (float)hypocentralDepth, (float)lowerDepth);
			}
			String hypocentralDASStr = row.get(col++);
			double hypocentralDAS = hypocentralDASStr.isBlank() ? Double.NaN : Double.parseDouble(hypocentralDASStr);
			if (!Double.isNaN(hypocentralDAS)) {
				Preconditions.checkState(hypocentralDAS >= 0d && (float)hypocentralDAS <= (float)length,
						"Hypocentral DAS (%s) must be >= 0 and <= len (%s)", (float)hypocentralDAS, (float)length);
			}
			TectonicRegionType tectonicRegionType = TectonicRegionType.valueOf(row.get(col++));
			int colsLeft = row.columns() - col;
			int[] associatedSections = null;
			double[] associatedSectionFracts = null;
			if (colsLeft > 0) {
				Preconditions.checkState(colsLeft % 2 == 0,
						"Have %s columns left for associations, which is not divisible by 2; expected pairs of id, fract",
						colsLeft);
				int numAssoc = colsLeft/2;
				associatedSections = new int[numAssoc];
				associatedSectionFracts = new double[numAssoc];
				for (int i=0; i<numAssoc; i++) {
					String sectStr = row.get(col++);
					if (sectStr.isBlank()) {
						// empty, bail here
						if (i == 0) {
							// didn't actually have any
							associatedSections = null;
							associatedSectionFracts = null;
						} else {
							// trim
							associatedSections = Arrays.copyOf(associatedSections, i);
							associatedSectionFracts = Arrays.copyOf(associatedSectionFracts, i);
						}
						break;
					} else {
						int sectID = Integer.parseInt(sectStr);
						Preconditions.checkState(sectID >= 0, "Bad associated sectID=%s", sectID);
						float fract = row.getFloat(col++);
						Preconditions.checkState(fract >= 0d && fract <= 1d, "Bad associated fraction=%s", fract);
						associatedSections[i] = sectID;
						associatedSectionFracts[i] = fract;
					}
				}
			}
			GriddedRuptureProperties props = cache.getCached(new GriddedRuptureProperties(mag, rake, dip,
					strike, strikeRange, upperDepth, lowerDepth, length, hypocentralDepth, hypocentralDAS,
					tectonicRegionType));
			GriddedRupture rup = new GriddedRupture(gridIndex, locs.get(gridIndex), props, rate, associatedSections, associatedSectionFracts);
			if (!trtRuptureLists.containsKey(tectonicRegionType)) {
				List<List<GriddedRupture>> ruptureLists = new ArrayList<>(locs.size());
				for (int i=0; i<locs.size(); i++)
					ruptureLists.add(null);
				trtRuptureLists.put(tectonicRegionType, ruptureLists);
			}
			List<List<GriddedRupture>> ruptureLists = trtRuptureLists.get(tectonicRegionType);
			if (ruptureLists.get(gridIndex) == null)
				ruptureLists.set(gridIndex, new ArrayList<>());
			ruptureLists.get(gridIndex).add(rup);
		}
		return trtRuptureLists;
	}
	
	public static final class GriddedRupturePropertiesCache {
		private HashMap<GriddedRuptureProperties, GriddedRuptureProperties> cache = new HashMap<>();
		
		public GriddedRuptureProperties getCached(GriddedRuptureProperties props) {
			GriddedRuptureProperties cached = cache.get(props);
			if (cached == null) {
				cache.put(props, props);
				return props;
			}
			return cached;
		}
	}
	
	/**
	 * Geometric properties of a {@link GriddedRupture}
	 */
	public static final class GriddedRuptureProperties implements Comparable<GriddedRuptureProperties> {
		// MAGNITUDE
		public final double magnitude;
		// FOCAL MECHANISM
		public final double rake;
		public final double dip;
		public final double strike;
		public final Range<Double> strikeRange;
		// FINITE PROPERTIES
		/**
		 * Rupture upper depth in km
		 */
		public final double upperDepth;
		/**
		 * Rupture lower depth in km
		 */
		public final double lowerDepth;
		/**
		 * Rupture length in km
		 */
		public final double length;
		/**
		 * Rupture hypocentral depth in km, or NaN (assumed halfway)
		 */
		public final double hypocentralDepth;
		/**
		 * Rupture hypocentral distance along strike in km, or NaN (assumed halfway)
		 */
		public final double hypocentralDAS;
		// TECTONIC REGIME
		public final TectonicRegionType tectonicRegionType;
		
		private transient int hashCode = -1;
		
		public GriddedRuptureProperties(double magnitude, double rake, double dip,
				double strike, Range<Double> strikeRange, double upperDepth, double lowerDepth, double length,
				double hypocentralDepth, double hypocentralDAS, TectonicRegionType tectonicRegionType) {
			super();
			this.magnitude = magnitude;
			this.rake = rake;
			this.dip = dip;
			this.strike = strike;
			this.strikeRange = strikeRange;
			this.upperDepth = upperDepth;
			this.lowerDepth = lowerDepth;
			this.length = length;
			this.hypocentralDepth = hypocentralDepth;
			this.hypocentralDAS = hypocentralDAS;
			this.tectonicRegionType = tectonicRegionType;
		}
		
		/**
		 * Get's the hypocentral depth, calculating it (assuming middle depth) if the stored hypocentralDepth parameter is not set
		 * @return
		 */
		public double getHypocentralDepth() {
			if (Double.isFinite(hypocentralDepth))
				return hypocentralDepth;
			if (lowerDepth == upperDepth)
				return upperDepth;
			return upperDepth + 0.5*(lowerDepth - upperDepth);
		}
		
		/**
		 * @return the fractional DAS value, or 0.5 if not set
		 */
		public double getFractionalHypocentralDAS() {
			if (Double.isFinite(hypocentralDAS))
				return hypocentralDAS/length;
			return 0.5;
		}
		
		/**
		 * Get's the hypocentral DAS, calculating it (assuming middle along strike) if the stored hypocentralDAS parameter is not set
		 * @return
		 */
		public double getHypocentralDAS() {
			if (Double.isFinite(hypocentralDAS))
				return hypocentralDAS;
			return 0.5*length;
		}
		
		/**
		 * Get's the down-dip width of the rupture in km
		 * @return
		 */
		public double getDownDipWidth() {
			double height = lowerDepth - upperDepth;
			return height/Math.sin(Math.toRadians(dip));
		}

		@Override
		public int hashCode() {
			// cache hashCode to make hashing faster as all fields are immutable
			if (hashCode == -1)
				hashCode = calcHashCode();
			return hashCode;
		}

		public int calcHashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Objects.hash(dip, hypocentralDAS, hypocentralDepth, length, lowerDepth, magnitude, rake,
					strike, strikeRange, tectonicRegionType, upperDepth);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			if (hashCode() != obj.hashCode())
				return false;
			GriddedRuptureProperties other = (GriddedRuptureProperties) obj;
			return Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
					&& Double.doubleToLongBits(hypocentralDAS) == Double.doubleToLongBits(other.hypocentralDAS)
					&& Double.doubleToLongBits(hypocentralDepth) == Double.doubleToLongBits(other.hypocentralDepth)
					&& Double.doubleToLongBits(length) == Double.doubleToLongBits(other.length)
					&& Double.doubleToLongBits(lowerDepth) == Double.doubleToLongBits(other.lowerDepth)
					&& Double.doubleToLongBits(magnitude) == Double.doubleToLongBits(other.magnitude)
					&& Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
					&& Double.doubleToLongBits(strike) == Double.doubleToLongBits(other.strike)
					&& Objects.equals(strikeRange, other.strikeRange) && tectonicRegionType == other.tectonicRegionType
					&& Double.doubleToLongBits(upperDepth) == Double.doubleToLongBits(other.upperDepth);
		}

		@Override
		public int compareTo(GriddedRuptureProperties other) {
			return RUP_FULL_PROPS_COMPARATOR.compare(this, other);
		}
		
		@Override
		public String toString() {
		   return "GriddedRuptureProperties[" +
		           "magnitude=" + (float) magnitude + "; " +
		           "rake=" + (float) rake + "; " +
		           "dip=" + (float) dip + "; " +
		           "strike=" + (float) strike + "; " +
		           "strikeRange=" + strikeRange + "; " +
		           "upperDepth=" + (float) upperDepth + "; " +
		           "lowerDepth=" + (float) lowerDepth + "; " +
		           "length=" + (float) length + "; " +
		           "hypocentralDepth=" + (float) hypocentralDepth + "; " +
		           "hypocentralDAS=" + (float) hypocentralDAS + "; " +
		           "tectonicRegionType=" + (tectonicRegionType != null ? tectonicRegionType.name() : "null") +
		           "]";
		}
		
	}
	
	/**
	 * Gridded rupture representation, consisting of {@link GriddedRuptureProperties}, rate, and section associations
	 */
	public static final class GriddedRupture implements Comparable<GriddedRupture> {
		public final GriddedRuptureProperties properties;
		// LOCATION
		public final int gridIndex;
		public final Location location;
		// RATE
		public final double rate;
		// ASSOCIATIONS
		public final int[] associatedSections;
		public final double[] associatedSectionFracts;
		
		private transient int hashCode = -1;
		
		public GriddedRupture(int gridIndex, Location location, GriddedRuptureProperties props, double rate) {
			this(gridIndex, location, props, rate, null, null);
		}
		
		public GriddedRupture(int gridIndex, Location location, GriddedRuptureProperties props,
				double rate, int[] associatedSections, double[] associatedSectionFracts) {
			super();
			this.gridIndex = gridIndex;
			this.location = location;
			this.properties = props;
			this.rate = rate;
			Preconditions.checkState((associatedSections == null) == (associatedSectionFracts == null));
			if (associatedSections != null) {
				Preconditions.checkState(associatedSections.length > 0, "Associations should be either null or non-empty");
				Preconditions.checkState(associatedSections.length == associatedSectionFracts.length);
				this.associatedSections = associatedSections;
				for (double assoc : associatedSectionFracts)
					Preconditions.checkState(assoc > 0d, "Association fractions must be >0: %s", assoc);
				this.associatedSectionFracts = associatedSectionFracts;
			} else {
				this.associatedSections = null;
				this.associatedSectionFracts = null;
			}
		}
		
		public GriddedRupture copyNewRate(double modRate) {
			return new GriddedRupture(gridIndex, location, properties, modRate, associatedSections, associatedSectionFracts);
		}
		
		public GriddedRupture copyNewGridIndex(int gridIndex) {
			return new GriddedRupture(gridIndex, location, properties, rate, associatedSections, associatedSectionFracts);
		}
		
		public double getFractAssociated(int sectionIndex) {
			if (associatedSections == null)
				return 0d;
			for (int i=0; i<associatedSections.length; i++)
				if (associatedSections[i] == sectionIndex)
					return associatedSectionFracts[i];
			return 0d;
		}

		@Override
		public int hashCode() {
			// cache hashCode to make hashing faster as all fields are immutable
			if (hashCode == -1)
				hashCode = calcHashCode();
			return hashCode;
		}

		public int calcHashCode() {
			final int prime = 31;
			int result = properties.hashCode();
			result = prime * result + gridIndex;
			result = prime * result + Double.hashCode(rate);
			result = prime * result + Arrays.hashCode(associatedSectionFracts);
			result = prime * result + Arrays.hashCode(associatedSections);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			if (hashCode() != obj.hashCode())
				return false;
			GriddedRupture other = (GriddedRupture) obj;
			return gridIndex == other.gridIndex
					&& Double.doubleToLongBits(rate) == Double.doubleToLongBits(other.rate)
					&& Arrays.equals(associatedSectionFracts, other.associatedSectionFracts)
					&& Arrays.equals(associatedSections, other.associatedSections)
					&& properties.equals(other.properties);
		}

		@Override
		public int compareTo(GriddedRupture other) {
			return RUP_FULL_PROPS_COMPARATOR.compare(this.properties, other.properties);
		}
		
		public double getFractAssociated() {
			if (associatedSectionFracts == null)
				return 0d;
			double fractSum = 0d;
			for (double fract : associatedSectionFracts)
				fractSum += fract;
			Preconditions.checkState(fractSum > 0d && (float)fractSum <= 1f);
			if ((float)fractSum == 1f)
				// assume it's just rounding errors
				return 1d;
			// make sure we don't accidentally return ~1.00000001 (we made sure it's approx <=1 earlier)
			return Math.min(fractSum, 1d);
		}
		
		public double getRateAssociated() {
			if (associatedSectionFracts == null)
				return 0d;
			double fractAssoc = getFractAssociated();
			if ((float)fractAssoc == 0f)
				return 0d;
			return this.rate*fractAssoc;
		}
		
		public double getRateUnassociated() {
			if (associatedSectionFracts == null)
				return rate;
			double fractUnassoc = 1d-getFractAssociated();
			if ((float)fractUnassoc == 0f)
				return 0d;
			if ((float)fractUnassoc == 1f)
				return rate;
			return this.rate*fractUnassoc;
		}
	}
	
	private static GriddedRupturePropComparator RUP_NON_AVERAGED_PROPS_COMPARATOR = new GriddedRupturePropComparator(true);
	
	private static GriddedRupturePropComparator RUP_FULL_PROPS_COMPARATOR = new GriddedRupturePropComparator(false);
	
	private static class GriddedRupturePropComparator implements Comparator<GriddedRuptureProperties> {
		
		private boolean averageQuantitiesOnly;
		
		private GriddedRupturePropComparator(boolean averageQuantitiesOnly) {
			this.averageQuantitiesOnly = averageQuantitiesOnly;
		}

		@Override
		public int compare(GriddedRuptureProperties rup1, GriddedRuptureProperties rup2) {
			int result;

			result = doubleCompAsFloat(rup1.magnitude, rup2.magnitude);
			if (result != 0) return result;

			result = doubleCompAsFloat(rup1.rake, rup2.rake);
			if (result != 0) return result;

			result = doubleCompAsFloat(rup1.dip, rup2.dip);
			if (result != 0) return result;

			result = doubleCompAsFloat(rup1.strike, rup2.strike);
			if (result != 0) return result;

			if (rup1.strikeRange == null && rup2.strikeRange != null) return -1;
			if (rup1.strikeRange != null && rup2.strikeRange == null) return 1;
			if (rup1.strikeRange != null && rup2.strikeRange != null) {
				result = rup1.strikeRange.lowerEndpoint().compareTo(rup2.strikeRange.lowerEndpoint());
				if (result != 0) return result;
				result = rup1.strikeRange.upperEndpoint().compareTo(rup2.strikeRange.upperEndpoint());
				if (result != 0) return result;
			}
			
			// treat each unique hypocentral depth as separate, but we'll average quantities that affect DDW (and length)
			result = doubleCompAsFloat(rup1.getHypocentralDepth(), rup2.getHypocentralDepth());
			if (result != 0) return result;
			// same with fractional DAS--we don't want to average explicitly set DAS values
			result = doubleCompAsFloat(rup1.getFractionalHypocentralDAS(), rup2.getFractionalHypocentralDAS());

			if (!averageQuantitiesOnly) {
				result = doubleCompAsFloat(rup1.upperDepth, rup2.upperDepth);
				if (result != 0) return result;

				result = doubleCompAsFloat(rup1.lowerDepth, rup2.lowerDepth);
				if (result != 0) return result;

				result = doubleCompAsFloat(rup1.length, rup2.length);
				if (result != 0) return result;

				result = doubleCompAsFloat(rup1.hypocentralDAS, rup2.hypocentralDAS);
				if (result != 0) return result;

				result = doubleCompAsFloat(rup1.hypocentralDepth, rup2.hypocentralDepth);
				if (result != 0) return result;
			}

			result = rup1.tectonicRegionType.compareTo(rup2.tectonicRegionType);
			return result;
		}
		
		private int doubleCompAsFloat(double val1, double val2) {
			return Float.compare((float)val1, (float)val2);
		}
		
	}
	
	public static PointSurfaceBuilder surfBuilderForRup(GriddedRupture rup) {
		PointSurfaceBuilder builder = new PointSurfaceBuilder(rup.location);
		return updateSurfBuilderForLoc(builder, rup, false);
	}
	
	private static PointSurfaceBuilder updateSurfBuilderForLoc(PointSurfaceBuilder surfBuilder, GriddedRupture rup, boolean forcePointSurf) {
		surfBuilder.magnitude(rup.properties.magnitude);
		surfBuilder.dip(rup.properties.dip);
		if (forcePointSurf)
			surfBuilder.strike(Double.NaN);
		else if (Double.isFinite(rup.properties.strike))
			surfBuilder.strike(rup.properties.strike);
		else if (rup.properties.strikeRange != null)
			surfBuilder.strikeRange(rup.properties.strikeRange);
		else
			surfBuilder.strike(Double.NaN);
		surfBuilder.upperDepth(rup.properties.upperDepth);
		surfBuilder.lowerDepth(rup.properties.lowerDepth);
		surfBuilder.length(rup.properties.length);
		double hypoDepth = rup.properties.getHypocentralDepth();
		surfBuilder.hypocentralDepth(hypoDepth);
		surfBuilder.das(rup.properties.getHypocentralDAS());
		return surfBuilder;
	}
	
	private static class GriddedRuptureSourceData implements PoissonPointSourceData {
		
		private List<GriddedRupture> rups;
		private List<Double> rates;
		private final List<RuptureSurface> surfs;
		
		public GriddedRuptureSourceData(Location gridLoc, List<GriddedRupture> gridRups,
				MagnitudeDependentAftershockFilter aftershockFilter, GriddedSeismicitySettings gridSourceSettings) {
			Preconditions.checkState(!gridRups.isEmpty());
			if (gridRups.get(0).properties.magnitude >= gridSourceSettings.minimumMagnitude) {
				// probably not mag-filtering, build lists with initial capacity
				int expectedSize = gridRups.size(); 
				if (gridSourceSettings.surfaceType == BackgroundRupType.CROSSHAIR)
					expectedSize *= 2;
				rups = new ArrayList<>(expectedSize);
				rates = new ArrayList<>(expectedSize);
				surfs = new ArrayList<>(expectedSize);
			} else {
				// mag-filtering, don't use initial capacity
				rups = new ArrayList<>();
				rates = new ArrayList<>();
				surfs = new ArrayList<>();
			}
			PointSurfaceBuilder surfBuilder = new PointSurfaceBuilder(gridLoc);
			for (GriddedRupture rup : gridRups) {
				if (rup.properties.magnitude < gridSourceSettings.minimumMagnitude)
					continue;
				boolean forcePointSurf = rup.properties.magnitude < gridSourceSettings.pointSourceMagnitudeCutoff;
				double rate = rup.rate;
				if (aftershockFilter != null)
					rate = aftershockFilter.getFilteredRate(rup.properties.magnitude, rup.rate);
				if (rate == 0d)
					continue;
				updateSurfBuilderForLoc(surfBuilder, rup, forcePointSurf);
				WeightedList<? extends RuptureSurface> rupSurfs = surfBuilder.build(
						forcePointSurf ? BackgroundRupType.POINT : gridSourceSettings.surfaceType, null);
				for (int i=0; i<rupSurfs.size(); i++) {
					RuptureSurface surf = rupSurfs.getValue(i);
					double weight = rupSurfs.getWeight(i);
					rups.add(rup);
					rates.add(rate*weight);
					surfs.add(surf);
				}
			}
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public double getMagnitude(int rupIndex) {
			return rups.get(rupIndex).properties.magnitude;
		}

		@Override
		public double getAveRake(int rupIndex) {
			return rups.get(rupIndex).properties.rake;
		}

		@Override
		public double getRate(int rupIndex) {
			return rates.get(rupIndex);
		}

		@Override
		public RuptureSurface getSurface(int rupIndex) {
			return surfs.get(rupIndex);
		}

		@Override
		public boolean isFinite(int rupIndex) {
			return !(surfs.get(rupIndex) instanceof PointSurface);
		}

		@Override
		public Location getHypocenter(Location sourceLoc, RuptureSurface rupSurface, int rupIndex) {
			return new Location(sourceLoc.lat, sourceLoc.lon, rups.get(rupIndex).properties.getHypocentralDepth());
		}
		
	}
	
	private PoissonPointSource buildSource(Location gridLoc, List<GriddedRupture> gridRups, double duration,
			MagnitudeDependentAftershockFilter aftershockFilter, GriddedSeismicitySettings gridSourceSettings,
			TectonicRegionType tectonicRegionType) {
		if (gridRups.isEmpty())
			return null;
		PointSource.PoissonBuilder builder = PointSource.poissonBuilder(gridLoc, tectonicRegionType);
		
		builder.data(new GriddedRuptureSourceData(gridLoc, gridRups, aftershockFilter, gridSourceSettings));
		builder.distCorrs(gridSourceSettings.distanceCorrections);
		builder.duration(duration);
		
		if (gridSourceSettings.supersamplingSettings != null) {
			Preconditions.checkState(latGridSpacing > 0d && lonGridSpacing > 0d);
			Region gridCell = new Region(new Location(gridLoc.lat - 0.5*latGridSpacing, gridLoc.lon - 0.5*lonGridSpacing),
					new Location(gridLoc.lat + 0.5*latGridSpacing, gridLoc.lon + 0.5*lonGridSpacing));
			builder.siteAdaptiveSupersampled(gridCell, gridSourceSettings.supersamplingSettings);
		}
		
		return builder.build();
	}
	
	private static class RupturePropertyAverager {
		private final PropAverager upperDepthAverager;
		private final PropAverager lowerDepthAverager;
		private final PropAverager lengthAverager;
		private final PropAverager dasAverager;
		private final PropAverager hypoDepthAverager;
		
		private boolean explicitHalfDAS = false;
		private boolean explicitHalfDepth = false;
		
		public RupturePropertyAverager(GriddedRuptureProperties firstProps, double processedSumWeights) {
			upperDepthAverager = new PropAverager(firstProps.upperDepth, processedSumWeights);
			lowerDepthAverager = new PropAverager(firstProps.lowerDepth, processedSumWeights);
			lengthAverager = new PropAverager(firstProps.length, processedSumWeights);
			
			double halfLength = 0.5*firstProps.length;
			if (Double.isNaN(firstProps.hypocentralDAS)) {
				dasAverager = new PropAverager(halfLength, processedSumWeights);
			} else {
				if ((float)firstProps.hypocentralDAS == (float)halfLength)
					explicitHalfDAS = true;
				dasAverager = new PropAverager(firstProps.hypocentralDAS, processedSumWeights);
			}
			
			double midDepth = firstProps.upperDepth + 0.5*(firstProps.lowerDepth - firstProps.upperDepth);
			if (Double.isNaN(firstProps.hypocentralDepth)) {
				hypoDepthAverager = new PropAverager(midDepth, processedSumWeights);
			} else {
				if ((float)firstProps.hypocentralDepth == midDepth)
					explicitHalfDepth = true;
				hypoDepthAverager = new PropAverager(firstProps.hypocentralDepth, processedSumWeights);
			}
		}
		
		public void add(GriddedRuptureProperties properties, double weight) {
			upperDepthAverager.add(properties.upperDepth, weight);
			lowerDepthAverager.add(properties.lowerDepth, weight);
			lengthAverager.add(properties.length, weight);
			
			double halfLength = 0.5*properties.length;
			if (Double.isNaN(properties.hypocentralDAS)) {
				dasAverager.add(halfLength, weight);
			} else {
				if ((float)properties.hypocentralDAS == (float)halfLength)
					explicitHalfDAS = true;
				dasAverager.add(properties.hypocentralDAS, weight);
			}
			
			double midDepth = properties.upperDepth + 0.5*(properties.lowerDepth - properties.upperDepth);
			if (Double.isNaN(properties.hypocentralDepth)) {
				hypoDepthAverager.add(midDepth, weight);
			} else {
				if ((float)properties.hypocentralDepth == midDepth)
					explicitHalfDepth = true;
				hypoDepthAverager.add(properties.hypocentralDepth, weight);
			}
		}
		
		public GriddedRuptureProperties build(GriddedRuptureProperties ref, double processedSumWeights, double sumWeights) {
			double upper = upperDepthAverager.getAverage(processedSumWeights, sumWeights);
			double lower = lowerDepthAverager.getAverage(processedSumWeights, sumWeights);
			double hypoDepth = hypoDepthAverager.getAverage(processedSumWeights, sumWeights);
			if (hypoDepthAverager.allSame && (float)hypoDepth == (float)(upper + 0.5*(lower-upper)) && !explicitHalfDepth)
				hypoDepth = Double.NaN;
			double length = lengthAverager.getAverage(processedSumWeights, sumWeights);
			double hypoDAS = dasAverager.getAverage(processedSumWeights, sumWeights);
			if (dasAverager.allSame && (float)hypoDAS == (float)(0.5*length) && !explicitHalfDAS)
				hypoDAS = Double.NaN;
			return new GriddedRuptureProperties(ref.magnitude, ref.rake, ref.dip, ref.strike, ref.strikeRange,
					upper, lower, length, hypoDepth, hypoDAS, ref.tectonicRegionType);
		}
	}
	
	private static class RuptureAverager {
		private double rateWeightedSum = 0d;
		private int[] associatedSectIDs = null;
		private double[] associatedWeightedRates = null;
		
		private GriddedRuptureProperties firstProps;
		private boolean allPropsIdentical;
		private RupturePropertyAverager propAverager;
		
		private double processedSumWeights = 0d;
		
		public void add(GriddedRupture rup, double weight) {
			if (firstProps == null) {
				firstProps = rup.properties;
				allPropsIdentical = true;
			} else {
				allPropsIdentical &= firstProps.equals(rup.properties);
			}
			rateWeightedSum = Math.fma(rup.rate, weight, rateWeightedSum);
			if (rup.associatedSections != null) {
				if (associatedSectIDs == null) {
					// first association, copy the ids directly
					associatedSectIDs = rup.associatedSections;
					associatedWeightedRates = new double[associatedSectIDs.length];
					for (int i=0; i<associatedWeightedRates.length; i++)
						// but scale the fractions by weight
						associatedWeightedRates[i] = rup.rate*rup.associatedSectionFracts[i]*weight;
				} else {
					// secondary association, need to do ID matching
					for (int i=0; i<rup.associatedSections.length; i++) {
						int index = Ints.indexOf(associatedSectIDs, rup.associatedSections[i]);
						if (index < 0) {
							// new association, expand the array to contain it
							associatedSectIDs = Arrays.copyOf(associatedSectIDs, associatedSectIDs.length+1);
							associatedWeightedRates = Arrays.copyOf(associatedWeightedRates, associatedWeightedRates.length+1);
							associatedSectIDs[associatedSectIDs.length-1] = rup.associatedSections[i];
							associatedWeightedRates[associatedWeightedRates.length-1] = rup.rate*rup.associatedSectionFracts[i]*weight;
						} else {
							// repeat association
							associatedWeightedRates[index] = Math.fma(rup.rate*rup.associatedSectionFracts[i], weight, associatedWeightedRates[index]);
						}
					}
				}
			}
			
			if (!allPropsIdentical) {
				// we have varying properties, need to track them individually
				if (propAverager == null) {
					// first time getting new properties, initialize
					propAverager = new RupturePropertyAverager(firstProps, processedSumWeights);
				}
				
				propAverager.add(rup.properties, weight);
			}
			
			processedSumWeights += weight;
		}
		
		public GriddedRupture build(int gridIndex, Location loc, double sumWeights, GriddedRupturePropertiesCache cache) {
//			boolean D =  gridIndex == 246 && (float)firstProps.magnitude == 7.85f;
//			boolean D =  gridIndex == 416 && (float)firstProps.magnitude == 7.75f;
			boolean D = false;
			if (D) System.out.println("DEBUG build(gridIndex="+gridIndex+", mag="+(float)firstProps.magnitude
						+", sumWeights="+(float)sumWeights+"); processedSumWeights="+(float)processedSumWeights);
			double[] associatedFracts = null;
			if (associatedWeightedRates != null) {
				associatedFracts = new double[associatedWeightedRates.length];
				for (int i=0; i<associatedFracts.length; i++)
					// don't need to normalize by sumWeights, as both numerator and denominator would have the same normalization
					associatedFracts[i] = associatedWeightedRates[i]/rateWeightedSum;
			}
			GriddedRuptureProperties properties = allPropsIdentical ?
					firstProps : cache.getCached(propAverager.build(firstProps, processedSumWeights, sumWeights));
			// rate needs to be normalized by the overall sum of weights (i.e., 0-weight assigned on branches where this rupture doesn't exist)
			if (D) System.out.println("\tfirst props: "+firstProps);
			if (D) System.out.println("\tbuilt props: "+properties);
			double rate = rateWeightedSum/sumWeights;
			return new GriddedRupture(gridIndex, loc, properties, rate, associatedSectIDs, associatedFracts);
		}
	}
	
	private static class PropAverager {
		private double weightedSum = 0d;
		// keep the first value in case they're all the same, in which case we'll use it directly
		// to avoid introducing floating point errors
		private Double firstVal;
		private boolean allSame;
		
		public PropAverager(double initialValue, double initialWeight) {
			firstVal = initialValue;
			allSame = true;
			weightedSum = initialValue * initialWeight;
		}
		
		public void add(double value, double weight) {
			if (firstVal == null) {
				firstVal = value;
				allSame = true;
			} else {
				allSame &= value == firstVal;
			}
			// fused multiply-add: can be more efficient than doing it via += value*weight, and more accurate (only 1 rounding error)
			weightedSum = Math.fma(value, weight, weightedSum);
		}
		
		public double getAverage(double processedSumWeights, double overallSumWeights) {
			if (allSame && (float)overallSumWeights == (float)processedSumWeights) {
				// every single branch (not just those processed here) had the same value, just return it
				return firstVal;
			}
			// this is a property (e.g., depth, length, etc), normalize by the weight of matching ruptures
			// and not the overall weight (as we would if there were a rate quantity)
			Preconditions.checkState(processedSumWeights > 0d);
			return weightedSum/processedSumWeights;
		}
	}
	
	public static class Averager implements AveragingAccumulator<GridSourceProvider> {
		
		private GriddedRegion gridReg = null;
		private LocationList locs;
		private EnumMap<TectonicRegionType, List<List<GriddedRuptureProperties>>> trtRupturePropLists;
		private EnumMap<TectonicRegionType, List<List<RuptureAverager>>> trtRuptureAvgLists;
		private double totWeight = 0d;
		
		private GriddedRupturePropertiesCache cache = new GriddedRupturePropertiesCache();
		
//		private double totM5Sum = 0d;
		
		private boolean haveMultipleDepths;

		@Override
		public Class<GridSourceProvider> getType() {
			return GridSourceProvider.class;
		}
		
		private final boolean D = true;

		@Override
		public void process(GridSourceProvider module, double relWeight) {
			Preconditions.checkState(module instanceof GridSourceList,
					"Can only average if all GridSourceProviders are of type GridSourceList");
			GridSourceList sourceList = (GridSourceList)module;
			if (trtRupturePropLists == null) {
				// first time through, init
				trtRupturePropLists = new EnumMap<>(TectonicRegionType.class);
				trtRuptureAvgLists = new EnumMap<>(TectonicRegionType.class);
				Preconditions.checkState(totWeight == 0d, "Can't reuse averagers");
				this.gridReg = sourceList.getGriddedRegion();
				this.locs = sourceList.locs;
				
				// see if we have multiple ruptures with different depths; if so, we'll retain each unique rupture
				// across all branches including depth information, otherwise we'll average depths
				this.haveMultipleDepths = false;
				for (TectonicRegionType trt : sourceList.getTectonicRegionTypes()) {
					for (int gridIndex=0; !haveMultipleDepths && gridIndex<locs.size(); gridIndex++) {
						List<GriddedRupture> ruptures = sourceList.getRuptures(trt, gridIndex);
						if (!ruptures.isEmpty()) {
							List<GriddedRuptureProperties> props = new ArrayList<>(ruptures.size());
							for (GriddedRupture rup : ruptures)
								props.add(rup.properties);
							props.sort(RUP_NON_AVERAGED_PROPS_COMPARATOR);
							for (int i=1; !haveMultipleDepths && i<ruptures.size(); i++)
								haveMultipleDepths = RUP_NON_AVERAGED_PROPS_COMPARATOR.compare(props.get(i-1), props.get(i)) == 0;
						}
					}
				}
				if (haveMultipleDepths)
					System.out.println("GridSourceList.Averager: we have multiple depths/lengths for otherwise unique ruptures; "
							+ "will not average any depth information");
			} else {
				// make sure they're identical
				Preconditions.checkState(locs.size() == sourceList.getNumLocations());
				for (int i=0; i<locs.size(); i++)
					Preconditions.checkState(LocationUtils.areSimilar(locs.get(i), sourceList.getLocation(i)));
			}
			
			Comparator<GriddedRuptureProperties> comp = haveMultipleDepths ? RUP_FULL_PROPS_COMPARATOR : RUP_NON_AVERAGED_PROPS_COMPARATOR;
			
			for (TectonicRegionType trt : sourceList.getTectonicRegionTypes()) {
				List<List<GriddedRuptureProperties>> rupturePropLists = trtRupturePropLists.get(trt);
				List<List<RuptureAverager>> ruptureAvgLists = trtRuptureAvgLists.get(trt);
				if (rupturePropLists == null) {
					rupturePropLists = new ArrayList<>(locs.size());
					ruptureAvgLists = new ArrayList<>(locs.size());
					for (int i=0; i<locs.size(); i++) {
						rupturePropLists.add(null);
						ruptureAvgLists.add(null);
					}
					trtRupturePropLists.put(trt, rupturePropLists);
					trtRuptureAvgLists.put(trt, ruptureAvgLists);
				}
				int[] prevMatchIndexes = null;
				for (int gridIndex=0; gridIndex<locs.size(); gridIndex++) {
					List<GriddedRupture> ruptures = sourceList.getRuptures(trt, gridIndex);
					if (!ruptures.isEmpty()) {
						if (prevMatchIndexes == null) {
							prevMatchIndexes = new int[ruptures.size()];
							for (int i=0; i<prevMatchIndexes.length; i++)
								prevMatchIndexes[i] = -1;
						} else if (ruptures.size() > prevMatchIndexes.length) {
							int prevLen = prevMatchIndexes.length;
							prevMatchIndexes = Arrays.copyOf(prevMatchIndexes, ruptures.size());
							for (int i=prevLen; i<prevMatchIndexes.length; i++)
								prevMatchIndexes[i] = -1;
						}
						List<GriddedRuptureProperties> rupturePropsList = rupturePropLists.get(gridIndex);
						List<RuptureAverager> ruptureAvgs = ruptureAvgLists.get(gridIndex);
						if (rupturePropsList == null) {
							rupturePropsList = new ArrayList<>();
							ruptureAvgs = new ArrayList<>();
							rupturePropLists.set(gridIndex, rupturePropsList);
							ruptureAvgLists.set(gridIndex, ruptureAvgs);
						}
						for (int i=0; i<ruptures.size(); i++) {
							GriddedRupture rupture = ruptures.get(i);
							int index;
							// see if the indexes already line up and we can skip the binary search
							if (prevMatchIndexes[i] >= 0 && rupturePropsList.size() > prevMatchIndexes[i] && comp.compare(rupture.properties, rupturePropsList.get(prevMatchIndexes[i])) == 0) {
								// we found a previous match for this index
								index = prevMatchIndexes[i];
							} else if (i < rupturePropsList.size() && comp.compare(rupture.properties, rupturePropsList.get(i)) == 0) {
								// lines up already
								index = i;
							} else {
								// need to do the search
								index = Collections.binarySearch(rupturePropsList, rupture.properties, comp);
							}
							
							RuptureAverager props;
							if (index < 0) {
								// new, need to add it
								index = -(index + 1);
								rupturePropsList.add(index, cache.getCached(rupture.properties));
								props = new RuptureAverager();
								ruptureAvgs.add(index, props);
							} else {
								// duplicate
								props = ruptureAvgs.get(index);
								if (i != index)
									prevMatchIndexes[i] = index;
							}
							props.add(rupture, relWeight);
						}
						Preconditions.checkState(rupturePropsList.size() == ruptureAvgs.size());
					}
				}
			}
			
			totWeight += relWeight;
		}

		@Override
		public GridSourceProvider getAverage() {
			Preconditions.checkState(totWeight > 0d, "No weight assigned?");
//			System.out.println("Building average; totM5="+(float)(totM5Sum/totWeight));
			
			EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureListsOut = new EnumMap<>(TectonicRegionType.class);
//			double testRateM5 = 0d;
			
			for (TectonicRegionType trt : trtRupturePropLists.keySet()) {
				List<List<GriddedRuptureProperties>> rupturePropLists = trtRupturePropLists.get(trt);
				List<List<RuptureAverager>> ruptureAvgLists = trtRuptureAvgLists.get(trt);
				Preconditions.checkState(rupturePropLists.size() == locs.size(),
						"rupList has %s, expected %s", rupturePropLists.size(), locs.size());
				Preconditions.checkState(ruptureAvgLists.size() == locs.size(),
						"rupPropList has %s, expected %s", ruptureAvgLists.size(), locs.size());
				
				List<List<GriddedRupture>> ruptureListsOut = new ArrayList<>(locs.size());
				
				for (int gridIndex=0; gridIndex<locs.size(); gridIndex++) {
					if (rupturePropLists.get(gridIndex) == null) {
						ruptureListsOut.add(null);
					} else {
						List<GriddedRuptureProperties> rupturePropsList = rupturePropLists.get(gridIndex);
						List<RuptureAverager> ruptureAvgs = ruptureAvgLists.get(gridIndex);
						Preconditions.checkState(rupturePropsList.size() == ruptureAvgs.size(),
								"rupList has %s, props has %s", rupturePropsList.size(), ruptureAvgs.size());
						List<GriddedRupture> ruptureListOut = new ArrayList<>(rupturePropsList.size());
						for (int i=0; i<rupturePropsList.size(); i++) {
							GriddedRupture modRup = ruptureAvgs.get(i).build(gridIndex, locs.get(gridIndex), totWeight, cache);
//							if (modRup.magnitude >= 5d)
//								testRateM5 += modRup.rate;
							ruptureListOut.add(modRup);
						}
						ruptureListsOut.add(ruptureListOut);
						Preconditions.checkState(ruptureListOut.size() == rupturePropsList.size());
					}
				}
				Preconditions.checkState(ruptureListsOut.size() == locs.size());
				
				trtRuptureListsOut.put(trt, ruptureListsOut);
			}
			
//			System.out.println("Averaged totM5="+(float)testRateM5);
			
			GridSourceList ret = new GridSourceList.Precomputed(gridReg, locs, trtRuptureListsOut);
			
			trtRupturePropLists = null; // to prevent reuse
			return ret;
		}
		
	}
	
	public interface FiniteRuptureConverter {
		
		public GriddedRupture buildFiniteRupture(int gridIndex, Location loc, double magnitude, double rate,
				FocalMech focalMech, TectonicRegionType trt, int[] associatedSections, double[] associatedSectionFracts,
				GriddedRupturePropertiesCache cache);
	}
	
	private static List<GriddedRupture> convertGridIndex(MFDGridSourceProvider mfdGridProv, FaultGridAssociations associations,
			FiniteRuptureConverter converter, int gridIndex, GriddedRupturePropertiesCache cache) {
		double fractSS = mfdGridProv.getFracStrikeSlip(gridIndex);
		double fractN = mfdGridProv.getFracNormal(gridIndex);
		double fractR = mfdGridProv.getFracReverse(gridIndex);
		TectonicRegionType trt = mfdGridProv.getTectonicRegionType(gridIndex);
		
		IncrementalMagFreqDist mfd = mfdGridProv.getMFD(trt, gridIndex);
		if (mfd == null)
			return null;
		IncrementalMagFreqDist mfdAssoc = mfdGridProv.getMFD_SubSeisOnFault(gridIndex);
		Map<Integer, Double> nodeFractAssociations = null;
		if (mfdAssoc != null) {
			nodeFractAssociations = new HashMap<>(associations.getScaledSectFracsOnNode(gridIndex));
			Preconditions.checkState(!nodeFractAssociations.isEmpty());
			// turn it into a fractional: scale to 1 if not already
			double sumFracts = 0d;
			for (double fract : nodeFractAssociations.values())
				sumFracts += fract;
			if ((float)sumFracts != 1f) {
				for (int sectIndex : new ArrayList<>(nodeFractAssociations.keySet()))
					nodeFractAssociations.put(sectIndex, nodeFractAssociations.get(sectIndex)/sumFracts);
			}
		}
		List<GriddedRupture> ruptureList = new ArrayList<>();
		for (int m=0; m<mfd.size(); m++) {
			double mag = mfd.getX(m);
			double totRate = mfd.getY(m);
			if (totRate == 0d)
				continue;
			double associatedFract = 0d;
			if (mfdAssoc != null && mfdAssoc.size() > m) {
				Preconditions.checkState((float)mfdAssoc.getX(m) == (float)mag);
				double assocRate = mfdAssoc.getY(mag);
				associatedFract = assocRate/totRate;
				Preconditions.checkState((float)associatedFract <= 1f, "Bad associatedFract = %s / %s = %s",
						assocRate, totRate, associatedFract);
			}
			for (FocalMech mech : FocalMech.values()) {
				double mechRate;
				switch (mech) {
				case STRIKE_SLIP:
					mechRate = totRate*fractSS;
					break;
				case NORMAL:
					mechRate = totRate*fractN;
					break;
				case REVERSE:
					mechRate = totRate*fractR;
					break;

				default:
					throw new IllegalStateException();
				}
				if (mechRate == 0d)
					continue;
				
				int[] associatedSections = null;
				double[] associatedSectionFracts = null;
				if (associatedFract > 0) {
					List<Integer> sectIndexes = new ArrayList<>(nodeFractAssociations.keySet());
					Collections.sort(sectIndexes);
					associatedSections = new int[sectIndexes.size()];
					associatedSectionFracts = new double[sectIndexes.size()];
					for (int s=0; s<sectIndexes.size(); s++) {
						int sectIndex = sectIndexes.get(s);
						associatedSections[s] = sectIndex;
						associatedSectionFracts[s] = associatedFract * nodeFractAssociations.get(sectIndex);
					}
				}
				ruptureList.add(converter.buildFiniteRupture(gridIndex, mfdGridProv.getLocation(gridIndex),
						mag, mechRate, mech, trt, associatedSections, associatedSectionFracts, cache));
			}
		}
		return ruptureList;
	}
	
	public static GridSourceList convert(MFDGridSourceProvider mfdGridProv, FaultGridAssociations associations,
			FiniteRuptureConverter converter) {
		int numLocs = mfdGridProv.getNumLocations();
		
		GriddedRupturePropertiesCache cache = new GriddedRupturePropertiesCache();
		
		EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
		for (int gridIndex=0; gridIndex<numLocs; gridIndex++) {
			TectonicRegionType trt = mfdGridProv.getTectonicRegionType(gridIndex);
			List<List<GriddedRupture>> ruptureLists = trtRuptureLists.get(trt);
			if (ruptureLists == null) {
				ruptureLists = new ArrayList<>(numLocs);
				for (int i=0; i<numLocs; i++)
					ruptureLists.add(null);
				trtRuptureLists.put(trt, ruptureLists);
			}
			
			ruptureLists.set(gridIndex, convertGridIndex(mfdGridProv, associations, converter, gridIndex, cache));
		}
		return new GridSourceList.Precomputed(mfdGridProv.getGriddedRegion(), trtRuptureLists);
	}
	
	public static GridSourceList combine(GridSourceList... gridLists) {
		Preconditions.checkState(gridLists.length > 1);
		
		if (gridLists[0].gridReg != null) {
			// first try to do it using a combined region
			GriddedRegion unionGridReg = gridLists[0].getGriddedRegion();
			double latSpacing = unionGridReg.getLatSpacing();
			double lonSpacing = unionGridReg.getLonSpacing();
			Location anchor = unionGridReg.getLocation(0);
			List<String> unionedNames = new ArrayList<>();
			unionedNames.add(unionGridReg.getName());
			for (int i=1; unionGridReg != null && i<gridLists.length; i++) {
				GriddedRegion myReg = gridLists[i].getGriddedRegion();
				if (myReg == null) {
					System.err.println("WARNING: region "+i+" is null, won't use regions when combining");
					unionGridReg = null;
				} else if ((float)myReg.getLatSpacing() != (float)latSpacing
							|| (float)myReg.getLonSpacing() != (float)lonSpacing) {
					System.err.println("WARNING: region "+i+" ("+myReg.getName()+") has different spacing than previous region(s)");
					unionGridReg = null;
				} else {
					if (myReg.equals(unionGridReg))
						continue;
					// see if that region contains this one
					boolean fullyContained = true;
					for (Location loc : myReg.getNodeList()) {
						if (unionGridReg.indexForLocation(loc) < 0) {
							fullyContained = false;
							break;
						}
					}
					if (fullyContained)
						// no need to union, this one is already fully contained
						continue;
					Region unionReg = Region.union(unionGridReg, myReg);
					if (unionReg == null) {
						unionGridReg = null;
						System.err.println("WARNING: couldn't union region "+i+" ("+myReg.getName()+") with prior region(s): "
								+Joiner.on("; ").join(unionedNames));
						break;
					}
					unionedNames.add(myReg.getName());
					unionGridReg = new GriddedRegion(unionReg, latSpacing, lonSpacing, anchor);
				}
			}
			
			if (unionGridReg != null) {
				// might work, but make sure that we still contain all of the grid nodes
				boolean fullyContained = true;
				for (GridSourceList gridList : gridLists) {
					GriddedRegion myReg = gridList.getGriddedRegion();
					for (Location loc : myReg.getNodeList()) {
						if (unionGridReg.indexForLocation(loc) < 0) {
							fullyContained = false;
							break;
						}
					}
					if (!fullyContained)
						break;
				}
				if (fullyContained) {
					System.out.println("Building combined GridSourceList using stitched gridded region");
					return combine(unionGridReg, gridLists);
				}
				System.err.println("WARNING: built a stitched gridded region for all sub-regions but there's a gridding "
						+ "mismatch, will revert to just a location list");
			} else {
				System.err.println("WARNING: couldn't build a stitched gridded region for all sub-regions, will revert "
						+ "to just a location list");
			}
		}
		
		System.out.println("Building combined GridSourceList using a location list (no stitched region)");
		
		LocationList locs = new LocationList();
		Map<Location, Integer> locIndexMap = new HashMap<>();
		EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
		
		// first find each unique location
		int rawNumLocs = 0;
		for (GridSourceList gridList : gridLists) {
			for (int gridIndex=0; gridIndex<gridList.getNumLocations(); gridIndex++) {
				Location loc = gridList.getLocation(gridIndex);
				Integer index = locIndexMap.get(loc);
				rawNumLocs++;
				if (index == null) {
					index = locs.size();
					locs.add(loc);
					locIndexMap.put(loc, index);
				}
			}
		}
		System.out.println("Found "+locs.size()+" unique locations (out of "+rawNumLocs+" total)");
		
		for (GridSourceList gridList : gridLists) {
			for (TectonicRegionType trt : gridList.getTectonicRegionTypes()) {
				List<List<GriddedRupture>> ruptureLists = trtRuptureLists.get(trt);
				if (ruptureLists == null) {
					ruptureLists = new ArrayList<>(locs.size());
					for (int i=0; i<locs.size(); i++)
						ruptureLists.add(null);
					trtRuptureLists.put(trt, ruptureLists);
				}
				for (int gridIndex=0; gridIndex<gridList.getNumLocations(); gridIndex++) {
					ImmutableList<GriddedRupture> rups = gridList.getRuptures(trt, gridIndex);
					if (!rups.isEmpty()) {
						Location loc = gridList.getLocation(gridIndex);
						Integer mappedIndex = locIndexMap.get(loc);
						Preconditions.checkNotNull(mappedIndex,
								"Location %s is not mapped to a location in the combined location list?", loc);
						List<GriddedRupture> destRups = ruptureLists.get(mappedIndex);
						if (destRups == null) {
							destRups = new ArrayList<>(rups.size());
							ruptureLists.set(mappedIndex, destRups);
						}
						for (GriddedRupture rup : rups)
							destRups.add(rup.copyNewGridIndex(mappedIndex));
					}
				}
			}
		}
		
		return new GridSourceList.Precomputed(locs, trtRuptureLists);
	}
	
	public static GridSourceList combine(GriddedRegion combRegion, GridSourceList... gridLists) {
		EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
		
		for (GridSourceList gridList : gridLists) {
			for (TectonicRegionType trt : gridList.getTectonicRegionTypes()) {
				List<List<GriddedRupture>> ruptureLists = trtRuptureLists.get(trt);
				if (ruptureLists == null) {
					ruptureLists = new ArrayList<>(combRegion.getNodeCount());
					for (int i=0; i<combRegion.getNodeCount(); i++)
						ruptureLists.add(null);
					trtRuptureLists.put(trt, ruptureLists);
				}
				for (int gridIndex=0; gridIndex<gridList.getNumLocations(); gridIndex++) {
					ImmutableList<GriddedRupture> rups = gridList.getRuptures(trt, gridIndex);
					if (!rups.isEmpty()) {
						Location loc = gridList.getLocation(gridIndex);
						Preconditions.checkState(loc.equals(gridList.getGriddedRegion().getLocation(gridIndex)));
						int mappedGridIndex = combRegion.indexForLocation(loc);
						Preconditions.checkState(mappedGridIndex >= 0,
								"Location %s is not mapped to a location in the given combined gridded region", loc);
						List<GriddedRupture> destRups = ruptureLists.get(mappedGridIndex);
						if (destRups == null) {
							destRups = new ArrayList<>(rups.size());
							ruptureLists.set(mappedGridIndex, destRups);
						}
						for (GriddedRupture rup : rups)
							destRups.add(rup.copyNewGridIndex(mappedGridIndex));
					}
				}
			}
		}
		
		return new GridSourceList.Precomputed(combRegion, trtRuptureLists);
	}
	
	public static class Precomputed extends GridSourceList {
		
		private IncrementalMagFreqDist refMFD; // used for getMFD(...) methods
		
		// the actual rupture data
		private EnumMap<TectonicRegionType, ImmutableList<ImmutableList<GriddedRupture>>> trtRuptureLists;
		
		// mappings from source index to tectonic region type and grid index
		private TectonicRegionType[] sourceTRTs;
		private int[] sourceGridIndexes;
		
		// mapping from associated sections to grid nodes
		private Map<Integer, Set<Integer>> sectAssociations;
		
		@SuppressWarnings("unused") // for deserialization
		private Precomputed() {};
		
		public Precomputed(LocationList locs, EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
			setAll(null, locs, trtRuptureLists);
		}
		
		public Precomputed(GriddedRegion gridReg, EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
			setAll(gridReg, gridReg.getNodeList(), trtRuptureLists);
		}
		
		public Precomputed(GriddedRegion gridReg, LocationList locs, EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
			setAll(gridReg, locs, trtRuptureLists);
		}
		
		public Precomputed(GridSourceList original, EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
			setAll(original.gridReg, original.locs, trtRuptureLists);
		}
		
		public Precomputed(GriddedRegion gridReg, TectonicRegionType trt, List<? extends List<GriddedRupture>> ruptureLists) {
			EnumMap<TectonicRegionType, List<? extends List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
			trtRuptureLists.put(trt, ruptureLists);
			setAll(gridReg, gridReg.getNodeList(), trtRuptureLists);
		}
		
		public Precomputed(LocationList locs, TectonicRegionType trt, List<? extends List<GriddedRupture>> ruptureLists) {
			EnumMap<TectonicRegionType, List<? extends List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
			trtRuptureLists.put(trt, ruptureLists);
			setAll(null, locs, trtRuptureLists);
		}
		
		private void setAll(GriddedRegion gridReg, LocationList locs,
				EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
			setLocations(gridReg, locs);
			int sourceCount = 0;
			for (TectonicRegionType trt : trtRuptureLists.keySet())
				for (List<GriddedRupture> ruptures : trtRuptureLists.get(trt))
					if (ruptures != null && !ruptures.isEmpty())
						sourceCount++;
			
			TectonicRegionType[] sourceTRTs = new TectonicRegionType[sourceCount];
			int[] sourceGridIndexes = new int[sourceCount];
			
			EnumMap<TectonicRegionType, ImmutableList<ImmutableList<GriddedRupture>>> trtRuptureListsOut = new EnumMap<>(TectonicRegionType.class);
			double minMag = Double.POSITIVE_INFINITY;
			double maxMag = Double.NEGATIVE_INFINITY;
			boolean magsTenthAligned = true;
			int numRups = 0;
			int sourceIndex = 0;
			HashMap<Integer, Set<Integer>> sectAssociations = new HashMap<>();
			for (TectonicRegionType trt : TectonicRegionType.values()) {
				List<? extends List<GriddedRupture>> ruptureLists = trtRuptureLists.get(trt);
				if (ruptureLists == null)
					continue;
				Preconditions.checkState(ruptureLists.size() == locs.size());
				ImmutableList.Builder<ImmutableList<GriddedRupture>> ruptureListsBuilder = ImmutableList.builder();
				for (int gridIndex=0; gridIndex<locs.size(); gridIndex++) {
					Location gridLoc = locs.get(gridIndex);
					List<GriddedRupture> ruptures = ruptureLists.get(gridIndex);
					if (ruptures == null || ruptures.isEmpty()) {
						ruptureListsBuilder.add(ImmutableList.of());
					} else {
						for (GriddedRupture rup : ruptures) {
							Preconditions.checkState(rup.properties.tectonicRegionType == trt, "Rupture says TRT is %s, but we're in the list for %s",
									rup.properties.tectonicRegionType, trt);
							Preconditions.checkState(LocationUtils.areSimilar(rup.location, gridLoc));
							numRups++;
							minMag = Math.min(minMag, rup.properties.magnitude);
							maxMag = Math.max(maxMag, rup.properties.magnitude);
							// detect the case where ruptures are directly on the tenths (e.g., 5.0, 5.1)
							magsTenthAligned &= (float)(rup.properties.magnitude*10d) == (float)Math.floor(rup.properties.magnitude*10d);
							
							if (rup.associatedSections != null) {
								for (int sectID : rup.associatedSections) {
									Set<Integer> sectNodes = sectAssociations.get(sectID);
									if (sectNodes == null) {
										sectNodes = new HashSet<>();
										sectAssociations.put(sectID, sectNodes);
									}
									sectNodes.add(gridIndex);
								}
							}
						}
						
						ruptureListsBuilder.add(ImmutableList.copyOf(ruptures));
						sourceTRTs[sourceIndex] = trt;
						sourceGridIndexes[sourceIndex] = gridIndex;
						sourceIndex++;
					}
				}
				trtRuptureListsOut.put(trt, ruptureListsBuilder.build());
			}
			Preconditions.checkState(sourceIndex == sourceCount,
					"Source count mismatch; expected=%s, sourceIndex=%s after last", sourceCount, sourceIndex);
			Preconditions.checkState(numRups > 0, "Must supply at least 1 rupture to determine MFD gridding");
			double delta = 0.1;
			if (!magsTenthAligned) {
				// align to 0.x5 bins (so that bin edges are at tenths)
				minMag = Math.floor(minMag*10d)/10d + 0.5*delta;
				maxMag = Math.floor(maxMag*10d)/10d + 0.5*delta;
			}
			int size = (int)Math.round((maxMag - minMag)/delta) + 1;
			refMFD = new IncrementalMagFreqDist(minMag, size, delta);
			
			this.trtRuptureLists = trtRuptureListsOut;
			this.sourceTRTs = sourceTRTs;
			this.sourceGridIndexes = sourceGridIndexes;
			this.sectAssociations = sectAssociations;
		}
		
		@Override
		public Set<TectonicRegionType> getTectonicRegionTypes() {
			return trtRuptureLists.keySet();
		}

		@Override
		public int getNumSources() {
			return sourceGridIndexes.length;
		}

		@Override
		public int getLocationIndexForSource(int sourceIndex) {
			return sourceGridIndexes[sourceIndex];
		}

		@Override
		public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
			return sourceTRTs[sourceIndex];
		}
		
		@Override
		public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
			Set<Integer> ret = sectAssociations.get(sectionIndex);
			if (ret == null)
				return Set.of();
			return ret;
		}
		
		@Override
		public IncrementalMagFreqDist getRefMFD() {
			return refMFD;
		}
		
		public ImmutableList<GriddedRupture> getRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			if (tectonicRegionType == null) {
				ImmutableList.Builder<GriddedRupture> listBuilder = ImmutableList.builder();
				for (TectonicRegionType trt : trtRuptureLists.keySet())
					listBuilder.addAll(trtRuptureLists.get(trt).get(gridIndex));
				return listBuilder.build();
			}
			ImmutableList<ImmutableList<GriddedRupture>> trtList = trtRuptureLists.get(tectonicRegionType);
			if (trtList == null)
				return ImmutableList.of();
			return trtList.get(gridIndex);
		}

		@Override
		public void scaleAll(TectonicRegionType tectonicRegionType, double[] valuesArray) {
			Preconditions.checkState(valuesArray.length == getNumLocations(),
					"Scale value size mismatch: %s != %s", valuesArray.length, getNumLocations());
			ImmutableList.Builder<ImmutableList<GriddedRupture>> modRupListBuilder = ImmutableList.builder();
			ImmutableList<ImmutableList<GriddedRupture>> ruptureLists = trtRuptureLists.get(tectonicRegionType);
			for (int i=0; i<valuesArray.length; i++) {
				ImmutableList<GriddedRupture> origRups = ruptureLists.get(i);
				if (valuesArray[i] == 0d) {
					modRupListBuilder.add(ImmutableList.of());
				} else if (valuesArray[i] == 1d) {
					modRupListBuilder.add(origRups);
				} else {
					ImmutableList.Builder<GriddedRupture> modRupBuilder = ImmutableList.builder();
					for (GriddedRupture rup : origRups)
						modRupBuilder.add(rup.copyNewRate(rup.rate*valuesArray[i]));
					modRupListBuilder.add(modRupBuilder.build());
				}
			}
			trtRuptureLists.put(tectonicRegionType, modRupListBuilder.build());
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			// load gridded region (if supplied)
			GriddedRegion gridReg = null;
			if (FileBackedModule.hasEntry(input, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)) {
				BufferedInputStream regionIS = FileBackedModule.getInputStream(input, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
				InputStreamReader regionReader = new InputStreamReader(regionIS);
				Feature regFeature = Feature.read(regionReader);
				gridReg = GriddedRegion.fromFeature(regFeature);
			}
			
			// load grid location CSV
			CSVFile<String> gridCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, ARCHIVE_GRID_LOCS_FILE_NAME);
			LocationList locs = loadGridLocsCSV(gridCSV, gridReg);
			
			// load ruptures themselves
			CSVReader rupSectsCSV = LargeCSV_BackedModule.loadFromArchive(input, entryPrefix, ARCHIVE_GRID_SOURCES_FILE_NAME);
			EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = loadGridSourcesCSV(rupSectsCSV, locs);
			setAll(gridReg, locs, trtRuptureLists);
		}
		
	}
	
	/**
	 * Utility class for dynamically building a GridSourceList without pre-caching all ruptures in memory
	 */
	public static abstract class DynamicallyBuilt extends GridSourceList {
		
		private Set<TectonicRegionType> trts;
		private IncrementalMagFreqDist refMFD;

		public DynamicallyBuilt(Set<TectonicRegionType> trts, LocationList locs, IncrementalMagFreqDist refMFD) {
			this(trts, null, locs, refMFD);
		}

		public DynamicallyBuilt(Set<TectonicRegionType> trts, GriddedRegion gridReg, IncrementalMagFreqDist refMFD) {
			this(trts, gridReg, gridReg.getNodeList(), refMFD);
		}

		private DynamicallyBuilt(Set<TectonicRegionType> trts, GriddedRegion gridReg, LocationList locs, IncrementalMagFreqDist refMFD) {
			super(gridReg, locs);
			this.trts = trts;
			this.refMFD = refMFD;
		}
		
		protected abstract List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex);
		
		@Override
		public Set<TectonicRegionType> getTectonicRegionTypes() {
			return trts;
		}

		@Override
		public ImmutableList<GriddedRupture> getRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			List<GriddedRupture> rups = buildRuptures(tectonicRegionType, gridIndex);
			if (rups == null || rups.isEmpty())
				return ImmutableList.of();
			return ImmutableList.copyOf(rups);
		}

		@Override
		public void scaleAll(TectonicRegionType tectonicRegionType, double[] valuesArray) {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public IncrementalMagFreqDist getRefMFD() {
			return refMFD;
		}
		
	}
	
	private static class MinMagFiltered extends DynamicallyBuilt {
		
		private GridSourceList gridSources;
		private float minMag;

		public MinMagFiltered(GridSourceList gridSources, float minMag) {
			super(gridSources.getTectonicRegionTypes(), gridSources.getGriddedRegion(),
					gridSources.locs, gridSources.getRefMFD());
			this.gridSources = gridSources;
			this.minMag = minMag;
		}

		@Override
		public int getNumSources() {
			return gridSources.getNumSources();
		}

		@Override
		public int getLocationIndexForSource(int sourceIndex) {
			return gridSources.getLocationIndexForSource(sourceIndex);
		}

		@Override
		protected List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			ImmutableList<GriddedRupture> origRups = gridSources.getRuptures(tectonicRegionType, gridIndex);
			if (origRups.isEmpty())
				return origRups;
			List<GriddedRupture> rups = new ArrayList<>();
			for (GriddedRupture rup : origRups)
				if ((float)rup.properties.magnitude >= minMag)
					rups.add(rup);
			return rups;
		}

		@Override
		public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
			return gridSources.tectonicRegionTypeForSourceIndex(sourceIndex);
		}

		@Override
		public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
			return gridSources.getAssociatedGridIndexes(sectionIndex);
		}
		
	}
	
	public static GridSourceList remapAssociations(GridSourceList original, int[] sectRemappings) {
		Map<Integer, Integer> map = new HashMap<>(sectRemappings.length);
		for (int s=0; s<sectRemappings.length; s++)
			map.put(s, sectRemappings[s]);
		return remapAssociations(original, map);
	}
	
	public static GridSourceList remapAssociations(GridSourceList original, Map<Integer, Integer> sectRemappings) {
		return new GridSourceListAssocRemapper(original, sectRemappings);
	}
	
	private static class GridSourceListAssocRemapper extends GridSourceList.DynamicallyBuilt {

		private GridSourceList orig;
		private Map<Integer, Integer> sectRemappings;
		private Map<Integer, Integer> sectRemappingsReversed;
		private HashSet<Integer> unmappedAssociations;

		public GridSourceListAssocRemapper(GridSourceList orig, Map<Integer, Integer> sectRemappings) {
			super(orig.getTectonicRegionTypes(), orig.getGriddedRegion(), orig.locs, orig.getRefMFD());
			this.orig = orig;
			this.sectRemappings = sectRemappings;
			sectRemappingsReversed = new HashMap<>(sectRemappings.size());
			for (int key : sectRemappings.keySet())
				sectRemappingsReversed.put(sectRemappings.get(key), key);
			unmappedAssociations = new HashSet<>();
		}

		@Override
		public int getNumSources() {
			return orig.getNumSources();
		}

		@Override
		public int getLocationIndexForSource(int sourceIndex) {
			return orig.getLocationIndexForSource(sourceIndex);
		}

		@Override
		protected List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			List<GriddedRupture> origRups = orig.getRuptures(tectonicRegionType, gridIndex);
			if (origRups.isEmpty())
				return origRups;
			List<GriddedRupture> ret = new ArrayList<>(origRups.size());
			for (GriddedRupture rup : origRups) {
				if (rup.associatedSections != null && rup.associatedSections.length > 0) {
					int[] remapped = new int[rup.associatedSections.length];
					for (int i=0; i<rup.associatedSections.length; i++) {
						int origIndex = rup.associatedSections[i];
						if (!sectRemappings.containsKey(origIndex)) {
							if (!unmappedAssociations.contains(origIndex)) {
								// first time we've encountered it
								// make sure there are no conflicts that map to this index
								for (Entry<Integer, Integer> remapping : sectRemappings.entrySet()) {
									Preconditions.checkState(!remapping.getValue().equals(origIndex),
											"Encountered association with index=%s for which no remapping exists, and it "
											+ "conflicts with a remapping from %s->%s.",
											origIndex, remapping.getKey(), remapping.getValue());
								}
								unmappedAssociations.add(origIndex);
								System.err.println("WARNING (GridSourceListAssocRemapper): encountered unexpected association ID of "
										+origIndex+" without a remapping; allowing it because there are no ID conflicts.");
							}
							remapped[i] = origIndex;
						} else {
							remapped[i] = sectRemappings.get(rup.associatedSections[i]);
						}
					}
					ret.add(new GriddedRupture(rup.gridIndex, rup.location, rup.properties, rup.rate, remapped, rup.associatedSectionFracts));
				} else {
					ret.add(rup);
				}
			}
			return ret;
		}

		@Override
		public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
			return orig.tectonicRegionTypeForSourceIndex(sourceIndex);
		}

		@Override
		public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
			// sectionIndex here is the new index, need to find the corresponding old one
			Integer origIndex = sectRemappingsReversed.get(sectionIndex);
			Preconditions.checkNotNull(origIndex, "No mapping found for new sectionIndex=%s", sectionIndex);
			return orig.getAssociatedGridIndexes(origIndex);
		}
		
	}
	
	public static class DynamicConverter extends DynamicallyBuilt {

		private MFDGridSourceProvider mfdGridProv;
		private FaultGridAssociations associations;
		private FiniteRuptureConverter converter;
		private GriddedRupturePropertiesCache cache;

		public DynamicConverter(MFDGridSourceProvider mfdGridProv, FaultGridAssociations associations,
				FiniteRuptureConverter converter) {
			super(mfdGridProv.getTectonicRegionTypes(), mfdGridProv.getGriddedRegion(), getRefMFD(mfdGridProv));
			this.mfdGridProv = mfdGridProv;
			this.associations = associations;
			this.converter = converter;
			this.cache = new GriddedRupturePropertiesCache();
		}
		
		private static IncrementalMagFreqDist getRefMFD(MFDGridSourceProvider mfdGridProv) {
			IncrementalMagFreqDist ref = null;
			int maxSize = 0;
			for (int gridIndex=0; gridIndex<mfdGridProv.getNumLocations(); gridIndex++) {
				IncrementalMagFreqDist mfd = mfdGridProv.getMFD(gridIndex);
				if (mfd == null)
					continue;
				if (ref == null)
					ref = mfd;
				maxSize = Integer.max(maxSize, mfd.size());
			}
			return new IncrementalMagFreqDist(ref.getMinX(), maxSize, ref.getDelta());
		}

		@Override
		public int getLocationIndexForSource(int sourceIndex) {
			return sourceIndex;
		}

		@Override
		public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
			return mfdGridProv.getTectonicRegionType(sourceIndex);
		}

		@Override
		protected List<GriddedRupture> buildRuptures(TectonicRegionType tectonicRegionType, int gridIndex) {
			if (tectonicRegionType == mfdGridProv.getTectonicRegionType(gridIndex))
				return convertGridIndex(mfdGridProv, associations, converter, gridIndex, cache);
			return null;
		}

		@Override
		public double getFracStrikeSlip(int gridIndex) {
			return mfdGridProv.getFracStrikeSlip(gridIndex);
		}

		@Override
		public double getFracReverse(int gridIndex) {
			return mfdGridProv.getFracReverse(gridIndex);
		}

		@Override
		public double getFracNormal(int gridIndex) {
			return mfdGridProv.getFracNormal(gridIndex);
		}

		@Override
		public int getNumSources() {
			return getNumLocations();
		}

		@Override
		public Set<Integer> getAssociatedGridIndexes(int sectionIndex) {
			Map<Integer, Double> assoc = associations.getNodeFractions(sectionIndex);
			if (assoc == null || assoc.isEmpty())
				return Set.of();
			return assoc.keySet();
		}
		
	}

}
