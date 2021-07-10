package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;

public class SolMFDPlot extends AbstractSolutionPlot {

	@Override
	public List<String> plot(FaultSystemSolution sol, FaultSystemSolution compSol, File outputDir,
			String relPathToOutput, String topLink) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		// TODO Auto-generated method stub
		return null;
	}

}
