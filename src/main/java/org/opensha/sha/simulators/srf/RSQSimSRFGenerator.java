package org.opensha.sha.simulators.srf;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.iden.EventIDsRupIden;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.utils.SimulatorUtils;

import com.google.common.base.Preconditions;

public class RSQSimSRFGenerator {
	
	public static enum SRFInterpolationMode {
		NONE("None"),
		ADJ_VEL("Adj. Velocity"),
		LIN_TAPER_VEL("Linear Taper");
//		CONST_VEL_ADJ_LEN
		
		private String name;
		private SRFInterpolationMode(String name) {
			this.name = name;
		}
	}
	
	public static List<SRF_PointData> buildSRF(RSQSimEventSlipTimeFunc func, List<SimulatorElement> patches,
			double dt, SRFInterpolationMode mode) {
		List<SRF_PointData> srfs = new ArrayList<>();
		for (SimulatorElement patch : patches)
			srfs.add(buildSRF(func, patch, dt, mode));
		return srfs;
	}
	
	private static class Taper {
		private double upTaperStart, upTaperEnd, downTaperStart, downTaperEnd, vel;

		public Taper(double upTaperStart, double upTaperEnd, double downTaperStart, double downTaperEnd, double vel) {
			super();
			this.upTaperStart = upTaperStart;
			this.upTaperEnd = upTaperEnd;
			Preconditions.checkState(upTaperEnd > upTaperStart);
			this.downTaperStart = downTaperStart;
			this.downTaperEnd = downTaperEnd;
			Preconditions.checkState(downTaperEnd > downTaperStart);
			Preconditions.checkState(downTaperStart > upTaperEnd);
			this.vel = vel;
		}
		
		public double getVel(double time) {
			if (time < upTaperStart || time > downTaperEnd)
				// outside of taper
				return 0d;
			if (time < upTaperEnd) {
				// in the up taper
				double fract = (time - upTaperStart)/(upTaperEnd - upTaperStart);
				return fract*vel;
			}
			if (time > downTaperStart) {
				// in the down taper
				double fract = (time - downTaperStart)/(downTaperEnd - downTaperStart);
				return (1d-fract)*vel;
			}
			return vel;
		}
	}
	
	public static SRF_PointData buildSRF(RSQSimEventSlipTimeFunc func, SimulatorElement patch, double dt, SRFInterpolationMode mode) {
		// convert to relative function (only done if needed, and cached as necessary so fast)
		func = func.asRelativeTimeFunc();
		
		Location loc = patch.getCenterLocation();
		int patchID = patch.getID();
		double tStart = func.getTimeOfFirstSlip(patchID);
		Preconditions.checkState(Double.isFinite(tStart), "Non-finite tStart for patch %s? %s", (Integer)patchID, (Double)tStart);
		double tEnd = func.getTimeOfLastSlip(patchID);
		Preconditions.checkState(Double.isFinite(tEnd), "Non-finite tEnd for patch %s? %s", (Integer)patchID, (Double)tEnd);
		
		List<Taper> tapers = null;
		if (mode == SRFInterpolationMode.LIN_TAPER_VEL) {
			tapers = new ArrayList<>();
			for (RSQSimStateTime trans : func.getTransitions(patchID)) {
				if (trans.getState() != RSQSimState.EARTHQUAKE_SLIP)
					continue;
				double s = trans.getStartTime();
				double e = trans.getEndTime();
				double d = e - s;
				double taperLen = d*0.1;
				double upTaperStart = s - 0.5*taperLen;
				double upTaperEnd = s + 0.5*taperLen;
				double downTaperStart = e - 0.5*taperLen;
				double downTaperEnd = e + 0.5*taperLen;
				tStart = Math.min(tStart, upTaperStart);
				tEnd = Math.max(tEnd, downTaperEnd);
				double slipVel = func.getVelocity(trans);
				Preconditions.checkState(Double.isFinite(slipVel) && slipVel > 0, "Bad slipVel for patch %s? %s", (Integer)patchID, (Double)slipVel);
				tapers.add(new Taper(upTaperStart, upTaperEnd, downTaperStart, downTaperEnd, slipVel));
			}
		}
		
		FocalMechanism focal = patch.getFocalMechanism();
		double totSlip = func.getCumulativeEventSlip(patchID, func.getEndTime());
		int numSteps = (int)Math.ceil((tEnd - tStart)/dt);
		double[] slipVels = new double[numSteps];
//		double constSlipPerStep = dt*slipVel;
		double curTotSlip = 0;
		
		for (int i=0; i<numSteps; i++) {
			double time = tStart + dt*i;
			switch (mode) {
			case NONE:
				slipVels[i] = func.getVelocity(patchID, time);
				break;
			case ADJ_VEL:
				double slipStart = func.getCumulativeEventSlip(patchID, time);
				double slipEnd = func.getCumulativeEventSlip(patchID, time+dt);
				slipVels[i] = (slipEnd - slipStart)/dt;
				break;
			case LIN_TAPER_VEL:
				for (Taper taper : tapers)
					slipVels[i] += taper.getVel(time);
				break;
//			case CONST_VEL_ADJ_LEN:
//				double targetSlip = func.getCumulativeEventSlip(patchID, time+dt);
//				double slipIfOn = curTotSlip + constSlipPerStep;
//				double deltaOn = Math.abs(targetSlip - slipIfOn);
//				double deltaOff = Math.abs(targetSlip - curTotSlip);
//				if (deltaOn < deltaOff)
//					slipVels[i] = slipVel;
//				break;

			default:
				throw new IllegalStateException("Unknown interpolation mode: "+mode);
			}
			curTotSlip += slipVels[i]*dt;
		}
		return new SRF_PointData(loc, focal, patch.getArea(), tStart, dt, totSlip, slipVels);
	}
	
	public static void plotSlip(File outputDir, String prefix,
			RSQSimEventSlipTimeFunc func, SimulatorElement patch, double dt, SRFInterpolationMode... modes) throws IOException {
		List<DiscretizedFunc> slipFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> slipChars = new ArrayList<>();
		
		List<DiscretizedFunc> velFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> velChars = new ArrayList<>();
		
		func = func.asRelativeTimeFunc();
		
		// build actual funcs
		int patchID = patch.getID();
		PlotCurveCharacterstics actualChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, 4f, PlotSymbol.FILLED_CIRCLE, 5f, Color.BLACK);
		DiscretizedFunc origSlipFunc = func.getSlipFunc(patchID);
		DiscretizedFunc shiftedSlipFunc = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : origSlipFunc)
			shiftedSlipFunc.set(pt.getX(), pt.getY());
		shiftedSlipFunc.setName("Actual");
		slipFuncs.add(shiftedSlipFunc);
		slipChars.add(actualChar);
		
		for (RSQSimStateTime trans : func.getTransitions(patchID)) {
			double vel;
			if (trans.getState() == RSQSimState.EARTHQUAKE_SLIP)
				vel = func.getVelocity(trans);
			else
				vel = 0d;
			DiscretizedFunc velFunc = new ArbitrarilyDiscretizedFunc();
			if (velFuncs.isEmpty())
				velFunc.setName("Actual");
			velFunc.set(trans.getStartTime(), vel);
			velFunc.set(trans.getEndTime(), vel);
			velFuncs.add(velFunc);
			velChars.add(actualChar);
		}
		
		DecimalFormat slipDF = new DecimalFormat("0.000");
		
		List<XYTextAnnotation> slipAnns = new ArrayList<>();
		double annX = 0.1;
		double totSlip = func.getCumulativeEventSlip(patchID, func.getEndTime());
		double slipMaxY = totSlip * 1.05;
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
		XYTextAnnotation totAnn = new XYTextAnnotation("Total Slip: "+slipDF.format(totSlip), annX, slipMaxY*(1d-0.05*slipFuncs.size()));
		totAnn.setTextAnchor(TextAnchor.TOP_LEFT);
		totAnn.setFont(annFont);
		slipAnns.add(totAnn);
		
		double eventLen = func.getEndTime();
		
		for (SRFInterpolationMode mode : modes) {
			SRF_PointData srf = buildSRF(func, patch, dt, mode);
			Color c;
			switch (mode) {
			case NONE:
				c = Color.RED;
				break;
			case ADJ_VEL:
				c = Color.GRAY;
				break;
			case LIN_TAPER_VEL:
				c = Color.CYAN;
				break;
//			case CONST_VEL_ADJ_LEN:
//				c = Color.GREEN;
//				break;

			default:
				throw new IllegalStateException("Unknown interpolation mode: "+mode);
			}
//			PlotCurveCharacterstics srfChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, PlotSymbol.CIRCLE, 4f, c);
			PlotCurveCharacterstics srfChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, c);
			
			double[] slips = srf.getCumulativeSlips1();
			double[] vels = srf.getVelocities1();
			
			DiscretizedFunc slipFunc = new ArbitrarilyDiscretizedFunc();
			slipFunc.setName(mode.toString());
			for (int i=0; i<slips.length; i++)
				slipFunc.set(srf.getTime(i), slips[i]);
			slipFuncs.add(slipFunc);
			slipChars.add(srfChar);
			
			XYTextAnnotation srfAnn = new XYTextAnnotation("Interp "+mode.name+" Slip: "+slipDF.format(slipFunc.getMaxY()),
					annX, slipMaxY*(1d-0.06*slipFuncs.size()));
			srfAnn.setTextAnchor(TextAnchor.TOP_LEFT);
			srfAnn.setFont(annFont);
			srfAnn.setPaint(c);
			slipAnns.add(srfAnn);
			
			eventLen = Math.max(eventLen, slipFunc.getMaxX());
			
			for (int i=0; i<vels.length; i++) {
				DiscretizedFunc velFunc = new ArbitrarilyDiscretizedFunc();
				if (i == 0)
					velFunc.setName(mode.toString());
				velFunc.set(srf.getTime(i), vels[i]);
				velFunc.set(srf.getTime(i+1), vels[i]);
				velFuncs.add(velFunc);
				velChars.add(srfChar);
			}
		}
		
		String title = "SRF Validation";
		String xAxisLabel = "Time (seconds)";
		PlotSpec slipSpec = new PlotSpec(slipFuncs, slipChars, title, xAxisLabel, "Cumulative Slip (m)");
		slipSpec.setLegendVisible(true);
		slipSpec.setPlotAnnotations(slipAnns);
		
		PlotSpec velSpec = new PlotSpec(velFuncs, velChars, title, xAxisLabel, "Velocity (m/s)");
		velSpec.setLegendVisible(false);
		
		List<PlotSpec> specs = new ArrayList<>();
		specs.add(slipSpec);
		specs.add(velSpec);
		
		Range xAxisRange = new Range(0, eventLen);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setBackgroundColor(Color.WHITE);
		
		List<Range> xRanges = new ArrayList<>();
		xRanges.add(xAxisRange);
		
		List<Range> yRanges = new ArrayList<>();
		yRanges.add(new Range(0d, slipMaxY));
		yRanges.add(new Range(0d, func.getMaxSlipVel()*1.05));
		
		gp.drawGraphPanel(specs, false, false, xRanges, null);

		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(1000, 1000);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}

	public static void main(String[] args) throws IOException {
		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2585_1myr");
		File geomFile = new File(catalogDir, "zfault_Deepen.in");
		File transFile = new File(catalogDir, "trans.rundir2585_1myrs.out");
		boolean variableSlipSpeed = transFile.getName().startsWith("transV");
		
		int[] eventIDs = { 9955310 };
		
//		File catalogDir = new File("/data/kevin/simulators/catalogs/JG_UCERF3_millionElement");
//		File geomFile = new File(catalogDir, "UCERF3.D3.1.millionElements.flt");
//		File transFile = new File(catalogDir, "trans.UCERF3.D3.1.millionElements1.out");
//		
//		int[] eventIDs = { 4099020 };
		
		double[] timeScalars = { 1d };
		boolean[] velScales = { false};
//		double[] timeScalars = { 1d, 2d };
//		boolean[] velScales = { false, true };
		
		boolean plotIndividual = true;
		int plotMod = 10;
		boolean writeSRF = false;
		
		double slipVel = 1d;
		
		File outputDir = new File(catalogDir, "event_srfs");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		System.out.println("Loading geometry...");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
//		double meanArea = 0d;
//		for (SimulatorElement e : elements)
//			meanArea += e.getArea()/1000000d; // to km^2
//		meanArea /= elements.size();
//		System.out.println("Loaded "+elements.size()+" elements. Mean area: "+(float)meanArea+" km^2");
		List<RuptureIdentifier> loadIdens = new ArrayList<>();
////		RuptureIdentifier loadIden = new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
////				new MagRangeRuptureIdentifier(minMag, maxMag),
////				new CatalogLengthLoadIden(maxLengthYears));
		loadIdens.add(new EventIDsRupIden(eventIDs));
		System.out.println("Loading events...");
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
		System.out.println("Loaded "+events.size()+" events");
		Preconditions.checkState(events.size() == eventIDs.length);

		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(
				transFile, elements, variableSlipSpeed);
		
//		SRFInterpolationMode[] modes = SRFInterpolationMode.values();
//		double[] dts = { 0.1, 0.05 };
		SRFInterpolationMode[] modes = { SRFInterpolationMode.ADJ_VEL };
//		double[] dts = { 0.05 };
		double[] dts = { 1.0 };
//		double[] dts = {  };
		double srfVersion = 1.0;
		
		int patchDigits = (elements.size()+"").length();
		
		for (RSQSimEvent event : events) {
			System.out.println("Event: "+event.getID()+", M"+(float)event.getMagnitude());
			int eventID = event.getID();
//			RSQSimEventSlipTimeFunc func = new RSQSimEventSlipTimeFunc(transReader.getTransitions(event), slipVel);
			Map<Integer, Double> slipVels = new HashMap<>();
			for (int elemID : event.getAllElementIDs())
				slipVels.put(elemID, slipVel);
			RSQSimEventSlipTimeFunc func = new RSQSimEventSlipTimeFunc(transReader.getTransitions(event), slipVels, variableSlipSpeed);
			String eventStr = "event_"+eventID;
			
			SummaryStatistics patchSlipEventDurations = new SummaryStatistics();
			SummaryStatistics patchFractSlippingDurations = new SummaryStatistics();
			SummaryStatistics patchSlipTotalDurations = new SummaryStatistics();
			SummaryStatistics patchCounts = new SummaryStatistics();
			for (int patchID : slipVels.keySet()) {
				List<RSQSimStateTime> patchTrans = func.getTransitions(patchID);
				
				int count = 0;
				double totSlippingDuration = 0d;
				for (RSQSimStateTime state : patchTrans) {
					if (state.getState() == RSQSimState.EARTHQUAKE_SLIP) {
						count++;
						double duration = state.getDuration();
						patchSlipEventDurations.addValue(duration);
						totSlippingDuration += duration;
					}
				}
				
				double totDuration = func.getTimeOfLastSlip(patchID) - func.getTimeOfFirstSlip(patchID);
				patchSlipTotalDurations.addValue(totDuration);
				patchFractSlippingDurations.addValue(totSlippingDuration/totDuration);
				patchCounts.addValue(count);
			}
			
			System.out.println("Patches slipped an average of "+(float)patchCounts.getMean()
				+" times (range: "+(int)patchCounts.getMin()+" => "+(int)patchCounts.getMax()+")");
			System.out.println("Each slip event has an average duration of "+(float)patchSlipEventDurations.getMean()
				+"s and a range of "+(float)patchSlipEventDurations.getMin()+"s to "
					+(float)patchSlipEventDurations.getMax()+"s");
			System.out.println("The average total duration (from beginning of first slip to end of last slip on an "
					+ "individual patch) is "+(float)patchSlipTotalDurations.getMean()
					+"s (range: "+(float)patchSlipTotalDurations.getMin()+"s => "
					+(float)patchSlipTotalDurations.getMax()+"s)");
			System.out.println("Of the duration (as defined above), patches are slipping (on average) "
					+(float)(100d*patchFractSlippingDurations.getMean())+" % of the time (range: "
					+(float)(100d*patchFractSlippingDurations.getMin())+"% => "
					+(float)(100d*patchFractSlippingDurations.getMax())+"%)");
			
			for (double dt : dts) {
				ArrayList<SimulatorElement> patches = event.getAllElements();
				
				for (double timeScale : timeScalars) {
					boolean[] myVelScales;
					if (timeScale == 1d)
						myVelScales = new boolean[] { false };
					else
						myVelScales = velScales;
					for (boolean velScale : myVelScales) {
						String prefix = eventStr+"_"+(float)dt+"s";
						RSQSimEventSlipTimeFunc myFunc = func;
						if (timeScale != 1d) {
							prefix += "_timeScale"+(float)timeScale;
							if (velScale)
								prefix += "_velScale";
							myFunc = func.getTimeScaledFunc(timeScale, velScale);
						}
						File eventOutputDir = new File(outputDir, prefix);
						System.out.println("dt="+dt+" => "+outputDir.getAbsolutePath());
						Preconditions.checkState(eventOutputDir.exists() || eventOutputDir.mkdir());
						
						if (plotIndividual) {
							System.out.println("Plotting patch slip/vel functions");
							for (int i=0; i<patches.size(); i++) {
								if (i % plotMod > 0)
									continue;
								SimulatorElement patch = patches.get(i);
								prefix = patch.getID()+"";
								while (prefix.length() < patchDigits)
									prefix = "0"+prefix;
								prefix = "patch_"+prefix;
								plotSlip(eventOutputDir, prefix, myFunc, patch, dt, modes);
							}
						}
						
						if (writeSRF) {
							for (SRFInterpolationMode mode : modes) {
								File srfFile = new File(eventOutputDir.getAbsolutePath()+"_"+mode.name()+".srf");
								System.out.println("Generating SRF for dt="+(float)dt+", "+mode);
								List<SRF_PointData> srf = buildSRF(myFunc, patches, dt, mode);
								SRF_PointData.writeSRF(srfFile, srf, srfVersion);
							}
						}
					}
				}
			}
		}
	}

}
