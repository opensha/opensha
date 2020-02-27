package org.opensha.sha.simulators.parsers;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.iden.LogicalAndRupIden;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.iden.SkipYearsLoadIden;
import org.opensha.sha.simulators.srf.RSQSimStateTime;
import org.opensha.sha.simulators.srf.RSQSimStateTransitionFileReader;
import org.opensha.sha.simulators.srf.RSQSimTransValidIden;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.io.LittleEndianDataOutputStream;

public class RSQSimFileWriter {
	
	private DataOutput dOut;
	private DataOutput eOut;
	private DataOutput pOut;
	private DataOutput tOut;
	
	private DataOutput transOut;
	
	public RSQSimFileWriter(File outputDir, String prefix, boolean bigEndian) throws IOException {
		this(outputDir, prefix, bigEndian, false, false);
	}
	
	public RSQSimFileWriter(File outputDir, String prefix, boolean bigEndian, boolean writeTrans, boolean transV)
			throws IOException {
		BufferedOutputStream dListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".dList")));
		BufferedOutputStream eListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".eList")));
		BufferedOutputStream pListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".pList")));
		BufferedOutputStream tListStream = new BufferedOutputStream(
				new FileOutputStream(new File(outputDir, prefix+".tList")));
		BufferedOutputStream transStream = null;
		if (writeTrans) {
			String name = transV ? "transV."+prefix+".out" : "trans."+prefix+".out";
			transStream = new BufferedOutputStream(new FileOutputStream(new File(outputDir, name)));
		}
		if (bigEndian) {
			eOut = new DataOutputStream(eListStream);
			pOut = new DataOutputStream(pListStream);
			dOut = new DataOutputStream(dListStream);
			tOut = new DataOutputStream(tListStream);
			if (writeTrans)
				transOut = new DataOutputStream(transStream);
		} else {
			eOut = new LittleEndianDataOutputStream(eListStream);
			pOut = new LittleEndianDataOutputStream(pListStream);
			dOut = new LittleEndianDataOutputStream(dListStream);
			tOut = new LittleEndianDataOutputStream(tListStream);
			if (writeTrans)
				transOut = new LittleEndianDataOutputStream(transStream);
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
		writeEvent(event, eventID, 0d);
	}
	
	public void writeEvent(RSQSimEvent event, int eventID, double timeOffset) throws IOException {
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
			tOut.writeDouble(slipTime.time+timeOffset);
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

	private int prevTransEventID;
	private RSQSimStateTime prevTrans;
	private double prevTransEventTime;
	private double prevTransEventFirstTransTime;
	private double prevTransEventLastTransTime;
	
	private double prevTransTime = Double.NEGATIVE_INFINITY;
	public synchronized double writeTransitions(RSQSimEvent event,
			RSQSimStateTransitionFileReader transReader, double timeOffset) throws IOException {
		
		boolean transV = transReader.isVariableSlipSpeed();
		
		List<RSQSimStateTime> allTrans = new ArrayList<>();
		transReader.getTransitions(event, allTrans);
		
		for (int i=0; i<allTrans.size(); i++) {
			RSQSimStateTime trans = allTrans.get(i);
			double transTime = trans.getStartTime();
			if (Double.isFinite(timeOffset) && timeOffset != 0d)
				transTime += timeOffset;
			if (transTime < prevTransTime) {
				String error = "Transitions are out of order! Working on transition "
						+i+" for event "+event.getID();
				error += "\n\tOriginal trans time: "+trans.getStartTime();
				error += "\n\tOriginal event time: "+event.getTime();
				error += "\n\tOffset write trans time: "+transTime
						+" ("+(float)(transTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)";
				error += "\n\tOriginal trans index before: "+transReader.getIndexBefore(trans.getStartTime());
				error += "\n\tOffset event time: "+(event.getTime()+timeOffset);
				error += "\n\tPrevious transitions time: "+prevTransTime
						+" ("+(float)(prevTransTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)";
				error += "\n\tOriginal trans index before pref time: "+transReader.getIndexBefore(prevTransTime-timeOffset);
				double secs = transTime - prevTransTime;
				error += "\n\ttime diff: "+secs+" s";
				error += "\n\tprevious event timing (modified times):";
				error += "\n\t\tevent time: "+prevTransEventTime;
				error += "\n\t\tfirst trans time: "+prevTransEventFirstTransTime;
				error += "\n\t\tlast trans time: "+prevTransEventLastTransTime;
				error += "\n\t\tduration: "+(prevTransEventLastTransTime-prevTransEventFirstTransTime);
				error += "\n\t\tevent ID: "+prevTransEventID;
				error += "\n\tprev transition patch: "+prevTrans.getPatchID();
				error += "\n\tprev transition: "+prevTrans;
				throw new IllegalStateException(error);
			}
			prevTransTime = transTime;
			transOut.writeDouble(transTime);
			transOut.writeInt(trans.getPatchID()-1); // trans file patches are 0-based
			transOut.writeByte(trans.getState().getStateInt());
			if (transV)
				transOut.writeDouble(trans.getVelocity());
		}
		
		prevTransEventTime = event.getTime()+timeOffset;
		prevTransEventFirstTransTime = allTrans.get(0).getStartTime()+timeOffset;
		prevTransEventLastTransTime = prevTransTime;
		prevTransEventID = event.getID();
		prevTrans = allTrans.get(allTrans.size()-1);
		
		return prevTransTime;
	}
	
	public void copyTransitions(RSQSimStateTransitionFileReader transReader, Range<Double> transRange,
			double timeOffset) throws IOException {
		int written = 0;
		double firstTime = Double.NaN;
		double lastTime = Double.NaN;
		
		boolean transV = transReader.isVariableSlipSpeed();
		
		for (RSQSimStateTime trans : transReader.getTransitionsIterable(transRange)) {
			double transTime = trans.getStartTime();
			if (written == 0)
				firstTime = transTime;
			written++;
			lastTime = transTime;
			
			if (Double.isFinite(timeOffset) && timeOffset != 0d)
				transTime += timeOffset;
			
			transOut.writeDouble(transTime);
			transOut.writeInt(trans.getPatchID()-1); // trans file patches are 0-based
			transOut.writeByte(trans.getState().getStateInt());
			if (transV)
				transOut.writeDouble(trans.getVelocity());
		}
		
		System.out.println("wrote "+written+" transitions from time range ["+firstTime+","+lastTime+"]");
		if (Double.isFinite(timeOffset) && timeOffset != 0d)
			System.out.println("\tOffset output time range: ["
					+(firstTime+timeOffset)+","+(lastTime+timeOffset)+"]");
	}
	
	public void close() throws IOException {
		((FilterOutputStream)eOut).close();
		((FilterOutputStream)pOut).close();
		((FilterOutputStream)dOut).close();
		((FilterOutputStream)tOut).close();
		if (transOut != null)
			((FilterOutputStream)transOut).close();
	}
	
	public static void writeFilteredCatalog(Iterable<RSQSimEvent> events, RuptureIdentifier filter,
			File outputDir, String prefix, boolean bigEndian) throws IOException {
		RSQSimFileWriter writer = new RSQSimFileWriter(outputDir, prefix, bigEndian);
		writer.writeEvents(events, filter);
		writer.close();
	}
	
	public static void stitchCatalogs(File outputDir, String outputPrefix, RuptureIdentifier filter,
			List<SimulatorElement> elements, File... inputDirs) throws IOException {
		Preconditions.checkState(inputDirs.length > 1, "Must supply at least 2 output directories");
		boolean bigEndian = RSQSimFileReader.isBigEndian(inputDirs[0], elements);
		
		RSQSimStateTransitionFileReader[] transReaders = loadTransReaders(inputDirs, bigEndian);
		
		boolean transV = transReaders == null ? false : transReaders[0].isVariableSlipSpeed();
		
		RSQSimFileWriter writer = new RSQSimFileWriter(outputDir, outputPrefix, bigEndian,
				transReaders != null, transV);
		
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
			if (transReaders != null) {
				Range<Double> transRange = Range.closed(transReaders[i].getFirstTransitionTime(),
						transReaders[i].getLastTransitionTime());
				if (nextEvent != null && nextEvent.getTime() <= transRange.upperEndpoint())
					// end immediately before nextEvent
					transRange = Range.closedOpen(transRange.lowerEndpoint(), nextEvent.getTime());
				System.out.println("Copying transitions for range: "+transRange);
				writer.copyTransitions(transReaders[i], transRange, 0d);
			}
		}
		System.out.println("Copying inputs");
		writeCopyInputsForCombined(outputDir, outputPrefix, inputDirs);
		System.out.println("Done extending");
		writer.close();
	}
	
	private static RSQSimStateTransitionFileReader[] loadTransReaders(File[] inputDirs, boolean bigEndian)
			throws IOException {
		File[] transFiles = new File[inputDirs.length];
		for (int i=0; i<inputDirs.length; i++) {
			// look for trans file, prioritizing transV
			for (File file : inputDirs[i].listFiles()) {
				String name = file.getName().toLowerCase();
				if (!name.endsWith(".out"))
					continue;
				if (name.startsWith("transv.")) {
					transFiles[i] = file;
					break;
				}
				if (name.startsWith("trans."))
					transFiles[i] = file;
			}
			if (transFiles[i] == null) {
				// don't have transition files
				transFiles = null;
				break;
			}
		}
		
		RSQSimStateTransitionFileReader[] transReaders = null;
		if (transFiles == null) {
			System.out.println("Don't have transition files, will only write event files");
		} else {
			System.out.println("Have transition files, initializing");
			ByteOrder byteOrder = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
			boolean transV = false;
			transReaders = new RSQSimStateTransitionFileReader[transFiles.length];
			for (int i=0; i<transReaders.length; i++) {
				boolean myTransV = transFiles[i].getName().toLowerCase().startsWith("transv");
				if (i == 0) {
					transV = myTransV;
					System.out.println("\ttransV? "+transV);
				} else {
					Preconditions.checkState(transV == myTransV,
					"some catalogs have transV files and some do not, can't combine");
				}
				transReaders[i] = new RSQSimStateTransitionFileReader(transFiles[i], byteOrder, transV);
				transReaders[i].setQuiet(true);
			}
		}
		return transReaders;
	}
	
	public static void combineSeparateCatalogs(File outputDir, String outputPrefix, RuptureIdentifier filter,
			List<SimulatorElement> elements, double skipYears, File... inputDirs) throws IOException {
		Preconditions.checkState(inputDirs.length > 1, "Must supply at least 2 output directories");
		boolean bigEndian = RSQSimFileReader.isBigEndian(inputDirs[0], elements);
		
		RSQSimStateTransitionFileReader[] transReaders = loadTransReaders(inputDirs, bigEndian);
		RSQSimTransValidIden[] transValidIdens = null;
		boolean transV = false;
		if (transReaders != null) {
			transV = transReaders[0].isVariableSlipSpeed();
			transValidIdens = new RSQSimTransValidIden[transReaders.length];
			Map<Integer, Double> slipVels = new HashMap<>();
			for (SimulatorElement elem : elements)
				// value doesn't matter here, not actually using velocities, jsut making sure transitions exist
				slipVels.put(elem.getID(), 1d);
			for (int i=0; i<transReaders.length; i++)
				transValidIdens[i] = new RSQSimTransValidIden(transReaders[i], slipVels);
		}
		
		RSQSimFileWriter writer = new RSQSimFileWriter(outputDir, outputPrefix, bigEndian,
				transReaders != null, transV);
		
		Random r = new Random(elements.size());
		
		int currentID = 1;
		double curTime = 0d;
		
		double nextPrintTime = 0d; // this one is in years
		
		int totalWritten = 0;
		
		FileWriter logFW = new FileWriter(new File(outputDir, "combine_log.txt"));

		for (int i=0; i<inputDirs.length; i++) {
			// load the events for this catalog
			System.out.println("*****************");
			System.out.println("Starting catalog "+i+" at "+curTime+" s = "
					+(float)(curTime/SimulatorUtils.SECONDS_PER_YEAR)+" yrs");
			System.out.println("Loading events...");
			List<RuptureIdentifier> loadIdens = new ArrayList<>();
			if (filter != null)
				loadIdens.add(filter);
			if (transValidIdens != null)
				loadIdens.add(transValidIdens[i]);
			if (skipYears > 0 && i > 0)
				loadIdens.add(new SkipYearsLoadIden(skipYears));
			if (loadIdens.isEmpty()) {
				loadIdens = null;
			} else if (loadIdens.size() > 1) {
				// convert to logical and
				LogicalAndRupIden andIden = new LogicalAndRupIden(loadIdens);
				loadIdens = new ArrayList<>();
				loadIdens.add(andIden);
			}
			List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(inputDirs[i], elements, loadIdens);
			System.out.println("Loaded "+events.size()+" events");
			Preconditions.checkState(!events.isEmpty(), "no matching events for catalog %i: %s",
					i, inputDirs[i].getAbsolutePath());
			
			// internal start time
			double myStartTime = events.get(0).getTime();
			System.out.println("Catalog "+i+" internal start time is "+myStartTime);
			
			logFW.write("catalog "+i+"\n");
			logFW.write("original first event: "+events.get(i).getID()+" at "+myStartTime+" ("
					+(float)(myStartTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			 
			List<Double> eventRIs = new ArrayList<>();
			double timeOffset = curTime - myStartTime;
			System.out.println("Time offset: "+timeOffset );
			System.out.println("*****************");
			
			logFW.write("time offset: "+timeOffset+"\n");
			logFW.write("modified first event: "+currentID+" at "+curTime+" ("
					+(float)(curTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			
			logFW.flush();
			
			Double prevWrittenTime = null;
			
			int catalogWritten = 0;
			
			for (RSQSimEvent event : events) {
				writer.writeEvent(event, currentID++, timeOffset);
				curTime = event.getTime()+timeOffset;
				if (transReaders != null) {
					double lastTime = writer.writeTransitions(event, transReaders[i], timeOffset);
					Preconditions.checkState(lastTime >= curTime);
					curTime = lastTime;
				}
				if (prevWrittenTime != null) {
					double ri = event.getTime() - prevWrittenTime;
					eventRIs.add(ri);
				}
				prevWrittenTime = event.getTime();
				catalogWritten++;
				totalWritten++;
				
				double curTimeYears = curTime / SimulatorUtils.SECONDS_PER_YEAR;
				if (curTimeYears > nextPrintTime) {
					System.out.println((float)curTimeYears+" yr: written "+totalWritten
							+" events, currentID="+currentID);
					if (nextPrintTime < 1000d)
						nextPrintTime += 100d;
					else
						nextPrintTime += 1000d;
				}
			}
			logFW.write("wrote "+catalogWritten+" events\n");
			logFW.write("original last event: "+events.get(events.size()-1).getID()
					+" at "+prevWrittenTime+" ("
					+(float)(prevWrittenTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			logFW.write("modified last event: "+(currentID-1)+" at "+(prevWrittenTime+timeOffset)+" ("
					+(float)(prevWrittenTime+timeOffset/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			logFW.write("end time: "+curTime+" ("
					+(float)(curTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			System.out.println("Processed "+(catalogWritten)+" events from catalog "+i);
			Preconditions.checkState(catalogWritten > 0, "Catalog "+i+" has no events");
			
			System.out.println("Ended at "+curTime);
			// choose a random recurrence interval between written events, and skip that far forward
			// that will put the first event from the next catalog at a reasonable place
			double randRI;
			if (eventRIs.isEmpty())
				randRI = 1;
			else
				randRI = eventRIs.get(r.nextInt(eventRIs.size()));
			System.out.println("\tRandom recurrence interval: "+randRI+" (s)");

			logFW.write("random RI: "+randRI+" ("
					+(float)(randRI/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			
			curTime += randRI;
			System.out.println("\tMod end time: "+curTime);

			logFW.write("mod end time: "+curTime+" ("
					+(float)(curTime/SimulatorUtils.SECONDS_PER_YEAR)+" yr)\n");
			logFW.write("\n");
		}
		logFW.close();
		System.out.println("Copying inputs");
		writeCopyInputsForCombined(outputDir, outputPrefix, inputDirs);
		System.out.println("Done combining");
		writer.close();
	}
	
	private static void writeCopyInputsForCombined(File outputDir, String prefix, File... inputDirs) throws IOException {
		Map<String, String> commonParams = null;
		for (int i=0; i<inputDirs.length; i++) {
			File paramFile = RSQSimFileReader.getParamFile(inputDirs[i]);
			if (paramFile == null) {
				System.out.println("No parameter file for catalog "+i+", won't combine: "
						+inputDirs[i].getAbsolutePath());
				break;
			}
			Map<String, String> params = RSQSimFileReader.readParams(paramFile);
			if (i == 0) {
				commonParams = params;
				String geomFileName = params.get("faultFname");
				if (geomFileName != null) {
					File geomFile = new File(inputDirs[i], geomFileName);
					if (geomFile.exists()) {
						System.out.println("Copying geom file from: "+geomFile.getAbsolutePath());
						Files.copy(geomFile, new File(outputDir, geomFile.getName()));
					}
				}
			} else {
				List<String> toRemove = new ArrayList<>();
				for (String key : commonParams.keySet()) {
					String v1 = commonParams.get(key);
					if (!params.containsKey(key)) {
						System.out.println("Catalog "+i+" doesn't have parameter: "+key+" = "+v1);
						toRemove.add(key);
					} else {
						String v2 = params.get(key);
						if (!v1.equals(v2)) {
							System.out.println("Catalog "+i+" has different value for '"+key+"': "+v1+" != "+v2);
							toRemove.add(key);
						}
					}
				}
				for (String key : toRemove)
					commonParams.remove(key);
			}
		}
		if (commonParams != null) {
			commonParams.put("outFnameInfix", prefix);
			File outputFile = new File(outputDir, prefix+".in");
			System.out.println("Writing "+commonParams.size()+" parameter values to: "
					+outputFile.getAbsolutePath());
			List<String> keys = new ArrayList<>(commonParams.keySet());
			Collections.sort(keys);
			FileWriter fw = new FileWriter(outputFile);
			for (String key : keys)
				fw.write(" "+key+" = "+commonParams.get(key)+"\n");
			fw.close();
		}
	}

	public static void main(String[] args) throws IOException {
		File catalogsDir = new File("/data/kevin/simulators/catalogs");
//		File outputDir = new File(catalogsDir, "text_combine_2585");
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
//		File input1 = new File(catalogsDir, "restart2585");
//		File input2 = new File(catalogsDir, "rundir2585extend");
//		File geomFile = new File(input1, "zfault_Deepen.in");
//		
//		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
//		System.out.println("Loaded "+elements.size()+" elements");
//		
//		stitchCatalogs(outputDir, "extended", null, elements, input1, input2);
		
//		File outputDir = new File(catalogsDir, "test_separate_combine");
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
//		File bruceDir = new File(catalogsDir, "bruce");
//		File input1 = new File(bruceDir, "rundir4860");
//		File input2 = new File(bruceDir, "rundir4860");
//		File geomFile = new File(input1, "zfault_Deepen.in");
//		
//		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
//		System.out.println("Loaded "+elements.size()+" elements");
//		
//		double skipYears = 5000;
//		
//		RuptureIdentifier filter = new MagRangeRuptureIdentifier(6.5d, Double.POSITIVE_INFINITY);
//		
//		combineSeparateCatalogs(outputDir, "combine", filter, elements, skipYears, input1, input2);
		
		File catDir = new File("/home/scec-00/rsqsim/catalogs/");
		File bruceDir = new File(catDir, "shaw");
		File meDir = new File(catDir, "kmilner");
		
		File outputDir = new File(meDir, "rundir4860_multi_combine");
		
		File[] inputDirs = new File[] {
			new File(bruceDir, "rundir4860"),
			new File(bruceDir, "rundir4860.01"),
			new File(bruceDir, "rundir4910"),
			new File(bruceDir, "rundir4911"),
			new File(bruceDir, "rundir4912"),
			new File(bruceDir, "rundir4913"),
			new File(bruceDir, "rundir4915"),
			new File(bruceDir, "rundir4916"),
			new File(bruceDir, "rundir4917"),
			new File(bruceDir, "rundir4918"),
			new File(bruceDir, "rundir4919"),
		};
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		for (File inputDir : inputDirs)
			Preconditions.checkState(inputDir.exists());
		
		RuptureIdentifier filter = new MagRangeRuptureIdentifier(6.5d, Double.POSITIVE_INFINITY);
		double skipYears = 5000;
		
		File geomFile = new File(inputDirs[0], "zfault_Deepen.in");
		
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		System.out.println("Loaded "+elements.size()+" elements");
		
		combineSeparateCatalogs(outputDir, "combined", filter, elements, skipYears, inputDirs);
	}

}
