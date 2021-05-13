package org.opensha.sha.simulators;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class EQSIM_Event extends SimulatorEvent {
	
	private List<EQSIM_EventRecord> records;
	
	public EQSIM_Event(EQSIM_EventRecord eventRecord) {
		this(Lists.newArrayList(eventRecord));
	}

	public EQSIM_Event(List<EQSIM_EventRecord> records) {
		super(records);
		this.records = records;
		EQSIM_EventRecord firstRecord = records.get(0);
	}
	
	public void addEventRecord(EQSIM_EventRecord eventRecord){
		if(isSameEvent(eventRecord))
			records.add(eventRecord);
		else throw new RuntimeException("Can't add because event IDs differ");
	}
	
	/**
	 * Note that das must be supplied in meters.
	 * @param sectId
	 * @param das - meters
	 * @return
	 */
	public boolean doesEventIncludeSectionAndDAS(int sectId, double das) {
		boolean includes = false;
		for(EQSIM_EventRecord eventRecord: records)
			if(eventRecord.getSectionID() == sectId && das<eventRecord.das_hi && das>eventRecord.das_lo)
				includes = true;
		return includes;
	}
	
	/**
	 * This attempts to find the distance along rupture for each element,
	 * (normalized by the total length of rupture).  The start and end of the
	 * rupture is arbitrary.
	 * 
	 * The results are in the same order as returned by getAllElementIDs().
	 * 
	 * This calculation is complicated and approximate for many reasons.
	 * 
	 * For example, say you have a small branch extending off a longer rupture,
	 * then the position of this fork is determined more by when it occurs rather
	 * than where it occurs (e.g., if it slips after all others, then it is positioned
	 * at the end, even if the branch is near the middle of the long fault).
	 * 
	 * 
	 * @return
	 */
	public double[] getNormDistAlongRupForElements() {
		if(hasElementSlipsAndIDsOnAllRecords()) {
			
			// there are two tricky issues here 1) the ordering of of EventRecords is by 
			// occurrence, not position along rupture, and 2) DAS values may need to be
			// flipped when stitching the rupture together.
			
			// store info for each event record (ER)
			Vertex[] vertexForMinDAS_forER = new Vertex[size()];
			Vertex[] vertexForMaxDAS_forER = new Vertex[size()];
			double[] minDAS_forER = new double[size()];	// meters
			double[] maxDAS_forER = new double[size()];	// meters
			boolean[] flipER = new boolean[size()];
			for(int er_index=0;er_index<size();er_index++) {
				EQSIM_EventRecord er = get(er_index);
				minDAS_forER[er_index] = er.getMinDAS();	// meters
				maxDAS_forER[er_index] = er.getMaxDAS();	// meters
				vertexForMinDAS_forER[er_index] = er.getVertxForMinDAS();
				vertexForMaxDAS_forER[er_index] = er.getVertxForMaxDAS();
			}
			
			
			// find the correct order for ERs along rupture ("min" is short for das_lo_vertex & "max" is short for das_hi_vertex)
			ArrayList<Integer> reorderedIndices = new ArrayList<Integer>(); // the order along the length, rather than temporal order
			if(size()>1) {
				// find the two closest points between the first two event records
				double min1_min2_dist = vertexForMinDAS_forER[0].getLinearDistance(vertexForMinDAS_forER[1]);
				double min1_max2_dist = vertexForMinDAS_forER[0].getLinearDistance(vertexForMaxDAS_forER[1]);
				double distFromMin1 = Math.min(min1_min2_dist, min1_max2_dist);

				double max1_min2_dist = vertexForMaxDAS_forER[0].getLinearDistance(vertexForMinDAS_forER[1]);
				double max1_max2_dist = vertexForMaxDAS_forER[0].getLinearDistance(vertexForMaxDAS_forER[1]);
				double distFromMax1 = Math.min(max1_min2_dist, max1_max2_dist);
				
				if(distFromMin1<distFromMax1) {// closer to the min-DAS end (put second in front of first)
					reorderedIndices.add(1);
					reorderedIndices.add(0);
				} 
				else {
					reorderedIndices.add(0);
					reorderedIndices.add(1);
				}
				
				// now add any others to beginning or end of list depending on which is closer 
				for(int er_index=2; er_index<size(); er_index++) {
					// distance from first ER
					double distFromFirstER = getMinDistBetweenEventRecordEnds(
							vertexForMinDAS_forER[reorderedIndices.get(0)],
							vertexForMaxDAS_forER[reorderedIndices.get(0)],
							vertexForMinDAS_forER[er_index],
							vertexForMaxDAS_forER[er_index]);
							
					// distance from last ER
					double distFromLastER = getMinDistBetweenEventRecordEnds(
							vertexForMinDAS_forER[reorderedIndices.get(reorderedIndices.size()-1)],
							vertexForMaxDAS_forER[reorderedIndices.get(reorderedIndices.size()-1)],
							vertexForMinDAS_forER[er_index],
							vertexForMaxDAS_forER[er_index]);
					
					if(distFromFirstER<distFromLastER)// it's closest to the first ER so add to beginning
						reorderedIndices.add(0, er_index);
					else
						reorderedIndices.add(er_index);
				}
			}
			else {
				reorderedIndices.add(0);   // only one ER
			}
			
// for(Integer index:reorderedIndices) System.out.println(get(index).getSectionID());

			
			// FILL in flipER (whether event records need to be flipped when stitched together)
			// determine whether to flip the first event record (if max das is not the closest to the second event record)
			if(size()>1) {
				double min1_min2_dist = vertexForMinDAS_forER[reorderedIndices.get(0)].getLinearDistance(vertexForMinDAS_forER[reorderedIndices.get(1)]);
				double min1_max2_dist = vertexForMinDAS_forER[reorderedIndices.get(0)].getLinearDistance(vertexForMaxDAS_forER[reorderedIndices.get(1)]);
				double distFromMin1 = Math.min(min1_min2_dist, min1_max2_dist);

				double max1_min2_dist = vertexForMaxDAS_forER[reorderedIndices.get(0)].getLinearDistance(vertexForMinDAS_forER[reorderedIndices.get(1)]);
				double max1_max2_dist = vertexForMaxDAS_forER[reorderedIndices.get(0)].getLinearDistance(vertexForMaxDAS_forER[reorderedIndices.get(1)]);
				double distFromMax1 = Math.min(max1_min2_dist, max1_max2_dist);
				
				if(distFromMin1<distFromMax1) // closer to the min-DAS end
					flipER[0]=true;
				else
					flipER[0]=false;				
			}
			else {
				flipER[0]=false;   // don't flip if only one ER
			}
			// now set any other records as flipped
			for(int er_index=1; er_index<size(); er_index++) {
				// get last vertex of previous ER
				int lastER_index = reorderedIndices.get(er_index-1);
				Vertex lastVertex;
				if(flipER[lastER_index])	// get min-das vertex if last one was flipped
					lastVertex=vertexForMinDAS_forER[lastER_index];
				else
					lastVertex=vertexForMaxDAS_forER[lastER_index];
				
				// now flip present ER if lastVertex is closer to max-das on present ER
				if(lastVertex.getLinearDistance(vertexForMinDAS_forER[reorderedIndices.get(er_index)]) < lastVertex.getLinearDistance(vertexForMaxDAS_forER[reorderedIndices.get(er_index)]))
					flipER[er_index]=false;
				else
					flipER[er_index]=true;
			}
			
			// compute the start distance along for each ER
			double[] startDistAlongForReorderedER = new double[size()];
			startDistAlongForReorderedER[0]=0;
			for(int i=1;i<size();i++)
				startDistAlongForReorderedER[i]=startDistAlongForReorderedER[i-1]+get(reorderedIndices.get(i-1)).getLength();	// meters

			// now compute distance along for each element
			int rectElemIndex=-1;
			double totalRuptureLength = getLength();	// this is in meters!
			double[] distAlongForEachElement = new double[getNumElements()];
			for(int er_index=0;er_index<size();er_index++) {
				double startDistanceAlong = startDistAlongForReorderedER[reorderedIndices.indexOf(new Integer(er_index))];
//				int reorderedIndex = reorderedIndices.get(er_index);
				EventRecord er = get(er_index);
// System.out.println("er.getSectionID()="+er.getSectionID()+"\t"+"startDistanceAlong="+startDistanceAlong);
				for(SimulatorElement rectElem : er.getElements()) {
					rectElemIndex += 1;
					double aveDAS = rectElem.getAveDAS()*1000;	// convert to meters
					if(flipER[er_index]) {	// index for flipER is not reordered!
						distAlongForEachElement[rectElemIndex] = (startDistanceAlong+maxDAS_forER[er_index]-aveDAS)/totalRuptureLength;
					}
					else {
						distAlongForEachElement[rectElemIndex] = (startDistanceAlong+aveDAS-minDAS_forER[er_index])/totalRuptureLength;
					}
				}
			}
			
			double minDistAlong = Double.MAX_VALUE;
			double maxDistAlong = Double.NEGATIVE_INFINITY;
			for(double distAlong:distAlongForEachElement) {
				if(minDistAlong>distAlong) minDistAlong=distAlong;
				if(maxDistAlong<distAlong) maxDistAlong=distAlong;
			}
// System.out.println((float)totalRuptureLength+"\t"+(float)(minDistAlong*totalRuptureLength)+"\t"+(float)(maxDistAlong*totalRuptureLength));

			return distAlongForEachElement;
		} else 
			return null;
	}

	/**
	 * This returns the area-averaged slip in meters
	 * @return
	 */
	public double getMeanSlip() {
		double aveSlip=0;
		double totalArea=0;
		for(EQSIM_EventRecord evRec:records) {
			aveSlip += evRec.getMeanSlip()*evRec.getArea();
			totalArea+=evRec.getArea();
		}
		return aveSlip/totalArea;
	}
	
	public EQSIM_EventRecord get(int index) {
		return records.get(index);
	}

}
