package org.opensha.commons.util.binFile;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import org.opensha.commons.data.xyz.ArbDiscrXYZ_DataSet;

import com.google.common.base.Preconditions;

public class BinaryXYZRandomAccessFile extends BinaryRandomAccessFile {
	
	private double placeholder = Double.NaN;
	private BufferWrapper<DoubleBuffer> threeDoubleBuffer;

	public BinaryXYZRandomAccessFile(File file, ByteOrder byteOrder, int numRecords) {
		super(file, byteOrder, numRecords);
		
		threeDoubleBuffer = getDoubleBuffer(3);
	}

	@Override
	protected int getHeaderLen() {
		// integer with location count
		return 4;
	}

	@Override
	protected int getRecordLen() {
		// 3 doubles: x, y, value
		return 8*3;
	}

	@Override
	protected byte[] getHeader() {
		BufferWrapper<IntBuffer> wrap = getIntBuffer(1);
		wrap.buffer.put(0, getNumRecords());
		return cloneBytes(wrap.bytes);
	}
	
	public void setPlaceholder(double placeholder) {
		this.placeholder = placeholder;
	}

	@Override
	protected synchronized byte[] getBlankRecord() {
		threeDoubleBuffer.buffer.position(0);
		threeDoubleBuffer.buffer.put(placeholder);
		threeDoubleBuffer.buffer.put(placeholder);
		threeDoubleBuffer.buffer.put(placeholder);
		return cloneBytes(threeDoubleBuffer.bytes);
	}
	
	public synchronized void write(int index, double x, double y, double z) throws IOException {
		threeDoubleBuffer.buffer.position(0);
		threeDoubleBuffer.buffer.put(x);
		threeDoubleBuffer.buffer.put(y);
		threeDoubleBuffer.buffer.put(z);
		writeRecord(index, cloneBytes(threeDoubleBuffer.getBytes()));
	}
	
	public synchronized boolean isCalculated(int index) throws IOException {
		byte[] record = readRecord(index);
		for (int i=0; i<record.length; i++)
			threeDoubleBuffer.getBytes()[i] = record[i];
		threeDoubleBuffer.getBuffer().position(0);
		double x = threeDoubleBuffer.getBuffer().get();
		double y = threeDoubleBuffer.getBuffer().get();
		double z = threeDoubleBuffer.getBuffer().get();
		return !isPlaceholder(x) && !isPlaceholder(y) && !isPlaceholder(z);
	}
	
	private boolean isPlaceholder(double val) {
		if (Double.isNaN(placeholder))
			return Double.isNaN(val);
		return val == placeholder;
	}
	
	public static ArbDiscrXYZ_DataSet loadXYZ(File file) throws IOException {
		long length = file.length();

		Preconditions.checkState(length > 0, "file is empty!");
		Preconditions.checkState((length-4) % 8 == 0, "file size after header isn't evenly divisible by 8, " +
		"thus not a sequence of double values.");
		
		InputStream is = new FileInputStream(file);

		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();
		
		ArbDiscrXYZ_DataSet xyz = new ArbDiscrXYZ_DataSet();

		for (int i=0; i<size; i++) {
			double x = in.readDouble();
			double y = in.readDouble();
			double z = in.readDouble();
			xyz.set(x, y, z);
		}

		in.close();
		
		return xyz;
	}

}
