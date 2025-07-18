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
import java.util.Set;
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
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.commons.util.modules.helpers.LargeCSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionBranchAverager;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionPolygonBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.PRVI25_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTree;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

/**
 * This module takes proxy faults with polygons attached, and re-discretizes them into multiple parallel proxy instances
 * that span the width of the polygon. This is used to generate finite fault rupture realizations that span the entire
 * polygon (rather than just using the proxy at the middle of the polygon) for hazard calculations.
 * 
 * Only ruptures that exclusively use proxy faults will be turned into proxy ruptures.
 */
public class ProxyFaultSectionInstances implements ArchivableModule, BranchAverageableModule<ProxyFaultSectionInstances> {
	
	public static final String PROXY_SECTS_FILE_NAME = "proxy_fault_section_instances.geojson";
	public static final String PROXY_RUP_SECTS_FILE_NAME = "proxy_rup_sect_indices.csv";
	
	private List<? extends FaultSection> proxySects;
	private Map<Integer, List<List<Integer>>> proxyRupSectIndices;
	
	private static final int TRACE_BUF_LENGTHS_ALONG_STRIKE_DEFAULT = 5;
	private static final int TRACE_BUF_LENGTHS_FAULT_NORMAL_DEFAULT = 10;
	private static final double MIN_FRACT_TRACE_LEN_DEFAULT = 0.25;
	private static final boolean SHEAR_TO_CONNECT_DEFAULT = true;
	
	private static final boolean D = false;
	
	/**
	 * 
	 * @param rupSet input fault system rupture set. fault sections will be split up so long as isProxyFault() is true
	 * and getZonePolygon() != null.
	 * @param minNumProxySectsPerPoly the minimum number or proxy instances (per polygon)
	 * @param maxProxySpacing the maximum spacing between proxy instances
	 * @return proxy instances module
	 */
	public static ProxyFaultSectionInstances build(FaultSystemRupSet rupSet, int minNumProxySectsPerPoly,
			double maxProxySpacing) {
		return build(rupSet, minNumProxySectsPerPoly, maxProxySpacing, MIN_FRACT_TRACE_LEN_DEFAULT,
				TRACE_BUF_LENGTHS_ALONG_STRIKE_DEFAULT, TRACE_BUF_LENGTHS_FAULT_NORMAL_DEFAULT,
				SHEAR_TO_CONNECT_DEFAULT);
	}
	
	/**
	 * 
	 * @param rupSet input fault system rupture set. fault sections will be split up so long as isProxyFault() is true
	 * and getZonePolygon() != null.
	 * @param minNumProxySectsPerPoly the minimum number or proxy instances (per polygon)
	 * @param maxProxySpacing the maximum spacing between proxy instances
	 * @param minFractTraceLen the shortest fraction of the original trace length that a proxy instance can be
	 * @param traceBufLengthsAlongStrike how many trace lengths along strike before and after the original proxy trace
	 * should we try extending the traces?
	 * @param traceBufLengthsFaultNormal how many trace lengths should we look in the fault-normal direction to find the
	 * width of the polygon?
	 * @param shearToConnect if true, fualt sections will be sheared such that they always connect removing any discontinuities
	 * in the resulting ruptures.
	 * @return proxy instances module
	 */
	public static ProxyFaultSectionInstances build(FaultSystemRupSet rupSet, int minNumProxySectsPerPoly,
			double maxProxySpacing, double minFractTraceLen, int traceBufLengthsAlongStrike,
			int traceBufLengthsFaultNormal, boolean shearToConnect) {
		List<FaultSection> allProxySects = new ArrayList<>();

		Preconditions.checkArgument(minNumProxySectsPerPoly > 1);
		Preconditions.checkArgument(maxProxySpacing > 0d);
		Preconditions.checkArgument(minFractTraceLen > 0d && minFractTraceLen <= 1d);
		Preconditions.checkArgument(traceBufLengthsAlongStrike > 0);
		Preconditions.checkArgument(traceBufLengthsFaultNormal > 0);
		
		DiscretizedFunc improvementWorthItFunc = new ArbitrarilyDiscretizedFunc();
		if (minFractTraceLen < 0.5)
			improvementWorthItFunc.set(0.5, 0.06);
		if (minFractTraceLen < 0.75)
			improvementWorthItFunc.set(0.75, 0.065);
		if (minFractTraceLen < 0.9)
			improvementWorthItFunc.set(0.9, 0.07);
		if (minFractTraceLen < 0.95)
			improvementWorthItFunc.set(0.95, 0.075);
		if (minFractTraceLen < 1d)
			improvementWorthItFunc.set(1d, 0.08);
		if (improvementWorthItFunc.size() == 0)
			improvementWorthItFunc = null;
		
		List<Integer> proxySectIDs = new ArrayList<>();
		Map<Integer, double[]> proxyMaxRelocationDists = new HashMap<>();
		Map<Integer, Integer> numProxySectsPerPolys = new HashMap<>();
		HashSet<Integer> possibleProxyRups = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			if (sect.isProxyFault() && sect.getZonePolygon() != null) {
				if (D) System.out.println("Figuring out maximum horizontal distances for "
						+sect.getSectionId()+". "+sect.getSectionName());
				proxySectIDs.add(sect.getSectionId());
				possibleProxyRups.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
				Region poly = sect.getZonePolygon();
				FaultTrace trace = sect.getFaultTrace();
				
				double traceLen = trace.getTraceLength();
				
				double maxTraceOrWidth = traceLen;
				if (sect.getAveDip() < 90d && sect.getOrigDownDipWidth() > 0d)
					// max of trace length or horizontal widthe of surface projected dipping rupture
					maxTraceOrWidth = Math.max(traceLen, Math.sqrt(Math.pow(sect.getOrigDownDipWidth(), 2d))
							- Math.pow(sect.getOrigAveUpperDepth() - sect.getAveLowerDepth(), 2d));
				
				double maxDistFaultNorm = maxTraceOrWidth*traceBufLengthsFaultNormal;
				
				double rightAz = sect.getDipDirection();
				if (sect instanceof GeoJSONFaultSection)
					rightAz = ((GeoJSONFaultSection)sect).getProperties().getDouble(SubSectionPolygonBuilder.SECT_POLY_DIRECTION_PROP_NAME, rightAz);
				if (D) System.out.println("\trightAz="+(float)rightAz+" (dipDir="+sect.getDipDirection()+")");
				double leftAz = rightAz + 180;
				
				double maxDistForLeft = findFurtherstViableRelocatedTrace(trace, poly, leftAz,
						maxDistFaultNorm, minFractTraceLen, improvementWorthItFunc);
				double maxDistForRight = findFurtherstViableRelocatedTrace(trace, poly, rightAz,
						maxDistFaultNorm, minFractTraceLen, improvementWorthItFunc);
				double width = maxDistForLeft+maxDistForRight;
				int numProxySectsPerPoly = Integer.max(minNumProxySectsPerPoly,
						(int)(Math.ceil(width/maxProxySpacing)+1d));
				if (D) System.out.println("\tMax dists: left="+(float)maxDistForLeft+", right="+(float)maxDistForRight
						+", width="+(float)(maxDistForLeft+maxDistForRight)+", initialNum="+numProxySectsPerPoly);
				Preconditions.checkState(maxDistForLeft > 0d);
				Preconditions.checkState(maxDistForRight > 0d);
				
				proxyMaxRelocationDists.put(sect.getSectionId(), new double[] {maxDistForLeft, maxDistForRight});
				numProxySectsPerPolys.put(sect.getSectionId(), numProxySectsPerPoly);
			}
		}
		Preconditions.checkState(!proxyMaxRelocationDists.isEmpty(), "No proxy faults found with polygons attached");
		
		// list of ruptures that are exclusively on proxy faults
		List<Integer> fullyProxyRuptures = new ArrayList<>();
		for (int rupIndex : possibleProxyRups) {
			boolean allOnProxies = true;
			List<Integer> rupSects = rupSet.getSectionsIndicesForRup(rupIndex);
			for (int sectIndex : rupSects) {
				if (!proxyMaxRelocationDists.containsKey(sectIndex)) {
					allOnProxies = false;
					break;
				}
			}
			if (allOnProxies)
				fullyProxyRuptures.add(rupIndex);
		}
		Preconditions.checkState(!fullyProxyRuptures.isEmpty(), "No ruptures exclusively on proxy faults?");
		
		// now do the accounting for the case of corupture of proxy sects that have different numbers of instances;
		// instance counts must be identical across all itnerconnected subsections, keep the largest number.
		boolean countsIncreased = true;
		while (countsIncreased) {
			countsIncreased = false;
			for (int rupIndex : fullyProxyRuptures) {
				List<Integer> rupSects = rupSet.getSectionsIndicesForRup(rupIndex);
				int maxNum = 0;
				int minNum = Integer.MAX_VALUE;
				for (int sectID : rupSects) {
					int myNum = numProxySectsPerPolys.get(sectID);
					maxNum = Integer.max(maxNum, myNum);
					minNum = Integer.min(minNum, myNum);
				}
				Preconditions.checkState(maxNum > 0);
				if (maxNum != minNum) {
					// need to increase
					countsIncreased = true;
					for (int sectID : rupSects)
						numProxySectsPerPolys.put(sectID, maxNum);
				}
			}
		}

		Map<Integer, List<FaultTrace>> subProxyExtendedTraces = new HashMap<>();
		Map<Integer, List<FaultTrace>> subProxyTraces = new HashMap<>();
		Map<Integer, List<FaultSection>> proxySectsByParent = new HashMap<>();
		Map<Integer, Double> proxySectsRightAzimuths = new HashMap<>();
		for (int sectID : proxySectIDs) {
			FaultSection sect = rupSet.getFaultSectionData(sectID);
			if (sect.getParentSectionId() >= 0) {
				if (!proxySectsByParent.containsKey(sect.getParentSectionId()))
					proxySectsByParent.put(sect.getParentSectionId(), new ArrayList<>());
				proxySectsByParent.get(sect.getParentSectionId()).add(sect);
			}
			double[] leftRightDists = proxyMaxRelocationDists.get(sectID);
			double maxDistForLeft = leftRightDists[0];
			double maxDistForRight = leftRightDists[1];
			int numProxySectsPerPoly = numProxySectsPerPolys.get(sectID);
			
			Region poly = sect.getZonePolygon();
			FaultTrace trace = sect.getFaultTrace();
			
			double traceLen = trace.getTraceLength();
			double rightAz = sect.getDipDirection();
			if (sect instanceof GeoJSONFaultSection)
				rightAz = ((GeoJSONFaultSection)sect).getProperties().getDouble(SubSectionPolygonBuilder.SECT_POLY_DIRECTION_PROP_NAME, rightAz);
			LocationVector traceVect = LocationUtils.vector(trace.first(), trace.last());
			proxySectsRightAzimuths.put(sect.getSectionId(), rightAz);
			
			EvenlyDiscretizedFunc distBins = new EvenlyDiscretizedFunc(-maxDistForLeft, maxDistForRight, numProxySectsPerPoly);
			Preconditions.checkState(distBins.size() == numProxySectsPerPoly);
			Preconditions.checkState((float)distBins.getX(0) == (float)-maxDistForLeft);
			Preconditions.checkState((float)distBins.getX(distBins.size()-1) == (float)maxDistForRight);
			
			double spacing = distBins.getX(1)-distBins.getX(0);
			
			if (D) System.out.println("Building "+numProxySectsPerPoly+" proxy instances for "
					+sectID+". "+sect.getSectionName()+"; spacing="+(float)spacing+" km");
			double spacing2 = (distBins.getX(distBins.size()-1) - distBins.getX(0))/(distBins.size()-1);
			Preconditions.checkState((float)spacing == (float)spacing2, "%s != %s", spacing, spacing2);
			Preconditions.checkState((float)spacing <= (float)maxProxySpacing,
					"Spacing=%s for %s with %s proxies exceeds the max of %s",
					(float)spacing, sect.getSectionName(), numProxySectsPerPoly, (float)maxProxySpacing);
			
			List<FaultTrace> extendedProxyTraces = new ArrayList<>();
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
				
//				if (D) System.out.println("\tBuilding proxy trace "+p+"/"+numProxySectsPerPoly
//						+" at dist="+(float)dist);
				
				FaultTrace proxyTrace = trimTraceToRegion(poly, extended);
				
				proxyTraces.add(proxyTrace);
				extendedProxyTraces.add(extended);
			}
			
			subProxyExtendedTraces.put(sect.getSectionId(), extendedProxyTraces);
			subProxyTraces.put(sect.getSectionId(), proxyTraces);
		}
		
		if (shearToConnect && !proxySectsByParent.isEmpty()) {
			// the length of this array is the number of iterations
			// the contents are the number of discretizations for that iteration
			// start out big and then reduce as we hone in
			// should always be odd in order to try the exact middle
			int[] shearIters = { 11, 5, 3};
			for (List<FaultSection> parentBundle : proxySectsByParent.values()) {
				if (parentBundle.size() == 1)
					continue;
				// make sure they have the same count
				int numProxySectsPerPoly = -1;
				for (FaultSection sect : parentBundle) {
					int myNum = numProxySectsPerPolys.get(sect.getSectionId());
					if (numProxySectsPerPoly < 0)
						numProxySectsPerPoly = myNum;
					else
						Preconditions.checkState(numProxySectsPerPoly == myNum);
				}
				
//				if (!parentBundle.get(0).getParentSectionName().equals("Anegada Passage SW PROXY"))
//					continue;
				
				if (D) System.out.println("Shearing "+parentBundle.get(0).getParentSectionName());
				for (int p=0; p<numProxySectsPerPoly; p++) {
//					if (p > 0)
//						break;
					
					for (int iter=0; iter<shearIters.length; iter++) {
						for (int b=0; b<parentBundle.size()-1; b++) {
							FaultSection sect1 = parentBundle.get(b);
							FaultSection sect2 = parentBundle.get(b+1);
							
							int sectID1 = sect1.getSectionId();
							int sectID2 = sect2.getSectionId();
							
							Region poly1 = sect1.getZonePolygon();
							Region poly2 = sect2.getZonePolygon();
							
							if (D) System.out.println("\tShearing proxy "+p+" sects "+b+" and "+(b+1)+" (iter="+iter+")");
							
							FaultTrace trimmedTrace1 = subProxyTraces.get(sectID1).get(p);
							FaultTrace extendedTrace1 = subProxyExtendedTraces.get(sectID1).get(p);
							if (b == 0)
								// don't let it extend beyond the start of the polygon; if we did, large shears can occur
								// that rotate it too much
								extendedTrace1 = trimTraceToRegion(poly1, extendedTrace1, true, false);
							Location sect1L1 = sect1.getFaultTrace().first();
							Location sect1L2 = sect1.getFaultTrace().last();
							double sect1End = LocationUtils.distanceToLineFast(sect1L1, sect1L2, trimmedTrace1.last());
							
							FaultTrace trimmedTrace2 = subProxyTraces.get(sectID2).get(p);
							FaultTrace extendedTrace2 = subProxyExtendedTraces.get(sectID2).get(p);
							if (b == parentBundle.size()-2)
								// don't let it extend beyond the end of the polygon; if we did, large shears can occur
								// that rotate it too much
								extendedTrace2 = trimTraceToRegion(poly2, extendedTrace2, false, true);
							Location sect2L1 = sect2.getFaultTrace().first();
							Location sect2L2 = sect2.getFaultTrace().last();
							double sect2Start = LocationUtils.distanceToLineFast(sect2L1, sect2L2, trimmedTrace2.first());
							
							if ((float)sect1End == (float)sect2Start)
								continue;
							
							// first, don't just split the difference because the extended trace could get cut off or extend
							// further after trimming; splitting the difference might not actually connect them
							double maxShear = Math.abs(sect1End - sect2Start);
							double avgRightAngle = FaultUtils.getAngleAverage(List.of(proxySectsRightAzimuths.get(sectID1),
									proxySectsRightAzimuths.get(sectID2)));
							
							double angle1, angle2;
							if (Math.abs(sect1End) > Math.abs(sect2Start)) {
								// 1 is further away than 2
								if (sect1End >= 0) {
									// 1 is on the right
									angle1 = avgRightAngle + 180; // bring 1 in to the left
									angle2 = avgRightAngle; // push 2 further to the right
								} else {
									// 1 is on the left
									angle1 = avgRightAngle; // bring 1 in to the right
									angle2 = avgRightAngle + 180; // push 2 further to the right
								}
							} else {
								// 2 is further away than 1
								if (sect2Start >= 0) {
									// 2 is on the right
									angle1 = avgRightAngle; // push 1 further to the right
									angle2 = avgRightAngle + 180; // bring 2 in to the left
								} else {
									// 2 is on the left
									angle1 = avgRightAngle + 180; // push 1 further to the left
									angle2 = avgRightAngle; // bring 2 in to the right
								}
							}
							EvenlyDiscretizedFunc shearTries = new EvenlyDiscretizedFunc(0d, maxShear, shearIters[iter]);
							
							FaultTrace closestTrimmedTrace1 = null;
							FaultTrace closestTrimmedTrace2 = null;
							FaultTrace closestExtendedTrace1 = null;
							FaultTrace closestExtendedTrace2 = null;
							int closestIndex = -1;
							double closestDist = Double.POSITIVE_INFINITY;
							
							for (int i=0; i<shearIters[iter]; i++) {
								double shearDist = shearTries.getX(i);
								
								FaultTrace shearedExtendedTrace1 = shearTrace(extendedTrace1,
										trimmedTrace1.first(), trimmedTrace1.last(), shearDist, angle1);
								FaultTrace shearedExtendedTrace2 = shearTrace(extendedTrace2,
										trimmedTrace2.last(), trimmedTrace2.first(), shearDist, angle2);
								FaultTrace shearedTrimmedTrace1;
								FaultTrace shearedTrimmedTrace2;
								try {
									shearedTrimmedTrace1 = trimTraceToRegion(poly1, shearedExtendedTrace1);
									shearedTrimmedTrace2 = trimTraceToRegion(poly2, shearedExtendedTrace2);
								} catch (Exception e) {
									if (D) System.err.println("A sheared trace doesn't intersect region");
									continue;
								}
								
								double dist = LocationUtils.horzDistanceFast(shearedTrimmedTrace1.last(), shearedTrimmedTrace2.first());
								
//								if (D && i == insideShearDiscr/2) {
//									System.out.println("Middle shear "+i+"/"+insideShearDiscr
//											+" results; shearDist="+(float)shearDist+", angle1="+(float)angle1
//											+", angle2="+(float)angle2+", trimmedDist="+(float)dist);
//								}
								
								if (dist < closestDist) {
									closestDist = dist;
									closestIndex = i;
									closestTrimmedTrace1 = shearedTrimmedTrace1;
									closestTrimmedTrace2 = shearedTrimmedTrace2;
									closestExtendedTrace1 = shearedExtendedTrace1;
									closestExtendedTrace2 = shearedExtendedTrace2;
								}
							}
							
							if (D) System.out.println("\t\tClosest distance after shearing was a distance of "+(float)closestDist
									+" for shearIndex="+closestIndex+"/"+shearIters[iter]+", shearDist="+(float)shearTries.getX(closestIndex));
							if (closestIndex > 0) { // >0 means we actually sheared
								subProxyTraces.get(sectID1).set(p, closestTrimmedTrace1);
								subProxyExtendedTraces.get(sectID1).set(p, closestExtendedTrace1);
								subProxyTraces.get(sectID2).set(p, closestTrimmedTrace2);
								subProxyExtendedTraces.get(sectID2).set(p, closestExtendedTrace2);
							}
						}
					}
				}
			}
		}
		
		// build proxy sects using those traces
		Map<Integer, List<FaultSection>> subProxySects = new HashMap<>();
		for (int sectID : proxySectIDs) {
			FaultSection sect = rupSet.getFaultSectionData(sectID);
			List<FaultTrace> proxyTraces = subProxyTraces.get(sectID);
			int numProxySectsPerPoly = numProxySectsPerPolys.get(sectID);
			
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
				// set the "parent" properties to point to the original proxy subsection
				myProxyProps.set(GeoJSONFaultSection.PARENT_ID, sect.getSectionId());
				myProxyProps.set(GeoJSONFaultSection.PARENT_NAME, sect.getSectionName());
				Feature proxyFeature = new Feature(id, geom, myProxyProps);
				FaultSection proxySect = GeoJSONFaultSection.fromFeature(proxyFeature);
				allProxySects.add(proxySect);
				myProxySects.add(proxySect);
			}
		}
		
		// now build ruptures
		Map<Integer, List<List<Integer>>> proxyRupSectIndices = new HashMap<>();
		for (int rupIndex : fullyProxyRuptures) {
			List<Integer> rupSects = rupSet.getSectionsIndicesForRup(rupIndex);
			int numProxySectsPerPoly = -1;
			for (int sectID : rupSects) {
				int myNum = numProxySectsPerPolys.get(sectID);
				if (numProxySectsPerPoly < 0)
					numProxySectsPerPoly = myNum;
				else
					Preconditions.checkState(numProxySectsPerPoly == myNum);
			}
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
		
		return new ProxyFaultSectionInstances(allProxySects, proxyRupSectIndices);
	}
	
	private static FaultTrace shearTrace(FaultTrace trace, Location anchor, Location reference, double distance, double azimuthDegrees) {
		double azimuth = Math.toRadians(azimuthDegrees);

		// Calculate new reference location
		Location newReference = LocationUtils.location(reference, azimuth, distance);

		// Compute shear factors
		double distAR = LocationUtils.horzDistance(anchor, reference);
		double azimuthAR = LocationUtils.azimuthRad(anchor, reference); // Use radians

		double distAR_new = LocationUtils.horzDistance(anchor, newReference);
		double azimuthAR_new = LocationUtils.azimuthRad(anchor, newReference); // Use radians

		double shearFactor = distAR_new / distAR;
		double azimuthDelta = azimuthAR_new - azimuthAR;

		// Transform other points
		FaultTrace shearedTrace = new FaultTrace(null);
		for (Location point : trace) {
			double distAP = LocationUtils.horzDistance(anchor, point);
			double azimuthAP = LocationUtils.azimuthRad(anchor, point); // Use radians

			// Adjust azimuth and distance
			double newDistAP = distAP * shearFactor;
			double newAzimuthAP = azimuthAP + azimuthDelta;

			// Compute new location
			Location newPoint = LocationUtils.location(anchor, newAzimuthAP, newDistAP);
			shearedTrace.add(newPoint);
		}
		return shearedTrace;
	}

	private static final double min_dist_to_resample = 0.1;
	
	private static FaultTrace trimTraceToRegion(Region poly, FaultTrace extended) {
		return trimTraceToRegion(poly, extended, true, true);
	}
	
	private static FaultTrace trimTraceToRegion(Region poly, FaultTrace extended, boolean trimBefore, boolean trimAfter) {
		FaultTrace proxyTrace = new FaultTrace(null);
		boolean polyContainedPrev = false;
		List<Location> origLocsBefore = new ArrayList<>();
		List<Location> origLocsAfter = new ArrayList<>();
//		System.out.println("Trimming "+extended);
		for (int i=0; i<extended.size(); i++) {
			Location loc = extended.get(i);
			
			boolean polyContainsLoc = poly.contains(loc);
			if (i > 0 && LocationUtils.horzDistanceFast(loc, extended.get(i-1)) > min_dist_to_resample) {
				// add any points between the previous one and this one that cross the boundary
				Location prev = extended.get(i-1);
				
				FaultTrace seg = new FaultTrace(null);
				seg.add(prev);
				seg.add(loc);
				Preconditions.checkState(!prev.equals(loc) && !LocationUtils.areSimilar(prev, loc),
						"Trace contains duplicates: %s == %s", prev, loc);
				int numSamples = Integer.max(100, (int)(seg.getTraceLength()*10d));
//				System.out.println("Resampling for i="+i+" with "+numSamples+" samples: "+seg);
				FaultTrace resampled = FaultUtils.resampleTrace(seg, numSamples);
				// sometimes the original location may not pass poly.contains(loc), but the resampled one
				// will due to precision issues. if either are true, we should include the location
				boolean polyContainsResampledLoc = poly.contains(resampled.last());
				if (polyContainsLoc != polyContainsResampledLoc) {
					// one of them is inside, force this point to be included
					polyContainsLoc = true;
				}
				boolean[] resampledInsides = new boolean[resampled.size()];
				for (int j=0; j<resampledInsides.length; j++) {
					if (j == 0)
						resampledInsides[j] = polyContainedPrev;
					else if (j == resampledInsides.length-1)
						resampledInsides[j] = polyContainsLoc;
					else
						resampledInsides[j] = poly.contains(resampled.get(j));
				}
				for (int j=1; j<resampled.size(); j++) {
					if (resampledInsides[j-1] != resampledInsides[j]) {
						// crosses a boundary
						// lets really narrow in on the location
						FaultTrace seg2 = new FaultTrace(null);
						seg2.add(resampled.get(j-1));
						seg2.add(resampled.get(j));
						FaultTrace resampled2 = FaultUtils.resampleTrace(seg2,
								Integer.max(10, (int)(seg2.getTraceLength()*10d)));
						boolean[] resampledInsides2 = new boolean[resampled2.size()];
						for (int k=0; k<resampledInsides2.length; k++) {
							if (k == 0)
								resampledInsides2[k] = resampledInsides[j-1];
							else if (k == resampledInsides2.length-1)
								resampledInsides2[k] = resampledInsides[j];
							else
								resampledInsides2[k] = poly.contains(resampled2.get(k));
						}
						boolean found = false;
						for (int k=1; k<resampled2.size(); k++) {
							if (resampledInsides2[k-1] != resampledInsides2[k]) {
								Location newLoc;
								if (resampledInsides2[k-1])
									newLoc = resampled2.get(k-1);
								else
									newLoc = resampled2.get(k);
								proxyTrace.add(newLoc);
								found = true;
								break;
							}
						}
						Preconditions.checkState(found);
					}
				}
			}
			
			if (polyContainsLoc) {
				proxyTrace.add(loc);
				origLocsAfter.clear();
			} else if (proxyTrace.isEmpty()) {
				origLocsBefore.add(loc);
			} else {
				origLocsAfter.add(loc);
			}
			polyContainedPrev = polyContainsLoc;
		}
		if (!trimBefore && !origLocsBefore.isEmpty())
			// add the original locations from before the polygon back in
			proxyTrace.addAll(0, origLocsBefore);
		if (!trimAfter && !origLocsAfter.isEmpty())
			// add the original locations from after the polygon back in
			proxyTrace.addAll(origLocsAfter);
		Preconditions.checkState(proxyTrace.size() > 1, "Only found %s locations within poly?", proxyTrace.size());
		return proxyTrace;
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
		
		if (D) System.out.println("\tmaxCoarse="+maxCoarse+" for maxDistAway="+(float)maxDistAway+" and azimuth="+(float)azimuth);
		// now resample just up to that distance
		distFractFunc = new EvenlyDiscretizedFunc(0d, maxCoarse+2*distFractFunc.getDelta(), 100);
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
	private ProxyFaultSectionInstances() {};

	private ProxyFaultSectionInstances(List<? extends FaultSection> proxySects,
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
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(
				FileBackedModule.initOutputStream(output, entryPrefix, PROXY_SECTS_FILE_NAME));
		GeoJSONFaultReader.writeFaultSections(writer, proxySects);
		writer.flush();
		output.closeEntry();
		
		CSVWriter csvWriter = new CSVWriter(FileBackedModule.initOutputStream(output, entryPrefix, PROXY_RUP_SECTS_FILE_NAME), false);
		buildRupSectsCSV(proxyRupSectIndices, csvWriter);
		csvWriter.flush();
		output.closeEntry();
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
		
		writer.flush();
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		// fault sections
		List<GeoJSONFaultSection> sections = GeoJSONFaultReader.readFaultSections(
				new InputStreamReader(FileBackedModule.getInputStream(input, entryPrefix, PROXY_SECTS_FILE_NAME)));
		for (int s=0; s<sections.size(); s++) {
			FaultSection sect = sections.get(s);
			Preconditions.checkState(sect.getSectionId() == s,
					"Fault sections must be provided in order starting with ID=0");
			Preconditions.checkState(sect.getParentSectionId() >= 0,
					"All proxy faults should have their parent ID set (to the corresponding subsection)");
		}
		proxySects = sections;
		
		CSVReader rupSectsCSV = LargeCSV_BackedModule.loadFromArchive(input, entryPrefix, PROXY_RUP_SECTS_FILE_NAME);
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
			for (int i=0; i<numRupSects; i++)
				sectIDs.add(csvRow.getInt(i+4));
			if (shortSafe)
				sectIDs = new FaultSystemRupSet.ShortListWrapper(sectIDs);
			else
				sectIDs = new FaultSystemRupSet.IntListWrapper(sectIDs);
			sectIDsList.set(proxyIndex, sectIDs);
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
		} catch(IOException x) {
			throw new RuntimeException(x);
		}

		return proxyRupSectIndices;
	}
	
	public List<? extends FaultSection> getProxySects() {
		return proxySects;
	}
	
	public Set<Integer> getProxyRupIndexes() {
		return proxyRupSectIndices.keySet();
	}
	
	public boolean rupHasProxies(int rupIndex) {
		return proxyRupSectIndices.containsKey(rupIndex);
	}
	
	public List<List<Integer>> getRupProxySectIndexes(int rupIndex) {
		return proxyRupSectIndices.get(rupIndex);
	}
	
	public List<List<FaultSection>> getRupProxySects(int rupIndex) {
		List<List<Integer>> sectIDsList = proxyRupSectIndices.get(rupIndex);
		List<List<FaultSection>> ret = new ArrayList<>(sectIDsList.size());
		for (List<Integer> sectIDs : sectIDsList) {
			List<FaultSection> sects = new ArrayList<>();
			for (int sectID : sectIDs)
				sects.add(proxySects.get(sectID));
			ret.add(sects);
		}
		return ret;
	}

	@Override
	public AveragingAccumulator<ProxyFaultSectionInstances> averagingAccumulator() {
		return new Averager();
	}
	
	public FaultSystemRupSet getSplitRuptureSet(FaultSystemRupSet origRupSet) {
		Map<Integer, List<FaultSection>> proxySectsMap = new HashMap<>();
		for (FaultSection proxySect : proxySects) {
			int origID = proxySect.getParentSectionId();
			List<FaultSection> sectProxies = proxySectsMap.get(origID);
			if (sectProxies == null) {
				sectProxies = new ArrayList<>();
				proxySectsMap.put(origID, sectProxies);
			}
			sectProxies.add(proxySect);
		}
		
		Map<Integer, Integer> proxyIDs_toNew = new HashMap<>(proxySects.size());
		Map<Integer, List<Integer>> sectIDs_oldToNew = new HashMap<>();
		List<FaultSection> modSects = new ArrayList<>();
		for (int s=0; s<origRupSet.getNumSections(); s++) {
			FaultSection origSect = origRupSet.getFaultSectionData(s);
			int origID = origSect.getSectionId();
			List<Integer> newIDs = new ArrayList<>();
			if (proxySectsMap.containsKey(origID)) {
				for (FaultSection proxySect : proxySectsMap.get(origID)) {
					FaultSection copy = proxySect.clone();
					copy.setParentSectionId(origSect.getParentSectionId());
					copy.setParentSectionName(origSect.getParentSectionName());
					int modID = modSects.size();
					newIDs.add(modID);
					copy.setSectionId(modID);
					modSects.add(copy);
					proxyIDs_toNew.put(proxySect.getSectionId(), modID);
				}
			} else {
				FaultSection copy = origSect.clone();
				int modID = modSects.size();
				newIDs.add(modID);
				copy.setSectionId(modID);
				modSects.add(copy);
			}
			sectIDs_oldToNew.put(origID, newIDs);
		}
		
		int rupIndex = 0;
		Map<Integer, List<Integer>> rupIDs_oldToNew = new HashMap<>();
		Map<Integer, Integer> rupIDs_newToOld = new HashMap<>();
		List<List<Integer>> modSectionForRups = new ArrayList<>();
		for (int origID=0; origID<origRupSet.getNumRuptures(); origID++) {
			if (rupHasProxies(origID)) {
				List<List<FaultSection>> proxies = getRupProxySects(origID);
				List<Integer> newIDs = new ArrayList<>(proxies.size());
				for (int i=0; i<proxies.size(); i++) {
					rupIDs_newToOld.put(rupIndex, origID);
					newIDs.add(rupIndex++);
					List<FaultSection> rupProxies = proxies.get(i);
					List<Integer> sectsForRups = new ArrayList<>(rupProxies.size());
					for (FaultSection proxy : rupProxies)
						sectsForRups.add(proxyIDs_toNew.get(proxy.getSectionId()));
					modSectionForRups.add(sectsForRups);
				}
				rupIDs_oldToNew.put(origID, newIDs);
			} else {
				rupIDs_newToOld.put(rupIndex, origID);
				rupIDs_oldToNew.put(origID, List.of(rupIndex++));
				List<Integer> origSectsForRups = origRupSet.getSectionsIndicesForRup(origID);
				List<Integer> sectsForRups = new ArrayList<>();
				for (int sectID : origSectsForRups) {
					List<Integer> newSectIDs = sectIDs_oldToNew.get(sectID);
					Preconditions.checkState(newSectIDs.size() == 1, "Rupture not identified as proxy rup but uses proxy"
							+ " sects; we do not yet support ruptures on proxy and non-proxy faults. Could add support "
							+ "in the future by keeping proxy surfaces around in this case and just using those for "
							+ "these ruptures.");
					sectsForRups.add(newSectIDs.get(0));
				}
				modSectionForRups.add(sectsForRups);
			}
		}
		
		double[] rupLengths = origRupSet.getLengthForAllRups();
		double[] modMags = new double[rupIndex];
		double[] modRakes = new double[rupIndex];
		double[] modRupAreas = new double[rupIndex];
		double[] modRupLengths = rupLengths == null ? null : new double[rupIndex];
		for (rupIndex=0; rupIndex<rupIDs_newToOld.size(); rupIndex++) {
			int origID = rupIDs_newToOld.get(rupIndex);
			modMags[rupIndex] = origRupSet.getMagForRup(origID);
			modRakes[rupIndex] = origRupSet.getAveRakeForRup(origID);
			modRupAreas[rupIndex] = origRupSet.getAreaForRup(origID);
			if (modRupLengths != null)
				modRupLengths[rupIndex] = rupLengths[origID];
		}
		
		FaultSystemRupSet modRupSet = new FaultSystemRupSet(modSects, modSectionForRups,
				modMags, modRakes, modRupAreas, modRupLengths);
		
		System.out.println("Split from "+origRupSet.getNumSections()+" sects to "+modRupSet.getNumSections());
		System.out.println("Split from "+origRupSet.getNumRuptures()+" rups to "+modRupSet.getNumRuptures());
		
		// add mappings module
		RuptureSetSplitMappings mappings = new RuptureSetSplitMappings(sectIDs_oldToNew, rupIDs_oldToNew);
		modRupSet.addModule(mappings);
		
		// now copy over any modules we can
		for (OpenSHA_Module module : origRupSet.getModulesAssignableTo(SplittableRuptureModule.class, true)) {
			OpenSHA_Module modModule;
			try {
				modModule = ((SplittableRuptureModule<?>)module).getForSplitRuptureSet(
						modRupSet, mappings);
			} catch (Exception e) {
				System.out.println("Couldn't split module "+module.getName()+", skipping: "+e.getMessage());
				continue;
			}
			if (modModule != null)
				modRupSet.addModule(modModule);
		}
		
		return modRupSet;
	}
	
	private static class Averager implements AveragingAccumulator<ProxyFaultSectionInstances> {
		
		private FaultSectionBranchAverager sectAverager;
		private Map<Integer, List<List<Integer>>> proxyRupSectIndices;

		@Override
		public Class<ProxyFaultSectionInstances> getType() {
			return ProxyFaultSectionInstances.class;
		}

		@Override
		public void process(ProxyFaultSectionInstances module, double relWeight) {
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
		public ProxyFaultSectionInstances getAverage() {
			return new ProxyFaultSectionInstances(sectAverager.buildAverageSects(), proxyRupSectIndices);
		}
		
	}
	
//	public static void main(String[] args) throws IOException {
//		FaultTrace testOrigTrace = new FaultTrace(null);
//		testOrigTrace.add(new Location(0.3, 0.1));
//		testOrigTrace.add(new Location(0.3, 0.2));
////		testOrigTrace.add(new Location(0.6, 0.5));
//		testOrigTrace.add(new Location(0.7, 0.8));
//		testOrigTrace.add(new Location(0.7, 0.9));
//		GeographicMapMaker mapMaker = new GeographicMapMaker(new Region(new Location(0d, 0d), new Location(1d, 1d)));
//		mapMaker.setWriteGeoJSON(false);
//		mapMaker.setWritePDFs(false);
//		
//		List<LocationList> lines = new ArrayList<>();
//		List<PlotCurveCharacterstics> chars = new ArrayList<>();
//		lines.add(testOrigTrace);
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, PlotSymbol.FILLED_CIRCLE, 5f, Color.BLACK));
//		lines.add(shearTrace(testOrigTrace, testOrigTrace.first(), testOrigTrace.last(), 10d, 0d));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, PlotSymbol.CIRCLE, 5f, Color.BLUE));
//		lines.add(shearTrace(testOrigTrace, testOrigTrace.first(), testOrigTrace.last(), 10d, 180d));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, PlotSymbol.CIRCLE, 5f, Color.RED));
//		lines.add(shearTrace(testOrigTrace, testOrigTrace.last(), testOrigTrace.first(), 10d, 0d));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, PlotSymbol.CIRCLE, 5f, Color.GREEN));
//		for (LocationList trace : lines)
//			System.out.println(trace);
////		mapMaker.plotLines(lines, Color.BLUE, 3f);
//		mapMaker.plotLines(lines, chars);
//		
//		mapMaker.plot(new File("/tmp"), "proxy_shear_test", " ");
//		
////		File solFile = new File("C:\\Users\\kmilner\\Downloads\\"
////				+ "results_PRVI_FM_INITIAL_branch_averaged.zip");
////		File solFile = new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
////				+ "2024_05_21-prvi25_crustal_branches-GEOLOGIC/results_PRVI_FM_INITIAL_branch_averaged.zip");
////		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
////		FaultSystemRupSet rupSet = sol.getRupSet();
//		
////		FaultSystemSolution sol = null;
////		File solFile = null;
////		FaultSystemRupSet rupSet = new PRVI25_InvConfigFactory().buildRuptureSet(
////				PRVI25_LogicTreeBranch.DEFAULT_CRUSTAL_ON_FAULT, FaultSysTools.defaultNumThreads());
////		ProxyFaultSectionInstances proxySects = build(rupSet, 5, 5d);
////		rupSet.addModule(proxySects);
////		
////		if (sol != null) {
////			File modSolFile = new File(solFile.getParentFile(), solFile.getName().substring(0, solFile.getName().indexOf(".zip"))+"_mod.zip");
////			sol.write(modSolFile);
////			proxySects = FaultSystemRupSet.load(modSolFile).requireModule(ProxyFaultSectionInstances.class);
////		}
////		
////		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
////		
//////		List<FaultSection> filteredSects = new ArrayList<>();
//////		for (FaultSection sect : subSects)
//////			if (sect.getName().startsWith("Anegada Passage"))
//////				filteredSects.add(sect);
//////		subSects = filteredSects;
////		
////		GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
////		
////		List<LocationList> lines = new ArrayList<>();
////		for (FaultSection sect : proxySects.proxySects)
////			lines.add(sect.getFaultTrace());
////		mapMaker.plotLines(lines, Color.BLUE, 1f);
////		
////		mapMaker.plot(new File("/tmp"), "proxy_finite_sect_test", " ");
////		mapMaker.plot(new File("C:\\Users\\kmilner\\Downloads"), "proxy_finite_sect_test", " ");
//	}

}
