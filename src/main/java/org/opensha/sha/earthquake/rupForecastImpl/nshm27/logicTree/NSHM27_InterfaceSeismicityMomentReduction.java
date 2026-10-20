package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.IOException;

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
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Controls how moment represented by observed interface seismicity is deducted from the
 * moment-balanced inversion. The extrapolated interface MFD model replaces, and therefore
 * bypasses, these reduction choices.
 */
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceSeismicityMomentReduction implements LogicTreeNode.FixedWeightNode {
	NONE("No Seismicity Moment Reduction", "No Reduction", 1d/3d),
	REGIONAL("Regional-Average Seismicity Moment Reduction", "Regional Reduction", 1d/3d),
	SECTION_SPECIFIC("Section-Specific Seismicity Moment Reduction", "Section Reduction", 1d/3d);
	
	// never allow a reduction below this fraction in order to prevent zero-slip inversions
	// (ignored by the extrapolated MFD model, which can be small but will never be zero anyway)
	public static double REDUCTION_FRACT_FLOOR = 0.01;
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM27_InterfaceSeismicityMomentReduction(String name, String shortName, double weight) {
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
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}
	
	public SectSlipRates adjustSlipRates(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) throws IOException {
		Preconditions.checkState(!NSHM27_InterfaceMFDModel.isExtrapolated(branch),
				"Extrapolated interface MFD slip-rate scaling must be applied by %s",
				NSHM27_InterfaceMFDModel.class.getSimpleName());
		final int numSections = rupSet.getNumSections();
		double[] inputReducedSlipRates = new double[numSections];
		double[] inputFullSlipSDs = new double[numSections];
		for (int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			// mm -> m
			inputReducedSlipRates[s] = sect.getReducedAveSlipRate()*1e-3;
			inputFullSlipSDs[s] = sect.getOrigSlipRateStdDev()*1e-3;
		}
		if (this == NONE)
			// use reduced slip rates but full slip SDs
			return SectSlipRates.precomputed(rupSet, inputReducedSlipRates, inputFullSlipSDs);
		NSHM27_InterfaceFaultModels fm = branch.requireValue(NSHM27_InterfaceFaultModels.class);
		NSHM27_SeismicityRegions seisReg = fm.getSeismicityRegion();
		NSHM27_GridSourceBuilder.doPreGridBuildHook(rupSet, branch);
		GridSourceList gridList = NSHM27_GridSourceBuilder.buildInterfaceGridSourceList(rupSet, branch, seisReg);
		
		FaultGridAssociations assoc = rupSet.requireModule(FaultGridAssociations.class);
		
		double[] moments = new double[numSections];
		
		System.out.println("Applying interface seismicity moment reduction="+this.name);
	
		double sectTotalMoment = 0d;
		double sectAssocTotalMoment = 0d;
		for (int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			moments[s] = sect.calcMomentRate(true);
			sectTotalMoment += moments[s];
			sectAssocTotalMoment += moments[s] * assoc.getSectionFractInRegion(s);
		}
		
//		System.out.println("Seis MFD for interface Mmin="+mMin+":\n"+seisMFD);
		double[] impliedMoments = new double[rupSet.getNumSections()];
		double impliedTotalMoment = 0d;
		double impliedAssocTotalMoment = 0d;
		for (int l=0; l<gridList.getNumLocations(); l++) {
			for (GriddedRupture gridRup : gridList.getRuptures(TectonicRegionType.SUBDUCTION_INTERFACE, l)) {
				double mo = MagUtils.magToMoment(gridRup.properties.magnitude) * gridRup.rate;
				impliedTotalMoment += mo;
				if (gridRup.associatedSections != null) {
					for (int i=0; i<gridRup.associatedSections.length; i++) {
						double assocMo = mo*gridRup.associatedSectionFracts[i];
						impliedMoments[gridRup.associatedSections[i]] += assocMo;
						impliedAssocTotalMoment += assocMo;
					}
				}
			}
		}
		
		System.out.println("\tSection moment:\tassoc="+(float)sectAssocTotalMoment+"\ttot="+(float)sectTotalMoment);
		System.out.println("\tGridded moment:\tassoc="+(float)impliedAssocTotalMoment+"\ttot="+(float)impliedTotalMoment);
		System.out.println("\tGridded fractions:\tassoc="+(float)(impliedAssocTotalMoment/sectAssocTotalMoment)
				+"\t"+(float)(impliedTotalMoment/sectTotalMoment));
		
		// calculate average reduction where associated
		double assocGridMoment = 0d;
		double assocFaultMoment = 0d;
		MinMaxAveTracker sectAssocTrack = new MinMaxAveTracker();
		for (int s=0; s<moments.length; s++) {
			assocGridMoment += impliedMoments[s];
			double assocFract = assoc.getSectionFractInRegion(s);
			sectAssocTrack.addValue(assocFract);
			assocFaultMoment += moments[s]*assocFract;
		}
		System.out.println("\tSection associations:\t"+sectAssocTrack);
		double avgReduction = assocGridMoment / assocFaultMoment;
		System.out.println("\tAverage associated reduction:\t"+(float)avgReduction);
		
		SectSlipRates slips = switch (this) {
		case REGIONAL: {
			double avgFactor = Math.max(REDUCTION_FRACT_FLOOR, 1d-avgReduction);
			if (avgReduction >= 1d)
				System.err.println("WARNING: associated interface moment ("+(float)assocFaultMoment+") was less than "
						+ "associated gridded moment ("+(float)assocGridMoment+") for branch "+branch);
			double[] reducedSlipRates = new double[numSections];
			for (int s=0; s<numSections; s++) {
				double orig = inputReducedSlipRates[s];
				reducedSlipRates[s] = orig * avgFactor;
				Preconditions.checkState(orig >= reducedSlipRates[s]);
			}
			System.out.println("Interface regional-average seismicity reduction for branch "+branch+": "
					+avgReduction+" (scale="+avgFactor+")");
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, inputFullSlipSDs);
		}
		case SECTION_SPECIFIC: {
			MinMaxAveTracker rawTrack = new MinMaxAveTracker();
			MinMaxAveTracker finalTrack = new MinMaxAveTracker();
			double sectWtSum = 0d;
			double gridWtSum = 0d;
			int numAbove = 0;
			double[] reducedSlipRates = new double[numSections];
			for (int s=0; s<numSections; s++) {
				double reduction = moments[s] > 0d ? impliedMoments[s] / moments[s] : 1d;
				rawTrack.addValue(Math.min(1d, reduction));
				double assocFract = assoc.getSectionFractInRegion(s);
				// add in the average reduction for any unassociated portion of the subsection
				reduction = assocFract*reduction + (1d-assocFract)*avgReduction;
				double factor = Math.max(REDUCTION_FRACT_FLOOR, 1d-reduction);
				if (reduction >= 1) {
					numAbove++;
					reduction = 1d;
				}
				reducedSlipRates[s] = inputReducedSlipRates[s] * factor;
				sectWtSum += reduction*moments[s];
				gridWtSum += reduction*impliedMoments[s];
				finalTrack.addValue(reduction);
			}
			System.out.println("\tWeighted final average reductions:\tsecWtd="+(float)(sectWtSum/sectTotalMoment)
					+"\tgridWtd="+(float)(gridWtSum/impliedAssocTotalMoment));
			System.out.println("Interface section-specific seismicity reductions for branch "+branch+":\n\traw="+rawTrack
					+"\n\tfinal="+finalTrack+" w/ avgReduction="+(float)avgReduction+" applied to unassociated; "
					+numAbove+"/"+moments.length+" fully reduced");
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, inputFullSlipSDs);
		}
		case NONE:
			throw new IllegalStateException("NONE returns before moment-reduction calculations");
		};
		double modSectTotalMoment = 0d;
		double modSectAssocTotalMoment = 0d;
		for (int s=0; s<numSections; s++) {
			double moment = slips.calcMomentRate(s);
			modSectTotalMoment += moment;
			modSectAssocTotalMoment += moment * assoc.getSectionFractInRegion(s);
		}
		// note for the future: these will be < gridded total moment if clipping occurs (i.e., sections are fully reduced)
		// because you can't reduce beyond the original slip rate
		System.out.println("\tSection reduced moment:\tassoc="+(float)modSectAssocTotalMoment+"\ttot="+(float)modSectTotalMoment);
		System.out.println("\tTotal reductions:\tassoc="+(float)(sectAssocTotalMoment - modSectAssocTotalMoment)
				+"\ttot="+(float)(sectTotalMoment - modSectTotalMoment));
		return slips;
	}
	
}
