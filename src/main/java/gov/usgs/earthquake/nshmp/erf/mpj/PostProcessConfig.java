package gov.usgs.earthquake.nshmp.erf.mpj;

import com.google.common.base.Preconditions;

public final class PostProcessConfig {

	private final boolean writeTrueMean;
	private final boolean writeNodeBranchAverages;
	private final int nodeBAAsyncThreads;
	private final boolean nodeBASkipSectBySect;
	private final GridSourcePostProcessConfig gridSourcePostProcess;

	private PostProcessConfig(Builder builder) {
		this.writeTrueMean = builder.writeTrueMean;
		this.writeNodeBranchAverages = builder.writeNodeBranchAverages;
		this.nodeBAAsyncThreads = builder.nodeBAAsyncThreads;
		this.nodeBASkipSectBySect = builder.nodeBASkipSectBySect;
		this.gridSourcePostProcess = builder.gridSourcePostProcess;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static PostProcessConfig defaults() {
		return builder().build();
	}

	public boolean writeTrueMean() {
		return writeTrueMean;
	}

	public boolean writeNodeBranchAverages() {
		return writeNodeBranchAverages;
	}

	public int nodeBAAsyncThreads() {
		return nodeBAAsyncThreads;
	}

	public boolean nodeBASkipSectBySect() {
		return nodeBASkipSectBySect;
	}

	public GridSourcePostProcessConfig gridSourcePostProcess() {
		return gridSourcePostProcess;
	}

	public static final class Builder {
		private boolean writeTrueMean = true;
		private boolean writeNodeBranchAverages = true;
		private int nodeBAAsyncThreads = 2;
		private boolean nodeBASkipSectBySect = true;
		private GridSourcePostProcessConfig gridSourcePostProcess;

		public Builder writeTrueMean(boolean writeTrueMean) {
			this.writeTrueMean = writeTrueMean;
			return this;
		}

		public Builder writeNodeBranchAverages(boolean writeNodeBranchAverages) {
			this.writeNodeBranchAverages = writeNodeBranchAverages;
			return this;
		}

		public Builder nodeBAAsyncThreads(int nodeBAAsyncThreads) {
			this.nodeBAAsyncThreads = nodeBAAsyncThreads;
			return this;
		}

		public Builder nodeBASkipSectBySect(boolean nodeBASkipSectBySect) {
			this.nodeBASkipSectBySect = nodeBASkipSectBySect;
			return this;
		}

		public Builder gridSourcePostProcess(GridSourcePostProcessConfig gridSourcePostProcess) {
			this.gridSourcePostProcess = gridSourcePostProcess;
			return this;
		}

		public PostProcessConfig build() {
			Preconditions.checkArgument(nodeBAAsyncThreads > 0, "nodeBAAsyncThreads must be > 0");
			return new PostProcessConfig(this);
		}
	}
}
