package org.opensha.commons.util.bugReports;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.ServerPrefUtils;


public class BugReport {

	public static String TRAC_URL = "http://opensha.org/trac";
	public static String TRAC_NEW_TICKET_URL = TRAC_URL+"/newticket";
	private static String enc = "UTF-8";
	
	public enum Type {
		DEFECT("defect"),
		ENHANCEMENT("enhancement"),
		TASK("task");
		
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
		TRAC_SITE("trac site"),
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
	private String reporter;
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
		String description = "Steps to reproduce: (PLEASE FILL IN)\n\n" +
						"Other info: (PLEASE FILL IN)\n\n" +
						"Application: " + appName + "\n" +
						"Version: " + appVersion + "\n" +
						"Bulid Type: " + ServerPrefUtils.SERVER_PREFS.getBuildType();
		
		description += "\nJava Version: "+System.getProperty("java.version");
		description += " ("+System.getProperty("java.vendor")+")";
		description += "\nOperating System: "+System.getProperty("os.name");
		description += " (arch: "+System.getProperty("os.arch");
		description += ", version: "+System.getProperty("os.version")+")";
		
		if (t != null) {
			description += "\n\nException:\n{{{\n" + getStackTrace(t)+"\n}}}\n";
		}
		if (metadata != null && metadata.length() > 0) {
			description += "\n\nMetadata:\n" + metadata;
		}
		
		Component component;
		if (buggyComp == null)
			component = null;
		else
			component = Component.fromClass(buggyComp.getClass());
		String keywords = appName;
		Type type = Type.DEFECT;
		
		init(summary, description, type, null, component, keywords);
	}
	
	public BugReport(String summary, String description, Type type,
			String reporter, Component component, String keywords) {
		init(summary, description, type, reporter, component, keywords);
	}
	
	private void init(String summary, String description, Type type,
			String reporter, Component component, String keywords) {
		this.summary = summary;
		this.description = description;
		this.type = type;
		this.reporter = reporter;
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

	public String getReporter() {
		return reporter;
	}

	public void setReporter(String reporter) {
		this.reporter = reporter;
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
	
	public URL buildTracURL() throws MalformedURLException {
		String link = TRAC_NEW_TICKET_URL;
		
		try {
			ArrayList<String> args = new ArrayList<String>();
			if (summary != null)
				args.add("summary="+URLEncoder.encode(summary, enc));
			if (type != null)
				args.add("type="+URLEncoder.encode(type.toString(), enc));
			if (reporter != null)
				args.add("reporter="+URLEncoder.encode(reporter, enc));
			if (component != null)
				args.add("component="+URLEncoder.encode(component.toString(), enc));
			if (keywords != null)
				args.add("keywords="+URLEncoder.encode(keywords, enc));
			if (appVersion != null)
				args.add("version="+URLEncoder.encode(appVersion.toString(), enc));
			if (description != null)
				args.add("description="+URLEncoder.encode(description, enc));
			
			for (int i=0; i<args.size(); i++) {
				if (i == 0)
					link += "?";
				else
					link += "&";
				link += args.get(i);
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		
		return new URL(link);
	}
	
	public Throwable getThrowable() {
		return t;
	}

}
