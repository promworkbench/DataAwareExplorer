package org.processmining.dataawareexplorer.explorer.infoview;

import java.awt.Component;
import java.util.Set;

public interface InfoView {

	Component getComponent();

	void updateUI(InfoData data, Set<Object> selectedNodes);

}
