package org.opensha.refFaultParamDb.vo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests XML round-trip serialization of {@link FaultSectionPrefData} via
 * {@link FaultSectionPrefData#toXMLMetadata(Element)} and
 * {@link FaultSectionPrefData#fromXMLMetadata(Element)}.
 */
public class FaultSectionPrefDataTest {

	private static final double TOL = 1e-10;

	/**
	 * Creates a fully populated {@link FaultSectionPrefData} with all properties set.
	 */
	protected static FaultSectionPrefData createFullyPopulated() {
		FaultSectionPrefData data = new FaultSectionPrefData();
		data.setSectionId(42);
		data.setSectionName("Test Section");
		data.setShortName("TS");
		data.setAveSlipRate(12.5);
		data.setSlipRateStdDev(1.3);
		data.setAveDip(45.0);
		data.setAveRake(-90.0);
		data.setAveUpperDepth(1.0);
		data.setAveLowerDepth(15.0);
		data.setAseismicSlipFactor(0.2);
		data.setCouplingCoeff(0.8);
		data.setDipDirection(270f);
		data.setParentSectionId(7);
		data.setParentSectionName("Parent Fault");
		data.setConnector(true);
		data.setDateOfLastEvent(1_000_000L);
		data.setSlipInLastEvent(2.5);
		data.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);

		FaultTrace trace = new FaultTrace("test trace");
		trace.add(new Location(-41.0, 174.0, 0.0));
		trace.add(new Location(-42.0, 175.0, 0.0));
		data.setFaultTrace(trace);

		return data;
	}

	/**
	 * Serializes data to XML and deserializes it back.
	 */
	protected static FaultSectionPrefData roundTrip(FaultSectionPrefData orig) {
		Document doc = DocumentHelper.createDocument();
		Element root = doc.addElement("root");
		orig.toXMLMetadata(root);
		Element el = root.element(FaultSectionPrefData.XML_METADATA_NAME);
		return FaultSectionPrefData.fromXMLMetadata(el);
	}

	@Test
	public void testXmlRoundTripAllProperties() {
		FaultSectionPrefData orig = createFullyPopulated();
		FaultSectionPrefData deser = roundTrip(orig);

		assertEquals("sectionId", orig.getSectionId(), deser.getSectionId());
		assertEquals("sectionName", orig.getSectionName(), deser.getSectionName());
		assertEquals("shortName", orig.getShortName(), deser.getShortName());
		assertEquals("slipRate", orig.getOrigAveSlipRate(), deser.getOrigAveSlipRate(), TOL);
		assertEquals("slipRateStdDev", orig.getOrigSlipRateStdDev(), deser.getOrigSlipRateStdDev(), TOL);
		assertEquals("dip", orig.getAveDip(), deser.getAveDip(), TOL);
		assertEquals("rake", orig.getAveRake(), deser.getAveRake(), TOL);
		assertEquals("upperDepth", orig.getOrigAveUpperDepth(), deser.getOrigAveUpperDepth(), TOL);
		assertEquals("lowerDepth", orig.getAveLowerDepth(), deser.getAveLowerDepth(), TOL);
		assertEquals("aseismicSlipFactor", orig.getAseismicSlipFactor(), deser.getAseismicSlipFactor(), TOL);
		assertEquals("couplingCoeff", orig.getCouplingCoeff(), deser.getCouplingCoeff(), TOL);
		assertEquals("dipDirection", orig.getDipDirection(), deser.getDipDirection(), TOL);
		assertEquals("parentSectionId", orig.getParentSectionId(), deser.getParentSectionId());
		assertEquals("parentSectionName", orig.getParentSectionName(), deser.getParentSectionName());
		assertEquals("connector", orig.isConnector(), deser.isConnector());
		assertEquals("dateOfLastEvent", orig.getDateOfLastEvent(), deser.getDateOfLastEvent());
		assertEquals("slipInLastEvent", orig.getSlipInLastEvent(), deser.getSlipInLastEvent(), TOL);
		assertEquals("tectonicRegionType", orig.getTectonicRegionType(), deser.getTectonicRegionType());

		FaultTrace origTrace = orig.getFaultTrace();
		FaultTrace deserTrace = deser.getFaultTrace();
		assertEquals("trace size", origTrace.size(), deserTrace.size());
		for (int i = 0; i < origTrace.size(); i++) {
			Location origLoc = origTrace.get(i);
			Location deserLoc = deserTrace.get(i);
			assertEquals("trace lat " + i, origLoc.getLatitude(), deserLoc.getLatitude(), TOL);
			assertEquals("trace lon " + i, origLoc.getLongitude(), deserLoc.getLongitude(), TOL);
			assertEquals("trace depth " + i, origLoc.getDepth(), deserLoc.getDepth(), TOL);
		}
	}

	@Test
	public void testXmlRoundTripOptionalFieldsAbsent() {
		FaultSectionPrefData orig = new FaultSectionPrefData();
		orig.setSectionId(1);
		orig.setSectionName("Minimal");
		orig.setAveSlipRate(5.0);
		orig.setSlipRateStdDev(0.5);
		orig.setAveDip(60.0);
		orig.setAveRake(0.0);
		orig.setAveUpperDepth(0.0);
		orig.setAveLowerDepth(10.0);
		orig.setAseismicSlipFactor(0.0);
		orig.setDipDirection(90f);

		FaultTrace trace = new FaultTrace("minimal trace");
		trace.add(new Location(-40.0, 173.0));
		orig.setFaultTrace(trace);

		FaultSectionPrefData deser = roundTrip(orig);

		assertNull("parentSectionName should be null", deser.getParentSectionName());
		assertEquals("parentSectionId default", -1, deser.getParentSectionId());
		assertEquals("connector default", false, deser.isConnector());
		assertEquals("dateOfLastEvent default", Long.MIN_VALUE, deser.getDateOfLastEvent());
		assertTrue("slipInLastEvent default should be NaN", Double.isNaN(deser.getSlipInLastEvent()));
		assertNull("tectonicRegionType should be null", deser.getTectonicRegionType());
	}

	@Test
	public void testXmlRoundTripAllTectonicRegionTypes() {
		for (TectonicRegionType trt : TectonicRegionType.values()) {
			FaultSectionPrefData orig = createFullyPopulated();
			orig.setTectonicRegionType(trt);

			FaultSectionPrefData deser = roundTrip(orig);
			assertEquals("TectonicRegionType " + trt.name(), trt, deser.getTectonicRegionType());
		}
	}
}
