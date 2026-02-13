package org.opensha.commons.util.bugReports;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.ServerPrefUtils;


public class BugReport {

	public static String GITHUB_ISSUES_URL = "https://github.com/opensha/opensha/issues";
	public static String GITHUB_NEW_ISSUE_URL = GITHUB_ISSUES_URL+"/new";
	private static String enc = "UTF-8";
	
	public enum Type {
		BUG("bug"),
		ENHANCEMENT("enhancement");
		
		private String typeStr;
		
		private Type(String typeStr) {
			this.typeStr = typeStr;
		}

		@Override
		public String toString() {
			return typeStr;
		}
	};
	
	public enum Component {
		SCEC_VDO("SCEC-VDO Plugins", "geo3d"),
		BUILD_PROCESS("build-process"),
		COMMONS("commons", "commons"),
		CYBERSHAKE("cybershake", "cybershake"),
		FAULT_DB("fault DB", "refFaultParamDb"),
		SHA("sha", "sha"),
		SRA("sra", "sra"),
		WEBSITE("website");
		
		private String compStr;
		private String pkgName;
		
		private Component(String compStr) {
			this(compStr, null);
		}
		
		private Component(String compStr, String pkgName) {
			this.compStr = compStr;
			this.pkgName = pkgName;
		}

		@Override
		public String toString() {
			return compStr;
		}
		
		public static Component fromClass(Class<?> clazz) {
			String[] pkg = clazz.getPackage().getName().split("\\.");
			for (int i=pkg.length-1; i>=0; i--) {
				String pkgName = pkg[i];
				for (Component comp : Component.values()) {
					if (comp.pkgName != null && comp.pkgName.equals(pkgName))
						return comp;
				}
			}
			return null;
		}
	};
	
	private String summary;
	private String description;
	private Type type;
	private Component component;
	private String keywords;
	private Throwable t;
	private ApplicationVersion appVersion;
	
	private static String getStackTraceAsCause(Throwable cause, StackTraceElement[] causedTrace) {
		StackTraceElement[] trace = cause.getStackTrace();
		int m = trace.length-1, n = causedTrace.length-1;
		while (m >= 0 && n >=0 && trace[m].equals(causedTrace[n])) {
			m--; n--;
		}
		int framesInCommon = trace.length - 1 - m;

		String str = "Caused by: " + cause;
		for (int i=0; i <= m; i++)
			str += "\n\tat " + trace[i];
		if (framesInCommon != 0)
			str += "\n\t... " + framesInCommon + " more";

		// Recurse if we have a cause
		Throwable ourCause = cause.getCause();
		if (ourCause != null)
			str += "\n"+getStackTraceAsCause(ourCause, trace);
		return str;
	}

	private static String getStackTrace(Throwable t) {
		String str = t.toString();

		StackTraceElement[] trace = t.getStackTrace();
		for (int i=0; i < trace.length; i++)
			str += "\n\tat " + trace[i];

		Throwable ourCause = t.getCause();
		if (ourCause != null) {
			str += "\n"+getStackTraceAsCause(ourCause, trace);
		}
		return str;
	}
	
	public BugReport() {
		
	}
	
	public BugReport(Throwable t, String metadata,
			String appName, ApplicationVersion appVersion, Object buggyComp) {
		this.t = t;
		this.appVersion = appVersion;
		
		String summary = "Bug in " + appName;
		StringBuilder descBuild = new StringBuilder();
		descBuild.append("Steps to reproduce: (PLEASE FILL IN)\n\n");
		descBuild.append("Other info: (PLEASE FILL IN)\n\n");
		descBuild.append("Application: ").append(appName).append("\n");
		descBuild.append("Version: ").append(appVersion).append("\n");
		descBuild.append("Build Type: ").append(ServerPrefUtils.SERVER_PREFS.getBuildType());
		
		descBuild.append("\nJava Version: ").append(System.getProperty("java.version"));
		descBuild.append(" (").append(System.getProperty("java.vendor")).append(")");
		descBuild.append("\nOperating System: ").append(System.getProperty("os.name"));
		descBuild.append(" (arch: ").append(System.getProperty("os.arch"));
		descBuild.append(", version: ").append(System.getProperty("os.version")+")");
		
		Runtime runtime = Runtime.getRuntime();

	    NumberFormat format = NumberFormat.getInstance();

	    long bytesPerMB = 1024*1014;
	    long maxMemory = runtime.maxMemory() / bytesPerMB;
	    long allocatedMemory = runtime.totalMemory() / bytesPerMB;
	    long freeMemory = runtime.freeMemory() / bytesPerMB;
	    
		descBuild.append("\nJVM Memory (MB): limit=").append(format.format(maxMemory));
		descBuild.append(", allocated=").append(format.format(allocatedMemory));
		descBuild.append(", free=").append(format.format(freeMemory));
		
		if (t != null) {
			if (t.getCause() != null) {
				Throwable rootCause = t.getCause();
				while (rootCause.getCause() != null)
					rootCause = rootCause.getCause();
				descBuild.append("\n\nRoot cause exception:\n```\n");
				descBuild.append(getStackTrace(rootCause)+"\n```\n");
			}
			descBuild.append("\n\nException:\n```\n").append(getStackTrace(t)).append("\n```\n");
		}
		if (metadata != null && metadata.length() > 0) {
			descBuild.append("\n\nMetadata:\n").append(metadata);
		}
		
		Component component;
		if (buggyComp == null)
			component = null;
		else
			component = Component.fromClass(buggyComp.getClass());
		String keywords = appName;
		Type type = Type.BUG;
		
		init(summary, descBuild.toString(), type, component, keywords);
	}
	
	public BugReport(String summary, String description, Type type,
			Component component, String keywords) {
		init(summary, description, type, component, keywords);
	}
	
	private void init(String summary, String description, Type type,
			Component component, String keywords) {
		this.summary = summary;
		this.description = description;
		this.type = type;
		this.component = component;
		this.keywords = keywords;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public Component getComponent() {
		return component;
	}

	public void setComponent(Component component) {
		this.component = component;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}
	
	public URL buildIssueURL() throws MalformedURLException {
		String link = GITHUB_NEW_ISSUE_URL;
		
		try {
			ArrayList<String> args = new ArrayList<String>();
			if (summary != null)
				args.add("title="+URLEncoder.encode(summary, enc));
			if (type != null)
				args.add("labels="+URLEncoder.encode(type.toString(), enc));
			// TODO
//			if (component != null)
//				args.add("component="+URLEncoder.encode(component.toString(), enc));
//			if (keywords != null)
//				args.add("keywords="+URLEncoder.encode(keywords, enc));
//			if (appVersion != null)
//				args.add("version="+URLEncoder.encode(appVersion.toString(), enc));
			if (description != null)
				args.add("body="+URLEncoder.encode(description, enc));
			
			for (int i=0; i<args.size(); i++) {
				if (i == 0)
					link += "?";
				else
					link += "&";
				link += args.get(i);
			}
			System.out.println(link);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		
		return new URL(link);
	}
	
	public Throwable getThrowable() {
		return t;
	}

}
