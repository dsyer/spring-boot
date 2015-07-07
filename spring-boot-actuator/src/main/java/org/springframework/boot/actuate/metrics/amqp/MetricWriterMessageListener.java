/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.actuate.metrics.amqp;

import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * @author Dave Syer
 */
public class MetricWriterMessageListener {

	private MetricWriter writer;

	private String defaultPrefix = "";

	public MetricWriterMessageListener(MetricWriter writer) {
		this.writer = writer;
	}

	/**
	 * @param defaultPrefix the default prefix to set
	 */
	public void setDefaultPrefix(String defaultPrefix) {
		this.defaultPrefix = defaultPrefix.endsWith(".") ? defaultPrefix : defaultPrefix
				+ ".";
	}

	public void process(
			@Payload Metric<?> metric,
			@Header(name = AmqpMetricWriter.HEADER_METRIC_PREFIX, required = false) String prefix) {
		prefix = prefix == null ? this.defaultPrefix : prefix + ".";
		metric = new Metric<Number>(prefix + metric.getName(), metric.getValue(),
				metric.getTimestamp());
		this.writer.set(metric);
	}

}
