/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview;

import org.processmining.dataawareexplorer.explorer.exception.NetVisualizationException;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData;

public interface NetView {

	/**
	 * Called on a background thread whenever the input data changed. For
	 * example, the alignment changed, the current filter changed, etc.
	 * 
	 * @throws NetVisualizationException
	 */
	void updateData() throws NetVisualizationException;

	ModelDecorationData getModelDecorationData();

	InfoData getInfoData();

	/**
	 * Called on the EDT thread after the data has been updated
	 */
	void updateUI();

	ConfigurationPanel getConfigurationPanel();

}
