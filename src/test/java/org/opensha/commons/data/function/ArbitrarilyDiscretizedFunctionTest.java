package org.opensha.commons.data.function;

import org.opensha.commons.data.function.AbstractDiscretizedFuncTest;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;

public class ArbitrarilyDiscretizedFunctionTest extends AbstractDiscretizedFuncTest {

	@Override
	DiscretizedFunc newEmptyDataSet() {
		return new ArbitrarilyDiscretizedFunc();
	}

	@Override
	public boolean isArbitrarilyDiscretized() {
		return true;
	}

}
