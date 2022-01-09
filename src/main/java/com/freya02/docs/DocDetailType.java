package com.freya02.docs;

import org.jetbrains.annotations.Nullable;

public enum DocDetailType {
	PARAMETERS("Parameters:"),
	TYPE_PARAMETERS("Type Parameters:"),
	RETURNS("Returns:"),
	SEE_ALSO("See Also:"),
	SPECIFIED_BY("Specified by:"),
	SINCE("Since:"),
	OVERRIDES("Overrides:"),
	INCUBATING("Incubating:"),
	ALL_KNOWN_IMPLEMENTING_CLASSES("All Known Implementing Classes:"),
	ALL_IMPLEMENTED_INTERFACES("All Implemented Interfaces:"),
	DIRECT_KNOWN_SUBCLASSES("Direct Known Subclasses:"),
	THROWS("Throws:");

	private final String elementText;
	DocDetailType(String elementText) {
		this.elementText = elementText;
	}

	@Nullable
	public static DocDetailType parseType(String elementText) {
		for (DocDetailType type : values()) {
			if (type.elementText.equals(elementText)) {
				return type;
			}
		}

		return null;
	}
}
