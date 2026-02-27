package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.GitVersion;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;

import com.google.gson.GsonBuilder;

public class BuildInfoModule implements JSON_TypeAdapterBackedModule<BuildInfoModule> {
	
	private Long buildTime;
	private String buildTimeHuman;
	private String gitHash;
	private String branch;
	private String remoteUrl;
	private ApplicationVersion openshaVersion;
	private Long creationTime;
	private String creationTimeHuman;
	private List<Map<String, String>> extra;

	@SuppressWarnings("unused") // used in deserialization
	private BuildInfoModule() {};
	
	public BuildInfoModule(Long buildTime, String gitHash, String branch, String remoteUrl, ApplicationVersion openshaVersion) {
		this.buildTime = buildTime;
		if(buildTime != null) {
			this.buildTimeHuman = new Date(buildTime).toString();
		}
		this.gitHash = gitHash;
		this.branch = branch;
		this.remoteUrl = remoteUrl;
		this.openshaVersion = openshaVersion;
		this.creationTime = System.currentTimeMillis();
		this.creationTimeHuman = new Date(creationTime).toString();
	}

	public static BuildInfoModule fromGitVersion(GitVersion git) throws IOException{
		Date date = git.loadBuildDate();
		Long buildTime = date == null ? null : date.getTime();
		String gitHash;
		try {
			gitHash = git.loadGitHash();
		} catch (Exception e) {
			gitHash = null;
		}
		String branch;
		try {
			branch = git.loadGitBranch();
		} catch (Exception e) {
			branch = null;
		}
		String remoteUrl;
		try {
			remoteUrl = git.loadGitRemote();
		} catch (Exception e) {
			remoteUrl = null;
		}
		ApplicationVersion openshaVersion = ApplicationVersion.loadBuildVersion();
		return new BuildInfoModule(buildTime, gitHash, branch, remoteUrl, openshaVersion);
	}

	public static BuildInfoModule detect() throws IOException {
		return fromGitVersion(new GitVersion());
	}

	public void addExtra(GitVersion gitVersion) {
		if (extra == null) {
			extra = new ArrayList<>();
		}
		extra.add(gitVersion.getMap());
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
		if (buildTime != null) {
			buildTimeHuman = new Date(buildTime).toString();
		}
		this.gitHash = value.gitHash;
		this.branch = value.branch;
		this.remoteUrl = value.remoteUrl;
		this.openshaVersion = value.openshaVersion;
		this.creationTime = value.creationTime;
		if (creationTime != null) {
			creationTimeHuman = new Date(creationTime).toString();
		}
		this.extra = value.extra;
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

	public String getRemoteUrl() {
		return remoteUrl;
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
