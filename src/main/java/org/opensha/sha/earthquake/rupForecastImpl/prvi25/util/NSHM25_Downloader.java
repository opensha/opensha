package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.scec.getfile.GetFile;

/**
 * GetFile wrapper for downloading NSHM 2025 ERFs.
 */
public class NSHM25_Downloader extends GetFile {
    private static final List<URI> ENDPOINTS = List.of(
            URI.create("https://g-3a9041.a78b8.36fe.data.globus.org/getfile/nshm25/nshm25.json"),  // CARC project2
            URI.create("https://data.opensha.org/getfile/nshm25/nshm25.json"),  // OpenSHA Server Alias
            URI.create("https://opensha.scec.org/getfile/nshm25/nshm25.json")   // OpenSHA Server Hardcode
    );

    /**
     * Create a GetFile instance for downloading NSHM25 models.
     * @param storeDir		Location to download models
     * @param showProgress
     */
    public NSHM25_Downloader(File storeDir, boolean showProgress) {
        super(
                /*name=*/"NSHM 2025",
                /*clientMetaFile=*/new File(
                        storeDir, "nshm25_client.json"),
                /*serverMetaURIs=*/ENDPOINTS,
                /*showProgress=*/showProgress);
    }

    /**
     * Use default storeDirectory and specify to show progress.
     */
    public NSHM25_Downloader(boolean showProgress) {
        this(getStoreDir(), showProgress);
    }

    /**
     * Use specified storeDirectory and show progress.
     * @param storeDir
     */
    public NSHM25_Downloader(File storeDir) {
        this(storeDir, /*showProgress=*/true);
    }

    /**
     * Noarg default storeDirectory and shows progress constructor.
     * (Recommended Constructor)
     */
    public NSHM25_Downloader() {
        this(/*showProgress=*/true);
    }

    /**
     * Get the default store directory for NSHM25 file downloads
     * @return	Default store directory to use in default constructor.
     */
    public static File getStoreDir() {
        Path storeDir = Paths.get(
                System.getProperty("user.home"), ".opensha", "nshm25");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            System.err.println(
                    "NSHM25_Downloader failed to create storeDir at " + storeDir);
            e.printStackTrace();
        }
        return storeDir.toFile();
    }
}

