package ca.uhn.fhir.rest.param;

/*
 * #%L
 * HAPI FHIR Library
 * %%
 * Copyright (C) 2014 University Health Network
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.PathSpecification;
import ca.uhn.fhir.model.dstu.valueset.SearchParamTypeEnum;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.method.QualifiedParamList;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

public class IncludeParameter extends BaseQueryParameter {

	private Class<? extends Collection<PathSpecification>> myInstantiableCollectionType;
	private HashSet<String> myAllow;
	private Class<?> mySpecType;

	public IncludeParameter(IncludeParam theAnnotation, Class<? extends Collection<PathSpecification>> theInstantiableCollectionType, Class<?> theSpecType) {
		myInstantiableCollectionType = theInstantiableCollectionType;
		if (theAnnotation.allow().length > 0) {
			myAllow = new HashSet<String>();
			for (String next : theAnnotation.allow()) {
				myAllow.add(next);
			}
		}

		mySpecType = theSpecType;
		if (mySpecType != PathSpecification.class && mySpecType != String.class) {
			throw new ConfigurationException("Invalid @" + IncludeParam.class.getSimpleName() + " parameter type: " + mySpecType);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public List<QualifiedParamList> encode(FhirContext theContext, Object theObject) throws InternalErrorException {
		ArrayList<QualifiedParamList> retVal = new ArrayList<QualifiedParamList>();

		if (myInstantiableCollectionType == null) {
			if (mySpecType == PathSpecification.class) {
				retVal.add(QualifiedParamList.singleton(((PathSpecification) theObject).getValue()));
			} else {
				retVal.add(QualifiedParamList.singleton(((String) theObject)));
			}
		} else {
			Collection<PathSpecification> val = (Collection<PathSpecification>) theObject;
			for (PathSpecification pathSpec : val) {
				retVal.add(QualifiedParamList.singleton(pathSpec.getValue()));
			}
		}

		return retVal;
	}

	@Override
	public String getName() {
		return "_include";
	}

	@Override
	public Object parse(List<QualifiedParamList> theString) throws InternalErrorException, InvalidRequestException {
		Collection<PathSpecification> retValCollection = null;
		if (myInstantiableCollectionType != null) {
			try {
				retValCollection = myInstantiableCollectionType.newInstance();
			} catch (Exception e) {
				throw new InternalErrorException("Failed to instantiate " + myInstantiableCollectionType.getName(), e);
			}
		}

		for (List<String> nextParamList : theString) {
			if (nextParamList.isEmpty()) {
				continue;
			}
			if (nextParamList.size() > 1) {
				throw new InvalidRequestException("'OR' query parameters (values containing ',') are not supported in _include parameters");
			}

			String value = nextParamList.get(0);
			if (myAllow != null) {
				if (!myAllow.contains(value)) {
					throw new InvalidRequestException("Invalid _include parameter value: '" + value + "'. Valid values are: " + new TreeSet<String>(myAllow));
				}
			}
			if (retValCollection == null) {
				if (mySpecType == String.class) {
					return value;
				} else {
					return new PathSpecification(value);
				}
			} else {
				retValCollection.add(new PathSpecification(value));
			}
		}

		return retValCollection;
	}

	@Override
	public boolean isRequired() {
		return false;
	}

	@Override
	public SearchParamTypeEnum getParamType() {
		return null;
	}

	@Override
	public boolean handlesMissing() {
		return true;
	}

}
