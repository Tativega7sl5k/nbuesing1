package dev.buesing.ksd.publisher;

import dev.buesing.ksd.common.domain.PurchaseOrder;
import dev.buesing.ksd.common.serde.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Producer {

    private static final Random RANDOM = new Random();

    private final Options options;

    public Producer(final Options options) {
        this.options = options;
    }

    private String getRandomSku() {
        return StringUtils.leftPad(Integer.toString(RANDOM.nextInt(options.getNumberOfProducts())), 10, '0');
    }

    private String getRandomUser() {
        return Integer.toString(RANDOM.nextInt(options.getNumberOfUsers()));
    }

    private String getRandomStore() {
        return Integer.toString(RANDOM.nextInt(options.getNumberOfStores()));
    }

    private int getRandomItemCount() {
        return options.getLineItemCount(); //RANDOM.nextInt(MAX_ORDER_SIZE) + 1;
    }

    private int getRandomQuantity() {
        return RANDOM.nextInt(options.getMaxQuantity()) + 1;
    }

    private PurchaseOrder createPurchaseOrder() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();

        purchaseOrder.setTimestamp(Instant.now());
        purchaseOrder.setOrderId(UUID.randomUUID().toString());
        purchaseOrder.setUserId(getRandomUser());
        purchaseOrder.setStoreId(getRandomStore());
        purchaseOrder.setItems(IntStream.range(0, getRandomItemCount())
                .boxed()
                .map(i -> {
                    final PurchaseOrder.LineItem item = new PurchaseOrder.LineItem();
                    item.setSku(getRandomSku());
                    item.setQuantity(getRandomQuantity());
                    item.setQuotedPrice(null); // TODO remove from domain
                    return item;
                })
                .collect(Collectors.toList())
        );

        return purchaseOrder;
    }

    public void start() {

        final KafkaProducer<String, PurchaseOrder> kafkaProducer = new KafkaProducer<>(properties(options));

        while (true) {

            PurchaseOrder purchaseOrder = createPurchaseOrder();

            log.info("Sending key={}, value={}", purchaseOrder.getOrderId(), purchaseOrder);
            kafkaProducer.send(new ProducerRecord<>(options.getPurchaseTopic(), null, purchaseOrder.getOrderId(), purchaseOrder), (metadata, exception) -> {
                if (exception != null) {
                    log.error("error producing to kafka", exception);
                } else {
                    log.debug("topic={}, partition={}, offset={}", metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            try {
                Thread.sleep(options.getPause());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // kafkaProducer.close();

    }

    private Map<String, Object> properties(final Options options) {
        return Map.ofEntries(
                Map.entry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, options.getBootstrapServers()),
                Map.entry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT"),
                Map.entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()),
                Map.entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName())
        );
    }

    private static void dumpRecord(final ConsumerRecord<String, String> record) {
        log.info("Record:\n\ttopic     : {}\n\tpartition : {}\n\toffset    : {}\n\tkey       : {}\n\tvalue     : {}", record.topic(), record.partition(), record.offset(), record.key(), record.value());
    }

    public static Properties toProperties(final Map<String, Object> map) {
        final Properties properties = new Properties();
        properties.putAll(map);
        return properties;
    }
}