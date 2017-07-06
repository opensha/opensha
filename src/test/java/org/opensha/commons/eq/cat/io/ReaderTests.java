package org.opensha.commons.eq.cat.io;

import static org.junit.Assert.*;
import static org.opensha.commons.eq.cat.util.DataType.*;
import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.eq.cat.Catalog;
import org.opensha.commons.eq.cat.DefaultCatalog;
import org.opensha.commons.eq.cat.MutableCatalog;
import org.opensha.commons.eq.cat.util.MagnitudeType;

public class ReaderTests {

	private static File BASIC_DAT = loadFile("Basic.cat");
	private static File ANSS_DAT = loadFile("ANSS.cat");
	private static File LSH_DAT = loadFile("LSH.cat");
	private static File NC_PARK_DAT = loadFile("NC_PARK.cat");
	private static File NC_USGS_DAT = loadFile("NC_USGS.cat");
	private static File NC_WALDHAUSER_DAT = loadFile("NC_WALDHAUSER.cat");
	private static File NCEDC_DAT = loadFile("NCEDC.cat");
	private static File SCEDC_DAT = loadFile("SCEDC.cat");
	private static File SCSN_DAT = loadFile("SCSN.cat");
	private static File SHLK_DAT = loadFile("SHLK.cat");
	private static File TW_RELOC_DAT = loadFile("TW_RELOC.cat");
	
	private static final String TEST_NAME = "testName";
	private static final String TEST_DESC = "testDesc";
	private static class TestReader extends AbstractReader {
		TestReader(int size) {super(TEST_NAME,TEST_DESC,size);}
		@Override
		public void parseLine(String line) {}
		@Override
		public void loadData() {}
		@Override
		public void initReader() {}
	};
	private static TestReader testReader = new TestReader(1);
	
	private static Catalog testCatalog;
	
	private static File loadFile(String name) {
		try {
			return new File(ReaderTests.class.getResource(
				"cats/"+name).toURI());
		} catch (Exception e) { return null; }
	}
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testCatalog = new MutableCatalog(BASIC_DAT, new Reader_Basic(20));
	}

	@Before
	public void setUp() throws Exception {}

	//////////////////////////////////////////////////
	//												//
	//				AbstractReader					//
	//												//
	//////////////////////////////////////////////////
	
	@Test(expected=IllegalArgumentException.class)
	public void testAbstractReaderIAE1() { 
		CatalogReader r = new Reader_Basic(0);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testAbstractReaderIAE2() {
		CatalogReader r = new Reader_Basic(Catalog.MAX_SIZE + 1);
	}
	
	@Test
	public void testAbstractReader() {
		assertTrue(testReader.toString().equals(TEST_NAME));
		assertTrue(testReader.description().equals(TEST_DESC));
	}
	
	
	@Test(expected=NullPointerException.class)
	public void testProcessNPE1() throws IOException {
		testReader.process(null, testCatalog);
	}
	
	@Test(expected=NullPointerException.class)
	public void testProcessNPE2() throws IOException {
		testReader.process(new File(""), null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testProcessIOE() throws IOException {
		Reader_Basic r = new Reader_Basic(10);
		r.process(loadFile("Bad.cat"), new MutableCatalog());
	}

	//////////////////////////////////////////////////
	//												//
	//				Individual Readers				//
	//												//
	//////////////////////////////////////////////////

	@Test
	public void testReader_Basic() throws IOException {
		Catalog c = new MutableCatalog(BASIC_DAT, new Reader_Basic(20));
		assertEquals(-1058788, c.getEventID(4));
		assertEquals(37.3127, c.getValue(LATITUDE, 4), 0.00001);
		assertEquals(-121.6315, c.getValue(LONGITUDE, 4), 0.00001);
		assertEquals(7.190, c.getValue(DEPTH, 4), 0.0001);
		assertEquals(1.48, c.getValue(MAGNITUDE, 4), 0.001);
		assertEquals(347219807000L, c.getTime(4));
	}

	
	@Test
	public void testReader_ANSS() throws IOException {
		Catalog c = new MutableCatalog(ANSS_DAT, new Reader_ANSS(20));
		assertEquals(-1049666, c.getEventID(2));
		assertEquals(1014004, c.getEventID(3));
		assertEquals(-1049675, c.getEventID(4));
		assertEquals(37.4525, c.getValue(LATITUDE, 4), 0.00001);
		assertEquals(-121.5290, c.getValue(LONGITUDE, 4), 0.00001);
		assertEquals(6.56, c.getValue(DEPTH, 4), 0.001);
		assertEquals(2.41, c.getValue(MAGNITUDE, 4), 0.001);
		assertEquals(315602779970L, c.getTime(4));
	}
	
	
	@Test
	public void testReader_LSH() throws IOException {
		Catalog c = new MutableCatalog(LSH_DAT, new Reader_LSH(20));
		assertEquals(3301631, c.getEventID(2));
		assertEquals(34.10257, c.getValue(LATITUDE, 2), 0.000001);
		assertEquals(-117.20084, c.getValue(LONGITUDE, 2), 0.000001);
		assertEquals(7.564, c.getValue(DEPTH, 2), 0.00001);
		assertEquals(1.50, c.getValue(MAGNITUDE, 2), 0.001);
		assertEquals(347414013050L, c.getTime(2));
	}


	@Test
	public void testReader_NC_PARK() throws IOException {
		Catalog c = new MutableCatalog(NC_PARK_DAT, new Reader_NC_PARK(20));
		assertEquals(21161445, c.getEventID(1));
		assertEquals(36.057106, c.getValue(LATITUDE, 1), 0.0000001);
		assertEquals(-120.623071, c.getValue(LONGITUDE, 1), 0.0000001);
		assertEquals(1.800, c.getValue(DEPTH, 1), 0.0001);
		assertEquals(1.1, c.getValue(MAGNITUDE, 1), 0.01);
		assertEquals(988706991610L, c.getTime(1));
	}


	@Test
	public void testReader_NC_USGS() throws IOException {
		Catalog c = new MutableCatalog(NC_USGS_DAT, new Reader_NC_USGS(20));
		assertEquals(17143, c.getEventID(1));
		assertEquals(37.259247, c.getValue(LATITUDE, 1), 0.0000001);
		assertEquals(-121.639877, c.getValue(LONGITUDE, 1), 0.0000001);
		assertEquals(6.735, c.getValue(DEPTH, 1), 0.0001);
		assertEquals(2.5, c.getValue(MAGNITUDE, 1), 0.01);
		assertEquals(452226002830L, c.getTime(1));
	}

	
	@Test
	public void testReader_NC_WALDHAUSER() throws IOException {
		Catalog c = new MutableCatalog(NC_WALDHAUSER_DAT, new Reader_NC_WALDHAUSER(20));
		assertEquals(-1109408, c.getEventID(3));
		assertEquals(37.51576, c.getValue(LATITUDE, 3), 0.000001);
		assertEquals(-118.75564, c.getValue(LONGITUDE, 3), 0.000001);
		assertEquals(7.427, c.getValue(DEPTH, 3), 0.0001);
		assertEquals(1.2, c.getValue(MAGNITUDE, 3), 0.01);
		assertEquals(441772084250L, c.getTime(3));
	}

	
	@Test
	public void testReader_NCEDC() throws IOException {
		Catalog c = new MutableCatalog(NCEDC_DAT, new Reader_NCEDC(20));
		assertEquals(21213006, c.getEventID(2));
		assertEquals(35.1800, c.getValue(LATITUDE, 2), 0.00001);
		assertEquals(-119.4055, c.getValue(LONGITUDE, 2), 0.00001);
		assertEquals(20.84, c.getValue(DEPTH, 2), 0.001);
		assertEquals(3.03, c.getValue(MAGNITUDE, 2), 0.001);
		assertEquals(1013890233750L, c.getTime(2));
	}

	
	@Test
	public void testReader_SCEDC() throws IOException {
		Catalog c = new MutableCatalog(SCEDC_DAT, new Reader_SCEDC(20));
		assertEquals(10148409, c.getEventID(2));
		assertEquals(34.530, c.getValue(LATITUDE, 2), 0.0001);
		assertEquals(-116.273, c.getValue(LONGITUDE, 2), 0.0001);
		assertEquals(5.4, c.getValue(DEPTH, 2), 0.01);
		assertEquals(1.50, c.getValue(MAGNITUDE, 2), 0.001);
		assertEquals(1129613952850L, c.getTime(2));
	}
	
	@Test
	public void testReader_SCSN() throws IOException {
		Catalog c = new MutableCatalog(SCSN_DAT, new Reader_SCSN(20));
		assertEquals(14204720, c.getEventID(2));
		assertEquals(34.2396, c.getValue(LATITUDE, 2), 0.0001);
		assertEquals(-117.44683, c.getValue(LONGITUDE, 2), 0.00001);
		assertEquals(10.70, c.getValue(DEPTH, 2), 0.001);
		assertEquals(3.5, c.getValue(MAGNITUDE, 2), 0.01);
		assertEquals(1134785727490L, c.getTime(2));
	}

	
	@Test
	public void testReader_SHLK() throws IOException {
		Catalog c = new MutableCatalog(SHLK_DAT, new Reader_SHLK(20));
		assertEquals(28281, c.getEventID(1));
		assertEquals(35.17560, c.getValue(LATITUDE, 1), 0.000001);
		assertEquals(-119.02250, c.getValue(LONGITUDE, 1), 0.0000001);
		assertEquals(15.890, c.getValue(DEPTH, 1), 0.0001);
		assertEquals(1.67, c.getValue(MAGNITUDE, 1), 0.001);
		assertEquals(441833367328L, c.getTime(1));
	}

	
	@Test
	public void testReader_TW_RELOC() throws IOException {
		Catalog c = new MutableCatalog(TW_RELOC_DAT, new Reader_TW_RELOC(20));
		assertEquals(23.4170, c.getValue(LATITUDE, 1), 0.00001);
		assertEquals(122.1108, c.getValue(LONGITUDE, 1), 0.000001);
		assertEquals(12.39, c.getValue(DEPTH, 1), 0.001);
		assertEquals(4.47, c.getValue(MAGNITUDE, 1), 0.001);
		assertEquals(1135661118540L, c.getTime(1));
	}

	
	public static void main(String[] args) {
		try {
			Catalog c = new MutableCatalog(TW_RELOC_DAT, new Reader_TW_RELOC(20));
			System.out.println(c.getTime(1));
//			Catalog c = new DefaultCatalog(LSH_DAT, new Reader_LSH(20));
//			System.out.println(c.getTime(2));
//			System.out.println(c.getEventID(3));
//			for (int i=0; i<c.size(); i++) {
//				System.out.println(c.getEventStringWithID(i));
//			}
			//assertEquals(c.getValue(LATITUDE, 3), 37.4525, 0.00001);
		} catch (IOException e) { e.printStackTrace(); }
	}
	
	

}
