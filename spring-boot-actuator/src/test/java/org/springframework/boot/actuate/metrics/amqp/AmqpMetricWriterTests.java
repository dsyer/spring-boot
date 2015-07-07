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

import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.writer.Delta;

/**
 * @author Dave Syer
 */
public class AmqpMetricWriterTests {

	private RabbitOperations template = Mockito.mock(RabbitOperations.class);

	private AmqpMetricWriter writer = new AmqpMetricWriter("prefix", "exchange",
			this.template);

	@Test
	public void simpleSet() {
		this.writer.set(new Metric<Number>("foo", 1.23));
		Mockito.verify(this.template).convertAndSend(Matchers.contains("exchange"),
				Matchers.contains("prefix"), Matchers.any(Metric.class),
				Matchers.any(MessagePostProcessor.class));
	}

	@Test
	public void simpleIncrement() {
		this.writer.increment(new Delta<Integer>("foo", 1));
		Mockito.verify(this.template).convertAndSend(Matchers.contains("exchange"),
				Matchers.contains("prefix"), Matchers.any(Delta.class),
				Matchers.any(MessagePostProcessor.class));
	}

	@Test
	public void simpleReset() {
		this.writer.reset("counter.foo");
		Mockito.verify(this.template).convertAndSend(Matchers.contains("exchange"),
				Matchers.contains("prefix"), Matchers.any(Metric.class),
				Matchers.any(MessagePostProcessor.class));
	}

}
