package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_FilterDescendants {

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_FilterDescendants.class)
						+" <etas-config.json> <input-file.bin OR results-dir> <output-file.bin>");
			System.exit(2);
		}
		
		File jsonFile = new File(args[0]);
		Preconditions.checkState(jsonFile.exists(), "ETAS JSON file doesn't exist: %s", jsonFile.getAbsoluteFile());
		
		System.out.println("Loading ETAS JSON file from "+jsonFile.getAbsolutePath());
		ETAS_Config config = ETAS_Config.readJSON(jsonFile);
		
		File inputFile = new File(args[1]);
		Preconditions.checkState(inputFile.exists(), "Input binary file doesn't exist: %s", inputFile.getAbsoluteFile());
		
		File outputFile = new File(args[args.length-1]);
		
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), ETAS_CatalogIO.buffer_len));

		// write number of catalogs as int
		out.writeInt(-1); // will overwrite later
		
		int numProcessed = ETAS_CatalogIteration.processCatalogs(inputFile, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				ETAS_Catalog filtered = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
				try {
					ETAS_CatalogIO.writeCatalogBinary(out, filtered);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
		
		out.close();
		
		System.out.println("Filtered "+numProcessed+" catalogs)");
		
		// now fix the catalog count
		RandomAccessFile raFile = new RandomAccessFile(outputFile, "rw");
		raFile.seek(0l);
		raFile.writeInt(numProcessed);
		raFile.close();
	}

}
