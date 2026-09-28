package com.alicia.cloudstorage.ragexecution.port;

@FunctionalInterface
public interface DatabaseHealthProbe {

    DependencyHealth check();
}
