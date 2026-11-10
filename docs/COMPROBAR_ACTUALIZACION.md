# Comprobar la actualización en Android Studio

1. Abrir el proyecto de esta carpeta y esperar a que termine la sincronización
   de Gradle. Elegir `app` y el dispositivo o emulador en la barra superior.
2. Pulsar Run para recompilar e instalar. Un proceso que ya estaba abierto en
   el teléfono puede seguir mostrando la instalación anterior hasta ejecutarlo.
3. En el mapa, abrir el menú lateral → Sobre nosotros. Debe aparecer **v1.2 (3)**.
   El número ahora se lee de la versión real de la aplicación.

## Cambios visibles

- **Rutas**: título neutro, búsqueda fija y acceso grande a Transportes en 3D.
  Las tarjetas muestran la letra sobre el color de la línea, código, empresa,
  primer y último paradero reales, tarifa y frecuencia. Ya no aparece el mapa
  de muestra que ocupaba la parte superior del catálogo.
- **Rutas → una línea**: ficha de identidad, duración/tarifa/paraderos/distancia,
  mapa del recorrido completo, visor 3D integrado y botón Iniciar navegación.
  Tocar el mapa abre el explorador con controles separados para tu ubicación
  y para encuadrar la ruta, leyenda y lista completa de paraderos.
- **Transportes 3D**: modelos originales/detallados, giro, zoom y desplazamiento
  con dos dedos. La identificación aplica los colores de referencia a la
  carrocería y sus franjas, y coloca el código/letra en el parabrisas.
- **Perfil**: tarjeta de identidad clara, billetera independiente y selector
  Credenciales. La foto del carné físico y el carné digital se abren por separado;
  el carné digital usa la foto de perfil. Ajustes agrupados en Preferencias,
  Accesibilidad y Privacidad y datos. Se retiraron contadores de viajes y trofeos
  ficticios de la presentación anterior.
- **Guardado**: pestañas Lugares/Líneas; lugares y paraderos en secciones
  distintas; las líneas usan las nuevas tarjetas. El explorador consulta el
  catálogo completo de paraderos, en lugar de limitarlo a una muestra de 24.
- **Seguridad**: saludo, reloj, pestañas Ayuda/Comunidad, acceso a emergencias,
  contacto, reportes y avisos de líneas guardadas o del viaje activo.
- **Mapa**: nueva cabecera y panel inferior plegable con controles, reporte y
  cantidad de vehículos disponibles. Los estados vacíos reflejan la conexión.
- **Perfil → Recargar**: formulario con saldo previsto, importes adaptables al
  tamaño del texto, elección de billetera y confirmación desplazable antes del
  historial. **Pagar con QR** presenta primero el escáner y después el QR de cobro,
  billetera, titular y celular, según la revisión iOS del 9/11.
- **Perfil → Idioma**: selector de seis idiomas.
- **Perfil → Respaldos y recuperación**: exportación de copias y restablecimiento
  de grupos con confirmación.
- **Menú del mapa → Notificaciones**: recordatorios por horario y destino,
  silencios y notificación de prueba. Firebase permanece pendiente de configurar.
- **Menú del mapa → Ajustes**: límite de micros visibles.
- **Mapa, después de calcular un viaje → Otras rutas**: alternativas de viaje.
- **Seguridad**: contacto de emergencia compartido con Perfil y paraderos guardados.

Las cinco pestañas y la temática de empresa siguen siendo los puntos de entrada.
Los cambios de esta versión incluyen la presentación y los flujos visibles de
la main iOS, además de los datos y servicios portados en la revisión anterior.

## Vistas previas reproducibles

Las capturas de Compose de Rutas, Perfil, Recargar y Pagar con QR están en
`app/src/screenshotTestDebug/reference`. Incluyen claro/oscuro y Rutas con
texto al 150 % en Rutas y Recargar. Representan componentes reales con datos de muestra, no una
sesión de GPS ni un teléfono conectado.

```powershell
.\gradlew.bat -Pcapturas=true -Pandroid.experimental.enableScreenshotTest=true :app:validateDebugScreenshotTest
```

Para regenerarlas tras revisar un cambio visual, usar
`:app:updateDebugScreenshotTest` con las mismas propiedades. El complemento
de capturas es opcional y usa la versión compatible con AGP 9.2 del proyecto;
la compilación habitual no lo activa. No es necesario actualizar AGP para usarlo.

## Git y GitHub

Un commit guarda los cambios en el repositorio de la computadora. Para que
aparezcan en GitHub hay que publicar los commits mediante Push. Los archivos
nuevos que todavía figuran como no versionados deben incluirse también en el
commit; de otro modo, el proyecto compartido puede quedar incompleto.
