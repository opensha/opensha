package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;

import com.google.common.io.Files;
import com.google.common.io.LittleEndianDataInputStream;

public class StiffnessVerify {

	public static void main(String[] args) throws IOException {
		File refDir = new File("/home/kevin/Simulators/catalogs/stiffness");
		File fltFile = new File(refDir, "ALLCAL2_Geometry.flt");
		File sigFile = new File(refDir, "Ksigma.stiffness.out");
		File tauFile = new File(refDir, "Ktau.stiffness.out");
		double lambda = 30000;
		double mu = 30000;
		
		List<Patch> patches = new ArrayList<>();
		
		for (String line : Files.readLines(fltFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty())
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			
			// this is the center
			double x = Double.parseDouble(tok.nextToken());
			double y = Double.parseDouble(tok.nextToken());
			double z = Double.parseDouble(tok.nextToken());
			double l = Double.parseDouble(tok.nextToken());
			double w = Double.parseDouble(tok.nextToken());
			
			double strike = Double.parseDouble(tok.nextToken());
			double dip = Double.parseDouble(tok.nextToken());
			double rake = Double.parseDouble(tok.nextToken());

			FocalMechanism focalMechanism = new FocalMechanism(strike, dip, rake);
			double[] center = { x, y, z };
			patches.add(new Patch(center, l, w, focalMechanism));
		}
		System.out.println("Loaded "+patches.size()+" patches");
		LittleEndianDataInputStream sigIn = new LittleEndianDataInputStream(
				new BufferedInputStream(new FileInputStream(sigFile)));
		LittleEndianDataInputStream tauIn = new LittleEndianDataInputStream(
				new BufferedInputStream(new FileInputStream(tauFile)));
		
		double closeDist = 10000d; // m
		DefaultXY_DataSet closeTauScatter = new DefaultXY_DataSet();
		DefaultXY_DataSet closeSigmaScatter = new DefaultXY_DataSet();
		
		DiffTrack sigDiffTrack = new DiffTrack("Sigma", new Range(1e-8, 1e1));
		DiffTrack tauDiffTrack = new DiffTrack("Tau", new Range(1e-6, 1e2));
		long count = 0;
		int numSingular = 0;
		
		EuclideanDistance distCalc = new EuclideanDistance();
		for (int i=0; i<patches.size(); i++) {
			Patch receiver = patches.get(i);
			for (int j=0; j<patches.size(); j++) {
				Patch source = patches.get(j);
				count++;
				double[] stiffness = StiffnessCalc.calcStiffness(lambda, mu, source, receiver);
				double rsSig = sigIn.readDouble();
				double rsTau = tauIn.readDouble();
				if (stiffness == null) {
					numSingular++;
				} else {
					sigDiffTrack.add(stiffness[0], rsSig);
					tauDiffTrack.add(stiffness[1], rsTau);
					
					double dist = distCalc.compute(receiver.center, source.center);
					if (dist <= closeDist) {
						closeSigmaScatter.set(Math.abs(rsSig), Math.abs(stiffness[0]));
						closeTauScatter.set(Math.abs(rsTau), Math.abs(stiffness[1]));
					}
				}

			}
			if (i % 100 == 0) {
				System.out.println("Done with patch "+i+" ("+count+" calculations, "
						+closeSigmaScatter.size()+" close)");
				sigDiffTrack.print();
				tauDiffTrack.print();
			}
//			if (i == 1000)
//				break;
		}

		System.out.println("Done with all ("+count+" calculations, "+numSingular+" singular)");
		sigDiffTrack.print();
		tauDiffTrack.print();
		
		sigIn.close();
		tauIn.close();
		
		System.out.println("Plotting sigma...");
		plotCloseScatter(refDir, "sigma_close_scatter", true, closeDist, closeSigmaScatter);
		System.out.println("Plotting tau...");
		plotCloseScatter(refDir, "tau_close_scatter", false, closeDist, closeTauScatter);
		System.out.println("DONE");
	}
	
	private static class DiffTrack {
		MinMaxAveTracker allDiffs = new MinMaxAveTracker();
		MinMaxAveTracker allPDiffs = new MinMaxAveTracker();
		Range normalRange;
		MinMaxAveTracker normalDiffs = new MinMaxAveTracker();
		MinMaxAveTracker normalPDiffs = new MinMaxAveTracker();
		int allSignErrors;
		int normalSignErrors;
		double maxSignError = 0d;
		private String name;
		
		public DiffTrack(String name, Range normalRange) {
			this.name = name;
			this.normalRange = normalRange;
		}
		
		public void add(double test, double target) {
			boolean normal = normalRange.contains(Math.abs(test)) || normalRange.contains(Math.abs(target));
			double diff = Math.abs(test - target);
			double pDiff = DataUtils.getPercentDiff(test, target);
			allDiffs.addValue(diff);
			allPDiffs.addValue(pDiff);
			if (normal) {
				normalDiffs.addValue(diff);
				normalPDiffs.addValue(pDiff);
			}
			if (signDiff(test, target)) {
				allSignErrors++;
				if (normal)
					normalSignErrors++;
				maxSignError = Math.max(maxSignError, Math.max(Math.abs(test), Math.abs(target)));
			}
		}
		
		public void print() {
			System.out.println("\t"+name+", all values:");
			System.out.println("\t\tdiffs:\t"+allDiffs);
			System.out.println("\t\tpDiffs:\t"+allPDiffs);
			System.out.println("\t\tsign errors:\t"+allSignErrors+"\tmax="+maxSignError);
			System.out.println("\t"+name+", normal values:");
			System.out.println("\t\tdiffs:\t"+normalDiffs);
			System.out.println("\t\tpDiffs:\t"+normalPDiffs);
			System.out.println("\t\tsign errors:\t"+normalSignErrors);
		}
	}
	
	private static boolean signDiff(double val1, double val2) {
		return (val1 > 0 && val2 < 0) || (val1 < 0 && val2 > 0);
	}
	
	private static void plotCloseScatter(File outputDir, String prefix, boolean sigma,
			double dist, DefaultXY_DataSet xy) throws IOException {
		Range range = new Range(1e-16, 1e3);

		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(xy);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		String value = sigma ? "ΔSigma (MPa)" : "ΔTau (MPa)";
		
		PlotSpec spec = new PlotSpec(funcs, chars, value+" Comparison, R<"+(int)dist+"m",
				"|RSQSim "+value+"|", "|OpenSHA "+value+"|");
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		File pngFile = new File(outputDir, prefix+".png");
		
		gp.drawGraphPanel(spec, true, true, range, range);
		gp.getChartPanel().setSize(800, 700);
		gp.saveAsPNG(pngFile.getAbsolutePath());
	}

}
