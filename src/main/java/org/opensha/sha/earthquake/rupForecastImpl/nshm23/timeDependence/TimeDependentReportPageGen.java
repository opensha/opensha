package org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence;

import static org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepUtils.MILLISEC_PER_YEAR;
import static org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepUtils.MILLISEC_TO_YEARS;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.UCERF3_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.WG02_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAveragingOrder;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectParticMFDs;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.erf.FaultSysSolERF_Calc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.AbstractDOLE_Data;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.PaleoDOLE_Data;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.PaleoMappingAlgorithm;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.HistoricalRupture;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.MappingType;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.utils.ProbModelsPlottingUtils;

public class TimeDependentReportPageGen {
	
	public enum DataToInclude {
		ALL_DATA,
		PALEO_ONLY,
		HIST_RUPS_ONLY
	}

	
	public static CPT getProbGainCPT() {
		CPT tempCPT=null;
		try {
			tempCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance();
		} catch (IOException e) {
			e.printStackTrace();
		}
		CPT ntsCPT = new CPT();
		double newEnd, newStart;
		Color endColor, startColor;
		for (CPTVal val : tempCPT) {
			if (val.end <= 0.35) {
				newEnd = val.end*0.9/0.35;
				endColor = val.maxColor;
			}
			else if (val.end <= 0.45) {
				newEnd = 0.9+(val.end-0.35)*(1.1-0.9)/(0.45-0.35);
				endColor = Color.GRAY;
			}
			else {
				newEnd =1.1+(val.end-0.45)*(3.0-1.1)/(1.0-0.45);
				endColor = val.maxColor;
			}
			
			if (val.start <= 0.35) {
				newStart = val.start*0.9/0.35;
				startColor = val.minColor;
			}
			else if (val.end <= 0.45) {
				newStart = 1.1+(val.start-0.45)*(3.0-1.1)/(1.0-0.45);
				startColor = Color.GRAY;
			}
			else {
				newStart = 1.1+(val.start-0.45)*(3.0-1.1)/(1.0-0.45);
				startColor = val.minColor;
			}
			ntsCPT.add(new CPTVal(newStart, startColor, newEnd,endColor));
		}
		ntsCPT.setAboveMaxColor(ntsCPT.getMaxColor());
		return ntsCPT;
	}
	
	public static String getExplanationText(PaleoMappingAlgorithm mappingAlg, DataToInclude dataToInclude, FSS_ProbabilityModel probModel) {
		String line = "";
		
//		String doleString = "date-of-last-event [DOLE, Hatem et al., 2025] (https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9)";
		String doleString = "date-of-last-event data <a href=\"https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9\">(DOLE, Hatem et al., 2025)</a>"; //[DOLE, Hatem et al., 2025] (https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9)";
		
		switch (dataToInclude) {
		case ALL_DATA:
			line += "This includes both historic ruptures and paleoseismic "+doleString+", where the former supersedes the latter if there is a conflict. ";
			break;
		case PALEO_ONLY:
			line += "This includes only paleoseismic "+doleString+" to show the influence of excluding historic ruptures. ";
			break;
		case HIST_RUPS_ONLY:
			line += "This includes only historic rupture "+doleString+". ";
			break;
		default:
			throw new IllegalStateException();
		}

		if(dataToInclude != DataToInclude.HIST_RUPS_ONLY ) {
			switch (mappingAlg) {
			case FULL_PARENT:
				line += "Paleoseismic constraints are applied to all subsections of the parent fault section. ";
				break;
			case CLOSEST_SECT:
				line = "This is for debugging only, where the paleo constraint is mapped only to the nearest subsection (used to visually verify that the nearest section was indeed chosen). ";
				break;
			case NEIGHBORING_SECTS:
				line += "Paleoseismic constraints are applied to the closest subsection and adjacent subsections (three subsections total, or just two if the paleo site is right near an end of the nearest subsection). ";
				break;

			default:
				throw new IllegalStateException();
			}
		}

		if (probModel == null || probModel instanceof UCERF3_ProbabilityModel) {			// U3 calculation type
			line += "The time-dependent probabilities are calculated as defined in the <a href=\"https://doi.org/10.1785/0120140093\">UCERF3-TD model</a>.  ";
		} else if (probModel instanceof WG02_ProbabilityModel) {
			line += "The time-dependent probabilities are calculated as defined in the WG02 & UCERF2 efforts, as described in equation (5) <a href=\"https://doi.org/10.1785/0120140094\">here</a>.  ";
		}
		else if (probModel instanceof FSS_ProbabilityModel.Poisson) {
			line += "The time-dependent probabilities are calculated using a Poisson model. ";
		}

		
		line+= "Clicking on the \"View GeoJSON\" links below will launch a browser based map from which you can click on faults to get "
				+ "associated values. Data files are provided at the bottom of this page (the last item in the table of contents), which "+
				"can be used for reproducibility tests.";
		
		return line;
	}
	
	

	/**
	 * This is the method used before switching to the new TD framework (TimeDepFaultSystemSolutionERF).
	 * This creates an old UCERF3 ERF in computing probabilities.
	 *  
	 * @param outputDir
	 * @param sol
	 * @param mappingAlg
	 * @param dataToInclude
	 * @throws IOException
	 */
	public static void generateOldPage(File outputDir, FaultSystemSolution sol, PaleoMappingAlgorithm mappingAlg, DataToInclude dataToInclude) throws IOException {
		if(!outputDir.exists()) outputDir.mkdir();
		
		File resourcesDir = new File(outputDir, "resources");
		if(!resourcesDir.exists()) resourcesDir.mkdir();
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		List<PaleoDOLE_Data> paleoDataList = new ArrayList<PaleoDOLE_Data>();
		if(dataToInclude == DataToInclude.PALEO_ONLY || dataToInclude == DataToInclude.ALL_DATA) {
			System.out.println("Loading Paleo data");
			paleoDataList = DOLE_SubsectionMapper.loadPaleoDOLE();			
		}
		List<HistoricalRupture> histRupDataList = new ArrayList<HistoricalRupture>();
		if(dataToInclude == DataToInclude.HIST_RUPS_ONLY || dataToInclude == DataToInclude.ALL_DATA) {
			System.out.println("Loading Historical Rupture data");
			histRupDataList = DOLE_SubsectionMapper.loadHistRups();			
		}
		System.out.println("Mapping DOLE data");
		String dole_ReportString = DOLE_SubsectionMapper.mapDOLE(subSects, histRupDataList, paleoDataList, mappingAlg, false);
		
		// write out the DOLE mapping report
		FileWriter fw = new FileWriter(new File(outputDir+"/dole_ReportString.txt"));
		fw.write(dole_ReportString); 
		fw.close();
		
		MappingType[] sectMappingTypes = new MappingType[subSects.size()];
		AbstractDOLE_Data[] sectMappings = new AbstractDOLE_Data[subSects.size()];
		Color[] sectTypeColors = new Color[subSects.size()];
		double[] sectYears = new double[subSects.size()];
		for (int s=0; s<sectYears.length; s++)
			sectYears[s] = Double.NaN;
		List<AbstractDOLE_Data> allDatas = new ArrayList<>();
		allDatas.addAll(histRupDataList);
		allDatas.addAll(paleoDataList);
		Color closestColor = Color.RED;
		Color neiborColor = Color.ORANGE;
		Color historicalColor = Color.MAGENTA;
		for (AbstractDOLE_Data data : allDatas) {
			List<FaultSection> mappings = data.getMappedSubSects();
			List<MappingType> mappingTypes = data.getMappedTypes();
			for (int i=0; i<mappings.size(); i++) {
				int index = mappings.get(i).getSectionId();
				MappingType type = mappingTypes.get(i);
				sectMappings[index] = data;
				sectMappingTypes[index] = type;
				sectYears[index] = data.year;
				switch (type) {
				case PALEO_CLOSEST_SECT:
					sectTypeColors[index] = closestColor;
					break;
				case PALEO_NEIGHBORING_SECT:
					sectTypeColors[index] = neiborColor;
					break;
				case HISTORICAL_RUP:
					sectTypeColors[index] = historicalColor;
					break;

				default:
					throw new IllegalStateException();
				}
			}
		}
		
		// build DOLE map
		GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
		mapMaker.setWriteGeoJSON(true);
		
		double minYear = -10000;
		double maxYear = 2024;
		CPT yearCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().reverse().rescale(minYear, maxYear);
		yearCPT.setPreferredTickInterval(2000);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.setSkipNaNs(true);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));
		mapMaker.setSectOutlineChar(null);
		mapMaker.plot(resourcesDir, "dole_year_map", " ");
		
		yearCPT = yearCPT.rescale(1000d, maxYear);
		yearCPT.setPreferredTickInterval(100);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.plot(resourcesDir, "dole_year_map_since_1000", " ");
		
		yearCPT = yearCPT.rescale(1800d, maxYear);
		yearCPT.setPreferredTickInterval(20);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.plot(resourcesDir, "dole_year_map_since_1800", " ");
		
		mapMaker.clearSectScalars();
		List<Color> sectTypeColorsList = new ArrayList<>();
		for (Color color : sectTypeColors)
			sectTypeColorsList.add(color);
		mapMaker.plotSectColors(sectTypeColorsList, null, null);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(0, 0, 255)));
		List<String> typeLabels = new ArrayList<>();
		List<PlotCurveCharacterstics> typeChars = new ArrayList<>();
		PlotCurveCharacterstics doleLocChar = new PlotCurveCharacterstics(PlotSymbol.INV_TRIANGLE, 2f, Color.GREEN);
		typeLabels.add("Paleo Locations");
		typeChars.add(doleLocChar);
		typeLabels.add("Paleo (Closest)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, closestColor));
		typeLabels.add("Paleo (Neighbor)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, neiborColor));
		typeLabels.add("Historical Rupture");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, historicalColor));
		mapMaker.setCustomLegendItems(typeLabels, typeChars);
		mapMaker.setLegendInset(RectangleAnchor.TOP_RIGHT);
		List<Location> doleLocs = new ArrayList<>();
		List<FeatureProperties> scatterProps = new ArrayList<>();
		for (PaleoDOLE_Data data : paleoDataList) {
			doleLocs.add(data.location);
			scatterProps.add(data.feature.properties);
		}
		mapMaker.plotScatters(doleLocs, doleLocChar.getColor());
		mapMaker.setScatterProperties(scatterProps);
		mapMaker.setScatterSymbol(doleLocChar.getSymbol(), doleLocChar.getSymbolWidth());
		mapMaker.plot(resourcesDir, "dole_mapping_type", " ");
		
		// make debug geojson that draws lines between sites and mapped faults
		// first do mapping without any historical
		List<FaultSection> clonedFaultSects = new ArrayList<>();
		List<PaleoDOLE_Data> doleDataDebug = DOLE_SubsectionMapper.loadPaleoDOLE();
		for (FaultSection sect : subSects)
			clonedFaultSects.add(sect.clone());
		PlotCurveCharacterstics connectorChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.CYAN);
		List<LocationList> debugLines = new ArrayList<>();
		List<PlotCurveCharacterstics> debugLineChars = new ArrayList<>();
		for (PaleoDOLE_Data data : doleDataDebug) {
			DOLE_SubsectionMapper.mapDOLE(clonedFaultSects, List.of(), List.of(data), PaleoMappingAlgorithm.CLOSEST_SECT, false);
			for (FaultSection sect : data.getMappedSubSects()) {
				Location closest = null;
				double closestDist = Double.MAX_VALUE;
				for (Location loc : FaultUtils.resampleTrace(sect.getFaultTrace(), 30)) {
					double dist = LocationUtils.horzDistance(loc, data.location);
					if (dist < closestDist) {
						closestDist = dist;
						closest = loc;
					}
				}
				LocationList line = new LocationList();
				line.add(data.location);
				line.add(closest);
				debugLines.add(line);
				debugLineChars.add(connectorChar);
			}
		}
		// add historical rupture traces
		PlotCurveCharacterstics histTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.f, Color.BLACK);
		for (HistoricalRupture rup : histRupDataList) {
			debugLines.add(rup.trace);
			debugLineChars.add(histTraceChar);
		}
		typeLabels.add("Paleo to Section Mapping");
		typeChars.add(connectorChar);
		typeLabels.add("Historical Rup Traces");
		typeChars.add(histTraceChar);
		mapMaker.setCustomLegendItems(typeLabels, typeChars);
		mapMaker.setLegendInset(RectangleAnchor.TOP_RIGHT);
		mapMaker.plotLines(debugLines, debugLineChars);
		mapMaker.plot(resourcesDir, "dole_mapping_debug", " ");
		
		mapMaker.clearCustomLegendItems();
		mapMaker.clearLines();
		mapMaker.clearScatters();
		mapMaker.setLegendInset(false);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));

		
		
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(new Date());
		int curYear = cal.get(GregorianCalendar.YEAR);
		
		double[] timeSince = new double[subSects.size()];
		double[] logTimeSince = new double[subSects.size()];
		double[] normTimeSince = new double[subSects.size()];
		double[] recurInt = new double[subSects.size()];
		double[] logRecurInt = new double[subSects.size()];

		
		List<Integer> parentIDs = new ArrayList<>();
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		for (int s=0; s<subSects.size(); s++) {
			recurInt[s] = 1d/sol.calcTotParticRateForSect(s);
			logRecurInt[s] = Math.log10(recurInt[s]);

			if (sectMappings[s] == null) {
				timeSince[s] = Double.NaN;
				logTimeSince[s] = Double.NaN;
				normTimeSince[s] = Double.NaN;
				continue;
			}
			FaultSection sect = subSects.get(s);
			int parentID = sect.getParentSectionId();
			if (!parentSectsMap.containsKey(parentID)) {
				parentSectsMap.put(parentID, new ArrayList<>());
				parentIDs.add(parentID);
			}
			parentSectsMap.get(parentID).add(sect);
			timeSince[s] = Math.max(0, curYear - sectMappings[s].year);
			logTimeSince[s] = Math.log10(timeSince[s]);
			normTimeSince[s] = timeSince[s]/recurInt[s];
		}
		
		
		double[] bpt_Mgt6pt7_prob = new double[subSects.size()];
		double[] log_Mgt6pt7_prob = new double[subSects.size()];
		double[] pois_Mgt6pt7_prob = new double[subSects.size()];
		double[] probGain_Mgt6pt7 = new double[subSects.size()];
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		// prob gain
// System.out.println("Starting ERF stuff)");
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
//		erf.setParameter(MagDependentAperiodicityParam.NAME,MagDependentAperiodicityOptions.MID_VALUES);
		erf.setParameter(HistoricOpenIntervalParam.NAME, 0d); // curYear-1875d); // or whatever
		erf.getTimeSpan().setStartTime(curYear);
		erf.getTimeSpan().setDuration(50d);
		erf.updateForecast();
		for (int s=0; s<subSects.size(); s++) {
			bpt_Mgt6pt7_prob[s] = FaultSysSolutionERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			log_Mgt6pt7_prob[s] = Math.log10(bpt_Mgt6pt7_prob[s]);
		}
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(50d);
		erf.updateForecast();
		String csv_dataSring = "sectID,yrOfLast,yrsSince,normTimeSince,supraRI,BPT_Prob_Mgt6pt7,Pois_Prob_Mgt6pt7,ProbGain_Mgt6pt7,ParID,SubsectName,ParentName,DOLE_MappingType\n";
		for (int s=0; s<subSects.size(); s++) {
			pois_Mgt6pt7_prob[s] = FaultSysSolutionERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			probGain_Mgt6pt7[s] = bpt_Mgt6pt7_prob[s]/pois_Mgt6pt7_prob[s];
			String sectName = subSects.get(s).getSectionName();
			String modName = sectName.replace(",","_");
			String parentName = subSects.get(s).getParentSectionName();
			String modParentName=null;
			if(parentName != null)
				modParentName = parentName.replace(",","_");
			csv_dataSring += s+","+(curYear-timeSince[s])+","+timeSince[s]+","+normTimeSince[s]+","+recurInt[s]+","+
					bpt_Mgt6pt7_prob[s]+","+pois_Mgt6pt7_prob[s]+","+probGain_Mgt6pt7[s]+","+
					subSects.get(s).getParentSectionId()+","+modName+","+modParentName+","+sectMappingTypes[s]+"\n";
		}
		// write out the DOLE mapping report
		FileWriter fw2 = new FileWriter(new File(outputDir+"/subSectDataMgt6pt7.csv"));
		fw2.write(csv_dataSring); 
		fw2.close();


// System.out.println("Done with ERF stuff)");
		
		mapMaker.clearSectColors();
		CPT riCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6).reverse();
		CPT logTS_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6);
		CPT normTimeSince_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0,3);
		CPT probGainCPT = getProbGainCPT();
		// System.out.println("HERE probGainCPT\n"+probGainCPT.toString());
		CPT logProbCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-6,1);

		
		String logriPrefix = "log_recurrence_interval";
		String ntsPrefix = "norm_time_since";
		String log_tsPrefix = "log_time_since";
		String probGainPrefix = "mGT6pt7_ProbGain";
		String log_probPrefix = "mGT6pt7_logProb50yr";
		
		mapMaker.plotSectScalars(logRecurInt, riCPT, "Log10 Recurrence Interval (years)");
		mapMaker.plot(resourcesDir, logriPrefix, " ");
		mapMaker.plotSectScalars(normTimeSince, normTimeSince_CPT, "Normalized Time Since Last Event");
		mapMaker.plot(resourcesDir, ntsPrefix, " ");
		mapMaker.plotSectScalars(logTimeSince, logTS_CPT, "Log10 Time Since Last Event (from "+curYear+")");
		mapMaker.plot(resourcesDir, log_tsPrefix, " ");
		mapMaker.plotSectScalars(probGain_Mgt6pt7, probGainCPT, "M≥6.7 Prob. Gain");
		mapMaker.plot(resourcesDir, probGainPrefix, " ");
		mapMaker.plotSectScalars(log_Mgt6pt7_prob, logProbCPT, "log10 M≥6.7 50-yr Prob.");
		mapMaker.plot(resourcesDir, log_probPrefix, " ");

		
		// Make solution report
		
		String relPath = resourcesDir.getName();
		List<String> lines = new ArrayList<>();
		if(dataToInclude == DataToInclude.PALEO_ONLY)
			lines.add("# Time-Dependence Report, Paleo Data Only, "+mappingAlg);
		else if(dataToInclude == DataToInclude.HIST_RUPS_ONLY)
			lines.add("# Time-Dependence Report, Historic Ruptures Only");
		else
			lines.add("# Time-Dependence Report, All DOLE Data, "+mappingAlg);
		lines.add(getExplanationText(mappingAlg, dataToInclude, null));
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		lines.add("## DOLE Mapping Type");
		lines.add(topLink); lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![DOLE Mapping Type]("+relPath+"/dole_mapping_type.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_mapping_type.geojson"));
		table.finalizeLine();

		lines.addAll(table.build());
		lines.add("");

		
		lines.add("## Year of Last Event");
		lines.add(topLink); lines.add("");
//		lines.add("bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,bla,");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Year of Last Event]("+relPath+"/dole_year_map.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map.geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Log10 Recurrence Interval");
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Recurrence Interval]("+relPath+"/"+logriPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+logriPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Normalized Time Since Last Event");
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Normalized Time Since Last Event]("+relPath+"/"+ntsPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+ntsPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Log10 50-yr Probability for M&ge;6.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 50-yr Probability for M&ge;6.7]("+relPath+"/"+log_probPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+log_probPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Probability Gain for M&ge;6.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![50-yr Probability Gain for M&ge;6.7]("+relPath+"/"+probGainPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probGainPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");

		lines.add("## Download CSV Data File");  
		lines.add(topLink); lines.add("");
//		lines.add(outputDir.getName()+"/subSectDataMgt6pt7.csv");
		String downloadLine = "Here: [subSectDataMgt6pt7.csv](subSectDataMgt6pt7.csv)";
		lines.add(downloadLine);
		lines.add("");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		lines.add("");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	/**
	 * This reads the specified column from the given file
	 * @param file
	 * @return
	 */
	private static double[] readSectProbFromFile(File file, int column) {
		
		double[] sectGain = null;
		try {
			List<String> fileLines = Files.readLines(file, Charset.defaultCharset());
			sectGain = new double[fileLines.size()-1];
			int s=-1;
			for (String line:fileLines) {
				s+=1;
				if(s==0)
					continue; // skip header line
				String[] st = StringUtils.split(line,",");
				sectGain[s-1] = Double.valueOf(st[column]);
			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		return sectGain;
	}
	
	
	/**
	 * This generates a page for the UCERF3 Branch Averaged Fault System Solution, 
	 * using the UCERF3 DOLE data.
	 * @param outputDir
	 * @param erf
	 * @param comparisonDir
	 * @param titleString
	 * @param infoString
	 * @throws IOException
	 */
	public static void generatePageUCERF3(File outputDir, TimeDepFaultSystemSolutionERF erf, 
			String titleString, String infoString) throws IOException {
		
//		//	get comparison dir name
//		File comparisonDir;
//        String comparisonDirName = Paths.get(comparisonDir.toString()).getFileName().toString();
		
		if(!outputDir.exists()) outputDir.mkdir();
		
		File resourcesDir = new File(outputDir, "resources");
		if(!resourcesDir.exists()) resourcesDir.mkdir();
		
		FSS_ProbabilityModel probModel = erf.getProbabilityModel();
		boolean isPoisson = false;
		
		boolean aveRecurIntervals=true;
		boolean aveNormTimeSinceLast=true;
		if (probModel instanceof UCERF3_ProbabilityModel) {			// U3 calculation type
			UCERF3_ProbabilityModel u3ProbModel = (UCERF3_ProbabilityModel)probModel;
			aveRecurIntervals = u3ProbModel.getAveragingTypeChoice().isAveRI();
			aveNormTimeSinceLast = u3ProbModel.getAveragingTypeChoice().isAveNTS();
			u3ProbModel.setSaveDebugInfo(false); // ??????????????
		} else if (probModel instanceof WG02_ProbabilityModel) {
//			WG02_ProbabilityModel wgProbModel = (WG02_ProbabilityModel)probModel; // not needed
			aveRecurIntervals = true; 		// default value applied by WG02
			aveNormTimeSinceLast = true;
		}
		else if (probModel instanceof FSS_ProbabilityModel.Poisson) {
			isPoisson = true;
		}
		else
			throw new RuntimeException("Unsupported type of FSS_ProbabilityModel: "+probModel.getName());

		int startYear = 0;
		if(!isPoisson)
			startYear = erf.getTimeSpan().getStartTimeYear();
		double duration = erf.getTimeSpan().getDuration();

		// make ERF parameter values string
		probModel.getAdjustableParameters().getParameterListMetadataString("\n\t");
		String erfParamMetadataString = 
				"\tStart Year = "+startYear+
				"\n\tDuration = "+duration+" years";
		
		erfParamMetadataString += "\n\t"+TimeDepFaultSystemSolutionERF.PROB_MODEL_PARAM_NAME+" = "+probModel.getName()+":"+
				"\n\t\t"+probModel.getAdjustableParameters().getParameterListMetadataString("\n\t\t");
				
		FaultSystemSolution sol = erf.getSolution();
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		double[] sectYears = new double[subSects.size()];
		for (int s=0; s<sectYears.length; s++) {
			long epoch = rupSet.getFaultSectionData(s).getDateOfLastEvent();
			if(epoch == Double.MIN_VALUE)
				sectYears[s] = Double.NaN;
			else
				sectYears[s] = (double)epoch/MILLISEC_PER_YEAR+1970.0;
		}
		
		// build DOLE map
		GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setDefaultPlotWidthPixels(1200);
		
		double minYear = -10000;
		double maxYear = 2024;
		CPT yearCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().reverse().rescale(minYear, maxYear);
		yearCPT.setPreferredTickInterval(2000);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.setSkipNaNs(true);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));
		mapMaker.setSectOutlineChar(null);
		mapMaker.plot(resourcesDir, "dole_year_map", " ");
		
		// alternative start years
//		yearCPT = yearCPT.rescale(1000d, maxYear);
//		yearCPT.setPreferredTickInterval(100);
//		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
//		mapMaker.plot(resourcesDir, "dole_year_map_since_1000", " ");
//		
//		yearCPT = yearCPT.rescale(1800d, maxYear);
//		yearCPT.setPreferredTickInterval(20);
//		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
//		mapMaker.plot(resourcesDir, "dole_year_map_since_1800", " ");
		
		yearCPT = yearCPT.rescale(1875d, 1925);
		yearCPT.setPreferredTickInterval(25);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.plot(resourcesDir, "dole_year_map_recent", " ");

		mapMaker.clearCustomLegendItems();
		mapMaker.clearLines();
		mapMaker.clearScatters();
		mapMaker.setLegendInset(false);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));
		
		double[] timeSince = new double[subSects.size()];
		double[] logTimeSince = new double[subSects.size()];
		double[] normTimeSince = new double[subSects.size()];
		double[] recurInt = new double[subSects.size()];
		double[] logRecurInt = new double[subSects.size()];

		
		List<Integer> parentIDs = new ArrayList<>();
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		for (int s=0; s<subSects.size(); s++) {
			recurInt[s] = 1d/sol.calcTotParticRateForSect(s);
			logRecurInt[s] = Math.log10(recurInt[s]);

			if (Double.isNaN(sectYears[s])) {
				timeSince[s] = Double.NaN;
				logTimeSince[s] = Double.NaN;
				normTimeSince[s] = Double.NaN;
				continue;
			}
			FaultSection sect = subSects.get(s);
			int parentID = sect.getParentSectionId();
			if (!parentSectsMap.containsKey(parentID)) {
				parentSectsMap.put(parentID, new ArrayList<>());
				parentIDs.add(parentID);
			}
			parentSectsMap.get(parentID).add(sect);
			timeSince[s] = Math.max(0, startYear - sectYears[s]);
			logTimeSince[s] = Math.log10(timeSince[s]);
			normTimeSince[s] = timeSince[s]/recurInt[s];
		}
		
		
		double[] td_Mgt6pt7_prob = new double[subSects.size()];
		double[] log_Mgt6pt7_prob = new double[subSects.size()];
		double[] pois_Mgt6pt7_prob = new double[subSects.size()];
		double[] probGain_Mgt6pt7 = new double[subSects.size()];

		erf.updateForecast();
		for (int s=0; s<subSects.size(); s++) {
			td_Mgt6pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			log_Mgt6pt7_prob[s] = Math.log10(td_Mgt6pt7_prob[s]);
		}
		
		double[] td_Mgt7pt7_prob = new double[subSects.size()];
		double[] log_Mgt7pt7_prob = new double[subSects.size()];
		double[] pois_Mgt7pt7_prob = new double[subSects.size()];
		double[] probGain_Mgt7pt7 = new double[subSects.size()];

		for (int s=0; s<subSects.size(); s++) {
			td_Mgt7pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 7.7, s);
			log_Mgt7pt7_prob[s] = Math.log10(td_Mgt7pt7_prob[s]);
		}
		
		
//		System.out.println("Starting Parent Section MPDs");
//		long timeMillis = System.currentTimeMillis();
		HashMap<Integer,String> parentNameID_Map = FaultSysSolERF_Calc.getParentSectNameFromID_Map(erf);
		
		// parent section cum part mag prob dists
		Map<Integer, EvenlyDiscretizedFunc> parCumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
				erf, 5.0, 50, 0.1);
		Map<Integer, EvenlyDiscretizedFunc> parIncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
				erf, 5.05, 50, 0.1);
		
		// temporarily set the forecast as Poisson, to get long-term rates, & no background 
//		IncludeBackgroundOption includeBackgroundOption = (IncludeBackgroundOption)erf.getParameter(IncludeBackgroundParam.NAME).getValue();
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.EXCLUDE);
		erf.setProbabilityModelChoice(FSS_ProbabilityModels.POISSON);
		erf.getTimeSpan().setDuration(duration); // THIS IS NEEDED
		erf.updateForecast();
		
		// TI parent section cum part mag prob dists
		Map<Integer, EvenlyDiscretizedFunc> parTI_CumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
				erf, 5.0, 50, 0.1); 
		Map<Integer, EvenlyDiscretizedFunc> parTI_IncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
				erf, 5.05, 50, 0.1); 
		
		// make parent MPDs
		File parentMPD_Dir = new File(resourcesDir, "parentMPD_Dir");
		if(!parentMPD_Dir.exists()) parentMPD_Dir.mkdir();
		makeParentMPD_Plots(parCumPartMPD_Map,parIncrPartMPD_Map,
				parTI_CumPartMPD_Map, parTI_IncrPartMPD_Map, 
				parentNameID_Map, parentMPD_Dir);
//		timeMillis = System.currentTimeMillis()-timeMillis;
//		System.out.println("Parent Sections Took (sec):"+ timeMillis/1000);
		

		String csv_dataSring = "sectID,yrOfLast,yrsSince,normTimeSince,recurInt,prob_Mgt6pt7,poisProb_Mgt6pt7,probGain_Mgt6pt7,"+
				"prob_Mgt7pt7,poisProb_Mgt7pt7,probGain_Mgt7pt7,"+
				"sectName,parentSectID,parentSectName,DOLE_MappingType\n";
		
		for (int s=0; s<subSects.size(); s++) {
			pois_Mgt6pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			probGain_Mgt6pt7[s] = td_Mgt6pt7_prob[s]/pois_Mgt6pt7_prob[s];
			pois_Mgt7pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 7.7, s);
			probGain_Mgt7pt7[s] = td_Mgt7pt7_prob[s]/pois_Mgt7pt7_prob[s];
			String sectName = subSects.get(s).getSectionName();
			String modName = sectName.replace(",","_");
			String parentName = subSects.get(s).getParentSectionName();
			String modParentName=null;
			if(parentName != null)
				modParentName = parentName.replace(",","_");
			csv_dataSring += s+","+(startYear-timeSince[s])+","+timeSince[s]+","+normTimeSince[s]+","+recurInt[s]+","+
					td_Mgt6pt7_prob[s]+","+pois_Mgt6pt7_prob[s]+","+probGain_Mgt6pt7[s]+","+
					td_Mgt7pt7_prob[s]+","+pois_Mgt7pt7_prob[s]+","+probGain_Mgt7pt7[s]+","+
					modName+","+subSects.get(s).getParentSectionId()+","+modParentName+",notApplicable"+"\n";
		}
		
		// reset ERF to original state
		erf.setCustomProbabilityModel(probModel);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(includeBackgroundOption);
		erf.updateForecast();

		// write out sectionOutputData.csv
		FileWriter fw2 = new FileWriter(new File(outputDir+"/sectionOutputData.csv"));
		fw2.write(csv_dataSring); 
		fw2.close();

		
//		// Get prob ratios with respect to reference dir
//		// Get 5th column:
//		double[] td_Mgt6pt7_prob_reference = readSectProbFromFile(new File(comparisonDir,"/sectionOutputData.csv"),5);
//		if(td_Mgt6pt7_prob_reference.length != td_Mgt6pt7_prob.length)
//			throw new RuntimeException("problem with comparison array length");
//		double[] Mgt6pt7_prob_ratioToReference = new double[td_Mgt6pt7_prob.length];
//		for(int s=0;s<td_Mgt6pt7_prob.length;s++) {
//			Mgt6pt7_prob_ratioToReference[s] = td_Mgt6pt7_prob[s]/td_Mgt6pt7_prob_reference[s];
//		}
//		// Get 5th column:
//		double[] td_Mgt7pt7_prob_reference = readSectProbFromFile(new File(comparisonDir,"/sectionOutputData.csv"),8);
//		if(td_Mgt7pt7_prob_reference.length != td_Mgt7pt7_prob.length)
//			throw new RuntimeException("problem with comparison array length");
//		double[] Mgt7pt7_prob_ratioToReference = new double[td_Mgt7pt7_prob.length];
//		for(int s=0;s<td_Mgt7pt7_prob.length;s++) {
//			Mgt7pt7_prob_ratioToReference[s] = td_Mgt7pt7_prob[s]/td_Mgt7pt7_prob_reference[s];
//		}


//		System.out.println("COMP HERE: "+td_Mgt6pt7_prob[0]+"\t"+td_Mgt6pt7_prob_reference[0]+"\t"+(td_Mgt6pt7_prob[0]/td_Mgt6pt7_prob_reference[0]));

// System.out.println("Done with ERF stuff)");
		
		mapMaker.clearSectColors();
		CPT riCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6).reverse();
		CPT logTS_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6);
		CPT normTimeSince_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0,3);
		CPT probGainCPT = getProbGainCPT();
		// System.out.println("HERE probGainCPT\n"+probGainCPT.toString());
		CPT logProbCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-6,1);

		
		String logriPrefix = "log_recurrence_interval";
		String ntsPrefix = "norm_time_since";
		String log_tsPrefix = "log_time_since";
		String probGainPrefix6pt7 = "mGT6pt7_ProbGain";
		String log_probPrefix6pt7 = "mGT6pt7_logProb";
		String probReferenceRatioPrefix6pt7 = "mGT6pt7_ProbRatioToReference";
		String probGainPrefix7pt7 = "mGT7pt7_ProbGain";
		String log_probPrefix7pt7 = "mGT7pt7_logProb";
		String probReferenceRatioPrefix7pt7 = "mGT7pt7_ProbRatioToReference";
		
		mapMaker.plotSectScalars(logRecurInt, riCPT, "Log10 Recurrence Interval (years)");
		mapMaker.plot(resourcesDir, logriPrefix, " ");
		mapMaker.plotSectScalars(normTimeSince, normTimeSince_CPT, "Normalized Time Since Last Event");
		mapMaker.plot(resourcesDir, ntsPrefix, " ");
		mapMaker.plotSectScalars(logTimeSince, logTS_CPT, "Log10 Time Since Last Event (from "+startYear+")");
		mapMaker.plot(resourcesDir, log_tsPrefix, " ");
		mapMaker.plotSectScalars(probGain_Mgt6pt7, probGainCPT, "M≥6.7 Prob. Gain");
		mapMaker.plot(resourcesDir, probGainPrefix6pt7, " ");
		mapMaker.plotSectScalars(log_Mgt6pt7_prob, logProbCPT, "log10 M≥6.7 Prob.");
		mapMaker.plot(resourcesDir, log_probPrefix6pt7, " ");
//		mapMaker.plotSectScalars(Mgt6pt7_prob_ratioToReference, probGainCPT, "M≥6.7 Prob. Ratio to Reference");
//		mapMaker.plot(resourcesDir, probReferenceRatioPrefix6pt7, " ");
		mapMaker.plotSectScalars(probGain_Mgt7pt7, probGainCPT, "M≥7.7 Prob. Gain");
		mapMaker.plot(resourcesDir, probGainPrefix7pt7, " ");
		mapMaker.plotSectScalars(log_Mgt7pt7_prob, logProbCPT, "log10 M≥7.7 Prob.");
		mapMaker.plot(resourcesDir, log_probPrefix7pt7, " ");
//		mapMaker.plotSectScalars(Mgt7pt7_prob_ratioToReference, probGainCPT, "M≥7.7 Prob. Ratio to Reference");
//		mapMaker.plot(resourcesDir, probReferenceRatioPrefix7pt7, " ");

//		double[] magForRupArray = new double[erf.getNumFaultSystemSources()];
//		double[] aperForRupArray = new double[erf.getNumFaultSystemSources()];
//		double[] aveSlipRateForRupArray = new double[erf.getNumFaultSystemSources()];
		
		// make the rupture data file (e.g., for verification) if U3 methodology (WG02 does not yet have getDebugString())
		if (probModel instanceof UCERF3_ProbabilityModel) {
			String headerString = "";
			headerString += "srcIndex,";	// added here
			headerString += "longTermRate,";	// added here
			headerString += "gainTest,";	// added here
			headerString += "numRup,";	// added here
			// from ProbabilityModelsCalc
			headerString += "fltSysRupIndex,";
			headerString += "probGain,";
			headerString += "condProb,";
			headerString += "rupMag,";
			headerString += "aveCondRecurInterval,";
			headerString += "aveCondRecurIntervalWhereUnknown,";		
			headerString += "aveTimeSinceLastWhereKnownYears,";
			headerString += "aveNormTimeSinceLastEventWhereKnown,";
			headerString += "totRupArea,";
			headerString += "totRupAreaWithDateOfLast,";
			headerString += "fractRupAreaWithDateOfLast,";
			headerString += "aper,";
			headerString += "numSubsectForRup,";
			headerString += "extrapolated,";
			headerString += "rupAveSlipRate,";
			headerString += "srcName,";
			
			FileWriter fw_rups;
			try {
				fw_rups = new FileWriter(new File(outputDir+"/ruptureDataFile.csv"));
				fw_rups.write(headerString+"\n"); 
				((UCERF3_ProbabilityModel)probModel).setSaveDebugInfo(true);
				for(int s=0;s<erf.getNumFaultSystemSources();s++) {
					if(erf.getSource(s).isSourcePoissonian())
						throw new RuntimeException("source is poissonian: "+s+"\t"+erf.getSource(s).getName());
					int fsrIndex = erf.getFltSysRupIndexForSource(s);
					double probGainTest = probModel.getProbabilityGain(fsrIndex, erf.getTimeSpan().getStartTimeInMillis(), duration);		
					double testRate=0;  // this should equal fss rate time gain
					for(int r=0;r<erf.getSource(s).getNumRuptures();r++) {
						double prob = erf.getSource(s).getRupture(r).getProbability();
						testRate+=prob/duration;
					}
					testRate /= probGainTest;
					double rupAveSlipRate = rupSet.getAveSlipRateForRup(fsrIndex);
					String srcName = erf.getSource(s).getName();
					String calcDebugString = ((UCERF3_ProbabilityModel)probModel).getDebugString();
					String[] strParseArray = StringUtils.split(calcDebugString,",");
//					magForRupArray[s] = Double.parseDouble(strParseArray[3]);
//					aperForRupArray[s] = Double.parseDouble(strParseArray[11]);
//					aveSlipRateForRupArray[s] = rupAveSlipRate;
					String line = s+","+sol.getRateForRup(fsrIndex)+","+probGainTest+","+erf.getSource(s).getNumRuptures()+
							","+calcDebugString+","+rupAveSlipRate+
							","+srcName.replace(",", "")+"\n";
					double testRateNormDiff = Math.abs(testRate-sol.getRateForRup(fsrIndex))/testRate;
					if(probGainTest > 1e-16 && testRateNormDiff>1e-6) {
						throw new RuntimeException("long-term rate discrepancy for srcID="+s+"; "+((float)testRate)+" vs "+((float)sol.getRateForRup(fsrIndex))+"; probGainTest="+probGainTest);
					}
					fw_rups.write(line); 
				}
				fw_rups.close();
				
				// write section data
				((UCERF3_ProbabilityModel)probModel).writeCurrentSectDataToCSV_File(outputDir, "sectionInputData.csv");
				
				
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

//		// Make aper scatter plots
//		makeRupAperScatterPlots(aperForRupArray, magForRupArray, 
//				aveSlipRateForRupArray, resourcesDir);
		
		// Make solution report
		
		String relPath = resourcesDir.getName();
		List<String> lines = new ArrayList<>();
		lines.add("# "+titleString);
		lines.add(infoString);
		lines.add("This uses the date of last event data applied in UCERF3. ");
		lines.add("");
		
		lines.add("ERF Time-Dependent Parameter Values:\n");
		lines.add(erfParamMetadataString);
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		

		lines.add("## Year of Last Event");
		lines.add(topLink); lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Year of Last Event]("+relPath+"/dole_year_map.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map.geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Log10 Recurrence Interval");
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Recurrence Interval]("+relPath+"/"+logriPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+logriPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Normalized Time Since Last Event");
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Normalized Time Since Last Event]("+relPath+"/"+ntsPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+ntsPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Log10 Probability for M&ge;6.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Probability for M&ge;6.7]("+relPath+"/"+log_probPrefix6pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+log_probPrefix6pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Probability Gain for M&ge;6.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Probability Gain for M&ge;6.7]("+relPath+"/"+probGainPrefix6pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probGainPrefix6pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
//		lines.add("## Ratio of M&ge;6.7 Probability to that of "+comparisonDirName);  
//		lines.add(topLink); lines.add("");
//		String compLinkString = "[../"+comparisonDirName+"](../"+comparisonDirName+")";
//		lines.add("Comparison model is in the directory: "+compLinkString); lines.add("");
//		table = MarkdownUtils.tableBuilder();
//		table.initNewLine();
//		table.addColumn("![M&ge;6.7 Probability Reference Ratio]("+relPath+"/"+probReferenceRatioPrefix6pt7+".png)");
//		table.finalizeLine().initNewLine();
//		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probReferenceRatioPrefix6pt7+".geojson"));
//		table.finalizeLine();
//		lines.addAll(table.build());
//		lines.add("");
		
		
		lines.add("## Log10 Probability for M&ge;7.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Probability for M&ge;7.7]("+relPath+"/"+log_probPrefix7pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+log_probPrefix7pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Probability Gain for M&ge;7.7");  
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Probability Gain for M&ge;7.7]("+relPath+"/"+probGainPrefix7pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probGainPrefix7pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
//		lines.add("## Ratio of M&ge;7.7 Probability to that of "+comparisonDirName);  
//		lines.add(topLink); lines.add("");
//		String compLinkString7pt7 = "[../"+comparisonDirName+"](../"+comparisonDirName+")";
//		lines.add("Comparison model is in the directory: "+compLinkString7pt7); lines.add("");
//		table = MarkdownUtils.tableBuilder();
//		table.initNewLine();
//		table.addColumn("![M&ge;7.7 Probability Reference Ratio]("+relPath+"/"+probReferenceRatioPrefix7pt7+".png)");
//		table.finalizeLine().initNewLine();
//		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probReferenceRatioPrefix7pt7+".geojson"));
//		table.finalizeLine();
//		lines.addAll(table.build());
//		lines.add("");


		lines.add("## Data Files");  
		lines.add(topLink); lines.add("");
		lines.add("Section data plotted above: [sectionOutputData.csv](sectionOutputData.csv)");
		lines.add("");
		lines.add("Fault system solution file: [../fullPrefUS_FSS.zip](../fullPrefUS_FSS.zip)");


		if (probModel instanceof UCERF3_ProbabilityModel) {
			lines.add("");
			lines.add("Fault section input data: [sectionInputData.csv](sectionInputData.csv)");
			lines.add("");
			lines.add("Fault ruptures data: [ruptureDataFile.csv](ruptureDataFile.csv)");
			lines.add("");
			lines.add("The sectionInputData.csv and ruptureDataFile.csv can be used to verify rupture probabilities.  "+
					"Note that each rupture in ruptureDataFile.csv represents a fault-system-solution rupture, "+
					"or a fault based source in the ERF (not a rupture within the latter). The OpenSHA probability calculation "+
					"can be found here: org.opensha.sha.earthquake.faultSysSolution.erf.td.UCERF3_ProbabilityModel.getProbability()");
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		lines.add("");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}


	public static void generatePage(File outputDir, TimeDepFaultSystemSolutionERF erf, PaleoMappingAlgorithm mappingAlg, 
			DataToInclude dataToInclude, File comparisonDir, String titleString, String infoString) throws IOException {
		
		generatePage( outputDir, erf, mappingAlg,dataToInclude, comparisonDir, titleString, infoString, null);
	}

	
	
	/**
	 * This assumes start time is at the beginning of the year in the timespan.
	 *  
	 * @param outputDir
	 * @param erf
	 * @param mappingAlg
	 * @param dataToInclude
	 * @throws IOException
	 */
	public static void generatePage(File outputDir, TimeDepFaultSystemSolutionERF erf, PaleoMappingAlgorithm mappingAlg, 
			DataToInclude dataToInclude, File comparisonDir, String titleString, String infoString,
			TimeDepFaultSystemSolutionERF ucerf3_erf) throws IOException {

		int testParentFaultID =-1;
		String testParentFaultName = "";
//		testParentFaultID=712;
//		testParentFaultName = "SanAndreas(Peninsula)";
		
		//	get comparison dir name
        String comparisonDirName = Paths.get(comparisonDir.toString()).getFileName().toString();

		
		if(!outputDir.exists()) outputDir.mkdir();
		
		File resourcesDir = new File(outputDir, "resources");
		if(!resourcesDir.exists()) resourcesDir.mkdir();
		
		FSS_ProbabilityModel probModel = erf.getProbabilityModel();
		boolean isPoisson = false;
		
//		boolean aveRecurIntervals=true;
//		boolean aveNormTimeSinceLast=true;
		if (probModel instanceof UCERF3_ProbabilityModel) {			// U3 calculation type
			UCERF3_ProbabilityModel u3ProbModel = (UCERF3_ProbabilityModel)probModel;
//			aveRecurIntervals = u3ProbModel.getAveragingTypeChoice().isAveRI();
//			aveNormTimeSinceLast = u3ProbModel.getAveragingTypeChoice().isAveNTS();
			u3ProbModel.setSaveDebugInfo(false); // ??????????????
		} else if (probModel instanceof WG02_ProbabilityModel) {
//			WG02_ProbabilityModel wgProbModel = (WG02_ProbabilityModel)probModel; // not needed
//			aveRecurIntervals = true; 		// default value applied by WG02
//			aveNormTimeSinceLast = true;
		}
		else if (probModel instanceof FSS_ProbabilityModel.Poisson) {
			isPoisson = true;
		}
		else {
//			do nothing		throw new RuntimeException("Unsupported type of FSS_ProbabilityModel: "+probModel.getName());
		}
		int startYear = 0;
		if(!isPoisson)
			startYear = erf.getTimeSpan().getStartTimeYear();
		double duration = erf.getTimeSpan().getDuration();

		// make ERF parameter values string
		String erfParamMetadataString = 
				"\tStart Year = "+startYear+
				"\n\tDuration = "+duration+" years";
		
		if(dataToInclude == DataToInclude.PALEO_ONLY)
			erfParamMetadataString += "\n\tDOLE Model = Paleo Data Only, "+mappingAlg;
		else if(dataToInclude == DataToInclude.HIST_RUPS_ONLY)
			erfParamMetadataString += "\n\tDOLE Model = Historic Ruptures Only";
		else
			erfParamMetadataString += "\n\tDOLE Model = All DOLE Data, "+mappingAlg;
		
		erfParamMetadataString += "\n\t"+TimeDepFaultSystemSolutionERF.PROB_MODEL_PARAM_NAME+" = "+probModel.getName()+":"+
				"\n\t\t"+probModel.getAdjustableParameters().getParameterListMetadataString("\n\t\t");
				
		FaultSystemSolution sol = erf.getSolution();
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		List<PaleoDOLE_Data> paleoDataList = new ArrayList<PaleoDOLE_Data>();
		if(dataToInclude == DataToInclude.PALEO_ONLY || dataToInclude == DataToInclude.ALL_DATA) {
			System.out.println("Loading Paleo data");
			paleoDataList = DOLE_SubsectionMapper.loadPaleoDOLE();			
		}
		List<HistoricalRupture> histRupDataList = new ArrayList<HistoricalRupture>();
		if(dataToInclude == DataToInclude.HIST_RUPS_ONLY || dataToInclude == DataToInclude.ALL_DATA) {
			System.out.println("Loading Historical Rupture data");
			histRupDataList = DOLE_SubsectionMapper.loadHistRups();			
		}
		System.out.println("Mapping DOLE data");
		String dole_ReportString = DOLE_SubsectionMapper.mapDOLE(subSects, histRupDataList, paleoDataList, mappingAlg, false);
		
		// need to do this so the ERF updates the cached DOLE values:
		erf.resetSectDOLE();
		
		// write out the DOLE mapping report
		FileWriter fw = new FileWriter(new File(outputDir+"/dole_ReportString.txt"));
		fw.write(dole_ReportString); 
		fw.close();
		
		MappingType[] sectMappingTypes = new MappingType[subSects.size()];
		AbstractDOLE_Data[] sectMappings = new AbstractDOLE_Data[subSects.size()];
		Color[] sectTypeColors = new Color[subSects.size()];
		double[] sectYears = new double[subSects.size()];
		for (int s=0; s<sectYears.length; s++)
			sectYears[s] = Double.NaN;
		List<AbstractDOLE_Data> allDatas = new ArrayList<>();
		allDatas.addAll(histRupDataList);
		allDatas.addAll(paleoDataList);
		Color closestColor = Color.RED;
		Color neiborColor = Color.ORANGE;
		Color historicalColor = Color.MAGENTA;
		for (AbstractDOLE_Data data : allDatas) {
			List<FaultSection> mappings = data.getMappedSubSects();
			List<MappingType> mappingTypes = data.getMappedTypes();
			for (int i=0; i<mappings.size(); i++) {
				int index = mappings.get(i).getSectionId();
				MappingType type = mappingTypes.get(i);
				sectMappings[index] = data;
				sectMappingTypes[index] = type;
				sectYears[index] = data.year;
				switch (type) {
				case PALEO_CLOSEST_SECT:
					sectTypeColors[index] = closestColor;
					break;
				case PALEO_NEIGHBORING_SECT:
					sectTypeColors[index] = neiborColor;
					break;
				case HISTORICAL_RUP:
					sectTypeColors[index] = historicalColor;
					break;

				default:
					throw new IllegalStateException();
				}
			}
		}
		
		// build DOLE map
		GeographicMapMaker mapMaker = new GeographicMapMaker(subSects);
		mapMaker.setWriteGeoJSON(true);
		mapMaker.setDefaultPlotWidthPixels(1200);
		
		double minYear = -10000;
		double maxYear = 2024;
		CPT yearCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().reverse().rescale(minYear, maxYear);
		yearCPT.setPreferredTickInterval(2000);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.setSkipNaNs(true);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));
		mapMaker.setSectOutlineChar(null);
		mapMaker.plot(resourcesDir, "dole_year_map", " ");
		
		// alternative start years
//		yearCPT = yearCPT.rescale(1000d, maxYear);
//		yearCPT.setPreferredTickInterval(100);
//		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
//		mapMaker.plot(resourcesDir, "dole_year_map_since_1000", " ");
//		
//		yearCPT = yearCPT.rescale(1800d, maxYear);
//		yearCPT.setPreferredTickInterval(20);
//		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
//		mapMaker.plot(resourcesDir, "dole_year_map_since_1800", " ");
		
		yearCPT = yearCPT.rescale(1875d, 1925);
		yearCPT.setPreferredTickInterval(25);
		mapMaker.plotSectScalars(sectYears, yearCPT, "Year of Last Event");
		mapMaker.plot(resourcesDir, "dole_year_map_recent", " ");

		
		mapMaker.clearSectScalars();
		List<Color> sectTypeColorsList = new ArrayList<>();
		for (Color color : sectTypeColors)
			sectTypeColorsList.add(color);
		mapMaker.plotSectColors(sectTypeColorsList, null, null);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(0, 0, 255)));
		List<String> typeLabels = new ArrayList<>();
		List<PlotCurveCharacterstics> typeChars = new ArrayList<>();
		PlotCurveCharacterstics doleLocChar = new PlotCurveCharacterstics(PlotSymbol.INV_TRIANGLE, 2f, Color.GREEN);
		typeLabels.add("Paleo Locations");
		typeChars.add(doleLocChar);
		typeLabels.add("Paleo (Closest)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, closestColor));
		typeLabels.add("Paleo (Neighbor)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, neiborColor));
		typeLabels.add("Historical Rupture");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, historicalColor));
		mapMaker.setCustomLegendItems(typeLabels, typeChars);
		mapMaker.setLegendInset(RectangleAnchor.TOP_RIGHT);
		List<Location> doleLocs = new ArrayList<>();
		List<FeatureProperties> scatterProps = new ArrayList<>();
		for (PaleoDOLE_Data data : paleoDataList) {
			doleLocs.add(data.location);
			scatterProps.add(data.feature.properties);
		}
		mapMaker.plotScatters(doleLocs, doleLocChar.getColor());
		mapMaker.setScatterProperties(scatterProps);
		mapMaker.setScatterSymbol(doleLocChar.getSymbol(), doleLocChar.getSymbolWidth());
		mapMaker.plot(resourcesDir, "dole_mapping_type", " ");
		
		// make debug geojson that draws lines between sites and mapped faults
		// first do mapping without any historical
		List<FaultSection> clonedFaultSects = new ArrayList<>();
		List<PaleoDOLE_Data> doleDataDebug = DOLE_SubsectionMapper.loadPaleoDOLE();
		for (FaultSection sect : subSects)
			clonedFaultSects.add(sect.clone());
		PlotCurveCharacterstics connectorChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.CYAN);
		List<LocationList> debugLines = new ArrayList<>();
		List<PlotCurveCharacterstics> debugLineChars = new ArrayList<>();
		for (PaleoDOLE_Data data : doleDataDebug) {
			DOLE_SubsectionMapper.mapDOLE(clonedFaultSects, List.of(), List.of(data), PaleoMappingAlgorithm.CLOSEST_SECT, false);
			for (FaultSection sect : data.getMappedSubSects()) {
				Location closest = null;
				double closestDist = Double.MAX_VALUE;
				for (Location loc : FaultUtils.resampleTrace(sect.getFaultTrace(), 30)) {
					double dist = LocationUtils.horzDistance(loc, data.location);
					if (dist < closestDist) {
						closestDist = dist;
						closest = loc;
					}
				}
				LocationList line = new LocationList();
				line.add(data.location);
				line.add(closest);
				debugLines.add(line);
				debugLineChars.add(connectorChar);
			}
		}
		// add historical rupture traces
		PlotCurveCharacterstics histTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.f, Color.BLACK);
		for (HistoricalRupture rup : histRupDataList) {
			debugLines.add(rup.trace);
			debugLineChars.add(histTraceChar);
		}
		typeLabels.add("Paleo to Section Mapping");
		typeChars.add(connectorChar);
		typeLabels.add("Historical Rup Traces");
		typeChars.add(histTraceChar);
		mapMaker.setCustomLegendItems(typeLabels, typeChars);
		mapMaker.setLegendInset(RectangleAnchor.TOP_RIGHT);
		mapMaker.plotLines(debugLines, debugLineChars);
		mapMaker.plot(resourcesDir, "dole_mapping_debug", " ");
		
		mapMaker.clearCustomLegendItems();
		mapMaker.clearLines();
		mapMaker.clearScatters();
		mapMaker.setLegendInset(false);
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(180, 180, 180)));
		
		
		
		double[] timeSince = new double[subSects.size()];
		double[] logTimeSince = new double[subSects.size()];
		double[] normTimeSince = new double[subSects.size()];
		double[] recurInt = new double[subSects.size()];
		double[] logRecurInt = new double[subSects.size()];
		
		HashMap<Integer,String> parentNameID_Map = FaultSysSolERF_Calc.getParentSectNameFromID_Map(erf);
//		Map<Integer, List<Integer>> sectIDsForParentMap = FaultSysSolERF_Calc.getSectionID_ListFromParentSectionID_Map(erf);
		for (int s=0; s<subSects.size(); s++) {
			recurInt[s] = 1d/sol.calcTotParticRateForSect(s);
			logRecurInt[s] = Math.log10(recurInt[s]);

			if (sectMappings[s] == null) {
				timeSince[s] = Double.NaN;
				logTimeSince[s] = Double.NaN;
				normTimeSince[s] = Double.NaN;
				continue;
			}
			timeSince[s] = Math.max(0, startYear - sectMappings[s].year);
			logTimeSince[s] = Math.log10(timeSince[s]);
			normTimeSince[s] = timeSince[s]/recurInt[s];
		}
		
		
		double[] td_Mgt6pt7_prob = new double[subSects.size()];
		double[] log_Mgt6pt7_prob = new double[subSects.size()];
		double[] pois_Mgt6pt7_prob = new double[subSects.size()];
		double[] probGain_Mgt6pt7 = new double[subSects.size()];
		double[] td_Mgt7pt7_prob = new double[subSects.size()];
		double[] log_Mgt7pt7_prob = new double[subSects.size()];
		double[] pois_Mgt7pt7_prob = new double[subSects.size()];
		double[] probGain_Mgt7pt7 = new double[subSects.size()];

		erf.updateForecast();
		
		for (int s=0; s<subSects.size(); s++) {
			td_Mgt6pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			log_Mgt6pt7_prob[s] = Math.log10(td_Mgt6pt7_prob[s]);

			td_Mgt7pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 7.7, s);
			log_Mgt7pt7_prob[s] = Math.log10(td_Mgt7pt7_prob[s]);
		}
		
		
		// parent section cum part mag prob dists
		Map<Integer, EvenlyDiscretizedFunc> parCumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
				erf, 5.0, 50, 0.1);
		Map<Integer, EvenlyDiscretizedFunc> parIncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
				erf, 5.05, 50, 0.1);
		
		// temporarily set the forecast as Poisson, to get long-term rates, & no background 
		erf.setProbabilityModelChoice(FSS_ProbabilityModels.POISSON);
		erf.getTimeSpan().setDuration(duration); // THIS IS NEEDED
		erf.updateForecast();
		
		// TI parent section cum part mag prob dists
		Map<Integer, EvenlyDiscretizedFunc> parTI_CumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
				erf, 5.0, 50, 0.1); 
		Map<Integer, EvenlyDiscretizedFunc> parTI_IncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
				erf, 5.05, 50, 0.1); 
		
		// make parent MPDs
		File parentMPD_Dir = new File(resourcesDir, "parentMPD_Dir");
		if(!parentMPD_Dir.exists()) parentMPD_Dir.mkdir();
		makeParentMPD_Plots(parCumPartMPD_Map,parIncrPartMPD_Map,
				parTI_CumPartMPD_Map, parTI_IncrPartMPD_Map, 
				parentNameID_Map, parentMPD_Dir);
		
			
		// These are for UCERF3 section indexing
		double[] ucerf3_Mgt6pt7_prob=null;
		double[] ucerf3_Mgt7pt7_prob=null;
		double[] ucerf3_recurInt=null;
		double[] ucerf3_timeSinceYrs = null;
		double[] ucerf3_normTimeSince = null;

		// These are for 2023 section indexing
		double[] td_Mgt6pt7_prob_ratio_ToU3 = new double[subSects.size()]; // size of 2023 model
		double[] td_Mgt7pt7_prob_ratio_ToU3 = new double[subSects.size()];
		double[] recurInt_ratioToU3 = new double[subSects.size()];
		double[] slipRateRatioToU3 = new double[subSects.size()];
		double[] normTimeSinceU3 = new double[subSects.size()]; //this one is for nshm23 subsection indexing
		double[] timeSinceYrsU3 = new double[subSects.size()]; //this one is for nshm23 subsection indexing
		
		// This gets the logic-tree-implied average RI distribution (cumulative) for each parent section
		Map<Integer, DiscretizedFunc> parSectAveRI_CumDistMap = getParentSectAveRI_CumDistFuncMap();

		
		// these are for possible U3 comparison plots for web page 
		String ri_FractileOnLogicTree_HistogramPrefix = "RI_FractileOnLogicTree_Histogram";
		String ucerf3_RI_FractileOnLogicTree_HistogramPrefix = "U3_RI_FractileOnLogicTree_Histogram";
		String ri_ratioToU3_HistogramPrefix = "RI_ratioToU3_Histogram";
		String ri_ratioToU3_RateWt_HistogramPrefix = "RI_ratioToU3_RateWt_Histogram";
		String ri_RatioVsSlipRateRatioScatterPlotPrefix = "RI_RatioVsSlipRateRatioScatterPlot";
		HashMap<Integer,Integer> nshmFromU3_ParentSectID_Map = null;
		HashMap<Integer,Integer> u3_from_nshm_ParentSectID_Map = null;
		Map<Integer, EvenlyDiscretizedFunc> u3_parCumPartMPD_Map = null;
		Map<Integer, EvenlyDiscretizedFunc> u3_parIncrPartMPD_Map = null;
		Map<Integer, EvenlyDiscretizedFunc> u3_parTI_IncrPartMPD_Map = null; 
		Map<Integer, EvenlyDiscretizedFunc> u3_parTI_CumPartMPD_Map = null; 
		HashMap<Integer,String> u3_parentNameID_Map = null;
//		Map<Integer, List<Integer>> u3_sectIDsForParentMap = null;
		if(ucerf3_erf !=null) {
			u3_parentNameID_Map = FaultSysSolERF_Calc.getParentSectNameFromID_Map(ucerf3_erf);
//			u3_sectIDsForParentMap = FaultSysSolERF_Calc.getSectionID_ListFromParentSectionID_Map(erf);

			// make parent id maps between models
			nshmFromU3_ParentSectID_Map = getParentSectionID_Mapping(ucerf3_erf.getSolution(),erf.getSolution());
			u3_from_nshm_ParentSectID_Map = new HashMap<Integer,Integer>();
			// make the opposite map
			for(int u3_id:nshmFromU3_ParentSectID_Map.keySet()) 
				u3_from_nshm_ParentSectID_Map.put(nshmFromU3_ParentSectID_Map.get(u3_id), u3_id);

			double[] u3_sectSlipRatesArray = ucerf3_erf.getSolution().getRupSet().getSlipRateForAllSections();
			double[] sectSlipRatesArray = sol.getRupSet().getSlipRateForAllSections();
			ucerf3_erf.updateForecast();
//System.out.println(ucerf3_erf.getTimeSpan().getStartTimeYear()+"\t"+ucerf3_erf.getTimeSpan().getDuration());
//System.out.println(ucerf3_erf.getProbabilityModel().getMetadataString());
//System.exit(0);
			int numU3_sections = ucerf3_erf.getSolution().getRupSet().getNumSections();
			ucerf3_Mgt6pt7_prob = new double[numU3_sections];
			ucerf3_Mgt7pt7_prob = new double[numU3_sections];
			ucerf3_recurInt = new double[numU3_sections];
			ucerf3_normTimeSince = new double[numU3_sections];
			ucerf3_timeSinceYrs = new double[numU3_sections];
			long presentTimeMillis = ucerf3_erf.getTimeSpan().getStartTimeInMillis();
			for (int s=0; s<numU3_sections; s++) {
				ucerf3_Mgt6pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(ucerf3_erf, 6.7, s);
				ucerf3_Mgt7pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(ucerf3_erf, 7.7, s);
				ucerf3_recurInt[s] = 1.0/ucerf3_erf.getSolution().calcTotParticRateForSect(s);
				long doleMillis = ucerf3_erf.getSolution().getRupSet().getFaultSectionData(s).getDateOfLastEvent();	
				if(doleMillis != Long.MIN_VALUE) {
					ucerf3_timeSinceYrs[s] = (double)(presentTimeMillis-doleMillis)*MILLISEC_TO_YEARS;
					ucerf3_normTimeSince[s] = ucerf3_timeSinceYrs[s]/ucerf3_recurInt[s];					
				}
				else {
					ucerf3_timeSinceYrs[s] = Double.NaN;
					ucerf3_normTimeSince[s] = Double.NaN;
				}
			}
			// these are current model to ucerf3 ratios (full number of sections)
			for(int s=0;s<subSects.size();s++) {
				td_Mgt6pt7_prob_ratio_ToU3[s] = Double.NaN;
				td_Mgt7pt7_prob_ratio_ToU3[s] = Double.NaN;
				recurInt_ratioToU3[s] = Double.NaN;
				slipRateRatioToU3[s] = Double.NaN;
				normTimeSinceU3[s] = Double.NaN;
				timeSinceYrsU3[s] = Double.NaN;
			}
			HistogramFunction ri_ratioToU3_Histogram = new HistogramFunction(0.05,50,0.1);
			HistogramFunction ri_ratioToU3_RateWt_Histogram = new HistogramFunction(0.05,50,0.1);
			HistogramFunction ri_FractileOnLogicTree_Histogram = new HistogramFunction(0.0,21,0.05);
			HistogramFunction ucerf3_RI_FractileOnLogicTree_Histogram = new HistogramFunction(0.0,21,0.05);
			DiscretizedFunc[] sectRI_CumDistArray = getNSHM23_WUS_SectRI_CumDistFuncArray();			
			
			// get section mapping
			HashMap<Integer,Integer> nshmFromU3_SectID_Map = getSubsectionID_Mapping(ucerf3_erf.getSolution(),erf.getSolution()); // FINISH THIS
			for(int u3_ID:nshmFromU3_SectID_Map.keySet()) {
				int nshmID = nshmFromU3_SectID_Map.get(u3_ID);
//String name1 = ucerf3_erf.getSolution().getRupSet().getFaultSectionData(u3_ID).getName();
//String name2 = erf.getSolution().getRupSet().getFaultSectionData(nshmID).getName();
//System.out.println(name1+"\t"+name2);
				if(ucerf3_Mgt6pt7_prob[u3_ID]>0.0)
					td_Mgt6pt7_prob_ratio_ToU3[nshmID] = td_Mgt6pt7_prob[nshmID]/ucerf3_Mgt6pt7_prob[u3_ID];
				else
					td_Mgt6pt7_prob_ratio_ToU3[nshmID] = Double.NaN;
				if(ucerf3_Mgt7pt7_prob[u3_ID]>0.0)
					td_Mgt7pt7_prob_ratio_ToU3[nshmID] = td_Mgt7pt7_prob[nshmID]/ucerf3_Mgt7pt7_prob[u3_ID];
				else
					td_Mgt7pt7_prob_ratio_ToU3[nshmID] = Double.NaN;
				recurInt_ratioToU3[nshmID] = recurInt[nshmID]/ucerf3_recurInt[u3_ID];
				slipRateRatioToU3[nshmID] = sectSlipRatesArray[nshmID]/u3_sectSlipRatesArray[u3_ID];
				timeSinceYrsU3[nshmID] = ucerf3_timeSinceYrs[u3_ID];
				normTimeSinceU3[nshmID] = ucerf3_normTimeSince[u3_ID];
				if(recurInt_ratioToU3[nshmID]<ri_ratioToU3_Histogram.getMaxX()) {
					ri_ratioToU3_Histogram.add(recurInt_ratioToU3[nshmID], 1.0);
					ri_ratioToU3_RateWt_Histogram.add(recurInt_ratioToU3[nshmID], 1.0/recurInt[nshmID]); // wt is 2023 rate
				}
				else {
					ri_ratioToU3_Histogram.add(ri_ratioToU3_Histogram.getMaxX(), 1.0);
					ri_ratioToU3_RateWt_Histogram.add(ri_ratioToU3_Histogram.getMaxX(), 1.0/recurInt[nshmID]); // wt is 2023 rate
				}
				// get logic tree fractile for recurInt[nshmID]
				double fractile = sectRI_CumDistArray[nshmID].getInterpolatedY(recurInt[nshmID]);
				ri_FractileOnLogicTree_Histogram.add(fractile,1.0);
				
				if(ucerf3_recurInt[u3_ID]<sectRI_CumDistArray[nshmID].getMinX()) // below all NSHM 2023 values
					ucerf3_RI_FractileOnLogicTree_Histogram.add(0, 1.0);
				else if(ucerf3_recurInt[u3_ID]>sectRI_CumDistArray[nshmID].getMaxX()) // above all NSHM 2023 values
					ucerf3_RI_FractileOnLogicTree_Histogram.add(1.0, 1.0);
				else {
					fractile = sectRI_CumDistArray[nshmID].getInterpolatedY(ucerf3_recurInt[u3_ID]);
					ucerf3_RI_FractileOnLogicTree_Histogram.add(fractile,1.0);					
				}
			}
			
			// plot RI ratios vs slip-rate ratios
			makeRI_RatioVsSlipRateRatioScatterPlot(recurInt_ratioToU3, slipRateRatioToU3, 
					resourcesDir, ri_RatioVsSlipRateRatioScatterPlotPrefix);
			
			// plot the supraRI_FractileOnLogicTree_Histogram
			ri_FractileOnLogicTree_Histogram.setName(ri_FractileOnLogicTree_HistogramPrefix);
			ri_FractileOnLogicTree_Histogram.setInfo("Mean = "+ri_FractileOnLogicTree_Histogram.computeMean()+
					"\nCOV = "+ri_FractileOnLogicTree_Histogram.computeCOV());
			ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
			funcs.add(ri_FractileOnLogicTree_Histogram);
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
			Range yAxisRange = new Range(0, ri_FractileOnLogicTree_Histogram.getMaxY()*1.1);
			ProbModelsPlottingUtils.writeAndOrPlotFuncs(funcs,plotChars,ri_FractileOnLogicTree_HistogramPrefix,"Fractile","Density",new Range(0,1),yAxisRange,
					false,false,7.0,6.0, new File(resourcesDir,ri_FractileOnLogicTree_HistogramPrefix), false);

			ucerf3_RI_FractileOnLogicTree_Histogram.setName(ucerf3_RI_FractileOnLogicTree_HistogramPrefix);
			ucerf3_RI_FractileOnLogicTree_Histogram.setInfo("Mean = "+ucerf3_RI_FractileOnLogicTree_Histogram.computeMean()+
					"\nCOV = "+ucerf3_RI_FractileOnLogicTree_Histogram.computeCOV());
			funcs = new ArrayList<XY_DataSet>();
			funcs.add(ucerf3_RI_FractileOnLogicTree_Histogram);
			yAxisRange = new Range(0, ucerf3_RI_FractileOnLogicTree_Histogram.getMaxY()*1.1);
			ProbModelsPlottingUtils.writeAndOrPlotFuncs(funcs,plotChars,ucerf3_RI_FractileOnLogicTree_HistogramPrefix,"Fractile","Density",new Range(0,1),yAxisRange,
					false,false,7.0,6.0, new File(resourcesDir,ucerf3_RI_FractileOnLogicTree_HistogramPrefix), false);

			// plot supraRI_ratioToU3_Histogram
			ri_ratioToU3_Histogram.setName(ri_ratioToU3_HistogramPrefix);
			ri_ratioToU3_Histogram.setInfo("Mean = "+ri_ratioToU3_Histogram.computeMean()+
					"\nCOV = "+ri_ratioToU3_Histogram.computeCOV()+"\n\nLast bin includes all ratios greater than maximum X");
			funcs = new ArrayList<XY_DataSet>();
			funcs.add(ri_ratioToU3_Histogram);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 2f, Color.RED));
			yAxisRange = new Range(0, ri_ratioToU3_Histogram.getMaxY()*1.1);
			ProbModelsPlottingUtils.writeAndOrPlotFuncs(funcs,plotChars,ri_ratioToU3_HistogramPrefix,"Ratio","Density",new Range(0,5),yAxisRange,
					false,false,7.0,6.0, new File(resourcesDir,ri_ratioToU3_HistogramPrefix), false);

			// plot supraRI_ratioToU3_Histogram - rate weighted
			ri_ratioToU3_RateWt_Histogram.normalizeToPDF();
			ri_ratioToU3_RateWt_Histogram.setName(ri_ratioToU3_RateWt_HistogramPrefix);
			ri_ratioToU3_RateWt_Histogram.setInfo("Mean = "+ri_ratioToU3_RateWt_Histogram.computeMean()+
					"\nCOV = "+ri_ratioToU3_RateWt_Histogram.computeCOV()+"\n\nLast bin includes all ratios greater than maximum X");
			funcs = new ArrayList<XY_DataSet>();
			funcs.add(ri_ratioToU3_RateWt_Histogram);
			yAxisRange = new Range(0, ri_ratioToU3_RateWt_Histogram.getMaxY()*1.1);
			ProbModelsPlottingUtils.writeAndOrPlotFuncs(funcs,plotChars,ri_ratioToU3_RateWt_HistogramPrefix,"Ratio","Density",new Range(0,5),yAxisRange,
					false,false,7.0,6.0, new File(resourcesDir,ri_ratioToU3_RateWt_HistogramPrefix), false);

			
			// Make parent part MFD comparison plots
			// parent section cum part mag prob dists
			u3_parIncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
					ucerf3_erf, 5.05, 50, 0.1);
			u3_parCumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
					ucerf3_erf, 5.0, 50, 0.1);
			
			// temporarily set the forecast as Poisson, to get long-term rates, & no background 

			FSS_ProbabilityModel probModelU3 = ucerf3_erf.getProbabilityModel();
			ucerf3_erf.setProbabilityModelChoice(FSS_ProbabilityModels.POISSON);
			ucerf3_erf.getTimeSpan().setDuration(duration); // THIS IS NEEDED
			ucerf3_erf.updateForecast();
			
			// TI parent section cum part mag prob dists
			u3_parTI_IncrPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartIncrMagProbDists(
					ucerf3_erf, 5.05, 50, 0.1); 
			u3_parTI_CumPartMPD_Map = FaultSysSolERF_Calc.calcParentSectSupraSeisPartCumMagProbDists(
					ucerf3_erf, 5.0, 50, 0.1); 
			// reset ERF to original state
			ucerf3_erf.setCustomProbabilityModel(probModelU3);
			ucerf3_erf.updateForecast();
			
			// make parent MPD plots
			File parentMPD_u3comp_Dir = new File(resourcesDir, "parentMPD_U3comparison_Dir");
			if(!parentMPD_u3comp_Dir.exists()) parentMPD_u3comp_Dir.mkdir();
			makeParentMPD_U3comparison_Plots(parIncrPartMPD_Map, parTI_IncrPartMPD_Map, 
					u3_parIncrPartMPD_Map, u3_parTI_IncrPartMPD_Map,
					nshmFromU3_ParentSectID_Map,
					parentNameID_Map, parentMPD_u3comp_Dir, true);
			makeParentMPD_U3comparison_Plots(parCumPartMPD_Map, parTI_CumPartMPD_Map, 
					u3_parCumPartMPD_Map, u3_parTI_CumPartMPD_Map,
					nshmFromU3_ParentSectID_Map,
					parentNameID_Map, parentMPD_u3comp_Dir, false);
		}
		



		String csv_sectDataString = "sectID,yrOfLast,yrsSince,normTimeSince,recurInt,prob_Mgt6pt7,poisProb_Mgt6pt7,"+
							"probGain_Mgt6pt7,prob_Mgt7pt7,poisProb_Mgt7pt7,probGain_Mgt7pt7,"+
							"sectName,parentSectID,parentSectName,DOLE_MappingType";
		if(ucerf3_erf !=null) 
			csv_sectDataString += ",recurInt_ratioToU3, prob_Mgt6pt7_ratio_ToU3,prob_Mgt7pt7_ratio_ToU3,slipRateRatioToU3,timeSinceYrsU3,normTimeSinceU3";
		csv_sectDataString += "\n";
		for (int s=0; s<subSects.size(); s++) {
			pois_Mgt6pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			probGain_Mgt6pt7[s] = td_Mgt6pt7_prob[s]/pois_Mgt6pt7_prob[s];
			pois_Mgt7pt7_prob[s] = FaultSysSolERF_Calc.calcParticipationProbForSect(erf, 7.7, s);
			probGain_Mgt7pt7[s] = td_Mgt7pt7_prob[s]/pois_Mgt7pt7_prob[s];
			String sectName = subSects.get(s).getSectionName();
			String modName = sectName.replace(",","_");
			String parentName = subSects.get(s).getParentSectionName();
			String modParentName=null;
			if(parentName != null)
				modParentName = parentName.replace(",","_");
			csv_sectDataString += s+","+(startYear-timeSince[s])+","+timeSince[s]+","+normTimeSince[s]+","+recurInt[s]+","+
					td_Mgt6pt7_prob[s]+","+pois_Mgt6pt7_prob[s]+","+probGain_Mgt6pt7[s]+","+
					td_Mgt7pt7_prob[s]+","+pois_Mgt7pt7_prob[s]+","+probGain_Mgt7pt7[s]+","+
					modName+","+subSects.get(s).getParentSectionId()+","+modParentName+","+sectMappingTypes[s];
			if(ucerf3_erf !=null) 
				csv_sectDataString += ","+recurInt_ratioToU3[s]+","+ td_Mgt6pt7_prob_ratio_ToU3[s]+","+td_Mgt7pt7_prob_ratio_ToU3[s]+","
						+slipRateRatioToU3[s]+","+timeSinceYrsU3[s]+","+normTimeSinceU3[s];
			csv_sectDataString += "\n";
		}
		
		// write out sectionOutputData.csv
		FileWriter fw2 = new FileWriter(new File(outputDir+"/sectionOutputData.csv"));
		fw2.write(csv_sectDataString); 
		fw2.close();
		
		// reset ERF to original state
		erf.setCustomProbabilityModel(probModel);
//		erf.getParameter(IncludeBackgroundParam.NAME).setValue(includeBackgroundOption);
		erf.updateForecast();

		
		// make parent section csv data file
		double[] magThreshVals = {5.0,6.7,7.7};
		Map<Integer, double[]> sectRateListForParentMap = FaultSysSolERF_Calc.getTotSectSupraSeisRateListForParentSectMap(erf);
		Map<Integer, double[]> aveNTS_AndFractForParentDataMap = FaultSysSolERF_Calc.getAveNormTimeSinceAndFractForParentSect(erf);
		Map<Integer, double[]> sectRateListForParentMapU3 = null;
		Map<Integer, double[]> aveNTS_AndFractForParentDataMapU3 = null;
		// header line
		String csv_parentDataSring = "parID,parName,meanRI,RI_fractile,aveNormTimeSince,fractWithDOLE,minMag";
		for(double mag:magThreshVals) {
			csv_parentDataSring += ",probMgt"+mag+",gainMgt"+mag;
		}
		if(ucerf3_erf !=null) {
			csv_parentDataSring += ",parID_U3,parNameU3,meanRI_U3,meanRI_ratioToU3,RI_fractile_U3,aveNormTimeSinceU3,fractWithDOLE_U3,minMagU3";
			for(double mag:magThreshVals)
				csv_parentDataSring += 	",probMgt"+mag+"_U3"+
										",gainMgt"+mag+"_U3"+
										",prob_Mgt"+mag+"_ratioToU3";
			sectRateListForParentMapU3 = FaultSysSolERF_Calc.getTotSectSupraSeisRateListForParentSectMap(ucerf3_erf);
			aveNTS_AndFractForParentDataMapU3 = FaultSysSolERF_Calc.getAveNormTimeSinceAndFractForParentSect(ucerf3_erf);
			
//testERF_DOLE(ucerf3_erf);
//System.out.println("HERE: "+aveNTS_AndFractForParentDataMapU3.get(286)[0]+"\t"+
//		aveNTS_AndFractForParentDataMapU3.get(286)[1]);			
//System.exit(0);

		}
		csv_parentDataSring += "\n";
		for(int id:parCumPartMPD_Map.keySet()) { // loop over 2023 parent IDs
			csv_parentDataSring += id+","+parentNameID_Map.get(id).replace(",","_");
			DescriptiveStatistics stats = new DescriptiveStatistics(sectRateListForParentMap.get(id));
			double meanRI = 1.0/stats.getMean(); // one over average rate
			double nts = aveNTS_AndFractForParentDataMap.get(id)[0];
			double fractDOLE = aveNTS_AndFractForParentDataMap.get(id)[1];
			double fractileForMean=Double.NaN;
			DiscretizedFunc cumRI_Dist = null;
			if(parSectAveRI_CumDistMap.containsKey(id)) {
				cumRI_Dist = parSectAveRI_CumDistMap.get(id);
				if(meanRI<cumRI_Dist.getMinX())
					fractileForMean=0;
				else if(meanRI>cumRI_Dist.getMaxX())
					fractileForMean=1;
				else
					fractileForMean = cumRI_Dist.getInterpolatedY(meanRI);				
			}
			double minMag = parIncrPartMPD_Map.get(id).getMinX_WithNonZeroRate();
			csv_parentDataSring += ","+meanRI+","+fractileForMean+","+nts+","+fractDOLE+","+minMag;
			for(double mag:magThreshVals) {
				csv_parentDataSring += ","+parCumPartMPD_Map.get(id).getY(mag)+","+
						(parCumPartMPD_Map.get(id).getY(mag)/parTI_CumPartMPD_Map.get(id).getY(mag));
			}
			if(ucerf3_erf !=null) {
				if(u3_from_nshm_ParentSectID_Map.containsKey(id)) {
					int u3_id = u3_from_nshm_ParentSectID_Map.get(id);
					csv_parentDataSring += ","+u3_id+","+u3_parentNameID_Map.get(u3_id).replace(",", " ");
					
					DescriptiveStatistics statsU3 = new DescriptiveStatistics(sectRateListForParentMapU3.get(u3_id));
					double meanRI_U3 = 1.0/statsU3.getMean();
					double ntsU3 = aveNTS_AndFractForParentDataMapU3.get(u3_id)[0];
					double fractDOLE_U3 = aveNTS_AndFractForParentDataMapU3.get(u3_id)[1];
					double fractileForU3_Mean=Double.NaN;
					if(cumRI_Dist != null) {
						if(meanRI_U3<cumRI_Dist.getMinX())
							fractileForU3_Mean=0;
						else if(meanRI_U3>cumRI_Dist.getMaxX())
							fractileForU3_Mean=1;
						else
							fractileForU3_Mean = cumRI_Dist.getInterpolatedY(meanRI_U3);						
					}
					double minMagU3 = u3_parIncrPartMPD_Map.get(u3_id).getMinX_WithNonZeroRate();

					csv_parentDataSring += ","+meanRI_U3+","+(meanRI/meanRI_U3)+","+fractileForU3_Mean+","+ntsU3+","+fractDOLE_U3+","+minMagU3;
					for(double mag:magThreshVals) {
						double probU3 = u3_parCumPartMPD_Map.get(u3_id).getY(mag);
						double gainU3 = probU3/u3_parTI_CumPartMPD_Map.get(u3_id).getY(mag);
						double ratioToU3 = parCumPartMPD_Map.get(id).getY(mag)/probU3;
						csv_parentDataSring += ","+probU3+","+gainU3+","+ratioToU3;
					}
				}
				else { // fill in NaNs
					for(int i=0;i<14;i++)
						csv_parentDataSring += ","+Double.NaN;
				}
			}
			csv_parentDataSring += "\n";
		}
		// write out parentSectionResultsData.csv
		FileWriter fw3 = new FileWriter(new File(outputDir+"/parentSectionResultsData.csv"));
		fw3.write(csv_parentDataSring); 
		fw3.close();

		
		// Get prob ratios with respect to reference dir
		// Get 5th column:
		double[] td_Mgt6pt7_prob_reference = readSectProbFromFile(new File(comparisonDir,"/sectionOutputData.csv"),5);
		if(td_Mgt6pt7_prob_reference.length != td_Mgt6pt7_prob.length)
			throw new RuntimeException("problem with comparison array length: "+td_Mgt6pt7_prob_reference.length+" vs "+td_Mgt6pt7_prob.length);
		double[] Mgt6pt7_prob_ratioToReference = new double[td_Mgt6pt7_prob.length];
		for(int s=0;s<td_Mgt6pt7_prob.length;s++) {
			Mgt6pt7_prob_ratioToReference[s] = td_Mgt6pt7_prob[s]/td_Mgt6pt7_prob_reference[s];
		}
		// Get 5th column:
		double[] td_Mgt7pt7_prob_reference = readSectProbFromFile(new File(comparisonDir,"/sectionOutputData.csv"),8);
		if(td_Mgt7pt7_prob_reference.length != td_Mgt7pt7_prob.length)
			throw new RuntimeException("problem with comparison array length");
		double[] Mgt7pt7_prob_ratioToReference = new double[td_Mgt7pt7_prob.length];
		for(int s=0;s<td_Mgt7pt7_prob.length;s++) {
			Mgt7pt7_prob_ratioToReference[s] = td_Mgt7pt7_prob[s]/td_Mgt7pt7_prob_reference[s];
		}


//		System.out.println("COMP HERE: "+td_Mgt6pt7_prob[0]+"\t"+td_Mgt6pt7_prob_reference[0]+"\t"+(td_Mgt6pt7_prob[0]/td_Mgt6pt7_prob_reference[0]));


		
		
// System.out.println("Done with ERF stuff)");
		
		mapMaker.clearSectColors();
		CPT riCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6).reverse();
		CPT logTS_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1,6);
		CPT normTimeSince_CPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0,3);
		CPT probGainCPT = getProbGainCPT();
		// System.out.println("HERE probGainCPT\n"+probGainCPT.toString());
		CPT logProbCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-6,1);

		
		String logriPrefix = "log_recurrence_interval";
		String ntsPrefix = "norm_time_since";
		String log_tsPrefix = "log_time_since";
		String probGainPrefix6pt7 = "mGT6pt7_ProbGain";
		String log_probPrefix6pt7 = "mGT6pt7_logProb";
		String probReferenceRatioPrefix6pt7 = "mGT6pt7_ProbRatioToReference";
		String probGainPrefix7pt7 = "mGT7pt7_ProbGain";
		String log_probPrefix7pt7 = "mGT7pt7_logProb";
		String probReferenceRatioPrefix7pt7 = "mGT7pt7_ProbRatioToReference";
		String mgt6pt7_prob_ratio_ToU3_Prefix = "mgt6pt7_prob_ratio_ToUCERF3";
		String mgt7pt7_prob_ratio_ToU3_Prefix = "mgt7pt7_prob_ratio_ToUCERF3";
		String supraRI_ratioToU3_Prefix = "supraRI_ratioToUCERF3";

		
		mapMaker.plotSectScalars(logRecurInt, riCPT, "Log10 Recurrence Interval (years)");
		mapMaker.plot(resourcesDir, logriPrefix, " ");
		mapMaker.plotSectScalars(normTimeSince, normTimeSince_CPT, "Normalized Time Since Last Event");
		mapMaker.plot(resourcesDir, ntsPrefix, " ");
		mapMaker.plotSectScalars(logTimeSince, logTS_CPT, "Log10 Time Since Last Event (from "+startYear+")");
		mapMaker.plot(resourcesDir, log_tsPrefix, " ");
		mapMaker.plotSectScalars(probGain_Mgt6pt7, probGainCPT, "M≥6.7 Prob. Gain");
		mapMaker.plot(resourcesDir, probGainPrefix6pt7, " ");
		mapMaker.plotSectScalars(log_Mgt6pt7_prob, logProbCPT, "log10 M≥6.7 Prob.");
		mapMaker.plot(resourcesDir, log_probPrefix6pt7, " ");
		mapMaker.plotSectScalars(Mgt6pt7_prob_ratioToReference, probGainCPT, "M≥6.7 Prob. Ratio to Reference");
		mapMaker.plot(resourcesDir, probReferenceRatioPrefix6pt7, " ");
		mapMaker.plotSectScalars(probGain_Mgt7pt7, probGainCPT, "M≥7.7 Prob. Gain");
		mapMaker.plot(resourcesDir, probGainPrefix7pt7, " ");
		mapMaker.plotSectScalars(log_Mgt7pt7_prob, logProbCPT, "log10 M≥7.7 Prob.");
		mapMaker.plot(resourcesDir, log_probPrefix7pt7, " ");
		mapMaker.plotSectScalars(Mgt7pt7_prob_ratioToReference, probGainCPT, "M≥7.7 Prob. Ratio to Reference");
		mapMaker.plot(resourcesDir, probReferenceRatioPrefix7pt7, " ");
		if(ucerf3_erf !=null) {
//			org.opensha.commons.data.region.CaliforniaRegions.RELM_TESTING
			Region region = new CaliforniaRegions.RELM_TESTING();
			GeographicMapMaker u3MapMaker = new GeographicMapMaker(region, subSects);
			u3MapMaker.setWriteGeoJSON(true);
			u3MapMaker.setDefaultPlotWidthPixels(1200);
		
			u3MapMaker.plotSectScalars(td_Mgt6pt7_prob_ratio_ToU3, probGainCPT, "M≥6.7 Prob. Ratio to UCERF3");
			u3MapMaker.plot(resourcesDir, mgt6pt7_prob_ratio_ToU3_Prefix, " ");
			u3MapMaker.plotSectScalars(td_Mgt7pt7_prob_ratio_ToU3, probGainCPT, "M≥7.7 Prob. Ratio to UCERF3");
			u3MapMaker.plot(resourcesDir, mgt7pt7_prob_ratio_ToU3_Prefix, " ");
			u3MapMaker.plotSectScalars(recurInt_ratioToU3, probGainCPT, "Recurrence Interval Ratio to UCERF3");
			u3MapMaker.plot(resourcesDir, supraRI_ratioToU3_Prefix, " ");			
		}

		double[] magForRupArray = new double[erf.getNumFaultSystemSources()];
		double[] aperForRupArray = new double[erf.getNumFaultSystemSources()];
		double[] aveSlipRateForRupArray = new double[erf.getNumFaultSystemSources()];
		
		// make the rupture data file (e.g., for verification) if U3 methodology (WG02 does not yet have getDebugString())
		if (probModel instanceof UCERF3_ProbabilityModel) {
			String headerString = "";
			headerString += "srcIndex,";	// added here
			headerString += "longTermRate,";	// added here
			headerString += "gainTest,";	// added here
			headerString += "numRup,";	// added here
			// from ProbabilityModelsCalc
			headerString += "fltSysRupIndex,";
			headerString += "probGain,";
			headerString += "condProb,";
			headerString += "rupMag,";
			headerString += "aveCondRecurInterval,";
			headerString += "aveCondRecurIntervalWhereUnknown,";		
			headerString += "aveTimeSinceLastWhereKnownYears,";
			headerString += "aveNormTimeSinceLastEventWhereKnown,";
			headerString += "totRupArea,";
			headerString += "totRupAreaWithDateOfLast,";
			headerString += "fractRupAreaWithDateOfLast,";
			headerString += "aper,";
			headerString += "numSubsectForRup,";
			headerString += "extrapolated,";
			headerString += "rupAveSlipRate,";
			headerString += "srcName,";
			
			FileWriter fw_rups;
			FileWriter fw_rups_test=null;
			try {
				fw_rups = new FileWriter(new File(outputDir+"/ruptureDataFile.csv"));
				fw_rups.write(headerString+"\n"); 
				if(testParentFaultID>=0) {
					fw_rups_test = new FileWriter(new File(outputDir+"/ruptureDataFile_"+testParentFaultName+".csv"));
					fw_rups_test.write(headerString+"\n"); 
				}
				((UCERF3_ProbabilityModel)probModel).setSaveDebugInfo(true);
				for(int s=0;s<erf.getNumFaultSystemSources();s++) {
					if(erf.getSource(s).isSourcePoissonian())
						throw new RuntimeException("source is poissonian: "+s+"\t"+erf.getSource(s).getName());
					int fsrIndex = erf.getFltSysRupIndexForSource(s);
					double probGainTest = probModel.getProbabilityGain(fsrIndex, erf.getTimeSpan().getStartTimeInMillis(), duration);		
					double testRate=0;  // this should equal fss rate time gain
					for(int r=0;r<erf.getSource(s).getNumRuptures();r++) {
						double prob = erf.getSource(s).getRupture(r).getProbability();
						testRate+=prob/duration;
					}
					testRate /= probGainTest;
					double rupAveSlipRate = rupSet.getAveSlipRateForRup(fsrIndex);
					String srcName = erf.getSource(s).getName();
					String calcDebugString = ((UCERF3_ProbabilityModel)probModel).getDebugString();
					String[] strParseArray = StringUtils.split(calcDebugString,",");
					magForRupArray[s] = Double.parseDouble(strParseArray[3]);
					aperForRupArray[s] = Double.parseDouble(strParseArray[11]);
					aveSlipRateForRupArray[s] = rupAveSlipRate;
					String line = s+","+sol.getRateForRup(fsrIndex)+","+probGainTest+","+erf.getSource(s).getNumRuptures()+
							","+calcDebugString+","+rupAveSlipRate+
							","+srcName.replace(",", "")+"\n";
					double testRateNormDiff = Math.abs(testRate-sol.getRateForRup(fsrIndex))/testRate;
					if(probGainTest > 1e-16 && testRateNormDiff>1e-6) {
						throw new RuntimeException("long-term rate discrepancy for srcID="+s+"; "+((float)testRate)+" vs "+((float)sol.getRateForRup(fsrIndex))+"; probGainTest="+probGainTest);
					}
					fw_rups.write(line); 
					if(testParentFaultID>=0 && rupSet.getParentSectionsForRup(fsrIndex).contains(testParentFaultID)) {
						fw_rups_test.write(line);
					}
				}
				fw_rups.close();
				if(testParentFaultID>=0)
					fw_rups_test.close();
				
				// write section data
				((UCERF3_ProbabilityModel)probModel).writeCurrentSectDataToCSV_File(outputDir, "sectionInputData.csv");
				
				// Make aper scatter plots
				makeRupAperScatterPlots(aperForRupArray, magForRupArray, 
						aveSlipRateForRupArray, resourcesDir);
			
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		
		// Make solution report
		
		String relPath = resourcesDir.getName();
		List<String> lines = new ArrayList<>();
		lines.add("# "+titleString);
		lines.add(infoString);
		lines.add(getExplanationText(mappingAlg, dataToInclude, probModel));
		lines.add("");
		
		lines.add("ERF Time-Dependent Parameter Values:\n");
		lines.add(erfParamMetadataString);
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(TOC)](#table-of-contents)_";
		
		lines.add("## DOLE Mapping Type");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![DOLE Mapping Type]("+relPath+"/dole_mapping_type.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_mapping_type.geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");

		
		lines.add("## Year of Last Event");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Year of Last Event]("+relPath+"/dole_year_map.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map.geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Log10 Recurrence Interval");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Recurrence Interval]("+relPath+"/"+logriPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+logriPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Normalized Time Since Last Event");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Normalized Time Since Last Event]("+relPath+"/"+ntsPrefix+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+ntsPrefix+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Log10 Probability for M&ge;6.7");  
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Probability for M&ge;6.7]("+relPath+"/"+log_probPrefix6pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+log_probPrefix6pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Probability Gain for M&ge;6.7");  
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Probability Gain for M&ge;6.7]("+relPath+"/"+probGainPrefix6pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probGainPrefix6pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Ratio of M&ge;6.7 Probability to that of "+comparisonDirName);  
		String compLinkString = "[../"+comparisonDirName+"](../"+comparisonDirName+")";
		lines.add("Comparison model is in the directory: "+compLinkString); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![M&ge;6.7 Probability Reference Ratio]("+relPath+"/"+probReferenceRatioPrefix6pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probReferenceRatioPrefix6pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		
		lines.add("## Log10 Probability for M&ge;7.7");  
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Log10 Probability for M&ge;7.7]("+relPath+"/"+log_probPrefix7pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+log_probPrefix7pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Probability Gain for M&ge;7.7");  
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![Probability Gain for M&ge;7.7]("+relPath+"/"+probGainPrefix7pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probGainPrefix7pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		lines.add("## Ratio of M&ge;7.7 Probability to that of "+comparisonDirName);  
		String compLinkString7pt7 = "[../"+comparisonDirName+"](../"+comparisonDirName+")";
		lines.add("Comparison model is in the directory: "+compLinkString7pt7); lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("![M&ge;7.7 Probability Reference Ratio]("+relPath+"/"+probReferenceRatioPrefix7pt7+".png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+probReferenceRatioPrefix7pt7+".geojson"));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add(topLink); lines.add("");
		
		if(ucerf3_erf !=null) {
			lines.add("## Ratio of M&ge;6.7 Probability to that of UCERF3");
			lines.add("Using same probability model as above but UCERF3 date of last event."); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![M&ge;6.7 Probability Ratio to UCERF3]("+relPath+"/"+mgt6pt7_prob_ratio_ToU3_Prefix+".png)");
			table.finalizeLine().initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+mgt6pt7_prob_ratio_ToU3_Prefix+".geojson"));
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");

			lines.add("## Ratio of M&ge;7.7 Probability to that of UCERF3");  
			lines.add("Using same probability model as above but UCERF3 date of last event."); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![M&ge;7.7 Probability Ratio to UCERF3]("+relPath+"/"+mgt7pt7_prob_ratio_ToU3_Prefix+".png)");
			table.finalizeLine().initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+mgt7pt7_prob_ratio_ToU3_Prefix+".geojson"));
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");
			
			lines.add("## Ratio of 2023 to UCERF3 Recurrence Interval");  
			lines.add("Change in recurrence interval relative to UCERF3."); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![RI Change Since UCERF3]("+relPath+"/"+supraRI_ratioToU3_Prefix+".png)");
			table.finalizeLine().initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+supraRI_ratioToU3_Prefix+".geojson"));
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");

			lines.add("## 2023 to UCERF3 Section RI Ratio Histogram");  
			lines.add("Histogram of 2023 to UCERF3 section recurrence intervals"); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![2023 vs UCERF3 RI ratio histogram]("+relPath+"/"+ri_ratioToU3_HistogramPrefix+".png)");
			table.finalizeLine().initNewLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");
			
			lines.add("## Rate Weighted 2023 to UCERF3 Section RI Ratio Histogram");  
			lines.add("Same as above, but contribution of each section to histogram is rate weighted (1.0/RI)"); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![2023 vs UCERF3 RI ratio histogram - rate weighted]("+relPath+"/"+ri_ratioToU3_RateWt_HistogramPrefix+".png)");
			table.finalizeLine().initNewLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");
			
			lines.add("## Section RI Ratio vs Slip-Rate Ratio");  
			lines.add("This is a scatter plot of section RI change (2023/UCERF3) versus slip-rate change."); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![2023 vs UCERF3 RI ratio versus slip-rate ratio]("+relPath+"/"+ri_RatioVsSlipRateRatioScatterPlotPrefix+".png)");
			table.finalizeLine().initNewLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");

			lines.add("## UCERF3 RI Fractile on 2023 Logic Tree");  
			lines.add("Where the UCERF3 recurrence interval lands on the 2023 logic-tree distribution."); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("![UCERF3 fractile]("+relPath+"/"+ucerf3_RI_FractileOnLogicTree_HistogramPrefix+".png)");
			table.finalizeLine().initNewLine();
			lines.addAll(table.build());
			lines.add(topLink); lines.add("");
		}
		
		lines.add("## Parent Section Magnitude Probability Distributions");  
		lines.add("These are participation distributions, meaning the probability of one or more events touching any part of the parent fault."); lines.add("");
		lines.add("Parent section MPDs: [resources/parentMPD_Dir](resources/parentMPD_Dir) (both incremenatal and cumulative plots, including comparisons to Poisson)");
		if(ucerf3_erf !=null) {
			lines.add("");
			lines.add("Comparisons to UCERF3: [resources/parentMPD_U3comparison_Dir](resources/parentMPD_U3comparison_Dir) (incremenatal plots only)");
		}
		lines.add("");
		lines.add(topLink); lines.add("");

		lines.add("## Data Files");  
		String u3_String = "";
		if(ucerf3_erf !=null)
			u3_String = " (including comparisons with UCERF3)";
		lines.add("Section data plotted above: [sectionOutputData.csv](sectionOutputData.csv)"+u3_String);
		lines.add("");
		lines.add("Parent-section data (aggregated over subsections): "+"[parentSectionResultsData.csv](parentSectionResultsData.csv)"+u3_String);

		lines.add("");
		lines.add("The above plots as well as others can be found here: [resources](resources)");

		if (probModel instanceof UCERF3_ProbabilityModel) {
			lines.add("");
			lines.add("Fault section input data: [sectionInputData.csv](sectionInputData.csv)");
			lines.add("");
			lines.add("Fault ruptures data: [ruptureDataFile.csv](ruptureDataFile.csv)");
			lines.add("");
			lines.add("The sectionInputData.csv and ruptureDataFile.csv can be used to verify rupture probabilities.  "+
					"Note that each rupture in ruptureDataFile.csv represents a fault-system-solution rupture, "+
					"or a fault based source in the ERF (not a rupture within the latter). The OpenSHA probability calculation "+
					"can be found in this class: org.opensha.sha.earthquake.faultSysSolution.erf.td.UCERF3_ProbabilityModel.getProbability()");
		}

		lines.add("");
		lines.add("Fault system solution file: [../fullPrefUS_FSS.zip](../fullPrefUS_FSS.zip)");
		lines.add("");
		lines.add(topLink); lines.add("");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		lines.add("");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}

	
	public static void makeRI_RatioVsSlipRateRatioScatterPlot(double[] riRatios, double[] slipRatRatios, 
			File resourcesDir, String filenamePrefix) {
		
		File fileNamePrefix1 = new File(resourcesDir, filenamePrefix);
		
		DefaultXY_DataSet data = new DefaultXY_DataSet();
		for(int s=0;s<riRatios.length;s++)
			if(!Double.isNaN(riRatios[s]))
				data.set(slipRatRatios[s],riRatios[s]);
		data.setName("RI_RatioVsSlipRateRatio");

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(data);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 0.5f, Color.RED));

		Range xAxisRange = new Range(0.01,100);
		Range yAxisRange = xAxisRange;
		boolean logX = true;
		boolean logY = true;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean integerYaxisTickLabeIncrements = false;

		PlotSpec spec = new PlotSpec(funcs, plotChars, "NSHM23 vs UCERF3 Sections", "Slip Rate Ratio", "RI Ratio");
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(xAxisRange, yAxisRange);
		gp.setTickLabelFontSize(16);
		gp.setAxisLabelFontSize(22);
		gp.setPlotLabelFontSize(16);
		gp.setBackgroundColor(Color.WHITE);
		gp.drawGraphPanel(spec, logX, logY); // spec can be a list
		int width = (int)(widthInches*72.);
		int height = (int)(heightInches*72.);
		gp.getChartPanel().setSize(width, height); 

		if(integerYaxisTickLabeIncrements)
			gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			
//			XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//			gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
//
		try {
			gp.saveAsPNG(fileNamePrefix1+".png");
			gp.saveAsPDF(fileNamePrefix1+".pdf");
			gp.saveAsTXT(fileNamePrefix1+".txt");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	
	public static void makeRupAperScatterPlots(double[] aperForRupArray, double[] magForRupArray, 
			double[] aveSlipRateForRupArray, File resourcesDir) {
		

		DefaultXY_DataSet aperVsMagFunc = new DefaultXY_DataSet(magForRupArray,aperForRupArray);
		aperVsMagFunc.setName("aperVsMagFunc");
		File fileNamePrefix1 = new File(resourcesDir, "aperVsMagFunc");
		ArrayList<XY_DataSet> funcs1 = new ArrayList<XY_DataSet>();
		funcs1.add(aperVsMagFunc);
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 0.5f, Color.RED));

		Range xAxisRange = null;
		Range yAxisRange = new Range (0.1,0.55);
		boolean logX = false;
		boolean logY = false;
		double widthInches = 7.0; // inches
		double heightInches = 6.0; // inches
		boolean integerYaxisTickLabeIncrements = false;

		PlotSpec spec = new PlotSpec(funcs1, plotChars, "", "Rup Magnitude", "Aperiodicity/COV");
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setUserBounds(xAxisRange, yAxisRange);
		gp.setTickLabelFontSize(16);
		gp.setAxisLabelFontSize(22);
		gp.setPlotLabelFontSize(16);
		gp.setBackgroundColor(Color.WHITE);
		gp.drawGraphPanel(spec, logX, logY); // spec can be a list
		int width = (int)(widthInches*72.);
		int height = (int)(heightInches*72.);
		gp.getChartPanel().setSize(width, height); 

		if(integerYaxisTickLabeIncrements)
			gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			
//			XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//			gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
//
		try {
			gp.saveAsPNG(fileNamePrefix1+".png");
			gp.saveAsPDF(fileNamePrefix1+".pdf");
			gp.saveAsTXT(fileNamePrefix1+".txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		double[] slipRateMilimeters = new double[aveSlipRateForRupArray.length];
		for(int i=0;i<aveSlipRateForRupArray.length;i++)
			slipRateMilimeters[i]=1000*aveSlipRateForRupArray[i];
		DefaultXY_DataSet aperVsAveSlipRateFunc = new DefaultXY_DataSet(slipRateMilimeters,aperForRupArray);
		aperVsAveSlipRateFunc.setName("aperVsAveSlipRateFunc");
		File fileNamePrefix2 = new File(resourcesDir, "aperVsAveSlipRateFunc");
		ArrayList<XY_DataSet> funcs2 = new ArrayList<XY_DataSet>();
		funcs2.add(aperVsAveSlipRateFunc);

		logX = true;

		PlotSpec spec2 = new PlotSpec(funcs2, plotChars, "", "Rup Ave Slip Rate (mm/yr)", "Aperiodicity/COV");
		
		gp = new HeadlessGraphPanel();
		gp.setUserBounds(xAxisRange, yAxisRange);
		gp.setTickLabelFontSize(16);
		gp.setAxisLabelFontSize(22);
		gp.setPlotLabelFontSize(16);
		gp.setBackgroundColor(Color.WHITE);
		gp.drawGraphPanel(spec2, logX, logY); // spec can be a list
		gp.getChartPanel().setSize(width, height); 

		if(integerYaxisTickLabeIncrements)
			gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			
//			XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//			gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
//
		try {
			gp.saveAsPNG(fileNamePrefix2+".png");
			gp.saveAsPDF(fileNamePrefix2+".pdf");
			gp.saveAsTXT(fileNamePrefix2+".txt");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		DefaultXY_DataSet magVsAveSlipRateFunc = new DefaultXY_DataSet(slipRateMilimeters,magForRupArray);
		magVsAveSlipRateFunc.setName("magVsAveSlipRateFunc");
		File fileNamePrefix3 = new File(resourcesDir, "magVsAveSlipRateFunc");
		ArrayList<XY_DataSet> funcs3 = new ArrayList<XY_DataSet>();
		funcs3.add(magVsAveSlipRateFunc);

		logX = true;

		PlotSpec spec3 = new PlotSpec(funcs3, plotChars, "", "Rup Ave Slip Rate (mm/yr)", "Rup Magnitude");
		
		gp = new HeadlessGraphPanel();
		gp.setUserBounds(xAxisRange, new Range(5,9.5));
		gp.setTickLabelFontSize(16);
		gp.setAxisLabelFontSize(22);
		gp.setPlotLabelFontSize(16);
		gp.setBackgroundColor(Color.WHITE);
		gp.drawGraphPanel(spec3, logX, logY); // spec can be a list
		gp.getChartPanel().setSize(width, height); 

		if(integerYaxisTickLabeIncrements)
			gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			
//			XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//			gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
//
		try {
			gp.saveAsPNG(fileNamePrefix3+".png");
			gp.saveAsPDF(fileNamePrefix3+".pdf");
			gp.saveAsTXT(fileNamePrefix3+".txt");
		} catch (IOException e) {
			e.printStackTrace();
		}



	}
	
	private static void makeParentMPD_Plots(Map<Integer, EvenlyDiscretizedFunc> parCumPartMPD_Map,
			Map<Integer, EvenlyDiscretizedFunc> parIncrPartMPD_Map,
			Map<Integer, EvenlyDiscretizedFunc> parTI_CumPartMPD_Map, 
			Map<Integer, EvenlyDiscretizedFunc> parTI_IncrPartMPD_Map, 
			HashMap<Integer,String> perentNameID_Map, File parentMPD_Dir) {
		
		for(int parID:perentNameID_Map.keySet()) {
			EvenlyDiscretizedFunc parCumMPD = parCumPartMPD_Map.get(parID);
			EvenlyDiscretizedFunc parCumTI_MPD = parTI_CumPartMPD_Map.get(parID);
			EvenlyDiscretizedFunc parIncrMPD = parIncrPartMPD_Map.get(parID);
			EvenlyDiscretizedFunc parIncrTI_MPD = parTI_IncrPartMPD_Map.get(parID);
			String parName = perentNameID_Map.get(parID);
			String parentMFD_FileNamePrefix = parName.replace(" ","").replace(",","_");
			parCumMPD.setName( "TD Cum. MPD");
			parCumTI_MPD.setName(" TI Cum. MPD");
			parIncrMPD.setName(" TD Incr. MPD");
			parIncrTI_MPD.setName(" TI Incr. MFD");
			parCumMPD.setInfo(parName + " TD Cumumlative Part Mag Prob Dist");
			parCumTI_MPD.setInfo(parName + " TI Cumulative Part Mag Prob Dist");
			parIncrMPD.setInfo(parName + " TD Incramental Part Mag Prob Dist");
			parIncrTI_MPD.setInfo(parName + " TI Incramental Part Mag Prob Dist");
			
			// 
			double maxY = Math.max(parCumTI_MPD.getMaxY(), parCumMPD.getMaxY())*1.2;
			
			double minY = Double.MAX_VALUE;
			double y1=0, y2=0;
			for(int i=parCumMPD.size()-1;i>-1;i--) { // start from end and move backward
				y1 = parCumMPD.getY(i);
				y2 = parCumTI_MPD.getY(i);
				if(y1==0 && y2==0)
					continue;
				if(y1>0 && y2==0)
					minY=y1;
				else if(y1==0 && y2>0)
					minY=y2;
				else // both are greater than zero; take the minimum
					minY = Math.min(y1,y2);
				break;
			}
			minY /= 1.2;
			
			ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
			funcs.add(parIncrMPD);
			funcs.add(parIncrTI_MPD);
			funcs.add(parCumMPD);
			funcs.add(parCumTI_MPD);
			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.RED));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));

			String plotName = parName;
			String xAxisLabel = "Magnitude";
			String yAxisLabel = "Participation Prob";
			Range xAxisRange = null;
			Range yAxisRange = new Range(minY,maxY);
			boolean logX = false;
			boolean logY = true;
			double widthInches = 7; // inches
			double heightInches = 6; // inches
			File fileNamePrefix = new File(parentMPD_Dir,parentMFD_FileNamePrefix+"_cumMagProbDists");
			
			PlotSpec spec = new PlotSpec(funcs, plotChars, plotName, xAxisLabel, yAxisLabel);
			spec.setLegendInset(true);
			
				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				gp.setUserBounds(xAxisRange, yAxisRange);
				gp.setTickLabelFontSize(16);
				gp.setAxisLabelFontSize(22);
				gp.setPlotLabelFontSize(16);
				gp.setBackgroundColor(Color.WHITE);
				gp.drawGraphPanel(spec, logX, logY); // spec can be a list
				int width = (int)(widthInches*72.);
				int height = (int)(heightInches*72.);
				gp.getChartPanel().setSize(width, height); 

//				if(integerYaxisTickLabeIncrements)
//					gp.getChartPanel().getChart().getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
				
//				XYTextAnnotation annotation = new XYTextAnnotation("here",xAxisRange.getCentralValue(),yAxisRange.getCentralValue());
//				gp.getChartPanel().getChart().getXYPlot().addAnnotation(annotation);	
	//
				try {
					gp.saveAsPNG(fileNamePrefix+".png");
					gp.saveAsPDF(fileNamePrefix+".pdf");
					gp.saveAsTXT(fileNamePrefix+".txt");
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}
	
	
	private static void makeParentMPD_U3comparison_Plots(
			Map<Integer, EvenlyDiscretizedFunc> parPartMPD_Map,
			Map<Integer, EvenlyDiscretizedFunc> parTI_PartMPD_Map, 
			Map<Integer, EvenlyDiscretizedFunc> u3_parPartMPD_Map,
			Map<Integer, EvenlyDiscretizedFunc> u3_parTI_PartMPD_Map,
			HashMap<Integer,Integer> nshmFromU3_ParentSectID_Map,
			HashMap<Integer,String> perentNameID_Map, File parentMPD_Dir,
			boolean incrementalMFD) {
		
		String mfdTypeString = "Cumulative";
		if(incrementalMFD)
			mfdTypeString = "Incramental";

		for(int u3_parID:nshmFromU3_ParentSectID_Map.keySet()) {
			int parID = nshmFromU3_ParentSectID_Map.get(u3_parID);
			EvenlyDiscretizedFunc parIncrMPD = parPartMPD_Map.get(parID);
			EvenlyDiscretizedFunc parIncrTI_MPD = parTI_PartMPD_Map.get(parID);
			EvenlyDiscretizedFunc u3_parIncrMPD = u3_parPartMPD_Map.get(u3_parID);
			EvenlyDiscretizedFunc u3_parIncrTI_MPD = u3_parTI_PartMPD_Map.get(u3_parID);
			String parName = perentNameID_Map.get(parID);
			String parentMFD_FileNamePrefix = parName.replace(" ","").replace(",","_");
			parIncrMPD.setName("NSHM23 TD ");
			parIncrTI_MPD.setName("NSHM23 TI");
			u3_parIncrMPD.setName("UCERF3 TD");
			u3_parIncrTI_MPD.setName("UCERF3 TI");
			parIncrMPD.setInfo("TD "+mfdTypeString+" Part Mag Prob Dist for "+parName);
			parIncrTI_MPD.setInfo("TI "+mfdTypeString+" Part Mag Prob Dist for "+parName);
			u3_parIncrMPD.setInfo("UCERF3 TD "+mfdTypeString+" Part Mag Prob Dist for "+parName);
			u3_parIncrTI_MPD.setInfo("UCERF3 TI "+mfdTypeString+" Part Mag Prob Dist for "+parName);

			ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
			funcs.add(parIncrMPD);
			funcs.add(parIncrTI_MPD);
			funcs.add(u3_parIncrMPD);
			funcs.add(u3_parIncrTI_MPD);
			double maxY=0;
			double minY=Double.POSITIVE_INFINITY;
			for(XY_DataSet func:funcs) {
				if(maxY<func.getMaxY()) 
					maxY=func.getMaxY();
				for(int i=0;i<func.size();i++)
					if(func.getY(i) != 0 && minY>func.getY(i))
						minY=func.getY(i);
			}
			maxY *= 1.2;
			minY /= 1.2;

			ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.ORANGE));
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN));

			String plotName = parName;
			String xAxisLabel = "Magnitude";
			String yAxisLabel =  mfdTypeString+" Participation Prob";
			Range xAxisRange = null;
			Range yAxisRange = new Range(minY,maxY);
			boolean logX = false;
			boolean logY = true;
			double widthInches = 7; // inches
			double heightInches = 6; // inches
			File fileNamePrefix = new File(parentMPD_Dir,parentMFD_FileNamePrefix+"_"+mfdTypeString+"MagProbDists");

			PlotSpec spec = new PlotSpec(funcs, plotChars, plotName, xAxisLabel, yAxisLabel);
			spec.setLegendInset(true);

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setUserBounds(xAxisRange, yAxisRange);
			gp.setTickLabelFontSize(16);
			gp.setAxisLabelFontSize(22);
			gp.setPlotLabelFontSize(16);
			gp.setBackgroundColor(Color.WHITE);
			gp.drawGraphPanel(spec, logX, logY); // spec can be a list
			int width = (int)(widthInches*72.);
			int height = (int)(heightInches*72.);
			gp.getChartPanel().setSize(width, height); 

			try {
				gp.saveAsPNG(fileNamePrefix+".png");
				gp.saveAsPDF(fileNamePrefix+".pdf");
				gp.saveAsTXT(fileNamePrefix+".txt");
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Now do cumulative
			
		}
	}

	
	public static HashMap<Integer,Integer> getSubsectionID_Mapping(FaultSystemSolution keySolution,FaultSystemSolution solution) {
		
		HashMap<Integer,Integer> id_ID_Map = new HashMap<Integer,Integer>();

		List<? extends FaultSection> fltSectList = solution.getRupSet().getFaultSectionDataList();
		List<? extends FaultSection> keyFltSectList = keySolution.getRupSet().getFaultSectionDataList();
		String errorLog = "";
		for(int i=0;i<keyFltSectList.size();i++) {
			FaultSection keySect = keyFltSectList.get(i);
			int keyID = keySect.getSectionId();
			String keyName = keySect.getSectionName();
			for(int j=0;j<fltSectList.size();j++) {
				FaultSection sect = fltSectList.get(j);
				int iD = sect.getSectionId();
				String name = sect.getSectionName();
				
				Location firstLoc1 = keySect.getFaultTrace().getFirst();
				Location lastLoc1 = keySect.getFaultTrace().getLast();
				Location firstLoc2 = sect.getFaultTrace().getFirst();
				Location lastLoc2 = sect.getFaultTrace().getLast();
				if(LocationUtils.linearDistance(firstLoc1, firstLoc2)<1.0) {
					if(LocationUtils.linearDistance(lastLoc1, lastLoc2)<1.0) {
//						System.out.println(keyID+"\t"+iD+"\t"+keyName+"\t"+name);
						if(id_ID_Map.keySet().contains(keyID)) {
							if(id_ID_Map.get(keyID) != iD) {
//								throw new RuntimeException(keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name);
								errorLog+=keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name+"\n";
							}
						}
						else {
							id_ID_Map.put(keyID, iD);
						}
					}
				}
				else if(LocationUtils.linearDistance(firstLoc1, lastLoc2)<1.0) {
					if(LocationUtils.linearDistance(lastLoc1, firstLoc2)<1.0) {
//						System.out.println(keyID+"\t"+iD+"\t"+keyName+"\t"+name);
						if(id_ID_Map.keySet().contains(keyID)) {
							if(id_ID_Map.get(keyID) != iD) {
//									throw new RuntimeException(keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name);
								errorLog+=keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name+"\n";
							}
						}
						else {
							id_ID_Map.put(keyID, iD);
						}
					}		
				}
			}
		}
		System.out.println("Num matches = "+id_ID_Map.size()+" out of "+keySolution.getRupSet().getNumSections()+" in keySolution");
		System.err.println(errorLog);
			
		return id_ID_Map;
	}
	
	
	public static HashMap<Integer,Integer> getParentSectionID_Mapping(FaultSystemSolution keySolution,FaultSystemSolution solution) {
		
		HashMap<Integer,Integer> id_Map = new HashMap<Integer,Integer>();

		ArrayList<Integer> keyParIDsList = new ArrayList<Integer>();
		List<? extends FaultSection> fltSectList = solution.getRupSet().getFaultSectionDataList();
		List<? extends FaultSection> keyFltSectList = keySolution.getRupSet().getFaultSectionDataList();
		String errorLog = "";
		for(int i=0;i<keyFltSectList.size();i++) {
			FaultSection keySect = keyFltSectList.get(i);
			int keyID = keySect.getParentSectionId();
			if(!keyParIDsList.contains(keyID))
				keyParIDsList.add(keyID);
			String keyName = keySect.getParentSectionName();
			for(int j=0;j<fltSectList.size();j++) {
				FaultSection sect = fltSectList.get(j);
				int iD = sect.getParentSectionId();
				String name = sect.getParentSectionName();
				
				Location firstLoc1 = keySect.getFaultTrace().getFirst();
				Location lastLoc1 = keySect.getFaultTrace().getLast();
				Location firstLoc2 = sect.getFaultTrace().getFirst();
				Location lastLoc2 = sect.getFaultTrace().getLast();
				if(LocationUtils.linearDistance(firstLoc1, firstLoc2)<1.0) {
					if(LocationUtils.linearDistance(lastLoc1, lastLoc2)<1.0) {
//						System.out.println(keyID+"\t"+iD+"\t"+keyName+"\t"+name);
						if(id_Map.keySet().contains(keyID)) {
							if(id_Map.get(keyID) != iD) {
//								throw new RuntimeException(keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name);
								errorLog+=keyID+" previously associated with "+id_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name+"\n";
							}
						}
						else {
							id_Map.put(keyID, iD);
						}
					}
				}
				else if(LocationUtils.linearDistance(firstLoc1, lastLoc2)<1.0) {
					if(LocationUtils.linearDistance(lastLoc1, firstLoc2)<1.0) {
//						System.out.println(keyID+"\t"+iD+"\t"+keyName+"\t"+name);
						if(id_Map.keySet().contains(keyID)) {
							if(id_Map.get(keyID) != iD) {
//									throw new RuntimeException(keyID+" previously associated with "+id_ID_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name);
								errorLog+=keyID+" previously associated with "+id_Map.get(keyID)+" and now with "+iD+"\t"+keyName+"\t"+name+"\n";
							}
						}
						else {
							id_Map.put(keyID, iD);
						}
					}		
				}
			}
		}
		System.out.println("Num matches = "+id_Map.size()+" out of "+keyParIDsList.size()+" parent faults in keySolution");
		System.err.println(errorLog);
			
		return id_Map;
	}


	/**
	 * This gives the cumulative distribution of RIs for each section from the NSHM 2023 WUS model
	 * 
	 * @return DiscretizedFunc[] - cumulative distribution function for each section index
	 */
	public static DiscretizedFunc[] getNSHM23_WUS_SectRI_CumDistFuncArray() {
//		long time = System.currentTimeMillis();
		
		File solfile = new File("/Users/field/nshm-haz_data/results_WUS_FM_v3_branch_averaged.zip");
		FaultSystemSolution sol=null;
		try {
			sol = FaultSystemSolution.load(solfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		FaultSystemRupSet rupSet = sol.getRupSet();
	
//		String testName = "/Users/field/Library/CloudStorage/OneDrive-DOI/Field_Other/ERF_Coordination/LongTermTD_2026/Analysis/PreliminaryResults/fullPrefUS_FSS.zip";
//		FaultSystemSolution test_sol = FSS_Fetcher2023.getPreferredFull_FSS(testName);		
//		// this tests that subsection names are same
//		for(int s=0;s<rupSet.getNumSections();s++) {
//			String name = rupSet.getFaultSectionData(s).getName();
//			String nameTest = test_sol.getRupSet().getFaultSectionData(s).getName();
//			if(!name.equals(nameTest)) {
//				System.out.println(s+"\t"+name+"\t"+nameTest);
//				System.exit(-1);
//			}
//			if(s<100)
//				System.out.println(s+"\t"+name+"\t"+nameTest);
//		}
//		System.exit(-1);

		
		BranchAveragingOrder branches = sol.requireModule(BranchAveragingOrder.class);
		BranchSectParticMFDs sectParticMFDs = sol.requireModule(BranchSectParticMFDs.class);
//		BranchParentSectParticMFDs parentParticMFDs = sol.requireModule(BranchParentSectParticMFDs.class);
		int numBranches = branches.getNumBranches();
		int numSects = rupSet.getNumSections();
		System.out.println(numBranches+" branches");
		
		
		ArbDiscrEmpiricalDistFunc[] empDistFuncArray = new ArbDiscrEmpiricalDistFunc[numSects];
		for(int s=0;s<numSects;s++)
			empDistFuncArray[s] = new ArbDiscrEmpiricalDistFunc();
		
		double sumWeight = 0d;
		for (int b=0; b<numBranches; b++)
			sumWeight += branches.getBranchWeight(b);
		for (int b=0; b<numBranches; b++) {
			double weight = branches.getBranchWeight(b) / sumWeight;
//			System.out.println("Branch "+b+" (wt="+(float)weight+"):\t"+branches.getBranchFileName(b));
			for (int s=0; s<numSects; s++) {
				IncrementalMagFreqDist sectParticMFD = sectParticMFDs.getSectionMFD(b, s);
				Preconditions.checkNotNull(sectParticMFD);
				empDistFuncArray[s].set(1.0/sectParticMFD.getTotalIncrRate(),weight);
//				int parentID = rupSet.getFaultSectionData(s).getParentSectionId();
//				IncrementalMagFreqDist parentParticMFD = parentParticMFDs.getSectionMFD(b, parentID);
//				Preconditions.checkNotNull(parentParticMFD);
			}
		}
		// convert to cumulative distributions
		DiscretizedFunc[] empCumDistArray = new DiscretizedFunc[numSects];
		for(int s=0;s<numSects;s++)
			empCumDistArray[s] = empDistFuncArray[s].getCumDist();
		
//		time = System.currentTimeMillis()-time;
//		System.out.println("took "+time/1000+" seconds; sumWeight = "+sumWeight);
		
		return empCumDistArray;
	}
	
	/**
	 * This provides the cumulative distribution of parent section average recurrence intervals
	 * for the NSHM 2023 WUS model.
	 * @return
	 */
	public static Map<Integer, DiscretizedFunc> getParentSectAveRI_CumDistFuncMap() {
//		long time = System.currentTimeMillis();
		
		File solfile = new File("/Users/field/nshm-haz_data/results_WUS_FM_v3_branch_averaged.zip");
		FaultSystemSolution sol=null;
		try {
			sol = FaultSystemSolution.load(solfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		BranchAveragingOrder branches = sol.requireModule(BranchAveragingOrder.class);
		BranchSectParticMFDs sectParticMFDs = sol.requireModule(BranchSectParticMFDs.class);
//		BranchParentSectParticMFDs parentParticMFDs = sol.requireModule(BranchParentSectParticMFDs.class);
		int numBranches = branches.getNumBranches();
		int numSects = rupSet.getNumSections();
//		System.out.println(numBranches+" branches");
		
		ArrayList<Integer> parID_List = new ArrayList<Integer>();
		for(int s=0;s<numSects;s++) {
			int parID = rupSet.getFaultSectionData(s).getParentSectionId();
			if(!parID_List.contains(parID))
				parID_List.add(parID);
		}
		Map<Integer, ArbDiscrEmpiricalDistFunc> parSectAveRI_EmpDistFuncMap = Maps.newHashMap();
		for(int parID:parID_List)
			parSectAveRI_EmpDistFuncMap.put(parID,new ArbDiscrEmpiricalDistFunc());
		
		double sumWeight = 0d;
		for (int b=0; b<numBranches; b++)
			sumWeight += branches.getBranchWeight(b);
		for (int b=0; b<numBranches; b++) {
			double weight = branches.getBranchWeight(b) / sumWeight;
//			System.out.println("Branch "+b+" (wt="+(float)weight+"):\t"+branches.getBranchFileName(b));
			HashMap<Integer,ArrayList<Double>> map = new HashMap<Integer,ArrayList<Double>>();
			for (int s=0; s<numSects; s++) {
				IncrementalMagFreqDist sectParticMFD = sectParticMFDs.getSectionMFD(b, s);
				Preconditions.checkNotNull(sectParticMFD);
				int parID = rupSet.getFaultSectionData(s).getParentSectionId();
				if(!map.keySet().contains(parID)) {
					ArrayList<Double> rateList = new ArrayList<Double>();
					rateList.add(sectParticMFD.getTotalIncrRate());
					map.put(parID,rateList);
				}
				else {
					map.get(parID).add(sectParticMFD.getTotalIncrRate());
				}
			}
			for(int parID:map.keySet()) {
				double meanRate=0;
				for(double rate:map.get(parID))
					meanRate+=rate;
				meanRate /= map.get(parID).size();
				parSectAveRI_EmpDistFuncMap.get(parID).set(1.0/meanRate,weight);
			}
		}
		
		// convert to cumulative distributions
		Map<Integer, DiscretizedFunc> parSectAveRI_CumDistFuncMap = Maps.newHashMap();
		for(int parID:parSectAveRI_EmpDistFuncMap.keySet())
			parSectAveRI_CumDistFuncMap.put(parID,parSectAveRI_EmpDistFuncMap.get(parID).getCumDist());
		
		// check that total weight is 1.0
		for(int parID:parSectAveRI_CumDistFuncMap.keySet()) {
			double test = Math.abs(1.0-parSectAveRI_CumDistFuncMap.get(parID).getMaxY());
			if(test>0.00001)
				throw new RuntimeException("This should be 1.0: "+parSectAveRI_CumDistFuncMap.get(parID).getMaxY());
		}
	
//		time = System.currentTimeMillis()-time;
//		System.out.println("took "+time/1000+" seconds; sumWeight = "+sumWeight);
		
		return parSectAveRI_CumDistFuncMap;
	}
	
	
	
	private static void testERF_DOLE(TimeDepFaultSystemSolutionERF erf) {
		int num=0;
		int numNotMin=0;
		FSS_ProbabilityModel probMod = erf.getProbabilityModel();
		for(int s=0;s<erf.getSolution().getRupSet().getNumSections();s++) {
			long d1 = erf.getSolution().getRupSet().getFaultSectionData(s).getDateOfLastEvent();
			long d2 = probMod.getSectDOLE(s);
			if(d1 != d2)
				num+=1;
			if(d1 != Long.MIN_VALUE) {
				numNotMin+=1;
				System.out.println(erf.getSolution().getRupSet().getFaultSectionData(s).getName());
			}
		}
		System.out.println("ERF DOLE Test: "+num+" discrepancies out of "+
				erf.getSolution().getRupSet().getNumSections()+
				"; "+numNotMin+" are not MIN_VALUE");	
	}
	




	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_02_02-nshm23_branches-WUS_FM_v3/results_WUS_FM_v3_branch_averaged_gridded.zip"));
		File tdMainDir = new File("/home/kevin/markdown/nshm23-misc/time_dependence");
		
		generateOldPage(new File(tdMainDir, "td_dole_full_parent"), sol, PaleoMappingAlgorithm.FULL_PARENT, DataToInclude.ALL_DATA);
		generateOldPage(new File(tdMainDir, "td_dole_neighbors"), sol, PaleoMappingAlgorithm.NEIGHBORING_SECTS, DataToInclude.ALL_DATA);
		generateOldPage(new File(tdMainDir, "td_dole_single"), sol, PaleoMappingAlgorithm.CLOSEST_SECT, DataToInclude.ALL_DATA);
	}

}
