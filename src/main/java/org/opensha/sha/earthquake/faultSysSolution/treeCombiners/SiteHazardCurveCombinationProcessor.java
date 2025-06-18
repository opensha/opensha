package org.opensha.sha.earthquake.faultSysSolution.treeCombiners;

import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner;
import org.opensha.commons.logicTree.treeCombiner.LogicTreeCombinationProcessor;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner.LogicTreeCombinationContext;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.SiteLogicTreeHazardPageGen;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.treeCombiners.HazardMapCombinationProcessor.CurveCombineCallable;
import org.opensha.sha.earthquake.faultSysSolution.treeCombiners.HazardMapCombinationProcessor.CurveCombineResult;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class SiteHazardCurveCombinationProcessor implements LogicTreeCombinationProcessor {

	private final File outerHazardCurvesFile;
	private final File innerHazardCurvesFile;
	private final File hazardCurvesOutputFile;
	
	private CSVFile<String> sitesCSV = null;
	private List<Site> sites = null;
	private List<List<String>> siteOutNames = null;
	private List<List<List<DiscretizedFunc>>> outerSiteCurves = null;
	private List<List<List<DiscretizedFunc>>> innerSiteCurves = null;
	private List<List<FileWriter>> siteCurveWriters = null;
	private List<Double> sitePeriods = null;
	private List<double[]> sitePerXVals = null;
	private File curveOutDir = null;
	
	private ExecutorService exec;
	private Stopwatch combineWatch = Stopwatch.createUnstarted();
	private Stopwatch curveWriteWatch = Stopwatch.createUnstarted();
	private LogicTree<?> combTree;

	public SiteHazardCurveCombinationProcessor(File outerHazardCurvesFile, File innerHazardCurvesFile,
			File hazardCurvesOutputFile) {
		super();
		this.outerHazardCurvesFile = outerHazardCurvesFile;
		this.innerHazardCurvesFile = innerHazardCurvesFile;
		this.hazardCurvesOutputFile = hazardCurvesOutputFile;
	}

	@Override
	public String getName() {
		return "Site hazard curves";
	}

	@Override
	public void init(LogicTreeCombinationContext treeCombination,
			ExecutorService exec, ExecutorService ioExec) throws IOException {
		this.combTree = treeCombination.combTree;
		this.exec = exec;
		Preconditions.checkState(treeCombination.averageAcrossLevels.isEmpty(), "Averaging not yet supported");
		ZipFile innerZip = new ZipFile(innerHazardCurvesFile);
		CSVFile<String> innerSitesCSV = CSVFile.readStream(innerZip.getInputStream(
				innerZip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
		
		sites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(innerSitesCSV, null);
		System.out.println("Loaded "+sites.size()+" hazard curve sites");
		
		ZipFile outerZip = new ZipFile(outerHazardCurvesFile);
		sitesCSV = CSVFile.readStream(outerZip.getInputStream(
				outerZip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
		
		List<Site> outerSites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(sitesCSV, null);
		Preconditions.checkState(sites.size() == outerSites.size(),
				"Inner hazard has %s sites and outer has %s", sites.size(), outerSites.size());
		outerSiteCurves = new ArrayList<>(sites.size());
		innerSiteCurves = new ArrayList<>(sites.size());
		siteOutNames = new ArrayList<>(sites.size());
		for (int s=0; s<sites.size(); s++) {
			Site site = sites.get(s);
			Preconditions.checkState(LocationUtils.areSimilar(site.getLocation(), outerSites.get(s).getLocation()));
			String sitePrefix = FileNameUtils.simplify(site.getName());
			
			System.out.println("Pre-loading site hazard curves for site "+s+"/"+sites.size()+": "+site.getName());
			
			Map<Double, ZipEntry> outerPerEntries = SiteLogicTreeHazardPageGen.locateSiteCurveCSVs(sitePrefix, outerZip);
			Preconditions.checkState(!outerPerEntries.isEmpty());
			Map<Double, ZipEntry> innerPerEntries = SiteLogicTreeHazardPageGen.locateSiteCurveCSVs(sitePrefix, innerZip);
			Preconditions.checkState(!innerPerEntries.isEmpty());
			Preconditions.checkState(outerPerEntries.size() == innerPerEntries.size());
			
			if (sitePeriods == null) {
				sitePeriods = new ArrayList<>(outerPerEntries.keySet());
				Collections.sort(sitePeriods);
				sitePerXVals = new ArrayList<>(sitePeriods.size());
			} else {
				Preconditions.checkState(outerPerEntries.size() == sitePeriods.size());
			}
			
			List<List<DiscretizedFunc>> outerPerCurves = new ArrayList<>(sitePeriods.size());
			outerSiteCurves.add(outerPerCurves);
			List<List<DiscretizedFunc>> innerPerCurves = new ArrayList<>(sitePeriods.size());
			innerSiteCurves.add(innerPerCurves);
			List<String> outNames = new ArrayList<>(sitePeriods.size());
			siteOutNames.add(outNames);
			for (int p=0; p<sitePeriods.size(); p++) {
				double period = sitePeriods.get(p);
				Preconditions.checkState(innerPerEntries.containsKey(period));
				List<DiscretizedFunc> outerSitePerCurves = SiteLogicTreeHazardPageGen.loadCurves(
						CSVFile.readStream(outerZip.getInputStream(outerPerEntries.get(period)), true), treeCombination.outerTree);
				Preconditions.checkState(outerSitePerCurves.size() == treeCombination.outerTree.size());
				outerPerCurves.add(outerSitePerCurves);
				List<DiscretizedFunc> innerSitePerCurves = SiteLogicTreeHazardPageGen.loadCurves(
						CSVFile.readStream(innerZip.getInputStream(innerPerEntries.get(period)), true), treeCombination.innerTree);
				Preconditions.checkState(innerSitePerCurves.size() == treeCombination.innerTree.size());
				innerPerCurves.add(innerSitePerCurves);
				outNames.add(outerPerEntries.get(period).getName());
				Preconditions.checkState(sitePerXVals.size() >= p);
				
				DiscretizedFunc outerCurve0 = outerSitePerCurves.get(0);
				DiscretizedFunc innerCurve0 = innerSitePerCurves.get(0);
				Preconditions.checkState(outerCurve0.size() == innerCurve0.size(),
						"Curve gridding differs between outer and inner site curves");
				if (sitePerXVals.size() == p) {
					// first time
					double[] xVals = new double[outerCurve0.size()];
					for (int i=0; i<xVals.length; i++) {
						xVals[i] = outerCurve0.getX(i);
						Preconditions.checkState((float)xVals[i] == (float)innerCurve0.getX(i));
					}
					sitePerXVals.add(xVals);
				}
			}
		}
		
		innerZip.close();
		outerZip.close();
		
		String outName = hazardCurvesOutputFile.getName();
		if (outName.toLowerCase().endsWith(".zip"))
			curveOutDir = new File(hazardCurvesOutputFile.getParentFile(), outName.substring(0, outName.length()-4));
		else
			curveOutDir = hazardCurvesOutputFile;
		Preconditions.checkState(curveOutDir.exists() || curveOutDir.mkdir(),
				"Doesn't exist and couldn't be created: %s", curveOutDir.getAbsolutePath());
	}

	@Override
	public void processBranch(LogicTreeBranch<?> combBranch, int combBranchIndex, double combBranchWeight,
			LogicTreeBranch<?> outerBranch, int outerBranchIndex, LogicTreeBranch<?> innerBranch,
			int innerBranchIndex) throws IOException {
		combineWatch.start();
		
		List<List<Future<CurveCombineResult>>> combineFutures = new ArrayList<>(sites.size());
		for (int s=0; s<sites.size(); s++) {
			List<Future<CurveCombineResult>> periodFutures = new ArrayList<>(sitePeriods.size());
			for (int p=0; p<sitePeriods.size(); p++) {
				double[] xVals = sitePerXVals.get(p);
				DiscretizedFunc outerCurve = outerSiteCurves.get(s).get(p).get(outerBranchIndex);
				DiscretizedFunc innerCurve = innerSiteCurves.get(s).get(p).get(innerBranchIndex);
				periodFutures.add(exec.submit(new CurveCombineCallable(s, xVals, outerCurve, innerCurve, null)));
			}
			combineFutures.add(periodFutures);
		}
		
		if (siteCurveWriters == null) {
			// first time, initialize writers
			siteCurveWriters = new ArrayList<>(sites.size());
			List<String> perHeaders = new ArrayList<>(sitePeriods.size());
			for (int p=0; p<sitePeriods.size(); p++) {
				double[] xVals = sitePerXVals.get(p);
				List<String> header = new ArrayList<>();
				header.add("Site Name");
				header.add("Branch Index");
				header.add("Branch Weight");
				for (int l=0; l<combBranch.size(); l++)
					header.add(combBranch.getLevel(l).getShortName());
				for (double x : xVals)
					header.add((float)x+"");
				perHeaders.add(CSVFile.getLineStr(header));
			}
			for (int s=0; s<sites.size(); s++) {
				List<FileWriter> perCurveWriters = new ArrayList<>(sitePeriods.size());
				for (int p=0; p<sitePeriods.size(); p++) {
					String outName = siteOutNames.get(s).get(p);
					FileWriter fw = new FileWriter(new File(curveOutDir, outName));
					fw.write(perHeaders.get(p));
					fw.write('\n');
					perCurveWriters.add(fw);
				}
				siteCurveWriters.add(perCurveWriters);
			}
		}

		List<List<String>> siteCombCurveLines = new ArrayList<>(sites.size());
		for (int s=0; s<sites.size(); s++) {
			List<String> perCurveLines = new ArrayList<>(sitePeriods.size());
			siteCombCurveLines.add(perCurveLines);
			List<String> commonPrefix = new ArrayList<>(combBranch.size()+3);
			commonPrefix.add(sites.get(s).getName());
			commonPrefix.add(combBranchIndex+"");
			commonPrefix.add(combBranchWeight+"");
			for (LogicTreeNode node : combBranch)
				commonPrefix.add(node.getShortName());
			String prefixStr = CSVFile.getLineStr(commonPrefix);
			StringBuilder line = new StringBuilder();
			for (int p=0; p<sitePeriods.size(); p++) {
				DiscretizedFunc curve;
				try {
					CurveCombineResult result = combineFutures.get(s).get(p).get();
//					if (s == 0 && p == 0) {
//						System.out.println("DEBUG COMBINE");
//						DiscretizedFunc outerCurve = outerSiteCurves.get(s).get(p).get(outerIndex);
//						DiscretizedFunc innerCurve = innerSiteCurves.get(s).get(p).get(innerIndex);
//						System.out.println("x\touter\tinner\tcomb");
//						for (int i=0; i<outerCurve.size(); i++)
//							System.out.println((float)outerCurve.getX(i)+"\t"+(float)outerCurve.getY(i)
//									+"\t"+(float)innerCurve.getY(i)+"\t"+(float)result.combCurve.getY(i));
//					}
					curve = result.combCurve;
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				line.setLength(0);
				line.append(prefixStr);
				for (Point2D pt : curve)
					line.append(',').append(String.valueOf(pt.getY()));
				line.append('\n');
				
				perCurveLines.add(line.toString());
			}
		}
		
		combineWatch.stop();
		
		curveWriteWatch.start();
		for (int s=0; s<sites.size(); s++)
			for (int p=0; p<sitePeriods.size(); p++)
				siteCurveWriters.get(s).get(p).write(siteCombCurveLines.get(s).get(p));
		curveWriteWatch.stop();
	}

	@Override
	public void close() throws IOException {
		System.out.println("Finalizing site hazard curve files");
		curveWriteWatch.start();
		for (int s=0; s<sites.size(); s++)
			for (int p=0; p<sitePeriods.size(); p++)
				siteCurveWriters.get(s).get(p).close();
		curveWriteWatch.stop();
		
//		blockingZipIOWatch.start(); // TODO?
		// zip them
		File curveZipFile;
		if (curveOutDir == hazardCurvesOutputFile)
			// we were supplied a directory, add .zip for the zip file
			curveZipFile = new File(curveOutDir.getAbsoluteFile()+".zip");
		else
			// we were supplied a zip file, use that directly
			curveZipFile = hazardCurvesOutputFile;
		System.out.println("Building site hazard curve zip file: "+curveZipFile.getAbsolutePath());
		
		ArchiveOutput output = new ArchiveOutput.ZipFileOutput(curveZipFile);
		
		output.putNextEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME);
		sitesCSV.writeToStream(output.getOutputStream());
		output.closeEntry();
		
		combTree.writeToArchive(output, null);
		
		for (int s=0; s<sites.size(); s++) {
			for (int p=0; p<sitePeriods.size(); p++) {
				String csvName = siteOutNames.get(s).get(p);
				System.out.println("Processing site "+s+"/"+sites.size()+" "+csvName);
				output.putNextEntry(csvName);
				
				File inFile = new File(curveOutDir, csvName);
				InputStream in = new BufferedInputStream(new FileInputStream(inFile));
				IOUtils.copy(in, output.getOutputStream());
				in.close();
				
				output.closeEntry();
			}
		}
		output.close();
//		blockingZipIOWatch.stop(); // TODO?
	}

	@Override
	public String getTimeBreakdownString(Stopwatch overallWatch) {
		return "Combining: "+AbstractLogicTreeCombiner.blockingTimePrint(combineWatch, overallWatch)
				+";\tWriting: "+AbstractLogicTreeCombiner.blockingTimePrint(curveWriteWatch, overallWatch);
	}
}
