package org.opensha.sha.faultSurface;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.geo.json.Geometry.GeometryCollection;
import org.opensha.commons.geo.json.Geometry.LineString;
import org.opensha.commons.geo.json.Geometry.MultiLineString;
import org.opensha.commons.geo.json.Geometry.MultiPolygon;
import org.opensha.commons.geo.json.Geometry.Polygon;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionPolygonBuilder;

import com.google.common.base.Preconditions;

public final class GeoJSONFaultSection implements FaultSection {
	
	// required data
	private int id;
	private String name;
	private double dip;
	private double rake;
	private double upperDepth;
	private double lowerDepth;
	private float dipDirection;
	private FaultTrace trace;
	private Region zonePolygon;
	private FeatureProperties properties;
	
	// common data to cache locally (to avoid more expensive FetureProperties queries)
	private double aveLongTermSlipRate;
	private double slipRateStdDev;
	private String parentSectionName;
	private int parentSectionId = -1;
	private int subSectionIndex = -1;
	private int subSectionIndexAlong = -1;
	private int subSectionIndexDownDip = -1;
	private long dateOfLastEventMillis = Long.MIN_VALUE;
	private int hashCode;
	private FaultTrace lowerTrace; // if supplied, will use an approximately gridded surface
	
	// helpers
	private StirlingSurfaceCache stirlingCache;
	// for faults with lower traces
	private ApproxEvenlyGriddedSurfaceCache approxGriddedCache;
	
	/*
	 * core property names
	 */
	
	/**
	 * Integer ID of this fault section.
	 */
	public static final String FAULT_ID = "FaultID";
	/**
	 * String name of this fault section.
	 */
	public static final String FAULT_NAME = "FaultName";
	/**
	 * Dip angle in decimal degrees
	 */
	public static final String DIP = "DipDeg";
	/**
	 * Dip direction in decimal degrees
	 */
	public static final String DIP_DIR = "DipDir";
	/**
	 * Rake angle in decimal degrees
	 */
	public static final String RAKE = "Rake";
	/**
	 * Lower seismogetnic depth in km
	 */
	public static final String LOW_DEPTH = "LowDepth";
	/**
	 * Upper seismogetnic depth in km
	 */
	public static final String UPPER_DEPTH = "UpDepth";
	
	/*
	 * optional property names
	 */
	
	/**
	 * Data of last event in epoch milliseconds if known, else {@link Long#MIN_VALUE}
	 */
	public static final String DATE_LAST = "DateLastEvent";
	/**
	 * Slip in meters of the last event that ruptured this fault
	 */
	public static final String SLIP_LAST = "SlipLastEvent";
	/**
	 * Fraction (value in the range [0,1)) of the fault area that is aseismic, typically applied by increasing the
	 * upper depth of the fault such that the area is reduced by this fraction.
	 */
	public static final String ASEIS = "AseismicSlipFactor";
	/**
	 * Fraction (value in the range [0,1]) of the slip rate of this fault that is released seismically.
	 */
	public static final String COUPLING = "CouplingCoeff";
	/**
	 * Average long-term on-plane slip rate of this fault in mm/yr.
	 */
	public static final String SLIP_RATE = "SlipRate";
	/**
	 * Integer ID of the parent to this fault. This is typically used when subdividing a fault into subsections, and
	 * will point to the ID of the original fault section.
	 */
	public static final String PARENT_ID = "ParentID";
	/**
	 * Name of the parent to this fault. This is typically used when subdividing a fault into subsections, and will
	 * give the name of the original fault section.
	 */
	public static final String PARENT_NAME = "ParentName";
	/**
	 * Standard deviation of the average long-term slip rate of this fault in mm/yr.
	 */
	public static final String SLIP_STD_DEV = "SlipRateStdDev";
	/**
	 * Boolean indicating that this fault is a Connector (currently unused).
	 */
	public static final String CONNECTOR = "Connector";
	/**
	 * Creep rate of this fault in mm/yr, usually <= slip rate
	 */
	public static final String CREEP_RATE = "CreepRate";
	/**
	 * Flag denoting that this fault section is a proxy fault and not an estimate of the actual fault geometry
	 */
	public static final String PROXY = "Proxy";
	// use MultiLineString instead
	@Deprecated private static final String LOWER_TRACE = "LowerTrace";
	
	/*
	 * optional subsection property names
	 */
	
	/**
	 * Subsection index, 0-based, for a subsectioned fault. The first subsection with the same {@link #PARENT_ID} should
	 * be 0, then 1, etc.
	 */
	public static final String SUB_SECT_INDEX = "SubSectIndex";
	/**
	 * Along-strike subsection (column) index (0-based) for faults that have subsections both in the along-strike and
	 * down-dip directions. An index of 0 means the first subsection in the along-strike direction.
	 */
	public static final String SUB_SECT_INDEX_ALONG = "SubSectIndexAlong";
	/**
	 * Down-dip subsection (row) index (0-based) for faults that have subsections both in the along-strike and
	 * down-dip directions. An index of 0 means that this subsection is in the uppermost row of the original fault.
	 */
	public static final String SUB_SECT_INDEX_DOWN = "SubSectIndexDown";
	
	public static class Builder {
		private final Feature feature;
		private final FeatureProperties props;
		
		public Builder(int faultID, String faultName, FaultTrace trace) {
			this(faultID, faultName, new Geometry.LineString(trace));
		}
		
		public Builder(int faultID, String faultName, Geometry geometry) {
			this(faultID, faultName, geometry, null);
		}
		
		public Builder(int faultID, String faultName, Geometry geometry, FeatureProperties props) {
			if (props == null)
				props = new FeatureProperties();
			props.set(FAULT_ID, faultID);
			props.set(FAULT_NAME, faultName);
			this.props = props;
			this.feature = new Feature(geometry, props);
		}
		
		public Builder rake(double rake) {
			props.set(RAKE, rake);
			return this;
		}
		
		public Builder dip(double dip) {
			props.set(DIP, dip);
			return this;
		}
		
		public Builder upperDepth(double upperDepth) {
			props.set(UPPER_DEPTH, upperDepth);
			return this;
		}
		
		public Builder lowerDepth(double lowerDepth) {
			props.set(LOW_DEPTH, lowerDepth);
			return this;
		}
		
		public Builder dipDir(float dipDir) {
			props.set(DIP_DIR, dipDir);
			return this;
		}
		
		public Builder slipRate(double slipRate) {
			props.set(SLIP_RATE, slipRate);
			return this;
		}
		
		public Builder slipRateStdDev(double slipRateStdDev) {
			props.set(SLIP_STD_DEV, slipRateStdDev);
			return this;
		}
		
		public Feature getFeature() {
			return feature;
		}
		
		public GeoJSONFaultSection build() {
			return fromFeature(feature);
		}
	}

	private GeoJSONFaultSection(Feature feature) {
		Preconditions.checkNotNull(feature, "feature cannot be null");
		Preconditions.checkNotNull(feature.geometry, "geometry cannot be null");
		Preconditions.checkNotNull(feature.properties, "properties cannot be null");
		
		// these are required properties
		
		// copy the properties over so that updates to this don't affect the feature as passed in
		this.properties = new FeatureProperties(feature.properties);
		
		if (feature.id != null && feature.id instanceof Number) {
			id = ((Number)feature.id).intValue();
		} else {
			Preconditions.checkState(feature.properties.containsKey(FAULT_ID),
					"Must supply feature ID or %s property", FAULT_ID);
			id = properties.getInt(FAULT_ID, -1);
		}
		Preconditions.checkState(id >= 0, "ID must be >= 0");
		
		name = properties.get(FAULT_NAME, null); // can be null
		
		rake = properties.getDouble(RAKE, Double.NaN);
//		checkPropFinite(RAKE, rake); // allow rakes to be attached later, don't enforce that it's specified now
		
		upperDepth = properties.getDouble(UPPER_DEPTH, Double.NaN);
		lowerDepth = properties.getDouble(LOW_DEPTH, Double.NaN);
		dip = properties.getDouble(DIP, Double.NaN);
		
		dipDirection = Float.NaN;
		if (properties.containsKey(DIP_DIR)) {
			Object dipDirProp = properties.get(DIP_DIR);
			if (dipDirProp instanceof Number) {
				dipDirection = ((Number)dipDirProp).floatValue();
			} else if (dipDirProp instanceof String) {
				try {
					dipDirection = (float)Double.parseDouble(dipDirProp.toString());
				} catch (NumberFormatException e) {
					// do nothing, probably an NSHM string such as 'SE'
				}
			}
		}
		
		setGeometry(feature.geometry);
		Preconditions.checkNotNull(trace,
				"Didn't find a FaultTrace in the supplied Feature. LineString and MultiLineString are supported");
		
		checkLoadDeprecatedLowerTraceProperty();
		
		if (lowerTrace != null) {
			// this checks that the lower trace is below the upper trace, and the the right hand rule is followed (if dipping).
			// it also sets the upper/lower depths, dip, and dip direction as needed (if omitted in the GeoJSON properties).
			validateLowerTrace(trace, lowerTrace);
		} else if (!Double.isFinite(upperDepth)) {
			// upper depth is missing, might be able to infer from upper fault trace
			Preconditions.checkState(feature.geometry.isSerializeZeroDepths(),
					"Can't infer upper depth: upper depth not specified, and fault trace did not specify depths.");
			// compute average upper depth
			int num = Integer.max(10, (int)trace.getTraceLength());
			upperDepth = FaultUtils.resampleTrace(trace, num).stream().map(S -> S.depth).mapToDouble(d->d).average().getAsDouble();
			properties.set(UPPER_DEPTH, upperDepth);
		}
		
		checkPropFinite(UPPER_DEPTH, upperDepth);
		FaultUtils.assertValidDepth(upperDepth);
		checkPropFinite(LOW_DEPTH, lowerDepth);
		FaultUtils.assertValidDepth(lowerDepth);
		checkPropFinite(DIP, dip);
		FaultUtils.assertValidDip(dip);
		
		if (!Double.isFinite(dipDirection)) {
			if (lowerTrace == null) {
				// use upper trace
				setDipDirection((float)(trace.getAveStrike()+90d));
			} else {
				// calculate between traces
				ApproxEvenlyGriddedSurface surf = getApproxGriddedSurface(1d, false);
				setDipDirection((float)surf.getAveDipDirection());
			}
		}

		cacheCommonValues();
		
		if (parentSectionId >= 0 && subSectionIndex == -1 && name != null && name.contains(STANDARD_SUBSECTION_PREFIX)) {
			// this is a subsection that was created before we added the subsection index field
			try {
				String suffix = name.substring(name.indexOf(STANDARD_SUBSECTION_PREFIX)+STANDARD_SUBSECTION_PREFIX.length()).trim();
				int ssIndex = Integer.parseInt(suffix);
				if (ssIndex >= 0) {
					properties.set(SUB_SECT_INDEX, ssIndex);
					properties.set(SUB_SECT_INDEX_ALONG, ssIndex);
					cacheCommonValues();
				}
			} catch (NumberFormatException e) {}
		}
	}
	
	private void checkLoadDeprecatedLowerTraceProperty() {
		Geometry lowerTraceGeom = properties.get(LOWER_TRACE, null);
		if (lowerTraceGeom != null) {
			System.err.println("WARNING: deprecated "+LOWER_TRACE+" property used; use a MultiLineString geometry instead");
			properties.remove(LOWER_TRACE);
			Preconditions.checkState(lowerTrace == null,
					"Can't have both an explicit LowerTrace and a lower trace in a MultiLineString geometry");
			LocationList line;
			if (lowerTraceGeom instanceof LineString) {
				line = ((LineString)lowerTraceGeom).line;
			} else if (lowerTraceGeom instanceof MultiLineString) {
				MultiLineString multiLine = (MultiLineString)lowerTraceGeom;
				Preconditions.checkState(multiLine.lines.size() == 1,
						"Lower trace MultiLineString only supported if exactly 1 line");
				line = multiLine.lines.get(0);
			} else {
				throw new IllegalStateException("Unsupported lower trace type: "+lowerTraceGeom.type);
			}
			lowerTrace = new FaultTrace(name);
			lowerTrace.addAll(line);
		}
	}

	private void cacheCommonValues() {
		// cache common values
		this.aveLongTermSlipRate = properties.getDouble(SLIP_RATE, Double.NaN);
		this.slipRateStdDev = properties.getDouble(SLIP_STD_DEV, Double.NaN);
		this.parentSectionName = properties.get(PARENT_NAME, null);
		this.parentSectionId = properties.getInt(PARENT_ID, -1);
		this.dateOfLastEventMillis = properties.getLong(DATE_LAST, Long.MIN_VALUE);
		this.subSectionIndex = properties.getInt(SUB_SECT_INDEX, -1);
		this.subSectionIndexAlong = properties.getInt(SUB_SECT_INDEX_ALONG, -1);
		this.subSectionIndexDownDip = properties.getInt(SUB_SECT_INDEX_DOWN, -1);

		updateHashCode();
	}

	private void setGeometry(Geometry geometry) {
		if (geometry instanceof GeometryCollection) {
			for (Geometry subGeom : ((GeometryCollection)geometry).geometries) {
				Preconditions.checkState(!(subGeom instanceof GeometryCollection),
						"GeoJSONFaultSections don't support nested geometry collections.");
				setGeometry(subGeom);
			}
		} else if (geometry instanceof LineString) {
			LocationList line = ((LineString)geometry).line;
			Preconditions.checkNotNull(line, "LineString has null geometry");
			Preconditions.checkState(!line.isEmpty(), "LineString is empty");
			Preconditions.checkState(trace == null, "Encountered a LineString but already have a fault trace");
			trace = new FaultTrace(name);
			trace.addAll(line);
		} else if (geometry instanceof MultiLineString) {
			List<LocationList> lines = ((MultiLineString)geometry).lines;
			Preconditions.checkNotNull(lines, "MultiLineString has null geometry");
			Preconditions.checkState(!lines.isEmpty(), "MultiLineString is empty");
			Preconditions.checkState(trace == null, "Encountered a MultiLineString but already have a fault trace");
			trace = new FaultTrace(name);
			trace.addAll(lines.get(0));
			if (lines.size() > 1) {
				Preconditions.checkState(lines.size() == 2, "MultiLineString supplied; must either containe 1 trace (upper only)"
						+ " or 2 (upper and then lower).");
				Preconditions.checkState(geometry.isSerializeZeroDepths(),
						"Depths must be explicitly supplied in the GeoJSON coordinates array of a lower trace");
				// TODO: support more than 2 (listric)?
				double avgUpperDepth = 0d;
				for (Location loc : trace)
					avgUpperDepth += loc.depth;
				avgUpperDepth /= trace.size();
				lowerTrace = new FaultTrace(name);
				lowerTrace.addAll(lines.get(1));
				double avgLowerDepth = 0d;
				for (Location loc : lowerTrace)
					avgLowerDepth += loc.depth;
				avgLowerDepth /= lowerTrace.size();
				Preconditions.checkState(avgLowerDepth > avgUpperDepth, "MultiLineString supplied with upper and lower traces, "
						+ "but lower trace (depth="+(float)avgLowerDepth+") is not below upper trace (depth="+(float)avgUpperDepth
						+"). Were depths supplied in GeoJSON (tuples)?");
			}
		} else if (geometry instanceof Polygon) {
			Region polygon = ((Polygon)geometry).asRegion();
			Preconditions.checkNotNull(polygon, "Polygon has null geometry");
			Preconditions.checkState(zonePolygon == null, "Encountered a Polygon but already have a zone polygon");
			zonePolygon = polygon;
		} else if (geometry instanceof MultiPolygon) {
			List<Polygon> polygons = ((MultiPolygon)geometry).polygons;
			Preconditions.checkNotNull(polygons, "MultiPolygon has null geometry");
			Preconditions.checkState(!polygons.isEmpty(), "MultiPolygon is empty");
			Preconditions.checkState(polygons.size() == 1,
					"Only support 1 zone polygon, this MultiPolygon has %s", polygons.size());
			Preconditions.checkState(zonePolygon == null, "Encountered a MultiPolygon but already have a zone polygon");
			zonePolygon = polygons.get(0).asRegion();
		} else {
			System.err.println("Skipping unexpected FaultSection geometry type: "+geometry.type);
		}
		
		if (upperDepth != 0d && dip != 90d && !geometry.isSerializeZeroDepths()) {
			// depths were not specified in the GeoJSON, and this is a buried dipping surface.
			// for this case, we assume that the depth is actually the top of the surface and not the up-dip projection.
			// if you want to override this, you can do three things:
			// 	* provide three-valued locations in the GeoJSON
			//  * provide at least one non-zero depth
			//  * manually call geometry.setSerializeZeroDepths(true))
			Preconditions.checkState(!Double.isNaN(upperDepth),
					"Can't infer upper depth: upper depth not specified, and fault trace did not specify depths.");
			LocationList modTrace = new LocationList();
			for (Location loc : trace)
				modTrace.add(new Location(loc.getLatitude(), loc.getLongitude(), upperDepth));
			trace = new FaultTrace(trace.getName());
			trace.addAll(modTrace);
		}
	}
	
	private void validateLowerTrace(FaultTrace upper, FaultTrace lower) {
		int num = Integer.max(10, (int)Math.max(upper.getTraceLength(), lower.getTraceLength()));
		
		// check depth
		FaultTrace resampledUpper = FaultUtils.resampleTrace(upper, num);
		double upperDepth = resampledUpper.stream().map(S -> S.depth).mapToDouble(d->d).average().getAsDouble();
		FaultTrace resampledLower = FaultUtils.resampleTrace(lower, num);
		double lowerDepth = resampledLower.stream().map(S -> S.depth).mapToDouble(d->d).average().getAsDouble();
		Preconditions.checkState(lowerDepth > upperDepth,
				"Upper trace (depth=%s) must be above (less than) lower trace (depth=%s)", upperDepth, lowerDepth);
		if (Double.isNaN(this.upperDepth)) {
			this.upperDepth = upperDepth;
			this.properties.set(UPPER_DEPTH, upperDepth);
		}
		if (Double.isNaN(this.lowerDepth)) {
			this.lowerDepth = lowerDepth;
			this.properties.set(UPPER_DEPTH, lowerDepth);
		}
		
		ApproxEvenlyGriddedSurface surf = getApproxGriddedSurface(1d, false);
		dip = properties.getDouble(DIP, Double.NaN);
		if (!Double.isFinite(dip)) {
			// calculate dip from the lower trace
			dip = surf.getAveDip();
			properties.set(DIP, dip);
		}
		
		// check right hand rule if dipping
		// skip the check if basically vertical
		if (dip < 90d && (dip <= 85 || LocationUtils.horzDistanceFast(upper.first(), lower.first()) > 0.1d
				|| LocationUtils.horzDistanceFast(upper.last(), lower.last()) > 0.1d)) {
			double strike = upper.getAveStrike();
			double simpleDipDir = FaultUtils.getAngleAverage(List.of(
					LocationUtils.azimuth(upper.first(), lower.first()),
					LocationUtils.azimuth(upper.last(), lower.last())));
			double idealDipDir = strike + 90d;
			while (idealDipDir > 360d)
				idealDipDir -= 360;
			double arDiff = FaultUtils.getAbsAngleDiff(idealDipDir, simpleDipDir);
			Preconditions.checkState(arDiff <= 90d,
					"Right hand rule violation: dip=%s, dipDir=%s, idealDipDir=%s, diff=%s",
					dip, simpleDipDir, idealDipDir, arDiff);
		}
		
		if (!Double.isFinite(this.dipDirection))
			setDipDirection((float)surf.getAveDipDirection());
	}
	
	private static void checkPropNonNull(String propName, Object value) {
		Preconditions.checkNotNull(value, "FaultSections must have the '%s' GeoJSON property", propName);
	}
	
	private static void checkPropFinite(String propName, double value) {
		Preconditions.checkState(Double.isFinite(value), "FaultSections must have the '%s' GeoJSON property", propName);
	}

	public GeoJSONFaultSection(FaultSection sect) {
		this.id = sect.getSectionId();
		this.name = sect.getSectionName();
		this.dip = sect.getAveDip();
		this.rake = sect.getAveRake();
		this.upperDepth = sect.getOrigAveUpperDepth();
		this.lowerDepth = sect.getAveLowerDepth();
		this.dipDirection = sect.getDipDirection();
		this.trace = sect.getFaultTrace();
		this.lowerTrace = sect.getLowerFaultTrace();
		this.zonePolygon = sect.getZonePolygon();
		if (sect instanceof GeoJSONFaultSection) {
			this.properties = new FeatureProperties(((GeoJSONFaultSection)sect).properties);
		} else {
			this.properties = new FeatureProperties();
			setProperty(FAULT_ID, id);
			setProperty(FAULT_NAME, name);
			setProperty(DIP, dip);
			setProperty(RAKE, rake);
			setProperty(LOW_DEPTH, lowerDepth);
			setProperty(UPPER_DEPTH, upperDepth);
			if (dipDirection != (float)(trace.getAveStrike()+90d) && Float.isFinite(dipDirection));
				setProperty(DIP_DIR, dipDirection);
			setDateOfLastEvent(sect.getDateOfLastEvent());
			setSlipInLastEvent(sect.getSlipInLastEvent());
			setAseismicSlipFactor(sect.getAseismicSlipFactor());
			setCouplingCoeff(sect.getCouplingCoeff());
			setAveSlipRate(sect.getOrigAveSlipRate());
			setParentSectionId(sect.getParentSectionId());
			setParentSectionName(sect.getParentSectionName());
			setSlipRateStdDev(sect.getOrigSlipRateStdDev());
			if (sect.isConnector())
				properties.set(CONNECTOR, true);
			if (sect.isProxyFault())
				properties.set(PROXY, true);
			setZonePolygon(sect.getZonePolygon());
		}
		cacheCommonValues();
	}

	GeoJSONFaultSection(int id, String name, double dip, double rake, double upperDepth,
			double lowerDepth, float dipDirection, FaultTrace trace, FeatureProperties properties) {
		this.id = id;
		this.name = name;
		this.dip = dip;
		this.rake = rake;
		this.upperDepth = upperDepth;
		this.lowerDepth = lowerDepth;
		this.dipDirection = dipDirection;
		this.trace = trace;
		this.properties = properties;
		cacheCommonValues();
	}
	
	public static GeoJSONFaultSection fromFeature(Feature feature) {
		return new GeoJSONFaultSection(feature);
	}
	
	public static Feature toFeature(FaultSection sect) {
		return new GeoJSONFaultSection(sect).toFeature();
	}
	
	/**
	 * Reads in a fault section GeoJSON Feature from nshmp-haz, converting their property names to ours
	 * 
	 * @param feature
	 * @return
	 */
	public static GeoJSONFaultSection fromNSHMP_HazFeature(Feature feature) {
		FeatureProperties origProps = feature.properties;
		FeatureProperties mappedProps = new FeatureProperties();
		
		Preconditions.checkState(feature.id != null && feature.id instanceof Number);
		int id = ((Number)feature.id).intValue();
		
		// required
		String name = origProps.require("name", String.class);
		mappedProps.set(FAULT_NAME, name);
		
		Geometry geometry = feature.geometry;
		if (geometry instanceof MultiLineString && ((MultiLineString)geometry).lines.size() == 2) {
			// subduction interface, convert to simple fault data
			MultiLineString multiGeom = (MultiLineString)geometry;
			LocationList upperTrace = multiGeom.lines.get(0);
			FaultTrace upperAsFaultTrace = new FaultTrace(null);
			upperAsFaultTrace.addAll(upperTrace);
			LocationList lowerTrace = multiGeom.lines.get(1);
			FaultTrace lowerAsFaultTrace = new FaultTrace(null);
			lowerAsFaultTrace.addAll(lowerTrace);
			ApproxEvenlyGriddedSurface approxSurf = new ApproxEvenlyGriddedSurface(
					upperAsFaultTrace, lowerAsFaultTrace, 1d);
			
			mappedProps.set(DIP, approxSurf.getAveDip());
			if (origProps.containsKey("rake"))
				mappedProps.set(RAKE, origProps.require("rake", Double.class));
			mappedProps.set(UPPER_DEPTH, approxSurf.getAveRupTopDepth());
			mappedProps.set(LOW_DEPTH, approxSurf.getAveRupBottomDepth());
			mappedProps.set(DIP_DIR, approxSurf.getAveDipDirection());
			
			// make all depths in the trace the same by projecting up or down dip
			LocationList newUpperTrace = new LocationList();
			double aveDepth = approxSurf.getAveRupTopDepth();
			double dipDir = approxSurf.getAveDipDirection();
			double dip = approxSurf.getAveDip();
			System.out.println("aveDep="+(float)aveDepth+"\tdipDir="+(float)dipDir+"\tdip="+(float)dip+"\n");
			for(Location loc:upperTrace) {
				double deltaDep = aveDepth-loc.depth; // positive means go to greater depth, meaning move in negative Z dir
				double azimuth = Double.NaN;
				if(deltaDep>0) 
					azimuth = dipDir; // move down dip
				else
					azimuth = dipDir+180; // move up dip
				double horzDist = Math.abs(deltaDep)/Math.tan(dip*Math.PI/180);
				LocationVector dir = new LocationVector(azimuth, horzDist, deltaDep);
//				System.out.println("azimuth="+(float)azimuth+"\thorzDist="+(float)horzDist+"\tvertDis="+(float)-deltaDep);
				newUpperTrace.add(LocationUtils.location(loc, dir));

//				newUpperTrace.add(new Location(loc.getLatitude(),loc.getLongitude(),aveDepth));
			}
			
//			System.out.println("Orig Trace:\n"+upperTrace);
//			System.out.println("\nRevised Trace:\n"+newUpperTrace);
//			System.exit(0);
			
			// convert geometry to simple line string with only upper trace
			geometry = new LineString(newUpperTrace);
			
			// set lower trace as separate property
			// TODO fix this
			mappedProps.put(LOWER_TRACE, new LineString(lowerTrace));
		} else {
			mappedProps.set(DIP, origProps.require("dip", Double.class));
			if (origProps.containsKey("rake"))
				mappedProps.set(RAKE, origProps.require("rake", Double.class));
			mappedProps.set(UPPER_DEPTH, origProps.require("upper-depth", Double.class));
			mappedProps.set(LOW_DEPTH, origProps.require("lower-depth", Double.class));
			if (origProps.containsKey("dip-direction"))
				mappedProps.set(DIP_DIR, origProps.require("dip-direction", Double.class));
		}
		
		// optional ones to carry forward
		if (origProps.containsKey("state"))
			mappedProps.set("PrimState", origProps.require("state", String.class));
		if (origProps.containsKey("references"))
			mappedProps.set("references", origProps.require("references", String.class));
		
		if (origProps.containsKey("aseismicity"))
			mappedProps.set(ASEIS, origProps.require("aseismicity", Double.class));
		if (origProps.containsKey("slip-rate"))
			mappedProps.set(SLIP_RATE, origProps.require("slip-rate", Double.class));

		// if "index" exists use above feature.id as parent id and index as id
		if (origProps.containsKey("index")) {
// System.out.println("INDEX HERE: "+origProps.getInt("index", -0)+"\t"+id+"\t"+name);
			mappedProps.set(PARENT_ID, id);
//			id = origProps.require("index", Integer.class);
			id = origProps.getInt("index", -0);
		}
		if (origProps.containsKey("parent-id")) {
			mappedProps.set(PARENT_ID, origProps.getInt("parent-id", -0));
		}


		
		return new GeoJSONFaultSection(new Feature(id, geometry, mappedProps));
	}
	
	public Feature toFeature() {
		Geometry geometry;
		Preconditions.checkNotNull(trace, "Trace is null");
		Geometry traceGeom;
		if (lowerTrace != null)
			traceGeom = new MultiLineString(List.of(trace, lowerTrace));
		else
			traceGeom = new LineString(trace);
		if ((upperDepth != 0d && dip != 90d) || lowerTrace != null) {
			// we need to serialize any zeroes
			traceGeom.setSerializeZeroDepths(true);
		}
		if (zonePolygon != null) {
			// both
			List<Geometry> geometries = new ArrayList<>();
			geometries.add(traceGeom);
			geometries.add(new Polygon(zonePolygon));
			geometry = new GeometryCollection(geometries);
		} else {
			geometry = traceGeom;
		}
		return new Feature(id, geometry, properties);
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public long getDateOfLastEvent() {
		return dateOfLastEventMillis;
	}

	@Override
	public void setDateOfLastEvent(long dateOfLastEventMillis) {
		this.dateOfLastEventMillis = dateOfLastEventMillis;
		properties.setConditional(DATE_LAST, dateOfLastEventMillis, dateOfLastEventMillis > Long.MIN_VALUE);
	}
	

	@Override
	public void setSlipInLastEvent(double slipInLastEvent) {
		properties.setConditional(SLIP_LAST, slipInLastEvent, Double.isFinite(slipInLastEvent));
	}

	@Override
	public double getSlipInLastEvent() {
		return properties.getDouble(SLIP_LAST, Double.NaN);
	}

	@Override
	public double getAseismicSlipFactor() {
		return properties.getDouble(ASEIS, 0d);
	}

	@Override
	public void setAseismicSlipFactor(double aseismicSlipFactor) {
		Preconditions.checkArgument(aseismicSlipFactor >= 0d && aseismicSlipFactor <= 1d,
				"aseismicSlipFactor not in the range [0,1]: %s", aseismicSlipFactor);
		properties.set(ASEIS, aseismicSlipFactor);
	}

	@Override
	public double getCouplingCoeff() {
		return properties.getDouble(COUPLING, 1d);
	}

	@Override
	public void setCouplingCoeff(double couplingCoeff) {
		Preconditions.checkArgument(couplingCoeff >= 0d && couplingCoeff <= 1d,
				"couplingCoeff not in the range [0,1]: %s", couplingCoeff);
		properties.set(COUPLING, couplingCoeff);
	}

	@Override
	public double getAveDip() {
		return dip;
	}

	@Override
	public double getOrigAveSlipRate() {
		return aveLongTermSlipRate;
	}

	@Override
	public void setAveSlipRate(double aveLongTermSlipRate) {
		this.aveLongTermSlipRate = aveLongTermSlipRate;
		properties.setConditional(SLIP_RATE, aveLongTermSlipRate, Double.isFinite(aveLongTermSlipRate));
	}

	@Override
	public double getAveLowerDepth() {
		return lowerDepth;
	}

	@Override
	public double getAveRake() {
		return rake;
	}

	@Override
	public void setAveRake(double aveRake) {
		FaultUtils.assertValidRake(aveRake);
		this.rake = aveRake;
		properties.set(RAKE, aveRake);
	}

	@Override
	public double getOrigAveUpperDepth() {
		return upperDepth;
	}

	@Override
	public float getDipDirection() {
		return dipDirection;
	}

	public void setDipDirection(float dipDirection) {
		while (dipDirection > 360f)
			dipDirection -= 360f;
		while (dipDirection < 0)
			dipDirection += 360f;
		this.dipDirection = dipDirection;
		properties.set(DIP_DIR, dipDirection);
	}

	@Override
	public FaultTrace getFaultTrace() {
		return trace;
	}

	@Override
	public FaultTrace getLowerFaultTrace() {
		return lowerTrace;
	}

	@Override
	public int getSectionId() {
		return id;
	}

	@Override
	public void setSectionId(int sectID) {
		this.id = sectID;
		properties.set(FAULT_ID, id);
		updateHashCode();
	}

	@Override
	public void setSectionName(String sectName) {
		this.name = sectName;
		properties.set(FAULT_NAME, sectName);
	}

	@Override
	public int getParentSectionId() {
		return parentSectionId;
	}

	@Override
	public void setParentSectionId(int parentSectionId) {
		this.parentSectionId = parentSectionId;
		properties.setConditional(PARENT_ID, parentSectionId, parentSectionId >= 0);
		updateHashCode();
	}
	

	@Override
	public String getParentSectionName() {
		return parentSectionName;
	}

	@Override
	public void setParentSectionName(String parentSectionName) {
		this.parentSectionName = parentSectionName;
		properties.set(PARENT_NAME, parentSectionName);
	}

	@Override
	public int getSubSectionIndex() {
		return subSectionIndex;
	}
	
	public void setSubSectionIndex(int subSectionIndex) {
		this.subSectionIndex = subSectionIndex;
	}

	@Override
	public int getSubSectionIndexAlong() {
		return subSectionIndexAlong;
	}
	
	public void setSubSectionIndexAlong(int subSectionIndexAlong) {
		this.subSectionIndexAlong = subSectionIndexAlong;
	}

	@Override
	public int getSubSectionIndexDownDip() {
		return subSectionIndexDownDip;
	}
	
	public void setSubSectionIndexDownDip(int subSectionIndexDownDip) {
		this.subSectionIndexDownDip = subSectionIndexDownDip;
	}

	@Override
	public List<GeoJSONFaultSection> getSubSectionsList(double maxSubSectionLen, int startId, int minSubSections) {
		List<FaultTrace> equalLengthSubsTrace, equalLengthLowerSubsTrace;
		if (lowerTrace == null) {
			// simple case
			equalLengthSubsTrace = FaultUtils.getEqualLengthSubsectionTraces(this.trace, maxSubSectionLen, minSubSections);
			equalLengthLowerSubsTrace = null;
		} else {
			List<FaultTrace[]> traces = FaultUtils.getEqualLengthSubsectionTraces(trace, lowerTrace, maxSubSectionLen, minSubSections);
			equalLengthSubsTrace = new ArrayList<>(traces.size());
			equalLengthLowerSubsTrace = new ArrayList<>(traces.size());
			for (FaultTrace[] trace : traces) {
				Preconditions.checkState(trace.length == 2);
				equalLengthSubsTrace.add(trace[0]);
				equalLengthLowerSubsTrace.add(trace[1]);
			}
		}
		
		List<GeoJSONFaultSection> subSectionList = new ArrayList<GeoJSONFaultSection>();
		for(int i=0; i<equalLengthSubsTrace.size(); ++i) {
			int myID = startId + i;
			String myName = name+STANDARD_SUBSECTION_PREFIX+(i);
			GeoJSONFaultSection subSection = new GeoJSONFaultSection(this);
			
			// clear these just in case they were somehow set externally
			subSection.properties.remove(FAULT_ID);
			subSection.properties.remove(FAULT_NAME);
			
			subSection.setSectionName(myName);
			subSection.setSectionId(myID);
			subSection.trace = equalLengthSubsTrace.get(i);
			subSection.setParentSectionId(this.id);
			subSection.setParentSectionName(this.name);
			subSection.setSubSectionIndex(i);
			subSection.setSubSectionIndexAlong(i);
			subSection.setSubSectionIndexDownDip(-1);
			// make sure dip direction is set from parent
			subSection.setDipDirection(dipDirection);
			
			if (lowerTrace != null) {
				FaultTrace lowerSubTrace = equalLengthLowerSubsTrace.get(i);
//				System.out.println(myName);
//				System.out.println("Upper trace az="+subSection.trace.getAveStrike()+": "+subSection.trace);
//				System.out.println("Lower trace az="+lowerSubTrace.getAveStrike()+": "+lowerSubTrace);
				// now done separately
//				subSection.properties.set(LOWER_TRACE, new LineString(lowerSubTrace));
				subSection.lowerTrace = lowerSubTrace;
				// calculate new dip
				subSection.dip = new ApproxEvenlyGriddedSurface(subSection.trace, subSection.lowerTrace,
						subSection.trace.getTraceLength()/20d).getAveDip();
				subSection.properties.set(DIP, subSection.dip);
				// calculate new depth
				int num = Integer.max(10, (int)Math.max(subSection.trace.getTraceLength(), lowerSubTrace.getTraceLength()));
				subSection.upperDepth = FaultUtils.resampleTrace(subSection.trace, num).stream().map(S -> S.depth).mapToDouble(d->d).average().getAsDouble();
				subSection.properties.set(UPPER_DEPTH, subSection.upperDepth);
				subSection.lowerDepth = FaultUtils.resampleTrace(lowerSubTrace, num).stream().map(S -> S.depth).mapToDouble(d->d).average().getAsDouble();
				subSection.properties.set(LOW_DEPTH, subSection.lowerDepth);
			}
			
			subSection.setZonePolygon(null);
			
			subSectionList.add(subSection);
		}
		if (zonePolygon != null)
			SubSectionPolygonBuilder.buildSubsectionPolygons(subSectionList, zonePolygon);
		
		return subSectionList;
	}
	
	static final String STANDARD_SUBSECTION_PREFIX = ", Subsection ";

	@Override
	public double getOrigSlipRateStdDev() {
		return slipRateStdDev;
	}

	@Override
	public void setSlipRateStdDev(double slipRateStdDev) {
		this.slipRateStdDev = slipRateStdDev;
		properties.setConditional(SLIP_STD_DEV, slipRateStdDev, Double.isFinite(slipRateStdDev));
	}

	@Override
	public boolean isConnector() {
		return properties.getBoolean(CONNECTOR, false);
	}

	@Override
	public boolean isProxyFault() {
		return properties.getBoolean(PROXY, false);
	}

	@Override
	public Region getZonePolygon() {
		return zonePolygon;
	}

	public void setZonePolygon(Region zonePolygon) {
		this.zonePolygon = zonePolygon;
	}

	@Override
	public RuptureSurface getFaultSurface(double gridSpacing) {
		return getFaultSurface(gridSpacing, false, true);
	}

	@Override
	public RuptureSurface getFaultSurface(
			double gridSpacing, boolean preserveGridSpacingExactly,
			boolean aseisReducesArea) {
		if (lowerTrace != null || !isUpperTraceSameDepth())
			return getApproxGriddedSurface(gridSpacing, aseisReducesArea);
		return getStirlingGriddedSurface(gridSpacing, preserveGridSpacingExactly, aseisReducesArea);
	}
	
	private boolean isUpperTraceSameDepth() {
		double depth = trace.first().depth;
		for (int i=0; i<trace.size(); i++)
			if (depth != trace.get(i).depth)
				return false;
		return true;
	}
	
	public void setProperty(String name, Object value) {
		properties.set(name, value);
	}
	
	public Object getProperty(String name) {
		return properties.get(name);
	}
	
	public <E> E getProperty(String name, E defaultValue) {
		return properties.get(name, defaultValue);
	}
	
	public FeatureProperties getProperties() {
		return properties;
	}
	
	/**
	 * This returns a StirlingGriddedSurface with the specified grid spacing, where aseismicSlipFactor
	 * is applied as a reduction of down-dip-width (an increase of the upper seis depth).
	 * @param gridSpacing
	 * @param preserveGridSpacingExactly - if false, this will decrease the grid spacing to fit the length 
	 * and ddw exactly (otherwise trimming occurs)
	 * @return
	 */
	public synchronized StirlingGriddedSurface getStirlingGriddedSurface(
			double gridSpacing, boolean preserveGridSpacingExactly,
			boolean aseisReducesArea) {
		if (stirlingCache == null)
			stirlingCache = new StirlingSurfaceCache(this);
		return stirlingCache.getStirlingGriddedSurface(gridSpacing, preserveGridSpacingExactly, aseisReducesArea);
	}
	
	/**
	 * This returns a ApproxEvenlyGriddedSurface with the specified grid spacing, where aseismicSlipFactor
	 * is applied as a reduction of down-dip-width (an increase of the upper seis depth).
	 * @param gridSpacing
	 * @return
	 */
	public synchronized ApproxEvenlyGriddedSurface getApproxGriddedSurface(
			double gridSpacing,
			boolean aseisReducesArea) {
		if (approxGriddedCache == null) {
			FaultTrace lowerTrace = this.lowerTrace;
			if (lowerTrace == null) {
				// no lower trace supplied, project down dip to lower depth
				if (dip == 90d) {
					// simple
					lowerTrace = new FaultTrace(name);
					for (Location loc : trace)
						lowerTrace.add(new Location(loc.lat, loc.lon, lowerDepth));
				} else {
					// complicated
					
					/*
					 * 	upper
					 * 		|\
					 * 		| \
					 * 		|  \  h
					 * 	y	|   \
					 * 		|____\ lower
					 * 		   x
					 * 
					 * y = lowerDepth - upperLoc.depth
					 * tan (dip) = x / y
					 * x = y*tan(dip)
					 */
					double aveDipRad = Math.toRadians(dip); // radians
					lowerTrace = new FaultTrace(name);
					for (Location traceLoc : trace) {
						Location upperLoc = StirlingGriddedSurface.getTopLocation(
								traceLoc, upperDepth, aveDipRad, dipDirection);
						
						// 'y' above
						double vertToBottom = lowerDepth - upperLoc.depth;
						Preconditions.checkState(vertToBottom > 0, "lower depth is above upper loc? %s %s", lowerDepth, upperLoc.depth);
						double horzToBottom = vertToBottom*Math.tan(aveDipRad);
						Preconditions.checkState(dip==90 || horzToBottom > 0, "no horizontal offset (%s) for lower trace with dip=%s?", horzToBottom, dip);

						LocationVector dir = new LocationVector(dipDirection, horzToBottom, vertToBottom);

						Location lowerLoc = LocationUtils.location(upperLoc, dir);
						lowerTrace.add(lowerLoc);
					}
				}
			}
//			System.out.println("Building approx surface with traces:\nUpper: "+trace+"\nLower: "+lowerTrace);
			approxGriddedCache = new ApproxEvenlyGriddedSurfaceCache(this, lowerTrace);
		}
		return approxGriddedCache.getApproxEvenlyGriddedSurface(gridSpacing, aseisReducesArea);
	}

	@Override
	public GeoJSONFaultSection clone() {
		return new GeoJSONFaultSection(this);
	}

	@Override
	public Element toXMLMetadata(Element root, String name) {
		// TODO remove
		FaultSectionPrefData prefData = new FaultSectionPrefData();
		prefData.setFaultSectionPrefData(this);
		return prefData.toXMLMetadata(root, name);
	}

	@Override
	public Element toXMLMetadata(Element root) {
		// TODO remove
		FaultSectionPrefData prefData = new FaultSectionPrefData();
		prefData.setFaultSectionPrefData(this);
		return prefData.toXMLMetadata(root, name);
	}
	
	// TODO remove
	public static GeoJSONFaultSection fromXMLMetadata(Element el) {
		return new GeoJSONFaultSection(FaultSectionPrefData.fromXMLMetadata(el));
	}
	
	private void updateHashCode() {
		this.hashCode = FaultSection.hashCode(this);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		return FaultSection.equals(this, obj);
	}
}
