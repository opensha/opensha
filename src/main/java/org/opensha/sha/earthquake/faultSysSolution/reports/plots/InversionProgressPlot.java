package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

import scratch.UCERF3.simulatedAnnealing.SimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.AnnealingProgress;

public class InversionProgressPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Simulated Annealing Energy";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		AnnealingProgress progress = sol.requireModule(AnnealingProgress.class);
		
		List<String> lines = new ArrayList<>();
		
		long millis = progress.getTime(progress.size()-1);
		double secs = millis/1000d;
		double mins = secs/60d;
		double hours = mins/60d;
		long perturbs = progress.getNumPerturbations(progress.size()-1);
		long iters = progress.getIterations(progress.size()-1);
		double totalEnergy = progress.getEnergies(progress.size()-1)[0];

		lines.add("* Iterations: "+countDF.format(iters));
		if (hours > 1.1)
			lines.add("* Time: "+optionalDigitDF.format(hours)+" hours");
		else if (mins > 0.999)
			lines.add("* Time: "+optionalDigitDF.format(mins)+" minutes");
		else
			lines.add("* Time: "+optionalDigitDF.format(secs)+" seconds");
		lines.add("* Perturbations: "+countDF.format(perturbs));
		lines.add("* Total energy: "+(float)totalEnergy);
		
		lines.add(getSubHeading()+" Final Energies");
		lines.add(topLink); lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Energy Type", "Final Energy");
		double[] finalEnergies = progress.getEnergies(progress.size()-1);
		List<String> types = progress.getEnergyTypes();
		for (int t=0; t<types.size(); t++)
			table.addLine("**"+types.get(t)+"**", (float)finalEnergies[t]);
		lines.addAll(table.build());
		
		// now plots
		String prefix = "sa_progress";
		SimulatedAnnealing.writeProgressPlots(progress, resourcesDir, prefix, sol.getRupSet().getNumRuptures());
		
		lines.add("");
		lines.add(getSubHeading()+" Energy Progress");
		lines.add(topLink); lines.add("");
		
		lines.add("![Energy vs Time]("+relPathToResources+"/"+prefix+"_energy_vs_time.png)");
		lines.add("");
		
		lines.add("![Energy vs Iterations]("+relPathToResources+"/"+prefix+"_energy_vs_iters.png)");
		lines.add("");
		
		lines.add("![Perturbations]("+relPathToResources+"/"+prefix+"_perturb_vs_iters.png)");
		
		lines.add("");
		lines.add(getSubHeading()+" Rate Distribution");
		lines.add(topLink); lines.add("");
		
		double[] rates = sol.getRateForAllRups();
		double[] ratesNoMin;
		if (sol.hasModule(WaterLevelRates.class))
			ratesNoMin = sol.getModule(WaterLevelRates.class).subtractFrom(rates);
		else
			ratesNoMin = rates;
		double[] initial = sol.hasModule(InitialSolution.class) ?
				sol.getModule(InitialSolution.class).get() : new double[rates.length];
		
		int numNonZero = 0;
		int numAboveWaterlevel = 0;
		for (int r=0; r<rates.length; r++) {
			if (rates[r] > 0) {
				numNonZero++;
				if (ratesNoMin[r] > 0)
					numAboveWaterlevel++;
			}
		}
		lines.add("* Non-zero ruptures: "+countDF.format(numNonZero)
			+" ("+percentDF.format((double)numNonZero/(double)rates.length)+")");
		if (ratesNoMin != rates)
			lines.add("* Ruptures above water-level: "+countDF.format(numAboveWaterlevel)
				+" ("+percentDF.format((double)numAboveWaterlevel/(double)rates.length)+")");
		lines.add("* Avg. # perturbations per rupture: "+(float)(perturbs/(double)rates.length));
		lines.add("* Avg. # perturbations per perturbed rupture: "+(float)(perturbs/(double)numAboveWaterlevel));
		
		SimulatedAnnealing.writeRateVsRankPlot(resourcesDir, prefix+"_rate_dist", ratesNoMin, rates, initial);
		lines.add("![Perturbations]("+relPathToResources+"/"+prefix+"_rate_dist.png)");
		
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(AnnealingProgress.class);
	}

}
