# ms-rutaexpress-auditoria

Microservicio de Dominio y Consumidor de Eventos en Spring Boot 3 para la persistencia inmutable del historial de auditoria y timeline de trazabilidad de encomiendas de RutaExpress.

---

## 1. Descripcion General

`ms-rutaexpress-auditoria` actua como el componente central de trazabilidad, cumplimiento normativo y auditoria forense del ecosistema RutaExpress. Se suscribe al topico troncal de Apache Kafka `shipments.events` bajo el grupo de consumidores `auditoria-group`, consumiendo los eventos generados por `ms-rutaexpress-envios` durante las transiciones de estado del paquete y registrandolos en un almacenamiento append-only inmutable en el esquema `USER_AUDITORIA` de Oracle Cloud.

---

## 2. Caracteristicas Principales

- **Consumidor Kafka Resiliente:** Escucha continua en el topico `shipments.events` con deserializacion segura (`ErrorHandlingDeserializer` + `JsonDeserializer`).
- **Persistencia Inmutable (Append-Only):** La tabla `HISTORIAL_AUDITORIA` no expone operaciones de actualizacion ni eliminacion (prohibidos endpoints de UPDATE y DELETE).
- **Idempotencia Garantizada:** Validacion por `eventoId` unico (UUID) para evitar registros duplicados ante eventuales reentregas de Kafka.
- **Consultas de Trazabilidad:**
  - Endpoint de Timeline por envio: `GET /api/auditoria/envios/{id}`.
  - Endpoint de consulta paginada y filtrada: `GET /api/auditoria/eventos`.
  - Endpoint de salud publica: `GET /api/publico/salud`.

---

## 3. Arquitectura y Tecnologias

- **Lenguaje:** Java 17
- **Framework:** Spring Boot 3.3.5 (Spring Web, Spring Data JPA, Spring Kafka, Spring Boot Actuator)
- **Mensajeria:** Apache Kafka 3.7.x (Cluster Confluent en puerto 9092)
- **Base de Datos Productiva:** Oracle Autonomous Database en Oracle Cloud Infrastructure (Esquema `USER_AUDITORIA` via Oracle Wallet `cwallet.sso`)
- **Base de Datos de Pruebas:** H2 Database en memoria (Modo Oracle)
- **Puerto de Servicio:** `8084`

---

## 4. Estructura de la Base de Datos

### Tabla `HISTORIAL_AUDITORIA` (Esquema `USER_AUDITORIA`)

| Columna | Tipo | Descripcion |
| :--- | :--- | :--- |
| `id` | NUMBER / BIGINT (PK) | Identificador autoincremental de auditoria |
| `evento_id` | VARCHAR2(64) (UNIQUE) | Identificador universal unico del evento (UUID) |
| `envio_id` | NUMBER / BIGINT | Identificador del envio auditado |
| `codigo_seguimiento` | VARCHAR2(50) | Codigo de seguimiento visible |
| `estado_anterior` | VARCHAR2(30) | Estado anterior del envio (null en creacion) |
| `estado_nuevo` | VARCHAR2(30) | Nuevo estado alcanzado |
| `usuario_email` | VARCHAR2(100) | Correo electronico del usuario u operador que ejecuto la accion |
| `usuario_rol` | VARCHAR2(50) | Rol del usuario (ej: ADMINISTRADOR, OPERADOR, SISTEMA) |
| `fecha_evento` | TIMESTAMP | Marca de tiempo original de emision del evento |
| `detalles_json` | VARCHAR2(4000) | Metadatos contextuales (peso, origen, destino, RUTs) |
| `fecha_registro` | TIMESTAMP | Marca de tiempo en que el consumidor persistio el registro |

**Indices Creados:**
- `idx_auditoria_envio` sobre `envio_id`
- `idx_auditoria_usuario` sobre `usuario_email`
- `idx_auditoria_fecha` sobre `fecha_evento`

---

## 5. Endpoints de la API REST

Todos los endpoints estan redactados obligatoriamente en espanol bajo convencion REST:

### A. Trazabilidad de Envio (Timeline)
`GET /api/auditoria/envios/{id}`
- **Parametros:** `id` (Long, ID numerico del envio).
- **Respuesta (200 OK):**
```json
{
  "envioId": 101,
  "codigoSeguimiento": "RE-2026-00101",
  "totalEventos": 2,
  "timeline": [
    {
      "id": 1,
      "eventoId": "d3b07384-d113-4f4e-a342-998877665544",
      "envioId": 101,
      "codigoSeguimiento": "RE-2026-00101",
      "estadoAnterior": null,
      "estadoNuevo": "CREADO",
      "usuarioEmail": "sistema@rutaexpress.cl",
      "usuarioRol": "SISTEMA",
      "fechaEvento": "2026-10-18T14:00:00",
      "fechaRegistro": "2026-10-18T14:00:01",
      "detalles": {
        "pesoKg": 5.20,
        "direccionOrigen": "Av. Libertador 1234",
        "direccionDestino": "Av. Providencia 4567"
      }
    },
    {
      "id": 2,
      "eventoId": "e4c18495-e224-5a5f-b453-112233445566",
      "envioId": 101,
      "codigoSeguimiento": "RE-2026-00101",
      "estadoAnterior": "CREADO",
      "estadoNuevo": "ACEPTADO",
      "usuarioEmail": "operador@rutaexpress.cl",
      "usuarioRol": "OPERADOR",
      "fechaEvento": "2026-10-18T14:15:00",
      "fechaRegistro": "2026-10-18T14:15:01",
      "detalles": {
        "pesoKg": 5.20
      }
    }
  ]
}
```

### B. Consulta Paginada de Eventos de Auditoria
`GET /api/auditoria/eventos?usuario=admin&estadoNuevo=ACEPTADO&page=0&size=20`
- **Filtros opcionales:**
  - `usuario`: Filtro parcial por correo electronico.
  - `fechaDesde`: Formato ISO `yyyy-MM-dd'T'HH:mm:ss`.
  - `fechaHasta`: Formato ISO `yyyy-MM-dd'T'HH:mm:ss`.
  - `estadoNuevo`: Estado resultante (ej: `ACEPTADO`, `EN_RUTA`).
  - `page`, `size`, `sort`: Paginacion estandar de Spring Data.

### C. Salud del Servicio
`GET /api/publico/salud`
- **Respuesta (200 OK):**
```json
{
  "estado": "DISPONIBLE",
  "servicio": "ms-rutaexpress-auditoria",
  "puerto": 8084,
  "timestamp": "2026-10-18T17:09:41"
}
```

---

## 6. Pruebas y Validacion

Ejecucion de pruebas automatizadas:
```bash
mvn clean test
```
Incluye pruebas unitarias de servicio y repositorio, y pruebas integrales con consumidor de Kafka real y persistencia H2.
