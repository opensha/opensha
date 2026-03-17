package scratch.UCERF3.utils;

import org.scec.getfile.GetFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * GetFile wrapper for downloading UCERF3 ERFs.
 */
public class UCERF3_Downloader extends GetFile {
    private static final List<URI> ENDPOINTS = List.of(
            URI.create("https://g-3a9041.a78b8.36fe.data.globus.org/getfile/ucerf3/ucerf3.json"),  // CARC project2
            URI.create("https://data.opensha.org/getfile/ucerf3/ucerf3.json"),  // OpenSHA Server Alias
            URI.create("https://opensha.scec.org/getfile/ucerf3/ucerf3.json")   // OpenSHA Server Hardcode
    );

    /**
     * Create a GetFile instance for downloading UCERF3 models.
     * @param storeDir		Location to download models
     * @param showProgress
     */
    public UCERF3_Downloader(File storeDir, boolean showProgress) {
        super(
                /*name=*/"UCERF3",
                /*clientMetaFile=*/new File(
                        storeDir, "ucerf3_client.json"),
                /*serverMetaURIs=*/ENDPOINTS,
                /*showProgress=*/showProgress);
    }

    /**
     * Use default storeDirectory and specify to show progress.
     */
    public UCERF3_Downloader(boolean showProgress) {
        this(getStoreDir(), showProgress);
    }

    /**
     * Use specified storeDirectory and show progress.
     * @param storeDir
     */
    public UCERF3_Downloader(File storeDir) {
        this(storeDir, /*showProgress=*/true);
    }

    /**
     * Noarg default storeDirectory and shows progress constructor.
     * (Recommended Constructor)
     */
    public UCERF3_Downloader() {
        this(/*showProgress=*/true);
    }

    /**
     * Get the default store directory for UCERF3 file downloads
     * @return	Default store directory to use in default constructor.
     */
    public static File getStoreDir() {
        Path storeDir = Paths.get(
                System.getProperty("user.home"), ".opensha", "ucerf3");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            System.err.println(
                    "UCERF3_Downloader failed to create storeDir at " + storeDir);
            e.printStackTrace();
        }
        return storeDir.toFile();
    }
}
