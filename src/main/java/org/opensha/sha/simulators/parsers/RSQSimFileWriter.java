package org.opensha.sha.simulators.parsers;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.io.LittleEndianDataOutputStream;

public class RSQSimFileWriter {
	
	private DataOutput dOut;
	private DataOutput eOut;
	private DataOutput pOut;
	private DataOutput tOut;
	
	public RSQSimFileWriter(File outputDir, String prefix, boolean bigEndian) throws IOException {
		BufferedOutputStream dListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".dList")));
		BufferedOutputStream eListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".eList")));
		BufferedOutputStream pListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".pList")));
		BufferedOutputStream tListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".tList")));
		if (bigEndian) {
			eOut = new DataOutputStream(eListStream);
			pOut = new DataOutputStream(pListStream);
			dOut = new DataOutputStream(dListStream);
			tOut = new DataOutputStream(tListStream);
		} else {
			eOut = new LittleEndianDataOutputStream(eListStream);
			pOut = new LittleEndianDataOutputStream(pListStream);
			dOut = new LittleEndianDataOutputStream(dListStream);
			tOut = new LittleEndianDataOutputStream(tListStream);
		}
	}
	
	private class PatchSlipTime implements Comparable<PatchSlipTime> {
		final int patchID;
		final double slip;
		final double time;
		
		public PatchSlipTime(int patchID, double slip, double time) {
			this.patchID = patchID;
			this.slip = slip;
			this.time = time;
		}
		
		@Override
		public int compareTo(PatchSlipTime o) {
			return Double.compare(time, o.time);
		}
	}
	
	public void writeEvent(RSQSimEvent event) throws IOException {
		writeEvent(event, event.getID());
	}
	
	public void writeEvent(RSQSimEvent event, int eventID) throws IOException {
		List<PatchSlipTime> slipTimes = new ArrayList<>();
		for (EventRecord r : event) {
			int[] ids = r.getElementIDs();
			double[] slips = r.getElementSlips();
			double[] times = r.getElementTimeFirstSlips();
			for (int i=0; i<ids.length; i++)
				slipTimes.add(new PatchSlipTime(ids[i], slips[i], times[i]));
		}
		Collections.sort(slipTimes);
		for (PatchSlipTime slipTime : slipTimes) {
			dOut.writeDouble(slipTime.slip);
			eOut.writeInt(eventID);
			pOut.writeInt(slipTime.patchID);
			tOut.writeDouble(slipTime.time);
		}
	}

	public void writeEvents(Iterable<RSQSimEvent> events) throws IOException {
		writeEvents(events, Double.NEGATIVE_INFINITY);
	}
	
	public void writeEvents(Iterable<RSQSimEvent> events, double minMag) throws IOException {
		RuptureIdentifier rupIden = Double.isFinite(minMag) && minMag > 0 ?
				new MagRangeRuptureIdentifier(minMag, Double.POSITIVE_INFINITY) : null;
		writeEvents(events, rupIden);
	}
	
	public void writeEvents(Iterable<RSQSimEvent> events, RuptureIdentifier rupIden) throws IOException {
		for (RSQSimEvent e : events) {
			if (rupIden == null || rupIden.isMatch(e))
				writeEvent(e);
		}
	}
	
	public void close() throws IOException {
		((FilterOutputStream)eOut).close();
		((FilterOutputStream)pOut).close();
		((FilterOutputStream)dOut).close();
		((FilterOutputStream)tOut).close();
	}
	
	public static void writeFilteredCatalog(Iterable<RSQSimEvent> events, RuptureIdentifier filter,
			File outputDir, String prefix, boolean bigEndian) throws IOException {
		RSQSimFileWriter writer = new RSQSimFileWriter(outputDir, prefix, bigEndian);
		writer.writeEvents(events, filter);
		writer.close();
	}
	
	public static void combineCatalogs(File outputDir, String outoutPrefix, RuptureIdentifier filter,
			List<SimulatorElement> elements, File... inputDirs) throws IOException {
		Preconditions.checkState(inputDirs.length > 1, "Must supply at least 2 output directories");
		boolean bigEndian = RSQSimFileReader.isBigEndian(inputDirs[0], elements);
		RSQSimFileWriter writer = new RSQSimFileWriter(outputDir, outoutPrefix, bigEndian);
		
		List<PeekingIterator<RSQSimEvent>> iterators = new ArrayList<>();
		RSQSimEvent prevLastEvent = null;
		int currentID = 1;
		double overallFirstTime = Double.NaN;
		for (int i=0; i<inputDirs.length; i++) {
			// initialize iterators for this and the next
			PeekingIterator<RSQSimEvent> it;
			if (iterators.size() > i) {
				it = iterators.get(i);
			} else {
				it = Iterators.peekingIterator(RSQSimFileReader.getEventsIterable(inputDirs[i], elements).iterator());
				iterators.add(it);
			}
			PeekingIterator<RSQSimEvent> nextIt = null;
			if (i+1 < inputDirs.length) {
				if (iterators.size() > i+1) {
					nextIt = iterators.get(i+1);
				} else {
					nextIt = Iterators.peekingIterator(RSQSimFileReader.getEventsIterable(inputDirs[i+1], elements).iterator());
					iterators.add(nextIt);
				}
			}
			RSQSimEvent nextEvent = nextIt == null ? null : nextIt.peek();
			
			boolean first = true;
			boolean firstOverlap = true;
			int numOverlap = 0;
			int totalNum = 0;
			double firstTime = Double.NaN;
			while (it.hasNext()) {
				totalNum++;
				RSQSimEvent event = it.next();
				if (first) {
					firstTime = event.getTime();
					System.out.println("Catalog "+i+" starts at t="+firstTime
							+" s ("+event.getTimeInYears()+" yrs), first ID="+event.getID());
					if (prevLastEvent != null) {
						double deltaSecs = firstTime - prevLastEvent.getTime();
						System.out.println("\t"+deltaSecs+" s ("+(deltaSecs/SimulatorUtils.SECONDS_PER_YEAR)
								+" yrs) after last event written from previous catalog");
					}
					first = false;
				}
				if (nextEvent == null || event.getTime() < nextEvent.getTime()) {
					prevLastEvent = event;
					currentID++;
					if (filter == null || filter.isMatch(event))
						writer.writeEvent(event, currentID);
				} else {
					if (nextEvent != null && firstOverlap) {
						firstOverlap = false;
						double t1 = event.getTime();
						double t2 = nextEvent.getTime();
						double m1 = event.getMagnitude();
						double m2 = nextEvent.getMagnitude();
						if (t1 != t2 || m1 != m2) {
							System.err.println("WARNING! Catalogs overlap, but first events in overlap zone are different. Is this catalog really an extension?");
							System.err.println("\tLast written event with id="+prevLastEvent.getID()+" from catalog "+i
										+":\tt="+prevLastEvent.getTime()+", m="+prevLastEvent.getMagnitude());
							System.err.println("\tNext event with id="+event.getID()+" at end of catalog "+i+":\tt="+t1+", m="+m1);
							System.err.println("\tEvent with id="+nextEvent.getID()+" at start of catalog "+(i+1)+":\tt="+t2+", m="+m2);
						}
					}
					numOverlap++;
				}
			}
			Preconditions.checkState(totalNum > 0 && prevLastEvent != null, "Catalog "+i+" has no events");
			if (i == 0)
				overallFirstTime = firstTime;
			System.out.println("Processed "+(totalNum - numOverlap)+" events from catalog "+i);
			if (numOverlap > 0)
				System.out.println("Skipped "+numOverlap+" events which overlap with next catalog");
			double curTime = prevLastEvent.getTime();
			System.out.println("Duration for catalog "+i+": "+(curTime-firstTime)/SimulatorUtils.SECONDS_PER_YEAR+" yrs");
			System.out.println("Current total duration: "+(curTime-overallFirstTime)/SimulatorUtils.SECONDS_PER_YEAR+" yrs");
		}
		System.out.println("Done extending");
		writer.close();
	}

	public static void main(String[] args) throws IOException {
		File catalogsDir = new File("/data/kevin/simulators/catalogs");
		File outputDir = new File(catalogsDir, "text_combine_2585");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File input1 = new File(catalogsDir, "restart2585");
		File input2 = new File(catalogsDir, "rundir2585extend");
		File geomFile = new File(input1, "zfault_Deepen.in");
		
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		System.out.println("Loaded "+elements.size()+" elements");
		
		combineCatalogs(outputDir, "extended", null, elements, input1, input2);
	}

}
