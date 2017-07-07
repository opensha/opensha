package org.opensha.nshmp2.util;

import static org.opensha.nshmp2.util.FaultCode.*;
import static org.opensha.nshmp2.util.Period.GM0P00;
import static org.opensha.nshmp2.util.SourceRegion.*;

import java.lang.reflect.Constructor;
import java.util.Map;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.nshmp2.erf.source.NSHMP_ERF;
import org.opensha.nshmp2.imr.NSHMP08_CEUS;
import org.opensha.nshmp2.imr.NSHMP08_CEUS_Grid;
import org.opensha.nshmp2.imr.NSHMP08_SUB_Interface;
import org.opensha.nshmp2.imr.NSHMP08_SUB_SlabGrid;
import org.opensha.nshmp2.imr.NSHMP08_WUS;
import org.opensha.nshmp2.imr.NSHMP08_WUS_Grid;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_ASK;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_ASK_EpiDn;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_ASK_EpiUp;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_BSSA;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_BSSA_EpiDn;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_BSSA_EpiUp;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CB;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CB_EpiDn;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CB_EpiUp;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CY;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CY_EpiDn;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_CY_EpiUp;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_GK;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_Idriss;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_Idriss_EpiDn;
import org.opensha.nshmp2.imr.ngaw2.NSHMP14_WUS_Idriss_EpiUp;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NSHMP14_WUS;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

/**
 * NSHMP source IMR identifier. The identifier can be used to retrieve instances
 * of the correct IMR for hazardcalculations. Although this class shares
 * identifying characteristics with {@link SourceRegion} and {@link SourceType},
 * there is no complete mapping of each type, hence an independent enum to allow
 * an {@link NSHMP_ERF} to identify which IMR should be used with it.
 * 
 * @author Peter Powers
 * @version $Id:$
 * @see SourceRegion
 * @see SourceType
 * @see NSHMP_ERF
 */
public enum SourceIMR {
	
	/**
	 * Used for {@link SourceRegion#WUS} and {@link SourceRegion#CA}
	 * {@link SourceType#FAULT} sources.
	 */
	WUS_FAULT(NSHMP08_WUS.class),

	/**
	 * Used for {@link SourceRegion#WUS} and {@link SourceRegion#CA}
	 * {@link SourceType#FAULT} sources.
	 */
	WUS_FAULT_14(NSHMP14_WUS.class),
	WUS_FAULT_14_AS(NSHMP14_WUS_ASK.class),
	WUS_FAULT_14_BS(NSHMP14_WUS_BSSA.class),
	WUS_FAULT_14_CB(NSHMP14_WUS_CB.class),
	WUS_FAULT_14_CY(NSHMP14_WUS_CY.class),
	WUS_FAULT_14_GK(NSHMP14_WUS_GK.class),
	WUS_FAULT_14_ID(NSHMP14_WUS_Idriss.class),

	WUS_FAULT_14_AS_EPI_UP(NSHMP14_WUS_ASK_EpiUp.class),
	WUS_FAULT_14_BS_EPI_UP(NSHMP14_WUS_BSSA_EpiUp.class),
	WUS_FAULT_14_CB_EPI_UP(NSHMP14_WUS_CB_EpiUp.class),
	WUS_FAULT_14_CY_EPI_UP(NSHMP14_WUS_CY_EpiUp.class),
	WUS_FAULT_14_ID_EPI_UP(NSHMP14_WUS_Idriss_EpiUp.class),

	WUS_FAULT_14_AS_EPI_DN(NSHMP14_WUS_ASK_EpiDn.class),
	WUS_FAULT_14_BS_EPI_DN(NSHMP14_WUS_BSSA_EpiDn.class),
	WUS_FAULT_14_CB_EPI_DN(NSHMP14_WUS_CB_EpiDn.class),
	WUS_FAULT_14_CY_EPI_DN(NSHMP14_WUS_CY_EpiDn.class),
	WUS_FAULT_14_ID_EPI_DN(NSHMP14_WUS_Idriss_EpiDn.class),

	/**
	 * Used for {@link SourceRegion#WUS} and {@link SourceRegion#CA}
	 * {@link SourceType#GRIDDED} sources.
	 */
	WUS_GRID(NSHMP08_WUS_Grid.class),

	/**
	 * Used for {@link SourceRegion#WUS} and {@link SourceRegion#CA} deep
	 * (subduction slab) {@link SourceType#GRIDDED} sources.
	 */
	WUS_SLAB(NSHMP08_SUB_SlabGrid.class),

	/**
	 * Used for {@link SourceRegion#CASC} and {@link SourceType#SUBDUCTION}
	 * sources.
	 */
	WUS_SUB(NSHMP08_SUB_Interface.class),

	/**
	 * Used for {@link SourceRegion#CEUS} {@link SourceType#FAULT} and
	 * {@link SourceType#CLUSTER} sources.
	 */
	CEUS_FAULT(NSHMP08_CEUS.class),

	/**
	 * Used for {@link SourceRegion#CEUS} {@link SourceType#GRIDDED} sources.
	 */
	CEUS_GRID(NSHMP08_CEUS_Grid.class);
	
	private Class<? extends ScalarIMR> clazz;
	private SourceIMR(Class<? extends ScalarIMR> clazz) {
		this.clazz = clazz;
	}
	
	/**
	 * Returns a map of all {@code SourceIMR} mapped to their corresponding
	 * {@code ScalarIMR} instances and initialized to th.
	 * @param p period of interest
	 * @return a {@code SourceIMR} to {@code ScalarIMR} map
	 */
	public static Map<SourceIMR, ScalarIMR> map(Period p) {
		Map<SourceIMR, ScalarIMR> map = Maps.newEnumMap(SourceIMR.class);
		for (SourceIMR simr : SourceIMR.values()) {
			map.put(simr, simr.instance(p));
		}
		return map;
	}
	
	/**
	 * Returns a new instance of the ERF represented by
	 * this reference.
	 * @param p period of interest
	 * @return a new <code>EqkRupForecastBaseAPI</code> instance
	 */
	public ScalarIMR instance(Period p) {
		try {
			Constructor<? extends ScalarIMR> con = clazz.getConstructor();
			ScalarIMR imr = con.newInstance();
			imr.setIntensityMeasure((p == GM0P00) ? PGA_Param.NAME : SA_Param.NAME);
			try {
				imr.getParameter(PeriodParam.NAME).setValue(p.getValue());
			}  catch (ConstraintException ce) { /* do nothing */ }
			return imr;
		} catch (Exception e) {
			System.out.println(e.getCause());
			throw Throwables.propagate(e);
		}
	}
	
	/**
	 * Returns the IMR identifier for the supplied source information. This is
	 * used by parsers when initializing NSHMP ERFs.
	 * @param region 
	 * @param type
	 * @param name
	 * @param code 
	 * @return an IMR identifier
	 */
	public static SourceIMR imrForSource(SourceType type, SourceRegion region,
			String name, FaultCode code) {
		switch(type) {
			case GRIDDED:
				if (region == CEUS)
					return (code == FIXED) ? CEUS_FAULT : CEUS_GRID;
				if (name.contains("deep")) return WUS_SLAB;
				return (code == FIXED) ? WUS_FAULT : WUS_GRID;
			case FAULT:
				if (region == CEUS) return CEUS_FAULT;
				return WUS_FAULT;
			case CLUSTER:
				return CEUS_FAULT;
			case SUBDUCTION:
				return WUS_SUB;
			default:
				return null;
		}
	}

}
