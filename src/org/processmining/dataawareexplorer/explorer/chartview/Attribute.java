package org.processmining.dataawareexplorer.explorer.chartview;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

public class Attribute implements Comparable<Attribute> {

	public enum AttributeOccurence {
		ANYWHERE("All values"), // 
		PRE("Values before selection"), // 
		POST("Values after selection"), //
		WRITTEN("Values written by selection"); //

		private final String desc;

		private AttributeOccurence(String desc) {
			this.desc = desc;
		}

		public String toString() {
			return desc;
		}

	}

	public enum AttributeOrigin {
		VARIABLE_PROCESS("Variable (Process values)"), //
		VARIABLE_LOG("Variable (Log values)"), // 
		VARIABLE_LOG_INVALID("Variable (Wrong values)"), // 
		UNMAPPED_EVENT("Log"), //  
		ALIGNMENT_EVENT("Alignment"), // 
		ALIGNMENT_TRACE("Alignment"), // 
		TIME("Time");

		private final String desc;

		private AttributeOrigin(String desc) {
			this.desc = desc;
		}

		public String toString() {
			return desc;
		}

	}

	private final String key;
	private final Class<?> type;
	private final AttributeOrigin attributeOrigin;
	private final AttributeOccurence attributeOccurence;

	public Attribute(String key, AttributeOrigin attributeOrigin, AttributeOccurence attributeOccurence,
			Class<?> type) {
		this.key = key;
		this.attributeOrigin = attributeOrigin;
		this.attributeOccurence = attributeOccurence;
		this.type = type;
	}

	public AttributeOrigin getOrigin() {
		return attributeOrigin;
	}

	public AttributeOccurence getOccurence() {
		return attributeOccurence;
	}

	public String getKey() {
		return key;
	}

	public Class<?> getType() {
		return type;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((attributeOrigin == null) ? 0 : attributeOrigin.hashCode());
		result = prime * result + ((attributeOccurence == null) ? 0 : attributeOccurence.hashCode());
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Attribute other = (Attribute) obj;
		if (attributeOrigin != other.attributeOrigin)
			return false;
		if (attributeOccurence != other.attributeOccurence)
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}

	public int compareTo(Attribute o) {
		return ComparisonChain.start().compare(this.getOccurence(), o.getOccurence(), Ordering.natural().nullsFirst())
				.compare(this.getOrigin(), o.getOrigin(), Ordering.natural().nullsFirst())
				.compare(this.getKey(), o.getKey(), Ordering.natural().nullsFirst()).result();
	}

	public String toString() {
		return attributeOrigin + ": " + key;
	}

}