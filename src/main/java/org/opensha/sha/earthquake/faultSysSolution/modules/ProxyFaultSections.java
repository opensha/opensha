package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
	private static final double MIN_FRACT_TRACE_LEN_DEFAULT = 0.1;
	
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
		
		HashSet<Integer> possibleProxyRups = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			if (sect.isProxyFault() && sect.getZonePolygon() != null) {
				possibleProxyRups.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
				Region poly = sect.getZonePolygon();
				FaultTrace trace = sect.getFaultTrace();
				
				// we'll first draw a line in the trace direction, but extended a bunch so that (hopefully) the entire
				// polygon will be not before or after the extended line (i.e., fully to the left and right)
				double traceLen = trace.getTraceLength();
				LocationVector traceVect = LocationUtils.vector(trace.first(), trace.last());
				Location bufferEnd = LocationUtils.location(trace.last(), traceVect.getAzimuthRad(), traceLen*traceBufLengthsAlongStrike);
				Location bufferStart = LocationUtils.location(trace.first(), traceVect.getAzimuthRad()+Math.PI, traceLen*traceBufLengthsAlongStrike);
				FaultTrace bufferTrace = new FaultTrace(null);
				bufferTrace.add(bufferStart);
				bufferTrace.add(bufferEnd);
				int numSamplesAlong = Integer.max(100, (int)(bufferTrace.getTraceLength()*(2*traceBufLengthsAlongStrike+1)));
				FaultTrace resampledBufferTrace = FaultUtils.resampleTrace(bufferTrace, numSamplesAlong-1);
				Preconditions.checkState(resampledBufferTrace.size() == numSamplesAlong);
				
				// now we'll draw perpendicular lines
				double maxTraceOrWidth = traceLen;
				if (sect.getAveDip() < 90d && sect.getOrigDownDipWidth() > 0d)
					// max of trace length or horizontal widthe of surface projected dipping rupture
					maxTraceOrWidth = Math.max(traceLen, Math.sqrt(Math.pow(sect.getOrigDownDipWidth(), 2d))
							- Math.pow(sect.getOrigAveUpperDepth() - sect.getAveLowerDepth(), 2d));
				int numSamplesAcross = Integer.max(101, (int)(maxTraceOrWidth*2d*traceBufLengthsFaultNormal));
				// we really want this to be odd so that one lies on the original trace (if straight)
				if (numSamplesAcross % 2 != 0)
					numSamplesAcross++;
				List<FaultTrace> faultNormSamples = new ArrayList<>(numSamplesAlong);
				List<boolean[]> faultNormSamplesInsides = new ArrayList<>(numSamplesAlong);
				double leftAz = traceVect.getAzimuthRad() - 0.5*Math.PI;
				double rightAz = traceVect.getAzimuthRad() + 0.5*Math.PI;
				int[] colFirstInsides = new int[numSamplesAlong];
				int[] colLastInsides = new int[numSamplesAlong];
				int firstRowInside = -1;
				int lastRowInside = -1;
				int firstColInside = -1;
				int lastColInside = -1;
				for (int col=0; col<numSamplesAlong; col++) {
					Location traceLoc = resampledBufferTrace.get(col);
					Location sampleStart = LocationUtils.location(traceLoc, leftAz, maxTraceOrWidth*traceBufLengthsFaultNormal);
					Location sampleEnd = LocationUtils.location(traceLoc, rightAz, maxTraceOrWidth*traceBufLengthsFaultNormal);
					FaultTrace sampleTrace = new FaultTrace(null);
					sampleTrace.add(sampleStart);
					sampleTrace.add(sampleEnd);
					FaultTrace resampledTrace = FaultUtils.resampleTrace(sampleTrace, numSamplesAcross-1);
					Preconditions.checkState(resampledTrace.size() == numSamplesAcross);
					boolean[] insides = new boolean[numSamplesAcross];
					int firstInsideIndex = -1;
					int lastInsideIndex = -1;
					for (int row=0; row<numSamplesAcross; row++) {
						if (poly.contains(resampledTrace.get(row))) {
							insides[row] = true;
							if (firstInsideIndex < 0)
								firstInsideIndex = row;
							lastInsideIndex = row;
							
							if (firstRowInside < 0)
								firstRowInside = row;
							if (row > lastRowInside)
								lastRowInside = row;
							if (firstColInside < 0)
								firstColInside = col;
							lastColInside = col;
						}
					}
					colFirstInsides[col] = firstInsideIndex;
					colLastInsides[col] = lastInsideIndex;
					faultNormSamples.add(resampledTrace);
					faultNormSamplesInsides.add(insides);
				}

				Preconditions.checkState(lastRowInside > firstRowInside,
						"firstRowInside=%s and lastRowInside=%s", firstRowInside, lastRowInside);
				Preconditions.checkState(lastColInside > firstColInside,
						"firstColInside=%s and lastColInside=%s", firstColInside, lastColInside);
				
				// now build the proxy faults
				// first just build for each row, then we'll filter down
				List<FaultTrace> viableProxyTraces = new ArrayList<>();
				for (int row=firstRowInside; row<=lastRowInside; row++) {
					FaultTrace rowTrace = new FaultTrace(null);
					for (int col=firstColInside; col<=lastColInside; col++)
						if (faultNormSamplesInsides.get(col)[row])
							rowTrace.add(faultNormSamples.get(col).get(row));
					if (rowTrace.size() > 1 && rowTrace.getTraceLength() > traceLen*minFractTraceLen)
						viableProxyTraces.add(rowTrace);
				}
				Preconditions.checkState(viableProxyTraces.size() > 1, "Only found %s viable row traces?", viableProxyTraces.size());
				
				List<FaultTrace> proxyTraces;
//				if (viableProxyTraces.size() < numProxySectsPerPoly*5) {
				if (true) {
					// need to resample them
					double maxLen = 0d;
					for (FaultTrace proxy : viableProxyTraces)
						maxLen = Math.max(maxLen, proxy.getTraceLength());
					int numResamplesAlong = Integer.max(10, (int)maxLen);
					List<FaultTrace> resampledViableProxies = new ArrayList<>(viableProxyTraces.size());
					for (FaultTrace proxy : viableProxyTraces)
						resampledViableProxies.add(FaultUtils.resampleTrace(proxy, numResamplesAlong-1));
					List<FaultTrace> resampledNorms = new ArrayList<>(numResamplesAlong);
					for (int i=0; i<numResamplesAlong; i++) {
						FaultTrace norm = new FaultTrace(null);
						for (int j=0; j<resampledViableProxies.size(); j++)
							norm.add(resampledViableProxies.get(j).get(i));
						resampledNorms.add(FaultUtils.resampleTrace(norm, numProxySectsPerPoly-1));
					}
					proxyTraces = new ArrayList<>(numProxySectsPerPoly);
					for (int p=0; p<numProxySectsPerPoly; p++) {
						FaultTrace proxyTrace = new FaultTrace(null);
						for (int i=0; i<numResamplesAlong; i++)
							proxyTrace.add(resampledNorms.get(i).get(p));
						proxyTraces.add(proxyTrace);
					}
				} else {
					// just grab from the set we have
					proxyTraces = new ArrayList<>(numProxySectsPerPoly);
					for (int p=0; p<numProxySectsPerPoly; p++) {
						double fract = (double)p/(double)(numProxySectsPerPoly-1);
						int index = (int)(fract*(viableProxyTraces.size()-1) + 0.5);
						Preconditions.checkState(index < viableProxyTraces.size());
						proxyTraces.add(viableProxyTraces.get(index));
					}
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
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_05_21-prvi25_crustal_branches-GEOLOGIC/results_PRVI_FM_INITIAL_branch_averaged.zip"));
		FaultSystemRupSet rupSet = sol.getRupSet();
		ProxyFaultSections proxySects = build(rupSet, 10);
		
		GeographicMapMaker mapMaker = new GeographicMapMaker(rupSet.getFaultSectionDataList());
		
		List<LocationList> lines = new ArrayList<>();
		for (FaultSection sect : proxySects.proxySects)
			lines.add(sect.getFaultTrace());
		mapMaker.plotLines(lines, Color.BLACK, 1f);
		
		mapMaker.plot(new File("/tmp"), "proxy_finite_sect_test", " ");
	}

}
