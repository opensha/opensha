package scratch.UCERF3.erf.mean;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.nshmp2.calc.ERF_ID;
import org.opensha.nshmp2.calc.HazardResultWriterSites;
import org.opensha.nshmp2.calc.ThreadedHazardCalc;
import org.opensha.nshmp2.util.Period;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.peter.ucerf3.calc.UC3_CalcUtils;

import com.google.common.base.Enums;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class CurveCalcTest {
	
	static String getSubDirName(double upperDepthTol, double magTol, boolean combineRakes, DeformationModels rakeBasisDM) {
		String subDirName = "dep"+(float)upperDepthTol;
		if (magTol > 0)
			subDirName += "_mags"+(float)magTol;
		else
			subDirName += "_fullMags";
		if (combineRakes)
			subDirName += "_combRakes";
		else
			subDirName += "_fullRakes";
		
		if (combineRakes) {
			if (rakeBasisDM != null)
				subDirName += "_"+rakeBasisDM.getShortName()+"Rakes";
			else
				subDirName += "_meanRakes";
		}
		
		return subDirName;
	}

	/**
	 * @param args
	 * @throws DocumentException 
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) {
		try {
			doCalc(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}
	
	static void doCalc(String[] args) throws IOException, DocumentException, InterruptedException, ExecutionException {
		File meanSolFile = new File(args[0]);
		File outputDir = new File(args[1]);
		
		System.out.println("Loading solution...");
		FaultSystemSolution sol = FaultSystemIO.loadSol(meanSolFile);
		FaultSystemRupSet rupSet = sol.getRupSet();
		System.out.println("done.");
		
		boolean gridded = args[2].trim().equals("gridded");
		boolean truemean = args[2].trim().equals("truemean");
		
		String subdirName;
		FaultSystemSolution calcSol;
		
		if (gridded) {
			calcSol = sol;
			subdirName = "gridded";
		} else if (truemean) {
			calcSol = sol;
			subdirName = "truemean";
		} else {
			double upperDepthTol = Double.parseDouble(args[2]);
			double magTol = Double.parseDouble(args[3]);
			boolean combineRakes = Boolean.parseBoolean(args[4]);
			DeformationModels rakeBasisDM = null;
			
			Map<Set<String>, Double> rakeBasis = null;
			if (combineRakes) {
				String rakeBasisStr = args[5];
				if (!rakeBasisStr.toLowerCase().trim().equals("null")) {
					rakeBasisDM = DeformationModels.valueOf(rakeBasisStr);
					rakeBasis = RuptureCombiner.loadRakeBasis(rakeBasisDM);
				}
			}
			
			subdirName = getSubDirName(upperDepthTol, magTol, combineRakes, rakeBasisDM);
			
			System.out.println("Combining rups for tol="+upperDepthTol+", combineRakes="+combineRakes);
			Stopwatch watch = Stopwatch.createStarted();
			FaultSystemSolution reducedSol = RuptureCombiner.getCombinedSolution(sol, upperDepthTol, false, combineRakes, rakeBasis);
			FaultSystemRupSet reducedRupSet = reducedSol.getRupSet();
			watch.stop();
			System.out.println("DONE. Took "+watch.elapsed(TimeUnit.SECONDS)+"s to reduce to "
					+reducedRupSet.getNumRuptures()+"/"+rupSet.getNumRuptures()+" rups and "
					+reducedRupSet.getNumSections()+"/"+rupSet.getNumSections()+" sects");
			
//			if (combineMags)
//				reducedSol.setRupMagDists(null);
			if (magTol > 0)
				reducedSol.setRupMagDists(RuptureCombiner.combineMFDs(magTol, reducedSol.getRupMagDists()));
			
			calcSol = reducedSol;
//			RuptureCombiner.checkIdentical(sol, reducedSol, true);
//			File testFile = new File("/tmp/reduced.zip");
//			FaultSystemIO.writeSol(reducedSol, testFile);
//			calcSol = null;
//			reducedSol = null;
//			sol = null;
//			System.gc();
//			calcSol = FaultSystemIO.loadSol(testFile);
//			reducedSol = calcSol;
//			RuptureCombiner.checkIdentical(sol, reducedSol, true);
//			testFile.delete();
////			calcSol = sol;
//			System.exit(0);
		}
		
		outputDir = new File(outputDir, subdirName);
		outputDir.mkdir();
		
		System.out.println("Instantiating ERF...");
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(calcSol);
		erf.getTimeSpan().setDuration(1d);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, true);
		if (gridded) {
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.ONLY);
			Preconditions.checkNotNull(calcSol.getGridSourceProvider());
		} else {
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		}
		System.out.println("done.");

		System.out.println("Updating forecast...");
		erf.updateForecast();
		System.out.println("done.");

		System.out.println("sources: "+erf.getNumSources());
		int totRups = 0;
		for (int i=0; i<erf.getNumSources(); i++)
			totRups += erf.getNumRuptures(i);
		System.out.println("ruptures: "+totRups);
		
		// write counts
		CSVFile<String> metadataCSV = new CSVFile<String>(false);
		metadataCSV.addLine("# FSS Rups", "# FSS Sects", "# Sources", "# Rups");
		metadataCSV.addLine(calcSol.getRupSet().getNumRuptures()+"", calcSol.getRupSet().getNumSections()+"",
				erf.getNumSources()+"", totRups+"");
		metadataCSV.writeToFile(new File(outputDir.getParentFile(), subdirName+"_counts.csv"));
		
		boolean epiUncert = false;
		
		String sitePath = "/home/scec-00/pmpowers/UC33/curvejobs/sites/all.txt";
		if (new File("/home/kevin/OpenSHA/UCERF3/MeanUCERF3-curves/all.txt").exists())
			sitePath = "/home/kevin/OpenSHA/UCERF3/MeanUCERF3-curves/all.txt";
		if (new File(meanSolFile.getParentFile(), "all.txt").exists())
			sitePath = new File(meanSolFile.getParentFile(), "all.txt").getAbsolutePath();
		String periodsStr = "GM0P00,GM0P20,GM1P00,GM4P00";
		List<Period> periods = readArgAsList(periodsStr, Period.class);
		
		EpistemicListERF wrappedERF = ERF_ID.wrapInList(erf);
		Map<String, Location> siteMap = UC3_CalcUtils.readSiteFile(sitePath);
		LocationList locs = new LocationList();
		for (Location loc : siteMap.values()) {
			locs.add(loc);
		}
		
		for (Period period : periods) {
			String outPath = outputDir.getAbsolutePath();
			System.out.println(outPath);
			HazardResultWriterSites writer = new HazardResultWriterSites(outPath,
				siteMap);
			writer.writeHeader(period);
			ThreadedHazardCalc thc = new ThreadedHazardCalc(wrappedERF, null, locs,
				period, epiUncert, writer, false);
			thc.calculate(null);
		}
	}
	
	private static final Splitter SPLIT = Splitter.on(',');

	private static <T extends Enum<T>> List<T> readArgAsList(String arg,
			Class<T> clazz) {
		Iterable<T> it = Iterables.transform(SPLIT.split(arg),
			Enums.stringConverter(clazz));
		return Lists.newArrayList(it);
	}

}
