package de.hfu.ep.datagenerator;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic topic() {
        return new NewTopic("weather", 1, (short) 1);
    }

}
