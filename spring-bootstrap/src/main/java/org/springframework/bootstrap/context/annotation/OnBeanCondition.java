/*
 * Copyright 2012-2013 the original author or authors.
 *
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
 */

package org.springframework.bootstrap.context.annotation;

import java.util.Collection;
import java.util.HashSet;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

/**
 * {@link Condition} that checks that specific beans are present.
 * 
 * @author Phillip Webb
 * @see ConditionalOnBean
 */
class OnBeanCondition extends AbstractOnBeanCondition {

	@Override
	protected Class<?> annotationClass() {
		return ConditionalOnBean.class;
	}

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		boolean result = super.matches(context, metadata);
		if (metadata instanceof AnnotationMetadata) {
			AnnotationMetadata typeMetaData = (AnnotationMetadata) metadata;
			String key = typeMetaData.getClassName();
			Collection<String> deferred = getBeansBeingDeferred(context.getBeanFactory());
			if (deferred.contains(key)) {
				return result;
			}
			deferred.add(key);
			// defer decision on this bean until the next time we are asked
			return true;
		}
		return result;
	}

	private Collection<String> getBeansBeingDeferred(
			ConfigurableListableBeanFactory beanFactory) {
		String name = OnBeanCondition.class.getName();
		if (!beanFactory.containsSingleton(name)) {
			beanFactory.registerSingleton(name, new HashSet<String>());
		}
		@SuppressWarnings("unchecked")
		Collection<String> result = (Collection<String>) beanFactory.getSingleton(name);
		return result;
	}

}
