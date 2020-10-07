package scratch.UCERF3.inversion.laughTest;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestPlausbilityResult {

	@Test
	public void testlogicalAnd() {
		
		// ANDing values with themselves does not change value
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.PASS.logicalAnd(PlausibilityResult.PASS));
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalAnd(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.FAIL_HARD_STOP.logicalAnd(PlausibilityResult.FAIL_HARD_STOP));
		
		// PASS and FAIL_FUTURE_POSSIBLE -> FAIL_FUTURE_POSSIBLE
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.PASS.logicalAnd(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalAnd(PlausibilityResult.PASS));
		
		// PASS and FAIL_HARD_STOP -> FAIL_HARD_STOP
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.PASS.logicalAnd(PlausibilityResult.FAIL_HARD_STOP));
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.FAIL_HARD_STOP.logicalAnd(PlausibilityResult.PASS));
		
		// FAIL_HARD_STOP and FAIL_FUTURE_POSSIBLE -> FAIL_HARD_STOP
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.FAIL_HARD_STOP.logicalAnd(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalAnd(PlausibilityResult.FAIL_HARD_STOP));
	}
	
	@Test
	public void testlogicalOr() {
		
		// ANDing values with themselves does not change value
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.PASS.logicalOr(PlausibilityResult.PASS));
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalOr(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.FAIL_HARD_STOP,
				PlausibilityResult.FAIL_HARD_STOP.logicalOr(PlausibilityResult.FAIL_HARD_STOP));
		
		// PASS and FAIL_FUTURE_POSSIBLE -> PASS
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.PASS.logicalOr(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalOr(PlausibilityResult.PASS));
		
		// PASS and FAIL_HARD_STOP -> PASS
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.PASS.logicalOr(PlausibilityResult.FAIL_HARD_STOP));
		assertEquals(PlausibilityResult.PASS,
				PlausibilityResult.FAIL_HARD_STOP.logicalOr(PlausibilityResult.PASS));
		
		// FAIL_HARD_STOP and FAIL_FUTURE_POSSIBLE -> FAIL_FUTURE_POSSIBLE
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.FAIL_HARD_STOP.logicalOr(PlausibilityResult.FAIL_FUTURE_POSSIBLE));
		assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
				PlausibilityResult.FAIL_FUTURE_POSSIBLE.logicalOr(PlausibilityResult.FAIL_HARD_STOP));
	}

}
