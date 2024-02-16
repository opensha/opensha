package org.opensha.sha.earthquake.faultSysSolution.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.ReturnPeriodUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator.EpsilonCategories;
import org.opensha.sha.calc.disaggregation.chart3d.PureJavaDisaggPlotter;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;
import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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
		
		ops.addOption(null, "return-periods", true, "Sets custom return periods (in years) to highlight in plots (or use in "
				+ "disaggregations if --disagg-rps is supplied). Default are those corresponding to 2% and 10% "
				+ "probability in 50 years.");
		
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
		
		ops.addOption(null, "disagg-prob", true, "Enables disaggregation at the specified probability of exceedance "
				+ "level(s); multiple levels can be comma separated.");
		
		ops.addOption(null, "disagg-iml", true, "Enables disaggregation at the specified intensity measure level(s); "
				+ "multiple levels can be comma separated.");
		
		ops.addOption(null, "disagg-rps", false, "Enables disaggregation at the recurrence intervals. By default, those "
				+ "are those corresponding to 2% and 10% in 50 years, override with --return-periods.");
		
		return ops;
	}
	
	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
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
		
		CustomReturnPeriod[] rps;
		if (cmd.hasOption("return-periods")) {
			String rpStr = cmd.getOptionValue("return-periods");
			double[] rpYears;
			if (rpStr.contains(",")) {
				String[] split = rpStr.split(",");
				rpYears = new double[split.length];
				for (int p=0; p<periods.length; p++)
					rpYears[p] = Double.parseDouble(split[p]);
			} else {
				rpYears = new double[] { Double.parseDouble(rpStr) };
			}
			rps = new CustomReturnPeriod[rpYears.length];
			for (int r=0; r<rps.length; r++)
				rps[r] = new CustomReturnPeriod(rpYears[r], erf.getTimeSpan().getDuration());
		} else {
			rps = new CustomReturnPeriod[] {
					new CustomReturnPeriod(ReturnPeriods.TWO_IN_50, erf.getTimeSpan().getDuration()),
					new CustomReturnPeriod(ReturnPeriods.TEN_IN_50, erf.getTimeSpan().getDuration())
			};
		}
		
		int numDisagg = 0;
		
		// disaggs
		double[] disaggProbs = null;
		if (cmd.hasOption("disagg-rps")) {
			disaggProbs = new double[rps.length];
			for (int r=0; r<rps.length; r++)
				disaggProbs[r] = rps[r].prob;
			numDisagg += disaggProbs.length;
		}
		if (cmd.hasOption("disagg-prob")) {
			String dStr = cmd.getOptionValue("disagg-prob");
			double[] newDisaggProbs;
			if (dStr.contains(",")) {
				String[] split = dStr.split(",");
				newDisaggProbs = new double[split.length];
				for (int i=0; i<newDisaggProbs.length; i++)
					newDisaggProbs[i] = Double.parseDouble(split[i]);
			} else {
				newDisaggProbs = new double[] { Double.parseDouble(dStr) };
			}
			numDisagg += newDisaggProbs.length;
			if (disaggProbs != null) {
				// combine with RPs
				int prevNum = disaggProbs.length;
				disaggProbs = Arrays.copyOf(disaggProbs, disaggProbs.length+newDisaggProbs.length);
				System.arraycopy(newDisaggProbs, 0, disaggProbs, prevNum, newDisaggProbs.length);
			} else {
				disaggProbs = newDisaggProbs;
			}
		}
		double[] disaggIMLs = null;
		if (cmd.hasOption("disagg-iml")) {
			String dStr = cmd.getOptionValue("disagg-iml");
			if (dStr.contains(",")) {
				String[] split = dStr.split(",");
				disaggIMLs = new double[split.length];
				for (int i=0; i<disaggIMLs.length; i++)
					disaggIMLs[i] = Double.parseDouble(split[i]);
			} else {
				disaggIMLs = new double[] { Double.parseDouble(dStr) };
			}
			numDisagg += disaggIMLs.length;
		}
		
		List<DisaggResult[][]> disaggResults = null;
		
		if (numDisagg > 0) {
			System.out.println("Disaggregating at "+numDisagg+" levels per site/period");
			
			List<DisaggCalcThread> disaggThreads = new ArrayList<>(threads);
			HazardCurveCalculator curveCalc = new HazardCurveCalculator();
			curveCalc.setMaxSourceDistance(maxDist);
			ParameterList calcParams = curveCalc.getAdjustableParams();
			for (int i=0; i<threads; i++) {
				disaggThreads.add(new DisaggCalcThread(new DisaggregationCalculator(), calcParams,
						i == 0 ? gmm0 : gmmSupplier.get(), disaggProbs, disaggIMLs));
			}
			
			disaggResults = calcDisagg(disaggThreads, sites, erf, periods, curves);
			
			if (outputDir == null) {
				String prefix = outputFile.getName();
				if (prefix.toLowerCase().endsWith(".csv"))
					prefix = prefix.substring(0, prefix.toLowerCase().indexOf(".csv"));
				writeDisaggCSVs(outputDir, prefix+"_disagg", sites, periods, disaggResults, rps);
			} else {
				writeDisaggCSVs(outputDir, "disagg", sites, periods, disaggResults, rps);
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
		
		List<CompletableFuture<Void>> plotFutures = new ArrayList<>();
		
		int numDisaggSources = 10;
		
		GeographicMapMaker disaggMapMaker = null;
		CPT disaggContribCPT = null;
		if (numDisagg > 0) {
			disaggMapMaker = new GeographicMapMaker(sol.getRupSet().getFaultSectionDataList());
			disaggContribCPT = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse().rescale(0d, 100d);
			disaggContribCPT.setNanColor(Color.WHITE);
		}
		
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
				plotFutures.add(plotCurve(resourcesDir, curvePrefix, periods[p], site.getName(), duration, rps, curves.get(s)[p], name,
						compCurves == null ? null : compCurves.get(s)[p], compName));
				table.addColumn("!["+periodLabel(periods[p])+" Curves]("+resourcesDir.getName()+"/"+curvePrefix+".png)");
			}
			table.finalizeLine();
			
			lines.addAll(table.wrap(perWrap, 0).build());
			lines.add("");
			
			// add return period table
			table = MarkdownUtils.tableBuilder();
			
			table.initNewLine();
			table.addColumn("Return Period");
			for (double period : periods)
				table.addColumn(periodLabel(period));
			table.finalizeLine();
			
			boolean[] comps = compSol == null ? new boolean[] {false} : new boolean[] {false,true};
			for (CustomReturnPeriod rp : rps) {
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
						double val = curveVal(curve, rp);
						table.addColumn((float)val+" "+periodUnits(periods[p]));
					}
					table.finalizeLine();
				}
			}
			
			lines.addAll(table.build());
			lines.add("");
			
			if (numDisagg > 0) {
				lines.add("### "+site.getName()+" Disaggregations");
				lines.add(topLink); lines.add("");
				
				DisaggResult[][] results = disaggResults.get(s);
				
				table = MarkdownUtils.tableBuilder();
				for (int p=0; p<periods.length; p++) {
					if (periods.length > 1) {
						table.initNewLine();
						table.addColumn(MarkdownUtils.boldCentered(periodLabel(periods[p])));
						for (int i=0; i<numDisagg-1; i++)
							table.addColumn("");
						table.finalizeLine();
					}
					
					table.initNewLine();
					for (int d=0; d<numDisagg; d++) {
						String label = disaggLabel(results[p][d], periods[p], rps);
						table.addColumn(MarkdownUtils.boldCentered(label));
					}
					table.finalizeLine();
					
					table.initNewLine();
					for (int d=0; d<numDisagg; d++) {
						String disaggPrefix = "disagg_"+prefix+"_"+periodPrefix(periods[p])
								+"_"+disaggPrefix(results[p][d], rps);
						DisaggResult result = results[p][d];
						plotFutures.add(CompletableFuture.runAsync(new Runnable() {
							
							@Override
							public void run() {
								try {
									PureJavaDisaggPlotter.writeChartPlot(resourcesDir, disaggPrefix,
											result.plotData, 800, 800, true, true);
								} catch (IOException e) {
									throw ExceptionUtils.asRuntimeException(e);
								}
							}
						}));
						table.addColumn("![Disagg Plot]("+resourcesDir.getName()+"/"+disaggPrefix+".png)");
					}
					table.finalizeLine();
					
					if (!erf.getParameter(IncludeBackgroundParam.NAME).getValue().equals(IncludeBackgroundOption.ONLY)) {
						// plot maps
						table.initNewLine();
						for (int d=0; d<numDisagg; d++) {
							String disaggPrefix = "disagg_"+prefix+"_"+periodPrefix(periods[p])
									+"_"+disaggPrefix(results[p][d], rps)+"_source_map";
							plotFutures.add(plotDisaggMap(resourcesDir, disaggPrefix, disaggMapMaker, site, maxDist,
									disaggContribCPT, results[p][d], erf));
							table.addColumn("![Disagg Source Map]("+resourcesDir.getName()+"/"+disaggPrefix+".png)");
						}
						table.finalizeLine();

						int maxDisaggSources = 0;
						for (DisaggResult result : results[p])
							maxDisaggSources = Integer.max(maxDisaggSources,
									result.consolidatedSourceInfo == null ? 0 : result.consolidatedSourceInfo.size());
						
						int myNumSources = Integer.min(numDisaggSources, maxDisaggSources);
						
						if (myNumSources > 1) {
							// now add sources
							table.initNewLine();
							table.addColumn(MarkdownUtils.boldCentered("Top Contributing Participating Sources"));
							for (int i=0; i<numDisagg-1; i++)
								table.addColumn("");
							table.finalizeLine();
							
							for (int i=0; i<numDisaggSources; i++) {
								table.initNewLine();
								for (int d=0; d<numDisagg; d++) {
									List<DisaggregationSourceRuptureInfo> sources = results[p][d].consolidatedSourceInfo;
									if (sources == null || sources.size() < i) {
										table.addColumn("");
										continue;
									}
									DisaggregationSourceRuptureInfo source = sources.get(i);
									table.addColumn(source.getName()+" ("+pDF.format(source.getRate()/results[p][d].totRate)+")"); 
								}
								table.finalizeLine();
							}
						}
					}
				}

				lines.addAll(table.build());
				lines.add("");
			}
		}
		
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 2));
		lines.add(tocIndex, "## Table Of Contents");
		
		System.out.println("Waiting on "+plotFutures.size()+" plot futures");
		for (CompletableFuture<Void> future : plotFutures)
			future.join();
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		System.out.println("DONE");
		System.exit(0);
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
			DiscretizedFunc[] curves = hazardTasks.getSiteResults(site).toArray(new DiscretizedFunc[0]);
			Preconditions.checkState(curves.length == periods.length);
		
			ret.add(curves);
		}
		
		return ret;
	}
	
	/**
	 * This distributes tasks to each thread in a round robin fashion so that each thread starts with it's own site
	 * (if possible). Threads will then be given additional curves for that same site before moving on to new sites.
	 * This should speed up calculations (avoid site distance cache misses). 
	 */
	private static abstract class AbstractSiteHazardTaskDistributor<E extends AbstractHazardCalcTask<T>, T> {

		protected List<Site> sites;
		protected double[] periods;
		private Map<Site, List<E>> siteTasks;
		private int curSiteIndex;
		private int numSitesDone = 0;
		
		public AbstractSiteHazardTaskDistributor(List<Site> sites, double[] periods) {
			this.sites = sites;
			this.periods = periods;
			siteTasks = new HashMap<>(sites.size());
			for (Site site : sites) {
				List<E> perTaskList = new ArrayList<>(periods.length);
				for (int i=0; i<periods.length; i++)
					perTaskList.add(null);
				siteTasks.put(site, perTaskList);
			}
			
			curSiteIndex = 0;
		}
		
		protected abstract E buildTask(int siteIndex, Site site, int periodIndex, double period);
		
		public synchronized E getNextTask(Site preferredSite) {
			if (numSitesDone == sites.size())
				// all done
				return null;
			if (preferredSite != null) {
				// see if we have another period for that site
				int prefSiteIndex = this.sites.indexOf(preferredSite);
				Preconditions.checkState(prefSiteIndex >= 0);
				List<E> tasks = siteTasks.get(preferredSite);
				for (int p=0; p<tasks.size(); p++) {
					if (tasks.get(p) == null) {
						// available
						E task = buildTask(prefSiteIndex, preferredSite, p, periods[p]);
						tasks.set(p, task);
						if (p == tasks.size()-1) {
							// done
							numSitesDone++;
						}
						return task;
					}
				}
			}
			// if we're here, we have no preferred site (or that site is already done)
			for (int i=0; i<sites.size(); i++) {
				int siteIndex = (curSiteIndex + i) % sites.size();
				Site site = sites.get(siteIndex);
				List<E> tasks = siteTasks.get(site);
				for (int p=0; p<tasks.size(); p++) {
					if (tasks.get(p) == null) {
						// available
						E task = buildTask(siteIndex, site, p, periods[p]);
						tasks.set(p, task);
						if (p == tasks.size()-1) {
							// done
							numSitesDone++;
						}
						// increment curSiteIndex
						curSiteIndex = (siteIndex+1) % sites.size();
						return task;
					}
				}
			}
			throw new IllegalStateException(numSitesDone+"/"+sites.size()+" sites are done, but didn't find any tasks?");
		}
		
		public List<T> getSiteResults(Site site) {
			List<E> tasks = this.siteTasks.get(site);
			List<T> ret = new ArrayList<>(tasks.size());
			for (E task : tasks)
				ret.add(task.getResult());
			return ret;
		}
	}
	
	private static class SiteHazardTaskDistributor extends AbstractSiteHazardTaskDistributor<HazardCalcTask, DiscretizedFunc> {

		private DiscretizedFunc[] periodXVals;
		
		public SiteHazardTaskDistributor(List<Site> sites, double[] periods, DiscretizedFunc[] periodXVals) {
			super(sites, periods);
			this.periodXVals = periodXVals;
		}

		@Override
		protected HazardCalcTask buildTask(int siteIndex, Site site, int periodIndex, double period) {
			return new HazardCalcTask(site, period, periodXVals[periodIndex]);
		}
	}
	
	private abstract static class AbstractHazardCalcTask<E> {
		final Site site;
		final double period;
		E result;
		
		public AbstractHazardCalcTask(Site site, double period) {
			super();
			this.site = site;
			this.period = period;
		}
		
		public void setResult(E result) {
			this.result = result;
		}
		
		public E getResult() {
			Preconditions.checkState(isCalculated(), "Not yet calculated for site %s period %s", site.getName(), period);
			return result;
		}
		
		public boolean isCalculated() {
			return result != null;
		}
	}
	
	private static class HazardCalcTask extends AbstractHazardCalcTask<DiscretizedFunc> {
		
		final DiscretizedFunc xValues;

		public HazardCalcTask(Site site, double period, DiscretizedFunc xValues) {
			super(site, period);
			this.xValues = xValues;
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
				DiscretizedFunc xValCurve = task.xValues;
				double[] linearXVals = new double[xValCurve.size()];
				double[] logXVals = new double[xValCurve.size()];
				for (int i=0; i<xValCurve.size(); i++) {
					double x = xValCurve.getX(i);
					linearXVals[i] = x;
					logXVals[i] = Math.log(x);
				}
				
				LightFixedXFunc logCurve = new LightFixedXFunc(logXVals, new double[logXVals.length]);
				
				SolHazardMapCalc.setIMforPeriod(gmm, task.period);
				
				calc.getHazardCurve(logCurve, task.site, gmm, erf);
				
				LightFixedXFunc linearCurve = new LightFixedXFunc(linearXVals, logCurve.getYVals());
				task.setResult(linearCurve);
				
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

	private static final DecimalFormat pDF = new DecimalFormat("0.##%");
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
	
	private static class CustomReturnPeriod {
		public final double prob;
		public final String label;
		public final String prefix;
		
		public CustomReturnPeriod(ReturnPeriods rp, double duration) {
			if (duration == 1d) {
				prob = rp.oneYearProb;
			} else {
				prob = ReturnPeriodUtils.calcExceedanceProb(rp.oneYearProb, 1d, duration);
			}
			label = rp.label;
			prefix = rp.name();
		}
		
		public CustomReturnPeriod(double returnPeriod, double duration) {
			prob = ReturnPeriodUtils.calcExceedanceProbForReturnPeriod(returnPeriod, duration);
			label = oDF.format(returnPeriod)+" years";
			prefix = oDF.format(returnPeriod)+"yr";
		}
	}
	
	public static double curveVal(DiscretizedFunc curve, CustomReturnPeriod rp) {
		double curveLevel = rp.prob;
		// curveLevel is a probability, return the IML at that probability
		if (curveLevel > curve.getMaxY())
			return 0d;
		else if (curveLevel < curve.getMinY())
			// saturated
			return curve.getMaxX();
		else
			return curve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
	}
	
	private static CompletableFuture<Void> plotCurve(File outputDir, String prefix, double period, String siteName, double duration,
			CustomReturnPeriod[] rps, DiscretizedFunc curve, String name, DiscretizedFunc compCurve, String compName) throws IOException {
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
			anns = addRPAnnotations(funcs, chars, xRange, rps);
		} else {
			yAxisLabel = oDF.format(duration)+"-Year Probability of Exceedance";
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, periodLabel(period)+" "+periodUnits(period), yAxisLabel);
		spec.setLegendInset(compCurve != null);
		if (anns != null)
			spec.setPlotAnnotations(anns);
		
		return CompletableFuture.runAsync(new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
				
				gp.drawGraphPanel(spec, true, true, xRange, yRange);
				
				try {
					PlotUtils.writePlots(outputDir, prefix, gp, 1000, 800, true, true, false);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}
	
	public static List<XYAnnotation> addRPAnnotations(List<? super DiscretizedFunc> funcs,
			List<PlotCurveCharacterstics> chars, Range xRange, CustomReturnPeriod[] rps) {
		List<XYAnnotation> anns = new ArrayList<>();
		
		for (CustomReturnPeriod rp : rps) {
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
			func.set(xRange.getLowerBound(), rp.prob);
			func.set(xRange.getUpperBound(), rp.prob);
			
			funcs.add(0, func);
			chars.add(0, new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.DARK_GRAY));
			
			XYTextAnnotation ann = new XYTextAnnotation(" "+rp.label, xRange.getUpperBound(), rp.prob);
			ann.setTextAnchor(TextAnchor.BASELINE_RIGHT);
			ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
			anns.add(ann);
		}
		
		return anns;
	}
	
	private static class DisaggCalcTask extends AbstractHazardCalcTask<DisaggResult[]> {
		
		private DiscretizedFunc siteCurve;

		public DisaggCalcTask(Site site, double period, DiscretizedFunc siteCurve) {
			super(site, period);
			this.siteCurve = siteCurve;
		}
	}
	
	private static class DisaggResult {
		private final double iml;
		private final double prob;
		private final boolean isFromProb;
		private final DisaggregationPlotData plotData;
		private final double totRate;
		private final List<DisaggregationSourceRuptureInfo> sourceInfo;
		private final List<DisaggregationSourceRuptureInfo> consolidatedSourceInfo;
		
		public DisaggResult(double iml, double prob, boolean isFromProb, DisaggregationPlotData plotData, double totRate,
				List<DisaggregationSourceRuptureInfo> sourceInfo, List<DisaggregationSourceRuptureInfo> consolidatedSourceInfo) {
			super();
			this.iml = iml;
			this.prob = prob;
			this.isFromProb = isFromProb;
			this.plotData = plotData;
			this.totRate = totRate;
			this.sourceInfo = sourceInfo;
			this.consolidatedSourceInfo = consolidatedSourceInfo;
		}
	}
	
	private static class SiteDisaggCalcTaskDistributor extends AbstractSiteHazardTaskDistributor<DisaggCalcTask, DisaggResult[]> {

		private List<DiscretizedFunc[]> siteCurves;

		public SiteDisaggCalcTaskDistributor(List<Site> sites, double[] periods, List<DiscretizedFunc[]> siteCurves) {
			super(sites, periods);
			this.siteCurves = siteCurves;
		}

		@Override
		protected DisaggCalcTask buildTask(int siteIndex, Site site, int periodIndex, double period) {
			return new DisaggCalcTask(site, period, siteCurves.get(siteIndex)[periodIndex]);
		}
		
	}
	
	private static class DisaggCalcThread extends Thread {
		
		private DisaggregationCalculator calc;
		private ParameterList calcParams;
		private AbstractERF erf;
		private ScalarIMR gmm;
		private SiteDisaggCalcTaskDistributor tasks;
		private double[] disaggProbs;
		private double[] disaggIMLs;

		public DisaggCalcThread(DisaggregationCalculator calc, ParameterList calcParams,
				ScalarIMR gmm, double[] disaggProbs, double[] disaggIMLs) {
			this.calc = calc;
			this.calcParams = calcParams;
			this.gmm = gmm;
			this.disaggProbs = disaggProbs;
			this.disaggIMLs = disaggIMLs;
		}
		
		public void init(AbstractERF erf, SiteDisaggCalcTaskDistributor tasks) {
			this.erf = erf;
			this.tasks = tasks;
		}

		@Override
		public void run() {
			DisaggCalcTask task = tasks.getNextTask(null);
			
			int numDisagg = 0;
			if (disaggProbs != null)
				numDisagg += disaggProbs.length;
			if (disaggIMLs != null)
				numDisagg += disaggIMLs.length;
			
			while (task != null) {
				SolHazardMapCalc.setIMforPeriod(gmm, task.period);
				
				DisaggResult[] results = new DisaggResult[numDisagg];
				for (int i=0; i<numDisagg; i++) {
					double prob, iml;
					boolean isFromProb;
					if (disaggProbs == null) {
						prob = Double.NaN;
						iml = disaggIMLs[i];
						isFromProb = false;
					} else if (i < disaggProbs.length) {
						prob = disaggProbs[i];
						iml = task.siteCurve.getFirstInterpolatedX_inLogXLogYDomain(prob);
//						System.out.println("Converted P="+(float)prob+" to iml="+(float)iml+" for site "
//								+task.site.getName()+" and p="+(float)task.period);
//						if (task.site.getName().equals("Los Angeles") && task.period == 0d)
//							System.out.println("Los Angeles PGA curve:\n"+task.siteCurve);
						isFromProb = true;
					} else {
						prob = Double.NaN;
						iml = disaggIMLs[i - disaggProbs.length];
						isFromProb = false;
					}
					
					calc.disaggregate(Math.log(iml), task.site, gmm, erf, calcParams);
					results[i] = new DisaggResult(iml, prob, isFromProb, calc.getDisaggPlotData(), calc.getTotalRate(),
							calc.getDisaggregationSourceList(), calc.getConsolidatedDisaggregationSourceList());
				}
				
				task.setResult(results);
				
				System.out.println("done disaggregating at "+numDisagg+" IMLs for "+task.site.name+", period="+(float)task.period);
				
				task = tasks.getNextTask(task.site);
			}
		}
		
	}
	
	private static List<DisaggResult[][]> calcDisagg(List<DisaggCalcThread> calcThreads, List<Site> sites,
			FaultSystemSolutionERF erf, double[] periods, List<DiscretizedFunc[]> siteCurves) {
		SiteDisaggCalcTaskDistributor disaggTasks = new SiteDisaggCalcTaskDistributor(sites, periods, siteCurves);
		
		System.out.println("Disaggregating for "+sites.size()+" sites and "+periods.length
				+" periods with "+calcThreads.size()+" threads");
		
		for (DisaggCalcThread thread : calcThreads) {
			AbstractERF threadERF;
			if (calcThreads.size() > 1 && sites.size() > 1)
				// give each thread their own distance cache to avoid cache contention
				threadERF = new DistCachedERFWrapper(erf);
			else
				threadERF = erf;
			thread.init(threadERF, disaggTasks);
			
			thread.start();
		}
		
		// join them
		for (DisaggCalcThread thread : calcThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		List<DisaggResult[][]> ret = new ArrayList<>(sites.size());
		for (Site site : sites) {
			DisaggResult[][] results = disaggTasks.getSiteResults(site).toArray(new DisaggResult[0][]);
			Preconditions.checkState(results.length == periods.length);
		
			ret.add(results);
		}
		
		return ret;
	}
	
	private static String disaggPrefix(DisaggResult result, CustomReturnPeriod[] rps) {
		if (result.isFromProb) {
			for (CustomReturnPeriod rp : rps)
				if ((float)rp.prob == (float)result.prob)
					return rp.prefix;
			return "prob_"+(float)result.prob;
		}
		return "iml_"+(float)result.iml;
	}
	
	private static String disaggLabel(DisaggResult result, double period, CustomReturnPeriod[] rps) {
		if (result.isFromProb) {
			for (CustomReturnPeriod rp : rps)
				if ((float)rp.prob == (float)result.prob)
					return rp.label+", "+(float)result.iml+" "+periodUnits(period);
		}
		return "P="+(float)result.prob+", "+(float)result.iml+" "+periodUnits(period);
	}
	
	private static void writeDisaggCSVs(File outputDir, String commonPrefix, List<Site> sites, double[] periods,
			List<DisaggResult[][]> results, CustomReturnPeriod[] rps) throws IOException {
		for (int s=0; s<sites.size(); s++) {
			Site site = sites.get(s);
			DisaggResult[][] siteResults = results.get(s);
			
			String sitePrefix = commonPrefix;
			if (sites.size() > 1) {
				if (!sitePrefix.isBlank())
					sitePrefix += "_";
				sitePrefix += site.getName().replaceAll("\\W+", "_");
				while (sitePrefix.contains("__"))
					sitePrefix = sitePrefix.replace("__", "_");
			}
			
			for (int p=0; p<periods.length; p++) {
				DisaggResult[] perResults = siteResults[p];
				
				for (int i=0; i<perResults.length; i++) {
					String prefix = sitePrefix+"_"+periodPrefix(periods[p])+"_"+disaggPrefix(perResults[i], rps);
					
					CSVFile<String> csv = new CSVFile<>(true);
					List<String> header = new ArrayList<>();
					header.add("Distance (km)");
					header.add("Magnitude");
					for (EpsilonCategories cat : EpsilonCategories.values())
						header.add(cat.label);
					header.add("Sum");
					csv.addLine(header);
					
					DisaggregationPlotData data = perResults[i].plotData;
					
					int numE = EpsilonCategories.values().length;
					
					double[][][] pdf = data.getPdf3D();
					for (int d=0; d<data.getDist_center().length; d++) {
						for (int m=0; m<data.getMag_center().length; m++) {
							List<String> line = new ArrayList<>(header.size());
							line.add((float)data.getDist_center()[d]+"");
							line.add((float)data.getMag_center()[m]+"");
							for (int e=0; e<numE; e++)
								line.add((float)pdf[d][m][e]+"");
							line.add((float)StatUtils.sum(pdf[d][m])+"");
							csv.addLine(line);
						}
					}
					
					csv.writeToFile(new File(outputDir, prefix+".csv"));
					
					double totalRate = perResults[i].totRate;
					List<DisaggregationSourceRuptureInfo> sources = perResults[i].consolidatedSourceInfo;
					
					csv = new CSVFile<>(true);
					header = new ArrayList<>();
					header.add("Source Name");
					header.add("Contribution (%)");
					csv.addLine(header);
					
					for (DisaggregationSourceRuptureInfo source : sources) {
						List<String> line = new ArrayList<>(header.size());
						line.add(source.getName());
						line.add((float)(100d*source.getRate()/totalRate)+"");
						csv.addLine(line);
					}
					
					csv.writeToFile(new File(outputDir, prefix+"_sources.csv"));
				}
			}
		}
	}
	
	private static CompletableFuture<Void> plotDisaggMap(File resourcesDir, String prefix,
			GeographicMapMaker mapMaker, Site site, double maxDist, CPT cpt, DisaggResult result,
			FaultSystemSolutionERF erf) throws IOException {
		double buffer = 10d;
		double latDelta = LocationUtils.location(site.getLocation(), 0d, maxDist+buffer).getLatitude() - site.getLocation().getLatitude();
		double lonDelta = LocationUtils.location(site.getLocation(), Math.PI/2, maxDist+buffer).getLongitude() - site.getLocation().getLongitude();
		mapMaker.setRegion(new Region(new Location(site.getLocation().getLatitude()+latDelta, site.getLocation().getLongitude()+lonDelta),
				new Location(site.getLocation().getLatitude()-latDelta, site.getLocation().getLongitude()-lonDelta)));
		
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
//		double cptMax = 
//		cpt = cpt.rescale(0d, Math.)
		
		double[] scalars = new double[rupSet.getNumSections()];
		for (DisaggregationSourceRuptureInfo source : result.sourceInfo) {
			int sourceID = source.getId();
			if (sourceID > 0 && sourceID < erf.getNumFaultSystemSources()) {
				int rupIndex = erf.getFltSysRupIndexForSource(sourceID);
				for (int sectID : rupSet.getSectionsIndicesForRup(rupIndex))
					scalars[sectID] += source.getRate();
			}
		}
		double cptMax = 0d;
		for (int i=0; i<scalars.length; i++) {
			if (scalars[i] == 0) {
				scalars[i] = Double.NaN;
			} else {
				double val = 100d*scalars[i]/result.totRate;
				cptMax = Math.max(cptMax, val);
				scalars[i] = val;
			}
		}
		// round cptMax
		cptMax = Math.ceil(cptMax*20d)/20d;
		cpt = cpt.rescale(0d, cptMax);
		
		mapMaker.setSkipNaNs(true);
		mapMaker.plotSectScalars(scalars, cpt, "Fault Source Participation Contribution (%)");
		mapMaker.clearScatters();
		Color siteColor = Color.BLUE.darker();
		siteColor = new Color(siteColor.getRed(), siteColor.getGreen(), siteColor.getBlue(), 180);
		mapMaker.plotScatters(List.of(site.getLocation()), siteColor);
		mapMaker.setScatterSymbol(PlotSymbol.FILLED_INV_TRIANGLE, 7f);
		
		PlotSpec spec = mapMaker.buildPlot(" ");
		Range xRange = mapMaker.getXRange();
		Range yRange = mapMaker.getYRange();
		
		double zTick;
		if ((float)cptMax >= 40f)
			zTick = 10d;
		else if ((float)cptMax >= 10f)
			zTick = 5d;
		else
			zTick = 1d;
		
		return CompletableFuture.runAsync(new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = new HeadlessGraphPanel(GeographicMapMaker.PLOT_PREFS_DEFAULT);
				
				gp.drawGraphPanel(spec, false, false, xRange, yRange);
				double maxSpan = Math.max(xRange.getLength(), yRange.getLength());
				double tick;
				if (maxSpan > 20)
					tick = 5d;
				else if (maxSpan > 8)
					tick = 2d;
				else if (maxSpan > 3)
					tick = 1d;
				else if (maxSpan > 1)
					tick = 0.5d;
				else
					tick = 0.2;
				PlotUtils.setXTick(gp, tick);
				PlotUtils.setYTick(gp, tick);
				for (Title subtitle : spec.getSubtitles())
					if (subtitle instanceof PaintScaleLegend)
						PlotUtils.setTick(((PaintScaleLegend)subtitle).getAxis(), zTick);
				
				try {
					PlotUtils.writePlots(resourcesDir, prefix, gp, 800, true, true, true, false);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}

}
