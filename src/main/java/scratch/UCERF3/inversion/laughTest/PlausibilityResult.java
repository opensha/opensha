package scratch.UCERF3.inversion.laughTest;

import com.google.common.base.Preconditions;

public enum PlausibilityResult {
	
	/**
	 * flag that this rupture passes
	 */
	PASS(true, true),
	/**
	 * flag that this rupture fails, but future matches are possible as the rupture is built
	 */
	FAIL_FUTURE_POSSIBLE(false, true),
	/**
	 * flag that this rupture fails, and no future extension of this rupture could pass
	 */
	FAIL_HARD_STOP(false, false);
	
	private boolean pass;
	private boolean cont;

	private PlausibilityResult(boolean pass, boolean cont) {
		if (pass)
			Preconditions.checkState(cont);
		this.pass = pass;
		this.cont = cont;
	}
	
	public boolean isPass() {
		return pass;
	}
	
	public boolean canContinue() {
		return cont;
	}
	
	public PlausibilityResult logicalAnd(PlausibilityResult result) {
		boolean newPass = pass && result.pass;
		boolean newCont = cont && result.cont;
		if (newPass)
			return PASS;
		// it failed
		if (newCont)
			return FAIL_FUTURE_POSSIBLE;
		return FAIL_HARD_STOP;
	}

	public PlausibilityResult logicalOr(PlausibilityResult result) {
		boolean newPass = pass || result.pass;
		boolean newCont = cont || result.cont;
		if (newPass)
			return PASS;
		// it failed
		if (newCont)
			return FAIL_FUTURE_POSSIBLE;
		return FAIL_HARD_STOP;
	}
	
	
}
