package org.processmining.dataawareexplorer.explorer.infoview;

import java.util.List;

import com.google.common.collect.ListMultimap;

public interface InfoData {

	public enum InfoType {
		PERCENTAGE, NUMBER, INTEGER, STRING, TIME
	}

	public interface Info {

		String getLabel();

		Object getData();

		InfoData.InfoType getType();

	}

	List<Info> getGlobalInfo();

	ListMultimap<Object, Info> getLocalInfo();

}