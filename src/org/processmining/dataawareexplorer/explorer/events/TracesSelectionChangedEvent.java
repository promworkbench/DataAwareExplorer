package org.processmining.dataawareexplorer.explorer.events;

import java.util.Collection;

import org.processmining.xesalignmentextension.XAlignmentExtension.XAlignment;

public interface TracesSelectionChangedEvent extends ExplorerEvent {

	Collection<XAlignment> getSelectedAlignments();

	Collection<XAlignment> getPreviouslySelectedAlignments();

}