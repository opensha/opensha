package org.opensha.commons.mapping.gmt;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.PSXYPolygon;
import org.opensha.commons.mapping.gmt.elements.PSXYSymbolSet;
import org.opensha.commons.mapping.gmt.elements.PSText;
import org.opensha.commons.mapping.gmt.elements.TopographicSlopeFile;
import org.opensha.commons.util.ServletWorkLimits;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.cybershake.maps.GMT_InterpolationSettings;

/** Validates HTTP map options before they reach the GMT renderer. */
public final class GMT_RenderArguments {

	private static final int MAX_PLOT_TEXT_LENGTH = 1024;
	private static final Set<String> FRAME_TYPES = Set.of("plain", "fancy", "fancy+", "inside", "graph",
			"graph-origin");
	private static final Pattern DISTANCE = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[cip]?");
	private static final Pattern TEXT_CONTROL = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");
	private static final Pattern LATEX = Pattern.compile("@\\[|<\\s*/?\\s*math\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern TEXT_FONT = Pattern.compile(
			"-F\\+f[1-9][0-9]*p,Helvetica-Bold,[0-9]{1,3}/[0-9]{1,3}/[0-9]{1,3}(?:\\+j(?:L[TB]?|R[TB]?))?");

	private GMT_RenderArguments() {}

	public static void validateRemoteMap(GMT_Map map) throws GMT_MapException {
		validateRemoteMaps(map);
	}

	/** Applies the per-map checks and counts requested grid work across a batch. */
	public static void validateRemoteMaps(GMT_Map... maps) throws GMT_MapException {
		long gridCells = 0;
		for (GMT_Map map : maps)
			gridCells += validateMap(map);
		validateGridWork(gridCells);
	}

	private static long validateMap(GMT_Map map) throws GMT_MapException {
		validateSettings(map.getGMT_Params());
		validatePointCounts(map);

		if (map.getCustomLabel() != null)
			validatePlotText("Colorbar label", map.getCustomLabel());
		if (map.getText() != null) {
			for (PSText annotation : map.getText()) {
				if (annotation == null)
					throw new GMT_MapException("Missing plot annotation");
				validatePlotText("Plot annotation", annotation.getText());
				Point2D point = annotation.getPoint();
				if (point == null || !Double.isFinite(point.getX()) || !Double.isFinite(point.getY()))
					throw new GMT_MapException("Invalid plot annotation coordinates");
				try {
					if (!TEXT_FONT.matcher(annotation.getFontArg()).matches())
						throw new GMT_MapException("Invalid plot annotation font, color, or justification");
				} catch (NullPointerException e) {
					throw new GMT_MapException("Missing plot annotation font, color, or justification", e);
				}
			}
		}

		Region region = map.getRegion();
		if (region == null || !validExtent(region.getMinLat(), region.getMaxLat())
				|| !validExtent(region.getMinLon(), region.getMaxLon()))
			throw new GMT_MapException("Invalid map region");
		requirePositive("Map image width", map.getImageWidth());
		if (map.getDpi() < 0 || !Double.isFinite(map.getContourIncrement()))
			throw new GMT_MapException("Invalid map resolution or contour interval");
		validateImageSize(map);
		if (map.getCPTCustomInterval() != null)
			requirePositive("Colorbar tick interval", map.getCPTCustomInterval());
		Double scaleMin = map.getCustomScaleMin();
		Double scaleMax = map.getCustomScaleMax();
		if ((scaleMin != null && !Double.isFinite(scaleMin)) || (scaleMax != null && !Double.isFinite(scaleMax))
				|| (scaleMin != null && scaleMax != null && scaleMin >= scaleMax))
			throw new GMT_MapException("Invalid color scale limits");

		long gridCells = estimateGridCells(region, map.getGriddedDataInc(), "Map");
		TopographicSlopeFile topo = map.getTopoResolution();
		if (topo != null) {
			double spacing = map.getGriddedDataInc();
			// GMT rounds the map extent to its base-grid spacing before topographic resampling.
			double longitudeSpan = roundedOutward(region.getMaxLon() - region.getMinLon(), spacing);
			double latitudeSpan = roundedOutward(region.getMaxLat() - region.getMinLat(), spacing);
			gridCells += estimateGridCells(longitudeSpan, latitudeSpan, topo.resolution() / 3600d, "Topographic");
		}
		GMT_InterpolationSettings interpolation = map.getInterpSettings();
		if (interpolation != null) {
			// The standard renderer uses the base spacing, and CyberShake uses the interpolation spacing.
			double spacing = Math.min(map.getGriddedDataInc(), interpolation.getInterpSpacing());
			long cells = estimateGridCells(region, spacing, "Interpolation");
			if (cells > ServletWorkLimits.INTERPOLATION_CELLS)
				throw new GMT_MapException("Interpolation grid exceeds the limit of "
						+ ServletWorkLimits.INTERPOLATION_CELLS + " cells. Increase spacing or select a smaller region.");
			gridCells += cells;
			if (!Double.isFinite(interpolation.getConvergenceLimit())
					|| !Double.isFinite(interpolation.getSearchRadius())
					|| !validTension(interpolation.getInteriorTension())
					|| !validTension(interpolation.getExteriorTension()))
				throw new GMT_MapException("Invalid GMT interpolation settings");
		}
		return gridCells;
	}

	/** Bounds the estimated size of one grid, including nodes along its edges. */
	public static long estimateGridCells(Region region, double spacing, String label) throws GMT_MapException {
		return estimateGridCells(region.getMaxLon() - region.getMinLon(), region.getMaxLat() - region.getMinLat(),
				spacing, label);
	}

	private static long estimateGridCells(double longitudeSpan, double latitudeSpan, double spacing, String label)
			throws GMT_MapException {
		requirePositive(label + " grid spacing", spacing);
		double width = Math.ceil(longitudeSpan / spacing) + 1;
		double height = Math.ceil(latitudeSpan / spacing) + 1;
		double cells = width * height;
		if (!(cells <= ServletWorkLimits.GRID_CELLS))
			throw new GMT_MapException(label + " grid exceeds the limit of " + ServletWorkLimits.GRID_CELLS
					+ " cells. Increase spacing or select a smaller region.");
		return (long) cells;
	}

	private static double roundedOutward(double span, double spacing) {
		return Math.max(span, Math.rint(span / spacing) * spacing);
	}

	public static void validateGridWork(long gridCells) throws GMT_MapException {
		if (gridCells > ServletWorkLimits.TOTAL_GRID_CELLS)
			throw new GMT_MapException("Total render work exceeds the limit of "
					+ ServletWorkLimits.TOTAL_GRID_CELLS + " estimated grid cells per request");
	}

	private static void validateImageSize(GMT_Map map) throws GMT_MapException {
		// Include the letter-size intermediate as well as any image resized to the requested width.
		double dpi = Math.max(map.getDpi(), 72);
		double width = Math.max(8.5, map.getImageWidth());
		validateImageSize(width, width * (11d / 8.5), dpi);
	}

	/** Checks a renderer's estimated raster dimensions before it builds the script. */
	public static void validateImageSize(double widthInches, double heightInches, double dpi)
			throws GMT_MapException {
		double pixels = Math.ceil(widthInches * dpi) * Math.ceil(heightInches * dpi);
		if (!(pixels <= ServletWorkLimits.IMAGE_PIXELS))
			throw new GMT_MapException("Map image exceeds the limit of " + ServletWorkLimits.IMAGE_PIXELS
					+ " estimated pixels. Reduce the DPI or image width.");
	}

	private static void validatePointCounts(GMT_Map map) throws GMT_MapException {
		if (map.getGriddedData() != null && map.getGriddedData().size() > ServletWorkLimits.MAP_POINTS)
			throw new GMT_MapException("Map data exceeds the limit of " + ServletWorkLimits.MAP_POINTS + " points");
		validatePalette(map.getCpt());
		PSXYSymbolSet symbolSet = map.getSymbolSet();
		long count = (long) size(map.getText()) + size(map.getSymbols()) + size(map.getPolys());
		if (symbolSet != null) {
			count += size(symbolSet.getSymbols());
			validatePalette(symbolSet.getCpt());
		}
		checkOverlayCount(count);
		if (map.getPolys() != null) {
			for (PSXYPolygon polygon : map.getPolys()) {
				if (polygon == null || polygon.getPoints() == null)
					throw new GMT_MapException("Missing map polygon");
				count += polygon.size();
				checkOverlayCount(count);
			}
		}
	}

	private static int size(List<?> list) {
		return list == null ? 0 : list.size();
	}

	private static void validatePalette(CPT palette) throws GMT_MapException {
		if (palette != null && palette.size() > ServletWorkLimits.PALETTE_ENTRIES)
			throw new GMT_MapException("Map palette exceeds the limit of " + ServletWorkLimits.PALETTE_ENTRIES
					+ " entries");
	}

	private static void checkOverlayCount(long count) throws GMT_MapException {
		if (count > ServletWorkLimits.OVERLAY_POINTS)
			throw new GMT_MapException("Map overlays exceed the limit of " + ServletWorkLimits.OVERLAY_POINTS
					+ " elements and vertices");
	}

	private static void validateSettings(Map<String, String> settings) throws GMT_MapException {
		if (settings == null)
			return;
		for (Map.Entry<String, String> setting : settings.entrySet()) {
			String name = setting.getKey();
			String value = setting.getValue();
			if (name == null || value == null || value.length() > 80)
				throw new GMT_MapException("Invalid custom GMT setting");
			boolean valid;
			switch (name) {
			case "MAP_FRAME_TYPE":
				valid = FRAME_TYPES.contains(value.toLowerCase(Locale.ROOT));
				break;
			case "MAP_FRAME_WIDTH":
				valid = value.equals("auto") || validDistance(value, true);
				break;
			case "MAP_TICK_LENGTH_PRIMARY":
				String[] lengths = value.split("/", -1);
				valid = value.equals("auto") || (lengths.length <= 2 && validDistance(lengths[0], false)
						&& (lengths.length == 1 || validDistance(lengths[1], false)));
				break;
			default:
				throw new GMT_MapException("Unsupported custom GMT setting");
			}
			if (!valid)
				throw new GMT_MapException("Invalid value for " + name);
		}
	}

	private static boolean validDistance(String value, boolean positive) {
		if (!DISTANCE.matcher(value).matches())
			return false;
		char last = value.charAt(value.length() - 1);
		double distance = Double.parseDouble(Character.isLetter(last)
				? value.substring(0, value.length() - 1) : value);
		return Double.isFinite(distance) && (!positive || distance > 0);
	}

	private static void validatePlotText(String role, String text) throws GMT_MapException {
		if (text == null || text.length() > MAX_PLOT_TEXT_LENGTH || TEXT_CONTROL.matcher(text).find())
			throw new GMT_MapException(
					role + " must be a single line of at most " + MAX_PLOT_TEXT_LENGTH + " characters");
		if (LATEX.matcher(text).find())
			throw new GMT_MapException("LaTeX is disabled in remote GMT plot text");
	}

	private static void requirePositive(String role, double value) throws GMT_MapException {
		if (!Double.isFinite(value) || value <= 0)
			throw new GMT_MapException(role + " must be finite and positive");
	}

	private static boolean validTension(double tension) {
		return tension >= 0 && tension <= 1;
	}

	private static boolean validExtent(double min, double max) {
		return Double.isFinite(min) && Double.isFinite(max) && min < max;
	}
}
