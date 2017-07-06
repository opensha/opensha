package util;

import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public class ThreadTest {

	@Before
	public void setUp() throws Exception {
	}
	
	private void failTestMethod() {
		fail("i'm failing and I can't get up!");
	}
	
	private void sleepTestMethod() {
		System.out.println("Starting to sleep...");
		try {
			Thread.sleep(5 * 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Waking up!");
	}
	
	@Test
	public void doTest() throws Throwable {
//		TestUtils.runTestWithTimer("failTestMethod", this, 30);
		TestUtils.runTestWithTimer("sleepTestMethod", this, 2);
	}

}
