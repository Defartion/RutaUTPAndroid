# Comparativa profunda de RutaUTP iOS y Android

Fecha de revisión: **10 de noviembre de 2026**, America/Lima.

## 1. Dictamen

**Android tiene una adaptación funcional extensa de iOS, con los mismos datos principales, pero todavía conserva divergencias importantes de comportamiento y de integración.** La siguiente actualización debería centrarse en las reglas de viaje, la conservación del estado y la integración compartida. Las pantallas principales, los favoritos, el monedero de demostración y los recursos 3D ya tienen implementaciones Android.

Los puntos de mayor impacto son:

1. La estimación de llegada de vehículos usa reglas diferentes: Android calcula tiempos para vehículos detenidos y rechaza algunos recorridos inversos que iOS admite.
2. La comparación de rutas omite la regla de iOS que exige un ahorro relevante antes de recomendar un transbordo frente a un directo.
3. Una búsqueda de destino pendiente puede sobrescribir una selección posterior en Android.
4. El cambio a Buses UTP puede conservar flota y contexto urbanos del mapa.
5. Los toques de notificaciones no protegen todos los tipos de viaje activo.
6. La configuración MQTT de Android se incorpora al APK, mientras iOS conserva credenciales aprovisionadas en Keychain.
7. El cliente FCM de Android y el registro APNs del backend actual son incompatibles.
8. El manifiesto Android utiliza una clave de Google literal y deja sin efecto la propiedad de compilación prevista para sustituirla.
9. El rectángulo de búsqueda de Android es más pequeño que la cobertura del propio GTFS.
10. La conservación de pestañas, el tracking y las traducciones presentan brechas comprobables.

La compilación y las pruebas Android pasan. Eso acredita su estado de compilación y los casos automatizados existentes; la igualdad de comportamiento de las dos aplicaciones necesita cerrar estos pendientes y completar pruebas en dispositivos.

## 2. Alcance y referencias exactas

| Repositorio | Rama consultada | Commit publicado |
|---|---|---|
| [RutaUTP iOS][ios-main] | main | 8145276bb6fc72a168cc78119def2727578ec0bb |
| [RutaUTP Android][android-main] | main | 7ba57cbe73f97e83fd112584a9e8ff89623ac4c1 |

Se consultó la referencia remota main de ambos repositorios. iOS se descargó con una clonación limitada a esa rama. El checkout Android está en main y su HEAD coincide con la referencia remota. No se inspeccionaron otras ramas.

El último commit iOS tiene fecha 9/11/2026, 16:43:42 -0500. El de Android tiene fecha 9/11/2026, 19:12:06 -0500, y su mensaje es «Actualizar pantallas Android a main iOS del 9 de noviembre».

**Consecuencia:** no hay una nueva main iOS posterior a la referencia que Android declara haber portado. Las brechas encontradas corresponden a diferencias dentro de esa adaptación, no a una cola nueva de commits iOS.

Se contrastaron navegación, pantallas, modelos, planificador, GTFS, búsqueda, ubicación, detección de pasajeros, MQTT, ocupación, cambios de ruta, notificaciones, persistencia, pagos de demostración, accesibilidad, traducciones, 3D, empaquetado y pruebas. Los comentarios y documentos del proyecto se usaron como contexto y se verificaron contra la implementación.

En Android había cinco archivos sin seguimiento antes de empezar: una configuración de Markdown de .idea y cuatro herramientas heredadas. Se conservaron y se excluyeron de las conclusiones sobre el contenido publicado de main. No se modificó código de la aplicación.

### Qué se pudo ejecutar

| Comprobación | Resultado de esta revisión |
|---|---|
| Android: testDebugUnitTest | **119 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas**. Se forzó una nueva ejecución del task de pruebas. |
| Android: assembleDebug | **Correcto**. APK generado. |
| Android: lintDebug | **Correcto**, sin errores; 76 advertencias y 16 sugerencias. |
| Comprobador del APK del repositorio | **Correcto**: seis mallas accesibles, catálogos y pantallas esperados. |
| Igualdad de recursos entre repositorios | Comparación binaria y hashes, detallados en evidencia.json. |
| Dispositivo Android | La lista de dispositivos y emuladores conectados está vacía. |
| Compilación/pruebas iOS | No ejecutables en este equipo Windows: requieren Xcode/macOS. |
| Pruebas Python del backend | Intentadas; detenidas al importar fcntl, un módulo utilizado por el backend y ausente en Windows. No se sustituyó ni simuló ese mecanismo. |
| Servicios remotos | No se enviaron ubicaciones, reportes, pagos ni notificaciones; no se alteró el broker ni el backend desplegado. |

Las referencias a defectos de flujo que siguen se apoyan en lectura del código. Sus escenarios se describen para reproducirlos en pruebas; no se presentan como grabaciones o ensayos realizados en un teléfono.

## 3. Matriz funcional

**Equivalencia de base** significa que existe una implementación Android del mismo flujo. No implica identidad visual, pruebas de campo aprobadas ni igualdad en todos sus casos límite.

| Área | iOS main | Android main | Evaluación |
|---|---|---|---|
| Bienvenida | Carrusel, idioma, entrada y legales | Carrusel, idioma, entrada y legales | Equivalencia de base. |
| Navegación principal | Cinco pestañas conservadas con TabView; barra común | Router manual con Crossfade y barra dentro de cada pantalla | Brecha de conservación y transiciones: F10. |
| Mapa | MapKit, ubicación, chips, selector de punto, líneas y vehículos | Google Maps Compose, ubicación, chips y selector | Amplia adaptación; divergencias F03, F04, F08 y F09. |
| Catálogo de rutas | GTFS, búsqueda por identidad y calles, variantes | Mismo feed y búsqueda especializada | Base alineada. |
| Rutas cercanas | Distancia al recorrido, radios 400/800 m | Distancia al recorrido, radios 400/800 m | Misma política principal; optimización distinta: F13. |
| Filtro desde un lugar guardado | Paraderos hasta 300 m | Paraderos hasta 300 m | Base alineada. |
| Detalle de ruta | Identidad, extremos, estadísticas, mapa y modelo | Identidad, extremos, estadísticas, mapa y modelo | Equivalencia de base. |
| Explorador | Recorrido, paraderos, encuadre y seguimiento | Recorrido, paraderos, encuadre y seguimiento | Requiere comparación en dispositivo. |
| Itinerarios | Directos y un transbordo; caminatas verificadas | Directos y un transbordo; caminatas verificadas | Parámetros comunes; selección de transbordos divergente: F02. |
| Llegada de vehículos | Geometría original, sentido, velocidad observada y objetivo hasta 1600 m | Geometría reducida, sentido solo forward y objetivo hasta 800 m | Brecha funcional: F01. |
| Navegación de una línea | Progreso, paradero próximo, simulación | Progreso, paradero próximo, simulación | Base alineada; falta prueba GPS/rotación/recreación. |
| Tracking puerta a puerta | Modelo conservado desde la raíz | Modelo creado dentro de la composición | Pérdida de sesión al salir: F10. |
| Confirmación de abordaje | Pregunta, selección de línea, ocupación y punto opcional | Flujo Android equivalente | Buena cobertura funcional de base. |
| Referencias de subida | Hasta 200 referencias locales, con coordenadas opcionales | Hasta 200 referencias locales, con coordenadas opcionales | Base alineada. |
| Ubicación compartida | Servicio compartido con conteo de consumidores | Servicio compartido con conteo de consumidores | Arquitectura equivalente; requiere permisos reales y pruebas de consumo. |
| Actividad física | CoreMotion | Activity Recognition Transition API | Equivalencia de intención; sensores y confianza no son idénticos. |
| Detección de pasajero | Máquina de estados y consentimiento | Máquina de estados y consentimiento | Umbrales principales alineados. |
| Vehículos MQTT | Contratos v2/v3, procedencia y control de simulación | Contratos v2/v3, procedencia y control de simulación | Receptor implementado; identidad y provisión pendientes: F06. |
| Ocupación comunitaria | Snapshot y recibo, confirmación por dos cuentas, caducidad | Mismo canal y política principal | Necesita ensayo conjunto con el backend. |
| Cambios de ruta | Reporte, punto, quorum y caducidad | Mismo canal y política principal | Necesita ensayo conjunto; push pendiente: F07. |
| Lugares guardados | Categoría, frecuencia, icono, coordenadas y campus fijo | Misma información con DataStore | Base alineada; no sincronizada entre dispositivos. |
| Líneas guardadas | Referencias route_id | Referencias route_id | Base alineada; futura separación institucional: F15. |
| Paraderos guardados | Compartidos entre Guardado y Seguridad | Compartidos entre Guardado y Seguridad | Base alineada. |
| Seguridad | Ayuda, contacto, comunidad y referencias | Ayuda, contacto, comunidad y referencias | Adaptación extensa. |
| Comunidad general | Datos y publicación de demostración | Datos y publicación de demostración | Límite compartido: sin backend de publicaciones. |
| Paraderos iluminados | Referencias seleccionadas del GTFS | Selección determinista del GTFS | No acreditan iluminación comprobada en campo. |
| Perfil | Identidad, foto, grupos de ajustes, contacto | Identidad, foto, grupos de ajustes, contacto | Equivalencia de base. |
| Carné | Imagen física y credencial digital de muestra | Imagen física y credencial digital de muestra | Sin verificación institucional en ambos. |
| Monedero | Saldo e historial locales de demostración | Saldo e historial locales de demostración | Equivalente; no es procesamiento financiero. |
| QR | Lectura y validación de formato, moneda e importe | Lectura y validación de formato, moneda e importe | Base robusta; requiere prueba de cámara. |
| Tarjetas | Referencias reducidas en almacenamiento protegido | Últimos cuatro dígitos, red y nombre cifrados | Base alineada; no almacena PAN/CVV para operar pagos. |
| Negocios/cupones | Catálogo local ficticio y favoritos | Mismo catálogo y favoritos | Equivalente como demostración. |
| Recordatorios locales | Agenda nativa y política de silencio | AlarmManager, canales y reprogramación | Equivalencia de intención; entrega inexacta y apertura divergente: F05. |
| Avisos remotos | Cliente APNs y worker compartido, desactivados por defecto | Cliente FCM y adaptador sin integración completa | F07: falta compatibilidad y despliegue verificable. |
| Idiomas | Seis opciones, catálogo y fallbacks | Seis opciones y mismo catálogo | Muchos textos Android no encuentran clave: F11. |
| Señas | Manifiesto local, clips es/en y ventana superior | Mismo contenido con Media3 y ventana Android | Recursos idénticos; faltan clips en otros cuatro idiomas en ambos. |
| Apariencia/temáticas | Preferencias y 24 sedes del catálogo | Preferencias y mismo catálogo | Base alineada; F04 afecta contexto de transporte. |
| 3D | Seis USDZ y presentación nativa iOS | Seis mallas convertidas y visor OpenGL ES | Geometría derivada del origen; materiales y gestos requieren QA visual. |
| Offline | Recursos locales y explicación de límites | Recursos locales y explicación de límites | Sin mapas base descargados; error de diagnóstico Android: F12. |
| Recuperación local | Conservación de originales y exportación | Conservación de JSON y exportación | Base alineada; no equivale a restauración completa ni a sincronización. |
| Pruebas e integración | XCTest, UI tests y workflow de GitHub | JUnit, capturas de Compose y test instrumental de plantilla | Falta automatización y cobertura de flujos Android: F14. |

## 4. Hallazgos y acciones para Android

Prioridades: **P1** afecta decisiones de viaje, integridad del contexto o una función que se quiera habilitar; **P2** afecta consistencia, cobertura o mantenibilidad. La prioridad de F07 depende de si los avisos remotos forman parte de la siguiente entrega. No se identificó un P0 demostrado.

### F01 — P1: el ETA de vehículos no sigue las reglas actuales de iOS

**Android:** VehicleETAEstimator reduce el shape a 250 puntos, admite objetivos hasta 800 m del recorrido, exige que el rumbo siga el sentido del shape y usa velocidad programada si la observada está por debajo de 0,5 m/s.

**iOS:** conserva el shape original; el objetivo puede estar hasta 1600 m; calcula para ambos sentidos si el vehículo va hacia el destino; si la velocidad observada es 0 o 0,2 m/s devuelve ausencia de ETA. Solo usa el horario cuando la velocidad es desconocida.

Esto produce tres diferencias visibles:

- Bus detenido: Android puede mostrar una llegada; iOS muestra ausencia de ETA.
- Objetivo situado entre 800 y 1600 m del corredor: Android descarta una estimación que iOS puede admitir.
- Vehículo con rumbo inverso al shape que se acerca a un objetivo situado detrás de su progreso: iOS puede calcular; Android lo rechaza.

Además, Android usa UTP como ancla de respaldo de las llegadas aunque el inicio del mapa sea otra sede. iOS usa el ancla vigente del mapa o la coordenada de inicio. La distancia restante puede referirse a un sitio distinto del que el usuario está consultando.

**Acción:** portar las reglas de velocidad, sentido, radio y ancla; separar geometría original para cálculo y geometría reducida para dibujo.

**Cierre:** fixtures compartidos para detenido, 0,2 m/s, tráfico lento, velocidad desconocida, rumbo ausente, sentido inverso, circular, destino a 1200 m, recorrido con curvas y sede distinta de UTP. iOS ya contiene varios de estos casos en VehiclePositionSanitizerTests; las dos pruebas ETA actuales de Android cubren un subconjunto.

Evidencia: [estimador Android][a-eta], [ancla Android][a-fleet], [estimador iOS][i-eta], [casos iOS][i-eta-tests].

### F02 — P1: se ofrecen transbordos que iOS descarta por poco beneficio

El planificador Android ya comparte límites relevantes: hasta un transbordo, 1600 m en los extremos, 350 m entre micros, caminata a 1,4 m/s, media frecuencia del segundo bus más dos minutos y penalización de ordenación.

La diferencia está en el comparador de alternativas. En iOS, cuando existe un directo, un transbordo solo resulta útil para comparación si el directo dura al menos 45 minutos y se ahorran al menos 15 minutos y el 25 % del tiempo. Android incluye transbordos de tramos de al menos 800 m, pero no aplica esa condición de ahorro.

**Ejemplo de política, no medición de un viaje real:** directo de 35 minutos frente a dos micros de 31 minutos. iOS descarta ese cambio para la comparación; Android no tiene esa exclusión.

**Acción:** añadir la política útil-para-comparación antes de elegir alternativas y destacados, manteniendo la búsqueda de transbordos cuando no existe directo.

**Cierre:** casos con directo de 35 minutos, de 60 minutos y ahorros de 5, 14, 15 y 20 minutos; comprobar recomendada, más rápida, subida cercana y todas las directas. Comparar IDs de rutas, subidas y bajadas, no solo duración.

Evidencia: [planificador Android][a-planner], [filtro y alternativas iOS][i-planner].

### F03 — P1: una respuesta de búsqueda antigua puede cambiar el destino nuevo

buscarTexto de Android crea una corrutina para Places sin guardar un job de búsqueda ni una revisión de selección. limpiar, seleccionar un chip o elegir otro destino no invalidan esa respuesta. Al terminar, el resultado llama a seleccionarLugarExterno.

iOS cancela búsquedas e invalida la selección anterior; verifica selectionRevision antes de aplicar la respuesta.

**Escenario:** iniciar búsqueda A con red lenta, elegir destino B o limpiar el mapa, recibir A. La implementación Android permite que A vuelva a seleccionar un destino.

Las consultas de rutas cercanas también se lanzan desde varias acciones sin un identificador de revisión del ancla; debe cubrirse el mismo orden de respuestas.

**Acción:** dar identidad a cada selección, cancelar el trabajo anterior y comprobar esa identidad después de cada espera. Limpiar debe invalidar toda selección pendiente.

**Cierre:** respuestas A/B en orden inverso, limpiar durante petición, cambio de sede durante cálculo y cambio de modo durante carga. Ninguna respuesta antigua debe modificar destino, cámara, flota o itinerario.

Evidencia: [búsqueda y limpieza Android][a-search], [búsqueda iOS][i-search].

### F04 — P1: cambiar a Buses UTP no invalida todo el contexto urbano

MapaScreen escucha el cambio de busesUTPActivos, pero llama a actualizarSedeTrabajo. Ese método sale inmediatamente si no cambian sede ni referencia al campus. Activar el modo institucional estando ya en UTP cumple precisamente esa condición.

El ViewModel del mapa sobrevive al cambio de pestaña. Su flota, proveedor real e itinerario pueden quedar asociados al catálogo urbano. El panel solo muestra «rutas próximamente» cuando la lista de buses está vacía; conservar buses previos evita ese estado. El receptor del mapa tampoco filtra su flota por catálogo institucional como hace iOS.

iOS incorpora el modo a la identidad de sus pestañas y reemplaza el contexto de tracking al cambiarlo.

**Acción:** tratar el catálogo/modo como parte de la identidad del estado; cancelar búsquedas y viajes, retirar flota y selecciones, y cargar exclusivamente el catálogo seleccionado. Un feed institucional ausente debe producir un mapa sin unidades urbanas atribuidas a UTP.

**Cierre:** abrir mapa urbano con flota y destino, activar Buses UTP desde Perfil, volver; repetir con MQTT configurado y sin él; desactivar el modo; cambiar empresa/sede. Ningún estado del contexto anterior debe contaminar el nuevo.

Evidencia: [actualización de sede Android][a-mode], [panel Android][a-panel], [cambio de contexto iOS][i-root].

### F05 — P1: abrir una notificación puede interrumpir un viaje activo

MainActivity navega directamente al mapa al recibir un recordatorio con destino y a una pestaña al recibir un aviso diario. En el caso push comprueba únicamente el viaje declarado del coordinador pasivo.

iOS protege tres estados: viaje declarado, tracking puerta a puerta y navegación de una línea abierta. Sus recordatorios informan antes de sustituir un destino activo y sus consejos diarios preservan el viaje.

En Android, salir de TrackingDemo retira la composición y detiene su modelo. Por eso un toque de recordatorio puede tener un efecto mayor que abrir una pestaña.

**Acción:** centralizar el estado de viaje/navegación y la recepción de acciones de notificación. Validar vigencia y existencia del destino; consumir cada acción una vez; informar o dejar pendiente la navegación cuando hay un viaje activo.

**Cierre:** tocar cada tipo de aviso durante tracking, navegación de una línea y contribución declarada; repetir al abrir proceso y al recrear la actividad. No cambiar el viaje ni procesar otra vez el mismo toque.

Evidencia: [manejo de intents Android][a-activity], [protecciones iOS][i-root-notifications].

### F06 — P1 antes de distribuir: falta aprovisionamiento MQTT por instalación

Android genera MQTT_USERNAME y MQTT_PASSWORD en BuildConfig y la aplicación resuelve su cuenta desde esas constantes. Mantener local.properties fuera de Git protege el archivo de desarrollo, pero no evita que las constantes entren al APK.

iOS ya tiene recuperación de configuración pública y almacenamiento separado de credenciales en Keychain tras aprovisionar el teléfono.

**Impacto:** instalar el mismo APK configurado en dos teléfonos les da la misma cuenta. El backend exige cuentas distintas para su quorum; dos dispositivos con la misma cuenta no representan dos aportantes independientes. También hay una diferencia operativa: cambiar o revocar una cuenta Android requiere otra distribución con el esquema actual.

**Acción:** añadir provisión controlada por instalación y almacenamiento de credenciales ligado a Android Keystore. Mantener separadas cuentas de lector, contribuyente y backend. Validar TLS y permisos del broker con dos instalaciones reales.

**Cierre:** mismo APK instalado en A y B, cuentas diferentes sin recompilar, revocación independiente, credencial ausente o ilegible sin degradación insegura y quorum que distingue ambos principales.

No se inspeccionaron ni copiaron los secretos del archivo local. La observación se deduce del código de compilación y lectura.

Evidencia: [campos de compilación Android][a-build], [configuración Android][a-mqtt], [aprovisionamiento iOS][i-mqtt].

### F07 — P1 si se habilita push: FCM no es compatible todavía con el backend

Android publica registro schemaVersion 2, provider fcm, platform android y packageName. El backend actual de main exige schemaVersion 1, valida tokens hexadecimales APNs e instancia APNsProvider.

integrations/fcm_provider.py es una pieza útil para enviar a FCM, pero no está conectada al registro, base de datos, selección de proveedor ni worker de ese backend. Agregar google-services.json por sí solo no resuelve esta incompatibilidad.

**Acción compartida:** acordar un contrato versionado de registro, persistir proveedor e identidad de aplicación, validar cada tipo de token, seleccionar el worker correcto y conservar revisiones, recibos, caducidad, silencios, quorum y deduplicación. Integrarlo en el backend compartido.

**Cierre:** registro aceptado/renovado/revocado por FCM; envío real a Android; política de silencio y caducidad respetada; apertura protegida durante un viaje; regresión de APNs; ausencia de tokens en logs.

La entrega APNs de iOS también está documentada como no desplegada/probada con Apple. La existencia del código no acredita que iOS tenga push operativo en producción.

Evidencia: [cliente Android][a-push], [adaptador FCM][a-fcm], [backend APNs y validador][i-push], [guía de activación FCM][fcm-official].

### F08 — P1 de configuración: MAPS_API_KEY no sustituye la clave del manifiesto

build.gradle.kts define manifestPlaceholders para MAPS_API_KEY, pero AndroidManifest.xml asigna un valor literal al metadato de Google Maps. La propiedad prevista no es la fuente efectiva de la clave.

DirectionsService y PlacesService leen ese mismo metadato para llamadas HTTP. Esto concentra mapa, búsqueda y caminatas en una configuración que no cambia al pasar la propiedad documentada.

**Acción:** conectar realmente la configuración de compilación al manifiesto; separar las configuraciones por uso cuando corresponda y comprobar restricciones de aplicación/API, servicios habilitados y cuotas. Revisar el acceso a los servicios web desde el móvil siguiendo la guía oficial.

**Cierre:** compilar con una configuración de prueba y verificar que el APK contiene la elegida; probar mapa, búsqueda y caminatas con restricciones habilitadas y errores de autorización visibles.

No se consultó Google Cloud, así que no puede afirmarse que la clave actual sea irrestricta, esté siendo abusada o tenga la facturación mal configurada. Tampoco se reproduce su valor en este informe.

Evidencia: [manifiesto Android][a-manifest], [configuración de compilación][a-build], [Directions][a-directions], [Places][a-places], [recomendaciones oficiales de Google][maps-security].

### F09 — P2: la búsqueda excluye parte del área cubierta por las rutas

PlacesService impone un rectángulo fijo: latitud -8.145 a -8.050 y longitud -79.075 a -78.990. Una comprobación del GTFS encuentra **999 de 4067 paraderos fuera de él**, aproximadamente el 24,6 %.

Esto cuantifica una incoherencia de cobertura; no significa que se hayan consultado 999 direcciones ni que cada paradero deba ser un resultado de Places. Sí demuestra que los resultados restringidos no pueden cubrir todo el ámbito de los datos locales.

iOS busca con la región de MapKit y no tiene este mismo rectángulo fijo en su flujo de búsqueda.

**Acción:** definir el ámbito de búsqueda como decisión de producto y alinearlo con la cobertura de rutas y sedes. Permitir búsquedas de referencias del área metropolitana que formen parte del recorrido.

**Cierre:** destinos de los extremos norte, sur, este y oeste del feed; comparación con las mismas consultas en iOS; prueba de resultados irrelevantes fuera del ámbito elegido.

Evidencia: [restricción Places][a-places], [búsqueda iOS][i-search] y conteo en evidencia.json.

### F10 — P2, asociada a F05: pestañas y tracking no conservan la misma sesión

iOS usa TabView para mantener la identidad de sus cinco pestañas y conserva RouteTrackingViewModel en la raíz. Android selecciona una pantalla mediante Crossfade de 250 ms y construye una barra inferior por pantalla.

Los ViewModels Android asociados a la actividad pueden sobrevivir; por tanto, no se pierde todo el estado. La brecha afecta a lo que solo vive en remember: por ejemplo, la subpestaña de Guardado, la ruta de detalle seleccionada y ciertas hojas o posiciones de listas.

TrackingDemo es más claro: crea TrackingDemoVM mediante remember y un scope de composición; al salir llama a detener y cancela ese scope. Volver crea otro modelo.

**Acción:** definir el estado conservado por pestaña, alojar el modelo de viaje fuera de la composición temporal y coordinar suspensión/reanudación con visibilidad y ciclo de vida. Ajustar las transiciones para alcanzar la experiencia inmediata de iOS.

**Cierre:** Guardado/Líneas → Mapa → Guardado conserva subpestaña y posición; Rutas/detalle → Perfil → Rutas conserva la selección acordada; tracking → otra pantalla → tracking conserva el viaje. Rotación y recreación necesitan una política explícita adicional.

Evidencia: [raíz Android][a-root], [Guardado][a-saved], [tracking Android][a-tracking], [raíz iOS][i-root].

### F11 — P2: tener el mismo JSON no da cobertura de traducciones equivalente

Ambas apps incluyen exactamente el mismo catálogo de **1958 entradas**, pero muchas frases Android difieren de las claves españolas de origen.

El barrido de llamadas L.t con primer argumento literal simple, excluyendo escapes e interpolaciones, encuentra:

| Métrica del barrido estático | Android | iOS |
|---|---|---|
| Primeros argumentos distintos detectados | 882 | 1394 |
| Sin clave en el catálogo | **329** | **25** |
| Proporción dentro de esa muestra | **37,3 %** | **1,8 %** |

Estos porcentajes describen esa muestra de textos del código; **no son porcentajes de pantallas ni una medición global de traducción**. El método no cubre L.f, textos construidos, escapes, interpolaciones ni todas las formas posibles de llamada.

L.t en Android devuelve el español cuando no hay traducción para francés, portugués de Brasil, alemán o chino. Ejemplos concretos sin clave: «Abrir ajustes de Android», «Acceso a la cámara denegado», «Accidente» y «Abajo» en controles del visor.

Las interpolaciones directas tienen un problema adicional: el texto con valores concretos no coincide con una clave estable. Conviene usar plantillas con parámetros.

**Acción:** unificar identificadores o claves estables y completar las cadenas propias de Android; parametrizar cantidades, paraderos e importes. Añadir comprobación automática de claves usadas.

**Cierre:** seis idiomas en las cinco pestañas, errores, permisos, notificaciones y 3D; verificar cambios de idioma sin volver a bienvenida ni perder viaje. El listado completo con archivos/líneas está en evidencia.json.

Evidencia: [resolución de idioma Android][a-language], catálogo compartido y listado reproducible adjunto.

### F12 — P2: el diagnóstico offline confunde fallo del feed con catálogo vacío

GTFSRepository.rutas captura el fallo, lo publica en errorCarga y devuelve una lista vacía. OfflineMapSheet solo detecta errores mediante try/catch alrededor de esa llamada. Como el fallo ya fue capturado, puede terminar mostrando «el catálogo no contiene rutas» y ocultar el reintento previsto.

**Acción:** consumir el estado de error real o un resultado explícito de carga; conservar la diferencia entre feed vacío válido, feed UTP aún ausente y feed urbano ilegible.

**Cierre:** carga urbana correcta, archivo requerido ausente, CSV inválido y modo UTP sin feed. El mensaje y el botón de reintento deben corresponder a cada estado.

Evidencia: [contrato de carga Android][a-gtfs], [pantalla offline][a-offline].

### F13 — P2: faltan optimizaciones de cálculo que iOS ya separó

iOS usa VehicleETACache para preparar la geometría una vez por catálogo y la proyección del objetivo por ancla. También incorpora GTFSNearbyRouteIndex como prefiltro conservador de líneas cercanas.

Android recorta la geometría, calcula longitud y proyecta el mismo objetivo de nuevo por vehículo al aplicar snapshots. Para cercanía, recorre las rutas en las consultas de 400/800 m.

**Acción:** después de F01, introducir cachés con invalidación por revisión de catálogo y ancla; añadir un prefiltro de límites que preserve todos los resultados correctos.

**Cierre:** resultados idénticos con y sin caché; benchmark de carga inicial, cambio de destino y snapshots con flota grande en un Android intermedio.

No se midieron FPS, batería ni latencias en dispositivo. La brecha de estructura está confirmada; su impacto cuantitativo queda por medir.

Evidencia: [ETA Android][a-eta], [consulta GTFS Android][a-gtfs], [caché iOS][i-eta-cache], [índice iOS][i-nearby-index].

### F14 — P2: el control de regresión Android es menor que el de iOS

iOS tiene un workflow versionado para main/PR, compilación Release y pruebas en simulador; hay 25 archivos de pruebas unitarias y dos de UI. La existencia del workflow no acredita que sus últimas ejecuciones remotas estén verdes; no se consultó ese estado.

Android tiene 17 archivos JUnit, 119 pruebas que pasan y ocho vistas previas de captura, pero no tiene archivos publicados en .github/workflows. Su prueba instrumental es todavía la plantilla que comprueba el nombre del paquete.

**Acción:** automatizar compilación, unitarias, lint y comprobación del APK. Añadir pruebas instrumentales de navegación y viaje, y capturas de los estados que hoy quedan fuera de Rutas/Perfil/recarga/QR.

**Cierre:** cada PR dirigido a main ejecuta las comprobaciones; artefactos y fallos quedan visibles; una regresión en F01–F05 rompe una prueba significativa.

Los conteos de archivos de prueba no son porcentajes de cobertura; los dos lenguajes organizan sus pruebas de forma distinta.

Evidencia: [workflow iOS][i-ci], [prueba instrumental Android][a-instrumented], [capturas Android][a-screenshots].

### F15 — P2 futuro: separar identidades del GTFS institucional

iOS prefija agency_id, route_id, trip_id, shape_id, stop_id y fare_id institucionales con utp:. Android separa sus cachés por carpeta, pero conserva los identificadores originales al parsear.

**Impacto condicionado:** hoy el feed institucional no existe en ninguna app, de modo que no se ha demostrado una colisión con datos actuales. Al incorporarlo, dos catálogos con los mismos IDs podrían mezclarse en favoritos, reportes o cachés.

**Acción:** establecer el espacio de nombres y su compatibilidad con el backend antes de incorporar el feed institucional.

**Cierre:** un feed urbano y otro institucional con IDs numéricos iguales se guardan, consultan y eliminan de forma independiente.

Evidencia: [aislamiento de IDs iOS][i-transport], [parser Android][a-gtfs].

## 5. Datos: qué está realmente sincronizado

### GTFS

Se compararon los once archivos completos, no solo nombres o cantidad de rutas. **Todos son binariamente idénticos.**

| Archivo | Filas de datos |
|---|---:|
| agency.txt | 56 |
| routes.txt | 102 |
| trips.txt | 102 |
| stops.txt | 4067 |
| stop_times.txt | 20170 |
| shapes.txt | 53616 |
| frequencies.txt | 102 |
| fare_attributes.txt | 102 |
| fare_rules.txt | 102 |
| calendar.txt | 1 |
| feed_info.txt | 1 |

El feed incluye metadatos «Arequipa Bus» y una versión fechada en noviembre de 2024 pese a usarse para el ámbito de Trujillo. Los dos repositorios comparten esa cuestión de procedencia/actualización. Hace falta revisar el origen y vigencia con el responsable de datos; igualdad de archivos no equivale a exactitud operativa actual.

Los parsers seleccionan un viaje por ruta conforme a este feed. Un futuro GTFS con varios viajes, sentidos u horarios requiere una decisión de modelo y pruebas en ambas plataformas.

### Catálogos, señas y 3D

| Recurso | Resultado |
|---|---|
| sedes-empresas-trujillo.json | Idéntico; 24 sedes. |
| negocios_promocionados.json | Idéntico; 100 negocios de demostración. |
| traducciones.json | Idéntico; 1958 entradas. |
| senias/manifest.json | Idéntico; 29 claves que referencian 28 archivos distintos. |
| Clips de señas | 56 MP4 idénticos: 28 en español y 28 en inglés. Las 29 claves resuelven en cada uno de esos idiomas. |
| Señas en fr/pt-BR/de/zh-Hans | Cero clips para las claves del manifiesto en ambos repositorios. |
| Transportes 3D Android | Seis mallas convertidas, con metadatos de partes; pruebas y comprobación del APK correctas. |

Las mallas Android y los USDZ iOS tienen formatos distintos; no se les atribuye igualdad binaria. El renderizado final, iluminación, materiales, texto y gestos deben compararse visualmente.

## 6. Arquitectura y equivalencias de plataforma

| Responsabilidad | iOS | Android | Criterio de paridad |
|---|---|---|---|
| UI y estado | SwiftUI, ObservableObject/Observation | Compose, ViewModel, StateFlow y mutableStateOf | Mismos estados visibles y reglas de conservación. |
| Asincronía | Task, actor, AsyncStream | Corrutinas, Flow, Mutex | Cancelación, revisión y publicación coherentes. |
| Mapa/búsqueda | MapKit y MKLocalSearch | Google Maps y Places HTTP | Comparar destinos y límites; proveedores pueden devolver resultados distintos. |
| Caminatas | MKDirections | Directions HTTP | Misma política de límites y estimación cuando no hay respuesta. |
| GPS | CoreLocation compartido | Fused Location Provider compartido | Evitar consumidores duplicados y respetar autorización/ciclo de vida. |
| Movimiento | CoreMotion | Activity Recognition Transition API | Validar detección con datos reales y permisos denegados. |
| Cámara/medios | AVFoundation/PhotosUI | CameraX/selector Android/Media3 | Flujos de permiso, lectura y cancelación equivalentes. |
| Preferencias | UserDefaults | DataStore y SharedPreferences | Compatibilidad del esquema dentro de cada plataforma. |
| Secretos locales | Keychain | Keystore y almacenamiento cifrado para tarjetas | Extender la protección a provisión MQTT. |
| Notificaciones | UNUserNotificationCenter y APNs | NotificationManager/AlarmManager y FCM | Políticas comunes; mecanismo y entrega nativos. |
| 3D | Recursos USDZ y presentación nativa iOS | OpenGL ES con mallas convertidas | Identidad/colores/gestos equivalentes; no exigir píxeles idénticos. |
| Servicio compartido | Backend Python y broker en el repo iOS | Consume contratos; aporta adaptador FCM | Versionar y probar contratos entre repositorios. |

Android tiene archivos de pantalla grandes: MapaScreen, TrackingDemoScreen y FlujoAbordaje concentran presentación, ciclo de vida y coordinación. iOS ya ha separado más partes del mapa, tracking y validación en servicios/modelos.

**Recomendación de estructura:** extraer las reglas compartidas de viaje y los estados de carga antes de ampliar la interfaz. Mantener una sola fuente del viaje activo, una identidad de catálogo y servicios inyectables para pruebas. No se necesita reproducir la misma organización de carpetas Swift en Kotlin.

## 7. Límites compartidos que afectan al producto completo

Estos puntos requieren decisiones del equipo y no representan funcionalidades terminadas de iOS que Android simplemente deba copiar:

- Autenticación y cuenta de usuario sincronizada: no hay un servicio completo que convierta el perfil local en una identidad verificada.
- Pagos y recargas: el saldo es local y de demostración; reconocer un QR no acredita un pago bancario.
- Carné: no está validado por la institución ni conectado a un sistema de credenciales.
- Comunidad general: foto y confirmación de publicación son demostración; no hay persistencia compartida de publicaciones.
- Negocios y descuentos: el catálogo es ficticio; guardar un cupón no acredita una promoción comercial real.
- Iluminación de paraderos: GTFS no aporta ese atributo; una muestra estable no es una inspección de seguridad.
- Buses institucionales: falta el feed gtfs-utp.
- Mapas offline completos: los recursos GTFS son locales, pero no se implementa una descarga de mapas base.
- Push: hay código para APNs/FCM, pero no una entrega real acreditada por esta auditoría.
- Ubicación comunitaria: depende de consentimiento, primer plano, cuentas independientes, backend activo y calidad GPS. No equivale a un GPS instalado por un operador en cada bus.
- Privacidad del tracking: cuenta MQTT, sesión, ubicación y tiempo pueden ser correlacionados por la infraestructura. Terminar el viaje local no acredita borrado del histórico del servidor.

## 8. Pruebas que faltan para afirmar paridad

### Contratos y lógica automatizada

1. Fixtures JSON comunes para posiciones v2/v3, estado de simulación, revisiones, reloj, revocación, ocupación y cambios de ruta. Cada plataforma procesa los mismos archivos.
2. Casos comunes de ETA con sentido, parada y velocidad desconocida.
3. Itinerarios con comparación de directos y transbordos, incluyendo el umbral 45 min/15 min/25 %.
4. Respuestas de red fuera de orden, cancelación y cambio de catálogo durante operaciones.
5. Apertura de notificaciones ante cada tipo de viaje activo.
6. JSON ilegible, reset con respaldo y error de carga del GTFS.
7. Cambio de idioma y detección de claves sin catálogo.
8. Catálogos urbano/institucional con IDs repetidos.
9. Recursos 3D del APK, no solo archivos fuente.

### Teléfono/emulador Android

| Grupo | Escenarios mínimos |
|---|---|
| Instalación | Inicio limpio, actualización conservando datos y apertura desde el icono. |
| Navegación | Cinco pestañas; regresar a detalle/subpestaña; botón Atrás; giro y recreación de actividad. |
| Ubicación | Precisa, aproximada, denegada, revocada desde ajustes, GPS apagado y primera posición tardía. |
| Viaje | Directo, transbordo, desvío, recálculo, pausa y finalización; no mezclar tracking con viaje declarado. |
| MQTT | Sin configurar, TLS válido, credenciales inválidas, caída/reconexión y dos teléfonos con cuentas distintas. |
| Simulación | Control vigente, expirado, revocado y reinicio de proceso; etiqueta correcta de procedencia. |
| Notificaciones | Permiso denegado/concedido, silencio, reinicio del dispositivo, cambio horario y toque durante viaje. |
| Cámara | Permiso denegado, QR válido/ambiguo, capturas repetidas y cancelación. |
| Accesibilidad | TalkBack, texto grande, tema oscuro, orden de foco, controles de mapa y señas sobre hojas. |
| 3D | Seis modelos, colores por ruta, giro/zoom/desplazamiento, entrada/salida y restauración de superficie. |
| Offline | Catálogo y señas locales; mensajes honestos al fallar búsqueda/mapa/caminatas. |

### Ensayo conjunto iPhone–Android–backend

Usar el mismo commit de datos y dos cuentas MQTT independientes. Repetir el mismo recorrido, registrar ventanas de tiempo y comparar decisiones de abordaje/descenso, rutas, ocupación, quorum y cambios. Ejecutar escenarios de simulación con sus contratos actuales.

El backend actual importa fcntl y no se ejecutó en Windows. El entorno de validación compartida debe ser el soportado por ese servicio, por ejemplo Linux/macOS o un entorno Linux preparado para estas pruebas. No crear otro agregador Android con reglas divergentes.

## 9. Plan priorizado de actualización

El [plan detallado por fases](../PLAN_PARIDAD_IOS_ANDROID.md) desarrolla este
orden con dependencias, criterios de cierre y las adaptaciones permitidas por
plataforma. Es la guía de ejecución y registra el avance actual. La
[fase 1](../FASE_1_REFERENCIA_Y_VALIDACION.md) ya dispone de referencia y
comprobaciones locales; este informe conserva los hallazgos de la auditoría inicial.

| Orden | Entrega | Incluye | Responsable principal | Complejidad | Condición de salida |
|---|---|---|---|---|---|
| 1 | Reglas correctas de viaje | F01, F02, F03 | Android con revisión de iOS | Media | ETA y selección de ruta coinciden en fixtures; respuestas antiguas no cambian destino. |
| 2 | Contexto y continuidad | F04, F05, F10 | Android | Alta | Modo, pestañas y notificaciones preservan o terminan explícitamente el viaje correcto. |
| 3 | Configuración distribuible | F06, F08 | Android e infraestructura | Alta | Cuenta por instalación; configuración efectiva de Google; dos dispositivos probados. |
| 4 | Cobertura funcional del usuario | F09, F11, F12 | Android y contenido | Media | Cobertura geográfica acordada, textos completos y errores distinguibles. |
| 5 | Integración remota opcional | F07 | Backend y Android, regresión iOS | Alta | FCM aceptado por registro/worker y entrega real validada. |
| 6 | Rendimiento y regresión | F13, F14 | Android y QA | Media | CI en main/PR, pruebas de flujos y mediciones en Android intermedio. |
| Antes del feed UTP | Identidades institucionales | F15 | Datos, backend y ambas apps | Media | IDs y catálogo aislados en todas las capas. |

Las fases 3 y 4 pueden organizarse a la vez que la validación de la fase 2 si el equipo dispone de capacidad. No se fija una fecha sin conocer responsables, disponibilidad de dispositivos y acceso a infraestructura.

### Qué conviene conservar

El repositorio ya tiene pruebas útiles del parser, dinero/QR, caché de caminatas, recursos y control de simulación; también preserva JSON ilegible y evita almacenar PAN/CVV. Esas garantías deben mantenerse al corregir la paridad.

El planificador, DataStore, CameraX, Media3 y OpenGL son soluciones Android válidas. La meta es compartir contratos y resultados relevantes para el usuario, manteniendo integraciones nativas por plataforma.

## 10. Seguimiento entre repositorios

El documento previo docs/PARIDAD_IOS.md registra correctamente el commit iOS de referencia y numerosos cambios portados. Conviene actualizar sus afirmaciones de equivalencia con estos hallazgos y sus pruebas de cierre; que una función exista no demuestra que ya se hayan portado todas sus reglas.

FIREBASE_ANDROID.md y tools/README.md aún enlazan algunas referencias anteriores del backend. El contrato debería anclarse al commit exacto usado por la entrega Android y acompañarse de fixtures ejecutables.

Proceso sugerido:

1. Registrar por entrega el commit iOS, Android y la revisión del contrato/backend.
2. Identificar cambios de main que afectan comportamiento o datos.
3. Añadir o actualizar un fixture compartido para cada regla.
4. Portar y ejecutar pruebas en ambas plataformas.
5. Cerrar la fila de paridad solo con resultado automático o evidencia de dispositivo, según el caso.

## 11. Evidencia y reproducción

Archivos adjuntos en esta carpeta:

- **INFORME.md**: diagnóstico y plan de actualización.
- **evidencia.json**: commits, hashes y filas GTFS, igualdad de catálogos/clips, textos sin clave con ubicaciones, pruebas y resumen lint.
- **comprobar_recursos.py**: reproduce los conteos y comparaciones de recursos. No consulta servicios ni credenciales.

Ejemplo, desde la raíz Android, con un checkout local de main iOS:

~~~powershell
python docs/auditoria-main-2026-11-10/comprobar_recursos.py --ios RUTA_LOCAL_IOS --android .
.\gradlew.bat :app:testDebugUnitTest --rerun --offline
.\gradlew.bat :app:assembleDebug :app:lintDebug --offline
python tools/verificar_apk.py
~~~

El modificador offline presupone que las dependencias ya están disponibles en la caché. Los resultados de pruebas/lint y el hash del APK se leen de los artefactos locales existentes; regenerarlos antes de recopilar una nueva evidencia.

Los enlaces siguientes apuntan a los commits congelados auditados. No dependen de que main cambie después.

[ios-main]: https://github.com/JDiazD33/RutaUTP/tree/8145276bb6fc72a168cc78119def2727578ec0bb
[android-main]: https://github.com/Defartion/RutaUTPAndroid/tree/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1
[a-eta]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/tracking/VehicleETAEstimator.kt#L28
[a-fleet]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/mapa/MapaViewModel.kt#L381
[a-planner]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/routing/TransitPlanner.kt#L196
[a-search]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/mapa/MapaViewModel.kt#L534
[a-mode]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/mapa/MapaViewModel.kt#L237
[a-panel]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/mapa/MapaScreen.kt#L1207
[a-activity]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/MainActivity.kt#L138
[a-build]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/build.gradle.kts#L31
[a-mqtt]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/tracking/MQTTConfiguration.kt#L42
[a-push]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/notificaciones/PushRutasService.kt#L110
[a-fcm]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/integrations/fcm_provider.py
[a-manifest]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/AndroidManifest.xml#L37
[a-directions]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/directions/DirectionsService.kt#L49
[a-places]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/places/PlacesService.kt#L57
[a-root]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/RootView.kt#L26
[a-saved]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/GuardadoPantalla.kt#L33
[a-tracking]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/TrackingDemoScreen.kt#L526
[a-language]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/idioma/L.kt#L33
[a-gtfs]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/data/gtfs/GTFSRepository.kt#L57
[a-offline]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/main/java/com/example/rutautpnative/ui/screens/PerfilExtras.kt#L44
[a-instrumented]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/androidTest/java/com/example/rutautpnative/ExampleInstrumentedTest.kt#L18
[a-screenshots]: https://github.com/Defartion/RutaUTPAndroid/blob/7ba57cbe73f97e83fd112584a9e8ff89623ac4c1/app/src/screenshotTest/kotlin/com/example/rutautpnative/PantallasIosCapturas.kt
[i-eta]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/Routing/VehicleETAEstimator.swift#L25
[i-eta-tests]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTPTests/VehiclePositionSanitizerTests.swift#L301
[i-planner]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/Routing/TransitPlanning.swift#L288
[i-search]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Screens/Mapa/MapaViewModel%2BBusqueda.swift#L49
[i-root]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Navigation/RootView.swift#L128
[i-root-notifications]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Navigation/RootView.swift#L182
[i-mqtt]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/Tracking/MQTTConfiguration.swift#L64
[i-push]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/backend/rutautp_backend/push_service.py#L183
[i-eta-cache]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/Routing/VehicleETACache.swift
[i-nearby-index]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/GTFS/GTFSNearbyRouteIndex.swift
[i-ci]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/.github/workflows/ios.yml
[i-transport]: https://github.com/JDiazD33/RutaUTP/blob/8145276bb6fc72a168cc78119def2727578ec0bb/RutaUTP/Services/GTFS/TransporteApp.swift#L78
[maps-security]: https://developers.google.com/maps/api-security-best-practices
[fcm-official]: https://firebase.google.com/docs/cloud-messaging/android/get-started
