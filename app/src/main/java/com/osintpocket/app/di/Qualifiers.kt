package com.osintpocket.app.di

import javax.inject.Qualifier

/** Cliente para analizar webs: sigue redirecciones y no reintenta (queremos ver la respuesta real). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebClient
