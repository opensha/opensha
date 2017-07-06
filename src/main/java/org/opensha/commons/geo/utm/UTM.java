package org.opensha.commons.geo.utm;

import java.util.Locale;

/**
 * Class representing UTM-coordinates. Based on code from stack overflow.
 * @see <a href="https://stackoverflow.com/questions/176137/java-convert-lat-lon-to-utm">Stack Overflow</a>
 * @see <a href="https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system">Wikipedia-entry on UTM</a>
 * @author Rolf Rander Næss
 *
 */
public class UTM
{
	private double easting;
	private double northing;
	private int zone;
	private char letter;

	public double getEasting() {
		return easting;
	}

	public double getNorthing() {
		return northing;
	}

	public int getZone() {
		return zone;
	}

	public char getLetter() {
		return letter;
	}

	public String toString() {
		return String.format("%s %c %s %s", zone, letter, easting, northing);
	}

	/**
	 * Tests the exact representation. There might be more representations for
	 * the same geographical point with different letters or zones, but that is
	 * not taken into account.
	 */
	public boolean equals(Object o) {
		if(o instanceof UTM) {
			UTM other = (UTM)o;
			return (zone == other.zone) &&
					(letter == other.letter) &&
					(easting == other.easting) &&
					(northing == other.northing);
		}
		return false;
	}

	@Override
	public int hashCode() {
		long least = Double.doubleToRawLongBits(easting);
		long lnort = Double.doubleToRawLongBits(northing);
		long x = least ^ lnort;
		return (int)(x ^ (x >>> 32));
	}

	public UTM(int zone, char letter, double easting, double northing) {
		this.zone = zone;
		this.letter = Character.toUpperCase(letter);
		this.easting = easting;
		this.northing = northing;
	}

	public UTM(String utm) {
		String[] parts=utm.split(" ");
		zone=Integer.parseInt(parts[0]);
		letter=parts[1].toUpperCase(Locale.ENGLISH).charAt(0);
		easting=Double.parseDouble(parts[2]);
		northing=Double.parseDouble(parts[3]);           
	}

	public UTM(WGS84 wgs) {
		fromWGS84(wgs.getLatitude(), wgs.getLongitude());
	}

	private void fromWGS84(double latitude, double longitude) {
		zone= (int) Math.floor(longitude/6+31);
		if (latitude<-72) 
			letter='C';
		else if (latitude<-64) 
			letter='D';
		else if (latitude<-56)
			letter='E';
		else if (latitude<-48)
			letter='F';
		else if (latitude<-40)
			letter='G';
		else if (latitude<-32)
			letter='H';
		else if (latitude<-24)
			letter='J';
		else if (latitude<-16)
			letter='K';
		else if (latitude<-8) 
			letter='L';
		else if (latitude<0)
			letter='M';
		else if (latitude<8)  
			letter='N';
		else if (latitude<16) 
			letter='P';
		else if (latitude<24) 
			letter='Q';
		else if (latitude<32) 
			letter='R';
		else if (latitude<40) 
			letter='S';
		else if (latitude<48) 
			letter='T';
		else if (latitude<56) 
			letter='U';
		else if (latitude<64) 
			letter='V';
		else if (latitude<72) 
			letter='W';
		else
			letter='X';
		easting=0.5*Math.log((1+Math.cos(latitude*Math.PI/180)*Math.sin(longitude*Math.PI/180-(6*zone-183)*Math.PI/180))/(1-Math.cos(latitude*Math.PI/180)*Math.sin(longitude*Math.PI/180-(6*zone-183)*Math.PI/180)))*0.9996*6399593.62/Math.pow((1+Math.pow(0.0820944379, 2)*Math.pow(Math.cos(latitude*Math.PI/180), 2)), 0.5)*(1+ Math.pow(0.0820944379,2)/2*Math.pow((0.5*Math.log((1+Math.cos(latitude*Math.PI/180)*Math.sin(longitude*Math.PI/180-(6*zone-183)*Math.PI/180))/(1-Math.cos(latitude*Math.PI/180)*Math.sin(longitude*Math.PI/180-(6*zone-183)*Math.PI/180)))),2)*Math.pow(Math.cos(latitude*Math.PI/180),2)/3)+500000;
		easting=Math.round(easting*100)*0.01;
		northing = (Math.atan(Math.tan(latitude*Math.PI/180)/Math.cos((longitude*Math.PI/180-(6*zone -183)*Math.PI/180)))-latitude*Math.PI/180)*0.9996*6399593.625/Math.sqrt(1+0.006739496742*Math.pow(Math.cos(latitude*Math.PI/180),2))*(1+0.006739496742/2*Math.pow(0.5*Math.log((1+Math.cos(latitude*Math.PI/180)*Math.sin((longitude*Math.PI/180-(6*zone -183)*Math.PI/180)))/(1-Math.cos(latitude*Math.PI/180)*Math.sin((longitude*Math.PI/180-(6*zone -183)*Math.PI/180)))),2)*Math.pow(Math.cos(latitude*Math.PI/180),2))+0.9996*6399593.625*(latitude*Math.PI/180-0.005054622556*(latitude*Math.PI/180+Math.sin(2*latitude*Math.PI/180)/2)+4.258201531e-05*(3*(latitude*Math.PI/180+Math.sin(2*latitude*Math.PI/180)/2)+Math.sin(2*latitude*Math.PI/180)*Math.pow(Math.cos(latitude*Math.PI/180),2))/4-1.674057895e-07*(5*(3*(latitude*Math.PI/180+Math.sin(2*latitude*Math.PI/180)/2)+Math.sin(2*latitude*Math.PI/180)*Math.pow(Math.cos(latitude*Math.PI/180),2))/4+Math.sin(2*latitude*Math.PI/180)*Math.pow(Math.cos(latitude*Math.PI/180),2)*Math.pow(Math.cos(latitude*Math.PI/180),2))/3);
		if (letter<'M')
			northing = northing + 10000000;
		northing=Math.round(northing*100)*0.01;
	}

}
