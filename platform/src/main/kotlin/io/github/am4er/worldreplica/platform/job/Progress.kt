package io.github.am4er.worldreplica.platform.job

class Progress {
    @Volatile
    var done = 0L

    @Volatile
    var total = 0L
}
