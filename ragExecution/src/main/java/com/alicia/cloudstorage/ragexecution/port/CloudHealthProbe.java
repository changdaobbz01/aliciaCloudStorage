package com.alicia.cloudstorage.ragexecution.port;

@FunctionalInterface
public interface CloudHealthProbe {

    DependencyHealth check();
}
