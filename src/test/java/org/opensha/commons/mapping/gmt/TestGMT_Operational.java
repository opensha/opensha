package org.opensha.commons.mapping.gmt;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import util.TestUtils;

public class TestGMT_Operational {
	
	private static GMT_MapGenerator gmt;
	private static GeoDataSet xyz;
	
	@BeforeClass
	public static void setUp() throws Exception {
		gmt = new GMT_MapGenerator();
		gmt.getAdjustableParamsList().getParameter(GMT_MapGenerator.LOG_PLOT_NAME).setValue(new Boolean(false));
		xyz = TestGMT_MapGenerator.generateTestData();
	}
	
	@Test
	public void testMakeMapUsingServletGMT_MapStringString() throws Throwable {
		TestUtils.runTestWithTimer("runTest", this, 120);
	}
	
	@SuppressWarnings("unused")
	private void runTest() {
		GMT_Map map = gmt.getGMTMapSpecification(xyz);
		for (int i=0; i<xyz.size(); i++) {
			System.out.println(xyz.getLocation(i) + ": " + xyz.get(i));
		}
		map.setRegion(new Region(new Location(xyz.getMaxLat(), xyz.getMaxLon()),
				new Location(xyz.getMinLat(), xyz.getMinLon())));
		map.setCustomScaleMin(null);
		map.setCustomScaleMax(null);
		String metadata = "My Map Metadata";
		String addr = null;
		try {
			addr = gmt.makeMapUsingServlet(map, metadata, "jUnitTest_" + System.currentTimeMillis());
		} catch (GMT_MapException e) {
			e.printStackTrace();
			fail("GMT_MapException: " + e.getMessage());
		} catch (RuntimeException e) {
			e.printStackTrace();
			fail("RuntimeException: " + e.getMessage());
		}
		assertNotNull("Image address should not be null", addr);
		assertTrue("Image address should not be of length 0", addr.length() > 0);
		assertTrue("Image address should start with 'http://'", addr.startsWith("http://"));
		URL url = null;
		try {
			url =  new URL(addr);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			fail("MalformedURLException for URL '" + addr + "', " + e.getMessage());
		}
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection)url.openConnection();
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException opening connection: " + e.getMessage());
		}
		assertTrue("Connection to fetch image should give JPEG or PNG content type",
				conn.getContentType().contains("jpeg") || conn.getContentType().contains("png"));
		BufferedImage image = null;
		try {
			image =  ImageIO.read(conn.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
			fail("IOException reading connection as image: " + e.getMessage());
		}
		int width = image.getWidth();
		int height = image.getHeight();
		
		System.out.println("Image size: " + width + "x" + height);
		
		assertTrue("Image width should be at least 300 pixels", width >= 300);
		assertTrue("Image hgithg should be at least 300 pixels", height >= 300);
		
		// the image should also not be simply blank, so lets make sure there are 100 unique colors
		ArrayList<Integer> colors = new ArrayList<Integer>();
		for (int x=0; x<width; x++) {
			for (int y=0; y<height; y++) {
				Integer argb = image.getRGB(x, y);
				if (!colors.contains(argb))
					colors.add(argb);
			}
		}
		System.out.println("Out of " + (width * height) + " pixels, " +
				"there are " + colors.size() + " unique colors.");
		int minUnique = 10000; // standard real test
//		int minUnique = 30000; // temp test to fail
		assertTrue("there should be at least "+minUnique
				+" unique colors (found: "+colors.size()+")", colors.size() > minUnique);
	}

}
