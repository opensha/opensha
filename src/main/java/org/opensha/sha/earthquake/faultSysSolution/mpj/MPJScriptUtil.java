package org.opensha.sha.earthquake.faultSysSolution.mpj;

import java.util.Map;

import com.google.common.base.Preconditions;

final class MPJScriptUtil {

	private MPJScriptUtil() {}

	static void putSystemProperty(Map<String, String> properties, String assignment) {
		Preconditions.checkNotNull(assignment, "System property assignment cannot be null");
		int equalsIndex = assignment.indexOf('=');
		Preconditions.checkArgument(equalsIndex > 0,
				"System property must be supplied as key=value: %s", assignment);
		putSystemProperty(properties, assignment.substring(0, equalsIndex), assignment.substring(equalsIndex + 1));
	}

	static void putSystemProperty(Map<String, String> properties, String key, String value) {
		Preconditions.checkNotNull(properties, "System property map cannot be null");
		Preconditions.checkArgument(key != null && !key.isBlank(), "System property key cannot be blank");
		Preconditions.checkArgument(key.indexOf('=') < 0, "System property key cannot contain '=': %s", key);
		for (int i=0; i<key.length(); i++)
			Preconditions.checkArgument(!Character.isWhitespace(key.charAt(i)),
					"System property key cannot contain whitespace: %s", key);
		Preconditions.checkNotNull(value, "System property value cannot be null for key: %s", key);
		properties.put(key, value);
	}

	static void appendSystemProperties(StringBuilder args, Map<String, String> properties) {
		for (Map.Entry<String, String> entry : properties.entrySet())
			args.append(" ").append(shellArgument("-D"+entry.getKey()+"="+entry.getValue()));
	}

	private static String shellArgument(String argument) {
		if (argument.matches("[A-Za-z0-9_./:=,+@%-]+"))
			return argument;
		return "'"+argument.replace("'", "'\"'\"'")+"'";
	}
}
