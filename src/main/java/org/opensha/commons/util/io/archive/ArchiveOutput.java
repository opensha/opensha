package org.opensha.commons.util.io.archive;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.Named;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.commons.util.modules.ModuleArchive;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/**
 * Output interface/abstraction layer for data stored as entries in an archive, e.g., for use with a {@link ModuleArchive}.
 * 
 * <p>To write data, callers much initialize a new entry via {@link #putNextEntry(String)}, write the data (if it's not
 * just a directory) via {@link #getOutputStream()}, then close the entry via {@link #closeEntry()}. Once everything is
 * written, callers must call {@link #close()} to close the archive.
 * 
 * @see ArchiveInput
 */
public interface ArchiveOutput extends Closeable, Named {
	
	public static final char SEPERATOR_CHAR = '/';
	public static final String SEPERATOR = ""+SEPERATOR_CHAR;
	
	/**
	 * Begins an entry with the given name. The separator for directories is a forward slash (see {@link #SEPERATOR}).
	 * 
	 * <p>To write a file, call this with the file name, write the contents to the result of {@link #getOutputStream()},
	 * then close the entry via {@link #closeEntry()}.
	 * 
	 * <p>To write a directory, call this with a name that ends in a forward slash (see {@link #SEPERATOR}) and then
	 * close the entry via {@link #closeEntry()}.
	 * 
	 * @param name
	 * @throws IOException
	 */
	public void putNextEntry(String name) throws IOException;
	
	/**
	 * Gets an {@link OutputStream} for the currently entry. Must call {@link #putNextEntry(String)} first, and must
	 * only call this once per entry. After writing to the stream, callers must then call {@link #closeEntry()}.
	 * 
	 * <p>This does not usually need to be wrapped in a {@link BufferedOutputStream} as all default implementations already
	 * use buffers.
	 * 
	 * <p><b>IMPORTANT: never close this output stream</b> as it may be reused for multiple entries, depending on the
	 * implementation. Instead, call {@link OutputStream#flush()} when you have finished writing.
	 * 
	 * @return {@link OutputStream} for the currently entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;
	
	/**
	 * Closes the current entry.
	 * 
	 * <p>Implementing classes will automatically flush the {@link OutputStream}, but if you wrap it in another layer
	 * (e.g., a {@link Writer}), be sure to flush that wrapper before calling.
	 * 
	 * @throws IOException
	 */
	public void closeEntry() throws IOException;
	
	/**
	 * Once an archive has been fully written and closed via {@link #close()}, this can be used to get a
	 * {@link ArchiveInput} from the completed output.
	 * 
	 * @return {@link ArchiveInput} that can be used to read the fully-written and closed output
	 * @throws IOException
	 */
	public ArchiveInput getCompletedInput() throws IOException;
	
	/**
	 * Transfer an entry of the given name from the given {@link ArchiveInput} to this {@link ArchiveOutput}.
	 * 
	 * <p>This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}, do not call them separately.
	 * 
	 * <p>The default implementation simply opens the entry, gets the input stream, calls
	 * {@link InputStream#transferTo(OutputStream)}, and closes the entry.
	 * 
	 * <p>This may be overridden in specific implementations to provide better performance. For example,
	 * {@link ArchiveOutput.ApacheZipFileOutput} and {@link ArchiveOutput.InMemoryZipOutput} can transfer data from their
	 * respective {@link ArchiveInput} implementations without unnecessary inflation and deflation of data during the
	 * transfer. For this reason, it is recommended to use the Apache zip file implementations when transferring many
	 * files from an {@link ArchiveInput} to an {@link ArchiveOutput}.
	 * 
	 * @param input {@link ArchiveInput} from which to fetch the entry
	 * @param name the name of the entry to copy
	 * @throws IOException
	 */
	public default void transferFrom(ArchiveInput input, String name) throws IOException {
		transferFrom(input, name, name);
	}
	
	/**
	 * Transfer an entry of the given name from the given {@link ArchiveInput} to this {@link ArchiveOutput},
	 * possibly renaming the entry.
	 * 
	 * <p>This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}, do not call them separately.
	 * 
	 * <p>The default implementation simply opens the entry, gets the input stream, calls
	 * {@link InputStream#transferTo(OutputStream)}, and closes the entry.
	 * 
	 * <p>This may be overridden in specific implementations to provide better performance. For example,
	 * {@link ArchiveOutput.ApacheZipFileOutput} and {@link ArchiveOutput.InMemoryZipOutput} can transfer data from their
	 * respective {@link ArchiveInput} implementations without unnecessary inflation and deflation of data during the
	 * transfer. For this reason, it is recommended to use the Apache zip file implementations when transferring many
	 * files from an {@link ArchiveInput} to an {@link ArchiveOutput}.
	 * 
	 * @param input {@link ArchiveInput} from which to fetch the entry
	 * @param sourceName the name of the source entry in the {@link ArchiveInput}
	 * @param destName the name of the entry to write in the {@link ArchiveOutput}
	 * @throws IOException
	 */
	public default void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
		transferFrom(input.getInputStream(sourceName), destName);
	}
	
	/**
	 * Create an entry of the given name with contents from the given input stream.
	 * 
	 * <p>This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}, do not call them separately.
	 * 
	 * <p>The default implementation simply calls:
	 * 
	 * <pre>
	 * putNextEntry(name);
	 * is.transferTo(getOutputStream());
	 * closeEntry();
	 * </pre>
	 * 
	 * @param input {@link InputStream} to transfer to this {@link ArchiveOutput}
	 * @param name the name of the entry to create
	 * @throws IOException
	 */
	public default void transferFrom(InputStream is, String name) throws IOException {
		putNextEntry(name);
		is.transferTo(getOutputStream());
		closeEntry();
	}
	
	/**
	 * {@link ArchiveOutput} that is backed by a {@link File}, possibly with a temporary file during writing
	 * (see {@link #getInProgressFile()}) that is moved to a final file (see {@link #getDestinationFile()}) when
	 * completed.
	 */
	public interface FileBacked extends ArchiveOutput {
		/**
		 * This returns the path to the output file representing this archive while it is being written (and before
		 * {@link #close()} is called), and may (but doesn't need to) differ from the eventual destination file
		 * (see {@link #getDestinationFile()}).
		 * 
		 * @return the output file while results are being written
		 */
		public File getInProgressFile();
		
		/**
		 * This returns the final output file after writing has completed (by calling {@link #close())}. This may
		 * (but won't necessarily) differ from {@link #getInProgressFile()}.
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
	
	public static File getDefaultInProgressFile(File outputFile) {
		return new File(outputFile.getParentFile(), outputFile.getName()+".tmp");
	}
	
	/**
	 * Gets the default {@link ArchiveOutput} implementation for the given file, using the filename.
	 * 
	 * @param outputFile output file to be written
	 * @return an {@link ArchiveOutput} for the given file
	 * @throws IOException
	 */
	public static ArchiveOutput getDefaultOutput(File outputFile) throws IOException {
		return getDefaultOutput(outputFile, null);
	}
	
	/**
	 * Gets the default {@link ArchiveOutput} implementation for the given file, using the filename as well
	 * as the input implementation to see if there is a compatible output. For example, if the input uses the Apache
	 * Zip library rather than the standard Java library, the output will as well so that transfer operations need not
	 * re-inflate and then deflate each entry.
	 * 
	 * @param outputFile output file to be written
	 * @param input if non-null, the returned {@link ArchiveOutput} will be chosen to maximize compatibility with the
	 * supplied {@link ArchiveInput} (so long as the file extension does not require another implementation)
	 * @return an {@link ArchiveOutput} for the given file
	 * @throws IOException
	 */
	public static ArchiveOutput getDefaultOutput(File outputFile, ArchiveInput input) throws IOException {
		String name = outputFile.getName().toLowerCase();
		if (outputFile.isDirectory() || (!outputFile.exists() && name.endsWith(File.separator)))
			return new DirectoryOutput(outputFile.toPath());
		if (name.endsWith(".tar")) {
			return new TarFileOutput(outputFile);
		} else if (name.endsWith(".zip")) {
			if (input instanceof ArchiveInput.AbstractApacheZipInput)
				return new ApacheZipFileOutput(outputFile);
			if (input instanceof ArchiveInput.ZipFileSystemInput)
				return new ZipFileSystemOutput(outputFile.toPath());
			return new ZipFileOutput(outputFile);
		}
		// unknown extension, assume it's zip but check if the input is tar
		if (input instanceof ArchiveInput.TarFileInput)
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

		/**
		 * Initializes a standard Java {@link ZipOutputStream}, first writing to the given file with <code>.tmp</code>
		 * appended, then moving to the the given file when {@link #close()} is called.
		 *  
		 * @param outputFile final destination output file
		 * @throws IOException
		 */
		public ZipFileOutput(File outputFile) throws IOException {
			this(outputFile, getDefaultInProgressFile(outputFile));
		}

		/**
		 * Initializes a standard Java {@link ZipOutputStream}, first writing to the given <code>outputFile</code>,
		 * then moving to the <code>inProgressFile</code> when {@link #close()} is called (if different from <code>outputFile</code>).
		 * 
		 * @param outputFile final destination output file
		 * @param inProgressFile output file used while writing
		 * @throws IOException
		 */
		public ZipFileOutput(File outputFile, File inProgressFile) throws IOException {
			this.outputFile = outputFile;
			this.inProgressFile = inProgressFile;
			zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(inProgressFile)));
		}
		
		/**
		 * Uses the provided {@link ZipOutputStream}. Note that it will be closed when {@link ZipFileOutput#close()} is called.
		 * @param zout pre-allocated {@link ZipOutputStream}
		 */
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "ZipOutputStream is still open");
			return new ArchiveInput.ZipFileInput(outputFile);
		}
		
	}
	
	public static abstract class AbstractApacheZipOutput implements ArchiveOutput {
		
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
		public void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
			if (input instanceof ArchiveInput.AbstractApacheZipInput) {
				ArchiveInput.AbstractApacheZipInput apache = (ArchiveInput.AbstractApacheZipInput)input;
				rawTransferApache(apache, sourceName, destName);
			} else {
				ArchiveOutput.super.transferFrom(input, sourceName, destName);
			}
		}
		
		protected void rawTransferApache(ArchiveInput.AbstractApacheZipInput apache, String sourceName, String destName) throws IOException {
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

		/**
		 * Default constructor that allocates a growable buffer with an intial size of 50 MB
		 * 
		 * @param compressed if true, entries will be compressed, otherwise uncompressed
		 */
		public InMemoryZipOutput(boolean compressed) {
			this(compressed, 1024*1024*50); // 50 MB
		}

		/**
		 * @param compressed if true, entries will be compressed, otherwise uncompressed
		 * @param initialSize initial size of the (growable) buffer, in bytes
		 */
		public InMemoryZipOutput(boolean compressed, int initialSize) {
			this(compressed, new CopyAvoidantInMemorySeekableByteChannel(initialSize));
		}
		
		/**
		 * Use this if you already have a buffer in memory; be sure that it is not closed (and will not become closed
		 * when the garbage collector calls close on any orphaned objects that used it).
		 * 
		 * @param compressed if true, entries will be compressed, otherwise uncompressed
		 * @param byteChannel already allocated data store
		 */
		public InMemoryZipOutput(boolean compressed, CopyAvoidantInMemorySeekableByteChannel byteChannel) {
			super(new ZipArchiveOutputStream(byteChannel), compressed);
			this.compressed = compressed;
			this.byteChannel = byteChannel;
//			System.out.println("Initial size: "+(float)(byteChannel.size()/(1024d*1024d))+" mb");
		}

		@Override
		public String getName() {
			return "In Memory ("+(compressed ? "zipped" : "uncompressed")+")";
		}

		@Override
		public void putNextEntry(String name) throws IOException {
//			System.out.println("STARTING WRITE: "+name);
			super.putNextEntry(name);
		}

		@Override
		public void closeEntry() throws IOException {
			super.closeEntry();
//			System.out.println("Current size: "+(float)(byteChannel.size()/(1024d*1024d))+" mb");
		}

		@Override
		public ArchiveInput.InMemoryZipInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "Not closed?");
			CopyAvoidantInMemorySeekableByteChannel copy = byteChannel.copy();
			copy.position(0l);
			return new ArchiveInput.InMemoryZipInput(copy);
		}
		
	}
	
	/**
	 * Zip file implementation using the Apeche Common I/O library; this can be used to efficiently transfer data
	 * from an {@link ArchiveInput.ApacheZipFileInput} without inflating and deflating.
	 */
	public static class ApacheZipFileOutput extends AbstractApacheZipOutput implements FileBacked {
		
		private File outputFile;
		private File inProgressFile;

		/**
		 * Initializes an Apache {@link ZipArchiveOutputStream}, first writing to the given file with <code>.tmp</code>
		 * appended, then moving to the the given file when {@link #close()} is called.
		 * 
		 * @param outputFile final destination output file
		 * @throws IOException
		 */
		public ApacheZipFileOutput(File outputFile) throws IOException {
			this(outputFile, getDefaultInProgressFile(outputFile));
		}

		/**
		 * Initializes an Apache {@link ZipArchiveOutputStream}, first writing to the given <code>outputFile</code>,
		 * then moving to the <code>inProgressFile</code> when {@link #close()} is called (if different from <code>outputFile</code>).
		 * 
		 * @param outputFile final destination output file
		 * @param inProgressFile output file used while writing
		 * @throws IOException
		 */
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "ZipOutputStream is still open");
			return new ArchiveInput.ApacheZipFileInput(outputFile);
		}
		
	}
	
	/**
	 * Asynchronous zip file writer where zipping operations are done to memory as the stream is written, and block when
	 * {@link #closeEntry()} is called. Writing is done asynchronously, and contents are written in order.
	 * 
	 * <p>Memory requirements of this are the zipped size of the two largest files.
	 * 
	 * <p>See {@link ParallelZipFileOutput} if you want increased parallelism at the expense of potentially-significant
	 * memory usage.
	 */
	public static class AsynchronousZipFileOutput extends ApacheZipFileOutput {
		
		private CopyAvoidantInMemorySeekableByteChannel zippingBuffer;
		private CopyAvoidantInMemorySeekableByteChannel writingBuffer;
		
		private String currentEntry;
		private InMemoryZipOutput currentOutput;
		
		private CompletableFuture<?> writeFuture;
		
		public AsynchronousZipFileOutput(File outputFile) throws IOException {
			super(outputFile);
			init();
		}
		
		public AsynchronousZipFileOutput(File outputFile, File inProgressFile) throws IOException {
			super(outputFile, inProgressFile);
			init();
		}
		
		private void init() {
			zippingBuffer = new CopyAvoidantInMemorySeekableByteChannel(1024*1024*5);
			zippingBuffer.setCloseable(false);
			writingBuffer = new CopyAvoidantInMemorySeekableByteChannel(1024*1024*5);
			writingBuffer.setCloseable(false);
		}
		
		private void startAsyncWrite() throws IOException {
			if (writeFuture != null)
				writeFuture.join();
			Preconditions.checkNotNull(currentEntry);
//			writeFuture = zipper.rawTransferFuture(this);
			String entryName = currentEntry;
			if (currentOutput == null) {
				// nothing written, presumably a directory
				super.putNextEntry(entryName);
				super.closeEntry();
				writeFuture = CompletableFuture.completedFuture(null);
			} else {
				currentOutput.close();
				zippingBuffer.position(0l); // reset to read from the beginning
				ArchiveInput.InMemoryZipInput input = new ArchiveInput.InMemoryZipInput(zippingBuffer);
				writeFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							rawTransferApache(input, entryName, entryName);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				});
			}
			currentEntry = null;
			currentOutput = null;
			// swap the buffers as the current compressed data buffer is being written to disk
			// this buffer is now available (finished writing via writeFuture.join() at the beginning of this method)
			CopyAvoidantInMemorySeekableByteChannel tmpBuffer = writingBuffer;
			// the one that was zipping is now in the process of writing
			writingBuffer = zippingBuffer;
			zippingBuffer = tmpBuffer; // can be for reused
		}

		@Override
		public synchronized void putNextEntry(String name) throws IOException {
			Preconditions.checkState(currentEntry == null);
			Preconditions.checkState(currentOutput == null);
			zippingBuffer.truncate(0l); // reset to the beginning
			currentEntry = name;
		}

		@Override
		public synchronized OutputStream getOutputStream() throws IOException {
			Preconditions.checkNotNull(currentEntry, "Called getOutputStream() without first calling putNextEntry()");
			Preconditions.checkState(currentOutput == null, "Can't call getOutputStream() twice on the same entry");
			currentOutput = new InMemoryZipOutput(true, zippingBuffer);
			currentOutput.putNextEntry(currentEntry);
			return currentOutput.getOutputStream();
		}

		@Override
		public synchronized void closeEntry() throws IOException {
			Preconditions.checkNotNull(currentEntry, "Called closeEntry() without first calling putNextEntry()");
			if (currentOutput != null) // null if it's a directory
				currentOutput.closeEntry();
			startAsyncWrite();
		}

		@Override
		public synchronized void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
			Preconditions.checkState(currentEntry == null && currentOutput == null,
					"Called transferFrom(..) when another entry was open?");
			putNextEntry(destName);
			currentOutput = new InMemoryZipOutput(true, zippingBuffer);
			currentOutput.transferFrom(input, sourceName, destName);
			startAsyncWrite();
		}

		@Override
		public void close() throws IOException {
			if (writeFuture != null)
				writeFuture.join();
			writeFuture = null;
			zippingBuffer = null;
			writingBuffer = null;
			super.close();
		}
	}
	
	/**
	 * Parallel zip file implementation where contents are zipped in memory in parallel, then written to the output
	 * file as they roll off the line. More throughput can be achieved if preserving the order is not important.
	 * 
	 * <p>This can require significant memory and no resources are released until it is closed. Contents are first
	 * written to an uncompressed in-memory buffer, zipped in parallel to additional in-memory-buffers, and finally
	 * written asynchronously. Be sure to have at least <code>threads x (largest uncompressed file size + largest
	 * compressed file size)</code> available.
	 * 
	 * <p>If only 1 parallel thread is needed or you are I/O bound, {@link AsynchronousZipFileOutput} may be faster and
	 * is significantly more memory efficient.
	 */
	public static class ParallelZipFileOutput extends ApacheZipFileOutput {
		
		private static final boolean D = false;

		private ArrayDeque<CompletableFuture<AsynchronousApacheZipper>> zipFutures;
		private ArrayDeque<AsynchronousApacheZipper> zippers;
		private CompletableFuture<Void> writeFuture;
		private boolean preserveOrder;
		private int maxThreads;
		private int threadsInitialized;
		private CopyAvoidantInMemorySeekableByteChannel secondaryZipBuffer;
		
		private boolean trackBlockingTimes;
		private Stopwatch overallWatch = null;
		private Stopwatch blockingWriteWatch = null;
		private Stopwatch blockingZipWatch = null;
		
		private Runnable postProcessRun;
		private ExecutorService postProcessExec = ExecutorUtils.singleTaskRejectingExecutor();
		
		private AsynchronousApacheZipper currentZipper;

		/**
		 * Parallel zip file output with the given number of parallel workers; contents will be written out in the order
		 * received.
		 * 
		 * <p>Contents are first written to the given file with <code>.tmp</code> appended, then moved to the the
		 * given file when {@link #close()} is called.
		 * 
		 * @param outputFile final destination output file
		 * @param threads the maximum number of parallel zipping threads
		 * @throws IOException
		 */
		public ParallelZipFileOutput(File outputFile, int threads) throws IOException {
			this(outputFile, threads, true);
		}

		/**
		 * Parallel zip file output with the given number of parallel workers. If <code>preserveOrder == true</code>,
		 * contents will be written out in the order received, otherwise they will be written as they complete for
		 * maximum parallelism.
		 * 
		 * @param outputFile final destination output file
		 * @param threads the maximum number of parallel zipping threads
		 * @param preserveOrder if true, entries will be written in the order in which they are received (regardless of
		 * which zipping operations finish first; otherwise, entries will be written in the order at which they complete
		 * zipping for maximum parallelism.
		 * @throws IOException
		 */
		public ParallelZipFileOutput(File outputFile, int threads, boolean preserveOrder) throws IOException {
			super(outputFile);
			init(threads, preserveOrder);
		}
		
		/**
		 * Parallel zip file output with the given number of parallel workers. If <code>preserveOrder == true</code>,
		 * contents will be written out in the order received, otherwise they will be written as they complete for
		 * maximum parallelism.
		 * 
		 * <p>Contents are first written to the given <code>outputFile</code>, then moved to the
		 * <code>inProgressFile</code> when {@link #close()} is called (if different from <code>outputFile</code>).
		 * 
		 * @param outputFile final destination output file
		 * @param inProgressFile output file used while writing
		 * @param threads the maximum number of parallel zipping threads
		 * @param preserveOrder if true, entries will be written in the order in which they are received (regardless of
		 * which zipping operations finish first; otherwise, entries will be written in the order at which they complete
		 * zipping for maximum parallelism.
		 * @throws IOException
		 */
		public ParallelZipFileOutput(File outputFile, File inProgressFile, int threads, boolean preserveOrder) throws IOException {
			super(outputFile, inProgressFile);
			init(threads, preserveOrder);
		}
		
		private void init(int threads, boolean preserveOrder) {
			this.preserveOrder = preserveOrder;
			Preconditions.checkState(threads > 0, "Must supply at least 1 thread");
			maxThreads = threads;
			threadsInitialized = 0;
			zipFutures = new ArrayDeque<>(threads);
			zippers = new ArrayDeque<>(threads);
			postProcessRun = new Runnable() {
				
				@Override
				public void run() {
					try {
						ParallelZipFileOutput.this.processZipFutures();
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			};
		}
		
		/**
		 * Enables or disables tracking of blocking times; if enabled, the overall clock (used to compute fractional blocking times)
		 * will be started immediately. Get stats with {@link #getBlockingTimeStats()}
		 * @param track
		 */
		public void setTrackBlockingTimes(boolean track) {
			if (this.trackBlockingTimes) {
				overallWatch.stop();
				blockingWriteWatch.stop();
				blockingWriteWatch.stop();
				blockingZipWatch.stop();
			}
			if (track) {
				trackBlockingTimes = true;
				overallWatch = Stopwatch.createStarted();
				blockingWriteWatch = Stopwatch.createUnstarted();
				blockingZipWatch = Stopwatch.createUnstarted();
			} else {
				trackBlockingTimes = false;
				overallWatch = null;
				blockingWriteWatch = null;
				blockingZipWatch = null;
			}
		}
		
		private static final DecimalFormat pDF = new DecimalFormat("0.#%");
		public String getBlockingTimeStats() {
			Preconditions.checkState(trackBlockingTimes);
			double totSecs = overallWatch.elapsed(TimeUnit.SECONDS)/1000d;
			double writeSecs = blockingWriteWatch.elapsed(TimeUnit.SECONDS)/1000d;
			double zipSecs = blockingZipWatch.elapsed(TimeUnit.SECONDS)/1000d;
			return pDF.format(writeSecs/totSecs)+" blocking writes, "+pDF.format(zipSecs/totSecs)+" blocking zips, "
					+threadsInitialized+"/"+maxThreads+" workers initialized";
		}
		
		/**
		 * This blocks until all pending zip and write operations are completed
		 * @throws IOException
		 */
		private synchronized void joinAllWriters() throws IOException {
			while (!zipFutures.isEmpty()) {
				AsynchronousApacheZipper zip = zipFutures.remove().join();
				transferZippedData(zip);
			}
			if (writeFuture != null) {
				if (trackBlockingTimes) blockingWriteWatch.start();
				writeFuture.join();
				if (trackBlockingTimes) blockingWriteWatch.stop();
				writeFuture = null;
			}
		}
		
		/**
		 * This looks for finished zippers to start writing, but only if we're not currently writing. This should
		 * always be a near-instantaneous operation (it does not block).
		 * 
		 * @throws IOException
		 */
		private synchronized void processZipFutures() throws IOException {
			if (writeFuture != null && !writeFuture.isDone())
				return;
			for (CompletableFuture<AsynchronousApacheZipper> peek : zipFutures) {
				if (writeFuture != null && !writeFuture.isDone())
					break;
				if (peek.isDone()) {
					Preconditions.checkState(zipFutures.remove(peek));
					AsynchronousApacheZipper zip = peek.join();
					transferZippedData(zip);
					break;
				} else if (preserveOrder) {
					break;
				}
			}
		}
		 
		/*
		 * Begins the data transfer for the given zipper asynchronously, and populates writeFuture. If a writeFuture
		 * already exists, it is joined first and the associated zipper is added back to the zipper queue
		 * 
		 * Externally synchronized
		 */
		private synchronized void transferZippedData(AsynchronousApacheZipper zipper) throws IOException {
			if (writeFuture != null) {
				if (trackBlockingTimes) blockingWriteWatch.start();
				writeFuture.join();
				if (trackBlockingTimes) blockingWriteWatch.stop();
			}
			Preconditions.checkState(zipper.hasEntry());
			if (!zipper.hasEntryData()) {
				// empty, probably just a directory
				super.putNextEntry(zipper.getEntryDestName());
				super.closeEntry();
				writeFuture = CompletableFuture.completedFuture(null);
				zippers.add(zipper);
				// see if there's another one ready
				processZipFutures();
			} else {
				writeFuture = zipper.rawTransferFuture(this);
				// make the zipper available for zipping
				secondaryZipBuffer = zipper.swapCompressedDataBuffer(secondaryZipBuffer);
				zippers.add(zipper);
				// when done writing this one, start the next (if ready)
				writeFuture.thenRunAsync(postProcessRun, postProcessExec);
			}
		}

		@Override
		public synchronized void close() throws IOException {
			joinAllWriters();
			postProcessExec.shutdown();
			Preconditions.checkState(zipFutures.isEmpty());
			Preconditions.checkState(writeFuture == null);
			zipFutures = null;
			zippers = null;
			if (trackBlockingTimes) {
				if (overallWatch.isRunning())
					overallWatch.stop();
				if (blockingWriteWatch.isRunning())
					blockingWriteWatch.stop();
				if (blockingZipWatch.isRunning())
					blockingZipWatch.stop();
			}
			super.close();
		}
		
		// synchronized externally
		private void prepareZipper() throws IOException {
			processZipFutures();
			Preconditions.checkState(currentZipper == null);
			// get a zipper
			// first just try to reuse one that's already dormant
			currentZipper = zippers.poll();
			if (currentZipper == null) {
				// none ready at the moment
				if (threadsInitialized < maxThreads) {
					// can build a new one
					currentZipper = new AsynchronousApacheZipper();
					threadsInitialized++;
				} else {
					// block until one is ready
					Preconditions.checkState(!zipFutures.isEmpty());
					CompletableFuture<AsynchronousApacheZipper> future = zipFutures.remove();
					if (trackBlockingTimes) blockingZipWatch.start();
					AsynchronousApacheZipper zip = future.join();
					if (trackBlockingTimes) blockingZipWatch.stop();
					transferZippedData(zip); // this populates writeFuture and adds a zipper back to the queue
					Preconditions.checkNotNull(writeFuture);
					currentZipper = zippers.remove();
				}
			}
			currentZipper.reset();
		}

		@Override
		public synchronized void putNextEntry(String name) throws IOException {
			if (D) System.out.println("putNextEntry("+name+")");
			prepareZipper();
			currentZipper.putNextEntry(name);
		}

		@Override
		public synchronized OutputStream getOutputStream() throws IOException {
			if (D) System.out.println("getOutputStream()");
			Preconditions.checkNotNull(currentZipper);
			return currentZipper.getOutputStream();
		}

		@Override
		public synchronized void closeEntry() throws IOException {
			if (D) System.out.println("closeEntry()");
			Preconditions.checkNotNull(currentZipper);
			CompletableFuture<AsynchronousApacheZipper> future = currentZipper.closeEntry();
			future.thenRunAsync(postProcessRun, postProcessExec);
			currentZipper = null;
			zipFutures.add(future);
		}

		@Override
		public synchronized void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
			if (D) System.out.println("transferFrom("+input.getName()+", "+sourceName+", "+destName+")");
			prepareZipper();
			CompletableFuture<AsynchronousApacheZipper> future = currentZipper.transferFrom(input, sourceName, destName);
			future.thenRunAsync(postProcessRun, postProcessExec);
			currentZipper = null;
			zipFutures.add(future);
		}
		
	}
	
	public abstract static class AbstractTarOutput implements ArchiveOutput {
		
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
			currentOutput = new ByteArrayOutputStream(1024*1024*5); // 5 MB
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
	 * UNIX Tarball (uncompressed) file output implementation. Data are written in memory first because the size must
	 * be known prior to writing, then flushed to disk.
	 */
	public static class TarFileOutput extends AbstractTarOutput implements FileBacked {
		
		private File outputFile;
		private File inProgressFile;

		/**
		 * Initializes an Apache {@link TarArchiveOutputStream}, first writing to the given file with <code>.tmp</code>
		 * appended, then moving to the the given file when {@link #close()} is called.
		 * 
		 * @param outputFile final destination output file
		 * @throws IOException
		 */
		public TarFileOutput(File outputFile) throws IOException {
			this(outputFile, new File(outputFile.getAbsolutePath()+".tmp"));
		}

		/**
		 * Initializes an Apache {@link TarArchiveOutputStream}, first writing to the given <code>outputFile</code>,
		 * then moving to the <code>inProgressFile</code> when {@link #close()} is called (if different from <code>outputFile</code>).
		 * 
		 * @param outputFile final destination output file
		 * @param inProgressFile output file used while writing
		 * @throws IOException
		 */
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(tout == null, "ZipOutputStream is still open");
			return new ArchiveInput.TarFileInput(outputFile);
		}
		
	}
	
	public static abstract class AbstractFileSystemOutput implements ArchiveOutput {
		
		protected FileSystem fs;
		protected Path upstreamPath;
		
		private Path currentPath;
		private OutputStream currentOutputStream;

		public AbstractFileSystemOutput(FileSystem fs) {
			this.fs = fs;
		}
		
		public AbstractFileSystemOutput(Path upstreamPath) {
			this.upstreamPath = upstreamPath;
		}
		
		@Override
		public void putNextEntry(String name) throws IOException {
			Preconditions.checkState(currentPath == null, "Never closed previous entry (%s)", currentPath);
			
			if (upstreamPath == null)
				currentPath = fs.getPath(name);
			else
				currentPath = upstreamPath.resolve(name);
			
			Path parent = currentPath.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			if (name.endsWith(SEPERATOR)) {
				if (Files.exists(currentPath))
					Preconditions.checkState(Files.isDirectory(currentPath),
							"Cannot create directory '%s' because path already exists and isn't a directory: %s",
							name, currentPath);
				else
					Files.createDirectory(currentPath);
			}
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			Preconditions.checkNotNull(currentPath, "Trying to getOutputStream without first calling initNewEntry");
			currentOutputStream = new BufferedOutputStream(Files.newOutputStream(currentPath,
					fs == null ? StandardOpenOption.CREATE : StandardOpenOption.CREATE_NEW));
			return currentOutputStream;
		}

		@Override
		public void closeEntry() throws IOException {
			Preconditions.checkNotNull(currentPath, "Trying to closeEntry without first calling initNewEntry");
			if (currentOutputStream == null) {
				Preconditions.checkState(Files.isDirectory(currentPath),
						"Called closeEntry() but no output streams currently open and not a directory; currentPath=%s", currentPath);
			} else {
				currentOutputStream.close();
			}
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
		}
		
	}
	
	/**
	 * This version uses the newer Java NIO {@link FileSystem} implementation. I see no performance difference between
	 * this and the old ZipOutputStream implementation
	 */
	public static class ZipFileSystemOutput extends AbstractFileSystemOutput implements FileBacked {
		
		private Path inProgressPath;
		private Path destinationPath;

		public ZipFileSystemOutput(Path path) throws IOException {
			this(path, Path.of(path.toString()+".tmp"));
		}

		public ZipFileSystemOutput(Path destinationPath, Path inProgressPath) throws IOException {
			super(initFS(inProgressPath));
			this.destinationPath = destinationPath.toAbsolutePath();
			this.inProgressPath = inProgressPath.toAbsolutePath();
		}

		private static FileSystem initFS(Path path) throws IOException {
			URI uri = URI.create("jar:"+path.toUri().toString());
			Map<String, String> env = Map.of("create", "true");
			return FileSystems.newFileSystem(uri, env);
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(fs == null, "Output FileSystem is still open");
			return new ArchiveInput.ZipFileSystemInput(destinationPath);
		}

		@Override
		public void close() throws IOException {
			super.close();
			if (!destinationPath.equals(inProgressPath))
				Files.move(inProgressPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
		}
		
	}
	
	/**
	 * This version uses the Java NIO {@link FileSystem} implementation to write to a directory.
	 */
	public static class DirectoryOutput extends AbstractFileSystemOutput implements FileBacked {

		public DirectoryOutput(Path path) throws IOException {
			super(checkCreate(path));
		}

		private static Path checkCreate(Path path) throws IOException {
			if (!Files.exists(path))
				Files.createDirectory(path);
			else
				Preconditions.checkState(Files.isDirectory(path),
						"Path already exists and is not a directory: %s", path);
			return path.toAbsolutePath();
		}

		@Override
		public String getName() {
			return upstreamPath.toString();
		}

		@Override
		public File getInProgressFile() {
			return upstreamPath.toFile();
		}

		@Override
		public File getDestinationFile() {
			return upstreamPath.toFile();
		}

		@Override
		public ArchiveInput getCompletedInput() throws IOException {
			return new ArchiveInput.DirectoryInput(upstreamPath);
		}
		
	}

}
