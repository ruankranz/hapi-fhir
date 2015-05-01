package ca.uhn.fhir.context;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2015 University Health Network
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

import java.lang.reflect.Field;

import org.hl7.fhir.instance.model.IBase;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;

public class RuntimeChildPrimitiveEnumerationDatatypeDefinition extends RuntimeChildPrimitiveDatatypeDefinition {

	private Object myBinder;
	private Class<?> myEnumerationType;

	public RuntimeChildPrimitiveEnumerationDatatypeDefinition(Field theField, String theElementName, Child theChildAnnotation, Description theDescriptionAnnotation,  Class<? extends IBase> theDatatype, Class<?> theBinderType) {
		super(theField, theElementName, theDescriptionAnnotation, theChildAnnotation, theDatatype);

		myEnumerationType = theBinderType;
	}

	@Override
	public Object getInstanceConstructorArguments() {
		Object retVal = myBinder;
		if (retVal == null) {
			Class<?> clazz;
			String className = myEnumerationType.getName() + "EnumFactory";
			try {
				clazz = Class.forName(className);
				retVal = clazz.newInstance();
				myBinder = retVal;
			} catch (Exception e) {
				throw new ConfigurationException("Failed to instantiate " + className, e);
			}
		}
		return retVal;
	}

}