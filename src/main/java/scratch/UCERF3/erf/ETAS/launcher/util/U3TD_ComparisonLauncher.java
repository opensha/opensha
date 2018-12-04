package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.PointSurface;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.FaultSystemIO;

public class U3TD_ComparisonLauncher {
	
	private static MagDependentAperiodicityOptions COV_DEFAULT = MagDependentAperiodicityOptions.MID_VALUES;
	
	private static Options createOptions() {
		Options ops = new Options();
		
		Option fssOption = new Option("f", "fss-file", true,
				"Fault System Solution zip file");
		fssOption.setRequired(true);
		ops.addOption(fssOption);

		Option startYearOption = new Option("y", "start-year", true,
				"Start year for simulation. If TD and not supplied, current year is used.");
		startYearOption.setRequired(false);
		ops.addOption(startYearOption);
		
		Option durationOption = new Option("d", "duration", true,
				"Duration in years for simulation");
		durationOption.setRequired(true);
		ops.addOption(durationOption);
		
		Option tiOption = new Option("ti", "time-independent", false,
				"If supplied, time-independent model used. Simulations will start at 1/1/1970");
		tiOption.setRequired(false);
		ops.addOption(tiOption);
		
		Option griddedOption = new Option("g", "gridded", false,
				"If supplied, gridded ruptures will be included");
		griddedOption.setRequired(false);
		ops.addOption(griddedOption);
		
		Option aftershockFilterOption = new Option("af", "aftershock-filter", false,
				"If supplied, Gardner Knopoff aftershock filter will be applied");
		aftershockFilterOption.setRequired(false);
		ops.addOption(aftershockFilterOption);
		
		Option covOption = new Option("c", "cov", true,
				"COV option for time dependent model. One of: LOW_VALUES, MID_VALUES, HIGH_VALUES. Default: "+COV_DEFAULT.name());
		covOption.setRequired(false);
		ops.addOption(covOption);
		
		return ops;
	}

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argsStr = "--duration 10 --fss-file /home/kevin/git/ucerf3-etas-launcher/inputs/"
					+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"
//					+ " --start-year 2017"
					+ " --cov MID_VALUES"
					+ " --time-independent"
					+ " --gridded"
					+ " /tmp/td_sim";
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
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(U3TD_ComparisonLauncher.class),
					options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		if (args.length != 1) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(U3TD_ComparisonLauncher.class)
					+" [options] <output-dir>");
			System.exit(2);
		}
		
		File fssFile = new File(cmd.getOptionValue("fss-file"));
		
		boolean ti = cmd.hasOption("time-independent");
		
		System.out.println("Loading FSS file");
		FaultSystemSolution fss = null;
		try {
			fss = FaultSystemIO.loadSol(fssFile);
		} catch (IOException | DocumentException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Building ERF");
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fss);
		
		if (cmd.hasOption("aftershock-filter"))
			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, true);
		
		GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		int startYear;
		if (cmd.hasOption("start-year")) {
			startYear = Integer.parseInt(cmd.getOptionValue("start-year"));
			cal.clear();
			cal.set(startYear, 0, 1);
		} else {
			startYear = cal.get(GregorianCalendar.YEAR);
		}
		
		if (ti) {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			System.out.println("startYear: "+startYear);
		} else {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
			MagDependentAperiodicityOptions cov = COV_DEFAULT;
			if (cmd.hasOption("cov"))
				cov = MagDependentAperiodicityOptions.valueOf(cmd.getOptionValue("cov"));
			erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
			
			erf.getTimeSpan().setStartTime(startYear);
			System.out.println("startYear: "+erf.getTimeSpan().getStartTimeYear());
		}
		if (cmd.hasOption("gridded")) {
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
			erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		} else {
			erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		}
		
		if (!ti)
			erf.eraseDatesOfLastEventAfterStartTime();
		double duration = Double.parseDouble(cmd.getOptionValue("duration"));
		erf.getTimeSpan().setDuration(Math.min(duration, 10d)); // this is the duration used for calculating TD probabilities, not sim duration

		erf.updateForecast();
		
		ProbabilityModelsCalc calc = new ProbabilityModelsCalc(erf);
		
		File outputDir = new File(args[0]);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		System.out.println("Simulating...");
		try {
			calc.testER_NextXyrSimulation(outputDir, null, 1, false, null, (double)duration);
		} catch (IOException e) {
			System.out.println("Error!");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Converting output to ETAS ASCII format (with random hypocenters)");
		try {
			FaultSystemRupSet rupSet = fss.getRupSet();
			File outputFile = new File(outputDir, "sampledEventsData.txt");
			List<ETAS_EqkRupture> etasRups = new ArrayList<>();
			Random r = new Random();
			for (String line : Files.readLines(outputFile, Charset.defaultCharset())) {
				line = line.trim();
				if (line.startsWith("nthRupIndex") || line.startsWith("#"))
					continue;
				String[] split = line.split("\t");
				Preconditions.checkState(split.length > 3);
				int fssIndex = Integer.parseInt(split[1]);
				int id = etasRups.size();
				long epochMillis = Long.parseLong(split[3]);
				if (ti)
					epochMillis += cal.getTimeInMillis();
				if (fssIndex == -1) {
					// gridded
					int nthIndex = Integer.parseInt(split[0]);
					ProbEqkRupture probRup = erf.getNthRupture(nthIndex);
					Preconditions.checkState(probRup.getRuptureSurface() instanceof PointSurface);
					Location hypoLoc = ((PointSurface)probRup.getRuptureSurface()).getLocation();
					ObsEqkRupture rup = new ObsEqkRupture(id+"", epochMillis, hypoLoc, probRup.getMag());
					ETAS_EqkRupture etasRup = new ETAS_EqkRupture(rup);
					etasRup.setID(id);
					etasRup.setFSSIndex(fssIndex);
					int srcIndex = erf.getSrcIndexForNthRup(nthIndex);
					etasRup.setGridNodeIndex(srcIndex - erf.getNumFaultSystemSources());
					etasRups.add(etasRup);
				} else {
					Preconditions.checkState(fssIndex >= 0 && fssIndex < rupSet.getNumRuptures(), "bad FSS index=%s", fssIndex);
					LocationList rupLocs = rupSet.getSurfaceForRupupture(fssIndex, 1d, false).getEvenlyDiscritizedListOfLocsOnSurface();
					Location hypoLoc = rupLocs.get(r.nextInt(rupLocs.size()));
					
					ObsEqkRupture rup = new ObsEqkRupture(id+"", epochMillis, hypoLoc, rupSet.getMagForRup(fssIndex));
					ETAS_EqkRupture etasRup = new ETAS_EqkRupture(rup);
					etasRup.setID(id);
					etasRup.setFSSIndex(fssIndex);
					etasRups.add(etasRup);
				}
			}
			Files.move(outputFile, new File(outputDir, "sampledEventsData_orig.txt"));
			System.out.println("Writing new output file");
			ETAS_CatalogIO.writeEventDataToFile(outputFile, etasRups);
		} catch (IOException e) {
			System.out.println("ERROR!");
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("DONE");
		System.exit(0);
	}

}
