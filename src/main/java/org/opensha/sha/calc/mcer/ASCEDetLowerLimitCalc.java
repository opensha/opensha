package org.opensha.sha.calc.mcer;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.cybershake.calc.mcer.MCERDataProductsCalc;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class ASCEDetLowerLimitCalc {
	
private static TLDataLoader tlData;

	public static double getTl(Location loc) {
		synchronized (MCERDataProductsCalc.class) {
			if (tlData == null) {
				try {
					tlData = new TLDataLoader(
							CSVFile.readStream(TLDataLoader.class.getResourceAsStream(
									"/resources/data/site/USGS_TL/tl-nodes.csv"), true),
							CSVFile.readStream(TLDataLoader.class.getResourceAsStream(
									"/resources/data/site/USGS_TL/tl-attributes.csv"), true));
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}
		
		double tl = tlData.getValue(loc);
		Preconditions.checkState(!Double.isNaN(tl), "No TL data found for site at "+loc);
		return tl;
	}

	public static DiscretizedFunc calc(DiscretizedFunc xValsFunc, double vs30, Location loc) {
		return calc(xValsFunc, vs30, getTl(loc));
	}
	
	public static DiscretizedFunc calc(DiscretizedFunc xValsFunc, double vs30, double tl) {
		// convert vs30 from m/s to ft/s
		vs30 *= 3.2808399;
		double fa, fv;
		// these were the previous values, via e-mail from C.B. Crouse on 2/3/2015
//		if (vs30 > 5000) {
//			// site class A
//			fa = 0.8;
//			fv = 0.8;
//		} else if (vs30 > 2500) {
//			// site class B
//			fa = 1.0;
//			fv = 1.0;
//		} else if (vs30 > 1200) {
//			// site class C
//			fa = 1.0;
//			fv = 1.3;
//		} else if (vs30 > 600) {
//			// site class D
//			fa = 1.0;
//			fv = 1.5;
//		} else {
//			// site class E
//			fa = 0.9;
//			fv = 2.4;
//		}
		// new values via e-mail from C.B. Crouse on 10/12/2016
		if (vs30 > 5000) {
			// site class A
			fa = 0.8;
			fv = 0.8;
		} else if (vs30 > 2500) {
			// site class B
			fa = 0.9;
			fv = 0.8;
		} else if (vs30 > 1200) {
			// site class C
			fa = 1.2;
			fv = 1.4;
		} else if (vs30 > 600) {
			// site class D
			fa = 1.0;
			fv = 2.5;
		} else {
			// site class E
			fa = 1.0;
			fv = 4.0;
		}
		
		return calc(xValsFunc, fv, fa, tl);
	}
	
	public static double calc(double period, double vs30, Location loc) {
		ArbitrarilyDiscretizedFunc xValsFunc = new ArbitrarilyDiscretizedFunc();
		xValsFunc.set(period, 0d);
		DiscretizedFunc result = calc(xValsFunc, vs30, loc);
		Preconditions.checkState(result.size() == 1);
		Preconditions.checkState(result.getX(0) == period);
		return result.getY(0);
	}
	
	public static DiscretizedFunc calc(DiscretizedFunc xValsFunc, double fv, double fa, double tl) {
		ArbitrarilyDiscretizedFunc ret = new ArbitrarilyDiscretizedFunc();
		
		double firstRatioXVal = 0.08*fv/fa;
		double secondRatioXVal = 0.4*fv/fa;
		
		List<Double> xVals = Lists.newArrayList();
		for (Point2D pt : xValsFunc)
			xVals.add(pt.getX());
		// make sure that the discontinuities in the function are included for plotting purposes
		if (isWithinDomain(xValsFunc, tl) && !xValsFunc.hasX(tl))
			xVals.add(tl);
		if (isWithinDomain(xValsFunc, firstRatioXVal) && !xValsFunc.hasX(firstRatioXVal))
			xVals.add(firstRatioXVal);
		if (isWithinDomain(xValsFunc, secondRatioXVal) && !xValsFunc.hasX(secondRatioXVal))
			xVals.add(secondRatioXVal);
		
		for (double t : xVals) {
			double sa;
			if (t >= tl)
				sa = 0.6*fv*tl/(t*t);
			else if (t >= secondRatioXVal)
				sa = 0.6*fv/t;
			else if (t >= firstRatioXVal)
				sa = 1.5*fa;
			else
				// linear interpolation from (0, 0.6*fa) to (0.08*fv/fa, 1.5*fa)
				sa = (1.5*fa - 0.6*fa)*t/firstRatioXVal + 0.6*fa;
			ret.set(t, sa);
		}
		
		return ret;
	}
	
	private static boolean isWithinDomain(DiscretizedFunc func, double x) {
		return x >= func.getMinX() && x <= func.getMaxX();
	}
	
	// PGA values
	
	/**
	 * 21.5.2 Deterministic MCEG Peak Ground Acceleration.
	 * 
	 * The deterministic geometric mean peak ground acceleration
	 * shall be calculated as the largest 84th-percentile geometric mean peak ground acceleration for characteristic
	 * earthquakes on all known active faults within the site region. The deterministic geometric mean peak ground
	 * acceleration shall not be taken as lower than 0.5 FPGA, where FPGA is determined using Table 11.8-1 with the
	 * value of PGA taken as 0.5g.
	 * @param vs30
	 * @return
	 */
	public static double calcPGA_G(double vs30) {
		// from Table 11.8-1 with PGA=0.5
		double fPGA;
		if (vs30 > 5000) {
			// site class A
			fPGA = 0.8;
		} else if (vs30 > 2500) {
			// site class B
			fPGA = 0.9;
		} else if (vs30 > 1200) {
			// site class C
			fPGA = 1.2;
		} else if (vs30 > 600) {
			// site class D
			fPGA = 1.1;
		} else {
			// site class E
			fPGA = 1.2;
		}
		return 0.5 * fPGA;
	}

}
