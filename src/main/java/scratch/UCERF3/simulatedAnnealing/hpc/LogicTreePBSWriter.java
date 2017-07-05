package scratch.UCERF3.simulatedAnnealing.hpc;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.EpicenterScriptWriter;
import org.opensha.commons.hpc.pbs.RangerScriptWriter;
import org.opensha.commons.hpc.pbs.StampedeScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.commons.util.ClassUtils;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionConfiguration;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.CommandLineInversionRunner.InversionOptions;
import scratch.UCERF3.logicTree.DiscreteListTreeTrimmer;
import scratch.UCERF3.logicTree.ListBasedTreeTrimmer;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchIterator;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.logicTree.LogicalAndTrimmer;
import scratch.UCERF3.logicTree.LogicalNotTreeTrimmer;
import scratch.UCERF3.logicTree.LogicalOrTrimmer;
import scratch.UCERF3.logicTree.SingleValsTreeTrimmer;
import scratch.UCERF3.logicTree.TreeTrimmer;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.VariableSubTimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class LogicTreePBSWriter {

	public static DateFormat df = new SimpleDateFormat("yyyy_MM_dd");

	public static ArrayList<File> getClasspath(RunSites runSite, File jobDir) {
		return getClasspath(runSite.RUN_DIR, jobDir);
	}
	
	public static ArrayList<File> getClasspath(File jarDir, File jobDir) {
		ArrayList<File> jars = new ArrayList<File>();
		jars.add(new File(jobDir, "OpenSHA_complete.jar"));
		jars.add(new File(jarDir, "parallelcolt-0.9.4.jar"));
		jars.add(new File(jarDir, "commons-cli-1.2.jar"));
		jars.add(new File(jarDir, "csparsej.jar"));
		return jars;
	}

	public enum RunSites {
		EPICENTER("/home/epicenter/kmilner/inversions", EpicenterScriptWriter.JAVA_BIN,
//				"/home/scec-02/kmilner/ucerf3/inversions/fm_store") {
				null, null, false) {
			@Override
			public BatchScriptWriter forBranch(LogicTreeBranch branch) {
				InversionModels im = branch.getValue(InversionModels.class);
				Preconditions.checkState(im != InversionModels.GR_CONSTRAINED,
						"are you kidding me? we can't run GR on epicenter!");
				return new EpicenterScriptWriter();
			}

			@Override
			public int getMaxHeapSizeMB(LogicTreeBranch branch) {
				return 7000;
			}

			@Override
			public int getPPN(LogicTreeBranch branch) {
				return 8;
			}
		},
		HPCC("/home/scec-02/kmilner/ucerf3/inversions", USC_HPCC_ScriptWriter.JAVA_BIN,
//				"/home/scec-02/kmilner/ucerf3/inversions/fm_store") {
//				null, USC_HPCC_ScriptWriter.FMPJ_HOME, true) {
				null, USC_HPCC_ScriptWriter.MPJ_HOME, false) {
			@Override
			public BatchScriptWriter forBranch(LogicTreeBranch branch) {
				if (branch != null && branch.getValue(InversionModels.class) == InversionModels.GR_CONSTRAINED)
					return new USC_HPCC_ScriptWriter("dodecacore");
//				return new USC_HPCC_ScriptWriter("quadcore"); // TODO
				return new USC_HPCC_ScriptWriter();
			}

			@Override
			public int getMaxHeapSizeMB(LogicTreeBranch branch) {
//				if (branch != null &&
//						branch.getValue(InversionModels.class) == InversionModels.GR_CONSTRAINED)
//					return 40000;
//				return 8000;
				return 60000; // new scec nodes
			}

			@Override
			public int getPPN(LogicTreeBranch branch) {
//				if (branch != null &&
//						branch.getValue(InversionModels.class) == InversionModels.GR_CONSTRAINED)
//					return 24;
//				return 8;
				return 20;
			}
		},
		RANGER("/work/00950/kevinm/ucerf3/inversion", RangerScriptWriter.JAVA_BIN,
				null, RangerScriptWriter.FMPJ_HOME, true) {
			@Override
			public BatchScriptWriter forBranch(LogicTreeBranch branch) {
				return new RangerScriptWriter();
			}

			@Override
			public int getMaxHeapSizeMB(LogicTreeBranch branch) {
				return 20000;
			}

			@Override
			public int getPPN(LogicTreeBranch branch) {
				return 16;
			}
		},
		STAMPEDE("/work/00950/kevinm/ucerf3/inversion", StampedeScriptWriter.JAVA_BIN,
				null, StampedeScriptWriter.FMPJ_HOME, true) {
			@Override
			public BatchScriptWriter forBranch(LogicTreeBranch branch) {
				return new StampedeScriptWriter();
			}

			@Override
			public int getMaxHeapSizeMB(LogicTreeBranch branch) {
				return 25000;
			}

			@Override
			public int getPPN(LogicTreeBranch branch) {
				return 16;
			}
		};

		private File RUN_DIR;
		private File JAVA_BIN;
		private String FM_STORE;
		private File MPJ_HOME;
		private boolean fastMPJ;

		private RunSites(String path, File javaBin, String fmStore, File mpjHome, boolean fastMPJ) {
			RUN_DIR = new File(path);
			JAVA_BIN = javaBin;
			FM_STORE = fmStore;
			MPJ_HOME = mpjHome;
			this.fastMPJ = fastMPJ;
		}

		public abstract BatchScriptWriter forBranch(LogicTreeBranch branch);
		public abstract int getMaxHeapSizeMB(LogicTreeBranch branch);
		public int getInitialHeapSizeMB(LogicTreeBranch branch) {
			return getMaxHeapSizeMB(branch);
		}
		public abstract int getPPN(LogicTreeBranch branch);

		public File getRUN_DIR() {
			return RUN_DIR;
		}

		public File getJAVA_BIN() {
			return JAVA_BIN;
		}

		public String getFM_STORE() {
			return FM_STORE;
		}

		public File getMPJ_HOME() {
			return MPJ_HOME;
		}

		public boolean isFastMPJ() {
			return fastMPJ;
		}
	}

	private static ArrayList<CustomArg[]> buildVariationBranches(List<CustomArg[]> variations, CustomArg[] curVariation) {
		if (curVariation == null)
			curVariation = new CustomArg[variations.size()];
		int ind = curVariation.length - variations.size();
		List<CustomArg[]> nextVars;
		if (variations.size() > 1)
			nextVars = variations.subList(1, variations.size());
		else
			nextVars = null;
		ArrayList<CustomArg[]> retVal = new ArrayList<CustomArg[]>();

		for (CustomArg var : variations.get(0)) {
			CustomArg[] branch = Arrays.copyOf(curVariation, curVariation.length);
			branch[ind] = var;
			if (nextVars == null)
				retVal.add(branch);
			else
				retVal.addAll(buildVariationBranches(nextVars, branch));
		}

		return retVal;
	}

	private static class CustomArg {
		private InversionOptions op;
		private String arg;

		public CustomArg(InversionOptions op, String arg) {
			this.op = op;
			if (op.hasOption())
				Preconditions.checkState(arg != null && !arg.isEmpty());
			else
				Preconditions.checkState(arg == null || arg.isEmpty());
			this.arg = arg;
		}
	}
	
	private static class InversionArg {
		
		private String arg;
		private String prefix;
		
		public InversionArg(String arg, String prefix) {
			this.arg = arg;
			this.prefix = prefix;
		}
		
	}

	private static CustomArg[] forOptions(InversionOptions op, String... args) {
		CustomArg[] ops = new CustomArg[args.length];
		for (int i=0; i<args.length; i++)
			ops[i] = new CustomArg(op, args[i]);
		return ops;
	}

	private static <E> E[] toArray(E... vals) {
		return vals;
	}

	public static List<LogicTreeBranchNode<?>> getNonZeroChoices(Class<? extends LogicTreeBranchNode<?>> clazz, InversionModels im) {
		List<LogicTreeBranchNode<?>> nonZeros = Lists.newArrayList();
		for (LogicTreeBranchNode<?> val : clazz.getEnumConstants())
			if (val.getRelativeWeight(im) > 0)
				nonZeros.add(val);
		return nonZeros;
	}

	private static List<LogicTreeBranchNode<?>> allOf(Class<? extends LogicTreeBranchNode<?>> clazz) {
		LogicTreeBranchNode<?>[] vals = clazz.getEnumConstants();

		return Arrays.asList(vals);
	}

	private static List<LogicTreeBranchNode<?>> toList(LogicTreeBranchNode<?>... vals) {
		return Arrays.asList(vals);
	}

	private static CustomArg[] buildVariationBranch(InversionOptions[] ops, String[] vals) {
		Preconditions.checkArgument(ops.length == vals.length);
		CustomArg[] args = new CustomArg[ops.length];
		for (int i=0; i<args.length; i++) {
			if (!ops[i].hasOption()) {
				if (vals[i] != null && vals[i].equals(TAG_OPTION_ON))
					args[i] = new CustomArg(ops[i], null);
			} else {
				args[i] = new CustomArg(ops[i], vals[i]);
			}
		}
		return args;
	}

	private static final String TAG_OPTION_ON = "Option On";
	private static final String TAG_OPTION_OFF = null;

	private static class VariableLogicTreeBranch extends LogicTreeBranch {
		CustomArg[] args;
		public VariableLogicTreeBranch(CustomArg[] args, boolean setNullToDefault, LogicTreeBranchNode<?>... branchChoices) {
			this(args, LogicTreeBranch.fromValues(setNullToDefault, branchChoices));
		}

		public VariableLogicTreeBranch(CustomArg[] args, LogicTreeBranch branch) {
			super(branch);
			this.args = args;
		}
		@Override
		public int getNumAwayFrom(LogicTreeBranch branch) {
			int num = super.getNumAwayFrom(branch);

			if (!(branch instanceof VariableLogicTreeBranch))
				return num;

			VariableLogicTreeBranch variableBranch = (VariableLogicTreeBranch)branch;

			if (args != null) {
				for (int i=0; i<args.length; i++) {
					CustomArg myArg = args[i];
					if (myArg == null)
						continue;

					if (variableBranch.args == null || variableBranch.args.length <= i) {
						num++;
						break;
					}
					CustomArg theirArg = variableBranch.args[i];
					if (theirArg.op != myArg.op) {
						num++;
						break;
					}

					if (myArg.arg == null && theirArg.arg != null) {
						num++;
						break;
					}

					if (!myArg.arg.equals(theirArg.arg)) {
						num++;
						break;
					}
				}
			}

			return num;
		}
	}
	
	private static LogicTreeBranch getUCERF2_noIM() {
		LogicTreeBranch UCERF2_noIM = (LogicTreeBranch) LogicTreeBranch.UCERF2.clone();
		UCERF2_noIM.clearValue(InversionModels.class);
//		UCERF2_noIM.clearValue(MomentRateFixes.class);
		return UCERF2_noIM;
	}
	
	private static TreeTrimmer getUCERF2Trimmer() {
		return new ListBasedTreeTrimmer(getUCERF2_noIM(), false);
	}
	
	private static TreeTrimmer getAllDM_IM_Trimmer(boolean bothFMs) {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();
		
		List<LogicTreeBranchNode<?>> faultModels = Lists.newArrayList();
		faultModels.add(FaultModels.FM3_1);
		if (bothFMs)
			faultModels.add(FaultModels.FM3_2);
		limitations.add(faultModels);
		
		List<LogicTreeBranchNode<?>> defModels = getNonZeroChoices(DeformationModels.class, null);
		limitations.add(defModels);
		
		List<LogicTreeBranchNode<?>> invModels = getNonZeroChoices(InversionModels.class, null);
		limitations.add(invModels);
		
		return ListBasedTreeTrimmer.getDefaultPlusSpecifiedTrimmer(limitations);
	}
	
	private static TreeTrimmer getUCERF3RefBranches() {
		List<LogicTreeBranch> branches = Lists.newArrayList();
		
		List<LogicTreeBranchNode<?>> dms = getNonZeroChoices(DeformationModels.class, null);
		List<LogicTreeBranchNode<?>> ims = getNonZeroChoices(InversionModels.class, null);
		
		// UCERF3
		for (LogicTreeBranchNode<?> dm : dms) {
			for (LogicTreeBranchNode<?> im : ims) {
				boolean isChar = ((InversionModels)im).isCharacteristic();
				MomentRateFixes momFix;
//				if (isChar)
					momFix = MomentRateFixes.NONE;
//				else
//					momFix = MomentRateFixes.APPLY_IMPLIED_CC;
				branches.add(LogicTreeBranch.fromValues(false, FaultModels.FM3_1, dm, im,
						ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, TotalMag5Rate.RATE_7p9,
						MaxMagOffFault.MAG_7p6, momFix, SpatialSeisPDF.UCERF3));
			}
		}
		
		// UCERF2
		for (LogicTreeBranchNode<?> im : ims) {
			boolean isChar = ((InversionModels)im).isCharacteristic();
			MomentRateFixes momFix;
//			if (isChar)
				momFix = MomentRateFixes.NONE;
//			else
//				momFix = MomentRateFixes.APPLY_IMPLIED_CC;
			branches.add(LogicTreeBranch.fromValues(false, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, im,
					ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, TotalMag5Rate.RATE_7p9,
					MaxMagOffFault.MAG_7p6, momFix, SpatialSeisPDF.UCERF3));
		}
		
		return new DiscreteListTreeTrimmer(branches);
	}
	
	public static TreeTrimmer getNonZeroOrUCERF2Trimmer() {
		final TreeTrimmer nonZero = ListBasedTreeTrimmer.getNonZeroWeightsTrimmer();
		final TreeTrimmer ucerf2Trim = getUCERF2Trimmer();
		
		return new TreeTrimmer() {
			
			@Override
			public boolean isTreeValid(LogicTreeBranch branch) {
				return nonZero.isTreeValid(branch) || ucerf2Trim.isTreeValid(branch);
			}
		};
	}
	
	public static TreeTrimmer getNoUCERF2Trimmer() {
		return new TreeTrimmer() {
			
			@Override
			public boolean isTreeValid(LogicTreeBranch branch) {
				return !branch.getValue(FaultModels.class).equals(FaultModels.FM2_1);
			}
		};
	}
	
	public static TreeTrimmer getNeokinemaOnlyTrimmer() {
		return new TreeTrimmer() {
			
			@Override
			public boolean isTreeValid(LogicTreeBranch branch) {
				return branch.getValue(DeformationModels.class).equals(DeformationModels.NEOKINEMA);
			}
		};
	}
	
	public static TreeTrimmer getZengOnlyTrimmer() {
		return new SingleValsTreeTrimmer(DeformationModels.ZENGBB);
	}
	
	private static TreeTrimmer getDiscreteCustomTrimmer() {
		List<LogicTreeBranch> branches = Lists.newArrayList();
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.ELLB_SQRT_LENGTH));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.ELLSWORTH_B));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.HANKS_BAKUN_08));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.SHAW_2009_MOD));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.SHAW_CONST_STRESS_DROP));
		
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.AVE_UCERF2, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.ELLB_SQRT_LENGTH, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
		
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
		
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, DeformationModels.ZENG, ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.HANKS_BAKUN_08, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
//		branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, DeformationModels.UCERF2_ALL, ScalingRelationships.SHAW_CONST_STRESS_DROP, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2));
		
//		ScalingRelationships[] scales = ScalingRelationships.values();
//		ScalingRelationships[] scales = { ScalingRelationships.ELLSWORTH_B, ScalingRelationships.SHAW_CONST_STRESS_DROP };
//		ScalingRelationships[] scales = { ScalingRelationships.ELLSWORTH_B, ScalingRelationships.HANKS_BAKUN_08, ScalingRelationships.SHAW_CONST_STRESS_DROP };
//		ScalingRelationships[] scales = { ScalingRelationships.ELLSWORTH_B, ScalingRelationships.HANKS_BAKUN_08,
//				ScalingRelationships.SHAW_CONST_STRESS_DROP, ScalingRelationships.SHAW_2009_MOD,
//				ScalingRelationships.ELLB_SQRT_LENGTH };
//		ScalingRelationships[] scales = { ScalingRelationships.ELLSWORTH_B };
		ScalingRelationships[] scales = { ScalingRelationships.SHAW_2009_MOD };
//		ScalingRelationships[] scales = { ScalingRelationships.ELLB_SQRT_LENGTH, ScalingRelationships.SHAW_2009_MOD, ScalingRelationships.SHAW_CONST_STRESS_DROP };
//		SlipAlongRuptureModels[] dsrs = { SlipAlongRuptureModels.TAPERED, SlipAlongRuptureModels.UNIFORM };
//		SlipAlongRuptureModels[] dsrs = { SlipAlongRuptureModels.TAPERED };
		SlipAlongRuptureModels[] dsrs = { SlipAlongRuptureModels.UNIFORM };
//		DeformationModels[] dms = { DeformationModels.UCERF2_ALL, DeformationModels.GEOLOGIC, DeformationModels.ABM, DeformationModels.NEOKINEMA, DeformationModels.ZENG };
//		DeformationModels[] dms = { DeformationModels.UCERF2_ALL, DeformationModels.ZENG };
		DeformationModels[] dms = { DeformationModels.UCERF2_ALL };
//		DeformationModels[] dms = { DeformationModels.ZENG };
		
		
		
		for (ScalingRelationships scale : scales) {
			for (SlipAlongRuptureModels dsr : dsrs) {
				for (DeformationModels dm : dms) {
					if (dm == DeformationModels.UCERF2_ALL)
						branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM2_1, dm, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_7p9,
								MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2, scale, dsr));
					else
						branches.add(LogicTreeBranch.fromValues(true, FaultModels.FM3_1, dm, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_7p9,
								MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3, scale, dsr));
				}
			}
		}
		
		return new DiscreteListTreeTrimmer(branches);
	}
	
	private static TreeTrimmer getFullBranchSpan() {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();

		List<LogicTreeBranchNode<?>> faultModels = toList(FaultModels.FM3_1, FaultModels.FM3_2);
		limitations.add(faultModels);

		List<LogicTreeBranchNode<?>> defModels = getNonZeroChoices(DeformationModels.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(defModels);

		List<LogicTreeBranchNode<?>> inversionModels = toList(InversionModels.CHAR_CONSTRAINED);
		limitations.add(inversionModels);

		List<LogicTreeBranchNode<?>> scaling = getNonZeroChoices(ScalingRelationships.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(scaling);

		List<LogicTreeBranchNode<?>> slipAlongs = getNonZeroChoices(SlipAlongRuptureModels.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(slipAlongs);

		List<LogicTreeBranchNode<?>> mag5s = getNonZeroChoices(TotalMag5Rate.class, InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> mag5s = toList(TotalMag5Rate.RATE_8p7);
		limitations.add(mag5s);

		List<LogicTreeBranchNode<?>> maxMags = getNonZeroChoices(MaxMagOffFault.class, InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> maxMags = toList(MaxMagOffFault.MAG_7p6);
		limitations.add(maxMags);

		List<LogicTreeBranchNode<?>> momentFixes = toList(MomentRateFixes.NONE);
		limitations.add(momentFixes);

		List<LogicTreeBranchNode<?>> spatialSeis = getNonZeroChoices(SpatialSeisPDF.class, InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> spatialSeis = toList(SpatialSeisPDF.UCERF3);
		limitations.add(spatialSeis);
		
		int tally = 1;
		System.out.println("FULL BRANCH SPAN. Allowed:");
		for (List<LogicTreeBranchNode<?>> allowed : limitations) {
			List<String> names = Lists.newArrayList();
			for (LogicTreeBranchNode<?> a : allowed)
				names.add(a.name());
			System.out.println("\t"+ClassUtils.getClassNameWithoutPackage(allowed.get(0).getClass())
					+" ("+names.size()+"): "+Joiner.on(", ").join(names));
			tally *= names.size();
		}
		System.out.println("TOTAL TALLY: "+tally);
		
		return new ListBasedTreeTrimmer(limitations);
	}
	
	private static TreeTrimmer getMiniBranchSpan() {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();

		List<LogicTreeBranchNode<?>> faultModels = toList(FaultModels.FM3_1, FaultModels.FM3_2);
		limitations.add(faultModels);

		List<LogicTreeBranchNode<?>> defModels = getNonZeroChoices(DeformationModels.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(defModels);

		List<LogicTreeBranchNode<?>> inversionModels = toList(InversionModels.CHAR_CONSTRAINED);
		limitations.add(inversionModels);

		List<LogicTreeBranchNode<?>> scaling = getNonZeroChoices(ScalingRelationships.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(scaling);

		List<LogicTreeBranchNode<?>> slipAlongs = getNonZeroChoices(SlipAlongRuptureModels.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(slipAlongs);

//		List<LogicTreeBranchNode<?>> mag5s = getNonZeroChoices(TotalMag5Rate.class, InversionModels.CHAR_CONSTRAINED);
		List<LogicTreeBranchNode<?>> mag5s = toList(TotalMag5Rate.RATE_7p9);
		limitations.add(mag5s);

//		List<LogicTreeBranchNode<?>> maxMags = getNonZeroChoices(MaxMagOffFault.class, InversionModels.CHAR_CONSTRAINED);
		List<LogicTreeBranchNode<?>> maxMags = toList(MaxMagOffFault.MAG_7p6);
		limitations.add(maxMags);

		List<LogicTreeBranchNode<?>> momentFixes = toList(MomentRateFixes.NONE);
		limitations.add(momentFixes);

//		List<LogicTreeBranchNode<?>> spatialSeis = getNonZeroChoices(SpatialSeisPDF.class, InversionModels.CHAR_CONSTRAINED);
		List<LogicTreeBranchNode<?>> spatialSeis = toList(SpatialSeisPDF.UCERF3);
		limitations.add(spatialSeis);
		
		return new ListBasedTreeTrimmer(limitations);
	}
	
	public static TreeTrimmer getCustomTrimmer() {
		List<List<LogicTreeBranchNode<?>>> limitations = Lists.newArrayList();

		List<LogicTreeBranchNode<?>> faultModels = toList(FaultModels.FM3_1);
//		List<LogicTreeBranchNode<?>> faultModels = toList(FaultModels.FM3_1, FaultModels.FM3_2);
		limitations.add(faultModels);

		// if null, all that are applicable to each fault model will be used
//		List<LogicTreeBranchNode<?>> defModels = toList(DeformationModels.GEOLOGIC);
		List<LogicTreeBranchNode<?>> defModels = getNonZeroChoices(DeformationModels.class, InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> defModels = toList(DeformationModels.ABM);
//		List<LogicTreeBranchNode<?>> defModels = toList(DeformationModels.NEOKINEMA);
//		List<LogicTreeBranchNode<?>> defModels = toList(DeformationModels.ZENG);
//		List<LogicTreeBranchNode<?>> defModels = toList(DeformationModels.ZENGBB, DeformationModels.NEOKINEMA);
		limitations.add(defModels);

//		List<LogicTreeBranchNode<?>> inversionModels = allOf(InversionModels.class);
		List<LogicTreeBranchNode<?>> inversionModels = toList(InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> inversionModels = toList(InversionModels.GR_CONSTRAINED);
		limitations.add(inversionModels);
		//		InversionModels[] inversionModels =  { InversionModels.CHAR, InversionModels.UNCONSTRAINED };
		//		InversionModels[] inversionModels =  { InversionModels.UNCONSTRAINED };
		//		InversionModels[] inversionModels =  { InversionModels.CHAR_CONSTRAINED };
		//		InversionModels[] inversionModels =  { InversionModels.CHAR, InversionModels.GR };
//				InversionModels[] inversionModels =  { InversionModels.GR_CONSTRAINED };

//		List<LogicTreeBranchNode<?>> scaling = toList(ScalingRelationships.ELLSWORTH_B);
//		List<LogicTreeBranchNode<?>> scaling = toList(ScalingRelationships.ELLSWORTH_B, ScalingRelationships.HANKS_BAKUN_08);
//		List<LogicTreeBranchNode<?>> scaling = toList(ScalingRelationships.HANKS_BAKUN_08);
//		List<LogicTreeBranchNode<?>> scaling = toList(ScalingRelationships.SHAW_2009_MOD, ScalingRelationships.SHAW_CONST_STRESS_DROP,
//					ScalingRelationships.ELLSWORTH_B, ScalingRelationships.ELLB_SQRT_LENGTH, ScalingRelationships.HANKS_BAKUN_08);
		List<LogicTreeBranchNode<?>> scaling = getNonZeroChoices(ScalingRelationships.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(scaling);

//		List<LogicTreeBranchNode<?>> slipAlongs = getNonZeroChoices(SlipAlongRuptureModels.class);
		List<LogicTreeBranchNode<?>> slipAlongs = toList(SlipAlongRuptureModels.UNIFORM);
//		List<LogicTreeBranchNode<?>> slipAlongs = getNonZeroChoices(SlipAlongRuptureModels.class, InversionModels.CHAR_CONSTRAINED);
		limitations.add(slipAlongs);

//		List<LogicTreeBranchNode<?>> mag5s = getNonZeroChoices(TotalMag5Rate.class, InversionModels.CHAR_CONSTRAINED);
		List<LogicTreeBranchNode<?>> mag5s = toList(TotalMag5Rate.RATE_7p9);
//		List<LogicTreeBranchNode<?>> mag5s = toList(TotalMag5Rate.RATE_10p6, TotalMag5Rate.RATE_8p7);
		limitations.add(mag5s);

//		List<LogicTreeBranchNode<?>> maxMags = getNonZeroChoices(MaxMagOffFault.class, InversionModels.CHAR_CONSTRAINED);
//		List<LogicTreeBranchNode<?>> maxMags = toList(MaxMagOffFault.MAG_8p0);
		List<LogicTreeBranchNode<?>> maxMags = toList(MaxMagOffFault.MAG_7p6);
		limitations.add(maxMags);

//		List<LogicTreeBranchNode<?>> momentFixes = getNonZeroChoices(MomentRateFixes.class);
//		List<LogicTreeBranchNode<?>> momentFixes = toList(MomentRateFixes.NONE, MomentRateFixes.APPLY_IMPLIED_CC);
		List<LogicTreeBranchNode<?>> momentFixes = toList(MomentRateFixes.NONE);
//		List<LogicTreeBranchNode<?>> momentFixes = toList(MomentRateFixes.RELAX_MFD);
		limitations.add(momentFixes);

//		List<LogicTreeBranchNode<?>> spatialSeis = getNonZeroChoices(SpatialSeisPDF.class, InversionModels.CHAR_CONSTRAINED);
		List<LogicTreeBranchNode<?>> spatialSeis = toList(SpatialSeisPDF.UCERF3);
		limitations.add(spatialSeis);
		
		return new ListBasedTreeTrimmer(limitations);
	}
	
	private static HashSet<String> loadIgnoresFromZip(File zipFile) throws IOException {
		HashSet<String> set = new HashSet<String>();
		
		ZipFile zip = new ZipFile(zipFile);
		
		for (ZipEntry entry : Collections.list(zip.entries())) {
			
			if (entry.isDirectory())
				continue;
			String name = new File(entry.getName()).getName();
			if (name.contains("noMinRates"))
				continue;
			if (name.contains("."))
				name = name.substring(0, name.lastIndexOf("."));
			System.out.println("Ignoring: "+name);
			set.add(name);
		}
		
		return set;
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
//		String runName = "ucerf3p3-synthetic-tests";
//		String runName = "biasi-downsample-tests";
		String runName = "milner-downsample-tests";
		if (args.length > 1)
			runName = args[1];
//		int constrained_run_mins = 60;	// 1 hour
//		int constrained_run_mins = 120;	// 2 hours
//		int constrained_run_mins = 180;	// 3 hours
//		int constrained_run_mins = 240;	// 4 hours
		int constrained_run_mins = 300; // 5 hours
//		int constrained_run_mins = 360;	// 6 hours
//		int constrained_run_mins = 480;	// 8 hours
//		int constrained_run_mins = 60 * 10;	// 10 hours
//		int constrained_run_mins = 60 * 16;	// 16 hours
//		int constrained_run_mins = 60 * 40;	// 40 hours
//		int constrained_run_mins = 10;
		runName = df.format(new Date())+"-"+runName;
		//		runName = "2012_03_02-weekend-converg-test";

		//		RunSites site = RunSites.RANGER;
		//		RunSites site = RunSites.EPICENTER;
		RunSites site = RunSites.HPCC;
		int batchSize = 16;
		int jobsPerNode = 4;
		String threads = "5";
//		RunSites site = RunSites.HPCC;
//		int batchSize = 0;
//		int jobsPerNode = 1;
//		String threads = "95%"; // max for 8 core nodes, 23/24 for dodecacore
//		String threads = "50%";
//		RunSites site = RunSites.RANGER;
//		int batchSize = 64;
//		int jobsPerNode = 2;
//		String threads = "8"; // *2 = 16 (out of 16 possible)
//		RunSites site = RunSites.STAMPEDE;
//		int batchSize = 128;
//		int jobsPerNode = 3;
//		String threads = "5"; // *2 = 16 (out of 16 possible)
		
//		LogicTreeBranch prescribedBranch = null;
		LogicTreeBranch prescribedBranch = (LogicTreeBranch) LogicTreeBranch.DEFAULT.clone();

		//		String nameAdd = "VarSub5_0.3";
		String nameAdd = null;
		
		HashSet<String> ignores = null;
//		HashSet<String> ignores = loadIgnoresFromZip(new File("/tmp/2012_12_27-ucerf3p2_prod_runs_1_bins.zip"));
//		HashSet<String> ignores = loadIgnoresFromZip(new File("/home/kevin/OpenSHA/UCERF3/inversions/" +
//				"2012_12_27-ucerf3p2_prod_runs_1/bins/2012_12_27-ucerf3p2_prod_runs_1_keeper_bins.zip"));

		int numRuns = 200;
		int runStart = 0;
		boolean forcePlots = false;

		boolean lightweight = numRuns > 10 || batchSize > 1;
		boolean noPlots = batchSize > 1;
		
		if (forcePlots) {
			lightweight = false;
			noPlots = false;
		}
		
		File runSubDir = new File(site.RUN_DIR, runName);
		
		int overallMaxJobs = -1;

//		TreeTrimmer trimmer = getCustomTrimmer();
		TreeTrimmer trimmer = getUCERF3RefBranches();
//		TreeTrimmer trimmer = getFullBranchSpan();
//		TreeTrimmer trimmer = getMiniBranchSpan();
//		TreeTrimmer trimmer = new SingleValsTreeTrimmer(FaultModels.FM3_1, DeformationModels.GEOLOGIC,
//				ScalingRelationships.ELLB_SQRT_LENGTH, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
//		TreeTrimmer trimmer = getNonZeroOrUCERF2Trimmer();
//		TreeTrimmer trimmer = getUCERF2Trimmer();
//		TreeTrimmer trimmer = getDiscreteCustomTrimmer();
		
		TreeTrimmer charOnly = new SingleValsTreeTrimmer(InversionModels.CHAR_CONSTRAINED);
		TreeTrimmer charUnconstOnly = new SingleValsTreeTrimmer(InversionModels.CHAR_UNCONSTRAINED);
		TreeTrimmer grOnly = new SingleValsTreeTrimmer(InversionModels.GR_CONSTRAINED);
		TreeTrimmer grUnconstOnly = new SingleValsTreeTrimmer(InversionModels.GR_UNCONSTRAINED);
		TreeTrimmer charOrGR = new LogicalOrTrimmer(charOnly, grOnly);
//		TreeTrimmer neoKOnly = new SingleValsTreeTrimmer(DeformationModels.NEOKINEMA);
		TreeTrimmer noRefBranches = new LogicalNotTreeTrimmer(getUCERF3RefBranches());
		TreeTrimmer noUCERF2 = getNoUCERF2Trimmer();
//		trimmer = new LogicalAndTrimmer(trimmer, charOrGR);
//		trimmer = new LogicalAndTrimmer(trimmer, charOrGR, noUCERF2);
//		trimmer = new LogicalAndTrimmer(trimmer, charOrGR, noUCERF2);
//		trimmer = new LogicalAndTrimmer(trimmer, charUnconstOnly, noUCERF2);
//		trimmer = new LogicalAndTrimmer(trimmer, grUnconstOnly, noUCERF2);
//		trimmer = new LogicalAndTrimmer(trimmer, charOnly);
		trimmer = new LogicalAndTrimmer(trimmer, charOnly, noUCERF2, getZengOnlyTrimmer());
//		trimmer = new LogicalAndTrimmer(trimmer, grOnly);
//		trimmer = new LogicalAndTrimmer(trimmer, grOnly, noUCERF2);
//		trimmer = new LogicalAndTrimmer(trimmer, grOnly, noRefBranches, noUCERF2);
		
//		trimmer = new LogicalAndTrimmer(trimmer, new SingleValsTreeTrimmer(ScalingRelationships.ELLSWORTH_B));
		
		
//		TreeTrimmer defaultBranchesTrimmer = getUCERF3RefBranches();
//		defaultBranchesTrimmer = new LogicalAndTrimmer(defaultBranchesTrimmer, getZengOnlyTrimmer());
//		defaultBranchesTrimmer = new LogicalAndTrimmer(defaultBranchesTrimmer, new SingleValsTreeTrimmer(DeformationModels.UCERF2_ALL));
//		TreeTrimmer defaultBranchesTrimmer = getCustomTrimmer();
		TreeTrimmer defaultBranchesTrimmer = null;
		
		// do all branch choices relative to these:
		HashMap<InversionModels, Integer> maxAway = Maps.newHashMap();
		maxAway.put(InversionModels.CHAR_CONSTRAINED, 0);
		maxAway.put(InversionModels.CHAR_UNCONSTRAINED, 0);
		maxAway.put(InversionModels.GR_CONSTRAINED, 0);
		maxAway.put(InversionModels.GR_UNCONSTRAINED, 0);

		// this is a somewhat kludgy way of passing in a special variation to the input generator
		ArrayList<CustomArg[]> variationBranches = null;
		List<CustomArg[]> variations = null;
		
		/*
		// this is for varying each weight one at a time
		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
		InversionOptions[] ops = { 	InversionOptions.SLIP_WT_NORM,
									InversionOptions.SLIP_WT_UNNORM,
									InversionOptions.PALEO_WT,
									InversionOptions.MFD_WT,
									InversionOptions.SECTION_NUCLEATION_MFD_WT,
									InversionOptions.PALEO_SECT_MFD_SMOOTH };
		
		String[] defaults_weights = {	"1", // slip norm
										"100", // slip unnorm
										"1.2", // paleo
										""+InversionConfiguration.DEFAULT_MFD_EQUALITY_WT, // MFD
										"0.01", // section nucleation
										"1000" }; // paleo sect smoothness
		
		// first add branch with defaults
		variationBranches.add(buildVariationBranch(ops, defaults_weights));
		// now add one offs
		for (int i=1; i<defaults_weights.length; i++) {
			String[] myWeightsHigh = Arrays.copyOf(defaults_weights, defaults_weights.length);
			String[] myWeightsLow = Arrays.copyOf(defaults_weights, defaults_weights.length);
			double myWeight = Double.parseDouble(defaults_weights[i]);
			myWeightsHigh[i] = ""+(float)(myWeight*10d);
			myWeightsLow[i] = ""+(float)(myWeight*0.1d);
			if (i == 1) {
				// do slips together
				double myWeight2 = Double.parseDouble(defaults_weights[0]);
				myWeightsHigh[0] = ""+(float)(myWeight2*10d);
				myWeightsLow[0] = ""+(float)(myWeight2*0.1d);
			}
			variationBranches.add(buildVariationBranch(ops, myWeightsHigh));
			variationBranches.add(buildVariationBranch(ops, myWeightsLow));
		}
		*/
		
		
//		// this is for varying each weight one at a time for deciding on paleo weights
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { 	InversionOptions.PALEO_WT,
//									InversionOptions.PALEO_SECT_MFD_SMOOTH,
//									InversionOptions.SECTION_NUCLEATION_MFD_WT };
//		
//		List<String[]> argVals = Lists.newArrayList();
//		argVals.add(toArray("0.4", "0.6", "0.8"));
//		argVals.add(toArray("500", "1000", "2000"));
//		argVals.add(toArray("0.005", "0.01", "0.02"));
//		
//		for (String val1 : argVals.get(0))
//			for (String val2 : argVals.get(1))
//				for (String val3 : argVals.get(2))
//					variationBranches.add(buildVariationBranch(ops, toArray(val1, val2, val3)));
		
		
//		// this is for varying each weight one at a time for testing rup smoothness
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { 	InversionOptions.INITIAL_GR, InversionOptions.RUP_SMOOTH_WT };
//		
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "1000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "10000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "100000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "1000000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "10000000")));
		
		
//		// this is for doing the GR starting model
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { 	InversionOptions.INITIAL_GR };
//		
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.INITIAL_ZERO };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.NO_WEIGHT_SLIP_RATES, InversionOptions.SLIP_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "100")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.PALEO_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray("1")));
//		variationBranches.add(buildVariationBranch(ops, toArray("1.5")));
//		variationBranches.add(buildVariationBranch(ops, toArray("2")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.PALEO_WT, InversionOptions.SECTION_NUCLEATION_MFD_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray("1", "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray("1.5", "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray("2", "0")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.SLIP_WT_TYPE };
////		variationBranches.add(buildVariationBranch(ops, toArray("NORM")));
//		variationBranches.add(buildVariationBranch(ops, toArray("BOTH")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.SLIP_WT_TYPE, InversionOptions.SLIP_WT_UNNORM };
////		variationBranches.add(buildVariationBranch(ops, toArray("NORM")));
//		variationBranches.add(buildVariationBranch(ops, toArray("BOTH", "0.01")));
//		variationBranches.add(buildVariationBranch(ops, toArray("BOTH", "0.02")));
//		variationBranches.add(buildVariationBranch(ops, toArray("BOTH", "0.05")));
//		variationBranches.add(buildVariationBranch(ops, toArray("BOTH", "1")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.SLIP_WT_UNNORM };
//		variationBranches.add(buildVariationBranch(ops, toArray("0.01")));
//		variationBranches.add(buildVariationBranch(ops, toArray("0.02")));
//		variationBranches.add(buildVariationBranch(ops, toArray("0.05")));
//		variationBranches.add(buildVariationBranch(ops, toArray("1")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.INITIAL_ZERO,  InversionOptions.SYNTHETIC, InversionOptions.SERIAL };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_ON, TAG_OPTION_ON)));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_ON, TAG_OPTION_OFF)));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON, TAG_OPTION_ON)));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON, TAG_OPTION_OFF)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.SERIAL, InversionOptions.INITIAL_RANDOM };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_ON)));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.SERIAL };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON)));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.MFD_WT, InversionOptions.SECTION_NUCLEATION_MFD_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray("0", "1")));
		
//		InversionOptions[] ops = { InversionOptions.PALEO_WT, InversionOptions.SECTION_NUCLEATION_MFD_WT };
//		InversionOptions[] ops = { InversionOptions.PALEO_WT, InversionOptions.SECTION_NUCLEATION_MFD_WT,
//				InversionOptions.PARKFIELD_WT };
//				InversionOptions.MFD_SMOOTHNESS_WT, InversionOptions.PALEO_SECT_MFD_SMOOTH };
//		List<String[]> argVals = Lists.newArrayList();
		// paleo
//		argVals.add(toArray("1"));
//		argVals.add(toArray("0.1", "1", "10"));
//		// section nucleation
//		argVals.add(toArray("0.001", "0.01", "0.1"));
//		// slip wt
//		argVals.add(toArray("10000"));
//		// mfd smoothness
//		argVals.add(toArray("0"));
//		// mfd smoothness for paleo sects
//		argVals.add(toArray("10", "100", "1000", "10000"));
//		
//		for (String val1 : argVals.get(0))
//			variationBranches.add(buildVariationBranch(ops, toArray(val1)));
		
//		for (String val1 : argVals.get(0))
//			for (String val2 : argVals.get(1))
//				variationBranches.add(buildVariationBranch(ops, toArray(val1, val2)));
		
//		for (String val1 : argVals.get(0))
//			for (String val2 : argVals.get(1))
//				for (String val3 : argVals.get(2))
//					variationBranches.add(buildVariationBranch(ops, toArray(val1, val2, val3)));
		
//		variationBranches = Lists.newArrayList();
//		InversionOptions[] ops = { InversionOptions.RUP_FILTER_FILE };
//		variationBranches.add(buildVariationBranch(ops, toArray(new File(runSubDir, "DistilledEnds.txt").getAbsolutePath())));
//		variationBranches.add(buildVariationBranch(ops, toArray(new File(runSubDir, "DistilledStarts.txt").getAbsolutePath())));
//		variationBranches.add(buildVariationBranch(ops, toArray(new File(runSubDir, "DistilledBoth.txt").getAbsolutePath())));
		
		variationBranches = Lists.newArrayList();
		InversionOptions[] ops = { InversionOptions.RUP_DOWNSAMPLE_DM };
		variationBranches.add(buildVariationBranch(ops, toArray("0.1")));
		
		List<InversionArg[]> saOptions = null;
		
//		saOptions = Lists.newArrayList();
//		
//		InversionArg[] invOps = { new InversionArg(
//				"--nonnegativity-const PREVENT_ZERO_RATES", "PreventZer") };
//		saOptions.add(invOps);
		
//		saOptions = Lists.newArrayList();
//		String[] coolingFuncs = { CoolingScheduleType.CLASSICAL_SA.name(),
//				CoolingScheduleType.FAST_SA.name() };
//		String[] nonnegTypes = { NonnegativityConstraintType.PREVENT_ZERO_RATES.name(),
//				NonnegativityConstraintType.LIMIT_ZERO_RATES.name(), NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN.name() };
//		String[] coolingSlowdowns = { "1", "10" };
//		String[] coolingFuncs = { CoolingScheduleType.FAST_SA.name() };
//		String[] nonnegTypes = { NonnegativityConstraintType.PREVENT_ZERO_RATES.name() };
//		String[] coolingSlowdowns = { "1", "10" };
//		
////		for (String coolingFunc : coolingFuncs) {
//			for (String nonneg : nonnegTypes) {
//				String nnVarStr = StringUtils.capitalize(nonneg.split("_")[0].toLowerCase());
//				for (String coolingSlow : coolingSlowdowns) {
//					InversionArg[] invOps = {
////							new InversionArg("--cooling-schedule "+coolingFunc,
////									"Cool"+coolingFunc.replaceAll("_", "")),
//							new InversionArg("--nonnegativity-const "+nonneg,
//									"NN"+nnVarStr),
//							new InversionArg("--slower-cooling "+coolingSlow,
//									"SlowCool"+coolingSlow)};
//					saOptions.add(invOps);
//				}
//			}
////		}
		
//		saOptions = Lists.newArrayList();
//		String[] coolingFuncs = { CoolingScheduleType.CLASSICAL_SA.name(),
//				CoolingScheduleType.FAST_SA.name(), CoolingScheduleType.VERYFAST_SA.name() };
//		String[] perturbFuncs = { GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE.name(),
//				GenerationFunctionType.GAUSSIAN.name(), GenerationFunctionType.TANGENT.name(),
//				GenerationFunctionType.POWER_LAW.name(), GenerationFunctionType.EXPONENTIAL.name() };
//		
//		for (String coolingFunc : coolingFuncs) {
//			for (String perturbFunc : perturbFuncs) {
//				InversionArg[] invOps = {
//						new InversionArg("--cooling-schedule "+coolingFunc,
//								"Cool"+coolingFunc.replaceAll("_", "")),
//						new InversionArg("--perturbation-function "+perturbFunc,
//								"Perturb"+perturbFunc.replaceAll("_", "")) };
//				saOptions.add(invOps);
//			}
//		}
		
//		saOptions = Lists.newArrayList();
//		String[] coolingSlowdowns = { "1", "2", "5", "10" };
//		
//		for (String coolingSlow : coolingSlowdowns) {
//			InversionArg[] invOps = { new InversionArg(
//					"--slower-cooling "+coolingSlow, "SlowCool"+coolingSlow) };
//			saOptions.add(invOps);
//		}
		
//		variationBranches.add(buildVariationBranch(ops, toArray("0")));
//		String[] mfdTrans = { "7.85" };
//		String[] aPrioriWts = { "0" };
//		String[] nuclWts = { "0.001", "0.01", "0.1" };
//		String[] paleoWts = { "0.1", "1", "10" };
//		String[] mfdWts = { "100", "1000", "10000" };
//		String[] eventSmoothWts = { "0", "100", "1000", "10000" };
//		for (String aPrioriWt : aPrioriWts)
//			for (String nuclWt : nuclWts)
//				for (String paleoWt : paleoWts)
//					for (String mfdWt : mfdWts)
//						variationBranches.add(buildVariationBranch(ops, toArray(nuclWt, paleoWt, mfdWt)));
//		String[] eventSmoothWts = { "0", "1000", "10000", "100000" };
//		String[] paleoWts = { "100", "1000" };
//		for (String paleoWt : paleoWts)
//			for (String eventSmoothWt : eventSmoothWts)
//				variationBranches.add(buildVariationBranch(ops, toArray(paleoWt, eventSmoothWt, "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray("0.1", "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, "100")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.INITIAL_ZERO };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF)));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.NO_SUBSEIS_RED,
//				InversionOptions.A_PRIORI_CONST_FOR_ZERO_RATES, InversionOptions.MFD_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_OFF, "0")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON, "0")));
		
//		variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//		InversionOptions[] ops = { InversionOptions.NO_SUBSEIS_RED,
//				InversionOptions.A_PRIORI_CONST_FOR_ZERO_RATES, InversionOptions.A_PRIORI_CONST_WT };
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_OFF, "100")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_OFF, "1000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON, "100")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_OFF, TAG_OPTION_ON, "1000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_OFF, "100")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_OFF, "1000")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_ON, "100")));
//		variationBranches.add(buildVariationBranch(ops, toArray(TAG_OPTION_ON, TAG_OPTION_ON, "1000")));

//				variationBranches = new ArrayList<LogicTreePBSWriter.CustomArg[]>();
//				InversionOptions[] ops = { InversionOptions.MFD_WT };
//				variationBranches.add(buildVariationBranch(ops, toArray("0")));
//				variationBranches.add(buildVariationBranch(ops, toArray("10")));
//				variationBranches.add(buildVariationBranch(ops, toArray("1000", "10", "1000")));
//				variationBranches.add(buildVariationBranch(ops, toArray("0", "10", "1000")));
//				variationBranches.add(buildVariationBranch(ops, toArray("1000", "0", "1000")));
//				variationBranches.add(buildVariationBranch(ops, toArray("1000", "10", "0")));
		//		variationBranches.add(buildVariationBranch(ops, toArray(null, "1000")));
		//		variationBranches.add(buildVariationBranch(ops, toArray(null, "100")));

		VariableLogicTreeBranch[] defaultBranches = null;
		
		boolean extraDM2Away = true;
		
		if (defaultBranchesTrimmer != null) {
			List<LogicTreeBranch> defBranches = Lists.newArrayList();
			for (LogicTreeBranch branch : new LogicTreeBranchIterator(defaultBranchesTrimmer))
				defBranches.add(branch);
			defaultBranches = new VariableLogicTreeBranch[defBranches.size()];
			for (int i=0; i<defBranches.size(); i++) {
				LogicTreeBranch branch = defBranches.get(i);
				defaultBranches[i] = new VariableLogicTreeBranch(null, branch);
			}
		}
		
//		VariableLogicTreeBranch[] defaultBranches = {
//				//				new VariableLogicTreeBranch(null, DeformationModels.GEOLOGIC_PLUS_ABM, MagAreaRelationships.ELL_B,
//				//						AveSlipForRupModels.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, null,
//				//						buildVariationBranch(ops, toArray("0.2", "0.5", "1", null))),
//				new VariableLogicTreeBranch(null, false, FaultModels.FM3_1, TotalMag5Rate.RATE_8p7, MaxMagOffFault.MAG_7p6,
//						MomentRateFixes.NONE, ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, SpatialSeisPDF.UCERF3)
//				//				new LogicTreeBranch(null, DeformationModels.GEOLOGIC, MagAreaRelationships.ELL_B,
//				//								AveSlipForRupModels.ELLSWORTH_B, null, null),
//				//				new LogicTreeBranch(null, DeformationModels.GEOLOGIC_PLUS_ABM, MagAreaRelationships.ELL_B,
//				//								AveSlipForRupModels.ELLSWORTH_B, null, null)
//		};

		if (variationBranches == null && (variations == null || variations.size() == 0)) {
			variationBranches = new ArrayList<CustomArg[]>();
			variationBranches.add(new CustomArg[0]);
		} else if (variationBranches == null) {
			// loop over each variation value building a logic tree
			variationBranches = buildVariationBranches(variations, null);
		}
		//		for (int i=variationBranches.size(); --i >= 0 && variationBranches.size() > 1;) {
		//			int numExtremes = 0;
		//			String[] branch = variationBranches.get(i);
		//			for (int j=0; j<branch.length; j++) {
		//				String[] choices = variations.get(j);
		//				if (branch[j].equals(choices[choices.length-1]))
		//					numExtremes++;
		//			}
		//			if (numExtremes >= 2)
		//				variationBranches.remove(i);
		//		}

		File writeDir;
		if (args.length > 0)
			writeDir = new File(new File(args[0]), runName);
		else
			writeDir = new File(new File("/home/kevin/OpenSHA/UCERF3/inversions"), runName);
		if (!writeDir.exists())
			writeDir.mkdir();

		//		String queue = "nbns";
		String queue = null;
		//		BatchScriptWriter batch = new USC_HPCC_ScriptWriter("pe1950");
		//		BatchScriptWriter batch = new USC_HPCC_ScriptWriter("quadcore");
		File javaBin = site.JAVA_BIN;
//		String threads = "95%"; // max for 8 core nodes, 23/24 for dodecacore
		//		String threads = "1";
		CoolingScheduleType cool = CoolingScheduleType.FAST_SA;
		if (saOptions != null) {
			cancelLoop:
			for (InversionArg[] saOps : saOptions) {
				for (InversionArg op : saOps) {
					if (op.arg.startsWith("--cool")) {
						cool = null;
						break cancelLoop;
					}
				}
			}
		}
		boolean noNonNeg = false;
		if (saOptions != null) {
			cancelLoop:
			for (InversionArg[] saOps : saOptions) {
				for (InversionArg op : saOps) {
					if (op.arg.startsWith("--nonnegativity")) {
						noNonNeg = true;
						break cancelLoop;
					}
				}
			}
		}
		CompletionCriteria[] subCompletions = { TimeCompletionCriteria.getInSeconds(1) };
//		CompletionCriteria[] subCompletions = { TimeCompletionCriteria.getInSeconds(1),
//				TimeCompletionCriteria.getInSeconds(2), TimeCompletionCriteria.getInSeconds(5),
//				TimeCompletionCriteria.getInSeconds(20) };
		//		CompletionCriteria subCompletion = VariableSubTimeCompletionCriteria.instance("5s", "300");
		boolean keepCurrentAsBest = false;
		JavaShellScriptWriter javaWriter = new JavaShellScriptWriter(javaBin, -1, getClasspath(site, runSubDir));
		javaWriter.setHeadless(true);
		if (site.FM_STORE != null) {
			javaWriter.setProperty(FaultModels.FAULT_MODEL_STORE_PROPERTY_NAME, site.FM_STORE);
		}

		int runDigits = new String((numRuns-1)+"").length();

		double nodeHours = 0;
		int cnt = 0;
		int ignoreCnt = 0;

		Iterable<LogicTreeBranch> it;
		if (prescribedBranch != null) {
			it = Lists.newArrayList();
			((List<LogicTreeBranch>)it).add(prescribedBranch);
			System.out.println("Using prescribed/hardcoded branch! "+prescribedBranch);
		} else {
			it = new LogicTreeBranchIterator(trimmer);
		}
		
		if (saOptions == null)
			saOptions = Lists.newArrayList();
		if (saOptions.isEmpty())
			saOptions.add(new InversionArg[0]);
		
		List<String> argsList = Lists.newArrayList();
		int maxJobMins = 0;

		mainLoop:
		for (LogicTreeBranch br : it) {
			for (CustomArg[] variationBranch : variationBranches) {
				for (InversionArg[] invArgs : saOptions) {
					for (CompletionCriteria subCompletion : subCompletions) {
						if (subCompletions.length > 1)
							System.out.println("SUB: "+subCompletion);
						
						VariableLogicTreeBranch branch = new VariableLogicTreeBranch(variationBranch, br);

						InversionModels im = branch.getValue(InversionModels.class);

						if (defaultBranches != null && defaultBranches.length > 0) {
							int closest = Integer.MAX_VALUE;
							for (LogicTreeBranch defaultBranch : defaultBranches) {
								int away = defaultBranch.getNumAwayFrom(branch);
								if (away < closest)
									closest = away;
							}
							int myMaxAway = maxAway.get(im);
							if (extraDM2Away && myMaxAway > 0 &&
									branch.getValue(FaultModels.class) == FaultModels.FM2_1) {
								myMaxAway++;
//								System.out.println("Incrementing maxAway (closest="+closest+")");
							}
							if (closest > myMaxAway)
								continue;
						}
						String name = branch.buildFileName();
						for (CustomArg variation : variationBranch) {
							if (variation == null)
								// this is the "off" state for a flag option
								name += "_VarNone";
							else
								name += "_Var"+variation.op.getFileName(variation.arg);
						}
						
						for (InversionArg invArg : invArgs) {
							if (invArg != null)
								name += "_Var"+invArg.prefix;
						}

						if (nameAdd != null && !nameAdd.isEmpty()) {
							if (!nameAdd.startsWith("_"))
								nameAdd = "_"+nameAdd;
							name += nameAdd;
						}
						
						if (subCompletions.length > 1)
							name += "_VarSubComp"+ThreadedSimulatedAnnealing.subCompletionArgVal(subCompletion);

						int mins;
						NonnegativityConstraintType nonNeg;

						BatchScriptWriter batch = site.forBranch(branch);
						TimeCompletionCriteria checkPointCriteria;
						if (im == InversionModels.GR_CONSTRAINED) {
							mins = constrained_run_mins;
							nonNeg = NonnegativityConstraintType.PREVENT_ZERO_RATES;
							batch = site.forBranch(branch);
							//											checkPointCritera = TimeCompletionCriteria.getInHours(2);
							checkPointCriteria = null;
						} else if (im == InversionModels.CHAR_CONSTRAINED) {
							mins = constrained_run_mins;
							nonNeg = NonnegativityConstraintType.LIMIT_ZERO_RATES;
							//											checkPointCritera = TimeCompletionCriteria.getInHours(2);
							checkPointCriteria = null;
						} else { // UNCONSTRAINED
							mins = 60;
							nonNeg = NonnegativityConstraintType.LIMIT_ZERO_RATES;
							checkPointCriteria = null;
						}
						if (noNonNeg)
							nonNeg = null;
						int ppn = site.getPPN(branch); // minimum number of cpus
						CompletionCriteria criteria = TimeCompletionCriteria.getInMinutes(mins);
						javaWriter.setMaxHeapSizeMB(site.getMaxHeapSizeMB(branch));
						javaWriter.setInitialHeapSizeMB(site.getInitialHeapSizeMB(branch));

						runLoop:
						for (int r=runStart; r<numRuns; r++) {
							String jobName = name;
							if (numRuns > 1) {
								String rStr = r+"";
								while (rStr.length() < runDigits)
									rStr = "0"+rStr;
								jobName += "_run"+rStr;
							}
							
							if (cnt == overallMaxJobs)
								break mainLoop;
							
							if (ignores != null) {
								for (String ignore : ignores) {
									if (jobName.startsWith(ignore)) {
										ignoreCnt++;
										continue runLoop;
									}
								}
							}

							File pbs = new File(writeDir, jobName+".pbs");
							System.out.println("Writing: "+pbs.getName());

							int jobMins = mins+60;
							if (jobMins > maxJobMins)
								maxJobMins = jobMins;

							String className = CommandLineInversionRunner.class.getName();
							String classArgs = ThreadedSimulatedAnnealing.completionCriteriaToArgument(criteria);
							classArgs += " "+ThreadedSimulatedAnnealing.subCompletionCriteriaToArgument(subCompletion);
							if (keepCurrentAsBest)
								classArgs += " --cur-as-best";
							if (cool != null)
								classArgs += " --cool "+cool.name();
							if (nonNeg != null)
								classArgs += " --nonneg "+nonNeg.name();
							classArgs += " --num-threads "+threads;
							if (checkPointCriteria != null)
								classArgs += " --checkpoint "+checkPointCriteria.getTimeStr();
							classArgs += " --branch-prefix "+jobName;
							classArgs += " --directory "+runSubDir.getAbsolutePath();
							if (lightweight && r > 0)
								classArgs += " --lightweight";
							if (noPlots)
								classArgs += " --no-plots";
							//										classArgs += " --slower-cooling 1000";
							for (CustomArg variation : variationBranch) {
								if (variation != null)
									// this is the "off" state for a flag option
									classArgs += " "+variation.op.getCommandLineArgs(variation.arg);
							}
							for (InversionArg invArg : invArgs) {
								if (invArg != null)
									classArgs += " "+invArg.arg;
							}
							
							argsList.add(classArgs);

							batch.writeScript(pbs, javaWriter.buildScript(className, classArgs),
									jobMins, 1, ppn, queue);

							nodeHours += (double)mins / 60d;

							cnt++;
						}
					}
				}
			}

		}

		System.out.println("Wrote "+cnt+" jobs (ignored "+ignoreCnt+")");
		System.out.println("Node hours: "+(float)nodeHours + " (/60: "+((float)nodeHours/60f)+") (/14: "+((float)nodeHours/14f)+")");
		//		DeformationModels.forFaultModel(null).toArray(new DeformationModels[0])
		if (batchSize > 0) {
			System.out.println("Writing batches!");
			writeMPJDispatchJob(site, argsList, numRuns, batchSize, jobsPerNode, maxJobMins, runSubDir, writeDir);
		}
		System.exit(0);
	}
	
	public static void writeMPJDispatchJob(RunSites site,
			List<String> argsList, int numRuns, int maxNodes, int jobsPerNode,
			int maxRuntimeMins, File remoteDir, File writeDir) throws IOException {
		if (numRuns > 1) {
			List<String> sortedArgsList = Lists.newArrayList();
			
			Preconditions.checkState(argsList.size() % numRuns == 0);
			int numBranches = argsList.size() / numRuns;
			for (int r=0; r<numRuns; r++) {
				for (int i=0; i<numBranches; i++) {
					int index = i * numRuns + r;
					sortedArgsList.add(argsList.get(index));
				}
			}
			
			argsList = sortedArgsList;
		}
		
		int maxJobsPerBatch = maxNodes * jobsPerNode;
		
		List<List<String>> bins = Lists.newArrayList();
		List<String> curBin = Lists.newArrayList();
		for (String args : argsList) {
			if (curBin.size() == maxJobsPerBatch) {
				bins.add(curBin);
				curBin = Lists.newArrayList();
			}
			curBin.add(args);
		}
		if (!curBin.isEmpty())
			bins.add(curBin);
		
		int numLen = ((bins.size()-1)+"").length();
		
		BatchScriptWriter batch = site.forBranch(null);
		int jobMins = maxRuntimeMins+60;
		int ppn = site.getPPN(null);
		
		JavaShellScriptWriter mpjWrite;
		if (site.fastMPJ)
			mpjWrite = new FastMPJShellScriptWriter(site.JAVA_BIN, site.getMaxHeapSizeMB(null),
					getClasspath(site, remoteDir), site.MPJ_HOME);
		else
			mpjWrite = new MPJExpressShellScriptWriter(site.JAVA_BIN, site.getMaxHeapSizeMB(null),
					getClasspath(site, remoteDir), site.MPJ_HOME);
		mpjWrite.setInitialHeapSizeMB(site.getInitialHeapSizeMB(null));
		mpjWrite.setHeadless(true);
		
		for (int i=0; i<bins.size(); i++) {
			String iStr = i+"";
			while (iStr.length() < numLen)
				iStr = "0"+iStr;
			
			List<String> bin = bins.get(i);
			List<String[]> binArrays = Lists.newArrayList();
			for (String args : bin)
				binArrays.add(Iterables.toArray(Splitter.on(" ").split(args), String.class));
			File xmlFile = new File(writeDir, "batch"+iStr+".xml");
			MPJInversionDistributor.writeXMLInputFile(binArrays, xmlFile);
			File remoteXMLFile = new File(remoteDir, xmlFile.getName());
			
			String args = "--exact-dispatch "+jobsPerNode+" "+remoteXMLFile.getAbsolutePath();
			
			List<String> script = mpjWrite.buildScript(MPJInversionDistributor.class.getName(), args);
			
			double nodesNeeded = (double)bin.size() / (double)jobsPerNode;
			int nodes = (int)Math.ceil(nodesNeeded);
			
			File batchFile = new File(writeDir, "batch"+iStr+".pbs");
			batch.writeScript(batchFile, script, jobMins, nodes, ppn, null);
			System.out.println("Writing "+batchFile.getName()+" ("+nodes+" nodes)");
		}
	}
	
	private static void writeBinnedJobs(RunSites site, List<String> pbsNames, int runsPerJob,
			int maxRuntimeMins, File remoteDir, File writeDir) throws IOException {
		// OLD VERSION
		Collections.sort(pbsNames, new Comparator<String>() {
			
			private int parseRun(String name) {
				if (!name.contains("_run"))
					return 0;
				name = name.substring(name.indexOf("_run")+4);
				return Integer.parseInt(name.substring(0, name.indexOf(".pbs")));
			}

			@Override
			public int compare(String o1, String o2) {
				Integer r1 = parseRun(o1);
				Integer r2 = parseRun(o2);
				if (r1 == r2)
					return o1.compareTo(o2);
				return r1.compareTo(r2);
			}
		});
		List<List<String>> bins = Lists.newArrayList();
		List<String> curBin = Lists.newArrayList();
		for (String pbsName : pbsNames) {
			if (curBin.size() == runsPerJob) {
				bins.add(curBin);
				curBin = Lists.newArrayList();
			}
			curBin.add(pbsName);
		}
		if (!curBin.isEmpty())
			bins.add(curBin);
		
		int numLen = ((bins.size()-1)+"").length();
		
		BatchScriptWriter batch = site.forBranch(null);
		int jobMins = maxRuntimeMins+30;
		int ppn = site.getPPN(null);
		
		for (int i=0; i<bins.size(); i++) {
			List<String> script = Lists.newArrayList();
			int nodeNumber = 1;
			List<String> bin = bins.get(i);
			for (int j=0; j<bin.size(); j++) {
				String pbsName = bin.get(j);
				script.add("");
				script.add("# run "+nodeNumber+": "+pbsName);
				script.add("node=`sed -n '"+nodeNumber+"p' $PBS_NODEFILE`");
				File pbsFile = new File(remoteDir, pbsName);
				File pbsStdOutFile = new File(remoteDir, pbsName+".output");
				script.add("chmod u+x "+pbsFile.getAbsolutePath());
				// ssh -n -f user@host "sh -c 'cd /whereever; nohup ./whatever > /dev/null 2>&1 &'"
				if (j == bin.size() -1)
					// for the last one we execute in foreground and wait for completion
					script.add("ssh $node \"sh -c '"+pbsFile.getAbsolutePath()+" > "
							+pbsStdOutFile.getAbsolutePath()+" 2>&1'\"");
				else
					script.add("ssh -n -f $node \"sh -c 'nohup "+pbsFile.getAbsolutePath()+" > "
							+pbsStdOutFile.getAbsolutePath()+" 2>&1 &'\"");
				
				nodeNumber++;
			}
			
			script.add("# sleep for 30 mins to make sure everything is done");
			script.add("sleep 1800");
			
			int nodes = bin.size();
			
			String iStr = i+"";
			while (iStr.length() < numLen)
				iStr = "0"+iStr;
			File batchFile = new File(writeDir, "batch"+iStr+".pbs");
			batch.writeScript(batchFile, script, jobMins, nodes, ppn, null);
			System.out.println("Writing "+batchFile.getName()+" ("+nodes+" nodes)");
		}
	}

}
