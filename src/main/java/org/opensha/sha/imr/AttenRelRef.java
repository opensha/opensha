package org.opensha.sha.imr;

import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14_BASIN;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.ASK_14_CYBERSHAKE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14_BASIN;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.BSSA_14_CYBERSHAKE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14_BASIN;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CB_14_CYBERSHAKE;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14_BASIN;
import static gov.usgs.earthquake.nshmp.gmm.Gmm.CY_14_CYBERSHAKE;
import static org.opensha.commons.util.DevStatus.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.data.Named;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedList.Unmodifiable;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.nshmp2.imr.NSHMP08_CEUS;
import org.opensha.nshmp2.imr.NSHMP08_WUS;
import org.opensha.nshmp2.imr.impl.AB2006_140_AttenRel;
import org.opensha.nshmp2.imr.impl.AB2006_200_AttenRel;
import org.opensha.nshmp2.imr.impl.Campbell_2003_AttenRel;
import org.opensha.nshmp2.imr.impl.FrankelEtAl_1996_AttenRel;
import org.opensha.nshmp2.imr.impl.SilvaEtAl_2002_AttenRel;
import org.opensha.nshmp2.imr.impl.SomervilleEtAl_2001_AttenRel;
import org.opensha.nshmp2.imr.impl.TP2005_AttenRel;
import org.opensha.nshmp2.imr.impl.ToroEtAl_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AS_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AS_2005_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.AfshariStewart_2016_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BC_2004_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BS_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CS_2005_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.DahleEtAl_1995_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.GouletEtAl_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.McVerryetal_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NGAWest_2014_Averaged_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NGAWest_2014_Averaged_AttenRel.NGAWest_2014_Averaged_AttenRel_NoIdriss;
import org.opensha.sha.imr.attenRelImpl.NGA_2008_Averaged_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NGA_2008_Averaged_AttenRel_NoAS;
import org.opensha.sha.imr.attenRelImpl.NSHMP_2008_CA;
import org.opensha.sha.imr.attenRelImpl.SEA_1999_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SadighEtAl_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SiteSpecific_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;
import org.opensha.sha.imr.attenRelImpl.WC94_DisplMagRel;
import org.opensha.sha.imr.attenRelImpl.ZhaoEtAl_2006_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedBA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.ASK_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.BSSA_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.CB_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.CY_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.GK_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.Idriss_2014_Wrapper;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.mod.ModAttenuationRelationship;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.StewartAfshariGoulet2017NonergodicGMPE;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

/**
 * This <code>enum</code> supplies references to
 * <code>AttenuationRelationship</code> implementations. Each reference can
 * return instances of the <code>AttenuationRelationship</code> it represents as
 * well as limited metadata such as the IMR's name and development status.
 * Static methods are provided to facilitate retrieval of specific
 * <code>Set</code>s of references and <code>List</code>s of instances.
 * 
 * @author Peter Powers
 * @version $Id: AttenRelRef.java 11385 2016-08-24 22:40:54Z kmilner $
 */
public enum AttenRelRef implements AttenRelSupplier {

	// PRODUCTION
	
	ASK_2014(ASK_2014_Wrapper.class,org.opensha.sha.imr.attenRelImpl.ngaw2.ASK_2014.NAME,  org.opensha.sha.imr.attenRelImpl.ngaw2.ASK_2014.SHORT_NAME, PRODUCTION),
	
	BSSA_2014(BSSA_2014_Wrapper.class, org.opensha.sha.imr.attenRelImpl.ngaw2.BSSA_2014.NAME, org.opensha.sha.imr.attenRelImpl.ngaw2.BSSA_2014.SHORT_NAME, PRODUCTION),
	
	CB_2014(CB_2014_Wrapper.class,org.opensha.sha.imr.attenRelImpl.ngaw2.CB_2014.NAME, org.opensha.sha.imr.attenRelImpl.ngaw2.CB_2014.SHORT_NAME, PRODUCTION),
	
	CY_2014(CY_2014_Wrapper.class, org.opensha.sha.imr.attenRelImpl.ngaw2.CY_2014.NAME, org.opensha.sha.imr.attenRelImpl.ngaw2.CY_2014.SHORT_NAME, PRODUCTION),
	
	IDRISS_2014(Idriss_2014_Wrapper.class, org.opensha.sha.imr.attenRelImpl.ngaw2.Idriss_2014.NAME, org.opensha.sha.imr.attenRelImpl.ngaw2.Idriss_2014.SHORT_NAME, PRODUCTION),
	
	NGAWest_2014_AVG(NGAWest_2014_Averaged_AttenRel.class, NGAWest_2014_Averaged_AttenRel.NAME, NGAWest_2014_Averaged_AttenRel.SHORT_NAME, PRODUCTION),
	
	NGAWest_2014_AVG_NOIDRISS(NGAWest_2014_Averaged_AttenRel_NoIdriss.class, NGAWest_2014_Averaged_AttenRel_NoIdriss.NAME, NGAWest_2014_Averaged_AttenRel_NoIdriss.SHORT_NAME, PRODUCTION),

	/** [NGA] Campbell & Bozorgnia (2008) */
	CB_2008(CB_2008_AttenRel.class, CB_2008_AttenRel.NAME, CB_2008_AttenRel.SHORT_NAME, PRODUCTION),

	/** [NGA] Boore & Atkinson (2008) */
	BA_2008(BA_2008_AttenRel.class, BA_2008_AttenRel.NAME, BA_2008_AttenRel.SHORT_NAME, PRODUCTION),

	/** [NGA] Abrahamson & Silva (2008) */
	AS_2008(AS_2008_AttenRel.class, AS_2008_AttenRel.NAME, AS_2008_AttenRel.SHORT_NAME, PRODUCTION),

	/** [NGA] Chiou & Youngs (2008) */
	CY_2008(CY_2008_AttenRel.class, CY_2008_AttenRel.NAME, CY_2008_AttenRel.SHORT_NAME, PRODUCTION),

	/** Goulet et al. (2006) */
	GOULET_2006(GouletEtAl_2006_AttenRel.class, GouletEtAl_2006_AttenRel.NAME,
			GouletEtAl_2006_AttenRel.SHORT_NAME, PRODUCTION),

	/** Zhao et al. (2006) */
	ZHAO_2006(ZhaoEtAl_2006_AttenRel.class, ZhaoEtAl_2006_AttenRel.NAME,
			ZhaoEtAl_2006_AttenRel.SHORT_NAME, PRODUCTION),

	/** Choi & Stewart (2005) */
	CS_2005(CS_2005_AttenRel.class, CS_2005_AttenRel.NAME, CS_2005_AttenRel.SHORT_NAME, PRODUCTION),

	/** Bazzuro & Cornell (2004) */
	BC_2004(BC_2004_AttenRel.class, BC_2004_AttenRel.NAME, BC_2004_AttenRel.SHORT_NAME, PRODUCTION),

	/** USGS combined */
	USGS_2004_COMBO(USGS_Combined_2004_AttenRel.class,
			USGS_Combined_2004_AttenRel.NAME, USGS_Combined_2004_AttenRel.SHORT_NAME, PRODUCTION),

	/** Baturay & Stewart (2003) */
	BS_2003(BS_2003_AttenRel.class, BS_2003_AttenRel.NAME, BS_2003_AttenRel.SHORT_NAME, PRODUCTION),

	/** Campbell & Bozorgnia (2003) */
	CB_2003(CB_2003_AttenRel.class, CB_2003_AttenRel.NAME, CB_2003_AttenRel.SHORT_NAME, PRODUCTION),

	/** ShakeMap */
	SHAKE_2003(ShakeMap_2003_AttenRel.class, ShakeMap_2003_AttenRel.NAME,
			ShakeMap_2003_AttenRel.SHORT_NAME, PRODUCTION),

	/** Field (2000) */
	FIELD_2000(Field_2000_AttenRel.class, Field_2000_AttenRel.NAME, Field_2000_AttenRel.SHORT_NAME, PRODUCTION),
	/** Abrahamson (2000) */
	ABRAHAM_2000(Abrahamson_2000_AttenRel.class, Abrahamson_2000_AttenRel.NAME,
			Abrahamson_2000_AttenRel.SHORT_NAME, PRODUCTION),
	/** McVerry et al. (2000) */
	MCVERRY_2000(McVerryetal_2000_AttenRel.class,
			McVerryetal_2000_AttenRel.NAME, McVerryetal_2000_AttenRel.SHORT_NAME, PRODUCTION),

	/** Sadigh et al. (1999) */
	SADIGH_1999(SEA_1999_AttenRel.class, SEA_1999_AttenRel.NAME, SEA_1999_AttenRel.SHORT_NAME, PRODUCTION),

	/** Abrahmson and Silva (1997) */
	AS_1997(AS_1997_AttenRel.class, AS_1997_AttenRel.NAME, AS_1997_AttenRel.SHORT_NAME, PRODUCTION),

	/** Boore, Joyner & Fumal (1997) */
	BJF_1997(BJF_1997_AttenRel.class, BJF_1997_AttenRel.NAME, BJF_1997_AttenRel.SHORT_NAME, PRODUCTION),

	/** Campbell (1997) */
	CAMPBELL_1997(Campbell_1997_AttenRel.class, Campbell_1997_AttenRel.NAME,
			Campbell_1997_AttenRel.SHORT_NAME, PRODUCTION),
	/** Sadigh et al. (1997) */
	SADIGH_1997(SadighEtAl_1997_AttenRel.class, SadighEtAl_1997_AttenRel.NAME,
			SadighEtAl_1997_AttenRel.SHORT_NAME, PRODUCTION),

	/** Dahle et al. (1995) */
	DAHLE_1995(DahleEtAl_1995_AttenRel.class, DahleEtAl_1995_AttenRel.NAME,
			DahleEtAl_1995_AttenRel.SHORT_NAME, PRODUCTION),
	
	NON_ERGODIC_2016(StewartAfshariGoulet2017NonergodicGMPE.class, StewartAfshariGoulet2017NonergodicGMPE.NAME, StewartAfshariGoulet2017NonergodicGMPE.SHORT_NAME, PRODUCTION),
	
	USGS_NSHM23_ACTIVE(null, "USGS NSHM23 Active Crustal",
			"NSHM23-Active", PRODUCTION) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
//			Unmodifiable<Gmm> gmms = WeightedList.evenlyWeighted(
//					Gmm.ASK_14_BASIN, Gmm.BSSA_14_BASIN, Gmm.CB_14_BASIN, Gmm.CY_14_BASIN);
//			return new NSHMP_GMM_Wrapper.WeightedCombination(gmms, getName(), getShortName(), false, null);
			return new NSHMP_GMM_Wrapper.Single(Gmm.TOTAL_TREE_CONUS_ACTIVE_CRUST_2023, getName(), getShortName(), false, null);
		}
		
	},
	
	USGS_NSHM23_ACTIVE_LA(null, "USGS NSHM23 Los Angeles Basin",
			"NSHM23-Active-LA", PRODUCTION) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			WeightedList<Gmm> gmms = WeightedList.of(
					new WeightedValue<>(Gmm.ASK_14, 0.125),
					new WeightedValue<>(Gmm.BSSA_14, 0.125),
					new WeightedValue<>(Gmm.CB_14, 0.125),
					new WeightedValue<>(Gmm.CY_14, 0.125),
					new WeightedValue<>(Gmm.ASK_14_BASIN, 0.0625),
					new WeightedValue<>(Gmm.BSSA_14_BASIN, 0.0625),
					new WeightedValue<>(Gmm.CB_14_BASIN, 0.0625),
					new WeightedValue<>(Gmm.CY_14_BASIN, 0.0625),
					new WeightedValue<>(Gmm.ASK_14_CYBERSHAKE, 0.0625),
					new WeightedValue<>(Gmm.BSSA_14_CYBERSHAKE, 0.0625),
					new WeightedValue<>(Gmm.CB_14_CYBERSHAKE, 0.0625),
					new WeightedValue<>(Gmm.CY_14_CYBERSHAKE, 0.0625));
			return new NSHMP_GMM_Wrapper.WeightedCombination(gmms, getName(), getShortName(), false, null);
		}
		
	},
	
	USGS_NSHM23_ACTIVE_SF(null, "USGS NSHM23 San Francisco",
			"NSHM23-Active-SF", PRODUCTION) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			WeightedList<Gmm> gmms = WeightedList.of(
					new WeightedValue<>(Gmm.ASK_14, 0.125),
					new WeightedValue<>(Gmm.BSSA_14, 0.125),
					new WeightedValue<>(Gmm.CB_14, 0.125),
					new WeightedValue<>(Gmm.CY_14, 0.125),
					new WeightedValue<>(Gmm.ASK_14_BASIN, 0.125),
					new WeightedValue<>(Gmm.BSSA_14_BASIN, 0.125),
					new WeightedValue<>(Gmm.CB_14_BASIN, 0.125),
					new WeightedValue<>(Gmm.CY_14_BASIN, 0.125));
			return new NSHMP_GMM_Wrapper.WeightedCombination(gmms, getName(), getShortName(), false, null);
		}
		
	},

	// DEVELOPMENT
	
	WRAPPED_ASK_2014(null, "NSHMP-Haz ASK (2014) Base",
			"WrapedASK2014", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.ASK_14_BASE, getName(), getShortName(), false, null);
		}
		
	},
	
	WRAPPED_BSSA_2014(null, "NSHMP-Haz BSSA (2014) Base",
			"WrapedBSSA2014", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.BSSA_14_BASE, getName(), getShortName(), false, null);
		}
		
	},
	
	WRAPPED_NGAW2_NoIDR(null, "NSHMP-Haz NGA-W2 (2014) Base (excl. Idriss)",
			"WrappedNGAW2-NoIdr", PRODUCTION) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			WeightedList<Gmm> gmms = WeightedList.of(
					new WeightedValue<>(Gmm.ASK_14_BASE, 0.25),
					new WeightedValue<>(Gmm.BSSA_14_BASE, 0.25),
					new WeightedValue<>(Gmm.CB_14_BASE, 0.25),
					new WeightedValue<>(Gmm.CY_14_BASE, 0.25));
			return new NSHMP_GMM_Wrapper.WeightedCombination(gmms, getName(), getShortName(), false, null);
		}
		
	},
	
	AG_2020_GLOBAL_INTERFACE(null, "AG (2020) Global Interface",
			"AG2020", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.AG_20_GLOBAL_INTERFACE, getName(), getShortName(), false, null);
		}
		
	},
	
	PSBAH_2020_GLOBAL_INTERFACE(null, "PSBAH (2020) Global Interface",
			"PSBAH2020-Interface", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.PSBAH_20_GLOBAL_INTERFACE, getName(), getShortName(), false, null);
		}
		
	},
	
	PSBAH_2020_GLOBAL_SLAB(null, "PSBAH (2020) Global Slab",
			"PSBAH2020-Slab", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.PSBAH_20_GLOBAL_SLAB, getName(), getShortName(), false, null);
		}
		
	},
	
	USGS_PRVI_ACTIVE(null, "USGS PRVI25 Active Crustal (beta)",
			"PRVI25-Active", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.TOTAL_TREE_PRVI_ACTIVE_CRUST_2025, getName(), getShortName(), false, null);
		}
		
	},
	
	USGS_PRVI_INTERFACE(null, "USGS PRVI25 Interface (beta)",
			"PRVI25-Interface", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.TOTAL_TREE_PRVI_INTERFACE_2025, getName(), getShortName(), false, null);
		}
		
	},
	
	USGS_PRVI_SLAB(null, "USGS PRVI25 Intraslab (beta)",
			"PRVI25-Intraslab", DEVELOPMENT) {
		
		@Override
		public AttenuationRelationship instance(
				ParameterChangeWarningListener listener) {
			return new NSHMP_GMM_Wrapper.Single(Gmm.TOTAL_TREE_PRVI_INTRASLAB_2025, getName(), getShortName(), false, null);
		}
		
	},

	/** Interpolation between periods using BA. */
	BA_2008_INTERP(InterpolatedBA_2008_AttenRel.class,
			InterpolatedBA_2008_AttenRel.NAME, InterpolatedBA_2008_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Average of 4 NGA's. */
	NGA_2008_4AVG(NGA_2008_Averaged_AttenRel.class,
			NGA_2008_Averaged_AttenRel.NAME, NGA_2008_Averaged_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Average of 3 NGA's. */
	NGA_2008_3AVG(NGA_2008_Averaged_AttenRel_NoAS.class,
			NGA_2008_Averaged_AttenRel_NoAS.NAME, NGA_2008_Averaged_AttenRel_NoAS.SHORT_NAME, DEVELOPMENT),

	/** Average of 3 NGA's used in the 20008 NSHMP */
	NSHMP_2008(NSHMP_2008_CA.class, NSHMP_2008_CA.NAME, NSHMP_2008_CA.SHORT_NAME, DEVELOPMENT),

	/** Multiple weighted attenuation relationships used in 20008 CEUS NSHMP */
	NSHMP_2008_CEUS(NSHMP08_CEUS.class, NSHMP08_CEUS.NAME, NSHMP08_CEUS.SHORT_NAME, ERROR), // TODO set to error, see ticket #435

	/** Atkinson and Booore (2006) with 140bar stress drop. For NSHMP CEUS. */
	AB_2006_140(AB2006_140_AttenRel.class, AB2006_140_AttenRel.NAME,
			AB2006_140_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Atkinson and Booore (2006) with 140bar stress drop. For NSHMP CEUS. */
	AB_2006_200(AB2006_200_AttenRel.class, AB2006_200_AttenRel.NAME,
			AB2006_200_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Campbell CEUS (2003). For NSHMP CEUS. */
	CAMPBELL_2003(Campbell_2003_AttenRel.class, Campbell_2003_AttenRel.NAME,
			Campbell_2003_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Frankel et al. (1996). For NSHMP CEUS. */
	FEA_1996(FrankelEtAl_1996_AttenRel.class, FrankelEtAl_1996_AttenRel.NAME,
			FrankelEtAl_1996_AttenRel.SHORT_NAME, ERROR), // TODO set to error because of ticket #366

	/** Somerville et al. (2001). For NSHMP CEUS. */
	SOMERVILLE_2001(SomervilleEtAl_2001_AttenRel.class,
			SomervilleEtAl_2001_AttenRel.NAME, SomervilleEtAl_2001_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Silva et al. (2002). For NSHMP CEUS. */
	SILVA_2002(SilvaEtAl_2002_AttenRel.class, SilvaEtAl_2002_AttenRel.NAME,
			SilvaEtAl_2002_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Toro et al. (1997). For NSHMP CEUS. */
	TORO_1997(ToroEtAl_1997_AttenRel.class, ToroEtAl_1997_AttenRel.NAME,
			ToroEtAl_1997_AttenRel.SHORT_NAME, DEVELOPMENT),

	/** Tavakoli and Pezeshk (2005). For NSHMP CEUS. */
	TP_2005(TP2005_AttenRel.class, TP2005_AttenRel.NAME, TP2005_AttenRel.SHORT_NAME, DEVELOPMENT),

	// EXPERIMENTAL
	
	GK_2014(GK_2014_Wrapper.class, org.opensha.sha.imr.attenRelImpl.ngaw2.GK_2014.NAME, org.opensha.sha.imr.attenRelImpl.ngaw2.GK_2014.SHORT_NAME, EXPERIMENTAL),
	
	MOD_ATTEN_REL(ModAttenuationRelationship.class, ModAttenuationRelationship.NAME, ModAttenuationRelationship.SHORT_NAME, EXPERIMENTAL),
	
	AFSHARI_STEWART_2016(AfshariStewart_2016_AttenRel.class, AfshariStewart_2016_AttenRel.NAME, AfshariStewart_2016_AttenRel.SHORT_NAME, EXPERIMENTAL),

	// DEPRECATED

	/** [NGA prelim] Campbell & Bozorgnia (2008) */
	CB_2006(CB_2006_AttenRel.class, CB_2006_AttenRel.NAME, CB_2006_AttenRel.SHORT_NAME, DEPRECATED),

	/** [NGA prelim] Boore & Atkinson (2008) */
	BA_2006(BA_2006_AttenRel.class, BA_2006_AttenRel.NAME, BA_2006_AttenRel.SHORT_NAME, DEPRECATED),

	/** [NGA prelim] Abrahamson & Silva (2008) */
	AS_2005(AS_2005_AttenRel.class, AS_2005_AttenRel.NAME, AS_2005_AttenRel.SHORT_NAME, DEPRECATED),

	/** [NGA prelim] Chiou & Youngs (2008) */
	CY_2006(CY_2006_AttenRel.class, CY_2006_AttenRel.NAME, CY_2006_AttenRel.SHORT_NAME, DEPRECATED),

	/** Site specific model */
	SITESPEC_2006(SiteSpecific_2006_AttenRel.class,
			SiteSpecific_2006_AttenRel.NAME, SiteSpecific_2006_AttenRel.SHORT_NAME, DEPRECATED),

	/** Wells & Coppersmith (1994) displacement model */
	WC_1994(WC94_DisplMagRel.class, WC94_DisplMagRel.NAME, WC94_DisplMagRel.SHORT_NAME, DEPRECATED);

	private Class<? extends AttenuationRelationship> clazz;
	private String name;
	private String shortName;
	private DevStatus status;

	private AttenRelRef(Class<? extends AttenuationRelationship> clazz,
		String name, String shortName, DevStatus status) {
		this.clazz = clazz;
		this.name = name;
		this.shortName = shortName;
		this.status = status;
	}

	@Override
	public String toString() {
		return name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}

	/**
	 * Returns the development status of the referenced
	 * <code>AttenuationRelationship</code>.
	 * @return the development status
	 */
	public DevStatus status() {
		return status;
	}
	
	public Class<? extends AttenuationRelationship> getAttenRelClass() {
		return clazz;
	}

	/**
	 * Returns a new instance of the attenuation relationship represented by
	 * this reference.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @return a new <code>AttenuationRelationship</code> instance
	 */
	public AttenuationRelationship instance(
			ParameterChangeWarningListener listener) {
		try {
			Object[] args = new Object[] { listener };
			Class<?>[] params = new Class[] { ParameterChangeWarningListener.class };
			Constructor<? extends AttenuationRelationship> con = clazz
				.getConstructor(params);
			return con.newInstance(args);
		} catch (Exception e) {
			// now try a no arg constructor
			try {
				Object[] args = new Object[] {};
				Class<?>[] params = new Class[] {};
				Constructor<? extends AttenuationRelationship> con = clazz
					.getConstructor(params);
				return con.newInstance(args);
			} catch (Exception e1) {
				// TODO init logging
				e.printStackTrace();
				return null;
			}
		}
	}

	/**
	 * Convenience method to return references for all
	 * <code>AttenuationRelationship</code> implementations that are currently
	 * production quality (i.e. fully tested and documented), under development,
	 * or experimental. The <code>Set</code> of references returned does not
	 * include deprecated references.
	 * @return reference <code>Set</code> of all non-deprecated
	 *         <code>AttenuationRelationship</code>s
	 * @see DevStatus
	 */
	public static Set<AttenRelRef> getAll() {
		return get(PRODUCTION, DEVELOPMENT, EXPERIMENTAL);
	}

	/**
	 * Convenience method to return references for all
	 * <code>AttenuationRelationship</code> implementations that should be
	 * included in applications with the given ServerPrefs. Production
	 * applications only include production IMRs, and development applications
	 * include everything but deprecated IMRs.
	 * 
	 * @param prefs <code>ServerPrefs</code> instance for which IMRs should be
	 *        selected
	 * @return
	 */
	public static Set<AttenRelRef> get(ServerPrefs prefs) {
		if (prefs == ServerPrefs.DEV_PREFS)
			return get(PRODUCTION, DEVELOPMENT, EXPERIMENTAL);
		else if (prefs == ServerPrefs.PRODUCTION_PREFS)
			return get(PRODUCTION);
		else
			throw new IllegalArgumentException(
				"Unknown ServerPrefs instance: " + prefs);
	}

	/**
	 * Convenience method to return references to
	 * <code>AttenuationRelationship</code> implementations at the specified
	 * levels of development.
	 * @param stati the development level(s) of the
	 *        <code>AttenuationRelationship</code> references to be retrieved
	 * @return a <code>Set</code> of <code>AttenuationRelationship</code>
	 *         references
	 * @see DevStatus
	 */
	public static Set<AttenRelRef> get(DevStatus... stati) {
		EnumSet<AttenRelRef> ariSet = EnumSet.allOf(AttenRelRef.class);
		for (AttenRelRef ari : ariSet) {
			if (!ArrayUtils.contains(stati, ari.status)) ariSet.remove(ari);
		}
		return ariSet;
	}

	/**
	 * Returns a <code>List</code> of <code>AttenuationRelationship</code>
	 * instances that are currently production quality (i.e. fully tested and
	 * documented), under development, or experimental. The list of
	 * <code>AttenuationRelationship</code>s returned does not include
	 * deprecated implementations.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @param sorted whether to sort the list by name
	 * @return a <code>List</code> of all non-deprecated
	 *         <code>AttenuationRelationship</code>s
	 */
	public static List<AttenuationRelationship> instanceList(
			ParameterChangeWarningListener listener, boolean sorted) {
		return buildInstanceList(getAll(), listener, sorted);
	}

	/**
	 * Returns a <code>List</code> of <code>AttenuationRelationship</code>
	 * instances that are appropriate for an application with the given
	 * <code>ServerPrefs</code>.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @param sorted whether to sort the list by name
	 * @param prefs <code>ServerPrefs</code> instance for which IMRs should be
	 *        selected
	 * @return a <code>List</code> of all non-deprecated
	 *         <code>AttenuationRelationship</code>s
	 */
	public static List<AttenuationRelationship> instanceList(
			ParameterChangeWarningListener listener, boolean sorted,
			ServerPrefs prefs) {
		return buildInstanceList(get(prefs), listener, sorted);
	}

	/**
	 * Returns a <code>List</code> of <code>AttenuationRelationship</code>
	 * instances specified by the supplied <code>Collection</code> of
	 * references.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @param sorted whether to sort the list by name
	 * @param refs to instances to retrieve
	 * @return a <code>List</code> of all non-deprecated
	 *         <code>AttenuationRelationship</code>s
	 */
	public static List<AttenuationRelationship> instanceList(
			ParameterChangeWarningListener listener, boolean sorted,
			Collection<AttenRelRef> refs) {
		return buildInstanceList(refs, listener, sorted);
	}

	/**
	 * Returns a <code>List</code> of <code>AttenuationRelationship</code>
	 * instances specified by the supplied references.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @param sorted whether to sort the list by name
	 * @param refs to instances to retrieve
	 * @return a <code>List</code> of all non-deprecated
	 *         <code>AttenuationRelationship</code>s
	 */
	public static List<AttenuationRelationship> instanceList(
			ParameterChangeWarningListener listener, boolean sorted,
			AttenRelRef... refs) {
		return buildInstanceList(Arrays.asList(refs), listener, sorted);
	}

	/**
	 * Returns a <code>List</code> of <code>AttenuationRelationship</code>
	 * instances at a specified level of development.
	 * @param listener to initialize instances with; may be <code>null</code>
	 * @param sorted whether to sort the list by name
	 * @param stati the development level(s) of the
	 *        <code>AttenuationRelationship</code> references to be retrieved
	 * @return a <code>List</code> of <code>AttenuationRelationship</code>s
	 */
	public static List<AttenuationRelationship> instanceList(
			ParameterChangeWarningListener listener, boolean sorted,
			DevStatus... stati) {
		return buildInstanceList(get(stati), listener, sorted);
	}

	private static List<AttenuationRelationship> buildInstanceList(
			Collection<AttenRelRef> arrSet,
			ParameterChangeWarningListener listener, boolean sorted) {
		List<AttenuationRelationship> arList = new ArrayList<AttenuationRelationship>();
		for (AttenRelRef arr : arrSet) {
			arList.add(arr.instance(listener));
		}
		if (sorted) Collections.sort(arList);
		return arList;
	}

	@Override
	public ScalarIMR get() {
		ScalarIMR instance = instance(null);
		instance.setParamDefaults();
		return instance;
	}

}
