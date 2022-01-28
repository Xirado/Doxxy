package com.freya02.docs.data;

import com.freya02.bot.utils.HTMLElement;
import com.freya02.docs.DocParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;

public class FieldDoc extends BaseDoc {
	@NotNull private final ClassDoc classDocs;

	@NotNull private final String fieldName;
	@NotNull private final String fieldType;
	@Nullable private final HTMLElement descriptionElement;

	@NotNull private final String elementId;
	@NotNull private final String url;
	@NotNull private final String modifiers;

	@NotNull private final DetailToElementsMap detailToElementsMap;

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

		this.detailToElementsMap = DetailToElementsMap.parseDetails(element);

		final DocDetail seeAlsoDetail = detailToElementsMap.getDetail(DocDetailType.SEE_ALSO);
		if (seeAlsoDetail != null) {
			this.seeAlso = new SeeAlso(seeAlsoDetail);
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

	@Override
	public String toString() {
		return fieldType + " " + fieldName + " : " + descriptionElement;
	}

	public String getSimpleSignature() {
		return fieldType + " " + fieldName;
	}

	@Override
	@NotNull
	public String getURL() {
		return url;
	}

	@Override
	@Nullable
	public HTMLElement getDescriptionElement() {
		return descriptionElement;
	}

	@Override
	@NotNull
	public DetailToElementsMap getDetailToElementsMap() {
		return detailToElementsMap;
	}

	@Nullable
	public SeeAlso getSeeAlso() {
		return seeAlso;
	}
}