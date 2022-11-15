package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

public class FaultAndGriddedSeparateTreeHazardCombiner {

	public static void main(String[] args) throws IOException {
		if (args.length < 7 || args.length > 8) {
			System.err.println("USAGE: <results_fault.zip> <results_fault_hazard_dir> "
					+ "<results_gridded.zip> <results_gridded_hazard_dir> "
					+ "<results_output_combined.zip> <results_output_hazard_combined.zip> <grid-region.geojson> [<periods>]");
			System.exit(1);
		}
		
		int cnt = 0;
		
		File faultSLTFile = new File(args[cnt++]);
		SolutionLogicTree faultSLT = SolutionLogicTree.load(faultSLTFile);
		File faultHazardDir = new File(args[cnt++]);
		
		SolutionLogicTree griddedSLT = SolutionLogicTree.load(new File(args[cnt++]));
		File griddedHazardDir = new File(args[cnt++]);
		
		File resultsOutFile = new File(args[cnt++]);
		File hazardOutFile = new File(args[cnt++]);
		
//		double gridSpacing = Double.parseDouble(args[cnt++]);
		File gridRegFile = new File(args[cnt++]);
		GriddedRegion gridReg;
		if (gridRegFile.getName().endsWith(".zip")) {
			// it's a zip file contains the gridded region file
			ZipFile zip = new ZipFile(gridRegFile);
			ZipEntry gridRegEntry = zip.getEntry("gridded_region.geojson");
			Preconditions.checkNotNull(gridRegEntry);
			BufferedInputStream is = new BufferedInputStream(zip.getInputStream(gridRegEntry));
			Feature gridRegFeature = Feature.read(new InputStreamReader(is));
			gridReg = GriddedRegion.fromFeature(gridRegFeature);
			zip.close();
		} else {
			Feature gridRegFeature = Feature.read(gridRegFile);
			gridReg = GriddedRegion.fromFeature(gridRegFeature);
		}
		double gridSpacing = gridReg.getSpacing();
		double[] periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;
		ReturnPeriods[] rps = ReturnPeriods.values();
		
		if (cnt < args.length) {
			List<Double> periodsList = new ArrayList<>();
			String periodsStr = args[cnt++];
			if (periodsStr.contains(",")) {
				String[] split = periodsStr.split(",");
				for (String str : split)
					periodsList.add(Double.parseDouble(str));
			} else {
				periodsList.add(Double.parseDouble(periodsStr));
			}
			periods = Doubles.toArray(periodsList);
		}
		
		LogicTree<?> faultTree = faultSLT.getLogicTree();
		LogicTree<?> gridTree = griddedSLT.getLogicTree();
		
		List<LogicTreeLevel<? extends LogicTreeNode>> combinedLevels = new ArrayList<>();
		combinedLevels.addAll(faultTree.getLevels());
		for (LogicTreeLevel<?> gridLevel : gridTree.getLevels()) {
			// see if it's already in the fault tree
			boolean duplicate = false;
			for (LogicTreeLevel<?> faultLevel : faultTree.getLevels()) {
				if (faultLevel.getType().equals(gridLevel.getType())) {
					duplicate = true;
					break;
				}
			}
			if (!duplicate)
				combinedLevels.add(gridLevel);
		}
		
		String faultHazardDirName = "hazard_"+(float)gridSpacing+"deg_grid_seis_"+IncludeBackgroundOption.EXCLUDE.name();
		String gridHazardDirName = "hazard_"+(float)gridSpacing+"deg_grid_seis_"+IncludeBackgroundOption.ONLY.name();
		
		// pre-load gridded seismicity hazard curves
		List<DiscretizedFunc[][]> gridSeisCurves = new ArrayList<>();
		for (LogicTreeBranch<?> gridBranch : gridTree) {
			System.out.println("Pre-loading curves for gridded seismicity branch "+gridBranch);
			File branchResultsDir = new File(griddedHazardDir, gridBranch.buildFileName());
			Preconditions.checkState(branchResultsDir.exists(), "%s doesn't exist", branchResultsDir.getAbsolutePath());
			File branchHazardDir = new File(branchResultsDir, gridHazardDirName);
			Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());
			
			gridSeisCurves.add(loadCurves(branchHazardDir, periods));
		}
		
		List<LogicTreeBranch<LogicTreeNode>> combinedBranches = new ArrayList<>();
		File tmpOut = new File(hazardOutFile.getAbsolutePath()+".tmp");
		ZipOutputStream hazardOutZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpOut)));
		
		CompletableFuture<Void> writeFuture = null;
		WriteCounter writeCounter = new WriteCounter();
		
		for (LogicTreeBranch<?> faultBranch : faultTree) {
			System.out.println("Processing fault branch "+faultBranch);
			File branchResultsDir = new File(faultHazardDir, faultBranch.buildFileName());
			Preconditions.checkState(branchResultsDir.exists(), "%s doesn't exist", branchResultsDir.getAbsolutePath());
			File branchHazardDir = new File(branchResultsDir, faultHazardDirName);
			Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());
			
			DiscretizedFunc[][] faultCurves = loadCurves(branchHazardDir, periods);
			
			double faultWeight = faultTree.getBranchWeight(faultBranch);
			
			for (int g=0; g<gridTree.size(); g++) {
				LogicTreeBranch<?> gridBranch = gridTree.getBranch(g);
				LogicTreeBranch<LogicTreeNode> combinedBranch = new LogicTreeBranch<>(combinedLevels);
				for (LogicTreeNode node : faultBranch)
					combinedBranch.setValue(node);
				for (LogicTreeNode node : gridBranch)
					combinedBranch.setValue(node);
				
				double gridWeight = gridTree.getBranchWeight(gridBranch);
				double combWeight = faultWeight * gridWeight;
				
				combinedBranch.setOrigBranchWeight(combWeight);
				
				DiscretizedFunc[][] gridCurves = gridSeisCurves.get(g);
				String combPrefix = combinedBranch.buildFileName();
				
				List<CompletableFuture<Void>> perCombCurves = new ArrayList<>();
				List<CompletableFuture<SimpleEntry<String, GriddedGeoDataSet>>> combineFutures = new ArrayList<>();
				
				for (int p1=0; p1<periods.length; p1++) {
					final int p = p1;
					final double period = periods[p];
					perCombCurves.add(CompletableFuture.runAsync(new Runnable() {

						@Override
						public void run() {
							Preconditions.checkState(faultCurves[p].length == gridCurves[p].length);
							Preconditions.checkState(faultCurves[p].length == gridReg.getNodeCount());
							DiscretizedFunc[] combCurves = new DiscretizedFunc[gridCurves[p].length];
							for (int i=0; i<combCurves.length; i++) {
								DiscretizedFunc faultCurve = faultCurves[p][i];
								DiscretizedFunc gridCurve = gridCurves[p][i];
								Preconditions.checkState(faultCurve.size() == gridCurve.size());
								ArbitrarilyDiscretizedFunc combCurve = new ArbitrarilyDiscretizedFunc();
								for (int j=0; j<faultCurve.size(); j++) {
									double x = faultCurve.getX(j);
									Preconditions.checkState((float)x == (float)gridCurve.getX(j));
									double y1 = faultCurve.getY(j);
									double y2 = gridCurve.getY(j);
									double y;
									if (y1 == 0)
										y = y2;
									else if (y2 == 0)
										y = y1;
									else
										y = 1d - (1d - y1)*(1d - y2);
									combCurve.set(x, y);
								}
								combCurves[i] = combCurve;
							}
							for (ReturnPeriods rp : rps) {
								double curveLevel = rp.oneYearProb;
								String mapFileName = MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt";
								String entryName = combPrefix+"/"+mapFileName;
								CompletableFuture<SimpleEntry<String, GriddedGeoDataSet>> combineFuture = CompletableFuture.supplyAsync(new Supplier<SimpleEntry<String, GriddedGeoDataSet>>() {

									@Override
									public SimpleEntry<String, GriddedGeoDataSet> get() {
										GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
										for (int i=0; i<xyz.size(); i++) {
											DiscretizedFunc curve = combCurves[i];
											double val;
											// curveLevel is a probability, return the IML at that probability
											if (curveLevel > curve.getMaxY())
												val = 0d;
											else if (curveLevel < curve.getMinY())
												// saturated
												val = curve.getMaxX();
											else
												val = curve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
											xyz.set(i, val);
										}
										// write map
										return new SimpleEntry<>(entryName, xyz);
									}
								});
								synchronized (combineFutures) {
									combineFutures.add(combineFuture);
								}
							}
						}
					}));
				}
				
				for (CompletableFuture<Void> future : perCombCurves)
					future.join();
				
				Map<String, GriddedGeoDataSet> writeMap = new HashMap<>();
				
				for (CompletableFuture<SimpleEntry<String, GriddedGeoDataSet>> combineFuture : combineFutures) {
					SimpleEntry<String, GriddedGeoDataSet> entry = combineFuture.join();
					Preconditions.checkState(writeMap.put(entry.getKey(), entry.getValue()) == null,
							"Duplicate entry? %s", entry.getKey());
				}
				
				if (writeFuture != null) 
					writeFuture.join();
				
				// everything should have been written now
				int writable = writeCounter.getWritable();
				Preconditions.checkState(writable == 0, "Have %s writable maps after join? written=%s",
						writable, writeCounter.getWritten());
				
				int expected = periods.length*rps.length;
				Preconditions.checkState(writeMap.size() == expected,
						"Expected $s writeable maps, have %s", expected, writeMap.size());
				
				writeCounter.incrementWritable(expected);
				
				System.out.println("Writing combined branch "+combinedBranches.size()+": "+combinedBranch);
				System.out.println("\tCombined weight: "+(float)faultWeight+" x "+(float)gridWeight+" = "+(float)combWeight);
				System.out.println("\tWriting "+writeCounter.getWritable()+" new maps, "+writeCounter.getWritten()+" written so far");
				combinedBranches.add(combinedBranch);
				
				writeFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							synchronized (hazardOutZip) {
								for (String entryName : writeMap.keySet()) {
									GriddedGeoDataSet xyz = writeMap.get(entryName);
									hazardOutZip.putNextEntry(new ZipEntry(entryName));
									ArbDiscrGeoDataSet.writeXYZStream(xyz, hazardOutZip);
									hazardOutZip.closeEntry();
									writeCounter.incrementWritten();
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
							System.exit(1);
						}
					}
				});
				
			}
		}
		
		if (writeFuture != null)
			writeFuture.join();
		
		System.out.println("Wrote "+writeCounter.getWritten()+" maps in total");
		
		hazardOutZip.putNextEntry(new ZipEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME));
		Feature gridFeature = gridReg.toFeature();
		Feature.write(gridFeature, new OutputStreamWriter(hazardOutZip));
		hazardOutZip.closeEntry();
		
		hazardOutZip.close();
		Files.move(tmpOut, hazardOutFile);
		
		// now copy over results zip file with the new tree
		tmpOut = new File(resultsOutFile.getAbsolutePath()+".tmp");
		ZipArchiveOutputStream zout = new ZipArchiveOutputStream(tmpOut);
		
		org.apache.commons.compress.archivers.zip.ZipFile zip = new org.apache.commons.compress.archivers.zip.ZipFile(faultSLTFile);
		
		zip.copyRawEntries(zout, new ZipArchiveEntryPredicate() {
			
			@Override
			public boolean test(ZipArchiveEntry zipArchiveEntry) {
				if (zipArchiveEntry.getName().endsWith("logic_tree.json")) {
					// update it
					try {
						LogicTree<?> tree = LogicTree.fromExisting(combinedLevels, combinedBranches);
						System.out.println("Updaing logic tree archive with "+tree.size()+" branches: "+zipArchiveEntry.getName());
						
						zout.putArchiveEntry(new ZipArchiveEntry(zipArchiveEntry.getName()));
						BufferedOutputStream bStream = new BufferedOutputStream(zout);
						tree.writeToStream(bStream);
						bStream.flush();
						zout.flush();
						zout.closeArchiveEntry();
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					return false;
				}
				return true;
			}
		});
		
		zip.close();
		zout.close();
		Files.move(tmpOut, resultsOutFile);
	}
	
	private static DiscretizedFunc[][] loadCurves(File hazardDir, double[] periods) throws IOException {
		DiscretizedFunc[][] curves = new DiscretizedFunc[periods.length][];
		for (int p=0; p<periods.length; p++) {
			String fileName = SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
			File csvFile = new File(hazardDir, fileName);
			if (!csvFile.exists())
				csvFile = new File(hazardDir, fileName+".gz");
			Preconditions.checkState(csvFile.exists(), "Curves CSV file not found: %s", csvFile.getAbsolutePath());
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			curves[p] = SolHazardMapCalc.loadCurvesCSV(csv, null);
		}
		return curves;
	}
	
	private static class WriteCounter {
		private int writable = 0;
		private int written = 0;
		
		public synchronized void incrementWritable(int num) {
			writable += num;
		}
		
		public synchronized void incrementWritable() {
			writable++;
		}
		
		public synchronized void incrementWritten() {
			written++;
			writable--;
		}
		
		public synchronized int getWritable() {
			return writable;
		}
		
		public synchronized int getWritten() {
			return written;
		}
	}

}
