package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ConnectivityClusters implements SubModule<FaultSystemRupSet>,
JSON_TypeAdapterBackedModule<List<ConnectivityCluster>>, Iterable<ConnectivityCluster>,
BranchAverageableModule<ConnectivityClusters>, AverageableModule.ConstantAverageable<ConnectivityClusters> {
	
	private FaultSystemRupSet rupSet;
	private List<ConnectivityCluster> clusters;
	
	public static ConnectivityClusters build(FaultSystemRupSet rupSet) {
		return new ConnectivityClusters(rupSet, ConnectivityCluster.build(rupSet));
	}

	public ConnectivityClusters(FaultSystemRupSet rupSet, List<ConnectivityCluster> clusters) {
		this.rupSet = rupSet;
		this.clusters = clusters;
	}
	
	@SuppressWarnings("unused") // used in deserialization
	private ConnectivityClusters() {};

	@Override
	public String getName() {
		return "Connectivity Clusters";
	}

	@Override
	public List<ConnectivityCluster> get() {
		return clusters;
	}

	@Override
	public void set(List<ConnectivityCluster> clusters) {
		Preconditions.checkState(this.clusters == null, "Clusters already initialized, should not call set() twice");
		this.clusters = clusters;
	}
	
	public int size() {
		return clusters.size();
	}
	
	public ConnectivityCluster get(int index) {
		return clusters.get(index);
	}
	
	public List<ConnectivityCluster> getSorted(Comparator<ConnectivityCluster> comp) {
		List<ConnectivityCluster> sorted = new ArrayList<>(clusters);
		Collections.sort(sorted, comp);
		return sorted;
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(parent));
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(newParent));
		return new ConnectivityClusters(newParent, clusters);
	}

	@Override
	public String getFileName() {
		return "connectivity_clusters.json";
	}

	@Override
	public Type getType() {
		return TypeToken.getParameterized(List.class, ConnectivityCluster.class).getType();
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {
		// do nothing, default serialization will work
	}

	@Override
	public Iterator<ConnectivityCluster> iterator() {
		return clusters.iterator();
	}
	
	public static final String CLUSTER_MISFITS_FILE_NAME = "connectivity_cluster_misfits.csv";
	public static final String LARGEST_CLUSTER_MISFIT_PROGRESS_FILE_NAME = "connectivity_cluster_largest_misfit_progress.csv";
	
	public static class ConnectivityClusterSolutionMisfits implements SubModule<FaultSystemSolution>, ArchivableModule,
	BranchAverageableModule<ConnectivityClusterSolutionMisfits>{

		private FaultSystemSolution sol;
		private ConnectivityClusters clusters;
		private List<InversionMisfitStats> clusterStats;
		private InversionMisfitProgress largestClusterProgress;

		public ConnectivityClusterSolutionMisfits(FaultSystemSolution sol,
				Map<ConnectivityCluster, InversionMisfitStats> clusterMisfits,
				InversionMisfitProgress largestClusterProgress) {
			setParent(sol);
			this.clusters = sol.getRupSet().requireModule(ConnectivityClusters.class);
			this.clusterStats = new ArrayList<>();
			for (ConnectivityCluster cluster : clusters)
				clusterStats.add(clusterMisfits.get(cluster));
			this.largestClusterProgress = largestClusterProgress;
		}
		
		public InversionMisfitStats getMisfitStats(int index) {
			return clusterStats.get(index);
		}
		
		public InversionMisfitProgress getLargestClusterMisfitProgress() {
			return largestClusterProgress;
		}
		
		@SuppressWarnings("unused") // used in deserialization
		private ConnectivityClusterSolutionMisfits() {}

		@Override
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			CSVFile<String> progressCSV = null;
			
			for (int i=0; i<clusters.size(); i++) {
				ConnectivityCluster cluster = clusters.get(i);
				InversionMisfitStats stats = clusterStats.get(i);
				
				if (stats != null) {
					CSVFile<?> clusterCSV = stats.getCSV();
					Preconditions.checkState(clusterCSV.getNumRows() > 0);
					if (progressCSV == null) {
						progressCSV = new CSVFile<>(true);
						List<String> header = new ArrayList<>();
						header.add("Cluster Index");
						header.add("# Sections");
						header.add("# Ruptures");
						for (Object val : clusterCSV.getLine(0))
							header.add(val.toString());
						progressCSV.addLine(header);
					}
					for (int row=1; row<clusterCSV.getNumRows(); row++) {
						List<String> line = new ArrayList<>(progressCSV.getNumCols());
						line.add(i+"");
						line.add(cluster.getNumSections()+"");
						line.add(cluster.getNumRuptures()+"");
						for (Object val : clusterCSV.getLine(row))
							line.add(val.toString());
						progressCSV.addLine(line);
					}
				}
			}
			
			if (progressCSV != null)
				CSV_BackedModule.writeToArchive(progressCSV, output, entryPrefix, CLUSTER_MISFITS_FILE_NAME);
			
			if (largestClusterProgress != null)
				CSV_BackedModule.writeToArchive(largestClusterProgress.getCSV(), output, entryPrefix,
						LARGEST_CLUSTER_MISFIT_PROGRESS_FILE_NAME);
		}

		@Override
		public String getName() {
			return "Connectivity Cluster Inversion Misfits";
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
			this.clusterStats = null;
			
			if (FileBackedModule.hasEntry(input, entryPrefix, CLUSTER_MISFITS_FILE_NAME)) {
				CSVFile<String> allStatsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, CLUSTER_MISFITS_FILE_NAME);
				Preconditions.checkState(allStatsCSV.getNumRows() > 0);
				
				List<CSVFile<String>> clusterCSVs = new ArrayList<>();
				List<String> origHeader = allStatsCSV.getLine(0);
				List<String> commonHeader = origHeader.subList(3, origHeader.size());
				
				if (clusters != null)
					for (int i=0; i<clusters.size(); i++)
						clusterCSVs.add(null);
				
				for (int row=1; row<allStatsCSV.getNumRows(); row++) {
					int clusterIndex = allStatsCSV.getInt(row, 0);
					
					while (clusterCSVs.size() <= clusterIndex)
						clusterCSVs.add(null);
					CSVFile<String> clusterCSV = clusterCSVs.get(clusterIndex);
					if (clusterCSV == null) {
						clusterCSV = new CSVFile<>(true);
						clusterCSV.addLine(commonHeader);
						clusterCSVs.set(clusterIndex, clusterCSV);
					}
					
					List<String> line = allStatsCSV.getLine(row);
					clusterCSV.addLine(line.subList(3, line.size()));
				}
				
				Preconditions.checkState(clusterCSVs.size() > 0);
				this.clusterStats = new ArrayList<>();
				for (CSVFile<String> clusterCSV : clusterCSVs) {
					if (clusterCSV == null)
						clusterStats.add(null);
					else
						clusterStats.add(InversionMisfitStats.fromCSV(clusterCSV));
				}
			}
			
			this.largestClusterProgress = null;
			if (FileBackedModule.hasEntry(input, entryPrefix, LARGEST_CLUSTER_MISFIT_PROGRESS_FILE_NAME)) {
				CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix,
						LARGEST_CLUSTER_MISFIT_PROGRESS_FILE_NAME);
				largestClusterProgress = new InversionMisfitProgress(progressCSV);
			}
		}

		@Override
		public void setParent(FaultSystemSolution sol) throws IllegalStateException {
			Preconditions.checkNotNull(sol, "Must supply solution");
			if (this.sol == sol)
				// can skip checks
				return;
			Preconditions.checkState(sol.getRupSet().hasModule(ConnectivityClusters.class),
					"Rupture set must have connectivity clusters attached");
			ConnectivityClusters newClusters = sol.getRupSet().requireModule(ConnectivityClusters.class);
			if (this.clusters != null) {
				Preconditions.checkState(clustersEquivalent(this.clusters, newClusters),
						"Can't set parent solution, clusters incompatible");
			} else if (clusterStats != null) {
				// we already have cluster stats, they could be from a file though and be missing nulls at the end
				Preconditions.checkState(newClusters.size() >= clusterStats.size());
				while (clusterStats.size() < newClusters.size())
					clusterStats.add(null);
			}
			this.clusters = newClusters;
			this.sol = sol;
		}

		@Override
		public FaultSystemSolution getParent() {
			return sol;
		}

		@Override
		public SubModule<FaultSystemSolution> copy(FaultSystemSolution newParent) throws IllegalStateException {
			ConnectivityClusterSolutionMisfits ret = new ConnectivityClusterSolutionMisfits();
			
			ret.clusters = clusters;
			ret.clusterStats = clusterStats;
			ret.setParent(newParent);
			
			return ret;
		}

		@Override
		public AveragingAccumulator<ConnectivityClusterSolutionMisfits> averagingAccumulator() {
			return new AveragingAccumulator<>() {
				
				private List<AveragingAccumulator<InversionMisfitStats>> statsAccumulators;
				private AveragingAccumulator<InversionMisfitProgress> progressAccumulator;

				@Override
				public Class<ConnectivityClusterSolutionMisfits> getType() {
					return ConnectivityClusterSolutionMisfits.class;
				}

				@Override
				public void process(ConnectivityClusterSolutionMisfits module, double relWeight) {
					if (statsAccumulators == null) {
						statsAccumulators =  new ArrayList<>();
						for (int i=0; i<clusters.size(); i++)
							statsAccumulators.add(null);
						if (module.largestClusterProgress != null)
							progressAccumulator = module.largestClusterProgress.averagingAccumulator();
					}
					
					Preconditions.checkState(clustersEquivalent(clusters, module.clusters));
					
					for (int i=0; i<clusters.size(); i++) {
						InversionMisfitStats stats = module.clusterStats.get(i);
						if (stats != null) {
							AveragingAccumulator<InversionMisfitStats> statsAccumulator = statsAccumulators.get(i);
							if (statsAccumulator == null) {
								statsAccumulator = stats.averagingAccumulator();
								statsAccumulators.set(i, statsAccumulator);
							}
							statsAccumulator.process(stats, relWeight);
						}
					}
					
					if (progressAccumulator != null && module.largestClusterProgress != null)
						// try averaging progress
						progressAccumulator.process(module.largestClusterProgress, relWeight);
				}

				@Override
				public ConnectivityClusterSolutionMisfits getAverage() {
					List<InversionMisfitStats> avgStats = new ArrayList<>();
					boolean hasAny = false;
					for (int i=0; i<clusters.size(); i++) {
						AveragingAccumulator<InversionMisfitStats> statsAccumulator = statsAccumulators.get(i);
						if (statsAccumulator == null) {
							avgStats.add(null);
						} else {
							avgStats.add(statsAccumulator.getAverage());
							hasAny = true;
						}
					}
					
					ConnectivityClusterSolutionMisfits ret = new ConnectivityClusterSolutionMisfits();
					ret.clusters = clusters;
					ret.clusterStats = avgStats;
					
					Preconditions.checkState(hasAny);
					
					if (progressAccumulator != null) {
						try {
							ret.largestClusterProgress = progressAccumulator.getAverage();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					return ret;
				}
			};
		}
		
	}

	@Override
	public Class<ConnectivityClusters> getAveragingType() {
		return ConnectivityClusters.class;
	}

	@Override
	public boolean isIdentical(ConnectivityClusters module) {
		return clustersEquivalent(this, module);
	}
	
	private static boolean clustersEquivalent(ConnectivityClusters clusters1, ConnectivityClusters clusters2) {
		if (clusters1 == clusters2)
			return true;
		if (clusters1 == null || clusters2 == null || !clusters1.rupSet.areSectionsEquivalentTo(clusters2.rupSet))
			return false;
		if (clusters1.size() != clusters2.size())
			return false;
		for (int i=0; i<clusters1.size(); i++) {
			ConnectivityCluster cluster1 = clusters1.get(i);
			ConnectivityCluster cluster2 = clusters2.get(i);
			if (cluster1.getNumRuptures() != cluster2.getNumRuptures())
				return false;
			if (cluster1.getNumSections() != cluster2.getNumSections())
				return false;
		}
		return true;
	}

}
