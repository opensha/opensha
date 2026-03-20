package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.NSHM26_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.InterfaceGridAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_SeisPDF_Loader;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_InterfaceObsSeisDMAdjustment implements LogicTreeNode {
	NONE("No Adjustment", "None", 1d),
	AVERAGE("Average Observed Seismicity", "Average", 1d),
	SECTION_SPECIFIC("Section-Specific Observed Seismicity", "Sect-Specific", 1d);
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM26_InterfaceObsSeisDMAdjustment(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}
	
	public void adjustSlipRates(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) throws IOException {
		if (this == NONE)
			return;
		NSHM26_InterfaceFaultModels fm = branch.requireValue(NSHM26_InterfaceFaultModels.class);
		NSHM26_SeismicityRegions seisReg = fm.getSeisReg();
		NSHM26_SeisRateModel rateModel = branch.requireValue(NSHM26_SeisRateModel.class);
		NSHM26_DeclusteringAlgorithms decluster = branch.requireValue(NSHM26_DeclusteringAlgorithms.class);
		NSHM26_SeisSmoothingAlgorithms smooth = branch.requireValue(NSHM26_SeisSmoothingAlgorithms.class);
		
		GriddedGeoDataSet pdf = NSHM26_SeisPDF_Loader.load2D(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, decluster, smooth);
		
		SectSlipRates origSlipRates = SectSlipRates.fromFaultSectData(rupSet);
		FaultGridAssociations assoc = rupSet.getModule(FaultGridAssociations.class);
		if (assoc == null) {
			assoc = new InterfaceGridAssociations(rupSet.getFaultSectionDataList(), pdf.getRegion());
			rupSet.addModule(assoc);
		} else {
			Preconditions.checkState(assoc.getRegion().equals(pdf.getRegion()));
		}
		int minNumSects = 1;
		if (branch.hasValue(NSHM26_InterfaceMinSubSects.class))
			minNumSects = branch.requireValue(NSHM26_InterfaceMinSubSects.class).getValue();
		
		// just go off of average mMin
		double[] sectMmins = NSHM26_GridSourceBuilder.getInterfaceSectMinMag(rupSet, branch);
		double[] moments = new double[sectMmins.length];
	
		for (int s=0; s<sectMmins.length; s++)
			moments[s] = rupSet.getFaultSectionData(s).calcMomentRate(true);
		double sectMmin = StatUtils.mean(sectMmins);
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(NSHM26_GridSourceBuilder.OVERALL_MMIN, sectMmin);
		
		IncrementalMagFreqDist seisMFD = rateModel.build(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE, refMFD,
				refMFD.getX(refMFD.getClosestXIndex(sectMmin-0.1)));
//		System.out.println("Seis MFD for interface Mmin="+mMin+":\n"+seisMFD);
		double[] impliedMoments = new double[rupSet.getNumSections()];
		for (int i=0; i<pdf.size(); i++) {
			double moSum = 0d;
			for (int j=0; j<seisMFD.size(); j++)
				moSum += MagUtils.magToMoment(seisMFD.getX(j))*seisMFD.getY(j)*pdf.get(i);
			Map<Integer, Double> mappings = assoc.getSectionFracsOnNode(i);
			for (int s : mappings.keySet())
				impliedMoments[s] += moSum*mappings.get(s);
		}
		
		SectSlipRates slips =  switch (this) {
		case AVERAGE: {
			double assocGridMoment = 0d;
			double assocFaultMoment = 0d;
			for (int s=0; s<moments.length; s++) {
				assocGridMoment += impliedMoments[s];
				assocFaultMoment += moments[s]*assoc.getSectionFractInRegion(s);
			}
			double reduction = assocGridMoment / assocFaultMoment;
			if (reduction >= 1d) {
				System.err.println("WARNING: associated interface moment ("+(float)assocFaultMoment+") is less than "
						+ "associated gridded moment (%s) for branch "+branch+", setting all slip rates to 0");
				yield SectSlipRates.precomputed(rupSet, new double[moments.length], origSlipRates.getSlipRateStdDevs());
			}
			double[] reducedSlipRates = new double[moments.length];
			for (int i=0; i<reducedSlipRates.length; i++)
				reducedSlipRates[i] = origSlipRates.getSlipRate(i) * (1d-reduction);
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, origSlipRates.getSlipRateStdDevs());
		}
		case SECTION_SPECIFIC: {
			MinMaxAveTracker track = new MinMaxAveTracker();
			int numAbove = 0;
			double[] reducedSlipRates = new double[moments.length];
			for (int i=0; i<impliedMoments.length; i++) {
				double reduction = impliedMoments[i] / moments[i];
				if (reduction >= 1) {
					reducedSlipRates[i] = 0d;
					numAbove++;
					reduction = 1d;
				} else {
					reducedSlipRates[i] = origSlipRates.getSlipRate(i) * (1d-reduction);
				}
				track.addValue(reduction);
			}
			System.out.println("Interface obs seis reductions for branch "+branch+": "+track+"; "+numAbove+" fully reduced");
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, origSlipRates.getSlipRateStdDevs());
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + this);
		};
		rupSet.addModule(slips);
	}

}
