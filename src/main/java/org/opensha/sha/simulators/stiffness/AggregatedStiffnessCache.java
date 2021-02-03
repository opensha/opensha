package org.opensha.sha.simulators.stiffness;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.ReceiverDistribution;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.StiffnessAggregation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;

public class AggregatedStiffnessCache {
	
	public Map<AggregationMethod, ReceiverDistribution[][][]> patchAggregatedCache;
	public Map<AggregationMethod, StiffnessAggregation[][]> patchSectAggregatedCache;
	public StiffnessAggregation[][] fullDistCache;
	
	private SubSectStiffnessCalculator calc;
	private List<? extends FaultSection> sects;
	private StiffnessType type;
	
	AggregatedStiffnessCache(SubSectStiffnessCalculator calc, StiffnessType type) {
		this.calc = calc;
		this.type = type;
		this.sects = calc.getSubSects();
		this.patchAggregatedCache = new HashMap<>();
		this.patchSectAggregatedCache = new HashMap<>();
		Preconditions.checkArgument(sects != null && !sects.isEmpty());
		for (int i=0; i<sects.size(); i++)
			Preconditions.checkState(sects.get(i).getSectionId() == i, "section IDs must be 0-based indexes");
	}
	
	public synchronized void clear() {
		fullDistCache = null;
		patchSectAggregatedCache.clear();
		patchAggregatedCache.clear();
	}
	
	public ReceiverDistribution[] getPatchAggregated(AggregationMethod patchAggMethod, FaultSection source, FaultSection receiver) {
		ReceiverDistribution[][][] cache = patchAggregatedCache.get(patchAggMethod);
		if (cache == null || cache[receiver.getSectionId()] == null)
			return null;
		return cache[receiver.getSectionId()][source.getSectionId()];
	}
	
	public synchronized void putPatchAggregated(AggregationMethod patchAggMethod, FaultSection source,
			FaultSection receiver, ReceiverDistribution[] aggregated) {
		ReceiverDistribution[][][] cache = patchAggregatedCache.get(patchAggMethod);
		if (cache == null) {
			cache = new ReceiverDistribution[sects.size()][sects.size()][];
			patchAggregatedCache.put(patchAggMethod, cache);
		}
		if (cache[receiver.getSectionId()] == null)
			cache[receiver.getSectionId()] = new ReceiverDistribution[sects.size()][];
		cache[receiver.getSectionId()][source.getSectionId()] = aggregated;
	}
	
	public StiffnessAggregation getSectAggregated(AggregationMethod patchAggMethod, FaultSection source, FaultSection receiver) {
		StiffnessAggregation[][] cache;
		if (patchAggMethod == null)
			cache = fullDistCache;
		else
			cache = patchSectAggregatedCache.get(patchAggMethod);
		if (cache == null || cache[receiver.getSectionId()] == null)
			return null;
		return cache[receiver.getSectionId()][source.getSectionId()];
	}
	
	public synchronized void putSectAggregated(AggregationMethod patchAggMethod, FaultSection source,
			FaultSection receiver, StiffnessAggregation aggregated) {
		putSectAggregated(patchAggMethod, source.getSectionId(), receiver.getSectionId(), aggregated);
	}
	
	private void putSectAggregated(AggregationMethod patchAggMethod, int sourceID, int receiverID, StiffnessAggregation aggregated) {
		StiffnessAggregation[][] cache;
		if (patchAggMethod == null) {
			if (fullDistCache == null)
				fullDistCache = new StiffnessAggregation[sects.size()][];
			cache = fullDistCache;
		} else {
			if (patchSectAggregatedCache == null)
				patchSectAggregatedCache = new HashMap<>();
			cache = patchSectAggregatedCache.get(patchAggMethod);
			if (cache == null) {
				cache = new StiffnessAggregation[sects.size()][];
				patchSectAggregatedCache.put(patchAggMethod, cache);
			}
		}
		if (cache[receiverID] == null)
			cache[receiverID] = new StiffnessAggregation[sects.size()];
		cache[receiverID][sourceID] = aggregated;
	}
	
	public int calcCacheSize() {
		int size = calcCacheSize(fullDistCache);
		for (StiffnessAggregation[][] cache : patchSectAggregatedCache.values())
			size += calcCacheSize(cache);
		return size;
	}
	
	private int calcCacheSize(StiffnessAggregation[][] cache) {
		if (cache == null)
			return 0;
		int cached = 0;
		for (int i=0; i<cache.length; i++) {
			if (cache[i] == null)
				continue;
			for (int j=0; j<cache.length; j++) {
				if (cache[i][j] != null)
					cached++;
			}
		}
		return cached;
	}
	
	public String getCacheFileName() {
		DecimalFormat df = new DecimalFormat("0.##");
		String ret = type.name().toLowerCase()+"_cache_"+sects.size()+"sects_"+df.format(calc.getGridSpacing())
			+"km_lambda"+df.format(calc.getLameLambda())+"_mu"+df.format(calc.getLameMu())+"_coeff"+(float)calc.getCoeffOfFriction()
			+"_align"+calc.getPatchAlignment().name();
		if (calc.getSelfStiffnessCap() > 0)
			ret += "_stiffCap"+df.format(calc.getSelfStiffnessCap())+"x";
		return ret+".csv";
	}
	
	public void writeCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> line = new ArrayList<>();
		line.add("Source ID");
		line.add("Receiver ID");
		line.add("Receiver Patch Aggregation");
		for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS)
			line.add(method.name());
		csv.addLine(line);
		writeCacheLines(csv, null, fullDistCache);
		for (AggregationMethod patchMethod : patchSectAggregatedCache.keySet())
			writeCacheLines(csv, patchMethod, patchSectAggregatedCache.get(patchMethod));
		csv.writeToFile(cacheFile);
	}
	
	private void writeCacheLines(CSVFile<String> csv, AggregationMethod patchMethod, StiffnessAggregation[][] cache) {
		if (cache == null)
			return;
		for (int i=0; i<cache.length; i++) {
			if (cache[i] == null)
				continue;
			for (int j=0; j<cache[i].length; j++) {
				if (cache[i][j] != null) {
					StiffnessAggregation agg = cache[i][j];
					List<String> line = new ArrayList<>();
					line.add(j+""); // source ID
					line.add(i+""); // receiver ID
					line.add(patchMethod == null ? "" : patchMethod.name());
					for (AggregationMethod method : AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS)
						line.add(agg.get(method)+"");
					csv.addLine(line);
				}
			}
		}
	}
	
	public int loadCacheFile(File cacheFile) throws IOException {
		System.out.println("Loading "+type+" cache from "+cacheFile.getAbsolutePath()+"...");
//		CSVFile<String> csv = CSVFile.readFile(cacheFile, true);
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(cacheFile), 1024*64);
		CSVFile<String> csv = CSVFile.readStream(bis, true);
		List<String> header = csv.getLine(0);
		if (header.size() < 4) {
			System.err.println("Warning: stiffness cache file is invalid, skipping loading");
			return 0;
		}
		AggregationMethod[] methods = new AggregationMethod[header.size()-3];
		if (methods.length != AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS.size()) {
			System.err.println("Warning: aggregation methods have changed and cache is now invalid, skipping loading");
			return 0;
		}
		HashSet<AggregationMethod> prevMethods = new HashSet<>();
		for (int i=0; i<methods.length; i++) {
			int col = i+3;
			String name = header.get(col);
			try {
				methods[i] = AggregationMethod.valueOf(name);
			} catch (IllegalArgumentException e) {
				System.err.println("Warning: aggregation methods have changed (can't find '"+name+"') and cache is now invalid, skipping loading");
				return 0;
			}
			if (!AggregatedStiffnessCalculator.CACHEABLE_AGG_METHODS.contains(methods[i])) {
				System.err.println("Warning: aggregation methods have changed and cache is now invalid, skipping loading");
				return 0;
			}
			if (prevMethods.contains(methods[i])) {
				System.err.println("Warning: cache contains duplicate columns and is invalid, skipping loading");
				return 0;
			}
			prevMethods.add(methods[i]);
		}
		for (int row=1; row<csv.getNumRows(); row++) {
			int col = 0;
			int sourceID = csv.getInt(row, col++);
			int receiverID = csv.getInt(row, col++);
			AggregationMethod patchAggMethod;
			String aggStr = csv.get(row, col++);
			if (aggStr.isEmpty())
				patchAggMethod = null;
			else
				patchAggMethod = AggregationMethod.valueOf(aggStr);
			double[] aggValues = new double[methods.length];
			for (int i=0; i<methods.length; i++)
				aggValues[i] = csv.getDouble(row, col++);
			StiffnessAggregation aggregation = new StiffnessAggregation(methods, aggValues);
			putSectAggregated(patchAggMethod, sourceID, receiverID, aggregation);
		}
		System.out.println("Loaded "+(csv.getNumRows()-1)+" values");
		return csv.getNumRows()-1;
	}
	
	public void copyCacheFrom(AggregatedStiffnessCache o) {
		Preconditions.checkState(type == o.type);
		Preconditions.checkState(sects.size() == o.sects.size());
		
		copyCacheFrom(null, o.fullDistCache);
		for (AggregationMethod aggMethod : patchSectAggregatedCache.keySet())
			copyCacheFrom(aggMethod, o.patchSectAggregatedCache.get(aggMethod));
	}
	
	private void copyCacheFrom(AggregationMethod patchAggMethod, StiffnessAggregation[][] cache) {
		if (cache == null)
			return;
		for (int r=0; r<cache.length; r++) {
			if (cache[r] == null)
				continue;
			for (int s=0; s<cache[r].length; s++)
				if (cache[r][s] != null)
					putSectAggregated(patchAggMethod, s, r, cache[r][s]);
		}
	}

}
