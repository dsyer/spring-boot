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

package sample.metrics.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.metrics.export.MetricExportProperties;
import org.springframework.boot.actuate.metrics.integration.AmqpMetricAggregationFlows;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@SpringBootApplication
public class SampleRabbitExportApplication {

	public static final String HEADER_METRIC_PREFIX = "metricPrefix";

	@Autowired
	private MetricExportProperties properties;

	@Value("${spring.metrics.export.rabbit.queue}")
	private String queue;

	@Bean
	public MessageChannel metricsChannel() {
		return new DirectChannel();
	}

	@Bean
	public IntegrationFlow outboundFlow(ConnectionFactory connectionFactory) {
		return AmqpMetricAggregationFlows.outboundFlow(this.properties.getAggregate()
				.getPrefix(), metricsChannel(), exchange().getName(), connectionFactory,
				messageConverter());
	}

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public FanoutExchange exchange() {
		return new FanoutExchange("spring.metrics");
	}

	@Bean
	protected Binding binding() {
		return BindingBuilder.bind(queue()).to(exchange());
	}

	@Bean
	protected Queue queue() {
		return new Queue(this.queue, true, false, true);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SampleRabbitExportApplication.class, args);
	}

}
