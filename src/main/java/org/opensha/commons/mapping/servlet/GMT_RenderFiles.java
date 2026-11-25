package org.opensha.commons.mapping.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;

import com.google.common.html.HtmlEscapers;

/**
 * File policy for HTTP map requests. Custom input paths are relative to the OpenSHA GMT data directory by default.
 * The JVM property {@code opensha.gmt.allowedInputDir} can select another absolute directory or disable custom inputs
 * with an empty value. The server should have read access and only administrators should have write access.
 * Local renderers can use their own paths.
 */
public final class GMT_RenderFiles {

	public static final String MAP_METADATA_FILE = "map_info.html";
	public static final String TEXT_METADATA_FILE = "metadata.txt";
	public static final String SCRIPT_FILE = "gmtScript.txt";
	private static final String INPUT_DIRECTORY_PROPERTY = "opensha.gmt.allowedInputDir";
	private static final String CUSTOM_PALETTE_FILE = "cptFile.cpt";
	private static final Pattern OUTPUT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
	private static final Pattern INPUT_COMPONENT = Pattern.compile("[A-Za-z0-9._ -]+");
	private static final Pattern METADATA_LINE_BREAK = Pattern.compile("<br[ \\t]*+(?:/[ \\t]*+)?>\\R?",
			Pattern.CASE_INSENSITIVE);
	private static final Set<String> RESERVED_NAMES = Set.of(
			MAP_METADATA_FILE, TEXT_METADATA_FILE, SCRIPT_FILE.toLowerCase(Locale.ROOT),
			"gearth.png", "scatter.xyz", "scatter_diffs.xyz", "ratios.xyz",
			"map_data_interpolated.txt", "map_data_interpolated_differences.txt");

	private GMT_RenderFiles() {}

	/** Publishes metadata as text in a server-generated HTML document. */
	public static void writeHtmlMetadata(Path outputDirectory, String metadata) throws IOException {
		// Legacy clients sometimes put a source newline immediately after a <br> tag.
		String text = METADATA_LINE_BREAK.matcher(String.valueOf(metadata)).replaceAll("\n");
		String page = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
				+ "<title>Map details</title>\n</head>\n<body>\n<pre>\n"
				+ HtmlEscapers.htmlEscaper().escape(text) + "</pre>\n</body>\n</html>\n";
		Files.writeString(outputDirectory.resolve(MAP_METADATA_FILE), page, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	public static void validateMap(GMT_Map map) throws GMT_MapException {
		String inputDirectory = System.getProperty(INPUT_DIRECTORY_PROPERTY, GMT_MapGenerator.OPENSHA_GMT_DATA_PATH);
		validateMap(map, inputDirectory, GMT_MapGeneratorServlet.GMT_DATA_DIR.toPath());
	}

	static void validateMap(GMT_Map map, String inputDirectory, Path outputDirectory) throws GMT_MapException {
		if (map == null)
			throw new GMT_MapException("Missing map specification");

		validateOutputName("XYZ", map.getXyzFileName(), false, ".txt", ".xyz");
		validateOutputName("PostScript", map.getPSFileName(), false, ".ps");
		validateOutputName("PDF", map.getPDFFileName(), true, ".pdf");
		validateOutputName("PNG", map.getPNGFileName(), true, ".png");
		validateOutputName("JPEG", map.getJPGFileName(), true, ".jpg", ".jpeg");

		String palette = map.getCptFile();
		if (palette == null) {
			map.setCustomCptFileName(CUSTOM_PALETTE_FILE);
		} else {
			boolean knownPalette = false;
			for (GMT_CPT_Files preset : GMT_CPT_Files.values()) {
				if (preset.getFileName().equals(palette)) {
					knownPalette = true;
					break;
				}
			}
			if (!knownPalette)
				throw new GMT_MapException("Unknown GMT palette");
		}

		if (map.getCustomGRDPath() != null || map.getCustomIntenPath() != null) {
			if (inputDirectory == null || inputDirectory.isBlank())
				throw new GMT_MapException("Custom GMT input files are disabled on this server");
			try {
				Path configuredRoot = Path.of(inputDirectory);
				if (!configuredRoot.isAbsolute())
					throw new GMT_MapException("The GMT input directory must be absolute");
				Path inputRoot = configuredRoot.toRealPath();
				Path outputRoot = outputDirectory.toFile().getCanonicalFile().toPath();
				if (!Files.isDirectory(inputRoot)
						|| inputRoot.startsWith(outputRoot) || outputRoot.startsWith(inputRoot))
					throw new GMT_MapException("The GMT input and output directories must be separate");

				map.setCustomGRDPath(resolveInput(inputRoot, map.getCustomGRDPath(), "grid", ".grd", ".nc"));
				map.setCustomIntenPath(resolveInput(inputRoot, map.getCustomIntenPath(), "intensity",
						".grd", ".nc", ".grad"));
			} catch (IOException | InvalidPathException e) {
				throw new GMT_MapException("Could not resolve the custom GMT input", e);
			}
		}
	}

	private static void validateOutputName(String role, String name, boolean optional, String... extensions)
			throws GMT_MapException {
		if (optional && name == null)
			return;
		if (name == null || !OUTPUT_NAME.matcher(name).matches()
				|| RESERVED_NAMES.contains(name.toLowerCase(Locale.ROOT)) || !hasExtension(name, extensions))
			throw new GMT_MapException("Invalid " + role + " output filename");
	}

	private static String resolveInput(Path root, String name, String role, String... extensions)
			throws IOException, GMT_MapException {
		if (name == null)
			return null;
		Path relative = Path.of(name);
		Path requested = root.resolve(relative).normalize();
		if (relative.isAbsolute() || !requested.startsWith(root))
			throw new GMT_MapException("Invalid custom GMT " + role + " path");

		Path file = requested.toRealPath();
		if (!file.startsWith(root) || !Files.isRegularFile(file) || !hasExtension(file.toString(), extensions))
			throw new GMT_MapException("Invalid custom GMT " + role + " file");
		// GMT interprets some filename punctuation as grid format and variable selectors.
		for (Path component : file) {
			if (!INPUT_COMPONENT.matcher(component.toString()).matches())
				throw new GMT_MapException("Invalid character in custom GMT " + role + " path");
		}
		return file.toString();
	}

	private static boolean hasExtension(String name, String... extensions) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String extension : extensions)
			if (lower.endsWith(extension))
				return true;
		return false;
	}
}
