/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.chartview;

import java.awt.Component;

public interface ChartView {

	Component getComponent();

	void updateData();

	void updateUI();

}
