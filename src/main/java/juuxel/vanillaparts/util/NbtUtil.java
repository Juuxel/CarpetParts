package juuxel.vanillaparts.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import java.util.HashMap;
import java.util.Map;

public final class NbtUtil {
    private static final Map<Class<? extends Enum<?>>, Map<String, ? extends Enum<?>>> CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E> & StringIdentifiable> E getEnum(NbtCompound nbt, String key, Class<E> type) {
        E value = (E) CACHE.computeIfAbsent(type, c -> createCache(type)).get(nbt.getString(key));
        if (value == null) value = type.getEnumConstants()[0];
        return value;
    }

    private static <E extends Enum<E> & StringIdentifiable> Map<String, E> createCache(Class<E> type) {
        Map<String, E> cache = new HashMap<>();

        for (E value : type.getEnumConstants()) {
            cache.put(value.asString(), value);
        }

        return cache;
    }

    public static <E extends Enum<E> & StringIdentifiable> void putEnum(NbtCompound nbt, String key, E value) {
        nbt.putString(key, value.asString());
    }

    public static <E> E getRegistryEntry(NbtCompound nbt, String key, Registry<E> registry) {
        return registry.get(new Identifier(nbt.getString(key)));
    }

    public static <E> void putRegistryEntry(NbtCompound nbt, String key, Registry<E> registry, E entry) {
        nbt.putString(key, registry.getId(entry).toString());
    }
}
