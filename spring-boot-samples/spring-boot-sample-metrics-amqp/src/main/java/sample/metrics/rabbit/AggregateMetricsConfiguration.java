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

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.actuate.endpoint.MetricReaderPublicMetrics;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.aggregate.AggregateMetricReader;
import org.springframework.boot.actuate.metrics.amqp.MetricWriterMessageListener;
import org.springframework.boot.actuate.metrics.reader.MetricReader;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AggregateMetricsConfiguration {

	private InMemoryMetricRepository inMemoryMetrics = new InMemoryMetricRepository();

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public MetricWriterMessageListener metricWriterMessageListener() {
		return new CustomListener(this.inMemoryMetrics);
	}

	@RabbitListener(queues = "${spring.metrics.export.rabbit.queue}")
	public static class CustomListener extends MetricWriterMessageListener {

		public CustomListener(MetricWriter writer) {
			super(writer);
		}

	}

	@Bean
	public PublicMetrics metricsAggregate() {
		return new MetricReaderPublicMetrics(aggregatesMetricReader());
	}

	private MetricReader aggregatesMetricReader() {
		AggregateMetricReader repository = new AggregateMetricReader(this.inMemoryMetrics);
		repository.setKeyPattern("d.d.d");
		return repository;
	}
}
