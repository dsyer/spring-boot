/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.boot.actuate.metrics.newrelic;

import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;

import com.newrelic.api.agent.NewRelic;

/**
 * Metric writer for <a href="http://newrelic.com">NewRelic</a>. Can be used to export
 * Spring Boot metrics to New Relic if the agent is installed. Should be harmless if the
 * agent is not present.
 *
 * @author Dave Syer
 *
 */
public class NewRelicMetricWriter implements MetricWriter {

	private String domain = "Spring/Java";

	/**
	 * A unique String identifying all the metrics from this application. Normally it
	 * suffices that this is like a folder name (the actual identity of the process and
	 * aggregation keys are set up by the agent).
	 *
	 * @param domain the domain to set
	 */
	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Override
	public void increment(Delta<?> delta) {
		NewRelic.incrementCounter(getName(delta.getName()), delta.getValue().intValue());
	}

	@Override
	public void set(Metric<?> value) {
		NewRelic.recordMetric(getName(value.getName()), value.getValue().floatValue());
	}

	private String getName(String name) {
		return this.domain + "/" + name;
	}

	@Override
	public void reset(String metricName) {
		// Not supported;
	}

}
