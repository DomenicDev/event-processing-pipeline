package de.hfu.ep.datagenerator;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinanceTopicConfiguration {

    @Bean
    public NewTopic financeTopic() {
        return new NewTopic("finance", 1, (short) 1);
    }

}
