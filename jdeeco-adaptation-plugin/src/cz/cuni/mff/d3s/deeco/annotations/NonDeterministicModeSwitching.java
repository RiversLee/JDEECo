/*******************************************************************************
 * Copyright 2016 Charles University in Prague
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *******************************************************************************/
package cz.cuni.mff.d3s.deeco.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cz.cuni.mff.d3s.metaadaptation.search.EmptyParameters;
import cz.cuni.mff.d3s.metaadaptation.search.SearchParameters;
import cz.cuni.mff.d3s.metaadaptation.search.StateSpaceSearch;

/**
 * Used to mark the component to be adapted by non-deterministic mode switching.
 * 
 * @author Dominik Skoda <skoda@d3s.mff.cuni.cz>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NonDeterministicModeSwitching {
	Class<? extends StateSpaceSearch> searchEngine();
	Class<? extends SearchParameters> searchParameters() default EmptyParameters.class;
}
