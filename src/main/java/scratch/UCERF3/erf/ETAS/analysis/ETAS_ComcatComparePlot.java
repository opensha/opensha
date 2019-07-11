package scratch.UCERF3.erf.ETAS.analysis;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_ComcatComparePlot extends ETAS_AbstractPlot {
	
	private Region mapRegion;
	private GriddedRegion gridRegion;

	protected ETAS_ComcatComparePlot(ETAS_Config config, ETAS_Launcher launcher) {
		super(config, launcher);
		
//		region = ETAS_EventMapPlotUtils.
	}

	@Override
	public boolean isFilterSpontaneous() {
		return true;
	}

	@Override
	protected void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog, List<ETAS_EqkRupture> triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		Preconditions.checkNotNull(triggeredOnlyCatalog);
	}

	@Override
	public void finalize(File outputDir, FaultSystemSolution fss) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
