package org.opensha.commons.util;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.scec.getfile.GetFile;

/**
 * Wraps a GetFile instance to enable download progress and any other
 * OpenSHA-specific features.
 */
public class GetFileWrapper {
	private GetFile gf;
	private boolean showProgress;
	private boolean ignoreErrors;

	// Thread to monitor download progress

	/** GetFileWrapper Constructor
	 * @param clientMetaFile	Reference to local metadata file on client
	 * @param serverMetaURI		Link to hosted server metadata file to download
	 * @param showProgress	Show user pane of progress or invoke silently.
	 * @param ignoreErrors  Whether to show user download errors
	 */
	public GetFileWrapper(File clientMetaFile,
			URI serverMetaURI,
			boolean showProgress,
			boolean ignoreErrors) {
		this.gf = new GetFile(clientMetaFile, serverMetaURI);
		this.showProgress = showProgress;
		this.ignoreErrors = ignoreErrors;
		// TODO: Allow creation of at most `n` backups by date.
	}
	
	/**
	 * Wraps a GetFile instance with CalcProgressBar and try/catch on a GetFile
	 * updateFile invocation.
	 * @param file		File reference where file should be downloaded
	 * @return boolean if file is updated or unchanged, Reference to file updated
	 */
	public Pair<Boolean, File> updateFile(File file) {
		String fileKey = getFileKey(file);
		// TODO: Leverage getFileSize
//		long fileSize = gf.getFileSize(fileKey);
		final CalcProgressBar progress = new CalcProgressBar("Downloading MeanUCERF3 Files", "downloading "+fileKey);
		Pair<Boolean, File> result = null;
		ExecutorService executor = Executors.newSingleThreadExecutor();
		// try to show progress bar
		try {
			if (showProgress) {
				progress.setVisible(true);
				executor.submit(() -> progressMonitor(file, progress));
			}
			result = gf.updateFile(fileKey);
		} catch (Exception e) {
			if (progress != null) {
				// not headless
				progress.setVisible(false);
				progress.dispose();
				if (!ignoreErrors) {
					String message = "Error downloading "+fileKey+".\nServer down or file moved, try again later.";
					JOptionPane.showMessageDialog(null, message, "Download Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			if (ignoreErrors)
				return null;
			else
				ExceptionUtils.throwAsRuntimeException(e);
		} finally {
			executor.shutdown();
		}
		if (progress != null) {
			progress.setVisible(false);
			progress.dispose();
		}
		return result;
	}
	
	public Map<String, List<File>> updateAll() {
		// TODO: Leverage getTotalSumFileSize.
		// We should regularly check for current `part` file (downloads are serial)

		return gf.updateAll();  // TODO
	}
	
	/**
	 * Monitoring thread for progress downloading a file
	 * @param file
	 * @param progress
	 */
	private void progressMonitor(File file, CalcProgressBar progress) {
		String fileKey = getFileKey(file);
		long total = gf.getFileSize(fileKey);
		File partial = new File(file.toString().concat(".part"));
		try {
			// Wait for 5 seconds for partial to be created. If download takes
			// longer than 5 seconds, then we should show progress bar.
			TimeUnit.SECONDS.sleep(3);
		} catch (InterruptedException e) {
			 Thread.currentThread().interrupt();
		}
		while (partial.exists()) {
			long count = partial.length();
			progress.updateProgress(count, total,
					count+" of "+total+" bytes downloaded");
			try {
				// Sleep for a short duration to periodically check the file size
				 TimeUnit.SECONDS.sleep(1);
			 } catch (InterruptedException e) {
				 Thread.currentThread().interrupt();
				 break;
			 }
		}
		if (file.exists() && !partial.exists()) {
			progress.updateProgress(total, total);
		}
	}
	
	private static String getFileKey(File file) {
		return FilenameUtils.getBaseName(file.getName());
	}
	
	// TODO: Create a public rollback function by date.
	// TODO: Integrate rollback into GUI per applicable ERF page.
}
