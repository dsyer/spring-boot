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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.ExportMetricWriter;
import org.springframework.boot.actuate.metrics.amqp.AmqpMetricWriter;
import org.springframework.boot.actuate.metrics.export.MetricExportProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SampleRabbitExportApplication {

	@Autowired
	private MetricExportProperties properties;

	@Value("${spring.metrics.export.rabbit.queue}")
	private String queue;

	@Bean
	@ExportMetricWriter
	public AmqpMetricWriter rabbitMetricWriter(ConnectionFactory connectionFactory,
			MessageConverter converter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(converter);
		return new AmqpMetricWriter(this.properties.getAggregate().getPrefix(),
				exchange().getName(), template);
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
