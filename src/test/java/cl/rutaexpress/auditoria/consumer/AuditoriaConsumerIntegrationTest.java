package cl.rutaexpress.auditoria.consumer;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.repository.HistorialAuditoriaRepository;
import cl.rutaexpress.auditoria.service.AuditoriaService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuditoriaConsumerIntegrationTest {

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private HistorialAuditoriaRepository repository;

    private KafkaTemplate<String, Object> testKafkaTemplate;

    @BeforeEach
    void setUpProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(props);
        testKafkaTemplate = new KafkaTemplate<>(pf);
    }

    @AfterEach
    void tearDown() {
        if (testKafkaTemplate != null) {
            testKafkaTemplate.destroy();
        }
    }

    @Test
    @DisplayName("Integracion con Cluster Real Kafka: Consumir evento desde shipments.events y persistir timeline inmutable")
    void testConsumirEventoYPersistirTimeline() throws Exception {
        Long envioId = 900L + new Random().nextInt(1000);
        UUID eventoId = UUID.randomUUID();
        String codigoSeguimiento = "RE-AUDIT-" + envioId;

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("pesoKg", 6.80);
        detalles.put("origen", "Bodega Central");
        detalles.put("destino", "Sucursal Norte");

        EventoEnvioKafkaDTO evento = new EventoEnvioKafkaDTO(
                eventoId,
                envioId,
                codigoSeguimiento,
                null,
                "CREADO",
                "operador1@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                detalles
        );

        // 1. Emitir evento real hacia Kafka shipments.events con particionamiento por clave
        testKafkaTemplate.send("shipments.events", String.valueOf(envioId), evento).get();

        // 2. Esperar que el consumidor de auditoria persista en H2
        long fin = System.currentTimeMillis() + 10000;
        List<HistorialAuditoria> eventos = Collections.emptyList();
        while (System.currentTimeMillis() < fin) {
            eventos = repository.findByEnvioIdOrderByFechaEventoAsc(envioId);
            if (!eventos.isEmpty()) {
                break;
            }
            Thread.sleep(250);
        }

        assertFalse(eventos.isEmpty(), "El evento debe haber sido consumido y persistido en HISTORIAL_AUDITORIA");

        // 3. Validar via Timeline Service
        TimelineEnvioResponse timeline = auditoriaService.obtenerTimelinePorEnvioId(envioId);
        assertNotNull(timeline);
        assertEquals(envioId, timeline.envioId());
        assertEquals(codigoSeguimiento, timeline.codigoSeguimiento());
        assertEquals(1, timeline.totalEventos());
        assertEquals("CREADO", timeline.timeline().get(0).estadoNuevo());
        assertEquals("operador1@rutaexpress.cl", timeline.timeline().get(0).usuarioEmail());
        assertEquals("OPERADOR", timeline.timeline().get(0).usuarioRol());
        assertEquals(eventoId.toString(), timeline.timeline().get(0).eventoId());
    }
}
