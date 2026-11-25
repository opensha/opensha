package org.opensha.commons.mapping.gmt;

import java.awt.geom.Point2D;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.PSText;
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
		validateSettings(map.getGMT_Params());

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
		requirePositive("Map grid spacing", map.getGriddedDataInc());
		requirePositive("Map image width", map.getImageWidth());
		if (map.getDpi() < 0 || !Double.isFinite(map.getContourIncrement()))
			throw new GMT_MapException("Invalid map resolution or contour interval");
		if (map.getCPTCustomInterval() != null)
			requirePositive("Colorbar tick interval", map.getCPTCustomInterval());
		Double scaleMin = map.getCustomScaleMin();
		Double scaleMax = map.getCustomScaleMax();
		if ((scaleMin != null && !Double.isFinite(scaleMin)) || (scaleMax != null && !Double.isFinite(scaleMax))
				|| (scaleMin != null && scaleMax != null && scaleMin >= scaleMax))
			throw new GMT_MapException("Invalid color scale limits");

		GMT_InterpolationSettings interpolation = map.getInterpSettings();
		if (interpolation != null) {
			requirePositive("Interpolation spacing", interpolation.getInterpSpacing());
			if (!Double.isFinite(interpolation.getConvergenceLimit())
					|| !Double.isFinite(interpolation.getSearchRadius())
					|| !validTension(interpolation.getInteriorTension())
					|| !validTension(interpolation.getExteriorTension()))
				throw new GMT_MapException("Invalid GMT interpolation settings");
		}
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
