package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;

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
	private double[] avgSlips = null;
	private List<List<Double>> avgRakes = null;
	private List<List<Integer>> sectIndices = null;
	private List<DiscretizedFunc> rupMFDs = null;
	
	private FaultSystemRupSet refRupSet = null;
	private double[] avgSectAseis = null;
	private double[] avgSectCoupling = null;
	private double[] avgSectSlipRates = null;
	private double[] avgSectSlipRateStdDevs = null;
	private List<List<Double>> avgSectRakes = null;
	
	// related to gridded seismicity
	private GridSourceProvider refGridProv = null;
	private GriddedRegion gridReg = null;
	private Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = null;
	private Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = null;
	private List<IncrementalMagFreqDist> sectSubSeisMFDs = null;
	
//	private List<? extends LogicTreeBranch<?>> branches = getLogicTree().getBranches();
	
	private LogicTreeBranch<LogicTreeNode> combBranch = null;
	
	private List<Double> weights = new ArrayList<>();
	
	private PaleoseismicConstraintData paleoData = null;
	
	private NamedFaults namedFaults = null;
	
	private Map<LogicTreeNode, Integer> nodeCounts = new HashMap<>();
	
	public BranchAverageSolutionCreator() {
		
	}
	
	public void addSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
		double weight = branch.getBranchWeight();
		weights.add(weight);
		totWeight += weight;
		FaultSystemRupSet rupSet = sol.getRupSet();
		GridSourceProvider gridProv = sol.getGridSourceProvider();
		SubSeismoOnFaultMFDs ssMFDs = sol.getModule(SubSeismoOnFaultMFDs.class);
		
		if (avgRates == null) {
			// first time
			avgRates = new double[rupSet.getNumRuptures()];
			avgMags = new double[avgRates.length];
			avgAreas = new double[avgRates.length];
			avgLengths = new double[avgRates.length];
			avgRakes = new ArrayList<>();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				avgRakes.add(new ArrayList<>());
			
			refRupSet = rupSet;
			
			avgSectAseis = new double[rupSet.getNumSections()];
			avgSectSlipRates = new double[rupSet.getNumSections()];
			avgSectSlipRateStdDevs = new double[rupSet.getNumSections()];
			avgSectCoupling = new double[rupSet.getNumSections()];
			avgSectRakes = new ArrayList<>();
			for (int s=0; s<rupSet.getNumSections(); s++)
				avgSectRakes.add(new ArrayList<>());
			
			if (gridProv != null) {
				refGridProv = gridProv;
				gridReg = gridProv.getGriddedRegion();
				nodeSubSeisMFDs = new HashMap<>();
				nodeUnassociatedMFDs = new HashMap<>();
			}
			
			if (ssMFDs != null) {
				sectSubSeisMFDs = new ArrayList<>();
				for (int s=0; s<rupSet.getNumSections(); s++)
					sectSubSeisMFDs.add(null);
			}
			
			if (rupSet.hasModule(AveSlipModule.class))
				avgSlips = new double[avgRates.length];
			
			combBranch = (LogicTreeBranch<LogicTreeNode>)branch.copy();
			sectIndices = rupSet.getSectionIndicesForAllRups();
			rupMFDs = new ArrayList<>();
			for (int r=0; r<avgRates.length; r++)
				rupMFDs.add(new ArbitrarilyDiscretizedFunc());
			
			paleoData = rupSet.getModule(PaleoseismicConstraintData.class);
			
			namedFaults = rupSet.getModule(NamedFaults.class);
		} else {
			Preconditions.checkState(refRupSet.isEquivalentTo(rupSet), "Rupture sets are not equivalent");
			if (refGridProv != null)
				Preconditions.checkNotNull(gridProv, "Some solutions have grid source providers and others don't");
			
			if (paleoData != null) {
				// see if it's the same
				PaleoseismicConstraintData myPaleoData = rupSet.getModule(PaleoseismicConstraintData.class);
				if (myPaleoData != null) {
					boolean same = paleoConstraintsSame(paleoData.getPaleoRateConstraints(),
							myPaleoData.getPaleoRateConstraints());
					same = same && paleoConstraintsSame(paleoData.getPaleoSlipConstraints(),
							myPaleoData.getPaleoSlipConstraints());
					if (same && paleoData.getPaleoProbModel() != null)
						same = paleoData.getPaleoProbModel().getClass().equals(myPaleoData.getPaleoProbModel().getClass());
					if (same && paleoData.getPaleoSlipProbModel() != null)
						same = paleoData.getPaleoSlipProbModel().getClass().equals(myPaleoData.getPaleoSlipProbModel().getClass());
					if (!same)
						paleoData = null;
				} else {
					// not all branches have it
					paleoData = null;
				}
			}
		}
		
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeNode combVal = combBranch.getValue(i);
			LogicTreeNode branchVal = branch.getValue(i);
			if (combVal != null && !combVal.equals(branchVal))
				combBranch.clearValue(i);
			int prevCount = nodeCounts.containsKey(branchVal) ? nodeCounts.get(branchVal) : 0;
			nodeCounts.put(branchVal, prevCount+1);
		}
		
		AveSlipModule slipModule = rupSet.getModule(AveSlipModule.class);
		if (avgSlips != null)
			Preconditions.checkNotNull(slipModule);
		addWeighted(avgRates, sol.getRateForAllRups(), weight);
		for (int r=0; r<avgRates.length; r++) {
			double rate = sol.getRateForRup(r);
			double mag = rupSet.getMagForRup(r);
			DiscretizedFunc rupMFD = rupMFDs.get(r);
			double y = rate*weight;
			if (rupMFD.hasX(mag))
				y += rupMFD.getY(mag);
			rupMFD.set(mag, y);
			avgRakes.get(r).add(rupSet.getAveRakeForRup(r));
			
			if (avgSlips != null)
				avgSlips[r] += weight*slipModule.getAveSlip(r);
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
			avgSectRakes.get(s).add(sect.getAveRake());
		}
		
		if (gridProv != null) {
			Preconditions.checkNotNull(refGridProv, "Some solutions have grid source providers and others don't");
			for (int i=0; i<gridReg.getNodeCount(); i++) {
				addWeighted(nodeSubSeisMFDs, i, gridProv.getNodeSubSeisMFD(i), weight);
				addWeighted(nodeUnassociatedMFDs, i, gridProv.getNodeUnassociatedMFD(i), weight);
			}
		}
		if (ssMFDs == null) {
			Preconditions.checkState(sectSubSeisMFDs == null, "Some solutions have sub seismo MFDs and others don't");
		} else {
			Preconditions.checkNotNull(sectSubSeisMFDs, "Some solutions have sub seismo MFDs and others don't");
			for (int s=0; s<rupSet.getNumSections(); s++) {
				IncrementalMagFreqDist subSeisMFD = ssMFDs.get(s);
				Preconditions.checkNotNull(subSeisMFD);
				IncrementalMagFreqDist avgMFD = sectSubSeisMFDs.get(s);
				if (avgMFD == null) {
					avgMFD = new IncrementalMagFreqDist(subSeisMFD.getMinX(), subSeisMFD.getMaxX(), subSeisMFD.size());
					sectSubSeisMFDs.set(s, avgMFD);
				}
				addWeighted(avgMFD, subSeisMFD, weight);
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
			DiscretizedFunc rupMFD = rupMFDs.get(r);
			rupMFD.scale(1d/totWeight);
			Preconditions.checkState((float)rupMFD.calcSumOfY_Vals() == (float)avgRates[r]);
			rakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(avgRakes.get(r), weights));
			if (avgSlips != null)
				avgSlips[r] /= totWeight;
		}
		
		GridSourceProvider combGridProv = null;
		if (refGridProv != null) {
			double[] fractSS = new double[refGridProv.size()];
			double[] fractR = new double[fractSS.length];
			double[] fractN = new double[fractSS.length];
			for (int i=0; i<fractSS.length; i++) {
				IncrementalMagFreqDist subSeisMFD = nodeSubSeisMFDs.get(i);
				if (subSeisMFD != null)
					subSeisMFD.scale(1d/totWeight);
				IncrementalMagFreqDist nodeUnassociatedMFD = nodeUnassociatedMFDs.get(i);
				if (nodeUnassociatedMFD != null)
					nodeUnassociatedMFD.scale(1d/totWeight);
				fractSS[i] = refGridProv.getFracStrikeSlip(i);
				fractR[i] = refGridProv.getFracReverse(i);
				fractN[i] = refGridProv.getFracNormal(i);
			}
			
			
			combGridProv = new AbstractGridSourceProvider.Precomputed(refGridProv.getGriddedRegion(),
					nodeSubSeisMFDs, nodeUnassociatedMFDs, fractSS, fractN, fractR);
		}
		if (sectSubSeisMFDs != null)
			for (int s=0; s<sectSubSeisMFDs.size(); s++)
				sectSubSeisMFDs.get(s).scale(1d/totWeight);
		
		List<FaultSection> subSects = new ArrayList<>();
		for (int s=0; s<refRupSet.getNumSections(); s++) {
			FaultSection refSect = refRupSet.getFaultSectionData(s);
			
			avgSectAseis[s] /= totWeight;
			avgSectCoupling[s] /= totWeight;
			avgSectSlipRates[s] /= totWeight;
			avgSectSlipRateStdDevs[s] /= totWeight;
			double avgRake = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(avgSectRakes.get(s), weights));
			
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
			if (avgSlips == null)
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
			System.out.println("Combined logic tre branch: "+combBranch);
		}
		if (avgSlips != null)
			avgRupSet.addModule(AveSlipModule.precomputed(avgRupSet, avgSlips));
		if (paleoData != null)
			avgRupSet.addModule(paleoData);
		if (namedFaults != null)
			avgRupSet.addModule(namedFaults);
		
		FaultSystemSolution sol = new FaultSystemSolution(avgRupSet, avgRates);
		sol.addModule(combBranch);
		if (combGridProv != null)
			sol.setGridSourceProvider(combGridProv);
		if (sectSubSeisMFDs != null)
			sol.addModule(new SubSeismoOnFaultMFDs(sectSubSeisMFDs));
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
	
	private static boolean paleoConstraintsSame(List<? extends SectMappedUncertainDataConstraint> constr1,
			List<? extends SectMappedUncertainDataConstraint> constr2) {
		if ((constr1 == null) != (constr2 == null))
			return false;
		if (constr1 == null && constr2 == null)
			return true;
		if (constr1.size() != constr2.size())
			return false;
		for (int i=0; i<constr1.size(); i++) {
			SectMappedUncertainDataConstraint c1 = constr1.get(i);
			SectMappedUncertainDataConstraint c2 = constr2.get(i);
			if (c1.sectionIndex != c2.sectionIndex)
				return false;
			if ((float)c1.bestEstimate != (float)c2.bestEstimate)
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
	
	public static void addWeighted(Map<Integer, IncrementalMagFreqDist> mfdMap, int index,
			IncrementalMagFreqDist newMFD, double weight) {
		if (newMFD == null)
			// simple case
			return;
		IncrementalMagFreqDist runningMFD = mfdMap.get(index);
		if (runningMFD == null) {
			runningMFD = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
			mfdMap.put(index, runningMFD);
		}
		addWeighted(runningMFD, newMFD, weight);
	}
	
	public static void addWeighted(IncrementalMagFreqDist runningMFD,
			IncrementalMagFreqDist newMFD, double weight) {
		Preconditions.checkState(runningMFD.size() == newMFD.size(), "MFD sizes inconsistent");
		Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
		Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
		for (int i=0; i<runningMFD.size(); i++)
			runningMFD.add(i, newMFD.getY(i)*weight);
	}
	
	private static void addWeighted(double[] running, double[] vals, double weight) {
		Preconditions.checkState(running.length == vals.length);
		for (int i=0; i<running.length; i++)
			running[i] += vals[i]*weight;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
