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

package org.springframework.boot.autoconfigure.report;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.Outcome;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Bean used to gather autoconfiguration decisions, and then generate a collection
 * of info for beans that were created by Boot as well as situations where the outcome
 * was negative.
 *
 * @author Greg Turnquist
 */
public class AutoConfigurationReport implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

	private Set<BootCreatedBeanInfo> beansBootCreated = new HashSet<BootCreatedBeanInfo>();
	private Map<String, List<AutoConfigurationDecision>> autoconfigurationDecisions =
			new HashMap<String, List<AutoConfigurationDecision>>();
	private Map<String, List<String>> positive = new HashMap<String, List<String>>();
	private Map<String, List<String>> negative = new HashMap<String, List<String>>();
	private ApplicationContext context;

	public static void registerDecision(ConditionContext context, String message,
			String classOrMethodName, Outcome outcome) {
		for (String beanName : context.getBeanFactory().getBeanDefinitionNames()) {
			if (beanName.equals("autoConfigurationReport")) {
				AutoConfigurationReport autoconfigurationReport = context
						.getBeanFactory().getBean(AutoConfigurationReport.class);
				autoconfigurationReport.registerDecision(message, classOrMethodName, outcome);
			}
		}
	}

	private void registerDecision(String message, String classOrMethodName, Outcome outcome) {
		AutoConfigurationDecision decision = new AutoConfigurationDecision(message, classOrMethodName, outcome);
		if (!this.autoconfigurationDecisions.containsKey(classOrMethodName)) {
			this.autoconfigurationDecisions.put(classOrMethodName, new ArrayList<AutoConfigurationDecision>());
		}
		this.autoconfigurationDecisions.get(classOrMethodName).add(decision);
	}

	public Set<BootCreatedBeanInfo> getBeansBootCreated() {
		return this.beansBootCreated;
	}

	public Map<String, List<String>> getNegativeDecisions() {
		return this.negative;
	}

	public Set<Class<?>> getBeanTypesBootCreated() {
		Set<Class<?>> beanTypesBootCreated = new HashSet<Class<?>>();
		for (BootCreatedBeanInfo bootCreatedBeanInfo : this.getBeansBootCreated()) {
			beanTypesBootCreated.add(bootCreatedBeanInfo.getBeanType());
		}
		return beanTypesBootCreated;
	}

	public Set<String> getBeanNamesBootCreated() {
		Set<String> beanNamesBootCreated = new HashSet<String>();
		for (BootCreatedBeanInfo bootCreatedBeanInfo : this.getBeansBootCreated()) {
			beanNamesBootCreated.add(bootCreatedBeanInfo.getBeanName());
		}
		return beanNamesBootCreated;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.context = applicationContext;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		splitDecisionsIntoPositiveAndNegative();
		scanPositiveDecisionsForBeansBootCreated();
	}

	/**
	 * Scan the list of {@link AutoConfigurationDecision}'s, and if all outcomes true,
	 * then put it on the positive list. Otherwise, put it on the negative list.
	 */
	private synchronized void splitDecisionsIntoPositiveAndNegative() {
		for (String key : this.autoconfigurationDecisions.keySet()) {
			boolean match = true;
			for (AutoConfigurationDecision decision : this.autoconfigurationDecisions.get(key)) {
				if (!decision.getOutcome().isMatch()) {
					match = false;
				}
			}
			if (match) {
				if (!positive.containsKey(key)) {
					positive.put(key, new ArrayList<String>());
				}
				for (AutoConfigurationDecision decision : this.autoconfigurationDecisions.get(key)) {
					positive.get(key).add(decision.getMessage());
				}
			} else {
				if (!negative.containsKey(key)) {
					negative.put(key, new ArrayList<String>());
				}
				for (AutoConfigurationDecision decision : this.autoconfigurationDecisions.get(key)) {
					negative.get(key).add(decision.getMessage());
				}
			}
		}
	}

	/**
	 * Scan all the decisions based on successful outcome, and
	 * try to find the corresponding beans Boot created.
	 */
	private synchronized void scanPositiveDecisionsForBeansBootCreated() {
		for (String key : this.positive.keySet()) {
			for (AutoConfigurationDecision decision : this.autoconfigurationDecisions.get(key)) {
				for (String beanName : context.getBeanDefinitionNames()) {
					Object bean = context.getBean(beanName);
					if (decision.getMessage().contains(beanName) && decision.getMessage().contains("matched")) {
						boolean anyMethodsAreBeans = false;
						for (Method method : bean.getClass().getMethods()) {
							if (context.containsBean(method.getName())) {
								this.beansBootCreated.add(new BootCreatedBeanInfo(method.getName(),
										method.getReturnType(), this.positive.get(key)));
								anyMethodsAreBeans = true;
							}
						}

						if (!anyMethodsAreBeans) {
							this.beansBootCreated.add(new BootCreatedBeanInfo(beanName, bean, this.positive.get(key)));
						}
					}
				}
			}
		}
	}

}
