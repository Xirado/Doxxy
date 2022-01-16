package com.freya02.docs;

import com.freya02.bot.utils.HTMLElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Map;

public class FieldDoc {
	@NotNull private final ClassDoc classDocs;

	@NotNull private final String fieldName;
	@NotNull private final String fieldType;
	@Nullable private final HTMLElement descriptionElement;

	@NotNull private final String elementId;
	@NotNull private final String url;
	@NotNull private final String modifiers;

	@NotNull private final Map<DocDetailType, List<HTMLElement>> detailToElementsMap;

	@Nullable private final SeeAlso seeAlso;

	public FieldDoc(@NotNull ClassDoc classDocs, @NotNull Element element) {
		this.classDocs = classDocs;

		this.elementId = element.id();
		this.url = classDocs.getURL() + "#" + elementId;

		//Get field modifiers
		final Element modifiersElement = element.selectFirst("div.member-signature > span.modifiers");
		if (modifiersElement == null) throw new DocParseException();
		this.modifiers = modifiersElement.text();

		//Get field name
		final Element fieldNameElement = element.selectFirst("h3");
		if (fieldNameElement == null) throw new DocParseException();
		this.fieldName = fieldNameElement.text();

		//Get field type
		final Element fieldTypeElement = element.selectFirst("div.member-signature > span.return-type");
		if (fieldTypeElement == null) throw new DocParseException();
		this.fieldType = fieldTypeElement.text();

		//Get field description
		final Element descriptionElement = element.selectFirst("section.detail > div.block");
		if (descriptionElement != null) {
			this.descriptionElement = new HTMLElement(descriptionElement);
		} else {
			this.descriptionElement = null;
		}

		this.detailToElementsMap = MethodDoc.getDetailToElementsMap(element);

		final List<HTMLElement> seeAlsoElements = detailToElementsMap.get(DocDetailType.SEE_ALSO);
		if (seeAlsoElements != null) {
			this.seeAlso = new SeeAlso(seeAlsoElements.get(0));
		} else {
			this.seeAlso = null;
		}
	}

	@NotNull
	public ClassDoc getClassDocs() {
		return classDocs;
	}

	@NotNull
	public String getElementId() {
		return elementId;
	}

	@NotNull
	public String getModifiers() {
		return modifiers;
	}

	@NotNull
	public String getFieldName() {
		return fieldName;
	}

	@NotNull
	public String getFieldType() {
		return fieldType;
	}

	@Nullable
	public HTMLElement getDescriptionElement() {
		return descriptionElement;
	}

	@Override
	public String toString() {
		return fieldType + " " + fieldName + " : " + descriptionElement;
	}

	@NotNull
	public String getURL() {
		return url;
	}

	public String getSimpleSignature() {
		return fieldType + " " + fieldName;
	}

	public Map<DocDetailType, List<HTMLElement>> getDetailToElementsMap() {
		return detailToElementsMap;
	}

	@Nullable
	public SeeAlso getSeeAlso() {
		return seeAlso;
	}
}
