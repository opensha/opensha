package org.opensha.sha.earthquake.faultSysSolution.util;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.TrueMeanRuptureMappings;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;

public class TrueMeanSolutionCreator {
	
	/**
	 * Class that describes a unique subsection as described by its id, dip,
	 * and upper/lower depths.
	 * 
	 * @author kevin
	 *
	 */
	private static class UniqueSection {
		// part of unique checks
		private int parentID;
		private String name;
		private double aveDip;
		private double aveUpperDepth;
		private double aveLowerDepth;
		
		private Object[] comp;
		
		// not part of unique checks
		private FaultSection sect;
		private int globalID;
		private double weightUsed;
		private double weightedSlip;
		private double weightedSlipStdDev;
		private double weightedCoupling;

		public UniqueSection(FaultSection sect) {
			super();
			this.parentID = sect.getParentSectionId();
			this.name = sect.getName();
			this.aveDip = sect.getAveDip();
			this.aveUpperDepth = sect.getReducedAveUpperDepth();
			this.aveLowerDepth = sect.getAveLowerDepth();
			
			comp = new Object[] {parentID, name, aveDip, aveUpperDepth, aveLowerDepth};
			
			this.sect = sect;
		}
		
		public void setGlobalID(int globalID) {
			this.globalID = globalID;
		}
		
		public void addInstance(FaultSection sect, double weight) {
			this.weightUsed += weight;
			weightedSlip += sect.getOrigAveSlipRate()*weight;
			weightedSlipStdDev += sect.getOrigSlipRateStdDev()*weight;
			weightedCoupling += sect.getCouplingCoeff()*weight;
		}
		
		private static DecimalFormat weightDF = new DecimalFormat("0.##%");
		
		public FaultSection buildGlobalInstance(int instanceNum, double totWeight) {
			FaultSection sect = this.sect.clone();
			if (instanceNum > 0 || weightUsed != totWeight)
				sect.setSectionName(sect.getSectionName()+" (Instance "+instanceNum+", "
						+weightDF.format(weightUsed/totWeight)+" weight)");
			sect.setSectionId(globalID);
			sect.setAveSlipRate(weightedSlip/totWeight);
			sect.setSlipRateStdDev(weightedSlipStdDev/totWeight);
			sect.setCouplingCoeff(weightedCoupling/weightUsed);
			return sect;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(comp);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			return Arrays.equals(comp, ((UniqueSection)obj).comp);
		}
	}
	
	/**
	 * Class that describes a unique rupture, used for combining. Mag's are stored
	 * in an MFD
	 * @author kevin
	 *
	 */
	private static class UniqueRupture {
		// part of unique checks
		private int[] sectIDs;
		private double rake;
		private double area;
		
		private Object[] comp;
		private int hashCode;
		
		// not part of unique checks
		private int globalID;
		private DiscretizedFunc rupMFD;
		private double length;
		
		private double weightMagSum = 0d;
		private double weightSum = 0d;
		
		public UniqueRupture(int[] sectIDs, double rake, double area, double length) {
			super();
			this.sectIDs = sectIDs;
			this.rake = rake;
			this.area = area;
			
			comp = new Object[sectIDs.length+2];
			int cnt = 0;
			comp[cnt++] = rake;
			comp[cnt++] = area;
			for (int sectID : sectIDs)
				comp[cnt++] = sectID;
			hashCode = Arrays.hashCode(comp);
			
			rupMFD = new ArbitrarilyDiscretizedFunc();
			this.length = length;
		}
		
		public void setGlobalID(int globalID) {
			this.globalID = globalID;
		}
		
		public void addWeightMag(double mag, double weight) {
			weightMagSum += mag*weight;
			weightSum += weight;
		}
		
		public boolean addForBranch(double mag, double rate, double weight) {
			boolean newMag = !rupMFD.hasX(mag);
			if (newMag)
				rupMFD.set(mag, rate*weight);
			else
				rupMFD.set(mag, rupMFD.getY(mag)+rate*weight);
			return newMag;
		}
		
		public boolean addForBranch(DiscretizedFunc branchMFD, double weight) {
			boolean newMag = false;
			for (Point2D pt : branchMFD) {
				if (addForBranch(pt.getX(), pt.getY(), weight))
					newMag = true;
			}
			return newMag;
		}
		
		public double getMeanMag() {
			if (rupMFD.size() == 0)
				return weightMagSum/weightSum;
			// average magnitude, weighted by both rate and branch weight
			double meanMag = 0d;
			double weightSum = 0d;
			for (Point2D pt : rupMFD) {
				meanMag += pt.getX()*pt.getY();
				weightSum += pt.getY();
			}
			return meanMag/weightSum;
		}
		
		public DiscretizedFunc getFinalMFD(double totWeight) {
			if (rupMFD.size() == 0)
				return null;
			// rescale for final MFD
			double[] xVals = new double[rupMFD.size()];
			double[] yVals = new double[rupMFD.size()];
			for (int i=0; i<xVals.length; i++) {
				xVals[i] = rupMFD.getX(i);
				yVals[i] = rupMFD.getY(i)/totWeight;
			}
			return new LightFixedXFunc(xVals, yVals);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			return Arrays.equals(comp, ((UniqueRupture)obj).comp);
		}
	}
	
	private HashMap<UniqueSection, Integer> uniqueSectsMap;
	private List<UniqueSection> uniqueSectsList;
	private HashMap<UniqueRupture, Integer> uniqueRupsMap;
	private List<UniqueRupture> uniqueRupsList;
	
	private List<LogicTreeBranch<?>> branches;
	private List<int[]> branchSectMappings;
	private List<int[]> branchRupMappings;
	private List<double[]> branchRupMags;
	
	private HashMap<Integer, int[]> sectMappingsCache;
	private HashMap<Integer, int[]> rupMappingsCache;
	private HashMap<Integer, double[]> rupMagsCache;
	
	private LogicTree<?> tree;
	private BranchWeightProvider weightProv;
	private double totWeight = 0d;

	private boolean doGridProv;
	private AveragingAccumulator<GridSourceProvider> gridProvAvg;
	
	private List<TectonicRegionType> rupTRTs = null;
	
	private boolean allSingleStranded = true;
	
	public TrueMeanSolutionCreator(LogicTree<?> tree) {
		this.tree = tree;
		this.weightProv = tree.getWeightProvider();
	}
	
	public TrueMeanSolutionCreator(LogicTree<?> tree, BranchWeightProvider weightProv) {
		this.tree = tree;
		this.weightProv = weightProv;
	}
	
	public void setDoGridProv(boolean doGridProv) {
		this.doGridProv = doGridProv;
	}
	
	public synchronized void addSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
		Preconditions.checkState(tree.contains(branch), "Branch not contained by tree");
		GridSourceProvider gridProv = doGridProv ? sol.getGridSourceProvider() : null;
		RupSetTectonicRegimes myRupTRTs = sol.getRupSet().getModule(RupSetTectonicRegimes.class);
		if (uniqueSectsMap == null) {
			// first time
			uniqueSectsMap = new HashMap<>();
			uniqueSectsList = new ArrayList<>();
			uniqueRupsMap = new HashMap<>();
			uniqueRupsList = new ArrayList<>();
			
			branches = new ArrayList<>();
			branchSectMappings = new ArrayList<>();
			branchRupMappings = new ArrayList<>();
			branchRupMags = new ArrayList<>();
			sectMappingsCache = new HashMap<>();
			rupMappingsCache = new HashMap<>();
			rupMagsCache = new HashMap<>();
			
			if (gridProv != null)
				gridProvAvg = gridProv.averagingAccumulator();
			if (myRupTRTs != null) {
				System.out.println("Will track rupture tectonic region types");
				rupTRTs = new ArrayList<>();
			}
		} else {
			if (gridProvAvg != null && gridProv == null) {
				System.err.println("WARNING: not all solutions contain grid source providers, disabling averaging");
				gridProvAvg = null;
			}
			if (rupTRTs != null && myRupTRTs == null) {
				System.err.println("WARNING: not all rupture sets contain tectonic region types, no longer tracking");
				rupTRTs = null;
			}
		}
		
		double weight = weightProv.getWeight(branch);
		System.out.println("Processing branch: "+branch+", weight="+weight);
		totWeight += weight;
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		allSingleStranded = allSingleStranded && rupSet.hasModule(ClusterRuptures.class);
		if (allSingleStranded) {
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			for (int r=0; allSingleStranded && r<cRups.size(); r++)
				allSingleStranded = cRups.get(r).singleStrand;
		}
		
		// figure out section mappings
		int[] sectMappings = new int[rupSet.getNumSections()];
		int numNewSects = 0;
		for (int s=0; s<sectMappings.length; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			UniqueSection unique = new UniqueSection(sect);
			int globalID;
			if (uniqueSectsMap.containsKey(unique)) {
				// duplicate
				globalID = uniqueSectsMap.get(unique);
				unique = uniqueSectsList.get(globalID);
			} else {
				// new
				numNewSects++;
				globalID = uniqueSectsList.size();
				unique.setGlobalID(globalID);
				uniqueSectsList.add(unique);
				uniqueSectsMap.put(unique, globalID);
			}
			sectMappings[s] = globalID;
			unique.addInstance(sect, weight);
		}
		System.out.println("\t"+numNewSects+"/"+sectMappings.length+" new unique sections");
		
		// figure out rupture mappings
		int[] rupMappings = new int[rupSet.getNumRuptures()];
		int numNewRups = 0;
		int numNewNonzeroRups = 0;
		int numNewMags = 0;
		RupMFDsModule rupMFDs = sol.getModule(RupMFDsModule.class);
		for (int r=0; r<rupMappings.length; r++) {
			List<Integer> origSectIDs = rupSet.getSectionsIndicesForRup(r);
			double rate = sol.getRateForRup(r);
			int[] globalSectIDs = new int[origSectIDs.size()];
			for (int i=0; i<globalSectIDs.length; i++)
				globalSectIDs[i] = sectMappings[origSectIDs.get(i)];
			UniqueRupture unique = new UniqueRupture(globalSectIDs, rupSet.getAveRakeForRup(r),
					rupSet.getAreaForRup(r), rupSet.getLengthForRup(r));
			int globalID;
			if (uniqueRupsMap.containsKey(unique)) {
				// duplicate
				globalID = uniqueRupsMap.get(unique);
				unique = uniqueRupsList.get(globalID);
			} else {
				// it's new
				numNewRups++;
				globalID = uniqueRupsMap.size();
				unique.setGlobalID(globalID);
				uniqueRupsList.add(unique);
				uniqueRupsMap.put(unique, globalID);
				if (rupTRTs != null)
					rupTRTs.add(myRupTRTs.get(r));
			}
			if (rate > 0 && unique.rupMFD.size() == 0)
				numNewNonzeroRups++;
			rupMappings[r] = globalID;
			double rupSetMag = rupSet.getMagForRup(r);
			unique.addWeightMag(rupSetMag, weight);
			if (rate > 0d) {
				DiscretizedFunc rupMFD = rupMFDs == null ? null : rupMFDs.getRuptureMFD(r);
				boolean newMag;
				if (rupMFD == null)
					newMag = unique.addForBranch(rupSetMag, rate, weight);
				else
					newMag = unique.addForBranch(rupMFD, weight);
				if (newMag)
					numNewMags++;
			}
		}
		System.out.println("\t"+numNewRups+"/"+rupMappings.length+" new unique ruptures");
		System.out.println("\t"+numNewNonzeroRups+"/"+rupMappings.length+" new unique ruptures with nonzero rates");
		System.out.println("\t"+numNewMags+"/"+rupMappings.length+" new unique rupture magnitudes");
		
		if (gridProvAvg != null)
			gridProvAvg.process(gridProv, weight);
		
		branches.add(branch);
		
		int sectMappingHash = Arrays.hashCode(sectMappings);
		if (sectMappingsCache.containsKey(sectMappingHash) && Arrays.equals(sectMappings, sectMappingsCache.get(sectMappingHash))) {
			// re-use the identical prior one
			branchSectMappings.add(sectMappingsCache.get(sectMappingHash));
		} else {
			branchSectMappings.add(sectMappings);
			sectMappingsCache.put(sectMappingHash, sectMappings);
		}
		
		int rupMappingHash = Arrays.hashCode(rupMappings);
		if (rupMappingsCache.containsKey(rupMappingHash) && Arrays.equals(rupMappings, rupMappingsCache.get(rupMappingHash))) {
			// re-use the identical prior one
			branchRupMappings.add(rupMappingsCache.get(rupMappingHash));
		} else {
			branchRupMappings.add(rupMappings);
			rupMappingsCache.put(rupMappingHash, rupMappings);
		}
		
		double[] rupMags = rupSet.getMagForAllRups();
		int magMappingHash = Arrays.hashCode(rupMags);
		if (rupMagsCache.containsKey(magMappingHash) && Arrays.equals(rupMags, rupMagsCache.get(magMappingHash))) {
			// re-use the identical prior one
			branchRupMags.add(rupMagsCache.get(magMappingHash));
		} else {
			branchRupMags.add(rupMags);
			rupMagsCache.put(magMappingHash, rupMags);
		}
	}
	
	public synchronized FaultSystemSolution build() {
		System.out.println("Building true mean with "+uniqueSectsList.size()+" unique sections and "
				+uniqueRupsList.size()+" unique ruptures, totalWeight="+totWeight);
		
		Table<Integer, String, Integer> instanceCounts = HashBasedTable.create();
		// build sections
		List<FaultSection> uniqueSects = new ArrayList<>();
		for (int s=0; s<uniqueSectsList.size(); s++) {
			UniqueSection unique = uniqueSectsList.get(s);
			
			// figure out the instance count
			Integer count = instanceCounts.get(unique.parentID, unique.name);
			if (count == null)
				count = 0;
			instanceCounts.put(unique.parentID, unique.name, count+1);
			
			// this will scale slip rates according to weight actually used
			FaultSection sect = unique.buildGlobalInstance(count, totWeight);
			uniqueSects.add(sect);
		}
		
		List<List<Integer>> globalSectsForRups = new ArrayList<>();
		double[] avgMags = new double[uniqueRupsList.size()];
		double[] rakes = new double[avgMags.length];
		double[] areas = new double[avgMags.length];
		double[] lengths = new double[avgMags.length];
		double[] rates = new double[avgMags.length];
		DiscretizedFunc[] rupMFDs = new DiscretizedFunc[avgMags.length];
		for (int r=0; r<avgMags.length; r++) {
			UniqueRupture unique = uniqueRupsList.get(r);
			avgMags[r] = unique.getMeanMag();
			rakes[r] = unique.rake;
			areas[r] = unique.area;
			lengths[r] = unique.length;
			DiscretizedFunc mfd = unique.getFinalMFD(totWeight);
			rates[r] = mfd == null ? 0d : mfd.calcSumOfY_Vals();
			rupMFDs[r] = mfd;
			globalSectsForRups.add(Ints.asList(unique.sectIDs));
		}
		
		FaultSystemRupSet avgRupSet = new FaultSystemRupSet(uniqueSects, globalSectsForRups, avgMags, rakes, areas, lengths);
		if (allSingleStranded)
			avgRupSet.addModule(ClusterRuptures.singleStranged(avgRupSet));
		
		if (rupTRTs != null)
			avgRupSet.addModule(new RupSetTectonicRegimes(avgRupSet, rupTRTs.toArray(new TectonicRegionType[0])));
		
		FaultSystemSolution avgSol = new FaultSystemSolution(avgRupSet, rates);
		avgSol.addModule(new RupMFDsModule(avgSol, rupMFDs));
		if (gridProvAvg != null)
			avgSol.addModule(gridProvAvg.getAverage());
		
		LogicTree<?> tree = this.tree;
		Preconditions.checkState(branches.size() <= tree.size(), "More branches added than exist in tree, must be duplicates");
		if (branches.size() < tree.size()) {
			// we did a subset, build a subset tree
			tree = tree.subset(branches);
		}
		avgRupSet.addModule(TrueMeanRuptureMappings.build(tree, branchSectMappings, branchRupMappings, branchRupMags));
		
		return avgSol;
	}
	
	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		File sltFile;
		File outputFile;
		File gridProvFile;
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			File dir = new File("/data/kevin/nshm23/batch_inversions/"
					+ "2023_06_23-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
			sltFile = new File(dir, "results.zip");
			gridProvFile = new File(dir, "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip");
			outputFile = new File(dir, "true_mean_solution.zip");
		} else if (args.length == 2 || args.length == 3) {
			sltFile = new File(args[0]);
			outputFile = new File(args[1]);
			if (args.length == 3)
				gridProvFile = new File(args[2]);
			else
				gridProvFile = null;
		} else {
			throw new IllegalArgumentException("USAGE: <slt-file.zip> <output-file.zip> [<BA sol or grid prov.zip>]");
		}
		Preconditions.checkState(sltFile.exists(), "Solution logic tree file doesn't exist: %s", sltFile.getAbsolutePath());
		SolutionLogicTree slt = SolutionLogicTree.load(sltFile);
		LogicTree<?> tree = slt.getLogicTree();
		GridSourceProvider avgGridProv = null;
		if (gridProvFile != null) {
			ZipFile zip = new ZipFile(gridProvFile);
			if (FaultSystemSolution.isSolution(zip)) {
				avgGridProv = FaultSystemSolution.load(zip).requireModule(GridSourceProvider.class);
			} else {
				ModuleArchive<OpenSHA_Module> avgArchive = new ModuleArchive<>(zip);
				avgGridProv = avgArchive.requireModule(GridSourceProvider.class);
			}
		}
		
		TrueMeanSolutionCreator creator = new TrueMeanSolutionCreator(tree);
		// only bother to average grid provs if we don't have an external
		creator.setDoGridProv(avgGridProv == null);
		
		ClusterRuptures cRups = null;
		for (int i=0; i<tree.size(); i++) {
			LogicTreeBranch<?> branch = tree.getBranch(i);
			System.out.println("Processing branch "+i+"/"+tree.size()+": "+branch);
			FaultSystemSolution sol = slt.forBranch(branch);
			if (cRups != null && cRups.size() != sol.getRupSet().getNumRuptures())
				cRups = null;
			if (cRups == null) {
				cRups = sol.getRupSet().getModule(ClusterRuptures.class);
				if (cRups == null) {
					System.err.println("WARNING: Building ClusterRuptures and assuming single-stranded");
					cRups = ClusterRuptures.singleStranged(sol.getRupSet());
					sol.getRupSet().addModule(cRups);
				}
			} else {
				sol.getRupSet().addModule(cRups);
			}
			creator.addSolution(sol, branch);
		}
		
		FaultSystemSolution avgSol = creator.build();
		if (avgGridProv != null)
			avgSol.setGridSourceProvider(avgGridProv);
		
		avgSol.write(outputFile);
	}

}
