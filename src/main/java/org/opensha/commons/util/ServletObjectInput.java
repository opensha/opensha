package org.opensha.commons.util;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/** Reads bounded, filtered Java objects from HTTP request bodies. */
public final class ServletObjectInput implements Closeable {

	private final ObjectInputStream input;
	private final Policy policy;
	private String rejectionReason;

	private ServletObjectInput(ObjectInputStream input, Policy policy) {
		this.input = input;
		this.policy = policy;
		ObjectInputFilter inherited = input.getObjectInputFilter();
		input.setObjectInputFilter(info -> {
			String reason = policy.rejectionReason(info);
			if (reason == null && inherited != null && inherited.checkInput(info) == Status.REJECTED)
				reason = "the JVM-wide deserialization filter rejected " + filterTarget(info);
			if (reason != null) {
				rejectionReason = reason;
				return Status.REJECTED;
			}
			return info.serialClass() == null ? Status.UNDECIDED : Status.ALLOWED;
		});
	}

	/**
	 * Reads the next object, preserving the reason if the filter rejects it. The JDK otherwise reports only
	 * {@code filter status: REJECTED}.
	 */
	public Object readObject() throws IOException, ClassNotFoundException {
		rejectionReason = null;
		try {
			return input.readObject();
		} catch (InvalidClassException e) {
			if (rejectionReason == null)
				throw e;
			InvalidClassException rejection = new InvalidClassException(policy.rejectedRequest(rejectionReason));
			rejection.initCause(e);
			throw rejection;
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(policy.rejectedRequest("serialized class " + e.getMessage()
					+ " is unavailable on the server. Check the client and server versions."), e);
		}
	}

	@Override
	public void close() throws IOException {
		input.close();
	}

	private static String filterTarget(ObjectInputFilter.FilterInfo info) {
		Class<?> type = info.serialClass();
		return type == null ? "an object reference" : type.getTypeName();
	}

	public static final class Policy {
		private final String endpoint;
		private final long maxBytes;
		private final long maxDepth;
		private final long maxReferences;
		private final long maxArrayLength;
		private final Set<Class<?>> allowed;

		Policy(String endpoint, long maxBytes, long maxDepth, long maxReferences, long maxArrayLength,
				Set<Class<?>> allowed) {
			if (maxBytes <= 0 || maxDepth <= 0 || maxReferences <= 0 || maxArrayLength < 0)
				throw new IllegalArgumentException("Invalid serialized request limits for " + endpoint);
			this.endpoint = endpoint;
			this.maxBytes = maxBytes;
			this.maxDepth = maxDepth;
			this.maxReferences = maxReferences;
			this.maxArrayLength = maxArrayLength;
			this.allowed = Set.copyOf(allowed);
		}

		private String rejectionReason(ObjectInputFilter.FilterInfo info) {
			if (info.depth() > maxDepth)
				return "object depth " + info.depth() + " exceeds the limit of " + maxDepth;
			if (info.references() > maxReferences)
				return "object reference count " + info.references() + " exceeds the limit of " + maxReferences;
			if (info.arrayLength() > maxArrayLength)
				return filterTarget(info) + " length " + info.arrayLength() + " exceeds the limit of " + maxArrayLength;
			Class<?> type = info.serialClass();
			if (type != null && !allowed.contains(type))
				return "class " + type.getTypeName() + " is outside the request allowlist. "
						+ "For a supported client request, review the serialized classes in ServletRequestPolicies.";
			return null;
		}

		private String rejectedRequest(String reason) {
			return "Rejected " + endpoint + " servlet request: " + reason;
		}

		private IOException byteLimitExceeded() {
			return new IOException(rejectedRequest("request body exceeds the limit of " + maxBytes + " bytes ("
					+ ServletRequestPolicies.BYTE_LIMIT_PROPERTY_PREFIX + endpoint + ")"));
		}
	}

	public static ServletObjectInput open(HttpServletRequest request, Policy policy) throws IOException {
		checkLength(request.getContentLengthLong(), policy);
		return open(request.getInputStream(), policy);
	}

	static ServletObjectInput open(InputStream source, long contentLength, Policy policy) throws IOException {
		checkLength(contentLength, policy);
		return open(source, policy);
	}

	private static ServletObjectInput open(InputStream source, Policy policy) throws IOException {
		InputStream limited = new LimitedInputStream(source, policy);
		try {
			return new ServletObjectInput(new ObjectInputStream(limited), policy);
		} catch (IOException | RuntimeException e) {
			try {
				limited.close();
			} catch (IOException closeError) {
				e.addSuppressed(closeError);
			}
			throw e;
		}
	}

	private static void checkLength(long contentLength, Policy policy) throws IOException {
		if (contentLength > policy.maxBytes)
			throw policy.byteLimitExceeded();
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private final Policy policy;
		private long remaining;

		LimitedInputStream(InputStream input, Policy policy) {
			super(input);
			this.policy = policy;
			this.remaining = policy.maxBytes;
		}

		@Override
		public int read() throws IOException {
			if (remaining == 0)
				return checkEnd();
			int value = in.read();
			if (value >= 0)
				remaining--;
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			Objects.checkFromIndexSize(offset, length, bytes.length);
			if (length == 0)
				return 0;
			if (remaining == 0)
				return checkEnd();
			int count = in.read(bytes, offset, (int) Math.min(remaining, length));
			if (count > 0)
				remaining -= count;
			return count;
		}

		@Override
		public long skip(long count) throws IOException {
			if (count <= 0)
				return 0;
			if (remaining == 0) {
				checkEnd();
				return 0;
			}
			long skipped = in.skip(Math.min(remaining, count));
			remaining -= skipped;
			return skipped;
		}

		@Override
		public int available() throws IOException {
			return (int) Math.min(remaining, in.available());
		}

		@Override
		public boolean markSupported() {
			return false;
		}

		@Override
		public void mark(int readLimit) {}

		@Override
		public void reset() throws IOException {
			throw new IOException("Mark/reset is not supported on a bounded request stream");
		}

		private int checkEnd() throws IOException {
			if (in.read() == -1)
				return -1;
			throw policy.byteLimitExceeded();
		}
	}
}
