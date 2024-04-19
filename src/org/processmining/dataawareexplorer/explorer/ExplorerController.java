package org.processmining.dataawareexplorer.explorer;

import org.processmining.dataawareexplorer.explorer.model.ExplorerModel;
import org.processmining.dataawareexplorer.explorer.netview.impl.ViewMode;
import org.processmining.dataawareexplorer.explorer.work.DiscoveryProcessor.DiscoveryResult;

public interface ExplorerController {

	DataAwareExplorer addExplorer(String title, DataAwareExplorer explorer);

	DataAwareExplorer addExplorerWithDiscoveredModel(ExplorerContext explorerContext, ExplorerUpdater explorerUpdater, ExplorerModel explorerModel,
			DiscoveryResult discoveryResult);

	void switchExplorer(DataAwareExplorer explorer, ViewMode viewMode);

}