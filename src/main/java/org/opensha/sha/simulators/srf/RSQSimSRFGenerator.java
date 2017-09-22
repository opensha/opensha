package org.opensha.sha.simulators.srf;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.iden.EventIDsRupIden;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;

import com.google.common.base.Preconditions;

public class RSQSimSRFGenerator {
	
	public static enum SRFInterpolationMode {
		NONE,
		ADJ_VEL,
		CONST_VEL_ADJ_LEN
	}
	
	public static List<SRF_PointData> buildSRF(RSQSimEventSlipTimeFunc func, List<SimulatorElement> patches,
			double dt, SRFInterpolationMode mode) {
		List<SRF_PointData> srfs = new ArrayList<>();
		for (SimulatorElement patch : patches)
			srfs.add(buildSRF(func, patch, dt, mode));
		return srfs;
	}
	
	public static SRF_PointData buildSRF(RSQSimEventSlipTimeFunc func, SimulatorElement patch, double dt, SRFInterpolationMode mode) {
		// convert to relative function (only done if needed, and cached as necessary so fast)
		func = func.asRelativeTimeFunc();
		
		Location loc = patch.getCenterLocation();
		int patchID = patch.getID();
		double tStart = func.getTimeOfFirstSlip(patchID);
		double tEnd = func.getTimeOfLastSlip(patchID);
		FocalMechanism focal = patch.getFocalMechanism();
		double totSlip = func.getCumulativeEventSlip(patchID, func.getEndTime());
		int numSteps = (int)Math.ceil((tEnd - tStart)/dt);
		double[] slipVels = new double[numSteps];
		double constSlipPerStep = dt*func.getSlipVelocity();
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
			case CONST_VEL_ADJ_LEN:
				double targetSlip = func.getCumulativeEventSlip(patchID, time+dt);
				double slipIfOn = curTotSlip + constSlipPerStep;
				double deltaOn = Math.abs(targetSlip - slipIfOn);
				double deltaOff = Math.abs(targetSlip - curTotSlip);
				if (deltaOn < deltaOff)
					slipVels[i] = func.getSlipVelocity();
				break;

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
				PlotLineType.SOLID, 3f, PlotSymbol.FILLED_CIRCLE, 4f, Color.BLACK);
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
				vel = func.getSlipVelocity();
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
		
		List<XYTextAnnotation> slipAnns = new ArrayList<>();
		double annX = 0.1;
		double totSlip = func.getCumulativeEventSlip(patchID, func.getEndTime());
		double slipMaxY = totSlip * 1.05;
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
		XYTextAnnotation totAnn = new XYTextAnnotation("Total Slip: "+(float)totSlip, annX, slipMaxY*(1d-0.05*slipFuncs.size()));
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
				c = Color.GREEN;
				break;
			case CONST_VEL_ADJ_LEN:
				c = Color.BLUE;
				break;

			default:
				throw new IllegalStateException("Unknown interpolation mode: "+mode);
			}
//			PlotCurveCharacterstics srfChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, PlotSymbol.CIRCLE, 4f, c);
			PlotCurveCharacterstics srfChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, c);
			
			double[] slips = srf.getCumulativeSlips1();
			double[] vels = srf.getVelocities1();
			
			DiscretizedFunc slipFunc = new ArbitrarilyDiscretizedFunc();
			slipFunc.setName(mode.toString());
			for (int i=0; i<slips.length; i++)
				slipFunc.set(srf.getTime(i), slips[i]);
			slipFuncs.add(slipFunc);
			slipChars.add(srfChar);
			
			XYTextAnnotation srfAnn = new XYTextAnnotation("Interp "+mode+" Slip: "+(float)slipFunc.getMaxY(),
					annX, slipMaxY*(1d-0.05*slipFuncs.size()));
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
		PlotSpec slipSpec = new PlotSpec(slipFuncs, slipChars, title, xAxisLabel, "Slip (m)");
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
		yRanges.add(new Range(0d, func.getSlipVelocity()*1.05));
		
		gp.drawGraphPanel(specs, false, false, xRanges, null);

		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(1000, 1000);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}

	public static void main(String[] args) throws IOException {
		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2194_long");
		File geomFile = new File(catalogDir, "zfault_Deepen.in");
		File transFile = new File(catalogDir, "trans.rundir2194_long.out");
		
		// must be sorted!
//		int[] eventIDs = { 399681 };
		int[] eventIDs = { 136704, 145982 };
		
		boolean plotIndividual = true;
		int plotMod = 10;
		boolean writeSRF = true;
		
		double slipVel = 1d;
		
		File outputDir = new File(catalogDir, "event_srfs");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		System.out.println("Loading geometry...");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		System.out.println("Loaded "+elements.size()+" elements");
		List<RuptureIdentifier> loadIdens = new ArrayList<>();
//		RuptureIdentifier loadIden = new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
//				new MagRangeRuptureIdentifier(minMag, maxMag),
//				new CatalogLengthLoadIden(maxLengthYears));
		loadIdens.add(new EventIDsRupIden(eventIDs));
		System.out.println("Loading events...");
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
		System.out.println("Loaded "+events.size()+" events");
		Preconditions.checkState(events.size() == eventIDs.length);

		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
		
		SRFInterpolationMode[] modes = SRFInterpolationMode.values();
		double[] dts = { 0.1, 0.05 };
		double srfVersion = 1.0;
		
		int patchDigits = (elements.size()+"").length();
		
		for (RSQSimEvent event : events) {
			System.out.println("Event: "+event.getID()+", M"+(float)event.getMagnitude());
			int eventID = event.getID();
			RSQSimEventSlipTimeFunc func = new RSQSimEventSlipTimeFunc(transReader.getTransitions(event), slipVel);
			for (double dt : dts) {
				File eventOutputDir = new File(outputDir, "event_"+eventID+"_"+(float)dt+"s");
				System.out.println("dt="+dt+" => "+outputDir.getAbsolutePath());
				Preconditions.checkState(eventOutputDir.exists() || eventOutputDir.mkdir());
				
				ArrayList<SimulatorElement> patches = event.getAllElements();
				if (plotIndividual) {
					System.out.println("Plotting patch slip/vel functions");
					for (int i=0; i<patches.size(); i++) {
						if (i % plotMod > 0)
							continue;
						SimulatorElement patch = patches.get(i);
						String prefix = patch.getID()+"";
						while (prefix.length() < patchDigits)
							prefix = "0"+prefix;
						prefix = "patch_"+prefix;
						plotSlip(eventOutputDir, prefix, func, patch, dt, modes);
					}
				}
				
				if (writeSRF) {
					for (SRFInterpolationMode mode : modes) {
						File srfFile = new File(eventOutputDir.getAbsolutePath()+"_"+mode.name()+".srf");
						System.out.println("Generating SRF for dt="+(float)dt+", "+mode);
						List<SRF_PointData> srf = buildSRF(func, patches, dt, mode);
						SRF_PointData.writeSRF(srfFile, srf, srfVersion);
					}
				}
			}
		}
	}

}
