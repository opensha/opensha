package org.opensha.commons.logicTree;

import static org.junit.Assert.*;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestLogicTreeBranch {

    @Test
    public void testFileBackedJSON() throws IOException {
        List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
        List<LogicTreeNode> values = new ArrayList<>();

        LogicTreeNode.FileBackedNode node = new LogicTreeNode.FileBackedNode("Node 1", "Node1", 1d, "n1");
        levels.add(new LogicTreeLevel.FileBackedLevel("Level 1", "Level1", node));
        values.add(node);

        node = new LogicTreeNode.FileBackedNode("Node 2", "Node2", 1d, "n2");
        levels.add(new LogicTreeLevel.FileBackedLevel("Level 2", "Level2", node));
        values.add(node);

        node = null;
        levels.add(new LogicTreeLevel.FileBackedLevel("Level 3", "Level3", node));
        values.add(node);

        LogicTreeBranch<?> branch = new LogicTreeBranch<>(levels, values);
        String json = branch.getJSON();
//        System.out.println(json);

        LogicTreeBranch<?> branch2 = new LogicTreeBranch<>();
        branch2.initFromJSON(json);
        String json2 = branch2.getJSON();

        assertEquals(branch, branch2);
        assertEquals(0, branch.getNumAwayFrom(branch2));
        assertEquals(json, json2);
    }

    @Test
    public void testEnumBackedJSON() throws IOException {
        List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
        List<LogicTreeNode> values = new ArrayList<>();

        levels.add(LogicTreeLevel.forEnum(FaultModels.class, "Fault Model", "FM"));
        values.add(FaultModels.FM3_1);
        levels.add(LogicTreeLevel.forEnum(DeformationModels.class, "Deformation Model", "DM"));
        values.add(DeformationModels.GEOLOGIC);
        levels.add(LogicTreeLevel.forEnum(InversionModels.class, "Inversion Model", "IM"));
        values.add(null);

        LogicTreeBranch<?> branch = new LogicTreeBranch<>(levels, values);
        String json = branch.getJSON();
//        System.out.println(json);

        LogicTreeBranch<?> branch2 = new LogicTreeBranch<>();
        branch2.initFromJSON(json);
        String json2 = branch2.getJSON();

        assertEquals(branch, branch2);
        assertEquals(0, branch.getNumAwayFrom(branch2));
        assertEquals(json, json2);
    }

    @JsonAdapter(TestAdapterBackedNode.Adapter.class)
    public static class TestAdapterBackedNode implements LogicTreeNode {

        protected int val1 = 0;
        protected double val2 = 0;

        public TestAdapterBackedNode(int val1, double val2){
            this.val1 = val1;
            this.val2 = val2;
        }

        @Override
        public String getName() {
            return "TestAdapterBackedNode";
        }

        @Override
        public String getShortName() {
            return "TestAdapterBackedNode";
        }

        @Override
        public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
            return 0;
        }

        @Override
        public String getFilePrefix() {
            return null;
        }

        @Override
        public boolean equals(Object o){
            if( o instanceof TestAdapterBackedNode){
                TestAdapterBackedNode other = (TestAdapterBackedNode) o;
                return val1 == other.val1 && val2 == other.val2;
            }else{
                return false;
            }
        }

        public static class Adapter extends TypeAdapter<LogicTreeNode> {

            @Override
            public void write(JsonWriter out, LogicTreeNode value) throws IOException {
                TestAdapterBackedNode node = (TestAdapterBackedNode) value;
                out.beginObject();
                out.name("a");
                out.value(node.val1);
                out.name("b");
                out.value(node.val2);
                out.endObject();
            }

            @Override
            public TestAdapterBackedNode read(JsonReader in) throws IOException {
                int val1 = 0;
                double val2 = 0;
                in.beginObject();
                while (in.hasNext()) {
                    switch (in.nextName()) {
                        case "a":
                            val1 = in.nextInt();
                            break;
                        case "b":
                            val2 = in.nextDouble();
                            break;
                    }
                }
                in.endObject();
                TestAdapterBackedNode node = new TestAdapterBackedNode(val1, val2);
                return node;
            }
        }

        public static class TestAdapterBackedLevel extends LogicTreeLevel.AdapterBackedLevel {
            public TestAdapterBackedLevel() {
                super("test adapter backed", "test adapter backed", TestAdapterBackedNode.class);
            }

			@Override
			protected Collection<String> getAffected() {
				return List.of();
			}

			@Override
			protected Collection<String> getNotAffected() {
				return List.of();
			}
        }
    }



    @Test
    public void testAdapterBackedJSON() throws IOException {
        List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
        List<LogicTreeNode> values = new ArrayList<>();

        levels.add(new TestAdapterBackedNode.TestAdapterBackedLevel());
        values.add(new TestAdapterBackedNode(42, 33));

        LogicTreeBranch<?> branch = new LogicTreeBranch<>(levels, values);
        String json = branch.getJSON();
        System.out.println(json);

        LogicTreeBranch<?> branch2 = new LogicTreeBranch<>();
        branch2.initFromJSON(json);
        String json2 = branch2.getJSON();

        assertEquals(branch, branch2);
        assertEquals(0, branch.getNumAwayFrom(branch2));
        assertEquals(json, json2);
    }

    @Test
    public void testGracefulAdapterBackedFailure() throws IOException {
        // JSON with unknown level class and node adapter class
        String json = "[\n" +
                "  {\n" +
                "    \"level\": {\n" +
                "      \"name\": \"test adapter backed\",\n" +
                "      \"shortName\": \"test adapter backed\",\n" +
                "      \"class\": \"UnknownLevel\"\n" +
                "    },\n" +
                "    \"value\": {\n" +
                "      \"name\": \"TestAdapterBackedNode\",\n" +
                "      \"shortName\": \"TestAdapterBackedNode\",\n" +
                "      \"weight\": 0.0,\n" +
                "      \"adapterValue\": {\n" +
                "        \"adapterClass\": \"UnknownAdapter\",\n" +
                "        \"adapterData\": {\n" +
                "          \"a\": 42,\n" +
                "          \"b\": 33.0\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "]\n";

        LogicTreeBranch<?> branch = new LogicTreeBranch<>();
        branch.initFromJSON(json);

        assertEquals( LogicTreeLevel.FileBackedLevel.class, branch.getLevel(0).getClass());
        assertEquals("TestAdapterBackedNode", branch.getValue(0).getName());
    }
}
