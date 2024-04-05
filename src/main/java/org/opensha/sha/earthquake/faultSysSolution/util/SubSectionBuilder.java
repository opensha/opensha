package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;

public class SubSectionBuilder {
	
	static final double DOWN_DIP_FRACT_DEFAULT = 0.5;
	static final double MAX_LEN_DEFAULT = Double.NaN;
	static final int MIN_SUB_SECTS_PER_FAULT_DEFAULT = 2;

	/**
	 * Builds a subsection list with default subsection lengths. These defaults could change over time, so it is best
	 * practice for finished models to use the fully specified method, {@link #buildSubSects(List, int, double, double)}
	 * instead.
	 * @param sects sections to be broken down into subsections
	 * @return subsection list with IDs corresponding to subsection index
	 */
	public static List<FaultSection> buildSubSects(List<? extends FaultSection> sects) {
		return SubSectionBuilder.buildSubSects(sects, MIN_SUB_SECTS_PER_FAULT_DEFAULT, DOWN_DIP_FRACT_DEFAULT, MAX_LEN_DEFAULT);
	}

	/**
	 * Builds a subsection list with default subsection lengths. One one of {@code ddwFract} and {@code fixedLen} should
	 * be supplied.
	 * 
	 * @param sects sections to be broken down into subsections
	 * @param minPerFault the minimum number of subsections per fault, regardless of length
	 * @param ddwFract length specified as a fraction of down dip width; if >0, subsections will be as close to
	 * {@code ddwFract * ddw} as possible without exceeding it.
	 * @param fixedLen fixed subsection length; if >0, subsections will be as close to {@code fixedLen} as possible
	 * without exceeding it.
	 * @return subsection list with IDs corresponding to subsection index
	 */
	public static List<FaultSection> buildSubSects(List<? extends FaultSection> sects, int minPerFault,
			double ddwFract, double fixedLen) {
		Preconditions.checkState(ddwFract > 0 || fixedLen > 0, "Must give either ddwFract or fixedLen >0");
		Preconditions.checkState(!(ddwFract > 0) || !(fixedLen > 0), "Can't supply both ddwFract and fixedLen");
		sects = new ArrayList<>(sects);
		Collections.sort(sects, new Comparator<FaultSection>() {
	
			@Override
			public int compare(FaultSection o1, FaultSection o2) {
				return o1.getSectionName().compareTo(o2.getSectionName());
			}
		});
		List<FaultSection> subSects = new ArrayList<>();
		for (FaultSection sect : sects) {
			double maxLen = fixedLen > 0d ? fixedLen : ddwFract*sect.getOrigDownDipWidth();
			subSects.addAll(sect.getSubSectionsList(maxLen, subSects.size(), minPerFault));
		}
		System.out.println("Built "+subSects.size()+" subsections");
		return subSects;
	}
	
	private enum Models {
		UCERF3("ucerf3", "Flag to enable UCERF3 fault and deformation models via --fault-model and --deformation-model.") {
			@Override
			public RupSetFaultModel getDefaultFM() {
				return FaultModels.FM3_1;
			}

			@Override
			public RupSetFaultModel getFM(String name) {
				return FaultModels.valueOf(name);
			}

			@Override
			public String fmOptionsStr() {
				return FaultSysTools.enumOptions(FaultModels.class);
			}

			@Override
			public RupSetDeformationModel getDM(String name) {
				return DeformationModels.valueOf(name);
			}

			@Override
			public String dmOptionsStr() {
				return FaultSysTools.enumOptions(DeformationModels.class);
			}
		},
		NSHM23("nshm23", "Flag to enable NSHM23 fault and deformation models via --fault-model and --deformation-model,"
				+ " as well as the --apply-std-dev-defaults option to apply NSHM23 uncertainty limits and defaults to "
				+ "externally supplied fault sections.") {
			@Override
			public RupSetFaultModel getDefaultFM() {
				return NSHM23_FaultModels.WUS_FM_v2;
			}

			@Override
			public RupSetFaultModel getFM(String name) {
				return NSHM23_FaultModels.valueOf(name);
			}

			@Override
			public String fmOptionsStr() {
				return FaultSysTools.enumOptions(NSHM23_FaultModels.class);
			}

			@Override
			public RupSetDeformationModel getDM(String name) {
				return NSHM23_DeformationModels.valueOf(name);
			}

			@Override
			public String dmOptionsStr() {
				return FaultSysTools.enumOptions(NSHM23_DeformationModels.class);
			}

			@Override
			public void addExtraOptions(Options ops) {
				ops.addOption(null, "apply-std-dev-defaults", false, "NSHM23 specific option to apply default "
						+ "NSHM23 slip rate standard deviation treatment.");
			}

			@Override
			public void processOptionsSubSects(CommandLine cmd, List<? extends FaultSection> subSects) {
				if (cmd.hasOption("apply-std-dev-defaults"))
					NSHM23_DeformationModels.applyStdDevDefaults(subSects);
			}
		};
		
		private String argName;
		private String argDescription;
		private Models(String argName, String argDescription) {
			this.argName = argName;
			this.argDescription = argDescription;
		}
		
		public abstract RupSetFaultModel getDefaultFM();
		public abstract RupSetFaultModel getFM(String name);
		public abstract String fmOptionsStr();
		public abstract RupSetDeformationModel getDM(String name);
		public abstract String dmOptionsStr();
		
		public void addExtraOptions(Options ops) {
			// do nothing (can be overridden)
		}
		public void processOptionsFullSects(CommandLine cmd, List<? extends FaultSection> sects) {
			// do nothing (can be overridden)
		}
		public void processOptionsSubSects(CommandLine cmd, List<? extends FaultSection> subSects) {
			// do nothing (can be overridden)
		}
	}
	
	private static Options createOptions(Models model) {
		Options ops = new Options();
		
		ops.addOption(FaultSysTools.helpOption());

		ops.addOption("if", "input-file", true,
				"Path to a file containing fault sections. This should either be a GeoJSON file, "
				+ "or a legacy OpenSHA fault sections XML file. See https://opensha.org/Geospatial-File-Formats "
				+ "for more information.");

		ops.addRequiredOption("of", "output-file", true,
				"Path to output GeoJSON file where subsections should be written.");
		
		for (Models m : Models.values())
			ops.addOption(null, m.argName, false, m.argDescription);
		
		if (model != null)
			model.addExtraOptions(ops);
		
		String fmDesc = "Fault Model, used to fetch fault sections as an alternative to --input-file. Must supply a "
				+ "model flag, e.g. --ucerf3 or --nshm23.";
		String dmDesc = "Deformation Model, used to in conjunction with --fault-model to override properties including "
				+ "slip rates (and often rakes) in accordance with the chosen deformation model.";
		if (model == null) {
			fmDesc += " To see available options, supply a model flag and --help to print this message with options listed.";
			dmDesc += " To see available options, supply a model flag and --help to print this message with options listed.";
		} else {
			fmDesc += " Options: "+model.fmOptionsStr();
			dmDesc += " Options: "+model.dmOptionsStr();
		}
		
		ops.addOption("f", "fault-model", true, fmDesc);
		ops.addOption("dm", "deformation-model", true, dmDesc);

		ops.addOption("ddf", "down-dip-fraction", true,
				"Fault sections are divided into equal length subsections; those lengths are determined as a function of "
				+ "the down-dip width of the fault. If not supplied, the default fraction of "+(float)DOWN_DIP_FRACT_DEFAULT
				+ " will be used.");
		ops.addOption("fl", "fixed-length", true,
				"Fault sections are divided into equal length subsections; those lengths are usually determined as a "
				+ "function of the down-dip width of the fault (see --down-dip-fraction), but can instead be of a fixed "
				+ "length through this option. If supplied, each subsection will be no longer than the given length "
				+ "(in kilometers).");
		ops.addOption("mpf", "min-per-fault", true,
				"Minimum number of subsections per fault, regaurdless of fault length. Default is "
				+MIN_SUB_SECTS_PER_FAULT_DEFAULT+".");
		
		return ops;
	}

	public static void main(String[] args) {
		Models model = null;
		// see if a model is supplied, and if so create model-specific options
		for (String arg : args) {
			arg = arg.trim();
			while (arg.startsWith("-"))
				arg = arg.substring(1);
			for (Models testModel : Models.values()) {
				if (arg.equals(testModel.argName)) {
					Preconditions.checkArgument(model == null,
							"Can't specify multiple models (both %s and %s specified)", model, testModel);
					model = testModel;
					break;
				}
			}
		}
		Options options = createOptions(model);
		
		CommandLine cmd = FaultSysTools.parseOptions(options, args, SubSectionBuilder.class);
		FaultSysTools.checkPrintHelp(options, cmd, SubSectionBuilder.class);
		
		RupSetFaultModel fm = null;
		RupSetDeformationModel dm = null;
		
		try {
			double ddwFract, fixedLen;
			if (cmd.hasOption("down-dip-fraction") || cmd.hasOption("fixed-length")) {
				Preconditions.checkArgument(!cmd.hasOption("down-dip-fraction") || !cmd.hasOption("fixed-length"),
						"Can't supply both --down-dip-fraction and --fixed-length");
				if (cmd.hasOption("down-dip-fraction")) {
					ddwFract = Double.parseDouble(cmd.getOptionValue("down-dip-fraction"));
					fixedLen = Double.NaN;
				} else {
					ddwFract = Double.NaN;
					fixedLen = Double.parseDouble(cmd.getOptionValue("fixed-length"));
				}
			} else {
				ddwFract = DOWN_DIP_FRACT_DEFAULT;
				fixedLen = MAX_LEN_DEFAULT;
			}
			int minPerFault = cmd.hasOption("min-per-fault") ?
					Integer.parseInt(cmd.getOptionValue("min-per-fault")) : MIN_SUB_SECTS_PER_FAULT_DEFAULT;
			
			List<? extends FaultSection> sects;
			if (cmd.hasOption("input-file")) {
				Preconditions.checkArgument(!cmd.hasOption("fault-model"),
						"Shouldn't supply both --input-file and --fault-model");
				Preconditions.checkArgument(!cmd.hasOption("deformation-model"),
						"Shouldn't supply both --input-file and --deformation-model");
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
				
				if (cmd.hasOption("deformation-model")) {
					System.err.println("WARNING: you are applying a deformation model to an externally supplied fault "
							+ "model. Unexpected behaviour may occur if the fault model does not match what the "
							+ "deformation model expects. Pay close attention to any warning messages and validate "
							+ "the output files.");
					Preconditions.checkArgument(model != null, "Can't use --deformation-model without first choosing "
							+ "a model (e.g., with --ucerf3 or --nshm23)");
					String dmStr = cmd.getOptionValue("fault-model");
					dm = model.getDM(cmd.getOptionValue("deformation-model"));
					Preconditions.checkNotNull(dm, "Unknown deformation model: %s", dmStr);
				}
			} else {
				Preconditions.checkArgument(cmd.hasOption("fault-model"),
						"Must supply either --input-file or --fault-model");
				Preconditions.checkArgument(model != null, "Can't use --fault-model without first choosing "
						+ "a model (e.g., with --ucerf3 or --nshm23)");
				String fmStr = cmd.getOptionValue("fault-model");
				fm = model.getFM(fmStr);
				Preconditions.checkNotNull(fm, "Unknown fault model: %s", fmStr);
				sects = fm.getFaultSections();
				if (cmd.hasOption("deformation-model")) {
					String dmStr = cmd.getOptionValue("deformation-model");
					dm = model.getDM(dmStr);
					Preconditions.checkNotNull(fm, "Unknown fault model: %s", dmStr);
					if (!dm.isApplicableTo(fm))
						System.err.println("WARNING: The chosen deformation model ("+dm.getShortName()
							+") does not think it is applicable to the chosen fault model ("+fm.getShortName()+"); will try to proceed anyway.");
				}
			}
			System.out.println("Loaded "+sects.size()+" fault sections.");
			Preconditions.checkState(!sects.isEmpty(), "Loaded fault sections are empty");
			
			if (model != null)
				model.processOptionsFullSects(cmd, sects);
			
			// build subsections
			List<? extends FaultSection> subSects = buildSubSects(sects, minPerFault, ddwFract, fixedLen);
			System.out.println("Built "+subSects.size()+" subsections");
			
			if (dm != null) {
				System.out.println("Applying deformation model: "+dm);
				subSects = dm.buildForSubsects(fm, subSects);
			}
			
			if (model != null)
				model.processOptionsSubSects(cmd, subSects);
			
			File outputFile = new File(cmd.getOptionValue("output-file"));
			System.out.println("Writing sections to "+outputFile.getAbsolutePath());
			GeoJSONFaultReader.writeFaultSections(outputFile, subSects);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
