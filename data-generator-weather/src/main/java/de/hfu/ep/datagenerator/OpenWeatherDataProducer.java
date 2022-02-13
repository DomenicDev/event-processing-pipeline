package de.hfu.ep.datagenerator;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;


@Component
@EnableScheduling
public class OpenWeatherDataProducer {

    private final KafkaTemplate<String, String> template;
    private final WebClient webClient;
    private final String token;

    public OpenWeatherDataProducer(KafkaTemplate<String, String> template,
                                   WebClient.Builder builder,
                                   @Value("${token}") String token) {
        this.template = template;
        this.webClient = builder.baseUrl("https://api.openweathermap.org/data/2.5/weather").build();
        this.token = token;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void receiveWeatherData() {
        // read data
        String currentData = readCurrentWeatherData();

        // extract data
        JSONObject data = new JSONObject(currentData);
        double temperature =  data.getJSONObject("main").getDouble("temp");
        double pressure = data.getJSONObject("main").getDouble("pressure");
        double humidity = data.getJSONObject("main").getDouble("humidity");

        // send data
        String messageData = createMessage(temperature, pressure, humidity);
        sendMessage(messageData);
    }

    private String readCurrentWeatherData() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("lat", "48.050")
                        .queryParam("lon", "8.20257")
                        .queryParam("appId", token)
                        .queryParam("mode", "json")
                        .queryParam("units", "metric")
                        .queryParam("lang", "DE")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String createMessage(double temperature, double pressure, double humidity) {
        return String.format("%s|%s|%s|%s", temperature, humidity, pressure, LocalDateTime.now());
    }

    private void sendMessage(String messageData) {
        template.send("weather", "furtwangen", messageData).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                System.out.println("failure..." + ex);
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                System.out.println("Success: " + result.getProducerRecord().value());
            }
        });
    }

}
