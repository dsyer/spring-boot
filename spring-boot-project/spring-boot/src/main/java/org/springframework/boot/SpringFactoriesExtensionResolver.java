/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@link ExtensionResolver} that uses {@link SpringFactoriesLoader} to resolve extensions
 * and extension names. Resolved extensions are {@link AnnotationAwareOrderComparator#sort
 * sorted}.
 *
 * @author Andy Wilkinson
 * @since 2.2
 */
public class SpringFactoriesExtensionResolver implements ExtensionResolver {

	@Override
	public <T> List<T> resolveExtensions(Class<T> extensionClass, ClassLoader classLoader,
			BiConsumer<String, RuntimeException> errorHandler) {
		Set<String> extensionNames = resolveExtensionNames(extensionClass, classLoader);
		List<T> extensions = new ArrayList<>(extensionNames.size());
		for (String extensionName : extensionNames) {
			try {
				extensions.add(
						instantiateExtension(extensionClass, extensionName, classLoader));
			}
			catch (RuntimeException ex) {
				errorHandler.accept(extensionName, ex);
			}
		}
		AnnotationAwareOrderComparator.sort(extensions);
		return extensions;
	}

	private Set<String> resolveExtensionNames(Class<?> extensionClass,
			ClassLoader classLoader) {
		return new LinkedHashSet<>(
				SpringFactoriesLoader.loadFactoryNames(extensionClass, classLoader));
	}

	@SuppressWarnings("unchecked")
	private <T> T instantiateExtension(Class<T> extensionClass, String extensionClassName,
			ClassLoader classLoader) {
		try {
			Class<?> instanceClass = ClassUtils.forName(extensionClassName, classLoader);
			if (!extensionClass.isAssignableFrom(instanceClass)) {
				throw new IllegalArgumentException("Class '" + extensionClassName
						+ "' is not assignable to '" + extensionClass.getName() + "'");
			}
			return (T) BeanUtils.instantiateClass(
					ReflectionUtils.accessibleConstructor(instanceClass));
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException("Unable to instantiate factory class '"
					+ extensionClass.getName() + "'", ex);
		}
	}

}
