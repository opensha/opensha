package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ClassUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.utils.FaultSystemIO;

public class ETAS_SectionSearch {
	
	private static final double RADIUS_DEFAULT = 50;
	
	private static Options createOptions() {
		Options ops = new Options();

		Option latOption = new Option("lat", "latitude", true,
				"Latitude of location around which to search for UCERF3 subsections");
		latOption.setRequired(false);
		ops.addOption(latOption);

		Option lonOption = new Option("lon", "longitude", true,
				"Longitue of location around which to search for UCERF3 subsections");
		lonOption.setRequired(false);
		ops.addOption(lonOption);

		Option radiusOption = new Option("r", "radius", true,
				"Radius around location in which to search for UCERF3 subsections in km. Default: "+(float)RADIUS_DEFAULT+" km");
		radiusOption.setRequired(false);
		ops.addOption(radiusOption);

		Option nameOption = new Option("n", "name", true,
				"Only return subsections which contain this string (ignoring case) in their name.");
		nameOption.setRequired(false);
		ops.addOption(nameOption);
		
		return ops;
	}

	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argsStr = "--latitude 34 --longitude -118 --radius 20 /home/kevin/git/ucerf3-etas-launcher/inputs/"
					+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip";
			args = argsStr.split(" ");
		}
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ETAS_SectionSearch.class),
					options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		if (args.length != 1) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_SectionSearch.class)
					+" [options] <fss_file.zip>");
			System.exit(2);
		}
		
		File fssFile = new File(args[0]);
		Preconditions.checkArgument(fssFile.exists(), "FSS file doesn't exist: %s", fssFile.getAbsolutePath());
		
		System.out.println("Loading fault system solution from "+fssFile.getAbsolutePath());
		FaultSystemSolution fss = FaultSystemIO.loadSol(fssFile);
		
		Region searchReg = null;
		Location centerLoc = null;
		if (cmd.hasOption("latitude")) {
			Preconditions.checkArgument(cmd.hasOption("longitude"), "supplied latitude but not longitude");
			double lat = Double.parseDouble(cmd.getOptionValue("latitude"));
			double lon = Double.parseDouble(cmd.getOptionValue("longitude"));
			centerLoc = new Location(lat, lon);
			System.out.println("Using search circular region around "+(float)lat+", "+(float)lon);
			double radius;
			if (cmd.hasOption("radius"))
				radius = Double.parseDouble(cmd.getOptionValue("radius"));
			else
				radius = RADIUS_DEFAULT;
			System.out.println("Search radius: "+(float)radius+" km");
			searchReg = new Region(centerLoc, radius);
		}
		
		String name = null;
		if (cmd.hasOption("name"))
			name = cmd.getOptionValue("name").trim();
		
		List<? extends FaultSection> sects = fss.getRupSet().getFaultSectionDataList();
		for (FaultSection sect : sects) {
			if (searchReg != null) {
				boolean match = false;
				for (Location loc : sect.getFaultTrace()) {
					if (searchReg.contains(loc)) {
						match = true;
						break;
					}
				}
				if (!match)
					continue;
			}
			if (name != null && !sect.getName().toLowerCase().contains(name.toLowerCase()))
				continue;
			
			System.out.println(sect.getSectionId()+": "+sect.getName());
			if (searchReg != null) {
				double dist = sect.getFaultSurface(0.1, false, true).getDistanceJB(centerLoc);
				System.out.println("\thorizontal distance to search location: "+(float)dist);
			}
			System.out.println("\tupper depth: "+(float)sect.getReducedAveUpperDepth());
			System.out.println("\tlower depth: "+(float)sect.getAveLowerDepth());
			System.out.println("\tstrike: "+(float)sect.getFaultTrace().getAveStrike());
			System.out.println("\tdip: "+(float)sect.getAveDip());
			System.out.println("\trake: "+(float)sect.getAveRake());
			System.out.println("\ttrace:");
			for (Location loc : sect.getFaultTrace())
				System.out.println("\t\t"+loc);
		}
	}

}