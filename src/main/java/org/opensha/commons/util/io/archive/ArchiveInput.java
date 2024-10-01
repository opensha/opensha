package org.opensha.commons.util.io.archive;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.opensha.commons.data.Named;
import org.opensha.commons.util.modules.ModuleArchive;

import com.google.common.base.Preconditions;

/**
 * Input interface/abstraction layer for data stored as entries in an archive, e.g., for use with a {@link ModuleArchive}.
 * 
 * @see ArchiveOutput
 */
public interface ArchiveInput extends Named, Closeable {
	
	/**
	 * @param name the entry name
	 * @return true if this archive contains an entry with the given name
	 * @throws IOException
	 */
	public boolean hasEntry(String name) throws IOException;
	
	/**
	 * @param name the entry name
	 * @return true if this entry represents a directory (ends with {@link ArchiveOutput#SEPERATOR}, false otherwise
	 */
	public default boolean isDirecotry(String name) {
		return name.endsWith(ArchiveOutput.SEPERATOR);
	}
	
	/**
	 * Returns the {@link InputStream} for the given entry. Can check for existence first via {@link #hasEntry(String)},
	 * otherwise an exception may be thrown.
	 * 
	 * @param name
	 * @return input stream for the given entry
	 * @throws IOException if the entry doesn't exist, or another I/O exception occurs
	 */
	public InputStream getInputStream(String name) throws IOException;
	
	/**
	 * @return iterable view of all entries
	 * @throws IOException
	 */
	public default Iterable<String> getEntries() throws IOException {
		Stream<String> stream = entryStream();
		return () -> stream.iterator();
	}
	
	/**
	 * @return stream of all entries
	 * @throws IOException
	 */
	public Stream<String> entryStream() throws IOException;
	
	/**
	 * Interface for a {@link ArchiveInput} that is backed by an input file
	 */
	public interface FileBacked extends ArchiveInput {
		
		/**
		 * @return the input file for this archive
		 */
		public File getInputFile();

		@Override
		default String getName() {
			File inputFile = getInputFile();
			if (inputFile == null)
				return null+"";
			return getInputFile().getAbsolutePath();
		}
	}
	
	/**
	 * @param file
	 * @return the default {@link ArchiveInput} for the given file, using the file name extension
	 * @throws IOException
	 */
	public static ArchiveInput getDefaultInput(File file) throws IOException {
		if (file.isDirectory())
			return new DirectoryInput(file.toPath());
		String name = file.getName().toLowerCase();
		if (name.endsWith(".tar"))
			return new TarFileInput(file);
		return new ZipFileInput(file);
	}
	
	/**
	 * Simplest input, using the standard {@link java.util.zip.ZipFile} implementation
	 */
	public static class ZipFileInput implements FileBacked {
		
		private File inputFile;
		private java.util.zip.ZipFile zip;
		
		private java.util.zip.ZipEntry prevEntry;

		/**
		 * Zip file input for the given file
		 * 
		 * @param file input zip file to read
		 * @throws IOException
		 */
		public ZipFileInput(File file) throws IOException {
			this(new java.util.zip.ZipFile(file));
			this.inputFile = file;
		}

		/**
		 * Zip file input for an already opened {@link java.util.zip.ZipFile}. Closing this {@link ArchiveInput} will
		 * close the passed in zip file.
		 * 
		 * @param zip
		 */
		public ZipFileInput(java.util.zip.ZipFile zip) {
			this.zip = zip;
			this.inputFile = new File(zip.getName());
		}

		@Override
		public boolean hasEntry(String name) {
			return getEntry(name) != null;
		}
		
		private java.util.zip.ZipEntry getEntry(String name) {
			java.util.zip.ZipEntry cached = prevEntry;
			if (cached != null && cached.getName().equals(name))
				return cached;
			java.util.zip.ZipEntry entry = zip.getEntry(name);
			if (entry != null)
				prevEntry = entry;
			return entry;
		}

		@Override
		public InputStream getInputStream(String name) throws IOException {
			java.util.zip.ZipEntry entry = getEntry(name);
			return zip.getInputStream(entry);
		}

		@Override
		public Iterable<String> getEntries() throws IOException {
			Stream<String> stream = entryStream();
			return () -> stream.iterator();
		}

		@Override
		public Stream<String> entryStream() throws IOException {
			return zip.stream().map(java.util.zip.ZipEntry::getName);
		}

		@Override
		public String getName() {
			return zip.getName();
		}

		@Override
		public void close() throws IOException {
			zip.close();
		}

		@Override
		public File getInputFile() {
			return inputFile;
		}
		
	}
	
	public abstract class AbstractApacheZipInput implements ArchiveInput {
		
		private org.apache.commons.compress.archivers.zip.ZipFile zip;
		
		private org.apache.commons.compress.archivers.zip.ZipArchiveEntry prevEntry;
		
		public AbstractApacheZipInput(org.apache.commons.compress.archivers.zip.ZipFile zip) {
			this.zip = zip;
		}

		@Override
		public boolean hasEntry(String name) {
			return getEntry(name) != null;
		}
		
		org.apache.commons.compress.archivers.zip.ZipArchiveEntry getEntry(String name) {
			org.apache.commons.compress.archivers.zip.ZipArchiveEntry cached = prevEntry;
			if (cached != null && cached.getName().equals(name))
				return cached;
			org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry = zip.getEntry(name);
			if (entry != null)
				prevEntry = entry;
			return entry;
		}

		InputStream getRawInputStream(org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry) throws IOException {
			return zip.getRawInputStream(entry);
		}

		@Override
		public InputStream getInputStream(String name) throws IOException {
			return zip.getInputStream(getEntry(name));
		}
		
		private Spliterator<String> entryNameSpliterator() {
			return Spliterators.spliteratorUnknownSize(
			        new Iterator<String>() {
			            private final Enumeration<? extends 
			            		org.apache.commons.compress.archivers.zip.ZipArchiveEntry> entries = zip.getEntries();
			            
			            public boolean hasNext() { return entries.hasMoreElements(); }
			            public String next() { return entries.nextElement().getName(); }
			        }, Spliterator.ORDERED);
		}

		@Override
		public Stream<String> entryStream() throws IOException {
			return StreamSupport.stream(entryNameSpliterator(), false);
		}

		@Override
		public void close() throws IOException {
			zip.close();
		}
	}
	
	/**
	 * In-memory zip input
	 */
	public static class InMemoryZipInput extends AbstractApacheZipInput {

		/**
		 * In-memory zip input
		 * 
		 * @param byteChannel
		 * @throws IOException
		 */
		public InMemoryZipInput(SeekableByteChannel byteChannel) throws IOException {
			super(org.apache.commons.compress.archivers.zip.ZipFile.builder().setSeekableByteChannel(byteChannel).get());
		}

		@Override
		public String getName() {
			return "In Memory";
		}
		
	}
	
	/**
	 * This version uses that Apache Commons Compress library's {@link org.apache.commons.compress.archivers.zip.ZipFile} implementation
	 * 
	 * The primary benefit of this implementation is that entries can be directly copied to an
	 * {@link ArchiveOutput.ApacheZipFileOutput} without deflating and then reinflating, greatly reducing the time
	 * to copy from one archive to another.
	 * 
	 * The primary downside of this implementation is that it iterates over the entire file at the beginning to cache
	 * zip entries and their attributes, and this can be slow for enormous files.
	 */
	public static class ApacheZipFileInput extends AbstractApacheZipInput implements FileBacked {

		private File file;

		/**
		 * Reads from the given file
		 * 
		 * @param file input zip file to be read
		 * @throws IOException
		 */
		public ApacheZipFileInput(File file) throws IOException {
			super(org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(file).get());
			this.file = file;
		}

		@Override
		public File getInputFile() {
			return file;
		}
		
	}
	
	public abstract class AbstractTarInput implements ArchiveInput {
		
		private TarFile tar;

		private List<TarArchiveEntry> entries;
		private Map<String, TarArchiveEntry> entryMap;
		
		public AbstractTarInput(TarFile tar) {
			this.tar = tar;
			this.entries = tar.getEntries();
			this.entryMap = entries.stream().collect(Collectors.toMap(TarArchiveEntry::getName, entry -> entry));
		}

		@Override
		public boolean hasEntry(String name) {
			return entryMap.containsKey(name);
		}
		
		TarArchiveEntry getEntry(String name) {
			return entryMap.get(name);
		}

		@Override
		public InputStream getInputStream(String name) throws IOException {
			return tar.getInputStream(getEntry(name));
		}

		@Override
		public Stream<String> entryStream() throws IOException {
			return entryMap.keySet().stream();
		}

		@Override
		public void close() throws IOException {
			tar.close();
		}
	}
	
	/**
	 * UNIX Tarball (uncompressed) file input
	 */
	public class TarFileInput extends AbstractTarInput implements FileBacked {

		private File tarFile;

		public TarFileInput(File tarFile) throws IOException {
			super(new TarFile(tarFile));
			this.tarFile = tarFile;
		}

		@Override
		public File getInputFile() {
			return tarFile;
		}
		
	}
	
	public static abstract class AbstractFileSystemInput implements ArchiveInput {
		
		protected FileSystem fs;
		private Path upstream;
		
		private String prevName;
		private Path prevPath;
		
		public AbstractFileSystemInput(FileSystem fs) {
			this.fs = fs;
		}
		
		public AbstractFileSystemInput(Path upstream) {
			this.upstream = upstream;
		}

		@Override
		public boolean hasEntry(String name) throws IOException {
			// will be null if it doesn't exist
			return getPath(name) != null;
		}
		
		private Path getPath(String name) {
			Path cached = prevPath;
			if (cached != null && prevName.equals(name))
				return cached;
			Path path;
			if (upstream != null)
				path = upstream.resolve(name);
			else
				path = fs.getPath(name);
			if (!Files.exists(path))
				return null;
			prevName = name;
			prevPath = path;
			return path;
		}

		@Override
		public InputStream getInputStream(String name) throws IOException {
			Path path = getPath(name);
			return new BufferedInputStream(Files.newInputStream(path));
		}

		@Override
		public Stream<String> entryStream() throws IOException {
			Path root;
			int trimSize;
			if (upstream != null) {
				root = upstream;
				String rootStr = root.toString();
				trimSize = rootStr.length() + (!rootStr.endsWith("/") || !rootStr.endsWith("\\") ? 1 : 0);
			} else {
				root = fs.getRootDirectories().iterator().next();
				trimSize = 1;
			}
			return Files.walk(root)
					.filter(P->!Files.isDirectory(P)) // no directories
					.map(Path::toString)
					.map(S->S.substring(trimSize)) // strip out the preceding /
					.filter(S->!S.isBlank()); // skip empty (will include just '/' which we just turned into an empty string)
		}

		@Override
		public void close() throws IOException {
			if (fs == null)
				return;
			fs.close();
			fs = null;
		}
	}
	
	/**
	 * This version uses the newer Java NIO {@link FileSystem} implementation. I see no performance difference between
	 * this and the old ZipFile implementation
	 */
	public static class ZipFileSystemInput extends AbstractFileSystemInput implements FileBacked {
		
		private Path zipPath;

		public ZipFileSystemInput(Path path) throws IOException {
			super(initFS(path));
			this.zipPath = path.toAbsolutePath();
		}
		
		private static FileSystem initFS(Path path) throws IOException {
			URI uri = URI.create("jar:"+path.toUri().toString());
			Map<String, String> env = Map.of("create", "false");
			return FileSystems.newFileSystem(uri, env);
		}

		@Override
		public String getName() {
			return zipPath.toString();
		}

		@Override
		public File getInputFile() {
			return zipPath.toFile();
		}
		
	}
	
	public static class DirectoryInput extends AbstractFileSystemInput implements FileBacked {
		private Path dirPath;

		public DirectoryInput(Path path) throws IOException {
			super(checkPath(path));
			this.dirPath = path.toAbsolutePath();
		}
		
		private static Path checkPath(Path path) throws IOException {
			Preconditions.checkState(Files.exists(path) && Files.isDirectory(path),
						"Path already exists and is not a directory: %s", path);
			return path.toAbsolutePath();
		}
		
		@Override
		public String getName() {
			return dirPath.toString();
		}

		@Override
		public File getInputFile() {
			return dirPath.toFile();
		}

		@Override
		public void close() throws IOException {
			// do not try to close the OS filesystem, just set to null
			fs = null;
		}
	}

}
