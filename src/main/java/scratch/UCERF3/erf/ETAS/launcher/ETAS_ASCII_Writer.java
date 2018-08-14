package scratch.UCERF3.erf.ETAS.launcher;

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

public class ETAS_ASCII_Writer {
	
	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_ASCII_Writer.class)
				+" <binary-catalogs-file> <output-dir/zip-file> [<num-catalogs>]");
			System.exit(2);
		}
		
		File binaryFile = new File(args[0]);
		Preconditions.checkState(binaryFile.exists());
		
		File outputDir = new File(args[1]);
		ZipOutputStream zip = null;
		byte[] zip_buffer = new byte[18024];
		if (outputDir.getName().toLowerCase().endsWith(".zip")) {
			zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputDir)));
			outputDir = Files.createTempDir();
		} else {
			Preconditions.checkArgument(outputDir.exists() || outputDir.mkdir(),
					"Output directory doesn't exist and couldn't be created: "+outputDir.getAbsolutePath());
		}
		
		int numCatalogs = -1;
		if (args.length == 3)
			numCatalogs = Integer.parseInt(args[2]);
		
		Iterator<List<ETAS_EqkRupture>> catalogsIterator = 
				ETAS_CatalogIO.getBinaryCatalogsIterable(binaryFile, 0).iterator();
		
		int numProcessed = 0;
		int modulus = 10;
		while (catalogsIterator.hasNext()) {
			if (numProcessed % modulus == 0) {
				System.out.println("Processing catalog "+numProcessed);
				if (numProcessed == modulus*10)
					modulus *= 10;
			}
			List<ETAS_EqkRupture> catalog;
			try {
				catalog = catalogsIterator.next();
				File catalogFile = new File(outputDir, "catalog_"+numProcessed+".txt");
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
				numProcessed++;
				if (numCatalogs > 0 && numProcessed == numCatalogs)
					break;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.flush();
				System.out.println("Partial catalog detected or other error, stopping with "+numProcessed+" catalogs");
				break;
			}
		}
		
		if (zip != null) {
			zip.close();
			outputDir.delete();
		}
		
		System.out.println("Completed ASCII output for "+numProcessed+" catalogs");
	}

}
