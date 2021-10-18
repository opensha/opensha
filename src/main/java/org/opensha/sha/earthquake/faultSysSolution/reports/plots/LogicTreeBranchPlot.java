package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class LogicTreeBranchPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Logic Tree Branch";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		LogicTreeBranch<?> branch = rupSet.requireModule(LogicTreeBranch.class);
		
		LogicTreeBranch<?> compBranch = meta.comparison == null ?
				null : meta.comparison.rupSet.getModule(LogicTreeBranch.class);
		
		if (compBranch == null)
			table.addLine("Level", "Choice", "Abbreviation");
		else
			table.addLine("Level", "Choice", "Abbrev.", "Comparison Choice", "Comparison Abbrev.");
		
		List<String> levelNames = new ArrayList<>();
		Map<String, LogicTreeNode> choices = new HashMap<>();
		Map<String, LogicTreeNode> compChoices = compBranch == null ? null : new HashMap<>();
		
		for (int i=0; i<branch.size(); i++) {
			LogicTreeLevel<?> level = branch.getLevel(i);
			String name = level.getName();
			levelNames.add(name);
			choices.put(name, branch.getValue(i));
		}
		
		if (compBranch != null) {
			for (int i=0; i<compBranch.size(); i++) {
				LogicTreeLevel<?> level = compBranch.getLevel(i);
				String name = level.getName();
				if (!levelNames.contains(name))
					levelNames.add(name);
				compChoices.put(name, compBranch.getValue(i));
			}
		}
		
		for (String name : levelNames) {
			table.initNewLine().addColumn("**"+name+"**");
			LogicTreeNode choice = choices.get(name);
			if (choice == null)
				table.addColumn(na).addColumn(na);
			else
				table.addColumn(choice.getName()).addColumn(choice.getShortName());
			if (compBranch != null) {
				LogicTreeNode compChoice = compChoices.get(name);
				if (compChoice == null)
					table.addColumn(na).addColumn(na);
				else
					table.addColumn(compChoice.getName()).addColumn(compChoice.getShortName());
			}
			table.finalizeLine();
		}
		
		return table.build();
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(LogicTreeBranch.class);
	}

}
