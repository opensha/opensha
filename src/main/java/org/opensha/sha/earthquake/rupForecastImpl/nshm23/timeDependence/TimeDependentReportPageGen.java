package org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence;

import java.awt.Color;
import java.io.File;
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
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.AbstractDOLE_Data;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.PaleoDOLE_Data;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.DOLE_MappingAlgorithm;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.HistoricalRupture;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence.DOLE_SubsectionMapper.MappingType;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class TimeDependentReportPageGen {
	
	public static void generatePage(File outputDir, FaultSystemSolution sol, DOLE_MappingAlgorithm mappingAlg) throws IOException {
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
		DOLE_SubsectionMapper.mapDOLE(subSects, histRupData, doleData, mappingAlg, true);
		
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
		Color directColor = Color.BLUE.darker();
		Color indirectColor = Color.GREEN.darker();
		Color historicalColor = Color.RED.darker();
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
				case DOLE_DIRECT:
					sectTypeColors[index] = directColor;
					break;
				case DOLE_INDIRECT:
					sectTypeColors[index] = indirectColor;
					break;
				case HISTORICAL:
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
		mapMaker.setSectNaNChar(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(0, 0, 0, 80)));
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
		List<String> typeLabels = new ArrayList<>();
		List<PlotCurveCharacterstics> typeChars = new ArrayList<>();
		PlotCurveCharacterstics doleLocChar = new PlotCurveCharacterstics(PlotSymbol.INV_TRIANGLE, 2f, Color.DARK_GRAY);
		typeLabels.add("DOLE Locations");
		typeChars.add(doleLocChar);
		typeLabels.add("DOLE (Direct)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, directColor));
		typeLabels.add("DOLE (Inirect)");
		typeChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, indirectColor));
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
		PlotCurveCharacterstics connectorChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.CYAN.darker());
		List<LocationList> debugLines = new ArrayList<>();
		List<PlotCurveCharacterstics> debugLineChars = new ArrayList<>();
		for (PaleoDOLE_Data data : doleDataDebug) {
			DOLE_SubsectionMapper.mapDOLE(clonedFaultSects, List.of(), List.of(data), DOLE_MappingAlgorithm.CLOSEST_SECT, false);
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
		PlotCurveCharacterstics histTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.ORANGE.darker());
		for (HistoricalRupture rup : histRupData) {
			debugLines.add(rup.trace);
			debugLineChars.add(histTraceChar);
		}
		typeLabels.add("DOLE to Section Mapping");
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
		
		List<Integer> parentIDs = new ArrayList<>();
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		for (int s=0; s<subSects.size(); s++) {
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
			
			double ri = 1d/sol.calcTotParticRateForSect(s);
			double ts = Math.max(0, curYear - sectMappings[s].year);
			timeSince[s] = ts;
			normTimeSince[s] = ts/ri;
			logTimeSince[s] = Math.log10(ts);
			logNormTimeSince[s] = Math.log10(ts/ri);
		}
		
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
				myNTScpt = ntsCPT;
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
		
		generatePage(new File(tdMainDir, "td_dole_full_parent"), sol, DOLE_MappingAlgorithm.FULL_PARENT);
		generatePage(new File(tdMainDir, "td_dole_neighbors"), sol, DOLE_MappingAlgorithm.NEIGHBORING_SECTS);
		generatePage(new File(tdMainDir, "td_dole_single"), sol, DOLE_MappingAlgorithm.CLOSEST_SECT);
	}

}
