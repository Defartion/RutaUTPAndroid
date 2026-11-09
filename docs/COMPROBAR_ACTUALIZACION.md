# Comprobar la actualización en Android Studio

1. Abrir el proyecto de esta carpeta y esperar a que termine la sincronización
   de Gradle. Elegir `app` y el dispositivo o emulador en la barra superior.
2. Pulsar Run para recompilar e instalar. Un proceso que ya estaba abierto en
   el teléfono puede seguir mostrando la instalación anterior hasta ejecutarlo.
3. En el mapa, abrir el menú lateral → Sobre nosotros. Debe aparecer **v1.1 (2)**.
   El número ahora se lee de la versión real de la aplicación.

## Cambios visibles

- **Rutas → 3D**, en la cabecera: visor de micros, combis y colectivos, con modelos
  original/detallado, giro y zoom. El detalle de una ruta también incluye su ficha
  de referencia y el botón Identificar transporte en 3D.
- **Perfil → Idioma**: selector de seis idiomas.
- **Perfil → Respaldos y recuperación**: exportación de copias y restablecimiento
  de grupos con confirmación.
- **Menú del mapa → Notificaciones**: recordatorios por horario y destino,
  silencios y notificación de prueba. Firebase permanece pendiente de configurar.
- **Menú del mapa → Ajustes**: límite de micros visibles.
- **Mapa, después de calcular un viaje → Otras rutas**: alternativas de viaje.
- **Seguridad**: contacto de emergencia compartido con Perfil y paraderos guardados.

La pantalla inicial, los colores generales y las cinco pestañas mantienen su
estructura. Gran parte de la actualización cambia datos, búsqueda, cálculo,
persistencia y servicios; no es un rediseño completo de la pantalla principal.

## Git y GitHub

Un commit guarda los cambios en el repositorio de la computadora. Para que
aparezcan en GitHub hay que publicar los commits mediante Push. Los archivos
nuevos que todavía figuran como no versionados deben incluirse también en el
commit; de otro modo, el proyecto compartido puede quedar incompleto.
