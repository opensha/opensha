package scratch.UCERF3.erf.ETAS.analysis;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class SimulationMarkdownGenerator {

	public static void main(String[] args) throws IOException {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2016_02_19-mojave_m7-10yr-full_td-subSeisSupraNucl-gridSeisCorr-scale1.14");
		File configFile = new File(simDir, "config.json");
		File inputFile = new File(simDir, "results_m5_preserve.bin");
		ETAS_Config config = ETAS_Config.readJSON(configFile);
		
		File outputDir = config.getOutputDir();
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Output dir doesn't exist and couldn't be created: %s", outputDir.getAbsolutePath());
		File plotsDir = new File(outputDir, "plots");
		Preconditions.checkState(plotsDir.exists() || plotsDir.mkdir(),
				"Plot dir doesn't exist and couldn't be created: %s", plotsDir.getAbsolutePath());
		
		List<AbstractPlot> plots = new ArrayList<>();
		
		if (config.hasTriggers())
			plots.add(new ETAS_MFD_Plot(config, "mag_num_cumulative", false, true));
		else
			plots.add(new ETAS_MFD_Plot(config, "mfd", true, true));
		
		Iterator<List<ETAS_EqkRupture>> catalogsIterator = ETAS_CatalogIO.getBinaryCatalogsIterable(inputFile, 0).iterator();
		
		boolean filterSpontaneous = false;
		for (AbstractPlot plot : plots)
			filterSpontaneous = filterSpontaneous || plot.isFilterSpontaneous();
		
		int numProcessed = 0;
		int modulus = 10;
		while (catalogsIterator.hasNext()) {
			int mod = numProcessed % modulus;
			if (mod == 0) {
				System.out.println("Processing catalog "+numProcessed);
				if (numProcessed == modulus*10)
					modulus *= 10;
			}
			List<ETAS_EqkRupture> catalog;
			try {
				catalog = catalogsIterator.next();
				numProcessed++;
			} catch (Exception e) {
				System.out.println("Error loading catalog "+numProcessed);
				e.printStackTrace();
				break;
			}
			List<ETAS_EqkRupture> triggeredOnlyCatalog = null;
			if (filterSpontaneous)
				triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
			for (AbstractPlot plot : plots)
				plot.doProcessCatalog(catalog, triggeredOnlyCatalog);
		}
		
		List<String> lines = new ArrayList<>();
		
		String simName = config.getSimulationName();
		if (simName == null || simName.isEmpty())
			simName = "ETAS Simulation";
		
		lines.add("# "+simName+" Results");
		lines.add("");
		
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		TableBuilder builder = MarkdownUtils.tableBuilder();
		builder.addLine(" ", simName);
		if (numProcessed < config.getNumSimulations())
			builder.addLine("Num Simulations", numProcessed+" (incomplete)");
		else
			builder.addLine("Num Simulations", numProcessed+"");
		builder.addLine("Start Time", df.format(new Date(config.getSimulationStartTimeMillis())));
		builder.addLine("Start Time Epoch Milliseconds", config.getSimulationStartTimeMillis()+"");
		builder.addLine("Duration", AbstractPlot.getTimeLabel(config.getDuration(), true));
		builder.addLine("Includes Spontaneous?", config.isIncludeSpontaneous()+"");
		List<ETAS_EqkRupture> triggerRups = launcher.getTriggerRuptures();
		addTriggerLines(builder, "Trigger Ruptures", triggerRups);
		List<ETAS_EqkRupture> histRups = launcher.getHistQkList();
		if (config.isTreatTriggerCatalogAsSpontaneous())
			addTriggerLines(builder, "Historical Ruptures", histRups);
		else
			addTriggerLines(builder, "Trigger Ruptures", histRups);
		lines.addAll(builder.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		for (AbstractPlot plot : plots) {
			plot.finalize(plotsDir);
			
			lines.addAll(plot.generateMarkdown(plotsDir.getName(), "##", topLink));
		}
		
		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));
		
		lines.addAll(tocIndex, tocLines);
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private static void addTriggerLines(TableBuilder builder, String name, List<ETAS_EqkRupture> triggerRups) {
		if (triggerRups == null || triggerRups.isEmpty()) {
			builder.addLine(name, "*(none)*");
		} else {
			if (triggerRups.size() > 10) {
				double firstMag = 0d;
				long firstOT = Long.MAX_VALUE;
				double lastMag = 0d;
				long lastOT = Long.MIN_VALUE;
				long biggestOT = Long.MIN_VALUE;
				double maxMag = 0d;
				for (ETAS_EqkRupture rup : triggerRups) {
					double mag = rup.getMag();
					long ot = rup.getOriginTime();
					
					if (mag > maxMag) {
						maxMag = mag;
						biggestOT = ot;
					}
					
					if (ot < firstOT) {
						firstOT = ot;
						firstMag = mag;
					}
					
					if (ot > lastOT) {
						lastOT = ot;
						lastMag = mag;
					}
				}
				builder.addLine(name, triggerRups.size()+" Trigger Ruptures");
				builder.addLine(" ", "First: M"+AbstractPlot.optionalDigitDF.format(firstMag)+" at "+df.format(new Date(firstOT)));
				builder.addLine(" ", "Last: M"+AbstractPlot.optionalDigitDF.format(lastMag)+" at "+df.format(new Date(lastOT)));
				builder.addLine(" ", "Largest: M"+AbstractPlot.optionalDigitDF.format(maxMag)+" at "+df.format(new Date(biggestOT)));
			}
		}
	}

	private static SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
}
