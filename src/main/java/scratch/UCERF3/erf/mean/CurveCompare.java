package scratch.UCERF3.erf.mean;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.nshmp2.util.Period;

import scratch.UCERF3.utils.ProbOfExceed;
import scratch.peter.curves.CurveUtilsUC33;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class CurveCompare {
	
	private static Table<String, Period, DiscretizedFunc> loadUC33Curves(File dir) throws IOException {
		Table<String, Period, DiscretizedFunc> table = HashBasedTable.create();
		
		for (File periodDir : dir.listFiles()) {
			if (!periodDir.isDirectory())
				continue;
			String periodStr = periodDir.getName();
			if (!periodStr.startsWith("GM"))
				continue;
			
			Period period = Period.valueOf(periodStr);
			
			for (File siteDir : periodDir.listFiles()) {
				if (!siteDir.isDirectory())
					continue;
				String siteName = siteDir.getName().trim();
				if (siteName.startsWith("."))
					continue;
				CSVFile<String> csv = CSVFile.readFile(new File(siteDir, "stats.csv"), true);
				List<Double> xVals = Lists.newArrayList();
				List<String> xValLine = csv.getLine(0); // header
				for (String str : xValLine.subList(4, xValLine.size()))
					if (!str.trim().isEmpty())
						xVals.add(Double.parseDouble(str));
				List<Double> yVals = Lists.newArrayList();
				List<String> yValLine = csv.getLine(1); // mean line
				for (String str : yValLine.subList(4, yValLine.size()))
					if (!str.trim().isEmpty())
						yVals.add(Double.parseDouble(str));
				Preconditions.checkState(xVals.size() > 0);
				Preconditions.checkState(xVals.size() == yVals.size());
				
				ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
				for (int i=0; i<xVals.size(); i++)
					func.set(xVals.get(i), yVals.get(i));
				
				table.put(siteName, period, func);
			}
		}
		return table;
	}
	
	private static Table<String, Period, DiscretizedFunc> loadMeanCurves(File dir) throws IOException {
		Table<String, Period, DiscretizedFunc> table = HashBasedTable.create();
		
		for (File periodDir : dir.listFiles()) {
			if (!periodDir.isDirectory())
				continue;
			String periodStr = periodDir.getName();
			if (!periodStr.startsWith("GM"))
				continue;
			
			Period period = Period.valueOf(periodStr);
			
			File curvesFile = new File(periodDir, "curves.csv");
			if (!curvesFile.exists())
				continue;
			CSVFile<String> csv = CSVFile.readFile(curvesFile, true);
			
			List<Double> xVals = Lists.newArrayList();
			List<String> xValLine = csv.getLine(0); // header
			for (String str : xValLine.subList(1, xValLine.size()))
				if (!str.trim().isEmpty())
					xVals.add(Double.parseDouble(str));
			Preconditions.checkState(xVals.size() > 0);
			
			for (int row=1; row<csv.getNumRows(); row++) {
				List<Double> yVals = Lists.newArrayList();
				List<String> yValLine = csv.getLine(row); // mean line
				String siteName = yValLine.get(0).trim();
				for (String str : yValLine.subList(1, yValLine.size()))
					if (!str.trim().isEmpty())
						yVals.add(Double.parseDouble(str));
				Preconditions.checkState(xVals.size() > 0);
				Preconditions.checkState(xVals.size() == yVals.size());
				
				ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
				for (int i=0; i<xVals.size(); i++)
					func.set(xVals.get(i), yVals.get(i));
				
				table.put(siteName, period, func);
			}
		}
		
		return table;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		File refFaultCurvesDir = new File("/home/kevin/OpenSHA/UCERF3/UC33-flt");
		System.out.println("Loading UCERF3.3 Fault Curves");
		Table<String, Period, DiscretizedFunc> refFltCurves = loadUC33Curves(refFaultCurvesDir);
		System.out.println("Loaded "+refFltCurves.rowKeySet().size()+" sites, "+refFltCurves.columnKeySet().size()
				+" periods, "+refFltCurves.size()+" curves");
		
		File refBGCurvesDir = new File("/home/kevin/OpenSHA/UCERF3/UC33-bg");
		System.out.println("Loading UCERF3.3 BG Curves");
		Table<String, Period, DiscretizedFunc> refBGCurves = loadUC33Curves(refBGCurvesDir);
		System.out.println("Loaded "+refBGCurves.rowKeySet().size()+" sites, "+refBGCurves.columnKeySet().size()
				+" periods, "+refBGCurves.size()+" curves");
		
		File meanCurvesDir = new File("/home/kevin/OpenSHA/UCERF3/MeanUCERF3-curves");
//		File meanCurvesDir = new File("/tmp");
		
		CSVFile<String> resultsCSV = new CSVFile<String>(true);
		resultsCSV.addLine("Upper Depth Tol (km)", "Combine Mags?", "Combine Rakes?", "Rake Basis",
				"# FSS Rups", "# FSS Sects", "# ERF Sources", "# ERF Ruptures",
				"Mean All Diff", "Min All Diff", "Max All Diff",
				"Mean 2in50 Diff", "Min 2in50 Diff", "Max 2in50 Diff",
				"Mean 10in50 Diff", "Min 10in50 Diff", "Max 10in50 Diff",
				"Best Site", "Worst Site", "% Values Above", "% Values Below");
		
		File[] subDirs = meanCurvesDir.listFiles();
		Arrays.sort(subDirs, new FileNameComparator());
		
		for (File dir : subDirs) {
			if (!dir.isDirectory())
				continue;
			String dirName = dir.getName();
			if (!dirName.startsWith("dep"))
				continue;
			
			List<String> split = Lists.newArrayList(underscore_split.split(dirName));
			double upperDepthTol = Double.parseDouble(split.get(0).replaceAll("dep", ""));
			double magTol;
			if (split.get(1).startsWith("full"))
				magTol = 0;
			else
				magTol = Double.parseDouble(split.get(1).replaceAll("mags", ""));
			boolean combineRakes = split.get(2).startsWith("comb");
			String rakeBasis;
			if (combineRakes)
				rakeBasis = split.get(3).replaceAll("Rakes", "");
			else
				rakeBasis = "N/A";
			
			System.out.println("Loading "+dirName);
			Table<String, Period, DiscretizedFunc> meanCurves = loadMeanCurves(dir);
			System.out.println("Loaded "+meanCurves.rowKeySet().size()+" sites, "+meanCurves.columnKeySet().size()
					+" periods, "+meanCurves.size()+" curves");
			
			if (meanCurves.isEmpty())
				continue;
			
			List<String> line = Lists.newArrayList((float)upperDepthTol+"", magTol+"", combineRakes+"", rakeBasis);
			
			line.addAll(loadCounts(dir));
			
			if (rakeBasis.equals("GEOL"))
				line.addAll(calcDiffs(meanCurves, refFltCurves, false));
			else
				line.addAll(calcDiffs(meanCurves, refFltCurves, false));
			
			resultsCSV.addLine(line);
		}
		
		File griddedDir = new File(meanCurvesDir, "gridded");
		if (griddedDir.exists()) {
			Table<String, Period, DiscretizedFunc> meanCurves = loadMeanCurves(griddedDir);
			if (meanCurves.size() > 0) {
				List<String> line = Lists.newArrayList("(gridded)", "(gridded)", "(gridded)", "(gridded)");
				
				line.addAll(loadCounts(griddedDir));
				
				line.addAll(calcDiffs(meanCurves, refBGCurves, false));
				
				resultsCSV.addLine(line);
			}
		}
		
		File truemeanDir = new File(meanCurvesDir, "truemean");
		if (truemeanDir.exists()) {
			Table<String, Period, DiscretizedFunc> meanCurves = loadMeanCurves(truemeanDir);
			if (meanCurves.size() > 0) {
				List<String> line = Lists.newArrayList("(truemean)", "(truemean)", "(truemean)", "(truemean)");
				
				line.addAll(loadCounts(truemeanDir));
				
				line.addAll(calcDiffs(meanCurves, refFltCurves, false));
				
				resultsCSV.addLine(line);
			}
		}
		
		resultsCSV.writeToFile(new File(meanCurvesDir, "diffs.csv"));
	}
	
	private static List<String> loadCounts(File dir) throws IOException {
		File csvFile = new File(dir.getParentFile(), dir.getName()+"_counts.csv");
		if (csvFile.exists()) {
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			return csv.getLine(1);
		} else {
			return Lists.newArrayList("", "", "" , "");
		}
	}
	
	private static List<String> calcDiffs(Table<String, Period, DiscretizedFunc> meanCurves,
			Table<String, Period, DiscretizedFunc> refCurves, boolean printCurves) {
		List<String> line = Lists.newArrayList();
		
		MinMaxAveTracker allTrack = new MinMaxAveTracker();
		MinMaxAveTracker twoIn50Track = new MinMaxAveTracker();
		MinMaxAveTracker tenIn50Track = new MinMaxAveTracker();
		
		String bestMatchSite = null;
		double bestMaxDiff = Double.POSITIVE_INFINITY;
		String worstMatchSite = null;
		double worstMaxDiff = 0d;
		
		Bias bias = new Bias();
		
		for (Cell<String, Period, DiscretizedFunc> cell : meanCurves.cellSet()) {
			String siteName = cell.getRowKey();
			Period period = cell.getColumnKey();
			DiscretizedFunc curve = cell.getValue();
			DiscretizedFunc refCurve = refCurves.get(siteName, period);
			Preconditions.checkNotNull(refCurve, "Ref curve not found for "+siteName+", "+period);
			Preconditions.checkState(curve.size() == refCurve.size(), "Curve sizes inconsistant!");
			
			double twoIn50 = CurveUtilsUC33.getPE(curve, ProbOfExceed.PE2IN50);
			double tenIn50 = CurveUtilsUC33.getPE(curve, ProbOfExceed.PE10IN50);
			double refTwoIn50 = CurveUtilsUC33.getPE(refCurve, ProbOfExceed.PE2IN50);
			double refTenIn50 = CurveUtilsUC33.getPE(refCurve, ProbOfExceed.PE10IN50);
			
			twoIn50Track.addValue(DataUtils.getPercentDiff(twoIn50, refTwoIn50));
			tenIn50Track.addValue(DataUtils.getPercentDiff(tenIn50, refTenIn50));
			
			MinMaxAveTracker myTrack = new MinMaxAveTracker();
			
			for (int i=0; i<curve.size(); i++) {
				Point2D pt = curve.get(i);
				Point2D refPt = refCurve.get(i);
				
				Preconditions.checkState((float)pt.getX() == (float)refPt.getX(),
						"ref curve has different x values!");
				
				if (pt.getY() < 1e-6 && refPt.getY() < 1e-6)
					// skip incredibly small ones
					continue;
				double diff = DataUtils.getPercentDiff(pt.getY(), refPt.getY());
				allTrack.addValue(diff);
				myTrack.addValue(diff);
				if (printCurves && diff>=100) {
					System.out.println("Big diff at\tx="+pt.getX()+"\ty="+pt.getY()+"\trefY="+refPt.getY());
				}
				
				bias.add(pt.getY(), refPt.getY());
			}
			double myMax = myTrack.getMax();
			if (myMax < bestMaxDiff) {
				bestMaxDiff = myMax;
				bestMatchSite = siteName;
			}
			if (myMax > worstMaxDiff) {
				worstMaxDiff = myMax;
				worstMatchSite = siteName;
			}
			
			if (printCurves) {
				System.out.println("Comparing for "+siteName);
				System.out.println("MEAN:\t"+getYs(curve));
				System.out.println("REF:\t"+getYs(refCurve));
			}
		}
		
		line.add((float)allTrack.getAverage()+" %");
		line.add((float)allTrack.getMin()+" %");
		line.add((float)allTrack.getMax()+" %");
		line.add((float)twoIn50Track.getAverage()+" %");
		line.add((float)twoIn50Track.getMin()+" %");
		line.add((float)twoIn50Track.getMax()+" %");
		line.add((float)tenIn50Track.getAverage()+" %");
		line.add((float)tenIn50Track.getMin()+" %");
		line.add((float)tenIn50Track.getMax()+" %");
		line.add(bestMatchSite);
		line.add(worstMatchSite);
		line.add((float)bias.getPercentAbove()+" %");
		line.add((float)bias.getPercentBelow()+" %");
		
		return line;
	}
	
	private static class Bias {
		
		private int numAbove=0;
		private int numBelow=0;
		private int total=0;
		
		public void add(double val, double refVal) {
			if (val > refVal)
				numAbove++;
			else if (val < refVal)
				numBelow++;
			total++;
		}
		
		public double getPercentAbove() {
			return 100d*(double)numAbove/(double)total;
		}

		
		public double getPercentBelow() {
			return 100d*(double)numBelow/(double)total;
		}
	}
	
	private static final Splitter underscore_split = Splitter.on('_');
	private static final Joiner comma_join = Joiner.on(',');
	
	private static String getYs(DiscretizedFunc func) {
		List<Double> ys = Lists.newArrayList();
		for (Point2D pt : func)
			ys.add(pt.getY());
		return comma_join.join(ys);
	}

}
