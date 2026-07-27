package org.opensha.commons.data.siteData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SiteDataValueListJSONTests {
	
	private static final double TOL = 1e-12;
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson geoJSONGson = FeatureCollection.buildGson();
	
	@Test
	public void testValueListAdapter() {
		SiteDataValueList<Double> list = buildDoubleList(true);
		
		String json = gson.toJson(list, SiteDataValueList.class);
		SiteDataValueList<?> deser = gson.fromJson(json, SiteDataValueList.class);
		
		assertValueListEquals(list, deser, true);
		assertNull("NaN values should be serialized as null", deser.getValues().get(2));
	}
	
	@Test
	public void testFeaturePropertiesValueList() {
		SiteDataValueList<Double> list = buildDoubleList(false);
		FeatureProperties props = new FeatureProperties();
		props.set("siteData", list);
		
		String json = geoJSONGson.toJson(props, FeatureProperties.class);
		FeatureProperties deserProps = geoJSONGson.fromJson(json, FeatureProperties.class);
		
		Object deserObj = deserProps.get("siteData");
		assertTrue("Expected SiteDataValueList, got "+deserObj.getClass()+":\n"+json,
				deserObj instanceof SiteDataValueList<?>);
		assertValueListEquals(list, (SiteDataValueList<?>)deserObj, false);
	}
	
	@Test
	public void testFeaturePropertiesValueListList() {
		SiteDataValueList<Double> doubleList = buildDoubleList(false);
		
		ArrayList<String> stringVals = new ArrayList<>();
		stringVals.add("A");
		stringVals.add(null);
		stringVals.add("C");
		stringVals.add("D");
		SiteDataValueList<String> stringList = new SiteDataValueList<>(
				SiteData.TYPE_WILLS_CLASS, SiteData.TYPE_FLAG_INFERRED, stringVals, "String Source");
		
		ArrayList<SiteDataValueList<?>> lists = new ArrayList<>();
		lists.add(doubleList);
		lists.add(stringList);
		SiteDataValueListList listList = new SiteDataValueListList(lists);
		
		FeatureProperties props = new FeatureProperties();
		props.set("siteDataLists", listList);
		
		String json = geoJSONGson.toJson(props, FeatureProperties.class);
		FeatureProperties deserProps = geoJSONGson.fromJson(json, FeatureProperties.class);
		
		Object deserObj = deserProps.get("siteDataLists");
		assertTrue("Expected SiteDataValueListList, got "+deserObj.getClass()+":\n"+json,
				deserObj instanceof SiteDataValueListList);
		SiteDataValueListList deser = (SiteDataValueListList)deserObj;
		assertEquals(listList.size(), deser.size());
		assertEquals(listList.getNumProviders(), deser.getNumProviders());
		
		for (int i=0; i<listList.size(); i++) {
			Double expectedDouble = doubleList.getValues().get(i);
			Object actualDouble = deser.getDataList(i).get(0).getValue();
			if (expectedDouble != null && Double.isNaN(expectedDouble))
				assertNull(actualDouble);
			else
				assertEquals(expectedDouble, actualDouble);
			assertEquals(stringList.getValues().get(i), deser.getDataList(i).get(1).getValue());
		}
	}
	
	private static SiteDataValueList<Double> buildDoubleList(boolean withLocs) {
		ArrayList<Double> vals = new ArrayList<>();
		vals.add(760d);
		vals.add(null);
		vals.add(Double.NaN);
		vals.add(500d);
		
		LocationList locs = null;
		if (withLocs) {
			locs = new LocationList();
			locs.add(new Location(34d, -118d));
			locs.add(new Location(34.1d, -118.1d));
			locs.add(new Location(34.2d, -118.2d));
			locs.add(new Location(34.3d, -118.3d));
		}
		
		return new SiteDataValueList<>(
				SiteData.TYPE_VS30, SiteData.TYPE_FLAG_INFERRED, vals, "Double Source", locs);
	}
	
	private static void assertValueListEquals(SiteDataValueList<?> expected, SiteDataValueList<?> actual,
			boolean expectLocs) {
		assertEquals(expected.getType(), actual.getType());
		assertEquals(expected.getFlag(), actual.getFlag());
		assertEquals(expected.getSourceName(), actual.getSourceName());
		assertEquals(expected.size(), actual.size());
		assertEquals(expectLocs, actual.hasLocations());
		
		for (int i=0; i<expected.size(); i++) {
			Object expectedValue = expected.getValues().get(i);
			Object actualValue = actual.getValues().get(i);
			if (expectedValue instanceof Double && Double.isNaN((Double)expectedValue))
				assertNull(actualValue);
			else
				assertEquals(expectedValue, actualValue);
			
			if (expectLocs) {
				Location expectedLoc = expected.getLocationAt(i);
				Location actualLoc = actual.getLocationAt(i);
				assertEquals(expectedLoc.getLatitude(), actualLoc.getLatitude(), TOL);
				assertEquals(expectedLoc.getLongitude(), actualLoc.getLongitude(), TOL);
				assertEquals(expectedLoc.getDepth(), actualLoc.getDepth(), TOL);
			}
		}
	}

}
