package org.processmining.dataawareexplorer.explorer.events;

import java.util.Set;

public interface ModelSelectionChangedEvent extends ExplorerEvent {

	Set<Object> getSelectedNodes();

	Set<Object> getPreviouslySelectedNodes();

}