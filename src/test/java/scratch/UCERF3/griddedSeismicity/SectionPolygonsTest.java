package scratch.UCERF3.griddedSeismicity;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

import java.awt.geom.Area;

public class SectionPolygonsTest {

    @Test
    public void testBuildBufferPoly() {
        // a fault that turns back on itself
        LocationList trace = new LocationList();
        trace.add(new Location(-43.5116, 171.398));
        trace.add(new Location(-43.5343, 171.3843));
        trace.add(new Location(-43.5527, 171.3412));

        Area actual = SectionPolygons.buildBufferPoly(trace, 220, 12);

        assertTrue(containsAll(actual, trace));
    }

    public static boolean containsAll(Area area, LocationList locations) {
        for (Location location : locations) {
            if (!containsWithTolerance(area, location, 0.0000000001)) {
               return false;
            }
        }
        return true;
    }

    public static boolean containsWithTolerance(Area area, Location location, double tolerance) {
        for (int lat = -1; lat <= 1; lat++) {
            for (int lon = -1; lon <= 1; lon++) {
                if (area.contains(
                        location.getLongitude() + lon * tolerance,
                        location.getLatitude() + lat * tolerance)) {
                    return true;
                }
            }
        }
        return false;
    }
}
