package com.morpheus.application.query.dsl;

/** Logical field type used to validate operators without exposing transport/store types. */
public enum QueryFieldType {
    TEXT,
    IDENTITY,
    ENUM,
    BOOLEAN,
    NUMBER
}
