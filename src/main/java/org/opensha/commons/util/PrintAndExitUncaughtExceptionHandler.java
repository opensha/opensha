package org.opensha.commons.util;

import java.lang.Thread.UncaughtExceptionHandler;

/**
 * The purpose of this is to ensure that applications exit when exceptions are thrown by
 * threads. Often, an exception will be thrown in a GUI thread and will never be caught.
 * 
 * @author kevin
 *
 */
public class PrintAndExitUncaughtExceptionHandler implements
		UncaughtExceptionHandler {

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		e.printStackTrace();
		System.err.println("PrintAndExitUncaughtExceptionHandler: Exception caught...exiting!");
		System.exit(1);
	}

}
