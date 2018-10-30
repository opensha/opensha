package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator.TestScenario;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_StatewideCatalogCompletenessParam;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.U3_EqkCatalogStatewideCompleteness;

public class MiscInfoAndPlotsCalc {
	
	
	public static void plotCatalogMagVsTime(ObsEqkRupList obsQkList, String fileName) {
		DefaultXY_DataSet yearVsMagXYdata = new DefaultXY_DataSet();
		for(ObsEqkRupture rup:obsQkList) {
			double otYear = rup.getOriginTime()/ProbabilityModelsCalc.MILLISEC_PER_YEAR+1970;
			yearVsMagXYdata.set(rup.getMag(),otYear);
		}
		
		U3_EqkCatalogStatewideCompleteness magComplete = U3_EqkCatalogStatewideCompleteness.STRICT;
		U3_EqkCatalogStatewideCompleteness magCompleteRelaxed = U3_EqkCatalogStatewideCompleteness.RELAXED;
		
		EvenlyDiscretizedFunc yrMagCompleteFunc = magComplete.getEvenlyDiscretizedMagYearFunc();
		DefaultXY_DataSet yearVsMagCompleteXYdata = new DefaultXY_DataSet();
		double deltaMagOver2 = yrMagCompleteFunc.getDelta()/2.0;
		for(int i=0;i<yrMagCompleteFunc.size();i++) {
			yearVsMagCompleteXYdata.set(yrMagCompleteFunc.getX(i)-deltaMagOver2,yrMagCompleteFunc.getY(i));
			yearVsMagCompleteXYdata.set(yrMagCompleteFunc.getX(i)+deltaMagOver2,yrMagCompleteFunc.getY(i));
		}
		
		EvenlyDiscretizedFunc yrMagCompleteFuncRelaxed = magCompleteRelaxed.getEvenlyDiscretizedMagYearFunc();
		DefaultXY_DataSet yearVsMagCompleteXYdataRelaxed = new DefaultXY_DataSet();
		double deltaMagOver2_Relaxed = yrMagCompleteFuncRelaxed.getDelta()/2.0;
		for(int i=0;i<yrMagCompleteFuncRelaxed.size();i++) {
			yearVsMagCompleteXYdataRelaxed.set(yrMagCompleteFuncRelaxed.getX(i)-deltaMagOver2_Relaxed,yrMagCompleteFuncRelaxed.getY(i));
			yearVsMagCompleteXYdataRelaxed.set(yrMagCompleteFuncRelaxed.getX(i)+deltaMagOver2_Relaxed,yrMagCompleteFuncRelaxed.getY(i));
		}


		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(yearVsMagXYdata);
		funcList.add(yearVsMagCompleteXYdata);
		funcList.add(yearVsMagCompleteXYdataRelaxed);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 1f, Color.RED));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		GraphWindow graph = new GraphWindow(funcList, "Year vs Mag", plotCharList); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Year");
		graph.setY_AxisRange(1750, 2015);
		
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName+".pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	public static void plotHistQksRateRatesVsTime() {
		double minYear = 1933;
		double maxYear = 2012;
		double deltaYear = 1;
		double minMag = 4;
		double maxMag = 5;
		int numYear = (int) Math.round(maxYear-minYear);
		
		File file = new File("/Users/field/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt");
		ObsEqkRupList histQksList=null;
		try {
			histQksList = UCERF3_CatalogParser.loadCatalogGardnerKnopoffFiltered(file);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.println("numQks = "+histQksList.size());
//		DefaultXY_DataSet rateVsTimeXYdata = new DefaultXY_DataSet();
		HistogramFunction rateVsTime = new HistogramFunction(minYear+deltaYear/2.0, maxYear-deltaYear/2.0, numYear);
		boolean first = true;
		double lastYear = Double.NaN;
		for(ObsEqkRupture rup:histQksList.getRupsBetweenMag(minMag, maxMag)) {
			double year = rup.getOriginTime()/ProbabilityModelsCalc.MILLISEC_PER_YEAR+1970;
			if(year>=minYear && year<=maxYear)
				rateVsTime.add(year, 1.0);
		}
		rateVsTime.scale(1.0/deltaYear);
		GraphWindow graph = new GraphWindow(rateVsTime, "Rate vs Time"); 
		graph.setX_AxisLabel("Year");
		graph.setY_AxisLabel("Rate (per year)");
//		graph.setYLog(true);
//		graph.setY_AxisRange(1e-5,1e4);

	}
	
	
	public static void plotFilteredCatalogMagFreqDist(List<ETAS_EqkRupture> obsQkList,U3_EqkCatalogStatewideCompleteness yrCompleteForMagFunc, 
			SummedMagFreqDist targetMFD, String fileName) {
		SummedMagFreqDist mfd = new SummedMagFreqDist(2.55,8.45,60);
		EvenlyDiscretizedFunc func = yrCompleteForMagFunc.getEvenlyDiscretizedMagYearFunc();
		for(ObsEqkRupture rup:obsQkList) {
			double yrs = 2012.0 - func.getClosestYtoX(rup.getMag());
			mfd.addResampledMagRate(rup.getMag(), 1.0/yrs, true);
		}
		mfd.setName("Catalog MFD");
		mfd.setInfo("Total Rate = "+mfd.getTotalIncrRate());
		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(mfd);
		funcList.add(mfd.getCumRateDistWithOffset());
		funcList.get(1).setName("Cumulative Catalog MFD");
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.BLUE));
		plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
		
		double expNumAftershockRatio=Double.NaN;
		if(targetMFD != null) {
			
			// compute ratio of expected num aftershocks
			double maxMag = targetMFD.getMaxMagWithNonZeroRate();
			double sumObs=0;
			double sumTarget=0;
			for(double mag=2.55;mag< maxMag+mfd.getDelta()/2.0; mag += mfd.getDelta()) {
				sumObs += mfd.getY(mag)*Math.pow(10, mag);
				sumTarget += targetMFD.getY(mag)*Math.pow(10, mag);
			}
			expNumAftershockRatio=sumObs/sumTarget;			
			targetMFD.setName("Target MFD");
			targetMFD.setInfo("Total Rate = "+targetMFD.getTotalIncrRate());
			funcList.add(targetMFD);
			funcList.add(targetMFD.getCumRateDistWithOffset());
			funcList.get(3).setName("Cumulative Target MFD");
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));			
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));			
		}
		mfd.setInfo(mfd.getInfo()+"\n"+"expNumAftershockRatio = "+(float)expNumAftershockRatio);

		GraphWindow graph = new GraphWindow(funcList, "MFDs; expNumAftershockRatio = "+(float)expNumAftershockRatio, plotCharList); 
		graph.setX_AxisLabel("Mag");
		graph.setY_AxisLabel("Rate (per year)");
		graph.setYLog(true);
		graph.setY_AxisRange(1e-5,1e4);
		
		if(fileName != null) {
			try {
				graph.saveAsPDF(fileName+".pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}


	}


	
	
	/**
	 * This utility finds the source index for the fault system rupture that has the given first and last subsection
	 * @param erf
	 * @param firstSectID
	 * @param secondSectID
	 */
	public static void writeTotRateRupOccurOnTheseTwoSections(FaultSystemSolutionERF erf, int firstSectID, int secondSectID) {
		System.out.println("Looking for source...");
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		double totRate=0;
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			if(sectListForSrc.contains(firstSectID) && sectListForSrc.contains(secondSectID)) {
				totRate += erf.getSource(s).computeTotalEquivMeanAnnualRate(erf.getTimeSpan().getDuration());
			}
		}
		System.out.println("totRate="+totRate+"\n\t"+rupSet.getFaultSectionData(firstSectID).getName()+"\n\t"+rupSet.getFaultSectionData(secondSectID).getName());
	}

	

	
	/**
	 * This utility writes a location near the center of the surface of the given section
	 * @param erf
	 * @param sectID
	 */
	public static void writeLocationAtCenterOfSectionSurf(FaultSystemSolutionERF erf, int sectID) {
		String name = erf.getSolution().getRupSet().getFaultSectionData(sectID).getName();
		StirlingGriddedSurface surf = erf.getSolution().getRupSet().getFaultSectionData(sectID).getStirlingGriddedSurface(1.0, false, true);
		Location loc = surf.getLocation(surf.getNumRows()/2, surf.getNumCols()/2);
		System.out.println("Locationat center of "+name+"\t"+loc.getLatitude()+", "+loc.getLongitude()+", "+loc.getDepth());
	}


	

	
	public static void writeInfoAboutClosestSectionToLoc(FaultSystemSolutionERF erf, Location loc) {
		List<FaultSectionPrefData> fltDataList = erf.getSolution().getRupSet().getFaultSectionDataList();
		double minDist = Double.MAX_VALUE;
		int index=-1;
		CalcProgressBar progressBar = new CalcProgressBar("Fault data to process", "junk");
		progressBar.showProgress(true);
		int counter=0;

		for(FaultSectionPrefData fltData:fltDataList) {
			progressBar.updateProgress(counter, fltDataList.size());
			counter+=1;
			double dist = LocationUtils.distanceToSurf(loc, fltData.getStirlingGriddedSurface(1.0, false, true));
			if(minDist>dist) {
				minDist=dist;
				index = fltData.getSectionId();
			}
		}
		progressBar.showProgress(false);
		minDist = LocationUtils.distanceToSurf(loc, fltDataList.get(index).getStirlingGriddedSurface(0.01, false, true));
		System.out.println(index+"\tdist="+(float)minDist+"\tfor\t"+fltDataList.get(index).getName());
	}

	
	
	/**
	 * This utility writes info about sources that use the given index and that are between the specified minimum and maximum mag
	 * @param erf
	 * @param sectID
	 * @param minMag
	 * @param maxMag
	 */
	public static void writeInfoAboutSourcesThatUseSection(FaultSystemSolutionERF erf, int sectID, double minMag, double maxMag) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		System.out.println("srcIndex\tfssIndex\tprob\tmag\tname\t"+rupSet.getFaultSectionData(sectID).getName());
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			if(sectListForSrc.contains(sectID)) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				double meanMag = erf.getSolution().getRupSet().getMagForRup(fssIndex);
				if(meanMag<minMag || meanMag>maxMag)
					continue;
				double prob = erf.getSource(s).computeTotalProb();
				System.out.println(+s+"\t"+fssIndex+"\t"+prob+"\t"+meanMag+"\t"+erf.getSource(s).getName());
//				double probAboveM7 = erf.getSource(s).computeTotalProbAbove(7.0);
//				if(probAboveM7 > 0.0)
//					System.out.println(+s+"\t"+fssIndex+"\t"+probAboveM7+"\t"+meanMag+"\t"+erf.getSource(s).getName());
			}
		}
	}
	

	
	
	/**
	 * This utility finds the source index for the fault system rupture that has the given first and last subsection
	 * @param erf
	 * @param firstSectID
	 * @param secondSectID
	 */
	public static void writeInfoAboutSourceWithThisFirstAndLastSection(FaultSystemSolutionERF erf, int firstSectID, int secondSectID) {
		System.out.println("Looking for source...");
		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
			List<Integer> sectListForSrc = rupSet.getSectionsIndicesForRup(erf.getFltSysRupIndexForSource(s));
			boolean firstIsIt = rupSet.getFaultSectionData(sectListForSrc.get(0)).getSectionId() == firstSectID;
			boolean lastIsIt = rupSet.getFaultSectionData(sectListForSrc.get(sectListForSrc.size()-1)).getSectionId() == secondSectID;
			if(firstIsIt && lastIsIt) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				System.out.println("SourceIndex="+s+"\tfssIndex="+fssIndex+"\t"+erf.getSource(s).getName()+"\tmag="+erf.getSolution().getRupSet().getMagForRup(fssIndex));
				break; 
			}
			firstIsIt = rupSet.getFaultSectionData(sectListForSrc.get(0)).getSectionId() == secondSectID;
			lastIsIt = rupSet.getFaultSectionData(sectListForSrc.get(sectListForSrc.size()-1)).getSectionId() == firstSectID;
			if(firstIsIt && lastIsIt) {
				int fssIndex=erf.getFltSysRupIndexForSource(s);
				System.out.println("SourceIndex="+s+"\tfssIndex="+fssIndex+"\t"+erf.getSource(s).getName()+"\tmag="+erf.getSolution().getRupSet().getMagForRup(fssIndex));
				break;
			}
		}
	}
	
	

	
	public static void tempTestGainResult() {
		
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2014d,10.0,false);	
		double[] td_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);
		
		
		// this will reset sections involved in scenario
		ETAS_Simulator.buildScenarioRup(TestScenario.MOJAVE_M7, erf, erf.getTimeSpan().getStartTimeInMillis());
		double[] td_postScen_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);

		
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		double[] ti_rates = FaultSysSolutionERF_Calc.calcParticipationRateForAllSects(erf, 6.7);

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();

		for(int i=0;i<ti_rates.length;i++)
			System.out.println(td_rates[i]+"\t"+td_postScen_rates[i]+"\t"+ti_rates[i]+"\t"+rupSet.getFaultSectionData(i).getName());

	}

	
	public static Location getMojaveTestLoc(double horzDist) {
		Location loc = new Location(34.698495, -118.508948, 6.550000191);
		if(horzDist == 0.0)
			return loc;
		else {
			LocationVector vect = new LocationVector((295.037-270.0), horzDist, 0.0);
			return LocationUtils.location(loc, vect);			
		}
	}
	

	/**
	 * This shows that El Mayor Cucapah reset Laguna Salada subsections 0-12 (leaving 13 and 14) according to UCERF3 rules.
	 * @param erf
	 */
	public static void plotElMayorAndLagunaSalada(FaultSystemSolutionERF_ETAS erf) {
		
		List<ETAS_EqkRupture> histCat=null;
		try {
			histCat = ETAS_Simulator.getFilteredHistCatalog(ETAS_Simulator.getTimeInMillisFromYear(2012), erf,
					U3ETAS_StatewideCatalogCompletenessParam.DEFAULT_VALUE);
		} catch (IOException | DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// this shows that the ID for El Mayor is 4552
//		for(int i=0;i<histCat.size();i++)
//			if(histCat.get(i).getMag()>7) {
//				double year = ((double)histCat.get(i).getOriginTime())/(365.25*24*3600*1000)+1970.0;
//				System.out.println(i+"\t"+histCat.get(i).getMag()+"\t"+year);
//			}
//		System.exit(-1);

		LocationList locListForElMayor = histCat.get(4552).getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
		DefaultXY_DataSet elMayorXYdata = new DefaultXY_DataSet();
		for(Location loc:locListForElMayor)
			elMayorXYdata.set(loc.getLongitude(), loc.getLatitude());
		
		ArrayList<XY_DataSet> funcList = new ArrayList<XY_DataSet>();
		funcList.add(elMayorXYdata);
		ArrayList<PlotCurveCharacterstics> plotCharList = new ArrayList<PlotCurveCharacterstics>();
		plotCharList.add(new PlotCurveCharacterstics(PlotSymbol.BOLD_CROSS, 1f, Color.RED));

		FaultSystemRupSet rupSet = ((FaultSystemSolutionERF)erf).getSolution().getRupSet();
		FaultPolyMgr faultPolyMgr = FaultPolyMgr.create(rupSet.getFaultSectionDataList(), InversionTargetMFDs.FAULT_BUFFER);	// this works for U3, but not generalized

		for(int i=1042;i<=1056;i++) {
			DefaultXY_DataSet lagunaSaladaPolygonsXYdata = new DefaultXY_DataSet();
			FaultSectionPrefData fltData = rupSet.getFaultSectionData(i);
			System.out.println(fltData.getName());
			Region polyReg = faultPolyMgr.getPoly(i);
			for(Location loc : polyReg.getBorder()) {
				lagunaSaladaPolygonsXYdata.set(loc.getLongitude(), loc.getLatitude());
			}
			funcList.add(lagunaSaladaPolygonsXYdata);
			plotCharList.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
		}
		
		GraphWindow graph = new GraphWindow(funcList, "El Mayor and Laguna Salada", plotCharList); 
		graph.setX_AxisLabel("Longitude");
		graph.setY_AxisLabel("Latitude");


		
	}
	
	/**
	 * 
	 * @param label - plot label
	 * @param local - whether GMT map is made locally or on server
	 * @param dirName
	 * @return
	 */
	public static void plotERF_RatesMap(FaultSystemSolutionERF_ETAS erf, String dirName) {
		
		CaliforniaRegions.RELM_TESTING_GRIDDED mapGriddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		GriddedGeoDataSet xyzDataSet = ERF_Calculator.getNucleationRatesInRegion(erf, mapGriddedRegion, 0d, 10d);
		
		System.out.println("OrigERF_RatesMap: min="+xyzDataSet.getMinZ()+"; max="+xyzDataSet.getMaxZ());
		
		String metadata = "Map from calling plotOrigERF_RatesMap() method";
		
		GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, -3.5);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 1.5);
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.MAX_SPECTRUM.getFileName());
		cptParam.getValue().setBelowMinColor(Color.WHITE);

		try {
			GMT_CA_Maps.makeMap(xyzDataSet, "OrigERF_RatesMap", metadata, dirName, gmt_MapGenerator);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}



	/**
	 *  A temporary method to see if switching back and forth from Poisson to Time Dependent effects
	 *  parameter values (e.g., start time and duration).
	 *  
	 * @param erf
	 */
	public static void testERF_ParamChanges(FaultSystemSolutionERF_ETAS erf) {
		
		ArrayList paramValueList = new ArrayList();
		System.out.println("\nOrig ERF Adjustable Paramteres:\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
			paramValueList.add(param.getValue());
		}
		TimeSpan tsp = erf.getTimeSpan();
		String startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		double duration = erf.getTimeSpan().getDuration();
		System.out.println("\tERF StartTime: "+startTimeString);
		System.out.println("\tERF TimeSpan Duration: "+duration+" years");
		paramValueList.add(startTimeString);
		paramValueList.add(duration);
		int numParams = paramValueList.size();
		
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		System.out.println("\nPois ERF Adjustable Paramteres:\n");
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
		}
		tsp = erf.getTimeSpan();
		startTimeString = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		System.out.println("\tERF StartTime: "+startTimeString);
		System.out.println("\tERF TimeSpan Duration: "+erf.getTimeSpan().getDuration()+" years");

		
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.updateForecast();
		System.out.println("\nFinal ERF Adjustable Paramteres:\n");
		int testNum = erf.getAdjustableParameterList().size()+2;
		if(numParams != testNum) {
			System.out.println("PROBLEM: num parameters changed:\t"+numParams+"\t"+testNum);
		}
		int i=0;
		for(Parameter param : erf.getAdjustableParameterList()) {
			System.out.println("\t"+param.getName()+" = "+param.getValue());
			if(param.getValue() != paramValueList.get(i))
				System.out.println("PROBLEM: "+param.getValue()+"\t"+paramValueList.get(i));
			i+=1;
		}
		tsp = erf.getTimeSpan();
		double duration2 = erf.getTimeSpan().getDuration();
		String startTimeString2 = tsp.getStartTimeMonth()+"/"+tsp.getStartTimeDay()+"/"+tsp.getStartTimeYear()+"; hr="+tsp.getStartTimeHour()+"; min="+tsp.getStartTimeMinute()+"; sec="+tsp.getStartTimeSecond();
		System.out.println("\tERF StartTime: "+startTimeString2);
		System.out.println("\tERF TimeSpan Duration: "+duration2+" years");
		if(!startTimeString2.equals(startTimeString))
			System.out.println("PROBLEM: "+startTimeString2+"\t"+startTimeString2);
		if(duration2 != duration)
			System.out.println("PROBLEM Duration: "+duration2+"\t"+duration);


	}

	

	public static void main(String[] args) {
		
		File catFile = new File("src/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt");
		ObsEqkRupList loadedRups;
		try {
			loadedRups = UCERF3_CatalogParser.loadCatalog(catFile);
			plotCatalogMagVsTime(loadedRups, "FullU3_CatMagVsTime");
			
			U3_EqkCatalogStatewideCompleteness magComplete = U3_EqkCatalogStatewideCompleteness.STRICT;
			ObsEqkRupList filteredCat = magComplete.getFilteredCatalog(loadedRups);
			plotCatalogMagVsTime(filteredCat, "Filtered_CatMagVsTime");
			
			U3_EqkCatalogStatewideCompleteness magCompleteRelaxed = U3_EqkCatalogStatewideCompleteness.RELAXED;
			ObsEqkRupList filteredCatRelaxed = magCompleteRelaxed.getFilteredCatalog(loadedRups);
			plotCatalogMagVsTime(filteredCatRelaxed, "RelaxedFiltered_FullU3_CatMagVsTimeFiltered");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
