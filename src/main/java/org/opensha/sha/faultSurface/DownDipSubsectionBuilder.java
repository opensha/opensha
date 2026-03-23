package org.opensha.sha.faultSurface;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.util.FaultUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

public class DownDipSubsectionBuilder {
	
	public static GeoJSONFaultSection[][] buildForConstantCountAlong(GeoJSONFaultSection parentSect,
			List<FaultTrace> depthTraces, int minNumAlong, double maxSubSectionLen, int startID) {
		double maxTraceLen = 0d;
		for (FaultTrace trace : depthTraces)
			maxTraceLen = Math.max(maxTraceLen, trace.getTraceLength());
		
		int numAlong = getNumAlong(maxTraceLen, maxSubSectionLen, minNumAlong, true);
		return buildForConstantCountAlong(parentSect, depthTraces, numAlong, startID);
	}
	
	private static int getNumAlong(double traceLength, double targetSubSectionLen, int minSubSections, boolean targetIsMax) {
		Preconditions.checkState(minSubSections > 0, "Minimum subsection count must be positive");
		Preconditions.checkState(targetSubSectionLen > 0, "Target subsection length must be positive");
		Preconditions.checkState(traceLength > 0d, "Trace length must be positive");
		// find the number of sub sections
		double avgNum = traceLength/targetSubSectionLen;
		int numSubSections;
		if (Math.floor(avgNum)!=avgNum) {
			if (targetIsMax)
				numSubSections = (int)Math.floor(avgNum)+1;
			else
				numSubSections = (int)Math.round(avgNum);
		} else {
			numSubSections = (int)avgNum;
		}
		if (numSubSections < minSubSections)
			numSubSections = minSubSections;
		return numSubSections;
	}
	
	public static GeoJSONFaultSection[][] buildForConstantCountAlong(GeoJSONFaultSection parentSect,
			List<FaultTrace> depthTraces, int numAlong, int startID) {
		Preconditions.checkState(numAlong >= 1, "NumAlong must be >=1: %s", numAlong);
		Preconditions.checkState(depthTraces.size() > 2, "Must supply at least 3 traces to build subsections down-dip");
		validateTraceList(depthTraces);
		
		int rows = depthTraces.size()-1;
		
		GeoJSONFaultSection[][] ret = new GeoJSONFaultSection[rows][];
		
		int overallID = startID;
		int overallIndex = 0;
		for (int row=0; row<rows; row++) {
			FaultTrace rowUpper = depthTraces.get(row);
			FaultTrace rowLower = depthTraces.get(row+1);
			
			ret[row] = buildRow(parentSect, rowUpper, rowLower, overallID, overallIndex, row, numAlong);
			overallID += numAlong;
			overallIndex += numAlong;
		}
		
		return ret;
	}
	
	public static GeoJSONFaultSection[][] buildForConstantLength(GeoJSONFaultSection parentSect,
			List<FaultTrace> depthTraces, int minNumAlong, double targetSubSectionLen, boolean targetIsMax, int startID) {
		Preconditions.checkState(depthTraces.size() > 2, "Must supply at least 3 traces to build subsections down-dip");
		validateTraceList(depthTraces);
		
		int rows = depthTraces.size()-1;
		
		GeoJSONFaultSection[][] ret = new GeoJSONFaultSection[rows][];
		
		int overallID = startID;
		int overallIndex = 0;
		for (int row=0; row<rows; row++) {
			FaultTrace rowUpper = depthTraces.get(row);
			FaultTrace rowLower = depthTraces.get(row+1);
			
			int numAlong = getNumAlong(rowUpper.getTraceLength(), targetSubSectionLen, minNumAlong, targetIsMax);
			
			ret[row] = buildRow(parentSect, rowUpper, rowLower, overallID, overallIndex, row, numAlong);
			overallID += numAlong;
			overallIndex += numAlong;
		}
		
		return ret;
	}
	
	private static GeoJSONFaultSection[] buildRow(GeoJSONFaultSection parentSect, FaultTrace rowUpper, FaultTrace rowLower,
			int startID, int startIndex, int rowIndex, int numAlong) {
		List<FaultTrace[]> rowTraces;
		if (numAlong == 1) {
			rowTraces = new ArrayList<>(1);
			rowTraces.add(new FaultTrace[] {rowUpper, rowLower});
		} else {
			rowTraces = FaultUtils.getEqualLengthSubsectionTraces(rowUpper, rowLower, numAlong);
		}
		Preconditions.checkState(rowTraces.size() == numAlong);
		
		GeoJSONFaultSection[] row = new GeoJSONFaultSection[numAlong];
		for (int i=0; i<numAlong; i++) {
			FaultTrace[] traces = rowTraces.get(i);
			FaultTrace upper = traces[0];
			FaultTrace lower = traces[1];
			
			row[i] = initSubSect(parentSect, upper, lower, startID+i, startIndex+i, i, rowIndex);
		}
		return row;
	}
	
	private static void validateTraceList(List<FaultTrace> depthTraces) {
		// make sure they are all >0 length
		for (int i=0; i<depthTraces.size(); i++) {
			FaultTrace trace = depthTraces.get(i);
			Preconditions.checkState(trace.size() > 1, "Trace must have at least 2 locations, trace %s has %s",
					i, trace.size());
			double length = trace.getTraceLength();
			Preconditions.checkState(length > 0d, "Trace length must be >0, trace %s is %s",
					i, Double.valueOf(length));
		}
		
		// make sure they get deeper
		double prevDepth = avgDepth(depthTraces.get(0));
		for (int i=1; i<depthTraces.size(); i++) {
			FaultTrace trace = depthTraces.get(i);
			double depth = avgDepth(trace);
			Preconditions.checkState(depth > prevDepth,
					"Traces must be provided in depth-sorted order from shallowest to deepest. "
					+ "Trace %s avgDepth=%s, trace %s avgDepth=%s",
					i, depth, i-1, prevDepth);
			prevDepth = depth;
		}
	}
	
	private static double avgDepth(FaultTrace trace) {
		double sum = 0d;
		for (Location loc : trace)
			sum += loc.depth;
		return sum/(double)trace.size();
	}
	
	private static GeoJSONFaultSection initSubSect(GeoJSONFaultSection refSect, FaultTrace upper, FaultTrace lower,
			int sectID, int index, int indexAlong, int indexDownDip) {
		String sectName = refSect.getName()+GeoJSONFaultSection.STANDARD_SUBSECTION_PREFIX+index+" ("+indexDownDip+", "+indexAlong+")";
		
		Feature feature = refSect.toFeature();
		// clone properties
		FeatureProperties props = new FeatureProperties(feature.properties);
		props.set(GeoJSONFaultSection.FAULT_ID, sectID);
		props.set(GeoJSONFaultSection.FAULT_NAME, sectName);
		props.set(GeoJSONFaultSection.PARENT_ID, refSect.getSectionId());
		props.set(GeoJSONFaultSection.PARENT_NAME, refSect.getSectionName());
		props.set(GeoJSONFaultSection.SUB_SECT_INDEX, index);
		props.set(GeoJSONFaultSection.SUB_SECT_INDEX_ALONG, indexAlong);
		props.set(GeoJSONFaultSection.SUB_SECT_INDEX_DOWN, indexDownDip);
		
		Geometry geom = new Geometry.MultiLineString(List.of(upper, lower));
		
		// calculate dip and depths
		double midLength = 0.5*(upper.getTraceLength() + lower.getTraceLength());
		int numRough = Integer.max(20, (int)midLength);
		ApproxEvenlyGriddedSurface roughSurf = new ApproxEvenlyGriddedSurface(upper, lower,
				midLength/(double)numRough, false, 0d);
		double dip = roughSurf.getAveDip();
		double dipDir = roughSurf.getAveDipDirection();
		props.set(GeoJSONFaultSection.DIP, dip);
		props.set(GeoJSONFaultSection.DIP_DIR, dipDir);
		props.set(GeoJSONFaultSection.UPPER_DEPTH, roughSurf.getAveRupTopDepth());
		props.set(GeoJSONFaultSection.LOW_DEPTH, roughSurf.getAveRupBottomDepth());
		
		Feature subsectFeature = new Feature(sectID, geom, props);
		
		return GeoJSONFaultSection.fromFeature(subsectFeature);
	}
	
	private static double DEPTH_REVERSAL_TOL = 0.5d; // km
	
	public static List<FaultTrace> interpolateDepthTraces(List<FaultTrace> depthTraces, int minNumDownDip,
			double targetDownDipWidth, boolean targetIsMax, Range<Double> depthRange) {
		Preconditions.checkState(depthTraces.size() > 1, "Must supply at least 2 depth traces for interpolation");
		validateTraceList(depthTraces);
		
		String namePrefix = depthTraces.get(0).getName();
		if (namePrefix == null || namePrefix.isBlank())
			namePrefix = "";
		else
			namePrefix += ", ";
		
		boolean topFullyPreserved = true;
		if (depthRange != null) {
			// make sure we have data in the given range
			// also see if we can skip any depth traces; only need the ones immediately bracketing the valid depth range
			double minDepth = depthRange.lowerEndpoint(); // lower value = upper depth
			double maxDepth = depthRange.upperEndpoint(); // upper value = lower depth
			
			boolean[] aboves = new boolean[depthTraces.size()];
			boolean[] belows = new boolean[depthTraces.size()];

			int numFullyAbove = 0;
			int numFullyBelow = 0;
			for (int i=0; i<depthTraces.size(); i++) {
				aboves[i] = true;
				belows[i] = true;
				for (Location loc : depthTraces.get(i)) {
					if (loc.depth >= minDepth)
						aboves[i] = false;
					if (loc.depth <= maxDepth)
						belows[i] = false;
				}
				Preconditions.checkState(!aboves[i] || !belows[i]);
				if (aboves[i])
					numFullyAbove++;
				if (belows[i])
					numFullyBelow++;
			}
			Preconditions.checkState(numFullyAbove < depthTraces.size(),
					"All %s depth traces are fully above the allowable depth range: %s", numFullyAbove, depthRange);
			Preconditions.checkState(numFullyBelow < depthTraces.size(),
					"All %s depth traces are fully below the allowable depth range: %s", numFullyBelow, depthRange);
			
			// if top trace is fully below the upper depth limit, it will will be preserved
			for (Location loc : depthTraces.get(0)) {
				if (loc.depth < minDepth) {
					topFullyPreserved = false;
					break;
				}
			}
			
			if (numFullyAbove > 1)
				// have multiple above it, can trim
				// keep the last one that is fully above
				depthTraces = depthTraces.subList(numFullyAbove-1, depthTraces.size());
			if (numFullyBelow > 1)
				// have multiple below it, can trim
				// keep the first one that is fully below
				depthTraces = depthTraces.subList(0, depthTraces.size()-(numFullyBelow-1));
		}
		
		double maxTraceLen = 0d;
		for (FaultTrace trace : depthTraces)
			maxTraceLen = Math.max(maxTraceLen, trace.getTraceLength());
		
		int numSamplesAlong;
		if (maxTraceLen > 500d)
			// really long, 5km spacing
			numSamplesAlong = (int)Math.ceil(maxTraceLen / 5d);
		else if (maxTraceLen > 100d)
			// intermediate, 1-5km spacing
			numSamplesAlong = 100;
		else
			// 1km spacing (min 10 pts)
			numSamplesAlong = Integer.max(10, (int)Math.ceil(maxTraceLen));
		List<FaultTrace> resampledTraces = new ArrayList<>(depthTraces.size());
		for (FaultTrace trace : depthTraces) {
			FaultTrace resampled = FaultUtils.resampleTrace(trace, numSamplesAlong-1);
			Preconditions.checkState(resampled.size() == numSamplesAlong);
			resampledTraces.add(resampled);
		}
		
		double maxBoundedDDW = 0d;
		DiscretizedFunc[] depthWidthFuncs = new DiscretizedFunc[numSamplesAlong];
		double[] boundedMinDepths = new double[numSamplesAlong];
		double[] boundedMaxDepths = new double[numSamplesAlong];
		double[] boundedDDWs = new double[numSamplesAlong];
		for (int i=0; i<numSamplesAlong; i++) {
			depthWidthFuncs[i] = new ArbitrarilyDiscretizedFunc();
			Location prevLoc = null;
			double ddw = 0d;
			for (int d=0; d<resampledTraces.size(); d++) {
				FaultTrace trace = resampledTraces.get(d);
				Location loc = trace.get(i);
				if (d == 0) {
					depthWidthFuncs[i].set(loc.depth, 0d);
				} else if (prevLoc.depth >= loc.depth) {
					Preconditions.checkState(Precision.equals(prevLoc.depth, loc.depth, DEPTH_REVERSAL_TOL),
							"Depths must monotonically increase (within tol=%s); resampled trace %s[%s]=%s, previous=%s",
							(float)DEPTH_REVERSAL_TOL, d, i, (float)loc.depth, (float)prevLoc.depth);
					// skip this point
				} else {
					// this distance can be exaggerated due to skewness
					double dist0 = LocationUtils.linearDistanceFast(prevLoc, loc);
					// find the smallest nearby distance
					int maxFailuresToQuit = 20;
					int numFailures = 0;
					double minForwardDist = dist0;
					for (int index=i+1; index<numSamplesAlong && numFailures<maxFailuresToQuit; index++) {
						double dist = LocationUtils.linearDistanceFast(prevLoc, trace.get(index));
						if (dist < minForwardDist) {
							minForwardDist = dist;
							numFailures = 0;
						} else {
							numFailures++;
						}
					}
					numFailures = 0;
					double minBackwardDist = dist0;
					for (int index=i-1; --index>=0 && numFailures<maxFailuresToQuit;) {
						double dist = LocationUtils.linearDistanceFast(prevLoc, trace.get(index));
						if (dist < minBackwardDist) {
							minBackwardDist = dist;
							numFailures = 0;
						} else {
							numFailures++;
						}
					}
					ddw += Math.min(minForwardDist, minBackwardDist);
//					System.out.println("Dist from "+prevLoc+" -> "+loc+": "+(float)dist+";\tddw="+(float)ddw);
					depthWidthFuncs[i].set(loc.depth, ddw);
				}
				prevLoc = loc;
			}
			
			double myMinDepth = depthWidthFuncs[i].getMinX();
			double myMaxDepth = depthWidthFuncs[i].getMaxX();
			double boundedDDW = ddw;
			
			boundedMinDepths[i] = myMinDepth;
			boundedMaxDepths[i] = myMaxDepth;
			if (depthRange != null) {
				Preconditions.checkState(myMinDepth <= depthRange.upperEndpoint() && myMaxDepth >= depthRange.lowerEndpoint(),
						"No valid traces inside depth range at along sample index %s; myMin=%s, myMax=%s, range=%s",
						myMinDepth, myMaxDepth, depthRange);
				if (!depthRange.contains(myMinDepth) || !depthRange.contains(myMaxDepth)) {
					// we extend beyond
					boundedMinDepths[i] = myMinDepth < depthRange.lowerEndpoint() ? depthRange.lowerEndpoint() : myMinDepth;
					boundedMaxDepths[i] = myMaxDepth > depthRange.upperEndpoint() ? depthRange.upperEndpoint() : myMaxDepth;
					boundedDDW = depthWidthFuncs[i].getInterpolatedY(boundedMaxDepths[i]) - depthWidthFuncs[i].getInterpolatedY(boundedMinDepths[i]);
				}
			}
			maxBoundedDDW = Math.max(maxBoundedDDW, boundedDDW);
			boundedDDWs[i] = boundedDDW;
			if (i == 0) {
				System.out.println("DEBUG "+i);
				System.out.println("\tDDW="+(float)ddw);
				System.out.println("\tboundedDDW="+(float)boundedDDW);
				System.out.println("\tboundedDepths=["+(float)boundedMinDepths[i]+", "+(float)boundedMaxDepths[i]+")");
//				System.out.println(depthWidthFuncs[i]);
//				System.exit(0);
			}
		}
		double fullDDW = targetIsMax ? maxBoundedDDW : StatUtils.mean(boundedDDWs);
		int numRows = getNumAlong(fullDDW, targetDownDipWidth, minNumDownDip, targetIsMax);
		System.out.println("NumRows="+numRows+" for ddw="+(float)fullDDW
				+" and target="+(float)targetDownDipWidth+" (max? "+targetIsMax+")");
		int numInterpTraces = numRows+1;
		
		List<FaultTrace> interpTraces = new ArrayList<>(numInterpTraces);
		for (int i=0; i<numInterpTraces; i++)
			interpTraces.add(new FaultTrace(null, numSamplesAlong));
		
		double[][] targetDepths = new double[numInterpTraces][numSamplesAlong];
		
		for (int i=0; i<numSamplesAlong; i++) {
			double myMinDepth = boundedMinDepths[i];
			double myMaxDepth = boundedMaxDepths[i];
			double ddwStart = depthWidthFuncs[i].getInterpolatedY(myMinDepth);
			double ddwEach = boundedDDWs[i]/(double)numRows;
			for (int d=0; d<numInterpTraces; d++) {
				double ddw = ddwStart + ddwEach*d;
				if (Precision.equals(ddw, 0d, 1e-10))
					targetDepths[d][i] = depthWidthFuncs[i].getMinX();
				else if (Precision.equals(ddw, depthWidthFuncs[i].getMaxY(), 1e-10))
					targetDepths[d][i] = depthWidthFuncs[i].getMaxX();
				else
					targetDepths[d][i] = depthWidthFuncs[i].getFirstInterpolatedX(ddw);
			}
			
		}
		
		for (int i=0; i<numSamplesAlong; i++) {
			// now interpolate those locations
			for (int d=0; d<numInterpTraces; d++) {
				double targetDepth = targetDepths[d][i];
				Location lastLocAbove = null;
				Location firstLocBelow = null;
				for (FaultTrace trace : resampledTraces) {
					Location loc = trace.get(i);
					if (loc.depth <= targetDepth) {
						lastLocAbove = loc;
					}
					if (loc.depth >= targetDepth) {
						firstLocBelow = loc;
						break;
					}
				}
				Preconditions.checkNotNull(lastLocAbove, "No location found at Z<=%s for sample %s depth %s with range [%s, %s]; topLoc=%s; \n%s",
						targetDepth, i, d, boundedMinDepths[i], boundedMaxDepths[i], resampledTraces.get(0).get(i), depthWidthFuncs[i]);
				Preconditions.checkNotNull(firstLocBelow, "No location found at Z>=%s for sample %s depth %s with range [%s, %s];topLoc=%s; \n%s",
						targetDepth, i, d, boundedMinDepths[i], boundedMaxDepths[i], resampledTraces.get(0).get(i), depthWidthFuncs[i]);
				if (lastLocAbove == firstLocBelow) {
					Preconditions.checkState(targetDepth == lastLocAbove.depth);
					interpTraces.get(d).add(lastLocAbove);
				} else {
					double delta = (targetDepth - lastLocAbove.depth)/(firstLocBelow.depth - lastLocAbove.depth);
					Preconditions.checkState(delta >= 0d && delta <= 1d);
					LocationVector vector = LocationUtils.vector(lastLocAbove, firstLocBelow);
					vector.setHorzDistance(vector.getHorzDistance()*delta);
					vector.setVertDistance(vector.getVertDistance()*delta);
					Location loc = LocationUtils.location(lastLocAbove, vector);
					interpTraces.get(d).add(loc);
				}
			}
		}
		
		if (topFullyPreserved) {
			// replace the top trace with the original
			System.out.println("Top trace is fully retained, keeping at original resolution");
			interpTraces.set(0, depthTraces.get(0));
		}
		
		DecimalFormat oDF = new DecimalFormat("0.#");
		for (int i=0; i<interpTraces.size(); i++) {
			FaultTrace trace = interpTraces.get(i);
			double avgDepth = trace.stream().mapToDouble((L) -> L.getDepth()).average().getAsDouble();
			trace.setName(namePrefix+"interpolated "+i+", avgZ="+oDF.format(avgDepth));
		}
		
		return interpTraces;
	}

}
