package org.processmining.dataawareexplorer.explorer.infoview;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public final class InfoDataImpl implements InfoData {

	private final ListMultimap<Object, Info> localInfo = ArrayListMultimap.create();
	private final List<Info> globalInfo = new ArrayList<Info>();

	public void addGlobal(String label, Object data, InfoType type) {
		globalInfo.add(new InfoImpl(label, data, type));
	}

	public void addLocal(Object localElement, String label, Object data, InfoType type) {
		localInfo.put(localElement, new InfoImpl(label, data, type));
	}

	public ListMultimap<Object, Info> getLocalInfo() {
		return localInfo;
	}

	public List<Info> getGlobalInfo() {
		return globalInfo;
	}

	public void clear() {
		globalInfo.clear();
		localInfo.clear();
	}

}