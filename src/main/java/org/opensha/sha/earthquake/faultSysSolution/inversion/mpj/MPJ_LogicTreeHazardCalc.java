package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.xyz.AbstractXYZ_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.AbstractExternalFetcher;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.AverageSolutionCreator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.logicTree.ScalarIMR_LogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_LogicTreeHazardCalc extends MPJTaskCalculator {

	private File outputDir;

	private SolutionLogicTree solTree;
	
	private static final double GRID_SPACING_DEFAULT = 0.1d;
	private double gridSpacing = GRID_SPACING_DEFAULT;
	
	private static final double MAX_DIST_DEFAULT = 500;
	private double maxDistance = MAX_DIST_DEFAULT;
	
	private static final double SKIP_MAX_DIST_DEFAULT = 300;
	private double skipMaxSiteDist = SKIP_MAX_DIST_DEFAULT;
	
	private static AttenRelRef GMPE_DEFAULT = AttenRelRef.ASK_2014;
	private AttenRelRef gmpeRef = GMPE_DEFAULT;
	
	private static final double[] PERIODS_DEFAULT = { 0d, 0.2d, 1d };
	private double[] periods = PERIODS_DEFAULT;
	
	private ReturnPeriods[] rps = ReturnPeriods.values();
	
	private static final IncludeBackgroundOption GRID_SEIS_DEFAULT = IncludeBackgroundOption.EXCLUDE;
	private IncludeBackgroundOption gridSeisOp = GRID_SEIS_DEFAULT;

	public MPJ_LogicTreeHazardCalc(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkState(inputFile.exists());
		solTree = SolutionLogicTree.load(inputFile);
		
		if (rank == 0)
			debug("Loaded "+solTree.getLogicTree().size()+" tree nodes/solutions");
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (cmd.hasOption("gridded-seis"))
			gridSeisOp = IncludeBackgroundOption.valueOf(cmd.getOptionValue("gridded-seis"));
		
		if (cmd.hasOption("grid-spacing"))
			gridSpacing = Double.parseDouble(cmd.getOptionValue("grid-spacing"));
		
		if (cmd.hasOption("max-distance"))
			maxDistance = Double.parseDouble(cmd.getOptionValue("max-distance"));
		
		if (cmd.hasOption("skip-max-distance"))
			skipMaxSiteDist = Double.parseDouble(cmd.getOptionValue("skip-max-distance"));
		
		if (cmd.hasOption("gmpe"))
			gmpeRef = AttenRelRef.valueOf(cmd.getOptionValue("gmpe"));
		
		if (cmd.hasOption("periods")) {
			List<Double> periodsList = new ArrayList<>();
			String periodsStr = cmd.getOptionValue("periods");
			if (periodsStr.contains(",")) {
				String[] split = periodsStr.split(",");
				for (String str : split)
					periodsList.add(Double.parseDouble(str));
			} else {
				periodsList.add(Double.parseDouble(periodsStr));
			}
			periods = Doubles.toArray(periodsList);
		}
		
		if (rank == 0) {
			waitOnDir(outputDir, 5, 1000);
			
			postBatchHook = new AsyncHazardWriter();
		}
	}
	
	private class AsyncHazardWriter extends AsyncPostBatchHook {
		
		private ZipOutputStream zout;
		
		public AsyncHazardWriter() throws FileNotFoundException {
			super(1);
			File outputFile = new File(outputDir.getParentFile(), "results_hazard.zip");
			zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
		}

		@Override
		public void shutdown() {
			super.shutdown();
			try {
				zout.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("Async: processing batch of size "+batch.length+" from "+processIndex+": "+getCountsString());
			try {
				for (int index : batch) {
					File runDir = getSolDir(solTree.getLogicTree().getBranch(index));
					zout.putNextEntry(new ZipEntry(runDir.getName()));
					zout.closeEntry();
					zout.flush();
					for (ReturnPeriods rp : rps) {
						for (double period : periods) {
							String prefix = mapPrefix(period, rp);
							
							File mapFile = new File(runDir, prefix+".txt");
							Preconditions.checkState(mapFile.exists());
							
							ZipEntry mapEntry = new ZipEntry(runDir.getName()+"/"+mapFile.getName());
							debug("Async: zipping "+mapEntry.getName());
							zout.putNextEntry(mapEntry);
							BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(mapFile));
							inStream.transferTo(zout);
							inStream.close();
							zout.flush();
							zout.closeEntry();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				abortAndExit(e, 1);
			}
			debug("Async: DONE processing batch of size "+batch.length+" from "+processIndex+": "+getCountsString());
		}
		
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (rank == 0) {
			debug("waiting for any post batch hook operations to finish");
			((AsyncPostBatchHook)postBatchHook).shutdown();
			debug("post batch hook done");
		}
	}
	
	private static void waitOnDir(File dir, int maxRetries, long sleepMillis) {
		int retry = 0;
		while (!(dir.exists() || dir.mkdir())) {
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (retry++ > maxRetries)
				throw new IllegalStateException("Directory doesn't exist and couldn't be created after "
						+maxRetries+" retries: "+dir.getAbsolutePath());
		}
	}

	@Override
	protected int getNumTasks() {
		return solTree.getLogicTree().size();
	}
	
	protected File getSolDir(LogicTreeBranch<?> branch) {
		String dirName = branch.buildFileName();
		File runDir = new File(outputDir, dirName);
		Preconditions.checkState(runDir.exists() || runDir.mkdir());
		
		return runDir;
	}
	
	public static String mapPrefix(double period, ReturnPeriods rp) {
		String ret = "map_";
		if (period == 0d)
			ret += "pga";
		else
			ret += (float)period+"s";
		ret += "_"+rp.name();
		return ret;
	}
	
	private Supplier<ScalarIMR> getGMM_Supplier(LogicTreeBranch<?> branch) {
		Supplier<ScalarIMR> supplier;
		if (branch.hasValue(ScalarIMR_LogicTreeNode.class))
			supplier = branch.requireValue(ScalarIMR_LogicTreeNode.class);
		else
			supplier = gmpeRef;
		
		if (branch.hasValue(ScalarIMR_ParamsLogicTreeNode.class)) {
			ScalarIMR_ParamsLogicTreeNode params = branch.requireValue(ScalarIMR_ParamsLogicTreeNode.class);
			return new Supplier<ScalarIMR>() {

				@Override
				public ScalarIMR get() {
					ScalarIMR imr = supplier.get();
					params.setParams(imr);
					return imr;
				}
			};
		}
		
		return supplier;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			System.gc();
			LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(index);
			
			debug("Loading index "+index+": "+branch);
			
			FaultSystemSolution sol = solTree.forBranch(branch);
			
			Region region = new ReportMetadata(new RupSetMetadata(null, sol)).region;
			GriddedRegion gridRegion = new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
			
			File runDir = getSolDir(branch);
			
			SolHazardMapCalc calc = null;
			if (runDir.exists()) {
				// see if it's already done
				try {
					calc = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, runDir, "curves");
				} catch (Exception e) {}
			}
			if (calc == null) {
				Supplier<ScalarIMR> gmpeSupplier = getGMM_Supplier(branch);
				ScalarIMR gmpe = gmpeSupplier.get();
				String paramStr = "";
				for (Parameter<?> param : gmpe.getOtherParams())
					paramStr += "; "+param.getName()+": "+param.getValue();
				debug("Calculating hazard curve for "+index
						+"\n\tBranch: "+branch
						+"\n\tGMPE: "+gmpe.getName()+paramStr);
				calc = new SolHazardMapCalc(sol, gmpeSupplier, gridRegion, gridSeisOp, periods);
				calc.setMaxSourceSiteDist(maxDistance);
				calc.setSkipMaxSourceSiteDist(skipMaxSiteDist);
				
				calc.calcHazardCurves(getNumThreads());
				calc.writeCurvesCSVs(runDir, "curves", true);
			}
			
			for (ReturnPeriods rp : rps) {
				for (double period : periods) {
					GriddedGeoDataSet map = calc.buildMap(period, rp);
					
					String prefix = mapPrefix(period, rp);
					
					AbstractXYZ_DataSet.writeXYZFile(map, new File(runDir, prefix+".txt"));
				}
			}
		}
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("if", "input-file", true, "Path to input file (solution logic tree zip)");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("sp", "grid-spacing", true, "Grid spacing in decimal degrees. Default: "+(float)GRID_SPACING_DEFAULT);
		ops.addOption("md", "max-distance", true, "Maximum source-site distance in km. Default: "+(float)MAX_DIST_DEFAULT);
		ops.addOption("smd", "skip-max-distance", true, "Skip sites with no source-site distances below this value, in km. "
				+ "Default: "+(float)SKIP_MAX_DIST_DEFAULT);
		ops.addOption("gs", "gridded-seis", true, "Gridded seismicity option. One of "
				+FaultSysTools.enumOptions(IncludeBackgroundOption.class)+". Default: "+GRID_SEIS_DEFAULT.name());
		ops.addOption("gm", "gmpe", true, "Sets GMPE. Note that this will be overriden if the Logic Tree "
				+ "supplies GMPE choices. Default: "+GMPE_DEFAULT.name());
		ops.addOption("p", "periods", true, "Calculation period(s). Mutliple can be comma separated");
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_LogicTreeHazardCalc.class);
			
			MPJ_LogicTreeHazardCalc driver = new MPJ_LogicTreeHazardCalc(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
