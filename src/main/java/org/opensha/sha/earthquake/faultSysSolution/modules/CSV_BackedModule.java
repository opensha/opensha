package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;

import com.google.common.base.Preconditions;

public interface CSV_BackedModule extends StatefulModule {
	
	/**
	 * File name to use for the CSV file that represents this module
	 * 
	 * @return
	 */
	public String getCSV_FileName();

	@Override
	default void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		String entryName = StatefulModule.getEntryName(entryPrefix, getCSV_FileName());
		
		ZipEntry entry = new ZipEntry(entryName);
		zout.putNextEntry(entry);
		
		BufferedOutputStream out = new BufferedOutputStream(zout);
		getCSV().writeToStream(out);
		out.flush();
		zout.closeEntry();
	}

	@Override
	default void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		String entryName = StatefulModule.getEntryName(entryPrefix, getCSV_FileName());
		ZipEntry entry = zip.getEntry(entryName);
		Preconditions.checkNotNull(entry, "Entry not found in zip archive: %s", entryName);
		
		BufferedInputStream zin = new BufferedInputStream(zip.getInputStream(entry));
		
		CSVFile<String> csv = CSVFile.readStream(zin, false);
		initFromCSV(csv);
		
		zin.close();
	}
	
	/**
	 * @return CSV representation of this Module
	 */
	public CSVFile<?> getCSV();
	
	/**
	 * Loads this module from a CSVFile instance.
	 * @param csv
	 */
	public void initFromCSV(CSVFile<String> csv);

	
}
