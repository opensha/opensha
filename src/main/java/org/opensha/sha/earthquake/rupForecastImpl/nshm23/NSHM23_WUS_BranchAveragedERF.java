package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import javax.swing.JOptionPane;

import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;

/**
 * USGS 2023 NSHM ERF, Western U.S., Branched Average ERF
 */
public class NSHM23_WUS_BranchAveragedERF extends BaseFaultSystemSolutionERF {

	private static final long serialVersionUID = 277613161331416141L;
	private static final String MODEL = "WUS_branch_averaged_gridded_simplified";
	
	private CompletableFuture<SolutionLogicTree> future;

	private static CompletableFuture<SolutionLogicTree> loadFetcher() {
		return new NSHM23_Downloader()
				.updateFile(MODEL)
				.thenApply(treeFile -> {
			if (treeFile == null || !treeFile.exists()) {
				JOptionPane.showMessageDialog(null,
						"Failed to download " + MODEL +
						". Verify internet connection and restart. Server may be down.",
						"NSHM23_WUS_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
			try {
				return new ModuleArchive<>(treeFile, SolutionLogicTree.class)
						.requireModule(SolutionLogicTree.class);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, e.getMessage(),
						"NSHM23_WUS_BranchAveragedERF", JOptionPane.ERROR_MESSAGE);
				return null;
			}
		});
	}

	/**
	 * Initializes FaulSystemSolutionERF with provided solution
	 * @param future	A future for the WUS model. Will resolve once available.
	 */
	public NSHM23_WUS_BranchAveragedERF(CompletableFuture<SolutionLogicTree> future) {
		this.future = future;
		// TODO
	}
	
	/**
	 * Noarg constructor uses default FaultSystemSolution
	 */
	public NSHM23_WUS_BranchAveragedERF() {
		this(loadFetcher());
	}
}
