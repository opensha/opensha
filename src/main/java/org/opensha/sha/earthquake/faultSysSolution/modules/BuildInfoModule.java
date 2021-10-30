package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Date;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;

import com.google.gson.GsonBuilder;

public class BuildInfoModule implements JSON_TypeAdapterBackedModule<BuildInfoModule> {
	
	private Long buildTime;
	private String gitHash;
	private String branch;
	private ApplicationVersion openshaVersion;
	private Long creationTime;

	@SuppressWarnings("unused") // used in deserialization
	private BuildInfoModule() {};
	
	public BuildInfoModule(Long buildTime, String gitHash, String branch, ApplicationVersion openshaVersion) {
		this.buildTime = buildTime;
		this.gitHash = gitHash;
		this.branch = branch;
		this.openshaVersion = openshaVersion;
		this.creationTime = System.currentTimeMillis();
	}
	
	public static BuildInfoModule detect() throws IOException {
		Date date = ApplicationVersion.loadBuildDate();
		Long buildTime = date == null ? null : date.getTime();
		String gitHash = ApplicationVersion.loadGitHash();
		String branch = ApplicationVersion.loadGitBranch();
		ApplicationVersion openshaVersion = ApplicationVersion.loadBuildVersion();
		return new BuildInfoModule(buildTime, gitHash, branch, openshaVersion);
	}

	@Override
	public String getFileName() {
		return "build_info.json";
	}

	@Override
	public String getName() {
		return "OpenSHA Build Information";
	}

	@Override
	public Type getType() {
		return BuildInfoModule.class;
	}

	@Override
	public BuildInfoModule get() {
		return this;
	}

	@Override
	public void set(BuildInfoModule value) {
		this.buildTime = value.buildTime;
		this.gitHash = value.gitHash;
		this.branch = value.branch;
		this.openshaVersion = value.openshaVersion;
		this.creationTime = value.creationTime;
	}

	public Long getBuildTime() {
		return buildTime;
	}

	public String getGitHash() {
		return gitHash;
	}

	public String getBranch() {
		return branch;
	}

	public ApplicationVersion getOpenshaVersion() {
		return openshaVersion;
	}

	public Long getCreationTime() {
		return creationTime;
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {}

}
