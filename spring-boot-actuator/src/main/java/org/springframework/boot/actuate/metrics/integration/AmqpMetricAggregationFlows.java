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

package org.springframework.boot.actuate.metrics.integration;

import java.util.Map;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.integration.dsl.HeaderEnricherSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.amqp.Amqp;
import org.springframework.integration.dsl.support.Consumer;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.messaging.MessageChannel;

/**
 * @author Dave Syer
 */
public class AmqpMetricAggregationFlows {

	private static final String HEADER_METRIC_PREFIX = "metricPrefix";

	public static IntegrationFlow inboundFlow(String queueName,
			ConnectionFactory connectionFactory, MessageConverter converter,
			GenericHandler<Metric<Number>> handler) {
		// @formatter:off
		return IntegrationFlows.from(Amqp
				.inboundAdapter(connectionFactory, queueName)
					.mappedRequestHeaders(AmqpMetricAggregationFlows.HEADER_METRIC_PREFIX)
					.messageConverter(converter))
				.<Metric<Number>> handle(new InboundEnricher())
				.handle(handler)
				.get();
		//@formatter:on
	}

	public static IntegrationFlow outboundFlow(String prefix,
			MessageChannel metricsChannel, String exchangeName,
			ConnectionFactory connectionFactory, MessageConverter converter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(converter);
		// @formatter:off
		return IntegrationFlows
				.from(metricsChannel)
				.enrichHeaders(new OutboundEnricher(prefix))
				.handle(Amqp
					.outboundAdapter(template)
					.mappedRequestHeaders(HEADER_METRIC_PREFIX)
					.exchangeName(exchangeName))
				.get();
		//@formatter:on
	}

	private static class OutboundEnricher implements Consumer<HeaderEnricherSpec> {

		private String prefix;

		public OutboundEnricher(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public void accept(HeaderEnricherSpec headers) {
			headers.header(HEADER_METRIC_PREFIX, this.prefix);
		}

	}

	private static class InboundEnricher implements GenericHandler<Metric<Number>> {

		@Override
		public Object handle(Metric<Number> metric, Map<String, Object> headers) {
			metric = new Metric<Number>(
					headers.get(AmqpMetricAggregationFlows.HEADER_METRIC_PREFIX) + "."
							+ metric.getName(), metric.getValue(), metric.getTimestamp());
			return metric;
		}

	}

}
