package org.opensha.commons.util.bugReports.knownBugImpl;

import org.opensha.commons.util.bugReports.BugReport;
import org.opensha.commons.util.bugReports.KnownBugDetector;

public class ExceptionTypeKnownBugDetector implements KnownBugDetector {
	
	private Class<? extends Throwable> exceptionClass;
	private String desc;
	private String message;
	private boolean messageIsRegex = false;
	
	private String throwingClassName;
	private String methodName;
	
	private boolean canIgnore;
	
	public ExceptionTypeKnownBugDetector(Class<? extends Throwable> exceptionClass, String desc, boolean canIgnore) {
		this(exceptionClass, null, desc, canIgnore);
	}
	
	public ExceptionTypeKnownBugDetector(
			Class<? extends Throwable> exceptionClass,
			String message,
			String desc,
			boolean canIgnore) {
		init(exceptionClass, null, null, message, desc, canIgnore);
	}
	
	public ExceptionTypeKnownBugDetector(Class<? extends Throwable> exceptionClass,
			Class<?> throwingClass,
			String methodName,
			String message,
			String desc,
			boolean canIgnore) {
		this(exceptionClass, throwingClass.getName(), methodName, message, desc, canIgnore);
	}
	
	public ExceptionTypeKnownBugDetector(Class<? extends Throwable> exceptionClass,
			String throwingClassName,
			String methodName,
			String message,
			String desc,
			boolean canIgnore) {
		init(exceptionClass, throwingClassName, methodName, message, desc, canIgnore);
	}
	
	private void init(Class<? extends Throwable> exceptionClass,
			String throwingClassName,
			String methodName,
			String message,
			String desc,
			boolean canIgnore) {
		this.exceptionClass = exceptionClass;
		this.throwingClassName = throwingClassName;
		this.methodName = methodName;
		this.desc = desc;
		this.message = message;
		this.canIgnore = canIgnore;
	}
	
	public ExceptionTypeKnownBugDetector setMessageAsRegex() {
		messageIsRegex = true;
		// return this so it can be called inline with the constructor;
		return this;
	}

	@Override
	public boolean isKnownBug(BugReport bug) {
		Throwable t = bug.getThrowable();
		return isExceptionMatch(t);
	}
	
	private boolean isExceptionMatch(Throwable t) {
		if (t == null)
			return false;
		if (t.getCause() != null && isExceptionMatch(t.getCause()))
			return true;
		if (t.getClass().equals(exceptionClass)) {
			// if the exception type is a match, then return true if our message
			// to match is null, or the message from the exception is not null and
			// starts with our message.
			
			// check against throwing class/method name
			if (throwingClassName != null && !throwingClassName.isEmpty()) {
				String className = t.getStackTrace()[0].getClassName();
				// use startswith because of potential subclasses
				if (!className.startsWith(throwingClassName))
					return false;
			}
			if (methodName != null) {
				String actualMethodName = t.getStackTrace()[0].getMethodName();
				if (!methodName.equals(actualMethodName))
					return false;
			}
			
			if (message == null) {
				return true;
			} else if (t.getMessage() != null) {
				if (messageIsRegex)
					return t.getMessage().matches(message);
				else
					return t.getMessage().startsWith(message);
			}
		}
		return false;
	}

	@Override
	public String getKnownBugDescription() {
		return desc;
	}

	@Override
	public boolean canIgnore() {
		return canIgnore;
	}

}
