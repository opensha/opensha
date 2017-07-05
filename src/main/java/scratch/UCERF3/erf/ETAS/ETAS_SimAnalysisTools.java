package scratch.UCERF3.erf.ETAS;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TimeZone;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbol.Symbol;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.kevin.ucerf3.etas.MPJ_ETAS_Simulator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;


public class ETAS_SimAnalysisTools {

	static PlotSpec getEpicenterMapSpec(
			String info, ObsEqkRupture mainShock, Collection<ETAS_EqkRupture> allAftershocks, LocationList regionBorder) {
		ArrayList<AbstractXY_DataSet> funcs = new ArrayList<AbstractXY_DataSet>();
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();

		// M<5
		DefaultXY_DataSet epLocsGen0_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 0, allAftershocks);
		if(epLocsGen0_Mlt5.size()>0) {
			funcs.add(epLocsGen0_Mlt5);
			epLocsGen0_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.BLACK));
		}
		DefaultXY_DataSet epLocsGen1_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 1, allAftershocks);
		if(epLocsGen1_Mlt5.size()>0) {
			funcs.add(epLocsGen1_Mlt5);
			epLocsGen1_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.RED));
		}
		DefaultXY_DataSet epLocsGen2_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 2, allAftershocks);
		if(epLocsGen2_Mlt5.size()>0) {
			funcs.add(epLocsGen2_Mlt5);
			epLocsGen2_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.BLUE));
		}
		DefaultXY_DataSet epLocsGen3_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 3, allAftershocks);
		if(epLocsGen3_Mlt5.size()>0) {
			funcs.add(epLocsGen3_Mlt5);
			epLocsGen3_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.GREEN));
		}
		DefaultXY_DataSet epLocsGen4_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 4, allAftershocks);
		if(epLocsGen4_Mlt5.size()>0) {
			funcs.add(epLocsGen4_Mlt5);
			epLocsGen4_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.LIGHT_GRAY));
		}
		DefaultXY_DataSet epLocsGen5_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 5, allAftershocks);
		if(epLocsGen5_Mlt5.size()>0) {
			funcs.add(epLocsGen5_Mlt5);
			epLocsGen5_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.ORANGE));
		}
		DefaultXY_DataSet epLocsGen6_Mlt5 = getEpicenterLocsXY_DataSet(2.0, 5.0, 6, allAftershocks);
		if(epLocsGen6_Mlt5.size()>0) {
			funcs.add(epLocsGen6_Mlt5);
			epLocsGen6_Mlt5.setInfo("(circles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 1f, Color.YELLOW));
		}


		// 5.0<=M<6.5
		DefaultXY_DataSet epLocsGen0_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 0, allAftershocks);
		if(epLocsGen0_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen0_Mgt5lt65);
			epLocsGen0_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.BLACK));
		}
		DefaultXY_DataSet epLocsGen1_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 1, allAftershocks);
		if(epLocsGen1_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen1_Mgt5lt65);
			epLocsGen1_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.RED));
		}
		DefaultXY_DataSet epLocsGen2_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 2, allAftershocks);
		if(epLocsGen2_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen2_Mgt5lt65);
			epLocsGen2_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.BLUE));
		}
		DefaultXY_DataSet epLocsGen3_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 3, allAftershocks);
		if(epLocsGen3_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen3_Mgt5lt65);
			epLocsGen3_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.GREEN));
		}
		DefaultXY_DataSet epLocsGen4_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 4, allAftershocks);
		if(epLocsGen4_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen4_Mgt5lt65);
			epLocsGen4_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.LIGHT_GRAY));
		}
		DefaultXY_DataSet epLocsGen5_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 5, allAftershocks);
		if(epLocsGen5_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen5_Mgt5lt65);
			epLocsGen5_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.ORANGE));
		}
		DefaultXY_DataSet epLocsGen6_Mgt5lt65 = getEpicenterLocsXY_DataSet(5.0, 6.5, 6, allAftershocks);
		if(epLocsGen6_Mgt5lt65.size()>0) {
			funcs.add(epLocsGen6_Mgt5lt65);
			epLocsGen6_Mgt5lt65.setInfo("(triangles)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.TRIANGLE, 4f, Color.YELLOW));
		}


		// 6.5<=M<9.0
		DefaultXY_DataSet epLocsGen0_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 0, allAftershocks);
		if(epLocsGen0_Mgt65.size()>0) {
			funcs.add(epLocsGen0_Mgt65);
			epLocsGen0_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.BLACK));
		}
		DefaultXY_DataSet epLocsGen1_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 1, allAftershocks);
		if(epLocsGen1_Mgt65.size()>0) {
			funcs.add(epLocsGen1_Mgt65);
			epLocsGen1_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.RED));
		}
		DefaultXY_DataSet epLocsGen2_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 2, allAftershocks);
		if(epLocsGen2_Mgt65.size()>0) {
			funcs.add(epLocsGen2_Mgt65);
			epLocsGen2_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.BLUE));
		}
		DefaultXY_DataSet epLocsGen3_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 3, allAftershocks);
		if(epLocsGen3_Mgt65.size()>0) {
			funcs.add(epLocsGen3_Mgt65);
			epLocsGen3_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.GREEN));
		}
		DefaultXY_DataSet epLocsGen4_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 4, allAftershocks);
		if(epLocsGen4_Mgt65.size()>0) {
			funcs.add(epLocsGen4_Mgt65);
			epLocsGen4_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.LIGHT_GRAY));
		}
		DefaultXY_DataSet epLocsGen5_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 5, allAftershocks);
		if(epLocsGen5_Mgt65.size()>0) {
			funcs.add(epLocsGen5_Mgt65);
			epLocsGen5_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.ORANGE));
		}
		DefaultXY_DataSet epLocsGen6_Mgt65 = getEpicenterLocsXY_DataSet(6.5, 9.0, 6, allAftershocks);
		if(epLocsGen6_Mgt65.size()>0) {
			funcs.add(epLocsGen6_Mgt65);
			epLocsGen6_Mgt65.setInfo("(squares)");
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.SQUARE, 8f, Color.YELLOW));
		}
		
//		System.out.println("latDada\t"+minLat+"\t"+maxLat+"\t"+minLon+"\t"+maxLon+"\t");
		
		if(mainShock != null) {
			FaultTrace trace = mainShock.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
			DefaultXY_DataSet traceFunc = new DefaultXY_DataSet();
			traceFunc.setName("Main Shock Trace");
			for(Location loc:trace)
				traceFunc.set(loc.getLongitude(), loc.getLatitude());
			funcs.add(traceFunc);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		// now plot non point source aftershocks
		int lai=0;
		for(ETAS_EqkRupture rup : allAftershocks) {
			if(!rup.getRuptureSurface().isPointSurface()) {
				FaultTrace trace = rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge();
				DefaultXY_DataSet traceFunc = new DefaultXY_DataSet();
				int gen=rup.getGeneration();
				traceFunc.setName("Large aftershock ID="+rup.getID()+";  generation="+gen+"; mag="+rup.getMag());
				for(Location loc:trace)
					traceFunc.set(loc.getLongitude(), loc.getLatitude());
				funcs.add(traceFunc);
				Color color;
				if(gen==0)
					color = Color.BLACK;
				else if(gen==1)
					color = Color.RED;
				else if(gen==2)
					color = Color.BLUE;
				else if(gen==3)
					color = Color.GREEN;
				else if(gen==4)
					color = Color.LIGHT_GRAY;
				else if(gen==5)
					color = Color.ORANGE;
				else// gen 6 and above
					color = Color.YELLOW;
				plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, color));
				lai+=1;
			}
		}
		
		// now plot the region border if not null
		if(regionBorder != null) {
			DefaultXY_DataSet regBorderFunc = new DefaultXY_DataSet();
			regBorderFunc.setName("Region Border");
			for(Location loc: regionBorder) {
				regBorderFunc.set(loc.getLongitude(), loc.getLatitude());
			}
			// close the polygon:
			regBorderFunc.set(regBorderFunc.get(0).getX(), regBorderFunc.get(0).getY());
			funcs.add(regBorderFunc);
			plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		String title = "Aftershock Epicenters for "+info;
		PlotSpec spec = new PlotSpec(funcs, plotChars, title, "Longitude", "Latitude");
		
		return spec;
	}
	
	static public EpicenterMapThread plotUpdatingEpicenterMap(String info, ObsEqkRupture mainShock, 
			Collection<ETAS_EqkRupture> allAftershocks, LocationList regionBorder) {
		long updateInterval = 100; // 1 seconds
		EpicenterMapThread thread = new EpicenterMapThread(info, mainShock, allAftershocks, regionBorder, updateInterval);
		new Thread(thread).start();
		return thread;
	}
	
	public static class EpicenterMapThread implements Runnable {
		
		private String info;
		private ObsEqkRupture mainShock; 
		private Collection<ETAS_EqkRupture> allAftershocks;
		private LocationList regionBorder;
		
		private long updateIntervalMillis;
		
		private boolean kill = false;
		
		private GraphWindow gw;
		
		public EpicenterMapThread(String info, ObsEqkRupture mainShock, 
				Collection<ETAS_EqkRupture> allAftershocks, LocationList regionBorder,
				long updateIntervalMillis) {
			this.info = info;
			this.mainShock = mainShock;
			this.allAftershocks = allAftershocks;
			this.regionBorder = regionBorder;
			this.updateIntervalMillis = updateIntervalMillis;
		}

		@Override
		public void run() {
			kill = false;
			int prevCnt = 0;
			
			long eventStart = -1;
			
			while (!kill) {
				try {
					Thread.sleep(updateIntervalMillis);
				} catch (InterruptedException e) {}
				
				if (allAftershocks.size() <= prevCnt)
					// no changes, skip update
					continue;
				
				// wrap aftershocks in new list in case it changes during plotting
				
				List<ETAS_EqkRupture> allAftershocks = Lists.newArrayList(this.allAftershocks);
				if (eventStart < 0)
					eventStart = allAftershocks.get(0).getOriginTime();
//				System.out.println("updating plot with "+allAftershocks.size()
//						+" ("+(allAftershocks.size()-prevCnt)+" new)");
				prevCnt = allAftershocks.size();
				PlotSpec spec = getEpicenterMapSpec(info, mainShock, allAftershocks, regionBorder);
				
				long endTime = allAftershocks.get(allAftershocks.size()-1).getOriginTime();
				
				long duration = endTime - eventStart;
				double durationSecs = (double)duration/1000d;
				double durationMins = durationSecs/60d;
				double durationHours = durationMins/60d;
				double durationDays = durationHours/24d;
				
				String timeStr;
				if (durationDays > 1d)
					timeStr = (float)durationDays+" days";
				else if (durationHours > 1d)
					timeStr = (float)durationHours+" hours";
				else if (durationMins > 1d)
					timeStr = (float)durationMins+" mins";
				else
					timeStr = (float)durationSecs+" secs";
				
				double minLat=90, maxLat=-90,minLon=360,maxLon=-360;
				if (gw == null) {
					for(PlotElement elem : spec.getPlotElems()) {
//						System.out.println(func.getMinX()+"\t"+func.getMaxX()+"\t"+func.getMinY()+"\t"+func.getMaxY());
						if (!(elem instanceof XY_DataSet))
							continue;
						XY_DataSet func = (XY_DataSet)elem;
						if(func.getMaxX()>maxLon) maxLon = func.getMaxX();
						if(func.getMinX()<minLon) minLon = func.getMinX();
						if(func.getMaxY()>maxLat) maxLat = func.getMaxY();
						if(func.getMinY()<minLat) minLat = func.getMinY();
					}
					double deltaLat = maxLat-minLat;
					double deltaLon = maxLon-minLon;
					double aveLat = (minLat+maxLat)/2;
					double scaleFactor = 1.57/Math.cos(aveLat*Math.PI/180);	// this is what deltaLon/deltaLat should equal
					if(deltaLat > deltaLon/scaleFactor)	// expand lon range
						maxLon = minLon + deltaLat*scaleFactor;
					else // expand lat range
						maxLat = minLat + deltaLon/scaleFactor;
				} else {
					minLat = gw.getY_AxisRange().getLowerBound();
					maxLat = gw.getY_AxisRange().getUpperBound();
					minLon = gw.getX_AxisRange().getLowerBound();
					maxLon = gw.getX_AxisRange().getUpperBound();
				}
				
				double x = minLon + (maxLon-minLon)*0.95;
				double y = minLat + (maxLat-minLat)*0.95;
				XYTextAnnotation ann = new XYTextAnnotation(timeStr, x, y);
				ann.setTextAnchor(TextAnchor.TOP_RIGHT);
				ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
				spec.setPlotAnnotations(Lists.newArrayList(ann));
				
				if (gw == null) {
					gw = new GraphWindow(spec, false);
					gw.setX_AxisRange(minLon, maxLon);
					gw.setY_AxisRange(minLat, maxLat);
					
					gw.setPlotLabelFontSize(18);
					gw.setAxisLabelFontSize(16);
					gw.setTickLabelFontSize(14);
					gw.setVisible(true);
				} else {
					gw.setAxisRange(gw.getX_AxisRange(), gw.getY_AxisRange());
					gw.setPlotSpec(spec);
				}
			}
			System.out.println("Done with map thread");
		}
		
		public void kill() {
			this.kill = true;
		}
		
	}
	
	/**
	 * This plots an Epicenter map using JFreeChart
	 * @param info
	 * @param pdf_FileNameFullPath - set null is not PDF plot desired
	 * @param mainShock - leave null if not available or desired
	 * @param allAftershocks
	 */
	public static void plotEpicenterMap(String info, String pdf_FileNameFullPath, ObsEqkRupture mainShock, 
			Collection<ETAS_EqkRupture> allAftershocks, LocationList regionBorder) {
		PlotSpec spec = getEpicenterMapSpec(info, mainShock, allAftershocks, regionBorder);
		
		double minLat=90, maxLat=-90,minLon=360,maxLon=-360;
		for(PlotElement elem : spec.getPlotElems()) {
//			System.out.println(func.getMinX()+"\t"+func.getMaxX()+"\t"+func.getMinY()+"\t"+func.getMaxY());
			if (!(elem instanceof XY_DataSet))
				continue;
			XY_DataSet func = (XY_DataSet)elem;
			if(func.getMaxX()>maxLon) maxLon = func.getMaxX();
			if(func.getMinX()<minLon) minLon = func.getMinX();
			if(func.getMaxY()>maxLat) maxLat = func.getMaxY();
			if(func.getMinY()<minLat) minLat = func.getMinY();
		}

		GraphWindow graph = new GraphWindow(spec);
		double deltaLat = maxLat-minLat;
		double deltaLon = maxLon-minLon;
		double aveLat = (minLat+maxLat)/2;
		double scaleFactor = 1.57/Math.cos(aveLat*Math.PI/180);	// this is what deltaLon/deltaLat should equal
		if(deltaLat > deltaLon/scaleFactor) {	// expand lon range
			double newLonMax = minLon + deltaLat*scaleFactor;
			graph.setX_AxisRange(minLon, newLonMax);
			graph.setY_AxisRange(minLat, maxLat);
		}
		else { // expand lat range
			double newMaxLat = minLat + deltaLon/scaleFactor;
			graph.setX_AxisRange(minLon, maxLon);
			graph.setY_AxisRange(minLat, newMaxLat);
		}
		
//		// ****** HACK FOR SSA TALK ************ (delete next two lines when done)
//		graph.setX_AxisRange(-120, -116);
//		graph.setY_AxisRange(35, 37);
		
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);

		if(pdf_FileNameFullPath != null)
		try {
			graph.saveAsPDF(pdf_FileNameFullPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	/**
	 * This also excludes non-point-source surfaces
	 * @param magLow
	 * @param magHigh
	 * @param generation
	 * @param eventsList
	 * @return
	 */
	private static DefaultXY_DataSet getEpicenterLocsXY_DataSet(double magLow, double magHigh, int generation,
			Collection<ETAS_EqkRupture> eventsList) {
		DefaultXY_DataSet epicenterLocs = new DefaultXY_DataSet();
		for (ETAS_EqkRupture event : eventsList) {
			if(event.getMag()>=magLow && event.getMag()<magHigh && event.getGeneration()==generation && event.getRuptureSurface().isPointSurface())
				epicenterLocs.set(event.getHypocenterLocation().getLongitude(), event.getHypocenterLocation().getLatitude());
		}
		epicenterLocs.setName("Generation "+generation+" Aftershock Epicenters for "+magLow+"<=Mag<"+magHigh);
		return epicenterLocs;
	}
	
	
	
	/**
	 * This plots MFDs for all events and all aftershocks (regardless of parent).  TODO Does the 
	 * latter make any sense?
	 * 
	 * @param info
	 * @param resultsDir
	 * @param eventsList
	 */
	public static void plotMagFreqDists(String info, File resultsDir, Collection<ETAS_EqkRupture> eventsList) {
		
		// get the observed mag-prob dist for the sampled set of events 
		ArbIncrementalMagFreqDist allEventsMagProbDist = new ArbIncrementalMagFreqDist(2.05,8.95, 70);
		ArbIncrementalMagFreqDist aftershockMagProbDist = new ArbIncrementalMagFreqDist(2.05,8.95, 70);
		for (ETAS_EqkRupture event : eventsList) {
			allEventsMagProbDist.addResampledMagRate(event.getMag(), 1.0, true);
			if(event.getGeneration() != 0)
				aftershockMagProbDist.addResampledMagRate(event.getMag(), 1.0, true);
		}
		allEventsMagProbDist.setName("All Events MFD for simulation "+info);
		allEventsMagProbDist.setInfo("Total Num = "+allEventsMagProbDist.calcSumOfY_Vals());
		aftershockMagProbDist.setName("All Aftershocks MFD for simulation "+info);
		aftershockMagProbDist.setInfo("Total Num = "+aftershockMagProbDist.calcSumOfY_Vals());
		
		ArrayList<EvenlyDiscretizedFunc> magProbDists = new ArrayList<EvenlyDiscretizedFunc>();
		magProbDists.add(allEventsMagProbDist);
		magProbDists.add(aftershockMagProbDist);
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLUE));
				
		// Plot these MFDs
		GraphWindow magProbDistsGraph = new GraphWindow(magProbDists, "MFD for All Events and Aftershocks",plotChars); 
		magProbDistsGraph.setX_AxisLabel("Mag");
		magProbDistsGraph.setY_AxisLabel("Number");
		magProbDistsGraph.setY_AxisRange(0.1, 1e3);
		magProbDistsGraph.setX_AxisRange(2d, 9d);
		magProbDistsGraph.setYLog(true);
		magProbDistsGraph.setPlotLabelFontSize(18);
		magProbDistsGraph.setAxisLabelFontSize(16);
		magProbDistsGraph.setTickLabelFontSize(14);
		
		
		ArrayList<EvenlyDiscretizedFunc> cumMagProbDists = new ArrayList<EvenlyDiscretizedFunc>();
		cumMagProbDists.add(allEventsMagProbDist.getCumRateDistWithOffset());
		cumMagProbDists.add(aftershockMagProbDist.getCumRateDistWithOffset());
		
		ArrayList<PlotCurveCharacterstics> plotCharsCum = new ArrayList<PlotCurveCharacterstics>();
		plotCharsCum.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotCharsCum.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
				
		// Plot these MFDs
		GraphWindow cuMagProbDistsGraph = new GraphWindow(cumMagProbDists, "Cumulative MFD for All Events and Aftershocks",plotCharsCum); 
		cuMagProbDistsGraph.setX_AxisLabel("Mag");
		cuMagProbDistsGraph.setY_AxisLabel("Number");
		cuMagProbDistsGraph.setY_AxisRange(0.1, 1e4);
		cuMagProbDistsGraph.setX_AxisRange(2d, 9d);
		cuMagProbDistsGraph.setYLog(true);
		cuMagProbDistsGraph.setPlotLabelFontSize(18);
		cuMagProbDistsGraph.setAxisLabelFontSize(16);
		cuMagProbDistsGraph.setTickLabelFontSize(14);
		
		if(resultsDir != null) {
			
			String pathName = new File(resultsDir,"simEventsMFD.pdf").getAbsolutePath();
			String pathNameCum = new File(resultsDir,"simEventsCumMFD.pdf").getAbsolutePath();
			try {
				magProbDistsGraph.saveAsPDF(pathName);
				cuMagProbDistsGraph.saveAsPDF(pathNameCum);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * This computes two aftershock MFDs for the given rupture from the list of events: all aftershocks 
	 * and primary aftershocks (in that order).  These are the total count of events, not yearly rates.
	 * @param eventsList
	 * @param rupID
	 * @param info
	 */
	public static ArrayList<IncrementalMagFreqDist> getAftershockMFDsForRup(Collection<ETAS_EqkRupture> eventsList, int rupID, String info) {
		// get the observed mag-prob dist for the sampled set of events 
		ArbIncrementalMagFreqDist allAftershocksMFD = new ArbIncrementalMagFreqDist(2.05,8.95, 70);
		ArbIncrementalMagFreqDist primaryAftershocksMFD = new ArbIncrementalMagFreqDist(2.05,8.95, 70);
		for (ETAS_EqkRupture event : eventsList) {
			ETAS_EqkRupture oldestAncestor = event.getOldestAncestor();
			if(oldestAncestor != null) {
				if(oldestAncestor.getID() == rupID) {
					allAftershocksMFD.addResampledMagRate(event.getMag(), 1.0, true);
					if(event.getGeneration() == 1)
						primaryAftershocksMFD.addResampledMagRate(event.getMag(), 1.0, true);
				}
			}
		}
		allAftershocksMFD.setName("MFD Histogram for all aftershocks of rupture (ID="+rupID+") for simulation "+info);
		allAftershocksMFD.setInfo("Total Num = "+allAftershocksMFD.calcSumOfY_Vals());
		primaryAftershocksMFD.setName("MFD Histogram of primary aftershocks of input rupture (ID="+rupID+") for simulation "+info);
		primaryAftershocksMFD.setInfo("Total Num = "+primaryAftershocksMFD.calcSumOfY_Vals());
		
		ArrayList<IncrementalMagFreqDist> magProbDists = new ArrayList<IncrementalMagFreqDist>();
		magProbDists.add(allAftershocksMFD);
		magProbDists.add(primaryAftershocksMFD);

		return magProbDists;
	}
	
	
	/**
	 * This returns the expected number of aftershocks, as a function of magnitude,  assuming the U3 regional MFD 
	 * applies everywhere.  This could be primary events or all aftershocks, as well as any duration following the
	 *  event, depending on what value is given for expNumForM2p5 (the expected number of events produced by an M 
	 *  2.5 main shock).  This assumes the ETAS alpha value is 1.0.
	 * @param mainshockMag
	 * @param expNumForM2p5
	 * @return
	 */
	public static IncrementalMagFreqDist getTotalAftershockMFD_ForU3_RegionalGR(double mainshockMag, double expNumForM2p5) {
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(2012, 1.0);
		return getTotalAftershockMFD_ForU3_RegionalGR(mainshockMag, expNumForM2p5, erf);
	}
	
	/**
	 * This returns the expected number of aftershocks, as a function of magnitude,  assuming the U3 regional MFD 
	 * applies everywhere.  This could be primary events or all aftershocks, as well as any duration following the
	 *  event, depending on what value is given for expNumForM2p5 (the expected number of events produced by an M 
	 *  2.5 main shock).  This assumes the ETAS alpha value is 1.0.
	 * @param mainshockMag
	 * @param expNumForM2p5
	 * @param fss
	 * @return
	 */
	public static IncrementalMagFreqDist getTotalAftershockMFD_ForU3_RegionalGR(double mainshockMag, double expNumForM2p5,
			FaultSystemSolution fss) {
		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(fss, 2012, 1.0);
		return getTotalAftershockMFD_ForU3_RegionalGR(mainshockMag, expNumForM2p5, erf);
	}
	
	/**
	 * This returns the expected number of aftershocks, as a function of magnitude,  assuming the U3 regional MFD 
	 * applies everywhere.  This could be primary events or all aftershocks, as well as any duration following the
	 *  event, depending on what value is given for expNumForM2p5 (the expected number of events produced by an M 
	 *  2.5 main shock).  This assumes the ETAS alpha value is 1.0.
	 * @param mainshockMag
	 * @param expNumForM2p5
	 * @param erf note probability model will be set to POISSON 
	 * @return
	 */
	public static IncrementalMagFreqDist getTotalAftershockMFD_ForU3_RegionalGR(double mainshockMag, double expNumForM2p5,
			FaultSystemSolutionERF erf) {
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();
		
		SummedMagFreqDist mfd = ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true);
		
		System.out.println("Orig MFD");
		System.out.println(mfd);
		
		double totalNum = expNumForM2p5*Math.pow(10d,mainshockMag-2.5);
		
		System.out.println("Total Num: "+totalNum);
		
		mfd.scaleToCumRate(0, totalNum);
		
		System.out.println("Scaled MFD");
		System.out.println(mfd);
		
		return mfd;

	}

	
	/**
	 * This plots the the two elements of mfdList, where the first is allAftershocksMFD and the second is 
	 * primaryAftershocksMFD for a given parent rupture; MFDs are total counts, not annualized rates.

	 * 
	 * mfdList is what is returned by getAftershockMFDsForRup(Collection<ETAS_EqkRupture> eventsList, int rupID, String info)
	 * 
	 * @param info
	 * @param resultsDir
	 * @param eventsList
	 * @param rupID
	 * @param mfds
	 */
	public static void plotMagFreqDistsForRup(String fileNamePrefix, File resultsDir, ArrayList<IncrementalMagFreqDist> mfdList) {
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
				
		// Plot these MFDs
		GraphWindow magProbDistsGraph = new GraphWindow(mfdList, "MFD Histogram for "+fileNamePrefix,plotChars); 
		magProbDistsGraph.setX_AxisLabel("Mag");
		magProbDistsGraph.setY_AxisLabel("Number");
		magProbDistsGraph.setY_AxisRange(1e-5, 1e3);
		magProbDistsGraph.setX_AxisRange(2d, 9d);
		magProbDistsGraph.setYLog(true);
		magProbDistsGraph.setPlotLabelFontSize(18);
		magProbDistsGraph.setAxisLabelFontSize(16);
		magProbDistsGraph.setTickLabelFontSize(14);
		
		
		ArrayList<EvenlyDiscretizedFunc> cumMagProbDists = new ArrayList<EvenlyDiscretizedFunc>();
		cumMagProbDists.add(mfdList.get(0).getCumRateDistWithOffset());
		cumMagProbDists.add(mfdList.get(1).getCumRateDistWithOffset());
		ArrayList<PlotCurveCharacterstics> plotCharsCum = new ArrayList<PlotCurveCharacterstics>();
		plotCharsCum.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		plotCharsCum.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		
		if(mfdList.size()>2) {
			cumMagProbDists.add(mfdList.get(2).getCumRateDistWithOffset());
			plotCharsCum.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
		}
				
		// Plot these MFDs
		GraphWindow cuMagProbDistsGraph = new GraphWindow(cumMagProbDists, "Cumulative MFD Hist for "+fileNamePrefix,plotCharsCum); 
		cuMagProbDistsGraph.setX_AxisLabel("Mag");
		cuMagProbDistsGraph.setY_AxisLabel("Number");
		cuMagProbDistsGraph.setY_AxisRange(1e-4, 1e4);
		cuMagProbDistsGraph.setX_AxisRange(2d, 9d);
		cuMagProbDistsGraph.setYLog(true);
		cuMagProbDistsGraph.setPlotLabelFontSize(18);
		cuMagProbDistsGraph.setAxisLabelFontSize(16);
		cuMagProbDistsGraph.setTickLabelFontSize(14);
		
		if(resultsDir != null) {
			
			String pathName = new File(resultsDir,fileNamePrefix+".pdf").getAbsolutePath();
			String pathNameCum = new File(resultsDir,fileNamePrefix+"_Cum.pdf").getAbsolutePath();
			try {
				magProbDistsGraph.saveAsPDF(pathName);
				cuMagProbDistsGraph.saveAsPDF(pathNameCum);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	

	
	/**
	 * This plots a PDF of the number of aftershocks versus log10-distance from the parent.  Also plotted 
	 * is the expected distance decay.
	 * @param info - string describing data
	 * @param pdf_FileName - full path name of PDF files to save to (leave null if not wanted)
	 * @param simulatedRupsQueue - list of sampled events
	 * @param distDecay - ETAS distance decay for expected curve
	 * @param minDist - ETAS min distance for expected curve
	 */
	public static void plotDistDecayDensityFromParentTriggerLocHist(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, 
			double distDecay, double minDist) {
		
		double histLogMin=-2.0;;
		double histLogMax = 4.0;
		int histNum = 31;
		EvenlyDiscretizedFunc expectedLogDistDecay = ETAS_Utils.getTargetDistDecayDensityFunc(histLogMin, histLogMax, histNum, distDecay, minDist);
		expectedLogDistDecay.setName("Expected Primary Dist Decay Density");
		expectedLogDistDecay.setInfo("(distDecay="+distDecay+" and minDist="+minDist+")");

		HistogramFunction obsLogDistDecayHist = new HistogramFunction(histLogMin, histLogMax, histNum);
		obsLogDistDecayHist.setName("Observed Primary Dist Decay Density (relative to parent) for all aftershocks in "+info);
		
		double numFromPrimary = 0;
		for (ETAS_EqkRupture event : simulatedRupsQueue) {
			if(event.getGeneration()>0) {	// skip spontaneous events
				double logDist = Math.log10(event.getDistanceToParent());
				if(logDist>=histLogMin && logDist<histLogMax) {
					obsLogDistDecayHist.add(logDist, 1.0);
				}
				numFromPrimary += 1;	
			}
		}
		
		obsLogDistDecayHist.scale(1.0/numFromPrimary);
		
		// now convert to rate in each bin by dividing by the widths in linear space
		for(int i=0;i<obsLogDistDecayHist.size();i++) {
			double xLogVal = obsLogDistDecayHist.getX(i);
			double binWidthLinear = Math.pow(10, xLogVal+obsLogDistDecayHist.getDelta()/2.0) - Math.pow(10, xLogVal-obsLogDistDecayHist.getDelta()/2.0);
			obsLogDistDecayHist.set(i,obsLogDistDecayHist.getY(i)/binWidthLinear);
		}

				
		
		// normalize to PDF
//		obsLogDistDecayHist.scale(1.0/(double)numFromPrimary);
		
		// Set num in info fields
		obsLogDistDecayHist.setInfo("(based on "+numFromPrimary+" aftershocks)");

		ArrayList<EvenlyDiscretizedFunc> distDecayFuncs = new ArrayList<EvenlyDiscretizedFunc>();
		distDecayFuncs.add(expectedLogDistDecay);
		distDecayFuncs.add(obsLogDistDecayHist);

		GraphWindow graph = new GraphWindow(distDecayFuncs, "Distance Decay Density for all Primary Aftershocks "+info); 
		graph.setX_AxisLabel("Log10-Distance (km)");
		graph.setY_AxisLabel("Log10 Aftershock Density (per km)");
		graph.setX_AxisRange(histLogMin, histLogMax);
		graph.setX_AxisRange(-1.5, 3);
		
//		graph.setY_AxisRange(1e-4, 0.3);
		double minYaxisVal=0;
		for(int i=obsLogDistDecayHist.size()-1;i>=0;i--) {
			if(obsLogDistDecayHist.getY(i)>0) {
				minYaxisVal=obsLogDistDecayHist.getY(i);
				break;
			}
		}
		graph.setY_AxisRange(minYaxisVal, graph.getY_AxisRange().getUpperBound());

		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Color.RED));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
	
	
	
	/**
	 * This plots a PDF of the number of aftershocks versus log10-distance for descendants of the specified rupture.  Also plotted 
	 * is the expected distance decay.
	 * @param info - string describing data
	 * @param pdf_FileName - full path name of PDF files to save to (leave null if not wanted)
	 * @param simulatedRupsQueue - list of sampled events
	 * @param distDecay - ETAS distance decay for expected curve
	 * @param minDist - ETAS min distance for expected curve
	 */
	public static void OLDplotDistDecayHistOfAshocksForRup(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, 
			double distDecay, double minDist, int rupID) {
		
		double histLogMin=-2.0;;
		double histLogMax = 4.0;
		int histNum = 31;
		EvenlyDiscretizedFunc expectedLogDistDecay = ETAS_Utils.getTargetDistDecayFunc(histLogMin, histLogMax, histNum, distDecay, minDist);
		expectedLogDistDecay.setName("Expected Log-Dist Decay");
		expectedLogDistDecay.setInfo("(distDecay="+distDecay+" and minDist="+minDist+")");

		EvenlyDiscretizedFunc obsLogDistDecayHist = new EvenlyDiscretizedFunc(histLogMin, histLogMax, histNum);
		obsLogDistDecayHist.setTolerance(obsLogDistDecayHist.getDelta());
		obsLogDistDecayHist.setName("Observed Dist Decay for 1st Generation Aftershocks of "+info);
		
		// this is for distances from the specified main shock
		EvenlyDiscretizedFunc obsLogDistDecayFromOldestAncestor = new EvenlyDiscretizedFunc(histLogMin, histLogMax, histNum);
		obsLogDistDecayFromOldestAncestor.setName("Observed Dist Decay for All Generation Aftershocks of "+info);
		obsLogDistDecayFromOldestAncestor.setTolerance(obsLogDistDecayHist.getDelta());

		double numFromOrigSurface = 0;
		double numFromParent = 0;
		for (ETAS_EqkRupture event : simulatedRupsQueue) {
			ETAS_EqkRupture oldestAncestor = event.getOldestAncestor();
			if(oldestAncestor != null && oldestAncestor.getID() == rupID) {
				// fill in distance from parent
				double logDist = Math.log10(event.getDistanceToParent());
				if(logDist<histLogMin) {
					obsLogDistDecayHist.add(0, 1.0);
				}
				else if(logDist<histLogMax) {
					obsLogDistDecayHist.add(logDist, 1.0);
				}
				numFromParent += 1;	
				
				// fill in distance from most ancient ancestor
				logDist = Math.log10(LocationUtils.distanceToSurfFast(event.getHypocenterLocation(), oldestAncestor.getRuptureSurface()));
				if(logDist<histLogMin) {
					obsLogDistDecayFromOldestAncestor.add(0, 1.0);
				}
				else if(logDist<histLogMax) {
					obsLogDistDecayFromOldestAncestor.add(logDist, 1.0);
				}
				numFromOrigSurface += 1;
			}
		}
				
		
		// normalize to PDF
		obsLogDistDecayHist.scale(1.0/(double)numFromParent);
		obsLogDistDecayFromOldestAncestor.scale(1.0/(double)numFromOrigSurface);
		
		// Set num in info fields
		obsLogDistDecayHist.setInfo("(based on "+numFromParent+" aftershocks)");
		obsLogDistDecayFromOldestAncestor.setInfo("(based on "+numFromOrigSurface+" aftershocks)");

		ArrayList<EvenlyDiscretizedFunc> distDecayFuncs = new ArrayList<EvenlyDiscretizedFunc>();
		distDecayFuncs.add(expectedLogDistDecay);
		distDecayFuncs.add(obsLogDistDecayHist);
		distDecayFuncs.add(obsLogDistDecayFromOldestAncestor);			

		GraphWindow graph = new GraphWindow(distDecayFuncs, "Aftershock Dist Decay for Input Rupture"); 
		graph.setX_AxisLabel("Log10-Distance (km)");
		graph.setY_AxisLabel("Fraction of Aftershocks");
		graph.setX_AxisRange(histLogMin, histLogMax);

		graph.setX_AxisRange(-1.5, 3);
//		graph.setY_AxisRange(1e-4, 0.3);
		double minYaxisVal1=0;
		for(int i=obsLogDistDecayHist.size()-1;i>=0;i--) {
			if(obsLogDistDecayHist.getY(i)>0) {
				minYaxisVal1=obsLogDistDecayHist.getY(i);
				break;
			}
		}
		double minYaxisVal2=0;
		for(int i=obsLogDistDecayFromOldestAncestor.size()-1;i>=0;i--) {
			if(obsLogDistDecayFromOldestAncestor.getY(i)>0) {
				minYaxisVal2=obsLogDistDecayFromOldestAncestor.getY(i);
				break;
			}
		}
		double minYaxisVal = Math.min(minYaxisVal1, minYaxisVal2);
		graph.setY_AxisRange(minYaxisVal, graph.getY_AxisRange().getUpperBound());

		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.BLUE));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	

	/**
	 * This computes a histogram of trigger distances (distance from the point on the main 
	 * shock that did the triggering) in log10 space on the x-axis.  Histogram values are
	 * normalized by the bin width in linear space, which is why this is called a density
	 * (values will sum to 1.0 if they are multiplied by the linear bin widths).
	 * 
	 * The first bin includes all distances down to 0.0.
	 * 
	 * This assumes that aftershockList is already filtered to the desired events (all are used)
	 * 
	 * @param aftershockList
	 * @param firstLogDist - the left edge of the first bin
	 * @param lastLogDist - the right edge of the last bin
	 * @param deltaLogDist - x-axis discretization in log10 space
	 * @return
	 */
	public static HistogramFunction getLogTriggerDistDecayDensityHist(List<ETAS_EqkRupture> aftershockList, double firstLogDist,
			double lastLogDist, double deltaLogDist) {
				
		int numPts = (int)Math.round((lastLogDist-firstLogDist)/deltaLogDist);
		HistogramFunction logDistDensityHist = new HistogramFunction(firstLogDist+deltaLogDist/2d,numPts,deltaLogDist);
		
		int numDist=aftershockList.size();
		for (ETAS_EqkRupture event : aftershockList) {
			double logDist = Math.log10(event.getDistanceToParent());
			if(logDist<=firstLogDist)
				logDistDensityHist.add(0, 1.0/numDist);	// put these in the first bin
			else if(logDist<lastLogDist)
				logDistDensityHist.add(logDist, 1.0/numDist);
		}

		// now convert to rate in each bin by dividing by the widths in linear space
		for(int i=0;i<logDistDensityHist.size();i++) {
			double xLogVal = logDistDensityHist.getX(i);
			double lowerValueLinear=0;
			if(i != 0)
				lowerValueLinear = Math.pow(10, xLogVal-deltaLogDist/2.0);
			double binWidthLinear = Math.pow(10, xLogVal+deltaLogDist/2.0) - lowerValueLinear;
			logDistDensityHist.set(i,logDistDensityHist.getY(i)/binWidthLinear);
		}
		return logDistDensityHist;
	}
	
	
	/**
	 * This computes a histogram of distances from the surface of the given rupture (closest 
	 * point on that surface rather than the point from which and aftershock was triggered)
	 * in log10 space on the x-axis.  Histogram values are normalized by the bin width in 
	 * linear space, which is why this is called a density (values will sum to 1.0 if they 
	 * are multiplied by the linear bin widths).
	 * 
	 * 	 * The first bin includes all distances down to 0.0.
	 * 
	 * This assumes that aftershockList is already filtered to the desired events (e.g., primary 
	 * or all aftershocks of the given rupture).
	 * 
	 * @param aftershockList
	 * @param rupture
	 * @param firstLogDist
	 * @param lastLogDist
	 * @param deltaLogDist
	 * @return
	 */
	public static HistogramFunction getLogDistDecayDensityFromRupSurfaceHist(List<ETAS_EqkRupture> aftershockList, RuptureSurface surf, double firstLogDist,
			double lastLogDist, double deltaLogDist) {
				
		int numPts = (int)Math.round((lastLogDist-firstLogDist)/deltaLogDist);
		HistogramFunction lgoDistDensityHist = new HistogramFunction(firstLogDist+deltaLogDist/2d,numPts,deltaLogDist);
		double minDistCutoff = Double.NEGATIVE_INFINITY; //disable since things can be before first bin
		
		int numDist=aftershockList.size();
		for (ETAS_EqkRupture event : aftershockList) {
			double logDist = Math.log10(quickSurfDistUseCutoff(event.getHypocenterLocation(), surf, minDistCutoff));
			if(logDist<=firstLogDist)
				lgoDistDensityHist.add(0, 1.0/numDist);
			else if(logDist<lastLogDist)
				lgoDistDensityHist.add(logDist, 1.0/numDist);
		}

		// now convert to rate in each bin by dividing by the widths in linear space
		for(int i=0;i<lgoDistDensityHist.size();i++) {
			double xLogVal = lgoDistDensityHist.getX(i);
			double lowerValueLinear=0;
			if(i != 0)
				lowerValueLinear = Math.pow(10, xLogVal-deltaLogDist/2.0);
			double binWidthLinear = Math.pow(10, xLogVal+deltaLogDist/2.0) - lowerValueLinear;
			lgoDistDensityHist.set(i,lgoDistDensityHist.getY(i)/binWidthLinear);
		}
		return lgoDistDensityHist;
	}
	
	/**
	 * Calculates surface distance to the given fault. If any distance is encountered below the
	 * given cutoff, that distance is returned (in the first histogram bin so exact value doesn't
	 * magger). If it's vertical, horizontal distance is calculated quickly for just the first
	 * row of the surface then combined with actual minimum vertical distance for efficiency.
	 * 
	 * @param hypo
	 * @param gridSurf
	 * @param minDistCutoff
	 * @return
	 */
	private static double quickSurfDistUseCutoff(Location hypo, RuptureSurface surf,
			double minDistCutoff) {
		if (surf instanceof CompoundSurface) {
			double min = Double.POSITIVE_INFINITY;
			for (RuptureSurface subSurf : ((CompoundSurface)surf).getSurfaceList()) {
				min = Math.min(min, quickSurfDistUseCutoff(hypo, subSurf, minDistCutoff));
				if (min < minDistCutoff)
					return min;
			}
			return min;
		}
		else if(surf instanceof PointSurface) {
			return LocationUtils.linearDistanceFast(hypo, ((PointSurface) surf).getLocation());
		}
		Preconditions.checkState(surf instanceof EvenlyGriddedSurface,
				"Surface must be either an EvenlyGriddedSurface or CompoundSurface comprised "
				+ "of only EvenlyGriddedSurface's; this is "+surf.getClass());
		EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
		
		double minDistance = Double.MAX_VALUE;
		double horzDist, vertDist, totalDist;
		
		if (gridSurf.getAveDip() == 90d) {
			// vertical strike slip, use horizontal trace dist
			int closestCol = -1;
			double minHorizDist = Double.POSITIVE_INFINITY;
			for (int col=0; col<gridSurf.getNumCols(); col++) {
				horzDist = LocationUtils.horzDistanceFast(hypo, gridSurf.get(0, col));
				if (horzDist < minHorizDist) {
					minHorizDist = horzDist;
					closestCol = col;
				}
			}
			double minVertDist = Double.POSITIVE_INFINITY;
			for (int row=0; row<gridSurf.getNumRows(); row++)
				minVertDist = Math.min(minVertDist,
						Math.abs(LocationUtils.vertDistance(hypo, gridSurf.get(row, closestCol))));
			return Math.sqrt(minHorizDist * minHorizDist + minVertDist * minVertDist);
			
//			int closestRow = -1;
//			int actualClosest = -1;
//			for (int row=0; row<gridSurf.getNumRows(); row++) {
//				vertDist = LocationUtils.vertDistance(hypo, gridSurf.get(row, closestCol));
//				if (vertDist < minVertDist) {
//					minVertDist = vertDist;
//					closestRow = row;
//				}
//				totalDist = LocationUtils.linearDistanceFast(hypo, gridSurf.get(row, closestCol));
//				if (totalDist < minDistance) {
//					actualClosest = row;
//					minDistance = totalDist;
//				}
//			}
//			if (actualClosest != closestRow) {
//				Location actualLoc = gridSurf.get(actualClosest, closestCol);
//				Location detectedLoc = gridSurf.get(closestRow, closestCol);
//				System.out.println("Actual: "+actualClosest+", detected: "+closestRow);
//				double actualHDist = LocationUtils.horzDistanceFast(hypo, actualLoc);
//				System.out.println("Actual hDist: "+actualHDist+", detected: "+minHorizDist);
//				double actualVdist = LocationUtils.vertDistance(hypo, actualLoc);
//				double detectedCalcVdist = LocationUtils.vertDistance(hypo, detectedLoc);
//				System.out.println("Actual vDist: "+actualVdist+", detected: "+minVertDist+", detected calc: "+detectedCalcVdist);
//				System.out.println("Actual true dist: "+minDistance+", detected: "
//						+LocationUtils.linearDistanceFast(hypo, detectedLoc));
//				System.exit(0);
//				
//			}
//			Preconditions.checkState(actualClosest == closestRow);
//			return LocationUtils.linearDistanceFast(hypo, gridSurf.get(closestRow, closestCol));
			
//			for (int row=0; row<gridSurf.getNumRows(); row++)
//				minDistance = Math.min(minDistance,
//						LocationUtils.linearDistanceFast(hypo, gridSurf.get(row, closestCol)));
//			return minDistance;
		}
		
		// not vertical, must check entire surface within cutoff
		
		double minDistCutoffSq = minDistCutoff*minDistCutoff;

		for (int col=0; col<gridSurf.getNumCols(); col++) {
			for (int row=0; row<gridSurf.getNumRows(); row++) {
				Location loc = gridSurf.get(row, col);
				horzDist = LocationUtils.horzDistanceFast(hypo, loc);
				vertDist = LocationUtils.vertDistance(hypo, loc);
				totalDist = horzDist * horzDist + vertDist * vertDist;
				if (totalDist < minDistance) minDistance = totalDist;
				if (totalDist < minDistCutoffSq)
					return Math.sqrt(totalDist);
			}
		}
		return Math.sqrt(minDistance);
	}
	
	/**
	 * This plots a PDF of the number of aftershocks versus log10-distance for descendants of the specified rupture.  Also plotted 
	 * is the expected distance decay.
	 * @param info - string describing data
	 * @param pdf_FileName - full path name of PDF files to save to (leave null if not wanted)
	 * @param simulatedRupsQueue - list of sampled events
	 * @param distDecay - ETAS distance decay for expected curve
	 * @param minDist - ETAS min distance for expected curve
	 */
	public static void plotDistDecayDensityOfAshocksForRup(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, 
			double distDecay, double minDist, ETAS_EqkRupture rupture) {
		
		double histLogMin=-2.0;;
		double histLogMax = 4.0;
		int histNum = 30;
		double histLogDelta = 0.2;
		int rupID = rupture.getID();
		
		EvenlyDiscretizedFunc expectedLogDistDecay = ETAS_Utils.getTargetDistDecayDensityFunc(histLogMin, histLogMax, histNum, distDecay, minDist);
		expectedLogDistDecay.setName("Expected Log-Dist Decay Density");
		expectedLogDistDecay.setInfo("(distDecay="+distDecay+" and minDist="+minDist+")");
		
		List<ETAS_EqkRupture> primaryAshocksList = getPrimaryAftershocks(new ArrayList<ETAS_EqkRupture>(simulatedRupsQueue), rupture.getID());
		HistogramFunction obsLogTriggerDistDecayForPrimayOnly = getLogTriggerDistDecayDensityHist(primaryAshocksList, histLogMin, histLogMax, histLogDelta);
		obsLogTriggerDistDecayForPrimayOnly.setName("Observed Trigger Dist Decay Density for Primary Aftershocks of "+info);
		
		List<ETAS_EqkRupture> allAshocksList = getChildrenFromCatalog(new ArrayList<ETAS_EqkRupture>(simulatedRupsQueue), rupture.getID());
		HistogramFunction obsLogDistDecayFromRupSurfaceAllAshocks = getLogDistDecayDensityFromRupSurfaceHist(allAshocksList, rupture.getRuptureSurface(), histLogMin, histLogMax, histLogDelta);
		obsLogDistDecayFromRupSurfaceAllAshocks.setName("Observed Dist Decay Density from Surface for All Aftershocks of "+info);
	
		
		// make nearest-neighbor data
		double[] distArrayPrimary = new double[primaryAshocksList.size()];
		for(int i=0;i<distArrayPrimary.length;i++)
			distArrayPrimary[i] = primaryAshocksList.get(i).getDistanceToParent();
		Arrays.sort(distArrayPrimary);
		DefaultXY_DataSet nearestNeighborPrimaryData = new DefaultXY_DataSet();
		for(int i=0;i<distArrayPrimary.length-1;i++) {
			double xVal=Math.log10((distArrayPrimary[i+1]+distArrayPrimary[i])/2.0);
			double yVal=1.0/(distArrayPrimary[i+1]-distArrayPrimary[i]);
			nearestNeighborPrimaryData.set(xVal,yVal/distArrayPrimary.length);
		}
		nearestNeighborPrimaryData.setName("Nearest neighbor distance data for primary events");
		nearestNeighborPrimaryData.setInfo("");

//		DataUtils.nearestNeighborHist(data, origin, size);


		ArrayList<XY_DataSet> distDecayFuncs = new ArrayList<XY_DataSet>();
		distDecayFuncs.add(nearestNeighborPrimaryData);
		distDecayFuncs.add(expectedLogDistDecay);
		distDecayFuncs.add(obsLogTriggerDistDecayForPrimayOnly);
		distDecayFuncs.add(obsLogDistDecayFromRupSurfaceAllAshocks);			

		GraphWindow graph = new GraphWindow(distDecayFuncs, "Aftershock Dist Decay Density for Scenario: "+info); 
		graph.setX_AxisLabel("Log10-Distance (km)");
		graph.setY_AxisLabel("Log10 Aftershock Density (per km)");
		graph.setX_AxisRange(histLogMin, histLogMax);

		graph.setX_AxisRange(-1.5, 3);
//		graph.setY_AxisRange(1e-4, 0.3);
		double minYaxisVal1=0;
		for(int i=obsLogTriggerDistDecayForPrimayOnly.size()-1;i>=0;i--) {
			if(obsLogTriggerDistDecayForPrimayOnly.getY(i)>0) {
				minYaxisVal1=obsLogTriggerDistDecayForPrimayOnly.getY(i);
				break;
			}
		}
		double minYaxisVal2=0;
		for(int i=obsLogDistDecayFromRupSurfaceAllAshocks.size()-1;i>=0;i--) {
			if(obsLogDistDecayFromRupSurfaceAllAshocks.getY(i)>0) {
				minYaxisVal2=obsLogDistDecayFromRupSurfaceAllAshocks.getY(i);
				break;
			}
		}
		double minYaxisVal = Math.min(minYaxisVal1, minYaxisVal2);
		graph.setY_AxisRange(minYaxisVal, graph.getY_AxisRange().getUpperBound());

		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.GREEN));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.BLUE));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	
	/**
	 * This plots the number of aftershocks versus log10-distance from the parent, 
	 * and if a "mainShock" is provided, also from this event.  Also plotted is the expected distance decay.
	 * @param info - string describing the given mainShock (below); set null if mainShock is also null
	 * @param pdf_FileName - full path name of PDF files to save to (leave null if not wanted)
	 * @param simulatedRupsQueue - list of sampled events
	 * @param mainShock - distances of all aftershocks  will be computed to this event if it's not null
	 */
	public static void oldPlotDistDecayHistForAshocks(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, 
			EqkRupture mainShock, double distDecay, double minDist) {
		
		double histLogMin=-2.0;;
		double histLogMax = 4.0;
		int histNum = 31;
		EvenlyDiscretizedFunc expectedLogDistDecay = ETAS_Utils.getTargetDistDecayFunc(histLogMin, histLogMax, histNum, distDecay, minDist);
		expectedLogDistDecay.setName("Expected Log-Dist Decay");
		expectedLogDistDecay.setInfo("(distDecay="+distDecay+" and minDist="+minDist+")");

		EvenlyDiscretizedFunc obsLogDistDecayHist = new EvenlyDiscretizedFunc(histLogMin, histLogMax, histNum);
		obsLogDistDecayHist.setTolerance(obsLogDistDecayHist.getDelta());
		obsLogDistDecayHist.setName("Observed Log_Dist Decay Histogram");
		
		// this is for distances from the specified main shock
		EvenlyDiscretizedFunc obsLogDistDecayFromMainShockHist = new EvenlyDiscretizedFunc(histLogMin, histLogMax, histNum);
		obsLogDistDecayFromMainShockHist.setName("Observed Log_Dist Decay From Specified Minshock Histogram");
		obsLogDistDecayFromMainShockHist.setTolerance(obsLogDistDecayHist.getDelta());


		double numFromMainShock = 0, numFromPrimary = 0;
		for (ETAS_EqkRupture event : simulatedRupsQueue) {
			if(event.getGeneration()>0) {	// skip spontaneous events
				double logDist = Math.log10(event.getDistanceToParent());
				if(logDist<histLogMin) {
					obsLogDistDecayHist.add(0, 1.0);
				}
				else if(logDist<histLogMax) {
					obsLogDistDecayHist.add(logDist, 1.0);
				}
				numFromPrimary += 1;	
//}
				if(mainShock != null) {	// might want to try leaving spontaneous events in this?
					logDist = Math.log10(LocationUtils.distanceToSurfFast(event.getHypocenterLocation(), mainShock.getRuptureSurface()));
					if(logDist<histLogMin) {
						obsLogDistDecayFromMainShockHist.add(0, 1.0);
					}
					else if(logDist<histLogMax) {
						obsLogDistDecayFromMainShockHist.add(logDist, 1.0);
					}
					numFromMainShock += 1;
				}
			}
		}
				
		
		// normalize to PDF
		obsLogDistDecayHist.scale(1.0/(double)numFromPrimary);
		if(mainShock != null) {
			obsLogDistDecayFromMainShockHist.scale(1.0/(double)numFromMainShock);
			if(mainShock.getRuptureSurface().isPointSurface())
				System.out.println("mainShock Loc: "+mainShock.getRuptureSurface().getFirstLocOnUpperEdge());
		}
		
		// convert to PDF
		
		// Set num in info fields
		obsLogDistDecayHist.setInfo("(based on "+numFromPrimary+" aftershocks)");


		ArrayList distDecayFuncs = new ArrayList();
		distDecayFuncs.add(expectedLogDistDecay);
		distDecayFuncs.add(obsLogDistDecayHist);
		if(mainShock != null) {
			obsLogDistDecayFromMainShockHist.setInfo("(based on "+numFromMainShock+" aftershocks)");

			// TEMP HACK FOR SSA TALK
//			distDecayFuncs.add(obsLogDistDecayFromMainShockHist);			
		}

		GraphWindow graph = new GraphWindow(distDecayFuncs, "Distance Decay for Aftershocks"+info); 
		graph.setX_AxisLabel("Log10-Distance (km)");
		graph.setY_AxisLabel("Fraction of Aftershocks");
		graph.setX_AxisRange(histLogMin, histLogMax);
		graph.setY_AxisRange(1e-6, 1);

// TEMP HACK FOR SSA TALK
		graph.setX_AxisRange(-1.5, 3);
		graph.setY_AxisRange(1e-4, 0.3);
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Color.RED));
		if(mainShock != null)
			plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.GREEN));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	
	
	
	/**
	 * Supra-seismogenic prob is computed as one minus the probability that none of the primary aftershocks
	 * trigger an event.
	 * @param rupInfo
	 * @param pdf_FileNamePrefix
	 * @param mfdList - containing MFD PDF given a primary event
	 * @param rupture
	 * @param expNum
	 * @return
	 */
	public static List<EvenlyDiscretizedFunc> getExpectedPrimaryMFDs_ForRup(String rupInfo, String pdf_FileNamePrefix,  List<SummedMagFreqDist> mfdList, 
			EqkRupture rupture, double expNum, boolean isPoisson) {
		
		IncrementalMagFreqDist mfdSupra = mfdList.get(1).deepClone();
		if(isPoisson) {
			mfdSupra.scale(expNum);	
			mfdSupra.setName("Expected MFD for supra seis primary aftershocks of "+rupInfo);
		}
		else {
			double probFirstSupra = mfdSupra.calcSumOfY_Vals();
			double probSupra = 1 - Math.pow(1.0-probFirstSupra, expNum);
			mfdSupra.scale(probSupra/probFirstSupra);
			mfdSupra.setName("Prob of one or more supra seis primary aftershocks of "+rupInfo);
		}
		mfdSupra.setInfo("Data:\n"+mfdSupra.getMetadataString());

		
		IncrementalMagFreqDist mfdSubSeis = mfdList.get(2).deepClone();
		mfdSubSeis.scale(expNum);
		
		SummedMagFreqDist mfd = new SummedMagFreqDist(mfdSupra.getMinX(), mfdSupra.getMaxX(), mfdSupra.size());
		mfd.addIncrementalMagFreqDist(mfdSupra);
		mfd.addIncrementalMagFreqDist(mfdSubSeis);
		
		mfd.setName("Expected MFD for primary aftershocks of "+rupInfo);
		mfd.setInfo("expNum="+expNum+"Data:\n"+mfd.getMetadataString());
		

		EvenlyDiscretizedFunc cumMFD=mfd.getCumRateDistWithOffset();
		cumMFD.setName("Cum MFD for primary aftershocks of "+rupInfo);
		String info = "expNum="+(float)expNum+" over forecast duration\n";
		double expNumAtMainshockMag = cumMFD.getInterpolatedY(rupture.getMag());
		info+="expNumAtMainshockMag="+(float)expNumAtMainshockMag+" (at mag "+(float)rupture.getMag()+")\n";
		info += "Data:\n"+cumMFD.getMetadataString();
		cumMFD.setInfo(info);

		
		EvenlyDiscretizedFunc cumMFDsupra = mfdSupra.getCumRateDistWithOffset();
		cumMFDsupra.setName("Cum MFD for supra seis primary aftershocks of "+rupInfo);
		cumMFDsupra.setInfo(cumMFDsupra.getMetadataString());

		ArrayList<EvenlyDiscretizedFunc> mfdListReturned = new ArrayList<EvenlyDiscretizedFunc>();
		mfdListReturned.add(mfd);
		mfdListReturned.add(cumMFD);
		mfdListReturned.add(mfdSupra);
		mfdListReturned.add(cumMFDsupra);
		
		return mfdListReturned;
	}

	
	
	/**
	 * This plots the expected MFD (PDF) of primary events, the expected number of secondary events 
	 * (as a function of magnitude), and the the expected cumulative MFD for primary events (if the
	 * given expNum is not NaN) from the given rupture.
	 * 
	 * This returns a list with the expected mfd (element 0) and the cumulative MFD (element 1, which is null if expNum=NaN),
	 * plus supra mfd (element 2) and cumulative supra mfd (element 3)
	 * 
	 * @param rupInfo - Info String
	 * @param pdf_FileNamePrefix - plots are saved if this is non null
	 * @param List<SummedMagFreqDist> mfdList
	 * @param rupture
	 * @param expNum - expected number of primary aftershocks
	 * @return
	 */
	public static void plotExpectedPrimaryMFD_ForRup(String rupInfo, String pdf_FileNamePrefix,  List<EvenlyDiscretizedFunc> mfdList, EqkRupture rupture, double expNum) {

		// make GR comparison
		SummedMagFreqDist incrMFD = (SummedMagFreqDist)mfdList.get(0);
		double minMag = incrMFD.getMinMagWithNonZeroRate();
		double maxMagWithNonZeroRate = incrMFD.getMaxMagWithNonZeroRate();
		int numMag = (int)Math.round((maxMagWithNonZeroRate-minMag)/incrMFD.getDelta()) + 1;
//System.out.println("HERE IT IS minMag="+minMag);
//System.out.println("maxMagWithNonZeroRate="+maxMagWithNonZeroRate);
//System.out.println("numMag="+numMag);
//System.out.println(incrMFD);

		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(1.0, 1.0, minMag, maxMagWithNonZeroRate, numMag);
		gr.scaleToIncrRate(3.05, incrMFD.getY(3.05));
		gr.setName("Perfect GR");
		gr.setInfo("Data:\n"+gr.getMetadataString());
		EvenlyDiscretizedFunc grCum=gr.getCumRateDistWithOffset();
		grCum.setInfo("Data:\n"+grCum.getMetadataString());

		
		ArrayList<EvenlyDiscretizedFunc> incrMFD_List = new ArrayList<EvenlyDiscretizedFunc>();
		incrMFD_List.add(gr);
		incrMFD_List.add(mfdList.get(0));
		incrMFD_List.add(mfdList.get(2));
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));

		GraphWindow magProbDistsGraph = new GraphWindow(incrMFD_List, "Expected Primary Aftershock MFD", plotChars); 
		magProbDistsGraph.setX_AxisLabel("Mag");
		magProbDistsGraph.setY_AxisLabel("Expected Num");
//		magProbDistsGraph.setY_AxisRange(10e-9, 10e-1);
//		magProbDistsGraph.setX_AxisRange(2., 9.);
		magProbDistsGraph.setYLog(true);
		magProbDistsGraph.setPlotLabelFontSize(22);
		magProbDistsGraph.setAxisLabelFontSize(20);
		magProbDistsGraph.setTickLabelFontSize(18);			
		
		ArrayList<EvenlyDiscretizedFunc> cumMFD_List = new ArrayList<EvenlyDiscretizedFunc>();
		double ratioAtM6pt3 = mfdList.get(1).getY(mfdList.get(1).getClosestXIndex(6.3))/grCum.getY(grCum.getClosestXIndex(6.3));
		String newInfo = "blue/gray Ratio at M 6.3="+(float)ratioAtM6pt3+"\n"+grCum.getInfo();
		grCum.setInfo(newInfo);
		cumMFD_List.add(grCum);
		cumMFD_List.add(mfdList.get(1));
		cumMFD_List.add(mfdList.get(3));
		
		// cumulative distribution of expected num primary
		GraphWindow cumDistsGraph = new GraphWindow(cumMFD_List, "Expected Cumulative Primary Aftershock MFD", plotChars); 
		cumDistsGraph.setX_AxisLabel("Mag");
		cumDistsGraph.setY_AxisLabel("Expected Number");
		cumDistsGraph.setY_AxisRange(10e-8, 10e4);
		cumDistsGraph.setX_AxisRange(2.,9.);
		cumDistsGraph.setYLog(true);
		cumDistsGraph.setPlotLabelFontSize(22);
		cumDistsGraph.setAxisLabelFontSize(20);
		cumDistsGraph.setTickLabelFontSize(18);			

		
		// expected relative num secondary aftershocks at each magnitude
		EvenlyDiscretizedFunc mfd = mfdList.get(0);
		IncrementalMagFreqDist expSecondaryNum = new IncrementalMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		for(int i= 0;i<expSecondaryNum.size();i++)
			expSecondaryNum.set(i,mfd.getY(i)*Math.pow(10,mfd.getX(i)));
		
		// TEST
		IncrementalMagFreqDist subMFD = new IncrementalMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
		IncrementalMagFreqDist supraMFD = new IncrementalMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
	
		
		
		
		double sum = 0;
		double sumSub = 0;
		boolean subSeisMag=true;
		int count=0;
		int lowIndex=expSecondaryNum.getClosestXIndex(expSecondaryNum.getMinMagWithNonZeroRate());
		int hiIndex=expSecondaryNum.getClosestXIndex(expSecondaryNum.getMaxMagWithNonZeroRate());
		for(int i=lowIndex; i<=hiIndex; i++) {
			sum += expSecondaryNum.getY(i);
			if(subSeisMag) {
				subMFD.set(i,mfd.getY(i));
			}
			else {
				supraMFD.set(i,mfd.getY(i));
			}
			if(subSeisMag) {
				sumSub+=expSecondaryNum.getY(i);
				// test next
				if(i+1<=hiIndex) {	// avoid going over highest mag in perfect GR
					double fractDiff = Math.abs(1-expSecondaryNum.getY(i)/expSecondaryNum.getY(i+1));
					if(fractDiff>0.01)
						subSeisMag=false;					
				}
			}
			count += 1;
		}
		expSecondaryNum.setName("RelNumExpSecAftershocks");
		String info = "This is the MFD of primary aftershocks multiplied by 10^mag\n";
		double sumPerfectGR = expSecondaryNum.getY(lowIndex)*count;
		info += "sum="+(float)sum+" (vs "+(float)sumPerfectGR+" for perfect GR; ratio="+(float)(sum/sumPerfectGR)+")\n";
		info  += "grCorr_numPrimary="+(float)((sumPerfectGR-sumSub)/(sum-sumSub))+"\n";
		info  += "grCorr_numPrimary2="+(float)ETAS_Utils.getScalingFactorToImposeGR_numPrimary(supraMFD, subMFD, false)+"\n";
		info  += "grCorr_supraRates="+(float)ETAS_Utils.getScalingFactorToImposeGR_supraRates(supraMFD, subMFD, false)+"\n";
//System.out.println(subMFD);
//System.out.println(supraMFD);
		info += "Data:\n"+expSecondaryNum.getMetadataString();
		expSecondaryNum.setInfo(info);
		GraphWindow expSecGraph = new GraphWindow(expSecondaryNum, "Relative Expected Num Secondary Aftershocks"); 
		expSecGraph.setX_AxisLabel("Mag");
		expSecGraph.setY_AxisLabel("Rel Num Secondary");
		expSecGraph.setX_AxisRange(2.,9.);
		expSecGraph.setPlotLabelFontSize(22);
		expSecGraph.setAxisLabelFontSize(20);
		expSecGraph.setTickLabelFontSize(18);			

		
		if(pdf_FileNamePrefix != null) {
			try {
				magProbDistsGraph.saveAsPDF(pdf_FileNamePrefix+"_Incr.pdf");
				magProbDistsGraph.saveAsTXT(pdf_FileNamePrefix+"_Incr.txt");
				expSecGraph.saveAsPDF(pdf_FileNamePrefix+"_ExpRelSecAft.pdf");
				expSecGraph.saveAsTXT(pdf_FileNamePrefix+"_ExpRelSecAft.txt");
				if(!Double.isNaN(expNum)) {
					cumDistsGraph.saveAsPDF(pdf_FileNamePrefix+"_Cum.pdf");
					cumDistsGraph.saveAsTXT(pdf_FileNamePrefix+"_Cum.txt");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}




	/**
	 * This computes the number of ruptures in each generation (the latter being the index of the returned array,
	 * with zero being spontaneous rupture).
	 * @param simulatedRupsQueue
	 * @param maxGeneration
	 * @return
	 */
	public static int[] getNumAftershocksForEachGeneration(Collection<ETAS_EqkRupture> simulatedRupsQueue, int maxGeneration) {
		int[] numForGen = new int[maxGeneration+1];	// add 1 to include 0
		for(ETAS_EqkRupture rup:simulatedRupsQueue) {
			if(rup.getGeneration() < numForGen.length)
				numForGen[rup.getGeneration()] +=1;
		}
		return numForGen;
	}
	

	public static HistogramFunction getPrimaryRateVsLogTimePDF_ForAllEvents(PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, double firstLogDay, double lastLogDay, double deltaLogDay) {
				
		int numPts = (int)Math.round((lastLogDay-firstLogDay)/deltaLogDay);
		HistogramFunction rateVsLogTimeHist = new HistogramFunction(firstLogDay+deltaLogDay/2d,numPts,deltaLogDay);
		
		int numMissedBeforeFirstBin=0;
		for (ETAS_EqkRupture event : simulatedRupsQueue) {
			if(event.getParentRup()==null || event.getGeneration() != 1)
				continue;
			double timeMillis = event.getOriginTime()-event.getParentRup().getOriginTime();
			double logTimeDays = Math.log10(timeMillis/ProbabilityModelsCalc.MILLISEC_PER_DAY);
			if(logTimeDays<=firstLogDay) {	// avoid spike in first bin
				numMissedBeforeFirstBin+=1;
				continue;
			}
			if(logTimeDays<lastLogDay)
				rateVsLogTimeHist.add(logTimeDays, 1.0);
		}

		// now convert to rate in each bin by dividing by the widths in linear space
		for(int i=0;i<rateVsLogTimeHist.size();i++) {
			double xLogVal = rateVsLogTimeHist.getX(i);
			double binWidthLinear = Math.pow(10, xLogVal+deltaLogDay/2.0) - Math.pow(10, xLogVal-deltaLogDay/2.0);
			rateVsLogTimeHist.set(i,rateVsLogTimeHist.getY(i)/binWidthLinear);
		}
		// normalize to PDF
		rateVsLogTimeHist.normalizeBySumOfY_Vals();
		
		rateVsLogTimeHist.setInfo("numMissedBeforeFirstBin="+numMissedBeforeFirstBin);
		return rateVsLogTimeHist;
	}

	
	
	public static HistogramFunction getAftershockRateVsLogTimeHistForRup(List<ETAS_EqkRupture> aftershockList, int rupID, long rupOT_millis, double firstLogDay,
			double lastLogDay, double deltaLogDay) {
				
		int numPts = (int)Math.round((lastLogDay-firstLogDay)/deltaLogDay);
		HistogramFunction rateVsLogTimeHist = new HistogramFunction(firstLogDay+deltaLogDay/2d,numPts,deltaLogDay);
		
		int numMissedBeforeFirstBin=0;
		for (ETAS_EqkRupture event : aftershockList) {
			double timeMillis = event.getOriginTime()-rupOT_millis;
			double logTimeDays = Math.log10(timeMillis/ProbabilityModelsCalc.MILLISEC_PER_DAY);
			if(logTimeDays<=firstLogDay) {	// avoid spike in first bin
				numMissedBeforeFirstBin+=1;
				continue;
			}
			if(logTimeDays<lastLogDay)
				rateVsLogTimeHist.add(logTimeDays, 1.0);
		}

		// now convert to rate in each bin by dividing by the widths in linear space
		for(int i=0;i<rateVsLogTimeHist.size();i++) {
			double xLogVal = rateVsLogTimeHist.getX(i);
			double binWidthLinear = Math.pow(10, xLogVal+deltaLogDay/2.0) - Math.pow(10, xLogVal-deltaLogDay/2.0);
			rateVsLogTimeHist.set(i,rateVsLogTimeHist.getY(i)/binWidthLinear);
		}
		rateVsLogTimeHist.setInfo("numMissedBeforeFirstBin="+numMissedBeforeFirstBin);
		return rateVsLogTimeHist;
	}

	
	/**
	 * This plots the primary aftershock rate decay for all primary events in the simulation (no matter who the parent is)
	 * @param info
	 * @param pdf_FileName
	 * @param simulatedRupsQueue
	 * @param etasProductivity_k
	 * @param etasTemporalDecay_p
	 * @param etasMinTime_c
	 */
	public static void plotRateVsLogTimeForPrimaryAshocks(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue, 
			double etasProductivity_k, double etasTemporalDecay_p, double etasMinTime_c) {
		
		double firstLogDay = -5;
		double lastLogDay = 5;
		double deltaLogDay =0.2;
//		int numPts = (int)Math.round((lastLogDay-firstLogDay)/deltaLogDay);
		
		// get rate histogram
		HistogramFunction rateVsLogTimePrimaryOnly = getPrimaryRateVsLogTimePDF_ForAllEvents(simulatedRupsQueue, firstLogDay, lastLogDay, deltaLogDay);
		rateVsLogTimePrimaryOnly.setName("Aftershock Rate PDF for all primary events in the "+info+" simulation");
//		rateVsLogTimePrimaryOnly.setInfo(" ");	// don't do this because there is info in here
	
		// make the target function & change it to a PDF
		HistogramFunction targetFunc = ETAS_Utils.getRateWithLogTimeFunc(etasProductivity_k, etasTemporalDecay_p, 7d, ETAS_Utils.magMin_DEFAULT, etasMinTime_c, firstLogDay, lastLogDay, deltaLogDay);
		targetFunc.normalizeBySumOfY_Vals();
		targetFunc.setName("Expected Rate Decay PDF for all Primary Aftershocks");

		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(rateVsLogTimePrimaryOnly);
		funcs.add(targetFunc);
		
		GraphWindow graph = new GraphWindow(funcs, "Primary Aftershock Rate for all events"); 
		graph.setX_AxisLabel("Log10 Days");
		graph.setY_AxisLabel("Rate (per day)");
		graph.setX_AxisRange(-4, 3);
		double minYaxisVal=0;
		for(int i=rateVsLogTimePrimaryOnly.size()-1;i>=0;i--) {
			if(rateVsLogTimePrimaryOnly.getY(i)>0) {
				minYaxisVal=rateVsLogTimePrimaryOnly.getY(i);
				break;
			}
		}
		graph.setY_AxisRange(minYaxisVal, graph.getY_AxisRange().getUpperBound());
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
//		graph.setXLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	public static void plotRateVsLogTimeForPrimaryAshocksOfRup(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue,
			ETAS_EqkRupture rup, double etasProductivity_k, double etasTemporalDecay_p, double etasMinTime_c) {
		
		double firstLogDay = -5;
		double lastLogDay = 5;
		double deltaLogDay =0.2;
//		int numPts = (int)Math.round((lastLogDay-firstLogDay)/deltaLogDay);
		
		List<ETAS_EqkRupture> aftershocksList = getPrimaryAftershocks(new ArrayList<ETAS_EqkRupture>(simulatedRupsQueue), rup.getID());
		
		// get rate histogram
		HistogramFunction rateVsLogTimePrimaryOnly = getAftershockRateVsLogTimeHistForRup(aftershocksList, rup.getID(), 
				rup.getOriginTime(), firstLogDay, lastLogDay, deltaLogDay);
		rateVsLogTimePrimaryOnly.setName("Primary Aftershock Rate Histogram for "+info);
//		rateVsLogTimePrimaryOnly.setInfo(" ");	// don't do this because there is info in here
		
		// get inter-event rates data
		double[] relativeEventTimesDays = new double[aftershocksList.size()];
		for(int i=0;i<aftershocksList.size();i++) {
			double days = ((double)(aftershocksList.get(i).getOriginTime()-rup.getOriginTime()))/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
			relativeEventTimesDays[i]=days;
		}
//		XY_DataSet interEventRatesXY_DataSet = DataUtils.nearestNeighborHist(relativeEventTimesDays, 0.0, 2);	// this does not provide x-values in log space
		DefaultXY_DataSet interEventRatesXY_DataSet = new DefaultXY_DataSet();
		for(int i=0;i<relativeEventTimesDays.length-1;i++) {
			double xVal = Math.log10((relativeEventTimesDays[i]+relativeEventTimesDays[i+1])/2);
			double yVal = 1.0/(relativeEventTimesDays[i+1]-relativeEventTimesDays[i]);
			if(!Double.isInfinite(yVal)) // avoid events that happened at the same time (at double precision, which accasionally happens)
				interEventRatesXY_DataSet.set(xVal,yVal);
		}
		
		interEventRatesXY_DataSet.setName("Inter-event Rates for "+info);
		interEventRatesXY_DataSet.setInfo(" ");
	
		// make the target function & change it to a PDF
		HistogramFunction targetFunc = ETAS_Utils.getRateWithLogTimeFunc(etasProductivity_k, etasTemporalDecay_p, rup.getMag(), ETAS_Utils.magMin_DEFAULT, etasMinTime_c, firstLogDay, lastLogDay, deltaLogDay);
		targetFunc.setName("Expected Rate Decay for Primary Aftershocks");

		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(interEventRatesXY_DataSet);
		funcs.add(rateVsLogTimePrimaryOnly);
		funcs.add(targetFunc);
		
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		GraphWindow graph = new GraphWindow(funcs, "Primary Aftershock Rate for"+info,plotChars); 

		graph.setX_AxisLabel("Log10 Days");
		graph.setY_AxisLabel("Rate (per day)");
		graph.setX_AxisRange(-4, 3);
		graph.setY_AxisRange(1e-3, graph.getY_AxisRange().getUpperBound());

		graph.setYLog(true);
//		graph.setXLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);

		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	}


	
	public static void plotRateVsLogTimeForAllAshocksOfRup(String info, String pdf_FileName, PriorityQueue<ETAS_EqkRupture> simulatedRupsQueue,
			ETAS_EqkRupture rup, double etasProductivity_k, double etasTemporalDecay_p, double etasMinTime_c) {
		
		double firstLogDay = -5;
		double lastLogDay = 5;
		double deltaLogDay =0.2;
//		int numPts = (int)Math.round((lastLogDay-firstLogDay)/deltaLogDay);
		
		List<ETAS_EqkRupture> aftershocksList = getChildrenFromCatalog(new ArrayList<ETAS_EqkRupture>(simulatedRupsQueue), rup.getID());
		
		// get rate histogram
		HistogramFunction rateVsLogTimePrimaryOnly = getAftershockRateVsLogTimeHistForRup(aftershocksList, rup.getID(), 
				rup.getOriginTime(), firstLogDay, lastLogDay, deltaLogDay);
		rateVsLogTimePrimaryOnly.setName("All Aftershocks Rate Histogram for "+info);
//		rateVsLogTimePrimaryOnly.setInfo(" ");	// don't do this because there is info in here

		// get inter-event rates data
		double[] relativeEventTimesDays = new double[aftershocksList.size()];
		for(int i=0;i<aftershocksList.size();i++) {
			double days = ((double)(aftershocksList.get(i).getOriginTime()-rup.getOriginTime()))/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
			relativeEventTimesDays[i]=days;
		}
//		XY_DataSet interEventRatesXY_DataSet = DataUtils.nearestNeighborHist(relativeEventTimesDays, 0.0, 2);	// this does not provide x-values in log space
		DefaultXY_DataSet interEventRatesXY_DataSet = new DefaultXY_DataSet();
		for(int i=0;i<relativeEventTimesDays.length-1;i++) {
			double xVal = Math.log10((relativeEventTimesDays[i]+relativeEventTimesDays[i+1])/2);
			double yVal = 1.0/(relativeEventTimesDays[i+1]-relativeEventTimesDays[i]);
			if(!Double.isInfinite(yVal)) // avoid events that happened at the same time (at double precision, which accasionally happens)
				interEventRatesXY_DataSet.set(xVal,yVal);
		}
		
		interEventRatesXY_DataSet.setName("Inter-event Rates for "+info);
		interEventRatesXY_DataSet.setInfo(" ");

	
		// make the target function & change it to a PDF
		HistogramFunction targetFunc = ETAS_Utils.getRateWithLogTimeFunc(etasProductivity_k, etasTemporalDecay_p, rup.getMag(), ETAS_Utils.magMin_DEFAULT, etasMinTime_c, firstLogDay, lastLogDay, deltaLogDay);
		targetFunc.setName("Expected Rate Decay for All Aftershocks");

		
		ArrayList<XY_DataSet> funcs = new ArrayList<XY_DataSet>();
		funcs.add(interEventRatesXY_DataSet);
		funcs.add(rateVsLogTimePrimaryOnly);
		funcs.add(targetFunc);
		
		GraphWindow graph = new GraphWindow(funcs, "All Aftershocks Rate for "+info); 
		graph.setX_AxisLabel("Log10 Days");
		graph.setY_AxisLabel("Rate (per day)");
		graph.setX_AxisRange(-4, 3);
		graph.setY_AxisRange(1e-3, graph.getY_AxisRange().getUpperBound());
		ArrayList<PlotCurveCharacterstics> plotChars = new ArrayList<PlotCurveCharacterstics>();
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLUE));
		plotChars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.RED));
		plotChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		graph.setPlotChars(plotChars);
		graph.setYLog(true);
//		graph.setXLog(true);
		graph.setPlotLabelFontSize(18);
		graph.setAxisLabelFontSize(16);
		graph.setTickLabelFontSize(14);
		if(pdf_FileName != null)
			try {
				graph.saveAsPDF(pdf_FileName);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	public static void writeMemoryUse(String info) {
		Runtime runtime = Runtime.getRuntime();

	    NumberFormat format = NumberFormat.getInstance();

	    StringBuilder sb = new StringBuilder();
	    long maxMemory = runtime.maxMemory();
	    long allocatedMemory = runtime.totalMemory();
	    long freeMemory = runtime.freeMemory();

	    System.out.println(info);
	    System.out.println("\tin use memory: " + format.format((allocatedMemory-freeMemory) / 1024));
	    System.out.println("\tfree memory: " + format.format(freeMemory / 1024));
	    System.out.println("\tallocated memory: " + format.format(allocatedMemory / 1024));
	    System.out.println("\tmax memory: " + format.format(maxMemory / 1024));
	    System.out.println("\ttotal free memory: " + format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024));
	}
	
	
	/**
	 * This plots the given catalog using GMT.
	 * @param catalog
	 * @param outputDir
	 * @param outputPrefix
	 * @param display
	 * @throws IOException
	 * @throws GMT_MapException
	 */
	public static void plotCatalogGMT(List<? extends ObsEqkRupture> catalog, File outputDir, String outputPrefix, boolean display)
			throws IOException, GMT_MapException {
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(2.5d, 8.5d);
		
		List<LocationList> faults = Lists.newArrayList();
		List<Double> valuesList = Lists.newArrayList();
		
		ArrayList<PSXYSymbol> symbols = Lists.newArrayList();
		
		for (ObsEqkRupture rup : catalog) {
			double mag = rup.getMag();
			if (rup.getRuptureSurface() == null) {
				// gridded
				Location hypo = rup.getHypocenterLocation();
				double width = mag - 2.5;
				if (width < 0.1)
					width = 0.1;
				width /= 50;
				symbols.add(new PSXYSymbol(new Point2D.Double(hypo.getLongitude(), hypo.getLatitude()),
						Symbol.CIRCLE, width, 0d, null, cpt.getColor((float)mag)));
			} else {
				// fault based
				faults.add(rup.getRuptureSurface().getEvenlyDiscritizedPerimeter());
				valuesList.add(mag);
			}
		}
		
		Region region = new CaliforniaRegions.RELM_TESTING();
		boolean skipNans = false;
		String label = "Magnitude";
		
		System.out.println("Making map with "+faults.size()+" fault based ruptures");
		
		double[] values = Doubles.toArray(valuesList);
		GMT_Map map = FaultBasedMapGen.buildMap(cpt, faults, values, null, 1d, region, skipNans, label);
		map.setSymbols(symbols);
		
		FaultBasedMapGen.plotMap(outputDir, outputPrefix, display, map);
	}
	
	
	/**
	 * This will return the fault system solution index of the given rupture, or -1 if it is a gridded
	 * seismicity rupture. This assumes that the Nth rupture index has already been set in the given rupture.
	 * 
	 * @param rup
	 * @param erf
	 * @return
	 */
	public static int getFSSIndex(ETAS_EqkRupture rup, FaultSystemSolutionERF erf) {
		if (rup.getFSSIndex() >= 0)
			return rup.getFSSIndex();
		int nthIndex = rup.getNthERF_Index();
		Preconditions.checkState(nthIndex >= 0, "No Nth rupture index!");
		int sourceIndex;
		try {
			sourceIndex = erf.getSrcIndexForNthRup(nthIndex);
		} catch (Exception e) {
			// it's a grid source that's above our max because we are using a different min mag cutoff
			return -1;
		}
		if (sourceIndex < erf.getNumFaultSystemSources())
			return erf.getFltSysRupIndexForSource(sourceIndex);
		return -1;
	}
	
	/**
	 * This will set the fault system solution index in each ETAS rupture from the Nth rupture index. 
	 * @param catalog
	 * @param erf
	 */
	public static void loadFSSIndexesFromNth(List<ETAS_EqkRupture> catalog, FaultSystemSolutionERF erf) {
		for (ETAS_EqkRupture rup : catalog) {
			int fssIndex = getFSSIndex(rup, erf);
			if (fssIndex >= 0)
				rup.setFSSIndex(fssIndex);
		}
	}
	
	/**
	 * This will set the rupture surface in each ETAS_EqkRupture with that from the given ERF using the 
	 * Nth rupture index in each ETAS_EqkRupture (e.g., use this when the catalog is read from a file that
	 * does not contain the finite rupture-surface data).  This assumes compatibility between the catalog 
	 * and erf.  TODO Is this latter assumption tested anywhere?
	 * 
	 * @param catalog
	 * @param erf
	 */
	public static void loadFSSRupSurfaces(List<ETAS_EqkRupture> catalog, FaultSystemSolutionERF erf) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		for (ETAS_EqkRupture rup : catalog) {
			int fssIndex = getFSSIndex(rup, erf);
			if (fssIndex >= 0) {
				Preconditions.checkState((float)rup.getMag() == (float)rupSet.getMagForRup(fssIndex),
						"Magnitude discrepancy: "+(float)rup.getMag()+" != "+(float)rupSet.getMagForRup(fssIndex));
				rup.setRuptureSurface(rupSet.getSurfaceForRupupture(fssIndex, 1d, false));
			}
		}
	}
	
	/**
	 * This will return a catalog that contains only ruptures that are, or are children/grandchildren/etc of the given
	 * parent event ID. This assumes that events are in chronological order.
	 * 
	 * @param catalog
	 * @param parentID
	 * @return
	 */
	public static List<ETAS_EqkRupture> getChildrenFromCatalog(List<ETAS_EqkRupture> catalog, int parentID) {
		List<ETAS_EqkRupture> ret = Lists.newArrayList();
		HashSet<Integer> parents = new HashSet<Integer>();
		parents.add(parentID);
		
		for (ETAS_EqkRupture rup : catalog) {
			if (parents.contains(rup.getParentID()) || rup.getID() == parentID) {
				// it's in the chain
				ret.add(rup);
				parents.add(rup.getID());
			}
		}
		
		return ret;
	}
	
	public static List<ETAS_EqkRupture> getAboveMagPreservingChain(List<ETAS_EqkRupture> catalog, double minMag) {
		Map<Integer, ETAS_EqkRupture> catalogMap = Maps.newHashMap();
		int minID = Integer.MAX_VALUE;
		for (ETAS_EqkRupture rup : catalog) {
			catalogMap.put(rup.getID(), rup);
			if (rup.getID() < minID)
				minID = rup.getID();
		}
		
		HashSet<Integer> idsForInclusion = new HashSet<Integer>();
		
		for (ETAS_EqkRupture rup : catalog) {
			Integer id = rup.getID();
			double mag = rup.getMag();
			if (mag >= minMag) {
				idsForInclusion.add(id);
				// now add any previous events in the chain
				while (rup.getParentID() >= 0) {
					Integer parentID = rup.getParentID();
					if (parentID < minID)
						// historical or scenario event
						break;
					if (idsForInclusion.contains(parentID))
						// already been through this chain
						break;
					idsForInclusion.add(parentID);
					rup = catalogMap.get(parentID);
					Preconditions.checkNotNull(rup,
							"Chain broken, no rup found for id=%s, filtered input catalog?", parentID);
				}
			}
		}
		
		List<ETAS_EqkRupture> ret = Lists.newArrayList();
		
		for (Integer id : idsForInclusion)
			ret.add(catalogMap.get(id));
		
		// now sort
		Collections.sort(ret, eventComparator);
		
		return ret;
	}
	
	public static final Comparator<ETAS_EqkRupture> eventComparator = new Comparator<ETAS_EqkRupture>() {

		@Override
		public int compare(ETAS_EqkRupture o1, ETAS_EqkRupture o2) {
			int ret = new Long(o1.getOriginTime()).compareTo(o2.getOriginTime());
			if (ret == 0)
				ret = new Integer(o1.getID()).compareTo(o2.getID());
			return ret;
		}
	};
	
	/**
	 * This will return a catalog that contains only ruptures that are direct children of the given parent ID,
	 * the parent rupture and any further generations are excluded
	 * 
	 * @param catalog
	 * @param parentID
	 * @return
	 */
	public static List<ETAS_EqkRupture> getPrimaryAftershocks(List<ETAS_EqkRupture> catalog, int parentID) {
		List<ETAS_EqkRupture> ret = Lists.newArrayList();
		
		for (ETAS_EqkRupture rup : catalog)
			if (rup.getParentID() == parentID)
				ret.add(rup);
		
		return ret;
	}
	
	/**
	 * This will return a catalog that contains only ruptures that are of the given generation
	 * 
	 * @param catalog
	 * @param generation
	 * @return
	 */
	public static List<ETAS_EqkRupture> getByGeneration(List<ETAS_EqkRupture> catalog, int generation) {
		List<ETAS_EqkRupture> ret = Lists.newArrayList();
		
		for (ETAS_EqkRupture rup : catalog)
			if (rup.getGeneration() == generation)
				ret.add(rup);
		
		return ret;
	}
	
	
	/**
	 * This will return a catalog that contains only ruptures that are primary aftershocks (first generation)
	 * 
	 * @param catalog
	 * @return
	 */
	public static List<ETAS_EqkRupture> getPrimaryAftershocks(List<ETAS_EqkRupture> catalog) {
		List<ETAS_EqkRupture> ret = Lists.newArrayList();
		
		for (ETAS_EqkRupture rup : catalog)
			if (rup.getGeneration() == 1)
				ret.add(rup);
		
		return ret;
	}
	
	public static double getMaxMag(List<ETAS_EqkRupture> catalog) {
		double maxMag = Double.NEGATIVE_INFINITY;
		for (ETAS_EqkRupture rup : catalog)
			maxMag = Math.max(maxMag, rup.getMag());
		return maxMag;
	}

	/**
	 * Generates a scatter plot of the number of aftershocks vs the maximum aftershock magnitude for a given
	 * suite of ETAS simulated catalogs. If parentID is supplied, the catalogs will first be filtered to only
	 * contain descendants of that rupture.
	 * 
	 * @param catalogs
	 * @param parentID
	 */
	public static void plotMaxMagVsNumAftershocks(List<List<ETAS_EqkRupture>> catalogs, int parentID) {
		try {
			plotMaxMagVsNumAftershocks(catalogs, parentID, 0d, null, null);
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
	}
	
	/**
	 * Generates a scatter plot of the number of aftershocks vs the maximum aftershock magnitude for a given
	 * suite of ETAS simulated catalogs. If parentID is supplied, the catalogs will first be filtered to only
	 * contain descendants of that rupture.
	 * 
	 * If outputFile is non null, plot will be written to the given file instead of displayed interactively.
	 * 
	 * If title is non null, the plot title will be replaced with the given title
	 * 
	 * @param catalogs
	 * @param parentID
	 * @param mainShockMag
	 * @param outputFile
	 * @param title
	 * @throws IOException
	 */
	public static void plotMaxMagVsNumAftershocks(List<List<ETAS_EqkRupture>> catalogs, int parentID,
			double mainShockMag, File outputFile, String title) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		for (List<ETAS_EqkRupture> catalog : catalogs) {
			if (parentID >= 0)
				catalog = getChildrenFromCatalog(catalog, parentID);
			double maxMag = 0d;
			for (ETAS_EqkRupture rup : catalog) {
				double mag = rup.getMag();
				if (mag > maxMag)
					maxMag = mag;
			}
			scatter.set(maxMag, (double)catalog.size());
		}
		
		List<PlotElement> elems = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		int bufferSize = 20;
		if (scatter.getMaxY() < 300)
			bufferSize = 5;
		
		double minX = scatter.getMinX() - 0.1;
		double maxX = scatter.getMaxX() + 0.1;
		double minY = scatter.getMinY() - bufferSize;
		double maxY = scatter.getMaxY() + bufferSize;
		
		if (mainShockMag > 0) {
			DefaultXY_DataSet magLine = new DefaultXY_DataSet();
			magLine.setName("Mainshock Magnitude");
			magLine.set(mainShockMag, 0);
			magLine.set(mainShockMag, minY);
			magLine.set(mainShockMag, 0.5*(minY+maxY));
			magLine.set(mainShockMag, maxY);
			magLine.set(mainShockMag, maxX*5);
			elems.add(magLine);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		}
		
		elems.add(scatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
		if (title == null)
			title = "Max Aftershock Mag vs Num Afershocks";
		PlotSpec spec = new PlotSpec(elems, chars, title,
				"Max Aftershock Mag", "Number of Aftershocks");
		
		if (mainShockMag > 0) {
			// now annotation
			int numAbove = 0;
			int tot = scatter.size();
			for (Point2D pt : scatter) {
				if (pt.getX() > mainShockMag)
					numAbove++;
			}
			
			String text = numAbove+"/"+tot+" ("+(float)(100d*(double)numAbove/(double)tot)+" %) above M"+(float)mainShockMag;
			
			if (title != null)
				System.out.println(title+": "+text);
			
			List<XYTextAnnotation> annotations = Lists.newArrayList();
			XYTextAnnotation ann = new XYTextAnnotation(text, minX+(maxX-minX)*0.2, minY+(maxY-minY)*0.95);
			ann.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
			annotations.add(ann);
			spec.setPlotAnnotations(annotations);
		}
		
		if (outputFile == null) {
			GraphWindow gw = new GraphWindow(spec);
			gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
			gw.setAxisRange(minX, maxX, minY, maxY);
			gw.setYLog(true);
		} else {
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(20);
			gp.setPlotLabelFontSize(21);
			gp.setBackgroundColor(Color.WHITE);
			
			gp.setUserBounds(minX, maxX, minY, maxY);
			gp.setYLog(true);
			gp.drawGraphPanel(spec);
			gp.getChartPanel().setSize(1000, 800);
			
			if (outputFile.getName().toLowerCase().endsWith(".png"))
				gp.saveAsPNG(outputFile.getAbsolutePath());
			else
				gp.saveAsPDF(outputFile.getAbsolutePath());
		}
		
	}
	
	
	
	/**
	 * This returns the indices of the highest values in given IntegerPDF_FunctionSampler 
	 * for the specified number of points, sorted from highest to lowest.
	 * @param valsArray
	 * @param numValues
	 * @return
	 */
	public static int[] getIndicesForHighestValuesInArray(IntegerPDF_FunctionSampler sampler, int numValues) {
		return getIndicesForHighestValuesInArray(sampler.getY_valuesArray(), numValues);
	}

	
	
	
	/**
	 * This returns the indices of the highest values in the given array for the specified number of points, 
	 * sorted from highest to lowest.
	 * @param valsArray
	 * @param numValues
	 * @return
	 */
	public static int[] getIndicesForHighestValuesInArray(double[] valsArray, int numValues) {
		
		// this class pairs a probability with an index for sorting
		class ProbPairing implements Comparable<ProbPairing> {
			private double value;
			private int index;
			private ProbPairing(double prob, int index) {
				this.value = prob;
				this.index = index;
			}

			@Override
			public int compareTo(ProbPairing o) {
				// negative so biggest first
				return -Double.compare(value, o.value);
			}

		};
		

		// this stores the minimum probability currently in the list
		double curMinProb = Double.POSITIVE_INFINITY;
		// list of probabilities. only the highest numToList values will be kept in this list
		// this list is always kept sorted, highest to lowest
		List<ProbPairing> pairings = new ArrayList<ProbPairing>(numValues+5);
		for(int i=0;i<valsArray.length;i++) {
			double value = valsArray[i];
			if (value < curMinProb && pairings.size() == numValues)
				// already below the current min, skip
				continue;
			ProbPairing pairing = new ProbPairing(value, i);
			int index = Collections.binarySearch(pairings, pairing);
			if (index < 0) {
				// not in there yet, calculate insertion index from binary search result
				index = -(index + 1);
			}
			pairings.add(index, pairing);
			// remove if we just made it too big
			if (pairings.size() > numValues)
				pairings.remove(pairings.size()-1);
			// reset current min
			curMinProb = pairings.get(pairings.size()-1).value;
		}

		// sanity checks
		double prevProb = Double.POSITIVE_INFINITY;
		for (ProbPairing pairing : pairings) {
			Preconditions.checkState(prevProb >= pairing.value, "pairing list isn't sorted?  prevProb="+
		prevProb+",  pairing.value="+pairing.value);
			prevProb = pairing.value;
		}

		int[] indices = new int[numValues];
		int i=0;
		for(ProbPairing pairing : pairings) {
			indices[i]=pairing.index;
			i++;
		}
		
		return indices;

	}
	
	
	public static void plotRateOverTime(List<ETAS_EqkRupture> catalog, double startTimeYears, double durationYears,
			double binWidthDays, File outputDir, String prefix, int plotWidthPixels,
			double annotateMinMag, double annotateBinWidth) throws IOException {
		Preconditions.checkState(!catalog.isEmpty(), "Empty catalog");
		long actualOT = catalog.get(0).getOriginTime();
		long actualMaxOT = catalog.get(catalog.size()-1).getOriginTime();
		long startOT;
		if (startTimeYears > 0)
			startOT = actualOT + (long)(startTimeYears*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		else
			startOT = actualOT;
		Preconditions.checkState(startOT < actualMaxOT, "Start time is after end of catalog");
		long binWidth = (long)(binWidthDays*ProbabilityModelsCalc.MILLISEC_PER_DAY);
		long maxOT = (long)(startOT + durationYears*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		
		if (maxOT > actualMaxOT) {
			maxOT = actualMaxOT;
//			durationYears = (double)(maxOT - startOT)/(double)ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		}
		int numBins = (int)((maxOT - startOT)/binWidth);
		Preconditions.checkState(numBins > 1, "Must have at least 2 bins!");
		
		boolean xAxisDays = durationYears < 2d;
		
		double funcDelta;
		if (xAxisDays)
			funcDelta = binWidthDays;
		else
			funcDelta = binWidthDays/365.25;
		double funcMin = funcDelta*0.5;
		
		EvenlyDiscretizedFunc rateFunc = new EvenlyDiscretizedFunc(funcMin, numBins, funcDelta);
		rateFunc.setName("Catalog");
		EvenlyDiscretizedFunc poissonFunc = new EvenlyDiscretizedFunc(funcMin, numBins, funcDelta);
		poissonFunc.setName("Poisson");
		
		// create poisson catalog
		List<ETAS_EqkRupture> poissonCatalog = getRandomizedCatalog(catalog);
		
		List<DefaultXY_DataSet> anns = populateRateFunc(catalog, rateFunc, startOT, binWidth,
				xAxisDays, annotateMinMag, annotateBinWidth);
		List<DefaultXY_DataSet> poissonAnns = populateRateFunc(poissonCatalog, poissonFunc, startOT,
				binWidth, xAxisDays, annotateMinMag, annotateBinWidth);
		
		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		funcs.add(poissonFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		funcs.add(rateFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
		
		double maxY = Math.max(rateFunc.getMaxY(), poissonFunc.getMaxY());
		double mainAnnY = maxY * 12.5;
		double poissonAnnY = maxY * 5;
		
		float minAnnSize = 10f;
		float scalarEach = 10;
		for (int i=anns.size(); --i>=0;) {
			DefaultXY_DataSet ann = anns.get(i);
			if (ann == null || ann.size() == 0)
				continue;
			funcs.add(ann);
			float symbolWidth = minAnnSize + i*scalarEach;
			ann.scale(mainAnnY);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, symbolWidth, Color.RED));
		}
		for (int i=poissonAnns.size(); --i>=0;) {
			DefaultXY_DataSet ann = poissonAnns.get(i);
			if (ann == null || ann.size() == 0)
				continue;
			funcs.add(ann);
			float symbolWidth = minAnnSize + i*scalarEach;
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, symbolWidth, Color.BLACK));
			ann.scale(poissonAnnY);
			ann.setName(null);
		}
		
		String xAxisLabel;
		if (xAxisDays)
			xAxisLabel = "Time (Days)";
		else
			xAxisLabel = "Time (Years)";
		String yAxisLabel;
		if ((float)binWidthDays == 1f)
			yAxisLabel = "Daily Rate";
		else if ((float)binWidthDays == 7f)
			yAxisLabel = "Weekly Rate";
		else if ((float)binWidthDays == 30f)
			yAxisLabel = "Monthly Rate";
		else
			yAxisLabel = "Rate per "+(float)binWidthDays+" Days";
		PlotSpec spec = new PlotSpec(funcs, chars, "Rate vs Time", xAxisLabel, yAxisLabel);
		spec.setLegendVisible(true);
		
		String fName;
		if (prefix == null)
			fName = "";
		else
			fName = prefix+"_";
		fName += "rate_over_time_"+(int)durationYears+"yrs";
		if (startTimeYears > 0)
			fName += "_start"+(int)startTimeYears;
		
		// now plot
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		// set user bounds?
		gp.setUserBounds(new Range(0d, durationYears), null);
		
		ETAS_MultiSimAnalysisTools.setFontSizes(gp);
		
		gp.drawGraphPanel(spec, false, true);
		if (plotWidthPixels <= 0)
			plotWidthPixels = (int)(durationYears * 2);
		gp.getChartPanel().setSize(plotWidthPixels, 800);
		gp.saveAsPNG(new File(outputDir, fName+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, fName+".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, fName+".txt").getAbsolutePath());
	}
	
	private static List<DefaultXY_DataSet> populateRateFunc(List<ETAS_EqkRupture> catalog, EvenlyDiscretizedFunc rateFunc,
			long startOT, long binWidth, boolean xAxisDays, double annotateMinMag, double annotateBinWidth) {
		List<DefaultXY_DataSet> annotations = Lists.newArrayList();
		
		int catalogIndex = 0;
		
		for (int i=0; i<rateFunc.size(); i++) {
			int num = 0;
			long binStart = startOT + binWidth*i;
			long binEnd = binStart + binWidth;
//			if (i < 20)
//				System.out.println("Bin "+i+": "+binStart+" => "+binEnd);
			
			for (int n=catalogIndex; n<catalog.size(); n++) {
				ETAS_EqkRupture rup = catalog.get(n);
				long ot = rup.getOriginTime();
//				if (i < 20)
//					System.out.println("\trup "+n+": "+ot);
				if (ot >= binEnd)
					break;
				catalogIndex++;
				if (ot >= binStart) {
					num++;
					if (annotateMinMag > 0 && rup.getMag() >= annotateMinMag) {
						int annIndex = (int)((rup.getMag()-annotateMinMag)/annotateBinWidth);
						while (annIndex >= annotations.size()) {
//							System.out.println("Adding annotation! current size="+annotations.size());
							DefaultXY_DataSet xy = new DefaultXY_DataSet();
							int index = annotations.size();
							float minMag = (float)(annotateMinMag + index*annotateBinWidth);
							xy.setName("M>="+minMag);
							annotations.add(xy);
						}
						double t;
						if (xAxisDays)
							t = (double)(ot - startOT)/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
						else
							t = (double)(ot - startOT)/(double)ProbabilityModelsCalc.MILLISEC_PER_YEAR;
						annotations.get(annIndex).set(t, 1d);
					}
				}
			}
			rateFunc.set(i, num);
		}
		
		return annotations;
	}
	
	/**
	 * Creates a randomized catalog using the time of the first and last ruptures. Ruptures are cloned
	 * and the original catalog/ruptures are unaltered.
	 * @param catalog randomized catalog, in order
	 * @return
	 */
	public static List<ETAS_EqkRupture> getRandomizedCatalog(List<ETAS_EqkRupture> catalog) {
		Preconditions.checkArgument(catalog.size() > 1, "Can't randomize catalog with less than 2 ruptures");
		long ot = catalog.get(0).getOriginTime();
		long durationMillis = catalog.get(catalog.size()-1).getOriginTime()-ot;
		List<ETAS_EqkRupture> randomized = Lists.newArrayList();
		for (ETAS_EqkRupture rup : catalog) {
			long time = ot + (long)(Math.random()*durationMillis);
			ETAS_EqkRupture clone = (ETAS_EqkRupture)rup.clone();
			clone.setOriginTime(time);
			randomized.add(clone);
		}
		Collections.sort(randomized, eventComparator);
		return randomized;
	}
	
	public static void main(String[] args) throws IOException, GMT_MapException, DocumentException {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2016_02_17-spontaneous-1000yr-scaleMFD1p14-full_td-subSeisSupraNucl-gridSeisCorr/");
		File catalogFile = new File(simDir, "individual_binary/catalog_000.bin");
		List<ETAS_EqkRupture> catalog = ETAS_CatalogIO.loadCatalogBinary(catalogFile);
		File outputDir = new File(simDir, "rate_over_time");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
//		double startTimeYears = 0;
//		double durationYears = 200;
//		int plotWidthPixels = 2000;
//		double binWidthDays = 30;
//		double startTimeYears = 20;
//		double durationYears = 30;
//		int plotWidthPixels = 2000;
//		double binWidthDays = 30;
		double startTimeYears = 550;
		double durationYears = 100;
		int plotWidthPixels = 2000;
		double binWidthDays = 30;
		String prefix = null;
//		double annotateMinMag = 6;
//		double annotateBinWidth = 0.5;
		double annotateMinMag = 0; // no annotations
		double annotateBinWidth = 0.5;
		plotRateOverTime(catalog, startTimeYears, durationYears, binWidthDays, outputDir, prefix, plotWidthPixels,
				annotateMinMag, annotateBinWidth);
//		File catalogFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/2014_05_28-mojave_7/"
//				+ "results/sim_003/simulatedEvents.txt");
//		
//		// needed to laod finite fault surfaces
//		FaultSystemSolution baSol = FaultSystemIO.loadSol(
//				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/"
//				+ "InversionSolutions/2013_05_10-ucerf3p3-production-10runs_"
//				+ "COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(baSol);
//		erf.updateForecast();
//		
//		List<ETAS_EqkRupture> catalog = loadCatalog(catalogFile, 5d);
//		loadFSSRupSurfaces(catalog, erf);
//		
//		plotCatalogGMT(catalog, new File("/tmp"), "etas_catalog", true);
		
//		File catalogsDir = new File("/home/kevin/OpenSHA/UCERF3/cybershake_etas/sims/2014_07_30-bombay_beach-extra/results");
//		File catalogsDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/2014_interns/2014_07_09-64481/results");
//		List<List<ETAS_EqkRupture>> catalogs = Lists.newArrayList();
//		for (File subdir : catalogsDir.listFiles()) {
//			if (!subdir.getName().startsWith("sim_") || !subdir.isDirectory())
//				continue;
//			if (!MPJ_ETAS_Simulator.isAlreadyDone(subdir))
//				continue;
//			File catalogFile = new File(subdir, "simulatedEvents.txt");
//			catalogs.add(loadCatalog(catalogFile));
//		}
//		plotMaxMagVsNumAftershocks(catalogs, 0);
	}

}
