/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.action;

import java.util.concurrent.Executor;

import org.processmining.dataawareexplorer.explorer.ExplorerContext;
import org.processmining.framework.plugin.PluginContext;

abstract public class AbstractExplorerAction<T> implements ExplorerAction<T> {

	private final ExplorerContext explorerContext;

	protected AbstractExplorerAction(ExplorerContext explorerContext) {
		super();
		this.explorerContext = explorerContext;
	}

	protected Executor getExecutor() {
		return explorerContext.getExecutor();
	}

	protected PluginContext getContext() {
		return explorerContext.getContext();
	}

	protected ExplorerContext getExplorerContext() {
		return explorerContext;
	}

	public void onError(String errorTitle, Exception e) {
		getExplorerContext().getUserQuery().showError(errorTitle, e);
	}

}