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
	private int id;
	private ProbEqkSource source;

	public DisaggregationSourceRuptureInfo(String name, double rate, int id, ProbEqkSource source) {

		this.name = name;
		this.rate = rate;
		this.id = id;
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
	
	public ProbEqkSource getSource() {
		return source;
	}
}
