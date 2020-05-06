package org.opensha.sha.simulators.srf;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.primitives.Ints;

public class RSQSimStateTransitionFileReader {
	
	private RandomAccessFile raFile;
	private long numTransitions;
	
	private ByteOrder byteOrder;
	
	private byte[] doubleBytes = new byte[8];
	private DoubleBuffer doubleBuff;
	private byte[] floatBytes = new byte[4];
	private FloatBuffer floatBuff;
	private byte[] intBytes = new byte[4];
	private IntBuffer intBuff;
	
	private double firstTime = Double.NaN;
	private double lastTime = Double.NaN;
	
	private double[] timeMarkers;
	private long[] indexMarkers;
	private int transPerRead;
	
	private long curIndex;
	// the read method will reuse this buffer for full length reads
	private byte[] fullBuffer;
	private RSQSimStateTime[] curTrans;
	private int batchReads = 0;
	private int backwardReads = 0;
	
	private TransVersion version;
	private int bytesPerRecord;
	
	private Map<Integer, Double> patchFixedVels;
	
	private static final boolean binary_search_within_bucket = false; // slow, extra reads
	private static final int num_time_func_precalcs = 1000;
//	private static final int max_trans_per_read = 100000;
	private static final int max_trans_per_read = Integer.MAX_VALUE;
	
	// keep looking for the end of slip events at most 7 days since event start (super conservative)
	private static double MAX_POST_READ = SimulatorUtils.SECONDS_PER_YEAR*7d/365d;
	
	public enum TransVersion {
		// 8 byte double for time, 4 byte int for patch ID, 1 byte int for state
		ORIGINAL(13),
		// 8 byte double for time, 4 byte int for patch ID, 1 byte int for state, 8 byte double for slip velocity
		TRANSV(21),
		// 8 byte double for time, 4-byte float relative time, 4-byte int event ID, 4 byte int for patch ID,
		// 1 byte int for state, 4 byte float for slip velocity
		CONSOLIDATED_RELATIVE(25);
		
		public final int bytesPerRecord;

		private TransVersion(int bytesPerRecord) {
			this.bytesPerRecord = bytesPerRecord;
		}
	}
	
	public RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder, TransVersion version) throws IOException {
		this(file, byteOrder, null, version);
	}
	
	public RSQSimStateTransitionFileReader(File file, List<SimulatorElement> elements)
			throws IOException {
		this(file, null, elements, null);
	}
	
	public RSQSimStateTransitionFileReader(File file, List<SimulatorElement> elements, TransVersion version)
			throws IOException {
		this(file, null, elements, version);
	}
	
	public RSQSimStateTransitionFileReader(File file, List<SimulatorElement> elements, ByteOrder byteOrder)
			throws IOException {
		this(file, byteOrder, elements, null);
	}
	
	private RSQSimStateTransitionFileReader(File file, ByteOrder byteOrder, List<SimulatorElement> elements,
			TransVersion version) throws IOException {
		raFile = new RandomAccessFile(file, "r");
		long length = raFile.length();
		this.version = version;
		this.byteOrder = byteOrder;
		if (byteOrder == null || version == null)
			detectEndianAndVersion(elements);
		bytesPerRecord = this.version.bytesPerRecord;
		numTransitions = length / bytesPerRecord;
		if (length % bytesPerRecord > 0)
			System.err.println("Warning, unexpected number of bytes in transitions file, possibly truncated. "
					+ "Extra: "+(length % bytesPerRecord));
		
		System.out.println("Detected "+numTransitions+" transitions");
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer floatRecord = ByteBuffer.wrap(floatBytes);
		floatRecord.order(byteOrder);
		floatBuff = floatRecord.asFloatBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		intBuff = intRecord.asIntBuffer();
	}
	
	private void detectEndianAndVersion(List<SimulatorElement> elements) throws IOException {
		TransVersion[] testVersions;
		if (this.version == null) {
			System.out.println("Need to detect transition file version");
			testVersions = TransVersion.values();
		} else {
			testVersions = new TransVersion[] { this.version };
		}
		
		boolean D = false;
		
		ByteOrder[] testOrders;
		if (this.byteOrder == null) {
			System.out.println("Need to detect transition file byte order");
			testOrders = new ByteOrder[] { ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN };
		} else {
			testOrders = new ByteOrder[] { this.byteOrder };
		}
		
		int numTestsEach = 100;
		Random r = new Random();

		byte[] intRecordBuffer = new byte[4];
		byte[] doubleRecordBuffer = new byte[8];
		
		Table<TransVersion, ByteOrder, Boolean> testResults = HashBasedTable.create();
		
		for (TransVersion testVersion : testVersions) {
			for (ByteOrder testOrder : testOrders) {
				IntBuffer intRecord = ByteBuffer.wrap(intRecordBuffer).order(testOrder).asIntBuffer();
				DoubleBuffer doubleRecord = ByteBuffer.wrap(doubleRecordBuffer).order(testOrder).asDoubleBuffer();
				
				if (D) System.out.println("Testing "+testVersion+", "+testOrder);
				
				boolean valid = true;
				long numVals = raFile.length() / testVersion.bytesPerRecord;
				Preconditions.checkState(numVals > 1, "Not enough values to test");
				if (numVals > Integer.MAX_VALUE)
					numVals = Integer.MAX_VALUE;
				DiscretizedFunc indexTimeFunc = new ArbitrarilyDiscretizedFunc();
				
				for (int i=0; i<numTestsEach && valid; i++) {
					long index = r.nextInt((int)numVals);
					long pos = index * testVersion.bytesPerRecord;
					
					long timePos;
					long patchPos;
					switch (testVersion) {
					case ORIGINAL:
						timePos = pos;
						patchPos = pos+8;
						break;
					case TRANSV:
						timePos = pos;
						patchPos = pos+8;
						break;
					case CONSOLIDATED_RELATIVE:
						timePos = pos;
						patchPos = pos+16;
						break;

					default:
						throw new IllegalStateException("Unsupported transition version: "+version);
					}
					
					raFile.seek(timePos);
					raFile.read(doubleRecordBuffer);
					
					double absTime = doubleRecord.get(0);
					indexTimeFunc.set((double)index, absTime);
					
					raFile.seek(patchPos);
					raFile.read(intRecordBuffer);
					
					// IDs in this file are 0-based, add 1
					int patchID = intRecord.get(0)+1;
					valid = RSQSimFileReader.isValidPatchID(patchID, elements);
					
					if (D && i < 5)
						System.out.println(i+". p="+patchID+"\tt="+absTime);
				}
				if (D) System.out.println("\tpatch valid? "+valid);
				// check that index/time func is monotonically increasing
				for (int n=1; valid && n<indexTimeFunc.size(); n++)
					valid = indexTimeFunc.getY(n) >= indexTimeFunc.getY(n-1);
				if (D) System.out.println("\tfully valid? "+valid);
				testResults.put(testVersion, testOrder, valid);
			}
		}
		
		boolean anyValid = false;
		for (Cell<TransVersion, ByteOrder, Boolean> cell : testResults.cellSet()) {
			if (cell.getValue()) {
				Preconditions.checkState(!anyValid,
						"Multiple valid trans file configurations found, cannot detect. Both of these are valid:\n"
						+ "\t%s, %s\n\t%s, %s", version, byteOrder, cell.getRowKey(), cell.getColumnKey());
				version = cell.getRowKey();
				byteOrder = cell.getColumnKey();
				anyValid = true;
				System.out.println("Detected "+version+", "+byteOrder);
			}
		}
		
		if (!anyValid) {
			// debug
			System.out.println("Failed to detect! Debugging, firstPatch="+elements.get(0).getID()
					+", lastPatch="+elements.get(elements.size()-1).getID());
			long[] debugPositions = { 0l, 8l, 12l, 16l, 20l, 21l };
			for (ByteOrder testOrder : testOrders) {
				System.out.println("Debugging detection with "+testOrder);
				byte[] buffer = new byte[8];
				IntBuffer intRecord = ByteBuffer.wrap(buffer).order(testOrder).asIntBuffer();
				FloatBuffer floatRecord = ByteBuffer.wrap(buffer).order(testOrder).asFloatBuffer();
				DoubleBuffer doubleRecord = ByteBuffer.wrap(buffer).order(testOrder).asDoubleBuffer();
				for (long pos : debugPositions) {
					System.out.println("POSITION: "+pos);
					raFile.seek(pos);
					raFile.read(buffer);
					System.out.println("\tByte:\t"+buffer[0]);
					System.out.println("\tInt:\t"+intRecord.get(0));
					System.out.println("\tFloat:\t"+floatRecord.get(0));
					System.out.println("\tDouble:\t"+doubleRecord.get(0));
				}
			}
		}
		
		Preconditions.checkState(anyValid, "Could not detect transition file type");
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
			timeMarkers[i] = read(index, 1)[0].absoluteTime;
			indexMarkers[i] = index;
			if (i > 0)
				Preconditions.checkState(timeMarkers[i] >= timeMarkers[i-1],
					"File is out of order: %s, %s", timeMarkers[i-1], timeMarkers[i]);
		}
		System.out.println("Done initializing time/index markers: ["+timeMarkers[0]/SimulatorUtils.SECONDS_PER_YEAR
				+" "+timeMarkers[timeMarkers.length-1]/SimulatorUtils.SECONDS_PER_YEAR+"]");
	}
	
	public void setPatchFixedVelocities(Map<Integer, Double> patchFixedVels) {
		this.patchFixedVels = patchFixedVels;
	}
	
	private boolean retainNegatives = false;
	public void setRetainNegativeSlips(boolean retainNegatives) {
		this.retainNegatives = retainNegatives;
	}
	
	public TransVersion getVersion() {
		return version;
	}
	
	private synchronized RSQSimStateTime[] read(long index, int num) throws IOException {
//		System.out.println("index: "+index);
		Preconditions.checkState(index >= 0, "Bad index: %s", index);
		long seek = index*bytesPerRecord;
		raFile.seek(seek);
		if (index + num > numTransitions)
			num = (int)(numTransitions - index);
		
		int len = bytesPerRecord * num;

		RSQSimStateTime[] ret;
		byte[] buffer;
		if (num == transPerRead && (curTrans == null || curTrans.length == num)) {
			// we're doing a full read, reuse buffers to reduce garbage collection
			if (fullBuffer == null)
				fullBuffer = new byte[len];
			buffer = fullBuffer;
			if (curTrans == null)
				curTrans = new RSQSimStateTime[num];
			else
				Preconditions.checkState(curTrans.length == num,
					"Reusing curTrans, but it's of wrong len: %s != %s", curTrans.length, num);
			ret = curTrans;
		} else {
			// this is a one off short read, create new ones
			buffer = new byte[len];
			ret = new RSQSimStateTime[num];
		}
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			try {
				int srcPos = i*bytesPerRecord;
				double absoluteTime;
				int patchID, eventID;
				float relativeTime, velocity;
				RSQSimState state;
				if (version == TransVersion.ORIGINAL || version == TransVersion.TRANSV) {
					absoluteTime = readDouble(buffer, srcPos); srcPos += 8;
					relativeTime = Float.NaN;
					patchID = readInt(buffer, srcPos)+1; srcPos += 4; // file is 0-based
					eventID = -1;
					int stateInt = buffer[srcPos]; srcPos += 1; // 1 byte int
					state = RSQSimState.forInt(stateInt);
					if (version == TransVersion.TRANSV) {
						velocity = (float)readDouble(buffer, srcPos);
					} else {
						Preconditions.checkNotNull(patchFixedVels,
								"Old transitions format, must set individual patch velocities with "
								+ "setPatchFixedVelocities(Map<Integer,Double)");
						Double patchVel = patchFixedVels.get(patchID);
						Preconditions.checkNotNull(patchVel, "No velocity for patch %s", patchID);
						velocity = patchVel.floatValue();
					}
				} else if (version == TransVersion.CONSOLIDATED_RELATIVE) {
					absoluteTime = readDouble(buffer, srcPos); srcPos += 8;
					relativeTime = readFloat(buffer, srcPos); srcPos += 4;
					eventID = readInt(buffer, srcPos); srcPos += 4; // file is 1-based
					patchID = readInt(buffer, srcPos)+1; srcPos += 4; // file is 0-based
					int stateInt = buffer[srcPos]; srcPos += 1; // 1 byte int
					state = RSQSimState.forInt(stateInt);
					velocity = readFloat(buffer, srcPos);
				} else {
					throw new IllegalStateException("Unsupported transition version: "+version);
				}
				
				if (i > 0)
					Preconditions.checkState(absoluteTime >= ret[i-1].absoluteTime,
						"File is out of order: %s, %s", ret[i-1].absoluteTime, absoluteTime);
				if (velocity < 0f && state == RSQSimState.EARTHQUAKE_SLIP && !retainNegatives) {
					System.err.println("WARNING: Bad (negative) velocity, setting to 1e-6 as temporary fix");
					velocity = 1e-6f;
				}
				ret[i] = new RSQSimStateTime(absoluteTime, relativeTime, eventID, patchID, state, velocity);
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
		
		return ret;
	}
	
	private double readDouble(byte[] buffer, int index) {
		System.arraycopy(buffer, index, doubleBytes, 0, 8);
		return doubleBuff.get(0);
	}
	
	private float readFloat(byte[] buffer, int index) {
		System.arraycopy(buffer, index, floatBytes, 0, 4);
		return floatBuff.get(0);
	}
	
	private int readInt(byte[] buffer, int index) {
		System.arraycopy(buffer, index, intBytes, 0, 4);
		return intBuff.get(0);
	}
	
	private static void debug_read(File file, long startIndex, int num, ByteOrder byteOrder, TransVersion version)
			throws IOException {
		RandomAccessFile raFile = new RandomAccessFile(file, "r");
		int bytesPerRecord = version.bytesPerRecord;
		long seek = startIndex*bytesPerRecord;
		System.out.println("Seeking to: "+seek);
		raFile.seek(seek);

		byte[] doubleBytes = new byte[8];
		byte[] floatBytes = new byte[4];
		byte[] intBytes = new byte[4];
		
		ByteBuffer doubleRecord = ByteBuffer.wrap(doubleBytes);
		doubleRecord.order(byteOrder);
		DoubleBuffer doubleBuff = doubleRecord.asDoubleBuffer();
		
		ByteBuffer floatRecord = ByteBuffer.wrap(floatBytes);
		floatRecord.order(byteOrder);
		FloatBuffer floatBuff = floatRecord.asFloatBuffer();
		
		ByteBuffer intRecord = ByteBuffer.wrap(intBytes);
		intRecord.order(byteOrder);
		IntBuffer intBuff = intRecord.asIntBuffer();
		
		int len = bytesPerRecord * num;
		
		byte[] buffer = new byte[len];
		raFile.readFully(buffer, 0, len);
		
		for (int i=0; i<num; i++) {
			int srcPos = i*bytesPerRecord;
			
			if (version == TransVersion.ORIGINAL || version == TransVersion.TRANSV) {
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double time = doubleBuff.get(0);
				System.arraycopy(buffer, srcPos, intBytes, 0, 4);
				srcPos += 4;
				int patch = intBuff.get(0);
				int stateInt = buffer[srcPos]; // 1 byte int
				srcPos += 1;
				
				if (version == TransVersion.TRANSV) {
					System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
					srcPos += 8;
					double vel = doubleBuff.get(0);
					System.out.println((startIndex+i)+":\t"+time+"\t"+patch+"\t"+stateInt+"\t"+vel);
				} else {
					System.out.println((startIndex+i)+":\t"+time+"\t"+patch+"\t"+stateInt);
				}
			} else if (version == TransVersion.CONSOLIDATED_RELATIVE) {
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double time = doubleBuff.get(0);
				System.arraycopy(buffer, srcPos, doubleBytes, 0, 8);
				srcPos += 8;
				double relTime = doubleBuff.get(0);
				System.arraycopy(buffer, srcPos, intBytes, 0, 4);
				srcPos += 4;
				int event = intBuff.get(0);
				System.arraycopy(buffer, srcPos, intBytes, 0, 4);
				srcPos += 4;
				int patch = intBuff.get(0);
				int stateInt = buffer[srcPos]; // 1 byte int
				srcPos += 1;
				System.arraycopy(buffer, srcPos, floatBytes, 0, 4);
				srcPos += 8;
				float vel = floatBuff.get(0);
				System.out.println((startIndex+i)+":\t"+time+"\t"+relTime+"\t"+event
						+"\t"+patch+"\t"+stateInt+"\t"+vel);
			} else {
				throw new IllegalStateException("Unsupported transition version: "+version);
			}
		}
		
		raFile.close();
	}
	
	private static void debug_read_all_transV(File file, ByteOrder byteOrder) throws IOException {
		RandomAccessFile raFile = new RandomAccessFile(file, "r");
		int bytesPerRecord = TransVersion.TRANSV.bytesPerRecord;
		
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
	private synchronized RSQSimStateTime readTransition(long index) throws IOException {
		if (timeMarkers == null)
			initTimeMarkers();
		long arrayIndex = index - curIndex;
		if (curTrans != null && arrayIndex >= 0l && arrayIndex < Integer.MAX_VALUE
				&& arrayIndex < curTrans.length)
			// it's already in memory
			return curTrans[(int)arrayIndex];
		// need to load it into memory
//		System.out.println("index="+index+" arrayIndex="+arrayIndex+" curTrans.length="
//				+(curTrans == null ? "null" : curTrans.length));
		if (!quiet) System.out.print("Caching "+transPerRead+" transitions at index "+index+"...");
		curIndex = index;
		curTrans = read(index, transPerRead);
		batchReads++;
		if (arrayIndex < 0l)
			backwardReads++;
		if (!quiet) System.out.println("DONE");
		return curTrans[0];
	}
	
	/**
	 * This will return all transitions between the specified start and end times (inclusive). It will also
	 * continue reading past the end time until the last EARTHQUAKE_SLIP (state 2) transition returned has
	 * been closed, i.e., that patch transitioned again. Thus every EARTHQUAKE_SLIP transitions will have its
	 * duration populated, unless the end of the file is encountered, but it will give up once it has read a week
	 * past the end time.
	 * 
	 * @param startTime
	 * @param endTime
	 * @return All transitions between the specified start and end times (inclusive)
	 * @throws IOException
	 */
	public List<RSQSimStateTime> getTransitions(double startTime, double endTime) throws IOException {
		List<RSQSimStateTime> trans = new ArrayList<>();
		
		long index = getIndexBefore(startTime);
		Map<Integer, RSQSimStateTime> prevPatchTrans = new HashMap<>();
		int numOpenSlip = 0; // number of EQ slip transitions which are still open
		while (index < numTransitions) {
			RSQSimStateTime transition = readTransition(index++);
			double time = transition.absoluteTime;
			if (time < startTime)
				continue;
			int patchID = transition.patchID;
			if (prevPatchTrans.containsKey(patchID)) {
				RSQSimStateTime prevTrans = prevPatchTrans.remove(patchID);
				prevTrans.setNextTransition(transition);
				if (prevTrans.state == RSQSimState.EARTHQUAKE_SLIP)
					numOpenSlip--;
			}
			if (time <= endTime) {
				if (transition.state == RSQSimState.EARTHQUAKE_SLIP)
					numOpenSlip++;
				prevPatchTrans.put(patchID, transition);
				trans.add(transition);
			} else {
				// see if we're done
				if (numOpenSlip == 0 || time > (endTime+MAX_POST_READ))
					// we've closed all previous transitions and can stop
					break;
				// otherwise we need to keep reading to close all slip transitions
			}
		}
		if (numOpenSlip != 0)
			System.err.println("WARNING: "+numOpenSlip+" EARTHQUAKE_SLIP transitions don't have durations");
		return trans;
	}
	
	public synchronized Iterable<RSQSimStateTime> getTransitionsIterable(double startTime, double endTime) throws IOException {
		return getTransitionsIterable(Range.closed(startTime, endTime));
	}
	
	public synchronized Iterable<RSQSimStateTime> getTransitionsIterable(Range<Double> timeRange) throws IOException {
		if (timeMarkers == null)
			initTimeMarkers();
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
		private double maxTime;
		
		private LinkedList<RSQSimStateTime> curList;
		private Map<Integer, RSQSimStateTime> patchPrevSlips;
		
		public TransIterator(Range<Double> timeRange) throws IOException {
			this.timeRange = timeRange;
			this.startTime = timeRange.lowerEndpoint();
			if (startTime < timeMarkers[0])
				index = 0;
			else
				index = getIndexBefore(startTime);
			
			curList = new LinkedList<>();
			patchPrevSlips = new HashMap<>();
			
			maxTime = timeRange.upperEndpoint() + MAX_POST_READ;
		}

		@Override
		public synchronized boolean hasNext() {
			if (!curList.isEmpty())
				// we have transitions ready
				return true;
			if (index >= numTransitions)
				return false;
			try {
				RSQSimStateTime trans = readTransition(index);
				double time = trans.absoluteTime;
				while (time <= startTime && !timeRange.contains(time)) {
					index++;
					if (index >= numTransitions)
						return false;
					trans = readTransition(index);
					time = trans.absoluteTime;
				}
				
				return timeRange.contains(time);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		private boolean isFirstReady() {
			RSQSimStateTime first = curList.peekFirst();
			return first.hasDuration() || first.state != RSQSimState.EARTHQUAKE_SLIP
					|| curList.peekLast().absoluteTime - first.absoluteTime > MAX_POST_READ;
		}

		@Override
		public synchronized RSQSimStateTime next() {
			Preconditions.checkState(hasNext()); // will advance index if before startTime
			while (index < numTransitions && (curList.isEmpty() || !isFirstReady())) {
				try {
					RSQSimStateTime trans = readTransition(index);
					index++;
					if (patchPrevSlips.containsKey(trans.patchID)) {
						RSQSimStateTime prevTrans = patchPrevSlips.remove(trans.patchID);
						prevTrans.setNextTransition(trans);
					}
					if (timeRange.contains(trans.absoluteTime)) {
						// we're in the range
						
						// keep track of this patch to close it later
						patchPrevSlips.put(trans.patchID, trans);
						curList.add(trans);
					} else {
						// we're after the range, but closing old slip events
						if (trans.absoluteTime > maxTime) {
							System.err.println("Didn't close all patches but read past max post read, bailing");
							break;
						}
					}
					if (index == numTransitions && !isFirstReady())
						System.err.println("Didn't close all patches but read through to end of transitions file");
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			
			return curList.removeFirst();
		}
		
	}
	
	/**
	 * Loads transitions for this event, binned by patch. Checks are made to ensure that each transition
	 * is associated with this event, either by checking eventID if available in trans file, or checking
	 * for the next event time.
	 * @param event
	 * @return
	 * @throws IOException
	 */
	public Map<Integer, List<RSQSimStateTime>> getTransitions(RSQSimEvent event) throws IOException {
		return getTransitions(event, null);
	}
	
	/**
	 * Loads transitions for this event, binned by patch. Checks are made to ensure that each transition
	 * is associated with this event, either by checking eventID if available in trans file, or checking
	 * for the next event time.
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
		
		double startTime = event.getTime();
		double nextEventTime = event.getNextEventTime();
		Preconditions.checkState(!Double.isNaN(nextEventTime));
		
		if (transList != null)
			Preconditions.checkState(transList.isEmpty(),
					"passed in transList should be empty (will be populated in order)");
		
		for (EventRecord rec : event) {
			Preconditions.checkState(rec instanceof RSQSimEventRecord);
			RSQSimEventRecord rsRecord = (RSQSimEventRecord)rec;
			int[] recPatchIDs = rsRecord.getElementIDs();
			Preconditions.checkNotNull(recPatchIDs);
			for (int i=0; i<recPatchIDs.length; i++)
				patchTransitions.put(recPatchIDs[i], new ArrayList<>());
		}
		
		double maxEndTime = startTime + MAX_POST_READ;
		
		int eventID = event.getID();
		
		long index = getIndexBefore(startTime);
//		
		int debugEventID = -999;
//		int debugEventID = 5;
		final boolean debug = debugEventID == eventID;
		int numPreReads = 0;
		int numDuringReads = 0;
		int numPostReads = 0;
		int numAssociated = 0;
		if (debug) {
			System.out.println("Reading transitions for event "+event.getID()
				+", a M"+(float)event.getMagnitude()+" at "+startTime+", next event is at "+nextEventTime);
			System.out.println("\tEvent patches: "+Joiner.on(",").join(Ints.asList(event.getAllElementIDs())));
		}
		
		// number of EARTHQUAKE_SLIP transitions which don't have their durations set yet
		int numOpen = 0;
		while (index < numTransitions) {
			RSQSimStateTime trans = readTransition(index++);
			
			int patchID = trans.patchID;
			
			double time = trans.absoluteTime;
			boolean related = patchTransitions.containsKey(patchID);
			
			if (trans.eventID >= 0) {
				if (trans.eventID < eventID) {
					if (debug)
						System.out.println(index+" is BEFORE: "+trans+" related ? "+related);
					// before this event
					numPreReads++;
					continue;
				} else if (trans.eventID > eventID) {
					// after this event
					if (debug)
						System.out.println(index+" is AFTER: "+trans+", closing "+numOpen
								+" patches. related ? "+related);
					numPostReads++;
					if (time > maxEndTime || numOpen == 0)
						break;
				} else {
					numDuringReads++;
				}
				if (debug)
					System.out.println(index+" is DURING: "+trans+" related ? "+related);
			} else {
				if (time < startTime) {
					if (debug)
						System.out.println(index+" is BEFORE: "+trans+" related ? "+related);
					// before this event
					numPreReads++;
					continue;
				} else if (time >= nextEventTime) {
					if (debug)
						System.out.println(index+" is AFTER: "+trans+", closing "+numOpen
								+" patches. related ? "+related);
					numPostReads++;
					if (numOpen == 0)
						// closed all patches, done
						break;
				} else {
					if (debug)
						System.out.println(index+" is DURING: "+trans+" related ? "+related);
					numDuringReads++;
				}
				if (time >= maxEndTime) {
					// after the possible transition times for any patch in this event
					numPostReads++;
					break;
				}
			}
			
			if (!related)
				// it's for an unrelated patch
				continue;
			
//			System.out.println("\t"+trans);
			
			List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
			
			RSQSimStateTime prevTrans = patchTimes.isEmpty() ? null : patchTimes.get(patchTimes.size()-1);
			if (prevTrans != null) {
				// close it
				if (prevTrans.state == RSQSimState.EARTHQUAKE_SLIP)
					numOpen--;
				prevTrans.setNextTransition(trans);
			}
			if (trans.state == RSQSimState.LOCKED && (prevTrans == null || prevTrans.state == RSQSimState.LOCKED)) {
				// either the first transition in this event is to LOCKED, or this is a duplicate LOCKED
				// skip it as it's unnecessary, and could be during the next event
				continue;
			}
			boolean thisEvent;
			if (trans.eventID < 0) {
				// use times
				thisEvent = time < nextEventTime;
			} else {
				// already checked event IDs above
				thisEvent = eventID == trans.eventID;
				if (thisEvent)
					Preconditions.checkState((float)time <= (float)nextEventTime && (float)time >= (float)startTime,
							"Matched transitions with eventIDs, but times don't make sense.\n"
							+ "\tTrans time: %s, event time: %s, next event time: %s\n\ttrans: %s",
							time, startTime, nextEventTime, trans);
			}
			
			if (!thisEvent && trans.state == RSQSimState.LOCKED && time == nextEventTime) {
				// it's exactly at the patch end time (first trans in next event), but this is setting it to the LOCKED state
				// if we're not already LOCKED, keep this transition to close it out
				thisEvent = prevTrans != null && prevTrans.state != RSQSimState.LOCKED;
//				if (thisEvent)
//					System.out.println("Kept a locking next-event transition on patch "+patchID+". Trans index "+(index-1));
			}
			if (thisEvent) {
				// it's actually for this patch and event
				if (!Double.isFinite(trans.relativeTime)) {
					// old transitions file, set relative time
					float relativeTime = (float)(trans.absoluteTime - startTime);
					trans = new RSQSimStateTime(trans.absoluteTime, relativeTime, eventID,
							patchID, trans.state, trans.velocity);
				}
				if (trans.eventID < 0)
					trans = new RSQSimStateTime(trans.absoluteTime, trans.relativeTime, eventID,
						patchID, trans.state, trans.velocity);
				if (transList != null)
					transList.add(trans);
				patchTimes.add(trans);
				numAssociated++;
				if (trans.state == RSQSimState.EARTHQUAKE_SLIP)
					numOpen++;
			}
		}
		// now close each patch
		for (int patchID : patchTransitions.keySet()) {
			List<RSQSimStateTime> patchTimes = patchTransitions.get(patchID);
			if (!patchTimes.isEmpty()) {
				RSQSimStateTime patchTime = patchTimes.get(patchTimes.size()-1);
				if (patchTime.state == RSQSimState.NUCLEATING_SLIP || patchTime.state == RSQSimState.EARTHQUAKE_SLIP)
					Preconditions.checkState(patchTime.hasDuration(), "Duration not populated for patch %s in event %s",
							patchID, eventID);
				// now trim anything off the end that's not related to earthquake slip
				// keep the last earthquake slip, or a locking event immediately after
				int lastIndex = 0;
				RSQSimState prevState = null;
				for (int i=0; i<patchTimes.size(); i++) {
					RSQSimState myState = patchTimes.get(i).state;
					if (myState == RSQSimState.EARTHQUAKE_SLIP)
						lastIndex = i;
					else if (prevState != null && prevState == RSQSimState.EARTHQUAKE_SLIP && myState == RSQSimState.LOCKED)
						lastIndex = i;
					prevState = myState;
				}
				while (patchTimes.size() > lastIndex+1)
					patchTimes.remove(patchTimes.size()-1);
			}
			if (debug) System.out.println("Patch "+patchID+" has "+patchTimes.size()+" transitions");
		}
		if (debug) {
			System.out.println("Read stats:");
			System.out.println("\t"+numPreReads+" transitions read before event");
			System.out.println("\t"+numDuringReads+" transitions read during event");
			System.out.println("\t"+numPostReads+" transitions read after event");
			System.out.println("\t"+numAssociated+" transitions were associated with event");
		}
		
		return patchTransitions;
	}
	
//	public long getIndexBefore(double time) {
//		int index = Arrays.binarySearch(timeMarkers, time);
//		if (index < 0)
//			index = -index - 2; // -2 because we want before
//		Preconditions.checkState(index >= 0 && index < timeMarkers.length);
//		if (time == timeMarkers[index] && index > 0)
//			// it's an exact match, go one before to be safe
//			index--;
//		return indexMarkers[index];
//	}
	
	public synchronized long getIndexBefore(double time) throws IOException {
		if (timeMarkers == null)
			initTimeMarkers();
		if (time < timeMarkers[0])
			return 0l;
		// find the bucket
//		System.out.println("Looking for index before "+time);
		int bucketIndex = Arrays.binarySearch(timeMarkers, time);
		if (bucketIndex < 0)
			bucketIndex = -bucketIndex - 2; // -2 because we want before
		Preconditions.checkState(bucketIndex >= 0 && bucketIndex < timeMarkers.length);
		if (time == timeMarkers[bucketIndex] && bucketIndex > 0) {
			// it's an exact match, go one before to be safe
//			System.out.println("\texact match, backing up");
			bucketIndex--;
		}
		long minIndex = indexMarkers[bucketIndex];
		
		// see if we're in already in memory, if so do the skip ahead here
		if (curTrans != null && (minIndex - curIndex) < Integer.MAX_VALUE-curTrans.length) {
			long nextArrayIndex = 1l + minIndex - curIndex;
			while (nextArrayIndex >= 0l && nextArrayIndex < curTrans.length) {
				if (curTrans[(int)nextArrayIndex].absoluteTime < time) {
					minIndex++;
					nextArrayIndex++;
				} else {
					break;
				}
			}
		}
		if (binary_search_within_bucket) {
			long maxIndex = bucketIndex == timeMarkers.length-1 ?
					numTransitions-1 : indexMarkers[bucketIndex+1];
			int iterations = 0;
			// now do a binary search within the bucket
			while (maxIndex - minIndex > 10l) {
				long guess = (minIndex + maxIndex) / 2l;
				double myTime = read(guess, 1)[0].absoluteTime;
				if (myTime >= time) {
					maxIndex = guess;
				} else {
					minIndex = guess;
				}
				iterations++;
			}
		}
		return minIndex;
	}
	
	public synchronized double getFirstTransitionTime() throws IOException {
		if (Double.isNaN(firstTime)) {
			firstTime = read(0, 1)[0].absoluteTime;
		}
		return firstTime;
	}
	
	public synchronized double getLastTransitionTime() throws IOException {
		if (Double.isNaN(lastTime)) {
			lastTime = read(numTransitions-1, 1)[0].absoluteTime;
		}
		return lastTime;
	}
	
	private static DecimalFormat timeDF = new DecimalFormat("0.000");
	public static void printTransitions(RSQSimEvent e, Map<Integer, List<RSQSimStateTime>> transitions) {
		printTransitions(e, transitions, System.out);
	}
	
	public static void printTransitions(RSQSimEvent e, Map<Integer, List<RSQSimStateTime>> transitions, PrintStream stream) {
		stream.println("Transitions for event "+e.getID()+" (M"+(float)e.getMagnitude()+")"
				+" at time "+(e.getTimeInYears())+" yr");
		for (int patchID : e.getAllElementIDs()) {
			List<RSQSimStateTime> patchTrans = transitions.get(patchID);
			stream.println("PATCH "+patchID);
			for (RSQSimStateTime trans : patchTrans) {
				double start = trans.relativeTime;
				double end = trans.hasDuration() ? start + trans.getDuration() : Double.NaN;
				RSQSimState state = trans.state;
				String str = "\t"+timeDF.format(start)+"s\t=>\t"+timeDF.format(end)+"s\t"+state+"\t["+timeDF.format(end - start)+"s";
				if (state == RSQSimState.EARTHQUAKE_SLIP)
					str += ", "+timeDF.format(trans.velocity)+" m/s";
				str += "]";
				stream.println(str);
			}
		}
		stream.flush();
	}
	
	public static void main(String[] args) throws IOException {
//		File d = new File("/home/kevin/Simulators/catalogs/bruce/rundir4950");
//		File tf = new File(d, "transV..out");
//		File d = new File("/home/kevin/Simulators/catalogs/singleSS");
//		File tf = new File(d, "trans.test.out");
		File d = new File("/home/kevin/Simulators/catalogs/bruce/rundir4983.01");
		File tf = new File(d, "trans..out");
		TransVersion version = TransVersion.CONSOLIDATED_RELATIVE;
		
		RSQSimStateTransitionFileReader reader = new RSQSimStateTransitionFileReader(
				tf, ByteOrder.LITTLE_ENDIAN, version);
//		int maxNum = 100;
//		int count = 0;
//		int minPatchID = Integer.MAX_VALUE;
//		int minEventID = Integer.MAX_VALUE;
//		int maxPatchID = 0;
//		int maxEventID = 0;
//		for (RSQSimStateTime trans : reader.getTransitionsIterable(reader.getFirstTransitionTime(),
//				reader.getLastTransitionTime())) {
//			if (count < maxNum)
//				System.out.println(count+". "+trans);
//			count++;
//			minPatchID = Integer.min(minPatchID, trans.patchID);
//			maxPatchID = Integer.max(maxPatchID, trans.patchID);
//			minEventID = Integer.min(minEventID, trans.eventID);
//			maxEventID = Integer.max(maxEventID, trans.eventID);
//		}
//		System.out.println("Patch ID range: "+minPatchID+" "+maxPatchID);
//		System.out.println("Event ID range: "+minEventID+" "+maxEventID);
		
//		long start = 803576214l;
//		for (int i=0; i<10; i++) {
//			long index = start+i;
//			System.out.println(index+": "+reader.read(index, 1)[0]);
//		}
		reader.read(803181015, 803985);
		
//		List<RSQSimStateTime> myTrans = reader.getTransitions(5.835340584329015E11-60, 5.83534058433055E11);
//		for (RSQSimStateTime t : myTrans) {
//			if (t.patchID == 219277)
//				System.out.println("\t"+t.absoluteTime+"\t"+t.patchID+"\t"+t.state+"\t"+t.velocity);
//		}
		System.exit(0);
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
			version = file.getName().toLowerCase().contains("transv")
					? TransVersion.TRANSV : TransVersion.CONSOLIDATED_RELATIVE;
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
			
			System.out.println("Version: "+version);
			
			if (version == TransVersion.TRANSV && num <= 0) {
				System.out.println("Debugging bad velocities");
				debug_read_all_transV(file, byteOrder);
			} else {
				System.out.println("Reading "+num+" starting at "+startIndex);
				debug_read(file, startIndex, num, byteOrder, version);
			}
			
			System.exit(0);
		} else if (args.length > 0 && args[0].equals("--print-rup")) {
			if (args.length < 4) {
				System.err.println("USAGE: --print-rup <trans-file> <geom-file> <rup-index-1> [...<rup-index-N>]");
				System.exit(2);
			}
			File transFile = new File(args[1]).getAbsoluteFile();
			version = transFile.getName().toLowerCase().contains("transv")
					? TransVersion.TRANSV : TransVersion.CONSOLIDATED_RELATIVE;
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
			
			RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements, version);
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
