package org.processmining.dataawareexplorer.explorer.traceview;

import java.awt.Component;

public interface TraceView {

	Component getComponent();

	void updateData();

	void updateUI();
	
}
