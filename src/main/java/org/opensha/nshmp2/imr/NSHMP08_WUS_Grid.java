package org.opensha.nshmp2.imr;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.nshmp2.util.CurveTable;
import org.opensha.nshmp2.util.Period;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;

import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * The grid implementation of {@link NSHMP08_WUS}.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_WUS_Grid extends NSHMP08_WUS implements GridIMR {

	public final static String NAME = "NSHMP 2008 WUS Combined (Grid)";
	public final static String SHORT_NAME = "NSHMP08_WUS_GRID";

	private static Table<Period, MechID, CurveTable> tables;
	private CurveTable table; // table in use by instance

	// flag to use for hybridized grids (e.g. %50 SS %50 RV); used to lower
	// f_nm and f_rv scale factors
	private boolean isGridHybrid = false;

	public NSHMP08_WUS_Grid() {
		getParameter(DepthTo1pt0kmPerSecParam.NAME).setValue(null);
		getParameter(DepthTo2pt5kmPerSecParam.NAME).setValue(null);
	}

	public void setGridHybrid(boolean isGridHybrid) {
		this.isGridHybrid = isGridHybrid;
	}
	
	void initImrMap() {
		imrMap = Maps.newHashMap();
		imrMap.put(new NSHMP_BA_2008_Grid(), 0.3333);
		imrMap.put(new NSHMP_CB_2008_Grid(), 0.3333);
		imrMap.put(new NSHMP_CY_2008_Grid(), 0.3334);
	}
	
	static {
		tables = HashBasedTable.create();
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public void setSite(Site site) {
		// overridden as its unnecessary to push Site down to child imrs
		// only needed in getExceedProbabilities(f)
		this.site = site;
	}
	
	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		// overridden as its unnecessary to push Site down to child imrs
		// only needed in getExceedProbabilities(f)
		this.eqkRupture = eqkRupture;
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls)
			throws ParameterException {
		double d = eqkRupture.getRuptureSurface().getDistanceJB(
			site.getLocation());
		double m = eqkRupture.getMag();
		return table.get(d, m);
	}
	
	@Override
	public DiscretizedFunc getExceedProbFromParent(DiscretizedFunc imls) {
		return super.getExceedProbabilities(imls);
	}
	
	/**
	 * Triggers build of probability of exceedence table for current
	 * configuration of IMR.
	 * @param depthForMag function that will supply mag dependent depth values
	 */
	public void setTable(Function<Double, Double> depthForMag) {
		String ft = fltTypeParam.getValue();
		MechID mechID = ft.equals("Strike-Slip") ? MechID.STRIKE_SLIP : ft
			.equals("Reverse") ? MechID.STRIKE_REVERSE : MechID.STRIKE_NORMAL;
		String imrName = getIntensityMeasure().getName();
		Period p = imrName.equals(PGA_Param.NAME) ? Period.GM0P00 : Period
			.valueForPeriod((Double) getParameter(PeriodParam.NAME).getValue());
		table = tables.get(p, mechID);
		if (table == null) table = initTable(p, depthForMag, mechID, this);
	}
	
	private static final double R = 200;
	private static final double dR = 1;
	private static final double minM = 5;
	private static final double maxM = 7.6;
	private static final double dM = 0.1;

	@SuppressWarnings("unchecked")
	private static synchronized CurveTable initTable(Period period,
			Function<Double, Double> depthForMag, MechID mechID, ScalarIMR imr) {
		CurveTable table = tables.get(period, mechID);
		if (table != null) {
			System.out.println("Cached NGA table [" + period + ", " + mechID +
				"]");
			return table;
		}
		System.out.println("Building NGA table [" + period + ", " + mechID +
			"]");
		imr.setIntensityMeasure((period == Period.GM0P00) ? PGA_Param.NAME
			: SA_Param.NAME);
		if (period != Period.GM0P00) {
			imr.getParameter(PeriodParam.NAME).setValue(period.getValue());
		}
		table = CurveTable.create(R, dR, minM, maxM, dM, imr,
			period.getFunction(), depthForMag);
		tables.put(period, mechID, table);
//		System.out.println(table.size());
		return table;
	}

	class NSHMP_BA_2008_Grid extends NSHMP_BA_2008 {
		
		@Override
		public double getMean(int iper, double vs30, double rjb, double mag,
				String fltType, double pga4nl) {
			
			// remember that pga4ln term uses coeff index 0
			double Fm, Fd, Fs;
			double U=0, S=0, N=0, R=0;
			if(fltType.equals(FLT_TYPE_UNKNOWN)) {
				U = 1.0;
			} else if(fltType.equals(FLT_TYPE_NORMAL)) {
				N = (isGridHybrid) ? 0.5 : 1.0;
				S = (isGridHybrid) ? 0.5 : 0.0;
			} else if(fltType.equals(FLT_TYPE_REVERSE)) {
				R = (isGridHybrid) ? 0.5 : 1.0;
				S = (isGridHybrid) ? 0.5 : 0.0;
			} else {
				S = 1.0;
			}
			
			// Compute Fm
			double magDiff = mag-mh[iper];
			if (mag <= mh[iper]) {
				Fm = e1[iper]*U + e2[iper]*S + e3[iper]*N + e4[iper]*R + 
				e5[iper]*magDiff+e6[iper]*magDiff*magDiff;
			}
			else {
				Fm = e1[iper]*U + e2[iper]*S + e3[iper]*N + e4[iper]*R +
				e7[iper]*(mag-mh[iper]);
			}

			double r = Math.sqrt(rjb * rjb + h[iper] * h[iper]);
			Fd = (c1[iper] + c2[iper]*(mag-m_ref))*Math.log(r/r_ref) +
			c3[iper]*(r-r_ref);

			// site response term
			if(iper == 0) {
				Fs = 0; // pga4nl case
			} else {
				double Flin = b_lin[iper]*Math.log(vs30/v_ref);	

				// compute bnl
				double bnl = 0;
				if (vs30 <= v1) 
					bnl = b1[iper];
				else if (vs30 <= v2 && vs30 > v1) 
					bnl = (b1[iper] - b2[iper]) * Math.log(vs30/v2) / Math.log(v1/v2) + b2[iper];
				else if (vs30 < v_ref && vs30 > v2) 
					bnl = b2[iper] * Math.log(vs30/v_ref) / Math.log(v2/v_ref);
				else
					bnl = 0.0;

				// compute Fnl
				double Fnl;
				if(pga4nl <= a1)
					Fnl = bnl*Math.log(pgalow/0.1);
				else if (pga4nl <= a2 & pga4nl > a1) {
					double c, d, dX, dY;
					dX = Math.log(a2/a1);
					dY = bnl*Math.log(a2/pgalow);
					c = (3*dY-bnl*dX)/(dX*dX);
					d = -(2*dY-bnl*dX)/(dX*dX*dX);

					Fnl = bnl*Math.log(pgalow/0.1) + 
					c*Math.pow(Math.log(pga4nl/a1),2) +
					d*Math.pow(Math.log(pga4nl/a1),3);

				} else {
					Fnl = bnl*Math.log(pga4nl/0.1);
				}
				Fs= Flin+Fnl;
			}
//			System.out.println("Fm: " + Fm);
			return (Fm + Fd + Fs);
		}
		
		@Override
		public double getStdDev(int iper, String stdDevType, String fltType) {
			// NSHMP always uses unkown mech for grid sources
			return super.getStdDev(iper, stdDevType, 
				BA_2008_AttenRel.FLT_TYPE_UNKNOWN);
		}
	}

	class NSHMP_CB_2008_Grid extends NSHMP_CB_2008 {
		
		@Override
		public double getMean(int iper, double vs30, double rRup,
			double distJB,double f_rv,
			double f_nm, double mag, double dip, double depthTop,
			double depthTo2pt5kmPerSec,
			boolean magSaturation, double pga_rock) {

			if (isGridHybrid) {
				if (f_nm > 0) f_nm = 0.5;
				if (f_rv > 0) f_rv = 0.5;
			}
			
			return super.getMean(iper, vs30, rRup, distJB, f_rv, f_nm, mag,
				dip, depthTop, depthTo2pt5kmPerSec, magSaturation, pga_rock);
		}
	}

	class NSHMP_CY_2008_Grid extends NSHMP_CY_2008 {
		
		@Override
		protected void compute_lnYref(int iper, double f_rv, double f_nm,
				double rRup, double distRupMinusJB_OverRup,
				double distRupMinusDistX_OverRup, double f_hw, double dip,
				double mag, double depthTop, double aftershock) {
			
			if (isGridHybrid) {
				if (f_nm > 0) f_nm = 0.5;
				if (f_rv > 0) f_rv = 0.5;
			}
			
			super.compute_lnYref(iper, f_rv, f_nm, rRup,
				distRupMinusJB_OverRup, distRupMinusDistX_OverRup, f_hw, dip,
				mag, depthTop, aftershock);
		}
	}
	
	/*
	 * KLUDGY Outside of this class, we want to treat focal mechanism weights as
	 * their actual values that sum to 1. For compactness for now, we use this
	 * enum to identify curve tables. The 2008 NSHMP is limited to 100%
	 * strike-slip, %50 Strike-Slip %50 Reverse, and %50 Strike-Slip %50 Normal.
	 */
	private enum MechID {
		STRIKE_SLIP, STRIKE_REVERSE, STRIKE_NORMAL;
	}
	
	public static void main(String[] args) {
		NSHMP08_WUS_Grid imr = new NSHMP08_WUS_Grid();
		Period per = Period.GM0P00;
		imr.setIntensityMeasure((per == Period.GM0P00) ? "PGA" : "SA");
		// we need to do this check for WUS NGA's becuase PGA is not keyed to
		// 0.0 as with other, older IMRs.
		if (per != Period.GM0P00) {
			imr.getParameter(PeriodParam.NAME).setValue(per.getValue());
		}
		
		// ERF dependent settings
		imr.getParameter(FaultTypeParam.NAME).setValue(CB_2008_AttenRel.FLT_TYPE_REVERSE);
		imr.setGridHybrid(true);
		
		Function<Double, Double> func = new Function<Double, Double>() {
			@Override public Double apply(Double mag) {
				return (mag < 6.5) ? 5.0 : 1.0;
			}
		}; // this should otherwise be obtained from GridERF

		imr.setTable(func);
		
		// setting period triggers table build
//		System.out.println(tables.size());
//		for (Period p : tables.rowKeySet()) {
//			System.out.println(p + " " + tables.get(p));
//		}
//		System.out.println(tables.get(per));
		System.out.println(tables.get(per, MechID.STRIKE_REVERSE).get(10.5, 6.65));
//		System.out.println(tables.get(per).get(0,5.0));
	}

}
