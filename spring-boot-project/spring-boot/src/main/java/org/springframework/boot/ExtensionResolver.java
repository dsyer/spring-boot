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

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Strategy interface for resolving extensions.
 *
 * @author Andy Wilkinson
 * @since 2.2
 */
public interface ExtensionResolver {

	public static final BiConsumer<String, RuntimeException> defaultErrorHandler = (name,
			ex) -> {
		throw ex;
	};

	/**
	 * Resolves the extensions of the given {@code extensionType} located using the given
	 * {@code classLoader}. If the extensions need to be instantiated, their default
	 * constructor will be used.
	 * @param extensionType type of the extensions
	 * @param classLoader class loader to use to locate and, if necessary, load the
	 * extensions
	 * @param <T> type of the extensions
	 * @return the extensions
	 * @throws IllegalArgumentException if a problem occurs when loading any of the
	 * extensions
	 */
	default <T> List<T> resolveExtensions(Class<T> extensionType,
			ClassLoader classLoader) {
		return resolveExtensions(extensionType, classLoader, defaultErrorHandler);
	}

	/**
	 * Resolves the extensions of the given {@code extensionType} located using the given
	 * {@code classLoader}. If the extensions need to be instantiated, their default
	 * constructor will be used. In the event of an error when resolving an extension, the
	 * given {@code errorHandler} will be called with the name of the extension and the
	 * error that occurred.
	 * @param extensionType type of the extensions
	 * @param classLoader class loader to use to locate and, if necessary, load the
	 * extensions
	 * @param errorHandler the error handler
	 * @param <T> type of the extensions
	 * @return the extensions
	 * @throws IllegalArgumentException if a problem occurs when loading any of the
	 * extensions
	 */
	<T> List<T> resolveExtensions(Class<T> extensionType, ClassLoader classLoader,
			BiConsumer<String, RuntimeException> errorHandler);

	/**
	 * Resolves the extensions of the given {@code extensionType} using the given
	 * {@code classLoader}. If the extensions need to be instantiated, the constructor
	 * that accepts the given {@code parameterTypes} will be used.
	 * @param extensionType type of the extensions
	 * @param classLoader class loader to use to locate and, if necessary, load the
	 * extensions
	 * @param parameterTypes the constructor parameter types
	 * @param args the constructor arguments
	 * @param <T> type of the extensions
	 * @return the extensions
	 * @throws IllegalArgumentException if a problem occurs when loading any of the
	 * extensions
	 */
	<T> List<T> resolveExtensions(Class<T> extensionType, ClassLoader classLoader,
			Class<?>[] parameterTypes, Object[] args);

}
