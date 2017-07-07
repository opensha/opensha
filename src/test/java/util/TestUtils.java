package util;

import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class contains utilities used by tests. For instance, private no-arg
 * constructors show up in Corbetura as not being covered. However, they can be
 * hit using reflection so utility methods are provided herein to reduce
 * repetition.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class TestUtils {

	/* Private no-arg constructor invokation via reflection. */
	public static Object callPrivateNoArgConstructor(final Class<?> cls) throws 
			InstantiationException,
			IllegalAccessException,
			InvocationTargetException {
		final Constructor<?> c = cls.getDeclaredConstructors()[0];
		c.setAccessible(true);
		final Object n = c.newInstance((Object[]) null);
		return n;
	}
	
	private static class TestMethodThread implements Runnable {
		
		private Method testMethod;
		private Object testObj;
		
		private Throwable exception;
		
		public TestMethodThread(Method testMethod, Object testObj) {
			this.testMethod = testMethod;
			this.testObj = testObj;
		}

		@Override
		public void run() {
			try {
				testMethod.invoke(testObj);
			} catch (Throwable t) {
				this.exception = t;
			}
		}
		
		public Throwable getException() {
			return exception;
		}
		
	}
	
	/**
	 * This runs a JUnit 4 test method in a separate thread with a timer. If the timeout is
	 * exceeded, then the test fails.
	 * 
	 * @param methodName the name of the test method
	 * @param testObj the object for which method <code>methodName</code> is to be called
	 * @param timeoutSeconds timeout in seconds until the test should fail
	 * @throws Throwable
	 */
	public static void runTestWithTimer(String methodName, Object testObj, int timeoutSeconds) throws Throwable {
		// get the method
		Method testMethod = testObj.getClass().getDeclaredMethod(methodName);
		
		// make sure it's accessible (not private)
		if (!testMethod.isAccessible())
			testMethod.setAccessible(true);
		
		// create the thread which will simply run this test
		TestMethodThread testThread = new TestMethodThread(testMethod, testObj);
		// start the thread
		Thread t = new Thread(testThread);
		t.start();
		// record the start time in milis
		long start = System.currentTimeMillis();
		
		while (t.isAlive()) {
			// seconds that the thread has been running
			double timeSecs = (double)(System.currentTimeMillis() - start) / 1000d;
			if (timeSecs > timeoutSeconds) {
				// if we're here, then it's exceeded it's allotted time. 
				try {
					// try calling interrupt to end any blocking operation
					t.interrupt();
				} catch (Throwable e) {
					e.printStackTrace();
				}
				// now fail
				fail("method '"+methodName+"' exceeded timeout of "+timeoutSeconds+" secs!");
			}
			
			// if we're here then it's still running, but within the time limit. Sleep for 500 milis
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		// see if it ended with an exception, this exception might be an assertion failed exception
		Throwable exception = testThread.getException();
		if (exception != null)
			throwThreadedExceptionAsApplicable(exception);
	}
	
	public static void throwThreadedExceptionAsApplicable(Throwable exception) throws Throwable {
		Throwable cause = exception.getCause();
		// if it's an assertion error, throw that so the failure shows up nicely in JUnit as opposed
		// to an error.
		if (cause != null &&
				(cause instanceof AssertionError || exception instanceof InvocationTargetException))
			throw cause;
		// otherwise it actually is an error (not a failure), throw the exception
		throw exception;
	}

}
