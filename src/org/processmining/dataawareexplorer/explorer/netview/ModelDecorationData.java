/*
 * Copyright (c) 2015 F. Mannhardt (f.mannhardt@tue.nl)
 */
package org.processmining.dataawareexplorer.explorer.netview;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.models.graphbased.AbstractGraphElement;

public interface ModelDecorationData {

	Map<AbstractGraphElement, Map<DecorationKey, Object>> getAttributes();

	Object putAttribute(AbstractGraphElement element, DecorationKey key, Object value);

	Object removeAttribute(AbstractGraphElement element, DecorationKey key);
	
	Collection<AbstractGraphElement> getExtraElements();

	Map<AbstractGraphElement, List<JMenuItem>> getMenuItems();

	void addContextMenuItem(AbstractGraphElement element, JMenuItem menuItem);

}