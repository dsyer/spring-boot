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

package org.springframework.boot.context.embedded;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Dave Syer
 */
public class BeanInitializationCycleTests {

	private static Log logger = LogFactory.getLog(BeanInitializationCycleTests.class);

	private AnnotationConfigEmbeddedWebApplicationContext context;

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Before
	public void init() {
		Application.counter.set(0);
	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void testFilterCycle() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Application.class, BaseFilter.class);
		this.context.setLazy(false);
		this.expected.expect(ApplicationContextException.class);
		this.expected.expectMessage("unresolvable circular reference");
		this.context.refresh();
	}

	@Test
	public void testFilterCycleAvoided() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Application.class, ScopedFilter.class);
		this.context.refresh();
	}

	@Test
	public void testFilterRegistrationCycleAvoided() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Application.class, FilterRegistration.class, ScopedFilter.class);
		this.context.refresh();
		this.context.getBean(ScopedFilter.class).getFilterConfig();
	}

	@Test
	public void testServletCycle() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Application.class, BaseServlet.class);
		this.context.setLazy(false);
		this.expected.expect(ApplicationContextException.class);
		this.expected.expectMessage("unresolvable circular reference");
		this.context.refresh();
	}

	@Test
	public void testServletCycleAvoided() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Application.class, ScopedServlet.class);
		this.context.refresh();
	}

	@Test
	public void testServletRegistrationCycleAvoided() {
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context
				.register(Application.class, ServletRegistration.class, ScopedServlet.class);
		this.context.refresh();
	}

	@Test
	public void testSimpleLazyScopedProxy() {
		Bar.initialized = false;
		this.context = new AnnotationConfigEmbeddedWebApplicationContext();
		this.context.register(Foo.class, Bar.class);
		this.context.refresh();
		assertFalse(Bar.initialized);
		logger.info("Finished: " + this.context.getBean(Bar.class));
		assertTrue(Bar.initialized);
	}

	@Configuration
	@Import(Container.class)
	protected static class Foo {
		@Autowired
		protected void init(Bar bar) {
			logger.info("Initializing Foo");
			Assert.state(bar != null);
		}
	}

	@Configuration
	@Lazy
	@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
	protected static class Bar {
		public static boolean initialized = false;

		@PostConstruct
		public void init() {
			logger.info("Initializing Bar");
			initialized = true;
		}
	}

	@Configuration
	@Import(Container.class)
	protected static class Application {

		public static AtomicInteger counter = new AtomicInteger();

		@Autowired
		protected void init(Context context) {
			logger.info("Initializing Application: "
					+ Application.counter.incrementAndGet());
			Assert.state(context != null);
		}

		@Bean
		public Context context() {
			return new Context();
		}

	}

	@Configuration
	protected static class Container {
		@Bean
		public EmbeddedServletContainerFactory embeddedServletContainerFactory() {
			return new MockEmbeddedServletContainerFactory();
		}
	}

	@Configuration
	protected static class FilterRegistration {
		@Bean
		public FilterRegistrationBean registration(
				@Qualifier("filter") javax.servlet.Filter filter) {
			return new FilterRegistrationBean(filter);
		}
	}

	@Configuration("filter")
	@Lazy
	@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
	protected static class ScopedFilter extends BaseFilter {
	}

	@Configuration
	protected static class BaseFilter extends OncePerRequestFilter {

		public BaseFilter() {
			this.logger.info("Creating Filter");
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request,
				HttpServletResponse response, FilterChain chain) throws ServletException,
				IOException {
		}

		@Autowired
		protected void start(Context context) {
			this.logger.info("Initializing Filter: "
					+ Application.counter.incrementAndGet());
			Assert.state(context != null);
		}

	}

	@Configuration
	protected static class ServletRegistration {
		@Bean
		public ServletRegistrationBean registration(
				@Qualifier("servlet") javax.servlet.Servlet servlet) {
			return new ServletRegistrationBean(servlet);
		}
	}

	@Configuration("servlet")
	@SuppressWarnings("serial")
	@Lazy
	@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
	protected static class ScopedServlet extends BaseServlet {
	}

	@Configuration
	protected static class BaseServlet extends GenericServlet {

		@Override
		public void service(ServletRequest req, ServletResponse res)
				throws ServletException, IOException {
		}

		@Autowired
		protected void start(Context context) {
			logger.info("Initializing Servlet: " + Application.counter.incrementAndGet());
			Assert.state(context != null);
		}

	}

	@Component
	protected static class Context {

		public Context() {
			logger.info("Creating Context: " + Application.counter.incrementAndGet());
		}

	}

}
