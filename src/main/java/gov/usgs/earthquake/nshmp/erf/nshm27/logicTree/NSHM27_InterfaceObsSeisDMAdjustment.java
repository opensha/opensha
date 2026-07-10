package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.text.DecimalFormat;

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
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_GridSourceBuilder;
import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceObsSeisDMAdjustment implements LogicTreeNode.FixedWeightNode {
	NONE("No Adjustment", "None", 1d),
	AVERAGE("Average Observed Seismicity Sub-Seis Reduction", "Avg-Sub-Seis", 1d),
	SECTION_SPECIFIC("Section-Specific Observed Seismicity", "Sect-Sub-Seis", 1d),
	EXTRAPOLATE("Match Extrapolated Observed Seismicity", "Extrapolate-Observed", 1d);
	
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
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}
	
	public SectSlipRates adjustSlipRates(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) throws IOException {
		final int numSections = rupSet.getNumSections();
		double[] inputFullSlipRates = new double[numSections];
		double[] inputReducedSlipRates = new double[numSections];
		double[] inputFullSlipSDs = new double[numSections];
		for (int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			// mm -> m
			inputFullSlipRates[s] = sect.getOrigAveSlipRate()*1e-3;
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
		
		// just go off of average mMin
		double[] sectMmins = NSHM27_GridSourceBuilder.getInterfaceSectMinMag(rupSet, branch);
		double[] moments = new double[numSections];
		
		System.out.println("Applying interface sub-seis ajustment="+this.name);
	
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
		
		SectSlipRates slips =  switch (this) {
		case AVERAGE: {
			if (avgReduction >= 1d) {
				System.err.println("WARNING: associated interface moment ("+(float)assocFaultMoment+") is less than "
						+ "associated gridded moment ("+(float)assocGridMoment+") for branch "+branch+", setting all slip rates to 0");
				yield SectSlipRates.precomputed(rupSet, new double[numSections], inputFullSlipSDs);
			}
			double[] reducedSlipRates = new double[numSections];
			for (int s=0; s<numSections; s++) {
				double orig = inputReducedSlipRates[s];
				reducedSlipRates[s] = orig * (1d-avgReduction);
				Preconditions.checkState(orig >= reducedSlipRates[s]);
			}
			System.out.println("Interface average seis reduction for branch "+branch+": "+avgReduction);
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
					reducedSlipRates[s] = inputReducedSlipRates[s] * (1d-reduction);
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
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, inputFullSlipSDs);
		}
		case EXTRAPOLATE: {
//			if (!"asdf".isBlank()) {
//				try {
//					throw new IllegalStateException"*****************\n******HERE******\n****************");
//				} catch (IllegalStateException e) {
//					e.printStackTrace();
//				}
//			}
			// figure out Mmax
			double mMax = NSHM27_InvConfigFactory.getIncludeRuptureMmax(rupSet, branch);
			Preconditions.checkState(mMax > 0d);
			
			// build a GR up to full Mmax
			IncrementalMagFreqDist refMFD = FaultSysTools.initEmptyMFD(NSHM27_GridSourceBuilder.OVERALL_MMIN, mMax);
			NSHM27_SeisRateModel rateModel = branch.requireValue(NSHM27_SeisRateModel.class);
			int mMaxIndex = refMFD.getClosestXIndex(mMax);
			IncrementalMagFreqDist totalGR = rateModel.build(seisReg, TectonicRegionType.SUBDUCTION_INTERFACE,
					refMFD, refMFD.getX(mMaxIndex));
			
			System.out.println("Building extrapolated DM with mMax="+(float)mMax);
			
			// figure out how much moment from the total GR is >= the section minimum mags (which can vary by sect)
			double sumAvailMoment = 0d;
			for (int s=0; s<rupSet.getNumSections(); s++) {
				double fractAssoc = assoc.getSectionFractInRegion(s);
				if (fractAssoc > 0d) {
					// what fraction of the total associated moment does this section represent
					double sectFract = fractAssoc * moments[s]/assocFaultMoment;
					int sectMminIndex = refMFD.getClosestXIndex(sectMmins[s]);
					for (int m=sectMminIndex; m<=mMaxIndex; m++)
						sumAvailMoment += sectFract*totalGR.getMomentRate(m);
				}
			}
			
			System.out.println("Total GR has "+totalGR.getTotalMomentRate());
			System.out.println("Supra-available moment is "+sumAvailMoment+" ("
					+new DecimalFormat("0.00%").format(sumAvailMoment/totalGR.getTotalMomentRate())+" of total GR)");
			
			double dmScale = sumAvailMoment / assocFaultMoment;
			System.out.println("DM scale = "+(float)sumAvailMoment+" / "+(float)assocFaultMoment+" = "+(float)dmScale);
			
			double[] reducedSlipRates = new double[numSections];
			double[] reducedSlipSDs = new double[numSections];
			for (int s=0; s<numSections; s++) {
				// this is with coupling already applied
				double inputFullSlipRate = inputFullSlipRates[s];
				double inputCoupledSlipRate = inputReducedSlipRates[s];
				double inputSlipRateSD = inputFullSlipSDs[s];
				
				FaultSection sect = rupSet.getFaultSectionData(s);
				Preconditions.checkState(sect instanceof GeoJSONFaultSection);
				GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
				// make sure this didn't get called multiple times; if it does, we'll be re-applying based on already reduced slip rates
				Preconditions.checkState(!geoSect.getProperties().containsKey(GEOJSON_INPUT_SLIP_RATE_PROP_NAME),
						"The extrapolation adjustment was already applied & baked into slip rates!");
				double coupling = sect.getCouplingCoeff();
				
				if (inputCoupledSlipRate == 0d) {
					// zero slip rate, keep it at zero
					reducedSlipRates[s] = 0d;
					reducedSlipSDs[s] = inputSlipRateSD;
					sect.setAveSlipRate(reducedSlipRates[s] * 1e3);
					sect.setSlipRateStdDev(reducedSlipSDs[s] * 1e3);
					geoSect.setProperty(GEOJSON_INPUT_SLIP_RATE_PROP_NAME, inputFullSlipRate*1e3);
					geoSect.setProperty(GEOJSON_INPUT_SLIP_SD_PROP_NAME, inputSlipRateSD*1e3);
				} else {
					double scaledCoupledSlip = inputCoupledSlipRate * dmScale;
					reducedSlipRates[s] = scaledCoupledSlip;
					
					// this is the reduced slip rate, but without coupling applied
					double withoutCouplingApplied = scaledCoupledSlip / coupling;
					
					// keep the SD as the fractional value of the original before coupling was applied
					// we always use un-reduced SD, and this is the value that would show up as the SD if our
					// DM-scaled slip rate had always been present
					
					// our DM slip rates std devs are all hardcoded, so use that
					double slipSD;
					if (NSHM27_InterfaceDeformationModels.isHardcodedFractionalStdDev())
						slipSD = NSHM27_InterfaceDeformationModels.HARDCODED_FRACTIONAL_STD_DEV*withoutCouplingApplied;
					else
						slipSD = NSHM27_InterfaceDeformationModels.DEFAULT_FRACT_SLIP_STD_DEV*withoutCouplingApplied;
					slipSD = Math.max(slipSD, NSHM27_InterfaceDeformationModels.STD_DEV_FLOOR*1e-3);
					reducedSlipSDs[s] = slipSD;
					
					// could reset the original fault section's slip rate in order to get apparent sub-seis reductions
					// coupled moment, but it blows up if as coupling goes to zero (infinite original rates)
//					double supraCoupledMoment = dmScale * moments[s];
//					double newFullSlipRate = withoutCouplingApplied * (supraCoupledMoment + impliedMoments[s]) / supraCoupledMoment;
//					sect.setAveSlipRate(newFullSlipRate * 1e3);
					
					// instead just set it to what we're using
					sect.setAveSlipRate(withoutCouplingApplied * 1e3);
					
					sect.setSlipRateStdDev(reducedSlipSDs[s] * 1e3);
					geoSect.setProperty(GEOJSON_INPUT_SLIP_RATE_PROP_NAME, inputFullSlipRate*1e3);
					geoSect.setProperty(GEOJSON_INPUT_SLIP_SD_PROP_NAME, inputSlipRateSD*1e3);
				}
			}
			yield SectSlipRates.precomputed(rupSet, reducedSlipRates, reducedSlipSDs);
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + this);
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
	
	public static final String GEOJSON_INPUT_SLIP_RATE_PROP_NAME = "InputSlipRate";
	public static final String GEOJSON_INPUT_SLIP_SD_PROP_NAME = "InputSlipRateStdDev";

}
