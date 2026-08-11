package org.opensha.sha.imr.attenRelImpl.nshmp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.opensha.nshmp.shaded.gmm.NshmpGroundMotion;
import org.opensha.nshmp.shaded.tree.NshmpBranch;
import org.opensha.nshmp.shaded.tree.NshmpLogicTree;
import org.opensha.nshmp.shaded.tree.NshmpLogicTree.Builder;

public interface GroundMotionLogicTreeFilter {
	
	public NshmpLogicTree<NshmpGroundMotion> filter(NshmpLogicTree<NshmpGroundMotion> tree);
	
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
		public NshmpLogicTree<NshmpGroundMotion> filter(NshmpLogicTree<NshmpGroundMotion> tree) {
			List<NshmpBranch<NshmpGroundMotion>> matches = new ArrayList<>(expectedSize < 0 ? tree.size() : expectedSize);
			double sumWeight = 0d;
			for (NshmpBranch<NshmpGroundMotion> branch : tree) {
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
				NshmpBranch<NshmpGroundMotion> branch = matches.get(0);
				return NshmpLogicTree.singleton(tree.name(), branch.id(), branch.value());
			}
			Builder<NshmpGroundMotion> builder = NshmpLogicTree.builder(tree.name());
			for (NshmpBranch<NshmpGroundMotion> branch : matches)
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
