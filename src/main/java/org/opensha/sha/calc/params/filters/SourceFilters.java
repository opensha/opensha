package org.opensha.sha.calc.params.filters;

public enum SourceFilters {
	
	FIXED_DIST_CUTOFF("Fixed Distance Cutoff") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new FixedDistanceCutoffFilter();
		}
	},
	TRT_DIST_CUTOFFS("Tectonic Regime-Specific Distance Cutoffs") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new TectonicRegionDistCutoffFilter();
		}
	},
	MAG_DIST_CUTOFFS("Magnitude-Dependent Distance Cutoffs") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new MagDependentDistCutoffFilter();
		}
	},
	MIN_MAG("Minimum Magnitude") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new MinMagFilter();
		}
	};
	
	private String label;

	private SourceFilters(String label) {
		this.label = label;
	}
	
	@Override
	public String toString() {
		return label;
	}

	public abstract SourceFilter initFilter();
	
	

}
