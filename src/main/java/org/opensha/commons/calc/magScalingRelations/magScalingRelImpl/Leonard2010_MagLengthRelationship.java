package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;

/**
 * Leonard (2010) magnitude-length relationships (https://doi.org/10.1785/0120090189)
 * 
 * All values taken from Table 6
 */
public class Leonard2010_MagLengthRelationship extends MagLengthRelationship {
	
	public static Leonard2010_MagLengthRelationship STRIKE_SLIP = new Leonard2010_MagLengthRelationship(
			1.67, 4.17, "Leonard (2010) M-L, Strike Slip");
	public static Leonard2010_MagLengthRelationship DIP_SLIP = new Leonard2010_MagLengthRelationship(
			1.67, 4.24, "Leonard (2010) M-L, Dip Slip");
	public static Leonard2010_MagLengthRelationship STABLE_CONTINENTAL = new Leonard2010_MagLengthRelationship(
			1.67, 4.32, "Leonard (2010) M-L, Stable Continental");
	
	public static Leonard2010_MagLengthRelationship STRIKE_SLIP_SURFACE = new Leonard2010_MagLengthRelationship(
			1.52, 4.33, "Leonard (2010) M-L, Strike Slip Surface Rupture Length");
	public static Leonard2010_MagLengthRelationship DIP_SLIP_SURFACE = new Leonard2010_MagLengthRelationship(
			1.52, 4.4, "Leonard (2010) M-L, Dip Slip Surface Rupture Length");
	
	private double a;
	private double b;
	private String name;

	private Leonard2010_MagLengthRelationship(double a, double b, String name) {
		this.a = a;
		this.b = b;
		this.name = name;
	}

	@Override
	public double getMedianMag(double length) {
		return a*Math.log10(length) + b;
	}

	@Override
	public double getMagStdDev() {
		return Double.NaN;
	}

	@Override
	public double getMedianLength(double mag) {
		// 10^((M - b)/a)
		return Math.pow(10, (mag - b)/a);
	}

	@Override
	public double getLengthStdDev() {
		return Double.NaN;
	}

	@Override
	public String getName() {
		return name;
	}

}
