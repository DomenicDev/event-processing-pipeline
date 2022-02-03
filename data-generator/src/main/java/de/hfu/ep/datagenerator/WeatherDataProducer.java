package de.hfu.ep.datagenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Component
@EnableScheduling
@RestController
public class WeatherDataProducer implements ListenableFutureCallback<SendResult<String, String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherDataProducer.class);

    private final KafkaTemplate<String, String> template;

    private final RandomWeatherRecord record1 = new RandomWeatherRecord("sensor1");
    private final RandomWeatherRecord record2 = new RandomWeatherRecord("sensor2");
    private final RandomWeatherRecord record3 = new RandomWeatherRecord("sensor3");

    public WeatherDataProducer(KafkaTemplate<String, String> template) {
        this.template = template;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 1000)
    public void sendMessage() {
        generateAndSendRandomValues(record1);
        generateAndSendRandomValues(record2);
        generateAndSendRandomValues(record3);
    }

    private void generateAndSendRandomValues(RandomWeatherRecord record) {
        record.nextRandomChange();
        double temperature = record.getTemperature();
        double humidity = record.getHumidity();
        double pressure = record.getPressure();
        String message = createMessage(record.getSensorName(), temperature, humidity, pressure);
        template.send("weather", message).addCallback(this);
    }

    private String createMessage(String sensorName, double temperature, double humidity, double pressure) {
        return String.format("%s|%s|%s|%s|%s", sensorName, temperature, humidity, pressure, LocalDateTime.now());
    }

    // LISTENER METHODS

    @Override
    public void onFailure(Throwable ex) {
        LOGGER.error("Weather data could not be sent", ex);
    }

    @Override
    public void onSuccess(SendResult<String, String> result) {
        LOGGER.info("Successfully sent message {}", result.getProducerRecord().value());
    }

    // REST CONTROLLER

    @GetMapping("/{factor}")
    public void factor(@PathVariable Integer factor) {
        if (factor == null) return;
        record1.setExtraMultiplier(factor);
        record2.setExtraMultiplier(factor);
        record3.setExtraMultiplier(factor);
    }
}
