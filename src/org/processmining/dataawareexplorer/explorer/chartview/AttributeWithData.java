package org.processmining.dataawareexplorer.explorer.chartview;

import com.google.common.collect.Multimap;

public final class AttributeWithData extends Attribute {

	private final Multimap<String, Comparable<?>> values;

	public AttributeWithData(String key, AttributeOrigin attributeOrigin, AttributeOccurence attributeOccurence,
			Class<?> type, Multimap<String, Comparable<?>> values) {
		super(key, attributeOrigin, attributeOccurence, type);
		this.values = values;
	}

	public Multimap<String, Comparable<?>> getValues() {
		return values;
	}

}
