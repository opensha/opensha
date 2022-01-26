package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.dom4j.Element;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAverageableModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Utility for building branch averaged solutions
 * 
 * @author kevin
 *
 */
public class BranchAverageSolutionCreator {
	
	private double totWeight = 0d; 
	private double[] avgRates = null;
	private double[] avgMags = null;
	private double[] avgAreas = null;
	private double[] avgLengths = null;
	private List<AngleAverager> avgRakes = null;
	private List<List<Integer>> sectIndices = null;
	private List<DiscretizedFunc> rupMFDs = null;
	
	private FaultSystemRupSet refRupSet = null;
	private double[] avgSectAseis = null;
	private double[] avgSectCoupling = null;
	private double[] avgSectSlipRates = null;
	private double[] avgSectSlipRateStdDevs = null;
	private List<AngleAverager> avgSectRakes = null;
	
//	private List<? extends LogicTreeBranch<?>> branches = getLogicTree().getBranches();
	
	private LogicTreeBranch<LogicTreeNode> combBranch = null;
	
	private List<Double> weights = new ArrayList<>();
	
	private Map<LogicTreeNode, Integer> nodeCounts = new HashMap<>();
	
	private boolean skipRupturesBelowSectMin = true;
	
	private boolean accumulatingSlipRates = true;
	
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> rupSetAvgAccumulators;
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> solAvgAccumulators;
	
	private List<Class<? extends OpenSHA_Module>> skipModules = new ArrayList<>();
	
	private BranchWeightProvider weightProv;
	
	public BranchAverageSolutionCreator(BranchWeightProvider weightProv) {
		this.weightProv = weightProv;
	}
	
	public void setSkipRupturesBelowSectMin(boolean skipRupturesBelowSectMin) {
		this.skipRupturesBelowSectMin = skipRupturesBelowSectMin;
	}
	
	public void skipModule(Class<? extends BranchAverageableModule<?>> clazz) {
		skipModules.add(clazz);
		if (SolutionSlipRates.class.isAssignableFrom(clazz))
			accumulatingSlipRates = false;
	}

	public void addSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
		double weight = weightProv.getWeight(branch);
		weights.add(weight);
		totWeight += weight;
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		if (accumulatingSlipRates && !sol.hasModule(SolutionSlipRates.class)) {
			if (rupSet.hasModule(AveSlipModule.class) && rupSet.hasModule(SlipAlongRuptureModel.class))
				// add a slip rate module so that actual branch-averaged slip rates are calculated
				// do this because averaged solutions & averaged slip modules won't result in the same solution
				// slip rates as averaging the solution slip rates themselves
				sol.addModule(SolutionSlipRates.calc(sol, rupSet.requireModule(AveSlipModule.class),
						rupSet.requireModule(SlipAlongRuptureModel.class)));
			else
				// don't bother trying to calculate any other slip rates, we don't have them for every branch
				accumulatingSlipRates = false;
		}
		
		if (avgRates == null) {
			// first time
			avgRates = new double[rupSet.getNumRuptures()];
			avgMags = new double[avgRates.length];
			avgAreas = new double[avgRates.length];
			avgLengths = new double[avgRates.length];
			avgRakes = new ArrayList<>();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				avgRakes.add(new AngleAverager());
			
			refRupSet = rupSet;
			
			avgSectAseis = new double[rupSet.getNumSections()];
			avgSectSlipRates = new double[rupSet.getNumSections()];
			avgSectSlipRateStdDevs = new double[rupSet.getNumSections()];
			avgSectCoupling = new double[rupSet.getNumSections()];
			avgSectRakes = new ArrayList<>();
			for (int s=0; s<rupSet.getNumSections(); s++)
				avgSectRakes.add(new AngleAverager());
			
			// initialize accumulators
			rupSetAvgAccumulators = initAccumulators(rupSet);
			solAvgAccumulators = initAccumulators(sol);
			
			combBranch = (LogicTreeBranch<LogicTreeNode>)branch.copy();
			sectIndices = rupSet.getSectionIndicesForAllRups();
			rupMFDs = new ArrayList<>();
			for (int r=0; r<avgRates.length; r++)
				rupMFDs.add(new ArbitrarilyDiscretizedFunc());
		} else {
			Preconditions.checkState(refRupSet.isEquivalentTo(rupSet), "Rupture sets are not equivalent");
		}
		
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeNode combVal = combBranch.getValue(i);
			LogicTreeNode branchVal = branch.getValue(i);
			if (combVal != null && !combVal.equals(branchVal))
				combBranch.clearValue(i);
			int prevCount = nodeCounts.containsKey(branchVal) ? nodeCounts.get(branchVal) : 0;
			nodeCounts.put(branchVal, prevCount+1);
		}
		
		for (int r=0; r<avgRates.length; r++) {
			avgRakes.get(r).add(rupSet.getAveRakeForRup(r), weight);
			double rate = sol.getRateForRup(r);
			if (rate == 0d)
				continue;
			double mag = rupSet.getMagForRup(r);
			
			if (skipRupturesBelowSectMin && modMinMags != null && modMinMags.isRupBelowSectMinMag(r))
				// skip
				continue;
			avgRates[r] += rate*weight;
			DiscretizedFunc rupMFD = rupMFDs.get(r);
			double y = rate*weight;
			if (rupMFD.hasX(mag))
				y += rupMFD.getY(mag);
			rupMFD.set(mag, y);
		}
		addWeighted(avgMags, rupSet.getMagForAllRups(), weight);
		addWeighted(avgAreas, rupSet.getAreaForAllRups(), weight);
		addWeighted(avgLengths, rupSet.getLengthForAllRups(), weight);
		
		for (int s=0; s<rupSet.getNumSections(); s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			avgSectAseis[s] += sect.getAseismicSlipFactor()*weight;
			avgSectSlipRates[s] += sect.getOrigAveSlipRate()*weight;
			avgSectSlipRateStdDevs[s] += sect.getOrigSlipRateStdDev()*weight;
			avgSectCoupling[s] += sect.getCouplingCoeff()*weight;
			avgSectRakes.get(s).add(sect.getAveRake(), weight);
		}
		
		// now work on modules
		processAccumulators(rupSetAvgAccumulators, rupSet, weight);
		processAccumulators(solAvgAccumulators, sol, weight);
	}
	
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> initAccumulators(
			ModuleContainer<OpenSHA_Module> container) {
		List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators = new ArrayList<>();
		for (OpenSHA_Module module : container.getModulesAssignableTo(BranchAverageableModule.class, true, skipModules)) {
			Preconditions.checkState(module instanceof BranchAverageableModule<?>);
			System.out.println("Building branch-averaging accumulator for: "+module.getName());
			AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator =
					((BranchAverageableModule<?>)module).averagingAccumulator();
			if (accumulator == null) {
				System.err.println("WARNING: accumulator is null for module "+module.getName()+", skipping averaging");
				continue;
			}
			accumulators.add(accumulator);
		}
		return accumulators;
	}
	
	private void processAccumulators(List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators, 
			ModuleContainer<OpenSHA_Module> container, double weight) {
		for (int i=accumulators.size(); --i>=0;) {
			AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator = accumulators.get(i);
			try {
				accumulator.processContainer(container, weight);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error processing accumulator, will no longer average "+accumulator.getType().getName());
				System.err.flush();
				accumulators.remove(i);
				if (accumulatingSlipRates && SolutionSlipRates.class.isAssignableFrom(accumulator.getType()))
					// stop calculating slip rates
					accumulatingSlipRates = false;
			}
		}
	}
	
	private static void buildAverageModules(List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators, 
			ModuleContainer<OpenSHA_Module> container) {
		for (AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator : accumulators) {
			try {
				container.addModule(accumulator.getAverage());
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error building average module of type "+accumulator.getType().getName());
				System.err.flush();
			}
		}
	}
	
	public FaultSystemSolution build() {
		Preconditions.checkState(!weights.isEmpty(), "No solutions added!");
		Preconditions.checkState(totWeight > 0, "Total weight is not positive: %s", totWeight);
		
		System.out.println("Common branches: "+combBranch);
//		if (!combBranch.hasValue(DeformationModels.class))
//			combBranch.setValue(DeformationModels.MEAN_UCERF3);
//		if (!combBranch.hasValue(ScalingRelationships.class))
//			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
//		if (!combBranch.hasValue(SlipAlongRuptureModels.class))
//			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
		
		// now scale by total weight
		System.out.println("Normalizing by total weight: "+totWeight);
		double[] rakes = new double[avgRates.length];
		for (int r=0; r<avgRates.length; r++) {
			avgRates[r] /= totWeight;
			avgMags[r] /= totWeight;
			avgAreas[r] /= totWeight;
			avgLengths[r] /= totWeight;
			if (avgRates[r] == 0d) {
				// clear the empty MFD
				Preconditions.checkState(rupMFDs.get(r).size() == 0);
				rupMFDs.set(r, null);
			} else {
				DiscretizedFunc rupMFD = rupMFDs.get(r);
				rupMFD.scale(1d/totWeight);
				double calcRate = rupMFD.calcSumOfY_Vals();
				Preconditions.checkState(DoubleMath.fuzzyEquals(calcRate, avgRates[r], 1e-15),
						"Rupture MFD rate=%s, avgRate=%s", calcRate, avgRates[r]);
			}
			rakes[r] = FaultUtils.getInRakeRange(avgRakes.get(r).getAverage());
		}
		
		List<FaultSection> subSects = new ArrayList<>();
		for (int s=0; s<refRupSet.getNumSections(); s++) {
			FaultSection refSect = refRupSet.getFaultSectionData(s);
			
			avgSectAseis[s] /= totWeight;
			avgSectCoupling[s] /= totWeight;
			avgSectSlipRates[s] /= totWeight;
			avgSectSlipRateStdDevs[s] /= totWeight;
			double avgRake = FaultUtils.getInRakeRange(avgSectRakes.get(s).getAverage());
			
			GeoJSONFaultSection avgSect = new GeoJSONFaultSection(new AvgFaultSection(refSect, avgSectAseis[s],
					avgSectCoupling[s], avgRake, avgSectSlipRates[s], avgSectSlipRateStdDevs[s]));
			subSects.add(avgSect);
		}
		
//		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).forU3Branch(combBranch).rupMags(avgMags).build();
//		// remove these as they're not correct for branch-averaged
//		avgRupSet.removeModuleInstances(InversionTargetMFDs.class);
//		avgRupSet.removeModuleInstances(SectSlipRates.class);
		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).rupRakes(rakes)
				.rupAreas(avgAreas).rupLengths(avgLengths).rupMags(avgMags).build();
		
		// now build averaged modules
		buildAverageModules(rupSetAvgAccumulators, avgRupSet);
		
		int numNonNull = 0;
		boolean haveSlipAlong = false;
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeNode value = combBranch.getValue(i);
			if (value != null) {
				numNonNull++;
				if (value instanceof SlipAlongRuptureModel) {
					avgRupSet.addModule((SlipAlongRuptureModel)value);
					haveSlipAlong = true;
				} else if (value instanceof SlipAlongRuptureModels) {
					avgRupSet.addModule(((SlipAlongRuptureModels)value).getModel());
					haveSlipAlong = true;
				}
			}
		}
		// special cases for UCERF3 branches
		if (!haveSlipAlong && hasAllEqually(nodeCounts, SlipAlongRuptureModels.UNIFORM, SlipAlongRuptureModels.TAPERED)) {
			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
			avgRupSet.addModule(SlipAlongRuptureModels.MEAN_UCERF3.getModel());
			numNonNull++;
		}
		if (combBranch.getValue(ScalingRelationships.class) == null && hasAllEqually(nodeCounts, ScalingRelationships.ELLB_SQRT_LENGTH,
				ScalingRelationships.ELLSWORTH_B, ScalingRelationships.HANKS_BAKUN_08,
				ScalingRelationships.SHAW_2009_MOD, ScalingRelationships.SHAW_CONST_STRESS_DROP)) {
			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
			if (!avgRupSet.hasModule(AveSlipModule.class))
				avgRupSet.addModule(AveSlipModule.forModel(avgRupSet, ScalingRelationships.MEAN_UCERF3));
			numNonNull++;
		}
		if (combBranch.getValue(DeformationModels.class) == null && hasAllEqually(nodeCounts, DeformationModels.GEOLOGIC,
				DeformationModels.ABM, DeformationModels.NEOKINEMA, DeformationModels.ZENGBB)) {
			combBranch.setValue(DeformationModels.MEAN_UCERF3);
			numNonNull++;
		}
		
		if (numNonNull > 0) {
			avgRupSet.addModule(combBranch);
			System.out.println("Combined logic tree branch: "+combBranch);
		}
		
		FaultSystemSolution sol = new FaultSystemSolution(avgRupSet, avgRates);
		
		// now build averaged modules
		buildAverageModules(solAvgAccumulators, sol);
		
		sol.addModule(combBranch);
		sol.addModule(new RupMFDsModule(sol, rupMFDs.toArray(new DiscretizedFunc[0])));
		
		String info = "Branch Averaged Fault System Solution, across "+weights.size()
				+" branches with a total weight of "+totWeight+"."
				+"\n\nThe utilized branches at each level are (counts in parenthesis):"
				+ "\n\n";
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeLevel<? extends LogicTreeNode> level = combBranch.getLevel(i);
			info += level.getName()+":\n";
			for (LogicTreeNode choice : level.getNodes()) {
				Integer count = nodeCounts.get(choice);
				if (count != null)
					info += "\t"+choice.getName()+" ("+count+")\n";
			}
		}
		
		sol.addModule(new InfoModule(info));
		return sol;
	}
	
	private boolean hasAllEqually(Map<LogicTreeNode, Integer> nodeCounts, LogicTreeNode... nodes) {
		Integer commonCount = null;
		for (LogicTreeNode node : nodes) {
			Integer count = nodeCounts.get(node);
			if (count == null)
				return false;
			if (commonCount == null)
				commonCount = count;
			else if (commonCount.intValue() != count.intValue())
				return false;
		}
		return true;
	}
	
	private class AvgFaultSection implements FaultSection {
		
		private FaultSection refSect;
		private double avgAseis;
		private double avgCoupling;
		private double avgRake;
		private double avgSlip;
		private double avgSlipStdDev;

		public AvgFaultSection(FaultSection refSect, double avgAseis, double avgCoupling, double avgRake, double avgSlip, double avgSlipStdDev) {
			this.refSect = refSect;
			this.avgAseis = avgAseis;
			this.avgCoupling = avgCoupling;
			this.avgRake = avgRake;
			this.avgSlip = avgSlip;
			this.avgSlipStdDev = avgSlipStdDev;
		}

		@Override
		public String getName() {
			return refSect.getName();
		}

		@Override
		public Element toXMLMetadata(Element root) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getDateOfLastEvent() {
			return refSect.getDateOfLastEvent();
		}

		@Override
		public void setDateOfLastEvent(long dateOfLastEventMillis) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSlipInLastEvent(double slipInLastEvent) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getSlipInLastEvent() {
			return refSect.getSlipInLastEvent();
		}

		@Override
		public double getAseismicSlipFactor() {
			if ((float)avgCoupling == 0f)
				return 0d;
			return avgAseis;
		}

		@Override
		public void setAseismicSlipFactor(double aseismicSlipFactor) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getCouplingCoeff() {
			if ((float)avgCoupling == 1f)
				return 1d;
			return avgCoupling;
		}

		@Override
		public void setCouplingCoeff(double couplingCoeff) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveDip() {
			return refSect.getAveDip();
		}

		@Override
		public double getOrigAveSlipRate() {
			return avgSlip;
		}

		@Override
		public void setAveSlipRate(double aveLongTermSlipRate) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveLowerDepth() {
			return refSect.getAveLowerDepth();
		}

		@Override
		public double getAveRake() {
			return avgRake;
		}

		@Override
		public void setAveRake(double aveRake) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigAveUpperDepth() {
			return refSect.getOrigAveUpperDepth();
		}

		@Override
		public float getDipDirection() {
			return refSect.getDipDirection();
		}

		@Override
		public FaultTrace getFaultTrace() {
			return refSect.getFaultTrace();
		}

		@Override
		public int getSectionId() {
			return refSect.getSectionId();
		}

		@Override
		public void setSectionId(int sectID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSectionName(String sectName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getParentSectionId() {
			return refSect.getParentSectionId();
		}

		@Override
		public void setParentSectionId(int parentSectionId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getParentSectionName() {
			return refSect.getParentSectionName();
		}

		@Override
		public void setParentSectionName(String parentSectionName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId,
				int minSubSections) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigSlipRateStdDev() {
			return avgSlipStdDev;
		}

		@Override
		public void setSlipRateStdDev(double slipRateStdDev) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isConnector() {
			return refSect.isConnector();
		}

		@Override
		public Region getZonePolygon() {
			return refSect.getZonePolygon();
		}

		@Override
		public void setZonePolygon(Region zonePolygon) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Element toXMLMetadata(Element root, String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing, boolean preserveGridSpacingExactly,
				boolean aseisReducesArea) {
			throw new UnsupportedOperationException();
		}

		@Override
		public FaultSection clone() {
			throw new UnsupportedOperationException();
		}
		
	}
	
	private static void addWeighted(double[] running, double[] vals, double weight) {
		Preconditions.checkState(running.length == vals.length);
		for (int i=0; i<running.length; i++)
			running[i] += vals[i]*weight;
	}
	
	private static Options createOptions() {
		Options ops = new Options();
		
		// TODO add documentation
		
		ops.addRequiredOption("if", "input-file", true, "Input solution logic tree zip file.");
		ops.addRequiredOption("of", "output-file", true, "Output branch averaged solution file.");
		ops.addOption("rt", "restrict-tree", true, "Restrict the logic tree to the given value. Specify values by either "
				+ "their short name or file prefix. If such a name is ambiguous (applies to multiple branch levels), "
				+ "excplicitly set the level as <level-short-name>=<value>. Repeat this argument to specify multiple "
				+ "values (within a single level and/or across multiple levels).");
		
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, BranchAverageSolutionCreator.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		SolutionLogicTree slt = SolutionLogicTree.load(inputFile);
		LogicTree<?> tree = slt.getLogicTree();
		
		BranchAverageSolutionCreator ba = new BranchAverageSolutionCreator(tree.getWeightProvider());
		
//		List<LogicTreeNode> restrictTo = new ArrayList<>();
		List<List<LogicTreeNode>> restrictTos = null;
		
		String[] restrictNames = cmd.getOptionValues("restrict-tree");
		if (restrictNames != null && restrictNames.length > 0) {
			restrictTos = new ArrayList<>();
			for (int i=0; i<tree.getLevels().size(); i++)
				restrictTos.add(new ArrayList<>());
			for (String op : restrictNames) {
				String valName = op;
				String levelName = null;
				if (op.contains("=")) {
					levelName = op.substring(0, op.indexOf('=')).trim();
					valName = op.substring(op.indexOf('=')+1).trim();
					System.out.println("Looking for logic tree level with name '"+levelName+"', value of '"+valName+"'");
				} else {
					System.out.println("Looking for logic tree value of '"+valName+"'");
				}
				LogicTreeNode match = null;
				for (int i=0; i<tree.getLevels().size(); i++) {
					LogicTreeLevel<?> level = tree.getLevels().get(i);
					if (levelName != null && !level.getShortName().equals(levelName))
						continue;
					for (LogicTreeNode value : level.getNodes()) {
//						System.out.println("Testing value="+value+" with prefix="+value.getFilePrefix());
						if (value.getShortName().equals(valName) || value.getFilePrefix().equals(valName)) {
							if (match == null) {
								System.out.println("Found matching node: "+value);
								match = value;
								restrictTos.get(i).add(value);
							} else {
								throw new IllegalStateException("Logic tree value '"+valName+"' is ambiguous (matches "
										+ "multiple values across multiple levels). Specify the appropriate level as "
										+ "--restrict-tree <level-short-name>=<value>.");
							}
						}
					}
				}
				Preconditions.checkNotNull(match, "Didn't find matching logic tree value='%s' (level='%s')",
						valName, levelName);
			}
		}
		
		for (LogicTreeBranch<?> branch : slt.getLogicTree()) {
			if (restrictTos != null) {
				Preconditions.checkState(branch.size() == tree.getLevels().size());
				boolean hasAll = true;
				for (int i=0; i<branch.size(); i++) {
					List<LogicTreeNode> restrictTo = restrictTos.get(i);
					if (restrictTo.isEmpty())
						continue;
					boolean match = false;
					for (LogicTreeNode restrict : restrictTo)
						if (branch.hasValue(restrict))
							match = true;
					if (!match) {
						hasAll = false;
						break;
					}
				}
				if (!hasAll) {
					System.out.println("Skipping branch: "+branch);
					continue;
				}
			}
			FaultSystemSolution sol = slt.forBranch(branch);
			
			ba.addSolution(sol, branch);
		}
		
		System.out.println("Building final branch-averaged solution.");
		FaultSystemSolution baSol = ba.build();
		baSol.write(outputFile);
	}

}
