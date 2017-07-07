package org.opensha.sha.earthquake.param;

public enum BPTAveragingTypeOptions {
	
	// TRUE: AveRI, AveNTS
	// FALSE: AveRate, AveTS
	AVE_RI_AVE_TIME_SINCE("AveRI and AveTimeSince", true, false),
	AVE_RI_AVE_NORM_TIME_SINCE("AveRI and AveNormTimeSince", true, true),
	AVE_RATE_AVE_NORM_TIME_SINCE("AveRate and AveNormTimeSince", false, true);
	
	private String name;
	private boolean aveRI;
	private boolean aveNTS;
	
	private BPTAveragingTypeOptions(String name, boolean aveRI, boolean aveNTS) {
		this.name = name;
		this.aveRI = aveRI;
		this.aveNTS = aveNTS;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public boolean isAveRI() {
		return aveRI;
	}
	
	public boolean isAveNTS() {
		return aveNTS;
	}
	
	public String getCompactLabel() {
		String str;
		if (aveRI)
			str = "AveRI";
		else
			str = "AveRate";
		str += "&";
		if (aveNTS)
			str += "AveNormTS";
		else
			str += "AveTS";
		return str;
	}
	
	public String getFileSafeLabel() {
		return getCompactLabel().replaceAll("&", "_");
	}

}
