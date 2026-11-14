# Plan por fases para igualar RutaUTP Android con iOS

Fecha: 10 de noviembre de 2026.

Estado: **fases 1–6 implementadas; fase 7 preparada sin activación; fase 8 con candidata local verificada**.
Última validación local: 14 de noviembre de 2026. La fase 3 conserva pendientes
las comprobaciones de interacción, rotación y GPS en dispositivo. La fase 4
requiere validar Google Cloud, el broker y dos teléfonos para su cierre integral.
La fase 5 conserva pendientes la revisión lingüística, TalkBack, texto grande
y la matriz de permisos en dispositivo.
La fase 6 incorpora revisión de vistas previas Android y pruebas geométricas;
conserva pendiente la comparación iPhone/Android y las pruebas de OpenGL en hardware.
La fase 7 cuenta con integración y pruebas locales; faltan Firebase, servidor
de pruebas y validación de entrega, según confirmó el equipo.
La fase 8 incorpora cachés, prefiltro conservador, equivalencia con el código
anterior, mediciones JVM y contratos de conservación de datos. La validación
de rendimiento y actualización instalada en Android continúa pendiente.
Ver [resultado y reproducción de fase 1](FASE_1_REFERENCIA_Y_VALIDACION.md).
Ver [correcciones y pruebas de fase 2](FASE_2_RUTAS_Y_LLEGADAS.md).
Ver [continuidad y contexto de fase 3](FASE_3_CONTINUIDAD_Y_CONTEXTO.md).
Ver [mapas, búsqueda y conexión de fase 4](FASE_4_MAPAS_BUSQUEDA_Y_CONEXION.md).
Ver [idiomas, accesibilidad y errores de fase 5](FASE_5_IDIOMAS_ACCESIBILIDAD_Y_ERRORES.md).
Ver [presentación y transportes 3D de fase 6](FASE_6_PRESENTACION_Y_TRANSPORTES_3D.md).
Ver [preparación de notificaciones remotas de fase 7](FASE_7_NOTIFICACIONES_REMOTAS.md).
Ver [rendimiento y candidata local de fase 8](FASE_8_RENDIMIENTO_Y_ENTREGA.md).
Las fases 7 y 8 se publican juntas en main en esta entrega. La ejecución remota
registrada durante su preparación aprobó el commit
[6a2297c](https://github.com/Defartion/RutaUTPAndroid/actions/runs/43612152671),
que incluye los commits previos 21cc604 y aedfc71. La fase 7 quedó guardada en
08ba24a; la fase 8 se conserva como commit independiente. Los resultados de
esta publicación se consultan en GitHub Actions.

## Objetivo y referencia

Conseguir que Android reproduzca las funciones, reglas de viaje, organización visual y continuidad de uso de iOS, utilizando las integraciones propias de Android.

Este plan desarrolla la [auditoría completa](auditoria-main-2026-11-10/INFORME.md). Los identificadores F01–F15 corresponden a sus hallazgos.

| Plataforma | Rama de referencia | Commit auditado |
|---|---|---|
| iOS | main | 8145276bb6fc72a168cc78119def2727578ec0bb |
| Android | main | 7ba57cbe73f97e83fd112584a9e8ff89623ac4c1 |

Estos commits fijan el punto de partida. Antes de implementar una fase se comprobará main y se registrarán los cambios relevantes posteriores, sin incorporar automáticamente reglas nuevas a mitad de una corrección.

## Cómo se decidirá la fidelidad

1. **Comportamiento:** mismos datos de entrada deben producir decisiones equivalentes sobre rutas, transbordos, llegada, viaje activo y errores.
2. **Contenido:** conservar nombres, mensajes, estados, secciones, información y orden de las acciones de iOS, adaptando menciones al sistema operativo.
3. **Presentación:** respetar jerarquía, paleta de empresa, agrupaciones, tarjetas, protagonismo del mapa y relaciones de tamaño. Adaptar las medidas al ancho disponible y al tamaño de texto.
4. **Continuidad:** conservar selección, posición y viaje cuando iOS los conserva. Distinguir cambio de pestaña, segundo plano, recreación de actividad y cierre de proceso.
5. **Integración nativa:** los permisos, el botón Atrás, los selectores de fotos/contactos, los avisos del sistema y los servicios de mapas deben seguir las reglas de Android.
6. **Límites visibles:** comunicar cuándo faltan ubicación, red, configuración, datos institucionales o un servicio real. Mantener los límites de demostración que también tiene iOS.

La revisión visual necesita comparar ambas aplicaciones en estados equivalentes. Mientras falten capturas o un iPhone disponible, esa validación se registrará como pendiente, aunque la implementación y sus pruebas de lógica estén terminadas.

## Orden general

| Fase | Entrega | Hallazgos | Depende de |
|---|---|---|---|
| 1 | Referencia reproducible y comprobaciones de regresión | F14, inicio | — |
| 2 | Cálculo fiel de rutas y llegadas | F01, F02 | 1 |
| 3 | Continuidad del viaje, pestañas y contexto | F03, F04, F05, F10; preparación F15 | 2 |
| 4 | Mapas, búsqueda y conexión por instalación | F06, F08, F09 | 3 |
| 5 | Idiomas, accesibilidad y errores | F11, F12 | 4 |
| 6 | Fidelidad visual y transportes 3D | Revisión transversal | 3, 5 |
| 7 | Notificaciones remotas Android y backend común | F07 | 3, 4; infraestructura disponible |
| 8 | Rendimiento y validación de la entrega | F13, cierre F14 | 2–6; 7 para publicar push |

Se seguirá este orden para reducir cambios simultáneos. Si la fase 7 depende de servicios aún no disponibles, se podrá completar la fase 8 con los avisos remotos identificados como pendientes y desactivados.

## Fase 1 — Referencia reproducible y comprobaciones de regresión

**Resultado esperado:** disponer de una referencia estable para saber qué debe hacer Android y detectar regresiones desde la primera corrección.

### Trabajo

- Registrar los commits de main, la versión de los datos y los contratos del backend que se van a usar.
- Convertir los hallazgos de la auditoría en una lista de seguimiento por fase.
- Mantener como base las 119 pruebas unitarias aprobadas, compilación, análisis estático y comprobador del APK.
- Preparar casos de referencia para ETA, comparación de rutas, cambio de modo y viaje activo; cada corrección añadirá sus pruebas de comportamiento.
- Incorporar comprobaciones automáticas de compilación, pruebas y análisis en las propuestas de cambio a main.
- Definir los estados visuales que se compararán en las cinco pestañas.

### Se cierra cuando

- La referencia iOS y Android queda registrada y reproducible.
- Las comprobaciones existentes pasan.
- Cada hallazgo tiene una fase, una prueba de cierre y un estado.
- Existe una comprobación automática inicial para los cambios dirigidos a main.

La infraestructura de pruebas se ampliará durante las siguientes fases; las pruebas en teléfono se hacen al cerrar cada flujo que las necesite.

## Fase 2 — Cálculo fiel de rutas y llegadas

**Resultado esperado:** las recomendaciones y estimaciones siguen las reglas actuales de iOS.

### Trabajo

- F01: evitar ETA para un vehículo detenido; distinguir velocidad desconocida y velocidad baja.
- F01: admitir ambos sentidos cuando el vehículo se dirige hacia el objetivo, incluyendo recorridos circulares.
- F01: aplicar el mismo radio de 1600 m para el objetivo y usar la referencia vigente del mapa o sede.
- F01: calcular sobre la geometría original y reservar la reducción de puntos para dibujar.
- F02: ofrecer transbordos frente a un directo conforme a la regla de iOS: directo de al menos 45 minutos y ahorro mínimo de 15 minutos y del 25 %.
- Conservar todas las líneas directas válidas y los criterios recomendada, más rápida y subida cercana.
- Mantener caminatas verificadas, límites de distancia y mensajes de estimación cuando falle el proveedor de rutas.

### Se cierra cuando

- Los casos de referencia coinciden en sentido, selección de línea y política de ETA.
- Se prueban detenido, tráfico lento, velocidad desconocida, falta de rumbo, sentido inverso y circular.
- Los casos límite de ahorro de transbordo producen los mismos resultados.
- Los fallos del servicio de caminatas conservan una ruta estimada identificada correctamente.

Las rutas peatonales concretas pueden diferir entre Google y Apple. Se exigirá la misma política de selección y claridad del estado, sin exigir el mismo trazado del proveedor.

## Fase 3 — Continuidad del viaje, pestañas y contexto

**Resultado esperado:** ninguna acción antigua, cambio de pestaña o notificación sustituye accidentalmente el viaje del usuario.

### Trabajo

- F03: cancelar e invalidar búsquedas anteriores al seleccionar otro destino, limpiar, cambiar de sede o cambiar de catálogo.
- F03: impedir que respuestas fuera de orden cambien destino, cámara, líneas o itinerario.
- F04: reconstruir el contexto al alternar entre transporte urbano y Buses UTP.
- F04: retirar flota, selección y cálculos del catálogo anterior; mostrar el estado correcto cuando falte el feed UTP.
- F10: conservar la subpestaña de Guardado, búsquedas, detalle de ruta y posiciones de listas conforme al comportamiento iOS.
- F10: alojar el modelo de tracking fuera de una composición temporal.
- F05: centralizar la protección de viaje declarado, tracking y navegación de una línea ante notificaciones.
- Consumir cada acción de notificación una sola vez y comprobar vigencia/existencia del destino.
- Definir el comportamiento de Atrás, giro y recreación de actividad; guardar únicamente el estado que deba sobrevivir.
- F15: preparar espacios de nombres separados para datos institucionales y probar IDs repetidos con datos de muestra, antes de incorporar el futuro feed.

### Se cierra cuando

- Una búsqueda A tardía no modifica una selección B.
- Cambiar de pestaña conserva el estado acordado y pausa el trabajo visual innecesario.
- Los recordatorios y avisos no interrumpen un viaje.
- Cambiar a Buses UTP no muestra unidades urbanas como institucionales.
- Los escenarios de recreación no duplican viajes, permisos ni acciones pendientes.
- La separación de IDs institucionales está definida y probada. La activación del feed real seguirá pendiente hasta disponer de los datos y del contrato del backend.

## Fase 4 — Mapas, búsqueda y conexión por instalación

**Resultado esperado:** el mismo APK puede distribuirse a teléfonos con identidades independientes y la configuración de mapas es efectiva.

### Trabajo

- F08: conectar la configuración prevista de Google con el valor real utilizado por la aplicación.
- F08: comprobar mapa, búsqueda y caminatas con las restricciones y servicios habilitados que correspondan.
- F09: definir una zona de búsqueda coherente con las rutas y sedes; el rectángulo actual deja fuera el ámbito de 999 paraderos del GTFS.
- F06: aprovisionar la cuenta MQTT de cada instalación sin tener que generar un APK distinto por teléfono.
- F06: conservar las credenciales en almacenamiento protegido por Android Keystore.
- Separar configuración pública del canal y credenciales; contemplar reemplazo y revocación de cuenta.
- Verificar conexión TLS, cuenta lectora, cuenta contribuyente y desconexión/reconexión.
- Comprobar el quorum con dos teléfonos y dos cuentas diferentes.

### Se cierra cuando

- Cambiar la configuración de Google cambia el valor efectivo de la aplicación.
- Las consultas acordadas cubren los extremos del área de servicio.
- Un mismo APK instalado en A y B usa cuentas diferentes.
- Revocar una cuenta no altera la otra instalación.
- Las funciones muestran estados claros ante configuración incompleta y fallos de autorización.

Las restricciones, cuotas y disponibilidad del proveedor se verificarán en el entorno del equipo; el código del repositorio no acredita por sí solo esos ajustes.

## Fase 5 — Idiomas, accesibilidad y errores

**Resultado esperado:** la aplicación mantiene el idioma seleccionado y ofrece estados comprensibles ante problemas.

### Trabajo

- F11: resolver los 329 textos literales detectados sin clave y ampliar la comprobación a plantillas e interpolaciones.
- Usar claves estables y parámetros para cantidades, importes y nombres de paradero.
- Revisar los seis idiomas en las pestañas, formularios, errores, permisos y avisos.
- F12: distinguir catálogo vacío, feed UTP pendiente y fallo de lectura del feed urbano; mostrar el reintento correspondiente.
- Revisar TalkBack: etiquetas, orden del foco, agrupación de tarjetas y controles de mapas/3D.
- Revisar texto al 150 % y 200 %, contraste y tema oscuro.
- Comprobar señas sobre hojas y pantallas completas, cancelación al salir y aviso de clip no disponible.
- Revisar rechazo de permisos de cámara, ubicación, movimiento y notificaciones.

### Se cierra cuando

- Las cadenas revisadas no vuelven al español por una clave ausente.
- Cambiar de idioma conserva pantalla y viaje.
- Cada fallo de datos/red/permisos muestra el mensaje y acción apropiados.
- Los flujos principales se pueden recorrer con TalkBack y texto grande.
- Las señas disponibles funcionan y las ausentes se identifican correctamente.

Los clips en francés, portugués de Brasil, alemán y chino siguen siendo un pendiente de contenido compartido por ambas plataformas.

## Fase 6 — Fidelidad visual y transportes 3D

**Resultado esperado:** Android conserva la identidad visual y la organización de iOS en pantallas pequeñas y grandes.

### Trabajo, en este orden

1. Navegación común: barra inferior, cabeceras, menú, zonas seguras y transiciones.
2. Mapa: buscador, chips, panel inferior, tarjetas de buses, selección y estados vacíos.
3. Rutas: buscador, identidad de línea, tarjetas, detalle y explorador.
4. Guardado: pestañas, secciones de lugares/paraderos, detalle y formularios.
5. Seguridad: Ayuda/Comunidad, contacto, reportes y paraderos.
6. Perfil: identidad, billetera, carné, cupones y grupos de ajustes.
7. Transportes 3D: seis modelos, colores, bandas, cartel, encuadre y gestos.

En cada pantalla se compararán claro/oscuro, datos cargados, carga, vacío, error y texto grande cuando corresponda. Se mantendrán las convenciones Android de Atrás, teclado, selectores y permisos.

### Se cierra cuando

- Las capturas comparables muestran la misma jerarquía, información y orden de acciones.
- No hay texto cortado, superposiciones ni controles inaccesibles en los tamaños probados.
- Los recursos 3D se abren desde el APK y conservan su identidad por línea.
- Giro, zoom, desplazamiento y restauración de la superficie funcionan en dispositivo.
- Las diferencias visuales inevitables de plataforma quedan registradas.

Una vista previa de Compose no sustituye la prueba de mapa, cámara o OpenGL en un dispositivo. Los materiales e iluminación 3D de ambos motores pueden producir diferencias aun utilizando la misma geometría de origen.

## Fase 7 — Notificaciones remotas Android y backend común

**Estado local:** integración preparada el 14/11/2026. Pasan 264 pruebas Android,
88 de backend y 42 de herramientas. Se mantiene APNs y se prepara FCM sobre
el backend fijado. La entrega real conserva las dependencias externas siguientes.

**Resultado esperado:** FCM y APNs comparten reglas de suscripción, vigencia y silencio, con proveedores de entrega distintos.

### Trabajo

- F07: acordar el registro versionado para APNs y FCM.
- Adaptar el backend compartido para persistir proveedor, aplicación, token y revisión.
- Conectar el adaptador FCM al worker después de las validaciones existentes.
- Mantener quorum, deduplicación, vigencia, pausas, días silenciados y horarios.
- Configurar Firebase y las cuentas/permisos MQTT requeridos.
- Probar permiso, token, registro confirmado, aceptación del proveedor y entrega al teléfono como estados separados.
- Validar aperturas durante un viaje utilizando las protecciones de fase 3.
- Comprobar que el soporte APNs existente no se rompe.

### Se cierra cuando

- Un registro FCM válido se acepta, renueva y revoca.
- Una notificación real llega al teléfono bajo las condiciones permitidas.
- Los eventos vencidos o silenciados no se entregan indebidamente.
- Los tokens y credenciales de servicio no aparecen en logs ni en el APK.
- Las pruebas de APNs y del contrato compartido continúan pasando.

### Dependencias externas

Proyecto Firebase del equipo, credenciales de servidor custodiadas fuera del repositorio, backend ejecutándose en su entorno soportado, permisos del broker y dispositivo Android compatible con la integración elegida.

Las dependencias faltantes mantienen esta fase pendiente. No se anunciará entrega remota operativa solo porque el cliente compile.

## Fase 8 — Rendimiento y validación de la entrega

**Estado local:** implementación y candidata verificadas el 14/11/2026.
Pasan 285 pruebas Android y 45 de herramientas; se conservan los 88 resultados
del paquete backend de fase 7 y 39 referencias Compose. Los tiempos publicados
son de JVM en la computadora, sin atribuirlos a Android. Tres pruebas nuevas
de dispositivo están compiladas; su ejecución, actualización instalada y
comparación con iPhone siguen pendientes.

**Resultado esperado:** la versión conserva las correcciones, responde bien en un teléfono Android intermedio y tiene una entrega reproducible.

### Trabajo

- F13: preparar geometría ETA por catálogo y proyección por destino, con invalidación correcta.
- F13: incorporar un prefiltro conservador para líneas cercanas.
- Medir carga inicial, búsquedas, cambio de destino y snapshots con flota grande.
- Revisar trabajo oculto, consumo de ubicación y comportamiento al bloquear/desbloquear.
- F14: completar pruebas instrumentales y capturas de los flujos corregidos.
- Ejecutar pruebas, compilación, análisis y comprobación del APK de la entrega.
- Probar una actualización sobre una instalación anterior para conservar favoritos, preferencias, fotos y saldo de demostración.
- Realizar una comparación conjunta iPhone–Android con el mismo escenario y el backend acordado.
- Actualizar la documentación de paridad con los resultados y límites restantes.

### Se cierra cuando

- Las comprobaciones automáticas pasan y las regresiones importantes están cubiertas.
- Las mediciones cumplen los objetivos acordados sobre el dispositivo de referencia.
- La aplicación conserva los datos anteriores y no mezcla simulación con posiciones observadas.
- Cada pendiente de dispositivo o infraestructura está identificado.
- La versión candidata tiene APK, referencias de código/datos y resultados de validación registrados.

## Diferencias de plataforma que se aceptarán y comprobarán

| Área | Qué debe mantenerse fiel a iOS | Adaptación Android |
|---|---|---|
| Mapas | Destino, reglas de cercanía, información y estados | Google Maps/Places/Directions pueden devolver cartografía o caminos distintos. |
| Ubicación | Consentimiento, decisión de abordaje y uso responsable | Permiso preciso/aproximado, Fused Location y ciclo de vida Android. |
| Movimiento | Reglas de evidencia y estados del detector | Activity Recognition entrega información distinta de CoreMotion; requiere validación en campo. |
| Pestañas | Conservar contexto y viaje | Implementación Compose con control de visibilidad y estado propio de Android. |
| Atrás y sistema | Salir/cancelar sin perder acciones indebidamente | Botón/gesto Atrás, teclado y recreación de actividad de Android. |
| Avisos locales | Horarios y política de silencio | Alarmas inexactas, canales y permisos del sistema; pueden existir retrasos. |
| Avisos remotos | Suscripciones, contenido, caducidad y apertura | FCM y su configuración propia, manteniendo APNs para iOS. |
| Fotos y contactos | Misma información elegida y resultado del formulario | Selectores y permisos nativos Android. |
| Accesibilidad | Acceso a las mismas acciones e información | TalkBack, escalado de texto y semántica Compose. |
| 3D | Modelo, identidad, colores, encuadre y gestos | OpenGL ES y recursos convertidos; iluminación/materiales pueden variar. |
| Persistencia | Conservar preferencias y datos al actualizar | DataStore, SharedPreferences y Keystore con migraciones Android. |

## Condición de cierre de cada fase

Cada fase terminará con:

- Funciones corregidas y hallazgos relacionados.
- Pruebas ejecutadas y sus resultados.
- Evidencia visual o de dispositivo cuando el cambio lo requiera.
- Diferencias Android/iOS aceptadas y su motivo.
- Pendientes externos que todavía impidan validar una parte.

Una fase que requiera dispositivo o infraestructura no se marcará completa solo con compilación y pruebas unitarias. Se registrará por separado la implementación terminada y la validación pendiente.

## Seguimiento actualizado

| Fase | Implementación | Validación |
|---|---|---|
| 1 | Implementada | Referencia y verificadores activos; workflow de main aprobado en GitHub el 13/11/2026. |
| 2 | Implementada | 146 pruebas Android aprobadas; 19 casos de referencia activos. Comprobación GPS y visual en dispositivo pendiente. |
| 3 | Implementada y publicada | 180 pruebas Android y 18 del verificador en su entrega; 27 referencias activas. Interacción, rotación y GPS en dispositivo pendientes. |
| 4 | Implementada localmente | 212 pruebas Android y 25 de herramientas; APK y análisis correctos. Tres pruebas de cifrado compiladas, pendientes de ejecutar en dispositivo. Google Cloud, broker, revocación y quorum con dos teléfonos pendientes. |
| 5 | Implementada localmente | 235 pruebas Android y 35 de herramientas; 1299 claves revisadas sin carencias detectadas. Dos pruebas de texto grande compiladas. Revisión lingüística, TalkBack, texto grande y permisos en dispositivo pendientes. |
| 6 | Pendiente | Requiere capturas comparables y dispositivo. |
| 7 | Pendiente | Requiere backend/Firebase y entrega real. |
| 8 | Pendiente | Requiere dispositivo y resultados de las fases anteriores. |

## Alcance compartido pendiente

Pagos reales, autenticación de cuenta, carné institucional verificado, publicaciones comunitarias en servidor, promociones reales, inspección de iluminación y el feed de buses UTP siguen siendo desarrollos compartidos pendientes. Este plan mantiene las funciones de demostración actuales mientras se corrige la fidelidad Android.

El aislamiento de IDs UTP se prepara en fase 3; publicar el catálogo institucional requiere los datos reales y coordinación con el backend.
