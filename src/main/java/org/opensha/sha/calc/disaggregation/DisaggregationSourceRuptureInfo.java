package org.opensha.sha.calc.disaggregation;

import org.opensha.sha.earthquake.ProbEqkSource;

/**
 * <p>Title: DisaggregationSourceInfo</p>
 *
 * <p>Description: Stores the Source info. required for Disaggregation.</p>
 *
 * @author
 * @version 1.0
 */
public class DisaggregationSourceRuptureInfo {

	private String name;
	private double rate;
	private double eventRate;
	private double mag;
	private double distance;
	private int id;
	private ProbEqkSource source;

	public DisaggregationSourceRuptureInfo(String name, double rate, int id, ProbEqkSource source) {

		this.name = name;
		this.rate = rate;
		this.id = id;
		this.source = source;
	}

	public DisaggregationSourceRuptureInfo(String name, double eventRate, double rate,
			int id,double mag,double distance, ProbEqkSource source) {
		this.name = name;
		this.rate = rate;
		this.id = id;
		this.eventRate = eventRate;
		this.mag = mag;
		this.distance = distance;
		this.source = source;
	}


	public DisaggregationSourceRuptureInfo(String name, double eventRate, double rate, int id, ProbEqkSource source) {

		this.name = name;
		this.rate = rate;
		this.id = id;
		this.eventRate = eventRate;
		this.source = source;
	}

	public int getId(){
		return id;
	}


	public double getRate(){
		return rate;
	}

	public String getName(){
		return name;
	}

	public double getEventRate(){
		return eventRate;
	}

	public double getMag(){
		return mag;
	}

	public double getDistance(){
		return distance;
	}
	
	public ProbEqkSource getSource() {
		return source;
	}
}
