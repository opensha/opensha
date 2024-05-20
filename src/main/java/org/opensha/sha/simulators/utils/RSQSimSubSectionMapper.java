package org.opensha.sha.simulators.utils;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
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
	
	private List<? extends FaultSection> subSects;
	private List<SimulatorElement> elements;
	private Map<Integer, Double> subSectAreas;
	private Map<IDPairing, Double> distsCache;
	private Map<SimulatorElement, SubSectDAS_Record> elemRawDASs;
	private Map<SimulatorElement, SubSectDAS_Record> elemSectDASs;
	
	public static class DAS_Record {
		public final double midDAS;
		public final double startDAS;
		public final double endDAS;
		
		public DAS_Record(double startDAS, double endDAS) {
			this(0.5*(startDAS + endDAS), startDAS, endDAS);
		}
		
		public DAS_Record(double midDAS, double startDAS, double endDAS) {
			Preconditions.checkState(endDAS >= startDAS && midDAS >= startDAS && midDAS <= endDAS,
					"Bad DAS: mid=%s, start=%s, end=%s", midDAS, startDAS, endDAS);
			this.midDAS = midDAS;
			this.startDAS = startDAS;
			this.endDAS = endDAS;
		}
		
		public DAS_Record getReversed(double totalLen) {
			Preconditions.checkState((float)endDAS <= (float)totalLen);
			return new DAS_Record(totalLen - midDAS, totalLen - endDAS, totalLen - startDAS);
		}
		
		public boolean contains(double das) {
			return das >= startDAS && das <= endDAS;
		}
	}
	
	public static class SubSectDAS_Record extends DAS_Record {

		public final double[] vertDASs;

		public SubSectDAS_Record(double[] vertDASs, double midDAS) {
			super(midDAS, StatUtils.min(vertDASs), StatUtils.max(vertDASs));
			this.vertDASs = vertDASs;
		}
	}
	
	private int minElemSectID;
	private Map<SimulatorElement, FaultSection> elemToSectsMap;
	private Map<FaultSection, HashSet<SimulatorElement>> sectsToElemsMap;
	
	// for slip on sections
	private Map<SimulatorElement, FaultSection> slipElemsToSectsMap;
	private Map<FaultSection, HashSet<SimulatorElement>> slipSectsToElemsMap;
	private Map<FaultSection, double[]> sectMidDepthConstraints;
	
	private LoadingCache<SimulatorEvent, List<List<SubSectionMapping>>> mappingsCache;

	public static final double MID_SEIS_MIN_DEPTH_DEFAULT = 4d;
	public static final double MID_SEIS_MAX_DEPTH_DEFAULT = 8d;
	public static final double MID_SEIS_BUFFER_DEFAULT = 2d;
	private double minSlipDepth;
	private double maxSlipDepth;
	private double faultDownDipBuffer;
	private double minFractForInclusion;
	
	public enum SlipAlongSectAlgorithm {
		MID_SEIS_FULL_SUBSECTION_LEN("Full Mapped Subsection Length",
				"Average slip in the mid-seismogenic zone across the whole length of each mapped subsection"),
		MID_SEIS_SLIPPED_LEN("Full Slipped Length",
				"Average slip in the mid-seismogenic zone across the section of fault that slipped "
				+ "(regardless of if that slip was in the mid-seismgenic zone or not)"),
		MID_SEIS_MID_SLIPPED_LEN("Mid-Seismogenic Slipped Length",
				"Average slip in the mid-seismogenic zone across the section of fault that slipped "
				+ "in that mid-seismogenic zone (including any holes with no slip)"),
		MID_SEIS_SURF_SLIP_LEN("Surface Slipped Length",
				"Average slip in the mid-seismogenic zone across the section of fault that had surface slip");
		
		private String name;
		private String description;
		
		private SlipAlongSectAlgorithm(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		public String toString() {
			return name;
		}
		
		public String getDescription() {
			return description;
		}
	}

	public RSQSimSubSectionMapper(List<? extends FaultSection> subSects, List<SimulatorElement> elements, double minFractForInclusion) {
		this(subSects, elements, minFractForInclusion, RSQSimUtils.calcSubSectAreas(elements, subSects), new HashMap<>());
	}
	
	public RSQSimSubSectionMapper(List<? extends FaultSection> subSects, List<SimulatorElement> elements, double minFractForInclusion,
			Map<Integer, Double> subSectAreas, Map<IDPairing, Double> distsCache) {
		this.subSects = subSects;
		this.elements = elements;
		this.minFractForInclusion = minFractForInclusion;
		this.subSectAreas = subSectAreas;
		this.distsCache = distsCache;
		
//		System.out.println("Min fract: "+this.minFractForInclusion);
		
		this.minElemSectID = RSQSimUtils.getSubSectIndexOffset(elements, subSects);
//		System.out.println("Offset: "+minElemSectID);
		
		// build basic mapping
		elemToSectsMap = new HashMap<>();
		sectsToElemsMap = new HashMap<>();
		for (SimulatorElement elem : elements) {
			int sectID = elem.getSectionID() - minElemSectID;
			Preconditions.checkState(sectID >= 0 && sectID <= subSects.size(), "Bad section id. origID=%s, minID=%s, index=%s, numSects=%s",
					elem.getSectionID(), minElemSectID, sectID, subSects.size());
			FaultSection sect = subSects.get(sectID);
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
		elemRawDASs = new HashMap<>();
		for (FaultSection sect : subSects) {
			if (!sectsToElemsMap.containsKey(sect))
				continue;
			Preconditions.checkState(subSectAreas.containsKey(sect.getSectionId()), "No area found for section %s, %s, with %s mapped elements",
					sect.getSectionId(), sect.getSectionName(), sectsToElemsMap.get(sect).size());
			HashSet<SimulatorElement> sectElems = sectsToElemsMap.get(sect);
			// first create a really high resolution 3-D fault surface
			SimpleFaultData sfd = new SimpleFaultData(sect.getAveDip(), Math.max(sect.getAveLowerDepth(), 20), sect.getOrigAveUpperDepth(),
					sect.getFaultTrace(), sect.getDipDirection());
//			StirlingGriddedSurface gridSurf = sect.getStirlingGriddedSurface(0.5, false, false);
			StirlingGriddedSurface gridSurf = new StirlingGriddedSurface(sfd, 0.5, 1d);
			
			double sectLen = sect.getFaultTrace().getTraceLength();
			
			int rows = gridSurf.getNumRows();
			int cols = gridSurf.getNumCols();
			
			int numDAS_x = 100;
			double spacingX = 1d/(numDAS_x-1);
			double spacingY = 1d/(rows-1);
			
			// normalized: x is DAS, from 0 to 1 (max DAS)
			// y is depth, from 0 (min depth) to 1 (max depth)
			EvenlyDiscrXYZ_DataSet dasXYZ = new EvenlyDiscrXYZ_DataSet(numDAS_x, rows, 0d, 0d, spacingX, spacingY);
			Preconditions.checkState((float)dasXYZ.getMaxX() == 1f);
			Preconditions.checkState((float)dasXYZ.getMaxY() == 1f);
			
//			boolean debugSect = sect.getParentSectionId() == 151;
			boolean debugSect = false;
			if (debugSect)
				System.out.println("DEBUG section "+sect.getSectionName()+" with "+sectElems.size()+" elements");
			
			// build a mapping function from the DAS computed assuming a straight line to the actual DAS
			// do this separately for each depth to avoid any systematic offset issues with depth
			// also normalize it, as we could be offset slightly along strike, which we'll correct for later
			for (int row=0; row<rows; row++) {
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
				for (int xInd=0; xInd<numDAS_x; xInd++) {
					double normX = dasXYZ.getX(xInd);
					Preconditions.checkState(normX <= 1d);
					double funcDAS = normX*dasFunc.getMaxX();
					Preconditions.checkState(funcDAS <= dasFunc.getMaxX(),
							"Bad funcDAS: %s * %s = %s > %s", normX, cumulativeLen, funcDAS, dasFunc.getMaxX());
					double uncorrectedDAS = dasFunc.getInterpolatedY(funcDAS);
					Preconditions.checkState(uncorrectedDAS <= cumulativeLen && uncorrectedDAS >= 0d);
					double realDAS = sectLen*uncorrectedDAS/cumulativeLen; // fix any difference in this row length vs the real length
					dasXYZ.set(xInd, row, realDAS);
				}
			}
			
			// calculate raw DAS for each element
			Map<SimulatorElement, Integer> elemRows = new HashMap<>();
			XY_DataSet dasDepthFunc = new DefaultXY_DataSet();
			List<Double> dips = new ArrayList<>();
			for (SimulatorElement elem : sectElems) {
				Location center = elem.getCenterLocation();
				dips.add(elem.getFocalMechanism().getDip());
				int closestRow = -1;
				double minDepthDiff = Double.POSITIVE_INFINITY;
				for (int row=0; row<rows; row++) {
					double depthDiff = Math.abs(center.getDepth() - gridSurf.get(row, 0).getDepth());
					if (depthDiff < minDepthDiff) {
						minDepthDiff = depthDiff;
						closestRow = row;
					}
				}
				Location start = gridSurf.get(closestRow, 0);
				Location end = gridSurf.get(closestRow, gridSurf.getNumCols()-1);
				double rawMidDAS = calcStraightLineDAS(start, end, center);
				Vertex[] verts = elem.getVertices();
				double[] vertDASs = new double[verts.length];
				for (int i=0; i<verts.length; i++) {
					vertDASs[i] = calcStraightLineDAS(start, end, verts[i]);
					dasDepthFunc.set(vertDASs[i], verts[i].getDepth());
				}
				try {
					elemRawDASs.put(elem, new SubSectDAS_Record(vertDASs, rawMidDAS));
				} catch (RuntimeException e1) {
					System.out.println("Element: "+elem.getID()+". "+elem.getName()+" ("+elem.getSectionName()+")");
					System.out.println("Center: "+center+", DAS="+calcStraightLineDAS(start, end, center));
					System.out.println("Vertices:");
					for (Location vert : elem.getVertices())
						System.out.println("\t"+vert+", DAS="+calcStraightLineDAS(start, end, vert));
					throw e1;
				}
				elemRows.put(elem, closestRow);
			}
			
			double minDepth = dasDepthFunc.getMinY();
			double maxDepth = dasDepthFunc.getMaxY();
			double aveDip = FaultUtils.getAngleAverage(dips);
			double estDDW = (maxDepth - minDepth)/Math.sin(Math.toRadians(aveDip));
			
			double aveArea = 0d;
			for (SimulatorElement e : sectElems)
				aveArea += e.getArea()*1e-6;
			aveArea /= sectElems.size();
			double aveSpacing = Math.sqrt(aveArea);
			
			double estNumDownDip = estDDW/aveSpacing;
			int numDepthBins = Integer.max(2, (int)Math.round(estNumDownDip / 3));
			if (debugSect) {
				System.out.println("\tEstimated DDW with dip="+(float)aveDip+": "+estDDW);
				System.out.println("\tEstimated average spacing: "+aveSpacing);
				System.out.println("\tEstimated num down dip: "+estNumDownDip);
				System.out.println("\tDepth bins: "+numDepthBins);
			}
			
			EvenlyDiscretizedFunc minFunc = new EvenlyDiscretizedFunc(minDepth, maxDepth, numDepthBins);
			EvenlyDiscretizedFunc maxFunc = new EvenlyDiscretizedFunc(minDepth, maxDepth, numDepthBins);
			for (int i=0; i<minFunc.size(); i++) {
				minFunc.set(i, Double.POSITIVE_INFINITY);
				maxFunc.set(i, Double.NEGATIVE_INFINITY);
			}
			
			for (Point2D pt : dasDepthFunc) {
				double das = pt.getX();
				double depth = pt.getY();
				int ind = minFunc.getClosestXIndex(depth);
				minFunc.set(ind, Math.min(minFunc.getY(ind), das));
				maxFunc.set(ind, Math.max(maxFunc.getY(ind), das));
			}
			
			boolean hasNonFinite = false;
			for (int i=0; i<minFunc.size(); i++)
				hasNonFinite = hasNonFinite || !Double.isFinite(minFunc.getY(i));
			Preconditions.checkState(!hasNonFinite, "We have a non-finite depth bin while correcting section DAS");
			
			DiscretizedFunc depthMinDASFunc = new ArbitrarilyDiscretizedFunc();
			DiscretizedFunc depthMaxDASFunc = new ArbitrarilyDiscretizedFunc();
			// only map based on the ends to avoid any squirreliness
			depthMinDASFunc = new ArbitrarilyDiscretizedFunc();
			depthMaxDASFunc = new ArbitrarilyDiscretizedFunc();
			depthMinDASFunc.set(minDepth, minFunc.getY(0));
			depthMinDASFunc.set(maxDepth, minFunc.getY(minFunc.size()-1));
			depthMaxDASFunc.set(minDepth, maxFunc.getY(0));
			depthMaxDASFunc.set(maxDepth, maxFunc.getY(maxFunc.size()-1));
			
			// now convert to actual DAS, with any bias removed
			for (SimulatorElement elem : sectElems) {
				SubSectDAS_Record rawDAS = elemRawDASs.get(elem);
				
				Vertex[] verts = elem.getVertices();
				
				// last one is mid
				Location[] vertLocs = new Location[verts.length];
				double[] normVertDASs = new double[vertLocs.length];
				for (int i=0; i<vertLocs.length; i++) {
					double myRawDAS;
					if (i < verts.length) {
						vertLocs[i] = verts[i];
						myRawDAS = rawDAS.vertDASs[i];
					} else {
						vertLocs[i] = elem.getCenterLocation();
						myRawDAS = rawDAS.midDAS;
					}
					double depth = vertLocs[i].getDepth();
					
					double myMinDAS = depthMinDASFunc.getInterpolatedY(depth);
					double myMaxDAS = depthMaxDASFunc.getInterpolatedY(depth);
					double dasSpan = myMaxDAS - myMinDAS;
					
					normVertDASs[i] = (myRawDAS - myMinDAS) / dasSpan;
				}
				
				// map onto actual DAS
				double[] interpVertDASArray = new double[normVertDASs.length];
				for (int i=0; i<normVertDASs.length; i++) {
					double normDAS = normVertDASs[i];
					if (normDAS < dasXYZ.getMinX())
						normDAS = dasXYZ.getMinX();
					if (normDAS > dasXYZ.getMaxX())
						normDAS = dasXYZ.getMaxX();
					double normDepth = (vertLocs[i].getDepth()-minDepth)/(maxDepth-minDepth);
					if (normDepth < dasXYZ.getMinY())
						normDepth = dasXYZ.getMinY();
					if (normDepth > dasXYZ.getMaxY())
						normDepth = dasXYZ.getMaxY();
					double das = dasXYZ.bilinearInterpolation(normDAS, normDepth);
					interpVertDASArray[i] = das;
					Preconditions.checkState((float)das >= 0f && (float)das <= (float)sectLen,
							"Bad interp das=%s, not in range [0 %s]", das, sectLen);
				}
				double interpMidDAS = StatUtils.mean(interpVertDASArray);
				SubSectDAS_Record das;
//				try {
					das = new SubSectDAS_Record(interpVertDASArray, interpMidDAS);
//				} catch (RuntimeException e1) {
//					System.out.println("Row Func: "+dasFuncs[row]);
//					System.out.println("Normalized DASs: mid="+interp+", range=["+normDASArray[1]+" "+normDASArray[2]+"]");
//					System.out.println("Interpolated DASs: mid="+interpDASArray[0]+", range=["+interpDASArray[1]+" "+interpDASArray[2]+"]");
//					throw e1;
//				}
//				if (elem.getID() == 180899 || elem.getID() == 180886 || elem.getID() == 180845 || elem.getID() == 180940) {
//				if (elem.getID() == 78269 || elem.getID() == 78358 || elem.getID() == 78387) {
//				if (elem.getID() == 262957 || elem.getID() == 262969) {
//					System.out.println("Debug for "+elem.getID()+", sectID="+elem.getSectionID()+", mapped row="+row);
//					System.out.println("\traw DAS: "+rawDAS);
//					System.out.println("\tmy DAS Range: "+myMinDAS+" "+myMaxDAS);
//					System.out.println("\tnorm DAS: "+normDAS);
//					System.out.println("\tinterp DAS: "+das);
//				}
				elemSectDASs.put(elem, das);
			}
		}
		
		mappingsCache = CacheBuilder.newBuilder().maximumSize(1000).build(new CacheLoader<SimulatorEvent, List<List<SubSectionMapping>>>() {

			@Override
			public List<List<SubSectionMapping>> load(SimulatorEvent key) throws Exception {
				return loadSubSectionMappings(key);
			}
			
		});
	}
	
	private double calcStraightLineDAS(Location start, Location end, Location loc) {
		double distToLine = Math.abs(LocationUtils.distanceToLineFast(start, end, loc));
		double distToStart = LocationUtils.horzDistanceFast(start, loc);
		if (distToLine > distToStart) {
			Preconditions.checkState(Math.abs(distToLine - distToStart) < 0.1,
					"Bad triangle in straight line DAS calc. distToLine=%s, distToStart=%s", distToLine, distToStart);
			return 0d;
		}
		double ret = Math.sqrt(distToStart*distToStart - distToLine*distToLine);
		// now check to see if we're before the start
		double startEndAz = LocationUtils.azimuth(start, end);
		Location orthogonal = LocationUtils.location(start, Math.toRadians(startEndAz+90), distToStart);
		double distToOrthogonal = LocationUtils.distanceToLineFast(start, orthogonal, loc);
		if (distToOrthogonal > 0)
			// positive sign here means above the orthogonal line, and thus before the start
			ret = -ret;
//		if (distToSegment > 1.01*distToLine && distToStart < distToEnd) {
//			// we're before the start, das should be negative
//			ret = -ret;
//		}
//		System.out.println("distToLine="+distToLine+"\tdistToStart="+distToStart+"\tret="+ret);
		Preconditions.checkState(Double.isFinite(ret), "Non-finite straight line DAS. distToLine=%s, distToStart=%s", distToLine, distToStart);
		return ret;
	}
	
	public List<? extends FaultSection> getSubSections() {
		return subSects;
	}
	
	public boolean isMapped(FaultSection sect) {
		return sectsToElemsMap.containsKey(sect) && !sectsToElemsMap.get(sect).isEmpty();
	}
	
	public FaultSection getMappedSection(SimulatorElement element) {
		return elemToSectsMap.get(element);
	}
	
	public Collection<SimulatorElement> getElementsForSection(FaultSection sect) {
		return sectsToElemsMap.get(sect);
	}
	
	public SubSectDAS_Record getElemSubSectDAS(SimulatorElement element) {
		return elemSectDASs.get(element);
	}
	
	/**
	 * Calling this method will enable tracking of slip on each subsection. Slip will be tracked within a band of elements for which
	 * the center is >= minDepth and <= maxDepth. If faultDownDipBuffer is >0, then only elements whose center depth is at least
	 * faultDownDipBuffer away down dip from the top of the fault, or up dip from the bottom, will be included.
	 * 
	 * This method uses default values of all parameters, and will not reset things if custom paremters have already been set.
	 */
	public void trackSlipOnSections() {
		if (slipElemsToSectsMap == null)
			trackSlipOnSections(MID_SEIS_MIN_DEPTH_DEFAULT, MID_SEIS_MAX_DEPTH_DEFAULT, MID_SEIS_BUFFER_DEFAULT);
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
		this.minSlipDepth = minDepth;
		this.maxSlipDepth = maxDepth;
		this.faultDownDipBuffer = faultDownDipBuffer;
		
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
				double[] depthConstraint = { depthRange[0] + bufferVertical, depthRange[1] - bufferVertical };
//				if (depthConstraint[0] > depthConstraint[1]) {
//					System.out.println("WARNING: depth range invalid for section "+sectID+": "+subSects.get(sectID).getName());
//					System.out.println("\tAveDip of "+(float)aveDip+" leads to vertical "
//							+"buffer of "+bufferVertical+" km for "+faultDownDipBuffer+" km down-dip buffer.");
//					System.out.println("\tSection depth range: ["+depthConstraint[0]+" "+depthConstraint[1]+"]");
//					System.out.println("\tSection buffered: ["+depthRange[0]+" "+depthRange[1]+"]");
//					System.out.println("\tGlobal min/max: ["+minDepth+" "+maxDepth+"]");
//				}
				sectDDConstraints.put(sectID, depthConstraint);
			}
		}
		
		slipElemsToSectsMap = new HashMap<>();
		slipSectsToElemsMap = new HashMap<>();
		sectMidDepthConstraints = new HashMap<>();
		
		for (SimulatorElement elem : elements) {
			int sectID = elem.getSectionID() - minElemSectID;
			FaultSection subSect = subSects.get(sectID);
			
			double myMinDepth = minDepth;
			double myMaxDepth = maxDepth;
			if (sectDDConstraints != null) {
				double[] constr = sectDDConstraints.get(sectID);
				if (constr[0] > myMaxDepth || constr[1] < myMinDepth) {
					// we're completely above or below the global range, just use the section buffered and ignore global ranges
					myMinDepth = constr[0];
					myMaxDepth = constr[1];
				} else {
					myMinDepth = Math.max(myMinDepth, constr[0]);
					myMaxDepth = Math.min(myMaxDepth, constr[1]);
				}
			}
			sectMidDepthConstraints.put(subSect, new double[] {myMinDepth, myMaxDepth});
			
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
		for (FaultSection subSect : subSects) {
			if (!slipSectsToElemsMap.containsKey(subSect) ) {
//				System.out.println("WARNING: no slip elements for section: "+subSect.getName());
				slipSectsToElemsMap.put(subSect, new HashSet<>());
			}
		}
	}
	
	public double getMinSlipDepth() {
		return minSlipDepth;
	}

	public double getMaxSlipDepth() {
		return maxSlipDepth;
	}

	public double getFaultDownDipBuffer() {
		return faultDownDipBuffer;
	}

	public double[] getSlipOnSectionDepthConstraints(FaultSection sect) {
		Preconditions.checkNotNull(sectMidDepthConstraints, "Must enable slip on section tracking first");
		return sectMidDepthConstraints.get(sect);
	}
	
	public HashSet<SimulatorElement> getSlipSectionsForSect(FaultSection sect) {
		Preconditions.checkNotNull(sectMidDepthConstraints, "Must enable slip on section tracking first");
		return slipSectsToElemsMap.get(sect);
	}
	
	public void setMinFractForInclusion(double minFractForInclusion) {
		this.minFractForInclusion = minFractForInclusion;
	}
	
	public double getMinFractForInclusion() {
		return minFractForInclusion;
	}
	
	/**
	 * Returns an unmodifiable list of all subsection mappings for which at least one element slipped in the event. Sections are
	 * bundled by parent section (one sublist for each parent), and both bundles and mappings within bundles are sorted from end to end
	 * of the rupture.
	 * @param event
	 * @return parent section bundles list of all mappings
	 */
	public List<List<SubSectionMapping>> getAllSubSectionMappings(SimulatorEvent event) {
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
	public List<List<SubSectionMapping>> getFilteredSubSectionMappings(SimulatorEvent event) {
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
	
	private List<List<SubSectionMapping>> loadSubSectionMappings(SimulatorEvent event) {
		List<SubSectionMapping> mappings = new ArrayList<>();
		
		Map<FaultSection, SubSectionMapping> sectMap = new HashMap<>();
		
		List<SimulatorElement> elems = event.getAllElements();
		double[] slips = event.getAllElementSlips();
		
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem = elems.get(i);
			Preconditions.checkNotNull(elem, "Element is null??");
			double slip = slips.length > 0 ? slips[i] : Double.MIN_VALUE;
//			Preconditions.checkState(slip >= 0, "Bad slip=%s on element %s, event %s",
//					slip, elem.getID(), event.getID());
			FaultSection sect = elemToSectsMap.get(elem);
			Preconditions.checkNotNull(sect, "No section mapping for element %s with section named: %s", elem.getID(), elem.getSectionName());
			SubSectionMapping mapping = sectMap.get(sect);
			if (mapping == null) {
				mapping = new SubSectionMapping(sect);
				sectMap.put(sect, mapping);
				mappings.add(mapping);
			}
			mapping.addSlip(elem, slip);
		}
		
		// bundle by parent section id
		Map<Integer, List<FaultSection>> rupSectsBundled = Maps.newHashMap();
		for (SubSectionMapping mapping : mappings) {
			// convert to 0-based
			int parentID = mapping.subSect.getParentSectionId();
			List<FaultSection> sects = rupSectsBundled.get(parentID);
			if (sects == null) {
				sects = new ArrayList<>();
				rupSectsBundled.put(parentID, sects);
			}
			sects.add(mapping.subSect);
		}
		
		// sort each bundle by section index
		List<List<FaultSection>> rupSectsListBundled = Lists.newArrayList();
		for (List<FaultSection> sects : rupSectsBundled.values()) {
			Collections.sort(sects, sectIDCompare);
			rupSectsListBundled.add(sects);
		}

		// sort bundles (put faults in order from one end to the other)
		HashSet<FaultSection> reversedSections = new HashSet<>();
		if (rupSectsListBundled.size() > 1)
			rupSectsListBundled = SimulatorFaultSystemSolution.sortRupture(subSects, rupSectsListBundled, distsCache, reversedSections);
		
		// build return list
		List<List<SubSectionMapping>> ret = new ArrayList<>();
		for (List<FaultSection> bundle : rupSectsListBundled) {
			List<SubSectionMapping> bundleMappings = new ArrayList<>();
			for (FaultSection sect : bundle) {
				SubSectionMapping mapping = sectMap.get(sect);
				mapping.setReversed(reversedSections.contains(sect));
				bundleMappings.add(mapping);
			}
			ret.add(Collections.unmodifiableList(bundleMappings));
		}
		
		return Collections.unmodifiableList(ret);
	}
	
	private static Comparator<FaultSection> sectIDCompare = new Comparator<FaultSection>() {

		@Override
		public int compare(FaultSection o1, FaultSection o2) {
			return Integer.valueOf(o1.getSectionId()).compareTo(o2.getSectionId());
		}
	};
	
	public class SubSectionMapping {
		private FaultSection subSect;
		private double areaSlipped;
		private double subSectArea;
		private double momentOnSect;
		
		private double minDAS = Double.POSITIVE_INFINITY;
		private double maxDAS = Double.NEGATIVE_INFINITY;
		
		private double minSlipRegionDAS = Double.POSITIVE_INFINITY;
		private double maxSlipRegionDAS = Double.NEGATIVE_INFINITY;
		private Map<SimulatorElement, Double> elemSlipsInSlipRegion;
		
		private double minSurfaceSlipDAS = Double.POSITIVE_INFINITY;
		private double maxSurfaceSlipDAS = Double.NEGATIVE_INFINITY;
		
		private boolean reversed;
		
		private SubSectionMapping(FaultSection subSect) {
			this.subSect = subSect;
			this.subSectArea = subSectAreas.get(subSect.getSectionId());
			
			if (slipSectsToElemsMap != null)
				elemSlipsInSlipRegion = new HashMap<>();
		}
		
		private void setReversed(boolean reversed) {
			this.reversed = reversed;
		}
		
		public boolean isReversed() {
			return reversed;
		}
		
		protected void addSlip(SimulatorElement elem, double slip) {
			if (slip <= 0d)
				return;
			double area = elem.getArea();
			Preconditions.checkState(area > 0, "Zero area!");
			areaSlipped += area;
			momentOnSect += FaultMomentCalc.getMoment(area, slip);
			DAS_Record das = elemSectDASs.get(elem);
			minDAS = Math.min(das.startDAS, minDAS);
			maxDAS = Math.max(das.endDAS, maxDAS);
			
			if (slipSectsToElemsMap != null) {
				// track slips
				if (slipSectsToElemsMap.get(subSect).contains(elem)) {
					// this element is inside the slip region
					minSlipRegionDAS = Math.min(das.startDAS, minSlipRegionDAS);
					maxSlipRegionDAS = Math.max(das.endDAS, maxSlipRegionDAS);
					elemSlipsInSlipRegion.put(elem, slip);
				}
				int numSurface = 0;
				for (Location loc : elem.getVertices())
					if ((float)loc.getDepth() == 0f)
						numSurface++;
				if (numSurface > 1) {
					minSurfaceSlipDAS = Math.min(minSurfaceSlipDAS, das.startDAS);
					maxSurfaceSlipDAS = Math.max(maxSurfaceSlipDAS, das.endDAS);
				}
			}
		}

		public FaultSection getSubSect() {
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
		
		public DAS_Record getDASforSlip(SlipAlongSectAlgorithm type) {
			Preconditions.checkState(slipElemsToSectsMap != null, "Must enable slip tracking with trackSlipOnSections(...)");
			switch (type) {
			case MID_SEIS_FULL_SUBSECTION_LEN:
				return new DAS_Record(0d, subSect.getFaultTrace().getTraceLength());
			case MID_SEIS_SURF_SLIP_LEN:
				if (Double.isFinite(minSurfaceSlipDAS))
					return new DAS_Record(minSurfaceSlipDAS, maxSurfaceSlipDAS);
				return null;
			case MID_SEIS_SLIPPED_LEN:
				if (Double.isFinite(minDAS))
					return new DAS_Record(minDAS, maxDAS);
				return null;
			case MID_SEIS_MID_SLIPPED_LEN:
				if (Double.isFinite(minSlipRegionDAS))
					return new DAS_Record(minSlipRegionDAS, maxSlipRegionDAS);
				return null;
			default:
				throw new IllegalStateException("Unsupported type: "+type);
			}
		}
		
		public double getLengthForSlip(SlipAlongSectAlgorithm type) {
			DAS_Record record = getDASforSlip(type);
			if (record == null)
				return 0d;
			return record.endDAS - record.startDAS;
		}
		
		public List<SimulatorElement> getSlipCalcElements(SlipAlongSectAlgorithm type) {
			return getSlipCalcElements(getDASforSlip(type));
		}
		
		public List<SimulatorElement> getSlipCalcElements(DAS_Record slipDAS) {
			if (slipDAS == null)
				return null;
			Preconditions.checkNotNull(slipSectsToElemsMap, "Must enable slip tracking with trackSlipOnSections(...)");
			List<SimulatorElement> elems = new ArrayList<>();
			for (SimulatorElement elem : slipSectsToElemsMap.get(subSect)) {
				if (slipDAS.contains(elemSectDASs.get(elem).midDAS))
					elems.add(elem);
			}
			return elems;
		}
		
		public double getAreaForAverageSlip(SlipAlongSectAlgorithm type) {
			return getAreaForAverageSlip(getDASforSlip(type));
		}
		
		public double getAreaForAverageSlip(DAS_Record slipDAS) {
			List<SimulatorElement> slipElems = getSlipCalcElements(slipDAS);
			if (slipElems == null)
				return 0d;
			
			double totArea = 0d;
			for (SimulatorElement elem : slipElems)
				totArea += elem.getArea();
			
			return totArea;
		}
		
		public double getAverageSlip(SlipAlongSectAlgorithm type) {
			return getAverageSlip(getDASforSlip(type));
		}
		
		public double getAverageSlip(DAS_Record slipDAS) {
			List<SimulatorElement> slipElems = getSlipCalcElements(slipDAS);
			if (slipElems == null || slipElems.isEmpty())
				return 0d;
			
			double areaWeightedSlipInSlipRegion = 0d;
			double totArea = 0d;
			for (SimulatorElement elem : slipElems) {
				double area = elem.getArea();
				totArea += area;
				if (elemSlipsInSlipRegion.containsKey(elem))
					areaWeightedSlipInSlipRegion += area * elemSlipsInSlipRegion.get(elem);
			}
			Preconditions.checkState(totArea > 0);
			return areaWeightedSlipInSlipRegion/totArea;
		}
	}
	
	private void debugPlotDAS(File outputDir, int parentSectID) throws IOException {
		List<FaultSection> parentSects = new ArrayList<>();
		String parentName = null;
		for (FaultSection sect : subSects) {
			if (sect.getParentSectionId() == parentSectID) {
				parentSects.add(sect);
				parentName = sect.getParentSectionName();
			}
		}
		Preconditions.checkState(!parentSects.isEmpty());
		List<SimulatorElement> mappedElements = new ArrayList<>();
		List<RuptureSurface> sectSurfs = new ArrayList<>();
		List<Double> mappedIDs = new ArrayList<>();
		for (FaultSection sect : parentSects) {
			mappedElements.addAll(sectsToElemsMap.get(sect));
			sectSurfs.add(sect.getFaultSurface(1d, false, false));
			for (int i=0; i<sectsToElemsMap.get(sect).size(); i++)
				mappedIDs.add((double)sect.getSectionId());
		}
		
		CompoundSurface surfaceToOutline = new CompoundSurface(sectSurfs);
		
		double[] dasScalars = new double[mappedElements.size()];
		
		String prefix = "das_debug_"+parentName.replaceAll("\\W+", "");
		
		// first raw das
		for (int i=0; i<dasScalars.length; i++)
			dasScalars[i] = elemRawDASs.get(mappedElements.get(i)).midDAS;
		
		CPT elemCPT = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0d, StatUtils.max(dasScalars));
		
		RupturePlotGenerator.writeMapPlot(elements, null, null, outputDir, prefix+"_raw_das", null, null, surfaceToOutline,
				mappedElements, dasScalars, elemCPT, parentName+" Raw DAS", null);
		
		// now mid das
		for (int i=0; i<dasScalars.length; i++)
			dasScalars[i] = elemSectDASs.get(mappedElements.get(i)).midDAS;
		
		elemCPT = elemCPT.rescale(0d, StatUtils.max(dasScalars));
		
		RupturePlotGenerator.writeMapPlot(elements, null, null, outputDir, prefix+"_mid_das", null, null, surfaceToOutline,
				mappedElements, dasScalars, elemCPT, parentName+" Mid DAS", null);
		
		// now sect
		dasScalars = Doubles.toArray(mappedIDs);
		
		elemCPT = elemCPT.rescale(StatUtils.min(dasScalars), StatUtils.max(dasScalars));
		
		RupturePlotGenerator.writeMapPlot(elements, null, null, outputDir, prefix+"_sect_mappings", null, null, surfaceToOutline,
				mappedElements, dasScalars, elemCPT, parentName+" Section Mappings", null);
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/data/kevin/simulators/catalogs/rundir2585_1myr");
//		File dir = new File("/data/kevin/simulators/catalogs/bruce/rundir3165");
		File geomFile = new File(dir, "zfault_Deepen.in");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		List<? extends FaultSection> subSects = RSQSimUtils.getUCERF3SubSectsForComparison(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		RSQSimSubSectionMapper mapper = new RSQSimSubSectionMapper(subSects, elements, 0.2);
		mapper.debugPlotDAS(new File("/tmp"), 142);
		mapper.debugPlotDAS(new File("/tmp"), 151);
		System.exit(0);
		int maxNum = 10;
		double minMag = 6.5;
		int count = 0;
		for (RSQSimEvent event : RSQSimFileReader.getEventsIterable(dir, elements,
				Lists.newArrayList(new MagRangeRuptureIdentifier(minMag, Double.POSITIVE_INFINITY)))) {
			List<List<SubSectionMapping>> allMappings = mapper.getAllSubSectionMappings(event);
			List<List<SubSectionMapping>> filteredMappings = mapper.getFilteredSubSectionMappings(event);
			HashSet<SubSectionMapping> allFiltered = new HashSet<>();
			for (List<SubSectionMapping> bundle : filteredMappings)
				allFiltered.addAll(bundle);
			boolean hasAll = true;
			System.out.println("Event with M="+event.getMagnitude());
			for (List<SubSectionMapping> bundle : allMappings) {
				for (SubSectionMapping mapping : bundle) {
					int sectID = mapping.getSubSect().getSectionId();
					double areaSlipped = mapping.getAreaSlipped();
					double totArea = mapper.subSectAreas.get(sectID);
					double fract = areaSlipped / totArea;
					System.out.println("\tSect "+sectID+": fractSlipped = "+(float)areaSlipped
							+" / "+(float)totArea+" = "+(float)fract);
					boolean included = allFiltered.contains(mapping);
					hasAll = hasAll && included;
					System.out.println("\t\tIncluded? "+included);
				}
			}
			count++;
			System.out.println();
			if (count == maxNum)
				break;
		}
	}

}
