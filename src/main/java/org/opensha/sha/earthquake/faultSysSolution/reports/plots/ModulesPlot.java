package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class ModulesPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Attached Modules";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		
		if (sol == null)
			return modulesTable(rupSet).build();
		
		// have both
		List<String> lines = new ArrayList<>();
		lines.add(getSubHeading()+" Rupture Set Modules");
		lines.add(topLink); lines.add("");
		
		lines.addAll(modulesTable(rupSet).build());
		lines.add("");
		
		lines.add(getSubHeading()+" Solution Modules");
		lines.add(topLink); lines.add("");
		
		lines.addAll(modulesTable(sol).build());
		return lines;
	}
	
	private static TableBuilder modulesTable(ModuleContainer<?> container) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.addLine("Name", "Implementing Class");
		
		for (OpenSHA_Module module : container.getModules())
			table.addLine(module.getName(), "_"+module.getClass().getName()+"_");
		
		return table;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
