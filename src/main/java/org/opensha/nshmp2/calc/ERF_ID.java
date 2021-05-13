package org.opensha.nshmp2.calc;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.param.Parameter;
import org.opensha.nshmp2.erf.NSHMP2008;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeIndependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;

import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2;
import scratch.UCERF3.utils.UpdatedUCERF2.GridSources;
import scratch.UCERF3.utils.UpdatedUCERF2.MeanUCERF2update;
import scratch.UCERF3.utils.UpdatedUCERF2.MeanUCERF2update_FM2p1;
import scratch.UCERF3.utils.UpdatedUCERF2.ModMeanUCERF2update_FM2p1;
import scratch.UCERF3.utils.UpdatedUCERF2.UCERF2_FM2pt1_FSS_ERFupdate;
import scratch.peter.ucerf3.calc.UC3_CalcUtils;

/**
 * Add comments here
 * 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("javadoc")
public enum ERF_ID {

	NSHMP08() {
		public EpistemicListERF instance() {
			return NSHMP2008.create();
		}
	},
	NSHMP08_CA() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCalifornia();
		}
	},
	NSHMP08_CA_NW() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCaliforniaNW();
		}
	},
	NSHMP08_CA_GRD() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCaliforniaGridded();
		}
	},
	NSHMP08_CA_FLT() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCaliforniaFault();
		}
	},
	NSHMP08_CA_FIX() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCaliforniaFixedStrk();
		}
	},
	NSHMP08_CA_PT() {
		public EpistemicListERF instance() {
			return NSHMP2008.createCaliforniaPointSrc();
		}
	},
	MEAN_UCERF2() {
		public EpistemicListERF instance() {
			return getMeanUC2();
		}
	},
	MEAN_UCERF2_GRD() {
		public EpistemicListERF instance() {
			return getMeanUC2_GRD();
		}
	},
	MEAN_UCERF2_FIX() {
		public EpistemicListERF instance() {
			return getMeanUC2_FIX();
		}
	},
	MEAN_UCERF2_PT() {
		public EpistemicListERF instance() {
			return getMeanUC2_PT();
		}
	},
	MEAN_UCERF2_FM2P1() {
		public EpistemicListERF instance() {
			return getMeanUC2_FM2P1();
		}
	},
	MOD_MEAN_UCERF2_FM2P1() {
		public EpistemicListERF instance() {
			return getModMeanUC2_FM2P1();
		}
	},
	UCERF2_FM2P1_FSS_ERF() {
		public EpistemicListERF instance() {
			return getUC2_FM2P1_FSS();
		}
	},
	/*
	 * NOTE UCERF2 Time Indep requires manual override of floating rupture
	 * offset as no direct access is provided to underlying UC2 object.
	 */
	UCERF2_TIME_INDEP() {
		public EpistemicListERF instance() {
			return getUC2_TI();
		}
	},
	UCERF3_REF_MEAN() {
		public EpistemicListERF instance() {
			return getUC3(UC3_CONV_PATH);
		}
	},
	UCERF3_REF_MEAN_VAR0() {
		public EpistemicListERF instance() {
			return getUC3(UC3_CONV_PATH_VAR0);
		}
	},
	
	UCERF3_UC2MAP1() {
		public EpistemicListERF instance() {
			return getUC3_noBG(UC3_UC2_MAP_TAP);
		}
	},
	UCERF3_UC2MAP2() {
		public EpistemicListERF instance() {
			return getUC3_noBG(UC3_UC2_MAP_UNI);
		}
	},
	UCERF3_RATE_TEST() {
		public EpistemicListERF instance() {
			return getUC3(UC3_RATE_TEST);
		}
	},
	
	
	/** 
	 * Placeholder identifier
	 */
	UCERF3_BRANCH() {
		public EpistemicListERF instance() {
			return null;
		}
	};


	public abstract EpistemicListERF instance();

	private static EpistemicListERF getMeanUC2() {
		final MeanUCERF2 erf = new MeanUCERF2update(GridSources.ALL);
		setParams(erf);
		return wrapInList(erf);
	}
	
	private static EpistemicListERF getMeanUC2_GRD() {
		final MeanUCERF2 erf = new MeanUCERF2update(GridSources.ALL);
		setParams(erf);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		return wrapInList(erf);
	}

	private static EpistemicListERF getMeanUC2_FIX() {
		final MeanUCERF2 erf = new MeanUCERF2update(GridSources.FIX_STRK);
		setParams(erf);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		return wrapInList(erf);
	}

	private static EpistemicListERF getMeanUC2_PT() {
		final MeanUCERF2 erf = new MeanUCERF2update(GridSources.PT_SRC);
		setParams(erf);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_ONLY);
		return wrapInList(erf);
	}

	private static EpistemicListERF getMeanUC2_FM2P1() {
		final MeanUCERF2 erf = new MeanUCERF2update_FM2p1();
		setParams(erf);
		return wrapInList(erf);
	}

	private static EpistemicListERF getModMeanUC2_FM2P1() {
		final ModMeanUCERF2 erf = new ModMeanUCERF2update_FM2p1();
		setParams(erf);
		return wrapInList(erf);
	}

	private static EpistemicListERF getUC2_FM2P1_FSS() {
		final FaultSystemSolutionERF erf = new UCERF2_FM2pt1_FSS_ERFupdate();
		erf.getTimeSpan().setDuration(1.0);
		return wrapInList(erf);
	}
	
	private static EpistemicListERF getUC2_TI() {
		final UCERF2_TimeIndependentEpistemicList erf = new UCERF2_TimeIndependentEpistemicList();
		Parameter bgSrcParam = erf.getParameter(UCERF2.BACK_SEIS_RUP_NAME);
		bgSrcParam.setValue(UCERF2.BACK_SEIS_RUP_POINT);
		Parameter floatParam = erf.getParameter(UCERF2.FLOATER_TYPE_PARAM_NAME);
		floatParam.setValue(UCERF2.FULL_DDW_FLOATER);
		TimeSpan ts = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		ts.setDuration(1);
		erf.setTimeSpan(ts);		
		return erf;
	}
	
	private static EpistemicListERF getUC3(String solPath) {
		FaultSystemSolutionERF erf = UC3_CalcUtils.getUC3_ERF(
			solPath, IncludeBackgroundOption.INCLUDE,
			false, true, 1.0);
		return wrapInList(erf);
	}

	private static EpistemicListERF getUC3_noBG(String solPath) {
		FaultSystemSolutionERF erf = UC3_CalcUtils.getUC3_ERF(
			solPath, IncludeBackgroundOption.EXCLUDE,
			false, true, 1.0);
		return wrapInList(erf);
	}

	private static EpistemicListERF getUC3_onlyBG(String solPath) {
		FaultSystemSolutionERF erf = UC3_CalcUtils.getUC3_ERF(
			solPath, IncludeBackgroundOption.ONLY,
			false, true, 1.0);
		return wrapInList(erf);
	}

	/**
	 * Wraps an ERF in an Epistemic list with weight=1.
	 * KLUDGY TODO This needs to go away; it's a by-product of wrapping
	 * the 2008 NSHMP sources in an epistemic list rather than creating
	 * a new CompoundSource or similar that can handle weights
	 * 
	 * @param erf
	 * @return
	 */
	public static EpistemicListERF wrapInList(final AbstractERF erf) {
		EpistemicListERF listERF = new AbstractEpistemicListERF() {
			{
				addERF(erf, 1.0);
			}
		};
		listERF.setTimeSpan(erf.getTimeSpan());
		return listERF;
	}
	
	private static void setParams(ERF uc2) {
		uc2.setParameter(MeanUCERF2.RUP_OFFSET_PARAM_NAME, 1.0);
		uc2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME,
			UCERF2.PROB_MODEL_POISSON);
		uc2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		uc2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_POINT);
		uc2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME,
			UCERF2.FULL_DDW_FLOATER);
		uc2.getTimeSpan().setDuration(1.0);
	}

	
	private static final String BASE_PATH = "/home/scec-00/pmpowers/UC3/src";
	
	private static final String UC3_CONV_PATH = BASE_PATH +
			"/conv/FM3_1_ZENG_Shaw09Mod_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip";
	private static final String UC3_CONV_PATH_VAR0 = BASE_PATH +
			"/conv/FM3_1_ZENG_Shaw09Mod_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarZeros_mean_sol.zip";

	private static final String UC3_RATE_TEST = BASE_PATH +
			"/rateTest/FM3_1_ZENG_Shaw09Mod_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_mean_sol.zip";

	private static final String UC31_PATH = BASE_PATH +
			"/tree/2012_10_29-tree-fm31_x7-fm32_x1_COMPOUND_SOL.zip";
	private static final String UC32_PATH = BASE_PATH +
			"/tree/2013_01_14-UC32-COMPOUND_SOL.zip";
	
	private static final String UC3_UC2_MAP_TAP = BASE_PATH +
			"/uc2map/FM2_1_UC2ALL_AveU2_DsrTap_CharConst_M5Rate7.6_MMaxOff7.6_NoFix_SpatSeisU2_mean_sol.zip";
	private static final String UC3_UC2_MAP_UNI = BASE_PATH +
			"/uc2map/FM2_1_UC2ALL_AveU2_DsrUni_CharConst_M5Rate7.6_MMaxOff7.6_NoFix_SpatSeisU2_mean_sol.zip";
	
	
//	private static final String UC31_1X_SOL_PATH = BASE_PATH +
//			"/tree/2012_10_14-fm31-tree-x1-COMPOUND_SOL.zip";
//	private static final String UC32_1X_SOL_PATH = BASE_PATH +
//			"/tree/2012_10_14-fm31-tree-x1-COMPOUND_SOL.zip";
//	private static final String UC31_5X_SOL_PATH = BASE_PATH +
//			"/tree/2012_10_14-fm31-tree-x5-COMPOUND_SOL.zip";
//	private static final String UC31_7X_SOL_PATH = BASE_PATH +
//			"/tree/2012_10_29-fm31-tree-x7-COMPOUND_SOL.zip";

		
}
