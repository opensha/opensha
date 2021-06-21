package org.opensha.sha.faultSurface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

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
	private Map<String, Object> properties;
	
	// helpers
	private StirlingSurfaceCache stirlingCache;
	
	// property names
	static final String DATE_LAST = "DateLastEvent";
	static final String SLIP_LAST = "SlipLastEvent";
	static final String ASEIS = "AseismicSlipFactor";
	static final String COUPLING = "CouplingCoeff";
	static final String SLIP_RATE = "SlipRate";
	static final String PARENT_ID = "ParentID";
	static final String PARENT_NAME = "ParentName";
	static final String SLIP_STD_DEV = "SlipRateStdDev";
	static final String CONNECTOR = "Connector";

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
			this.properties = new HashMap<>(((GeoJSONFaultSection)sect).properties);
		} else {
			this.properties = new HashMap<>();
			setDateOfLastEvent(sect.getDateOfLastEvent());
			setSlipInLastEvent(sect.getSlipInLastEvent());
			setAseismicSlipFactor(sect.getAseismicSlipFactor());
			setCouplingCoeff(sect.getCouplingCoeff());
			setAveSlipRate(sect.getOrigAveSlipRate());
			setParentSectionId(sect.getParentSectionId());
			setParentSectionName(sect.getParentSectionName());
			setSlipRateStdDev(sect.getOrigSlipRateStdDev());
			if (sect.isConnector())
				setProperty(CONNECTOR, true);
			setZonePolygon(sect.getZonePolygon());
		}
	}

	GeoJSONFaultSection(int id, String name, double dip, double rake, double upperDepth,
			double lowerDepth, float dipDirection, FaultTrace trace, Map<String, Object> properties) {
		this.id = id;
		this.name = name;
		this.dip = dip;
		this.rake = rake;
		this.upperDepth = upperDepth;
		this.lowerDepth = lowerDepth;
		this.dipDirection = dipDirection;
		this.trace = trace;
		this.properties = properties;
	}
	
	public static class Adapter extends TypeAdapter<GeoJSONFaultSection> {

		@Override
		public void write(JsonWriter out, GeoJSONFaultSection sect) throws IOException {
			out.beginObject();
			
			out.name("type").value("Feature");
			
			
			/*
			 * If a Feature has a commonly used identifier, that identifier
      		 * SHOULD be included as a member of the Feature object with the name
      		 * "id", and the value of this member is either a JSON string or
      		 * number.
			 */
			out.name("id").value(sect.id);
			
			out.name("properties").beginObject();
			
			out.name("FaultID").value(sect.id);
			out.name("FaultName").value(sect.name);
			out.name("DipDeg").value(sect.dip);
			out.name("DipDir").value(sect.dipDirection);
			out.name("Rake").value(sect.rake);
			out.name("LowDepth").value(sect.lowerDepth);
			out.name("UpDepth").value(sect.upperDepth);
			for (String property : sect.properties.keySet()) {
				Object value = sect.properties.get(property);
				if (value == null)
					out.name(property).nullValue();
				else if (value instanceof Number)
					out.name(property).value((Number)value);
				else if (value instanceof Boolean)
					out.name(property).value((Boolean)value);
				else
					out.name(property).value(value.toString());
			}
			
			out.endObject(); // end properties
			
			out.name("geometry").beginObject();
			
			Preconditions.checkState(sect.trace != null);
			if (sect.zonePolygon != null) {
				// need to write both as a multi
				out.name("type").value("GeometryCollection");
				out.name("geometries").beginArray().beginObject();
			}
			
			out.name("type").value("LineString");
			
			out.name("coordinates").beginArray();
			
			for (Location loc : sect.trace) {
				out.beginArray();
				out.value(loc.getLongitude()).value(loc.getLatitude());
				if (loc.getDepth() != 0d)
					out.value(-loc.getDepth()*1e3); // elevation in meters
				out.endArray();
			}
			
			out.endArray(); // end coordinates
			
			out.endObject();// end geometry
			
			if (sect.zonePolygon != null) {
				// finish the multi and add polygon
				out.beginObject();
				
				out.name("type").value("Polygon");
				
				out.name("coordinates").beginArray();
				
				out.beginArray(); // first coord array
				LocationList border = getPolygonBorder(sect.zonePolygon);
				for (Location loc : border) {
					out.beginArray();
					out.value(loc.getLongitude()).value(loc.getLatitude());
					if (loc.getDepth() != 0d)
						out.value(-loc.getDepth()*1e3); // elevation in meters
					out.endArray();
				}
				out.endArray(); // end first coord array
				
				out.endArray(); // end coordinates
				
				out.endObject();// end this geometry
				
				out.endArray(); // end geometries
				
				out.endObject();// end geometry collection
			}
			
			out.endObject(); // end geometry
		}

		@Override
		public GeoJSONFaultSection read(JsonReader in) throws IOException {
			// these are required properties
			Integer id = null;
			String name = null;
			Double dipDeg = null;
			Double rake = null;
			Double upDepth = null;
			Double lowDepth = null;
			Float dipDir = null;
			
			FaultTrace trace = null;
			Region zonePolygon = null;
			
			Map<String, Object> properties = null;
			
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					String type = in.nextString();
					Preconditions.checkState(type.equals("Feature"), "Expected 'Feature' but was '%s'", type);
					break;
				case "properties":
					in.beginObject();
					properties = new HashMap<>();
					while (in.hasNext()) {
						String propName = in.nextName();
						JsonToken token = in.peek();
						switch (propName) {
						case "FaultID":
							id = in.nextInt();
							break;
						case "FaultName":
							name = in.nextString();
							break;
						case "DipDeg":
							dipDeg = in.nextDouble();
							break;
						case "Rake":
							rake = in.nextDouble();
							break;
						case "LowDepth":
							lowDepth = in.nextDouble();
							break;
						case "UpDepth":
							upDepth = in.nextDouble();
							break;
						case "DipDir":
							if (token == JsonToken.NUMBER)
								dipDir = (float)in.nextDouble();
							else
								// skip text-based directions in the NSHM 2023 v2 files, e.g., "SE"
								in.skipValue();
							break;
							

						default:
							if (token == JsonToken.NULL) {
								in.nextNull();
							} else if (token == JsonToken.BOOLEAN) {
								properties.put(propName, in.nextBoolean());
							} else if (token == JsonToken.NUMBER) {
								String numStr = in.nextString();
								if (numStr.contains(".") || numStr.toLowerCase().contains("e"))
									properties.put(propName, Double.parseDouble(numStr));
								else
									properties.put(propName, Long.parseLong(numStr));
							} else if (token == JsonToken.STRING) {
								properties.put(propName, in.nextString());
							} else {
								in.skipValue();
							}
							break;
						}
					}
					in.endObject();
					break;
					
				case "geometry":
					GeometryContainer geomContainer = readGeometry(in, id, name);
					trace = geomContainer.trace;
					zonePolygon = geomContainer.polygon;
					break;
				case "id":
					if (id != null) {
						// use the global 'id' field of the feature object
						try {
							id = in.nextInt();
						} catch (NumberFormatException e) {
							System.err.println("Tried to parse feature 'id' field as an interger ID, but failed: "+e.getMessage());
						}
					} else {
						in.skipValue();
					}
					break;
					
				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			Preconditions.checkNotNull(name);
			Preconditions.checkNotNull(id);
			Preconditions.checkNotNull(dipDeg);
			Preconditions.checkNotNull(rake);
			Preconditions.checkNotNull(lowDepth);
			Preconditions.checkNotNull(upDepth);
			Preconditions.checkNotNull(trace);
			if (dipDir == null)
				dipDir = (float)(trace.getAveStrike()+90d);
			GeoJSONFaultSection sect = new GeoJSONFaultSection(id, name, dipDeg, rake, upDepth, lowDepth, dipDir, trace, properties);
			sect.setZonePolygon(zonePolygon);
			return sect;
		}
		
	}
	
	/**
	 * 
	 * @param region
	 * @return a valid GeoJSON polygon border, closed and following their right-hand rule
	 */
	static LocationList getPolygonBorder(Region region) {
		LocationList border = new LocationList();
		border.addAll(region.getBorder());
		// close it
		if (!border.first().equals(border.last()))
			border.add(border.first());
		Location prev = null;
		
		// figire out the direction
		double directionTest = 0d;
		for (Location loc : border) {
			if (prev != null)
				directionTest += (loc.getLongitude()-prev.getLongitude())*(loc.getLatitude()+prev.getLatitude());
			prev = loc;
		}
//		System.out.println("Direction test: "+directionTest);
		if (directionTest > 0)
			// directionTest > 0 indicates positive, RFC 7946 states that exteriors must be counter-clockwise
			border.reverse();
		
		return border;
	}
	
	private static class GeometryContainer {
		private final Integer id;
		private final String name;
		
		private FaultTrace trace;
		private Region polygon;
		
		public GeometryContainer(Integer id, String name) {
			super();
			this.id = id;
			this.name = name;
		}
	}
	
	static GeometryContainer readGeometry(JsonReader reader, Integer id, String name) throws IOException {
		GeometryContainer container = new GeometryContainer(id, name);
		
		doReadGeometry(reader, container);
		Preconditions.checkNotNull(container.trace, "Trace geometry not found for %s", id);
		
		return container;
	}
	
	private static void doReadGeometry(JsonReader reader, GeometryContainer container) throws IOException {
//		System.out.println("doReadGeometry at "+reader.getPath());
		reader.beginObject();

		String type = null;
		while (reader.hasNext()) {
			LocationList border = null;
			switch (reader.nextName()) {
			case "type":
				type = reader.nextString();
				break;
			case "coordinates":
				Preconditions.checkState(type != null, "type must be supplied before coordinates\n"+reader.getPath());
				switch (type) {
				case "LineString":
					reader.beginArray();
					Preconditions.checkState(container.trace == null,
							"We already have a fault trace for %s but encountered a LineString geometry");
					container.trace = new FaultTrace(container.name);
					container.trace.addAll(loadCoordinatesArray(reader));
					reader.endArray();
					break;
				case "MultiLineString":
					reader.beginArray();
					reader.beginArray();
					Preconditions.checkState(container.trace == null,
							"We already have a fault trace for %s but encountered a MultiLineString geometry");
					container.trace = new FaultTrace(container.name);
					container.trace.addAll(loadCoordinatesArray(reader));
					reader.endArray();
					if (reader.peek() != JsonToken.END_ARRAY) {
						//						System.err.println("WARNING: skipping mult-trace for "+name+" ("+id+")");
						//						while (reader.peek() != JsonToken.END_ARRAY) {
						////							System.out.println("Skipping "+reader.peek());
						//							reader.skipValue();
						//						}
						System.err.println("WARNING: concatenating multi-trace for "+container.name+" ("+container.id+")");
						int extraLocs = 0;
						List<FaultTrace> extraTraces = new ArrayList<>();
						while (reader.hasNext()) {
							reader.beginArray();
							FaultTrace extraTrace = new FaultTrace(null);
							while (reader.hasNext()) {
								reader.beginArray();
								double lon = reader.nextDouble();
								double lat = reader.nextDouble();
								double depth = 0d;
								if (reader.peek() != JsonToken.END_ARRAY)
									depth = -reader.nextDouble()*1e-3; // elev in m -> depth in km
								Location newLoc = new Location(lat, lon, depth);
								extraLocs++;
								extraTrace.add(newLoc);
								reader.endArray();
							}
							extraTraces.add(extraTrace);
							reader.endArray();
						}
						// figure out where they go
						while (!extraTraces.isEmpty()) {
							int bestIndex = -1;
							boolean bestAtEnd = false;
							double bestDistance = Double.POSITIVE_INFINITY;
							for (int i=0; i<extraTraces.size(); i++) {
								FaultTrace extraTrace = extraTraces.get(i);
								double beforeDist = LocationUtils.horzDistanceFast(extraTrace.last(), container.trace.first());
								double afterDist = LocationUtils.horzDistanceFast(extraTrace.first(), container.trace.last());
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
							FaultTrace addTrace = extraTraces.remove(bestIndex);
							//							System.err.println("\tadding extra trace with inOrder="+bestAtEnd+", dist="+bestDistance);
							if (bestAtEnd) {
								System.err.println("\tadding extra trace to end:\n\t\tprevLoc="+container.trace.last()+"\n\t\tnextLoc="
										+addTrace.first()+"\t(dist="+bestDistance+")");
								container.trace.addAll(addTrace);
							} else {
								System.err.println("\tadding extra trace to start (before previous):\n\t\tprevLoc="
										+container.trace.last()+"\n\t\tnextLoc="+addTrace.first()+"\t(dist="+bestDistance+")");
								container.trace.addAll(0, addTrace);
							}
							if (bestIndex != 0)
								System.out.println("\t\tWARNING: didn't add new traces in order");
						}
						//						System.err.println("\tAdded "+extraTraces+" traces w/ "+extraLocs+" locs. "
						//								+ "Max dist from prevLast to new trace: "+(float)maxDistFromPrev);
					}
					reader.endArray();
					break;
				case "Polygon":
					reader.beginArray();
					reader.beginArray();
					Preconditions.checkState(container.polygon == null,
							"We already have a polygon for %s but encountered a Polygon geometry");
					border = loadCoordinatesArray(reader);
					container.polygon = new Region(border, BorderType.MERCATOR_LINEAR);
					reader.endArray();
					reader.endArray();
					break;
				case "MultiPolygon":
					reader.beginArray();
					reader.beginArray();
					reader.beginArray();
					Preconditions.checkState(container.polygon == null,
							"We already have a polygon for %s but encountered a MultiPolygon geometry");
					border = loadCoordinatesArray(reader);
					container.polygon = new Region(border, BorderType.MERCATOR_LINEAR);
					reader.endArray();
					reader.endArray();
					Preconditions.checkState(reader.peek() == JsonToken.END_ARRAY,
							"Although MultiPolygon is technically supported, it must consist of a single polygon");
					reader.endArray();
					break;

				default:
					throw new IllegalStateException("Unsupported geometry type: "+type);
				}
				break;
			case "geometries":
				Preconditions.checkState(type.equals("GeometryCollection"), "Encountered 'geometries' but type='%s'", type);
				reader.beginArray();
				while (reader.hasNext())
					doReadGeometry(reader, container);
				reader.endArray();
				break;

			default:
				break;
			}
		}
		
		reader.endObject();
//		System.out.println("END doReadGeometry at "+reader.getPath());
	}
	
	static LocationList loadCoordinatesArray(JsonReader reader) throws IOException {
		LocationList list = new LocationList();
		while (reader.hasNext()) {
			reader.beginArray();
			double lon = reader.nextDouble();
			double lat = reader.nextDouble();
			double depth = 0d;
			if (reader.peek() != JsonToken.END_ARRAY)
				depth = -reader.nextDouble()*1e-3; // elev in m -> depth in km
			list.add(new Location(lat, lon, depth));
			reader.endArray();
		}
		Preconditions.checkState(!list.isEmpty(), "Coordinate array is empty");
		return list;
	}
	
	public boolean getBooleanProperty(String name, boolean defaultValue) {
		Object val = properties.get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof String) {
			String str = (String)val;
			str = str.trim().toLowerCase();
			if (str.equals("true") || str.equals("yes"))
				return true;
			return false;
		}
		try {
			return (Boolean)val;
		} catch (ClassCastException e) {
			System.err.println("Fault property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
	}
	
	public double getDoubleProperty(String name, double defaultValue) {
		return getNumberProp(name, defaultValue).doubleValue();
	}
	
	public int getIntProperty(String name, int defaultValue) {
		return getNumberProp(name, defaultValue).intValue();
	}
	
	public long getLongProperty(String name, long defaultValue) {
		return getNumberProp(name, defaultValue).longValue();
	}
	
	private Number getNumberProp(String name, Number defaultValue) {
		Object val = properties.get(name);
		if (val == null)
			return defaultValue;
		if (val instanceof String) {
			// try to parse string
			try {
				String str = (String)val;
				if (str.contains(".") || str.toLowerCase().contains("e"))
					return Double.parseDouble(str);
				return Long.parseLong(str);
			} catch (NumberFormatException e) {
				System.err.println("Fault property with name '"+name
						+"' is of a string that could not be parsed to a number: "+e.getMessage());
				return defaultValue;
			}
		}
		try {
			return (Number)val;
		} catch (ClassCastException e) {
			System.err.println("Fault property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
	}
	
	@SuppressWarnings("unchecked") // is checked
	public <E> E getProperty(String name, E defaultValue) {
		Object val = properties.get(name);
		if (val == null)
			return defaultValue;
		try {
			return (E)val;
		} catch (ClassCastException e) {
			System.err.println("Fault property with name '"+name+"' is of an unexpected type: "+e.getMessage());
			return defaultValue;
		}
	}
	
	public void setProperty(String name, Object value) {
		if (value == null)
			clearProperty(name);
		else
			properties.put(name, value);
	}
	
	public boolean clearProperty(String name) {
		return properties.remove(name) != null;
	}
	
	private void setConditional(String name, Object value, boolean condition) {
		if (condition)
			setProperty(name, value);
		else
			clearProperty(name);
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public long getDateOfLastEvent() {
		return getLongProperty(DATE_LAST, Long.MIN_VALUE);
	}

	@Override
	public void setDateOfLastEvent(long dateOfLastEventMillis) {
		setConditional(DATE_LAST, dateOfLastEventMillis, dateOfLastEventMillis > Long.MIN_VALUE);
	}
	

	@Override
	public void setSlipInLastEvent(double slipInLastEvent) {
		setConditional(SLIP_LAST, slipInLastEvent, Double.isFinite(slipInLastEvent));
	}

	@Override
	public double getSlipInLastEvent() {
		return getDoubleProperty(SLIP_LAST, Double.NaN);
	}

	@Override
	public double getAseismicSlipFactor() {
		return getDoubleProperty(ASEIS, 0d);
	}

	@Override
	public void setAseismicSlipFactor(double aseismicSlipFactor) {
		Preconditions.checkArgument(aseismicSlipFactor >= 0d && aseismicSlipFactor <= 1d,
				"aseismicSlipFactor not in the range [0,1]: %s", aseismicSlipFactor);
		setProperty(ASEIS, aseismicSlipFactor);
	}

	@Override
	public double getCouplingCoeff() {
		return getDoubleProperty(COUPLING, 1d);
	}

	@Override
	public void setCouplingCoeff(double couplingCoeff) {
		Preconditions.checkArgument(couplingCoeff >= 0d && couplingCoeff <= 1d,
				"couplingCoeff not in the range [0,1]: %s", couplingCoeff);
		setProperty(COUPLING, couplingCoeff);
	}

	@Override
	public double getAveDip() {
		return dip;
	}

	@Override
	public double getOrigAveSlipRate() {
		return getDoubleProperty(SLIP_RATE, Double.NaN);
	}

	@Override
	public void setAveSlipRate(double aveLongTermSlipRate) {
		setConditional(SLIP_RATE, aveLongTermSlipRate, Double.isFinite(aveLongTermSlipRate));
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
		this.rake = aveRake;
	}

	@Override
	public double getOrigAveUpperDepth() {
		return upperDepth;
	}

	@Override
	public float getDipDirection() {
		return dipDirection;
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
	}

	@Override
	public void setSectionName(String sectName) {
		this.name = sectName;
	}

	@Override
	public int getParentSectionId() {
		return getIntProperty(PARENT_ID, -1);
	}

	@Override
	public void setParentSectionId(int parentSectionId) {
		setConditional(PARENT_ID, parentSectionId, parentSectionId >= 0);
	}
	

	@Override
	public String getParentSectionName() {
		return getProperty(PARENT_NAME, null);
	}

	@Override
	public void setParentSectionName(String parentSectionName) {
		setProperty(PARENT_NAME, parentSectionName);
	}

	@Override
	public List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId, int minSubSections) {
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
			
			subSection.id = myID;
			subSection.name = myName;
			subSection.trace = equalLengthSubsTrace.get(i);
			subSection.setParentSectionId(this.id);
			subSection.setParentSectionName(this.name);
			subSectionList.add(subSection);
		}
		return subSectionList;
	}
	

	@Override
	public double getOrigSlipRateStdDev() {
		return getDoubleProperty(SLIP_STD_DEV, Double.NaN);
	}

	@Override
	public void setSlipRateStdDev(double slipRateStdDev) {
		setConditional(SLIP_STD_DEV, slipRateStdDev, Double.isFinite(slipRateStdDev));
	}

	@Override
	public boolean isConnector() {
		return getBooleanProperty(CONNECTOR, false);
	}

	@Override
	public Region getZonePolygon() {
		return zonePolygon;
	}

	public void setZonePolygon(Region zonePolygon) {
		this.zonePolygon = zonePolygon;
	}

	@Override
	public StirlingGriddedSurface getFaultSurface(double gridSpacing) {
		return getStirlingGriddedSurface(gridSpacing, true, true);
	}

	@Override
	public StirlingGriddedSurface getFaultSurface(
			double gridSpacing, boolean preserveGridSpacingExactly,
			boolean aseisReducesArea) {
		return getStirlingGriddedSurface(gridSpacing, preserveGridSpacingExactly, aseisReducesArea);
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

	@Override
	public FaultSection clone() {
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

	@Override
	public int hashCode() {
		return FaultSection.hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return FaultSection.equals(this, obj);
	}
}
