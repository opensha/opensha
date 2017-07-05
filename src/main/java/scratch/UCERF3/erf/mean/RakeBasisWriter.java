package scratch.UCERF3.erf.mean;

import java.io.File;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opensha.commons.data.CSVFile;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.erf.mean.RuptureCombiner.IntHashSet;
import scratch.UCERF3.utils.MatrixIO;

public class RakeBasisWriter {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		DeformationModels[] dms = DeformationModels.values();
		
		File writeDir = new File("/tmp");
		
		Map<String, Integer> namesToIndexMap = null;
		Map<Set<String>, Integer> namesToRupIndexMap = null;
		
		for (DeformationModels dm : dms) {
			if (dm.getRelativeWeight(null) <= 0)
				continue;
			
			Map<Set<String>, Double> rakeBasis = RuptureCombiner.loadRakeBasis(dm);
			
			if (namesToIndexMap == null) {
				// write subsect names CSV file
				HashSet<String> subsectNames = new HashSet<String>();
				for (Set<String> subsects : rakeBasis.keySet())
					subsectNames.addAll(subsects);
				namesToIndexMap = Maps.newHashMap();
				
				CSVFile<String> subsectCSV = new CSVFile<String>(true);
				
				subsectCSV.addLine("Subsect Name", "Subsect Index");
				int subsectIndex = 0;
				for (String subsectName : subsectNames) {
					namesToIndexMap.put(subsectName, subsectIndex);
					subsectCSV.addLine(subsectName, subsectIndex+"");
					subsectIndex++;
				}
				subsectCSV.writeToFile(new File(writeDir, "subsects.csv"));
				
				// write rup index map
				namesToRupIndexMap = Maps.newHashMap();
				int ruptureIndex = 0;
				List<List<Integer>> sectsForRups = Lists.newArrayList();
				for (Set<String> subsects : rakeBasis.keySet()) {
					List<Integer> sectsForRup = Lists.newArrayList();
					
					for (String subsect : subsects)
						sectsForRup.add(namesToIndexMap.get(subsect));
					
					namesToRupIndexMap.put(subsects, ruptureIndex);
					sectsForRups.add(sectsForRup);
					ruptureIndex++;
				}
				MatrixIO.intListListToFile(sectsForRups, new File(writeDir, "rup_sects.bin"));
			}
			
			double[] rakes = new double[namesToRupIndexMap.size()];
			
			for (Set<String> subsects : rakeBasis.keySet()) {
				double rake = rakeBasis.get(subsects);
				int index = namesToRupIndexMap.get(subsects);
				
				rakes[index] = rake;
			}
			
			String fname = dm.getShortName()+"_rup_rakes.bin";
			MatrixIO.doubleArrayToFile(rakes, new File(writeDir, fname));
		}
	}
	
	/**
	 * Loads rake basis for the given DM from the given zip file
	 * @param zip
	 * @param dm
	 * @return
	 * @throws IOException
	 */
	public static Map<Set<String>, Double> loadRakeBasis(ZipFile zip, DeformationModels dm)
			throws IOException {
		ZipEntry csvEntry = zip.getEntry("subsects.csv");
		CSVFile<String> subsectsCSV = CSVFile.readStream(zip.getInputStream(csvEntry), true);
		
		Map<Integer, String> indexToNamesMap = Maps.newHashMap();
		
		for (int row=1; row<subsectsCSV.getNumRows(); row++) {
			List<String> line = subsectsCSV.getLine(row);
			Integer index = Integer.parseInt(line.get(1));
			String name = line.get(0);
			indexToNamesMap.put(index, name);
		}
		
		Map<HashSet<String>, Integer> namesToRupIndexMap = null;
		ZipEntry rupIndexEntry = zip.getEntry("rup_sects.bin");
		List<List<Integer>> rupIndexes = MatrixIO.intListListFromInputStream(zip.getInputStream(rupIndexEntry));
		
		ZipEntry rakesEntry = zip.getEntry(dm.getShortName()+"_rup_rakes.bin");
		double[] rakes = MatrixIO.doubleArrayFromInputStream(zip.getInputStream(rakesEntry), rupIndexes.size()*8);
		
		Map<Set<String>, Double> rakeBasis = Maps.newHashMap();
		for (int rupIndex=0; rupIndex<rupIndexes.size(); rupIndex++) {
			Set<String> subsectNames = new IndexedStringSet(indexToNamesMap, rupIndexes.get(rupIndex));
			rakeBasis.put(subsectNames, rakes[rupIndex]);
		}
		
		return rakeBasis;
	}
	
	/**
	 * More efficient way of storing rake basis. hashCode() returns the same as from HashSet<String>
	 * @author kevin
	 *
	 */
	private static class IndexedStringSet extends AbstractSet<String> {
		
		private Map<Integer, String> indexToNamesMap;
		private IntHashSet indexes;
		private int hashCode;
		
		public IndexedStringSet(Map<Integer, String> indexToNamesMap, List<Integer> indexes) {
			this.indexToNamesMap = indexToNamesMap;
			this.indexes = new IntHashSet(indexes);
			this.hashCode = new HashSet<String>(this).hashCode();
		}

		@Override
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				
				private Iterator<Integer> it = indexes.iterator();

				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public String next() {
					return indexToNamesMap.get(it.next());
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException("cannot remove");
				}
			};
		}

		@Override
		public int size() {
			return indexes.size();
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj.getClass() == IndexedStringSet.class) {
				IndexedStringSet o = (IndexedStringSet)obj;
				return indexes.equals(o.indexes) && indexToNamesMap == o.indexToNamesMap;
			} else {
				return super.equals(obj);
			}
		}

	}

}
