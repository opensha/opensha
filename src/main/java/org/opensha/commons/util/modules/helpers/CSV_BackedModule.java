package org.opensha.commons.util.modules.helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;

/**
 * Helper interface for {@link ArchivableModule}'s that are backed by a single CSV file. Implementations need only
 * implement {@link #getFileName()}, {@link #getCSV()}, and {@link #initFromCSV(CSVFile)}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface CSV_BackedModule extends FileBackedModule {
	
	/**
	 * @return CSV representation of this Module
	 */
	public CSVFile<?> getCSV();
	
	/**
	 * Loads this module from a CSVFile instance.
	 * @param csv
	 */
	public void initFromCSV(CSVFile<String> csv);

	@Override
	default void writeToStream(OutputStream out) throws IOException {
		getCSV().writeToStream(out);
	}

	@Override
	default void initFromStream(BufferedInputStream in) throws IOException {
		CSVFile<String> csv = CSVFile.readStream(in, false);
		initFromCSV(csv);
	}
	
	public static void writeToArchive(CSVFile<?> csv, ArchiveOutput output, String entryPrefix, String fileName)
			throws IOException {
		FileBackedModule.initEntry(output, entryPrefix, fileName);
		BufferedOutputStream out = new BufferedOutputStream(output.getOutputStream());
		csv.writeToStream(out);
		out.flush();
		output.closeEntry();
	}

	public static CSVFile<String> loadFromArchive(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
		BufferedInputStream zin = FileBackedModule.getInputStream(input, entryPrefix, fileName);
		
		CSVFile<String> csv = CSVFile.readStream(zin, false);
		
		zin.close();
		return csv;
	}

	public static CSVReader loadLargeFileFromArchive(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
		BufferedInputStream zin = FileBackedModule.getInputStream(input, entryPrefix, fileName);

		return new CSVReader(zin);
	}
}
