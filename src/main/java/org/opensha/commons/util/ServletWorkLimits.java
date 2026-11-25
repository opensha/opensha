package org.opensha.commons.util;

/** Limits on server work after a request passes the serialized input checks. */
public final class ServletWorkLimits {

	private static final String PREFIX = "opensha.servlet.";

	public static final int SITE_LOCATIONS = positive("maxSiteLocations", 100_000);
	public static final int DISAGG_BINS = positive("maxDisaggregationBins", 50_000);
	public static final int GRID_CELLS = positive("maxRenderGridCells", 100_000_000);
	public static final int INTERPOLATION_CELLS = positive("maxInterpolationGridCells", 10_000_000);
	public static final int TOTAL_GRID_CELLS = positive("maxTotalRenderGridCells", 500_000_000);
	public static final int IMAGE_PIXELS = positive("maxRenderImagePixels", 64_000_000);
	public static final int MAP_POINTS = positive("maxRenderDataPoints", 2_000_000);
	public static final int OVERLAY_POINTS = positive("maxRenderOverlayPoints", 100_000);
	public static final int PALETTE_ENTRIES = positive("maxRenderPaletteEntries", 4096);
	public static final int CYBERSHAKE_COMPARISONS = positive("maxCyberShakeComparisons", 500_000_000);
	public static final int CONCURRENT_RENDERS = positive("maxConcurrentRenders", 5);
	public static final int QUEUED_RENDERS = positive("maxQueuedRenders", 10);
	public static final int RENDER_WAIT_SECONDS = positive("maxRenderWaitSeconds", 300);
	public static final int SCRIPT_SECONDS = positive("maxScriptSeconds", 900);

	private ServletWorkLimits() {}

	private static int positive(String property, int defaultValue) {
		String name = PREFIX + property;
		int value = Integer.getInteger(name, defaultValue);
		if (value <= 0)
			throw new IllegalArgumentException(name + " must be positive");
		return value;
	}
}
