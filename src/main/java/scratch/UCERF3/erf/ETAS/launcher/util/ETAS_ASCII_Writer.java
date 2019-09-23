package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;

class ETAS_ASCII_Writer {
	
	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_ASCII_Writer.class)
				+" <input-file.bin OR results-dir> <output-dir OR output.zip> [<num-catalogs>]");
			System.exit(2);
		}
		
		File inputFile = new File(args[0]);
		Preconditions.checkState(inputFile.exists());
		
		String outputName = args[1];
		File outputDir;
		ZipOutputStream zip;
		byte[] zip_buffer = new byte[18024];
		if (outputName.toLowerCase().endsWith(".zip")) {
			zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputName)));
			outputDir = Files.createTempDir();
		} else {
			outputDir = new File(outputName);
			Preconditions.checkArgument(outputDir.exists() || outputDir.mkdir(),
					"Output directory doesn't exist and couldn't be created: "+outputDir.getAbsolutePath());
			zip = null;
		}
		
		int numCatalogs = -1;
		if (args.length == 3)
			numCatalogs = Integer.parseInt(args[2]);
		
		int numProcessed = ETAS_CatalogIteration.processCatalogs(inputFile, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(List<ETAS_EqkRupture> catalog, int index) {
				File catalogFile = new File(outputDir, "catalog_"+index+".txt");
				try {
					ETAS_CatalogIO.writeEventDataToFile(catalogFile, catalog);
					if (zip != null) {
						// Add ZIP entry to output stream.
						zip.putNextEntry(new ZipEntry(catalogFile.getName()));
					
						// Associate a file input stream for the current file
						FileInputStream in = new FileInputStream(catalogFile);

						// Transfer bytes from the current file to the ZIP file
						//out.write(buffer, 0, in.read(buffer));

						int len;
						while ((len = in.read(zip_buffer)) > 0) {
							zip.write(zip_buffer, 0, len);
						}
						
						// Close the current entry
						zip.closeEntry();

						// Close the current file input stream
						in.close();
						
						catalogFile.delete();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, numCatalogs, 0d);
		
		if (zip != null) {
			zip.close();
			outputDir.delete();
		}
		
		System.out.println("Completed ASCII output for "+numProcessed+" catalogs");
	}

}
