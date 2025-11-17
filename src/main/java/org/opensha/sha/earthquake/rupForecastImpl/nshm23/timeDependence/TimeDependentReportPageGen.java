package org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ui.RectangleAnchor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
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

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.FaultSysSolutionERF_Calc;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

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
	
	public static String getExplanationText(PaleoMappingAlgorithm mappingAlg, DataToInclude dataToInclude) {
		String line = "";
		
//		String doleString = "date-of-last-event [DOLE, Hatem et al., 2025] (https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9)";
		String doleString = "date-of-last-event data <a href=\"https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9\">(DOLE, Hatem et al., 2025)</a>"; //[DOLE, Hatem et al., 2025] (https://data.usgs.gov/datacatalog/data/USGS:65c64fc9d34ef4b119cb28e9)";
		
//		"Here: [subSectDataMgt6pt7.csv](subSectDataMgt6pt7.csv)";
		// <a href="//google.com">Google</a>
		
		switch (dataToInclude) {
		case ALL_DATA:
			line += "This model includes both historic ruptures and paleoseismic "+doleString+", where the former supersedes the latter if there is a conflict. ";
			break;
		case PALEO_ONLY:
			line += "This model includes only paleoseismic "+doleString+" to show the influence of excluding historic ruptures. ";
			break;
		case HIST_RUPS_ONLY:
			line += "This model includes only historic rupture "+doleString+" to show the influence of excluding paleoseismic constraints. ";
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

		line+= "The time-dependent probabilities are calculated as defined in the <a href=\"https://doi.org/10.1785/0120140093\">UCERF3-TD model</a> (branch averaged Probability model).  "
				+ "Clicking on the \"View GeoJSON\" links below will launch a browser based map from which you can click on faults to get "
				+ "associated values. A data file is provided at the bottom of this page  (the last item in the table of contents).";
		
		//
		
		return line;
	}

	
	public static void generatePage(File outputDir, FaultSystemSolution sol, PaleoMappingAlgorithm mappingAlg, DataToInclude dataToInclude) throws IOException {
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
		String csv_dataSring = "subSect,yrLast,yrsSince,normTimeSince,supraRI,BPT_Prob_Mgt6pt7,Pois_Prob_Mgt6pt7,ProbGain_Mgt6pt7,ParID,SubsectName,ParentName,DOLE_MappingType\n";
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
		lines.add(getExplanationText(mappingAlg, dataToInclude));
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
	
	
	public static void old_generatePage(File outputDir, FaultSystemSolution sol, PaleoMappingAlgorithm mappingAlg) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		System.out.println("Loading DOLE data");
		List<PaleoDOLE_Data> doleData = DOLE_SubsectionMapper.loadPaleoDOLE();
		System.out.println("Loading Historical Rupture data");
		List<HistoricalRupture> histRupData = DOLE_SubsectionMapper.loadHistRups();
		System.out.println("Mapping DOLE data");
		String dole_ReportString = DOLE_SubsectionMapper.mapDOLE(subSects, histRupData, doleData, mappingAlg, false);
		
		// write out the DOLE mapping report
		FileWriter fw = new FileWriter(new File(outputDir+"/dole_ReportString.txt"));
		fw.write(dole_ReportString); 
		fw.close();
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Time-Dependence Report, "+mappingAlg);
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		MappingType[] sectMappingTypes = new MappingType[subSects.size()];
		AbstractDOLE_Data[] sectMappings = new AbstractDOLE_Data[subSects.size()];
		Color[] sectTypeColors = new Color[subSects.size()];
		double[] sectYears = new double[subSects.size()];
		for (int s=0; s<sectYears.length; s++)
			sectYears[s] = Double.NaN;
		List<AbstractDOLE_Data> allDatas = new ArrayList<>();
		allDatas.addAll(histRupData);
		allDatas.addAll(doleData);
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
		CPT yearCPT = GMT_CPT_Files.SEQUENTIAL_BATLOW_UNIFORM.instance().rescale(minYear, maxYear);
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
		for (PaleoDOLE_Data data : doleData) {
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
		for (HistoricalRupture rup : histRupData) {
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

		
		lines.add("## Date of Last Event");
		lines.add(topLink); lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		String relPath = resourcesDir.getName();
		table.initNewLine();
		table.addColumn("![DOLE Map 1]("+relPath+"/dole_year_map.png)");
		table.addColumn("![DOLE Map 2]("+relPath+"/dole_year_map_since_1000.png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map.geojson"));
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map_since_1000.geojson"));
		table.finalizeLine().initNewLine();
		table.addColumn("![DOLE Map 2]("+resourcesDir.getName()+"/dole_year_map_since_1800.png)");
		table.addColumn("![DOLE Map 2]("+resourcesDir.getName()+"/dole_mapping_type.png)");
		table.finalizeLine().initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_year_map_since_1800.geojson"));
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/dole_mapping_type.geojson")
				+" "+RupSetMapMaker.getGeoJSONViewerRelativeLink("View Mapping Debug GeoJSON", relPath+"/dole_mapping_debug.geojson"));
		table.finalizeLine();
		
		lines.addAll(table.build());
		lines.add("");
		
		lines.add("## Time Since Last Event");
		lines.add(topLink); lines.add("");
		
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(new Date());
		int curYear = cal.get(GregorianCalendar.YEAR);
		
		double[] timeSince = new double[subSects.size()];
		double[] normTimeSince = new double[subSects.size()];
		double[] logTimeSince = new double[subSects.size()];
		double[] logNormTimeSince = new double[subSects.size()];
		double[] recurInt = new double[subSects.size()];
		
		
		List<Integer> parentIDs = new ArrayList<>();
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		for (int s=0; s<subSects.size(); s++) {
			recurInt[s] = 1d/sol.calcTotParticRateForSect(s);
			if (sectMappings[s] == null) {
				timeSince[s] = Double.NaN;
				normTimeSince[s] = Double.NaN;
				logTimeSince[s] = Double.NaN;
				logNormTimeSince[s] = Double.NaN;
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
			normTimeSince[s] = timeSince[s]/recurInt[s];
			logTimeSince[s] = Math.log10(timeSince[s]);
			logNormTimeSince[s] = Math.log10(timeSince[s]/recurInt[s]);
		}
		
		double[] bpt_Mgt6pt7_prob = new double[subSects.size()];
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
		}
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(50d);
		erf.updateForecast();
		String csv_dataSring = "subSect,yrLast,yrsSince,normTimeSince,supraRI,BPT_Prob_Mgt6pt7,Pois_Prob_Mgt6pt7,ProbGain_Mgt6pt7,ParID,ParName\n";
		for (int s=0; s<subSects.size(); s++) {
			pois_Mgt6pt7_prob[s] = FaultSysSolutionERF_Calc.calcParticipationProbForSect(erf, 6.7, s);
			probGain_Mgt6pt7[s] = bpt_Mgt6pt7_prob[s]/pois_Mgt6pt7_prob[s];
			String parentName = subSects.get(s).getParentSectionName();
			String modParentName=null;
			if(parentName != null)
				modParentName = parentName.replace(",","_");
			csv_dataSring += s+","+(curYear-timeSince[s])+","+timeSince[s]+","+normTimeSince[s]+","+recurInt[s]+","+
					bpt_Mgt6pt7_prob[s]+","+pois_Mgt6pt7_prob[s]+","+probGain_Mgt6pt7[s]+","+
					subSects.get(s).getParentSectionId()+","+modParentName+"\n";
		}
		// write out the DOLE mapping report
		FileWriter fw2 = new FileWriter(new File(outputDir+"/subSectDataMgt6pt7.csv"));
		fw2.write(csv_dataSring); 
		fw2.close();

		mapMaker.plotSectScalars(probGain_Mgt6pt7, GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 3d), "M≥6.7 Prob. Gain");
		mapMaker.plot(resourcesDir, "mGT6pt7_ProbGain", " ");
//System.out.println("Done with ERF stuff)");
		
		mapMaker.clearSectColors();
		CPT tsCPT = GMT_CPT_Files.SEQUENTIAL_BATLOW_UNIFORM.instance().rescale(0d, 1000);
		CPT logTSCPT = GMT_CPT_Files.SEQUENTIAL_BATLOW_UNIFORM.instance().rescale(0d, 4);
		CPT ntsCPT1 = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(0d, 2);
		CPT ntsCPT2 = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(-1, 3);
		CPT ntsCPT = new CPT();
		for (CPTVal val : ntsCPT1) {
			ntsCPT.add(val);
			if (val.end >= 1f)
				break;
		}
		for (CPTVal val : ntsCPT2) {
			if (val.start <= 1f)
				continue;
			ntsCPT.add(val);
		}
		CPT logNTSCPT = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(-1d, 1);
		
		table = MarkdownUtils.tableBuilder();
		
		for (boolean log : new boolean[] {false, true}) {
			String tsLabel = "Time Since Last Event (years)";
			String tsPrefix = "time_since";
			String ntsLabel = "Normalized Time Since Last Event";
			String ntsPrefix = "norm_time_since";
			double[] tsData, ntsData;
			CPT myTScpt, myNTScpt;
			if (log) {
				tsLabel = "Log10 "+tsLabel;
				tsPrefix += "_log";
				ntsLabel = "Log10 "+ntsLabel;
				ntsPrefix += "_log";
				myTScpt = logTSCPT;
				myNTScpt = logNTSCPT;
				tsData = logTimeSince;
				ntsData = logNormTimeSince;
			} else {
				myTScpt = tsCPT;
//				myNTScpt = ntsCPT;
				myNTScpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 3d);
				tsData = timeSince;
				ntsData = normTimeSince;
			}
			mapMaker.plotSectScalars(tsData, myTScpt, tsLabel);
			mapMaker.plot(resourcesDir, tsPrefix, " ");
			mapMaker.plotSectScalars(ntsData, myNTScpt, ntsLabel);
			mapMaker.plot(resourcesDir, ntsPrefix, " ");
			
			table.initNewLine();
			table.addColumn("![Time Since Last Event]("+relPath+"/"+tsPrefix+".png)");
			table.addColumn("![Time Since Last Event]("+relPath+"/"+ntsPrefix+".png)");
			table.finalizeLine().initNewLine();
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+tsPrefix+".geojson"));
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+ntsPrefix+".geojson"));
			table.finalizeLine();
		}
		
		lines.addAll(table.build());
		lines.add("");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}


	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
				+ "2024_02_02-nshm23_branches-WUS_FM_v3/results_WUS_FM_v3_branch_averaged_gridded.zip"));
		File tdMainDir = new File("/home/kevin/markdown/nshm23-misc/time_dependence");
		
		generatePage(new File(tdMainDir, "td_dole_full_parent"), sol, PaleoMappingAlgorithm.FULL_PARENT, DataToInclude.ALL_DATA);
		generatePage(new File(tdMainDir, "td_dole_neighbors"), sol, PaleoMappingAlgorithm.NEIGHBORING_SECTS, DataToInclude.ALL_DATA);
		generatePage(new File(tdMainDir, "td_dole_single"), sol, PaleoMappingAlgorithm.CLOSEST_SECT, DataToInclude.ALL_DATA);
	}

}
