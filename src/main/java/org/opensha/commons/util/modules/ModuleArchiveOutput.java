package org.opensha.commons.util.modules;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.opensha.commons.data.Named;

import com.google.common.base.Preconditions;

/**
 * Output interface for a {@link ModuleArchive}. To write data, callers much initialize a new entry via
 * {@link #putNextEntry(String)}, write the data (if it's not just a directory) via {@link #getOutputStream()}, then
 * close the entry via {@link #closeEntry()}. Once everything is written, callers must call {@link #close()}.
 */
public interface ModuleArchiveOutput extends Closeable, Named {
	
	/**
	 * Begins an entry with the given name
	 * @param name
	 * @throws IOException
	 */
	public void putNextEntry(String name) throws IOException;
	
	/**
	 * Gets an {@link OutputStream} for the currently entry. Must call {@link #putNextEntry(String)} first, and should
	 * only call this once. IMPORTANT: never close this output stream as it may be reused for multiple entries, depending
	 * on the implementation.
	 * @return {@link OutputStream} for the currently entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;
	
	/**
	 * Closes the current entry. This will automatically flush the {@link OutputStream}
	 * @throws IOException
	 */
	public void closeEntry() throws IOException;
	
	/**
	 * Once an archive has been fully written and closed via {@link #close()}, this can be used to get a {@link ModuleArchiveInput}
	 * from the completed output.
	 * @return
	 * @throws IOException
	 */
	public ModuleArchiveInput getCompletedInput() throws IOException;
	
	/**
	 * Transfer an entry of the given name from the given {@link ModuleArchiveInput} to this {@link ModuleArchiveOutput}.
	 * 
	 * This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}.
	 * 
	 * @param input
	 * @param name
	 * @throws IOException
	 */
	public default void transferFrom(ModuleArchiveInput input, String name) throws IOException {
		transferFrom(input, name, name);
	}
	
	/**
	 * Transfer an entry of the given name from the given {@link ModuleArchiveInput} to this {@link ModuleArchiveOutput},
	 * possibly renaming the entry.
	 * 
	 * This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}.
	 * 
	 * @param input
	 * @param sourceName
	 * @param destName
	 * @throws IOException
	 */
	public default void transferFrom(ModuleArchiveInput input, String sourceName, String destName) throws IOException {
		transferFrom(input.getInputStream(sourceName), destName);
	}
	
	/**
	 * Create an entry of the given name with contents from the given input stream.
	 * 
	 * This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}.
	 * 
	 * @param input
	 * @param name
	 * @throws IOException
	 */
	public default void transferFrom(InputStream is, String name) throws IOException {
		putNextEntry(name);
		is.transferTo(getOutputStream());
		closeEntry();
	}
	
	/**
	 * {@link ModuleArchiveOutput} that is backed by a {@link File}, possibly with a temporary file while writing
	 * (see {@link #getInProgressFile()}) that is written to a final file (see {@link #getDestinationFile()}) when
	 * completed.
	 */
	public interface FileBacked extends ModuleArchiveOutput {
		/**
		 * This returns the path to the output file representing this archive while it is writing (and before
		 * {@link #close()} is called), and may (but doesn't need to) differ from the eventual destination file
		 * (see {@link #getDestinationFile()}).
		 * 
		 * @return the output file while results are being written
		 */
		public File getInProgressFile();
		
		/**
		 * This returns the final output file after this writing has completed (by calling {@link #close())}.
		 * 
		 * @return the final output file
		 */
		public File getDestinationFile();

		@Override
		default String getName() {
			File destFile = getDestinationFile();
			if (destFile == null)
				return null;
			return destFile.getAbsolutePath();
		}
		
	}
	
	/**
	 * Gets the default {@link ModuleArchiveOutput} implementation for the given file, using the filename.
	 * @param outputFile
	 * @return
	 * @throws IOException
	 */
	public static ModuleArchiveOutput getDefaultOutput(File outputFile) throws IOException {
		return getDefaultOutput(outputFile, null);
	}
	
	/**
	 * Gets the default {@link ModuleArchiveOutput} implementation for the given file, using the filename as well
	 * as the input implementation to see if there is a compatible output. For example, if the input uses the Apache
	 * Zip library rather than the standard Java library, the output will as well so that transfer operations need not
	 * re-inflate and then deflate each entry.
	 * @param outputFile
	 * @param input
	 * @return
	 * @throws IOException
	 */
	public static ModuleArchiveOutput getDefaultOutput(File outputFile, ModuleArchiveInput input) throws IOException {
		String name = outputFile.getName().toLowerCase();
		if (name.endsWith(".tar")) {
			return new TarFileOutput(outputFile);
		} else if (name.endsWith(".zip")) {
			if (input instanceof ModuleArchiveInput.ApacheZipFileInput)
				return new ApacheZipFileOutput(outputFile);
			if (input instanceof ModuleArchiveInput.ZipFileSystemInput)
				return new ZipFileSystemOutput(outputFile.toPath());
			return new ZipFileOutput(outputFile);
		}
		// unknown extesion, assume it's probably zip but check if the input is tar
		if (input instanceof ModuleArchiveInput.TarFileInput)
			return new TarFileOutput(outputFile);
		return new ZipFileOutput(outputFile);
	}
	
	/**
	 * Output implementation using the standard Java {@link ZipOutputStream}.
	 */
	public static class ZipFileOutput implements FileBacked {
		
		private File inProgressFile;
		private File outputFile;
		private ZipOutputStream zout;

		public ZipFileOutput(File outputFile) throws IOException {
			this(outputFile, new File(outputFile.getAbsolutePath()+".tmp"));
		}

		public ZipFileOutput(File outputFile, File inProgressFile) throws IOException {
			this.outputFile = outputFile;
			this.inProgressFile = inProgressFile;
			zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(inProgressFile)));
		}
		
		public ZipFileOutput(ZipOutputStream zout) {
			this.outputFile = new File(zout.toString());
			this.inProgressFile = outputFile;
		}

		@Override
		public void putNextEntry(String name) throws IOException {
			zout.putNextEntry(new ZipEntry(name));
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return zout;
		}

		@Override
		public void closeEntry() throws IOException {
			zout.flush();
			zout.closeEntry();
		}

		@Override
		public void close() throws IOException {
			if (zout == null)
				// already closed
				return;
			zout.close();
			zout = null;
			if (!inProgressFile.equals(outputFile))
				Files.move(inProgressFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		@Override
		public File getInProgressFile() {
			return inProgressFile;
		}

		@Override
		public File getDestinationFile() {
			return outputFile;
		}

		@Override
		public ModuleArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "ZipOutputStream is still open");
			return new ModuleArchiveInput.ZipFileInput(outputFile);
		}
		
	}
	
	public static abstract class AbstractApacheZipOutput implements ModuleArchiveOutput {
		
		protected ZipArchiveOutputStream zout;

		public AbstractApacheZipOutput(ZipArchiveOutputStream zout, boolean compressed) {
			this.zout = zout;
			if (!compressed)
				zout.setMethod(ZipEntry.STORED);
		}

		@Override
		public void putNextEntry(String name) throws IOException {
			zout.putArchiveEntry(new ZipArchiveEntry(name));
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return zout;
		}

		@Override
		public void closeEntry() throws IOException {
			zout.flush();
			zout.closeArchiveEntry();
		}

		@Override
		public void transferFrom(ModuleArchiveInput input, String sourceName, String destName) throws IOException {
			if (input instanceof ModuleArchiveInput.ApacheZipFileInput) {
				ModuleArchiveInput.ApacheZipFileInput apache = (ModuleArchiveInput.ApacheZipFileInput)input;
				ZipArchiveEntry sourceEntry = apache.getEntry(sourceName);
				ZipArchiveEntry outEntry = new ZipArchiveEntry(destName);
				outEntry.setCompressedSize(sourceEntry.getCompressedSize());
				outEntry.setCrc(sourceEntry.getCrc());
				outEntry.setExternalAttributes(sourceEntry.getExternalAttributes());
				outEntry.setExtra(sourceEntry.getExtra());
				outEntry.setExtraFields(sourceEntry.getExtraFields());
				outEntry.setGeneralPurposeBit(sourceEntry.getGeneralPurposeBit());
				outEntry.setInternalAttributes(sourceEntry.getInternalAttributes());
				outEntry.setMethod(sourceEntry.getMethod());
				outEntry.setRawFlag(sourceEntry.getRawFlag());
				outEntry.setSize(sourceEntry.getSize());
				
				zout.addRawArchiveEntry(outEntry, apache.getRawInputStream(sourceEntry));
			} else {
				ModuleArchiveOutput.super.transferFrom(input, sourceName, destName);
			}
		}

		@Override
		public void close() throws IOException {
			if (zout == null)
				// already closed
				return;
			zout.close();
			zout = null;
		}
		
	}
	
	/**
	 * In-memory implementation using the Apache libraries, supporting either compressed or uncompressed entries.
	 */
	public static class InMemoryZipOutput extends AbstractApacheZipOutput {

		private boolean compressed;
		private CopyAvoidantInMemorySeekableByteChannel byteChannel;

		public InMemoryZipOutput(boolean compressed) {
			this(compressed, 1024*1024*100); // 500 MB
		}

		public InMemoryZipOutput(boolean compressed, int size) {
			this(compressed, new CopyAvoidantInMemorySeekableByteChannel(size));
		}
		
		public InMemoryZipOutput(boolean compressed, CopyAvoidantInMemorySeekableByteChannel byteChannel) {
			super(new ZipArchiveOutputStream(byteChannel), compressed);
//			super(new ZipArchiveOutputStream(new BufferedOutputStream(new ByteBu)))
//			super(new ZipArchiveOutputStream(new ByteArrayOutputStream(size)), compressed);
			this.compressed = compressed;
			this.byteChannel = byteChannel;
			System.out.println("Initial size: "+(float)(byteChannel.size()/(1024d*1024d))+" mb");
		}

		@Override
		public String getName() {
			return "In Memory ("+(compressed ? "zipped" : "uncompressed")+")";
		}

		@Override
		public void putNextEntry(String name) throws IOException {
			System.out.println("STARTING WRITE: "+name);
			super.putNextEntry(name);
		}

		@Override
		public void closeEntry() throws IOException {
			super.closeEntry();
			System.out.println("Current size: "+(float)(byteChannel.size()/(1024d*1024d))+" mb");
		}

		@Override
		public ModuleArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "Not closed?");
			CopyAvoidantInMemorySeekableByteChannel copy = byteChannel.copy();
			copy.position(0l);
			return new ModuleArchiveInput.InMemoryZipFileInput(copy);
		}
		
	}
	
	/**
	 * Zip file implementation using the Apeche Common I/O library; this can be used to efficiently transfer data
	 * from an {@link ModuleArchiveInput.ApacheZipFileInput} without inflating and deflating.
	 */
	public static class ApacheZipFileOutput extends AbstractApacheZipOutput implements FileBacked {
		
		private File outputFile;
		private File inProgressFile;

		public ApacheZipFileOutput(File outputFile) throws IOException {
			this(outputFile, new File(outputFile.getAbsolutePath()+".tmp"));
		}

		public ApacheZipFileOutput(File outputFile, File inProgressFile) throws IOException {
			super(new ZipArchiveOutputStream(inProgressFile), true);
			this.outputFile = outputFile;
			this.inProgressFile = inProgressFile;
		}

		@Override
		public void close() throws IOException {
			if (zout == null)
				// already closed
				return;
			super.close();
			if (!inProgressFile.equals(outputFile))
				Files.move(inProgressFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		@Override
		public File getInProgressFile() {
			return inProgressFile;
		}

		@Override
		public File getDestinationFile() {
			return outputFile;
		}

		@Override
		public ModuleArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "ZipOutputStream is still open");
			return new ModuleArchiveInput.ApacheZipFileInput(outputFile);
		}
		
	}
	
	public abstract static class AbstractTarOutput implements ModuleArchiveOutput {
		
		protected TarArchiveOutputStream tout;
		
		private String currentEntry = null;
		private ByteArrayOutputStream currentOutput = null;

		public AbstractTarOutput(TarArchiveOutputStream tout) {
			this.tout = tout;
		}

		@Override
		public void putNextEntry(String name) throws IOException {
			Preconditions.checkState(currentEntry == null,
					"Called putNextEntry(%s) with an already open entry (%s)", name, currentEntry);
			this.currentEntry = name;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			Preconditions.checkNotNull(currentEntry, "Must putNextEntry before calling getOutputStream");
			Preconditions.checkState(currentOutput == null, "Can't call getOutputStream multiple times, must closeEntry and putNextEntry");
			currentOutput = new ByteArrayOutputStream();
			return currentOutput;
		}

		@Override
		public void closeEntry() throws IOException {
			Preconditions.checkNotNull(currentEntry, "Can't closeEntry because it was never opened");
			TarArchiveEntry entry = new TarArchiveEntry(currentEntry);
			if (currentOutput == null) {
				// assume it's a directory
				Preconditions.checkState(entry.isDirectory(),
						"Output stream never written for %s, but it's not a directory (doesn't end with '/')", currentEntry);
				tout.putArchiveEntry(entry);
			} else {
				entry.setSize(currentOutput.size());
				tout.putArchiveEntry(entry);
				currentOutput.writeTo(tout);
				tout.closeArchiveEntry();
			}
			currentOutput = null;
			currentEntry = null;
		}

		@Override
		public void close() throws IOException {
			if (tout == null)
				// already closed
				return;
			tout.close();
		}
		
	}
	
	/**
	 * UNIX Tarball (uncompressed) file output implementation
	 */
	public static class TarFileOutput extends AbstractTarOutput implements FileBacked {
		
		private File outputFile;
		private File inProgressFile;

		public TarFileOutput(File outputFile) throws IOException {
			this(outputFile, new File(outputFile.getAbsolutePath()+".tmp"));
		}

		public TarFileOutput(File outputFile, File inProgressFile) throws IOException {
			super(new TarArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(inProgressFile))));
			this.outputFile = outputFile;
			this.inProgressFile = inProgressFile;
		}

		@Override
		public String getName() {
			return outputFile.getAbsolutePath();
		}

		@Override
		public File getInProgressFile() {
			return inProgressFile;
		}

		@Override
		public File getDestinationFile() {
			return outputFile;
		}

		@Override
		public void close() throws IOException {
			if (tout == null)
				// already closed
				return;
			super.close();
			if (!inProgressFile.equals(outputFile))
				Files.move(inProgressFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		@Override
		public ModuleArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(tout == null, "ZipOutputStream is still open");
			return new ModuleArchiveInput.TarFileInput(outputFile);
		}
		
	}
	
	/**
	 * This version uses the newer Java NIO {@link FileSystem} implementation. I see no performance difference between
	 * this and the old ZipOutputStream implementation
	 */
	public static class ZipFileSystemOutput implements FileBacked {
		
		private Path inProgressPath;
		private Path destinationPath;
		
		private FileSystem fs;
		
		private Path currentPath;
		private OutputStream currentOutputStream;

		public ZipFileSystemOutput(Path path) throws IOException {
			destinationPath = path.toAbsolutePath();
			inProgressPath = Path.of(destinationPath.toString()+".tmp");
			
			URI uri = URI.create("jar:"+inProgressPath.toUri().toString());
			Map<String, String> env = Map.of("create", "true");
			fs = FileSystems.newFileSystem(uri, env);
		}

		@Override
		public void putNextEntry(String name) throws IOException {
			Preconditions.checkState(currentPath == null, "Never closed previous entry (%s)", currentPath);
			
			currentPath = fs.getPath(name);
			
			Path parent = currentPath.getParent();
			if (parent != null)
				Files.createDirectories(parent);
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			Preconditions.checkNotNull(currentPath, "Trying to getOutputStream without first calling initNewEntry");
			currentOutputStream = Files.newOutputStream(currentPath, StandardOpenOption.CREATE_NEW);
			return currentOutputStream;
		}

		@Override
		public void closeEntry() throws IOException {
			Preconditions.checkNotNull(currentOutputStream, "Called closeEntry() but no entry currently open?");
			currentOutputStream.close();
			currentPath = null;
			currentOutputStream = null;
		}

		@Override
		public void close() throws IOException {
			if (fs == null)
				// already closed
				return;
			fs.close();
			fs = null;
			if (!destinationPath.equals(inProgressPath))
				Files.move(inProgressPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
		}

		@Override
		public String getName() {
			return destinationPath.toString();
		}

		@Override
		public File getInProgressFile() {
			return inProgressPath.toFile();
		}

		@Override
		public File getDestinationFile() {
			return destinationPath.toFile();
		}

		@Override
		public ModuleArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(fs == null, "Output FileSystem is still open");
			return new ModuleArchiveInput.ZipFileSystemInput(destinationPath);
		}
		
	}

}
