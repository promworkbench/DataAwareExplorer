/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.dataawareexplorer.explorer.ExplorerUpdater;
import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;

public interface NetViewFactory {
	
	NetView newInstance(ExplorerContext context, ExplorerUpdater updater, ExplorerModel explorerInput);
	
}
