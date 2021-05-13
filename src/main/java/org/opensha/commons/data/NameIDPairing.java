package org.opensha.commons.data;

public class NameIDPairing implements Named {

	private int id;
	private String name;
	
	public NameIDPairing(String name, int id) {
		this.id = id;
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public int getID() {
		return id;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NameIDPairing other = (NameIDPairing) obj;
		if (id != other.id)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
