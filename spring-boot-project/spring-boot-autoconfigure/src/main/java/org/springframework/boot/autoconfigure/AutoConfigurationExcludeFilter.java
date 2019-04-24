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

package org.springframework.boot.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.ExtensionResolver;
import org.springframework.boot.SpringFactoriesExtensionResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/**
 * A {@link TypeFilter} implementation that matches registered auto-configuration classes.
 *
 * @author Stephane Nicoll
 * @since 1.5.0
 */
public class AutoConfigurationExcludeFilter
		implements TypeFilter, BeanClassLoaderAware, BeanFactoryAware {

	private ClassLoader beanClassLoader;

	private ExtensionResolver extensionResolver;

	private volatile List<String> autoConfigurations;

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		try {
			this.extensionResolver = beanFactory.getBean(ExtensionResolver.class);
		}
		catch (NoSuchBeanDefinitionException ex) {
			this.extensionResolver = new SpringFactoriesExtensionResolver();
		}
	}

	@Override
	public boolean match(MetadataReader metadataReader,
			MetadataReaderFactory metadataReaderFactory) throws IOException {
		return isConfiguration(metadataReader) && isAutoConfiguration(metadataReader);
	}

	private boolean isConfiguration(MetadataReader metadataReader) {
		return metadataReader.getAnnotationMetadata()
				.isAnnotated(Configuration.class.getName());
	}

	private boolean isAutoConfiguration(MetadataReader metadataReader) {
		return getAutoConfigurations()
				.contains(metadataReader.getClassMetadata().getClassName());
	}

	protected List<String> getAutoConfigurations() {
		if (this.autoConfigurations == null) {
			this.autoConfigurations = new ArrayList<>(
					new CompositeAutoConfigurationResolver(this.extensionResolver
							.resolveExtensions(AutoConfigurationResolver.class,
									this.beanClassLoader)).resolve(
											EnableAutoConfiguration.class,
											this.beanClassLoader));
		}
		return this.autoConfigurations;
	}

}
