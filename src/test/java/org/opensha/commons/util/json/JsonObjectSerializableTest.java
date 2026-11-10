package org.opensha.commons.util.json;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.GroupedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.SectionGroupingType;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonObjectSerializableTest {

	@Test
	public void testReflectiveAdapterUsesPrivateConstructor() {
		JsonObject json = new JsonObject();
		json.add("value", new JsonPrimitive(42));
		PrivateConstructorValue value =
				new JsonObjectSerializable.ReflectiveJsonObjectAdapter<PrivateConstructorValue>(
						PrivateConstructorValue.class).fromJsonTree(json);
		assertEquals(42, value.value);
	}

	@Test
	public void testSectionDistributionSamplersRoundTrip() {
		assertRoundTrip(new FixedFractileSampler(0.37), FixedFractileSampler.class);
		assertRoundTrip(new GroupedFractileSampler(123456L, SectionGroupingType.PARENT),
				GroupedFractileSampler.class);
	}

	private static <E extends JsonObjectSerializable> void assertRoundTrip(E expected, Class<E> type) {
		JsonObjectSerializable.ReflectiveJsonObjectAdapter<E> adapter =
				new JsonObjectSerializable.ReflectiveJsonObjectAdapter<>(type);
		assertEquals(expected, adapter.fromJsonTree(adapter.toJsonTree(expected)));
	}

	private static final class PrivateConstructorValue implements JsonObjectSerializable {
		private int value;

		private PrivateConstructorValue() {}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("value", new JsonPrimitive(value));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			value = jsonObj.get("value").getAsInt();
		}
	}
}
