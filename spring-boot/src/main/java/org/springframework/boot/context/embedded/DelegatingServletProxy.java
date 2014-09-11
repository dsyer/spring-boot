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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.DelegatingFilterProxy;

/**
 * Proxy for a standard Servlet, delegating to a Spring-managed bean that implements the
 * Servlet interface. Supports a "targetBeanName" servlet init-param in {@code web.xml},
 * specifying the name of the target bean in the Spring application context.
 * 
 * <p>
 * {@code web.xml} will usually contain a {@code DelegatingServletProxy} definition, with
 * the specified {@code servlet-name} corresponding to a bean name in Spring's root
 * application context. All calls to the filter proxy will then be delegated to that bean
 * in the Spring context, which is required to implement the standard Servlet interface.
 * 
 * <p>
 * This approach is particularly useful for Servlet implementation with complex setup
 * needs, allowing to apply the full Spring bean definition machinery to Servlet
 * instances. Alternatively, consider standard Servlet setup in combination with looking
 * up service beans from the Spring root application context.
 * 
 * <p>
 * <b>NOTE:</b> The lifecycle methods defined by the Servlet interface will by default
 * <i>not</i> be delegated to the target bean, relying on the Spring application context
 * to manage the lifecycle of that bean. Specifying the "targetFilterLifecycle" filter
 * init-param as "true" will enforce invocation of the {@code Servlet.init} and
 * {@code Servlet.destroy} lifecycle methods on the target bean, letting the servlet
 * container manage the servlet lifecycle.
 * 
 * <p>
 * Code borrowed heavily from {@link DelegatingFilterProxy} and the HttpServletBean in
 * Spring MVC.
 * 
 * @author Dave Syer
 */
public class DelegatingServletProxy extends HttpServlet implements EnvironmentAware,
		EnvironmentCapable {

	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Set of required properties (Strings) that must be supplied as config parameters to
	 * this servlet.
	 */
	private final Set<String> requiredProperties = new HashSet<String>();

	private ConfigurableEnvironment environment;

	private String contextAttribute;

	private WebApplicationContext webApplicationContext;

	private String targetBeanName;

	private boolean targetServletLifecycle = false;

	private volatile Servlet delegate;

	private final Object delegateMonitor = new Object();

	/**
	 * Create a new {@code DelegatingServletProxy}. For traditional (pre-Servlet 3.0) use
	 * in {@code web.xml}.
	 * @see #setTargetBeanName(String)
	 */
	public DelegatingServletProxy() {
	}

	/**
	 * Create a new {@code DelegatingServletProxy} with the given {@link Servlet}
	 * delegate. Bypasses entirely the need for interacting with a Spring application
	 * context, specifying the {@linkplain #setTargetBeanName target bean name}, etc.
	 * <p>
	 * For use in Servlet 3.0+ environments where instance-based registration of Servlets
	 * is supported.
	 * @param delegate the {@code Servlet} instance that this proxy will delegate to and
	 * manage the lifecycle for (must not be {@code null}).
	 * @see #service(ServletRequest, ServletResponse)
	 * @see #invokeDelegate(Servlet, ServletRequest, ServletResponse)
	 * @see #destroy()
	 * @see #setEnvironment(org.springframework.core.env.Environment)
	 */
	public DelegatingServletProxy(Servlet delegate) {
		Assert.notNull(delegate, "delegate Servlet object must not be null");
		this.delegate = delegate;
	}

	/**
	 * Create a new {@code DelegatingServletProxy} that will retrieve the named target
	 * bean from the Spring {@code WebApplicationContext} found in the
	 * {@code ServletContext} (either the 'root' application context or the context named
	 * by {@link #setContextAttribute}).
	 * <p>
	 * For use in Servlet 3.0+ environments where instance-based registration of Servlets
	 * is supported.
	 * <p>
	 * The target bean must implement the standard Servlet Servlet.
	 * @param targetBeanName name of the target Servlet bean to look up in the Spring
	 * application context (must not be {@code null}).
	 * @see #findWebApplicationContext()
	 * @see #setEnvironment(org.springframework.core.env.Environment)
	 */
	public DelegatingServletProxy(String targetBeanName) {
		this(targetBeanName, null);
	}

	/**
	 * Create a new {@code DelegatingServletProxy} that will retrieve the named target
	 * bean from the given Spring {@code WebApplicationContext}.
	 * <p>
	 * For use in Servlet 3.0+ environments where instance-based registration of Servlets
	 * is supported.
	 * <p>
	 * The target bean must implement the standard Servlet Servlet interface.
	 * <p>
	 * The given {@code WebApplicationContext} may or may not be refreshed when passed in.
	 * If it has not, and if the context implements {@link ConfigurableApplicationContext}
	 * , a {@link ConfigurableApplicationContext#refresh() refresh()} will be attempted
	 * before retrieving the named target bean.
	 * <p>
	 * This proxy's {@code Environment} will be inherited from the given
	 * {@code WebApplicationContext}.
	 * @param targetBeanName name of the target Servlet bean in the Spring application
	 * context (must not be {@code null}).
	 * @param wac the application context from which the target Servlet will be retrieved;
	 * if {@code null}, an application context will be looked up from
	 * {@code ServletContext} as a fallback.
	 * @see #findWebApplicationContext()
	 * @see #setEnvironment(org.springframework.core.env.Environment)
	 */
	public DelegatingServletProxy(String targetBeanName, WebApplicationContext wac) {
		Assert.hasText(targetBeanName,
				"target Servlet bean name must not be null or empty");
		this.setTargetBeanName(targetBeanName);
		this.webApplicationContext = wac;
		if (wac != null) {
			this.setEnvironment(wac.getEnvironment());
		}
	}

	/**
	 * Subclasses can invoke this method to specify that this property (which must match a
	 * JavaBean property they expose) is mandatory, and must be supplied as a config
	 * parameter. This should be called from the constructor of a subclass.
	 * <p>
	 * This method is only relevant in case of traditional initialization driven by a
	 * ServletConfig instance.
	 * @param property name of the required property
	 */
	protected final void addRequiredProperty(String property) {
		this.requiredProperties.add(property);
	}

	/**
	 * Map config parameters onto bean properties of this servlet, and invoke subclass
	 * initialization.
	 * @throws ServletException if bean properties are invalid (or required properties are
	 * missing), or if subclass initialization fails.
	 */
	@Override
	public final void init() throws ServletException {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Initializing servlet '" + getServletName() + "'");
		}

		// Set bean properties from init parameters.
		try {
			PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(),
					this.requiredProperties);
			BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
			ResourceLoader resourceLoader = new ServletContextResourceLoader(
					getServletContext());
			bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader,
					getEnvironment()));
			initBeanWrapper(bw);
			bw.setPropertyValues(pvs, true);
		}
		catch (BeansException ex) {
			this.logger.error("Failed to set bean properties on servlet '"
					+ getServletName() + "'", ex);
			throw ex;
		}

		// Let subclasses do whatever initialization they like.
		initServletBean();

		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Servlet '" + getServletName()
					+ "' configured successfully");
		}
	}

	/**
	 * Initialize the BeanWrapper for this HttpServletBean, possibly with custom editors.
	 * <p>
	 * This default implementation is empty.
	 * @param bw the BeanWrapper to initialize
	 * @throws BeansException if thrown by BeanWrapper methods
	 * @see org.springframework.beans.BeanWrapper#registerCustomEditor
	 */
	protected void initBeanWrapper(BeanWrapper bw) throws BeansException {
	}

	/**
	 * Overridden method that simply returns {@code null} when no ServletConfig set yet.
	 * @see #getServletConfig()
	 */
	@Override
	public final String getServletName() {
		return (getServletConfig() != null ? getServletConfig().getServletName() : null);
	}

	/**
	 * Overridden method that simply returns {@code null} when no ServletConfig set yet.
	 * @see #getServletConfig()
	 */
	@Override
	public final ServletContext getServletContext() {
		return (getServletConfig() != null ? getServletConfig().getServletContext()
				: null);
	}

	/**
	 * {@inheritDoc}
	 * @throws IllegalArgumentException if environment is not assignable to
	 * {@code ConfigurableEnvironment}.
	 */
	@Override
	public void setEnvironment(Environment environment) {
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment);
		this.environment = (ConfigurableEnvironment) environment;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * If {@code null}, a new environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = this.createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardServletEnvironment}. Subclasses may override
	 * in order to configure the environment or specialize the environment type returned.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardServletEnvironment();
	}

	/**
	 * Set the name of the ServletContext attribute which should be used to retrieve the
	 * {@link WebApplicationContext} from which to load the delegate {@link Servlet} bean.
	 */
	public void setContextAttribute(String contextAttribute) {
		this.contextAttribute = contextAttribute;
	}

	/**
	 * Return the name of the ServletContext attribute which should be used to retrieve
	 * the {@link WebApplicationContext} from which to load the delegate {@link Servlet}
	 * bean.
	 */
	public String getContextAttribute() {
		return this.contextAttribute;
	}

	/**
	 * Set the name of the target bean in the Spring application context. The target bean
	 * must implement the standard Servlet 2.3 Servlet interface.
	 * <p>
	 * By default, the {@code Servlet-name} as specified for the DelegatingServletProxy in
	 * {@code web.xml} will be used.
	 */
	public void setTargetBeanName(String targetBeanName) {
		this.targetBeanName = targetBeanName;
	}

	/**
	 * Return the name of the target bean in the Spring application context.
	 */
	protected String getTargetBeanName() {
		return this.targetBeanName;
	}

	/**
	 * Set whether to invoke the {@code Servlet.init} and {@code Servlet.destroy}
	 * lifecycle methods on the target bean.
	 * <p>
	 * Default is "false"; target beans usually rely on the Spring application context for
	 * managing their lifecycle. Setting this flag to "true" means that the servlet
	 * container will control the lifecycle of the target Servlet, with this proxy
	 * delegating the corresponding calls.
	 */
	public void setTargetServletLifecycle(boolean targetServletLifecycle) {
		this.targetServletLifecycle = targetServletLifecycle;
	}

	/**
	 * Return whether to invoke the {@code Servlet.init} and {@code Servlet.destroy}
	 * lifecycle methods on the target bean.
	 */
	protected boolean isTargetServletLifecycle() {
		return this.targetServletLifecycle;
	}

	/**
	 * Subclasses may override this to perform custom initialization. All bean properties
	 * of this servlet will have been set before this method is invoked.
	 * <p>
	 * This default implementation is empty.
	 * @throws ServletException if subclass initialization fails
	 */
	protected void initServletBean() throws ServletException {
		synchronized (this.delegateMonitor) {
			if (this.delegate == null) {
				// If no target bean name specified, use Servlet name.
				if (this.targetBeanName == null) {
					this.targetBeanName = getServletName();
				}
				// Fetch Spring root application context and initialize the delegate
				// early,
				// if possible. If the root application context will be started after this
				// Servlet proxy, we'll have to resort to lazy initialization.
				WebApplicationContext wac = findWebApplicationContext();
				if (wac != null) {
					this.delegate = initDelegate(wac);
				}
			}
		}
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// Lazily initialize the delegate if necessary.
		Servlet delegateToUse = this.delegate;
		if (delegateToUse == null) {
			synchronized (this.delegateMonitor) {
				if (this.delegate == null) {
					WebApplicationContext wac = findWebApplicationContext();
					if (wac == null) {
						throw new IllegalStateException(
								"No WebApplicationContext found: no ContextLoaderListener registered?");
					}
					this.delegate = initDelegate(wac);
				}
				delegateToUse = this.delegate;
			}
		}

		// Let the delegate perform the actual doServlet operation.
		invokeDelegate(delegateToUse, request, response);
	}

	@Override
	public void destroy() {
		Servlet delegateToUse = this.delegate;
		if (delegateToUse != null) {
			destroyDelegate(delegateToUse);
		}
	}

	/**
	 * Return the {@code WebApplicationContext} passed in at construction time, if
	 * available. Otherwise, attempt to retrieve a {@code WebApplicationContext} from the
	 * {@code ServletContext} attribute with the {@linkplain #setContextAttribute
	 * configured name} if set. Otherwise look up a {@code WebApplicationContext} under
	 * the well-known "root" application context attribute. The
	 * {@code WebApplicationContext} must have already been loaded and stored in the
	 * {@code ServletContext} before this Servlet gets initialized (or invoked).
	 * <p>
	 * Subclasses may override this method to provide a different
	 * {@code WebApplicationContext} retrieval strategy.
	 * @return the {@code WebApplicationContext} for this proxy, or {@code null} if not
	 * found
	 * @see #DelegatingServletProxy(String, WebApplicationContext)
	 * @see #getContextAttribute()
	 * @see WebApplicationContextUtils#getWebApplicationContext(javax.servlet.ServletContext)
	 * @see WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE
	 */
	protected WebApplicationContext findWebApplicationContext() {
		if (this.webApplicationContext != null) {
			// the user has injected a context at construction time -> use it
			if (this.webApplicationContext instanceof ConfigurableApplicationContext) {
				if (!((ConfigurableApplicationContext) this.webApplicationContext)
						.isActive()) {
					// the context has not yet been refreshed -> do so before returning it
					((ConfigurableApplicationContext) this.webApplicationContext)
							.refresh();
				}
			}
			return this.webApplicationContext;
		}
		String attrName = getContextAttribute();
		if (attrName != null) {
			return WebApplicationContextUtils.getWebApplicationContext(
					getServletContext(), attrName);
		}
		else {
			return WebApplicationContextUtils
					.getWebApplicationContext(getServletContext());
		}
	}

	/**
	 * Initialize the Servlet delegate, defined as bean the given Spring application
	 * context.
	 * <p>
	 * The default implementation fetches the bean from the application context and calls
	 * the standard {@code Servlet.init} method on it, passing in the ServletConfig of
	 * this Servlet proxy.
	 * @param wac the root application context
	 * @return the initialized delegate Servlet
	 * @throws ServletException if thrown by the Servlet
	 * @see #getTargetBeanName()
	 * @see #isTargetServletLifecycle()
	 * @see #getServletConfig()
	 * @see javax.servlet.Servlet#init(javax.servlet.ServletConfig)
	 */
	protected Servlet initDelegate(WebApplicationContext wac) throws ServletException {
		Servlet delegate = wac.getBean(getTargetBeanName(), Servlet.class);
		if (isTargetServletLifecycle()) {
			delegate.init(getServletConfig());
		}
		return delegate;
	}

	/**
	 * Actually invoke the delegate Servlet with the given request and response.
	 * @param delegate the delegate Servlet
	 * @param request the current HTTP request
	 * @param response the current HTTP response
	 * @throws ServletException if thrown by the Servlet
	 * @throws IOException if thrown by the Servlet
	 */
	protected void invokeDelegate(Servlet delegate, ServletRequest request,
			ServletResponse response) throws ServletException, IOException {

		delegate.service(request, response);
	}

	/**
	 * Destroy the Servlet delegate. Default implementation simply calls
	 * {@code Servlet.destroy} on it.
	 * @param delegate the Servlet delegate (never {@code null})
	 * @see #isTargetServletLifecycle()
	 * @see javax.servlet.Servlet#destroy()
	 */
	protected void destroyDelegate(Servlet delegate) {
		if (isTargetServletLifecycle()) {
			delegate.destroy();
		}
	}

	/**
	 * PropertyValues implementation created from ServletConfig init parameters.
	 */
	private static class ServletConfigPropertyValues extends MutablePropertyValues {

		/**
		 * Create new ServletConfigPropertyValues.
		 * @param config ServletConfig we'll use to take PropertyValues from
		 * @param requiredProperties set of property names we need, where we can't accept
		 * default values
		 * @throws ServletException if any required properties are missing
		 */
		public ServletConfigPropertyValues(ServletConfig config,
				Set<String> requiredProperties) throws ServletException {

			Set<String> missingProps = (requiredProperties != null && !requiredProperties
					.isEmpty()) ? new HashSet<String>(requiredProperties) : null;

			Enumeration<String> en = config.getInitParameterNames();
			while (en.hasMoreElements()) {
				String property = en.nextElement();
				Object value = config.getInitParameter(property);
				addPropertyValue(new PropertyValue(property, value));
				if (missingProps != null) {
					missingProps.remove(property);
				}
			}

			// Fail if we are still missing properties.
			if (missingProps != null && missingProps.size() > 0) {
				throw new ServletException(
						"Initialization from ServletConfig for servlet '"
								+ config.getServletName()
								+ "' failed; the following required properties were missing: "
								+ StringUtils.collectionToDelimitedString(missingProps,
										", "));
			}
		}
	}
}
