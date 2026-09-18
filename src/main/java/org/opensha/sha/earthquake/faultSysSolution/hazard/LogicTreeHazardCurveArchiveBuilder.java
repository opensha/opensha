package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;

import com.google.common.base.Preconditions;

/**
 * Builds standalone archives of branch hazard-curve CSV files from an existing logic-tree results directory.
 */
public class LogicTreeHazardCurveArchiveBuilder {

	private static final String HAZARD_DIR_PREFIX = "hazard_";
	private static final String CURVES_PREFIX = "curves_";
	private static final String FORCE_ARG = "--force";

	public static void main(String[] args) throws IOException {
		List<String> positional = new ArrayList<>();
		boolean force = false;
		for (String arg : args) {
			if (arg.equals(FORCE_ARG))
				force = true;
			else
				positional.add(arg);
		}
		if (positional.isEmpty() || positional.size() > 3) {
			System.err.println("USAGE: <results-dir> [<hazard-subdir-name> [<output-file>]] [--force]");
			System.err.println("If no hazard subdirectory is supplied, all discovered configurations are archived.");
			System.exit(2);
		}

		File resultsDir = new File(positional.get(0));
		Preconditions.checkArgument(resultsDir.isDirectory(), "Results directory doesn't exist: %s",
				resultsDir.getAbsolutePath());
		File runDir = resultsDir.getAbsoluteFile().getParentFile();
		Preconditions.checkNotNull(runDir, "Results directory has no parent: %s", resultsDir.getAbsolutePath());

		Map<String, List<File>> discovered = discoverHazardDirectories(resultsDir);
		Preconditions.checkState(!discovered.isEmpty(), "No hazard curve directories found beneath %s",
				resultsDir.getAbsolutePath());

		if (positional.size() >= 2) {
			String hazardSubDirName = positional.get(1);
			List<File> hazardDirs = discovered.get(hazardSubDirName);
			Preconditions.checkNotNull(hazardDirs, "Hazard subdirectory '%s' was not found; available: %s",
					hazardSubDirName, discovered.keySet());
			File outputFile = positional.size() == 3 ? new File(positional.get(2))
					: defaultOutputFile(runDir, hazardSubDirName, discovered.keySet());
			buildArchive(runDir, hazardSubDirName, hazardDirs, outputFile, force);
		} else {
			for (Map.Entry<String, List<File>> entry : discovered.entrySet()) {
				File outputFile = defaultOutputFile(runDir, entry.getKey(), discovered.keySet());
				buildArchive(runDir, entry.getKey(), entry.getValue(), outputFile, force);
			}
		}
	}

	private static Map<String, List<File>> discoverHazardDirectories(File resultsDir) {
		Map<String, List<File>> byName = new LinkedHashMap<>();
		ArrayDeque<File> dirs = new ArrayDeque<>();
		dirs.add(resultsDir);
		while (!dirs.isEmpty()) {
			File dir = dirs.removeFirst();
			File[] children = dir.listFiles();
			if (children == null)
				continue;
			for (File child : children) {
				if (!child.isDirectory())
					continue;
				if (child.getName().startsWith(HAZARD_DIR_PREFIX) && !curveFiles(child).isEmpty()) {
					byName.computeIfAbsent(child.getName(), unused -> new ArrayList<>()).add(child);
				} else {
					dirs.addLast(child);
				}
			}
		}
		for (List<File> hazardDirs : byName.values())
			hazardDirs.sort(Comparator.comparing(File::getAbsolutePath));
		return byName;
	}

	private static List<File> curveFiles(File hazardDir) {
		File[] files = hazardDir.listFiles(file -> file.isFile()
				&& file.getName().startsWith(CURVES_PREFIX)
				&& (file.getName().endsWith(".csv") || file.getName().endsWith(".csv.gz")));
		if (files == null || files.length == 0)
			return List.of();
		List<File> ret = new ArrayList<>(List.of(files));
		ret.sort(Comparator.comparing(File::getName));
		return ret;
	}

	private static File defaultOutputFile(File runDir, String hazardSubDirName, Set<String> allConfigurations) {
		if (allConfigurations.size() == 1)
			return new File(runDir, "results_hazard_curves.zip");
		String suffix = shortSuffix(hazardSubDirName);
		long matches = allConfigurations.stream().map(LogicTreeHazardCurveArchiveBuilder::shortSuffix)
				.filter(suffix::equals).count();
		if (matches > 1)
			suffix = hazardSubDirName.substring(HAZARD_DIR_PREFIX.length());
		return new File(runDir, "results_hazard_"+suffix+"_curves.zip");
	}

	private static String shortSuffix(String hazardSubDirName) {
		int optionIndex = hazardSubDirName.lastIndexOf("_grid_seis_");
		return optionIndex >= 0 ? hazardSubDirName.substring(optionIndex+"_grid_seis_".length())
				: hazardSubDirName.substring(HAZARD_DIR_PREFIX.length());
	}

	private static void buildArchive(File runDir, String hazardSubDirName, List<File> hazardDirs,
			File outputFile, boolean force) throws IOException {
		Preconditions.checkState(force || !outputFile.exists(),
				"Output file already exists (supply %s to replace it): %s", FORCE_ARG, outputFile.getAbsolutePath());
		File outputParent = outputFile.getAbsoluteFile().getParentFile();
		Preconditions.checkState(outputParent.isDirectory(), "Output parent directory doesn't exist: %s",
				outputParent.getAbsolutePath());

		System.out.println("Building "+outputFile.getAbsolutePath());
		System.out.println("\tHazard configuration: "+hazardSubDirName);
		System.out.println("\tBranch directories: "+hazardDirs.size());
		File mapArchive = findMapArchive(runDir, hazardSubDirName);
		validateHazardDirectories(hazardDirs, mapArchive);

		File stagingFile = new File(outputFile.getAbsolutePath()+".building");
		Files.deleteIfExists(stagingFile.toPath());
		Files.deleteIfExists(new File(stagingFile.getAbsolutePath()+".tmp").toPath());
		Set<String> entries = new HashSet<>();
		int curveCount = 0;
		boolean completed = false;
		boolean closed = false;
		ArchiveOutput output = new ArchiveOutput.ParallelZipFileOutput(stagingFile, 4, false);
		try {
			for (File hazardDir : hazardDirs) {
				String branchName = hazardDir.getParentFile().getName();
				String branchEntry = branchName+"/";
				Preconditions.checkState(entries.add(branchEntry),
						"Duplicate branch directory name '%s'; use a more specific input directory", branchName);
				output.putNextEntry(branchEntry);
				output.closeEntry();
				for (File curveFile : curveFiles(hazardDir)) {
					String entryName = branchEntry+curveFile.getName();
					Preconditions.checkState(entries.add(entryName), "Duplicate archive entry: %s", entryName);
					try (InputStream in = new BufferedInputStream(new FileInputStream(curveFile))) {
						output.transferFrom(in, entryName);
					}
					curveCount++;
				}
			}

			if (mapArchive != null) {
				System.out.println("\tCopying metadata and mean curves from "+mapArchive.getName());
				copySupportingEntries(mapArchive, output, entries);
			} else {
				System.out.println("\tNo matching hazard map archive found; copying available loose metadata");
				copyLooseMetadata(runDir, output, entries);
			}
			completed = true;
		} finally {
			try {
				output.close();
				closed = true;
			} finally {
				if (!completed || !closed) {
					Files.deleteIfExists(stagingFile.toPath());
					Files.deleteIfExists(new File(stagingFile.getAbsolutePath()+".tmp").toPath());
				}
			}
		}
		Files.move(stagingFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		System.out.println("\tWrote "+curveCount+" branch curve files");
	}

	private static void validateHazardDirectories(List<File> hazardDirs, File mapArchive) throws IOException {
		Set<String> expectedCurveFiles = null;
		Set<String> branchNames = new HashSet<>();
		for (File hazardDir : hazardDirs) {
			String branchName = hazardDir.getParentFile().getName();
			Preconditions.checkState(branchNames.add(branchName), "Duplicate branch directory name: %s", branchName);
			Set<String> myCurveFiles = new HashSet<>();
			for (File curveFile : curveFiles(hazardDir))
				myCurveFiles.add(curveFile.getName());
			if (expectedCurveFiles == null)
				expectedCurveFiles = myCurveFiles;
			else
				Preconditions.checkState(expectedCurveFiles.equals(myCurveFiles),
						"Curve file set differs for branch %s; expected %s, found %s",
						branchName, expectedCurveFiles, myCurveFiles);
		}

		if (mapArchive == null) {
			System.out.println("\tWARNING: no map archive is available to verify the complete branch set");
			return;
		}
		Set<String> mapBranches = new HashSet<>();
		try (ZipFile zip = new ZipFile(mapArchive)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				int slash = name.indexOf('/');
				if (!entry.isDirectory() && slash > 0 && name.indexOf('/', slash+1) < 0
						&& name.substring(slash+1).startsWith("map_"))
					mapBranches.add(name.substring(0, slash));
			}
		}
		Preconditions.checkState(branchNames.equals(mapBranches),
				"Curve branch set does not match %s: curve-only=%s, map-only=%s",
				mapArchive.getName(), difference(branchNames, mapBranches), difference(mapBranches, branchNames));
		System.out.println("\tVerified "+branchNames.size()+" branches against "+mapArchive.getName());
	}

	private static Set<String> difference(Set<String> first, Set<String> second) {
		Set<String> difference = new HashSet<>(first);
		difference.removeAll(second);
		return difference;
	}

	private static File findMapArchive(File runDir, String hazardSubDirName) {
		List<File> archives = new ArrayList<>();
		File[] files = runDir.listFiles(file -> file.isFile()
				&& file.getName().startsWith("results_hazard")
				&& file.getName().endsWith(".zip")
				&& !file.getName().contains("_curves"));
		if (files != null)
			archives.addAll(List.of(files));
		if (archives.isEmpty())
			return null;
		if (archives.size() == 1)
			return archives.get(0);

		String suffix = shortSuffix(hazardSubDirName);
		String expectedName = "results_hazard_"+suffix+".zip";
		for (File archive : archives)
			if (archive.getName().equalsIgnoreCase(expectedName))
				return archive;
		return null;
	}

	private static void copySupportingEntries(File mapArchive, ArchiveOutput output,
			Set<String> writtenEntries) throws IOException {
		try (ZipFile zip = new ZipFile(mapArchive)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				boolean metadata = name.equals(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME)
						|| name.equals(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME)
						|| name.equals(MPJ_LogicTreeHazardCalc.ORIG_LOGIC_TREE_FILE_NAME);
				boolean meanCurves = !name.contains("/") && name.startsWith("mean_curves_")
						&& (name.endsWith(".csv") || name.endsWith(".csv.gz"));
				if ((metadata || meanCurves) && writtenEntries.add(name)) {
					try (InputStream in = zip.getInputStream(entry)) {
						output.transferFrom(in, name);
					}
				}
			}
		}
	}

	private static void copyLooseMetadata(File runDir, ArchiveOutput output,
			Set<String> writtenEntries) throws IOException {
		File regionFile = new File(runDir, MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
		if (regionFile.isFile())
			copyFile(regionFile, MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME, output, writtenEntries);

		File analysisTree = new File(runDir, "logic_tree_analysis.json");
		File originalTree = new File(runDir, AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
		if (analysisTree.isFile()) {
			copyFile(analysisTree, AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME, output, writtenEntries);
			if (originalTree.isFile())
				copyFile(originalTree, MPJ_LogicTreeHazardCalc.ORIG_LOGIC_TREE_FILE_NAME, output, writtenEntries);
		} else if (originalTree.isFile()) {
			copyFile(originalTree, AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME, output, writtenEntries);
		}
	}

	private static void copyFile(File file, String entryName, ArchiveOutput output,
			Set<String> writtenEntries) throws IOException {
		if (!writtenEntries.add(entryName))
			return;
		try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
			output.transferFrom(in, entryName);
		}
	}
}
