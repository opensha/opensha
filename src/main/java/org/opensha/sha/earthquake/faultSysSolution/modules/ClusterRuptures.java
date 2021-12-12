package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class ClusterRuptures implements SubModule<FaultSystemRupSet>, Iterable<ClusterRupture>,
BranchAverageableModule<ClusterRuptures>, AverageableModule.ConstantAverageable<ClusterRuptures> {
	
	protected FaultSystemRupSet rupSet;

	public ClusterRuptures(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

	public abstract List<ClusterRupture> getAll();
	
	public ClusterRupture get(int rupIndex) {
		return getAll().get(rupIndex);
	}

	@Override
	public Iterator<ClusterRupture> iterator() {
		return getAll().iterator();
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}
	
	public int size() {
		return getAll().size();
	}
	
	/**
	 * Builds cluster ruptures for this RuptureSet. If the plausibility configuration has been set
	 * and no splays are allowed, then they will be built assuming an ordered single strand rupture.
	 * Otherwise, the given RuptureConnectionSearch will be used to construct ClusterRupture representations
	 * 
	 * @param rupSet
	 * @param search
	 * @return
	 */
	public static ClusterRuptures instance(FaultSystemRupSet rupSet, RuptureConnectionSearch search) {
		PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
		System.out.println("Building ClusterRuptures for "+rupSet.getNumRuptures()+" ruptures");
		if (config != null && config.getMaxNumSplays() == 0) {
			// if splays aren't allowed and we have a plausibility configuration, then simple strand ruptures
			System.out.println("Assuming simple single strand ruptures");
			return new SingleStranded(rupSet, null);
		}
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		List<Future<ClusterRupture>> futures = new ArrayList<>();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			futures.add(exec.submit(new ClusterRupCalc(search, r)));
		
		List<ClusterRupture> ruptures = new ArrayList<>();
		
		for (int r=0; r<futures.size(); r++) {
			if (r % 1000 == 0)
				System.out.println("Calculating for rupture "+r+"/"+rupSet.getNumRuptures());
			Future<ClusterRupture> future = futures.get(r);
			try {
				ruptures.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				exec.shutdown();
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		System.out.println("Built "+ruptures.size()+" ruptures");
		
		exec.shutdown();
		return new Precomputed(rupSet, ruptures);
	}

	private static class ClusterRupCalc implements Callable<ClusterRupture> {
		
		private RuptureConnectionSearch search;
		private int rupIndex;

		public ClusterRupCalc(RuptureConnectionSearch search, int rupIndex) {
			this.search = search;
			this.rupIndex = rupIndex;
		}

		@Override
		public ClusterRupture call() throws Exception {
			ClusterRupture rupture = search.buildClusterRupture(rupIndex, true, false);
			
			int numSplays = rupture.getTotalNumSplays();
			if (numSplays > 0) {
				// see if there is an alternative route through this rupture with fewer splays
				double mainStrandLen = 0d;
				for (FaultSubsectionCluster cluster : rupture.clusters)
					for (FaultSection sect : cluster.subSects)
						mainStrandLen += sect.getTraceLength();
				for (ClusterRupture alternative : rupture.getPreferredAltRepresentations(search)) {
					int altNumSplays = alternative.getTotalNumSplays();
					double altStrandLen = 0d;
					for (FaultSubsectionCluster cluster : alternative.clusters)
						for (FaultSection sect : cluster.subSects)
							altStrandLen += sect.getTraceLength();
					if (altNumSplays < numSplays ||
							(altNumSplays == numSplays && altStrandLen > mainStrandLen)) {
						// switch to this representation if it has fewer splays, or the same number
						// of splays but a longer primary strand
						rupture = alternative;
						numSplays = altNumSplays;
						break;
					}
				}
			}
			
			return rupture;
		}
		
	}
	
	public static ClusterRuptures instance(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
		return instance(rupSet, clusterRuptures, false);
	}
	
	public static ClusterRuptures instance(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures,
			boolean forceSerialization) {
		if (!forceSerialization) {
			boolean singleStrand = true;
			for (ClusterRupture rup : clusterRuptures) {
				if (!rup.singleStrand) {
					singleStrand = false;
					break;
				}
			}
			if (singleStrand)
				return new SingleStranded(rupSet, clusterRuptures);
		}
		return new Precomputed(rupSet, clusterRuptures);
	}
	
	public static ClusterRuptures singleStranged(FaultSystemRupSet rupSet) {
		return new SingleStranded(rupSet, null);
	}

	private static class SingleStranded extends ClusterRuptures implements ArchivableModule {
		
		private List<ClusterRupture> clusterRuptures;
		
		@SuppressWarnings("unused")
		public SingleStranded() {
			this(null, null);
		}

		public SingleStranded(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
			super(rupSet);
			if (clusterRuptures != null)
				Preconditions.checkNotNull(rupSet);
			this.clusterRuptures = clusterRuptures;
		}

		@Override
		public String getName() {
			return "Single-Strand Cluster Ruptures";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public List<ClusterRupture> getAll() {
			Preconditions.checkNotNull(rupSet, "Not initialized with a rupture set");
			if (clusterRuptures == null) {
				synchronized (this) {
					if (clusterRuptures == null) {
						// build them
						SectionDistanceAzimuthCalculator distAzCalc = null;
						if (rupSet.hasModule(PlausibilityConfiguration.class))
							distAzCalc = rupSet.getModule(PlausibilityConfiguration.class).getDistAzCalc();
						else if (rupSet.hasModule(SectionDistanceAzimuthCalculator.class))
							distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
						if (distAzCalc == null)
							distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
						List<ClusterRupture> rups = new ArrayList<>(rupSet.getNumRuptures());
						for (int r=0; r<rupSet.getNumRuptures(); r++)
							rups.add(ClusterRupture.forOrderedSingleStrandRupture(rupSet.getFaultSectionDataForRupture(r), distAzCalc));
						this.clusterRuptures = rups;
					}
				}
			}
			return clusterRuptures;
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) {
			if (clusterRuptures != null) {
				// see if we can keep them
				if (newParent.isEquivalentTo(rupSet))
					return new SingleStranded(newParent, clusterRuptures);
			}
			return new SingleStranded(newParent, null);
		}

		@Override
		public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
			if (this.clusterRuptures != null)
				Preconditions.checkState(rupSet.isEquivalentTo(parent));
			this.rupSet = parent;
		}
		
	}
	
	private static class Precomputed extends ClusterRuptures implements JSON_BackedModule {
		
		private List<ClusterRupture> clusterRuptures;

		@SuppressWarnings("unused")
		public Precomputed() {
			super(null);
		}

		public Precomputed(FaultSystemRupSet rupSet, List<ClusterRupture> clusterRuptures) {
			super(rupSet);
			Preconditions.checkState(clusterRuptures.size() == rupSet.getNumRuptures());
			this.clusterRuptures = clusterRuptures;
		}

		@Override
		public String getName() {
			return "Precomputed Cluster Ruptures";
		}
		
		@Override
		public String getFileName() {
			return "cluster_ruptures.json";
		}

		@Override
		public Gson buildGson() {
			return ClusterRupture.buildGson(rupSet.getFaultSectionDataList(), false);
		}

		@Override
		public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
			// TODO make JSON more compact
			Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
			gson.toJson(clusterRuptures, listType, out);
		}

		@Override
		public void initFromJSON(JsonReader in, Gson gson) throws IOException {
			Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
			List<ClusterRupture> ruptures = gson.fromJson(in, listType);
			Preconditions.checkState(ruptures.size() == rupSet.getNumRuptures());
			this.clusterRuptures = ruptures;
		}

		@Override
		public List<ClusterRupture> getAll() {
			return clusterRuptures;
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) {
			Preconditions.checkState(rupSet.isEquivalentTo(newParent));
			return new Precomputed(newParent, clusterRuptures);
		}

		@Override
		public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
			if (this.clusterRuptures != null)
				Preconditions.checkState(rupSet.isEquivalentTo(parent));
			this.rupSet = parent;
		}
	}

}
