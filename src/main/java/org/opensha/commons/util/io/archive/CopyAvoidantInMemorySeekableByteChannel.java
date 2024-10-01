package org.opensha.commons.util.io.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.google.common.base.Preconditions;

/**
 * A {@link SeekableByteChannel} implementation that wraps a byte[], based on {@link SeekableInMemoryByteChannel},
 * except that array copy operations are avoided by keeping a list of byte[] rather than constantly growing it via arraycopy.
 * 
 * @NotThreadSafe
 */
public class CopyAvoidantInMemorySeekableByteChannel implements SeekableByteChannel {

	private static final int DEFAULT_INITIAL_SIZE = 1024*1024*10; // 10 MB
	
	private static final boolean D = false;
	private static final boolean DD = D && false;
	
	private int curBlockSize;
	private int maxBlockSize;

	private List<byte[]> allData;
	private List<Integer> allDataStartPos;

	private int curDataIndex;
	private int curDataStartPos;
	private int posIndexInCur;
	private byte[] curData;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicBoolean closeable = new AtomicBoolean(true);
	private int position, size;

	/**
	 * Constructor taking a byte array.
	 *
	 * <p>This constructor is intended to be used when reading from a given byte array.</p>
	 *
	 * @param data input data or pre-allocated array.
	 */
	public CopyAvoidantInMemorySeekableByteChannel(final byte[] data) {
		init(data, data.length); // passed in data, that is the initial size
	}

	/**
	 * Constructor taking a byte array and the initial size (which cannot exceed the array size). Set the size to
	 * zero if this is simply a pre-allocated array, or to the size of the existing data if data are passed in.
	 *
	 * <p>This constructor is intended to be used when reading from a given byte array or when pre-allocated.</p>
	 *
	 * @param data input data or pre-allocated array.
	 */
	public CopyAvoidantInMemorySeekableByteChannel(final byte[] data, int size) {
		init(data, size); // passed in data with its own initial size
	}

	/**
	 * Parameterless constructor - allocates internal buffer by itself.
	 */
	public CopyAvoidantInMemorySeekableByteChannel() {
		this(DEFAULT_INITIAL_SIZE);
	}

	/**
	 * Constructor taking a size of storage to be allocated. Although the given size will be pre-allocated, the size
	 * of the channel will be initialized to zero.
	 *
	 * <p>Creates a channel and allocates internal storage of a given size.</p>
	 *
	 * @param size size of internal buffer to allocate, in bytes.
	 */
	public CopyAvoidantInMemorySeekableByteChannel(final int size) {
		init(new byte[size], 0); // empty buffer of the pre-allocated size, channel size is set to zero
	}
	
	private void init(byte[] initial, int size) {
		if (D) System.out.println("CopyAvoidant.init(byte["+initial.length+"], "+size+")");
		Preconditions.checkState(size <= initial.length);
		this.curData = initial;
		this.curDataStartPos = 0;
		this.size = size;
		this.position = 0;
		this.curBlockSize = Integer.min(1024*1024*64, Integer.max(size, 1024*1024)); // at least 1 MB, but no more than 64 MB
		this.maxBlockSize = curBlockSize * 16; // will be no more than 1 GB (64 MB x 16)
		allData = new ArrayList<>();
		allData.add(curData);
		allDataStartPos = new ArrayList<>();
		allDataStartPos.add(0);
		curDataIndex = 0;
		posIndexInCur = 0;
	}

	/**
	 * Returns this channel's position.
	 *
	 * <p>This method violates the contract of {@link SeekableByteChannel#position()} as it will not throw any exception
	 * when invoked on a closed channel. Instead it will return the position the channel had when close has been
	 * called.</p>
	 */
	@Override
	public long position() {
		return position;
	}

	@Override
	public SeekableByteChannel position(final long newPosition) throws IOException {
		ensureOpen();
		if (newPosition < 0L || newPosition > Integer.MAX_VALUE) {
			throw new IOException("Position has to be in range 0.. " + Integer.MAX_VALUE);
		}
		setPositionInternal((int)newPosition);
		return this;
	}
	
	/**
	 * Sets the position and updates the internal buffers
	 * 
	 * @param newPosition
	 */
	private void setPositionInternal(int newPosition) {
		Preconditions.checkState(newPosition >= 0);
		if (newPosition == 0) {
			// simple case
			curDataIndex = 0;
			curData = allData.get(0);
			curDataStartPos = 0;
			posIndexInCur = 0;
			position = 0;
			return;
		}
		// see if we switched to a new buffer
		int posInCur = newPosition - curDataStartPos;
		if (DD) System.out.println("CopyAvoidant.setPositionInternal("+newPosition+"); "
				+ "posInCur="+posInCur+", curDataIndex="+curDataIndex+", curDataStartPos="+curDataStartPos);
		if (posInCur < 0) {
			// position is before our current buffer
			// TODO: maybe we should search forward from zero instead of going backwards?
			// TODO: or should we binary search?
			while (true) {
				Preconditions.checkState(curDataIndex > 0);
				curDataIndex--;
				curData = allData.get(curDataIndex);
				curDataStartPos = allDataStartPos.get(curDataIndex);
				if (DD) System.out.println("\tCopyAvoidant.setPositionInternal: moved backwards, curDataIndex="+curDataIndex+", curDataStartPos="+curDataStartPos);
				if (newPosition >= curDataStartPos)
					// this buffer contains the position
					break;
			}
		} else if (posInCur >= curData.length) {
			// position is after our current buffer
			while (curDataIndex < allData.size()-1) {
				curDataIndex++;
				curData = allData.get(curDataIndex);
				curDataStartPos = allDataStartPos.get(curDataIndex);
				if (DD) System.out.println("\tCopyAvoidant.setPositionInternal: moved forwards, curDataIndex="+curDataIndex+", curDataStartPos="+curDataStartPos);
				if (newPosition < curDataStartPos + curData.length)
					// this buffer doesn't contain the position
					break;
			}
		}
		posIndexInCur = newPosition - curDataStartPos;
		Preconditions.checkState(posIndexInCur >= 0, "Bad posIndexInCur=%s with newPosition=%s, curDataStartPos=%s, and curDataIndex=%s",
				posIndexInCur, newPosition, curDataStartPos, curDataIndex);
		position = newPosition;
	}

	/**
	 * Returns the current size of entity to which this channel is connected.
	 *
	 * <p>This method violates the contract of {@link SeekableByteChannel#size} as it will not throw any exception when
	 * invoked on a closed channel. Instead it will return the size the channel had when close has been called.</p>
	 */
	@Override
	public long size() {
		return size;
	}

	/**
	 * Truncates the entity, to which this channel is connected, to the given size. Does not re-allocate any memory; any
	 * existing buffers are kept for future writing.
	 *
	 * <p>This method violates the contract of {@link SeekableByteChannel#truncate} as it will not throw any exception when
	 * invoked on a closed channel.</p>
	 *
	 * @throws IllegalArgumentException if size is negative or bigger than the maximum of a Java integer
	 */
	@Override
	public SeekableByteChannel truncate(final long newSize) {
		if (newSize < 0L || newSize > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Size has to be in range 0.. " + Integer.MAX_VALUE);
		}
		if (size > newSize) {
			size = (int) newSize;
		}
		if (position > newSize) {
			setPositionInternal((int)newSize);
		}
		return this;
	}

	@Override
	public int read(final ByteBuffer buf) throws IOException {
		ensureOpen();
		int wanted = buf.remaining();
		if (DD) System.out.println("CopyAvoidant.read(ByteBuffer["+wanted+"])");
		final int possible = size - position;
		if (possible <= 0) {
			return -1;
		}
		if (wanted > possible) {
			wanted = possible;
		}
		
		int read = 0;
		
		while (wanted > 0) {
			int possibleInCur = curData.length - posIndexInCur;
			if (wanted <= possibleInCur) {
				// can read everything we need from current
				if (DD) System.out.println("\tCopyAvoidant.read: reading all from current");
				buf.put(curData, posIndexInCur, wanted);
				setPositionInternal(position + wanted);
				read += wanted;
				wanted = 0;
			} else {
				// partial read
				int readLen = possibleInCur;
				if (DD) System.out.println("\tCopyAvoidant.read: reading partial, readLen="+readLen);
				buf.put(curData, posIndexInCur, readLen);
				wanted -= readLen;
				setPositionInternal(position + readLen);
				read += readLen;
				if (posIndexInCur > curData.length)
					break;
			}
		}
		return read;
	}

	@Override
	public void close() {
		if (closeable.get())
			closed.set(true);
	}
	
	/**
	 * Can be used to re-open this channel after a close() call, typically to re-use
	 */
	public void open() {
		closed.set(false);
	}
	
	/**
	 * This can be used to disable the close() functionality, useful it you're re-using this channel. Garbage collection
	 * operations can auto close channels when pruning old objects, even if you never explicitly closed them.
	 * @param closeable
	 */
	public void setCloseable(boolean closeable) {
		this.closeable.set(closeable);
	}

	@Override
	public boolean isOpen() {
		return !closed.get();
	}

	@Override
	public int write(final ByteBuffer b) throws IOException {
		ensureOpen();
		int wanted = b.remaining();
		if (DD) System.out.println("CopyAvoidant.write(ByteBuffer["+wanted+"])");
		int newSize = position + wanted;
		if (newSize < 0) // overflow
			wanted = Integer.MAX_VALUE - position;
		int origWanted = wanted;
		while (wanted > 0) {
			int possibleInCur = curData.length - posIndexInCur;
			if (DD) System.out.println("\tCopyAvoidant.write: wanted="+wanted+" with possibleInCur = "
					+curData.length+" - "+posIndexInCur+" = "+possibleInCur);
			
			if (wanted <= possibleInCur) {
				// can write everything we need to current
				if (DD) System.out.println("\tCopyAvoidant.write: writing all to current");
				b.get(curData, posIndexInCur, wanted);
				setPositionInternal(position + wanted);
				size = Integer.max(size, position);
				wanted = 0;
			} else {
				if (possibleInCur > 0) {
					// partial write
					int writeLen = possibleInCur;
					if (DD) System.out.println("\tCopyAvoidant.write: writing partial, writeLen="+writeLen);
					b.get(curData, posIndexInCur, writeLen);
					wanted -= writeLen;
					setPositionInternal(position + writeLen);
					size = Integer.max(size, position);
				}
				while (posIndexInCur >= curData.length) {
					// need to increase the size
					Preconditions.checkState(curBlockSize > 0);
					byte[] next = new byte[curBlockSize];
					int nextBlockSize = Integer.max(curBlockSize * 2, maxBlockSize);
					if (nextBlockSize > 0)
						curBlockSize = nextBlockSize;
					if (D) System.out.println("\tCopyAvoidant.write: reszising by adding block "+allData.size()+", new byte["+curBlockSize+"]");
					int startPos = allDataStartPos.get(allDataStartPos.size()-1) + allData.get(allData.size()-1).length;
					allData.add(next);
					allDataStartPos.add(startPos);
					curData = next;
					curDataIndex = allDataStartPos.size()-1;
					curDataStartPos = startPos;
					posIndexInCur = position - startPos;
					Preconditions.checkState(posIndexInCur >= 0);
				}
			}
		}
		return origWanted;
	}

	/**
	 * Obtains the array backing this channel.
	 *
	 * <p>NOTE:
	 * The returned buffer may be longer containing data, use
	 * {@link #size()} to obtain the size of data stored in the buffer.</p>
	 *
	 * @return internal byte array.
	 */
	public byte[] array() {
		if (allData.size() == 1)
			return curData;
		// need to concatenate (expensive)
		if (D) System.out.println("CopyAvoidant.array(): Concatinating "+allData.size()+" arrays into one big array of size="+size);
		byte[] bigArray = new byte[size];
		int destPos = 0;
		for (byte[] source : allData) {
			int destEnd = destPos + source.length;
			int len = destEnd > size ? size - destPos : source.length;
			if (len <= 0)
				break;
			System.arraycopy(source, 0, bigArray, destPos, len);
			destPos += len;
		}
		// re-initialize with the big array
		// but keep the current block size
		int curBlockSize = this.curBlockSize;
		int maxBlockSize = this.maxBlockSize;
		init(bigArray, size);
		this.curBlockSize = curBlockSize;
		this.maxBlockSize = maxBlockSize;
		return bigArray;
	}

	private void ensureOpen() throws ClosedChannelException {
		if (!isOpen()) {
			throw new ClosedChannelException();
		}
	}
	
	public CopyAvoidantInMemorySeekableByteChannel copy() {
		CopyAvoidantInMemorySeekableByteChannel ret = new CopyAvoidantInMemorySeekableByteChannel(allData.get(0));
		for (int i=1; i<allData.size(); i++) {
			ret.allData.add(allData.get(i));
			ret.allDataStartPos.add(allDataStartPos.get(i));
		}
		ret.size = size;
		ret.setPositionInternal(position);
		ret.setCloseable(closeable.get());
		return ret;
	}
	
	/**
	 * This returns an output stream view of this channel, starting at the current position. This does not
	 * keep its own position, so any seeks, reads, or writes done directly to the channel (or via another nput or
	 * output stream) will affect this one. Similarly, bytes written here will advance the position in the channel.
	 * 
	 * @return output stream representation at the current position
	 */
	public OutputStream getOutputStream() {
		ByteBuffer singleByteBuffer = ByteBuffer.allocate(1);
		return new OutputStream() {
			
		    @Override
		    public void write(int b) throws IOException {
		    	singleByteBuffer.clear();
		    	singleByteBuffer.put(0, (byte) b);
		    	singleByteBuffer.flip(); // Prepare the buffer for writing
		        CopyAvoidantInMemorySeekableByteChannel.this.write(singleByteBuffer);
		    }
		    
		    @Override
		    public void write(byte[] b, int off, int len) throws IOException {
		        // Wrap the provided byte array in a ByteBuffer
		        ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
		        CopyAvoidantInMemorySeekableByteChannel.this.write(buffer);
		    }
		};
	}
	
	/**
	 * This returns an input stream view of this channel, starting at the current position. This does not
	 * keep its own position, so any seeks, reads, or writes done directly to the channel (or via another input or 
	 * output stream) will affect this one. Similarly, bytes read here will advance the position in the channel.
	 * 
	 * If you wish to read from the beginning, be sure to reset the position to 0 on the channel beforehand.
	 * 
	 * @return input stream representation starting at the current position (reset position to 0 beforehand if needed)
	 */
	public InputStream getInputStream() {
		ByteBuffer singleByteBuffer = ByteBuffer.allocate(1);
		return new InputStream() {
			
			@Override
			public int read() throws IOException {
				// Reuse the single-byte buffer for the read operation
				singleByteBuffer.clear(); // Reset the buffer
				int bytesRead = CopyAvoidantInMemorySeekableByteChannel.this.read(singleByteBuffer);
				if (bytesRead == -1) {
					return -1; // End of the stream
				}
				singleByteBuffer.flip(); // Prepare the buffer for reading
				return singleByteBuffer.get() & 0xFF; // Return as unsigned byte
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				if (b == null) {
					throw new NullPointerException();
				} else if (off < 0 || len < 0 || len > b.length - off) {
					throw new IndexOutOfBoundsException();
				} else if (len == 0) {
					return 0;
				}

				// Wrap the byte array with the appropriate offset and length in a ByteBuffer
				ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
				int bytesRead = CopyAvoidantInMemorySeekableByteChannel.this.read(buffer);
				return bytesRead == 0 ? -1 : bytesRead; // Return -1 if no bytes read, otherwise bytesRead
			}

			@Override
			public long skip(long n) throws IOException {
				if (n < 0) {
					return 0;
				}
				long currentPosition = CopyAvoidantInMemorySeekableByteChannel.this.position();
				long newPosition = currentPosition + n;
				long size = CopyAvoidantInMemorySeekableByteChannel.this.size();
				if (newPosition > size) {
					newPosition = size; // Cap at the end of the channel
				}
				CopyAvoidantInMemorySeekableByteChannel.this.position(newPosition);
				return newPosition - currentPosition;
			}

			@Override
			public int available() throws IOException {
				long size = CopyAvoidantInMemorySeekableByteChannel.this.size();
				long position = CopyAvoidantInMemorySeekableByteChannel.this.position();
				return (int) Math.min(Integer.MAX_VALUE, size - position);
			}
		};
	}

}
