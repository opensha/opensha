package gov.usgs.earthquake.nshmp.erf.mpj;

import com.google.common.base.Preconditions;

public final class PostProcessConfig {

	private final boolean writeTrueMean;
	private final boolean writeNodeBranchAverages;
	private final int nodeBAAsyncThreads;
	private final boolean nodeBASkipSectBySect;
	private final GridSourceConfig gridSourcePostProcess;

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

	public GridSourceConfig gridSourcePostProcess() {
		return gridSourcePostProcess;
	}

	public static final class Builder {
		private boolean writeTrueMean = true;
		private boolean writeNodeBranchAverages = true;
		private int nodeBAAsyncThreads = 2;
		private boolean nodeBASkipSectBySect = true;
		private GridSourceConfig gridSourcePostProcess;

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

		public Builder gridSourcePostProcess(GridSourceConfig gridSourcePostProcess) {
			this.gridSourcePostProcess = gridSourcePostProcess;
			return this;
		}

		public PostProcessConfig build() {
			Preconditions.checkArgument(nodeBAAsyncThreads > 0, "nodeBAAsyncThreads must be > 0");
			return new PostProcessConfig(this);
		}
	}

	public static final class GridSourceConfig {

		private final int samplesPerSolution;
		private final double solutionMinMag;
		private final boolean averageOnly;
		private final boolean writeHazardProducts;

		private GridSourceConfig(Builder builder) {
			this.samplesPerSolution = builder.samplesPerSolution;
			this.solutionMinMag = builder.solutionMinMag;
			this.averageOnly = builder.averageOnly;
			this.writeHazardProducts = builder.writeHazardProducts;
		}

		public static Builder builder() {
			return new Builder();
		}

		public int samplesPerSolution() {
			return samplesPerSolution;
		}

		public double solutionMinMag() {
			return solutionMinMag;
		}

		public boolean averageOnly() {
			return averageOnly;
		}

		public boolean writeHazardProducts() {
			return writeHazardProducts;
		}

		public static final class Builder {
			private int samplesPerSolution = 5;
			private double solutionMinMag = 5d;
			private boolean averageOnly = true;
			private boolean writeHazardProducts = true;

			public Builder samplesPerSolution(int samplesPerSolution) {
				this.samplesPerSolution = samplesPerSolution;
				return this;
			}

			public Builder solutionMinMag(double solutionMinMag) {
				this.solutionMinMag = solutionMinMag;
				return this;
			}

			public Builder averageOnly(boolean averageOnly) {
				this.averageOnly = averageOnly;
				return this;
			}

			public Builder writeHazardProducts(boolean writeHazardProducts) {
				this.writeHazardProducts = writeHazardProducts;
				return this;
			}

			public GridSourceConfig build() {
				Preconditions.checkArgument(samplesPerSolution > 0, "samplesPerSolution must be > 0");
				return new GridSourceConfig(this);
			}
		}
	}
}
