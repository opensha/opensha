package org.opensha.commons.util.modules.helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
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
	public default void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		writeToArchive(output, entryPrefix, getFileName());
	}

	public default void writeToArchive(ArchiveOutput output, String entryPrefix, String fileName) throws IOException {
		initEntry(output, entryPrefix, fileName);
		
		OutputStream out = output.getOutputStream();
		BufferedOutputStream bout;
		if (out instanceof BufferedOutputStream)
			bout = (BufferedOutputStream)out;
		else
			bout = new BufferedOutputStream(out);
		writeToStream(bout);
		bout.flush();
		output.closeEntry();
	}
	
	/**
	 * Initializes an entry in the given {@link ArchiveOutput} taking the given prefix into account.
	 * 
	 * @param zout
	 * @param entryPrefix
	 * @param fileName
	 * @return fully qualified entry name
	 * @throws IOException
	 */
	public static String initEntry(ArchiveOutput out, String entryPrefix, String fileName)
			throws IOException {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		
		out.putNextEntry(entryName);
		return entryName;
	}
	
	/**
	 * Calls {@link #initEntry(ArchiveOutput, String, String)} to initialize an entry, then returns an
	 * {@link OutputStream} for that entry;
	 * 
	 * @param out
	 * @param entryPrefix
	 * @param fileName
	 * @return
	 * @throws IOException 
	 */
	public static OutputStream initOutputStream(ArchiveOutput out, String entryPrefix, String fileName)
			throws IOException {
		initEntry(out, entryPrefix, fileName);
		return out.getOutputStream();
	}
	
	/**
	 * Write the file contents to this output stream
	 * 
	 * @param out
	 * @throws IOException
	 */
	public void writeToStream(OutputStream out) throws IOException;

	@Override
	public default void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		BufferedInputStream zin = getInputStream(input, entryPrefix, getFileName());
		initFromStream(zin);
		
		zin.close();
	}
	
	public static boolean hasEntry(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		return input.hasEntry(entryName);
	}
	
	public static final int DEFAULT_BUFFER_SIZE = 1024 * 32 * 8; // 32 kBytes
	
	public static BufferedInputStream getInputStream(ArchiveInput input, String entryPrefix, String fileName) throws IOException {
		String entryName = ArchivableModule.getEntryName(entryPrefix, fileName);
		Preconditions.checkNotNull(entryName, "entryName is null. prefix='%s', fileName='%s'", entryPrefix, fileName);
		
		return new BufferedInputStream(input.getInputStream(entryName), DEFAULT_BUFFER_SIZE);
	}
	/**
	 * Initialize this module from the contents of the file (via this stream)
	 * 
	 * @param in
	 * @throws IOException
	 */
	public void initFromStream(BufferedInputStream in) throws IOException;

}
