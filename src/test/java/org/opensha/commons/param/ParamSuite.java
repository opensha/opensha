package org.opensha.commons.param;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.param.editor.NewParameterEditorTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	NewParameterEditorTest.class,
	AbstractParamTest.class
})

public class ParamSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(ParamSuite.class);
	}
}
