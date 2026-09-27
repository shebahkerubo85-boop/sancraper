plugins {
    kotlin("jvm") version "2.2.20" apply false
    // Required: without the serialization compiler plugin no @Serializable serializer is
    // generated, and every model throws SerializationException at runtime.
    kotlin("plugin.serialization") version "2.2.20" apply false
}
