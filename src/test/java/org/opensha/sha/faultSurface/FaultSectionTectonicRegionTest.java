package org.opensha.sha.faultSurface;

import static org.junit.Assert.*;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests for the TectonicRegionType property on FaultSection implementations.
 */
public class FaultSectionTectonicRegionTest {

    /** Creates a minimal FaultSectionPrefData for testing. */
    protected static FaultSectionPrefData buildPrefData(TectonicRegionType trt) {
        FaultSectionPrefData data = new FaultSectionPrefData();
        data.setSectionId(1);
        data.setSectionName("Test Fault");
        data.setAveDip(45.0);
        data.setAveRake(90.0);
        data.setAveUpperDepth(0.0);
        data.setAveLowerDepth(10.0);
        FaultTrace trace = new FaultTrace("test");
        trace.add(new Location(-41.0, 174.0));
        trace.add(new Location(-42.0, 175.0));
        data.setFaultTrace(trace);
        data.setDipDirection(264f);
        if (trt != null)
            data.setTectonicRegionType(trt);
        return data;
    }

    /** Creates a minimal GeoJSONFaultSection for testing. */
    protected static GeoJSONFaultSection buildGeoJSON(TectonicRegionType trt) {
        FaultTrace trace = new FaultTrace("test");
        trace.add(new Location(-41.0, 174.0));
        trace.add(new Location(-42.0, 175.0));
        GeoJSONFaultSection.Builder builder =
                new GeoJSONFaultSection.Builder(1, "Test Fault", trace)
                        .dip(45.0)
                        .rake(90.0)
                        .upperDepth(0.0)
                        .lowerDepth(10.0);
        if (trt != null)
            builder.tectonicRegion(trt);
        return builder.build();
    }

    // ---- FaultSectionPrefData ----

    @Test
    public void prefData_defaultIsNull() {
        FaultSectionPrefData data = buildPrefData(null);
        assertNull(data.getTectonicRegionType());
    }

    @Test
    public void prefData_getterSetterRoundTrip() {
        FaultSectionPrefData data = buildPrefData(null);
        data.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, data.getTectonicRegionType());
    }

    @Test
    public void prefData_clone_propagatesTRT() {
        FaultSectionPrefData data = buildPrefData(TectonicRegionType.SUBDUCTION_INTERFACE);
        FaultSectionPrefData cloned = data.clone();
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, cloned.getTectonicRegionType());
    }

    @Test
    public void prefData_getSubSections_propagatesTRT() {
        FaultSectionPrefData data = buildPrefData(TectonicRegionType.SUBDUCTION_SLAB);
        List<FaultSectionPrefData> subs = data.getSubSectionsList(50.0, 100);
        assertFalse(subs.isEmpty());
        for (FaultSectionPrefData sub : subs) {
            assertEquals(TectonicRegionType.SUBDUCTION_SLAB, sub.getTectonicRegionType());
        }
    }

    @Test
    public void prefData_xml_roundTrip_withTRT() {
        FaultSectionPrefData data = buildPrefData(TectonicRegionType.ACTIVE_SHALLOW);
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("root");
        data.toXMLMetadata(root);
        Element el = root.element(FaultSectionPrefData.XML_METADATA_NAME);
        FaultSectionPrefData restored = FaultSectionPrefData.fromXMLMetadata(el);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, restored.getTectonicRegionType());
    }

    @Test
    public void prefData_xml_roundTrip_withoutTRT() {
        FaultSectionPrefData data = buildPrefData(null);
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("root");
        data.toXMLMetadata(root);
        Element el = root.element(FaultSectionPrefData.XML_METADATA_NAME);
        FaultSectionPrefData restored = FaultSectionPrefData.fromXMLMetadata(el);
        assertNull(restored.getTectonicRegionType());
    }

    // ---- GeoJSONFaultSection ----

    @Test
    public void geoJSON_defaultIsNull() {
        GeoJSONFaultSection sect = buildGeoJSON(null);
        assertNull(sect.getTectonicRegionType());
    }

    @Test
    public void geoJSON_getterSetterRoundTrip() {
        GeoJSONFaultSection sect = buildGeoJSON(null);
        sect.setTectonicRegionType(TectonicRegionType.VOLCANIC);
        assertEquals(TectonicRegionType.VOLCANIC, sect.getTectonicRegionType());
    }

    @Test
    public void geoJSON_builderSetsProperty() {
        GeoJSONFaultSection sect = buildGeoJSON(TectonicRegionType.STABLE_SHALLOW);
        assertEquals(TectonicRegionType.STABLE_SHALLOW, sect.getTectonicRegionType());
    }

    @Test
    public void geoJSON_featureRoundTrip_withTRT() {
        GeoJSONFaultSection original = buildGeoJSON(TectonicRegionType.SUBDUCTION_INTERFACE);
        Feature feature = original.toFeature();
        GeoJSONFaultSection restored = GeoJSONFaultSection.fromFeature(feature);
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, restored.getTectonicRegionType());
    }

    @Test
    public void geoJSON_featureRoundTrip_withoutTRT() {
        GeoJSONFaultSection original = buildGeoJSON(null);
        Feature feature = original.toFeature();
        GeoJSONFaultSection restored = GeoJSONFaultSection.fromFeature(feature);
        assertNull(restored.getTectonicRegionType());
    }

    @Test
    public void geoJSON_setNull_removesTRT() {
        GeoJSONFaultSection sect = buildGeoJSON(TectonicRegionType.ACTIVE_SHALLOW);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, sect.getTectonicRegionType());
        sect.setTectonicRegionType(null);
        assertNull(sect.getTectonicRegionType());
    }

    @Test
    public void geoJSON_clone_propagatesTRT() {
        GeoJSONFaultSection sect = buildGeoJSON(TectonicRegionType.ACTIVE_SHALLOW);
        GeoJSONFaultSection cloned = (GeoJSONFaultSection) sect.clone();
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, cloned.getTectonicRegionType());
    }

    // ---- Cross-implementation copy ----

    @Test
    public void geoJSON_fromPrefData_copiesTRT() {
        FaultSectionPrefData data = buildPrefData(TectonicRegionType.SUBDUCTION_INTERFACE);
        GeoJSONFaultSection sect = GeoJSONFaultSection.fromFaultSection(data);
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, sect.getTectonicRegionType());
    }

    @Test
    public void geoJSON_fromPrefData_nullTRT() {
        FaultSectionPrefData data = buildPrefData(null);
        GeoJSONFaultSection sect = GeoJSONFaultSection.fromFaultSection(data);
        assertNull(sect.getTectonicRegionType());
    }

    // ---- FaultSection interface default ----

    @Test
    public void interfaceDefault_returnsNull() {
        FaultSection anon = new MinimalFaultSection();
        assertNull(anon.getTectonicRegionType());
        // setter is no-op by default
        anon.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
        assertNull(anon.getTectonicRegionType());
    }

    /**
     * Minimal FaultSection implementation to test interface defaults.
     */
    private static class MinimalFaultSection implements FaultSection {
        public long getDateOfLastEvent() { return Long.MIN_VALUE; }
        public void setDateOfLastEvent(long d) {}
        public void setSlipInLastEvent(double s) {}
        public double getSlipInLastEvent() { return Double.NaN; }
        public double getAseismicSlipFactor() { return 0; }
        public void setAseismicSlipFactor(double a) {}
        public double getCouplingCoeff() { return 1; }
        public void setCouplingCoeff(double c) {}
        public double getAveDip() { return 45; }
        public double getOrigAveSlipRate() { return 0; }
        public void setAveSlipRate(double r) {}
        public double getAveLowerDepth() { return 10; }
        public double getAveRake() { return 90; }
        public void setAveRake(double r) {}
        public double getOrigAveUpperDepth() { return 0; }
        public float getDipDirection() { return 0; }
        public FaultTrace getFaultTrace() { return null; }
        public int getSectionId() { return 0; }
        public void setSectionId(int i) {}
        public void setSectionName(String s) {}
        public int getParentSectionId() { return -1; }
        public void setParentSectionId(int i) {}
        public String getParentSectionName() { return null; }
        public void setParentSectionName(String s) {}
        public List<? extends FaultSection> getSubSectionsList(double m, int s, int min) { return null; }
        public double getOrigSlipRateStdDev() { return 0; }
        public void setSlipRateStdDev(double s) {}
        public boolean isConnector() { return false; }
        public boolean isProxyFault() { return false; }
        public org.opensha.commons.geo.Region getZonePolygon() { return null; }
        public void setZonePolygon(org.opensha.commons.geo.Region r) {}
        public Element toXMLMetadata(Element root, String name) { return null; }
        public Element toXMLMetadata(Element root) { return null; }
        public RuptureSurface getFaultSurface(double g) { return null; }
        public RuptureSurface getFaultSurface(double g, boolean p, boolean a) { return null; }
        public FaultSection clone() { return null; }
        public String getName() { return "test"; }
    }
}
