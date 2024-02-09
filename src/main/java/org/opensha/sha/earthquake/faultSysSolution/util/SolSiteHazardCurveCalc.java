package org.opensha.sha.earthquake.faultSysSolution.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.geometry.spherical.oned.ArcsSet.Split;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.SiteLogicTreeHazardPageGen;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.NEHRP_TestCity;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class SolSiteHazardCurveCalc {
	
	private static final String NAME_HEADER = "Name";
	private static final String LAT_HEADER = "Latitude";
	private static final String LON_HEADER = "Longitude";
	private static final String VS30_HEADER = Vs30_Param.NAME;
	private static final String Z10_HEADER = "Z1.0";
	private static final String Z25_HEADER = "Z2.5";
	
	private static final AttenRelRef GMM_DEFAULT = AttenRelRef.ASK_2014;
	
	private static final double MAX_DIST_DEFAULT = 200d;
	
	private static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption(FaultSysTools.helpOption());
		ops.addOption(FaultSysTools.threadsOption());
		
		// inputs and outputs
		
		ops.addRequiredOption("if", "input-file", true, "Input solution file.");
		
		ops.addOption("n", "name", true,
				"Name of the rupture set/solution, if not supplied then the file name will be used as the name.");

		ops.addOption("cmp", "compare-to", true, "Optional path to an alternative solution for comparison.");
		
		ops.addOption("cn", "comp-name", true,
				"Name of the comparison rupture set, if not supplied then the file name will be used.");
		
		ops.addOption("od", "output-dir", true, "Output directory where curves and a report (with plots) will be written."
				+ "You must supply either this, or --output-file to write curves only (without the repoort). The report "
				+ "will be written to `index.html` and `README.md`, curves will be written to `curves_<period>.csv`, and all "
				+ "plots will be placed in a `resources` subdirctory.");
		
		ops.addOption("of", "output-file", true, "Output CSV file where hazard curves will be written if you don't want "
				+ "to generate a report with plots (alternative to --output-dir). If multiple periods are supplied, "
				+ "a period-specific suffix will be added before the .csv extension. If a comparison model, '_comp' "
				+ "will also be appended.");
		
		ops.addOption(null, "site-location", true, "Site location specified as <lat>,<lon>; must supply either this, "
				+ "--sites, or --nehrp-sites.");
		
		ops.addOption(null, "site-name", true, "Site name, optionally used in conjunction with --site-location.");
		
		ops.addOption(null, "sites", true, "Path to a site list CSV file. The first 3 columns must be: "
				+ "`"+NAME_HEADER+"`, `"+LAT_HEADER+"`, and `"+LON_HEADER+"`. The first row is assumed to be a header, "
				+ "and site parameters can optionally be passed in via additional columns (supplied in any "
				+ "combination/order). Recognized site paramter column headers are: `"+VS30_HEADER+"` (unts are m/s), "
				+ "`"+Z10_HEADER+"` (units are m, supply `NaN` for default treatment), and `"+Z25_HEADER+"` (units are "
				+ "km, supply `NaN` for default treatment).");
		
		ops.addOption(null, "nehrp-sites", false, "Flag to calculate at NEHRP sites. If the solutions supplies a model "
				+ "region, sites in that region will be included, otherwise sites within the hazard calculation maximum "
				+ "distance (see --max-distance) will be included.");
		
		ops.addOption(null, "vs30", true, "Site Vs30 in m/s.");
		
		ops.addOption(null, "z10", true, "Site Z1.0 (depth Vs=1.0 km/s), supplied in meters. If not supplied (and the "
				+ "chosen GMPE uses Z1.0), the default (usually Vs30-dependent) model will be used.");
		
		ops.addOption(null, "z25", true, "Site Z2.5 (depth Vs=2.5 km/s), supplied in kilometers. If not supplied (and the "
				+ "chosen GMPE uses Z2.5), the default (usually Vs30-dependent) model will be used.");
		
		// TODO: site data providers?
		
		// calculation parameters
		
		// TODO: spectrum support?
		
		ops.addRequiredOption("p", "periods", true, "Calculation spectral period(s). Mutliple can be comma separated; supply 0 "
				+ "for PGA, or -1 for PGV.");
		
		ops.addOption(null, "gmpe", true, "GMPE name. Default is `"+GMM_DEFAULT.name()+"`, "
				+ "and the full list can be found at https://github.com/opensha/opensha/blob/master/src/main/java/org/"
				+ "opensha/sha/imr/AttenRelRef.java");
		
		ops.addOption(null, "max-distance", true, "Maximum distance for hazard curve calculations in km. Default is "
				+(int)MAX_DIST_DEFAULT+" km.");
		
		ops.addOption(null, "gridded-seis", true, "By default, gridded seismicity will be included in calculations "
				+ "if gridded sources are present in the input fault system solution. You can override this behavior "
				+ "with this argument, and options are: "+FaultSysTools.enumOptions(IncludeBackgroundOption.class));
		
		ops.addOption(null, "duration", true, "Sets the duration for curve calculations; default is 1 year (annual "
				+ "probabilities of exceedance).");
		
		// TODO
		ops.addOption(null, "disagg-prob", true, "Enables disaggregation at the specified annual probability of "
				+ "exceedance level(s); multiple levels can be comma separated. Requires an active internet connection "
				+ "to generate plots, otherwise only a table will be shown.");
		
		// TODO
		ops.addOption(null, "disagg-iml", true, "Enables disaggregation at the specified intensity measure level(s); "
				+ "multiple levels can be comma separated. Requires an active internet connection to generate plots, "
				+ "otherwise only a table will be shown.");
		
		return ops;
	}
	
	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SolSiteHazardCurveCalc.class);
		
		FaultSysTools.checkPrintHelp(null, cmd, SolSiteHazardCurveCalc.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		FaultSystemSolution sol = FaultSystemSolution.load(inputFile);
		
		String name = cmd.hasOption("name") ? cmd.getOptionValue("name") : "Solution";
		
		FaultSystemSolution compSol = null;
		String compName = null;
		if (cmd.hasOption("compare-to")) {
			compSol = FaultSystemSolution.load(new File(cmd.getOptionValue("compare-to")));
			compName = cmd.hasOption("comp-name") ? cmd.getOptionValue("comp-name") : "Comparison Solution";
		}
		
		File outputDir, outputFile;
		if (cmd.hasOption("output-dir")) {
			Preconditions.checkArgument(!cmd.hasOption("output-file"), "Can't supply both --output-file and --output-dir");
			
			outputDir = new File(cmd.getOptionValue("output-dir"));
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			outputFile = null;
		} else {
			Preconditions.checkArgument(cmd.hasOption("output-file"), "Must supply either --output-file or --output-dir");
			
			outputFile = new File(cmd.getOptionValue("output-file"));
			outputDir = null;
		}
		
		Supplier<ScalarIMR> gmmSupplier = cmd.hasOption("gmpe") ? AttenRelRef.valueOf(cmd.getOptionValue("gmpe")) : GMM_DEFAULT;
		
		ScalarIMR gmm0 = gmmSupplier.get();
		
		double maxDist = cmd.hasOption("max-distance") ?
				Double.parseDouble(cmd.getOptionValue("max-distance")) : MAX_DIST_DEFAULT;
		
		List<Site> sites = new ArrayList<>();
		if (cmd.hasOption("site-location")) {
			Preconditions.checkArgument(!cmd.hasOption("sites"), "Can't supply both --sites and --site-location");
			Preconditions.checkArgument(!cmd.hasOption("sites"), "Can't supply both --nehrpsites and --site-location");
			String locStr = cmd.getOptionValue("site-location");
			Preconditions.checkState(locStr.contains(","),
					"Unexpected site location format, should be <lat>,<lon>, e.g.: 34,-118, supplied: %s", locStr);
			String[] locSplit = locStr.split(",");
			Preconditions.checkState(locSplit.length == 2,
					"Unexpected site location format, should be <lat>,<lon>, e.g.: 34,-118, supplied: %s", locStr);
			
			Location loc = new Location(Double.parseDouble(locSplit[0]), Double.parseDouble(locSplit[1]));
			String siteName = cmd.hasOption("site-name") ? cmd.getOptionValue("site-name") : "Site ("+locStr+")";
			
			Site site = new Site(loc, siteName);
			addDefaultSiteParams(site, gmm0, cmd);
			sites.add(site);
		} else if (cmd.hasOption("nehrp-sites")) {
			Preconditions.checkArgument(!cmd.hasOption("sites"), "Can't supply both --nehrpsites and --sites");
			Region region = null;
			if (sol.getRupSet().hasModule(ModelRegion.class))
				region = sol.getRupSet().getModule(ModelRegion.class).getRegion();
			else if (sol.getGridSourceProvider() != null)
				region = sol.getGridSourceProvider().getGriddedRegion();
			
			for (NEHRP_TestCity nehrp : NEHRP_TestCity.values()) {
				if (region == null) {
					boolean include = false;
					for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
						for (Location loc : sect.getFaultTrace()) {
							if (LocationUtils.horzDistanceFast(loc, nehrp.location()) < maxDist) {
								include = true;
								break;
							}
						}
					}
					if (!include)
						continue;
				} else {
					if (!region.contains(nehrp.location()))
						continue;
				}
				Site site = new Site(nehrp.location(), nehrp.toString());
				addDefaultSiteParams(site, gmm0, cmd);
				sites.add(site);
			}
			sites.sort(new NamedComparator());
		} else {
			Preconditions.checkArgument(cmd.hasOption("sites"), "Must supply either --sites or --site-location");
			File csvFile = new File(cmd.getOptionValue("sites"));
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			Preconditions.checkState(csv.getNumRows() > 1,
					"Expected at least 2 ros (1 header and at least 1 site), have %s", csv.getNumRows());
			Preconditions.checkState(csv.getNumCols() >= 3 && csv.getNumCols() <= 6,
					"Expected between 3 and 6 columns, have %s", csv.getNumCols());
			Preconditions.checkState(csv.get(0, 0).trim().toLowerCase().equals(NAME_HEADER.toLowerCase()),
					"First header coulumn must be `%s`, got: %s", NAME_HEADER, csv.getInt(0, 0));
			Preconditions.checkState(csv.get(0, 1).trim().toLowerCase().equals(LAT_HEADER.toLowerCase()),
					"Second header coulumn must be `%s`, got: %s", LAT_HEADER, csv.getInt(0, 1));
			Preconditions.checkState(csv.get(0, 2).trim().toLowerCase().equals(LON_HEADER.toLowerCase()),
					"Third header coulumn must be `%s`, got: %s", LON_HEADER, csv.getInt(0, 2));
			int vsCol = -1;
			int z10Col = -1;
			int z25Col = -1;
			for (int col=3; col<csv.getNumCols(); col++) {
				String colName = csv.get(0, col).toLowerCase().trim();
				if (colName.equals(VS30_HEADER.toLowerCase())) {
					Preconditions.checkState(vsCol < 0, "Multiple Vs30 columns supplied.");
					Preconditions.checkState(!cmd.hasOption("vs30"),
							"Can't supply Vs30 both in the site CSV file and via the command line");
					vsCol = col;
				} else if (colName.equals(Z10_HEADER.toLowerCase())) {
					Preconditions.checkState(z10Col < 0, "Multiple Z1.0 columns supplied.");
					Preconditions.checkState(!cmd.hasOption("z10"),
							"Can't supply Z1.0 both in the site CSV file and via the command line");
					z10Col = col;
				} else if (colName.equals(Z25_HEADER.toLowerCase())) {
					Preconditions.checkState(z25Col < 0, "Multiple Z2.5 columns supplied.");
					Preconditions.checkState(!cmd.hasOption("z25"),
							"Can't supply Z2.5 both in the site CSV file and via the command line");
					z25Col = col;
				} else {
					throw new IllegalStateException("Unexpected column in header: "+csv.get(0, col));
				}
			}
			
			for (int row=1; row<csv.getNumRows(); row++) {
				Site site = new Site(new Location(csv.getDouble(row, 1), csv.getDouble(row, 2)), csv.get(row, 0));
				addDefaultSiteParams(site, gmm0, cmd);
				if (vsCol >= 0 && site.containsParameter(Vs30_Param.NAME))
					setDoubleParam(site.getParameter(Vs30_Param.NAME), csv.getDouble(row, vsCol));
				if (z10Col >= 0 && site.containsParameter(DepthTo1pt0kmPerSecParam.NAME))
					setDoubleParam(site.getParameter(DepthTo1pt0kmPerSecParam.NAME), csv.getDouble(row, z10Col));
				if (z25Col >= 0 && site.containsParameter(DepthTo2pt5kmPerSecParam.NAME))
					setDoubleParam(site.getParameter(DepthTo2pt5kmPerSecParam.NAME), csv.getDouble(row, z25Col));
				sites.add(site);
			}
		}
		
		Preconditions.checkState(!sites.isEmpty(), "No sites supplied?");
		
		String perStr = cmd.getOptionValue("periods");
		double[] periods;
		if (perStr.contains(",")) {
			String[] split = perStr.split(",");
			periods = new double[split.length];
			for (int p=0; p<periods.length; p++)
				periods[p] = Double.parseDouble(split[p]);
		} else {
			periods = new double[] { Double.parseDouble(perStr) };
		}
		
		DiscretizedFunc[] periodXVals = new DiscretizedFunc[periods.length];
		IMT_Info imtInfo = new IMT_Info();
		for (int p=0; p<periods.length; p++) {
			if (periods[p] == -1d)
				periodXVals[p] = imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
			else if (periods[p] == 0d)
				periodXVals[p] = imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
			else
				periodXVals[p] = imtInfo.getDefaultHazardCurve(SA_Param.NAME);
		}
		
		System.out.println("Building ERF for "+name);
		FaultSystemSolutionERF erf = buildERF(sol, cmd);
		
		int numCurves = sites.size() * periods.length;
		int threads = Integer.min(FaultSysTools.getNumThreads(cmd), numCurves);
		
		List<HazardCalcThread> calcThreads = new ArrayList<>(threads);		
		for (int i=0; i<threads; i++) {
			HazardCurveCalculator calc = new HazardCurveCalculator();
			calc.setMaxSourceDistance(maxDist);
			calcThreads.add(new HazardCalcThread(calc, i == 0 ? gmm0 : gmmSupplier.get()));
		}
		
		List<DiscretizedFunc[]> curves = calcHazardCurves(calcThreads, sites, erf, periods, periodXVals);
		
		// write curves
		for (int p=0; p<periods.length; p++) {
			File csvFile;
			if (outputDir == null) {
				csvFile = outputFile;
				if (periods.length > 1) {
					// add suffix
					String prefix = csvFile.getName();
					if (prefix.toLowerCase().endsWith(".csv"))
						prefix = prefix.substring(0, prefix.toLowerCase().indexOf(".csv"));
					csvFile = new File(csvFile.getParentFile(), SolHazardMapCalc.getCSV_FileName(prefix, periods[p]));
				}
			} else {
				csvFile = new File(outputDir, SolHazardMapCalc.getCSV_FileName("curves", periods[p]));
			}
			writeCurvesCSV(csvFile, extractPeriodCurves(curves, p), sites);
		}
		
		FaultSystemSolutionERF compERF = null;
		List<DiscretizedFunc[]> compCurves = null;
		if (compSol != null) {
			System.out.println("Building ERF for "+compName);
			compERF = buildERF(compSol, cmd);
			
			compCurves = calcHazardCurves(calcThreads, sites, compERF, periods, periodXVals);
			
			// write comparison curves
			for (int p=0; p<periods.length; p++) {
				File csvFile;
				if (outputDir == null) {
					csvFile = outputFile;
					if (periods.length > 1) {
						// add suffix
						String prefix = csvFile.getName();
						if (prefix.toLowerCase().endsWith(".csv"))
							prefix = prefix.substring(0, prefix.toLowerCase().indexOf(".csv"));
						prefix += "_comp";
						csvFile = new File(csvFile.getParentFile(), SolHazardMapCalc.getCSV_FileName(prefix, periods[p]));
					}
				} else {
					csvFile = new File(outputDir, SolHazardMapCalc.getCSV_FileName("curves", periods[p]));
				}
				writeCurvesCSV(csvFile, extractPeriodCurves(compCurves, p), sites);
			}
		}
		
		if (outputDir == null)
			// no report, done
			System.exit(0);
		
		System.out.println("Writing report");
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# "+name +" Hazard Curves");
		lines.add("");
		
		int perWrap;
		if (periods.length > 6)
			perWrap = 4;
		else if (periods.length > 4)
			perWrap = 3;
		else
			perWrap = 2;
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		double duration = erf.getTimeSpan().getDuration();
		
		for (int s=0; s<sites.size(); s++) {
			Site site = sites.get(s);
			
			String prefix = site.getName().replaceAll("\\W+", "_");
			while (prefix.contains("__"))
				prefix = prefix.replace("__", "_");
			
			lines.add("## "+site.getName());
			lines.add(topLink); lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			table.initNewLine();
			for (double period : periods)
				table.addColumn(MarkdownUtils.boldCentered(periodLabel(period)));
			table.finalizeLine();
			
			table.initNewLine();
			for (int p=0; p<periods.length; p++) {
				String curvePrefix = "curves_"+prefix+"_"+periodPrefix(periods[p]);
				plotCurve(resourcesDir, curvePrefix, periods[p], site.getName(), duration, curves.get(s)[p], name,
						compCurves == null ? null : compCurves.get(s)[p], compName);
				table.addColumn("!["+periodLabel(periods[p])+" Curves]("+resourcesDir.getName()+"/"+curvePrefix+".png)");
			}
			table.finalizeLine();
			
			lines.addAll(table.wrap(perWrap, 0).build());
			lines.add("");
			if (duration == 1d) {
				// add return period table
				table = MarkdownUtils.tableBuilder();
				
				table.initNewLine();
				table.addColumn("Return Period");
				for (double period : periods)
					table.addColumn(MarkdownUtils.boldCentered(periodLabel(period)));
				table.finalizeLine();
				
				boolean[] comps = compSol == null ? new boolean[] {false} : new boolean[] {false,true};
				for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
					if (compSol != null) {
						// add line with just the return period
						table.addLine("__"+rp.label+"__");
					}
					for (boolean isComp : comps) {
						table.initNewLine();
						if (compSol != null)
							table.addColumn("_"+(isComp ? compName : name)+"_");
						else
							table.addColumn("__"+rp.label+"__");
						for (int p=0; p<periods.length; p++) {
							DiscretizedFunc curve = isComp ? compCurves.get(s)[p] : curves.get(s)[p];
							double val = SiteLogicTreeHazardPageGen.curveVal(curve, rp);
							table.addColumn((float)val+" "+periodUnits(periods[p]));
						}
						table.finalizeLine();
					}
				}
				
				lines.addAll(table.build());
				lines.add("");
			}
		}
		
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private static void addDefaultSiteParams(Site site, ScalarIMR gmm, CommandLine cmd) {
		for (Parameter<?> param : gmm.getSiteParams()) {
			param = (Parameter<?>) param.clone();
			if (param.getName().equals(Vs30_Param.NAME) && cmd.hasOption("vs30"))
				setDoubleParam(param, Double.parseDouble(cmd.getOptionValue("vs30")));
			else if (param.getName().equals(DepthTo1pt0kmPerSecParam.NAME) && cmd.hasOption("z10"))
				setDoubleParam(param, Double.parseDouble(cmd.getOptionValue("z10")));
			else if (param.getName().equals(DepthTo2pt5kmPerSecParam.NAME) && cmd.hasOption("z25"))
				setDoubleParam(param, Double.parseDouble(cmd.getOptionValue("z25")));
			site.addParameter(param);
		}
	}
	
	private static void setDoubleParam(Parameter<?> param, double value) {
		Preconditions.checkState(param instanceof DoubleParameter,
				"Expected DoubleParamter for %s, is %s", param.getName(), param.getType());
		if (param instanceof WarningDoubleParameter) {
			WarningDoubleParameter warn = (WarningDoubleParameter)param;
			if (!warn.isRecommended(value))
				System.err.println("WARNING: Value '"+value+"' is not recommented for parameter "+param.getName());
			warn.setValueIgnoreWarning(value);
		} else {
			((DoubleParameter)param).setValue(value);
		}
	}
	
	private static FaultSystemSolutionERF buildERF(FaultSystemSolution sol, CommandLine cmd) {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		
		boolean hasGridProv = sol.getGridSourceProvider() != null;
		IncludeBackgroundOption gridOp;
		if (cmd.hasOption("gridded-seis")) {
			gridOp = IncludeBackgroundOption.valueOf(cmd.getOptionValue("gridded-seis").toUpperCase());
			if (gridOp != IncludeBackgroundOption.EXCLUDE)
				Preconditions.checkState(hasGridProv, "Gridded seismicity enabled via --gridded-seis %s, but solution "
						+ "doesn't have gridded sources", gridOp.name());
		} else {
			gridOp = hasGridProv ? IncludeBackgroundOption.INCLUDE : IncludeBackgroundOption.EXCLUDE;
		}
		erf.setParameter(IncludeBackgroundParam.NAME, gridOp);
		
		double duration = cmd.hasOption("duration") ? Double.parseDouble(cmd.getOptionValue("duration")) : 1d;
		erf.getTimeSpan().setDuration(duration);
		
		erf.updateForecast();
		return erf;
	}
	
	private static List<DiscretizedFunc[]> calcHazardCurves(List<HazardCalcThread> calcThreads, List<Site> sites,
			FaultSystemSolutionERF erf, double[] periods, DiscretizedFunc[] periodXVals) {
		SiteHazardTaskDistributor hazardTasks = new SiteHazardTaskDistributor(sites, periods, periodXVals);
		
		int numCurves = sites.size() * periods.length;
		System.out.println("Calculating "+numCurves+" hazard curves ("+sites.size()+" sites and "+periods.length
				+" periods) with "+calcThreads.size()+" threads");
		
		for (HazardCalcThread thread : calcThreads) {
			AbstractERF threadERF;
			if (calcThreads.size() > 1 && sites.size() > 1)
				// give each thread their own distance cache to avoid cache contention
				threadERF = new DistCachedERFWrapper(erf);
			else
				threadERF = erf;
			thread.init(threadERF, hazardTasks);
			
			thread.start();
		}
		
		// join them
		for (HazardCalcThread thread : calcThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		List<DiscretizedFunc[]> ret = new ArrayList<>(sites.size());
		for (Site site : sites) {
			HazardCalcTask[] tasks = hazardTasks.siteTasks.get(site);
			DiscretizedFunc[] curves = new DiscretizedFunc[periods.length];
			for (int p=0; p<periods.length; p++)
				curves[p] = tasks[p].curve;
			ret.add(curves);
		}
		
		return ret;
	}
	
	/**
	 * This distributes tasks to each thread in a round robin fashion so that each thread starts with it's own site
	 * (if possible). Threads will then be given additional curves for that same site before moving on to new sites.
	 * This should speed up calculations (avoid site distance cache misses). 
	 */
	private static class SiteHazardTaskDistributor {

		private List<Site> sites;
		private double[] periods;
		private DiscretizedFunc[] periodXVals;
		private Map<Site, HazardCalcTask[]> siteTasks;
		private int curSiteIndex;
		private int numSitesDone = 0;
		
		public SiteHazardTaskDistributor(List<Site> sites, double[] periods, DiscretizedFunc[] periodXVals) {
			this.sites = sites;
			this.periods = periods;
			siteTasks = new HashMap<>(sites.size());
			for (Site site : sites)
				siteTasks.put(site, new HazardCalcTask[periods.length]);
			this.periodXVals = periodXVals;
			
			curSiteIndex = 0;
		}
		
		public synchronized HazardCalcTask getNextTask(Site preferredSite) {
			if (numSitesDone == sites.size())
				// all done
				return null;
			if (preferredSite != null) {
				// see if we have another period for that site
				HazardCalcTask[] tasks = siteTasks.get(preferredSite);
				for (int p=0; p<tasks.length; p++) {
					if (tasks[p] == null) {
						// available
						tasks[p] = new HazardCalcTask(preferredSite, periods[p], periodXVals[p].deepClone());
						if (p == tasks.length-1) {
							// done
							numSitesDone++;
						}
						return tasks[p];
					}
				}
			}
			// if we're here, we have no preferred site (or that site is already done)
			for (int i=0; i<sites.size(); i++) {
				int siteIndex = (curSiteIndex + i) % sites.size();
				Site site = sites.get(siteIndex);
				HazardCalcTask[] tasks = siteTasks.get(site);
				for (int p=0; p<tasks.length; p++) {
					if (tasks[p] == null) {
						// available
						tasks[p] = new HazardCalcTask(site, periods[p], periodXVals[p].deepClone());
						if (p == tasks.length-1) {
							// done
							numSitesDone++;
						}
						// increment curSiteIndex
						curSiteIndex = (siteIndex+1) % sites.size();
						return tasks[p];
					}
				}
			}
			throw new IllegalStateException(numSitesDone+"/"+sites.size()+" sites are done, but didn't find any tasks?");
		}
	}
	
	private static class HazardCalcTask {
		final Site site;
		final double period;
		final DiscretizedFunc curve;
		
		public HazardCalcTask(Site site, double period, DiscretizedFunc curve) {
			super();
			this.site = site;
			this.period = period;
			this.curve = curve;
		}
	}
	
	private static class HazardCalcThread extends Thread {
		
		private HazardCurveCalculator calc;
		private AbstractERF erf;
		private ScalarIMR gmm;
		private SiteHazardTaskDistributor tasks;

		public HazardCalcThread(HazardCurveCalculator calc, ScalarIMR gmm) {
			this.calc = calc;
			this.gmm = gmm;
		}
		
		public void init(AbstractERF erf, SiteHazardTaskDistributor tasks) {
			this.erf = erf;
			this.tasks = tasks;
		}

		@Override
		public void run() {
			HazardCalcTask task = tasks.getNextTask(null);
			
			while (task != null) {
				DiscretizedFunc linearCurve = task.curve;
				double[] xVals = new double[linearCurve.size()];
				for (int i=0; i<xVals.length; i++)
					xVals[i] = Math.log(linearCurve.getX(i));
				
				LightFixedXFunc logCurve = new LightFixedXFunc(xVals, new double[xVals.length]);
				
				SolHazardMapCalc.setIMforPeriod(gmm, task.period);
				
				calc.getHazardCurve(logCurve, task.site, gmm, erf);
				// fill in y values for linear curve
				for (int i=0; i<xVals.length; i++)
					linearCurve.set(i, logCurve.getY(i));
				
				System.out.println("done calculating curve for "+task.site.name+", period="+(float)task.period);
				
				task = tasks.getNextTask(task.site);
			}
		}
		
	}
	
	private static List<DiscretizedFunc> extractPeriodCurves(List<DiscretizedFunc[]> curves, int periodIndex) {
		List<DiscretizedFunc> ret = new ArrayList<>(curves.size());
		for (DiscretizedFunc[] array : curves)
			ret.add(array[periodIndex]);
		return ret;
	}
	
	private static void writeCurvesCSV(File csvFile, List<DiscretizedFunc> curves, List<Site> sites) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		DiscretizedFunc curve0 = curves.get(0);
		List<String> header = new ArrayList<>(curve0.size()+3);
		
		header.add("Name");
		header.add("Latitude");
		header.add("Longitude");
		for (int i=0; i<curve0.size(); i++)
			header.add((float)curve0.getX(i)+"");
		csv.addLine(header);
		
		for (int s=0; s<sites.size(); s++) {
			List<String> line = new ArrayList<>(header.size());
			Site site = sites.get(s);
			line.add(site.getName());
			line.add((float)site.getLocation().getLatitude()+"");
			line.add((float)site.getLocation().getLongitude()+"");
			DiscretizedFunc curve = curves.get(s);
			Preconditions.checkState(curve.size() == curve0.size());
			for (int i=0; i<curve.size(); i++)
				line.add(curve.getY(i)+"");
			csv.addLine(line);
		}
		
		System.out.println("Writing "+csvFile.getAbsolutePath());
		csv.writeToFile(csvFile);
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	private static String periodLabel(double period) {
		if (period == 0d)
			return "PGA";
		if (period == -1d)
			return "PGV";
		return oDF.format(period)+"s SA";
	}
	private static String periodPrefix(double period) {
		if (period == 0d)
			return "pga";
		if (period == -1d)
			return "pgv";
		return "sa_"+oDF.format(period)+"s";
	}
	private static String periodUnits(double period) {
		if (period == -1d)
			return "(cm/s)";
		return "(g)";
	}
	
	private static void plotCurve(File outputDir, String prefix, double period, String siteName, double duration,
			DiscretizedFunc curve, String name, DiscretizedFunc compCurve, String compName) throws IOException {
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		curve.setName(name);
		funcs.add(curve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED.darker()));
		
		if (compCurve != null) {
			compCurve.setName(compName);
			funcs.add(compCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE.darker()));
		}
		
		Range yRange = new Range(1e-6, 1e0);
		double minX = Double.POSITIVE_INFINITY;
		double maxX = 0d;
		for (DiscretizedFunc func : funcs) {
			for (Point2D pt : func) {
				if (pt.getX() > 1e-2 && (float)pt.getY() < (float)yRange.getUpperBound()
						&& (float)pt.getY() > (float)yRange.getLowerBound()) {
					minX = Math.min(minX, pt.getX());
					maxX = Math.max(maxX, pt.getX());
				}
			}
		}
		minX = Math.pow(10, Math.floor(Math.log10(minX)));
		maxX = Math.pow(10, Math.ceil(Math.log10(maxX)));
		Range xRange = new Range(minX, maxX);
		
		String yAxisLabel;
		List<XYAnnotation> anns = null;
		if (duration == 1d) {
			yAxisLabel = "Annual Probability of Exceedance";
			anns = SiteLogicTreeHazardPageGen.addRPAnnotations(funcs, chars, xRange, SolHazardMapCalc.MAP_RPS);
		} else {
			yAxisLabel = oDF.format(duration)+"-Year Probability of Exceedance";
		}
		
		
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, periodLabel(period)+" "+periodUnits(period), yAxisLabel);
		spec.setLegendInset(compCurve != null);
		if (anns != null)
			spec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		
		gp.drawGraphPanel(spec, true, true, xRange, yRange);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 1000, 800, true, true, false);
	}

}
