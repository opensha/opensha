package org.opensha.commons.util.binFile;

import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.ArbDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.geo.Location;

import com.google.common.io.LittleEndianDataOutputStream;

public class BinaryGeoDatasetRandomAccessFile extends BinaryXYZRandomAccessFile {

	public BinaryGeoDatasetRandomAccessFile(File file, ByteOrder byteOrder, int numRecords) {
		super(file, byteOrder, numRecords);
	}
	
	public void write(int index, Location loc, double val) throws IOException {
		write(index, loc.getLongitude(), loc.getLatitude(), val);
	}
	
	public static ArbDiscrGeoDataSet loadGeoDataset(File file) throws IOException {
		ArbDiscrXYZ_DataSet xyz = loadXYZ(file);
		ArbDiscrGeoDataSet geo = new ArbDiscrGeoDataSet(false);
		for (int i=0; i<xyz.size(); i++) {
			Point2D pt = xyz.getPoint(i);
			double val = xyz.get(i);
			geo.set(new Location(pt.getY(), pt.getX()), val);
		}
		return geo;
	}
	
	public static void writeGeoDataset(GeoDataSet data, ByteOrder byteOrder, File file) throws IOException {
		BufferedOutputStream buf = new BufferedOutputStream(new FileOutputStream(file));
		DataOutput dout;
		if (byteOrder == ByteOrder.BIG_ENDIAN)
			dout = new DataOutputStream(buf);
		else
			dout = new LittleEndianDataOutputStream(buf);
		
		dout.writeInt(data.size());
		
		for (int i=0; i<data.size(); i++) {
			Location loc = data.getLocation(i);
			dout.writeDouble(loc.getLongitude());
			dout.writeDouble(loc.getLatitude());
			dout.writeDouble(data.get(i));
		}
		
		buf.close();
	}

}
