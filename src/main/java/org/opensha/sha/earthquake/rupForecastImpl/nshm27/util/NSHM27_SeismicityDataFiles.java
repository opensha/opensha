package org.opensha.sha.earthquake.rupForecastImpl.nshm27.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_InvConfigFactory;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.Direct;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisBValueMethod;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModelEpochCatalog.EpochWeighting;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/** Locates NSHM27 seismicity inputs with their original, data-provider filenames. */
public final class NSHM27_SeismicityDataFiles {

	public static final String RATE_DATASET = "2026_10_19-gamma";
	public static final String PDF_DATASET = "2026_10_10-gamma";

	private static final Pattern ADAPTIVE_SUFFIX = Pattern.compile(".*_adn\\d+\\.csv");

	private NSHM27_SeismicityDataFiles() {}

	public static File locateRateWeights(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod) {
		String prefix = ratePrefix(region, trt);
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix) && name.endsWith("-weights.csv"),
				region.getShortName()+" "+NSHM27_RegionLoader.getNameForTRT(trt)+" epoch weights");
	}

	public static File locateUnionRateWeights(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod) {
		String prefix = unionRatePrefix(region);
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix) && name.endsWith("-weights.csv"),
				region.getShortName()+" union epoch weights");
	}

	public static File locateEpochRatePairs(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod, int epochIndex) {
		Preconditions.checkArgument(epochIndex >= 0, "Epoch index must be nonnegative: %s", epochIndex);
		String prefix = ratePrefix(region, trt);
		String suffix = "-epoch-"+(epochIndex+1)+"-pairs.csv";
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix) && name.endsWith(suffix),
				region.getShortName()+" "+NSHM27_RegionLoader.getNameForTRT(trt)
				+" epoch "+(epochIndex+1)+" rate pairs");
	}

	public static File locateUnionEpochRatePairs(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod, int epochIndex) {
		Preconditions.checkArgument(epochIndex >= 0, "Epoch index must be nonnegative: %s", epochIndex);
		String prefix = unionRatePrefix(region);
		String suffix = "-epoch-"+(epochIndex+1)+"-pairs.csv";
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix) && name.endsWith(suffix),
				region.getShortName()+" union epoch "+(epochIndex+1)+" rate pairs");
	}

	public static File locateMixtureRatePairs(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) {
		return locateMixtureRateFile(region, trt, bMethod, weighting, "-pairs.csv", "rate pairs");
	}

	public static File locateMixtureRateBranches(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) {
		return locateMixtureRateFile(region, trt, bMethod, weighting, "-branches.csv", "rate branches");
	}

	public static File locateUnionMixtureRateBranches(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) {
		String prefix = "comb-"+region.getTitleCaseAcronym()+"-Union-";
		String suffix = ("-mixture-"+weighting.name()+"-branches.csv").toLowerCase(Locale.US);
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix)
				&& name.toLowerCase(Locale.US).endsWith(suffix),
				region.getShortName()+" union "+weighting.name()+" mixture rate branches");
	}

	private static File locateMixtureRateFile(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting, String suffix, String description) {
		String prefix = ratePrefix(region, trt);
		String mixtureSuffix = ("-mixture-"+weighting.name()+suffix).toLowerCase(Locale.US);
		return findUnique(rateDirectory(region, bMethod), name -> name.startsWith(prefix)
				&& name.toLowerCase(Locale.US).endsWith(mixtureSuffix),
				region.getShortName()+" "+NSHM27_RegionLoader.getNameForTRT(trt)+" "
				+weighting.name()+" mixture "+description);
	}

	public static File locateDirectRates(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		String prefix = region.getTitleCaseAcronym()+"-"+rateRegimeLabel(trt)+"-";
		return findUnique(rateDirectory(region, NSHM27_SeisBValueMethod.CLASSIFICATION_DEPENDENT),
				name -> name.startsWith(prefix) && name.endsWith("-directrates.csv"),
				region.getShortName()+" "+NSHM27_RegionLoader.getNameForTRT(trt)+" direct rates");
	}

	public static File locateUnionDirectRates(NSHM27_SeismicityRegions region) {
		String prefix = region.getTitleCaseAcronym()+"-Union-";
		return findUnique(rateDirectory(region, NSHM27_SeisBValueMethod.CLASSIFICATION_DEPENDENT),
				name -> name.startsWith(prefix) && name.endsWith("-directrates.csv"),
				region.getShortName()+" union direct rates");
	}

	public static List<Direct> loadDirectRates(NSHM27_SeismicityRegions region,
			TectonicRegionType trt) throws IOException {
		return loadDirectRates(locateDirectRates(region, trt));
	}

	public static List<Direct> loadUnionDirectRates(NSHM27_SeismicityRegions region) throws IOException {
		return loadDirectRates(locateUnionDirectRates(region));
	}

	private static List<Direct> loadDirectRates(File file) throws IOException {
		return SeismicityRateFileLoader.loadDirectBranches(CSVFile.readFile(file, false));
	}

	public static File locateSpatialPDF(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			NSHM27_DeclusteringAlgorithms declustering, NSHM27_SeisSmoothingAlgorithms smoothing) {
		Preconditions.checkArgument(declustering != NSHM27_DeclusteringAlgorithms.AVERAGE,
				"AVERAGE does not identify an individual declustering PDF");
		Preconditions.checkArgument(smoothing != NSHM27_SeisSmoothingAlgorithms.AVERAGE,
				"AVERAGE does not identify an individual smoothing PDF");

		String regionPrefix = "apdf_"+region.name().toLowerCase(Locale.US)+"_";
		String regimeToken = "_"+pdfRegimeLabel(trt).toLowerCase(Locale.US)+"_";
		String declusteringToken = switch (declustering) {
		case GK -> "_gk_";
		case NN -> "_nn_mean_";
		case REAS -> "_r85_";
		default -> throw new IllegalArgumentException("Unsupported declustering algorithm: "+declustering);
		};

		return findUnique(pdfDirectory(region), name -> {
			String lower = name.toLowerCase(Locale.US);
			if (!lower.startsWith(regionPrefix) || !lower.contains(regimeToken)
					|| !lower.contains(declusteringToken) || !lower.endsWith(".csv"))
				return false;
			boolean smoothingMatches = switch (smoothing) {
			case ADAPTIVE -> ADAPTIVE_SUFFIX.matcher(lower).matches();
			case FIXED -> lower.endsWith("_fixed.csv");
			default -> false;
			};
			if (!smoothingMatches)
				return false;
			boolean is3D = lower.contains("_3d_");
			return trt == TectonicRegionType.SUBDUCTION_SLAB ? is3D : !is3D;
		}, region.getShortName()+" "+NSHM27_RegionLoader.getNameForTRT(trt)+" "
				+declustering.name()+" "+smoothing.name()+" spatial PDF");
	}

	private static File rateDirectory(NSHM27_SeismicityRegions region, NSHM27_SeisBValueMethod bMethod) {
		return datasetDirectory("seis_rate_samples", region, rateDatasetDirectoryName(bMethod));
	}

	public static String rateDatasetDirectoryName(NSHM27_SeisBValueMethod bMethod) {
		return RATE_DATASET+"-"+bMethod.getDatasetSuffix();
	}

	private static File pdfDirectory(NSHM27_SeismicityRegions region) {
		return datasetDirectory("spatial_seis_pdfs", region, PDF_DATASET);
	}

	private static File datasetDirectory(String type, NSHM27_SeismicityRegions region, String dataset) {
		File directory = new File(NSHM27_InvConfigFactory.locateDataDirectory(),
				type+"/"+region.name().toLowerCase(Locale.US)+"/"+dataset);
		Preconditions.checkState(directory.isDirectory(), "Dataset directory does not exist: %s", directory);
		return directory;
	}

	private static String ratePrefix(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		return "comb-"+region.getTitleCaseAcronym()+"-"+rateRegimeLabel(trt)+"-";
	}

	private static String unionRatePrefix(NSHM27_SeismicityRegions region) {
		return "comb-"+region.getTitleCaseAcronym()+"-Union-";
	}

	private static String rateRegimeLabel(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW -> "Crustal";
		case SUBDUCTION_INTERFACE -> "Interface";
		case SUBDUCTION_SLAB -> "Downgoing";
		default -> throw new IllegalArgumentException("Unsupported tectonic regime: "+trt);
		};
	}

	private static String pdfRegimeLabel(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW -> "Crustal";
		case SUBDUCTION_INTERFACE -> "Interface";
		case SUBDUCTION_SLAB -> "Intraslab";
		default -> throw new IllegalArgumentException("Unsupported tectonic regime: "+trt);
		};
	}

	private static File findUnique(File directory, Predicate<String> matcher, String description) {
		File[] matches = directory.listFiles(file -> file.isFile() && matcher.test(file.getName()));
		Preconditions.checkState(matches != null, "Could not list dataset directory: %s", directory);
		Arrays.sort(matches);
		Preconditions.checkState(matches.length == 1,
				"Expected exactly one %s in %s, found %s: %s", description, directory,
				matches.length, fileNames(matches));
		return matches[0];
	}

	private static List<String> fileNames(File[] files) {
		List<String> names = new ArrayList<>(files.length);
		for (File file : files)
			names.add(file.getName());
		return names;
	}

	/**
	 * Copies the default SQRTN mixture branch files into their canonical resource locations.
	 * Run from the repository root. Existing files are accepted only when byte-for-byte identical.
	 */
	public static void main(String[] args) throws IOException {
		Preconditions.checkArgument(args.length == 0, "This utility takes no arguments");
		File resourceRoot = new File("src/main/resources/data/erf/nshm27");
		Preconditions.checkState(resourceRoot.isDirectory(),
				"Run from the OpenSHA repository root; resource directory not found: %s",
				resourceRoot.getAbsolutePath());
		TectonicRegionType[] trts = { TectonicRegionType.ACTIVE_SHALLOW,
				TectonicRegionType.SUBDUCTION_INTERFACE, TectonicRegionType.SUBDUCTION_SLAB };
		for (NSHM27_SeismicityRegions region : NSHM27_SeismicityRegions.values()) {
			for (NSHM27_SeisBValueMethod bMethod : NSHM27_SeisBValueMethod.values()) {
				File destinationDir = new File(resourceRoot, region.name().toLowerCase(Locale.US)
						+"/seismicity/rates/"+rateDatasetDirectoryName(bMethod));
				Files.createDirectories(destinationDir.toPath());
				for (TectonicRegionType trt : trts) {
					File source = locateMixtureRateBranches(region, trt, bMethod, EpochWeighting.SQRTN);
					File destination = new File(destinationDir,
							NSHM27_RegionLoader.getNameForTRT(trt).toUpperCase(Locale.US)+"_MIXTURE_SQRTN.csv");
					copyResource(source, destination);
				}
				File unionSource = locateUnionMixtureRateBranches(region, bMethod, EpochWeighting.SQRTN);
				copyResource(unionSource, new File(destinationDir, "UNION_MIXTURE_SQRTN.csv"));
			}
		}
	}

	private static void copyResource(File source, File destination) throws IOException {
		if (destination.exists()) {
			Preconditions.checkState(Arrays.equals(Files.readAllBytes(source.toPath()),
					Files.readAllBytes(destination.toPath())),
					"Refusing to overwrite a different resource file: %s", destination);
			System.out.println("Already current: "+destination);
		} else {
			Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
			System.out.println("Copied "+source+" -> "+destination);
		}
	}
}
