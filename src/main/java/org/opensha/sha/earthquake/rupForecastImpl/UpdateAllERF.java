package org.opensha.sha.earthquake.rupForecastImpl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.NSHM25_Downloader;
import org.scec.getfile.GetFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import scratch.UCERF3.utils.UCERF3_Downloader;

/**
 * Test that we're able to download all ERF data using GetFile.
 * Required for testing for filesystem corruption.
 * This should only be run occasionally as updating all files may be expensive.
 */
public class UpdateAllERF {

    /**
     * Returns a list of all key entries from the JSON
     * @param jsonFile The metadata file listing all JSON entries
     * @return
     */
    private static String[] getModels(File jsonFile) {
        try (Reader reader = new FileReader(jsonFile)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            String[] keys = jsonObject.keySet().toArray(new String[0]);
            return keys;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Download all ERF data for a given ERF.
     * Future resolves true if all files are downloaded and verified, false otherwise
     * @param erfName    Name of ERF
     * @param downloader GetFile instance to download specific ERF
     * @return CompletableFuture<Boolean> that completes when the update and verification are done
     */
    public static CompletableFuture<Boolean> updateERF(String erfName, GetFile downloader) {
        System.out.println("Starting update for " + erfName);

        return downloader.updateAll()
            .thenApply(result -> {
                System.out.println("Update completed for " + erfName + ", checking files...");
                try {
                    for (String dat : getModels(
                            new File(System.getProperty("user.home"), ".opensha/"+erfName+"/"+erfName+".json"))) {
                        File model = new File(System.getProperty("user.home"), ".opensha/"+erfName+"/" + dat + ".zip");
                        System.out.println("Checking if " + model + " exists...");
                        if (!model.exists()) {
                            System.out.println(model + " is missing!");
                            return false;
                        }
                    }
                    System.out.println("All files verified for " + erfName);
                    return true;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            })
            .exceptionally(throwable -> {
                System.err.println("Error updating " + erfName + ": " + throwable.getMessage());
                throwable.printStackTrace();
                return false;
            });
    }

    public static void main(String[] args) {
        boolean showProgress = false;

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        futures.add(updateERF("ucerf3", new UCERF3_Downloader(showProgress)));
        futures.add(updateERF("nshm23", new NSHM23_Downloader(showProgress)));
        futures.add(updateERF("nshm25", new NSHM25_Downloader(showProgress)));

        // Wait for all updates to complete
        CompletableFuture<Void> allUpdates = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            // Block until all are complete
            allUpdates.join();
            System.out.println("All ERF updates completed!");

            boolean allSuccess = true;
            for (CompletableFuture<Boolean> future : futures) {
                Boolean result = future.get();
                if (result == null || !result) {
                    allSuccess = false;
                }
            }

            if (allSuccess) {
                System.out.println("All ERF updates completed successfully!");
            } else {
                System.err.println("Some ERF updates failed!");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error during ERF updates: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
