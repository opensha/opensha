package org.opensha.commons.gui.plot.jfreechart.tornado;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

public class TornadoDemo {

	public static void main(String[] args) throws IOException {
		TornadoDiagram t = new TornadoDiagram("Test Tornado", "X", "Category", 1d);
		
		t.addTornadoValue("Category 1", "C1", 0.5+Math.random());
		t.addTornadoValue("Category 1", "C2", 0.5+Math.random());
		t.addTornadoValue("Category 1", "C3", 0.5+Math.random());
		
		t.addTornadoValue("Category 2", "C1", 1d + 0.8*(Math.random()-0.5));
		t.addTornadoValue("Category 2", "C2", 1d + 0.8*(Math.random()-0.5));
		t.addTornadoValue("Category 2", "C3", 1d + 0.8*(Math.random()-0.5));
		
		t.addTornadoValue("Category 3", "C1", 1d + 0.3*(Math.random()-0.5));
		t.addTornadoValue("Category 3", "C2", 1d + 0.3*(Math.random()-0.5));
		t.addTornadoValue("Category 3", "C3", 1d + 0.3*(Math.random()-0.5));
		
		GraphWindow gw = t.displayPlot();
		gw.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		t.getHeadlessPlot(500, 400).saveAsPNG(new File("/tmp/tornado.png").getAbsolutePath());
		
		
		File csvDir = new File("/home/kevin/OpenSHA/UCERF3/TimeDependent_AVE_ALL/m6.7_30yr/BranchSensitivityMaps");
		double[] meanVals = null;
		double[] calcVals = null;
		int numFiles = 0;
		for (File file : csvDir.listFiles()) {
			if (!file.getName().endsWith(".csv"))
				continue;
			CSVFile<String> csv = CSVFile.readFile(file, true);
			if (meanVals == null) {
				meanVals = new double[csv.getNumRows()-1];
				calcVals = new double[csv.getNumRows()-1];
			}
			for (int i=0; i<meanVals.length; i++) {
				List<String> line = csv.getLine(i+1);
				double v = Double.parseDouble(line.get(1));
				double m = Double.parseDouble(line.get(2));
				meanVals[i] = m;
				calcVals[i] += v;
			}
			numFiles++;
		}
		for (int i=0; i<meanVals.length; i++)
			calcVals[i] /= (double)numFiles;
		
		MinMaxAveTracker track = new MinMaxAveTracker();
		MinMaxAveTracker pTrack = new MinMaxAveTracker();
		
		for (int i=0; i<meanVals.length; i++) {
			double calc = calcVals[i];
			double table = meanVals[i];
			double pDiff = DataUtils.getPercentDiff(calc, table);
			track.addValue(calc - table);
			pTrack.addValue(pDiff);
		}
		System.out.println("Actual diff: "+track);
		System.out.println("P diff: "+pTrack);
	}

}
