package org.opensha.sha.faultSurface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.LastEventData;

public class GeoJSONFaultSectionTest {
	
	private static Gson gson;
	private static Random r = new Random();
	
	private static List<FaultSection> faultModel;
	
	@BeforeClass
	public static void beforeClass() throws IOException {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		gson = builder.create();
		
		faultModel = FaultModels.FM3_1.fetchFaultSections();
		LastEventData.populateSubSects(faultModel, LastEventData.load());
	}
	
	private static String toJSON(FaultSection sect) {
		GeoJSONFaultSection jsonSect;
		if (sect instanceof GeoJSONFaultSection)
			jsonSect = (GeoJSONFaultSection)sect;
		else
			jsonSect = new GeoJSONFaultSection(sect);
		
		return gson.toJson(jsonSect.toFeature());
	}
	
	private static GeoJSONFaultSection fromJSON(String json) {
		Feature feature = gson.fromJson(json, Feature.class);
		return GeoJSONFaultSection.fromFeature(feature);
	}

	@Test
	public void testFM_Serialize() {
		for (FaultSection sect : faultModel) {
			String json = toJSON(sect);
			
//			System.out.println(json);
			
			FaultSection deser = fromJSON(json);
			testEquals(sect, deser);
		}
	}
	
	@Test
	public void testInferTraceDepth() {
		// first don't specify the depth, make sure it's inferred
		Geometry geom = new Geometry.LineString(new Location(0d, 0d), new Location(1d, 1d));
		geom.setSerializeZeroDepths(false);
		FeatureProperties props = new FeatureProperties();
		double upDepth = 3d;
		props.set("DipDeg", 60d);
		props.set("UpDepth", upDepth);
		props.set("LowDepth", 12d);
		props.set("Rake", 90d);
		Feature feature = new Feature(1, geom, props);
		
		GeoJSONFaultSection sect = GeoJSONFaultSection.fromFeature(feature);
		for (Location loc : sect.getFaultTrace())
			assertEquals("Depth was not specified, should have been set to upper depth", upDepth, loc.getDepth(), TOL);
		
		// now specify the depth as zero, make sure it's not blown away
		geom.setSerializeZeroDepths(true);
		feature = new Feature(1, geom, props);
		sect = GeoJSONFaultSection.fromFeature(feature);
		for (Location loc : sect.getFaultTrace())
			assertEquals("Depth was specified, should not have been set to upper depth", 0d, loc.getDepth(), TOL);
		
		// not specify the depth as non zero, make sure it's retained
		double customDepth = 1d;
		geom = new Geometry.LineString(new Location(0d, 0d, 1d), new Location(1d, 1d, 1d));
		feature = new Feature(1, geom, props);
		sect = GeoJSONFaultSection.fromFeature(feature);
		for (Location loc : sect.getFaultTrace())
			assertEquals("Depth was specified, should not have been set to upper depth", customDepth, loc.getDepth(), TOL);
	}

	private static void testEquals(FaultSection orig, FaultSection test) {
		assertEquals("Name mismatch", orig.getSectionName(), test.getSectionName());
		assertEquals("ID mismatch", orig.getSectionId(), test.getSectionId());
		doubleTest("Aseis", orig.getAseismicSlipFactor(), test.getAseismicSlipFactor());
		doubleTest("Dip", orig.getAveDip(), test.getAveDip());
		doubleTest("Rake", orig.getAveRake(), test.getAveRake());
		doubleTest("CC", orig.getCouplingCoeff(), test.getCouplingCoeff());
		assertEquals("Date last mismatch", orig.getDateOfLastEvent(), test.getDateOfLastEvent());
		doubleTest("CC", orig.getDipDirection(), test.getDipDirection());
		doubleTest("Slip rate", orig.getOrigAveSlipRate(), test.getOrigAveSlipRate());
		doubleTest("Slip std dev", orig.getOrigSlipRateStdDev(), test.getOrigSlipRateStdDev());
		assertEquals("Parent ID", orig.getParentSectionId(), test.getParentSectionId());
		assertEquals("Parent Name", orig.getParentSectionName(), test.getParentSectionName());
		doubleTest("Slip in last event", orig.getSlipInLastEvent(), test.getSlipInLastEvent());
		doubleTest("Lower", orig.getAveLowerDepth(), test.getAveLowerDepth());
		doubleTest("Upper", orig.getOrigAveUpperDepth(), test.getOrigAveUpperDepth());
		doubleTest("Creep reduced area", orig.getArea(true), test.getArea(true));
		doubleTest("Orig area", orig.getArea(false), test.getArea(false));
		doubleTest("Trace length", orig.getTraceLength(), test.getTraceLength());
		
		FaultTrace origTrace = orig.getFaultTrace();
		FaultTrace testTrace = test.getFaultTrace();
		locListTest("trace", origTrace, testTrace);
		
		Region origPoly = orig.getZonePolygon();
		Region testPoly = test.getZonePolygon();
		if (origPoly == null) {
			assertNull("Had no polygon but serialized has one?", testPoly);
		} else {
			assertNotNull("Had a polygon but serialized doesn't", testPoly);
			LocationList origBorder = Geometry.getPolygonBorder(origPoly.getBorder(), false);
			LocationList testBorder = Geometry.getPolygonBorder(testPoly.getBorder(), false);
			locListTest("poly border", origBorder, testBorder);
		}
		
		assertTrue("equals() is false", orig.equals(test));
		assertEquals("hashCode() mismatch", orig.hashCode(), test.hashCode());
	}
	
	private static final double TOL = 1e-16;
	private static void doubleTest(String type, double expected, double actual) {
		if (Double.isNaN(expected))
			assertTrue(type+" should be NaN", Double.isNaN(actual));
		else
			assertEquals(type+" mismatch", expected, actual, TOL);
	}
	
	private static void locListTest(String name, LocationList expected, LocationList actual) {
		assertEquals(name+" size mismatch", expected.size(), actual.size());
		for (int i=0; i<expected.size(); i++)
			locTest(name+" pt "+i, expected.get(i), actual.get(i));
	}
	
	private static void locTest(String name, Location expected, Location actual) {
		assertEquals(name+" lat mismatch", expected.getLatitude(), actual.getLatitude(), TOL);
		assertEquals(name+" lon mismatch", expected.getLongitude(), actual.getLongitude(), TOL);
		assertEquals(name+" depth mismatch", expected.getDepth(), actual.getDepth(), TOL);
	}
	
	@Test
	public void testStandardSerializedObjectTypes() {
		
		for (FaultSection sect : faultModel) {
			sect = sect.clone();
			int randParent = r.nextInt(1000);
			sect.setParentSectionId(randParent);
			double randSlipInLast = r.nextDouble();
			sect.setSlipInLastEvent(randSlipInLast);
			boolean connector = r.nextBoolean();
			((FaultSectionPrefData)sect).setConnector(connector);
			String randParentName = "Fake parent "+r.nextFloat();
			sect.setParentSectionName(randParentName);
			String json = toJSON(sect);
			
//			System.out.println(json);
			
			GeoJSONFaultSection deser = fromJSON(json);
			testEquals(sect, deser);
			
			testLongProperty(GeoJSONFaultSection.PARENT_ID, deser, randParent);
			
			testStringProperty(GeoJSONFaultSection.PARENT_NAME, deser, randParentName);
			
			testDoubleProperty(GeoJSONFaultSection.SLIP_LAST, deser, randSlipInLast);
			
			testBooleanProperty(GeoJSONFaultSection.CONNECTOR, deser, connector, false);
		}
	}
	
	@Test
	public void testExternalSerializedObjectTypes() {
		String intPropName = "IntegerProp";
		String longPropName = "LongProp";
		String doublePropName = "DoubleProp";
		String floatPropName = "FloatProp";
		String stringPropName = "StringProp";
		String boolPropName = "BooleanProp";
		for (FaultSection sect : faultModel) {
			GeoJSONFaultSection jsonSect = new GeoJSONFaultSection(sect);
			
			int intVal = r.nextInt();
			long longVal = r.nextLong();
			double doubleVal = r.nextDouble();
			float floatVal = r.nextFloat();
			String stringVal = "String "+r.nextLong();
			boolean boolVal = r.nextBoolean();
			
			jsonSect.setProperty(intPropName, intVal);
			jsonSect.setProperty(longPropName, longVal);
			jsonSect.setProperty(doublePropName, doubleVal);
			jsonSect.setProperty(floatPropName, floatVal);
			jsonSect.setProperty(stringPropName, stringVal);
			jsonSect.setProperty(boolPropName, boolVal);
			
			String json = toJSON(jsonSect);
			
//			System.out.println(json);
			
			GeoJSONFaultSection deser = fromJSON(json);
			testEquals(sect, deser);
			
			testLongProperty(intPropName, deser, intVal);
			testLongProperty(longPropName, deser, longVal);
			testFloatProperty(floatPropName, deser, floatVal);
			testDoubleProperty(doublePropName, deser, doubleVal);
			testStringProperty(stringPropName, deser, stringVal);
			testBooleanProperty(boolPropName, deser, boolVal, true);
		}
	}
	
	@Test
	public void testQuotedObjectTypes() {
		String singleQuoteLongPropName = "SingleQuoteLongProp";
		String doubleQuoteLongPropName = "DoubleQuoteLongProp";
		String singleQuoteDoublePropName = "SingleQuoteDoubleProp";
		String doubleQuoteDoublePropName = "DoubleQuoteDoubleProp";
		String singleQuoteBoolPropName = "SingleQuoteBoolProp";
		String doubleQuoteBoolPropName = "DoubleQuoteBoolProp";
		for (FaultSection sect : faultModel) {
			GeoJSONFaultSection jsonSect = new GeoJSONFaultSection(sect);
			
			long longVal = r.nextLong();
			double doubleVal = r.nextDouble();
			boolean boolVal = r.nextBoolean();
			
			jsonSect.setProperty(singleQuoteLongPropName, getEscapeKey(1));
			jsonSect.setProperty(doubleQuoteLongPropName, getEscapeKey(2));
			jsonSect.setProperty(singleQuoteDoublePropName, getEscapeKey(3));
			jsonSect.setProperty(doubleQuoteDoublePropName, getEscapeKey(4));
			jsonSect.setProperty(singleQuoteBoolPropName, getEscapeKey(5));
			jsonSect.setProperty(doubleQuoteBoolPropName, getEscapeKey(6));
			
			String json = toJSON(jsonSect);
			
			json = escapeReplace(json, 1, "'"+longVal+"'");
			json = escapeReplace(json, 2, "\""+longVal+"\"");
			json = escapeReplace(json, 3, "'"+doubleVal+"'");
			json = escapeReplace(json, 4, "\""+doubleVal+"\"");
			json = escapeReplace(json, 5, "'"+boolVal+"'");
			json = escapeReplace(json, 6, "\""+boolVal+"\"");
			
//			System.out.println(json);
			
			GeoJSONFaultSection deser = fromJSON(json);
			testEquals(sect, deser);
			
			testLongProperty(singleQuoteLongPropName, deser, longVal);
			testLongProperty(doubleQuoteLongPropName, deser, longVal);
			testDoubleProperty(singleQuoteDoublePropName, deser, doubleVal);
			testDoubleProperty(doubleQuoteDoublePropName, deser, doubleVal);
			testBooleanProperty(singleQuoteBoolPropName, deser, boolVal, true);
			testBooleanProperty(doubleQuoteBoolPropName, deser, boolVal, true);
		}
	}
	
	private static final long escape_base = 12345654321000l;
	
	private static long getEscapeKey(int index) {
		return escape_base+(long)index;
	}
	
	private static String escapeReplace(String json, int index, String replacement) {
		long key = getEscapeKey(index);
		if (!json.contains(key+""))
			throw new IllegalStateException("Escape key not found in JSON: "+key);
		return json.replaceFirst(key+"", replacement);
	}
	
	private static void testLongProperty(String propName, GeoJSONFaultSection sect, long expected) {
		Object actual = sect.getProperty(propName, null);
		assertNotNull(propName+" wasn't [de]serialized", actual);
		if (!(actual instanceof String)) {
			assertTrue(propName+" should be deserialized as a Number, type is "+actual.getClass().getName(), actual instanceof Number);
			assertTrue(propName+" should be deserialized as a Long, type is "+actual.getClass().getName(), actual instanceof Long);
		}
		assertEquals(propName+" wasn't deserialized correctly",
				expected, sect.getProperties().getLong(propName, -1));
	}
	
	private static void testDoubleProperty(String propName, GeoJSONFaultSection sect, double expected) {
		Object actual = sect.getProperty(propName, null);
		assertNotNull(propName+" wasn't [de]serialized", actual);
		if (!(actual instanceof String)) {
			assertTrue(propName+" should be deserialized as a Number, type is "+actual.getClass().getName(), actual instanceof Number);
			assertTrue(propName+" should be deserialized as a Double, type is "+actual.getClass().getName(), actual instanceof Double);
		}
		doubleTest(propName, expected, sect.getProperties().getDouble(propName, Double.NaN));
	}
	
	private static void testFloatProperty(String propName, GeoJSONFaultSection sect, float expected) {
		Object actual = sect.getProperty(propName, null);
		assertNotNull(propName+" wasn't [de]serialized", actual);
		if (!(actual instanceof String)) {
			assertTrue(propName+" should be deserialized as a Number, type is "+actual.getClass().getName(), actual instanceof Number);
			assertTrue(propName+" should be deserialized as a Double, type is "+actual.getClass().getName(), actual instanceof Double);
		}
		doubleTest(propName, expected, (float)sect.getProperties().getDouble(propName, Double.NaN));
	}
	
	private static void testStringProperty(String propName, GeoJSONFaultSection sect, String expected) {
		Object actual = sect.getProperty(propName, null);
		assertNotNull(propName+" wasn't [de]serialized", actual);
		assertTrue(propName+" should be deserialized as a String, type is "+actual.getClass().getName(), actual instanceof String);
		assertEquals(propName+" wasn't deserialized correctly", expected, (String)actual);
	}
	
	private static void testBooleanProperty(String propName, GeoJSONFaultSection sect, boolean expected, boolean serialzeFalse) {
		Object actual = sect.getProperty(propName, null);
		if (expected || serialzeFalse) {
			assertNotNull(propName+" wasn't [de]serialized", actual);
			if (!(actual instanceof String)) {
				assertTrue(propName+" should be deserialized as a Boolean, type is "+actual.getClass().getName(),
						actual instanceof Boolean);
			}
			assertEquals(propName+" wasn't deserialized correctly",
					expected, sect.getProperties().getBoolean(propName, !expected));
		} else {
			assertNull(propName+" is false and shouldn't have been serialized", actual);
		}
	}

}
