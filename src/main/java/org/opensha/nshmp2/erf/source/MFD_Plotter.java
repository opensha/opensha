package org.opensha.nshmp2.erf.source;

import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.nshmp2.util.SourceType.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.util.Interpolate;
import org.opensha.nshmp2.util.FaultType;
import org.opensha.nshmp2.util.NSHMP_Utils;
import org.opensha.nshmp2.util.Utils;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.Lists;

/**
 * Utility for plotting mfds for faults or grid sources or both. Utility assumes
 * that magnitude range of interest in 4.55 to 8.05 with a delta of 0.1. Results
 * are undefined for MFDs that exceed this range.
 * 
 * This really only worksd for California
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class MFD_Plotter {

	// self consistent set of min, max, delta, and num
	private static final double M_MIN = 6.2;
	private static final double M_MAX = 8.2;
	private static final double M_DELTA = 0.05;
	private static final int M_NUM = 41;
	
//	private static final double grWt = 0.333;
//	private static final double chWt = 0.667;
//	private static String title = "Carson Range-Kings Canyon GR 33% CH 67%";

//	private static final double grWt = 0.667;
//	private static final double chWt = 0.333;
//	private static String title = "Carson Range-Kings Canyon GR 67% CH 33%";


//	private static final double grWt = 0.5;
//	private static final double chWt = 0.5;
//	private static String title = "Carson Range-Kings Canyon GR 50% CH 50%";

	private static ArrayList<PlotCurveCharacterstics> plotChars;
	private static ArrayList<PlotCurveCharacterstics> plotCharsB;
	private static List<String> titles;
	private static List<Double> grWts;
	private static List<Double> chWts;
	private static String title = "Carson Range-Kings Canyon";
	
	static {
		// init plot characteristics
		plotChars = Lists.newArrayList(
			getLineChar(new Color(0, 174, 239)),
			getLineChar(new Color(0, 84, 166)),
			getLineChar(new Color(0, 174, 239)),
			getLineChar(new Color(247, 148, 29)),
			getLineChar(new Color(242, 101, 34)),
			getLineChar(new Color(247, 148, 29)),
			getLineChar(new Color(230, 20, 100)));
		
		plotCharsB = Lists.newArrayList(
			getLineChar(new Color(0, 84, 166)),
			getLineChar(new Color(230, 20, 100)),
			getLineChar(new Color(242, 101, 34)));

		titles = Lists.newArrayList(
			title + " GR 33% CH 67%",
			title + " GR 50% CH 50%",
			title + " GR 67% CH 33%");
	
		grWts = Lists.newArrayList(0.333, 0.5, 0.667);
		chWts = Lists.newArrayList(0.667, 0.5, 0.333);
	}
	
	private static PlotCurveCharacterstics getLineChar(Color c) {
		return new PlotCurveCharacterstics(PlotLineType.SOLID,2f, c);
	}
	
	public static void main(String[] args) {
		List<SourceFile> fltSrcs = Lists.newArrayList();
		fltSrcs.addAll(SourceMgr.get(WUS, FAULT, "nv.3dip.gr.in"));
		fltSrcs.addAll(SourceMgr.get(WUS, FAULT, "nv.3dip.ch.in")); 

//		String name = "1136abcd Pleasant Valley fault zone";
//		double yMin = 3e-8;
//		double yMax = 4e-5;

		String name = "1285_1654 Carson Range-Kings Canyon faults";
		double yMin = 1e-6;
		double yMax = 1e-3;
		
		List<IncrementalMagFreqDist> sums = Lists.newArrayList();
		
		for (int i = 0; i < titles.size(); i++) {
			if (i == 0) {
				List<IncrementalMagFreqDist> mfds = getMFDs(fltSrcs, name,
					chWts.get(i), grWts.get(i));
				IncrementalMagFreqDist sum = sum(mfds);
				sums.add(sum);
				mfds.add(sum);
				plot(mfds, plotChars, titles.get(i), yMin, yMax);

//				for (IncrementalMagFreqDist mfd : mfds)
//					System.out.println(mfd);
			}
		}
		plot(sums, plotCharsB, title, yMin, yMax);
	}

	private static List<IncrementalMagFreqDist> getMFDs(
			List<SourceFile> srcFiles, String name, double chWt, double grWt) {

		Logger log = NSHMP_Utils.logger();
		Level level = Level.SEVERE;
		log.setLevel(level);
		for (Handler h : NSHMP_Utils.logger().getHandlers()) {
			h.setLevel(level);
		}
		List<IncrementalMagFreqDist> mfds = Lists.newArrayList();
		for (SourceFile sf : srcFiles) {

			FaultParser parser = new FaultParser(log);
			FaultERF erf = parser.parse(sf);

			List<FaultSource> srcs = Lists.newArrayList();
			for (FaultSource src : erf.getSources()) {
				if (src.getName().equals(name)) {
					srcs.add(src);
					System.out.println(src);
					System.out.println(src.trace);
				}
			}

			mfds.addAll(merge3dipMFDs(srcs, chWt, grWt));
		}
		return mfds;
	}

	/*
	 * Assumes that multiple fault sources with identically discretized MFDs are
	 * being passed in.
	 */
	private static List<IncrementalMagFreqDist> merge3dipMFDs(
			List<FaultSource> srcs, double chWt, double grWt) {
		List<IncrementalMagFreqDist> mfds = Lists.newArrayList();
		boolean first = true;
		int idx = 0;
		for (FaultSource src : srcs) {
			double scale = src.type.equals(FaultType.CH) ? chWt : grWt;
			for (int i = 0; i < src.mfds.size(); i++) {
				IncrementalMagFreqDist mfd = toIncr(src.mfds.get(i));
				mfd.scale(scale);
				if (first) {
					String info = buildInfo(src, idx++);
					mfd.setInfo(info);
					mfds.add(mfd);
				} else {
					Utils.addFunc(mfds.get(i), mfd);
				}
			}
			first = false;
		}
		return mfds;
	}
	
	private static String buildInfo(FaultSource src, int idx) {
		StringBuffer sb = new StringBuffer(src.name);
		FaultType type = src.type;
		sb.append(" ").append(src.type.name());
		String unc = "epi";
		if (idx == 0) unc += "-";
		if (idx == 1) unc = "";
		if (idx == 2) unc += "+";
		sb.append(" ").append(unc);
		return sb.toString();
	}

	private static void plot(List<? extends DiscretizedFunc> mfds,
			ArrayList<PlotCurveCharacterstics> plotChars, String title,
			double yMin, double yMax) {
		ArrayList funcs = Lists.newArrayList();
		funcs.addAll(mfds);
		GraphWindow graph = new GraphWindow(funcs,
				title, plotChars);
			graph.setX_AxisLabel("Magnitude");
			graph.setY_AxisLabel("Incremental Rate");
			graph.setYLog(true);
			graph.setX_AxisRange(M_MIN, M_MAX);
			graph.setY_AxisRange(yMin, yMax);
	}

	
	private static IncrementalMagFreqDist sum(List<IncrementalMagFreqDist> mfds) {		
		// convert all mfds to match domain of 'out' and sum
		SummedMagFreqDist sum = new SummedMagFreqDist(M_MIN, M_NUM, M_DELTA);
		for (IncrementalMagFreqDist src : mfds) {
			IncrementalMagFreqDist dest = createTarget();
			resample(src, dest);
			sum.addIncrementalMagFreqDist(dest);
		}
		return sum;
	}
	
	private static IncrementalMagFreqDist createTarget() {
		return new IncrementalMagFreqDist(M_MIN, M_NUM, M_DELTA);
	}
	
	/*
	 * fills out dest with y-values resampled from src
	 * @param src
	 * @param dest
	 */
	private static void resample(IncrementalMagFreqDist src,
			IncrementalMagFreqDist dest) {

		// for all points in dest, skip points above and below domain of src
		// and interpolate others
		
		// src mfd points sued as basis for interpolation
		double[] xSrc = new double[src.size()];
		double[] ySrc = new double[src.size()];
		int idx = 0;
		for (Point2D p : src) {
			xSrc[idx] = p.getX();
			ySrc[idx++] = p.getY();
		}
		
		// iterate dest
		double min = src.getMinX();
		double max = src.getMaxX();
		idx = 0;
		for (Point2D p : dest) {
			double x = p.getX();
			// we've added near-zero values above and below each
			// mfd so we don't worry about extrapolation
			if (x < min || x > max) continue;
			double y = Interpolate.findLogLogY(xSrc, ySrc, x);
			dest.set(x, y);
		}
	}
		
	/*
	 * Rewrites an IncrMFD. Needed to convert signature of GaussMFDs which can
	 * not be added
	 */
	private static IncrementalMagFreqDist toIncr(IncrementalMagFreqDist in) {
		double delta = in.getDelta();
		double min = in.getMinX() - delta;
		double max = in.getMaxX() + delta;
		int num = in.size() + 2;
		IncrementalMagFreqDist out = new IncrementalMagFreqDist(min, max, num);
		out.set(0, 1e-30);
		out.set(num-1, 1e-30);
		for (Point2D p : in) {
			out.set(p);
		}
		return out;
	}

	/*
	 * Returns the midpoint between two points.
	 */
	private static Point2D interpolate(Point2D p1, Point2D p2) {
		double x = (p1.getX() + p2.getX()) / 2;
		double y = (p1.getY() + p2.getY()) / 2;
		return new Point2D.Double(x, y);
	}
}
