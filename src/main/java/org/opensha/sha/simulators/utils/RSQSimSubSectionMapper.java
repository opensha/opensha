package org.opensha.sha.simulators.utils;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.Interpolate;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.kevin.simulators.erf.SimulatorFaultSystemSolution;

public class RSQSimSubSectionMapper {
	
	private List<FaultSectionPrefData> subSects;
	private List<SimulatorElement> elements;
	private Map<Integer, Double> subSectAreas;
	private Map<IDPairing, Double> distsCache;
	private Map<SimulatorElement, Double> elemSectDASs;
	
	private int minElemSectID;
	private Map<SimulatorElement, FaultSectionPrefData> elemToSectsMap;
	private Map<FaultSectionPrefData, HashSet<SimulatorElement>> sectsToElemsMap;
	
	// for slip on sections
	private Map<SimulatorElement, FaultSectionPrefData> slipElemsToSectsMap;
	private Map<FaultSectionPrefData, HashSet<SimulatorElement>> slipSectsToElemsMap;
	
	private LoadingCache<RSQSimEvent, List<List<SubSectionMapping>>> mappingsCache;
	
	public enum SlipAlongSectAlgorithm {
		MID_SEIS_FULL_LEN,
		MID_SEIS_SLIPPED_LEN,
		MID_SEIS_MID_SLIPPED_LEN,
		MID_SEIS_SURF_SLIP_LEN
	}

	public RSQSimSubSectionMapper(List<FaultSectionPrefData> subSects, List<SimulatorElement> elements) {
		this(subSects, elements, RSQSimUtils.calcSubSectAreas(elements, subSects), new HashMap<>());
	}
	
	public RSQSimSubSectionMapper(List<FaultSectionPrefData> subSects, List<SimulatorElement> elements,
			Map<Integer, Double> subSectAreas, Map<IDPairing, Double> distsCache) {
		this.subSects = subSects;
		this.elements = elements;
		this.subSectAreas = subSectAreas;
		this.distsCache = distsCache;
		
		this.minElemSectID = RSQSimUtils.getSubSectIndexOffset(elements, subSects);
		
		// build basic mapping
		elemToSectsMap = new HashMap<>();
		sectsToElemsMap = new HashMap<>();
		for (SimulatorElement elem : elements) {
			int sectID = elem.getSectionID() - minElemSectID;
			Preconditions.checkState(sectID >= 0 && sectID <= subSects.size(), "Bad section id. origID=%s, minID=%s, index=%s, numSects=%s",
					elem.getSectionID(), minElemSectID, sectID, subSects.size());
			FaultSectionPrefData sect = subSects.get(sectID);
			elemToSectsMap.put(elem, sect);
			HashSet<SimulatorElement> sectElems = sectsToElemsMap.get(sect);
			if (sectElems == null) {
				sectElems = new HashSet<>();
				sectsToElemsMap.put(sect, sectElems);
			}
			sectElems.add(elem);
		}
		
		// now compute DAS for each element
		elemSectDASs = new HashMap<>();
		for (FaultSectionPrefData sect : subSects) {
			if (!sectsToElemsMap.containsKey(sect))
				continue;
			// first create a really high resolution 3-D fault surface
			SimpleFaultData sfd = new SimpleFaultData(sect.getAveDip(), Math.max(sect.getAveLowerDepth(), 20), sect.getOrigAveUpperDepth(),
					sect.getFaultTrace(), sect.getDipDirection());
//			StirlingGriddedSurface gridSurf = sect.getStirlingGriddedSurface(0.5, false, false);
			StirlingGriddedSurface gridSurf = new StirlingGriddedSurface(sfd, 0.5, 1d);
			
			ArbitrarilyDiscretizedFunc[] dasFuncs = new ArbitrarilyDiscretizedFunc[gridSurf.getNumRows()];
			
			// build a mapping function from the DAS computed assuming a straight line to the actual DAS
			// do this separately for each depth to avoid any systematic offset issues with depth
			// also normalize it, as we could be offset slightly along strike, which we'll correct for later
			for (int row=0; row<dasFuncs.length; row++) {
				double cumulativeLen = 0;
				ArbitrarilyDiscretizedFunc dasFunc = new ArbitrarilyDiscretizedFunc();
				dasFunc.set(0d, 0d);
				Location start = gridSurf.get(row, 0);
				Location end = gridSurf.get(row, gridSurf.getNumCols()-1);
				for (int col=1; col<gridSurf.getNumCols(); col++) {
					cumulativeLen += LocationUtils.horzDistanceFast(gridSurf.get(row, col-1), gridSurf.get(row, col));
					double rawDAS = calcStraightLineDAS(start, end, gridSurf.get(row, col));
					dasFunc.set(rawDAS, cumulativeLen);
				}
				
				// now normalize
				dasFuncs[row] = new ArbitrarilyDiscretizedFunc();
				double minDAS = dasFunc.getMinX();
				double maxDAS = dasFunc.getMaxX();
				double dasSpan = maxDAS - minDAS;
				for (Point2D pt : dasFunc) {
					double normX = (pt.getX() - minDAS) / dasSpan;
					dasFuncs[row].set(normX, pt.getY());
				}
			}
			
			// calculate raw DAS for each element
			Map<SimulatorElement, Double> rawDASs = new HashMap<>();
			Map<SimulatorElement, Integer> elemRows = new HashMap<>();
			XY_DataSet dasDepthFunc = new DefaultXY_DataSet();
			for (SimulatorElement elem : sectsToElemsMap.get(sect)) {
				Location center = elem.getCenterLocation();
				int closestRow = -1;
				double minDepthDiff = Double.POSITIVE_INFINITY;
				for (int row=0; row<gridSurf.getNumRows(); row++) {
					double depthDiff = Math.abs(center.getDepth() - gridSurf.get(row, 0).getDepth());
					if (depthDiff < minDepthDiff) {
						minDepthDiff = depthDiff;
						closestRow = row;
					}
				}
				Location start = gridSurf.get(closestRow, 0);
				Location end = gridSurf.get(closestRow, gridSurf.getNumCols()-1);
				double rawDAS = calcStraightLineDAS(start, end, center);
				rawDASs.put(elem, rawDAS);
				elemRows.put(elem, closestRow);
				dasDepthFunc.set(rawDAS, center.getDepth());
			}
			
			double minDepth = dasDepthFunc.getMinY();
			double maxDepth = dasDepthFunc.getMaxY();
			
			double midDepth = 0.5*(maxDepth + minDepth);
			double upperHalfMin = Double.POSITIVE_INFINITY;
			double upperHalfMax = Double.NEGATIVE_INFINITY;
			double lowerHalfMin = Double.POSITIVE_INFINITY;
			double lowerHalfMax = Double.NEGATIVE_INFINITY;
			
			for (Point2D pt : dasDepthFunc) {
				double das = pt.getX();
				double depth = pt.getY();
				if (depth > midDepth) {
					lowerHalfMax = Math.max(lowerHalfMax, das);
					lowerHalfMin = Math.min(lowerHalfMin, das);
				} else {
					upperHalfMax = Math.max(upperHalfMax, das);
					upperHalfMin = Math.min(upperHalfMin, das);
				}
			}
			
			// first calculate assuming no depth dependent bias
			DiscretizedFunc depthMinDASFunc = new ArbitrarilyDiscretizedFunc();
			DiscretizedFunc depthMaxDASFunc = new ArbitrarilyDiscretizedFunc();
			depthMinDASFunc.set(minDepth, upperHalfMin);
			depthMinDASFunc.set(maxDepth, lowerHalfMin);
			depthMaxDASFunc.set(minDepth, upperHalfMax);
			depthMaxDASFunc.set(maxDepth, lowerHalfMax);
//			depthMinDASFunc.set(minDepth, dasDepthFunc.getMinX());
//			depthMinDASFunc.set(maxDepth, dasDepthFunc.getMinX());
//			depthMaxDASFunc.set(minDepth, dasDepthFunc.getMaxX());
//			depthMaxDASFunc.set(maxDepth, dasDepthFunc.getMaxX());
			
//			double depthRange = maxDepth - minDepth;
//			double minDAS = dasDepthFunc.getMinX();
//			double maxDAS = dasDepthFunc.getMaxX();
//			double aveArea = 0d;
//			for (SimulatorElement e : rawDASs.keySet())
//				aveArea += e.getArea()*1e-6;
//			aveArea /= rawDASs.keySet().size();
//			double aveSpacing = Math.sqrt(aveArea);
//			
//			double numSpacings = depthRange / aveSpacing;
//			
//			if (numSpacings > 5) {
//				// allow for depth dependence
//				List<Double> upperDASs = new ArrayList<>();
//				List<Double> lowerDASs = new ArrayList<>();
//				
//				// the fraction from the bottom or top that we consider to be the bottom or top
//				// this depends on the spacing and width, and needs to be conservative enough to put enough elements into each bin
//				double fract;
//				if (numSpacings > 30)
//					// we have more than 20 element rows, do the top or bottom 20%
//					fract = 0.15;
//				else
//					fract = Interpolate.findY(5, 0.5, 30, 0.15, numSpacings);
//				
//				for (SimulatorElement elem : rawDASs.keySet()) {
//					double depth = elem.getCenterLocation().getDepth();
//					double fractDepth = (depth - minDepth) / depthRange;
//					Preconditions.checkState(fractDepth >= 0d && fractDepth <= 1d);
//					if (fractDepth < fract)
//						upperDASs.add(rawDASs.get(elem));
//					else if (fractDepth > (1d-fract))
//						lowerDASs.add(rawDASs.get(elem));
//				}
//				// make sure we have enough in each bin
//				if (upperDASs.size() > 5 && lowerDASs.size() > 5) {
//					double[] uppers = Doubles.toArray(upperDASs);
//					double[] lowers = Doubles.toArray(lowerDASs);
//					double upperMin = StatUtils.min(uppers);
//					double upperMax = StatUtils.max(uppers);
//					double lowerMin = StatUtils.min(lowers);
//					double lowerMax = StatUtils.max(lowers);
//					
//					depthMinDASFunc.set(minDepth, upperMin);
//					depthMinDASFunc.set(maxDepth, lowerMin);
//					depthMaxDASFunc.set(minDepth, upperMax);
//					depthMaxDASFunc.set(maxDepth, lowerMax);
//					
//					System.out.println("Doing depth skew correction for "+sect.getName()+" with "+rawDASs.size()+" total");
//					System.out.println("\tfract: "+fract);
//					System.out.println("\tUpper range from "+uppers.length+" vals: ["+(float)upperMin+" "+(float)upperMax+"]");
//					System.out.println("\tLower range from "+lowers.length+" vals: ["+(float)lowerMin+" "+(float)lowerMax+"]");
//				}
//			}
			
			// now convert to actual DAS, with any bias removed
			for (SimulatorElement elem : rawDASs.keySet()) {
				double rawDAS = rawDASs.get(elem);
				double depth = elem.getCenterLocation().getDepth();
				
				double myMinDAS = depthMinDASFunc.getInterpolatedY(depth);
				double myMaxDAS = depthMaxDASFunc.getInterpolatedY(depth);
				double dasSpan = myMaxDAS - myMinDAS;
				
				// normalize it
				double normDAS = (rawDAS - myMinDAS) / dasSpan;
//				Preconditions.checkState((float)normDAS <= 1f && (float)normDAS >= 0f,
//						"Bad normalized DAS. raw=%s, range=[%s %s], depth=%s", rawDAS, myMinDAS, myMaxDAS, depth);
				
				int row = elemRows.get(elem);
				double das;
				if (normDAS <= dasFuncs[row].getMinX())
					das = dasFuncs[row].getMinY();
				else if (normDAS >= dasFuncs[row].getMaxX())
					das = dasFuncs[row].getMaxY();
				else
					das = dasFuncs[row].getInterpolatedY(normDAS);
//				if (elem.getID() == 180899 || elem.getID() == 180886 || elem.getID() == 180845 || elem.getID() == 180940) {
//				if (elem.getID() == 78269 || elem.getID() == 78358 || elem.getID() == 78387) {
//					System.out.println("Debug for "+elem.getID()+", sectID="+elem.getSectionID()+", mapped row="+row);
//					System.out.println("\traw DAS: "+rawDAS);
//					System.out.println("\tmy DAS Range: "+myMinDAS+" "+myMaxDAS);
//					System.out.println("\tnorm DAS: "+normDAS);
//					System.out.println("\tinterp DAS: "+das);
//				}
				elemSectDASs.put(elem, das);
			}
		}
		
		mappingsCache = CacheBuilder.newBuilder().maximumSize(1000).build(new CacheLoader<RSQSimEvent, List<List<SubSectionMapping>>>() {

			@Override
			public List<List<SubSectionMapping>> load(RSQSimEvent key) throws Exception {
				return loadSubSectionMappings(key);
			}
			
		});
	}
	
	private double calcStraightLineDAS(Location start, Location end, Location loc) {
		double distToLine = Math.abs(LocationUtils.distanceToLineFast(start, end, loc));
		double distToSegment = Math.abs(LocationUtils.distanceToLineSegmentFast(start, end, loc));
		double distToStart = LocationUtils.horzDistanceFast(start, loc);
		double distToEnd = LocationUtils.horzDistanceFast(end, loc);
		if (distToLine > distToStart) {
			Preconditions.checkState(Math.abs(distToLine - distToStart) < 0.1,
					"Bad triangle in straight line DAS calc. distToLine=%s, distToStart=%s", distToLine, distToStart);
			return 0d;
		}
		double ret = Math.sqrt(distToStart*distToStart - distToLine*distToLine);
		if (distToSegment > 1.01*distToLine && distToStart < distToEnd) {
			// we're before the start, das should be negative
			ret = -ret;
		}
		Preconditions.checkState(Double.isFinite(ret), "Non-finite straight line DAS. distToLine=%s, distToStart=%s", distToLine, distToStart);
		return ret;
	}
	
	public List<FaultSectionPrefData> getSubSections() {
		return subSects;
	}
	
	public FaultSectionPrefData getMappedSection(SimulatorElement element) {
		return elemToSectsMap.get(element);
	}
	
	public double getElemSubSectDAS(SimulatorElement element) {
		return elemSectDASs.get(element);
	}
	
	/**
	 * Calling this method will enable tracking of slip on each subsection. Slip will be tracked within a band of elements for which
	 * the center is >= minDepth and <= maxDepth. If faultDownDipBuffer is >0, then only elements whose center depth is at least
	 * faultDownDipBuffer away down dip from the top of the fault, or up dip from the bottom, will be included.
	 * @param minDepth
	 * @param maxDepth
	 * @param faultDownDipBuffer
	 */
	public void trackSlipOnSections(double minDepth, double maxDepth, double faultDownDipBuffer) {
		mappingsCache.invalidateAll();
		Preconditions.checkArgument(minDepth < maxDepth, "minDepth must be > maxDepth!");
		
		Map<Integer, double[]> sectDDConstraints = null;
		if (faultDownDipBuffer > 0) {
			// only include elements that are at least this distance from the top or bottom of the section
			sectDDConstraints = new HashMap<>();
			
			Map<Integer, List<Double>> sectDips = new HashMap<>();
			
			// gather min/max depths for each sect
			for (SimulatorElement elem : elements) {
				int sectID = elem.getSectionID() - minElemSectID;
				double[] minMax = sectDDConstraints.get(sectID);
				if (minMax == null) {
					minMax = new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
					sectDDConstraints.put(sectID, minMax);
					sectDips.put(sectID, new ArrayList<>());
				}
				double depth = elem.getCenterLocation().getDepth();
				minMax[0] = Math.min(minMax[0], depth);
				minMax[1] = Math.max(minMax[1], depth);
				sectDips.get(sectID).add(elem.getFocalMechanism().getDip());
			}
			
			// now convert from extrema to constraints
			for (Integer sectID : sectDDConstraints.keySet()) {
				List<Double> dips = sectDips.get(sectID);
				double aveDip = 0d;
				for (Double dip : dips)
					aveDip += dip;
				aveDip /= (double)dips.size();
				double bufferVertical = Math.sin(Math.toRadians(aveDip))*faultDownDipBuffer;
				double[] depthRange = sectDDConstraints.get(sectID);
				depthRange[0] += bufferVertical;
				depthRange[1] -= bufferVertical;
				if (depthRange[0] > depthRange[1] || depthRange[1] < minDepth || depthRange[0] > maxDepth) {
					double sMinDepth = depthRange[0]-bufferVertical;
					double sMaxDepth = depthRange[0]-bufferVertical;
					System.out.println("WARNING: depth range invalid for section "+sectID+". AveDip of "+(float)aveDip+" leads to vertical "
							+"buffer of "+bufferVertical+" km for "+faultDownDipBuffer+" km down-dip buffer.");
					System.out.println("\tSection depth range: ["+sMinDepth+" "+sMaxDepth+"]");
					System.out.println("\tSection buffered: ["+depthRange[0]+" "+depthRange[1]+"]");
					System.out.println("\tGlobal min/max: ["+minDepth+" "+maxDepth+"]");
				}
			}
		}
		
		slipElemsToSectsMap = new HashMap<>();
		slipSectsToElemsMap = new HashMap<>();
		
		for (SimulatorElement elem : elements) {
			int sectID = elem.getSectionID() - minElemSectID;
			FaultSectionPrefData subSect = subSects.get(sectID);
			
			double myMinDepth = minDepth;
			double myMaxDepth = maxDepth;
			if (sectDDConstraints != null) {
				double[] constr = sectDDConstraints.get(sectID);
				myMinDepth = Math.max(myMinDepth, constr[0]);
				myMaxDepth = Math.min(myMaxDepth, constr[1]);
			}
			
			double depth = elem.getCenterLocation().getDepth();
			if (depth >= myMinDepth && depth <= myMaxDepth) {
				slipElemsToSectsMap.put(elem, subSect);
				HashSet<SimulatorElement> elemsForSect = slipSectsToElemsMap.get(subSect);
				if (elemsForSect == null) {
					elemsForSect = new HashSet<>();
					slipSectsToElemsMap.put(subSect, elemsForSect);
				}
				elemsForSect.add(elem);
			}
		}
	}
	
	/**
	 * Returns an unmodifiable list of all subsection mappings for which at least one element slipped in the event. Sections are
	 * bundled by parent section (one sublist for each parent), and both bundles and mappings within bundles are sorted from end to end
	 * of the rupture.
	 * @param event
	 * @return parent section bundles list of all mappings
	 */
	public List<List<SubSectionMapping>> getAllSubSectionMappings(RSQSimEvent event) {
		try {
			return mappingsCache.get(event);
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	/**
	 * Returns a list of all subsection mappings for which at least the given fraction of the subsection area slipped in the
	 * event. Sections are bundled by parent section (one sublist for each parent), and both bundles and mappings within bundles are sorted
	 * from end to end of the rupture.
	 * @param event
	 * @return parent section bundles list of all mappings
	 */
	public List<List<SubSectionMapping>> getFilteredSubSectionMappings(RSQSimEvent event, double minFractForInclusion) {
		List<List<SubSectionMapping>> allMappings = getAllSubSectionMappings(event);
		if (minFractForInclusion == 0d)
			return allMappings;
		List<List<SubSectionMapping>> ret = new ArrayList<>();
		for (List<SubSectionMapping> bundle : allMappings) {
			List<SubSectionMapping> filtered = new ArrayList<>();
			for (SubSectionMapping mapping : bundle) {
				double fractOn = mapping.getAreaSlipped() / subSectAreas.get(mapping.subSect.getSectionId());
				if (fractOn >= minFractForInclusion)
					filtered.add(mapping);
			}
			if (!filtered.isEmpty())
				ret.add(filtered);
		}
		return ret;
	}
	
//	public List<List<SubSectionMapping>> bundleMappings(List<SubSectionMapping> mappings) {
//		try {
//			return mappingsCache.get(event);
//		} catch (ExecutionException e) {
//			throw ExceptionUtils.asRuntimeException(e);
//		}
//	}
	
	private List<List<SubSectionMapping>> loadSubSectionMappings(RSQSimEvent event) {
		List<SubSectionMapping> mappings = new ArrayList<>();
		
		Map<FaultSectionPrefData, SubSectionMapping> sectMap = new HashMap<>();
		
		List<SimulatorElement> elems = event.getAllElements();
		double[] slips = event.getAllElementSlips();
		
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem = elems.get(i);
			double slip = slips[i];
			FaultSectionPrefData sect = elemToSectsMap.get(elem);
			SubSectionMapping mapping = sectMap.get(sect);
			if (mapping == null) {
				mapping = new SubSectionMapping(sect);
				sectMap.put(sect, mapping);
				mappings.add(mapping);
			}
			mapping.addSlip(elem, slip);
		}
		
		// bundle by parent section id
		Map<Integer, List<FaultSectionPrefData>> rupSectsBundled = Maps.newHashMap();
		for (SubSectionMapping mapping : mappings) {
			// convert to 0-based
			int parentID = mapping.subSect.getParentSectionId();
			List<FaultSectionPrefData> sects = rupSectsBundled.get(parentID);
			if (sects == null) {
				sects = new ArrayList<>();
				rupSectsBundled.put(parentID, sects);
			}
			sects.add(mapping.subSect);
		}
		
		// sort each bundle my section index
		List<List<FaultSectionPrefData>> rupSectsListBundled = Lists.newArrayList();
		for (List<FaultSectionPrefData> sects : rupSectsBundled.values()) {
			Collections.sort(sects, sectIDCompare);
			rupSectsListBundled.add(sects);
		}

		// sort bundles (put faults in order from one end to the other)
		if (rupSectsListBundled.size() > 1)
			rupSectsListBundled = SimulatorFaultSystemSolution.sortRupture(subSects, rupSectsListBundled, distsCache);
		
		// build return list
		List<List<SubSectionMapping>> ret = new ArrayList<>();
		for (List<FaultSectionPrefData> bundle : rupSectsListBundled) {
			List<SubSectionMapping> bundleMappings = new ArrayList<>();
			for (FaultSectionPrefData sect : bundle)
				bundleMappings.add(sectMap.get(sect));
			ret.add(Collections.unmodifiableList(bundleMappings));
		}
		
		return Collections.unmodifiableList(ret);
	}
	
	private static Comparator<FaultSectionPrefData> sectIDCompare = new Comparator<FaultSectionPrefData>() {

		@Override
		public int compare(FaultSectionPrefData o1, FaultSectionPrefData o2) {
			return new Integer(o1.getSectionId()).compareTo(o2.getSectionId());
		}
	};
	
	public class SubSectionMapping {
		private FaultSectionPrefData subSect;
		private double areaSlipped;
		private double subSectArea;
		private double momentOnSect;
		
		private double minDAS = Double.POSITIVE_INFINITY;
		private double maxDAS = Double.NEGATIVE_INFINITY;
		
		private double minSlipRegionDAS = Double.POSITIVE_INFINITY;
		private double maxSlipRegionDAS = Double.NEGATIVE_INFINITY;
		private double areaWeightedSlipInSlipRegion;
		
		public SubSectionMapping(FaultSectionPrefData subSect) {
			this.subSect = subSect;
			this.areaSlipped = subSectAreas.get(subSect.getSectionId());
		}
		
		protected void addSlip(SimulatorElement elem, double slip) {
			Preconditions.checkState(slip > 0);
			double area = elem.getArea();
			areaSlipped += area;
			momentOnSect += FaultMomentCalc.getMoment(area, slip);
			double das = elemSectDASs.get(elem);
			minDAS = Math.min(das, minDAS);
			maxDAS = Math.max(das, maxDAS);
			
			if (slipSectsToElemsMap != null) {
				// track slips
				if (slipSectsToElemsMap.get(subSect).contains(elem)) {
					// this element is inside the slip region
					minSlipRegionDAS = Math.min(das, minSlipRegionDAS);
					maxSlipRegionDAS = Math.max(das, maxSlipRegionDAS);
					areaWeightedSlipInSlipRegion += area*slip;
				}
			}
		}

		public FaultSectionPrefData getSubSect() {
			return subSect;
		}

		public double getAreaSlipped() {
			return areaSlipped;
		}

		public double getSubSectArea() {
			return subSectArea;
		}

		public double getMomentOnSect() {
			return momentOnSect;
		}
		
		public double getLengthForSlip(SlipAlongSectAlgorithm type) {
			Preconditions.checkState(slipElemsToSectsMap != null, "Must enable slip tracking with trackSlipOnSections(...)");
			switch (type) {
			case MID_SEIS_FULL_LEN:
				return subSect.getFaultTrace().getTraceLength();
			case MID_SEIS_SURF_SLIP_LEN:
				throw new IllegalStateException("TODO");
			case MID_SEIS_SLIPPED_LEN:
				return maxDAS - minDAS;
			case MID_SEIS_MID_SLIPPED_LEN:
				return maxSlipRegionDAS - minSlipRegionDAS;

			default:
				throw new IllegalStateException("Unsupported type: "+type);
			}
		}
		
		public double getAreaForAverageSlip(SlipAlongSectAlgorithm type) {
			Preconditions.checkState(slipElemsToSectsMap != null, "Must enable slip tracking with trackSlipOnSections(...)");
			double totArea = 0d;
			for (SimulatorElement elem : slipSectsToElemsMap.get(subSect)) {
				double das = elemSectDASs.get(elem);
				double area = elem.getArea();
				switch (type) {
				case MID_SEIS_FULL_LEN:
					totArea += area;
					break;
				case MID_SEIS_SURF_SLIP_LEN:
					throw new IllegalStateException("TODO");
				case MID_SEIS_SLIPPED_LEN:
					if (das >= minDAS && das <= maxDAS)
						totArea += area;
					break;
				case MID_SEIS_MID_SLIPPED_LEN:
					if (das >= minSlipRegionDAS && das <= maxSlipRegionDAS)
						totArea += area;
					break;

				default:
					throw new IllegalStateException("Unsupported type: "+type);
				}
			}
			return totArea;
		}
		
		public double getAverageSlip(SlipAlongSectAlgorithm type) {
			double areaForSlipCalc = getAreaForAverageSlip(type);
			if (areaForSlipCalc == 0d) {
				Preconditions.checkState(areaWeightedSlipInSlipRegion == 0d);
				return 0d;
			}
			return areaWeightedSlipInSlipRegion/areaForSlipCalc;
		}
	}
	
	public static void main(String[] args) throws IOException {
//		File dir = new File("/data/kevin/simulators/catalogs/rundir2585_1myr");
		File dir = new File("/data/kevin/simulators/catalogs/bruce/rundir3165");
		File geomFile = new File(dir, "zfault_Deepen.in");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		List<FaultSectionPrefData> subSects = RSQSimUtils.getUCERF3SubSectsForComparison(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		RSQSimSubSectionMapper mapper = new RSQSimSubSectionMapper(subSects, elements);
		
	}

}
