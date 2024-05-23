package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

/**
 * This class discretized fault polygons into proxy fault sections spanning the polygon and generally following the
 * strike of (and keeping other properties from) the proxy fault.
 */
public class ProxyFaultSections implements ArchivableModule {
	
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
				// TODO, shouldn't need to actually keep the resampled trace
				int numSamplesAlong = Integer.max(500, (int)(traceLen*traceBufLengthsAlongStrike*10));
				
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
					FaultTrace resampled = FaultUtils.resampleTrace(extended, numSamplesAlong);
					int firstIndexInside = -1;
					int lastIndexInside = -1;
					for (int i=0; i<resampled.size(); i++) {
						Location loc = resampled.get(i);
						if (poly.contains(loc)) {
							if (firstIndexInside < 0)
								firstIndexInside = i;
							lastIndexInside = i;
						}
					}
					Preconditions.checkState(lastIndexInside > firstIndexInside);
					FaultTrace proxyTrace = new FaultTrace(null);
					for (int i=firstIndexInside; i<=lastIndexInside; i++)
						proxyTrace.add(resampled.get(i));
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
					proxyProps.set(GeoJSONFaultSection.FAULT_ID, id);
					proxyProps.set(GeoJSONFaultSection.FAULT_NAME, name);
					proxyProps.set(GeoJSONFaultSection.PARENT_ID, sect.getSectionId());
					proxyProps.set(GeoJSONFaultSection.PARENT_NAME, sect.getSectionName());
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
		EvenlyDiscretizedFunc distFractFunc = new EvenlyDiscretizedFunc(0d, maxDistAway, 500);
		
		for (int i=0; i<distFractFunc.size(); i++) {
			double dist = distFractFunc.getX(i);
			LocationVector vector = new LocationVector(azimuth, dist, 0d);
			FaultTrace relocated = relocate(trace, vector);
			distFractFunc.set(i, fractInside(relocated, poly));
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
		// TODO Auto-generated method stub

	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// TODO Auto-generated method stub

	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("C:\\Users\\kmilner\\Downloads\\"
				+ "results_PRVI_FM_INITIAL_branch_averaged.zip"));
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
//				+ "2024_05_21-prvi25_crustal_branches-GEOLOGIC/results_PRVI_FM_INITIAL_branch_averaged.zip"));
		FaultSystemRupSet rupSet = sol.getRupSet();
		ProxyFaultSections proxySects = build(rupSet, 10);
		
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
		mapMaker.plotLines(lines, Color.BLACK, 1f);
		
//		mapMaker.plot(new File("/tmp"), "proxy_finite_sect_test", " ");
		mapMaker.plot(new File("C:\\Users\\kmilner\\Downloads"), "proxy_finite_sect_test", " ");
	}

}
