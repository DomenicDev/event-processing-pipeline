package de.hfu.ep.datagenerator;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.Channel.MessageListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FinanceDataProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceDataProducer.class);

    private final KafkaTemplate<String, Double> template;


    public FinanceDataProducer(KafkaTemplate<String, Double> template, @Value("${token}") String token) throws AblyException {
        this.template = template;
        AblyRealtime realtime = new AblyRealtime(token);

        // subscribe to bitcoin updates
        String chanName = "[product:ably-coindesk/crypto-pricing]btc:usd";
        Channel bitcoinChannel = realtime.channels.get(chanName);
        bitcoinChannel.subscribe((MessageListener) this::sendBitcoinMessage);

        // subscribe to ethereum updates
        String ethChannelName = "[product:ably-coindesk/crypto-pricing]eth:usd";
        Channel ethereumChannel = realtime.channels.get(ethChannelName);
        ethereumChannel.subscribe((MessageListener) this::sendEthereumMessage);
    }

    private void sendBitcoinMessage(Message message) {
        convertAndSend("bitcoin", message);
    }

    private void sendEthereumMessage(Message message) {
        convertAndSend("ethereum", message);
    }

    private void convertAndSend(String key, Message message) {
        try {
            Double data = Double.parseDouble((String) message.data);
            sendMessage(key, data);
        } catch (Exception e) {
            LOGGER.error("error while reading data", e);
        }
    }

    private void sendMessage(String key, Double value) {
        template.send("finance", key, value);
    }

}