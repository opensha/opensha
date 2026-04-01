package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

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
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_GridSourceBuilder;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.InterfaceGridAssociations;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_SeisPDF_Loader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceObsSeisDMAdjustment implements LogicTreeNode {
	NONE("No Adjustment", "None", 1d),
	AVERAGE("Average Observed Seismicity", "Average", 1d),
	SECTION_SPECIFIC("Section-Specific Observed Seismicity", "Sect-Specific", 1d);
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM27_InterfaceObsSeisDMAdjustment(String name, String shortName, double weight) {
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
		NSHM27_InterfaceFaultModels fm = branch.requireValue(NSHM27_InterfaceFaultModels.class);
		NSHM27_SeismicityRegions seisReg = fm.getSeisReg();
		NSHM27_GridSourceBuilder.doPreGridBuildHook(rupSet, branch);
		GridSourceList gridList = NSHM27_GridSourceBuilder.buildInterfaceGridSourceList(rupSet, branch, seisReg);
		
		SectSlipRates origSlipRates = SectSlipRates.fromFaultSectData(rupSet);
		FaultGridAssociations assoc = rupSet.requireModule(FaultGridAssociations.class);
		
		// just go off of average mMin
		double[] sectMmins = NSHM27_GridSourceBuilder.getInterfaceSectMinMag(rupSet, branch);
		double[] moments = new double[sectMmins.length];
		double[] slipSDs = new double[sectMmins.length];
		
		System.out.println("Applying interface sub-seis ajustment="+this.name);
	
		double sectTotalMoment = 0d;
		double sectAssocTotalMoment = 0d;
		for (int s=0; s<sectMmins.length; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			moments[s] = sect.calcMomentRate(true);
			// grab the original slip std dev; the default SectSlipRates below will use the reduced version
			slipSDs[s] = sect.getOrigSlipRateStdDev()*1e-3; // mm -> m
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
		
		SectSlipRates slips =  switch (this) {
		case AVERAGE: {
			if (avgReduction >= 1d) {
				System.err.println("WARNING: associated interface moment ("+(float)assocFaultMoment+") is less than "
						+ "associated gridded moment (%s) for branch "+branch+", setting all slip rates to 0");
				yield SectSlipRates.precomputed(rupSet, new double[moments.length], slipSDs);
			}
			double[] reducedSlipRates = new double[moments.length];
			for (int i=0; i<reducedSlipRates.length; i++) {
				double orig = origSlipRates.getSlipRate(i);
				reducedSlipRates[i] = orig * (1d-avgReduction);
				Preconditions.checkState(orig >= reducedSlipRates[i]);
			}
			System.out.println("Interface average seis reduction for branch "+branch+": "+avgReduction);
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, slipSDs);
		}
		case SECTION_SPECIFIC: {
			MinMaxAveTracker rawTrack = new MinMaxAveTracker();
			MinMaxAveTracker finalTrack = new MinMaxAveTracker();
			double sectWtSum = 0d;
			double gridWtSum = 0d;
			int numAbove = 0;
			double[] reducedSlipRates = new double[moments.length];
			for (int s=0; s<impliedMoments.length; s++) {
				double reduction = impliedMoments[s] / moments[s];
				rawTrack.addValue(Math.min(1d, reduction));
				double assocFract = assoc.getSectionFractInRegion(s);
				// add in the average reduction for any un-assocated portion of the subsection
				reduction = assocFract*reduction + (1d-assocFract)*avgReduction;
				if (reduction >= 1) {
					reducedSlipRates[s] = 0d;
					numAbove++;
					reduction = 1d;
				} else {
					reducedSlipRates[s] = origSlipRates.getSlipRate(s) * (1d-reduction);
				}
				sectWtSum += reduction*moments[s];
				gridWtSum += reduction*impliedMoments[s];
				finalTrack.addValue(reduction);
			}
			System.out.println("\tWeighted final average reductions:\tsecWtd="+(float)(sectWtSum/sectTotalMoment)
					+"\tgridWtd="+(float)(gridWtSum/impliedAssocTotalMoment));
			System.out.println("Interface obs seis reductions for branch "+branch+":\n\traw="+rawTrack
					+"\n\tfinal="+finalTrack+" w/ avgReduction="+(float)avgReduction+" applied to unassociated; "
					+numAbove+"/"+moments.length+" fully reduced");
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, slipSDs);
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + this);
		};
		double modSectTotalMoment = 0d;
		double modSectAssocTotalMoment = 0d;
		for (int s=0; s<sectMmins.length; s++) {
			double moment = slips.calcMomentRate(s);
			modSectTotalMoment += moment;
			modSectAssocTotalMoment += moment * assoc.getSectionFractInRegion(s);
		}
		// note for the future: these will be < gridded total moment if clipping occurs (i.e., sections are fully reduced)
		// because you can't reduce beyond the original slip rate
		System.out.println("\tSection reduced moment:\tassoc="+(float)modSectAssocTotalMoment+"\ttot="+(float)modSectTotalMoment);
		System.out.println("\tTotal reductions:\tassoc="+(float)(sectAssocTotalMoment - modSectAssocTotalMoment)
				+"\ttot="+(float)(sectTotalMoment - modSectTotalMoment));
		rupSet.addModule(slips);
	}

}
