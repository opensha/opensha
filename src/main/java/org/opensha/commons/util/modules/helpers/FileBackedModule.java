package org.opensha.commons.util.modules.helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;

import com.google.common.base.Preconditions;

/**
 * Helper class for an {@link ArchivableModule} that is backed by a single file. Provides default implementations
 * of {@link ArchivableModule#initFromArchive(ZipFile, String)} and
 * {@link ArchivableModule#writeToArchive(ZipOutputStream, String)}, requiring that implementing classes only need to
 * specify a file name, be able to write themselves to a {@link BufferedOutputStream}, and initialize themselves from a
 * {@link BufferedInputStream}. 
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface FileBackedModule extends ArchivableModule {
	
	/**
	 * @return file name used by this module
	 */
	public String getFileName();

	@Override
	public default void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		writeToArchive(zout, entryPrefix, getFileName());
	}

	public default void writeToArchive(ZipOutputStream zout, String entryPrefix, String fileName) throws IOException {
		initEntry(zout, entryPrefix, fileName);
		
		BufferedOutputStream out = new BufferedOutputStream(zout);
		writeToStream(out);
		out.flush();
		zout.closeEntry();
	}
	
	/**
	 * Initializes a {@link ZipEntry} in the given {@link ZipOutputStream} taking the given prefix into account.
	 * 
	 * @param zout
	 * @param entryPrefix
	 * @param fileName
	 * @throws IOException
	 */
	public static void initEntry(ZipOutputStream zout, String entryPrefix, String fileName)
			throws IOException {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		
		ZipEntry entry = new ZipEntry(entryName);
		zout.putNextEntry(entry);
	}
	
	/**
	 * Write the file contents to this output stream
	 * 
	 * @param out
	 * @throws IOException
	 */
	public void writeToStream(BufferedOutputStream out) throws IOException;

	@Override
	public default void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		BufferedInputStream zin = getInputStream(zip, entryPrefix, getFileName());
		initFromStream(zin);
		
		zin.close();
	}
	
	public static boolean hasEntry(ZipFile zip, String entryPrefix, String fileName) {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		return zip.getEntry(entryName) != null;
	}
	
	public static BufferedInputStream getInputStream(ZipFile zip, String entryPrefix, String fileName) throws IOException {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		ZipEntry entry = zip.getEntry(entryName);
		Preconditions.checkNotNull(entry, "Entry not found in zip archive: %s", entryName);
		
		return new BufferedInputStream(zip.getInputStream(entry));
	}
	/**
	 * Initialize this module from the contents of the file (via this stream)
	 * 
	 * @param in
	 * @throws IOException
	 */
	public void initFromStream(BufferedInputStream in) throws IOException;

}
