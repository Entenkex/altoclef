package adris.altoclef.util.serialization;

import adris.altoclef.util.helpers.ItemHelper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Reads/writes a single {@link Item} as its id ("diamond", "minecraft:diamond"), so it works for
 * any type that contains items (lists, maps, ...). Old numeric raw ids are still accepted.
 */
public final class ItemIdCodec {

    private ItemIdCodec() {
    }

    public static SimpleModule module() {
        SimpleModule module = new SimpleModule("altoclef-items");
        module.addSerializer(Item.class, new Serializer());
        module.addDeserializer(Item.class, new Deserializer());
        return module;
    }

    public static final class Serializer extends StdSerializer<Item> {
        public Serializer() {
            super(Item.class);
        }

        @Override
        public void serialize(Item item, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(ItemHelper.trimItemName(item.getTranslationKey()));
        }
    }

    public static final class Deserializer extends StdDeserializer<Item> {
        public Deserializer() {
            super(Item.class);
        }

        @Override
        public Item deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                // Old raw id
                return Item.byRawId(p.getIntValue());
            }
            String itemKey = ItemHelper.trimItemName(p.getValueAsString());
            Identifier identifier = Identifier.of(itemKey);
            if (Registries.ITEM.containsId(identifier)) {
                return Registries.ITEM.get(identifier);
            }
            return (Item) ctxt.handleWeirdStringValue(Item.class, itemKey, "unknown item");
        }
    }
}
