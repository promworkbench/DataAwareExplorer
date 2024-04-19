/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.model;

import java.util.Collections;
import java.util.Set;

public class FilterConfiguration {

	public enum SelectionFilterMode {
		AND("All occured (AND)"), OR("One occured (OR)"), NEVER("Never occurs (NEVER)"), NONE("No filter");

		private final String desc;

		private SelectionFilterMode(String desc) {
			this.desc = desc;
		}

		@Override
		public String toString() {
			return desc;
		}

	}

	private String filterQuery = "";
	private SelectionFilterMode selectionFilterMode = SelectionFilterMode.NONE;
	private Set<Object> selectedNodes = Collections.emptySet();

	private boolean queryDirty = true;
	private boolean selectionDirty = true;
	private boolean selectionModeDirty = true;

	public FilterConfiguration() {
		super();
	}

	public String getFilterQuery() {
		return filterQuery;
	}

	public void setFilterQuery(String filterQuery) {
		if (!filterQuery.equals(this.filterQuery)) {
			queryDirty = true;
		}
		this.filterQuery = filterQuery;
	}

	public SelectionFilterMode getSelectionFilterMode() {
		return selectionFilterMode;
	}

	public void setSelectionFilterMode(SelectionFilterMode selectionFilterMode) {
		if (selectionFilterMode != this.selectionFilterMode) {
			selectionModeDirty = true;
		}
		this.selectionFilterMode = selectionFilterMode;
	}

	public Set<Object> getSelectedNodes() {
		return selectedNodes;
	}

	public void setSelectedNodes(Set<Object> selectedNodes) {
		this.selectedNodes = selectedNodes;
	}

	public boolean isQueryDirty() {
		return queryDirty;
	}

	public boolean isSelectionDirty() {
		return selectionDirty;
	}

	public boolean isSelectionModeDirty() {
		return selectionModeDirty;
	}

	public void resetDirtyFlags() {
		queryDirty = false;
		selectionDirty = false;
		selectionModeDirty = false;
	}

}
