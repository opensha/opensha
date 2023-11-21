package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
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
	public static final String LOWER_TRACE = "LowerTrace";

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
		
		dip = properties.getDouble(DIP, Double.NaN);
		checkPropFinite(DIP, dip);
		FaultUtils.assertValidDip(dip);
		
		rake = properties.getDouble(RAKE, Double.NaN);
//		checkPropFinite(RAKE, rake); // allow rakes to be attached later, don't enforce that it's specified now
		
		upperDepth = properties.getDouble(UPPER_DEPTH, Double.NaN);
		checkPropFinite(UPPER_DEPTH, dip);
		FaultUtils.assertValidDepth(upperDepth);
		
		lowerDepth = properties.getDouble(LOW_DEPTH, Double.NaN);
		checkPropFinite(LOW_DEPTH, dip);
		FaultUtils.assertValidDepth(lowerDepth);
		
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
		
		Geometry lowerTraceGeom = properties.get(LOWER_TRACE, null);
		if (lowerTraceGeom != null) {
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
				System.err.println("WARNING: concatenating multi-trace for "+name+" ("+id+")");
				List<LocationList> extraTraces = lines.subList(1, lines.size());
				// figure out where they go
				while (!extraTraces.isEmpty()) {
					int bestIndex = -1;
					boolean bestAtEnd = false;
					double bestDistance = Double.POSITIVE_INFINITY;
					for (int i=0; i<extraTraces.size(); i++) {
						LocationList extraTrace = extraTraces.get(i);
						double beforeDist = LocationUtils.horzDistanceFast(extraTrace.last(), trace.first());
						double afterDist = LocationUtils.horzDistanceFast(extraTrace.first(), trace.last());
						if (beforeDist < afterDist && beforeDist < bestDistance) {
							bestIndex = i;
							bestAtEnd = false;
							bestDistance = beforeDist;
						} else if (afterDist <= beforeDist && afterDist < bestDistance) {
							bestIndex = i;
							bestAtEnd = true;
							bestDistance = afterDist;
						}
					}
					LocationList addTrace = extraTraces.remove(bestIndex);
					if (bestAtEnd) {
						System.err.println("\tadding extra trace to end:\n\t\tprevLoc="+trace.last()+"\n\t\tnextLoc="
								+addTrace.first()+"\t(dist="+bestDistance+")");
						trace.addAll(addTrace);
					} else {
						System.err.println("\tadding extra trace to start (before previous):\n\t\tprevLoc="
								+trace.last()+"\n\t\tnextLoc="+addTrace.first()+"\t(dist="+bestDistance+")");
						trace.addAll(0, addTrace);
					}
					if (bestIndex != 0)
						System.out.println("\t\tWARNING: didn't add new traces in order");
				}
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
			LocationList modTrace = new LocationList();
			for (Location loc : trace)
				modTrace.add(new Location(loc.getLatitude(), loc.getLongitude(), upperDepth));
			trace = new FaultTrace(trace.getName());
			trace.addAll(modTrace);
		}
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
			// subduction interface, convert to simple fault data (TODO: actually store the lower trace and do it right)
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
			
			// convert geometry to simple line string with only upper trace
			geometry = new LineString(upperTrace);
			
			// set lower trace as separate property
			mappedProps.put(LOWER_TRACE, new LineString(lowerTrace));
		} else {
			mappedProps.set(DIP, origProps.require("dip", Double.class));
			mappedProps.set(RAKE, origProps.require("rake", Double.class));
			mappedProps.set(UPPER_DEPTH, origProps.require("upper-depth", Double.class));
			mappedProps.set(LOW_DEPTH, origProps.require("lower-depth", Double.class));
		}
		
		// optional ones to carry forward
		if (origProps.containsKey("state"))
			mappedProps.set("PrimState", origProps.require("state", String.class));
		if (origProps.containsKey("references"))
			mappedProps.set("references", origProps.get("references"));
		
		return new GeoJSONFaultSection(new Feature(id, geometry, mappedProps));
	}
	
	public Feature toFeature() {
		Geometry geometry;
		Preconditions.checkNotNull(trace, "Trace is null");
		Geometry traceGeom = new LineString(trace);
		if (upperDepth != 0d && dip != 90d) {
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
		ArrayList<FaultTrace> equalLengthSubsTrace =
				FaultUtils.getEqualLengthSubsectionTraces(this.trace, maxSubSectionLen, minSubSections);
		ArrayList<GeoJSONFaultSection> subSectionList = new ArrayList<GeoJSONFaultSection>();
		for(int i=0; i<equalLengthSubsTrace.size(); ++i) {
			int myID = startId + i;
			String myName = name+", Subsection "+(i);
			GeoJSONFaultSection subSection = new GeoJSONFaultSection(this);
			
			// clear these just in case they were somehow set externally
			subSection.properties.remove("FaultID");
			subSection.properties.remove("FaultName");
			
			subSection.setSectionName(myName);
			subSection.setSectionId(myID);
			subSection.trace = equalLengthSubsTrace.get(i);
			subSection.setParentSectionId(this.id);
			subSection.setParentSectionName(this.name);
			// make sure dip direction is set from parent
			subSection.setDipDirection(dipDirection);
			subSectionList.add(subSection);
		}
		return subSectionList;
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
		if (lowerTrace != null)
			return getApproxGriddedSurface(gridSpacing, aseisReducesArea);
		return getStirlingGriddedSurface(gridSpacing, preserveGridSpacingExactly, aseisReducesArea);
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
		if (approxGriddedCache == null)
			approxGriddedCache = new ApproxEvenlyGriddedSurfaceCache(this, lowerTrace);
		return approxGriddedCache.getStirlingGriddedSurface(gridSpacing, aseisReducesArea);
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
