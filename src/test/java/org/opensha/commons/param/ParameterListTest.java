package org.opensha.commons.param;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;


public class ParameterListTest {

    ParameterList l1;
    ParameterList l2;
    ParameterList l3;

    @Before
    public void setUp() {
       l1 = new ParameterList();
       l1.addParameter(new BooleanParameter("bp1", true));
       l1.addParameter(new DoubleParameter("dp1", 1.2));
       l1.addParameter(new BooleanParameter("bp2", true));
       l1.addParameter(new BooleanParameter("bp3", false));
       l2 = new ParameterList();
       l2.addParameter(new DoubleParameter("dp1", 1.2));
       l2.addParameter(new BooleanParameter("bp3", false));
       l2.addParameter(new DoubleParameter("dp2", 100.0));
       l3 = new ParameterList();
       l3.addParameter(new DoubleParameter("dp1", 1.2));
       l3.addParameter(new DoubleParameter("dp3", 1.2));
    }

    @Test
    public void intersection() {
        // intersect l1, l2
        ParameterList intersectList = ParameterList.intersection(l1, l2);
        assertTrue(intersectList.containsParameter("dp1"));
        assertTrue(intersectList.containsParameter("bp3"));
        assertFalse(intersectList.containsParameter("bp1"));
        assertFalse(intersectList.containsParameter("bp2"));
        assertFalse(intersectList.containsParameter("dp2"));
        assertFalse(intersectList.containsParameter("dp3"));
        // intersect l1, l2, l3
        intersectList = ParameterList.intersection(l1, l2, l3);
        assertTrue(intersectList.containsParameter("dp1"));
        assertFalse(intersectList.containsParameter("dp2"));
        assertFalse(intersectList.containsParameter("dp3"));
        assertFalse(intersectList.containsParameter("bp1"));
        assertFalse(intersectList.containsParameter("bp2"));
        assertFalse(intersectList.containsParameter("bp3"));
    }

    @Test
    public void union() {
        // union l1, l2
        ParameterList unionList = ParameterList.union(l1, l2);
        assertTrue(unionList.containsParameter("dp1"));
        assertTrue(unionList.containsParameter("dp2"));
        assertTrue(unionList.containsParameter("bp1"));
        assertTrue(unionList.containsParameter("bp2"));
        assertTrue(unionList.containsParameter("bp3"));
        assertFalse(unionList.containsParameter("dp3"));
        // union l1, l2, l3
        unionList = ParameterList.union(l1, l2, l3);
        assertTrue(unionList.containsParameter("dp1"));
        assertTrue(unionList.containsParameter("dp2"));
        assertTrue(unionList.containsParameter("dp3"));
        assertTrue(unionList.containsParameter("bp1"));
        assertTrue(unionList.containsParameter("bp2"));
        assertTrue(unionList.containsParameter("bp3"));
    }

    @Test
    public void getVisibleParams() {
       ParameterList visible = l1.getVisibleParams();
       assertTrue(visible.isEmpty());
        l1.getParameter("bp1").getEditor().setVisible(true);
        assertTrue(visible.isEmpty());
        visible = l1.getVisibleParams();
        assertTrue(visible.containsParameter("bp1"));
    }
}
