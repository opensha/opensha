package gov.usgs.earthquake.nshmp.erf.mpj;

import com.google.common.base.Preconditions;

public final class InversionScriptWriteRequest {

	private final RunConfig run;
	private final HPCConfig hpc;
	private final LogicTreeConfig logicTree;
	private final InversionConfig inversion;
	private final HazardConfig hazard;
	private final PostProcessConfig postProcess;

	private InversionScriptWriteRequest(Builder builder) {
		this.run = builder.run;
		this.hpc = builder.hpc;
		this.logicTree = builder.logicTree;
		this.inversion = builder.inversion;
		this.hazard = builder.hazard;
		this.postProcess = builder.postProcess;
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

	public LogicTreeConfig logicTree() {
		return logicTree;
	}

	public InversionConfig inversion() {
		return inversion;
	}

	public HazardConfig hazard() {
		return hazard;
	}

	public PostProcessConfig postProcess() {
		return postProcess;
	}

	public static final class Builder {
		private RunConfig run;
		private HPCConfig hpc;
		private LogicTreeConfig logicTree;
		private InversionConfig inversion;
		private HazardConfig hazard;
		private PostProcessConfig postProcess = PostProcessConfig.defaults();

		public Builder run(RunConfig run) {
			this.run = run;
			return this;
		}

		public Builder hpc(HPCConfig hpc) {
			this.hpc = hpc;
			return this;
		}

		public Builder logicTree(LogicTreeConfig logicTree) {
			this.logicTree = logicTree;
			return this;
		}

		public Builder inversion(InversionConfig inversion) {
			this.inversion = inversion;
			return this;
		}

		public Builder hazard(HazardConfig hazard) {
			this.hazard = hazard;
			return this;
		}

		public Builder postProcess(PostProcessConfig postProcess) {
			this.postProcess = postProcess;
			return this;
		}

		public InversionScriptWriteRequest build() {
			Preconditions.checkNotNull(run, "run config is required");
			Preconditions.checkNotNull(hpc, "HPC config is required");
			Preconditions.checkNotNull(logicTree, "logic tree config is required");
			Preconditions.checkNotNull(inversion, "inversion config is required");
			Preconditions.checkNotNull(postProcess, "post-process config is required");
			return new InversionScriptWriteRequest(this);
		}
	}
}
