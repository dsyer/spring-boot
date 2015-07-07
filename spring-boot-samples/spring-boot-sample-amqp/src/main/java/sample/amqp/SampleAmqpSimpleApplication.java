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

package sample.amqp;

import java.util.Date;

import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SampleAmqpSimpleApplication {

	// If the producer was a separate application you would re-declare the exchange there
	// as well, but there's no need for this sample app.
	@RabbitListener(bindings = @QueueBinding(key = "foo", value = @Queue, exchange = @Exchange(value = "exchange.foo", type = ExchangeTypes.TOPIC)))
	public void process(@Payload String foo) {
		System.out.println(new Date() + ": " + foo);
	}

	@Bean
	public Sender mySender() {
		return new Sender();
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SampleAmqpSimpleApplication.class, args);
	}

}
