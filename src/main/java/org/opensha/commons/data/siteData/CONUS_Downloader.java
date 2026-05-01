package org.opensha.commons.data.siteData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simply downloads site data from USGS GitLab for specified <code>CONUS_Versions</code>.
 * Will not re-download if the data is already present.
 */
public class CONUS_Downloader extends AbstractGitLabDownloader {
    private static final String BASE_URL = "https://code.usgs.gov/ghsc/nshmp/nshms/nshm-conus/-/archive/";
    private static final Logger log = LoggerFactory.getLogger(CONUS_Downloader.class);

    /**
     * CONUS_Downloader constructor collects data on which version to
     * download and where to download to.
     * @param version Which site data to download from the USGS GitLab
     * @param outputDir Where the downloaded data will be stored
     */
    CONUS_Downloader(CONUS_Versions version, Path outputDir) {
        super(version, outputDir);
    }

    /**
     * CONUS_Downloader constructor with a default location for downloads.
     * (Recommended Constructor)
     * @param version Which site data to download from the USGS GitLab
     */
    CONUS_Downloader(CONUS_Versions version) {
        this(version, getStoreDir());
    }

    @Override
    protected String getArchiveName() {
        return "nshm-conus-" + version.getTag() + ".zip";
    }

    @Override
    protected String getDownloadURL() {
        return BASE_URL + version.getTag() + "/" + getArchiveName() + "?ref_type=tags&path=site-data";
    }

    @Override
    protected String getSiteDataEntry() {
        // Simply uses the name of the CONUS_Versions Enum.
       return ((CONUS_Versions)version).name();
    }

    /**
     * Get the default store directory for CONUS file downloads
     * @return	Default store directory to use in default constructor.
     */
    public static Path getStoreDir() {
        Path storeDir = Paths.get(
                System.getProperty("user.home"), ".site_data", "conus");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            log.error("e: ", e);
            System.err.println("CONUS_Downloader failed to create storeDir at " + storeDir);
        }
        return storeDir;
    }

    /**
     * CLT to test CONUS site data retrieval from USGS GitLab
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Match name of the enum (not the display name)
        if (args.length != 1) {
            System.out.println("Usage: CONUS_Downloader <CONUS_Versions>");
            System.out.println("e.g., NSHM18, NSHM23");
            return;
        }
        CONUS_Versions version;
        try {
            version = Enum.valueOf(CONUS_Versions.class, args[0]);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid input: " + args[0]);
            System.out.println("Valid inputs are: ");
            for (CONUS_Versions v : CONUS_Versions.values()) {
                System.out.println("  " + v + " for " + v.getDisplayName());
            }
            return;
        }
        System.out.println("Downloaded: " + new CONUS_Downloader(version).downloadSiteData());
    }
}
