package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumMap;
import java.util.Map;

import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.Parameterized;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Interface for a rupture aperiodicity model.
 * <p>
 * Implementation note: currently, probability model implementation assume that they will only encounter a handful of
 * discrete values, which they use to construct normalized CDF caches. If we ever implement continous values, that needs
 * to be relaxed.
 */
public interface AperiodicityModel extends Parameterized {

	/**
	 * Returns the aperiodicity (COV) for the given rupture (in the {@link FaultSystemSolution} used to instantiate
	 * this model), potentially combining different values across sections for which
	 * {@link #getSectionAperiodicity(FaultSection, int)} varies.
	 * 
	 * @param fltSysSolution
	 * @param ruptureIndex
	 * @return aperiodicity for the given rupture
	 */
	public abstract double getRuptureAperiodicity(int ruptureIndex);
	
	/**
	 * This returns any adjustable parameters for this model, or null if there are none.
	 * <p>
	 * The default implementation returns null.
	 * 
	 * @return adjustable parameters or null if there are none
	 */
	public default ParameterList getAdjustableParameters() {
		return null;
	}
	
	public static class SingleValued implements AperiodicityModel {
		
		private DoubleParameter param;
		private ParameterList params;
		
		public SingleValued(double aperiodicity, boolean adjustable) {
			this(aperiodicity, adjustable, "Aperiodicity");
		}

		public SingleValued(double aperiodicity, boolean adjustable, String parameterName) {
			param = new DoubleParameter(parameterName, 0d, Math.max(10d, aperiodicity));
			param.setValue(aperiodicity);
			if (adjustable) {
				params = new ParameterList();
				params.addParameter(param);
			}
		}

		@Override
		public double getRuptureAperiodicity(int ruptureIndex) {
			return param.getValue();
		}
		
		public void setValue(double aperiodicity) {
			param.setValue(aperiodicity);
			if (param.isEditorBuilt())
				param.getEditor().refreshParamEditor();
		}

		@Override
		public ParameterList getAdjustableParameters() {
			return params;
		}
		
	}
	
	// TODO: could add a "SectionDependent" variants that return different values for different fault sections,
	// e.g., for fault maturity.
	
	public static abstract class MagnitudeDependent implements AperiodicityModel {
		
		private FaultSystemRupSet rupSet;

		public MagnitudeDependent(FaultSystemRupSet rupSet) {
			this.rupSet = rupSet;
		}

		@Override
		public double getRuptureAperiodicity(int ruptureIndex) {
			return getRuptureAperiodicity(rupSet.getMagForRup(ruptureIndex));
		}
		
		public abstract double getRuptureAperiodicity(double magnitude);
		
	}
	
	public static class MagnitudeBinned extends MagnitudeDependent {
		
		private double[] aperValues;
		private double[] aperMagBoundaries;

		public MagnitudeBinned(FaultSystemRupSet rupSet, double[] aperValues, double[] aperMagBoundaries) {
			super(rupSet);
			Preconditions.checkState(aperValues.length == aperMagBoundaries.length+1,
					"Should have 1 more aperiodicity value than magnitude bin edge");
			// make sure magnitude bins are monotonically increasing
			for (int m=1; m<aperMagBoundaries.length; m++)
				Preconditions.checkState(aperMagBoundaries[m] > aperMagBoundaries[m-1],
						"Magnitudes must be monotonically increasing");
			this.aperValues = aperValues;
			this.aperMagBoundaries = aperMagBoundaries;
		}
		
		public double getRuptureAperiodicity(double magnitude) {
			for (int i=0; i<aperMagBoundaries.length; i++) {
				if (magnitude <= aperMagBoundaries[i])
					return aperValues[i];
			}
			return aperValues[aperValues.length-1];
		}
		
	}
	
	public static class TRT_Dependent implements AperiodicityModel {
		
		private Map<TectonicRegionType, AperiodicityModel> models;
		private AperiodicityModel fallback;
		private RupSetTectonicRegimes trts;
		private ParameterList params;

		public TRT_Dependent(FaultSystemRupSet rupSet, Map<TectonicRegionType, AperiodicityModel> models) {
			this(rupSet, models, null, null);
		}

		public TRT_Dependent(FaultSystemRupSet rupSet, Map<TectonicRegionType, AperiodicityModel> models,
				AperiodicityModel fallback, ParameterList params) {
			// convert to EnumMap, which will be faster
			// this also decouples us from that which was passed in for immutability
			models = new EnumMap<>(models);
			this.models = models;
			this.fallback = fallback;
			this.params = params;
			trts = rupSet.requireModule(RupSetTectonicRegimes.class);
		}

		@Override
		public double getRuptureAperiodicity(int ruptureIndex) {
			TectonicRegionType trt = trts.get(ruptureIndex);
			AperiodicityModel model;
			if (trt == null) {
				Preconditions.checkNotNull(fallback, "No TectonicRegionType for rupture %s and fallback not provided", ruptureIndex);
				model = fallback;
			} else {
				model = models.get(trt);
				if (model == null) {
					Preconditions.checkNotNull(fallback, "No AperiodicityModel for %s and fallback not provided", trt);
					model = fallback;
				}
			}
			return model.getRuptureAperiodicity(ruptureIndex);
		}

		@Override
		public ParameterList getAdjustableParameters() {
			return params;
		}
	}
	
}
