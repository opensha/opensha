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

import com.google.common.base.Preconditions;

/**
 * Class to write to/read from a binary file which contains an array of double values. Values can be written in random
 * order. Must initialize on one thread/process before use.
 * @author kevin
 *
 */
public class BinaryDoubleScalarRandomAccessFile extends BinaryRandomAccessFile {
	
	private BufferWrapper<DoubleBuffer> singleDoubleBuffer;
	private double placeholder = Double.NaN;
	
	public BinaryDoubleScalarRandomAccessFile(File file, int numVals) {
		this(file, ByteOrder.BIG_ENDIAN, numVals);
	}
	
	public BinaryDoubleScalarRandomAccessFile(File file, ByteOrder byteOrder, int numVals) {
		super(file, byteOrder, numVals);
		
		singleDoubleBuffer = getDoubleBuffer(1);
	}
	
	public void setPlaceholder(double placeholder) {
		this.placeholder = placeholder;
	}
	
	public synchronized void write(int index, double val) throws IOException {
		singleDoubleBuffer.buffer.put(0, val);
		writeRecord(index, cloneBytes(singleDoubleBuffer.getBytes()));
	}

	@Override
	protected int getHeaderLen() {
		return 4;
	}

	@Override
	protected int getRecordLen() {
		return 8;
	}

	@Override
	protected byte[] getHeader() {
		BufferWrapper<IntBuffer> wrap = getIntBuffer(1);
		wrap.buffer.put(0, getNumRecords());
		return cloneBytes(wrap.bytes);
	}

	@Override
	protected synchronized byte[] getBlankRecord() {
		singleDoubleBuffer.buffer.put(0, placeholder);
		return cloneBytes(singleDoubleBuffer.bytes);
	}
	
	public synchronized boolean isCalculated(int index) throws IOException {
		byte[] record = readRecord(index);
		for (int i=0; i<record.length; i++)
			singleDoubleBuffer.getBytes()[i] = record[i];
		double val = singleDoubleBuffer.getBuffer().get(0);
		if (Double.isNaN(placeholder))
			return !Double.isNaN(val);
		return val != placeholder;
	}
	
	public static double[] readFile(File file) throws IOException {
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

		double[] array = new double[size];

		for (int i=0; i<size; i++)
			array[i] = in.readDouble();

		in.close();

		return array;
	}

}
