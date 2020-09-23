package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Named;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFetcher;

/**
 * This abstract class defines cluster connections. The getClusters() method will add all
 * connections the first time that it is called.
 * 
 * @author kevin
 *
 */
public abstract class ClusterConnectionStrategy implements Named {

	private List<? extends FaultSection> subSections;
	private List<FaultSubsectionCluster> clusters;
	
	protected transient HashSet<IDPairing> connectedParents;
	protected transient boolean connectionsAdded = false;
	
	public ClusterConnectionStrategy(List<? extends FaultSection> subSections) {
		this(subSections, buildClusters(subSections));
	}
	
	public ClusterConnectionStrategy(List<? extends FaultSection> subSections,
			List<FaultSubsectionCluster> clusters) {
		this.subSections = ImmutableList.copyOf(subSections);
		this.clusters = ImmutableList.copyOf(clusters);
	}
	
	/**
	 * Builds clusters from the given sub-sections (without adding any connections)
	 * @param subSections
	 * @return
	 */
	public static List<FaultSubsectionCluster> buildClusters(List<? extends FaultSection> subSections) {
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		
		List<FaultSection> curClusterSects = null;
		int curParentID = -1;
		
		for (FaultSection subSect : subSections) {
			int parentID = subSect.getParentSectionId();
			Preconditions.checkState(parentID >= 0,
					"Subsections are required, but this section doesn't have a parent ID set: %s. %s",
					subSect.getSectionId(), subSect.getSectionName());
			if (parentID != curParentID) {
				if (curClusterSects != null)
					clusters.add(new FaultSubsectionCluster(curClusterSects));
				curParentID = parentID;
				curClusterSects = new ArrayList<>();
			}
			curClusterSects.add(subSect);
		}
		clusters.add(new FaultSubsectionCluster(curClusterSects));
		
		return clusters;
	}
	
	/**
	 * @return list of sub sections
	 */
	public synchronized List<? extends FaultSection> getSubSections() {
		return subSections;
	}
	
	/**
	 * @return list of full clusters, after adding all connections
	 */
	public synchronized List<FaultSubsectionCluster> getClusters() {
		if (!connectionsAdded) {
			System.out.println("Building connections between "+clusters.size()+" clusters");
			int count = buildConnections();
			System.out.println("Found "+count+" possible section connections");
		}
		return clusters;
	}

	/**
	 * Populates all possible connections between the given clusters (via the
	 * FaultSubsectionCluster.addConnection(Jump) method).
	 * 
	 * If this method is overridden, you must also override the areParentSectsConnected method.
	 * 
	 * @return the number of connections added
	 */
	private int buildConnections() {
		int count = 0;
		connectedParents = new HashSet<>();
		for (int c1=0; c1<clusters.size(); c1++) {
			FaultSubsectionCluster cluster1 = clusters.get(c1);
			for (int c2=c1+1; c2<clusters.size(); c2++) {
				FaultSubsectionCluster cluster2 = clusters.get(c2);
				List<Jump> jumps = buildPossibleConnections(cluster1, cluster2);
				if (jumps != null) {
					for (Jump jump : jumps) {
						connectedParents.add(new IDPairing(cluster1.parentSectionID, cluster2.parentSectionID));
						connectedParents.add(new IDPairing(cluster2.parentSectionID, cluster1.parentSectionID));
						cluster1.addConnection(jump);
						cluster2.addConnection(jump.reverse());
						count++;
					}
				}
			}
		}
		connectionsAdded = true;
		return count;
	}
	
	/**
	 * Builds a list of all possible jumps between all full clusters
	 * 
	 * @param clusters
	 * @return
	 */
	public List<Jump> getAllPossibleJumps() {
		// force it to populate connections if not yet populated
		getClusters();
		
		List<Jump> jumps = new ArrayList<>();
		for (int c1=0; c1<clusters.size(); c1++) {
			FaultSubsectionCluster cluster1 = clusters.get(c1);
			jumps.addAll(cluster1.getConnections());
		}
		return jumps;
	}
	
	/**
	 * Returns a list of any possible connection(s) between the given clusters
	 * 
	 * @param from
	 * @param to
	 * @return List of allowed jumps, can be empty or null if no connections possible
	 */
	protected abstract List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to);
	
	/**
	 * 
	 * @param parentID1
	 * @param parentID2
	 * @return true if there is any allowed direct connection between the given parent section IDs
	 */
	public boolean areParentSectsConnected(int parentID1, int parentID2) {
		// force it to populate connections if not yet populated
		getClusters();
		return connectedParents.contains(new IDPairing(parentID1, parentID2));
	}
	
	public static class ConnStratTypeAdapter extends TypeAdapter<ClusterConnectionStrategy> {

		private List<? extends FaultSection> subSects;

		public ConnStratTypeAdapter(List<? extends FaultSection> subSects) {
			this.subSects = subSects;
		}

		@Override
		public void write(JsonWriter out, ClusterConnectionStrategy value) throws IOException {
			// serialize based on clusters list
			out.beginObject();
			out.name("name").value(value.getName());
			out.name("clusters").beginArray();
			for (FaultSubsectionCluster cluster : value.getClusters()) {
				out.beginObject();
				out.name("parentID").value(cluster.parentSectionID);
				out.name("subSectIDs").beginArray();
				for (FaultSection sect : cluster.subSects)
					out.value(sect.getSectionId());
				out.endArray();
				out.name("endSectIDs").beginArray();
				for (FaultSection sect : cluster.endSects)
					out.value(sect.getSectionId());
				out.endArray();
				List<Jump> connections = cluster.getConnections();
				if (connections != null) {
					out.name("connections").beginArray();
					for (Jump jump : connections) {
						out.beginObject();
						out.name("fromSectID").value(jump.fromSection.getSectionId());
						out.name("toParentID").value(jump.toSection.getParentSectionId());
						out.name("toSectID").value(jump.toSection.getSectionId());
						out.name("distance").value(jump.distance);
						out.endObject();
					}
					out.endArray();
				}
				out.endObject();
			}
			out.endArray();
			out.endObject();
		}

		@Override
		public ClusterConnectionStrategy read(JsonReader in) throws IOException {
			in.beginObject();
			String name = null;
			List<FaultSubsectionCluster> clusters = null;
			
			while (in.hasNext()) {
				String jsonName = in.nextName();
				switch (jsonName) {
				case "name":
					name = in.nextString();
					break;
				case "clusters":
					clusters = loadClusters(in);
					break;
				default:
					throw new IllegalStateException("Unexpected JSON with name="+jsonName);
				}
			}
			in.endObject();
			Preconditions.checkNotNull(clusters);
			return new PrecomputedClusterConnectionStrategy(name, subSects, clusters);
		}
		
		private FaultSection getSect(int sectID) {
			Preconditions.checkState(sectID >= 0 && sectID < subSects.size(),
					"sectID=%s outside valid range: [0,%s]", sectID, subSects.size()-1);
			FaultSection sect = subSects.get(sectID);;
			Preconditions.checkState(sect.getSectionId() == sectID,
					"Subsection indexing mismatch. Section at index %s has sectID=%s",
					sectID, sect.getSectionId());
			return sect;
		}
		
		private List<FaultSubsectionCluster> loadClusters(JsonReader in) throws IOException {
			List<FaultSubsectionCluster> clusters = new ArrayList<>();
			in.beginArray();
			
			Map<Integer, FaultSubsectionCluster> parentsToClusters = new HashMap<>();
			// can't fill in jumps until all clusters have been loaded
			Map<FaultSubsectionCluster, List<JumpStub>> clusterJumps = new HashMap<>();
			
			while (in.hasNext()) {
				in.beginObject();
				
				Integer parentID = null;
				List<FaultSection> subSects = null;
				List<FaultSection> endSects = null;
				List<JumpStub> jumps = null;
				
				while (in.hasNext()) {
					switch (in.nextName()) {
					case "parentID":
						parentID = in.nextInt();
						break;
					case "subSectIDs":
						in.beginArray();
						subSects = new ArrayList<>();
						while (in.hasNext())
							subSects.add(getSect(in.nextInt()));
						in.endArray();
						break;
					case "endSectIDs":
						in.beginArray();
						endSects = new ArrayList<>();
						while (in.hasNext())
							endSects.add(getSect(in.nextInt()));
						in.endArray();
						break;
					case "connections":
						in.beginArray();
						jumps = new ArrayList<>();
						while (in.hasNext()) {
							in.beginObject();
							FaultSection fromSect = null;
							FaultSection toSect = null;
							Double distance = null;
							Integer toParentID = null;
							while (in.hasNext()) {
								switch (in.nextName()) {
								case "fromSectID":
									fromSect = getSect(in.nextInt());
									break;
								case "toSectID":
									toSect = getSect(in.nextInt());
									break;
								case "toParentID":
									toParentID = in.nextInt();
									break;
								case "distance":
									distance = in.nextDouble();
									break;

								default:
									break;
								}
							}
							Preconditions.checkNotNull(fromSect, "Jump fromSectID not specified");
							Preconditions.checkNotNull(toSect, "Jump toSectID not specified");
							Preconditions.checkNotNull(toParentID, "Jump toParentSectID not specified");
							Preconditions.checkState(toParentID.intValue() == toSect.getParentSectionId(),
									"toParentID=%s but toSect.getParentSectionId()=%s",
									toParentID, toSect.getParentSectionId());
							Preconditions.checkNotNull(distance, "Jump distance not specified");
							jumps.add(new JumpStub(fromSect, toSect, distance));
						
							in.endObject();
						}
						in.endArray();
						break;

					default:
						break;
					}
				}
				Preconditions.checkNotNull(parentID, "Cluster parentID not specified");
				Preconditions.checkNotNull(subSects, "Cluster subSects not specified");
				FaultSubsectionCluster cluster;
				if (endSects != null)
					cluster = new FaultSubsectionCluster(subSects, endSects);
				else
					cluster = new FaultSubsectionCluster(subSects);
				clusters.add(cluster);
				parentsToClusters.put(parentID, cluster);
				if (jumps != null)
					clusterJumps.put(cluster, jumps);
				
				in.endObject();
			}
			
			in.endArray();
			
			// now finalize jumps
			for (FaultSubsectionCluster cluster : clusterJumps.keySet()) {
				List<JumpStub> stubs = clusterJumps.get(cluster);
				for (JumpStub stub : stubs) {
					int toParent = stub.toSection.getParentSectionId();
					FaultSubsectionCluster toCluster = parentsToClusters.get(toParent);
					Preconditions.checkNotNull(toCluster,
							"Jump to a non-existent cluster with parentID=%s", toParent);
					Preconditions.checkState(stub.fromSection.getParentSectionId() == cluster.parentSectionID,
							"Jump fromSect is from an unexpected parent section");
					cluster.addConnection(new Jump(stub.fromSection, cluster,
							stub.toSection, toCluster, stub.distance));
				}
			}
			return clusters;
		}
		
	}
	
	private static class JumpStub {
		final FaultSection fromSection;
		final FaultSection toSection;
		final double distance;
		public JumpStub(FaultSection fromSection, FaultSection toSection, double distance) {
			super();
			this.fromSection = fromSection;
			this.toSection = toSection;
			this.distance = distance;
		}
	}
	
	public static void main(String[] args) throws IOException {
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = fm.getFilterBasis();
		
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm,
				null, 0.1);
		
		List<FaultSection> parentSects = fm.fetchFaultSections();
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File cacheFile = new File("/tmp/dist_az_cache_"+fm.encodeChoiceString()+"_"+subSects.size()
			+"_sects_"+parentSects.size()+"_parents.csv");
		if (cacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+cacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(cacheFile);
		}
		
		DistCutoffClosestSectClusterConnectionStrategy connStrat =
				new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 5d);
		
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		builder.registerTypeHierarchyAdapter(ClusterConnectionStrategy.class,
				new ConnStratTypeAdapter(subSects));
		
		Gson gson = builder.create();
		
		String json = gson.toJson(connStrat);
		System.out.println(json);
		System.out.println("Loading...");
		ClusterConnectionStrategy loaded = gson.fromJson(json, ClusterConnectionStrategy.class);
		System.out.println("Validating...");
		List<FaultSubsectionCluster> clusters1 = connStrat.getClusters();
		List<FaultSubsectionCluster> clusters2 = loaded.getClusters();
		Preconditions.checkState(clusters1.size() == clusters2.size());
		for (int i=0; i<clusters1.size(); i++) {
			FaultSubsectionCluster c1 = clusters1.get(i);
			FaultSubsectionCluster c2 = clusters2.get(i);
			Preconditions.checkState(c1.equals(c2),
					"Cluster mismatch at %s:\n\tOrig: %s\n\tLoaded", i, c1, c2);
			List<Jump> jumps1 = c1.getConnections();
			List<Jump> jumps2 = c2.getConnections();
			if (jumps1 == null) {
				Preconditions.checkState(jumps2 == null);
			} else {
				Preconditions.checkState(jumps1.size() == jumps2.size());
				for (int j=0; j<jumps1.size(); j++)
					Preconditions.checkState(jumps1.get(j).equals(jumps2.get(j)));
			}
		}
		System.out.println("DONE");
	}

}
