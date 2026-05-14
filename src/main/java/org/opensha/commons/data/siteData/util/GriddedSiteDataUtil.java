package org.opensha.commons.data.siteData.util;

import gov.usgs.earthquake.nshmp.Maths;
import org.opensha.commons.geo.Location;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility methods for working with gridded site data
 */
public class GriddedSiteDataUtil {
    /* Snap location to closest multiple of spacing. */
    public static Location snapToGrid(Location loc, double spacing) {
        int scale = BigDecimal.valueOf(spacing).scale();
        return snapToGrid(loc, spacing, scale);
    }

    /* Snap location to closest multiple of spacing. */
    public static Location snapToGrid(Location loc, double spacing, int scale) {
        return new Location(
                snapToGrid(loc.getLatitude(), spacing, scale),
                snapToGrid(loc.getLongitude(), spacing, scale));
    }

    /*
     * Snap value to closest multiple of spacing.
     *
     * Double precision rounding errors results in grid midpoints rounding both up
     * and down in the initial Math.round() call.
     */
    private static double snapToGrid(double value, double spacing, int scale) {
        return Maths.round(
                Math.round(value / spacing) * spacing,
                scale, RoundingMode.HALF_UP);
    }

}
