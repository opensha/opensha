package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

/**
 * This class maps minisections (straight spans between trace locations on a fault section) to subsections.
 * 
 * Note that, here, all minisection indexes are 0-based. Some data files use 1-based minisection indexes, and should be
 * converted externally.
 * 
 * @author kevin
 *
 */
public class MinisectionMappings {

	private Map<Integer, FaultSection> sectsByID;
	
	private int[] subSectMinisectionAssociationNum;
	private boolean[][] subSectMinisectionAssociations;
	private double[][] subSectMinisectionLengths;
	private double[][] subSectMinisectionFracts;
	private double[] subSectLengths;
	
	public MinisectionMappings(List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects) {
		sectsByID = new HashMap<>();
		for (FaultSection fullSect : fullSects) {
			Preconditions.checkState(!sectsByID.containsKey(fullSect.getSectionId()));
			sectsByID.put(fullSect.getSectionId(), fullSect);
		}
		
		subSectMinisectionAssociationNum = new int[subSects.size()];
		subSectMinisectionAssociations = new boolean[subSects.size()][];
		subSectMinisectionLengths = new double[subSects.size()][];
		subSectMinisectionFracts = new double[subSects.size()][];
		subSectLengths = new double[subSects.size()];
		
		for (int s=0; s<subSects.size(); s++) {
			FaultSection subSect = subSects.get(s);
			int parentID = subSect.getParentSectionId();
			FaultSection parentSect = sectsByID.get(parentID);
			
			// subsection tract
			FaultTrace subTrace = subSect.getFaultTrace();
			Preconditions.checkState(subTrace.size()>1, "sub section trace only has one point!!!!");
			Location subStart = subTrace.get(0);
			Location subEnd = subTrace.get(subTrace.size()-1);

			FaultTrace trace = parentSect.getFaultTrace();

			// this is the index of the trace point that is either before or equal to the start of the sub section
			int traceIndexBefore = -1;
			// this is the index of the trace point that is either after or exactly at the end of the sub section
			int traceIndexAfter = -1;
			
			// now see if there are any trace points in between the start and end of the sub section
			for (int i=0; i<trace.size(); i++) {
				// loop over section trace. we leave out the first and last point because those are
				// by definition end points and are already equal to sub section start/end points
				Location tracePt = trace.get(i);

				if (isBefore(subStart, subEnd, tracePt)) {
//					if (DD) System.out.println("Trace "+i+" is BEFORE");
					traceIndexBefore = i;
				} else if (isAfter(subStart, subEnd, tracePt)) {
					// we want just the first index after, so we break
//					if (DD) System.out.println("Trace "+i+" is AFTER");
					traceIndexAfter = i;
					break;
				} else {
					//								if (DD) System.out.println("Trace "+i+" must be BETWEEN");
				}
			}
			Preconditions.checkState(traceIndexBefore >= 0, "trace index before not found!");
			Preconditions.checkState(traceIndexAfter > traceIndexBefore, "trace index after not found!");
			
			int numMinisections = trace.size()-1;
			
			subSectMinisectionAssociations[s] = new boolean[numMinisections];
			subSectMinisectionLengths[s] = new double[numMinisections];
			
			for (int i=traceIndexBefore; i<traceIndexAfter; i++) {
				subSectMinisectionAssociations[s][i] = true;
				Location start, end;
				if (i == traceIndexBefore)
					start = subStart;
				else
					start = trace.get(i);
				if (i == traceIndexAfter-1)
					end = subEnd;
				else
					end = trace.get(i+1);
				subSectMinisectionLengths[s][i] = LocationUtils.horzDistanceFast(start, end);
				subSectLengths[s] += subSectMinisectionLengths[s][i];
				subSectMinisectionAssociationNum[s]++;
			}
			subSectMinisectionFracts[s] = new double[numMinisections];
			for (int i=0; i<subSectMinisectionFracts[s].length; i++)
				if (subSectMinisectionAssociations[s][i])
					subSectMinisectionFracts[s][i] = subSectMinisectionLengths[s][i]/subSectLengths[s];
		}
	}
	
	/**
	 * Determines if the given point, pt, is before or equal to the start point. This is
	 * done by determining that pt is closer to start than end, and is further from end
	 * than start is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isBefore(Location start, Location end, Location pt) {
		if (start.equals(pt) || LocationUtils.areSimilar(start, pt))
			return true;
		double pt_start_dist = LocationUtils.horzDistanceFast(pt, start);
		if (pt_start_dist == 0)
			return true;
		double pt_end_dist = LocationUtils.horzDistanceFast(pt, end);
		double start_end_dist = LocationUtils.horzDistanceFast(start, end);

		return pt_start_dist < pt_end_dist && pt_end_dist > start_end_dist;
	}

	/**
	 * Determines if the given point, pt, is after or equal to the end point. This is
	 * done by determining that pt is closer to end than start, and is further from start
	 * than end is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isAfter(Location start, Location end, Location pt) {
		if (end.equals(pt) || LocationUtils.areSimilar(end, pt))
			return true;
		double pt_end_dist = LocationUtils.horzDistanceFast(pt, end);
		if (pt_end_dist == 0)
			return true;
		double pt_start_dist = LocationUtils.horzDistanceFast(pt, start);
		double start_end_dist = LocationUtils.horzDistanceFast(start, end);

		return pt_end_dist < pt_start_dist && pt_start_dist > start_end_dist;
	}
	
	public boolean hasParent(int parentID) {
		return sectsByID.containsKey(parentID);
	}
	
	public int getNumMinisectionsForParent(int parentID) {
		return sectsByID.get(parentID).getFaultTrace().size()-1;
	}
	
	/**
	 * @param subSectIndex
	 * @return the number of minisections associated with this subsection
	 */
	public int getNumAssociatedMinisections(int subSectIndex) {
		return subSectMinisectionAssociationNum[subSectIndex];
	}
	
	private int getFirstAssociatedMinisection(int subSectIndex) {
		for (int i=0; i<subSectMinisectionAssociations[subSectIndex].length; i++)
			if (subSectMinisectionAssociations[subSectIndex][i])
				return i;
		throw new IllegalStateException("No assiciated minisections for subsection "+subSectIndex);
	}
	
	/**
	 * @param subSectIndex
	 * @return association booleans for each minisection
	 */
	public boolean[] getMinisectionAssociations(int subSectIndex) {
		return subSectMinisectionAssociations[subSectIndex];
	}
	
	/**
	 * @param subSectIndex
	 * @return mapped length of each minisection onto the given subsection (0 if not associated)
	 */
	public double[] getMinisectionLengths(int subSectIndex) {
		return subSectMinisectionLengths[subSectIndex];
	}
	
	/**
	 * @param subSectIndex
	 * @return fractional association of each minisection onto the given subsection (0 if not associated)
	 */
	public double[] getMinisectionFracts(int subSectIndex) {
		return subSectMinisectionLengths[subSectIndex];
	}
	
	/**
	 * @param subSectIndex
	 * @param minisectionIndex 0-based minisection index
	 * @return true if the given subsection is associated with the given minisection
	 */
	public boolean isAssociated(int subSectIndex, int minisectionIndex) {
		return subSectMinisectionAssociations[subSectIndex][minisectionIndex];
	}
	
	/**
	 * @param subSectIndex
	 * @param minisectionIndex 0-based minisection index
	 * @return fractional association of the given subsection with the given minisection
	 */
	public double getFractAssociated(int subSectIndex, int minisectionIndex) {
		return subSectMinisectionFracts[subSectIndex][minisectionIndex];
	}
	
	/**
	 * @param subSectIndex
	 * @param values values for each minisection
	 * @return average of the given minisection values for this subsection, computed according to minisection
	 * fractional association
	 */
	public double getAssociationScaledAverage(int subSectIndex, double[] values) {
		return getAssociationScaledAverage(subSectIndex, Doubles.asList(values));
	}
	
	/**
	 * @param subSectIndex
	 * @param values values for each minisection
	 * @return average of the given minisection values for this subsection, computed according to minisection
	 * fractional association
	 */
	public double getAssociationScaledAverage(int subSectIndex, List<Double> values) {
		Preconditions.checkState(values.size() == subSectMinisectionLengths[subSectIndex].length,
				"Given %s values but have %s minisections for subsection %s",
				values.size(), subSectMinisectionLengths[subSectIndex].length, subSectIndex);
		int numAssociations = getNumAssociatedMinisections(subSectIndex);
		Preconditions.checkState(numAssociations >= 1, "No associations found for subsection %s", subSectIndex);
		if (numAssociations == 1 || areAllAssociatedEqual(values, subSectIndex))
			// only a single association or all associated values equal, just return that value
			return values.get(getFirstAssociatedMinisection(subSectIndex));
		double scaledAvg = 0;
		for (int i=0; i<values.size(); i++)
			scaledAvg += values.get(i) * subSectMinisectionLengths[subSectIndex][i] / subSectLengths[subSectIndex];
		return scaledAvg;
	}
	
	private boolean areAllAssociatedEqual(List<Double> values, int subSectIndex) {
		int first = getFirstAssociatedMinisection(subSectIndex);
		double firstVal = values.get(first);
		for (int i=first+1; i<values.size(); i++) {
			if (subSectMinisectionAssociations[subSectIndex][i]) {
				double val = values.get(i);
				if ((float)val != (float)firstVal)
					return false;
			}
		}
		return true;
	}
	
	/**
	 * @param subSectIndex
	 * @param values angle values for each minisection
	 * @return average angle of the given minisection values for this subsection, computed according to minisection
	 * fractional association. Returned in the range [0, 360]
	 */
	public double getAssociationScaledAngleAverage(int subSectIndex, double[] values) {
		return getAssociationScaledAngleAverage(subSectIndex, Doubles.asList(values));
	}
	
	/**
	 * @param subSectIndex
	 * @param values angle values for each minisection
	 * @return average angle of the given minisection values for this subsection, computed according to minisection
	 * fractional association.
	 */
	public double getAssociationScaledAngleAverage(int subSectIndex, List<Double> values) {
		Preconditions.checkState(values.size() == subSectMinisectionLengths[subSectIndex].length,
				"Given %s values but have %s minisections for subsection %s",
				values.size(), subSectMinisectionLengths[subSectIndex].length, subSectIndex);
		int numAssociations = getNumAssociatedMinisections(subSectIndex);
		Preconditions.checkState(numAssociations >= 1, "No associations found for subsection %s", subSectIndex);
		if (numAssociations == 1 || areAllAssociatedEqual(values, subSectIndex))
			// only a single association or all associated values equal, just return that value
			return values.get(getFirstAssociatedMinisection(subSectIndex));
		
		AngleAverager avg = new AngleAverager();
		for (int i=0; i<values.size(); i++)
			if (subSectMinisectionAssociations[subSectIndex][i])
				avg.add(values.get(i), subSectMinisectionLengths[subSectIndex][i]);
		return avg.getAverage();
	}
	
	/**
	 * Will print a warning but still use data where the minisection start/end location is at least this distance away
	 * from the fault model trace location, in km
	 */
	private static final double GEODETIC_LOC_WARN_TOL = 0.1;
	/**
	 * Will not use data where the minisection start/end location is at least this distance away from the fault model
	 * trace location, in km
	 */
	private static final double GEODETIC_LOC_ERR_TOL = 1;
	
	public boolean areMinisectionDataForParentValid(int parentID, List<? extends MinisectionDataRecord> records, boolean verbose) {
		return checkMinisectionDataForParentValid(parentID, records, verbose, false);
	}
	
	public void assertMinisectionDataForParentValid(int parentID, List<? extends MinisectionDataRecord> records) {
		checkMinisectionDataForParentValid(parentID, records, false, true);
	}
	
	private boolean checkMinisectionDataForParentValid(int parentID, List<? extends MinisectionDataRecord> records,
			boolean verbose, boolean failOnInvalid) {
		FaultSection sect = sectsByID.get(parentID);
		
		if (sect == null) {
			String error = "Minisection data references fault with ID="+parentID
					+", which was not found in fault model";
			if (failOnInvalid)
				throw new IllegalStateException(error);
			if (verbose)
				System.err.println("WARNING: "+error);
			return false;
		}
		
		String name = sect.getSectionName();
		int id = sect.getSectionId();
		
		FaultTrace trace = sect.getFaultTrace();
		
		if (trace.size() != records.size()+1) {
			String error = id+". "+name+". Trace has "+trace.size()+" locations which means "+(trace.size()-1)
					+" minisections, but have "+records.size()+" minisections";
			if (failOnInvalid)
				throw new IllegalStateException(error);
			if (verbose)
				System.err.println("WARNING: "+error);
			return false;
		}
		
		// now check each individual
		boolean ret = true;
		for (MinisectionDataRecord record : records)
			ret = checkMinisectionDataValid(record, verbose, failOnInvalid) && ret;
		return ret;
	}
	
	public boolean isMinisectionDataValid(MinisectionDataRecord record, boolean verbose) {
		return checkMinisectionDataValid(record, verbose, false);
	}
	
	public void assertMinisectionDataValid(MinisectionDataRecord record) {
		checkMinisectionDataValid(record, false, true);
	}
	
	private boolean checkMinisectionDataValid(MinisectionDataRecord record, boolean verbose, boolean failOnInvalid) {
		FaultSection sect = sectsByID.get(record.parentID);
		
		if (sect == null) {
			String error = "Minisection data references fault with ID="+record.parentID
					+", which was not found in fault model";
			if (failOnInvalid)
				throw new IllegalStateException(error);
			if (verbose)
				System.err.println("WARNING: "+error);
			return false;
		}
		
		String name = sect.getSectionName();
		int id = sect.getSectionId();
		
		FaultTrace trace = sect.getFaultTrace();
		if (record.minisectionID >= trace.size()-1) {
			String error = id+". "+name+". Trace has "+trace.size()+" locations which means "+(trace.size()-1)
					+" minisections, but encountered minisection with ID="+record.minisectionID;
			if (failOnInvalid)
				throw new IllegalStateException(error);
			if (verbose)
				System.err.println("WARNING: "+error);
			return false;
		}
		
		if (record.startLoc != null || record.endLoc != null) {
			Location traceLoc1 = trace.get(record.minisectionID);
			Location traceLoc2 = trace.get(record.minisectionID+1);
			
			double dist1 = record.startLoc == null ? Double.NaN : LocationUtils.horzDistanceFast(traceLoc1, record.startLoc);
			double dist2 = record.endLoc == null ? Double.NaN : LocationUtils.horzDistanceFast(traceLoc2, record.endLoc);
			if (dist1 > GEODETIC_LOC_WARN_TOL || dist2 > GEODETIC_LOC_WARN_TOL) {
				String error = id+". "+name+". Trace/minisection location mismatch for minisection "+record.minisectionID+":";
				error += "\n\tStart loc: ["+(float)traceLoc1.getLatitude()+", "+(float)traceLoc1.getLongitude()
					+"] vs ["+(float)record.startLoc.getLatitude()+", "+(float)record.startLoc.getLongitude()
					+"], dist="+(float)dist1+" km";
				error += "\n\tEnd loc: ["+(float)traceLoc2.getLatitude()+", "+(float)traceLoc2.getLongitude()
					+"] vs ["+(float)record.endLoc.getLatitude()+", "+(float)record.endLoc.getLongitude()
					+"], dist="+(float)dist2+" km";
				if (dist1 > GEODETIC_LOC_ERR_TOL || dist2 > GEODETIC_LOC_ERR_TOL) {
					if (failOnInvalid)
						throw new IllegalStateException(error);
					if (verbose)
						System.err.println("WARNING: "+error);
					return false;
				} else if (verbose) {
					System.err.println("WARNING: "+error);
				}
			}
		}
		
		// checks out
		return true;
	}
	
	public static abstract class MinisectionDataRecord {
		public final int parentID;
		public final int minisectionID;
		public final Location startLoc;
		public final Location endLoc;
		
		public MinisectionDataRecord(int parentID, int minisectionID, Location startLoc, Location endLoc) {
			super();
			Preconditions.checkState(parentID >= 0);
			this.parentID = parentID;
			Preconditions.checkState(minisectionID >= 0);
			this.minisectionID = minisectionID;
			this.startLoc = startLoc;
			this.endLoc = endLoc;
		}
	}

}
