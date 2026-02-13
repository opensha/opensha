package org.opensha.sha.calc.sourceFilters;

public enum SourceFilters {
	
	FIXED_DIST_CUTOFF(FixedDistanceCutoffFilter.class, "Fixed Distance Cutoff") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new FixedDistanceCutoffFilter();
		}
	},
	TRT_DIST_CUTOFFS(TectonicRegionDistCutoffFilter.class, "Tectonic Regime-Specific Distance Cutoffs") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new TectonicRegionDistCutoffFilter();
		}
	},
	MAG_DIST_CUTOFFS(MagDependentDistCutoffFilter.class, "Magnitude-Dependent Distance Cutoffs") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new MagDependentDistCutoffFilter();
		}
	},
	MIN_MAG(MinMagFilter.class, "Minimum Magnitude") {
		@Override
		public synchronized SourceFilter initFilter() {
			return new MinMagFilter();
		}
	};
	
	private String label;
	private Class<? extends SourceFilter> filterClass;

	private SourceFilters(Class<? extends SourceFilter> filterClass, String label) {
		this.filterClass = filterClass;
		this.label = label;
	}
	
	@Override
	public String toString() {
		return label;
	}

	public abstract SourceFilter initFilter();
	
	public Class<? extends SourceFilter> getFilterClass() {
		return filterClass;
	}

}
