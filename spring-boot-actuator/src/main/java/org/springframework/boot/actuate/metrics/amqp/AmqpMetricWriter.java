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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.util.StringUtils;

/**
 * A {@link MetricWriter} that pushes data to AMQP.
 *
 * @author Dave Syer
 * @since 1.3.0
 */
public class AmqpMetricWriter implements MetricWriter {

	public static final String HEADER_METRIC_NAME = "metric.name";

	public static final String HEADER_METRIC_PREFIX = "metric.prefix";

	private static final String DEFAULT_KEY = "spring.metrics";

	private static final MessagePostProcessor NOOP_POSTPROCESSOR = new MessagePostProcessor() {
		@Override
		public Message postProcessMessage(Message message) throws AmqpException {
			return message;
		}
	};

	private final RabbitOperations template;

	private String exchange;

	private String key;

	private boolean addHeaders = true;

	private Map<String, String> headers = new LinkedHashMap<String, String>();

	private String prefix;

	/**
	 * @param headers the fixed headers to add
	 */
	public void setHeaders(Map<String, String> headers) {
		this.headers.putAll(headers);
	}

	/**
	 * @param addHeaders the flag to set
	 */
	public void setAddHeaders(boolean addHeaders) {
		this.addHeaders = addHeaders;
	}

	/**
	 * Create a new writer instance with the given parameters.
	 * @param exchange the exchange to which we send metrics
	 * @param template the connection to the AMQP broker
	 */
	public AmqpMetricWriter(String exchange, RabbitOperations template) {
		this(null, exchange, template);
	}

	/**
	 * Create a new writer with the given parameters.
	 * @param prefix the prefix for routing key to use when sending metrics (can be null)
	 * @param exchange the exchange to which we send metrics
	 * @param template the connection to the AMQP broker
	 */
	public AmqpMetricWriter(String prefix, String exchange, RabbitOperations template) {
		this.exchange = exchange;
		this.template = template;
		prefix = StringUtils.hasText(prefix) ? prefix : DEFAULT_KEY;
		while (prefix.endsWith(".")) {
			prefix = prefix.substring(prefix.length() - 1);
		}
		this.key = prefix;
		this.prefix = prefix + ".";
	}

	@Override
	public void increment(Delta<?> delta) {
		MessagePostProcessor headerEnricher = getHeaderEnricher(delta);
		this.template.convertAndSend(this.exchange, this.prefix + delta.getName(), delta,
				headerEnricher);
	}

	@Override
	public void set(Metric<?> value) {
		MessagePostProcessor headerEnricher = getHeaderEnricher(value);
		this.template.convertAndSend(this.exchange, this.prefix + value.getName(), value,
				headerEnricher);
	}

	@Override
	public void reset(String name) {
		if (name.contains("counter.")) {
			Metric<Number> value = new Metric<Number>(name, 0L);
			MessagePostProcessor headerEnricher = getHeaderEnricher(value);
			this.template.convertAndSend(this.exchange, this.prefix + value.getName(),
					value, headerEnricher);
		}
	}

	private MessagePostProcessor getHeaderEnricher(final Metric<?> value) {
		if (!this.addHeaders) {
			return NOOP_POSTPROCESSOR;
		}
		return new MessagePostProcessor() {

			@Override
			public Message postProcessMessage(Message message) throws AmqpException {
				MessageProperties properties = message.getMessageProperties();
				properties.setHeader(HEADER_METRIC_NAME, value.getName());
				properties.setHeader(HEADER_METRIC_PREFIX, AmqpMetricWriter.this.key);
				for (String key : AmqpMetricWriter.this.headers.keySet()) {
					properties.setHeader(key, AmqpMetricWriter.this.headers.get(key));
				}
				return message;
			}

		};
	}

}
