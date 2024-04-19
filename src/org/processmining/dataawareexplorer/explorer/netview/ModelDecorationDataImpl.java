package org.processmining.dataawareexplorer.explorer.netview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizConverter.DecorationKey;
import org.processmining.models.graphbased.AbstractGraphElement;

import com.google.common.collect.ImmutableMap;

public final class ModelDecorationDataImpl implements ModelDecorationData {

	private final Map<AbstractGraphElement, Map<DecorationKey, Object>> decorationMap = new HashMap<>();
	private final Map<AbstractGraphElement, List<JMenuItem>> menuItemMap = new HashMap<>();
	private final Collection<AbstractGraphElement> extraElements = new ArrayList<>();

	public ModelDecorationDataImpl() {
		super();
	}

	public Map<AbstractGraphElement, Map<DecorationKey, Object>> getAttributes() {
		return ImmutableMap.copyOf(decorationMap);
	}

	private Map<DecorationKey, Object> createOrGetDecoration(AbstractGraphElement element) {
		Map<DecorationKey, Object> decoration = decorationMap.get(element);
		if (decoration != null) {
			return decoration;
		}
		Map<DecorationKey, Object> newDecoration = new EnumMap<DecorationKey, Object>(DecorationKey.class);
		Map<DecorationKey, Object> previousKey = decorationMap.put(element, newDecoration);
		if (previousKey == null) {
			return newDecoration;
		} else {
			return previousKey;
		}
	}

	public void clear() {
		decorationMap.clear();
		extraElements.clear();
		menuItemMap.clear();
	}

	public Object putAttribute(AbstractGraphElement element, DecorationKey key, Object value) {
		Map<DecorationKey, Object> internalMap = createOrGetDecoration(element);
		return internalMap.put(key, value);
	}

	public Object removeAttribute(AbstractGraphElement element, DecorationKey key) {
		Map<DecorationKey, Object> internalMap = createOrGetDecoration(element);
		return internalMap.remove(key);
	}

	public Collection<AbstractGraphElement> getExtraElements() {
		return extraElements;
	}

	private List<JMenuItem> createOrGetMenuList(AbstractGraphElement element) {
		List<JMenuItem> menuItemList = menuItemMap.get(element);
		if (menuItemList == null) {
			menuItemList = new ArrayList<>();
			menuItemMap.put(element, menuItemList);
		}
		return menuItemList;
	}

	public void addContextMenuItem(AbstractGraphElement element, JMenuItem menuItem) {
		List<JMenuItem> menuItemList = createOrGetMenuList(element);
		menuItemList.add(menuItem);
	}

	public Map<AbstractGraphElement, List<JMenuItem>> getMenuItems() {
		return ImmutableMap.copyOf(menuItemMap);
	}

}