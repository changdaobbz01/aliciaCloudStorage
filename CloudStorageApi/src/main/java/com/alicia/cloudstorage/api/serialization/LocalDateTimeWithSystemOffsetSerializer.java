package com.alicia.cloudstorage.api.serialization;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LocalDateTimeWithSystemOffsetSerializer extends ValueSerializer<LocalDateTime> {

    @Override
    public void serialize(
            LocalDateTime value,
            JsonGenerator generator,
            SerializationContext context
    ) {
        generator.writeString(
                value.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
    }
}
