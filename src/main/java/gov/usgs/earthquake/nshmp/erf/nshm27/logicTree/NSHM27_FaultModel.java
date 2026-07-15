package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_MapRegions;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateModel;

public interface NSHM27_FaultModel extends RupSetFaultModel, RupSetSubsectioningModel, LogicTreeNode.FixedWeightNode {
	
	public NSHM27_SeismicityRegions getSeismicityRegion();
	
	public TectonicRegionType getTectonicRegime();
	
	@Override
	public default void attachDefaultModules(FaultSystemRupSet rupSet) {
		NSHM27_SeismicityRegions seisReg = getSeismicityRegion();
		rupSet.addAvailableModule(() -> {
			return buildROI(seisReg);
		}, RegionsOfInterest.class);
		rupSet.addAvailableModule(() -> {
			return new ModelRegion(seisReg.load());
		}, ModelRegion.class);
		rupSet.addAvailableModule(() -> {
			return RupSetTectonicRegimes.constant(rupSet, getTectonicRegime());
		}, RupSetTectonicRegimes.class);
	}
	
	public static RegionsOfInterest buildROI(NSHM27_SeismicityRegions seisReg) throws IOException {
		TectonicRegionType[] trts = {TectonicRegionType.SUBDUCTION_INTERFACE,
				TectonicRegionType.SUBDUCTION_SLAB,TectonicRegionType.ACTIVE_SHALLOW};
		int size = (trts.length+1)*2;
		List<Region> trtRegions = new ArrayList<>(size);
		List<IncrementalMagFreqDist> trtMFDs = new ArrayList<>(size);
		List<TectonicRegionType> regionTRTs = new ArrayList<>(size);
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(5.01, 9.01);
		
		// full seismicity region
		Region fullReg = seisReg.load();
		// add for all TRTs
		trtRegions.add(fullReg);
		SummedMagFreqDist fullObsMFD = new SummedMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
		trtMFDs.add(fullObsMFD);
		regionTRTs.add(null);
		// add individually for each TRT
		for (TectonicRegionType trt : trts) {
			trtRegions.add(cloneForTRT(seisReg.load(), trt));
			double mMax = NSHM27_SeisRateModelBranch.getPlotMmax(trt);
			WeightedList<UncertainBoundedIncrMagFreqDist> weightedMFDs = new WeightedList<>();
			for (NSHM27_SeisClassificationMethod classification : NSHM27_SeisClassificationMethod.values()) {
				double weight = classification.getNodeWeight();
				if (weight == 0)
					continue;
				UncertainBoundedIncrMagFreqDist obsMFD = NSHM27_SeisRateModelBranch.loadRateModel(
						seisReg, classification, trt).getBounded(refMFD, mMax);
				weightedMFDs.add(obsMFD, weight);
			}
			UncertainBoundedIncrMagFreqDist obsMFD = SeismicityRateModel.averageUncert(weightedMFDs);
			fullObsMFD.addIncrementalMagFreqDist(obsMFD);
			trtMFDs.add(obsMFD);
			regionTRTs.add(trt);
		}
		
		Region mapRegion = NSHM27_MapRegions.valueOf(seisReg.name()).load();
		// add for all TRTs
		trtRegions.add(mapRegion);
		trtMFDs.add(null);
		regionTRTs.add(null);
		// add individually for each TRT
		for (TectonicRegionType trt : trts) {
			trtRegions.add(cloneForTRT(mapRegion, trt));
			// TODO remapped; would have to load average PDFs
			trtMFDs.add(null);
			regionTRTs.add(trt);
		}
		return new RegionsOfInterest(trtRegions, trtMFDs, regionTRTs);
	}
	
	private static Region cloneForTRT(Region reg, TectonicRegionType trt) {
		reg = reg.clone();
		reg.setName(reg.getName()+" ("+NSHM27_RegionLoader.getNameForTRT(trt)+")");
		return reg;
	}

}
