package org.opensha.nshmp;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.math3.util.MathUtils;
import org.apache.commons.math3.util.Precision;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * List of 34 city sites in regions of the United States of greatest seismic
 * risk as specified in the 2009 edition of the <a
 * href="http://www.fema.gov/library/viewRecord.do?id=4103" target=_blank">NEHRP
 * Recommended Seismic Provisions</a>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
@SuppressWarnings("all")
public enum NEHRP_TestCity {

	// SoCal
	LOS_ANGELES(34.05, -118.25),
	CENTURY_CITY(34.05, -118.40),
	NORTHRIDGE(34.20, -118.55),
	LONG_BEACH(33.80, -118.20),
	IRVINE(33.65, -117.80),
	RIVERSIDE(33.95, -117.40),
	SAN_BERNARDINO(34.10, -117.30),
	SAN_LUIS_OBISPO(35.30, -120.65),
	SAN_DIEGO(32.70, -117.15),
	SANTA_BARBARA(34.45, -119.70),
	VENTURA(34.30, -119.30),

	// NoCal
	OAKLAND(37.80, -122.25),
	CONCORD(37.95, -122.00),
	MONTEREY(36.60, -121.90),
	SACRAMENTO(38.60, -121.50),
	SAN_FRANCISCO(37.75, -122.40),
	SAN_MATEO(37.55, -122.30),
	SAN_JOSE(37.35, -121.90),
	SANTA_CRUZ(36.95, -122.05),
	VALLEJO(38.10, -122.25),
	SANTA_ROSA(38.45, -122.70),

	// PNW
	SEATTLE(47.60, -122.30),
	TACOMA(47.25, -122.45),
	EVERETT(48.00, -122.20),
	PORTLAND(45.50, -122.65),

	// B&R
	SALT_LAKE_CITY(40.75, -111.90),
	BOISE(43.60, -116.20),
	RENO(39.55, -119.80),
	LAS_VEGAS(36.20, -115.15),

	// CEUS
	ST_LOUIS(38.60, -90.20),
	MEMPHIS(35.15, -90.05),
	CHARLESTON(32.80, -79.95),
	CHICAGO(41.85, -87.65),
	NEW_YORK(40.75, -74.00);

	private Location loc;

	private NEHRP_TestCity(double lat, double lon) {
		loc = new Location(lat, lon);
	}

	/**
	 * Returns the geographic <code>Location</code> of the city.
	 * @return the <code>Location</code> of the city
	 */
	public Location location() {
		return loc;
	}
	
	/**
	 * Returns the location of the city shifted to the nearest 0.1 lat lon
	 * unit.
	 * @return the shifted location
	 */
	public Location shiftedLocation() {
		// Precision rounds negatives down
		double lat = Precision.round(loc.getLatitude(), 1);
		double lon = Precision.round(loc.getLongitude(), 1);
		return new Location(lat, lon, loc.getDepth());
	}

	/**
	 * Returns all California cities.
	 * @return a {@code Set} of California cities
	 */
	public static EnumSet<NEHRP_TestCity> getCA() {
		return EnumSet.range(LOS_ANGELES, SANTA_ROSA);
	}
	
	/**
	 * Returns all California cities.
	 * @return a {@code Set} of California cities
	 */
	public static EnumSet<NEHRP_TestCity> getShortListCA() {
		return EnumSet.of(LOS_ANGELES, RIVERSIDE, SAN_DIEGO, SANTA_BARBARA,
			OAKLAND, SACRAMENTO, SAN_FRANCISCO, SAN_JOSE);
	}
	
	/**
	 * Returns the city associated with the supplied location or {@code null}
	 * if no city is coincident with the location.
	 * @param loc location to search for
	 * @return the city at location
	 * @see LocationUtils#areSimilar(Location, Location);
	 */
	public static NEHRP_TestCity forLocation(Location loc) {
		for (NEHRP_TestCity city : NEHRP_TestCity.values()) {
			if (LocationUtils.areSimilar(city.loc, loc)) return city;
		}
		return null;
	}
	
	public static Map<String, Location> asMap() {
		Map<String, Location> cityMap = Maps.newHashMap();
		for (NEHRP_TestCity city : NEHRP_TestCity.values()) {
			cityMap.put(city.name(), city.location());
		}
		return ImmutableMap.copyOf(cityMap);
	}
	
	/**
	 * Returns a site object for this location.
	 * @return
	 */
	public Site getSite() {
		Site s = new Site(loc, this.name());
		// CY AS
		DepthTo1pt0kmPerSecParam d10p = new DepthTo1pt0kmPerSecParam(null,
			0, 1000, true);
		d10p.setValueAsDefault();
		s.addParameter(d10p);
		// CB
		DepthTo2pt5kmPerSecParam d25p = new DepthTo2pt5kmPerSecParam(null,
			0, 1000, true);
		d25p.setValueAsDefault();
		s.addParameter(d25p);
		// all
		Vs30_Param vs30p = new Vs30_Param(760);
		vs30p.setValueAsDefault();
		s.addParameter(vs30p);
		// AS CY
		Vs30_TypeParam vs30tp = new Vs30_TypeParam();
		s.addParameter(vs30tp);
		return s;
	}

	public static void main(String[] args) {
		for (NEHRP_TestCity city : NEHRP_TestCity.values()) {
			Location loc = city.location();
			System.out.println(city.name() + "," +
				Precision.round(loc.getLatitude(), 2) + "," +
				Precision.round(loc.getLongitude(), 2));
			System.out.println();
		}
	}
	

	@Override
	public String toString() {
		return WordUtils.capitalizeFully(name().replace('_',' '));
	}


}
