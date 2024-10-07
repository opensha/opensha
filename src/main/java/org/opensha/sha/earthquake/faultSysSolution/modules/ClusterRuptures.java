package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
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
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class ClusterRuptures implements SubModule<FaultSystemRupSet>, Iterable<ClusterRupture>,
BranchAverageableModule<ClusterRuptures>, AverageableModule.ConstantAverageable<ClusterRuptures>,
SplittableRuptureModule<ClusterRuptures> {
	
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
	
	@Override
	public Class<ClusterRuptures> getAveragingType() {
		return ClusterRuptures.class;
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
		return instance(rupSet, search, true);
	}

	/**
	 * Builds cluster ruptures for this RuptureSet. If the plausibility configuration has been set
	 * and no splays are allowed, then they will be built assuming an ordered single strand rupture.
	 * Otherwise, the given RuptureConnectionSearch will be used to construct ClusterRupture representations
	 * 
	 * @param rupSet
	 * @param search
	 * @param maintainOrder
	 * @return
	 */
	public static ClusterRuptures instance(FaultSystemRupSet rupSet, RuptureConnectionSearch search, boolean maintainOrder) {
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
			futures.add(exec.submit(new ClusterRupCalc(search, r, maintainOrder)));
		
		List<ClusterRupture> ruptures = new ArrayList<>();
		
		for (int r=0; r<futures.size(); r++) {
			if (r % 1000 == 0)
				System.out.println("Calculating for rupture "+r+"/"+rupSet.getNumRuptures());
			Future<ClusterRupture> future = futures.get(r);
			try {
				ruptures.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				exec.shutdown();
				synchronized (ClusterRuptures.class) {
					System.err.println("Failed to build rupture for "+r+", trying again with debug enabled");
					System.err.flush();
					System.out.flush();
					ClusterRupCalc calc = new ClusterRupCalc(search, r, maintainOrder, true);
					Exception secondary = null;
					ClusterRupture ret = null;
					try {
						ret = calc.call();
					} catch (Exception e1) {
						secondary = e1;
					}
					if (secondary == null)
						System.err.println("Weird: didn't get an exception on retry with debug=true");
					if (ret != null)
						System.err.println("Weird: got this rupture on retry with debug=true: "+ret);
					System.err.flush();
					System.out.flush();
					
				}
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
		private boolean debug;
		private boolean maintainOrder;
		
		public ClusterRupCalc(RuptureConnectionSearch search, int rupIndex, boolean maintainOrder) {
			this(search, rupIndex, maintainOrder, false);
		}

		public ClusterRupCalc(RuptureConnectionSearch search, int rupIndex, boolean maintainOrder, boolean debug) {
			this.search = search;
			this.rupIndex = rupIndex;
			this.maintainOrder = maintainOrder;
			this.debug = debug;
		}

		@Override
		public ClusterRupture call() throws Exception {
			ClusterRupture rupture = search.buildClusterRupture(rupIndex, maintainOrder, debug);
			
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

	public static class SingleStranded extends ClusterRuptures implements ArchivableModule {
		
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
		public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
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
						// try to get plausibility config, but don't load if available but not loaded as that could
						// potentially take a long time
						PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class, false);
						if (plausibility != null)
							distAzCalc = plausibility.getDistAzCalc();
						if (rupSet.hasModule(SectionDistanceAzimuthCalculator.class)) {
							SectionDistanceAzimuthCalculator distAzCalc2 = rupSet.requireModule(SectionDistanceAzimuthCalculator.class);
							if (distAzCalc == null || distAzCalc2.getNumCachedDistances() > distAzCalc.getNumCachedDistances())
								distAzCalc = distAzCalc2;
						}
						if (distAzCalc == null) {
							distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
							rupSet.addModule(distAzCalc);
						}
						int numCached = distAzCalc.getNumCachedDistances();
						if (numCached > 0)
							System.out.println("Building single-stranded ClusterRupture representations and "
									+ "with "+numCached+" already-cached distances");
						else
							System.out.println("Building single-stranded ClusterRupture representations and "
									+ "calculating necessary distances");
						Stopwatch watch = Stopwatch.createStarted();
						int threads = FaultSysTools.defaultNumThreads();
						if (numCached == 0 && threads > 1 && !rupSet.hasModule(RuptureSubSetMappings.class)
								&& rupSet.getNumSections() > 100) {
							// first cache distances
							// find all directly connected sections
							HashSet<IDPairing> directJumps = new HashSet<>();
							List<? extends FaultSection> sects = rupSet.getFaultSectionDataList();
							Map<Integer, List<FaultSection>> parentsToSectsMap = sects.stream().collect(
									Collectors.groupingBy(S->S.getParentSectionId()));
							ExecutorService exec = Executors.newFixedThreadPool(threads);
							for (List<Integer> rup : rupSet.getSectionIndicesForAllRups()) {
								int prevParentID = -1;
								for (int sectIndex : rup) {
									int parentID = sects.get(sectIndex).getParentSectionId();
									if (prevParentID >= 0 && parentID != prevParentID) {
										IDPairing parentPair = parentID > prevParentID ?
												new IDPairing(prevParentID, parentID) : new IDPairing(parentID, prevParentID);
										if (!directJumps.contains(parentPair)) {
											directJumps.add(parentPair);
											exec.submit(new ParentSectsDistCacheRunnable(
													parentsToSectsMap.get(parentID),
													parentsToSectsMap.get(prevParentID), distAzCalc));
										}
									}
									prevParentID = parentID;
								}
							}
							
							System.out.println("Pre-caching distances for "+directJumps.size()+" parent-to-parent jumps with "+threads+" threads");
							
							// no need to wait for it to finish
							exec.shutdown();
						}
						
						List<ClusterRupture> rups = new ArrayList<>(rupSet.getNumRuptures());
						for (int r=0; r<rupSet.getNumRuptures(); r++)
							rups.add(ClusterRupture.forOrderedSingleStrandRupture(
									rupSet.getFaultSectionDataForRupture(r), distAzCalc));
						watch.stop();
						double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
						System.out.println("Took "+(float)secs+" secs to build "+rups.size()+" ClusterRupture representations");
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

		@Override
		public SingleStranded getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			// don't keep cache, new ruptures will have new IDs and FaultSection indexes
			return new SingleStranded(rupSubSet, null);
		}

		@Override
		public ClusterRuptures getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			// don't keep cache, new ruptures will have new IDs and FaultSection indexes
			return new SingleStranded(splitRupSet, null);
		}
		
	}
	
	private static class ParentSectsDistCacheRunnable implements Runnable {
		private final List<FaultSection> sects1;
		private final List<FaultSection> sects2;
		private final SectionDistanceAzimuthCalculator distAzCalc;
		
		public ParentSectsDistCacheRunnable(List<FaultSection> sects1, List<FaultSection> sects2,
				SectionDistanceAzimuthCalculator distAzCalc) {
			super();
			this.sects1 = sects1;
			this.sects2 = sects2;
			this.distAzCalc = distAzCalc;
		}

		@Override
		public void run() {
			for (FaultSection s1 : sects1)
				for (FaultSection s2 : sects2)
					distAzCalc.getDistance(s1, s2);
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

		@Override
		public AveragingAccumulator<ClusterRuptures> averagingAccumulator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ClusterRuptures getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
			throw new UnsupportedOperationException("Not yet supported");
//			List<ClusterRupture> subSet = new ArrayList<>();
//			for (int r=0; r<mappings.getNumRetainedRuptures(); r++) {
//				ClusterRupture origRup = clusterRuptures.get(mappings.getOrigRupID(r));
//				
//				
//			}
//			return subSet;
		}

		@Override
		public ClusterRuptures getForSplitRuptureSet(FaultSystemRupSet splitRupSet, RuptureSetSplitMappings mappings) {
			throw new UnsupportedOperationException("Not yet supported");
		}
	}

	@Override
	public boolean isIdentical(ClusterRuptures module) {
		return this.rupSet.isEquivalentTo(module.rupSet);
	}

}
