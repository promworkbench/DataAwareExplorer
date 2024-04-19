/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer;

import java.awt.Component;

import org.processmining.dataawareexplorer.explorer.netview.ModelDecorationData;

public interface NetVisualization {

	void beforeUpdate();

	void afterUpdate();

	void updateData(ModelDecorationData decoration);
	
	void updateUI();

	Component getComponent();

}
