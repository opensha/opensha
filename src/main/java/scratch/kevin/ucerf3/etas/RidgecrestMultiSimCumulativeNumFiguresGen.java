package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotMetadata;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotResult;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class RidgecrestMultiSimCumulativeNumFiguresGen {

	public static void main(String[] args) throws IOException {
		File gitDir = new File("/home/kevin/git/ucerf3-etas-results");
//		updateMagComplete(mainDir, "ci38457511", 3.5);
//		System.exit(0);
		
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/ridgecrest_plots");
		
	}
	
	static List<String> buildPlots(File gitDir, File outputDir, String relativePathToOutput, String heading) throws IOException {
		double[] minMags = { 3.5, 4d, 5d };
		
		List<String> lines = new ArrayList<>();
		
		File[] simDirs = {
				new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces"),
				new File(gitDir, "2019_07_16-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_19-ComCatM7p1_ci38457511_14DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_07_27-ComCatM7p1_ci38457511_21DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_03-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_19-ComCatM7p1_ci38457511_35DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_19-ComCatM7p1_ci38457511_42DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_24-ComCatM7p1_ci38457511_49DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_31-ComCatM7p1_ci38457511_56DaysAfter_ShakeMapSurfaces"),
		};
		String prefix = "cumulative_num_shakemap_surfs";
		
		lines.add(heading+" ShakeMap Surfaces");
		lines.add("");
		lines.addAll(writeCumNumPlots(simDirs, minMags, outputDir, relativePathToOutput, prefix));
		
		lines.add("");
		simDirs = new File[] {
				new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_PointSources-noSpont-full_td-scale1.14"),
				new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_28DaysAfter_PointSources-noSpont-full_td-scale1.14")
			};
		prefix = "cumulative_num_point_sources";
		
		lines.add(heading+" Point Sources");
		lines.add("");
		lines.addAll(writeCumNumPlots(simDirs, minMags, outputDir, relativePathToOutput, prefix));
		
		lines.add("");
		simDirs = new File[] {
				new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_ShakeMapSurfaces_NoFaults-noSpont-poisson-griddedOnly"),
				new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces_NoFaults-noSpont-poisson-griddedOnly")
			};
		prefix = "cumulative_num_shakemap_surfs_no_faults";
		
		lines.add(heading+" ShakeMap Surfaces, No Faults");
		lines.add("");
		lines.addAll(writeCumNumPlots(simDirs, minMags, outputDir, relativePathToOutput, prefix));
		
		lines.add("");
		simDirs = new File[] {
				new File(gitDir, "2019_08_30-ComCatM7p1_ci38457511_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002"),
				new File(gitDir, "2019_09_02-ComCatM7p1_ci38457511_7DaysAfter_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002"),
				new File(gitDir, "2019_08_31-ComCatM7p1_ci38457511_28DaysAfter_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002")
			};
		prefix = "cumulative_num_seq_specific";
		
		lines.add(heading+" ShakeMap Surfaces, Sequence Specific Params");
		lines.add("");
		lines.addAll(writeCumNumPlots(simDirs, minMags, outputDir, relativePathToOutput, prefix));
		
		return lines;
	}
	
	static List<String> writeCumNumPlots(File[] simDirs, double[] minMags, File outputDir, String relativePathToOutput,
			String prefix) throws IOException {
		List<ETAS_Config> configs = new ArrayList<>(); 
		Map<ETAS_Config, DataCompare[]> compares = new HashMap<>();
		long minPlotTime = Long.MAX_VALUE;
		
		for (File simDir : simDirs) {
			File configFile = new File(simDir, "config.json");
			Preconditions.checkState(configFile.exists());
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			configs.add(config);
			
			File plotDir = new File(simDir, "plots");
			File metadataFile = new File(plotDir, "metadata.json");
			Preconditions.checkState(metadataFile.exists());
			PlotMetadata meta = SimulationMarkdownGenerator.readPlotMetadata(metadataFile);
			for (PlotResult plot : meta.plots) {
				if (plot.className.contains("ETAS_ComcatComparePlot")) {
					Date date = new Date(plot.time);
					System.out.println(simDir.getName()+" was updated on "+date);
					minPlotTime = Long.min(minPlotTime, plot.time);
				}
			}
			
			DataCompare[] comps = new DataCompare[minMags.length];
			for (int i=0; i<minMags.length; i++) {
				double minMag = minMags[i];
				String csvName = "comcat_compare_cumulative_num_m"+optionalDigitDF.format(minMag)+".csv";
				File csv = new File(plotDir, csvName);
				if (csv.exists())
					comps[i] = new DataCompare(CSVFile.readFile(csv, true));
				else
					System.out.println("\tNo CSV file for M>="+(float)minMag);
			}
			compares.put(config, comps);
		}
		Date earliestDate = new Date(minPlotTime);
		System.out.println("Earliest plot date: "+earliestDate);
		
		// sort configs by start time, increasing
		configs.sort(new Comparator<ETAS_Config>() {

			@Override
			public int compare(ETAS_Config o1, ETAS_Config o2) {
				return Long.compare(o1.getSimulationStartTimeMillis(), o2.getSimulationStartTimeMillis());
			}
		});
		
		boolean logX = false;
		boolean logY = false;
		
		String[][] prefixes = new String[minMags.length][2];
		
		for (int m=0; m<minMags.length; m++) {
			List<ETAS_Config> magConfigs = new ArrayList<>();
			List<DataCompare> dataComps = new ArrayList<>();
			
			for (ETAS_Config config : configs) {
				DataCompare[] comps = compares.get(config);
				if (comps[m] != null) {
					magConfigs.add(config);
					dataComps.add(comps[m]);
				}
			}
			
			System.out.println(magConfigs.size()+"/"+configs.size()+" simulations have Mc="+(float)minMags[m]);
			if (magConfigs.isEmpty())
				continue;
			
			ArbitrarilyDiscretizedFunc dataFunc = dataComps.get(0).comcatFunc;
			dataFunc.setName("ComCat Data");
			
			long firstTime = magConfigs.get(0).getSimulationStartTimeMillis();
			
			List<Double> startDays = new ArrayList<>();
			List<Double> dataAtStart = new ArrayList<>();
			
			for (ETAS_Config config : magConfigs) {
				long myTime = config.getSimulationStartTimeMillis();
				Preconditions.checkState(myTime >= firstTime);
				double myStartDays = (double)(myTime - firstTime)/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
				if (myStartDays >= dataFunc.getMaxX()) {
					System.out.println("Skipping simulation (starts after data func maxX): "+config.getSimulationName());
					startDays.add(null);
					dataAtStart.add(null);
					continue;
				}
				double valueAtStart = myStartDays >= dataFunc.getMaxX() ? Double.NaN : dataFunc.getInterpolatedY(myStartDays);
				if ((float)myStartDays == 0f && valueAtStart > 0) {
					System.err.println("WARNING: zero data bin not zero, pretending it is for first offset");
					valueAtStart = 0d;
				}
				startDays.add(myStartDays);
				dataAtStart.add(valueAtStart);
			}
			
			for (boolean median : new boolean[] { false, true }) {
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				funcs.add(dataFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.RED));
				
				String simValName;
				if (median)
					simValName = "Median";
				else
					simValName = "Mean";
				
				for (int i=0; i<dataComps.size(); i++) {
					DataCompare comp = dataComps.get(i);
					Double xAdd = startDays.get(i);
					if (xAdd == null)
						continue;
					double yAdd = dataAtStart.get(i);
					double maxX = dataFunc.getMaxX();
					if (i < dataComps.size()-1 && startDays.get(i+1) != null)
						maxX = Math.min(maxX, startDays.get(i+1));
					if (xAdd >= maxX || Double.isNaN(yAdd))
						break;
					
					ArbitrarilyDiscretizedFunc simRawFunc;
					if (median)
						simRawFunc = comp.medianFunc;
					else
						simRawFunc = comp.meanFunc;
					
					DiscretizedFunc simFunc = getModFunc(simRawFunc, xAdd, yAdd, maxX);
					DiscretizedFunc simFullFunc = getModFunc(simRawFunc, xAdd, yAdd, dataFunc.getMaxX());
					DiscretizedFunc p02p5Func = getModFunc(comp.p02p5Func, xAdd, yAdd, maxX);
					DiscretizedFunc p97p5Func = getModFunc(comp.p97p5Func, xAdd, yAdd, maxX);
					
					if (i == 0) {
//						simFunc.setName("Simulated "+simValName+" (until next update)");
//						simFullFunc.setName("Full Simulated "+simValName);
						simFunc.setName("Simulated "+simValName);
						p02p5Func.setName("2.5,97.5 %-iles");
					}
					funcs.add(simFunc);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
					
					funcs.add(0, simFullFunc);
					chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
					
					funcs.add(p02p5Func);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
					
					funcs.add(p97p5Func);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
				}
				
				// add transparent data func on top
				ArbitrarilyDiscretizedFunc dataFuncClone = dataFunc.deepClone();
				dataFuncClone.setName(null);
				funcs.add(dataFuncClone);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, new Color(255, 0, 0, 127)));
				
				HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
				
				double mc = minMags[m];
				String title = "Cumulative Number Comparison, Mc="+optionalDigitDF.format(mc)+", "+simValName;
				String xAxisLabel = "Time (days)";
				String yAxisLabel = "Cumulative Num Earthquakes M≥"+optionalDigitDF.format(mc);
				PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
				spec.setLegendVisible(true);
				
				double maxY = 0d;
				for (XY_DataSet xy : funcs)
					maxY = Math.max(maxY, xy.getMaxY());
				
				gp.setLegendFontSize(18);
				Range xRange, yRange;
				if (logX)
					xRange = new Range(dataFunc.getX(1), dataFunc.getMaxX());
				else
					xRange = new Range(0, dataFunc.getMaxX());
				if (logY)
					yRange = new Range(1, maxY);
				else
					yRange = new Range(0, maxY);

				gp.drawGraphPanel(spec, logX, logY, xRange, yRange);
				gp.getChartPanel().setSize(800, 600);
				String myPrefix = prefix+"_m"+optionalDigitDF.format(mc)+"_"+simValName.toLowerCase();
				gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
				gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
				if (median)
					prefixes[m][1] = myPrefix+".png";
				else
					prefixes[m][0] = myPrefix+".png";
			}
		}
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("Min Mag");
		for (double minMag : minMags)
			table.addColumn("M&ge;"+optionalDigitDF.format(minMag));
		table.finalizeLine();
		for (int i=0; i<2; i++) {
			table.initNewLine();
			if (i == 1)
				table.addColumn("**Median**");
			else
				table.addColumn("**Mean**");
			for (int m=0; m<minMags.length; m++)
				table.addColumn("![Plot]("+relativePathToOutput+"/"+prefixes[m][i]+")");
			table.finalizeLine();
		}
		
		return table.build();
	}
	
	private static DiscretizedFunc getModFunc(DiscretizedFunc rawFunc, double addX, double addY, double maxX) {
		DiscretizedFunc ret = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : rawFunc) {
			double x = addX + pt.getX();
			if (x > maxX)
				break;
			double y = addY + pt.getY();
			ret.set(x, y);
		}
		return ret;
	}
	
	protected static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	private static void updateMagComplete(File mainDir, String eventID, Double magComplete) throws IOException {
		for (File simDir : mainDir.listFiles()) {
			if (!simDir.getName().contains(eventID))
				continue;
			File configFile = new File(simDir, "config.json");
			ETAS_Config.updateComcatMagComplete(configFile, 3.5);
		}
	}
	
	private static class DataCompare {
		
		final ArbitrarilyDiscretizedFunc comcatFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc medianFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc modeFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc minFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc maxFunc = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc p02p5Func = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc p16Func = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc p84Func = new ArbitrarilyDiscretizedFunc();
		final ArbitrarilyDiscretizedFunc p97p5Func = new ArbitrarilyDiscretizedFunc();
		
		public DataCompare(CSVFile<String> csv) {
			Preconditions.checkState(csv.getNumCols() == 11);
			
			boolean years = csv.get(0, 0).toLowerCase().contains("years");
			
			for (int row=1; row<csv.getNumRows(); row++) {
				int col = 0;
				
				double x = csv.getDouble(row, col++);
				if (years)
					x *= 365.25;
				comcatFunc.set(x, csv.getDouble(row, col++));
				meanFunc.set(x, csv.getDouble(row, col++));
				medianFunc.set(x, csv.getDouble(row, col++));
				modeFunc.set(x, csv.getDouble(row, col++));
				minFunc.set(x, csv.getDouble(row, col++));
				maxFunc.set(x, csv.getDouble(row, col++));
				p02p5Func.set(x, csv.getDouble(row, col++));
				p16Func.set(x, csv.getDouble(row, col++));
				p84Func.set(x, csv.getDouble(row, col++));
				p97p5Func.set(x, csv.getDouble(row, col++));
			}
		}
	}

}
