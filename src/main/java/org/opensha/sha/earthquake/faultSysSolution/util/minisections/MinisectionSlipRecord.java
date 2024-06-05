package org.opensha.sha.earthquake.faultSysSolution.util.minisections;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

import com.google.common.base.Preconditions;

public class MinisectionSlipRecord extends AbstractMinisectionDataRecord {
	public final double rake;
	public final double slipRate; // mm/yr
	public final double slipRateStdDev; // mm/yr
	
	public MinisectionSlipRecord(int parentID, int minisectionID, Location startLoc, Location endLoc, double rake,
			double slipRate, double slipRateStdDev) {
		super(parentID, minisectionID, startLoc, endLoc);
		this.rake = rake;
		this.slipRate = slipRate;
		this.slipRateStdDev = slipRateStdDev;
	}
	
	public static Map<Integer, List<MinisectionSlipRecord>> readMinisectionsFile(File file) throws IOException {
		FileReader reader = new FileReader(file);
		Map<Integer, List<MinisectionSlipRecord>> ret = readMinisectionsFile(reader);
		reader.close();
		return ret;
	}
	
	public static Map<Integer, List<MinisectionSlipRecord>> readMinisectionsFile(InputStream is) throws IOException {
		InputStreamReader reader = new InputStreamReader(is);
		Map<Integer, List<MinisectionSlipRecord>> ret = readMinisectionsFile(reader);
		reader.close();
		return ret;
	}
	
	public static Map<Integer, List<MinisectionSlipRecord>> readMinisectionsFile(Reader reader) throws IOException {
		BufferedReader bRead = reader instanceof BufferedReader ? (BufferedReader)reader : new BufferedReader(reader);
		Map<Integer, List<MinisectionSlipRecord>> ret = new HashMap<>();
		
		String line = null;
		while ((line = bRead.readLine()) != null) {
			line = line.trim();
			if (line.isBlank() || line.startsWith("#"))
				continue;
			line = line.replaceAll("\t", " ");
			while (line.contains("  "))
				line = line.replaceAll("  ", " ");
			String[] split = line.split(" ");
			Preconditions.checkState(split.length == 9 || split.length == 8, "Expected 8/9 columns, have %s. Line: %s", split.length, line);
			
			int index = 0;
			int parentID = Integer.parseInt(split[index++]);
			Preconditions.checkState(parentID >= 0, "Bad parentID=%s. Line: %s", parentID, line);
			int minisectionID = Integer.parseInt(split[index++]);
			Preconditions.checkState(minisectionID >= 1, "Bad minisectionID=%s. Line: %s", minisectionID, line);
			double startLat = Double.parseDouble(split[index++]);
			double startLon = Double.parseDouble(split[index++]);
			Location startLoc = new Location(startLat, startLon);
			double endLat = Double.parseDouble(split[index++]);
			double endLon = Double.parseDouble(split[index++]);
			Location endLoc = new Location(endLat, endLon);
			double rake = Double.parseDouble(split[index++]);
			Preconditions.checkState(Double.isFinite(rake) && (float)rake >= -180f && (float)rake <= 180f, 
					"Bad rake=%s. Line: %s", rake, line);
			double slipRate = Double.parseDouble(split[index++]);
			Preconditions.checkState(slipRate >= 0d && Double.isFinite(slipRate),
					"Bad slipRate=%s. Line: %s", slipRate, line);
			double slipRateStdDev;
			if (split.length > index) {
				slipRateStdDev = Double.parseDouble(split[index++]);
				Preconditions.checkState(slipRateStdDev >= 0d && Double.isFinite(slipRateStdDev),
						"Bad slipRateStdDev=%s. Line: %s", slipRateStdDev, line);
			} else {
				slipRateStdDev = Double.NaN;
			}
			
			List<MinisectionSlipRecord> parentRecs = ret.get(parentID);
			if (parentRecs == null) {
				parentRecs = new ArrayList<>();
				ret.put(parentID, parentRecs);
				Preconditions.checkState(minisectionID == 1,
						"First minisection encounterd for fault %s, but minisection ID is %s",
						parentID, minisectionID);
			} else {
				MinisectionSlipRecord prev = parentRecs.get(parentRecs.size()-1);
				Preconditions.checkState(minisectionID == prev.minisectionID+2, // +2 here as prev is 0-based
						"Minisections are out of order for fault %s, %s is directly after %s",
						parentID, minisectionID, prev.minisectionID);
				Preconditions.checkState(startLoc.equals(prev.endLoc) || LocationUtils.areSimilar(startLoc, prev.endLoc),
						"Previons endLoc does not match startLoc for %s %s:\n\t%s\n\t%s",
						parentID, minisectionID, prev.endLoc, startLoc);
			}
			
			// convert minisections to 0-based
			parentRecs.add(new MinisectionSlipRecord(
					parentID, minisectionID-1, startLoc, endLoc, rake, slipRate, slipRateStdDev));
		}
		
		return ret;
	}
	
	public static void writeMinisectionsFile(File file, Map<Integer, List<MinisectionSlipRecord>> recsMap) throws IOException {
		FileWriter fw = new FileWriter(file);
		
		fw.write("#FaultID\tMinisectionID\tStartLat\tStartLon\tEndLat\tEndLon\tRake\tSlipRate(mm/y)\tStdDev\n");
		
		List<Integer> parentsSorted = new ArrayList<>(recsMap.keySet());
		Collections.sort(parentsSorted);
		for (int parentID : parentsSorted) {
			List<MinisectionSlipRecord> recs = recsMap.get(parentID);
			Preconditions.checkNotNull(recs);
			
			for (MinisectionSlipRecord rec : recs) {
				Preconditions.checkNotNull(rec);
				Preconditions.checkState(rec.parentID == parentID);
				String line = rec.parentID+"\t"+(rec.minisectionID+1); // +1 here, file is 1-based
				line += "\t"+(float)rec.startLoc.lat+"\t"+(float)rec.startLoc.lon;
				line += "\t"+(float)rec.endLoc.lat+"\t"+(float)rec.endLoc.lon;
				line += "\t"+(float)rec.rake+"\t"+rec.slipRate+"\t"+rec.slipRateStdDev;
				fw.write(line+"\n");
			}
		}
		
		fw.close();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(rake, slipRate, slipRateStdDev);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		MinisectionSlipRecord other = (MinisectionSlipRecord) obj;
		return Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
				&& Double.doubleToLongBits(slipRate) == Double.doubleToLongBits(other.slipRate)
				&& Double.doubleToLongBits(slipRateStdDev) == Double.doubleToLongBits(other.slipRateStdDev);
	}
}