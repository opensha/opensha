package scratch.UCERF3.analysis;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.data.xyz.XYZ_DataSetMath;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.InversionConfiguration;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.DeformationModelFileParser;
import scratch.UCERF3.utils.DeformationModelFileParser.DeformationSection;
import scratch.UCERF3.utils.FaultSectionDataWriter;
import scratch.UCERF3.utils.IDPairing;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.DeformationModelOffFaultMoRateData;
import scratch.UCERF3.utils.SmoothSeismicitySpatialPDF_Fetcher;
import scratch.UCERF3.utils.UCERF2_A_FaultMapper;
import scratch.UCERF3.utils.UCERF3_DataUtils;
import scratch.UCERF3.utils.FindEquivUCERF2_Ruptures.FindEquivUCERF2_FM3_Ruptures;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt1;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_MFDsCalc;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;

public class DeformationModelsCalc {
	
	public static void plotDDW_AndLowerSeisDepthDistributions(List<FaultSectionPrefData> subsectData, String plotTitle) {
		
		HistogramFunction origDepthsHist = new HistogramFunction(0.5, 70, 1.0);
		HistogramFunction reducedDDW_Hist = new HistogramFunction(0.5, 70, 1.0);
		
		ArrayList<String> largeValuesInfoLSD = new ArrayList<String>();
		ArrayList<String> largeValuesInfoDDW = new ArrayList<String>();
		
		double meanLSD=0;
		double meanDDW=0;
		double meanLowerMinusUpperSeisDepth=0;
		int num=0;
		
		double totWt=0;
		for(FaultSectionPrefData data : subsectData) {
			if(data.getAveLowerDepth()>25.0) {
//				System.out.println(data.toString());
				String info = data.getParentSectionName()+"\tLowSeisDep = "+Math.round(data.getAveLowerDepth());
				if(!largeValuesInfoLSD.contains(info)) largeValuesInfoLSD.add(info);
			}
			num+=1;
			meanLSD+= data.getAveLowerDepth();
			origDepthsHist.add(data.getAveLowerDepth(), 1.0);
			meanDDW += data.getReducedDownDipWidth();
			double wt = data.getReducedAveSlipRate();
			if(Double.isNaN(wt)) {
				System.out.println("NaN slip rate: "+data.getName());
				wt=0;
			}
			else {
				totWt += wt;
			}
			meanLowerMinusUpperSeisDepth += wt*(1.0-data.getAseismicSlipFactor())*(data.getAveLowerDepth()-data.getOrigAveUpperDepth());
			reducedDDW_Hist.add(data.getReducedDownDipWidth(), 1.0);
			if(data.getReducedDownDipWidth()>25.0) {
				String info = data.getParentSectionName()+"\tDownDipWidth = "+Math.round(data.getReducedDownDipWidth());
				if(!largeValuesInfoDDW.contains(info)) largeValuesInfoDDW.add(info);
			}
		}
		
		meanLSD /= num;
		meanDDW /= num;
//		meanLowerMinusUpperSeisDepth /= num;
		meanLowerMinusUpperSeisDepth /= totWt;
		
		System.out.println("meanLowerMinusUpperSeisDepth="+meanLowerMinusUpperSeisDepth);
		
		origDepthsHist.normalizeBySumOfY_Vals();
		origDepthsHist.setName("Distribution of Lower Seis. Depths; mean = "+Math.round(meanLSD));
		String infoLSW = "(among all fault subsections, and not influcenced by aseismicity)\n\nValues greater than 25km:\n\n";
		for(String info:largeValuesInfoLSD)
			infoLSW += "\t"+ info+"\n";
		origDepthsHist.setInfo(infoLSW);

		reducedDDW_Hist.normalizeBySumOfY_Vals();
		reducedDDW_Hist.setName("Distribution of Down-Dip Widths; mean = "+Math.round(meanDDW));
		String infoDDW = "(among all fault subsections, and reduced by aseismicity)\n\nValues greater than 25km:\n\n";
		for(String info:largeValuesInfoDDW)
			infoDDW += "\t"+ info+"\n";
		reducedDDW_Hist.setInfo(infoDDW);

		
		ArrayList<HistogramFunction> hists = new ArrayList<HistogramFunction>();
		hists.add(origDepthsHist);
		hists.add(reducedDDW_Hist);
		
//		ArrayList<PlotCurveCharacterstics> list = new ArrayList<PlotCurveCharacterstics>();
//		list.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
//		list.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		
		GraphWindow graph = new GraphWindow(hists, plotTitle); 
		graph.setX_AxisLabel("Depth or Width (km)");
		graph.setY_AxisLabel("Normalized Number");

		
	}
	
	/**
	 * This calculates the total moment rate for a given list of section data
	 * @param sectData
	 * @param creepReduced
	 * @return
	 */
	public static double calculateTotalMomentRate(List<FaultSectionPrefData> sectData, boolean creepReduced) {
		double totMoRate=0;
		for(FaultSectionPrefData data : sectData) {
			double moRate = data.calcMomentRate(creepReduced);
			if(!Double.isNaN(moRate))
				totMoRate += moRate;
		}
		return totMoRate;
	}
	
	
	/**
	 * This tests whether any part of the surface fall outside the polygon?  
	 * NOTE THAT THIS MAY ONLY USE THE POLYGONS IN THE FAULT DATABASE (NOT ADDED BUFFER); CONFIRM BEFORE USING.
	 * These cannot yet be subsections
	 * @param sectData
	 */
	public static void testFaultZonePolygons(FaultModels faultModel) {
		
//		ArrayList<FaultSectionPrefData> sectData =  FaultModels.FM3_1.fetchFaultSections();
//		sectData.addAll(FaultModels.FM3_2.fetchFaultSections());
		
		double sectLen = 7d;
		List<FaultSectionPrefData> faultData =  faultModel.fetchFaultSections();
		List<FaultSectionPrefData> sectData  = Lists.newArrayList();
		for (FaultSectionPrefData fault : faultData) {
			sectData.addAll(fault.getSubSectionsList(sectLen));
		}
		FaultPolyMgr polyMgr = FaultPolyMgr.create(faultModel, null, sectLen);
		
		ArrayList<String> nullNames = new ArrayList<String>();
		ArrayList<String> outsideZoneNames = new ArrayList<String>();
		ArrayList<String> goodZoneNames = new ArrayList<String>();

		for(FaultSectionPrefData data: sectData){
//			Region zone = data.getZonePolygon();
			Region zone = polyMgr.getPoly(data.getSectionId());
			
			if(zone == null) {
				if(!nullNames.contains(data.getSectionName()))
					nullNames.add(data.getSectionName());
			}
			else {
				LocationList surfLocs = data.getStirlingGriddedSurface(1.0, false, false).getEvenlyDiscritizedListOfLocsOnSurface();
				boolean good = true;
				for(Location loc : surfLocs) {
					if(!zone.contains(loc)) {
						double dist = zone.distanceToLocation(loc);
						if(dist>0.5) {
							if(!outsideZoneNames.contains(data.getSectionName()))
								outsideZoneNames.add(data.getSectionName()+"\t\tLoc that's outside:"+(float)loc.getLatitude()+"\t"+(float)loc.getLongitude());
							good = false;
							break;							
						}
					}
				}
				if(good == true) {
					if(!goodZoneNames.contains(data.getSectionName()))
						goodZoneNames.add(data.getSectionName());

				}
			}
		}
		
		System.out.println("\nThese sections have null fault zone polygons\n");
		for(String name : nullNames)
			System.out.println("\t"+name);
		
		System.out.println("\nThese sections have surface points outside the fault zone polygon\n");
		for(String name : outsideZoneNames)
			System.out.println("\t"+name);
		
		System.out.println("\nThese sections are good (have all surface points inside the fault zone polygon)\n");
		for(String name : goodZoneNames)
			System.out.println("\t"+name);
	}
	
	
	/**
	 * This computes the total moment rate on faults for the given deformation model
	 * @param fm
	 * @param dm
	 * @param creepReduced
	 * @return
	 */
	public static double calcFaultMoRateForDefModel(FaultModels fm, DeformationModels dm, boolean creepReduced) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		return calculateTotalMomentRate(defFetch.getSubSectionList(),true);
	}
	
	/**
	 * This computes the total moment rate on faults for the given deformation model
	 * @param fm
	 * @param dm
	 * @param creepReduced
	 * @return
	 */
	public static double calcTotalMoRateForDefModel(FaultModels fm, DeformationModels dm, boolean creepReduced) {
		return calcFaultMoRateForDefModel(fm,dm,creepReduced)+calcMoRateOffFaultsForDefModel(fm,dm);
	}

	
	
	/**
	 * this returns the total off-fault moment rate for the given deformation model
	 * @param fm
	 * @param dm
	 * @return
	 */
	public static double calcMoRateOffFaultsForDefModel(FaultModels fm, DeformationModels dm) {
		DeformationModelOffFaultMoRateData offFaultData = DeformationModelOffFaultMoRateData.getInstance();
		return offFaultData.getTotalOffFaultMomentRate(fm, dm);
	}

	
	
	/**
	 * 
	 * @param fm
	 * @param dm
	 * @param rateM5 - rate of events great than or equal to M5
	 * @return
	 */
	private static String getTableLineForMoRateAndMmaxDataForDefModels(FaultModels fm, DeformationModels dm, double rateM5) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		double moRate = calculateTotalMomentRate(defFetch.getSubSectionList(),true);
		System.out.println(fm.getName()+", "+dm.getName()+ " (reduced):\t"+(float)moRate);
		System.out.println(fm.getName()+", "+dm.getName()+ " (not reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),false));
		double moRateOffFaults = calcMoRateOffFaultsForDefModel(fm, dm);
		double totMoRate = moRate+moRateOffFaults;
		double fractOff = moRateOffFaults/totMoRate;
		GutenbergRichterMagFreqDist targetMFD = new GutenbergRichterMagFreqDist(0.0005,9.9995,10000);
		targetMFD.setAllButMagUpper(0.0005, totMoRate, rateM5*1e5, 1.0, true);
		
		// now get moment rate of new faults only
		ArrayList<String> getEquivUCERF2_SectionNames = FindEquivUCERF2_FM3_Ruptures.getAllSectionNames(fm);
		ArrayList<FaultSectionPrefData> newSectionData = new ArrayList<FaultSectionPrefData>();
		for(FaultSectionPrefData data:defFetch.getSubSectionList())
			if (!getEquivUCERF2_SectionNames.contains(data.getParentSectionName()))
				newSectionData.add(data);
		double newFaultMoRate = calculateTotalMomentRate(newSectionData,true);
		
		
		System.out.println("totMoRate="+(float)totMoRate+"\tgetTotalMomentRate()="+(float)targetMFD.getTotalMomentRate()+"\tMgt4rate="+(float)targetMFD.getCumRate(4.0005)+
				"\tupperMag="+targetMFD.getMagUpper()+"\tMgt8rate="+(float)targetMFD.getCumRate(8.0005));
		return fm+"\t"+dm+"\t"+(float)(moRate/1e19)+"\t"+(float)fractOff+"\t"+(float)(moRateOffFaults/1e19)+"\t"+(float)(totMoRate/1e19)+"\t"+
				(float)targetMFD.getMagUpper()+"\t"+(float)targetMFD.getCumRate(8.0005)+"\t"+(float)(1.0/targetMFD.getCumRate(8.0005))+
						"\t"+(float)(newFaultMoRate/1e19);
	}
	
	
	
	/**
	 * This computes the average moment rate inside the given region, where the average
	 * is over all fault and deformation models.  UCERF2 is done as well.
	 * 
	 */
	public static void writeAveMoRateOfParentSectionsInsideRegion(Region region) {
		
		ArrayList<Double> u3_MoRateInside = new ArrayList<Double>();
		ArrayList<String> u3_SectNameInside = new ArrayList<String>();
		FaultModels[] fm_array = FaultModels.values();
		DeformationModels[] dm_array = DeformationModels.values();
		DeformationModelFetcher defFetch;
		for(FaultModels fm:fm_array) {
			for(DeformationModels dm:dm_array) {
				if(fm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) > 0 && dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) > 0) {
					defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
					System.out.println(fm+"\t"+fm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED)+"\t"+dm+"\t"+dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED));
					for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
						StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
						double frcInside = surf.getFractionOfSurfaceInRegion(region);
						if(frcInside > 1e-4) {
							double moRate = frcInside*data.calcMomentRate(true)*fm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED)*dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED);
							String u3_name = data.getParentSectionName();
							if(!u3_SectNameInside.contains(u3_name)) {	// it's a new parent section
								u3_SectNameInside.add(u3_name);
								u3_MoRateInside.add(moRate) ;
							}
							else {
								int index = u3_SectNameInside.indexOf(u3_name);
								double newMoRate = u3_MoRateInside.get(index)+moRate;
								u3_MoRateInside.set(index, newMoRate);
							}
						}
					}
				}
			}
		}
		double total=0;
		System.out.println("\nUCERF3 section moment rates inside "+region.getName()+":");
		for(int i=0;i<u3_MoRateInside.size();i++) {
			System.out.println((float)u3_MoRateInside.get(i).doubleValue()+"\t"+u3_SectNameInside.get(i));
			total+= u3_MoRateInside.get(i);
		}
		System.out.println("UCERF3 total = "+(float)total);

		

		/** UCERF2 deformation model IDs:
		 * D2.1 = 82
		 * D2.2 = 83
		 * D2.3 = 84
		 * D2.4 = 85
		 * D2.5 = 86
		 * D2.6 = 87
		 */
		int[] u2_dm_array = {82,83,84,85,86,87};
		double[] u2_dm_wts_array = {0.5*0.5,0.5*0.2,0.5*0.3,0.5*0.5,0.5*0.2,0.5*0.3};
		ArrayList<Double> u2_MoRateInside = new ArrayList<Double>();
		ArrayList<String> u2_SectNameInside = new ArrayList<String>();
		for(int i=0; i<u2_dm_array.length;i++) {
			int dmID = u2_dm_array[i];
			for(FaultSectionPrefData data : DeformationModelFetcher.getAll_UCERF2Sections(false, dmID)) {
				StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
				double frcInside = surf.getFractionOfSurfaceInRegion(region);
				if(frcInside > 1e-4) {
					double moRate = frcInside*data.calcMomentRate(true)*u2_dm_wts_array[i];
					String u2_name = data.getSectionName();
					if(!u2_SectNameInside.contains(u2_name)) {	// it's a new parent section
						u2_SectNameInside.add(u2_name);
						u2_MoRateInside.add(moRate) ;
					}
					else {
						int index = u2_SectNameInside.indexOf(u2_name);
						double newMoRate = u2_MoRateInside.get(index)+moRate;
						u2_MoRateInside.set(index, newMoRate);
					}
				}
			}
		}
		total=0;
		System.out.println("\nUCERF2 section moment rates inside "+region.getName()+":");
		for(int i=0;i<u2_MoRateInside.size();i++) {
			System.out.println((float)u2_MoRateInside.get(i).doubleValue()+"\t"+u2_SectNameInside.get(i));
			total+= u2_MoRateInside.get(i);
		}
		System.out.println("UCERF2 total = "+(float)total);
	}
	
	
	
	/**
	 * This lists the parent fault sections near the site, ordered by a proxy hazard
	 * computed as the moment-rate of the section multiplied by the mean 1-second-period 
	 * SA from BA_2008_AttenRel with M=7, vs30=400, COMPONENT_GMRotI50, FLT_TYPE_UNKNOWN,
	 * and the distance for the given fault section.
	 * 
	 */
	public static void writeParentSectionsNearSite(Location loc, int maxNumSectionsToList) {
		
		// Make distance func for hazard proxy
		BA_2008_AttenRel ba2008 = new BA_2008_AttenRel(null);
		// set SA with period = 1.0
		ba2008.setIntensityMeasure(SA_Param.NAME);
		ba2008.getParameter(PeriodParam.NAME).setValue(new Double(1.0));
		double vs30	= 400;
		double mag = 7;
		String faultType = BA_2008_AttenRel.FLT_TYPE_UNKNOWN;
		Component component = Component.GMRotI50;
		
		ba2008.getParameter(Vs30_Param.NAME).setValue(vs30);
		ba2008.getParameter(MagParam.NAME).setValue(mag);
		ba2008.getParameter(FaultTypeParam.NAME).setValue(faultType);
		ba2008.getParameter(ComponentParam.NAME).setValue(component);
		
		EvenlyDiscretizedFunc imlVsDistFunc = new EvenlyDiscretizedFunc(0., 201., 202);
		for(int i=0;i<imlVsDistFunc.size();i++) {
			Double dist = new Double(imlVsDistFunc.getX(i));
			DistanceJBParameter distParm = (DistanceJBParameter) ba2008.getParameter(DistanceJBParameter.NAME);
			distParm.setValueIgnoreWarning(dist);
			imlVsDistFunc.set(i,Math.exp(ba2008.getMean()));
		}
		
		// I checked this against the attenuation relationship plotting app
//		for(Parameter param: ba2008.getMeanIndependentParams()) {
//			System.out.println(param.getName()+"\t"+param.getValue());
//		}
//		System.out.println(imlVsDistFunc);

		
		
		// UCERF3 Sections
		ArrayList<Double> u3_ParSectMoRate = new ArrayList<Double>();
		ArrayList<Double> u3_ParSectMinDist = new ArrayList<Double>();
		ArrayList<Double> u3_ParSectHazProxy = new ArrayList<Double>();
		ArrayList<String> u3_SectName = new ArrayList<String>();
		FaultModels[] fm_array = FaultModels.values();
		DeformationModels[] dm_array = DeformationModels.values();
		DeformationModelFetcher defFetch;
		for(FaultModels fm:fm_array) {
			for(DeformationModels dm:dm_array) {
				if(fm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) > 0 && dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) > 0) {
					defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
					for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
						StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
						double distJB = surf.getDistanceJB(loc);
						if(distJB <= 200) {
							double moRate = data.calcMomentRate(true)*fm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED)*dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED);
							String u3_name = data.getParentSectionName();
							if(!u3_SectName.contains(u3_name)) {	// it's a new parent section
								u3_SectName.add(u3_name);
								u3_ParSectMoRate.add(moRate);
								u3_ParSectMinDist.add(distJB);
							}
							else {
								int index = u3_SectName.indexOf(u3_name);
								double newMoRate = u3_ParSectMoRate.get(index)+moRate;
								u3_ParSectMoRate.set(index, newMoRate);
								if(distJB<u3_ParSectMinDist.get(index))	// replace distance if less
									u3_ParSectMinDist.set(index,distJB);
							}
						}
					}
				}
			}
		}
		
		// set the hazard proxy value
		for(int i=0;i<u3_ParSectMinDist.size();i++) {
			u3_ParSectHazProxy.add(u3_ParSectMoRate.get(i)*imlVsDistFunc.getInterpolatedY(u3_ParSectMinDist.get(i)));
		}
		// list of sorted indices
		List<Integer> sortedIndices = DataUtils.sortedIndices(u3_ParSectHazProxy,false);

		
		// TESTS:
//		System.out.println("NON SORTED:");
//		for(int i=0;i<u3_ParSectMinDist.size();i++) {
//			System.out.println(u3_ParSectMinDist.get(i)+"\t"+u3_ParSectMoRate.get(i)+"\t"+u3_ParSectHazProxy.get(i)+"\t"+u3_SectName.get(i));
//		}
//		System.out.println("SORTED:");
//		for(int index : sortedIndices) {
//			System.out.println(u3_ParSectMinDist.get(index)+"\t"+u3_ParSectMoRate.get(index)+"\t"+u3_ParSectHazProxy.get(index)+"\t"+u3_SectName.get(index));
//		}
		
		System.out.println("UCERF3:");
		System.out.println("minDistJB\tMoRate\thazProxy\tSectName");
		for(int i=0; i<maxNumSectionsToList;i++) {
			int index = sortedIndices.get(i);
			System.out.println(u3_ParSectMinDist.get(index)+"\t"+u3_ParSectMoRate.get(index)+"\t"+u3_ParSectHazProxy.get(index)+"\t"+u3_SectName.get(index));
		}

		

//		/** UCERF2 deformation model IDs:
//		 * D2.1 = 82
//		 * D2.2 = 83
//		 * D2.3 = 84
//		 * D2.4 = 85
//		 * D2.5 = 86
//		 * D2.6 = 87
//		 */
		int[] u2_dm_array = {82,83,84,85,86,87};
		double[] u2_dm_wts_array = {0.5*0.5,0.5*0.2,0.5*0.3,0.5*0.5,0.5*0.2,0.5*0.3};
		ArrayList<Double> u2_ParSectMoRate = new ArrayList<Double>();
		ArrayList<Double> u2_ParSectMinDist = new ArrayList<Double>();
		ArrayList<Double> u2_ParSectHazProxy = new ArrayList<Double>();
		ArrayList<String> u2_SectName = new ArrayList<String>();
		for(int i=0; i<u2_dm_array.length;i++) {
			int dmID = u2_dm_array[i];
			for(FaultSectionPrefData data : DeformationModelFetcher.getAll_UCERF2Sections(false, dmID)) {
				StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
				double distJB = surf.getDistanceJB(loc);
				if(distJB <= 200) {
					double moRate = data.calcMomentRate(true)*u2_dm_wts_array[i];
					String u2_name = data.getSectionName();
					if(!u2_SectName.contains(u2_name)) {	// it's a new parent section
						u2_SectName.add(u2_name);
						u2_ParSectMoRate.add(moRate);
						u2_ParSectMinDist.add(distJB);
					}
					else {
						int index = u2_SectName.indexOf(u2_name);
						double newMoRate = u2_ParSectMoRate.get(index)+moRate;
						u2_ParSectMoRate.set(index, newMoRate);
						if(distJB<u2_ParSectMinDist.get(index))	// replace distance if less
							u2_ParSectMinDist.set(index,distJB);
					}
				}
			}
		}
		
		
		for(int i=0;i<u2_ParSectMinDist.size();i++) {
			u2_ParSectHazProxy.add(u2_ParSectMoRate.get(i)*imlVsDistFunc.getInterpolatedY(u2_ParSectMinDist.get(i)));
		}
		List<Integer> sortedIndices2 = DataUtils.sortedIndices(u2_ParSectHazProxy,false);

		
//		System.out.println("NON SORTED:");
//		for(int i=0;i<u2_ParSectMinDist.size();i++) {
//			System.out.println(u2_ParSectMinDist.get(i)+"\t"+u2_ParSectMoRate.get(i)+"\t"+u2_ParSectHazProxy.get(i)+"\t"+u2_SectName.get(i));
//		}
//		System.out.println("SORTED:");
//		for(int index : sortedIndices2) {
//			System.out.println(u2_ParSectMinDist.get(index)+"\t"+u2_ParSectMoRate.get(index)+"\t"+u2_ParSectHazProxy.get(index)+"\t"+u2_SectName.get(index));
//		}
		
		System.out.println("UCERF2:");
		System.out.println("minDistJB\tMoRate\thazProxy\tSectName");
		for(int i=0; i<maxNumSectionsToList;i++) {
			int index = sortedIndices2.get(i);
			System.out.println(u2_ParSectMinDist.get(index)+"\t"+u2_ParSectMoRate.get(index)+"\t"+u2_ParSectHazProxy.get(index)+"\t"+u2_SectName.get(index));
		}

	}

	
	
	public static void writeMoRateOfParentSectionsForAllDefAndFaultModels() {
		
		// make a master list of fault sections
		ArrayList<String> fm3_sectionNamesList = new ArrayList<String>();
		ArrayList<Integer> fm3_sectionIDsList = new ArrayList<Integer>();
		
		// loop over fault model 3.1
		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(!fm3_sectionNamesList.contains(data.getParentSectionName())) {
				fm3_sectionNamesList.add(data.getParentSectionName());
				fm3_sectionIDsList.add(data.getParentSectionId());
			}
		}
		// add those from FM 3.2
		defFetch = new DeformationModelFetcher(FaultModels.FM3_2, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(!fm3_sectionNamesList.contains(data.getParentSectionName())) {
				fm3_sectionNamesList.add(data.getParentSectionName());
				fm3_sectionIDsList.add(data.getParentSectionId());
			}
		}
		boolean creepReduced = true;
		Hashtable<Integer,Double> moRateForFM3pt1_Zeng = getParentSectMoRateHashtable(FaultModels.FM3_1, DeformationModels.ZENGBB, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt1_NeoKinema = getParentSectMoRateHashtable(FaultModels.FM3_1, DeformationModels.NEOKINEMA, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt1_Geologic = getParentSectMoRateHashtable(FaultModels.FM3_1, DeformationModels.GEOLOGIC, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt1_ABM = getParentSectMoRateHashtable(FaultModels.FM3_1, DeformationModels.ABM, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt2_Zeng = getParentSectMoRateHashtable(FaultModels.FM3_2, DeformationModels.ZENGBB, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt2_NeoKinema = getParentSectMoRateHashtable(FaultModels.FM3_2, DeformationModels.NEOKINEMA, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt2_Geologic = getParentSectMoRateHashtable(FaultModels.FM3_2, DeformationModels.GEOLOGIC, creepReduced);
		Hashtable<Integer,Double> moRateForFM3pt2_ABM = getParentSectMoRateHashtable(FaultModels.FM3_2, DeformationModels.ABM, creepReduced);
		
		// these only map names that actually changed, not ones that didn't
		HashMap<String, String> u2nameFromU3NameMapFM3pt1 = null;
		HashMap<String, String> u2nameFromU3NameMapFM3pt2 = null;
		try {
			u2nameFromU3NameMapFM3pt1 = UCERF2_Section_MFDsCalc.loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_1);
			u2nameFromU3NameMapFM3pt2 = UCERF2_Section_MFDsCalc.loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<String> newFaultSectionsList = getListOfNewFaultSectionNames();
		
		/** UCERF2 deformation model IDs:
		 * D2.1 = 82
		 * D2.2 = 83
		 * D2.3 = 84
		 * D2.4 = 85
		 * D2.5 = 86
		 * D2.6 = 87
		 */
		HashMap<String,Double> moRateForDM2pt1 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82), creepReduced);
		HashMap<String,Double> moRateForDM2pt2 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 83), creepReduced);
		HashMap<String,Double> moRateForDM2pt3 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 84), creepReduced);
		HashMap<String,Double> moRateForDM2pt4 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85), creepReduced);
		HashMap<String,Double> moRateForDM2pt5 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 86), creepReduced);
		HashMap<String,Double> moRateForDM2pt6 = getMoRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 87), creepReduced);

		
		ArrayList<String> lines = new ArrayList<String>();
		String line = "sectName\tsectID\tFM3pt1_Zeng\tFM3pt1_NeoKinema\tFM3pt1_Geologic\tFM3pt1_ABM\tFM3pt2_Zeng\tFM3pt2_NeoKinema\tFM3pt2_Geologic\tFM3pt2_ABM";
		line += "\tu2_name\tDM2pt1\tDM2pt2\tDM2pt3\tDM2pt4\tDM2pt5\tDM2pt6";
		lines.add(line);
		for(int s=0; s<fm3_sectionNamesList.size();s++) {
			int id = fm3_sectionIDsList.get(s);
			String u3_name = fm3_sectionNamesList.get(s);
			line = u3_name;
			line += "\t"+id;
			line += "\t"+moRateForFM3pt1_Zeng.get(id);
			line += "\t"+moRateForFM3pt1_NeoKinema.get(id);
			line += "\t"+moRateForFM3pt1_Geologic.get(id);
			line += "\t"+moRateForFM3pt1_ABM.get(id);

			line += "\t"+moRateForFM3pt2_Zeng.get(id);
			line += "\t"+moRateForFM3pt2_NeoKinema.get(id);
			line += "\t"+moRateForFM3pt2_Geologic.get(id);
			line += "\t"+moRateForFM3pt2_ABM.get(id);
			
			String u2_name = u2nameFromU3NameMapFM3pt1.get(u3_name);
			if(u2_name == null)	// try again if that failed
				u2_name = u2nameFromU3NameMapFM3pt2.get(u3_name);
			if(u2_name == null)
				u2_name = u3_name;	// in case name didn't change
			if(newFaultSectionsList.contains(u2_name))	// set back to null if it's a new fault
				u2_name = null;
			else if(u2_name.equals("Green Valley 2011 CFM"))	// this is the one special case where previous sections were combined
				u2_name = null;
 // System.out.println(u3_name+"\t"+u2_name);
			
			line += "\t"+u2_name;
			line += "\t"+moRateForDM2pt1.get(u2_name);
			line += "\t"+moRateForDM2pt2.get(u2_name);
			line += "\t"+moRateForDM2pt3.get(u2_name);
			line += "\t"+moRateForDM2pt4.get(u2_name);
			line += "\t"+moRateForDM2pt5.get(u2_name);
			line += "\t"+moRateForDM2pt6.get(u2_name);
			
			lines.add(line);
		}

//		for(String str:u2nameFromU3NameMapFM3pt1.keySet()) {
//			System.out.println(str+"\t\t\t"+u2nameFromU3NameMapFM3pt1.get(str));
//		}
		
		File dataFile = new File("dev/scratch/UCERF3/data/scratch/FaultSectionMomentRates.txt");
		try {
			FileWriter fw = new FileWriter(dataFile);
			for(String str:lines) {
//			for(String str:moRateForDM2pt1.keySet()) {
				fw.write(str+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}

	}
	
	
	
	public static void writeAveSlipRateEtcOfParentSectionsForAllDefAndFaultModels() {
		
		// make a master list of fault sections
		ArrayList<String> fm3_sectionNamesList = new ArrayList<String>();
		ArrayList<Integer> fm3_sectionIDsList = new ArrayList<Integer>();
		
		// loop over fault model 3.1
		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(!fm3_sectionNamesList.contains(data.getParentSectionName())) {
				fm3_sectionNamesList.add(data.getParentSectionName());
				fm3_sectionIDsList.add(data.getParentSectionId());
			}
		}
		// add those from FM 3.2
		defFetch = new DeformationModelFetcher(FaultModels.FM3_2, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(!fm3_sectionNamesList.contains(data.getParentSectionName())) {
				fm3_sectionNamesList.add(data.getParentSectionName());
				fm3_sectionIDsList.add(data.getParentSectionId());
			}
		}
		
		double[] fm3_sectionLengthList = new double[fm3_sectionNamesList.size()];	// km
		double[] fm3_sectionOrigAreaList = new double[fm3_sectionNamesList.size()];	// km-sq
		boolean[] sectionDone = new boolean[fm3_sectionNamesList.size()];
		
		// loop over fault model 3.1
		defFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(int s=0; s<fm3_sectionIDsList.size();s++) {
			sectionDone[s]=false;
			int parSectID = fm3_sectionIDsList.get(s);
			for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
				if(data.getParentSectionId() == parSectID) {
					fm3_sectionLengthList[s] += data.getTraceLength();
					fm3_sectionOrigAreaList[s] += data.getOrigDownDipWidth()*data.getTraceLength();
					sectionDone[s]=true;
				}
			}
		}
		
		// add those from FM 3.2
		defFetch = new DeformationModelFetcher(FaultModels.FM3_2, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(int s=0; s<fm3_sectionIDsList.size();s++) {
			if(sectionDone[s])
				continue;
			int parSectID = fm3_sectionIDsList.get(s);
			for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
				if(data.getParentSectionId() == parSectID) {
					fm3_sectionLengthList[s] += data.getTraceLength();
					fm3_sectionOrigAreaList[s] += data.getOrigDownDipWidth()*data.getTraceLength();
				}
			}
		}
		
		boolean creepReduced = false;
		Hashtable<Integer,Double> slipRateForFM3pt1_Zeng = getParentSectAveSlipRateHashtable(FaultModels.FM3_1, DeformationModels.ZENGBB, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt1_NeoKinema = getParentSectAveSlipRateHashtable(FaultModels.FM3_1, DeformationModels.NEOKINEMA, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt1_Geologic = getParentSectAveSlipRateHashtable(FaultModels.FM3_1, DeformationModels.GEOLOGIC, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt1_ABM = getParentSectAveSlipRateHashtable(FaultModels.FM3_1, DeformationModels.ABM, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt2_Zeng = getParentSectAveSlipRateHashtable(FaultModels.FM3_2, DeformationModels.ZENGBB, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt2_NeoKinema = getParentSectAveSlipRateHashtable(FaultModels.FM3_2, DeformationModels.NEOKINEMA, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt2_Geologic = getParentSectAveSlipRateHashtable(FaultModels.FM3_2, DeformationModels.GEOLOGIC, creepReduced);
		Hashtable<Integer,Double> slipRateForFM3pt2_ABM = getParentSectAveSlipRateHashtable(FaultModels.FM3_2, DeformationModels.ABM, creepReduced);

		// asies
		Hashtable<Integer,Double> aseisForFM3pt1_Zeng = getParentSectAveAseisHashtable(FaultModels.FM3_1, DeformationModels.ZENGBB);
		Hashtable<Integer,Double> aseisForFM3pt1_NeoKinema = getParentSectAveAseisHashtable(FaultModels.FM3_1, DeformationModels.NEOKINEMA);
		Hashtable<Integer,Double> aseisForFM3pt1_Geologic = getParentSectAveAseisHashtable(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		Hashtable<Integer,Double> aseisForFM3pt1_ABM = getParentSectAveAseisHashtable(FaultModels.FM3_1, DeformationModels.ABM);
		Hashtable<Integer,Double> aseisForFM3pt2_Zeng = getParentSectAveAseisHashtable(FaultModels.FM3_2, DeformationModels.ZENGBB);
		Hashtable<Integer,Double> aseisForFM3pt2_NeoKinema = getParentSectAveAseisHashtable(FaultModels.FM3_2, DeformationModels.NEOKINEMA);
		Hashtable<Integer,Double> aseisForFM3pt2_Geologic = getParentSectAveAseisHashtable(FaultModels.FM3_2, DeformationModels.GEOLOGIC);
		Hashtable<Integer,Double> aseisForFM3pt2_ABM = getParentSectAveAseisHashtable(FaultModels.FM3_2, DeformationModels.ABM);
		
		// coupCoeff
		Hashtable<Integer,Double> ccForFM3pt1_Zeng = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_1, DeformationModels.ZENGBB);
		Hashtable<Integer,Double> ccForFM3pt1_NeoKinema = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_1, DeformationModels.NEOKINEMA);
		Hashtable<Integer,Double> ccForFM3pt1_Geologic = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		Hashtable<Integer,Double> ccForFM3pt1_ABM = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_1, DeformationModels.ABM);
		Hashtable<Integer,Double> ccForFM3pt2_Zeng = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_2, DeformationModels.ZENGBB);
		Hashtable<Integer,Double> ccForFM3pt2_NeoKinema = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_2, DeformationModels.NEOKINEMA);
		Hashtable<Integer,Double> ccForFM3pt2_Geologic = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_2, DeformationModels.GEOLOGIC);
		Hashtable<Integer,Double> ccForFM3pt2_ABM = getParentSectAveCouplingCoeffHashtable(FaultModels.FM3_2, DeformationModels.ABM);

		// these only map names that actually changed, not ones that didn't
		HashMap<String, String> u2nameFromU3NameMapFM3pt1 = null;
		HashMap<String, String> u2nameFromU3NameMapFM3pt2 = null;
		try {
			u2nameFromU3NameMapFM3pt1 = UCERF2_Section_MFDsCalc.loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_1);
			u2nameFromU3NameMapFM3pt2 = UCERF2_Section_MFDsCalc.loadUCERF3toUCER2NameMappingFile(FaultModels.FM3_2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<String> newFaultSectionsList = getListOfNewFaultSectionNames();
		
		/** UCERF2 deformation model IDs:
		 * D2.1 = 82
		 * D2.2 = 83
		 * D2.3 = 84
		 * D2.4 = 85
		 * D2.5 = 86
		 * D2.6 = 87
		 */
		HashMap<String,Double> slipRateForDM2pt1 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82));
		HashMap<String,Double> slipRateForDM2pt2 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 83));
		HashMap<String,Double> slipRateForDM2pt3 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 84));
		HashMap<String,Double> slipRateForDM2pt4 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85));
		HashMap<String,Double> slipRateForDM2pt5 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 86));
		HashMap<String,Double> slipRateForDM2pt6 = getOrigSlipRateHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 87));
		
		// the following differ only by fault model, so computing for DM2.1 and DM2.4
		HashMap<String,Double> lengthForDM2pt1 = getLengthHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82));
		HashMap<String,Double> lengthForDM2pt4 = getLengthHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85));

		// the following differ only by fault model, so computing for DM2.1 and DM2.4
		HashMap<String,Double> origAreaForDM2pt1 = getOrigAreaHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82));
		HashMap<String,Double> origAreaForDM2pt4 = getOrigAreaHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85));

		// the following differ only by fault model (I checked), so computing for DM2.1 and DM2.4
		HashMap<String,Double> aseisForDM2pt1 = getAseisHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82));
		HashMap<String,Double> aseisForDM2pt4 = getAseisHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85));

		// the following differ only by fault model (I checked), so computing for DM2.1 and DM2.4
		HashMap<String,Double> ccForDM2pt1 = getCouplingCoeffHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 82));
		HashMap<String,Double> ccForDM2pt4 = getCouplingCoeffHashtable(DeformationModelFetcher.getAll_UCERF2Sections(false, 85));

		
		ArrayList<String> lines = new ArrayList<String>();
		String line = "sectName\tsectID\tLength\torigArea";
		line += "\tFM3pt1_Zeng\tFM3pt1_NeoKinema\tFM3pt1_Geologic\tFM3pt1_ABM\tFM3pt2_Zeng\tFM3pt2_NeoKinema\tFM3pt2_Geologic\tFM3pt2_ABM";
		line += "\tFM3pt1_Zeng_aseis\tFM3pt1_NeoKinema_aseis\tFM3pt1_Geologic_aseis\tFM3pt1_ABM_aseis\tFM3pt2_Zeng_aseis\tFM3pt2_NeoKinema_aseis\tFM3pt2_Geologic_aseis\tFM3pt2_ABM_aseis";
		line += "\tFM3pt1_Zeng_cc\tFM3pt1_NeoKinema_cc\tFM3pt1_Geologic_cc\tFM3pt1_ABM_cc\tFM3pt2_Zeng_cc\tFM3pt2_NeoKinema_cc\tFM3pt2_Geologic_cc\tFM3pt2_ABM_cc";
		line += "\tu2_name\tlength\torigArea\tDM2pt1\tDM2pt2\tDM2pt3\tDM2pt4\tDM2pt5\tDM2pt6\tAseis\tcoupCoeff";
		lines.add(line);
		for(int s=0; s<fm3_sectionNamesList.size();s++) {
			int id = fm3_sectionIDsList.get(s);
			double areaTimesShearMod = fm3_sectionOrigAreaList[s]*1e6*FaultMomentCalc.SHEAR_MODULUS;	// 1e6 converts area to m-sq
			String u3_name = fm3_sectionNamesList.get(s);
			line = u3_name;
			line += "\t"+id;
			line += "\t"+fm3_sectionLengthList[s];
			line += "\t"+fm3_sectionOrigAreaList[s];
			// slip rates
			line += "\t"+slipRateForFM3pt1_Zeng.get(id);	
			line += "\t"+slipRateForFM3pt1_NeoKinema.get(id);
			line += "\t"+slipRateForFM3pt1_Geologic.get(id);
			line += "\t"+slipRateForFM3pt1_ABM.get(id);
			line += "\t"+slipRateForFM3pt2_Zeng.get(id);
			line += "\t"+slipRateForFM3pt2_NeoKinema.get(id);
			line += "\t"+slipRateForFM3pt2_Geologic.get(id);
			line += "\t"+slipRateForFM3pt2_ABM.get(id);
			// aseis
			line += "\t"+aseisForFM3pt1_Zeng.get(id);	
			line += "\t"+aseisForFM3pt1_NeoKinema.get(id);
			line += "\t"+aseisForFM3pt1_Geologic.get(id);
			line += "\t"+aseisForFM3pt1_ABM.get(id);
			line += "\t"+aseisForFM3pt2_Zeng.get(id);
			line += "\t"+aseisForFM3pt2_NeoKinema.get(id);
			line += "\t"+aseisForFM3pt2_Geologic.get(id);
			line += "\t"+aseisForFM3pt2_ABM.get(id);
			// coup coeff
			line += "\t"+ccForFM3pt1_Zeng.get(id);	
			line += "\t"+ccForFM3pt1_NeoKinema.get(id);
			line += "\t"+ccForFM3pt1_Geologic.get(id);
			line += "\t"+ccForFM3pt1_ABM.get(id);
			line += "\t"+ccForFM3pt2_Zeng.get(id);
			line += "\t"+ccForFM3pt2_NeoKinema.get(id);
			line += "\t"+ccForFM3pt2_Geologic.get(id);
			line += "\t"+ccForFM3pt2_ABM.get(id);
			
			String u2_name = u2nameFromU3NameMapFM3pt1.get(u3_name);
			if(u2_name == null)	// try again if that failed
				u2_name = u2nameFromU3NameMapFM3pt2.get(u3_name);
			if(u2_name == null)
				u2_name = u3_name;	// in case name didn't change
			if(newFaultSectionsList.contains(u2_name))	// set back to null if it's a new fault
				u2_name = null;
			else if(u2_name.equals("Green Valley 2011 CFM"))	// this is the one special case where previous sections were combined
				u2_name = null;
 // System.out.println(u3_name+"\t"+u2_name);
			
			line += "\t"+u2_name;
			
			// area
			Double u2_length = lengthForDM2pt1.get(u2_name);
			if(u2_length == null)
				u2_length = lengthForDM2pt4.get(u2_name);
			line += "\t"+u2_length;
			
			// area
			Double u2_area = origAreaForDM2pt1.get(u2_name);
			if(u2_area == null)
				u2_area = origAreaForDM2pt4.get(u2_name);
			line += "\t"+u2_area;

			line += "\t"+slipRateForDM2pt1.get(u2_name);
			line += "\t"+slipRateForDM2pt2.get(u2_name);
			line += "\t"+slipRateForDM2pt3.get(u2_name);
			line += "\t"+slipRateForDM2pt4.get(u2_name);
			line += "\t"+slipRateForDM2pt5.get(u2_name);
			line += "\t"+slipRateForDM2pt6.get(u2_name);
			
			Double u2_aseis = aseisForDM2pt1.get(u2_name);
			if(u2_aseis == null)
				u2_aseis = aseisForDM2pt4.get(u2_name);
			line += "\t"+u2_aseis;

			Double u2_cc = ccForDM2pt1.get(u2_name);
			if(u2_cc == null)
				u2_cc = ccForDM2pt4.get(u2_name);
			line += "\t"+u2_cc;

	
//			line += "\t"+aseisForDM2pt1.get(u2_name);
//			line += "\t"+aseisForDM2pt2.get(u2_name);
//			line += "\t"+aseisForDM2pt3.get(u2_name);
//			line += "\t"+aseisForDM2pt4.get(u2_name);
//			line += "\t"+aseisForDM2pt5.get(u2_name);
//			line += "\t"+aseisForDM2pt6.get(u2_name);
			
//			line += "\t"+ccForDM2pt1.get(u2_name);
//			line += "\t"+ccForDM2pt2.get(u2_name);
//			line += "\t"+ccForDM2pt3.get(u2_name);
//			line += "\t"+ccForDM2pt4.get(u2_name);
//			line += "\t"+ccForDM2pt5.get(u2_name);
//			line += "\t"+ccForDM2pt6.get(u2_name);
			
			lines.add(line);
		}

//		for(String str:u2nameFromU3NameMapFM3pt1.keySet()) {
//			System.out.println(str+"\t\t\t"+u2nameFromU3NameMapFM3pt1.get(str));
//		}
		
		File dataFile = new File("dev/scratch/UCERF3/data/scratch/ParentFaultSectionSlipRatesEtc.txt");
		try {
			FileWriter fw = new FileWriter(dataFile);
			for(String str:lines) {
//			for(String str:moRateForDM2pt1.keySet()) {
				fw.write(str+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}

	}

	
	
	private static HashMap<String,Double> getMoRateHashtable(ArrayList<FaultSectionPrefData> faultSectDataList, boolean creepReduced) {
		
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.calcMomentRate(creepReduced));
		}
		
		return hashtable;
	}
	
	
	private static HashMap<String,Double> getOrigSlipRateHashtable(ArrayList<FaultSectionPrefData> faultSectDataList) {
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.getOrigAveSlipRate());
		}
		return hashtable;
	}

	private static HashMap<String,Double> getAseisHashtable(ArrayList<FaultSectionPrefData> faultSectDataList) {
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.getAseismicSlipFactor());
		}
		return hashtable;
	}
	
	private static HashMap<String,Double> getCouplingCoeffHashtable(ArrayList<FaultSectionPrefData> faultSectDataList) {
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.getCouplingCoeff());
		}
		return hashtable;
	}

	/**
	 * Areas are in km-sq
	 * @param faultSectDataList
	 * @return
	 */
	private static HashMap<String,Double> getOrigAreaHashtable(ArrayList<FaultSectionPrefData> faultSectDataList) {
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.getOrigDownDipWidth()*data.getTraceLength());
		}
		return hashtable;
	}

	/**
	 * Areas are in km
	 * @param faultSectDataList
	 * @return
	 */
	private static HashMap<String,Double> getLengthHashtable(ArrayList<FaultSectionPrefData> faultSectDataList) {
		HashMap<String,Double> hashtable = new HashMap<String,Double>();
		for(FaultSectionPrefData data:faultSectDataList) {
			hashtable.put(data.getName(), data.getTraceLength());
		}
		return hashtable;
	}


	private static Hashtable<Integer,Double> getParentSectMoRateHashtable(FaultModels fm, DeformationModels dm, boolean creepReduced) {
		
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();

		String lastName = "";
		Integer lastID = -100;
		double moRate=0;

		for(FaultSectionPrefData data:defFetch.getSubSectionList()) {
			if(data.getParentSectionName().equals(lastName)) {
				moRate += data.calcMomentRate(creepReduced);
			}
			else {
				if(!lastName.equals("")) {
					hashtable.put(lastID, moRate);
				}
				// set first values for new parent section
				moRate=data.calcMomentRate(creepReduced);
				lastName = data.getParentSectionName();
				lastID = data.getParentSectionId();
			}
		}
		// do the last one
		hashtable.put(lastID, moRate);

		return hashtable;
	}
	
	
	/**
	 * This computes a wt-averaged aseismicity from subsection values (wted by subsection area) for each parent section.  
	 * This is the same as the un-wted ave to the extent subsections have the same area.
	 * @param fm
	 * @param dm
	 * @return
	 */
	private static Hashtable<Integer,Double> getParentSectAveAseisHashtable(FaultModels fm, DeformationModels dm) {
		
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();

		String lastName = "";
		Integer lastID = -100;
		double totOrigArea=0;
		double totReducedArea=0;

		for(FaultSectionPrefData data:defFetch.getSubSectionList()) {
			if(data.getParentSectionName().equals(lastName)) {
				totOrigArea += data.getOrigDownDipWidth()*data.getTraceLength();
				totReducedArea += data.getReducedDownDipWidth()*data.getTraceLength();
			}
			else {
				if(!lastName.equals("")) {
					hashtable.put(lastID, 1.0-totReducedArea/totOrigArea);
				}
				// set first values for new parent section
				totOrigArea = data.getOrigDownDipWidth()*data.getTraceLength();
				totReducedArea = data.getReducedDownDipWidth()*data.getTraceLength();
				lastName = data.getParentSectionName();
				lastID = data.getParentSectionId();
			}
		}
		// do the last one
		hashtable.put(lastID, 1.0-totReducedArea/totOrigArea);

		return hashtable;
	}
	
	
	/**
	 * This computes a wt-averaged coupling coefficient from subsection values (wted by subsection area) for each parent section.  
	 * This is the same as the un-wted ave to the extent subsections have the same area.
	 * @param fm
	 * @param dm
	 * @return
	 */
	private static Hashtable<Integer,Double> getParentSectAveCouplingCoeffHashtable(FaultModels fm, DeformationModels dm) {
		
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();

		String lastName = "";
		Integer lastID = -100;
		double totOrigArea=0;
		double aveCC =0;

		for(FaultSectionPrefData data:defFetch.getSubSectionList()) {
			if(data.getParentSectionName().equals(lastName)) {
				double area = data.getOrigDownDipWidth()*data.getTraceLength();
				totOrigArea += area;
				aveCC += data.getCouplingCoeff()*area;
			}
			else {
				if(!lastName.equals("")) {
					hashtable.put(lastID, aveCC/totOrigArea);
				}
				// set first values for new parent section
				double area = data.getOrigDownDipWidth()*data.getTraceLength();
				totOrigArea = area;
				aveCC = data.getCouplingCoeff()*area;
				lastName = data.getParentSectionName();
				lastID = data.getParentSectionId();
			}
		}
		// do the last one
		hashtable.put(lastID, aveCC/totOrigArea);

		return hashtable;
	}

	
	
	/**
	 * This is an alt way of doing it (compute from slip-rate aves rather than ave of CCs).
	 * @param fm
	 * @param dm
	 * @return
	 */
	private static Hashtable<Integer,Double> getParentSectAveCouplingCoeffHashtableAlt(FaultModels fm, DeformationModels dm) {
		
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();
		Hashtable<Integer,Double> hashtableOrigSlipRate = getParentSectAveSlipRateHashtable(fm, dm, false);
		Hashtable<Integer,Double> hashtableReducedSlipRate = getParentSectAveSlipRateHashtable(fm, dm, true);
		for(int key:hashtableOrigSlipRate.keySet()) {
			hashtable.put(key, hashtableReducedSlipRate.get(key)/hashtableOrigSlipRate.get(key));
		}

		return hashtable;
	}



	/**
	 * This computes a wt-averaged slip rate from subsection values (wted by subsection area) for each parent section.  
	 * This is the same as the un-wted ave to the extent subsections have the same area.  The creep-reduced case does no
	 * utilize reduced areas in the wted ave because this would screw things up (reduced slip rate can be greater than
	 * original).
	 * @param fm
	 * @param dm
	 * @return
	 */
	private static Hashtable<Integer,Double> getParentSectAveSlipRateHashtable(FaultModels fm, DeformationModels dm, boolean creepReduced) {
		
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();

		String lastName = "";
		Integer lastID = -100;
		double sumOrig=0;
		double sumReduced=0;
		double origAreaSum=0;
//		double reducedAreaSum=0;

		for(FaultSectionPrefData data:defFetch.getSubSectionList()) {
			if(data.getParentSectionName().equals(lastName)) {
				double origArea = data.getOrigDownDipWidth()*data.getTraceLength();
				double reducedArea = data.getReducedDownDipWidth()*data.getTraceLength();
				origAreaSum+=origArea;
//				reducedAreaSum+=reducedArea;
				sumOrig += data.getOrigAveSlipRate()*origArea;
				sumReduced += data.getReducedAveSlipRate()*reducedArea;
			}
			else {
				if(!lastName.equals("")) {
					if(creepReduced)
						hashtable.put(lastID, sumReduced/origAreaSum);
					else
						hashtable.put(lastID, sumOrig/origAreaSum);
				}
				// set first values for new parent section
				double origArea = data.getOrigDownDipWidth()*data.getTraceLength();
				double reducedArea = data.getReducedDownDipWidth()*data.getTraceLength();
				origAreaSum=origArea;
//				reducedAreaSum=reducedArea;
				sumOrig = data.getOrigAveSlipRate()*origArea;
				sumReduced = data.getReducedAveSlipRate()*reducedArea;
				lastName = data.getParentSectionName();
				lastID = data.getParentSectionId();
			}
		}
		// do the last one
		if(creepReduced)
			hashtable.put(lastID, sumReduced/origAreaSum);
		else
			hashtable.put(lastID, sumOrig/origAreaSum);

		return hashtable;
	}
	
	
	
	/**
	 * This is test to see why the parent section 651 have a section ave coupling coefficient that exceeds 1.0
	 * @param fm
	 * @param dm
	 */
	private static void testGetParentSectAveSlipRateHashtable(FaultModels fm, DeformationModels dm) {
		
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		Hashtable<Integer,Double> hashtable = new Hashtable<Integer,Double>();

		String lastName = "";
		Integer lastID = -100;
		double sumOrig=0;
		double sumReduced=0;
		double origAreaSum=0;
		double reducedAreaSum=0;

		for(FaultSectionPrefData data:defFetch.getSubSectionList()) {
			if(data.getParentSectionId()==651) {
				if(data.getParentSectionName().equals(lastName)) {
					double origArea = data.getOrigDownDipWidth()*data.getTraceLength();
					double reducedArea = data.getReducedDownDipWidth()*data.getTraceLength();
					origAreaSum+=origArea;
					reducedAreaSum+=reducedArea;
					sumOrig += data.getOrigAveSlipRate()*origArea;
					sumReduced += data.getReducedAveSlipRate()*reducedArea;
					System.out.println(origArea+"\t"+reducedArea+"\t"+data.getOrigAveSlipRate()+"\t"+data.getReducedAveSlipRate()+"\t"+sumOrig+"\t"+sumReduced+"\t"+lastName);
				}
				else {
					// set first values for new parent section
					double origArea = data.getOrigDownDipWidth()*data.getTraceLength();
					double reducedArea = data.getReducedDownDipWidth()*data.getTraceLength();
					origAreaSum=origArea;
					reducedAreaSum=reducedArea;
					sumOrig = data.getOrigAveSlipRate()*origArea;
					sumReduced = data.getReducedAveSlipRate()*reducedArea;
					lastName = data.getParentSectionName();
					lastID = data.getParentSectionId();
					System.out.println(origArea+"\t"+reducedArea+"\t"+data.getOrigAveSlipRate()+"\t"+data.getReducedAveSlipRate()+"\t"+
							origAreaSum+"\t"+reducedAreaSum+"\t"+sumOrig+"\t"+sumReduced+"\t"+lastName);
				}
				
			}
		}
		System.out.println(sumReduced/reducedAreaSum);
		System.out.println(sumOrig/origAreaSum);
		
	}



	
	
	public static void writeMoRateOfParentSections(FaultModels fm, DeformationModels dm) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		// get list of sections in UCERF2
		ArrayList<String> getEquivUCERF2_SectionNames = FindEquivUCERF2_FM3_Ruptures.getAllSectionNames(fm);

		String lastName = "";
		double moRateReduced=0, moRateNotReduced=0;
		System.out.println("Sect Name\t"+"moRateReduced\tmoRateNotReduced\tIn UCERF2?");

		for(FaultSectionPrefData data:defFetch.getSubSectionList())
			if(data.getParentSectionName().equals(lastName)) {
				moRateReduced += data.calcMomentRate(true);
				moRateNotReduced += data.calcMomentRate(false);
			}
			else {
				if(!lastName.equals("")) {
					System.out.println(lastName+"\t"+(float)moRateReduced+"\t"+(float)moRateNotReduced+"\t"+getEquivUCERF2_SectionNames.contains(lastName));
				}
				// set first values for new parent section
				moRateReduced=data.calcMomentRate(true);
				moRateNotReduced=data.calcMomentRate(false);
				lastName = data.getParentSectionName();
			}

		System.out.println(lastName+"\t"+(float)moRateReduced+"\t"+(float)moRateNotReduced+"\t"+getEquivUCERF2_SectionNames.contains(lastName));

	}
	
	
	public static void writeAveReducedSlipRateEtcOfParentSections(FaultModels fm, DeformationModels dm) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		String lastName = "";
		double slipRate=0;
		double aseisFact=0;
		double reducedDDW=0;
		int numSubSect =0;

		System.out.println("Sect Name\tSlipRate\tAsiesFactor");
		for(FaultSectionPrefData data:defFetch.getSubSectionList())
			if(data.getParentSectionName().equals(lastName)) {
				slipRate += data.getReducedAveSlipRate();
				aseisFact += data.getAseismicSlipFactor();
				reducedDDW += data.getReducedDownDipWidth();
				numSubSect += 1;
			}
			else {
				if(!lastName.equals("")) {
					System.out.println(lastName+"\t"+(float)(slipRate/(double)numSubSect)+"\t"+(float)(aseisFact/(double)numSubSect)+
							"\t"+(float)(reducedDDW/(double)numSubSect));
				}
				// set first values for new parent section
				slipRate = data.getReducedAveSlipRate();
				lastName = data.getParentSectionName();
				aseisFact = data.getAseismicSlipFactor();
				reducedDDW = data.getReducedDownDipWidth();
				numSubSect = 1;
			}
	}
	
	

	
	
	public static void writeSubSectDataForParent(String parentSectionName, FaultModels fm, DeformationModels dm) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		System.out.println("Sect Name\tSlipRate\tAveDip\tAsiesFactor\tOrigUpperDepth+\tLowerDepth\tOrigDDW\tReducedDDW\tCouplingCoeff");
		for(FaultSectionPrefData data:defFetch.getSubSectionList())
			if(data.getParentSectionName().equals(parentSectionName)) {
				System.out.println(data.getName()+"\t"+(float)data.getReducedAveSlipRate()+"\t"+(float)data.getAveDip()+
						"\t"+(float)data.getAseismicSlipFactor()+"\t"+(float)data.getOrigAveUpperDepth()+
						"\t"+(float)data.getAveLowerDepth()+"\t"+(float)data.getOrigDownDipWidth()+
						"\t"+(float)data.getReducedDownDipWidth()+"\t"+data.getCouplingCoeff());
			}
	}


	
	public static void writeParentSectionsInsideRegion(FaultModels fm, DeformationModels dm, Region region) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		String lastName = "";
		System.out.println(region.getName()+"\nSect Name\tFractionInside\tMoRateInside");
		double numInside=0, totNum=0, moRateInside=0;

		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(data.getParentSectionName().equals(lastName)) {
				StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
				double frcInside = surf.getFractionOfSurfaceInRegion(region);
				int numPts = surf.getNumCols()*surf.getNumRows();
				numInside += frcInside*numPts;
				totNum += numPts;
				moRateInside += frcInside*data.calcMomentRate(true);
			}
			else {
				if(!lastName.equals("") && totNum >0) {
					double frac = (numInside/totNum);
					if(frac > 0.3)
						System.out.println(lastName+"\t"+(float)frac+"\t"+(float)moRateInside);
				}
				StirlingGriddedSurface surf = data.getStirlingGriddedSurface(1.0);
				double frcInside = surf.getFractionOfSurfaceInRegion(region);
				int numPts = surf.getNumCols()*surf.getNumRows();
				numInside = frcInside*numPts;
				totNum = numPts;
				moRateInside = frcInside*data.calcMomentRate(true);
				lastName = data.getParentSectionName();
			}
		}
	}

	
	
	public static void calcMoRateAndMmaxDataForDefModels() {
		
		double rateM5 = TotalMag5Rate.RATE_7p9.getRateMag5();
		
		ArrayList<String> tableData= new ArrayList<String>();
		FaultModels fm = FaultModels.FM3_1;
		
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.ABM,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.GEOLOGIC,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.NEOKINEMA,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.ZENGBB,rateM5));

		fm = FaultModels.FM3_2;
		
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.ABM,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.GEOLOGIC,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.NEOKINEMA,rateM5));
		tableData.add(getTableLineForMoRateAndMmaxDataForDefModels(fm,DeformationModels.ZENGBB,rateM5));

		System.out.println("\nfltMod\tdefMod\tfltMoRate\tfractOff\tmoRateOff\ttotMoRate\tMmax\tRate_gtM8\tMRIgtM8\tnewFltMoRate");
		for(String tableLine : tableData)
			System.out.println(tableLine);
				
	}
	
	
	
	/**
	 * This gets the names of sections that are new to UCERF3 (either the section was added or it now
	 * has a slip rate from the Geologic deformation model, as UCERF2 ignored sections with no slip rate)
	 */
	public static ArrayList<String> getListOfNewFaultSectionNames() {
		
		// get section name from FM 3.1
		ArrayList<String> fm3_sectionNamesList = new ArrayList<String>();
		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList())
			if(!fm3_sectionNamesList.contains(data.getParentSectionName()))
				fm3_sectionNamesList.add(data.getParentSectionName());
		// add those from FM 3.2
		defFetch = new DeformationModelFetcher(FaultModels.FM3_2, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList())
			if(!fm3_sectionNamesList.contains(data.getParentSectionName()))
				fm3_sectionNamesList.add(data.getParentSectionName());
		
		// Get those that existed in UCERF2
		ArrayList<String> equivUCERF2_SectionNames = FindEquivUCERF2_FM3_Ruptures.getAllSectionNames(FaultModels.FM3_1);
		ArrayList<String> equivUCERF2_SectionNamesTemp = FindEquivUCERF2_FM3_Ruptures.getAllSectionNames(FaultModels.FM3_2);
		for(String name: equivUCERF2_SectionNamesTemp)
			if(!equivUCERF2_SectionNames.contains(name))
				equivUCERF2_SectionNames.add(name);
		
		// now make the list of new names
		ArrayList<String> newSectionName = new ArrayList<String>();
		for(String name : fm3_sectionNamesList)
			if (!equivUCERF2_SectionNames.contains(name))
				newSectionName.add(name);
		
		for(String name: newSectionName)
			System.out.println(name);
		System.out.println("There are "+newSectionName.size()+" new sections listed above");
		
		return newSectionName;


	}
	
	public static void plotNewFaultSectionsInGMT() {
		
		ArrayList<String> newParSectionNames = getListOfNewFaultSectionNames();
		
		List<LocationList> faults = Lists.newArrayList();
		List<Double> valsList = Lists.newArrayList();
		
		// get section name from FM 3.1
		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(newParSectionNames.contains(data.getParentSectionName())) { // check that it's a new section
				faults.add(data.getFaultTrace());
				valsList.add(data.getReducedAveSlipRate());
			}
		}
		// this will plot any duplicate over each other
		defFetch = new DeformationModelFetcher(FaultModels.FM3_2, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(newParSectionNames.contains(data.getParentSectionName())) { 
				faults.add(data.getFaultTrace());
				valsList.add(data.getReducedAveSlipRate());
			}
		}
		
		double[] values = Doubles.toArray(valsList);
		
		Region region = new CaliforniaRegions.RELM_TESTING();
		File saveDir = GMT_CA_Maps.GMT_DIR;
		boolean display = true;

		
		try {
			FaultBasedMapGen.makeFaultPlot(FaultBasedMapGen.getSlipRateCPT(), faults, values, region, saveDir, "NewFaultsOnly", display, false, "Slip Rate (mm/yr)");
		} catch (GMT_MapException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
	/**
	 * This plots a historgram of fractional reductions due to creep,
	 * and also calculates some things (these latter things ignore the 
	 * creeping section for some reason).
	 * @param fm
	 * @param dm
	 */
	public static void plotMoRateReductionHist(FaultModels fm, DeformationModels dm) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		HistogramFunction moRateReductionHist = new HistogramFunction(0d, 51, 0.02);

		double totNoReduction=0, totWithReductionNotRedeced=0, totWithReductionRedeced=0;
		double straightAve=0;
		ArrayList<String> parantNamesOfReduced = new ArrayList<String>();
		int numReduced=0, numNotReduced=0;
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			double ratio = data.calcMomentRate(true)/data.calcMomentRate(false);
			if(!Double.isNaN(ratio)) {
				moRateReductionHist.add(ratio, 1.0);
				if(moRateReductionHist.getClosestXIndex(ratio) == moRateReductionHist.size()-1) {	// no reduction
					totNoReduction += data.calcMomentRate(false);
					numNotReduced += 1;
				}
				else if (!data.getParentSectionName().equals("San Andreas (Creeping Section) 2011 CFM")) {
					totWithReductionNotRedeced += data.calcMomentRate(false);
					totWithReductionRedeced += data.calcMomentRate(true);
					straightAve += data.calcMomentRate(true)/data.calcMomentRate(false);
					numReduced+=1;
					if(!parantNamesOfReduced.contains(data.getParentSectionName()))
						parantNamesOfReduced.add(data.getParentSectionName());
				}
			}
		}
		straightAve /= numReduced;
		double percReduced = 100d*(double)numReduced/(double)(numNotReduced+numReduced);
		double aveRatio = totWithReductionRedeced/totWithReductionNotRedeced;
		System.out.println(numReduced+" out of "+(numReduced+numNotReduced)+" subsections were reduced; ("+(float)percReduced+")");
		System.out.println("totNoReduction="+(float)totNoReduction);
		System.out.println("totWithReductionNotRedeced="+(float)totWithReductionNotRedeced);
		System.out.println("aveRatio="+(float)aveRatio);
		System.out.println("straightAve="+(float)straightAve);
		System.out.println("potential further reduction ((1.0-aveRatio)*totNoReduction)"+(float)((1.0-aveRatio)*totNoReduction));
		
		GraphWindow graph = new GraphWindow(moRateReductionHist, "Moment Rate Reduction Histogram"); 
		graph.setX_AxisLabel("Fractional Reduction (due to creep)");
		graph.setY_AxisLabel("Number of Fault Sub-Sections");
		
		System.out.println("Parent Names of those reduced");
		for(String name: parantNamesOfReduced)
			System.out.println("\t"+name);


		
	}
	
	
	
	private static void makeSpatialMoRateMaps(FaultModels fm, DeformationModels dm, GriddedGeoDataSet refForRatioData) {
		DeformationModelOffFaultMoRateData spatPDFgen = DeformationModelOffFaultMoRateData.getInstance();
		
		GriddedGeoDataSet offFaultData = spatPDFgen.getDefModSpatialOffFaultMoRates(fm, dm);
		GriddedGeoDataSet onFaultData = getDefModFaultMoRatesInRELM_Region(fm, dm);
		GriddedGeoDataSet totalMoRateData = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		for(int i=0; i<totalMoRateData.size();i++)
			totalMoRateData.set(i, offFaultData.get(i)+onFaultData.get(i));

		System.out.println(dm+"\tmaxMoRate="+totalMoRateData.getMaxZ());
		System.out.println(dm+"\tminMoRate="+offFaultData.getMinZ());

		
		try {
			GMT_CA_Maps.plotSpatialMoRate_Map(offFaultData, dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_OffFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(onFaultData, dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_OnFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(totalMoRateData.copy(), dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_TotalMoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(totalMoRateData, refForRatioData, dm+" Ratio", " " , dm.getShortName()+"_RatioMap");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * This returns the average on-fault moment rates for the given fault model, where the average
	 * is taken over: ABM, NEOKINEMA, ZENG, and optionally GEOLOGIC..
	 * @param fm
	 */
	public static GriddedGeoDataSet getAveDefModSpatialOnFaultMomentRateData(FaultModels fm, boolean includeGeologic) {

		ArrayList<DeformationModels> dmListForAve = new ArrayList<DeformationModels>();
		if(includeGeologic) {
			dmListForAve.add(DeformationModels.GEOLOGIC);
		}
		dmListForAve.add(DeformationModels.ABM);
		dmListForAve.add(DeformationModels.NEOKINEMA);
		dmListForAve.add(DeformationModels.ZENGBB);

		GriddedGeoDataSet aveDefModOnFault = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		for(int i=0;i<aveDefModOnFault.size();i++) {// initialize to zero
			aveDefModOnFault.set(i,0);
		}
		for(DeformationModels dm : dmListForAve) {
			GriddedGeoDataSet onFaultData = getDefModFaultMoRatesInRELM_Region(fm, dm);
			dm.getRelativeWeight(null);
			
			for(int i=0;i<onFaultData.size();i++) {
				if(!Double.isNaN(onFaultData.get(i)))	// treat the Geo NaNs as zero
					aveDefModOnFault.set(i, aveDefModOnFault.get(i) + onFaultData.get(i)/dmListForAve.size());
			}
		}
		return aveDefModOnFault;
	}
	
	
	/**
	 * This returns the wt averaged on-fault moment rates for the given fault model, where the weights are from the logic tree.
	 * @param fm
	 */
	public static GriddedGeoDataSet getWtAveDefModSpatialOnFaultMomentRateData() {

		ArrayList<DeformationModels> dmListForAve = new ArrayList<DeformationModels>();
		dmListForAve.add(DeformationModels.GEOLOGIC);
		dmListForAve.add(DeformationModels.ABM);
		dmListForAve.add(DeformationModels.NEOKINEMA);
		dmListForAve.add(DeformationModels.ZENGBB);
		
		// test weights
		for(DeformationModels dm : dmListForAve) {
			System.out.println(dm+" wt="+dm.getRelativeWeight(null));
		}

		GriddedGeoDataSet aveDefModOnFault = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		for(int i=0;i<aveDefModOnFault.size();i++) {// initialize to zero
			aveDefModOnFault.set(i,0);
		}
		for(DeformationModels dm : dmListForAve) {
			// do first fault model
			GriddedGeoDataSet onFaultData = getDefModFaultMoRatesInRELM_Region(FaultModels.FM3_1, dm);
			for(int i=0;i<onFaultData.size();i++) {
				if(!Double.isNaN(onFaultData.get(i)))	// treat the Geo NaNs as zero
					aveDefModOnFault.set(i, aveDefModOnFault.get(i) + onFaultData.get(i)*dm.getRelativeWeight(null)*0.5);	// 0.5 is for the this fault model
			}
			// now do second fault model
			GriddedGeoDataSet onFaultData2 = getDefModFaultMoRatesInRELM_Region(FaultModels.FM3_2, dm);
			for(int i=0;i<onFaultData2.size();i++) {
				if(!Double.isNaN(onFaultData2.get(i)))	// treat the Geo NaNs as zero
					aveDefModOnFault.set(i, aveDefModOnFault.get(i) + onFaultData2.get(i)*dm.getRelativeWeight(null)*0.5);	// 0.5 is for the this fault model
			}
		}
		return aveDefModOnFault;
	}

	
	/**
	 * This returns the average moment rate data including both on- and off-fault sources,
	 * where the average is taken over  ABM, NEOKINEMA, ZENG, and optionally GEOLOGIC.
	 * 
	 * The off-fault geologic is included here (which has a uniform distribution off fault)
	 * @param fm
	 * @return
	 */
	public static GriddedGeoDataSet getAveDefModSpatialMomentRateData(FaultModels fm, boolean includeGeolInOnFltAve,
			boolean includeGeolInOffFltAve) {

		GriddedGeoDataSet aveDefModOnFault = getAveDefModSpatialOnFaultMomentRateData(fm, includeGeolInOnFltAve);
		GriddedGeoDataSet aveDefModOffFault = DeformationModelOffFaultMoRateData.getInstance().getAveDefModelSpatialOffFaultMoRates(fm, includeGeolInOffFltAve);
		
		GriddedGeoDataSet aveDefModData = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();

		for(int i=0;i<aveDefModData.size();i++) {// initialize to zero
			aveDefModData.set(i,aveDefModOnFault.get(i)+aveDefModOffFault.get(i));
		}
		return aveDefModData;
	}
	
	/**
	 * This returns a pdf of the ave deformation model (including faults, both fault 
	 * models 3.1 and 3.2, and the following deformation models: ABM, NEOKINEMA, ZENG, 
	 * and GEOLOGIC, although the latter is excluded in the off-fault portion because
	 * it's not available).
	 * @return
	 */
	public static GriddedGeoDataSet getAveDefModSpatialPDF_WithFaults() {
		GriddedGeoDataSet data1 = getAveDefModSpatialMomentRateData(FaultModels.FM3_1, true, false);
		GriddedGeoDataSet data2 = getAveDefModSpatialMomentRateData(FaultModels.FM3_2, true, false);
		GriddedGeoDataSet pdf = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		double sum=0;
		for(int i=0; i<pdf.size();i++) {
			pdf.set(i, data1.get(i)+data2.get(i));
			sum += pdf.get(i);
		}
		for(int i=0; i<pdf.size();i++) {
			pdf.set(i, pdf.get(i)/sum);
		}
		return pdf;
	}


	
	/**
	 * This generates Figures in reports and talks
	 */
	public static void plotWtAveOnFaultMoRateRatioToUCERF2_Map() {
		
		// Get wt ave def model for on-fault data
		GriddedGeoDataSet aveDefModOnFault = getWtAveDefModSpatialOnFaultMomentRateData();
		
		// make UCERF2 on-fault data
		ModMeanUCERF2 erf= new ModMeanUCERF2();
		erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		erf.updateForecast();
		GriddedGeoDataSet ucerf2_Faults = ERF_Calculator.getMomentRatesInRegion(erf, RELM_RegionUtils.getGriddedRegionInstance());

		try {
			GMT_CA_Maps.plotRatioOfRateMaps(aveDefModOnFault, ucerf2_Faults, "WtedAveDefModOnFault_RatioToUCERF2_MoRateMap", " " , "WtedAveDefModOnFault_RatioToUCERF2_MoRateMap");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



		
	}
	
	
	
	
	/**
	 * This generates Figures in reports and talks using only FM 3.1.
	 */
	public static void plotAllSpatialMoRateMaps() {
		
		FaultModels fm = FaultModels.FM3_1;
		
		// make UCERF2 on, off, and total rates data
		ModMeanUCERF2 erf= new ModMeanUCERF2();
		erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		erf.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		erf.updateForecast();
		GriddedGeoDataSet ucerf2_OffFault = ERF_Calculator.getMomentRatesInRegion(erf, RELM_RegionUtils.getGriddedRegionInstance());
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		erf.updateForecast();
		GriddedGeoDataSet ucerf2_Faults = ERF_Calculator.getMomentRatesInRegion(erf, RELM_RegionUtils.getGriddedRegionInstance());
		GriddedGeoDataSet ucerf2_All = new GriddedGeoDataSet(RELM_RegionUtils.getGriddedRegionInstance(), true);
		double fltTest=0, offTest=0, allTest=0;
		for(int i=0;i<ucerf2_All.size();i++) {
			offTest += ucerf2_OffFault.get(i);
			fltTest += ucerf2_Faults.get(i);
			ucerf2_All.set(i, ucerf2_OffFault.get(i)+ucerf2_Faults.get(i));
			allTest += ucerf2_All.get(i);
		}
		try {
			GMT_CA_Maps.plotSpatialMoRate_Map(ucerf2_Faults.copy(), "UCERF2 On-Fault MoRate-Nm/yr", " " , "UCERF2_OnFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(ucerf2_OffFault.copy(), "UCERF2 Off-Fault MoRate-Nm/yr", " " , "UCERF2_OffFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(ucerf2_All.copy(), "UCERF2 Total MoRate-Nm/yr", " " , "UCERF2_TotalMoRateMap");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Now make ave def model data (excluding geol for off-fault)
		DeformationModelOffFaultMoRateData spatPDFgen = DeformationModelOffFaultMoRateData.getInstance();
		GriddedGeoDataSet aveDefModOnFault = getAveDefModSpatialOnFaultMomentRateData(fm, true);	// include geol here
		GriddedGeoDataSet aveDefModOffFault = spatPDFgen.getAveDefModelSpatialOffFaultMoRates(fm, false);	// don't include geol here
		GriddedGeoDataSet aveDefModTotal = getAveDefModSpatialMomentRateData(fm, true, false);

		try {
			GMT_CA_Maps.plotSpatialMoRate_Map(aveDefModOnFault.copy(), "AveDefModOnFaultMoRate-Nm/yr", " " , "AveDefModOnFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(aveDefModOffFault.copy(), "AveDefModOffFaultMoRate-Nm/yr", " " , "AveDefModOffFaultMoRateMap");
			GMT_CA_Maps.plotSpatialMoRate_Map(aveDefModTotal.copy(), "AveDefModTotalMoRate-Nm/yr", " " , "AveDefModTotalMoRate");
			GMT_CA_Maps.plotRatioOfRateMaps(aveDefModOnFault, ucerf2_Faults, "AveDefModOnFault_RatioToUCERF2_MoRateMap", " " , "AveDefModOnFault_RatioToUCERF2_MoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(aveDefModOffFault, ucerf2_OffFault, "AveDefModOffFault_RatioToUCERF2_MoRateMap", " " , "AveDefModOffFault_RatioToUCERF2_MoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(aveDefModTotal, ucerf2_All, "AveDefModTotal_RatioToUCERF2_MoRateMap", " " , "AveDefModTotal_RatioToUCERF2_MoRateMap");

			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_Faults, aveDefModOnFault, "UCERF2OnFault_RatioToAveDefMod_MoRateMap", " " , "UCERF2OnFault_RatioToAveDefMod_MoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_OffFault, aveDefModOffFault, "UCERF2OffFault_RatioToAveDefMod_MoRateMap", " " , "UCERF2OffFault_RatioToAveDefMod_MoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_All, aveDefModTotal, "UCERF2_Total_RatioToAveDefMod_MoRateMap", " " , "UCERF2_Total_RatioToAveDefMod_MoRateMap");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		// now do individual dm models relative to U2 and the average DM
		ArrayList<DeformationModels> dmList = new ArrayList<DeformationModels>();
		dmList.add(DeformationModels.GEOLOGIC);
		dmList.add(DeformationModels.ABM);
		dmList.add(DeformationModels.NEOKINEMA);
		dmList.add(DeformationModels.ZENGBB);
				

		for(DeformationModels dm : dmList) {
			GriddedGeoDataSet offFaultData = spatPDFgen.getDefModSpatialOffFaultMoRates(fm, dm);
			GriddedGeoDataSet onFaultData = getDefModFaultMoRatesInRELM_Region(fm, dm);
			GriddedGeoDataSet totalMoRateData = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
			for(int i=0; i<totalMoRateData.size();i++) {
				// some GEOL on-fault values are NaN:
				if(Double.isNaN(onFaultData.get(i))) {
					System.out.println("NaN onFault:\t"+i+"\t"+dm.getShortName());
					totalMoRateData.set(i, offFaultData.get(i));
				}
				else {
					totalMoRateData.set(i, offFaultData.get(i)+onFaultData.get(i));
				}
			}
			
			try {
				GMT_CA_Maps.plotSpatialMoRate_Map(onFaultData.copy(), dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_OnFaultMoRateMap");
				GMT_CA_Maps.plotSpatialMoRate_Map(offFaultData.copy(), dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_OffFaultMoRateMap");
				GMT_CA_Maps.plotSpatialMoRate_Map(totalMoRateData.copy(), dm+" MoRate-Nm/yr", " " , dm.getShortName()+"_TotalMoRateMap");
				// ratios to U2
				GMT_CA_Maps.plotRatioOfRateMaps(onFaultData, ucerf2_Faults, dm+" OnFaulRatioToUCERF2", " " , dm.getShortName()+"_OnFaulRatioToUCERF2Map");
				GMT_CA_Maps.plotRatioOfRateMaps(offFaultData, ucerf2_OffFault, dm+" OffFaulRatioToUCERF2", " " , dm.getShortName()+"_OffFaulRatioToUCERF2Map");
				GMT_CA_Maps.plotRatioOfRateMaps(totalMoRateData, ucerf2_All, dm+" TotalRatioToUCERF2", " " , dm.getShortName()+"_TotalRatioToUCERF2Map");
				// ratios to Ave
				GMT_CA_Maps.plotRatioOfRateMaps(onFaultData, aveDefModOnFault, dm+" OnFaulRatioToAveDefMod", " " , dm.getShortName()+"_OnFaulRatioToAveDefModMap");
				GMT_CA_Maps.plotRatioOfRateMaps(offFaultData, aveDefModOffFault, dm+" OffFaulRatioToAveDefMod", " " , dm.getShortName()+"_OffFaulRatioToAveDefModMap");
				GMT_CA_Maps.plotRatioOfRateMaps(totalMoRateData, aveDefModTotal, dm+" TotalRatioToAveDefMod", " " , dm.getShortName()+"_TotalRatioToAveDefModMap");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		

		// Now do the smooth-seismicity results
		double totAveMomentRate=0;
		for(int i=0; i<aveDefModTotal.size();i++)
			totAveMomentRate+=aveDefModTotal.get(i);
		GriddedGeoDataSet ucerf2_SmSeisDist = SmoothSeismicitySpatialPDF_Fetcher.getUCERF2pdfAsGeoData();
		for(int i=0;i< ucerf2_SmSeisDist.size();i++)
			ucerf2_SmSeisDist.set(i, totAveMomentRate*ucerf2_SmSeisDist.get(i));
		
		GriddedGeoDataSet ucerf3_SmSeisDist = SmoothSeismicitySpatialPDF_Fetcher.getUCERF3pdfAsGeoData();
		for(int i=0;i< ucerf3_SmSeisDist.size();i++)
			ucerf3_SmSeisDist.set(i, totAveMomentRate*ucerf3_SmSeisDist.get(i));
		System.out.println("totAveMomentRate="+totAveMomentRate);
		
		// FOLLOWING COMMENTED OUT UNTIL PETER GIVES ME THE UPDATE TO THE 4TH LINE DOWN - Needs to get a faultSustemRupSet to do this properly
		// make aveDefModTotal inside and outside fault polygons
//		GriddedGeoDataSet aveDefModTotalInsideFaults = aveDefModTotal.copy();
//		GriddedGeoDataSet aveDefModTotalOutsideFaults = aveDefModTotal.copy();
//		double[] nodeFracsInside = FaultPolyMgr.getNodeFractions(fm);
//		for(int i=0;i<aveDefModTotal.size();i++) {
//			aveDefModTotalInsideFaults.set(i, aveDefModTotalInsideFaults.get(i)*nodeFracsInside[i]);
//			aveDefModTotalOutsideFaults.set(i, aveDefModTotalOutsideFaults.get(i)*(1-nodeFracsInside[i]));
//		}

		try {
			GMT_CA_Maps.plotSpatialMoRate_Map(ucerf2_SmSeisDist.copy(), "UCERF2_SmoothSeis MoRate-Nm/yr", " " , "UCERF2_SmSeisMoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_SmSeisDist, aveDefModTotal, "UCERF2_SmSeisToTotAveDefModRatio", " " , "UCERF2_SmSeisToTotAveDefModRatioMap");
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_SmSeisDist, aveDefModTotalInsideFaults, "UCERF2_SmSeisToInsideFaultAveDefModRatio", " " , "UCERF2_SmSeisToInsideFaultAveDefModRatioMap");
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_SmSeisDist, aveDefModTotalOutsideFaults, "UCERF2_SmSeisToOutsideFaultAveDefModRatio", " " , "UCERF2_SmSeisToOutsideFaultAveDefModRatioMap");

			GMT_CA_Maps.plotSpatialMoRate_Map(ucerf3_SmSeisDist.copy(), "UCERF3_SmoothSeis MoRate-Nm/yr", " " , "UCERF3_SmSeisMoRateMap");
			GMT_CA_Maps.plotRatioOfRateMaps(ucerf3_SmSeisDist, aveDefModTotal, "UCERF3_SmSeisToTotAveDefModRatio", " " , "UCERF3_SmSeisToTotAveDefModRatioMap");
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf3_SmSeisDist, aveDefModTotalInsideFaults, "UCERF3_SmSeisToInsideFaultAveDefModRatio", " " , "UCERF3_SmSeisToInsideFaultAveDefModRatioMap");
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf3_SmSeisDist, aveDefModTotalOutsideFaults, "UCERF3_SmSeisToOutsideFaultAveDefModRatio", " " , "UCERF3_SmSeisToOutsideFaultAveDefModRatioMap");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		// now make Mmax map plot
		double totObsRate = TotalMag5Rate.RATE_7p9.getRateMag5();
		GriddedGeoDataSet mMaxData = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		ucerf3_SmSeisDist = SmoothSeismicitySpatialPDF_Fetcher.getUCERF3pdfAsGeoData();
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(0, 3000, 0.01);
		for(int i=0;i< ucerf3_SmSeisDist.size();i++) {
			double rate = totObsRate*ucerf3_SmSeisDist.get(i)*1e5;		// increase by 1e5 for rate at zero mag
			double moRate = aveDefModTotal.get(i);
				gr.setAllButMagUpper(0, moRate, rate, 1.0, false);
				mMaxData.set(i, gr.getMagUpper());
		}
		try {
			GMT_CA_Maps.plotMagnitudeMap(mMaxData, "Implied Mmax", " " , "AveDefMod_UCERF3_smSeis_ImpliedMmaxMap");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
	}
	
//	public static void plotSmSeisOverAveDefModelMap() {
//		FaultModels fm = FaultModels.FM3_1;
//		ArrayList<DeformationModels> dmList = new ArrayList<DeformationModels>();
//		dmList.add(DeformationModels.GEOLOGIC);
//		dmList.add(DeformationModels.ABM);
//		dmList.add(DeformationModels.NEOKINEMA);
////		dmList.add(DeformationModels.GEOBOUND);
//		dmList.add(DeformationModels.ZENG);
//		
//		DeformationModelOffFaultMoRateData spatPDFgen = DeformationModelOffFaultMoRateData.getInstance();
//		
//		ArrayList<GriddedGeoDataSet> dmMoRatesList = new ArrayList<GriddedGeoDataSet>();
//		
//		// compute aveDefModPDF
//		GriddedGeoDataSet aveDefModPDF = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
//		GriddedGeoDataSet aveDefModMoRates = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
//		for(int i=0;i<aveDefModPDF.size();i++) {// initialize to zero
//			aveDefModPDF.set(i,0);
//			aveDefModMoRates.set(i,0);
//		}
//
//		for(DeformationModels dm : dmList) {
//			System.out.println("adding "+dm+" to the mean");
//			GriddedGeoDataSet offFaultData = spatPDFgen.getDefModSpatialOffFaultMoRates(fm, dm);
//			GriddedGeoDataSet onFaultData = getDefModMoRatesInRELM_Region(fm, dm);
//			GriddedGeoDataSet totalMoRateData = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
//			for(int i=0; i<totalMoRateData.size();i++) {
//				// some GEOL on-fault values are NaN:
//				if(Double.isNaN(onFaultData.get(i))) {
//					System.out.println("NaN onFault:\t"+i+"\t"+dm.getShortName());
//					totalMoRateData.set(i, offFaultData.get(i));
//				}
//				else {
//					totalMoRateData.set(i, offFaultData.get(i)+onFaultData.get(i));
//				}
//			}
//			double sum=0;
//			for(int i=0;i<totalMoRateData.size();i++) 
//				sum += totalMoRateData.get(i);
//			System.out.println("sum="+(float)sum);
//			for(int i=0;i<totalMoRateData.size();i++) {
//				double newVal = (totalMoRateData.get(i)/sum)/dmList.size();
//				aveDefModPDF.set(i, aveDefModPDF.get(i)+newVal);
//				aveDefModMoRates.set(i, aveDefModMoRates.get(i) + totalMoRateData.get(i)/dmList.size());
//			}
//			dmMoRatesList.add(totalMoRateData);
//
//		}
//		
//		double sum=0;
//		for(int i=0;i<aveDefModPDF.size();i++) 
//			sum += aveDefModPDF.get(i);
//		System.out.println("Test:  sum="+(float)sum+" (should be 1.0)");
//
//		GriddedGeoDataSet ucerf2_SmSeisData = SmoothSeismicitySpatialPDF_Fetcher.getUCERF2_PDF();
//		GriddedGeoDataSet ucerf3_SmSeisData = SmoothSeismicitySpatialPDF_Fetcher.getUCERF3_PDF();
//
//		try {
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf2_SmSeisData, aveDefModPDF, "UCERF2_SmSeis/AveDefMod (Ratio)", " " , "UCERF2_SmSeis_AveDefMod_RatioMap");
//			GMT_CA_Maps.plotRatioOfRateMaps(ucerf3_SmSeisData, aveDefModPDF, "UCERF3_SmSeis/AveDefMod (Ratio)", " " , "UCERF3_SmSeis_AveDefMod_RatioMap");
//			GMT_CA_Maps.plotSpatialMoRate_Map(aveDefModMoRates.copy(), "AveDevMod MoRate-Nm/yr", " " , "AveDefMod_TotalMoRateMap");
//
//			for(int i=0;i<dmList.size();i++) {
//				System.out.println(i+": plotting "+dmList.get(i)+" maps");
//				// first one here is a duplicate of what's already done in plotAllSpatialMoRateMaps(), but repeated as a test
//				GMT_CA_Maps.plotSpatialMoRate_Map(dmMoRatesList.get(i).copy(), dmList.get(i)+" MoRate-Nm/yr", " " , dmList.get(i).getShortName()+"_TotalMoRateMap");
//				GMT_CA_Maps.plotRatioOfRateMaps(dmMoRatesList.get(i), aveDefModMoRates, dmList.get(i)+" Ratio", " " , dmList.get(i).getShortName()+"_RatioToMeanMoRateMap");
//			}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//	}
	

	/**
	 * This computes a GriddedGeoDataSet with the total fault moment rate in each bin.
	 * 
	 *  This include moment rate reductions
	 * @param fm
	 * @param dm
	 * @return
	 */
	public static GriddedGeoDataSet getDefModFaultMoRatesInRELM_Region(FaultModels fm, DeformationModels dm) {
		GriddedGeoDataSet moRates = RELM_RegionUtils.getRELM_RegionGeoDataSetInstance();
		GriddedRegion relmGrid = RELM_RegionUtils.getGriddedRegionInstance();
//		System.out.println("moRates.size()="+moRates.size());
//		System.out.println("relmGrid.getNodeCount()="+relmGrid.getNodeCount());

		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			double mr = data.calcMomentRate(true);
			LocationList locList = data.getStirlingGriddedSurface(1.0).getEvenlyDiscritizedListOfLocsOnSurface();
			mr /= (double)locList.size();
			for(Location loc: locList) {
				int index = relmGrid.indexForLocation(loc);
				if(index >=0) {
					double oldVal = moRates.get(index);
					moRates.set(index,oldVal+mr);
				}
//				else
//					System.out.println(loc+"\t"+data.getName());
			}
		}
		
//		// test sum values
//		double sum=0;
//		for(int i=0;i<moRates.size();i++) {
//			sum += moRates.get(i);
//		}
//		System.out.println("HERE " +dm+" MoRate=\t"+sum+"\taltCalc=\t"+calcFaultMoRateForDefModel(fm,dm,true));


		
		return moRates;
	}
	
	
	public static void writeFractionRegionNodesInsideFaultPolygons() {
		
		double totRateMgt5 = TotalMag5Rate.RATE_7p9.getRateMag5();
		
		double[] nodeFracs = FaultPolyMgr.getNodeFractions(FaultModels.FM3_1, null, null);
		GriddedGeoDataSet ucerf2_SmSeisDist = SmoothSeismicitySpatialPDF_Fetcher.getUCERF2pdfAsGeoData();
		GriddedGeoDataSet ucerf3_SmSeisDist = SmoothSeismicitySpatialPDF_Fetcher.getUCERF3pdfAsGeoData();
		double sum = 0;
		double totRateInsideU2=0, totRateInsideU3=0;
		for(int i=0;i<nodeFracs.length;i++) {
			sum += nodeFracs[i];
			totRateInsideU2 += nodeFracs[i]*ucerf2_SmSeisDist.get(i)*totRateMgt5;
			totRateInsideU3 += nodeFracs[i]*ucerf3_SmSeisDist.get(i)*totRateMgt5;
		}
		float fracInside = (float)(sum/(double)nodeFracs.length);
		System.out.println("totFracNodesInFaultPolygons="+fracInside+"\t("+Math.round(100*fracInside)+"%)");

		double totRateOutsideU2 = totRateMgt5 - totRateInsideU2;
		double totRateOutsideU3 = totRateMgt5 - totRateInsideU3;
		System.out.println("UCERF\trateInside\t(%rateIn)\trateOut\t(%rateOut)\totRate");

		System.out.println("UCERF2\t"+(float)totRateInsideU2+"\t("
				+Math.round(100*totRateInsideU2/totRateMgt5)+"%)\t"+
				+(float)totRateOutsideU2+"\t("+
				Math.round(100*totRateOutsideU2/totRateMgt5)+"%)\t"+totRateMgt5);
		System.out.println("UCERF3\t"+(float)totRateInsideU3+"\t("
				+Math.round(100*totRateInsideU3/totRateMgt5)+"%)\t"+
				+(float)totRateOutsideU3+"\t("+
				Math.round(100*totRateOutsideU3/totRateMgt5)+"%)\t"+totRateMgt5);
//		System.out.println("UCERF3\t"+(float)totRateInsideU3+"\t("+Math.round(100*totRateInsideU3/totRateMgt5)+"%)");

		
	}
	
	/**
	 * This writes the fraction of off-fault moment rates (from all the deformation models) that are
	 * inside the polygons of FM 3.1.
	 */
	public static void writeFractionOffFaultMoRateInsideFaultPolygons() {
		
		FaultModels fm = FaultModels.FM3_1;
		DeformationModelOffFaultMoRateData spatPDFgen = DeformationModelOffFaultMoRateData.getInstance();
		double[] nodeFracs = FaultPolyMgr.getNodeFractions(fm, null, null);

		ArrayList<DeformationModels> dmList = new ArrayList<DeformationModels>();
		dmList.add(DeformationModels.GEOLOGIC);
		dmList.add(DeformationModels.ABM);
		dmList.add(DeformationModels.NEOKINEMA);
		dmList.add(DeformationModels.GEOBOUND);
		dmList.add(DeformationModels.ZENG);
				
		System.out.println("DefMod\t%Inside\tMoRateInside\ttotMoRate (all off fault for"+fm+")");
		for(DeformationModels dm : dmList) {
			GriddedGeoDataSet offFaultData = spatPDFgen.getDefModSpatialOffFaultMoRates(fm, dm);
			double totOffFaultMoRate=0, totOffFaultMoRateInside=0;
			for(int i=0;i<offFaultData.size();i++) {
				totOffFaultMoRate += offFaultData.get(i);
				totOffFaultMoRateInside += offFaultData.get(i)*nodeFracs[i];
			}
			int perc = (int)(100.0*totOffFaultMoRateInside/totOffFaultMoRate);
			System.out.println(dm.getShortName()+"\t"+perc+"\t"+(float)totOffFaultMoRateInside+"\t"+(float)totOffFaultMoRate);
		}
	}
	
	
	public static void plotMmaxVersusFractSeisOffFault(double moRateOnFault, double moRateOffFault, double totMge5_rate,
			String label, String fileName) {
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(0, 1100, 0.01);
		EvenlyDiscretizedFunc offMmaxFunc = new EvenlyDiscretizedFunc(0.1,9,0.1);
		EvenlyDiscretizedFunc onMmaxFunc = new EvenlyDiscretizedFunc(0.1,9,0.1);
		for(int i=0; i<offMmaxFunc.size();i++) {
			double fracOff = offMmaxFunc.getX(i);
			gr.setAllButMagUpper(0.0, moRateOffFault, fracOff*totMge5_rate*1e5, 1.0, false);
			offMmaxFunc.set(i,gr.getMagUpper());
			gr.setAllButMagUpper(0.0, moRateOnFault, (1-fracOff)*totMge5_rate*1e5, 1.0, false);
			onMmaxFunc.set(i,gr.getMagUpper());
		}
		offMmaxFunc.setName("offMmaxFunc");
		onMmaxFunc.setName("onMmaxFunc");
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(offMmaxFunc);
		funcs.add(onMmaxFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5, null, 0, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5, null, 0, Color.BLUE));
		GraphWindow graph = new GraphWindow(funcs, label);
		graph.setX_AxisRange(0, 1);
		graph.setY_AxisRange(6.5, 10);
		graph.setX_AxisLabel("Fraction of Total Seismicity That is Off Fault");
		graph.setY_AxisLabel("Maximum Magnitude");
		graph.setTickLabelFontSize(14);
		graph.setAxisLabelFontSize(16);
		graph.setPlotLabelFontSize(18);
		if(fileName != null) {
			try {
				graph.saveAsPNG(fileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}		
	}
	
	public static void plotAllMmaxVersusFractSeisOffFault() {
		
		double rateMge5 = TotalMag5Rate.RATE_7p9.getRateMag5();
		
		// hard-coded values for UCERF2
		plotMmaxVersusFractSeisOffFault(1.73e19, 0.54e19, rateMge5,"UCERF2 Def Mod 2.1", "mMaxVsOffFltSeis_UCERF2.png");
		
		ArrayList<DeformationModels> defModList= new ArrayList<DeformationModels>();
		FaultModels fm = FaultModels.FM3_1;
		
		defModList.add(DeformationModels.ABM);
		defModList.add(DeformationModels.GEOLOGIC);
		defModList.add(DeformationModels.GEOLOGIC_PLUS_ABM);
		defModList.add(DeformationModels.NEOKINEMA);
		defModList.add(DeformationModels.ZENG);
		defModList.add(DeformationModels.GEOBOUND);
		
		for(DeformationModels dm :defModList) {
			String label = dm+" Def Mod";
			String fileName = "mMaxVsOffFltSeis_"+dm+".png";
			plotMmaxVersusFractSeisOffFault(calcFaultMoRateForDefModel(fm,dm,true), 
					calcMoRateOffFaultsForDefModel(fm,dm), rateMge5, label, fileName);
		}

	}
	
	public static List<FaultSectionPrefData> getIsolatedEndpoints(
			List<FaultSectionPrefData> subSects, Map<IDPairing, Double> distances, int maxAwayFromEnd) {
		List<FaultSectionPrefData> isolated = Lists.newArrayList();
		
		Map<Integer, FaultSectionPrefData> subSectsMap = Maps.newHashMap();
		for (FaultSectionPrefData sect : subSects)
			subSectsMap.put(sect.getSectionId(), sect);
		
		Map<Integer, List<FaultSectionPrefData>> parentSects = Maps.newHashMap();
		
		for (int sectIndex=0; sectIndex<subSects.size(); sectIndex++) {
			FaultSectionPrefData sect = subSects.get(sectIndex);
			int parentID = sect.getParentSectionId();
			List<FaultSectionPrefData> parentSectsList = parentSects.get(parentID);
			if (parentSectsList == null) {
				parentSectsList = Lists.newArrayList();
				parentSects.put(parentID, parentSectsList);
			}
			
			parentSectsList.add(sect);
		}
		
		Map<Integer, List<FaultSectionPrefData>> sectPairings = Maps.newHashMap();
		
		for (IDPairing pairing : distances.keySet()) {
			Integer id = pairing.getID1(); // only need to do for ID1 since the reversed pairing will also appear in this list
			
			List<FaultSectionPrefData> pairings = sectPairings.get(id);
			if (pairings == null) {
				pairings = Lists.newArrayList();
				sectPairings.put(id, pairings);
			}
			
			if (!pairings.contains(pairing.getID2()))
				pairings.add(subSectsMap.get(pairing.getID2()));
		}
		
		for (int parentID : parentSects.keySet()) {
			List<FaultSectionPrefData> sects = parentSects.get(parentID);
			
			int myMaxAwayFromEnd = maxAwayFromEnd;
			if (myMaxAwayFromEnd >= sects.size())
				myMaxAwayFromEnd = sects.size()-1;
			
			if (!isConnected(sects.subList(0, myMaxAwayFromEnd+1), sectPairings))
				isolated.add(sects.get(0));
			
			int lastIndex = sects.size()-1;
			if (!isConnected(sects.subList(lastIndex-myMaxAwayFromEnd, lastIndex+1), sectPairings))
				isolated.add(sects.get(lastIndex));
		}
		
		return isolated;
	}
	
	/**
	 * returns true if any of the given sections are connected to a fault with a different parent ID
	 * @param sects
	 * @param sectPairings
	 * @return
	 */
	private static boolean isConnected(List<FaultSectionPrefData> sects, Map<Integer, List<FaultSectionPrefData>> sectPairings) {
		for (FaultSectionPrefData sect : sects) {
			// look for a pairing that is within the distance cutoff and has a different parent
			List<FaultSectionPrefData> pairings = sectPairings.get(sect.getSectionId());
			
			for (FaultSectionPrefData pairing : pairings) {
				if (pairing.getParentSectionId() != sect.getParentSectionId())
					return true;
			}
		}
		return false;
	}
	
	/**
	 * This method computes the fraction of a SpatialSeisPDF that's inside the fault-section polygons 
	 * of the given fltSectPrefDataList.
	 * @param fltSectPrefDataList
	 * @param spatialSeisPDF
	 * @return
	 */
//	public static double getFractSpatialPDF_InsideSectionPolygons(
//			List<FaultSectionPrefData> fltSectPrefDataList, 
//			SpatialSeisPDF spatialSeisPDF) {
//		double sum = 0;
//		GriddedSeisUtils gsu = new GriddedSeisUtils(fltSectPrefDataList, spatialSeisPDF, 12.0);
//		return gsu.pdfInPolys();
//	}
	
	
	
	public static void writeFaultsThatWereTypeA_InUCERF2(FaultModels fm, DeformationModels dm) {
		DeformationModelFetcher defFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		ArrayList<String> parNameList = new ArrayList<String>();
		for(FaultSectionPrefData data : defFetch.getSubSectionList()) {
			if(UCERF2_A_FaultMapper.wasUCERF2_TypeAFault(data.getParentSectionId())) {
				String parName = data.getParentSectionName();
				if(!parNameList.contains(parName))
					parNameList.add(parName);
			}
		}
		System.out.println("Fault sections that were on type-A faults in UCERF2 for Def Mod "+dm+":");
		for(String name:parNameList)
			System.out.println(name);
	}
	
	/**
	 * This will write all deformation model files and fault model files for
	 * http://www.wgcep.org/components-deformation_model_3x to the specified directory.
	 * @param dir
	 * @throws IOException 
	 */
	public static void writeDefModelFaultModelFilesForWebsite(File dir) throws IOException {
		if (!dir.exists())
			Preconditions.checkState(dir.mkdir(), "Directory doesn't exist and couldn't be created: "+dir.getAbsolutePath());
		
		DateFormat df = new SimpleDateFormat("yyyy_MM_dd");
		String dateStr = df.format(new Date());
		
		// we will zip everything at the end. this is a list of filename lists for each zip file
		List<List<String>> fileNamesForZip = Lists.newArrayList();
		// this is the list of names for the zip files to be created
		List<String> zipFileNames = Lists.newArrayList();
		// readme files for each zip file
		List<List<String>> readmes = Lists.newArrayList();
		
		// for metadata
		String methodName = DeformationModelsCalc.class.getName()+".writeDefModelFaultModelFilesForWebsite(...)";
		
		// minisection deformation model files
		List<String> dmFileNames = Lists.newArrayList();
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			for (DeformationModels dm : DeformationModels.values()) {
				// skip zero weight
				if (dm.getRelativeWeight(null) == 0)
					continue;
				
				Map<Integer, DeformationSection> dmSects = DeformationModelFileParser.load(dm.getDataFileURL(fm));
				File dmFile = new File(dir, dateStr+"-"+fm.getShortName()+"-"+dm.getShortName()+"-mini-sects.csv");
				DeformationModelFileParser.write(dmSects, dmFile);
				dmFileNames.add(dmFile.getName());
			}
		}
		fileNamesForZip.add(dmFileNames);
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			File faultNamesFile = new File(dir, fm.encodeChoiceString()+"_fault_names.txt");
			FileWriter namesFW = new FileWriter(faultNamesFile);
			namesFW.write("# Fault ID\tFault Name\n");
			List<FaultSectionPrefData> parentSects = fm.fetchFaultSections();
			Collections.sort(parentSects, new NamedComparator());
			for (FaultSectionPrefData sect : parentSects)
				namesFW.write(sect.getSectionId()+"\t"+sect.getSectionName()+"\n");
			namesFW.close();
			dmFileNames.add(faultNamesFile.getName());
		}
		zipFileNames.add(dateStr+"-deformation-models-mini-sects.zip");
		List<String> dmReadme = Lists.newArrayList();
		dmReadme.add("FILE: "+zipFileNames.get(zipFileNames.size()-1));
		dmReadme.add("Generated by OpenSHA method: "+methodName);
		dmReadme.add("This zip file contains deformation model minisection files as provided by the individual modelers. " +
				"Slip rates are not yet reduced for creep.");
		dmReadme.add("");
		dmReadme.add("Each CSV file contains the following columns: <minisection>,<start lon>,<start lat>,"
				+ "<end lon>,<end lat>,<slip rate (mm/yr)>,<rake>");
		dmReadme.add("");
		dmReadme.add("Minisection refers to each span between points on the fault trace. A perfectly straight"
				+ " fault with only start and end points would only have a single minisection, while a fault"
				+ " with N kinks/bends will have N+1 minisections. Deformation modelers assign slip rates"
				+ " and rakes to each of these minisections, denoted by <fault section ID>.<mini section index>."
				+ " For example, the first minisection on the Carrizo section of the San Andreas Fault"
				+ " (with ID 300), would be denoted as '300.01'. A list of fault section IDs and names"
				+ " for each fault model are also given in the files 'FM3_*_fault_names.txt'");
		readmes.add(dmReadme);
		
		
		// sub section deformation model files
		List<String> dmSubSectFileNames = Lists.newArrayList();
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			for (DeformationModels dm : DeformationModels.values()) {
				// skip zero weight
				if (dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) == 0)
					continue;
				
				DeformationModelFetcher fetch = new DeformationModelFetcher(
						fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
				File dmFile = new File(dir, dateStr+"-"+fm.getShortName()+"-"+dm.getShortName()+"-sub-sects.txt");
				List<String> metaData = Lists.newArrayList();
				metaData.add("Fault Sub Sections file generated on "+dateStr+" by "+methodName);
				metaData.add("Fault Model: "+fm.getName());
				metaData.add("Deformation Model: "+dm.getName());
				metaData.add("Note that upper seismogenic depths reflect aseismic reductions, and that slip rates " +
						"reported have a coupling coefficient applied and as such may be lower than the original " +
						"deformation model rate.");
				FaultSectionDataWriter.writeSectionsToFile(fetch.getSubSectionList(), metaData, dmFile, true);
				dmSubSectFileNames.add(dmFile.getName());
			}
		}
		fileNamesForZip.add(dmSubSectFileNames);
		zipFileNames.add(dateStr+"-deformation-models-sub-sects.zip");
		List<String> dmSubReadme = Lists.newArrayList();
		dmSubReadme.add("FILE: "+zipFileNames.get(zipFileNames.size()-1));
		dmSubReadme.add("Generated by OpenSHA method: "+methodName);
		dmSubReadme.add("This zip file contains deformation model sub section files generated by mapping the minisection data on" +
				" to our sub sections. These files also reflect creep reductions in both the upper seismogenic depth and slip rate.");
		readmes.add(dmSubReadme);
		
		
		// sub section deformation model files
		List<String> dmSubSectNoRedFileNames = Lists.newArrayList();
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			for (DeformationModels dm : DeformationModels.values()) {
				// skip zero weight
				if (dm.getRelativeWeight(InversionModels.CHAR_CONSTRAINED) == 0)
					continue;
				
				DeformationModelFetcher fetch = new DeformationModelFetcher(
						fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
				File dmFile = new File(dir, dateStr+"-"+fm.getShortName()+"-"+dm.getShortName()+"-sub-sects-no-reduce.txt");
				List<String> metaData = Lists.newArrayList();
				metaData.add("Fault Sub Sections file generated on "+dateStr+" by "+methodName);
				metaData.add("Fault Model: "+fm.getName());
				metaData.add("Deformation Model: "+dm.getName());
				metaData.add("Note that upper seismogenic depths DO NOT reflect aseismic reductions, and that slip rates " +
						"reported DO NOT have a coupling coefficient applied");
				FaultSectionDataWriter.writeSectionsToFile(fetch.getSubSectionList(), metaData, dmFile, false);
				dmSubSectNoRedFileNames.add(dmFile.getName());
			}
		}
		fileNamesForZip.add(dmSubSectNoRedFileNames);
		zipFileNames.add(dateStr+"-deformation-models-sub-sects-no-reduce.zip");
		List<String> dmSubNoRedReadme = Lists.newArrayList();
		dmSubNoRedReadme.add("FILE: "+zipFileNames.get(zipFileNames.size()-1));
		dmSubNoRedReadme.add("Generated by OpenSHA method: "+methodName);
		dmSubNoRedReadme.add("This zip file contains deformation model sub section files generated by mapping the minisection data on" +
				" to our sub sections. These files also reflect creep reductions in both the upper seismogenic depth and slip rate.");
		readmes.add(dmSubNoRedReadme);
		
		
		// fault model input files
		List<String> fmFileNames = Lists.newArrayList();
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			ArrayList<FaultSectionPrefData> sects = fm.fetchFaultSections();
			File fmFile = new File(dir, dateStr+"-"+fm.getShortName()+"-sections.txt");
			List<String> metaData = Lists.newArrayList();
			metaData.add("Fault Sections file generated on "+dateStr+" by "+methodName);
			metaData.add("Fault Model: "+fm.getName());
			metaData.add("Note that upper seismogenic depths DO NOT reflect aseismic reductions as these are " +
					"deformation model specific, and that slip rates reported are geologic slip rates and should not be used.");
			FaultSectionDataWriter.writeSectionsToFile(sects, metaData, fmFile, false);
			fmFileNames.add(fmFile.getName());
		}
		fileNamesForZip.add(fmFileNames);
		zipFileNames.add(dateStr+"-fault-models.zip");
		List<String> fmReadme = Lists.newArrayList();
		fmReadme.add("FILE: "+zipFileNames.get(zipFileNames.size()-1));
		fmReadme.add("Generated by OpenSHA method: "+methodName);
		fmReadme.add("This zip file contains fault model parent fault sections (prior to sub sectioning) and do not reflect" +
				"creep reductions.");
		readmes.add(fmReadme);
		
		
		// deformation model plots
		File plotSubDir = new File(dir, "def_model_plots");
		if (!plotSubDir.exists())
			Preconditions.checkState(plotSubDir.mkdir(),
					"Directory doesn't exist and couldn't be created: "+plotSubDir.getAbsolutePath());
		Region region = new CaliforniaRegions.RELM_TESTING();
		try {
			FaultBasedMapGen.plotDeformationModelSlips(region, plotSubDir, false);
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		List<String> dmPlotReadme = Lists.newArrayList();
		dmPlotReadme.add("DIRECTORY: "+plotSubDir.getName());
		dmPlotReadme.add("Generated by OpenSHA method: "+methodName);
		dmPlotReadme.add("This direcotry contains map based plots of each deformation model, as well as geologic slip rate sites. " +
				"Rates plotted in these files are not creep reduced.");
		File plotReadmeFile = new File(plotSubDir, "README");
		FileWriter fw = new FileWriter(plotReadmeFile);
		for (String line : dmPlotReadme)
			fw.write(line+"\n");
		fw.close();
		
		
		// now write zip files (and delete originals)
		for (int i=0; i<fileNamesForZip.size(); i++){
			List<String> fileNames = fileNamesForZip.get(i);
			String zipFileName = zipFileNames.get(i);
			
			List<String> readme = readmes.get(i);
			File readmeFile = new File(dir, "README.txt");
			fw = new FileWriter(readmeFile);
			for (String line : readme)
				fw.write(line+"\n");
			fw.close();
			fileNames.add(readmeFile.getName());
			
			System.out.println("Writing zip file: "+zipFileName);
			FileUtils.createZipFile(new File(dir, zipFileName).getAbsolutePath(), dir.getAbsolutePath(), fileNames);
			
			// now delete originals
			for (String fileName : fileNames) {
				File file = new File(dir, fileName);
				if (!file.delete())
					System.err.println("WARNING: couldn't remove: "+file.getAbsolutePath());
			}
		}
	}
	
	
	/**
	 * This loops over all the deformation models and makes a histogram of mini-section
	 * slip rates (log10 values)
	 */
	public static void plotSectSlipRateHistForAllDeformationModels() {

	HistogramFunction histogram = new HistogramFunction(-4.9, 36, 0.2);
	
	FaultModels[] fm_list = {FaultModels.FM3_1, FaultModels.FM3_2};
	DeformationModels[] dm_list = {DeformationModels.GEOLOGIC,DeformationModels.ABM,DeformationModels.NEOKINEMA,DeformationModels.ZENGBB, };
		
	for(FaultModels fm : fm_list) {
		for(DeformationModels dm : dm_list) {
			if (fm == FaultModels.FM2_1) {
				DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1);
				for (FaultSectionPrefData fault : dmFetch.getSubSectionList()) {
					histogram.add(Math.log10(fault.getOrigAveSlipRate()), 1.0);
				}
			} else {
				Map<Integer, DeformationSection> sects;
				try {
					sects = DeformationModelFileParser.load(dm.getDataFileURL(fm));
					for (DeformationSection sect : sects.values()) {
						for (int i=0; i<sect.getLocs1().size(); i++) {
							double log10_SlipRate = Math.log10(sect.getSlips().get(i));
							int index = histogram.getXIndex(log10_SlipRate);
							if(index != -1)
								histogram.add(index, 1.0);
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
			}
		}
	}
		
		GraphWindow graph = new GraphWindow(histogram.getCumulativeDistFunction(), "Histogram of mini-section slip rates"); 
		graph.setX_AxisLabel("Number");
		graph.setY_AxisLabel("Log10 Slip Rate");

		
	}

	

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		
//		testGetParentSectAveSlipRateHashtable(FaultModels.FM3_1, DeformationModels.NEOKINEMA);
		
//		plotWtAveOnFaultMoRateRatioToUCERF2_Map();

		
//		writeAveMoRateOfParentSectionsInsideRegion(new CaliforniaRegions.NORTHRIDGE_BOX());

//		writeAveSlipRateEtcOfParentSectionsForAllDefAndFaultModels();
		
//		writeMoRateOfParentSectionsForAllDefAndFaultModels();
		
//		plotAllSpatialMoRateMaps();
		
//		plotSectSlipRateHistForAllDeformationModels();
		
	
//		calcMoRateAndMmaxDataForDefModels();

		
		// Pre May 2013:
		

//		writeParentSectionsNearSite(new Location(37.7,-122.4),10);
		
		writeDefModelFaultModelFilesForWebsite(new File("/tmp/dm_files"));
		
		

						
//		writeMoRateOfParentSections(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		


			
//		plotNewFaultSectionsInGMT();
				
//		writeListOfNewFaultSections();
		
				
//		writeSubSectDataForParent("Imperial", FaultModels.FM3_1, DeformationModels.GEOLOGIC);
//		writeSubSectDataForParent("Mendocino", FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		
//		writeAveReducedSlipRateEtcOfParentSections(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		
//		writeFaultsThatWereTypeA_InUCERF2(FaultModels.FM2_1, DeformationModels.UCERF2_ALL);
//		writeFaultsThatWereTypeA_InUCERF2(FaultModels.FM3_1, DeformationModels.ZENG);
		
//		writeParentSectionsInsideRegion(FaultModels.FM3_1, DeformationModels.GEOLOGIC, new CaliforniaRegions.SF_BOX()); 
//		writeParentSectionsInsideRegion(FaultModels.FM3_1, DeformationModels.GEOLOGIC, new CaliforniaRegions.LA_BOX()); 
//		writeParentSectionsInsideRegion(FaultModels.FM3_1, DeformationModels.ZENG, new CaliforniaRegions.LA_BOX()); 
		
		

		
//		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1, 
//				DeformationModels.NEOKINEMA, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 
//				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);

//		System.out.println(getFractSpatialPDF_InsideSectionPolygons(
//			defFetch.getSubSectionList(), SpatialSeisPDF.UCERF3));
		
//		plotDDW_AndLowerSeisDepthDistributions(defFetch.getSubSectionList(),"FM3_1 & GEOLOGIC Def Mod");

		
//		plotAllMmaxVersusFractSeisOffFault();

//		writeFractionOffFaultMoRateInsideFaultPolygons();
		
//		writeFractionRegionNodesInsideFaultPolygons();
		
//		writeMoRateOfParentSections(FaultModels.FM3_1, DeformationModels.NEOKINEMA);
		

//		testFaultZonePolygons(FaultModels.FM3_1);
//		testFaultZonePolygons(FaultModels.FM3_2);
		
//		writeListOfNewFaultSections();
		
//		plotAllSpatialMoRateMaps();
		
//		writeMoRateOfParentSections(FaultModels.FM3_1,DeformationModels.GEOLOGIC);
		
//		File default_scratch_dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "FaultSystemRupSets");
		
//		plotMoRateReductionHist(FaultModels.FM3_1,DeformationModels.GEOLOGIC);
		
//		DeformationModelFetcher defFetch = new DeformationModelFetcher(FaultModels.FM3_1,
//				DeformationModels.GEOLOGIC,default_scratch_dir);
//		System.out.println("GEOLOGIC moment Rate (reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),true));
//		System.out.println("GEOLOGIC moment Rate (not reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),false));
//
//		defFetch = new DeformationModelFetcher(FaultModels.FM3_1,
//				DeformationModels.GEOLOGIC_PLUS_ABM,default_scratch_dir);
//		System.out.println("GEOLOGIC_PLUS_ABM moment Rate (reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),true));
//		System.out.println("GEOLOGIC_PLUS_ABM moment Rate (not reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),false));
//
//		defFetch = new DeformationModelFetcher(FaultModels.FM3_1,
//				DeformationModels.ABM,default_scratch_dir);
//		System.out.println("ABM moment Rate (reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),true));
//		System.out.println("ABM moment Rate (not reduced):\t"+(float)calculateTotalMomentRate(defFetch.getSubSectionList(),false));

		
		
	}

}
