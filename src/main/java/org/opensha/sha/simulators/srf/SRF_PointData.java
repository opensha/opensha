package org.opensha.sha.simulators.srf;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.earthquake.FocalMechanism;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class SRF_PointData {
	
	private Location loc;
	private FocalMechanism focal;
	private double area; // m^2
	private double tInit; // initiation time (s)
	private double dt; // time step (s)
	private double[] slipVels1;
	private double[] slipVels2;
	private double[] slipVels3;
	private double[] cumSlips1;
	private double[] cumSlips2;
	private double[] cumSlips3;
	private double totSlip1; // total slip (m) in u1 direction
	private double totSlip2; // total slip (m) in u2 direction
	private double totSlip3; // total slip (m) in u3 direction
	
	private double endTime;
	
	public SRF_PointData(Location loc, FocalMechanism focal, double area, double tInit, double dt,
			double totSlip1, double[] slipVels1) {
		this(loc, focal, area, tInit, dt, totSlip1, slipVels1, 0, new double[0], 0, new double[0]);
	}
	
	public SRF_PointData(Location loc, FocalMechanism focal, double area, double tInit, double dt,
			double totSlip1, double[] slipVels1, double totSlip2, double[] slipVels2, double totSlip3,
			double[] slipVels3) {
		this.loc = loc;
		this.focal = focal;
		this.area = area;
		this.tInit = tInit;
		this.dt = dt;
		
		endTime = tInit; // will be updated below in buildSlips
		
		this.totSlip1 = totSlip1;
		this.slipVels1 = slipVels1;
		this.cumSlips1 = buildSlips(slipVels1);
		
		this.totSlip2 = totSlip2;
		this.slipVels2 = slipVels2;
		this.cumSlips2 = buildSlips(slipVels2);
		
		this.totSlip3 = totSlip3;
		this.slipVels3 = slipVels3;
		this.cumSlips3 = buildSlips(slipVels3);
	}
	
	private double[] buildSlips(double[] slipVels) {
		double[] slips = new double[slipVels.length+1];
		
		slips[0] = 0d;
		for (int i=1; i<slips.length; i++) {
			double vel = slipVels[i-1];
			slips[i] = slips[i-1] + vel*dt;
			
			double t = tInit + i*dt;
			endTime = Math.max(endTime, t);
		}
		
		return slips;
	}
	
//	private void buildSlipFuncs(double[] slipVels, double totalSlip, DiscretizedFunc slipFunc, DiscretizedFunc velFunc) {
//		if (slipVels.length == 0)
//			return;
//		double curSlip = 0d;
//		double curTime = tInit;
//		
//		for (int i=0; i<slipVels.length; i++) {
//			slipFunc.set(curTime, curSlip);
//			velFunc.set(curTime, slipVels[i]);
//			
//			curTime += dt;
//			curSlip += slipVels[i]*dt;
//		}
//		
//		// now add last point
//		
//		// curSlip is now at where we would be after 1 additional time step
//		// this is likely more than the total slip, so we need to find out when it actually ended
//		double x0 = slipFunc.getMaxX();
//		double x1 = curTime;
//		double y0 = slipFunc.getY(slipFunc.size()-1);
//		double y1 = curSlip;
//		// can fail this test, depending on interpolation method
//		// needs external validation
////		Preconditions.checkState((float)curSlip >= (float)totalSlip,
////				"Bad totalSlip. Estimated %s at last time step, would be %s at the next, with total slip of %s",
////				(float)y0, (float)curSlip, (float)totalSlip);
//		if (y1 > y0) {
//			// need to add additional point
//			double timeMaxSlip = Interpolate.findX(x0, y0, x1, y0, totalSlip);
//			slipFunc.set(timeMaxSlip, totalSlip);
//			velFunc.set(timeMaxSlip, slipVels[slipVels.length-1]);
//		}
//		
//		this.endTime = Math.max(endTime, slipFunc.getMaxX());
//	}
	
	public Location getLocation() {
		return loc;
	}
	
	public double getStartTime() {
		return tInit;
	}
	
	public double getEndTime() {
		return endTime;
	}
	
	public double getDuration() {
		return getEndTime() - getStartTime();
	}
	
	public double[] getCumulativeSlips1() {
		return cumSlips1;
	}
	
	public double[] getCumulativeSlips2() {
		return cumSlips2;
	}
	
	public double[] getCumulativeSlips3() {
		return cumSlips3;
	}
	
	public double[] getVelocities1() {
		return slipVels1;
	}
	
	public double[] getVelocities2() {
		return slipVels2;
	}
	
	public double[] getVelocities3() {
		return slipVels3;
	}
	
	public double getTime(int index) {
		return tInit + dt*index;
	}
	
	private static final String split_regex = "\\s+";
	
	private static SRF_PointData fromPointLine(String line, double version) {
		line = line.trim();
		String[] split = line.split(split_regex);
		Preconditions.checkState(split.length >= 15, "Bad file line. Should have at least 15 space separated fields");
		int index = 0;
		double lon = Double.parseDouble(split[index++]);
		double lat = Double.parseDouble(split[index++]);
		double dep = Double.parseDouble(split[index++]);
		Location loc = new Location(lat, lon, dep);
		
		double strike = Double.parseDouble(split[index++]);
		double dip = Double.parseDouble(split[index++]);
		double area = Double.parseDouble(split[index++]);
		// area is in cm^2, convert
		area *= 0.0001;
		double tInit = Double.parseDouble(split[index++]);
		double dt = Double.parseDouble(split[index++]);
		if (version > 1)
			index += 2; // skip vs and den
		double rake = Double.parseDouble(split[index++]);
		// convert them from cm to m
		double totSlip1 = Double.parseDouble(split[index++])*0.01;
		int nt1 = Integer.parseInt(split[index++]);
		double totSlip2 = Double.parseDouble(split[index++])*0.01;
		int nt2 = Integer.parseInt(split[index++]);
		double totSlip3 = Double.parseDouble(split[index++])*0.01;
		int nt3 = Integer.parseInt(split[index++]);
		double[] slipVels1 = new double[nt1];
		double[] slipVels2 = new double[nt2];
		double[] slipVels3 = new double[nt3];
		// convert them from cm/s to m/s
		for (int i=0; i<nt1; i++)
			slipVels1[i] = Double.parseDouble(split[index++])*0.01;
		for (int i=0; i<nt2; i++)
			slipVels2[i] = Double.parseDouble(split[index++])*0.01;
		for (int i=0; i<nt3; i++)
			slipVels3[i] = Double.parseDouble(split[index++])*0.01;
		Preconditions.checkState(index == split.length, "Unxpected number values. Expected %s, encountered %s", index, split.length);
		
		FocalMechanism focal = new FocalMechanism(strike, dip, rake);
		
		return new SRF_PointData(loc, focal, area, tInit, dt, totSlip1, slipVels1, totSlip2, slipVels2, totSlip3, slipVels3);
	}
	
	public static List<SRF_PointData> readSRF(File srfFile) throws IOException {
		int numPoints = -1;
		List<SRF_PointData> points = new ArrayList<>();
		double version = -1;
		for (String line : Files.readLines(srfFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.isEmpty())
				continue;
			if (version < 0) {
				// first line is version
				version = Double.parseDouble(line);
				Preconditions.checkState(version > 0);
				continue;
			}
			if (line.startsWith("POINTS")) {
				Preconditions.checkState(numPoints < 0, "Duplicate POINTS line");
				numPoints = Integer.parseInt(line.split(" ")[1]);
				Preconditions.checkState(numPoints > 0, "Bad num points: %s", numPoints);
			} else if (numPoints > 0) {
//				System.out.println("Parsing line: "+line);
				points.add(fromPointLine(line, version));
			}
		}
		
		Preconditions.checkState(numPoints == points.size(), "Expected %s points, encountered %s", numPoints, points.size());
		
		return points;
	}
	
	private static final String sep = "\t";
	private static final DecimalFormat decimalDF = new DecimalFormat("0.######");
	private static final DecimalFormat expDF = new DecimalFormat("0.00000E00");
	
	public static void writeSRF(File srfFile, List<SRF_PointData> points, double version) throws IOException {
		FileWriter fw = new FileWriter(srfFile);
		
		Preconditions.checkState(version >= 1d && version <= 2d, "Can only write SRF version 1.0 and 2.0");
		fw.write((float)version+"\n");
		
		fw.write("POINTS "+points.size()+"\n");
		for (SRF_PointData point : points) {
			StringBuilder str = new StringBuilder();
			str.append(decimalDF.format(point.loc.getLongitude())).append(sep);
			str.append(decimalDF.format(point.loc.getLatitude())).append(sep);
			str.append(expDF.format(point.loc.getDepth())).append(sep);
			str.append(decimalDF.format(point.focal.getStrike())).append(sep);
			str.append(decimalDF.format(point.focal.getDip())).append(sep);
			str.append(expDF.format(point.area*10000)).append(sep); // m^2 to cm^2
			str.append(decimalDF.format(point.tInit)).append(sep);
			str.append(expDF.format(point.dt)).append(sep);
			if (version > 1) {
				str.append("-1").append(sep); // VS
				str.append("-1").append(sep); // DEN
			}
			str.append("\n");
			str.append(decimalDF.format(point.focal.getRake())).append(sep);
			str.append(decimalDF.format(point.totSlip1*100d)).append(sep); // m to cm
			str.append(point.slipVels1.length).append(sep);
			str.append(decimalDF.format(point.totSlip2*100d)).append(sep); // m to cm
			str.append(point.slipVels2.length).append(sep);
			str.append(decimalDF.format(point.totSlip3*100d)).append(sep); // m to cm
			str.append(point.slipVels3.length);
			double[] allSlips = new double[point.slipVels1.length + point.slipVels2.length + point.slipVels3.length];
			if (point.slipVels1.length > 0)
				System.arraycopy(point.slipVels1, 0, allSlips, 0, point.slipVels1.length);
			if (point.slipVels2.length > 0)
				System.arraycopy(point.slipVels2, 0, allSlips, point.slipVels1.length, point.slipVels2.length);
			if (point.slipVels3.length > 0)
				System.arraycopy(point.slipVels3, 0, allSlips, point.slipVels1.length+point.slipVels2.length, point.slipVels3.length);
			for (int i=0; i<allSlips.length; i++) {
				if (i % 6 == 0)
					str.append("\n");
				str.append(sep).append(expDF.format(allSlips[i]*100d));
			}
//			for (double s : point.slipVels1)
//				str.append(sep).append(writeDF.format(s*100d)); // m to cm
//			for (double s : point.slipVels2)
//				str.append(sep).append(writeDF.format(s*100d)); // m to cm
//			for (double s : point.slipVels3)
//				str.append(sep).append(writeDF.format(s*100d)); // m to cm
			str.append("\n");
			fw.write(str.toString());
		}
		
		fw.close();
	}
	
	public static void main(String[] args) throws IOException {
//		File srfFile = new File("/home/kevin/Simulators/srfs/UCERF3.D3.1.1km.tri.2.adjSigma3.380-480kyrs.event11630381.srf");
		File srfFile = new File("/home/kevin/Simulators/srfs/UCERF3.D3.1.1km.tri.2.adjSigma3.380-480kyrs.event11650379.srf");
		readSRF(srfFile);
	}

}