package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.util.TectonicRegionType;

public enum AperiodicityModels {
	
	UCERF3_LOW("UCERF3 Low (0.4,0.3,0.2,0.1)") {
		private double[] aperValues = new double[] {0.4,0.3,0.2,0.1};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public double getAperiodicity(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			return getU3Aperiodicity(fltSysSolution.getRupSet().getMagForRup(fltSysRupIndex), aperValues, aperMagBoundaries);
		}
	},
	UCERF3_MIDDLE("UCERF3 Middle (0.5,0.4,0.3,0.2)") {
		private double[] aperValues = new double[] {0.5,0.4,0.3,0.2};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public double getAperiodicity(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			return getU3Aperiodicity(fltSysSolution.getRupSet().getMagForRup(fltSysRupIndex), aperValues, aperMagBoundaries);
		}
	},
	UCERF3_HIGH("UCERF3 High (0.6,0.5,0.4,0.3)") {
		private double[] aperValues = new double[] {0.6,0.5,0.4,0.3};
		private double[] aperMagBoundaries = new double[] {6.7,7.2,7.7};
		@Override
		public double getAperiodicity(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			return getU3Aperiodicity(fltSysSolution.getRupSet().getMagForRup(fltSysRupIndex), aperValues, aperMagBoundaries);
		}
	},
	NSHM26_MIDDLE("NSHM (2026) Middle") {
		@Override
		public double getAperiodicity(FaultSystemSolution fltSysSolution, int fltSysRupIndex) {
			// probably depends on TRT and magnitude
			FaultSystemRupSet rupSet = fltSysSolution.getRupSet();
			RupSetTectonicRegimes trts = rupSet.getModule(RupSetTectonicRegimes.class);
			TectonicRegionType trt = trts == null ? TectonicRegionType.ACTIVE_SHALLOW : trts.get(fltSysRupIndex);
			throw new UnsupportedOperationException("Not Yet Impelemented");
		}
	};
	
	public static EnumSet<AperiodicityModels> UCERF3_MODELS = EnumSet.of(UCERF3_LOW, UCERF3_MIDDLE, UCERF3_HIGH);
	public static EnumSet<AperiodicityModels> NSHM26_MODELS = EnumSet.of(NSHM26_MIDDLE);
	
	private static double getU3Aperiodicity(double mag, double[] aperValues, double[] aperMagBoundaries) {
		for (int i=0; i<aperMagBoundaries.length; i++) {
			if (mag <= aperMagBoundaries[i])
				return aperValues[i];
		}
		return aperValues[aperValues.length-1];
	}
	
	private String name;

	private AperiodicityModels(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
	
	public abstract double getAperiodicity(FaultSystemSolution fltSysSolution, int fltSysRupIndex);

}
