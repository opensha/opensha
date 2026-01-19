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
	private long dateOfLastEventMillis = Long.MIN_VALUE;
	private int hashCode;
	private FaultTrace lowerTrace; // if supplied, will use an approximately gridded surface
	
	// helpers
	private StirlingSurfaceCache stirlingCache;
	// for faults with lower traces
	private ApproxEvenlyGriddedSurfaceCache approxGriddedCache;
	
	// core property names
	public static final String FAULT_ID = "FaultID";
	public static final String FAULT_NAME = "FaultName";
	public static final String DIP = "DipDeg";
	public static final String DIP_DIR = "DipDir";
	public static final String RAKE = "Rake";
	public static final String LOW_DEPTH = "LowDepth";
	public static final String UPPER_DEPTH = "UpDepth";
	
	// optional property names
	public static final String DATE_LAST = "DateLastEvent";
	public static final String SLIP_LAST = "SlipLastEvent";
	public static final String ASEIS = "AseismicSlipFactor";
	public static final String COUPLING = "CouplingCoeff";
	public static final String SLIP_RATE = "SlipRate";
	public static final String PARENT_ID = "ParentID";
	public static final String PARENT_NAME = "ParentName";
	public static final String SLIP_STD_DEV = "SlipRateStdDev";
	public static final String CONNECTOR = "Connector";
	public static final String CREEP_RATE = "CreepRate";
	public static final String PROXY = "Proxy";
	// use MultiLineString instead
	@Deprecated private static final String LOWER_TRACE = "LowerTrace";
	
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
	public List<GeoJSONFaultSection> getSubSectionsList(double maxSubSectionLen, int startId, int minSubSections) {
		List<FaultTrace> equalLengthSubsTrace, equalLengthLowerSubsTrace;
		if (lowerTrace == null) {
			// simple case
			equalLengthSubsTrace = FaultUtils.getEqualLengthSubsectionTraces(this.trace, maxSubSectionLen, minSubSections);
			equalLengthLowerSubsTrace = null;
		} else {
			// we have a lower trace, which is more complex.
			// we could just split the upper and lower traces into equal length pieces and connect them, but those can
			// be skewed if one trace has more (and uneven) curvature than the other
			
			// instead, we'll try to build less skewed sections by subsectioning a trace down the middle of the fault
			// and then projecting up/down to the top/bottom
			
			// build a trace at the middle
			int numResample = Integer.max(100, (int)Math.max(trace.getTraceLength(), lowerTrace.getTraceLength()));
			FaultTrace upperResampled = FaultUtils.resampleTrace(trace, numResample);
			FaultTrace lowerResampled = FaultUtils.resampleTrace(lowerTrace, numResample);
			Preconditions.checkState(upperResampled.size() == lowerResampled.size());
			// this won't necessarily be evenly spaced, but that's fine (we'll build equal length traces next)
			FaultTrace middleTrace = new FaultTrace(null);
			double maxHorzDist = 0d;
			for (int i=0; i<upperResampled.size(); i++) {
				Location upperLoc = upperResampled.get(i);
				Location lowerLoc = lowerResampled.get(i);
				// vector from upper to lower
				LocationVector vector = LocationUtils.vector(upperLoc, lowerLoc);
				maxHorzDist = Math.max(maxHorzDist, vector.getHorzDistance());
				// scale by 0.5 to get a middle loc
				vector.setHorzDistance(0.5*vector.getHorzDistance());
				vector.setVertDistance(0.5*vector.getVertDistance());
				middleTrace.add(LocationUtils.location(upperLoc, vector));
			}
			
			// resample the middle trace to get subsections
			ArrayList<FaultTrace> equalLengthMiddleTraces = FaultUtils.getEqualLengthSubsectionTraces(
					middleTrace, maxSubSectionLen, minSubSections);
			int numSubSects = equalLengthMiddleTraces.size();
			// project the middle trace to the upper and lower traces; do that by finding the index on the resampled
			// traces that is closest to a right angle from middle trace strike direction
			int[][] closestUpperIndexes = new int[numSubSects][2];
			int[][] closestLowerIndexes = new int[numSubSects][2];
			for (int i=0; i<numSubSects; i++) {
				FaultTrace middle = equalLengthMiddleTraces.get(i);
				double strike = middle.getAveStrike();
				double leftOfStrikeRad = Math.toRadians(strike-90d);
				double rightOfStrikeRad = Math.toRadians(strike+90d);
				Location[] firstLine = {
						LocationUtils.location(middle.first(), leftOfStrikeRad, maxHorzDist),
						LocationUtils.location(middle.first(), rightOfStrikeRad, maxHorzDist)
				};
				Location[] lastLine = {
						LocationUtils.location(middle.last(), leftOfStrikeRad, maxHorzDist),
						LocationUtils.location(middle.last(), rightOfStrikeRad, maxHorzDist)
				};
				double upperFirstDist = Double.POSITIVE_INFINITY;
				double upperLastDist = Double.POSITIVE_INFINITY;
				double lowerFirstDist = Double.POSITIVE_INFINITY;
				double lowerLastDist = Double.POSITIVE_INFINITY;
				// this could be sped up, we shouldn't need to search the whole trace every time
				for (int j=0; j<upperResampled.size(); j++) {
					double distUpFirst = Math.abs(LocationUtils.distanceToLineFast(firstLine[0], firstLine[1], upperResampled.get(j)));
					if (distUpFirst < upperFirstDist) {
						upperFirstDist = distUpFirst;
						closestUpperIndexes[i][0] = j;
					}
					double distUpLast = Math.abs(LocationUtils.distanceToLineFast(lastLine[0], lastLine[1], upperResampled.get(j)));
					if (distUpLast < upperLastDist) {
						upperLastDist = distUpLast;
						closestUpperIndexes[i][1] = j;
					}
					double distLowFirst = Math.abs(LocationUtils.distanceToLineFast(firstLine[0], firstLine[1], lowerResampled.get(j)));
					if (distLowFirst < lowerFirstDist) {
						lowerFirstDist = distLowFirst;
						closestLowerIndexes[i][0] = j;
					}
					double distLowLast = Math.abs(LocationUtils.distanceToLineFast(lastLine[0], lastLine[1], lowerResampled.get(j)));
					if (distLowLast < lowerLastDist) {
						lowerLastDist = distLowLast;
						closestLowerIndexes[i][1] = j;
					}
				}
//				System.out.println("Raw mappings for subsection "+i);
//				System.out.println("\t"+closestUpperIndexes[i][0]+" "+closestUpperIndexes[i][1]);
//				System.out.println("\t"+(float)upperFirstDist+" "+(float)upperLastDist);
//				System.out.println("\t"+closestLowerIndexes[i][0]+" "+closestLowerIndexes[i][1]);
//				System.out.println("\t"+(float)lowerFirstDist+" "+(float)lowerLastDist);
			}
			// now process to fix two cases:
			// * any overlaps with the neighbors
			// * ensure that we include the overall first or last point on the traces
			for (int i=0; i<numSubSects; i++) {
				int[] myUpper = closestUpperIndexes[i];
				int[] myLower = closestLowerIndexes[i];
				if (i == 0) {
					// force it to start at the first point
					myUpper[0] = 0;
					myLower[0] = 0;
				} else {
					// average with the previous
					int[] prevUpper = closestUpperIndexes[i-1];
					if (myUpper[0] != prevUpper[1]) {
						double tieBreaker = myUpper[1]-myUpper[0] > prevUpper[1]-prevUpper[0] ? 0.1 : -0.1;
						int avg = (int)(0.5*(myUpper[0] + prevUpper[1])+tieBreaker);
						myUpper[0] = avg;
						prevUpper[1] = avg;
					}
					int[] prevLower = closestLowerIndexes[i-1];
					if (myLower[0] != prevLower[1]) {
						double tieBreaker = myLower[1]-myLower[0] > prevLower[1]-prevLower[0] ? 0.1 : -0.1;
						int avg = (int)(0.5*(myLower[0] + prevLower[1])+tieBreaker);
						myLower[0] = avg;
						prevLower[1] = avg;
					}
				}
				
				if (i == numSubSects-1) {
					// force it to end at the last point
					myUpper[1] = upperResampled.size()-1;
					myLower[1] = upperResampled.size()-1;
				}
			}
			// now check to make sure that none are weird (last same as or before first)
			boolean fail = false;
			for (int i=0; i<numSubSects; i++) {
				int[] myUpper = closestUpperIndexes[i];
				int[] myLower = closestLowerIndexes[i];
				if (myUpper[0] >= myUpper[1] || myLower[0] >= myLower[1]) {
					System.out.println("Fail for subsection "+i);
					System.out.println("\tupper: "+myUpper[0]+"->"+myUpper[1]);
					System.out.println("\tlower: "+myLower[0]+"->"+myLower[1]);
					fail = true;
					break;
				}
			}
			if (fail) {
				// fallback to the possibly skewed subsections just using the resampled upper and lower trace
				System.err.println("WARNING: failed to build unskewed subsections for "+id+". "+name
						+", reverting to splitting upper and lower trace evenly");
				equalLengthSubsTrace = FaultUtils.getEqualLengthSubsectionTraces(this.trace, maxSubSectionLen, minSubSections);
				equalLengthLowerSubsTrace = FaultUtils.getEqualLengthSubsectionTraces(this.lowerTrace, equalLengthSubsTrace.size());
				Preconditions.checkState(equalLengthLowerSubsTrace.size() == equalLengthLowerSubsTrace.size());
			} else {
				// build our nicer subsections
				equalLengthSubsTrace = new ArrayList<>(numSubSects);
				equalLengthLowerSubsTrace = new ArrayList<>(numSubSects);
				
				int upperSearchStartIndex = 0;
				int lowerSearchStartIndex = 0;
				for (int i=0; i<numSubSects; i++) {
					FaultTrace upperSubTrace = new FaultTrace(null);
					FaultTrace lowerSubTrace = new FaultTrace(null);
					int[] myUpper = closestUpperIndexes[i];
					int[] myLower = closestLowerIndexes[i];
					Location upperFirst = upperResampled.get(myUpper[0]);
					Location upperLast = upperResampled.get(myUpper[1]);
					Location lowerFirst = lowerResampled.get(myLower[0]);
					Location lowerLast = lowerResampled.get(myLower[1]);
					upperSubTrace.add(upperFirst);
					lowerSubTrace.add(lowerFirst);
					
					// add any intermediate locations
					upperSearchStartIndex = addIntermediateTracePoints(trace, upperSubTrace, upperFirst, upperLast, upperSearchStartIndex);
					lowerSearchStartIndex = addIntermediateTracePoints(lowerTrace, lowerSubTrace, lowerFirst, lowerLast, lowerSearchStartIndex);
					
					upperSubTrace.add(upperLast);
					lowerSubTrace.add(lowerLast);
//					int upperBeforeStartIndex = -1;
//					int upperAfterEndIndex = -1;
//					int lowerBeforeStartIndex = -1;
//					int lowerAfterEndIndex = -1;
//					for (int j=0; j<2; j++) {
//						int targetUpperSampledIndex = closestUpperIndexes[i][j];
//						int targetLowerSampledIndex = closestUpperIndexes[i][j];
//						
//						if (i == 0 && j == 0) {
//							// simple, just start at the beginning
//							upperSubTrace.add(trace.first());
//							lowerSubTrace.add(lowerTrace.first());
//						} else if (i == numSubSects-1 && j == 2) {
//							// need to search for the index before
//						}
//						
//						
//					}
//					
//					int upperBeforeIndex = -1;
//					for (int j=upperSearchStartIndex; j<trace.size(); j++)
//					int lowerBeforeIndex = -1;
					
					equalLengthSubsTrace.add(upperSubTrace);
					equalLengthLowerSubsTrace.add(lowerSubTrace);
				}
			}
		}
		
		List<GeoJSONFaultSection> subSectionList = new ArrayList<GeoJSONFaultSection>();
		for(int i=0; i<equalLengthSubsTrace.size(); ++i) {
			int myID = startId + i;
			String myName = name+", Subsection "+(i);
			GeoJSONFaultSection subSection = new GeoJSONFaultSection(this);
			
			// clear these just in case they were somehow set externally
			subSection.properties.remove(FAULT_ID);
			subSection.properties.remove(FAULT_NAME);
			
			subSection.setSectionName(myName);
			subSection.setSectionId(myID);
			subSection.trace = equalLengthSubsTrace.get(i);
			subSection.setParentSectionId(this.id);
			subSection.setParentSectionName(this.name);
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
	
	private static int addIntermediateTracePoints(FaultTrace rawTrace, FaultTrace subSectTrace,
			Location subsectionStart, Location subsectionEnd, int searchStartIndex) {
//		System.out.println("Adding intermediate points with start:\t"+subsectionStart);
		// find the segment for the start index
		double minDist = Double.POSITIVE_INFINITY;
		int closestSegToStart = -1;
		for (int i=searchStartIndex; i<rawTrace.size()-1; i++) {
			Location loc1 = rawTrace.get(i);
			Location loc2 = rawTrace.get(i+1);
			double distToSeg = LocationUtils.distanceToLineSegmentFast(loc1, loc2, subsectionStart);
			if (distToSeg < minDist) {
				closestSegToStart = i;
				minDist = distToSeg;
			} else if (minDist < 1d && distToSeg > 10d) {
				// we've already found it and gone past, stop searching
				break;
			}
		}
//		System.out.println("\tClosest segment to start: "+closestSegToStart+" (minDist="+(float)minDist+")");
		
		// find the segment for the start index
		minDist = Double.POSITIVE_INFINITY;
		int closestSegToEnd = -1;
		for (int i=closestSegToStart; i<rawTrace.size()-1; i++) {
			Location loc1 = rawTrace.get(i);
			Location loc2 = rawTrace.get(i+1);
			double distToSeg = LocationUtils.distanceToLineSegmentFast(loc1, loc2, subsectionEnd);
			if (distToSeg < minDist) {
				closestSegToEnd = i;
				minDist = distToSeg;
			} else if (minDist < 1d && distToSeg > 10d) {
				// we've already found it and gone past, stop searching
				break;
			}
		}
//		System.out.println("\tClosest segment to end: "+closestSegToEnd+" (minDist="+(float)minDist+")");
		
		// we've now identified the segments on which the start and end section lie
		if (closestSegToStart < closestSegToEnd) {
			// there's at least one point between the two
			for (int i=closestSegToStart+1; i<=closestSegToEnd; i++) {
//				System.out.println("\tAdding intermediate: "+i+". "+rawTrace.get(i));
				subSectTrace.add(rawTrace.get(i));
			}
		}
//		System.out.println("Done adding intermediate points with end:\t"+subsectionEnd);
		
		return closestSegToEnd;
	}

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
