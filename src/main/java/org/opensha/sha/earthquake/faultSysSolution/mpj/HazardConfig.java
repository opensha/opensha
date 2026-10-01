package org.opensha.sha.earthquake.faultSysSolution.mpj;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.erf.FSS_ERF_Config;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenIntervals;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.imr.AttenRelRef;

import com.google.common.base.Preconditions;

public final class HazardConfig {

	private final IncludeBackgroundOption backgroundOption;
	private final List<AttenRelRef> gmpes;
	private final GriddedRegion region;
	private final Double gridSpacing;
	private final List<Site> sites;
	private final Double vs30;
	private final Double sigmaTruncation;
	private final Double maxDistance;
	private final boolean disablePointOptimizations;
	private final boolean useNSHMP_IMLs;
	private final boolean supersample;
	private final boolean writeCurves;
	private final double[] periods;
	private final boolean useInversionJobTime;
	private final Double minutesPerBranch;
	private final FSS_ERF_Config erfConfig;

	private HazardConfig(Builder builder) {
		this.backgroundOption = builder.backgroundOption;
		this.gmpes = List.copyOf(builder.gmpes);
		this.region = builder.region;
		this.gridSpacing = builder.gridSpacing;
		this.sites = List.copyOf(builder.sites);
		this.vs30 = builder.vs30;
		this.sigmaTruncation = builder.sigmaTruncation;
		this.maxDistance = builder.maxDistance;
		this.disablePointOptimizations = builder.disablePointOptimizations;
		this.useNSHMP_IMLs = builder.useNSHMP_IMLs;
		this.supersample = builder.supersample;
		this.writeCurves = builder.writeCurves;
		this.periods = builder.periods == null ? null : builder.periods.clone();
		this.useInversionJobTime = builder.useInversionJobTime;
		this.minutesPerBranch = builder.minutesPerBranch;
		this.erfConfig = builder.erfConfigBuilder.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public IncludeBackgroundOption backgroundOption() {
		return backgroundOption;
	}

	public List<AttenRelRef> gmpes() {
		return gmpes;
	}

	public GriddedRegion region() {
		return region;
	}

	public Double gridSpacing() {
		return gridSpacing;
	}

	public List<Site> sites() {
		return sites;
	}

	public Double vs30() {
		return vs30;
	}

	public Double sigmaTruncation() {
		return sigmaTruncation;
	}

	public Double maxDistance() {
		return maxDistance;
	}

	public boolean disablePointOptimizations() {
		return disablePointOptimizations;
	}

	public boolean useNSHMP_IMLs() {
		return useNSHMP_IMLs;
	}

	public boolean supersample() {
		return supersample;
	}

	public boolean writeCurves() {
		return writeCurves;
	}

	public double[] periods() {
		return periods == null ? null : periods.clone();
	}

	public boolean useInversionJobTime() {
		return useInversionJobTime;
	}

	public Double minutesPerBranch() {
		return minutesPerBranch;
	}

	public FSS_ERF_Config erfConfig() {
		return erfConfig;
	}
	
	public static void addOptions(Options ops) {
		ops.addOption(null, "hazard-gridded-seis", true, "Hazard gridded seismicity option, one of: "
				+FaultSysTools.enumOptions(IncludeBackgroundOption.class));
		ops.addOption(null, "hazard-gridded-region", true, "Path to gridded region GeoJSON file");
		ops.addOption(null, "hazard-grid-spacing", true, "Hazard grid spacing");
		ops.addOption(null, "gmpe", true, "GMPE reference names, can supply multiple times. If supplied, any previously set GMPEs will be removed.");
		ops.addOption(null, "vs30", true, "Hazard Vs30 override.");
		ops.addOption(null, "sigma-trunc", true,
				"Hazard sigma truncation override; supply 'null' to disable it.");
		ops.addOption(null, "max-distance", true, "Hazard maximum source-site distance in km.");
		ops.addOption(null, "disable-point-optimizations", false, "Disable point source optimizations.");
		ops.addOption(null, "nshmp-imls", false, "Use NSHMP period-dependent IMLs.");
		ops.addOption(null, "supersample", false, "Enable hazard supersampling.");
		ops.addOption(null, "no-supersample", false, "Disable hazard supersampling.");
		ops.addOption(null, "write-hazard-curves", false,
				"Write a separate hazard curve archive containing curves for every logic-tree branch.");
		ops.addOption(null, "periods", true, "Comma-separated hazard periods, e.g., 0,0.2,1");
		ops.addOption(null, "hazard-time-same-as-inversion", false,
				"Use the computed inversion job wall time for the main hazard job.");
		ops.addOption(null, "hazard-minutes-per-branch", true,
				"Estimated main hazard calculation time per logic-tree branch in minutes.");
		ops.addOption(null, "hazard-duration", true, "Hazard forecast duration in years. Default: 1");
		ops.addOption(null, "hazard-prob-model", true, "Hazard fault-system probability model, one of: "
				+FaultSysTools.enumOptions(FSS_ProbabilityModels.class));
		ops.addOption(null, "hazard-start-year", true,
				"Hazard forecast start year; required for a non-Poisson probability model.");
		ops.addOption(null, "hazard-renewal-model", true, "Hazard renewal model distribution, one of: "
				+FaultSysTools.enumOptions(RenewalModels.class));
		ops.addOption(null, "hazard-aperiodicity-model", true, "Hazard aperiodicity model, one of: "
				+FaultSysTools.enumOptions(AperiodicityModels.class));
		ops.addOption(null, "hazard-aperiodicity-value", true,
				"Hazard aperiodicity value; implies --hazard-aperiodicity-model SINGLE_VALUED.");
		ops.addOption(null, "hazard-hist-open-interval", true, "Hazard historical open interval model, one of: "
				+FaultSysTools.enumOptions(HistoricalOpenIntervals.class));
		ops.addOption(null, "hazard-hist-open-interval-year", true,
				"Hazard historical open interval start year; implies --hazard-hist-open-interval SINGLE_YEAR.");
		ops.addOption(null, "hazard-aseis-reduces-area", false,
				"Enable aseismicity area reductions in hazard calculations (default).");
		ops.addOption(null, "hazard-no-aseis-reduces-area", false,
				"Disable aseismicity area reductions in hazard calculations.");
		ops.addOption(null, "hazard-no-mfds", false, "Disable rupture MFDs in hazard calculations.");
		ops.addOption(null, "hazard-no-proxy-ruptures", false, "Disable proxy ruptures in hazard calculations.");
	}

	public static final class Builder {
		private IncludeBackgroundOption backgroundOption = IncludeBackgroundOption.EXCLUDE;
		private final List<AttenRelRef> gmpes = new ArrayList<>();
		private GriddedRegion region;
		private Double gridSpacing;
		private final List<Site> sites = new ArrayList<>();
		private Double vs30;
		private Double sigmaTruncation;
		private Double maxDistance;
		private boolean disablePointOptimizations;
		private boolean useNSHMP_IMLs;
		private boolean supersample;
		private boolean writeCurves;
		private double[] periods;
		private boolean useInversionJobTime;
		private Double minutesPerBranch;
		private FSS_ERF_Config.Builder erfConfigBuilder = FSS_ERF_Config.builder();
		
		public Builder forCMD(CommandLine cmd) {
			Preconditions.checkArgument(!(cmd.hasOption("hazard-time-same-as-inversion")
					&& cmd.hasOption("hazard-minutes-per-branch")),
					"cannot supply both --hazard-time-same-as-inversion and --hazard-minutes-per-branch");
			if (cmd.hasOption("hazard-gridded-seis"))
				backgroundOption = IncludeBackgroundOption.valueOf(cmd.getOptionValue("hazard-gridded-seis"));
			if (cmd.hasOption("hazard-gridded-region")) {
				try {
					region = GriddedRegion.fromFeature(Feature.read(new File(cmd.getOptionValue("hazard-gridded-region"))));
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			if (cmd.hasOption("hazard-grid-spacing"))
				gridSpacing = Double.parseDouble(cmd.getOptionValue("hazard-grid-spacing"));
			if (cmd.hasOption("gmpe")) {
				gmpes.clear();
				for (String name : cmd.getOptionValues("gmpe")) {
					gmpe(AttenRelRef.valueOf(name));
				}
			}
			if (cmd.hasOption("vs30"))
				vs30 = Double.parseDouble(cmd.getOptionValue("vs30"));
			if (cmd.hasOption("sigma-trunc")) {
				String sigmaTruncStr = cmd.getOptionValue("sigma-trunc");
				if ("null".equalsIgnoreCase(sigmaTruncStr))
					sigmaTruncation = null;
				else
					sigmaTruncation = Double.parseDouble(sigmaTruncStr);
			}
			if (cmd.hasOption("max-distance"))
				maxDistance = Double.parseDouble(cmd.getOptionValue("max-distance"));
			if (cmd.hasOption("disable-point-optimizations"))
				disablePointOptimizations = true;
			if (cmd.hasOption("nshmp-imls"))
				useNSHMP_IMLs = true;
			if (cmd.hasOption("supersample"))
				supersample = true;
			if (cmd.hasOption("no-supersample"))
				supersample = false;
			if (cmd.hasOption("write-hazard-curves"))
				writeCurves = true;
			if (cmd.hasOption("periods")) {
				String[] split = cmd.getOptionValue("periods").split(",");
				double[] parsed = new double[split.length];
				for (int i=0; i<split.length; i++)
					parsed[i] = Double.parseDouble(split[i].trim());
				periods = parsed;
			}
			if (cmd.hasOption("hazard-time-same-as-inversion"))
				useInversionJobTime(true);
			else if (cmd.hasOption("hazard-minutes-per-branch"))
				minutesPerBranch(Double.parseDouble(cmd.getOptionValue("hazard-minutes-per-branch")));
			if (cmd.hasOption("hazard-duration"))
				durationYears(Double.parseDouble(cmd.getOptionValue("hazard-duration")));
			if (cmd.hasOption("hazard-prob-model"))
				probabilityModel(FSS_ProbabilityModels.valueOf(
						cmd.getOptionValue("hazard-prob-model").trim().toUpperCase()));
			if (cmd.hasOption("hazard-start-year"))
				startYear(Integer.parseInt(cmd.getOptionValue("hazard-start-year")));
			if (cmd.hasOption("hazard-renewal-model"))
				renewalModel(RenewalModels.valueOf(
						cmd.getOptionValue("hazard-renewal-model").trim().toUpperCase()));
			if (cmd.hasOption("hazard-aperiodicity-model"))
				aperiodicityModel(AperiodicityModels.valueOf(
						cmd.getOptionValue("hazard-aperiodicity-model").trim().toUpperCase()));
			if (cmd.hasOption("hazard-aperiodicity-value"))
				aperiodicityValue(Double.parseDouble(cmd.getOptionValue("hazard-aperiodicity-value")));
			if (cmd.hasOption("hazard-hist-open-interval"))
				historicalOpenInterval(HistoricalOpenIntervals.valueOf(
						cmd.getOptionValue("hazard-hist-open-interval").trim().toUpperCase()));
			if (cmd.hasOption("hazard-hist-open-interval-year"))
				historicalOpenIntervalYear(Integer.parseInt(
						cmd.getOptionValue("hazard-hist-open-interval-year")));
			Preconditions.checkArgument(!(cmd.hasOption("hazard-aseis-reduces-area")
					&& cmd.hasOption("hazard-no-aseis-reduces-area")),
					"cannot both enable and disable aseismicity area reductions");
			if (cmd.hasOption("hazard-aseis-reduces-area") || cmd.hasOption("hazard-no-aseis-reduces-area"))
				aseisReducesArea(cmd.hasOption("hazard-aseis-reduces-area"));
			if (cmd.hasOption("hazard-no-mfds"))
				useRupMFDs(false);
			if (cmd.hasOption("hazard-no-proxy-ruptures"))
				useProxyRuptures(false);
			return this;
		}

		public Builder backgroundOption(IncludeBackgroundOption backgroundOption) {
			this.backgroundOption = backgroundOption;
			return this;
		}

		public Builder gmpe(AttenRelRef gmpe) {
			if (gmpe != null)
				gmpes.add(gmpe);
			return this;
		}

		public Builder gmpes(Collection<AttenRelRef> gmpes) {
			if (gmpes != null)
				this.gmpes.addAll(gmpes);
			return this;
		}

		public Builder region(GriddedRegion region) {
			this.region = region;
			return this;
		}

		public Builder gridSpacing(Double gridSpacing) {
			this.gridSpacing = gridSpacing;
			return this;
		}

		public Builder site(Site site) {
			if (site != null)
				sites.add(site);
			return this;
		}

		public Builder sites(Collection<Site> sites) {
			if (sites != null)
				this.sites.addAll(sites);
			return this;
		}

		public Builder vs30(Double vs30) {
			this.vs30 = vs30;
			return this;
		}

		public Builder sigmaTruncation(Double sigmaTruncation) {
			this.sigmaTruncation = sigmaTruncation;
			return this;
		}

		public Builder maxDistance(Double maxDistance) {
			this.maxDistance = maxDistance;
			return this;
		}

		public Builder disablePointOptimizations(boolean disablePointOptimizations) {
			this.disablePointOptimizations = disablePointOptimizations;
			return this;
		}

		public Builder setUseNSHMP_IMLs(boolean useNSHMP_IMLs) {
			this.useNSHMP_IMLs = useNSHMP_IMLs;
			return this;
		}

		public Builder supersample(boolean supersample) {
			this.supersample = supersample;
			return this;
		}

		public Builder writeCurves(boolean writeCurves) {
			this.writeCurves = writeCurves;
			return this;
		}

		public Builder periods(double... periods) {
			this.periods = periods == null ? null : periods.clone();
			return this;
		}

		public Builder useInversionJobTime(boolean useInversionJobTime) {
			this.useInversionJobTime = useInversionJobTime;
			if (useInversionJobTime)
				this.minutesPerBranch = null;
			return this;
		}

		public Builder minutesPerBranch(Double minutesPerBranch) {
			this.minutesPerBranch = minutesPerBranch;
			if (minutesPerBranch != null)
				this.useInversionJobTime = false;
			return this;
		}

		public Builder durationYears(double durationYears) {
			erfConfigBuilder.durationYears(durationYears);
			return this;
		}

		public Builder probabilityModel(FSS_ProbabilityModels probabilityModel) {
			erfConfigBuilder.probabilityModel(probabilityModel);
			return this;
		}

		public Builder startYear(Integer startYear) {
			erfConfigBuilder.startYear(startYear);
			return this;
		}

		public Builder renewalModel(RenewalModels renewalModel) {
			erfConfigBuilder.renewalModel(renewalModel);
			return this;
		}

		public Builder aperiodicityModel(AperiodicityModels aperiodicityModel) {
			erfConfigBuilder.aperiodicityModel(aperiodicityModel);
			return this;
		}

		public Builder aperiodicityValue(Double aperiodicityValue) {
			erfConfigBuilder.aperiodicityValue(aperiodicityValue);
			return this;
		}

		public Builder historicalOpenInterval(HistoricalOpenIntervals historicalOpenInterval) {
			erfConfigBuilder.historicalOpenInterval(historicalOpenInterval);
			return this;
		}

		public Builder historicalOpenIntervalYear(Integer historicalOpenIntervalYear) {
			erfConfigBuilder.historicalOpenIntervalYear(historicalOpenIntervalYear);
			return this;
		}

		public Builder aseisReducesArea(boolean aseisReducesArea) {
			erfConfigBuilder.aseisReducesArea(aseisReducesArea);
			return this;
		}

		public Builder useRupMFDs(boolean useRupMFDs) {
			erfConfigBuilder.useRupMFDs(useRupMFDs);
			return this;
		}

		public Builder useProxyRuptures(boolean useProxyRuptures) {
			erfConfigBuilder.useProxyRuptures(useProxyRuptures);
			return this;
		}

		public Builder erfConfig(FSS_ERF_Config erfConfig) {
			erfConfigBuilder = Preconditions.checkNotNull(erfConfig).toBuilder();
			return this;
		}

		public HazardConfig build() {
			Preconditions.checkArgument(minutesPerBranch == null
					|| (Double.isFinite(minutesPerBranch) && minutesPerBranch > 0d),
					"minutesPerBranch must be finite and > 0");
			return new HazardConfig(this);
		}
	}
}
