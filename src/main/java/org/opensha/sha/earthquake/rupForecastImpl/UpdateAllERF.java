package org.opensha.sha.earthquake.rupForecastImpl;

import java.io.File;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.scec.getfile.GetFile;

/**
 * Test that we're able to download all ERF data using GetFile.
 * Required for testing for filesystem corruption.
 * This should only be run occassionally as updating all files may be expensive.
 */
public class UpdateAllERF {
		private static final String[] UCERF3_DAT = {
			"branch_avgs_combined",
			"cached_dep100.0_depMean_rakeMean",
			"cached_FM3_1_dep100.0_depMean_rakeMean",
			"cached_FM3_2_dep100.0_depMean_rakeMean",
			"FM3_1_ABM_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_1_branch_averaged_full_modules",
			"FM3_1_branch_averaged_with_logic_tree",
			"FM3_1_branch_averaged",
			"FM3_1_GEOL_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_1_mean_ucerf3_sol",
			"FM3_1_NEOK_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_1_SpatSeisU2_branch_averaged_full_modules",
			"FM3_1_SpatSeisU2_branch_averaged",
			"FM3_1_SpatSeisU3_branch_averaged_full_modules",
			"FM3_1_SpatSeisU3_branch_averaged",
			"FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_1_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_2_ABM_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_2_branch_averaged_full_modules",
			"FM3_2_branch_averaged_with_logic_tree",
			"FM3_2_branch_averaged",
			"FM3_2_GEOL_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_2_mean_ucerf3_sol",
			"FM3_2_NEOK_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_2_SpatSeisU2_branch_averaged_full_modules",
			"FM3_2_SpatSeisU2_branch_averaged",
			"FM3_2_SpatSeisU3_branch_averaged_full_modules",
			"FM3_2_SpatSeisU3_branch_averaged",
			"FM3_2_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"FM3_2_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3",
			"full_logic_tree_with_gridded",
			"full_logic_tree",
			"mean_ucerf3_sol_with_mappings",
			"mean_ucerf3_sol",
			"rake_basis",
			"full_ucerf3_compound_sol",
		};
		
		private static final String[] NSHM23_DAT = {
			"WUS_branch_averaged_gridded_simplified",
		};

	public static void main(String[] args) {
		final GetFile UCERF3_DOWNLOADER = new GetFile(
				/*name=*/"UCERF3",
				/*clientMetaFile=*/new File(
						System.getProperty("user.home"), ".opensha/ucerf3/ucerf3_client.json"),
				/*serverMetaURI=*/URI.create(
						"https://g-c662a6.a78b8.36fe.data.globus.org/getfile/ucerf3/ucerf3.json"),
				/*showProgress=*/false);
		
		try {
			UCERF3_DOWNLOADER.updateAll().get();
			for (String dat : UCERF3_DAT) {
				File model = new File(System.getProperty("user.home"), ".opensha/ucerf3/" + dat);
				assert(model.exists());
			}
			
		new NSHM23_Downloader(/*showProgress=*/false).updateAll().get();
			for (String dat : NSHM23_DAT) {
				File model = new File(System.getProperty("user.home"), ".opensha/nshm23/" + dat);
				assert(model.exists());
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

}
