package org.opensha.sha.imr.attenRelImpl.nshmp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.gmm.GroundMotion;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree;
import gov.usgs.earthquake.nshmp.tree.LogicTree.Builder;

public interface GroundMotionLogicTreeFilter {
	
	public LogicTree<GroundMotion> filter(LogicTree<GroundMotion> tree);
	
	@JsonAdapter(StringMatchingAdapter.class)
	public static class StringMatching implements GroundMotionLogicTreeFilter {
		
		private String[] required;
		private int expectedSize = -1;

		public StringMatching(String... required) {
			Preconditions.checkArgument(required != null && required.length > 0);
			this.required = required;
		}
		
		public String[] getRequired() {
			return required;
		}

		@Override
		public LogicTree<GroundMotion> filter(LogicTree<GroundMotion> tree) {
			List<Branch<GroundMotion>> matches = new ArrayList<>(expectedSize < 0 ? tree.size() : expectedSize);
			double sumWeight = 0d;
			for (Branch<GroundMotion> branch : tree) {
				boolean match = true;
				String id = branch.id();
				for (int r=0; match && r<required.length; r++)
					match = id.contains(required[r]);
				if (match) {
					matches.add(branch);
					sumWeight += branch.weight();
				}
			}
			Preconditions.checkState(!matches.isEmpty() && sumWeight > 0d);
			expectedSize = matches.size();
			if (matches.size() == 1) {
				Branch<GroundMotion> branch = matches.get(0);
				return LogicTree.singleton(tree.name(), branch.id(), branch.value());
			}
			Builder<GroundMotion> builder = LogicTree.builder(tree.name());
			for (Branch<GroundMotion> branch : matches)
				builder.addBranch(branch.id(), branch.value(), branch.weight()/sumWeight);
			return builder.build();
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("StringMatching[");
			for (int i=0; i<required.length; i++) {
				if (i > 0)
					ret.append(", ");
				ret.append("'").append(required[i]).append("'");
			}
			ret.append("]");
			return ret.toString();
		}
		
	}
	
	public static class StringMatchingAdapter extends TypeAdapter<StringMatching> {

		@Override
		public void write(JsonWriter out, StringMatching value) throws IOException {
			out.beginArray();
			for (String string : value.required)
				out.value(string);
			out.endArray();
		}

		@Override
		public StringMatching read(JsonReader in) throws IOException {
			in.beginArray();
			List<String> required = new ArrayList<>();
			while (in.hasNext())
				required.add(in.nextString());
			in.endArray();
			return new StringMatching(required.toArray(new String[0]));
		}
		
	}

}
