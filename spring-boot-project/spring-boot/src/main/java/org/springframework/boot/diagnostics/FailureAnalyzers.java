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

package org.springframework.boot.diagnostics;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.ExtensionResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootExceptionReporter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.util.Assert;

/**
 * Utility to trigger {@link FailureAnalyzer} and {@link FailureAnalysisReporter}
 * instances loaded from {@code spring.factories}.
 * <p>
 * A {@code FailureAnalyzer} that requires access to the {@link BeanFactory} in order to
 * perform its analysis can implement {@code BeanFactoryAware} to have the
 * {@code BeanFactory} injected prior to {@link FailureAnalyzer#analyze(Throwable)} being
 * called.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Stephane Nicoll
 */
final class FailureAnalyzers implements SpringBootExceptionReporter {

	private static final Log logger = LogFactory.getLog(FailureAnalyzers.class);

	private final ClassLoader classLoader;

	private final ExtensionResolver extensionLoader;

	private final List<FailureAnalyzer> analyzers;

	FailureAnalyzers(SpringApplication application,
			ConfigurableApplicationContext context) {
		this(application, context, null);
	}

	FailureAnalyzers(SpringApplication application,
			ConfigurableApplicationContext context, ClassLoader classLoader) {
		Assert.notNull(context, "Context must not be null");
		this.classLoader = (classLoader != null) ? classLoader : context.getClassLoader();
		this.extensionLoader = application.getExtensionResolver();
		this.analyzers = loadFailureAnalyzers(application.getExtensionResolver(),
				this.classLoader);
		prepareFailureAnalyzers(this.analyzers, context);
	}

	private List<FailureAnalyzer> loadFailureAnalyzers(
			ExtensionResolver extensionResolver, ClassLoader classLoader) {
		return extensionResolver.resolveExtensions(FailureAnalyzer.class, classLoader,
				(analyzerName, ex) -> logger.trace("Failed to load " + analyzerName, ex));
	}

	private void prepareFailureAnalyzers(List<FailureAnalyzer> analyzers,
			ConfigurableApplicationContext context) {
		for (FailureAnalyzer analyzer : analyzers) {
			prepareAnalyzer(context, analyzer);
		}
	}

	private void prepareAnalyzer(ConfigurableApplicationContext context,
			FailureAnalyzer analyzer) {
		if (analyzer instanceof BeanFactoryAware) {
			((BeanFactoryAware) analyzer).setBeanFactory(context.getBeanFactory());
		}
		if (analyzer instanceof EnvironmentAware) {
			((EnvironmentAware) analyzer).setEnvironment(context.getEnvironment());
		}
	}

	@Override
	public boolean reportException(Throwable failure) {
		FailureAnalysis analysis = analyze(failure, this.analyzers);
		return report(analysis, this.classLoader);
	}

	private FailureAnalysis analyze(Throwable failure, List<FailureAnalyzer> analyzers) {
		for (FailureAnalyzer analyzer : analyzers) {
			try {
				FailureAnalysis analysis = analyzer.analyze(failure);
				if (analysis != null) {
					return analysis;
				}
			}
			catch (Throwable ex) {
				logger.debug("FailureAnalyzer " + analyzer + " failed", ex);
			}
		}
		return null;
	}

	private boolean report(FailureAnalysis analysis, ClassLoader classLoader) {
		List<FailureAnalysisReporter> reporters = this.extensionLoader
				.resolveExtensions(FailureAnalysisReporter.class, classLoader);
		if (analysis == null || reporters.isEmpty()) {
			return false;
		}
		for (FailureAnalysisReporter reporter : reporters) {
			reporter.report(analysis);
		}
		return true;
	}

}
