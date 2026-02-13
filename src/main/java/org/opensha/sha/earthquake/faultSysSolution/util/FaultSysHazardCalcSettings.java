package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.WarningParameter;
import org.opensha.sha.calc.sourceFilters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter.TectonicRegionDistanceCutoffs;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class FaultSysHazardCalcSettings {
	
	static AttenRelRef CRUSTAL_GMPE_DEFAULT = AttenRelRef.WRAPPED_ASK_2014;
	static AttenRelRef STABLE_GMPE_DEFAULT = AttenRelRef.ASK_2014; // TODO
	static AttenRelRef INTERFACE_GMPE_DEFAULT = AttenRelRef.PSBAH_2020_GLOBAL_INTERFACE;
	static AttenRelRef SLAB_GMPE_DEFAULT = AttenRelRef.PSBAH_2020_GLOBAL_SLAB;
	
	public static Map<TectonicRegionType, AttenRelSupplier> getGMM_Suppliers(
			LogicTreeBranch<?> branch, Map<TectonicRegionType, AttenRelSupplier> upstream,
			boolean paramOverridesFromUpstream) {
		if (branch == null)
			return upstream;
		
		Map<TectonicRegionType, AttenRelSupplier> suppliers;
		if (branch.hasValue(ScalarIMRsLogicTreeNode.class)) {
			List<ScalarIMRsLogicTreeNode> gmmNodes = branch.getValues(ScalarIMRsLogicTreeNode.class);
			suppliers = new EnumMap<>(TectonicRegionType.class);
			for (ScalarIMRsLogicTreeNode gmmNode : gmmNodes) {
				Map<TectonicRegionType, AttenRelSupplier> nodeSuppliers = gmmNode.getSuppliers();
				for (TectonicRegionType trt : nodeSuppliers.keySet()) {
					Preconditions.checkState(!suppliers.containsKey(trt),
							"Multiple ScalarIMRsLogicTreeNode's supply GMMs for %s", trt);
					System.out.println("Applying branch-specific GMM supplier for trt="+trt+":\t"+gmmNode.getName());
					AttenRelSupplier supplier = nodeSuppliers.get(trt);
					if (paramOverridesFromUpstream) {
						AttenRelSupplier myUpstream = upstream.get(trt);
						if (myUpstream != null && myUpstream instanceof ParamOverrideSupplier) {
							Map<String, Object> overrides = ((ParamOverrideSupplier)myUpstream).parameterOverrides;
							System.out.println("\tCopying upstream parameter overrides ("+overrides.size()
									+" params) to branch-specific GMM supplier for trt="+trt);
							System.out.println("");
							supplier = new ParamOverrideSupplier(supplier, overrides);
						}
					}
					suppliers.put(trt, supplier);
				}
			}
		} else {
			suppliers = new EnumMap<>(upstream);
		}
		
		// now see if we have any applicable GMM parameter logic tree branches
		List<ScalarIMR_ParamsLogicTreeNode> paramsNodes = null;
		for (int i=0; i<branch.size(); i++) {
			LogicTreeNode node = branch.getValue(i);
			if (node instanceof ScalarIMR_ParamsLogicTreeNode) {
				if (paramsNodes == null)
					paramsNodes = new ArrayList<>();
				paramsNodes.add((ScalarIMR_ParamsLogicTreeNode)node);
			}
		}
		
		for (TectonicRegionType trt : List.copyOf(suppliers.keySet())) { // wrap in a list as we will be modifying the map
			AttenRelSupplier origSupplier = suppliers.get(trt);
			AttenRelSupplier supplier = origSupplier;
			
			if (paramsNodes != null)
				// wrap with node parameter overrides
				suppliers.put(trt, new NodeParamOverrideSupplier(supplier, paramsNodes));
		}
		
		return suppliers;
	}
	
	public static class NodeParamOverrideSupplier implements AttenRelSupplier {
		
		private AttenRelSupplier supplier;
		private Collection<ScalarIMR_ParamsLogicTreeNode> params;

		public NodeParamOverrideSupplier(AttenRelSupplier supplier, ScalarIMR_ParamsLogicTreeNode params) {
			this(supplier, List.of(params));
		}
		
		public NodeParamOverrideSupplier(AttenRelSupplier supplier, Collection<ScalarIMR_ParamsLogicTreeNode> params) {
			this.supplier = supplier;
			this.params = params;
		}

		@Override
		public ScalarIMR get() {
			ScalarIMR gmm = supplier.get();
			for (ScalarIMR_ParamsLogicTreeNode myParams : params)
				if (myParams.isApplicableTo(gmm))
					myParams.setParams(gmm);
			return gmm;
		}

		@Override
		public String getShortName() {
			return supplier.getShortName()+"-NodeParamOverride";
		}
	
		@Override
		public String getName() {
			return supplier.getName()+" (Node Param Override)";
		}
		
	}

	public static class ParamOverrideSupplier implements AttenRelSupplier {
		
		private AttenRelSupplier supplier;
		private Map<String, Object> parameterOverrides;
	
		public ParamOverrideSupplier(AttenRelSupplier supplier, Map<String, Object> parameterOverrides) {
			this.supplier = supplier;
			this.parameterOverrides = parameterOverrides;
		}
		
		public boolean matches(Map<String, Object> parameterOverrides) {
			return this.parameterOverrides.equals(parameterOverrides);
		}
	
		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public ScalarIMR get() {
			ScalarIMR gmm = supplier.get();
			for (String paramName : parameterOverrides.keySet()) {
				Parameter param = gmm.getParameter(paramName);
				Object value = parameterOverrides.get(paramName);
				if (param instanceof WarningParameter<?>)
					((WarningParameter)param).setValueIgnoreWarning(value);
				else
					param.setValue(value);
			}
			return gmm;
		}
	
		@Override
		public String getShortName() {
			return supplier.getShortName()+"-ParamOverride";
		}
	
		@Override
		public String getName() {
			return supplier.getName()+" (Param Override)";
		}
		
	}

	public static Map<TectonicRegionType, AttenRelSupplier> getGMMs(CommandLine cmd) {
		Map<TectonicRegionType, AttenRelSupplier> ret = new EnumMap<>(TectonicRegionType.class);
		if (cmd.hasOption("gmpe")) {
			Preconditions.checkState(!cmd.hasOption("trt-gmpe"), "Can't specify both --gmpe and --trt-gmpe");
			String[] gmmStrs = cmd.getOptionValues("gmpe");
			for (String gmmStr : gmmStrs) {
				AttenRelRef gmpeRef = AttenRelRef.valueOf(gmmStr);
				if (gmmStrs.length > 1) {
					// TRT specific
					TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam)gmpeRef.get().getParameter(TectonicRegionTypeParam.NAME);
					Preconditions.checkState(trtParam != null, "Multiple GMPEs supplied, but GMPE "+gmpeRef.getShortName()+" doesn't have a TRT");
					TectonicRegionType trt = trtParam.getValueAsTRT();
					ret.put(trt, gmpeRef);
				} else {
					// single, just use ACTIVE_SHALLOW (will be used for all)
					ret.put(TectonicRegionType.ACTIVE_SHALLOW, gmpeRef);
				}
			}
		} else {
			ret.putAll(FaultSysHazardCalcSettings.getDefaultGMMs());
			if (cmd.hasOption("trt-gmpe")) {
				for (String val : cmd.getOptionValues("gmmRefs")) {
					Preconditions.checkState(val.contains(":"), "Expected <trt>:<gmm>, can't parse argument: %s", val);
					int index = val.indexOf(":");
					String trtName = val.substring(0, index);
					TectonicRegionType trt = TectonicRegionType.valueOf(trtName);
					String gmmName = val.substring(index+1);
					AttenRelRef gmm = AttenRelRef.valueOf(gmmName);
					ret.put(trt, gmm);
				}
			}
		}
		
		Map<String, Object> parameterOverrides = FaultSysHazardCalcSettings.getGMM_ParamOverrides(cmd);
		
		if (!parameterOverrides.isEmpty()) {
			List<TectonicRegionType> trts = List.copyOf(ret.keySet());
			for (TectonicRegionType trt : trts) {
				AttenRelSupplier supplier = ret.get(trt);
				ret.put(trt, new org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings.ParamOverrideSupplier(supplier, parameterOverrides));
			}
		}
		
		return ret;
	}

	public static Map<String, Object> getGMM_ParamOverrides(CommandLine cmd) {
		Map<String, Object> parameterOverrides = new LinkedHashMap<>(); // linked preserves order, which could be important
		
		if (cmd.hasOption("vs30")) {
			double vs30 = Double.parseDouble(cmd.getOptionValue("vs30"));
			System.out.println("Setting GMM Vs30="+(float)vs30);
			parameterOverrides.put(Vs30_Param.NAME, vs30);
		}
		
		if (cmd.hasOption("gmm-sigma-trunc-one-sided") || cmd.hasOption("gmm-sigma-trunc-two-sided")) {
			double sigma;
			boolean twoSided;
			if (cmd.hasOption("gmm-sigma-trunc-one-sided")) {
				Preconditions.checkState(!cmd.hasOption("gmm-sigma-trunc-two-sided"), "can't enable both one- and two-sided truncation");
				sigma = Double.parseDouble(cmd.getOptionValue("gmm-sigma-trunc-one-sided"));
				twoSided = false;
				System.out.println("Enabling GMM one-sided sigma truncation at "+(float)sigma+" sigma");
			} else {
				sigma = Double.parseDouble(cmd.getOptionValue("gmm-sigma-trunc-two-sided"));
				twoSided = true;
				System.out.println("Enabling GMM two-sided sigma truncation at "+(float)sigma+" sigma");
			}
			parameterOverrides.put(SigmaTruncTypeParam.NAME, twoSided ?
					SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_2SIDED : SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
			parameterOverrides.put(SigmaTruncLevelParam.NAME, sigma);
		}
		return parameterOverrides;
	}

	public static Map<TectonicRegionType, AttenRelRef> getDefaultGMMs() {
		EnumMap<TectonicRegionType, AttenRelRef> ret = new EnumMap<>(TectonicRegionType.class);
		ret.put(TectonicRegionType.ACTIVE_SHALLOW, CRUSTAL_GMPE_DEFAULT);
		ret.put(TectonicRegionType.STABLE_SHALLOW, STABLE_GMPE_DEFAULT);
		ret.put(TectonicRegionType.SUBDUCTION_INTERFACE, INTERFACE_GMPE_DEFAULT);
		ret.put(TectonicRegionType.SUBDUCTION_SLAB, SLAB_GMPE_DEFAULT);
		return ret;
	}

	public static Map<TectonicRegionType, AttenRelSupplier> wrapInTRTMap(AttenRelSupplier gmpeRef) {
		if (gmpeRef == null)
			return null;
		EnumMap<TectonicRegionType, AttenRelSupplier> ret = new EnumMap<>(TectonicRegionType.class);
		ret.put(TectonicRegionType.ACTIVE_SHALLOW, gmpeRef);
		return ret;
	}

	public static Map<TectonicRegionType, Supplier<ScalarIMR>> wrapInTRTMap(Supplier<ScalarIMR> gmpeRef) {
		if (gmpeRef == null)
			return null;
		EnumMap<TectonicRegionType, Supplier<ScalarIMR>> ret = new EnumMap<>(TectonicRegionType.class);
		ret.put(TectonicRegionType.ACTIVE_SHALLOW, gmpeRef);
		return ret;
	}

	public static SourceFilterManager getDefaultSourceFilters() {
		SourceFilterManager sourceFilters = new SourceFilterManager(SourceFilters.TRT_DIST_CUTOFFS);
		return sourceFilters;
	}

	public static SourceFilterManager getSourceFilters(CommandLine cmd) {
		SourceFilterManager sourceFilters;
		if (cmd.hasOption("max-distance")) {
			sourceFilters = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
			double maxDist = Double.parseDouble(cmd.getOptionValue("max-distance"));
			((FixedDistanceCutoffFilter)sourceFilters.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF)).setMaxDistance(maxDist);
		} else {
			sourceFilters = getDefaultSourceFilters();
		}
		return sourceFilters;
	}

	public static SourceFilterManager getDefaultSiteSkipSourceFilters(SourceFilterManager sourceFilters) {
		SourceFilterManager ret = null;
		if (sourceFilters.isEnabled(SourceFilters.TRT_DIST_CUTOFFS)) {
			TectonicRegionDistCutoffFilter fullFilter = (TectonicRegionDistCutoffFilter)
					sourceFilters.getFilterInstance(SourceFilters.TRT_DIST_CUTOFFS);
			TectonicRegionDistanceCutoffs fullCutoffs = fullFilter.getCutoffs();
			ret = new SourceFilterManager(SourceFilters.TRT_DIST_CUTOFFS);
			TectonicRegionDistCutoffFilter skipFilter = (TectonicRegionDistCutoffFilter)
					ret.getFilterInstance(SourceFilters.TRT_DIST_CUTOFFS);
			TectonicRegionDistanceCutoffs skipCutoffs = skipFilter.getCutoffs();
			for (TectonicRegionType trt : TectonicRegionType.values())
				skipCutoffs.setCutoffDist(trt, fullCutoffs.getCutoffDist(trt)*FaultSysHazardCalcSettings.SITE_SKIP_FRACT);
		}
		if (sourceFilters.isEnabled(SourceFilters.FIXED_DIST_CUTOFF)) {
			if (ret == null)
				ret = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
			else
				ret.setEnabled(SourceFilters.FIXED_DIST_CUTOFF, true);
			FixedDistanceCutoffFilter fullFilter = (FixedDistanceCutoffFilter)sourceFilters.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF);
			FixedDistanceCutoffFilter skipFilter = (FixedDistanceCutoffFilter)ret.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF);
			skipFilter.setMaxDistance(fullFilter.getMaxDistance()*FaultSysHazardCalcSettings.SITE_SKIP_FRACT);
		}
		return ret;
	}

	public static SourceFilterManager getSiteSkipSourceFilters(SourceFilterManager sourceFilters, CommandLine cmd) {
		SourceFilterManager siteSkipSourceFilters;
		if (cmd.hasOption("skip-max-distance")) {
			siteSkipSourceFilters = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
			double maxDist = Double.parseDouble(cmd.getOptionValue("skip-max-distance"));
			((FixedDistanceCutoffFilter)siteSkipSourceFilters.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF)).setMaxDistance(maxDist);
		} else {
			siteSkipSourceFilters = getDefaultSiteSkipSourceFilters(sourceFilters);
		}
		return siteSkipSourceFilters;
	}

	static final SourceFilterManager SOURCE_FILTER_DEFAULT = new SourceFilterManager(SourceFilters.TRT_DIST_CUTOFFS);

	public static Map<TectonicRegionType, ScalarIMR> getGmmInstances(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeRefMap) {
		EnumMap<TectonicRegionType, ScalarIMR> ret = new EnumMap<>(TectonicRegionType.class);
		for (TectonicRegionType trt : gmpeRefMap.keySet())
			ret.put(trt, gmpeRefMap.get(trt).get());
		return ret;
	}

	public static ParameterList getDefaultRefSiteParams(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeRefMap) {
		return FaultSysHazardCalcSettings.getDefaultSiteParams(getGmmInstances(gmpeRefMap));
	}

	public static ParameterList getDefaultSiteParams(Map<TectonicRegionType, ScalarIMR> gmpeMap) {
		if (gmpeMap.size() == 1) {
			return gmpeMap.values().iterator().next().getSiteParams();
		} else {
			ParameterList siteParams = new ParameterList();
			for (ScalarIMR gmpe: gmpeMap.values()) {
				for (Parameter<?> param : gmpe.getSiteParams()) {
					if (!siteParams.containsParameter(param.getName()))
						siteParams.addParameter(param);
				}
			}
			return siteParams;
		}
	}

	public static DiscretizedFunc getDefaultXVals(double period) {
		return FaultSysHazardCalcSettings.getDefaultXVals(new IMT_Info(), period);
	}

	public static DiscretizedFunc getDefaultXVals(IMT_Info imtInfo, double period) {
		if (period == -1d)
			return imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
		else if (period == 0d)
			return imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
		else
			return imtInfo.getDefaultHazardCurve(SA_Param.NAME);
	}

	public static void setIMforPeriod(Map<TectonicRegionType, ScalarIMR> gmpeMap, double period) {
		for (ScalarIMR gmpe : gmpeMap.values())
			FaultSysHazardCalcSettings.setIMforPeriod(gmpe, period);
	}

	public static void setIMforPeriod(ScalarIMR gmpe, double period) {
		if (period == -1d) {
			gmpe.setIntensityMeasure(PGV_Param.NAME);
		} else if (period == 0d) {
			gmpe.setIntensityMeasure(PGA_Param.NAME);
		} else {
			Preconditions.checkState(period > 0);
			gmpe.setIntensityMeasure(SA_Param.NAME);
			SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), period);
		}
	}

	public static void addCommonOptions(Options ops, boolean includeSiteSkip) {
		ops.addOption("gm", "gmpe", true, "Sets a single GMPE that will be used for all TectonicRegionTypes. If this is supplied "
				+ "multiple times, then each gmpe must have a TectonicRegionTypeParameter that will be used to determine the GMPE "
				+ "for each TRT. Note that this will be overriden if the Logic Tree supplies GMPE choices. Default is TectonicRegionType-specific.");
		ops.addOption(null, "trt-gmpe", true, "Sets the GMPE for the given TectonicRegionType in the format: <TRT>:<GMM>. "
				+ "For example: ACTIVE_SHALLOW:ASK_2014. Note that this will be overriden if the Logic Tree supplies GMPE choices.");
		ops.addOption("p", "periods", true, "Calculation period(s). Mutliple can be comma separated");
		ops.addOption("md", "max-distance", true, "Maximum source-site distance in km. Default is TectonicRegionType-specific.");
		ops.addOption(null, "vs30", true, "Site Vs30 value (uses GMM default otherwise)");
		ops.addOption(null, "gmm-sigma-trunc-one-sided", true, "Enables one-sided GMM sigma truncation; default is disabled.");
		ops.addOption(null, "gmm-sigma-trunc-two-sided", true, "Enables two-sided GMM sigma truncation; default is disabled.");
		ops.addOption(null, "supersample", false, "Flag to enable grid cell supersampling (default is disabled)");
		ops.addOption(null, "supersample-quick", false, "Flag to enable grid cell supersampling with faster parameters (default is disabled)");
		ops.addOption(null, "dist-corr", true, "Set the point-source distance correction method. Default is "
				+BaseFaultSystemSolutionERF.DIST_CORR_TYPE_DEFAULT.name()+"; options are: "+FaultSysTools.enumOptions(PointSourceDistanceCorrections.class));
		ops.addOption(null, "point-source-type", true, "Sets the point source surface type. Default is "
				+BaseFaultSystemSolutionERF.BG_RUP_TYPE_DEFAULT.name()+"; options are: "+FaultSysTools.enumOptions(BackgroundRupType.class));
		if (includeSiteSkip)
			ops.addOption("smd", "skip-max-distance", true, "Skip sites with no source-site distances below this value, in km. "
					+ "Default is "+(int)(FaultSysHazardCalcSettings.SITE_SKIP_FRACT*100d)+"% of the TectonicRegionType-specific default maximum distance.");
	}

	static final double SITE_SKIP_FRACT = 0.8;

	public static GriddedSeismicitySettings getGridSeisSettings(CommandLine cmd) {
		GriddedSeismicitySettings settings = BaseFaultSystemSolutionERF.GRID_SETTINGS_DEFAULT;
		
		if (cmd.hasOption("supersample"))
			settings = settings.forSupersamplingSettings(GridCellSupersamplingSettings.DEFAULT);
		else if (cmd.hasOption("supersample-quick"))
			settings = settings.forSupersamplingSettings(GridCellSupersamplingSettings.QUICK);
		else
			settings = settings.forSupersamplingSettings(null);
		
		if (cmd.hasOption("dist-corr"))
			settings = settings.forDistanceCorrections(PointSourceDistanceCorrections.valueOf(cmd.getOptionValue("dist-corr")));
		
		if (cmd.hasOption("point-source-type"))
			settings = settings.forSurfaceType(BackgroundRupType.valueOf(cmd.getOptionValue("point-source-type")));
		
		return settings;
	}

}
