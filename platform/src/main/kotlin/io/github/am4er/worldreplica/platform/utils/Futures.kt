package io.github.am4er.worldreplica.platform.utils

import java.util.concurrent.CompletionException

fun unwrap(e: Throwable): Throwable = if (e is CompletionException && e.cause != null) e.cause!! else e
