package org.opensha.commons.data.siteData;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simply downloads site data from GitLab at a specified git repository, path, and tag.
 * This does not offer any guarantee against data corruption.
 * If a download fails, it will not be retried, but an error will be thrown and
 * if a desktop environment is available, a GUI pop-up window will attempt to be shown.
 * <p>
 * If there is data at the specified download path, it is assumed to be up-to-date.
 * This is a reasonable assumption as a tagged version is not expected to change.
 * If there is a future need for data integrity validation and dynamic updates,
 * the GetFile framework should be considered on a fork of the repository with
 * the appropriate GetFile metadata and MD5 checksums.
 * </p>
 */
public abstract class AbstractGitLabDownloader {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private static final boolean D = false;

    protected final VersionResolver version;
    protected final Path outputDir;

    /**
     * Constructor collects data on which version to download and where to download to.
     * @param version Which version of the data to download from GitLab
     * @param outputDir Where the downloaded data will be stored.
     */
    public AbstractGitLabDownloader(VersionResolver version, Path outputDir) {
        this.version = version;
        this.outputDir = outputDir;
    }

    /**
     * Full GitLab URL to retrieve data from.
     * <p>
     * URL should follow the format for "Download Archive" that can be found on
     * the GitLab page for the data.
     * </p>
     */
    protected abstract String getDownloadURL();

    /**
     * The name of the archive we are downloading from GitLab.
     * This can match the name of the archive retrieved from GitLab if used in
     * the `getDownloadURL` implementation.
     */
    protected abstract String getArchiveName();


    /**
     * Name of directory to store extracted site data.
     * Must be unique for each type of data to retrieve.
     */
    protected abstract String getSiteDataEntry();

    /**
     * Extracts the downloaded zip archive
     * Extracted archive name is same as `getSiteDataEntry`
     * @return path to extracted archive
     */
    private Path extractArchive() throws IOException {
        // Check if file exists or throw error
        Path archive = outputDir.resolve(getArchiveName());
        String errTitle = "Extraction Failed";
        if (!Files.exists(archive)) {
            String errMsg = "Failed to extract archive " + archive + ". Does not exist.";
            log.error(errMsg);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, errTitle, JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        Path target = outputDir.resolve(getSiteDataEntry());

        // Create target directory if it doesn't exist
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        // Extract the zip file
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            // The subdirectory we want to extract data from into `outputDir`
            String extractPrefix = findExtractionPrefix(zipFile);
            if (D) System.out.println("Extracting data from " + extractPrefix);

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryPath = entry.getName();

                // Only process entries that are inside the site-data directory
                if (entryPath.startsWith(extractPrefix)) {
                    // Remove the prefix to get the relative path within site-data
                    String relativePath = entryPath.substring(extractPrefix.length());

                    Path entryFile = target.resolve(relativePath);

                    // Create parent directories if they don't exist
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryFile);
                    } else {
                        // Create parent directories for the file
                        Files.createDirectories(entryFile.getParent());

                        // Extract the file
                        try (InputStream is = zipFile.getInputStream(entry);
                             OutputStream fos = Files.newOutputStream(entryFile)) {
                            IOUtils.copy(is, fos);
                        }
                    }
                }
                // Skip all other entries (like the top-level directory and anything outside site-data)
            }
        } catch (java.util.zip.ZipException e) {
            String errMsg = "Invalid or corrupted zip file: " + archive.getFileName() + " - " + e.getMessage();
            log.error(errMsg, e);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, errTitle, JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            String errMsg = "Failed to read zip file: " + archive.getFileName() + " - " + e.getMessage();
            log.error(errMsg, e);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, errTitle, JOptionPane.ERROR_MESSAGE);
            }
        }

        // Delete the archive after extraction
        Files.delete(archive);

        return (Files.exists(target)) ? target : null;
    }

    /**
     * Finds the appropriate extraction prefix by identifying the deepest directory
     * that contains all entries. This method looks for the point where multiple
     * directories appear and uses the common parent directory.
     * <br \>
     * This is necessary as we want to extract the site data from a zip file
     * regardless of how deeply nested it is.
     */
    private String findExtractionPrefix(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        String commonPrefix = null;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryPath = entry.getName();
            // Skip directories that are too shallow (like top-level directories)
            if (entryPath.split("/").length < 2) {
                continue;
            }
            if (commonPrefix == null) {
                commonPrefix = entryPath;
            } else {
                // Find the common prefix between current commonPrefix and entryPath
                commonPrefix = findCommonPrefix(commonPrefix, entryPath);
            }
        }
        // Ensure we have a valid prefix that ends with "/"
        if (commonPrefix != null && !commonPrefix.endsWith("/")) {
            // Find the last "/" and include everything up to that point
            int lastSlash = commonPrefix.lastIndexOf("/");
            if (lastSlash != -1) {
                commonPrefix = commonPrefix.substring(0, lastSlash + 1);
            }
        }

        return commonPrefix;
    }

    /**
     * Finds the common prefix between two paths
     */
    private String findCommonPrefix(String path1, String path2) {
        int minLength = Math.min(path1.length(), path2.length());
        int commonLength = 0;

        for (int i = 0; i < minLength; i++) {
            if (path1.charAt(i) == path2.charAt(i)) {
                commonLength++;
            } else {
                break;
            }
        }

        return path1.substring(0, commonLength);
    }

    /**
     * Attempts to download and extract the data for specified versions.
     * @return path to the extracted archive where data can be found
     */
    public Path downloadSiteData() {
        // Simply returns the path if data was already downloaded.
        // Check for the extracted archive to determine if the data exists.
        Path target = outputDir.resolve(getSiteDataEntry());
        if (D) System.out.println("Check if data already exists at " + target);
        if (Files.exists(target)) {
            return target;
        }
        // Download the archive to the outputDir, then extract it and erase the archive.
        URL url;
        try {
            url = new URL(getDownloadURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Downloading data for " + version.getDisplayName() + " from " + url);
        try (InputStream in = url.openStream()) {
            Path downloadPath = outputDir.resolve(getArchiveName());
            Files.copy(in, downloadPath, StandardCopyOption.REPLACE_EXISTING);
            if (D) System.out.println("Done downloading to " + downloadPath);
            return extractArchive();
        } catch (IOException e) {
            log.error("e: ", e);
            String errMsg = "Failed to download data for " + version.getDisplayName() + " at " + getDownloadURL() + ".\n"
                    + "The GitLab server must be down for maintenance. Please try again later.";
            log.error(errMsg);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, "Download Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null; // Failed to download data
    }

}
