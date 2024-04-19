package org.processmining.dataawareexplorer.explorer;

import java.util.concurrent.ExecutorService;

import org.processmining.framework.plugin.PluginContext;
import org.processmining.logenhancement.view.LogViewContext;

public interface ExplorerContext {

	ExecutorService getExecutor();

	ExplorerInterface getUserQuery();

	LogViewContext getLogViewContext();

	@Deprecated
	PluginContext getContext();

}
