package org.opensha.commons.util.io.archive;

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
	 * @param name
	 * @throws IOException
	 */
	public void putNextEntry(String name) throws IOException;
	
	/**
	 * Gets an {@link OutputStream} for the currently entry. Must call {@link #putNextEntry(String)} first, and must
	 * only call this once. After writing to the stream, callers must then call {@link #closeEntry()}.
	 * 
	 * This does not usually need to be wrapped in a {@link BufferedOutputStream} as all default implementations already
	 * use buffers.
	 * 
	 * <p><b>IMPORTANT: never close this output stream</b> as it may be reused for multiple entries, depending on the
	 * implementation. Instead, call {@link OutputStream#flush()} when you have finished writing.
	 * @return {@link OutputStream} for the currently entry
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws IOException;
	
	/**
	 * Closes the current entry.
	 * 
	 * <p>This will automatically flush the {@link OutputStream}, but if you wrap it in another layer be sure to flush
	 * the wrapper before calling.
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
	 * This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}.
	 * 
	 * @param input
	 * @param name
	 * @throws IOException
	 */
	public default void transferFrom(ArchiveInput input, String name) throws IOException {
		transferFrom(input, name, name);
	}
	
	/**
	 * Transfer an entry of the given name from the given {@link ArchiveInput} to this {@link ArchiveOutput},
	 * possibly renaming the entry.
	 * 
	 * This automatically calls {@link #putNextEntry(String)} and {@link #closeEntry()}.
	 * 
	 * @param input
	 * @param sourceName
	 * @param destName
	 * @throws IOException
	 */
	public default void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
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
	 * {@link ArchiveOutput} that is backed by a {@link File}, possibly with a temporary file while writing
	 * (see {@link #getInProgressFile()}) that is written to a final file (see {@link #getDestinationFile()}) when
	 * completed.
	 */
	public interface FileBacked extends ArchiveOutput {
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
	 * Gets the default {@link ArchiveOutput} implementation for the given file, using the filename.
	 * 
	 * @param outputFile
	 * @return
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
	 * @param outputFile
	 * @param input
	 * @return
	 * @throws IOException
	 */
	public static ArchiveOutput getDefaultOutput(File outputFile, ArchiveInput input) throws IOException {
		String name = outputFile.getName().toLowerCase();
		if (outputFile.isDirectory() || (!outputFile.exists() && name.endsWith(File.separator)))
			return new DirectoryOutput(outputFile.toPath());
		if (name.endsWith(".tar")) {
			return new TarFileOutput(outputFile);
		} else if (name.endsWith(".zip")) {
			if (input instanceof ArchiveInput.ApacheZipFileInput)
				return new ApacheZipFileOutput(outputFile);
			if (input instanceof ArchiveInput.ZipFileSystemInput)
				return new ZipFileSystemOutput(outputFile.toPath());
			return new ZipFileOutput(outputFile);
		}
		// unknown extesion, assume it's probably zip but check if the input is tar
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "Not closed?");
			CopyAvoidantInMemorySeekableByteChannel copy = byteChannel.copy();
			copy.position(0l);
			return new ArchiveInput.InMemoryZipFileInput(copy);
		}
		
	}
	
	/**
	 * Zip file implementation using the Apeche Common I/O library; this can be used to efficiently transfer data
	 * from an {@link ArchiveInput.ApacheZipFileInput} without inflating and deflating.
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
		public ArchiveInput getCompletedInput() throws IOException {
			Preconditions.checkState(zout == null, "ZipOutputStream is still open");
			return new ArchiveInput.ApacheZipFileInput(outputFile);
		}
		
	}
	
	/**
	 * Parallel zip file implementation where contents are zipped in memory in parallel, then written to the output
	 * file as they roll off the line. More throughput can be achieved if preserving the order is not important.
	 * 
	 * <p>This can require significant memory and no resources are released until it is closed. Be sure to have at least
	 * <code>threads x (largest uncompressed file size + largest compressed file size)</code> available.
	 */
	public static class ParallelZipFileOutput extends ApacheZipFileOutput {

		private ArrayDeque<CompletableFuture<Zipper>> zipFutures;
		private ArrayDeque<Zipper> zippers;
		private CompletableFuture<Zipper> writeFuture;
		private boolean preserveOrder;
		private int maxThreads;
		private int threadsInitialized;
		
		private boolean trackBlockingTimes;
		private Stopwatch overallWatch = null;
		private Stopwatch blockingWriteWatch = null;
		private Stopwatch blockingZipWatch = null;
		
		private ExecutorService postProcessExec = ExecutorUtils.singleTaskRejectingExecutor();
		
		private Zipper currentZipper;

		/**
		 * Parallel zip file output with the given number of parallel workers; contents will be written out in the order
		 * received.
		 * 
		 * @param outputFile
		 * @param threads
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
		 * @param outputFile
		 * @param threads
		 * @param preserveOrder
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
		 * @param outputFile
		 * @param inProgressFile
		 * @param threads
		 * @param preserveOrder
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
				Zipper zip = zipFutures.remove().join();
				transferZippedData(zip);
			}
			if (writeFuture != null) {
				if (trackBlockingTimes) blockingWriteWatch.start();
				zippers.add(writeFuture.join());
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
			for (CompletableFuture<Zipper> peek : zipFutures) {
				if (writeFuture != null && !writeFuture.isDone())
					break;
				if (peek.isDone()) {
					Preconditions.checkState(zipFutures.remove(peek));
					Zipper zip = peek.join();
					transferZippedData(zip);
					break;
				} else if (preserveOrder) {
					break;
				}
			}
//			while ((writeFuture == null || writeFuture.isDone()) && !zipFutures.isEmpty()) {
//				CompletableFuture<Zipper> peek = zipFutures.peek();
//				if (peek.isDone()) {
//					Zipper zip = zipFutures.poll().join();
//					transferZippedData(zip);
//					break;
//				} else if (preserveOrder) {
//					break;
//				}
//			}
		}
		 
		/*
		 * Begins the data transfer for the given zipper asynchronously, and populates writeFuture. If a writeFuture
		 * already exists, it is joined first and the associated zipper is added back to the zipper queue
		 * 
		 * Externally synchronized
		 */
		private synchronized void transferZippedData(Zipper zipper) throws IOException {
			if (writeFuture != null) {
				if (trackBlockingTimes) blockingWriteWatch.start();
				Zipper prevZipper = writeFuture.join();
				if (trackBlockingTimes) blockingWriteWatch.stop();
				zippers.add(prevZipper);
			}
			Preconditions.checkNotNull(zipper.entryDestName);
			if (zipper.entryInput == null) {
				// empty, probably just a directory
				super.putNextEntry(zipper.entryDestName);
				super.closeEntry();
				writeFuture = CompletableFuture.completedFuture(zipper);
				// see if there's another one ready
				processZipFutures();
			} else {
				writeFuture = CompletableFuture.supplyAsync(new Supplier<Zipper>() {

					@Override
					public Zipper get() {
						try {
							ParallelZipFileOutput.this.rawTransferApache(zipper.entryInput, zipper.entrySourceName, zipper.entryDestName);
							if (!zipper.externalInput)
								zipper.entryInput.close();
							zipper.entryInput = null;
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
						return zipper;
					}
				});
				// when done writing this one, start the next (if ready)
				writeFuture.thenRunAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							ParallelZipFileOutput.this.processZipFutures();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}, postProcessExec);
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
					currentZipper = new Zipper();
					threadsInitialized++;
				} else {
					// block until one is ready
					if (writeFuture == null) {
						// no one is currently writing, which means we must have some queued
						Preconditions.checkState(!zipFutures.isEmpty());
						CompletableFuture<Zipper> future = zipFutures.remove();
						if (trackBlockingTimes) blockingZipWatch.start();
						Zipper zip = future.join();
						if (trackBlockingTimes) blockingZipWatch.stop();
						transferZippedData(zip); // this populates writeFuture
					}
					Preconditions.checkNotNull(writeFuture);
					// get it from the write queue
					if (trackBlockingTimes) blockingWriteWatch.start();
					currentZipper = writeFuture.join();
					if (trackBlockingTimes) blockingWriteWatch.stop();
					writeFuture = null;
				}
			}
			currentZipper.reset();
		}

		@Override
		public synchronized void putNextEntry(String name) throws IOException {
			prepareZipper();
			currentZipper.putNextEntry(name);
		}

		@Override
		public synchronized OutputStream getOutputStream() throws IOException {
			Preconditions.checkNotNull(currentZipper);
			return currentZipper.getOutputStream();
		}

		@Override
		public synchronized void closeEntry() throws IOException {
			Preconditions.checkNotNull(currentZipper);
			CompletableFuture<Zipper> future = currentZipper.closeEntry();
			currentZipper = null;
			zipFutures.add(future);
		}

		@Override
		public synchronized void transferFrom(InputStream is, String name) throws IOException {
			putNextEntry(name);
			is.transferTo(getOutputStream());
			is.close();
			closeEntry();
		}

		@Override
		public synchronized void transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
			prepareZipper();
			currentZipper.transferFrom(input, sourceName, destName);
		}
		
		private class Zipper {
			
			private CopyAvoidantInMemorySeekableByteChannel uncompressedData;
			private CopyAvoidantInMemorySeekableByteChannel compressedData;
			
			private String entrySourceName;
			private String entryDestName;
			private boolean externalInput;
			private ArchiveInput.AbstractApacheZipInput entryInput;

			private Zipper() {
				uncompressedData = new CopyAvoidantInMemorySeekableByteChannel(1024*1024*32); // 32 MB
				uncompressedData.setCloseable(false);
				compressedData = new CopyAvoidantInMemorySeekableByteChannel(1024*1024*5); // 5 MB
				compressedData.setCloseable(false);
			}
			
			public void reset() {
				entrySourceName = null;
				entryDestName = null;
				entryInput = null;
				externalInput = false;
				// reset each buffer: size=0 and position=0
				uncompressedData.truncate(0l);
				compressedData.truncate(0l);
			}
			
			public void putNextEntry(String name) {
				Preconditions.checkState(entrySourceName == null, "didn't close prior entry");
				this.entrySourceName = name;
				this.entryDestName = name;
			}
			
			public OutputStream getOutputStream() {
				Preconditions.checkNotNull(entrySourceName, "Not currently writing");
				return uncompressedData.getOutputStream();
			}
			
			public CompletableFuture<Zipper> transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
				Preconditions.checkState(entrySourceName == null, "didn't close prior entry");
				if (input instanceof ArchiveInput.AbstractApacheZipInput) {
					entryInput = (ArchiveInput.AbstractApacheZipInput)input;
					entrySourceName = sourceName;
					entryDestName = destName;
					externalInput = true;
					return CompletableFuture.completedFuture(this);
				}
				putNextEntry(destName);
				input.getInputStream(sourceName).transferTo(getOutputStream());
				return closeEntry();
			}
			
			public CompletableFuture<Zipper> closeEntry() {
				// all uncompressed data has been written, now compress in parallel
				if (uncompressedData.size() == 0l)
					// nothing written
					return CompletableFuture.completedFuture(this);
				CompletableFuture<Zipper> future = CompletableFuture.supplyAsync(new Supplier<Zipper>() {

					@Override
					public Zipper get() {
						try {
							Preconditions.checkState(compressedData.size() == 0l);
							Preconditions.checkState(compressedData.position() == 0l);
							ZipArchiveOutputStream zout = new ZipArchiveOutputStream(compressedData);
							ZipArchiveEntry entry = new ZipArchiveEntry(entryDestName);
							zout.putArchiveEntry(entry);
							long compressedPosPrior = compressedData.position();
							uncompressedData.position(0l);
							Preconditions.checkState(uncompressedData.position() == 0l);
							uncompressedData.getInputStream().transferTo(zout);
							zout.flush();
							zout.closeArchiveEntry();
							zout.close();
							long compressedPosAfter = compressedData.position();
							long uncompressedPos = uncompressedData.position();
							long uncompressedSize = uncompressedData.size();
//							System.out.println("Wrote "+entryName+";"
//									+"\n\tuncompressedData.size()="+uncompressedData.size()
//									+"\n\tentry.getSize()="+entry.getSize()
//									+"\n\tcompressedData.size()="+compressedData.size()
//									+"\n\tentry.getCompressedSize()="+entry.getCompressedSize()
//									+" (implied overhead: "+(compressedData.size()-entry.getCompressedSize())+")");
							Preconditions.checkState(uncompressedPos == uncompressedSize,
									"uncompressedData.position()=%s after write, uncompressedData.size()=%s",
									uncompressedPos, uncompressedSize);
							Preconditions.checkState(compressedPosAfter > compressedPosPrior,
									"compressedData.position()=%s after write, compressedData.position()=%s before write",
									compressedPosAfter, compressedPosPrior);
							Preconditions.checkState(compressedData.size() > 0l);
							compressedData.position(0l);
							entryInput = new ArchiveInput.InMemoryZipFileInput(compressedData);
							return Zipper.this;
						} catch (IOException e) {
							throw new RuntimeException("Exception writing "+entryDestName, e);
//							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				});
				
				// this will cause it to start transferring zipped output (if it's not already)
				future.thenRunAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							ParallelZipFileOutput.this.processZipFutures();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}, postProcessExec);
				return future;
			}
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
	 * UNIX Tarball (uncompressed) file output implementation. Data are written in memory first because the size must
	 * be known prior to writing, then flushed to disk.
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
			currentOutputStream = Files.newOutputStream(currentPath, fs == null ? StandardOpenOption.CREATE : StandardOpenOption.CREATE_NEW);
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
