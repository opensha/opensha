package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MultiDirectionalPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class KaikouraRuptureTests {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File refRupSet = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/"
				+ "nz_demo5_crustal_plausibleMulti10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths"
				+ "_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(refRupSet);
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		List<FaultSection> rupSects = new ArrayList<>();
		
		CSVFile<String> sectsCSV = CSVFile.readStream(KaikouraRuptureTests.class.getResourceAsStream("KaikouraRuptureSects.csv"), false);
		
		for (int row=1; row<sectsCSV.getNumRows(); row++) {
			String name = sectsCSV.get(row, 0);
			int id = sectsCSV.getInt(row, 1);
			System.out.println("Adding sections for "+name+" ("+id+")");
			
			if (sectsCSV.get(row, 8).trim().isEmpty()) {
				System.out.println("\tskipping, no sections");
				continue;
			}
			
			int startIndex = sectsCSV.getInt(row, 8);
			int endIndex = sectsCSV.getInt(row, 9);
			for (int i=startIndex; i<=endIndex; i++) {
				FaultSection sect = subSects.get(i);
				System.out.println("\t"+sect.getSectionId()+". "+sect.getSectionName());
				Preconditions.checkState(sect.getParentSectionId() == id);
				rupSects.add(sect);
			}
		}
		
		System.out.println("Rupture has "+rupSects.size()+" sections");
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		RuptureConnectionSearch connSearch = new RuptureConnectionSearch(rupSet, distAzCalc, 100d, false);
		
		System.out.println("Building clusters...");
		List<FaultSubsectionCluster> clusters = connSearch.calcClusters(rupSects, true);
		System.out.println("Building jumps...");
		List<Jump> jumps = connSearch.calcRuptureJumps(clusters, true);
		
		System.out.println("Building rupture...");
		ClusterRupture rup = connSearch.buildClusterRupture(clusters, jumps, true);
		
		System.out.println("*** RUPTURE ***");
		System.out.println(rup);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		filters.addAll(rupSet.getPlausibilityConfiguration().getFilters());
		filters.add(new CumulativeAzimuthChangeFilter(new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
		filters.add(new CumulativeRakeChangeFilter(180f));
		filters.add(new JumpAzimuthChangeFilter(new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 60f));
		filters.add(new CumulativeProbabilityFilter(0.001f, new Shaw07JumpDistProb(1, 3d)));
		
		boolean splayed = !rup.splays.isEmpty();
		
		for (int f=0; f<filters.size(); f++) {
			PlausibilityFilter filter = filters.get(f);
			if (filter instanceof PathPlausibilityFilter) {
				PathPlausibilityFilter pFilter = (PathPlausibilityFilter)filter;
				if (pFilter.getEvaluators().length > 1) {
					// separate them
					for (NucleationClusterEvaluator eval : pFilter.getEvaluators()) {
						if (eval instanceof NucleationClusterEvaluator.Scalar<?>)
							filters.add(f+1, new PathPlausibilityFilter.Scalar<>((NucleationClusterEvaluator.Scalar<?>)eval));
						else
							filters.add(f+1, new PathPlausibilityFilter(eval));
					}
				}
			}
		}
		
		for (PlausibilityFilter filter : filters) {
			if (filter.isDirectional(splayed)) {
				if (filter instanceof ScalarValuePlausibiltyFilter<?>)
					filter = new MultiDirectionalPlausibilityFilter.Scalar<>((ScalarValuePlausibiltyFilter<?>)filter, connSearch, false);
				else
					filter = new MultiDirectionalPlausibilityFilter(filter, connSearch, false);
			}
			
			System.out.println("Testing "+filter.getName());
			PlausibilityResult result = filter.apply(rup, false);
			System.out.println("\tResult: "+result);
			if (filter instanceof ScalarValuePlausibiltyFilter<?>)
				System.out.println("\tScalar: "+((ScalarValuePlausibiltyFilter<?>)filter).getValue(rup));
		}
	}

}
