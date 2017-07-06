package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;

/**
 * This class is used to perform common math operations on and between XYZ datasets.
 * 
 * @author kevin
 *
 */
public class XYZ_DataSetMath {
	
	private static boolean isGeo(XYZ_DataSet map1, XYZ_DataSet map2) {
		if (map1 instanceof GeoDataSet && map2 instanceof GeoDataSet)
			return true;
		return false;
	}
	
	/**
	 * Returns new <code>GeographicDataSetAPI</code> that represents the values in the
	 * two given maps added together.
	 * @param map1
	 * @param map2
	 * @return
	 */
	public static XYZ_DataSet add(XYZ_DataSet map1, XYZ_DataSet map2) {
		if (isGeo(map1, map2))
			return GeoDataSetMath.add((GeoDataSet)map1, (GeoDataSet)map2);
		ArbDiscrXYZ_DataSet sum = new ArbDiscrXYZ_DataSet();
		
		for (int i=0; i<map1.size(); i++) {
			Point2D point = map1.getPoint(i);
			double val1 = map1.get(i);
			int map2Index = map2.indexOf(point);
			if (map2Index >= 0) {
				double val2 = map2.get(map2Index);
				sum.set(point, val1 + val2);
			}
		}
		return sum;
	}
	
	/**
	 * Returns new <code>GeographicDataSetAPI</code> that represents the values in the
	 * minuend map minus the values in the subtrahend map.
	 * @param map1
	 * @param map2
	 * @return
	 */
	public static XYZ_DataSet subtract(XYZ_DataSet minuend, XYZ_DataSet subtrahend) {
		if (isGeo(minuend, subtrahend))
			return GeoDataSetMath.subtract((GeoDataSet)minuend, (GeoDataSet)subtrahend);
		ArbDiscrXYZ_DataSet difference = new ArbDiscrXYZ_DataSet();
		
		for (int i=0; i<minuend.size(); i++) {
			Point2D point = minuend.getPoint(i);
			double val1 = minuend.get(i);
			int map2Index = subtrahend.indexOf(point);
			if (map2Index >= 0) {
				double val2 = subtrahend.get(map2Index);
				difference.set(point, val1 - val2);
			}
		}
		
		return difference;
	}
	
	/**
	 * Returns new <code>GeographicDataSetAPI</code> that represents the values in the
	 * two given maps multiplied together.
	 * @param map1
	 * @param map2
	 * @return
	 */
	public static XYZ_DataSet multiply(XYZ_DataSet map1, XYZ_DataSet map2) {
		if (isGeo(map1, map2))
			return GeoDataSetMath.multiply((GeoDataSet)map1, (GeoDataSet)map2);
		ArbDiscrXYZ_DataSet product = new ArbDiscrXYZ_DataSet();
		
		for (int i=0; i<map1.size(); i++) {
			Point2D point = map1.getPoint(i);
			double val1 = map1.get(i);
			int map2Index = map2.indexOf(point);
			if (map2Index >= 0) {
				double val2 = map2.get(map2Index);
				product.set(point, val1 * val2);
			}
		}
		return product;
	}
	
	/**
	 * Returns new <code>GeographicDataSetAPI</code> that represents the values in the
	 * minuend map minus the values in the subtrahend map.
	 * @param map1
	 * @param map2
	 * @return
	 */
	public static XYZ_DataSet divide(XYZ_DataSet dividend, XYZ_DataSet divisor) {
		if (isGeo(dividend, divisor))
			return GeoDataSetMath.divide((GeoDataSet)dividend, (GeoDataSet)divisor);
		ArbDiscrXYZ_DataSet quotient = new ArbDiscrXYZ_DataSet();
		
		for (int i=0; i<dividend.size(); i++) {
			Point2D point = dividend.getPoint(i);
			double val1 = dividend.get(i);
			int map2Index = divisor.indexOf(point);
			if (map2Index >= 0) {
				double val2 = divisor.get(map2Index);
				quotient.set(point, val1 / val2);
			}
		}
		
		return quotient;
	}

}
