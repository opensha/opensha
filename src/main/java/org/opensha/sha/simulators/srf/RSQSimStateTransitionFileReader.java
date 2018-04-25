package org.opensha.sha.simulators.srf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.RSQSimEventRecord;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.iden.CatalogLengthLoadIden;
import org.opensha.sha.simulators.iden.EventIDsRupIden;
import org.opensha.sha.simulators.iden.LogicalAndRupIden;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.iden.SkipYearsLoadIden;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class RSQSimStateTransitionFileReader {
	
	private RandomAccessFile raFile;
	private long numTransitions;
	private byte[] doubleBytes = new byte[8];
	private DoubleBuffer doubleBuff;
	private byte[] intBytes = new byte[4];
	private IntBuffer intBuff;
	
	private double[] timeMarkers;
	private long[] indexMarkers;
	private int transPerRead;
	
	private long curIndex;
	private double[] curTimes;
	private int[] curPatchIDs;
	private RSQSimState[] curStates;
	
	// 8 byte double for time, 4 byte int for patch ID, 1 byte int for state
	private static final int bytes_per_record = 13;
	
	private static final int num_time_func_precalcs = 1000;
//	private static final int max_trans_per_read = 10000;
	private static final int max_trans_per_read = Integer.MAX_VALUE;
	private static final double max_event_time_s = 60*60; // 1 hour
	
	public RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder) throws IOException {
		this(file, byteOrder, null);
	}
	
	public RSQSimStateTransitionFileReader(File file, List<SimulatorElement> elements) throws IOException {
		this(file, null, elements);
	}
	
	private RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder, List<SimulatorElement> elements)
			throws IOException {
		raFile = new RandomAccessFile(file, "r");
		long length = raFile.length();
		numTransitions = length / bytes_per_record;
		if (length % bytes_per_record > 0)
			System.err.println("Warning, unexpected number of bytes in transitions file, possibly truncated. "
					+ "Extra: "+(length % bytes_per_record));
		
		System.out.println("Detected "+numTransitions+" transitions");
		
		if (byteOrder == null)
			byteOrder = detectByteOrder(elements);
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		intBuff = intRecord.asIntBuffer();
		
		// first skip through the file to map out the time/indexes
		initTimeMarkers();
	}
	
	private ByteOrder detectByteOrder(List<SimulatorElement> elements) throws IOException {
		System.out.println("Detecting byte order using patch IDs...");
		// 4 byte ints
		long numVals = numTransitions;
		if (numVals > Integer.MAX_VALUE)
			numVals = Integer.MAX_VALUE;
		
		int numToCheck = 100;
		
		// start out assuming both true, will quickly find out which one is really true
		boolean bigEndian = true;
		boolean littleEndian = true;
		
		byte[] recordBuffer = new byte[4];
		IntBuffer littleRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		IntBuffer bigRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		
		MinMaxAveTracker bigTrack = new MinMaxAveTracker();
		MinMaxAveTracker littleTrack = new MinMaxAveTracker();
		
		Random r = new Random();
		for (int i=0; i<numToCheck; i++) {
			long index = r.nextInt((int)numVals);
			long pos = index * bytes_per_record + 8;
			
			raFile.seek(pos);
			raFile.read(recordBuffer);
			
			// IDs in this file are 1-based, convert to 0-based by subtracting one
			int littleEndianID = littleRecord.get(0) - 1;
			int bigEndianID = bigRecord.get(0) - 1;
			
			bigTrack.addValue(bigEndianID);
			littleTrack.addValue(littleEndianID);
			
//			System.out.println("IDs:\tBIG\t"+bigEndianID+"\tLIT\t"+littleEndianID);
			
			bigEndian = bigEndian && isValidPatchID(bigEndianID, elements);
			littleEndian = littleEndian && isValidPatchID(littleEndianID, elements);
		}
		
		Preconditions.checkState(bigEndian || littleEndian, "Couldn't detect endianness - bad patch IDs?"
				+ "\n\tBig Tracker: "+bigTrack+"\n\tLittle Tracker: "+littleTrack+"\n\tNum elements: "+elements.size());
		Preconditions.checkState(!bigEndian || !littleEndian, "Passed both big and little endian tests???");
		
		ByteOrder order;
		if (bigEndian)
			order = ByteOrder.BIG_ENDIAN;
		else
			order = ByteOrder.LITTLE_ENDIAN;
		System.out.println("Detected "+order);
		return order;
	}
	
	private static boolean isValidPatchID(int patchID, List<SimulatorElement> elements) {
		return patchID > 0 && patchID <= elements.size();
	}
	
	private synchronized void initTimeMarkers() throws IOException {
		int timeIndexSize = num_time_func_precalcs;
		if (timeIndexSize > numTransitions)
			timeIndexSize = (int)numTransitions;
		timeMarkers = new double[timeIndexSize];
		indexMarkers = new long[timeIndexSize];
		long numEach = numTransitions / timeIndexSize;
		System.out.println("Initializing "+timeIndexSize+" time/index markers ("+numEach+" each bin)");
		if (numEach > max_trans_per_read)
			transPerRead = max_trans_per_read;
		else
			transPerRead = (int)numEach;
		for (int i=0; i<timeIndexSize; i++) {
			long index = numEach * i;
			read(index, 1);
			timeMarkers[i] = curTimes[0];
			indexMarkers[i] = index;
			if (i > 0)
				Preconditions.checkState(timeMarkers[i] >= timeMarkers[i-1],
					"File is out of order: %s, %s", timeMarkers[i-1], timeMarkers[i]);
		}
		System.out.println("Done initializing time/index markers: ["+timeMarkers[0]/SimulatorUtils.SECONDS_PER_YEAR
				+" "+timeMarkers[timeMarkers.length-1]/SimulatorUtils.SECONDS_PER_YEAR+"]");
	}
	
	private synchronized void read(long index, int num) throws IOException {
//		System.out.println("index: "+index);
		Preconditions.checkState(index >= 0, "Bad index: %s", index);
		raFile.seek(index*bytes_per_record);
		curIndex = index;
		if (index + num > numTransitions)
			num = (int)(numTransitions - index);
		curTimes = new double[num];
		curPatchIDs = new int[num];
		curStates = new RSQSimState[num];
		
		int len = bytes_per_record * num;
		
		byte[] buffer = new byte[len];
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			int srcPos = i*bytes_per_record;
//			System.out.println("srcPos="+srcPos);
			System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
			srcPos += 8;
			curTimes[i] = doubleBuff.get(0);
			if (i > 0)
				Preconditions.checkState(curTimes[i] >= curTimes[i-1],
					"File is out of order: %s, %s", curTimes[i-1], curTimes[i]);
			System.arraycopy(buffer, srcPos, intBytes, 0, 4);
			srcPos += 4;
			curPatchIDs[i] = intBuff.get(0);
			int stateInt = buffer[srcPos]; // 1 byte int
//			System.out.println("time="+(float)curTimes[i]+"\tpatch="+curPatchIDs[i]+"\tstateInt="+stateInt);
			curStates[i] = RSQSimState.forInt(stateInt);
		}
	}
	
	private static void debug_read(File file, int startIndex, int num, ByteOrder byteOrder) throws IOException {
		RandomAccessFile raFile = new RandomAccessFile(file, "r");
		raFile.seek(startIndex*bytes_per_record);
		
		byte[] doubleBytes = new byte[8];
		byte[] intBytes = new byte[4];
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		DoubleBuffer doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		IntBuffer intBuff = intRecord.asIntBuffer();
		
		int len = bytes_per_record * num;
		
		byte[] buffer = new byte[len];
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			int srcPos = i*bytes_per_record;
			System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
			srcPos += 8;
			double time = doubleBuff.get(0);
			System.arraycopy(buffer, srcPos, intBytes, 0, 4);
			srcPos += 4;
			int patch = intBuff.get(0);
			int stateInt = buffer[srcPos]; // 1 byte int
			
			System.out.println(time+"\t"+patch+"\t"+stateInt);
		}
		
		raFile.close();
	}
	
	/**
	 * Reads the given transition into memory if necessary, then returns the index in the cur* arrays
	 * of that transition
	 * @param index
	 * @return
	 * @throws IOException 
	 */
	private int readTransition(long index) throws IOException {
		long arrayIndex = index - curIndex;
		if (arrayIndex >= 0l && arrayIndex < Integer.MAX_VALUE && arrayIndex < curTimes.length)
			// it's already in memory
			return (int)arrayIndex;
		// need to load it into memory
		System.out.print("Caching "+transPerRead+" transisions at index "+index+"...");
		read(index, transPerRead);
		System.out.println("DONE");
		return 0;
	}
	
	public List<RSQSimStateTime> getTransitions(double startTime, double endTime) throws IOException {
		List<RSQSimStateTime> trans = new ArrayList<>();
		
		long index;
		if (startTime < timeMarkers[0])
			index = 0;
		else
			index = getIndexBefore(startTime);
		while (index < numTransitions) {
			int arrayIndex = readTransition(index++);
			double time = curTimes[arrayIndex];
			int patchID = curPatchIDs[arrayIndex]+1; // these are zero based but in events it's 1-based
			if (time < startTime)
				continue;
			if (time > endTime)
				break;
			trans.add(new RSQSimStateTime(patchID, time, curStates[arrayIndex]));
		}
		return trans;
	}
	
	public Map<Integer, List<RSQSimStateTime>> getTransitions(RSQSimEvent event) throws IOException {
		Map<Integer, List<RSQSimStateTime>> patchTransitions = new HashMap<>();
		
		// this will store the time of the next event on this patch. so any transitions that happen on this patch
		// with time >= startTime and time < patchEndTime are associated with this event
		Map<Integer, Double> patchEndTimes = new HashMap<>();
		double startTime = event.getTime();
		
		for (EventRecord rec : event) {
			Preconditions.checkState(rec instanceof RSQSimEventRecord);
			RSQSimEventRecord rsRecord = (RSQSimEventRecord)rec;
			int[] recPatchIDs = rsRecord.getElementIDs();
			Preconditions.checkNotNull(recPatchIDs);
			double[] nextSlipTimes = rsRecord.getNextSlipTimes();
			Preconditions.checkNotNull(nextSlipTimes);
			for (int i=0; i<recPatchIDs.length; i++) {
				patchTransitions.put(recPatchIDs[i], new ArrayList<>());
				patchEndTimes.put(recPatchIDs[i], nextSlipTimes[i]);
			}
		}
		
		double maxEndTime = startTime + max_event_time_s;
		
		long index = getIndexBefore(startTime);
		while (index < numTransitions) {
			int arrayIndex = readTransition(index++);
			double time = curTimes[arrayIndex];
			if (time < startTime)
				// before this event
				continue;
			if (time >= maxEndTime)
				// after the possible transition times for any patch in this event
				break;
			int patchID = curPatchIDs[arrayIndex]+1; // these are zero based but in events it's 1-based
			if (!patchEndTimes.containsKey(patchID))
				// it's for an unrelated patch
				continue;
			boolean thisEvent = time < patchEndTimes.get(patchID) ||
					(curStates[arrayIndex] == RSQSimState.LOCKED && time == patchEndTimes.get(patchID));
//			if (patchID == 193480)
//				System.out.println("patch "+patchID+" t="+time+" ("+(time - startTime)+") "+curStates[arrayIndex].name()+" "+thisEvent);
			if (thisEvent) {
				// it's actually for this patch and event
				List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
				if (!patchTimes.isEmpty()) {
					// close out the previous one
					RSQSimStateTime prev = patchTimes.get(patchTimes.size()-1);
					prev.setEndTime(time);
				}
				patchTimes.add(new RSQSimStateTime(patchID, time, curStates[arrayIndex]));
			}
		}
		// now close each patch
		for (int patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
			if (!patchTimes.isEmpty()) {
				RSQSimStateTime patchTime = patchTimes.get(patchTimes.size()-1);
				Preconditions.checkState(patchTime.getState() == RSQSimState.LOCKED,
						"Event %s, patch %s ended in non-locked state! Entered %s at relative t=%s. Relative next time: %s",
						event.getID(), patchID, patchTime.getState(), patchTime.getStartTime()-startTime, patchEndTimes.get(patchID) - startTime);
				patchTimes.remove(patchTimes.size()-1);
			}
		}
		
		return patchTransitions;
	}
	
	private long getIndexBefore(double time) {
		int index = Arrays.binarySearch(timeMarkers, time);
		if (index < 0)
			index = -index - 2; // -2 because we want before
		Preconditions.checkState(index >= 0 && index < timeMarkers.length);
		if (time == timeMarkers[index] && index > 0)
			// it's an exact match, go one before to be safe
			index--;
		return indexMarkers[index];
	}
	
	public synchronized double getFirstTransitionTime() throws IOException {
		read(0, 1);
		return curTimes[0];
	}
	
	public double getLastTransitionTime() throws IOException {
		read(numTransitions-1, 1);
		return curTimes[0];
	}
	
	private static DecimalFormat timeDF = new DecimalFormat("0.000");
	public static void printTransitions(RSQSimEvent e, Map<Integer, List<RSQSimStateTime>> transitions) {
		System.out.println("Transitions for event "+e.getID()+" (M"+(float)e.getMagnitude()+")"
				+" at time "+(e.getTimeInYears())+" yr");
		double eventTime = e.getTime();
		for (int patchID : e.getAllElementIDs()) {
			List<RSQSimStateTime> patchTrans = transitions.get(patchID);
			System.out.println("PATCH "+patchID);
			for (RSQSimStateTime trans : patchTrans) {
				double start = trans.getStartTime() - eventTime;
				double end = trans.getEndTime() - eventTime;
				RSQSimState state = trans.getState();
				System.out.println("\t"+timeDF.format(start)+"s\t=>\t"+timeDF.format(end)+"s\t"+state+"\t["+timeDF.format(end - start)+"s]");
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
//			File dir = new File("/data/kevin/simulators/catalogs/baseCatalogSW_10");
//			File transFile = new File(dir, "trans.baseCatalogSW_10t.out");
//			File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//			String str = "--print-rup "+transFile.getAbsolutePath()+" "+geomFile.getAbsolutePath()
//				+" 32777581 32777582 32777583 32777584 32777585 32777586 32777587 32777588 32777589 327775810";
//			System.out.println("HARDCODED: "+str);
//			args = Iterables.toArray(Splitter.on(" ").split(str), String.class);
			File dir = new File("/data/kevin/simulators/catalogs/rundir2585extend");
			File transFile = new File(dir, "trans.extend2585.out");
			args = Iterables.toArray(Splitter.on(" ").split("--debug "+transFile.getAbsolutePath()+" 0 100 little"), String.class);
		}
		if (args.length > 0 && args[0].equals("--debug")) {
			if (args.length != 5) {
				System.err.println("USAGE: --debug <trans-file> <start-index> <num> <little/big>");
				System.exit(2);
			}
			File file = new File(args[1]);
			int startIndex = Integer.parseInt(args[2]);
			int num = Integer.parseInt(args[3]);
			ByteOrder byteOrder = null;
			if (args[4].toLowerCase().startsWith("little"))
				byteOrder = ByteOrder.LITTLE_ENDIAN;
			else if (args[4].toLowerCase().startsWith("big"))
				byteOrder = ByteOrder.LITTLE_ENDIAN;
			else {
				System.out.println("Expected 'little' or 'big'");
				System.exit(2);
			}
			
			debug_read(file, startIndex, num, byteOrder);
			
			System.exit(0);
		} else if (args.length > 0 && args[0].equals("--print-rup")) {
			if (args.length < 4) {
				System.err.println("USAGE: --print-rup <trans-file> <geom-file> <rup-index-1> [...<rup-index-N>]");
				System.exit(2);
			}
			File transFile = new File(args[1]).getAbsoluteFile();
			File catalogDir = transFile.getParentFile();
			File geomFile = new File(args[2]);
			int[] rupIDs = new int[args.length-3];
			for (int i=0; i<rupIDs.length; i++)
				rupIDs[i] = Integer.parseInt(args[3+i]);
			System.out.println("Loading geometry...");
			List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
			System.out.println("Loaded "+elements.size()+" elements");
			List<RuptureIdentifier> loadIdens = new ArrayList<>();
			RuptureIdentifier loadIden = new EventIDsRupIden(rupIDs);
			loadIdens.add(loadIden);
			System.out.println("Loading events...");
			List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
			System.out.println("Loaded "+events.size()+" events");
			
			RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
			System.out.println("Transition file starts at "+transReader.getFirstTransitionTime()+" seconds");
			System.out.println("Transition file ends at "+transReader.getLastTransitionTime()+" seconds");
			for (RSQSimEvent e : events) {
				System.out.println("************************************************");
				double secsFromEnd = transReader.getLastTransitionTime() - e.getTime();
				System.out.println("Event "+e.getID()+" at t="+e.getTime()+" s ("+(float)secsFromEnd+" s from end of trans file)");
				Map<Integer, List<RSQSimStateTime>> transitions = transReader.getTransitions(e);
				printTransitions(e, transitions);
				System.out.println("************************************************");
			}
			
			System.exit(0);
		}
////		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2194_long");
////		File geomFile = new File(catalogDir, "zfault_Deepen.in");
////		File transFile = new File(catalogDir, "trans.rundir2194_long.out");
//		File catalogDir = new File("/data/kevin/simulators/catalogs/jacqui_12km_K");
//		File geomFile = new File(catalogDir, "UCERF3wFixed12kmDepth.flt");
//		File transFile = new File(catalogDir, "trans.UCERF3_flt_12km_K.out");
////		double minMag = 6d;
////		double maxMag = 6.1d;
//		double minMag = 7d;
//		double maxMag = 10d;
//		double skipYears = 5000;
//		double maxLengthYears = 25000;
//		double slipVel = 1d; // m/s
//		
//		// load events
//		System.out.println("Loading geometry...");
//		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
//		System.out.println("Loaded "+elements.size()+" elements");
//		List<RuptureIdentifier> loadIdens = new ArrayList<>();
//		RuptureIdentifier loadIden = new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
//				new MagRangeRuptureIdentifier(minMag, maxMag),
//				new CatalogLengthLoadIden(maxLengthYears));
//		loadIdens.add(loadIden);
//		System.out.println("Loading events...");
//		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
//		System.out.println("Loaded "+events.size()+" events with "+(float)minMag+"<=M<="+(float)maxMag
//				+", duration: "+SimulatorUtils.getSimulationDurationYears(events)+" years");
////		List<RSQSimEvent> events = null;
//		
//		// load transitions
//		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
////		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, ByteOrder.LITTLE_ENDIAN);
////		System.exit(0);
////		transReader.readTransition(0);
//		
//		double pDiffThresh = 1d;
//		MinMaxAveTracker pDiffTrack = new MinMaxAveTracker();
//		for (int i=0; i<10; i++) {
//			int index = new Random().nextInt(events.size());
//			RSQSimEvent e = events.get(index);
//			System.out.println("************************************************");
//			Map<Integer, List<RSQSimStateTime>> transitions = transReader.getTransitions(e);
//			printTransitions(e, transitions);
//			System.out.println("Validating...");
//			System.out.flush();
//			RSQSimEventSlipTimeFunc slipFunc = new RSQSimEventSlipTimeFunc(transitions, slipVel);
//			MinMaxAveTracker subTrack = slipFunc.validateTotalSlip(e, pDiffThresh);
//			pDiffTrack.addFrom(subTrack);
//			System.out.println("DONE. Max diff: "+(float)subTrack.getMax()+" %");
//			System.out.println("************************************************");
//		}
//		
//		System.out.println();
//		System.out.println("Patch slip % diffs: "+pDiffTrack);
	}
}
