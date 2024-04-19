/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import org.processmining.dataawareexplorer.explorer.events.ExplorerEvent;

import com.google.common.eventbus.EventBus;

public interface ExplorerUpdater {

	EventBus getEventBus();

	void post(ExplorerEvent event);

}