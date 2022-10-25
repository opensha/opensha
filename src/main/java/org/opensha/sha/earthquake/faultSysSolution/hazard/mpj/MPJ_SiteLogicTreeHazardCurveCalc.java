package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import mpi.MPI;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class MPJ_SiteLogicTreeHazardCurveCalc extends MPJTaskCalculator {
	
	public static final String SITES_CSV_FILE_NAME = "sites.csv";
	private CSVFile<String> inputSitesCSV;
	private List<Site> sites;
	
	private static final double MAX_DIST_DEFAULT = 500;
	private double maxDistance = MAX_DIST_DEFAULT;
	
	private static AttenRelRef GMPE_DEFAULT = AttenRelRef.ASK_2014;
	private AttenRelRef gmpeRef = GMPE_DEFAULT;
	
//	private static final double[] PERIODS_DEFAULT = { 0d, 0.2d, 1d };
	private static final double[] PERIODS_DEFAULT = { 0d, 1d };
	private double[] periods = PERIODS_DEFAULT;
	
	private DiscretizedFunc[] xVals;
	private DiscretizedFunc[] logXVals;
	
	private static final IncludeBackgroundOption GRID_SEIS_DEFAULT = IncludeBackgroundOption.INCLUDE;
	private IncludeBackgroundOption gridSeisOp = GRID_SEIS_DEFAULT;
	
	private SolutionLogicTree solTree;
	private LogicTree<?> tree;
	
	private File outputDir;
	
	private List<String> sitePrefixes;
	
	private List<List<CSVFile<String>>> siteCSVs;
	
	private ExecutorService exec;
	
	private File outputFile;

	public MPJ_SiteLogicTreeHazardCurveCalc(CommandLine cmd) throws IOException {
		super(cmd);
		this.shuffle = false;
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkState(inputFile.exists());
		if (inputFile.isDirectory()) {
			Preconditions.checkArgument(cmd.hasOption("logic-tree"), "Must supply logic tree file if input-file is"
					+ " a results directory");
			File logicTreeFile = new File(cmd.getOptionValue("logic-tree"));
			Preconditions.checkArgument(logicTreeFile.exists(), "Logic tree file doesn't exist: %s",
					logicTreeFile.getAbsolutePath());
			LogicTree<?> tree = LogicTree.read(logicTreeFile);
			
			solTree = new SolutionLogicTree.ResultsDirReader(inputFile, tree);
		} else {
			// it should be SolutionLogicTree zip file
			solTree = SolutionLogicTree.load(inputFile);
		}
		tree = solTree.getLogicTree();
		
		if (rank == 0)
			debug("Loaded "+solTree.getLogicTree().size()+" tree nodes/solutions");
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (cmd.hasOption("gridded-seis"))
			gridSeisOp = IncludeBackgroundOption.valueOf(cmd.getOptionValue("gridded-seis"));
		
		if (cmd.hasOption("max-distance"))
			maxDistance = Double.parseDouble(cmd.getOptionValue("max-distance"));
		
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
		
		File sitesFile = new File(cmd.getOptionValue("sites-file"));
		Preconditions.checkState(sitesFile.exists());
		inputSitesCSV = CSVFile.readFile(sitesFile, true);
		ScalarIMR gmpe = gmpeRef.instance(null);
		gmpe.setParamDefaults();
		sites = parseSitesCSV(inputSitesCSV, gmpe);
		sitePrefixes = new ArrayList<>();
		siteCSVs = new ArrayList<>();
		for (Site site : sites) {
			if (rank == 0) {
				debug("Site "+site.getName()+" at "+site.getLocation());
				for (Parameter<?> param : site)
					debug(param.getName()+": "+param.getValue());
			}
			sitePrefixes.add(site.getName().replaceAll("\\W+", "_"));
			siteCSVs.add(null);
		}
		
		xVals = new DiscretizedFunc[periods.length];
		logXVals = new DiscretizedFunc[periods.length];
		IMT_Info imtInfo = new IMT_Info();
		for (int p=0; p<periods.length; p++) {
			if (periods[p] == -1d)
				xVals[p] = imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
			else if (periods[p] == 0d)
				xVals[p] = imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
			else
				xVals[p] = imtInfo.getDefaultHazardCurve(SA_Param.NAME);
			logXVals[p] = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : xVals[p])
				logXVals[p].set(Math.log(pt.getX()), 0d);
		}
		
		if (rank == 0) {
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			if (cmd.hasOption("output-file"))
				outputFile = new File(cmd.getOptionValue("output-file"));
			else
				outputFile = new File(outputDir.getParentFile(), outputDir.getName()+".zip");
		}
		
		exec = Executors.newFixedThreadPool(getNumThreads());
	}
	
	public static List<Site> parseSitesCSV(CSVFile<String> sitesCSV, ScalarIMR gmpe) {
		List<Site> sites = new ArrayList<>();
		for (int row=0; row<sitesCSV.getNumRows(); row++) {
			if (row == 0) {
				// see if first row is a header
				try {
					sitesCSV.getDouble(row, 1);
					sitesCSV.getDouble(row, 2);
				} catch (NumberFormatException e) {
					continue;
				}
			}
			String siteName = sitesCSV.get(row, 0).trim();
			double lat = sitesCSV.getDouble(row, 1);
			double lon = sitesCSV.getDouble(row, 2);
			Location loc = new Location(lat, lon);
			Site site = new Site(loc, siteName);
			if (gmpe != null) {
				for (Parameter<?> param : gmpe.getSiteParams())
					site.addParameter((Parameter<?>)param.clone());
				
				for (int col=3; col<sitesCSV.getNumCols(); col++) {
					String paramName = sitesCSV.get(0, col);
					for (Parameter<?> param : site) {
						if (param.getName().toLowerCase().equals(paramName.toLowerCase()) && param instanceof DoubleParameter) {
							((DoubleParameter)param).setValue(sitesCSV.getDouble(row, col));
						}
					}
				}
			}
			sites.add(site);
		}
		Preconditions.checkState(!sites.isEmpty());
		return sites;
	}

	@Override
	protected int getNumTasks() {
		return tree.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int branchIndex : batch) {
			LogicTreeBranch<?> branch = tree.getBranch(branchIndex);
			FaultSystemSolution sol = solTree.forBranch(branch);
			FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
			if (gridSeisOp == IncludeBackgroundOption.INCLUDE || gridSeisOp == IncludeBackgroundOption.ONLY)
				Preconditions.checkNotNull(sol.getGridSourceProvider(),
						"Grid source provider is null, but gridded seis option is %s", gridSeisOp);
			erf.setParameter(IncludeBackgroundParam.NAME, gridSeisOp);
			erf.updateForecast();
			Supplier<ScalarIMR> gmpeSupplier = MPJ_LogicTreeHazardCalc.getGMM_Supplier(branch, gmpeRef);
			
			Deque<DistCachedERFWrapper> deque = new ArrayDeque<>();
			
			List<Future<SiteCalcCallable>> futures = new ArrayList<>();
			
			for (int siteIndex=0; siteIndex<sites.size(); siteIndex++)
				futures.add(exec.submit(new SiteCalcCallable(siteIndex, erf, deque, gmpeSupplier)));
			
			for (Future<SiteCalcCallable> future : futures) {
				SiteCalcCallable calc = future.get();
				
				List<CSVFile<String>> csvs = siteCSVs.get(calc.siteIndex);
				if (csvs == null) {
					csvs = new ArrayList<>();
					for (int p=0; p<periods.length; p++) {
						CSVFile<String> csv = new CSVFile<>(true);
						List<String> header = new ArrayList<>();
						header.add("Site Name");
						header.add("Branch Index");
						header.add("Branch Weight");
						for (int l=0; l<branch.size(); l++)
							header.add(branch.getLevel(l).getShortName());
						for (Point2D pt : xVals[p])
							header.add((float)pt.getX()+"");
						csv.addLine(header);
						csvs.add(csv);
					}
					siteCSVs.set(calc.siteIndex, csvs);
				}
				List<String> commonPrefix = new ArrayList<>();
				commonPrefix.add(sites.get(calc.siteIndex).getName());
				commonPrefix.add(branchIndex+"");
				commonPrefix.add(tree.getBranchWeight(branchIndex)+"");
				for (LogicTreeNode node : branch)
					commonPrefix.add(node.getShortName());
				for (int p=0; p<periods.length; p++) {
					List<String> line = new ArrayList<>(commonPrefix.size()+xVals[p].size());
					line.addAll(commonPrefix);
					for (Point2D pt : calc.curves[p])
						line.add(pt.getY()+"");
					csvs.get(p).addLine(line);
				}
			}
		}
	}
	
	private synchronized DistCachedERFWrapper checkOutERF(FaultSystemSolutionERF fssERF, Deque<DistCachedERFWrapper> deque) {
		if (!deque.isEmpty()) {
			return deque.pop();
		}
		return new DistCachedERFWrapper(fssERF);
	}
	
	private synchronized void checkInERF(DistCachedERFWrapper erf, Deque<DistCachedERFWrapper> deque) {
		deque.push(erf);
	}
	
	private class SiteCalcCallable implements Callable<SiteCalcCallable> {
		// inputs
		private int siteIndex;
		private FaultSystemSolutionERF fssERF;
		private Deque<DistCachedERFWrapper> erfDeque;
		private Supplier<ScalarIMR> gmpeSupplier;
		
		// outputs
		private DiscretizedFunc[] curves;
		
		public SiteCalcCallable(int siteIndex, FaultSystemSolutionERF fssERF, Deque<DistCachedERFWrapper> erfDeque,
				Supplier<ScalarIMR> gmpeSupplier) {
			super();
			this.siteIndex = siteIndex;
			this.fssERF = fssERF;
			this.erfDeque = erfDeque;
			this.gmpeSupplier = gmpeSupplier;
		}

		@Override
		public SiteCalcCallable call() throws Exception {
			DistCachedERFWrapper erf = checkOutERF(fssERF, erfDeque);
			
			ScalarIMR gmpe = gmpeSupplier.get();
			
			Site site = sites.get(siteIndex);
			
			curves = new DiscretizedFunc[periods.length];
			
			HazardCurveCalculator calc = new HazardCurveCalculator();
			calc.setMaxSourceDistance(maxDistance);
			
			for (int p=0; p<periods.length; p++) {
				if (periods[p] == -1d) {
					gmpe.setIntensityMeasure(PGV_Param.NAME);
				} else if (periods[p] == 0d) {
					gmpe.setIntensityMeasure(PGA_Param.NAME);
				} else {
					Preconditions.checkState(periods[p] > 0d);
					gmpe.setIntensityMeasure(SA_Param.NAME);
					SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), periods[p]);
				}
				
				DiscretizedFunc logHazCurve = logXVals[p].deepClone();
				
				calc.getHazardCurve(logHazCurve, site, gmpe, erf);
				
				double sumY = logHazCurve.calcSumOfY_Vals();
				if (!Double.isFinite(sumY) || sumY <= 0d) {
					System.err.println("Hazard curve is non-finite or zero. sumY="+sumY);
					System.err.println("\tSite: "+site.getName()+", "+site.getLocation());
					System.err.println("\tGMPE: "+gmpe.getName());
					System.err.println("\tLog Curve:\n"+logHazCurve);
				}
				Preconditions.checkState(Double.isFinite(sumY), "Non-finite hazard curve");
				
				curves[p] = xVals[p].deepClone();
				Preconditions.checkState(curves[p].size() == logHazCurve.size());
				for (int i=0; i<logHazCurve.size(); i++)
					curves[p].set(i, logHazCurve.getY(i));
			}
			
			checkInERF(erf, erfDeque);
			return this;
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		exec.shutdown();
		
		File processDir = new File(outputDir, "process_"+rank);
		Preconditions.checkState(processDir.exists() || processDir.mkdir());
		
		for (int s=0; s<sites.size(); s++) {
			List<CSVFile<String>> csvs = siteCSVs.get(s);
			if (csvs == null)
				continue;
			for (int p=0; p<csvs.size(); p++) {
				CSVFile<String> csv = csvs.get(p);
				String csvName = getCSVName(s, p);
				csv.writeToFile(new File(processDir, csvName));
			}
		}
		
		MPI.COMM_WORLD.Barrier();
		
		if (rank == 0) {
			// merge them
			for (int rank=1; rank<size; rank++) {
				processDir = new File(outputDir, "process_"+rank);
				Preconditions.checkState(processDir.exists());
				for (int s=0; s<sites.size(); s++) {
					List<CSVFile<String>> csvs = siteCSVs.get(s);
					if (csvs == null) {
						csvs = new ArrayList<>();
						for (int p=0; p<periods.length; p++)
							csvs.add(null);
						siteCSVs.set(s, csvs);
					}
					for (int p=0; p<periods.length; p++) {
						CSVFile<String> destCSV = csvs.get(p);
						File sourceCSVFile = new File(processDir, getCSVName(s, p));
						if (sourceCSVFile.exists()) {
							CSVFile<String> sourceCSV = CSVFile.readFile(sourceCSVFile, true);
							int origCount = destCSV == null ? 0 : destCSV.getNumRows()-1;
							if (destCSV == null) {
								destCSV = sourceCSV;
								csvs.set(p, sourceCSV);
							} else {
								for (int row=1; row<sourceCSV.getNumRows(); row++)
									destCSV.addLine(sourceCSV.getLine(row));
							}
							int count = destCSV.getNumRows()-1;
							debug("Merged in "+(count-origCount)+" curves from "+rank+" for site "
									+sites.get(s).getName()+", period="+(float)periods[p]);
						}
					}
				}
			}
			
			// write outputs
			BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(outputFile));
			ZipOutputStream zout = new ZipOutputStream(bout);
			zout.putNextEntry(new ZipEntry(SITES_CSV_FILE_NAME));
			inputSitesCSV.writeToStream(zout);
			zout.closeEntry();
			
			tree.writeToArchive(zout, null);
			
			for (int s=0; s<sites.size(); s++) {
				List<CSVFile<String>> csvs = siteCSVs.get(s);
				Preconditions.checkNotNull(csvs);
				for (int p=0; p<periods.length; p++) {
					CSVFile<String> csv = csvs.get(p);
					Preconditions.checkState(csv.getNumRows() == tree.size()+1, "Expected %s rows, have %s",
							tree.size()+1, csv.getNumRows());
					String csvName = getCSVName(s, p);
					csv.writeToFile(new File(outputDir, csvName));
					
					zout.putNextEntry(new ZipEntry(csvName));
					csv.writeToStream(zout);
					zout.closeEntry();
				}
			}
			
			zout.close();
			
			// delete process files
			for (int rank=0; rank<size; rank++) {
				processDir = new File(outputDir, "process_"+rank);
				Preconditions.checkState(processDir.exists());
				FileUtils.deleteRecursive(processDir);
			}
		}
	}
	
	private String getCSVName(int siteIndex, int periodIndex) {
		String ret = sitePrefixes.get(siteIndex);
		if (periods[periodIndex] == -1d)
			ret += "_pgv";
		else if (periods[periodIndex] == 0d)
			ret += "_pga";
		else
			ret += "_sa_"+(float)periods[periodIndex];
		ret += ".csv";
		return ret;
	}

	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("if", "input-file", true, "Path to input file (solution logic tree zip)");
		ops.addOption("lt", "logic-tree", true, "Path to logic tree JSON file, required if a results directory is "
				+ "supplied with --input-file");
		ops.addRequiredOption("sf", "sites-file", true, "Path to sites CSV file");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("of", "output-file", true, "Path to output zip file. Default will be based on the output directory");
		ops.addOption("md", "max-distance", true, "Maximum source-site distance in km. Default: "+(float)MAX_DIST_DEFAULT);
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
			
			CommandLine cmd = parse(options, args, MPJ_SiteLogicTreeHazardCurveCalc.class);
			
			MPJ_SiteLogicTreeHazardCurveCalc driver = new MPJ_SiteLogicTreeHazardCurveCalc(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
