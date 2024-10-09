package org.opensha.commons.util.io.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveOutput.AbstractApacheZipOutput;

import com.google.common.base.Preconditions;

class AsynchronousApacheZipper {
	
	private CopyAvoidantInMemorySeekableByteChannel uncompressedData;
	private CopyAvoidantInMemorySeekableByteChannel compressedData;
	
	private String entrySourceName;
	private String entryDestName;
	private boolean externalInput;
	private ArchiveInput.AbstractApacheZipInput entryInput;

	AsynchronousApacheZipper() {
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
	
	public boolean hasEntry() {
		return entryDestName != null;
	}
	
	public boolean hasEntryData() {
		Preconditions.checkState(hasEntry());
		return entryInput != null;
	}
	
	public String getEntrySourceName() {
		return entrySourceName;
	}
	
	public String getEntryDestName() {
		return entrySourceName;
	}
	
	CopyAvoidantInMemorySeekableByteChannel swapCompressedDataBuffer(CopyAvoidantInMemorySeekableByteChannel newBuffer) {
		if (newBuffer == null) {
			newBuffer = new CopyAvoidantInMemorySeekableByteChannel(Integer.max(1024*1024*5, (int)compressedData.size()));
			newBuffer.setCloseable(false);
		}
		CopyAvoidantInMemorySeekableByteChannel oldBuffer = this.compressedData;
		this.compressedData = newBuffer;
		return oldBuffer;
	}
	
	public void rawTransferEntryTo(AbstractApacheZipOutput output) throws IOException {
		output.rawTransferApache(entryInput, entrySourceName, entryDestName);
		if (!externalInput)
			entryInput.close();
		entryInput = null;
	}
	
	public CompletableFuture<Void> rawTransferFuture(AbstractApacheZipOutput output) {
		boolean externalInput = this.externalInput;
		ArchiveInput.AbstractApacheZipInput myInput = this.entryInput;
		String entrySourceName = this.entrySourceName;
		String entryDestName = this.entryDestName;
		entryInput = null;
		return CompletableFuture.runAsync(new Runnable() {
			
			@Override
			public void run() {
				try {
					output.rawTransferApache(myInput, entrySourceName, entryDestName);
					if (!externalInput)
						myInput.close();
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
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
	
	public CompletableFuture<AsynchronousApacheZipper> transferFrom(ArchiveInput input, String sourceName, String destName) throws IOException {
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
	
	public CompletableFuture<AsynchronousApacheZipper> closeEntry() {
		// all uncompressed data has been written, now compress in parallel
		if (uncompressedData.size() == 0l)
			// nothing written
			return CompletableFuture.completedFuture(this);
		return CompletableFuture.supplyAsync(new Supplier<AsynchronousApacheZipper>() {

			@Override
			public AsynchronousApacheZipper get() {
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
//					System.out.println("Wrote "+entryName+";"
//							+"\n\tuncompressedData.size()="+uncompressedData.size()
//							+"\n\tentry.getSize()="+entry.getSize()
//							+"\n\tcompressedData.size()="+compressedData.size()
//							+"\n\tentry.getCompressedSize()="+entry.getCompressedSize()
//							+" (implied overhead: "+(compressedData.size()-entry.getCompressedSize())+")");
					Preconditions.checkState(uncompressedPos == uncompressedSize,
							"uncompressedData.position()=%s after write, uncompressedData.size()=%s",
							uncompressedPos, uncompressedSize);
					Preconditions.checkState(compressedPosAfter > compressedPosPrior,
							"compressedData.position()=%s after write, compressedData.position()=%s before write",
							compressedPosAfter, compressedPosPrior);
					Preconditions.checkState(compressedData.size() > 0l);
					compressedData.position(0l);
					entryInput = new ArchiveInput.InMemoryZipInput(compressedData);
					return AsynchronousApacheZipper.this;
				} catch (IOException e) {
					throw new RuntimeException("Exception writing "+entryDestName, e);
				}
			}
		});
	}

}
