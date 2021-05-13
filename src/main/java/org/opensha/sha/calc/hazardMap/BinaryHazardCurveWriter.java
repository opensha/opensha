package org.opensha.sha.calc.hazardMap;

import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class BinaryHazardCurveWriter {
	
	private File outputFile;
	
	public BinaryHazardCurveWriter(File outputFile) {
		this.outputFile = outputFile;
	}
	
	public void writeCurves(Map<Location, ? extends DiscretizedFunc> map) throws IOException {
		List<Location> sites = Lists.newArrayList();
		List<DiscretizedFunc> curves = Lists.newArrayList();
		
		for (Location loc : map.keySet()) {
			sites.add(loc);
			curves.add(map.get(loc));
		}
		
		writeCurves(curves, sites);
	}
	
	public void writeCurves(List<? extends DiscretizedFunc> curves, List<Location> sites) throws IOException {
		Preconditions.checkArgument(curves.size() == sites.size());
		Preconditions.checkArgument(!curves.isEmpty());
		
		OutputStream out = new FileOutputStream(outputFile);
		out = new BufferedOutputStream(out);
		DataOutputStream dout = new DataOutputStream(out);
		
		DiscretizedFunc xVals = curves.get(0);
		// num x vals
		dout.writeInt(xVals.size());
		for (int i=0; i<xVals.size(); i++)
			dout.writeDouble(xVals.getX(i));
		
		// now write all of the y values
		for (int i=0; i<curves.size(); i++) {
			DiscretizedFunc curve = curves.get(i);
			Preconditions.checkState(curve.size() == xVals.size());
			Location loc = sites.get(i);
			
			dout.writeDouble(loc.getLatitude());
			dout.writeDouble(loc.getLongitude());
			for (Point2D pt : curve)
				dout.writeDouble(pt.getY());
		}
		
		dout.close();
	}

}
