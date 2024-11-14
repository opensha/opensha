package org.opensha.commons.util.modules.helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;

/**
 * Helper interface for {@link ArchivableModule}'s that are backed by a single CSV file that is never fully stored in
 * memory. The CSV will be written via a {@link CSVWriter}, and read in va a {@link CSVReader}. Implementations need only
 * implement {@link #getFileName()}, {@link #writeCSV(CSVWriter)}, and {@link #initFromCSV(CSVReader)}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface LargeCSV_BackedModule extends FileBackedModule {
	
	/**
	 * Write this module to the given CSVWriter
	 * @param writer
	 * @throws IOException
	 */
	public void writeCSV(CSVWriter writer) throws IOException;
	
	/**
	 * Loads this module from a CSVReader instance.
	 * @param csv
	 */
	public void initFromCSV(CSVReader csv);

	@Override
	default void writeToStream(OutputStream out) throws IOException {
		CSVWriter writer = new CSVWriter(out, false);
		writeCSV(writer);
		writer.flush();
	}

	@Override
	default void initFromStream(BufferedInputStream in) throws IOException {
		CSVReader csv = new CSVReader(in);
		initFromCSV(csv);
	}

	public static CSVReader loadFromArchive(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
		BufferedInputStream zin = FileBackedModule.getInputStream(input, entryPrefix, fileName);

		return new CSVReader(zin);
	}
}
