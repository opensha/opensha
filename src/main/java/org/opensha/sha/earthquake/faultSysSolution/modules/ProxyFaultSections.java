package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionBranchAverager;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

/**
 * This class discretized fault polygons into proxy fault sections spanning the polygon and generally following the
 * strike of (and keeping other properties from) the proxy fault.
 */
public class ProxyFaultSections implements ArchivableModule, BranchAverageableModule<ProxyFaultSections> {
	
	public static final String PROXY_SECTS_FILE_NAME = "proxy_fault_sections.geojson";
	public static final String PROXY_RUP_SECTS_FILE_NAME = "proxy_indices.csv";
	
	private List<? extends FaultSection> proxySects;
	private Map<Integer, List<List<Integer>>> proxyRupSectIndices;
	
	private static final int TRACE_BUF_LENGTHS_ALONG_STRIKE_DEFAULT = 2;
	private static final int TRACE_BUF_LENGTHS_FAULT_NORMAL_DEFAULT = 5;
	private static final double MIN_FRACT_TRACE_LEN_DEFAULT = 0.25;
	
	public static ProxyFaultSections build(FaultSystemRupSet rupSet, int numProxySectsPerPoly) {
		return build(rupSet, numProxySectsPerPoly, MIN_FRACT_TRACE_LEN_DEFAULT,
				TRACE_BUF_LENGTHS_ALONG_STRIKE_DEFAULT, TRACE_BUF_LENGTHS_FAULT_NORMAL_DEFAULT);
	}
	
	public static ProxyFaultSections build(FaultSystemRupSet rupSet, int numProxySectsPerPoly,
			double minFractTraceLen, int traceBufLengthsAlongStrike, int traceBufLengthsFaultNormal) {
		List<FaultSection> allProxySects = new ArrayList<>();
		
		Preconditions.checkState(numProxySectsPerPoly > 1);
		Preconditions.checkState(minFractTraceLen > 0d);
		Preconditions.checkState(traceBufLengthsAlongStrike > 0);
		Preconditions.checkState(traceBufLengthsFaultNormal > 0);
		
		Map<Integer, List<FaultSection>> subProxySects = new HashMap<>();
		
		DiscretizedFunc improvementWorthItFunc = new ArbitrarilyDiscretizedFunc();
		if (minFractTraceLen < 0.5)
			improvementWorthItFunc.set(0.5, 0.05);
		if (minFractTraceLen < 0.75)
			improvementWorthItFunc.set(0.75, 0.10);
		if (minFractTraceLen < 0.9)
			improvementWorthItFunc.set(0.9, 0.11);
		if (minFractTraceLen < 0.95)
			improvementWorthItFunc.set(0.95, 0.12);
		if (minFractTraceLen < 1d)
			improvementWorthItFunc.set(1d, 0.13);
		if (improvementWorthItFunc.size() == 0)
			improvementWorthItFunc = null;
		
		HashSet<Integer> possibleProxyRups = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			if (sect.isProxyFault() && sect.getZonePolygon() != null) {
				possibleProxyRups.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
				Region poly = sect.getZonePolygon();
				FaultTrace trace = sect.getFaultTrace();
				
				double traceLen = trace.getTraceLength();
				LocationVector traceVect = LocationUtils.vector(trace.first(), trace.last());
				
				double maxTraceOrWidth = traceLen;
				if (sect.getAveDip() < 90d && sect.getOrigDownDipWidth() > 0d)
					// max of trace length or horizontal widthe of surface projected dipping rupture
					maxTraceOrWidth = Math.max(traceLen, Math.sqrt(Math.pow(sect.getOrigDownDipWidth(), 2d))
							- Math.pow(sect.getOrigAveUpperDepth() - sect.getAveLowerDepth(), 2d));
				
				double maxDistFaultNorm = maxTraceOrWidth*traceBufLengthsFaultNormal;
				
				double leftAz = traceVect.getAzimuth() - 90d;
				double rightAz = traceVect.getAzimuth() + 90d;
				
				
				double maxDistForLeft = findFurtherstViableRelocatedTrace(trace, poly, leftAz,
						maxDistFaultNorm, minFractTraceLen, improvementWorthItFunc);
				double maxDistForRight = findFurtherstViableRelocatedTrace(trace, poly, rightAz,
						maxDistFaultNorm, minFractTraceLen, improvementWorthItFunc);
				Preconditions.checkState(maxDistForLeft > 0d);
				Preconditions.checkState(maxDistForRight > 0d);
				
				EvenlyDiscretizedFunc distBins = new EvenlyDiscretizedFunc(-maxDistForLeft, maxDistForRight, numProxySectsPerPoly);
				List<FaultTrace> proxyTraces = new ArrayList<>();
				for (int p=0; p<numProxySectsPerPoly; p++) {
					double dist = distBins.getX(p);
					FaultTrace relocatedTrace = relocate(trace, new LocationVector(rightAz, dist, 0d));
					// extend it in the trace direction in each direction
					FaultTrace extended = new FaultTrace(null);
					extended.add(LocationUtils.location(relocatedTrace.first(),
							traceVect.getAzimuthRad()+Math.PI, traceLen*traceBufLengthsAlongStrike));
					extended.addAll(relocatedTrace);
					extended.add(LocationUtils.location(relocatedTrace.last(),
							traceVect.getAzimuthRad(), traceLen*traceBufLengthsAlongStrike));
					
					FaultTrace proxyTrace = new FaultTrace(null);
					for (int i=0; i<extended.size(); i++) {
						Location loc = extended.get(i);
						
						if (i > 0) {
							// add any points between the previous one and this one that cross the boundary
							Location prev = extended.get(i-1);
							FaultTrace seg = new FaultTrace(null);
							seg.add(prev);
							seg.add(loc);
							int numSamples = Integer.max(100, (int)(seg.getTraceLength()*10d));
							FaultTrace resampled = FaultUtils.resampleTrace(seg, numSamples);
							for (int j=1; j<resampled.size(); j++) {
								Location sample1 = resampled.get(j-1);
								Location sample2 = resampled.get(j);
								if (poly.contains(sample1) != poly.contains(sample2)) {
									// crosses a boundary
									// lets really narrow in on the location
									FaultTrace seg2 = new FaultTrace(null);
									seg2.add(sample1);
									seg2.add(sample2);
									FaultTrace resampled2 = FaultUtils.resampleTrace(seg2,
											Integer.max(10, (int)(seg2.getTraceLength()*10d)));
									boolean found = false;
									for (int k=1; k<resampled2.size(); k++) {
										Location resample1 = resampled2.get(k-1);
										Location resample2 = resampled2.get(k);
										if (poly.contains(resample1) != poly.contains(resample2)) {
											if (poly.contains(resample1))
												proxyTrace.add(resample1);
											else
												proxyTrace.add(resample2);
											found = true;
											break;
										}
									}
									Preconditions.checkState(found);
								}
							}
						}
						
						if (poly.contains(loc))
							proxyTrace.add(loc);
					}
					Preconditions.checkState(proxyTrace.size() > 1);
					proxyTraces.add(proxyTrace);
				}
				
				// build proxy sects using those traces
				Preconditions.checkState(proxyTraces.size() == numProxySectsPerPoly);
				GeoJSONFaultSection geoSect = sect instanceof GeoJSONFaultSection ? (GeoJSONFaultSection)sect : new GeoJSONFaultSection(sect);
				Feature origFeature = geoSect.toFeature();
				FeatureProperties proxyProps = new FeatureProperties(origFeature.properties);
				proxyProps.remove(GeoJSONFaultSection.FAULT_ID);
				proxyProps.remove(GeoJSONFaultSection.FAULT_NAME);
				proxyProps.remove(GeoJSONFaultSection.PARENT_ID);
				proxyProps.remove(GeoJSONFaultSection.PARENT_NAME);
				double origSlip = sect.getOrigAveSlipRate();
				double proxySlip = origSlip / (double)numProxySectsPerPoly;
				double origSlipSD = sect.getOrigSlipRateStdDev();
				double proxySlipSD = origSlip > 0d && origSlipSD > 0d ? origSlipSD / (double)numProxySectsPerPoly : origSlipSD;
				proxyProps.set(GeoJSONFaultSection.SLIP_RATE, proxySlip);
				proxyProps.set(GeoJSONFaultSection.SLIP_STD_DEV, proxySlipSD);
				
				List<FaultSection> myProxySects = new ArrayList<>(numProxySectsPerPoly);
				subProxySects.put(sect.getSectionId(), myProxySects);
				for (int p=0; p<numProxySectsPerPoly; p++) {
					FaultTrace proxyTrace = proxyTraces.get(p);
					Geometry geom = new Geometry.LineString(proxyTrace);
					int id = allProxySects.size();
					String name = sect.getSectionName()+", Proxy "+p;
					FeatureProperties myProxyProps = new FeatureProperties(proxyProps);
					myProxyProps.set(GeoJSONFaultSection.FAULT_ID, id);
					myProxyProps.set(GeoJSONFaultSection.FAULT_NAME, name);
					myProxyProps.set(GeoJSONFaultSection.PARENT_ID, sect.getSectionId());
					myProxyProps.set(GeoJSONFaultSection.PARENT_NAME, sect.getSectionName());
					Feature proxyFeature = new Feature(id, geom, myProxyProps);
					FaultSection proxySect = GeoJSONFaultSection.fromFeature(proxyFeature);
					allProxySects.add(proxySect);
					myProxySects.add(proxySect);
				}
			}
		}
		
		// now build ruptures
		Map<Integer, List<List<Integer>>> proxyRupSectIndices = new HashMap<>();
		for (int rupIndex : possibleProxyRups) {
			boolean allOnProxies = true;
			List<Integer> rupSects = rupSet.getSectionsIndicesForRup(rupIndex);
			for (int sectIndex : rupSects) {
				if (!subProxySects.containsKey(sectIndex)) {
					allOnProxies = false;
					break;
				}
			}
			if (allOnProxies) {
				// build proxy ruptures for this
				List<List<Integer>> proxyIndexesList = new ArrayList<>();
				for (int p=0; p<numProxySectsPerPoly; p++) {
					List<Integer> proxyIndexes = new ArrayList<>(rupSects.size());
					for (int sectIndex : rupSects) {
						FaultSection proxyFault = subProxySects.get(sectIndex).get(p);
						proxyIndexes.add(proxyFault.getSectionId());
					}
					proxyIndexesList.add(proxyIndexes);
				}
				proxyRupSectIndices.put(rupIndex, proxyIndexesList);
			}
		}
		
		return new ProxyFaultSections(allProxySects, proxyRupSectIndices);
	}
	
	private static FaultTrace relocate(FaultTrace trace, LocationVector vect) {
		FaultTrace ret = new FaultTrace(null);
		for (Location loc : trace)
			ret.add(LocationUtils.location(loc, vect));
		return ret;
	}
	
	private static double fractInside(FaultTrace trace, Region poly) {
		FaultTrace resampled = FaultUtils.resampleTrace(trace, 100);
		int numInside = 0;
		for (Location loc : resampled)
			if (poly.contains(loc))
				numInside++;
		return (double)numInside/(double)resampled.size();
	}
	
	private static double findFurtherstViableRelocatedTrace(FaultTrace trace, Region poly,
			double azimuth, double maxDistAway, double minFract, DiscretizedFunc improvementWorthItFunc) {
		// first find the maximum distance to any containment using a coarse discretization
		EvenlyDiscretizedFunc distFractFunc = new EvenlyDiscretizedFunc(0d, maxDistAway, 100);
		
		double maxCoarse = 0d;
		for (int i=0; i<distFractFunc.size(); i++) {
			double dist = distFractFunc.getX(i);
			LocationVector vector = new LocationVector(azimuth, dist, 0d);
			FaultTrace relocated = relocate(trace, vector);
			double fract = fractInside(relocated, poly);
			if (fract > 0d)
				maxCoarse = dist;
			distFractFunc.set(i, fract);
		}
		
		// now resample just up to that distance
		distFractFunc = new EvenlyDiscretizedFunc(0d, maxCoarse+1d, 100);
		for (int i=0; i<distFractFunc.size(); i++) {
			double dist = distFractFunc.getX(i);
			LocationVector vector = new LocationVector(azimuth, dist, 0d);
			FaultTrace relocated = relocate(trace, vector);
			double fract = fractInside(relocated, poly);
			distFractFunc.set(i, fract);
		}
		
		double fractForRet = -1d;
		double retDist = -1d;
		for (int i=distFractFunc.size(); --i>=0;) {
			double dist = distFractFunc.getX(i);
			double fract = distFractFunc.getY(i);
			if (fract >= minFract) {
				retDist = dist;
				fractForRet = fract;
				break;
			}
		}
		
		Preconditions.checkState(fractForRet > 0d, "Bad fract for %s: %s\nfunc: %s", trace.getName(), fractForRet, distFractFunc);
		if (improvementWorthItFunc != null) {
			// see if we can improve
			double origDist = retDist;
			for (Point2D pt : improvementWorthItFunc) {
				double targetFract = pt.getX();
				double testDist = -1d;
				double fractForTest = -1d;
				for (int i=distFractFunc.size(); --i>=0;) {
					double dist = distFractFunc.getX(i);
					double fract = distFractFunc.getY(i);
					if (fract >= targetFract) {
						testDist = dist;
						fractForTest = fract;
						break;
					}
				}
				if (testDist > 0d) {
					double deltaFract = (origDist - testDist)/origDist;
					double maxDelta = pt.getY();
					if (deltaFract <= maxDelta) {
						retDist = testDist;
						fractForRet = fractForTest;
					}
				} else {
					break;
				}
			}
		}
		return retDist;
	}
	
	// for deserialization
	private ProxyFaultSections() {};

	private ProxyFaultSections(List<? extends FaultSection> proxySects,
			Map<Integer, List<List<Integer>>> proxyRupSectIndices) {
		super();
		this.proxySects = proxySects;
		this.proxyRupSectIndices = proxyRupSectIndices;
	}

	@Override
	public String getName() {
		return "Proxy Fault Sections";
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		FileBackedModule.initEntry(zout, entryPrefix, PROXY_SECTS_FILE_NAME);
		OutputStreamWriter writer = new OutputStreamWriter(zout);
		GeoJSONFaultReader.writeFaultSections(writer, proxySects);
		writer.flush();
		zout.flush();
		zout.closeEntry();
		
		FileBackedModule.initEntry(zout, entryPrefix, PROXY_RUP_SECTS_FILE_NAME);
		CSVWriter csvWriter = new CSVWriter(zout, false);
		buildRupSectsCSV(proxyRupSectIndices, csvWriter);
		csvWriter.flush();
		zout.closeEntry();
	}
	
	public static void buildRupSectsCSV(Map<Integer, List<List<Integer>>> proxyRupSectIndices, CSVWriter writer) throws IOException {
		int maxNumSects = 0;
		for (List<List<Integer>> list : proxyRupSectIndices.values())
			for (List<Integer> ids : list)
				maxNumSects = Integer.max(maxNumSects, ids.size());

		List<String> header = new ArrayList<>(List.of("Rupture Index", "Num Proxy Representations",
				"Proxy Representation Index", "Num Sections", "Proxy Sect # 1"));

		for (int s = 1; s < maxNumSects; s++)
			header.add("# " + (s + 1));

		writer.write(header);
		
		List<Integer> rupIndexes = new ArrayList<>(proxyRupSectIndices.keySet());
		Collections.sort(rupIndexes);

		for (int r : rupIndexes) {
			List<List<Integer>> sectIDsList = proxyRupSectIndices.get(r);
			int numProxies = sectIDsList.size();
			
			for (int p=0; p<numProxies; p++) {
				List<Integer> sectIDs = sectIDsList.get(p);
				List<String> line = new ArrayList<>(4 + sectIDs.size());

				line.add(r + "");
				line.add(numProxies + "");
				line.add(p + "");
				line.add(sectIDs.size() + "");
				for (int s : sectIDs)
					line.add(s + "");
				writer.write(line);
			}
		}
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// fault sections
		List<GeoJSONFaultSection> sections = GeoJSONFaultReader.readFaultSections(
				new InputStreamReader(FileBackedModule.getInputStream(zip, entryPrefix, PROXY_SECTS_FILE_NAME)));
		for (int s=0; s<sections.size(); s++) {
			FaultSection sect = sections.get(s);
			Preconditions.checkState(sect.getSectionId() == s,
					"Fault sections must be provided in order starting with ID=0");
			Preconditions.checkState(sect.getParentSectionId() >= 0,
					"All proxy faults should have their parent ID set (to the corresponding subsection)");
		}
		proxySects = sections;
		
		CSVReader rupSectsCSV = CSV_BackedModule.loadLargeFileFromArchive(zip, entryPrefix, PROXY_RUP_SECTS_FILE_NAME);
		proxyRupSectIndices = loadRupSectsCSV(rupSectsCSV, sections.size());
	}
	
	public static Map<Integer, List<List<Integer>>> loadRupSectsCSV(CSVReader rupSectsCSV, int numSections) {
		Map<Integer, List<List<Integer>>> proxyRupSectIndices = new HashMap<>();
		boolean shortSafe = numSections < Short.MAX_VALUE;
		rupSectsCSV.read(); // skip header row
		
		while (true) {
			CSVReader.Row csvRow = rupSectsCSV.read();
			if (csvRow == null)
				break;
			int rupIndex = csvRow.getInt(0);
			Preconditions.checkState(rupIndex >= 0,
					"Bad rupIndex=%s", rupIndex);
			int numProxies = csvRow.getInt(1);
			Preconditions.checkState(numProxies > 0,
					"Bad numProxies=%s for rupIndex=%s", numProxies, rupIndex);
			int proxyIndex = csvRow.getInt(2);
			Preconditions.checkState(proxyIndex >= 0 && proxyIndex < numProxies,
					"Bad proxyIndex=%s for rupIndex=%s with numProxies=%s", proxyIndex, rupIndex, numProxies);
			int numRupSects = csvRow.getInt(3);
			Preconditions.checkState(numRupSects > 0,
					"Bad numRupSects=%s for rupIndex=%s", numRupSects, rupIndex);
			Preconditions.checkState(csvRow.getLine().size() == numRupSects+4,
					"Expected numRupSects+4=%s columns for rupIndex=%s proxyIndex=%s, have %s columns",
					numRupSects+4, rupIndex, proxyIndex, csvRow.getLine().size());
			List<List<Integer>> sectIDsList = proxyRupSectIndices.get(rupIndex);
			if (sectIDsList == null) {
				sectIDsList = new ArrayList<>(numProxies);
				for (int p=0; p<numProxies; p++)
					sectIDsList.add(null);
				proxyRupSectIndices.put(rupIndex, sectIDsList);
			} else {
				Preconditions.checkState(sectIDsList.size() == numProxies);
				Preconditions.checkState(sectIDsList.get(proxyIndex) == null);
			}
			List<Integer> sectIDs = new ArrayList<>(numRupSects);
			sectIDsList.set(proxyIndex, sectIDs);
			for (int i=0; i<numRupSects; i++)
				sectIDs.add(csvRow.getInt(i+4));
		}
		
		Preconditions.checkState(!proxyRupSectIndices.isEmpty(), "No proxy ruptures included?");
		
		// make sure they're all complete
		for (int rupIndex : proxyRupSectIndices.keySet()) {
			List<List<Integer>> sectIDsList = proxyRupSectIndices.get(rupIndex);
			for (int p=0; p<sectIDsList.size(); p++) {
				Preconditions.checkNotNull(sectIDsList.get(p),
						"Proxy %s/%s never filled in for rupIndex=%s", p, sectIDsList.size(), rupIndex);
			}
		}

		try {
			rupSectsCSV.close();
		}catch(IOException x) {
			throw new RuntimeException(x);
		}

		return proxyRupSectIndices;
	}

	@Override
	public AveragingAccumulator<ProxyFaultSections> averagingAccumulator() {
		return new Averager();
	}
	
	private static class Averager implements AveragingAccumulator<ProxyFaultSections> {
		
		private FaultSectionBranchAverager sectAverager;
		private Map<Integer, List<List<Integer>>> proxyRupSectIndices;

		@Override
		public Class<ProxyFaultSections> getType() {
			return ProxyFaultSections.class;
		}

		@Override
		public void process(ProxyFaultSections module, double relWeight) {
			if (sectAverager == null) {
				// first time
				sectAverager = new FaultSectionBranchAverager(module.proxySects);
				proxyRupSectIndices = module.proxyRupSectIndices;
			} else {
				Preconditions.checkState(proxyRupSectIndices.size() == module.proxyRupSectIndices.size());
				for (int rupIndex : proxyRupSectIndices.keySet()) {
					List<List<Integer>> mine = proxyRupSectIndices.get(rupIndex);
					List<List<Integer>> theirs = module.proxyRupSectIndices.get(rupIndex);
					Preconditions.checkNotNull(theirs);
					Preconditions.checkState(mine.size() == theirs.size());
					// make sure the first and last are the same
					Preconditions.checkState(mine.get(0).equals(theirs.get(0)));
					Preconditions.checkState(mine.get(mine.size()-1).equals(theirs.get(mine.size()-1)));
				}
			}
			sectAverager.addWeighted(module.proxySects, relWeight);
		}

		@Override
		public ProxyFaultSections getAverage() {
			return new ProxyFaultSections(sectAverager.buildAverageSects(), proxyRupSectIndices);
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		File solFile = new File("C:\\Users\\kmilner\\Downloads\\"
				+ "results_PRVI_FM_INITIAL_branch_averaged.zip");
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//				+ "2024_05_21-prvi25_crustal_branches-GEOLOGIC/results_PRVI_FM_INITIAL_branch_averaged.zip"));
		FaultSystemRupSet rupSet = sol.getRupSet();
		ProxyFaultSections proxySects = build(rupSet, 10);
		rupSet.addModule(proxySects);
		
		File modSolFile = new File(solFile.getParentFile(), solFile.getName().substring(0, solFile.getName().indexOf(".zip"))+"_mod.zip");
		sol.write(modSolFile);
		proxySects = FaultSystemRupSet.load(modSolFile).requireModule(ProxyFaultSections.class);
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
//		List<FaultSection> filteredSects = new ArrayList<>();
//		for (FaultSection sect : subSects)
//			if (sect.getName().startsWith("Anegada Passage"))
//				filteredSects.add(sect);
//		subSects = filteredSects;
		
		GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
		
		List<LocationList> lines = new ArrayList<>();
		for (FaultSection sect : proxySects.proxySects)
			lines.add(sect.getFaultTrace());
		mapMaker.plotLines(lines, Color.BLUE, 1f);
		
//		mapMaker.plot(new File("/tmp"), "proxy_finite_sect_test", " ");
		mapMaker.plot(new File("C:\\Users\\kmilner\\Downloads"), "proxy_finite_sect_test", " ");
	}

}
