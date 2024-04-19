/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.action;

public interface ExplorerAction<T> {

	void onBeforeAction();

	void execute();

	void onAfterAction(T result);

	void onError(String errorTitle, Exception e);
}