package gov.usgs.earthquake.nshmp.erf.mpj;

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
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.imr.AttenRelRef;

public final class HazardConfig {

	private final IncludeBackgroundOption backgroundOption;
	private final List<AttenRelRef> gmpes;
	private final GriddedRegion region;
	private final Double gridSpacing;
	private final List<Site> sites;
	private final Double vs30;
	private final Double sigmaTruncation;
	private final boolean supersample;
	private final double[] periods;

	private HazardConfig(Builder builder) {
		this.backgroundOption = builder.backgroundOption;
		this.gmpes = List.copyOf(builder.gmpes);
		this.region = builder.region;
		this.gridSpacing = builder.gridSpacing;
		this.sites = List.copyOf(builder.sites);
		this.vs30 = builder.vs30;
		this.sigmaTruncation = builder.sigmaTruncation;
		this.supersample = builder.supersample;
		this.periods = builder.periods == null ? null : builder.periods.clone();
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

	public boolean supersample() {
		return supersample;
	}

	public double[] periods() {
		return periods == null ? null : periods.clone();
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
		ops.addOption(null, "supersample", false, "Enable hazard supersampling.");
		ops.addOption(null, "no-supersample", false, "Disable hazard supersampling.");
		ops.addOption(null, "periods", true, "Comma-separated hazard periods, e.g., 0,0.2,1");
	}

	public static final class Builder {
		private IncludeBackgroundOption backgroundOption = IncludeBackgroundOption.EXCLUDE;
		private final List<AttenRelRef> gmpes = new ArrayList<>();
		private GriddedRegion region;
		private Double gridSpacing;
		private final List<Site> sites = new ArrayList<>();
		private Double vs30;
		private Double sigmaTruncation;
		private boolean supersample;
		private double[] periods;
		
		public Builder forCMD(CommandLine cmd) {
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
			if (cmd.hasOption("supersample"))
				supersample = true;
			if (cmd.hasOption("no-supersample"))
				supersample = false;
			if (cmd.hasOption("periods")) {
				String[] split = cmd.getOptionValue("periods").split(",");
				double[] parsed = new double[split.length];
				for (int i=0; i<split.length; i++)
					parsed[i] = Double.parseDouble(split[i].trim());
				periods = parsed;
			}
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

		public Builder supersample(boolean supersample) {
			this.supersample = supersample;
			return this;
		}

		public Builder periods(double... periods) {
			this.periods = periods == null ? null : periods.clone();
			return this;
		}

		public HazardConfig build() {
			return new HazardConfig(this);
		}
	}
}
