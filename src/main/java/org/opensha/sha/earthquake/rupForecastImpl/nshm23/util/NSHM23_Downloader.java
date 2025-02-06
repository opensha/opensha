package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.File;
import java.net.URI;

import org.scec.getfile.GetFile;

import scratch.UCERF3.erf.mean.MeanUCERF3;

/**
 * GetFile wrapper for downloading NSHM 2023 ERFs.
 */
public class NSHM23_Downloader extends GetFile {
	private static final String DOWNLOAD_URL = "https://g-c662a6.a78b8.36fe.data.globus.org/getfile/nshm23/nshm23.json";
	
	/**
	 * Create a GetFile instance for downloading NSHM23 models.
	 * @param storeDir		Location to download models
	 * @param showProgress
	 */
	public NSHM23_Downloader(File storeDir, boolean showProgress) {
		super(
				/*name=*/"NSHM 2023",
				/*clientMetaFile=*/new File(
						storeDir, "nshm23_client.json"),
				/*serverMetaURI=*/URI.create(DOWNLOAD_URL),
				/*showProgress=*/showProgress);
	}
	
	/**
	 * Default noarg constructor uses the same store as MeanUCERF3.
	 */
	public NSHM23_Downloader() {
		// TODO: Migrate getStoreDir out of MeanUCERF3 and into a utility class
		this(MeanUCERF3.getStoreDir(), /*showProgress=*/true);
	}
}

	