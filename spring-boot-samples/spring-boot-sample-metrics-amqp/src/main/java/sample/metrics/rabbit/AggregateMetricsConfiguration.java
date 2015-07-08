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

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.MetricReaderPublicMetrics;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.aggregate.AggregateMetricReader;
import org.springframework.boot.actuate.metrics.export.MetricExportProperties;
import org.springframework.boot.actuate.metrics.integration.AmqpMetricAggregationFlows;
import org.springframework.boot.actuate.metrics.reader.MetricReader;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
public class AggregateMetricsConfiguration {

	@Value("${spring.metrics.export.rabbit.queue}")
	private String queue;

	@Autowired
	private MetricExportProperties properties;

	private InMemoryMetricRepository inMemoryMetrics = new InMemoryMetricRepository();

	@Bean
	public IntegrationFlow inboundFlow(ConnectionFactory connectionFactory,
			MessageConverter converter) {
		return AmqpMetricAggregationFlows.inboundFlow(this.queue, connectionFactory,
				converter, (metric, headers) -> {
					this.inMemoryMetrics.set(metric);
					return null;
				});
	}

	@Bean
	public PublicMetrics metricsAggregate() {
		return new MetricReaderPublicMetrics(aggregatesMetricReader());
	}

	private MetricReader aggregatesMetricReader() {
		AggregateMetricReader repository = new AggregateMetricReader(this.inMemoryMetrics);
		repository.setKeyPattern(this.properties.getAggregate().getKeyPattern());
		return repository;
	}
}
