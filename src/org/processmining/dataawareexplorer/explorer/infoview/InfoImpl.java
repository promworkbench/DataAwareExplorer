package org.processmining.dataawareexplorer.explorer.infoview;

import org.processmining.dataawareexplorer.explorer.infoview.InfoData.Info;
import org.processmining.dataawareexplorer.explorer.infoview.InfoData.InfoType;

public final class InfoImpl implements Info {

	private String label;
	private Object data;
	private InfoType type;

	public InfoImpl(String label, Object data, InfoType type) {
		this.label = label;
		this.data = data;
		this.type = type;
	}

	public String getLabel() {
		return label;
	}

	public Object getData() {
		return data;
	}

	public InfoType getType() {
		return type;
	}

}