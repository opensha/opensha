package scratch.UCERF3.inversion.ruptures;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.MinSectsPerParentFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterPermutationStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.MaxDistSingleConnectionClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRuptureBuilder {
	
	private List<FaultSubsectionCluster> clusters;
	private List<PlausibilityFilter> filters;
	private int maxNumSplays = 0;
	
	public ClusterRuptureBuilder(List<? extends FaultSection> subSections,
			ClusterConnectionStrategy connectionStrategy, SectionDistanceAzimuthCalculator distAzCalc,
			List<PlausibilityFilter> filters, int maxNumSplays) {
		this(buildClusters(subSections, connectionStrategy, distAzCalc), filters, maxNumSplays);
	}
	
	public static List<FaultSubsectionCluster> buildClusters(List<? extends FaultSection> subSections,
			ClusterConnectionStrategy connectionStrategy, SectionDistanceAzimuthCalculator distAzCalc) {
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
		System.out.println("Building connections for "+subSections.size()
			+" subsections on "+clusters.size()+" parent sections");
		int count = connectionStrategy.addConnections(clusters, distAzCalc);
		System.out.println("Found "+count+" possible section connections");
		
		return clusters;
	}
	
	public ClusterRuptureBuilder(List<FaultSubsectionCluster> clusters,
			List<PlausibilityFilter> filters, int maxNumSplays) {
		this.clusters = clusters;
		this.filters = filters;
		this.maxNumSplays = maxNumSplays;
	}
	
	private final int debugSection = 13;
	private int largestRup = 0;
	private int largestRupPrintMod = 10;
	
	public List<ClusterRupture> build(ClusterPermutationStrategy permutationStrategy) {
		List<ClusterRupture> rups = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		largestRup = 0;
		
		for (FaultSubsectionCluster cluster : clusters) {
			for (FaultSection startSection : cluster.subSects) {
				if (startSection.getSectionId() == debugSection)
					System.out.println("Building for section "+debugSection);
				for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
						cluster, startSection)) {
					FaultSubsectionCluster[] primaryStrand = { permutation };
					ClusterRupture rup = new ClusterRupture(primaryStrand);
					PlausibilityResult result = testRup(rup);
					if (startSection.getSectionId() == debugSection)
						System.out.println("\tPermutation "+permutation+" result="+result);
					if (!result.canContinue())
						// stop building here
						continue;
					if (result.isPass()) {
						// passes as is, add it if it's new
						UniqueRupture unique = new UniqueRupture(rup);
						if (!uniques.contains(unique)) {
							rups.add(rup);
							uniques.add(unique);
							if (rup.sectsSet.size() > largestRup) {
								largestRup = rup.sectsSet.size();
								if (largestRup % largestRupPrintMod == 0)
									System.out.println("\tNew largest rup has "+largestRup
											+" subsections with "+rup.jumps.size()
											+" jumps ("+rups.size()+" rups in total)");
							}
						}
					}
					// continue to build this rupture
					addRuptures(rups, uniques, rup, primaryStrand, permutationStrategy);
				}
				if (startSection.getSectionId() == debugSection)
					System.out.println("DONE building for section "+debugSection);
			}
			System.out.println("Have "+rups.size()+" ruptures after processing cluster "
					+cluster.parentSectionID+": "+cluster.parentSectionName);
		}
		
		return rups;
	}
	
	private PlausibilityResult testRup(ClusterRupture rupture) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (PlausibilityFilter filter : filters) {
			result = result.logicalAnd(filter.apply(rupture));
			if (!result.canContinue())
				break;
		}
		return result;
	}
	
	private PlausibilityResult offerJump(ClusterRupture rupture, Jump jump) {
		PlausibilityResult result = PlausibilityResult.PASS;
		final boolean debug = rupture.primaryStrand[0].subSects.get(0).getSectionId() == debugSection;
		for (PlausibilityFilter filter : filters) {
			PlausibilityResult filterResult = filter.test(rupture, jump);
			if (debug)
				System.out.println("\t\t"+ClassUtils.getClassNameWithoutPackage(filter.getClass())
					+": "+filterResult);
			result = result.logicalAnd(filterResult);
			if (!result.canContinue())
				break;
		}
		return result;
	}
	
	private void addRuptures(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques,
			ClusterRupture currentRupture, FaultSubsectionCluster[] currentStrand,
			ClusterPermutationStrategy permutationStrategy) {
		FaultSubsectionCluster lastCluster = currentStrand[currentStrand.length-1];
		FaultSection firstSection = currentStrand[0].firstSect;
		FaultSection lastSection = lastCluster.lastSect;

		// try to grow this strand first
		for (Jump jump : lastCluster.getConnections(lastSection)) {
			if (!currentRupture.sectsSet.contains(jump.toSection))
				addJumpPermutations(rups, uniques, currentRupture, currentStrand, permutationStrategy, jump);
		}
		
		// now try to add splays
		if (currentStrand == currentRupture.primaryStrand && currentRupture.splays.size() < maxNumSplays) {
			for (FaultSubsectionCluster cluster : currentRupture.primaryStrand) {
				for (FaultSection section : cluster.subSects) {
					if (section.equals(firstSection))
						// can't jump from the first section of the rupture
						continue;
					if (section.equals(lastSection))
						// this would be a continuation of the main rupture, not a splay
						break;
					for (Jump jump : cluster.getConnections(section)) {
						if (!currentRupture.sectsSet.contains(jump.toSection))
							addJumpPermutations(rups, uniques, currentRupture, currentStrand, permutationStrategy, jump);
					}
				}
			}
		}
	}

	void addJumpPermutations(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques, ClusterRupture currentRupture,
			FaultSubsectionCluster[] currentStrand, ClusterPermutationStrategy permutationStrategy, Jump jump) {
		permutationLoop:
		for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
				jump.toCluster, jump.toSection)) {
			for (FaultSection sect : permutation.subSects) {
				if (currentRupture.sectsSet.contains(sect)) {
					// this permutation contains a section already part of this rupture, stop
					if (permutationStrategy.arePermutationsIncremental())
						// future permutations will also include this section, stop altogether
						break permutationLoop;
					continue permutationLoop;
				}
			}
			Preconditions.checkState(permutation.firstSect.equals(jump.toSection));
			List<FaultSection> leadingSections = new ArrayList<>();
			for (FaultSubsectionCluster cluster : currentStrand) {
				for (FaultSection sect : cluster.subSects) {
					leadingSections.add(sect);
					if (sect.equals(jump.fromSection))
						break;
				}
			}
			Preconditions.checkState(leadingSections.get(leadingSections.size()-1).equals(jump.fromSection));
			Jump testJump = new Jump(leadingSections, jump.fromCluster,
					jump.toSection, permutation, jump.distance);
			if (currentStrand[0].firstSect.getSectionId() == debugSection) {
				StringBuilder str = new StringBuilder("\tMulti ");
				for (FaultSubsectionCluster cluster : currentStrand)
					str.append(cluster);
				str.append(" => ").append(permutation);
				System.out.println(str);
			}
			PlausibilityResult result = offerJump(currentRupture, testJump);
			if (currentStrand[0].firstSect.getSectionId() == debugSection)
				System.out.println("\t\tresult: "+result);
			if (!result.canContinue()) {
				// stop building this permutation
				if (permutationStrategy.arePermutationsIncremental())
					// further permutations won't work either, bail on this junction as a whole
					break;
				continue;
			}
			ClusterRupture rup = currentRupture.take(testJump);
			if (result.isPass()) {
				// passes as is, add it if it's new
				UniqueRupture unique = new UniqueRupture(rup);
				if (!uniques.contains(unique)) {
					rups.add(rup);
					uniques.add(unique);
				}
				if (rup.sectsSet.size() > largestRup) {
					largestRup = rup.sectsSet.size();
					if (largestRup % largestRupPrintMod == 0)
						System.out.println("\tNew largest rup has "+largestRup
								+" subsections with "+rup.jumps.size()
								+" jumps ("+rups.size()+" rups in total)");
				}
			}
			// continue to build this rupture
			FaultSubsectionCluster[] newCurrentStrand;
			if (currentStrand == currentRupture.primaryStrand) {
				newCurrentStrand = rup.primaryStrand;
			} else {
				// we're building a splay, try to continue that one
				newCurrentStrand = null;
				for (int i=0; i<currentRupture.splays.size(); i++) {
					if (currentStrand == currentRupture.splays.get(i)) {
						newCurrentStrand = rup.splays.get(i);
						break;
					}
				}
				Preconditions.checkNotNull(newCurrentStrand);
				FaultSection newLast = newCurrentStrand[newCurrentStrand.length-1].lastSect;
				Preconditions.checkState(newLast.equals(permutation.lastSect));
			}
			addRuptures(rups, uniques, rup, newCurrentStrand, permutationStrategy);
		}
	}
	
	private class UniqueRupture {
		private List<Integer> sectsSorted;
		private UniqueRupture(ClusterRupture rup) {
			sectsSorted = new ArrayList<>();
			for (FaultSection sect : rup.sectsSet)
				sectsSorted.add(sect.getSectionId());
			Collections.sort(sectsSorted);
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getEnclosingInstance().hashCode();
			result = prime * result + ((sectsSorted == null) ? 0 : sectsSorted.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniqueRupture other = (UniqueRupture) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			if (sectsSorted == null) {
				if (other.sectsSorted != null)
					return false;
			} else if (!sectsSorted.equals(other.sectsSorted))
				return false;
			return true;
		}
		private ClusterRuptureBuilder getEnclosingInstance() {
			return ClusterRuptureBuilder.this;
		}
	}

	public static void main(String[] args) throws IOException {
		FaultModels fm = FaultModels.FM3_1;;
		List<FaultSection> parentSects = fm.fetchFaultSections();
		List<FaultSection> subSects = new ArrayList<>();
		
//		HashSet<Integer> includeParents = new HashSet<>();
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Andreas"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("Garlock"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Jacinto (SB to C)"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Jacinto (CC to SM)"));
		HashSet<Integer> includeParents = null;
		
		for (FaultSection sect : parentSects) {
			if (includeParents != null && !includeParents.contains(sect.getSectionId()))
				continue;
			double maxSubSectionLen = 0.5*sect.getOrigDownDipWidth();
			subSects.addAll(sect.getSubSectionsList(maxSubSectionLen, subSects.size(), 2));
		}
		
		ClusterConnectionStrategy connectionStrategy = new MaxDistSingleConnectionClusterConnectionStrategy(5d);
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File cacheFile = new File("/tmp/dist_az_cache_"+fm.encodeChoiceString()+"_"+subSects.size()
			+"_sects_"+parentSects.size()+"_parents.csv");
		if (cacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+cacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(cacheFile);
		}
		List<FaultSubsectionCluster> clusters = buildClusters(subSects, connectionStrategy, distAzCalc);
		System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
		distAzCalc.writeCacheFile(cacheFile);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		filters.add(new JumpAzimuthChangeFilter(distAzCalc, 60d, true));
		filters.add(new TotalAzimuthChangeFilter(distAzCalc, 60d, true));
		filters.add(new CumulativeAzimuthChangeFilter(distAzCalc, 560d, false, false));
		filters.add(new MinSectsPerParentFilter(2, true, clusters));
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(clusters, filters, 0);
		
		System.out.println("Building ruptures...");
		List<ClusterRupture> rups = builder.build(new UCERF3ClusterPermuationStrategy());
		System.out.println("Built "+rups.size()+" ruptures");
		
		// write out test rup set
		double[] mags = new double[rups.size()];
		double[] rakes = new double[rups.size()];
		double[] rupAreas = new double[rups.size()];
		List<List<Integer>> sectionForRups = new ArrayList<>();
		for (int r=0; r<rups.size(); r++) {
			List<FaultSection> sects = rups.get(r).buildOrderedSectionList();
			List<Integer> ids = new ArrayList<>();
			for (FaultSection sect : sects)
				ids.add(sect.getSectionId());
			sectionForRups.add(ids);
			mags[r] = Double.NaN;
			rakes[r] = Double.NaN;
			rupAreas[r] = Double.NaN;
		}
		FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, null, null, null, 
			sectionForRups, mags, rakes, rupAreas, null, "");
		FaultSystemIO.writeRupSet(rupSet, new File("/tmp/test_rup_set.zip"));
		System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
		distAzCalc.writeCacheFile(cacheFile);
	}

}
