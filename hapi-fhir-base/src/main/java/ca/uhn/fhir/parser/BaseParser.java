package ca.uhn.fhir.parser;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2016 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.instance.model.api.IBaseHasModifierExtensions;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IDomainResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeDeclaredChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum;
import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimeChildContainedResources;
import ca.uhn.fhir.context.RuntimeChildNarrativeDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.BundleEntry;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ISupportsUndeclaredExtensions;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.Tag;
import ca.uhn.fhir.model.api.TagList;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

public abstract class BaseParser implements IParser {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(BaseParser.class);

	private ContainedResources myContainedResources;
	private FhirContext myContext;
	private Set<String> myDontEncodeElements;
	private boolean myDontEncodeElementsIncludesStars;
	private Set<String> myEncodeElements;
	private Set<String> myEncodeElementsAppliesToResourceTypes;
	private boolean myEncodeElementsIncludesStars;
	private IIdType myEncodeForceResourceId;
	private IParserErrorHandler myErrorHandler;
	private boolean myOmitResourceId;
	private List<Class<? extends IBaseResource>> myPreferTypes;
	private String myServerBaseUrl;
	private boolean myStripVersionsFromReferences = true;
	private boolean mySummaryMode;
	private boolean mySuppressNarratives;

	/**
	 * Constructor
	 * 
	 * @param theParserErrorHandler
	 */
	public BaseParser(FhirContext theContext, IParserErrorHandler theParserErrorHandler) {
		myContext = theContext;
		myErrorHandler = theParserErrorHandler;
	}

	protected Iterable<CompositeChildElement> compositeChildIterator(IBase theCompositeElement, final boolean theContainedResource, final CompositeChildElement theParent) {
		
		BaseRuntimeElementCompositeDefinition<?> elementDef = (BaseRuntimeElementCompositeDefinition<?>) myContext.getElementDefinition(theCompositeElement.getClass());
		final List<BaseRuntimeChildDefinition> children = elementDef.getChildrenAndExtension();
		
		return new Iterable<BaseParser.CompositeChildElement>() {
			@Override
			public Iterator<CompositeChildElement> iterator() {

				return new Iterator<CompositeChildElement>() {
					private Iterator<? extends BaseRuntimeChildDefinition> myChildrenIter;
					private Boolean myHasNext = null;
					private CompositeChildElement myNext;

					/**
					 * Constructor
					 */
					{
						myChildrenIter = children.iterator();
					}

					@Override
					public boolean hasNext() {
						if (myHasNext != null) {
							return myHasNext;
						}

						myNext = null;
						do {
							if (myChildrenIter.hasNext() == false) {
								myHasNext = Boolean.FALSE;
								return false;
							}

							myNext = new CompositeChildElement(theParent, myChildrenIter.next());

							/*
							 * There are lots of reasons we might skip encoding a particular child
							 */
//							if (myNext.getDef().getElementName().equals("extension") || myNext.getDef().getElementName().equals("modifierExtension")) {
//								myNext = null;
//							} else 
							if (myNext.getDef().getElementName().equals("id")) {
								myNext = null;
							} else if (!myNext.shouldBeEncoded()) {
								myNext = null;
							} else if (isSummaryMode() && !myNext.getDef().isSummary()) {
								myNext = null;
							} else if (myNext.getDef() instanceof RuntimeChildNarrativeDefinition) {
								if (isSuppressNarratives() || isSummaryMode()) {
									myNext = null;
								} else if (theContainedResource) {
									myNext = null;
								}
							} else if (myNext.getDef() instanceof RuntimeChildContainedResources) {
								if (theContainedResource) {
									myNext = null;
								}
							}

						} while (myNext == null);

						myHasNext = true;
						return true;
					}

					@Override
					public CompositeChildElement next() {
						if (myHasNext == null) {
							if (!hasNext()) {
								throw new IllegalStateException();
							}
						}
						CompositeChildElement retVal = myNext;
						myNext = null;
						myHasNext = null;
						return retVal;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	private void containResourcesForEncoding(ContainedResources theContained, IBaseResource theResource, IBaseResource theTarget) {
		Set<String> allIds = new HashSet<String>();
		Map<String, IBaseResource> existingIdToContainedResource = null;

		if (theTarget instanceof IResource) {
			List<? extends IResource> containedResources = ((IResource) theTarget).getContained().getContainedResources();
			for (IResource next : containedResources) {
				String nextId = next.getId().getValue();
				if (StringUtils.isNotBlank(nextId)) {
					if (!nextId.startsWith("#")) {
						nextId = '#' + nextId;
					}
					allIds.add(nextId);
					if (existingIdToContainedResource == null) {
						existingIdToContainedResource = new HashMap<String, IBaseResource>();
					}
					existingIdToContainedResource.put(nextId, next);
				}
			}
		} else if (theTarget instanceof IDomainResource) {
			List<? extends IAnyResource> containedResources = ((IDomainResource) theTarget).getContained();
			for (IAnyResource next : containedResources) {
				String nextId = next.getIdElement().getValue();
				if (StringUtils.isNotBlank(nextId)) {
					if (!nextId.startsWith("#")) {
						nextId = '#' + nextId;
					}
					allIds.add(nextId);
					if (existingIdToContainedResource == null) {
						existingIdToContainedResource = new HashMap<String, IBaseResource>();
					}
					existingIdToContainedResource.put(nextId, next);
				}
			}
		} else {
			// no resources to contain
		}

		{
			List<IBaseReference> allElements = myContext.newTerser().getAllPopulatedChildElementsOfType(theResource, IBaseReference.class);
			for (IBaseReference next : allElements) {
				IBaseResource resource = next.getResource();
				if (resource != null) {
					if (resource.getIdElement().isEmpty() || resource.getIdElement().isLocal()) {
						if (theContained.getResourceId(resource) != null) {
							// Prevent infinite recursion if there are circular loops in the contained resources
							continue;
						}
						theContained.addContained(resource);
					} else {
						continue;
					}

					containResourcesForEncoding(theContained, resource, theTarget);
				} else if (next.getReferenceElement().isLocal()) {
					if (existingIdToContainedResource != null) {
						IBaseResource potentialTarget = existingIdToContainedResource.remove(next.getReferenceElement().getValue());
						if (potentialTarget != null) {
							theContained.addContained(next.getReferenceElement(), potentialTarget);
							containResourcesForEncoding(theContained, potentialTarget, theTarget);
						}
					}
				}
			}
		}

	}

	protected void containResourcesForEncoding(IBaseResource theResource) {
		ContainedResources contained = new ContainedResources();
		containResourcesForEncoding(contained, theResource, theResource);
		myContainedResources = contained;
	}

	private String determineReferenceText(IBaseReference theRef) {
		IIdType ref = theRef.getReferenceElement();
		if (isBlank(ref.getIdPart())) {
			String reference = ref.getValue();
			if (theRef.getResource() != null) {
				IIdType containedId = getContainedResources().getResourceId(theRef.getResource());
				if (containedId != null && !containedId.isEmpty()) {
					if (containedId.isLocal()) {
						reference = containedId.getValue();
					} else {
						reference = "#" + containedId.getValue();
					}
				} else {
					IIdType refId = theRef.getResource().getIdElement();
					if (refId != null) {
						if (refId.hasIdPart()) {
							if (!refId.hasResourceType()) {
								refId = refId.withResourceType(myContext.getResourceDefinition(theRef.getResource()).getName());
							}
							if (isStripVersionsFromReferences()) {
								reference = refId.toVersionless().getValue();
							} else {
								reference = refId.getValue();
							}
						}
					}
				}
			}
			return reference;
		} else {
			if (!ref.hasResourceType() && !ref.isLocal() && theRef.getResource() != null) {
				ref = ref.withResourceType(myContext.getResourceDefinition(theRef.getResource()).getName());
			}
			if (isNotBlank(myServerBaseUrl) && StringUtils.equals(myServerBaseUrl, ref.getBaseUrl())) {
				if (isStripVersionsFromReferences()) {
					return ref.toUnqualifiedVersionless().getValue();
				} else {
					return ref.toUnqualified().getValue();
				}
			} else {
				if (isStripVersionsFromReferences()) {
					return ref.toVersionless().getValue();
				} else {
					return ref.getValue();
				}
			}
		}
	}

	protected String determineResourceBaseUrl(String bundleBaseUrl, BundleEntry theEntry) {
		IResource resource = theEntry.getResource();
		if (resource == null) {
			return null;
		}

		String resourceBaseUrl = null;
		if (resource.getId() != null && resource.getId().hasBaseUrl()) {
			if (!resource.getId().getBaseUrl().equals(bundleBaseUrl)) {
				resourceBaseUrl = resource.getId().getBaseUrl();
			}
		}
		return resourceBaseUrl;
	}

	protected abstract void doEncodeBundleToWriter(Bundle theBundle, Writer theWriter) throws IOException, DataFormatException;

	protected abstract void doEncodeResourceToWriter(IBaseResource theResource, Writer theWriter) throws IOException, DataFormatException;

	protected abstract <T extends IBaseResource> T doParseResource(Class<T> theResourceType, Reader theReader) throws DataFormatException;

	@Override
	public String encodeBundleToString(Bundle theBundle) throws DataFormatException {
		if (theBundle == null) {
			throw new NullPointerException("Bundle can not be null");
		}
		StringWriter stringWriter = new StringWriter();
		try {
			encodeBundleToWriter(theBundle, stringWriter);
		} catch (IOException e) {
			throw new Error("Encountered IOException during write to string - This should not happen!");
		}

		return stringWriter.toString();
	}

	@Override
	public final void encodeBundleToWriter(Bundle theBundle, Writer theWriter) throws IOException, DataFormatException {
		Validate.notNull(theBundle, "theBundle must not be null");
		Validate.notNull(theWriter, "theWriter must not be null");
		doEncodeBundleToWriter(theBundle, theWriter);
	}

	@Override
	public String encodeResourceToString(IBaseResource theResource) throws DataFormatException {
		Writer stringWriter = new StringWriter();
		try {
			encodeResourceToWriter(theResource, stringWriter);
		} catch (IOException e) {
			throw new Error("Encountered IOException during write to string - This should not happen!");
		}
		return stringWriter.toString();
	}

	@Override
	public final void encodeResourceToWriter(IBaseResource theResource, Writer theWriter) throws IOException, DataFormatException {
		Validate.notNull(theResource, "theResource can not be null");
		Validate.notNull(theWriter, "theWriter can not be null");

		if (theResource.getStructureFhirVersionEnum() != myContext.getVersion().getVersion()) {
			throw new IllegalArgumentException("This parser is for FHIR version " + myContext.getVersion().getVersion() + " - Can not encode a structure for version " + theResource.getStructureFhirVersionEnum());
		}
		
		doEncodeResourceToWriter(theResource, theWriter);
	}

	@Override
	public String encodeTagListToString(TagList theTagList) {
		Writer stringWriter = new StringWriter();
		try {
			encodeTagListToWriter(theTagList, stringWriter);
		} catch (IOException e) {
			throw new Error("Encountered IOException during write to string - This should not happen!");
		}
		return stringWriter.toString();
	}

	private void filterCodingsWithNoCodeOrSystem(List<? extends IBaseCoding> tagList) {
		for (int i = 0; i < tagList.size(); i++) {
			if (isBlank(tagList.get(i).getCode()) && isBlank(tagList.get(i).getSystem())) {
				tagList.remove(i);
				i--;
			}
		}
	}

	protected IIdType fixContainedResourceId(String theValue) {
		IIdType retVal = (IIdType) myContext.getElementDefinition("id").newInstance();
		if (StringUtils.isNotBlank(theValue) && theValue.charAt(0) == '#') {
			retVal.setValue(theValue.substring(1));
		} else {
			retVal.setValue(theValue);
		}
		return retVal;
	}

	ContainedResources getContainedResources() {
		return myContainedResources;
	}

	/**
	 * See {@link #setEncodeElements(Set)}
	 */
	@Override
	public Set<String> getEncodeElements() {
		return myEncodeElements;
	}

	/**
	 * See {@link #setEncodeElementsAppliesToResourceTypes(Set)}
	 */
	@Override
	public Set<String> getEncodeElementsAppliesToResourceTypes() {
		return myEncodeElementsAppliesToResourceTypes;
	}

	@Override
	public IIdType getEncodeForceResourceId() {
		return myEncodeForceResourceId;
	}

	protected IParserErrorHandler getErrorHandler() {
		return myErrorHandler;
	}

	protected TagList getMetaTagsForEncoding(IResource theIResource) {
		TagList tags = ResourceMetadataKeyEnum.TAG_LIST.get(theIResource);
		if (shouldAddSubsettedTag()) {
			tags = new TagList(tags);
			tags.add(new Tag(Constants.TAG_SUBSETTED_SYSTEM, Constants.TAG_SUBSETTED_CODE, subsetDescription()));
		}

		return tags;
	}

	@Override
	public List<Class<? extends IBaseResource>> getPreferTypes() {
		return myPreferTypes;
	}

	@SuppressWarnings("deprecation")
	protected <T extends IPrimitiveType<String>> List<T> getProfileTagsForEncoding(IBaseResource theResource, List<T> theProfiles) {
		switch (myContext.getAddProfileTagWhenEncoding()) {
		case NEVER:
			return theProfiles;
		case ONLY_FOR_CUSTOM:
			RuntimeResourceDefinition resDef = myContext.getResourceDefinition(theResource);
			if (resDef.isStandardType()) {
				return theProfiles;
			}
			break;
		case ALWAYS:
			break;
		}

		if (myContext.getVersion().getVersion().isNewerThan(FhirVersionEnum.DSTU1)) {
			RuntimeResourceDefinition nextDef = myContext.getResourceDefinition(theResource);
			String profile = nextDef.getResourceProfile(myServerBaseUrl);
			if (isNotBlank(profile)) {
				for (T next : theProfiles) {
					if (profile.equals(next.getValue())) {
						return theProfiles;
					}
				}

				List<T> newList = new ArrayList<T>();
				newList.addAll(theProfiles);

				BaseRuntimeElementDefinition<?> idElement = myContext.getElementDefinition("id");
				@SuppressWarnings("unchecked")
				T newId = (T) idElement.newInstance();
				newId.setValue(profile);

				newList.add(newId);
				return newList;
			}
		}

		return theProfiles;
	}

	/**
	 * If set to <code>true</code> (default is <code>false</code>), narratives will not be included in the encoded
	 * values.
	 * 
	 * @deprecated Use {@link #isSuppressNarratives()}
	 */
	@Deprecated
	public boolean getSuppressNarratives() {
		return mySuppressNarratives;
	}

	protected boolean isChildContained(BaseRuntimeElementDefinition<?> childDef, boolean theIncludedResource) {
		return (childDef.getChildType() == ChildTypeEnum.CONTAINED_RESOURCES || childDef.getChildType() == ChildTypeEnum.CONTAINED_RESOURCE_LIST) && getContainedResources().isEmpty() == false && theIncludedResource == false;
	}

	@Override
	public boolean isOmitResourceId() {
		return myOmitResourceId;
	}

	@Override
	public boolean isStripVersionsFromReferences() {
		return myStripVersionsFromReferences;
	}

	@Override
	public boolean isSummaryMode() {
		return mySummaryMode;
	}

	/**
	 * If set to <code>true</code> (default is <code>false</code>), narratives will not be included in the encoded
	 * values.
	 * 
	 * @since 1.2
	 */
	public boolean isSuppressNarratives() {
		return mySuppressNarratives;
	}

	@Override
	public Bundle parseBundle(Reader theReader) {
		if (myContext.getVersion().getVersion() == FhirVersionEnum.DSTU2_HL7ORG) {
			throw new IllegalStateException("Can't parse DSTU1 (Atom) bundle in HL7.org DSTU2 mode. Use parseResource(Bundle.class, foo) instead.");
		}
		return parseBundle(null, theReader);
	}

	@Override
	public Bundle parseBundle(String theXml) throws ConfigurationException, DataFormatException {
		StringReader reader = new StringReader(theXml);
		return parseBundle(reader);
	}

	@Override
	public <T extends IBaseResource> T parseResource(Class<T> theResourceType, Reader theReader) throws DataFormatException {
		
		if (theResourceType != null) {
			RuntimeResourceDefinition def = myContext.getResourceDefinition(theResourceType);
			if (def.getStructureVersion() != myContext.getVersion().getVersion()) {
				throw new IllegalArgumentException("This parser is for FHIR version " + myContext.getVersion().getVersion() + " - Can not parse a structure for version " + def.getStructureVersion());
			}
		}
		
		T retVal = doParseResource(theResourceType, theReader);

		RuntimeResourceDefinition def = myContext.getResourceDefinition(retVal);
		if ("Bundle".equals(def.getName())) {

			BaseRuntimeChildDefinition entryChild = def.getChildByName("entry");
			BaseRuntimeElementCompositeDefinition<?> entryDef = (BaseRuntimeElementCompositeDefinition<?>) entryChild.getChildByName("entry");
			List<IBase> entries = entryChild.getAccessor().getValues(retVal);
			if (entries != null) {
				for (IBase nextEntry : entries) {

					/**
					 * If Bundle.entry.fullUrl is populated, set the resource ID to that
					 */
					// TODO: should emit a warning and maybe notify the error handler if the resource ID doesn't match the
					// fullUrl idPart
					BaseRuntimeChildDefinition fullUrlChild = entryDef.getChildByName("fullUrl");
					if (fullUrlChild == null) {
						continue; // TODO: remove this once the data model in tinder plugin catches up to 1.2
					}
					List<IBase> fullUrl = fullUrlChild.getAccessor().getValues(nextEntry);
					if (fullUrl != null && !fullUrl.isEmpty()) {
						IPrimitiveType<?> value = (IPrimitiveType<?>) fullUrl.get(0);
						if (value.isEmpty() == false) {
							List<IBase> entryResources = entryDef.getChildByName("resource").getAccessor().getValues(nextEntry);
							if (entryResources != null && entryResources.size() > 0) {
								IBaseResource res = (IBaseResource) entryResources.get(0);
								String versionId = res.getIdElement().getVersionIdPart();
								res.setId(value.getValueAsString());
								if (isNotBlank(versionId) && res.getIdElement().hasVersionIdPart() == false) {
									res.setId(res.getIdElement().withVersion(versionId));
								}
							}
						}
					}

				}
			}

		}

		return retVal;
	}

	@SuppressWarnings("cast")
	@Override
	public <T extends IBaseResource> T parseResource(Class<T> theResourceType, String theMessageString) {
		StringReader reader = new StringReader(theMessageString);
		return (T) parseResource(theResourceType, reader);
	}

	@Override
	public IBaseResource parseResource(Reader theReader) throws ConfigurationException, DataFormatException {
		return parseResource(null, theReader);
	}

	@Override
	public IBaseResource parseResource(String theMessageString) throws ConfigurationException, DataFormatException {
		return parseResource(null, theMessageString);
	}

	@Override
	public TagList parseTagList(String theString) {
		return parseTagList(new StringReader(theString));
	}

	protected List<? extends IBase> preProcessValues(BaseRuntimeChildDefinition metaChildUncast, IBaseResource theResource, List<? extends IBase> theValues) {
		if (myContext.getVersion().getVersion().isRi()) {

			/*
			 * If we're encoding the meta tag, we do some massaging of the meta values before
			 * encoding. Buf if there is no meta element at all, we create one since we're possibly going to be
			 * adding things to it
			 */
			if (theValues.isEmpty() && metaChildUncast.getElementName().equals("meta")) {
				BaseRuntimeElementDefinition<?> metaChild = metaChildUncast.getChildByName("meta");
				if (IBaseMetaType.class.isAssignableFrom(metaChild.getImplementingClass())) {
					IBaseMetaType newType = (IBaseMetaType) metaChild.newInstance();
					theValues = Collections.singletonList(newType);
				}
			}

			if (theValues.size() == 1 && theValues.get(0) instanceof IBaseMetaType) {

				IBaseMetaType metaValue = (IBaseMetaType) theValues.get(0);
				try {
					metaValue = (IBaseMetaType) metaValue.getClass().getMethod("copy").invoke(metaValue);
				} catch (Exception e) {
					throw new InternalErrorException("Failed to duplicate meta", e);
				}

				if (isBlank(metaValue.getVersionId())) {
					if (theResource.getIdElement().hasVersionIdPart()) {
						metaValue.setVersionId(theResource.getIdElement().getVersionIdPart());
					}
				}

				filterCodingsWithNoCodeOrSystem(metaValue.getTag());
				filterCodingsWithNoCodeOrSystem(metaValue.getSecurity());

				List<? extends IPrimitiveType<String>> newProfileList = getProfileTagsForEncoding(theResource, metaValue.getProfile());
				List<? extends IPrimitiveType<String>> oldProfileList = metaValue.getProfile();
				if (oldProfileList != newProfileList) {
					oldProfileList.clear();
					for (IPrimitiveType<String> next : newProfileList) {
						if (isNotBlank(next.getValue())) {
							metaValue.addProfile(next.getValue());
						}
					}
				}

				if (shouldAddSubsettedTag()) {
					IBaseCoding coding = metaValue.addTag();
					coding.setCode(Constants.TAG_SUBSETTED_CODE);
					coding.setSystem(Constants.TAG_SUBSETTED_SYSTEM);
					coding.setDisplay(subsetDescription());
				}

				return Collections.singletonList(metaValue);
			}
		}

		@SuppressWarnings("unchecked")
		List<IBase> retVal = (List<IBase>) theValues;
		
		for (int i = 0; i < retVal.size(); i++) {
			IBase next = retVal.get(i);
			
			/*
			 * If we have automatically contained any resources via
			 * their references, this ensures that we output the new
			 * local reference
			 */
			if (next instanceof IBaseReference) {
				IBaseReference nextRef = (IBaseReference)next;
				String refText = determineReferenceText(nextRef);
				if (!StringUtils.equals(refText, nextRef.getReferenceElement().getValue())) {
					
					if (retVal == theValues) {
						retVal = new ArrayList<IBase>(theValues);
					}
					IBaseReference newRef = (IBaseReference) myContext.getElementDefinition(nextRef.getClass()).newInstance();
					myContext.newTerser().cloneInto(nextRef, newRef, true);
					newRef.setReference(refText);
					retVal.set(i, newRef);
					
				}
			}
		}
		
		return retVal;
	}

	@Override
	public void setDontEncodeElements(Set<String> theDontEncodeElements) {
		myDontEncodeElementsIncludesStars = false;
		if (theDontEncodeElements == null || theDontEncodeElements.isEmpty()) {
			myDontEncodeElements = null;
		} else {
			myDontEncodeElements = theDontEncodeElements;
			for (String next : theDontEncodeElements) {
				if (next.startsWith("*.")) {
					myDontEncodeElementsIncludesStars = true;
				}
			}
		}
	}

	@Override
	public void setEncodeElements(Set<String> theEncodeElements) {
		myEncodeElementsIncludesStars = false;
		if (theEncodeElements == null || theEncodeElements.isEmpty()) {
			myEncodeElements = null;
		} else {
			myEncodeElements = theEncodeElements;
			for (String next : theEncodeElements) {
				if (next.startsWith("*.")) {
					myEncodeElementsIncludesStars = true;
				}
			}
		}
	}

	@Override
	public void setEncodeElementsAppliesToResourceTypes(Set<String> theEncodeElementsAppliesToResourceTypes) {
		if (theEncodeElementsAppliesToResourceTypes == null || theEncodeElementsAppliesToResourceTypes.isEmpty()) {
			myEncodeElementsAppliesToResourceTypes = null;
		} else {
			myEncodeElementsAppliesToResourceTypes = theEncodeElementsAppliesToResourceTypes;
		}
	}

	@Override
	public BaseParser setEncodeForceResourceId(IIdType theEncodeForceResourceId) {
		myEncodeForceResourceId = theEncodeForceResourceId;
		return this;
	}

	@Override
	public IParser setOmitResourceId(boolean theOmitResourceId) {
		myOmitResourceId = theOmitResourceId;
		return this;
	}

	@Override
	public IParser setParserErrorHandler(IParserErrorHandler theErrorHandler) {
		Validate.notNull(theErrorHandler, "theErrorHandler must not be null");
		myErrorHandler = theErrorHandler;
		return this;
	}

	@Override
	public void setPreferTypes(List<Class<? extends IBaseResource>> thePreferTypes) {
		myPreferTypes = thePreferTypes;
	}

	@Override
	public IParser setServerBaseUrl(String theUrl) {
		myServerBaseUrl = isNotBlank(theUrl) ? theUrl : null;
		return this;
	}

	@Override
	public IParser setStripVersionsFromReferences(boolean theStripVersionsFromReferences) {
		myStripVersionsFromReferences = theStripVersionsFromReferences;
		return this;
	}

	@Override
	public IParser setSummaryMode(boolean theSummaryMode) {
		mySummaryMode = theSummaryMode;
		return this;
	}

	@Override
	public IParser setSuppressNarratives(boolean theSuppressNarratives) {
		mySuppressNarratives = theSuppressNarratives;
		return this;
	}

	protected boolean shouldAddSubsettedTag() {
		return isSummaryMode() || isSuppressNarratives();
	}

	protected boolean shouldEncodeResourceId(IBaseResource theResource) {
		boolean retVal = true;
		if (isOmitResourceId()) {
			retVal = false;
		} else {
			if (myDontEncodeElements != null) {
				String resourceName = myContext.getResourceDefinition(theResource).getName();
				if (myDontEncodeElements.contains(resourceName + ".id")) {
					retVal = false;
				} else if (myDontEncodeElements.contains("*.id")) {
					retVal = false;
				}
			}
		}
		return retVal;
	}

	/**
	 * Used for DSTU2 only
	 */
	protected boolean shouldEncodeResourceMeta(IResource theResource) {
		if (myDontEncodeElements != null) {
			String resourceName = myContext.getResourceDefinition(theResource).getName();
			if (myDontEncodeElements.contains(resourceName + ".meta")) {
				return false;
			} else if (myDontEncodeElements.contains("*.meta")) {
				return false;
			}
		}
		return true;
	}

	private String subsetDescription() {
		return "Resource encoded in summary mode";
	}

	protected void throwExceptionForUnknownChildType(BaseRuntimeChildDefinition nextChild, Class<? extends IBase> theType) {
		if (nextChild instanceof BaseRuntimeDeclaredChildDefinition) {
			StringBuilder b = new StringBuilder();
			b.append(((BaseRuntimeDeclaredChildDefinition) nextChild).getElementName());
			b.append(" has type ");
			b.append(theType.getName());
			b.append(" but this is not a valid type for this element");
			if (nextChild instanceof RuntimeChildChoiceDefinition) {
				RuntimeChildChoiceDefinition choice = (RuntimeChildChoiceDefinition) nextChild;
				b.append(" - Expected one of: " + choice.getValidChildTypes());
			}
			throw new DataFormatException(b.toString());
		}
		throw new DataFormatException(nextChild + " has no child of type " + theType);
	}

	protected static <T> List<T> extractMetadataListNotNull(IResource resource, ResourceMetadataKeyEnum<List<T>> key) {
		List<? extends T> securityLabels = key.get(resource);
		if (securityLabels == null) {
			securityLabels = Collections.emptyList();
		}
		return new ArrayList<T>(securityLabels);
	}

	static boolean hasExtensions(IBase theElement) {
		if (theElement instanceof ISupportsUndeclaredExtensions) {
			ISupportsUndeclaredExtensions res = (ISupportsUndeclaredExtensions) theElement;
			if (res.getUndeclaredExtensions().size() > 0 || res.getUndeclaredModifierExtensions().size() > 0) {
				return true;
			}
		}
		if (theElement instanceof IBaseHasExtensions) {
			IBaseHasExtensions res = (IBaseHasExtensions) theElement;
			if (res.hasExtension()) {
				return true;
			}
		}
		if (theElement instanceof IBaseHasModifierExtensions) {
			IBaseHasModifierExtensions res = (IBaseHasModifierExtensions) theElement;
			if (res.hasModifierExtension()) {
				return true;
			}
		}
		return false;
	}

	protected class CompositeChildElement {
		private final BaseRuntimeChildDefinition myDef;
		private final CompositeChildElement myParent;
		private final RuntimeResourceDefinition myResDef;

		public CompositeChildElement(CompositeChildElement theParent, BaseRuntimeChildDefinition theDef) {
			myDef = theDef;
			myParent = theParent;
			myResDef = null;

			if (ourLog.isTraceEnabled()) {
				if (theParent != null) {
					StringBuilder path = theParent.buildPath();
					if (path != null) {
						path.append('.');
						path.append(myDef.getElementName());
						ourLog.trace(" * Next path: {}", path.toString());
					}
				}
			}

		}

		public CompositeChildElement(RuntimeResourceDefinition theResDef) {
			myResDef = theResDef;
			myDef = null;
			myParent = null;
		}

		private StringBuilder buildPath() {
			if (myResDef != null) {
				StringBuilder b = new StringBuilder();
				b.append(myResDef.getName());
				return b;
			} else {
				StringBuilder b = myParent.buildPath();
				if (b != null && myDef != null) {
					b.append('.');
					b.append(myDef.getElementName());
				}
				return b;
			}
		}

		private boolean checkIfParentShouldBeEncodedAndBuildPath(StringBuilder thePathBuilder, boolean theStarPass) {
			return checkIfPathMatches(thePathBuilder, theStarPass, myEncodeElementsAppliesToResourceTypes, myEncodeElements, true);
		}

		private boolean checkIfParentShouldNotBeEncodedAndBuildPath(StringBuilder thePathBuilder, boolean theStarPass) {
			return checkIfPathMatches(thePathBuilder, theStarPass, null, myDontEncodeElements, false);
		}

		private boolean checkIfPathMatches(StringBuilder thePathBuilder, boolean theStarPass, Set<String> theResourceTypes, Set<String> theElements, boolean theCheckingForWhitelist) {
			if (myResDef != null) {
				if (theResourceTypes != null) {
					if (!theResourceTypes.contains(myResDef.getName())) {
						return true;
					}
				}
				if (theStarPass) {
					thePathBuilder.append('*');
				} else {
					thePathBuilder.append(myResDef.getName());
				}
				if (theElements.contains(thePathBuilder.toString())) {
					return true;
				} else {
					return false;
				}
			} else if (myParent != null) {
				boolean parentCheck;
				if (theCheckingForWhitelist) {
					parentCheck = myParent.checkIfParentShouldBeEncodedAndBuildPath(thePathBuilder, theStarPass);
				} else {
					parentCheck = myParent.checkIfParentShouldNotBeEncodedAndBuildPath(thePathBuilder, theStarPass);
				}
				if (parentCheck) {
					return true;
				}

				if (myDef != null) {
					thePathBuilder.append('.');
					thePathBuilder.append(myDef.getElementName());
					return theElements.contains(thePathBuilder.toString());
				}
			}

			return true;
		}

		public BaseRuntimeChildDefinition getDef() {
			return myDef;
		}

		public CompositeChildElement getParent() {
			return myParent;
		}

		public RuntimeResourceDefinition getResDef() {
			return myResDef;
		}

		public boolean shouldBeEncoded() {
			boolean retVal = true;
			if (myEncodeElements != null) {
				retVal = checkIfParentShouldBeEncodedAndBuildPath(new StringBuilder(), false);
				if (retVal == false && myEncodeElementsIncludesStars) {
					retVal = checkIfParentShouldBeEncodedAndBuildPath(new StringBuilder(), true);
				}
			}
			if (retVal && myDontEncodeElements != null) {
				retVal = !checkIfParentShouldNotBeEncodedAndBuildPath(new StringBuilder(), false);
				if (retVal && myDontEncodeElementsIncludesStars) {
					retVal = !checkIfParentShouldNotBeEncodedAndBuildPath(new StringBuilder(), true);
				}
			}
			return retVal;
		}
	}

	static class ContainedResources {
		private long myNextContainedId = 1;

		private List<IBaseResource> myResources = new ArrayList<IBaseResource>();
		private IdentityHashMap<IBaseResource, IIdType> myResourceToId = new IdentityHashMap<IBaseResource, IIdType>();

		public void addContained(IBaseResource theResource) {
			if (myResourceToId.containsKey(theResource)) {
				return;
			}

			IIdType newId;
			if (theResource.getIdElement().isLocal()) {
				newId = theResource.getIdElement();
			} else {
				// TODO: make this configurable between the two below (and something else?)
				// newId = new IdDt(UUID.randomUUID().toString());
				newId = new IdDt(myNextContainedId++);
			}

			myResourceToId.put(theResource, newId);
			myResources.add(theResource);
		}

		public void addContained(IIdType theId, IBaseResource theResource) {
			myResourceToId.put(theResource, theId);
			myResources.add(theResource);
		}

		public List<IBaseResource> getContainedResources() {
			return myResources;
		}

		public IIdType getResourceId(IBaseResource theNext) {
			return myResourceToId.get(theNext);
		}

		public boolean isEmpty() {
			return myResourceToId.isEmpty();
		}

	}

}
