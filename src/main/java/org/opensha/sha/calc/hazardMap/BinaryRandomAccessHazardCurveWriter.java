package org.opensha.sha.calc.hazardMap;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.binFile.BinaryRandomAccessFile;

import com.google.common.base.Preconditions;

public class BinaryRandomAccessHazardCurveWriter extends BinaryRandomAccessFile {
	
	private DiscretizedFunc xVals;
	private int numXVals;
	
	public BinaryRandomAccessHazardCurveWriter(File file, int numSites, DiscretizedFunc xVals) {
		this(file, ByteOrder.BIG_ENDIAN, numSites, xVals);
	}

	public BinaryRandomAccessHazardCurveWriter(File file, ByteOrder byteOrder, int numSites, DiscretizedFunc xVals) {
		super(file, byteOrder, numSites);
		this.xVals = xVals;
		this.numXVals = xVals.size();
	}

	@Override
	protected int getHeaderLen() {
		// header: [num x vals] [x1] [x2] ... [xN]. num is 4 byte int, vals are 8 bit doubles
		return 4+(numXVals)*8;
	}

	@Override
	protected int getRecordLen() {
		// record: lat, lon, and one for each curve point. all 8 byte doubles
		return (numXVals+2)*8;
	}

	@Override
	protected synchronized byte[] getHeader() {
		byte[] header = new byte[getHeaderLen()];
		int pos = 0;
		BufferWrapper<IntBuffer> buff = getIntBuffer(1);
		buff.getBuffer().put(0, numXVals);
		for (int i=0; i<buff.length(); i++)
			header[pos++] = buff.getBytes()[i];
		BufferWrapper<DoubleBuffer> singleDoubleBuff = getDoubleBuffer(1);
		for (int i=0; i<numXVals; i++) {
			singleDoubleBuff.getBuffer().put(0, xVals.getX(i));
			for (int j=0; j<singleDoubleBuff.length(); j++)
				header[pos++] = singleDoubleBuff.getBytes()[j];
		}
		return header;
	}

	@Override
	protected synchronized byte[] getBlankRecord() {
		byte[] record = new byte[getRecordLen()];
		int pos = 0;
		BufferWrapper<DoubleBuffer> singleDoubleBuff = getDoubleBuffer(1);
		singleDoubleBuff.getBuffer().put(0, Double.NaN);
		for (int i=0; i<numXVals+2; i++) {
			for (int j=0; j<singleDoubleBuff.length(); j++)
				record[pos++] = singleDoubleBuff.getBytes()[j];
		}
		return record;
	}
	
	public synchronized void writeCurve(int index, Location loc, DiscretizedFunc curve) throws IOException {
		Preconditions.checkState(curve.size() == numXVals);
		
		BufferWrapper<DoubleBuffer> recordBuff = getDoubleBuffer(numXVals+2);
		recordBuff.getBuffer().position(0);
		recordBuff.getBuffer().put(loc.getLatitude());
		recordBuff.getBuffer().put(loc.getLongitude());
		for (int i=0; i<numXVals; i++)
			recordBuff.getBuffer().put(curve.getY(i));
		writeRecord(index, cloneBytes(recordBuff.getBytes()));
	}
	
	public synchronized boolean isCurveCalculated(int index, Location loc) throws IOException {
		byte[] record = readRecord(index);
		BufferWrapper<DoubleBuffer> recordBuff = getDoubleBuffer(numXVals+2);
		for (int i=0; i<record.length; i++)
			recordBuff.getBytes()[i] = record[i];
		recordBuff.getBuffer().position(0);
		double lat = recordBuff.getBuffer().get();
		double lon = recordBuff.getBuffer().get();
		double val1 = recordBuff.getBuffer().get();
		return !Double.isNaN(val1) && (float)lat == (float)loc.getLatitude() && (float)lon == (float)loc.getLongitude();
	}

}
