package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.utils.FaultSystemIO;

public class ETAS_RuptureSearch {
	
	private static final double RADIUS_DEFAULT = 50;
	
	private static Options createOptions() {
		Options ops = new Options();

		Option latOption = new Option("lat", "latitude", true,
				"Latitude of location around which to search for UCERF3 subsections");
		latOption.setRequired(true);
		ops.addOption(latOption);

		Option lonOption = new Option("lon", "longitude", true,
				"Longitue of location around which to search for UCERF3 subsections");
		lonOption.setRequired(true);
		ops.addOption(lonOption);

		Option radiusOption = new Option("r", "radius", true,
				"Radius around location in which to search for UCERF3 subsections in km. Default: "+(float)RADIUS_DEFAULT+" km");
		radiusOption.setRequired(true);
		ops.addOption(radiusOption);

		Option minMagOption = new Option("min", "min-mag", true,
				"Minimum magnitude for the search");
		minMagOption.setRequired(true);
		ops.addOption(minMagOption);

		Option maxMagOption = new Option("max", "max-mag", true,
				"Maximum magnitude for the search");
		maxMagOption.setRequired(true);
		ops.addOption(maxMagOption);
		
		return ops;
	}

	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argsStr = "--latitude 34 --longitude -118 --radius 20 --min-mag 7 --max-mag 7.3 /home/kevin/git/ucerf3-etas-launcher/inputs/"
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
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ETAS_RuptureSearch.class),
					options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		if (args.length != 1) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_RuptureSearch.class)
					+" [options] <fss_file.zip>");
			System.exit(2);
		}
		
		File fssFile = new File(args[0]);
		Preconditions.checkArgument(fssFile.exists(), "FSS file doesn't exist: %s", fssFile.getAbsolutePath());
		
		System.out.println("Loading fault system solution from "+fssFile.getAbsolutePath());
		FaultSystemSolution fss = FaultSystemIO.loadSol(fssFile);
		
		double lat = Double.parseDouble(cmd.getOptionValue("latitude"));
		double lon = Double.parseDouble(cmd.getOptionValue("longitude"));
		Location centerLoc = new Location(lat, lon);
		System.out.println("Using search circular region around "+(float)lat+", "+(float)lon);
		double radius;
		if (cmd.hasOption("radius"))
			radius = Double.parseDouble(cmd.getOptionValue("radius"));
		else
			radius = RADIUS_DEFAULT;
		System.out.println("Search radius: "+(float)radius+" km");
		Region searchReg = new Region(centerLoc, radius);
		
		double minMag = Double.parseDouble(cmd.getOptionValue("min-mag"));
		double maxMag = Double.parseDouble(cmd.getOptionValue("max-mag"));
		
		FaultSystemRupSet rupSet = fss.getRupSet();
		List<FaultSectionPrefData> sects = rupSet.getFaultSectionDataList();
		HashSet<Integer> rupsForSects = new HashSet<>();
		for (FaultSectionPrefData sect : sects) {
			boolean match = false;
			for (Location loc : sect.getFaultTrace()) {
				if (searchReg.contains(loc)) {
					match = true;
					break;
				}
			}
			if (match)
				rupsForSects.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
		}
		
		List<Integer> rups = new ArrayList<>(rupsForSects);
		Collections.sort(rups);
		
		for (int r : rups) {
			double mag = rupSet.getMagForRup(r);
			if (mag < minMag || mag > maxMag)
				continue;
			
			RuptureSurface surf = rupSet.getSurfaceForRupupture(r, 1d, false);
			
			System.out.println("Rupture "+r+", M="+(float)mag);
			double dist = surf.getDistanceJB(centerLoc);
			System.out.println("\thorizontal distance to search location: "+(float)dist);
			System.out.println("\tSubsections:");
			for (int s : rupSet.getSectionsIndicesForRup(r)) {
				FaultSectionPrefData sect = rupSet.getFaultSectionData(s);
				System.out.println("\t\t"+sect.getSectionId()+". "+sect.getSectionName());
			}
			System.out.println("\tupper depth: "+(float)surf.getAveRupTopDepth());
			System.out.println("\twidth: "+(float)surf.getAveWidth());
			System.out.println("\tstrike: "+(float)surf.getAveStrike());
			System.out.println("\tdip: "+(float)surf.getAveDip());
			System.out.println("\trake: "+(float)rupSet.getAveRakeForRup(r));
			System.out.println("\tFirst location: "+surf.getFirstLocOnUpperEdge());
			System.out.println("\tLast location: "+surf.getLastLocOnUpperEdge());
//			for (Location loc : surf.getUpperEdge())
//				System.out.println("\t\t"+loc);
		}
	}

}