package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class ModulesPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Attached Modules";
	}
	
	private static final String common_package_prefix = AveSlipModule.class.getPackageName();

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add("List of all modules that have been attached to this "+(sol == null ? "Rupture Set." : "Solution."));
		lines.add("");
		lines.add("_Note: Modules classes in the standard modules package, `"+common_package_prefix+"`, have been "
				+ "shortened to omit the package name._");
		lines.add("");
		
		if (sol == null) {
			lines.addAll(modulesTable(rupSet).build());
			return lines;
		}
		
		// have both
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
		
		for (OpenSHA_Module module : container.getModules()) {
			String className = module.getClass().getName();
			if (className.startsWith(common_package_prefix))
				className = className.substring(common_package_prefix.length()+1);
			table.addLine("**"+module.getName()+"**", "`"+className+"`");
		}
		
		return table;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
