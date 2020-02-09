package org.opensha.sha.simulators.srf;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
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
import com.google.common.collect.Range;

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
	private double[] curVels;
	
	private int bytesPerRecord;
	private boolean transV;
	
	// 8 byte double for time, 4 byte int for patch ID, 1 byte int for state
	private static final int regular_bytes_per_record = 13;
	// 8 byte double for time, 4 byte int for patch ID, 1 byte int for state, 8 byte double for slip velocity
	private static final int transV_bytes_per_record = 21;
	
	private static final int num_time_func_precalcs = 1000;
//	private static final int max_trans_per_read = 10000;
	private static final int max_trans_per_read = Integer.MAX_VALUE;
	private static final double max_event_time_s = 60*60; // 1 hour
	
	public RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder, boolean transV) throws IOException {
		this(file, byteOrder, null, transV);
	}
	
	public RSQSimStateTransitionFileReader(File file, List<SimulatorElement> elements, boolean transV) throws IOException {
		this(file, null, elements, transV);
	}
	
	private RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder, List<SimulatorElement> elements, boolean transV)
			throws IOException {
		raFile = new RandomAccessFile(file, "r");
		long length = raFile.length();
		this.transV = transV;
		if (transV)
			bytesPerRecord = transV_bytes_per_record;
		else
			bytesPerRecord = regular_bytes_per_record;
		numTransitions = length / bytesPerRecord;
		if (length % bytesPerRecord > 0)
			System.err.println("Warning, unexpected number of bytes in transitions file, possibly truncated. "
					+ "Extra: "+(length % bytesPerRecord));
		
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
			long pos = index * bytesPerRecord + 8;
			
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
	
	private boolean retainNegatives = false;
	public void setRetainNegativeSlips(boolean retainNegatives) {
		this.retainNegatives = retainNegatives;
	}
	
	private synchronized void read(long index, int num) throws IOException {
//		System.out.println("index: "+index);
		Preconditions.checkState(index >= 0, "Bad index: %s", index);
		long seek = index*bytesPerRecord;
		raFile.seek(seek);
		curIndex = index;
		if (index + num > numTransitions)
			num = (int)(numTransitions - index);
		curTimes = new double[num];
		curPatchIDs = new int[num];
		curStates = new RSQSimState[num];
		if (transV)
			curVels = new double[num];
		
		int len = bytesPerRecord * num;
		
		byte[] buffer = new byte[len];
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			try {
				int srcPos = i*bytesPerRecord;
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
				srcPos += 1;
//			System.out.println("time="+(float)curTimes[i]+"\tpatch="+curPatchIDs[i]+"\tstateInt="+stateInt);
				curStates[i] = RSQSimState.forInt(stateInt);
				if (transV) {
					System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
					srcPos += 8;
					curVels[i] = doubleBuff.get(0);
					if (curVels[i] < 0 && curStates[i] == RSQSimState.EARTHQUAKE_SLIP && !retainNegatives) {
						System.err.println("WARNING: Bad (negative) velocity, setting to 1e-6 as temporary fix");
						curVels[i] = 1e-6;
					}
					Preconditions.checkState(curStates[i] != RSQSimState.EARTHQUAKE_SLIP || curVels[i] > 0,
							"Bad slip velocity in state %s at pos=%s: %s", curStates[i], srcPos, curVels[i]);
				}
			} catch (Exception e) {
				System.out.println();
				System.out.flush();
				String message = "Error reading transition index "+(index+i)+". Read began at index "
						+index+" (pos="+seek+"), this is transition "+i+" of "+num+" in this read";
				if (e instanceof IOException)
					throw new IOException(message, e);
				throw new RuntimeException(message, e);
			}
		}
	}
	
	private static void debug_read(File file, long startIndex, int num, ByteOrder byteOrder, boolean transV) throws IOException {
		RandomAccessFile raFile = new RandomAccessFile(file, "r");
		int bytesPerRecord = transV ? transV_bytes_per_record : regular_bytes_per_record;
		long seek = startIndex*bytesPerRecord;
		System.out.println("Seeking to: "+seek);
		raFile.seek(seek);
		
		byte[] doubleBytes = new byte[8];
		byte[] intBytes = new byte[4];
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		DoubleBuffer doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		IntBuffer intBuff = intRecord.asIntBuffer();
		
		int len = bytesPerRecord * num;
		
		byte[] buffer = new byte[len];
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			int srcPos = i*bytesPerRecord;
			System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
			srcPos += 8;
			double time = doubleBuff.get(0);
			System.arraycopy(buffer, srcPos, intBytes, 0, 4);
			srcPos += 4;
			int patch = intBuff.get(0);
			int stateInt = buffer[srcPos]; // 1 byte int
			srcPos += 1;
			
			if (transV) {
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double vel = doubleBuff.get(0);
				System.out.println((startIndex+i)+":\t"+time+"\t"+patch+"\t"+stateInt+"\t"+vel);
			} else {
				System.out.println((startIndex+i)+":\t"+time+"\t"+patch+"\t"+stateInt);
			}
		}
		
		raFile.close();
	}
	
	private static void debug_read_all_transV(File file, ByteOrder byteOrder) throws IOException {
		RandomAccessFile raFile = new RandomAccessFile(file, "r");
		int bytesPerRecord = transV_bytes_per_record;
		
		byte[] doubleBytes = new byte[8];
		byte[] intBytes = new byte[4];
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		DoubleBuffer doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		IntBuffer intBuff = intRecord.asIntBuffer();
		
		long file_length = raFile.length();
		long num = file_length / bytesPerRecord;
		
		int numPerBuffer = 10000;
		
		int bufferLen = bytesPerRecord * numPerBuffer;
		
		byte[] buffer = new byte[bufferLen];
		
		long transitionIndex = 0;
		long filePos = 0;
		
		int numBad = 0;
		
		outer:
		while (transitionIndex <= num) {
			for (int i=0; i<numPerBuffer; i++) {
				if (transitionIndex == num)
					break outer;
				if (i == 0) {
					long numLeft = num-transitionIndex;
					int numToRead = (int)Long.min(numLeft, numPerBuffer);
					raFile.readFully(buffer, 0, numToRead*bytesPerRecord);
				}
				int srcPos = i*bytesPerRecord;
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double time = doubleBuff.get(0);
				System.arraycopy(buffer, srcPos, intBytes, 0, 4);
				srcPos += 4;
				int patch = intBuff.get(0);
				int stateInt = buffer[srcPos]; // 1 byte int
				srcPos += 1;
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double vel = doubleBuff.get(0);
				if (stateInt == 2 && vel <= 0) {
					System.out.println("Bad velocity at transition "+transitionIndex+" at pos="+(filePos+i*bytesPerRecord));
					for (int j=i-1; j<numPerBuffer && j<=i+1; j++) {
						srcPos = j*bytesPerRecord;
						System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
						srcPos += 8;
						time = doubleBuff.get(0);
						System.arraycopy(buffer, srcPos, intBytes, 0, 4);
						srcPos += 4;
						patch = intBuff.get(0);
						stateInt = buffer[srcPos]; // 1 byte int
						srcPos += 1;
						System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
						srcPos += 8;
						vel = doubleBuff.get(0);
						System.out.println("\t"+(transitionIndex+(j-i))+":\t"+time+"\t"+patch+"\t"+stateInt+"\t"+vel);
						numBad++;
					}
				}
				transitionIndex++;
				filePos += srcPos;
			}
		}
		
		System.out.println("Num bad: "+numBad);
		
		raFile.close();
	}
	
	private boolean quiet;
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
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
		if (!quiet) System.out.print("Caching "+transPerRead+" transisions at index "+index+"...");
		read(index, transPerRead);
		if (!quiet) System.out.println("DONE");
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
			double vel = transV ? curVels[arrayIndex] : Double.NaN;
			trans.add(new RSQSimStateTime(patchID, time, curStates[arrayIndex], vel));
		}
		return trans;
	}
	
	public synchronized Iterable<RSQSimStateTime> getTransitionsIterable(double startTime, double endTime) {
		return getTransitionsIterable(Range.closed(startTime, endTime));
	}
	
	public synchronized Iterable<RSQSimStateTime> getTransitionsIterable(Range<Double> timeRange) {
		TransIterator it = new TransIterator(timeRange);
		return new Iterable<RSQSimStateTime>() {
			
			@Override
			public Iterator<RSQSimStateTime> iterator() {
				return it;
			}
		};
	}
	
	private class TransIterator implements Iterator<RSQSimStateTime> {
		
		private long index;
		private Range<Double> timeRange;
		private double startTime;
		
		public TransIterator(Range<Double> timeRange) {
			this.timeRange = timeRange;
			this.startTime = timeRange.lowerEndpoint();
			if (startTime < timeMarkers[0])
				index = 0;
			else
				index = getIndexBefore(startTime);
		}

		@Override
		public synchronized boolean hasNext() {
			if (index >= numTransitions)
				return false;
			try {
				int arrayIndex = readTransition(index);
				double time = curTimes[arrayIndex];
				while (time <= startTime && !timeRange.contains(time)) {
					index++;
					if (index >= numTransitions)
						return false;
					arrayIndex = readTransition(index);
					time = curTimes[arrayIndex];
				}
				
				return timeRange.contains(time);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		public synchronized RSQSimStateTime next() {
			Preconditions.checkState(hasNext()); // will advance index if before startTime
			int arrayIndex;
			try {
				arrayIndex = readTransition(index);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			index++;
			int patchID = curPatchIDs[arrayIndex]+1; // these are zero based but in events it's 1-based
			double time = curTimes[arrayIndex];
			Preconditions.checkState(timeRange.contains(time));
			double vel = transV ? curVels[arrayIndex] : Double.NaN;
			return new RSQSimStateTime(patchID, time, curStates[arrayIndex], vel);
		}
		
	}
	
	/**
	 * Loads transitions for this event, binned by patch. Checks are made to ensure that each transition
	 * is associated with this event by checking the next-slip-times of each patch (the time that they slip
	 * in the next event).
	 * @param event
	 * @return
	 * @throws IOException
	 */
	public Map<Integer, List<RSQSimStateTime>> getTransitions(RSQSimEvent event) throws IOException {
		return getTransitions(event, null);
	}
	
	/**
	 * Loads transitions for this event, binned by patch. Checks are made to ensure that each transition
	 * is associated with this event by checking the next-slip-times of each patch (the time that they slip
	 * in the next event).
	 * 
	 * You can supply an empty list in order to get them back in order as well, not binned by patch
	 * @param event
	 * @param transList empty list to be filled in with related transitions, in order
	 * @return
	 * @throws IOException
	 */
	
	public Map<Integer, List<RSQSimStateTime>> getTransitions(RSQSimEvent event, List<RSQSimStateTime> transList)
			throws IOException {
		Map<Integer, List<RSQSimStateTime>> patchTransitions = new HashMap<>();
		
		// this will store the time of the next event on this patch. so any transitions that happen on this patch
		// with time >= startTime and time < patchEndTime are associated with this event
		Map<Integer, Double> patchEndTimes = new HashMap<>();
		double startTime = event.getTime();
		
		if (transList != null)
			Preconditions.checkState(transList.isEmpty(),
					"passed in transList should be empty (will be populated in order)");
		
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
			List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
			double endTime = patchEndTimes.get(patchID);
			RSQSimState prevState = patchTimes.isEmpty() ? null : patchTimes.get(patchTimes.size()-1).getState();
			if (curStates[arrayIndex] == RSQSimState.LOCKED && (prevState == null || prevState == RSQSimState.LOCKED)) {
				// either the first transition in this event is to LOCKED, or this is a duplicate LOCKED
				// skip it as it's uneccesary, and could be during the next event
				continue;
			}
			boolean thisEvent = time < endTime;
//			if (patchID == 219277 && time >= 5.835340584208368E11 && time <= 5.83534058433055E11) {
//				System.out.println("DEBUG: "+time+"\t"+patchID+"\t"+curStates[arrayIndex]);
//				System.out.println("\t\tend time on this patch: "+patchEndTimes.get(patchID));
//				System.out.println("\t\tprev trans on this patch: "+patchTimes.get(patchTimes.size()-1));
//				System.out.flush();
//			}
			if (!thisEvent && curStates[arrayIndex] == RSQSimState.LOCKED && time == endTime) {
				// it's exactly at the patch end time (first trans in next event), but this is setting it to the LOCKED state
				// if we're not already LOCKED, keep this transition to close it out
				thisEvent = !patchTimes.isEmpty() && prevState != RSQSimState.LOCKED;
//				if (thisEvent)
//					System.out.println("Kept a locking next-event transition on patch "+patchID+". Trans index "+(index-1));
			}
//			if (patchID == 193480)
//				System.out.println("patch "+patchID+" t="+time+" ("+(time - startTime)+") "+curStates[arrayIndex].name()+" "+thisEvent);
			if (thisEvent) {
				// it's actually for this patch and event
				if (!patchTimes.isEmpty()) {
					// close out the previous one
					RSQSimStateTime prev = patchTimes.get(patchTimes.size()-1);
					prev.setEndTime(time);
				}
				double vel = transV ? curVels[arrayIndex] : Double.NaN;
				RSQSimStateTime trans = new RSQSimStateTime(patchID, time, curStates[arrayIndex], vel);
				patchTimes.add(trans);
				if (transList != null)
					transList.add(trans);
			}
		}
		// now close each patch
		for (int patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
			if (!patchTimes.isEmpty()) {
				RSQSimStateTime patchTime = patchTimes.get(patchTimes.size()-1);
				Preconditions.checkState(patchTime.getState() != RSQSimState.NUCLEATING_SLIP && patchTime.getState() != RSQSimState.EARTHQUAKE_SLIP,
						"Event %s, patch %s ended in non-locked state! Entered %s at relative t=%s. Relative next time: %s",
						event.getID(), patchID, patchTime.getState(), patchTime.getStartTime()-startTime, patchEndTimes.get(patchID) - startTime);
				patchTimes.remove(patchTimes.size()-1);
			}
		}
		
		return patchTransitions;
	}
	
	public long getIndexBefore(double time) {
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
	
	public boolean isVariableSlipSpeed() {
		return transV;
	}
	
	private static DecimalFormat timeDF = new DecimalFormat("0.000");
	public static void printTransitions(RSQSimEvent e, Map<Integer, List<RSQSimStateTime>> transitions) {
		printTransitions(e, transitions, System.out);
	}
	
	public static void printTransitions(RSQSimEvent e, Map<Integer, List<RSQSimStateTime>> transitions, PrintStream stream) {
		stream.println("Transitions for event "+e.getID()+" (M"+(float)e.getMagnitude()+")"
				+" at time "+(e.getTimeInYears())+" yr");
		double eventTime = e.getTime();
		for (int patchID : e.getAllElementIDs()) {
			List<RSQSimStateTime> patchTrans = transitions.get(patchID);
			stream.println("PATCH "+patchID);
			for (RSQSimStateTime trans : patchTrans) {
				double start = trans.getStartTime() - eventTime;
				double end = trans.getEndTime() - eventTime;
				RSQSimState state = trans.getState();
				String str = "\t"+timeDF.format(start)+"s\t=>\t"+timeDF.format(end)+"s\t"+state+"\t["+timeDF.format(end - start)+"s";
				if (state == RSQSimState.EARTHQUAKE_SLIP && !Double.isNaN(trans.getVelocity()))
					str += ", "+timeDF.format(trans.getVelocity())+" m/s";
				str += "]";
				stream.println(str);
			}
		}
		stream.flush();
	}
	
	public static void main(String[] args) throws IOException {
//		File d = new File("/home/kevin/Simulators/catalogs/bruce/rundir4860");
//		File tf = new File(d, "transV..out");
//		
//		RSQSimStateTransitionFileReader reader = new RSQSimStateTransitionFileReader(
//				tf, ByteOrder.LITTLE_ENDIAN, true);
//		List<RSQSimStateTime> myTrans = reader.getTransitions(5.835340584329015E11-60, 5.83534058433055E11);
//		for (RSQSimStateTime t : myTrans) {
//			double start = t.getStartTime();
//			RSQSimState state = t.getState();
//			if (t.getPatchID() == 219277)
//				System.out.println("\t"+start+"\t"+t.getPatchID()+"\t"+state+"\t"+t.getVelocity());
//		}
//		System.exit(0);
		if (args.length == 1 && args[0].equals("--hardcoded")) {
//			File dir = new File("/data/kevin/simulators/catalogs/baseCatalogSW_10");
//			File transFile = new File(dir, "trans.baseCatalogSW_10t.out");
//			File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//			String str = "--print-rup "+transFile.getAbsolutePath()+" "+geomFile.getAbsolutePath()
//				+" 32777581 32777582 32777583 32777584 32777585 32777586 32777587 32777588 32777589 327775810";
//			System.out.println("HARDCODED: "+str);
//			args = Iterables.toArray(Splitter.on(" ").split(str), String.class);
			File dir = new File("/home/kevin/Simulators/catalogs/bruce/rundir4860");
			File geomFile = new File(dir, "zfault_Deepen.in");
			File transFile = new File(dir, "transV..out");
			long startIndex = 983197248;
			int num = 100;
//			File dir = new File("/home/kevin/Simulators/catalogs/test_double_4860");
//			File transFile = new File(dir, "transV.combine.out");
//			long startIndex = 2428679190l;
//			int num = 10;
//			int startIndex = 310068232;
//			int num = 364216;
//			int startIndex = 13007950;
//			int num = 20;
//			int startIndex = 0;
//			int num = -1;
//			args = Iterables.toArray(Splitter.on(" ").split("--debug "+transFile.getAbsolutePath()+" "+startIndex+" "+num+" little"), String.class);

			String str = "--print-rup "+transFile.getAbsolutePath()+" "+geomFile.getAbsolutePath()
				+" 619852";
			args = Iterables.toArray(Splitter.on(" ").split(str), String.class);
		}
		if (args.length > 0 && args[0].equals("--debug")) {
			if (args.length != 5) {
				System.err.println("USAGE: --debug <trans-file> <start-index> <num> <little/big>");
				System.exit(2);
			}
			File file = new File(args[1]);
			boolean transV = file.getName().startsWith("transV");
			long startIndex = Long.parseLong(args[2]);
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
			
			System.out.println("TransV ? "+transV);
			
			if (transV && num <= 0) {
				System.out.println("Debugging bad velocities");
				debug_read_all_transV(file, byteOrder);
			} else {
				System.out.println("Reading "+num+" starting at "+startIndex);
				debug_read(file, startIndex, num, byteOrder, transV);
			}
			
			System.exit(0);
		} else if (args.length > 0 && args[0].equals("--print-rup")) {
			if (args.length < 4) {
				System.err.println("USAGE: --print-rup <trans-file> <geom-file> <rup-index-1> [...<rup-index-N>]");
				System.exit(2);
			}
			File transFile = new File(args[1]).getAbsoluteFile();
			boolean transV = transFile.getName().startsWith("transV");
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
			
			RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements, transV);
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
