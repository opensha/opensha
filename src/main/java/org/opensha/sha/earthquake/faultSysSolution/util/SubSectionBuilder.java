package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;

class SubSectionBuilder {
	
	static final double DOWN_DIP_FRACT_DEFAULT = 0.5;
	static final int MIN_SUB_SECTS_PER_FAULT_DEFAULT = 2;
	
	private static Options createOptions() {
		Options ops = new Options();

		Option inputOption = new Option("if", "input-file", true,
				"Path to a file containing fault sections. This should either be a GeoJSON file, "
				+ "or a legacy OpenSHA fault sections XML file. See https://opensha.org/Geospatial-File-Formats "
				+ "for more information.");
		inputOption.setRequired(false);
		ops.addOption(inputOption);

		Option outputOption = new Option("of", "output-file", true,
				"Path to output GeoJSON file where subsections should be written.");
		outputOption.setRequired(true);
		ops.addOption(outputOption);

		Option faultModelOption = new Option("f", "fault-model", true,
				"UCERF3 Fault Model, used to fetch UCERF3 fault sections as an alternative to --input-file. "
				+ "Options: "+FaultSysTools.enumOptions(FaultModels.class));
		faultModelOption.setRequired(false);
		ops.addOption(faultModelOption);

		Option ddfOption = new Option("ddf", "down-dip-fraction", true,
				"Fault sections are divided into equal length subsections; those lengths are determined as a function of "
				+ "the down-dip width of the fault. If not supplied, the default fraction of "+(float)DOWN_DIP_FRACT_DEFAULT
				+ " will be used.");
		ddfOption.setRequired(false);
		ops.addOption(ddfOption);

		Option lenOption = new Option("fl", "fixed-length", true,
				"Fault sections are divided into equal length subsections; those lengths are usually determined as a "
				+ "function of the down-dip width of the fault (see --down-dip-fraction), but can instead be of a fixed "
				+ "length through this optino. If supplied, each subsection will be no longer than the given length "
				+ "(in kilometers).");
		lenOption.setRequired(false);
		ops.addOption(lenOption);

		Option mpfOption = new Option("mpf", "min-per-fault", true,
				"Minimum number of subsections per fault, regaurdless of fault length. Default is "
				+MIN_SUB_SECTS_PER_FAULT_DEFAULT+".");
		mpfOption.setRequired(false);
		ops.addOption(mpfOption);
		
		return ops;
	}

	public static void main(String[] args) {
		Options options = createOptions();
		
		CommandLine cmd = FaultSysTools.parseOptions(options, args, SubSectionBuilder.class);
		
		try {
			List<? extends FaultSection> sects;
			if (cmd.hasOption("input-file")) {
				Preconditions.checkArgument(!cmd.hasOption("fault-model"), "Shouldn't supply both --input-file and --fault-model");
				File inputFile = new File(cmd.getOptionValue("input-file"));
				Preconditions.checkState(inputFile.exists(), "Input file doesn't exist: %s", inputFile.getAbsolutePath());
				
				String fName = inputFile.getName().trim().toLowerCase();
				if (fName.endsWith(".xml")) {
					// old XML file
					sects = FaultModels.loadStoredFaultSections(inputFile);
				} else {
					if (!fName.endsWith(".json") && !fName.endsWith(".geojson"))
						System.err.println("Warning: expected a GeoJSON file, but input file has an unexpected "
								+ "extension: "+inputFile.getName()
								+ "\nWill attemp to parse as GeoJSON anyway. See file format details at "
								+ "https://opensha.org/Geospatial-File-Formats");
					sects = GeoJSONFaultReader.readFaultSections(inputFile);
				}
			} else {
				Preconditions.checkArgument(cmd.hasOption("fault-model"), "Must supply either --input-file or --fault-model");
				String fmStr = cmd.getOptionValue("fault-model");
				FaultModels fm = FaultModels.valueOf(fmStr);
				Preconditions.checkNotNull(fm, "Unknown fault model: %s", fmStr);
				sects = fm.getFaultSections();
			}
			System.out.println("Loaded "+sects.size()+" fault sections.");
			Preconditions.checkState(!sects.isEmpty(), "Loaded fault sections are empty");
			
			double ddwFract = cmd.hasOption("down-dip-fraction") ?
					Double.parseDouble(cmd.getOptionValue("down-dip-fraction")) : DOWN_DIP_FRACT_DEFAULT;
			boolean isFixedLen = cmd.hasOption("fixed-length");
			double fixedLen = Double.NaN;
			if (isFixedLen)
				fixedLen = Double.parseDouble(cmd.getOptionValue("fixed-length"));
			
			int minPerFault = cmd.hasOption("min-per-fault") ?
					Integer.parseInt(cmd.getOptionValue("min-per-fault")) : MIN_SUB_SECTS_PER_FAULT_DEFAULT;
			
			List<FaultSection> subSects = new ArrayList<>();
			for (FaultSection sect : sects) {
				double maxLen = isFixedLen ? fixedLen : ddwFract*sect.getOrigDownDipWidth();
				subSects.addAll(sect.getSubSectionsList(maxLen, subSects.size(), minPerFault));
			}
			System.out.println("Built "+subSects.size()+" subsections");
			
			File outputFile = new File(cmd.getOptionValue("output-file"));
			System.out.println("Writing sections to "+outputFile.getAbsolutePath());
			GeoJSONFaultReader.writeFaultSections(outputFile, subSects);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
