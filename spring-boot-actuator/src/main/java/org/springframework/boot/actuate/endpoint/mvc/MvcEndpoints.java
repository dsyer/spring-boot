/*
 * Copyright 2012-2014 the original author or authors.
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

package org.springframework.boot.actuate.endpoint.mvc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * A registry for all {@link MvcEndpoint} beans, and a factory for a set of generic ones
 * wrapping existing {@link Endpoint} instances that are not already exposed as MVC
 * endpoints.
 *
 * @author Dave Syer
 */
@Component
public class MvcEndpoints implements ApplicationContextAware, InitializingBean {

	private ApplicationContext applicationContext;

	private final Set<MvcEndpoint> endpoints = new HashSet<MvcEndpoint>();

	private final Set<MvcEndpoint> fallbacks = new HashSet<MvcEndpoint>();

	private Set<Class<?>> customTypes;

	private String contextPath;

	public MvcEndpoints(String contextPath) {
		this.contextPath = contextPath;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Collection<MvcEndpoint> existing = BeanFactoryUtils
				.beansOfTypeIncludingAncestors(this.applicationContext, MvcEndpoint.class)
				.values();
		for (MvcEndpoint endpoint : existing) {
			if (isFallback(endpoint)) {
				this.fallbacks.add(endpoint);
			}
			else {
				this.endpoints.add(endpoint);
			}
		}
		this.customTypes = findEndpointClasses(existing);
		@SuppressWarnings("rawtypes")
		Collection<Endpoint> delegates = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				this.applicationContext, Endpoint.class).values();
		for (Endpoint<?> endpoint : delegates) {
			if (isGenericEndpoint(endpoint.getClass()) && endpoint.isEnabled()) {
				this.endpoints.add(new EndpointMvcAdapter(endpoint));
			}
		}
	}

	public boolean isFallback(MvcEndpoint endpoint) {
		return AnnotationUtils
				.findAnnotation(endpoint.getClass(), FallbackEndpoint.class) != null
				&& isHomePage(endpoint);
	}

	private boolean isHomePage(MvcEndpoint endpoint) {
		String contextPath = this.contextPath;
		if (contextPath == null) {
			contextPath = "";
		}
		String path = contextPath + endpoint.getPath();
		return "".equals(path) || "/".equals(path);
	}

	private Set<Class<?>> findEndpointClasses(Collection<MvcEndpoint> existing) {
		Set<Class<?>> types = new HashSet<Class<?>>();
		for (MvcEndpoint endpoint : existing) {
			Class<?> type = endpoint.getEndpointType();
			if (type != null) {
				types.add(type);
			}
		}
		return types;
	}

	public Set<? extends MvcEndpoint> getEndpoints() {
		return this.endpoints;
	}

	public Set<? extends MvcEndpoint> getFallbackEndpoints() {
		return this.fallbacks;
	}

	private boolean isGenericEndpoint(Class<?> type) {
		return !this.customTypes.contains(type)
				&& !MvcEndpoint.class.isAssignableFrom(type);
	}

}
