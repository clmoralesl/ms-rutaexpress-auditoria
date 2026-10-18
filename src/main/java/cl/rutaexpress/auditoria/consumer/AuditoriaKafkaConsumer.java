package cl.rutaexpress.auditoria.consumer;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.service.AuditoriaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaKafkaConsumer.class);

    private final AuditoriaService auditoriaService;

    public AuditoriaKafkaConsumer(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @KafkaListener(
            topics = "${kafka.topic.shipments-events:shipments.events}",
            groupId = "${spring.kafka.consumer.group-id:auditoria-group}"
    )
    public void consumirEvento(ConsumerRecord<String, EventoEnvioKafkaDTO> record) {
        if (record == null || record.value() == null) {
            log.warn("Mensaje recibido nulo en topico shipments.events. Se ignora.");
            return;
        }

        EventoEnvioKafkaDTO evento = record.value();
        String clave = record.key();
        int particion = record.partition();
        long offset = record.offset();

        log.info("Evento recibido desde Kafka [topico={}, particion={}, offset={}, clave={}, envioId={}, estadoNuevo={}]",
                record.topic(), particion, offset, clave, evento.envioId(), evento.estadoNuevo());

        try {
            auditoriaService.registrarEvento(evento);
        } catch (Exception ex) {
            log.error("Fallo inesperado al persistir evento de auditoria [eventoId={}, envioId={}]: {}",
                    evento.eventoId(), evento.envioId(), ex.getMessage(), ex);
        }
    }
}
