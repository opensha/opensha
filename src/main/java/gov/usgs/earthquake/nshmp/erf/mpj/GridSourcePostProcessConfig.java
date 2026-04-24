package gov.usgs.earthquake.nshmp.erf.mpj;

import com.google.common.base.Preconditions;

public final class GridSourcePostProcessConfig {

	private final int samplesPerSolution;
	private final double solutionMinMag;
	private final boolean averageOnly;
	private final boolean writeHazardProducts;

	private GridSourcePostProcessConfig(Builder builder) {
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

		public GridSourcePostProcessConfig build() {
			Preconditions.checkArgument(samplesPerSolution > 0, "samplesPerSolution must be > 0");
			return new GridSourcePostProcessConfig(this);
		}
	}
}
