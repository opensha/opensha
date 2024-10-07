package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class GeneralInfoPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "General Information";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		InfoModule info = null;
		if (sol != null)
			info = sol.getModule(InfoModule.class);
		else
			info = rupSet.getModule(InfoModule.class);
		
		String inputName = null;
		if (sol != null && sol.getArchive().getInput() != null)
			inputName = sol.getArchive().getInput().getName();
		else
			inputName = rupSet.getArchive().getInput().getName();
		
		BuildInfoModule buildInfo = null;
		if (sol != null)
			buildInfo = sol.getModule(BuildInfoModule.class);
		else
			buildInfo = rupSet.getModule(BuildInfoModule.class);
		
		if (info == null && buildInfo == null && inputName == null)
			return null;
		
		List<String> lines = new ArrayList<>();
		
		String solOrRS = sol == null ? "Rupture Set" : "Solution";
		
		if (inputName != null && !inputName.isBlank()) {
			File file = null;
			try {
				file = new File(inputName);
				if (!file.exists())
					file = null;
			} catch (Exception e) {}
			if (file == null)
				lines.add(solOrRS+" Input: `"+inputName+"`");
			else
				lines.add(solOrRS+" File Path: `"+file.getAbsolutePath()+"`");
			lines.add("");
		}
		
		if (info != null && info.getText() != null && !info.getText().isBlank()) {
			if (buildInfo != null) {
				lines.add(getSubHeading()+" Attached Metadata");
				lines.add(topLink); lines.add("");
			}
			
			lines.add("```");
			lines.add(info.getText());
			lines.add("```");
		}
		
		if (buildInfo!= null) {
			if (info != null) {
				lines.add(getSubHeading()+" OpenSHA Build Info");
				lines.add(topLink); lines.add("");
			}
			
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			table.addLine("_Description_", "_Value_");
			if (buildInfo.getCreationTime() != null)
				table.addLine(solOrRS+" Creation Time", df.format(new Date(buildInfo.getCreationTime())));
			if (buildInfo.getBuildTime() != null)
				table.addLine(solOrRS+" OpenSHA Build Time", df.format(new Date(buildInfo.getBuildTime())));
			if (buildInfo.getBranch() != null)
				table.addLine(solOrRS+" OpenSHA Git Branch", "`"+buildInfo.getBranch()+"`");
			if (buildInfo.getGitHash() != null)
				table.addLine(solOrRS+" OpenSHA Git Hash", "`"+buildInfo.getGitHash()+"`");
			
			BuildInfoModule current = null;
			try {
				current = BuildInfoModule.detect();
			} catch (IOException e) {
				System.err.println("Couldn't get current build info: "+e.getMessage());
			}
			if (current != null) {
				if (current.getCreationTime() != null)
					table.addLine("Report Creation Time", df.format(new Date(current.getCreationTime())));
				if (current.getBuildTime() != null)
					table.addLine("Report OpenSHA Build Time", df.format(new Date(current.getBuildTime())));
				if (current.getBranch() != null)
					table.addLine("Report OpenSHA Git Branch", "`"+current.getBranch()+"`");
				if (current.getGitHash() != null)
					table.addLine("Report OpenSHA Git Hash", "`"+current.getGitHash()+"`");
			}
//			if (buildInfo.getOpenshaVersion() != null)
//				table.addLine(OpenSHA Application Version, buildInfo.getOpenshaVersion());
			
			lines.addAll(table.build());
		}
		
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
