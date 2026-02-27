package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import static org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeSimplifiedMapCurveWriter.FRACTILES;
import static org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeSimplifiedMapCurveWriter.PERCENTILE_HEADERS;;

public class LogicTreeSimplifiedMapDataWriter {

	public static void main(String[] args) {
		try {
			run(args);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void run(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("USAGE: LogicTreeSimplifiedMapDataWriter <results.zip> <output.zip>");
			System.exit(1);
		}
		File inputFile = new File(args[0]);
		Preconditions.checkState(inputFile.exists());
		File outputFile = new File(args[1]);
		
		ArchiveInput input = ArchiveInput.getDefaultInput(inputFile);
		ArchiveOutput output = ArchiveOutput.getDefaultOutput(outputFile, input);
		
		LogicTree<?> tree = LogicTree.read(new InputStreamReader(new BufferedInputStream(input.getInputStream("logic_tree.json"))));
		
		ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
		
		int treeSize = tree.size();
		
		List<Double> branchWeights = new ArrayList<>(treeSize);
		double sumWeights = 0d;
		for (int i=0; i<treeSize; i++) {
			double weight = tree.getBranchWeight(i);
			branchWeights.add(weight);
			sumWeights += weight;
		}
		if (!Precision.equals(sumWeights, 1d, 1e-5)) {
			double scalar = 1d/sumWeights;
			for (int i=0; i<treeSize; i++)
				branchWeights.set(i, branchWeights.get(i)*scalar);
		}
		
		Feature gridRegFeature = Feature.read(new InputStreamReader(new BufferedInputStream(input.getInputStream("gridded_region.geojson"))));
		GriddedRegion gridReg = GriddedRegion.fromFeature(gridRegFeature);
		
		System.out.println("Writing logic tree JSON and CSVs");
		
		CSVFile<String> logicTreeCSV = LogicTreeSimplifiedMapCurveWriter.buildLogicTreeCSV(tree, branchWeights);
		
		output.putNextEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
		Writer writer = new OutputStreamWriter(output.getOutputStream());
		Feature.write(gridReg.toFeature(), writer);
		writer.flush();
		output.closeEntry();
		
		tree.writeToArchive(output, "");
		
		output.putNextEntry("logic_tree.csv");
		logicTreeCSV.writeToStream(output.getOutputStream());
		output.closeEntry();
		
		logicTreeCSV = null;
		
		double[] periods = LogicTreeHazardCompare.detectHazardPeriods(new ReturnPeriods[] {ReturnPeriods.TWO_IN_50}, input);
		input.close(); // loader below uses file directly
		
		LogicTreeHazardCompare loader = new LogicTreeHazardCompare(null, tree, inputFile, rps, periods, gridReg.getSpacing());
		
		Stopwatch overallWatch = Stopwatch.createStarted();
		
		ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
		
		for (int p=0; p<periods.length; p++) {
			String perLabel, perUnits;
			if (periods[p] == -1d) {
				perLabel = "PGV";
				perUnits = "cm/s";
			} else if (periods[p] == 0d) {
				perLabel = "PGA";
				perUnits = "g";
			} else {
				perLabel = (float)periods[p]+"s SA";
				perUnits = "g";
			}
			
			for (ReturnPeriods rp : rps) {
				System.out.println("Doing "+perLabel+", "+rp);
				String csvName = MPJ_SiteLogicTreeHazardCurveCalc.getSitePeriodPrefix("map", periods[p])+"_"+rp.name()+".csv";
				
				GriddedGeoDataSet[] maps = loader.loadMaps(rp, periods[p]);
				
				Preconditions.checkState(maps.length == treeSize);
				
				System.out.println("\tLoading mean");
				GriddedGeoDataSet meanMap = loader.loadPrecomputedMeanMap(LogicTreeCurveAverager.MEAN_PREFIX, rp, periods[p]);
				if (meanMap == null) {
					System.out.println("\tHave to calculate mean");
					meanMap = loader.buildMean(maps);
				}
				
				System.out.println("\tCalculating Min/Max");
				
				GriddedGeoDataSet min = loader.buildMin(maps, branchWeights);
				GriddedGeoDataSet max = loader.buildMax(maps, branchWeights);
				
				System.out.println("\tCalculating SD/COV");
				GriddedGeoDataSet sd = new GriddedGeoDataSet(gridReg);
				GriddedGeoDataSet cov = new GriddedGeoDataSet(gridReg);
				LogicTreeHazardCompare.calcSD_COV(maps, branchWeights, meanMap, sd, cov, exec);
				
				System.out.println("\tCalculating NCDFs");
				LightFixedXFunc[] ncdfs = loader.buildNormCDFs(maps, branchWeights);
				
				System.out.println("\tCalculating median");
				GriddedGeoDataSet medianMap = loader.calcMapAtPercentile(ncdfs, gridReg, 50d);
				
				System.out.println("\tCalculating "+FRACTILES.length+" fractiles");
				GriddedGeoDataSet[] percentileMaps = new GriddedGeoDataSet[FRACTILES.length];
				for (int f=0; f<percentileMaps.length; f++)
					percentileMaps[f] = loader.calcMapAtPercentile(ncdfs, gridReg, FRACTILES[f]*100d);
				
				System.out.println("\tWriting CSV");
				CSVFile<String> csv = new CSVFile<>(true);
				
				List<String> header = new ArrayList<>(9+PERCENTILE_HEADERS.length);
				header.add("Grid Index");
				header.add("Latitude");
				header.add("Longitude");
				header.add("Mean");
				header.add("Median");
				header.add("Standard Deviation");
				header.add("COV");
				header.add("Min");
				for (String pHeader : PERCENTILE_HEADERS)
					header.add(pHeader);
				header.add("Max");
				csv.addLine(header);
				for (int i=0; i<meanMap.size(); i++) {
					List<String> line = new ArrayList<>(header.size());
					line.add(i+"");
					Location loc = gridReg.getLocation(i);
					line.add((float)loc.lat+"");
					line.add((float)loc.lon+"");
					line.add(meanMap.get(i)+"");
					line.add(medianMap.get(i)+"");
					line.add(sd.get(i)+"");
					line.add(cov.get(i)+"");
					line.add(min.get(i)+"");
					for (int f=0; f<percentileMaps.length; f++)
						line.add(percentileMaps[f].get(i)+"");
					line.add(max.get(i)+"");
					csv.addLine(line);
				}
				
				output.putNextEntry(csvName);
				csv.writeToStream(output.getOutputStream());
				output.closeEntry();
			}
		}
		
		exec.shutdown();
		
		output.close();
		
		System.out.println("DONE");
		overallWatch.stop();
		System.out.println("Took "+timeStr(overallWatch));
		System.exit(0);
	}
	
	private static final DecimalFormat timeDF = new DecimalFormat("0.0");
	
	private static String timeStr(Stopwatch watch) {
		 double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		 if (secs > 60d) {
			 double mins = secs/60d;
			 if (mins > 60d) {
				 return timeDF.format(mins/60d)+"h";
			 } else {
				 return timeDF.format(mins)+"m";
			 }
		 } else {
			 return timeDF.format(secs)+"s";
		 }
	}

}
