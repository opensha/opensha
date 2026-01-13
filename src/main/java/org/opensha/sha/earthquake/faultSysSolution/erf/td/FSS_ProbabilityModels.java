package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.param.ParamLinker;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.ParameterizedEnumParameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;

/**
 * Enum of probability model options, similar to old ProbabilityModelOptions
 */
public enum FSS_ProbabilityModels {

	POISSON("Poisson") {
		@Override
		public FSS_ProbabilityModel.Poisson getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			return new FSS_ProbabilityModel.Poisson(sol);
		}
	},
	/**
	 * Flexible option using the UCERF3 methodology and supporting all sub-model parameters and implementations
	 */
	UCERF3_METHOD("UCERF3-TD Methodology") {
		@Override
		public UCERF3_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			return new UCERF3_ProbabilityModel(
					sol, longTermPartRateForSectArray,
					AperiodicityModels.NSHM26_MIDDLE,
					RenewalModels.BPT,
					HistoricalOpenIntervals.UCERF3,
					BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
		}
	},
	/**
	 * NSHM (2026) TD implementation with options defaulting and restricted to those supported by the (to be) published
	 * model.
	 */
	NSHM26("NSHM (2026)") {
		@Override
		public UCERF3_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			return new UCERF3_ProbabilityModel(
					sol, longTermPartRateForSectArray,
					// initialize with NSHM26 middle aperiodicity and allow only the NSHM26 aperiodicity branches
					AperiodicityModels.NSHM26_MIDDLE, AperiodicityModels.NSHM26_MODELS,
					// initialize with BPT but allow any of the renewal model distributions
					RenewalModels.BPT, EnumSet.allOf(RenewalModels.class),
					// allow U3 and no hist open interval
					HistoricalOpenIntervals.UCERF3, HistoricalOpenIntervals.UCERF3_MODELS,
					// U3 default, only bother showing that for now
					BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE, EnumSet.of(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE));
		}
	},
	/**
	 * UCERF3-TD (2014) implementation with options defaulting and restricted to those supported by published model
	 */
	UCERF3_BPT("UCERF3-TD BPT (2014)") {
		@Override
		public UCERF3_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			return new UCERF3_ProbabilityModel(
					sol, longTermPartRateForSectArray,
					// initialize with U3 middle aperiodicity and allow only the 3 UCERF3 aperiodicity branches
					AperiodicityModels.UCERF3_MIDDLE, AperiodicityModels.UCERF3_MODELS,
					// allow only BPT
					RenewalModels.BPT, EnumSet.of(RenewalModels.BPT),
					// allow U3 and no hist open interval
					HistoricalOpenIntervals.UCERF3, HistoricalOpenIntervals.UCERF3_MODELS,
					// U3 default, allow all
					BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE, EnumSet.allOf(BPTAveragingTypeOptions.class));
		}
	},
	/**
	 * UCERF3-TD (2014) preferred blend, no adjustable parameters
	 */
	UCERF3_PREF_BLEND("UCERF3-TD Preferred Blend (2014)") {
		@Override
		public FSS_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			WeightedList<FSS_ProbabilityModel> models = new WeightedList<>(4);
			
			// TODO: need to figure out param linker stuff for the new ParameterizedEnumParameter, this won't work yet
			
			FSS_ProbabilityModel u3Low = UCERF3_BPT.getProbabilityModel(sol, longTermPartRateForSectArray);
			u3Low.getAdjustableParameters().setValue(AperiodicityModels.PARAM_NAME, AperiodicityModels.UCERF3_LOW);
			// we'll show these parameters in the GUI, and the ParamLinker calls below will make sure any changes are
			// propagated to each other U3 model. Keep all but the aperiodicity parameter
			ParameterList params = new ParameterList();
			for (Parameter<?> param : u3Low.getAdjustableParameters())
				params.addParameter(param);
			models.add(u3Low, 0.1);
			
			FSS_ProbabilityModel u3Middle = UCERF3_BPT.getProbabilityModel(sol, longTermPartRateForSectArray);
			u3Middle.getAdjustableParameters().setValue(AperiodicityModels.PARAM_NAME, AperiodicityModels.UCERF3_MIDDLE);
			// link parameters in the reference model to this one 
			for (Parameter<?> param : params)
				ParamLinker.link(param, u3Middle.getAdjustableParameters().getParameter(param.getName()));
			models.add(u3Middle, 0.4);
			
			FSS_ProbabilityModel u3High = UCERF3_BPT.getProbabilityModel(sol, longTermPartRateForSectArray);
			u3High.getAdjustableParameters().setValue(AperiodicityModels.PARAM_NAME, AperiodicityModels.UCERF3_HIGH);
			// link parameters in the reference model to this one 
			for (Parameter<?> param : params)
				ParamLinker.link(param, u3High.getAdjustableParameters().getParameter(param.getName()));
			models.add(u3High, 0.3);
			
			models.add(new FSS_ProbabilityModel.Poisson(sol), 0.2);
			
			return new FSS_ProbabilityModel.WeightedCombination(this.toString(), models, params);
		}
	},
	WG02("WGCEP (2002)") {
		@Override
		public WG02_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray) {
			return new WG02_ProbabilityModel(sol, longTermPartRateForSectArray);
		}
	};
	
	private String name;

	private FSS_ProbabilityModels(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public abstract FSS_ProbabilityModel getProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray);
}
