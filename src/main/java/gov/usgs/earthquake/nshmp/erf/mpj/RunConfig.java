package gov.usgs.earthquake.nshmp.erf.mpj;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

public final class RunConfig {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM_dd");

	private final String directoryName;
	private final String baseName;
	private final List<String> nameTokens;
	private final String datePrefix;
	private final boolean includeDatePrefix;

	private RunConfig(Builder builder) {
		this.directoryName = builder.directoryName;
		this.baseName = builder.baseName;
		this.nameTokens = List.copyOf(builder.nameTokens);
		this.datePrefix = builder.datePrefix;
		this.includeDatePrefix = builder.includeDatePrefix;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String buildDirectoryName() {
		if (directoryName != null)
			return directoryName;
		List<String> tokens = new ArrayList<>();
		if (includeDatePrefix)
			tokens.add(datePrefix != null ? datePrefix : DATE_FORMAT.format(LocalDate.now()));
		tokens.add(baseName);
		tokens.addAll(nameTokens);
		return String.join("-", tokens);
	}

	public static final class Builder {
		private String directoryName;
		private String baseName;
		private final List<String> nameTokens = new ArrayList<>();
		private String datePrefix;
		private boolean includeDatePrefix = true;

		public Builder directoryName(String directoryName) {
			this.directoryName = directoryName;
			return this;
		}

		public Builder baseName(String baseName) {
			this.baseName = baseName;
			return this;
		}

		public Builder addNameToken(String nameToken) {
			if (nameToken != null && !nameToken.isBlank())
				nameTokens.add(nameToken);
			return this;
		}

		public Builder addNameTokens(List<String> nameTokens) {
			if (nameTokens != null)
				for (String token : nameTokens)
					addNameToken(token);
			return this;
		}

		public Builder datePrefix(String datePrefix) {
			this.datePrefix = datePrefix;
			return this;
		}

		public Builder noDatePrefix() {
			this.includeDatePrefix = false;
			return this;
		}

		public RunConfig build() {
			if (directoryName == null)
				Preconditions.checkArgument(baseName != null && !baseName.isBlank(),
						"baseName is required unless directoryName is set");
			return new RunConfig(this);
		}
	}
}
