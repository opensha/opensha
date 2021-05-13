package org.opensha.commons.util.binFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public abstract class BinaryRandomAccessFile {

	private File file;
	private RandomAccessFile ra;
	private ByteOrder byteOrder;
	
	private int numRecords;

	public BinaryRandomAccessFile(File file, ByteOrder byteOrder, int numRecords) {
		this.file = file;
		this.byteOrder = byteOrder;
		this.numRecords = numRecords;
	}
	
	public int getNumRecords() {
		return numRecords;
	}

	private synchronized RandomAccessFile getRA() throws IOException {
		if (ra == null) {
			if (!file.exists()) {
				// initialize everything, synchonized to this class
				System.out.println("Warning, not initialized. Initializing with first archive");
				initialize();
			}
			ra = new RandomAccessFile(file, "rws");
		}
		return ra;
	}
	
	protected abstract int getHeaderLen();
	
	protected abstract int getRecordLen();
	
	protected long calcFilePos(int index) {
		return getHeaderLen() + getRecordLen()*(long)index;
	}
	
	protected abstract byte[] getHeader();
	
	protected abstract byte[] getBlankRecord();
	
	/**
	 * Must be called before first write, but if using multiple threads/processes, only call once.
	 * @throws IOException
	 */
	public void initialize() throws IOException {
		long expectedLen = calcFilePos(numRecords);
		
		if (file.exists() && file.length() == expectedLen)
			return;
		
		// partial file, delete
		if (file.exists())
			Files.move(file, new File(file.getAbsolutePath()+".bad"));
		
		Preconditions.checkState(!file.exists());
		
		Preconditions.checkState(expectedLen < Integer.MAX_VALUE);
		byte[] bytes = new byte[(int)expectedLen];
		
		// write header
		int pos = 0;
		byte[] header = getHeader();
		Preconditions.checkState(header.length == getHeaderLen());
		for (int i=0; i<header.length; i++)
			bytes[pos++] = header[i];
		
		byte[] blank = getBlankRecord();
		Preconditions.checkState(blank.length == getRecordLen());
		
		while (pos < expectedLen) {
			for (int i=0; i<blank.length; i++)
				bytes[pos++] = blank[i];
		}
		
		// now write
		ra = new RandomAccessFile(file, "rws");
		ra.write(bytes);
	}
	
	protected synchronized void writeRecord(int index, byte[] record) throws IOException {
		Preconditions.checkState(index < numRecords);
		Preconditions.checkState(record.length == getRecordLen());
		
		RandomAccessFile ra = getRA();
		ra.seek(calcFilePos(index));
		ra.write(record);
	}
	
	protected synchronized byte[] readRecord(int index) throws IOException {
		byte[] record = new byte[getRecordLen()];
		RandomAccessFile ra = getRA();
		ra.seek(calcFilePos(index));
		ra.readFully(record, 0, record.length);
		return record;
	}
	
	public void close() throws IOException {
		RandomAccessFile ra = getRA();
		ra.close();
	}
	
	// Convencience methods
	
	protected static byte[] cloneBytes(byte[] in) {
		byte[] out = new byte[in.length];
		for (int i=0; i<in.length; i++)
			out[i] = in[i];
		return out;
	}
	
	protected class BufferWrapper<E extends Buffer> {
		E buffer;
		byte[] bytes;
		
		public BufferWrapper(E buffer, byte[] bytes) {
			this.buffer = buffer;
			this.bytes = bytes;
		}
		
		public E getBuffer() {
			return buffer;
		}
		
		public byte[] getBytes() {
			return bytes;
		}
		
		public int length() {
			return bytes.length;
		}
	}
	
	private Map<Integer, BufferWrapper<IntBuffer>> intBuffMap = Maps.newHashMap();
	private Map<Integer, BufferWrapper<LongBuffer>> longBuffMap = Maps.newHashMap();
	private Map<Integer, BufferWrapper<FloatBuffer>> floatBuffMap = Maps.newHashMap();
	private Map<Integer, BufferWrapper<DoubleBuffer>> doubleBuffMap = Maps.newHashMap();
	
	protected synchronized BufferWrapper<IntBuffer> getIntBuffer(int numInts) {
		BufferWrapper<IntBuffer> wrap = intBuffMap.get(numInts);
		if (wrap == null) {
			byte[] bytes = new byte[numInts*4];
			ByteBuffer buff = buildBuff(bytes);
			wrap = new BufferWrapper<IntBuffer>(buff.asIntBuffer(), bytes);
			intBuffMap.put(numInts, wrap);
		}
		return wrap;
	}
	
	protected synchronized BufferWrapper<LongBuffer> getLongBuffer(int numLongs) {
		BufferWrapper<LongBuffer> wrap = longBuffMap.get(numLongs);
		if (wrap == null) {
			byte[] bytes = new byte[numLongs*8];
			ByteBuffer buff = buildBuff(bytes);
			wrap = new BufferWrapper<LongBuffer>(buff.asLongBuffer(), bytes);
			longBuffMap.put(numLongs, wrap);
		}
		return wrap;
	}
	
	protected synchronized BufferWrapper<FloatBuffer> getFloatBuffer(int numFloats) {
		BufferWrapper<FloatBuffer> wrap = floatBuffMap.get(numFloats);
		if (wrap == null) {
			byte[] bytes = new byte[numFloats*4];
			ByteBuffer buff = buildBuff(bytes);
			wrap = new BufferWrapper<FloatBuffer>(buff.asFloatBuffer(), bytes);
			floatBuffMap.put(numFloats, wrap);
		}
		return wrap;
	}
	
	protected synchronized BufferWrapper<DoubleBuffer> getDoubleBuffer(int numDoubles) {
		BufferWrapper<DoubleBuffer> wrap = doubleBuffMap.get(numDoubles);
		if (wrap == null) {
			byte[] bytes = new byte[numDoubles*8];
			ByteBuffer buff = buildBuff(bytes);
			wrap = new BufferWrapper<DoubleBuffer>(buff.asDoubleBuffer(), bytes);
			doubleBuffMap.put(numDoubles, wrap);
		}
		return wrap;
	}
	
	private ByteBuffer buildBuff(byte[] bytes) {
		ByteBuffer buff = ByteBuffer.wrap(bytes);
		buff.order(byteOrder);
		return buff;
	}

}