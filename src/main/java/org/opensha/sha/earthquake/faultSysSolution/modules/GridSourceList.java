package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVReader.Row;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSurfaceBuilder;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

public class GridSourceList implements GridSourceProvider, ArchivableModule {
	
	private LocationList locs;
	private GriddedRegion gridReg; // can be null
	
	private EnumMap<TectonicRegionType, ImmutableList<ImmutableList<GriddedRupture>>> trtRuptureLists;
	private IncrementalMagFreqDist refMFD;
	private TectonicRegionType[] sourceTRTs;
	private int[] sourceGridIndexes;
	
	private void setAll(GriddedRegion gridReg, LocationList locs,
			EnumMap<TectonicRegionType, ? extends List<? extends List<GriddedRupture>>> trtRuptureLists) {
		Preconditions.checkNotNull(locs);
		if (gridReg != null)
			Preconditions.checkState(locs.size() == gridReg.getNodeCount(),
					"Location list has %s locations, gridded region has %s", locs.size(), gridReg.getNodeCount());
		int sourceCount = 0;
		for (TectonicRegionType trt : trtRuptureLists.keySet())
			for (List<GriddedRupture> ruptures : trtRuptureLists.get(trt))
				if (!ruptures.isEmpty())
					sourceCount++;
		
		TectonicRegionType[] sourceTRTs = new TectonicRegionType[sourceCount];
		int[] sourceGridIndexes = new int[sourceCount];
		
		EnumMap<TectonicRegionType, ImmutableList<ImmutableList<GriddedRupture>>> trtRuptureListsOut = new EnumMap<>(TectonicRegionType.class);
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		boolean magsTenthAligned = true;
		int numRups = 0;
		int sourceIndex = 0;
		for (TectonicRegionType trt : TectonicRegionType.values()) {
			List<? extends List<GriddedRupture>> ruptureLists = trtRuptureLists.get(trt);
			if (ruptureLists == null)
				continue;
			Preconditions.checkState(ruptureLists.size() == locs.size());
			ImmutableList.Builder<ImmutableList<GriddedRupture>> ruptureListsBuilder = ImmutableList.builder();
			for (int gridIndex=0; gridIndex<locs.size(); gridIndex++) {
				List<GriddedRupture> ruptures = ruptureLists.get(gridIndex);
				if (ruptures == null || ruptures.isEmpty()) {
					ruptureListsBuilder.add(ImmutableList.of());
				} else {
					for (GriddedRupture rup : ruptures) {
						numRups++;
						minMag = Math.min(minMag, rup.magnitude);
						maxMag = Math.max(maxMag, rup.magnitude);
						// detect the case where ruptures are directly on the tenths (e.g., 5.0, 5.1)
						magsTenthAligned &= (float)(rup.magnitude*10d) == (float)Math.floor(rup.magnitude*10d);
					}
					
					ruptureListsBuilder.add(ImmutableList.copyOf(ruptures));
					sourceTRTs[sourceIndex] = trt;
					sourceGridIndexes[sourceIndex] = gridIndex;
					sourceIndex++;
				}
			}
		}
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
		this.locs = locs;
		this.gridReg = gridReg;
		this.sourceTRTs = sourceTRTs;
		this.sourceGridIndexes = sourceGridIndexes;
	}

	@Override
	public String getName() {
		return "Grid Source List";
	}

	@Override
	public AveragingAccumulator<GridSourceProvider> averagingAccumulator() {
		// TODO averaging
		return null;
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
	public Set<TectonicRegionType> getTectonicRegionTypes() {
		return trtRuptureLists.keySet();
	}

	@Override
	public int getNumSources() {
		return sourceGridIndexes.length;
	}
	
	public int locationIndexForSourceIndex(int sourceIndex) {
		return sourceGridIndexes[sourceIndex];
	}
	
	public TectonicRegionType tectonicRegionTypeForSourceIndex(int sourceIndex) {
		return sourceTRTs[sourceIndex];
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
	public ProbEqkSource getSource(int sourceIndex, double duration, DoubleBinaryOperator aftershockFilter,
			BackgroundRupType bgRupType) {
		return getSource(tectonicRegionTypeForSourceIndex(sourceIndex), locationIndexForSourceIndex(sourceIndex),
				duration, aftershockFilter, bgRupType);
	}

	@Override
	public ProbEqkSource getSource(TectonicRegionType tectonicRegionType, int gridIndex, double duration,
			DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType) {
		return new GriddedRuptureSource(getLocation(gridIndex), getRuptures(tectonicRegionType, gridIndex),
				duration, aftershockFilter, bgRupType, TectonicRegionType.ACTIVE_SHALLOW);
	}

	@Override
	public ProbEqkSource getSourceSubSeisOnFault(TectonicRegionType tectonicRegionType, int gridIndex, double duration, DoubleBinaryOperator aftershockFilter,
			BackgroundRupType bgRupType) {
		return new GriddedRuptureSource(getLocation(gridIndex), getRupturesSubSeisOnFault(tectonicRegionType, gridIndex),
				duration, aftershockFilter, bgRupType, TectonicRegionType.ACTIVE_SHALLOW);
	}

	@Override
	public ProbEqkSource getSourceUnassociated(TectonicRegionType tectonicRegionType, int gridIndex, double duration, DoubleBinaryOperator aftershockFilter,
			BackgroundRupType bgRupType) {
		return new GriddedRuptureSource(getLocation(gridIndex), getRupturesUnassociated(tectonicRegionType, gridIndex),
				duration, aftershockFilter, bgRupType, TectonicRegionType.ACTIVE_SHALLOW);
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
		IncrementalMagFreqDist mfd;
		if (Double.isFinite(minMag)) {
			int minIndex = refMFD.getClosestXIndex(minMag);
			mfd = new IncrementalMagFreqDist(refMFD.getX(minIndex), refMFD.size()-minIndex, refMFD.getDelta());
			// reset minMag to be the bin edge
			minMag = mfd.getMinX() - 0.5*mfd.getDelta();
		} else {
			mfd = refMFD.deepClone();
		}
		int maxIndexNonZero = 0;
		for (GriddedRupture rup : getRuptures(tectonicRegionType, gridIndex)) {
			if (rup.magnitude >= minMag && rup.rate >= 0d) {
				int index = mfd.getClosestXIndex(rup.magnitude);
				mfd.add(index, rup.rate);
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
	
	private static List<Range<Double>> SS_RANGES = List.of(
			Range.closedOpen(-180d, -135d),
			Range.open(-45d, 45d),
			Range.openClosed(135d, 180d));
	private static Range<Double> REV_RANGE = Range.closed(45d, 135d);
	private static Range<Double> NORM_RANGE = Range.closed(-135d, -45d);

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
					if (range.contains(rup.rake)) {
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
					modRupBuilder.add(rup.getNewRate(rup.rake*valuesArray[i]));
				modRupListBuilder.add(modRupBuilder.build());
			}
		}
		trtRuptureLists.put(tectonicRegionType, modRupListBuilder.build());
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
	
	public void setArhiveRounding(boolean round) {
		this.round = true;
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		if (gridReg != null) {
			// write the gridded region
			FileBackedModule.initEntry(zout, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
			Feature regFeature = gridReg.toFeature();
			OutputStreamWriter writer = new OutputStreamWriter(zout);
			Feature.write(regFeature, writer);
			writer.flush();
			zout.flush();
			zout.closeEntry();
		}
		
		// write grid locations
		CSVFile<String> gridCSV = new CSVFile<>(true);
		gridCSV.addLine("Grid Index", "Latitude", "Longitude");
		for (int i=0; i<getNumLocations(); i++) {
			Location loc = getLocation(i);
			gridCSV.addLine(i+"", getFixedPrecision(loc.lat, locRoundScale), getFixedPrecision(loc.lon, locRoundScale));
		}
		CSV_BackedModule.writeToArchive(gridCSV, zout, entryPrefix, ARCHIVE_GRID_LOCS_FILE_NAME);
		
		// write gridded rupture list
		// use CSVWriter for efficiency
		FileBackedModule.initEntry(zout, entryPrefix, ARCHIVE_GRID_SOURCES_FILE_NAME);
		CSVWriter rupCSV = new CSVWriter(zout, false);
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
		header.add("Tectonic Regime");
		int maxNumAssoc = 0;
		for (TectonicRegionType trt : getTectonicRegionTypes())
			for (int i=0; i<gridReg.getNodeCount(); i++)
				for (GriddedRupture rup : getRuptures(trt, i))
					if (rup.associatedSections != null)
						maxNumAssoc = Integer.max(maxNumAssoc, rup.associatedSections.length);
		if (maxNumAssoc > 0) {
			for (int i=0; i<maxNumAssoc; i++) {
				header.add("Associated Section Index "+(i+1));
				header.add("Fraction Associated "+(i+1));
			}
		}
		rupCSV.write(header);
		for (int i=0; i<gridReg.getNodeCount(); i++) { 
			for (TectonicRegionType trt : getTectonicRegionTypes()) {
				for (GriddedRupture rup : getRuptures(trt, i)) {
					List<String> line = new ArrayList<>();
					line.add(i+"");
					line.add(getFixedPrecision(rup.magnitude, magRoundScale));
					line.add(getSigFigs(rup.rate, rateRoundSigFigs));
					line.add(getSigFigs(rup.rake, mechRoundSigFigs));
					line.add(getSigFigs(rup.dip, mechRoundSigFigs));
					if (rup.strikeRange != null)
						line.add(rangeToString(rup.strikeRange));
					else
						line.add(getSigFigs(rup.strike, mechRoundSigFigs));
					line.add(getSigFigs(rup.upperDepth, depthRoundSigFigs));
					line.add(getSigFigs(rup.lowerDepth, depthRoundSigFigs));
					line.add(getSigFigs(rup.length, lenRoundSigFigs));
					line.add(rup.tectonicRegionType.name());
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
		zout.closeEntry();
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

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// load gridded region (if supplied)
		GriddedRegion gridReg = null;
		if (FileBackedModule.hasEntry(zip, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)) {
			BufferedInputStream regionIS = FileBackedModule.getInputStream(zip, entryPrefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
			InputStreamReader regionReader = new InputStreamReader(regionIS);
			Feature regFeature = Feature.read(regionReader);
			gridReg = GriddedRegion.fromFeature(regFeature);
		}
		
		// load grid location CSV
		CSVFile<String> gridCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ARCHIVE_GRID_LOCS_FILE_NAME);
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
		LocationList locs;
		if (gridReg == null) {
			// use the CSV file directly
			locs = fileLocs;
		} else {
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
			locs = gridReg.getNodeList();
		}
		
		// load ruptures themselves
		CSVReader rupSectsCSV = CSV_BackedModule.loadLargeFileFromArchive(zip, entryPrefix, ARCHIVE_GRID_SOURCES_FILE_NAME);
		rupSectsCSV.read(); // skip header row
		EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = new EnumMap<>(TectonicRegionType.class);
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
			TectonicRegionType tectonicRegionType = TectonicRegionType.valueOf(row.get(col++));
			int colsLeft = row.columns() - (col+1);
			int[] associatedSections = null;
			double[] associatedSectionFracts = null;
			if (colsLeft > 0) {
				Preconditions.checkState(colsLeft % 2 == 0,
						"Have %s columns left for associations, which is not divisible by 2; expected pairs of id, fract");
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
						double fract = row.getDouble(col++);
						Preconditions.checkState(fract >= 0d && fract <= 1d, "Bad associated fraction=%s", fract);
						associatedSections[i] = sectID;
						associatedSectionFracts[i] = fract;
					}
				}
			}
			GriddedRupture rup = new GriddedRupture(gridIndex, locs.get(gridIndex), mag, rate, rake, dip,
					strike, strikeRange, upperDepth, lowerDepth, length, tectonicRegionType,
					associatedSections, associatedSectionFracts);
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
		setAll(gridReg, locs, trtRuptureLists);
	}
	
	/**
	 * Gridded rupture representation.
	 * 
	 * Note that hashCode() and equals() do not consider the rate, and can thus be used to find identical ruptures
	 * when combining multiple models (e.g., averaging or adding).
	 */
	public static class GriddedRupture {
		// LOCATION
		public final int gridIndex;
		public final Location location;
		// MAGNITUDE
		public final double magnitude;
		// RATE
		public final double rate;
		// FOCAL MECHANISM
		public final double rake;
		public final double dip;
		public final double strike;
		public final Range<Double> strikeRange;
		// FINITE PROPERTIES
		public final double upperDepth;
		public final double lowerDepth;
		public final double length;
		// TECTONIC REGIME
		public final TectonicRegionType tectonicRegionType;
		// ASSOCIATIONS
		public final int[] associatedSections;
		public final double[] associatedSectionFracts;
		
		public GriddedRupture(int gridIndex, Location location, double magnitude, double rate, double rake, double dip,
				double strike, Range<Double> strikeRange, double upperDepth, double lowerDepth, double length,
				TectonicRegionType tectonicRegionType, int[] associatedSections, double[] associatedSectionFracts) {
			super();
			this.gridIndex = gridIndex;
			this.location = location;
			this.magnitude = magnitude;
			this.rate = rate;
			this.rake = rake;
			this.dip = dip;
			this.strike = strike;
			this.strikeRange = strikeRange;
			this.upperDepth = upperDepth;
			this.lowerDepth = lowerDepth;
			this.length = length;
			this.tectonicRegionType = tectonicRegionType;
			this.associatedSections = associatedSections;
			this.associatedSectionFracts = associatedSectionFracts;
		}
		
		public GriddedRupture getNewRate(double modRate) {
			return new GriddedRupture(gridIndex, location, magnitude, modRate,
					rake, dip, strike, strikeRange, upperDepth, lowerDepth,
					length, tectonicRegionType, associatedSections, associatedSectionFracts);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(associatedSectionFracts);
			result = prime * result + Arrays.hashCode(associatedSections);
			result = prime * result + Objects.hash(dip, gridIndex, length, location, lowerDepth, magnitude, rake,
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
			GriddedRupture other = (GriddedRupture) obj;
			return Arrays.equals(associatedSectionFracts, other.associatedSectionFracts)
					&& Arrays.equals(associatedSections, other.associatedSections)
					&& Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
					&& gridIndex == other.gridIndex
					&& Double.doubleToLongBits(length) == Double.doubleToLongBits(other.length)
					&& Objects.equals(location, other.location)
					&& Double.doubleToLongBits(lowerDepth) == Double.doubleToLongBits(other.lowerDepth)
					&& Double.doubleToLongBits(magnitude) == Double.doubleToLongBits(other.magnitude)
					&& Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
					&& Double.doubleToLongBits(strike) == Double.doubleToLongBits(other.strike)
					&& Objects.equals(strikeRange, other.strikeRange) && tectonicRegionType == other.tectonicRegionType
					&& Double.doubleToLongBits(upperDepth) == Double.doubleToLongBits(other.upperDepth);
		}
	}
	
	public static class GriddedRuptureSource extends ProbEqkSource {
		
		private PointSurface sourceSurf;
		private Location gridLoc;
		private List<ProbEqkRupture> ruptures;
		
		public GriddedRuptureSource(Location gridLoc, List<GriddedRupture> gridRups, double duration,
				DoubleBinaryOperator aftershockFilter, BackgroundRupType bgRupType, TectonicRegionType tectonicRegionType) {
			this.gridLoc = gridLoc;
			this.sourceSurf = new PointSurface(gridLoc);
			PointSurfaceBuilder surfBuilder = new PointSurfaceBuilder(gridLoc);
			ruptures = new ArrayList<>(gridRups.size());
			for (GriddedRupture rup : gridRups) {
				surfBuilder.magnitude(rup.magnitude);
				surfBuilder.dip(rup.dip);
				if (Double.isFinite(rup.strike)) {
					surfBuilder.strike(rup.strike);
				} else if (rup.strikeRange != null) {
					surfBuilder.strikeRange(rup.strikeRange);
				} else {
					surfBuilder.strike(Double.NaN);
				}
				surfBuilder.upperDepth(rup.upperDepth);
				surfBuilder.lowerDepth(rup.lowerDepth);
				surfBuilder.length(rup.length);
				RuptureSurface[] surfs = surfBuilder.build(bgRupType);
				double rate = rup.rate;
				if (aftershockFilter != null)
					rate = aftershockFilter.applyAsDouble(rup.magnitude, rup.rate);
				if (rate == 0d)
					continue;
				double rateEach = surfs.length == 1 ? rate : rate/(double)surfs.length;
				double probEach = 1 - Math.exp(-rateEach * duration);
				for (RuptureSurface surf : surfs) {
					if (surf instanceof FiniteApproxPointSurface)
						// TODO: hack to get nshmp corrected rJB until we revamp the framework
						((FiniteApproxPointSurface)surf).setDistCorrMagAndType(rup.magnitude, null);
					ruptures.add(new ProbEqkRupture(rup.magnitude, rup.rake, probEach, surf, null));
				}
			}
			this.setTectonicRegionType(tectonicRegionType);
		}

		@Override
		public LocationList getAllSourceLocs() {
			LocationList locList = new LocationList();
			locList.add(gridLoc);
			return locList;
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return sourceSurf;
		}

		@Override
		public double getMinDistance(Site site) {
			return LocationUtils.horzDistanceFast(site.getLocation(), gridLoc);
		}

		@Override
		public int getNumRuptures() {
			return ruptures.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return ruptures.get(nRupture);
		}
		
	}

}
