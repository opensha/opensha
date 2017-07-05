package scratch.UCERF3.utils;

import java.util.ArrayList;

import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

public class GeologicSlipRate {

	private boolean isMax;
	private boolean isMin;
	private boolean isRange;

	private double discrete;
	private double min;
	private double max;

	private Location loc;
	
	private String valString;
	
	private int sectID = -1;

	public GeologicSlipRate(Location loc, double min, double max) {
		this.loc = loc;

		isMax = false;
		isMin = false;
		isRange = true;
		discrete = Double.NaN;
		this.min = min;
		this.max = max;
	}

	public GeologicSlipRate(Location loc, boolean isMin, boolean isMax, double value) {
		this.loc = loc;
		Preconditions.checkArgument(!(isMin && isMax), "isMin and isMax can't both be true!");

		this.isMin = isMin;
		this.isMax = isMax;
		this.isRange = false;
		this.discrete = value;
		this.min = Double.NaN;
		this.max = Double.NaN;
	}

	public double getValue() {
		if (isRange)
			return (max + min)/2d;
		else
			return discrete;
	}

	public boolean isMax() {
		return isMax;
	}

	public boolean isMin() {
		return isMin;
	}

	public boolean isRange() {
		return isRange;
	}
	
	public String getValString() {
		return valString;
	}
	
	public void setValString(String description) {
		this.valString = description;
	}
	
	public Location getLocation() {
		return loc;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}
	
	public void setSectID(int sectID) {
		this.sectID = sectID;
	}
	
	public int getSectID() {
		return sectID;
	}

	public static String numbersSpacesOnly(String str, boolean allowMinus) {
		if (str == null) {
			return null;
		}

		StringBuffer strBuff = new StringBuffer();
		char c;

		for (int i = 0; i < str.length() ; i++) {
			c = str.charAt(i);

			if (Character.isDigit(c) || c == ' ' || c == '.'
				|| (allowMinus && c == '-')) {
				strBuff.append(c);
			}
		}
		return strBuff.toString();
	}
	
	private static String[] removeEmpty(String[] vals) {
		ArrayList<String> newVals = new ArrayList<String>();
		for (String val : vals) {
			if (val.length() > 0)
				newVals.add(val);
		}
		String[] ret = new String[newVals.size()];
		for (int i=0; i<ret.length; i++) {
			ret[i] = newVals.get(i);
		}
		return ret;
	}

	public static GeologicSlipRate fromString(Location loc, String str) {
		GeologicSlipRate rate;
		str = str.trim();
		if (str.contains("-")) {
			// it's a range
			String numSpaces = numbersSpacesOnly(str, false);
			String[] vals = removeEmpty(numSpaces.split(" "));
			if (vals.length != 2) {
				String valStr = null;
				for (String val : vals) {
					if (valStr == null)
						valStr = "";
					else
						valStr += ",";
					valStr += val;
				}
				throw new IllegalStateException("could not parse range: "+str+" (vals: "+valStr+")");
			}
			double min = Double.parseDouble(vals[0]);
			double max = Double.parseDouble(vals[1]);
			
			rate = new GeologicSlipRate(loc, min, max);
		} else if (str.contains("±")) {
			String numSpaces = numbersSpacesOnly(str, false);
			double val = Double.parseDouble(removeEmpty(numSpaces.split(" "))[0]);
			rate = new GeologicSlipRate(loc, false, false, val);
		} else {
			boolean isMin = false;
			boolean isMax = false;
			if (str.contains("<") || str.contains("≤"))
				isMax = true;
			else if (str.contains(">") || str.contains("≥"))
				isMin = true;
			String numSpaces = numbersSpacesOnly(str, false);
			double val = Double.parseDouble(removeEmpty(numSpaces.split(" "))[0]);
			rate = new GeologicSlipRate(loc, isMin, isMax, val);
		}
		rate.setValString(str);
		return rate;
	}

	@Override
	public String toString() {
//		return "GeologicSlipRate [isMax=" + isMax + ", isMin=" + isMin
//				+ ", isRange=" + isRange + ", discrete=" + discrete + ", min="
//				+ min + ", max=" + max + ", loc=" + loc + "]";
		String ret = "Slip Rate: "+getValue();
		if (getValString() != null)
			ret += " ("+getValString()+")";
		return ret;
	}

}
