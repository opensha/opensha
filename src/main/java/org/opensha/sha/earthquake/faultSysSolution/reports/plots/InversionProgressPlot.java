package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.Interpolate;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class InversionProgressPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Simulated Annealing Energy";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		AnnealingProgress progress = sol.requireModule(AnnealingProgress.class);
		AnnealingProgress compProgress = null;
		if (meta.hasComparisonSol())
			compProgress = meta.comparison.sol.getModule(AnnealingProgress.class);
		
		List<String> lines = new ArrayList<>();
		
		long millis = progress.getTime(progress.size()-1);
		double secs = millis/1000d;
		double mins = secs/60d;
		double hours = mins/60d;
		long perturbs = progress.getNumPerturbations(progress.size()-1);
		long iters = progress.getIterations(progress.size()-1);
		double totalEnergy = progress.getEnergies(progress.size()-1)[0];

		int ips = (int)((double)iters/secs + 0.5);
		double ipp = (double)iters/(double)perturbs;
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		// will invert this table, so rows = columns here
		table.initNewLine();
		if (compProgress != null)
			table.addColumn("");
		table.addColumn("**Iterations**").addColumn("**Time**").addColumn("**Iterations Per Sec.**")
			.addColumn("**Perturbations**").addColumn("**Iterations Per Perturb.**").addColumn("**Total Energy**");
		table.finalizeLine().initNewLine();
		if (compProgress != null)
			table.addColumn("Primary");
		
		table.addColumn(countDF.format(iters));
		table.addColumn(ThreadedSimulatedAnnealing.timeStr(millis));
		table.addColumn(countDF.format(ips));
		table.addColumn(countDF.format(perturbs));
		table.addColumn(ipp > 100d ? countDF.format((int)(ipp+0.5)) : twoDigits.format(ipp));
		
		table.addColumn((float)totalEnergy);
		table.finalizeLine();
		
		long cperturbs = -1;
		if (compProgress != null) {
			long cmillis = compProgress.getTime(compProgress.size()-1);
			double csecs = cmillis/1000d;
			cperturbs = compProgress.getNumPerturbations(compProgress.size()-1);
			long citers = compProgress.getIterations(compProgress.size()-1);
			double ctotalEnergy = compProgress.getEnergies(compProgress.size()-1)[0];

			int cips = (int)((double)citers/csecs + 0.5);
			int cipp = (int)((double)citers/(double)cperturbs + 0.5);
			
			table.initNewLine().addColumn("Comparison");
			table.addColumn(countDF.format(citers));
			table.addColumn(ThreadedSimulatedAnnealing.timeStr(cmillis));
			table.addColumn(countDF.format(cips));
			table.addColumn(countDF.format(cperturbs));
			table.addColumn(ipp > 100d ? countDF.format((int)(cipp+0.5)) : twoDigits.format(cipp));
			table.addColumn((float)ctotalEnergy);
			table.finalizeLine();
		}
		
		lines.addAll(table.invert().build());
		lines.add("");
		
		lines.add(getSubHeading()+" Final Energies");
		lines.add(topLink); lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		long deltaEachMillis;
		if (hours > 20)
			deltaEachMillis = 1000l*60l*60l*5l; // 5 hours
		else if (hours > 9)
			deltaEachMillis = 1000l*60l*60l*2l; // 2 hours
		else if (hours > 3)
			deltaEachMillis = 1000l*60l*60l*1l; // 1 hour
		else if (hours > 1)
			deltaEachMillis = 1000l*60l*30l; // 30 mins
		else if (mins > 30)
			deltaEachMillis = 1000l*60l*15l; // 15 mins
		else if (mins > 10)
			deltaEachMillis = 1000l*60l*5l; // 5 mins
		else
			deltaEachMillis = 1000l*60l*5l; // 1 min
		table.initNewLine().addColumn("Energy Type").addColumn("Final Energy ("
			+ThreadedSimulatedAnnealing.timeStr(progress.getTime(progress.size()-1))+")").addColumn("% of Total");
		List<Long> progressTimes = new ArrayList<>();
		List<Integer> progressIndexAfters = new ArrayList<>();
		int curIndex = 0;
		long maxTimeToInclude = (long)(millis*0.95d);
		for (long t=deltaEachMillis; t<maxTimeToInclude; t+=deltaEachMillis) {
			if (t < progress.getTime(0))
				continue;
			progressTimes.add(t);
			String str = "";
			if (t == deltaEachMillis)
				str = "After ";
			str += ThreadedSimulatedAnnealing.timeStr(t);
//			System.out.println(str+" at "+t);
			table.addColumn("_"+str+"_");
			while (curIndex < progress.size()) {
				long time = progress.getTime(curIndex);
				if (time >= t)
					break;
				curIndex++;
			}
			progressIndexAfters.add(curIndex);
		}
		table.finalizeLine();
		
		double[] finalEnergies = progress.getEnergies(progress.size()-1);
		List<String> types = progress.getEnergyTypes();
		for (int t=0; t<types.size(); t++) {
			table.initNewLine();
			table.addColumn("**"+types.get(t)+"**");
			if (t == 0)
				table.addColumn("**"+(float)finalEnergies[t]+"**").addColumn("");
			else
				table.addColumn((float)finalEnergies[t]).addColumn(percentDF.format(finalEnergies[t]/finalEnergies[0]));
			for (int i=0; i<progressTimes.size(); i++) {
				long time = progressTimes.get(i);
				int i1 = progressIndexAfters.get(i);
				double val;
				if (i1 == 0) {
					val = progress.getEnergies(i1)[t];
				} else if (i1 >= progress.size()) {
					val = progress.getEnergies(progress.size()-1)[t];
				} else {
					// interpolate
					int i0 = i1-1;
					double x1 = progress.getTime(i0);
					double x2 = progress.getTime(i1);
					double y1 = progress.getEnergies(i0)[t];
					double y2 = progress.getEnergies(i1)[t];
					val = Interpolate.findY(x1, y1, x2, y2, time);
				}
				String str = (float)val+"";
				if (i1 == 0 || i1 >= progress.size())
					str += "*";
				table.addColumn("_"+str+"_");
			}
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		// now plots
		String prefix = "sa_progress";
		SimulatedAnnealing.writeProgressPlots(progress, resourcesDir, prefix, sol.getRupSet().getNumRuptures(), compProgress);
		
		lines.add("");
		lines.add(getSubHeading()+" Energy Progress");
		lines.add(topLink); lines.add("");
		
		lines.add("![Energy vs Time]("+relPathToResources+"/"+prefix+"_energy_vs_time.png)");
		lines.add("");
		
		lines.add("![Energy vs Iterations]("+relPathToResources+"/"+prefix+"_energy_vs_iters.png)");
		lines.add("");
		
		lines.add("![Perturbations]("+relPathToResources+"/"+prefix+"_perturb_vs_iters.png)");
		
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(AnnealingProgress.class);
	}

}
