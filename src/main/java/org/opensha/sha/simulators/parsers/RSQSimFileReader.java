package org.opensha.sha.simulators.parsers;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.poi.util.LittleEndianByteArrayInputStream;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.PlaneUtils;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.RSQSimEventRecord;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.TriangularElement;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.iden.LogicalAndRupIden;
import org.opensha.sha.simulators.iden.MagRangeRuptureIdentifier;
import org.opensha.sha.simulators.iden.RegionIden;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.iden.SectionIDIden;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LittleEndianDataInputStream;

public class RSQSimFileReader {
	
	public static List<SimulatorElement> readGeometryFile(File geomFile,int longZone, char latZone) throws IOException {
		return readGeometryFile(new FileInputStream(geomFile), longZone, latZone);
	}
	
	public static List<SimulatorElement> readGeometryFile(URL url, int longZone, char latZone) throws IOException {
		return readGeometryFile(url.openStream(), longZone, latZone);
	}
	
	public static List<SimulatorElement> readGeometryFile(InputStream is, int longZone, char latZone) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		
		String line = reader.readLine();
		// strip header lines
		while (line != null && line.startsWith("#"))
			line = reader.readLine();
		
		boolean triangular = isTriangular(line);
		
		List<SimulatorElement> elements = Lists.newArrayList();
		
		int elemID = 1;
		int vertexID = 1;
		
		while (line != null) {
			StringTokenizer tok = new StringTokenizer(line);
			
			try {
				if (triangular) {
					double x1 = Double.parseDouble(tok.nextToken());
					double y1 = Double.parseDouble(tok.nextToken());
					double z1 = Double.parseDouble(tok.nextToken());
					Location loc1 = utmToLoc(longZone, latZone, x1, y1, z1);
					double x2 = Double.parseDouble(tok.nextToken());
					double y2 = Double.parseDouble(tok.nextToken());
					double z2 = Double.parseDouble(tok.nextToken());
					Location loc2 = utmToLoc(longZone, latZone, x2, y2, z2);
					double x3 = Double.parseDouble(tok.nextToken());
					double y3 = Double.parseDouble(tok.nextToken());
					double z3 = Double.parseDouble(tok.nextToken());
					Location loc3 = utmToLoc(longZone, latZone, x3, y3, z3);
					
					double rake = Double.parseDouble(tok.nextToken());
					
					String slipRateStr = tok.nextToken();
					double slipRate = slipRateStr.equals("NA") ? 0d : Double.parseDouble(slipRateStr);
					
					int sectNum = -1;
					String sectName = null;
					
					if (tok.hasMoreTokens()) {
						String sectNumStr = tok.nextToken();
						if (!sectNumStr.equals("NA")) {
							if (sectNumStr.endsWith(".0"))
								sectNumStr = sectNumStr.substring(0, sectNumStr.length()-2);
							sectNum = Integer.parseInt(sectNumStr);
							if (tok.hasMoreTokens())
								sectName = tok.nextToken();
						}
					}
					
					Vertex[] vertices = new Vertex[3];
					vertices[0] = new Vertex(loc1.getLatitude(), loc1.getLongitude(), loc1.getDepth(), vertexID++);
					vertices[1] = new Vertex(loc2.getLatitude(), loc2.getLongitude(), loc2.getDepth(), vertexID++);
					vertices[2] = new Vertex(loc3.getLatitude(), loc3.getLongitude(), loc3.getDepth(), vertexID++);
					
					int numAlongStrike = -1;
					int numDownDip = -1;
					
					// convert to m/yr form m/s
					slipRate = slipRate*SimulatorUtils.SECONDS_PER_YEAR;
					
					double aseisFactor = 0d;
					FocalMechanism focalMechanism = calcTriangularMech(new Vector3D(x1, y1, z1),
							new Vector3D(x2, y2, z2), new Vector3D(x3, y3, z3), rake);
					
					SimulatorElement elem = new TriangularElement(elemID++, vertices, sectName, -1, sectNum,
							numAlongStrike, numDownDip, slipRate, aseisFactor, focalMechanism);
					elements.add(elem);
				} else {
					// rectangular
					
					// this is the center
					double x = Double.parseDouble(tok.nextToken());
					double y = Double.parseDouble(tok.nextToken());
					double z = Double.parseDouble(tok.nextToken());
					double l = Double.parseDouble(tok.nextToken());
					double w = Double.parseDouble(tok.nextToken());
					Location center = utmToLoc(longZone, latZone, x, y, z);
					
					double strike = Double.parseDouble(tok.nextToken());
					double dip = Double.parseDouble(tok.nextToken());
					double rake = Double.parseDouble(tok.nextToken());
					double slipRate = Double.parseDouble(tok.nextToken());
					
					int sectNum = -1;
					String sectName = null;
					
					if (tok.hasMoreTokens()) {
						sectNum = Integer.parseInt(tok.nextToken());
						if (tok.hasMoreTokens())
							sectName = tok.nextToken();
					}
					
					double halfWidthKM = w*0.5/1000d;
					double halfLengthKM = l*0.5/1000d;
					
					Vertex[] vertices = new Vertex[4];
					LocationVector v = new LocationVector(strike, halfLengthKM, 0d);
					Location centerLeft = LocationUtils.location(center, v);
					v.reverse();
					Location centerRight = LocationUtils.location(center, v);
					
					// a list of 4 vertices, where the order is as follows as viewed 
					// from the positive side of the fault: 0th is top left, 1st is lower left,
					// 2nd is lower right, and 3rd is upper right (counter clockwise)
					if (dip == 90) {
						// simple case
						vertices[0] = new Vertex(centerRight.getLatitude(), centerRight.getLongitude(),
								center.getDepth()+halfWidthKM, vertexID++);
						vertices[1] = new Vertex(centerRight.getLatitude(), centerRight.getLongitude(),
								center.getDepth()-halfWidthKM, vertexID++);
						vertices[2] = new Vertex(centerLeft.getLatitude(), centerLeft.getLongitude(),
								center.getDepth()-halfWidthKM, vertexID++);
						vertices[3] = new Vertex(centerLeft.getLatitude(), centerLeft.getLongitude(),
								center.getDepth()+halfWidthKM, vertexID++);
					} else {
						// more complicated
						// TODO untested!
						double dipDir = strike + 90;
						double dipDirRad = Math.toRadians(dipDir);
						double widthKM = w/1000d;
						double horizontal = widthKM * Math.cos(dipDirRad);
						double vertical = widthKM * Math.sin(dipDirRad);
						// oriented to go down dip
						v = new LocationVector(dipDir, horizontal, vertical);
						Location botLeft = LocationUtils.location(centerLeft, v);
						Location botRight = LocationUtils.location(centerRight, v);
						v.reverse();
						Location topLeft = LocationUtils.location(centerLeft, v);
						Location topRight = LocationUtils.location(centerRight, v);
						
						vertices[0] = new Vertex(topLeft.getLatitude(), topLeft.getLongitude(), topLeft.getDepth(), vertexID++);
						vertices[1] = new Vertex(botLeft.getLatitude(), botLeft.getLongitude(), botLeft.getDepth(), vertexID++);
						vertices[2] = new Vertex(botRight.getLatitude(), botRight.getLongitude(), botRight.getDepth(), vertexID++);
						vertices[3] = new Vertex(topRight.getLatitude(), topRight.getLongitude(), topRight.getDepth(), vertexID++);
					}
					
					int numAlongStrike = -1;
					int numDownDip = -1;
					
					// convert to m/yr form m/s
					slipRate = slipRate*SimulatorUtils.SECONDS_PER_YEAR;
					
					double aseisFactor = 0d;
					FocalMechanism focalMechanism = new FocalMechanism(strike, dip, rake);
					
					boolean perfectRect = (float)l == (float)w;
					
					SimulatorElement elem = new RectangularElement(elemID++, vertices, sectName, -1, sectNum,
							numAlongStrike, numDownDip, slipRate, aseisFactor, focalMechanism, perfectRect);
					elements.add(elem);
				}
			} catch (RuntimeException e) {
				System.err.println("Error parsing geometry: "+e.getMessage()+"\n\tOffending line: "+line);
				throw e;
			}
			
			line = reader.readLine();
		}
		
		// see if this is a subsection based catalog
		boolean subsections = true;
		Map<String, Integer> faultIDsMap = Maps.newHashMap();
		int curFaultID = 1;
		for (SimulatorElement elem : elements) {
			String sectName = elem.getSectionName();
			if (sectName == null || !sectName.contains("Subsection")) {
				subsections = false;
				break;
			}
			String faultName = sectName.substring(0, sectName.indexOf("Subsection"));
			while (faultName.endsWith(","))
				faultName = faultName.substring(0, faultName.length()-1);
			Integer faultID = faultIDsMap.get(faultName);
			if (faultID == null) {
				faultID = curFaultID++;
				faultIDsMap.put(faultName, faultID);
			}
			// also add mapping from this subsection
			faultIDsMap.put(sectName, faultID);
		}
		if (subsections) {
			for (SimulatorElement elem : elements)
				elem.setFaultID(faultIDsMap.get(elem.getSectionName()));
		}
		
		return elements;
	}
	
	private static boolean isTriangular(String line) {
		// 0	1	2	3	4	5		6	7		8		9			10		11			12
		// triangular line:
		// x1	y1	z1	x2	y2	z2		x3	y3		z3		rake		slip	[sectNum]	[sectName]
		// rectangular line:
		// x	y	z	l	w	strike	dip	rake	slip	[sectNum]	[sectName]
		
		StringTokenizer tok = new StringTokenizer(line);
		int num = tok.countTokens();
		Preconditions.checkState(num >= 9);
		
		if (num < 11)
			// always rectangular
			return false;
		// 11 column file can be rectangular with section info or triangular without
		// if rectangular, 11th column must be a non-numeric string. So if it can be parsed to a double,
		// then it's the slip column in a triangular file
		List<String> tokens = Lists.newArrayList();
		while (tok.hasMoreTokens())
			tokens.add(tok.nextToken());
		String tok11 = tokens.get(10);
		try {
			Double.parseDouble(tok11);
			// can be parsed, must be slip rate
			return true;
		} catch (NumberFormatException e) {
			// can't be parsed, must be section name
			return false;
		}
	}
	
//	private static FocalMechanism calcTriangularMech(Vertex[] vertices, double rake) {
//		Location loc1 = vertices[0];
//		Location loc2 = vertices[1];
//		Location loc3 = vertices[2];
//		double avgLat = (loc1.getLatitude() + loc2.getLatitude() + loc3.getLatitude()) / 3;
//		double avgLon = (loc1.getLongitude() + loc2.getLongitude() + loc3.getLongitude()) / 3;
//		Location avgLoc = new Location(avgLat, avgLon);
//		double latDegPerKM = GeoTools.degreesLatPerKm(avgLoc);
//		double lonDegPerKM = GeoTools.degreesLonPerKm(avgLoc);
//		
////		double[][] vertices = new double[][] {
////			{ 0.0, 0.0, -loc1.getDepth() },
////			{ deltaLonKM(loc1, loc2), deltaLatKM(loc1, loc2), -loc2.getDepth() },
////			{ deltaLonKM(loc1, loc3), deltaLatKM(loc1, loc3), -loc3.getDepth() } };
//		
//		Vector3D c1 = new Vector3D(0d, 0d, -loc1.getDepth());
//		Vector3D c2 = new Vector3D((loc2.getLongitude()-loc1.getLongitude())/lonDegPerKM,
//				(loc2.getLatitude()-loc1.getLatitude())/latDegPerKM, -loc2.getDepth());
//		Vector3D c3 = new Vector3D((loc3.getLongitude()-loc1.getLongitude())/lonDegPerKM,
//				(loc3.getLatitude()-loc1.getLatitude())/latDegPerKM, -loc3.getDepth());
//		
////		Vector3D c1 = new Vector3D(vertices[0].getLongitude(), vertices[0].getLatitude(), -vertices[0].getDepth());
////		Vector3D c2 = new Vector3D(vertices[1].getLongitude(), vertices[1].getLatitude(), -vertices[1].getDepth());
////		Vector3D c3 = new Vector3D(vertices[2].getLongitude(), vertices[2].getLatitude(), -vertices[2].getDepth());
//		
//		return calcTriangularMech(c1, c2, c3, rake);
//	}
	
	/**
	 * Algorithm provided by Keith Richards-Dinger via e-mail 9/19/17. Alternative implementation exists in PlaneUtils,
	 * but this matches his definition perfectly (and does calculation in UTM).
	 * @param c1
	 * @param c2
	 * @param c3
	 * @param rake
	 * @return
	 */
	private static FocalMechanism calcTriangularMech(Vector3D c1, Vector3D c2, Vector3D c3, double rake) {
		Vector3D dx1 = c2.subtract(c1);
		Vector3D dx2 = c3.subtract(c1);
		
		Vector3D nu = Vector3D.crossProduct(dx1, dx2);
		if (nu.getZ() < 0)
			nu = nu.scalarMultiply(-1d);
		double area = 0.5*Math.sqrt(nu.dotProduct(nu));
		nu.scalarMultiply(1d/(2*area));
		
		double strike = -(180d/Math.PI)*Math.atan2(nu.getY(), nu.getX());
		while (strike < 0)
			strike += 360;
		
		double dip = 90d - (180d/Math.PI)*Math.atan2(nu.getZ(), Math.sqrt(nu.getX()*nu.getX() + nu.getY()*nu.getY()));
		
		return new FocalMechanism(strike, dip, rake);
	}
	
	private static Location utmToLoc(int longZone, char latZone, double x, double y, double z) {
		UTM utm = new UTM(longZone, latZone, x, y);
		WGS84 wgs84 = new WGS84(utm);
		double lat = wgs84.getLatitude();
		double lon = wgs84.getLongitude();
		if (longZone > 50 && lon < 0)
			lon += 360d;
		return new Location(lat, lon, -z/1000d);
	}
	
	public static void main(String[] args) throws IOException {
//		File dir = new File("/home/kevin/Simulators/UCERF3_125kyrs");
////		File geomFile = new File(dir, "UCERF3.1km.tri.flt");
//		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
		
//		File dir = new File("/home/kevin/Simulators/bruce/rundir1420");
//		File geomFile = new File(dir, "zfault_Deepen.in");
		
//		File dir = new File("/home/kevin/Simulators/UCERF3_interns/UCERF3sigmahigh");
//		File dir = new File("/home/kevin/Simulators/UCERF3_interns/combine340");
		File dir = new File("/home/kevin/Simulators/catalogs/SWminAdefaultB");
		File geomFile = new File(dir, "UCERF3.D3.1.1km.tri.2.flt");
//		File dir = new File("/data/kevin/simulators/catalogs/bruce/rundir2142");
//		File geomFile = new File(dir, "zfault_Deepen.in");
		
		List<SimulatorElement> elements = readGeometryFile(geomFile, 11, 'S');
		System.out.println("Loaded "+elements.size()+" elements");
//		for (Location loc : elements.get(0).getVertices())
//			System.out.println(loc);
		MinMaxAveTracker strikeTrack = new MinMaxAveTracker();
		MinMaxAveTracker dipTrack = new MinMaxAveTracker();
		for (SimulatorElement e : elements) {
			FocalMechanism mech = e.getFocalMechanism();
			strikeTrack.addValue(mech.getStrike());
			dipTrack.addValue(mech.getDip());
			if (Math.random() < 0.001)
				System.out.println(e.getID()+". "+e.getSectionName()+" Mech: s="+mech.getStrike()
					+"\td="+mech.getDip()+"\tr="+mech.getRake());
		}
		System.out.println("Strikes: "+strikeTrack);
		System.out.println("Dips: "+dipTrack);
		System.exit(0);
		
//		File eventsDir = new File("/home/kevin/Simulators/UCERF3_qlistbig2");
//		File eventsDir = new File("/home/kevin/Simulators/UCERF3_35kyrs");
		File eventsDir = dir;
		
//		boolean bigEndian = isBigEndian(new File(eventsDir, "UCERF3base_80yrs.pList"), elements);
//		listFileDebug(new File(eventsDir, "UCERF3base_80yrs.eList"), 100, bigEndian, true);
//		boolean bigEndian = isBigEndian(new File(eventsDir, "UCERF3_35kyrs.pList"), elements);
//		listFileDebug(new File(eventsDir, "UCERF3_35kyrs.eList"), 100, bigEndian, true);
		
//		List<RSQSimEvent> events = readEventsFile(eventsDir, elements, Lists.newArrayList(new MagRangeRuptureIdentifier(7d, 10d)));
		List<RSQSimEvent> events = readEventsFile(eventsDir, elements);
		System.out.println("Loaded "+events.size()+" events");
		System.out.println("Duration: "+SimulatorUtils.getSimulationDurationYears(events)+" years");
		for (double minMag : new double[] { 6d, 6.5d, 7d }) {
			List<RSQSimEvent> subEvents = new MagRangeRuptureIdentifier(minMag, 10d).getMatches(events);
			System.out.println(subEvents.size()+" events M>="+minMag);
			MinMaxAveTracker ptsTrack = new MinMaxAveTracker();
			for (RSQSimEvent e : subEvents)
				ptsTrack.addValue(e.getNumElements());
			System.out.println("Points: "+ptsTrack);
		}
		
//		SectionIDIden safIden = SectionIDIden.getUCERF3_SAF(FaultModels.FM3_1,
//				RSQSimUtils.getUCERF3SubSectsForComparison(FaultModels.FM3_1, DeformationModels.GEOLOGIC), elements);
//		RuptureIdentifier ssafIden = new LogicalAndRupIden(safIden, new RegionIden(new CaliforniaRegions.RELM_SOCAL()));
//		List<RSQSimEvent> safEvents = ssafIden.getMatches(events);
//		
//		double delta = 5d;
//		for (int i=0; i<safEvents.size()-3; i++) {
//			RSQSimEvent e1 = safEvents.get(i);
//			RSQSimEvent e2 = safEvents.get(i+1);
//			RSQSimEvent e3 = safEvents.get(i+2);
//			double t1 = e1.getTimeInYears();
//			double t2 = e2.getTimeInYears();
//			double t3 = e3.getTimeInYears();
//			double myDelta = t3 - t1;
//			if (myDelta < delta) {
//				System.out.println("****************");
//				System.out.println(e1.getID()+" M"+(float)e1.getMagnitude());
//				System.out.println(e2.getID()+" M"+(float)e1.getMagnitude()+" "+(float)(t2-t1)+" yrs later");
//				System.out.println(e3.getID()+" M"+(float)e3.getMagnitude()+" "+(float)(t3-t2)+" yrs later");
//			}
//		}
		
//		while (true) {
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		
//		FaultModels fm = FaultModels.FM3_1;
//		SectionIDIden garlockIden = SectionIDIden.getUCERF3_Garlock(
//				fm, RSQSimUtils.getUCERF3SubSectsForComparison(fm, fm.getFilterBasis()), elements);
//		SectionIDIden safIden = SectionIDIden.getUCERF3_SAF(
//				fm, RSQSimUtils.getUCERF3SubSectsForComparison(fm, fm.getFilterBasis()), elements);
//		LogicalAndRupIden safSoCalIden = new LogicalAndRupIden(safIden, new RegionIden(new CaliforniaRegions.RELM_SOCAL()));
//		System.out.println("Num M>=7 events on both Garlock AND S.SAF: "+
//				new LogicalAndRupIden(safSoCalIden, garlockIden).getMatches(events).size());
//		for (double fract : new double[] {0d, 0.25, 0.5, 0.75, 1d}) {
//			garlockIden.setMomentFractForInclusion(fract);
//			System.out.println("Garlock M>=7 events with "+(float)(fract*100d)+" % moment: "+garlockIden.getMatches(events).size());
//			safIden.setMomentFractForInclusion(fract);
//			System.out.println("S.SAF M>=7 events with "+(float)(fract*100d)+" % moment: "+safSoCalIden.getMatches(events).size());
//		}
//		double duration = events.get(events.size()-1).getTimeInYears() - events.get(0).getTimeInYears();
//		System.out.println("Duration: "+duration+" years");
//		System.out.println("\t"+events.get(0).getTimeInYears()+" to "+events.get(events.size()-1).getTimeInYears()+" years");
//		MinMaxAveTracker magTrack = new MinMaxAveTracker();
//		for (EQSIM_Event event : events)
//			magTrack.addValue(event.getMagnitude());
//		System.out.println("Mags: "+magTrack);
//		
//		RegionIden soCalIden = new RegionIden(new CaliforniaRegions.RELM_SOCAL());
//		soCalIden.getMatches(events);
	}
	
	public static List<RSQSimEvent> readEventsFile(File file, List<SimulatorElement> elements) throws IOException {
		return readEventsFile(file, elements, null);
	}
	
	public static List<RSQSimEvent> readEventsFile(File file, List<SimulatorElement> elements,
			Collection<? extends RuptureIdentifier> rupIdens) throws IOException {
		return readEventsFile(file, elements, rupIdens, false);
	}
	
	public static List<RSQSimEvent> readEventsFile(File file, List<SimulatorElement> elements,
			Collection<? extends RuptureIdentifier> rupIdens, boolean skipSlipsAndTimes) throws IOException {
		// detect file names
		if (file.isDirectory()) {
			// find the first .*List file and use that as the basis
			for (File sub : file.listFiles()) {
				if (sub.getName().endsWith(".eList")) {
					System.out.println("Found eList file in directory: "+sub.getAbsolutePath());
					return readEventsFile(sub, elements, rupIdens, skipSlipsAndTimes);
				}
			}
			throw new FileNotFoundException("Couldn't find eList file in given directory");
		}
		String name = file.getName();
		Preconditions.checkArgument(name.endsWith("List"),
				"Must supply either directory containing all list files, or one of the files themselves");
		File dir = file.getParentFile();
		String prefix = name.substring(0, name.lastIndexOf("."));
		System.out.println("Detected prefix: "+prefix);
		File eListFile = new File(dir, prefix+".eList");
		Preconditions.checkState(eListFile.exists(),
				"Couldn't find eList file with prefix %s: %s", prefix, eListFile.getAbsolutePath());
		File pListFile = new File(dir, prefix+".pList");
		Preconditions.checkState(pListFile.exists(),
				"Couldn't find eList file with prefix %s: %s", prefix, pListFile.getAbsolutePath());
		File dListFile = new File(dir, prefix+".dList");
		Preconditions.checkState(dListFile.exists(),
				"Couldn't find dList file with prefix %s: %s", prefix, dListFile.getAbsolutePath());
		File tListFile = new File(dir, prefix+".tList");
		Preconditions.checkState(tListFile.exists(),
				"Couldn't find tList file with prefix %s: %s", prefix, tListFile.getAbsolutePath());
		return readEventsFile(new FileInputStream(eListFile), new FileInputStream(pListFile), new FileInputStream(dListFile),
				new FileInputStream(tListFile), elements, rupIdens, isBigEndian(pListFile, elements), skipSlipsAndTimes);
	}
	
	public static List<RSQSimEvent> readEventsFile(File eListFile, File pListFile, File dListFile, File tListFile,
			List<SimulatorElement> elements) throws IOException {
		return readEventsFile(eListFile, pListFile, dListFile, tListFile, elements, null, isBigEndian(pListFile, elements));
	}
	
	public static List<RSQSimEvent> readEventsFile(File eListFile, File pListFile, File dListFile, File tListFile,
			List<SimulatorElement> elements, Collection<? extends RuptureIdentifier> rupIdens) throws IOException {
		return readEventsFile(eListFile, pListFile, dListFile, tListFile, elements, rupIdens, isBigEndian(pListFile, elements));
	}
	
	public static List<RSQSimEvent> readEventsFile(File eListFile, File pListFile, File dListFile, File tListFile,
			List<SimulatorElement> elements, Collection<? extends RuptureIdentifier> rupIdens, boolean bigEndian) throws IOException {
		return readEventsFile(new FileInputStream(eListFile), new FileInputStream(pListFile), new FileInputStream(dListFile),
				new FileInputStream(tListFile), elements, rupIdens, bigEndian, false);
	}
	
	/**
	 * Detects big endianness by checking patch IDs in the given patch ID file
	 * @param pListFile
	 * @param elements
	 * @return
	 * @throws IOException
	 */
	public static boolean isBigEndian(File pListFile, List<SimulatorElement> elements) throws IOException {
		if (pListFile.isDirectory()) {
			for (File file : pListFile.listFiles()) {
				if (file.getName().endsWith(".pList")) {
					pListFile = file;
					break;
				}
			}
		}
		RandomAccessFile raFile = new RandomAccessFile(pListFile, "r");
		// 4 byte ints
		long len = raFile.length();
		int numVals;
		if (len > Integer.MAX_VALUE)
			numVals = Integer.MAX_VALUE/4;
		else
			numVals = (int)(len/4l);
		
		int numToCheck = 100;
		
		// start out assuming both true, will quickly find out which one is really true
		boolean bigEndian = true;
		boolean littleEndian = true;
		
		byte[] recordBuffer = new byte[4];
		IntBuffer littleRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		IntBuffer bigRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		
		Random r = new Random();
		for (int i=0; i<numToCheck; i++) {
			long pos = r.nextInt(numVals)*4l;
			
			raFile.seek(pos);
			raFile.read(recordBuffer);
			
			int littleEndianID = littleRecord.get(0);
			int bigEndianID = bigRecord.get(0);
			
			bigEndian = bigEndian && isValidPatchID(bigEndianID, elements);
			littleEndian = littleEndian && isValidPatchID(littleEndianID, elements);
		}
		
		raFile.close();
		
		Preconditions.checkState(bigEndian || littleEndian, "Couldn't detect endianness - bad patch IDs?");
		Preconditions.checkState(!bigEndian || !littleEndian, "Passed both big and little endian tests???");
		return bigEndian;
	}
	
	public static boolean isValidPatchID(int patchID, List<SimulatorElement> elements) {
		int minID = elements.get(0).getID();
		int maxID = elements.get(elements.size()-1).getID();
		return patchID >= minID && patchID <= maxID;
	}
	
	/**
	 * Read RSQSim *List binary files
	 * 
	 * @param eListStream
	 * @param pListStream
	 * @param dListStream
	 * @param tListStream
	 * @param elements
	 * @param bigEndian
	 * @return
	 * @throws IOException
	 */
	private static List<RSQSimEvent> readEventsFile(
			InputStream eListStream, InputStream pListStream, InputStream dListStream, InputStream tListStream,
			List<SimulatorElement> elements, Collection<? extends RuptureIdentifier> rupIdens, boolean bigEndian,
			boolean skipSlipsAndTimes) throws IOException {
		List<RSQSimEvent> events = Lists.newArrayList();
		
		populateEvents(eListStream, pListStream, dListStream, tListStream, elements, rupIdens, bigEndian, events, skipSlipsAndTimes);
		
		return events;
	}
	
	private static void populateEvents(
			InputStream eListStream, InputStream pListStream, InputStream dListStream, InputStream tListStream,
			List<SimulatorElement> elements, Collection<? extends RuptureIdentifier> rupIdens, boolean bigEndian,
			Collection<RSQSimEvent> events, boolean skipSlipsAndTimes) throws IOException {
		if (!(eListStream instanceof BufferedInputStream))
			eListStream = new BufferedInputStream(eListStream);
		if (!(pListStream instanceof BufferedInputStream))
			pListStream = new BufferedInputStream(pListStream);
		if (!(dListStream instanceof BufferedInputStream))
			dListStream = new BufferedInputStream(dListStream);
		if (!(tListStream instanceof BufferedInputStream))
			tListStream = new BufferedInputStream(tListStream);
		
		if (skipSlipsAndTimes)
			System.out.println("Skipping individual patch slips and times to conserve memory");
		
		DataInput eIn, pIn, dIn, tIn;
		if (bigEndian) {
			eIn = new DataInputStream(eListStream);
			pIn = new DataInputStream(pListStream);
			dIn = new DataInputStream(dListStream);
			tIn = new DataInputStream(tListStream);
		} else {
			eIn = new LittleEndianDataInputStream(eListStream);
			pIn = new LittleEndianDataInputStream(pListStream);
			dIn = new LittleEndianDataInputStream(dListStream);
			tIn = new LittleEndianDataInputStream(tListStream);
		}
		
		// one EventRecord for each section, or one in total if elements don't have section information
		int curEventID = -1;
		Map<Integer, RSQSimEventRecord> curRecordMap = Maps.newHashMap();
		
		Map<Integer, RSQSimEventRecord> patchToPrevRecordMap = Maps.newHashMap();
		
		HashSet<Integer> eventIDsLoaded = new HashSet<Integer>();
		
		int eventID, patchID;
		double slip, time, elementMoment;
		
		int numNegSlips = 0;
		
		RSQSimEvent prevEvent = null;
		
		while (true) {
			try {
				eventID = eIn.readInt(); // 1-based, keep as is for now as it shouldn't matter
				patchID = pIn.readInt(); // 1-based, which matches readGeometry
				slip = dIn.readDouble(); // in meters
				time = tIn.readDouble(); // in seconds
				if (prevEvent != null && time < prevEvent.getNextEventTime())
					prevEvent.setNextEventTime(time);
				
				if (slip < 0) {
					if (numNegSlips == 0)
						System.err.println("WARNING: Negative slip present in dList file.");
					if (numNegSlips < 10)
						System.err.println("\teventID="+eventID+"\tpatchID="+patchID+"\tslip="+slip);
					else if (numNegSlips == 10)
						System.err.println("Future negetive slip warning supressed");
					numNegSlips++;
				}
				
//				if (eventID % 10000 == 0 && curEventID != eventID)
//					System.out.println("Loading eventID="+eventID+". So far kept "+events.size()+" events");
				
				Preconditions.checkState(isValidPatchID(patchID, elements));
				
				SimulatorElement element = elements.get(patchID-1);
				Preconditions.checkState(element.getID() == patchID, "Elements not sequential");
				elementMoment = FaultMomentCalc.getMoment(element.getArea(), slip);
				
				if (eventID != curEventID) {
//					System.out.println("Next event: "+eventID+", closing old: "+curEventID);
					// we have a new event
					if (prevEvent != null && events instanceof BlockingDeque) {
						try {
							// put the PREVIOUS event to be sure that the next event time has
							// been filled in
							// block until space is available
							((BlockingDeque<RSQSimEvent>)events).putLast(prevEvent);
						} catch (InterruptedException e) {
							ExceptionUtils.throwAsRuntimeException(e);
						}
					}
					
					if (!curRecordMap.isEmpty()) {
						Preconditions.checkState(!eventIDsLoaded.contains(curEventID),
								"Duplicate eventID found, file is out of order or corrupt. Trying to process "
								+ "new event with ID %s, but that ID has already been processed. Next eventID=%s",
								curEventID, eventID);
						eventIDsLoaded.add(curEventID);
						RSQSimEvent event = buildEvent(curEventID, curRecordMap, rupIdens);
						if (event != null) {
							event.setNextEventTime(time);
							// can be null if filters were supplied
							if (!(events instanceof BlockingDeque)) {
								events.add(event);
							}
							prevEvent = event;
						} else {
							prevEvent = null;
							// it wasn't at match, see if we can bail (e.g. if we're only loading the first x years)
							// it is important to do this check AFTER a failure to ensure that next event time is 
							// loaded into the previous event and accurate
							boolean possible = false;
							for (RuptureIdentifier iden : rupIdens) {
								if (iden.furtherMatchesPossible()) {
									possible = true;
									break;
								}
							}
							if (!possible) {
								curRecordMap.clear();
								break;
							}
						}
					}
					curRecordMap.clear();
//					System.out.println("Processed event "+curEventID);
					curEventID = eventID;
				}
				
//				RSQSimEventRecord prevEventRecord = patchToPrevRecordMap.get(patchID);
//				if (prevEventRecord != null && !skipSlipsAndTimes)
//					// tell the previous event that this is the next time this patch ruptured
//					// used for mapping transition information to specific events
//					prevEventRecord.setNextSlipTime(patchID, time);
				
				// EventRecord for this individual fault section in this event
				RSQSimEventRecord event = curRecordMap.get(element.getSectionID());
				if (event == null) {
					event = new RSQSimEventRecord(elements);
					curRecordMap.put(element.getSectionID(), event);
					event.setTime(time);
					event.setMoment(0);
					event.setSectionID(element.getSectionID());
					event.setFirstPatchToSlip(patchID);
				}
				if (skipSlipsAndTimes) {
					if (slip > 0)
						event.addElement(patchID);
				} else {
					event.addSlip(patchID, slip, time);
					if (time < event.getTime()) {
						event.setFirstPatchToSlip(patchID);
						event.setTime(time);
					}
				}
				event.setMoment(event.getMoment()+elementMoment);
				patchToPrevRecordMap.put(patchID, event);
			} catch (EOFException e) {
				break;
			}
		}
		if (numNegSlips > 0)
			System.err.println("WARNING: found "+numNegSlips+" total negative slips!");
		if (prevEvent != null && events instanceof BlockingDeque) {
			// need to add the last event as well
			// block until space is available
			try {
				((BlockingDeque<RSQSimEvent>)events).putLast(prevEvent);
			} catch (InterruptedException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		if (!curRecordMap.isEmpty()) {
			Preconditions.checkState(!eventIDsLoaded.contains(curEventID),
					"Duplicate eventID found, file is out of order or corrupt: %s", curEventID);
			eventIDsLoaded.add(curEventID);
			RSQSimEvent event = buildEvent(curEventID, curRecordMap, rupIdens);
			if (event != null) {
				event.setNextEventTime(Double.POSITIVE_INFINITY);
				// can be null if filters were supplied
				if (events instanceof BlockingDeque) {
					// block until space is available
					try {
						((BlockingDeque<RSQSimEvent>)events).putLast(event);
					} catch (InterruptedException e) {
						ExceptionUtils.throwAsRuntimeException(e);
					}
				} else {
					events.add(event);
				}
			}
		}
		((FilterInputStream)eIn).close();
		((FilterInputStream)pIn).close();
		((FilterInputStream)dIn).close();
		((FilterInputStream)tIn).close();
		
		if (events instanceof List)
			Collections.sort((List<RSQSimEvent>)events);
	}
	
	private static void listFileDebug(File file, int numToPrint, boolean bigEndian, boolean integer) throws IOException {
		InputStream fin = new BufferedInputStream(new FileInputStream(file));
		
		System.out.println(file.getName()+" big endian? "+bigEndian);
		
		DataInput dataIn;
		
		if (bigEndian) {
			dataIn = new DataInputStream(fin);
		} else {
			dataIn = new LittleEndianDataInputStream(fin);
		}
		
		int count = 0;
		while (true) {
			try {
				if (count == numToPrint)
					break;
				Object val;
				if (integer)
					val = dataIn.readInt();
				else
					val = dataIn.readDouble();
				System.out.println(count+":\t"+val);
				count++;
			} catch (EOFException e) {
				break;
			}
		}
		((FilterInputStream)dataIn).close();
	}
	
	private static EventRecordTimeComparator recordTimeComp = new EventRecordTimeComparator();
	
	private static RSQSimEvent buildEvent(int eventID, Map<Integer, RSQSimEventRecord> records,
			Collection<? extends RuptureIdentifier> rupIdens) {
		List<RSQSimEventRecord> recordsForEvent = Lists.newArrayList(records.values());
		
		// sort records by time, earliest first
		Collections.sort(recordsForEvent, recordTimeComp);
		
		// calculate magnitude
		double totMoment = 0; // in N-m
		for (EventRecord rec : recordsForEvent)
			totMoment += rec.getMoment();
		double mag = MagUtils.momentToMag(totMoment);
		
		// set global properties in each record
		for (RSQSimEventRecord rec : recordsForEvent) {
			rec.setMagnitude(mag);
			rec.setTime(recordsForEvent.get(0).getTime()); // global according to class docs
			rec.setID(eventID);
			double recordArea = 0;
			for (SimulatorElement elem : rec.getElements())
				recordArea += elem.getArea();
			rec.setArea(recordArea);
			// TODO duration?
		}
		
		RSQSimEvent event = new RSQSimEvent(recordsForEvent);
		
		if (rupIdens != null) {
			boolean keep = false;
			for (RuptureIdentifier iden : rupIdens) {
				if (iden.isMatch(event)) {
					keep = true;
					break;
				}
			}
			if (!keep)
				return null;
		}
		return event;
	}
	
	private static class EventRecordTimeComparator implements Comparator<RSQSimEventRecord> {

		@Override
		public int compare(RSQSimEventRecord o1, RSQSimEventRecord o2) {
			return Double.compare(o1.getTime(), o2.getTime());
		}
		
	}
	
	public static Iterable<RSQSimEvent> getEventsIterable(File file, List<SimulatorElement> elements) throws IOException {
		return getEventsIterable(file, elements, null);
	}
	
	public static Iterable<RSQSimEvent> getEventsIterable(File file, List<SimulatorElement> elements,
			Collection<? extends RuptureIdentifier> rupIdens) throws IOException {
		return getEventsIterable(file, elements, rupIdens, false);
	}
	
	public static Iterable<RSQSimEvent> getEventsIterable(File file, List<SimulatorElement> elements,
			Collection<? extends RuptureIdentifier> rupIdens, boolean skipSlipsAndTimes) throws IOException {
		// detect file names
		if (file.isDirectory()) {
			// find the first .*List file and use that as the basis
			for (File sub : file.listFiles()) {
				if (sub.getName().endsWith(".eList")) {
					System.out.println("Found eList file in directory: "+sub.getAbsolutePath());
					return getEventsIterable(sub, elements, rupIdens, skipSlipsAndTimes);
				}
			}
			throw new FileNotFoundException("Couldn't find eList file in given directory");
		}
		String name = file.getName();
		Preconditions.checkArgument(name.endsWith("List"),
				"Must supply either directory containing all list files, or one of the files themselves");
		File dir = file.getParentFile();
		String prefix = name.substring(0, name.lastIndexOf("."));
		System.out.println("Detected prefix: "+prefix);
		File eListFile = new File(dir, prefix+".eList");
		Preconditions.checkState(eListFile.exists(),
				"Couldn't find eList file with prefix %s: %s", prefix, eListFile.getAbsolutePath());
		File pListFile = new File(dir, prefix+".pList");
		Preconditions.checkState(pListFile.exists(),
				"Couldn't find eList file with prefix %s: %s", prefix, pListFile.getAbsolutePath());
		File dListFile = new File(dir, prefix+".dList");
		Preconditions.checkState(dListFile.exists(),
				"Couldn't find dList file with prefix %s: %s", prefix, dListFile.getAbsolutePath());
		File tListFile = new File(dir, prefix+".tList");
		Preconditions.checkState(tListFile.exists(),
				"Couldn't find tList file with prefix %s: %s", prefix, tListFile.getAbsolutePath());
		
		return new RSQSimEventsIterable(new FileInputStream(eListFile), new FileInputStream(pListFile), new FileInputStream(dListFile),
				new FileInputStream(tListFile), elements, rupIdens, isBigEndian(pListFile, elements), skipSlipsAndTimes);
	}
	
	// max number of events to load into memory when iterating over events file
	private static final int ITERABLE_PRELOAD_CAPACITY = 10000;
	
	private static class RSQSimEventsIterable implements Iterable<RSQSimEvent> {
		
		private InputStream eListStream;
		private InputStream pListStream;
		private InputStream dListStream;
		private InputStream tListStream;
		private List<SimulatorElement> elements;
		private Collection<? extends RuptureIdentifier> rupIdens;
		private boolean bigEndian;
		private boolean skipSlipsAndTimes;

		private RSQSimEventsIterable(InputStream eListStream, InputStream pListStream, InputStream dListStream, InputStream tListStream,
				List<SimulatorElement> elements, Collection<? extends RuptureIdentifier> rupIdens, boolean bigEndian, boolean skipSlipsAndTimes) {
			this.eListStream = eListStream;
			this.pListStream = pListStream;
			this.dListStream = dListStream;
			this.tListStream = tListStream;
			this.elements = elements;
			this.rupIdens = rupIdens;
			this.bigEndian = bigEndian;
			this.skipSlipsAndTimes = skipSlipsAndTimes;
		}

		@Override
		public Iterator<RSQSimEvent> iterator() {
			final LinkedBlockingDeque<RSQSimEvent> deque = new LinkedBlockingDeque<RSQSimEvent>(ITERABLE_PRELOAD_CAPACITY);
			Thread loadThread = new Thread() {
				@Override
				public void run() {
					try {
						populateEvents(eListStream, pListStream, dListStream, tListStream, elements, rupIdens, bigEndian, deque, skipSlipsAndTimes);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			loadThread.start();
			return new RSQSimEventsIterator(deque, loadThread);
		}
		
	}
	
	private static class RSQSimEventsIterator implements Iterator<RSQSimEvent> {
		
		private BlockingDeque<RSQSimEvent> deque;
		private Thread loadThread;
		
		private RSQSimEventsIterator(BlockingDeque<RSQSimEvent> deque, Thread loadThread) {
			this.deque = deque;
			this.loadThread = loadThread;
		}

		@Override
		public boolean hasNext() {
//			System.out.println("Iterator.hasNext(), waiting");
			waitUntilReady();
//			System.out.println("Iterator.hasNext(), done waiting. Size: "+deque.size());
			return !deque.isEmpty();
		}

		@Override
		public RSQSimEvent next() {
//			System.out.println("Iterator.next(), waiting");
			waitUntilReady();
//			System.out.println("Iterator.next(), done waiting. Size: "+deque.size());
			Preconditions.checkState(deque.size() <= ITERABLE_PRELOAD_CAPACITY);
			return deque.removeFirst();
		}
		
		/**
		 * Blocks until either a new event is available or the loading thread has completed (all events populated)
		 */
		private void waitUntilReady() {
			while (deque.isEmpty() && loadThread.isAlive()) {
//				try {
//					Thread.sleep(100);
//				} catch (InterruptedException e) {
//					ExceptionUtils.throwAsRuntimeException(e);
//				}
			}
		}
		
	}
	
	public static File findByExt(File dir, String ext) throws FileNotFoundException {
		for (File file : dir.listFiles())
			if (file.getName().endsWith(ext))
				return file;
		throw new FileNotFoundException("No files ending in '"+ext+"' found in "+dir.getAbsolutePath());
	}
	
	public static int getNumEvents(File eListFile) throws IOException {
		if (eListFile.isDirectory())
			eListFile = findByExt(eListFile, ".eList");
		RandomAccessFile raFile = new RandomAccessFile(eListFile, "r");
		
		byte[] recordBuffer = new byte[4];
		IntBuffer littleRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		IntBuffer bigRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
		
		// 4 byte ints
		long len = raFile.length();
		long numVals = len/recordBuffer.length;
		
		// start out assuming both true, will quickly find out which one is really true
		boolean bigEndian = true;
		boolean littleEndian = true;
		
		// detect endianness
		long pos = 0;
		int prevLittle = -1;
		int prevBig = -1;
		int checkLoops = 0;
		while (bigEndian && littleEndian) {
			raFile.seek(pos);
			raFile.read(recordBuffer);
			
			int littleID = littleRecord.get(0);
			int bigID = bigRecord.get(0);
			
			if (prevLittle != -1 && prevBig != -1) {
				if (littleID < prevLittle)
					littleEndian = false;
				if (bigID < prevBig)
					bigEndian = false;
			}
			prevLittle = littleID;
			prevBig = bigID;
			pos += recordBuffer.length;
			checkLoops++;
		}
		
		Preconditions.checkState(bigEndian || littleEndian);
//		System.out.println("Little? "+littleEndian+". Big? "+bigEndian+" (took "+checkLoops+" reads)");
		
		raFile.seek(0);
		raFile.read(recordBuffer);
		int firstID;
		if (littleEndian)
			firstID = littleRecord.get(0);
		else
			firstID = bigRecord.get(0);
		raFile.seek((numVals-1)*recordBuffer.length);
		raFile.read(recordBuffer);
		int lastID;
		if (littleEndian)
			lastID = littleRecord.get(0);
		else
			lastID = bigRecord.get(0);
		
		raFile.close();
		return lastID - firstID;
	}
	
	public static double getDurationYears(File tListFile) throws IOException {
		if (tListFile.isDirectory())
			tListFile = findByExt(tListFile, ".tList");
		RandomAccessFile raFile = new RandomAccessFile(tListFile, "r");
		
		byte[] recordBuffer = new byte[8];
		DoubleBuffer littleRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
		DoubleBuffer bigRecord = ByteBuffer.wrap(recordBuffer).order(ByteOrder.BIG_ENDIAN).asDoubleBuffer();
		
		// 8 byte doubles
		long len = raFile.length();
		long numVals = len/recordBuffer.length;
		
		// start out assuming both true, will quickly find out which one is really true
		boolean bigEndian = true;
		boolean littleEndian = true;
		
		// detect endianness
		long pos = 0;
		double prevLittle = Double.NaN;
		double prevBig = Double.NaN;
		int checkLoops = 0;
		while (bigEndian && littleEndian) {
			raFile.seek(pos);
			raFile.read(recordBuffer);
			
			double littleTime = littleRecord.get(0);
			double bigTime = bigRecord.get(0);
			
//			System.out.println("pos="+pos+"\tlittleTime="+(float)littleTime+"\tbigTime="+(float)bigTime);
			
			if (!Double.isNaN(prevLittle) && !Double.isNaN(prevBig)) {
				if (!validTimes(prevLittle, littleTime))
//				if ((float)littleTime < (float)prevLittle)
					littleEndian = false;
//				if ((float)bigTime < (float)prevBig)
				if (!validTimes(prevBig, bigTime))
					bigEndian = false;
			}
			prevLittle = littleTime;
			prevBig = bigTime;
			pos += recordBuffer.length;
			checkLoops++;
		}
		
		Preconditions.checkState(bigEndian || littleEndian);
//		System.out.println("Little? "+littleEndian+". Big? "+bigEndian+" (took "+checkLoops+" reads)");
		
		raFile.seek(0);
		raFile.read(recordBuffer);
		double firstTime;
		if (littleEndian)
			firstTime = littleRecord.get(0);
		else
			firstTime = bigRecord.get(0);
		raFile.seek((numVals-1)*recordBuffer.length);
		raFile.read(recordBuffer);
		double lastTime;
		if (littleEndian)
			lastTime = littleRecord.get(0);
		else
			lastTime = bigRecord.get(0);
		
		raFile.close();
		return (lastTime - firstTime)/SimulatorUtils.SECONDS_PER_YEAR;
	}
	
	private static final boolean validTimes(double prevTime, double time) {
		if ((float)time >= (float)prevTime)
			return true;
		// less than, but could be close
		if (prevTime - time < 60)
			// it's within a minute, probably not an endianness issue
			return true;
		return false;
	}
	
	public static File getParamFile(File catalogDir) throws IOException {
		File bruceInFile = new File(catalogDir, "multiparam.in");
		if (bruceInFile.exists())
			return bruceInFile;
		for (File file : catalogDir.listFiles()) {
			String name = file.getName();
			if (!name.endsWith(".in"))
				continue;
			if (isParamFile(file))
				return file;
		}
		return null;
	}
	
	private static boolean isParamFile(File file) throws IOException {
		int max = 1000;
		int count = 0;
		for (String line : Files.readLines(file, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("A_1"))
				return true;
			if (count++ > max)
				return false;
		}
		return false;
	}
	
	public static Map<String, String> readParams(File paramFile) throws IOException {
		System.out.println("Loading params from "+paramFile.getAbsolutePath());
		Map<String, String> params = new HashMap<>();
		for (String line : Files.readLines(paramFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.contains("=")) {
				int ind = line.indexOf("=");
				String key = line.substring(0, ind).trim();
				String val = line.substring(ind+1).trim();
				params.put(key, val);
			}
		}
		return params;
	}

}
