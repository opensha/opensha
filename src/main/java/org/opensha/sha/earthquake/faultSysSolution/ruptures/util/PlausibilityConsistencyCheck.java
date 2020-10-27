package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MultiDirectionalPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptivePermutationStrategy;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class PlausibilityConsistencyCheck {

	public static void main(String[] args) throws IOException, DocumentException {
		// this will check a rupture set against it's plausibility fiters to make sure that it passes them
		// if it doesn't, then there is a bug somewhere, most likely a difference between the apply and testJump
		// methods of a filter
		
		File rupSetFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/"
				+ "fm3_1_cmlAz_cffClusterPathPositive_sectFractPerm0.1.zip");
		
		// alternative rupture set for possible comparisons (see booleans before)
		File altRupSetFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/"
				+ "fm3_1_cmlAz_cffClusterPathPositive.zip");
		
		// these booleans only apply if altRupSetFile != null, and are mutually exclusive
		// if true, then the apply the filters to ruptures in the alternative set that aren't in the
		// main rupture set
		boolean testUniqueAltRups = true;
		// if ture, use the representations (orderings) of ruptures from here that are also
		// in the main rup set
		boolean useAltRupOrders = false;
		
		int maxNumVerbose = 5;
//		HashSet<Class<? extends PlausibilityFilter>> includeTypes = null;
		HashSet<Class<? extends PlausibilityFilter>> includeTypes = new HashSet<>();
		includeTypes.add(SectCountAdaptivePermutationStrategy.ConnPointCleanupFilter.class);
		
		System.out.println("Loading rupture set");
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(rupSetFile);
		
		PlausibilityConfiguration config = rupSet.getPlausibilityConfiguration();
		Preconditions.checkNotNull(config, "Rup set doesn't have plausbility configuration attached");
		
		List<ClusterRupture> rups = rupSet.getClusterRuptures();
		Preconditions.checkNotNull(rups, "Rup set doesn't have cluster ruptures attached");
		List<ClusterRupture> origRups = rups;
		
		PlausibilityConfiguration configForMulti = config;
		
		if (altRupSetFile != null && (useAltRupOrders || testUniqueAltRups)) {
			Preconditions.checkState(!useAltRupOrders || !testUniqueAltRups,
					"testUniqueAltRups and useAltRupOrders are mutually exclusive");
			System.out.println("Loading alt rupture set");
			FaultSystemRupSet altRupSet = FaultSystemIO.loadRupSet(altRupSetFile);
			List<ClusterRupture> altRups = altRupSet.getClusterRuptures();
			HashMap<UniqueRupture, ClusterRupture> uniques = new HashMap<>();
			for (ClusterRupture rup : rups)
				uniques.put(rup.unique, rup);
			
			rups = new ArrayList<>();
			for (ClusterRupture rup : altRups) {
				boolean common = uniques.containsKey(rup.unique);
				if (common && useAltRupOrders || !common && testUniqueAltRups)
					rups.add(rup);
			}
			System.out.println("Using "+rups.size()+" identical ruptures from alt source");
			configForMulti = altRupSet.getPlausibilityConfiguration();
		} else {
			useAltRupOrders = false;
			testUniqueAltRups = false;
		}
		
		int failCount = 0;
		
		List<PlausibilityFilter> filters = config.getFilters();
		if (includeTypes != null)
			for (int i=filters.size(); --i>=0;)
				if (!includeTypes.contains(filters.get(i).getClass()))
					filters.remove(i);
		System.out.println("Testing "+rups.size()+" ruptures against "+filters.size()+" filters");
		
//		RuptureConnectionSearch connSearch = new RuptureConnectionSearch(
//				rupSet, config.getDistAzCalc(), config.getConnectionStrategy().getMaxJumpDist(), false);
		for (int i=0; i<filters.size(); i++) {
			PlausibilityFilter filter = filters.get(i);
			if (filter.isDirectional(false)) {
				System.out.println("Wrapping "+filter.getName()+" with a multi-directional");
//				filters.set(i, new MultiDirectionalPlausibilityFilter(filter, connSearch, false));
				filters.set(i, new MultiDirectionalPlausibilityFilter(filter, configForMulti, false));
			}
		}
		
		for (ClusterRupture rup : rups) {
			boolean fail = false;
			for (PlausibilityFilter filter : filters) {
				// if testUniqueAltRups is true and it passes, we want to debug
				// otherwise we want to debug if it's false
				if (filter.apply(rup, false).isPass() == testUniqueAltRups) {
					fail = true;
					if (failCount < maxNumVerbose) {
						System.out.println("=======================");
						if (testUniqueAltRups)
							System.out.println("Found an alt rupture which passes!");
						else
							System.out.println("Found a failure!");
						System.out.println("\tFilter: "+filter.getName());
						System.out.println("\tRupture: "+rup);
						System.out.println("Applying verbose...");
						PlausibilityResult result = filter.apply(rup, true);
						System.out.println("Result: "+result);
						System.out.println("=======================");
						if (origRups != rups) {
							// look for the original version
							ClusterRupture origMatch = null;
							for (ClusterRupture oRup : origRups) {
								if (rup.unique.equals(oRup.unique)) {
									origMatch = oRup;
									break;
								}
							}
							if (origMatch == null) {
								System.out.println("No original match found");
							} else {
								System.out.println("Testing original match");
								System.out.println("\tRupture: "+origMatch);
								System.out.println("Applying verbose...");
								result = filter.apply(origMatch, true);
								System.out.println("Result: "+result);
							}
							System.out.println("=======================");
						}
					}
				}
			}
			if (fail)
				failCount++;
//				System.exit(0);
		}
		if (testUniqueAltRups)
			System.out.println("Total number alt ruptures which pass: "+failCount+"/"+rups.size());
		else
			System.out.println("Total number of ruptures which fail: "+failCount+"/"+rups.size());
	}

}
