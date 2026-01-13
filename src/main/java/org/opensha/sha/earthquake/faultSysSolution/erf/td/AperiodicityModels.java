package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumMap;
import java.util.EnumSet;

import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.util.TectonicRegionType;

public enum AperiodicityModels {
	
	UCERF3_LOW("UCERF3 Low (0.4,0.3,0.2,0.1)") {
		private double[] aperValues = new double[] {0.4,0.3,0.2,0.1};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public AperiodicityModel instance(FaultSystemSolution fltSysSolution) {
			return new AperiodicityModel.MagnitudeBinned(fltSysSolution.getRupSet(), aperValues, aperMagBoundaries);
		}
	},
	UCERF3_MIDDLE("UCERF3 Middle (0.5,0.4,0.3,0.2)") {
		private double[] aperValues = new double[] {0.5,0.4,0.3,0.2};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public AperiodicityModel instance(FaultSystemSolution fltSysSolution) {
			return new AperiodicityModel.MagnitudeBinned(fltSysSolution.getRupSet(), aperValues, aperMagBoundaries);
		}
	},
	UCERF3_HIGH("UCERF3 High (0.6,0.5,0.4,0.3)") {
		private double[] aperValues = new double[] {0.6,0.5,0.4,0.3};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public AperiodicityModel instance(FaultSystemSolution fltSysSolution) {
			return new AperiodicityModel.MagnitudeBinned(fltSysSolution.getRupSet(), aperValues, aperMagBoundaries);
		}
	},
	NSHM26_MIDDLE("NSHM (2026) Middle") {
		@Override
		public AperiodicityModel instance(FaultSystemSolution fltSysSolution) {
			// TODO. this is an example stub
			// these could, e.g., be magnitude-dependent
			EnumMap<TectonicRegionType, AperiodicityModel> models = new EnumMap<>(TectonicRegionType.class);
			models.put(TectonicRegionType.ACTIVE_SHALLOW, new AperiodicityModel.SingleValued(0.4, false));
			models.put(TectonicRegionType.STABLE_SHALLOW, new AperiodicityModel.SingleValued(0.4, false));
			models.put(TectonicRegionType.SUBDUCTION_INTERFACE, new AperiodicityModel.SingleValued(0.3, false));
			// used if a rupture of an unexpected or unknown TRT is passed in
			AperiodicityModel fallback = models.get(TectonicRegionType.ACTIVE_SHALLOW);
			// they could also have shared adjustable params
			ParameterList params = null;
			return new AperiodicityModel.TRT_Dependent(fltSysSolution.getRupSet(),
					models, fallback, params);
		}
	},
	SINGLE_VALUED("Single Value") {
		@Override
		public AperiodicityModel instance(FaultSystemSolution fltSysSolution) {
			return new AperiodicityModel.SingleValued(0.3, true); // true -> adjustable
		}
	};
	
	public static EnumSet<AperiodicityModels> UCERF3_MODELS = EnumSet.of(UCERF3_LOW, UCERF3_MIDDLE, UCERF3_HIGH);
	public static EnumSet<AperiodicityModels> NSHM26_MODELS = EnumSet.of(NSHM26_MIDDLE);
	
	private String name;

	private AperiodicityModels(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
	
	public abstract AperiodicityModel instance(FaultSystemSolution fltSysSolution);
	
	public static final String PARAM_NAME = "Aperiodicity Model";
	
	public static EnumParameterizedModelarameter<AperiodicityModels, AperiodicityModel> buildParameter(
			FaultSystemSolution fltSysSolution, EnumSet<AperiodicityModels> choices, AperiodicityModels defaultValue) {
		return new EnumParameterizedModelarameter<AperiodicityModels, AperiodicityModel>(
				PARAM_NAME, choices, defaultValue, true, e -> e.instance(fltSysSolution));
	}

}
