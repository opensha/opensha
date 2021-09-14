package scratch.UCERF3.utils;

import static org.junit.Assert.*;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class MFD_InversionConstraintAdapterTest {

    static MFD_InversionConstraint.Adapter adapter = new MFD_InversionConstraint.Adapter();

    @Test
    public void readWriteTest() throws IOException {
        IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(0, 10, 0.5);
        Region region = new Region(new Location(5, 5), 10);
        MFD_InversionConstraint expected = new MFD_InversionConstraint(mfd, region);

        MFD_InversionConstraint actual = writeAndReadJSON(expected);

        assertEquals(expected.getClass(), actual.getClass());
        assertFuncEquals(mfd, actual.getMagFreqDist());
        assertReqionEquals(region, actual.getRegion());
    }

    @Test
    public void weightedReadWriteTest() throws IOException {
        IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(0, 10, 0.5);
        Region region = new Region(new Location(5, 5), 10);
        EvenlyDiscretizedFunc weights = new EvenlyDiscretizedFunc(1, 8, 0.4);
        MFD_WeightedInversionConstraint expected = new MFD_WeightedInversionConstraint(mfd, region, weights);

        MFD_WeightedInversionConstraint actual = (MFD_WeightedInversionConstraint) writeAndReadJSON(expected);

        assertEquals(expected.getClass(), actual.getClass());
        assertFuncEquals(mfd, actual.getMagFreqDist());
        assertReqionEquals(region, actual.getRegion());
        assertFuncEquals(weights, actual.getWeights());
    }

    /**
     * Asserts that two Region instances are equal after writing to and reading from JSON.
     *
     * @param expected the Region that was written to JSON
     * @param actual   the Region that was read from JSON
     */
    public void assertReqionEquals(Region expected, Region actual) {
        assertEquals(
                ((Geometry.Polygon) expected.toFeature().geometry).polygon,
                ((Geometry.Polygon) actual.toFeature().geometry).polygon);
    }

    public void assertFuncEquals(EvenlyDiscretizedFunc expected, EvenlyDiscretizedFunc actual) {
        assertEquals(expected.xValues(), actual.xValues());
        assertEquals(expected.yValues(), actual.yValues());
    }

    public MFD_InversionConstraint writeAndReadJSON(MFD_InversionConstraint constraint) throws IOException {
        StringWriter writer = new StringWriter();
        adapter.write(new JsonWriter(writer), constraint);
        return adapter.read(new JsonReader(new StringReader(writer.toString())));
    }

}
