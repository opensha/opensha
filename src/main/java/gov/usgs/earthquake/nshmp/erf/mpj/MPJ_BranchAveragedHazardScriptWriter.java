package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SingleSolHazardCalc;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrections;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_BranchAveragedHazardScriptWriter {

	public File writeScripts(Request request) throws IOException {
		Preconditions.checkNotNull(request, "request is required");

		String dirName = request.run().buildDirectoryName();
		File localDir = new File(request.hpc().localMainDir(), dirName);
		Preconditions.checkState(localDir.exists() || localDir.mkdir(), "Couldn't create %s", localDir);

		String mainDirPath = "$MAIN_DIR";
		String dirPath = "$DIR";
		List<File> classpath = List.of(new File(dirPath+"/"+request.hpc().jarFileName()));
		JavaShellScriptWriter mpjWriter = request.hpc().buildMPJWriter(classpath);
		mpjWriter.setEnvVar("MAIN_DIR", request.hpc().remoteMainDir().getAbsolutePath());
		mpjWriter.setEnvVar("DIR", mainDirPath+"/"+dirName);
		JavaShellScriptWriter singleNodeMPJWriter = request.hpc().buildSingleNodeMPJWriter(classpath);
		request.hpc().copyEnvVars(mpjWriter, singleNodeMPJWriter);
		if (request.linkFromDirectoryName() != null) {
			List<String> setupLines = linkSetupLines(request, dirPath);
			mpjWriter.setCustomSetupLines(setupLines);
			singleNodeMPJWriter.setCustomSetupLines(setupLines);
		}

		BatchScriptWriter batchWriter = request.hpc().buildBatchWriter();
		String resultsPath = dirPath+"/results";
		String inputFilePath = dirPath+"/"+request.solutionFileName();
		HazardScriptUtil.HazardRegion region = HazardScriptUtil.resolveHazardRegion(localDir, request.hazard(), null);

		System.out.println("Directory name: "+dirName);
		System.out.println("Local output dir: "+localDir.getAbsolutePath());
		System.out.println("Input solution: "+inputFilePath);
		System.out.println("Region: "+region.arg().trim());
		System.out.println("Hazard job time: "+request.wallTimeMinutes()+" mins = "
				+(float)((double)request.wallTimeMinutes()/60d)+" hours");

		for (IncludeBackgroundOption backgroundOption : request.backgroundOptions())
			writeHazardJob(localDir, batchWriter, mpjWriter, singleNodeMPJWriter, request, backgroundOption,
					inputFilePath, resultsPath, region.arg());

		return localDir;
	}

	private List<String> linkSetupLines(Request request, String dirPath) {
		String target = "$MAIN_DIR/"+request.linkFromDirectoryName()+"/"+request.solutionFileName();
		String link = dirPath+"/"+request.solutionFileName();
		List<String> setupLines = new ArrayList<>();
		setupLines.add("if [[ ! -e "+link+" ]];then");
		setupLines.add("  ln -s "+target+" "+link);
		setupLines.add("fi");
		return setupLines;
	}

	private void writeHazardJob(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			JavaShellScriptWriter singleNodeMPJWriter, Request request, IncludeBackgroundOption backgroundOption,
			String inputFilePath, String resultsPath, String regionArg) throws IOException {
		int nodes = resolveNodes(request, backgroundOption);
		StringBuilder args = new StringBuilder();
		HazardScriptUtil.appendArg(args, "--input-file", inputFilePath);
		HazardScriptUtil.appendArg(args, "--output-dir", resultsPath);
		HazardScriptUtil.appendArg(args, "--output-file", resultsPath+"_hazard_"+backgroundOption.name()+".zip");
		args.append(regionArg);
		HazardScriptUtil.appendArg(args, "--gridded-seis", backgroundOption.name());
		if (request.noMFDs())
			HazardScriptUtil.appendFlag(args, "--no-mfds");
		if (backgroundOption != IncludeBackgroundOption.EXCLUDE)
			appendGriddedSourceArgs(args, request);
		HazardScriptUtil.appendSharedArgs(args, request.hazard(), false);
		appendSupersamplingArgs(args, request.supersamplingMode());
		for (String extraArg : request.extraArgs())
			args.append(" ").append(extraArg);
		args.append(" ").append(buildDispatchArgs(request, backgroundOption));

		JavaShellScriptWriter writer = runSingleNodeInclude(request, backgroundOption) ? singleNodeMPJWriter : mpjWriter;
		List<String> script = writer.buildScript(MPJ_SingleSolHazardCalc.class.getName(), args.toString());
		File jobFile = new File(localDir, "batch_hazard_"+backgroundOption.name()+".slurm");
		batchWriter.writeScript(jobFile, script, HazardScriptUtil.capWeek(request.wallTimeMinutes()), nodes,
				request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().queue());
	}

	private void appendGriddedSourceArgs(StringBuilder args, Request request) {
		if (request.distanceCorrection() != null)
			HazardScriptUtil.appendArg(args, "--dist-corr", request.distanceCorrection().name());
		if (request.pointSourceType() != null)
			HazardScriptUtil.appendArg(args, "--point-source-type", request.pointSourceType().name());
		if (request.pointFiniteNumRandSurfaces() != null)
			HazardScriptUtil.appendArg(args, "--point-finite-num-rand-surfaces",
					request.pointFiniteNumRandSurfaces());
		if (request.pointFiniteSampleAlongStrike())
			HazardScriptUtil.appendFlag(args, "--point-finite-sample-along-strike");
		if (request.pointFiniteSampleDownDip())
			HazardScriptUtil.appendFlag(args, "--point-finite-sample-down-dip");
		if (request.pointFiniteMinMag() != null)
			HazardScriptUtil.appendArg(args, "--point-finite-min-mag", request.pointFiniteMinMag().floatValue());
	}

	private void appendSupersamplingArgs(StringBuilder args, SupersamplingMode supersamplingMode) {
		switch (supersamplingMode) {
		case NONE:
			break;
		case QUICK:
			HazardScriptUtil.appendFlag(args, "--supersample-quick");
			break;
		case FULL:
			HazardScriptUtil.appendFlag(args, "--supersample");
			break;
		case FULL_WITH_FINITE:
			HazardScriptUtil.appendFlag(args, "--supersample-finite");
			break;
		default:
			throw new IllegalStateException("Unhandled supersampling mode: "+supersamplingMode);
		}
	}

	private String buildDispatchArgs(Request request, IncludeBackgroundOption backgroundOption) {
		MPJTaskCalculator.ArgumentBuilder builder = MPJTaskCalculator.argumentBuilder();
		if (runSingleNodeInclude(request, backgroundOption)) {
			builder.exactDispatch(resolveGridNodeCount(request));
		} else {
			builder.minDispatch(request.hpc().threadsPerNode()).maxDispatch(resolveMaxDispatch(request));
		}
		return builder.threads(request.hpc().threadsPerNode()).build();
	}

	private int resolveNodes(Request request, IncludeBackgroundOption backgroundOption) {
		if (runSingleNodeInclude(request, backgroundOption))
			return 1;
		return request.hpc().nodes();
	}

	private boolean runSingleNodeInclude(Request request, IncludeBackgroundOption backgroundOption) {
		return request.singleNodeIncludeWhenWritingAllBackgroundOptions()
				&& backgroundOption == IncludeBackgroundOption.INCLUDE
				&& request.backgroundOptions().containsAll(Arrays.asList(IncludeBackgroundOption.values()));
	}

	private int resolveMaxDispatch(Request request) {
		if (request.maxDispatch() != null)
			return request.maxDispatch();
		int gridNodeCount = resolveGridNodeCount(request);
		int threads = request.hpc().threadsPerNode();
		if (gridNodeCount > 50000)
			return Integer.max(threads*20, 1000);
		if (gridNodeCount > 10000)
			return Integer.max(threads*10, 500);
		if (gridNodeCount > 5000)
			return threads*5;
		return threads*3;
	}

	private int resolveGridNodeCount(Request request) {
		if (request.hazard().region() == null)
			return request.hpc().threadsPerNode();
		return request.hazard().region().getNodeCount();
	}

	public enum SupersamplingMode {
		NONE,
		QUICK,
		FULL,
		FULL_WITH_FINITE
	}

	public static final class Request {

		private final RunConfig run;
		private final HPCConfig hpc;
		private final HazardConfig hazard;
		private final String solutionFileName;
		private final String linkFromDirectoryName;
		private final List<IncludeBackgroundOption> backgroundOptions;
		private final int wallTimeMinutes;
		private final boolean noMFDs;
		private final SupersamplingMode supersamplingMode;
		private final PointSourceDistanceCorrections distanceCorrection;
		private final BackgroundRupType pointSourceType;
		private final Integer pointFiniteNumRandSurfaces;
		private final boolean pointFiniteSampleAlongStrike;
		private final boolean pointFiniteSampleDownDip;
		private final Double pointFiniteMinMag;
		private final Integer maxDispatch;
		private final boolean singleNodeIncludeWhenWritingAllBackgroundOptions;
		private final List<String> extraArgs;

		private Request(Builder builder) {
			this.run = builder.run;
			this.hpc = builder.hpc;
			this.hazard = builder.hazard;
			this.solutionFileName = builder.solutionFileName;
			this.linkFromDirectoryName = builder.linkFromDirectoryName;
			this.backgroundOptions = List.copyOf(builder.backgroundOptions);
			this.wallTimeMinutes = builder.wallTimeMinutes;
			this.noMFDs = builder.noMFDs;
			this.supersamplingMode = builder.supersamplingMode == null
					? (builder.hazard.supersample() ? SupersamplingMode.QUICK : SupersamplingMode.NONE)
					: builder.supersamplingMode;
			this.distanceCorrection = builder.distanceCorrection;
			this.pointSourceType = builder.pointSourceType;
			this.pointFiniteNumRandSurfaces = builder.pointFiniteNumRandSurfaces;
			this.pointFiniteSampleAlongStrike = builder.pointFiniteSampleAlongStrike;
			this.pointFiniteSampleDownDip = builder.pointFiniteSampleDownDip;
			this.pointFiniteMinMag = builder.pointFiniteMinMag;
			this.maxDispatch = builder.maxDispatch;
			this.singleNodeIncludeWhenWritingAllBackgroundOptions = builder.singleNodeIncludeWhenWritingAllBackgroundOptions;
			this.extraArgs = List.copyOf(builder.extraArgs);
		}

		public static Builder builder() {
			return new Builder();
		}

		public RunConfig run() {
			return run;
		}

		public HPCConfig hpc() {
			return hpc;
		}

		public HazardConfig hazard() {
			return hazard;
		}

		public String solutionFileName() {
			return solutionFileName;
		}

		public String linkFromDirectoryName() {
			return linkFromDirectoryName;
		}

		public List<IncludeBackgroundOption> backgroundOptions() {
			return backgroundOptions;
		}

		public int wallTimeMinutes() {
			return wallTimeMinutes;
		}

		public boolean noMFDs() {
			return noMFDs;
		}

		public SupersamplingMode supersamplingMode() {
			return supersamplingMode;
		}

		public PointSourceDistanceCorrections distanceCorrection() {
			return distanceCorrection;
		}

		public BackgroundRupType pointSourceType() {
			return pointSourceType;
		}

		public Integer pointFiniteNumRandSurfaces() {
			return pointFiniteNumRandSurfaces;
		}

		public boolean pointFiniteSampleAlongStrike() {
			return pointFiniteSampleAlongStrike;
		}

		public boolean pointFiniteSampleDownDip() {
			return pointFiniteSampleDownDip;
		}

		public Double pointFiniteMinMag() {
			return pointFiniteMinMag;
		}

		public Integer maxDispatch() {
			return maxDispatch;
		}

		public boolean singleNodeIncludeWhenWritingAllBackgroundOptions() {
			return singleNodeIncludeWhenWritingAllBackgroundOptions;
		}

		public List<String> extraArgs() {
			return extraArgs;
		}

		public static final class Builder {
			private RunConfig run;
			private HPCConfig hpc;
			private HazardConfig hazard;
			private String solutionFileName = "results_branch_averaged.zip";
			private String linkFromDirectoryName;
			private final List<IncludeBackgroundOption> backgroundOptions = new ArrayList<>();
			private int wallTimeMinutes = 600;
			private boolean noMFDs;
			private SupersamplingMode supersamplingMode;
			private PointSourceDistanceCorrections distanceCorrection;
			private BackgroundRupType pointSourceType;
			private Integer pointFiniteNumRandSurfaces;
			private boolean pointFiniteSampleAlongStrike;
			private boolean pointFiniteSampleDownDip;
			private Double pointFiniteMinMag;
			private Integer maxDispatch;
			private boolean singleNodeIncludeWhenWritingAllBackgroundOptions = true;
			private final List<String> extraArgs = new ArrayList<>();

			private Builder() {
				backgroundOptions.addAll(Arrays.asList(IncludeBackgroundOption.values()));
			}

			public Builder run(RunConfig run) {
				this.run = run;
				return this;
			}

			public Builder hpc(HPCConfig hpc) {
				this.hpc = hpc;
				return this;
			}

			public Builder hazard(HazardConfig hazard) {
				this.hazard = hazard;
				return this;
			}

			public Builder solutionFileName(String solutionFileName) {
				this.solutionFileName = solutionFileName;
				return this;
			}

			public Builder linkFromDirectoryName(String linkFromDirectoryName) {
				this.linkFromDirectoryName = linkFromDirectoryName;
				return this;
			}

			public Builder backgroundOptions(IncludeBackgroundOption... backgroundOptions) {
				this.backgroundOptions.clear();
				if (backgroundOptions != null)
					this.backgroundOptions.addAll(Arrays.asList(backgroundOptions));
				return this;
			}

			public Builder backgroundOptions(Collection<IncludeBackgroundOption> backgroundOptions) {
				this.backgroundOptions.clear();
				if (backgroundOptions != null)
					this.backgroundOptions.addAll(backgroundOptions);
				return this;
			}

			public Builder wallTimeMinutes(int wallTimeMinutes) {
				this.wallTimeMinutes = wallTimeMinutes;
				return this;
			}

			public Builder noMFDs(boolean noMFDs) {
				this.noMFDs = noMFDs;
				return this;
			}

			public Builder supersamplingMode(SupersamplingMode supersamplingMode) {
				this.supersamplingMode = supersamplingMode;
				return this;
			}

			public Builder distanceCorrection(PointSourceDistanceCorrections distanceCorrection) {
				this.distanceCorrection = distanceCorrection;
				return this;
			}

			public Builder pointSourceType(BackgroundRupType pointSourceType) {
				this.pointSourceType = pointSourceType;
				return this;
			}

			public Builder pointFiniteNumRandSurfaces(Integer pointFiniteNumRandSurfaces) {
				this.pointFiniteNumRandSurfaces = pointFiniteNumRandSurfaces;
				return this;
			}

			public Builder pointFiniteSampleAlongStrike(boolean pointFiniteSampleAlongStrike) {
				this.pointFiniteSampleAlongStrike = pointFiniteSampleAlongStrike;
				return this;
			}

			public Builder pointFiniteSampleDownDip(boolean pointFiniteSampleDownDip) {
				this.pointFiniteSampleDownDip = pointFiniteSampleDownDip;
				return this;
			}

			public Builder pointFiniteMinMag(Double pointFiniteMinMag) {
				this.pointFiniteMinMag = pointFiniteMinMag;
				return this;
			}

			public Builder maxDispatch(Integer maxDispatch) {
				this.maxDispatch = maxDispatch;
				return this;
			}

			public Builder singleNodeIncludeWhenWritingAllBackgroundOptions(
					boolean singleNodeIncludeWhenWritingAllBackgroundOptions) {
				this.singleNodeIncludeWhenWritingAllBackgroundOptions = singleNodeIncludeWhenWritingAllBackgroundOptions;
				return this;
			}

			public Builder addExtraArg(String extraArg) {
				if (extraArg != null && !extraArg.isBlank())
					extraArgs.add(extraArg);
				return this;
			}

			public Builder addExtraArgs(Collection<String> extraArgs) {
				if (extraArgs != null)
					for (String extraArg : extraArgs)
						addExtraArg(extraArg);
				return this;
			}

			public Request build() {
				Preconditions.checkNotNull(run, "run config is required");
				Preconditions.checkNotNull(hpc, "HPC config is required");
				Preconditions.checkNotNull(hazard, "hazard config is required");
				Preconditions.checkArgument(solutionFileName != null && !solutionFileName.isBlank(),
						"solutionFileName is required");
				Preconditions.checkArgument(!backgroundOptions.isEmpty(), "at least one background option is required");
				Preconditions.checkArgument(wallTimeMinutes > 0, "wallTimeMinutes must be > 0");
				if (maxDispatch != null)
					Preconditions.checkArgument(maxDispatch > 0, "maxDispatch must be > 0");
				if (pointFiniteNumRandSurfaces != null)
					Preconditions.checkArgument(pointFiniteNumRandSurfaces > 0,
							"pointFiniteNumRandSurfaces must be > 0");
				return new Request(this);
			}
		}
	}
}
