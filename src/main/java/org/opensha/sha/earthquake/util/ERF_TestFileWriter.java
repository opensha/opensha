package org.opensha.sha.earthquake.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.sha.earthquake.BaseERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public class ERF_TestFileWriter {
	
	private static class RuptureRecord implements Comparable<RuptureRecord> {
		public final int sourceID;
		public final int rupID;
		public final float mag;
		public final float prob;
		public final float rake;
		public final String surfType;
		public final float length;
		public final float width;
		public final float topDepth;
		public final float strike;
		public final float dip;
		public final Location firstSurfLoc;
		public final float rJB_100;
		public final float rRup_100;
		public final float rSeis_100;
		public final float rX_100;
		
		public static final List<String> header = List.of("Source ID", "Rupture ID", "Magnitude", "Probability", "Rake",
				"Surface Type", "Surface Length", "Surface Width", "Surface Top Depth",
				"Surface Strike", "Surface Dip",
				"First Location Lat", "First Location Lon", "First Location Depth",
				"rJB at 100km", "rRup at 100km", "rSeis at 100km", "rX at 100km");
		
		public RuptureRecord(int sourceID, int rupID, ProbEqkRupture rup) {
			this.sourceID = sourceID;
			this.rupID = rupID;
			this.mag = (float)rup.getMag();
			this.prob = (float)rup.getProbability();
			this.rake = (float)rup.getAveRake();
			
			RuptureSurface surf = rup.getRuptureSurface();
			this.surfType = ClassUtils.getClassNameWithoutPackage(surf.getClass());
			this.length = (float)surf.getAveLength();
			this.width = (float)surf.getAveWidth();
			this.topDepth = (float)surf.getAveRupTopDepth();
			double strike = surf.getAveStrike();
			this.strike = (float)strike;
			this.dip = (float)surf.getAveDip();
			this.firstSurfLoc = surf.getEvenlyDiscretizedLocation(0);
			if (!Double.isFinite(strike))
				strike = 0d;
			Location testLoc = LocationUtils.location(firstSurfLoc, Math.toRadians(strike+45), 100d);
			this.rJB_100 = (float)surf.getDistanceJB(testLoc);
			this.rRup_100 = (float)surf.getDistanceRup(testLoc);
			this.rSeis_100 = (float)surf.getDistanceSeis(testLoc);
			this.rX_100 = (float)surf.getDistanceX(testLoc);
		}
		
		public RuptureRecord(List<String> line) {
			Preconditions.checkState(line.size() == header.size());
			
			int index = 0;
			sourceID = Integer.parseInt(line.get(index++));
			rupID = Integer.parseInt(line.get(index++));
			mag = Float.parseFloat(line.get(index++));
			prob = Float.parseFloat(line.get(index++));
			rake = Float.parseFloat(line.get(index++));
			surfType = line.get(index++);
			length = Float.parseFloat(line.get(index++));
			width = Float.parseFloat(line.get(index++));
			topDepth = Float.parseFloat(line.get(index++));
			strike = Float.parseFloat(line.get(index++));
			dip = Float.parseFloat(line.get(index++));
			firstSurfLoc = new Location(Double.parseDouble(line.get(index++)),
					Double.parseDouble(line.get(index++)),
					Double.parseDouble(line.get(index++)));
			rJB_100 = Float.parseFloat(line.get(index++));
			rRup_100 = Float.parseFloat(line.get(index++));
			rSeis_100 = Float.parseFloat(line.get(index++));
			rX_100 = Float.parseFloat(line.get(index++));
			Preconditions.checkState(index == line.size());
		}
		
		public List<String> toCSVLine() {
			List<String> line = new ArrayList<>(header.size());
			
			line.add(sourceID+"");
			line.add(rupID+"");
			line.add(mag+"");
			line.add(prob+"");
			line.add(rake+"");
			line.add(surfType);
			line.add(length+"");
			line.add(width+"");
			line.add(topDepth+"");
			line.add(strike+"");
			line.add(dip+"");
			line.add((float)firstSurfLoc.lat+"");
			line.add((float)firstSurfLoc.lon+"");
			line.add((float)firstSurfLoc.depth+"");
			line.add(rJB_100+"");
			line.add(rRup_100+"");
			line.add(rSeis_100+"");
			line.add(rX_100+"");
			
			return line;
		}

		@Override
		public int compareTo(RuptureRecord o) {
			int c = Integer.compare(sourceID, o.sourceID);
			if (c != 0)
				return c;
			
			c = Float.compare(mag, o.mag);
			if (c != 0)
				return c;
			
			c = Float.compare(rake, o.rake);
			if (c != 0)
				return c;
			
			c = Float.compare(dip, o.dip);
			if (c != 0)
				return c;
			
			c = Float.compare(rX_100, o.rX_100);
			if (c != 0)
				return c;
			
			return Float.compare(prob, o.prob);
		}

		@Override
		public String toString() {
			return "mag=" + mag + ", prob=" + prob + ", rake=" + rake + ", surfType=" + surfType
					+ ", length=" + length + ", width=" + width + ", topDepth=" + topDepth + ", strike=" + strike
					+ ", dip=" + dip + ", firstSurfLat=" + (float)firstSurfLoc.lat + ", firstSurfLon=" + (float)firstSurfLoc.lon
					+ ", firstSurfDepth=" + (float)firstSurfLoc.depth + ", rJB_100=" + rJB_100 + ", rRup_100=" + rRup_100
					+ ", rSeis_100=" + rSeis_100 + ", rX_100=" + rX_100;
		}
	}
	
	private static class RuptureComparison {
		public final RuptureRecord r1;
		public final RuptureRecord r2;
		public final boolean mag;
		public final boolean prob;
		public final boolean rake;
		public final boolean surfType;
		public final boolean length;
		public final boolean width;
		public final boolean topDepth;
		public final boolean strike;
		public final boolean dip;
		public final boolean firstSurfLat;
		public final boolean firstSurfLon;
		public final boolean firstSurfDepth;
		public final boolean rJB_100;
		public final boolean rRup_100;
		public final boolean rSeis_100;
		public final boolean rX_100;
		
		public RuptureComparison(RuptureRecord r1, RuptureRecord r2) {
			this.r1 = r1;
			this.r2 = r2;
			mag = Precision.equals(r1.mag, r2.mag, 1e-3);
			prob = Precision.equals(r1.prob, r2.prob, 1e-6);
			rake = Precision.equals(r1.rake, r2.rake, 1e-3);
			surfType = r1.surfType.equals(r2.surfType);
			length = Precision.equals(r1.length, r2.length, 1e-3);
			width = Precision.equals(r1.width, r2.width, 1e-3);
			topDepth = Precision.equals(r1.topDepth, r2.topDepth, 1e-3);
			strike = Precision.equals(r1.strike, r2.strike, 1e-3);
			dip = Precision.equals(r1.dip, r2.dip, 1e-3);
			firstSurfLat = Precision.equals(r1.firstSurfLoc.lat, r2.firstSurfLoc.lat, 1e-3);
			firstSurfLon = Precision.equals(r1.firstSurfLoc.lon, r2.firstSurfLoc.lon, 1e-3);
			firstSurfDepth = Precision.equals(r1.firstSurfLoc.depth, r2.firstSurfLoc.depth, 1e-3);
			rJB_100 = distEquals(r1.rJB_100, r2.rJB_100);
			rRup_100 = distEquals(r1.rRup_100, r2.rRup_100);
			rSeis_100 = distEquals(r1.rSeis_100, r2.rSeis_100);
			rX_100 = distEquals(r1.rX_100, r2.rX_100);
		}
		
		private static boolean distEquals(double dist1, double dist2) {
			double tol = Math.max(0.1, 0.02*Math.max(dist1, dist2));
			return Precision.equals(dist2, dist2, tol);
		}
		
		public boolean fullEquals() {
			return basicEquals() && strikeDependentEquals() && surfTypeEquals();
		}
		
		public boolean basicEquals() {
			return mag && prob && rake && length && width && topDepth && dip && firstSurfDepth
					&& rJB_100 && rRup_100 && rSeis_100;
		}
		
		public boolean strikeDependentEquals() {
			return strike && rX_100 && firstSurfLat && firstSurfLon;
		}
		
		public boolean surfTypeEquals() {
			return surfType;
		}
		
		private static final String mismatchStr = "\t**** MISMATCH ****";
		
		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			String newLineTabs = "\n\t\t";
			str.append("\t\t").append("Source ID:\t").append(r1.sourceID).append("\t").append(r2.sourceID);
			if (r1.sourceID != r2.sourceID)
				str.append(mismatchStr);
			str.append(newLineTabs).append("Rupture ID:\t").append(r1.rupID).append("\t").append(r2.rupID);
			if (r1.rupID != r2.rupID)
				str.append(mismatchStr);
			str.append(newLineTabs).append(numericMismatchStr("Magnitude", r1.mag, r2.mag, mag));
			str.append(newLineTabs).append(numericMismatchStr("Probability", r1.prob, r2.prob, prob));
			str.append(newLineTabs).append(numericMismatchStr("Rake", r1.rake, r2.rake, rake));
			str.append(newLineTabs).append("Surface Type:\t").append(r1.surfType).append("\t").append(r2.surfType);
			if (!surfType)
				str.append(mismatchStr);
			str.append(newLineTabs).append(numericMismatchStr("Length", r1.length, r2.length, length));
			str.append(newLineTabs).append(numericMismatchStr("Width", r1.width, r2.width, width));
			str.append(newLineTabs).append(numericMismatchStr("Top Depth", r1.topDepth, r2.topDepth, topDepth));
			str.append(newLineTabs).append(numericMismatchStr("Strike", r1.strike, r2.strike, strike));
			str.append(newLineTabs).append(numericMismatchStr("Dip", r1.dip, r2.dip, dip));
			str.append(newLineTabs).append(numericMismatchStr("L0 Lat", (float)r1.firstSurfLoc.lat,  (float)r1.firstSurfLoc.lat, firstSurfLat));
			str.append(newLineTabs).append(numericMismatchStr("L0 Lon", (float)r1.firstSurfLoc.lon,  (float)r1.firstSurfLoc.lon, firstSurfLon));
			str.append(newLineTabs).append(numericMismatchStr("L0 Depth", (float)r1.firstSurfLoc.depth,  (float)r1.firstSurfLoc.depth, firstSurfDepth));
			str.append(newLineTabs).append(numericMismatchStr("rJB 100", r1.rJB_100, r2.rJB_100, rJB_100));
			str.append(newLineTabs).append(numericMismatchStr("rRup 100", r1.rRup_100, r2.rRup_100, rRup_100));
			str.append(newLineTabs).append(numericMismatchStr("rSeis 100", r1.rSeis_100, r2.rSeis_100, rSeis_100));
			str.append(newLineTabs).append(numericMismatchStr("rX 100", r1.rX_100, r2.rX_100, rX_100));
			return str.toString();
		}
		
		private static String numericMismatchStr(String name, float val1, float val2, boolean match) {
			StringBuilder str = new StringBuilder();
			
			str.append(name).append(":\t").append(val1).append("\t").append(val2);
			if (!match) {
				str.append(mismatchStr);
				float diff = Math.abs(val1 - val2);
				str.append("\t|diff|=").append(diff);
				str.append("\t(").append(pDF.format(diff/val1)).append(")");
			}
			
			return str.toString();
		}
	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.##%");
	
	public static class RuptureComparisonAggregator {
		private int magCount = 0;
		private int probCount = 0;
		private int rakeCount = 0;
		private int surfTypeCount = 0;
		private int lengthCount = 0;
		private int widthCount = 0;
		private int topDepthCount = 0;
		private int strikeCount = 0;
		private int dipCount = 0;
		private int firstSurfLatCount = 0;
		private int firstSurfLonCount = 0;
		private int firstSurfDepthCount = 0;
		private int rJB_100Count = 0;
		private int rRup_100Count = 0;
		private int rSeis_100Count = 0;
		private int rX_100Count = 0;

		public void count(RuptureComparison comp) {
			if (!comp.mag) magCount++;
			if (!comp.prob) probCount++;
			if (!comp.rake) rakeCount++;
			if (!comp.surfType) surfTypeCount++;
			if (!comp.length) lengthCount++;
			if (!comp.width) widthCount++;
			if (!comp.topDepth) topDepthCount++;
			if (!comp.strike) strikeCount++;
			if (!comp.dip) dipCount++;
			if (!comp.firstSurfLat) firstSurfLatCount++;
			if (!comp.firstSurfLon) firstSurfLonCount++;
			if (!comp.firstSurfDepth) firstSurfDepthCount++;
			if (!comp.rJB_100) rJB_100Count++;
			if (!comp.rRup_100) rRup_100Count++;
			if (!comp.rSeis_100) rSeis_100Count++;
			if (!comp.rX_100) rX_100Count++;
		}

		// Getters for each count
		public int getMagCount() { return magCount; }
		public int getProbCount() { return probCount; }
		public int getRakeCount() { return rakeCount; }
		public int getSurfTypeCount() { return surfTypeCount; }
		public int getLengthCount() { return lengthCount; }
		public int getWidthCount() { return widthCount; }
		public int getTopDepthCount() { return topDepthCount; }
		public int getStrikeCount() { return strikeCount; }
		public int getDipCount() { return dipCount; }
		public int getFirstSurfLatCount() { return firstSurfLatCount; }
		public int getFirstSurfLonCount() { return firstSurfLonCount; }
		public int getFirstSurfDepthCount() { return firstSurfDepthCount; }
		public int getRJB_100Count() { return rJB_100Count; }
		public int getRRup_100Count() { return rRup_100Count; }
		public int getRSeis_100Count() { return rSeis_100Count; }
		public int getRX_100Count() { return rX_100Count; }

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (magCount > 0) sb.append("\t\tmagCount: ").append(magCount).append("\n");
			if (probCount > 0) sb.append("\t\tprobCount: ").append(probCount).append("\n");
			if (rakeCount > 0) sb.append("\t\trakeCount: ").append(rakeCount).append("\n");
			if (surfTypeCount > 0) sb.append("\t\tsurfTypeCount: ").append(surfTypeCount).append("\n");
			if (lengthCount > 0) sb.append("\t\tlengthCount: ").append(lengthCount).append("\n");
			if (widthCount > 0) sb.append("\t\twidthCount: ").append(widthCount).append("\n");
			if (topDepthCount > 0) sb.append("\t\ttopDepthCount: ").append(topDepthCount).append("\n");
			if (strikeCount > 0) sb.append("\t\tstrikeCount: ").append(strikeCount).append("\n");
			if (dipCount > 0) sb.append("\t\tdipCount: ").append(dipCount).append("\n");
			if (firstSurfLatCount > 0) sb.append("\t\tfirstSurfLatCount: ").append(firstSurfLatCount).append("\n");
			if (firstSurfLonCount > 0) sb.append("\t\tfirstSurfLonCount: ").append(firstSurfLonCount).append("\n");
			if (firstSurfDepthCount > 0) sb.append("\t\tfirstSurfDepthCount: ").append(firstSurfDepthCount).append("\n");
			if (rJB_100Count > 0) sb.append("\t\trJB_100Count: ").append(rJB_100Count).append("\n");
			if (rRup_100Count > 0) sb.append("\t\trRup_100Count: ").append(rRup_100Count).append("\n");
			if (rSeis_100Count > 0) sb.append("\t\trSeis_100Count: ").append(rSeis_100Count).append("\n");
			if (rX_100Count > 0) sb.append("\t\trX_100Count: ").append(rX_100Count).append("\n");
			return sb.toString();
		}
	}
	
	private static void writeCSV(ERF erf, File outputFile) throws IOException {
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(outputFile));
		CSVWriter writer = new CSVWriter(bout, true);
		
		writer.write(RuptureRecord.header);
		
		int numSources = erf.getNumSources();
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			int numRups = source.getNumRuptures();
			for (int rupID=0; rupID<numRups; rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				
				writer.write(new RuptureRecord(sourceID, rupID, rup).toCSVLine());
			}
		}
		
		writer.flush();
		bout.close();
	}
	
	private static void compareCSVs(File refFile, File testFile, CSVFile<String> summaryCSV) throws IOException {
		CSVFile<String> refCSV = CSVFile.readFile(refFile, true);
		CSVFile<String> testCSV = CSVFile.readFile(testFile, true);
		
		List<List<RuptureRecord>> refRecs = loadRecords(refCSV);
		List<List<RuptureRecord>> testRecs = loadRecords(testCSV);
		
		int numSourcesWithOrderMismatch = 0;
		int numExtraRefSources = 0;
		int numExtraTestSources = 0;
		int numRefZeroRateRuptures = 0;
		int numTestZeroRateRuptures = 0;
		int numExtraRefRuptures = 0;
		int numExtraTestRuptures = 0;
		
		RuptureComparisonAggregator mismatchAgg = new RuptureComparisonAggregator();
		
		boolean pass = true;
		boolean passesExceptStrike = true;
		boolean passesExceptSurfClass = true;
		
		double refSumProb = 0d;
		for (List<RuptureRecord> recs : refRecs)
			for (RuptureRecord rec : recs)
				refSumProb += rec.prob;
		double testSumProb = 0d;
		for (List<RuptureRecord> recs : testRecs)
			for (RuptureRecord rec : recs)
				testSumProb += rec.prob;
		
		int maxCount = Integer.max(refRecs.size(), testRecs.size());
		for (int s=0; s<maxCount; s++) {
			if (s == refRecs.size()) {
				numExtraTestSources = testRecs.size()-refRecs.size();
				System.err.println("\tTest has "+numExtraTestSources+" extra sources; first: "+testRecs.get(s).get(0));
				System.err.flush();
				pass = false;
				break;
			} else if (s == testRecs.size()) {
				numExtraRefSources = refRecs.size()-testRecs.size();
				System.err.println("\tReference has "+numExtraRefSources+" extra sources; first: "+refRecs.get(s).get(0));
				System.err.flush();
				pass = false;
				break;
			}
			List<RuptureRecord> refSourceRecs = refRecs.get(s);
			List<RuptureRecord> testSourceRecs = testRecs.get(s);
			
			Preconditions.checkState(refSourceRecs.get(0).sourceID == testSourceRecs.get(0).sourceID);
			// sort them to get rid of any order mismatches
			Collections.sort(refSourceRecs);
			Collections.sort(testSourceRecs);
			
			boolean orderMatch = refSourceRecs.size() == testSourceRecs.size();
			
			// remove zeros
			for (int i=refSourceRecs.size(); --i>=0;) {
				if (refSourceRecs.get(i).prob == 0f) {
					refSourceRecs.remove(i);
					numRefZeroRateRuptures++;
				}
			}
			for (int i=testSourceRecs.size(); --i>=0;) {
				if (testSourceRecs.get(i).prob == 0f) {
					testSourceRecs.remove(i);
					numTestZeroRateRuptures++;
				}
			}
			
			int maxSize = Integer.max(refSourceRecs.size(), testSourceRecs.size());
			
			for (int r=0; r<maxSize; r++) {
				if (r == refSourceRecs.size()) {
					int extra = testSourceRecs.size() - refSourceRecs.size();
					System.err.println("\tTest has "+extra+" extra ruptures; first: "+testSourceRecs.get(s));
					numExtraTestRuptures += extra;
					System.err.flush();
					pass = false;
					break;
				} else if (r == testSourceRecs.size()) {
					int extra = refSourceRecs.size() - testSourceRecs.size();
					System.err.println("\tRef has "+extra+" extra ruptures; first: "+refSourceRecs.get(s));
					numExtraRefRuptures += extra;
					System.err.flush();
					pass = false;
					break;
				}
				RuptureRecord refRec = refSourceRecs.get(r);
				RuptureRecord testRec = testSourceRecs.get(r);
				Preconditions.checkState(refRec.sourceID == testRec.sourceID);
				RuptureComparison comp = new RuptureComparison(refRec, testRec);
				orderMatch &= refRec.rupID == testRec.rupID;
				if (comp.fullEquals())
					continue;
				mismatchAgg.count(comp);
				
				boolean basic = comp.basicEquals();
				boolean strike = comp.strikeDependentEquals();
				boolean type = comp.surfTypeEquals();
				
				boolean newExceptStrike = passesExceptStrike && basic && type;
				boolean newExceptType = passesExceptSurfClass && basic && strike;
				
				// we don't match
				if (pass) {
					// fist mismatch
					System.err.println("First mismatch at r="+r+":\n"+comp);
				} else {
					boolean print = false;
					if (passesExceptStrike != newExceptStrike) {
						System.err.println("First non-strike mismatch at r="+r+":");
						print = true;
					}
					if (passesExceptSurfClass != newExceptType) {
						System.err.println("First non-suface type mismatch at r="+r+":");
						print = true;
					}
					if (print)
						System.err.println("\n"+comp);
				}
				pass = false;
				passesExceptStrike = newExceptStrike;
				passesExceptSurfClass = newExceptType;
			}
			if (!orderMatch)
				numSourcesWithOrderMismatch++;
		}
		System.out.flush();
		System.err.flush();
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		
		if (pass) {
			System.out.println("\tPerfect match");
		} else {
			if (passesExceptStrike)
				System.out.println("\tMatches except for strike (and strike-derrivatives)");
			if (passesExceptSurfClass)
				System.out.println("\tMatches except for surface type");
			if (!passesExceptStrike && !passesExceptSurfClass)
				System.out.println("\tMismatch");
			if (numSourcesWithOrderMismatch > 0)
				System.out.println("\tEncoutnered "+numSourcesWithOrderMismatch+" sources with rupture order mismatches");
			if (numRefZeroRateRuptures > 0)
				System.out.println("\tRef had "+numRefZeroRateRuptures+" zero-prob ruptures");
			if (numTestZeroRateRuptures > 0)
				System.out.println("\tTest had "+numTestZeroRateRuptures+" zero-prob ruptures");
			if (numExtraRefSources > 0)
				System.out.println("\tRef had "+numExtraRefSources+" extra sources");
			if (numExtraTestSources > 0)
				System.out.println("\tTest had "+numExtraTestSources+" extra sources");
			if (numExtraRefRuptures > 0)
				System.out.println("\tRef had "+numExtraRefRuptures+" extra ruptures in matching sources");
			if (numExtraTestRuptures > 0)
				System.out.println("\tTest had "+numExtraTestRuptures+" extra ruptures in matching sources");
			
			System.out.println("\tColumn mismatch counts:\n"+mismatchAgg);
		}
		
		if (summaryCSV.getNumRows() == 0)
			summaryCSV.addLine("File Name",
					"PerfectMatch?",
					"Match Ignoring Order?",
					"Match Except Strike Changes?",
					"Match Except Surface Class?",
					"Rupture Order Changed?",
					"# Extra Reference Sources",
					"# Extra Test Sources",
					"# Extra Reference Ruptures",
					"# Extra Test Ruptures",
					"# Zero-Prob Reference Ruptures",
					"# Zero-Prob Test Ruptures");
		
		summaryCSV.addLine(refFile.getName(),
				(pass && numSourcesWithOrderMismatch == 0)+"",
				pass+"",
				passesExceptStrike+"",
				passesExceptSurfClass+"",
				(numSourcesWithOrderMismatch > 0)+"",
				numExtraRefSources+"",
				numExtraTestSources+"",
				numExtraRefRuptures+"",
				numExtraTestRuptures+"",
				numRefZeroRateRuptures+"",
				numTestZeroRateRuptures+"");
		
		System.out.println("\tRef Psum:\t"+(float)refSumProb);
		System.out.println("\tTest Psum:\t"+(float)testSumProb);
		double diff = testSumProb - refSumProb;
		System.out.println("\t\tDiff:\t"+(float)diff+" ("+pDF.format(diff/refSumProb)+")");
		
		System.out.flush();
	}
	
	private static List<List<RuptureRecord>> loadRecords(CSVFile<String> csv) {
		List<List<RuptureRecord>> sourceRecords = new ArrayList<>();
		
		List<RuptureRecord> curSourceRecs = null;
		int curSourceID = -1;
		
		for (int row=1; row<csv.getNumRows(); row++) {
			RuptureRecord rec = new RuptureRecord(csv.getLine(row));
			if (rec.sourceID != curSourceID) {
				curSourceRecs = new ArrayList<>();
				Preconditions.checkState(rec.sourceID == sourceRecords.size());
				sourceRecords.add(curSourceRecs);
				curSourceID = rec.sourceID;
			}
			curSourceRecs.add(rec);
		}
		
		return sourceRecords;
	}
	
	private static void compareDirs(File refDir, File testDir) throws IOException {
		File[] files = refDir.listFiles();
		Arrays.sort(files, new FileNameComparator());
		
		CSVFile<String> summaryCSV = new CSVFile<>(true);
		
		for (File file : files) {
			if (!file.getName().endsWith(".csv") || file.getName().startsWith("results_summary"))
				continue;
			File testFile = new File(testDir, file.getName());
			
			System.out.println("Testing "+file.getName());
			System.out.flush();
			if (!testFile.exists()) {
				System.err.println("\tDoesn't exist in "+testDir.getAbsolutePath());
				System.err.flush();
			} else {
				compareCSVs(file, testFile, summaryCSV);
			}
			
			System.out.println();
			System.out.flush();
		}
		
		summaryCSV.writeToFile(new File(testDir, "results_summary_comp_"+refDir.getName()+".csv"));
	}

	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		File outputBaseDir = new File("/home/kevin/OpenSHA/erf_test_files/2024_11_06/");
		File refDir = new File(outputBaseDir, "master");
		File testDir = new File(outputBaseDir, "point_source_refactor");
		
		boolean write = false;
		boolean compare = !write;
		String debugName = null;
		int debugSourceID = -1;
		
//		debugName = "MEAN_UCERF_2.csv";
//		debugSourceID = 1920;
		
		String branch;
		try {
			branch = ApplicationVersion.loadGitBranch();
		} catch (Exception e) {
			branch = null;
		}
		
		boolean thisIsRef = branch == null || branch.equals("master");
		System.out.println("On branch '"+branch+"'; ref ? "+thisIsRef);
		
		File outputDir = thisIsRef ? refDir : testDir;
		
		if (write) {
			for (ERF_Ref ref : ERF_Ref.values()) {
				System.out.println(ref.name()+": "+ref+" ("+ref.getERFClass()+")");
				System.out.flush();
				List<File> outputFiles = new ArrayList<>();
				try {
					BaseERF baseERF = ref.instance();
					if (baseERF instanceof ERF) {
						ERF erf = (ERF)baseERF;
						
						List<String> erfSuffixes = new ArrayList<>();
						List<ERF> erfInstances = new ArrayList<>();
						
						boolean hasBG = setBackgroudEnabled(erf, true);
						erf.updateForecast();
						
						if (hasBG) {
							erfInstances.add(erf);
							erfSuffixes.add("_bg_include");
							
							ERF erf2 = (ERF)ref.instance();
							Preconditions.checkState(setBackgroudEnabled(erf2, false));
							erf2.updateForecast();
							erfInstances.add(erf2);
							erfSuffixes.add("_bg_exclude");
						} else {
							erfInstances.add(erf);
							erfSuffixes.add("");
						}
						
						ParameterList params = erf.getAdjustableParameterList();
						
						String rupParamName = Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_NAME;
						if (params.containsParameter(rupParamName) && params.getParameter(rupParamName) instanceof StringParameter) {
							Parameter<String> param = params.getParameter(String.class, rupParamName);
							ERF erf2 = (ERF)ref.instance();
							Preconditions.checkState(setBackgroudEnabled(erf2, true));
							boolean alreadyUsed = false;
							if (param.isAllowed(Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_FINITE)
									&& !param.getValue().equals(Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_FINITE)) {
								erf2.setParameter(rupParamName,
										Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_FINITE);
								erf2.updateForecast();
								erfInstances.add(erf2);
								erfSuffixes.add("_bg_finite");
								alreadyUsed = true;
							} else if (param.isAllowed(UCERF2.BACK_SEIS_RUP_FINITE)
									&& !param.getValue().equals(UCERF2.BACK_SEIS_RUP_FINITE)) {
								erf2.setParameter(rupParamName,
										UCERF2.BACK_SEIS_RUP_FINITE);
								erf2.updateForecast();
								erfInstances.add(erf2);
								erfSuffixes.add("_bg_finite");
								alreadyUsed = true;
							} else {
								System.out.println("Didn't find finite option for "+rupParamName);
							}
							
							// might have already been finite
							if (!param.getValue().equals(Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_POINT)
									&& param.isAllowed(Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_POINT)) {
								if (alreadyUsed) {
									erf2 = (ERF)ref.instance();
									Preconditions.checkState(setBackgroudEnabled(erf2, true));
								}
								// set to point
								erf2.setParameter(rupParamName,
										Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_POINT);
								erf2.updateForecast();
								erfInstances.add(erf2);
								erfSuffixes.add("_bg_point");
							}
						}
						
						rupParamName = BackgroundRupParam.NAME;
						if (params.containsParameter(rupParamName) && params.getParameter(rupParamName) instanceof BackgroundRupParam) {
							BackgroundRupParam param = (BackgroundRupParam)params.getParameter(rupParamName);
							ERF erf2 = (ERF)ref.instance();
							Preconditions.checkState(setBackgroudEnabled(erf2, true));
							boolean alreadyUsed = false;
							BackgroundRupType defaultVal = param.getValue();
							for (BackgroundRupType type : BackgroundRupType.values()) {
								if (type != defaultVal && param.isAllowed(type)) {
									if (alreadyUsed) {
										erf2 = (ERF)ref.instance();
										Preconditions.checkState(setBackgroudEnabled(erf2, true));
									}
									erf2.setParameter(rupParamName, type);
									erf2.updateForecast();
									erfInstances.add(erf2);
									erfSuffixes.add("_"+type.name());
									alreadyUsed = true;
								}
							}
						}
						
						if (params.containsParameter(MeanUCERF2.CYBERSHAKE_DDW_CORR_PARAM_NAME)) {
							ERF erf2 = (ERF)ref.instance();
							// don't bother enabling background for CS DDW
							setBackgroudEnabled(erf2, false);
							erf2.setParameter(MeanUCERF2.CYBERSHAKE_DDW_CORR_PARAM_NAME, true);
							erf2.updateForecast();
							erfInstances.add(erf2);
							erfSuffixes.add("_cs_ddw");
						}
						
						for (int e=0; e<erfInstances.size(); e++) {
							File outputFile = new File(outputDir, ref.name()+erfSuffixes.get(e)+".csv");
							System.out.println("Writing "+outputFile.getName());
							
							ERF erfInstance = erfInstances.get(e);
							
							System.out.println("Parameters:");
							for (Parameter<?> param : erfInstance.getAdjustableParameterList())
								System.out.println("\t"+param.getName()+":\t"+param.getValue());
							
							outputFiles.add(outputFile);
							writeCSV(erfInstance, outputFile);
							System.out.println();
						}
					} else {
						System.err.println("Skipping ERF of type "+baseERF.getClass().getName());
					}
				} catch (Throwable e) {
					e.printStackTrace();
					for (File outputFile : outputFiles)
						if (outputFile.exists())
							outputFile.delete();
				}
				System.out.flush();
				System.err.flush();
				System.out.println();
				System.out.flush();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (debugName != null && !debugName.isEmpty() && debugSourceID >= 0) {
			File refFile = new File(refDir, debugName);
			File testFile = new File(testDir, debugName);
			System.out.println("Debug for "+debugName+", source "+debugSourceID);
			List<List<RuptureRecord>> refRecords = loadRecords(CSVFile.readFile(refFile, true));
			List<List<RuptureRecord>> testRecords = loadRecords(CSVFile.readFile(testFile, true));
			
			Preconditions.checkState(debugSourceID < refRecords.size());
			Preconditions.checkState(debugSourceID < testRecords.size());
			
			List<RuptureRecord> refs = refRecords.get(debugSourceID);
			List<RuptureRecord> tests = testRecords.get(debugSourceID);
			
			for (boolean sort : new boolean[] {false,true}) {
				for (boolean ref : new boolean[] {true,false}) {
					List<RuptureRecord> recs = ref ? refs : tests;
					if (ref)
						System.out.println("Reference"+(sort ? " Sorted" : ""));
					else
						System.out.println("Test"+(sort ? " Sorted" : ""));
					if (sort)
						Collections.sort(recs);
					for (RuptureRecord rec : recs)
						System.out.println(rec);
					System.out.println();
				}
			}
		}
		
		if (compare)
			compareDirs(refDir, testDir);
		
		System.out.println("\nDONE");
		
		System.exit(0);
	}

	private static boolean setBackgroudEnabled(ERF erf, boolean enabled) {
		ParameterList params = erf.getAdjustableParameterList();
		
		if (params.containsParameter(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_NAME)
				&& params.getParameter(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_NAME) instanceof StringParameter) {
			erf.setParameter(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_NAME,
					enabled ? Frankel96_AdjustableEqkRupForecast.BACK_SEIS_INCLUDE : Frankel96_AdjustableEqkRupForecast.BACK_SEIS_EXCLUDE);
			return true;
		}
		if (params.containsParameter(IncludeBackgroundParam.NAME) && params.getParameter(IncludeBackgroundParam.NAME) instanceof IncludeBackgroundParam) {
			erf.setParameter(IncludeBackgroundParam.NAME,
					enabled ? IncludeBackgroundOption.INCLUDE : IncludeBackgroundOption.EXCLUDE);
			return true;
		}
		return false;
	}

}
